#!/usr/bin/env python3
"""Les lucky blocks : blocs, ecran d'ouverture, icones d'evenements et d'effets.

    python3 -I tools/lucky_assets.py [--jar <jar de PalaMod extrait>]
    python3 -I tools/lucky_assets.py --check
    python3 -I tools/lucky_assets.py --stats

Ce que le script ecrit sans le jar (et que --check verifie) :

  - les blockstates, modeles, definitions d'objet et tables de butin des trois
    lucky blocks (paladium, endium, findium) et des faux blocs de paladium de
    « Ouaiche t'es relou » et « Ouaiche t'es tres relou » ;
  - les modeles et definitions des objets des lucky blocks (LuckyItems) et les
    definitions d'equipement de leurs armures ;
  - les noms (blocs, effets et objets) en francais et en anglais.

Avec --jar (PIL requis), il recopie de PalaMod :

  - les textures des blocs (`textures/blocks/<type>_lucky_block.png`) ;
  - le cadre de la machine a sous (`gui/LuckyBlock/minecraft/foreground.png`,
    reduit de moitie : 907 x 489), le point d'interrogation et la croix vide ;
  - l'image de chaque evenement porte (`gui/LuckyBlock/events/`), mise au carre
    de 16 x 16 ; celle de « 20 000 lieu sous terre » manque dans le jar de
    PalaMod lui-meme : elle est dessinee ici ;
  - les icones d'effet (18 x 18), tirees des images des evenements ;
  - les icones des objets des lucky blocks et les couches portees de leurs
    armures (`textures/items/`, `textures/models/`) ;
  - et il dessine les filtres d'ecran (aplats), le chat de « I love cats », le
    billet, et les images qui reprenaient une autre licence (DRAWN).

Les evenements portes sont lus dans les sources Java (SOURCES) : --check echoue
si l'un d'eux n'a pas son image, ou porte un nom avec un accent.

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
# Les fichiers ou sont declares les evenements portes (c.classic / c.findium).
SOURCES = ("GoodEvents.java", "BadEvents.java", "FindiumEvents.java", "EffectEvents.java", "TrickEvents.java",
           "FindiumTricks.java", "ContentEvents.java", "FindiumContent.java",
           "LuckyItemEvents.java")
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
    # Le deuxieme lot.
    "rayman": ("Rayman", "Rayman", "rayman"),
    "ghost": ("Fantome", "Ghost", "ghost"),
    "rotate": ("Le monde a l'envers", "Upside Down", "reverse"),
    "steve": ("Steve", "Steve", "steve_jobs"),
    "flou": ("Ou sont mes lunettes ?", "Where Are My Glasses?", "flou"),
    "cat": ("I love cats", "I Love Cats", "i_love_cats"),
    "green": ("Filtre vert", "Green Filter", "vision_nocturne"),
    "tree": ("L'ami des arbres", "Tree Friend", "teamtree"),
    "boom_mobs": ("Boom les mobs", "Boom Mobs", "boom_les_mobs"),
    "explose": ("EXPLOSION !", "EXPLOSION!", "explosif"),
    "inversion": ("Inversion", "Inversion", "inversion"),
    "half_job_xp": ("Divise XP", "Half Job XP", "divise_xp"),
    "pumpkin_blur": ("Le fond du bol", "Bottom of the Bowl", "on_voit_pas_le_fond_du_bol"),
}

# Les filtres d'ecran (EventsManager.drawFilter) : des aplats, aux couleurs de
# greenfilter.png et floufilter.png de PalaMod, etires sur tout l'ecran.
FILTERS = {
    "green": (154, 255, 105, 121),
    "flou": (214, 214, 214, 222),
}

LANG = {
    "fr_fr": {"block.palamod.paladium_fake_block": "Bloc de paladium",
              "block.palamod.green_paladium_fake_block": "Bloc de paladium vert"},
    "en_us": {"block.palamod.paladium_fake_block": "Paladium Block",
              "block.palamod.green_paladium_fake_block": "Green Paladium Block"},
}

# Les faux blocs de « Ouaiche t'es relou » et de « Ouaiche t'es tres relou » : le modele
# du vrai bloc, et le vrai bloc au bout des quinze sauts.
FAKE_BLOCKS = {
    "paladium_fake_block": "paladium_block",
    "green_paladium_fake_block": "green_paladium_block",
}

# Les objets des lucky blocks (LuckyItems) : texture dans le jar (sous textures/), texture
# dans le mod (palamod:item/...), tenu en main comme une arme, noms francais et anglais.
# La fausse armure en endium reprend les icones de la vraie ; le billet, dont l'image
# venait d'un autre mod (customnpcs:npcMoney), est dessine ici.
ITEMS = {
    "pig_helmet": ("items/pig_helmet.png", "pig_helmet", False, "Casque de cochon", "Pig Helmet"),
    "pig_chestplate": ("items/pig_chestplate.png", "pig_chestplate", False, "Plastron de cochon",
                       "Pig Chestplate"),
    "pig_leggings": ("items/pig_leggings.png", "pig_leggings", False, "Jambieres de cochon", "Pig Leggings"),
    "pig_boots": ("items/pig_boots.png", "pig_boots", False, "Bottes de cochon", "Pig Boots"),
    "cave_helmet": ("items/cave_helmet.png", "cave_helmet", False, "Casque de speleologue", "Cave Helmet"),
    "invisible_helmet": ("items/invisible_helmet.png", "invisible_helmet", False, "Casque invisible",
                         "Invisible Helmet"),
    "invisible_chestplate": ("items/invisible_chestplate.png", "invisible_chestplate", False,
                             "Plastron invisible", "Invisible Chestplate"),
    "invisible_leggings": ("items/invisible_leggings.png", "invisible_leggings", False, "Jambieres invisibles",
                           "Invisible Leggings"),
    "invisible_boots": ("items/invisible_boots.png", "invisible_boots", False, "Bottes invisibles",
                        "Invisible Boots"),
    "fake_endium_helmet": (None, "endium_helmet", False, "Casque en faux endium", "Fake Endium Helmet"),
    "fake_endium_chestplate": (None, "endium_chestplate", False, "Plastron en faux endium",
                               "Fake Endium Chestplate"),
    "fake_endium_leggings": (None, "endium_leggings", False, "Jambieres en faux endium", "Fake Endium Leggings"),
    "fake_endium_boots": (None, "endium_boots", False, "Bottes en faux endium", "Fake Endium Boots"),
    "balaclava_helmet": ("items/balaclava_helmet.png", "balaclava_helmet", False, "Cagoule noire", "Balaclava"),
    "air_min_boots": ("items/airMin.png", "air_min_boots", False, "Air Min", "Air Min"),
    "speed_apple": ("items/speed_apple.png", "speed_apple", False, "Pomme de vitesse", "Speed Apple"),
    "calcium": ("items/calcium.png", "calcium", False, "Brique de lait", "Milk Carton"),
    "fake_money": (None, "fake_money", False, "Billet", "Banknote"),
    "couac_sword": ("items/couac_sword.png", "couac_sword", True, "Epee Couac", "Couac Sword"),
}

# Les couches portees des armures des lucky blocks (getArmorTexture) : materiau -> couche
# -> (texture, image du jar). L'armure invisible l'est parce que ses deux couches sont
# entierement transparentes ; la fausse armure en endium porte celles de la vraie.
EQUIPMENT = {
    "pig": {"humanoid": ("pig", "models/pig_armor_1.png"),
            "humanoid_leggings": ("pig", "models/pig_armor_2.png")},
    "cave_helmet": {"humanoid": ("cave_helmet", "models/cave_helmet_layer_1.png")},
    "invisible": {"humanoid": ("invisible", "models/invisible_armor_1.png"),
                  "humanoid_leggings": ("invisible", "models/invisible_armor_2.png")},
    "balaclava": {"humanoid": ("balaclava", "models/balaclava_helmet.png")},
    "air_min": {"humanoid": ("air_min", "models/air_min_boots.png")},
    "fake_endium": {"humanoid": ("endium", None), "humanoid_leggings": ("endium", None)},
}
# Sur le cote des AirMin portees, une petite marque sombre (une signature de marque) :
# effacee, la toile reprend sa couleur.
AIR_MIN_MARK = ((50, 50, 50, 255), (246, 230, 215, 255))

# Les images d'evenement rangees dans un sous-dossier du jar.
ALIASES = {
    "homme_invisible": "may/homme_invisible.png",
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


# Le coffre maudit (« Triforce » chez PalaMod, dont l'image reprend l'embleme d'une
# autre licence) : un coffre en endium frappe par un eclair, dessine ici.
CURSED_ICON = [
    "..........yYY...",
    "..OOOOOOOOYYOO..",
    ".ObbbbbbyYYbbbO.",
    ".ObBBBByYYYYYbO.",
    ".OBBBBBBByYYBBO.",
    ".ODDDDDLLYYDDDO.",
    ".OOOOOOLYYOOOOO.",
    ".OBBBByYYYYYBBO.",
    ".OBBBBBByYYBBBO.",
    ".OBBBBByYYBBBBO.",
    ".ODBBByYYBBBBDO.",
    ".OOOOOYYOOOOOOO.",
    "....yYY.........",
    "...yYY..........",
    "...yY...........",
    "................",
]
CURSED_COLORS = {
    ".": (0, 0, 0, 0), "O": (18, 22, 58, 255), "b": (96, 146, 255, 255), "B": (42, 88, 232, 255),
    "D": (22, 46, 140, 255), "L": (250, 200, 40, 255), "Y": (255, 232, 70, 255), "y": (196, 140, 10, 255),
}
# « N'en parle a personne » (l'image de PalaMod est le logo d'une plateforme video) :
# un dossier marque d'un tampon rouge, dessine ici.
FOLDER_ICON = [
    "................",
    ".OOOOO..........",
    ".OYYYYO.........",
    ".OYYYYYOOOOOOOO.",
    ".OYYYYYYYYYYYYO.",
    ".OOOOOOOOOOOOOO.",
    ".OyyyyyyyyyyyyO.",
    ".OyyRRRRRRRRyyO.",
    ".OyyRWWRWWWRyyO.",
    ".OyyRRRRRRRRyyO.",
    ".OyyyyyyyyyyyyO.",
    ".OyyyyyyyyyyyyO.",
    ".OyyyyyyyyyyyyO.",
    ".OOOOOOOOOOOOOO.",
    "................",
    "................",
]
FOLDER_COLORS = {
    ".": (0, 0, 0, 0), "O": (64, 40, 8, 255), "Y": (226, 170, 52, 255), "y": (250, 208, 98, 255),
    "R": (206, 28, 28, 255), "W": (255, 236, 236, 255),
}
# « J'ai glisse chef » (l'image de PalaMod reprend la peau de banane d'un jeu de course) :
# un panneau de sol glissant, dessine ici. Puis « Coup de turbo » (PalaMod montrait le
# numero d'une voiture de dessin anime) : la pomme de vitesse lancee ; « Air Min »
# (PalaMod montrait le logo d'une marque de baskets) : une basket ailee ; et le billet.
WET_ICON = [
    "................",
    "......OOOO......",
    ".....OYYYYO.....",
    ".....OYKKYO.....",
    "....OYYKKYYO....",
    "....OYYKKYYO....",
    "....OYYKKYYO....",
    "...OYYYKKYYYO...",
    "...OYYYYYYYYO...",
    "...OYYYKKYYYO...",
    "..OYYYYKKYYYYO..",
    "..OYbbYYYYbbYO..",
    "..ObbbbYYbbbbO..",
    "..OOOOOOOOOOOO..",
    "...Oo......oO...",
    "..OO........OO..",
]
WET_COLORS = {
    ".": (0, 0, 0, 0), "O": (60, 44, 6, 255), "Y": (250, 206, 40, 255), "K": (24, 24, 24, 255),
    "b": (70, 150, 230, 255), "o": (130, 100, 30, 255),
}
TURBO_ICON = [
    "................",
    "..........K.....",
    ".........KGG....",
    "........OOOOO...",
    ".......ObbBBBO..",
    "WWWW..ObWBBBBBO.",
    "......ObbBBBBBO.",
    "..WWWWOBBBBBBBO.",
    "......OBBBBBBBO.",
    "WWWW..OBBBBBBdO.",
    "......OBBBBBddO.",
    "..WW...OBBBddO..",
    ".......OOdOOO...",
    "........OO.OO...",
    "................",
    "................",
]
TURBO_COLORS = {
    ".": (0, 0, 0, 0), "O": (20, 58, 92, 255), "B": (120, 210, 240, 255), "b": (190, 240, 255, 255),
    "W": (255, 255, 255, 255), "d": (70, 150, 200, 255), "K": (80, 50, 20, 255), "G": (70, 170, 60, 255),
}
SNEAKER_ICON = [
    "................",
    "................",
    "WWWWWW..........",
    ".wwwwwWW........",
    "..WWWWWWW.......",
    "...wwwwwWW......",
    "....WWWWWW......",
    ".....wwwwW.OOOO.",
    "......OOOOOWWWWO",
    ".....OWWWWWGWWWO",
    ".....OWWWWWWGWWO",
    ".....OWWWWWWWWWO",
    ".....OrrrrrrrrrO",
    ".....OOOOOOOOOOO",
    "................",
    "................",
]
SNEAKER_COLORS = {
    ".": (0, 0, 0, 0), "O": (40, 40, 48, 255), "W": (246, 246, 250, 255), "w": (190, 214, 236, 255),
    "G": (150, 150, 160, 255), "r": (120, 120, 132, 255),
}
MONEY_ITEM = [
    "................",
    "................",
    "..OOOOOOOOOOOO..",
    "..OaaaaaaaaaaO..",
    "..OaOOOOOOOOOOOO",
    "..OaOggggggggggO",
    "..OaOgGggOOggGgO",
    "..OaOggOOddOOggO",
    "...OOggOdddOggO.",
    "....OggOOddOOggO",
    "....OgGggOOggGgO",
    "....OggggggggggO",
    "....OOOOOOOOOOOO",
    "................",
    "................",
    "................",
]
MONEY_COLORS = {
    ".": (0, 0, 0, 0), "O": (34, 74, 40, 255), "a": (150, 196, 140, 255), "g": (176, 222, 160, 255),
    "G": (92, 150, 84, 255), "d": (120, 176, 110, 255),
}

# Les images dessinees ici plutot que prises dans le jar.
DRAWN = {
    "teleportyone": (DEEP_ICON, DEEP_COLORS),
    "coffre_maudit": (CURSED_ICON, CURSED_COLORS),
    "dossier_secret": (FOLDER_ICON, FOLDER_COLORS),
    "sol_glissant": (WET_ICON, WET_COLORS),
    "coup_de_turbo": (TURBO_ICON, TURBO_COLORS),
    "baskets_ailees": (SNEAKER_ICON, SNEAKER_COLORS),
}
# Les textures d'objet dessinees ici.
DRAWN_ITEMS = {
    "fake_money": (MONEY_ITEM, MONEY_COLORS),
}


def ported():
    """Les evenements des sources Java : (genre, id, nom, image, malus, rarete)."""
    events = []
    pattern = re.compile(r'c\.(classic|findium)\(\s*([A-Z_]+|"[^"]+")\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,'
                         r'\s*(true|false)\s*,\s*(\d+)')
    constants = {}
    for name in SOURCES:
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
    for fake, real in FAKE_BLOCKS.items():
        put(os.path.join(ASSETS, "blockstates", fake + ".json"),
            {"variants": {"hits=%d" % hits: {"model": "%s:block/%s" % (NS, real)} for hits in range(16)}})
        # Au bout de ses quinze sauts, le faux bloc rend le vrai.
        put(os.path.join(DATA, "loot_table", "blocks", fake + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, real)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": "%s:blocks/%s" % (NS, fake),
        })
    for name, (_source, texture, handheld, _fr, _en) in ITEMS.items():
        put(os.path.join(ASSETS, "models", "item", name + ".json"), {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, texture)},
        })
        put(os.path.join(ASSETS, "items", name + ".json"),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
    # La definition d'equipement : c'est elle que l'ArmorMaterial designe par son nom.
    for material, layers in EQUIPMENT.items():
        put(os.path.join(ASSETS, "equipment", material + ".json"), {"layers": {
            layer: [{"texture": "%s:%s" % (NS, texture)}] for layer, (texture, _source) in layers.items()}})
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
        for name, (_source, _texture, _handheld, fr, en) in ITEMS.items():
            labels["item.%s.%s" % (NS, name)] = fr if french else en
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
        elif icon in ALIASES:
            image = square(Image.open(os.path.join(folder, ALIASES[icon])).convert("RGBA"))
        elif icon in DRAWN:
            image = pixels(*DRAWN[icon])
        else:
            raise SystemExit("image d'evenement introuvable dans le jar : %s" % icon)
        image.save(target)
        drawn[icon] = image
    for effect, (_fr, _en, icon) in EFFECTS.items():
        canvas = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        canvas.paste(drawn[icon], (1, 1), drawn[icon])
        canvas.save(os.path.join(effects, effect + ".png"))


def pixels(rows, colors):
    from PIL import Image

    image = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            image.putpixel((x, y), colors.get(char, (0, 0, 0, 0)))
    return image


def item_textures(jar):
    """Les icones des objets (16 x 16) et les couches portees des armures (64 x 32)."""
    from PIL import Image

    source = os.path.join(jar, "assets", "palamod", "textures")
    items = os.path.join(ASSETS, "textures", "item")
    os.makedirs(items, exist_ok=True)
    for name, (path, texture, _handheld, _fr, _en) in ITEMS.items():
        if name in DRAWN_ITEMS:
            pixels(*DRAWN_ITEMS[name]).save(os.path.join(items, texture + ".png"))
        elif path is not None:
            image = Image.open(os.path.join(source, path)).convert("RGBA")
            if image.size != (16, 16):
                raise SystemExit("icone d'objet de taille inattendue : %s %s" % (path, image.size))
            image.save(os.path.join(items, texture + ".png"))
    for material, layers in EQUIPMENT.items():
        for layer, (texture, path) in layers.items():
            if path is None:
                continue
            image = Image.open(os.path.join(source, path)).convert("RGBA")
            if image.size != (64, 32):
                raise SystemExit("couche portee de taille inattendue : %s %s" % (path, image.size))
            if material == "air_min":
                mark, cloth = AIR_MIN_MARK
                for y in range(image.height):
                    for x in range(image.width):
                        if image.getpixel((x, y)) == mark:
                            image.putpixel((x, y), cloth)
            folder = os.path.join(ASSETS, "textures", "entity", "equipment", layer)
            os.makedirs(folder, exist_ok=True)
            image.save(os.path.join(folder, texture + ".png"))


def draw_cat():
    """Le chat de « I love cats », dessine ici (PalaMod montrait une photo) : 64 x 74."""
    from PIL import Image, ImageDraw

    width, height = 64, 74
    image = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(image)
    # Un ciel de fin de journee, puis le sol.
    for y in range(height):
        if y < 46:
            k = y / 45.0
            color = (int(150 + 100 * k), int(190 + 5 * k), int(235 - 95 * k), 255)
        else:
            color = (104, 104, 110, 255) if (y // 2) % 2 == 0 else (98, 98, 104, 255)
        draw.line([(0, y), (width - 1, y)], fill=color)
    fur = (226, 140, 60, 255)
    dark = (176, 96, 34, 255)
    line = (110, 56, 20, 255)
    cream = (250, 232, 208, 255)
    # La queue, puis le corps couche.
    draw.ellipse([40, 54, 62, 66], fill=fur, outline=line)
    draw.line([(52, 56), (54, 64)], fill=dark, width=2)
    draw.ellipse([8, 44, 56, 70], fill=fur, outline=line)
    for x in (16, 24, 40, 48):
        draw.line([(x, 47), (x + 2, 55)], fill=dark, width=2)
    draw.ellipse([14, 62, 26, 71], fill=cream, outline=line)
    draw.ellipse([36, 62, 48, 71], fill=cream, outline=line)
    # La tete, les oreilles.
    draw.polygon([(17, 26), (20, 9), (29, 20)], fill=fur, outline=line)
    draw.polygon([(47, 26), (44, 9), (35, 20)], fill=fur, outline=line)
    draw.polygon([(20, 22), (21, 13), (26, 20)], fill=(240, 160, 160, 255))
    draw.polygon([(44, 22), (43, 13), (38, 20)], fill=(240, 160, 160, 255))
    draw.ellipse([15, 16, 49, 48], fill=fur, outline=line)
    for x in (28, 32, 36):
        draw.line([(x, 18), (x, 24)], fill=dark, width=2)
    draw.ellipse([23, 33, 41, 46], fill=cream, outline=None)
    # Les yeux mi-clos, le nez, la bouche, les moustaches.
    for x0 in (21, 37):
        draw.ellipse([x0, 27, x0 + 6, 32], fill=(120, 200, 90, 255), outline=line)
        draw.line([(x0, 28), (x0 + 6, 28)], fill=line, width=2)
        draw.line([(x0 + 3, 29), (x0 + 3, 31)], fill=(20, 20, 20, 255))
    draw.polygon([(30, 35), (34, 35), (32, 38)], fill=(236, 120, 140, 255))
    draw.line([(32, 38), (32, 40)], fill=line)
    draw.line([(32, 40), (29, 42)], fill=line)
    draw.line([(32, 40), (35, 42)], fill=line)
    for y, dy in ((37, -2), (39, 0), (41, 2)):
        draw.line([(22, y), (12, y + dy)], fill=(240, 240, 240, 255))
        draw.line([(42, y), (52, y + dy)], fill=(240, 240, 240, 255))
    return image


def overlays():
    """Les filtres et le chat des effets d'ecran, sans le jar de PalaMod."""
    from PIL import Image

    folder = os.path.join(ASSETS, "textures", "gui", "lucky", "overlay")
    os.makedirs(folder, exist_ok=True)
    for name, color in FILTERS.items():
        Image.new("RGBA", (16, 16), color).save(os.path.join(folder, name + ".png"))
    draw_cat().save(os.path.join(folder, "cat.png"))


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
        item_textures(jar)
        overlays()
    needed = ["textures/block/%s_lucky_block.png" % key for key in TYPES]
    needed += ["textures/gui/lucky/%s.png" % name for name in ("foreground", "unknown", "empty")]
    needed += ["textures/gui/lucky/events/%s.png" % icon for _k, _i, _l, icon, _b, _r in events]
    needed += ["textures/mob_effect/%s.png" % effect for effect in EFFECTS]
    needed += ["textures/gui/lucky/overlay/%s.png" % name for name in list(FILTERS) + ["cat"]]
    needed += ["textures/item/%s.png" % texture for _s, texture, _h, _f, _e in ITEMS.values()]
    needed += ["textures/entity/equipment/%s/%s.png" % (layer, texture)
               for layers in EQUIPMENT.values() for layer, (texture, _source) in layers.items()]
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
