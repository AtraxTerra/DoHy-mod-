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
| 22c | Objets de défense : obsidiennes à effet, spikes obsi, Fake Water, éponges, brouilleur, enclumes, amulettes… | fait |
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

## Objets de défense (22c)

Tout est fabricable ; c'est la **pose ou l'usage** qui demande le palier de l'arbre (`DefenseGate`),
comme les verrous de métier : 26.2 ne sait pas annuler une fabrication sans détruire ses
ingrédients. Sans faction, rien n'est débloqué ; le créatif passe outre.

- **Obsidienne renforcée** (4 obsidiennes → 4), ses escaliers et sa dalle : on ne la mine pas,
  la TNT ordinaire ne l'entame pas (résistance 60), un wither la casse. **L'outil magique**
  (10 000 usages) la retire intacte, comme tout bloc du tag `palamod:magical_tool_removable`.
- **Recettes** : celles de PalaMod (`PRegister_Craft`, `ObsiEffect`, `PPRegisterer`, amulettes du
  Chasseur). Le coffre en fer d'Iron Chests (obsidienne à wither) devient un coffre, la ficelle de
  diamant (amulette du chasseur) une ficelle ; discrétion et combat, désactivées sur Paladium, et le
  home remover reçoivent une recette.
- **Obsidiennes piégées** : empoisonnée, boom, méga boom, de lave, sulfurique, knockback, gelée,
  anti-aggro, compacte, slime, à wither, et une par spike (bois, fer, or, diamant, améthyste,
  titane, paladium). Détruites par un wither, une explosion assez forte (pillage, lot 22d) ou un
  raid (lot 22f), elles déclenchent leur effet au tick suivant ; l'outil magique ne les déclenche
  pas. Valeurs relevées au bytecode (`ObsiEffect`). **Solo** : le piège frappe tout ce qui vit
  autour sauf le propriétaire du claim ; le poison passe en Wither sur les morts-vivants ;
  l'anti-aggro calme tous les monstres à 6 blocs. Plus l'obsidienne à redstone (un fil en bloc)
  et un observateur en obsidienne.
- **Spikes** en bois, fer, or, diamant, améthyste et titane (2 à 12 points de dégâts), à côté de
  celui en paladium. Escaliers, dalle, porte (que les zombies n'enfoncent pas) et trappe en
  obsidienne.
- **Fake Water** (seau : eau + bâton en paladium) : de l'eau qui ronge, 2 points par seconde, sauf
  pour le propriétaire du claim. **Éponge à lave** (boit la lave comme l'éponge l'eau, se vide au
  four) et **éponge compressée** (ingrédient des TNT du pillage).
- **Enclumes** en améthyste, titane et paladium : l'enclume vanilla, qui ne s'abîme pas, qu'on ne
  mine pas, et qui encaisse 1, 2 ou 3 coups de wither.
- **Brouilleur de coordonnées** — Paladium : faux F3 aux joueurs proches. **Solo** : à 16 blocs,
  les monstres perdent de vue les joueurs à plus de 6 blocs. Sa recette demande l'Unclaim Finder
  rouge : une boussole le remplace jusqu'au lot 22d.
- **Pistolet à liquide** : pompe les sources (eau, lave, eau angélique, Fake Water ; 16 seaux) et
  les pose à 24 blocs. **Home remover** — Paladium : efface les homes ennemis d'un chunk.
  **Solo** : efface les monstres du chunk et y empêche leur apparition une heure. **Pierre
  d'aggro** : un monstre lâche sa cible et te prend pour la sienne (4 usages).
- **Amulettes**, fondues à l'enclume dans un plastron en paladium ou en endium : magma (résistance
  au feu, comme Paladium) ; **solo** pour les autres : voyance (les monstres à 24 blocs brillent),
  discrétion (ils te voient deux fois moins loin), combat (+15 % contre eux), chasseur (+25 % d'XP
  de Chasseur).

| Clé de l'arbre | Ce qu'elle débloque |
|----------------|---------------------|
| `craft:voyance_amulet` | fondre l'amulette de voyance |
| `craft:hardened_obsidian` | poser l'obsidienne renforcée, ses escaliers, sa dalle |
| `use:spikes_basic` / `_amethyst` / `_titane` / `_paladium` | poser les spikes et leurs obsidiennes |
| `use:<effet>_obsidian` | poser l'obsidienne piégée |
| `craft:coordinate_jammer`, `craft:lava_sponge` | poser le brouilleur, l'éponge à lave |
| `craft:aggro_stone`, `craft:fake_water` | utiliser la pierre d'aggro, vider le seau de Fake Water |
| `craft:<métal>_anvil` | poser l'enclume |
| `use:pistol_tank`, `use:home_remover` | utiliser le pistolet, le home remover |

