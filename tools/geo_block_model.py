#!/usr/bin/env python3
"""Traduit un modele GeckoLib / Bedrock (.geo.json) en modele de bloc 26.2.

PalaMod dessinait certaines machines avec GeckoLib (le totem fleuri, par
exemple). DoHy n'embarque pas GeckoLib : un modele sans animation se ramene a
un modele de bloc JSON, avec ses elements et leurs UV par face.

Conventions de Blockbench entre les deux formats :

- l'axe X du Bedrock est le miroir de celui de Java : un cube d'origine x et
  de largeur w va de 8 - x - w a 8 - x ; Y ne bouge pas, Z est decale de 8 ;
- les faces est et ouest s'echangent donc ;
- les UV par face du Bedrock sont « uv » + « uv_size », dans l'espace de
  texture_width (16 ici, l'espace des modeles de bloc) ; celles du dessus et
  du dessous y sont rangees a l'envers sur les deux axes ;
- une rotation de 90 degres autour de Y d'un cube centre sur son pivot ne
  change pas sa forme : elle tourne ses faces. C'est la seule rotation prise
  en charge, le format de bloc n'acceptant pas 90 degres.

Usage :
    python3 -I tools/geo_block_model.py modele.geo.json palamod:block/texture > modele.json
"""

import json
import sys

SIDES = ["north", "east", "south", "west"]


def java_uv(face, uv, size):
    u, v = uv
    w, h = size
    if face in ("up", "down"):
        return [u + w, v + h, u, v]
    return [u, v, u + w, v + h]


def convert(geo, texture):
    geometry = geo["minecraft:geometry"][0]
    elements = []
    for bone in geometry["bones"]:
        if bone.get("rotation") and any(bone["rotation"]):
            raise SystemExit(f"os {bone['name']} tourne : non pris en charge")
        for cube in bone.get("cubes", []):
            x, y, z = cube["origin"]
            w, h, d = cube["size"]
            start = [8 - x - w, y, z + 8]
            end = [8 - x, y + h, z + 8 + d]
            faces = {}
            for name, spec in cube["uv"].items():
                java = {"east": "west", "west": "east"}.get(name, name)
                faces[java] = {"uv": [round(c, 4) for c in java_uv(name, spec["uv"], spec["uv_size"])],
                               "texture": "#all"}
            turn = (cube.get("rotation") or [0, 0, 0])
            if any(turn):
                if turn[0] or turn[2] or turn[1] % 90:
                    raise SystemExit(f"rotation {turn} : seule une rotation de 90 degres autour de Y passe")
                pivot = cube.get("pivot", [0, 0, 0])
                if abs((x + w / 2) - pivot[0]) > 1e-6 or abs((z + d / 2) - pivot[2]) > 1e-6 or w != d:
                    raise SystemExit("cube tourne qui n'est pas carre et centre sur son pivot")
                # X miroir : le sens de rotation s'inverse. Chaque quart de tour
                # envoie la face nord a l'est, et ainsi de suite.
                quarter = (-turn[1] // 90) % 4
                turned = {}
                for name, face in faces.items():
                    if name in SIDES:
                        turned[SIDES[(SIDES.index(name) + quarter) % 4]] = face
                    else:
                        face = dict(face)
                        face["rotation"] = (face.get("rotation", 0) + 90 * quarter) % 360
                        turned[name] = face
                faces = turned
            elements.append({"from": start, "to": end, "faces": faces})
    return {
        "parent": "minecraft:block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": elements,
    }


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 1
    geo = json.loads(open(argv[1], encoding="utf-8").read())
    print(json.dumps(convert(geo, argv[2]), indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
