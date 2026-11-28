#!/usr/bin/env python3
"""Les potions d'XP de metier : textures, icones d'effet, modeles, objets, noms.

    python3 -I tools/xp_potion_assets.py [--jar <jar de PalaMod extrait>]
    python3 -I tools/xp_potion_assets.py --check

La Potion de Double XP est celle de PalaMod (`textures/items/boost/PotionDoubleXp.png`,
16 x 16) : `--jar` la recopie. La Triple n'existe pas dans PalaMod ; sa texture
est la meme fiole, le liquide passe a l'or et le « 2 » redessine en « 3 ». Les
icones d'effet (18 x 18) sont les deux fioles, centrees. PIL est requis avec
`--jar`, pas pour `--check`.

`--check` (CI) refait les modeles, les objets et les noms en memoire, et echoue
si un fichier a change ou si une texture manque.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "palamod")
NS = "palamod"

POTIONS = {
    "double_xp_potion": ("Potion de Double XP", "Double XP Potion", "double_job_xp",
                         "Double XP metier", "Double Job XP"),
    "triple_xp_potion": ("Potion de Triple XP", "Triple XP Potion", "triple_job_xp",
                         "Triple XP metier", "Triple Job XP"),
}

LANG = {
    "fr_fr": {
        "item.palamod.xp_potion.named": "%s (%s)",
        "tooltip.palamod.xp_potion": "%s XP sur tous tes metiers pendant %s de jeu",
        "tooltip.palamod.xp_potion.stack": "La duree s'ajoute ; le x3 passe avant le x2.",
        "message.palamod.xp_potion": "XP de metier %s : encore %s",
    },
    "en_us": {
        "item.palamod.xp_potion.named": "%s (%s)",
        "tooltip.palamod.xp_potion": "%s XP on all your jobs for %s of play",
        "tooltip.palamod.xp_potion.stack": "Drunk while active: the time adds up. x3 overrides x2.",
        "message.palamod.xp_potion": "Job XP %s: %s left",
    },
}

# La fiole de PalaMod : verts du liquide et du verre -> ors, pour la Triple.
GOLD = {
    (132, 202, 40): (214, 160, 30),
    (146, 222, 48): (236, 182, 40),
    (115, 243, 65): (255, 214, 80),
    (85, 134, 22): (160, 110, 15),
    (207, 254, 188): (255, 241, 196),
    (175, 255, 143): (255, 226, 140),
}
# Le chiffre : (x, y) -> couleur. Le « 2 » de PalaMod devient un « 3 ».
LIGHT = (255, 214, 80)
DARK = (81, 60, 3)
THREE = {(8, 8): LIGHT, (9, 9): DARK, (9, 10): (236, 182, 40), (10, 10): DARK}


def outputs():
    files = {}

    def put(path, payload):
        files[os.path.join(ASSETS, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    for name in POTIONS:
        put("models/item/%s.json" % name,
            {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        put("items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
    return files


def lang_updates():
    updates = {}
    for code, entries in LANG.items():
        labels = dict(entries)
        for name, (item_fr, item_en, effect, effect_fr, effect_en) in POTIONS.items():
            labels["item.%s.%s" % (NS, name)] = item_fr if code == "fr_fr" else item_en
            labels["effect.%s.%s" % (NS, effect)] = effect_fr if code == "fr_fr" else effect_en
        updates[code] = labels
    return updates


def textures(jar):
    from PIL import Image

    source = Image.open(os.path.join(jar, "assets", "palamod", "textures", "items", "boost",
                                     "PotionDoubleXp.png")).convert("RGBA")
    double = source.copy()
    triple = source.copy()
    for x in range(triple.width):
        for y in range(triple.height):
            r, g, b, a = triple.getpixel((x, y))
            if a and (r, g, b) in GOLD:
                triple.putpixel((x, y), GOLD[(r, g, b)] + (a,))
    for (x, y), color in THREE.items():
        triple.putpixel((x, y), color + (255,))
    for name, image in (("double_xp_potion", double), ("triple_xp_potion", triple)):
        image.save(os.path.join(ASSETS, "textures", "item", "%s.png" % name))
        icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        icon.paste(image, (1, 1))
        os.makedirs(os.path.join(ASSETS, "textures", "mob_effect"), exist_ok=True)
        icon.save(os.path.join(ASSETS, "textures", "mob_effect", "%s.png" % POTIONS[name][2]))


def main():
    check = "--check" in sys.argv
    jar = sys.argv[sys.argv.index("--jar") + 1] if "--jar" in sys.argv else None
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
    if jar and not check:
        textures(jar)
    for name, (_fr, _en, effect, _efr, _een) in POTIONS.items():
        for relative in ("textures/item/%s.png" % name, "textures/mob_effect/%s.png" % effect):
            if not os.path.exists(os.path.join(ASSETS, relative)):
                stale.append("%s absente (relancer avec --jar)" % relative)
    if stale:
        for line in stale:
            print("::error::potions d'XP : %s n'est pas a jour. Lance 'python3 -I tools/xp_potion_assets.py'." % line)
        sys.exit(1)
    print("potions d'XP : %d potions, textures, icones, modeles et noms a jour" % len(POTIONS))


if __name__ == "__main__":
    main()
