#!/usr/bin/env python3
"""Ecrit la table des gisements et des lieux de la Terre : data/dohy/earth/deposits.json et sites.json.

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

EARTH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "dohy",
                     "earth")
OUT = os.path.join(EARTH, "deposits.json")
SITES_OUT = os.path.join(EARTH, "sites.json")


def box(south, north, west, east):
    return {"south": south, "north": north, "west": west, "east": east}


def deposit(name, ore, deepslate, countries, veins, size=6, min_y=-56, max_y=32, boxes=(), radius=0, sites=()):
    entry = {"id": name, "ore": ore}
    if deepslate:
        entry["deepslate"] = deepslate
    entry["countries"] = countries.split()
    if boxes:
        entry["boxes"] = list(boxes)
    if sites:
        entry["sites"] = list(sites)
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

ZONES = ("chernobyl", "fukushima")


def site(name, label, kind, latitude, longitude, radius, core=0):
    entry = {"id": name, "name": label, "kind": kind, "latitude": latitude, "longitude": longitude, "radius": radius}
    if kind == "irradiated":
        entry.update({"core_radius": core, "biome": "dohy:exclusion_zone", "core_biome": "dohy:exclusion_core"})
    return entry


# Une carte de la Terre au 1:110 000 environ (68,7 blocs par degre) : un bloc fait 1,6 km. Les zones sont
# donc a l'echelle du jeu plutot que du terrain — celle de Tchernobyl reelle ne ferait que 19 blocs de rayon.
# Chaque centre est celui du chunk sec le plus proche du lieu reel, dans le bon pays, sur la carte Earth24k
# (au plus 37 km) :
# la centrale de Tchernobyl tombe dans le chunk de la Pripiat, Cap Canaveral et Fukushima dans la mer.
SITES = [
    site("chernobyl", "Tchernobyl", "irradiated", 51.196, 29.919, 40, 12),
    site("fukushima", "Fukushima", "irradiated", 37.381, 140.817, 28, 8),
    site("baikonur", "Baikonour", "launch", 45.937, 63.235, 48),
    site("canaveral", "Cap Canaveral", "launch", 28.195, -80.747, 48),
    site("kourou", "Kourou", "launch", 5.184, -52.789, 48),
    site("jiuquan", "Jiuquan", "launch", 40.962, 100.278, 48),
]

LAUNCH_BLOCKS = ["hbmspace:launch_pad_rocket", "hbmspace:transporter_rocket",
                 "hbmspace:orbital_station_launcher", "hbmspace:dyson_launcher"]

DEPOSITS = [
    # -- Les minerais du jeu. Sur la carte, plus rien n'est seme partout : le charbon, le fer et le cuivre ne
    # sont que la ou on les exploite ou les a exploites (les vieux bassins d'Europe compris).
    deposit("coal", *vanilla("coal"),
            "CHN IND USA IDN AUS RUS ZAF DEU POL KAZ COL UKR MNG GBR FRA BEL CZE ESP TUR VNM CAN MOZ ZWE BWA "
            "BRA VEN PAK JPN NZL TZA PRK KOR",
            3.0, 12, 0, 120),
    deposit("iron", *vanilla("iron"),
            "AUS BRA CHN IND RUS UKR SWE ZAF CAN USA KAZ IRN MRT LBR FRA ESP AUT DEU GBR NOR GIN SLE VEN CHL "
            "PER MEX TUR PRK MNG VNM MYS GAB CMR DZA EGY",
            3.0, 8, -32, 72),
    deposit("copper", *vanilla("copper"),
            "CHL PER COD ZMB CHN USA AUS IDN MEX RUS KAZ POL CAN MNG ZAF BWA NAM PAN ECU ARG IRN PHL PNG ESP PRT "
            "SWE FIN SRB BGR CYP ARM UZB TUR MRT ERI LAO MMR",
            3.0, 9, -16, 96),
    # Le quartz du Nether, que la carte semait partout : dans les pays du quartz, et dans leurs mines.
    deposit("quartz", "minecraft:nether_quartz_ore", None, "BRA USA MDG IND CHN RUS NOR DEU",
            2.0, 8, -32, 64),
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
    # -- Les zones irradiees : les minerais de NTM NEXT qu'on ne trouve nulle part ailleurs sur la Terre.
    # Ceux d'un cratere de Sellafield : la radiation les a faits ; il faut une combinaison pour les prendre.
    deposit("zone_schrabidium", "hbm:ore_sellafield_schrabidium", None, "", 0.3, 3, -56, 40, sites=ZONES),
    deposit("zone_radgem", "hbm:ore_sellafield_radgem", None, "", 0.8, 3, -56, 48, sites=ZONES),
    deposit("zone_uranium", "hbm:ore_sellafield_uranium_scorched", None, "", 2.0, 5, -56, 56, sites=ZONES),
    deposit("zone_diamond", "hbm:ore_sellafield_diamond", None, "", 1.0, 4, -56, 24, sites=ZONES),
    deposit("zone_emerald", "hbm:ore_sellafield_emerald", None, "", 0.8, 3, -40, 56, sites=ZONES),
    # -- Create ------------------------------------------------------------------------
    deposit("zinc", "create:zinc_ore", "create:deepslate_zinc_ore", "CHN PER AUS IND USA MEX BOL KAZ SWE IRL",
            3.0, 8, -60, 70),
]

# Moins de filons epars depuis les gisements geants (2026-11-13) : les autres ressources gardent 60 % de leurs
# filons ; le charbon, le fer, le cuivre et le quartz, qui n'existent plus ailleurs sur la carte, gardent les leurs.
SCATTER = 0.6
# Le nom de chaque ressource, pour le chat (sans accents) ; vide : la ressource ne se cite pas.
LABELS = {"coal": "charbon", "iron": "fer", "copper": "cuivre", "quartz": "quartz", "gold": "or",
          "diamond": "diamant", "emerald": "emeraude", "lapis": "lapis", "redstone": "redstone",
          "aluminium": "bauxite", "titanium": "titane", "lead": "plomb", "sulfur": "soufre", "niter": "salpetre",
          "tungsten": "tungstene", "fluorite": "fluorine", "beryllium": "beryllium", "lignite": "lignite",
          "asbestos": "amiante", "cinnabar": "cinabre", "uranium": "uranium", "thorium": "thorium",
          "rare_earth": "terres rares", "lithium": "lithium", "cobalt": "cobalt", "coltan": "coltan",
          "oil": "petrole", "zinc": "zinc"}
for _entry in DEPOSITS:
    _entry["label"] = LABELS.get(_entry["id"], "")
    if _entry["id"] not in ("coal", "iron", "copper", "quartz", "bedrock_oil") and not _entry["id"].startswith("zone_"):
        _entry["veins"] = round(_entry["veins"] * SCATTER, 2)


# -- Les gisements geants : de vraies mines et de vrais champs ---------------------------------------------
# Une mine geante est un corps de minerai de plusieurs chunks pose a la place de la vraie mine : un ellipsoide
# bossele de 22, 16 ou 11 blocs de rayon (rangs 1, 2, 3), allonge et tourne comme le gisement s'il le faut.
# A l'echelle de la carte (un bloc pour 1,6 km), le rayon d'un rang 1 couvre 35 km : c'est le district minier.
# Les gisements de diamant sont des cheminees de kimberlite (tuf, du fond du monde jusque sous la surface) ;
# le petrole et le gaz, des nappes minces et larges sous les vrais champs, et le Create Diesel Generators y voit
# du petrole dans ses chunks ; les sables bitumineux de l'Athabasca, une couche sous la surface.
#
# Les coordonnees sont celles des mines et des champs reels (a quelques kilometres pres) ; les rangs disent
# leur importance mondiale, pas un tonnage. Les noms sont sans accents : ils s'affichent dans le chat.

TIERS = {1: (22, 12), 2: (16, 9), 3: (11, 7)}


def res(label, ore, deepslate, y, density, surface=0, rich=None):
    return {"label": label, "ore": ore, "deepslate": deepslate, "y": y, "density": density, "surface": surface,
            "rich": rich}


RESOURCES = {
    "coal": res("charbon", *vanilla("coal"), 40, 0.40),
    "iron": res("fer", *vanilla("iron"), 24, 0.35, rich=("minecraft:raw_iron_block", 0.025)),
    "copper": res("cuivre", *vanilla("copper"), 24, 0.30, rich=("minecraft:raw_copper_block", 0.025)),
    "gold": res("or", *vanilla("gold"), 0, 0.10, rich=("minecraft:raw_gold_block", 0.01)),
    "diamond": res("diamant", *vanilla("diamond"), -14, 0.025),
    "emerald": res("emeraude", *vanilla("emerald"), 40, 0.06),
    "lapis": res("lapis", *vanilla("lapis"), 0, 0.15),
    "redstone": res("redstone", *vanilla("redstone"), -30, 0.20),
    "uranium": res("uranium", *ntm("uranium"), -10, 0.12),
    "thorium": res("thorium", *ntm("thorium"), 10, 0.12),
    "rare_earth": res("terres rares", *ntm("rare"), 0, 0.12),
    "lithium": res("lithium", "hbm:ore_gneiss_lithium", None, 20, 0.15),
    "cobalt": res("cobalt", *ntm("cobalt"), -30, 0.10),
    "coltan": res("coltan", *ntm("coltan"), 0, 0.10),
    "aluminium": res("bauxite", *ntm("aluminium"), 0, 0.35, surface=6),
    "titanium": res("titane", *ntm("titanium"), 20, 0.25),
    "lead": res("plomb", *ntm("lead"), 0, 0.25),
    "zinc": res("zinc", "create:zinc_ore", "create:deepslate_zinc_ore", 0, 0.25),
    "tungsten": res("tungstene", *ntm("tungsten"), 0, 0.15),
    "fluorite": res("fluorine", *ntm("fluorite"), 20, 0.20),
    "sulfur": res("soufre", *ntm("sulfur"), 20, 0.25),
    "niter": res("salpetre", *ntm("niter"), 0, 0.30, surface=6),
    "beryllium": res("beryllium", *ntm("beryllium"), 0, 0.10),
    "asbestos": res("amiante", *ntm("asbestos"), 20, 0.25),
    "lignite": res("lignite", *ntm("lignite"), 0, 0.45, surface=8),
    "quartz": res("quartz", "minecraft:nether_quartz_ore", None, 20, 0.25),
}

# Les geodes de quartz d'AE2 dans les mines de quartz : une coque de quartz certus, du quartz bourgeonnant, du vide.
GEODE = {"shell": "ae2:quartz_block", "budding": "ae2:flawed_budding_quartz", "budding_chance": 0.12,
         "radius": 4}
GEODES = {1: 6, 2: 4, 3: 2}


def mine(name, label, resource, lat, lon, country, tier=2, stretch=1.0, angle=0, extras=()):
    """Une mine geante : stretch > 1 l'allonge dans la direction 'angle' (degres, 0 = est-ouest)."""
    r, ry = TIERS[tier]
    info = RESOURCES[resource]
    body = {"id": name, "name": label, "resource": info["label"], "kind": "ore", "country": country,
            "latitude": lat, "longitude": lon, "tier": tier,
            "rx": round(r * stretch ** 0.5, 1), "rz": round(r / stretch ** 0.5, 1), "ry": ry, "angle": angle,
            "y": info["y"], "ore": info["ore"], "density": info["density"]}
    if info["deepslate"]:
        body["deepslate"] = info["deepslate"]
    if info["surface"]:
        body["surface"] = info["surface"]
    if info["rich"]:
        body["rich"] = {"block": info["rich"][0], "chance": info["rich"][1]}
    if extras:
        body["extras"] = []
        for other, chance in extras:
            extra = {"ore": RESOURCES[other]["ore"], "chance": chance}
            if RESOURCES[other]["deepslate"]:
                extra["deepslate"] = RESOURCES[other]["deepslate"]
            body["extras"].append(extra)
    if resource == "quartz":
        body["geodes"] = dict(GEODE, count=GEODES[tier])
    return body


def pipe(name, label, lat, lon, country, tier=2):
    """Une cheminee de kimberlite : du tuf, du diamant, du fond du monde jusqu'a six blocs sous la surface."""
    radius = {1: 8, 2: 6, 3: 5}[tier]
    info = RESOURCES["diamond"]
    return {"id": name, "name": label, "resource": info["label"], "kind": "pipe", "country": country,
            "latitude": lat, "longitude": lon, "tier": tier, "rx": radius, "rz": radius, "ry": 0, "angle": 0,
            "y": -58, "ore": info["ore"], "deepslate": info["deepslate"], "density": info["density"],
            "filler": "minecraft:tuff"}


def field(name, label, kind, lat, lon, country, rx, rz, angle=0, strength=0.7):
    """Un champ de petrole ou de gaz : une nappe de 'hbm:ore_oil' (le derrick de NTM en tire petrole et gaz).

    strength : la richesse des chunks pour le Create Diesel Generators (1 = 9 millions de mB au centre,
    au-dela de 1,12 le coeur est inepuisable)."""
    return {"id": name, "name": label, "resource": "gaz" if kind == "gas" else "petrole", "kind": "pool",
            "country": country, "latitude": lat, "longitude": lon, "tier": 1 if max(rx, rz) >= 30 else 2,
            "rx": rx, "rz": rz, "ry": 3, "angle": angle, "y": 10, "ore": "hbm:ore_oil", "density": 0.85,
            "oil": strength}


def sands(name, label, lat, lon, country, rx, rz, strength=0.8):
    """Des sables bitumineux : une couche de 'hbm:ore_oil_sand' de 2 a 14 blocs sous la surface."""
    return {"id": name, "name": label, "resource": "sables bitumineux", "kind": "sand", "country": country,
            "latitude": lat, "longitude": lon, "tier": 1, "rx": rx, "rz": rz, "ry": 6, "angle": 0, "y": 0,
            "surface": 8, "ore": "hbm:ore_oil_sand", "density": 0.9, "oil": strength}


BODIES = [
    # -- Fer --------------------------------------------------------------------------------------------------
    mine("kiruna", "Kiruna", "iron", 67.85, 20.22, "SWE", 1, 2.0, 30),
    mine("carajas", "Carajas", "iron", -6.07, -50.18, "BRA", 1),
    mine("quadrilatero", "Quadrilatero Ferrifero", "iron", -20.00, -43.60, "BRA", 1, 1.5, 60),
    mine("newman", "Mount Whaleback (Pilbara)", "iron", -23.36, 119.73, "AUS", 1, 2.0, 160),
    mine("tom_price", "Tom Price (Pilbara)", "iron", -22.69, 117.79, "AUS", 2),
    mine("kryvyi_rih", "Kryvyi Rih", "iron", 47.91, 33.39, "UKR", 1, 2.5, 90),
    mine("kursk", "Anomalie de Koursk", "iron", 51.75, 36.50, "RUS", 1, 2.0, 30),
    mine("labrador", "Fosse du Labrador", "iron", 52.95, -66.92, "CAN", 2, 2.0, 60),
    mine("mesabi", "Mesabi Range", "iron", 47.50, -92.55, "USA", 2, 3.0, 20),
    mine("simandou", "Simandou", "iron", 8.55, -8.90, "GIN", 2, 2.5, 90),
    mine("zouerat", "Zouerate", "iron", 22.73, -12.47, "MRT", 2),
    mine("sishen", "Sishen", "iron", -27.73, 22.99, "ZAF", 2),
    mine("anshan", "Anshan", "iron", 41.10, 122.99, "CHN", 2),
    mine("bailadila", "Bailadila", "iron", 18.60, 81.20, "IND", 2),
    mine("lorraine", "Bassin lorrain", "iron", 49.30, 6.00, "FRA", 3, 1.8, 100),
    mine("erzberg", "Erzberg", "iron", 47.52, 14.90, "AUT", 3),
    mine("rudny", "Rudny", "iron", 52.97, 63.12, "KAZ", 3),
    mine("gol_e_gohar", "Gol-e-Gohar", "iron", 29.10, 55.33, "IRN", 3),
    mine("biscaye", "Biscaye", "iron", 43.25, -2.95, "ESP", 3),
    mine("kostomuksha", "Kostomouksha", "iron", 64.57, 30.58, "RUS", 3),
    mine("marcona", "Marcona", "iron", -15.20, -75.10, "PER", 3),
    # -- Cuivre -----------------------------------------------------------------------------------------------
    mine("escondida", "Escondida", "copper", -24.27, -69.07, "CHL", 1),
    mine("chuquicamata", "Chuquicamata", "copper", -22.29, -68.90, "CHL", 1, 1.5, 90),
    mine("el_teniente", "El Teniente", "copper", -34.09, -70.35, "CHL", 2),
    mine("collahuasi", "Collahuasi", "copper", -20.97, -68.64, "CHL", 3),
    mine("cerro_verde", "Cerro Verde", "copper", -16.53, -71.59, "PER", 2),
    mine("antamina", "Antamina", "copper", -9.54, -77.05, "PER", 2, extras=[("zinc", 0.08)]),
    mine("grasberg", "Grasberg", "copper", -4.05, 137.11, "IDN", 1, extras=[("gold", 0.05)]),
    mine("bingham", "Bingham Canyon", "copper", 40.52, -112.15, "USA", 2, extras=[("gold", 0.02)]),
    mine("morenci", "Morenci", "copper", 33.08, -109.36, "USA", 2),
    mine("kolwezi", "Kolwezi", "copper", -10.75, 25.40, "COD", 1, 1.5, 0, extras=[("cobalt", 0.08)]),
    mine("copperbelt", "Copperbelt (Kitwe)", "copper", -12.53, 27.85, "ZMB", 1, 2.0, 135,
         extras=[("cobalt", 0.03)]),
    mine("oyu_tolgoi", "Oyu Tolgoi", "copper", 43.00, 106.85, "MNG", 2, extras=[("gold", 0.02)]),
    mine("olympic_dam", "Olympic Dam", "copper", -30.44, 136.88, "AUS", 1,
         extras=[("uranium", 0.08), ("gold", 0.02)]),
    mine("norilsk", "Norilsk", "copper", 69.35, 88.20, "RUS", 1, extras=[("cobalt", 0.03)]),
    mine("lubin", "Lubin", "copper", 51.40, 16.20, "POL", 2, 2.0, 120),
    mine("rio_tinto", "Rio Tinto", "copper", 37.70, -6.59, "ESP", 3),
    mine("neves_corvo", "Neves-Corvo", "copper", 37.57, -7.97, "PRT", 3, extras=[("zinc", 0.05)]),
    mine("bor", "Bor", "copper", 44.08, 22.10, "SRB", 3),
    mine("aitik", "Aitik", "copper", 67.07, 20.95, "SWE", 3),
    mine("dexing", "Dexing", "copper", 28.98, 117.72, "CHN", 2),
    mine("sar_cheshmeh", "Sar Cheshmeh", "copper", 29.95, 55.87, "IRN", 3),
    mine("cananea", "Cananea", "copper", 30.98, -110.30, "MEX", 2),
    mine("aktogay", "Aktogay", "copper", 46.97, 79.98, "KAZ", 3),
    mine("mount_isa", "Mount Isa", "copper", -20.73, 139.49, "AUS", 2, extras=[("lead", 0.10), ("zinc", 0.08)]),
    mine("sudbury", "Bassin de Sudbury", "copper", 46.60, -81.10, "CAN", 2, 1.6, 0, extras=[("cobalt", 0.03)]),
    # -- Or ---------------------------------------------------------------------------------------------------
    mine("witwatersrand", "Witwatersrand", "gold", -26.20, 27.80, "ZAF", 1, 2.5, 160),
    mine("carlin", "Carlin Trend", "gold", 40.90, -116.30, "USA", 2, 2.0, 130),
    mine("muruntau", "Muruntau", "gold", 41.50, 64.57, "UZB", 1),
    mine("kalgoorlie", "Super Pit (Kalgoorlie)", "gold", -30.78, 121.50, "AUS", 2),
    mine("sukhoi_log", "Soukhoi Log", "gold", 58.65, 115.20, "RUS", 2),
    mine("kolyma", "Kolyma", "gold", 62.80, 148.20, "RUS", 2, 2.0, 120),
    mine("yanacocha", "Yanacocha", "gold", -6.98, -78.52, "PER", 2),
    mine("obuasi", "Obuasi", "gold", 6.20, -1.67, "GHA", 2),
    mine("pueblo_viejo", "Pueblo Viejo", "gold", 18.93, -70.17, "DOM", 3),
    mine("kibali", "Kibali", "gold", 3.10, 29.60, "COD", 3),
    mine("klondike", "Klondike", "gold", 63.90, -139.00, "CAN", 3),
    mine("porgera", "Porgera", "gold", -5.47, 143.08, "PNG", 3),
    mine("mother_lode", "Mother Lode", "gold", 38.20, -120.60, "USA", 3, 2.5, 120),
    mine("sadiola", "Sadiola", "gold", 13.88, -11.70, "MLI", 3),
    # -- Diamant : des cheminees -------------------------------------------------------------------------------
    pipe("jwaneng", "Jwaneng", -24.53, 24.70, "BWA", 1),
    pipe("orapa", "Orapa", -21.31, 25.37, "BWA", 2),
    pipe("mir", "Mir (Mirny)", 62.53, 113.99, "RUS", 1),
    pipe("udachny", "Oudatchny", 66.43, 112.40, "RUS", 2),
    pipe("argyle", "Argyle", -16.71, 128.39, "AUS", 2),
    pipe("kimberley", "Big Hole (Kimberley)", -28.74, 24.77, "ZAF", 2),
    pipe("catoca", "Catoca", -9.40, 20.30, "AGO", 2),
    pipe("ekati", "Ekati", 64.70, -110.60, "CAN", 2),
    pipe("mbuji_mayi", "Mbuji-Mayi", -6.15, 23.60, "COD", 2),
    pipe("venetia", "Venetia", -22.43, 29.33, "ZAF", 3),
    # -- Emeraude, lapis, redstone (les districts du mercure) ---------------------------------------------------
    mine("muzo", "Muzo", "emerald", 5.53, -74.10, "COL", 2),
    mine("chivor", "Chivor", "emerald", 4.88, -73.37, "COL", 3),
    mine("panjshir", "Panjshir", "emerald", 35.30, 69.50, "AFG", 3),
    mine("swat", "Swat", "emerald", 34.77, 72.36, "PAK", 3),
    mine("sar_e_sang", "Sar-e-Sang", "lapis", 36.20, 70.79, "AFG", 2),
    mine("baikal_lapis", "Malaya Bystraya", "lapis", 51.53, 103.62, "RUS", 3),
    mine("ovalle", "Flor de los Andes", "lapis", -30.50, -70.60, "CHL", 3),
    mine("almaden", "Almaden", "redstone", 38.77, -4.84, "ESP", 2),
    mine("idrija", "Idrija", "redstone", 46.00, 14.03, "SVN", 3),
    mine("amiata", "Monte Amiata", "redstone", 42.88, 11.62, "ITA", 3),
    mine("wanshan", "Wanshan", "redstone", 27.52, 109.20, "CHN", 3),
    mine("khaidarkan", "Khaidarkan", "redstone", 39.95, 71.33, "KGZ", 3),
    mine("new_almaden", "New Almaden", "redstone", 37.18, -121.82, "USA", 3),
    mine("huancavelica", "Huancavelica", "redstone", -12.79, -74.97, "PER", 3),
    # -- Charbon et lignite -------------------------------------------------------------------------------------
    mine("powder_river", "Powder River", "coal", 43.70, -105.25, "USA", 1, 2.0, 90),
    mine("appalaches", "Appalaches", "coal", 38.00, -81.50, "USA", 2, 2.5, 45),
    mine("illinois", "Bassin de l'Illinois", "coal", 38.50, -88.50, "USA", 3),
    mine("shanxi", "Shanxi", "coal", 37.80, 112.50, "CHN", 1, 1.5, 90),
    mine("shendong", "Shendong", "coal", 39.30, 110.20, "CHN", 1),
    mine("jharia", "Jharia", "coal", 23.75, 86.42, "IND", 2),
    mine("korba", "Korba", "coal", 22.35, 82.68, "IND", 3),
    mine("kuzbass", "Kouzbass", "coal", 54.00, 87.00, "RUS", 1, 2.0, 120),
    mine("bowen", "Bassin de Bowen", "coal", -22.50, 148.30, "AUS", 1, 3.0, 100),
    mine("hunter", "Hunter Valley", "coal", -32.40, 151.00, "AUS", 2),
    mine("witbank", "Witbank", "coal", -25.87, 29.23, "ZAF", 2),
    mine("ruhr", "Ruhr", "coal", 51.50, 7.20, "DEU", 2, 2.0, 0),
    mine("silesie", "Haute-Silesie", "coal", 50.25, 18.90, "POL", 2),
    mine("donbass", "Donbass", "coal", 48.00, 38.00, "UKR", 2, 2.0, 10),
    mine("nord_pas_de_calais", "Bassin du Nord", "coal", 50.43, 2.83, "FRA", 3, 2.5, 10),
    mine("sarre", "Sarre", "coal", 49.35, 6.95, "DEU", 3),
    mine("galles", "Pays de Galles", "coal", 51.70, -3.40, "GBR", 3),
    mine("yorkshire", "Yorkshire", "coal", 53.60, -1.40, "GBR", 3),
    mine("asturies", "Asturies", "coal", 43.25, -5.75, "ESP", 3),
    mine("ekibastuz", "Ekibastouz", "coal", 51.72, 75.32, "KAZ", 2),
    mine("kalimantan", "Kalimantan oriental", "coal", -0.50, 117.20, "IDN", 2),
    mine("cerrejon", "El Cerrejon", "coal", 11.10, -72.60, "COL", 2),
    mine("tavan_tolgoi", "Tavan Tolgoi", "coal", 43.60, 105.50, "MNG", 2),
    mine("moatize", "Moatize", "coal", -16.10, 33.75, "MOZ", 3),
    mine("zonguldak", "Zonguldak", "coal", 41.30, 31.90, "TUR", 3),
    mine("quang_ninh", "Quang Ninh", "coal", 21.10, 107.10, "VNM", 3),
    mine("elk_valley", "Elk Valley", "coal", 49.90, -114.90, "CAN", 3),
    mine("garzweiler", "Garzweiler", "lignite", 51.05, 6.45, "DEU", 2),
    mine("lusace", "Lusace", "lignite", 51.55, 14.35, "DEU", 3),
    mine("belchatow", "Belchatow", "lignite", 51.25, 19.33, "POL", 3),
    mine("latrobe", "Latrobe Valley", "lignite", -38.20, 146.40, "AUS", 3),
    mine("maritsa", "Maritsa", "lignite", 42.15, 25.90, "BGR", 3),
    # -- Uranium, thorium, terres rares -------------------------------------------------------------------------
    mine("athabasca_uranium", "Cigar Lake et McArthur River", "uranium", 57.85, -104.80, "CAN", 1, 1.6, 30),
    mine("chu_sarysu", "Chu-Sarysu (Inkai)", "uranium", 44.50, 68.50, "KAZ", 1, 2.0, 150),
    mine("rossing", "Rossing", "uranium", -22.48, 15.03, "NAM", 2),
    mine("arlit", "Arlit", "uranium", 18.74, 7.39, "NER", 2),
    mine("ranger", "Ranger", "uranium", -12.68, 132.92, "AUS", 3),
    mine("elliot_lake", "Elliot Lake", "uranium", 46.38, -82.65, "CAN", 3),
    mine("wismut", "Ronneburg (Wismut)", "uranium", 50.86, 12.17, "DEU", 3),
    mine("pribram", "Pribram", "uranium", 49.69, 14.01, "CZE", 3),
    mine("krasnokamensk", "Krasnokamensk", "uranium", 50.10, 118.03, "RUS", 2),
    mine("uchkuduk", "Uchkuduk", "uranium", 42.15, 63.55, "UZB", 3),
    mine("limousin", "Limousin", "uranium", 46.05, 1.45, "FRA", 3),
    mine("shinkolobwe", "Shinkolobwe", "uranium", -11.04, 26.58, "COD", 3),
    mine("chavara", "Chavara", "thorium", 8.95, 76.55, "IND", 2, 2.5, 120, extras=[("titanium", 0.10)]),
    mine("lemhi", "Lemhi Pass", "thorium", 44.97, -113.43, "USA", 3),
    mine("espirito_santo", "Sables de Guarapari", "thorium", -20.60, -40.60, "BRA", 3),
    mine("bayan_obo", "Bayan Obo", "rare_earth", 41.78, 109.97, "CHN", 1, extras=[("iron", 0.15)]),
    mine("mountain_pass", "Mountain Pass", "rare_earth", 35.48, -115.53, "USA", 2),
    mine("mount_weld", "Mount Weld", "rare_earth", -28.86, 122.55, "AUS", 2),
    mine("kvanefjeld", "Kvanefjeld", "rare_earth", 60.97, -45.98, "GRL", 3),
    mine("lovozero", "Lovozero", "rare_earth", 67.85, 34.65, "RUS", 3),
    mine("norra_karr", "Norra Karr", "rare_earth", 58.10, 14.67, "SWE", 3),
    # -- Lithium, cobalt, coltan --------------------------------------------------------------------------------
    mine("atacama_lithium", "Salar d'Atacama", "lithium", -23.50, -68.25, "CHL", 1),
    mine("uyuni", "Salar d'Uyuni", "lithium", -20.13, -67.49, "BOL", 1, 1.5, 0),
    mine("hombre_muerto", "Hombre Muerto", "lithium", -25.40, -67.10, "ARG", 2),
    mine("greenbushes", "Greenbushes", "lithium", -33.86, 116.06, "AUS", 1),
    mine("pilgangoora", "Pilgangoora", "lithium", -21.03, 118.92, "AUS", 3),
    mine("yichun", "Yichun", "lithium", 27.80, 114.40, "CHN", 3),
    mine("bikita", "Bikita", "lithium", -20.08, 31.62, "ZWE", 3),
    mine("barroso", "Barroso", "lithium", 41.65, -7.70, "PRT", 3),
    mine("bou_azzer", "Bou Azzer", "cobalt", 30.52, -6.92, "MAR", 3),
    mine("moa", "Moa", "cobalt", 20.60, -74.90, "CUB", 3),
    mine("rubaya", "Rubaya", "coltan", -1.55, 28.88, "COD", 2),
    mine("kenticha", "Kenticha", "coltan", 5.45, 38.95, "ETH", 3),
    mine("pitinga", "Pitinga", "coltan", -0.80, -60.10, "BRA", 3),
    # -- Bauxite, titane ----------------------------------------------------------------------------------------
    mine("weipa", "Weipa", "aluminium", -12.67, 141.87, "AUS", 1, 2.0, 90),
    mine("boke", "Boke (Sangaredi)", "aluminium", 11.10, -13.80, "GIN", 1),
    mine("trombetas", "Trombetas", "aluminium", -1.47, -56.38, "BRA", 2),
    mine("jamaique", "Jamaique", "aluminium", 18.15, -77.30, "JAM", 3),
    mine("gove", "Gove", "aluminium", -12.20, 136.80, "AUS", 3),
    mine("huntly", "Huntly", "aluminium", -32.60, 116.10, "AUS", 3),
    mine("les_baux", "Les Baux", "aluminium", 43.74, 4.80, "FRA", 3),
    mine("moengo", "Moengo", "aluminium", 5.60, -54.40, "SUR", 3),
    mine("severouralsk", "Severouralsk", "aluminium", 60.15, 59.95, "RUS", 3),
    mine("panchpatmali", "Panchpatmali", "aluminium", 18.80, 82.90, "IND", 3),
    mine("richards_bay", "Richards Bay", "titanium", -28.75, 32.05, "ZAF", 2),
    mine("tellnes", "Tellnes", "titanium", 58.33, 6.42, "NOR", 2),
    mine("lac_tio", "Lac Tio", "titanium", 50.55, -63.40, "CAN", 2),
    mine("moma", "Moma", "titanium", -16.70, 39.20, "MOZ", 3),
    mine("irshansk", "Irshansk", "titanium", 50.70, 28.60, "UKR", 3),
    # -- Plomb et zinc ------------------------------------------------------------------------------------------
    mine("red_dog", "Red Dog", "zinc", 68.07, -162.87, "USA", 1, extras=[("lead", 0.12)]),
    mine("broken_hill", "Broken Hill", "lead", -31.95, 141.45, "AUS", 2, extras=[("zinc", 0.10)]),
    mine("rampura_agucha", "Rampura Agucha", "zinc", 25.83, 74.73, "IND", 2, extras=[("lead", 0.05)]),
    mine("navan", "Navan", "zinc", 53.67, -6.72, "IRL", 3, extras=[("lead", 0.05)]),
    mine("kidd_creek", "Kidd Creek", "zinc", 48.69, -81.37, "CAN", 3, extras=[("copper", 0.05)]),
    mine("skorpion", "Skorpion", "zinc", -27.84, 16.60, "NAM", 3),
    mine("viburnum", "Viburnum Trend", "lead", 37.60, -91.10, "USA", 2, 2.0, 90),
    mine("ridder", "Ridder", "lead", 50.35, 83.50, "KAZ", 3, extras=[("zinc", 0.08)]),
    mine("penasquito", "Penasquito", "zinc", 24.65, -101.70, "MEX", 3, extras=[("lead", 0.06)]),
    mine("olkusz", "Olkusz", "zinc", 50.28, 19.57, "POL", 3, extras=[("lead", 0.05)]),
    # -- Tungstene, fluorine, soufre, salpetre, beryllium, amiante, quartz ---------------------------------------
    mine("dayu", "Dayu", "tungsten", 25.40, 114.30, "CHN", 2),
    mine("mittersill", "Mittersill", "tungsten", 47.25, 12.47, "AUT", 3),
    mine("panasqueira", "Panasqueira", "tungsten", 40.15, -7.75, "PRT", 3),
    mine("sangdong", "Sangdong", "tungsten", 37.14, 128.84, "KOR", 3),
    mine("cantung", "Cantung", "tungsten", 61.97, -128.25, "CAN", 3),
    mine("nui_phao", "Nui Phao", "tungsten", 21.65, 105.65, "VNM", 3),
    mine("tyrnyauz", "Tyrnyaouz", "tungsten", 43.40, 42.90, "RUS", 3),
    mine("las_cuevas", "Las Cuevas", "fluorite", 21.65, -100.45, "MEX", 3),
    mine("bor_undur", "Bor-Undur", "fluorite", 46.30, 109.30, "MNG", 3),
    mine("vergenoeg", "Vergenoeg", "fluorite", -25.27, 28.55, "ZAF", 3),
    mine("kerio", "Vallee de Kerio", "fluorite", 0.60, 35.60, "KEN", 3),
    mine("sicile", "Soufrieres de Sicile", "sulfur", 37.50, 14.00, "ITA", 3),
    mine("tarnobrzeg", "Tarnobrzeg", "sulfur", 50.57, 21.68, "POL", 3),
    mine("louisiane", "Domes de Louisiane", "sulfur", 30.10, -93.20, "USA", 3),
    mine("mishraq", "Mishraq", "sulfur", 36.00, 43.30, "IRQ", 3),
    mine("salitreras", "Salitreras d'Atacama", "niter", -20.20, -69.80, "CHL", 2, 2.0, 90),
    mine("spor_mountain", "Spor Mountain", "beryllium", 39.72, -113.23, "USA", 2),
    mine("asbest", "Asbest", "asbestos", 57.00, 61.45, "RUS", 2),
    mine("thetford", "Thetford Mines", "asbestos", 46.10, -71.30, "CAN", 3),
    mine("spruce_pine", "Spruce Pine", "quartz", 35.92, -82.07, "USA", 2),
    mine("minas_quartz", "Diamantina", "quartz", -18.25, -43.60, "BRA", 2),
    mine("arkansas", "Hot Springs", "quartz", 34.50, -93.05, "USA", 2),
    mine("madagascar", "Hauts plateaux", "quartz", -19.80, 46.80, "MDG", 3),
    # -- Petrole et gaz -------------------------------------------------------------------------------------------
    field("ghawar", "Ghawar", "oil", 25.20, 49.30, "SAU", 12, 75, 0, 1.2),
    field("burgan", "Burgan", "oil", 28.95, 47.95, "KWT", 22, 22, 0, 1.0),
    field("safaniya", "Safaniya", "oil", 27.95, 48.80, "~SAU", 14, 28, 30, 0.9),
    field("rumaila", "Rumaila", "oil", 30.40, 47.35, "IRQ", 10, 40, 0, 1.0),
    field("kirkuk", "Kirkouk", "oil", 35.47, 44.39, "IRQ", 8, 50, 130, 0.8),
    field("ahvaz", "Ahvaz", "oil", 31.30, 48.70, "IRN", 10, 35, 120, 0.9),
    field("gachsaran", "Gachsaran", "oil", 30.36, 50.80, "IRN", 8, 30, 130, 0.7),
    field("south_pars", "South Pars / North Dome", "gas", 26.60, 52.00, "~QAT", 40, 30, 0, 1.1),
    field("upper_zakum", "Upper Zakum", "oil", 24.85, 53.65, "~ARE", 18, 14, 0, 0.8),
    field("samotlor", "Samotlor", "oil", 61.15, 76.70, "RUS", 25, 25, 0, 0.9),
    field("priobskoye", "Priobskoye", "oil", 61.30, 70.80, "RUS", 20, 20, 0, 0.7),
    field("urengoy", "Ourengoi", "gas", 66.00, 77.50, "RUS", 15, 45, 0, 1.0),
    field("yamburg", "Iambourg", "gas", 67.90, 75.50, "RUS", 20, 20, 0, 0.8),
    field("romashkino", "Romachkino", "oil", 54.85, 52.30, "RUS", 22, 22, 0, 0.8),
    field("tengiz", "Tengiz", "oil", 46.15, 53.40, "KAZ", 16, 16, 0, 0.8),
    field("kashagan", "Kashagan", "oil", 46.15, 51.55, "~KAZ", 20, 14, 0, 0.8),
    field("permian", "Bassin permien", "oil", 31.90, -102.30, "USA", 50, 45, 0, 0.9),
    field("east_texas", "East Texas", "oil", 32.40, -94.80, "USA", 8, 35, 0, 0.6),
    field("prudhoe", "Prudhoe Bay", "oil", 70.30, -148.40, "USA", 24, 12, 0, 0.8),
    field("bakken", "Bakken", "oil", 47.90, -103.40, "USA", 35, 30, 0, 0.6),
    field("gulf_mexico", "Golfe du Mexique", "oil", 27.80, -90.50, "~USA", 40, 16, 0, 0.8),
    field("cantarell", "Cantarell", "oil", 19.40, -92.20, "~MEX", 16, 16, 0, 0.8),
    field("orinoco", "Ceinture de l'Orenoque", "oil", 8.50, -64.00, "VEN", 70, 12, 0, 1.0),
    field("maracaibo", "Lac de Maracaibo", "oil", 10.20, -71.40, "VEN", 14, 28, 0, 0.8),
    field("brent", "Brent", "oil", 61.05, 1.70, "~GBR", 10, 18, 0, 0.6),
    field("forties", "Forties", "oil", 57.73, 0.97, "~GBR", 10, 10, 0, 0.6),
    field("ekofisk", "Ekofisk", "oil", 56.55, 3.21, "~NOR", 8, 8, 0, 0.5),
    field("troll", "Troll", "gas", 60.65, 3.72, "~NOR", 12, 16, 0, 0.7),
    field("groningen", "Groningue", "gas", 53.25, 6.75, "NLD", 14, 12, 0, 0.7),
    field("hassi_messaoud", "Hassi Messaoud", "oil", 31.70, 6.05, "DZA", 22, 22, 0, 0.8),
    field("hassi_rmel", "Hassi R'Mel", "gas", 32.93, 3.27, "DZA", 22, 16, 0, 0.8),
    field("sarir", "Sarir", "oil", 27.60, 22.50, "LBY", 18, 14, 0, 0.7),
    field("zelten", "Zelten", "oil", 28.95, 19.75, "LBY", 14, 14, 0, 0.6),
    field("niger_delta", "Delta du Niger", "oil", 5.00, 6.30, "NGA", 30, 18, 0, 0.8),
    field("daqing", "Daqing", "oil", 46.60, 125.00, "CHN", 10, 40, 0, 0.8),
    field("tarim", "Bassin du Tarim", "oil", 41.20, 83.90, "CHN", 25, 15, 0, 0.6),
    field("bohai", "Golfe de Bohai", "oil", 38.60, 119.30, "~CHN", 18, 14, 0, 0.6),
    field("santos", "Pre-sal de Santos", "oil", -25.50, -43.00, "~BRA", 30, 18, 0, 0.9),
    field("campos", "Bassin de Campos", "oil", -22.40, -40.10, "~BRA", 16, 16, 0, 0.7),
    field("minas", "Minas (Sumatra)", "oil", 0.75, 101.45, "IDN", 14, 14, 0, 0.6),
    field("galkynysh", "Galkynysh", "gas", 37.30, 62.30, "TKM", 24, 18, 0, 0.9),
    field("azeri", "Azeri-Chirag-Guneshli", "oil", 40.00, 51.00, "~AZE", 22, 8, 125, 0.7),
    field("leduc", "Leduc", "oil", 53.27, -113.55, "CAN", 12, 12, 0, 0.5),
    field("angola", "Offshore angolais", "oil", -7.50, 12.20, "~AGO", 14, 20, 0, 0.6),
    field("bombay_high", "Mumbai High", "oil", 19.50, 71.30, "~IND", 12, 14, 0, 0.6),
    field("sakhaline", "Sakhaline", "oil", 52.70, 143.60, "~RUS", 10, 24, 0, 0.6),
    sands("athabasca", "Sables bitumineux de l'Athabasca", 57.00, -111.50, "CAN", 45, 55, 0.8),
]

# Les minerais retires de chaque chunk de la carte avant les gisements : ceux que la carte Earth24k porte
# deja (WorldPainter y a seme du fer, de l'or, du diamant, du charbon et du quartz du Nether a peu pres
# partout), ceux de NTM NEXT qui echappent a son interrupteur (son petrole), ceux que le jeu aurait
# encore poses, et ceux des gisements eux-memes : un chunk resservi repart de zero.
CLEAR_EXTRA = ["minecraft:nether_quartz_ore", "hbm:ore_oil", "hbm:ore_bedrock_oil", "hbm:ore_oil_sand"]


def clear_list():
    blocks = set(CLEAR_EXTRA)
    for entry in DEPOSITS:
        if entry["id"].startswith("zone_"):
            continue
        blocks.add(entry["ore"])
        if entry.get("deepslate"):
            blocks.add(entry["deepslate"])
    for body in BODIES:
        if body["kind"] in ("ore", "pipe"):
            blocks.add(body["ore"])
            if body.get("deepslate"):
                blocks.add(body["deepslate"])
            for extra in body.get("extras", ()):
                blocks.add(extra["ore"])
                if extra.get("deepslate"):
                    blocks.add(extra["deepslate"])
    return sorted(blocks)


def check_grid(path_grid, path_countries):
    """Verifie que chaque gisement geant tombe dans son pays sur la grille de la carte (ou en mer, '~')."""
    import math
    import numpy
    grid = numpy.load(path_grid)
    with open(path_countries, encoding="utf-8") as fh:
        codes = {entry["id"]: entry["code"] for entry in json.load(fh)}
    bad = 0
    for body in BODIES:
        lat, lon = body["latitude"], body["longitude"]
        x = 1.33 + lon * 68.6755
        z = 700.35 - math.degrees(1.25 * math.log(math.tan(math.pi / 4 + 0.4 * math.radians(lat)))) * 68.6755
        col, row = int((x + 12368) // 8), int((z + 7136) // 8)
        found = codes.get(int(grid[row, col]) & 0x7FFF) if int(grid[row, col]) else None
        want = body["country"].lstrip("~")
        if body["country"].startswith("~"):
            ok = found in (None, want)
        else:
            ok = found == want
        if not ok:
            bad += 1
            print("HORS PAYS : %-20s voulu %s, trouve %s (x %d, z %d)" % (body["id"], body["country"], found, x, z))
    print("%d gisements geants, %d hors de leur pays" % (len(BODIES), bad))


def main():
    known = None
    args = [arg for arg in sys.argv[1:] if not arg.startswith("--")]
    if args:
        with open(args[0], encoding="utf-8") as fh:
            data = json.load(fh)
        known = {entry["code"] for entry in (data["countries"] if isinstance(data, dict) else data)}
    if len(args) > 1:
        check_grid(args[1], args[0])
    for entry in DEPOSITS:
        for code in entry["countries"]:
            if known is not None and code not in known:
                raise SystemExit("gisement %s : pays inconnu de la carte : %s" % (entry["id"], code))
        for area in entry.get("boxes", ()):
            if not (area["south"] < area["north"] and area["west"] < area["east"]):
                raise SystemExit("gisement %s : rectangle a l'envers : %s" % (entry["id"], area))
    ids = [body["id"] for body in BODIES]
    if len(ids) != len(set(ids)):
        raise SystemExit("gisements geants : identifiant en double")
    for body in BODIES:
        if known is not None and body["country"].lstrip("~") not in known:
            raise SystemExit("gisement geant %s : pays inconnu : %s" % (body["id"], body["country"]))
        if any(ord(ch) > 127 for ch in body["name"] + body["resource"]):
            raise SystemExit("gisement geant %s : accent dans le nom" % body["id"])
    payload = {
        "_comment": "Les gisements de la Terre, GENERE par tools/earth_deposits.py. Ils ne servent que dans un monde "
                    "dont un datapack decrit la carte (earth.json). 'deposits' : des filons par pays ('veins' : "
                    "filons par chunk, en moyenne ; 'radius' : une poche ronde au lieu d'un filon). 'bodies' : les "
                    "gisements geants, de vraies mines et de vrais champs. 'clear' : les minerais retires de chaque "
                    "chunk avant tout. Change 'ground_version' pour tout resservir (nettoyage, gisements geants, "
                    "filons), 'version' pour resservir les seuls filons.",
        "version": 1,
        "ground_version": 1,
        "per_tick": 6,
        "clear": clear_list(),
        "deposits": DEPOSITS,
        "bodies": BODIES,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    known_sites = {entry["id"] for entry in SITES}
    for entry in DEPOSITS:
        for name in entry.get("sites", ()):
            if name not in known_sites:
                raise SystemExit("gisement %s : lieu inconnu : %s" % (entry["id"], name))
    with open(SITES_OUT, "w", encoding="utf-8") as fh:
        json.dump({
            "_comment": "Les lieux de la Terre, GENERE par tools/earth_deposits.py : zones irradiees et bases de "
                        "lancement, par latitude et longitude. Ils ne servent que dans un monde dont un datapack "
                        "decrit la carte. 'launch_blocks' : les blocs qui ne se posent que sur une base de lancement. "
                        "Change 'version' pour resservir les chunks deja visites.",
            "version": 1,
            "sites": SITES,
            "launch_blocks": LAUNCH_BLOCKS,
        }, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("%d gisements et %d lieux ecrits." % (len(DEPOSITS), len(SITES)))


if __name__ == "__main__":
    main()
