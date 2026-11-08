#!/usr/bin/env python3
"""Releve les recettes de table de PalaMod depuis son bytecode.

Pourquoi un extracteur et pas une transcription a la main : les recettes sont
eparpillees sur quatre registres (`PRegister_Craft`, `miner/init/Crafts`,
`hunter/init/Crafts`, `smeltery/registerer/PSRegister_Craft`) et il y en a
pres de 290. Les recopier a l'oeil garantit une faute de grille — et une faute
de grille ne casse rien : elle donne une recette qui ne fonctionne pas, ou pire
une recette plus avantageuse que l'originale, decouverte des mois plus tard par
un joueur qui l'exploite.

La forme en bytecode est tres reguliere, parce que `GameRegistry.addRecipe`
prend un `Object...` que javac construit toujours de la meme facon :

    new ItemStack ; dup
    getstatic <Registre>.<NOM>          <- le resultat
    iconst_1 / bipush N                 <- la quantite
    iconst_0                            <- le meta
    invokespecial ItemStack."<init>"
    bipush 9 ; anewarray Object         <- le tableau d'arguments
      dup ; iconst_0 ; ldc " F " ; aastore          <- les lignes de la grille
      dup ; iconst_3 ; bipush 70 ; Character.valueOf <- une cle ('F' = 70)
      dup ; iconst_4 ; getstatic ... ; aastore       <- son ingredient
    invokestatic GameRegistry.addRecipe

On lit donc chaque appel a l'envers : le resultat juste avant le tableau, puis
les elements du tableau dans l'ordre — des chaines tant qu'on n'a pas vu de
`Character`, puis des couples (cle, ingredient).

Usage :

    javap -p -c -constants -cp <jar extrait> <classe> > dump.txt
    python3 tools/extract_crafts.py dump.txt=<chemin/Classe.class> [...] > crafts.json

Le fichier .class est obligatoire en pratique : javap rogne les espaces de fin
des chaines, et les lignes de grille en ont souvent. Voir constant_pool().

Le script ne mappe rien vers les identifiants modernes : il rend les noms
d'origine tels quels. La correspondance est un choix de portage, elle appartient
a `tools/palamod_assets.py` et doit rester lisible.
"""

import json
import re
import struct
import sys

# Le descripteur fait partie du motif, et ce n'est pas du zele : addRecipe a
# DEUX surcharges. Celle qui prend un IRecipe (8 appels, des recettes a classe
# propre comme SpikeUpgradeRecipe) n'a pas de tableau d'arguments, et sans ce
# filtre le parseur remontait jusqu'au tableau de la recette PRECEDENTE — six
# recettes se retrouvaient attribuees au totem.
CALL = re.compile(
    r"// Method (?:cpw/mods/fml/common/registry/)?GameRegistry\."
    r"(addRecipe|addShapelessRecipe|addShapedRecipe):"
    r"\(Lnet/minecraft/item/ItemStack;\[Ljava/lang/Object;\)")
GETSTATIC = re.compile(r"getstatic\s+#\d+\s+// Field ([\w/$]+)\.(\w+):")
LDC_STRING = re.compile(r"ldc(?:_w)?\s+#(\d+)\s+// String (.*)$")
PUSH_INT = re.compile(r"(?:iconst_(\d)|bipush\s+(-?\d+)|sipush\s+(-?\d+))")
ANEWARRAY = re.compile(r"anewarray\s+#\d+\s+// class java/lang/Object")
CHAR_VALUEOF = re.compile(r"// Method java/lang/Character\.valueOf:")
NEW_ITEMSTACK = re.compile(r"new\s+#\d+\s+// class net/minecraft/item/ItemStack")
ITEMSTACK_INIT = re.compile(r'// Method net/minecraft/item/ItemStack\."<init>":\(([^)]*)\)')


def constant_pool(path):
    """Les chaines du pool de constantes, lues dans le fichier .class lui-meme.

    javap ROGNE LES ESPACES DE FIN de toutes les chaines qu'il affiche — dans le
    commentaire de `ldc` comme dans le pool de `javap -v`. Une ligne de grille
    « " Z " » devient « " Z" », et la recette sort avec des lignes de largeurs
    differentes. 26.2 la rejette au chargement (« each row must be the same
    width ») sans rien dire en jeu : onze recettes perdues — toutes les pioches,
    deux haches, trois charbons mixes — avant que ce lecteur existe.

    On lit donc le pool soi-meme : un `ldc #25` designe l'entree 25, une
    CONSTANT_String qui pointe vers un CONSTANT_Utf8, dont on prend les octets
    tels quels.
    """
    data = open(path, "rb").read()
    count = struct.unpack(">H", data[8:10])[0]
    pos = 10
    utf8 = {}
    strings = {}
    index = 1
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:                                   # Utf8
            length = struct.unpack(">H", data[pos:pos + 2])[0]
            utf8[index] = data[pos + 2:pos + 2 + length].decode("utf-8", "replace")
            pos += 2 + length
        elif tag == 8:                                 # String -> Utf8
            strings[index] = struct.unpack(">H", data[pos:pos + 2])[0]
            pos += 2
        elif tag in (7, 16, 19, 20):                   # Class, MethodType, Module, Package
            pos += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):     # Integer, Float, refs, NameAndType, Dynamic
            pos += 4
        elif tag in (5, 6):                            # Long, Double : deux entrees
            pos += 8
            index += 1
        elif tag == 15:                                # MethodHandle
            pos += 3
        else:
            raise ValueError("%s : tag de pool inconnu %d a l'entree %d" % (path, tag, index))
        index += 1
    return {i: utf8[target] for i, target in strings.items()}


def short(owner, name):
    """« BlocksRegister.COBBLEBREAKER » plutot que le chemin complet."""
    return "%s.%s" % (owner.rsplit("/", 1)[-1], name)


def pushed_int(line):
    match = PUSH_INT.search(line)
    if not match:
        return None
    for group in match.groups():
        if group is not None:
            return int(group)
    return None


POOL = None


def parse(lines):
    """Les recettes d'un dump, dans l'ordre du fichier."""
    recipes = []
    for index, line in enumerate(lines):
        kind = CALL.search(line)
        if not kind:
            continue
        recipe = read_call(lines, index, kind.group(1))
        if recipe:
            recipes.append(recipe)
    return recipes


def read_call(lines, call_index, kind):
    """Remonte du site d'appel jusqu'au `new ItemStack` du resultat."""
    array_index = None
    for back in range(call_index - 1, max(call_index - 400, -1), -1):
        if ANEWARRAY.search(lines[back]):
            array_index = back
            break
    if array_index is None:
        return None

    result = read_result(lines, array_index)
    arguments = read_arguments(lines, array_index, call_index)

    pattern = [argument for argument in arguments if argument[0] == "str"]
    rest = arguments[len(pattern):]

    keys = {}
    for position in range(0, len(rest) - 1, 2):
        key, ingredient = rest[position], rest[position + 1]
        if key[0] != "char":
            # Une recette sans grille (shapeless) : tout est ingredient.
            keys = None
            break
        if ingredient[0] != "ref":
            keys = None
            break
        # Le meta compte : la recette du totem demande un meta 4
        # (tete de creeper) et un meta 15 (poudre d'os). Le perdre
        # donnerait une tete de squelette et un colorant noir.
        keys[key[1]] = {"id": ingredient[1], "meta": ingredient[2]}

    grid = [p[1] for p in pattern]

    # Une grille peut nommer une case qu'aucune cle ne definit, et declarer une
    # cle qu'aucune case n'utilise. Ce n'est pas theorique : la recette du
    # cobblebreaker de PalaMod utilise « P » et definit « D ». En 1.7.10,
    # CraftingManager met null pour un caractere inconnu, donc la case est
    # VIDE — la pioche en paladium n'a jamais ete demandee. Un relevé qui
    # corrigerait la faute en silence donnerait une recette plus chere que
    # l'originale ; on la signale et on porte ce que le jeu faisait.
    used = {char for row in grid for char in row if char != " "}
    declared = set(keys or ())
    anomalies = {}
    if used - declared:
        anomalies["cases_sans_cle"] = sorted(used - declared)
    if declared - used:
        anomalies["cles_inutilisees"] = sorted(declared - used)

    return {
        "kind": kind,
        "result": result,
        "pattern": grid,
        "keys": keys,
        "anomalies": anomalies or None,
        # Conserve brut : une recette que le parseur n'a pas comprise doit
        # rester visible plutot que d'etre silencieusement simplifiee.
        "raw": [list(a) for a in arguments],
    }


def read_result(lines, array_index):
    """Le resultat : l'ItemStack construit juste avant le tableau.

    <p>La regle est positionnelle et non une recherche par mots-cles, et c'est
    une correction : la premiere version remontait jusqu'au premier
    {@code new ItemStack} croise dans les 60 lignes precedentes. Quand les
    INGREDIENTS sont eux-memes des {@code new ItemStack} — ce qui arrive des
    qu'un ingredient a un meta — elle attrapait celui de la recette d'avant, et
    attribuait six recettes au totem. Ici on part du dernier
    {@code invokespecial ItemStack."<init>"} avant le tableau, puis on remonte a
    son {@code new} : c'est, par construction de javac, le resultat.
    """
    init_index = None
    for back in range(array_index - 1, max(array_index - 40, -1), -1):
        if ITEMSTACK_INIT.search(lines[back]):
            init_index = back
            break
    if init_index is None:
        return {"item": None, "count": 1, "meta": 0}

    descriptor = ITEMSTACK_INIT.search(lines[init_index]).group(1)
    new_index = None
    for back in range(init_index - 1, max(init_index - 20, -1), -1):
        if NEW_ITEMSTACK.search(lines[back]):
            new_index = back
            break
    if new_index is None:
        return {"item": None, "count": 1, "meta": 0}

    item = None
    numbers = []
    for index in range(new_index, init_index):
        field = GETSTATIC.search(lines[index])
        if field and item is None:
            item = short(field.group(1), field.group(2))
            continue
        if item is not None:
            value = pushed_int(lines[index])
            if value is not None:
                numbers.append(value)

    # Les surcharges : (Block), (Item, int) et (Item, int, int). Le descripteur
    # dit laquelle, donc combien de nombres sont une quantite et un meta.
    count = numbers[0] if descriptor.count("I") >= 1 and numbers else 1
    meta = numbers[1] if descriptor.count("I") >= 2 and len(numbers) > 1 else 0
    return {"item": item, "count": count, "meta": meta}


def read_arguments(lines, array_index, call_index):
    """Les elements du tableau, dans l'ordre des `aastore`.

    <p>Un element peut etre une chaine (une ligne de grille, ou un nom de
    dictionnaire de minerais), un caractere (une cle), un champ statique, ou un
    {@code new ItemStack} quand l'ingredient porte un meta. Ce dernier cas est
    celui que la premiere version ignorait : la cle suivante se retrouvait
    appariee au mauvais ingredient, ou l'appariement cassait completement.
    """
    arguments = []
    index = array_index + 1
    while index < call_index:
        line = lines[index]

        if NEW_ITEMSTACK.search(line):
            # Un ingredient avec meta : on consomme jusqu'a son constructeur.
            item = None
            numbers = []
            end = index
            for forward in range(index + 1, call_index):
                end = forward
                if ITEMSTACK_INIT.search(lines[forward]):
                    break
                field = GETSTATIC.search(lines[forward])
                if field and item is None:
                    item = short(field.group(1), field.group(2))
                elif item is not None:
                    value = pushed_int(lines[forward])
                    if value is not None:
                        numbers.append(value)
            descriptor = ITEMSTACK_INIT.search(lines[end])
            meta = 0
            if descriptor and descriptor.group(1).count("I") >= 2 and len(numbers) > 1:
                meta = numbers[1]
            arguments.append(("ref", item, meta))
            index = end + 1
            continue

        text = LDC_STRING.search(line)
        if text:
            exact = POOL.get(int(text.group(1))) if POOL else None
            arguments.append(("str", exact if exact is not None else text.group(2)))
            index += 1
            continue

        if CHAR_VALUEOF.search(line):
            for back in range(index - 1, max(index - 4, array_index), -1):
                value = pushed_int(lines[back])
                if value is not None:
                    arguments.append(("char", chr(value)))
                    break
            index += 1
            continue

        field = GETSTATIC.search(line)
        if field:
            arguments.append(("ref", short(field.group(1), field.group(2)), 0))
            index += 1
            continue

        index += 1

    return arguments


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: extract_crafts.py <dump javap> [...]")

    global POOL
    everything = []
    for argument in sys.argv[1:]:
        # « dump.txt=Classe.class » : la classe donne les chaines exactes. Sans
        # elle on retombe sur le texte de javap, et les grilles a espace final
        # seront fausses — c'est signale plutot que tolere.
        path, _, class_file = argument.partition("=")
        POOL = constant_pool(class_file) if class_file else None
        if POOL is None:
            print("%s : pas de fichier .class, les espaces de fin des grilles seront "
                  "perdus" % path, file=sys.stderr)
        with open(path, encoding="utf-8", errors="replace") as handle:
            lines = handle.read().split("\n")
        found = parse(lines)
        for recipe in found:
            recipe["source"] = path.rsplit("/", 1)[-1]
        everything.extend(found)
        print("%s : %d recettes" % (path, len(found)), file=sys.stderr)

    json.dump(everything, sys.stdout, indent=2, ensure_ascii=False)
    print(file=sys.stdout)
    return 0


if __name__ == "__main__":
    sys.exit(main())
