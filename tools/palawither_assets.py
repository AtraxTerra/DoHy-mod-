#!/usr/bin/env python3
"""Les withers du pillage de PalaWither : modeles, animations, textures, sons, objets, receptacle.

    python3 -I tools/palawither_assets.py --import PalaWither-3.6.0.pala [--preview DOSSIER]
    python3 -I tools/palawither_assets.py --check

Tout vient du jar PalaWither 3.6.0 (Paladium Games), fourni par le joueur ; credit dans le
README (« Credits des textures »). Le jar n'est pas dans le depot : --import ne sert qu'a
le reprendre, la CI ne fait que --check.

Ce que --import reprend :

  - les cinq withers du jar (baby, passif, assoiffe, predateur, supreme) : geometrie
    Bedrock (copiee telle quelle, lue a l'execution par client/rpg/BedrockModel),
    animations normalisees par tools/rpg_entities.py (idle ; spawn et death pour le
    supreme) et texture. Ils remplacent les peaux dessinees d'apres le wiki
    (tools/wither_skins.py, retire) ;
  - leurs sons : l'invocation, la naissance et la boucle jouee au receptacle pendant
    l'invocation ; et le signal d'erreur quand l'invocateur quitte la zone ;
  - les gemmes (le wither de base et les cinq genres) et les trois ameliorations, en
    16 x 16, a la place des gemmes et ameliorations teintees ;
  - le receptacle : sa geometrie et ses deux textures, eteinte et allumee (le rendu
    est celui de WitherReceptacleRenderer) ; ses particules sont un carre de sa texture ;
  - la barre de vie de chaque genre (la bande de l'overlay de PalaWither) ;
  - rendues ici en logiciel depuis la geometrie et la texture du jar (le rendu de
    tools/palapet_assets.py) : les icones des quatre tetes, du laser de ciblage et du
    receptacle, en 32 x 32 (le jar les dessine en 3D dans l'inventaire).

Ecrit aussi le JSON du receptacle (etat de bloc, modele reduit a ses particules, objet).
"""
import io
import json
import os
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
NS = "palamod"
JAR = "assets/palawither/"

# Les genres du jar, sous le nom du mod (WitherKind.id) : les memes.
WITHERS = ("baby_wither", "passive_wither", "thirsty_wither", "predator_wither", "supreme_wither")
HEADS = ("passive_wither", "thirsty_wither", "predator_wither", "supreme_wither")
# Le nom de la gemme dans le jar -> l'objet du mod.
GEMS = {"vanilla": "wither_gem", "baby": "baby_wither_gem", "passive": "passive_wither_gem",
        "thirsty": "thirsty_wither_gem", "predator": "predator_wither_gem", "supreme": "supreme_wither_gem"}
UPGRADES = {"furtive": "stealth_upgrade", "resistance": "wither_resistance_upgrade",
            "slimy": "wither_slime_upgrade"}
# La bande de la barre de vie : celle du wither de base (wither.png) et celle de chaque genre.
BARS = {"wither": "paladium_wither", "baby": "baby_wither", "passive": "passive_wither",
        "thirsty": "thirsty_wither", "predator": "predator_wither", "supreme": "supreme_wither"}
SOUNDS = ("invoke", "loop", "spawn")
ICON = 32
# Le carre des particules du receptacle : la frise de pierre de sa texture (64 x 64).
RECEPTACLE_PARTICLE = (16, 0, 32, 16)


def copies():
    """(chemin dans le jar, chemin sous assets/palamod, verification) des fichiers copies tels quels."""
    pairs = []
    for name in WITHERS:
        pairs.append((JAR + "models/entities/%s/model.json" % name, "wither/%s/model.json" % name, "json"))
        pairs.append((JAR + "textures/entities/%s.png" % name, "textures/entity/wither/%s.png" % name, "png"))
        for sound in SOUNDS:
            pairs.append((JAR + "sounds/entities/%s/%s.ogg" % (name, sound), "sounds/%s/%s.ogg" % (name, sound),
                          "ogg"))
    pairs.append((JAR + "sounds/error.ogg", "sounds/wither_receptacle/error.ogg", "ogg"))
    for source, item in GEMS.items():
        pairs.append((JAR + "textures/items/gem/%s.png" % source, "textures/item/%s.png" % item, (16, 16)))
    for source, item in UPGRADES.items():
        pairs.append((JAR + "textures/items/upgrade/%s.png" % source, "textures/item/%s.png" % item, (16, 16)))
    pairs.append((JAR + "models/blocks/wither_receptacle.json", "wither/receptacle/model.json", "json"))
    for state in ("off", "on"):
        pairs.append((JAR + "textures/blocks/wither_receptacle/%s.png" % state,
                      "textures/entity/wither_receptacle/%s.png" % state, (64, 64)))
    for source, kind in BARS.items():
        pairs.append((JAR + "textures/overlay/wither/foreground/%s.png" % source,
                      "textures/gui/wither/bar/%s.png" % kind, (2709, 45)))
    return pairs


def animations():
    return [(JAR + "models/entities/%s/animation.json" % name, "wither/%s/animation.json" % name)
            for name in WITHERS]


def rendered():
    """Les images rendues ici depuis le jar."""
    names = ["textures/item/%s_head.png" % name for name in HEADS]
    return names + ["textures/item/wither_target_laser.png", "textures/item/wither_receptacle.png",
                    "textures/block/wither_receptacle.png"]


def imported_paths():
    return [target for _s, target, _c in copies()] + [target for _s, target in animations()] + rendered()


def sound_events():
    """Les evenements sonores que sounds.json doit declarer (ajoutes a la main, voir --check)."""
    events = {}
    for name in WITHERS:
        for sound in SOUNDS:
            events["%s.%s" % (name, sound)] = "%s:%s/%s" % (NS, name, sound)
    events["wither_receptacle.error"] = "%s:wither_receptacle/error" % NS
    return events


def write_bytes(relative, data):
    path = os.path.join(ASSETS, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(data)


def without(geometry, bones):
    """La geometrie sans les os nommes (ni leurs enfants)."""
    names = {bone["name"] for bone in geometry["bones"]}
    parent = {bone["name"]: bone.get("parent") for bone in geometry["bones"]}

    def hidden(name):
        while name in names:
            if name in bones:
                return True
            name = parent.get(name)
        return False

    copy = dict(geometry)
    copy["bones"] = [bone for bone in geometry["bones"] if not hidden(bone["name"])]
    return copy


def import_from(jar, preview):
    from PIL import Image

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import palapet_assets  # nos outils : le rendu logiciel des geometries Bedrock
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
            elif check == "ogg":
                if data[:4] != b"OggS":
                    raise SystemExit("%s : pas un OGG" % source)
            elif check == "png":
                if palapet_assets.png_size(data) is None:
                    raise SystemExit("%s : pas un PNG" % source)
            elif palapet_assets.png_size(data) != check:
                raise SystemExit("%s : %s au lieu de %s" % (source, palapet_assets.png_size(data), check))
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

        icons = {}
        for name in HEADS:
            icons["%s_head" % name] = palapet_assets.render(
                geometry(JAR + "models/items/head/%s/model.json" % name),
                texture(JAR + "textures/items/head/%s/texture.png" % name), ICON, yaw=-35.0, pitch=18.0, margin=1)
        # Le laser eteint : sans son rayon (light_on, que l'animation « active » etire).
        icons["wither_target_laser"] = palapet_assets.render(
            without(geometry(JAR + "models/items/target/wither_target_laser_system/model.json"), {"light_on"}),
            texture(JAR + "textures/items/target/wither_target_laser_system/item.png"), ICON, yaw=-90.0,
            pitch=10.0, margin=1)
        off = texture(JAR + "textures/blocks/wither_receptacle/off.png")
        icons["wither_receptacle"] = palapet_assets.render(geometry(JAR + "models/blocks/wither_receptacle.json"),
                                                           off, ICON, yaw=-45.0, pitch=20.0, margin=1)
        for item, image in icons.items():
            write_bytes("textures/item/%s.png" % item, palapet_assets.png_bytes(image))
            if preview:
                os.makedirs(preview, exist_ok=True)
                image.resize((ICON * 6, ICON * 6), Image.NEAREST).save(os.path.join(preview, item + ".png"))
        write_bytes("textures/block/wither_receptacle.png",
                    palapet_assets.png_bytes(off.crop(RECEPTACLE_PARTICLE)))
    print("PalaWither : %d fichiers repris de %s" % (len(imported_paths()), os.path.basename(jar)))


# -- JSON ----------------------------------------------------------------------------------------

def outputs():
    files = {}

    def put(path, payload):
        files[os.path.join(ASSETS, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    # Le receptacle n'a que ses particules : il est dessine par WitherReceptacleRenderer.
    put("models/block/wither_receptacle.json", {"textures": {"particle": "%s:block/wither_receptacle" % NS}})
    put("blockstates/wither_receptacle.json", {"variants": {
        "facing=%s" % facing: {"model": "%s:block/wither_receptacle" % NS}
        for facing in ("north", "east", "south", "west")}})
    put("models/item/wither_receptacle.json", {"parent": "minecraft:item/generated",
                                               "textures": {"layer0": "%s:item/wither_receptacle" % NS}})
    put("items/wither_receptacle.json", {"model": {"type": "minecraft:model",
                                                   "model": "%s:item/wither_receptacle" % NS}})
    return files


def check_sounds():
    """Les evenements de sounds.json (edite a la main) : presents et pointant sur nos fichiers."""
    problems = []
    path = os.path.join(ASSETS, "sounds.json")
    declared = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    for event, sound in sound_events().items():
        entry = declared.get(event)
        listed = [s if isinstance(s, str) else s.get("name") for s in (entry or {}).get("sounds", [])]
        if sound not in listed:
            problems.append("sounds.json : %s -> %s manque" % (event, sound))
    return problems


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
            stale.append("%s absent (--import PalaWither-3.6.0.pala)" % relative)
    stale.extend(check_sounds())
    if stale:
        for line in stale:
            print("::error::Withers de PalaWither : %s n'est pas a jour. "
                  "Lance 'python3 -I tools/palawither_assets.py'." % line)
        sys.exit(1)
    print("Withers de PalaWither : %d withers, gemmes, ameliorations, receptacle et sons a jour" % len(WITHERS))


if __name__ == "__main__":
    main()
