#!/usr/bin/env python3
"""La presse a imprimer et la table d'ecriture : geometrie, modeles, recettes, butin, noms.

    python3 -I tools/press_assets.py [--jar <jar de PalaMod extrait>]
    python3 -I tools/press_assets.py --check

Dans PalaMod 10.3.0, la presse ({@code BlockPrintPress}) et la table d'ecriture
({@code BlockTypeMachine}) sont deux modeles Techne dessines par un
TileEntitySpecialRenderer : la presse a son plateau qui s'abaisse, ses rouleaux
encreurs qui basculent, sa plaque d'encre qui se remplit, ses livres empiles ;
la table a ses chassis empiles, la plaque gravee et le livre pose. La
geometrie est relevee ici boite par boite depuis les constructeurs de
{@code ModelPrintPress}, {@code ModelInkPlate}, {@code ModelPrintPressBooks},
{@code ModelTypeset}, {@code ModelTypesetBase} et {@code ModelTypesetBook}
(javap). Le script ecrit :

* `client/press/PressGeometry.java` — la meme geometrie en code vanilla, pour le
  rendu des blocs poses, avec les groupes de pieces de chaque methode de rendu
  d'origine ;
* les objets en 3D (`models/item/print_press.json`, `type_machine.json`), les
  blockstates, les modeles de bloc (les particules seulement), la dalle en
  paladium (une dalle vanilla), le butin, les recettes de `PRegister_Craft` et
  les noms.

Les textures viennent du jar de PalaMod (`textures/models/*.png`,
`textures/items/*.png`) : `--jar` les recopie. Sans `--jar`, les textures deja
posees restent.

`--check` (CI) refait tout en memoire et echoue si un fichier genere a change.

Reperes des modeles d'origine : y vers le bas. Le rendu de 1.7.10 les tourne de
180 degres autour de x (y et z s'inversent) apres l'orientation du bloc ; la
presse est dessinee a l'echelle 0,05 (un bloc vaut 20 unites), la table a
l'echelle 1/48.
"""
import json
import math
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "client", "press",
                    "PressGeometry.java")
NS = "palamod"

# Releve par javap -c dans PalaMod 10.3.0 : (piece, pivot, rotation en radians, miroir,
# boites (u, v, x, y, z, w, h, d)). Toutes les pieces sont en miroir ({@code mirror = true}
# avant {@code addBox}), comme l'export Techne d'origine.
MODELS = {
    "PRESS": {
        "model": "ModelPrintPress", "layer": ("print_press", "main"), "size": (128, 64),
        "parts": [
            ("LLeg", (0.0, 0.0, -3.0), (0.0, 0.0, 0.0), True, [(31, 28, -9.0, 4.0, -8.0, 3, 19, 16)]),
            ("RLeg", (0.0, 0.0, -3.0), (0.0, 0.0, 0.0), True, [(31, 28, 6.0, 4.0, -8.0, 3, 19, 16)]),
            ("frontbrace", (0.0, 2.0, 0.0), (0.0, 0.0, 0.0), True, [(95, 34, -6.0, 8.0, -10.0, 12, 11, 4)]),
            ("bottombrace", (0.0, 2.0, 0.0), (0.0, 0.0, 0.0), True, [(89, 50, -6.0, 12.0, -2.0, 12, 7, 7)]),
            ("backbrace", (0.0, 2.0, 0.0), (0.0, 0.0, 0.0), True, [(62, 34, -6.0, 10.0, 1.0, 12, 2, 4)]),
            ("pibrace", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), True, [(37, 0, -1.0, 1.0, 1.0, 2, 6, 4)]),
            ("pback", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), True, [(73, 0, -5.0, 2.0, 0.0, 10, 12, 1)]),
            ("table", (0.0, 3.0, 0.0), (0.0, 0.0, 0.0), True, [(70, 24, -10.0, 0.0, -13.0, 20, 1, 8)]),
            ("irshaft", (0.0, 4.0, 5.0), (0.0, 0.0, 0.0), True, [(2, 42, -5.0, 0.0, 0.0, 10, 1, 1)]),
            ("plate", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), True, [(77, 14, -4.0, 3.0, -1.0, 8, 8, 1)]),
            ("press", (0.0, 15.5, 1.0), (0.0, 0.0, 0.0), True, [
                (50, 0, -5.0, -14.0, 0.5, 10, 10, 1),
                (50, 12, -4.0, -12.0, -2.5, 8, 8, 3)]),
            ("inkRollers", (0.0, 4.0, 6.0), (0.0, 0.0, 0.0), True, [
                (9, 22, -6.0, -7.0, -2.5, 1, 1, 4),
                (9, 22, 5.0, -7.0, -2.5, 1, 1, 4),
                (2, 22, 5.0, -6.0, -1.5, 1, 10, 2),
                (2, 22, -6.0, -6.0, -1.5, 1, 10, 2),
                (1, 35, -5.0, 2.0, -2.0, 10, 3, 3),
                (3, 19, -5.0, -7.0, 0.0, 10, 1, 1),
                (3, 19, -5.0, -7.0, -2.0, 10, 1, 1)]),
        ],
    },
    "INK_PLATE": {
        "model": "ModelInkPlate", "layer": ("print_press", "ink"), "size": (32, 32),
        "parts": [
            ("inkplate", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), True, [(0, 0, -3.5, 0.0, 0.5, 7, 1, 7)]),
        ],
    },
    "PRESS_BOOKS": {
        "model": "ModelPrintPressBooks", "layer": ("print_press", "books"), "size": (32, 32),
        "parts": [
            ("Bbook1", (0.0, 3.0, 0.0), (0.0, 0.0, 0.0), True, [(0, 0, -9.0, -2.0, -14.0, 6, 2, 9)]),
            ("Bbook2", (0.0, 3.0, 0.0), (0.0, 0.0, 0.0), True, [(0, 0, -10.0, -4.0, -13.0, 6, 2, 9)]),
            ("Bbook3", (0.0, 3.0, 0.0), (0.0, 0.0, 0.0), True, [(0, 0, -11.0, -6.0, -13.0, 6, 2, 9)]),
            ("Bbook4", (0.0, 3.0, 0.0), (0.0, 0.0, 0.0), True, [(0, 0, -9.0, -8.0, -14.0, 6, 2, 9)]),
            ("Fbook", (0.0, 3.0, 0.0), (0.0, 0.0, 0.0), True, [(0, 12, 3.0, -2.0, -14.0, 6, 2, 9)]),
        ],
    },
    "TYPESET": {
        "model": "ModelTypeset", "layer": ("type_machine", "main"), "size": (128, 64),
        "parts": [
            ("topedge", (0, 0, 0), (0, 0, 0), True, [(0, 57, -24.0, 0.0, 24.0, 48, 2, 1)]),
            ("buttomedge", (0, 0, 0), (0, 0, 0), True, [(0, 57, -24.0, 0.0, -23.0, 48, 2, 1)]),
            ("mid1edge", (0, 0, 0), (0, 0, 0), True, [(0, 57, -23.0, 0.0, -4.0, 46, 2, 1)]),
            ("mid2edge", (0, 0, 0), (0, 0, 0), True, [(0, 57, -23.0, 0.0, 5.0, 46, 2, 1)]),
            ("leftedge", (0, 0, 0), (0, 0, 0), True, [(0, 8, -24.0, 0.0, -22.0, 1, 2, 46)]),
            ("rightedge", (0, 0, 0), (0, 0, 0), True, [(0, 8, 23.0, 0.0, -22.0, 1, 2, 46)]),
            ("bcedge", (0, 0, 0), (0, 0, 0), True, [(4, 22, -0.5, 0.0, -22.0, 1, 2, 18)]),
            ("m2edge", (0, 0, 0), (0, 0, 0), True, [(74, 42, -3.0, 0.0, -3.0, 1, 2, 8)]),
            ("m3edge", (0, 0, 0), (0, 0, 0), True, [(74, 42, 2.0, 0.0, -3.0, 1, 2, 8)]),
            ("m4edge", (0, 0, 0), (0, 0, 0), True, [(74, 42, 7.0, 0.0, -3.0, 1, 2, 8)]),
            ("m1edge", (0, 0, 0), (0, 0, 0), True, [(74, 42, -8.0, 0.0, -3.0, 1, 2, 8)]),
            ("trcedge", (0, 0, 0), (0, 0, 0), True, [(4, 22, 9.0, 0.0, 6.0, 1, 2, 18)]),
            ("tlcedge", (0, 0, 0), (0, 0, 0), True, [(4, 22, -10.0, 0.0, 6.0, 1, 2, 18)]),
            ("mcb1edge", (0, 0, 0), (0, 0, 0), True, [(7, 47, -23.0, 0.0, -1.0, 15, 2, 1)]),
            ("mcb2edge", (0, 0, 0), (0, 0, 0), True, [(7, 47, -23.0, 0.0, 2.0, 15, 2, 1)]),
            ("mcb3edge", (0, 0, 0), (0, 0, 0), True, [(7, 47, 8.0, 0.0, -1.0, 15, 2, 1)]),
            ("mcb4edge", (0, 0, 0), (0, 0, 0), True, [(7, 47, 8.0, 0.0, 2.0, 15, 2, 1)]),
            ("tl1edge", (0, 0, 0), (0, 0, 0), True, [(9, 43, -23.0, 0.0, 20.0, 13, 2, 1)]),
            ("tl2edge", (0, 0, 0), (0, 0, 0), True, [(9, 43, -23.0, 0.0, 16.0, 13, 2, 1)]),
            ("tr1edge", (0, 0, 0), (0, 0, 0), True, [(9, 43, 10.0, 0.0, 20.0, 13, 2, 1)]),
            ("tr2edge", (0, 0, 0), (0, 0, 0), True, [(9, 43, 10.0, 0.0, 16.0, 13, 2, 1)]),
            ("tlmc2edge", (0, 0, 0), (0, 0, 0), True, [(50, 40, -15.0, 0.0, 6.0, 1, 2, 10)]),
            ("tlmc1edge", (0, 0, 0), (0, 0, 0), True, [(50, 40, -19.0, 0.0, 6.0, 1, 2, 10)]),
            ("trmc2edge", (0, 0, 0), (0, 0, 0), True, [(50, 40, 18.0, 0.0, 6.0, 1, 2, 10)]),
            ("trmc1edge", (0, 0, 0), (0, 0, 0), True, [(50, 40, 14.0, 0.0, 6.0, 1, 2, 10)]),
            ("tlc1", (0, 0, 0), (0, 0, 0), True, [(51, 20, -7.0, 0.0, 10.0, 2, 2, 10)]),
            ("trc1", (0, 0, 0), (0, 0, 0), True, [(51, 20, 5.0, 0.0, 10.0, 2, 2, 10)]),
            ("tbc1", (0, 0, 0), (0, 0, 0), True, [(74, 26, -5.0, 0.0, 10.0, 10, 2, 2)]),
            ("ttc1", (0, 0, 0), (0, 0, 0), True, [(74, 26, -5.0, 0.0, 18.0, 10, 2, 2)]),
            ("tlc2", (0, 0, 0), (0, 0, 0), True, [(51, 20, -8.0, -2.0, 11.0, 2, 2, 10)]),
            ("trc2", (0, 0, 0), (0, 0, 0), True, [(51, 20, 4.0, -2.0, 11.0, 2, 2, 10)]),
            ("tbc2", (0, 0, 0), (0, 0, 0), True, [(74, 26, -6.0, -2.0, 11.0, 10, 2, 2)]),
            ("ttc2", (0, 0, 0), (0, 0, 0), True, [(74, 26, -6.0, -2.0, 19.0, 10, 2, 2)]),
            ("tlc3", (0, 0, 0), (0, 0, 0), True, [(51, 20, -6.0, -4.0, 11.0, 2, 2, 10)]),
            ("trc3", (0, 0, 0), (0, 0, 0), True, [(51, 20, 6.0, -4.0, 11.0, 2, 2, 10)]),
            ("tbc3", (0, 0, 0), (0, 0, 0), True, [(74, 26, -4.0, -4.0, 11.0, 10, 2, 2)]),
            ("ttc3", (0, 0, 0), (0, 0, 0), True, [(74, 26, -4.0, -4.0, 19.0, 10, 2, 2)]),
            ("tlc4", (0, 0, 0), (0, 0, 0), True, [(51, 20, -7.0, -6.0, 10.0, 2, 2, 10)]),
            ("trc4", (0, 0, 0), (0, 0, 0), True, [(51, 20, 5.0, -6.0, 10.0, 2, 2, 10)]),
            ("tbc4", (0, 0, 0), (0, 0, 0), True, [(74, 26, -5.0, -6.0, 10.0, 10, 2, 2)]),
            ("ttc4", (0, 0, 0), (0, 0, 0), True, [(74, 26, -5.0, -6.0, 18.0, 10, 2, 2)]),
            ("blPlate", (0, 0, 0), (0, 0, 0), True, [(50, 0, -19.0, 0.0, -18.0, 14, 2, 10)]),
            ("brPlate", (0, 0, 0), (0, 0, 0), True, [(50, 0, 5.0, 0.0, -18.0, 14, 2, 10)]),
            ("blcRight", (0, 0, 0), (0, 0, 0), True, [(51, 20, -7.0, 0.0, -18.0, 2, 2, 10)]),
            ("blcLeft", (0, 0, 0), (0, 0, 0), True, [(51, 20, -19.0, 0.0, -18.0, 2, 2, 10)]),
            ("blcTop", (0, 0, 0), (0, 0, 0), True, [(74, 26, -17.0, 0.0, -10.0, 10, 2, 2)]),
            ("blcBottom", (0, 0, 0), (0, 0, 0), True, [(74, 26, -17.0, 0.0, -18.0, 10, 2, 2)]),
        ],
    },
    "TYPESET_BASE": {
        "model": "ModelTypesetBase", "layer": ("type_machine", "base"), "size": (256, 128),
        "parts": [
            ("base", (0, 0, 0), (0, 0, 0), True, [(0, 0, -24.0, 2.0, -23.0, 48, 42, 48)]),
        ],
    },
    "TYPESET_BOOK": {
        "model": "ModelTypesetBook", "layer": ("type_machine", "book"), "size": (128, 32),
        "parts": [
            ("book", (0, 0, 0), (0, 0, 0), True, [(0, 0, -22.5, -3.0, -20.5, 21, 5, 15)]),
        ],
    },
}

# Les groupes de pieces des methodes de rendu d'origine.
GROUPS = {
    "PRESS_MAIN": ("PRESS", ["LLeg", "RLeg", "frontbrace", "bottombrace", "backbrace", "pibrace", "pback",
                             "table", "irshaft"]),
    "TYPESET_TOP": ("TYPESET", ["topedge", "buttomedge", "mid1edge", "mid2edge", "leftedge", "rightedge",
                                "bcedge", "m2edge", "m3edge", "m4edge", "m1edge", "trcedge", "tlcedge",
                                "mcb1edge", "mcb2edge", "mcb3edge", "mcb4edge", "tl1edge", "tl2edge", "tr1edge",
                                "tr2edge", "tlmc2edge", "tlmc1edge", "trmc2edge", "trmc1edge"]),
    "EMPTY_BOOKS": ("PRESS_BOOKS", ["Bbook1", "Bbook2", "Bbook3", "Bbook4"]),
}
# renderChaseStack(n) : une couche de chassis par seize chassis, quatre au plus.
CHASE_LAYERS = [["tlc%d" % n, "trc%d" % n, "tbc%d" % n, "ttc%d" % n] for n in range(1, 5)]

# Echelles de rendu d'origine : 0,05 pour la presse ({@code render(0.05F)}), 1/48 pour la table.
PRESS_SCALE = 0.05
TYPESET_SCALE = 1.0 / 48.0

TEXTURES = {
    # destination (sous textures/) -> source (sous assets/palamod/textures/ du jar)
    "block/press/printpress.png": "models/printpress.png",
    "block/press/pressbooks.png": "models/pressbooks2.png",
    "block/typeset/typeset_top.png": "models/typeset_top.png",
    "block/typeset/typeset_base.png": "models/typeset_base.png",
    "block/typeset/typeset_book.png": "models/typeset_book3.png",
    "item/chase.png": "items/chase.png",
    "item/paladium_ink.png": "items/paladium_ink.png",
    "item/enchanted_plate.png": "items/plate.png",
}
for _level in range(9):
    TEXTURES["block/press/inkplate%d.png" % _level] = "models/inkplate%d.png" % _level

LANG = {
    "fr_fr": {
        "block.palamod.print_press": "Presse a imprimer",
        "block.palamod.type_machine": "Table d'ecriture",
        "block.palamod.paladium_slab": "Dalle en paladium",
        "item.palamod.chase": "Plaque d'impression",
        "item.palamod.paladium_ink": "Encre de paladium",
        "item.palamod.enchanted_plate": "Plaque d'impression enchantee",
        "tooltip.palamod.chase.use": "Se grave a la table d'ecriture avec un livre enchante",
        "tooltip.palamod.paladium_ink.use": "Presse a imprimer : deux encres par livre",
        "tooltip.palamod.enchanted_plate.uses": "Impressions restantes : %s / %s",
        "tooltip.palamod.enchanted_plate.use": "Se pose dans la presse a imprimer",
        "message.palamod.type_machine.levels": "Il te faut %s niveaux pour graver cette plaque.",
        "message.palamod.type_machine.missing": "Pose un livre enchante et une plaque d'impression sur la table.",
        "message.palamod.type_machine.taken": "Retire d'abord la plaque deja gravee.",
        "message.palamod.type_machine.engraved": "Plaque gravee : %s niveaux depenses.",
        "hud.palamod.print_press.ink": "%s encres",
        "hud.palamod.print_press.books": "%s livres",
        "hud.palamod.type_machine.cost": "Gravure : %s niveaux",
    },
    "en_us": {
        "block.palamod.print_press": "Print Press",
        "block.palamod.type_machine": "Typesetting Table",
        "block.palamod.paladium_slab": "Paladium Slab",
        "item.palamod.chase": "Printing Plate",
        "item.palamod.paladium_ink": "Paladium Ink",
        "item.palamod.enchanted_plate": "Enchanted Printing Plate",
        "tooltip.palamod.chase.use": "Engraved at the typesetting table with an enchanted book",
        "tooltip.palamod.paladium_ink.use": "Print press: two inks per book",
        "tooltip.palamod.enchanted_plate.uses": "Prints left: %s / %s",
        "tooltip.palamod.enchanted_plate.use": "Goes into the print press",
        "message.palamod.type_machine.levels": "You need %s levels to engrave this plate.",
        "message.palamod.type_machine.missing": "Put an enchanted book and a printing plate on the table.",
        "message.palamod.type_machine.taken": "Take the engraved plate out first.",
        "message.palamod.type_machine.engraved": "Plate engraved: %s levels spent.",
        "hud.palamod.print_press.ink": "%s inks",
        "hud.palamod.print_press.books": "%s books",
        "hud.palamod.type_machine.cost": "Engraving: %s levels",
    },
}


def number(value):
    """Un flottant Java : « 6F », « -4.573F »."""
    text = ("%.4f" % value).rstrip("0").rstrip(".")
    if text in ("-0", ""):
        text = "0"
    return text + "F"


def java_source():
    lines = [
        "package fr.atraxterra.dohy.client.press;",
        "",
        "import net.minecraft.client.model.geom.ModelLayerLocation;",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.LayerDefinition;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "import net.minecraft.resources.Identifier;",
        "",
        "/**",
        " * GENERE par tools/press_assets.py. Ne pas modifier a la main : relancer le script.",
        " *",
        " * <p>Les modeles de la presse a imprimer et de la table d'ecriture (PalaMod 10.3.0),",
        " * boite par boite, sur leurs textures d'origine. Repere des modeles d'entite : y vers",
        " * le bas. Toutes les boites sont en miroir, comme dans l'original.",
        " */",
        "@SuppressWarnings(\"unused\")",
        "public final class PressGeometry {",
        "",
    ]
    for key, model in MODELS.items():
        name, layer = model["layer"]
        lines.append("    /** {@code %s}. */" % model["model"])
        lines.append("    public static final ModelLayerLocation %s = new ModelLayerLocation(" % key)
        lines.append("            Identifier.fromNamespaceAndPath(\"%s\", \"%s\"), \"%s\");" % (NS, name, layer))
    lines.append("")
    for key, (_model, parts) in GROUPS.items():
        lines.append("    public static final String[] %s = {%s};" % (
            key, ", ".join("\"%s\"" % part for part in parts)))
    lines.append("    public static final String[][] CHASE_LAYERS = {%s};" % ", ".join(
        "{%s}" % ", ".join("\"%s\"" % part for part in layer) for layer in CHASE_LAYERS))
    lines += [
        "",
        "    /** Echelle de rendu de la presse ({@code render(0.05F)}), rapportee au 1/16 des modeles vanilla. */",
        "    public static final float PRESS_SCALE = %s;" % number(PRESS_SCALE * 16.0),
        "    /** Echelle de rendu de la table ({@code render(1/48F)}). */",
        "    public static final float TYPESET_SCALE = %s;" % number(TYPESET_SCALE * 16.0),
        "",
        "    private PressGeometry() {",
        "    }",
    ]
    for key, model in MODELS.items():
        width, height = model["size"]
        method = "create" + "".join(part.capitalize() for part in key.split("_")) + "Layer"
        lines += [
            "",
            "    public static LayerDefinition %s() {" % method,
            "        MeshDefinition mesh = new MeshDefinition();",
            "        PartDefinition root = mesh.getRoot();",
        ]
        for part, pivot, rotation, mirror, boxes in model["parts"]:
            builder = "CubeListBuilder.create()" + (".mirror()" if mirror else "")
            for u, v, x, y, z, w, h, d in boxes:
                builder += "\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)" % (
                    u, v, number(x), number(y), number(z), number(w), number(h), number(d))
            pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(
                number(value) for value in (*pivot, *rotation))
            lines.append("        root.addOrReplaceChild(\"%s\", %s,\n                %s);" % (part, builder, pose))
        lines += [
            "        return LayerDefinition.create(mesh, %d, %d);" % (width, height),
            "    }",
        ]
    lines += ["}", ""]
    return "\n".join(lines)


# -- Les objets en 3D -----------------------------------------------------------------
#
# Une boite de modele devient un element de modele de bloc. Le passage suit le rendu
# d'origine : sommet du modele, pivot de la piece, echelle, puis le demi-tour autour de x
# (y et z s'inversent) suivi d'un demi-tour autour de y, pour que la face avant regarde le
# nord comme celle d'un fourneau vanilla : au total, un demi-tour autour de z (x et y
# s'inversent). Les UV de chaque face sont ceux du ModelBox de 1.7.10 (miroir compris :
# les x sont echanges avant de poser les sommets), lus sommet par sommet, puis ranges dans
# l'ordre des faces de modele de bloc.

def box_quads(u, v, x, y, z, w, h, d, mirror):
    """Les six faces d'un ModelBox : quatre sommets (position, uv) chacune."""
    x0, x1 = x, x + w
    if mirror:
        x0, x1 = x1, x0
    y0, y1, z0, z1 = y, y + h, z, z + d
    v7, v0, v1, v2 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v3, v4, v5, v6 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    faces = [
        ([v4, v0, v1, v5], (u + d + w, v + d, u + d + w + d, v + d + h)),
        ([v7, v3, v6, v2], (u, v + d, u + d, v + d + h)),
        ([v4, v3, v7, v0], (u + d, v, u + d + w, v + d)),
        ([v1, v2, v6, v5], (u + d + w, v + d, u + d + w + w, v)),
        ([v0, v7, v2, v1], (u + d, v + d, u + d + w, v + d + h)),
        ([v3, v4, v5, v6], (u + d + w + d, v + d, u + d + w + d + w, v + d + h)),
    ]
    out = []
    for vertices, (u1, v1_, u2, v2_) in faces:
        # TexturedQuad : sommet 0 -> (u2, v1), 1 -> (u1, v1), 2 -> (u1, v2), 3 -> (u2, v2).
        out.append(list(zip(vertices, [(u2, v1_), (u1, v1_), (u1, v2_), (u2, v2_)])))
    return out


# Pour chaque face de modele de bloc : l'axe constant, son cote, et les coins
# « haut-gauche » et « bas-droit » de la texture (axe, cote) vus de l'exterieur.
FACE_FRAMES = {
    "north": (2, 0, ((0, 1), (1, 1)), ((0, 0), (1, 0))),
    "south": (2, 1, ((0, 0), (1, 1)), ((0, 1), (1, 0))),
    "west": (0, 0, ((2, 0), (1, 1)), ((2, 1), (1, 0))),
    "east": (0, 1, ((2, 1), (1, 1)), ((2, 0), (1, 0))),
    "up": (1, 1, ((0, 0), (2, 0)), ((0, 1), (2, 1))),
    "down": (1, 0, ((0, 0), (2, 1)), ((0, 1), (2, 0))),
}


def element_faces(quads, low, high, texture, size):
    """Les faces d'un element, depuis les quads transformes (sommets en pixels de bloc)."""
    width, height = size
    faces = {}
    for quad in quads:
        for name, (axis, side, top_left, bottom_right) in FACE_FRAMES.items():
            plane = high[axis] if side else low[axis]
            if all(abs(point[axis] - plane) < 1e-4 for point, _uv in quad):
                break
        else:
            raise SystemExit("face introuvable")

        def corner(spec):
            for point, uv in quad:
                if all(abs(point[a] - (high[a] if s else low[a])) < 1e-4 for a, s in spec):
                    return uv
            raise SystemExit("coin introuvable (%s)" % name)

        u1, v1 = corner(top_left)
        u2, v2 = corner(bottom_right)
        faces[name] = {"uv": [round(u1 * 16.0 / width, 4), round(v1 * 16.0 / height, 4),
                              round(u2 * 16.0 / width, 4), round(v2 * 16.0 / height, 4)],
                       "texture": "#" + texture}
    return faces


def to_block(point, pivot, scale):
    """Un sommet de piece en pixels de bloc, avant recentrage : demi-tour autour de z."""
    px, py, pz = (pivot[i] + point[i] for i in range(3))
    return (-px * scale, -py * scale, pz * scale)


def snap(angle):
    """Les modeles de bloc ne tournent que par pas de 22,5 degres."""
    return round(angle / 22.5) * 22.5


def item_elements(pieces, scale):
    """Les elements d'un objet en 3D : (modele, pieces, texture, rotation forcee) -> elements."""
    raw = []
    for key, names, texture, forced in pieces:
        model = MODELS[key]
        by_name = {part[0]: part for part in model["parts"]}
        for name in names:
            _part, pivot, rotation, mirror, boxes = by_name[name]
            rotation = forced.get(name, rotation)
            for u, v, x, y, z, w, h, d in boxes:
                quads = [[(to_block(point, pivot, scale), uv) for point, uv in quad]
                         for quad in box_quads(u, v, x, y, z, w, h, d, mirror)]
                points = [point for quad in quads for point, _uv in quad]
                low = tuple(min(p[i] for p in points) for i in range(3))
                high = tuple(max(p[i] for p in points) for i in range(3))
                origin = to_block((0.0, 0.0, 0.0), pivot, scale)
                raw.append((low, high, element_faces(quads, low, high, texture, model["size"]),
                            rotation, origin))
    # Recentrage : le pied a y = 0, le milieu de l'emprise au centre du bloc.
    min_x = min(item[0][0] for item in raw)
    max_x = max(item[1][0] for item in raw)
    min_y = min(item[0][1] for item in raw)
    min_z = min(item[0][2] for item in raw)
    max_z = max(item[1][2] for item in raw)
    shift = (8.0 - (min_x + max_x) / 2.0, -min_y, 8.0 - (min_z + max_z) / 2.0)
    out = []
    for low, high, faces, rotation, origin in raw:
        element = {"from": [round(low[i] + shift[i], 4) for i in range(3)],
                   "to": [round(high[i] + shift[i], 4) for i in range(3)],
                   "faces": faces}
        rx, ry, rz = rotation
        # Le demi-tour autour de z garde le sens d'une rotation autour de z et
        # inverse celui d'une rotation autour de x ou de y.
        for axis, angle in (("x", -rx), ("y", -ry), ("z", rz)):
            degrees = snap(math.degrees(angle))
            if degrees:
                element["rotation"] = {"origin": [round(origin[i] + shift[i], 4) for i in range(3)],
                                       "axis": axis, "angle": degrees}
                break
        for corner in element["from"] + element["to"]:
            if corner < -16 or corner > 32:
                raise SystemExit("une boite sort de -16..32 (%s)" % corner)
        out.append(element)
    return out


# Les transformations d'un bloc vanilla (block/block.json).
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}

# La presse au repos : le plateau a 0,5 rad, les rouleaux a 0,12 rad ({@code xRot} du rendu d'origine).
PRESS_REST = {"press": (0.5, 0.0, 0.0), "inkRollers": (0.12, 0.0, 0.0)}


def outputs():
    """Tous les fichiers texte generes : chemin -> contenu."""
    files = {JAVA: java_source()}

    def put(path, payload):
        files[os.path.join(RES, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    press = NS + ":block/press/printpress"
    put("assets/%s/models/item/print_press.json" % NS, {
        "textures": {"press": press, "ink": NS + ":block/press/inkplate0", "particle": press},
        "elements": item_elements([
            ("PRESS", GROUPS["PRESS_MAIN"][1] + ["press", "inkRollers"], "press", PRESS_REST),
            ("INK_PLATE", ["inkplate"], "ink", {}),
        ], PRESS_SCALE * 16.0),
        "display": DISPLAY,
    })
    top = NS + ":block/typeset/typeset_top"
    put("assets/%s/models/item/type_machine.json" % NS, {
        "textures": {"top": top, "base": NS + ":block/typeset/typeset_base", "particle": top},
        "elements": item_elements([
            ("TYPESET", GROUPS["TYPESET_TOP"][1], "top", {}),
            ("TYPESET_BASE", ["base"], "base", {}),
        ], TYPESET_SCALE * 16.0),
        "display": DISPLAY,
    })
    # Les blocs poses sont dessines par leur rendu : le modele ne porte que les particules
    # (l'enclume pour la presse, les planches de chene pour la table, comme l'original).
    for name, particle in (("print_press", "minecraft:block/anvil"), ("type_machine", "minecraft:block/oak_planks")):
        put("assets/%s/models/block/%s.json" % (NS, name), {"textures": {"particle": particle}})
        put("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s" % facing: {"model": "%s:block/%s" % (NS, name)}
            for facing in ("north", "south", "west", "east")}})
        put("assets/%s/items/%s.json" % (NS, name),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        put("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    # La dalle en paladium : une dalle vanilla, a la texture du bloc de paladium.
    texture = NS + ":block/paladium_block"
    sides = {"bottom": texture, "top": texture, "side": texture}
    put("assets/%s/models/block/paladium_slab.json" % NS, {"parent": "minecraft:block/slab", "textures": sides})
    put("assets/%s/models/block/paladium_slab_top.json" % NS,
        {"parent": "minecraft:block/slab_top", "textures": sides})
    put("assets/%s/blockstates/paladium_slab.json" % NS, {"variants": {
        "type=bottom": {"model": NS + ":block/paladium_slab"},
        "type=top": {"model": NS + ":block/paladium_slab_top"},
        "type=double": {"model": NS + ":block/paladium_block"}}})
    put("assets/%s/models/item/paladium_slab.json" % NS, {"parent": NS + ":block/paladium_slab"})
    put("assets/%s/items/paladium_slab.json" % NS,
        {"model": {"type": "minecraft:model", "model": NS + ":item/paladium_slab"}})
    put("data/%s/loot_table/blocks/paladium_slab.json" % NS, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:item", "name": NS + ":paladium_slab",
            "functions": [
                {"function": "minecraft:set_count", "count": 2, "add": False,
                 "conditions": [{"condition": "minecraft:block_state_property", "block": NS + ":paladium_slab",
                                 "properties": {"type": "double"}}]},
                {"function": "minecraft:explosion_decay"}]}]}]})

    for item in ("chase", "paladium_ink", "enchanted_plate"):
        put("assets/%s/models/item/%s.json" % (NS, item),
            {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
        put("assets/%s/items/%s.json" % (NS, item),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, item)}})

    # PRegister_Craft.
    def shaped(name, pattern, key, count=1):
        put("data/%s/recipe/%s.json" % (NS, name), {
            "type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
            "result": {"id": "%s:%s" % (NS, name), "count": count}})

    shaped("print_press", ["IBI", "SPS", "ZZZ"], {
        "I": "minecraft:iron_ingot", "B": "minecraft:blaze_rod", "S": NS + ":paladium_slab",
        "P": "minecraft:light_weighted_pressure_plate", "Z": "minecraft:iron_block"})
    shaped("type_machine", ["ICI", "SSS", "BRB"], {
        "I": "minecraft:iron_ingot", "C": NS + ":chase", "S": "#minecraft:wooden_slabs",
        "B": "#minecraft:planks", "R": NS + ":findium"})
    shaped("chase", [" S ", "SIS", " S "], {"S": NS + ":paladium_slab", "I": "minecraft:iron_block"})
    shaped("paladium_ink", ["XXX", "XYX", "XXX"], {"X": NS + ":paladium_ingot", "Y": "minecraft:ink_sac"},
           count=4)
    shaped("paladium_slab", ["XXX"], {"X": NS + ":paladium_ingot"}, count=6)
    return files


def lang_updates():
    return LANG


def main():
    check = "--check" in sys.argv
    jar = None
    if "--jar" in sys.argv:
        jar = sys.argv[sys.argv.index("--jar") + 1]

    stale = []
    for path, content in outputs().items():
        current = None
        if os.path.exists(path):
            with open(path, encoding="utf-8") as handle:
                current = handle.read()
        if current == content:
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(content)

    for code, labels in lang_updates().items():
        path = os.path.join(ASSETS, "lang", "%s.json" % code)
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        missing = {key: value for key, value in labels.items() if lang.get(key) != value}
        if not missing:
            continue
        if check:
            stale.append("%s : %s" % (os.path.relpath(path, ROOT), ", ".join(sorted(missing))))
            continue
        lang.update(missing)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(lang, handle, indent=2, ensure_ascii=False)
            handle.write("\n")

    for target, source in TEXTURES.items():
        path = os.path.join(ASSETS, "textures", target)
        if jar and not check:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            shutil.copyfile(os.path.join(jar, "assets", "palamod", "textures", source), path)
        elif not os.path.exists(path):
            stale.append("%s absente (relancer avec --jar)" % os.path.relpath(path, ROOT))

    if stale:
        for line in stale:
            print("::error::presse : %s n'est pas a jour. Lance 'python3 -I tools/press_assets.py'." % line)
        sys.exit(1)
    print("presse : %d modeles, %d boites, fichiers a jour" % (
        len(MODELS), sum(len(part[4]) for model in MODELS.values() for part in model["parts"])))


if __name__ == "__main__":
    main()
