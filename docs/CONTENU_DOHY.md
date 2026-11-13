# Contenu propre à DoHy

Ce qui ne vient pas de Paladium. Le contenu repris de PalaMod vit sous l'espace de noms `palamod`
pour que son pack de textures s'applique (`docs/PALAMOD.md`) ; ce qui est décrit ici vit sous
`dohy`, a son propre onglet créatif, et **ses textures sont dessinées par
`tools/dohy_assets.py`** — rien n'est copié d'un autre mod.

## Serveur de trading (`dohy:trading_server`)

Inspiré des serveurs de trading de **NationsGlory**, où la compétence d'ingénieur consiste entre
autres à en installer un. Ici : une machine posée qui transforme de l'énergie en argent.

### Ce qu'elle fait

- **Sous tension, elle produit 9 000 $ par heure**, par tick, sans arrondi perdu. L'argent
  s'accumule dans sa caisse ; un clic sur la carte « Caisse » de son écran l'encaisse.
- **Caisse pleine (8 h de production, 72 000 $), elle s'arrête** : un revenu passif qui demande
  quand même de passer.
- **Quatre serveurs tournent à la fois par joueur.** Les suivants attendent (« En attente ») et
  prennent la place d'un serveur cassé ou déchargé. Le compte se fait sur les machines en mémoire :
  un serveur dans un chunk déchargé ne produit pas et ne prend pas de place.
- **Elle appartient à qui la pose.** Seul le propriétaire encaisse. Cassée, elle lui rend sa caisse.
- Ses voyants s'allument quand elle produit (état de bloc `lit`, lumière 7).

### Compatibilité NTM NEXT

DoHy ne dépend pas de NTM NEXT et n'appelle rien de son code. La compatibilité passe par deux
standards que NTM NEXT lit lui-même.

| | Comment | Vérifié |
|---|---|---|
| **Énergie** | Le bloc expose la capacité d'énergie de NeoForge (`Capabilities.Energy.BLOCK`, en FE), en réception seule, sur toutes ses faces. Les câbles de NTM NEXT cherchent cette capacité sur leurs voisins et y poussent leur courant en convertissant **5 HE en 1 FE** (`EnergyConversion`, `he_ratio` 5 / `fe_ratio` 1). Le serveur tire donc **250 HE par tick**. | La capacité, en jeu, par les mêmes appels qu'un câble (insertion en transaction, simulation annulée, extraction refusée). **Pas avec un câble NTM réel** : la machine de test n'a pas NTM. |
| **Recette** | `data/dohy/recipe/assembly_machine/ass.dohy_trading_server.json`, de type `hbm:assembly_machine` : 8 plaques d'acier (`#c:plates/steel`), 4 circuits avancés, 2 écrans cathodiques, 1 moteur, 4 lingots de paladium ; 600 ticks, 500 HE par tick. Elle porte la condition `neoforge:mod_loaded` `hbm`. | Le format, sur les 707 recettes d'assemblage de NTM NEXT et son codec (`GenericRecipe`). Sans NTM, la recette est bien ignorée sans erreur. **Pas dans la machine d'assemblage elle-même.** |

Sans NTM NEXT, une recette d'établi prend le relais (blocs de fer, bloc de redstone, diamants,
lingot de paladium, bloc d'or) ; il faut alors un autre mod qui fournit du FE pour l'alimenter.

### Code et réglages

- `tech/DoHyTech` (enregistrement, capacité), `TradingServerBlock`, `TradingServerBlockEntity`,
  `TradingServerMenu`, `TradingServerConfig`.
- `data/trading_server.json` : `income_per_hour`, `energy_per_tick`, `energy_capacity`,
  `energy_max_input`, `buffer_hours`, `max_per_player`.
- Le tag `minecraft:mineable/pickaxe` est écrit par `tools/palamod_assets.py`, qui y inscrit
  aussi les blocs d'ici.

## Agriculture (onglet « DoHy — agriculture »)

Reprise de la filière agricole de **NationsGlory** (wiki, catégorie « Agriculture ») : des
céréales, des OGM à chercher, un moulin, une machine à pain, des pains à effets, un élevage à
nourrir. Le tracteur et le silo n'en font pas partie (le shop tient lieu de silo). Réglages :
`data/agriculture.json` ; chiffres : `docs/BALANCE.md` § 6 sexdecies.

### Les dix céréales

Neuf sous `dohy`, plus le blé de Minecraft. Chacune a sa culture (huit stades), sa semence, son
grain, sa farine.

| Climat | Céréales | Où on trouve les semences |
|---|---|---|
| Tempéré (température de biome de 0,4 à 0,9 : plaines, forêts, marais) | maïs, orge | l'herbe de ces biomes |
| Froid (sous 0,4 : taïga, toundra, sommets) | avoine, seigle | l'herbe de ces biomes |
| Chaud (0,9 et plus : jungle, savane, désert) | tournesol, soja | l'herbe de ces biomes |
| Partout | blé | comme d'habitude |
| Partout | **fonio, sorgho, kamut** (OGM) | la machine à OGM, nulle part ailleurs |

- NationsGlory réserve six céréales à des continents ; ici le **climat du biome** en tient lieu.
  Il se lit sur la température de base du biome : un biome de mod se range tout seul.
- **Hors de son climat, une semence refuse d'être plantée** et le dit dans la barre d'action
  (« Maïs : ne pousse qu'en climat tempéré (ici : froid) »). Une culture posée autrement ne
  pousse pas et la poudre d'os n'y fait rien.
- Une herbe cassée a 4 % de chance de lâcher une semence d'une céréale **du climat où elle
  pousse**.
- Une céréale naturelle mûre rend 1 à 2 grains et ses semences, comme le blé.
- **Un OGM mûr rend 2 à 4 grains et aucune semence.** Arraché avant maturité, il rend sa semence.
  Il pousse plus lentement (60 % des ticks de croissance).

### La machine à OGM : une recherche

Un OGM ne se trouve pas, il se cherche (Fermier niveau 20).

- La recette d'une souche est **secrète** : 9 objets différents parmi 26 (bambou, canne à sucre,
  cactus, œuf, sucre…), chacun en quantité précise de 1 à 16. Elle se tire de la graine du monde,
  du joueur et de la souche : rien n'est stocké, et elle n'est écrite nulle part.
- L'écran de la machine compose : on ajoute un ingrédient d'un clic, **clic gauche un de plus,
  clic droit un de moins**. « Lancer l'essai » puise dans l'inventaire.
- **Un essai consomme toute la composition, juste ou fausse.**
- Raté, il dit **quelle part de la composition est dans la recette** et donne **un indice** sur la
  première erreur : la devinette d'un ingrédient qui manque (« Un roseau qui ne pousse qu'au bord
  de l'eau »), ou la fourchette d'une quantité fausse (« il en faut entre 5 et 8 », puis « un peu
  plus » ou « un peu moins » une fois dans la fourchette). Les six derniers verdicts restent dans
  le carnet de l'écran.
- Le métier aide : au niveau 30 un échec dit aussi combien de quantités sont justes, au niveau 60
  il donne deux indices.
- Réussi, il rend **8 graines** et la recette est connue : une carte la recharge d'un clic.
- **Après 2 000 graines fabriquées, la souche mute** : la recette change et la recherche reprend.

Simulée sur 4 000 recettes avec une méthode simple (un intrus en tête pour faire sortir les
devinettes, puis les quantités une à une) : **28 essais en moyenne avec un indice (1 450 objets),
18 avec deux (800 objets)**.

### Les machines électriques

| Bloc | Rôle |
|---|---|
| **Moulin** (`dohy:flour_mill`) | 3 céréales + 120 FE → 1 farine. Clic gauche une farine, clic droit jusqu'à une pile. |
| **Machine à pain** (`dohy:bread_machine`) | la liste des pains ; un clic par fournée, clic droit huit fournées. |
| **Biogénérateur** (`dohy:biogenerator`) | brûle des céréales (clic avec la céréale en main, 64 en attente) : 400 FE par céréale, 20 FE par tick, poussés vers les machines qui le touchent. |

- **À la main** : un clic sur une recette de l'écran puise dans l'inventaire du joueur et y rend
  le produit, tout de suite. C'est ce travail-là qui donne de l'XP de Fermier (2 par farine, 8 à
  40 par fournée) et le pain en plus du métier.
- **En automate** : le moulin et la machine à pain ont six cases d'entrée et trois de sortie. Un
  **entonnoir dessus ou sur un côté** les remplit, un **entonnoir dessous** les vide ; les tuyaux
  des mods passent par la capacité d'objets de NeoForge, avec les mêmes faces. Tant qu'elle a de
  quoi faire et de l'énergie, la machine travaille seule : une farine en 10 ticks, une fournée en
  40. Elle moud la première farine possible et cuit la première fournée dont tous les ingrédients
  sont là.
- **Une sorte d'objet n'occupe qu'une case d'entrée** (64 au plus) : un entonnoir de farine ne
  peut pas boucher la place de la crème.
- L'écran montre ce que la machine contient (section « Dans la machine ») : « Charger » y dépose
  ce qu'elle accepte depuis le sac, « Tout reprendre » le rend, un clic sur une pile la reprend.
- Le biogénérateur prend aussi ses céréales par entonnoir.
- Cassée, une machine rend ce qu'elle contenait. La réserve d'énergie est de 20 000 FE.
- Ce que l'automate ne fait pas : donner de l'XP, sortir le pain en plus, cuire le pain d'épices.
  Ce qu'il produit compte quand même pour les **contrats** de celui qui l'a posée.
- **Énergie** : la capacité FE de NeoForge, sur toutes les faces, comme le serveur de trading. Le
  moulin et la machine à pain reçoivent, le biogénérateur donne. Un câble de NTM NEXT les alimente
  (5 HE = 1 FE) ; sans autre mod, le biogénérateur posé contre elles suffit.
- **Recettes** : machine d'assemblage de NTM NEXT (6 plaques d'acier, 1 circuit de base, 1 moteur,
  et une meule, un four ou un composteur) sous condition `neoforge:mod_loaded` `hbm` ; recette
  d'établi sous la condition inverse.
- Vérifié sans NTM, comme pour le serveur : la capacité répond comme à un câble, mais ni un câble
  réel ni la machine d'assemblage n'ont été essayés.

### Les pains

Dix pains, un par céréale, plus le pain d'épices. Ils ne sortent que de la machine à pain. La gamme est celle du wiki (nourriture, soin en
demi-cœurs, effets) ; les noms et les recettes sont de DoHy.

| Pain | Fournée | Faim | Effet |
|---|---|---|---|
| Pain de maïs | 4 farines → 4 | 4 | — |
| Pain d'avoine | 4 farines → 4 | 5 | — |
| Pain de seigle | 4 farines → 4 | 6 | — |
| Pain d'orge | 2 farines, 1 sucre | 4 | +1 cœur, Rapidité I et Célérité I 20 s |
| Pain de soja | 2 farines, 1 œuf | 6 | +1,5 cœur, Régénération II 5 s |
| Pain de tournesol | 2 farines, 1 crème | 4 | Vol de vigueur I 1 min |
| Pain du mineur | 2 farines de blé, 1 saindoux, 2 baies lumineuses | 4 | Flair du mineur 5 min |
| Pain d'épices | 2 farines de seigle, 1 rayon de miel, 1 sucre → 2 | 8 | +2 cœurs, Régénération I 10 s, Résistance I 1 min. **Fermier niveau 50, à la main seulement.** |
| Pain de fonio | 2 farines, 1 crème, 1 saindoux | 6 | +2 cœurs, Absorption I et Dernier rempart 30 s |
| Pain de sorgho | 3 farines, 1 crème, 1 saindoux, 1 œuf | 2 | +2 cœurs, Force II, Rapidité II et Absorption I 2 min, Régénération II 30 s, Vol de vigueur II 1 min |
| Pain de kamut | 2 farines, 1 crème, 1 melon scintillant | 4 | lève les effets néfastes, Immunité 2 min |

Quatre effets n'existent pas dans Minecraft (`agri/AgriEffects`) :

- **Vol de vigueur** (le « Power Steal » du wiki) : un coup de mêlée rend en vie 10 % des dégâts
  infligés, par niveau.
- **Dernier rempart** (« Last Stand ») : le coup qui aurait tué laisse un cœur, et l'effet s'en va.
- **Immunité** : aucun effet néfaste ne tient. Sur NationsGlory ce pain guérit la « Contagion »
  d'un missile ; DoHy n'a pas cette maladie, le pain lève donc tous les effets néfastes.
- **Flair du mineur** : une pierre cassée a 15 % de chance de lâcher un minerai (fer, cuivre, or,
  améthyste, titane, paladium, findium, diamant, trixium, du plus courant au plus rare), **au plus
  un toutes les 20 secondes**.

Un Fermier de niveau 40 a 10 % de chance de sortir un pain de plus par fournée.

### L'élevage

Inchangé dans son principe : une **mangeoire** (64 céréales) et un **abreuvoir** (4 seaux) à
6 blocs. Toutes les 2 minutes, la mangeoire nourrit jusqu'à 6 bêtes adultes ; chacune mange
**4 céréales** et laisse un produit : la vache de la **crème**, le cochon du **saindoux**, la poule
un œuf. Une tournée coûte une dose d'eau.

### Le tracteur, ses outils, le silo et la pompe

Le fonctionnement suit celui des engins agricoles de NationsGlory (tracteur, charrue, semoir,
épandeur, moissonneuse, silo) tel que le décrit son wiki public ; **le modèle, la texture, les
icônes et le code sont dessinés et écrits pour DoHy** (`tools/tractor_model.py`,
`tools/dohy_assets.py`), rien n'est repris de leurs fichiers.

**Le tracteur** (`dohy:tractor`, Fermier niveau 10 pour le poser et le conduire) est une entité.

- Un clic sur le sol le pose ; un clic dessus fait monter. Il se conduit comme on marche : il
  tourne vers où l'on regarde, avance, recule au ralenti, grimpe un bloc. Environ 8,5 blocs par
  seconde à vide, 5 quand l'outil travaille.
- **Carburant** : un réservoir de 4 000 unités, une unité par bloc parcouru, deux quand l'outil
  travaille. À sec, il ne bouge plus. On fait le plein d'un clic, le carburant en main : un
  **bidon de NTM NEXT** (diesel, essence, pétroil, kérosène, biocarburant, fioul, éthanol — 0,6 à
  1,1 unité par mB), un seau de lave (250), du charbon (250 la pièce), un bloc de charbon, une
  tige de blaze. Ou en le garant à côté d'une pompe.
- **Un outil à la fois**, large de trois blocs, qui agit derrière lui. Clic sur le tracteur,
  l'outil en main, pour l'atteler (l'ancien revient dans le sac). Un outil attelé travaille ; au
  volant, la touche de saut tenue un instant l'arrête et le remet en marche.

| Outil | Ce qu'il fait |
|---|---|
| **Charrue** (`dohy:tractor_plow`) | l'herbe, la terre et les chemins deviennent de la terre labourée ; les herbes hautes sont arrachées. |
| **Semoir** (`dohy:tractor_seeder`) | sème sur la terre labourée libre les semences chargées (1 152 au plus) : blé et les neuf céréales. Par défaut, la première semence chargée qui pousse dans le climat du champ ; l'écran permet d'en imposer une. |
| **Épandeur** (`dohy:tractor_spreader`) | une poudre d'os par pousse, une fois par passage (576 au plus). |
| **Moissonneuse** (`dohy:tractor_harvester`) | coupe le blé et les céréales **mûrs**. Le grain va dans la trémie (2 304), les semences rendues retournent au semoir. |

- **La moisson compte comme une récolte à la main** : elle passe par la casse normale du bloc, au
  nom du conducteur. XP de Fermier, contrats de récolte et collections s'appliquent sans code à
  part.
- **L'écran** (accroupi + clic, ou la touche d'inventaire au volant) : le réservoir, l'outil
  (l'atteler, le dételer, l'arrêter), charger les semences et l'engrais du sac, choisir la
  semence, reprendre une pile de la trémie.
- **Accroupi + coup** le démonte : il rend le tracteur (qui garde son carburant), l'outil, les
  semences, l'engrais et la récolte. Rien d'autre ne l'abîme. Ses roues ne piétinent pas la terre
  labourée.

**Le silo** (`dohy:silo`) stocke 16 384 céréales, toutes sortes confondues (blé et les neuf
d'ici).

- Un tracteur qui passe à moins de 4 blocs **y vide sa trémie tout seul**.
- Un clic avec une céréale en main la dépose ; un clic ouvre l'écran : le stock, « Tout déposer »,
  un clic pour prendre une pile, un clic droit pour **acheter un lot de 16** au prix de la boutique
  (cours de l'heure et remises compris), versé directement dans le silo.
- **Automatisation** : entonnoir, convoyeur ou tuyau d'objets dessus ou sur un côté pour entrer,
  dessous pour sortir — la même règle que le moulin, par la capacité d'objets de NeoForge. Un
  entonnoir sous le silo alimente donc un moulin ou un biogénérateur.
- Cassé, il rend tout son grain.

**La pompe à carburant** (`dohy:fuel_pump`) a une cuve de 16 000 mB qui n'accepte que les
carburants de la table.

- Elle fait le plein de tout tracteur garé à moins de 3 blocs, à 40 mB par tick.
- On la remplit à la main (bidon, seau) ou **par un tuyau de fluide** : sa cuve est offerte à la
  capacité de fluide de NeoForge sur toutes ses faces, celle que cherchent les tuyaux de NTM NEXT.
- Un clic à main nue dit ce qu'elle contient.

**Compatibilité NTM NEXT**, en résumé : les carburants sont ses fluides (`hbm:diesel`,
`hbm:gasoline`…), lus par la capacité de fluide des objets (ses bidons) et des blocs (ses
tuyaux) ; le silo parle à ses convoyeurs par la capacité d'objets ; les sept recettes passent par
sa machine d'assemblage (plaques d'acier, moteurs, circuit) sous condition `neoforge:mod_loaded`
`hbm`, avec une recette d'établi sous la condition inverse. Sans NTM NEXT, tout marche au charbon
et à la lave.

Réglages : bloc `farming` et `tractor_level` de `data/agriculture.json` (réservoir, consommation,
vitesses, capacités, portées, table des carburants).

### Contrats et commandes

- Un nouveau type de contrat, **`PRODUCE`** : produire à une machine. Sa cible est l'objet produit
  ou son tag (`#dohy:flours`, `#dohy:breads`). Il avance à la main comme en automate.
- Contrats du jour : « Récolter des céréales mûres » (300), « Moudre de la farine » (120),
  « Cuire des pains » (60). Contrat de la semaine : « Grande fournée » (600 pains).
- **Commande de trixium** : dix commandes de plus (maïs, avoine, tournesol, farine de blé, pains
  de maïs, de seigle, d'orge, de soja, crème, saindoux), soit 52. Toutes s'achètent au shop.

### En vente, au cours de l'heure

Les grains, les farines, les pains, la crème et le saindoux sont au shop, onglet
**« Agriculture »**. Leur prix **change chaque heure réelle**, objet par objet : un facteur entre
0,7 et 1,4, le même à la vente et à l'achat (`economy/market/HourlyCourse`, bloc `course` de
`data/economy.json`). La fiche du shop l'affiche (« COURS DE L'HEURE +18 % · CHANGE DANS 23 MIN »),
`/worth` aussi. Les grains et farines d'OGM ne s'achètent pas ; les semences ne sont pas au shop.

### Code

- `agri/` : `Cereal`, `Climate`, `AgriCropBlock`, `SeedItem` ; `GmoResearch`, `GmoMachineMenu`,
  `AgriData` (le carnet, attaché au joueur) ; `AgriMachineBlock`, `AgriMachineBlockEntity`,
  `MillMenu`, `BreadMachineMenu`, `AgriProduction` (contrats) ; `Bread`, `BreadItem`,
  `AgriEffects` ; `FeederBlock`,
  `FeederBlockEntity` ; `AgriEvents` (herbe, flair du mineur) ; `AgriConfig`.
- Le tracteur : `Tractor` (l'entité), `TractorTool`, `TractorToolItem`, `TractorItem`,
  `TractorFuel`, `TractorMenu`, `TractorEvents` ; `SiloBlock`, `SiloBlockEntity`, `SiloMenu` ;
  `FuelPumpBlock`, `FuelPumpBlockEntity` ; côté client `client/agri/` (`TractorGeometry` est
  écrit par `tools/tractor_model.py`).
- `tools/dohy_assets.py` dessine tout ; `tools/check_agri_prices.py` (CI) vérifie qu'aucune
  recette ne devient rentable en achetant ses ingrédients au cours le plus bas.

## La Terre (une carte du monde réel)

DoHy sait jouer sur une carte de la Terre faite d'avance (une carte WorldPainter, par exemple) :
il dit dans quel pays on se trouve, lâche le joueur au hasard sur une terre, et pose les minerais
là où le monde réel les exploite. **Rien de cela n'existe dans un monde ordinaire** : c'est un
datapack, posé dans la sauvegarde de la carte, qui l'allume.

### Le datapack d'une carte

Il remplace `data/dohy/earth/earth.json` (livré éteint par le mod) par le **calage** de sa carte
et fournit la grille des pays :

- `projection` (`miller` ou `equirectangular`), `blocks_per_degree`, `origin_x` (le x du méridien de
  Greenwich), `equator_z` (le z de l'équateur) ;
- `grid` : un fichier binaire compressé, une case pour quelques blocs, deux octets par case (le
  numéro du pays ; le bit de poids fort marque des eaux côtières) ;
- `countries` : numéro, code à trois lettres, nom, teinte de carte.

Le datapack de la carte **Earth24k** est sur la branche `datapack-terre` du dépôt. Calage mesuré
sur la carte elle-même : projection de Miller, 68,6755 blocs par degré, Greenwich en x = 1,33,
équateur en z = 700,35 — 99 % de concordance avec les côtes de Natural Earth (hors Antarctique).
La grille des 256 pays et territoires vient de Natural Earth (domaine public), une case pour
8 blocs.

### Ce que ça change en jeu

- **Le pays.** Une fois par seconde, le mod regarde où est le joueur. Au passage d'une frontière,
  un titre s'affiche (« Allemagne — Aux mains d'Arcanum », la nation qui le tient) avec une
  ligne dans le chat (« … Tu quittes : France. ») ; en haute mer, « Eaux internationales ». Un pays ne s'annonce qu'après deux relevés de suite : longer une
  frontière ne fait pas clignoter l'écran. Les eaux à moins de 32 blocs d'une côte sont celles du
  pays ; au sud du 60ᵉ parallèle, tout est l'Antarctique.
- **Le saut.** Au spawn, derrière la plateforme d'arrivée, une dalle porte **la carte du monde**
  en blocs (un pixel pour 232 blocs de la carte, chaque pays d'une des sept teintes d'une carte
  politique). On y marche : la barre d'action dit le pays qu'on a sous les pieds. Un **puits** y
  est ouvert dans l'Atlantique : qui y saute est lâché 96 blocs au-dessus d'une terre tirée au
  hasard — la même chance pour chaque parcelle de terre du globe — et descend en chute lente ; la
  réception ne blesse pas. Le point est vérifié avant le saut : jamais sur l'eau.
- **Les ressources** (`data/dohy/earth/deposits.json`, écrit par `tools/earth_deposits.py`) ;
  refonte du 2026-11-13 : **plus rien n'est semé partout**.
  - **Le nettoyage.** La carte Earth24k portait déjà, posés par WorldPainter, du fer, de l'or, du
    diamant, du charbon et surtout du quartz du Nether (50 à 70 par chunk) à peu près partout.
    À sa première visite, chaque chunk en est vidé, avec tout ce que le jeu ou les mods y auraient
    encore posé (la liste `clear` : les minerais du jeu, de NTM NEXT, de Create, le pétrole) ;
    chaque minerai prend la roche d'un voisin du même chunk. Puis viennent les gisements géants,
    puis les filons.
  - **Filons par pays** (34 ressources). Le charbon, le fer et le cuivre ne sont plus que dans les
    pays qui les exploitent ou les ont exploités — les vieux bassins d'Europe compris (France,
    Belgique, Allemagne, Royaume-Uni, Espagne…) ; le quartz du Nether au Brésil, aux États-Unis, à
    Madagascar, en Inde, en Chine, en Russie, en Norvège et en Allemagne. Les autres ressources
    gardent leurs pays et **60 % de leurs filons** : les gisements géants prennent le relais.
  - **245 gisements géants** (`bodies`), de vraies mines et de vrais champs, posés à leur place
    (latitude et longitude du site réel, à quelques kilomètres près), chacun sur plusieurs chunks :
    - **Mines** : un ellipsoïde bosselé de 22, 16 ou 11 blocs de rayon selon le rang mondial
      (12, 9 ou 7 de haut), allongé et tourné quand le gisement l'est (Kiruna, le Witwatersrand,
      le bassin de Bowen). Densité selon la ressource : 40 % de charbon, 35 % de fer, 30 % de
      cuivre, 10 % d'or, 12 % d'uranium… ; au cœur, un tiers de plus, et des blocs de minerai brut
      (fer, cuivre, or). Certaines mêlent des minerais : Olympic Dam (cuivre, uranium, or),
      Kolwezi (cuivre, cobalt), Mount Isa (cuivre, plomb, zinc), Grasberg (cuivre, or)… Sous la
      surface : le centre à 24 blocs de haut pour le fer et le cuivre, plus bas pour l'or et
      l'uranium, et jamais à moins de quatre blocs du sol ; la bauxite, le salpêtre et le lignite
      à fleur de sol, comme dans leurs mines à ciel ouvert.
    - **Diamant** : des **cheminées de kimberlite** (tuf semé de 2,5 % de diamant), de 8, 6 ou
      5 blocs de rayon, plus larges en haut, du fond du monde jusqu'à six blocs sous la surface :
      Jwaneng, Orapa, Mir, Oudatchny, Argyle, Kimberley, Catoca, Ekati, Mbuji-Mayi, Venetia.
    - **Pétrole et gaz** : des **nappes** de pétrole de NTM NEXT (85 %), minces (six blocs) et
      larges, à 10 blocs de haut ou sous le fond de la mer : Ghawar (12 × 75 blocs, du nord au
      sud), Burgan, Safaniya, Rumaila, Kirkouk, South Pars, Samotlor, Ourengoï, Tengiz, Kashagan,
      le bassin permien, Prudhoe Bay, le golfe du Mexique, Cantarell, la ceinture de l'Orénoque,
      Maracaibo, Brent, Forties, Troll, Groningue, Hassi Messaoud, le delta du Niger, Daqing, le
      pré-sal de Santos… (47 champs). Le derrick de NTM en tire pétrole et gaz. Les **sables
      bitumineux de l'Athabasca** sont une couche de sable bitumineux de NTM de 2 à 14 blocs
      sous la surface.
    - **Create Diesel Generators** : les chunks d'un champ ont du pétrole pour sa pompe, 4 à
      9 millions de mB par point de richesse du bord au centre ; Ghawar et South Pars ont un cœur
      inépuisable (plus de 10 millions). Ailleurs, le datapack lui retire le pétrole au hasard.
    - **Quartz** : quatre mines de quartz du Nether (Spruce Pine, Diamantina, Hot Springs,
      Madagascar), avec des **géodes de quartz d'AE2** (une coque de quartz certus semée de 12 %
      de quartz bourgeonnant, creuse) : 6, 4 ou 2 selon le rang.
    - Leur forme ne dépend que de leur nom : chaque chunk en pose sa part quand il arrive, et rien
      n'est lu hors de ce chunk. Aucun ne déborde sur un autre ; chacun tombe dans son pays sur la
      grille de la carte (vérifié par le script, `earth_deposits.py countries.json grille.npy`).
  - **Les trouver** : `/gisements [ressource]` donne les six plus proches, leur pays, leur
    direction et leur distance (« Kiruna, fer (Suède) : 412 blocs au nord ») ; au passage d'une
    frontière, le chat dit les ressources du pays (« Ressources : charbon, fer ; gisements géants :
    Bassin lorrain (fer), Bassin du Nord (charbon). »).
  - Tout est posé **à l'arrivée du chunk**, pas à sa génération : une carte faite d'avance a déjà
    ses chunks sur le disque. Chaque chunk servi en garde deux marques (`earth_ground` pour le
    nettoyage et les gisements géants, `earth_deposits` pour les filons) ; un chunk visité avant
    la refonte est repris une fois en entier. Les blocs sont écrits dans les sections du chunk
    (un minerai qui remplace de la roche ne change ni la lumière ni les hauteurs) : un millième de
    seconde par chunk, et 15 ms au plus de service par tick. Un minerai d'un mod absent est ignoré.
- **Ce que le datapack retire.** Sur la carte, tous les minerais du jeu (charbon, fer, cuivre, or,
  redstone, diamant, lapis, émeraude) ne sont plus générés (`neoforge:remove_features`), ni le
  zinc de Create ; les minerais d'overworld de NTM NEXT sont coupés par son propre interrupteur
  (`hbm/world_config/worldgen.json`) ; le pétrole au hasard du Create Diesel Generators aussi
  (`deny_oil_biomes`). Tout vient des filons, des gisements géants et de la dimension du Mineur.

### Les zones irradiées : Tchernobyl et Fukushima

Deux zones d'exclusion, données par leur latitude et leur longitude (`data/dohy/earth/sites.json`,
écrit par `tools/earth_deposits.py`) : elles valent pour toute carte calée sur le globe.

| Zone | Rayon | Cœur | Radiation (NTM NEXT) |
|---|---|---|---|
| Tchernobyl | 40 blocs | 12 blocs | 0,5 RAD/s dans la zone, 4 RAD/s au cœur ; ×4 et ×2 sous la pluie |
| Fukushima | 28 blocs | 8 blocs | idem |

- **La radiation est celle de NTM NEXT**, pas du code de DoHy : à leur arrivée, les chunks de la
  zone prennent deux biomes à DoHy (`dohy:exclusion_zone`, `dohy:exclusion_core` : herbe jaunie,
  « forêt rousse » au cœur), et NTM NEXT irradie ces biomes par ses propres données
  (`data/dohy/hbm/biome_radiation/`). Ses combinaisons, son RadAway et son compteur Geiger
  marchent donc comme partout. 4 RAD/s au cœur, c'est la mort en un peu plus de quatre minutes
  sans protection ; la zone, une demi-heure. Les biomes ne changent que sur les terres : la mer
  garde le sien.
- **Au cœur**, la terre, les arbres, les feuilles et les plantes deviennent ceux, morts, de NTM
  NEXT. **Au site du réacteur**, une enceinte de béton éventrée autour d'un tas de corium et de
  gravats de corium (les blocs de NTM NEXT : les tenir en main irradie), et une étiquette.
- **Les minerais rares** : ceux d'un cratère de Sellafield, qu'on ne trouve nulle part ailleurs sur
  la Terre — schrabidium, radgem, uranium brûlé, diamant et émeraude de Sellafield (gisements à
  champ `sites`).
- **Un message** à l'entrée : « Tchernobyl — Zone d'exclusion : radiation, combinaison
  conseillée ».
- Sans NTM NEXT, la zone n'est qu'un décor (les biomes et le message) : ni radiation, ni corium,
  ni minerais de Sellafield.

### Les bases de lancement

Baïkonour, Cap Canaveral, Kourou (Centre spatial guyanais) et Jiuquan, 48 blocs de rayon chacune.

- **Les pas de tir des fusées ne se posent que là** : pas de tir de fusée, fusée de transport,
  lanceur de station orbitale et lanceur Dyson de NTM NEXT: Space (`launch_blocks`). Ailleurs sur
  la carte, la pose est refusée — l'objet reste au joueur — avec la base la plus proche, sa
  distance et sa direction. Sur la Lune ou Mars, rien ne change.
- Au centre de chaque base, une **aire de lancement** : un chunk à plat, en béton, cerclé de jaune,
  dégagé au-dessus, et le nom de la base. On s'y installe : il faut y construire l'usine à fusées.
- Un message à l'entrée : « Baïkonour — Base de lancement : les fusées décollent d'ici ».

**Les centres.** Une carte de la Terre met 1,6 km dans un bloc : un chunk fait 25 km, et la
centrale de Tchernobyl tombe dans le chunk de la Pripiat, Cap Canaveral et Fukushima dans la mer.
Chaque lieu est donc centré sur le chunk sec le plus proche, dans le bon pays, sur la carte
Earth24k (37 km au plus du lieu réel). Les zones sont à l'échelle du jeu : celle de Tchernobyl
réelle ferait 19 blocs de rayon.

### Les pays à prendre

L'esprit de NationsGlory sur une partie solo : ta faction prend des pays entiers de la grille, un
par un, aux **nations rivales** que le jeu fait vivre.

- **Les nations rivales** sont les douze factions fictives des events (Les Corbeaux, Nordheim,
  Arcanum…). Elles se partagent le monde **par régions** : chaque pays est rattaché à la nation
  la plus proche de son cœur, après un regroupement des pays sur le globe (k-moyennes, chaque pays
  pesant la racine de sa taille), tiré de la graine du monde. Sur la carte Earth24k et la graine de
  l'essai : Arcanum tient l'Europe et la Scandinavie (49 pays), Kraken le Canada et les
  États-Unis, Nordheim la Russie et l'Asie centrale, Basilic la Chine, l'Inde et le Japon… Rien de
  ce partage n'est sauvegardé : seuls les pays pris par des joueurs le sont.
- **La capitale.** Dans un de tes claims, sur la carte, `/f pays capitale` (ou la carte de
  l'écran). Son pays devient ta **première cible**. Tant que tu ne tiens aucun pays, elle se
  déplace où tu veux ; ensuite, seulement dans un pays que tu tiens. Elle est marquée en rouge sur
  la carte du monde du spawn.
- **Viser un pays** (`/f pays viser`, ici ou par son nom, ou l'écran) : d'abord celui de ta
  capitale ; ensuite un **voisin** de tes terres — un pays dont la terre est à 300 blocs environ
  d'un pays que tu tiens. La France tient l'outre-mer : la tenir ouvre aussi les Antilles, la
  Guyane, l'océan Indien. Une île sans voisin (les Bermudes, Sainte-Hélène…) se vise de partout.
  Un pays à la fois ; changer de cible ne perd rien.
- **Le prendre, aux events de faction.** Au prochain event (Boss, Egg Hunt, Totem, KOTH), la
  nation qui tient ta cible est **de la partie**, parmi les trois factions rivales. Si tu finis
  **devant elle**, l'ELO que tu gagnes à cet event compte en **points de conquête** sur ce pays.
  Derrière elle, rien ne bouge. Le pays tombe quand ses points sont réunis : titre, annonce dans
  le chat, et il passe à ta faction.
- **Le prix d'un pays** dépend de sa terre sur la grille : de 200 points pour le Vatican à 3 200
  pour la France et 22 000 pour la Russie (chiffrage : `docs/BALANCE.md` § 6 novodecies).
- **Ce que donne un pays** : **un claim de plus** par pays tenu, et dans la notation de la semaine,
  chaque pays compte pour quatre claims du territoire. Les pays tenus sont **en or** sur la carte
  du monde du spawn.
- **Les frontières** disent qui tient le pays : « France — Aux mains d'Arcanum · ta cible (227 /
  3 200) », « France — Aux mains de ta faction ». Sur la carte du spawn, la barre d'action aussi.
- **Rendre un pays** : `/f pays abandonner <pays>`, ou deux clics dans l'écran ; il retourne à la
  nation de sa région, et la capitale part avec s'il la portait. Dissoudre la faction rend tous
  ses pays.
- **Entre joueurs** (sur un serveur) : un pays tenu par la faction d'un joueur ne se vise pas — pas
  de guerre entre joueurs, comme pour les raids.
- **Écran** : carte « Pays » sur la page de faction (avec une carte du monde de la Terre) : ta
  nation (capitale, pays tenus, cible et ses points), le pays sous tes pieds, les douze voisins les
  plus proches avec leur prix, et ce que tient chaque nation rivale. Commandes : `/f pays`,
  `capitale`, `viser [pays]`, `renoncer`, `abandonner <pays>`, `liste` ; pour les opérateurs,
  `donner <pays>`.

### Les capitales

Ta faction pose **sa** capitale dans un de ses claims (voir plus haut) ; elle est en rouge sur la
carte du monde du spawn. Les **villes des capitales réelles** (215 villes agrandies, onze sur le
plan de la vraie ville, ajoutées le 2026-11-13) ont été **retirées le jour même**, à la demande du
joueur : plus aucune ne se construit. Celles déjà posées dans un monde y restent telles quelles,
avec leurs hologrammes ; leurs deux marques de sauvegarde (`earth_city`, `earth_cities`) sont lues
sans erreur puis effacées à la sauvegarde suivante.

### L'atlas (2026-11-13)

Une carte faite maison, à la place de Xaero ou de JourneyMap (`client/atlas/`), dans le style des
écrans du kit de PalaMod (panneaux, boutons, polices et croix du kit de DoHy).

- **Minimap** en haut à gauche : le nord en haut, ta flèche au milieu, tes coordonnées et le pays
  dessous (en or s'il est à ta faction). **N** l'affiche ou la cache, **=** et **-** zooment
  (de 4 pixels par bloc à 16 blocs par pixel). Quatre tailles (56, 72, 96 ou 112 pixels
  d'interface de côté, **72** par défaut), au bouton TAILLE de la grande carte.
- **Grande carte** (**M**) : glisser pour se déplacer, la molette pour le zoom (de 8 pixels par
  bloc à 128 blocs par pixel), **C** pour revenir sur soi ; la barre du bas dit les coordonnées,
  le pays et qui le tient, le gisement ou le point sous la souris. En haut, les calques : pays
  tenus, gisements, points, points en jeu, minimap.
- **Le terrain exploré** : chaque chunk reçu est relevé (la couleur de carte du bloc du dessus,
  éclaircie ou assombrie selon la pente, l'eau plus sombre quand elle est profonde ; sous un
  plafond comme le Nether, le premier sol sous la place libre), quelques chunks par tick du plus
  proche au plus loin, ceux d'autour du joueur relus toutes les 5 s. Gardé sur le disque dans
  `dohy_atlas/<monde>/<dimension>/`, en dalles de 256 blocs (un pixel par bloc) et en dalles
  d'ensemble de 4 096 blocs (un pixel pour 16) pour voir loin.
- **Sur la carte de la Terre**, le serveur envoie ce que le client ne peut pas voir : le **globe**
  (la grille des pays, visible d'avance sous le terrain exploré : mers, pays, frontières), les
  **pays tenus** (ta faction en or, chaque nation rivale à sa couleur, ta cible en jaune), les
  **245 gisements géants** et les lieux (une case à la couleur de la ressource), et ta
  **capitale** (case rouge).
- **Points de passage** : clic droit sur la grande carte pour en poser un (nom, couleur parmi
  huit), clic sur un point pour le modifier ou le supprimer ; le **point de mort** est posé tout
  seul à chaque mort (un seul, remplacé). Dans le monde, chaque point de la dimension s'affiche à
  sa place à l'écran avec sa distance (jusqu'à 6 000 blocs). Gardés dans
  `dohy_atlas/<monde>/points.json`.
- Si Xaero's World Map reste installé, sa carte prend aussi **M** : changer une des deux touches.

### La faune (2026-11-13)

Le jeu pose les animaux paisibles surtout à la génération des chunks ; ensuite, il n'en ajoute que
toutes les 20 secondes, jamais plus d'une dizaine autour du joueur, et ceux qu'on chasse ne
reviennent guère. Sur la carte de la Terre, faite d'avance, des régions entières restaient vides.
`world/Wildlife` (tout l'overworld, carte de la Terre ou non) :

- Toutes les 20 secondes (décalé de 10 s du tirage du jeu), pour chaque joueur : s'il y a moins
  de **24 animaux à 96 blocs**, **deux groupes** au plus arrivent à 32-80 blocs, jamais à moins
  de 24 blocs d'un joueur, dans un chunk déjà chargé.
- L'espèce est tirée dans la liste du biome, comme le fait le jeu : les animaux des mods compris
  (les pigeons de NTM NEXT dans les plaines et les savanes), sauf le **Dancarok**, qui garde son
  propre tirage (un sauvage par 128 blocs). Chaque bête passe la règle d'apparition de son espèce
  (le bon sol, la lumière, la place) : pas de mouton dans le désert. Groupes de quatre au plus.
- La règle `spawn_mobs` l'arrête. Les animaux ne disparaissent pas : chaque coin où l'on passe
  garde sa vingtaine de bêtes.

### Les saisons (2026-11-13)

Des saisons selon la latitude, dans l'esprit de Serene Seasons (qui, lui, donne la même saison
au monde entier). Choix du joueur : **une saison par semaine réelle**, neige **réaliste**,
cultures, couleurs et atlas.

- **Le calendrier** : l'année dure quatre semaines ; chaque saison s'ouvre le **lundi à 5 h**
  (heure de Paris, comme les contrats). Le 16 novembre 2026 ouvre l'hiver du nord. Le **sud** vit
  la même année à contre-temps : son hiver est l'été du nord. Sous les **tropiques** (moins de
  23,44°) pas d'hiver, mais une saison sèche et une saison des pluies (un nom, sans effet : la
  pluie du jeu est la même partout). Le temps est celui de l'horloge réelle : les saisons
  avancent serveur éteint.
- **Le froid** : la saison s'ajoute à la température des biomes. Sa force monte des tropiques
  (rien) à 65° (toute) ; le plein hiver retire 0,75, le plein été ajoute trois fois moins. Le jeu
  fait le reste avec ses propres règles : la pluie devient neige (à l'écran aussi), l'eau gèle,
  la neige tient. **Réglage réaliste**, vérifié par `tools/SeasonMathTest.java` : en plaine et en
  forêt, pas de neige à Paris ; un peu à Berlin au cœur de l'hiver ; franchement à Moscou,
  Stockholm, Ushuaia ; la taïga et la montagne blanchissent plus bas (le jeu refroidit déjà
  l'air avec l'altitude) ; la toundra dégèle l'été, les glaciers tiennent.
- **La fonte, même sans joueur** : le jeu ne fait jamais fondre la neige au redoux, et ne touche
  qu'aux chunks proches du joueur. `SeasonGround` le fait :
  - **le balayage** : à chaque tick, 48 chunks chargés, deux colonnes au hasard dans chacun ;
    neige et glace fondent où il fait doux (température ≥ 0,18, un peu au-dessus de la limite de
    la neige pour qu'un bloc ne gèle et ne fonde pas tour à tour). Tous les chunks chargés y
    passent, ceux du spawn et les chunks forcés compris : la fonte continue serveur sans joueur
    (pause du serveur vide désactivée) ;
  - **le rattrapage** : un chunk qui revient en mémoire est remis à la saison avant d'être vu —
    toute sa neige fond s'il fait doux ; s'il fait froid, il reçoit la couche de neige et la
    glace de rive que l'hiver lui aurait données. Une fois par palier (64 par année, ~10 h).
  - Un chunk que personne ne charge ne peut pas être modifié : c'est le rattrapage qui le remet
    d'aplomb à son retour. Ne fondent que les couches de neige et la glace simple de surface ;
    jamais un bloc posé par un joueur, ni un bloc de neige, ni la glace compacte ou bleue.
- **Les cultures** : à l'abri (sous un toit, une verrière : pas de vue sur le ciel), tout pousse.
  Dehors, rien ne pousse s'il gèle ; sinon la part des pousses baisse avec l'hiver, jusqu'à une
  sur cinq au cœur de l'hiver au-delà de 65°. Tropiques et été : tout pousse. La poudre d'os
  n'est pas freinée. Toutes les cultures qui passent par l'événement de pousse de NeoForge sont
  concernées (céréales DoHy, PalaMod, blé, canne, baies...).
- **Les couleurs** (client, accentuées à la demande du joueur) : en automne le feuillage rougit
  franchement, et pas d'une seule couleur — rouge, orange, jaune ou roux selon le bosquet (une
  tache par 4 blocs, à peu près un arbre) — et l'herbe tourne à la paille ; en hiver feuillage
  brun gris et herbe sèche ; au printemps un vert tendre et vif ; l'été, les couleurs du jeu.
  Force entière dès 45° (la France comprise), nulle sous les tropiques. Sapins et bouleaux gardent
  leur couleur fixe. Les couleurs avancent par paliers (~10 h) : à chaque palier, les chunks
  affichés se reconstruisent, l'ancien rendu restant affiché jusqu'au nouveau.
- **L'atlas** : sous le pays, la saison, le jour de la semaine de saison et la température du
  lieu — « Hiver  j3/7  -4 °C ». **`/saison`** donne le détail et le temps avant la suivante.
  `/saison admin avancer <jours>` / `remettre` décale l'horloge pour essayer (op, jamais
  sauvegardé).
- **Code** : `season/SeasonMath` (les chiffres, sans Minecraft, testés seuls en CI),
  `SeasonClimate` (table de décalage par bande de 16 blocs, serveur et client), `Seasons`
  (calendrier, envoi `SeasonSync`, commande), `SeasonGround`, `SeasonCrops` ;
  `mixin/BiomeTemperatureMixin` (la saison dans `Biome.getTemperature`), `BiomeAccessor` ;
  `client/season/ClientSeasons`, `SeasonColors`, `mixin/BiomeColorsMixin` (client).

### Les cartes WorldPainter en 26.2

Une carte dont les chunks attendent leur décoration est enregistrée à l'étape `liquid_carvers`.
Cette étape n'existe plus : en 26.2 elle se lit `empty`, et le jeu repasse sa propre génération
par-dessus le chunk dessiné (essayé sur un chunk copié à cette étape : son biome change et la
moitié de ses blocs aussi). `mixin/SerializableChunkDataMixin` renomme l'étape en `carvers` dans
les données du chunk avant leur lecture : le jeu reprend à la décoration.

### Code

- `earth/` : `EarthData` (calage, grille, pays), `EarthEvents` (chargement avant les mondes,
  frontières, lieux, pose des pas de tir, file des gisements), `EarthJump` (le saut),
  `EarthDeposits` (la table, la file des chunks, les filons), `EarthBodies` (le nettoyage, les gisements géants, le pétrole de Create Diesel Generators), `EarthBodyCommand` (`/gisements`), `EarthSites` (zones irradiées et bases de lancement),
  `EarthNations` (les pays à prendre : partage du monde, voisins, capitale, conquête),
  `NationMenu` et `NationCommand` (l'onglet et `/f pays`), `DoHyEarth` (la marque des chunks, les
  pays pris sur le monde de la carte, la nation du joueur).
- `faction/event/EventRun` : la nation qui tient un pays visé est mise parmi les rivaux de
  l'event, et la fin de l'event compte les points de conquête.
- `spawn/SpawnBuilder.worldMap` : la dalle et le puits ; le spawn est redessiné quand une carte de
  la Terre apparaît ou disparaît (sa version vaut alors celle du dessin plus mille).
  `SpawnApi.paintNations` repeint les pixels des pays pris et des capitales.

## Le spawn (dimension `dohy:spawn`)

Un lieu à part, comme le spawn des serveurs dont le mod s'inspire : on y vient ouvrir ses box et
passer aux guichets. Thème **brutaliste et industriel** : béton brut, acier, cheminées, tuyaux.

### Y aller, en revenir

- **`/spawn`**, ou la carte « Spawn » du menu : on arrive sur la plateforme ronde. La même commande,
  ou une borne « RETOUR », ramène d'où l'on venait, au bloc près.
- On ne le rejoint que depuis l'Overworld, le Nether ou l'End (pas depuis un donjon ou une arène).
- La première visite **construit** le lieu : quelques secondes, pendant lesquelles on attend où
  l'on est ; on y est envoyé dès que c'est fini. Les visites suivantes sont immédiates.

### Le lieu

- Un **massif rocheux flottant** au-dessus des nuages, entouré de treize pitons ; des mâts
  d'éclairage sur certains.
- La **plateforme d'arrivée**, ronde, sur son propre piton, reliée au parvis par un **pont** à
  portiques d'acier.
- Le **parvis**, un plateau ovale de terre et d'herbe tenu par un mur de pierre : une allée dallée
  du pont au porche, des épicéas, des lampadaires.
- Un seul **bâtiment**, brutaliste, en pierre, posé sur un **socle planté** en deux marches courbes :
  trois **gradins** aux bouts arrondis, en retrait l'un sur l'autre, cernés de bandeaux vitrés et de
  **terrasses** plantées ; au-dessus, une **masse haute** épaulée de contreforts en pente ; à l'est,
  un **anneau** en porte-à-faux sur pilotis, un épicéa en son centre ; à l'ouest, un **pylône** à
  pilastres relié aux gradins par trois passerelles superposées, qui butent sur une dalle debout
  portant le signe « D ». Une échelle monte dans le pylône jusqu'à son belvédère.
- Dedans, de la pierre et du bois : murs carrelés, plafonds à caissons, mains courantes de bois,
  lanternes. Une **nef** basse, ses colonnes et leurs plantes en pot, puis l'**atrium** — un puits
  de 46 blocs sous une verrière, une lame de lumière sur toute sa hauteur entre deux **murs verts**.
  Son sol est de brique ; au centre, un **jardin** (un massif, quatre jardinières, deux bancs). Les
  **six box** sont dans des niches à leur couleur (améthyste, saharia, paladium à l'ouest ;
  halloween, trixium, endium à l'est) : un clic avec sa clé ouvre une box, comme chez soi.
- De part et d'autre de l'atrium, deux **travées de bornes** : COMMERCE à l'est (shop, boutique,
  marché), CARRIÈRE à l'ouest (banque, contrats, musée). Une borne ouvre le même écran que le menu.
  La borne RETOUR est à côté de l'entrée (et sur la plateforme d'arrivée).
- Deux **étages de galeries** regardent l'atrium, de la verdure retombe de leurs garde-corps ; des
  escaliers de pierre taillée, entre deux limons massifs, y montent depuis chaque travée. Chaque galerie ouvre sur sa terrasse, sur l'anneau et sur les passerelles.
- Derrière le bâtiment, deux **cheminées** qui fument, des cuves et leurs tuyaux.
- Le ciel et l'heure sont ceux de l'Overworld ; les nuages passent sous le parvis.

### Les règles

- On n'y casse rien et on n'y pose rien (sauf en créatif), les explosions n'abîment rien.
- On n'y prend aucun dégât. Qui tombe dans le vide est reposé sur la plateforme d'arrivée.
- Aucune créature n'y apparaît.

### Code

- `spawn/DoHySpawn` (dimension, borne, étiquette, pièces jointes), `SpawnApi` (aller, revenir,
  construire par tranches de 5 000 blocs par tick), `SpawnBuilder` (le dessin : tout est calculé,
  aucun fichier de structure ; `VERSION` à augmenter pour faire reconstruire — un spawn d'une autre
  version est d'abord vidé, pitons compris, puis rebâti à la visite suivante), `SpawnEvents`
  (règles, `/spawn`), `SpawnTerminalBlock` (`dohy:terminal`, une propriété `kind` par service),
  `HologramBlock` (`dohy:hologram`, les textes flottants).
- `data/dohy/dimension/spawn.json`, `dimension_type/spawn.json`, `worldgen/biome/spawn.json`,
  écrits par `tools/dohy_assets.py`.

## Sur un serveur dédié (2026-11-13)

Le joueur joue désormais sur un serveur qui tourne 24 h/24. Le mod y fonctionne tel quel : les
vérifications en jeu de la CI tournent déjà sur un serveur dédié (le client de capture s'y
connecte), et aucun code client n'est appelé côté serveur.

### Installer

- **Le jar** dans `mods/` du serveur **et** de chaque client : le mod a des blocs, des écrans et
  des paquets des deux côtés. Les mods purement client (shaders, etc.) restent côté client.
- **Le datapack de la carte** (`dohy_terre.zip`) dans `world/datapacks/` du serveur (le dossier
  du monde est `level-name` dans `server.properties`).
- **Les sauvegardes de secours de l'économie** s'écrivent dans `world/dohy/` : trois copies
  glissantes, toutes les cinq minutes tant qu'un joueur est connecté, et à l'arrêt.

### L'heure

Toutes les heures du mod — la bascule de 5 h des contrats, midi pour la commande trixium, le
lundi minuit du classement, les events de faction, les saisons du trixium, les dates des menus —
sont à **l'heure de Paris**, quelle que soit la machine : un hébergeur tourne souvent en UTC, et
l'event de 21 h y tomberait à 22 h ou 23 h. Un serveur ailleurs la change au lancement, par une
option Java (dans `user_jvm_args.txt`) : `-Ddohy.fuseau=America/Montreal` (un nom de fuseau
IANA), ou `-Ddohy.fuseau=systeme` pour l'heure de la machine. Au démarrage, le journal dit
l'heure retenue : `Horloge DoHy : Europe/Paris (13/11 17:42), machine en UTC`.

### La pause d'un serveur vide

Par défaut, un serveur se met en pause une minute après le départ du dernier joueur
(`pause-when-empty-seconds=60` dans `server.properties`) ; `-1` la désactive. Pour DoHy, ça
change peu :

- ce qui doit avancer pendant l'absence suit déjà l'horloge réelle, pause ou pas : contrats,
  cours du marché, intérêts d'épargne (rattrapés à la connexion), saison du trixium, classement
  du lundi ;
- les machines d'une base ne tournent pas sans joueur, pause ou pas : ses chunks se déchargent.
  DoHy ne force le chargement que des donjons, de l'arène d'event et des bases ennemies en
  reconstruction, le temps de l'action ;
- sans pause, le temps de jeu continue : jour et nuit, météo, et les minuteries comptées en
  ticks (la protection du Home Remover, l'amélioration du Wither) s'écoulent pendant l'absence.

### Ce qui reste pensé pour un joueur

La faction n'a qu'un membre, les rivaux et les bases ennemies sont fictifs, les tableaux de
l'atrium montrent le premier joueur présent au spawn, et le boss d'event a ses points de vie du
solo. Chaque joueur a sa propre économie, sa faction et ses contrats : un ami qui rejoint le
serveur joue sans gêner personne, mais sans vraie interaction (faction commune, pillage entre
joueurs) — ce serait un lot à part.

### Code

- `util/RealClock` : `zone()`, le fuseau de tout le mod (option `dohy.fuseau`, Paris par
  défaut). Il ne dépend que de `java.time` : la CI le compile seul et le teste
  (`tools/RealClockTest.java`, fuseau compris). `FactionWeekly.zone()`, `FactionEvents`,
  `TrixiumSeason`, `NationMenu` et `GuardianStoneItem` passent par lui.
- `util/ClockReport` : la ligne du journal au démarrage, et l'avertissement si l'option
  `dohy.fuseau` n'est pas comprise.
