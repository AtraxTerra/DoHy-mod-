#!/usr/bin/env python3
"""Le musee ne doit nommer que des pieces qui existent, une fois chacune.

`data/museum.json` liste les pieces a la main : c'est le prix d'un musee qui
n'expose que du rare. Le jeu, lui, ignore sans bruit une piece qu'il ne connait
pas — c'est voulu, une aile d'un mod absent doit simplement ne pas s'ouvrir.
Une faute de frappe dans un identifiant palamod aurait donc le meme effet
qu'un mod absent : une vitrine de moins, et personne pour le remarquer.

Le script verifie ce que le jeu ne signale pas :

- chaque piece `palamod:` a un nom francais, donc un objet enregistre ;
- une piece n'est nommee que dans une aile (un don ne remplit qu'une vitrine) ;
- chaque piece a sa ligne « ou la trouver », et aucune ligne n'est orpheline ;
- les cles d'aile sont uniques, les recompenses d'un type connu ;
- les textes sont sans accents, comme tout ce que le serveur envoie.

Les pieces `minecraft:` et celles des autres mods ne sont pas verifiables d'ici
(pas de registre hors du jeu) : le journal du serveur les liste au chargement,
ligne « Musee : pieces absentes de ce jeu ».
"""

import json
import pathlib
import sys

MUSEUM = pathlib.Path("data/museum.json")
LANG = pathlib.Path("src/main/resources/assets/palamod/lang/fr_fr.json")
REWARDS = {"attribute", "double_drop_chance", "buy_discount"}


def main():
    museum = json.loads(MUSEUM.read_text(encoding="utf-8"))
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    errors = []

    wings = list(museum.get("wings", []))
    patron = dict(museum.get("patron", {}))
    patron.setdefault("id", "patron")
    if not wings:
        errors.append("aucune aile")

    seen_wings = set()
    owner = {}
    for wing in wings + [patron]:
        key = wing.get("id", "")
        if not key:
            errors.append("une aile n'a pas de cle 'id'")
        if key in seen_wings:
            errors.append("cle d'aile en double : %s" % key)
        seen_wings.add(key)

        items = wing.get("items", [])
        hints = wing.get("hints", {})
        if not items:
            errors.append("%s : aucune piece" % key)
        for item in items:
            if item in owner:
                errors.append("%s est dans deux ailes : %s et %s" % (item, owner[item], key))
            owner[item] = key
            if item.startswith("palamod:"):
                name = item.split(":", 1)[1]
                if "item.palamod." + name not in lang and "block.palamod." + name not in lang:
                    errors.append("%s : %s n'a pas de nom francais, donc pas d'objet" % (key, item))
            if item not in hints:
                errors.append("%s : %s n'a pas de ligne dans 'hints'" % (key, item))
        for item in hints:
            if item not in items:
                errors.append("%s : 'hints' nomme %s, qui n'est pas une piece de l'aile" % (key, item))

        for text in [wing.get("display", "")] + list(hints.values()):
            if any(ord(char) > 127 for char in text):
                errors.append("%s : texte avec accents : %s" % (key, text))

        if not wing.get("rewards"):
            errors.append("%s : aucune recompense" % key)
        for reward in wing.get("rewards", []):
            if reward.get("type") not in REWARDS:
                errors.append("%s : recompense de type inconnu : %s" % (key, reward.get("type")))
            if reward.get("type") == "attribute" and not reward.get("attribute"):
                errors.append("%s : recompense d'attribut sans attribut" % key)

    if errors:
        for error in errors:
            print("::error::musee : %s" % error)
        return 1
    print("Musee : %d ailes, %d pieces, %d au panneau mecene."
          % (len(wings), sum(len(w["items"]) for w in wings), len(patron.get("items", []))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
