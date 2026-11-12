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
- `tools/dohy_assets.py` dessine tout ; `tools/check_agri_prices.py` (CI) vérifie qu'aucune
  recette ne devient rentable en achetant ses ingrédients au cours le plus bas.

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
- La **plateforme d'arrivée**, ronde, sur son propre piton, reliée à la place par un **pont** à
  portiques d'acier.
- La **place**, une dalle de béton à pans coupés, avec ses lampes de sol et la borne du retour.
- Le **hall des box** : six travées entre des voiles de béton, sous un toit en porte-à-faux fendu
  de puits de jour, adossé à une **tour** en gradins. Une box par travée (améthyste, paladium,
  endium, saharia, trixium, halloween) : un clic avec sa clé l'ouvre, comme une box posée chez soi.
- Deux **pavillons de bornes** : COMMERCE (shop, boutique, marché) et CARRIÈRE (banque, contrats,
  musée). Une borne ouvre le même écran que le menu.
- Deux **cheminées** qui fument, des cuves et leurs tuyaux.
- Le ciel et l'heure sont ceux de l'Overworld ; les nuages passent sous la place.

### Les règles

- On n'y casse rien et on n'y pose rien (sauf en créatif), les explosions n'abîment rien.
- On n'y prend aucun dégât. Qui tombe dans le vide est reposé sur la plateforme d'arrivée.
- Aucune créature n'y apparaît.

### Code

- `spawn/DoHySpawn` (dimension, borne, étiquette, pièces jointes), `SpawnApi` (aller, revenir,
  construire par tranches de 5 000 blocs par tick), `SpawnBuilder` (le dessin : tout est calculé,
  aucun fichier de structure ; `VERSION` à augmenter pour faire reconstruire), `SpawnEvents`
  (règles, `/spawn`), `SpawnTerminalBlock` (`dohy:terminal`, une propriété `kind` par service),
  `HologramBlock` (`dohy:hologram`, les textes flottants).
- `data/dohy/dimension/spawn.json`, `dimension_type/spawn.json`, `worldgen/biome/spawn.json`,
  écrits par `tools/dohy_assets.py`.
