# Faction solo (chapitre 22)

La faction de Paladium (catégorie « Java | Faction » du wiki) remixée pour une partie **solo
PvE**. Choix du joueur :

- **progression seule** : la faction est ta base et ta progression ; ni factions rivales, ni PNJ ;
- **raids sur demande** : ta base n'est attaquée que si tu lances un raid de monstres ;
- **tous les objets** de pillage et de défense de PalaMod ;
- **events aux vraies heures** du calendrier de Paladium.

## Lots

| Lot | Contenu | État |
|-----|---------|------|
| 22a | Cœur : création, claims et protection, banque, homes, ELO et ligues, onglets, `/f` | fait |
| 22b | Arbre de technologie : Global, Pillage, Défense, Économie | à faire |
| 22c | Objets de défense : obsidiennes à effet, spikes obsi, Fake Water, éponges, brouilleur, enclumes, amulettes… | à faire |
| 22d | Objets de pillage : dynamites, TNT spéciales, withers custom, Unclaim Finder, arc en paladium, flèche de Switch… | à faire |
| 22e | Réserve d'or : multibloc, investissements à intérêts | à faire |
| 22f | Events à heures fixes (Egg Hunt, End, KOTH, Boss, Totem) et raids sur demande | à faire |

## Ce qui change en solo

| Paladium | DoHy solo |
|----------|-----------|
| Membres, grades, recrutement, annuaire | Pas d'objet : un seul membre, le chef |
| Claims protégés contre les autres factions | Claims protégés contre le monde : explosions de monstres, endermen, champs piétinés, intrus sur un serveur ; chaque claim peut refuser l'apparition des monstres |
| Classement : part des factions actives (5 % en Diamant…) | Paliers d'ELO cumulé (Argent 5 000, Or 15 000, Platine 35 000, Diamant 75 000) |
| Semaine sans victoire : une ligue plus bas, pas de récompense | Pareil, le lundi à minuit (heure du serveur) ; l'ELO revient au seuil de la ligue |
| Récompenses : Points Boutique et 2 % de l'argent du serveur | Argent versé à la banque de faction (25 k à 1,25 M $ selon la ligue) |
| Touche F | Touche G par défaut (F échange les mains en vanilla) |

## Cœur (22a)

- **Création** : touche de faction ou `/f create`, 100 lingots de paladium (wiki, « Gestion d'une
  faction »). Nom (3 à 16 caractères, sans caractères spéciaux), description, deux tags parmi
  douze (Farmeur, Mineur, Chasseur…), blason parmi les seize couleurs. Le MOTD s'ajoute en
  modification et s'affiche à la connexion.
- **Données** : `FactionData`, pièce jointe du joueur (conservée à la mort) ; chaque chunk
  revendiqué porte aussi son propriétaire (`ClaimOwner`), pour que la protection réponde sans le
  joueur.
- **Claims** : 5 au départ (l'arbre en ajoute), dans le Monde, le Nether ou l'End. Tes propres
  explosions (TNT, dynamites) cassent ton claim, celles des monstres non.
- **Banque de faction** : dépôts et retraits, historique des 30 derniers mouvements ; elle paiera
  l'arbre de technologie.
- **F homes** : posés dans un claim, rejoints après quelques secondes immobile. Aucun avant le
  palier 5 de la branche Globale, comme sur Paladium.
- **ELO et ligues** : gagnés aux events et aux raids (lot 22f) ; une ligue franchie se gagne tout de
  suite, la chute pour inactivité se fait le lundi.
- **Écrans** : création et modification (`FactionEditScreen`), puis les onglets sur le kit — page de
  la faction, banque, claims, classement, paramètres.
- **Commandes** : `/f` (ou `/faction`), `create`, `claim`, `unclaim`, `unclaimall confirm`,
  `home [n]`, `sethome [n]`, `bank deposit|withdraw`, `info`, `map`, `disband confirm`.
