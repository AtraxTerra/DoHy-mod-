#!/usr/bin/env python3
"""Genere les assets et les donnees du namespace palamod.

Pourquoi un generateur plutot que 150 fichiers ecrits a la main : chaque item
demande trois fichiers (texture, modele, definition d'item) et chaque bloc six.
Pour 24 items et 13 blocs cela fait plus de 150 fichiers dont la seule variable
est un nom. Les ecrire a la main garantit une faute de frappe quelque part, et
une faute de frappe dans un chemin de modele ne fait pas echouer la compilation
— elle donne une texture violette en jeu, trois semaines plus tard.

    # 1. extraire le jar de PalaMod (c'est un zip)
    mkdir palamod && cd palamod && unzip ../PalaMod-10.3.0.pala
    # 2. generer
    python3 tools/palamod_assets.py palamod

Les textures viennent du jar de PalaMod, pas du pack Faithful : ce sont les
textures *par defaut* du mod. Le pack officiel les remplace ensuite, ce qui est
exactement son role — et c'est pour cela que le namespace est `palamod`.

26.2 est pointilleux sur deux choses, verifiees dans le jar vanilla :
  - un item demande DEUX fichiers, `models/item/x.json` (la geometrie) ET
    `items/x.json` (la definition). Sans le second l'icone est violette ;
  - les dossiers de donnees sont au SINGULIER : `loot_table/`, `recipe/`,
    `tags/item/`, `tags/block/`.
"""
import json
import os
import shutil
import struct
import sys

NS = "palamod"
RES = "src/main/resources"

# ---------------------------------------------------------------------------
# Items. nom -> (texture source dans le jar, nom FR, nom EN)
# ---------------------------------------------------------------------------

PARTICLES = {
    # Sorties du cobblebreaker, de la plus courante a la plus rare.
    # Neuf particules d'un meme minerai donnent un lingot.
    "iron_particle": ("particle/particle_iron.png", "Particule de fer", "Iron Particle"),
    "gold_particle": ("particle/particle_gold.png", "Particule d'or", "Gold Particle"),
    "diamond_particle": ("particle/particle_diamond.png", "Particule de diamant", "Diamond Particle"),
    "amethyst_particle": ("particle/particle_amethyst.png", "Particule d'amethyste", "Amethyst Particle"),
    "titane_particle": ("particle/particle_titane.png", "Particule de titane", "Titane Particle"),
    "paladium_particle": ("particle/particle_paladium.png", "Particule de paladium", "Paladium Particle"),
    # Septieme type, present dans le code et absent du wiki.
    "green_paladium_particle": ("particle/particle_green_paladium.png",
                                "Particule de paladium vert", "Green Paladium Particle"),
}

MATERIALS = {
    "amethyst_ingot": ("materials/amethyst_ingot.png", "Lingot d'amethyste", "Amethyst Ingot"),
    "titane_ingot": ("materials/titane_ingot.png", "Lingot de titane", "Titane Ingot"),
    "paladium_ingot": ("materials/paladium_ingot.png", "Lingot de paladium", "Paladium Ingot"),
    "green_paladium_ingot": ("materials/paladium_green_ingot.png",
                             "Lingot de paladium vert", "Green Paladium Ingot"),
    "endium_ingot": ("materials/endium_ingot.png", "Lingot d'endium", "Endium Ingot"),
    "endium_nugget": ("materials/endium_nugget.png", "Pepite d'endium", "Endium Nugget"),
    "endium_fragment": ("materials/endium_fragment.png", "Fragment d'endium", "Endium Fragment"),
    "findium": ("materials/findium.png", "Findium", "Findium"),
    "trixium": ("materials/trixium.png", "Trixium", "Trixium"),
}

COMPRESSED = {
    "compressed_amethyst": ("machine/compressed_amethyst.png",
                            "Amethyste compressee", "Compressed Amethyst"),
    "compressed_titane": ("machine/compressed_titane.png",
                          "Titane compresse", "Compressed Titane"),
    "compressed_paladium": ("machine/compressed_paladium.png",
                            "Paladium compresse", "Compressed Paladium"),
    "compressed_endium": ("machine/compressed_endium.png",
                          "Endium compresse", "Compressed Endium"),
}

# Carburants du crusher. Un charbon par type de fruit, releve dans
# TileCrusher$EnumCrusherRecipes. Leurs recettes de fabrication arrivent au
# chapitre des machines : les inventer ici serait un chiffre de plus a defaire.
MIXED_COALS = {
    "gold_mixed_coal": ("machine/goldMixedCoal.png", "Charbon mixe d'or", "Goldmixed Coal"),
    "amethyst_mixed_coal": ("machine/amethystMixedCoal.png",
                            "Charbon mixe d'amethyste", "Amethystmixed Coal"),
    "titane_mixed_coal": ("machine/titaneMixedCoal.png",
                          "Charbon mixe de titane", "Titanemixed Coal"),
    "pala_mixed_coal": ("machine/palaMixedCoal.png",
                        "Charbon mixe de paladium", "Palamixed Coal"),
}

ITEMS = {}
ITEMS.update(PARTICLES)
ITEMS.update(MATERIALS)
ITEMS.update(COMPRESSED)
ITEMS.update(MIXED_COALS)

# ---------------------------------------------------------------------------
# Blocs. Durete, resistance et niveau de pioche releves au bytecode dans
# fr.paladium.palamod.modules.world.PWorld — pas devines.
# ---------------------------------------------------------------------------

TOOL_TAG = {1: "needs_stone_tool", 2: "needs_iron_tool", 3: "needs_diamond_tool"}

ORES = {
    # nom: (texture, durete, resistance, niveau de pioche, item lache, FR, EN)
    "amethyst_ore": ("ores/amethyst_ore.png", 3.0, 5.0, 1,
                     "palamod:amethyst_ingot", "Minerai d'amethyste", "Amethyst Ore"),
    "titane_ore": ("ores/titane_ore.png", 3.0, 5.0, 2,
                   "palamod:titane_ingot", "Minerai de titane", "Titane Ore"),
    "paladium_ore": ("ores/paladium_ore.png", 5.0, 5.0, 3,
                     "palamod:paladium_ingot", "Minerai de paladium", "Paladium Ore"),
    "paladium_green_ore": ("ores/paladium_green_ore.png", 5.0, 5.0, 3,
                           "palamod:green_paladium_ingot",
                           "Minerai de paladium vert", "Green Paladium Ore"),
    "findium_ore": ("ores/findium_ore.png", 5.0, 5.0, 3,
                    "palamod:findium", "Minerai de findium", "Findium Ore"),
    "trixium_ore": ("ores/trixium_ore.png", 3.0, 5.0, 3,
                    "palamod:trixium", "Minerai de trixium", "Trixium Ore"),
}

STORAGE = {
    # nom: (texture, lingot correspondant, FR, EN)
    "amethyst_block": ("amethyst/amethyst_block.png", "palamod:amethyst_ingot",
                       "Bloc d'amethyste", "Block of Amethyst"),
    "titane_block": ("titane/titane_block.png", "palamod:titane_ingot",
                     "Bloc de titane", "Block of Titane"),
    "paladium_block": ("paladium/paladium_block.png", "palamod:paladium_ingot",
                       "Bloc de paladium", "Block of Paladium"),
    "green_paladium_block": ("gpaladium/gpaladium_block.png", "palamod:green_paladium_ingot",
                             "Bloc de paladium vert", "Block of Green Paladium"),
    # Attention au chemin : PalaMod contient TROIS endium_block.png, et deux
    # sont en 15x15 — un fichier isole a la racine de blocks/ et un asset de
    # GUI de LuckyBlock. Le bon est dans le sous-dossier endium/, comme pour
    # tous les autres materiaux. La garde de taille ci-dessous existe a cause
    # de cette confusion, qui est passee en revue une premiere fois.
    "endium_block": ("endium/endium_block.png", "palamod:endium_ingot",
                     "Bloc d'endium", "Block of Endium"),
    "findium_block": ("findium/findium_block.png", "palamod:findium",
                      "Bloc de findium", "Block of Findium"),
    "trixium_block": ("trixium/trixium_block.png", "palamod:trixium",
                      "Bloc de trixium", "Block of Trixium"),
}

# ---------------------------------------------------------------------------
# Cultures.
#
# Tout vient de BaseBlockCrops.getDrops et de PRegister_Blocks, lus au
# bytecode. Le mecanisme du lot rare n'est NULLE PART sur le wiki : a maturite,
# un tirage sur 10 000 decide entre un lingot et le fruit.
#
# nom : (
#   graine, fruit, nombre de dessins, stades d'origine,
#   lot rare, chance du lot sur 10000, chance de rendre la graine,
#   FR du bloc, FR de la graine, FR du fruit,
#   EN du bloc, EN de la graine, EN du fruit,
# )
# ---------------------------------------------------------------------------

CROP_AGE_MAX = 7  # AGE_7 : 8 stades, ce que CropBlock impose en 26.2

CROPS = {
    "eggplant_crop": (
        "eggplant_seeds", "eggplant", 4, 8,
        "palamod:amethyst_ingot", 800, 1.0,
        "Aubergines", "Graines d'aubergine", "Aubergine",
        "Eggplants", "Eggplant Seeds", "Eggplant"),
    "chervil_crop": (
        "chervil_seeds", "chervil", 4, 8,
        "palamod:titane_ingot", 500, 1.0,
        "Cerfeuil", "Graines de cerfeuil", "Cerfeuil",
        "Chervil", "Chervil Seeds", "Chervil"),
    "kiwano_crop": (
        "kiwano_seeds", "kiwano", 5, 10,
        "palamod:paladium_ingot", 200, 1.0,
        "Kiwanos", "Graines de kiwano", "Kiwano",
        "Kiwanos", "Kiwano Seeds", "Kiwano"),
    # L'orangeblue est la seule dont la graine ne revient pas toujours : un
    # second tirage, sur 30, ne la rend que si le resultat vaut 0 ou 1, soit
    # 6,67 %. C'est ce qui l'empeche de s'auto-entretenir, et c'est voulu —
    # c'est la seule source d'endium du jeu.
    "orangeblue_crop": (
        "orangeblue_seeds", "orangeblue", 5, 10,
        "palamod:endium_nugget", 25, 2.0 / 30.0,
        "Orangeblues", "Graines d'orangeblue", "Orangeblue",
        "Orangeblues", "Orangeblue Seeds", "Orangeblue"),
}

# Textures sources des graines et des fruits. Elles ne suivent pas le nom de
# l'item : la graine d'aubergine est items/seeds/eggplant.png et son fruit
# items/fruits/eggplants.png, au pluriel.
CROP_TEXTURES = {
    "eggplant_seeds": "seeds/eggplant.png",
    "chervil_seeds": "seeds/chervil.png",
    "kiwano_seeds": "seeds/kiwano.png",
    "orangeblue_seeds": "seeds/orangeblue.png",
    "eggplant": "fruits/eggplants.png",
    "chervil": "fruits/chervil.png",
    "kiwano": "fruits/kiwano.png",
    "orangeblue": "fruits/orangeblue.png",
}


def stage_for_age(age, frames):
    """Quel dessin montrer a cet age.

    <p>Le PalaMod faisait avancer un dessin tous les deux stades : 8 stades pour
    4 dessins, 10 pour 5. Avec 8 stades imposes, les cultures a 4 dessins
    gardent ce rythme exactement ; celles a 5 dessins le gardent jusqu'au
    quatrieme et montrent le dernier sur le seul stade final. La maturite reste
    donc lisible d'un coup d'oeil, ce qui est tout ce qu'on demande au dessin.
    """
    if frames >= 5:
        return min(frames - 1, age // 2 if age < 6 else age - 3)
    return min(frames - 1, age // 2)


# Generation du monde. nom -> (taille de filon, nombre par chunk, y min, y max)
#
# La taille de filon est RELEVEE dans BaseWorldGenerator : amethyste 8,
# titane 6, paladium 4, paladium vert 3, findium 3, trixium 3. Le nombre et la
# profondeur sont CALIBRES sur le monde de 26.2 — voir le commentaire a l'usage.
ORE_GEN = {
    "amethyst_ore": (8, 8, -24, 64),
    "titane_ore": (6, 6, -32, 40),
    "paladium_ore": (4, 4, -48, 16),
    "paladium_green_ore": (3, 2, -56, -8),
    "findium_ore": (3, 3, -64, 0),
    "trixium_ore": (3, 1, -64, -32),
}

# Neuf particules -> un lingot. Le fer, l'or et le diamant retombent sur les
# items vanilla : le cobblebreaker ne doit pas creer un fer parallele.
PARTICLE_TO_INGOT = {
    "iron_particle": "minecraft:iron_ingot",
    "gold_particle": "minecraft:gold_ingot",
    "diamond_particle": "minecraft:diamond",
    "amethyst_particle": "palamod:amethyst_ingot",
    "titane_particle": "palamod:titane_ingot",
    "paladium_particle": "palamod:paladium_ingot",
    "green_paladium_particle": "palamod:green_paladium_ingot",
}

# Neuf blocs -> un compresse. C'est la progression de Paladium : lingot, bloc,
# compresse. L'endium n'a pas de minerai classique, son bloc vient des pepites.
COMPRESSED_FROM = {
    "compressed_amethyst": "palamod:amethyst_block",
    "compressed_titane": "palamod:titane_block",
    "compressed_paladium": "palamod:paladium_block",
    "compressed_endium": "palamod:endium_block",
}


def write(path, payload):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def png_size(path):
    """Largeur et hauteur d'un PNG, lues dans son en-tete IHDR."""
    with open(path, "rb") as fh:
        head = fh.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


# Les textures hors 16x16 sont refusees.
#
# PalaMod traine des homonymes : il y a trois `endium_block.png`, dont deux en
# 15x15 (un fichier isole a la racine de blocks/ et un asset de GUI). Le script
# a pris le mauvais la premiere fois, et rien ne l'a signale — une texture
# 15x15 part dans l'atlas sans erreur de chargement et se voit seulement en
# jeu, sur un bloc qu'il faut d'abord pouvoir obtenir.
#
# Un chemin explicite ne suffit donc pas : il faut verifier ce qu'on a copie.
EXPECTED_SIZE = (16, 16)


def copy_texture(source_root, relative, destination, wrong_size=None):
    src = os.path.join(source_root, "assets", NS, "textures", relative)
    if not os.path.exists(src):
        return False
    size = png_size(src)
    if size is not None and size != EXPECTED_SIZE and wrong_size is not None:
        wrong_size.append("%s : %dx%d au lieu de %dx%d"
                          % (relative, size[0], size[1], *EXPECTED_SIZE))
        return False
    dst = os.path.join(RES, "assets", NS, "textures", destination)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
    return True


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: palamod_assets.py <dossier PalaMod extrait>")
    root = sys.argv[1]

    missing = []
    wrong_size = []
    fr = {}
    en = {}

    # ---- items ----
    for name, (texture, label_fr, label_en) in ITEMS.items():
        if not copy_texture(root, "items/" + texture, "item/%s.png" % name, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        # La definition d'item. 26.2 l'exige en plus du modele : sans elle
        # l'icone est violette, et c'est la premiere cause d'icones manquantes.
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    # ---- blocs ----
    blocks = {}
    for name, (texture, _h, _r, _lvl, drop, label_fr, label_en) in ORES.items():
        blocks[name] = (texture, drop, label_fr, label_en)
    for name, (texture, ingot, label_fr, label_en) in STORAGE.items():
        blocks[name] = (texture, "%s:%s" % (NS, name), label_fr, label_en)

    for name, (texture, drop, label_fr, label_en) in blocks.items():
        if not copy_texture(root, "blocks/" + texture, "block/%s.png" % name, wrong_size):
            missing.append("blocks/" + texture)
        write("assets/%s/blockstates/%s.json" % (NS, name), {
            "variants": {"": {"model": "%s:block/%s" % (NS, name)}},
        })
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "%s:block/%s" % (NS, name)},
        })
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en

    # ---- tables de butin ----
    # Un minerai lache son lingot, et le bloc lui-meme a la pioche soie. C'est
    # le comportement de Paladium, et il satisfait la regle de DESIGN.md
    # « minerai != metal » : deux marchandises distinctes, deux tiers.
    for name, (_t, _h, _r, _lvl, drop, _fr, _en) in ORES.items():
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [
                        {
                            "type": "minecraft:item",
                            "name": "%s:%s" % (NS, name),
                            "conditions": [{
                                "condition": "minecraft:match_tool",
                                "predicate": {"predicates": {
                                    "minecraft:enchantments": [
                                        {"enchantments": "minecraft:silk_touch",
                                         "levels": {"min": 1}}
                                    ]
                                }},
                            }],
                        },
                        {
                            "type": "minecraft:item",
                            "name": drop,
                            "functions": [
                                {"function": "minecraft:apply_bonus",
                                 "enchantment": "minecraft:fortune",
                                 "formula": "minecraft:ore_drops"},
                                {"function": "minecraft:explosion_decay"},
                            ],
                        },
                    ],
                }],
            }],
        })

    for name in STORAGE:
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
            }],
        })

    # ---- recettes ----
    # Format plat des ingredients : "palamod:x", pas {"item": "..."}.
    def shapeless(path, ingredients, result, count=1, group=None):
        payload = {
            "type": "minecraft:crafting_shapeless",
            "ingredients": ingredients,
            "result": {"id": result, "count": count},
        }
        if group:
            payload["group"] = group
        write("data/%s/recipe/%s.json" % (NS, path), payload)

    def packed(path, ingredient, result, group=None):
        payload = {
            "type": "minecraft:crafting_shaped",
            "pattern": ["XXX", "XXX", "XXX"],
            "key": {"X": ingredient},
            "result": {"id": result, "count": 1},
        }
        if group:
            payload["group"] = group
        write("data/%s/recipe/%s.json" % (NS, path), payload)

    for particle, ingot in PARTICLE_TO_INGOT.items():
        packed("%s_from_particles" % ingot.split(":")[1],
               "%s:%s" % (NS, particle), ingot, group="palamod_particles")

    for name, (_t, ingot, _fr, _en) in STORAGE.items():
        packed("%s_from_ingots" % name, ingot, "%s:%s" % (NS, name))
        shapeless("%s_from_block" % ingot.split(":")[1],
                  ["%s:%s" % (NS, name)], ingot, count=9)

    for compressed, block in COMPRESSED_FROM.items():
        packed("%s_from_blocks" % compressed, block, "%s:%s" % (NS, compressed))
        shapeless("%s_from_compressed" % block.split(":")[1],
                  ["%s:%s" % (NS, compressed)], block, count=9)

    packed("endium_ingot_from_nuggets", "%s:endium_nugget" % NS, "%s:endium_ingot" % NS)
    shapeless("endium_nugget_from_ingot", ["%s:endium_ingot" % NS],
              "%s:endium_nugget" % NS, count=9)

    # ---- tags ----
    def tag(path, entries):
        write("data/%s" % path, {"replace": False, "values": entries})

    tag("%s/tags/item/particles.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(PARTICLES)])
    tag("%s/tags/item/ingots.json" % NS, [
        "%s:amethyst_ingot" % NS, "%s:titane_ingot" % NS, "%s:paladium_ingot" % NS,
        "%s:green_paladium_ingot" % NS, "%s:endium_ingot" % NS])
    tag("%s/tags/item/compressed.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(COMPRESSED)])
    tag("%s/tags/item/mixed_coals.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(MIXED_COALS)])
    tag("%s/tags/block/ores.json" % NS, ["%s:%s" % (NS, n) for n in sorted(ORES)])
    tag("%s/tags/block/storage_blocks.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(STORAGE)])

    # Tags vanilla : ils fusionnent, donc on ajoute sans remplacer. Sans
    # mineable/pickaxe un bloc se casse a la main a vitesse normale, et sans
    # needs_*_tool le niveau de pioche releve dans PWorld n'est pas applique.
    tag("minecraft/tags/block/mineable/pickaxe.json",
        ["%s:%s" % (NS, n) for n in sorted(list(ORES) + list(STORAGE))])
    by_level = {}
    for name, (_t, _h, _r, level, _d, _fr, _en) in ORES.items():
        by_level.setdefault(level, []).append("%s:%s" % (NS, name))
    # Un bloc de stockage suit le niveau de son minerai ; l'endium et le
    # trixium n'ont pas de minerai classique, ils prennent le diamant.
    by_level.setdefault(1, []).append("%s:amethyst_block" % NS)
    by_level.setdefault(2, []).append("%s:titane_block" % NS)
    by_level.setdefault(3, []).extend([
        "%s:paladium_block" % NS, "%s:green_paladium_block" % NS,
        "%s:endium_block" % NS, "%s:findium_block" % NS, "%s:trixium_block" % NS])
    for level, entries in by_level.items():
        tag("minecraft/tags/block/%s.json" % TOOL_TAG[level], sorted(entries))

    # ---- cultures ----
    for crop, spec in CROPS.items():
        (seed, fruit, frames, _original_stages, rare, rare_chance, seed_chance,
         fr_block, fr_seed, fr_fruit, en_block, en_seed, en_fruit) = spec
        base = crop.removesuffix("_crop")

        # Les dessins de stade. Un modele par dessin, pas un par stade.
        for frame in range(frames):
            texture = "%s_stage_%d" % (base, frame)
            if not copy_texture(root, "blocks/crops/%s.png" % texture,
                                "block/%s.png" % texture, wrong_size):
                missing.append("blocks/crops/%s.png" % texture)
            write("assets/%s/models/block/%s_stage_%d.json" % (NS, crop, frame), {
                "parent": "minecraft:block/crop",
                "textures": {"crop": "%s:block/%s" % (NS, texture)},
            })

        write("assets/%s/blockstates/%s.json" % (NS, crop), {
            "variants": {
                "age=%d" % age: {
                    "model": "%s:block/%s_stage_%d" % (NS, crop, stage_for_age(age, frames))
                }
                for age in range(CROP_AGE_MAX + 1)
            },
        })
        fr["block.%s.%s" % (NS, crop)] = fr_block
        en["block.%s.%s" % (NS, crop)] = en_block

        # Graine et fruit : des items plats ordinaires.
        for name, label_fr, label_en in ((seed, fr_seed, en_seed),
                                         (fruit, fr_fruit, en_fruit)):
            source = CROP_TEXTURES[name]
            if not copy_texture(root, "items/" + source, "item/%s.png" % name, wrong_size):
                missing.append("items/" + source)
            write("assets/%s/models/item/%s.json" % (NS, name), {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": "%s:item/%s" % (NS, name)},
            })
            write("assets/%s/items/%s.json" % (NS, name), {
                "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
            })
            fr["item.%s.%s" % (NS, name)] = label_fr
            en["item.%s.%s" % (NS, name)] = label_en

        # La table de butin, et c'est la que vit tout l'interet des cultures.
        #
        # Une culture immature ne lache RIEN, pas meme sa graine : la condition
        # d'age porte sur les deux reserves. C'est le comportement d'origine,
        # plus dur que le ble vanilla, et il est reproduit tel quel.
        mature = {
            "condition": "minecraft:block_state_property",
            "block": "%s:%s" % (NS, crop),
            "properties": {"age": str(CROP_AGE_MAX)},
        }
        seed_pool = {
            "rolls": 1,
            "conditions": [mature],
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, seed)}],
        }
        if seed_chance < 1.0:
            seed_pool["conditions"] = [mature, {
                "condition": "minecraft:random_chance",
                "chance": round(seed_chance, 6),
            }]

        write("data/%s/loot_table/blocks/%s.json" % (NS, crop), {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "conditions": [mature],
                    # alternatives : le lot rare s'il sort, le fruit sinon. Le
                    # lingot REMPLACE le fruit, il ne s'y ajoute pas.
                    "entries": [{
                        "type": "minecraft:alternatives",
                        "children": [
                            {"type": "minecraft:item", "name": rare,
                             "conditions": [{"condition": "minecraft:random_chance",
                                             "chance": rare_chance / 10000.0}]},
                            {"type": "minecraft:item", "name": "%s:%s" % (NS, fruit)},
                        ],
                    }],
                },
                seed_pool,
            ],
        })

    tag("%s/tags/item/seeds.json" % NS,
        sorted("%s:%s" % (NS, spec[0]) for spec in CROPS.values()))
    tag("%s/tags/item/fruits.json" % NS,
        sorted("%s:%s" % (NS, spec[1]) for spec in CROPS.values()))
    tag("%s/tags/block/crops.json" % NS, ["%s:%s" % (NS, c) for c in sorted(CROPS)])
    # Les cultures comptent comme des cultures pour le jeu : la houe les
    # recolte, les villageois les reconnaissent, et le tag DoHy des cultures
    # sert deja a l'XP d'agriculture.
    tag("minecraft/tags/block/crops.json", ["%s:%s" % (NS, c) for c in sorted(CROPS)])

    # ---- generation du monde ----
    #
    # La TAILLE DE FILON vient du code (BaseWorldGenerator, constructeurs de
    # WorldGenMinable). Le NOMBRE PAR CHUNK et la PROFONDEUR sont calibres, et
    # c'est volontaire : en 1.7.10 le monde allait de 0 a 255 et la deepslate
    # n'existait pas. Recopier les hauteurs d'origine donnerait des minerais
    # concentres au mauvais endroit d'un monde qui descend maintenant a -64.
    # On se cale donc sur l'analogue vanilla de chaque palier de pioche.
    for name, (size, count, min_y, max_y) in ORE_GEN.items():
        # « ore_amethyst » plutot que « ore_amethyst_ore » : le suffixe du
        # bloc ferait doublon dans le nom de la feature.
        feature = "ore_" + name.removesuffix("_ore")
        state = {"Name": "%s:%s" % (NS, name)}
        write("data/%s/worldgen/configured_feature/%s.json" % (NS, feature), {
            "type": "minecraft:ore",
            "config": {
                "size": size,
                "discard_chance_on_air_exposure": 0.0,
                # Deux cibles, meme bloc : sans la seconde le minerai
                # s'arreterait net a la frontiere de la deepslate, ou se
                # trouve desormais la moitie de la zone de minage.
                "targets": [
                    {"target": {"predicate_type": "minecraft:tag_match",
                                "tag": "minecraft:stone_ore_replaceables"},
                     "state": state},
                    {"target": {"predicate_type": "minecraft:tag_match",
                                "tag": "minecraft:deepslate_ore_replaceables"},
                     "state": state},
                ],
            },
        })
        write("data/%s/worldgen/placed_feature/%s.json" % (NS, feature), {
            "feature": "%s:%s" % (NS, feature),
            "placement": [
                {"type": "minecraft:count", "count": count},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range",
                 "height": {"type": "minecraft:uniform",
                            "min_inclusive": {"absolute": min_y},
                            "max_inclusive": {"absolute": max_y}}},
                {"type": "minecraft:biome"},
            ],
        })

    # Un seul modificateur de biome pour les six minerais : NeoForge les ajoute
    # a l'etape underground_ores de tout biome de l'overworld.
    write("data/%s/neoforge/biome_modifier/ores.json" % NS, {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": ["%s:ore_%s" % (NS, name.removesuffix("_ore"))
                     for name in sorted(ORE_GEN)],
        "step": "underground_ores",
    })

    # ---- langues ----
    fr["itemGroup.%s.materials" % NS] = "PalaMod — minerais et materiaux"
    en["itemGroup.%s.materials" % NS] = "PalaMod — Ores and Materials"
    for code, table in (("fr_fr", fr), ("en_us", en)):
        write("assets/%s/lang/%s.json" % (NS, code),
              {key: table[key] for key in sorted(table)})

    # ---- rapport ----
    print("items   : %d" % len(ITEMS))
    print("blocs   : %d (%d minerais, %d blocs de stockage)"
          % (len(blocks), len(ORES), len(STORAGE)))
    print("langues : %d cles" % len(fr))
    if missing:
        print("\nTEXTURES ABSENTES (%d) — a corriger, elles donneront du violet :" % len(missing))
        for path in missing:
            print("  " + path)
    if wrong_size:
        print("\nTEXTURES A LA MAUVAISE TAILLE (%d) — probablement un homonyme,"
              " verifier le sous-dossier :" % len(wrong_size))
        for line in wrong_size:
            print("  " + line)
    if missing or wrong_size:
        return 1
    print("\ntoutes les textures ont ete trouvees, toutes en %dx%d" % EXPECTED_SIZE)
    return 0


if __name__ == "__main__":
    sys.exit(main())
