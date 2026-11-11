#!/usr/bin/env python3
"""Chiffre les pieces de machine NTM NEXT du shop a partir des recettes du mod.

Le prix d'une piece est ``premium`` (115 %) fois ce que coutent ses
ingredients, par la recette la moins chere. Une piece ne coute donc jamais
moins au shop que ce qu'il faut pour la fabriquer.

Ce que coute un ingredient, dans l'ordre :

1. un item vanilla : sa colonne ``buy_price`` de ``data/prices.csv``, ou la
   table ``costing.vanilla`` de ``data/tag_prices.json`` ;
2. une forme d'une matiere connue (``c:ingots/lead``, ``hbm:billets/silicon``…) :
   le prix du lingot de son cran, multiplie par le facteur de la forme. Les
   matieres connues sont les dix metaux du shop (``materials``) et les
   matieres de reference (``costing.reference``), qui ne sont pas au shop ;
3. sinon, sa recette la moins chere, recursivement.

Les fluides et l'energie ne sont pas comptes. Les recettes de recyclage, a
sortie aleatoire, ou derriere une option de NTM desactivee par defaut (mode
528, LBSM…) sont ignorees.

Les donnees de NTM sont lues comme des donnees, jamais executees. Lancer avec
``-I`` :

    python3 -I tools/gen_ntm_prices.py <dossier data de NTM NEXT>
    python3 -I tools/gen_ntm_prices.py ../ntm-next/neoforge/src/main/resources/data

Le bloc ``components`` de ``data/tag_prices.json`` est reecrit ; le reste du
fichier est conserve.
"""

import argparse
import csv
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TAG_PRICES = ROOT / "data" / "tag_prices.json"
PRICES_CSV = ROOT / "data" / "prices.csv"

# Les machines dont les recettes fabriquent. Les autres (broyeur, centrifugeuse,
# transmutation, recyclage…) decomposent ou tirent au sort.
RECIPE_TYPES = {
    "minecraft:crafting_shaped", "minecraft:crafting_shapeless",
    "hbm:crafting_shaped", "hbm:crafting_shapeless",
    "minecraft:smelting", "minecraft:blasting",
    "hbm:press", "hbm:assembly_machine", "hbm:precision_assembler",
    "hbm:soldering", "hbm:arc_welder", "hbm:anvil_construction",
}

INGREDIENT_LISTS = ("input_items", "pcb", "solder")


def round_sig(x, digits=2):
    """Comme tools/gen_prices.py : deux chiffres significatifs, minimum 1."""
    if x <= 0:
        return 0
    magnitude = int(math.floor(math.log10(x)))
    factor = 10 ** (magnitude - digits + 1)
    return max(1, int(round(x / factor) * factor))


class Pricer:
    def __init__(self, data_dir, config, vanilla):
        self.config = config
        costing = config["costing"]
        self.premium = costing["premium"]
        self.flags = {k: v for k, v in costing["flags"].items() if not k.startswith("_")}
        self.levels = config["levels"]
        self.aliases = {k: v for k, v in costing["aliases"].items() if not k.startswith("_")}
        self.vanilla = dict(vanilla)
        self.vanilla.update({k: v for k, v in costing["vanilla"].items() if not k.startswith("_")})
        # Matiere -> prix du lingot. Les metaux du shop et les matieres de
        # reference ont le meme statut ici : seul le shop les distingue.
        self.material_price = {}
        for name, spec in list(config["materials"].items()) + list(costing["reference"].items()):
            if name.startswith("_"):
                continue
            self.material_price[name] = self.levels[spec["level"]]["buy_unit"]
        # Les formes chiffrees directement (lingot, pepite, billette...). Les
        # formes sans facteur, plaques et fils, passent par leur recette.
        self.form_factor = {k: v for k, v in costing["reference_forms"].items() if not k.startswith("_")}
        self.tags = self._load_tags(Path(data_dir))
        self.recipes = self._load_recipes(Path(data_dir))
        self.memo = {}
        self.how = {}
        self.stack = set()

    # -- donnees -------------------------------------------------------------

    @staticmethod
    def _load_tags(data_dir):
        tags = {}
        for namespace_dir in data_dir.iterdir():
            item_tags = namespace_dir / "tags" / "item"
            if not item_tags.is_dir():
                continue
            for path in item_tags.rglob("*.json"):
                tag = "%s:%s" % (namespace_dir.name, path.relative_to(item_tags).with_suffix("").as_posix())
                try:
                    values = json.loads(path.read_text(encoding="utf-8")).get("values", [])
                except (ValueError, OSError):
                    continue
                members = []
                for value in values:
                    member = value if isinstance(value, str) else value.get("id")
                    if member:
                        members.append(member)
                tags[tag] = members
        return tags

    def _conditions_hold(self, recipe):
        for condition in recipe.get("neoforge:conditions", []) or []:
            if condition.get("type") != "hbm:flag":
                return False
            name = condition.get("name", "")
            wanted = not name.startswith("!")
            if self.flags.get(name.lstrip("!"), False) != wanted:
                return False
        return True

    def _load_recipes(self, data_dir):
        recipes = {}
        for path in data_dir.glob("*/recipe/**/*.json"):
            if "recycle" in path.name:
                continue
            try:
                recipe = json.loads(path.read_text(encoding="utf-8"))
            except (ValueError, OSError):
                continue
            if recipe.get("type") not in RECIPE_TYPES or not self._conditions_hold(recipe):
                continue
            if "recycle" in str(recipe.get("name", "")).lower():
                continue
            output = self._output(recipe)
            if output is None:
                continue
            item, count = output
            recipes.setdefault(item, []).append((path.stem, recipe, count))
        return recipes

    @staticmethod
    def _output(recipe):
        """L'unique sortie certaine d'une recette, ou None."""
        if "result" in recipe:
            result = recipe["result"]
            if isinstance(result, str):
                return result, 1
            return result.get("id") or result.get("item"), result.get("count", 1)
        outputs = recipe.get("output_items") or []
        if len(outputs) != 1:
            return None
        output = outputs[0]
        if isinstance(output, str):
            return output, 1
        if isinstance(output, dict) and output.get("chance", 1.0) >= 1.0 and output.get("id"):
            return output["id"], output.get("count", 1)
        return None

    # -- couts ---------------------------------------------------------------

    def tag_members(self, tag, seen=None):
        seen = seen or set()
        if tag in seen:
            return []
        seen.add(tag)
        members = []
        for member in self.tags.get(tag, []):
            if member.startswith("#"):
                members += self.tag_members(member[1:], seen)
            else:
                members.append(member)
        if not members and tag in self.aliases:
            members.append(self.aliases[tag])
        return members

    def material_cost(self, tag):
        """Le cout d'une forme de matiere connue, ou None."""
        namespace, _, path = tag.partition(":")
        if "/" not in path:
            return None
        form, _, material = path.partition("/")
        if material not in self.material_price:
            return None
        factor = self.form_factor.get(form)
        if factor is None:
            return None
        return self.material_price[material] * factor

    def tag_cost(self, tag):
        direct = self.material_cost(tag)
        if direct is not None:
            return direct, "matiere %s" % tag
        best = None
        for member in self.tag_members(tag):
            cost = self.item_cost(member)
            if cost is not None and (best is None or cost < best[0]):
                best = (cost, member)
        if best is None:
            return None, None
        return best[0], best[1]

    def ingredient_cost(self, ingredient):
        if isinstance(ingredient, list):
            costs = [self.ingredient_cost(alternative) for alternative in ingredient]
            costs = [c for c in costs if c is not None]
            return min(costs) if costs else None
        if isinstance(ingredient, dict):
            kind = ingredient.get("neoforge:ingredient_type")
            if kind == "neoforge:compound":
                return self.ingredient_cost(ingredient.get("children", []))
            if kind == "neoforge:difference":
                return self.ingredient_cost(ingredient.get("base"))
            if kind is not None:
                return None
            if "tag" in ingredient:
                return self.tag_cost(ingredient["tag"])[0]
            if "item" in ingredient:
                return self.item_cost(ingredient["item"])
            if "ingredient" in ingredient:
                return self.ingredient_cost(ingredient["ingredient"])
            return None
        if isinstance(ingredient, str):
            if ingredient.startswith("#"):
                return self.tag_cost(ingredient[1:])[0]
            return self.item_cost(ingredient)
        return None

    def recipe_inputs(self, recipe):
        """Paires (ingredient, quantite) d'une recette."""
        if "pattern" in recipe:
            counts = {}
            for row in recipe["pattern"]:
                for symbol in row:
                    if symbol != " ":
                        counts[symbol] = counts.get(symbol, 0) + 1
            return [(recipe["key"][symbol], count) for symbol, count in counts.items()]
        if "ingredients" in recipe:
            return [(ingredient, 1) for ingredient in recipe["ingredients"]]
        if "ingredient" in recipe:
            return [(recipe["ingredient"], 1)]
        inputs = []
        for key in INGREDIENT_LISTS:
            for entry in recipe.get(key, []) or []:
                if isinstance(entry, dict) and "count" in entry and "ingredient" in entry:
                    inputs.append((entry["ingredient"], entry["count"]))
                else:
                    inputs.append((entry, 1))
        return inputs

    def item_cost(self, item):
        if item in self.memo:
            return self.memo[item]
        if item in self.vanilla:
            self.memo[item] = self.vanilla[item]
            self.how[item] = "vanilla"
            return self.memo[item]
        # Une forme de matiere connue se chiffre a son cran, pas a sa recette.
        best = None
        for tag, members in self.tags.items():
            if item in members:
                cost = self.material_cost(tag)
                if cost is not None and (best is None or cost < best[0]):
                    best = (cost, "matiere %s" % tag)
        if best is not None:
            self.memo[item], self.how[item] = best
            return best[0]
        if item in self.stack:
            return None
        self.stack.add(item)
        for name, recipe, count in self.recipes.get(item, []):
            total = 0.0
            for ingredient, quantity in self.recipe_inputs(recipe):
                cost = self.ingredient_cost(ingredient)
                if cost is None:
                    total = None
                    break
                total += cost * quantity
            if total is None:
                continue
            unit = total / max(1, count)
            if best is None or unit < best[0]:
                best = (unit, "recette %s" % name)
        self.stack.discard(item)
        if best is None:
            return None
        # Pas de memo d'un echec : il peut venir d'un cycle en cours.
        self.memo[item], self.how[item] = best
        return best[0]


def load_vanilla():
    vanilla = {}
    with PRICES_CSV.open(encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            try:
                buy = int(row["buy_price"])
            except (KeyError, ValueError):
                continue
            if buy > 0 and row["item_id"].startswith("minecraft:"):
                vanilla[row["item_id"]] = buy
    return vanilla


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("data_dir", help="dossier data des ressources de NTM NEXT")
    parser.add_argument("--dry-run", action="store_true", help="afficher sans ecrire")
    args = parser.parse_args()

    config = json.loads(TAG_PRICES.read_text(encoding="utf-8"))
    pricer = Pricer(args.data_dir, config, load_vanilla())

    components = {}
    missing = []
    print("%-34s %14s  %s" % ("piece", "prix", "chiffrage"))
    for target in config["costing"]["targets"]:
        cost = pricer.item_cost(target)
        if cost is None:
            missing.append(target)
            continue
        price = round_sig(cost * pricer.premium)
        components[target] = price
        print("%-34s %14s  %s" % (target, "{:,}".format(price).replace(",", " "), pricer.how.get(target, "")))
    if missing:
        print("\nSans recette chiffrable, laisses hors du shop :", ", ".join(missing), file=sys.stderr)

    if args.dry_run:
        return
    config["components"] = components
    TAG_PRICES.write_text(json.dumps(config, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("\n%d pieces ecrites dans %s" % (len(components), TAG_PRICES.relative_to(ROOT)))


if __name__ == "__main__":
    main()
