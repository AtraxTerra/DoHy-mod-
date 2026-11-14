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
- `Ui` : la palette et les polices.

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
  trois onglets (déblocages, bonus, récompenses) en listes qui défilent. Vert : acquis ; gris :
  verrouillé ; orange : déclaré mais absent du mod (objet pas encore porté, effet pas appliqué).
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
  (château) et les **métiers** (objet doré). Le bouton rouge sous le craft, à la place du Lucky Pass
  de Paladium, ouvre le **pass du mois** (bouton PASS et relique du mois, depuis le lot 8 ; il
  ouvrait le concours Trixium, qui reste au menu principal). Une bulle nomme chaque bouton.
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

## Vérification

Une branche de travail (`ci/shots`) lance un vrai client sous Xvfb sur GitHub, dans un monde
créé par le serveur dédié, ouvre les écrans par le vrai serveur intégré et pousse des captures
1920×1080 sur `shots-out`. Le monde porte des dimensions de datapack : le client demande une
sauvegarde à l'ouverture, ce que la branche passe d'elle-même. Rien de cela n'est dans le mod.
