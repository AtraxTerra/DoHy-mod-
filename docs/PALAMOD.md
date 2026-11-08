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

**Pas porté : le cobblebreaker et ses trois améliorations.** Le cobblebreaker demande la
voidstone et le modificateur de fortune du grinder ; les améliorations demandent le
modificateur de vitesse puis s'enchaînent. La voidstone a un comportement propre dans PalaMod
et un verrou dans `data/jobs.json` — la porter comme ingrédient inerte serait un mensonge, un
joueur la fabriquerait et constaterait qu'elle ne fait rien. Les modificateurs appartiennent au
grinder, donc au chapitre 5b.

Le relevé complet est archivé dans `data/palamod_crafts.json` : les 282 recettes, avec leurs
anomalies, prêtes pour les chapitres suivants.

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
