"""Lit le javap -c d'un modele Techne 1.7.10 (ModelRenderer(this,u,v), addBox, setRotationPoint, setRotation)
et sort la liste des pieces : nom, u, v, boite (x,y,z,w,h,d), pivot, rotation, miroir, taille de texture."""
import re, sys, json
lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
# ne garder que le constructeur
start = next(i for i, l in enumerate(lines) if re.search(r"public [\w.]+\(\);", l))
end = next(i for i in range(start + 1, len(lines)) if re.match(r"\s+(public|private|protected) ", lines[i]))
code = lines[start:end]
stack = []
parts = {}
order = []
cur = None
def num(tok):
    return float(tok)
for l in code:
    m = re.match(r"\s*\d+: (\w+)\s*(.*)$", l)
    if not m:
        continue
    op, rest = m.group(1), m.group(2)
    com = rest.split("//", 1)[1].strip() if "//" in rest else ""
    if op.startswith("iconst_"):
        v = op[7:]; stack.append(-1.0 if v == "m1" else float(v))
    elif op in ("bipush", "sipush"):
        stack.append(float(rest.split()[0]))
    elif op.startswith("fconst_"):
        stack.append(float(op[7:]))
    elif op in ("ldc", "ldc_w", "ldc2_w"):
        mm = re.search(r"(float|double|int) (-?[\d.Ee+-]+)", com)
        stack.append(float(mm.group(2)) if mm else com)
    elif op == "aload_0":
        stack.append("this")
    elif op == "new":
        stack.append("NEW")
    elif op == "dup":
        stack.append(stack[-1] if stack else None)
    elif op == "invokespecial" and "ModelRenderer.\"<init>\":(Lnet/minecraft/client/model/ModelBase;II)V" in com:
        v = stack.pop(); u = stack.pop(); stack.pop(); stack.pop()  # this, NEW(dup)
        cur = {"u": int(u), "v": int(v), "boxes": [], "pivot": [0, 0, 0], "rot": [0, 0, 0], "mirror": False, "tex": None}
        stack[-1] = cur if stack else cur
    elif op == "putfield" and "ModelRenderer;" in com:
        name = com.split("Field ")[1].split(":")[0]
        obj = stack.pop(); stack.pop()
        parts[name] = obj; order.append(name)
    elif op == "getfield" and "ModelRenderer;" in com:
        name = com.split("Field ")[1].split(":")[0]
        stack.pop(); stack.append(parts.get(name))
    elif op == "invokevirtual" and "func_78789_a:(FFFIII)V" in com:  # addBox
        d = stack.pop(); h = stack.pop(); w = stack.pop(); z = stack.pop(); y = stack.pop(); x = stack.pop(); obj = stack.pop()
        obj["boxes"].append([x, y, z, w, h, d])
    elif op == "invokevirtual" and "func_78790_a:(FFFIIIF)V" in com:  # addBox with inflate
        g = stack.pop(); d = stack.pop(); h = stack.pop(); w = stack.pop(); z = stack.pop(); y = stack.pop(); x = stack.pop(); obj = stack.pop()
        obj["boxes"].append([x, y, z, w, h, d])
    elif op == "invokevirtual" and "func_78793_a:(FFF)V" in com:  # setRotationPoint
        pz = stack.pop(); py = stack.pop(); px = stack.pop(); obj = stack.pop()
        obj["pivot"] = [px, py, pz]
    elif op == "invokevirtual" and "func_78787_b:(II)Lnet/minecraft/client/model/ModelRenderer;" in com:  # setTextureSize
        th = stack.pop(); tw = stack.pop(); obj = stack.pop(); obj["tex"] = [int(tw), int(th)]
        stack.append(obj)
    elif op == "putfield" and "field_78809_i:Z" in com:  # mirror
        val = stack.pop(); obj = stack.pop(); obj["mirror"] = bool(val)
    elif op == "invokespecial" and "setRotation" in com:
        rz = stack.pop(); ry = stack.pop(); rx = stack.pop(); obj = stack.pop(); stack.pop()
        obj["rot"] = [rx, ry, rz]
    elif op == "pop":
        if stack: stack.pop()
    elif op in ("putfield",) and ("field_78090_t" in com or "field_78089_u" in com):
        stack.pop(); stack.pop()
out = [dict(name=n, **parts[n]) for n in order]
json.dump(out, sys.stdout, indent=1)
