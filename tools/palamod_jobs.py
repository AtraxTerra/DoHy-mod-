#!/usr/bin/env python3
"""Genere data/jobs.json depuis le bytecode de PalaMod.

Pourquoi un script et pas une table ecrite a la main : les niveaux de
deblocage sont dans PalaMod 10.3.0, pas sur le wiki. Les y lire est la seule
facon d'etre juste, et le wiki se trompe par endroits (il donne le totem de
fertilite au niveau 55, le code dit 70). Relancer ce script sur une version
suivante du mod regenere la table au lieu de demander un audit a la main.

    # 1. extraire le jar (c'est un zip)
    mkdir palamod && cd palamod && unzip ../PalaMod-10.3.0.pala
    # 2. lire les tables au bytecode
    python3 tools/extract_jobs.py . > jobs_raw.json
    # 3. produire le datapack
    python3 tools/palamod_jobs.py jobs_raw.json > data/jobs.json

CONVERSION D'ECHELLE. PalaMod et PalaJobs 8.0.0 comptent **20 niveaux** par
metier. Les metiers de DoHy en comptent **100**. Les niveaux sont donc
multiplies par 5. Deux verifications independantes valident le facteur : le
cobblebreaker est au niveau 5 dans le code et le wiki Java l'annonce au 25 ; le
crusher est au 8 et le wiki l'annonce au 40. Les deux tombent juste.

Le verrou du mod porte sur la POSE et l'USAGE, pas sur le craft — voir
JobGateEvents pour la raison (ItemCraftedEvent n'est pas annulable en 26.2).
On prend donc l'union des deux tables de PalaMod, au niveau le plus bas des
deux quand elles different.
"""
import json
import sys

SCALE = 5

JOB_KEYS = {
    "Miner": "miner",
    "Farmer": "farmer",
    "Hunter": "hunter",
    "Alchemist": "alchemist",
}

# Nom du champ dans PalaMod -> (identifiant moderne, libelle francais).
#
# Les identifiants suivent la convention moderne (snake_case, le bloc de
# culture separe de son fruit et de sa graine) plutot que les noms
# d'origine en 1.7.10. Les libelles viennent de assets/palamod/lang/fr_FR.lang
# quand le mod en fournit un.
NAMES = {
    # -- Mineur : machines et outils
    "COBBLEBREAKER": ("cobblebreaker", "Cobblebreaker"),
    "COBBLEBREAKER_AMETHYST_UPGRADE": (
        "cobblebreaker_amethyst_upgrade", "Amelioration amethyste du cobblebreaker (x1,5)"),
    "COBBLEBREAKER_TITANE_UPGRADE": (
        "cobblebreaker_titane_upgrade", "Amelioration titane du cobblebreaker (x2,5)"),
    "COBBLEBREAKER_PALADIUM_UPGRADE": (
        "cobblebreaker_paladium_upgrade", "Amelioration paladium du cobblebreaker (x4)"),
    "FORGE": ("palaforge", "Palaforge"),
    "PALADIUM_HOPPER": ("paladium_hopper", "Entonnoir en paladium"),
    "WITHERED_REINFORCED_PISTON": ("withered_reinforced_piston", "Piston renforce wither"),
    "VOIDSTONE": ("voidstone", "Voidstone"),
    "VOIDSTONE_MINAGE": ("voidstone_minage", "Voidstone minage"),
    "MAGNET": ("magnet", "Aimant"),
    "MOULA_STONE": ("moula_stone", "Dollars Stone"),
    "BUILDER_WAND": ("builder_wand", "Outil de construction"),
    "AMETHYST_EXCAVATOR": ("amethyst_excavator", "Excavatrice en amethyste"),
    "TITANE_EXCAVATOR": ("titane_excavator", "Excavatrice en titane"),
    "PALADIUM_EXCAVATOR": ("paladium_excavator", "Excavatrice en paladium"),
    "ENDIUM_PICKAXE": ("endium_pickaxe", "Pioche en endium"),
    "ENDIUM_CAVE_BLOCK": ("endium_cave_block", "Cave block en endium"),
    "ENDIUM_NUGGET_ORE": ("endium_nugget_ore", "Minerai de pepites d'endium"),
    "HARDNESS_POTION": ("hardness_potion", "Potion de durete"),

    # -- Fermier : cultures et machines
    "CRUSHER": ("crusher", "Crusher"),
    "Totem": ("totem_of_fertility", "Totem de fertilite"),
    "CROPS_EGGPLANT": ("eggplant_crop", "Culture d'aubergines"),
    "CROPS_CHERVIL": ("chervil_crop", "Culture de cerfeuil"),
    "CROPS_KIWANO": ("kiwano_crop", "Culture de kiwanos"),
    "CROPS_ORANGEBLUE": ("orangeblue_crop", "Culture d'orangeblues"),
    # Les noms de champ lvl1..lvl5 ne suivent PAS l'ordre des tiers de
    # Paladium : lvl4 est l'endium et lvl5 le paladium vert, et le paladium
    # vert vient avant l'endium. D'ou les niveaux 18 puis 20, qui semblent
    # inverses mais ne le sont pas. On nomme par le materiau, pas par le
    # numero, pour que l'ordre se lise tout seul.
    "SEEDPLANTER_LVL1": ("amethyst_seedplanter", "Seedplanter en amethyste"),
    "SEEDPLANTER_LVL2": ("titane_seedplanter", "Seedplanter en titane"),
    "SEEDPLANTER_LVL3": ("paladium_seedplanter", "Seedplanter en paladium"),
    "SEEDPLANTER_LVL5": ("green_paladium_seedplanter", "Seedplanter en paladium vert"),
    "SEEDPLANTER_LVL4": ("endium_seedplanter", "Seedplanter en endium"),
    "ENDIUM_AXE": ("endium_axe", "Hache en endium"),
    "ENDIUM_CHESTPLATE": ("endium_chestplate", "Plastron en endium"),

    # -- Chasseur
    "HUNTER_COMPASS": ("hunter_compass", "Boussole de chasseur"),
    "INFERNAL_KNOCKER": ("infernal_knocker", "Infernal knocker"),
    "SPAWNER_FINDER": ("spawner_finder", "Spawner finder"),
    "CAPTURE_SWORD": ("capture_sword", "Epee de capture"),
    "CAPTURE_STONE": ("capture_stone", "Pierre de capture"),
    "PALADIUM_KEY": ("paladium_key", "Cle en paladium"),
    "ENDIUM_NAMETAG": ("endium_nametag", "Etiquette en endium"),
    "ENDIUM_HELMET": ("endium_helmet", "Casque en endium"),
    "ENDIUM_SWORD": ("endium_sword", "Epee en endium"),
    "ENDIUM_TOTEM": ("endium_totem", "Totem en endium"),
    "SACRIFICE_HOTEL": ("sacrifice_hotel", "Autel de sacrifice"),
    "PERLINPINPIN_POWER": ("perlinpinpin_powder", "Poudre de perlimpinpin"),
    "WITHER_DUST": ("wither_dust", "Poudre de wither"),

    # -- Alchimiste
    "ALCHEMY_CREATOR_BLOCK": ("alchemy_creator", "Table d'alchimiste"),
    "CAULDRON_BLOCK": ("cauldron", "Bloc du chaudron"),
    "CAULDRON_CORE": ("cauldron_core", "Coeur du chaudron"),
    "FLOWER_FARM": ("flower_totem", "Totem fleuri"),
    "TANK_GOLD": ("gold_tank", "Tank en or"),
    "TANK_AMETHYSTE": ("amethyst_tank", "Tank en amethyste"),
    "TANK_TITANE": ("titane_tank", "Tank en titane"),
    "TANK_PALADIUM": ("paladium_tank", "Tank en paladium"),
    "SHINY_JACARANDA_WOOD": ("shiny_jacaranda_wood", "Bois de jacaranda brillant"),
    "SHINY_JUDEECERCIS_WOOD": ("shiny_judeecercis_wood", "Bois de judee brillant"),
    "SHINY_ERABLE_WOOD": ("shiny_erable_wood", "Bois d'erable brillant"),
    "SHINY_OSTRYA_WOOD": ("shiny_ostrya_wood", "Bois d'ostrya brillant"),
    "AMETHYSTE_PORTAL_KEY": ("amethyst_portal_key", "Cle de portail en amethyste"),
    "AMETHYSTE_PORTAL_BLOCK": ("amethyst_portal_block", "Bloc de portail en amethyste"),
    "TITANE_PORTAL_KEY": ("titane_portal_key", "Cle de portail en titane"),
    "TITANE_PORTAL_BLOCK": ("titane_portal_block", "Bloc de portail en titane"),
    "PALADIUM_PORTAL_KEY": ("paladium_portal_key", "Cle de portail en paladium"),
    "PALADIUM_PORTAL_BLOCK": ("paladium_portal_block", "Bloc de portail en paladium"),
    "ENDIUM_PORTAL_KEY": ("endium_portal_key", "Cle de portail en endium"),
    "ENDIUM_PORTAL_BLOCK": ("endium_portal_block", "Bloc de portail en endium"),
    "ENDIUM_ANGLE_PORTAL_BLOCK": ("endium_angle_portal_block", "Bloc d'angle de portail en endium"),
    "ENDIUM_LEGGINGS": ("endium_leggings", "Jambieres en endium"),
    "ENDIUM_BOOTS": ("endium_boots", "Bottes en endium"),
    "ENDIUM_POLLEN": ("endium_pollen", "Pollen d'endium"),
    "ENDIUM_HEART": ("endium_heart", "Coeur en endium"),
}


# Objets a meta : en 1.7.10 une seule entree de registre, en 26.2 autant
# d'objets distincts. Sans cette table, les quatre outils de construction
# s'ecraseraient en un seul verrou.
NAMES_BY_META = {
    ("BUILDER_WAND", 0): ("amethyst_builder_wand", "Outil de construction en amethyste"),
    ("BUILDER_WAND", 1): ("titane_builder_wand", "Outil de construction en titane"),
    ("BUILDER_WAND", 2): ("paladium_builder_wand", "Outil de construction en paladium"),
    ("BUILDER_WAND", 3): ("endium_builder_wand", "Outil de construction en endium"),
}


def fallback(field):
    """Un champ non repertorie devient un identifiant lisible plutot qu'une erreur."""
    return field.lower(), field.replace("_", " ").capitalize()


# ---------------------------------------------------------------------------
# Recompenses de niveau (Reward.builder dans les quatre registerers).
#
# Seules les recompenses en OBJETS sont portees. Les recompenses en argent de
# Paladium (1 000 $ au niveau 2, puis +500 $ par niveau) ne le sont pas : c'est
# l'economie de DoHy qui paie chaque niveau, avec sa propre courbe.
# ---------------------------------------------------------------------------

# Objets vanilla, par champ obfusque de 1.7.10. La lettre finale d'un champ SRG
# suit l'ordre de declaration de la classe : Items.field_151079_bi est la
# perle de l'Ender, _bj la baguette de blaze, _bo la fiole vide — la meme
# fiole que la potion de durete rend apres usage, ce qui recoupe la table.
VANILLA = {
    ("Blocks", "field_150343_Z", 0): ("minecraft:obsidian", "Obsidienne"),
    ("Blocks", "field_150449_bY", 0): ("minecraft:nether_quartz_ore", "Minerai de quartz du Nether"),
    ("Items", "field_151072_bj", 0): ("minecraft:blaze_rod", "Baguette de blaze"),
    # Crane : meta 1 wither squelette ; meta 5 n'existe pas en 1.7.10 vanilla,
    # c'est la tete de dragon qu'ajoute le module back2future de PalaMod
    # (enableFancySkulls, repris d'Et Futurum).
    ("Items", "field_151144_bL", 1): ("minecraft:wither_skeleton_skull", "Crane de wither squelette"),
    ("Items", "field_151144_bL", 5): ("minecraft:dragon_head", "Tete de dragon"),
}

# Objets de PalaMod sans verrou, donc absents de NAMES.
REWARD_NAMES = {
    "PALADIUM_INGOT": ("paladium_ingot", "Lingot de paladium"),
    "TRIXIUM": ("trixium", "Trixium"),
    "FINDIUM": ("findium", "Findium"),
    "AMETHYST_INGOT": ("amethyst_ingot", "Lingot d'amethyste"),
    "TITANE_INGOT": ("titane_ingot", "Lingot de titane"),
    "PALADIUM_GREEN_INGOT": ("green_paladium_ingot", "Lingot de paladium vert"),
    "ENDIUM_NUGGET": ("endium_nugget", "Pepite d'endium"),
    "COMPRESSED_AMETHYST": ("compressed_amethyst", "Amethyste compressee"),
    "MODIFIER_SPEED": ("speed_modifier", "Modificateur de vitesse"),
    "TITANE_RADIUS_HOE": ("titane_radius_hoe", "Houe en titane"),
    "SEED_EGGPLANT": ("eggplant_seeds", "Graines d'aubergine"),
    "SEED_CHERVIL": ("chervil_seeds", "Graines de cerfeuil"),
    "SEED_KIWANO": ("kiwano_seeds", "Graines de kiwano"),
    "PALADIUM_PICKAXE": ("paladium_pickaxe", "Pioche en paladium"),
    "PALADIUM_SWORD": ("paladium_sword", "Epee en paladium"),
    "PALADIUM_HELMET": ("paladium_helmet", "Casque en paladium"),
    "PALADIUM_CHESTPLATE": ("paladium_chestplate", "Plastron en paladium"),
    "PALADIUM_LEGGINGS": ("paladium_leggings", "Jambieres en paladium"),
    "PALADIUM_BOOTS": ("paladium_boots", "Bottes en paladium"),
    # Pas encore portes : la recompense restera inerte jusqu'a leur chapitre.
    "JOB_CANDY": ("job_candy", "Bonbon de metier"),
    "WITHER_RECEPTACLE": ("wither_receptacle", "Receptacle de wither"),
    "FURNACE_UPGRADE": ("furnace_upgrade", "Amelioration de four"),
    "TRAVEL_BOOTS": ("travel_boots", "Bottes de voyage"),
    "TRAVEL_LEGGINGS": ("travel_leggings", "Jambieres de voyage"),
    "TRAVEL_JUMPCHEST": ("travel_jumpchest", "Plastron de saut"),
    "TRAVEL_SLIMYHELMET": ("travel_slimyhelmet", "Casque gluant"),
    "PALADIUM_CHEST": ("paladium_chest", "Coffre en paladium"),
    "DIAMOND_STRING": ("diamond_string", "Fil de diamant"),
    "BOWUPGRADE_EMPTY": ("empty_bow_upgrade", "Amelioration d'arc vide"),
    "TUSK": ("tusk", "Defense"),
    "LIGHTNING_POTION": ("lightning_potion", "Potion de foudre"),
    "EXTRAPOLATED_BUCKET": ("extrapolated_bucket", "Seau extrapole"),
    "COMPRESSED_QUARTZ": ("compressed_quartz", "Quartz compresse"),
    "LOG_JACARANDA": ("jacaranda_log", "Buche de jacaranda"),
    "LOG_JUDEECERCIS": ("judeecercis_log", "Buche d'arbre de Judee"),
    "LOG_OSTRYA": ("ostrya_log", "Buche d'ostrya"),
    "LEAVE_ERABLE": ("erable_leaves", "Feuilles d'erable"),
}

# Enchantements de 1.7.10 par champ SRG. Meme regle de lettre : protection _c,
# tranchant _j, chatiment _k, efficacite _p, solidite _r, fortune _s.
ENCHANTMENTS = {
    "field_77332_c": "minecraft:protection",
    "field_77338_j": "minecraft:sharpness",
    "field_77339_k": "minecraft:smite",
    "field_77349_p": "minecraft:efficiency",
    "field_77347_r": "minecraft:unbreaking",
    "field_77346_s": "minecraft:fortune",
    "field_77348_q": "minecraft:silk_touch",
    "field_77335_o": "minecraft:looting",
}


def reward_item(owner, name, meta):
    """-> (identifiant, libelle). Echoue plutot que de deviner un vanilla."""
    if owner in ("Blocks", "Items"):
        key = (owner, name, meta)
        if key not in VANILLA:
            raise SystemExit("recompense : objet vanilla inconnu %s.%s meta %d — "
                             "l'ajouter a VANILLA" % (owner, name, meta))
        return VANILLA[key]
    path, label = REWARD_NAMES.get(name) or NAMES.get(name) or fallback(name)
    return "palamod:" + path, label


def rewards_of(raw):
    rewards = []
    for job_class, tables in raw.items():
        job = JOB_KEYS.get(job_class)
        if job is None:
            continue
        for reward in tables.get("rewards", []):
            if reward.get("type") != "GIVE":
                continue
            owner, name = reward["item"]
            item_id, label = reward_item(owner, name, reward.get("meta", 0))
            entry = {
                "job": job,
                "level": reward["level"] * SCALE,
                "id": item_id,
                "count": reward.get("count", 1),
                "label": label,
            }
            enchantments = []
            for enchantment in reward.get("enchantments", []):
                if enchantment["id"] not in ENCHANTMENTS:
                    raise SystemExit("recompense : enchantement inconnu %s" % enchantment["id"])
                enchantments.append({"id": ENCHANTMENTS[enchantment["id"]],
                                     "level": enchantment["level"]})
            if enchantments:
                entry["enchantments"] = enchantments
            rewards.append(entry)
    order = list(JOB_KEYS.values())
    rewards.sort(key=lambda r: (order.index(r["job"]), r["level"]))
    return rewards


def main():
    raw = json.load(open(sys.argv[1], encoding="utf-8"))

    # (job, id) -> level, au niveau le plus bas des deux tables.
    merged = {}
    for job_class, tables in raw.items():
        job = JOB_KEYS.get(job_class)
        if job is None:
            continue
        for method in ("addBlacklistedCraft", "addBlacklistedUsage"):
            for row in tables.get(method, []):
                # Niveau 0 ou 1 : aucun verrou reel, le joueur y est des le depart.
                if row["level"] < 2:
                    continue
                meta = row.get("meta", 0)
                path, label = NAMES_BY_META.get(
                    (row["name"], meta), NAMES.get(row["name"], fallback(row["name"])))
                key = (job, path, label)
                level = row["level"] * SCALE
                if key not in merged or level < merged[key]:
                    merged[key] = level

    unlocks = [
        {"id": f"palamod:{path}", "job": job, "level": level, "label": label}
        for (job, path, label), level in merged.items()
    ]
    unlocks.sort(key=lambda u: (list(JOB_KEYS.values()).index(u["job"]), u["level"], u["id"]))

    document = {
        "_comment": (
            "Deblocages et recompenses en objets par metier. GENERE par tools/palamod_jobs.py "
            "depuis le bytecode de PalaMod 10.3.0 — ne pas editer a la main. Les metiers sont "
            "la progression de DoHy : leur courbe, leurs sources d'XP et leurs perks sont dans "
            "job_progression.json."
        ),
        "_echelle": (
            f"PalaMod compte 20 niveaux par metier, DoHy en compte 100 : les "
            f"niveaux du mod sont multiplies par {SCALE}. Valide deux fois contre le wiki Java, "
            "qui est deja gradue sur 100 : cobblebreaker niveau 5 dans le code -> 25, annonce 25 ; "
            "crusher niveau 8 -> 40, annonce 40."
        ),
        "_portee": (
            "Le verrou porte sur la pose et l'usage, pas sur le craft : "
            "PlayerEvent.ItemCraftedEvent n'est pas annulable en 26.2 (verifie a la sonde), et "
            "tous les contournements font perdre les ingredients au joueur. Voir JobGateEvents."
        ),
        "_syntaxe": (
            "id peut commencer par # pour viser un tag, meme convention que les sources d'XP de "
            "job_progression.json. label sert aux messages quand l'item n'est pas encore enregistre."
        ),
        "_attention": (
            "Une entree nommant un item que le mod n'a pas encore enregistre est INERTE. C'est "
            "normal tant que les chapitres minerais, cultures et machines ne sont pas faits : le "
            "chargement le signale et /metiers audit les liste."
        ),
        "unlocks": unlocks,
        "_recompenses": (
            "Objets donnes en atteignant un niveau, en plus de l'argent et des perks de "
            "job_progression.json. Releves dans les Reward.builder de PalaMod, niveaux "
            "multiplies par 5 comme les verrous. Les recompenses en argent de Paladium ne sont "
            "pas portees : DoHy paie deja chaque niveau. Un objet non enregistre est inerte."
        ),
        "rewards": rewards_of(raw),
    }
    json.dump(document, sys.stdout, indent=2, ensure_ascii=False)
    print()


if __name__ == "__main__":
    main()
