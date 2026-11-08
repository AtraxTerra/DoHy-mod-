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
| `PaladiumMachineRecipies.add` | 38 | **palamachine** → chapitre 5b |
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
joueur le plus proche à 30 blocs a Mineur 90. Sa conversion de l'obsidienne voisine en
obsidienne wither (portail de la dimension de minage) n'est pas portée.

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

Toutes les 8 ticks, prend **la pile entière** dans l'inventaire du dessus — rien au sol. Une
chaîne d'entonnoirs en paladium envoie directement au dernier inventaire. **Écart** : l'original
se bloquait sur la première pile qui ne rentrait pas ; ici on essaie les suivantes.

### Coffres en améthyste et en titane — `ModdedChestContainer`

108 cases (12 × 9). **Écart** : les cases d'anneaux de l'original ne sont pas portées, et le
couvercle ne s'ouvre pas (modèle JSON statique tiré du `ModelChest`, pas de rendu animé).

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
demande Mineur 100 (`#palamod:break_locked`). **Écart** : il ne naissait que dans la dimension
de minage, non portée. Il est placé au fond de l'overworld (y −64 à −54) : quatre tirages par
chunk à 1 sur 225, chaque filon ramené à un bloc — d'où `replace_single_block` et 1 chance
sur 56 par chunk.
