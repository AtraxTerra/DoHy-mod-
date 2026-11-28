#!/usr/bin/env python3
"""Le Botaniste, lots 2a et 2b : le pot, les quinze plantes, l'arrosoir, la guitare, le filet,
les nibblers, le scanner de Genis, la mutation inconnue et le dechet de mutation.

    python3 -I tools/botanist_lab_assets.py --import JobsReborn-1.92.0.pala
    python3 -I tools/botanist_lab_assets.py --check

Les textures, les modeles et les animations sont ceux de JobsReborn 1.92.0
(Paladium Games), repris du jar fourni par le joueur ; credit dans le README
(« Credits des textures »). Le jar n'est pas dans le depot : --import ne sert
qu'a les reprendre, la CI ne fait que --check.

Ce que --import reprend :

  - les icones d'objet, telles quelles (16 x 16) : le pot, l'arrosoir, la
    guitare, les graines de mutation (assets/jobs/textures/items/botanist/) ;
    l'icone du pot sert aussi aux particules du bloc ;
  - le pot et les plantes, modeles Bedrock (minecraft:geometry) et textures :
    la geometrie est copiee telle quelle et lue par le mod a l'execution
    (client/rpg/BedrockModel, le moteur des monstres de donjon) ; les
    animations sont normalisees par tools/rpg_entities.py (images cles
    [t, x, y, z, interpolation]). Une plante a quatre etats, empiles dans son
    fichier : graine, bourgeon, fleur, fanee ; l'animation idle_state_<n>
    montre le sien et le pose au sol ;
  - les icones des besoins (assets/jobs/textures/needs/botanist/*.svg),
    rendues en PNG de 32 x 32 par rsvg-convert, et le losange du kit
    d'interface du botaniste (ui/botanist/kit/diamond*.svg) qui leur sert
    de fond ;
  - lot 2b : le scanner de Genis (modele, texture, animations idle,
    scan_in_progress, scan_complete et no_coal ; son icone d'objet), le
    nibbler (modele, texture, animations idle, walk, nibble et panic), les
    icones du filet, de la mutation inconnue et du dechet de mutation, et les
    sons du nibbler, du filet, du scanner et des stades d'une plante. Les
    evenements sonores sont declares a la main dans sounds.json (exacts, voir
    --check).

Ecrit aussi le JSON (blockstates, modeles des blocs reduits a leurs particules :
le pot, sa plante et le scanner sont dessines par leurs rendus), les objets, les
tables de butin, les recettes (celles de JobsReborn pour l'arrosoir, la guitare
et le filet, les notres pour le pot et le scanner) et les noms.
"""
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
NS = "palamod"

# id : (nom fr, nom en, nom fr de la graine, nom en de la graine). Sans accent : police du jeu.
# Les trois plantes de base (lot 2a), puis les douze du lot 2b, par climat et par rarete ; les noms
# anglais sont les identifiants de JobsReborn, les noms francais sont a nous.
MUTATIONS = {
    "ironwheat": ("Ble de fer", "Ironwheat", "Graine de ble de fer", "Ironwheat Seed"),
    "moonfrost": ("Givrelune", "Moonfrost", "Graine de givrelune", "Moonfrost Seed"),
    "sand_coral": ("Corail des sables", "Sand Coral", "Graine de corail des sables", "Sand Coral Seed"),
    "glowbloom": ("Luminelle", "Glowbloom", "Graine de luminelle", "Glowbloom Seed"),
    "puffbloom": ("Duvetine", "Puffbloom", "Graine de duvetine", "Puffbloom Seed"),
    "jade_maw": ("Gueule de jade", "Jade Maw", "Graine de gueule de jade", "Jade Maw Seed"),
    "motherroot": ("Racine-mere", "Motherroot", "Graine de racine-mere", "Motherroot Seed"),
    "thornwick": ("Meche-epine", "Thornwick", "Graine de meche-epine", "Thornwick Seed"),
    "songlily": ("Lys chantant", "Songlily", "Graine de lys chantant", "Songlily Seed"),
    "aurelys": ("Aurelys", "Aurelys", "Graine d'aurelys", "Aurelys Seed"),
    "arcanium": ("Arcanium", "Arcanium", "Graine d'arcanium", "Arcanium Seed"),
    "sunbloom": ("Fleur solaire", "Sunbloom", "Graine de fleur solaire", "Sunbloom Seed"),
    "smokepod": ("Cosse fumante", "Smokepod", "Graine de cosse fumante", "Smokepod Seed"),
    "miasma_bloom": ("Fleur des miasmes", "Miasma Bloom", "Graine de fleur des miasmes", "Miasma Bloom Seed"),
    "ember_orchid": ("Orchidee de braise", "Ember Orchid", "Graine d'orchidee de braise", "Ember Orchid Seed"),
}
POT = ("botanist_pot", "Pot de botaniste", "Botanist Pot")
SCANNER = ("gene_scanner", "Scanner de Genis", "Genis Scanner")
TOOLS = {
    "watering_can": ("Arrosoir", "Watering Can"),
    "guitar": ("Guitare", "Guitar"),
    "net": ("Filet", "Net"),
}
# Les objets plats du lot 2b : (nom fr, nom en).
LOOSE = {
    "unknown_mutation": ("Mutation inconnue", "Unknown Mutation"),
    "mutation_waste": ("Dechet de mutation", "Mutation Waste"),
}
NIBBLER = ("nibbler", "Nibbler", "Nibbler")
# Les icones de besoin : celles des besoins, et des objets que la configuration de JobsReborn
# reclame (charbon, poudre d'os, glowstone, pepite d'or, glace, sable, paladium).
NEEDS = ("water", "music", "shake", "burst", "nibbler", "coal", "bone_meal", "glowstone", "gold_nugget", "ice",
         "sand", "paladium")
# Les sons du lot 2b (assets/jobs/sounds/botanist/) : dossier -> fichiers, sans .ogg.
SOUNDS = {
    "nibbler": ["idle%d" % n for n in range(1, 8)] + ["panic"] + ["scream%d" % n for n in range(1, 6)]
               + ["walk%d" % n for n in range(1, 4)],
    "net": ["grab1", "grab2", "grab3", "nibblers1", "nibblers2", "nibblers3", "whoosh"],
    "scanner": ["no_coal", "scan_complete", "scan_in_progress"],
    "plant/common": ["bud1", "bud2", "bud3", "bud4", "blooming1", "blooming2", "blooming3", "wilted1", "wilted2",
                     "wilted3"],
}
# Les evenements sonores (sounds.json) : nom -> fichiers sous palamod:botanist/.
SOUND_EVENTS = {
    "botanist.nibbler.idle": ["nibbler/idle%d" % n for n in range(1, 8)],
    "botanist.nibbler.walk": ["nibbler/walk%d" % n for n in range(1, 4)],
    "botanist.nibbler.panic": ["nibbler/panic"],
    "botanist.nibbler.scream": ["nibbler/scream%d" % n for n in range(1, 6)],
    "botanist.net.grab": ["net/grab1", "net/grab2", "net/grab3"],
    "botanist.net.nibblers": ["net/nibblers1", "net/nibblers2", "net/nibblers3"],
    "botanist.net.whoosh": ["net/whoosh"],
    "botanist.scanner.no_coal": ["scanner/no_coal"],
    "botanist.scanner.scan_complete": ["scanner/scan_complete"],
    "botanist.scanner.scan_in_progress": ["scanner/scan_in_progress"],
    "botanist.plant.bud": ["plant/common/bud1", "plant/common/bud2", "plant/common/bud3", "plant/common/bud4"],
    "botanist.plant.blooming": ["plant/common/blooming1", "plant/common/blooming2", "plant/common/blooming3"],
    "botanist.plant.wilted": ["plant/common/wilted1", "plant/common/wilted2", "plant/common/wilted3"],
}
NEED_SIZE = 32
# Le losange du kit d'interface du botaniste (kit/diamond.svg) : le fond de l'icone de besoin.
BADGES = {"badge": "diamond", "badge_outline": "diamond_outline"}
BADGE_SIZE = 48

JAR_ITEMS = "assets/jobs/textures/items/botanist/"
JAR_BLOCKS = "assets/jobs/textures/blocks/botanist/"
JAR_MODELS = "assets/jobs/models/botanist/blocks/"
JAR_ENTITIES = "assets/jobs/models/botanist/entities/"
JAR_NEEDS = "assets/jobs/textures/needs/botanist/"
JAR_KIT = "assets/jobs/textures/ui/botanist/kit/"
JAR_SOUNDS = "assets/jobs/sounds/botanist/"


def copies():
    """Les fichiers copies tels quels : (chemin dans le jar, chemin sous assets/palamod, taille PNG ou None)."""
    pairs = [(JAR_ITEMS + "botanist_pot.png", "textures/item/botanist_pot.png", (16, 16)),
             (JAR_ITEMS + "botanist_pot.png", "textures/block/botanist_pot.png", (16, 16))]
    for tool in TOOLS:
        pairs.append((JAR_ITEMS + tool + ".png", "textures/item/%s.png" % tool, (16, 16)))
    for mutation in MUTATIONS:
        pairs.append((JAR_ITEMS + "mutation/%s.png" % mutation, "textures/item/%s_seed.png" % mutation, (16, 16)))
    pairs.append((JAR_MODELS + "pot/model.json", "botanist/pot/model.json", None))
    pairs.append((JAR_MODELS + "pot/texture.png", "textures/botanist/pot.png", (48, 48)))
    pairs.append((JAR_MODELS + "pot/texture_mutated.png", "textures/botanist/pot_mutated.png", (48, 48)))
    for mutation in MUTATIONS:
        pairs.append((JAR_MODELS + "mutations/%s/model.json" % mutation, "botanist/mutation/%s/model.json" % mutation,
                      None))
        pairs.append((JAR_MODELS + "mutations/%s/texture.png" % mutation, "textures/botanist/mutation/%s.png" % mutation,
                      "any"))
    # Lot 2b : le scanner, le nibbler, et les objets plats.
    pairs.append((JAR_BLOCKS + "gene_scanner.png", "textures/item/gene_scanner.png", (16, 16)))
    pairs.append((JAR_BLOCKS + "gene_scanner.png", "textures/block/gene_scanner.png", (16, 16)))
    pairs.append((JAR_MODELS + "scanner/model.json", "botanist/scanner/model.json", None))
    pairs.append((JAR_MODELS + "scanner/texture.png", "textures/botanist/scanner.png", (144, 144)))
    pairs.append((JAR_ENTITIES + "nibbler/model.json", "botanist/nibbler/model.json", None))
    pairs.append((JAR_ENTITIES + "nibbler/texture.png", "textures/botanist/nibbler.png", (48, 48)))
    for name in LOOSE:
        pairs.append((JAR_ITEMS + name + ".png", "textures/item/%s.png" % name, (16, 16)))
    for folder, names in SOUNDS.items():
        for name in names:
            pairs.append((JAR_SOUNDS + "%s/%s.ogg" % (folder, name), "sounds/botanist/%s/%s.ogg" % (folder, name), "ogg"))
    return pairs


def animations():
    """Les animations normalisees : (chemin dans le jar, chemin sous assets/palamod)."""
    pairs = [(JAR_MODELS + "pot/animation.json", "botanist/pot/animation.json")]
    for mutation in MUTATIONS:
        pairs.append((JAR_MODELS + "mutations/%s/animation.json" % mutation,
                      "botanist/mutation/%s/animation.json" % mutation))
    pairs.append((JAR_MODELS + "scanner/animation.json", "botanist/scanner/animation.json"))
    pairs.append((JAR_ENTITIES + "nibbler/animation.json", "botanist/nibbler/animation.json"))
    return pairs


def need_icons():
    """Les SVG rendus en PNG : (chemin dans le jar, chemin sous assets/palamod, cote en pixels)."""
    icons = [(JAR_NEEDS + need + ".svg", "textures/botanist/need/%s.png" % need, NEED_SIZE) for need in NEEDS]
    for name, svg in BADGES.items():
        icons.append((JAR_KIT + svg + ".svg", "textures/botanist/need/%s.png" % name, BADGE_SIZE))
    return icons


def imported_paths():
    return [target for _source, target, _size in copies()] + [target for _source, target in animations()] \
        + [target for _source, target, _size in need_icons()]


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def write_bytes(relative, data):
    path = os.path.join(ASSETS, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(data)


def import_from(jar):
    """Reprend du jar ce que liste copies(), animations() et need_icons()."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import rpg_entities  # nos outils : la normalisation des animations Bedrock

    rsvg = shutil.which("rsvg-convert")
    if rsvg is None:
        raise SystemExit("rsvg-convert introuvable : il rend les icones de besoin (SVG) en PNG")
    with zipfile.ZipFile(jar) as archive, tempfile.TemporaryDirectory() as scratch:
        names = set(archive.namelist())

        def read(source):
            if source not in names:
                raise SystemExit("%s absent du jar %s" % (source, jar))
            return archive.read(source)

        for source, target, size in copies():
            data = read(source)
            if size is None:
                json.loads(data.decode("utf-8"))
            elif size == "ogg":
                if data[:4] != b"OggS":
                    raise SystemExit("%s : pas un Ogg" % source)
            elif size == "any":
                if png_size(data) is None:
                    raise SystemExit("%s : pas un PNG" % source)
            elif png_size(data) != size:
                raise SystemExit("%s : %s au lieu de %s" % (source, png_size(data), size))
            write_bytes(target, data)
        for source, target in animations():
            raw = os.path.join(scratch, "animation.json")
            with open(raw, "wb") as handle:
                handle.write(read(source))
            converted = rpg_entities.convert_animations(raw)
            write_bytes(target, (json.dumps(converted, separators=(",", ":")) + "\n").encode("utf-8"))
        for source, target, side in need_icons():
            svg = os.path.join(scratch, "need.svg")
            png = os.path.join(scratch, "need.png")
            with open(svg, "wb") as handle:
                handle.write(read(source))
            subprocess.run([rsvg, "-w", str(side), "-h", str(side), "-o", png, svg], check=True)
            with open(png, "rb") as handle:
                write_bytes(target, handle.read())
    print("JobsReborn : %d fichiers repris de %s" % (len(imported_paths()), os.path.basename(jar)))


# -- JSON ----------------------------------------------------------------------------------------

def shaped(pattern, key, result, category="equipment"):
    return {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key,
            "result": {"id": "%s:%s" % (NS, result), "count": 1}}


def outputs():
    files = {}

    def put(root, path, payload):
        files[os.path.join(root, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    def item(name, parent="minecraft:item/generated"):
        put(ASSETS, "models/item/%s.json" % name, {"parent": parent, "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        put(ASSETS, "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})

    # Le bloc n'a que ses particules : le pot et sa plante sont dessines par BotanistPotRenderer.
    put(ASSETS, "models/block/botanist_pot.json", {"textures": {"particle": "%s:block/botanist_pot" % NS}})
    put(ASSETS, "blockstates/botanist_pot.json", {"variants": {"": {"model": "%s:block/botanist_pot" % NS}}})
    item(POT[0])
    for mutation in MUTATIONS:
        item(mutation + "_seed")
    for tool in TOOLS:
        item(tool, "minecraft:item/handheld")
    for name in LOOSE:
        item(name)
    # Le scanner : deux blocs de haut, sans modele propre (dessine par GeneScannerRenderer) ; l'objet
    # garde l'icone du jar.
    put(ASSETS, "models/block/%s.json" % SCANNER[0], {"textures": {"particle": "%s:block/%s" % (NS, SCANNER[0])}})
    put(ASSETS, "blockstates/%s.json" % SCANNER[0], {"variants": {
        "facing=%s,half=%s" % (facing, half): {"model": "%s:block/%s" % (NS, SCANNER[0])}
        for facing in ("north", "south", "west", "east") for half in ("lower", "upper")}})
    item(SCANNER[0])
    put(DATA, "loot_table/blocks/botanist_pot.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:botanist_pot" % NS}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # JobsBotanistRecipes : l'arrosoir et la guitare.
    put(DATA, "recipe/watering_can.json", shaped(
        ["IWI", "KIK", " K "],
        {"I": "%s:paladium_ingot" % NS, "W": "minecraft:water_bucket", "K": "%s:paladium_stick" % NS},
        "watering_can"))
    put(DATA, "recipe/guitar.json", shaped(
        [" SK", "PNS", "PP "],
        {"S": "%s:diamond_string" % NS, "K": "%s:paladium_stick" % NS, "P": "#minecraft:planks",
         "N": "minecraft:note_block"},
        "guitar"))
    # Le pot n'a pas de recette dans JobsReborn (ceux du laboratoire Genis) : terre cuite et terre.
    put(DATA, "recipe/botanist_pot.json",
        shaped(["T T", "TDT", "TTT"], {"T": "minecraft:terracotta", "D": "minecraft:dirt"}, POT[0], "misc"))
    # JobsBotanistRecipes : le filet (fil de diamant, boule de slime, baton de paladium).
    put(DATA, "recipe/net.json", shaped(
        [" SB", " KS", "K  "],
        {"S": "%s:diamond_string" % NS, "B": "minecraft:slime_ball", "K": "%s:paladium_stick" % NS}, "net"))
    # Le scanner n'a pas de recette dans JobsReborn (machine du laboratoire) : titane, verre, une
    # longue-vue pour l'oeil, de la redstone.
    put(DATA, "recipe/%s.json" % SCANNER[0], shaped(
        ["TGT", "GLG", "TRT"],
        {"T": "%s:titane_ingot" % NS, "G": "minecraft:glass", "L": "minecraft:spyglass", "R": "minecraft:redstone"},
        SCANNER[0], "misc"))
    # Le scanner rend son objet par sa moitie basse seulement.
    put(DATA, "loot_table/blocks/%s.json" % SCANNER[0], {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, SCANNER[0])}],
        "conditions": [{"condition": "minecraft:survives_explosion"},
                       {"condition": "minecraft:block_state_property", "block": "%s:%s" % (NS, SCANNER[0]),
                        "properties": {"half": "lower"}}]}]})
    return files


LANG = {
    "fr_fr": {
        "tooltip.palamod.mutation_seed.climate": "Climat : %s",
        "tooltip.palamod.mutation_seed.growth": "Bourgeon en %s, fleur %s plus tard",
        "tooltip.palamod.mutation_seed.harvests": "%s recoltes, une fleur toutes les %s",
        "tooltip.palamod.mutation_seed.pot": "Se plante dans un pot de botaniste. Pousse pres de toi seulement.",
        "tooltip.palamod.botanist.climate.cold": "Glacial",
        "tooltip.palamod.botanist.climate.temperate": "Normal",
        "tooltip.palamod.botanist.climate.hot": "Desertique",
        "tooltip.palamod.watering_can.charges": "Charges : %s",
        "tooltip.palamod.watering_can.fill": "Clic droit sur une source d'eau pour le remplir.",
        "tooltip.palamod.guitar.use": "Joue un air aux plantes qui en reclament.",
        "tooltip.palamod.botanist.tool.use": "Clic droit sur une plante qui le reclame.",
        "message.palamod.botanist_lab.catch_up": "Laboratoire du botaniste, pour tes niveaux de Fermier :",
        "tooltip.palamod.mutation_seed.rarity": "Rarete : %s",
        "tooltip.palamod.mutation_seed.level": "Fermier %s pour la planter",
        "tooltip.palamod.botanist.rarity.common": "Commune",
        "tooltip.palamod.botanist.rarity.rare": "Rare",
        "tooltip.palamod.botanist.rarity.epic": "Epique",
        "tooltip.palamod.botanist.rarity.legendary": "Legendaire",
        "tooltip.palamod.net.nibblers": "Nibblers : %s / %s",
        "tooltip.palamod.net.catch": "Clic droit sur un nibbler pour l'attraper.",
        "tooltip.palamod.unknown_mutation.scan": "A analyser au Scanner de Genis.",
        "tooltip.palamod.mutation_waste.use": "Residu sans usage d'un essai rate.",
        "tooltip.palamod.gene_scanner.use": "Revele la mutation d'une graine et l'ajoute a ton codex.",
        "tooltip.palamod.gene_scanner.fuel": "Chaque scan consomme 1 charbon.",
        "container.palamod.gene_scanner": "Scanner",
        "message.palamod.botanist_lab2.catch_up": "Scanner et filet du botaniste, pour tes niveaux de Fermier :",
    },
    "en_us": {
        "tooltip.palamod.mutation_seed.climate": "Climate: %s",
        "tooltip.palamod.mutation_seed.growth": "Bud in %s, flower %s later",
        "tooltip.palamod.mutation_seed.harvests": "%s harvests, a flower every %s",
        "tooltip.palamod.mutation_seed.pot": "Plant in a botanist pot. Only grows near you.",
        "tooltip.palamod.botanist.climate.cold": "Glacial",
        "tooltip.palamod.botanist.climate.temperate": "Normal",
        "tooltip.palamod.botanist.climate.hot": "Desert",
        "tooltip.palamod.watering_can.charges": "Charges: %s",
        "tooltip.palamod.watering_can.fill": "Right-click a water source to fill it.",
        "tooltip.palamod.guitar.use": "Plays a tune to plants that ask for one.",
        "tooltip.palamod.botanist.tool.use": "Right-click a plant that asks for it.",
        "message.palamod.botanist_lab.catch_up": "Botanist lab, for your Farmer levels:",
        "tooltip.palamod.mutation_seed.rarity": "Rarity: %s",
        "tooltip.palamod.mutation_seed.level": "Farmer %s to plant it",
        "tooltip.palamod.botanist.rarity.common": "Common",
        "tooltip.palamod.botanist.rarity.rare": "Rare",
        "tooltip.palamod.botanist.rarity.epic": "Epic",
        "tooltip.palamod.botanist.rarity.legendary": "Legendary",
        "tooltip.palamod.net.nibblers": "Nibblers: %s / %s",
        "tooltip.palamod.net.catch": "Right-click a nibbler to catch it.",
        "tooltip.palamod.unknown_mutation.scan": "To analyze at the Genis Scanner.",
        "tooltip.palamod.mutation_waste.use": "Useless residue of a failed attempt.",
        "tooltip.palamod.gene_scanner.use": "Reveals a seed's mutation and adds it to your codex.",
        "tooltip.palamod.gene_scanner.fuel": "Each scan uses 1 coal.",
        "container.palamod.gene_scanner": "Scanner",
        "message.palamod.botanist_lab2.catch_up": "Botanist scanner and net, for your Farmer levels:",
    },
}


def lang_updates():
    updates = {}
    for code, entries in LANG.items():
        labels = dict(entries)
        french = code == "fr_fr"
        for mutation, (_name_fr, _name_en, seed_fr, seed_en) in MUTATIONS.items():
            labels["item.%s.%s_seed" % (NS, mutation)] = seed_fr if french else seed_en
        labels["block.%s.%s" % (NS, POT[0])] = POT[1] if french else POT[2]
        labels["block.%s.%s" % (NS, SCANNER[0])] = SCANNER[1] if french else SCANNER[2]
        for tool, (tool_fr, tool_en) in TOOLS.items():
            labels["item.%s.%s" % (NS, tool)] = tool_fr if french else tool_en
        for name, (name_fr, name_en) in LOOSE.items():
            labels["item.%s.%s" % (NS, name)] = name_fr if french else name_en
        labels["entity.%s.%s" % (NS, NIBBLER[0])] = NIBBLER[1] if french else NIBBLER[2]
        updates[code] = labels
    return updates


def check_sounds():
    """Les evenements de sounds.json (edite a la main) : presents, et pointant sur nos fichiers."""
    problems = []
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as handle:
        sounds = json.load(handle)
    for event, files in SOUND_EVENTS.items():
        entry = sounds.get(event) or {}
        listed = [item if isinstance(item, str) else item.get("name") for item in entry.get("sounds", [])]
        wanted = ["%s:botanist/%s" % (NS, name) for name in files]
        if listed != wanted:
            problems.append("sounds.json : %s -> %s" % (event, ", ".join(wanted)))
    return problems


def main():
    check = "--check" in sys.argv
    jar = sys.argv[sys.argv.index("--import") + 1] if "--import" in sys.argv else None
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
    stale.extend(check_sounds())
    if jar and not check:
        import_from(jar)
    for relative in imported_paths():
        if not os.path.exists(os.path.join(ASSETS, relative)):
            stale.append("%s absent (--import JobsReborn-1.92.0.pala)" % relative)
    if stale:
        for line in stale:
            print("::error::Laboratoire du botaniste : %s n'est pas a jour. "
                  "Lance 'python3 -I tools/botanist_lab_assets.py'." % line)
        sys.exit(1)
    print("Laboratoire du botaniste : pot, %d plantes, graines, outils, scanner, nibbler, sons, recettes et noms "
          "a jour" % len(MUTATIONS))


if __name__ == "__main__":
    main()
