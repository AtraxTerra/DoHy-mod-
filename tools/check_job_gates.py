#!/usr/bin/env python3
"""Tout gestionnaire de verrou de metier, ou de technologie, doit exempter le creatif.

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
# Les verrous de technologie (chapitre 23, lot 23d) obeissent a la meme regle.
TECH_EVENTS = pathlib.Path("src/main/java/fr/atraxterra/dohy/faction/tech/TechGateEvents.java")
TECH_GATE = pathlib.Path("src/main/java/fr/atraxterra/dohy/faction/tech/TechGate.java")

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


def check(events_path, gate_path, events_name, gate_name, what):
    """Les controles d'une paire de classes : l'application (les gestionnaires) et la resolution."""
    errors = []

    if not events_path.exists():
        return [f"{events_path} absent"], 0

    text = events_path.read_text(encoding="utf-8")

    marker = "private static boolean exempt("
    if marker not in text:
        errors.append(
            f"{events_name} n'a plus de methode exempt() : les joueurs en creatif "
            f"repassent par les verrous {what}")
    else:
        body = text.split(marker, 1)[1].split("\n    }", 1)[0]
        if not any(call in body for call in GAME_MODE_CALLS):
            errors.append(
                f"{events_name}.exempt() ne teste aucun mode de jeu : elle ne peut donc plus "
                "reconnaitre un joueur en creatif")

    found = handlers(text)
    if not found:
        errors.append(
            f"aucun gestionnaire @SubscribeEvent trouve dans {events_name} : les "
            "motifs de ce script ne correspondent plus au fichier")
    for name, body in sorted(found.items()):
        if "exempt(" not in body:
            errors.append(
                f"{events_name}.{name} n'appelle pas exempt() : ce verrou s'appliquerait aussi "
                "en creatif, et ca ne se voit qu'en jeu")

    # L'exemption ne doit pas migrer dans la couche qui repond « a-t-il le
    # niveau (la technologie) ? », dont se servent les ecrans pour afficher la
    # progression.
    if gate_path.exists():
        gate = gate_path.read_text(encoding="utf-8")
        allows = gate.split("static boolean allows(", 1)
        if len(allows) > 1:
            body = allows[1].split("\n    }", 1)[0]
            for call in GAME_MODE_CALLS:
                if call in body:
                    errors.append(
                        f"{gate_name}.allows teste {call} : l'exemption appartient a "
                        f"{events_name}. Ici elle afficherait « tout deverrouille » "
                        "et masquerait la vraie progression")
    return errors, len(found)


def main():
    errors = []
    job_errors, jobs = check(EVENTS, GATE, "JobGateEvents", "JobGate", "de metier")
    tech_errors, techs = check(TECH_EVENTS, TECH_GATE, "TechGateEvents", "TechGate", "de technologie")
    errors += job_errors + tech_errors

    if errors:
        for message in errors:
            print(f"::error::{message}")
        print(f"\n{len(errors)} probleme(s) sur les verrous.", file=sys.stderr)
        return 1

    print(f"verrous de metier : {jobs} gestionnaire(s) ; verrous de technologie : {techs} gestionnaire(s) ;"
          " tous exemptent le creatif, et allows() dit toujours la verite")
    return 0


if __name__ == "__main__":
    sys.exit(main())
