#!/usr/bin/env python3
"""Le Base Claim Controller : modele, texture, etat de bloc, objet et noms.

    python3 -I tools/bcc_assets.py
    python3 -I tools/bcc_assets.py --check

Le BCC de Paladium est un bloc du plugin de faction : rien dans le jar de
PalaMod. Le dessin est propre au mod : un socle d'acier sombre cercle de rouge,
une colonne a bandes d'or, un cristal rouge qui luit, une couronne d'or. Texture
de 16 x 16 dessinee ici (sans Pillow, la CI ne verifie que le JSON).
"""
import io
import json
import os
import sys

try:
    from PIL import Image
except ImportError:
    Image = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "dohy")
NAME = "base_claim_controller"
TEX = "dohy:block/" + NAME

LANG = {
    "fr_fr": {
        "block.dohy.base_claim_controller": "Base Claim Controller",
        "tooltip.dohy.base_claim_controller": "Le coeur de ta base : gloire et ELO chaque jour, coffre de 55 cases",
        "tooltip.dohy.base_claim_controller.place":
            "Au centre d'un claim de 3 x 3 chunks, les 8 blocs autour et dessus vides",
        "tooltip.dohy.base_claim_controller.warn":
            "Pose ratee : il disparait. Pillable en 2 minutes de clic droit",
    },
    "en_us": {
        "block.dohy.base_claim_controller": "Base Claim Controller",
        "tooltip.dohy.base_claim_controller": "The heart of your base: glory and ELO every day, 55 slot storage",
        "tooltip.dohy.base_claim_controller.place":
            "At the center of a 3 x 3 chunk claim, the 8 blocks around and above empty",
        "tooltip.dohy.base_claim_controller.warn":
            "Failed placement: it vanishes. Looted with 2 minutes of right click",
    },
}


def rgb(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


STEEL_DARK = rgb("262a31")
STEEL = rgb("3a404a")
STEEL_LIGHT = rgb("6e7682")
RED = rgb("c8202a")
RED_LIGHT = rgb("ff5a3c")
RED_DARK = rgb("7a0f16")
GOLD = rgb("d8a93f")
GOLD_LIGHT = rgb("f6d37a")
GOLD_DARK = rgb("8a6420")
CORE = rgb("ff3b2f")
CORE_LIGHT = rgb("ffb08a")
CORE_DARK = rgb("a3121b")


def texture():
    """Quatre regions : le socle (lignes 0-3), la colonne (4-9, a gauche), le cristal (4-9, a
    droite), la couronne (10-15)."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for y in range(16):
        for x in range(16):
            if y < 4:
                color = STEEL_DARK if (x + y) % 3 else STEEL
                if y == 1:
                    color = RED if x % 4 else RED_DARK
                if y == 3 and x % 5 == 2:
                    color = STEEL_LIGHT
            elif y < 10 and x < 8:
                color = [STEEL_LIGHT, STEEL_LIGHT, STEEL, STEEL, STEEL, STEEL_DARK, STEEL, STEEL_LIGHT][x]
                if y in (4, 9):
                    color = GOLD if x % 3 else GOLD_DARK
            elif y < 10:
                dx, dy = x - 11.5, y - 6.5
                r = dx * dx + dy * dy
                color = CORE_LIGHT if r < 2.0 else CORE if r < 7.0 else CORE_DARK
                if (x + y) % 5 == 0 and r >= 2.0:
                    color = RED_LIGHT
            else:
                color = GOLD if (x + y) % 4 else GOLD_LIGHT
                if y in (10, 15) or x in (0, 15):
                    color = GOLD_DARK
            px[x, y] = color
    return image


def model():
    elements = []

    def element(frm, to, faces):
        elements.append({"from": list(frm), "to": list(to),
                         "faces": {face: {"uv": uv, "texture": "#bcc"} for face, uv in faces.items()}})

    def box(frm, to, side, top, bottom=None):
        element(frm, to, {"north": side, "south": side, "east": side, "west": side, "up": top,
                          "down": bottom or top})

    box((1, 0, 1), (15, 3, 15), [1, 0, 15, 3], [1, 0, 15, 3], [0, 0, 14, 3])
    box((4, 3, 4), (12, 10, 12), [0, 4, 8, 10], [0, 4, 8, 6])
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        box((x, 3, z), (x + 2, 12, z + 2), [0, 10, 2, 15], [0, 10, 2, 12])
    box((5, 10, 5), (11, 14, 11), [9, 4, 15, 9], [9, 4, 15, 9])
    box((3, 12, 3), (13, 13, 13), [0, 10, 16, 11], [0, 10, 16, 15])
    for frm, to in (((3, 13, 3), (13, 15, 4)), ((3, 13, 12), (13, 15, 13)), ((3, 13, 4), (4, 15, 12)),
                    ((12, 13, 4), (13, 15, 12))):
        box(frm, to, [0, 11, 10, 13], [0, 11, 10, 12])
    box((7, 14, 7), (9, 16, 9), [10, 5, 12, 7], [10, 5, 12, 7])
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"bcc": TEX, "particle": TEX}, "elements": elements}


def json_bytes(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def outputs(images):
    files = {
        os.path.join(ASSETS, "models", "block", NAME + ".json"): json_bytes(model()),
        os.path.join(ASSETS, "blockstates", NAME + ".json"): json_bytes(
            {"variants": {"": {"model": "dohy:block/" + NAME}}}),
        os.path.join(ASSETS, "items", NAME + ".json"): json_bytes(
            {"model": {"type": "minecraft:model", "model": "dohy:block/" + NAME}}),
    }
    if images:
        files[os.path.join(ASSETS, "textures", "block", NAME + ".png")] = texture()
    return files


def same(path, data):
    if not os.path.exists(path):
        return False
    if isinstance(data, bytes):
        with open(path, "rb") as handle:
            return handle.read() == data
    on_disk = Image.open(path).convert("RGBA")
    return on_disk.size == data.size and on_disk.tobytes() == data.convert("RGBA").tobytes()


def main():
    check = "--check" in sys.argv
    images = Image is not None
    if not images and not check:
        raise SystemExit("Pillow manque : il faut Pillow pour ecrire la texture.")
    stale = []
    for path, data in outputs(images).items():
        if same(path, data):
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if isinstance(data, bytes):
            with open(path, "wb") as handle:
                handle.write(data)
        else:
            buffer = io.BytesIO()
            data.save(buffer, format="PNG", optimize=True)
            with open(path, "wb") as handle:
                handle.write(buffer.getvalue())
    for code, labels in LANG.items():
        path = os.path.join(ASSETS, "lang", "%s.json" % code)
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        missing = {key: value for key, value in labels.items() if lang.get(key) != value}
        if not missing:
            continue
        if check:
            stale.append("%s : %s" % (os.path.relpath(path, ROOT), ", ".join(sorted(missing))))
            continue
        lang.update(missing)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(lang, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
    texture_path = os.path.join(ASSETS, "textures", "block", NAME + ".png")
    if not os.path.exists(texture_path):
        stale.append(os.path.relpath(texture_path, ROOT) + " absente")
    if stale:
        for line in stale:
            print("::error::BCC : %s n'est pas a jour. Lance 'python3 -I tools/bcc_assets.py'." % line)
        sys.exit(1)
    print("BCC : fichiers a jour" + ("" if images else " (sans Pillow : JSON seulement)"))


if __name__ == "__main__":
    main()
