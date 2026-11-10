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
| 3 | Palamod : donjons, profil RPG, monture, golem, dimension du Mineur, enchantement mystique | fait (en validation) |
| 4 | Conteneurs : machines (crusher déjà maquetté), coffres en paladium, coffre de vente, musée | à faire |

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
  `TILES` (petites vignettes d'objet : collections, vitrines du musée) ;
- des **cartes** : objet, titre, deux ou trois lignes, pastille, liseré de couleur, barre
  d'avancement, bulle détaillée, et deux actions (clic gauche, clic droit). Grisée : pas assez
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

## Vérification

Une branche de travail (`ci/shots`) lance un vrai client sous Xvfb sur GitHub, dans un monde
créé par le serveur dédié, ouvre les écrans par le vrai serveur intégré et pousse des captures
1920×1080 sur `shots-out`. Le monde porte des dimensions de datapack : le client demande une
sauvegarde à l'ouverture, ce que la branche passe d'elle-même. Rien de cela n'est dans le mod.
