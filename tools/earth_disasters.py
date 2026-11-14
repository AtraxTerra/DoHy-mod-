#!/usr/bin/env python3
"""Ecrit la table des catastrophes de la Terre : data/dohy/earth/disasters.json.

Quatre familles, toutes donnees en latitude et longitude (elles valent pour
toute carte calee sur le globe) :

- les volcans actifs (un cone permanent avec son cratere, et ses eruptions) ;
- les zones sismiques : des lignes de failles ou de fosses, avec une demi-largeur
  en kilometres ;
- les bassins cycloniques : ou naissent les tempetes, leur cap, la latitude ou
  elles remontent, leur saison et la region qu'elles balaient ;
- les regions de mousson : un cercle, et sa saison des pluies.

    python3 -I tools/earth_disasters.py [earth.json countries.bin]

Avec le calage et la grille des pays d'une carte en arguments, chaque volcan
est pose sur la case de terre la plus proche de son pays (a 30 km au plus) : un
volcan d'ile trop petite pour la grille est retire, avec un message. La liste
est faite pour le jeu, pas pour un atlas.
"""

import gzip
import json
import math
import os
import struct
import sys

EARTH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "dohy",
                     "earth")
OUT = os.path.join(EARTH, "disasters.json")

# id, nom, pays (code a trois lettres de Natural Earth), latitude, longitude
VOLCANOES = [
    ("etna", "Etna", "ITA", 37.751, 14.993),
    ("vesuve", "Vesuve", "ITA", 40.821, 14.426),
    ("stromboli", "Stromboli", "ITA", 38.789, 15.213),
    ("santorin", "Santorin", "GRC", 36.404, 25.396),
    ("teide", "Teide", "ESP", 28.272, -16.642),
    ("hekla", "Hekla", "ISL", 63.983, -19.666),
    ("fuji", "Mont Fuji", "JPN", 35.361, 138.727),
    ("sakurajima", "Sakurajima", "JPN", 31.585, 130.657),
    ("krakatoa", "Krakatoa", "IDN", -6.102, 105.423),
    ("merapi", "Merapi", "IDN", -7.540, 110.446),
    ("tambora", "Tambora", "IDN", -8.250, 118.000),
    ("pinatubo", "Pinatubo", "PHL", 15.130, 120.350),
    ("mayon", "Mayon", "PHL", 13.257, 123.685),
    ("popocatepetl", "Popocatepetl", "MEX", 19.023, -98.622),
    ("colima", "Colima", "MEX", 19.514, -103.620),
    ("saint_helens", "Mont Saint Helens", "USA", 46.191, -122.196),
    ("kilauea", "Kilauea", "USA", 19.421, -155.287),
    ("arenal", "Arenal", "CRI", 10.463, -84.703),
    ("masaya", "Masaya", "NIC", 11.984, -86.161),
    ("cotopaxi", "Cotopaxi", "ECU", -0.677, -78.436),
    ("villarrica", "Villarrica", "CHL", -39.420, -71.930),
    ("nyiragongo", "Nyiragongo", "COD", -1.520, 29.250),
    ("erta_ale", "Erta Ale", "ETH", 13.600, 40.670),
    ("lengai", "Ol Doinyo Lengai", "TZA", -2.764, 35.914),
    ("klioutchevskoi", "Klioutchevskoi", "RUS", 56.056, 160.642),
    ("ruapehu", "Ruapehu", "NZL", -39.281, 175.564),
    ("fournaise", "Piton de la Fournaise", "FRA", -21.244, 55.708),
    ("pelee", "Montagne Pelee", "FRA", 14.809, -61.165),
]

# id, nom, points (latitude, longitude), demi-largeur en km, magnitude la plus forte, poids
ZONES = [
    ("japon", "Japon", [(45.0, 142.5), (41.5, 141.5), (38.3, 141.0), (35.5, 139.8), (33.5, 133.5), (31.5, 130.8)],
     220, 9.0, 3.0),
    ("kamtchatka", "Kamtchatka et Kouriles", [(56.0, 162.0), (52.0, 158.5), (47.0, 153.0)], 180, 9.0, 1.0),
    ("taiwan", "Taiwan", [(25.0, 121.6), (22.0, 120.8)], 120, 7.7, 1.0),
    ("philippines", "Philippines", [(18.5, 121.0), (14.5, 121.0), (10.5, 124.5), (6.5, 126.0)], 180, 8.0, 2.0),
    ("indonesie", "Indonesie", [(5.0, 95.0), (-1.0, 99.5), (-6.5, 106.0), (-8.0, 112.0), (-8.5, 118.0)], 200, 9.1,
     3.0),
    ("papouasie", "Papouasie", [(-4.0, 141.0), (-6.0, 147.5), (-5.5, 152.0)], 180, 8.2, 1.0),
    ("nouvelle_zelande", "Nouvelle-Zelande", [(-37.0, 176.0), (-41.5, 174.5), (-45.0, 168.0)], 150, 8.0, 1.0),
    ("chili", "Chili", [(-18.0, -70.3), (-27.0, -70.8), (-33.5, -71.8), (-38.0, -73.4), (-43.0, -73.8)], 200, 9.5,
     3.0),
    ("perou", "Perou et Equateur", [(-1.0, -80.3), (-6.0, -80.5), (-12.0, -77.2), (-16.5, -73.0)], 180, 8.8, 2.0),
    ("amerique_centrale", "Amerique centrale", [(7.0, -77.5), (9.5, -84.0), (12.5, -87.5), (14.5, -91.5)], 160,
     8.0, 2.0),
    ("mexique", "Mexique", [(16.0, -95.0), (17.0, -100.5), (19.0, -104.5)], 180, 8.2, 2.0),
    ("californie", "Californie", [(32.5, -115.5), (34.5, -118.5), (37.5, -122.3), (40.3, -124.3)], 140, 8.0, 2.0),
    ("alaska", "Alaska", [(61.0, -147.5), (58.0, -153.0), (55.0, -161.0)], 200, 9.2, 1.0),
    ("haiti", "Caraibes", [(19.0, -74.5), (18.5, -72.5), (18.5, -68.5)], 120, 7.5, 1.0),
    ("portugal", "Portugal", [(38.7, -9.2), (37.0, -8.5)], 120, 7.5, 0.5),
    ("atlas", "Atlas et Tell", [(36.5, 3.0), (35.0, -1.0), (31.0, -8.0)], 120, 7.3, 1.0),
    ("italie", "Italie", [(44.5, 11.0), (42.5, 13.4), (40.8, 15.3), (38.2, 15.6), (37.5, 14.8)], 120, 7.0, 2.0),
    ("grece", "Grece", [(39.5, 20.5), (37.5, 22.0), (35.3, 25.0), (37.8, 26.8)], 140, 7.0, 1.0),
    ("turquie", "Turquie", [(40.7, 30.0), (39.8, 39.5), (38.0, 38.5), (36.5, 36.2)], 150, 7.8, 2.0),
    ("iran", "Iran", [(36.5, 52.0), (34.0, 47.5), (30.0, 51.5), (27.5, 57.0)], 200, 7.5, 2.0),
    ("hindou_kouch", "Hindou Kouch", [(36.5, 70.8), (34.0, 71.5), (30.0, 67.0)], 150, 7.6, 1.0),
    ("himalaya", "Himalaya", [(34.5, 73.5), (31.0, 78.5), (28.0, 84.5), (27.5, 88.5), (26.5, 93.0)], 180, 8.6, 2.0),
    ("sichuan", "Sichuan et Yunnan", [(32.0, 103.5), (28.0, 102.5), (24.0, 101.0)], 150, 7.9, 1.0),
]


def box(south, north, west, east, heading):
    return {"south": south, "north": north, "west": west, "east": east, "heading": heading}


# Les bassins : la saison est une fenetre de l'annee locale de l'hemisphere (0 ouvre l'hiver, 0,5 l'ete),
# la region (sud, nord, ouest, est) est celle que leurs tempetes balaient.
BASINS = [
    {"id": "atlantique_nord", "name": "Atlantique Nord", "word": "ouragan", "season": [0.55, 0.88], "weight": 3.0,
     "recurve_latitude": 28.0, "area": [8.0, 42.0, -100.0, -25.0],
     "genesis": [box(10.0, 18.0, -55.0, -35.0, 285.0), box(14.0, 21.0, -84.0, -68.0, 300.0)]},
    {"id": "pacifique_nord_est", "name": "Pacifique Nord-Est", "word": "ouragan", "season": [0.55, 0.85],
     "weight": 1.5, "recurve_latitude": 21.0, "area": [8.0, 32.0, -125.0, -85.0],
     "genesis": [box(10.0, 15.0, -104.0, -92.0, 292.0)]},
    {"id": "pacifique_nord_ouest", "name": "Pacifique Nord-Ouest", "word": "typhon", "season": [0.5, 0.9],
     "weight": 3.0, "recurve_latitude": 24.0, "area": [5.0, 45.0, 100.0, 160.0],
     "genesis": [box(8.0, 18.0, 130.0, 152.0, 290.0)]},
    {"id": "indien_nord", "name": "Ocean Indien Nord", "word": "cyclone", "season": [0.4, 0.85], "weight": 1.5,
     "recurve_latitude": 30.0, "area": [6.0, 28.0, 55.0, 100.0],
     "genesis": [box(10.0, 16.0, 85.0, 92.0, 330.0), box(12.0, 18.0, 62.0, 70.0, 305.0)]},
    {"id": "indien_sud_ouest", "name": "Ocean Indien Sud-Ouest", "word": "cyclone", "season": [0.55, 0.85],
     "weight": 1.5, "recurve_latitude": -21.0, "area": [-32.0, -8.0, 30.0, 90.0],
     "genesis": [box(-15.0, -9.0, 55.0, 80.0, 245.0)]},
    {"id": "australie", "name": "Australie et Pacifique Sud", "word": "cyclone", "season": [0.55, 0.85],
     "weight": 1.5, "recurve_latitude": -21.0, "area": [-32.0, -8.0, 110.0, 185.0],
     "genesis": [box(-16.0, -10.0, 148.0, 175.0, 235.0), box(-14.0, -10.0, 118.0, 128.0, 225.0)]},
]

# id, nom, latitude, longitude, rayon en km
MONSOONS = [
    ("inde", "Inde", 22.0, 79.0, 1300),
    ("bengale", "Bangladesh et Birmanie", 22.5, 92.0, 600),
    ("indochine", "Asie du Sud-Est", 15.5, 103.0, 800),
    ("chine_sud", "Chine du Sud", 25.0, 112.0, 800),
    ("afrique_ouest", "Afrique de l'Ouest", 11.0, -2.0, 1400),
    ("australie_nord", "Australie du Nord", -15.0, 133.0, 900),
]

NAMES = ["Alice", "Bruno", "Celine", "Damien", "Elsa", "Fabien", "Gisele", "Hugo", "Ines", "Jules", "Karine", "Leon",
         "Maelle", "Nestor", "Odile", "Pascal", "Rosalie", "Simon", "Tania", "Victor", "Wanda", "Xavier", "Yvette",
         "Zacharie"]

SETTINGS = {
    "check_seconds": 20,
    "quake_minutes": 45,
    "quake_news_minutes": 150,
    "eruption_minutes": 75,
    "eruption_near_blocks": 400,
    "eruption_news_minutes": 300,
    "eruption_warning_seconds": 300,
    "hurricane_minutes": 80,
    "hurricane_target_share": 0.65,
    "monsoon_minutes": 25,
    "restore_hours": 3.0,
    "max_active": 6,
}


class Grid:
    """La grille des pays d'une carte, et son calage (projection de Miller)."""

    WATER = 0x8000

    def __init__(self, earth_path, grid_path):
        with open(earth_path, encoding="utf-8") as handle:
            earth = json.load(handle)
        self.bpd = earth["blocks_per_degree"]
        self.origin_x = earth["origin_x"]
        self.equator_z = earth["equator_z"]
        self.miller = earth.get("projection", "miller") != "equirectangular"
        grid = earth["grid"]
        self.min_x, self.min_z, self.cell = grid["min_x"], grid["min_z"], grid["cell"]
        self.width, self.height = grid["width"], grid["height"]
        self.codes = {country["code"]: country["id"] for country in earth["countries"]}
        with gzip.open(grid_path, "rb") as handle:
            raw = handle.read()
        self.cells = struct.unpack(">%dH" % (self.width * self.height), raw[:self.width * self.height * 2])

    def x(self, longitude):
        return self.origin_x + longitude * self.bpd

    def z(self, latitude):
        projected = latitude
        if self.miller:
            radians = math.radians(max(-89.0, min(89.0, latitude)))
            projected = math.degrees(1.25 * math.log(math.tan(math.pi / 4.0 + 0.4 * radians)))
        return self.equator_z - projected * self.bpd

    def longitude(self, x):
        return (x - self.origin_x) / self.bpd

    def latitude(self, z):
        projected = (self.equator_z - z) / self.bpd
        if not self.miller:
            return projected
        radians = math.radians(projected)
        return math.degrees(2.5 * math.atan(math.exp(0.8 * radians)) - 0.625 * math.pi)

    def value(self, column, row):
        if column < 0 or row < 0 or column >= self.width or row >= self.height:
            return 0
        return self.cells[row * self.width + column]

    def land(self, column, row, country):
        value = self.value(column, row)
        return value != 0 and not value & self.WATER and (country is None or value & 0x7FFF == country)

    def place(self, latitude, longitude, code, limit_km=30.0):
        """La case de terre du pays la plus proche, entouree de terre si possible : (latitude, longitude, km)."""
        country = self.codes.get(code)
        x, z = self.x(longitude), self.z(latitude)
        column = int((x - self.min_x) // self.cell)
        row = int((z - self.min_z) // self.cell)
        km_per_block = 111.32 * math.cos(math.radians(latitude)) / self.bpd
        reach = int(math.ceil(limit_km / km_per_block / self.cell)) + 1
        best = None
        for dz in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                c, r = column + dx, row + dz
                if not self.land(c, r, country):
                    continue
                # une case entouree de terre vaut mieux qu'une pointe de cote : le cone y tient
                solid = sum(1 for oz in (-1, 0, 1) for ox in (-1, 0, 1) if self.land(c + ox, r + oz, None))
                cx = self.min_x + c * self.cell + self.cell / 2.0
                cz = self.min_z + r * self.cell + self.cell / 2.0
                distance = math.hypot(cx - x, cz - z) * km_per_block
                score = distance + (0.0 if solid == 9 else 25.0)
                if distance <= limit_km and (best is None or score < best[0]):
                    best = (score, cx, cz, distance)
        if best is None:
            return None
        return round(self.latitude(best[2]), 4), round(self.longitude(best[1]), 4), best[3]


def main():
    grid = None
    if len(sys.argv) >= 3:
        grid = Grid(sys.argv[1], sys.argv[2])
    volcanoes = []
    for vid, name, code, latitude, longitude in VOLCANOES:
        if grid is not None:
            placed = grid.place(latitude, longitude, code)
            if placed is None:
                print("retire (pas de terre de %s a 30 km) : %s" % (code, name))
                continue
            latitude, longitude, km = placed
            print("%-24s %s  %8.3f %9.3f  (%.0f km du sommet reel)" % (name, code, latitude, longitude, km))
        volcanoes.append({"id": vid, "name": name, "country": code, "latitude": latitude, "longitude": longitude})
    data = {
        "_comment": "Ecrit par tools/earth_disasters.py : les volcans, les zones sismiques, les bassins cycloniques "
                    "et les regions de mousson de la Terre, en latitude et longitude. Les volcans sont poses sur la "
                    "terre de la grille Earth24k.",
        "version": 1,
        "settings": SETTINGS,
        "volcanoes": volcanoes,
        "zones": [{"id": zid, "name": name, "points": [[lat, lon] for lat, lon in points], "width_km": width,
                   "max_magnitude": top, "weight": weight} for zid, name, points, width, top, weight in ZONES],
        "basins": BASINS,
        "monsoons": [{"id": mid, "name": name, "latitude": lat, "longitude": lon, "radius_km": radius}
                     for mid, name, lat, lon, radius in MONSOONS],
        "names": NAMES,
    }
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    print("%d volcans, %d zones, %d bassins, %d moussons -> %s" % (len(volcanoes), len(ZONES), len(BASINS),
                                                                    len(MONSOONS), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
