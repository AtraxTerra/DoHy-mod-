#!/usr/bin/env python3
"""Le marche : ce que les vendeurs fictifs peuvent mettre en vente.

    python3 -I tools/market_offers.py            ecrit data/auction.json et affiche la reserve
    python3 -I tools/market_offers.py --check    verifie que data/auction.json est a jour

Le Market de Paladium est un hotel des ventes entre joueurs. En solo, il n'y a
personne en face : des vendeurs fictifs y deposent chaque jour une douzaine
d'annonces, tirees de la reserve ci-dessous. Chaque annonce a un lot (combien
d'objets), un stock (combien de lots) et un prix, qui tourne autour de la
**cote** de l'objet.

La cote est la valeur boutique de tools/crate_values.py : le prix d'achat au
shop, sinon le cout des ingredients, sinon une cote fixee a la main (les
pierres legendaires, par exemple). Ce qui n'a de cote nulle part en recoit une
ici (QUOTES), avec sa raison. Le marche ne rachete rien : il n'y a pas de
boucle d'argent a surveiller, seulement des prix a tenir au-dessus de ce que
rapporte la revente (le quart de la cote).

Les prix eux-memes ne sont pas ecrits ici : le jeu les tire chaque jour
(AuctionHouse). Ce fichier fixe la reserve, les cotes et les bornes.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import crate_values  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "data", "auction.json")
NS = "palamod"

# Ce qui n'a pas de valeur boutique. La regle : ce que l'objet remplace ou ce
# qu'il faut pour le fabriquer, arrondi.
QUOTES = {
    # Runes (PALAMOD § 24) : une rune commune se fait de 4 fragments communs et
    # d'un lingot de paladium, une rare de 4 fragments rares et d'un bloc. Les
    # fragments ne tombent qu'en donjon, a partir des paliers 10 et 25.
    "rune_fragment_common": 40_000,
    "rune_fragment_rare": 150_000,
    "rune_common": 170_000,
    "rune_rare": 620_000,
    # L'epique ne se fabrique pas : quatre runes rares.
    "rune_epic": 2_500_000,
    # Essences (PALAMOD § 23) : elles reparent l'equipement de donjon de leur
    # tier, un quart de durabilite par essence, comme un lingot du metal.
    "essence_fragment": 20_000,
    "essence": 80_000,
    "essence_concentrate": 300_000,
    # Equipement de donjon : une recompense unique de palier (5 a 50). Bien en
    # dessous de l'endium (5,8 a 11,5 M la piece), dont le renforce a les
    # valeurs hors donjon du paladium vert.
    "dungeon_basic": 400_000,
    "dungeon_reinforced": 2_000_000,
    # L'amelioration de capacite de spawner : un crane de wither squelette, un
    # modificateur de grinder, des pepites d'endium et un spawner vide.
    "upgrade_more": 450_000,
    # Le coffre a poser d'une box : son prix a la boutique. Les cles prennent
    # le prix de leur box dans data/crates.json (KEY_OFFERS).
    "box": 25_000,
}

# Les cles en promotion : la box, la taille du lot, le stock, le poids. Plus la
# box est chere, plus sa cle est rare au marche.
KEY_OFFERS = (
    ("amethyst", (1, 3), (1, 3), 12),
    ("saharia", (1, 2), (1, 2), 8),
    ("paladium", (1, 1), (1, 1), 5),
    ("halloween", (1, 1), (1, 1), 3),
    ("trixium", (1, 1), (1, 1), 2),
    ("endium", (1, 1), (1, 1), 1),
)

DUNGEONS = ("talikus", "nimbria", "vitalys", "manelios")
ELEMENTS = ("forest", "cavern", "flame", "aquatic")
PIECES = ("sword", "helmet", "chestplate", "leggings", "boots")
STONES = ("teleportation", "invisibility", "fortune", "power", "jobs", "chaos")
BOXES = ("amethyst", "paladium", "endium", "saharia", "trixium", "halloween")

# Les rayons du marche, dans l'ordre de l'ecran : combien d'annonces par jour.
CATEGORIES = [
    {"id": "relics", "title": "RELIQUES", "icon": "upgrade", "count": 3},
    {"id": "dungeon", "title": "DONJONS", "icon": "armor", "count": 4},
    {"id": "hunt", "title": "CHASSE ET WITHER", "icon": "melt", "count": 2},
    {"id": "crates", "title": "CLES ET BOXES", "icon": "resources", "count": 3},
]

# Les pseudos des vendeurs : inventes, a la maniere d'un serveur.
SELLERS = [
    "Krolm_", "Zeph1r", "MamieCactus", "LeGrosBill", "Paladinou", "Endiumax", "Trixie_", "BobLeMineur",
    "CapitaineFer", "Nyxo", "Farmeuse38", "DrPioche", "Sablotin", "OursBleu", "Lun4tik", "Grimoire_",
    "PatateDouce", "Volt4ge", "Mirabelle", "TontonCreeper", "Azurine", "SireLapin", "Kaktus", "Ombrelune",
]

# Les cles et les boxes ne se vendent jamais plus cher qu'a la boutique : une
# annonce y est une promotion, ou rien.
PROMO = [0.75, 1.0]


def offer(category, item, quote, lot, stock, weight, factor=None):
    entry = {"category": category, "item": "%s:%s" % (NS, item), "quote": quote,
             "lot": list(lot), "stock": list(stock), "weight": weight}
    if factor:
        entry["factor"] = list(factor)
    return entry


def pool():
    q = QUOTES
    offers = []
    # -- Reliques : ce qui ne s'achete nulle part ailleurs.
    # Les pierres sont le gros lot de la caisse rare : une annonce sur huit du
    # rayon, soit une pierre en vente deux jours sur cinq.
    offers.append(offer("relics", "legendary_stone_random", None, (1, 1), (1, 1), 3))
    for stone in STONES:
        offers.append(offer("relics", "legendary_stone_" + stone, None, (1, 1), (1, 1), 1))
    offers.append(offer("relics", "endium_gauntlet", None, (1, 1), (1, 1), 10))
    offers.append(offer("relics", "pocket_enderchest", None, (1, 1), (1, 1), 10))
    offers.append(offer("relics", "cloud", None, (1, 1), (1, 2), 12))
    offers.append(offer("relics", "hunter_amulet", None, (1, 1), (1, 2), 12))
    offers.append(offer("relics", "endium_nugget", None, (1, 4), (1, 2), 10))
    offers.append(offer("relics", "endium_ingot", None, (1, 1), (1, 1), 6))
    # -- Donjons : runes, essences, equipement des deux premiers tiers.
    offers.append(offer("dungeon", "rune_fragment_common", q["rune_fragment_common"], (2, 6), (1, 3), 12))
    offers.append(offer("dungeon", "rune_fragment_rare", q["rune_fragment_rare"], (1, 4), (1, 2), 9))
    offers.append(offer("dungeon", "rune_common", q["rune_common"], (1, 1), (1, 2), 8))
    offers.append(offer("dungeon", "rune_rare", q["rune_rare"], (1, 1), (1, 1), 5))
    offers.append(offer("dungeon", "rune_epic", q["rune_epic"], (1, 1), (1, 1), 2))
    for element in ELEMENTS:
        offers.append(offer("dungeon", element + "_essence_fragment", q["essence_fragment"], (2, 8), (1, 2), 3))
        offers.append(offer("dungeon", element + "_essence", q["essence"], (1, 4), (1, 2), 3))
        offers.append(offer("dungeon", element + "_essence_concentrate", q["essence_concentrate"], (1, 2), (1, 1), 2))
    for dungeon in DUNGEONS:
        for piece in PIECES:
            offers.append(offer("dungeon", "%s_%s" % (dungeon, piece), q["dungeon_basic"], (1, 1), (1, 1), 1))
            offers.append(offer("dungeon", "%s_reinforced_%s" % (dungeon, piece), q["dungeon_reinforced"],
                                (1, 1), (1, 1), 1))
    # -- Chasse et wither : ameliorations de spawner, structures, gemmes.
    for upgrade in ("speed", "looting", "slime"):
        offers.append(offer("hunt", "upgrade_" + upgrade, None, (1, 1), (1, 2), 5))
    offers.append(offer("hunt", "upgrade_more", q["upgrade_more"], (1, 1), (1, 1), 3))
    offers.append(offer("hunt", "spawner_structure_three", None, (4, 12), (1, 2), 4))
    offers.append(offer("hunt", "spawner_structure_four", None, (2, 6), (1, 1), 2))
    offers.append(offer("hunt", "capture_stone", None, (1, 3), (1, 3), 6))
    offers.append(offer("hunt", "passive_wither_gem", None, (1, 1), (1, 2), 4))
    offers.append(offer("hunt", "predator_wither_gem", None, (1, 1), (1, 1), 2))
    offers.append(offer("hunt", "supreme_wither_gem", None, (1, 1), (1, 1), 1))
    # -- Cles et boxes : des promotions.
    with open(os.path.join(ROOT, "data", "crates.json")) as handle:
        crates = json.load(handle)["crates"]
    for box, lot, stock, weight in KEY_OFFERS:
        offers.append(offer("crates", "crate_key_" + box, crates[box]["price"], lot, stock, weight, PROMO))
    for box in BOXES:
        offers.append(offer("crates", "box_" + box, q["box"], (1, 1), (1, 1), 2, PROMO))
    return offers


def build():
    values = crate_values.build_values()
    if isinstance(values, tuple):
        values = values[0]
    with open(os.path.join(ROOT, "src/main/resources/assets/palamod/lang/fr_fr.json"), encoding="utf-8") as handle:
        lang = json.load(handle)
    offers = pool()
    problems = []
    for entry in offers:
        path = entry["item"].split(":", 1)[1]
        name = lang.get("item.%s.%s" % (NS, path)) or lang.get("block.%s.%s" % (NS, path))
        if name is None:
            problems.append("%s : objet inconnu de PalaMod" % entry["item"])
        if entry["quote"] is None:
            if entry["item"] not in values:
                problems.append("%s : ni valeur boutique ni cote" % entry["item"])
                continue
            entry["quote"] = int(round(values[entry["item"]]))
        entry["_name"] = name or path
    if problems:
        raise SystemExit("\n".join(problems))
    known = {category["id"] for category in CATEGORIES}
    for category in CATEGORIES:
        available = sum(1 for entry in offers if entry["category"] == category["id"])
        if available < category["count"]:
            raise SystemExit("%s : %d annonces par jour pour %d offres" % (category["id"], category["count"], available))
    for entry in offers:
        if entry["category"] not in known:
            raise SystemExit("%s : rayon inconnu %s" % (entry["item"], entry["category"]))
    return {
        "_comment": "GENERE par tools/market_offers.py : la reserve du marche. quote : la cote d'un objet ; "
                    "lot : combien d'objets par annonce ; stock : combien de lots ; weight : sa chance d'etre "
                    "tire dans son rayon ; factor : bornes du prix en part de la cote, sinon celles de prices.",
        "per_reroll": 250_000,
        "max_rerolls": 4,
        # Le prix d'une annonce, en part de la cote : une bonne affaire, le
        # prix courant, ou un vendeur gourmand. chance en pour cent.
        "prices": [
            {"label": "deal", "chance": 15, "factor": [0.70, 0.88]},
            {"label": "fair", "chance": 65, "factor": [0.98, 1.25]},
            {"label": "greedy", "chance": 20, "factor": [1.30, 1.60]},
        ],
        "sellers": SELLERS,
        "categories": CATEGORIES,
        "offers": [{key: value for key, value in entry.items() if not key.startswith("_")} for entry in offers],
    }, offers


def render(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main(argv):
    data, offers = build()
    text = render(data)
    if "--check" in argv:
        current = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if current != text:
            raise SystemExit("data/auction.json n'est pas a jour : relancer tools/market_offers.py")
        print("data/auction.json est a jour (%d offres)" % len(offers))
        return
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(text)
    for category in data["categories"]:
        mine = [entry for entry in offers if entry["category"] == category["id"]]
        total = sum(entry["weight"] for entry in mine)
        print("%s — %d annonces par jour, %d offres" % (category["title"], category["count"], len(mine)))
        for entry in sorted(mine, key=lambda e: -e["weight"]):
            lot = entry["lot"]
            print("  %5.1f %%  %-42s cote %13s  lot %s" % (
                100.0 * entry["weight"] / total, entry["_name"][:42], format(entry["quote"], ",").replace(",", " "),
                "%d" % lot[0] if lot[0] == lot[1] else "%d a %d" % tuple(lot)))


if __name__ == "__main__":
    main(sys.argv[1:])
