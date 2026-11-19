#!/usr/bin/env python3
"""Les quatre trones de metier (niveau 95) : geometrie, modeles, recettes, butin, noms.

    python3 -I tools/throne_assets.py [--jar <jar de PalaMod extrait>]
    python3 -I tools/throne_assets.py --check

Le trone de PalaMod (`BlockMinerThrone` et ses trois freres, PalaJobs) est un
modele Blockbench pour ModelBase (`ModelJobThrone`, texture 128 x 128) dessine
par un TileEntitySpecialRenderer, avec le nom du proprietaire en rouge
au-dessus. La geometrie est relevee ici, boite par boite, depuis le
constructeur de `ModelJobThrone` (javap). Le script ecrit :

* `client/throne/ThroneGeometry.java` — la meme geometrie en code vanilla, pour
  le rendu du bloc pose (comme l'original : un modele d'entite, pas un modele
  de bloc, parce que le trone fait pres de trois blocs de haut et qu'un modele
  de bloc ne sort pas de -16..32) ;
* l'objet en 3D (`models/item/throne.json`), ses quatre textures, les
  blockstates, le modele de bloc (les particules seulement), le butin (le trone
  garde son proprietaire), les recettes et les noms.

Les textures viennent du jar de PalaMod (`textures/blocks/thrones/*.png`) :
`--jar` les recopie. Sans `--jar`, les textures deja posees restent.

`--check` (CI) refait tout en memoire et echoue si un fichier genere a change.

Reperes du modele d'origine : y vers le bas, sol a 24, et le rendu de 1.7.10
le tourne de 180 degres autour de z (x et y s'inversent). Le trone regarde le
nord dans l'orientation de base.
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "client", "throne",
                    "ThroneGeometry.java")
NS = "palamod"
TEXTURE = 128

# metier -> (texture du jar, nom FR, nom EN, objet du milieu de la recette)
THRONES = {
    "miner": ("miner.png", "Trone du mineur", "Miner Throne", "palamod:anti_aggro_obsidian"),
    "farmer": ("farmer.png", "Trone du fermier", "Farmer Throne", "palamod:green_paladium_seedplanter"),
    "hunter": ("hunter.png", "Trone du chasseur", "Hunter Throne", "palamod:paladium_horn"),
    "alchemist": ("alchemist.png", "Trone de l'alchimiste", "Alchemist Throne", "palamod:paladium_portal_block"),
}

# ModelJobThrone : (nom, parent, pivot, rotation en radians x y z, boites (u, v, x, y, z, w, h, d)).
GEOMETRY = [
    ("group", None, (6.0, 12.0, -1.0), (0.0, 0.0, 0.0), [
        (0, 41, -12.0, 3.0, -8.0, 12, 6, 14),
        (0, 0, -15.0, -20.0, 6.0, 18, 29, 5),
        (46, 0, -13.0, 9.0, -5.0, 14, 3, 13)]),
    ("bone", "group", (-6.0, -23.5, 8.5), (0.0, 0.0, 0.0), [
        (52, 72, -2.0, -8.5, -6.5, 4, 5, 4),
        (22, 63, -7.0, -10.5, -1.5, 4, 7, 4),
        (22, 63, 3.0, -10.5, -1.5, 4, 7, 4)]),
    ("cube_r1", "bone", (0.0, 0.0, 0.0), (0.0, 0.7854, 0.0), [
        (32, 20, -7.0, -3.5, -7.0, 14, 7, 14)]),
    ("bone2", "group", (5.2591, -4.8572, -1.0), (0.0, 0.0, 0.0), [
        (0, 61, -5.2591, 4.8572, -7.0, 4, 9, 14)]),
    ("cube_r2", "bone2", (0.0, 0.0, 0.0), (0.0, 0.0, 0.3927), [
        (64, 41, -3.0, -0.5, -7.0, 6, 1, 14)]),
    ("cube_r3", "bone2", (-4.573, 1.8942, 0.0), (0.0, 0.0, 0.3927), [
        (66, 56, -0.5, -3.0, -7.0, 1, 6, 14)]),
    ("cube_r4", "bone2", (-3.2336, -1.3394, 0.0), (0.0, 0.0, 0.3927), [
        (36, 67, -0.5, -0.5, -7.0, 1, 1, 14)]),
    ("cube_r5", "bone2", (-1.3394, 3.2336, -1.0), (0.0, 0.0, 0.3927), [
        (36, 45, -3.0, -3.0, -8.0, 6, 6, 16)]),
    ("bone3", "group", (-17.2591, -4.8572, -1.0), (0.0, 0.0, 0.0), [
        (0, 61, 1.2591, 4.8572, -7.0, 4, 9, 14)]),
    ("cube_r6", "bone3", (0.0, 0.0, 0.0), (0.0, 0.0, -0.3927), [
        (64, 41, -3.0, -0.5, -7.0, 6, 1, 14)]),
    ("cube_r7", "bone3", (4.573, 1.8942, 0.0), (0.0, 0.0, -0.3927), [
        (66, 56, -0.5, -3.0, -7.0, 1, 6, 14)]),
    ("cube_r8", "bone3", (3.2336, -1.3394, 0.0), (0.0, 0.0, -0.3927), [
        (36, 67, -0.5, -0.5, -7.0, 1, 1, 14)]),
    ("cube_r9", "bone3", (1.3394, 3.2336, -1.0), (0.0, 0.0, -0.3927), [
        (36, 45, -3.0, -3.0, -8.0, 6, 6, 16)]),
]

LANG = {
    "fr_fr": {
        "tooltip.palamod.throne.owner": "Trone de %s",
        "tooltip.palamod.throne.blank": "Pose-le : il prendra ton nom",
        "tooltip.palamod.throne.sit": "Clic droit pour t'y asseoir (metier niveau 95)",
        "message.palamod.throne.taken": "Quelqu'un est deja assis sur ce trone.",
        "entity.palamod.throne_seat": "Siege de trone",
    },
    "en_us": {
        "tooltip.palamod.throne.owner": "%s's throne",
        "tooltip.palamod.throne.blank": "Place it: it will take your name",
        "tooltip.palamod.throne.sit": "Right-click to sit (job level 95)",
        "message.palamod.throne.taken": "Someone is already sitting on this throne.",
        "entity.palamod.throne_seat": "Throne Seat",
    },
}


def number(value):
    """Un flottant Java : « 6F », « -4.573F »."""
    text = ("%.4f" % value).rstrip("0").rstrip(".")
    if text in ("-0", ""):
        text = "0"
    return text + "F"


def java_name(name):
    parts = name.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


def java_source():
    lines = [
        "package fr.atraxterra.dohy.client.throne;",
        "",
        "import net.minecraft.client.model.geom.ModelLayerLocation;",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.LayerDefinition;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "import net.minecraft.resources.Identifier;",
        "",
        "/**",
        " * GENERE par tools/throne_assets.py. Ne pas modifier a la main : relancer le script.",
        " *",
        " * <p>La geometrie de {@code ModelJobThrone} (PalaMod 10.3.0), boite par boite, sur",
        " * sa texture de 128 x 128. Repere des modeles d'entite : y vers le bas, sol a 24.",
        " */",
        "@SuppressWarnings(\"unused\")",
        "public final class ThroneGeometry {",
        "",
        "    public static final ModelLayerLocation LAYER = new ModelLayerLocation(",
        "            Identifier.fromNamespaceAndPath(\"palamod\", \"throne\"), \"main\");",
        "",
        "    private ThroneGeometry() {",
        "    }",
        "",
        "    public static LayerDefinition createLayer() {",
        "        MeshDefinition mesh = new MeshDefinition();",
        "        PartDefinition root = mesh.getRoot();",
    ]
    for name, parent, pivot, rotation, cubes in GEOMETRY:
        builder = "CubeListBuilder.create()"
        for u, v, x, y, z, w, h, d in cubes:
            builder += "\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)" % (
                u, v, number(x), number(y), number(z), number(w), number(h), number(d))
        pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(
            number(value) for value in (*pivot, *rotation))
        owner = java_name(parent) if parent else "root"
        lines.append("        PartDefinition %s = %s.addOrReplaceChild(\"%s\", %s,\n                %s);" % (
            java_name(name), owner, name, builder, pose))
    lines += [
        "        return LayerDefinition.create(mesh, %d, %d);" % (TEXTURE, TEXTURE),
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


# -- L'objet en 3D -----------------------------------------------------------------

def scale(value):
    return round(value * 16.0 / TEXTURE, 4)


def faces(u, v, w, h, d):
    """Les faces d'une boite de modele, apres le demi-tour autour de z du rendu d'origine.

    Les rectangles sont ceux de ModelPart.Cube (le meme depliage que le ModelBox de
    1.7.10) ; l'ordre des coins donne le sens, verifie face par face contre les
    sommets du modele : la face -x du modele devient l'est, la face -y le dessus.
    """
    rects = {
        "north": (u + d, v + d, u + d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        "east": (u, v + d, u + d, v + d + h),
        "west": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "up": (u + d + w, v + d, u + d, v),
        "down": (u + d + 2 * w, v, u + d + w, v + d),
    }
    return {face: {"uv": [scale(c) for c in rect], "texture": "#throne"} for face, rect in rects.items()}


def chain(name):
    """Le pivot cumule d'un noeud, et la rotation qu'il porte (une seule par chaine ici)."""
    by_name = {entry[0]: entry for entry in GEOMETRY}
    total = [0.0, 0.0, 0.0]
    rotation = (0.0, 0.0, 0.0)
    node = by_name[name]
    while node is not None:
        _name, parent, pivot, rot, _cubes = node
        total = [total[i] + pivot[i] for i in range(3)]
        if any(rot):
            if any(rotation):
                raise SystemExit("deux rotations dans la meme chaine : %s" % name)
            rotation = rot
        node = by_name[parent] if parent else None
    return total, rotation


def elements(offset_y):
    """Les boites en elements de modele. Le repere du jeu : x et y inverses, sol a 24 - offset."""
    out = []
    for name, _parent, _pivot, _rotation, cubes in GEOMETRY:
        pivot, rotation = chain(name)
        origin = (8.0 - pivot[0], 24.0 - offset_y - pivot[1], 8.0 + pivot[2])
        for u, v, x, y, z, w, h, d in cubes:
            frm = [origin[0] - (x + w), origin[1] - (y + h), origin[2] + z]
            to = [origin[0] - x, origin[1] - y, origin[2] + z + d]
            element = {"from": [round(c, 4) for c in frm], "to": [round(c, 4) for c in to],
                       "faces": faces(u, v, w, h, d)}
            rx, ry, rz = rotation
            if rz:
                # Le demi-tour autour de z ne change pas une rotation autour de z.
                element["rotation"] = {"origin": [round(c, 4) for c in origin], "axis": "z",
                                       "angle": round(rz * 180.0 / 3.141592653589793, 1)}
            elif ry:
                # Il retourne l'axe y : l'angle change de signe.
                element["rotation"] = {"origin": [round(c, 4) for c in origin], "axis": "y",
                                       "angle": round(-ry * 180.0 / 3.141592653589793, 1)}
            elif rx:
                element["rotation"] = {"origin": [round(c, 4) for c in origin], "axis": "x",
                                       "angle": round(-rx * 180.0 / 3.141592653589793, 1)}
            for corner in frm + to:
                if corner < -16 or corner > 32:
                    raise SystemExit("%s : une boite sort de -16..32 (%s)" % (name, corner))
            out.append(element)
    return out


# L'objet est descendu d'un bloc (y de -16 a 30) pour tenir dans -16..32 ; les
# transformations d'affichage le recentrent.
ITEM_OFFSET = 16.0
DISPLAY = {
    "gui": {"rotation": [30, 160, 0], "translation": [0.3, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.18, 0.18, 0.18]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0.3, 0], "scale": [0.3, 0.3, 0.3]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 10, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
    "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.22, 0.22, 0.22]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.22, 0.22, 0.22]},
}


def outputs():
    """Tous les fichiers texte generes : chemin -> contenu."""
    files = {JAVA: java_source()}

    def put(path, payload):
        files[os.path.join(RES, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    put("assets/%s/models/item/throne.json" % NS, {
        "textures": {"particle": "#throne"},
        "elements": elements(ITEM_OFFSET),
        "display": DISPLAY,
    })
    for job, (_texture, _fr, _en, middle) in THRONES.items():
        name = "%s_throne" % job
        texture = "%s:block/%s" % (NS, name)
        put("assets/%s/models/item/%s.json" % (NS, name),
            {"parent": "%s:item/throne" % NS, "textures": {"throne": texture, "particle": texture}})
        put("assets/%s/items/%s.json" % (NS, name),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        # Le bloc pose est dessine par son rendu : le modele ne porte que les particules.
        put("assets/%s/models/block/%s.json" % (NS, name), {"textures": {"particle": texture}})
        put("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s" % facing: {"model": "%s:block/%s" % (NS, name)}
            for facing in ("north", "south", "west", "east")}})
        put("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{
                "type": "minecraft:item", "name": "%s:%s" % (NS, name),
                "functions": [{"function": "minecraft:copy_components", "source": "block_entity",
                               "include": ["%s:throne_owner" % NS]}]}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        # CraftRegistry de PalaJobs : EOE / OFO / EOE.
        put("data/%s/recipe/%s.json" % (NS, name), {
            "type": "minecraft:crafting_shaped",
            "pattern": ["EOE", "OFO", "EOE"],
            "key": {"E": "palamod:endium_fragment", "O": middle, "F": "palamod:findium_block"},
            "result": {"id": "%s:%s" % (NS, name), "count": 1},
        })
    return files


def lang_updates():
    updates = {}
    for code, entries in LANG.items():
        labels = dict(entries)
        for job, (_texture, label_fr, label_en, _middle) in THRONES.items():
            labels["block.%s.%s_throne" % (NS, job)] = label_fr if code == "fr_fr" else label_en
        updates[code] = labels
    return updates


def main():
    check = "--check" in sys.argv
    jar = None
    if "--jar" in sys.argv:
        jar = sys.argv[sys.argv.index("--jar") + 1]

    stale = []
    for path, content in outputs().items():
        current = None
        if os.path.exists(path):
            with open(path, encoding="utf-8") as handle:
                current = handle.read()
        if current == content:
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(content)

    for code, labels in lang_updates().items():
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

    for job, (texture, _fr, _en, _middle) in THRONES.items():
        target = os.path.join(ASSETS, "textures", "block", "%s_throne.png" % job)
        if jar and not check:
            source = os.path.join(jar, "assets", "palamod", "textures", "blocks", "thrones", texture)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(source, target)
        elif not os.path.exists(target):
            stale.append("%s absente (relancer avec --jar)" % os.path.relpath(target, ROOT))

    if stale:
        for line in stale:
            print("::error::trones : %s n'est pas a jour. Lance 'python3 -I tools/throne_assets.py'." % line)
        sys.exit(1)
    print("trones : %d metiers, %d boites, fichiers a jour" % (
        len(THRONES), sum(len(entry[4]) for entry in GEOMETRY)))


if __name__ == "__main__":
    main()
