#!/usr/bin/env python3
"""L'agriculture ne doit pas offrir d'argent gratuit.

Les objets de l'agriculture se vendent et s'achetent a un cours qui change
chaque heure (`data/economy.json`, bloc `course`). Deux objets tirent leur cours
separement : a une heure donnee, un ingredient peut etre au plus bas pendant que
son produit est au plus haut. Si une recette devenait alors rentable — acheter
les ingredients au shop, les passer a la machine, revendre le produit — ce serait
une pompe a argent, et elle tournerait toute seule a chaque bonne heure.

Le script verifie, sur le catalogue genere et les recettes de
`data/agriculture.json` :

- qu'aucun objet ne se revend plus cher qu'il ne s'achete, meme achete au cours
  le plus bas et revendu au plus haut ;
- qu'aucune recette (moulin, fournee de pain, bete nourrie) ne rapporte plus que
  ses ingredients ne coutent, dans le meme pire cas. Une recette dont un
  ingredient ne s'achete pas (les OGM) est sure d'office : on ne peut pas
  l'alimenter au shop ;
- que chaque fournee nomme un pain qui existe (`agri/Bread.java`) et que chaque
  objet `dohy:` qu'elle consomme est un objet du mod ;
- que le pool de la recherche a assez d'ingredients, sans doublon, et que les
  devinettes sont sans accents comme tout ce que le serveur envoie.

L'energie des machines n'est pas comptee : elle ne fait qu'ajouter au cout.
Un ingredient `minecraft:` absent du catalogue est compte a cout nul, le cas le
plus severe (un prix par tag pourrait le rendre achetable).
"""

import json
import pathlib
import re
import sys

AGRI = pathlib.Path("data/agriculture.json")
ECONOMY = pathlib.Path("data/economy.json")
PRICES = pathlib.Path("data/generated/prices.json")
BREADS = pathlib.Path("src/main/java/fr/atraxterra/dohy/agri/Bread.java")
ITEMS = pathlib.Path("src/main/resources/assets/dohy/items")
CEREALS = ["corn", "barley", "oat", "rye", "sunflower", "soy", "fonio", "sorghum", "kamut"]
PRODUCTS = ["dohy:cream", "dohy:lard", "minecraft:egg"]


def main():
    agri = json.loads(AGRI.read_text(encoding="utf-8"))
    course = json.loads(ECONOMY.read_text(encoding="utf-8")).get("course", {})
    prices = json.loads(PRICES.read_text(encoding="utf-8"))["items"]
    errors = []

    low, high = float(course.get("min", 1.0)), float(course.get("max", 1.0))
    followed = {category.upper() for category in course.get("categories", [])}
    if not 0 < low <= high:
        errors.append("cours : il faut 0 < min <= max (min %s, max %s)" % (low, high))

    def follows(item):
        return item in prices and prices[item]["category"].upper() in followed

    def best_sale(item):
        """Ce que rapporte au mieux la vente d'un exemplaire ; 0 s'il ne se vend pas."""
        entry = prices.get(item)
        if entry is None or not entry.get("sellable", True):
            return 0.0
        return entry["base_price"] * (high if follows(item) else 1.0)

    def cheapest_buy(item):
        """Ce que coute au moins l'achat d'un exemplaire ; None s'il ne s'achete pas."""
        entry = prices.get(item)
        if entry is None:
            return None if item.startswith("dohy:") else 0.0
        if entry.get("buy_price", 0) <= 0:
            return None
        return entry["buy_price"] * (low if follows(item) else 1.0)

    # 1. Aucun objet ne se revend plus cher qu'il ne s'achete.
    for item, entry in sorted(prices.items()):
        if not follows(item):
            continue
        cost = cheapest_buy(item)
        if cost is not None and best_sale(item) >= cost:
            errors.append("%s : revendu au mieux %.0f, achete au pire %.0f" % (item, best_sale(item), cost))

    # 2. Aucune recette n'est rentable en achetant ses ingredients.
    def recipe(name, inputs, outputs):
        cost = 0.0
        for item, count in inputs.items():
            unit = cheapest_buy(item)
            if unit is None:
                return
            cost += unit * count
        revenue = sum(best_sale(item) * count for item, count in outputs.items())
        if revenue > cost:
            errors.append("%s : rapporte au mieux %.0f pour %.0f d'ingredients achetes" % (name, revenue, cost))

    machines = agri["machines"]
    for key in ["wheat"] + CEREALS:
        grain = "minecraft:wheat" if key == "wheat" else "dohy:" + key
        recipe("moulin %s" % key, {grain: machines["mill_ratio"]}, {"dohy:%s_flour" % key: 1})

    known = set(re.findall(r'\("([a-z_]+_bread)"', BREADS.read_text(encoding="utf-8")))
    seen = set()
    for batch in agri["breads"]:
        name = batch["id"]
        if name not in known:
            errors.append("fournee %s : ce pain n'existe pas dans Bread.java" % name)
        if name in seen:
            errors.append("fournee %s : declaree deux fois" % name)
        seen.add(name)
        for item in batch["inputs"]:
            if item.startswith("dohy:") and not (ITEMS / (item.split(":")[1] + ".json")).exists():
                errors.append("fournee %s : %s n'est pas un objet du mod" % (name, item))
        # Le pain en plus du Fermier confirme compte dans le pire cas.
        count = batch.get("count", 1) + machines.get("bonus_bread_chance", 0.0)
        recipe("fournee %s" % name, batch["inputs"], {"dohy:" + name: count})
    for name in sorted(known - seen):
        errors.append("pain %s : aucune fournee ne le produit" % name)

    for cereal in ["minecraft:wheat"] + ["dohy:" + key for key in CEREALS]:
        for product in PRODUCTS:
            recipe("elevage %s -> %s" % (cereal, product), {cereal: agri["feed_ration"]}, {product: 1})

    # 3. La recherche.
    gmo = agri["gmo"]
    pool = [entry["item"] for entry in gmo["pool"]]
    if len(pool) < gmo["ingredients"]:
        errors.append("recherche : %d ingredients demandes, %d dans le pool" % (gmo["ingredients"], len(pool)))
    for item in sorted({item for item in pool if pool.count(item) > 1}):
        errors.append("recherche : %s est deux fois dans le pool" % item)
    clues = [entry["clue"] for entry in gmo["pool"]]
    for clue in sorted({clue for clue in clues if clues.count(clue) > 1}):
        errors.append("recherche : deux ingredients ont la devinette « %s »" % clue)
    for clue in clues:
        if any(ord(char) > 127 for char in clue):
            errors.append("recherche : devinette avec accent ou caractere special : %s" % clue)
    if gmo["range_width"] > gmo["max_quantity"]:
        errors.append("recherche : la fourchette est plus large que la quantite maximale")

    if errors:
        print("Agriculture : %d probleme(s)" % len(errors))
        for error in errors:
            print("  - " + error)
        return 1
    print("Agriculture : %d recettes sans arbitrage au cours [%.2f ; %.2f], %d ingredients de recherche."
          % (len(["wheat"] + CEREALS) + len(agri["breads"]) + (len(CEREALS) + 1) * len(PRODUCTS), low, high,
             len(pool)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
