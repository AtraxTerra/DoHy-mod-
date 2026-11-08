#!/usr/bin/env python3
"""Tout gestionnaire de verrou de metier doit exempter le creatif.

Le probleme que ce script attrape est une faute d'omission, pas d'ecriture.
`JobGateEvents` annule des evenements quand le joueur n'a pas le niveau, et le
creatif est exempte dans chaque gestionnaire. Le chapitre 5 ajoutera des
verrous (seedplanters, totem, grinder) : un gestionnaire de plus ecrit sur le
modele des trois autres mais sans le test d'exemption compile, se charge,
tourne, et bloque les administrateurs en creatif. Personne ne le remarque avant
qu'un joueur s'en plaigne.

Le script lit les gestionnaires annotes `@SubscribeEvent` de la classe et exige
que chacun appelle `exempt(`. Il verifie aussi que l'exemption n'a pas migre
dans `JobGate.allows`, ou elle afficherait « 70/70 » a un administrateur de
niveau 1 et masquerait sa vraie progression.
"""

import pathlib
import re
import sys

EVENTS = pathlib.Path("src/main/java/fr/atraxterra/dohy/job/JobGateEvents.java")
GATE = pathlib.Path("src/main/java/fr/atraxterra/dohy/job/JobGate.java")

# Les marques d'un test de mode de jeu, pour reperer une exemption egaree.
GAME_MODE_CALLS = ("isCreative", "hasInfiniteMaterials", "getAbilities", "gameMode")


def handlers(text):
    """Les gestionnaires d'evenement et leur corps, par nom."""
    found = {}
    for match in re.finditer(r"@SubscribeEvent\s+public static void (\w+)\(", text):
        name = match.start(1) and match.group(1)
        # Le corps va de l'accolade ouvrante a la premiere accolade fermante
        # en colonne 4 : c'est l'indentation des methodes de cette classe.
        start = text.index("{", match.end())
        end = text.index("\n    }", start)
        found[name] = text[start:end]
    return found


def main():
    errors = []

    if not EVENTS.exists():
        print(f"::error::{EVENTS} absent", file=sys.stderr)
        return 1

    text = EVENTS.read_text(encoding="utf-8")

    marker = "private static boolean exempt("
    if marker not in text:
        errors.append(
            "JobGateEvents n'a plus de methode exempt() : les joueurs en creatif "
            "repassent par les verrous de metier")
    else:
        body = text.split(marker, 1)[1].split("\n    }", 1)[0]
        if not any(call in body for call in GAME_MODE_CALLS):
            errors.append(
                "exempt() ne teste aucun mode de jeu : elle ne peut donc plus "
                "reconnaitre un joueur en creatif")

    found = handlers(text)
    if not found:
        errors.append(
            "aucun gestionnaire @SubscribeEvent trouve dans JobGateEvents : les "
            "motifs de ce script ne correspondent plus au fichier")
    for name, body in sorted(found.items()):
        if "exempt(" not in body:
            errors.append(
                f"{name} n'appelle pas exempt() : ce verrou s'appliquerait aussi "
                "en creatif, et ca ne se voit qu'en jeu")

    # L'exemption ne doit pas migrer dans la couche qui repond « a-t-il le
    # niveau ? », dont se servent le Hub, JobsMenu et /metiers pour afficher la
    # progression.
    if GATE.exists():
        gate = GATE.read_text(encoding="utf-8")
        allows = gate.split("static boolean allows(", 1)
        if len(allows) > 1:
            body = allows[1].split("\n    }", 1)[0]
            for call in GAME_MODE_CALLS:
                if call in body:
                    errors.append(
                        f"JobGate.allows teste {call} : l'exemption appartient a "
                        "JobGateEvents. Ici elle afficherait « tout deverrouille » "
                        "dans /metiers et masquerait la vraie progression")

    if errors:
        for message in errors:
            print(f"::error::{message}")
        print(f"\n{len(errors)} probleme(s) sur les verrous de metier.", file=sys.stderr)
        return 1

    print(f"verrous de metier : {len(found)} gestionnaire(s), tous exemptent le "
          "creatif, et allows() dit toujours le niveau reel")
    return 0


if __name__ == "__main__":
    sys.exit(main())
