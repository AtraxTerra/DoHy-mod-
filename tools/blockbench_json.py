#!/usr/bin/env python3
"""Modele 1.7.10 exporte de Blockbench (javap -c -p) -> elements de modele de bloc 26.2.

    python3 -I tools/blockbench_json.py Model.javap Renderer.javap texture_id > model.json
    python3 -I tools/blockbench_json.py --lucky <PalaMod extrait> tools/data/lucky_block_models.json

Les modeles des lucky blocks du mois de PalaMod (ModelChristmasLuckyBlock...) ne sont pas
des modeles Techne : chaque piece est un ModelRenderer(this) ; ses boites sont des
ModelBox(renderer, u, v, x, y, z, w, h, d, delta) ajoutees a sa liste ; les pieces
s'emboitent (addChild, func_78792_a) et tournent (setRotationAngle ou les champs
rotateAngle). Ce script rejoue le constructeur sur une petite pile, puis pose chaque boite
dans le bloc.

La pose est celle de la TESR, lue dans son javap : glTranslated(tx, ty, tz), glRotatef(180,
0, 0, 1) (le retournement des modeles de 1.7.10), glScalef s'il y en a un, puis le modele a
l'echelle 1/16. Un point du modele (x, y, z), en pixels, tombe donc au pixel
(16 tx - x, 16 ty - y, 16 tz + z) du bloc.

Une piece tournee garde sa rotation : depuis 25w46a, un element de modele de bloc tourne sur
les trois axes (x, puis y, puis z, en degres, sans limite d'angle), dans le meme ordre que
ModelRenderer.render (glRotatef Z, Y, X : le sommet tourne d'abord autour de X). Le
retournement de la TESR change le signe des angles autour de X et de Y.
"""
import json
import math
import os
import re
import sys


def read(path):
    return open(path, encoding="utf-8", errors="replace").read().splitlines()


def method(lines, signature):
    """Les instructions d'une methode dont la declaration contient `signature`."""
    start = next((i for i, l in enumerate(lines) if signature in l and "(" in l), None)
    if start is None:
        return []
    out = []
    for l in lines[start + 1:]:
        if re.match(r"\s{2}(public|private|protected|static)\b", l) or l.strip() == "}":
            break
        out.append(l)
    return out


def ops(code):
    for l in code:
        m = re.match(r"\s*\d+: (\w+)\s*(.*)$", l)
        if m:
            rest = m.group(2)
            yield m.group(1), rest, (rest.split("//", 1)[1].strip() if "//" in rest else "")


def constant(op, rest, com):
    if op.startswith("iconst_"):
        v = op[7:]
        return -1.0 if v == "m1" else float(v)
    if op.startswith("fconst_") or op.startswith("dconst_") or op.startswith("lconst_"):
        return float(op[7:])
    if op in ("bipush", "sipush"):
        return float(rest.split()[0])
    if op in ("ldc", "ldc_w", "ldc2_w"):
        m = re.search(r"(float|double|int|long) (-?[\d.Ee+-]+|NaN)", com)
        return float(m.group(2)) if m else com
    return None


def parse(model_lines):
    ctor = method(model_lines, "public " + next(
        re.search(r"class ([\w.$]+)", l).group(1) for l in model_lines if " class " in l and "{" in l) + "(")
    parts = {}
    tex = [64, 32]
    stack = []
    # les variables locales : certains exports gardent une piece dans une variable
    # (ModelRenderer bone = new ModelRenderer(this) ; bone.addChild(...))
    local = {}
    stored = 0
    for op, rest, com in ops(ctor):
        value = constant(op, rest, com)
        if value is not None:
            stack.append(value)
            continue
        if op.startswith("astore"):
            index = int(op[7:]) if op.startswith("astore_") else int(rest.split()[0])
            value = stack.pop()
            if isinstance(value, tuple) and value[0] == "renderer":
                stored += 1
                name = "local%d_%d" % (index, stored)
                parts[name] = value[1]
                parts[name]["name"] = name
                value = ("part", name)
            local[index] = value
            continue
        if op.startswith("aload") and op != "aload_0":
            index = int(op[6:]) if op.startswith("aload_") else int(rest.split()[0])
            stack.append(local.get(index, "this" if index == 0 else None))
            continue
        if op in ("aload_0", "dup", "pop"):
            if op == "aload_0":
                stack.append("this")
            elif op == "dup" and stack:
                stack.append(stack[-1])
            elif op == "pop" and stack:
                stack.pop()
            continue
        if op == "new":
            stack.append(("new", com.split()[-1]))
            continue
        if op == "getfield":
            m = re.search(r"Field (?:[\w/$]+\.)?(\w+):", com)
            name = m.group(1)
            owner = stack.pop() if stack else None
            if name in parts:
                stack.append(("part", name))
            elif name == "field_78804_l":
                stack.append(("cubes", owner[1] if isinstance(owner, tuple) else owner))
            else:
                stack.append(("field", name))
            continue
        if op == "putfield":
            m = re.search(r"Field (?:[\w/$]+\.)?(\w+):(\S+)", com)
            name, desc = m.group(1), m.group(2)
            value = stack.pop()
            owner = stack.pop() if stack else None
            if name == "field_78090_t":
                tex[0] = int(value)
            elif name == "field_78089_u":
                tex[1] = int(value)
            elif "ModelRenderer" in desc and isinstance(value, tuple) and value[0] == "renderer":
                parts[name] = value[1]
                parts[name]["name"] = name
            elif name in ("field_78795_f", "field_78796_g", "field_78808_h") and isinstance(owner, tuple):
                axis = {"field_78795_f": 0, "field_78796_g": 1, "field_78808_h": 2}[name]
                parts[owner[1]]["rot"][axis] = float(value)
            elif name == "field_78809_i" and isinstance(owner, tuple):
                parts[owner[1]]["mirror"] = bool(value)
            continue
        if op == "invokespecial":
            if "ModelRenderer.\"<init>\"" in com:
                args = 2 if "(Lnet/minecraft/client/model/ModelBase;II)" in com else (
                    1 if "(Lnet/minecraft/client/model/ModelBase;)" in com else 2)
                vals = [stack.pop() for _ in range(args)][::-1] if args > 1 else [stack.pop()]
                stack.pop()  # this
                stack.pop()  # le new
                if stack and stack[-1] == ("new", "net/minecraft/client/model/ModelRenderer"):
                    stack.pop()
                uv = [int(vals[0]), int(vals[1])] if len(vals) == 2 and not isinstance(vals[0], str) else [0, 0]
                stack.append(("renderer", {"pivot": [0.0, 0.0, 0.0], "rot": [0.0, 0.0, 0.0], "boxes": [],
                                           "children": [], "uv": uv, "mirror": False}))
                continue
            if "ModelBox.\"<init>\"" in com:
                desc = com[com.index("(") + 1:com.index(")")]
                count = len(re.findall(r"L[^;]+;|[IFZD]", desc))
                vals = [stack.pop() for _ in range(count)][::-1]
                stack.pop()  # le new
                if stack and isinstance(stack[-1], tuple) and stack[-1][0] == "new":
                    stack.pop()
                owner, u, v, x, y, z, w, h, d = vals[:9]
                delta = vals[9] if len(vals) > 9 and not isinstance(vals[9], bool) else 0.0
                mirror = bool(vals[10]) if len(vals) > 10 else False
                stack.append(("box", owner[1], [int(u), int(v), x, y, z, int(w), int(h), int(d), float(delta),
                                                mirror]))
                continue
            if "ModelBase.\"<init>\"" in com:
                stack.clear()
            continue
        if op == "invokeinterface" and "List.add" in com:
            box = stack.pop()
            target = stack.pop()
            if isinstance(box, tuple) and box[0] == "box":
                parts[box[1]]["boxes"].append(box[2])
            stack.append(1.0)
            continue
        if op == "invokevirtual":
            if "func_78793_a" in com:  # setRotationPoint
                z, y, x = stack.pop(), stack.pop(), stack.pop()
                owner = stack.pop()
                parts[owner[1]]["pivot"] = [x, y, z]
            elif "func_78792_a" in com:  # addChild
                child = stack.pop()
                parent = stack.pop()
                parts[parent[1]]["children"].append(child[1])
            elif "setRotationAngle" in com or "setRotation" in com:
                z, y, x = stack.pop(), stack.pop(), stack.pop()
                owner = stack.pop()
                stack.pop()  # this
                parts[owner[1]]["rot"] = [x, y, z]
            elif "func_78784_a" in com:  # setTextureOffset
                v, u = stack.pop(), stack.pop()
                owner = stack.pop()
                parts[owner[1]]["uv"] = [int(u), int(v)]
                stack.append(owner)
            elif "func_78789_a" in com or "func_78790_a" in com:  # addBox
                count = 6 if "func_78789_a" in com else 7
                vals = [stack.pop() for _ in range(count)][::-1]
                owner = stack.pop()
                p = parts[owner[1]]
                x, y, z, w, h, d = vals[:6]
                delta = vals[6] if count == 7 else 0.0
                p["boxes"].append([p["uv"][0], p["uv"][1], x, y, z, int(w), int(h), int(d), float(delta),
                                   p["mirror"]])
                if "func_78789_a" in com:
                    stack.append(owner)
            elif "func_78787_b" in com:  # setTextureSize
                v, u = stack.pop(), stack.pop()
                owner = stack.pop()
                stack.append(owner)
            else:
                raise SystemExit("instruction non geree : " + com)
            continue
    return parts, tex


def roots(model_lines, parts):
    """Les pieces dessinees par le modele (render, renderAll...) : func_78785_a sur un champ."""
    found = []
    for sig in ("void func_78088_a(", "void renderAll(", "void render("):
        last = None
        for op, rest, com in ops(method(model_lines, sig)):
            m = re.search(r"Field (\w+):Lnet/minecraft/client/model/ModelRenderer;", com) if op == "getfield" else None
            if m:
                last = m.group(1)
            if op == "invokevirtual" and "func_78785_a" in com and last in parts and last not in found:
                found.append(last)
        if found:
            break
    children = {c for p in parts.values() for c in p["children"]}
    return [r for r in found if r not in children] or [n for n in parts if n not in children]


def pose(renderer_lines):
    """La pose de la TESR : translation (en blocs), echelle."""
    translate = [0.5, 1.5, 0.5]
    scale = 1.0
    pending = []
    for op, rest, com in ops(method(renderer_lines, "func_147500_a") or renderer_lines):
        value = constant(op, rest, com)
        if value is not None and not isinstance(value, str):
            pending.append(value)
            continue
        if "glTranslate" in com:
            if len(pending) >= 3:
                translate = pending[-3:]
            pending = []
        elif "glScale" in com:
            if len(pending) >= 3:
                scale = pending[-3]
            pending = []
        elif "glRotate" in com:
            pending = []
    return translate, scale


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


IDENTITY = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def euler_xyz(m):
    """Les angles (degres) d'une rotation R = Rz(c) Ry(b) Rx(a) : x d'abord, puis y, puis z."""
    sy = -m[2][0]
    sy = max(-1.0, min(1.0, sy))
    b = math.asin(sy)
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(m[2][1], m[2][2])
        c = math.atan2(m[1][0], m[0][0])
    else:
        a = math.atan2(-m[1][2], m[1][1])
        c = 0.0
    out = []
    for t in (a, b, c):
        degrees = math.degrees(t)
        # les constantes de Blockbench sont arrondies (3.1416) : un quart de tour reste un quart
        snapped = round(degrees / 90.0) * 90.0
        out.append(round(snapped if abs(degrees - snapped) < 0.05 else degrees, 2) + 0.0)
    return out


def convert(model_path, renderer_path, texture):
    model_lines = read(model_path)
    parts, (tw, th) = parse(model_lines)
    translate, scale = pose(read(renderer_path))
    s = scale
    origin = [16 * translate[0], 16 * translate[1], 16 * translate[2]]

    # Une boite peut deborder du patron (un export de Blockbench garde ses UV meme hors de la
    # texture) : 1.7.10 repetait la texture, 26.2 refuse le modele. On s'arrete au bord.
    def U(px):
        return round(min(max(px, 0), tw) * 16.0 / tw, 4)

    def V(py):
        return round(min(max(py, 0), th) * 16.0 / th, 4)

    def world(p):
        # le retournement de la TESR (180 degres autour de Z) et sa translation
        return [round(origin[0] - s * p[0], 4), round(origin[1] - s * p[1], 4), round(origin[2] + s * p[2], 4)]

    flip = [[-1, 0, 0], [0, -1, 0], [0, 0, 1]]
    elements = []

    def walk(name, at, rot):
        p = parts[name]
        pivot = [at[i] + apply(rot, p["pivot"])[i] for i in range(3)]
        local = mul(mul(rz(p["rot"][2]), ry(p["rot"][1])), rx(p["rot"][0]))
        total = mul(rot, local)
        for (u, v, x, y, z, w, h, d, delta, mirror) in p["boxes"]:
            lo = [x - delta, y - delta, z - delta]
            hi = [x + w + delta, y + h + delta, z + d + delta]
            a = world([pivot[0] + lo[0], pivot[1] + lo[1], pivot[2] + lo[2]])
            b = world([pivot[0] + hi[0], pivot[1] + hi[1], pivot[2] + hi[2]])
            frm = [min(a[i], b[i]) for i in range(3)]
            to = [max(a[i], b[i]) for i in range(3)]
            # Les faces de ModelBox (TexturedQuad), passees par le retournement de la TESR : le
            # -Z du modele reste le nord, son -X devient l'est, son -Y (le haut : y descend) le
            # dessus. Chaque UV suit le sens des UV par defaut de la face en 26.2 (le bord gauche
            # vu de dehors en premier), sans miroir.
            faces = {
                "up": [U(u + d + w), V(v + d), U(u + d), V(v)],
                "down": [U(u + d + 2 * w), V(v), U(u + d + w), V(v + d)],
                "west": [U(u + d + w), V(v + d), U(u + 2 * d + w), V(v + d + h)],
                "east": [U(u), V(v + d), U(u + d), V(v + d + h)],
                "north": [U(u + d), V(v + d), U(u + d + w), V(v + d + h)],
                "south": [U(u + 2 * d + w), V(v + d), U(u + 2 * d + 2 * w), V(v + d + h)],
            }
            if mirror:
                faces["east"], faces["west"] = faces["west"], faces["east"]
                for k in faces:
                    f = faces[k]
                    faces[k] = [f[2], f[1], f[0], f[3]]
            element = {"from": frm, "to": to,
                       "faces": {k: {"uv": uv, "texture": "#main"} for k, uv in faces.items()
                                 if not (frm[0] == to[0] and k in ("north", "south", "up", "down")
                                         or frm[1] == to[1] and k in ("north", "south", "east", "west")
                                         or frm[2] == to[2] and k in ("east", "west", "up", "down"))}}
            if any(abs(total[i][j] - IDENTITY[i][j]) > 1e-6 for i in range(3) for j in range(3)):
                ax, ay, az = euler_xyz(mul(mul(flip, total), flip))
                rotation = {"origin": world(pivot)}
                for key, angle in (("x", ax), ("y", ay), ("z", az)):
                    if abs(angle) > 1e-4:
                        rotation[key] = angle
                element["rotation"] = rotation
            elements.append(element)
        for child in p["children"]:
            walk(child, pivot, total)

    for root in roots(model_lines, parts):
        walk(root, [0.0, 0.0, 0.0], IDENTITY)
    model = {"parent": "minecraft:block/block", "textures": {"main": texture, "particle": texture},
             "elements": elements}
    # Un modele qui deborde du bloc (oreilles, noeud, cadre) : l'icone est reduite pour tenir a peu
    # pres dans sa case (un peu moins pour les grands cadres a plat, sans quoi le bloc n'y serait
    # qu'un point), et recentree en hauteur sur le modele.
    lo = [min(e["from"][i] for e in elements) for i in range(3)]
    hi = [max(e["to"][i] for e in elements) for i in range(3)]
    center = [(lo[i] + hi[i]) / 2.0 for i in range(3)]
    half = max((hi[i] - lo[i]) / 2.0 for i in range(3))
    if half > 8.0 or abs(center[1] - 8.0) > 0.5:
        scale = round(max(0.42, 0.625 * 8.0 / max(half, 8.0)), 4)
        lift = round(-(center[1] - 8.0) * scale * 0.866, 3) + 0.0
        model["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, lift, 0],
                                    "scale": [scale, scale, scale]}}
    return model, [tw, th]


# Les lucky blocks du mois de PalaMod 10.3.0 : le modele et la TESR de chacun.
LUCKY = {
    "march": ("monthly.modules.march.client.model.block.ModelMarchLuckyBlock",
              "monthly.modules.march.client.render.tile.TileEntityMarchLuckyBlockRenderer"),
    "easter": ("blocks.models.easter.ModelEasterLuckyBlock", "tileentity.easter.TileEntityEasterLuckyBlockRenderer"),
    "may": ("blocks.models.may.ModelMayLuckyBlock", "tileentity.may.TileEntityMayLuckyBlockRenderer"),
    "june": ("blocks.models.june.ModelJuneLuckyBlock", "tileentity.june.TileEntityJuneLuckyBlockRenderer"),
    "july": ("monthly.modules.july.client.renders.models.blocks.ModelJulyLuckyBlock",
             "monthly.modules.july.client.renders.tiles.TileEntityJulyLuckyBlockRenderer"),
    "august": ("monthly.modules.august.client.renders.models.blocks.ModelAugustLuckyBlock",
               "monthly.modules.august.client.renders.tiles.TileEntityAugustLuckyBlockRenderer"),
    "september": ("monthly.modules.september.client.models.blocks.ModelSeptemberLuckyBlock",
                  "monthly.modules.september.client.renders.tiles.TileEntitySeptemberLuckyBlockRenderer"),
    "november": ("monthly.modules.november.client.models.blocks.ModelNovemberLuckyBlock",
                 "monthly.modules.november.client.renders.tiles.TileEntityNovemberLuckyBlockRenderer"),
    "christmas": ("blocks.models.ModelChristmasLuckyBlock",
                  "tileentity.christmas.TileEntityChristmasLuckyBlockRenderer"),
}


def lucky(jar, out):
    """Ecrit tools/data/lucky_block_models.json depuis le jar de PalaMod extrait (javap requis)."""
    import subprocess
    import tempfile
    models = {}
    base = "fr.paladium.palamod.modules.luckyblock."
    with tempfile.TemporaryDirectory() as tmp:
        for key, (model_class, renderer_class) in LUCKY.items():
            paths = []
            for name in (model_class, renderer_class):
                dump = subprocess.run(["javap", "-c", "-p", "-classpath", jar, base + name], capture_output=True,
                                      text=True, check=True).stdout
                path = os.path.join(tmp, name + ".javap")
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(dump)
                paths.append(path)
            model, size = convert(paths[0], paths[1], "palamod:block/%s_lucky_block" % key)
            models[key] = {"texture_size": size, "model": model}
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(models, handle, indent=1)
        handle.write("\n")
    print("%d modeles de lucky blocks du mois ecrits dans %s" % (len(models), out))


if __name__ == "__main__":
    if sys.argv[1] == "--lucky":
        lucky(sys.argv[2], sys.argv[3])
    else:
        model, _size = convert(sys.argv[1], sys.argv[2], sys.argv[3])
        json.dump(model, sys.stdout, indent=2)
        sys.stdout.write("\n")
