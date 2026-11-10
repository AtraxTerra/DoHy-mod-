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
| 22b | Arbre de technologie : Global, Pillage, Défense, Économie | fait |
| 22c | Objets de défense : obsidiennes à effet, spikes obsi, Fake Water, éponges, brouilleur, enclumes, amulettes… | fait |
| 22d | Objets de pillage : dynamites, TNT spéciales, Unclaim Finder, explorateur de coffre, arc en paladium, flèche de Switch… | fait |
| 22d-bis | Withers du pillage : réceptacle, gemmes de wither, améliorations | fait |
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
objet pas encore porté affiche son lot (22e) et ne progresse pas.

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
  Chasseur). Le coffre en fer d'Iron Chests (obsidienne à wither) devient un coffre ; discrétion et
  combat, désactivées sur Paladium, et le home remover reçoivent une recette.
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

| Clé de l'arbre | Ce qu'elle débloque |
|----------------|---------------------|
| `craft:receptacle` | poser le réceptacle |
| `use:baby_wither`, `use:wither`, `use:passive_wither`, `use:funny_wither` | invoquer ces withers |
| `use:thirsty_wither`, `use:predator_wither`, `use:supreme_wither` | invoquer les withers majeurs |
| `craft:wither_resistance_upgrade`, `craft:wither_slime_upgrade`, `craft:stealth_upgrade` | poser l'amélioration |

Compteurs branchés : `spawn:<wither>`, `regen:wither`, `wither:obsidian` (obsidiennes cassées ou
soufflées par un wither que tu as invoqué), `kill:baby_wither`.
