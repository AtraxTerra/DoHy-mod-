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
metier. Les skills de DoHy en comptent **100**. Les niveaux sont donc
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


def fallback(field):
    """Un champ non repertorie devient un identifiant lisible plutot qu'une erreur."""
    return field.lower(), field.replace("_", " ").capitalize()


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
                path, label = NAMES.get(row["name"], fallback(row["name"]))
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
            "Table des deblocages par metier. GENERE par tools/palamod_jobs.py depuis le "
            "bytecode de PalaMod 10.3.0 — ne pas editer a la main. Un metier est un nom donne "
            "a un skill : Mineur lit mining, Chasseur lit combat, Fermier lit farming, "
            "Alchimiste lit alchemy. Les niveaux ci-dessous sont donc des niveaux de skill."
        ),
        "_echelle": (
            f"PalaMod compte 20 niveaux par metier, les skills de DoHy en comptent 100 : les "
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
            "skills.json. label sert aux messages quand l'item n'est pas encore enregistre."
        ),
        "_attention": (
            "Une entree nommant un item que le mod n'a pas encore enregistre est INERTE. C'est "
            "normal tant que les chapitres minerais, cultures et machines ne sont pas faits : le "
            "chargement le signale et /metiers audit les liste."
        ),
        "unlocks": unlocks,
    }
    json.dump(document, sys.stdout, indent=2, ensure_ascii=False)
    print()


if __name__ == "__main__":
    main()
