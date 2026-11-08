#!/usr/bin/env python3
"""Genere les assets et les donnees du namespace palamod.

Pourquoi un generateur plutot que 150 fichiers ecrits a la main : chaque item
demande trois fichiers (texture, modele, definition d'item) et chaque bloc six.
Pour 24 items et 13 blocs cela fait plus de 150 fichiers dont la seule variable
est un nom. Les ecrire a la main garantit une faute de frappe quelque part, et
une faute de frappe dans un chemin de modele ne fait pas echouer la compilation
— elle donne une texture violette en jeu, trois semaines plus tard.

    mkdir pack && cd pack && unzip ../updated_faithfull_paladium_officiel.zip
    mkdir jar  && cd jar  && unzip ../PalaMod-10.3.0.pala
    python3 tools/palamod_assets.py pack jar

Les textures viennent du **pack officiel** en priorite, et du jar de PalaMod
seulement pour celles que le pack n'a pas. Le pack est la version entretenue :
il corrige au passage des defauts du jar — c'est lui qui a le bon
`endium_block` en 16x16, la ou le jar traine trois homonymes dont deux en 15x15.

Le repli n'est jamais silencieux : la provenance de chaque texture est comptee
et affichee, et les fichiers pris dans le jar sont listes nommement.

Le namespace reste `palamod` : un joueur qui installe le pack officiel par
dessus retrouve donc exactement les memes fichiers, et ses mises a jour
s'appliquent sans rien regenerer.

26.2 est pointilleux sur deux choses, verifiees dans le jar vanilla :
  - un item demande DEUX fichiers, `models/item/x.json` (la geometrie) ET
    `items/x.json` (la definition). Sans le second l'icone est violette ;
  - les dossiers de donnees sont au SINGULIER : `loot_table/`, `recipe/`,
    `tags/item/`, `tags/block/`.
"""
import json
import re
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

# Intermediaires de fabrication, sans aucun comportement. Releves parce que dix
# des quinze recettes du chapitre les demandent : les cinq seedplanters, le
# crusher et le charbon mixe de paladium.
CRAFTING_PARTS = {
    "amethyst_stick": ("stick/amethyst.png", "Baton en amethyste", "Amethyst Stick"),
    "titane_stick": ("stick/titane.png", "Baton en titane", "Titane Stick"),
    "paladium_stick": ("stick/paladium.png", "Baton en paladium", "Paladium Stick"),
    "paladium_core": ("paladiumCore.png", "Coeur en paladium", "Paladium Core"),
    # La chaine du cobblebreaker. Les modificateurs sont ceux du grinder
    # (PSRegister_Items), l'orbe un BaseItem nu, la voidstone une poubelle.
    "speed_orb": ("machine/orb_speed.png", "Orbe de vitesse", "Speed Orb"),
    "speed_modifier": ("modifier/speed.png", "Modificateur de vitesse", "Speed Modifier"),
    "fortune_modifier": ("modifier/fortune.png", "Modificateur de fortune", "Fortune Modifier"),
    "voidstone": ("voidstone.png", "Voidstone", "Voidstone"),
}

ITEMS = {}
ITEMS.update(PARTICLES)
ITEMS.update(MATERIALS)
ITEMS.update(COMPRESSED)
ITEMS.update(MIXED_COALS)
ITEMS.update(CRAFTING_PARTS)

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


# Le pack officiel ne range pas tout comme le jar. Une entree ici dit ou
# chercher dans le pack quand le chemin du jar n'y mene pas.
PACK_PATHS = {
    # Dans le jar, blocks/endium_block.png est un fichier isole en 15x15 et le
    # bon est dans blocks/endium/. Dans le pack, la racine porte le bon.
    "blocks/endium/endium_block.png": "blocks/endium_block.png",
}

# Compteur de provenance, rempli par copy_texture et affiche a la fin.
provenance = {"pack": 0, "jar": []}


def copy_texture(roots, relative, destination, wrong_size=None, expected=None):
    """Copie une texture, pack d'abord, jar en repli.

    roots est (pack, jar). Le pack est la version entretenue ; le jar ne sert
    que pour ce qu'il est seul a avoir, et chaque repli est enregistre pour
    etre affiche — un repli silencieux ferait croire que le pack couvre tout.
    """
    pack_root, jar_root = roots
    candidates = []
    if pack_root:
        candidates.append(("pack", os.path.join(
            pack_root, "assets", NS, "textures", PACK_PATHS.get(relative, relative))))
    if jar_root:
        candidates.append(("jar", os.path.join(jar_root, "assets", NS, "textures", relative)))

    for origin, src in candidates:
        if not os.path.exists(src):
            continue
        size = png_size(src)
        # Les icones font 16x16 ; les couches d'armure portees font 64x32, la
        # disposition UV du modele de joueur — la meme en 1.7.10 et en 26.2.
        want = expected or EXPECTED_SIZE
        if size is not None and size != want:
            if wrong_size is not None:
                wrong_size.append("%s (%s) : %dx%d au lieu de %dx%d"
                                  % (relative, origin, size[0], size[1], *want))
            continue
        dst = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        if origin == "pack":
            provenance["pack"] += 1
        else:
            provenance["jar"].append(relative)
        return True
    return False


def main():
    if len(sys.argv) < 2:
        raise SystemExit(
            "usage: palamod_assets.py <pack officiel extrait> [<jar PalaMod extrait>]")
    root = (sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)

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

    # Les tags s'ACCUMULENT au lieu de s'ecraser.
    #
    # Plusieurs familles ecrivent dans le meme fichier : minerais, blocs de
    # stockage et machines vont tous dans mineable/pickaxe. Avec une ecriture
    # directe, le dernier appel effacait les precedents — et un tag efface ne
    # produit aucune erreur, juste un bloc qui se casse a la main a vitesse
    # normale. On collecte, et on ecrit une fois a la fin.
    collected_tags = {}

    def tag(path, entries):
        collected_tags.setdefault(path, set()).update(entries)

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

    def shaped(path, pattern, keys, result, count=1):
        """Une recette a grille, telle qu'elle est relevee au bytecode.

        `pattern` et `keys` sont recopies de la sortie de
        tools/extract_crafts.py, sans reinterpretation. Les lignes de grille
        d'origine peuvent faire une ou deux cases de large : le format moderne
        l'accepte, il n'y a rien a completer.
        """
        write("data/%s/recipe/%s.json" % (NS, path), {
            "type": "minecraft:crafting_shaped",
            "pattern": pattern,
            "key": keys,
            "result": {"id": result, "count": count},
        })

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

    # ---- terre fertilisee ----
    #
    # Deux blocs, une seule texture. La version labouree reutilise la texture de
    # la version brute sur ses cotes et son dessous, et emprunte le dessus de la
    # terre labouree irriguee de vanilla : c'est exactement ce que le bloc fait
    # — une terre toujours humide — et aucun dessus n'existe dans les sources.
    if not copy_texture(root, "blocks/connectedDirt.png",
                        "block/fertilized_dirt.png", wrong_size):
        missing.append("blocks/connectedDirt.png")

    write("assets/%s/models/block/fertilized_dirt.json" % NS, {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "%s:block/fertilized_dirt" % NS},
    })
    write("assets/%s/models/block/fertilized_farmland.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "minecraft:block/farmland_moist",
            "side": "%s:block/fertilized_dirt" % NS,
            "bottom": "%s:block/fertilized_dirt" % NS,
        },
    })

    for soil, label_fr, label_en in (
            ("fertilized_dirt", "Terre fertilisee", "Fertilized Dirt"),
            ("fertilized_farmland", "Terre fertilisee labouree", "Tilled Fertilized Dirt")):
        write("assets/%s/blockstates/%s.json" % (NS, soil),
              {"variants": {"": {"model": "%s:block/%s" % (NS, soil)}}})
        write("assets/%s/models/item/%s.json" % (NS, soil),
              {"parent": "%s:block/%s" % (NS, soil)})
        write("assets/%s/items/%s.json" % (NS, soil), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, soil)},
        })
        fr["block.%s.%s" % (NS, soil)] = label_fr
        en["block.%s.%s" % (NS, soil)] = label_en
        # Les deux rendent de la terre fertilisee BRUTE : labourer puis casser
        # ne doit rien couter, sinon un coup de houe mal place devient une
        # perte seche.
        write("data/%s/loot_table/blocks/%s.json" % (NS, soil), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item",
                             "name": "%s:fertilized_dirt" % NS}],
            }],
        })

    # RECETTE INVENTEE — la seule du chapitre, et elle est signalee comme telle.
    #
    # Le PalaMod n'en donne aucune : sur le serveur, la terre fertilisee
    # s'achetait. Plutot que de la laisser inaccessible hors creatif, on pose
    # une recette thematique (des os, donc de l'engrais) et calibree pour une
    # ferme : un bloc d'os pour huit blocs de terre, soit un peu plus d'une
    # poudre d'os par bloc. Elle n'accelere que la croissance, jamais le
    # rendement, donc le risque d'equilibrage est faible.
    #
    # C'est un fichier de datapack : la changer ne demande pas de recompiler.
    write("data/%s/recipe/fertilized_dirt.json" % NS, {
        "type": "minecraft:crafting_shaped",
        "pattern": ["DDD", "DBD", "DDD"],
        "key": {"D": "minecraft:dirt", "B": "minecraft:bone_block"},
        "result": {"id": "%s:fertilized_dirt" % NS, "count": 8},
    })

    # La version brute entre dans le tag dirt : pousses d'arbre, fleurs et
    # herbe y poussent alors sans une ligne de code. La version labouree n'y
    # entre pas — ce serait lui faire porter aussi les plantes de surface.
    tag("minecraft/tags/block/dirt.json", ["%s:fertilized_dirt" % NS])
    tag("minecraft/tags/block/mineable/shovel.json",
        ["%s:fertilized_dirt" % NS, "%s:fertilized_farmland" % NS])

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

    # ---- machines ----
    #
    # Le cobblebreaker a QUATRE apparences, une par palier : c'est ce qui dit au
    # joueur ou en est sa machine sans qu'il ouvre quoi que ce soit, puisque le
    # mod n'a pas d'interface dessinee. Le palier est une propriete d'etat, donc
    # un modele par palier et une variante de blockstate par palier.
    for tier in ("default", "amethyst", "titane", "paladium"):
        for face in ("front", "side", "top"):
            source = "blocks/machines/cobblebreaker/cobblebreaker_%s_%s.png" % (tier, face)
            target = "block/cobblebreaker_%s_%s.png" % (tier, face)
            if not copy_texture(root, source, target, wrong_size):
                missing.append(source)
        write("assets/%s/models/block/cobblebreaker_%s.json" % (NS, tier), {
            # cube_bottom_top donne une face distincte dessus et dessous ; le
            # dessous reprend le dessus, le PalaMod n'ayant pas de texture
            # propre pour lui.
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": "%s:block/cobblebreaker_%s_top" % (NS, tier),
                "bottom": "%s:block/cobblebreaker_%s_top" % (NS, tier),
                "side": "%s:block/cobblebreaker_%s_side" % (NS, tier),
            },
        })

    write("assets/%s/blockstates/cobblebreaker.json" % NS, {
        "variants": {
            "tier=%s" % tier: {"model": "%s:block/cobblebreaker_%s" % (NS, tier)}
            for tier in ("default", "amethyst", "titane", "paladium")
        },
    })
    # L'item du bloc montre le palier de base : c'est celui qu'on pose.
    write("assets/%s/models/item/cobblebreaker.json" % NS,
          {"parent": "%s:block/cobblebreaker_default" % NS})
    write("assets/%s/items/cobblebreaker.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/cobblebreaker_default" % NS},
    })

    # Le crusher : une seule apparence. Ses textures ne sont QUE dans le jar.
    for face in ("front", "side", "top"):
        source = "blocks/machines/crusher_%s.png" % face
        if not copy_texture(root, source, "block/crusher_%s.png" % face, wrong_size):
            missing.append(source)
    write("assets/%s/models/block/crusher.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "%s:block/crusher_top" % NS,
            "bottom": "%s:block/crusher_top" % NS,
            "side": "%s:block/crusher_side" % NS,
        },
    })
    write("assets/%s/blockstates/crusher.json" % NS,
          {"variants": {"": {"model": "%s:block/crusher" % NS}}})
    write("assets/%s/models/item/crusher.json" % NS, {"parent": "%s:block/crusher" % NS})
    write("assets/%s/items/crusher.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/crusher" % NS},
    })

    for machine, label_fr, label_en in (
            ("cobblebreaker", "Cobblebreaker", "Cobblebreaker"),
            ("crusher", "Crusher", "Crusher")):
        fr["block.%s.%s" % (NS, machine)] = label_fr
        en["block.%s.%s" % (NS, machine)] = label_en
        write("data/%s/loot_table/blocks/%s.json" % (NS, machine), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": "%s:%s" % (NS, machine)}]}],
        })

    # Les trois ameliorations du cobblebreaker.
    for tier, label_fr, label_en in (
            ("amethyst", "Amelioration amethyste (x1,5)", "Amethyst Upgrade (x1.5)"),
            ("titane", "Amelioration titane (x2,5)", "Titane Upgrade (x2.5)"),
            ("paladium", "Amelioration paladium (x4)", "Paladium Upgrade (x4)")):
        name = "cobblebreaker_%s_upgrade" % tier
        if not copy_texture(root, "items/%s.png" % name, "item/%s.png" % name, wrong_size):
            missing.append("items/%s.png" % name)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    tag("minecraft/tags/block/mineable/pickaxe.json",
        ["%s:cobblebreaker" % NS, "%s:crusher" % NS])
    tag("minecraft/tags/block/needs_stone_tool.json",
        ["%s:cobblebreaker" % NS, "%s:crusher" % NS])

    # ---- chapitre 5 : le totem de fertilite ----
    #
    # L'original le dessine avec un TESR (ModelTotem, RenderTotem) : un mat
    # sculpte, pas un cube. On s'en tient a un cube texture, parce qu'un
    # renderer de block entity est le genre de code client que ce mod evite, et
    # que la texture blocks/totem.png du pack suffit a le rendre
    # reconnaissable. Note comme un ecart dans docs/PALAMOD.md.
    if not copy_texture(root, "blocks/totem.png", "block/totem_of_fertility.png", wrong_size):
        missing.append("blocks/totem.png")
    write("assets/%s/models/block/totem_of_fertility.json" % NS, {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "%s:block/totem_of_fertility" % NS},
    })
    write("assets/%s/blockstates/totem_of_fertility.json" % NS,
          {"variants": {"": {"model": "%s:block/totem_of_fertility" % NS}}})
    write("assets/%s/models/item/totem_of_fertility.json" % NS,
          {"parent": "%s:block/totem_of_fertility" % NS})
    write("assets/%s/items/totem_of_fertility.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/totem_of_fertility" % NS},
    })
    write("data/%s/loot_table/blocks/totem_of_fertility.json" % NS, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": "%s:totem_of_fertility" % NS}]}],
    })
    fr["block.%s.totem_of_fertility" % NS] = "Totem de fertilite"
    en["block.%s.totem_of_fertility" % NS] = "Totem of Fertility"

    # A la hache, pas a la pioche : c'est du bois, et il n'exige aucun palier
    # d'outil. Les autres machines du chapitre 5 seront du metal.
    tag("minecraft/tags/block/mineable/axe.json", ["%s:totem_of_fertility" % NS])

    # ---- chapitre 5 : les cinq seedplanters ----
    #
    # Les identifiants viennent des verrous du chapitre 1 ; les textures
    # d'origine s'appellent seedplanter_lvl1 a lvl5. La correspondance est ici
    # et nulle part ailleurs — le Java ne connait que les identifiants.
    for name, level, side, label_fr, label_en in (
            ("amethyst_seedplanter", 1, 2, "Seedplanter en amethyste", "Amethyst Seedplanter"),
            ("titane_seedplanter", 2, 4, "Seedplanter en titane", "Titane Seedplanter"),
            ("paladium_seedplanter", 3, 6, "Seedplanter en paladium", "Paladium Seedplanter"),
            ("green_paladium_seedplanter", 5, 12,
             "Seedplanter en paladium vert", "Green Paladium Seedplanter"),
            ("endium_seedplanter", 4, 12, "Seedplanter en endium", "Endium Seedplanter")):
        source = "items/seedplanter/seedplanter_lvl%d.png" % level
        if not copy_texture(root, source, "item/%s.png" % name, wrong_size):
            missing.append(source)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        # Le cote du carre est dans le nom affiche : c'est la seule difference
        # entre les cinq, et un joueur qui compare deux planteurs dans son
        # inventaire n'a pas d'autre moyen de la connaitre.
        fr["item.%s.%s" % (NS, name)] = "%s (%dx%d)" % (label_fr, side, side)
        en["item.%s.%s" % (NS, name)] = "%s (%dx%d)" % (label_en, side, side)

    # ---- chapitre 7 : l'equipement ----
    #
    # Quarante-huit objets et le lingot d'endium mixe. Les icones viennent du
    # pack officiel ; les couches portees de textures/models/<set>_armor_1 et _2,
    # au format 64x32 du modele de joueur — la meme disposition UV en 1.7.10 et
    # en 26.2, donc rien a redessiner.
    #
    # Le paladium vert s'appelle paladium_green_* dans les textures et
    # green_paladium_* ici, comme le reste du mod. Les houes, qui viennent de
    # PalaJobs, s'appelaient deja green_paladium_radius_hoe.
    EQUIPMENT_METALS = (
        # (id, prefixe de texture, set d'armure 1.7.10 ou None, FR, EN, outils)
        ("amethyst", "amethyst", "amethyst", "en amethyste", "Amethyst",
         ("sword", "pickaxe", "axe", "shovel")),
        ("titane", "titane", "titane", "en titane", "Titane",
         ("sword", "pickaxe", "axe", "shovel")),
        ("paladium", "paladium", "paladium", "en paladium", "Paladium",
         ("sword", "pickaxe", "axe", "shovel")),
        ("green_paladium", "paladium_green", "paladium_green", "en paladium vert", "Green Paladium",
         ("sword", "pickaxe", "axe", "shovel")),
        ("mixed_endium", "mixed_endium", "mixed_endium", "en endium mixe", "Mixed Endium",
         ()),
        # Pas de pelle en endium dans PalaMod.
        ("endium", "endium", "endium", "en endium", "Endium",
         ("sword", "pickaxe", "axe")),
    )
    TOOL_FR = {"sword": "Epee", "pickaxe": "Pioche", "axe": "Hache", "shovel": "Pelle"}
    TOOL_EN = {"sword": "Sword", "pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel"}
    ARMOR_FR = {"helmet": "Casque", "chestplate": "Plastron", "leggings": "Jambieres", "boots": "Bottes"}
    ARMOR_EN = {"helmet": "Helmet", "chestplate": "Chestplate", "leggings": "Leggings", "boots": "Boots"}
    HOE_SIDE = {"amethyst": 2, "titane": 4, "paladium": 6, "green_paladium": 8, "endium": 12}

    def equipment_item(name, texture, handheld):
        if not copy_texture(root, "items/%s.png" % texture, "item/%s.png" % name, wrong_size):
            missing.append("items/%s.png" % texture)
        # Un outil se tient en diagonale dans la main ; une piece d'armure non.
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })

    for metal, prefix, armor_set, fr_suffix, en_prefix, tools in EQUIPMENT_METALS:
        for tool in tools:
            name = "%s_%s" % (metal, tool)
            equipment_item(name, "%s_%s" % (prefix, tool), True)
            fr["item.%s.%s" % (NS, name)] = "%s %s" % (TOOL_FR[tool], fr_suffix)
            en["item.%s.%s" % (NS, name)] = "%s %s" % (en_prefix, TOOL_EN[tool])
            tag("minecraft/tags/item/%s.json" % {"sword": "swords", "pickaxe": "pickaxes",
                                                 "axe": "axes", "shovel": "shovels"}[tool],
                ["%s:%s" % (NS, name)])

        if metal in HOE_SIDE:
            name = "%s_radius_hoe" % metal
            # Le nom de texture vient de PalaJobs : green_paladium_*, pas
            # paladium_green_*.
            equipment_item(name, name, True)
            side = HOE_SIDE[metal]
            fr["item.%s.%s" % (NS, name)] = "Houe %s (%dx%d)" % (fr_suffix, side, side)
            en["item.%s.%s" % (NS, name)] = "%s Radius Hoe (%dx%d)" % (en_prefix, side, side)
            tag("minecraft/tags/item/hoes.json", ["%s:%s" % (NS, name)])

        # L'armure : quatre icones, une definition d'equipement, deux couches.
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            name = "%s_%s" % (metal, piece)
            equipment_item(name, "%s_%s" % (prefix, piece), False)
            fr["item.%s.%s" % (NS, name)] = "%s %s" % (ARMOR_FR[piece], fr_suffix)
            en["item.%s.%s" % (NS, name)] = "%s %s" % (en_prefix, ARMOR_EN[piece])
            # Ces tags decident a eux seuls quels enchantements s'appliquent :
            # sans eux, une epee Paladium refuserait Tranchant et un casque
            # Protection, sans la moindre erreur. Et trimmable_armor ouvre les
            # garnitures.
            tag("minecraft/tags/item/%s.json" % {"helmet": "head_armor", "chestplate": "chest_armor",
                                                 "leggings": "leg_armor", "boots": "foot_armor"}[piece],
                ["%s:%s" % (NS, name)])
            tag("minecraft/tags/item/trimmable_armor.json", ["%s:%s" % (NS, name)])

        for layer, suffix in (("humanoid", 1), ("humanoid_leggings", 2)):
            source = "models/%s_armor_%d.png" % (armor_set, suffix)
            if not copy_texture(root, source, "entity/equipment/%s/%s.png" % (layer, metal),
                                wrong_size, expected=(64, 32)):
                missing.append(source)
        # La definition d'equipement : c'est elle que l'ArmorMaterial designe
        # par son identifiant. Absente, le joueur porte une armure invisible.
        write("assets/%s/equipment/%s.json" % (NS, metal), {
            "layers": {
                "humanoid": [{"texture": "%s:%s" % (NS, metal)}],
                "humanoid_leggings": [{"texture": "%s:%s" % (NS, metal)}],
            },
        })

    # Le lingot d'endium mixe.
    if not copy_texture(root, "items/mixed_endium_ingot.png", "item/mixed_endium_ingot.png", wrong_size):
        missing.append("items/mixed_endium_ingot.png")
    write("assets/%s/models/item/mixed_endium_ingot.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/mixed_endium_ingot" % NS},
    })
    write("assets/%s/items/mixed_endium_ingot.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/mixed_endium_ingot" % NS},
    })
    fr["item.%s.mixed_endium_ingot" % NS] = "Lingot d'endium mixe"
    en["item.%s.mixed_endium_ingot" % NS] = "Mixed Endium Ingot"

    # Les tags de reparation. Une entree de plus que de metaux : l'armure en
    # paladium se repare au TITANE, seule exception sur les 43 pieces.
    for tag_name, ingot in (("amethyst", "amethyst_ingot"), ("titane", "titane_ingot"),
                            ("paladium", "paladium_ingot"), ("paladium_armor", "titane_ingot"),
                            ("green_paladium", "green_paladium_ingot"),
                            ("mixed_endium", "mixed_endium_ingot"), ("endium", "endium_ingot")):
        tag("%s/tags/item/repairs/%s.json" % (NS, tag_name), ["%s:%s" % (NS, ingot)])

    # ---- objets du Mineur (chapitre 8) ----
    #
    # Les excavatrices et les outils de construction se tiennent comme des
    # outils ; les objets de poche sont plats. Les excavatrices ne vont PAS dans
    # #minecraft:shovels : ce tag ouvrirait la table d'enchantement et
    # l'enclume, et ItemExcavator n'est pas enchantable.
    MINER_ITEMS = {
        "amethyst_excavator": ("excavator/amethyst.png", True,
                               "Excavatrice en amethyste", "Amethyst Excavator"),
        "titane_excavator": ("excavator/titane.png", True,
                             "Excavatrice en titane", "Titane Excavator"),
        "paladium_excavator": ("excavator/paladium.png", True,
                               "Excavatrice en paladium", "Paladium Excavator"),
        "amethyst_builder_wand": ("builder_wand_amethyst.png", True,
                                  "Outil de construction en amethyste", "Amethyst Builder Wand"),
        "titane_builder_wand": ("builder_wand_titane.png", True,
                                "Outil de construction en titane", "Titane Builder Wand"),
        "paladium_builder_wand": ("builder_wand_paladium.png", True,
                                  "Outil de construction en paladium", "Paladium Builder Wand"),
        "endium_builder_wand": ("builder_wand_endium.png", True,
                                "Outil de construction en endium", "Endium Builder Wand"),
        "moula_stone": ("moula_stone.png", False, "Dollars Stone", "Dollars Stone"),
        "hardness_potion": ("hardness_potion.png", False, "Potion de durete", "Hardness Potion"),
    }
    for name, (texture, handheld, label_fr, label_en) in MINER_ITEMS.items():
        if not copy_texture(root, "items/" + texture, "item/%s.png" % name, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    # L'aimant change d'icone selon son etat. L'etat est la presence du
    # composant enchantment_glint_override (MagnetItem) : la definition d'item
    # le teste avec has_component, sans code client.
    for state, texture in (("magnet", "magnet.png"), ("magnet_on", "magnet_on.png")):
        if not copy_texture(root, "items/" + texture, "item/%s.png" % state, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, state), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, state)},
        })
    write("assets/%s/items/magnet.json" % NS, {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:has_component",
            "component": "minecraft:enchantment_glint_override",
            "on_true": {"type": "minecraft:model", "model": "%s:item/magnet_on" % NS},
            "on_false": {"type": "minecraft:model", "model": "%s:item/magnet" % NS},
        },
    })
    fr["item.%s.magnet" % NS] = "Aimant"
    en["item.%s.magnet" % NS] = "Magnet"

    # L'icone de l'effet de durete : PalaMod n'en avait pas (CustomPotion sans
    # setIconIndex), et 26.2 afficherait du violet. On reprend la potion,
    # centree dans les 18x18 d'une icone d'effet.
    potion = os.path.join(RES, "assets", NS, "textures", "item", "hardness_potion.png")
    if os.path.exists(potion):
        from PIL import Image
        icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        icon.paste(Image.open(potion).convert("RGBA"), (1, 1))
        target = os.path.join(RES, "assets", NS, "textures", "mob_effect", "hardness.png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        icon.save(target)
    fr["effect.%s.hardness" % NS] = "Durete"
    en["effect.%s.hardness" % NS] = "Hardness"

    # La Voidstone minage : un objet plat.
    if not copy_texture(root, "items/voidstone_minage.png", "item/voidstone_minage.png", wrong_size):
        missing.append("items/voidstone_minage.png")
    write("assets/%s/models/item/voidstone_minage.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/voidstone_minage" % NS},
    })
    write("assets/%s/items/voidstone_minage.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/voidstone_minage" % NS},
    })
    fr["item.%s.voidstone_minage" % NS] = "Voidstone minage"
    en["item.%s.voidstone_minage" % NS] = "Mining Voidstone"

    def miner_block(name, textures, label_fr, label_en, parent="minecraft:block/cube_all",
                    orientable=False, drop=True, item=True, render_type=None, pickaxe=True):
        """Un bloc du Mineur : textures, modele, etat, objet, butin, nom, tags.

        textures : {cle du modele: chemin sous textures/ de PalaMod}. Chaque
        texture est copiee sous block/<name>_<cle>.png, ou block/<name>.png
        s'il n'y en a qu'une.
        """
        model_textures = {}
        for key, source in textures.items():
            target = name if len(textures) == 1 else "%s_%s" % (name, key)
            if not copy_texture(root, source, "block/%s.png" % target, wrong_size):
                missing.append(source)
            model_textures[key] = "%s:block/%s" % (NS, target)
        if "particle" not in model_textures:
            model_textures["particle"] = next(iter(model_textures.values()))
        model = {"parent": parent, "textures": model_textures}
        if render_type:
            model["render_type"] = render_type
        write("assets/%s/models/block/%s.json" % (NS, name), model)
        if orientable:
            # La face avant regarde le joueur : nord sans rotation, comme le
            # fourneau vanilla.
            write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
                "facing=%s" % facing: dict({"model": "%s:block/%s" % (NS, name)},
                                           **({"y": rot} if rot else {}))
                for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
            }})
        else:
            write("assets/%s/blockstates/%s.json" % (NS, name), {
                "variants": {"": {"model": "%s:block/%s" % (NS, name)}},
            })
        if item:
            write("assets/%s/models/item/%s.json" % (NS, name),
                  {"parent": "%s:block/%s" % (NS, name)})
            write("assets/%s/items/%s.json" % (NS, name), {
                "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
            })
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }] if drop else [],
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en
        if pickaxe:
            tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])

    # La void stone : aucun drop par table, elle rend la Voidstone minage et sa
    # reserve par playerWillDestroy. Pas d'objet propre.
    miner_block("void_stone", {"all": "blocks/voidstone.png"},
                "Void stone", "Void Stone", drop=False, item=False)
    # Le piston renforce wither : oriente, face avant + cotes, dessus et
    # dessous a la texture de cote (getIcon de BlockWitheredReinforcedPiston).
    miner_block("withered_reinforced_piston",
                {"front": "blocks/withered_reinforced_piston_front.png",
                 "side": "blocks/withered_reinforced_piston_side.png"},
                "Piston renforce wither", "Withered Reinforced Piston",
                parent="minecraft:block/orientable", orientable=True)
    # orientable demande aussi « top » : le cote, comme dans l'original.
    write("assets/%s/models/block/withered_reinforced_piston.json" % NS, {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": "%s:block/withered_reinforced_piston_front" % NS,
            "side": "%s:block/withered_reinforced_piston_side" % NS,
            "top": "%s:block/withered_reinforced_piston_side" % NS,
            "particle": "%s:block/withered_reinforced_piston_side" % NS,
        },
    })

    # Les cave blocks : du verre troue. Pas de render_type : depuis 26.1 le
    # jeu choisit lui-meme la passe cutout d'apres les pixels transparents de
    # la texture. Pioche en pierre (BlockCave : setHarvestLevel("pickaxe", 1)).
    miner_block("cave_block", {"all": "blocks/caveblock/cave_block.png"},
                "Cave block", "Cave Block")
    miner_block("endium_cave_block", {"all": "blocks/caveblock/endium_cave_block.png"},
                "Cave block en endium", "Endium Cave Block")
    tag("minecraft/tags/block/needs_stone_tool.json",
        ["%s:cave_block" % NS, "%s:endium_cave_block" % NS])

    # Le minerai de pepites d'endium : une pepite, toujours une seule — ni
    # fortune ni toucher de soie (BlockEndiumNuggetOre.getDrops). Le casser
    # demande Mineur 100, par le tag palamod:break_locked que lit JobGateEvents.
    miner_block("endium_nugget_ore", {"all": "blocks/miner/endium_nuggets_ore.png"},
                "Minerai de pepites d'endium", "Endium Nugget Ore")
    write("data/%s/loot_table/blocks/endium_nugget_ore.json" % NS, {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "%s:endium_nugget" % NS}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    tag("%s/tags/block/break_locked.json" % NS, ["%s:endium_nugget_ore" % NS])

    # La Palafurnace et la Palaforge : orientees comme le fourneau vanilla,
    # avec une face avant eteinte et une allumee (propriete lit).
    def lit_machine(name, front_off, front_on, side, top, label_fr, label_en, tool):
        sources = {"front_off": front_off, "front_on": front_on, "side": side, "top": top}
        for key, source in sources.items():
            if not copy_texture(root, source, "block/%s_%s.png" % (name, key), wrong_size):
                missing.append(source)
        for suffix, front in (("", "front_off"), ("_on", "front_on")):
            write("assets/%s/models/block/%s%s.json" % (NS, name, suffix), {
                "parent": "minecraft:block/orientable",
                "textures": {
                    "front": "%s:block/%s_%s" % (NS, name, front),
                    "side": "%s:block/%s_side" % (NS, name),
                    "top": "%s:block/%s_top" % (NS, name),
                    "particle": "%s:block/%s_side" % (NS, name),
                },
            })
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s,lit=%s" % (facing, lit): dict(
                {"model": "%s:block/%s%s" % (NS, name, "_on" if lit == "true" else "")},
                **({"y": rot} if rot else {}))
            for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
            for lit in ("false", "true")
        }})
        write("assets/%s/models/item/%s.json" % (NS, name), {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/%s.json" % tool, ["%s:%s" % (NS, name)])

    # Dessus = cote pour la Palafurnace (BlockPaladiumFurnace.getIcon) ;
    # la forge a un dessus propre.
    lit_machine("paladium_furnace", "blocks/machines/paladium_furnace_front_off.png",
                "blocks/machines/paladium_furnace_front_on.png",
                "blocks/machines/paladium_furnace_side.png",
                "blocks/machines/paladium_furnace_side.png",
                "Palafurnace", "Palafurnace", "needs_iron_tool")
    lit_machine("palaforge", "blocks/machines/forge_front_off.png",
                "blocks/machines/forge_front_on.png", "blocks/machines/forge_side.png",
                "blocks/machines/forge_top.png", "Palaforge", "Palaforge", "needs_stone_tool")

    # L'amelioration de fourneau : jusqu'a seize dans la case de gauche.
    if not copy_texture(root, "items/machine/furnace_upgrade.png", "item/furnace_upgrade.png",
                        wrong_size):
        missing.append("items/machine/furnace_upgrade.png")
    write("assets/%s/models/item/furnace_upgrade.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/furnace_upgrade" % NS},
    })
    write("assets/%s/items/furnace_upgrade.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/furnace_upgrade" % NS},
    })
    fr["item.%s.furnace_upgrade" % NS] = "Amelioration de fourneau"
    en["item.%s.furnace_upgrade" % NS] = "Furnace Upgrade"

    # L'entonnoir en paladium : le modele de l'entonnoir vanilla, retexture.
    # Le modele vanilla declare ses propres elements et lit #top, #side,
    # #inside et #particle : un enfant qui ne change que les textures suffit.
    for key in ("top", "inside", "outside"):
        source = "blocks/paladium_hopper_%s.png" % key
        if not copy_texture(root, source, "block/paladium_hopper_%s.png" % key, wrong_size):
            missing.append(source)
    hopper_textures = {
        "particle": "%s:block/paladium_hopper_outside" % NS,
        "top": "%s:block/paladium_hopper_top" % NS,
        "side": "%s:block/paladium_hopper_outside" % NS,
        "inside": "%s:block/paladium_hopper_inside" % NS,
    }
    write("assets/%s/models/block/paladium_hopper.json" % NS,
          {"parent": "minecraft:block/hopper", "textures": hopper_textures})
    write("assets/%s/models/block/paladium_hopper_side.json" % NS,
          {"parent": "minecraft:block/hopper_side", "textures": hopper_textures})
    write("assets/%s/blockstates/paladium_hopper.json" % NS, {"variants": {
        "facing=down": {"model": "%s:block/paladium_hopper" % NS},
        "facing=north": {"model": "%s:block/paladium_hopper_side" % NS},
        "facing=east": {"model": "%s:block/paladium_hopper_side" % NS, "y": 90},
        "facing=south": {"model": "%s:block/paladium_hopper_side" % NS, "y": 180},
        "facing=west": {"model": "%s:block/paladium_hopper_side" % NS, "y": 270},
    }})
    # L'objet est une icone plate, comme l'entonnoir vanilla.
    if not copy_texture(root, "items/paladium_hopper.png", "item/paladium_hopper.png", wrong_size):
        missing.append("items/paladium_hopper.png")
    write("assets/%s/models/item/paladium_hopper.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/paladium_hopper" % NS},
    })
    write("assets/%s/items/paladium_hopper.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/paladium_hopper" % NS},
    })
    write("data/%s/loot_table/blocks/paladium_hopper.json" % NS, {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "%s:paladium_hopper" % NS}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    fr["block.%s.paladium_hopper" % NS] = "Entonnoir en paladium"
    en["block.%s.paladium_hopper" % NS] = "Paladium Hopper"
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:paladium_hopper" % NS])

    # Les coffres en amethyste et en titane. L'original les dessine avec le
    # ModelChest de 1.7.10 (TileEntityModdedChestRenderer) ; le meme modele en
    # JSON, couvercle ferme : socle 14x10x14, couvercle 14x5x14, serrure
    # 2x4x1, sur une texture de coffre 64x64 (UV en pixels / 4). Face avant au
    # sud, comme le coffre vanilla sans rotation.
    def chest_faces(faces):
        return {face: {"uv": uv, "texture": "#chest"} for face, uv in faces.items()}

    chest_elements = [
        {"from": [1, 0, 1], "to": [15, 10, 15], "faces": chest_faces({
            "north": [10.5, 8.25, 14, 10.75], "south": [3.5, 8.25, 7, 10.75],
            "west": [0, 8.25, 3.5, 10.75], "east": [7, 8.25, 10.5, 10.75],
            "down": [7, 4.75, 10.5, 8.25], "up": [3.5, 4.75, 7, 8.25]})},
        {"from": [1, 9, 1], "to": [15, 14, 15], "faces": chest_faces({
            "north": [10.5, 3.5, 14, 4.75], "south": [3.5, 3.5, 7, 4.75],
            "west": [0, 3.5, 3.5, 4.75], "east": [7, 3.5, 10.5, 4.75],
            "up": [3.5, 0, 7, 3.5], "down": [7, 0, 10.5, 3.5]})},
        {"from": [7, 7, 15], "to": [9, 11, 16], "faces": chest_faces({
            "south": [0.25, 0.25, 0.75, 1.25], "west": [0, 0.25, 0.25, 1.25],
            "east": [0.75, 0.25, 1, 1.25], "up": [0.25, 0, 0.75, 0.25],
            "down": [0.75, 0, 1.25, 0.25], "north": [1, 0.25, 1.5, 1.25]})},
    ]
    for name, texture, metal, label_fr, label_en in (
            ("amethyst_chest", "models/AmethystChest.png", "amethyst",
             "Coffre en amethyste", "Amethyst Chest"),
            ("titane_chest", "models/TitaneChest.png", "titane",
             "Coffre en titane", "Titane Chest")):
        if not copy_texture(root, texture, "block/%s.png" % name, wrong_size, expected=(64, 64)):
            missing.append(texture)
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/block",
            "textures": {"chest": "%s:block/%s" % (NS, name),
                         "particle": "%s:block/%s_block" % (NS, metal)},
            "elements": chest_elements,
        })
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s" % facing: dict({"model": "%s:block/%s" % (NS, name)},
                                       **({"y": rot} if rot else {}))
            for facing, rot in (("south", 0), ("west", 90), ("north", 180), ("east", 270))
        }})
        write("assets/%s/models/item/%s.json" % (NS, name), {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/needs_iron_tool.json", ["%s:%s" % (NS, name)])

    # Ce qu'aucun outil de zone ne casse (AreaMining.IMMUNE).
    tag("%s/tags/block/area_mining_immune.json" % NS,
        ["minecraft:obsidian", "minecraft:crying_obsidian"])

    # ---- chapitre 9 : l'eau angelique ----
    #
    # Un fluide : ses textures animees (16 x 16n, avec leur .mcmeta) vont dans
    # block/fluid/, ou les lit le modele de fluide enregistre par DoHyClient. Le
    # bloc n'a qu'une particule, comme l'eau vanilla, et pas d'objet : c'est le
    # seau.
    for source, target in (("blocks/fluids/angelicwater_still", "block/fluid/angelic_water_still"),
                           ("blocks/fluids/angelicwater_flowing", "block/fluid/angelic_water_flow")):
        found = False
        for origin in (root[0], root[1]):
            if not origin:
                continue
            base = os.path.join(origin, "assets", NS, "textures", source)
            if os.path.exists(base + ".png"):
                destination = os.path.join(RES, "assets", NS, "textures", target)
                os.makedirs(os.path.dirname(destination), exist_ok=True)
                shutil.copyfile(base + ".png", destination + ".png")
                if os.path.exists(base + ".png.mcmeta"):
                    shutil.copyfile(base + ".png.mcmeta", destination + ".png.mcmeta")
                found = True
                break
        if not found:
            missing.append(source + ".png")
    write("assets/%s/models/block/angelic_water.json" % NS,
          {"textures": {"particle": "%s:block/fluid/angelic_water_still" % NS}})
    write("assets/%s/blockstates/angelic_water.json" % NS,
          {"variants": {"": {"model": "%s:block/angelic_water" % NS}}})
    write("data/%s/loot_table/blocks/angelic_water.json" % NS, {"type": "minecraft:block", "pools": []})
    if not copy_texture(root, "items/fluids/bucket_angelic.png", "item/angelic_water_bucket.png", wrong_size):
        missing.append("items/fluids/bucket_angelic.png")
    write("assets/%s/models/item/angelic_water_bucket.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/angelic_water_bucket" % NS},
    })
    write("assets/%s/items/angelic_water_bucket.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/angelic_water_bucket" % NS},
    })
    fr["block.%s.angelic_water" % NS] = "Eau angelique"
    en["block.%s.angelic_water" % NS] = "Angelic Water"
    fr["item.%s.angelic_water_bucket" % NS] = "Seau d'eau angelique"
    en["item.%s.angelic_water_bucket" % NS] = "Angelic Water Bucket"
    # PRegister_Items : ["Y", "X"], une potion de regeneration (meta 8193) sur
    # un seau d'eau. Le seau d'eau rend son seau vide, comme en 1.7.10. La potion
    # se reconnait a son contenu : un ingredient a composants de NeoForge.
    shaped("angelic_water_bucket", ["Y", "X"], {
        "Y": {"neoforge:ingredient_type": "neoforge:components",
              "items": "minecraft:potion",
              "components": {"minecraft:potion_contents": {"potion": "minecraft:regeneration"}},
              "strict": False},
        "X": "minecraft:water_bucket",
    }, "%s:angelic_water_bucket" % NS)

    # ---- les recettes de fabrication ----
    #
    # Une seule source : data/palamod_crafts.json, le relevé de
    # tools/extract_crafts.py sur les quatre registres de PalaMod. Rien n'est
    # recopie a la main — c'est precisement une recopie a la main, depuis un
    # relevé encore faux, qui avait produit les charbons mixes a grille
    # inegale. Corriger l'extracteur corrige maintenant toutes les recettes.
    #
    # PORTED dit quelles recettes porter, par nom PalaMod -> identifiant.
    # INGREDIENTS traduit chaque ingredient, meta compris. Un ingredient absent
    # de la table fait ECHOUER le generateur : deviner une correspondance
    # donnerait une recette fausse qui se fabrique quand meme.
    PORTED = {
        # Intermediaires
        "STICK_AMETHYST": "amethyst_stick", "STICK_TITANE": "titane_stick",
        "STICK_PALADIUM": "paladium_stick", "PALADIUM_CORE": "paladium_core",
        "MIXED_ENDIUM_INGOT": "mixed_endium_ingot",
        "ORB_SPEED": "speed_orb", "MODIFIER_SPEED": "speed_modifier",
        "MODIFIER_FORTUNE": "fortune_modifier", "VOIDSTONE": "voidstone",
        # Charbons mixes
        "GOLDMIXEDCOAL": "gold_mixed_coal", "AMETHYSTMIXEDCOAL": "amethyst_mixed_coal",
        "TITANEMIXEDCOAL": "titane_mixed_coal", "PALAMIXEDCOAL": "pala_mixed_coal",
        # Machines et ameliorations
        "COBBLEBREAKER": "cobblebreaker", "CRUSHER": "crusher", "Totem": "totem_of_fertility",
        "COBBLEBREAKER_AMETHYST_UPGRADE": "cobblebreaker_amethyst_upgrade",
        "COBBLEBREAKER_TITANE_UPGRADE": "cobblebreaker_titane_upgrade",
        "COBBLEBREAKER_PALADIUM_UPGRADE": "cobblebreaker_paladium_upgrade",
        # Seedplanters : lvl4 est l'endium, lvl5 le paladium vert
        "SEEDPLANTER_LVL1": "amethyst_seedplanter", "SEEDPLANTER_LVL2": "titane_seedplanter",
        "SEEDPLANTER_LVL3": "paladium_seedplanter", "SEEDPLANTER_LVL4": "endium_seedplanter",
        "SEEDPLANTER_LVL5": "green_paladium_seedplanter",
        # Mineur (chapitre 8)
        "AMETHYST_EXCAVATOR": "amethyst_excavator", "TITANE_EXCAVATOR": "titane_excavator",
        "PALADIUM_EXCAVATOR": "paladium_excavator", "MAGNET": "magnet",
        "MOULA_STONE": "moula_stone", "VOIDSTONE_MINAGE": "voidstone_minage",
        "CAVE_BLOCK": "cave_block", "ENDIUM_CAVE_BLOCK": "endium_cave_block",
        "PALADIUM_FURNACE": "paladium_furnace", "FORGE": "palaforge",
        "FURNACE_UPGRADE": "furnace_upgrade", "PALADIUM_HOPPER": "paladium_hopper",
        "AMETHYST_CHEST": "amethyst_chest", "TITANE_CHEST": "titane_chest",
    }
    # Un meme objet 1.7.10 a plusieurs metas, plusieurs objets en 26.2 : la
    # recette se choisit par (nom, meta). Sans cette table, les quatre outils de
    # construction prendraient tous la recette du premier.
    PORTED_BY_META = {
        ("BUILDER_WAND", 0): "amethyst_builder_wand",
        ("BUILDER_WAND", 1): "titane_builder_wand",
        ("BUILDER_WAND", 2): "paladium_builder_wand",
        ("BUILDER_WAND", 3): "endium_builder_wand",
    }
    # Les memes familles que EQUIPMENT_METALS plus haut : l'endium mixe n'a que
    # l'armure, l'endium n'a pas de pelle.
    for metal, palamod_metal, tools in (
            ("amethyst", "AMETHYST", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("titane", "TITANE", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("paladium", "PALADIUM", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("green_paladium", "PALADIUM_GREEN", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("mixed_endium", "MIXED_ENDIUM", ()),
            ("endium", "ENDIUM", ("SWORD", "PICKAXE", "AXE"))):
        for piece in tools + ("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"):
            PORTED["%s_%s" % (palamod_metal, piece)] = "%s_%s" % (metal, piece.lower())
    for metal in ("AMETHYST", "TITANE", "PALADIUM", "GREEN_PALADIUM", "ENDIUM"):
        PORTED["%s_RADIUS_HOE" % metal] = "%s_radius_hoe" % metal.lower()

    INGREDIENTS = {
        ("Items.field_151055_y", 0): "minecraft:stick",
        ("Items.field_151044_h", 0): "minecraft:coal",
        ("Items.field_151043_k", 0): "minecraft:gold_ingot",
        ("Items.field_151102_aT", 0): "minecraft:sugar",
        ("Blocks.field_150347_e", 0): "minecraft:cobblestone",
        ("Blocks.field_150486_ae", 0): "minecraft:chest",
        ("Blocks.field_150340_R", 0): "minecraft:gold_block",
        # Bloc de bois sans meta : le chene, et seulement lui en 1.7.10.
        ("Blocks.field_150364_r", 0): "minecraft:oak_log",
        # Tete meta 4 : creeper. Colorant meta 15 : poudre d'os.
        ("Items.field_151144_bL", 4): "minecraft:creeper_head",
        ("Items.field_151100_aR", 15): "minecraft:bone_meal",
        ("BlocksRegister.BLOCK_GPALADIUM", 0): "%s:green_paladium_block" % NS,
        ("BlocksRegister.COBBLEBREAKER", 0): "%s:cobblebreaker" % NS,
        ("ItemsRegister.FINDIUM", 0): "%s:findium" % NS,
        ("ItemsRegister.ENDIUM_FRAGMENT", 0): "%s:endium_fragment" % NS,
        ("ItemsRegister.ENDIUM_NUGGET", 0): "%s:endium_nugget" % NS,
        ("ItemsRegister.PALADIUM_GREEN_INGOT", 0): "%s:green_paladium_ingot" % NS,
        ("Items.field_151079_bi", 0): "minecraft:ender_pearl",
        ("Blocks.field_150339_S", 0): "minecraft:iron_block",
        ("BlocksRegister.BLOCK_AMETHYST", 0): "%s:amethyst_block" % NS,
        ("BlocksRegister.BLOCK_TITANE", 0): "%s:titane_block" % NS,
        ("BlocksRegister.BLOCK_PALADIUM", 0): "%s:paladium_block" % NS,
        ("Blocks.field_150359_w", 0): "minecraft:glass",
        ("Blocks.field_150336_V", 0): "minecraft:bricks",
    }
    for metal in ("AMETHYST", "TITANE", "PALADIUM", "ENDIUM"):
        INGREDIENTS[("ItemsRegister.%s_INGOT" % metal, 0)] = "%s:%s_ingot" % (NS, metal.lower())
        INGREDIENTS[("ItemsRegister.COMPRESSED_%s" % metal, 0)] = "%s:compressed_%s" % (NS, metal.lower())
    # Tout ce que PORTED produit peut servir d'ingredient a une autre recette.
    for palamod_name, ported in PORTED.items():
        for register in ("ItemsRegister", "BlocksRegister", "PSRegister_Items", "ItemsRegistry"):
            INGREDIENTS.setdefault(("%s.%s" % (register, palamod_name), 0), "%s:%s" % (NS, ported))

    crafts = json.load(open(os.path.join(os.path.dirname(__file__), "..", "data",
                                         "palamod_crafts.json"), encoding="utf-8"))
    emitted = {}
    for recipe in crafts:
        result = (recipe["result"]["item"] or "").split(".", 1)[-1]
        name = PORTED_BY_META.get((result, recipe["result"].get("meta", 0)), PORTED.get(result))
        if (result, 0) in PORTED_BY_META and name == PORTED.get(result):
            name = None
        # Les haches existent en deux grilles miroir et quelques recettes sont
        # declarees deux fois. 26.2 accepte le miroir de lui-meme : une recette
        # par objet, la premiere.
        if name is None or name in emitted or not recipe["keys"]:
            continue

        pattern = recipe["pattern"]
        width = max(len(row) for row in pattern)
        # Une grille de 1.7.10 est rectangulaire par construction : CraftingManager
        # concatene les lignes et decoupe par la largeur de la derniere. Une
        # ligne plus courte ici est donc un defaut de relevé, pas une donnee.
        if any(len(row) != width for row in pattern):
            raise SystemExit("recette de %s : lignes de largeurs inegales %r — "
                             "relevé fautif, relancer extract_crafts.py avec les .class"
                             % (name, pattern))

        keys = {}
        for key, ingredient in recipe["keys"].items():
            target = INGREDIENTS.get((ingredient["id"], ingredient["meta"]))
            if target is None:
                raise SystemExit("recette de %s : ingredient inconnu %s (meta %d) — "
                                 "l'ajouter a INGREDIENTS plutot que de deviner"
                                 % (name, ingredient["id"], ingredient["meta"]))
            keys[key] = target

        # Une case dont la lettre n'a pas de cle valait null en 1.7.10, donc
        # VIDE ; une cle qu'aucune case n'utilise ne servait a rien. 26.2 refuse
        # les deux (« Key defines symbols that aren't used in pattern »). On
        # porte ce que le jeu faisait : c'est le cas du cobblebreaker, ou la
        # pioche en paladium n'a jamais ete demandee.
        used = {char for row in pattern for char in row if char != " "}
        pattern = ["".join(char if char in keys else " " for char in row) for row in pattern]
        keys = {key: value for key, value in keys.items() if key in used}

        shaped(name, pattern, keys, "%s:%s" % (NS, name), recipe["result"]["count"])
        emitted[name] = recipe

    missing_recipes = sorted((set(PORTED.values()) | set(PORTED_BY_META.values())) - set(emitted))
    if missing_recipes:
        raise SystemExit("aucune recette relevee pour : %s" % ", ".join(missing_recipes))
    print("recettes : %d portees depuis data/palamod_crafts.json" % len(emitted))

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

    # Le minerai de pepites d'endium. En 1.7.10 il ne naissait que dans la
    # dimension de minage (BaseWorldGenerator, isMinerDimension), qui n'est pas
    # portee : on le place donc au fond de l'overworld, ou se trouve
    # l'equivalent de sa couche. Quatre tirages par chunk a 1 chance sur 225
    # (paladiumgreen_ore_rarety x 3), et un filon de 3 ramene a UN bloc — le
    # code effacait les autres dans un cube de 17. D'ou replace_single_block,
    # et une chance de 4/225, soit 1 sur 56.
    nugget_state = {"Name": "%s:endium_nugget_ore" % NS}
    write("data/%s/worldgen/configured_feature/ore_endium_nugget.json" % NS, {
        "type": "minecraft:replace_single_block",
        "config": {"targets": [
            {"target": {"predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:stone_ore_replaceables"},
             "state": nugget_state},
            {"target": {"predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:deepslate_ore_replaceables"},
             "state": nugget_state},
        ]},
    })
    write("data/%s/worldgen/placed_feature/ore_endium_nugget.json" % NS, {
        "feature": "%s:ore_endium_nugget" % NS,
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 56},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range",
             "height": {"type": "minecraft:uniform",
                        "min_inclusive": {"absolute": -64},
                        "max_inclusive": {"absolute": -54}}},
            {"type": "minecraft:biome"},
        ],
    })

    # Un seul modificateur de biome pour tous les minerais : NeoForge les ajoute
    # a l'etape underground_ores de tout biome de l'overworld.
    write("data/%s/neoforge/biome_modifier/ores.json" % NS, {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": ["%s:ore_%s" % (NS, name.removesuffix("_ore"))
                     for name in sorted(ORE_GEN)] + ["%s:ore_endium_nugget" % NS],
        "step": "underground_ores",
    })

    # ---- ecriture des tags accumules ----
    for path, entries in sorted(collected_tags.items()):
        write("data/%s" % path, {"replace": False, "values": sorted(entries)})

    # ---- langues ----
    fr["itemGroup.%s.materials" % NS] = "PalaMod — minerais et materiaux"
    en["itemGroup.%s.materials" % NS] = "PalaMod — Ores and Materials"
    for code, table in (("fr_fr", fr), ("en_us", en)):
        write("assets/%s/lang/%s.json" % (NS, code),
              {key: table[key] for key in sorted(table)})

    # ---- rapport ----
    print("textures : %d du pack officiel, %d du jar"
          % (provenance["pack"], len(provenance["jar"])))
    if provenance["jar"]:
        print("  absentes du pack, reprises du jar :")
        for path in sorted(provenance["jar"]):
            print("    " + path)
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
