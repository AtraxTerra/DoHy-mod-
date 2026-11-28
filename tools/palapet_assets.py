#!/usr/bin/env python3
"""Les familiers de PalaPet : modeles, animations, textures, portraits, cage et appat.

    python3 -I tools/palapet_assets.py --import PalaPet-7.12.0.pala [--preview DOSSIER]
    python3 -I tools/palapet_assets.py --check

Les vingt et un familiers de Paladium, leur cage et l'appat viennent du jar
PalaPet 7.12.0 (Paladium Games), fourni par le joueur ; credit dans le README
(« Credits des textures »). Le jar n'est pas dans le depot : --import ne sert
qu'a les reprendre, la CI ne fait que --check.

Ce que --import reprend :

  - pour chaque familier (FAMILIARS : nom du mod -> nom de PalaPet), sa
    geometrie Bedrock (geo/pets, copiee telle quelle, lue a l'execution par
    client/rpg/BedrockModel), ses animations normalisees par
    tools/rpg_entities.py (idle, walk, swim, fall, skill...) et sa texture ;
  - la cage (geo/cage, ses animations idle et closing, sa texture) et l'appat ;
  - les portraits des menus (textures/gui/pet/<familier>.png, 96 x 96) et
    l'icone de la cage (32 x 32), rendus ici en logiciel depuis la geometrie et
    la texture du jar, dans le repere de RpgGeometry ; les particules de la cage
    sont un carre de 16 x 16 pris dans sa texture.

Ecrit aussi le JSON de la cage (etat de bloc, modele reduit a ses particules :
la cage et son occupant sont dessines par PetCageRenderer, objet).
"""
import io
import json
import math
import os
import struct
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
NS = "palamod"

# Le nom dans le mod (Familiar.id) -> le nom dans PalaPet.
FAMILIARS = {
    "dog": "dog", "cat": "cat", "rabbit": "rabbit", "penguin": "pet_penguin", "chameleon": "pet_chameleon",
    "blobfish": "pet_blobfish", "mini_golem": "pet_mini_golem", "dragon": "dragon", "kapio_koi": "kapio_koi",
    "arty": "pet_arty", "zombie_hand": "pet_zombie_hand", "nimbria_hand": "nimbria_hand",
    "reindeer": "pet_reindeer", "fox_patronus": "pet_fox_patronus", "ufo": "pet_ufo", "saharia_bat": "saharia_bat",
    "talikus_mosquito": "talikus_mosquito", "feng_uang": "feng_uang", "winter_fairy": "winter_fairy_pet",
    "bubble_wither": "bubble_wither", "ender_dragon": "pet_ender_dragon",
}
JAR = "assets/palapet/"
PORTRAIT = 96
CAGE_ICON = 32
# Le carre des particules de la cage : des planches de sa texture (64 x 64).
CAGE_PARTICLE = (16, 0, 32, 16)


def copies():
    """(chemin dans le jar, chemin sous assets/palamod, verification) des fichiers copies tels quels."""
    pairs = []
    for name, source in FAMILIARS.items():
        pairs.append((JAR + "geo/pets/%s.geo.json" % source, "pet/%s/model.json" % name, "json"))
        pairs.append((JAR + "textures/pets/%s.png" % source, "textures/entity/familiar/%s.png" % name, "png"))
    pairs.append((JAR + "geo/cage/cage.geo.json", "pet/cage/model.json", "json"))
    pairs.append((JAR + "textures/blocks/cage.png", "textures/entity/pet_cage.png", "png"))
    pairs.append((JAR + "textures/items/bait.png", "textures/item/pet_bait.png", (16, 16)))
    return pairs


def animations():
    pairs = [(JAR + "animations/pets/%s.animation.json" % source, "pet/%s/animation.json" % name)
             for name, source in FAMILIARS.items()]
    pairs.append((JAR + "animations/cage/cage.animation.json", "pet/cage/animation.json"))
    return pairs


def rendered():
    """Les images rendues ici depuis le jar."""
    names = ["textures/gui/pet/%s.png" % name for name in FAMILIARS]
    return names + ["textures/item/pet_cage.png", "textures/block/pet_cage.png"]


def imported_paths():
    return [target for _s, target, _c in copies()] + [target for _s, target in animations()] + rendered()


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


# -- Rendu logiciel d'une geometrie Bedrock ---------------------------------------------------
#
# Les memes conventions que client/rpg/RpgGeometry (et donc que le jeu) : repere du modele
# vanilla, y vers le bas, sol a 24 pixels ; un os racine en (px, 24 - py, pz), un enfant en
# (p - parent) avec y retourne ; une boite en (ox - px, -(oy + sy - py), oz - pz) ; rotations
# (degres, memes signes) appliquees z, puis y, puis x (ModelPart.translateAndRotate) ; une
# boite qui tourne devient une piece a son propre pivot. Les faces sont celles de
# ModelPart.Cube, et la face vanilla DOWN porte l'UV « up », WEST l'UV « east », et inversement.

FACES = {
    # direction vanilla : (sommets de la boite, nom Bedrock de l'UV, normale)
    "down": ((5, 4, 0, 1), "up", (0, -1, 0)),
    "up": ((2, 3, 7, 6), "down", (0, 1, 0)),
    "west": ((0, 4, 7, 3), "east", (-1, 0, 0)),
    "north": ((1, 0, 3, 2), "north", (0, 0, -1)),
    "east": ((5, 1, 2, 6), "west", (1, 0, 0)),
    "south": ((4, 5, 6, 7), "south", (0, 0, 1)),
}


def vec(node, key):
    value = node.get(key) or [0, 0, 0]
    return [float(value[i]) if i < len(value) else 0.0 for i in range(3)]


def rotation(np, degrees):
    x, y, z = (math.radians(a) for a in degrees)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def polygons(np, geometry):
    """Chaque face : (quatre sommets dans le repere du modele, quatre UV en pixels, normale)."""
    bones = geometry["bones"]
    names = {bone["name"] for bone in bones}
    children = {}
    roots = []
    for bone in bones:
        parent = bone.get("parent")
        if parent in names:
            children.setdefault(parent, []).append(bone)
        else:
            roots.append(bone)
    out = []

    def cube_faces(cube, pivot, matrix, offset):
        origin, size = vec(cube, "origin"), vec(cube, "size")
        inflate = float(cube.get("inflate", 0.0))
        uv = cube.get("uv")
        if uv is None:
            return
        mirror = bool(cube.get("mirror", False)) and not isinstance(uv, dict)
        x0 = origin[0] - pivot[0] - inflate
        y0 = -(origin[1] + size[1] - pivot[1]) - inflate
        z0 = origin[2] - pivot[2] - inflate
        x1, y1, z1 = x0 + size[0] + 2 * inflate, y0 + size[1] + 2 * inflate, z0 + size[2] + 2 * inflate
        if mirror:
            x0, x1 = x1, x0
        corners = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                   (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        if isinstance(uv, dict):
            rects = {}
            turns = {}
            for direction, (_indices, bedrock, _normal) in FACES.items():
                face = uv.get(bedrock)
                if face:
                    at, extent = vec(face, "uv"), vec(face, "uv_size")
                    rects[direction] = (at[0], at[1], at[0] + extent[0], at[1] + extent[1])
                    turns[direction] = int(round(float(face.get("uv_rotation", 0)) / 90.0)) % 4
        else:
            # Les UV en boite de ModelPart.Cube (texOffs u, v ; largeur, hauteur, profondeur).
            u, v = float(uv[0]), float(uv[1])
            w, h, d = size
            l, m, n, o, p, q = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
            r, s_, t = v, v + d, v + d + h
            rects = {"down": (m, r, n, s_), "up": (n, s_, o, r), "west": (l, s_, m, t),
                     "north": (m, s_, n, t), "east": (n, s_, p, t), "south": (p, s_, q, t)}
            turns = {}
        for direction, (indices, _bedrock, normal) in FACES.items():
            if direction not in rects:
                continue
            u0, v0, u1, v1 = rects[direction]
            uvs = [(u1, v0), (u0, v0), (u0, v1), (u1, v1)]
            turn = turns.get(direction, 0)
            if turn:
                uvs = [uvs[(i + turn) % 4] for i in range(4)]
            points = [offset + matrix @ np.array(corners[i]) for i in indices]
            if mirror:
                points, uvs = points[::-1], uvs[::-1]
                normal = (-normal[0], normal[1], normal[2])
            out.append((points, uvs, matrix @ np.array(normal, dtype=float)))

    def visit(bone, parent_pivot, matrix, offset):
        pivot = vec(bone, "pivot")
        if parent_pivot is None:
            local = np.array([pivot[0], 24.0 - pivot[1], pivot[2]])
        else:
            local = np.array([pivot[0] - parent_pivot[0], -(pivot[1] - parent_pivot[1]), pivot[2] - parent_pivot[2]])
        here_offset = offset + matrix @ local
        here = matrix @ rotation(np, vec(bone, "rotation"))
        for cube in bone.get("cubes", []):
            turn = vec(cube, "rotation")
            if any(turn):
                center = vec(cube, "pivot") if "pivot" in cube else pivot
                shift = np.array([center[0] - pivot[0], -(center[1] - pivot[1]), center[2] - pivot[2]])
                cube_faces(cube, center, here @ rotation(np, turn), here_offset + here @ shift)
            else:
                cube_faces(cube, pivot, here, here_offset)
        for child in children.get(bone["name"], []):
            visit(child, pivot, here, here_offset)

    for bone in roots:
        visit(bone, None, np.identity(3), np.zeros(3))
    return out


def render(geometry, texture, size, yaw=-35.0, pitch=18.0, margin=6):
    """Le modele vu de trois quarts (vue de pet_assets.py : il nous fait face), au tampon de profondeur."""
    import numpy as np
    from PIL import Image

    tex = np.asarray(texture.convert("RGBA")).astype(np.float32)
    th, tw = tex.shape[0], tex.shape[1]
    view = rotation(np, [pitch, 0, 0]) @ rotation(np, [0, yaw, 0]) @ np.diag([1.0, -1.0, 1.0])
    quads = []
    for points, uvs, normal in polygons(np, geometry):
        projected = np.array([view @ point for point in points])
        facing = view @ normal
        if facing[2] >= -1e-6:
            continue
        quads.append((projected, uvs, facing))
    if not quads:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))
    every = np.concatenate([q[0] for q in quads])
    low, high = every.min(axis=0), every.max(axis=0)
    span = max(high[0] - low[0], high[1] - low[1], 1e-6)
    scale = (size - 2 * margin) / span
    center = (low + high) / 2.0
    color = np.zeros((size, size, 4), dtype=np.float32)
    depth = np.full((size, size), np.inf)
    light = np.array([-0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)
    for points, uvs, normal in quads:
        screen = np.empty((4, 3))
        screen[:, 0] = (points[:, 0] - center[0]) * scale + size / 2.0
        screen[:, 1] = size / 2.0 - (points[:, 1] - center[1]) * scale
        screen[:, 2] = points[:, 2]
        p0, p1, p3 = screen[0], screen[1], screen[3]
        e1, e2 = p1[:2] - p0[:2], p3[:2] - p0[:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        uv0, uv1, uv3 = np.array(uvs[0]), np.array(uvs[1]), np.array(uvs[3])
        n = normal / max(np.linalg.norm(normal), 1e-9)
        brightness = 0.58 + 0.42 * max(0.0, float(np.dot(n * np.array([1.0, 1.0, -1.0]), light)))
        xs, ys = screen[:, 0], screen[:, 1]
        for py in range(max(0, int(math.floor(ys.min()))), min(size, int(math.ceil(ys.max())) + 1)):
            for px in range(max(0, int(math.floor(xs.min()))), min(size, int(math.ceil(xs.max())) + 1)):
                q = np.array([px + 0.5, py + 0.5]) - p0[:2]
                s = (q[0] * e2[1] - q[1] * e2[0]) / det
                t = (e1[0] * q[1] - e1[1] * q[0]) / det
                if s < 0 or s > 1 or t < 0 or t > 1:
                    continue
                z = p0[2] + s * (p1[2] - p0[2]) + t * (p3[2] - p0[2])
                if z >= depth[py, px]:
                    continue
                u, v = uv0 + s * (uv1 - uv0) + t * (uv3 - uv0)
                texel = tex[min(th - 1, max(0, int(math.floor(v)))), min(tw - 1, max(0, int(math.floor(u))))]
                if texel[3] < 8:
                    continue
                depth[py, px] = z
                color[py, px, :3] = np.clip(texel[:3] * brightness, 0, 255)
                color[py, px, 3] = 255 if texel[3] >= 255 else max(color[py, px, 3], texel[3])
    return Image.fromarray(color.astype(np.uint8), "RGBA")


def portrait(geometry, texture):
    """Le familier et, dessous, une ombre douce, pour qu'il ne flotte pas sur le fond du menu."""
    from PIL import Image

    image = render(geometry, texture, PORTRAIT, margin=8)
    halo = Image.new("RGBA", image.size, (0, 0, 0, 0))
    pixels = halo.load()
    for y in range(PORTRAIT):
        for x in range(PORTRAIT):
            dx = (x - PORTRAIT / 2.0) / (PORTRAIT * 0.36)
            dy = (y - PORTRAIT * 0.86) / (PORTRAIT * 0.08)
            r = dx * dx + dy * dy
            if r < 1.0:
                pixels[x, y] = (0, 0, 0, int(70 * (1.0 - r)))
    return Image.alpha_composite(halo, image)


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def write_bytes(relative, data):
    path = os.path.join(ASSETS, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(data)


def import_from(jar, preview):
    from PIL import Image

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import rpg_entities  # nos outils : la normalisation des animations Bedrock

    with zipfile.ZipFile(jar) as archive, tempfile.TemporaryDirectory() as scratch:
        names = set(archive.namelist())

        def read(source):
            if source not in names:
                raise SystemExit("%s absent du jar %s" % (source, jar))
            return archive.read(source)

        for source, target, check in copies():
            data = read(source)
            if check == "json":
                json.loads(data.decode("utf-8"))
            elif check == "png":
                if png_size(data) is None:
                    raise SystemExit("%s : pas un PNG" % source)
            elif png_size(data) != check:
                raise SystemExit("%s : %s au lieu de %s" % (source, png_size(data), check))
            write_bytes(target, data)
        for source, target in animations():
            raw = os.path.join(scratch, "animation.json")
            with open(raw, "wb") as handle:
                handle.write(read(source))
            converted = rpg_entities.convert_animations(raw)
            write_bytes(target, (json.dumps(converted, separators=(",", ":")) + "\n").encode("utf-8"))

        def geometry(source):
            return json.loads(read(source).decode("utf-8"))["minecraft:geometry"][0]

        def texture(source):
            return Image.open(io.BytesIO(read(source))).convert("RGBA")

        for name, source in FAMILIARS.items():
            image = portrait(geometry(JAR + "geo/pets/%s.geo.json" % source),
                             texture(JAR + "textures/pets/%s.png" % source))
            write_bytes("textures/gui/pet/%s.png" % name, png_bytes(image))
            if preview:
                os.makedirs(preview, exist_ok=True)
                image.resize((PORTRAIT * 2, PORTRAIT * 2), Image.NEAREST).save(os.path.join(preview, name + ".png"))
        cage = texture(JAR + "textures/blocks/cage.png")
        icon = render(geometry(JAR + "geo/cage/cage.geo.json"), cage, CAGE_ICON, margin=1)
        write_bytes("textures/item/pet_cage.png", png_bytes(icon))
        write_bytes("textures/block/pet_cage.png", png_bytes(cage.crop(CAGE_PARTICLE)))
        if preview:
            icon.resize((CAGE_ICON * 6, CAGE_ICON * 6), Image.NEAREST).save(os.path.join(preview, "cage.png"))
    print("PalaPet : %d fichiers repris de %s" % (len(imported_paths()), os.path.basename(jar)))


# -- JSON ----------------------------------------------------------------------------------------

def outputs():
    files = {}

    def put(path, payload):
        files[os.path.join(ASSETS, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    # La cage n'a que ses particules : elle et son occupant sont dessines par PetCageRenderer.
    put("models/block/pet_cage.json", {"textures": {"particle": "%s:block/pet_cage" % NS}})
    put("blockstates/pet_cage.json", {"variants": {
        "facing=%s" % facing: {"model": "%s:block/pet_cage" % NS} for facing in ("north", "east", "south", "west")}})
    put("models/item/pet_cage.json", {"parent": "minecraft:item/generated",
                                      "textures": {"layer0": "%s:item/pet_cage" % NS}})
    put("items/pet_cage.json", {"model": {"type": "minecraft:model", "model": "%s:item/pet_cage" % NS}})
    return files


def main():
    check = "--check" in sys.argv
    jar = sys.argv[sys.argv.index("--import") + 1] if "--import" in sys.argv else None
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    stale = []
    for path, content in outputs().items():
        current = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if current == content:
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(content)
    if jar and not check:
        import_from(jar, preview)
    for relative in imported_paths():
        if not os.path.exists(os.path.join(ASSETS, relative)):
            stale.append("%s absent (--import PalaPet-7.12.0.pala)" % relative)
    if stale:
        for line in stale:
            print("::error::Familiers de PalaPet : %s n'est pas a jour. "
                  "Lance 'python3 -I tools/palapet_assets.py'." % line)
        sys.exit(1)
    print("Familiers de PalaPet : %d familiers, cage et appat a jour" % len(FAMILIARS))


if __name__ == "__main__":
    main()
