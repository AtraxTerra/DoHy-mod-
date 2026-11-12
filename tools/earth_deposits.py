#!/usr/bin/env python3
"""Ecrit la table des gisements de la Terre : data/dohy/earth/deposits.json.

Chaque gisement est pose la ou le monde reel exploite la ressource : une liste
de pays (codes a trois lettres de Natural Earth) et, pour une region d'un grand
pays ou un champ en mer, des rectangles en degres (sud, nord, ouest, est). La
liste suit les principaux producteurs ; elle est faite pour le jeu, pas pour un
atlas.

    python3 -I tools/earth_deposits.py [countries.json]

Avec la liste des pays d'une carte en argument, le script verifie que chaque
code cite y existe.
"""

import json
import os
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "dohy",
                   "earth", "deposits.json")


def box(south, north, west, east):
    return {"south": south, "north": north, "west": west, "east": east}


def deposit(name, ore, deepslate, countries, veins, size=6, min_y=-56, max_y=32, boxes=(), radius=0):
    entry = {"id": name, "ore": ore}
    if deepslate:
        entry["deepslate"] = deepslate
    entry["countries"] = countries.split()
    if boxes:
        entry["boxes"] = list(boxes)
    entry.update({"min_y": min_y, "max_y": max_y, "veins": veins})
    if radius:
        entry["radius"] = radius
    else:
        entry["size"] = size
    return entry


def vanilla(name):
    return "minecraft:%s_ore" % name, "minecraft:deepslate_%s_ore" % name


def ntm(name):
    return "hbm:ore_%s" % name, "hbm:ore_%s_deepslate" % name


YAKUTIA = box(58, 72, 105, 135)
WEST_SIBERIA = box(56, 68, 60, 85)
TEXAS = box(26, 36, -106, -93)
GULF_OF_MEXICO = box(26, 29.5, -95, -88)
NORTH_SEA = box(56, 62, 0, 5)
PERSIAN_GULF = box(24, 30, 48, 54)
CASPIAN = box(38, 47, 49, 55)

DEPOSITS = [
    # -- Les minerais du jeu. Le charbon, le fer et le cuivre poussent partout ; ici, ils abondent.
    deposit("coal", *vanilla("coal"), "CHN IND USA IDN AUS RUS ZAF DEU POL KAZ COL UKR MNG GBR",
            4.0, 14, 0, 120),
    deposit("iron", *vanilla("iron"), "AUS BRA CHN IND RUS UKR SWE ZAF CAN USA KAZ IRN MRT LBR",
            4.0, 9, -32, 72),
    deposit("copper", *vanilla("copper"), "CHL PER COD ZMB CHN USA AUS IDN MEX RUS KAZ POL",
            4.0, 10, -16, 96),
    # L'or, le diamant, l'emeraude, le lapis et la redstone ne viennent que d'ici (et de la dimension du Mineur).
    deposit("gold", *vanilla("gold"), "CHN AUS USA CAN ZAF GHA PER MEX UZB IDN BRA MLI BFA TZA PNG",
            4.0, 8, -56, 32, boxes=[box(50, 66, 100, 160)]),
    deposit("diamond", *vanilla("diamond"), "BWA COD ZAF AGO NAM SLE LSO ZWE",
            2.5, 6, -60, 8, boxes=[YAKUTIA, box(60, 68, -120, -100), box(-20, -14, 123, 130)]),
    deposit("emerald", *vanilla("emerald"), "COL ZMB ZWE AFG PAK MDG",
            1.5, 4, -16, 120, boxes=[box(-22, -10, -46, -38)]),
    deposit("lapis", *vanilla("lapis"), "AFG CHL PAK",
            3.0, 7, -40, 40, boxes=[box(50, 54, 100, 108)]),
    deposit("redstone", *vanilla("redstone"), "ESP CHN KGZ TJK MEX ITA SVN USA RUS",
            4.0, 8, -60, 16),
    # -- NTM NEXT. Ses minerais de l'overworld sont coupes sur la carte : ils ne viennent que d'ici.
    # Les metaux de tous les jours : assez de pays pour qu'il y en ait sur chaque continent.
    deposit("aluminium", *ntm("aluminium"),
            "AUS GIN CHN BRA IND JAM IDN KAZ SAU VNM GRC FRA RUS USA CAN GHA SUR GUY HUN TUR MOZ",
            6.0, 6, -32, 96),
    deposit("titanium", *ntm("titanium"),
            "CHN ZAF AUS MOZ NOR UKR IND MDG KEN SEN VNM CAN USA BRA RUS KAZ JPN SLE",
            5.0, 6, -56, 48),
    deposit("lead", *ntm("lead"),
            "CHN AUS USA PER MEX IND BOL SWE POL IRL RUS KAZ CAN MAR ZAF TUR ESP DEU BRA",
            5.0, 9, -56, 64),
    deposit("sulfur", *ntm("sulfur"),
            "CHN USA SAU KAZ ARE ITA JPN IDN POL RUS CAN MEX CHL IRN IRQ DEU IND TUR ETH",
            4.0, 8, -56, 64),
    deposit("niter", *ntm("niter"),
            "CHL IND PER BOL EGY CHN USA ESP DZA ZAF RUS UKR IRN AUS ARG",
            5.0, 6, -32, 64),
    deposit("tungsten", *ntm("tungsten"),
            "CHN VNM BOL RWA AUT PRT ESP PRK RUS CAN USA AUS BRA GBR MNG KAZ",
            6.0, 8, -56, 64),
    deposit("fluorite", *ntm("fluorite"),
            "CHN MEX MNG ZAF VNM ESP GBR FRA DEU USA RUS KEN MAR IRN BRA ARG THA",
            5.0, 4, -32, 80),
    deposit("beryllium", *ntm("beryllium"),
            "CHN MOZ BRA MDG RWA USA RUS KAZ IND ARG PRT UGA",
            4.0, 4, -56, 32),
    deposit("lignite", *ntm("lignite"),
            "DEU POL CZE GRC TUR AUS SRB BGR ROU RUS USA CHN IND IDN CAN THA",
            2.0, 20, 0, 96),
    deposit("asbestos", *ntm("asbestos"), "KAZ CHN BRA ZWE RUS CAN ZAF IND ITA GRC",
            3.0, 4, -32, 64),
    deposit("cinnabar", *ntm("cinnabar"), "ESP CHN KGZ TJK MEX ITA SVN PER USA RUS TUR DZA",
            2.0, 4, -56, 32),
    # Les ressources strategiques : peu de pays, il faut y aller.
    deposit("uranium", *ntm("uranium"), "KAZ AUS NAM NER UZB UKR ZAF CHN IND CZE",
            6.0, 5, -56, 32, boxes=[box(54, 60, -110, -102), box(37, 45, -110, -103), box(50, 56, 108, 120)]),
    deposit("thorium", *ntm("thorium"), "IND BRA AUS EGY TUR NOR VEN",
            5.0, 5, -56, 48),
    deposit("rare_earth", *ntm("rare"), "CHN AUS MMR VNM BRA IND GRL SWE",
            5.0, 5, -56, 32, boxes=[box(33, 37, -118, -113), box(66, 70, 30, 40)]),
    deposit("lithium", "hbm:ore_gneiss_lithium", None, "CHL ARG BOL AUS CHN ZWE PRT",
            5.0, 6, 0, 48),
    deposit("cobalt", *ntm("cobalt"), "COD ZMB AUS PHL CUB MDG MAR",
            2.0, 4, -60, -20, boxes=[box(66, 70, 86, 92)]),
    deposit("coltan", *ntm("coltan"), "COD RWA BRA NGA MOZ ETH BDI",
            2.0, 4, -40, 40),
    # Le petrole de NTM NEXT garde ses poches rares partout ; les bassins petroliers et leurs mers en ont bien plus.
    deposit("oil", "hbm:ore_oil", None,
            "SAU IRQ IRN KWT ARE QAT OMN VEN NGA LBY DZA AGO AZE IDN MYS BRN GAB COG GNQ SDN SDS EGY SYR YEM "
            "TKM ECU COL ROU TTO",
            0.15, min_y=15, max_y=39, radius=6,
            boxes=[WEST_SIBERIA, TEXAS, GULF_OF_MEXICO, NORTH_SEA, PERSIAN_GULF, CASPIAN,
                   box(68, 71, -156, -146), box(46, 49, -104, -100), box(18, 21, -94, -90),
                   box(-26, -22, -44, -40), box(54, 58, -115, -110), box(45, 47, 123, 126),
                   box(-40, -37, -70, -68)]),
    deposit("bedrock_oil", "hbm:ore_bedrock_oil", None, "SAU IRQ IRN KWT ARE QAT VEN",
            0.03, 3, -63, -61, boxes=[WEST_SIBERIA, TEXAS]),
    # -- Create ------------------------------------------------------------------------
    deposit("zinc", "create:zinc_ore", "create:deepslate_zinc_ore", "CHN PER AUS IND USA MEX BOL KAZ SWE IRL",
            3.0, 8, -60, 70),
]


def main():
    known = None
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8") as fh:
            data = json.load(fh)
        known = {entry["code"] for entry in (data["countries"] if isinstance(data, dict) else data)}
    for entry in DEPOSITS:
        for code in entry["countries"]:
            if known is not None and code not in known:
                raise SystemExit("gisement %s : pays inconnu de la carte : %s" % (entry["id"], code))
        for area in entry.get("boxes", ()):
            if not (area["south"] < area["north"] and area["west"] < area["east"]):
                raise SystemExit("gisement %s : rectangle a l'envers : %s" % (entry["id"], area))
    payload = {
        "_comment": "Les gisements de la Terre, GENERE par tools/earth_deposits.py. Ils ne servent que dans un monde "
                    "dont un datapack decrit la carte (earth.json). 'veins' : filons par chunk, en moyenne ; "
                    "'radius' : une poche ronde au lieu d'un filon. Change 'version' pour resservir les chunks "
                    "deja visites.",
        "version": 1,
        "per_tick": 12,
        "deposits": DEPOSITS,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("%d gisements ecrits." % len(DEPOSITS))


if __name__ == "__main__":
    main()
