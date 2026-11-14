#!/usr/bin/env python3
"""Le marche noir : ce que le receleur du spawn vend, met en lot et rachete.

    python3 -I tools/black_market.py            ecrit data/black_market.json et affiche la reserve
    python3 -I tools/black_market.py --check    verifie que data/black_market.json est a jour

Le receleur tient boutique sous le pont du spawn, toujours ouvert. Chaque jour
(a la bascule de 5 h des contrats), il a un autre arrivage :

  - de la **marchandise volee** aux nations rivales, moins chere que partout
    ailleurs (entre 50 et 70 % de sa valeur boutique) ; l'acheter, c'est un
    grief de plus pour la nation volee, si l'on a une faction ;
  - de la **contrebande** : ce qui ne se vend nulle part ailleurs (bouteilles
    d'XP de metier, totems, pommes enchantees), plus chere que sa valeur ;
  - un **lot sans etiquette** : un prix fixe, un contenu au hasard ;
  - et il **rachete** ce que le shop refuse (equipement de donjon, runes,
    essences, gemmes), entre 25 et 40 % de sa valeur, quelques pieces par jour.

La valeur d'un objet est sa valeur boutique (tools/crate_values.py), sinon sa
cote au marche (data/auction.json, tools/market_offers.py), sinon une cote
fixee ici (QUOTES), avec sa raison.

Le controle (le script sort en erreur sinon) :
  - aucune boucle d'argent : le receleur vend toujours plus cher (au moins
    FLOOR de la valeur) qu'il ne rachete (au plus CEILING) ;
  - le lot sans etiquette rend en moyenne entre 80 et 92 % de son prix ;
  - chaque objet existe dans le mod ou dans le jeu, et a une valeur.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import crate_values  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "data", "black_market.json")

# Ce qui n'a de valeur ni au shop ni au marche.
QUOTES = {
    # Une bouteille de 10 000 XP de metier : un dixieme de niveau 50 (52 500 XP
    # le niveau), a peu pres un niveau 20 entier ; l'argent de ces niveaux, et
    # le temps de metier qu'elle remplace.
    "job_bottle": 40_000,
}
JOB_XP = 10_000

STOLEN_FACTOR = (0.50, 0.70)
CONTRABAND_FACTOR = (1.30, 1.70)
BUYBACK_FACTOR = (0.25, 0.40)
FLOOR = 0.50
CEILING = 0.40
MYSTERY_LOW, MYSTERY_HIGH = 0.80, 0.92

# objet, lot, stock, poids
STOLEN = [
    ("palamod:paladium_ingot", (32, 64), (1, 2), 10),
    ("palamod:paladium_block", (8, 16), (1, 1), 6),
    ("palamod:green_paladium_ingot", (8, 24), (1, 2), 8),
    ("palamod:findium", (8, 16), (1, 2), 8),
    ("palamod:trixium", (16, 48), (1, 2), 8),
    ("palamod:trixium_ore", (4, 12), (1, 1), 4),
    ("palamod:invoker_stone", (2, 6), (1, 1), 5),
    ("palamod:endium_nugget", (1, 2), (1, 1), 3),
    ("palamod:capture_stone", (1, 3), (1, 1), 4),
    ("palamod:passive_wither_gem", (1, 1), (1, 1), 2),
    ("palamod:upgrade_speed", (1, 1), (1, 1), 2),
    ("palamod:upgrade_looting", (1, 1), (1, 1), 2),
    ("palamod:rune_fragment_common", (1, 3), (1, 1), 4),
    ("palamod:rune_fragment_rare", (1, 1), (1, 1), 2),
    ("palamod:forest_essence", (1, 2), (1, 1), 1),
    ("palamod:cavern_essence", (1, 2), (1, 1), 1),
    ("palamod:flame_essence", (1, 2), (1, 1), 1),
    ("palamod:aquatic_essence", (1, 2), (1, 1), 1),
    ("palamod:crate_key_amethyst", (1, 1), (1, 1), 3),
    ("palamod:crate_key_saharia", (1, 1), (1, 1), 1),
]

# objet, lot, stock, poids, XP de bouteille
CONTRABAND = [
    ("palamod:job_bottle_miner", (1, 2), (1, 3), 3, JOB_XP),
    ("palamod:job_bottle_hunter", (1, 2), (1, 3), 3, JOB_XP),
    ("palamod:job_bottle_farmer", (1, 2), (1, 3), 3, JOB_XP),
    ("palamod:job_bottle_alchemist", (1, 2), (1, 3), 3, JOB_XP),
    ("minecraft:totem_of_undying", (1, 1), (1, 2), 2, 0),
    ("minecraft:enchanted_golden_apple", (1, 1), (1, 1), 1, 0),
]

MYSTERY_PRICE = 135_000
MYSTERY_STOCK = 3
# etiquette, poids, contenu
MYSTERY = [
    ("De la ferraille", 30, [("minecraft:rotten_flesh", 32), ("minecraft:iron_ingot", 8)]),
    ("Un petit lot", 25, [("palamod:paladium_ingot", 32), ("palamod:findium", 8)]),
    ("Un bon lot", 20, [("palamod:trixium", 16), ("palamod:green_paladium_ingot", 16)]),
    ("Un beau lot", 13, [("palamod:crate_key_amethyst", 1), ("palamod:rune_fragment_rare", 1)]),
    ("Un tres beau lot", 8, [("palamod:endium_nugget", 2)]),
    ("Le gros lot", 3, [("palamod:crate_key_saharia", 1), ("palamod:endium_nugget", 1)]),
]

DUNGEONS = ("talikus", "nimbria", "vitalys", "manelios")
PIECES = ("sword", "helmet", "chestplate", "leggings", "boots")
ELEMENTS = ("forest", "cavern", "flame", "aquatic")


def buyback():
    """objet, plafond du jour, poids"""
    out = []
    for dungeon in DUNGEONS:
        for piece in PIECES:
            out.append(("palamod:%s_%s" % (dungeon, piece), (1, 2), 2))
            out.append(("palamod:%s_reinforced_%s" % (dungeon, piece), (1, 1), 1))
    out += [
        ("palamod:rune_common", (1, 3), 6),
        ("palamod:rune_rare", (1, 1), 3),
        ("palamod:rune_epic", (1, 1), 1),
        ("palamod:passive_wither_gem", (1, 2), 3),
        ("palamod:predator_wither_gem", (1, 1), 1),
    ]
    for element in ELEMENTS:
        out.append(("palamod:%s_essence" % element, (2, 4), 2))
        out.append(("palamod:%s_essence_concentrate" % element, (1, 2), 1))
    return out


QUOTES_OF_THE_DAY = [
    "Ce qui tombe du camion ne se rend pas.",
    "Pas de nom, pas de question, pas de facture.",
    "En liquide. La banque, ca laisse des traces.",
    "Si on te demande, tu n'es jamais descendu ici.",
    "Tout part avant 5 h. Demain, autre arrivage.",
    "Je rachete ce que le shop refuse. Sans poser de questions.",
    "Un lot sans etiquette ? Parfois de l'or, parfois du vent.",
    "Les nations comptent leurs caisses. Moi, je compte les miennes.",
]


def known_items():
    items = set()
    for ns in ("dohy", "palamod"):
        folder = os.path.join(ROOT, "src", "main", "resources", "assets", ns, "items")
        for name in os.listdir(folder):
            if name.endswith(".json"):
                items.add("%s:%s" % (ns, name[:-5]))
    return items


def values():
    shop = crate_values.build_values()
    if isinstance(shop, tuple):
        shop = shop[0]
    out = {key: float(value) for key, value in shop.items() if value}
    with open(os.path.join(ROOT, "data", "auction.json"), encoding="utf-8") as handle:
        for offer in json.load(handle)["offers"]:
            out.setdefault(offer["item"], float(offer["quote"]))
    for job in ("miner", "hunter", "farmer", "alchemist"):
        out["palamod:job_bottle_" + job] = float(QUOTES["job_bottle"])
    return out


def build():
    worth = values()
    items = known_items()
    problems = []

    def value_of(item):
        if not item.startswith("minecraft:") and item not in items:
            problems.append("%s : objet inconnu du mod" % item)
        if item not in worth:
            problems.append("%s : aucune valeur (shop, marche ou QUOTES)" % item)
            return 0
        return int(round(worth[item]))

    stolen = [{"item": item, "value": value_of(item), "lot": list(lot), "stock": list(stock), "weight": weight}
              for item, lot, stock, weight in STOLEN]
    contraband = []
    for item, lot, stock, weight, xp in CONTRABAND:
        entry = {"item": item, "value": value_of(item), "lot": list(lot), "stock": list(stock), "weight": weight}
        if xp:
            entry["job_xp"] = xp
        contraband.append(entry)
    demands = [{"item": item, "value": value_of(item), "cap": list(cap), "weight": weight}
               for item, cap, weight in buyback()]
    outcomes = []
    expected = 0.0
    total = sum(weight for _, weight, _ in MYSTERY)
    for label, weight, contents in MYSTERY:
        worth_of = sum(value_of(item) * count for item, count in contents)
        expected += weight / total * worth_of
        outcomes.append({"label": label, "weight": weight,
                         "items": [{"item": item, "count": count} for item, count in contents]})
    if problems:
        raise SystemExit("\n".join(problems))

    # Aucune boucle : ce que le receleur vend coute plus que ce qu'il rachete.
    if STOLEN_FACTOR[0] < FLOOR or BUYBACK_FACTOR[1] > CEILING or CEILING >= FLOOR:
        raise SystemExit("bornes de prix : vente au moins %.0f %%, rachat au plus %.0f %%" % (FLOOR * 100, CEILING * 100))
    ratio = expected / MYSTERY_PRICE
    if not MYSTERY_LOW <= ratio <= MYSTERY_HIGH:
        raise SystemExit("lot sans etiquette : rend %.0f %% de son prix (attendu %.0f a %.0f %%)"
                         % (ratio * 100, MYSTERY_LOW * 100, MYSTERY_HIGH * 100))

    return {
        "_comment": "GENERE par tools/black_market.py : le marche noir du spawn. value : la valeur boutique "
                    "d'un objet ; lot : combien d'objets par offre ; stock : combien de lots par jour ; cap : "
                    "combien de pieces le receleur rachete par jour ; factor : le prix en part de la valeur.",
        "grief": 4.0,
        "stolen": {"count": 4, "factor": list(STOLEN_FACTOR), "offers": stolen},
        "contraband": {"count": 2, "factor": list(CONTRABAND_FACTOR), "offers": contraband},
        "mystery": {"price": MYSTERY_PRICE, "stock": MYSTERY_STOCK, "outcomes": outcomes},
        "buyback": {"count": 3, "factor": list(BUYBACK_FACTOR), "demands": demands},
        "quotes": QUOTES_OF_THE_DAY,
    }, ratio


def render(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main(argv):
    data, ratio = build()
    text = render(data)
    if "--check" in argv:
        current = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if current != text:
            raise SystemExit("data/black_market.json n'est pas a jour : relancer tools/black_market.py")
        print("data/black_market.json est a jour (lot sans etiquette : %.0f %% de son prix)" % (ratio * 100))
        return
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(text)
    for key in ("stolen", "contraband"):
        print(key, len(data[key]["offers"]), "offres,", data[key]["count"], "par jour")
    print("buyback", len(data["buyback"]["demands"]), "demandes,", data["buyback"]["count"], "par jour")
    print("lot sans etiquette : rend %.0f %% de son prix en moyenne" % (ratio * 100))


if __name__ == "__main__":
    main(sys.argv[1:])
