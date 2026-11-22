# La nation et ses technologies (chapitre 23)

Grosse mise à jour demandée le 2026-11-20 : **des arbres de technologie dans la partie faction**,
divisés en domaines (énergie, industrie, agriculture…) et **bâtis sur NTM NEXT, NTM NEXT: Space et
NTM: Cursed** (Leafia Cursed Addons) ; **la partie pays et la partie faction fusionnent** en une
seule. Ce document prépare le terrain : les choix, l'architecture, l'inventaire des trois mods et
le découpage en lots.

Choix du joueur (2026-11-20) :

| Question | Choix |
|---|---|
| Ce qu'apporte une technologie | **Machines + bonus** : une machine des trois mods ne se pose qu'une fois sa technologie recherchée (le créatif passe, les machines déjà posées restent) ; chaque palier donne aussi un bonus |
| Ce que coûte une recherche | **Laboratoire + énergie** : un laboratoire posé dans tes claims produit des points de recherche en consommant de l'électricité ; s'y ajoutent des pièces NTM et de l'argent |
| L'arbre actuel (Globale, Pillage, Défense, Économie) | **Fondu dans les nouveaux domaines**, progression reprise |
| La fusion pays + faction | **Une seule nation** : un nom, un rang, un seul menu à onglets ; les pays tenus nourrissent la recherche |

## Lots

| Lot | Contenu | État |
|-----|---------|------|
| 23-0 | Préparation : inventaire des trois mods, brouillon des neuf arbres, ce document | fait |
| 23a | Une seule nation : le menu à onglets (territoire, technologies, économie, guerre…), le rang, la façade `Nation` | fait |
| 23b | Le moteur des technologies : domaines en données, état de recherche, écran des arbres ; l'arbre actuel fondu dedans | à faire |
| 23c | Le laboratoire de recherche : bloc, énergie (FE, réseaux de NTM NEXT), points de recherche, les pays qui nourrissent la recherche | à faire |
| 23d | Les verrous : pose des machines, exemptions, infobulles, encyclopédie | à faire |
| 23e | Contenu : Énergie et Industrie (paliers, coûts, bonus) | à faire |
| 23f | Contenu : Chimie et Agriculture | à faire |
| 23g | Contenu : Nucléaire et Défense | à faire |
| 23h | Contenu : Espace | à faire |
| 23i | Équilibrage, captures, vérification en jeu | à faire |

L'ordre compte : la nation (23a) donne le menu où vivent les technologies ; le moteur (23b) et le
laboratoire (23c) doivent exister avant que le moindre verrou (23d) ne s'active. Les verrous
arrivent avec les premiers domaines complets, pour qu'aucune machine ne soit bloquée sans moyen
de la débloquer.

## Les trois mods (inventaire du 2026-11-20)

| Mod | Version (serveur et client) | Objets | Blocs | Machines retenues |
|---|---|---:|---:|---:|
| NTM NEXT (`hbm`) | 1.0.5 | 3 979 | 1 247 | 445 |
| NTM NEXT: Space (`hbmspace`) | 1.0.2 | 1 087 | 723 | 47 |
| NTM: Cursed (`leafia`, Leafia Cursed Addons) | 1.0.1 | 621 | 469 | 101 |

« Machines retenues » : les blocs fonctionnels, hors minerais, décor, fluides et pièces de
multibloc. La liste est dans `tools/data/ntm_machines.json` (identifiant, mod, nom anglais et nom
français quand le mod en donne un), écrite par `tools/ntm_inventory.py` à partir des jars du
dossier mods (lus comme des archives, rien n'est exécuté) ; à relancer quand un des mods change de
version.

**Le brouillon des arbres** (`tools/data/tech_catalogue.json`) range **570 de ces 593 machines**
dans 52 paliers ; les 23 autres restent libres (plantes et graines, ruines et variantes
endommagées, fluides, décor). `tools/check_tech_catalogue.py` vérifie que chaque motif nomme une
machine qui existe, qu'aucune n'est rangée deux fois, que les prérequis tiennent, et liste avec
`--orphans` ce qui n'est rangé nulle part.

Ce que chaque mod apporte :

- **NTM NEXT** : presque tout. Énergie de la chaudière à bois au FEnSU, métallurgie (presses,
  fonderie, enclumes à paliers), assembleuses, chimie et pétrole, réacteurs (pile de Chicago,
  ZIRNOX, RBMK, PWR, Watz, fusion, ICF, DFC), tourelles, missiles et bombes, convoyeurs et drones.
- **NTM NEXT: Space** : fusées (bâtiment d'assemblage, pas de tir), stations orbitales et leurs
  propulseurs, sphère de Dyson, atmosphère et terraformation, serre industrielle, panneau solaire,
  foreuse à magma, collecteur de géante gazeuse, cultures (café, menthe, thé, fraise, cire).
- **NTM: Cursed** : réacteurs (PWR de Leafia, LFTR à sels fondus, APR, antimatière AMC),
  turbines modulaires (2×2 à 9×9), éolienne, chaudière haute pression, tuyaux spéciaux, cuve de
  mélange, ascenseurs, balise anti-météores, compléments du DFC.

## Une seule nation (23a)

Aujourd'hui, la faction et la nation vivent côte à côte : `FactionData` (claims, banque, arbre,
homes, ligue), la nation de la carte de la Terre (`EarthNations` : capitale, pays tenus, cible),
la guerre (`WarData`), le marché international, les mines. Le lot 22j a déjà réuni la page ; le
lot 23a fait de la faction **ta nation**, partout :

- **Un nom, un rang.** Le nom de la faction est celui de la nation. Le rang (`NationRank` :
  Faction, Cité, État, Royaume, Empire, Superpuissance) s'affiche partout où l'on voit la faction.
  Sans carte de la Terre, pas de pays : le rang suit le Gouvernement.
- **Un seul menu**, « Ma nation », à onglets :

  | Onglet | Ce qu'on y trouve (écrans actuels) |
  |---|---|
  | Vue d'ensemble | la page de faction |
  | Territoire | claims, capitale, pays à prendre, mines de la Terre |
  | Technologies | les neuf arbres, le laboratoire, la recherche en cours |
  | Économie | banque, coffre de la banque, réserve d'or, marché international |
  | Guerre | la guerre des nations (diplomatie, assauts, riposte) |
  | Events | events de faction |
  | Classement | classement, notation de la semaine |
  | Paramètres | paramètres |

- **Les données** restent où elles sont (pièces jointes du joueur et du monde) : une façade
  `Nation` les réunit derrière une seule API (nom, rang, territoire, trésor, recherche). Pas de
  migration risquée de pièce jointe dans ce lot ; si un champ doit changer de place plus tard, ce
  sera avec une migration (`optionalFieldOf`, valeur par défaut lue de l'ancien endroit).
- **Les pays nourrissent la recherche** (23c) : chaque pays tenu ajoute aux points des
  laboratoires, la capitale aussi.

### Fait (2026-11-21)

- **La façade** `faction/Nation` : nom, rang et titre (« Royaume de Nordheim »), territoire
  (claims, capitale, pays, cible, mines), trésor (banque, coffre, réserve d'or), recherche
  (paliers acquis, en cours, prêts ; le palier du Gouvernement), guerre (conflit, siège, nations
  hostiles). Elle lit les données où elles sont et n'écrit rien : aucune pièce jointe ne change.
- **Le rang sans carte de la Terre** suit le Gouvernement : en attendant ses domaines (23b), deux
  paliers de la branche Globale en font un (la conversion prévue plus bas) — Cité au palier 4,
  État au 6, Royaume au 8, Empire au 10, Superpuissance au 12. Sur la carte, rien ne change : les
  pays font le rang.
- **Le menu « Ma nation »**, huit onglets sur les écrans existants (le lot 24b en ajoute un neuvième, Entreprises, voir `docs/ORGANISATIONS.md`), dont les identifiants ne
  changent pas (les fiches de l'encyclopédie se découvrent comme avant) :

  | Onglet | Écran | Ses pages de détail (l'onglet reste allumé) |
  |---|---|---|
  | Vue d'ensemble | `faction` : identité et rang, territoire, banque, ligue, puis ce qui attend (technologies, guerre, events, home, bases ennemies, réserve, marché, notation) | — |
  | Territoire | `faction_claims` : tes terres (capitale, pays, cible, pays à prendre, mines), ici, tes claims | `faction_nations`, `faction_mines` |
  | Technologies | `faction_tree` (l'arbre actuel, jusqu'au lot 23b) | `faction_branch` |
  | Économie | `faction_bank` : déposer, retirer, trésor (réserve d'or, marché international, mines), coffre, historique | `faction_reserve`, `faction_world_market` |
  | Guerre | `guerre` : la guerre des nations, et le pillage (bases ennemies) | `faction_enemy_bases` |
  | Events | `faction_events` | — |
  | Classement | `faction_ranking` : ligues, semaine, notation | `faction_notation` |
  | Paramètres | `faction_settings` | — |

  L'hexagone d'une page de détail revient à son onglet ; celui d'un onglet, à la vue d'ensemble.
  Depuis le lot 24a (`docs/ORGANISATIONS.md`), les onglets Paramètres et Guerre laissent leur
  place à **Organisation** (régime, cadres, paramètres) et **Diplomatie** (traités, guerre,
  pillage) ; les paramètres, la guerre et les bases ennemies y deviennent des pages de détail.
- **Le nom et le rang partout** : la pastille du HUD montre le rang (la ligue passe sur la ligne
  du dessous), la liste des joueurs écrit « Royaume · Nordheim », le menu principal, la barre
  latérale (« Ma nation »), l'inventaire, `/f info` (titre, rang et ce qui donne le suivant) ;
  `/nation` ouvre le même menu que `/f` ; le préfixe des messages devient `[Nation]`.
- **Le vocabulaire** : « banque de faction » devient « banque nationale », « ta faction »
  « ta nation » (messages, écrans, encyclopédie, infobulles de la balise de concession, touche G
  « Ma nation (menu) »). Restent « events de faction » (le nom des events de Paladium), le
  concours Trixium et les succès de Paladium, et l'arbre de faction jusqu'au lot 23b.

## Les arbres de technologie (23b)

**Neuf domaines**, de cinq ou six paliers. Un palier **déverrouille la pose de ses machines** et
donne **ses bonus** ; il demande le palier d'avant et parfois un palier d'un autre domaine (on ne
raffine pas sans électricité, on ne lance pas de fusée sans chimie). **Le palier I de chaque
domaine est acquis d'office** — sauf l'Espace — pour que rien de ce qu'on fait au début ne se
bloque.

| Domaine | Vient de | Paliers (brouillon) |
|---|---|---|
| Gouvernement | Globale | Fondation · Administration · Symboles · État · Centralisation · Superpuissance |
| Économie | Économie | Trésor · Stockage · Commerce · Concessions · Bourse · Hégémonie |
| Énergie | — | Combustion · Vapeur · Carburants · Réseau et renouvelables · Grandes turbines · Stockage massif |
| Industrie | — | Atelier · Fonderie · Électromécanique · Production de masse · Laser et calcul · Forge à plasma |
| Chimie | — | Fluides · Pétrole · Raffinage · Pétrochimie · Chimie lourde |
| Agriculture | — | Cultures · Mécanisation · Agro-industrie · Génétique · Serres industrielles |
| Nucléaire | — | Radioprotection · Premiers réacteurs · RBMK · Eau pressurisée · Réacteurs avancés · Fusion et antimatière |
| Défense | Défense, Pillage | Fortifications · Tourelles légères · Artillerie · Missiles · Armes nucléaires · Armes exotiques |
| Espace | — | Communications · Lanceurs · Fusées · Stations orbitales · Colonisation · Ingénierie stellaire |

Quelques jalons, pour lire l'arbre (le détail est dans le brouillon) :

- **Énergie** : chaudière à bois, moteur Stirling et câbles (I) ; chaudière et turbine
  industrielles, tours de refroidissement (II) ; diesel, turbine à gaz à cycle combiné (III) ;
  tour solaire, géothermie, éolienne de Cursed, pylônes d'acier (IV) ; turbine Léviathan et
  turbines modulaires de Cursed (V) ; FEnSU (VI).
- **Industrie** : presse, haut fourneau de briques, broyeur (I) ; fonderie complète (II) ;
  assembleuse, soudeuse à l'arc, centrifugeuse, convoyeurs (III) ; usine d'assemblage,
  assembleuse de précision, grande foreuse, drones, réseau pneumatique (IV) ; laser minier,
  supercalculateur (V) ; forge à plasma, annihilateur (VI). Les enclumes de NTM suivent : fer et
  plomb (I), acier (II), desh (III)… jusqu'au dineutronium (VI).
- **Chimie** : tuyaux et pompes (I) ; derrick, chevalet de pompage, usine chimique (II) ;
  raffinerie, tour de fractionnement, craquage (III) ; distillation sous vide, cokéfaction (IV) ;
  fracturation, usine chimique lourde, alkylation et distillation cryogénique de Space (V).
- **Agriculture** : les machines agricoles de DoHy entrent dans l'arbre avec celles de NTM et de
  Space : abreuvoir (I) ; batteuse, scierie, tracteur, silo (II) ; moulin, machine à pain,
  biogénérateur, laiterie de Space (III) ; machine à OGM, bots de farming (IV) ; serre
  industrielle de Space (V).
- **Nucléaire** : compteur Geiger, décontamination, pile de Chicago (I) ; réacteur de recherche,
  ZIRNOX, RTG, centrifugeuse à gaz (II) ; RBMK (III) ; PWR de NTM et de Cursed, PUREX, SILEX (IV) ;
  Watz, LFTR, APR, cyclotron, accélérateur de particules (V) ; fusion, ICF, DFC, antimatière (VI).
- **Défense** : explosifs simples, mines, portes blindées (I) ; tourelles légères, sirène (II) ;
  autocanon, lance-roquettes, radar, presse à munitions (III) ; pas de tir de missiles, radar
  lourd, artillerie (IV) ; bombes nucléaires, champ de force, tourelle MASER (V) ; F.L.E.I.J.A.,
  bombe au balefire, bombe à antimatière de Space (VI).
- **Espace** : récepteur satellite (I) ; pas de tir Soyouz (II) ; bâtiment d'assemblage des
  fusées, pas de tir de Space (III) ; stations orbitales et propulseurs (IV) ; atmosphère,
  foreuse à magma, collecteur de géante gazeuse (V) ; sphère de Dyson, éditeur d'atmosphère,
  téléporteur (VI).

**Les bonus** agissent sur les systèmes de DoHy — les machines de NTM elles-mêmes ne sont pas
modifiées (aucune injection dans leur code) :

| Domaine | Bonus (brouillon) |
|---|---|
| Gouvernement | claims, F homes et leur délai, blason, MOTD (repris de la Globale) ; nombre de laboratoires ; recherches menées à la fois |
| Économie | réserve d'or (reprise de l'Économie) ; taxe du marché international ; revenus des mines de la Terre |
| Énergie | énergie consommée par les laboratoires et les machines de DoHy |
| Industrie | argent du Mineur ; minerais des mines de la Terre ; cases du coffre de la banque ; vitesse de recherche |
| Chimie | champs de pétrole et de gaz de la Terre |
| Agriculture | vitesse des machines agricoles de DoHy ; XP et argent du Fermier |
| Nucléaire | butin des largages nucléaires ; vitesse de recherche |
| Défense | claims, réduction des dégâts en event, objets de pillage (repris de la Défense et du Pillage) ; vie des soldats des assauts des nations |
| Espace | vitesse de recherche |

**L'écran** (onglet Technologies) : les neuf domaines en cartes (palier atteint, recherche en
cours) ; un domaine ouvre ses paliers, chacun avec ses machines (icônes), ses bonus, son coût et
ses prérequis, et « Rechercher » quand tout est réuni. Les données vivent dans
`data/dohy/tech/<domaine>.json` (le brouillon y passera), rechargées par `/dohy reload` comme les
métiers.

## Ce que coûte une recherche (23c)

**Le laboratoire de recherche** est un bloc de DoHy, posé dans un claim. Il consomme de
l'électricité et produit des **points de recherche** pour toute la nation ; le Gouvernement en
permet un, deux puis trois.

- **L'énergie** : le laboratoire prend de l'énergie NeoForge (FE), comme les machines agricoles de
  DoHy. NTM NEXT relie ses réseaux à ces blocs par son pont d'énergie (`EnergyConversion`,
  `feFromHe` / `heFromFe`, les points « étrangers » de ses réseaux) : un câble de NTM branché au
  laboratoire doit l'alimenter. **À vérifier en jeu au lot 23c** ; à défaut, un convertisseur.
- **Les pays** : chaque pays tenu ajoute aux points des laboratoires, la capitale aussi (taux et
  plafond au lot d'équilibrage).
- **Le coût d'un palier** : des points, des pièces de NTM (livrées au laboratoire) et de l'argent
  de la banque de faction. Ordres de grandeur, à équilibrer (23i) :

  | Palier | Points | Pièces de NTM (exemple) | Argent |
  |---|---:|---|---:|
  | I | acquis d'office | — | — |
  | II | 1 500 | plaques d'acier, bobines de cuivre | 25 000 $ |
  | III | 6 000 | circuits intégrés, condensateurs | 100 000 $ |
  | IV | 20 000 | circuits militaires, micropuces | 500 000 $ |
  | V | 60 000 | circuits polyvalents, unités de contrôle | 2 000 000 $ |
  | VI | 180 000 | processeurs quantiques, horloges atomiques | 10 000 000 $ |

- **Une recherche à la fois**, puis deux et trois avec le Gouvernement.

## Les verrous (23d)

- **La pose** d'une machine d'un palier non acquis est refusée, avec le palier qui la débloque
  (« Technologie requise : Énergie II, Vapeur »). Le **créatif** passe, comme pour les verrous de
  métier (le contrôle de la CI s'y appliquera aussi).
- **Les machines déjà posées** continuent de tourner : seul un nouveau placement est verrouillé.
  Une machine déjà posée qu'on casse et qu'on repose demande sa technologie.
- **Sans faction**, seuls les paliers d'office.
- **L'infobulle** de chaque machine verrouillée dit son domaine et son palier (l'état de recherche
  est envoyé au client).
- Le moteur reprend celui des verrous de métier (`JobGate`) : un index par identifiant et des
  motifs, reconstruit à chaque rechargement.
- Pas de verrou sur les recettes : la fabrication passe par les machines de NTM (assembleuses,
  modèles), hors de portée ; le verrou de pose suffit.

## L'arbre actuel, fondu dedans

| Ancienne branche | Domaine | Correspondance |
|---|---|---|
| Globale (12 paliers) | Gouvernement | anciens paliers 1-2 → palier I, 3-4 → II… 11-12 → VI |
| Défense (12) | Défense | idem |
| Pillage (12) | Défense | idem, les déverrouillages d'objets (`use:dynamite`, `craft:hood_helmet`…) suivent |
| Économie (6) | Économie | palier pour palier |

- **Rien ne se perd** : chaque ancien palier acquis garde sa récompense (claims, homes, blason,
  MOTD, réserve d'or, réduction des dégâts, objets). Un nouveau palier est acquis quand tous ses
  anciens paliers l'étaient ; sinon il reste à rechercher.
- Les anciennes conditions (« N claims », « N h de jeu », « visiter N structures »…) restent des
  conditions des paliers repris : elles gardent le goût de l'ancien arbre.
- Une amélioration en cours au moment de la mise à jour s'achève avant la conversion.

## Encore à trancher, lot par lot

- Les chiffres : coûts, points par laboratoire, apport des pays (23c, 23i).
- Les machines agricoles de DoHy dans l'arbre Agriculture (le brouillon les y met).
- Ce qu'il advient d'une machine verrouillée trouvée dans un coffre de pillage : elle se garde,
  elle ne se pose pas.
- Les nouveautés d'une prochaine version des trois mods : `tools/ntm_inventory.py` les sort,
  `tools/check_tech_catalogue.py --orphans` dit où il faut les ranger.
