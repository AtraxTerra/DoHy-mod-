#!/usr/bin/env python3
"""Ressources du kit d'interface DoHy UI (chapitre 21).

Usage : python3 tools/ui_assets.py <dossier des jars extraits>

Le dossier contient les jars de Paladium deja extraits, comme pour
palamod_assets.py : Paladium-UI-7.0.0 (polices, boutons, banniere) et
PalaJobs-8.0.0 (hexagones des metiers, quetes). Le script ecrit dans
src/main/resources/assets/dohy :

- font/ : les polices TrueType et leurs definitions (une par usage) ;
- textures/gui/kit/ : les images, retaillees et recolorees.

Les petites icones des machines (losanges, chaleur, accueil) sont dessinees
ici, pixel par pixel : elles n'existent dans aucun jar.
"""

import json
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy")
KIT = os.path.join(ASSETS, "textures", "gui", "kit")
FONT = os.path.join(ASSETS, "font")

# Couleurs des metiers, relevees sur la capture de l'ecran des metiers.
JOB_COLORS = {
    "miner": (239, 56, 38),
    "farmer": (250, 196, 0),
    "hunter": (47, 91, 234),
    "alchemist": (236, 79, 181),
}
JOB_ART = {"miner": "miner", "farmer": "farmer", "hunter": "hunter", "alchemist": "alchimiste"}


def save(image, *parts):
    path = os.path.join(KIT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, optimize=True)


def resized(image, width):
    height = round(image.height * width / image.width)
    return image.resize((width, height), Image.LANCZOS)


def fonts(ui_root):
    os.makedirs(FONT, exist_ok=True)
    source = os.path.join(ui_root, "assets", "paladium-ui", "fonts")
    copies = {
        "dungeons.ttf": "Minecraft-Dungeons/Minecraft-Dungeons.ttf",
        "wide.ttf": "Minecraft/Minecraft.ttf",
        "pixelnes.otf": "Pixel-NES/Pixel-NES.otf",
        "montserrat_semibold.ttf": "Montserrat/Montserrat-SemiBold.ttf",
        "montserrat_bold.ttf": "Montserrat/Montserrat-Bold.ttf",
    }
    for target, relative in copies.items():
        shutil.copyfile(os.path.join(source, relative), os.path.join(FONT, target))
    shutil.copyfile(os.path.join(source, "Montserrat", "OFL.txt"), os.path.join(FONT, "montserrat_ofl.txt"))
    # Chaque police est definie a la taille 8 : le code la met a l'echelle
    # voulue. Le sur-echantillonnage garde le trait net jusqu'a x2.
    definitions = {
        "title": ("dungeons.ttf", 12.0),
        "wide": ("wide.ttf", 10.0),
        "pixel": ("pixelnes.otf", 10.0),
        "body": ("montserrat_semibold.ttf", 10.0),
        "bold": ("montserrat_bold.ttf", 10.0),
    }
    for name, (file, oversample) in definitions.items():
        with open(os.path.join(FONT, name + ".json"), "w", encoding="utf-8") as out:
            json.dump({"providers": [
                {"type": "ttf", "file": "dohy:" + file, "size": 8.0, "oversample": oversample,
                 "shift": [0.0, 0.0], "skip": ""},
                {"type": "reference", "id": "minecraft:default"},
            ]}, out, indent=2)
            out.write("\n")


def jobs(jobs_root):
    icons = os.path.join(jobs_root, "assets", "palajobs", "textures", "gui", "icons")
    for job, art in JOB_ART.items():
        image = Image.open(os.path.join(icons, art + ".png")).convert("RGBA")
        # L'anneau entre l'illustration et le bord sombre est transparent : le
        # jeu peint dessous un hexagone de la couleur du metier. On le cuit ici :
        # est colore tout pixel transparent que l'exterieur n'atteint pas.
        color = JOB_COLORS[job]
        pixels = image.load()
        width, height = image.size
        outside = bytearray(width * height)
        stack = [(x, y) for x in range(width) for y in (0, height - 1)]
        stack += [(x, y) for y in range(height) for x in (0, width - 1)]
        while stack:
            x, y = stack.pop()
            if x < 0 or y < 0 or x >= width or y >= height or outside[y * width + x]:
                continue
            if pixels[x, y][3] >= 200:
                continue
            outside[y * width + x] = 1
            stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                if a < 255 and not outside[y * width + x]:
                    k = a / 255.0
                    pixels[x, y] = (round(r * k + color[0] * (1 - k)), round(g * k + color[1] * (1 - k)),
                                    round(b * k + color[2] * (1 - k)), 255)
        save(resized(image, 256), "jobs", job + ".png")

    bookmark = Image.open(os.path.join(jobs_root, "..", "Paladium-UI-7.0.0", "assets", "paladium-ui",
                                       "textures", "bookmark", "bookmark.png")).convert("RGBA")
    base = (233, 55, 37)
    for job, color in JOB_COLORS.items():
        tinted = Image.new("RGBA", bookmark.size)
        source = bookmark.load()
        target = tinted.load()
        for y in range(bookmark.height):
            for x in range(bookmark.width):
                r, g, b, a = source[x, y]
                shade = (r + g + b) / float(sum(base))
                target[x, y] = tuple(min(255, round(c * shade)) for c in color) + (a,)
        save(resized(tinted, 128), "jobs", "banner_" + job + ".png")

    quest = os.path.join(jobs_root, "assets", "palajobs", "textures", "gui", "home", "quest")
    for name in ("checked", "unchecked"):
        save(Image.open(os.path.join(quest, name + ".png")).convert("RGBA"), "quest_" + name + ".png")


def buttons(ui_root):
    textures = os.path.join(ui_root, "assets", "paladium-ui", "textures")
    save(resized(Image.open(os.path.join(textures, "buttons", "hexa_border.png")).convert("RGBA"), 128),
         "hexa_border.png")
    save(Image.open(os.path.join(textures, "icons", "money.png")).convert("RGBA"), "money.png")
    for name in ("search", "close"):
        save(resized(Image.open(os.path.join(textures, "buttons", name + ".png")).convert("RGBA"), 32),
             "icon_" + name + ".png")


def pixel_art(rows, palette, scale=1):
    """Une image depuis une grille de caracteres : '.' est transparent."""
    height = len(rows)
    width = max(len(row) for row in rows)
    image = Image.new("RGBA", (width * scale, height * scale), (0, 0, 0, 0))
    pixels = image.load()
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char == "." or char == " ":
                continue
            for dy in range(scale):
                for dx in range(scale):
                    pixels[x * scale + dx, y * scale + dy] = palette[char]
    return image


DIAMOND = [
    "......dd......",
    ".....dccd.....",
    "....dcccdd....",
    "...dcccccdd...",
    "..dcccccccdd..",
    ".dcccccccccdd.",
    "dcccccccccccdd",
    "dcccccccccccdd",
    ".dccccccccccd.",
    "..dccccccccd..",
    "...dccccccd...",
    "....dccccd....",
    ".....dccd.....",
    "......dd......",
]

GLYPHS = {
    # Une flamme, une grille, une fleche vers le haut, un engrenage simplifie.
    "fuel": ["..w...", ".ww.w.", ".wwww.", "wwkwww", "wkkkww", ".wkkw."],
    "craft": ["wwwwww", "w.w.ww", "wwwwww", "w.w.ww", "wwwwww", "......"],
    "upgrade": ["..ww..", ".wwww.", "wwwwww", "..ww..", "..ww..", "......"],
    "melt": ["..w...", ".ww.w.", ".wwww.", "wwkwww", "wkkkww", ".wkkw."],
    "resources": [".w..w.", "wwwwww", ".wkkw.", ".wkkw.", "wwwwww", ".w..w."],
}

SECTION_COLORS = {
    "fuel": ((232, 54, 34), (150, 30, 20)),
    "craft": ((150, 150, 158), (90, 90, 98)),
    "upgrade": ((240, 150, 30), (170, 95, 15)),
    "melt": ((232, 54, 34), (150, 30, 20)),
    "resources": ((240, 150, 30), (170, 95, 15)),
}

HEAT = [
    ".x...x...x.",
    ".x...x...x.",
    "x...x...x..",
    "x...x...x..",
    ".x...x...x.",
    ".x...x...x.",
    "..x...x...x",
    "..x...x...x",
    ".x...x...x.",
    ".x...x...x.",
    "xx..xx..xx.",
]

HOME = [
    "....ww....",
    "...wwww...",
    "..wwwwww..",
    ".wwwwwwww.",
    "wwwwwwwwww",
    ".ww.ww.ww.",
    ".ww.ww.ww.",
    ".wwwwwwww.",
]


BACK = [
    "...ww.....",
    "..www.....",
    ".wwwwwwww.",
    "wwwwwwwww.",
    ".wwwwwwww.",
    "..www.....",
    "...ww.....",
]


def drawn():
    for name, (fill, dark) in SECTION_COLORS.items():
        icon = pixel_art(DIAMOND, {"d": dark + (255,), "c": fill + (255,)})
        glyph = pixel_art(GLYPHS[name], {"w": (30, 25, 30, 255), "k": fill + (255,)})
        icon.alpha_composite(glyph, (4, 4))
        save(icon.resize((icon.width * 4, icon.height * 4), Image.NEAREST), "section_" + name + ".png")
    heat = pixel_art(HEAT, {"x": (70, 70, 80, 255)})
    save(heat.resize((heat.width * 4, heat.height * 4), Image.NEAREST), "heat.png")
    home = pixel_art(HOME, {"w": (255, 255, 255, 255)})
    save(home.resize((home.width * 4, home.height * 4), Image.NEAREST), "icon_home.png")
    back = pixel_art(BACK, {"w": (255, 255, 255, 255)})
    save(back.resize((back.width * 4, back.height * 4), Image.NEAREST), "icon_back.png")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    extract = sys.argv[1]
    ui_root = os.path.join(extract, "Paladium-UI-7.0.0")
    jobs_root = os.path.join(extract, "PalaJobs-8.0.0")
    os.makedirs(KIT, exist_ok=True)
    fonts(ui_root)
    jobs(jobs_root)
    buttons(ui_root)
    drawn()
    print("kit d'interface ecrit dans", os.path.relpath(ASSETS, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
