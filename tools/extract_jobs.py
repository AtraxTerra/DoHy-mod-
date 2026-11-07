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
FIELD = re.compile(r'getstatic\s+#\d+\s+//\s+Field\s+(?:fr/paladium/palamod/api/)?(\w+)\.(\w+):')
CALL = re.compile(r'invokevirtual\s+#\d+\s+//\s+Method\s+\S*?(addBlacklistedCraft|addBlacklistedUsage|addReward|addObjective)')

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


def parse(text):
    """-> {method: [ {level, register, name} ] }"""
    found = {}
    method = None
    level = None
    field = None

    for line in text.splitlines():
        header = re.match(r'^  (?:public|private|protected)?\s*\w[\w. <>\[\]]*\s+(\w+)\(', line)
        if header and 'Code:' not in line:
            method = header.group(1)
            level = None
            field = None
            continue

        value = pushed(line)
        if value is not None:
            level = value

        field_match = FIELD.search(line)
        if field_match:
            field = (field_match.group(1), field_match.group(2))

        call = CALL.search(line)
        if call and level is not None and field is not None:
            found.setdefault(call.group(1), []).append({
                "level": level,
                "register": field[0],
                "name": field[1],
            })
            field = None

    return found


def main():
    classpath = sys.argv[1] if len(sys.argv) > 1 else "."
    result = {}
    for job in JOBS:
        text = disassemble(classpath, BASE.format(job))
        if not text:
            continue
        result[job] = parse(text)
        counts = {k: len(v) for k, v in result[job].items()}
        print(f"{job}: {counts}", file=sys.stderr)
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
