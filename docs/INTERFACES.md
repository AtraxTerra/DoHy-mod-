# DoHy UI — le kit d'interface (chapitre 21)

Les écrans de DoHy quittent les coffres pour le style des interfaces de Paladium (Paladium-UI,
Zephyr-UI, PalaJobs) : fond anthracite, cadre gris à coins crantés, titres rouges, cases claires.
Les maquettes de référence sont les captures fournies par le joueur (métiers, Admin Shop,
autocrafter, crusher, cobblebreaker, grinder).

## Lots

| Lot | Écrans | État |
|-----|--------|------|
| 1 | Kit, métiers et fiche d'un métier, shop | fait |
| 2 | Économie : menu principal, banque, contrats, boutique de boosters, collections, musée, améliorations | fait |
| 3 | Palamod : donjons, profil RPG, monture, golem, dimension du Mineur, enchantement mystique | fait |
| 4 | Conteneurs : machines, coffres en paladium, coffre de vente, guichet du musée, sac de la monture, coffres du golem | fait |
| 5 | Inventaire du joueur, avec l'accès aux menus | fait |
| 6 | Ouverture des caisses : la roulette | fait |
| 7 | Encyclopédie : mise en page `TEXT`, menu du plan de construction, bouton « ? » de l'inventaire | fait |
| 8 | Pass du mois (tuiles des 35 récompenses, quêtes), marché noir ; le bouton du Lucky Pass ouvre le pass | fait |
| 9 | Modes des donjons (cartes de mode au-dessus des paliers), autel des offrandes des boss de métier | fait |
| 10 | Lucky blocks et Lucky Pass | fait |
| 11 | Le commerce : un écran à quatre onglets (acheter, vendre, boutique, hôtel des ventes) | fait |
| 12 | Les grades : la page des grades, la section de la boutique, la liste des joueurs, l'Ender Chest agrandi | fait |

Chaque lot est validé sur des captures prises en jeu avant son jar.

## Architecture

Le serveur reste maître de toutes les règles ; le client ne fait que montrer et demander.

- **Vue** (`fr.atraxterra.dohy.ui.view`, commun) : un `record` qui implémente `UiView`, avec son
  `StreamCodec`. Elle contient tout ce que l'écran affiche, déjà calculé : prix remisés, niveaux,
  progression. Exemples : `ShopView`, `JobsView`, `JobDetailView`.
- **Transport** (`UiNet`) : `Show` envoie une vue au joueur ; `Action` revient avec un verbe, un
  argument texte et un nombre (`buy`, `minecraft:cobblestone`, `16`). Le serveur n'accepte une
  action que pour la vue qu'il a lui-même ouverte à ce joueur. Un `close` en retard n'oublie
  jamais une vue plus récente.
- **Contrôleur** (côté serveur, dans le paquet du domaine) : construit la vue et traite les
  actions, puis renvoie la vue à jour. `ShopController`, `JobsController`.
- **Écran** (`fr.atraxterra.dohy.client.ui.screen`, client) : un `UiScreen` qui implémente
  `ViewScreen`. Une vue du même type, reçue pendant que l'écran est ouvert, le met à jour sans le
  rouvrir : la sélection, la recherche et le défilement restent.

Pour ajouter un écran : la vue et son codec, l'enregistrement dans `UiNet.onRegister` avec le
contrôleur, l'écran, et sa fabrique dans `UiClient`.

## Le kit (`fr.atraxterra.dohy.client.ui`)

- `UiScreen` : une maquette de taille fixe, en unités de maquette, mise à l'échelle de la
  fenêtre. Comme chez Paladium, l'interface occupe la même part de l'écran quelle que soit
  l'échelle d'interface du jeu. L'échelle est arrondie pour qu'une unité tombe sur un nombre
  entier de pixels, donc les filets d'un pixel restent nets. Le monde reste visible derrière,
  assombri, sans flou. La touche d'inventaire ferme l'écran, sauf dans un champ de saisie.
- `UiGraphics` : panneau, cases, boutons, flèche, images, texte en polices du kit à n'importe
  quelle taille, objets à n'importe quelle taille, bulles, calques (listes ouvertes).
- Éléments : `UiButton` (rouge, vert, gris), `CloseButton`, `TextField` (recherche), `Dropdown`,
  `ItemGrid` (grille d'objets qui défile), `RowList` (liste qui défile), `QuantityField`
  (Maj = ±16).
- `Ui` : la palette et les polices. `Ui.Paladium` reprend à la lettre celle de Paladium-UI
  (`ColorConstant` du jar 7.15 : fond `1E1D23`, fond clair `40404D`, rouge `EF3926`, vert
  `25E251`, noir `0F0F12`, gris `323232` et `989898`, désactivé `A7A7A7`) ; le rouge, le vert, le
  cadre et le noir du kit s'y alignent.
- `Anim` : le temps des animations, les courbes de Robert Penner (`Anim.Ease` : linéaire,
  quadratique, cubique, quartique, quintique, sinus, exponentielle, circulaire, recul, élastique,
  rebond, en entrée, en sortie et aux deux bouts, celles du moteur d'animation de Zephyr-UI) et
  `Anim.Tween` (une valeur d'un point à un autre, sur une courbe, après un délai).

### Le moteur Zephyr (2026-11-27)

Les formes du kit (`Paint` : rectangles arrondis, pastilles, anneaux, cadres, dégradés, ombres,
halos, pointillés) sont dessinées au GPU, comme le moteur de Zephyr-UI et ses shaders `rounded`,
`shadow` et `circle`, dans le rendu retenu de la 26.2 :

- deux pipelines d'interface du kit (`Zephyr.ROUNDED`, `Zephyr.SHADOW`), sur le modèle du
  pipeline d'interface du jeu (`GUI_SNIPPET`) avec l'UV dans le format des sommets, et leurs
  shaders (`assets/dohy/shaders/core/zephyr*.vsh|fsh`) ;
- une forme est un élément d'interface comme un autre (`ZephyrShape`, `submitGuiElementRenderState`) :
  elle se range et se coupe aux ciseaux comme le reste ;
- les coins sont des quads dont l'UV porte la distance au coin intérieur, en rayons : le shader en
  tire le bord, lissé sur un pixel physique quelle que soit l'échelle (`fwidth`) ; un anneau porte
  son rayon intérieur dans la partie entière de l'UV ;
- un dégradé est porté par les couleurs des sommets : un dégradé arrondi est une seule forme, là
  où l'ancien chemin posait une bande par ligne de pixels ;
- plus de texture par rayon, d'anneau ou de halo : les formes voisines partagent le même pipeline,
  donc le même appel de dessin.

Zephyr-UI passait le rayon et le rectangle de chaque forme en uniformes, un appel de dessin par
forme ; ce n'est pas possible dans le rendu retenu, d'où l'UV. Si la carte graphique refusait les
shaders (pipeline non valide à la compilation), `Zephyr.active()` le voit et `Paint` reprend ses
textures générées : l'API de `Paint` n'a pas changé, les écrans n'ont rien à faire. La coche et la
flèche restent des textures.

Vérifié en captures (CI, rendu logiciel Mesa) : l'accueil, les métiers, le commerce, la boutique,
les grades et le HUD, moteur actif puis coupé, sont identiques à l'œil ; seuls les pixels de
lissage des bords diffèrent de quelques unités.

### L'épuration des écrans (2026-11-27)

Un tour des écrans en captures (trente écrans, ouverts par le vrai serveur intégré) : les menus
dans la coque sont cohérents ; les écrans qui s'en écartent passent un par un dans le style des
menus, sans rien perdre (mêmes bulles, mêmes clics, mêmes messages).

- **Familier** (`PetScreen`, `/pet`) : il quitte son cadre à filets pour la coque des menus, avec
  une entrée **Familier** dans la barre latérale (groupe Personnage). À gauche, la fiche comme
  celle d'un métier : portrait, nom, pastilles du niveau et de la rareté, barre d'XP, cases
  BONHEUR (avec sa barre) et COMPETENCES (le multiplicateur), le bouton visible ou caché, les
  quêtes du jour. À droite, la section EMPLACEMENTS (le prochain s'ouvre au niveau indiqué),
  la section COMPETENCES : onglets passives et actives dans leur creux, une consigne à côté
  (« Emplacement 3 : clique une compétence pour l'y poser », ou la touche de la roue pour les
  actives), les lignes au liseré de leur couleur (posée en jaune, active en bleu, verrouillée en
  gris avec son niveau et un cadenas), RETIRER au bouton rouge du kit. En en-tête : compétences
  posées et quêtes du jour. Sans familier : la carte qui le dit, les étapes numérotées, les
  vingt et un familiers et la légende des raretés.
- Le **profil RPG**, le **clicker** et le **Lucky Pass** reprennent des écrans de Paladium
  (capture du joueur, PalaClicker, machine à sous de PalaMod) : ils restent tels quels.

### Polices

Définies dans `assets/dohy/font/*.json` (TrueType, taille 8, sur-échantillonnées), avec la
police du jeu en secours :

| Police | Fichier | Usage |
|--------|---------|-------|
| `dohy:title` | Minecraft Dungeons | grands titres rouges, nom de l'objet du shop |
| `dohy:wide` | Minecraft (large) | libellés : MINEUR, VOTRE ARGENT, boutons |
| `dohy:pixel` | Pixel NES | intertitres des machines |
| `dohy:body`, `dohy:bold` | Montserrat SemiBold et Bold | texte courant |

### Ressources

`tools/ui_assets.py <dossier des jars extraits>` écrit `assets/dohy/font` et
`assets/dohy/textures/gui/kit` depuis Paladium-UI 7.0.0 et PalaJobs 8.0.0 : polices, hexagones
des métiers (l'anneau transparent est coloré à la couleur du métier, comme le jeu le peignait),
hexagones de quête, hexagone du bord gauche, billet, loupe, croix. Les petites icônes des machines
(losanges, chaleur), la maison et la flèche de retour sont dessinées pixel par pixel dans le
script.

## Écrans du lot 1

- **Métiers** (`/metiers`, menu principal) : les quatre hexagones avec leur niveau ; la bulle
  donne l'XP. En bas, les contrats du jour en « quêtes quotidiennes », avec le bonus de
  récompense du niveau global et le bonus d'XP de métier des améliorations. Clic sur un métier :
  sa fiche. L'hexagone de gauche ramène au menu principal.
- **Fiche d'un métier** : hexagone, niveau, XP du niveau, argent du prochain niveau, XP totale ;
  quatre onglets (déblocages, primes, récompenses, XP) en listes qui défilent. Vert : acquis ; gris :
  verrouillé ; orange : déclaré mais absent du mod (objet pas encore porté, effet pas appliqué).
  L'onglet **XP** (ajouté le 2026-11-15, `JobXpGuide`) liste ce qui rapporte de l'XP au métier,
  coupé en sections par des lignes titres (état `HEADER`, qui ne compte pas dans le total de
  l'onglet) : gain, unité, niveau où il devient possible ; vert s'il est à portée. La bulle du
  premier titre rappelle que les valeurs sont de base et donne le bonus d'XP du joueur.
- **Shop** (`/shop`, menu principal) : catégorie et recherche (sans accents) à gauche, solde et
  fiche de l'objet à droite. Les règles sont celles de l'ancien shop : remise d'achat plafonnée à
  25 %, prix de vente dynamique, vente verrouillée tant que la collection n'est pas assez
  avancée. Les anciens `ShopMenu` et `JobsMenu` en coffre sont supprimés.

## Les menus génériques (lot 2)

Le menu principal, la banque, les contrats, la boutique, les collections, le musée et les
améliorations n'ont pas de maquette Paladium. Ils partagent une vue, `MenuView`, dessinée par
`MenuScreen` dans le style des autres écrans :

- un titre rouge, qui rétrécit plutôt que d'être coupé ; un sous-titre ;
- un cartouche de chiffres à cadre vert, en haut à droite (liquide, épargne, niveau global…) ;
- des **sections** avec un intertitre à losange, chacune dans une disposition :
  `GRID` (cartes larges, quatre par rangée), `ROWS` (lignes pleine largeur : les contrats),
  `TILES` (petites vignettes d'objet : collections, vitrines du musée), `TEXT` (du texte qui
  se lit : les fiches de l'encyclopédie, voir plus bas) ;
- des **cartes** : objet, titre, deux ou trois lignes, pastille, liseré de couleur, barre
  d'avancement, bulle détaillée, et deux actions (clic gauche, clic droit). Un titre un peu trop
  long rétrécit (de 4,4 à 3,8) avant d'être coupé (depuis le lot 7). Grisée : pas assez
  d'argent, déjà actif ; verte : terminé, acquis, exposé ;
- la zone défile à la molette.

Côté serveur, chaque menu est un `MenuController` (`ui/menu`) : il construit sa vue et traite
les clics sur ses cartes ; après une action, la vue est renvoyée si le joueur est toujours dessus.
Les sous-pages (une catégorie de collections, une aile du musée) sont un état par joueur dans le
contrôleur ; l'hexagone de gauche y revient avant de retourner au menu principal. `Hub.open`
reste le point d'entrée des autres écrans. Les sept anciens menus en coffre sont supprimés ; le
coffre de vente et le guichet du musée, de vrais conteneurs, viendront au lot 4.

## Les écrans Palamod (lot 3)

Les menus en coffre de la partie Palamod passent sur la même `MenuView` :

- **donjons** : le choix du palier (cent vignettes, vert réussi, jaune le suivant, gris
  verrouillé, un crâne aux paliers de boss), l'autel de boost, la marchande et le sac ;
- **profil RPG** : niveau et points libres, une section par statistique (arbre et runes), les
  points réservés puis validés, la remise à zéro en deux clics ;
- **monture** : la fiche de la monture équipée (invoquer, nourrir, XP partagée), l'enclos
  (clic : équiper, clic droit : récupérer, Maj + clic : soigner) et le soin aux bandages ;
- **golem** : l'accueil, l'arbre des compétences par branche (racine, utilitaire, guerre,
  fermier) et les choix des compétences à option ;
- **dimension du Mineur** : l'entrée (règles, session, rejoindre) et le bilan de fin de session ;
- **table de l'alchimiste** : l'objet tenu et les deux enchantements mystiques, avec la raison
  d'un refus dans la bulle.

Maj + clic gauche arrive au serveur comme bouton 2. Le serveur peut fermer l'écran
(`UiNet.close`) avant une téléportation : entrée d'un donjon, de la dimension du Mineur. Les vrais
inventaires (sac de la monture, coffre du golem) restent des conteneurs, pour le lot 4.

Les textes que le serveur ne doit pas traduire lui-même (noms d'objets, d'enchantements, clés de
traduction) partent en composant JSON (`MenuView.text`) : le client les traduit dans sa langue à
l'arrivée de la vue. Un serveur dédié ne connaît que l'anglais ; sans cela, un joueur français
lirait « Diamond Boots ». La fiche d'un métier suit le même chemin.

## Les machines et les coffres (lot 4)

Les écrans de conteneur passent par `UiContainerScreen` : un `AbstractContainerScreen` dessiné
avec le kit, sans mise à l'échelle libre (les cases sont celles du menu, que le jeu place et teste au
pixel d'interface). Le fond passe avant les objets, les bulles après.

- **Machines** (`MachineKitScreen`, 262 x 236) : le titre en haut à gauche, la machine en sections
  (intertitres à losange), l'inventaire en bas. Le cadre de chaque case est dessiné à la position de
  la case du `MachineLayout` : il n'y a plus de texture de fond, donc plus de `gen_gui.py` ni de
  `gui_layout.json` à accorder. `check_assets.py` vérifie toujours la taille des inventaires, les
  bornes de la zone de la machine et les chevauchements. Chaque machine a son dessin
  (`MachineDesigns`) : flèches qui suivent le cycle, jauges du combustible ou du réservoir, et une
  bulle sur chaque case vide qui dit ce qu'on y pose.
- **Crusher** : les quatre recettes et leurs bacs, synchronisés par quatre valeurs libres du menu
  (`DATA_EXTRA`) et la recette mise en avant (`DATA_SELECTED`).
- **Coffres en paladium** : la grille de 12 x 9, les anneaux teintés du métal, le titre au-dessus.
- **Inventaires en coffre** : coffre de vente, guichet du musée, sac de la monture, coffre et
  gardien du golem, coffre de l'End en paladium ont chacun leur type de menu (`KitMenus`) pour ouvrir
  l'écran du kit au lieu du coffre vanilla. Le menu reste un `ChestMenu` ; l'écran affiche la
  consigne de l'usage (« vendu à la fermeture »...).

## L'inventaire du joueur (lot 5)

Demandé le 2026-11-11, sur la maquette de l'inventaire de Paladium : les menus s'ouvrent depuis
l'inventaire au lieu des commandes. `KitInventoryScreen` remplace l'écran du jeu à l'ouverture
(`InventoryClient`, `ScreenEvent.Opening`), en survie et en aventure ; le mode créatif garde son
inventaire.

- **Disposition** : le titre « INVENTAIRE », les intertitres « ARMURE » et « CRAFT » à losange, les
  quatre cases d'armure et le personnage qui suit la souris, la grille 2 x 2 et son résultat, la
  main secondaire, l'inventaire et la barre d'action. Les cases sont celles du menu d'inventaire du
  jeu (`Slot.x` et `Slot.y` sont finals en 26.2) : le panneau se dessine autour, déborde au-dessus et
  à droite, et l'écran est recentré sur lui.
- **Accès aux menus** : le bouton rouge en haut à droite ouvre le menu principal ; les cinq cases
  ouvrent le **shop** (étal), les **contrats** (parchemin), la **banque** (émeraude), la **faction**
  (château) et les **métiers** (objet doré). Sous le craft, là où Paladium mettait le Lucky Pass,
  le bouton rouge PASS ouvre le **pass du mois** (relique du mois, depuis le lot 8 ; il ouvrait le
  concours Trixium, qui reste au menu principal) et, depuis le lot 10, le bouton vert LUCKY à côté
  ouvre le **Lucky Pass**. Une bulle nomme chaque bouton.
- **Côté serveur** : un clic ferme l'inventaire (le serveur rend la grille de craft et l'objet tenu
  à la souris), puis envoie `InventoryNav.Open` ; `HubController.openTarget` n'ouvre que les menus de
  sa liste, par les mêmes cartes que le menu principal.
- **Ce qui part** : le livre de recettes, la liste des effets à droite de l'inventaire (ils restent en
  haut à droite de l'écran de jeu) et le bouton « Profil RPG » au-dessus de l'inventaire ; le profil
  s'ouvre par le menu principal ou `/profil`.
- **Icônes** : les cinq raccourcis et le losange de l'armure sont découpés dans la maquette fournie
  (`inv_*.png`, `section_armor.png`), le fond rendu transparent.

## L'ouverture des caisses (lot 6)

Demandée le 2026-11-11 avec la refonte du butin. Les caisses sont depuis devenues les six boxes de
PalaMod (`docs/PALAMOD.md` § 33) : la roulette est la même, elle porte le nom de la box. Ouvrir
une box (carte de la boutique, clé en main, bouton « Rouvrir », ou `/crate open`) affiche
`CrateScreen` à la place du message dans le chat.

- **Le tirage reste au serveur** : `Crates.open` tire le lot, le donne tout de suite (argent au
  liquide, objets à l'inventaire, le surplus au sol), puis envoie `CrateView` : 46 cases tirées
  dans la table et le lot gagné à la case 39. Fermer l'écran en route ne change rien au lot.
- **La roulette** : la bande file, puis ralentit sur un peu plus de cinq secondes et s'arrête sous
  le repère, au hasard dans la case et pas toujours au milieu. Un tic à chaque case qui passe, plus
  aigu en fin de course. Un clic sur la roulette, ou Espace, saute l'animation.
- **Les classes** : chaque case porte la couleur de sa classe, commun (gris), rare (bleu), épique
  (violet), légendaire (or). À l'arrêt, la case gagnante s'illumine, les autres s'éteignent, et un
  son marque la classe : l'orbe d'XP, la montée de niveau, l'améthyste, la fanfare du défi.
- **Sous la roulette** : la classe, le nom du lot, sa quantité, et pour un lot groupé ses objets en
  ligne (avec leur bulle). En bas, la chance de chaque classe pour cette box.
- **Les boutons** : « Rouvrir » consomme une clé, ou, sans clé et avec assez de liquide, en achète
  une au prix affiché sur le bouton ; « Boutique » revient aux cartes des boxes.
- **Le chat** : le message du gain attend la fin de l'animation (`REVEAL_TICKS`), pour ne pas la
  gâcher. Un lot épique ou légendaire est annoncé aux autres joueurs.

## L'encyclopédie (lot 7)

Demandée le 2026-11-14 (contenu : `docs/CONTENU_DOHY.md`, « L'encyclopédie et les plans de
construction »). Elle passe par `MenuView`, avec une mise en page de plus :

- **`TEXT`** : une carte par bloc de texte, pleine largeur. Le titre de la carte est l'intertitre
  (en gras) ; chaque ligne est un paragraphe, coupé à la largeur (`UiGraphics.wrap`, utilisable
  hors du dessin, comme `measure`). Une ligne qui commence par `- ` est une puce (carré de la
  couleur du liseré), par `/` une commande (en gras, cyan), par `! ` une astuce (« ! » et texte en
  or). L'objet de la carte, s'il y en a un, se met à gauche et décale le texte. La hauteur de la
  carte suit son texte ; une carte de texte sans action n'a pas de bulle.
- **Le menu du plan** (`PlanMenu`) : `GRID` pour les structures, les tailles et les actions,
  `ROWS` pour le matériel (barre d'avancement : les blocs en place).
- **Le bouton « ? »** de l'inventaire, vert, à gauche du bouton du menu principal, ouvre le
  sommaire ; `encyclopedia` rejoint la liste des cibles de `HubController.openTarget`. La
  touche J envoie la même demande (`InventoryNav.Open`).

## Le pass et le marché noir (lot 8)

- **Le pass** (`PassMenu`) : le cartouche (niveau, XP du niveau, fin du mois), la relique du mois
  et « Tout récupérer » en `GRID`, les **35 récompenses en `TILES`** (le numéro du niveau en
  pastille ; vert : récupérée, liseré or et clic : à récupérer, grisée : pas encore atteinte ; la
  bulle dit ce que donne le niveau), puis les quêtes du jour et du mois en `ROWS` (barre
  d'avancement, pastille de l'XP).
- **Le marché noir** (`BlackMarketMenu`) : la marchandise volée (liseré rouge, la nation volée), la
  contrebande (violet) et le lot sans étiquette (or) en `GRID` (le prix, le stock qui reste,
  « VENDU » en pastille quand il n'y en a plus), ce que le receleur rachète en `ROWS` (vert, le
  prix d'une pièce en pastille). Une carte trop chère reste cliquable : le refus dit ce qu'il
  manque.

## Les modes des donjons et l'autel (lot 9)

- **Le choix du palier** (`DungeonMenus.Paliers`) : une section MODE en tête (trois cartes,
  « CHOISI » en pastille sur le mode retenu, un clic change de mode et les vignettes suivent), puis
  « Avant d'entrer », puis les cent paliers du mode. En Difficile, les paliers en 0 montrent une rose
  du wither et « Boss Dark : … » ; le cartouche dit le métier, le mode et le dernier palier réussi
  (le record en Difficile).
- **L'autel des offrandes** (`JobBossMenu`, vue `dohy:autel`) : les quatre gardiens en `GRID`
  (niveau, état — offrande en %, « Arrive dans 42 min », « T'attend dans l'arène » —, pastille PRET
  ou EN ROUTE, barre de l'offrande sur celui qu'on regarde), son offrande en `TILES` (déposé / requis,
  ce qu'il y a dans le sac, vert quand c'est complet), la carte d'action (Offrir à l'autel seulement,
  En route, ou Combattre), puis ce qu'il rapporte en `ROWS`.

## Les lucky blocks et le Lucky Pass (lot 10)

- **L'ouverture d'un lucky block** (`LuckyBlockScreen`, vue `dohy:lucky_block`) : un panneau à la
  couleur du bloc, l'en-tête LUCKY BLOCK et sa sorte, la pastille DECOUVERTS x / 147, puis la
  machine à sous de PalaMod (son avant-plan, ses trois rouleaux d'icônes, leurs temps : 5 s par
  rouleau, départs décalés de 600 ms, arrivée avec un rebond). Sous la machine : l'invite, puis
  OUVERTURE EN COURS..., puis la pastille BONUS ou MALUS (et NOUVEAU), le nom de l'événement et le
  compte à rebours de la fermeture. Un bouton, OUVRIR, PASSER puis FERMER ; Espace et Entrée le
  valent. Un tic à chaque case qui passe, un coup sourd à chaque rouleau qui s'arrête, une enclume
  pour un malus, un carillon pour un bon.
- **Le Lucky Pass** (`LuckyPassScreen`, vue `dohy:lucky_pass`) : le même cadre, trois rouleaux de
  cases (un lucky block, ou la croix de PalaMod pour une case vide), le nombre de lancers, LANCER
  puis le gain (« Gagne : 1 PALADIUM + 1 FINDIUM ») et l'heure du prochain lancer. Un lancer offert
  (Roulette, Boule de Crystal) laisse LANCER actif après celui du jour ; les rouleaux s'arrêtent
  quand même sur les gains du lancer qui vient d'être fait.
- **Les filtres d'écran** des effets (deuxième lot d'événements, `LuckyOverlays`) : sous toute l'interface, le voile
  gris de « Sans lunette », le vert de « Vision nocturne », la vue de la citrouille du bol, et le
  chat dessiné de « I love cats » au milieu de l'écran, large de la moitié de l'écran.
- **« 1 000 000 joueurs »** (troisième lot, `OneMillionScreen`, vue `dohy:one_million`) : la
  bannière criarde d'un faux gagnant, dessinée pour DoHy — cadre bleu glace, fond vert vif,
  confettis qui tombent, deux coupes dorées, « VOUS ETES LE 1 000 000 JOUEUR ! VOUS AVEZ GAGNE
  UN FULL ENDIUM !!!! » et un bouton blanc RECUPERER que montre une main. Le bouton, Espace,
  Entrée ou Échap ferment l'écran ; l'aveu de LuckyMan arrive alors dans le chat.
- **Le menu principal** passe à cinq cartes sur une ligne quand les noms tiennent (la carte
  « Lucky Pass » rejoint la liste des choses à faire).

## Le commerce (lot 11, 2026-11-19)

Demandé par le joueur : des shops, une boutique et un marché « plus simples de compréhension »
(ce qui gênait : on ne savait pas lequel vendait quoi, et les écrans étaient trop chargés), et un
hôtel des ventes qui marche en solo.

- **Un seul écran, quatre onglets** (`fr.atraxterra.dohy.shop.Commerce`) : la barre latérale
  n'a plus qu'une entrée **Commerce** à la place de Shop, Coffre de vente, Boutique et Marché.
  Le titre est COMMERCE partout ; la rangée d'onglets **Acheter · Vendre · Boutique · Hôtel des
  ventes** est celle de la coque (`MenuView.Tab`) ; le sous-titre dit en une phrase ce qu'on
  trouve dans l'onglet ouvert, et où aller pour le reste. Les quatre onglets gardent leur vue et
  leur contrôleur (`dohy:shop`, `dohy:sell`, `dohy:boutique`, `dohy:auction`) : un clic sur un
  onglet passe par `Commerce.openTab`, que `MenuController` et `ShopController` appellent.
  Compteurs : le nombre de sortes d'objets à vendre sur Vendre, « N A RECUPERER » sur l'hôtel
  des ventes ; la pastille de l'entrée Commerce compte les invendus.
- **Acheter** (`ShopScreen`) : seulement ce qui s'achète. Une carte montre l'objet, son nom et un
  seul prix (vert si on peut le payer). La fiche : PRIX A L'UNITE (remise comprise), le prix du
  moment s'il suit le cours de l'heure, la quantité, le total, un seul bouton ACHETER (PAS ASSEZ
  D'ARGENT sinon). **Les courbes** (demandé le 2026-11-19 : « uniquement pour les ressources qui
  changent régulièrement ») : seuls les objets dont le prix change chaque heure — le cours de
  l'heure, les 32 objets de l'agriculture — en ont une, en miniature sur leur carte (l'objet à
  gauche, la courbe et la variation du moment à droite) et en grand sous la fiche (prix des
  24 dernières heures, plus haut, plus bas, l'heure de chaque point sous la souris). Un prix fixe
  n'en a pas : sa courbe n'était qu'une ligne plate. Les catégories vides disparaissent. **La recherche** trouve aussi ce qui s'achète ailleurs — les annonces du jour
  de l'hôtel des ventes, les boosters, clés, coffres à poser, potions et l'assurance de la
  boutique (`ShopView.Elsewhere`) : une carte à bandeau jaune dit où, un clic y mène ; rien
  trouvé du tout, l'écran le dit (« ni au shop, ni à la boutique, ni dans les annonces du
  jour »).
- **Vendre** (`SellController`, vue `dohy:sell`) : VENDRE D'UN COUP (Tout vendre, avec le
  total et une confirmation ; le coffre de vente), PRIX QUI CHANGENT CHAQUE HEURE (les objets du
  cours de l'heure, en cartes avec leur courbe : c'est là que l'heure de vendre compte), CE QUE LE
  SHOP RACHETE en `ROWS` (quantité,
  prix pièce, total, pastille PRIX BAS quand la pression de vente l'a fait baisser, barre de
  collection grisée quand la vente n'est pas encore ouverte ; un clic vend toute la ligne), A
  L'HOTEL DES VENTES (les raretés du sac, un clic ouvre leur mise en vente). Sac vide de tout
  ça : trois lignes disent où se vend quoi. `/sell` ouvre cet onglet, `/sell coffre` le coffre.
- **Boutique** (`BoutiqueController`) : un seul chiffre (TON ARGENT, et le booster s'il tourne),
  des lignes qui disent ce que fait l'achat (« Ventes x2 pendant 20 min », « 2 clés sur toi :
  clic droit pour ouvrir »), les coffres à poser en dernier. Les cartes Banque et Améliorations
  (de simples liens) sont retirées.
- **Hôtel des ventes** (`AuctionController`) : TES VENTES (annonces en cours avec leur prix et
  le temps qu'il faut en général, VENDU, A RECUPERER, et la carte Mettre en vente), ANNONCES DU
  JOUR en une seule section (le vendeur passe dans la bulle), PLUS D'ANNONCES. La mise en vente
  se fait en deux pages : QUEL OBJET ? (les raretés du sac, prix conseillé), puis A QUEL PRIX ?
  (l'objet en tête, Vente rapide, Prix conseillé, Prix fort, chacun avec ce qu'il rapporte et
  le temps qu'il faut). Une carte Retour, ou le fil d'Ariane, ramène à la page principale.
- **Ailleurs** : le raccourci de l'inventaire devient « Commerce — Acheter, vendre, hotel des
  ventes » ; la carte Marché du menu principal devient Hôtel des ventes (ce qui est en vente,
  les invendus) ; les bornes du spawn ouvrent le bon onglet. `/hdv` et `/ah` ouvrent l'hôtel des
  ventes, `/commerce` l'onglet Acheter.

## Les grades (lot 12, 2026-11-20)

- **La page des grades** (`GradesController`, vue `dohy:grades`, entrée **Grade** du groupe
  Personnage) : en tête TON GRADE (dans sa couleur), PREMIUM (les jours qui restent) et TON
  ARGENT. Puis quatre sections : LES GRADES (Héros, Légende, Divinité et Premium ; la ligne de prix
  dit « Passer : 90 M » quand on monte, « Ton grade » ou « Contenu dans Légende » quand c'est
  acquis ; la bulle liste tout ce que donne le grade ; l'achat demande confirmation), TES COMMANDES
  (une carte par commande avec son icône et ce qu'elle fait ; débloquée, un clic la lance et la
  seconde ligne dit son état — vol, vision nocturne, demandes de téléportation ; verrouillée, elle
  est grisée et dit le grade qu'il faut), CE QUE DONNE CHAQUE GRADE (`ROWS` : un avantage par ligne,
  les valeurs Joueur · Héros · Légende · Divinité, celle de Premium, et la sienne en pastille
  « TOI : 10 »), et POUR TOUS (`TEXT` : les commandes de tous, avec ce qui reste au joueur).
- **La Boutique** : la section GRADES : DU CONFORT, POUR TOUJOURS ouvre l'onglet, avec les quatre
  mêmes cartes ; clic droit, la page des grades.
- **La recherche de l'onglet Acheter** trouve « Grade Héros »... et « Premium 30 jours », avec le
  bandeau jaune qui mène à la Boutique.
- **La liste des joueurs** (touche Tab) : le pseudo prend la couleur du grade, suivi d'une
  pastille « HEROS », « LEGENDE+ » (avec Premium) ou « PREMIUM », avant celle de la faction.
- **L'Ender Chest agrandi** : l'écran de coffre du kit (`KitChestScreen`), jusqu'à sept rangées,
  avec l'icône de l'Ender Chest et la consigne « Agrandi par ton grade » (types de menu
  `dohy:grade_ender_4` à `_7`).

## La renaissance des métiers (lot 15, 2026-11-20)

- **La page Métiers** : une section RENAISSANCE en tête de la fiche. Avant le niveau 100, la carte
  « Renaissance n » est grisée avec sa barre vers le 100 (« Encore 12 niveaux ») ; au 100, elle
  s'allume (« Retour au niveau 0 », « Bonus total : +20 % ») et demande confirmation. Après une
  première renaissance, une carte dorée dit le bonus acquis (pastille R2). La fiche du métier dit
  « NIVEAU 12 · R2 » et, dans TES BONUS, la ligne de la renaissance ; les onglets « R2 NIV. 12 » ;
  les déblocages restent verts, les primes et les récompenses du tour repartent de zéro.
- **Partout ailleurs** : le rang à côté du métier dans la barre d'XP (« Fermier R2 12 »), les
  notifications d'XP et de niveau, les cartes de l'accueil (« R2 · NIV. 12 ») et `/metiers`.

## Les bots de farming (lot 13, 2026-11-20)

- **Le menu du bot** (`FarmBotMenu`, vue `dohy:farm_bot`, clic droit sur son bot la main vide ;
  il ne répond plus à plus de 12 blocs) : en tête ÉTAT (dans sa couleur : Récolte, Range sa
  récolte, Attend que ça pousse, Rejoint sa zone, Coffres pleins, Sac plein sans coffre, Pas de
  zone, En pause), RÉCOLTES et SAC (en rouge quand il est plein). Puis SA ZONE (sa taille et ses
  coins, clic droit pour l'effacer ; les cultures mûres qu'il voit ; la télécommande : la relier à
  ce bot, ou en acheter une), SES COFFRES (« n / 6 » : une carte par coffre lié, avec sa position
  et sa distance, la pastille PLEIN s'il l'était au dernier passage, clic droit pour le délier ;
  puis la consigne pour en lier un), SON SAC (`TILES` : une vignette par objet, le total en
  pastille) et ACTIONS (pause ou reprise, vider maintenant, prendre son sac, ranger le bot, avec
  confirmation).
- **Le sous-titre** dit ce qui manque : la zone, puis un coffre ; ensuite, ce que fait le bot.
- **La Boutique** : la section BOTS DE FARMING, après les potions : le bot et la télécommande.
- **Les objets** : leur icône et leur bulle ; un bot rangé dit aussi sa zone et ses coffres.
- **Dans le monde** : le bot, un petit robot de ferme sur chenilles, chapeau sur la tête et houe
  en main (il la lève à chaque récolte). Télécommande en main : le contour de la zone en
  étincelles, les coffres liés en points verts, un premier coin en attente en lueur blanche.

## Les mines de la Terre (lot 14, 2026-11-20)

- **L'onglet Mines** de la faction (`MineMenu`, vue `dohy:faction_mines`, sur une carte de la
  Terre ; la pastille de l'onglet compte les mines) : en tête MINES, PAR HEURE et COFFRE. Puis
  CAPTURE EN COURS (la balise, le temps qui reste, une barre de progression ; grisée et « En pause »
  loin de la balise), TES MINES|n (une carte par mine : son minerai en icône, la ressource et le
  pays, ce qu'elle verse par heure, la pastille RANG ; en orange celle qu'une nation reprendrait ;
  la bulle dit la date de capture ; un clic la rend, avec confirmation), LES PLUS PROCHES (six
  mines, leur direction et leur distance, qui les tient, ce qu'elles verseraient) et COMMENT FAIRE
  (`TEXT`).
- **La banque de faction** : une section COFFRE DE LA BANQUE|n / 54 (`TILES`, une vignette par
  pile, le nombre en pastille ; un clic prend la pile ; « Tout prendre ») et la statistique COFFRE.
- **La vue d'ensemble** : une carte Mines dans TERRITOIRE (le nombre de mines, l'argent par heure).
- **La Boutique** : la section MINES DE LA TERRE, après les bots, avec la balise de concession
  (seulement sur une carte de la Terre).
- **La guerre** : la bulle d'une nation dit la mine qu'elle reprendrait ; une carte « Mine en jeu »
  rejoint celle du pays en jeu.
- **Dans le monde** : la balise (un socle de métal, un mât d'acier, une lampe ambre, un fanion
  orange frappé d'une pioche), la barre de capture en haut de l'écran, l'annonce de la mine dans
  la barre d'action, et le titre de la capture.

## La riposte contre les nations (lot 16, 2026-11-20)

- **L'écran de la guerre** (`WarMenu`, vue `dohy:guerre`) : la statistique PUISSANCE (rouge
  au-delà de 50, orange au-delà de 35) entre GUERRE et COFFRE ; la section LA COALITION CONTRE TOI
  (une carte par nation — sa bannière, « Alliée aux Corbeaux », son humeur en barre, la pastille
  PACTE — et une carte du pacte ou de l'assaut commun ; la bulle explique la coalition) ; la
  section TA CONTRE-OFFENSIVE, sans guerre en cours (jusqu'à quatre nations, d'abord celles qui te
  doivent quelque chose : ce qu'elles te doivent, le butin estimé de leur place forte, la pastille
  GUERRE ; un clic demande confirmation ; grisées avec leur raison : « Vaincue, elle se relève »,
  « En trêve ») ; l'heure de l'assaut d'une guerre que tu as déclarée s'intitule SA RIPOSTE.
- **Les nations** : les pastilles ALLIÉE et TE DOIT ; la bulle ajoute ce qu'elle te doit, son
  pacte, ce qu'elle a appris, sa défaite, et la commande pour lui déclarer la guerre.
- **Leur place forte** : la carte « Si elle tombe » (« Ce qu'elle t'a pris + 30 % de son
  trésor » ; la bulle détaille ce qu'elle rendra, et son alliée).
- **Ta base vue par elles** : les cartes « Ta puissance » (une étoile du Nether, la barre, le détail
  en bulle) et « Ce qu'elles ont appris » (la nation qui te connaît le mieux et sa leçon ; la bulle
  les donne toutes).
- **L'encyclopédie** : la fiche « La guerre » gagne Ta contre-offensive, Elles apprennent et Les
  coalitions.
- **Dans le monde** : les soldats du contingent allié portent les couleurs de leur nation, et la
  barre de l'assaut les deux noms (« Assaut des Corbeaux et de Kraken »).

## Vérification

Une branche de travail (`ci/shots`) lance un vrai client sous Xvfb sur GitHub, dans un monde
créé par le serveur dédié, ouvre les écrans par le vrai serveur intégré et pousse des captures
1920×1080 sur `shots-out`. Le monde porte des dimensions de datapack : le client demande une
sauvegarde à l'ouverture, ce que la branche passe d'elle-même. Rien de cela n'est dans le mod.
