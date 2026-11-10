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
| 22b | Arbre de technologie : Global, Pillage, Défense, Économie | fait (en validation) |
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

## Arbre de technologie (22b)

Quatre branches (`FactionTree`), douze paliers (six pour l'Économie). Un palier demande, tous
ensemble, son coût payé par la banque de faction (et de l'or liquide pour l'Économie), ses
conditions et le palier précédent ; il s'améliore ensuite pendant une durée réelle (2 h à 14 j),
une amélioration à la fois par branche. Coûts, durées et récompenses sont ceux du wiki.

**Conditions remixées solo** : « N membres » → « N claims » ; « membres connectés N h » → « N h de
jeu » ; « visiter N factions » → « visiter N structures » (villages, temples, avant-postes…) ;
« tuer N joueurs différents » → « tuer N espèces différentes ». Les compteurs viennent des
statistiques du jeu (blocs parcourus, temps de jeu, blocs cassés, objets fabriqués) ou des compteurs
de faction (`FactionStats` : stick of god, outil de construction, et ceux des lots suivants). Un
objet pas encore porté affiche son lot (22c, 22d, 22e) et ne progresse pas.

**Récompenses** : les « emplacements de membre » de la branche Globale deviennent deux claims
chacun ; la Défense ajoute ses claims (10 à 200 par palier) ; F homes (1, 2 puis 3) et leur délai
(10, 5, 3 puis 0 s) ; réduction des dégâts de raid (-5 à -20 %) ; XP de donjon (+5 à +20 %) ; blason
personnalisé (palier 4 : copier une bannière à motifs) ; MOTD (palier 6) ; déblocages d'objets
(`use:dynamite`, `craft:hood_helmet`…, vérifiés par les objets des lots 22c et 22d) ; réserve d'or
(palier, valeur par or liquide, intérêts).

**Écrans** : l'arbre (quatre branches et ce qui est acquis), puis une branche : ses paliers, la
bulle de chaque condition avec sa progression, « PRÊT » quand tout est réuni. `/f arbre` ; pour les
opérateurs, `/f arbre finir` et `/f arbre palier <branche> <n>`.
