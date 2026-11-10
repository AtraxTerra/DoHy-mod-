#!/usr/bin/env python3
"""Releve des succes de PalaMod et PalaJobs, au bytecode.

Les succes sont declares dans des methodes d'enregistrement a coups de
builders (``CraftAchievement.builder().id(..).name(..)...build()``), puis
groupes par ``MultiAchievement.addSubAchievement`` et inscrits par
``register()``. Ce script rejoue ces methodes sur une petite pile : il suit les
constantes, les champs statiques, les ``new ItemStack(..)``, les variables
locales et les appels de builder, et rend la liste des succes.

Usage : extract_achievements.py <racine PalaMod> <racine PalaJobs> [sortie.json]
"""

import base64
import json
import re
import subprocess
import sys

SOURCES = [
    ("palamod", "fr.paladium.palamod.modules.achievements.AchievementsManager"),
    ("palajobs", "fr.paladium.palajobs.core.CommonProxy"),
]

INSN = re.compile(r"^\s*(\d+): (\w+)\s*(.*)$")
METHOD = re.compile(r"^  (?:[\w$<>\[\].,]+ )*([\w$<>]+)\((.*)\);$")


def javap(root, cls):
    out = subprocess.run(["javap", "-p", "-c", "-constants", "-cp", root, cls],
                         capture_output=True, text=True, check=True).stdout
    return out.splitlines()


def arg_count(desc):
    inside = desc[desc.index("(") + 1:desc.index(")")]
    n, i = 0, 0
    while i < len(inside):
        c = inside[i]
        if c == "[":
            i += 1
            continue
        if c == "L":
            i = inside.index(";", i)
        n += 1
        i += 1
    return n


def short(owner):
    return owner.split("/")[-1]


def decode_icon(s):
    try:
        return base64.b64decode(s).decode("utf-8", "replace")
    except Exception:
        return s


class Builder(dict):
    pass


def run_method(lines):
    stack, local, registered = [], {}, []

    def pop(n=1):
        vals = [stack.pop() if stack else None for _ in range(n)]
        return vals[::-1]

    for line in lines:
        m = INSN.match(line)
        if not m:
            continue
        op, rest = m.group(2), m.group(3)
        comment = rest.split("//", 1)[1].strip() if "//" in rest else ""
        if op in ("ldc", "ldc_w", "ldc2_w"):
            kind, _, val = comment.partition(" ")
            if kind == "String":
                stack.append(val)
            elif kind in ("int", "long"):
                stack.append(int(val.rstrip("l")))
            elif kind in ("float", "double"):
                stack.append(float(val.rstrip("fd")))
            else:
                stack.append({"class": val})
        elif op.startswith("iconst_"):
            stack.append(-1 if op.endswith("m1") else int(op[-1]))
        elif op in ("bipush", "sipush"):
            stack.append(int(rest.split()[0]))
        elif op == "aconst_null":
            stack.append(None)
        elif op == "getstatic":
            ref = comment.split(" ", 1)[1].split(":")[0]
            owner, name = ref.rsplit(".", 1)
            stack.append({"field": f"{short(owner)}.{name}"})
        elif op == "new":
            stack.append({"new": short(comment.split(" ", 1)[1])})
        elif op == "dup":
            stack.append(stack[-1] if stack else None)
        elif op == "pop":
            pop()
        elif op == "checkcast":
            pass
        elif op.startswith("astore"):
            idx = int(op.split("_")[1]) if "_" in op else int(rest.split()[0])
            local[idx] = pop()[0]
        elif op.startswith("aload"):
            idx = int(op.split("_")[1]) if "_" in op else int(rest.split()[0])
            stack.append(local.get(idx))
        elif op == "anewarray":
            n = pop()[0]
            stack.append([None] * (n if isinstance(n, int) else 0))
        elif op == "aastore":
            arr, i, v = pop(3)
            if isinstance(arr, list) and isinstance(i, int) and i < len(arr):
                arr[i] = v
        elif op.startswith("invoke"):
            kind, _, ref = comment.partition(" ")
            name_desc = ref.split(":", 1)
            target, desc = name_desc[0], name_desc[1] if len(name_desc) > 1 else "()V"
            owner, _, meth = target.rpartition(".")
            meth = meth.strip('"')
            args = pop(arg_count(desc)) if arg_count(desc) else []
            ret_void = desc.endswith(")V")
            if op == "invokestatic":
                if meth == "builder":
                    stack.append(Builder(type=short(owner).replace("Achievement", "") or "?",
                                         subs=[]))
                elif not ret_void:
                    stack.append({"call": f"{short(owner)}.{meth}", "args": args})
                continue
            if op == "invokedynamic":
                if not ret_void:
                    stack.append({"lambda": True})
                continue
            recv = pop()[0]
            if op == "invokespecial" and meth == "<init>":
                if isinstance(recv, dict) and "new" in recv:
                    recv["args"] = args
                continue
            if isinstance(recv, Builder):
                if meth == "build":
                    stack.append(recv)
                    continue
                if meth == "addSubAchievement":
                    recv["subs"].append(args[0])
                    stack.append(recv)
                    continue
                if meth == "register":
                    registered.append(recv)
                    continue
                recv[meth] = args[0] if len(args) == 1 else args
                if not ret_void:
                    stack.append(recv)
                continue
            if not ret_void:
                stack.append({"call": meth, "on": recv, "args": args})
    return registered


def methods(lines):
    """Decoupe la sortie javap par methode."""
    cur, body = None, []
    for line in lines:
        m = METHOD.match(line)
        if m:
            if cur:
                yield cur, body
            cur, body = m.group(1), []
        elif cur:
            body.append(line)
    if cur:
        yield cur, body


def describe(v):
    if isinstance(v, dict):
        if "field" in v:
            return v["field"].split(".", 1)[1]
        if v.get("new") == "ItemStack":
            parts = [describe(a) for a in v.get("args", [])]
            return ":".join(str(p) for p in parts)
        if "call" in v:
            return f"{v['call']}({', '.join(str(describe(a)) for a in v.get('args', []))})"
    if isinstance(v, list):
        return [describe(x) for x in v]
    return v


def clean(b, source):
    out = {"source": source, "type": b["type"]}
    for k, v in b.items():
        if k in ("type", "subs"):
            continue
        if k == "icon" and isinstance(v, str):
            v = decode_icon(v)
        out[k] = describe(v)
    if b["subs"]:
        out["subs"] = [clean(s, source) for s in b["subs"] if isinstance(s, Builder)]
    return out


def main():
    roots = {"palamod": sys.argv[1], "palajobs": sys.argv[2]}
    result = []
    for source, cls in SOURCES:
        lines = javap(roots[source], cls)
        for name, body in methods(lines):
            if not any(".builder:()" in l for l in body):
                continue
            for b in run_method(body):
                entry = clean(b, source)
                entry["method"] = name
                result.append(entry)
    text = json.dumps(result, ensure_ascii=False, indent=1)
    if len(sys.argv) > 3:
        open(sys.argv[3], "w", encoding="utf-8").write(text)
    else:
        print(text)
    flat = sum(1 + len(e.get("subs", [])) for e in result)
    print(f"{len(result)} succes inscrits, {flat} avec leurs etapes", file=sys.stderr)


if __name__ == "__main__":
    main()
