"""Modele Techne/Tabula 1.7.10 (javap) -> modele de bloc JSON 26.2.

Lit les pieces (constructeur) et l'echelle de chacune (glScaled dans func_78088_a),
applique la pose de la TESR (translate 0.5,1.5,0.5 ; rotation 180 sur Z) et ecrit
des elements JSON avec des UV par face (taille de texture tw x th).
usage : techne_json.py Model.javap tw th texture_id > model.json
"""
import json, re, subprocess, sys
src, tw, th, tex = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
parts = json.loads(subprocess.run([sys.executable, "-I", __file__.replace("techne_json.py", "techne_parts.py"), src],
                                  capture_output=True, text=True, check=True).stdout)
lines = open(src, encoding="utf-8", errors="replace").read().splitlines()
start = next(i for i, l in enumerate(lines) if "public void func_78088_a(" in l)
scale = {}
pending = []
last_part = None
cur = 1.0
for l in lines[start:]:
    if re.match(r"\s+(public|private|protected) ", l) and "func_78088_a" not in l:
        break
    m = re.search(r"ldc2_w\s+#\d+\s+// double (-?[\d.Ee+-]+)", l)
    if m:
        pending.append(float(m.group(1)))
    if "GL11.glScaled" in l or "GL11.glScalef" in l:
        if len(pending) >= 3:
            cur = pending[-3]
        pending = []
    m = re.search(r"getfield\s+#\d+\s+// Field (\w+):Lnet/minecraft/client/model/ModelRenderer;", l)
    if m:
        last_part = m.group(1)
    if "func_78785_a" in l and last_part:
        scale[last_part] = cur
    if "GL11.glPopMatrix" in l:
        cur = 1.0
        pending = []

def U(px): return round(px * 16.0 / tw, 4)
def V(py): return round(py * 16.0 / th, 4)

elements = []
for p in parts:
    s = scale.get(p["name"])
    if s is None:
        continue  # piece jamais dessinee
    px, py, pz = p["pivot"]
    u, v = p["u"], p["v"]
    for (x, y, z, w, h, d) in p["boxes"]:
        x0, x1 = px + s * x, px + s * (x + w)
        y0, y1 = py + s * y, py + s * (y + h)
        z0, z1 = pz + s * z, pz + s * (z + d)
        frm = [round(8 - x1, 4), round(24 - y1, 4), round(8 + z0, 4)]
        to = [round(8 - x0, 4), round(24 - y0, 4), round(8 + z1, 4)]
        w, h, d = int(w), int(h), int(d)
        faces = {
            "up":    [U(u + d + w), V(v), U(u + d), V(v + d)],
            "down":  [U(u + d + 2 * w), V(v), U(u + d + w), V(v + d)],
            "west":  [U(u + d + w), V(v + d), U(u + 2 * d + w), V(v + d + h)],
            "east":  [U(u), V(v + d), U(u + d), V(v + d + h)],
            "north": [U(u + d + w), V(v + d), U(u + d), V(v + d + h)],
            "south": [U(u + 2 * d + 2 * w), V(v + d), U(u + 2 * d + w), V(v + d + h)],
        }
        elements.append({"from": frm, "to": to,
                         "faces": {k: {"uv": uv, "texture": "#main"} for k, uv in faces.items()}})
model = {"parent": "minecraft:block/block", "textures": {"main": tex, "particle": tex}, "elements": elements}
json.dump(model, sys.stdout, indent=2)
sys.stdout.write("\n")
