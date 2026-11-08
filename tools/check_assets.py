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
ITEM_FACTORIES = ("registerSimpleItem", "registerItem", "simple", "seed")
BLOCK_FACTORIES = ("registerSimpleBlock", "registerBlock", "ore", "storage", "crop")

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
    source = "\n".join(p.read_text(encoding="utf-8") for p in sorted(JAVA.glob("*.java")))
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

    if errors:
        for message in errors:
            print(f"::error::{message}")
        print(f"\n{len(errors)} probleme(s) dans la chaine d'assets palamod.", file=sys.stderr)
        return 1

    print(f"chaine d'assets palamod verifiee : {len(blocks)} blocs "
          f"(dont {len(itemless)} sans item), {len(items)} items, "
          f"blockstates, modeles, textures, butin et noms francais complets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
