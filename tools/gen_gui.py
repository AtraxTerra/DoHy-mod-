#!/usr/bin/env python3
"""Dessine les fonds d'interface des machines, dans le style de Paladium.

Paladium ne dessinait pas ses interfaces avec des images de fond : sa
bibliotheque Paladium-UI (sur Zephyr-UI) les composait par code, en aplats
sombres, avec quelques elements (fleche, coins colores par metal, silhouettes
« fantomes » dans les cases d'entree). Il n'y a donc pas de fond a convertir.

On redessine donc dans SA palette, relevee au bytecode dans
`fr.paladium.paladiumui.kit.constant.ColorConstant` :

    BACKGROUND        (30, 29, 35)     le fond des panneaux
    LIGHT_BACKGROUND  (64, 64, 77)     les cases
    PRIMARY           (239, 57, 38)    le rouge-orange Paladium
    BLACK             (15, 15, 18)
    LIGHT_GRAY        (152, 152, 152)  le texte secondaire

La premiere version reprenait le gris des coffres vanilla : lisible, mais
etranger a Paladium, et avec deux defauts de disposition — la case de charbon du
crusher mordait sur le libelle « Inventaire », et la fleche du cobblebreaker
etait decentree de 4,5 px. Le crusher reprend maintenant la geometrie exacte du
fourneau vanilla, que tout joueur lit sans y penser.

    python3 tools/gen_gui.py [<pack officiel extrait> [<jar PalaMod extrait>]]

Le pack et le jar ne servent qu'aux silhouettes fantomes (la pierre du
cobblebreaker, le tourbillon de la voidstone). Sans eux, les cases restent
vides — c'est signale, pas tu.

LA DISPOSITION DES SLOTS EST LA SOURCE UNIQUE. Elle est aussi emise dans
`data/gui_layout.json`, que `check_assets.py` compare aux constantes du code
Java : un slot dessine a un endroit et declare a un autre donne une interface ou
les objets flottent a cote de leur case, et rien ne le signale.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

RES = "src/main/resources"
NS = "palamod"

# Palette de Paladium-UI (ColorConstant), plus deux nuances derivees pour le
# relief des cases.
BACKGROUND = (30, 29, 35, 255)
LIGHT_BACKGROUND = (64, 64, 77, 255)
PRIMARY = (239, 57, 38, 255)
BLACK = (15, 15, 18, 255)
LIGHT_GRAY = (152, 152, 152, 255)
WHITE = (255, 255, 255, 255)
BORDER = (46, 45, 54, 255)          # contour du panneau, separateurs
SLOT_FILL = (42, 41, 50, 255)       # le creux d'une case
SLOT_EDGE = (64, 64, 77, 255)       # LIGHT_BACKGROUND, en liseré
TRANSPARENT = (0, 0, 0, 0)

# Coins de couleur par metal, comme ceux de gui/crusher/*_corner.png. Les quatre
# premiers sont pris sur ces textures ; fer, or et diamant sur les lingots.
METAL = {
    "iron": (216, 216, 216, 255),
    "gold": (245, 211, 63, 255),
    "diamond": (77, 232, 224, 255),
    "amethyst": (166, 77, 255, 255),
    "titane": (127, 127, 127, 255),
    "paladium": (239, 57, 38, 255),
    "endium": (77, 121, 255, 255),
    "green_paladium": (37, 226, 81, 255),
}

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
        # Une entree en haut au centre, six sorties sur une rangee en dessous,
        # rangees du metal le plus courant au plus rare — l'ordre ou elles se
        # remplissent. Chaque sortie porte le coin de couleur de son metal.
        "slots": [{"x": 80, "y": 18, "role": "input", "ghost": "cobblestone"}] + [
            {"x": 34 + 18 * index, "y": 54, "role": "output", "metal": metal}
            for index, metal in enumerate(
                ("iron", "gold", "diamond", "amethyst", "titane", "paladium"))
        ],
        "arrow": {"x": 76, "y": 36, "vertical": True},
    },
    "crusher": {
        # La geometrie exacte du fourneau vanilla : matiere en haut, combustible
        # en bas, flamme entre les deux, fleche, resultat. Un joueur la lit sans
        # explication, et le crusher fait la meme chose.
        "slots": [
            {"x": 56, "y": 17, "role": "fruit", "ghost": "fruit"},
            {"x": 56, "y": 53, "role": "fuel", "ghost": "fuel"},
            {"x": 116, "y": 35, "role": "output", "big": True},
        ],
        "arrow": {"x": 79, "y": 34, "vertical": False},
        "flame": {"x": 57, "y": 37},
        # Le compteur de fruits broyes, sous la fleche : jusqu'a 128 pour
        # l'orangeblue, et c'est le seul etat de la machine qu'on ne voit pas
        # autrement.
        "counter": {"x": 91, "y": 58},
    },
    "voidstone": {
        # Une seule case, au centre : tout ce qu'on y pose disparait. Le
        # tourbillon de gui/void_slot_background.png dit ce qu'elle fait.
        "slots": [{"x": 80, "y": 35, "role": "input", "ghost": "void", "big": True}],
    },
    "palafurnace": {
        # PaladiumFurnaceContainer : le fourneau vanilla, plus la case des
        # ameliorations de fourneau a gauche, qui en accepte seize.
        "slots": [
            {"x": 56, "y": 17, "role": "input"},
            {"x": 56, "y": 53, "role": "fuel", "ghost": "fuel"},
            {"x": 116, "y": 35, "role": "output", "big": True},
            {"x": 19, "y": 35, "role": "upgrade", "ghost": "upgrade"},
        ],
        "arrow": {"x": 79, "y": 34, "vertical": False},
        "flame": {"x": 57, "y": 37},
    },
    "palaforge": {
        # ForgeContainer : la piece a recycler en haut, les lingots en bas, la
        # fleche descend de l'une a l'autre ; le combustible a droite sous sa
        # flamme. La sortie descend jusqu'a y 74 : pas de cadre de 26 px ni de
        # separateur, qui couperaient la case.
        "slots": [
            {"x": 58, "y": 15, "role": "equipment", "ghost": "equipment"},
            {"x": 58, "y": 58, "role": "output"},
            {"x": 115, "y": 45, "role": "fuel", "ghost": "fuel"},
        ],
        "arrow": {"x": 54, "y": 36, "vertical": True},
        "flame": {"x": 116, "y": 27},
        "separator": False,
    },
    "grinder": {
        # GrinderContainer, resserre dans un panneau vanilla : la forge en haut
        # (pattern ou tete, puis socle ou baton, la fleche, le resultat), les
        # modificateurs en bas (modificateur, la seconde fleche, l'outil qu'il
        # ameliore sur place), le paladium a droite — sa case, sa jauge, et
        # « x/100 » dessous, comme l'original l'ecrivait sous son reservoir.
        "slots": [
            {"x": 78, "y": 18, "role": "output"},
            {"x": 8, "y": 18, "role": "input"},
            {"x": 26, "y": 18, "role": "input"},
            {"x": 8, "y": 50, "role": "upgrade", "ghost": "upgrade"},
            {"x": 60, "y": 50, "role": "equipment", "ghost": "equipment"},
            {"x": 120, "y": 18, "role": "fuel", "ghost": "fuel"},
        ],
        "arrow": {"x": 48, "y": 18, "vertical": False},
        "arrow2": {"x": 30, "y": 50, "vertical": False},
        "flame": {"x": 121, "y": 38},
        "counter": {"x": 128, "y": 56},
    },
    "food_machine": {
        # TileEntityFoodMachine : la lave sous sa jauge, les trois ingredients
        # en colonne (fibres, mineraux, proteines), la cuisson, quatre sorties.
        "slots": [
            {"x": 8, "y": 53, "role": "fuel", "ghost": "lava"},
            {"x": 44, "y": 17, "role": "input", "ghost": "fibers"},
            {"x": 44, "y": 35, "role": "input", "ghost": "minerals"},
            {"x": 44, "y": 53, "role": "input", "ghost": "protein"},
            {"x": 98, "y": 26, "role": "output"},
            {"x": 116, "y": 26, "role": "output"},
            {"x": 98, "y": 44, "role": "output"},
            {"x": 116, "y": 44, "role": "output"},
        ],
        "arrow": {"x": 68, "y": 35, "vertical": False},
        "flame": {"x": 9, "y": 35},
    },
    "palamachine": {
        # PaladiumMachineContainer, resserre : la piece a transformer au centre
        # d'un quinconce, les quatre ingredients aux coins (1 et 3 en haut, 2 et
        # 4 en bas, comme ils encadraient la piece dans l'original), la fleche
        # des 10 secondes, le resultat.
        "slots": [
            {"x": 44, "y": 35, "role": "input", "ghost": "stick"},
            {"x": 26, "y": 17, "role": "input"},
            {"x": 26, "y": 53, "role": "input"},
            {"x": 62, "y": 17, "role": "input"},
            {"x": 62, "y": 53, "role": "input"},
            {"x": 124, "y": 35, "role": "output", "big": True},
        ],
        "arrow": {"x": 89, "y": 34, "vertical": False},
    },
    "golem_box": {
        # TileEntityGuardianBox : trois mini golems, leurs croquettes dessous,
        # les 8 minutes de production, cinq sorties.
        "slots": [
            {"x": 44, "y": 17, "role": "equipment", "ghost": "mini_golem"},
            {"x": 62, "y": 17, "role": "equipment", "ghost": "mini_golem"},
            {"x": 80, "y": 17, "role": "equipment", "ghost": "mini_golem"},
            {"x": 44, "y": 53, "role": "input", "ghost": "croquette"},
            {"x": 62, "y": 53, "role": "input", "ghost": "croquette"},
            {"x": 80, "y": 53, "role": "input", "ghost": "croquette"},
            {"x": 128, "y": 17, "role": "output"},
            {"x": 146, "y": 17, "role": "output"},
            {"x": 128, "y": 35, "role": "output"},
            {"x": 146, "y": 35, "role": "output"},
            {"x": 128, "y": 53, "role": "output"},
        ],
        "arrow": {"x": 100, "y": 35, "vertical": False},
    },
}

# Les coffres en amethyste et en titane (ModdedChestContainer) : 12 x 9 cases,
# puis l'inventaire du joueur centre dessous. Ce ne sont pas des machines —
# PalaChestScreen a sa propre geometrie, reprise de PalaChestMenu — donc hors
# du manifeste.
# Le coffre en or d'Iron Chests a 9 colonnes : panneau de 186 px, l'inventaire
# recentre. Memes formules que ChestTier.panelWidth et playerX.
CHEST_HEIGHT = 256
CHEST_X, CHEST_Y = 12, 8
CHEST_ROWS = 9
CHEST_PLAYER_Y, CHEST_HOTBAR_Y = 174, 232
CHESTS = {"amethyst_chest": ("amethyst", 12), "titane_chest": ("titane", 12),
          "paladium_chest": ("paladium", 12), "green_paladium_chest": ("green_paladium", 12),
          "endium_chest": ("endium", 12), "gold_chest": ("gold", 9)}
# Les anneaux de reparation (ModdedChestLogic) : rangees reparees du haut, cases
# d'anneau dans une colonne a droite de la grille. Memes nombres que ChestTier ;
# memes formules que ChestTier.ringX, panelWidth et textureWidth.
CHEST_RINGS = {"amethyst_chest": (1, 1), "titane_chest": (1, 2), "paladium_chest": (2, 4),
               "green_paladium_chest": (6, 4), "endium_chest": (9, 4)}
RING_GAP = 6
RING_COLUMN = RING_GAP + 16

GHOSTS = {}


def load_ghosts(pack, jar):
    """Les silhouettes fantomes, a 30 % d'opacite et desaturees.

    Elles disent quoi poser sans qu'on lise une ligne : c'est le procede de
    Paladium (gui/crusher/plant_placeholder.png, fuel_placeholder.png). Les
    placeholders d'origine font 200x200 et sont lisses : reduits a 16x16 ils
    seraient flous. On prend donc les textures 16x16 des objets eux-memes.
    """
    sources = {
        "fruit": os.path.join(RES, "assets", NS, "textures", "item", "eggplant.png"),
        "fuel": os.path.join(RES, "assets", NS, "textures", "item", "gold_mixed_coal.png"),
        "upgrade": os.path.join(RES, "assets", NS, "textures", "item", "furnace_upgrade.png"),
        "equipment": os.path.join(RES, "assets", NS, "textures", "item", "paladium_pickaxe.png"),
        "croquette": os.path.join(RES, "assets", NS, "textures", "item", "croquette.png"),
        "mini_golem": os.path.join(RES, "assets", NS, "textures", "item", "mini_golem.png"),
        "stick": os.path.join(RES, "assets", NS, "textures", "item", "heal_stick.png"),
        "ring": os.path.join(RES, "assets", NS, "textures", "item", "small_ring.png"),
        "endium_ring": os.path.join(RES, "assets", NS, "textures", "item", "endium_small_ring.png"),
    }
    if pack:
        for ghost, item in (("lava", "lava_bucket"), ("fibers", "wheat"), ("minerals", "flint"),
                            ("protein", "beef")):
            sources[ghost] = os.path.join(pack, "assets", "minecraft", "textures", "item", item + ".png")
    if pack:
        sources["cobblestone"] = os.path.join(
            pack, "assets", "minecraft", "textures", "block", "cobblestone.png")
    if jar:
        sources["void"] = os.path.join(
            jar, "assets", NS, "textures", "gui", "void_slot_background.png")
    for name, path in sources.items():
        if not os.path.exists(path):
            print("  silhouette %s absente (%s)" % (name, path))
            continue
        image = Image.open(path).convert("RGBA")
        if image.size != (16, 16):
            image = image.resize((16, 16), Image.LANCZOS)
        pixels = image.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = pixels[x, y]
                grey = int(0.3 * r + 0.59 * g + 0.11 * b)
                pixels[x, y] = (grey, grey, grey, int(a * 0.30))
        GHOSTS[name] = image


def panel(draw, width, height, separator=True):
    """Le panneau : aplat sombre, contour discret, coins casses d'un pixel.

    Une barre PRIMARY sous l'en-tete, la signature des interfaces Paladium, et
    un separateur au-dessus de l'inventaire du joueur.
    """
    draw.rectangle([1, 0, width - 2, height - 1], fill=BACKGROUND)
    draw.rectangle([0, 1, width - 1, height - 2], fill=BACKGROUND)
    draw.line([1, 0, width - 2, 0], fill=BORDER)
    draw.line([1, height - 1, width - 2, height - 1], fill=BORDER)
    draw.line([0, 1, 0, height - 2], fill=BORDER)
    draw.line([width - 1, 1, width - 1, height - 2], fill=BORDER)
    # Barre d'accent sous le titre (le titre est ecrit par le jeu en y 6).
    draw.rectangle([7, 15, 40, 15], fill=PRIMARY)
    # Separateur au-dessus du libelle « Inventaire » (ecrit en y 72).
    if separator:
        draw.line([7, 69, width - 8, 69], fill=BORDER)


def slot(image, draw, x, y, entry=None):
    """Une case : creux sombre, liseré clair, et ses ornements.

    Le decalage de -1 est la convention vanilla : un slot declare en (x, y)
    contient son objet a cette position, et son cadre deborde d'un pixel autour.
    Une case de sortie principale (« big ») est entouree d'un cadre de 26x26,
    comme le resultat du fourneau.
    """
    entry = entry or {}
    left, top = x - 1, y - 1
    if entry.get("big"):
        draw.rectangle([left - 4, top - 4, left + 21, top + 21], outline=SLOT_EDGE)
    draw.rectangle([left, top, left + 17, top + 17], fill=SLOT_FILL, outline=SLOT_EDGE)
    # Ombre interieure d'un pixel en haut a gauche : la case se lit en creux.
    draw.line([left + 1, top + 1, left + 16, top + 1], fill=BLACK)
    draw.line([left + 1, top + 1, left + 1, top + 16], fill=BLACK)

    ghost = GHOSTS.get(entry.get("ghost"))
    if ghost is not None:
        image.alpha_composite(ghost, (x, y))

    metal = entry.get("metal")
    if metal:
        # Coin triangulaire en haut a droite, comme gui/crusher/*_corner.png :
        # la rarete se lit de gauche a droite sans ouvrir d'infobulle.
        right = left + 16
        draw.polygon([(right - 4, top + 1), (right, top + 1), (right, top + 5)], fill=METAL[metal])


def arrow(draw, u, v, filled, vertical):
    """Les deux etats de la fleche : liseré vide, puis blanche comme l'originale.

    gui/arrow_furnace_filler.png est blanc pur sur fond sombre : c'est la
    couleur de remplissage de Paladium, reprise telle quelle.
    """
    body = WHITE if filled else LIGHT_BACKGROUND
    if vertical:
        # Centree dans la boite de 24x17 : la premiere version la dessinait
        # autour de u + 7,5 et elle tombait 4,5 px a cote de la case d'entree.
        cx = u + 12
        draw.rectangle([cx - 2, v, cx + 1, v + 9], fill=body)
        draw.polygon([(cx - 6, v + 9), (cx + 5, v + 9), (cx, v + 15), (cx - 1, v + 15)], fill=body)
    else:
        cy = v + 8
        draw.rectangle([u, cy - 2, u + 14, cy + 1], fill=body)
        draw.polygon([(u + 14, cy - 7), (u + 14, cy + 6), (u + 22, cy), (u + 22, cy - 1)], fill=body)


def flame(image, u, v, filled):
    """La jauge de combustible : une flamme, en degrade quand elle brule.

    Le degrade reprend gui/crusher/buttons/gold_full.png, la barre de carburant
    du crusher d'origine : orange profond en bas, jaune en haut.
    """
    mask = Image.new("L", (14, 14), 0)
    ImageDraw.Draw(mask).polygon(
        [(7, 0), (10, 5), (12, 3), (13, 9), (11, 12), (7, 13), (3, 12), (1, 9), (2, 3), (4, 5)],
        fill=255)
    fill = Image.new("RGBA", (14, 14), LIGHT_BACKGROUND)
    if filled:
        pixels = fill.load()
        for y in range(14):
            t = y / 13.0
            # haut (t=0) : jaune ; bas (t=1) : orange profond
            r = int(255 * (1 - t) + 210 * t)
            g = int(214 * (1 - t) + 60 * t)
            b = int(40 * (1 - t) + 10 * t)
            for x in range(14):
                pixels[x, y] = (r, g, b, 255)
    image.paste(fill, (u, v), mask)


def build(name, layout):
    image = Image.new("RGBA", (TEXTURE, TEXTURE), TRANSPARENT)
    draw = ImageDraw.Draw(image)

    panel(draw, WIDTH, HEIGHT, layout.get("separator", True))

    for entry in layout["slots"]:
        slot(image, draw, entry["x"], entry["y"], entry)

    # Inventaire du joueur : trois rangees de neuf, puis la barre d'action.
    for row in range(3):
        for column in range(9):
            slot(image, draw, PLAYER_X + column * 18, PLAYER_Y + row * 18)
    for column in range(9):
        slot(image, draw, PLAYER_X + column * 18, HOTBAR_Y)

    if "arrow" in layout:
        vertical = layout["arrow"]["vertical"]
        arrow(draw, ARROW_U, ARROW_V, False, vertical)
        arrow(draw, ARROW_U, ARROW_FULL_V, True, vertical)
    if "flame" in layout:
        flame(image, FLAME_U, FLAME_V, False)
        flame(image, FLAME_U, FLAME_FULL_V, True)

    path = os.path.join(RES, "assets", NS, "textures", "gui", "%s.png" % name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    return path


def build_chest(name, metal, columns):
    """Le fond d'un coffre : la grille, l'inventaire, un coin du metal, et la
    colonne d'anneaux s'il en prend.

    La colonne elargit le panneau au-dela de 256 px : la texture s'elargit
    avec lui (ChestTier.textureWidth), et l'ecran passe sa largeur au blit.
    """
    repair_rows, rings = CHEST_RINGS.get(name, (0, 0))
    GRID_WIDTH = columns * 18 + 24
    CHEST_WIDTH = GRID_WIDTH + (RING_COLUMN if rings else 0)
    CHEST_PLAYER_X = (GRID_WIDTH - 9 * 18) // 2
    image = Image.new("RGBA", (max(TEXTURE, CHEST_WIDTH), TEXTURE), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 0, CHEST_WIDTH - 2, CHEST_HEIGHT - 1], fill=BACKGROUND)
    draw.rectangle([0, 1, CHEST_WIDTH - 1, CHEST_HEIGHT - 2], fill=BACKGROUND)
    draw.line([1, 0, CHEST_WIDTH - 2, 0], fill=BORDER)
    draw.line([1, CHEST_HEIGHT - 1, CHEST_WIDTH - 2, CHEST_HEIGHT - 1], fill=BORDER)
    draw.line([0, 1, 0, CHEST_HEIGHT - 2], fill=BORDER)
    draw.line([CHEST_WIDTH - 1, 1, CHEST_WIDTH - 1, CHEST_HEIGHT - 2], fill=BORDER)
    # Le titre est ecrit au-dessus du panneau : la barre d'accent prend la
    # couleur du metal, en haut a gauche, a la place de celle des machines.
    draw.rectangle([CHEST_X - 1, 3, CHEST_X + 32, 4], fill=METAL[metal])
    for row in range(CHEST_ROWS):
        for column in range(columns):
            slot(image, draw, CHEST_X + column * 18, CHEST_Y + row * 18)
    if rings:
        # Les rangees que les anneaux reparent : un filet du metal a leur
        # gauche, pour qu'on sache ou ranger ce qui doit l'etre.
        draw.rectangle([CHEST_X - 5, CHEST_Y - 1, CHEST_X - 4, CHEST_Y + repair_rows * 18 - 2],
                       fill=METAL[metal])
        ring_x = CHEST_X + columns * 18 + RING_GAP
        ghost = "endium_ring" if metal == "endium" else "ring"
        for ring in range(rings):
            slot(image, draw, ring_x, CHEST_Y + ring * 18, {"ghost": ghost, "metal": metal})
    for row in range(3):
        for column in range(9):
            slot(image, draw, CHEST_PLAYER_X + column * 18, CHEST_PLAYER_Y + row * 18)
    for column in range(9):
        slot(image, draw, CHEST_PLAYER_X + column * 18, CHEST_HOTBAR_Y)
    path = os.path.join(RES, "assets", NS, "textures", "gui", "%s.png" % name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    return path


def main():
    pack = sys.argv[1] if len(sys.argv) > 1 else None
    jar = sys.argv[2] if len(sys.argv) > 2 else None
    load_ghosts(pack, jar)

    written = []
    for name, layout in LAYOUTS.items():
        written.append(build(name, layout))
    for name, (metal, columns) in CHESTS.items():
        written.append(build_chest(name, metal, columns))

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
                # Le manifeste ne garde que ce que le Java declare : position et
                # role. Les ornements (silhouette, metal, cadre) sont du dessin.
                "slots": [{"x": e["x"], "y": e["y"], "role": e["role"]} for e in layout["slots"]],
                **({"arrow": layout["arrow"]} if "arrow" in layout else {}),
                **({"arrow2": layout["arrow2"]} if "arrow2" in layout else {}),
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
