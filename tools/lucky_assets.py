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
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "palamod")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "palamod")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "palamod", "lucky")
CATALOG = os.path.join(ROOT, "tools", "data", "lucky_events.json")
# Les fichiers ou sont declares les evenements portes (c.classic / c.findium).
SOURCES = ("GoodEvents.java", "BadEvents.java", "FindiumEvents.java", "EffectEvents.java", "TrickEvents.java",
           "FindiumTricks.java", "ContentEvents.java", "FindiumContent.java",
           "LuckyItemEvents.java", "ClassicContent.java", "MarchEvents.java",
           "EasterEvents.java", "MayEvents.java", "JuneEvents.java",
           "JulyEvents.java", "AugustEvents.java")
NS = "palamod"

TYPES = {
    "paladium": ("Lucky Block en paladium", "Paladium Lucky Block"),
    "endium": ("Lucky Block en endium", "Endium Lucky Block"),
    "findium": ("Lucky Block en findium", "Findium Lucky Block"),
    # Les lucky blocks saisonniers (LuckyType.BLACK, MARCH...), dans l'ordre de l'enum.
    "black": ("Lucky Block noir", "Black Lucky Block"),
    "march": ("Lucky Block de mars", "March Lucky Block"),
    "easter": ("Lucky Block de Paques", "Easter Lucky Block"),
    "may": ("Lucky Block de mai", "May Lucky Block"),
    "june": ("Lucky Block de juin", "June Lucky Block"),
    "july": ("Lucky Block de juillet", "July Lucky Block"),
    "august": ("Lucky Block d'aout", "August Lucky Block"),
    "september": ("Lucky Block de septembre", "September Lucky Block"),
    "halloween": ("Lucky Block d'Halloween", "Halloween Lucky Block"),
    "november": ("Lucky Block de novembre", "November Lucky Block"),
    "christmas": ("Lucky Block de Noel", "Christmas Lucky Block"),
}
# Le seul bloc dont l'image n'a pas le nom du type dans le jar.
TYPE_TEXTURES = {"christmas": "christmas_luckyblock.png"}

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
    # Le cinquieme lot.
    "commando": ("Mission commando", "Commando", "mission_commando"),
    "cow_player": ("Cow Player", "Cow Player", "cow_player"),
    "lag": ("56K", "56K", "56k"),
    "magnetic": ("Aimante", "Magnetic", "aimente"),
    "no_texture": ("404 Not found", "404 Not Found", "404_"),
    "bad_face": ("Mauvaise mine", "Bad Face", "bad_face"),
    "fart": ("T'as mange quoi ?", "What Did You Eat?", "t_a_mange_quoi"),
    "tombe": ("Ton heure a sonne", "Your Time Has Come", "tombe"),
    "sugar": ("Trop de sucre", "Sugar Rush", "soda_pop"),
    "giant": ("Geant", "Giant", "champignon_geant"),
    # Lucky blocks saisonniers (SeasonEffects).
    "star": ("La tete dans les etoiles", "Head in the Stars", "march/head_in_stars"),
    "no_oxygen": ("Plus d'oxygene", "No Oxygen", "march/no_oxygen"),
    "jupiter_gravity": ("Gravite de Jupiter", "Jupiter Gravity", "march/astronaut_training"),
    "flip": ("Ca fait flipper", "Flipped", "easter/fait_flipper"),
    "hacked": ("Hackerman", "Hacked", "easter/hackerman"),
    "april_fish": ("Poisson d'avril", "April Fish", "easter/poisson_avril"),
    "pink": ("La vie en rose", "Life in Pink", "may/vie_en_rose"),
    "dance": ("Danse", "Dance", "june/super_danseur"),
    "quack": ("Quack", "Quack", "june/quack"),
    "silent_movie": ("Cinema muet", "Silent Movie", "june/cinema_muet"),
    "fire_walk": ("Enflamme le dance-floor", "Fire Walk", "june/enflamme_dancefloor"),
    "no_boat": ("Marin d'eau douce", "Landlubber", "july/fresh_water_sailor"),
    "eye_patch": ("Cache-oeil", "Eye Patch", "july/eye_patch"),
    "scurvy": ("Scorbut", "Scurvy", "july/scorbut"),
    "pirate_talk": ("Parler comme un pirate", "Pirate Talk", "july/talk_like_pirate"),
    "immune": ("Invulnerabilite", "Immunity", "july/red_cross"),
    "sunburn": ("Coup de soleil", "Sunburn", "august/sunburn"),
    "baking": ("Cuisson", "Baking", "august/armor_bake"),
    "summer_body": ("Summer body", "Summer Body", "august/summer_body_dilemma"),
    "mess": ("Tout un foin", "Hay Mess", "august/dont_make_mess"),
    "no_forward": ("Aucune avancee", "No Progress", "may/aucune_avancee"),
}

# Les filtres d'ecran (EventsManager.drawFilter) : des aplats, aux couleurs de
# greenfilter.png et floufilter.png de PalaMod, etires sur tout l'ecran.
FILTERS = {
    "green": (154, 255, 105, 121),
    "flou": (214, 214, 214, 222),
    # « La vie en rose » (PotionPink).
    "pink": (255, 120, 190, 90),
    # « Cinema muet » : un vieux film, sepia.
    "sepia": (112, 88, 60, 110),
    # « Faut pas en faire tout un foin » : de la paille partout.
    "hay": (214, 184, 74, 150),
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
    "jetpack": {"humanoid": ("jetpack", "models/jetpack_layer_1.png")},
    "party_mask": {"humanoid": ("party_mask", None)},
    "star_helmet": {"humanoid": ("star_helmet", "armors/star_helmet.png")},
    "easter_bunny": {"humanoid": ("easter_bunny", "models/easter_bunny_armor_1.png"),
                     "humanoid_leggings": ("easter_bunny", "models/easter_bunny_armor_1.png")},
    "ghost_cape": {"humanoid": ("ghost_cape", "models/ghostcape_armor_1.png")},
    "eye_patch": {"humanoid": ("eye_patch", "models/eye_patch.png")},
}
# Sur le cote des AirMin portees, une petite marque sombre (une signature de marque) :
# effacee, la toile reprend sa couleur.
AIR_MIN_MARK = ((50, 50, 50, 255), (246, 230, 215, 255))

# Les objets du cinquieme lot (LuckyGadgets) : texture du jar (sous textures/) ou None si
# elle est dessinee ici (DRAWN_ITEMS), texture du mod, tenu en main, noms.
GADGET_ITEMS = {
    "grappin": ("items/grappin_standby.png", "grappin", True, "Grappin", "Grappling Hook"),
    "grappin_hook": ("items/grappin_hook.png", "grappin_hook", False, "Crochet de grappin", "Grappling Hook Head"),
    "jetpack": ("items/jetpack.png", "jetpack", False, "Jetpack", "Jetpack"),
    "lasso": ("items/lasso.png", "lasso", False, "Lasso", "Lasso"),
    "magic_potion": ("items/magic_potion.png", "magic_potion", False, "Potion magique", "Magic Potion"),
    "blaze_and_steel": ("items/blaze_and_steel.png", "blaze_and_steel", False, "Briquet de blaze", "Blaze and Steel"),
    "chunk_analyser": ("items/chunk_analyser.png", "chunk_analyser", False, "Analyseur de chunk", "Chunk Analyser"),
    "boat_furnace": ("items/boatfurnace.png", "boat_furnace", False, "Turboat", "Turboat"),
    "new_year_firework": ("items/happy_new_year_firework.png", "new_year_firework", False, "Fusee du nouvel an",
                          "New Year Firework"),
    "giant_mushroom": (None, "giant_mushroom", False, "Champignon geant", "Giant Mushroom"),
    "space_food": ("items/spacefood.png", "space_food", False, "Nourriture de l'espace", "Space Food"),
    "stats_stone": ("items/stats_stone.png", "stats_stone", False, "Pierre de statistiques", "Stats Stone"),
    "soda": (None, "soda", False, "Soda", "Soda"),
    "meter": ("items/meter.png", "meter", False, "Metre", "Measuring Tape"),
    "biome_painter": ("items/biome_painter.png", "biome_painter", True, "Peintre de biome", "Biome Painter"),
    "poulpogun": ("items/poulpogun.png", "poulpogun", True, "PoulpoGun", "PoulpoGun"),
    "smoke_bomb": ("items/smokebomb.png", "smoke_bomb", False, "Bombe fumigene", "Smoke Bomb"),
    "party_mask": (None, "party_mask", False, "Masque de bal", "Party Mask"),
    "banana": ("items/banana.png", "banana", False, "Banane", "Banana"),
    "snake_venom": ("items/snake_venom.png", "snake_venom", False, "Venin de serpent", "Snake Venom"),
    "parrot_feather": ("items/parrot_feather.png", "parrot_feather", False, "Plume de perroquet", "Parrot Feather"),
    "turtle_scales": ("items/turtle_scales.png", "turtle_scales", False, "Ecailles de tortue", "Turtle Scales"),
    "goat_shoe": ("items/goat_shoe.png", "goat_shoe", False, "Fer de chevre", "Goat Shoe"),
    "meduse_hook": ("items/meduse_hook.png", "meduse_hook", False, "Crochet de meduse", "Jellyfish Hook"),
    "panda_drool": ("items/panda_drool.png", "panda_drool", False, "Bave de panda", "Panda Drool"),
    "snail_shell": ("items/snail_shell.png", "snail_shell", False, "Coquille d'escargot", "Snail Shell"),
}
# La boussole de pistage garde les aiguilles de la boussole du jeu (cible : la pierre d'aimant).
COMPASS = ("follow_compass", "Boussole de pistage", "Tracking Compass")

# Les blocs du cinquieme lot : forme du modele, textures (palamod:block/... ou minecraft:...),
# butin ("self", None, ou une table), noms. Les textures « jar: » viennent du jar, « draw: »
# sont dessinees ici (DRAWN_BLOCKS).
GADGET_BLOCKS = {
    "colored_lamp": ("lamp", {}, "self", "Lampe coloree", "Colored Lamp"),
    "fish_bowl": ("cube_all", {"all": "draw:fish_bowl"}, "self", "Bocal a poisson", "Fish Bowl"),
    "hunter_flower": ("cross", {"cross": "jar:blocks/flowers/flower_hunter.png"}, "self", "Plante du chasseur",
                      "Hunter Plant"),
    "kawaii_tnt": ("cube_bottom_top", {"side": "jar:blocks/kawaii_tnt_side.png", "top": "jar:blocks/kawaii_tnt_top.png",
                                       "bottom": "jar:blocks/kawaii_tnt_bottom.png"}, "self", "TNT mignonne", "Kawaii TNT"),
    "less_kawaii_tnt": ("cube_bottom_top", {"side": "jar:blocks/less_kawaii_tnt_side.png",
                                            "top": "jar:blocks/less_kawaii_tnt_top.png",
                                            "bottom": "jar:blocks/less_kawaii_tnt_bottom.png"}, "self",
                        "TNT moins mignonne", "Less Kawaii TNT"),
    "fake_tnt": ("cube_bottom_top", {"side": "minecraft:block/tnt_side", "top": "minecraft:block/tnt_top",
                                     "bottom": "minecraft:block/tnt_bottom"}, "self", "Fausse TNT", "Fake TNT"),
    "flashy_block": ("cube_all", {"all": "jar:blocks/flashy_block.png"}, "self", "Bloc flashy", "Flashy Block"),
    "banana_log": ("cube_column", {"side": "jar:blocks/banana_wood.png", "end": "jar:blocks/banana_planks.png"},
                   "self", "Bois de bananier", "Banana Log"),
    "banana_leaves": ("cube_all", {"all": "jar:blocks/banana_leave.png"}, "banana", "Feuilles de bananier",
                      "Banana Leaves"),
    "fake_paladium_ore": ("parent", {"parent": "palamod:block/paladium_ore"}, None, "Minerai de paladium",
                          "Paladium Ore"),
    "wireless_lever": ("box", {"all": "jar:blocks/wireless_lever.png", "box": [4, 0, 4, 12, 6, 12]}, "self",
                       "Levier sans fil", "Wireless Lever"),
    "dyeing_machine": ("cube_bottom_top", {"side": "jar:blocks/dyeing_machine_side.png",
                                           "top": "jar:blocks/dyeing_machine_top.png",
                                           "bottom": "jar:blocks/dyeing_machine_bottom.png"}, "self",
                       "Machine a teindre", "Dyeing Machine"),
    "basketball": ("box", {"all": "draw:basketball", "box": [4, 0, 4, 12, 8, 12]}, "self", "Ballon de basket",
                   "Basketball"),
    "basket_hoop": ("box", {"all": "draw:basket_hoop", "box": [0, 0, 14, 16, 16, 16]}, "self", "Panier de basket",
                    "Basketball Hoop"),
    "alarm": ("box", {"all": "draw:alarm", "box": [3, 0, 3, 13, 12, 13]}, "self", "Alarme", "Alarm"),
    "fake_portal": ("portal", {}, None, "Portail du Nether", "Nether Portal"),
    "jaws_trap": ("box", {"all": "draw:jaws_trap", "box": [1, 0, 1, 15, 3, 15]}, "self", "Piege a machoires",
                  "Jaws Trap"),
    "pet_rock": ("box", {"all": "jar:blocks/petrock/petrock.png", "box": [4, 0, 4, 12, 6, 12]}, "self",
                 "Caillou de compagnie", "Pet Rock"),
    "sleeping_bag": ("box", {"all": "draw:sleeping_bag", "box": [0, 0, 0, 16, 3, 16]}, "self", "Sac de couchage",
                     "Sleeping Bag"),
    "fake_lava": ("cube_all", {"all": "minecraft:block/lava_still"}, None, "Lave", "Lava"),
    "cat_detector": ("detector", {"off": "draw:cat_detector", "on": "draw:cat_detector"}, "self",
                     "Detecteur de chats", "Cat Detector"),
    "chat_detector": ("detector", {"off": "jar:blocks/chatdetector_off.png", "on": "jar:blocks/chatdetector_on.png"},
                      "self", "Detecteur de chat", "Chat Detector"),
    "safe_chest": ("orientable", {"front": "jar:blocks/safechest/safe_front.png",
                                  "side": "jar:blocks/safechest/safe_side.png"}, "self", "Coffre-fort", "Safe"),
    "mega_safe_chest": ("orientable", {"front": "jar:blocks/safechest/megasafe_front.png",
                                       "side": "jar:blocks/safechest/megasafe_side.png"}, None,
                        "Mega coffre-fort", "Mega Safe"),
    "ultra_safe_chest": ("orientable", {"front": "jar:blocks/safechest/ultrasafe_front.png",
                                        "side": "jar:blocks/safechest/ultrasafe_side.png"}, None,
                         "Ultra coffre-fort", "Ultra Safe"),
    "string_trap": ("box", {"all": "minecraft:block/tripwire", "box": [0, 0, 0, 16, 2, 16]}, "self",
                    "Piege a ficelle", "String Trap"),
    "fake_command_block": ("cube_front", {"front": "minecraft:block/command_block_front",
                                          "side": "minecraft:block/command_block_side",
                                          "back": "minecraft:block/command_block_back"}, "self",
                           "Bloc de commande", "Command Block"),
    "bruteforcer": ("orientable", {"front": "jar:blocks/bruteforcer/bruteforcer_front.png",
                                   "side": "jar:blocks/bruteforcer/bruteforcer_side.png"}, "self", "Bruteforcer",
                    "Bruteforcer"),
    "lucky_flower": ("cross", {"cross": "jar:blocks/flowers/flower_lucky.png"}, "self", "Fleur porte-bonheur",
                     "Lucky Flower"),
    "tomb": ("box", {"all": "minecraft:block/polished_andesite", "box": [1, 0, 4, 15, 15, 12]}, None, "Tombe",
             "Tomb"),
}
# Les objets des lucky blocks saisonniers (SeasonItems), comme GADGET_ITEMS : texture du jar,
# texture du mod, tenu en main, noms. Ils sont ajoutes a GADGET_ITEMS, qui fait le reste.
SEASON_ITEMS = {
    # Mars.
    "star_helmet": ("items/star_helmet.png", "star_helmet", False, "Casque des etoiles", "Star Helmet"),
    "galactic_pile": ("items/galactic_pile.png", "galactic_pile", False, "Pile galactique", "Galactic Battery"),
    "moon_piece": ("items/moon_piece.png", "moon_piece", False, "Morceau de lune", "Moon Piece"),
    "supersonic_rocket": ("items/supersonic_rocket.png", "supersonic_rocket", False, "Fusee supersonique",
                          "Supersonic Rocket"),
    "splash_potion_mars_gravity": ("items/splash_potion_mars_gravity.png", "splash_potion_mars_gravity", False,
                                   "Potion de gravite martienne", "Mars Gravity Potion"),
    "splash_potion_moon_gravity": ("items/splash_potion_moon_gravity.png", "splash_potion_moon_gravity", False,
                                   "Potion de gravite lunaire", "Moon Gravity Potion"),
    "splash_potion_jupiter_gravity": ("items/splash_potion_jupiter_gravity.png", "splash_potion_jupiter_gravity",
                                      False, "Potion de gravite de Jupiter", "Jupiter Gravity Potion"),
    "surviving_stone": ("items/survivingstone.png", "surviving_stone", False, "Pierre de survie", "Surviving Stone"),
    # Paques.
    "chocolate_egg": ("items/chocolate_egg.png", "chocolate_egg", False, "Oeuf en chocolat", "Chocolate Egg"),
    "fake_chocolate_egg": ("items/chocolate_egg.png", "fake_chocolate_egg", False, "Oeuf en chocolat",
                           "Chocolate Egg"),
    "golden_egg": ("items/golden_egg.png", "golden_egg", False, "Oeuf d'or", "Golden Egg"),
    "april_fish": ("items/april_fish.png", "april_fish", False, "Poisson d'avril", "April Fish"),
    "rainbow_leather": ("items/rainbow_leather.png", "rainbow_leather", False, "Cuir arc-en-ciel", "Rainbow Leather"),
    "rainbow_coloring": ("items/rainbow_coloring.png", "rainbow_coloring", False, "Colorant arc-en-ciel",
                         "Rainbow Coloring"),
    "easter_bunny_helmet": ("items/easter_bunny_helmet.png", "easter_bunny_helmet", False, "Tete de lapin de Paques",
                            "Easter Bunny Head"),
    "easter_bunny_chestplate": ("items/easter_bunny_chestplate.png", "easter_bunny_chestplate", False,
                                "Costume de lapin de Paques", "Easter Bunny Suit"),
    "easter_bunny_leggings": ("items/easter_bunny_leggings.png", "easter_bunny_leggings", False,
                              "Pantalon de lapin de Paques", "Easter Bunny Pants"),
    "easter_bunny_boots": ("items/easter_bunny_boots.png", "easter_bunny_boots", False, "Pattes de lapin de Paques",
                           "Easter Bunny Paws"),
    # Mai.
    "player_saddle": ("items/player_saddle.png", "player_saddle", False, "Selle de joueur", "Player Saddle"),
    "ghost_cape": ("items/ghostcape.png", "ghost_cape", False, "Cape fantome", "Ghost Cape"),
    "ghostly_sword": ("items/ghostly_sword.png", "ghostly_sword", True, "Epee fantome", "Ghostly Sword"),
    # Juin.
    "radio": ("items/radio.png", "radio", False, "Radio", "Radio"),
    "trumpet": ("items/trumpet.png", "trumpet", False, "Trompette", "Trumpet"),
    "gold_record": ("items/gold_record.png", "gold_record", False, "Disque d'or", "Gold Record"),
    "electric_guitar": ("items/electric_guitar.png", "electric_guitar", True, "Guitare electrique", "Electric Guitar"),
    "shark_plush": (None, "shark_plush", False, "Peluche requin", "Shark Plush"),
    # Juillet.
    "sea_bottle": ("items/sea_bottle.png", "sea_bottle", False, "Bouteille a la mer", "Message in a Bottle"),
    "dry_message": ("items/dry_message.png", "dry_message", False, "Message seche", "Dry Message"),
    "pirate_message": ("items/pirate_king_message.png", "pirate_message", False, "Message du roi des pirates",
                       "Pirate King's Message"),
    "eye_patch": ("items/eye_patch.png", "eye_patch", False, "Cache-oeil", "Eye Patch"),
    "pipe": ("items/pipe.png", "pipe", False, "Pipe", "Pipe"),
    "orange": ("items/orange_consumable.png", "orange", False, "Orange", "Orange"),
    "sailor_tooth": ("items/sailor_tooth.png", "sailor_tooth", False, "Dent de marin", "Sailor Tooth"),
    "tooth_skeleton_skull": ("items/tooth_skeleton_skull.png", "tooth_skeleton_skull", False, "Crane edente",
                             "Toothless Skull"),
    "parrot_seeds": ("items/parrot_seeds.png", "parrot_seeds", False, "Graines de perroquet", "Parrot Seeds"),
    "drum": ("items/drum.png", "drum", False, "Tambour", "Drum"),
    "accordion": ("items/accordion.png", "accordion", False, "Accordeon", "Accordion"),
    "treasure_map": ("items/treasure_map.png", "treasure_map", False, "Carte au tresor", "Treasure Map"),
    "invulnerability_potion": ("items/invulnerability_potion.png", "invulnerability_potion", False,
                               "Potion d'invulnerabilite", "Invulnerability Potion"),
    # Aout.
    "ice_cream": ("items/ice_cream.png", "ice_cream", False, "Glace", "Ice Cream"),
    "double_ice_cream": ("items/double_ice_cream.png", "double_ice_cream", False, "Glace deux boules",
                         "Double Ice Cream"),
    "solar_cream": ("items/solar_cream.png", "solar_cream", False, "Creme solaire", "Sun Cream"),
    "volley_ball": ("items/volley_ball.png", "volley_ball", False, "Ballon de volley", "Volleyball"),
    "portable_volley_court": ("items/portable_volley_court.png", "portable_volley_court", False,
                              "Terrain de volley portatif", "Portable Volleyball Court"),
}
SEASON_BLOCKS = {
    "telescope": ("cross", {"cross": "jar:items/telescope.png"}, "self", "Telescope", "Telescope"),
    "easter_gift": ("cross", {"cross": "jar:blocks/easter_gift_item.png"}, "self", "Cadeau de Paques", "Easter Gift"),
    "fake_easter_gift": ("cross", {"cross": "jar:blocks/easter_gift_item.png"}, "self", "Cadeau de Paques",
                         "Easter Gift"),
    "blue_easter_egg": ("cross", {"cross": "jar:blocks/easter_egg_blue_item.png"}, None, "Oeuf de Paques bleu",
                        "Blue Easter Egg"),
    "yellow_easter_egg": ("cross", {"cross": "jar:blocks/easter_egg_yellow_item.png"}, "self", "Oeuf de Paques jaune",
                          "Yellow Easter Egg"),
    "stone_hearth": ("cube_all", {"all": "jar:blocks/stone_hearth.png"}, "self", "Foyer de pierre", "Stone Hearth"),
    "fake_gold_block": ("cube_all", {"all": "minecraft:block/gold_block"}, None, "Bloc d'or", "Block of Gold"),
    "bright_sound": ("lit", {"off": "jar:blocks/bright_sound_off.png", "on": "jar:blocks/bright_sound_on.png"}, "self",
                     "Bloc sonore", "Bright Sound"),
    "powder_barrel": ("cube_bottom_top", {"top": "jar:blocks/powder_barrel_top.png",
                                          "bottom": "jar:blocks/powder_barrel_top.png",
                                          "side": "jar:blocks/powder_barrel_side.png"}, "self", "Baril de poudre",
                      "Powder Barrel"),
    "encrypted_computer": ("cube_front", {"front": "draw:computer_locked", "back": "minecraft:block/iron_block",
                                          "side": "minecraft:block/iron_block"}, "self", "Ordinateur crypte",
                           "Encrypted Computer"),
    "decrypted_computer": ("cube_front", {"front": "draw:computer_open", "back": "minecraft:block/iron_block",
                                          "side": "minecraft:block/iron_block"}, "self", "Ordinateur decrypte",
                           "Decrypted Computer"),
    "lead_block": ("cube_all", {"all": "jar:blocks/lead.png"}, "self", "Bloc de plomb", "Block of Lead"),
    "ventilator": ("fan", {"front": "jar:blocks/ventilator_front.png", "side": "jar:blocks/ventilator_side.png"}, "self",
                   "Ventilateur", "Fan"),
    "treadmill": ("cube_bottom_top", {"top": "jar:blocks/treadmill.png", "bottom": "minecraft:block/iron_block",
                                      "side": "minecraft:block/iron_block"}, "self", "Tapis de course", "Treadmill"),
    "quicksand": ("cube_all", {"all": "minecraft:block/sand"}, None, "Sable mouvant", "Quicksand"),
}
GADGET_ITEMS.update(SEASON_ITEMS)
GADGET_BLOCKS.update(SEASON_BLOCKS)
# Les recettes des objets saisonniers (le casque des etoiles de « Plus d'oxygene »).
SEASON_RECIPES = {
    "star_helmet": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["IGI", "I I"],
                    "key": {"I": "minecraft:iron_ingot", "G": "minecraft:glowstone"},
                    "result": {"id": "palamod:star_helmet", "count": 1}},
    "gold_ingot_from_golden_egg": {"type": "minecraft:crafting_shapeless", "category": "misc",
                                   "ingredients": ["palamod:golden_egg"],
                                   "result": {"id": "minecraft:gold_ingot", "count": 3}},
}
SEASON_RECIPES.update({
    "orange": {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": ["minecraft:apple", "minecraft:orange_dye"],
               "result": {"id": "palamod:orange", "count": 1}},
    "bone_meal_from_sailor_tooth": {"type": "minecraft:crafting_shapeless", "category": "misc",
                                    "ingredients": ["palamod:sailor_tooth"],
                                    "result": {"id": "minecraft:bone_meal", "count": 8}},
    "pirate_message": {"type": "minecraft:smelting", "category": "misc", "ingredient": "palamod:dry_message",
                       "result": {"id": "palamod:pirate_message", "count": 1}, "experience": 0.1,
                       "cookingtime": 200},
})
# Le cuir arc-en-ciel et une piece d'armure en cuir : la piece devient arc-en-ciel (PalaLucky.RAINBOW).
for piece in ("helmet", "chestplate", "leggings", "boots"):
    SEASON_RECIPES["rainbow_leather_%s" % piece] = {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": ["palamod:rainbow_leather", "minecraft:leather_%s" % piece],
        "result": {"id": "minecraft:leather_%s" % piece, "count": 1, "components": {"palamod:lucky_rainbow": True}}}
# Les images d'ecran copiees du jar : textures/gui/lucky/overlay/<nom>.png.
SEASON_OVERLAYS = {"stars": "ui/march/head_in_stars/head_in_stars.png"}

# Les seize teintes de la lampe coloree (getRenderColor), dans l'ordre de ses couleurs.
LAMP_TINTS = [0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFF7D00, 0xFF7D7D, 0x7DFF7D,
              0x7D00FF, 0x7DFF00, 0x7D7DFF, 0x00FF7D, 0xFF007D, 0xBEBEBE]
# Les tableaux de PalaMod (EntityLuckyPainting.EnumArt) : nom, largeur et hauteur en pixels,
# position dans painting/lucky_paintings.png.
PAINTINGS = [
    ("paladium_small", 16, 16, 0, 0), ("snow", 16, 16, 16, 0), ("alban", 16, 16, 32, 0),
    ("aztec", 16, 16, 48, 0), ("bomb", 16, 16, 64, 0), ("plant", 16, 16, 80, 0), ("wasteland", 16, 16, 96, 0),
    ("pool", 32, 16, 0, 32), ("courbet", 32, 16, 32, 32), ("sea", 32, 16, 64, 32), ("sunset", 32, 16, 96, 32),
    ("creebet", 32, 16, 128, 32), ("wanderer", 16, 32, 0, 64), ("paladium", 32, 32, 0, 128),
    ("bust", 32, 32, 32, 128), ("stage", 32, 32, 64, 128), ("void", 32, 32, 96, 128), ("butterfly", 64, 32, 0, 96),
    ("golem", 64, 64, 0, 192), ("cherry", 64, 48, 192, 64),
]

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

# Les dessins du cinquieme lot : des chauves-souris (l'image de PalaMod etait le logo d'un
# heros de bande dessinee), un champignon sans visage, un masque de bal, une canette verte et
# une silhouette barree remplacent des images qui reprenaient un personnage, un logo ou une
# marque ; les autres sont des blocs dont PalaMod n'avait qu'un rendu en 3D.
BATS_ICON = [
    "................",
    "................",
    "..K..........K..",
    ".KK...KKKK...KK.",
    ".KKK.KKKKKK.KKK.",
    "KKKKKKRKKRKKKKKK",
    "KKKKKKKKKKKKKKKK",
    ".KKKK.KKKK.KKKK.",
    "..K....KK....K..",
    "................",
    "....k......k....",
    "...kk.kkkk.kk...",
    "..kkkkkrkrkkkk..",
    "...kk.kkkk.kk...",
    "......k..k......",
    "................",
]
BATS_COLORS = {".": (0, 0, 0, 0), "K": (40, 30, 48, 255), "k": (70, 56, 80, 255), "R": (230, 40, 40, 255),
               "r": (230, 60, 60, 255)}
MUSHROOM_ICON = [
    "................",
    ".....RRRRRR.....",
    "...RRRWWRRRRR...",
    "..RRRWWWWRRWRR..",
    ".RRWRRWWRRRWWRR.",
    ".RWWWRRRRRRRWRR.",
    "RRWWWRRRWWRRRRRR",
    "RRRWRRRWWWWRRWWR",
    "RrrrrrrrrrrrrrrR",
    ".rr..........rr.",
    "......SSSS......",
    ".....SSSSSS.....",
    ".....SSSSSS.....",
    ".....SSssSS.....",
    "......ssss......",
    "................",
]
MUSHROOM_COLORS = {".": (0, 0, 0, 0), "R": (214, 38, 38, 255), "r": (150, 20, 20, 255), "W": (250, 246, 236, 255),
                   "S": (238, 222, 190, 255), "s": (200, 180, 146, 255)}
MASK_ICON = [
    "................",
    "................",
    "................",
    "......Y..Y......",
    "...YYYPPPPYYY...",
    "..YPPPPPPPPPPY..",
    ".YPPP..PP..PPPY.",
    ".YPP....P...PPY.",
    ".YPPP..PPP.PPPY.",
    "..YPPPPPYPPPPY..",
    "...YYPPY.YPPYY..",
    ".....YY...YY....",
    "................",
    "................",
    "................",
    "................",
]
MASK_COLORS = {".": (0, 0, 0, 0), "Y": (232, 186, 46, 255), "P": (120, 50, 170, 255)}
SODA_ICON = [
    "................",
    "......SSSS......",
    ".....SssssS.....",
    ".....GGGGGG.....",
    ".....GGgGGG.....",
    ".....GWGgGG.....",
    ".....GGGGWG.....",
    ".....GgGWGG.....",
    ".....GGgGGG.....",
    ".....GWGGgG.....",
    ".....GGGGGG.....",
    ".....GGGgGG.....",
    ".....GGGGGG.....",
    ".....SSSSSS.....",
    "......ssss......",
    "................",
]
SODA_COLORS = {".": (0, 0, 0, 0), "S": (196, 200, 206, 255), "s": (140, 146, 154, 255), "G": (40, 168, 92, 255),
               "g": (26, 120, 64, 255), "W": (220, 250, 230, 255)}
STALKER_ICON = [
    "................",
    ".....DDDDDD.....",
    "....DDDDDDDD....",
    "...DDddddddDD...",
    "...DdKKKKKKdD...",
    "...DdKKKKKKdD...",
    "...DdKKKKKKdD...",
    "..XDdKKKKKKdDX..",
    "...XDddddddDX...",
    "..DDXDDDDDDXDD..",
    ".DDDDXDDDDXDDDD.",
    ".DDDDDXDDXDDDDD.",
    ".DDDDDDXXDDDDDD.",
    ".DDDDDXDDXDDDDD.",
    ".DDDDXDDDDXDDDD.",
    "................",
]
STALKER_COLORS = {".": (0, 0, 0, 0), "D": (52, 52, 64, 255), "d": (36, 36, 44, 255), "K": (14, 14, 18, 255),
                  "X": (210, 30, 30, 255)}
FISHBOWL_TEX = [
    "GGGGGGGGGGGGGGGG",
    "G..............G",
    "G.w............G",
    "G.w............G",
    "G..............G",
    "G..............G",
    "Gbbbbbbbbbbbbbbb",
    "GbbbbOOObbbbbbbG",
    "GbbbOOOOOObbbbbG",
    "GbbOOOKOOOOObbbG",
    "GbbbOOOOOObOObbG",
    "GbbbbOOObbbbObbG",
    "GbbbbbbbbbbbbbbG",
    "GbbbbbbbbbbbbbbG",
    "GbbbbbbbbbbbbbbG",
    "GGGGGGGGGGGGGGGG",
]
FISHBOWL_COLORS = {".": (200, 230, 255, 40), "G": (190, 220, 240, 170), "w": (255, 255, 255, 160),
                   "b": (70, 140, 230, 110), "O": (250, 140, 30, 255), "K": (20, 20, 20, 255)}
BALL_TEX = [
    "OOOOOOOKOOOOOOOO",
    "OOOOOOOKOOOOOOOO",
    "KOOOOOOKOOOOOOOK",
    "OKKOOOOKOOOOOKKO",
    "OOOKKOOKOOOKKOOO",
    "OOOOOKKKKKKOOOOO",
    "OOOOOOOKOOOOOOOO",
    "KKKKKKKKKKKKKKKK",
    "OOOOOOOKOOOOOOOO",
    "OOOOOKKKKKKOOOOO",
    "OOOKKOOKOOOKKOOO",
    "OKKOOOOKOOOOOKKO",
    "KOOOOOOKOOOOOOOK",
    "OOOOOOOKOOOOOOOO",
    "OOOOOOOKOOOOOOOO",
    "OOOOOOOKOOOOOOOO",
]
BALL_COLORS = {"O": (232, 112, 34, 255), "K": (40, 26, 20, 255)}
BOARD_TEX = [
    "KKKKKKKKKKKKKKKK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWRRRRRRWWWWK",
    "KWWWWRWWWWRWWWWK",
    "KWWWWRWWWWRWWWWK",
    "KWWWWRWWWWRWWWWK",
    "KWWWWRRRRRRWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KWWWWWWWWWWWWWWK",
    "KKKKKKKKKKKKKKKK",
    "......RRRR......",
    ".....R....R.....",
    ".....R....R.....",
    "......RRRR......",
    "................",
]
BOARD_COLORS = {".": (0, 0, 0, 0), "K": (30, 30, 30, 255), "W": (240, 240, 240, 255), "R": (210, 40, 40, 255)}
ALARM_TEX = [
    "....RRRRRRRR....",
    "...RrrrrrrrrR...",
    "..RrrWWrrrrrrR..",
    "..RrWWrrrrrrrR..",
    "..RrrrrrrrrrrR..",
    "..RrrrrrrrrrrR..",
    "..RrrrrrrrrrrR..",
    "..RrrrrrrrrrrR..",
    "..RRRRRRRRRRRR..",
    ".GGGGGGGGGGGGGG.",
    ".GgggggggggggggG",
    ".GgggggggggggggG",
    ".GGGGGGGGGGGGGG.",
    "................",
    "................",
    "................",
]
ALARM_COLORS = {".": (0, 0, 0, 0), "R": (150, 20, 20, 255), "r": (230, 50, 40, 255), "W": (255, 220, 210, 255),
                "G": (70, 70, 78, 255), "g": (110, 110, 120, 255)}
JAWS_TEX = [
    "................",
    ".GGGGGGGGGGGGGG.",
    ".GgTgTgTgTgTgTG.",
    ".GTgTgTgTgTgTgG.",
    ".G............G.",
    ".G............G.",
    ".G....OOOO....G.",
    ".G....OKKO....G.",
    ".G....OKKO....G.",
    ".G....OOOO....G.",
    ".G............G.",
    ".G............G.",
    ".GgTgTgTgTgTgTG.",
    ".GTgTgTgTgTgTgG.",
    ".GGGGGGGGGGGGGG.",
    "................",
]
JAWS_COLORS = {".": (0, 0, 0, 0), "G": (90, 90, 98, 255), "g": (130, 130, 140, 255), "T": (210, 210, 220, 255),
               "O": (140, 100, 50, 255), "K": (90, 60, 30, 255)}
BAG_TEX = [
    "BBBBBBBBBBBBBBBB",
    "BbbbbbbbbbbbbbbB",
    "BbWWWWWbbbbbbbbB",
    "BbWWWWWbbbbbbbbB",
    "BbWWWWWbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BBBBBBBBBBBBBBBB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BbbbbbbbbbbbbbbB",
    "BBBBBBBBBBBBBBBB",
]
BAG_COLORS = {"B": (30, 60, 140, 255), "b": (56, 98, 196, 255), "W": (236, 236, 244, 255)}
CAT_TEX = [
    "SSSSSSSSSSSSSSSS",
    "SssssssssssssssS",
    "SsOs........sOsS",
    "SsOOs......sOOsS",
    "SsOOOOOOOOOOOOsS",
    "SsOOOOOOOOOOOOsS",
    "SsOOGGOOOOGGOOsS",
    "SsOOGKOOOOGKOOsS",
    "SsOOOOOOOOOOOOsS",
    "SsOOOOOPPOOOOOsS",
    "SsOOOOKOOKOOOOsS",
    "SsOOOOOKKOOOOOsS",
    "SssOOOOOOOOOOssS",
    "SsssOOOOOOOOsssS",
    "SssssssssssssssS",
    "SSSSSSSSSSSSSSSS",
]
CAT_COLORS = {"S": (96, 96, 104, 255), "s": (126, 126, 134, 255), ".": (126, 126, 134, 255),
              "O": (232, 150, 60, 255), "G": (120, 220, 90, 255), "K": (24, 24, 24, 255), "P": (240, 140, 160, 255)}
# La texture manquante de « 404 Not found », a moitie transparente.
MISSING_TEX = ["MMKK"] * 2 + ["KKMM"] * 2
MISSING_COLORS = {"M": (248, 0, 248, 120), "K": (0, 0, 0, 120)}

# Les images dessinees ici plutot que prises dans le jar.
DRAWN = {
    "chauves_souris": (BATS_ICON, BATS_COLORS),
    "champignon_geant": (MUSHROOM_ICON, MUSHROOM_COLORS),
    "masque_de_bal": (MASK_ICON, MASK_COLORS),
    "soda_pop": (SODA_ICON, SODA_COLORS),
    "retire_du_jeu_dessin": (STALKER_ICON, STALKER_COLORS),
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
    "giant_mushroom": (MUSHROOM_ICON, MUSHROOM_COLORS),
    "soda": (SODA_ICON, SODA_COLORS),
    "party_mask": (MASK_ICON, MASK_COLORS),
}
# Les textures de bloc dessinees ici.
# La peluche requin (« La musique qui fait peur ! ») : dessinee ici, la texture du jar est un patron de modele.
SHARK_TEX = [
    "................",
    "........G.......",
    ".......GG.......",
    "......GGG.......",
    ".....GGGGG......",
    "...GGGGGGGGG..G.",
    "..GGGGGGGGGGGGG.",
    ".GWGGGGGGGGGGG..",
    ".GGGGGGGGGGGGGG.",
    ".GWWWWWWWWGGG..G",
    "..WWwWwWWWWG....",
    "...WWWWWWWW.....",
    ".....GG.........",
    "................",
    "................",
    "................",
]
SHARK_COLORS = {"G": (112, 128, 150, 255), "W": (232, 236, 240, 255), "w": (200, 60, 60, 255)}
DRAWN_ITEMS["shark_plush"] = (SHARK_TEX, SHARK_COLORS)

# Les ecrans des ordinateurs de « Pirate informatique » (le jar n'a que les patrons de leur modele).
COMPUTER_LOCKED = [
    "GGGGGGGGGGGGGGGG",
    "GKKKKKKKKKKKKKKG",
    "GKKKKKKKKKKKKKKG",
    "GKKKKKRRRRKKKKKG",
    "GKKKKRKKKKRKKKKG",
    "GKKKKRKKKKRKKKKG",
    "GKKKRRRRRRRRKKKG",
    "GKKKRRRRRRRRKKKG",
    "GKKKRRRKKRRRKKKG",
    "GKKKRRRRRRRRKKKG",
    "GKKKRRRRRRRRKKKG",
    "GKKKKKKKKKKKKKKG",
    "GKKKKKKKKKKKKKKG",
    "GGGGGGGGGGGGGGGG",
    "GGGGGGDDDDGGGGGG",
    "GGGGGGGGGGGGGGGG",
]
COMPUTER_OPEN = [
    "GGGGGGGGGGGGGGGG",
    "GKKKKKKKKKKKKKKG",
    "GKVVKVVVKVKVVKKG",
    "GKKKKKKKKKKKKKKG",
    "GKVKVVKVVVVKKKKG",
    "GKKKKKKKKKKKKKKG",
    "GKVVVKVKVVKVVKKG",
    "GKKKKKKKKKKKKKKG",
    "GKVKVVVVKVKKKKKG",
    "GKKKKKKKKKKKKKKG",
    "GKVVKVKVVVKVKKKG",
    "GKKKKKKKKKKKKKKG",
    "GKKKKKKKKKKKKKKG",
    "GGGGGGGGGGGGGGGG",
    "GGGGGGDDDDGGGGGG",
    "GGGGGGGGGGGGGGGG",
]
COMPUTER_COLORS = {"G": (150, 154, 160, 255), "K": (18, 22, 28, 255), "R": (210, 50, 50, 255),
                   "V": (60, 220, 90, 255), "D": (90, 94, 100, 255)}

DRAWN_BLOCKS = {
    "fish_bowl": (FISHBOWL_TEX, FISHBOWL_COLORS),
    "basketball": (BALL_TEX, BALL_COLORS),
    "basket_hoop": (BOARD_TEX, BOARD_COLORS),
    "alarm": (ALARM_TEX, ALARM_COLORS),
    "jaws_trap": (JAWS_TEX, JAWS_COLORS),
    "sleeping_bag": (BAG_TEX, BAG_COLORS),
    "cat_detector": (CAT_TEX, CAT_COLORS),
    "computer_locked": (COMPUTER_LOCKED, COMPUTER_COLORS),
    "computer_open": (COMPUTER_OPEN, COMPUTER_COLORS),
}


def ported():
    """Les evenements des sources Java : (genre, id, nom, image, malus, rarete)."""
    events = []
    pattern = re.compile(r'c\.(classic|findium|season\(LuckyType\.[A-Z]+)\s*[(,]\s*([A-Z_]+|"[^"]+")\s*,'
                         r'\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*(true|false)\s*,\s*(\d+)')
    constants = {}
    for name in SOURCES:
        path = os.path.join(JAVA, name)
        text = open(path, encoding="utf-8").read()
        for key, value in re.findall(r'static final String ([A-Z_]+) = "([^"]+)";', text):
            constants[key] = value
        for kind, ident, label, icon, bad, rarity in pattern.findall(text):
            ident = constants.get(ident, ident.strip('"'))
            # c.season(LuckyType.MARCH, ...) : le genre est le type, en minuscules.
            kind = kind.split(".")[-1].lower() if kind.startswith("season") else kind
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
    gadget_outputs(put)
    return files


def block_texture(ref):
    """Une texture de bloc du lot 5 : jar:..., draw:... ou une texture du jeu."""
    if ref.startswith("jar:"):
        return "%s:block/lucky/%s" % (NS, os.path.basename(ref[4:])[:-4])
    if ref.startswith("draw:"):
        return "%s:block/lucky/%s" % (NS, ref[5:])
    return ref


def box_model(textures, box):
    """Un pave de {from} a {to}, la meme texture sur ses six faces."""
    x0, y0, z0, x1, y1, z1 = box
    texture = block_texture(textures["all"])
    uv = {"north": [x0, 16 - y1, x1, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
          "east": [z0, 16 - y1, z1, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
          "up": [x0, z0, x1, z1], "down": [x0, z0, x1, z1]}
    return {"parent": "minecraft:block/block", "textures": {"all": texture, "particle": texture},
            "elements": [{"from": [x0, y0, z0], "to": [x1, y1, z1],
                          "faces": {face: {"uv": coords, "texture": "#all"} for face, coords in uv.items()}}]}


def gadget_outputs(put):
    """Blocs, objets, tableaux et noms du cinquieme lot."""
    for name, recipe in SEASON_RECIPES.items():
        put(os.path.join(DATA, "recipe", name + ".json"), recipe)
    def model(name, payload):
        put(os.path.join(ASSETS, "models", "block", name + ".json"), payload)

    def states(name, variants):
        put(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})

    def item_model(name, payload):
        put(os.path.join(ASSETS, "models", "item", name + ".json"), payload)

    def item_def(name, payload=None):
        put(os.path.join(ASSETS, "items", name + ".json"),
            payload or {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})

    facings = {"north": 0, "east": 90, "south": 180, "west": 270}
    for name, (kind, textures, loot, _fr, _en) in GADGET_BLOCKS.items():
        ref = "%s:block/%s" % (NS, name)
        if kind == "lamp":
            for color in range(16):
                for lit in (False, True):
                    model("%s_%d_%s" % (name, color, "on" if lit else "off"),
                          {"parent": "minecraft:block/cube_all",
                           "textures": {"all": "%s:block/lucky/lamp_%d_%s" % (NS, color, "on" if lit else "off")}})
            states(name, {"color=%d,lit=%s" % (color, str(lit).lower()):
                          {"model": "%s_%d_%s" % (ref, color, "on" if lit else "off")}
                          for color in range(16) for lit in (False, True)})
            item_model(name, {"parent": ref + "_0_off"})
        elif kind == "detector":
            for powered in (False, True):
                suffix = "_on" if powered else ""
                model(name + suffix, {"parent": "minecraft:block/cube_all",
                                      "textures": {"all": block_texture(textures["on" if powered else "off"])}})
            states(name, {"powered=false": {"model": ref}, "powered=true": {"model": ref + "_on"}})
            item_model(name, {"parent": ref})
        elif kind == "lit":
            for lit in (False, True):
                suffix = "_on" if lit else ""
                model(name + suffix, {"parent": "minecraft:block/cube_all",
                                      "textures": {"all": block_texture(textures["on" if lit else "off"])}})
            states(name, {"lit=false": {"model": ref}, "lit=true": {"model": ref + "_on"}})
            item_model(name, {"parent": ref})
        elif kind == "fan":
            model(name, {"parent": "minecraft:block/orientable",
                         "textures": {"front": block_texture(textures["front"]), "side": block_texture(textures["side"]),
                                      "top": block_texture(textures["side"])}})
            states(name, {"facing=%s,lit=%s" % (facing, lit): ({"model": ref, "y": rotation} if rotation
                                                                else {"model": ref})
                          for facing, rotation in facings.items() for lit in ("false", "true")})
            item_model(name, {"parent": ref})
        elif kind == "orientable":
            model(name, {"parent": "minecraft:block/orientable",
                         "textures": {"front": block_texture(textures["front"]), "side": block_texture(textures["side"]),
                                      "top": block_texture(textures["side"])}})
            variants = {}
            for facing, rotation in facings.items():
                if name == "bruteforcer":
                    for powered in ("false", "true"):
                        variants["facing=%s,powered=%s" % (facing, powered)] = {"model": ref, "y": rotation} \
                            if rotation else {"model": ref}
                else:
                    variants["facing=%s" % facing] = {"model": ref, "y": rotation} if rotation else {"model": ref}
            states(name, variants)
            item_model(name, {"parent": ref})
        elif kind == "portal":
            states(name, {"": {"model": "minecraft:block/nether_portal_ns"}})
            item_model(name, {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:block/nether_portal"}})
        elif kind == "cross":
            texture = block_texture(textures["cross"])
            model(name, {"parent": "minecraft:block/cross", "textures": {"cross": texture}})
            states(name, {"": {"model": ref}})
            item_model(name, {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
        elif kind == "parent":
            states(name, {"": {"model": textures["parent"]}})
            item_model(name, {"parent": textures["parent"]})
        else:
            if kind == "box":
                model(name, box_model(textures, textures["box"]))
            elif kind == "cube_front":
                model(name, {"parent": "minecraft:block/cube", "textures": {
                    "north": block_texture(textures["front"]), "south": block_texture(textures["back"]),
                    "east": block_texture(textures["side"]), "west": block_texture(textures["side"]),
                    "up": block_texture(textures["side"]), "down": block_texture(textures["side"]),
                    "particle": block_texture(textures["side"])}})
            else:
                model(name, {"parent": "minecraft:block/" + kind,
                             "textures": {key: block_texture(value) for key, value in textures.items()}})
            keys = {"alarm": ["powered=false", "powered=true"], "fake_command_block": ["triggered=false", "triggered=true"]}
            states(name, {key: {"model": ref} for key in keys.get(name, [""])})
            if name != "tomb":
                item_model(name, {"parent": ref})
        if name != "tomb":
            item_def(name)
        if loot == "self":
            pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]
        elif loot == "banana":
            pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": "%s:banana" % NS,
                 "conditions": [{"condition": "minecraft:random_chance", "chance": 0.7}]},
                {"type": "minecraft:item", "name": "minecraft:stick"}]}],
                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]
        else:
            pools = []
        put(os.path.join(DATA, "loot_table", "blocks", name + ".json"),
            {"type": "minecraft:block", "pools": pools, "random_sequence": "%s:blocks/%s" % (NS, name)})
    for name, (_source, texture, handheld, _fr, _en) in GADGET_ITEMS.items():
        item_model(name, {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
                          "textures": {"layer0": "%s:item/%s" % (NS, texture)}})
        if name == "smoke_bomb":
            item_def(name, {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name),
                                      "tints": [{"type": "minecraft:dye", "default": -3618616}]}})
        else:
            item_def(name)
    # La boussole de pistage : les 32 aiguilles de la boussole du jeu, vers la pierre d'aimant.
    item_def(COMPASS[0], {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone", "scale": 32.0,
        "entries": [{"threshold": index - 0.5, "model": {"type": "minecraft:model",
                                                           "model": "minecraft:item/compass_%02d" % (index % 32)}}
                    for index in range(33)],
        "fallback": {"type": "minecraft:model", "model": "minecraft:item/compass_16"}}})
    for name, width, height, _u, _v in PAINTINGS:
        put(os.path.join(DATA, "painting_variant", "lucky_%s.json" % name),
            {"asset_id": "%s:lucky_%s" % (NS, name), "width": width // 16, "height": height // 16})


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
        for name, (_source, _texture, _handheld, fr, en) in GADGET_ITEMS.items():
            labels["item.%s.%s" % (NS, name)] = fr if french else en
        labels["item.%s.%s" % (NS, COMPASS[0])] = COMPASS[1] if french else COMPASS[2]
        for name, (_kind, _textures, _loot, fr, en) in GADGET_BLOCKS.items():
            labels["block.%s.%s" % (NS, name)] = fr if french else en
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
        image = Image.open(os.path.join(source, "blocks", TYPE_TEXTURES.get(key, "%s_lucky_block.png" % key))).convert(
            "RGBA")
        # Les blocs du mois sont dessines en 64 ou 128 pixels : ramenes a 16, comme les autres.
        if image.size != (16, 16):
            image = image.resize((16, 16), Image.BOX)
        image.save(os.path.join(block_dir, "%s_lucky_block.png" % key))
    frame = Image.open(os.path.join(source, "gui", "LuckyBlock", "minecraft", "foreground.png")).convert("RGBA")
    frame.resize((frame.width // 2, frame.height // 2), Image.LANCZOS).save(os.path.join(gui, "foreground.png"))
    Image.open(os.path.join(source, "gui", "LuckyBlock", "minecraft", "unknown.png")).convert("RGBA").save(
        os.path.join(gui, "unknown.png"))
    Image.open(os.path.join(source, "gui", "LuckyPass", "minecraft", "empty.png")).convert("RGBA").save(
        os.path.join(gui, "empty.png"))
    folder = os.path.join(source, "gui", "LuckyBlock", "events")
    # Les images des evenements du mois sont rangees par mois (march/asteroid_rain).
    available = {}
    for root, _dirs, names in os.walk(folder):
        for name in names:
            if name.endswith(".png"):
                relative = os.path.relpath(os.path.join(root, name), folder).replace(os.sep, "/")
                available.setdefault(relative.lower(), relative)
    wanted = {icon for _kind, _ident, _label, icon, _bad, _rarity in events}
    wanted |= {icon for _fr, _en, icon in EFFECTS.values()}
    drawn = {}
    for icon in sorted(wanted):
        target = os.path.join(icons, icon + ".png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
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
    for name, (path, texture, _handheld, _fr, _en) in list(ITEMS.items()) + list(GADGET_ITEMS.items()):
        if name in DRAWN_ITEMS:
            pixels(*DRAWN_ITEMS[name]).save(os.path.join(items, texture + ".png"))
        elif path is not None:
            image = Image.open(os.path.join(source, path)).convert("RGBA")
            if image.width <= 16 and image.height <= 16 and image.size != (16, 16):
                # Quelques icones de PalaMod font 15 x 15 : on les centre sur 16 x 16.
                padded = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
                padded.paste(image, ((16 - image.width) // 2, (16 - image.height) // 2))
                image = padded
            animated = os.path.exists(os.path.join(source, path) + ".mcmeta") and image.height % image.width == 0
            if (image.width != image.height and not animated) or image.width % 16:
                raise SystemExit("icone d'objet de taille inattendue : %s %s" % (path, image.size))
            if not animated and image.size != (16, 16):
                # Quelques icones de PalaMod sont dessinees en 32 : ramenees a 16, comme les autres.
                image = image.resize((16, 16), Image.BOX)
            image.save(os.path.join(items, texture + ".png"))
            if animated:
                # Une bande d'images animee (le cuir et le colorant arc-en-ciel) : son .mcmeta suit.
                shutil.copyfile(os.path.join(source, path) + ".mcmeta", os.path.join(items, texture + ".png.mcmeta"))
    for material, layers in EQUIPMENT.items():
        for layer, (texture, path) in layers.items():
            if path is None:
                continue
            image = Image.open(os.path.join(source, path)).convert("RGBA")
            if image.size != (64, 32):
                raise SystemExit("couche portee de taille inattendue : %s %s" % (path, image.size))
            if material == "party_mask":
                continue
            if material == "air_min":
                mark, cloth = AIR_MIN_MARK
                for y in range(image.height):
                    for x in range(image.width):
                        if image.getpixel((x, y)) == mark:
                            image.putpixel((x, y), cloth)
            folder = os.path.join(ASSETS, "textures", "entity", "equipment", layer)
            os.makedirs(folder, exist_ok=True)
            image.save(os.path.join(folder, texture + ".png"))


def gadget_textures(jar):
    """Les textures de bloc du lot 5, la lampe en seize couleurs, les tableaux, le masque porte."""
    from PIL import Image

    source = os.path.join(jar, "assets", "palamod", "textures")
    folder = os.path.join(ASSETS, "textures", "block", "lucky")
    os.makedirs(folder, exist_ok=True)
    for name, (_kind, refs, _loot, _fr, _en) in GADGET_BLOCKS.items():
        for ref in refs.values():
            if not isinstance(ref, str):
                continue
            if ref.startswith("jar:"):
                image = Image.open(os.path.join(source, ref[4:])).convert("RGBA")
                if image.width != image.height:
                    image = image.crop((0, 0, min(image.size), min(image.size)))
                image.save(os.path.join(folder, os.path.basename(ref[4:])))
            elif ref.startswith("draw:"):
                pixels(*DRAWN_BLOCKS[ref[5:]]).save(os.path.join(folder, ref[5:] + ".png"))
    # La lampe coloree : la texture grise de PalaMod, teinte ({@code getRenderColor}), eteinte plus sombre.
    lamp = Image.open(os.path.join(source, "blocks", "colored_lamp.png")).convert("RGBA")
    for index, tint in enumerate(LAMP_TINTS):
        for lit in (False, True):
            factor = 1.0 if lit else 0.45
            image = Image.new("RGBA", lamp.size)
            for y in range(lamp.height):
                for x in range(lamp.width):
                    r, g, b, a = lamp.getpixel((x, y))
                    image.putpixel((x, y), (int(r * ((tint >> 16) & 255) / 255 * factor),
                                            int(g * ((tint >> 8) & 255) / 255 * factor),
                                            int(b * (tint & 255) / 255 * factor), a))
            image.save(os.path.join(folder, "lamp_%d_%s.png" % (index, "on" if lit else "off")))
    # Les tableaux de PalaMod, decoupes dans leur planche.
    sheet = Image.open(os.path.join(source, "painting", "lucky_paintings.png")).convert("RGBA")
    paintings = os.path.join(ASSETS, "textures", "painting")
    os.makedirs(paintings, exist_ok=True)
    for name, width, height, u, v in PAINTINGS:
        sheet.crop((u, v, u + width, v + height)).save(os.path.join(paintings, "lucky_%s.png" % name))
    overlay = os.path.join(ASSETS, "textures", "gui", "lucky", "overlay")
    os.makedirs(overlay, exist_ok=True)
    for name, path in SEASON_OVERLAYS.items():
        Image.open(os.path.join(source, path)).convert("RGBA").save(os.path.join(overlay, name + ".png"))
    # Le masque de bal porte : un loup violet borde d'or sur les yeux (calque du chapeau).
    layer = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    gold, purple, dark = (232, 186, 46, 255), (120, 50, 170, 255), (60, 20, 90, 255)
    for x in range(40, 48):
        for y in (10, 13):
            layer.putpixel((x, y), gold)
        for y in (11, 12):
            layer.putpixel((x, y), dark if x in (41, 42, 45, 46) else purple)
    for x in range(32, 40):
        for y in range(11, 13):
            layer.putpixel((x + 16, y), purple)
            layer.putpixel((x + 24 if x + 24 < 64 else x, y), purple)
    target = os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid")
    os.makedirs(target, exist_ok=True)
    layer.save(os.path.join(target, "party_mask.png"))


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
    image = Image.new("RGBA", (4, 4))
    for y, row in enumerate(MISSING_TEX):
        for x, char in enumerate(row):
            image.putpixel((x, y), MISSING_COLORS[char])
    image.resize((16, 16), Image.NEAREST).save(os.path.join(folder, "missing.png"))
    # « Hackerman » : des colonnes de chiffres verts sur fond transparent (64 x 64, tirage fixe).
    import random as _random
    rng = _random.Random(1337)
    matrix = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for column in range(0, 64, 4):
        if rng.random() < 0.35:
            continue
        top = rng.randrange(64)
        for row in range(0, rng.randrange(16, 56), 5):
            glyph = rng.choice(((1, 1, 1, 1), (0, 1, 1, 0)))
            alpha = max(60, 230 - row * 3)
            for dy in range(4):
                for dx in range(3):
                    if glyph[dy] and (dx != 1 or dy in (0, 3)):
                        matrix.putpixel((column + dx, (top + row + dy) % 64), (40, 230, 90, alpha))
    matrix.save(os.path.join(folder, "matrix.png"))
    patch = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(8):
            patch.putpixel((x, y), (8, 8, 8, 255))
    patch.save(os.path.join(folder, "eye_patch.png"))


def stats(events):
    catalog = json.load(open(CATALOG, encoding="utf-8"))
    ids = {ident for _kind, ident, _label, _icon, _bad, _rarity in events}
    print("%d evenements portes sur %d" % (len(ids), len(catalog)))
    for key in [key.upper() for key in TYPES]:
        pool = [event for event in catalog if key in event["types"]]
        good = sum(1.0 / event["rarity"] for event in pool if not event["bad"])
        bad = sum(1.0 / event["rarity"] for event in pool if event["bad"])
        share = 1.25 * good / (1.25 * good + bad)
        mine = [event for event in pool if event["id"] in ids]
        my_good = sum(1.0 / event["rarity"] for event in mine if not event["bad"])
        my_bad = sum(1.0 / event["rarity"] for event in mine if event["bad"])
        print("%-9s %3d/%3d portes  bons %.4f (Lucky Pass compris)  masse couverte : bons %5.1f %%, malus %5.1f %%"
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
        if not re.fullmatch(r"[a-z0-9_.\-]+(/[a-z0-9_.\-]+)?", icon):
            stale.append("image au nom invalide : %s (%s)" % (ident, icon))
        known = catalog.get(ident)
        if known is None:
            stale.append("evenement inconnu de PalaMod : %s" % ident)
        elif known["rarity"] != rarity or known["bad"] != bad:
            stale.append("%s : rarete ou malus different de PalaMod (%s, %s)" % (ident, known["rarity"], known["bad"]))
        elif kind in ("classic", "findium") and (kind == "findium") != ("FINDIUM" in known["types"]):
            stale.append("%s : pas le bon lucky block" % ident)
        elif kind not in ("classic", "findium") and known["types"] != [kind.upper()]:
            stale.append("%s : pas le bon lucky block (%s)" % (ident, ", ".join(known["types"])))
    # Les objets nommes dans le code des evenements (SeasonKit.stack("palamod:...", n)) : chaque nom
    # doit exister, sans quoi l'objet donne serait de l'air.
    for name in os.listdir(JAVA):
        if not name.endswith(".java"):
            continue
        text = open(os.path.join(JAVA, name), encoding="utf-8").read()
        for namespace, item in re.findall(r'stack\("([a-z_]+):([a-z0-9_/]+)"', text):
            if namespace != "minecraft" and not os.path.exists(os.path.join(
                    ROOT, "src", "main", "resources", "assets", namespace, "items", item + ".json")):
                stale.append("%s : objet inconnu %s:%s" % (name, namespace, item))
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
        gadget_textures(jar)
        overlays()
    needed = ["textures/block/%s_lucky_block.png" % key for key in TYPES]
    needed += ["textures/gui/lucky/%s.png" % name for name in ("foreground", "unknown", "empty")]
    needed += ["textures/gui/lucky/events/%s.png" % icon for _k, _i, _l, icon, _b, _r in events]
    needed += ["textures/mob_effect/%s.png" % effect for effect in EFFECTS]
    needed += ["textures/gui/lucky/overlay/%s.png" % name
               for name in list(FILTERS) + ["cat", "missing", "matrix", "eye_patch"] + list(SEASON_OVERLAYS)]
    needed += ["textures/item/%s.png" % texture for _s, texture, _h, _f, _e in GADGET_ITEMS.values()]
    needed += ["textures/painting/lucky_%s.png" % name for name, _w, _h, _u, _v in PAINTINGS]
    needed += ["textures/block/lucky/lamp_%d_%s.png" % (index, state) for index in range(16) for state in ("on", "off")]
    for _kind, refs, _loot, _fr, _en in GADGET_BLOCKS.values():
        for ref in refs.values():
            if isinstance(ref, str) and ref.startswith(("jar:", "draw:")):
                needed.append("textures/block/lucky/%s.png" % (os.path.basename(ref[4:])[:-4] if ref.startswith("jar:")
                                                               else ref[5:]))
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
