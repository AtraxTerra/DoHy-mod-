#!/usr/bin/env python3
"""Porte les modeles des monstres de donjon de Paladium (config/rpgentities).

    python3 -I tools/rpg_entities.py <dossier rpgentities> [<jar PalaRPG extrait>]

Avec le jar PalaRPG (son dossier assets/), le decor des donjons est porte
aussi : coffre, jarre, marchands Mammon et Mephisto, et leurs sons (PROPS).

Paladium dessine ses monstres de donjon en Bedrock (format GeckoLib), rendus
par Lindworm, sa bibliotheque maison. On garde la geometrie telle quelle (le
mod la lit a l'execution, RpgModelLoader), et on prepare le reste :

  - les animations sont normalisees : chaque canal devient une liste
    d'images cles [t, x, y, z, interpolation]. Les expressions molang
    (math.sin(q.anim_time * 360) ...) ne sont pas evaluables par le jeu
    vanilla : elles sont echantillonnees ici, vingt fois par seconde ;
  - une texture animee (.mcmeta) est decoupee en images : le jeu n'anime pas
    les textures d'entite, le rendu choisit l'image selon le temps ;
  - les sons sont copies et declares dans assets/palarpg/sounds.json ;
  - deux index : assets/palarpg/entity/index.json pour le rendu (client),
    data/palarpg/rpg_skins.json pour le serveur (quel modele pour quel
    monstre, quels sons).

Seuls les modeles des quatre donjons portes, et seulement ceux qu'un monstre
ou un boss utilise (MOBS, BOSSES), sont copies : les projectiles et les effets
restent dehors.
"""
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import zlib

RES = "src/main/resources"
# Un espace de noms a part, celui du mod donjon de Paladium : ce script en est le
# seul auteur (sounds.json compris), palamod_assets.py ne le touche pas.
NS = "palarpg"
FFMPEG = shutil.which("ffmpeg")
SAMPLE_RATE = 20

# (donjon, type) -> modele. Les types sont ceux de MobKind.
MOBS = {
    "talikus": {"melee": "talikus_melee", "ranged": "talikus_distance_bow", "mage": "talikus_mage",
                "flying": "talikus_flying", "assassin": "talikus_assassin", "tank": "talikus_tank"},
    "nimbria": {"melee": "nimbria_melee", "ranged": "nimbria_distance", "mage": "nimbria_mage",
                "flying": "nimbria_flying", "assassin": "nimbria_assassin", "tank": "nimbria_tank"},
    "vitalys": {"melee": "vitalys_melee", "ranged": "vitalys_distance", "mage": "vitalys_mage",
                "flying": "vitalys_flying", "assassin": "vitalys_assassin", "tank": "vitalys_tank"},
    "manelios": {"melee": "manelios_melee", "ranged": "manelios_distance", "mage": "manelios_totem",
                 "flying": "manelios_flying", "assassin": "manelios_assassin", "tank": "manelios_tank",
                 # Les deux petits noyes qui naissent d'un guerrier de Manelios.
                 "split": "manelios_melee_tiny"},
}

# Nom du boss (Dungeon.bosses / miniBosses) -> modele. Les noms viennent du wiki,
# les modeles de Paladium : la correspondance est presque toujours le nom.
BOSSES = {
    "Gardien de Talikus": "talikus_miniboss", "Arbre ancien": "talikus_tree",
    "Exo-armure": "talikus_exoarmor", "Grand-mere": "talikus_grandma",
    "Sanglier de guerre": "talikus_warhog", "Seigneur de Talikus": "talikus_boss",
    "Grappin": "talikus_grappling_hook", "Machine de guerre": "talikus_warmachine",
    "Dragon de Talikus": "talikus_dragon", "Guerrier": "talikus_warrior",
    "Freres mineurs": "nimbria_miniboss", "Fileuse": "nimbria_spinner", "Main de pierre": "nimbria_hand",
    "Lanceur": "nimbria_launcher", "Ancien": "nimbria_ancient", "Seigneur de Nimbria": "nimbria_boss",
    "Serpent": "nimbria_snake", "Grolem": "nimbria_grolem", "Coeur de paladium": "nimbria_paladium_heart",
    "Dragon de Nimbria": "nimbria_dragon",
    "Traqueuse": "vitalys_miniboss", "Chimere": "vitalys_chimera", "Portail": "vitalys_portal",
    "Marteau": "vitalys_miniboss_hammer", "Marmite": "vitalys_marmite", "Seigneur de Vitalys": "vitalys_boss",
    "Phenix": "vitalys_phoenix", "Cerbere": "vitalys_cerberus", "Mage de Vitalys": "vitalys_boss_mage",
    "Dragon de Vitalys": "vitalys_dragon",
    "Jumeaux noyes": "manelios_shark", "Davy Jones": "manelios_davy_jones",
    "Poisson-globe": "manelios_pufferfish", "Hydre": "manelios_hydra", "Crabe": "manelios_crab",
    "Seigneur de Manelios": "manelios_boss", "Leviathan": "manelios_leviathan", "Kraken": "manelios_kraken",
    "Mage de Manelios": "manelios_mage", "Dragon de Manelios": "manelios_dragon",
}

# Les montures volantes jouent leurs variantes « _flying » quand elles existent.
FLYING = {"talikus_dragon", "nimbria_dragon", "vitalys_dragon", "manelios_dragon", "vitalys_phoenix",
          "talikus_flying", "nimbria_flying", "vitalys_flying", "manelios_flying"}

VARIANTS = ("yellow", "orange", "red", "black")


# -- Molang -------------------------------------------------------------------

TOKEN = re.compile(r"\s*(?:(\d+\.?\d*(?:e-?\d+)?|\.\d+)|([A-Za-z_][A-Za-z_0-9.]*)|(.))")


def molang(text, time):
    """Evalue une expression molang des fichiers de Paladium, au temps `time` (s)."""
    tokens = []
    for number, name, op in TOKEN.findall(text.strip()):
        if number:
            tokens.append(("n", float(number)))
        elif name:
            tokens.append(("i", name.lower()))
        elif op.strip():
            tokens.append(("o", op))
    pos = [0]

    def peek():
        return tokens[pos[0]] if pos[0] < len(tokens) else (None, None)

    def take():
        token = peek()
        pos[0] += 1
        return token

    def expression():
        value = term()
        while peek() in (("o", "+"), ("o", "-")):
            op = take()[1]
            value = value + term() if op == "+" else value - term()
        return value

    def term():
        value = factor()
        while peek() in (("o", "*"), ("o", "/")):
            op = take()[1]
            other = factor()
            value = value * other if op == "*" else (value / other if other else 0.0)
        return value

    def factor():
        kind, value = take()
        if kind == "o" and value == "-":
            return -factor()
        if kind == "o" and value == "+":
            return factor()
        if kind == "n":
            return value
        if kind == "o" and value == "(":
            inner = expression()
            take()
            return inner
        if kind == "i":
            if value in ("q.anim_time", "query.anim_time", "query.life_time", "q.life_time"):
                return time
            if value.startswith("math."):
                take()  # (
                args = [expression()]
                while peek() == ("o", ","):
                    take()
                    args.append(expression())
                take()  # )
                fn = value[5:]
                if fn == "sin":
                    return math.sin(math.radians(args[0]))
                if fn == "cos":
                    return math.cos(math.radians(args[0]))
                if fn == "max":
                    return max(args)
                if fn == "min":
                    return min(args)
                if fn == "abs":
                    return abs(args[0])
                raise ValueError("fonction molang inconnue : " + value)
            return 0.0
        raise ValueError("molang illisible : %r" % text)

    return expression()


def component(value, time):
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return molang(value, time)
    raise ValueError("composante illisible : %r" % (value,))


def has_molang(value):
    return isinstance(value, str) and not re.fullmatch(r"\s*-?\d+\.?\d*\s*", value)


# -- Animations ---------------------------------------------------------------

def raw_vector(value, side="post"):
    """La valeur d'une image cle, avant evaluation : trois composantes."""
    if isinstance(value, dict):
        if "vector" in value:
            value = value["vector"]
        elif side in value or "post" in value or "pre" in value:
            value = value.get(side, value.get("post", value.get("pre")))
            return raw_vector(value)
    if isinstance(value, (int, float, str)):
        return [value, value, value]
    return list(value)


def interpolation(value):
    if isinstance(value, dict):
        mode = str(value.get("lerp_mode", value.get("easing", "linear"))).lower()
        if "catmull" in mode:
            return "catmullrom"
    return "linear"


# L'ecart tolere en retirant une image cle : degres, pixels, facteur d'echelle.
TOLERANCE = {"rotation": 0.05, "position": 0.02, "scale": 0.003}


def simplify(frames, tolerance):
    """Retire les images cles qu'une interpolation lineaire de leurs voisines redonne.

    L'echantillonnage du molang a 20 Hz produit des centaines d'images par
    canal, dont la plupart sont alignees (ou identiques, sur un canal qui ne
    bouge pas). Une image n'est retiree que si toutes celles sautees depuis la
    derniere gardee restent a moins de `tolerance` de la corde : l'ecart ne
    s'accumule pas. Un canal qui n'est pas tout lineaire est laisse tel quel.
    """
    if len(frames) < 3 or any(frame[4] != "linear" for frame in frames):
        return frames
    kept = [frames[0]]
    anchor = 0
    for index in range(1, len(frames) - 1):
        start, end = frames[anchor], frames[index + 1]
        span = end[0] - start[0]
        fits = span > 0
        if fits:
            for middle in frames[anchor + 1:index + 1]:
                mix = (middle[0] - start[0]) / span
                if any(abs(start[c] + (end[c] - start[c]) * mix - middle[c]) > tolerance for c in (1, 2, 3)):
                    fits = False
                    break
        if not fits:
            kept.append(frames[index])
            anchor = index
    kept.append(frames[-1])
    return kept


def channel_frames(frames, length):
    """[[t, x, y, z, interpolation], ...] pour un canal, molang echantillonne."""
    if not isinstance(frames, dict) or ("vector" in frames and not any(
            re.fullmatch(r"\d+(\.\d+)?", key) for key in frames)):
        frames = {"0.0": frames}
    keys = sorted(((float(time), value) for time, value in frames.items()), key=lambda item: item[0])
    dynamic = any(has_molang(part) for _, value in keys for part in raw_vector(value))
    if not dynamic:
        return [[round(time, 4)] + [round(component(part, time), 4) for part in raw_vector(value)]
                + [interpolation(value)] for time, value in keys]
    span = length if length > 0 else 1.0
    out = []
    steps = max(2, int(round(span * SAMPLE_RATE)))
    for index in range(steps + 1):
        time = span * index / steps
        before = [k for k in keys if k[0] <= time] or keys[:1]
        after = [k for k in keys if k[0] >= time] or keys[-1:]
        t0, v0 = before[-1]
        t1, v1 = after[0]
        a = [component(p, time) for p in raw_vector(v0, "post")]
        b = [component(p, time) for p in raw_vector(v1, "pre")]
        mix = 0.0 if t1 == t0 else (time - t0) / (t1 - t0)
        out.append([round(time, 4)] + [round(x + (y - x) * mix, 4) for x, y in zip(a, b)] + ["linear"])
    return out


def convert_animations(source):
    data = json.load(open(source, encoding="utf-8"))
    out = {}
    for full_name, anim in data.get("animations", {}).items():
        name = full_name.split(".")[-1]
        length = float(anim.get("animation_length", 0) or 0)
        bones = {}
        last = 0.0
        for bone, channels in anim.get("bones", {}).items():
            converted = {}
            for channel in ("rotation", "position", "scale"):
                if channel in channels:
                    frames = channel_frames(channels[channel], length)
                    last = max(last, frames[-1][0])
                    converted[channel] = simplify(frames, TOLERANCE[channel])
            if converted:
                bones[bone] = converted
        if length <= 0:
            length = last if last > 0 else 1.0
        out[name] = {"length": round(length, 4), "loop": anim.get("loop") is True, "bones": bones}
    return out


def pick(animations, *candidates):
    for name in candidates:
        if name in animations:
            return name
    return None


def roles(model, animations):
    """Les animations jouees par le rendu, par role."""
    names = set(animations)
    flying = model in FLYING
    fly = "_flying" if flying else ""
    attacks = sorted(n for n in names if re.match(r"(atk|attack)", n)
                     and not re.search(r"(_start|_stop|_end|_loop|_idle|_grounded)$", n)
                     and (not flying or n.endswith("_flying") or not any(m.endswith("_flying") for m in names)))
    if flying and not attacks:
        attacks = sorted(n for n in names if re.match(r"(atk|attack)", n))
    return {
        "pose": pick(names, "pose" + fly, "pose", "POSE"),
        "idle": pick(names, "idle" + fly, "idle", "idle_goblin", "idle2", "loop"),
        "walk": pick(names, "walk" + fly, "walk", "fly", "swim_fast", "run"),
        "attacks": attacks,
        "hurt": pick(names, "hurt" + fly, "hurt", "hit"),
        "death": pick(names, "death", "death_fusion"),
        "spawn": pick(names, "spawn"),
    }


# -- Textures -----------------------------------------------------------------

def png_size(path):
    with open(path, "rb") as handle:
        return struct.unpack(">II", handle.read(24)[16:24])


def split_frames(path, frame_height, out_pattern):
    """Decoupe une bande d'images verticale en fichiers ; rend le nombre d'images."""
    data = open(path, "rb").read()
    chunks = []
    offset = 8
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunks.append((data[offset + 4:offset + 8], data[offset + 8:offset + 8 + length]))
        offset += 12 + length
    width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", chunks[0][1])
    if interlace:
        raise SystemExit("PNG entrelacee : " + path)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    bpp = max(1, channels * depth // 8)
    row = (width * channels * depth + 7) // 8
    raw = zlib.decompress(b"".join(body for kind, body in chunks if kind == b"IDAT"))
    # Defiltrer : chaque image doit commencer par une ligne qui ne depend pas
    # de la precedente.
    rows = []
    previous = bytearray(row)
    for y in range(height):
        start = y * (row + 1)
        kind = raw[start]
        line = bytearray(raw[start + 1:start + 1 + row])
        for x in range(row):
            left = line[x - bpp] if x >= bpp else 0
            up = previous[x]
            corner = previous[x - bpp] if x >= bpp else 0
            if kind == 1:
                line[x] = (line[x] + left) & 0xFF
            elif kind == 2:
                line[x] = (line[x] + up) & 0xFF
            elif kind == 3:
                line[x] = (line[x] + ((left + up) >> 1)) & 0xFF
            elif kind == 4:
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 0xFF
        rows.append(bytes(line))
        previous = line
    frames = height // frame_height
    others = [(kind, body) for kind, body in chunks[1:] if kind not in (b"IDAT", b"IEND")]

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    for index in range(frames):
        body = b"".join(b"\x00" + rows[y] for y in range(index * frame_height, (index + 1) * frame_height))
        ihdr = struct.pack(">IIBBBBB", width, frame_height, depth, color, 0, 0, 0)
        png = [b"\x89PNG\r\n\x1a\n", chunk(b"IHDR", ihdr)] + [chunk(k, b) for k, b in others]
        png += [chunk(b"IDAT", zlib.compress(body, 9)), chunk(b"IEND", b"")]
        with open(out_pattern % index, "wb") as handle:
            handle.write(b"".join(png))
    return frames


def write(path, payload):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")


def transcode(source, target):
    """Un son en mono 44,1 kHz, Vorbis qualite 4.

    Paladium livre ses sons a 240 kbit/s, parfois en stereo ; le jeu ne place
    dans l'espace qu'un son mono, et un bruitage n'a pas besoin de ce debit :
    le jar y gagne plus de 40 Mo. Sans ffmpeg, le son est copie tel quel.
    """
    if FFMPEG:
        done = subprocess.run([FFMPEG, "-nostdin", "-v", "error", "-y", "-i", source, "-vn", "-map_metadata", "-1",
                               "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "4",
                               # Sans cela le numero de serie Ogg est tire au hasard : meme son, autre fichier.
                               "-fflags", "+bitexact", "-flags:a", "+bitexact", target])
        if done.returncode == 0:
            return
    shutil.copyfile(source, target)


def extent(geometry):
    """Largeur et hauteur du modele au repos, en blocs (rotations ignorees) : de quoi borner sa taille."""
    xs, ys, zs = [], [], []
    for bone in geometry.get("bones", []):
        for cube in bone.get("cubes", []):
            origin, size = cube["origin"], cube["size"]
            xs += [origin[0], origin[0] + size[0]]
            ys += [origin[1], origin[1] + size[1]]
            zs += [origin[2], origin[2] + size[2]]
    if not xs:
        return [1.0, 1.0]
    width = max(max(xs) - min(xs), max(zs) - min(zs)) / 16.0
    return [round(width, 2), round(max(ys) / 16.0, 2)]


# Le decor des donjons, pris dans le jar PalaRPG (assets/palarpg) : modele ->
# (dossier du modele, {couleur: texture}). Ces objets jouent une pose donnee
# par le serveur (RpgSkin.pose), pas les roles d'un monstre.
PROPS = {
    "dungeon_chest": ("models/blocks/dungeon/chest", {
        "default": "textures/blocks/dungeon/chest/common.png",
        "rare": "textures/blocks/dungeon/chest/rare.png",
        "legendary": "textures/blocks/dungeon/chest/legendary.png"}),
    "dungeon_jar": ("models/blocks/dungeon/jar", {"default": "textures/blocks/dungeon/jar/texture.png"}),
    "merchant_mammon": ("models/entities/dungeon/merchant/mammon", {
        "default": "models/entities/dungeon/merchant/mammon/texture.png"}),
    "merchant_mephisto": ("models/entities/dungeon/merchant/mephisto", {
        "default": "models/entities/dungeon/merchant/mephisto/texture.png"}),
}

# Les sons du decor : identifiant -> fichier sous assets/palarpg/sounds du jar.
PROP_SOUNDS = {
    "dungeon.loot.common": "dungeon/loot/common.ogg",
    "dungeon.loot.rare": "dungeon/loot/rare.ogg",
    "dungeon.loot.legendary": "dungeon/loot/legendary.ogg",
    "dungeon.loot.spawn": "dungeon/loot/spawn.ogg",
    "dungeon.jar.break": "dungeon/block/jar/break.ogg",
}


# Les boxes de la boutique de Paladium (jar PalaShop, assets/palashop/boxes) :
# six coffres animes. On ne garde que l'attente et les quatre ouvertures
# completes (« fast »), une par classe de lot, avec la bande-son de chacune.
BOXES = ("amethyst", "paladium", "endium", "trixium", "saharia", "halloween")
BOX_CLIPS = ("idle", "fast_common", "fast_rare", "fast_epic", "fast_legendary")
# Les os translucides d'une box, en plus de ses os « vfx » : ceux dont la
# texture a des pixels a demi transparents (l'onde de sable, l'aura noire).
BOX_GLOW = {"saharia": ("wave", "wave2"), "halloween": ("black_aura",)}


def main():
    root = sys.argv[1]
    jar = sys.argv[2] if len(sys.argv) > 2 else None
    # Troisieme argument : le jar PalaShop extrait, pour les boxes.
    shop = sys.argv[3] if len(sys.argv) > 3 else None
    used = sorted(set(m for table in MOBS.values() for m in table.values()) | set(BOSSES.values()))
    for folder in ("assets/%s/entity" % NS, "assets/%s/textures/entity" % NS, "assets/%s/sounds/entity" % NS):
        shutil.rmtree(os.path.join(RES, folder), ignore_errors=True)

    index = {}
    skins = {}
    sounds_json_path = os.path.join(RES, "assets", NS, "sounds.json")
    sounds_json = {}
    total_sounds = 0
    for model in used:
        source = os.path.join(root, "models", model)
        info = json.load(open(os.path.join(source, "data.json"), encoding="utf-8"))
        geo = json.load(open(os.path.join(source, info.get("model", "model.json")), encoding="utf-8"))
        geometry = geo["minecraft:geometry"][0]
        write("assets/%s/entity/%s/model.json" % (NS, model), {"minecraft:geometry": [geometry]})
        animations = convert_animations(os.path.join(source, info.get("animation", "animation.json")))
        write("assets/%s/entity/%s/animation.json" % (NS, model), animations)

        textures = {}
        files = {"default": info.get("texture", "texture.png")}
        for variant, name in info.get("variants", {}).items():
            files[variant] = name
        width = geometry["description"]["texture_width"]
        height = geometry["description"]["texture_height"]
        target_dir = os.path.join(RES, "assets", NS, "textures", "entity", model)
        os.makedirs(target_dir, exist_ok=True)
        for variant, name in files.items():
            path = os.path.join(source, name)
            if not os.path.exists(path):
                continue
            meta_path = path + ".mcmeta"
            w, h = png_size(path)
            if os.path.exists(meta_path):
                meta = json.load(open(meta_path, encoding="utf-8")).get("animation", {})
                frame_height = int(meta.get("height", w * height // width))
                frames = split_frames(path, frame_height, os.path.join(target_dir, variant + "_%d.png"))
                textures[variant] = {"frames": frames, "frametime": int(meta.get("frametime", 1))}
            else:
                shutil.copyfile(path, os.path.join(target_dir, variant + ".png"))
                textures[variant] = {"frames": 1, "frametime": 1}

        sound_names = []
        sound_dir = os.path.join(source, "sounds")
        if os.path.isdir(sound_dir):
            out_dir = os.path.join(RES, "assets", NS, "sounds", "entity", model)
            os.makedirs(out_dir, exist_ok=True)
            for name in sorted(os.listdir(sound_dir)):
                if not name.endswith(".ogg"):
                    continue
                stem = name[:-4].lower()
                transcode(os.path.join(sound_dir, name), os.path.join(out_dir, stem + ".ogg"))
                sounds_json["entity.%s.%s" % (model, stem)] = {
                    "sounds": [{"name": "%s:entity/%s/%s" % (NS, model, stem)}]}
                sound_names.append(stem)
                total_sounds += 1

        index[model] = {"texture_size": [width, height], "textures": textures,
                        "roles": roles(model, animations), "flying": model in FLYING}
        skins[model] = {"variants": sorted(textures), "sounds": sound_names, "size": extent(geometry)}
        print("%-26s %3d os, %2d animations, %d textures, %2d sons" % (
            model, len(geometry.get("bones", [])), len(animations), len(textures), len(sound_names)))

    if jar:
        assets = os.path.join(jar, "assets", NS)
        for model, (folder, files) in PROPS.items():
            geo = json.load(open(os.path.join(assets, folder, "model.json"), encoding="utf-8"))
            geometry = geo["minecraft:geometry"][0]
            write("assets/%s/entity/%s/model.json" % (NS, model), {"minecraft:geometry": [geometry]})
            animations = convert_animations(os.path.join(assets, folder, "animation.json"))
            write("assets/%s/entity/%s/animation.json" % (NS, model), animations)
            target_dir = os.path.join(RES, "assets", NS, "textures", "entity", model)
            os.makedirs(target_dir, exist_ok=True)
            textures = {}
            for variant, name in files.items():
                shutil.copyfile(os.path.join(assets, name), os.path.join(target_dir, variant + ".png"))
                textures[variant] = {"frames": 1, "frametime": 1}
            index[model] = {
                "texture_size": [geometry["description"]["texture_width"],
                                 geometry["description"]["texture_height"]],
                "textures": textures,
                "roles": {"pose": None, "idle": "idle" if "idle" in animations else None, "walk": None,
                          "attacks": [], "hurt": None, "death": None, "spawn": None},
                "flying": False}
            print("%-26s %3d os, %2d animations, %d textures (decor)" % (
                model, len(geometry.get("bones", [])), len(animations), len(textures)))
        for name, source in PROP_SOUNDS.items():
            target = os.path.join(RES, "assets", NS, "sounds", "entity", name.replace(".", "_") + ".ogg")
            transcode(os.path.join(assets, "sounds", source), target)
            sounds_json[name] = {"sounds": [{"name": "%s:entity/%s" % (NS, name.replace(".", "_"))}]}
            total_sounds += 1

    boxes = {}
    if shop:
        for box in BOXES:
            source = os.path.join(shop, "assets", "palashop", "boxes", box)
            model = "box_" + box
            geo = json.load(open(os.path.join(source, "model.json"), encoding="utf-8"))
            geometry = geo["minecraft:geometry"][0]
            write("assets/%s/entity/%s/model.json" % (NS, model), {"minecraft:geometry": [geometry]})
            animations = convert_animations(os.path.join(source, "animation.json"))
            animations = {name: animations[name] for name in BOX_CLIPS}
            write("assets/%s/entity/%s/animation.json" % (NS, model), animations)
            target_dir = os.path.join(RES, "assets", NS, "textures", "entity", model)
            os.makedirs(target_dir, exist_ok=True)
            shutil.copyfile(os.path.join(source, "texture.png"), os.path.join(target_dir, "default.png"))
            index[model] = {
                "texture_size": [geometry["description"]["texture_width"],
                                 geometry["description"]["texture_height"]],
                "textures": {"default": {"frames": 1, "frametime": 1}},
                "roles": {"pose": None, "idle": "idle", "walk": None,
                          "attacks": [], "hurt": None, "death": None, "spawn": None},
                "flying": False,
                # Les rayons, faisceaux et eclats de l'ouverture : dessines a
                # part, translucides et en pleine lumiere (RpgEntityModel.drawGlow).
                "glow": sorted([bone["name"] for bone in geometry["bones"] if "vfx" in bone["name"]]
                               + list(BOX_GLOW.get(box, ())))}
            # Les reperes « open » et « close » de chaque ouverture : le moment ou le lot se montre.
            raw = json.load(open(os.path.join(source, "animation.json"), encoding="utf-8"))["animations"]
            clips = {}
            for name in BOX_CLIPS[1:]:
                marks = {text.strip(" ;"): float(time) for time, text in raw[name].get("timeline", {}).items()}
                clips[name[5:]] = {"length": animations[name]["length"], "open": marks["open"],
                                   "close": marks["close"]}
                stem = "box_%s_%s" % (box, name)
                transcode(os.path.join(source, "sound", "fast", name[5:] + ".ogg"),
                          os.path.join(RES, "assets", NS, "sounds", "entity", stem + ".ogg"))
                sounds_json["box.%s.%s" % (box, name)] = {
                    "sounds": [{"name": "%s:entity/%s" % (NS, stem)}]}
                total_sounds += 1
            boxes[box] = clips
            print("%-26s %3d os, %2d animations (box)" % (model, len(geometry.get("bones", [])), len(animations)))
        with open(os.path.join(RES, "data", NS, "boxes.json"), "w", encoding="utf-8") as handle:
            json.dump({"_comment": "GENERE par tools/rpg_entities.py : les ouvertures des boxes de PalaShop, "
                                   "par classe de lot. Durees et reperes en secondes.",
                       "boxes": boxes}, handle, indent=2, ensure_ascii=False)
            handle.write("\n")

    write("assets/%s/entity/index.json" % NS, index)
    with open(sounds_json_path, "w", encoding="utf-8") as handle:
        json.dump(dict(sorted(sounds_json.items())), handle, indent=1, ensure_ascii=False)
        handle.write("\n")
    data = {
        "_comment": "GENERE par tools/rpg_entities.py : le modele de chaque monstre et boss de donjon, "
                    "ses couleurs et ses sons. Les modeles viennent de config/rpgentities de Paladium.",
        "mobs": MOBS, "bosses": BOSSES, "models": skins,
    }
    os.makedirs(os.path.join(RES, "data", NS), exist_ok=True)
    with open(os.path.join(RES, "data", NS, "rpg_skins.json"), "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print("%d modeles, %d sons" % (len(used), total_sounds))


if __name__ == "__main__":
    main()
