#!/usr/bin/env python3
"""Les pieces des ecrans du jeu redessines par PalaVanillaGui : table d'enchantement, alambic.

    python3 -I tools/palavanillagui_assets.py --import PalaVanillaGui-6.6.1.pala
    python3 -I tools/palavanillagui_assets.py --check

Tout vient du jar PalaVanillaGui 6.6.1 (Paladium Games), fourni par le joueur ; credit dans le
README (« Credits des textures »). Le jar n'est pas dans le depot : --import ne sert qu'a le
reprendre, la CI ne fait que --check.

Ce que --import reprend, reduit a peu pres a quatre pixels par unite d'interface (les images du
jar font plusieurs centaines de pixels ; l'interface les dessine en douze a cinquante unites),
et lisse a l'affichage (un .mcmeta par image) :

  - la table d'enchantement : le fond aux runes, sous le livre ; le losange XP ;
  - l'alambic : le trace qui relie l'ingredient aux fioles, les bulles et la fleche de la
    distillation (le creux et le plein de chacune). Les cases vides gardent les silhouettes
    du jeu (fiole, poudre de blaze), dessinees par l'ecran du jeu lui-meme.

Les ecrans eux-memes sont client/ui/vanilla/KitStations.java.
"""
import io
import json
import os
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "gui", "kit", "vanilla")
JAR = "assets/palavanillagui/textures/gui/"

# L'image du mod -> (l'image du jar, la taille ecrite).
PIECES = {
    "enchant_runes": ("enchanttable/enchant_table_background.png", (208, 190)),
    "xp_badge": ("enchanttable/xp_badge.png", (40, 40)),
    "brewing_line": ("brewing_line.png", (196, 61)),
    "bubbles": ("bubble_foreground.png", (48, 120)),
    "bubbles_back": ("bubble_background.png", (48, 120)),
    "brew_arrow": ("arrow_foreground.png", (39, 112)),
    "brew_arrow_back": ("arrow_background.png", (39, 112)),
}

MCMETA = json.dumps({"texture": {"blur": True, "clamp": True}}, indent=2) + "\n"


def png_size(path):
    """La taille d'un PNG, lue dans son en-tete (sans Pillow : la CI n'en a pas besoin)."""
    with open(path, "rb") as handle:
        head = handle.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def import_from(jar):
    from PIL import Image
    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(jar) as archive:
        for name, (source, size) in PIECES.items():
            image = Image.open(io.BytesIO(archive.read(JAR + source))).convert("RGBA")
            image.resize(size, Image.LANCZOS).save(os.path.join(OUT, name + ".png"), optimize=True)


def problems():
    found = []
    for name, (_source, size) in PIECES.items():
        path = os.path.join(OUT, name + ".png")
        if not os.path.exists(path):
            found.append("%s.png absent (--import PalaVanillaGui-6.6.1.pala)" % name)
        elif png_size(path) != size:
            found.append("%s.png fait %s au lieu de %s" % (name, png_size(path), size))
        meta = path + ".mcmeta"
        if not os.path.exists(meta) or open(meta, encoding="utf-8").read() != MCMETA:
            found.append("%s.png.mcmeta manque ou differe" % name)
    return found


def main():
    check = "--check" in sys.argv
    jar = sys.argv[sys.argv.index("--import") + 1] if "--import" in sys.argv else None
    if not check:
        if jar:
            import_from(jar)
        for name in PIECES:
            if os.path.exists(os.path.join(OUT, name + ".png")):
                with open(os.path.join(OUT, name + ".png.mcmeta"), "w", encoding="utf-8") as handle:
                    handle.write(MCMETA)
    stale = problems()
    if stale:
        for line in stale:
            print("::error::Pieces de PalaVanillaGui : %s. Lance 'python3 -I tools/palavanillagui_assets.py'." % line)
        sys.exit(1)
    print("Pieces de PalaVanillaGui : %d images a jour" % len(PIECES))


if __name__ == "__main__":
    main()
