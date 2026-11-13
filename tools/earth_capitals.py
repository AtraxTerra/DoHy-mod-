#!/usr/bin/env python3
"""Ecrit la liste des capitales de la Terre : data/dohy/earth/capitals.json.

Chaque pays de la grille recoit sa capitale reelle (Natural Earth, domaine
public : la ville de classe « Admin-0 capital », a defaut la plus grande ville
du pays), sa population, et une taille de ville jouable en chunks :

    moins de 300 000 habitants   3 x 3 chunks (48 blocs)
    moins de 1 million           5 x 5 chunks, sans les coins
    moins de 5 millions          7 x 7 chunks, sans les coins
    au-dela                      9 x 9 chunks, sans les coins

A 1 bloc pour 1,1 km, Paris tiendrait en une dizaine de blocs : les villes sont
agrandies dix fois, et se genent la ou les capitales sont proches. Le script les
place des plus peuplees aux moins peuplees : une ville qui en chevauche une
autre (ou un lieu de sites.json) est decalee d'au plus six chunks dans son pays,
puis retrecie ; faute de place, la Cite du Vatican, dans Rome, n'a pas de ville a elle. Chaque centre est un
chunk dont la case est de la terre du bon pays, au plus pres de la ville reelle.

    python3 -I tools/earth_capitals.py earth.json countries.bin \\
        ne_10m_populated_places.geojson ne_10m_admin_0_countries.geojson

earth.json et countries.bin sont ceux de la carte (le datapack) ; les deux
fichiers Natural Earth viennent de github.com/nvkelso/natural-earth-vector.
"""

import gzip
import json
import math
import os
import re
import sys
import unicodedata

EARTH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "dohy",
                     "earth")
OUT = os.path.join(EARTH, "capitals.json")
SITES = os.path.join(EARTH, "sites.json")

# Les codes de la grille sont les ADM0_A3 de Natural Earth ; quelques villes portent un autre code.
ALIASES = {"SDS": "SSD"}

# Un monument par grande capitale, dessine pour le jeu ; les autres ont un palais national.
MONUMENTS = {
    "FRA": "lattice_tower",
    "GBR": "clock_tower",
    "ITA": "amphitheatre",
    "RUS": "onion_domes",
    "USA": "obelisk_capitol",
    "CHN": "imperial_gate",
    "EGY": "pyramids",
    "DEU": "column_gate",
    "GRC": "acropolis",
    "IND": "triumph_arch",
    "JPN": "pagoda",
}

CLASSES = ["Admin-0 capital", "Admin-0 capital alt", "Admin-0 region capital", "Admin-1 region capital",
           "Admin-1 capital", "Populated place"]

MAX_SHIFT = 6


def slug(text):
    text = unicodedata.normalize("NFD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def style(props):
    region = props.get("REGION_UN", "")
    sub = props.get("SUBREGION", "")
    if region == "Europe":
        return "europe"
    if region == "Africa":
        return "orient" if sub == "Northern Africa" else "afrique"
    if region == "Asia":
        return "orient" if sub in ("Western Asia", "Central Asia") else "asie"
    if region == "Americas":
        return "ameriques" if sub == "Northern America" else "latam"
    if region == "Oceania":
        return "ameriques" if sub == "Australia and New Zealand" else "latam"
    return "ameriques"


def radius(population):
    if population < 300_000:
        return 1
    if population < 1_000_000:
        return 2
    if population < 5_000_000:
        return 3
    return 4


def offsets(r):
    return [(dx, dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= r * r + r]


def main():
    if len(sys.argv) != 5:
        print(__doc__)
        sys.exit(2)
    earth = json.load(open(sys.argv[1], encoding="utf-8"))
    grid = earth["grid"]
    width, height, cell = grid["width"], grid["height"], grid["cell"]
    min_x, min_z = grid["min_x"], grid["min_z"]
    bpd, origin_x, equator_z = earth["blocks_per_degree"], earth["origin_x"], earth["equator_z"]
    raw = gzip.open(sys.argv[2]).read()
    cells = [int.from_bytes(raw[i:i + 2], "big") for i in range(0, len(raw), 2)]

    def land(column, row):
        if not (0 <= column < width and 0 <= row < height):
            return 0
        value = cells[row * width + column]
        return value & 0x7FFF if value and not value & 0x8000 else 0

    def block_x(lon):
        return origin_x + lon * bpd

    def block_z(lat):
        r = math.radians(lat)
        return equator_z - math.degrees(1.25 * math.log(math.tan(math.pi / 4 + 0.4 * r))) * bpd

    def lon_of(x):
        return (x - origin_x) / bpd

    def lat_of(z):
        p = math.radians((equator_z - z) / bpd)
        return math.degrees(2.5 * math.atan(math.exp(0.8 * p)) - 0.625 * math.pi)

    def country_at(x, z):
        return land(int((x - min_x) // cell), int((z - min_z) // cell))

    def solid(x, z, country):
        """Le chunk qui contient ce point a sa case en terre du pays, entouree de terre."""
        column, row = int((x - min_x) // cell), int((z - min_z) // cell)
        if land(column, row) != country:
            return False
        return all(land(column + dc, row + dr) for dc in (-1, 0, 1) for dr in (-1, 0, 1))

    places = [f["properties"] for f in json.load(open(sys.argv[3], encoding="utf-8"))["features"]]
    regions = {f["properties"]["ADM0_A3"]: f["properties"]
               for f in json.load(open(sys.argv[4], encoding="utf-8"))["features"]}
    sites = json.load(open(SITES, encoding="utf-8"))["sites"] if os.path.exists(SITES) else []

    count = [0] * 65536
    for value in cells:
        if value and not value & 0x8000:
            count[value & 0x7FFF] += 1

    wanted = []
    for country in earth["countries"]:
        if count[country["id"]] == 0:
            continue
        code = ALIASES.get(country["code"], country["code"])
        candidates = [p for p in places if p["ADM0_A3"] == code]
        if not candidates:
            continue
        candidates.sort(key=lambda p: (CLASSES.index(p["FEATURECLA"]) if p["FEATURECLA"] in CLASSES else 9,
                                       -p["POP_MAX"]))
        place = candidates[0]
        wanted.append((country, place))
    wanted.sort(key=lambda item: -item[1]["POP_MAX"])

    taken = set()
    for site in sites:
        x, z = block_x(site["longitude"]), block_z(site["latitude"])
        reach = site["radius"] + 16
        for cx in range(int((x - reach) // 16), int((x + reach) // 16) + 1):
            for cz in range(int((z - reach) // 16), int((z + reach) // 16) + 1):
                if math.hypot(cx * 16 + 8 - x, cz * 16 + 8 - z) <= reach + 12:
                    taken.add((cx, cz))

    out = []
    absorbed = []
    shifted = []
    for country, place in wanted:
        x, z = block_x(place["LONGITUDE"]), block_z(place["LATITUDE"])
        home_cx, home_cz = int(x // 16), int(z // 16)
        r0 = radius(place["POP_MAX"])
        chosen = None
        for r in range(r0, 0, -1):
            # au plus pres de la vraie ville, sur une case du pays ; une case entouree de terre d'abord
            # (la ville ne tombe pas au bord de l'eau), une case de cote s'il n'y a rien d'autre (les iles)
            rings = []
            for dx in range(-MAX_SHIFT, MAX_SHIFT + 1):
                for dz in range(-MAX_SHIFT, MAX_SHIFT + 1):
                    px, pz = (home_cx + dx) * 16 + 8, (home_cz + dz) * 16 + 8
                    if country_at(px, pz) != country["id"]:
                        continue
                    penalty = 0 if not (dx or dz) or solid(px, pz, country["id"]) else 9
                    rings.append((dx * dx + dz * dz + penalty, dx, dz))
            rings.sort()
            for _, dx, dz in rings:
                cx, cz = home_cx + dx, home_cz + dz
                chunks = [(cx + ox, cz + oz) for ox, oz in offsets(r)]
                if any((a + i, b + j) in taken for a, b in chunks for i in (-1, 0, 1) for j in (-1, 0, 1)):
                    continue
                chosen = (cx, cz, r, chunks, dx, dz)
                break
            if chosen:
                break
        if not chosen:
            absorbed.append(place["NAME_FR"] or place["NAME"])
            continue
        cx, cz, r, chunks, dx, dz = chosen
        taken.update(chunks)
        if dx or dz or r != r0:
            shifted.append("%s (%+d,%+d chunks, rayon %d au lieu de %d)" % (place["NAME_FR"] or place["NAME"], dx, dz,
                                                                            r, r0))
        center_x, center_z = cx * 16 + 8, cz * 16 + 8
        entry = {
            "id": slug(place["NAMEASCII"] or place["NAME"]),
            "name": place["NAME_FR"] or place["NAME"],
            "country": country["code"],
            "latitude": round(lat_of(center_z), 5),
            "longitude": round(lon_of(center_x), 5),
            "population": int(place["POP_MAX"]),
            "radius": r,
            "style": style(regions.get(country["code"], regions.get(ALIASES.get(country["code"], ""), {}))),
        }
        if country["code"] in MONUMENTS:
            entry["monument"] = MONUMENTS[country["code"]]
        out.append(entry)

    ids = set()
    for entry in out:
        base = entry["id"]
        n = 2
        while entry["id"] in ids:
            entry["id"] = "%s_%d" % (base, n)
            n += 1
        ids.add(entry["id"])
    out.sort(key=lambda e: e["id"])
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"version": 1, "cities": out}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print("%d capitales ecrites, %d chunks de ville." % (len(out), sum(len(offsets(e["radius"])) for e in out)))
    print("Decalees ou retrecies : %d — %s" % (len(shifted), "; ".join(shifted)))
    print("Sans ville (pas de place) : %s" % ", ".join(absorbed))


if __name__ == "__main__":
    main()
