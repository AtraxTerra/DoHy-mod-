#!/usr/bin/env python3
"""Verifie la chaine d'assets du namespace palamod.

Pourquoi ce script existe : **aucune** des fautes qu'il cherche ne fait echouer
la compilation. Un modele qui pointe sur une texture absente donne du violet,
un blockstate qui nomme un modele inexistant donne un bloc invisible, un item
sans definition donne une icone violette, une texture 15x15 part dans l'atlas
sans un mot. Toutes se voient en jeu, des semaines plus tard, sur un objet
qu'il faut d'abord pouvoir obtenir.

Il remonte la chaine comme le jeu la remonte :

    enregistrement Java
      -> blockstate -> modele de bloc -> texture
      -> definition d'item -> modele d'item -> texture
      -> table de butin

    python3 tools/check_assets.py

Code retour 1 et une ligne ::error:: par probleme, lisible par GitHub Actions.
"""
import json
import pathlib
import re
import struct
import sys

NS = "palamod"
RES = pathlib.Path("src/main/resources")
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS
JAVA = pathlib.Path("src/main/java/fr/atraxterra/dohy/palamod")

EXPECTED_PNG = (16, 16)

# Les fabriques de PalaItems, PalaBlocks et PalaCrops, en plus des methodes de
# NeoForge. Un nom ajoute ici doit l'etre aussi dans BLOCK_FACTORIES s'il
# enregistre un bloc.
ITEM_FACTORIES = ("registerSimpleItem", "registerItem", "simple", "seed", "planter")
BLOCK_FACTORIES = ("registerSimpleBlock", "registerBlock", "ore", "storage", "crop", "soil")

# Les blocs sans item : on les obtient en semant, comme le ble vanilla. Ils
# n'ont donc ni definition d'item ni modele d'item, et c'est normal.
ITEMLESS = ("crop",)

errors = []


def fail(message):
    errors.append(message)


def png_size(path):
    head = path.read_bytes()[:24]
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def resolve(reference, kind):
    """« palamod:block/x » -> le chemin du fichier, ou None si c'est du vanilla.

    Les references minecraft: ne sont pas verifiables ici — le jar vanilla n'est
    pas dans le depot — et sont donc ignorees volontairement.
    """
    if ":" not in reference:
        reference = "minecraft:" + reference
    namespace, path = reference.split(":", 1)
    if namespace != NS:
        return None
    if kind == "model":
        return ASSETS / "models" / (path + ".json")
    return ASSETS / "textures" / (path + ".png")


def registered():
    """Les noms enregistres dans le source Java, par famille."""
    # rglob et non glob : les machines vivent dans un sous-paquet, et ne pas
    # descendre y rendait tous leurs assets « morts » a tort.
    source = "\n".join(p.read_text(encoding="utf-8") for p in sorted(JAVA.rglob("*.java")))
    items, blocks, itemless = set(), set(), set()
    for factory in ITEM_FACTORIES:
        items |= set(re.findall(rf'\b{factory}\(\s*"([a-z0-9_]+)"', source))
    for factory in BLOCK_FACTORIES:
        found = set(re.findall(rf'\b{factory}\(\s*"([a-z0-9_]+)"', source))
        blocks |= found
        if factory in ITEMLESS:
            itemless |= found
    # Un bloc n'est pas un item : son BlockItem est enregistre par boucle, pas
    # par un appel nomme, donc il n'apparait pas dans items.
    return items - blocks, blocks, itemless


def check_textures():
    for folder in ("item", "block"):
        directory = ASSETS / "textures" / folder
        if not directory.is_dir():
            continue
        for png in sorted(directory.glob("*.png")):
            size = png_size(png)
            if size is None:
                fail(f"{folder}/{png.name} : ce n'est pas un PNG")
            elif size != EXPECTED_PNG:
                fail(f"{folder}/{png.name} : {size[0]}x{size[1]} au lieu de "
                     f"{EXPECTED_PNG[0]}x{EXPECTED_PNG[1]} — homonyme pris dans le jar ? "
                     "verifier le sous-dossier")


def check_models():
    """Chaque modele doit pointer sur des textures qui existent."""
    for model in sorted((ASSETS / "models").rglob("*.json")):
        try:
            payload = json.loads(model.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            fail(f"models/{model.name} : JSON illisible ({error})")
            continue
        relative = model.relative_to(ASSETS / "models")
        parent = payload.get("parent")
        if parent:
            target = resolve(parent, "model")
            if target is not None and not target.exists():
                fail(f"models/{relative} : parent absent ({parent})")
        for slot, reference in (payload.get("textures") or {}).items():
            if not isinstance(reference, str) or reference.startswith("#"):
                continue
            target = resolve(reference, "texture")
            if target is not None and not target.exists():
                fail(f"models/{relative} : texture « {slot} » absente ({reference}) "
                     "— ce sera du violet en jeu")


def check_blockstates(blocks):
    for name in sorted(blocks):
        path = ASSETS / "blockstates" / f"{name}.json"
        if not path.exists():
            fail(f"{name} : bloc enregistre sans blockstates/{name}.json — invisible en jeu")
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            fail(f"blockstates/{name}.json : JSON illisible ({error})")
            continue
        for variant, definition in (payload.get("variants") or {}).items():
            entries = definition if isinstance(definition, list) else [definition]
            for entry in entries:
                reference = entry.get("model")
                if not reference:
                    fail(f"blockstates/{name}.json : variante « {variant} » sans modele")
                    continue
                target = resolve(reference, "model")
                if target is not None and not target.exists():
                    fail(f"blockstates/{name}.json : variante « {variant} » pointe sur "
                         f"un modele absent ({reference})")


def check_items(items, blocks, itemless):
    # Tout objet qui existe en inventaire a besoin d'une definition d'item en
    # plus de son modele : c'est la paire que 26.2 exige, et c'est la seconde
    # qui manque quand une icone est violette.
    for name in sorted(items | (blocks - itemless)):
        definition = ASSETS / "items" / f"{name}.json"
        if not definition.exists():
            fail(f"{name} : pas de assets/{NS}/items/{name}.json — icone violette")
            continue
        try:
            payload = json.loads(definition.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            fail(f"items/{name}.json : JSON illisible ({error})")
            continue
        reference = (payload.get("model") or {}).get("model")
        if not reference:
            fail(f"items/{name}.json : pas de modele declare")
            continue
        target = resolve(reference, "model")
        if target is not None and not target.exists():
            fail(f"items/{name}.json : pointe sur un modele absent ({reference})")

    # Dans l'autre sens : un asset sans enregistrement est un fichier mort.
    known = items | blocks
    for definition in sorted((ASSETS / "items").glob("*.json")):
        if definition.stem not in known:
            fail(f"{definition.stem} : definition d'item sans enregistrement Java "
                 "(fichier mort)")


def check_loot(blocks):
    for name in sorted(blocks):
        if not (DATA / "loot_table" / "blocks" / f"{name}.json").exists():
            fail(f"{name} : bloc sans table de butin, il ne lachera rien")


def check_lang(items, blocks):
    """Une cle de langue manquante affiche l'identifiant brut au joueur."""
    path = ASSETS / "lang" / "fr_fr.json"
    if not path.exists():
        fail("assets/%s/lang/fr_fr.json absent" % NS)
        return
    keys = set(json.loads(path.read_text(encoding="utf-8")))
    for name in sorted(items):
        if f"item.{NS}.{name}" not in keys:
            fail(f"{name} : pas de nom francais (item.{NS}.{name})")
    for name in sorted(blocks):
        if f"block.{NS}.{name}" not in keys:
            fail(f"{name} : pas de nom francais (block.{NS}.{name})")


# ---------------------------------------------------------------------------
# Interfaces : le Java et le generateur doivent decrire la meme disposition.
#
# Les positions des cases vivent a deux endroits, et il n'y a pas moyen de
# faire autrement : le generateur les dessine dans la texture, le Java les
# declare au menu. Un decalage de deux pixels donne une interface ou les objets
# flottent a cote de leur creux, et absolument rien ne le signale — ni la
# compilation, ni le chargement, ni un demarrage reussi. C'est exactement le
# genre de faute que ce script existe pour attraper.
# ---------------------------------------------------------------------------

LAYOUT_JAVA = JAVA / "machine" / "MachineLayout.java"
MENU_JAVA = JAVA / "machine" / "MachineMenu.java"
SCREEN_JAVA = JAVA.parent / "client" / "MachineScreen.java"
GUI_MANIFEST = pathlib.Path("data") / "gui_layout.json"

# Le role note par le generateur dit si le shift-clic peut y deposer.
INPUT_ROLES = {"input", "fruit", "fuel"}

# Taille de reference des blits de MachineScreen.
GUI_PNG = (256, 256)


def java_ints(path):
    """Les constantes entieres d'un fichier, par nom."""
    text = path.read_text(encoding="utf-8")
    return {
        name: int(value)
        for name, value in re.findall(r"static final int (\w+)\s*=\s*(-?\d+);", text)
    }


def _resolve(token, constants, path):
    token = token.strip()
    if re.fullmatch(r"-?\d+", token):
        return int(token)
    if token in constants:
        return constants[token]
    fail(f"gui : {path.name} utilise {token}, introuvable comme constante entiere")
    return None


def _layout_source(text, name):
    """Le texte de l'initialiseur d'une constante MachineLayout."""
    start = text.find(f"MachineLayout {name} = new MachineLayout(")
    if start < 0:
        return None
    end = text.find("\n\n", start)
    return text[start:end if end > 0 else len(text)]


def check_gui():
    if not GUI_MANIFEST.exists():
        fail("data/gui_layout.json absent : lancer tools/gen_gui.py")
        return
    for path in (LAYOUT_JAVA, MENU_JAVA, SCREEN_JAVA):
        if not path.exists():
            fail(f"gui : {path} absent")
            return

    manifest = json.loads(GUI_MANIFEST.read_text(encoding="utf-8"))
    constants = java_ints(LAYOUT_JAVA)
    layout_text = LAYOUT_JAVA.read_text(encoding="utf-8")

    # -- Le panneau et l'inventaire du joueur
    panel = manifest["panel"]
    screen = java_ints(SCREEN_JAVA)
    for key, java_name in (("width", "PANEL_WIDTH"), ("height", "PANEL_HEIGHT")):
        if screen.get(java_name) != panel[key]:
            fail(f"gui : MachineScreen.{java_name}={screen.get(java_name)} "
                 f"mais le panneau genere fait {panel[key]} de {key}")
    menu = java_ints(MENU_JAVA)
    for key, java_name in (("x", "PLAYER_X"), ("y", "PLAYER_Y"), ("hotbar_y", "HOTBAR_Y")):
        if menu.get(java_name) != manifest["player"][key]:
            fail(f"gui : MachineMenu.{java_name}={menu.get(java_name)} "
                 f"mais les creux du joueur sont a {manifest['player'][key]}")

    # -- Les sprites, lus hors panneau dans la texture
    for sprite, prefix in (("arrow", "ARROW"), ("flame", "FLAME")):
        expected = manifest["sprites"][sprite]
        for key, suffix in (("u", "U"), ("v", "V"), ("full_v", "FULL_V"),
                            ("width", "W"), ("height", "H")):
            java_name = f"{prefix}_{suffix}"
            if constants.get(java_name) != expected[key]:
                fail(f"gui : MachineLayout.{java_name}={constants.get(java_name)} "
                     f"mais le sprite {sprite} genere a {key}={expected[key]}")

    # -- Chaque machine : ses cases, ses jauges, sa texture
    for name, expected in manifest["machines"].items():
        texture = ASSETS / "textures" / "gui" / f"{name}.png"
        if not texture.exists():
            fail(f"gui : {name} n'a pas de texture (textures/gui/{name}.png)")
        else:
            # MachineScreen passe 256x256 comme taille de reference a chaque
            # blit. Une texture d'une autre taille decalerait tous les sprites
            # sans erreur : le jeu lirait simplement au mauvais endroit.
            size = png_size(texture)
            if size != GUI_PNG:
                fail(f"gui : textures/gui/{name}.png fait {size} au lieu de "
                     f"{GUI_PNG} — MachineScreen.TEXTURE_SIZE suppose "
                     f"{GUI_PNG[0]}")

        source = _layout_source(layout_text, name.upper())
        if source is None:
            fail(f"gui : MachineLayout n'a pas de constante {name.upper()}")
            continue

        # La taille de l'inventaire doit egaler le nombre de cases du menu.
        #
        # C'est l'invariant dont la violation a coute une session de debogage :
        # un menu de 9 cases sur un inventaire de 7 jette « Container size 7 is
        # smaller than expected 9 » a l'ouverture — et le jeu SUPPRIME cette
        # erreur (« Failed to handle packet ... suppressing error »). Le joueur
        # voit un clic droit sans effet, sans un mot dans le chat. 183 fois dans
        # le log, et rien a l'ecran.
        entity = JAVA / "machine" / (name.capitalize() + "BlockEntity.java")
        if not entity.exists():
            fail(f"gui : {name} n'a pas de {entity.name} — la convention de nom "
                 "a change, ce controle ne verifie plus rien")
        else:
            declared_size = java_ints(entity).get("SLOT_COUNT")
            if declared_size != len(expected["slots"]):
                fail(f"gui : {entity.name} declare SLOT_COUNT={declared_size} mais "
                     f"l'interface a {len(expected['slots'])} cases. L'ouverture "
                     "echouera en silence, l'erreur etant supprimee par le jeu")

        slots = re.findall(r"new SlotPos\((-?\d+),\s*(-?\d+),\s*(true|false)\)", source)
        if len(slots) != len(expected["slots"]):
            fail(f"gui : {name} a {len(slots)} cases en Java et "
                 f"{len(expected['slots'])} dans la texture")
        for index, (java_slot, generated) in enumerate(zip(slots, expected["slots"])):
            x, y, is_input = int(java_slot[0]), int(java_slot[1]), java_slot[2] == "true"
            if (x, y) != (generated["x"], generated["y"]):
                fail(f"gui : {name} case {index} declaree en ({x},{y}) "
                     f"mais le creux est dessine en ({generated['x']},{generated['y']})")
            if is_input != (generated["role"] in INPUT_ROLES):
                fail(f"gui : {name} case {index} est '{generated['role']}' pour le "
                     f"generateur mais input={is_input} en Java")

        sprites = re.findall(r"new Sprite\(([^)]*)\)", source)
        declared = {}
        for raw in sprites:
            parts = [part.strip() for part in raw.split(",")]
            x = _resolve(parts[0], constants, LAYOUT_JAVA)
            y = _resolve(parts[1], constants, LAYOUT_JAVA)
            vertical = parts[7] == "true"
            declared["flame" if parts[2].startswith("FLAME") else "arrow"] = (x, y, vertical)

        for sprite in ("arrow", "flame"):
            generated = expected.get(sprite)
            if (generated is None) != (sprite not in declared):
                fail(f"gui : {name} a un {sprite} d'un seul cote "
                     f"(Java: {sprite in declared}, texture: {generated is not None})")
                continue
            if generated is None:
                continue
            x, y, vertical = declared[sprite]
            if (x, y) != (generated["x"], generated["y"]):
                fail(f"gui : {name} {sprite} declare en ({x},{y}) mais dessine en "
                     f"({generated['x']},{generated['y']})")
            if "vertical" in generated and vertical != generated["vertical"]:
                fail(f"gui : {name} {sprite} vertical={vertical} en Java mais "
                     f"{generated['vertical']} dans la texture")

        # Le compteur de lot : meme regle que les cases, il est positionne des
        # deux cotes. Ici le generateur ne dessine rien — il reserve la place —
        # donc un desaccord ecrirait le chiffre par-dessus une case.
        text_pos = re.search(r"new TextPos\((-?\d+),\s*(-?\d+)\)", source)
        generated = expected.get("counter")
        if (generated is None) != (text_pos is None):
            fail(f"gui : {name} a un compteur d'un seul cote "
                 f"(Java: {text_pos is not None}, manifeste: {generated is not None})")
        elif generated is not None:
            x, y = int(text_pos.group(1)), int(text_pos.group(2))
            if (x, y) != (generated["x"], generated["y"]):
                fail(f"gui : {name} compteur declare en ({x},{y}) mais reserve en "
                     f"({generated['x']},{generated['y']})")


def main():
    items, blocks, itemless = registered()

    if not items and not blocks:
        fail("aucun objet palamod trouve dans le source : les motifs de ce script "
             "ne correspondent plus aux fabriques (voir ITEM_FACTORIES / BLOCK_FACTORIES)")

    check_textures()
    check_models()
    check_blockstates(blocks)
    check_items(items, blocks, itemless)
    check_loot(blocks)
    check_lang(items, blocks)
    check_gui()

    if errors:
        for message in errors:
            print(f"::error::{message}")
        print(f"\n{len(errors)} probleme(s) dans la chaine d'assets palamod.", file=sys.stderr)
        return 1

    print(f"chaine d'assets palamod verifiee : {len(blocks)} blocs "
          f"(dont {len(itemless)} sans item), {len(items)} items, "
          f"blockstates, modeles, textures, butin, noms francais "
          "et dispositions d'interface coherentes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
