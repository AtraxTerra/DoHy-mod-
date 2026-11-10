#!/usr/bin/env python3
"""Verifie la chaine d'assets du namespace palamod.

Pourquoi ce script existe : **aucune** des fautes qu'il cherche ne fait echouer
la compilation. Un modele qui pointe sur une texture absente donne du violet,
un blockstate qui nomme un modele inexistant donne un bloc invisible, un item
sans definition donne une icone violette, une texture 15x15 part dans l'atlas
sans un mot. Toutes se voient en jeu, des semaines plus tard, sur un objet
qu'il faut d'abord pouvoir obtenir.

Il remonte la chaine comme le jeu la remonte :

    enregistrement Java
      -> blockstate -> modele de bloc -> texture
      -> definition d'item -> modele d'item -> texture
      -> table de butin

    python3 tools/check_assets.py

Code retour 1 et une ligne ::error:: par probleme, lisible par GitHub Actions.
"""
import json
import pathlib
import re
import struct
import sys

NS = "palamod"
RES = pathlib.Path("src/main/resources")
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS
JAVA = pathlib.Path("src/main/java/fr/atraxterra/dohy/palamod")

EXPECTED_PNG = (16, 16)

# Les fabriques de PalaItems, PalaBlocks et PalaCrops, en plus des methodes de
# NeoForge. Un nom ajoute ici doit l'etre aussi dans BLOCK_FACTORIES s'il
# enregistre un bloc.
ITEM_FACTORIES = ("registerSimpleItem", "registerItem", "simple", "seed", "planter",
                  "sword", "pickaxe", "axe", "shovel", "hoe", "armor", "stick", "ring", "amulet")
BLOCK_FACTORIES = ("registerSimpleBlock", "registerBlock", "ore", "storage", "crop", "soil",
                   # Les objets de defense (chapitre 22) : obsidiennes piegees, a spike, enclumes.
                   "effect", "spikeObsidian", "anvil")

# Les blocs sans item : on les obtient en semant, comme le ble vanilla. Ils
# n'ont donc ni definition d'item ni modele d'item, et c'est normal.
ITEMLESS = ("crop",)

# Blocs sans item enregistres par registerBlock : la void stone se pose depuis
# la Voidstone minage et la rend a la casse, elle n'a pas d'objet propre. L'eau
# angelique est un fluide : son objet est le seau.
ITEMLESS_BLOCKS = ("void_stone", "angelic_water",
                   # Alchimiste : une buche entamee se casse en bois mort, la
                   # surface du portail n'existe que portail ouvert.
                   "tapped_jacaranda_log", "tapped_judeecercis_log", "tapped_erable_log",
                   "tapped_ostrya_log", "alchemist_portal",
                   # Dimension du Mineur : la nappe du portail, comme celle de
                   # l'alchimiste, n'existe que cadre allume.
                   "miner_portal",
                   # La Fake Water (chapitre 22) : son objet est le seau.
                   "fake_water")

errors = []


def fail(message):
    errors.append(message)


def png_size(path):
    head = path.read_bytes()[:24]
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def resolve(reference, kind):
    """« palamod:block/x » -> le chemin du fichier, ou None si c'est du vanilla.

    Les references minecraft: ne sont pas verifiables ici — le jar vanilla n'est
    pas dans le depot — et sont donc ignorees volontairement.
    """
    if ":" not in reference:
        reference = "minecraft:" + reference
    namespace, path = reference.split(":", 1)
    if namespace != NS:
        return None
    if kind == "model":
        return ASSETS / "models" / (path + ".json")
    return ASSETS / "textures" / (path + ".png")


def registered():
    """Les noms enregistres dans le source Java, par famille."""
    # rglob et non glob : les machines vivent dans un sous-paquet, et ne pas
    # descendre y rendait tous leurs assets « morts » a tort.
    source = "\n".join(p.read_text(encoding="utf-8") for p in sorted(JAVA.rglob("*.java")))
    items, blocks, itemless = set(), set(), set()
    for factory in ITEM_FACTORIES:
        items |= set(re.findall(rf'\b{factory}\(\s*"([a-z0-9_]+)"', source))
    for factory in BLOCK_FACTORIES:
        found = set(re.findall(rf'\b{factory}\(\s*"([a-z0-9_]+)"', source))
        blocks |= found
        if factory in ITEMLESS:
            itemless |= found
    # Les coffres sont enregistres par boucle sur ChestTier : leur nom est la
    # premiere chaine de chaque constante de l'enum.
    tiers = JAVA / "storage" / "ChestTier.java"
    if tiers.exists():
        blocks |= set(re.findall(r'^\s+[A-Z_]+\("([a-z0-9_]+)"', tiers.read_text(encoding="utf-8"), re.M))
    # Un bloc n'est pas un item : son BlockItem est enregistre par boucle, pas
    # par un appel nomme, donc il n'apparait pas dans items.
    itemless |= set(ITEMLESS_BLOCKS) & blocks
    return items - blocks, blocks, itemless


def model_references(node):
    """Les modeles qu'une definition d'item peut afficher, toutes branches suivies."""
    if not isinstance(node, dict):
        return []
    if node.get("type", "minecraft:model") in ("minecraft:model", "model") and "model" in node \
            and isinstance(node["model"], str):
        return [node["model"]]
    found = []
    for key in ("on_true", "on_false", "fallback", "model"):
        found += model_references(node.get(key))
    for case in node.get("cases", []) or []:
        found += model_references(case.get("model"))
    for entry in node.get("entries", []) or []:
        found += model_references(entry.get("model"))
    return found


# Les textures qui ne sont pas des faces de cube : un modele a elements qui
# reprend une texture d'entite garde la taille de cette texture. Chaque entree
# est un choix, pas une tolerance — les coffres reprennent la texture 64x64 du
# ModelChest de 1.7.10 et leurs UV sont calcules pour elle.
MODEL_TEXTURES = {
    "block/amethyst_chest.png": (64, 64),
    "block/titane_chest.png": (64, 64),
    "block/paladium_chest.png": (64, 64),
    "block/green_paladium_chest.png": (64, 64),
    "block/endium_chest.png": (64, 64),
    "block/paladium_ender_chest.png": (64, 64),
    "block/gold_chest.png": (64, 64),
    # L'extracteur reprend la texture de son ModelExtractor (Techne, 64 x 32).
    "block/extractor_on.png": (64, 32),
    "block/extractor_off.png": (64, 32),
    # La surface du portail : 32 images de 16 x 16, animees par son .mcmeta.
    "block/alchemist_portal.png": (16, 512),
    # Baies et buisson a XP murs : des bandes animees (.mcmeta), 7 et 8 images.
    "block/xp_bush_ripe.png": (16, 112),
    "item/xp_berry.png": (16, 128),
    "item/compressed_xp_berry.png": (16, 128),
    # L'armure antique : icones de rendus 3D (Palamod Recreated), en 32 x 32.
    "item/antique_helmet.png": (32, 32),
    "item/antique_chestplate.png": (32, 32),
    "item/antique_leggings.png": (32, 32),
    "item/antique_boots.png": (32, 32),
    # La machine a croquettes reprend la texture de son FoodMachineModel.
    "block/food_machine.png": (64, 46),
    # Le gestionnaire d'enclos reprend celle de son BarnControllerModel.
    "block/barn_controller.png": (64, 64),
    # Le portail du Mineur : 32 images de 48 x 48 (blocks/miner_portal.png).
    "block/miner_portal.png": (48, 1536),
    # Le pistolet a liquide reprend la texture de son ModelPistolTank, une par
    # contenu (vide, eau, lave, autre).
    "item/pistol_tank_empty.png": (64, 64),
    "item/pistol_tank_water.png": (64, 64),
    "item/pistol_tank_lava.png": (64, 64),
    "item/pistol_tank_fluid.png": (64, 64),
    # L'eponge compressee n'existe qu'en 32 x 32, dans le pack comme dans le jar.
    "block/compressed_sponge.png": (32, 32),
}


def check_textures():
    for folder in ("item", "block"):
        directory = ASSETS / "textures" / folder
        if not directory.is_dir():
            continue
        for png in sorted(directory.glob("*.png")):
            size = png_size(png)
            if size is None:
                fail(f"{folder}/{png.name} : ce n'est pas un PNG")
            elif size != MODEL_TEXTURES.get(f"{folder}/{png.name}", EXPECTED_PNG):
                fail(f"{folder}/{png.name} : {size[0]}x{size[1]} au lieu de "
                     f"{EXPECTED_PNG[0]}x{EXPECTED_PNG[1]} — homonyme pris dans le jar ? "
                     "verifier le sous-dossier")


def check_models():
    """Chaque modele doit pointer sur des textures qui existent."""
    for model in sorted((ASSETS / "models").rglob("*.json")):
        try:
            payload = json.loads(model.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            fail(f"models/{model.name} : JSON illisible ({error})")
            continue
        relative = model.relative_to(ASSETS / "models")
        parent = payload.get("parent")
        if parent:
            target = resolve(parent, "model")
            if target is not None and not target.exists():
                fail(f"models/{relative} : parent absent ({parent})")
        for slot, reference in (payload.get("textures") or {}).items():
            if not isinstance(reference, str) or reference.startswith("#"):
                continue
            target = resolve(reference, "texture")
            if target is not None and not target.exists():
                fail(f"models/{relative} : texture « {slot} » absente ({reference}) "
                     "— ce sera du violet en jeu")


def check_blockstates(blocks):
    for name in sorted(blocks):
        path = ASSETS / "blockstates" / f"{name}.json"
        if not path.exists():
            fail(f"{name} : bloc enregistre sans blockstates/{name}.json — invisible en jeu")
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            fail(f"blockstates/{name}.json : JSON illisible ({error})")
            continue
        for variant, definition in (payload.get("variants") or {}).items():
            entries = definition if isinstance(definition, list) else [definition]
            for entry in entries:
                reference = entry.get("model")
                if not reference:
                    fail(f"blockstates/{name}.json : variante « {variant} » sans modele")
                    continue
                target = resolve(reference, "model")
                if target is not None and not target.exists():
                    fail(f"blockstates/{name}.json : variante « {variant} » pointe sur "
                         f"un modele absent ({reference})")


def check_items(items, blocks, itemless):
    # Tout objet qui existe en inventaire a besoin d'une definition d'item en
    # plus de son modele : c'est la paire que 26.2 exige, et c'est la seconde
    # qui manque quand une icone est violette.
    for name in sorted(items | (blocks - itemless)):
        definition = ASSETS / "items" / f"{name}.json"
        if not definition.exists():
            fail(f"{name} : pas de assets/{NS}/items/{name}.json — icone violette")
            continue
        try:
            payload = json.loads(definition.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            fail(f"items/{name}.json : JSON illisible ({error})")
            continue
        # Une definition peut choisir son modele selon un etat (condition,
        # select) : l'aimant change d'icone quand il est actif. On suit donc
        # toutes les branches, et chacune doit aboutir a un modele qui existe.
        references = model_references(payload.get("model") or {})
        if not references:
            fail(f"items/{name}.json : pas de modele declare")
            continue
        for reference in references:
            target = resolve(reference, "model")
            if target is not None and not target.exists():
                fail(f"items/{name}.json : pointe sur un modele absent ({reference})")

    # Dans l'autre sens : un asset sans enregistrement est un fichier mort.
    known = items | blocks
    for definition in sorted((ASSETS / "items").glob("*.json")):
        if definition.stem not in known:
            fail(f"{definition.stem} : definition d'item sans enregistrement Java "
                 "(fichier mort)")


def check_loot(blocks):
    for name in sorted(blocks):
        if not (DATA / "loot_table" / "blocks" / f"{name}.json").exists():
            fail(f"{name} : bloc sans table de butin, il ne lachera rien")


def check_lang(items, blocks):
    """Une cle de langue manquante affiche l'identifiant brut au joueur."""
    path = ASSETS / "lang" / "fr_fr.json"
    if not path.exists():
        fail("assets/%s/lang/fr_fr.json absent" % NS)
        return
    keys = set(json.loads(path.read_text(encoding="utf-8")))
    for name in sorted(items):
        if f"item.{NS}.{name}" not in keys:
            fail(f"{name} : pas de nom francais (item.{NS}.{name})")
    for name in sorted(blocks):
        if f"block.{NS}.{name}" not in keys:
            fail(f"{name} : pas de nom francais (block.{NS}.{name})")


# ---------------------------------------------------------------------------
# Interfaces : le Java et le generateur doivent decrire la meme disposition.
#
# Les positions des cases vivent a deux endroits, et il n'y a pas moyen de
# faire autrement : le generateur les dessine dans la texture, le Java les
# declare au menu. Un decalage de deux pixels donne une interface ou les objets
# flottent a cote de leur creux, et absolument rien ne le signale — ni la
# compilation, ni le chargement, ni un demarrage reussi. C'est exactement le
# genre de faute que ce script existe pour attraper.
# ---------------------------------------------------------------------------

LAYOUT_JAVA = JAVA / "machine" / "MachineLayout.java"
MENU_JAVA = JAVA / "machine" / "MachineMenu.java"
SCREEN_JAVA = JAVA.parent / "client" / "ui" / "screen" / "MachineKitScreen.java"


def java_ints(path):
    """Les constantes entieres d'un fichier, par nom."""
    text = path.read_text(encoding="utf-8")
    return {
        name: int(value)
        for name, value in re.findall(r"static final int (\w+)\s*=\s*(-?\d+);", text)
    }


def check_gui():
    """
    Les machines (chapitre 21) : l'ecran dessine le cadre de chaque case a la
    position de la case, il n'y a plus de texture a accorder. Restent trois
    fautes silencieuses : un inventaire plus petit que le menu (l'ouverture
    echoue sans un mot, l'erreur etant supprimee par le jeu), une case hors du
    panneau ou sur l'inventaire du joueur, deux cases qui se chevauchent.
    """
    for path in (LAYOUT_JAVA, MENU_JAVA, SCREEN_JAVA):
        if not path.exists():
            fail(f"gui : {path} absent")
            return
    screen = java_ints(SCREEN_JAVA)
    menu = java_ints(MENU_JAVA)
    width, height = screen.get("WIDTH"), screen.get("HEIGHT")
    player_y = menu.get("PLAYER_Y")
    if None in (width, height, player_y):
        fail("gui : WIDTH/HEIGHT de MachineKitScreen ou PLAYER_Y de MachineMenu introuvables")
        return
    if menu.get("HOTBAR_Y", 0) + 18 > height:
        fail(f"gui : la barre d'action ({menu.get('HOTBAR_Y')}) deborde du panneau ({height})")

    text = LAYOUT_JAVA.read_text(encoding="utf-8")
    layouts = re.findall(r"MachineLayout (\w+) = new MachineLayout\(List\.of\((.*?)\)\);", text, re.S)
    if not layouts:
        fail("gui : aucune MachineLayout trouvee — le motif de ce controle ne correspond plus")
        return
    for constant, body in layouts:
        name = constant.lower()
        slots = [(int(x), int(y), flag == "true")
                 for x, y, flag in re.findall(r"new SlotPos\((-?\d+),\s*(-?\d+),\s*(true|false)\)", body)]
        camel = "".join(part.capitalize() for part in name.split("_"))
        found = sorted(JAVA.rglob(camel + "BlockEntity.java")) or sorted(JAVA.rglob(camel + "Container.java"))
        if not found:
            fail(f"gui : {name} n'a ni BlockEntity ni Container — la convention de nom "
                 "a change, ce controle ne verifie plus rien")
        else:
            declared = java_ints(found[0]).get("SLOT_COUNT")
            if declared is not None and declared != len(slots):
                fail(f"gui : {found[0].name} declare SLOT_COUNT={declared} mais l'interface a "
                     f"{len(slots)} cases. L'ouverture echouera en silence")
        for index, (x, y, _) in enumerate(slots):
            if x < 10 or x + 16 > width - 10 or y < 34 or y + 16 > player_y - 12:
                fail(f"gui : {name} case {index} en ({x},{y}) sort de la zone de la machine")
        for first in range(len(slots)):
            for second in range(first + 1, len(slots)):
                (x1, y1, _), (x2, y2, _) = slots[first], slots[second]
                if abs(x1 - x2) < 18 and abs(y1 - y2) < 18:
                    fail(f"gui : {name} cases {first} et {second} se chevauchent")

# ---------------------------------------------------------------------------
# Recettes : aucune ne doit nommer un objet palamod qui n'existe pas.
#
# Une recette qui reference un identifiant inconnu ne casse pas la compilation
# et ne plante pas le chargement : Minecraft la rejette au chargement du
# datapack et passe a la suivante. Le joueur se retrouve avec un objet
# introuvable, et il faut lire le log pour savoir pourquoi. Comme le reste des
# controles de ce script, celui-ci rend visible une faute qui ne se signale pas
# toute seule.
#
# Les identifiants vanilla ne sont pas verifies : il faudrait le jar de
# Minecraft, que la CI de donnees n'a pas. C'est la compilation qui les attrape
# quand ils apparaissent aussi dans le Java, et le chargement sinon.
# ---------------------------------------------------------------------------

RECIPES = DATA / "recipe"


def recipe_ids(payload):
    """Tous les identifiants d'objet d'une recette, ingredients et resultat."""
    found = set()

    def walk(node):
        if isinstance(node, str):
            if node.startswith(NS + ":"):
                found.add(node.split(":", 1)[1])
        elif isinstance(node, list):
            for child in node:
                walk(child)
        elif isinstance(node, dict):
            for key, child in node.items():
                # Les tags (#palamod:...) ne sont pas des objets, ni le type
                # d'une recette speciale (palamod:mini_golem_repair).
                if key == "type" or isinstance(child, str) and child.startswith("#"):
                    continue
                walk(child)

    walk(payload)
    return found


def check_recipes(items, blocks):
    if not RECIPES.is_dir():
        return
    known = set(items) | set(blocks)
    count = 0
    for path in sorted(RECIPES.glob("*.json")):
        count += 1
        payload = json.loads(path.read_text(encoding="utf-8"))
        # Une grille a lignes de largeurs differentes est rejetee au chargement
        # (« each row must be the same width ») — onze recettes perdues ainsi,
        # pioches et charbons mixes, avant que ce controle existe. Et une cle
        # qu'aucune case n'utilise l'est aussi.
        pattern = payload.get("pattern")
        if pattern:
            if len({len(row) for row in pattern}) > 1:
                fail(f"recipe/{path.name} : lignes de largeurs inegales {pattern} — "
                     "26.2 rejettera la recette au chargement")
            used = {char for row in pattern for char in row if char != " "}
            declared = set(payload.get("key", {}))
            if used - declared:
                fail(f"recipe/{path.name} : la grille utilise {sorted(used - declared)} "
                     "sans cle")
            if declared - used:
                fail(f"recipe/{path.name} : cles {sorted(declared - used)} jamais utilisees "
                     "— 26.2 rejettera la recette")
        for name in sorted(recipe_ids(payload)):
            if name not in known:
                fail(f"recipe/{path.name} nomme {NS}:{name}, qui n'est pas "
                     "enregistre : la recette sera rejetee au chargement et "
                     "l'objet restera introuvable")
    if count:
        print(f"recettes : {count} verifiees")


# ---------------------------------------------------------------------------
# Equipement : deux fautes que rien d'autre ne signale.
#
# 1. L'armure invisible. Un ArmorMaterial designe une definition d'equipement
#    par son nom ; la definition designe des couches ; les couches sont des
#    textures. Un maillon manquant donne un joueur qui porte une armure qu'on ne
#    voit pas — l'objet marche, protege, et n'apparait pas.
# 2. L'objet qui refuse les enchantements. En 26.2 ce sont des TAGS d'item qui
#    decident si Tranchant va sur une epee ou Protection sur un casque. Une piece
#    absente de son tag s'enchante a l'enclume avec rien, sans erreur.
# ---------------------------------------------------------------------------

EQUIPMENT = ASSETS / "equipment"
LAYERS = ASSETS / "textures" / "entity" / "equipment"
VANILLA_ITEM_TAGS = RES / "data" / "minecraft" / "tags" / "item"
ARMOR_MATERIALS_JAVA = JAVA / "equipment" / "PalaArmorMaterials.java"
EQUIPMENT_JAVA = JAVA / "equipment" / "PalaEquipment.java"
# L'equipement de donjon (chapitre 19) declare ses materiaux et ses pieces a
# part, avec les memes fabriques : il passe par les memes controles.
DUNGEON_GEAR_JAVA = JAVA / "dungeon" / "PalaDungeonGear.java"

LAYER_SIZE = (64, 32)

# La fabrique Java -> le tag vanilla qui ouvre les enchantements.
TOOL_TAGS = {"sword": "swords", "pickaxe": "pickaxes", "axe": "axes",
             "shovel": "shovels", "hoe": "hoes"}
ARMOR_TAGS = {"HELMET": "head_armor", "CHESTPLATE": "chest_armor",
              "LEGGINGS": "leg_armor", "BOOTS": "foot_armor"}


def _tag_values(name):
    path = VANILLA_ITEM_TAGS / f"{name}.json"
    if not path.exists():
        return set()
    return set(json.loads(path.read_text(encoding="utf-8")).get("values", []))


def check_equipment():
    if not ARMOR_MATERIALS_JAVA.exists() or not EQUIPMENT_JAVA.exists():
        return

    # -- 1. materiau -> definition -> couches -> textures
    material_source = ARMOR_MATERIALS_JAVA.read_text(encoding="utf-8")
    if DUNGEON_GEAR_JAVA.exists():
        material_source += DUNGEON_GEAR_JAVA.read_text(encoding="utf-8")
    materials = set(re.findall(r'material\(\s*"([a-z0-9_]+)"', material_source))
    if not materials:
        fail("equipement : aucun materiau trouve dans PalaArmorMaterials — le motif "
             "de ce controle ne correspond plus au code")
    definitions = {p.stem for p in EQUIPMENT.glob("*.json")} if EQUIPMENT.is_dir() else set()

    for name in sorted(materials - definitions):
        fail(f"equipement : le materiau « {name} » n'a pas de assets/{NS}/equipment/{name}.json "
             "— l'armure sera portee mais invisible")
    for name in sorted(definitions - materials):
        fail(f"equipement : equipment/{name}.json ne correspond a aucun materiau (fichier mort)")

    for name in sorted(definitions & materials):
        payload = json.loads((EQUIPMENT / f"{name}.json").read_text(encoding="utf-8"))
        for layer, entries in payload.get("layers", {}).items():
            for entry in entries:
                texture = entry["texture"].split(":", 1)[1]
                path = LAYERS / layer / f"{texture}.png"
                if not path.exists():
                    fail(f"equipement : {name} demande la couche {layer}/{texture}.png, absente "
                         "— cette partie de l'armure sera invisible")
                    continue
                size = png_size(path)
                if size != LAYER_SIZE:
                    fail(f"equipement : {layer}/{texture}.png fait {size}, une couche portee "
                         f"fait {LAYER_SIZE} — le modele lirait la texture au mauvais endroit")

    # -- 2. chaque piece dans le tag qui ouvre ses enchantements
    source = EQUIPMENT_JAVA.read_text(encoding="utf-8")
    if DUNGEON_GEAR_JAVA.exists():
        source += DUNGEON_GEAR_JAVA.read_text(encoding="utf-8")
    checked = 0
    for factory, tag_name in TOOL_TAGS.items():
        values = _tag_values(tag_name)
        for name in re.findall(rf'\b{factory}\(\s*"([a-z0-9_]+)"', source):
            checked += 1
            if f"{NS}:{name}" not in values:
                fail(f"equipement : {name} n'est pas dans #minecraft:{tag_name} — "
                     "il refusera les enchantements de son type")
    # Les outils a classe propre passent par registerItem, que le motif
    # ci-dessus ne voit pas : la pioche, la hache et l'epee en endium.
    for name, tag_name in (("endium_pickaxe", "pickaxes"), ("endium_axe", "axes"),
                           ("endium_sword", "swords")):
        if f'registerItem("{name}"' in source:
            checked += 1
            if f"{NS}:{name}" not in _tag_values(tag_name):
                fail(f"equipement : {name} n'est pas dans #minecraft:{tag_name} — "
                     "il refusera les enchantements de son type")
    for name, piece in re.findall(r'\barmor\(\s*"([a-z0-9_]+)",[^;]*ArmorType\.(\w+)\)', source):
        checked += 1
        tag_name = ARMOR_TAGS[piece]
        if f"{NS}:{name}" not in _tag_values(tag_name):
            fail(f"equipement : {name} n'est pas dans #minecraft:{tag_name} — "
                 "il refusera Protection et les enchantements d'armure")
    print(f"equipement : {len(materials)} materiaux, {checked} pieces dans leurs tags d'enchantement")


def main():
    items, blocks, itemless = registered()

    if not items and not blocks:
        fail("aucun objet palamod trouve dans le source : les motifs de ce script "
             "ne correspondent plus aux fabriques (voir ITEM_FACTORIES / BLOCK_FACTORIES)")

    check_textures()
    check_models()
    check_blockstates(blocks)
    check_items(items, blocks, itemless)
    check_loot(blocks)
    check_lang(items, blocks)
    check_recipes(items, blocks)
    check_equipment()
    check_gui()

    if errors:
        for message in errors:
            print(f"::error::{message}")
        print(f"\n{len(errors)} probleme(s) dans la chaine d'assets palamod.", file=sys.stderr)
        return 1

    print(f"chaine d'assets palamod verifiee : {len(blocks)} blocs "
          f"(dont {len(itemless)} sans item), {len(items)} items, "
          f"blockstates, modeles, textures, butin, noms francais "
          "et dispositions d'interface coherentes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
