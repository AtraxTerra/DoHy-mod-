# PalaMod — ce que dit le code

Référence extraite **au bytecode** de `PalaMod-10.3.0.pala` et `PalaJobs-8.0.0.pala`
(mods Forge 1.7.10, classes non obfusquées, lues avec `javap -c`).

Ce fichier existe parce que le wiki Paladium est incomplet et par endroits faux. Quand les
deux sources divergent, **le code fait foi** et l'écart est noté ici. Les chapitres de
conversion s'appuient sur ce document, pas sur le wiki.

| Source | Contenu utile |
|---|---|
| `PalaMod-10.3.0.pala` | 4 275 classes, 5 175 PNG — machines, cultures, minerais, verrous de métier |
| `PalaJobs-8.0.0.pala` | 811 classes — table d'XP, types d'objectifs et de quêtes |
| `updated_faithfull_paladium_officiel.zip` | 2 721 PNG, namespaces `palamod` / `palajobs` / `palagamemod` |

Outils : `tools/extract_jobs.py` (lit les tables de métier au bytecode) puis
`tools/palamod_jobs.py` (produit `data/jobs.json`).

---

## 1. Échelle des niveaux — le point qui conditionne tout

PalaMod et PalaJobs 8.0.0 comptent **20 niveaux par métier**. Les skills de DoHy en
comptent **100**. Tous les niveaux relevés dans le code sont donc **multipliés par 5**.

Le facteur est validé deux fois, contre le wiki Java qui est déjà gradué sur 100 :

| Objet | Niveau dans le code | ×5 | Niveau annoncé par le wiki Java |
|---|---|---|---|
| Cobblebreaker | 5 | **25** | 25 ✔ |
| Crusher | 8 | **40** | 40 ✔ |

Deux correspondances exactes sur deux vérifications indépendantes : la conversion est bonne.

**Écart relevé** : le wiki donne le totem de fertilité au niveau 55 ; le code le place au 14,
soit 70. Le code gagne.

### Table d'XP de PalaJobs — conservée pour mémoire, non utilisée

`JobExpUtils.LEVELS` porte le coût de chaque niveau (XP pour passer du niveau *n−1* au
niveau *n*) :

| Niveau | XP | Niveau | XP |
|---|---|---|---|
| 2 | 22 123 | 12 | 821 062 |
| 3 | 40 390 | 13 | 982 632 |
| 4 | 73 751 | 14 | 1 160 743 |
| 5 | 118 886 | 15 | 1 355 741 |
| 6 | 176 611 | 16 | 1 567 953 |
| 7 | 247 616 | 17 | 1 797 690 |
| 8 | 332 507 | 18 | 2 045 248 |
| 9 | 431 826 | 19 | 2 310 912 |
| 10 | 546 062 | 20 | 2 594 953 |
| 11 | 675 669 | | |

Au-delà du niveau 20, le coût reste constant à 2 594 953 (`getNeededXpForLvl` retombe sur la
dernière entrée) et `getLevel` ne plafonne pas. Cette table **n'est pas** celle du mod : la
fusion métier/skill fait que c'est la courbe de `skills.json` (`100 × niveau^1,6`) qui
s'applique. Elle est ici pour qu'on sache ce qu'on a écarté, et pourquoi.

### Les quatre métiers

`JobType` : `MINER`, `HUNTER`, `FARMER`, `ALCHEMIST`. Le wiki Bedrock annonce trois métiers ;
le code en déclare quatre. Mappés sur les skills par `Job` : `mining`, `combat`, `farming`,
`alchemy`.

### Types d'objectifs et de quêtes

`ObjectiveType` (17) : `BREAK_BLOCK`, `SMELT`, `CRUSHER_GENERATE`, `COBBLE_BREAKER`, `CRAFT`,
`DROP_CAULDRON`, `EXTRACT_SEVE`, `CRAFT_CAULDRON`, `CRAFT_PORTAL`, `WITHER_SPAWN`, `FISH`,
`ENTITY_KILL`, `ENTITY_KILL_SPECIAL`, `ARROW_KILL`, `XP_BOTTLER`, `QUEST`, `WARZONE_EVENT`.

`QuestType` (10) : `ITEM_USE`, `ACTION`, `BLOCK_BREAK`, `BLOCK_PLACE`, `ITEM_GIVE`,
`ITEM_CRAFT`, `FISHING`, `ENTITY_KILL`, `ENTITY_FEED`, `FURNACE_CRAFT`.

`MultiplierType` : `QUEST`, `RANK`, `DOUBLE_XP`. `RewardType` : `GIVE`, `MONEY_GIVE`,
`CRAFT`, `USAGE`.

---

## 2. Verrous de métier

PalaMod déclare deux tables par métier : `addBlacklistedCraft(niveau, item)` et
`addBlacklistedUsage(niveau, item)`. Au total **72 crafts** et **53 usages** verrouillés.
Les niveaux concordent entre les deux tables, à quelques entrées près.

Le mod ne reproduit que l'**usage et la pose**, pas le craft :
`PlayerEvent.ItemCraftedEvent` n'implémente pas `ICancellableEvent` en 26.2 (vérifié à la
sonde), et tous les contournements détruisent les ingrédients déjà consommés — ce qui viole
le principe 4 de `DESIGN.md`. Voir `JobGateEvents` pour le raisonnement complet.

La table convertie vit dans `data/jobs.json` (70 entrées après fusion et retrait des
niveaux 0 et 1, qui ne verrouillent rien).

---

## 3. Cobblebreaker

`CobbleBreakerUpgrade` — un cycle de traitement, en ticks :

| Palier | `work` (ticks) | Durée | Multiplicateur | Niveau code | Niveau DoHy |
|---|---|---|---|---|---|
| Défaut | 400 | 20,0 s | ×1 | — | 25 (la machine) |
| Améthyste | 266 | 13,3 s | ×1,50 | 10 | 50 |
| Titane | 160 | 8,0 s | ×2,50 | 13 | 65 |
| Paladium | 100 | 5,0 s | ×4,00 | 16 | 80 |

Les multiplicateurs annoncés par le wiki (×1,5 / ×2,5 / ×4) sont **exactement** ceux que
donnent les ticks. Le wiki ne donnait pas la cadence de base : c'est 400 ticks, soit 20 s.

Sorties : six particules, de la plus courante à la plus rare — fer, or, diamant, améthyste,
titane, paladium. **Neuf particules d'un même minerai donnent un lingot.** Les textures
existent (`palamod/textures/items/particle/particle_*.png`), les noms de langue aussi
(`item.particle.iron.name` … et un `item.particle.green.paladium.name`, soit un septième
type non documenté par le wiki).

> **À extraire encore** : les probabilités par particule, dans `TileCobbleBreaker`.

---

## 4. Crusher

`TileCrusher$EnumCrusherRecipes` et `$EnumCrusherResult`. Le wiki est ici exact de bout en
bout, le code le confirme :

| Fruit | Charbon mixé consommé | Fruits par jauge pleine | Sortie |
|---|---|---|---|
| Eggplant | or (`GOLDMIXEDCOAL`) | 32 | 5 lingots d'améthyste |
| Chervil | améthyste (`AMETHYSTMIXEDCOAL`) | 32 | 15 lingots de titane |
| Kiwano | titane (`TITANEMIXEDCOAL`) | 64 | 15 lingots de paladium |
| Orangeblue | paladium (`PALAMIXEDCOAL`) | 128 | 1 pépite d'endium |

Un fruit fondu consomme un charbon ; chaque type de charbon se stocke jusqu'à 64. Rendements
de craft des charbons, d'après le wiki : or ×6, améthyste ×8, titane ×8, paladium ×1.

Verrou : **Fermier 8 → niveau 40**.

---

## 5. Totem de fertilité

`TileEntityTotem` :

| Donnée | Valeur |
|---|---|
| `DEFAULT_MAX_BOOSTED_BLOCKS` | **2 048** blocs accélérés par lingot de paladium |
| Zone | boucle de −7 à +8 sur X et Z, soit **15 × 15** |
| Période du tick | 10 ticks |
| Stock de lingots | 64 |
| Verrou | Fermier 14 → niveau **70** (le wiki dit 55 — faux) |

Alimentation : lingot de paladium en main, clic droit sur le totem (un lingot par clic).
Clic droit à main vide : consulter le contenu. Maj + clic droit : afficher la zone en vert.
Cassé, il rend les lingots restants.

---

## 6. Cultures

`BaseBlockCrops` reprend la mécanique de croissance **vanilla** : lumière ≥ 9, tirage
`random.nextInt(25 / vitesse + 1)`, avec les mêmes facteurs de voisinage (3,0 et 4,0) que le
blé. Elle se transpose donc directement sur `CropBlock` en 26.2, sans rien réinventer.

Probabilités de drop lues dans `getDrops`, sur une base de 10 000 : **800** (8 %), **500**
(5 %), **200** (2 %) et **25** (0,25 %).

| Culture | Stades (textures) | Verrou Fermier (code → DoHy) |
|---|---|---|
| Eggplant | 4 (`_stage_0` à `_3`) | 7 → **35** |
| Chervil | 4 | 12 → **60** |
| Kiwano | 5 (`_stage_0` à `_4`) | 16 → **80** |
| Orangeblue | 5 | 20 → **100** |

Chaque culture a trois assets : le bloc (`blocks/crops/<nom>_stage_N.png`), la graine
(`items/seeds/<nom>.png`) et le fruit (`items/fruits/<nom>.png`).

### Seedplanters

Les noms de champ `SEEDPLANTER_LVL1..5` **ne suivent pas** l'ordre des paliers : `LVL4` est
l'endium et `LVL5` le paladium vert, or le paladium vert vient avant l'endium. Les niveaux
18 puis 20 semblent donc inversés alors qu'ils sont justes. `data/jobs.json` les nomme par
matériau (`green_paladium_seedplanter`, `endium_seedplanter`) pour que l'ordre se lise seul.

| Seedplanter | Niveau code | Niveau DoHy |
|---|---|---|
| Améthyste | 3 | 15 |
| Titane | 6 | 30 |
| Paladium | 15 | 75 |
| Paladium vert | 18 | 90 |
| Endium | 20 | 100 |

---

## 7. Grinder — multiblock, pas un bloc

Le wiki le décrit comme un assemblage de 30 blocs plus une source de lave, placement strict,
sur trois couches. Les sous-composants existent dans le code (`grinder block` 595,
`grinder casing` 596, `grinder frame` 597) et trois textures sont dans le pack
(`blocks/smeltery/grinder_block_front`, `_side`, `grinder_casing_block`,
`grinder_frame_block`).

**Manquent au pack** : `grinder_table` et `grinder_robinia`. Le wiki se contredit lui-même
sur les totaux (30 blocs listés contre 26 annoncés ; 134 paladium calculés contre 132
annoncés). À reprendre dans le code avant de l'implémenter.

---

## 8. Assets disponibles, et les trous

Namespace `palamod` du pack Faithful officiel — c'est pour cela que le mod enregistre sous
ce namespace : le pack s'applique alors tel quel, sans conversion ni copie.

Présent : cobblebreaker 4 paliers (front/side/top), palafurnace (on/off/side), 4 cultures ×
4-5 stades, 4 graines, 4 fruits, 6 particules, 4 charbons mixés, 5 seedplanters, minerais
(améthyste, findium, paladium, paladium vert, titane, trixium), lingots, compressés, anneaux,
sticks, 26 blocs d'alchimiste, 6 blocs de spawner, 5 GUI de coffre.

**Trous à combler** : aucune texture de bloc pour le **crusher**, la **palamachine** et le
**totem** (seul `items/totem.png` existe), ni pour `grinder_table` / `grinder_robinia`.

Attention à l'arborescence : le pack est en 1.7.10 (`textures/blocks/`, `textures/items/`).
En 26.2 il faut `textures/block/` et `textures/item/` — conversion mécanique à faire au
chapitre 2.
