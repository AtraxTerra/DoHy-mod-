#!/usr/bin/env python3
"""Verifie le parcours NTM (data/ntm_path.json).

Une faute ici ne casse pas la compilation : une etape dont l'objet n'existe
pas ne se valide jamais, et le joueur reste bloque sans savoir pourquoi. Ce
script attrape avant la compilation ce qui se voit dans le fichier seul :

  - textes sans accents (les polices du kit n'en ont pas ; « · » est permis) ;
  - identifiants d'etapes et de chapitres uniques et bien formes ;
  - objectifs connus (have, near), quantites et recompenses positives ;
  - titres et libelles courts (ils se coupent sinon), mode d'emploi non vide ;
  - recompenses en objets de mods connus.

Avec le dossier data de NTM NEXT en argument, il verifie aussi que chaque
objet nomme existe dans cette version du mod, et qu'une machine a poser est
bien un bloc. Les donnees de NTM sont lues comme des donnees, jamais
executees.

Usage :
    python3 -I tools/check_ntm_path.py
    python3 -I tools/check_ntm_path.py <dossier data de NTM NEXT>
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PATH = ROOT / "data" / "ntm_path.json"

ID = re.compile(r"^[a-z0-9_]+$")
ITEM = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
GOALS = {"have", "near"}
NAMESPACES = {"hbm", "minecraft", "dohy", "palamod"}
TITLE_CHARS = 22
LABEL_CHARS = 32
ALLOWED = {"·", "«", "»", "—"}


def strings(node, where):
    if isinstance(node, str):
        yield where, node
    elif isinstance(node, list):
        for index, child in enumerate(node):
            yield from strings(child, f"{where}[{index}]")
    elif isinstance(node, dict):
        for key, child in node.items():
            yield from strings(child, f"{where}.{key}")


def ntm_assets(data_dir):
    """Les objets et les blocs de NTM, d'apres ses ressources (assets a cote de data)."""
    assets = Path(data_dir).resolve().parent / "assets" / "hbm"
    items = {p.stem for p in (assets / "items").glob("*.json")}
    blocks = {p.stem for p in (assets / "blockstates").glob("*.json")}
    if not items:
        return None, None
    return items, blocks


def main(argv):
    errors = []
    try:
        book = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"parcours NTM illisible : {error}")
        return 1

    for where, text in strings(book, "parcours"):
        bad = sorted({ch for ch in text if ord(ch) > 127 and ch not in ALLOWED})
        if bad:
            errors.append(f"{where} : caracteres hors police {''.join(bad)!r}")

    items = blocks = None
    if len(argv) > 1:
        items, blocks = ntm_assets(argv[1])
        if items is None:
            errors.append(f"{argv[1]} : pas d'assets/hbm/items a cote de ce dossier data")

    radius = book.get("radius", 8)
    if not isinstance(radius, int) or not 1 <= radius <= 16:
        errors.append(f"rayon {radius!r} hors de 1..16")

    chapters = book.get("chapters", [])
    if not chapters:
        errors.append("aucun chapitre")
    chapter_ids = set()
    step_ids = set()
    steps = 0
    money = 0
    for chapter in chapters:
        cid = chapter.get("id", "")
        if not ID.match(cid):
            errors.append(f"chapitre {cid!r} : identifiant mal forme")
        if cid in chapter_ids:
            errors.append(f"chapitre {cid!r} en double")
        chapter_ids.add(cid)
        if not chapter.get("name"):
            errors.append(f"chapitre {cid} : nom vide")
        if not re.match(r"^[0-9A-Fa-f]{6}$", chapter.get("color", "22E558")):
            errors.append(f"chapitre {cid} : couleur attendue en RRGGBB")
        if not chapter.get("steps"):
            errors.append(f"chapitre {cid} : aucune etape")
        for step in chapter.get("steps", []):
            steps += 1
            sid = step.get("id", "")
            where = f"etape {sid}"
            if not ID.match(sid):
                errors.append(f"{where} : identifiant mal forme")
            if sid in step_ids:
                errors.append(f"{where} : en double")
            step_ids.add(sid)
            title = step.get("title", "")
            if not title:
                errors.append(f"{where} : titre vide")
            elif len(title) > TITLE_CHARS:
                errors.append(f"{where} : titre trop long ({len(title)} > {TITLE_CHARS}) : {title!r}")
            if not ITEM.match(step.get("icon", "")):
                errors.append(f"{where} : icone mal formee {step.get('icon')!r}")
            if not step.get("text"):
                errors.append(f"{where} : mode d'emploi vide")

            goal = step.get("goal", {})
            kind = goal.get("type")
            if kind not in GOALS:
                errors.append(f"{where} : objectif inconnu {kind!r} (connus : {', '.join(sorted(GOALS))})")
            label = goal.get("label", "")
            if not label:
                errors.append(f"{where} : objectif sans libelle")
            elif len(label) > LABEL_CHARS:
                errors.append(f"{where} : libelle trop long ({len(label)} > {LABEL_CHARS}) : {label!r}")
            targets = goal.get("items", [])
            if not targets:
                errors.append(f"{where} : objectif sans objet")
            count = goal.get("count", 1)
            if not isinstance(count, int) or count < 1:
                errors.append(f"{where} : quantite {count!r}")
            if kind == "near" and (goal.get("all") or count != 1):
                errors.append(f"{where} : une machine a poser n'a ni quantite ni 'all'")
            for target in targets:
                if not ITEM.match(target):
                    errors.append(f"{where} : objet mal forme {target!r}")
                    continue
                namespace, name = target.split(":", 1)
                if namespace != "hbm":
                    errors.append(f"{where} : {target} n'est pas un objet de NTM")
                elif items is not None:
                    if name not in items:
                        errors.append(f"{where} : {target} n'existe pas dans cette version de NTM")
                    if kind == "near" and name not in blocks:
                        errors.append(f"{where} : {target} n'est pas un bloc, il ne se pose pas")

            icon = step.get("icon", "")
            if items is not None and icon.startswith("hbm:") and icon.split(":", 1)[1] not in items:
                errors.append(f"{where} : icone {icon} inconnue de NTM")

            reward = step.get("reward", {})
            amount = reward.get("money", 0)
            if not isinstance(amount, int) or amount < 0:
                errors.append(f"{where} : recompense {amount!r}")
            else:
                money += amount
            for prize in reward.get("items", []):
                pid = prize.get("id", "")
                if not ITEM.match(pid) or pid.split(":", 1)[0] not in NAMESPACES:
                    errors.append(f"{where} : objet de recompense {pid!r}")
                elif items is not None and pid.startswith("hbm:") and pid.split(":", 1)[1] not in items:
                    errors.append(f"{where} : objet de recompense {pid} inconnu de NTM")
                if not isinstance(prize.get("count", 1), int) or prize.get("count", 1) < 1:
                    errors.append(f"{where} : quantite de recompense {prize.get('count')!r}")

    if errors:
        for error in errors:
            print(error)
        print(f"{len(errors)} probleme(s) dans le parcours NTM")
        return 1
    checked = " et les objets de NTM" if items is not None else ""
    print(f"parcours NTM : {steps} etapes en {len(chapters)} chapitres, {money} $ de recompenses, "
          f"fichier{checked} en ordre")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
