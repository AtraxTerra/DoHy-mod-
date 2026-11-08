#!/usr/bin/env python3
"""Cherche les champs statiques qui lisent un champ declare plus bas.

Pourquoi ce controle existe : le 2026-11-08, le mod a plante au demarrage sur
un `ExceptionInInitializerError` parce qu'un champ statique appelait une
methode qui lisait une liste declaree **trois lignes plus bas**. Les champs
statiques s'initialisent dans l'ordre du fichier, donc la liste valait encore
`null`.

Le compilateur ne voit rien : une lecture directe serait refusee (« illegal
forward reference »), mais **passer par un appel de methode contourne la
regle**. Le jar se construit, la CI est verte, et le jeu refuse de demarrer.

Le controle suit donc le meme chemin que la JVM :

    champ statique -> methode statique privee appelee -> champs qu'elle lit

et signale ceux qui sont declares apres le champ qui les declenche.

    python3 tools/check_static_init.py

La parade a preferer reste de passer la valeur en argument : le compilateur
retrouve alors la forward reference et refuse tout seul.
"""
import pathlib
import re
import sys

SOURCE = pathlib.Path("src/main/java")

# Declaration d'un champ statique, avec ou sans initialiseur.
FIELD = re.compile(r'^\s*(?:public|protected|private)?\s*static\s+(?:final\s+)?'
                   r'[\w.<>,?\[\]\s]+?\s+([A-Z_][A-Z0-9_]*)\s*=', re.MULTILINE)
# Declaration d'une methode statique privee.
METHOD = re.compile(r'^\s*private\s+static\s+[\w.<>,?\[\]\s]+?\s+(\w+)\s*\(', re.MULTILINE)


def body_of(text, start):
    """Le corps de la methode qui commence a cet index, par comptage d'accolades."""
    opening = text.find("{", start)
    if opening < 0:
        return ""
    depth = 0
    for index in range(opening, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[opening:index]
    return text[opening:]


def check(path):
    text = path.read_text(encoding="utf-8")
    problems = []

    # Ordre de declaration des champs statiques.
    order = {}
    for match in FIELD.finditer(text):
        order.setdefault(match.group(1), match.start())
    if not order:
        return problems

    # Corps de chaque methode statique privee.
    bodies = {}
    for match in METHOD.finditer(text):
        bodies[match.group(1)] = body_of(text, match.end())

    for match in FIELD.finditer(text):
        field = match.group(1)
        position = match.start()
        # L'initialiseur : de « = » jusqu'au point-virgule de fin.
        end = text.find(";", match.end())
        initializer = text[match.end():end if end > 0 else len(text)]

        for method, body in bodies.items():
            if not re.search(r'\b%s\s*\(' % re.escape(method), initializer):
                continue
            for other, other_position in order.items():
                if other == field or other_position <= position:
                    continue
                if re.search(r'\b%s\b' % re.escape(other), body):
                    problems.append(
                        f"{path}: le champ {field} appelle {method}(), qui lit {other} "
                        f"— declare PLUS BAS, donc encore null a ce moment. "
                        f"Passer {other} en argument, ou le declarer avant {field}.")
    return problems


def main():
    problems = []
    for path in sorted(SOURCE.rglob("*.java")):
        problems.extend(check(path))

    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        print(f"\n{len(problems)} initialisation(s) statique(s) dans le mauvais ordre.",
              file=sys.stderr)
        return 1

    print("ordre des initialisations statiques : rien a signaler")
    return 0


if __name__ == "__main__":
    sys.exit(main())
