#!/usr/bin/env python3
"""Verifie le pass mensuel (data/pass.json) contre le code du mod.

Un pass qui ne se finit pas, une quete qui ne compte jamais (type inconnu,
tag qui n'existe pas), une recompense oubliee : rien de cela ne fait d'erreur
au chargement, tout se voit au bout d'un mois. Ce script l'attrape avant.

Controles :
  - douze themes, un par mois, chacun avec ses quetes du mois ;
  - les types de quete existent (ContractType.java), les cibles et les icones
    de dohy et palamod existent (objets, blocs, tags du bon registre) ;
  - des identifiants de quete uniques, des quantites et des XP positives ;
  - une recompense par niveau, de 1 au dernier, la relique au dernier, des
    objets qui existent ;
  - assez d'XP dans un mois pour finir le pass sans tout faire (au moins
    1,3 fois ce qu'il faut, quel que soit le mois) ;
  - des textes sans accents (les polices du kit n'en ont pas).

Usage : python3 -I tools/check_pass.py
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PASS = ROOT / "data" / "pass.json"
JAVA = ROOT / "src" / "main" / "java"
RESOURCES = ROOT / "src" / "main" / "resources"
MOD = {"dohy", "palamod"}
DAYS = 30
SLACK = 1.3

# le registre des tags vises par chaque type d'objectif
TAG_FOLDER = {
    "MINE_BLOCK": "block", "HARVEST": "block", "PLACE_BLOCK": "block",
    "KILL_ENTITY": "entity_type", "PRODUCE": "item",
}


def contract_types():
    text = (JAVA / "fr/atraxterra/dohy/contract/ContractType.java").read_text(encoding="utf-8")
    body = text[text.index("enum ContractType"):text.index(";", text.index("enum ContractType"))]
    return {name for name in re.findall(r"\b([A-Z][A-Z_]+)\b", body)} - {"UNKNOWN"}


def item_exists(value):
    namespace, path = value.split(":", 1)
    if namespace not in MOD:
        return True
    return (RESOURCES / "assets" / namespace / "items" / (path + ".json")).is_file()


def block_exists(value):
    namespace, path = value.split(":", 1)
    if namespace not in MOD:
        return True
    return (RESOURCES / "assets" / namespace / "blockstates" / (path + ".json")).is_file()


def target_problem(kind, target):
    if target.startswith("#"):
        namespace, path = target[1:].split(":", 1)
        if namespace not in MOD:
            return None
        folder = TAG_FOLDER.get(kind)
        if folder is None:
            return "un tag pour un objectif sans cible"
        if not (RESOURCES / "data" / namespace / "tags" / folder / (path + ".json")).is_file():
            return "tag %s introuvable (data/%s/tags/%s)" % (target, namespace, folder)
        return None
    if ":" not in target:
        return "cible sans espace de noms"
    if kind in ("MINE_BLOCK", "HARVEST", "PLACE_BLOCK") and not block_exists(target):
        return "bloc inconnu"
    if kind == "PRODUCE" and not item_exists(target):
        return "objet inconnu"
    return None


def ascii_problem(text):
    bad = sorted({ch for ch in text if ord(ch) > 127 and ch != "·"})
    return "caracteres hors police %r" % "".join(bad) if bad else None


def main():
    errors = []
    data = json.loads(PASS.read_text(encoding="utf-8"))
    types = contract_types()
    levels = data.get("levels", 35)
    per_level = data.get("xp_per_level", 1000)
    needed = levels * per_level
    seen = set()

    def check_quest(where, quest):
        qid = quest.get("id", "")
        if not qid or qid in seen:
            errors.append("%s : identifiant de quete vide ou en double %r" % (where, qid))
        seen.add(qid)
        kind = quest.get("type", "")
        if kind not in types:
            errors.append("%s : type inconnu %r" % (qid, kind))
        if quest.get("amount", 0) <= 0 or quest.get("xp", 0) <= 0:
            errors.append("%s : quantite et XP doivent etre positives" % qid)
        target = quest.get("target")
        if target:
            problem = target_problem(kind, target)
            if problem:
                errors.append("%s : cible %r : %s" % (qid, target, problem))
        icon = quest.get("icon")
        if icon and not item_exists(icon):
            errors.append("%s : icone inconnue %r" % (qid, icon))
        for field in ("label",):
            problem = ascii_problem(quest.get(field, ""))
            if problem:
                errors.append("%s : %s : %s" % (qid, field, problem))

    daily = data["daily"]
    pool = daily["pool"]
    if len(pool) < daily["count"] * 3:
        errors.append("pool du jour trop maigre : %d quetes pour %d par jour" % (len(pool), daily["count"]))
    for quest in pool:
        check_quest("jour", quest)
    daily_xp = daily["count"] * sum(q["xp"] for q in pool) / max(1, len(pool)) * DAYS

    months = {}
    for theme in data["themes"]:
        month = theme.get("month")
        if month in months:
            errors.append("mois %s en double" % month)
        months[month] = theme
        for field in ("name", "tagline"):
            problem = ascii_problem(theme.get(field, ""))
            if problem:
                errors.append("theme %s : %s : %s" % (month, field, problem))
        if not re.match(r"^[0-9A-Fa-f]{6}$", theme.get("color", "")):
            errors.append("theme %s : couleur attendue en RRGGBB" % month)
        if not item_exists(theme.get("icon", "")):
            errors.append("theme %s : icone inconnue" % month)
        if not theme.get("quests"):
            errors.append("theme %s : aucune quete du mois" % month)
        for quest in theme.get("quests", []):
            check_quest("mois %s" % month, quest)
        total = daily_xp + sum(q["xp"] for q in theme.get("quests", []))
        if total < SLACK * needed:
            errors.append("theme %s : %d XP possibles pour %d necessaires (au moins x%.1f)"
                          % (month, total, needed, SLACK))
    if sorted(months) != list(range(1, 13)):
        errors.append("il faut un theme par mois, de 1 a 12 (trouves : %s)" % sorted(months))

    rewards = {}
    for reward in data["rewards"]:
        level = reward.get("level")
        if level in rewards:
            errors.append("niveau %s : deux recompenses" % level)
        rewards[level] = reward
        if reward.get("money", 0) < 0:
            errors.append("niveau %s : argent negatif" % level)
        for stack in reward.get("items", []):
            if not item_exists(stack.get("item", "")):
                errors.append("niveau %s : objet inconnu %r" % (level, stack.get("item")))
            if stack.get("count", 1) <= 0:
                errors.append("niveau %s : quantite nulle" % level)
        if not reward.get("money") and not reward.get("items") and not reward.get("relic"):
            errors.append("niveau %s : recompense vide" % level)
        if reward.get("relic") and level != levels:
            errors.append("niveau %s : la relique ne va qu'au dernier niveau" % level)
    if sorted(rewards) != list(range(1, levels + 1)):
        errors.append("il faut une recompense par niveau, de 1 a %d" % levels)
    if not rewards.get(levels, {}).get("relic"):
        errors.append("le dernier niveau doit donner la relique du mois")

    if errors:
        for error in errors:
            print(error)
        print("%d probleme(s) dans le pass" % len(errors))
        return 1
    print("pass : %d niveaux, %d quetes du jour, 12 themes ; ~%d XP par mois pour %d necessaires"
          % (levels, len(pool), daily_xp + sum(q["xp"] for q in data["themes"][0]["quests"]), needed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
