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

PalaMod et PalaJobs 8.0.0 comptent **20 niveaux par métier**. Les métiers de DoHy en
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
dernière entrée) et `getLevel` ne plafonne pas. Cette table **n'est pas** celle du mod :
c'est la courbe de `job_progression.json` (`100 × niveau^1,6`) qui s'applique, sur 100
niveaux. Elle est ici pour qu'on sache ce qu'on a écarté, et pourquoi.

Elle sert quand même une fois : à **convertir une quantité d'XP de PalaJobs**. Le total pour
atteindre le niveau 20 y vaut 17 302 375 ; celui de DoHy pour le niveau 100 vaut 6 175 195.
Une XP de PalaJobs vaut donc **0,357** XP de DoHy, à progression égale. C'est ce rapport qui
traduit les gains d'XP codés en dur dans PalaMod (la potion de dureté, le God Villager).

### Les quatre métiers

`JobType` : `MINER`, `HUNTER`, `FARMER`, `ALCHEMIST`. Le wiki Bedrock annonce trois métiers ;
le code en déclare quatre. Ce sont les quatre progressions de DoHy (`Job`) : ils ont remplacé
les neuf skills le 2026-11-08. Minage, combat, agriculture et alchimie leur ont légué leur XP
(reprise à la connexion par `JobMigration`), leurs sources et leurs perks.

### Récompenses de niveau

Chaque registerer appelle `addReward(niveau, Reward.builder()...build())`. 280 récompenses :
204 en objets (`RewardType.GIVE`), 76 en argent (`MONEY_GIVE`, 1 000 $ au niveau 2 puis
+500 $ par niveau, 10 500 $ au 20). **Seuls les objets sont portés** : DoHy paie déjà chaque
niveau avec sa propre courbe. Ils sont dans `jobs.json` (`rewards`), niveaux ×5.

| Ce que tous les métiers reçoivent | Paliers |
|---|---|
| Lingots de paladium et trixium, 5 × le niveau Paladium | chaque palier, 1 050 de chaque au total |
| Pépite d'endium | 100 |
| Bonbon de métier (pas encore porté) | 95 |

Et par métier, entre autres : la pioche en paladium Efficacité V / Solidité III (Mineur, 30),
l'armure en paladium Protection IV / Solidité III (Mineur, 55), l'épée en paladium
Châtiment V / Solidité III (Chasseur, 30), 64 obsidiennes (Mineur, 65), un crâne de
wither squelette (Mineur et Alchimiste), une tête de dragon (Chasseur, 95).

**Piège d'extraction** : la pioche, l'épée et l'armure enchantées ne passent pas par un
`new ItemStack` dans la chaîne du `Reward.builder` — elles sont construites à part, enchantées
par `func_77966_a`, rangées dans une variable locale, puis passées par un `aload`. Un relevé
qui ne suit pas les variables locales les perd (six récompenses). `extract_jobs.py` suit donc
les `astore`/`aload`.

**Tête de dragon** : la récompense du Chasseur au niveau 19 est un crâne vanilla de méta 5,
qui n'existe pas en 1.7.10 vanilla. C'est le module `back2future` de PalaMod
(`enableFancySkulls`, repris d'Et Futurum) qui l'ajoute.

**Joueurs migrés** : à la première connexion après le remplacement des skills, un joueur
reçoit les objets de tous les paliers qu'il avait déjà franchis — leur argent avait été
versé, les objets n'existaient pas.

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

**Entonnoirs** (vanilla ou en paladium) : ils ne prennent que les particules, jamais la
cobblestone ni la Voidstone minage de l'entrée. Le crusher, de même, ne lâche que son résultat.

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

`TileEntityTotem`, relevé intégralement au bytecode. **L'ambiguïté ms/ticks est tranchée : ce
sont des millisecondes.** Le tick commence par `System.currentTimeMillis()`, et les deux champs
sont des `long` comparés à cette horloge :

```
 4: invokestatic System.currentTimeMillis():J   → now
26: getfield timeforgrowth:J ; ifne             → si timeforgrowth == 0
36: ldc2_w 10000l ; ladd                        →   timeforgrowth = now + 10000
55: lload now ; getfield waiting ; ifle         → si now > waiting
66: ldc2_w 1000l ; ladd                         →   waiting = now + 1000
```

| Donnée | Valeur | Source |
|---|---|---|
| Période de croissance | **10 s** (200 ticks) | `timeforgrowth = now + 10000` |
| Période de balayage de la zone | **1 s** (20 ticks) | `waiting = now + 1000` |
| Zone balayée | **15 × 15**, de −7 à +7 inclus, **au niveau `y − 1`** | boucle `bipush -7` … `if_icmpge 8`, `field_145848_d` − 1 |
| Poussées de croissance par lingot | **2 048** | `DEFAULT_MAX_BOOSTED_BLOCKS` |
| Stock de lingots | 64 | la pile de carburant |
| Verrou | Fermier 14 → niveau **70** (le wiki dit 55 — faux) | `FarmerRegisterer` |

**Le totem ne travaille que sur la terre fertilisée.** Le balayage ne retient que les
`BlockConnectedDirt` (`instanceof #101`), et le boost revérifie la condition avant d'agir. Le
totem et la terre fertilisée du chapitre 3 sont donc un couple : sans terre fertilisée, un
totem alimenté ne fait rien. Ce n'est écrit nulle part sur le wiki.

Ce que fait une poussée, par case retenue (`lambda$updateEntity$0`) :

1. La plante est le bloc en `y + 1` ; elle doit exister, ne pas être de l'air, et être
   `IPlantable`. Sans carburant, rien.
2. Si elle est `IGrowable` et peut pousser : particules d'os (`playAuxSfx 2005`), puis
   `blockboost++`. Quand `blockboost > 2048`, **un lingot est consommé** et le compteur repart
   à zéro. Le lingot paie donc 2 048 *poussées réussies*, pas 2 048 blocs.
3. Puis **1 ou 2 tirages** (`random.nextInt(2) + 1`) de `updateTick` sur la plante.
4. Branche sans `IGrowable` (cannes, cactus) : `updateTick` en `y + 1`, puis en `y + 2` si
   c'est le même bloc.

**Une bizarrerie de l'original, non reproduite.** Le balayage ne tourne que
`if (blockList.isEmpty())`. La liste ne faisant que grossir, elle n'est rebalayée qu'une fois :
de la terre fertilisée posée **après** le totem n'est jamais prise en compte. C'est un piège,
pas un choix d'équilibrage — le port rebalaye à chaque cycle de croissance (225 blocs, c'est
gratuit).

Alimentation : lingot de paladium en main, clic droit (un lingot par clic). Clic droit à main
vide : consulter le contenu. Maj + clic droit : afficher la zone. Cassé, il rend les lingots.

---

## 6. Cultures

`BaseBlockCrops` reprend la mécanique de croissance **vanilla** : lumière ≥ 9, tirage
`random.nextInt(25 / vitesse + 1)`, avec les mêmes facteurs de voisinage (3,0 et 4,0) que le
blé. Elle se transpose donc directement sur `CropBlock` en 26.2, sans rien réinventer.

Probabilités de drop lues dans `getDrops`, sur une base de 10 000 : **800** (8 %), **500**
(5 %), **200** (2 %) et **25** (0,25 %).

### Le lot rare — le vrai mécanisme, absent du wiki

Lu dans `BaseBlockCrops.getDrops` : **à maturité, un tirage sur 10 000 décide entre un lingot
et le fruit.** Le lingot *remplace* le fruit, il ne s'y ajoute pas. L'agriculture est donc une
voie d'accès aux métaux à part entière, parallèle au minage — ce qui explique pourquoi le
crusher est verrouillé sur le **Fermier** et non sur le Mineur.

| Culture | Stades (code) | Dessins | Chance du lot | Lot rare | Graine rendue | Verrou Fermier |
|---|---|---|---|---|---|---|
| Eggplant | 8 | 4 | 800 → **8 %** | lingot d'améthyste | toujours | 7 → **35** |
| Chervil | 8 | 4 | 500 → **5 %** | lingot de titane | toujours | 12 → **60** |
| Kiwano | 10 | 5 | 200 → **2 %** | lingot de paladium | toujours | 16 → **80** |
| Orangeblue | 10 | 5 | 25 → **0,25 %** | pépite d'endium | **6,67 %** | 20 → **100** |

**L'orangeblue est la seule à ne pas s'auto-entretenir** : un second tirage, sur 30, ne rend la
graine que si le résultat vaut 0 ou 1. C'est délibéré — c'est la seule source d'endium du jeu.

**Une culture immature ne lâche rien du tout**, pas même sa graine : `func_149745_a` rend 0 et
`BlockBush` ne donne rien. C'est plus dur que le blé vanilla.

Un dessin couvre **deux stades consécutifs** (stage 0 pour les âges 0-1, etc.) — relevé dans les
tableaux de `textureNames` de `PRegister_Blocks`.

Le troisième argument du constructeur (`5`, `10`, `15`, `20` selon la culture) est **mort** : il
n'est jamais stocké ni lu. Rien à porter.

Chaque culture a trois assets : le bloc (`blocks/crops/<nom>_stage_N.png`), la graine
(`items/seeds/<nom>.png`) et le fruit (`items/fruits/<nom>.png`, **au pluriel** pour l'aubergine :
`eggplants.png`).

### La terre fertilisée — `connectedDirt`, l'accélérateur de cultures

Lu dans `BlockConnectedDirt`, absent du wiki. **À chaque tick aléatoire, le bloc fait passer
deux ticks de croissance supplémentaires à la plante posée dessus.** Avec celui que la plante
reçoit par elle-même, elle avance environ **trois fois plus vite**. Des particules d'os
(effet 2005) sont jouées quand la plante peut encore pousser.

S'y ajoute `isFertile()` qui rend toujours `true` : la culture touche le bonus de terre labourée
irriguée **sans aucune eau à proximité**.

En 26.2 il faut deux choses pour cela. `CropBlock.getGrowthSpeed` (les tiges de melon et de
citrouille passent par le même calcul) ne compte un sol que s'il est dans le tag
`minecraft:grows_crops` — NeoForge interroge bien `canSustainPlant`, mais en passant le sol
lui-même comme plante, si bien que le bloc ne peut pas répondre à la place du tag. Puis il demande
`isFertile` : 3 points si oui, 1 si non. La terre fertilisée labourée est donc dans
`grows_crops` (et `supports_crops`, que lisent les tiges pour se poser) et surcharge `isFertile`.
Sans le tag, elle valait 0 point, moins qu'une terre labourée sèche.

Deux variantes, un seul `boolean tilled` dans le constructeur :

| Variante | Porte | Obtention |
|---|---|---|
| `connectedDirt` | herbe, fleurs, pousses d'arbre (types Plains, Cave, Water) | — |
| `connectedDirtTilled` | les cultures (type Crop) | houe sur la version brute |

**Les deux accélèrent** : `func_149674_a` n'est pas conditionnée au booléen, donc la version
brute nourrit aussi les arbres et les fleurs.

Le passage de l'une à l'autre se fait à la houe, via `UseHoeEvent` dans `PlayerHandler`.
Textures : une seule, `blocks/connectedDirt.png`, **absente du pack officiel** — reprise du jar.
Dureté 0,6, son de gravier, hauteur 15/16 comme la terre labourée.

**Aucune recette de craft n'existe dans le code** : sur le serveur, la terre fertilisée
s'achetait.

### Le compromis de portage sur le nombre de stades

En 26.2, `CropBlock` est câblé sur `AGE_7` (8 stades) et **n'expose plus de `getAgeProperty()`**
surchargeable. L'aubergine et le cerfeuil sont donc **exacts**. Le kiwano et l'orangeblue
passent de 10 à 8 stades, ce qui les ferait mûrir 20 % plus vite — pas anodin quand l'orangeblue
est la seule source d'endium.

La compensation porte sur la **cadence** et non sur le nombre d'étapes : `PalaCropBlock` ignore
20 % des ticks de croissance pour ces deux cultures, de sorte que le *temps total* de maturation
reste celui de Paladium. Le joueur voit huit étapes au lieu de dix ; il attend aussi longtemps.

### Seedplanters

Cinq **items**, pas des blocs. `SeedPlanterSeedFood(String name, int r, int lvlmin, ArrayList)`,
relevé dans `PRegister_Items` :

| Texture / id d'origine | Nom | `r` | Côté planté (`r/2 × 2`) | `lvlmin` | Verrou `FarmerRegisterer` ×5 |
|---|---|---|---|---|---|
| `seedplanter_lvl1` | Améthyste | 3 | 2 × 2 | 5 | **15** |
| `seedplanter_lvl2` | Titane | 5 | 4 × 4 | 10 | **30** |
| `seedplanter_lvl3` | Paladium | 7 | 6 × 6 | 15 | **75** |
| `seedplanter_lvl4` | Endium | 12 | 12 × 12 | 20 | **100** |
| `seedplanter_lvl5` | Paladium vert | 12 | 12 × 12 | 17 | **90** |

**Deux jeux de niveaux se contredisent dans PalaMod.** Le champ `lvlmin` de l'item (5/10/15/20/17)
et le niveau du registre de métier (3/6/15/18/20 sur l'échelle 20) ne concordent que pour le
paladium. C'est `FarmerRegisterer` qui gouverne le verrou — c'est lui que `JobGate` lit depuis
`data/jobs.json` — et `lvlmin` n'est qu'une valeur d'infobulle. Les deux sont notés ici pour que
le prochain qui trouve un écart sache qu'il est d'origine.

Noter que le paladium vert a **le même rayon que l'endium** pour un niveau plus bas : c'est une
version alternative, pas un palier supérieur.

**Le mécanisme, et il est très particulier** (`func_77659_a`) : les graines doivent être dans
**la case de la barre d'action immédiatement à droite** du planteur
(`mainInventory[currentItem + 1]`). Le planteur lit cette pile, en déduit la plante
(`ItemSeeds.getPlant` ou `BaseItemSeed.getPlant`), puis :

```java
int half = r / 2;
for (dx = -half; dx < half; dx++)
  for (dz = -half; dz < half; dz++) {
      x = (int) player.posX + dx; y = (int) player.posY; z = (int) player.posZ + dz;
      if (!world.isAirBlock(x, y, z)) continue;
      Block below = world.getBlock(x, y - 1, z);
      if (!(below instanceof BlockFarmland || below instanceof BlockConnectedDirt)) continue;
      if (seeds.stackSize <= 0) continue;
      world.setBlock(x, y, z, plant);
      seeds.stackSize--;
  }
```

Donc : **au niveau des pieds du joueur**, case d'air obligatoire, sol labouré **ou terre
fertilisée**, une graine consommée par case. Aucun labour : le planteur plante, il ne prépare
pas. Et la boucle `-half` → `< half` n'est pas centrée sur le joueur : elle est décalée d'un
demi-bloc, ce qui est le comportement d'origine.

---

## 6 bis. Minerais et matériaux — relevés dans `PWorld` et `BaseWorldGenerator`

Statistiques de bloc, lues au bytecode (`BlockOre(material, dureté, texture)` puis
`setHarvestLevel("pickaxe", n)`) :

| Minerai | Dureté | Résistance | Niveau de pioche | Tag 26.2 |
|---|---|---|---|---|
| Améthyste | 3,0 | 5,0 | 1 | `needs_stone_tool` |
| Titane | 3,0 | 5,0 | 2 | `needs_iron_tool` |
| Paladium | 5,0 | 5,0 | 3 | `needs_diamond_tool` |
| Paladium vert | 5,0 | 5,0 | 3 | `needs_diamond_tool` |
| Findium | 5,0 | 5,0 | 3 | `needs_diamond_tool` |
| Trixium | 3,0 | 5,0 | 3 | `needs_diamond_tool` |

**Butin — écart demandé par le joueur (2026-11-09).** PalaMod lâchait directement le lingot (ou
le findium, le trixium), multiplié par la Fortune. Ici, chaque minerai se rend **lui-même**, comme
le minerai de pépites d'endium et celui de Pierre d'Invocation, et se fond au four (200 ticks) ou
au haut fourneau (100) : 0,7 XP pour l'améthyste et le titane, 1 pour les autres. La Fortune ne
joue plus, sauf à la pioche des dieux, qui fond ce qu'elle casse et multiplie le résultat.

Tailles de filon, lues dans les constructeurs de `WorldGenMinable` :
améthyste **8**, titane **6**, paladium **4**, paladium vert **3**, findium **3**, trixium **3**.

**Les hauteurs d'origine ne sont pas reprises, et c'est volontaire.** En 1.7.10 le monde allait
de 0 à 255 et la deepslate n'existait pas ; recopier ces plages concentrerait les minerais au
mauvais endroit d'un monde qui descend maintenant à −64. Le nombre par chunk et la profondeur
sont donc **calibrés** sur l'analogue vanilla de chaque niveau de pioche, les tailles de filon
restant celles du code. C'est le seul endroit du chapitre où un chiffre est de moi.

| Minerai | Filon (code) | Par chunk (calibré) | Hauteur (calibrée) |
|---|---|---|---|
| Améthyste | 8 | 8 | −24 à 64 |
| Titane | 6 | 6 | −32 à 40 |
| Paladium | 4 | 4 | −48 à 16 |
| Paladium vert | 3 | 2 | −56 à −8 |
| Findium | 3 | 3 | −64 à 0 |
| Trixium | 3 | 1 | −64 à −32 |

### Une septième particule, absente du wiki

Le wiki annonce six particules de minerai. Le code en a **sept** : `item.particle.green.paladium.name`
existe et sa texture est dans le jar. Elle est enregistrée.

### Formats 26.2, établis sur preuve

Relevés avec `gradle packMeta`, qui lit le jar de Minecraft et celui de NeoForge :

| | Valeur |
|---|---|
| `version.json` de MC 26.2 | `resource_major` 88, `resource_minor` 0, `data_major` 107, `data_minor` 1 |
| `pack.mcmeta` du jar universel NeoForge 26.2.0.93 | `min_format` et `max_format` = `[107, 1]` |
| Dossiers de données (jar vanilla) | `loot_table/`, `recipe/`, `tags/`, `worldgen/` — **au singulier** |
| Dossiers d'assets | `blockstates/`, `models/`, `textures/`, `lang/`, **`items/`** |

Le dossier `assets/<ns>/items/` est la définition d'item, obligatoire en plus du modèle :
sans elle l'icône est violette. C'est ce que vérifie le garde-fou de CI du chapitre 2.

---

### Interfaces de conteneur en 26.2 — relevé sur le classpath

`GuiGraphics` n'existe plus : le rendu d'interface est passé en **mode retenu**. Un écran
n'écrit plus dans un contexte graphique, il *extrait* son état dans un objet que le moteur rend
ensuite. Signatures confirmées par `gradle apiProbe` :

| Rôle | 1.21.x | 26.2 |
|---|---|---|
| Contexte de rendu | `GuiGraphics` | `GuiGraphicsExtractor` |
| Fond d'un écran de conteneur | `renderBg(GuiGraphics, float, int, int)` | `extractBackground(GuiGraphicsExtractor, int, int, float)` — **public** |
| Dessin d'une portion de texture | `blit(Identifier, …)` | `blit(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int texW, int texH)` |
| Pipeline d'interface | — | `RenderPipelines.GUI_TEXTURED` |
| Association type → écran | `MenuScreens.register` | `RegisterMenuScreensEvent.register(MenuType<? extends M>, MenuScreens.ScreenConstructor<M, U>)` |
| Constructeur d'écran | idem | `AbstractContainerScreen(T, Inventory, Component, int imageWidth, int imageHeight)` |
| Texte d'un écran de conteneur | `renderLabels(GuiGraphics, int, int)` | **supprimé** → écrire dans `extractContents(GuiGraphicsExtractor, int, int, float)` |
| Écriture d'une ligne | `GuiGraphics.drawString` | `extractor.text(Font, …, x, y, color)` / `centeredText(Font, …, centerX, y, color)` |
| Coin de l'écran | `getGuiLeft()` / `getGuiTop()` | **marqués pour suppression** → `getLeftPos()` / `getTopPos()` |

Noter l'**ordre des arguments de `extractBackground`** : le `partialTick` est passé en dernier
alors que `renderBg` le prenait en premier. Les quatre paramètres sont des nombres, donc une
inversion compile sans un mot — elle se voit seulement en jeu, par un fond qui ne suit pas la
souris.

Le canal d'entiers entre un menu serveur et sa copie client reste `ContainerData` /
`addDataSlots` : c'est la seule façon de synchroniser une progression sans écrire de paquet.

Premier usage de cette sonde, et un piège écarté : en 1.21.x, `BaseEntityBlock` redéfinit
`getRenderShape()` pour rendre `INVISIBLE`, ce qui rend un bloc invisible tant qu'on ne
redéfinit pas à `MODEL` (c'est pourquoi `AbstractFurnaceBlock` le fait). **En 26.2 ce piège
n'existe plus** : `javap -p` montre que `BaseEntityBlock` ne déclare que son constructeur,
`codec()`, `triggerEvent`, `getMenuProvider` et `createTickerHelper` — pas de
`getRenderShape`. Les machines s'affichent donc depuis leur blockstate, sans redéfinition.
À noter aussi, `getMenuProvider` y est **protégé** et rend la block entity quand elle est un
`MenuProvider`.

Pour tout crochet **protégé** (`getRenderShape`, `useWithoutItem`,
`createBlockStateDefinition`), la sonde par réflexion ne sert à rien : elle ne liste que le
public et ne dit jamais ce qu'une méthode retourne. Utiliser
`gradle bytecodeProbe -Pjavap=<classe> -Pmember=<méthode>`, qui lance `javap -p -c -constants`
sur le classpath.

Deux pièges qui compilent sans un mot :

- Un chiffre écrit dans `extractBackground` passe **sous les objets** des cases. Le texte va
  dans `extractContents`, après `super`.
- `register` de `RegisterMenuScreensEvent` déclare `<M extends AbstractContainerMenu, U extends
  Screen & MenuAccess<M>>`, et `U` ne se déduit que du type de retour de la lambda — qui est
  lui-même une expression à inférer. Sans témoin de type explicite
  (`event.<MachineMenu, MachineScreen>register(…)`), javac rend « cannot infer type-variable(s)
  M,U ».

---

## 9. Recettes de fabrication — relevées, pas transcrites

Les recettes sont **éparpillées sur quatre registres**, ce qui explique pourquoi le chapitre 2
avait noté « ingrédients des charbons mixés inconnus » : je n'avais regardé que
`PRegister_Craft`.

| Registre | Appels | Nature |
|---|---|---|
| `PRegister_Craft` | 218 | table de craft |
| `smeltery/registerer/PSRegister_Craft` | 29 | table de craft |
| `hunter/init/Crafts` | 24 | table de craft |
| `miner/init/Crafts` | 19 | table de craft |
| `PaladiumMachineRecipies.add` | 38 | **palamachine** → chapitre 16 (33 portées) |
| `GrinderRecipe.addUpgrade` | 6 | **grinder** → chapitre 5b |
| alchimie, bow machine | 12 | machines non portées |

`tools/extract_crafts.py` lit les quatre premiers et rend **282 recettes** — exactement
`249 + 11 + 22`, le compte des trois surcharges de `GameRegistry` dans le bytecode. C'est le
contrôle qui dit que le parseur voit tous les appels et n'en invente aucun.

### Trois fautes que le relevé automatique a rattrapées

**1. La surcharge `addRecipe(IRecipe)`.** Huit appels passent une recette à classe propre
(`SpikeUpgradeRecipe`) et n'ont donc pas de tableau d'arguments. Sans filtrer sur le
descripteur, le parseur remontait au tableau de la recette *précédente* : **six recettes se
retrouvaient attribuées au totem**. Le motif inclut maintenant
`(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)`.

**2. Le résultat se lit par position, pas par recherche.** Dès qu'un *ingrédient* est un
`new ItemStack` — ce qui arrive quand il porte un meta — une recherche arrière « le premier
`new ItemStack` croisé » attrape le mauvais. On part du dernier
`invokespecial ItemStack."<init>"` avant le tableau et on remonte à son `new` : par
construction de javac, c'est le résultat.

**3. Les metas comptent.** La recette du totem demande `Items.skull` **meta 4** et
`Items.dye` **meta 15** : une tête de creeper et de la poudre d'os, pas une tête de squelette
et de l'encre noire. De la poudre d'os dans un totem de fertilité n'est évidemment pas un
hasard, et la perdre aurait donné une recette fausse qui se fabrique quand même.

### Quatrième faute, trouvée en jeu : javap rogne les espaces de fin

Onze recettes — toutes les pioches, deux haches, trois charbons mixés — ont été **rejetées au
chargement** (`Invalid pattern: each row must be the same width`) sans rien afficher en jeu. La
cause : javap rogne les espaces de fin de toutes les chaînes qu'il affiche, dans le commentaire
de `ldc` **comme dans le pool de `javap -v`**. La ligne `" Y "` sortait `" Y"`.

`extract_crafts.py` lit donc maintenant le pool de constantes **dans le fichier `.class`
lui-même** : un `ldc #25` désigne une `CONSTANT_String`, qui pointe vers une `CONSTANT_Utf8`,
dont on prend les octets tels quels. Le générateur refuse toute grille non rectangulaire (une
grille 1.7.10 l'est par construction : `CraftingManager` découpe la chaîne concaténée par la
largeur de la dernière ligne), et `check_assets.py` vérifie largeurs et clés de chaque recette.

### Une faute dans PalaMod, portée telle quelle

La recette du cobblebreaker a une grille `CPC / CVC / CFC` et des clés `C`, **`D`**, `V`, `F` :
le `P` de la grille n'a pas de clé, et la clé `D` (pioche en paladium) n'est utilisée nulle
part. En 1.7.10, `CraftingManager` met `null` pour un caractère inconnu, donc **la case du haut
au milieu est vide** et la pioche n'a jamais été demandée. Corriger la faute donnerait une
recette plus chère que l'originale ; `extract_crafts.py` signale l'anomalie et on porte ce que
le jeu faisait. Cinq recettes sur 282 ont ce genre d'incohérence.

### Ce qui est porté, et ce qui attend

Quinze objets étaient introuvables en survie. Dix le sont plus, **plus quatre intermédiaires
qu'il fallait porter d'abord** : sans les bâtons en améthyste, titane et paladium, et sans le
cœur en paladium, les cinq seedplanters, le crusher et le charbon mixé de paladium étaient
impossibles à écrire.

| Résultat | Rendement | Note |
|---|---|---|
| Bâton améthyste / titane | 1 | 2 lingots verticaux |
| Bâton paladium | **4** | même grille, rendement différent — donnée d'origine |
| Cœur en paladium | 1 | 5 paladium + 4 titane |
| Charbon mixé or | **6** | 1 lingot d'or + 1 charbon |
| Charbon mixé améthyste / titane | **8** | 4 lingots + 1 charbon mixé or |
| Charbon mixé paladium | **1** | + 2 bâtons de paladium |
| Totem de fertilité | 1 | 4 bûches, tête de creeper, 2 paladium compressé, poudre d'os |
| Crusher | 1 | 4 cœurs, améthyste compressé, 4 bâtons, findium |
| Les 5 seedplanters | 1 | chaîne lvl1 → lvl2 → lvl3 → **lvl5** → lvl4 |

Les rendements 6, 8, 8 et 1 des charbons mixés **concordent avec le wiki**, ce qui confirme le
relevé par une source indépendante.

La chaîne des seedplanters dit la même chose que la table des métiers, par un autre chemin : le
paladium vert est un **passage obligé** vers l'endium, ce qui explique son verrou à 90 contre
100.

**Porté depuis : le cobblebreaker et ses trois améliorations**, avec leur chaîne complète :

| Objet | Recette | Nature dans PalaMod |
|---|---|---|
| Voidstone | 4 améthyste + coffre | `ItemVoidStone` : **poubelle de poche**, ouvre une case qui détruit ce qu'on y pose — refuse les outils et elle-même |
| Orbe de vitesse | 4 paladium + sucre | `BaseItem` nu |
| Modificateur de vitesse | 4 paladium + orbe | `ItemGrinderModifier`, sans comportement : le grinder le lira |
| Modificateur de fortune | 4 paladium + bloc d'or | idem |
| Cobblebreaker | 6 pierres, voidstone, modificateur de fortune | case du haut vide (la faute `P`/`D`) |
| Amélioration améthyste | 6 améthyste, **un cobblebreaker**, modificateur de vitesse | consomme une machine |
| Amélioration titane / paladium | 8 lingots + l'amélioration précédente | chaîne |

**Ancienne note (chapitre 5a), conservée pour l'historique : pas porté alors.** Le cobblebreaker demande la
voidstone et le modificateur de fortune du grinder ; les améliorations demandent le
modificateur de vitesse puis s'enchaînent. La voidstone a un comportement propre dans PalaMod
et un verrou dans `data/jobs.json` — la porter comme ingrédient inerte serait un mensonge, un
joueur la fabriquerait et constaterait qu'elle ne fait rien. Les modificateurs appartiennent au
grinder, donc au chapitre 5b.

Le relevé complet est archivé dans `data/palamod_crafts.json` : les 282 recettes, avec leurs
anomalies, prêtes pour les chapitres suivants.

---

## 10. Équipement — outils, armes, armures et leurs effets

Relevé sur trois sources : `PToolMaterial` et `PArmorMaterial` (PalaMod), `PRegister_Items`
pour la construction de chaque objet, et **PalaJobs** pour les houes, qui appartiennent au
métier de fermier et non au module des outils — c'est pourquoi elles n'apparaissaient pas dans
le registre principal.

### Matériaux d'outils — `EnumHelper.addToolMaterial(nom, récolte, durabilité, efficacité, dégâts, enchant.)`

| Matériau | Récolte | Durabilité | Efficacité | Bonus de dégâts | Enchantabilité |
|---|---|---|---|---|---|
| améthyste | 3 | 1 999 | 20,0 | 3,0 | 20 |
| titane | 3 | 2 999 | 23,0 | 3,5 | 20 |
| paladium | 3 | 4 999 | 28,0 | 6,0 | 15 |
| paladium vert | 3 | 4 999 | 35,0 | 6,0 | 15 |
| endium | **4** | 4 999 | 40,0 | 7,3 | 25 |

Pour mémoire, la netherite vanilla a une efficacité de 9. Les outils Paladium creusent **deux à
quatre fois plus vite** : c'est la donnée d'origine, pas une erreur d'échelle.

**Les épées se portent à l'identique.** En 1.7.10 une épée fait `4 + bonus` ; en 26.2,
`Properties.sword(matériau, 3.0, −2.4)` donne `1 + bonus + 3`. Même formule : améthyste 7,
titane 7,5, paladium et paladium vert 10, endium 11,3. Pioche, hache, pelle et houe reprennent
les décalages des outils en diamant de 26.2, si bien qu'un outil Paladium est à l'outil vanilla
moderne ce qu'il était à l'outil vanilla de 1.7.10 : le même bonus sur la même base.

**`SwordItem` et `PickaxeItem` n'existent plus en 26.2.** Une épée est un `Item` nu avec
`Properties.sword(...)`. Hache, pelle et houe gardent leur classe pour l'écorçage, les chemins
et le labour.

L'endium n'a **pas de pelle** dans PalaMod.

### Houes à rayon — PalaJobs, `ItemRadiusHoe(durabilité, rayon, nom)`

| Houe | Durabilité | Rayon | Carré labouré |
|---|---|---|---|
| améthyste | 1 999 | 2 | 2 × 2 |
| titane | 2 999 | 4 | 4 × 4 |
| paladium | 4 999 | 6 | 6 × 6 |
| paladium vert | 4 999 | 8 | 8 × 8 |
| endium | 4 999 | 12 | 12 × 12 |

Même géométrie exacte que les seedplanters : `half = (int)(radius / 2.0)`, boucle de `-half`
inclus à `half` exclu, donc un carré décalé d'un demi-bloc. Le bloc visé doit être de l'herbe,
de la terre ou du sol labouré ; chaque case passe par l'événement de labour, et **une seule
durabilité est consommée par clic**, pas par case. Les houes et les planteurs vont par paires
— on laboure le carré qu'on plantera.

La recette de la houe en endium demande des **pépites** d'endium, pas des lingots.

### Matériaux d'armure — `EnumHelper.addArmorMaterial(nom, facteur, réductions[4], enchant.)`

| Set | Facteur | Casque | Plastron | Jambières | Bottes | Total | Enchant. |
|---|---|---|---|---|---|---|---|
| améthyste | 170 | 9 | 10 | 9 | 8 | 36 | 44 |
| titane | 200 | 9 | 11 | 10 | 8 | 38 | 35 |
| paladium | 260 | 10 | 13 | 11 | 9 | 43 | 15 |
| paladium vert | 300 | 10 | 13 | 11 | 9 | 43 | 15 |
| endium mixé | 350 | 10 | 14 | 11 | 9 | 44 | 15 |
| endium | 400 | 11 | 14 | 11 | 10 | 46 | 15 |

La durabilité se porte directement : `ArmorType.getDurability(facteur)` multiplie par les mêmes
bases {11, 16, 15, 13} qu'en 1.7.10. Casque en endium : 4 400.

### La seule valeur calibrée du chapitre : la défense

**Ces totaux sont impossibles à porter tels quels, et la raison mérite d'être écrite.** En
1.7.10, Forge calcule `points / 25` par pièce, additionne, et plafonne à 100 %. Toute armure de
25 points ou plus rendait donc **invulnérable aux coups** — et chaque set Paladium en fait au
moins 36. Ce n'est évidemment pas le jeu réel : le calcul de dégâts de Paladium vit dans un
**plugin serveur qui n'est dans aucun des jars fournis**. Vérifié en cherchant
`applyArmorCalculations`, `ISpecialArmor` et `ArmorProperties` dans tous : rien.

En 26.2, l'attribut d'armure plafonne à 30 et la réduction à 80 %. La projection retenue :

- **ancrée sur le diamant**, présent dans les deux versions avec les mêmes valeurs (3/8/6/3) ;
- **pièce par pièce** et non set par set : la même droite par emplacement, si bien qu'un palier
  supérieur ne peut jamais être moins bon qu'un inférieur, et que deux originaux égaux restent
  égaux. La projection par set, essayée d'abord, rendait le casque en titane moins bon que celui
  en améthyste — alors que les deux valent 9 ;
- défense projetée de [20 (diamant) … 46 (endium)] sur [20 … 30], robustesse de [2 … 4].

| Set | Défense 26.2 | Total | Robustesse | Réduction contre 10 / 20 / 30 |
|---|---|---|---|---|
| *diamant* | *3 / 8 / 6 / 3* | *20* | *2* | *64 / 48 / 32 %* |
| *netherite* | *3 / 8 / 6 / 3* | *20* | *3* | *65 / 51 / 36 %* |
| améthyste | 5 / 9 / 7 / 5 | 26 | 3,2 | 80 / 75 / 61 % |
| titane | 5 / 9 / 8 / 5 | 27 | 3,4 | 80 / 80 / 66 % |
| paladium | 6 / 10 / 8 / 5 | 29 | 3,8 | 80 / 80 / 75 % |
| paladium vert | 6 / 10 / 8 / 5 | 29 | 3,8 | 80 / 80 / 75 % |
| endium mixé | 6 / 10 / 8 / 5 | 29 | 3,8 | 80 / 80 / 75 % |
| endium | 6 / 10 / 8 / 6 | 30 | 4,0 | 80 / 80 / 80 % |

**À savoir avant de tester : la formule de 26.2 écrase le haut de gamme.** Contre un coup de 10,
tous les sets Paladium sont au plafond. Ce n'est pas trahir l'original : en 1.7.10 côté client,
ils étaient tous à 100 %, donc tout aussi identiques. La progression se lit sur les **gros
coups** — épée en paladium tranchant V et coup critique, c'est 20 à 30 —, sur la **durabilité**
(1 870 → 4 400 pour un casque) et sur les **effets**. Si l'écart paraît trop faible en jeu, le
levier est la robustesse : c'est elle qui sépare les armures contre les coups lourds.

### Réparation

Chaque objet se répare avec son propre métal — **sauf l'armure en paladium, qui se répare au
titane**. Une seule exception sur 43 objets, relevée au constructeur ; portée telle quelle.

Le système de réparation propre à Paladium (`IRepairable`, une capacité de réparation par pièce,
8 000 / 12 000 / 10 000 / 7 000 pour le paladium) n'est **pas** porté : c'est l'enclume vanilla
qui répare, avec l'ingrédient d'origine.

### Effets — `onArmorTick`, relevés classe par classe

| Set | Casque | Plastron | Jambières | Bottes | Panoplie complète |
|---|---|---|---|---|---|
| améthyste, titane | — | — | — | — | — |
| paladium | vision nocturne | — | — | vitesse I | — |
| paladium vert | vision nocturne | célérité I | — | vitesse II | — |
| endium mixé | vision nocturne | résistance au feu | célérité II | vitesse II **et aucune chute** | — |
| endium | vision nocturne | vision nocturne | vision nocturne | vision nocturne | **invisibilité** |

Trois détails qu'on ne devinerait pas :

- **Les bottes en endium mixé annulent toute chute.** Elles appliquent `PotionFeatherFalling`,
  une potion propre à PalaMod que `PotionHandler` lit sur `LivingHurtEvent` : dégâts de
  `DamageSource.fall` ramenés à 0, événement annulé. Pas une réduction, une immunité.
- **La panoplie complète d'endium rend invisible**, réappliquée à chaque tick. Le test porte sur
  les quatre emplacements (`instanceof ItemEndiumArmor` × 4). L'armure elle-même reste
  visible, comme pour toute invisibilité.
- **Le code d'endium contient deux effets morts.** Après le bloc d'invisibilité, un `goto` saute
  à la fin ; les blocs de force et de vitesse II qui suivent ne sont la cible d'aucun saut. Ils
  ne s'appliquaient donc jamais, et ne sont pas portés.

L'épée en paladium vert a sa propre classe, mais **aucun effet de combat** : seulement une icône
alternative (« elucidatorIcon »). Elle se porte comme une épée ordinaire.

### Ce qui n'est pas porté

| Objet | Pourquoi |
|---|---|
| Armures de tournoi, de voyage, antiques ; objets d'événement | hors du périmètre demandé, chacun a un mécanisme propre |
| Épées larges et épées rapides (`Broadsword`, `Fastsword`) | fabriquées par le système de forge à motifs, qui n'est pas porté |
| Amulettes de l'armure en endium | un système de NBT à part entière |
| Boucle d'inventaire en tête de l'`onArmorTick` d'endium | elle déplace armures et épées dans l'inventaire ; son but n'est pas lisible au bytecode, la deviner serait pire que l'omettre |
| Réparation `IRepairable` | remplacée par l'enclume vanilla |

---

## 7. Grinder — multiblock, pas un bloc

Porté au chapitre 12 : voir § 15. Les incohérences notées ici (30 blocs contre 26, 134 paladium
contre 132, `grinder_table`, `grinder_robinia`) venaient d'une ancienne page du wiki ; le code et
le wiki actuel concordent sur 26 blocs et une source de lave, 126 paladium, et ni table ni robinia.

---

## 8. Assets disponibles, et les trous

Namespace `palamod` du pack Faithful officiel — c'est pour cela que le mod enregistre sous
ce namespace : le pack s'applique alors tel quel, sans conversion ni copie.

**Les textures embarquées viennent du pack officiel en priorité**, et du jar de PalaMod
seulement pour les quatre qu'il est seul à avoir : `connectedDirt`, `trixium_block`,
`compressed_endium` et `particle_green_paladium`. Le pack est la version entretenue et
corrige au passage des défauts du jar — c'est lui qui porte le bon `endium_block` en 16×16,
là où le jar traîne trois homonymes dont deux en 15×15. Le générateur affiche la
provenance et nomme chaque repli : aucun n'est silencieux.

Présent : cobblebreaker 4 paliers (front/side/top), palafurnace (on/off/side), 4 cultures ×
4-5 stades, 4 graines, 4 fruits, 6 particules, 4 charbons mixés, 5 seedplanters, minerais
(améthyste, findium, paladium, paladium vert, titane, trixium), lingots, compressés, anneaux,
sticks, 26 blocs d'alchimiste, 6 blocs de spawner, 5 GUI de coffre.

**Trous à combler** : aucune texture de bloc pour le **crusher**, la **palamachine** et le
**totem** (seul `items/totem.png` existe), ni pour `grinder_table` / `grinder_robinia`.

Attention à l'arborescence : le pack est en 1.7.10 (`textures/blocks/`, `textures/items/`).
En 26.2 il faut `textures/block/` et `textures/item/` — conversion mécanique à faire au
chapitre 2.

---

## 11. Mineur — les objets débloqués (chapitre 8)

Relevé dans `MinerRegisterer` (verrous), `ToolHandler`, `ItemBuilderWand`, `PlayerHandler`,
`TileEntityVoidStone`, `PaladiumFurnaceLogic`, `ForgeLogic`, `TileEntityPaladiumHopper`,
`BlockEndiumCaveRenderer`, `EventsManager` et `BaseWorldGenerator`. Niveaux du code ×5.

| Niveau | Objet | Wiki | Porté |
|---|---|---|---|
| 10 | Voidstone | 2 | déjà au chapitre 4 |
| 15 / 55 / 75 | Excavatrices améthyste / titane / paladium | 3 / 11 / 15 | oui |
| 15 | Commande `/nether` | 3 | **non, à la demande du joueur** |
| 20 / 60 / 85 / 100 | Outils de construction | 4 / 12 / 17 / 20 | oui |
| 25 | Cobblebreaker | 5 | déjà au chapitre 4 |
| 30 | Voidstone minage | 6 | oui |
| 35 | Aimant | 7 | oui |
| 40 | Dollars Stone (`moula_stone`) | 8 | oui |
| 45 | Entonnoir en paladium | 9 | oui |
| 50 / 65 / 80 | Améliorations du cobblebreaker | 10 / 13 / 16 | déjà au chapitre 4 |
| 70 | Palaforge | 14 | oui |
| 80 | Potion de dureté | — | oui |
| 85 | Amélioration de vitesse du spawner | 17 | **non** — pas de système de spawner |
| 90 | Cave block en endium | 18 | oui |
| 90 | Piston renforcé wither (compresseur à cobblestone) | 18 | oui |
| 95 | God Villager | 19 | oui (sans verrou : c'est une apparition) |
| 95 | Trône du Mineur | 19 | **non, laissé de côté à la demande du joueur** |
| 100 | Minerai de pépites d'endium, pioche en endium | 20 | oui |

Les prérequis non listés par le wiki sont portés aussi : **coffres en améthyste et en titane**
(ingrédients de l'entonnoir et de la Voidstone minage), **Palafurnace** et **amélioration de
fourneau** (ingrédients de la Palaforge), **cave block** (ingrédient du cave block en endium).

### Le relevé des verrous ratait 26 objets

`extract_jobs.py` ne reconnaissait que les champs des registres `palamod/api`. Les trônes, les
objets du spawner et les houes à rayon vivent dans d'autres registres (`BlocksRegistry`,
`SpawnerItemRegistry`, `ItemsRegistry` de PalaJobs) : 26 verrous perdus, 99 au lieu de 73.
Conséquence visible hors de ce chapitre : **les houes à rayon sont maintenant verrouillées**
(Fermier 10, 25, 65, 85 et 100). Les verrous d'objets pas encore portés (trônes, spawner) sont
dans la table et restent inertes tant que l'objet n'existe pas.

### Outils de zone — `ToolHandler.removeBlocksInIteration`

Plan 3×3 perpendiculaire à la face visée (lancer de rayon depuis les yeux). Un bloc du plan
n'est cassé que s'il est **au plus aussi dur** que le bloc visé, de dureté positive, récoltable
par l'outil et de sa famille. **Une seule usure par clic** : seul le bloc visé use l'outil.
Chaque bloc du plan passe par `destroyBlock` — drops, XP de Mineur, protections et suivi des
blocs posés compris.

- **Excavatrice** : une pelle (`ItemSpade`), non enchantable (`getItemEnchantability` = 0).
- **Pioche en endium** : `setDamage` plafonne l'usure, elle ne casse jamais ; à bout d'usure le
  3×3 s'arrête. 26.2 détruit une pile qui *atteint* le maximum : le plafond est un point plus bas.
- **Écart** : l'obsidienne et l'obsidienne pleureuse sont exclues du 3×3
  (`#palamod:area_mining_immune`) — sur un serveur de faction, neuf obsidiennes d'un coup
  ouvriraient les bases.

### Outils de construction — `ItemBuilderWand`

Côté 6, 9, 11 ou 20 parcouru de `-côté/2` à `côté/2` exclu : 6, 8, 10 et 20 positions, soit
les 36, 64, 100 et 400 blocs du wiki. Copie le bloc cliqué sur chaque position du plan qui porte
le même bloc et dont la voisine est libre. Un joueur dans le volume annule tout. **Deux bugs de
l'original corrigés** : l'objet était consommé *avant* le test de protection (un bloc refusé
était perdu), et les branches est/ouest étaient inversées.

### Aimant, Dollars Stone, Voidstone minage — `PlayerHandler.playerTickEvent`

- **Aimant** : actif dans la barre d'action, ramène chaque tick les objets d'un cube de 6 blocs.
  Clic droit pour l'allumer, l'éclat d'enchantement le montre.
- **Dollars Stone** (case 8, hors créatif) : retire chaque tick un exemplaire de chaque pile de
  son filtre (pierre, cobblestone, terre, gravier, granite, diorite, andésite et formes polies).
  Paladium payait 0,10 $ la cobblestone et la terre. **Ici c'est le marché de DoHy qui paie**, au
  prix dynamique, par lots d'une seconde : la pierre ne devient pas une source d'argent de plus.
  Un objet que DoHy ne rachète pas est détruit, comme dans l'original ; un objet dont la vente est
  verrouillée par la collection est laissé.
- **Voidstone minage** (case 8) : avale les piles **entières** de pierre et de cobblestone dans son
  compteur, détruit le reste de son filtre. Posée dans un cobblebreaker, elle en est l'entrée
  (une pierre par cycle) ; clic droit sur un bloc, elle se pose en **void stone**.

### Compresseur à cobblestone — `TileEntityVoidStone`

Une void stone entre deux **pistons renforcés wither** sur le même axe, les deux alimentés :
jusqu'à 64 obsidiennes par activation, 100 pierres chacune. Couper le courant réarme le compteur.
Les 64 sortent en une pile plutôt qu'en 64 entités.

**Le piston renforcé wither n'est pas un piston** : `BlockWitheredReinforcedPiston` est un bloc
orienté qui ne pousse rien — le wiki se trompe (« poussent 3 blocs de plus »). Il tombe des
pistons pris dans **l'explosion d'apparition** d'un Wither (source directe : le Wither), si le
joueur le plus proche à 30 blocs a Mineur 90. Alimenté, il change la première **obsidienne**
voisine (est, ouest, sud, nord ; jamais dessus ni dessous) en **obsidienne wither**, le cadre du
portail de la dimension du Mineur (§ 18).

### Palafurnace — `PaladiumFurnaceLogic`

Recettes et combustibles du fourneau vanilla. `n` = améliorations de fourneau dans la case de
gauche, plafonné à 16, et 1 si elle est vide : une cuisson dure `200 / n` ticks et le combustible
brûle `n` fois plus vite. **Une seule amélioration ne change rien** (`n` vaut déjà 1) : c'est
l'original. Pas d'XP de fourneau au retrait.

### Palaforge — `ForgeLogic`, `EnumAllowItemsForge`

Refond l'équipement en lingots : `maximum × pct / 100` en division entière, `pct` étant la
durabilité restante. Cycle de 101 ticks. Maximums (casque, plastron, jambières, bottes, épée,
hache, pioche, pelle) : fer et or 4-7-6-3-2-2-2-1, titane 5-8-7-4-2-3-3-1, améthyste
4-7-6-3-2-3-3-1, paladium 4-7-6-3-2-2-2-1, endium idem sans pelle. **Bugs corrigés** : une pièce
qui ne rendait rien brûlait tout le combustible, et le contenu était perdu à la casse.

### Entonnoir en paladium — `TileEntityPaladiumHopper`

Toutes les 8 ticks, prend **la pile entière** dans l'inventaire du dessus. Une chaîne
d'entonnoirs en paladium envoie directement au dernier inventaire. **Écarts** : l'original se
bloquait sur la première pile qui ne rentrait pas ; ici on essaie les suivantes. Et sans
inventaire au-dessus, il aspire les objets au sol, dans la zone de l'entonnoir vanilla —
l'original n'aspirait rien, et paraissait en panne sous un tas d'objets (retour en jeu du
2026-11-09). Il respecte ce qu'une machine accepte de lâcher (les particules d'un cobblebreaker).

### Coffres — `ModdedChestContainer`, `BlockPaladiumEnderChest`, Iron Chests

- **Améthyste, titane, paladium, paladium vert, endium** : 108 cases (12 × 9), fer, dureté 12,
  pioche en fer. Recettes du relevé de PalaMod.
- **Coffre en or** (Iron Chests, demandé par le gestionnaire d'enclos) : 81 cases (9 × 9), dureté 3.
  Le coffre en fer n'est pas porté : huit lingots d'or autour d'un coffre en fer deviennent quatre
  d'or et quatre de fer autour d'un coffre vanilla.
- **Coffre de l'End en paladium** : le coffre de l'End du joueur, le même que le vanilla, avec une
  quatrième rangée gardée à part sur le joueur (PalaMod réglait 4 ou 5 rangées par permission ; le
  défaut est porté). Un coffre de l'End seul le fabrique ; sans Toucher de soie, 8 obsidiennes.
- **Écart** : les cases d'anneaux de l'original ne sont pas portées, et le couvercle ne s'ouvre pas
  (modèle JSON statique tiré du `ModelChest`, pas de rendu animé).

### Cave block et cave block en endium

Le cave block se déclare opaque avec une texture trouée : le moteur ne dessine pas les faces
collées à lui, et l'on voit les grottes à travers — un x-ray volontaire, gardé tel quel. Le
cave block en endium montre en plus **les conteneurs vus à travers lui** (contour bleu à travers
les murs). L'original le dessinait côté client ; ici c'est un shulker invisible et lumineux
envoyé par paquets au seul joueur concerné, coloré par une équipe que lui seul connaît. Couleur
d'équipe bleue au lieu de `#2537FB`.

### Potion de dureté, God Villager, XP convertie

- **Potion de dureté** : 8 minutes d'effet. Sous l'effet, une obsidienne récoltée sans toucher
  de soie donne 4 XP de PalaJobs. PalaMod ne la fabrique pas (récompense de jetons) : shop et
  créatif.
- **God Villager** : une chance sur 500 000 000 par tick de joueur Mineur 95. Un échange :
  64 blocs de paladium contre une pépite d'endium, puis il disparaît et donne 15 000 XP de
  PalaJobs. **Écart d'apparence** : un marchand ambulant vanilla nommé, pas l'entité d'origine.
- Les gains d'XP codés en dur sont des XP de PalaJobs, **convertis au rapport 0,357** (§ 1) :
  4 → 1,43 (partie fractionnaire tirée au sort), 15 000 → environ 5 350.

### Minerai de pépites d'endium — `BlockEndiumNuggetOre`, `BaseWorldGenerator`

Dureté 5, résistance de la bedrock, une seule pépite sans fortune ni toucher de soie ; le casser
demande Mineur 100 (`#palamod:break_locked`). Il ne naissait que dans la dimension du Mineur,
portée au chapitre 15 (§ 18) : il n'existe plus que là, à son taux d'origine — quatre tirages à
1 sur 900 (`rand(3 × rareté du paladium vert)`), un chunk sur 225, y −64 à −54. Un filon de 3
ramené à un bloc (le code effaçait les autres), d'où `replace_single_block`. Du chapitre 8 au
chapitre 15 il naissait aussi au fond de l'overworld, à 1 chance sur 56 par chunk : les chunks
générés pendant cette période en gardent.

---

## 12. Fermier — les objets débloqués (chapitre 9)

Relevé dans `FarmerRegisterer` (verrous), `BaseItemAxe` et `BlockAngelicWater`. Niveaux du code ×5.

| Niveau | Objet | Wiki | Porté |
|---|---|---|---|
| 10 / 25 / 65 / 85 / 100 | Houes à rayon | 2 / 5 / 13 / 17 / 20 | déjà au chapitre 7 |
| 15 / 30 / 75 / 90 / 100 | Seedplanters | 3 / 6 / 15 / 18 / 20 | déjà au chapitre 5a |
| 35 / 60 / 80 / 100 | Cultures | 7 / 12 / 16 / 20 | déjà au chapitre 3 |
| 40 | Crusher | 8 | déjà au chapitre 4 |
| 45 / 55 | Graines d'herbe colorée, herbe colorée | 9 / 11 | **non, écartées par le joueur** |
| 50 | Graine de racinier | 10 | **non, écartée par le joueur** |
| 70 | Totem de fertilité | 14 | déjà au chapitre 5a |
| 95 | Trône du fermier | 19 | **non — aucun trône n'est porté, à la demande du joueur** |
| 100 | Hache en endium (abattage), plastron en endium | 20 | oui (le plastron l'était au chapitre 7) |

Les herbes colorées et le racinier ont été portés puis **retirés à la demande du joueur** après
lecture du résultat. Leurs relevés restent valables pour qui voudrait y revenir : dix couleurs
en métas, une graine qui change la terre labourée **humide** en herbe teinte ; un arbre de trois
bûches dont la racine descend en **une seule colonne** jusqu'à la bedrock ou l'obsidienne (pas
l'obsidienne renforcée), recette à base de **bûches** de bois mort. Leurs verrous restent dans
`data/jobs.json`, inertes.

### Hache en endium — `BaseItemAxe.onBlockDestroyed`, `breakTree`

Une bûche cassée abat toutes les bûches qui la touchent, par les 26 voisines, jusqu'à 15 pas,
sans limite de nombre ; les feuilles restent et pourrissent d'elles-mêmes. **Une usure par
arbre.** L'usure est plafonnée : à bout, la hache ne fait plus qu'abattre, elle coupe et frappe
toujours (seule l'épée en endium refusait vraiment de servir). Plafond un point sous le maximum,
comme la pioche en endium. **Écart** : chaque bûche passe par la casse d'un joueur, donc
protections, drops normaux et suivi des blocs posés la voient ; l'original ne voyait que la
première. Une bûche est ce que `#minecraft:logs` contient.

### Eau angélique — `PRegister_Fluids`, `BlockAngelicWater`, `ItemBucketBase`

Pas un objet du Fermier, mais l'ingrédient de sa graine de racinier, et demandée par le joueur.
- Un fluide de type eau (`Material.water`) : on y nage, on s'y noie, il éteint le feu et
  humidifie la terre labourée. Densité 200, sans source infinie.
- **Un joueur dedans regagne un demi-cœur toutes les 40 ticks** s'il est blessé, le rythme d'une
  régénération I. **Écart** : l'original gardait ce compteur sur le bloc, partagé par tous les
  joueurs ; il est ici par joueur.
- Le seau, par un : potion de régénération sur un seau d'eau (le seau d'eau rend son seau vide).
  La potion est reconnue par un ingrédient à composants de NeoForge.
- **Physique de l'eau** : le type de fluide doit être déclaré `isWaterLike(true)`. Sans ce
  drapeau, 26.2 ne compte pas le fluide comme de l'eau (`Entity.updateFluidInteraction` ne suit
  que ces types) : le joueur le traversait comme de l'air et le soin ne se déclenchait jamais —
  trouvé au premier test en jeu. Le soin lit maintenant le fluide aux pieds ou aux yeux du
  joueur, sans dépendre de ce suivi.
- Rendu : en 26.2 un fluide se dessine par un **modèle de fluide** (`RegisterFluidModelsEvent`,
  `FluidModel.Unbaked`), plus par les textures de son type. Textures animées de PalaMod, sans
  teinte.
- Pas porté : ses usages dans le chaudron de l'alchimiste, les réservoirs, le pistolet à
  réservoir et les familiers — ils viendront avec leurs modules.

## 13. Chasseur — les objets débloqués (chapitre 10)

Relevé dans `HunterRegisterer` (verrous), `ItemPaladiumFishingRod`, `ItemCaptureSword`,
`ItemCaptureStone`, `ItemCavernHammer`, `SpawnerEventHandler`, `SpawnerManager`, et pour le
spawner sur le wiki (« spawner customisé »). Niveaux du code ×5. Périmètre choisi par le joueur.

| Niveau | Objet | Porté |
|---|---|---|
| 20 | Canne à pêche en paladium | oui |
| 25 | Marteau de caverne | oui |
| 30 | Spawner vide (spawner customisé) | oui, avec les âmes et les paliers |
| 65 / 70 | Épée de capture, pierre de capture | oui |
| 100 | Épée en endium (ne frappe plus à bout) | oui (l'épée l'était au chapitre 7) |
| — | Spawner cassé | oui (pas de verrou dans l'original) |
| 10 / 15 / 35 / 40–100 | Bateau en bambou, cage et familiers, mini golem, structures et contrôleur de spawner, détecteur, améliorations, clé, étiquette, totem, autel, boussole, poudres | **non, écartés par le joueur** |
| 95 | Trône du chasseur | **non — aucun trône** |

Les verrous non portés restent dans `data/jobs.json`, inertes.

### Canne à pêche en paladium — `ItemPaladiumFishingRod`

Durabilité 128, enchantable. Dans l'original elle ne pêchait pas plus vite : elle changeait les
chances du mini-jeu de pêche de Paladium, qui n'existe pas ici. **Écart** : elle donne **+3 de
chance** en main (le bonus de *Chance de la mer* III), ce qui améliore le butin de la même façon.

### Épée en endium — `onLeftClickEntity`

À un point de durabilité, elle ne s'use plus et **refuse de frapper** (l'attaque est annulée) ;
elle reste réparable. Même plafond que la pioche et la hache en endium.

### Épée et pierre de capture — `ItemCaptureSword`, `ItemCaptureStone`

- Tuer une créature avec l'épée (durabilité 32) l'**enferme** dans la dernière pierre de capture
  vide de l'inventaire : pas de butin, pas d'XP. Les joueurs ne se capturent pas. Sans pierre
  vide, ou si la créature n'est pas capturable, elle disparaît sans rien laisser : c'est le
  comportement d'origine. Une créature de pile n'en emporte qu'une.
- Créatures capturables (liste de l'original, traduite) : cochon, vache, mouton, slime, zombie,
  zombie villageois, creeper, poulet, lapin, tortue, panda, dauphin, perroquet, chèvre.
- Clic droit avec une pierre pleine : la créature réapparaît sur la face visée, **avec son
  nom, son âge, sa couleur et ses autres données**, soignée. **Écart** : l'original ne gardait
  que le type ; garder les données est la seule façon de ne pas perdre une bête nommée.
- **Recette substituée** : l'épée demandait des objets non portés ; ici bâtons et épées en
  paladium autour d'un paladium compressé.

### Marteau de caverne et âmes — `ItemCavernHammer`, `SpawnerEventHandler`

- Casser au marteau un **spawner de donjon** de zombie, squelette, araignée, araignée venimeuse,
  slime ou sorcière ajoute **une âme** au premier spawner vide de cette créature qui a de la place
  dans l'inventaire, sinon au dernier spawner vide sans créature. Sans spawner où la mettre, la
  casse est refusée (un message le dit) : l'âme serait perdue.
- Casser un spawner de donjon donne 1 250 XP de Chasseur (converties) à partir du niveau 40.
- **Écarts** : sans marteau, un spawner de donjon se casse normalement ; un spawner customisé
  posé ne donne jamais d'âme (on taperait sans fin le sien).
- **Recette substituée** : blocs de titane, obsidienne et pioche en paladium.

### Spawner cassé

Chaque spawner de donjon pris dans l'explosion d'un creeper a une chance sur deux de lâcher un
spawner cassé. Il sert à fabriquer le spawner vide ; **recette substituée** (le gardien de
pierre, non porté, est remplacé par un cœur de paladium).

### Spawner customisé — wiki, `SpawnerManager`

- Les âmes s'accumulent jusqu'à **250**. Paliers : **100 / 150 / 200 / 250 âmes → I à IV**.
  Sous 100 (palier 0), le spawner posé reste **vide**. La cage prend la couleur du palier
  (améthyste, titane, paladium, endium) ; l'objet aussi, avec le palier et les âmes en info-bulle.
- Posé, il **fonctionne comme un spawner vanilla** (délai, portée du joueur, lumière), à la
  demande du joueur. Dans PalaMod, sans contrôleur, il ne faisait rien.
- **Les créatures s'empilent** (demande du joueur, contre le lag) : une vague rejoint la pile de
  sa créature à 16 blocs du spawner et y ajoute 4, au lieu d'apparaître ; le nombre est affiché
  au-dessus (« x7 »). **Plafond par palier : 3 / 6 / 9 / 12.** Tuer une pile n'en tue **qu'une** :
  son butin et son XP, puis une créature neuve reprend la pile, moins une.
- Cassé (n'importe quel outil), il se rend **avec ses âmes**, sans XP ; deux spawners de la même
  créature se fusionnent à l'enclume, âmes additionnées jusqu'à 250, pour total / 10 niveaux.
- **Piège trouvé au bytecode** : annuler l'apparition (`FinalizeSpawnEvent`) laisse le spawner
  vanilla réessayer à chaque tick sans relancer son délai. La vague est donc refusée à l'entrée
  dans le monde (`EntityJoinLevelEvent`) : `BaseSpawner.serverTick` relance alors son délai
  normal, que l'ajout échoue ou réussisse.

## 14. Alchimiste — les objets débloqués (chapitre 11)

Relevé dans `PAlchimiste` (recettes du chaudron et des portails), `CraftMod`, `TileEntityExtractor`,
`BlockTank`, `TileEntityCauldronCore`, `TileEntityAlchemistPortalController`,
`BlockPortalAlchemist`, `EnchantMod`, et pour les arbres dans `PWorld`, `WorldTree1Gen` / `2Gen`,
`BaseWorldGenerator`. Niveaux du code ×5. Périmètre choisi par le joueur : arbres et sève,
chaudron, portails, enchantements mystiques.

| Niveau | Objet | Porté |
|---|---|---|
| 10 / 35 / 50 / 80 | Tanks en or, améthyste, titane, paladium | oui (sève seulement) |
| 20 | Bloc et cœur du chaudron | oui |
| 25 / 40 / 55 / 100 | Bois brillants (jacaranda, judée, érable, ostrya) | oui |
| 30 / 45 / 60 / 100 | Blocs et clés de portail (améthyste, titane, paladium, endium) | oui |
| 100 | Bloc d'angle, cœur et pollen d'endium | oui |
| 100 | Jambières et bottes en endium | déjà au chapitre 7 |
| 15 / 40 | Table d'alchimiste, totem fleuri | **non, écartés par le joueur** |
| 75 / 90 | Améliorations de spawner | **non** (sans contrôleur de spawner) |
| 95 | Trône de l'alchimiste | **non — aucun trône** |

Sans verrou : fiole, extracteur, potion de foudre, fleurs, pollens, bibliothèque mystique, arbres.

### Les arbres — `PWorld`, `WorldTreeNGen`

- Quatre essences (jacaranda, judée, érable, ostrya) et le bois mort : bûche, planches, feuilles,
  pousse. Bûches et planches durent 3 (plus que le bois vanilla), **ni inflammables ni dans
  `#planks`** : une planche de PalaMod ne fait ni bâtons ni établi. Une bûche donne quatre
  planches. Bûches et planches brûlent 300 ticks au four (bois de 1.7.10).
- Les **générateurs sont des patrons figés**, communs aux quatre essences : 30 bûches et 97
  feuilles (11 de haut) deux fois sur trois, 35 et 129 (12 de haut) sinon ; les patrons 3 et 4
  ne sont jamais tirés. Coordonnées recopiées du bytecode (`PalaTreeFeature`).
- Les **feuilles ne pourrissent pas** (un bloc simple) : 1,01 % de pousse, la feuille à la
  cisaille ou au toucher de soie. La pousse suit la croissance vanilla.
- XP : 10 par bûche de jacaranda, judée ou érable coupée (aucune pour l'ostrya), converties.
- **Écart : génération activée.** L'original ne générait ces arbres que sur le serveur de minage
  (`Server Minage`, désactivé par défaut) : sans eux, rien de l'Alchimiste ne s'obtient. Rythme
  d'origine : un essai par essence et par chunk, sur de l'herbe, 9 % ; **divisé par deux à la
  demande du joueur** (1/22 ici), tout l'overworld.
- **Écart** : l'arbre de l'original écrasait tout dans son empreinte ; ici il ne remplace que
  l'air, les plantes, l'eau et les feuilles.

### La sève — `TileEntityExtractor`, `TileEntityWood`, `BlockTank`

- **Unité** : une fiole pleine = 1 000 unités = un seau. Une fiole remplie de n sixièmes vaut
  `(int)(n / 6 × 100) × 10` : 160, 330, 500, 660, 830, 1 000. Fioles par 16.
- **Extracteur** (recette : 2) : posé, il vise le bloc dans la direction du regard. Une fiole
  vide dedans, devant une bûche à sève : un sixième **toutes les 11 ticks**. La bûche devient
  une bûche entamée de **36 sixièmes (6 fioles), qui ne se régénère jamais** ; vidée, du bois
  mort ; cassée, du bois mort (sa sève est perdue). 144 sixièmes par extracteur (24 fioles),
  puis il se brise. L'objet garde ses utilisations restantes. La fiole se voit au pied de
  l'extracteur, à son niveau de remplissage (propriété d'état `flask`, pas de rendu dynamique). Accroupi, clic droit : la fiole
  ressort, avec l'XP d'extraction — 60 / 120 / 240 par fiole pleine (jacaranda dès 0, judée dès
  25, érable dès 50), **rien pour l'ostrya**, comme l'original.
- **Tanks** : 35 / 60 / 95 / 135 fioles. Clic droit avec des fioles : elles s'y vident (une
  fiole entière ou rien) ; accroupi : chaque fiole de la pile gagne un sixième (160 ou 170
  unités) ; main vide : le contenu. L'objet garde son contenu.
- **Écarts** : le remplissage était au clic gauche (il casse le bloc ici) ; une fiole vide perd
  son type de sève (l'original le gardait, et la fiole ne s'empilait plus) ; un tank ne remplit
  plus une fiole avec la sève d'un autre arbre ; l'extracteur usé disparaît (il se rechargeait à la
  palamachine : elle recharge ici un extracteur entamé, § 20) ; les tanks ne prennent que la sève (eau, lave et eau sulfurique ne
  servaient qu'au pistolet à tank, non porté).

### Le chaudron — `BlockCauldronCore`, `TileEntityCauldronCore`

- **Forme** : 64 blocs du chaudron — un plancher de 7 × 7 et un anneau de 5 × 5 au-dessus —
  puis le cœur posé au centre du plancher, en dernier : la foudre tombe et le chaudron est
  formé. **Écart** : le coin (+2, +2) de l'anneau n'était pas vérifié ; un clic droit main vide
  sur le cœur refait la vérification.
- **Bassin** : un seau d'eau angélique **jeté** dans le bassin vide le remplit d'un coup (le seau
  est consommé). Un bassin entamé — une source puisée, un seau versé à la main — fait exploser
  le chaudron : le cœur saute, le bassin devient de la lave.
- **Travail** : bassin plein, un joueur à 4,5 blocs qui a le niveau du cœur. Tout objet jeté est
  traité au tick, le résultat ressort au-dessus du centre :
  - potion de foudre + bûche : **2 bois brillants** de l'essence — leur seule source ;
  - ~~poudre lumineuse : 3 pépites d'or~~ — **retirée au chapitre économique** (§ 19) : la poudre
    s'achète 4 $ et les pépites se revendent 27 $, de l'argent infini ;
  - coquelicot et ses cousins, pissenlit : par fleur, 1/500 fleur minérale, 1/300 fleur de
    paladium, 1/5 000 harpagophytum, 1/15 000 fleur d'endium, 1/1 754 clathrus ; XP 0,75 à 2
    par fleur dès Alchimiste 15 ;
  - fleur de paladium : un lingot de paladium (XP 100 dès 80) ;
  - fleur d'endium (Alchimiste 100) : un pollen d'endium, plus 25 % paladium, 15 % titane,
    10 % améthyste, 0,2 % pépite d'endium ;
  - pollens : titane → 1 bloc de titane, améthyste → 2 blocs d'améthyste, paladium → 5 lingots.
- Intégrité vérifiée toutes les 11 ticks de travail. Aucune XP pour les recettes du chaudron :
  l'original déclarait une XP qu'il ne versait jamais.
- **Écart** : une fleur d'endium jetée sans le niveau reste là ; l'original la consommait pour rien.
- **Potion de foudre** : sous la pluie, un casque en fer ou en or, une fiole d'eau vide en main —
  1/800 par tick (≈ 40 s) — la foudre tombe et la fiole devient potion ; 30 XP. **Écart** :
  il faut être sous la pluie, pas seulement qu'il pleuve quelque part.
- **Fleurs** : six fleurs dans les plaines, les collines venteuses et les forêts. L'original
  tirait une hauteur entre 50 et 74 qui devait tomber pile sur le sol ; le rythme effectif est
  gardé : une fleur de paladium tous les 125 chunks, d'endium tous les 2 500, etc.
- **Ajout** : l'original ne disait rien quand un objet jeté ne donnait rien, et le chaudron
  semblait cassé. Un message dans la barre d'action, une fois par objet jeté : bassin à remplir
  d'abord (seau d'eau angélique jeté), bûche ou potion de foudre manquante, objet sans recette,
  niveau d'Alchimiste insuffisant. Ce que le chaudron produit et qui retombe n'est pas signalé.

### Les portails — `TileEntityAlchemistPortalController`, `BlockPortalAlchemist`

- **Ce ne sont pas des portails qui mènent ailleurs** : des autels. Un anneau de 5 × 5 à plat,
  le bloc de portail au milieu d'un côté, les quatre coins dans le bloc du métal (le bloc
  d'angle pour l'endium), le reste en bois brillant de la sève. Clé insérée : le trou de 3 × 3
  s'emplit de la surface du portail ; main vide, la clé ressort.
- On y jette des fioles (ou des tanks) de la bonne sève ; elle s'accumule **dans la clé**, qui la
  garde. Par palier : améthyste 1 000 → 1 lingot (XP 6 dès 15), titane 2 000 → 1 lingot (20 dès
  30), paladium 4 000 → 1 lingot (40 dès 50), endium **3 448 000** + 2 pollens d'endium → 1
  pépite (aucune XP) — `sipush 3448` fois mille, porté tel quel.
- **Écarts**, des corrections : fioles et tanks reviennent vides (l'original les détruisait) ;
  sans clé, seule la surface disparaît (l'original vidait le trou de tout, à chaque tick) ; les
  quatre coins sont vérifiés (un ne l'était pas dans une orientation) ; endium : deux pollens par
  pépite toujours (un tank n'en demandait pas), plusieurs pépites par lancer, la sève en trop
  n'est plus détruite, les pollens sont sauvegardés.
- **Ajout** : une fiole vide ou d'une autre sève est refusée avec un message qui nomme la sève
  attendue par ce portail.

### Les enchantements mystiques — `EnchantMod`, `IMixinEnchantTable`

- Bibliothèque mystique (recette : 2) : **puissance 4**. Une table d'enchantement entourée de plus
  de 60 — seize bibliothèques mystiques — ouvre la **table de l'Alchimiste** : elle enchante
  l'objet tenu en main (l'original avait une case), un niveau par clic, **30 niveaux
  d'expérience** chaque fois. Ni à la table vanilla, ni en livre.
- **Anti chute** (bottes, 1 niveau) : on ne tombe plus d'un bord, comme accroupi. Un mixin sur
  `Player.isStayingOnGroundSurface` — le seul point qui décide, sans événement.
- **Grass Breaker** (épées, 3 niveaux, Fermier 20) : les hautes herbes lâchent jusqu'à `niveau`
  graines de plus par graine. **Écart** : l'original le réservait aux épées rapides, non portées.
- Pas portés : le casque d'unclaim (ni unclaim finder ni factions), la protection contre l'eau
  sulfurique (pas d'eau sulfurique), la table « lisible » d'Alchimiste 75.

---

## 15. Grinder, outils du grinder, buisson à XP, pioche des dieux (chapitre 12)

Relevé dans `smeltery` (`PSRegister_Blocks`, `PSRegister_Items`, `PSRegister_Craft`, `BlockGrinder`,
`GrinderLogic`, `GrinderRecipe`, `UpgradeHelper`, `SlotUpgradeGrinder`), `ItemBroadsword`,
`ItemFastsword`, `ItemHammer`, `ToolHandler`, `BlockXpBush`, `ItemXpBerry`, `BaseWorldGenerator`,
`miner.item.ItemGodPickaxe`, `CommandGodPickaxe`. **Aucun verrou de métier** sur tout le chapitre,
ni dans PalaMod ni dans PalaJobs.

### Le multibloc — `BlockGrinder.checkNorth/East/South/West`

- Un cube plein de 3 × 3 × 3 : le **grinder** au milieu d'une face, une **source de lave** au
  centre, un **casing** au centre de chacune des cinq autres faces, des **cadres** sur les vingt
  arêtes et coins. Le cube peut s'étendre de n'importe quel côté horizontal du grinder, son
  orientation n'est pas lue. Coût : 126 lingots de paladium, 40 de titane, un four en paladium.
- Le grinder s'allume quand le cube est complet ; incomplet, il ne s'ouvre pas et le dit en barre
  d'action. **Écart** : PalaMod ne revoyait la structure qu'à l'ouverture, et une machine dont on
  avait cassé le cube continuait de travailler. Ici, elle est revue toutes les secondes et la
  machine s'arrête.

### La machine — `GrinderLogic`

- Six cases : sortie, deux entrées, modificateur, outil, carburant. Réservoir de **100 paladium**,
  rempli toutes les 21 ticks par un lingot (1), un bloc (9), ou une pioche (3), une épée (2), une
  pièce d'armure (5 / 8 / 7 / 4) en paladium au prorata de leur usure. Le paladium du réservoir
  est perdu si on casse le grinder, comme dans PalaMod.
- **Forge** (100 ticks) : 19 recettes. Pattern + base → tête d'outil (coûts : broadsword 4,
  pioche 3, épée 2, fastsword 1, marteau 6, pelle 1, hache 3) ; pattern de lingot ou de bloc +
  base → lingot (1) ou bloc (9) de paladium ; tête + bâton → l'outil en paladium (0 avec un bâton
  en paladium, 1 avec un bâton en bois). Les patterns ne sont jamais consommés.
- **Amélioration** (100 ticks, gratuite) : un modificateur posé sur un outil qui a encore un
  emplacement. Marteau en paladium : fusion (1), fortune (3), vitesse (3) ; broadsword (4
  emplacements) et fastsword (3) en paladium : dommage (3), flamme (1), recul (2) ;
  l'amélioration d'emplacement (2) sur tout outil, +2 emplacements pour un occupé.
- Pas d'accès par entonnoir.

### Les outils — `ItemHammer`, `ItemBroadsword`, `ItemFastsword`

- **Marteaux** (améthyste, titane, paladium) : 3 × 3, neuf fois plus lents qu'une pioche du même
  métal, la vitesse multipliée par (niveau de vitesse + 1). Seul le bloc visé use l'outil.
  Fusion : chaque objet lâché sort cuit. Fortune : l'enchantement vanilla, posé par le grinder.
- **Broadswords** : 7,7 / 8,2 / 9 de dégâts, +2,8 par niveau de dommage. **Fastswords** : 6 / 5 /
  7, +1,5 par niveau ; tenue en main, Célérité IV ; la cible ne reste invulnérable que 18 ticks.
  Flamme : la cible brûle 10 secondes ; recul : un demi-bloc par niveau.
- **Écarts**, des coquilles corrigées : broadsword en titane et fastsword en paladium avaient 20
  de durabilité (portée à 2 000) ; la flamme de la fastsword durait 200 secondes (des ticks pris
  pour des secondes, ramenée à 10).
- Recettes restaurées : le marteau de caverne reprend son marteau en paladium, l'épée de capture
  ses trois têtes d'arme (les quatre bâtons à effet restent des bâtons en paladium).

### Le buisson à XP — `BlockXpBush`, `ItemXpBerry`

- Quatre stades, pousse au hasard sans lumière. Mûr, un clic (ou un coup) le ramène au stade 3 et
  donne une baie. Une baie donne 2 ou 3 points d'XP ; une baie compressée (9 baies), neuf fois
  plus.
- Généré comme un minerai : trois essais par chunk à une chance sur cinq, un filon de trois dans
  la pierre, à la hauteur du titane (−32 à 40).

### La pioche des dieux — `ItemGodPickaxe`

- Craft sans verrou : pioches en améthyste, paladium et titane sur deux bâtons en paladium.
  Incassable, non enchantable ; la barre d'usure montre l'avancement du niveau. 8 de dégâts.
- **1 XP par bloc** plein (ou feuillage) de dureté non nulle ; seuil du niveau n :
  `(int) (80 · n^2,3 / 1,5)`. Le niveau 100 demande 2 074 721 blocs, puis l'XP ne compte plus.
- Paliers : bois (1-5), pierre, fer, diamant, **or** (21-25, plus rapide mais ne récolte plus le
  fer ni le diamant — l'original), améthyste, titane, paladium (36-80), paladium vert (81-100).
  Efficacité I à V de 41 à 65, Fortune I à III de 66 à 80. Vitesse et récolte valent pour tous
  les blocs : pioche, hache et pelle à la fois. Une texture tous les cinq niveaux.
- Au niveau 100 : accroupi + clic droit active ou retire le Toucher de soie ; la **fusion
  automatique** (5 verres autour d'un four en paladium) se pose au grinder, une fois, sans
  paladium. Seul le premier objet lâché compte : cuit, il sort en un exemplaire, 1 à 2 en Fortune
  III sur un minerai. Tout bloc y passe (le sable donne du verre).
- `/godpickaxe create <niveau>` et `/godpickaxe add auto_smelt`, réservés aux opérateurs.
- **Écarts**, des corrections : pile sur un seuil, PalaMod retombait au niveau 1 le temps d'un bloc,
  enchantements effacés ; il réécrivait les enchantements à chaque bloc, Toucher de soie compris ;
  l'amélioration de fusion se perdait sur un marteau ou une épée ; la fusion faisait perdre l'XP
  des minerais. L'amélioration « big hole », lue par aucun code et sans recette, n'est pas portée.

### Ce qui n'est pas porté

- Le jeton de récompense du Mineur 95 (un marteau en paladium avec 3 fortune et 3 vitesse) : les
  récompenses de DoHy sont des objets sans composants.
- Succès, quêtes et événements du grinder (PalaPass, tutoriel), l'événement « Pioche de Légende »
  du lucky block Findium (pas de lucky blocks), le bonus de familier de la pioche.
- La fusion automatique ne crédite pas d'XP de métier : DoHy n'a pas de source « cuisson ».

---

## 16. Golem gardien, boîte à golem, croquettes (chapitre 13)

Relevé dans le mod GuardianGolem 6.1.0 (`fr.welsymc.guardiangolem`, ponté par PalaMod) :
`EntityGolem`, `EntityAIGuardian`, `ModGuardianUpgrades`, `Choices`, `GuardianUtils`,
`TileEntityFoodMachine`, `TileEntityGuardianBox`, `BlockGuardian`, `GuardianKeeperHandler`,
`CraftInit`, `MiniGolemRecipe`, `MiniGolemRepair`. Tout passe sous l'espace `palamod`. Seul verrou de
métier : le mini golem, Chasseur 35 (déjà dans `data/jobs.json`).

### Le golem — `EntityGolem`

- **Éveil** : une pierre de gardien (pierre et paladium compressé) frappée sur un bloc gardien posé
  sur un T renversé de blocs de paladium. Les quatre blocs disparaissent. Pierre vide : un golem
  neuf ; pierre habitée : le golem qu'elle garde. Il appartient à celui qui l'éveille.
- **Chargement** : 30 secondes à mi-vie, immobile, puis la vie remonte. **Écart** : l'original le
  refaisait à chaque rechargement du chunk.
- **Statistiques** : `200 + 125 × niveau` PV, `6 + 0,25 × niveau` de dégâts, une frappe par seconde,
  recul encaissé à moitié. +1 PV par seconde hors combat (5 minutes après le dernier coup reçu).
- **Cibles** : tout joueur à 40 blocs, sauf le propriétaire et la liste blanche ; ce qui le frappe ;
  les golems des autres joueurs. **Écart** : l'original attaquait aussi les golems de son propre
  maître.
- **XP** : il mange lingots et blocs — améthyste 1 / 9, titane 2 / 18, paladium 30 / 270, findium
  70 / 630 (avec Régime novateur). Accroupi, toute la pile. Niveau n → n + 1 : 1 000 XP, puis
  +5 % par niveau ; 120 050 XP de 0 à 40, soit 4 002 lingots de paladium. Un point de capacité
  par niveau.
- **Mort ou marteau** (3 coups, propriétaire ou opérateur) : le stockage tombe, la pierre part au
  **gardien de golem** du propriétaire — neuf cases, ouvertes par n'importe quel bloc gardien, même
  hors ligne. Gardien plein : le golem est perdu. Le golem fou ne revient jamais.
- **Barre de vie** au-dessus de l'écran, à 32 blocs, sauf avec Discret.

### L'arbre — `ModGuardianUpgrades`

Golemancie (1) ouvre trois voies : utilitaire, guerrière, farmeur (0 chacune). Guerrière et farmeur
s'excluent. Coûts et effets du code (les descriptions de l'original se trompaient) :

- Utilitaire : Régime novateur (1), Gourmand (1, XP ×2), Mecagolem (1, sans effet, ouvre la suite),
  Discret (1), Golem en boîte (1), Stockeur (2, 16 cases), Golem de ménage (1, ramasse au sol),
  Wither hunter (2).
- Guerrière : Combattant (5, dégâts 6,6), Berserker (2, +20 PV), Combattant 2 (3, dégâts 7,8),
  Résistance avancée (2), Résistance spécialisée (2), Carnage (3, deux frappes par seconde),
  Accélération (2, +15 % de vitesse), Dernier souffle (3), Golem fou (10, encaisse 60 % de moins,
  attaque tout le monde, plus d'interface), Magicien (2), Désynchronisation (3, chaque coup frappe
  aussi le joueur le plus proche).
- Farmeur : Pacification (1, ne bouge ni ne se bat), Régime industriel (1), Ferero-, Amétho-, Titano-,
  Palado-créateur (2, 2, 2, 3), Bouclier (2, aucun dégât d'explosion).
- Les **choix** : withers (les chasser et frapper les monstres ×1,3, ou être ignoré d'eux), résistance
  avancée (coups ×0,8 et projectiles ×1,33, ou l'inverse ×0,7 / ×1,2), résistance spécialisée
  (explosions, feu, magie, noyade), dernier souffle à 10 % de vie (Force IV 5 s, Régénération III
  10 s, Faiblesse III et cécité II à l'attaquant, Wither II à l'attaquant), magicien toutes les
  29 s (repousser, Lenteur III, Nausée II, enflammer).
- **Écarts**, des corrections : Wither hunter choix 2 rendait le golem passif ; la Désynchronisation
  pouvait frapper le maître ; le magicien enflammait 100 secondes (5 dans sa description) ; la
  résistance spécialisée 4 doublait la magie (sa description dit les liquides : la noyade).
- **Pas portés**, parce qu'ils ne font rien dans GuardianGolem ou demandent un service externe :
  Lanceur d'alerte (webhook Discord), Démembrement (`earthquake()` vide), Démultiplication 1 à 3
  (aucune limite par chunk), Auto-crafter (jamais dans l'arbre).

### Les croquettes et leur machine — `TileEntityFoodMachine`

- Seule source de croquettes. Lave au seau : 2 unités le seau, 100 au plus, 0,05 la fournée.
  Trois cases (fibres, minéraux, protéines), un de chaque, 7 ticks : **en-cas** 5 (deux parmi
  pastèque / citrouille / canne à sucre, pierre / pierre taillée, chair putréfiée), **goûter** 8
  (deux parmi verrue / pomme de terre / carotte, gravier, viande crue ou œuf, plus un ingrédient
  d'en-cas), **gros repas** 16 (un parmi pomme / blé, pierre moussue, poisson cru, plus un de
  goûter). Quarante fournées par seau.
- **Écarts** : l'original consommait sans lave, pouvait consommer trois fois pour une fournée,
  versait dans une case qui ne contenait pas de croquettes et oubliait la fournée au redémarrage.
- Recette : deux **pommes en paladium** (portées ici : huit lingots et une pomme), findium,
  deux seaux d'eau, paladium compressé, deux obsidiennes, une étoile du Nether.
- Une croquette donnée à un golem créateur rend 10 fer, 5 améthyste, 3 titane ou 1 paladium (le
  meilleur créateur qu'il a), une fois toutes les 8 minutes.

### La boîte à golem — `TileEntityGuardianBox`

- Un cube de 3 × 3 × 3 : 25 cadres, le bloc de boîte au centre d'une face latérale, une source
  d'**eau angélique** au centre.
- Trois mini golems (pierre habitée entre trois blocs de paladium, avec Golem en boîte) ; toutes
  les 8 minutes, chacun mange une croquette et rend le métal choisi — accroupi, main vide, clic
  droit sur la boîte —, au plus le meilleur créateur commun. 144 productions par mini golem ; la
  clé le répare (il redevient une pierre, usure remise à neuf).
- Posé au sol, un mini golem redevient un golem.
- **Écarts** : la structure est vérifiée pour travailler (plus seulement pour ouvrir), le métal ne
  déborde plus dans les cases de croquettes, les mini golems se retirent comme des objets.

### Ce qui n'est pas porté

- Le renommeur (une étiquette vanilla nomme le golem), les cosmétiques (chapeaux, particules,
  skins : lucky blocks et calendrier de l'Avent), le livre de recettes de la machine, le bonus de
  familier (+5 croquettes).
- `/guardianadmin`, `/goleminfos`, `/guardiankeeper`. Restent `/whitelistgolem add|remove` et
  `/guardiangolem <niveau>` (opérateurs, pour tester).
- L'arbre est un coffre de six rangées (une colonne de trois cases par voie), pas l'image défilante
  de l'original.

## 17. Monture : le Dancarok et l'enclos (chapitre 14)

Relevé dans le mod Mount 6.1.0 (`fr.paladium.mount`), croisé avec PalaMod (capture, croquettes,
recettes) et Lindworm (l'animation). Tout passe sous l'espace `palamod`. Seul le Dancarok est porté :
le Ravirok et le Tedarok, les deux autres espèces, ne le sont pas. Aucun verrou de métier sur les
objets de monture ; l'épée et la pierre de capture gardent les leurs (Chasseur 65 et 70).

### Le cycle — `MountManager`, `CommandMount`

1. **Capture** : un Dancarok sauvage tué à l'épée de capture, une pierre de capture vide sur soi,
   donne une **gemme sauvage** (grise). La pierre est consommée, pas remplie.
2. **Apprivoiser** : la gemme déposée dans un gestionnaire d'enclos devient la vôtre (dorée),
   nourriture pleine, vie pleine.
3. **Équiper** : la monture passe de la gemme au joueur. Sa vie, son énergie, son XP et son
   inventaire vivent **sur le joueur** ; l'entité invoquée n'en est qu'une projection.
4. **Invoquer** : touche **K** (menu de la monture) puis Invoquer, ou `/mount summon` ; trois
   secondes immobile. Il faut le **niveau 10** (`>= 10`), de la vie et de l'énergie. En selle, la
   même action désinvoque.
5. **Déséquiper** dans l'enclos : une gemme neuve y revient ; l'inventaire et la corne restent au
   joueur, pour la monture suivante.

Une gemme sortie de l'enclos **périme en 30 jours** de temps réel, définitivement ; redéposée, son
horloge s'arrête.

### Le Dancarok — `EntityDancarokMount`, `EntityMount`

- 2,5 × 1,9. **Sauvage** : 53 PV, immobile (il regarde les joueurs, rien d'autre), ne disparaît
  jamais, ne se monte pas. Il apparaît, rarement, dans les plaines et les savanes : **aucun code
  d'apparition dans les jars**, les sauvages étaient posés à la main sur le serveur « Farmland » ;
  la règle d'apparition est donc un ajout.
- **Niveaux** : 1 à 100, seuil du niveau L = somme de `i^2,5` tronquée pas à pas (749 XP au niveau
  10, plafond 2 907 301). Seule source : les orbes d'XP ramassées, partagées selon le réglage
  (50 % par défaut, `/mount xp <0-100>` ou le menu) — que la monture soit invoquée ou non.
  L'orbe est réduite à la part du joueur avant Raccommodage, comme l'original la réécrivait.
- **Vie** : 4 PV par niveau (40 au niveau 10, 400 au 100). Tous les coups reçus vont à cette vie,
  **doublés si elle est montée** ; le cavalier ne blesse pas sa monture. À zéro : son inventaire
  accessible et sa corne tombent, elle est désinvoquée. Pas de régénération : seuls les soins de
  l'enclos la rendent. Pas de dégâts de chute.
- **Inventaire** : `max(1, 36 × niveau / 100)` cases (3 au niveau 10, 36 au 100) et une case de
  corne, par le menu ou la touche d'inventaire en selle.
- **Conduite** : vitesse 0,225 (près de 10 blocs/s), une marche d'un bloc, un saut fixe d'environ
  deux tiers de bloc, marche arrière au quart, de côté à moitié. **Écart** : le saut part au
  relâchement de la touche, comme pour les montures vanilla (Mount sautait tant qu'elle était
  enfoncée). Elle coule, son cavalier reste en
  selle et peut se noyer. Tout coup reçu fait tomber le cavalier. En selle, on ne ramasse ni ne
  jette rien.
- **Énergie** : 10 000 ; −1 par bloc parcouru ; à zéro, désinvoquée. Une **croquette** (machine à
  croquettes, chapitre 13) rend 50.
- **Modèle et animations** : le fichier Bedrock du jar, converti une fois pour toutes en code
  vanilla par `tools/bedrock_model.py` (pas de GeckoLib). Marche (sauvage) ou course (invoquée),
  attente, et le coup de corne. La texture suit la corne posée.

### Les cornes — `ItemHorn`, `CSMountMovement`, `ToolHandler`

- Améthyste, titane, paladium : 3 999, 5 999, 9 999 utilisations. En selle, **clic droit
  maintenu** : un coup toutes les 500 ms au plus, quelle que soit la corne.
- La zone n'est **pas** un 3×3, 4×4, 5×5 : `s` (3, 4, 5) est un rayon, la zone fait
  `(2s+1) × (s+2) × 2` blocs — **7×5×2, 9×6×2, 11×7×2** —, ancrée sur la monture, à partir de ses
  pattes, orientée par la face visée (rayon de 10 blocs depuis ses yeux). Blocs de pioche et de
  pelle, lâchés sans condition d'outil, avec leur XP. **L'obsidienne ne part jamais.** Chaque coup
  coûte `(2s+1)(s+2)` d'énergie : 35, 54, 77.
- **Écarts** : chaque bloc passe par l'événement de casse (protections, verrous, suivi des blocs
  posés — l'original ne l'envoyait jamais) ; les blocs à block entity sont épargnés ; un coup qui ne
  casse rien n'use rien ; faute d'énergie, le coup est refusé plutôt que de creuser la jauge.

### L'enclos — `BlockBarnController`, `TileEntityBarnController`

- Un carré de **foin** de 3×3 à 7×7, un anneau de **fondations d'enclos** chacune surmontée d'une
  **barrière d'enclos**, et le **gestionnaire** posé sur le foin (n'importe où). Une **couveuse**
  sur le foin permet les soins. Vérifié à chaque clic droit, avec le message précis de l'original.
- Capacité : 4, 8, 14, 20, 30 montures de 3×3 à 7×7.
- **Soins** : couveuse, deux montures au plus, un bandage par tranche de 1 h 30 de temps réel ;
  +10 % des PV max par bandage, crédités à la fin. Aucun seuil de 30 % : une monture dont il
  reste de la vie s'équipe.
- La barrière d'enclos ne fait qu'un bloc de haut en collision : le Dancarok la franchit.
- **Écarts** : 30 montures logent vraiment dans un 7×7 (l'original n'avait que 21 cases) ; la
  taille de l'enclos est sauvegardée ; chaque action vérifie que la gemme est à soi ; casser le
  gestionnaire ne soigne plus entièrement toute gemme déjà soignée (seules comptent les tranches
  écoulées des soins en cours) ; une gemme rendue à un inventaire plein tombe au lieu de
  disparaître.

### Recettes

- Relevé de PalaMod : fondation (8 terre cuite grise, 1 bloc de paladium), barrière (4 + 2),
  couveuse (6 terre cuite, 2 verre, 1 orbe de régénération), gestionnaire (6 terre cuite, 2 blocs
  de paladium, 1 **coffre en or**), bandage (6 papier, 2 ficelle, 1 orbe), **orbe de
  régénération** (4 lingots de paladium autour d'une tranche de pastèque scintillante).
- **Cornes** : l'éléphant et sa défense ne sont pas portés. Les cornes se fabriquent à la
  **palamachine** (chapitre 16, § 20), la défense remplacée par un **bloc d'os** : os au centre,
  lingot, bloc, lingot, bloc. Les recettes « bâtons + bloc » sont des **réparations** : la corne
  usée au centre, trois bâtons du métal (celui du milieu devient un cœur en paladium pour la corne
  en paladium) et un bloc ; elle ressort neuve.

### Ce qui n'est pas porté, et ce qui change de forme

- Les quatre écrans plein cadre (accueil, inventaire, nourriture, enclos) deviennent des coffres :
  accueil (K ou `/mount`), inventaire, enclos, soins. Le nom se change par `/mount nom <nom>`
  (4 à 16 caractères). Nourrir : une croquette, ou Maj + clic pour remplir — **écart** : l'original
  consommait toute la pile posée et perdait l'excédent.
- `/mount unequip`, qui détruisait la monture, n'est pas porté. `/mount give [niveau]` (opérateurs)
  donne une gemme apprivoisée ; le niveau est un ajout, pour essayer sans des heures d'orbes.
- **Écart** : rien ne tombe à la mort du joueur (l'original lâchait l'inventaire de la monture
  rangée et gardait celui de la monture invoquée, l'inverse de l'intention).
- Les miniatures de montures au-dessus de l'enclos, l'œuf d'apparition, les permissions
  (multiplicateur Trixium), les protections WorldGuard, le multi-serveur, la compétence
  Vétérinaire du mod Pet, le profil web.

## 18. Dimension du Mineur (chapitre 15)

Relevé dans `modules/miner/dimminer` de PalaMod 10.3.0, avec `BaseWorldGenerator` et
`MinerRegisterer`. Chez Paladium ce n'était **pas une dimension Forge** mais un serveur à part
(« dimminer ») rejoint par BungeeCord : un overworld ordinaire où PalaMod triplait ses minerais. En
solo, c'est une vraie dimension, `palamod:miner`, et l'on y entre et en sort par téléportation.

### Le portail — `BlockWitheredObsidian`, `BlockMinerPortal`

1. **Le cadre** : de l'obsidienne wither (aucune recette : un piston renforcé wither alimenté
   change l'obsidienne voisine, § 11). Un cadre de portail du Nether, intérieur de 2×3 à 21×21.
2. **Le charger** : chaque bloc du cadre reçoit **10 blocs de paladium**, un par clic droit. Il
   s'éclaire d'un cran par bloc ; au dixième, tonnerre et éclair (visuel, sans feu ni dégâts).
3. **L'allumer** : du feu sur le cadre chargé ; il faut que **tout** le cadre soit à 10.
4. **Entrer** : il faut **Mineur 90** (18 × 5 ; l'original testait 18 au portail et 17 au paquet,
   et écrivait 17). Le premier joueur à traverser devient propriétaire du portail ; un autre est
   repoussé. Le portail ouvre un écran (informations, temps et points restants, « Créer » ou
   « Rejoindre la dimension »).

Cassé, un bloc de cadre rend lui-même et, **tant que le portail n'a jamais été ouvert**, ses blocs
de paladium. Un piston ne le pousse pas (immobile comme tout bloc à block entity en 1.7.10). Le
portail se referme si son cadre est incomplet ou sa nappe trouée. Incassable, sans objet.

### La session — `DimMinerPlayer`, `ServerDimMinerEventHandler`

- **1 000 points**, **3 jours de temps réel** (`DURATION`, horloge système). Casser du paladium
  coûte 1 point, du paladium vert 5, une pépite d'endium 501 — une seule par session. Le reste est
  gratuit. Sans assez de points, le minerai ne se casse pas. Un bloc posé par un joueur ne coûte
  rien et ne compte pas.
- Une **section** de 5 000 × 5 000 par session, attribuée en spirale carrée depuis (0, 0) ; on ne
  peut pas en sortir. **Écart** : le compteur des sections est sauvegardé (l'original repartait de
  0 à chaque redémarrage) et la section est gardée toute la session (l'original en donnait une
  neuve, avec sa plateforme, à chaque connexion).
- L'arrivée : un sol libre près du centre (spirale de 20 blocs, 7 × 7 × 6 de place), une
  plateforme 6 × 5 d'obsidienne wither et un cadre. **Écart** : ce cadre était décoratif (le kick
  ramenait au serveur Faction) ; il porte ici le **portail de sortie**. Après onze sections sans sol
  (des océans), une plateforme au niveau de la mer.
- **Dans la dimension** : aucun monstre, pas de dégâts de chute, aucun conteneur posé, l'obsidienne
  wither ne se casse pas. **Écart** : une explosion n'y casse aucun bloc — la TNT abattrait les
  minerais sans payer de points. Une barre en haut de l'écran donne les points et le temps restants
  (`UIDimMinerOverlay`). Le créatif et le spectateur échappent aux règles de pose, de casse et de
  bordure (`bypass`).
- **Sortir** : le portail de l'arrivée, ou `/dimminer quitter`. On ressort devant le portail
  d'entrée ; la session continue. Par la commande, on retrouvera sa place ; par le portail,
  l'arrivée.
- **Fin** : points à zéro ou trois jours écoulés → « Votre dimension mineur va s'effondrer dans 30
  secondes. », plus ni pose ni casse, puis retour devant le portail d'entrée. L'écran de fin
  (blocs minés, temps passé, minerais, XP de Mineur gagnée) s'ouvre, et **chaque bloc du cadre
  d'entrée a une chance sur trois d'être détruit**.
- **Écart** : la mort est évitée — soigné, ramené à l'arrivée. L'original n'avait rien (le serveur
  de repli s'en chargeait) ; sans lui, on réapparaîtrait dans l'overworld avec une session en plan.
- La dimension ne donne pas d'XP propre : l'XP de Mineur ordinaire s'y applique.

### Le monde — `BaseWorldGenerator`

Le terrain de l'overworld (mêmes réglages de bruit, même graine), un seul biome `palamod:miner` :
les plaines sans pluie, sans mobs, sans salle à monstres. Une nuit fixe : ciel et brouillard noirs,
lumière du ciel de la nuit vanilla, brouillard proche (le client de Paladium forçait l'heure à
15 000 et vidait le ciel). Lit et ancre de réapparition sans effet.

Les minerais PalaMod y sont multipliés comme sur le serveur dimminer : `mult = 3`, d'où
`ceil(3 × base) + 1` tentatives au lieu de `base + 1`, rapport appliqué aux comptes de l'overworld
DoHy — améthyste 8 → 21, titane 6 → 16, paladium 4 → 9, paladium vert 2 → 4, findium 3 → 8,
trixium 1 → 3, buisson à XP 3 → 7 essais. Pépite d'endium : un chunk sur 225, et seulement ici.
Les minerais vanilla gardent leur taux.

### Minerai de Pierre d'Invocation — `PWorld.INVOKER_ORE`, `invocker_stone`

Dureté 5, se rend lui-même ; au four, une **Pierre d'Invocation** (1 XP). Couches du findium, filon
de 3 : une chance sur deux par chunk dans l'overworld (0,4 à l'origine), un filon par chunk dans
la dimension. La pierre ne servait qu'aux runes de donjon de PalaRPG, non porté.

### Ce qui n'est pas porté, et ce qui change de forme

- Le **boss** `EntityMinerBoss` : enregistré mais jamais invoqué dans l'original, et refusé par le
  filtre anti-monstres de sa propre dimension.
- L'ambiance du client : éclairs et chauves-souris aléatoires, tremblement de caméra et brouillard
  qui se resserrent avec les points, écran de transition. Il reste un ciel noir et un brouillard
  fixe.
- Les écrans ZephyrUI (entrée, fin) deviennent des coffres ; l'overlay, une barre de boss.
- Les joueurs ne sont pas cachés les uns aux autres. Les permissions, le réseau, les succès et
  `/dimension` (externes) ne sont pas portés.

## 19. Économie : PalaMod dans le marché de DoHy (chapitre 6)

Les objets de PalaMod n'avaient aucun prix : aucun n'est dans les tags `c:` que lisent les règles
de `tag_prices.json`. Ils entrent maintenant au catalogue par `data/rates.csv`, au modèle de DoHy
(`prix = cible du tier / taux horaire`, `BALANCE.md` § 1), dans un sixième onglet du shop,
**PalaMod**. Il a pris la case de la boutique dans le pied du shop ; la boutique reste au menu.

### Ce qui se vend, et ce qui ne se vend pas

La règle de `DESIGN.md` § 3.3 s'applique telle quelle : **seule la matière première se vend**.
Rien de ce qui sort d'une recette ou d'une machine — outils, armures, blocs de stockage,
compressés, charbons mixés, particules, pollens, têtes, sèves, croquettes — n'a de prix, comme
pour les objets fabriqués vanilla. `/dohy scan palamod` en liste donc 241 : c'est voulu.

| Objet | Tier | Taux/h | Prix | Achat | Pourquoi ce taux |
|---|---|---|---|---|---|
| Lingot d'améthyste | FARM | 600 | 30 | 120 | sève de jacaranda (6 par bûche), boîte à golem, aubergine, crusher |
| Lingot de titane | FARM | 400 | 45 | 180 | crusher (15 pour 32 cerfeuils), boîte à golem, sève de judée |
| Lingot de paladium | FARM | 250 | 72 | 290 | crusher, sève d'érable, boîte à golem, kiwano ; dimension du Mineur |
| Lingot de paladium vert | FOUND | 80 | 500 | 2 000 | minage seul |
| Findium | FOUND | 100 | 400 | 1 600 | minage seul |
| Trixium | FOUND | 50 | 800 | 3 200 | minage seul, un filon par chunk |
| Pierre d'Invocation, son minerai | FOUND | 20 | 2 000 | 8 000 | un filon pour deux chunks |
| Pépite d'endium | RARE | 1,5 | 40 000 | 160 000 | une par session de dimension |
| Minerais (pioche soie) | FOUND | 400 → 25 | 100 → 1 600 | ×4 | améthyste, titane, paladium, vert, findium, trixium |
| Aubergine, cerfeuil | FARM | 1 200 | 15 | 60 | comme le blé |
| Kiwano | FARM | 900 | 20 | 80 | pousse à 0,8 |
| Orangeblue | FARM | 60 | 300 | 1 200 | la graine ne revient qu'une fois sur 15 |
| Bûches des quatre essences | FARM | 1 200 | 15 | — | **non achetables** : achetée, la sève d'une bûche de jacaranda vaut 6 lingots |
| Baie d'XP | FARM | 1 000 | 18 | 72 | buisson à XP |

**Les métaux de PalaMod sont renouvelables**, d'où le tier FARM du lingot et FOUND du minerai,
exactement comme le fer vanilla (lingot à 15, minerai à 100) : le lingot sort aussi du crusher,
des cultures, de la sève et des golems ; le minerai ne revient jamais. Les rapports que ces
machines imposent sont respectés : titane ≈ 1,07 améthyste au crusher, paladium ≈ 2,1 titane.
Le paladium vert, le findium et le trixium ne sortent d'aucune machine : FOUND.

Les **fleurs**, les **pousses** et les graines n'ont pas de prix, comme les fleurs et la plupart
des graines vanilla. Une pousse plantée donne un arbre de 30 à 35 bûches, donc 190 lingots
d'améthyste en sève : l'acheter serait une imprimante, la vendre ne sert à rien.

### L'audit des machines

Chaque machine qui produit a été relevée (entrées, sorties, cadence, automatisation) :

| Producteur | Verdict |
|---|---|
| Chaudron, poudre lumineuse → 3 pépites d'or | **Retiré** (écart, décision du joueur). La poudre s'achète 4 $ et les pépites se revendent 27 $ : de l'argent infini, jusqu'à 32 recettes par tick |
| Corne du Dancarok (154 blocs par coup), excavatrices (9), Dollars Stone | Le plancher de 35 % des blocs « trouvables » faisait de chaque bloc de masse une rente. **Plancher de 5 %** pour vingt blocs de masse (sable, ardoise, tuf, granite, netherrack, pierre, cobble, gravier…), sans bonus de plancher des paliers ni du prestige. Le joueur à la main ne le sent pas : il faut vendre des milliers de blocs pour y descendre |
| Cobblebreaker (1,6 diamant/h par machine au palier paladium) | **Gardé** (décision du joueur). Limité par la pierre fournie et la chute du prix du diamant (S0 = 64) ; le diamant reste à 1 000 |
| Boîte à golem (10 fer ou 5 améthyste ou 3 titane ou 1 paladium par croquette) | Une ferme, régulée par le prix dynamique comme toutes les fermes. Fixe le taux des lingots |
| Sève et portails (6 / 3 / 1,5 lingots par bûche) | Une ferme d'arbres, à surveiller : c'est la source d'améthyste la plus rapide. Bûches non achetables |
| Cultures et crusher | Fermes. Les cultures de PalaMod n'étaient pas dans `#dohy:crops` (voir plus bas) |
| Grinder, Palafurnace, Palaforge, pioche des dieux, marteau | Neutres : ni gain de matière ni doublement au-delà de la fortune vanilla |
| Spawner customisé, pierre de capture, aimant, God Villager | Pas une source (âmes finies, piles de mobs qui meurent une à une) |
| Compresseur à cobblestone (100 pierres → 1 obsidienne) | Une perte : 100 pierres valent plus qu'une obsidienne |
| Dimension du Mineur | 1 000 points par session : au plus 1 000 minerais de paladium, une pépite d'endium |

**Achat → conversion → revente**, vérifié pour chaque objet achetable : un minerai acheté puis
cassé à Fortune III rend 2,2 lingots (améthyste 400 $ → 66 $, paladium 1 100 $ → 158 $) ; 32
aubergines achetées (1 920 $) font 5 améthystes au crusher (150 $) ; 720 cobbles (8 640 $) font
1,6 diamant au cobblebreaker ; 100 pierres (1 200 $) une obsidienne. Aucune boucle. Les
croquettes de la boîte à golem coûtent presque rien, achetées ou cultivées : c'est une ferme, pas
une boucle — le débit tient aux mini golems (une production toutes les 8 minutes).

### Ce qui change ailleurs

- **Les cultures de PalaMod comptent enfin.** Absentes de `#dohy:crops`, celles plantées à la main
  étaient des blocs posés : ni XP de Fermier, ni collection, ni contrat de récolte. Seules celles
  du seedplanter comptaient.
- **Les bonus de récolte ne copient plus leurs graines** (`#dohy:replant_seeds`) : avec le seul tag
  vanilla, un champ de kiwano grossissait tout seul.
- **`#c:ores`** reçoit les huit minerais de PalaMod : la chasse aux minerais hebdomadaire les compte.
- **Contrats** : trois quotidiens (48 minerais d'améthyste, 16 de paladium, 150 récoltes PalaMod)
  et un hebdomadaire (400 minerais de PalaMod).
- **Musée** : une aile PalaMod, 23 pièces (la pépite d'endium, RARE, va au panneau mécène). Elle
  rapporte **+2 points d'armure**.
- Les récompenses de niveau de PalaJobs pèsent maintenant : 1 050 lingots de paladium (≈ 76 k) et
  autant de trixium (≈ 840 k) par métier maxé.

## 20. Pour le PvP : sticks, anneaux, deltaplane, palamachine (chapitre 16)

Relevé dans `ItemStickBase`, `BaseItemRing(Endium)`, `ItemRepairableArmor`, `ARepairable`,
`BaseItemPickaxe`, `ModdedChestLogic`, `EndiumChestLogic`, `ItemHangGlider`,
`PaladiumMachineLogic`, `PaladiumMachineRecipies`, `PRegister_Craft`, `BaseBlockSlimePad` et
`BlockSpike`. Le wiki (« Java – Pour le PvP ») sert de contrôle ; là où il contredit le code,
c'est le code qui est porté, et l'écart est noté.

### La palamachine — `PaladiumMachineLogic`

Un bloc (fer, dureté 12, pioche en fer), cinq entrées et une sortie, **200 ticks** par
fabrication, sans combustible. Les cinq entrées sont positionnelles : la pièce au centre, les
quatre ingrédients aux coins. L'usure de la pièce centrale est ignorée — c'est ce qui permet de
réparer. Ni entonnoir ni conduit. Recette à l'établi : 8 pierres autour d'un paladium compressé.

- Le résultat **garde les enchantements de la pièce centrale**. Le wiki dit qu'un anneau réparé
  perd les siens ; le code les recopie.
- Un stick compte ses réparations (« Réparation : n / 5 ») ; la machine refuse la sixième.
- **Écarts** : la machine attend quand la sortie contient un autre objet (l'original consommait
  les entrées et perdait le résultat) ; la première réparation n'est plus gratuite et un stick ne
  lègue plus son compteur au stick divin qu'il sert à fabriquer.
- Portées : 12 recettes d'anneaux, 14 de sticks, 6 de cornes de monture, et la recharge de
  l'extracteur (un extracteur entamé, 3 lingots de paladium et un seau d'eau : il ressort à 144
  utilisations). La défense d'éléphant devient un **bloc d'os** ; les anciens substituts des
  cornes à l'établi sont retirés. Laissées : détecteurs de claim, runes de donjon (non portés).

| Création | Pièce centrale | Ingrédients (1, 2, 3, 4) |
|---|---|---|
| Stick de régénération | orbe de régénération | bâton de paladium, bloc de paladium, bâton de paladium, bloc de paladium |
| Stick de vitesse | orbe de vitesse | bâton d'améthyste, améthyste compressée, bâton de titane, titane compressé |
| Stick de force | orbe de force | bâton de paladium, bloc de paladium, bâton de titane, bloc de paladium |
| Stick de saut | orbe de saut | bâton de titane, slimepad, bâton de titane, slimepad |
| Bâton divin | stick de force | stick de régénération, bloc de paladium, stick de vitesse, findium |
| Stick de super saut | orbe de saut | stick de saut, bloc de paladium, stick de saut, bloc de paladium |
| Stick de dommage | épée en titane | bâton d'améthyste, spike en paladium, bâton d'améthyste, spike en paladium |
| Small Ring | bloc de paladium | 4 lingots de paladium |
| Medium Ring | Small Ring | bloc, lingot, bloc, lingot de paladium |
| Big Ring | Medium Ring | paladium compressé, 3 lingots de paladium |
| Small Ring en endium | Medium Ring | 4 fragments d'endium |
| Medium Ring en endium | Small Ring en endium | bloc de paladium, bloc de findium, bloc de paladium, pépite d'endium |
| Big Ring en endium | Medium Ring en endium | paladium compressé, pépite, paladium compressé, pépite |

Les réparations ont chacune leur recette (`PalamachineRecipes`). À l'établi : orbe de force
(4 lingots de paladium autour d'une poudre de blaze), orbe de saut (autour d'une verrue du Nether),
aile (×2 : 3 bâtons de paladium, 3 cuirs), deltaplane (2 ailes, 2 cuirs), slimepad (6 boules de
slime), spike en paladium (3 épées en titane sur 3 lingots de paladium).

### Les sticks — `ItemStickBase`

Clic droit : un effet, un point d'usure, une recharge. **La recharge est par type et par joueur**
(`timer-<type>` sur le joueur) : tous les sticks identiques la partagent. Elle court en temps réel,
survit à la déconnexion et à la mort, et s'affiche sur la barre d'action. Seul Solidité
s'applique, à l'enclume.

| Stick | Durabilité | Recharge | Effet (amplificateurs du code) |
|---|---|---|---|
| Régénération | 15 | 10 s | +6 points de vie |
| Vitesse | 15 | 15 s | vitesse VII, 3,5 s |
| Force | 15 | 15 s | force III, 3,5 s |
| Saut | 15 | 30 s | +1 de vitesse verticale (environ 5 blocs) |
| Bâton divin | 8 | 25 s | régénération VI 4 s, 6 points d'absorption, saturation II, vitesse IX 4 s, force III 5 s |
| Dommage | 16 | 30 s | repousse de 0,7 et soulève de 0,7 tout ce qui vit dans un cube de 3 blocs |
| Super saut | 8 | 40 s | +2,365 de vitesse verticale (environ 25 blocs) |

Le wiki annonce vitesse III et des recharges un peu plus courtes : ce sont les valeurs du code qui
sont portées. **Écarts** : l'absorption passe par l'effet Absorption II de deux minutes (en 26.2,
6 points d'absorption sans effet seraient ramenés à 0) ; le stick de dommage respecte le PvP du
serveur et les équipes (l'original laissait les modules de protection l'annuler) ; son
« tremblement de terre » de blocs projetés est rendu par des particules.

### Les anneaux — `BaseItemRing`, `IRepairable`

400, 1 000 ou 3 200 de durabilité. **Dans l'inventaire**, un anneau répare l'armure portée d'un
point par pièce et par tick, et s'use d'autant. **Dans un coffre**, posé dans la colonne
d'anneaux, il répare d'un point par tick chaque pièce abîmée des premières rangées.

| Pièce | Où | Anneau | Capacité |
|---|---|---|---|
| Armure paladium, paladium vert, endium mixé | portée ; coffre | paladium | casque 8 000, plastron 12 000, jambières 10 000, bottes 7 000 |
| Armure endium | portée ; coffre en endium | endium | illimitée |
| Épée, pioche, hache, pelle en paladium | coffre seulement | paladium | 3 remises à neuf |
| Épée, pioche, hache en endium | coffre en endium seulement | endium | illimitée |

Portée, la capacité se consomme point par point ; au coffre, par remise à neuf complète. Les
outils en améthyste, titane et paladium vert ne se réparent pas. La capacité restante s'affiche
une fois entamée (ajout de DoHy).

| Coffre | Rangées réparées | Anneaux |
|---|---|---|
| Améthyste | 1 | 1 |
| Titane | 1 | 2 |
| Paladium | 2 | 4 |
| Paladium vert | 6 | 4 |
| Endium | 9 (toutes) | 4, en endium, et ne répare que l'endium |

La colonne d'anneaux est à droite de la grille ; l'original la posait à cheval sur le bord du
panneau, qui s'élargit ici. Les entonnoirs n'y ont pas accès. Un filet de la couleur du métal
marque les rangées réparées.

### Le deltaplane — `ItemHangGlider`

Clic droit pour le déployer ou le replier ; déployé, il brille. Tenu en main et déployé, tant que
le joueur tombe : vitesse verticale × 0,6 et +0,03 vers l'avant ; accroupi, × 0,8 et +0,1. La chute
ne compte pas. Il se replie seul au sol : on le déploie donc en l'air (au sol, il se replie au
tick suivant, comme dans l'original). La physique tourne chez le client (le mouvement d'un
joueur y est calculé), l'état et l'annulation de la chute sur le serveur. Sans durabilité. Pas
porté : le modèle tenu au-dessus de la tête.

### Slimepad et spike

- **Slimepad** (`BaseBlockSlimePad`) : on le traverse ; il pousse de 0,2 vers le haut et de 0,2
  dans la direction du regard au moment de la pose (huit directions, l'inverse accroupi), annule
  la chute, et ne fait rien sous redstone. Pas porté : la recharge de la Warzone, les couleurs au
  ciseau.
- **Spike en paladium** (`BlockSpike`) : posé contre la face cliquée, il inflige 15 points
  (source cactus) à ce qui s'y appuie. Résistance 60. Seul le spike en paladium est porté : c'est
  l'ingrédient du stick de dommage.

## 21. Pierres légendaires et gant en endium (chapitre 17)

Relevé dans `LegendaryStone`, `LegendaryStone$Effect`, `ItemEndiumGauntlet`,
`EndiumGauntletRecipe`, `UpgradeHelper`, `GrinderLogic` et `PRegister_Craft`. Le wiki
(« Java – Les legendary stones ») sert de contrôle.

### Les pierres — `LegendaryStone`

Clic droit : l'effet, puis **24 heures** de recharge en temps réel, notée sur la pierre (elle la
suit si on la donne). Prête, elle brille. Les pierres ne se consomment pas. **Téléportation et
chaos** ont en plus un verrou de **10 minutes par joueur**, commun à toutes leurs pierres (le wiki
dit 1 h pour le chaos : c'est l'infobulle, le serveur vérifie 10 min). Le verrou d'une heure des
quatre autres n'était jamais écrit dans l'original : on peut enchaîner deux pierres différentes.

| Pierre | Effet |
|---|---|
| Aléatoire | s'ouvre : elle disparaît et donne l'une des six autres, prête à servir |
| Téléportation | un point au hasard à moins de 25 000 blocs de l'origine, jusqu'à 11 essais ; la recharge part même en cas d'échec |
| Invisibilité | invisibilité II, 2 minutes, à tous les joueurs d'un cube de 3 blocs autour du lanceur, lui compris |
| Fortune | 2 à 25 blocs jetés devant le joueur : charbon, fer, or, diamant, améthyste, titane (15 % chacun), paladium (10 %) |
| Pouvoir | **résistance au feu, 30 minutes** (voir les écarts) |
| Métier | 5 000 à 20 000 points d'XP d'un métier au hasard, bonus du joueur compris |
| Chaos | l'inventaire principal de chaque autre joueur à 10 blocs ou moins est battu (20 échanges de cases) |

**Écarts.**
- **Pouvoir** : l'original rendait immunisé 5 minutes à l'eau sulfurique (la « fake water » des
  raids), qui n'est pas portée. Remplacée, au choix du joueur, par 30 minutes de résistance au feu.
- **Téléportation** : seulement dans l'Overworld (Nether : le toit ; End : le vide ; dimension du
  Mineur : ses sections) — ailleurs, la pierre refuse sans consommer sa recharge. On atterrit à la
  surface (l'original montait depuis y = 63 jusqu'au premier bloc d'air, parfois dans une
  grotte) ; un point est refusé hors de la bordure du monde, sur la bedrock, sur la lave ou dans
  la protection du spawn.
- **Chaos** : sur le fil du serveur (l'original lançait un fil à part) et seulement sur les joueurs
  que le lanceur pourrait frapper (PvP, équipes).
- Le verrou de 10 minutes survit à la mort (l'original le perdait, ce qui l'offrait à une mort
  volontaire).
- **Pierre aléatoire** : la pierre tirée remplace l'aléatoire dans la main, comme un seau rempli
  (en créatif, elle s'ajoute à l'inventaire). Retirer puis ajouter ne marche pas en 26.2 : le jeu
  remettait la pile consommée dans la case où la pierre venait d'arriver, et elle disparaissait
  (retour en jeu du 2026-11-09).

### Le gant en endium — `ItemEndiumGauntlet`

- **À l'établi** : une pierre (sauf l'aléatoire) entre deux lingots d'endium, un cœur en paladium
  dessus et dessous. Le gant naît avec cette pierre et sa recharge. La recette ne prend jamais un
  gant existant.
- **Au grinder** : pierre dans la case du modificateur, gant dans celle de l'outil ; 100 ticks,
  sans paladium. Une pierre de chaque type au plus. Le gant garde la recharge la plus récente.
- **Accroupi, clic droit** : la pierre suivante (dans l'ordre téléportation, invisibilité, fortune,
  pouvoir, métier, chaos). **Clic droit** : l'effet de la pierre sélectionnée, avec sa propre
  recharge dans ce gant — **12 heures** au lieu de 24 quand il porte les six.
- Son nom dit la pierre sélectionnée, « Endium Gauntlet (Chaos) » ; l'infobulle liste les pierres
  et la recharge de chacune.
- **Écart** : complet, il devient l'**Infinity Gauntlet**, nom et icône dorée. L'original ne les
  prenait que si l'on renommait le gant ainsi à l'enclume.

### Obtention — au choix du joueur

Les lucky blocks et les événements de Paladium n'existent pas ici. La pierre aléatoire est dans la
**caisse rare** (poids 4 sur 104) et la **caisse légendaire** (10 sur 112), le gant complet dans la
**caisse légendaire** (2 sur 112). Une entrée de caisse accepte maintenant des composants
(`components`), ce qui permet le gant garni.

### Ce qui n'est pas porté

La pierre légendaire corrompue (événement de novembre), les orbes du chaos (8 par pierre de
chaos), les modules de compétences de familier qui raccourcissaient les recharges, la fiche de
profil (`/legendarystone`), les particules complètes de `LengendaryStoneFx` (une double hélice de
la couleur de la pierre les remplace).

## 22. Donjons : le cœur jouable (chapitre 18)

Les donjons sont de la V12 de Paladium, postérieurs à la 10.3.0 de PalaMod : **aucun code
d'origine**. La source est le wiki (« Univers des donjons » et les quatre pages « Donjon … »),
qui donne les règles, les noms et les récompenses, mais presque aucun chiffre. Tout ce qui
manque est de DoHy et noté comme tel. Ce chapitre est le premier de trois : 18 cœur jouable ;
19 équipement, essences et notes D à S ; 20 runes et arbre de compétences. Les sorts et le mana,
prévus d'abord, sont laissés de côté au choix du joueur.

### Les quatre donjons

| Donjon | Métier | Thème des salles | Monstres (mêlée, distance, mage, volant, assassin) |
|---|---|---|---|
| Talikus | Fermier | boue tassée, épicéa, champilampes | zombie villageois, squelette, sorcière, blaze, araignée |
| Nimbria | Mineur | ardoise des abîmes, fer brut | zombie, vagabond, évocateur, blaze, araignée venimeuse |
| Vitalys | Chasseur | pierre moussue, jungle, os | zombie momifié, squelette des marais, sorcière, blaze, araignée |
| Manelios | Alchimiste | prismarine, lanternes marines | noyé, noyé au trident, évocateur, blaze, araignée |

### Entrée — la clé fait le hub

Sur Paladium, on entrait par un hub dédié. Ici, **clic droit avec la clé** : un écran de coffre
de 100 paliers (45 par page). Vert : réussi, rejouable ; jaune : le suivant ; gris : verrouillé.
Les paliers en 5 et en 0 portent un crâne. Il faut réussir un palier pour ouvrir le suivant
(wiki). **Le groupe** : le chef et jusqu'à trois joueurs à 6 blocs de lui (« solo ou jusqu'à 4 »).
Seul le chef a besoin d'une clé (wiki), **consommée à l'entrée** (choix du joueur).

**Clés** (wiki : paladium vert, invoker stone, stick en paladium, lingot de paladium et
l'ingrédient du métier ; la grille n'est que dans les images) : pierre d'invocation en haut,
sticks en paladium de part et d'autre de l'ingrédient (blé, cobblestone, chair putréfiée,
coquelicot), lingots de paladium et lingot de paladium vert en bas. Icône : la clé en paladium
de PalaMod, teintée.

### Un palier

Une dimension vide, `palamod:dungeon` (monde plat sans couche, sans ciel ni apparition
naturelle), une **région par partie** (1 024 blocs d'écart, compteur gardé sur l'overworld,
jamais réutilisée). Les salles sont **construites par le code** dans les blocs du thème, puis
effacées ; tant qu'une salle existe, elle tient ses chunks chargés.

- **Trois salles.** La première est un **combat** (wiki : « obligatoire »). À la fin d'une
  salle, des **portes** s'ouvrent dans le mur du fond : **une à trois** au choix (wiki) entre
  combat, énigme, boost et marchande, chacune vitrée de sa couleur sous un panneau. On choisit en
  y avançant. La troisième salle d'un palier en 5 est celle du **mini-boss**, d'un palier en 0
  celle du **boss**. Après la troisième, la porte de **sortie** : le palier est réussi.
- **Combat** : 2 vagues, 3 à partir du palier 50 ; `3 + palier / 25` monstres par vague, **2 de
  plus par joueur** au-delà du premier (« plus de joueurs, plus d'ennemis »).
- **Énigme** : 3 à 5 boutons sur les murs, à trouver et presser.
- **Boost** : un autel (table d'enchantement) propose 3 bonus tirés parmi force I, résistance I,
  régénération I, vitesse I, absorption II, vie supplémentaire I, résistance au feu ; un par
  joueur, 10 minutes, retirés à la fin du palier.
- **Marchande** : une villageoise immobile vend en argent DoHy, prix × `(1 + palier / 50)` —
  pomme dorée 1 500, steaks ×16 300, potion de soin II jetable 800, régénération 600, flèches ×32
  200, perles ×4 400, totem 20 000.

### Monstres

Les six types du wiki, des monstres vanilla du thème, nommés (« Guerrier de Talikus »…), colorés
selon la tranche du palier — **Jaune** 1–25, **Orange** 26–50, **Rouge** 51–75, **Noire** 76–100
(le wiki en fait des variantes de difficulté, sans chiffres).

| Type | Vie au palier 1 | Dès le palier | Rôle |
|---|---|---|---|
| Mêlée | 22 | 1 | fonce ; **à Manelios, deux petits noyés à sa mort** (wiki : « se démultiplie ») |
| Distance | 20 | 1 | tire en gardant ses distances |
| Volant | 20 | 5 | blaze |
| Mage | 30 | 10 | immobile, **aucun dégât** (wiki) : toutes les 3 s, force et vitesse aux monstres à 10 blocs, ou lenteur et faiblesse aux joueurs, en alternance |
| Assassin | 18 | 10 | araignée qui bondit |
| Tank | 60 | 20 | ×1,35 de taille, armure 8, recul presque nul |

Vie × `1 + 0,06 (palier − 1)` (×6,9 au palier 100), dégâts × `1 + 0,03 (palier − 1)`
(×4 au palier 100), y compris leurs flèches et boules de feu. Ils ne lâchent rien (le butin est
dans les coffres) et ne se battent pas entre eux. Un monstre né d'un autre (vex, cavalier)
rejoint la salle.

### Mini-boss et boss

Le wiki en nomme cinq de chaque par donjon, sans dire où ils apparaissent ; la table des
récompenses de boss va de 5 en 5. **Mini-boss aux paliers en 5, boss aux paliers en 0**, à tour
de rôle (palier 5 : le mini-boss V11, 15 : le premier de la V12…). Chacun est un monstre vanilla
agrandi, nommé, avec deux ou trois capacités toutes les 3,5 à 7 s : ruée, onde de choc, renforts,
salve de boules de feu, attraction, téléportation dans le dos, cercle de feu, enracinement.
Deux mini-boss sont des **duos** (wiki : Nimbria et Manelios), chacun à 60 % de la vie.

Vie : `120` (mini-boss) ou `250` (boss) × `1 + 0,05 (palier − 1)` × `1 + 0,6 (joueurs − 1)`.
Au-delà du plafond vanilla de 1 024 points, le boss encaisse moins de dégâts dans la même
proportion. Barre de boss.

### Coffres et sac

À la fin d'une salle de combat, d'énigme ou de boss, **un coffre au centre, individuel** (wiki :
« chacun ses récompenses, personne ne peut voler celles d'un autre »). Le butin va dans le
**sac**, livré **à la fin du palier** (wiki) ; un coffre laissé est ramassé en partant.

| Sac | Contenance |
|---|---|
| sans sac | 9 piles |
| Sac de donjon en cuir (cuir, coffre, entonnoir, invoker stone) | 18 |
| Sac de donjon tier 2 (Nimbria, paliers 40 et 50) | 27 |
| Sac de donjon tier 3 (Nimbria, paliers 60 et 70) | 36 |

Le meilleur sac de l'inventaire à l'entrée fixe la contenance ; ce qui dépasse est perdu.
`/donjon sac` le montre.

**Butin** (wiki, cumulatif : tout ce qui est débloqué au palier ou avant). Le wiki ne donne
**aucun taux ni aucune quantité** : les poids et les quantités sont de DoHy.
- un coffre tire `2 + palier / 50` fois dans la réserve du donjon ; quantités × `1 + palier / 50` ;
- un coffre de boss tire en plus 1 fois (2 après un boss) dans la **table des boss** : armures en
  paladium P4U3, épées S5U3F2 et S5U3L3, pioche E5U3, blocs, paladium vert, invoker stone,
  bouteilles d'XP de métier (1 000), fragment d'endium ; puis l'armure d'endium mixte P4U3, l'étoile
  du Nether, la tête de wither, la pépite d'endium, la pierre légendaire aléatoire et la pioche des
  dieux niveau 60, aux paliers du tableau du wiki ;
- les **récompenses uniques** du palier exact tombent une fois sur deux, à la sortie. Seuls les
  sacs de Nimbria existent pour l'instant.

**Venus aux chapitres suivants** : épées et armures de donjon, essences et pièces antiques (§ 23),
runes (§ 24). **Non portés** : parchemins de sort (sorts sautés) et de craft. **Absents de DoHy**, donc ignorés : obsidienne
infusée, têtes de thirsty et de prédator, totem fleuri, bonbons, jetpack, défense d'éléphant,
pièce de lune, double XP, têtes de wither passive et suprême.

### Fin du palier

- **Réussite** : sac livré (ou jeté au sol s'il ne tient pas), XP du métier du donjon
  `600 + 40 × palier` (wiki : « XP Métier » sans chiffre), palier noté, retour au point d'entrée.
- **Échec** (choix du joueur) : la **mort** fait sortir — soigné, ramené, **inventaire gardé**,
  sac perdu, clé non rendue. De même `/donjon quitter` et la déconnexion. Sans plus personne
  dedans, le palier est échoué et la région effacée.
- Les parties ne vivent qu'en mémoire : un arrêt du serveur ramène tout le monde, sans rien
  livrer ; qui se reconnecte dans la dimension sans partie y est renvoyé à son entrée.

### Règles de la dimension

Rien ne s'y casse ni ne s'y pose (sauf en créatif), les explosions et les monstres n'y dégradent
rien, rien ne s'y jette (l'objet revient dans l'inventaire), aucun bloc à interface ne s'y ouvre
hors du coffre et de l'autel. Un joueur sorti de sa salle (perle, chorus, toit) y est ramené.

### Bouteilles d'XP de métier

`job_bottle_farmer/miner/hunter/alchemist` (textures de PalaMod) : 1 000 XP du métier, clic droit
pour une, accroupi pour la pile ; l'XP passe par les bonus du joueur.

### Vérifié au démarrage

Par un auto-test temporaire (hors de la PR) : construction des cinq salles des quatre donjons,
six types de monstres et leurs variantes, petits noyés, quarante boss créés, boss au plafond de
vie, boutons de l'énigme qui ouvrent les portes, coffres, tirages de butin aux paliers 1 à 100,
déroulé complet d'un palier jusqu'à la sortie et effacement de la région, chunks libérés. Le jeu
avec un joueur (portes, coffres, menus, mort) reste à éprouver en jeu.

## 23. Donjons : équipement, essences, notes D à S (chapitre 19)

Deuxième chapitre des donjons. Le wiki donne les tiers, les paliers d'obtention et la règle de
réparation, et **aucune statistique**. Choix du joueur : un équipement fort **surtout en donjon**,
les textures de **Palamod Recreated**, et un **coffre selon la note**.

### L'équipement — trois armures et trois épées par donjon

| Tier (wiki) | Hors donjon, comme | Épée | En donjon, par pièce d'armure | Épée en donjon |
|---|---|---|---|---|
| Basique (tier 1) | améthyste | 7 de dégâts | −4 % des dégâts des monstres | +25 % |
| Renforcée (tier 2) | paladium | 10 | −7 % | +50 % |
| Légendaire (tier 3) | endium mixte | 10,6 | −10 % | +80 % |
| Antique (palier 100) | endium | — | −12 % | — |

- Les valeurs hors donjon reprennent les valeurs d'origine du métal correspondant, converties
  comme les armures de PalaMod (§ 10) ; durabilités 170, 260, 350 et 400.
- Le bonus de donjon ne joue que dans `palamod:dungeon`, contre ses monstres : **à plein dans son
  propre donjon, à moitié dans les trois autres**. L'armure plafonne à **−60 %** au total.
  L'épée compte pour les coups portés à la main.
- **Set antique** (wiki : une pièce par donjon au palier 100 — plastron à Talikus, bottes à
  Nimbria, casque à Vitalys, pantalon à Manelios) : bonus à plein dans tous les donjons et, complet,
  **−10 %** de dégâts reçus et **+20 %** de dégâts en plus. Hors des donjons, vol et invisibilité,
  et une armure en 3D : voir § 25.

### Obtention — les récompenses uniques du wiki

À la sortie du palier exact, une chance sur deux chacune (comme au chapitre 18) :

| Palier | Récompense |
|---|---|
| 5, 15 | épée basique (Nimbria : pas au 15) |
| 10 à 20 | une pièce d'armure basique au hasard |
| 35, 45 | épée renforcée (Nimbria : pas au 35) |
| 40 à 50 | une pièce d'armure renforcée au hasard |
| 65, 75 | épée légendaire |
| 70 à 80 | une pièce d'armure légendaire au hasard |
| 100 | la pièce antique du donjon |

Les paliers se rejouent : on complète un set en rejouant sa tranche. Les parchemins de craft du
même tableau ne sont pas portés.

### Essences et réparation

Wiki : « Fragment d'essence » répare l'armure basique, « Essence » la renforcée, « Essence
concentrée » la légendaire, et une essence ne répare que son tier. Les textures de Palamod Recreated
donnent un **élément par donjon** : forêt (Talikus), caverne (Nimbria), flamme (Vitalys), aquatique
(Manelios). Une essence répare donc, **à l'enclume**, l'équipement de son donjon et de son tier
(25 % de durabilité par essence, comme un lingot) ; l'antique prend n'importe quelle essence
concentrée. Le wiki ne cite qu'une « Essence de tier 2 » (Talikus, palier 40) : toutes les réserves
de coffre en ont, à partir du palier de l'équipement qu'elles réparent (10, 40, 70).

### La note — D à S

Wiki : une note par joueur à la fin du palier, selon les dégâts, la survie et l'implication ; une
meilleure note ouvre un meilleur coffre. Seules D et S sont nommées. Calcul de DoHy :

`0,35 × dégâts + 0,45 × survie + 0,20 × implication`, chaque part de 0 à 1 :
- **dégâts** : sa part des dégâts infligés aux monstres, rapportée à une part égale (seul : 1) ;
- **survie** : `1 − vie perdue / (40 × salles de combat terminées)` ;
- **implication** : sa part des monstres tués, rapportée à une part égale (seul : 1, ou 0,5 sans
  aucun tué).

| Note | Score | Coffre de la note |
|---|---|---|
| S | ≥ 0,90 | 4 tirages, 50 % d'essence |
| A | ≥ 0,75 | 3 tirages, 35 % |
| B | ≥ 0,60 | 2 tirages, 20 % |
| C | ≥ 0,40 | 1 tirage, 10 % |
| D | sinon | rien |

Les tirages viennent de la réserve du donjon ; l'essence est du tier du palier. Le coffre de la
note est livré avec le sac, hors de sa contenance. La note s'affiche au titre et dans le chat avec
ses trois parts.

### Textures

Armures, couches portées, épées et essences : **Palamod Recreated** (SILWOX / Solea Pixel, mod de
fans 1.21, « All Rights Reserved »), au choix du joueur — crédité dans le README, à ne pas
redistribuer publiquement sans leur accord. Leurs statistiques (des valeurs MCreator identiques
pour les quatre donjons) ne sont pas reprises.

## 24. Donjons : runes et arbre de compétences (chapitre 20)

Troisième et dernier chapitre des donjons. **Les sorts et le mana sont laissés de côté**, au choix
du joueur (et les parchemins de sort ne tombent pas). Deux autres choix du joueur : les statistiques
RPG ne jouent **qu'en donjon**, contre ses monstres, et les métiers donnent **1 point tous les 5
niveaux** (le wiki dit 1 par niveau).

### Statistiques RPG

Le wiki nomme quatre branches sans les définir. Ici :

| Statistique | Effet (en donjon, contre ses monstres) | Par nœud | Plafond (arbre + runes) |
|---|---|---|---|
| Attaque | dégâts infligés en plus | +4 % | 100 % |
| Esquive | chance d'éviter entièrement un coup | +2 % | 35 % |
| Critique | chance d'un coup à 150 % | +3 % | 60 % |
| Défense | dégâts reçus en moins | +3 % | 50 % |

Elles s'ajoutent à l'équipement de donjon (§ 23), par multiplication : armure de donjon puis
défense RPG.

### Niveau RPG et points de compétence

- **XP RPG** (wiki : chaque salle terminée) : `10 + 2 × palier` par salle de combat ou d'énigme, le
  triple pour une salle de boss.
- **Niveau** 1 à 100 : du niveau N au suivant, `50 + 20 N` XP — 103 950 au total pour le niveau 100.
- **Points** (wiki) : 1 par niveau RPG (100), 1 tous les 5 paliers réussis **de chaque donjon**
  (80), 1 tous les 5 niveaux de chaque métier (80). **260 au plus**, le prix de l'arbre entier.
  Les points ne sont pas stockés : ils se recalculent.

### L'arbre — « Profil RPG »

Wiki : depuis l'inventaire, bouton « Profil RPG » ; à gauche les statistiques (onglets Perso et
Global), à droite l'arbre, dont on achète les cases puis « Valider » ; « Reset » annule ce qui
n'est pas validé. Ici :
- un **bouton « Profil RPG »** au-dessus de l'inventaire, **`/profil`**, et une vignette du menu
  `/menu` ;
- un écran de coffre : une rangée par branche, **8 nœuds** à acheter dans l'ordre (coûts 2, 4, 5,
  7, 9, 11, 12, 15 — 65 par branche) ; un clic réserve le nœud suivant, un clic sur le dernier
  réservé le rend ; **Valider** achète, **Reset** annule les réserves ;
- tuiles **Perso** (l'arbre seul) et **Global** (arbre, runes de l'armure portée, équipement de
  donjon) ;
- **ajout** : « Réinitialiser l'arbre » rend tous les points validés contre **25 000 $** (le wiki
  ne dit pas si un choix validé se défait).

### Les runes

| Objet | Origine |
|---|---|
| Fragment de rune commune | coffres, dès le palier 10 |
| Fragment de rune rare | coffres, dès 25 (Talikus, Nimbria) ou 30 (Vitalys, Manelios) |
| Rune commune | coffres dès 40 (Vitalys : 50) ; palamachine : 4 fragments communs + 1 lingot de paladium |
| Rune rare | coffres dès 50 ; palamachine : 4 fragments rares + 1 bloc de paladium |
| Rune épique | coffres dès 60 (seulement dans les exemples du wiki) ; palamachine : 3 runes rares + 2 fragments d'endium |

- **Statistiques** (wiki : deux au hasard, une seule possible pour les communes) : parmi les quatre.
  Une ligne vaut `base × rareté × (1 + 0,5 × (tier − 1 + bonus))` — base attaque 2 %, esquive 1 %,
  critique et défense 1,5 % ; rareté commune ×1, rare ×1,5, épique ×2. Une rune épique de tier 4
  bien fusionnée donne ainsi jusqu'à 16 % d'attaque.
- **Rareté et tier** : le wiki les mélange ; ici, deux axes. La rareté fixe les lignes, le tier
  (1 à 4) les fait monter.
- **Amélioration, à l'enclume** (wiki) : une rune et une autre de même rareté et de même tier →
  tier suivant, pour **10, 15 puis 20 niveaux**. Les lignes qu'elle partage avec la rune sacrifiée
  gagnent un cran de bonus (wiki : « elle fusionne les statistiques qu'elles partagent »), les
  autres restent.
- **Fusion, au grinder** (wiki) : rune dans la case du modificateur, pièce d'armure de donjon dans
  celle de l'outil. **Une rune par pièce, pour toujours.** Coût en paladium du réservoir : casque 5,
  plastron 8, jambières 7, bottes 4. Seule l'armure de donjon (et l'antique) en prend.
- Une rune donnée par commande, sans statistiques, se révèle d'un clic droit. Textures : aucune
  n'existait, une tablette gravée dessinée pour l'occasion.

### Ce qui n'est pas porté

Les sorts et le mana, au choix du joueur ; les parchemins de sort et de craft.

## 25. Le stuff antique

Demande du joueur : « les bonnes textures et les bons effets ». **Le wiki ne décrit pas l'armure
antique** : il ne la nomme que comme récompense unique du palier 100 (Plastron antique à Talikus,
Botte Antique à Nimbria, Casque Antique à Vitalys, Pantalon Antique à Manelios). Les seules traces
de ses effets sont dans les patchnotes de Paladium V11 :
- 11.0.0.1 : « correction de la description de l'effet d'invisibilité sur l'armure antique » ;
- 11.0.2.1 : « correction de pouvoir voler en retirant l'armure antique » ;
- 11.0.4.1 : « correction de l'affichage de l'armure antique » (elle a un rendu à part).

### Effets (choix du joueur)

- **Set complet, hors des donjons : vol et invisibilité.** Le vol est donné tant que les quatre
  pièces sont portées ; il est retiré dès qu'une manque (le bogue de la 11.0.2.1), et qui volait
  reçoit 5 s de chute lente. L'invisibilité est entretenue sans particules ; une potion
  d'invisibilité bue à côté n'est pas touchée. Le vol n'est retiré qu'à qui l'a reçu de l'armure
  (créatif et spectateur gardent le leur).
- **En donjon** : pas de vol (il casserait les salles), le bonus de donjon du § 23 — −12 % par
  pièce, et au set complet −10 % de dégâts reçus et +20 % de dégâts.
- Statistiques hors donjon et réparation inchangées (§ 23 : comme l'endium, essence concentrée).

### Textures et modèle

La seule armure antique dessinée est l'« Ancient armor » de **Palamod Recreated** (SILWOX /
Solea Pixel), déjà source des textures de donjon au choix du joueur :
- **icônes** : leurs rendus 3D (256 à 512 px), recadrés et ramenés à **32 × 32** ;
- **armure portée en 3D** : leurs quatre modèles Java (`Modelancien_helmet` et suivants), traduits
  de leur bytecode par `tools/model2java.py` sans retoucher les cubes, sur leur texture de
  256 × 256. NeoForge remplace le modèle d'armure plat (`IClientItemExtensions`) et recopie les
  poses du joueur sur le modèle 3D. Les couches plates au bronze ne restent qu'en secours ; pas de
  garniture sur l'antique.

Leurs pièces « avancées » (fusionnées avec des pierres légendaires), leur boîte et leur clé
antiques et leur marteau antique sont leurs propres inventions, pas reprises.

---

## 26. Concours Trixium (chapitre 23)

Le chapitre 23 réunit le **concours Trixium** (lot 23a) et les **succès** (lot 23b). Les
**lucky blocks** (module `luckyblock`, 2 008 classes, le plus gros morceau de PalaMod) sont
**écartés, au choix du joueur** : ni les blocs, ni leurs événements, ni leurs objets, ni le
Lucky Stats. Les objets que PalaMod ne donnait que par eux restent absents.

Sources : le module `trixium` de PalaMod, le wiki (« Concours Trixium », « Objets exclusifs ») et
deux articles du blog de Paladium (« Le Classement Trixium est de retour », « Rétrospective de
la V8 »).

### Ce que dit le code

- **Dépôt** : `/trixium` ouvre l'écran ; un emplacement n'accepte que le trixium et le bloc de
  trixium (`TrixiumSlot.isItemValid`). Un bloc compte **9** (`onSlotChanged`). Le dépôt part à
  l'API de Paladium : il ne revient jamais.
- **Barre de paliers** : chaque palier est `(uuid, value, big, image, commands)`
  (`TrixiumRewardConfig`) ; atteint, il se récupère par un clic. **Les seuils et les récompenses
  sont dans la configuration du serveur, pas dans le jar.**
- **Classement** : joueurs et factions, avec le profil de chacun. Le blog précise que le
  classement des factions est **pour information**, sans récompense.
- **Saisons** : la V8 a duré 206 jours (7 juillet 2022 – 29 janvier 2023). Le top 60 était
  récompensé par tranches : 1er, 2e, 3e, 4e, 5–10, 11–20, 21–30, 31–40, 41–60 (lots réels,
  grades, Lucky Pass, cosmétiques).

### En solo (choix du joueur)

- **Une saison par mois réel**, du 1er à 5 h au 1er suivant à 5 h, heure du serveur (la bascule à
  5 h est celle des contrats). La barre repart de zéro à chaque saison.
- **59 joueurs fictifs** (`TrixiumRivals`) déposent au fil du mois, le soir, à des heures tirées
  d'avance : tout se déduit du numéro de la saison, rien n'est sauvegardé. Leurs totaux de fin de
  saison suivent **2 500 × rang^−0,62**, à 15 % près (chiffre de moi) : environ 2 500 pour la
  1re place, 600 pour la 10e, 300 pour la 30e. Au rythme du minage (50 trixium par heure de
  minage pur), le podium demande un mois tourné vers le trixium.
- **Fin de saison** : à la connexion suivante, ou dans la minute pour un joueur présent, le rang
  final donne sa prime. Les paliers atteints mais pas récupérés sont envoyés d'office (comme le
  Palapass). Il faut avoir déposé au moins un trixium pour être classé.
- **Factions** : les joueurs fictifs se répartissent entre huit factions fictives (celles des
  events) ; ta faction y figure avec ton dépôt. Pour information, comme sur Paladium.

| Palier | Récompense | | Rang | Prime |
|---|---|---|---|---|
| 25 | 20 000 $ | | 1er | 2 000 000 $ |
| 50 | 40 000 $ | | 2e | 1 500 000 $ |
| 100 | **Fabrique à nuage** | | 3e | 1 000 000 $ |
| 200 | 120 000 $ | | 4e | 750 000 $ |
| 300 | 180 000 $ | | 5e – 10e | 400 000 $ |
| 500 | **Enderchest de poche** | | 11e – 20e | 200 000 $ |
| 750 | 400 000 $ | | 21e – 30e | 100 000 $ |
| 1 000 | 600 000 $ | | 31e – 40e | 60 000 $ |
| 1 500 | **Pierre légendaire aléatoire** | | 41e – 60e | 30 000 $ |
| 2 000 | 1 200 000 $ | | | |

**Le garde-fou du prix.** Le trixium se vend 800 $ et s'achète 3 200 $ (chapitre économique).
La barre rapporte 2,56 M$ pour 2 000 trixium, soit 1 280 $ l'unité, plus trois objets ; avec la
prime de la 1re place, un mois à 2 500 trixium rend environ 1 800 $ l'unité. Déposer vaut donc
mieux que vendre dès le deuxième palier, mais **acheter du trixium pour le déposer fait toujours
perdre de l'argent**.

### Les objets exclusifs

- **Fabrique à nuage** (`ItemCloud`, `BlockCloud`, `TileEntityCloud`) : pose un nuage (tissu,
  dureté 0, ne lâche rien) qui disparaît au bout de la durée choisie. **Maj + clic droit** change
  la durée : 15 s, 30 s, 1 min, 5 min. La fabrique **n'est jamais consommée**. Désactivée quand on
  porte l'œuf de l'Egg Hunt, comme l'original.
  **Écart** : `TileEntityCloud` ne sauvegardait pas sa durée ; un nuage dont le chunk se
  déchargeait revenait sans compte à rebours et restait pour de bon. Ici le reste est sauvegardé.
- **Enderchest de poche** (`ItemPocketEnderchest`) : un clic droit ouvre l'ender chest, partout,
  **sauf en combat**. Paladium lisait son plugin CombatTag ; ici, être en combat, c'est avoir
  frappé ou été frappé par une créature dans les 10 dernières secondes.
- **Selle humaine** (`ItemPlayerSaddle`) : ne sert qu'à monter sur un autre joueur. **Écartée**
  (choix du joueur).
- **Pinceau de peinture** (`ItemPaintingBrush`) : enregistré dans le jar sans texture ni usage.
  Rien à porter.

### Écran et commandes

- **Écran** : dépôt (tout le trixium du sac d'un clic, au lieu de la fenêtre à un emplacement),
  saison et prime actuelle, bilan de la saison précédente, les dix paliers, le top 10 des joueurs
  (et ta ligne), les factions. Une carte du menu principal y mène, avec les paliers à récupérer.
- `/trixium`, `/trixium deposer` ; pour les opérateurs, `/trixium admin avancer <jours>` (pour
  essayer une fin de saison) et `/trixium admin horloge` (remise à l'heure).
