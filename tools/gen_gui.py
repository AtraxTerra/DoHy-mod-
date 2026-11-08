#!/usr/bin/env python3
"""Dessine les fonds d'interface des machines, au style vanilla.

Pourquoi generer plutot que reprendre les textures de Paladium : elles ne sont
pas reutilisables. Le PalaMod dessinait ses interfaces avec un framework maison
(Helios / Paladium-UI) qui assemblait des coins et des remplissages a la volee —
`gui/crusher/` contient des `*_corner.png` de 26x26 et des `*_placeholder.png`
de 200x200, pas un fond. Et le cobblebreaker n'a aucun fond du tout, seulement
une icone d'outil de 43x43. Il n'y a donc rien a convertir.

On produit donc des panneaux au format vanilla (256x256, panneau 176x166 en
haut a gauche), avec la palette exacte des interfaces du jeu, plus les sprites
de progression a des decalages connus.

    python3 tools/gen_gui.py

LA DISPOSITION DES SLOTS EST LA SOURCE UNIQUE. Elle est aussi emise dans
`data/palamod/gui_layout.json`, que `check_assets.py` compare aux constantes du
code Java : un slot dessine a un endroit et declare a un autre donne une
interface ou les objets flottent a cote de leur case, et rien ne le signale.
"""
import json
import os

from PIL import Image, ImageDraw

RES = "src/main/resources"
NS = "palamod"

# Palette des interfaces vanilla, relevee sur les textures du jeu.
PANEL = (198, 198, 198, 255)
PANEL_LIGHT = (255, 255, 255, 255)
PANEL_DARK = (85, 85, 85, 255)
SLOT_BG = (139, 139, 139, 255)
SLOT_DARK = (55, 55, 55, 255)
TRANSPARENT = (0, 0, 0, 0)

WIDTH, HEIGHT = 176, 166
TEXTURE = 256

# Inventaire du joueur, aux coordonnees vanilla. Les machines ne les changent
# jamais : un joueur doit retrouver son sac au meme endroit partout.
PLAYER_X, PLAYER_Y = 8, 84
HOTBAR_Y = 142

# Sprites de progression, ranges sous le panneau dans la meme texture.
ARROW_U, ARROW_V, ARROW_W, ARROW_H = 176, 0, 24, 17
ARROW_FULL_V = 17
FLAME_U, FLAME_V, FLAME_W, FLAME_H = 176, 34, 14, 14
FLAME_FULL_V = 48

# ---------------------------------------------------------------------------
# Disposition des machines. (x, y) du coin haut gauche de chaque case.
# ---------------------------------------------------------------------------

LAYOUTS = {
    "cobblebreaker": {
        "title": "Cobblebreaker",
        # Une entree en haut au centre, six sorties sur une rangee en dessous.
        # Les six cases sont rangees du metal le plus courant au plus rare,
        # de gauche a droite : c'est l'ordre dans lequel le joueur les voit se
        # remplir, et celui des tranches du tirage.
        "slots": [{"x": 80, "y": 18, "role": "input"}] + [
            {"x": 34 + 18 * index, "y": 54, "role": "output"} for index in range(6)
        ],
        "arrow": {"x": 76, "y": 36, "vertical": True},
    },
    "crusher": {
        "title": "Crusher",
        # Fruit au-dessus du charbon, a gauche ; la fleche traverse ; sortie a
        # droite. C'est la disposition du fourneau vanilla, que tout le monde
        # sait lire sans explication.
        "slots": [
            {"x": 44, "y": 24, "role": "fruit"},
            {"x": 44, "y": 60, "role": "fuel"},
            {"x": 116, "y": 42, "role": "output"},
        ],
        "arrow": {"x": 76, "y": 44, "vertical": False},
        "flame": {"x": 45, "y": 44},
        # Le compteur de fruits broyes : centre sous la fleche, a l'ecart des
        # trois cases. C'est l'etat le plus volumineux de la machine — jusqu'a
        # 128 fruits pour l'orangeblue — et il n'etait visible nulle part.
        "counter": {"x": 88, "y": 66},
    },
}


def panel(draw, x, y, width, height):
    """Un panneau vanilla : fond gris, lumiere en haut a gauche, ombre en bas a droite."""
    draw.rectangle([x, y, x + width - 1, y + height - 1], fill=PANEL)
    draw.line([x, y, x + width - 2, y], fill=PANEL_LIGHT)
    draw.line([x, y, x, y + height - 2], fill=PANEL_LIGHT)
    draw.line([x + width - 1, y + 1, x + width - 1, y + height - 1], fill=PANEL_DARK)
    draw.line([x + 1, y + height - 1, x + width - 1, y + height - 1], fill=PANEL_DARK)


def slot(draw, x, y):
    """Une case 18x18 : creux gris, ombre en haut a gauche, lumiere en bas a droite.

    Le decalage de -1 est la convention vanilla : un slot declare en (x, y)
    contient son objet a cette position, et son cadre deborde d'un pixel autour.
    """
    left, top = x - 1, y - 1
    draw.rectangle([left, top, left + 17, top + 17], fill=SLOT_BG)
    draw.line([left, top, left + 16, top], fill=SLOT_DARK)
    draw.line([left, top, left, top + 16], fill=SLOT_DARK)
    draw.line([left + 17, top + 1, left + 17, top + 17], fill=PANEL_LIGHT)
    draw.line([left + 1, top + 17, left + 17, top + 17], fill=PANEL_LIGHT)


def arrow(draw, u, v, filled, vertical):
    """Les deux etats de la fleche de progression : vide, puis pleine."""
    body = (110, 110, 110, 255) if not filled else (255, 255, 255, 255)
    if vertical:
        # Une fleche vers le bas tient dans 16x24 ; on la dessine dans la meme
        # case que l'horizontale pour n'avoir qu'un couple de decalages.
        draw.rectangle([u + 5, v, u + 10, v + 10], fill=body)
        draw.polygon([(u + 2, v + 9), (u + 13, v + 9), (u + 7, v + 16)], fill=body)
    else:
        draw.rectangle([u, v + 5, u + 15, v + 11], fill=body)
        draw.polygon([(u + 14, v + 2), (u + 14, v + 14), (u + 23, v + 8)], fill=body)


def flame(draw, u, v, filled):
    """La jauge de carburant, en forme de flamme."""
    body = (90, 90, 90, 255) if not filled else (255, 170, 40, 255)
    draw.polygon([(u + 7, v), (u + 11, v + 6), (u + 13, v + 4), (u + 13, v + 10),
                  (u + 7, v + 13), (u + 1, v + 10), (u + 1, v + 4), (u + 3, v + 6)],
                 fill=body)


def build(name, layout):
    image = Image.new("RGBA", (TEXTURE, TEXTURE), TRANSPARENT)
    draw = ImageDraw.Draw(image)

    panel(draw, 0, 0, WIDTH, HEIGHT)

    for entry in layout["slots"]:
        slot(draw, entry["x"], entry["y"])

    # Inventaire du joueur : trois rangees de neuf, puis la barre d'action.
    for row in range(3):
        for column in range(9):
            slot(draw, PLAYER_X + column * 18, PLAYER_Y + row * 18)
    for column in range(9):
        slot(draw, PLAYER_X + column * 18, HOTBAR_Y)

    vertical = layout["arrow"]["vertical"]
    arrow(draw, ARROW_U, ARROW_V, False, vertical)
    arrow(draw, ARROW_U, ARROW_FULL_V, True, vertical)
    if "flame" in layout:
        flame(draw, FLAME_U, FLAME_V, False)
        flame(draw, FLAME_U, FLAME_FULL_V, True)

    path = os.path.join(RES, "assets", NS, "textures", "gui", "%s.png" % name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    return path


def main():
    written = []
    for name, layout in LAYOUTS.items():
        written.append(build(name, layout))

    # Le manifeste que la CI compare au code Java.
    manifest = {
        "_comment": "GENERE par tools/gen_gui.py. Positions des cases de chaque "
                    "interface, en pixels depuis le coin haut gauche du panneau. "
                    "check_assets.py verifie que le code Java declare les memes.",
        "panel": {"width": WIDTH, "height": HEIGHT},
        "player": {"x": PLAYER_X, "y": PLAYER_Y, "hotbar_y": HOTBAR_Y},
        "sprites": {
            "arrow": {"u": ARROW_U, "v": ARROW_V, "full_v": ARROW_FULL_V,
                      "width": ARROW_W, "height": ARROW_H},
            "flame": {"u": FLAME_U, "v": FLAME_V, "full_v": FLAME_FULL_V,
                      "width": FLAME_W, "height": FLAME_H},
        },
        "machines": {
            name: {
                "slots": layout["slots"],
                "arrow": layout["arrow"],
                **({"flame": layout["flame"]} if "flame" in layout else {}),
                **({"counter": layout["counter"]} if "counter" in layout else {}),
            }
            for name, layout in LAYOUTS.items()
        },
    }
    path = os.path.join("data", "gui_layout.json")
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    written.append(path)

    for entry in written:
        print("  " + entry)
    print("%d interfaces dessinees" % len(LAYOUTS))


if __name__ == "__main__":
    main()
