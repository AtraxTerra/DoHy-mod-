#!/usr/bin/env python3
"""Verifie l'encyclopedie (data/encyclopedia.json) contre le code du mod.

Une fiche qui promet sans tenir se voit en jeu : un voisin qui n'ouvre rien,
une icone en livre, une commande inconnue, une fiche qui ne se decouvre
jamais. Ce script attrape tout cela avant la compilation.

Controles :
  - textes sans accents (les polices du kit n'en ont pas ; « · » est permis) ;
  - identifiants de fiches et de categories uniques, categories connues ;
  - voisins (``see``) existants, sans doublon ni renvoi a soi ;
  - objets (icones, decouverte) : ceux de dohy et palamod existent dans
    assets/<ns>/items, les tags dans data/<ns>/tags/item ;
  - plans connus de Blueprints.java, menus (``views``) connus du code,
    dimensions connues du datapack, cles de progression connues
    d'EncyclopediaProgress.java ;
  - commandes « /racine ... : description » dont la racine est enregistree ;
  - lignes de carte courtes (elles se coupent sinon), resume non vide.

Usage : python3 -I tools/check_encyclopedia.py
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BOOK = ROOT / "data" / "encyclopedia.json"
JAVA = ROOT / "src" / "main" / "java"
RESOURCES = ROOT / "src" / "main" / "resources"

CARD_LINES = 2
CARD_CHARS = 30
ID = re.compile(r"^[a-z0-9_]+$")
ITEM = re.compile(r"^#?[a-z0-9_.-]+:[a-z0-9_./-]+$")
VANILLA_DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}
MOD_NAMESPACES = {"dohy", "palamod"}


def java_text():
    return "\n".join(path.read_text(encoding="utf-8") for path in sorted(JAVA.rglob("*.java")))


def known_views(source):
    views = set(re.findall(r'super\("([a-z0-9_]+)"', source))
    views |= set(re.findall(r'UiNet\.id\("([a-z0-9_/]+)"\)', source))
    return {"dohy:" + view for view in views}


def known_plans():
    text = (JAVA / "fr/atraxterra/dohy/guide/plan/Blueprints.java").read_text(encoding="utf-8")
    return set(re.findall(r'new Blueprint\("([a-z0-9_]+)"', text))


def known_progress():
    text = (JAVA / "fr/atraxterra/dohy/guide/EncyclopediaProgress.java").read_text(encoding="utf-8")
    keys = set(re.findall(r'case "([a-z0-9_:]+)"\s*->', text))
    return keys


JOBS = {"miner", "hunter", "farmer", "alchemist"}


def known_roots(source):
    roots = set(re.findall(r'register\(\s*(?:Commands\.literal|[A-Za-z_]+)\("([a-z_]+)"', source))
    roots |= set(re.findall(r'(?:return|=)\s*Commands\.literal\("([a-z_]+)"\)', source))
    return roots


def known_dimensions():
    out = set(VANILLA_DIMENSIONS)
    for path in RESOURCES.glob("data/*/dimension/*.json"):
        out.add(path.parent.parent.name + ":" + path.stem)
    return out


def item_problem(value):
    if not ITEM.match(value):
        return "identifiant mal forme"
    tag = value.startswith("#")
    namespace, path = value.lstrip("#").split(":", 1)
    if namespace == "minecraft" or namespace == "c":
        return None
    if namespace not in MOD_NAMESPACES:
        return "espace de noms inconnu"
    if tag:
        if not (RESOURCES / "data" / namespace / "tags" / "item" / (path + ".json")).is_file():
            return "tag introuvable"
        return None
    if not (RESOURCES / "assets" / namespace / "items" / (path + ".json")).is_file():
        return "objet introuvable"
    return None


def strings(node, where):
    if isinstance(node, str):
        yield where, node
    elif isinstance(node, list):
        for index, child in enumerate(node):
            yield from strings(child, f"{where}[{index}]")
    elif isinstance(node, dict):
        for key, child in node.items():
            yield from strings(child, f"{where}.{key}")


def main():
    errors = []
    try:
        book = json.loads(BOOK.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"encyclopedie illisible : {error}")
        return 1

    for where, text in strings(book, "book"):
        bad = sorted({ch for ch in text if ord(ch) > 127 and ch != "·"})
        if bad:
            errors.append(f"{where} : caracteres hors police {''.join(bad)!r}")

    source = java_text()
    views = known_views(source)
    plans = known_plans()
    progress = known_progress()
    roots = known_roots(source)
    dimensions = known_dimensions()

    categories = {}
    for category in book.get("categories", []):
        cid = category.get("id", "")
        if not ID.match(cid):
            errors.append(f"categorie {cid!r} : identifiant mal forme")
        if cid in categories:
            errors.append(f"categorie {cid!r} en double")
        categories[cid] = category
        if not re.match(r"^[0-9A-Fa-f]{6}$", category.get("color", "")):
            errors.append(f"categorie {cid!r} : couleur attendue en RRGGBB")
        problem = item_problem(category.get("icon", ""))
        if problem:
            errors.append(f"categorie {cid!r} : icone {category.get('icon')!r} : {problem}")

    entries = book.get("entries", [])
    ids = {}
    for entry in entries:
        eid = entry.get("id", "")
        if not ID.match(eid):
            errors.append(f"fiche {eid!r} : identifiant mal forme")
        if eid in ids:
            errors.append(f"fiche {eid!r} en double")
        ids[eid] = entry

    used = set()
    for entry in entries:
        eid = entry["id"]
        if entry.get("category") not in categories:
            errors.append(f"{eid} : categorie inconnue {entry.get('category')!r}")
        used.add(entry.get("category"))
        problem = item_problem(entry.get("icon", ""))
        if problem:
            errors.append(f"{eid} : icone {entry.get('icon')!r} : {problem}")
        if not entry.get("title"):
            errors.append(f"{eid} : titre vide")
        card = entry.get("card", [])
        if len(card) > CARD_LINES:
            errors.append(f"{eid} : carte de {len(card)} lignes (au plus {CARD_LINES})")
        for line in card:
            if len(line) > CARD_CHARS:
                errors.append(f"{eid} : ligne de carte trop longue ({len(line)} > {CARD_CHARS}) : {line!r}")
        if not entry.get("summary"):
            errors.append(f"{eid} : resume vide")
        for section in entry.get("sections", []):
            if not section.get("text"):
                errors.append(f"{eid} : section {section.get('title')!r} vide")

        see = entry.get("see", [])
        if len(see) != len(set(see)):
            errors.append(f"{eid} : voisin en double")
        for other in see:
            if other == eid:
                errors.append(f"{eid} : renvoie a elle-meme")
            elif other not in ids:
                errors.append(f"{eid} : voisin inconnu {other!r}")

        for plan in entry.get("plans", []):
            if plan not in plans:
                errors.append(f"{eid} : plan inconnu {plan!r} (connus : {', '.join(sorted(plans))})")

        key = entry.get("progress", "")
        if key:
            if key.startswith("metier:"):
                if key.split(":", 1)[1] not in JOBS:
                    errors.append(f"{eid} : metier inconnu {key!r}")
            elif key not in progress:
                errors.append(f"{eid} : progression inconnue {key!r} (connues : {', '.join(sorted(progress))})")

        for command in entry.get("commands", []):
            if " : " not in command:
                errors.append(f"{eid} : commande sans « : description » : {command!r}")
                continue
            what = command.split(" : ", 1)[0]
            if what.startswith("/"):
                root = what[1:].split(" ", 1)[0]
                if root not in roots:
                    errors.append(f"{eid} : commande inconnue /{root}")

        discover = entry.get("discover", {})
        for item in discover.get("items", []):
            problem = item_problem(item)
            if problem:
                errors.append(f"{eid} : objet de decouverte {item!r} : {problem}")
        for dimension in discover.get("dimensions", []):
            if dimension not in dimensions:
                errors.append(f"{eid} : dimension inconnue {dimension!r}")
        for view in discover.get("views", []):
            if view not in views:
                errors.append(f"{eid} : menu inconnu {view!r}")

    for cid in categories:
        if cid not in used:
            errors.append(f"categorie {cid!r} sans fiche")

    if errors:
        for error in errors:
            print(error)
        print(f"{len(errors)} probleme(s) dans l'encyclopedie")
        return 1
    print(f"encyclopedie : {len(entries)} fiches en {len(categories)} categories, tout est en ordre")
    return 0


if __name__ == "__main__":
    sys.exit(main())
