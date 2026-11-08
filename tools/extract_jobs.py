#!/usr/bin/env python3
"""Extrait les tables de metier de PalaMod depuis le bytecode.

Motif recherche dans la sortie de javap -c :

    <push int>                       -> le niveau requis
    getstatic ...Register.NAME:...   -> l'item ou le bloc
    invokevirtual ...addBlacklistedCraft / addBlacklistedUsage / addReward

On suit le dernier entier empile et le dernier champ statique lu, puis on
emet la paire quand l'appel arrive. Robuste au fait que le constructeur
d'ItemStack a plusieurs formes (Item, Block, Item+count+meta).
"""
import json
import re
import subprocess
import sys

PUSH = re.compile(r'^\s*\d+:\s+(iconst_(\d)|bipush\s+(-?\d+)|sipush\s+(-?\d+)|ldc\s+#\d+\s+//\s+int\s+(-?\d+))')
# Le chemin du registre est ignore, seul compte son nom de classe : les verrous
# visent ItemsRegister et BlocksRegister (palamod/api), mais aussi les registres
# des modules — BlocksRegistry des metiers (les trones), SpawnerItemRegistry de
# PalaSpawner. L'ancienne expression n'acceptait que palamod/api et perdait les
# autres sans rien dire.
FIELD = re.compile(r'getstatic\s+#\d+\s+//\s+Field\s+(?:[\w$]+/)*([\w$]+)\.(\w+):')
CALL = re.compile(r'invokevirtual\s+#\d+\s+//\s+Method\s+\S*?(addBlacklistedCraft|addBlacklistedUsage|addObjective)')

JOBS = ["Miner", "Farmer", "Hunter", "Alchemist"]
BASE = "fr.paladium.palamod.modules.ajobs.common.registerer.jobs.type.{}Registerer"


def pushed(line):
    match = PUSH.match(line)
    if not match:
        return None
    for group in match.groups()[1:]:
        if group is not None:
            return int(group)
    return None


def disassemble(classpath, name):
    out = subprocess.run(
        ["javap", "-p", "-c", "-constants", "-cp", classpath, name],
        capture_output=True, text=True, env={"JAVA_TOOL_OPTIONS": "", "PATH": "/usr/bin:/bin"})
    if out.returncode != 0:
        print(f"  echec javap sur {name}: {out.stderr[:200]}", file=sys.stderr)
        return ""
    return out.stdout


NEW_STACK = re.compile(r'new\s+#\d+\s+//\s+class\s+net/minecraft/item/ItemStack')


def parse(text):
    """-> {method: [ {level, register, name, meta} ] }

    Le niveau est l'entier empile JUSTE AVANT le `new ItemStack`, et non le
    dernier entier vu avant l'appel. La premiere version prenait le dernier :
    pour `addBlacklistedCraft(4, new ItemStack(BUILDER_WAND, 1, 0))`, elle
    lisait la meta ou la quantite au lieu du 4. Tout verrou pose sur un objet a
    meta portait donc un faux niveau — et les quatre outils de construction,
    quatre metas d'un meme objet, s'ecrasaient en un seul verrou.

    La meta est relevee aussi : c'est elle qui distingue ces variantes.
    """
    found = {}
    method = None
    level = None          # dernier entier vu hors d'un constructeur d'ItemStack
    frozen = None         # le niveau, fige au `new ItemStack`
    in_stack = False
    stack_ints = []
    field = None

    for line in text.splitlines():
        header = re.match(r'^  (?:public|private|protected)?\s*\w[\w. <>\[\]]*\s+(\w+)\(', line)
        if header and 'Code:' not in line:
            method = header.group(1)
            level = frozen = field = None
            in_stack = False
            stack_ints = []
            continue

        if NEW_STACK.search(line):
            frozen = level
            in_stack = True
            stack_ints = []
            continue

        value = pushed(line)
        if value is not None:
            if in_stack:
                stack_ints.append(value)
            else:
                level = value

        field_match = FIELD.search(line)
        if field_match:
            field = (field_match.group(1), field_match.group(2))

        if in_stack and 'ItemStack."<init>"' in line:
            in_stack = False
            continue

        call = CALL.search(line)
        if call and field is not None:
            # Sans ItemStack (cas d'un appel qui prend l'objet directement),
            # le niveau est le dernier entier vu.
            chosen = frozen if frozen is not None else level
            if chosen is not None:
                found.setdefault(call.group(1), []).append({
                    "level": chosen,
                    "register": field[0],
                    "name": field[1],
                    # (Item, quantite, meta) : la meta est le second entier.
                    "meta": stack_ints[1] if len(stack_ints) >= 2 else 0,
                })
            field = None
            frozen = None
            stack_ints = []

    return found


# ---------------------------------------------------------------------------
# Recompenses de niveau.
#
# Elles ne suivent pas le motif des verrous : chaque recompense est une chaine
# de constructeur,
#
#     Reward.builder().level(N).rewardItem(new ItemStack(ITEM, count[, meta]))
#                     .type(RewardType.GIVE).build()
#     Reward.builder().level(N).moneyAmount(2500.0).type(MONEY_GIVE).build()
#
# et certaines piles sont construites a part, enchantees, rangees dans une
# variable locale, puis passees a rewardItem par un aload. Sans suivre les
# variables locales, la pioche Efficacite V / Solidite III du mineur au niveau
# 6 et les quatre pieces d'armure du niveau 11 seraient perdues — elles
# n'ont pas de `new ItemStack` dans leur chaine.
# ---------------------------------------------------------------------------

NUMBER = re.compile(
    r'^\s*\d+:\s+(?:iconst_(m1|\d)|bipush\s+(-?\d+)|sipush\s+(-?\d+)'
    r'|ldc(?:_w)?\s+#\d+\s+//\s+(?:int|float)\s+(-?[\d.E]+)f?'
    r'|ldc2_w\s+#\d+\s+//\s+(?:double|long)\s+(-?[\d.E]+)[dl]?)\s*$')
ANY_FIELD = re.compile(r'getstatic\s+#\d+\s+//\s+Field\s+([\w/$]+)\.(\w+):')
STORE = re.compile(r'^\s*\d+:\s+astore(?:_(\d)|\s+(\d+))\s*$')
LOAD = re.compile(r'^\s*\d+:\s+aload(?:_(\d)|\s+(\d+))\s*$')


def number(line):
    match = NUMBER.match(line)
    if not match:
        return None
    small, *rest = match.groups()
    if small is not None:
        return -1 if small == "m1" else int(small)
    for value in rest:
        if value is not None:
            return float(value) if ("." in value or "E" in value) else int(value)
    return None


def parse_rewards(text):
    """-> [ {level, type, item?, count?, meta?, enchantments?, money?} ]"""
    rewards = []
    current = None          # la recompense en cours de construction
    last_number = None
    building = None         # la pile entre `new ItemStack` et son <init>
    finished = None         # la derniere pile complete, en haut de pile
    locals_ = {}            # variable locale -> pile
    loaded = None           # index de la derniere variable chargee
    enchant = None          # enchantement lu, en attente de son niveau

    for line in text.splitlines():
        if re.match(r'^  (?:public|private|protected)?\s*\w[\w. <>\[\]]*\s+(\w+)\(', line):
            current = building = finished = loaded = enchant = None
            locals_ = {}
            continue

        if NEW_STACK.search(line):
            building = {"item": None, "ints": [], "enchantments": []}
            continue

        value = number(line)
        if value is not None:
            if building is not None:
                building["ints"].append(value)
            else:
                last_number = value

        field = ANY_FIELD.search(line)
        if field:
            owner, name = field.group(1).split("/")[-1], field.group(2)
            if building is not None and building["item"] is None:
                building["item"] = (owner, name)
            elif owner == "Enchantment":
                enchant = name
            elif owner == "RewardType" and current is not None:
                current["type"] = name

        if building is not None and 'ItemStack."<init>"' in line:
            ints = building["ints"]
            finished = {
                "item": building["item"],
                "count": ints[0] if ints else 1,
                "meta": ints[1] if len(ints) > 1 else 0,
                "enchantments": [],
            }
            building = None
            continue

        store = STORE.match(line)
        if store and finished is not None:
            locals_[int(store.group(1) or store.group(2))] = finished
            finished = None
            continue

        load = LOAD.match(line)
        if load:
            index = int(load.group(1) or load.group(2))
            loaded = index if index != 0 else None
            if loaded in locals_:
                finished = locals_[loaded]
            continue

        if "ItemStack.func_77966_a" in line and loaded in locals_ and enchant:
            locals_[loaded]["enchantments"].append({"id": enchant, "level": last_number})
            enchant = None
            continue

        if "Reward.builder" in line:
            current = {}
            last_number = None
            continue
        if current is None:
            continue
        if "RewardBuilder.level" in line:
            current["level"] = last_number
        elif "RewardBuilder.moneyAmount" in line:
            current["money"] = last_number
        elif "RewardBuilder.rewardItem" in line and finished is not None:
            current.update({k: v for k, v in finished.items()})
            finished = None
        elif "RewardBuilder.build" in line:
            rewards.append(current)
            current = None

    return rewards


def main():
    classpath = sys.argv[1] if len(sys.argv) > 1 else "."
    result = {}
    for job in JOBS:
        text = disassemble(classpath, BASE.format(job))
        if not text:
            continue
        result[job] = parse(text)
        result[job]["rewards"] = parse_rewards(text)
        counts = {k: len(v) for k, v in result[job].items()}
        print(f"{job}: {counts}", file=sys.stderr)
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
