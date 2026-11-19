#!/usr/bin/env python3
"""Les lucky blocks : blocs, ecran d'ouverture, icones d'evenements et d'effets.

    python3 -I tools/lucky_assets.py [--jar <jar de PalaMod extrait>]
    python3 -I tools/lucky_assets.py --check
    python3 -I tools/lucky_assets.py --stats

Ce que le script ecrit sans le jar (et que --check verifie) :

  - les blockstates, modeles, definitions d'objet et tables de butin des trois
    lucky blocks (paladium, endium, findium) et du faux bloc de paladium de
    « Ouaiche t'es relou » ;
  - les noms (blocs et effets) en francais et en anglais.

Avec --jar (PIL requis), il recopie de PalaMod :

  - les textures des blocs (`textures/blocks/<type>_lucky_block.png`) ;
  - le cadre de la machine a sous (`gui/LuckyBlock/minecraft/foreground.png`,
    reduit de moitie : 907 x 489), le point d'interrogation et la croix vide ;
  - l'image de chaque evenement porte (`gui/LuckyBlock/events/`), mise au carre
    de 16 x 16 ; celle de « 20 000 lieu sous terre » manque dans le jar de
    PalaMod lui-meme : elle est dessinee ici ;
  - les icones d'effet (18 x 18), tirees des images des evenements.

Les evenements portes sont lus dans les sources Java (GoodEvents, BadEvents,
FindiumEvents) : --check echoue si l'un d'eux n'a pas son image, ou porte un
nom avec un accent.

--stats compare le catalogue complet de PalaMod (`tools/data/lucky_events.json`,
439 evenements) a ce qui est porte : la part de bons tirages par lucky block,
bonus du Lucky Pass compris (les constantes de LuckyEvents), et la part de la
masse de tirage deja couverte.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "palamod")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "palamod")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "palamod", "lucky")
CATALOG = os.path.join(ROOT, "tools", "data", "lucky_events.json")
NS = "palamod"

TYPES = {
    "paladium": ("Lucky Block en paladium", "Paladium Lucky Block"),
    "endium": ("Lucky Block en endium", "Endium Lucky Block"),
    "findium": ("Lucky Block en findium", "Findium Lucky Block"),
}

# Les effets des evenements : nom francais (lang de PalaMod), anglais, image d'evenement.
EFFECTS = {
    "super_man": ("SuperMan", "SuperMan", "superman"),
    "walk_in_music": ("Bouge en rythme", "Walk in Music", "walk_in_music"),
    "fire_man": ("Tout feu tout flamme", "Fire Man", "tout_feu_tout_flamme"),
    "bunny_life": ("Gros Relou", "Bunny Life", "bunny_life"),
    "hide_head": ("Ou donner de la tete", "Where Is My Head", "hidehead"),
    "frozen_ground": ("Sol glace", "Frozen Ground", "frozenground"),
    "no_jump": ("Mais pas si haut !", "No Jump", "sonic"),
    "third_person": ("Prendre du recul", "Step Back", "recul"),
    "anvil_money": ("Money", "Anvil Money", "danselighting"),
}

LANG = {
    "fr_fr": {"block.palamod.paladium_fake_block": "Bloc de paladium"},
    "en_us": {"block.palamod.paladium_fake_block": "Paladium Block"},
}

# L'image de TeleportYOne, absente du jar de PalaMod : une fleche jaune qui
# plonge dans l'ardoise des profondeurs.
DEEP_ICON = [
    "SSSSSSSSSSSSSSSS",
    "SsSSSSOOOOSSSsSS",
    "SSSSSSOYYOSSSSSS",
    "SSsSSSOYYOSSSSsS",
    "SSSSSSOYYOSSSSSS",
    "SSSSSSOYYOSSSsSS",
    "SsSSSSOYYOSSSSSS",
    "SSSOOOOYYOOOOSSS",
    "SSSOYYYYYYYYOSsS",
    "SsSSOYYYYYYOSSSS",
    "SSSSSOYYYYOSSSSS",
    "SSsSSSOYYOSSSSsS",
    "SSSSSSSOOSSSSSSS",
    "SDDDDDDDDDDDDDDS",
    "SDdDDDdDDDDdDDDS",
    "SSSSSSSSSSSSSSSS",
]
DEEP_COLORS = {
    "S": (58, 58, 64, 255), "s": (72, 72, 80, 255), "O": (40, 26, 0, 255), "Y": (250, 196, 0, 255),
    "D": (100, 220, 220, 255), "d": (60, 170, 170, 255),
}


def ported():
    """Les evenements des sources Java : (genre, id, nom, image, malus, rarete)."""
    events = []
    pattern = re.compile(r'c\.(classic|findium)\(\s*([A-Z_]+|"[^"]+")\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,'
                         r'\s*(true|false)\s*,\s*(\d+)')
    constants = {}
    for name in ("GoodEvents.java", "BadEvents.java", "FindiumEvents.java"):
        path = os.path.join(JAVA, name)
        text = open(path, encoding="utf-8").read()
        for key, value in re.findall(r'static final String ([A-Z_]+) = "([^"]+)";', text):
            constants[key] = value
        for kind, ident, label, icon, bad, rarity in pattern.findall(text):
            ident = constants.get(ident, ident.strip('"'))
            events.append((kind, ident, label, icon, bad == "true", int(rarity)))
    return events


def outputs():
    files = {}

    def put(path, payload):
        files[os.path.join(path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    for key in TYPES:
        name = "%s_lucky_block" % key
        put(os.path.join(ASSETS, "blockstates", name + ".json"),
            {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
        put(os.path.join(ASSETS, "models", "block", name + ".json"),
            {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/%s" % (NS, name)}})
        put(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s" % (NS, name)})
        put(os.path.join(ASSETS, "items", name + ".json"),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        # Casse, il se rend avec l'evenement deja tire ({@code onBlockHarvested}).
        put(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{
                    "type": "minecraft:item",
                    "name": "%s:%s" % (NS, name),
                    "functions": [{
                        "function": "minecraft:copy_components",
                        "source": "block_entity",
                        "include": ["%s:lucky_event" % NS, "%s:lucky_chained" % NS],
                    }],
                }],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": "%s:blocks/%s" % (NS, name),
        })
    put(os.path.join(ASSETS, "blockstates", "paladium_fake_block.json"),
        {"variants": {"hits=%d" % hits: {"model": "%s:block/paladium_block" % NS} for hits in range(16)}})
    # Au bout de ses quinze sauts, le faux bloc rend un vrai bloc de paladium.
    put(os.path.join(DATA, "loot_table", "blocks", "paladium_fake_block.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "%s:paladium_block" % NS}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "%s:blocks/paladium_fake_block" % NS,
    })
    return files


def lang_updates():
    updates = {}
    for code, entries in LANG.items():
        labels = dict(entries)
        french = code == "fr_fr"
        for key, (fr, en) in TYPES.items():
            labels["block.%s.%s_lucky_block" % (NS, key)] = fr if french else en
        for effect, (fr, en, _icon) in EFFECTS.items():
            labels["effect.%s.%s" % (NS, effect)] = fr if french else en
        updates[code] = labels
    return updates


def square(image):
    """Une image d'evenement (15 x 15, 14 x 16...) posee sur 16 x 16."""
    from PIL import Image

    canvas = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    if image.size == (16, 16):
        return image
    image.thumbnail((16, 16), Image.NEAREST)
    canvas.paste(image, ((16 - image.width) // 2, (16 - image.height) // 2), image)
    return canvas


def textures(jar, events):
    from PIL import Image

    source = os.path.join(jar, "assets", "palamod", "textures")
    block_dir = os.path.join(ASSETS, "textures", "block")
    gui = os.path.join(ASSETS, "textures", "gui", "lucky")
    icons = os.path.join(gui, "events")
    effects = os.path.join(ASSETS, "textures", "mob_effect")
    for folder in (block_dir, gui, icons, effects):
        os.makedirs(folder, exist_ok=True)
    for key in TYPES:
        Image.open(os.path.join(source, "blocks", "%s_lucky_block.png" % key)).convert("RGBA").save(
            os.path.join(block_dir, "%s_lucky_block.png" % key))
    frame = Image.open(os.path.join(source, "gui", "LuckyBlock", "minecraft", "foreground.png")).convert("RGBA")
    frame.resize((frame.width // 2, frame.height // 2), Image.LANCZOS).save(os.path.join(gui, "foreground.png"))
    Image.open(os.path.join(source, "gui", "LuckyBlock", "minecraft", "unknown.png")).convert("RGBA").save(
        os.path.join(gui, "unknown.png"))
    Image.open(os.path.join(source, "gui", "LuckyPass", "minecraft", "empty.png")).convert("RGBA").save(
        os.path.join(gui, "empty.png"))
    folder = os.path.join(source, "gui", "LuckyBlock", "events")
    available = {name.lower(): name for name in os.listdir(folder) if name.endswith(".png")}
    wanted = {icon for _kind, _ident, _label, icon, _bad, _rarity in events}
    wanted |= {icon for _fr, _en, icon in EFFECTS.values()}
    drawn = {}
    for icon in sorted(wanted):
        target = os.path.join(icons, icon + ".png")
        if icon + ".png" in available:
            image = square(Image.open(os.path.join(folder, available[icon + ".png"])).convert("RGBA"))
        elif icon == "teleportyone":
            image = Image.new("RGBA", (16, 16))
            for y, row in enumerate(DEEP_ICON):
                for x, char in enumerate(row):
                    image.putpixel((x, y), DEEP_COLORS[char])
        else:
            raise SystemExit("image d'evenement introuvable dans le jar : %s" % icon)
        image.save(target)
        drawn[icon] = image
    for effect, (_fr, _en, icon) in EFFECTS.items():
        canvas = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        canvas.paste(drawn[icon], (1, 1), drawn[icon])
        canvas.save(os.path.join(effects, effect + ".png"))


def stats(events):
    catalog = json.load(open(CATALOG, encoding="utf-8"))
    ids = {ident for _kind, ident, _label, _icon, _bad, _rarity in events}
    print("%d evenements portes sur %d" % (len(ids), len(catalog)))
    for key in ("PALADIUM", "ENDIUM", "FINDIUM"):
        pool = [event for event in catalog if key in event["types"]]
        good = sum(1.0 / event["rarity"] for event in pool if not event["bad"])
        bad = sum(1.0 / event["rarity"] for event in pool if event["bad"])
        share = 1.25 * good / (1.25 * good + bad)
        mine = [event for event in pool if event["id"] in ids]
        my_good = sum(1.0 / event["rarity"] for event in mine if not event["bad"])
        my_bad = sum(1.0 / event["rarity"] for event in mine if event["bad"])
        print("%-8s %3d/%3d portes  bons %.4f (Lucky Pass compris)  masse couverte : bons %4.1f %%, malus %4.1f %%"
              % (key, len(mine), len(pool), share, 100.0 * my_good / good if good else 0.0,
                 100.0 * my_bad / bad if bad else 0.0))


def main():
    check = "--check" in sys.argv
    jar = sys.argv[sys.argv.index("--jar") + 1] if "--jar" in sys.argv else None
    events = ported()
    if "--stats" in sys.argv:
        stats(events)
        return
    stale = []
    seen = set()
    catalog = {event["id"]: event for event in json.load(open(CATALOG, encoding="utf-8"))}
    for kind, ident, label, icon, bad, rarity in events:
        if ident in seen:
            stale.append("evenement en double : %s" % ident)
        seen.add(ident)
        if any(ord(char) > 127 for char in label):
            stale.append("nom avec accent : %s (%s)" % (ident, label))
        if not re.fullmatch(r"[a-z0-9_.\-]+", icon):
            stale.append("image au nom invalide : %s (%s)" % (ident, icon))
        known = catalog.get(ident)
        if known is None:
            stale.append("evenement inconnu de PalaMod : %s" % ident)
        elif known["rarity"] != rarity or known["bad"] != bad:
            stale.append("%s : rarete ou malus different de PalaMod (%s, %s)" % (ident, known["rarity"], known["bad"]))
        elif (kind == "findium") != ("FINDIUM" in known["types"]):
            stale.append("%s : pas le bon lucky block" % ident)
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
        textures(jar, events)
    needed = ["textures/block/%s_lucky_block.png" % key for key in TYPES]
    needed += ["textures/gui/lucky/%s.png" % name for name in ("foreground", "unknown", "empty")]
    needed += ["textures/gui/lucky/events/%s.png" % icon for _k, _i, _l, icon, _b, _r in events]
    needed += ["textures/mob_effect/%s.png" % effect for effect in EFFECTS]
    for relative in needed:
        if not os.path.exists(os.path.join(ASSETS, relative)):
            stale.append("%s absente (relancer avec --jar)" % relative)
    if stale:
        for line in stale:
            print("::error::lucky blocks : %s. Lance 'python3 -I tools/lucky_assets.py'." % line)
        sys.exit(1)
    print("lucky blocks : %d evenements, blocs, ecran, icones et noms a jour" % len(events))


if __name__ == "__main__":
    main()
