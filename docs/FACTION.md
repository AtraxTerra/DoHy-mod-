# Faction solo (chapitre 22)

La faction de Paladium (catégorie « Java | Faction » du wiki) remixée pour une partie **solo
PvE**. Choix du joueur :

- **progression seule** : la faction est ta base et ta progression ; pas de faction rivale vivante — seulement des
  **bases ennemies fictives**, générées sur demande pour être pillées (lot 22g) ;
- **pas de raids** sur ta base (choix du joueur) ;
- **tous les objets** de pillage et de défense de PalaMod ;
- **events aux vraies heures** du calendrier de Paladium.

## Lots

| Lot | Contenu | État |
|-----|---------|------|
| 22a | Cœur : création, claims et protection, banque, homes, ELO et ligues, onglets, `/f` | fait |
| 22b | Arbre de technologie : Global, Pillage, Défense, Économie | fait |
| 22c | Objets de défense : obsidiennes à effet, spikes obsi, Fake Water, éponges, brouilleur, enclumes, amulettes… | fait |
| 22d | Objets de pillage : dynamites, TNT spéciales, Unclaim Finder, explorateur de coffre, arc en paladium, flèche de Switch… | fait |
| 22d-bis | Withers du pillage : réceptacle, gemmes de wither, améliorations | fait |
| 22e | Réserve d'or : multibloc, investissements à intérêts | fait |
| 22f | Events à heures fixes : le calendrier et le **Boss** | fait |
| 22f-2 | Events : KOTH à points et Totem | fait |
| 22f-3 | Event : Egg Hunt (l'End et les raids ne sont pas repris : choix du joueur) | fait |
| 22g | Bases ennemies sur demande, à plus de 9 000 blocs, à piller : trois tailles, cinq genres, terrain qui revient après le raid | fait |
| 22h | Notation de la semaine : une note sur 100 chaque lundi, une bourse à la banque de faction (repris de NationsGlory) | fait |
| 22i | Pays à prendre, sur une carte de la Terre : capitale, conquête des pays aux events contre les nations rivales | fait |
| 22j | Refonte : la faction est une nation (rangs, page unifiée), marché international à stock mondial, notation à cinq critères avec classement face aux nations | fait |

## Ce qui change en solo

| Paladium | DoHy solo |
|----------|-----------|
| Membres, grades, recrutement, annuaire | Pas d'objet : un seul membre, le chef |
| Claims protégés contre les autres factions | Claims protégés contre le monde : explosions de monstres, endermen, champs piétinés, intrus sur un serveur ; chaque claim peut refuser l'apparition des monstres |
| Classement : part des factions actives (5 % en Diamant…) | Paliers d'ELO cumulé (Argent 5 000, Or 15 000, Platine 35 000, Diamant 75 000) |
| Semaine sans victoire : une ligue plus bas, pas de récompense | Pareil, le lundi à minuit (heure de Paris) ; l'ELO revient au seuil de la ligue |
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
objet pas encore porté affiche son lot et ne progresse pas.

**Récompenses** : les « emplacements de membre » de la branche Globale deviennent deux claims
chacun ; la Défense ajoute ses claims (10 à 200 par palier) ; F homes (1, 2 puis 3) et leur délai
(10, 5, 3 puis 0 s) ; réduction des dégâts en event (-5 à -20 %, contre les raids sur Paladium) ; XP de donjon (+5 à +20 %) ; blason
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
  Chasseur). Le coffre en fer d'Iron Chests (obsidienne à wither) devient un coffre ; discrétion et
  combat, désactivées sur Paladium, et le home remover reçoivent une recette.
- **Obsidiennes piégées** : empoisonnée, boom, méga boom, de lave, sulfurique, knockback, gelée,
  anti-aggro, compacte, slime, à wither, et une par spike (bois, fer, or, diamant, améthyste,
  titane, paladium). Détruites par un wither, une explosion assez forte (pillage, lot 22d) ou un
  raid, elles déclenchent leur effet au tick suivant ; l'outil magique ne les déclenche
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
  rouge.
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

## Objets de pillage (22d)

Mêmes verrous que la défense (`DefenseGate`) : tout se fabrique, l'usage demande le palier. Les
compteurs de la branche Pillage sont branchés (dynamites lancées, flèches de Switch, nappes vidées,
modificateurs posés, secondes de Fake Water évitées, Unclaim Helmet posé, withers invoqués).

- **Dynamites** (normale, ninja, big) : se lancent, se collent au premier bloc ou à la première
  créature touchée, et sautent 2 à 2,5 s plus tard (puissance 2,5, 3 et 4,5 ; la TNT vaut 4). La
  ninja est muette. Explosion de TNT : l'obsidienne, les portes renforcées et l'eau tiennent.
- **TNT du pillage** — compacte (6), en **endium** (8,8, la valeur du wiki ; le code disait 13,2)
  qui **perce** : jusqu'à dix blocs d'obsidienne à trois blocs cèdent, les obsidiennes piégées se
  déclenchent ; **éponge** (boit les liquides d'un cube de 5, sans exploser) ; **magique** (4, plus
  cécité II et poison II à 5 blocs) ; **à wither** (un wither vanilla, un second une fois sur cent).
  Toutes s'allument comme la TNT ; les claims des autres ne sont ni percés ni asséchés.
- **Unclaim Finders** vert, orange, rouge et bleu (1, 9, 25, 49 chunks) — Paladium : la part de
  tile entities, qui trahit une base non claim. **Solo** : les conteneurs (coffres de structures,
  de donjons, de bases à piller), 10 % par conteneur ; le bleu donne la direction du plus proche.
  **Spawner Finder** : les spawners de 9 chunks. Tenus en main, ils s'usent (2 h 30) et un
  cartouche du kit s'affiche au milieu du bord gauche. Orange, rouge et bleu sortent de la
  palamachine ; ce qui en sort compte maintenant comme fabriqué.
- **Explorateur de coffre** : accroupi, clic droit sur un conteneur → son contenu en lecture seule,
  sans l'ouvrir (un coffre de structure scellé tire son butin).
- **Détecteur de présence** — Paladium : allumé quand un membre d'une faction donnée est
  connecté. **Solo** : allumé, avec un signal de redstone 15, quand un monstre rôde à 16 blocs.
- **Seau extrapolé** (32 usages) : vise un liquide, toute la nappe disparaît (512 blocs au plus).
- **Arc en paladium** (900 usages, flèches à 3 de dégâts) et ses modificateurs **Range** (flèche
  1,5 fois plus rapide) et **Speed** (se bande 1,5 fois plus vite), posés à l'**enclume** au lieu
  de la Bow Machine. **Flèche de Switch** : tirée à cet arc, tireur et cible échangent leur place.
- **Cagoule** (le casque en fer de 26.2, sur la couche de PalaMod). **Solo** : une fois débloquée,
  les monstres te repèrent à 60 % de leur distance.
- **Ficelle de diamant** : l'arc, la dynamite ; l'amulette du chasseur la retrouve.
- **Enchantements** de la table de l'Alchimiste : **Unclaim Helmet** (le casque vaut un finder
  vert) et **Fake Water Protection** (la Fake Water ne ronge plus ; débloqué au palier 6).

| Clé de l'arbre | Ce qu'elle débloque |
|----------------|---------------------|
| `use:dynamite`, `use:ninja_dynamite`, `use:big_dynamite` | lancer la dynamite |
| `craft:hood_helmet` | l'effet de la cagoule |
| `craft:chest_explorer`, `use:online_detector` | utiliser l'explorateur, poser le détecteur |
| `craft:paladium_bow`, `craft:switch_arrow` | tirer à l'arc en paladium, l'échange de la flèche |
| `craft:extrapolated_bucket` | utiliser le seau extrapolé |
| `enchant:fake_water_protection` | l'enchantement à la table de l'Alchimiste |
| `use:wither` | poser la TNT à wither |

## Withers du pillage (22d-bis)

Le module PalaWither n'est pas dans les jars fournis : ses recettes le sont (`PPalaWither.init`), ses
comportements viennent du wiki (« Réceptacle à Wither », « Pour le pillage »).

- **Réceptacle à wither** (3 quartz électrifiés, un crâne de wither, des planches) : clic droit
  avec une gemme, et l'invocation commence. Il faut une faction, la clé de l'arbre de ce wither, le
  niveau d'Alchimiste des gemmes majeures (assoiffé 70, prédateur 90, suprême 100), et un réceptacle
  **hors de tout claim**. L'invocateur reste à 6 blocs (5 s à 20 min selon le wither) ; absent plus
  d'une minute, l'invocation échoue et la gemme est perdue. Le wither naît dans la charge du wither
  vanilla. **15 000 PV**, entamés par les explosions proches (une TNT à un bloc : environ 350), avec
  une barre quand on le regarde. Les withers à 6 blocs regagnent 2 PV par seconde.
- **Withers à combattre** : de base (le wither vanilla), **amusant** (même force, la peau et les sons
  du « funny wither » de PalaMod), **baby** (deux fois plus petit, 150 PV, des têtes deux fois moins
  fortes).
- **Withers alliés** — sur Paladium, « passifs » ou « non agressifs envers leur faction ». **Solo** :
  ils n'attaquent ni toi ni les joueurs, seulement les monstres, et cassent ce qu'ils touchent.
  **Passif** (300 PV) ; **assoiffé** (450 PV, insensible aux liquides, ne lance que des têtes
  bleues qui percent l'obsidienne, n'avance que poussé) ; **prédateur** (600 PV, immobile, va où le
  **laser de ciblage** l'envoie) ; **suprême** (intuable, guidé au laser, éveillé au bout d'une
  minute au lieu de douze heures, appelle un prédateur toutes les deux minutes, saute au bout de
  15 min).
- **Améliorations** (une par wither, une heure) : **discrétion** (ni barre de vie ni bruit),
  **résistance** (dégâts hors joueurs divisés par deux), **visqueuse** (casse la slime obsidienne
  sans la déclencher, +20 PV par bloc de slime). Ni discrétion ni résistance sur le suprême.
- **Selle de wither** et **canne à pêche de wither** : on s'assoit sur un wither (de base, amusant,
  baby, passif), la canne en main il suit le regard ; l'eau le desselle, et une selle ne sert qu'une
  fois. **Flèche de mi-vie** : tirée à l'arc en paladium, un wither perd la moitié de sa vie (ni le
  prédateur ni le suprême). **Poudre de wither** : une sorcière sur deux, dès Chasseur 20 ; quatre
  font un fragment de crâne.
- **Remplacements** : la pâte de camouflage de SecretRooms (amélioration de discrétion) devient la
  membrane de phantom, la boussole du chasseur (laser) une boussole, l'obsidienne infusée de
  paladium (tête suprême) l'obsidienne compacte. La gemme de base se fabrique dans la forme du
  wither vanilla ; l'amusante (Lucky Blocks sur Paladium) avec du colorant jaune.
- **TNT à wither** : elle invoque désormais le wither de base du pillage, compté à l'allumeur.
- **Apparence**, d'après les rendus du wiki (`tools/wither_skins.py`, `PalaWitherGeometry`) :
  **prédateur** anthracite aux dessus lavande, yeux, bouche et braises des côtes incandescents,
  deux cornes en L ; **suprême** violet, yeux et croix de poitrine roses lumineux, épines sur les
  têtes, deux grands bras griffus qui se balancent ; **assoiffé** couleur d'os sec, des flèches
  plantées dans les crânes et les côtes ; **baby** ardoise aux yeux cyan lumineux ; **passif** os
  pâle couvert de mousse, des pousses sur les têtes. Le wither de base garde la peau vanilla,
  l'amusant celle de PalaMod. Pendant la charge, chaque genre clignote dans sa peau bleue.
  Le **réceptacle** est un autel qui suit sa recette : socle de planches, dalle d'obsidienne, quatre
  piliers de quartz électrifié coiffés de quartz compressé, et le crâne de wither aux yeux allumés,
  tourné vers qui l'a posé.

| Clé de l'arbre | Ce qu'elle débloque |
|----------------|---------------------|
| `craft:receptacle` | poser le réceptacle |
| `use:baby_wither`, `use:wither`, `use:passive_wither`, `use:funny_wither` | invoquer ces withers |
| `use:thirsty_wither`, `use:predator_wither`, `use:supreme_wither` | invoquer les withers majeurs |
| `craft:wither_resistance_upgrade`, `craft:wither_slime_upgrade`, `craft:stealth_upgrade` | poser l'amélioration |

Compteurs branchés : `spawn:<wither>`, `regen:wither`, `wither:obsidian` (obsidiennes cassées ou
soufflées par un wither que tu as invoqué), `kill:baby_wither`.

## Bases ennemies (22g)

Seul, les objets de pillage n'avaient pas de cible. Sur demande (onglet **Bases ennemies** de la faction,
ou `/f base generer [1-3] [genre]`), une faction ennemie fictive bâtit une place forte dans le Monde :

- **À plus de 9 000 blocs** de toi *et* de chacun de tes claims (emprise comprise), entre 9 000 et
  10 500 blocs environ, dans la bordure du monde, hors océan, rivière et plage, sur le terrain le plus
  plat trouvé en 64 essais — la hauteur est lue dans le bruit du générateur, sans rien générer.
- Le site est cherché sur plusieurs ticks (8 ms au plus), ses chunks se chargent en tâche de fond (ticket
  forcé), puis la base est **bâtie par étapes**, 15 ms par tick : même la forteresse ne fige pas le
  serveur. Une **boussole** pointe vers elle (`/f base boussole` pour une autre). Une base à la fois.
- **Ses claims suivent la règle de Paladium** et débordent de **deux chunks de glacis** autour des
  murs : on n'y casse, n'y pose ni n'y ouvre rien à la main — **même en créatif**. La TNT ne s'y pose
  donc pas : elle arrive en **dynamite** lancée, tirée par un **canon à TNT** bâti hors des claims, ou
  c'est un **wither** invoqué dehors (le réceptacle veut un chunk libre) qu'on amène au mur. Les
  distributeurs tirent les dynamites et allument les TNT de PalaMod (grosse, endium, éponge, effet,
  wither) comme la TNT vanilla : les canons à TNT marchent avec toutes. Les
  explosions et les withers y cassent tout ; le seau extrapolé et le pistolet à liquide y travaillent.
  Ses pièges — et, avec NTM NEXT, ses tourelles, ses mines et ses barbelés — frappent l'assaillant.
- **Entrer dans la salle des coffres, c'est l'avoir pillée** : ELO, argent à la banque de
  faction, et ses claims tombent — les coffres s'ouvrent. Compteurs `raid:base` et `raid:<taille>`.
- **Abandonner** (`/f base abandonner`, deux clics dans l'onglet) l'oublie, sans récompense.
- **Cinq minutes après la fin du raid** (pillage ou abandon), la base s'efface et **le terrain revient
  tel qu'il était** — voir plus bas.

### Trois tailles, quatre genres militaires (refonte du 2026-11-13)

La **taille** fixe l'enceinte, la **récompense** et le nombre de défenseurs ; le **genre** fixe ce qu'il y
a entre les murs, la couleur du béton et la façon d'atteindre les coffres. Le genre se choisit dans
l'onglet (« Au hasard » par défaut) ou en dernier argument de la commande (`silo`, `bunker`,
`sovietique`, `radar`). La refonte du 2026-11-13 a remplacé les cinq genres d'avant (citadelle, domaine
agricole, entrepôt, usine, démonstration de force) par des bases militaires ; **le système de raid, lui,
n'a pas bougé** : mêmes couches, mêmes tailles, même salle des coffres. Une base d'avant la refonte
encore en cours s'affiche comme une forteresse soviétique.

| Taille | Emprise, claims | Enceintes | Salle forte | Défenseurs | Récompense |
|--------|-----------------|-----------|-------------|------------|------------|
| Avant-poste | 61 × 61, 9 × 9 chunks | une : béton · eau · béton, 8 de haut, fossé d'eau | 3 couches : béton · eau · obsidienne | DEF1 | +150 ELO, 40 000 $ |
| Base | 93 × 93, 11 × 11 chunks | deux : obsidienne · eau · obsidienne piégée · renforcée (11 de haut), puis béton · eau · renforcée, chacune entre deux parements de béton ; fossé et douve de Fake Water, rang de barbelés | 5 couches : renforcée · eau · obsidienne · boom · béton | DEF2 | +500 ELO, 200 000 $ |
| Forteresse | 129 × 129, 13 × 13 chunks | deux : renforcée · eau · piégée · obsidienne · eau · renforcée (15 de haut), puis renforcée · eau · piégée · obsidienne, entre deux parements ; douve de Fake Water, fossé de lave, rang de barbelés empoisonnés | 7 couches : renforcée · eau · enclumes en paladium et anti-aggro · obsidiennes à wither · eau · mega boom · blackstone | DEF3 | +1 500 ELO, 800 000 $ |

Le béton est du **béton du jeu** (et du grès pour la base radar) : il se casse à la TNT comme la pierre
qu'il remplace, et les parements s'ajoutent aux couches sans en retirer — une base n'est pas plus
facile qu'avant. Le béton de NTM, presque insensible aux explosifs, n'est jamais employé.

Le plan commun, du dehors au dedans :

- un **fossé** en eau et un pont d'asphalte bordé de grillage, devant la **porterie** au nord : la
  porte blindée coulissante de NTM (une herse sans le mod) entre deux miradors, un linteau, un seuil
  rayé ;
- l'**enceinte** à couches : de l'eau est prise entre deux murs, donc une TNT qui perce le premier mur
  explose ensuite dans l'eau sans rien casser ; il faut vider l'eau (seau extrapolé, TNT éponge).
  Dessus, un **chemin de ronde de béton**, un parapet et du **barbelé**. Des **miradors** d'angle, et
  de courtine à partir de la base : un fût de béton plein à bandes, un toit-terrasse à grillage, un
  garde, le drapeau du genre, un **projecteur halogène** allumé par un levier ; leur tourelle est sur
  un **balcon de tir** à leur pied ;
- l'anneau des **quartiers** : des terrains d'une quinzaine de blocs, tirés au sort, où le genre pose
  ses bâtiments, dont ses **nids de mitrailleuse** ;
- derrière la porterie, un **bastion** de béton et d'obsidienne renforcée en travers de l'allée ;
- à partir de la base, une **seconde enceinte**, un fossé et un rang de barbelés ;
- la **salle forte**, à couches sur ses six faces, dessous compris.

| Genre | Béton | Quartiers | Où sont les coffres |
|-------|-------|-----------|---------------------|
| **Silo de missiles** | blanc, bandes rayées jaune et noir | **puits de lancement** creusés dans le sol (un missile en blocs au fond, la **trappe de silo** de NTM au ras du sol), **dépôt de carburant** (cuves, échelles, canalisations, fûts, grillage), poste de tir (écrans radar), nid de mitrailleuse, caserne, armurerie, groupe électrogène, dépôt de munitions | poste de tir **enterré** sous un tablier de béton rayé et la **grande trappe** du silo : il faut creuser |
| **Bunker** | gris, bandes vert armée | **casemates** (blockhaus à meurtrières dans un talus, tourelle sur le toit), **tranchées** (parois et caillebotis de sapin, sacs de sable, sape couverte, barbelé devant), caserne, armurerie, groupe électrogène, entrepôt, dépôt de munitions ; sol de boue et de gravier | poste de commandement **enterré** sous le **blockhaus** (talus de terre, porte de bunker, écrans radar, table des cartes, classeurs) : il faut creuser |
| **Forteresse soviétique** | gris clair, bandes rouges | **miradors** de bois à projecteur, **garage de blindés** (un char en blocs, palaforge, établi), **place d'armes** à la grande étoile rouge, mâts à drapeau rouge, tribune sur les grands terrains, caserne, nid, armurerie, entrepôt, dépôt de munitions ; dalles de béton | **donjon de béton** au centre, au niveau du sol : fausses fenêtres, bandes rouges, **l'étoile rouge** au-dessus de la porte blindée, drapeau rouge sur le toit |
| **Base radar** | grès, bandes bleues | **radômes** (coupoles blanches), **mât d'antennes** en treillis à feu d'obstacle, **grand radar** de NTM sur son socle, **héliport** (cercle, H, balises, manche à air), salle de contrôle, nid, caserne, groupe électrogène, entrepôt, dépôt de munitions | **salle des opérations** au niveau du sol, sous le grand radar du toit |

Les **armureries** chargent des **palettes d'armement** (les caisses d'obus et de cartouches de NTM —
jamais de munitions nucléaires — ou des flèches et des fusées sans le mod) ; leurs caisses s'empilent
dès que le chunk tourne.

### Les défenses de NTM NEXT

Avec NTM NEXT installé, la base se défend avec les armes du mod (`NtmDefenses`, sans dépendance de
compilation : tout passe par le registre et la réflexion ; sans le mod, rien de tout cela n'est posé
et la base garde ses gardes, ses spikes et ses lanternes) :

- **Des tourelles armées** : la mitrailleuse « Mister Friendly » (5,56 mm), la mitrailleuse lourde
  « Chekhov's Gun » (.50 BMG), et à la forteresse le MASER « Maxwell » sur la salle forte. Elles ne visent
  **que les joueurs** (ni les gardes, ni les bêtes, ni les withers), en survie ou en aventure : jamais en
  créatif ni en spectateur. Les mitrailleuses portent à **32 blocs**, le Maxwell à **64** — moins que le
  glacis : on peut bâtir son canon à TNT hors de leur portée.
- **Basses, pour voir le sol** : une tourelle de NTM ne vise qu'à 30° sous l'horizon (35° pour le
  Maxwell). Posée en haut d'un mirador, elle ne verrait personne au sol. Les tourelles sont donc sur
  des **balcons de tir** à trois blocs du sol, au pied des miradors d'angle et de la porterie (et du
  mirador sud de la forteresse), dans les **nids de mitrailleuse** et sur les **casemates** des
  quartiers, et sur la salle forte : au ras du tablier du silo, aux coins du toit du blockhaus, sur
  deux balcons du donjon ou de la station radar — sauf les Maxwell de la forteresse, en surplomb aux
  coins du toit, assez hauts et assez longs pour tenir les quartiers.
- **Chargées et entretenues** : batterie pleine et deux piles de munitions à la pose. Leur batterie se
  vide en cinq secondes ; tant que la base est ta cible et que ses chunks sont chargés, elles sont
  **rechargées toutes les deux secondes** (batterie et munitions) : il faut les **détruire** — à la
  dynamite, au canon, au wither — ou les **éviter**. Le Maxwell ne tire que sur sa batterie.
- **Éteintes à la fin** : la base pillée (ou abandonnée, si ses chunks sont chargés), ses tourelles
  s'éteignent ; comme tout le reste, elles se démontent, leurs munitions comprises.

| Taille | Tourelles |
|--------|-----------|
| Avant-poste | 2 balcons d'angle (Friendly), 1 nid (ou 2 casemates au bunker), 2 sur la salle forte (Friendly) |
| Base | 4 balcons d'angle (Chekhov), 2 balcons de porterie (Friendly), 1 ou 2 nids (3 casemates au bunker), 2 sur la salle forte (Chekhov) |
| Forteresse | 4 balcons d'angle et 2 de porterie (Chekhov), 1 balcon au mirador sud (Friendly), 2 nids (4 casemates au bunker), 4 Maxwell sur la salle forte |

- **Un champ de mines** dans le glacis, sur le terrain naturel, en couronne de 3 blocs au-delà de
  l'emprise jusqu'à 17 (avant-poste), 21 (base) ou 25 blocs (forteresse), armé d'avance : mines antipersonnel (40 à l'avant-poste), plus des mines à éclats à
  partir de la base (90), plus des mines antichar à la forteresse (160). La **route de la porterie**
  reste libre. Elles sautent au passage d'un joueur… ou d'une bête du glacis. Jamais de mine nucléaire.
- **Du barbelé** sur tous les parapets, devant les tranchées, devant le bastion et en anneau autour de la
  salle forte (empoisonné à la forteresse).
- **Le décor du mod** : projecteurs halogènes, porte blindée coulissante, portes de bunker, trappes de
  silo, grand radar, écrans radar, classeurs, caisses d'acier, fûts, asphalte, béton rayé, sacs de
  sable, grillage.

Les gardes sont surtout des illageois (pillards, vindicateurs, sorcières, ravageurs, évocateur) : ils ne
brûlent pas au soleil. Ils sont persistants. Les coffres tirent leur butin à l'ouverture ou à
l'explosion : les tables de la salle forte (`palamod:chests/enemy_*` par taille), et celles des quartiers
(`enemy_vrac`, `enemy_entrepot`, `enemy_atelier`, `enemy_arsenal`).

### Le terrain reprend ses droits

Une base pillée laissait une ruine de 129 blocs de côté et des cratères autour. Désormais :

- **Avant le premier bloc**, le chantier relève tout le carré de claims (l'emprise et son glacis), de
  48 blocs sous le sol à 24 blocs au-dessus du plus haut relief. Le relevé est écrit, compressé, dans
  `<monde>/dohy/bases/<x>_<z>.terrain` : il survit aux redémarrages, la base peut attendre des jours.
- **À la fin du raid** — tu entres dans la salle forte, ou tu abandonnes — un compte à rebours de
  **5 minutes de jeu** part (`EnemyBaseRegen.DELAY_TICKS`). Le chat le dit, puis prévient à 1 minute et
  à 10 secondes. C'est le temps qu'il reste pour vider les coffres et démonter les machines.
- **À l'échéance**, les chunks sont chargés en tâche de fond et relus tranche par tranche, 15 ms par
  tick : seuls les blocs qui ont changé sont reposés, de bas en haut, sans mise à jour de voisinage
  (rien ne tombe, rien ne coule) et sans vider les coffres par terre. Une seconde passe rattrape ce qui a
  bougé pendant la première. Les murs, les machines et les
  cratères disparaissent ; les collines, les arbres et les rivières reviennent. Les gardes et les bêtes
  de la base disparaissent, ses derniers claims tombent.
- Un joueur resté sur place reçoit la **chute lente** (45 s) et, s'il se retrouve dans la roche, est
  remonté à la surface. Un chunk que tu as **revendiqué entre-temps** n'est pas touché.
- L'échéance est gardée dans `<x>_<z>.due` : quitter le monde la suspend, le rouvrir la reprend.

**Limites** : ce que tu as posé toi-même dans l'emprise pendant ces cinq minutes part avec la base ; le
contenu d'un coffre naturel (village, mine) rasé par la base ne revient pas, seulement le bloc ; une
base bâtie avant ce système n'a pas de relevé et reste en place.

## Réserve d'or (22e)

D'après le wiki (« Investissement Réserve d'or ») : la banque de faction place son argent dans une
réserve bâtie dans tes claims, et récolte des intérêts.

- **Trois blocs** :
  - **coffre-fort de faction** : 5 titanes compressés et 4 obsidiennes renforcées. L'obsidienne
    infusée (diamant) du wiki n'existe pas ici ;
  - **vitre de coffre-fort** : 8 vitres et 1 findium ;
  - **or liquide** : 8 quartz électrifiés et 1 pépite d'endium.

  Les trois résistent à toutes les explosions : seul un wither en vient à bout.
- **Le cube** :
  - 5, 6 ou 7 de côté (paliers 1, 2 et 3, débloqués par la branche Économie de l'arbre) ;
  - arêtes, dessus et dessous en coffres-forts, centre des quatre flancs en vitres, cœur en or
    liquide (ou vide) ;
  - le tout dans tes claims.

  | Palier | Cube | Coffres-forts | Vitres | Or liquide au plus |
  |--------|------|---------------|--------|--------------------|
  | 1 | 5 × 5 × 5 | 62 | 36 | 27 |
  | 2 | 6 × 6 × 6 | 88 | 64 | 64 |
  | 3 | 7 × 7 × 7 | 118 | 100 | 125 |

  Le palier 2 suit la même règle que les deux autres. Les chiffres du wiki pour lui (66, 48, 36) ne
  tombent pas juste sur un cube de 6.
- **Ouvrir** : clic droit mains nues sur un coffre-fort reconnaît le cube et ouvre l'onglet **Réserve
  d'or** (aussi sur la page de faction et par `/f reserve`). Un bloc en main, le coffre-fort se laisse
  bâtir contre. Le palier construit alimente le compteur `reserve:tier` de l'arbre.
- **Placer** :
  - chaque or liquide du cœur permet de placer 400 000 $, et jusqu'à 800 000 $ avec l'arbre ;
  - on choisit un montant (100 000, 1 M, 10 M ou le plus possible), puis une durée réelle :
    1 jour à +1 %, 3 jours à +4 %, 7 jours à +10 %, 14 jours à +25 % ;
  - le multiplicateur d'intérêts de l'arbre s'applique (×2, ×3) ;
  - l'argent sort de la banque de faction. Six placements au plus, 10 000 $ au moins ;
  - chaque placement alimente `reserve:invests`.
- **Récolter** : à l'échéance, la mise et les intérêts reviennent à la banque. Avant l'échéance, on
  peut retirer sa mise, moins 5 %, en deux clics.
- **Le risque** :
  - tant que de l'argent est placé, la réserve ne se démonte pas à la main ;
  - si un wither en casse un bloc, tout l'argent placé est perdu ;
  - un relevé par minute repère aussi une réserve abîmée.

## Events de faction (22f)

Le calendrier du wiki (« Java | Event faction », « Java | Boss », « Java | Totem »), **à l'heure du
serveur** : annonce à 10, 5 et 1 minute, départ à la minute pile. Un event ne part que si un joueur
en faction est connecté, et un seul à la fois.

| Event | Quand | ELO | État |
|---|---|---|---|
| Boss | tous les jours, 1 h et 18 h | 700 | fait |
| Egg Hunt | mardi et dimanche, 19 h (60 min) | 3 000 | fait |
| Totem | mercredi, 21 h | 10 000 | fait |
| KOTH à points | samedi, 21 h 30 (30 min) | 10 000 | fait |

- **Warzone** : `palamod:warzone`, une dimension vide au biome `the_void` (aucun monstre n'y
  apparaît). Chaque event a son arène, bâtie par le code la première fois. On y entre par l'écran des
  events ou `/f event rejoindre`, on en sort par `/f event quitter`, à la fin de l'event (30 s après)
  ou en mourant. **On y garde son inventaire** : en solo, l'arène n'est ouverte que le temps de
  l'event.
- **Factions rivales** : trois factions fictives par event (noms et couleurs tirés au sort) envoient
  deux combattants chacune, renouvelés toutes les 45 s — des vindicateurs à leur bannière. Au Boss,
  ils visent le boss et ses sbires et ne t'attaquent que si tu les frappes (hache de fer). Au KOTH et
  au Totem, c'est du PvP (épée de pierre) : ils se battent entre eux et contre toi, et filent vers
  la zone ou le totem quand personne n'est à portée. Elles prennent leur part du classement.
- **Blocs** : dans la Warzone, on ne casse que ce qu'on a posé pendant l'event (et les toiles
  d'Arachna) ; tout ce qui a été posé disparaît à la fin. Les explosions n'abîment pas les arènes.
- **ELO par rang** : comme le PalaMod (`ExponentialPool.distribute(n, 1.2, elo)`), chaque rang
  touche 1,2 fois la part du suivant. Seul, tu touches tout le pot ; à quatre factions, le premier
  prend 32 %, le dernier 19 %.
- **Écran** : l'event en cours (vie du boss, ton rang, rejoindre ou quitter, les rivaux), le
  calendrier avec le temps restant, les cinq boss et leurs attaques. Carte sur la vue de faction,
  `/f event`. Opérateurs : `/f event lancer <event> [boss]`, `/f event arreter`.

### Le Boss

Un des cinq boss du PalaMod, à tour de rôle (deux par jour). Modèles d'origine traduits du bytecode
(`tools/modelbase2java.py`, exports Blockbench 1.7.10 sans retouche), textures du jar, taille
`sizeMultiplier` : Beep Boop fait 6 × 11 blocs, Azhur 4 × 10, Tobalt 5,5 × 4,2.

| Boss | Attaques (wiki) |
|---|---|
| Beep Boop | Mécabras (saisit à 5 blocs, 3 s, 20/s, puis projette) ; Séisme (à chaque 15 % de vie perdue, 50 blocs, faiblesse et cécité) ; Tic-Tac (engrenage à travers l'armure, 5 à 25 blocs) |
| Tobalt | Koudboul (qui frappe au contact vole à 15 blocs) ; Éruption (25 blocs, immobilise 5 s) ; Bélier (charge ; un bloc posé sur sa route l'étourdit selon sa résistance) ; Épines IV tant qu'il n'est pas étourdi |
| Azhur | Jetsable (tornade, 7 blocs) ; Sunami (vague, 25 blocs) ; Déchaînement (à chaque 20 % : repousse à 15 blocs, cécité, zombies du désert) |
| Morsula | Stupéfreeze (gel, 15 blocs) ; Charge (projette à 7 blocs de haut) ; Picaglagla (pics de glace, dès deux cibles) |
| Arachna | Exploweb (toiles, Wither I, lenteur, cécité, 25 blocs) ; Morsure (poison II 10 s) ; Captoile (cage de toiles et araignées, 7 à 25 blocs) |

Épines I puis II sous 50 % et 25 % de vie pour tous sauf Tobalt, comme le wiki. **Solo** : 1 000
points de vie (6 500 sur Paladium, partagés ; l'attribut plafonne à 1 024 en 26.2), dégâts du wiki divisés par quatre, coups des rivaux
comptés à 30 % ; Déchaînement fait venir 8 zombies (25), Captoile 4 araignées (7), Picaglagla
compte les rivaux comme cibles. Le boss repart s'il n'a pas été frappé depuis 5 min, ou après 30 min.
À sa mort : 700 ELO par rang aux dégâts, bilan dans le chat (« Ta faction » 2e place »), compteur
`event:boss`.

### Le KOTH à points

Samedi, 21 h 30, trente minutes. Une arène ronde de 26 blocs de rayon, avec au centre une colline à
trois gradins : la **zone de capture** est son sommet (3,5 blocs de rayon, un phare au milieu).

- **Chaque seconde, un occupant de la zone est tiré au sort** : sa faction marque un point (wiki).
  Plus une faction a de monde dans la zone, plus elle a de chances ; en solo, il faut vider la zone
  des combattants rivaux pour marquer à coup sûr.
- **Après une mort, la zone ne te compte plus pendant 10 min** (wiki).
- Barre en haut de l'écran : temps restant, qui tient la zone, qui mène. 10 000 ELO par rang aux
  points ; titre du vainqueur et ta place pour chacun.

### Le Totem

Mercredi, 21 h. On arrive en surface et on **descend un long escalier jusqu'à une grotte** (wiki) :
un dôme de 24 blocs de rayon, le totem au centre, la zone de dépôt au nord.

- Le totem se casse **bloc par bloc, du sommet à la base** ; il fait douze blocs (or et pierre noire
  sculptée), et redevient de la bedrock hors event, comme sur Paladium.
- Le bloc cassé part avec toi (tu brilles) : il faut **rester 20 s d'affilée dans la zone de dépôt**
  (en sortir remet le compte à zéro). **1 point par seconde** dans la zone avec un bloc, **500 points**
  au dépôt (wiki).
- **Mort ou temps écoulé : le bloc retourne sur le totem.** Le wiki ne donne pas le temps de port :
  90 s.
- Les combattants rivaux cassent le totem eux aussi (3 s au pied) ; leur porteur brille, ne se bat
  plus et file à la zone : le tuer renvoie son bloc sur le totem. Une faction rivale ne porte qu'un
  bloc à la fois.
- Trente minutes au plus (le wiki ne dit rien), moins si les douze blocs sont déposés. 10 000 ELO par
  rang aux points.

### L'Egg Hunt

Mardi et dimanche, 19 h. L'île volcanique de **Volcania** : 40 blocs de rayon, bordée d'un muret,
un autel d'obsidienne au centre, quatre bassins de lave autour.

- **Abattre Volcania** : le dragon de l'Egg Hunt (`EntityCustomDragon` du PalaMod, copie du dragon
  de l'Ender). Ici c'est le dragon de l'Ender de 26.2 avec la texture `volcania.png` du PalaMod. Il
  tourne autour de l'île et pique toutes les 8 à 14 s sur un joueur ou un rival ; à 75, 50 et 25 %
  de vie, il rugit et plane 6 s juste au-dessus de l'autel, à portée d'épée (le dragon vanilla se
  pose sur son podium, Volcania n'en a pas). Il ne casse rien
  (aucun monstre ne casse de bloc dans la Warzone). Pas abattu en 20 min : il repart, sans œuf.
- **L'œuf** apparaît sur l'autel. On le prend d'un clic ; **le porteur fait gagner une seconde par
  seconde** à sa faction (classement au temps de port, wiki).
- **Tuer le porteur vole l'œuf** ; mort autrement, ou lâché (`/f event lacher`), il retombe là où il
  était. Qui l'a perdu ne peut le reprendre qu'après **10 min** (`PICKUP_LOCK_MILLIS` du PalaMod).
  Tomber dans le vide ou quitter l'arène le renvoie sur l'autel.
- **60 min de chasse** après la mort du dragon ; 3 000 ELO par rang au temps de port (facteur 1,2,
  comme `EggHuntManager`).
- **Rivaux** : pendant le combat, ils patrouillent loin de l'autel ; ils ne touchent pas l'œuf
  dans les 10 s où il apparaît (celui qui a abattu Volcania a sa chance), puis le ramassent,
  s'enfuient avec, et pourchassent quiconque le porte.

### Réduction des dégâts en event

La récompense « dégâts de raid » de la branche Défense (-5, -10, -15, -20 %) protège ici des boss,
de Volcania, de leurs sbires et des combattants rivaux, dans la Warzone : il n'y a pas de raids en
solo.

## Notation de la semaine (22h, refondue en 22j)

Reprise des **notations de pays de NationsGlory**, où chaque pays est noté toutes les semaines et
payé le lundi selon son rang. La note tombe le lundi à minuit, avec la ligue.

**Cinq critères**, 100 points :

| Critère | Plafond | Ce qui est mesuré | Sorte |
|---|---|---|---|
| Activité | 20 | 10 pour les jours joués (5 jours donnent le plafond), 10 pour les contrats (0,4 par contrat du jour ou commande de trixium, 3 pour celui de la semaine) | semaine |
| Économie | 20 | Argent gagné dans la semaine, retraits de la banque de faction déduits ; logarithmique de 10 k à 10 M | semaine |
| Commerce | 15 | Volume vendu et acheté au marché international ; logarithmique de 5 k à 2 M | semaine |
| Militaire | 20 | 4 points par victoire ELO de la semaine | semaine |
| Territoire | 25 | Capitale posée 3 ; pays tenus 12 (racine du nombre sur 12 : 1 pays 3,5, 3 pays 6) ; claims 5 (16 donnent le plafond) ; musée 5 | état |

Les métiers et le trésor ne comptent plus : ils ne faisaient que monter. Sans carte de la Terre,
les 15 points de la capitale et des pays vont pour moitié aux claims, pour moitié au musée.

**Le classement.** Chaque semaine, les douze nations rivales ont leur note, tirée de la graine du
monde et de la semaine : 38 à 72 au hasard, plus 8 points selon la part de leurs pays d'origine
qu'elles tiennent encore. Leur prendre des pays les fait reculer. La faction se classe parmi
elles, de 1re à 13e ; à note égale elle passe devant.

**Les récompenses** :
- **Bourse** : 2 500 $ le point, à la banque de faction (inchangée).
- **Conquête** : 5 points de conquête par point de note sur le pays visé ; × 2 pour la 1re place,
  × 1,5 pour la 2e, × 1,25 pour la 3e. Le pays tombe s'ils suffisent.
- **Podium** : trois, deux ou un lot de ressources du marché international, 10 000 $ pièce au
  prix de base.
- **Première place** : un claim de plus, qui reste (au plus 8).

- **Rang de note** : S dès 85, A dès 70, B dès 50, C dès 30, D en dessous.
- **Absence** : seule la semaine qui vient de finir est notée.
- **Écran** : carte « Notation » sur la page de faction (note et rang), puis la semaine critère
  par critère, les récompenses à ce rang, la dernière semaine, et le classement des treize.

Compteurs dans les stats de la faction, préfixe `note:` ; claims gagnés : `prestige:claims`.
Réglages : `data/notations.json`. Code : `FactionNotation`, `NotationConfig`, `FactionWeekly`.

## La faction est une nation (22j)

Sur une carte de la Terre, la page de faction devient celle d'une nation : une section
« Territoire » y montre la capitale, les pays tenus et la cible (clic : poser la capitale,
renoncer à la cible), avec un accès aux voisins et aux nations rivales. La faction a un **rang**,
selon les pays qu'elle tient :

| Rang | Condition | Taxe du marché international |
|---|---|---|
| Faction | pas de capitale | 15 % |
| Cité | une capitale, aucun pays | 14 % |
| État | 1 pays | 12 % |
| Royaume | 5 pays | 10 % |
| Empire | 15 pays | 8 % |
| Superpuissance | 40 pays | 6 % |

Code : `NationRank`.

## Marché international (22j)

Un stock mondial par ressource, et un cours qui le suit. Ouvert à toute faction : carte « Marché
international » de la page de faction, ou `/f marche`.

- **25 ressources cotées** : fer, or, cuivre, charbon, diamant, émeraude, redstone, lapis,
  quartz, obsidienne, chêne, blé, pomme de terre, carotte, canne à sucre, cuir, os, ficelle,
  poudre, slime, perle de l'Ender, bâton de blaze, améthyste, titane, paladium.
- **Le cours** vaut (stock de référence / stock)^0,6 : 1 à la référence (quatre fois la
  demi-saturation de la ressource : 8 000 pour le fer, 256 pour le diamant), jusqu'à 2 en pénurie,
  0,4 quand le marché est saturé.
- **Vendre** (clic : tout ce que tu portes) paie le prix de base × le cours, moins la taxe du
  rang. La vente gonfle le stock : le cours baisse à mesure, et l'échange est payé à l'intégrale
  — mille d'un coup ou en dix fois, même somme. **À 0,4 le marché est saturé : il n'achète plus.**
- **Acheter** (clic droit : une pile) coûte 2,8 fois le cours, jamais moins de 2,6 fois le prix
  de base. C'est moins cher que la boutique (4 fois) tant que le stock est là ; en pénurie, le
  marché n'a plus rien.
- **Les gros lots des nations.** Toutes les dix minutes de jeu, une nation rivale achète ou vend
  15 à 40 % du stock de référence d'une ressource : le cours saute, et la dépêche passe dans le
  chat (« Nordheim vend 2 400 × Lingot de fer : le cours tombe à -14 % »). Une nation vend plutôt
  ce qui est cher et achète plutôt ce qui est bradé.
- **Le retour.** Entre deux lots, chaque stock revient vers sa référence : 10 % de l'écart par
  vingt minutes de jeu, le rythme d'oubli des ventes à la boutique.
- **Notation** : le volume échangé fait le critère Commerce.

Chiffrage et garde-fous : `docs/BALANCE.md` § 6 vicies. Code : `faction/market/WorldMarket`,
`WorldMarketMenu` ; l'état est sur le monde principal (`world_market`).

## Pays à prendre (22i)

Sur une carte de la Terre seulement (le datapack de la carte). La faction pose une **capitale**
dans un de ses claims, puis prend des pays entiers aux douze **nations rivales** — les factions
fictives des events, qui se partagent le monde par régions. La nation qui tient le pays visé est
mise parmi les trois rivaux du prochain event ; finir devant elle fait compter l'ELO gagné en
points de conquête, et le pays tombe quand ses points sont réunis. Chaque pays tenu ajoute un
claim, et compte pour quatre claims dans la notation. Pas de guerre entre joueurs : un pays tenu
par un joueur ne se vise pas.

Description complète : `docs/CONTENU_DOHY.md`, section « Les pays à prendre » ; chiffrage :
`docs/BALANCE.md` § 6 novodecies. Code : `earth/EarthNations`, `NationMenu`, `NationCommand` ;
`/f pays`.
