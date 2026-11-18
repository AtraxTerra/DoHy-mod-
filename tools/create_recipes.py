#!/usr/bin/env python3
"""Genere les recettes qui relient Create (Create Fly) au reste de DoHy.

Ecrit dans src/main/resources/data/dohy/recipe/create/. Chaque recette porte
ses conditions neoforge:mod_loaded : sans Create (ou sans NTM NEXT pour les
siennes), elle n'existe pas, et le mod se charge comme avant.

Ce qui est relie :

  Minerais de PalaMod -> roues de broyage.
      Amethyste, titane, paladium, paladium vert : 9 particules (un lingot),
      plus 75 % de chance d'en avoir 9 autres, comme le minerai de fer de
      Create (1 broye + 75 %). Findium et trixium : la pierre, plus 75 %, comme
      le diamant. Les particules se changent en lingots a l'etabli, et la
      presse de Create sur un bassin le fait seule (compactage).
      Pas de broyage pour le minerai de pepites d'endium (une pepite vaut
      40 000 $, le doubler serait une rente) ni pour le minerai d'invocateur.

  Minerais de NTM NEXT -> roues de broyage.
      Plomb, uranium, aluminium : les minerais broyes de Create, que Create
      Fly fournit sans recette (ses recettes de compatibilite ne sont pas
      chargees). Lavage : 9 pepites de NTM ; four et haut-fourneau : le
      lingot de NTM. Titane, tungstene, beryllium, cobalt, thorium,
      lithium : la poudre de NTM, que NTM fond deja en lingot. Les minerais de
      gneiss de fer, d'or et de cuivre : les broyes de Create.
      La matiere brute ne se multiplie pas (une broyee, de l'XP), comme chez
      Create : seul le minerai entier (pioche soie) gagne 75 %. Le shop ne
      rachete pas les lingots de NTM : le rendement ne devient pas un revenu.

  Cereales de DoHy -> meule.
      Une farine pour trois grains en moyenne (un tiers de chance par grain),
      le rapport du moulin de DoHy. Le ble garde la meule de Create (farine
      de Create).

Usage :
    python3 -I tools/create_recipes.py          # ecrit les fichiers
    python3 -I tools/create_recipes.py --check  # verifie qu'ils sont a jour
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "data" / "dohy" / "recipe" / "create"

CREATE = {"type": "neoforge:mod_loaded", "modid": "create"}
NTM = {"type": "neoforge:mod_loaded", "modid": "hbm"}


def tag_filled(tag):
    return {"type": "neoforge:not", "value": {"type": "neoforge:tag_empty", "tag": tag}}


def out(item, count=1, chance=None):
    entry = {"id": item}
    if count != 1:
        entry["count"] = count
    if chance is not None:
        entry["chance"] = chance
    return entry


def recipe(conditions, kind, ingredient, results, time=None):
    body = {"neoforge:conditions": conditions, "type": kind, "ingredient": ingredient}
    if time is not None:
        body["processing_time"] = time
    body["results"] = results
    return body


def cooking(conditions, kind, ingredient, result, time, xp):
    return {
        "neoforge:conditions": conditions,
        "type": kind,
        "category": "misc",
        "cookingtime": time,
        "experience": xp,
        "ingredient": ingredient,
        "result": {"count": 1, "id": result},
    }


def recipes():
    files = {}

    # -- PalaMod : minerai -> particules (9 = un lingot) ou pierre --------------
    particles = {
        "amethyst_ore": ("palamod:amethyst_particle", 300),
        "titane_ore": ("palamod:titane_particle", 300),
        "paladium_ore": ("palamod:paladium_particle", 350),
        "paladium_green_ore": ("palamod:green_paladium_particle", 350),
    }
    for ore, (particle, time) in particles.items():
        files[f"crushing/palamod_{ore}"] = recipe(
            [CREATE], "create:crushing", f"palamod:{ore}",
            [out(particle, 9), out(particle, 9, 0.75), out("create:experience_nugget", 1, 0.75),
             out("minecraft:cobblestone", 1, 0.125)], time)
    for ore, gem in (("findium_ore", "palamod:findium"), ("trixium_ore", "palamod:trixium")):
        files[f"crushing/palamod_{ore}"] = recipe(
            [CREATE], "create:crushing", f"palamod:{ore}",
            [out(gem), out(gem, 1, 0.75), out("create:experience_nugget", 1, 0.75),
             out("minecraft:cobblestone", 1, 0.125)], 350)

    # -- NTM : les metaux que Create sait broyer --------------------------------
    crushed = {
        # matiere (sous-tag c:) : (broye de Create, pepite NTM ou None, lingot NTM)
        "lead": ("create:crushed_raw_lead", "hbm:nugget_lead", "hbm:ingot_lead"),
        "uranium": ("create:crushed_raw_uranium", "hbm:nugget_uranium", "hbm:ingot_uranium"),
        "aluminum": ("create:crushed_raw_aluminum", None, "hbm:ingot_aluminium"),
    }
    for metal, (broken, nugget, ingot) in crushed.items():
        ores, raws = f"c:ores/{metal}", f"c:raw_materials/{metal}"
        files[f"crushing/ntm_{metal}_ore"] = recipe(
            [CREATE, NTM, tag_filled(ores)], "create:crushing", f"#{ores}",
            [out(broken), out(broken, 1, 0.75), out("create:experience_nugget", 1, 0.75)], 400)
        files[f"crushing/ntm_raw_{metal}"] = recipe(
            [CREATE, NTM, tag_filled(raws)], "create:crushing", f"#{raws}",
            [out(broken), out("create:experience_nugget", 1, 0.75)], 400)
        if nugget:
            files[f"splashing/ntm_crushed_raw_{metal}"] = recipe(
                [CREATE, NTM], "create:splashing", broken, [out(nugget, 9)])
        files[f"smelting/ntm_{metal}_ingot_from_crushed"] = cooking(
            [CREATE, NTM], "minecraft:smelting", broken, ingot, 200, 0.1)
        files[f"blasting/ntm_{metal}_ingot_from_crushed"] = cooking(
            [CREATE, NTM], "minecraft:blasting", broken, ingot, 100, 0.1)

    # -- NTM : les metaux sans broye chez Create -> poudre de NTM ---------------
    powders = {
        "titanium": "hbm:powder_titanium",
        "tungsten": "hbm:powder_tungsten",
        "beryllium": "hbm:powder_beryllium",
        "cobalt": "hbm:powder_cobalt",
        "thorium": "hbm:powder_thorium",
        "lithium": "hbm:powder_lithium",
    }
    for metal, powder in powders.items():
        ores, raws = f"c:ores/{metal}", f"c:raw_materials/{metal}"
        files[f"crushing/ntm_{metal}_ore"] = recipe(
            [CREATE, NTM, tag_filled(ores)], "create:crushing", f"#{ores}",
            [out(powder), out(powder, 1, 0.75), out("create:experience_nugget", 1, 0.75)], 400)
        files[f"crushing/ntm_raw_{metal}"] = recipe(
            [CREATE, NTM, tag_filled(raws)], "create:crushing", f"#{raws}",
            [out(powder), out("create:experience_nugget", 1, 0.75)], 400)

    # -- NTM : minerais de gneiss de fer, d'or et de cuivre ---------------------
    # Par identifiant, pas par c:ores/<metal> : ce tag contient aussi les
    # minerais de Minecraft, que Create broie deja.
    gneiss = {
        "iron": ("create:crushed_raw_iron", 1, 0.75),
        "gold": ("create:crushed_raw_gold", 1, 0.75),
        "copper": ("create:crushed_raw_copper", 5, 0.25),
    }
    for metal, (broken, count, extra) in gneiss.items():
        files[f"crushing/ntm_gneiss_{metal}_ore"] = recipe(
            [CREATE, NTM], "create:crushing", f"hbm:ore_gneiss_{metal}",
            [out(broken, count), out(broken, 1, extra), out("create:experience_nugget", 1, 0.75)], 250)

    # -- Cereales de DoHy -> meule : une farine pour trois grains ---------------
    for cereal in ("corn", "barley", "oat", "rye", "sunflower", "soy", "fonio", "sorghum", "kamut"):
        files[f"milling/{cereal}"] = recipe(
            [CREATE], "create:milling", f"dohy:{cereal}", [out(f"dohy:{cereal}_flour", 1, 0.333)], 150)

    return files


def render(body):
    return json.dumps(body, indent=2, ensure_ascii=True) + "\n"


def main():
    check = "--check" in sys.argv[1:]
    wanted = {OUT / f"{name}.json": render(body) for name, body in recipes().items()}
    present = set(OUT.rglob("*.json")) if OUT.exists() else set()
    stale = sorted(str(p.relative_to(ROOT)) for p in present - set(wanted))
    changed = sorted(str(p.relative_to(ROOT)) for p, text in wanted.items()
                     if not p.exists() or p.read_text(encoding="utf-8") != text)
    if check:
        for path in changed:
            print(f"::error::{path} n'est pas a jour : lance python3 -I tools/create_recipes.py")
        for path in stale:
            print(f"::error::{path} n'est plus genere : lance python3 -I tools/create_recipes.py")
        if changed or stale:
            sys.exit(1)
        print(f"{len(wanted)} recettes Create a jour")
        return
    for path in stale:
        (ROOT / path).unlink()
    for path, text in wanted.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(f"{len(wanted)} recettes Create ecrites, {len(stale)} retirees")


if __name__ == "__main__":
    main()
