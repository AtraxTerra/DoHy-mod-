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
| Bonbon à déballer (5 bouteilles d'XP du métier jusqu'au 2026-11-14) | 95 |

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

**Remplaçants** (2026-11-11) : le bonbon à déballer de PalaMod (`ItemCandy`) devenait 5
bouteilles d'XP du métier. **Porté le 2026-11-14** (« Les bonbons de métier », plus bas) : la
récompense du niveau 95 est de nouveau le bonbon à déballer.
La défense d'éléphant (Chasseur, 80) devenait un bloc d'os, comme dans les recettes de cornes de
la palamachine ; **portée le 2026-11-17** avec l'éléphant, c'est de nouveau elle. Sans remplaçant,
ces récompenses n'étaient pas distribuées. L'armure de voyage (Fermier, 55)
est portée depuis (§ 28). `tools/palamod_jobs.py` porte la règle (`SUBSTITUTES`).

**Joueurs migrés** : à la première connexion après le remplacement des skills, un joueur
reçoit les objets de tous les paliers qu'il avait déjà franchis — leur argent avait été
versé, les objets n'existaient pas.

### Les bonbons de métier (2026-11-14)

Demandés le 2026-11-14, d'après le wiki Java (pages « Évolution du métier », 19/06/2026) : cinq
bonbons donnent **50 000 XP de PalaJobs d'un coup** — le vert au Fermier, le jaune au Mineur, le
bleu au Chasseur, le mauve à l'Alchimiste, le multicolore aux quatre — et le **bonbon à déballer**
donne un bonbon. Objets `palamod:candy_green`, `_yellow`, `_blue`, `_purple`, `_rainbow`,
`_wrapped` (`CandyItem`, `WrappedCandyItem`). Textures : celles que le joueur a fournies le
2026-11-15 (mod et dépôt privés), posées par `tools/candy_assets.py` — `candy_pink` y devient
`candy_purple`, `candy` devient `candy_wrapped`.

| Point | DoHy | Source |
|---|---|---|
| XP d'un bonbon | **17 850** (50 000 × 0,357, § 1), par `JobApi.grant` | wiki : 50 000 |
| Bonbon à déballer | multicolore 10 %, sinon une couleur (22,5 % chacune) | chances absentes du wiki : DoHy |
| Où | pass du mois (niveaux 5, 11, 17, 21, à la place des bouteilles d'XP) ; métiers niveau 95 ; coffres de boss dès le palier 50 | wiki : Palapass ; patch note V8.5 (niveau 90 du wiki Java) ; récompenses de boss (« Bonbon emballée » au 50, « à déballer » aux 85 et 95) |
| Double XP | la potion de Double XP (x2) ou de Triple XP (x3) multiplie l'XP du bonbon (§ 36) | wiki : la potion double l'XP du bonbon |

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

**Apparence** (revue le 2026-11-11). L'original le dessine avec un TESR (`ModelTotem`,
`RenderTotem`) : un mât sculpté de deux blocs de haut — socle, trois têtes empilées, deux ailes
en escalier — dont le visage luit de la couleur du carburant. Le portage en avait fait un cube ;
c'est désormais le modèle d'origine, relevé au bytecode (quatorze boîtes, texture
`models/totem.png`) et traduit en **modèle de bloc**, sans renderer de block entity. Le visage
de `totem_layer.png` est un plan lumineux sur la tête du milieu (devant) et celle du haut
(derrière). Deux propriétés de bloc : `facing`, le visage regarde celui qui pose le totem ;
`charge`, de 0 à 3 — éteint à vide, puis orange (1 à 21 lingots), jaune (22 à 42), vert (43 à
64), d'après la teinte `max(lingots / 64 / 3, 0,07)` de l'original. Un totem alimenté qui a de
la terre fertilisée dans sa zone lâche quelques étincelles vertes chaque seconde.
**Écarts** : la couleur va par paliers et non en continu ; le battement du visage à chaque
poussée (`timer / 60`) n'est pas repris ; le bloc n'occupe que sa case, la moitié haute du mât
se traverse.

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

### Les graines dans l'herbe — `EventHandlerAlchemist.FarmerDropHandler` (2026-11-15)

Absentes de DoHy jusqu'au 2026-11-15 : les graines de PalaMod ne venaient que des récompenses du
Fermier, du donjon Talikus et des récoltes. Le wiki (« Objets débloqués Fermier ») les fait
pourtant tomber de l'herbe, « comme pour des graines de blé ». Le code le confirme et donne les
chiffres : sur `BreakEvent` de `Blocks.tallgrass`, cinq tirages indépendants de
`(int) (Math.random() * 10000)`, chacun gardé par le verrou de la culture
(`JobsBridge.canUseCraft`) :

| Graine | Tirage | Chance | Verrou (DoHy) |
|---|---|---|---|
| Aubergine | ≤ 2 000 | 20 % | Fermier 35 |
| Cerfeuil | ≤ 2 000 | 20 % | Fermier 60 |
| Kiwano | ≤ 25 | 0,26 % (1 sur 385) | Fermier 80 |
| Orangeblue | ≤ 5 (≤ 7 avec la permission `palamod.boostseedorangeblue`) | 0,06 % (1 sur 1 667) | Fermier 100 |
| Cristal de glace ×3 | ≤ 1 000 | 10 % | aucun — culture d'événement, non portée |

Porté dans `GrassSeeds`. **Écart** : les hautes herbes et les grandes fougères comptent aussi
(une fois par plante), comme les céréales de DoHy ; `Blocks.tallgrass` n'était que l'herbe basse
et la fougère. La graine rejoint les drops du bloc en priorité basse, après Grass Breaker, qui ne
la multiplie donc pas — dans l'original il ne relançait que les drops vanilla. Pas de garde
`PlacedBlocks` : l'herbe repousse à la poudre d'os, la « ferme à graines » du wiki est voulue.

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

### Comparaison avec le relevé, 2026-11-11

Toutes les recettes portées ont été rapprochées du relevé : même grille (miroir compris), même
rendement, et pour chaque ingrédient d'origine, toujours le même objet moderne. Ce qui en est
sorti :

| Recette | Avant | Maintenant (comme l'original) |
|---|---|---|
| Améthyste, titane, paladium compressés | 9 blocs, et retour en 9 blocs | **8 blocs autour d'un lingot**, sans retour (un retour rendrait 8 lingots par tour) |
| Endium compressé | 9 blocs d'endium (729 pépites) | **4 fragments d'endium aux coins, 5 blocs de paladium en croix** |
| Vitre de coffre-fort | 8 verres et un findium : la grille du bloc de grotte, l'une masquait l'autre | **8 vitres** et un findium |
| Épée de capture | 4 bâtons en paladium à la place des sticks | **sticks de vitesse, de force, de soin et de dégâts** (palamachine, chapitre 16) |
| Orbe de régénération | melon scintillant seulement | **aussi avec un livre**, la seconde recette d'origine |
| Fragment d'endium | introuvable hors des donjons | **une pépite donne 9 fragments**, 9 fragments une pépite |
| Particules | seulement 9 particules → 1 lingot | **un lingot (ou un diamant) donne 9 particules**, sauf le fer et l'or : la même case donne déjà leurs pépites vanilla |
| Sable, obsidienne | — | **1 grès → 4 sable**, **4 obsidiennes renforcées (2 × 2) → 4 obsidiennes** |

Le fragment d'endium entre dans le lingot d'endium mixte : sans sa recette, l'armure d'endium
mixte ne se fabriquait qu'avec les fragments des donjons. Un auto-test sur serveur dédié (hors de
la PR) vérifie chaque recette : elle reconnaît ses propres ingrédients, donne son résultat et
n'est masquée par aucune autre.

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
| 95 | Trône du Mineur | 19 | oui, le 2026-11-19 (§ 36) |
| 100 | Minerai de pépites d'endium, pioche en endium | 20 | oui |

Les prérequis non listés par le wiki sont portés aussi : **coffres en améthyste et en titane**
(ingrédients de l'entonnoir et de la Voidstone minage), **Palafurnace** et **amélioration de
fourneau** (ingrédients de la Palaforge), **cave block** (ingrédient du cave block en endium).

### Le relevé des verrous ratait 26 objets

`extract_jobs.py` ne reconnaissait que les champs des registres `palamod/api`. Les trônes, les
objets du spawner et les houes à rayon vivent dans d'autres registres (`BlocksRegistry`,
`SpawnerItemRegistry`, `ItemsRegistry` de PalaJobs) : 26 verrous perdus, 99 au lieu de 73.
Conséquence visible hors de ce chapitre : **les houes à rayon sont maintenant verrouillées**
(Fermier 10, 25, 65, 85 et 100). Les verrous d'objets pas encore portés (spawner) sont
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
  **Depuis le 2026-11-12**, il vient aussi au bout de **sept commandes de trixium livrées de
  suite** (écran des contrats), sans condition de niveau : `docs/BALANCE.md` § 6 terdecies.
- Les gains d'XP codés en dur sont des XP de PalaJobs, **convertis au rapport 0,357** (§ 1) :
  4 → 1,43 (partie fractionnaire tirée au sort), 15 000 → environ 5 350.

### L'XP de Mineur des minerais et des particules (2026-11-15)

> **Remplacé le jour même** pour les minerais par le barème de Bedrock (section suivante) ; les
> particules du cobblebreaker restent telles quelles.

Jusqu'au 2026-11-15, **les minerais de PalaMod ne rapportaient aucune XP de Mineur** :
`job_progression.json` ne listait que des minerais vanilla, et les tags `#dohy:mining_xp/*`,
prévus pour un mod tiers, sont vides. Le wiki (« Evolution du métier Mineur ») ne donne pas d'XP
de minage pour eux : le minage y rapporte sur la roche et les minerais vanilla, et les minerais
de PalaMod passent par les **particules** du cobblebreaker. Deux corrections :

| Source | XP | D'où vient la valeur |
|---|---|---|
| Minerai d'améthyste | 12 par bloc | échelle de DoHy, par le rythme de minage (400/h) |
| Minerai de titane | 16 | 300/h |
| Minerai de paladium | 30 | 150/h |
| Minerai de findium | 75 | 50/h |
| Minerai de paladium vert | 90 | 40/h, comme le diamant |
| Minerai de trixium | 140 | 25/h |
| Minerai de pierre d'invocation | 170 | 20/h |
| Particules du cobblebreaker, prises à la main, dès Mineur 25 | fer 0,71 · or 1,43 · diamant 2,86 · améthyste 4,28 · titane 5,71 · paladium 7,14 | wiki × 0,357 (niveau 5 du wiki) |

- **Les minerais** suivent l'échelle des blocs de DoHy, pas celle de PalaJobs : sur le vanilla,
  l'XP d'un minerai suit environ 2 300 × (blocs trouvés par heure)^−0,88 — fer 12, or 20, diamant
  90, émeraude 110, débris 400 —, et les rythmes sont ceux des lignes `FOUND` de `data/rates.csv`.
  Les minerais de PalaMod se rendent eux-mêmes ; un minerai posé puis recassé ne rapporte rien
  (`PlacedBlocks`), comme tous les blocs.
- **Le minerai de pépites d'endium** n'a pas de ligne : il ne se casse qu'au niveau 100, le dernier.
- **Les particules** : l'XP passe par `MinerXpEvents.particlesTaken`, appelé quand la particule
  sort de la machine **à la main**, par clic (`onTake`) ou par maj-clic (`quickMoveStack`, qui
  n'appelait pas `onTake` : les succès du crusher et du cobblebreaker ne comptaient pas non plus
  au maj-clic, c'est corrigé au passage). Un entonnoir ne donne rien, comme dans l'original.
- Le même jour, **les minerais vanilla oubliés** : charbon (5, 6 en abîmes), variantes des abîmes
  du cuivre, de l'or, de la redstone, du lapis et de l'émeraude (environ +15 %, comme le fer et le
  diamant), quartz du Nether (8) et or du Nether (7). Ils ne rapportaient rien non plus.

### Les métiers sur le barème de Paladium Bedrock (2026-11-15)

**Demande** : « pour le métier de mineur, rend compatible le fait d'utiliser les hammer avec smelt
donne de l'xp, et aide toi de https://palatracker.bromine.fr/xp-calculator/bedrock et met à jour
le système de métiers ». Choix du joueur : **la cuisson aux trois métiers** de Bedrock (Mineur,
Fermier, Chasseur) et **tout recalculer sur Bedrock** (casse, cuisson, créatures).

**La source.** Le calculateur d'XP de PalaTracker, version Bedrock : le champ `bedrock` de
`how_to_xp` dans `src/lib/constants.ts` de BroMinee/PaladiumClicker, identique au site le
2026-11-15. Paladium Bedrock a 20 niveaux par métier (courbe `metier_xp_bedrock`, 35,8 M d'XP au
total) et trois métiers ; le minage rapporte à la casse **et à la fonte** (« casser, fondre et
raffiner », dit le wiki de Bedrock), la chasse aux animaux comme aux monstres et à la cuisson de
leur viande, l'agriculture aux récoltes, à la pomme de terre cuite, au pain et à la tarte.

**La conversion : une XP de Bedrock vaut une XP de DoHy.** Les niveaux de DoHy valent ceux de
Bedrock fois 5, et il faut la même XP pour le niveau 50 de DoHy (1 031 701) que pour le 10 de
Bedrock (1 061 305). Avant, DoHy demande jusqu'à deux fois plus d'XP pour un même palier (le 25 :
174 546 contre 87 023) ; après, de moins en moins (le 100 : 6,2 M contre 35,8 M pour le 20). Les
XP de l'onglet sont donc celles que PalaTracker affiche. Un niveau de Bedrock devient un niveau de
DoHy fois 5 : celui à partir duquel la source rapporte — en dessous, rien.

**Les paliers.** Bedrock donne parfois deux lignes pour un même objet : le lingot de titane fondu
vaut 5 XP au niveau 5 et 50 au niveau 6, le lingot de paladium 6 puis 150 au niveau 8, le paladium
vert 200 au niveau 12 puis 400 au 15. Ils sont lus comme des paliers : le plus haut atteint
s'applique (`JobProgression.Reward`, une valeur ou une liste de `{level, xp}`).

| Métier | Bedrock (extraits) | Niveau DoHy |
|---|---|---|
| Mineur, casse | andésite, granite, diorite 1 · tuf 3 · charbon 1 · fer 3 · cuivre 2 · or 6 · lapis 8 · redstone 6 · quartz 6 · obsidienne 6 · émeraude 30 | 0 |
| | diamant 20 | 10 |
| | variantes des abîmes (charbon 2, fer 5, cuivre 3, lapis 12, redstone 15, émeraude 45, diamant 30 ; or 9, déduit) | 50 |
| | améthyste 18 · titane 27 · paladium 36 · findium 100 | 25 |
| | paladium vert 40 (« Nugget Green Paladium », le plus proche) | 75 |
| Mineur, fonte | cuivre 1 · fer 2 (10) · or 3 (15) · améthyste 4 (25) · titane 5 (25) puis 50 (30) · paladium 6 (25) puis 150 (40) · paladium vert 200 (60) puis 400 (75) | entre parenthèses |
| Fermier | blé 1 (0) · pomme de terre 2 (5) · carotte 3 (15) · aubergine 20 (15) · betterave 8 (25) · cerfeuil 40 (30) · cactus 1 (35) · canne à sucre 2 (45) · citrouille 3 (55) · pastèque 5 (65) · kiwano 10 (70) · orangeblue 200 (95) ; pomme de terre cuite 2 (10) ; pain 5 (20) ; tarte 2 (75) | entre parenthèses |
| Chasseur | vache 80 (0) · mouton 90 (5) · poule 100 (10) · cochon 110 (15) · zombie 160 (20) · squelette 200 (30) · creeper 140 et araignée 210 (40) · araignée venimeuse 220 (50) · chauve-souris 300 (60) · wither squelette 400 (70) · blaze 500 (80) ; steak 40 (5) · mouton et saumon cuits 45 (10) · poulet 50 (15) · porc et lapin cuits 55 (20) | entre parenthèses |

**Ce que Bedrock ne connaît pas** garde sa place, à son ancienne valeur multipliée par le rapport
médian Bedrock / ancien de sa famille (calculé sur les sources communes, arrondi à l'unité sous
100, à deux chiffres au-dessus), au niveau de la source de Bedrock la plus proche :

| Famille | Rapport | Sources |
|---|---|---|
| Blocs vanilla et des autres mods | × 0,317 | débris antiques 130 (50), or du Nether 2, soufre et cinabre 3, pique de soufre 2, amas d'améthyste 5, `#dohy:mining_xp/*` 10, 25, 57, 110 |
| Minerais de PalaMod | × 1,417 | trixium 200, pierre d'invocation 240 (25, le palier des minerais de PalaMod) |
| Cultures | × 1,667 | verrue du Nether 3, cacao 5, baies 3, torchefleur et plante-amphore 67, maïs, avoine, seigle, orge, soja, tournesol 7, fonio, sorgho, kamut 23 |
| Animaux | × 33,3 | lapin 100 (20, comme le lapin cuit) |
| Monstres | × 36,7 | enderman 440 (70), sorcière 660 (80), gardien 370 (60), shulker 1 100 et piglin brute 1 300 (80), cube de soufre 290 (50) — le niveau de la créature de Bedrock la plus forte qui ne rapporte pas davantage |
| Boss | × 36,7 | gardien ancien 4 400 (0), warden 29 000 (80), wither 44 000 et dragon 73 000 (95) — face aux Arty de Bedrock (1 000 au niveau 0, 20 000 au 16, 40 000 au 19) |

**Ce qui change pour le joueur.**
- **La pierre et l'ardoise ne rapportent plus rien**, comme sur Bedrock ; l'andésite, le granite,
  la diorite et le tuf, si.
- **Les animaux passent au Chasseur** (vache 80, cochon 110…) ; le Fermier garde ses récoltes, la
  cuisine et ses machines.
- **La fonte rapporte** (`JobXpEvents.smelted`), par objet sorti : à la case de résultat d'un four,
  d'un haut fourneau ou d'un fumoir, au clic comme au maj-clic (`ItemSmeltedEvent`) ; au
  palafurnace (`MachineMenu`) ; et sur place, au **marteau en fusion** (`SmelteryEvents`) et à la
  **pioche du dieu en fusion** (`GodPickaxeEvents`) — la compatibilité demandée. La Fortune y
  multiplie les lingots, donc l'XP, ce que PalaTracker compte comme son « bonus Fortune ». Un
  entonnoir ne donne rien.
- **La fabrication rapporte** (`ItemCraftedEvent`) : le pain et la tarte au Fermier.
- **Deux garde-fous.** Une créature née d'un spawner (`Mob.getSpawnType()`), ou tuée au
  contrôleur de spawners, ne rapporte qu'**un quarantième** : le contrôleur se frappe deux fois
  par seconde sans risque, et à pleine valeur il menait au niveau 100 en quelques heures ; un
  quarantième, c'est à peu près l'ancien barème (zombie 4). Et **un petit ne rapporte rien** —
  il ne lâche rien, et l'élevage tuerait les veaux à la naissance.
- **Ne bougent pas** : l'Alchimiste (absent de Bedrock), les particules du cobblebreaker,
  l'obsidienne sous Dureté, le God Villager, la pêche, le spawner cassé, les machines de
  l'agriculture, les donjons, les gardiens, les bonbons, les bouteilles et les pierres.

`tools/bedrock_jobs.py` porte la table de Bedrock, l'ancienne table de DoHy et les règles
ci-dessus ; il réécrit les sources des trois métiers dans `data/job_progression.json`.

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
| 45 / 55 | Graines d'herbe colorée, herbe colorée | 9 / 11 | oui, le 2026-11-24 (§ 44) — écartées une première fois, redemandées |
| 50 | Graine de racinier | 10 | oui, le 2026-11-24 (§ 44) — écartée une première fois, redemandée |
| 70 | Totem de fertilité | 14 | déjà au chapitre 5a |
| 95 | Trône du fermier | 19 | oui, le 2026-11-19 (§ 36) |
| 100 | Hache en endium (abattage), plastron en endium | 20 | oui (le plastron l'était au chapitre 7) |

Les herbes colorées et le racinier ont été portés puis **retirés à la demande du joueur** après
lecture du résultat (2026-11-08), puis **redemandés le 2026-11-24** et refaits avec les vraies
textures de PalaJobs : voir § 44. Le relevé ci-dessous est celui de la première version. Leurs relevés restent valables pour qui voudrait y revenir : dix couleurs
en métas, une graine qui change la terre labourée **humide** en herbe teinte ; un arbre de trois
bûches dont la racine descend en **une seule colonne** jusqu'à la bedrock ou l'obsidienne (pas
l'obsidienne renforcée), recette à base de **bûches** de bois mort. Leurs verrous restent dans
`data/jobs.json`, inertes jusqu'au § 44.

### Hache en endium — `BaseItemAxe.onBlockDestroyed`, `breakTree`

Une bûche cassée abat toutes les bûches qui la touchent, par les 26 voisines, jusqu'à 15 pas,
sans limite de nombre ; les feuilles restent et tombent d'elles-mêmes. **Une usure par
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
| 40 / 60 / 80 / 100 | Obsidiennes infusées : structures de spawner I à IV | oui — §32 |
| — | Contrôleur de spawner | oui — §32 (pas de verrou dans l'original) |
| 75 | Amélioration de butin de spawner (les trois autres : Mineur 85, Alchimiste 75 et 90) | oui — §32 |
| 10 / 45 / 90 / 100 | Bateau en bambou, knocker infernal, étiquette en endium, totem en endium, autel de sacrifice, boussoles, poudre de perlimpinpin | oui, le 2026-11-24 (§ 44) |
| 15 | Cage à familier (et les familiers) | **pas encore** — lot suivant |
| 35 / 50 / 85 | Mini golem, détecteur de spawner, clé en paladium | oui, venus avec leurs modules (golem, pillage, stockage) |
| 95 | Trône du chasseur | oui, le 2026-11-19 (§ 36) |

Le seul verrou du Chasseur encore sans objet est celui de la cage à familier ; il reste dans
`data/jobs.json`, inerte.

### Canne à pêche en paladium — `ItemPaladiumFishingRod`

Durabilité 128, enchantable. Dans l'original elle ne pêchait pas plus vite : elle changeait les
chances du mini-jeu de pêche de Paladium. **Depuis le 2026-11-15, le mini-jeu est porté** (« La
pêche de PalaJobs », ci-dessous) : la canne ouvre le mini-jeu, et son ancien écart (+3 de chance en
main) est retiré.

### Le pont-levis (2026-11-15)

Demandé le 2026-11-15 (« ajoute le drawbridge »), d'après Palamod Recreated : `palamod:drawbridge`
(`DrawbridgeBlock`, `DrawbridgeBlockEntity`, onglet des défenses). Un bloc à neuf cases : alimenté
en redstone, il pose devant lui (face avant tournée là où regardait le joueur qui l'a posé), un
bloc tous les deux ticks, jusqu'à **seize** blocs pris dans son inventaire ; le courant coupé, il
les reprend du plus loin au plus près et les range (au sol si l'inventaire est plein). Il ne
reprend que ses propres blocs encore en place, s'arrête devant un obstacle, ne pose pas de bloc à
inventaire. Cassé, il lâche ses blocs posés et son contenu ; le comparateur lit sa longueur.
Recette et textures du dossier fourni par le joueur (`tools/drawbridge_assets.py`) : deux pistons,
deux blocs de slime, un distributeur, trois poudres de redstone.

### La pêche de PalaJobs (2026-11-15)

Demandée le 2026-11-15, choix du joueur : **le mini-jeu seulement avec les cannes de Paladium**
(la canne en bois reste vanilla), **la canne en endium en plus**, et **les poissons se vendent**
(shop et marché, plus cher selon la rareté). Paquet `palamod/fishing/` (`FishingGame`,
`FishingTable`, `FishingView`), écran `FishingScreen`, objets dans `PalaHunter`, textures du jar de
PalaJobs fourni par le joueur (`tools/fishing_assets.py`).

| Point | DoHy | Source |
|---|---|---|
| Déclenchement | un poisson remonté avec une canne en paladium ou en endium : le butin vanilla est annulé, le mini-jeu s'ouvre | wiki : l'interface s'ouvre quand un poisson mord |
| Barre | zones verte (×1), rouge (×2) au milieu de la verte, violette (Boost ×5, ×3 au légendaire) au milieu de la rouge, jaune (rareté suivante, nouvelle manche) ailleurs ; largeurs et durées de `FishingRegistry` (3 s, 2,5 s, 2 s) | PalaJobs |
| Raretés | commun, rare, légendaire, mythique ; butin pondéré de `FishingRegistry` | PalaJobs |
| Chances de départ | canne en paladium 75 / 21 / 4 / 0 % ; canne en endium 55 / 32 / 10 / 3 % ; Boost ou Level-Up une manche sur sept chacun | DoHy |
| XP de Chasseur | celle du wiki × 0,357 (§ 1) : de 9 (morue) à 5 355 (kraken), × le nombre de poissons | wiki |
| Niveaux | 10 (carpe, bar, raie, thon, poisson lune), 15 (poisson à XP), 20 (baleine, kraken) : en dessous, le tirage les exclut | wiki × 5 |
| Vente | rythme de prise dans `data/rates.csv` (de 120 carpes à 1,2 kraken par heure), prix tirés par `tools/gen_prices.py` : de 100 $ (carpe) à 10 000 $ (kraken) | DoHy |
| Canne en endium | durabilité 512, recette de la canne en paladium avec un lingot d'endium en bout ; seule à ouvrir le mythique | PalaJobs (objet) ; DoHy (recette) |

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
  slime ou sorcière — et, **écart**, de vache, de cochon ou de creeper, que portent les ruines de la
  dimension du Mineur (§ 18) — ajoute **une âme** au premier spawner vide de cette créature qui a de la place
  dans l'inventaire, sinon au dernier spawner vide sans créature. Sans spawner où la mettre, la
  casse est refusée (un message le dit) : l'âme serait perdue.
- Casser un spawner de donjon donne 1 250 XP de Chasseur (converties) à partir du niveau 40.
- **Écarts** : sans marteau, un spawner de donjon se casse normalement ; un spawner customisé
  posé ne donne jamais d'âme (on taperait sans fin le sien).
- **Recette d'origine** depuis que le marteau en paladium existe : six blocs de titane, deux
  obsidiennes, un marteau en paladium.

### Spawner cassé

Chaque spawner de donjon pris dans l'explosion d'un creeper a une chance sur deux de lâcher un
spawner cassé. Il sert à fabriquer le spawner vide ; **recette d'origine** depuis que la pierre
de gardien existe : crâne de squelette, quatre findiums, paladium compressé, spawner cassé,
pierre de gardien, crusher.

### Spawner customisé — wiki, `SpawnerManager`

- Les âmes s'accumulent jusqu'à **250**. Paliers : **100 / 150 / 200 / 250 âmes → I à IV**.
  Sous 100 (palier 0), le spawner posé reste **vide**. La cage prend la couleur du palier
  (améthyste, titane, paladium, endium) ; l'objet aussi, avec le palier et les âmes en info-bulle.
- Posé **hors d'une structure**, il **fonctionne comme un spawner vanilla** (délai, portée du
  joueur, lumière), à la demande du joueur. Dans PalaMod, sans contrôleur, il ne faisait rien.
- Posé **dans une structure**, c'est le contrôleur qui compte ses vagues (§32) : le spawner passe
  à l'état `linked`, ne fait plus apparaître de créature dans le monde et n'a plus de tick
  serveur. Toutes les cinq secondes il vérifie que son contrôleur le couvre encore ; sinon il
  redevient un spawner ordinaire.
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

### L'éléphant — `EntityElephant`, `ModelElephant`, `RenderElephant` (2026-11-17)

Relevé au bytecode (`hunter.entites.EntityElephant`, son modèle et son rendu ; `HunterRegisterer`
pour l'XP). Il ne venait que des lucky blocks et des événements du serveur : aucun code ne le
faisait apparaître.

| Âge | Taille | Vie | Coup | Vitesse | Recul | Texture |
|---|---|---|---|---|---|---|
| petit | 1 × 1,5 | 25 | 5 | 0,6 | insensible | 64 × 64 |
| ado | 1,5 × 2,3 | 50 | 10 | 0,5 | 0,65 | 128 × 128 |
| adulte | 3 × 4 | 100 | 25 | 0,3 | insensible | 160 × 160 |

- **Naissance** : un âge au hasard (`entityInit` : `rand(3)`), robe 0, sauvage. Le petit est
  insensible au recul par une faute de l'original (l'âge comparé à 0,3) : gardée.
- **IA** : promenade (0,6), regarde les joueurs, ne charge (0,8) que le joueur qui le frappe.
- **Bambou** (`interact`, main droite) : un bambou (+1) ou un bloc de bambou (+9) ; accroupi, toute
  la pile. Il soigne d'autant. Sauvage, seul le petit mange, et s'apprivoise si son compte de
  bambou atteint un tirage de 0 à 9. Apprivoisé, il grandit : ado à 64, adulte à 128, robe 1 à
  1 000, robe 2 à 5 000 ; il refuse tout à 100 000. Étincelles de coup critique (75), cœurs à
  l'apprivoisement (100) et au plein (200). Compté pour le succès « Élevage d'éléphants ».
- **Monture** : le maître monte d'un clic droit main vide. Bambou en main, l'éléphant avance droit
  devant dans le lacet du cavalier (`moveEntityWithHeading(0, 0,4 / 0,3 / 0,2)`), à la vitesse de
  sa promenade (0,6 × l'attribut, ramenée au sol de 26.2 : × 0,45) ; sinon il se promène, le
  cavalier sur le dos. Le cavalier s'assied à `0,75 × hauteur` de 1.7.10.
- **Mort** : un ado ou un adulte lâche une fois sur 25 de 0 à 2 **défenses d'éléphant**, plus de 0
  à Butin (`getDropItem`, `dropFewItems`), et 5 orbes d'XP comme un monstre.
- **XP de Chasseur** : 15 XP de PalaJobs au niveau 8 (`EntityKillObjective`), soit **100 XP dès
  Chasseur 40**. Le barème des créatures est celui de Bedrock, qui n'a pas d'éléphant : son XP
  passe au rapport médian Bedrock / PalaJobs des animaux communs (vache, mouton, poulet, cochon :
  14 dans PalaJobs, 80 à 110 sur Bedrock, × 6,79), dans `tools/bedrock_jobs.py`. Le **bonus de
  l'éléphant nourri** (`EntityKillSpecialObjective`, niveau 11 → **55**) : de 128 à 1 000
  bambous, 1 à 40 XP de PalaJobs ; au-delà, `bambou / 100 + rand(bambou / 10 + bambou / 25)` ;
  converti au même rapport que la mise à mort.
- **Capture** : à l'épée et à la pierre de capture, à tout âge (« Zoo original »).
- **Apparition (écart)** : par petits groupes (1 à 3, poids 3) dans les savanes et les jungles
  (`palamod:elephant_spawns`, `biome_modifier/elephant.json`), sur un sol d'animal et au jour ;
  `Wildlife` en ramène sur une carte déjà générée.
- **Écarts** : une créature et pas un monstre (`EntityMob`) — il ne disparaît plus en paisible et
  sait nager ; sauvage et sans nom, il s'en va loin des joueurs, comme un monstre de 1.7.10,
  mais un apprivoisé reste (l'original pouvait disparaître). Les sons de monstre sont gardés.
  La peau « Elmer » (un éléphant nommé d'après un personnage de livre pour enfants) n'est pas
  reprise : c'est l'œuvre d'un autre. L'œuf d'apparition a une texture de DoHy (gris, taches
  gris clair, les couleurs de `registerGlobalEntityID`).
- **Pas encore dans les donjons** : la défense d'éléphant et le totem fleuri figuraient dans les
  tables du wiki, à des paliers non relevés ici ; ils restent hors des coffres.

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
| 15 | Table d'alchimiste | oui, le 2026-11-24 (§ 44) |
| 40 | Totem fleuri | oui (jardin, plan de construction) |
| 75 / 90 | Améliorations de spawner | **non** (sans contrôleur de spawner) |
| 95 | Trône de l'alchimiste | oui, le 2026-11-19 (§ 36) |

Sans verrou : fiole, extracteur, potion de foudre, fleurs, pollens, bibliothèque mystique, arbres.

### Les arbres — `PWorld`, `WorldTreeNGen`

- Quatre essences (jacaranda, judée, érable, ostrya) et le bois mort : bûche, planches, feuilles,
  pousse. Bûches et planches durent 3 (plus que le bois vanilla), **ni inflammables ni dans
  `#planks`** : une planche de PalaMod ne fait ni bâtons ni établi. Une bûche donne quatre
  planches. Bûches et planches brûlent 300 ticks au four (bois de 1.7.10).
- Les **générateurs sont des patrons figés**, communs aux quatre essences : 30 bûches et 97
  feuilles (11 de haut) deux fois sur trois, 35 et 129 (12 de haut) sinon ; les patrons 3 et 4
  ne sont jamais tirés. Coordonnées recopiées du bytecode (`PalaTreeFeature`).
- Les feuilles : la feuille à la cisaille ou au toucher de soie, sinon une pousse. La pousse suit
  la croissance vanilla.
- **Écart : une pousse sur vingt** (demandé le 2026-11-14), plus avec Fortune (6,25 %, 8,3 %,
  10 %), comme le chêne vanilla ; aussi quand la feuille tombe seule. L'original : une sur 99
  (1,01 %). Un arbre de PalaMod n'en rendait alors qu'une en moyenne, et aucune une fois sur
  trois (0,99⁹⁷ ≈ 37 % pour le premier patron) ; à 5 %, environ cinq (97 feuilles) ou six (129),
  et presque jamais aucune.
- **Écart : les feuilles tombent** (2026-11-13 ; l'original : un bloc simple, qui ne pourrissait
  jamais). Ce sont de vraies feuilles (`PalaLeavesBlock`) : à plus de six pas d'une bûche, elles
  tombent, et vite avec **Leaves Be Gone**, qui ne traite que les vraies feuilles. Posées à la
  main, elles restent.
  - **Distance par les 26 voisines**, coins compris, et seulement par les bûches et les feuilles
    de la même essence : les patrons sont clairsemés, un cinquième de leurs feuilles ne touche
    rien par une face et serait tombé dès la pousse avec le calcul vanilla. Ainsi, chaque feuille
    des deux patrons est à un ou deux pas d'une bûche. L'arbre est posé avec ses distances.
  - **Feuilles d'avant ce changement** : sauvegardées sans propriété, elles reviennent à l'état
    par défaut, « distance 1 » : elles tiennent tant que rien ne bouge autour. Dès qu'une bûche
    ou une feuille voisine change, la vraie distance se recalcule de proche en proche : abattre un
    vieil arbre le déplume comme un neuf ; un bouquet déjà sans tronc tombe quand on en touche
    une feuille. Celles qu'on avait posées à la main avant ne se distinguent pas : loin d'une
    bûche, elles tombent aussi si on touche leur voisine.
  - Les bûches entamées par l'extracteur comptent comme des bûches (`#minecraft:logs`).
  - Une feuille sur cent qui pend dans le vide lâche une particule de feuille à la couleur de
    l'essence ; ses pixels transparents ont une teinte sombre, pour le rendu sans découpe.
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
- **La fleur minérale** (`BaseBlockFlower.onBlockActivated`, `FLOWER_MINERAL` seule ; portée le
  2026-11-18) : plantée, elle se change en lingots à la poudre d'os. Les six premières la font
  « pousser » sans rien montrer (métadonnée 0 → 6, ici `growth`) ; la septième la consomme et
  donne, un tiers chacun, 2 à 4 lingots de titane, 1 à 2 de paladium ou 3 à 5 d'améthyste,
  lâchés aux pieds du joueur et ramassables tout de suite (ici : dans son sac, sinon au sol).
  Un distributeur n'y fait rien, comme dans l'original. **Écarts** : les particules de poudre
  d'os à chaque coup, la fleur se brise à la dernière ; en créatif, la poudre n'est pas
  consommée. Elle se ramasse toujours quand on la casse (l'original ne la rendait qu'au toucher
  de soie) et reprend alors à zéro.
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
- **Grass Breaker** (épées, 3 niveaux, Fermier 20) : une haute herbe cassée à l'épée relance son
  butin une fois de plus, **avec Fortune I**, et chaque pile gagne de 0 à `niveau` graines
  (`AlchemistEventHandler`). **Écart** : l'original le réservait aux épées rapides, non portées.
- **Unclaim Helmet** (casques, 1 niveau) : fondu à l'enclume avec un Unclaim Finder (15 niveaux),
  le casque porté **devient** ce finder, de sa couleur — le HUD du finder s'affiche sans l'avoir en
  main. L'arbre de faction compte les poses.
- **Fake Water Protection** (casques, 1 niveau, arbre de faction) : dans la Fake Water, le casque
  seul encaisse, **66 points d'usure par seconde** (`BlockSulfuricWater`). Avant cette version,
  toute pièce d'armure le portait et l'usure n'était pas comptée.
- **Experience Bottler** (tout objet à usure, 1 niveau, Mineur 60) : tenu ou porté, l'XP de métier
  gagnée remplit des **bouteilles d'XP de métier** de ce métier (1 000 au plus chacune), en
  complétant d'abord les bouteilles entamées, puis en convertissant les fioles vides du sac
  (`PMiner`, `AJobsEventHandler`). Sans fiole, l'XP va au métier. Les bouteilles bues ne
  remplissent rien.
- **Voleur d'XP** (épées, 4 niveaux, Chasseur 55) : à chaque coup porté à un joueur, 2 / 4 / 7 /
  10 % de chances de lui prendre 40 / 50 / 70 / 80 points d'expérience.
- **Overwhelmed** (plastrons, 3 niveaux, Chasseur 95) : une amulette de plus par niveau sur le
  plastron (une sans).
- **Récupération** (épées) et **Récupération de minage** (outils), 1 niveau : le butin d'un
  monstre tué ou d'un bloc cassé va directement dans le sac ; ce qui ne tient pas reste au sol.
  Les monstres nommés gardent leur butin au sol.
- **Expérience** (épées, 3 niveaux) : +1 / 3 / 5 % sur l'XP ramassée. **Écart** : l'original ne
  venait que des lucky blocks de septembre, absents ; il passe ici par la table.
- Pas portés : **Omniscient** (aucun effet dans le code d'origine : l'enchantement est déclaré et
  rien ne le lit), **Confusion** (cité par le wiki seulement, sans code ni chiffres), la table
  « lisible » d'Alchimiste 75. Frost Walker est celui de vanilla.

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
  Fusion : chaque objet lâché sort cuit, avec l'XP de métier et l'XP verte du four — celle de la
  recette, 0,7 par lingot de fer, 1 par lingot de paladium (demande du 2026-11-21). Fortune :
  l'enchantement vanilla, posé par le grinder.
- **Broadswords** : 7,7 / 8,2 / 9 de dégâts, +2,8 par niveau de dommage. **Fastswords** : 6 / 5 /
  7, +1,5 par niveau ; tenue en main, Célérité IV ; la cible ne reste invulnérable que 18 ticks.
  Flamme : la cible brûle 10 secondes ; recul : un demi-bloc par niveau.
- **Écarts**, des coquilles corrigées : broadsword en titane et fastsword en paladium avaient 20
  de durabilité (portée à 2 000) ; la flamme de la fastsword durait 200 secondes (des ticks pris
  pour des secondes, ramenée à 10).
- Recettes restaurées : le marteau de caverne reprend son marteau en paladium, l'épée de capture
  ses trois têtes d'arme, puis, le 2026-11-11, ses quatre sticks à effet (§ 9).

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
  des minerais (ici elle la garde, et y ajoute l'XP verte du four). L'amélioration « big hole », lue par aucun code et sans recette, n'est pas portée.

### Ce qui n'est pas porté

- Le jeton de récompense du Mineur 95 (un marteau en paladium avec 3 fortune et 3 vitesse) : les
  récompenses de DoHy sont des objets sans composants.
- Succès, quêtes et événements du grinder (PalaPass, tutoriel), l'événement « Pioche de Légende »
  du lucky block Findium (pas de lucky blocks), le bonus de familier de la pioche.

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
recettes) et Lindworm (l'animation). Tout passe sous l'espace `palamod`. Les trois espèces sont
portées : le Dancarok (chapitre 14), le Ravirok et le Tedarok (lot du 2026-11-24, ci-dessous).
Aucun verrou de métier sur les objets de monture ; l'épée et la pierre de capture gardent les leurs
(Chasseur 65 et 70).

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
  jamais, ne se monte pas. **Aucun code d'apparition dans les jars**, les sauvages étaient posés
  à la main sur le serveur « Farmland » ; la règle d'apparition est donc un ajout. Biomes :
  plaines, plaines de tournesols, prairie, savanes (et `#c:is_savanna` des autres mods), tag
  `palamod:dancarok_spawns` ; sur de l'herbe, au jour.
  - **Règle vanilla** : poids 3 parmi les créatures (moutons 12, cochons 10, poules 10,
    vaches 8, chevaux 5, ânes 1), un à la fois. Elle joue surtout à la génération des chunks.
  - **`DancarokSpawner`** (2026-11-13) : sur une carte déjà générée (la Terre), il n'y a pas de
    génération de chunks, et le tirage des créatures toutes les 20 secondes est plafonné à une
    dizaine de bêtes que moutons et vaches remplissent : un Dancarok n'y venait presque jamais.
    Une fois par minute, une chance sur huit par joueur dans l'overworld : un sauvage apparaît à
    32-64 blocs, hors de vue, dans un de ces biomes, s'il n'y en a aucun autre à 128 blocs. On en
    croise un par coin de plaine où l'on passe un peu de temps (huit minutes en moyenne). La règle
    `spawn_mobs` l'arrête.
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
- **Cornes** : elles se fabriquent à la **palamachine** (chapitre 16, § 20) autour d'une
  **défense d'éléphant** : défense au centre, lingot, bloc, lingot, bloc. Avant l'éléphant
  (2026-11-17), un bloc d'os la remplaçait. Les recettes « bâtons + bloc » sont des
  **réparations** : la corne
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

### Le Ravirok et le Tedarok — `RavirokMount`, `TedarokMount`, `MountType`

Relevé dans les classes d'espèce (`hp`, `speed`, `nbSlots`, `jumpStrenght`, `skills`) et dans
`EntityMount.moveEntityWithHeading`, `EntityRavirokMount` et `CSMountMovement`, croisé avec la page
« Montures » du wiki de Paladium.

| | Dancarok | Ravirok | Tedarok |
|---|---|---|---|
| Taille | 2,5 × 1,9 | 1,85 × 1,6 | 1,45 × 1,65 |
| PV au niveau 100 | 400 | 100 | 200 |
| Cases au niveau 100 | 36 (+ la corne) | 9 | 18 |
| Vitesse (× celle de la monture) | 1 | 1,3 | 1,15 ; 1,9 en course |
| Détente (× le saut) | 1 | 0 | 3,5 (environ cinq blocs) |
| Compétences | `MiningSkill` (corne) | `FlySkill` | `AutoJumpSkill(5)`, `RunningSkill(1,9)`, `WaterWalkSkill(1,15)`, `WaterRunSkill(1,9)` au niveau 50 |

- **La gemme porte l'espèce** (`species`, le `Type` de l'original) ; une gemme ou une monture
  équipée d'avant n'en a pas : c'est un Dancarok. Le nom par défaut est celui de l'espèce.
- **Le Ravirok vole** et ne marche pas : la touche de saut le fait monter (0,15 bloc par tick), la
  touche de course descendre (0,3), il plane entre les deux, jamais au-dessus de la couche 256
  (`onEntityUpdate`, et le correctif V11.0.4.1 de Paladium). En l'air, la vitesse est celle de la
  monture × 1,3 avec le freinage de l'air (0,91). Il bat des ailes toutes les secondes. Le cavalier
  qui descend en vol tombe.
- **Le Tedarok** franchit d'un pas cinq blocs (`stepHeight = 5`), court × 1,9 à la touche de course,
  marche sur l'eau dès le niveau 1, et y court au niveau 50. **Écart** : Mount le laissait nager à
  la surface (une poussée de 0,1 rattrapait sa chute) ; ici il marche sur l'eau calme comme
  l'arpenteur sur la lave, à la vitesse de la terre, et remonte s'il tombe dedans.
- La **corne** est au Dancarok : la case est fermée sur les deux autres (la corne reste gardée), le
  clic droit n'y donne aucun coup, et l'on peut **casser et poser des blocs** en selle (wiki).
- Les **touches** passent du client qui conduit à la monture qu'il simule (`MountClient`) : pas de
  paquet, la monture contrôlée se déplace côté client en 26.2.
- **Sauvages** : aucune règle d'apparition dans les jars (Paladium les posait dans sa Farmland ; le
  correctif V8.5.0.0 y ajoute le Ravirok). Ici : `DancarokSpawner` en pose une de chaque espèce dans
  ses biomes (Ravirok : collines battues des vents, pics, pentes, cerisaie ; Tedarok : forêts et
  taïgas), une à la fois par espèce à 128 blocs, et la farmland en garde une par coin (prairie,
  collines, forêt). Modèles, animations (`idle`, `walk`, `fly` ; `walk`, `run`, `idle`), textures et
  sons du jar, convertis par `tools/bedrock_model.py`.
- `/mount give [niveau] [dancarok|ravirok|tedarok]` (opérateurs).

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
- **Dans la dimension** : aucun monstre hors de ceux des spawners des ruines (plus bas), pas de dégâts
  de chute, aucun conteneur posé, l'obsidienne wither ne se casse pas. **Écart** : une explosion n'y casse aucun bloc — la TNT abattrait les
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
les plaines sans pluie, sans mobs, sans salle à monstres — les ruines à spawner en tiennent lieu. Une nuit fixe : ciel et brouillard noirs,
lumière du ciel de la nuit vanilla, brouillard proche (le client de Paladium forçait l'heure à
15 000 et vidait le ciel). Lit et ancre de réapparition sans effet.

Les minerais PalaMod y sont multipliés comme sur le serveur dimminer : `mult = 3`, d'où
`ceil(3 × base) + 1` tentatives au lieu de `base + 1`, rapport appliqué aux comptes de l'overworld
DoHy — améthyste 8 → 21, titane 6 → 16, paladium 4 → 9, paladium vert 2 → 4, findium 3 → 8,
trixium 1 → 3, buisson à XP 3 → 7 essais. Pépite d'endium : un chunk sur 225, et seulement ici.
Les minerais vanilla gardent leur taux.

### Les ruines à spawner — `MinerRuinFeature` (ajout de DoHy)

Sur le serveur de minage de Paladium, les âmes du marteau des cavernes (§ Chasseur) se récoltaient
dans les salles à monstres du monde. La dimension de DoHy n'en avait pas : un seul biome, sans
monstres. Ces ruines les remplacent, **fréquentes**, chacune autour d'un **spawner** :

| Créature | En surface | Sous terre (salle fermée de 7 × 7 sur 4) |
|----------|------------|-------------------------------------------|
| Zombie | crypte en pierre taillée moussue, murs ébréchés, toiles | pavés moussus, toiles |
| Squelette | ossuaire d'ardoise, quatre piliers d'os, crânes | briques d'ardoise, piliers d'os |
| Sorcière | hutte d'épicéa sous un toit débordant, chaudron | pierre taillée, bibliothèques, chaudron |
| Creeper | sanctuaire de grès aux piliers ciselés (le visage du creeper) | grès, piliers ciselés |
| Vache | enclos de chêne, abri à foin | étable de chêne sur herbe, foin, lanternes |
| Cochon | porcherie d'épicéa dans la boue, abri à foin | étable d'épicéa sur herbe et boue, foin, lanternes |

- **Fréquence** : en surface, un tirage par chunk à une chance sur 9, sur un sol à peu près plat et
  hors de l'eau — une lanterne signale la ruine dans la nuit. Sous terre, un tirage par chunk à une
  chance sur 10, entre y = −48 et 48, que la roche valide : une salle ne se taille ni dans une
  grotte ni dans un lac. Les six créatures sont tirées à parts égales. Mesuré en plaine sur
  361 chunks (304 × 304 blocs) : 20 ruines en surface, une tous les 18 chunks, et 29 salles sous
  terre, une tous les 12 chunks. Réglage : `placed_feature/miner/ruin.json` et `crypt.json`
  (`tools/palamod_assets.py`).
- **Les spawners marchent** : leurs créatures apparaissent, monstres compris — c'est la seule
  exception à « aucun monstre » (`DimMinerEvents.onJoin` laisse passer une créature née d'un
  spawner).
  - En surface, ils portent leurs propres règles d'apparition (`custom_spawn_rules`, lumière de 0
    à 15) : sans elles, la nuit fixe interdirait les bêtes (il leur faut de la lumière et de
    l'herbe) et la lumière du ciel les monstres.
  - Sous terre, ces règles ne valent rien — elles lisent la lumière du ciel assombrie par la nuit,
    négative là où le ciel ne porte pas — et les salles suivent les règles ordinaires : **noires**
    chez les monstres (une torche posée les arrête, comme dans une salle à monstres vanilla),
    **éclairées et sur herbe** chez les bêtes.
- **Les âmes** : le marteau des cavernes accepte désormais la **vache**, le **cochon** et le
  **creeper** en plus des six créatures d'origine (**écart**). Casser un spawner ne coûte aucun point
  de session.
- Les mondes déjà créés : seuls les chunks de la dimension générés après la mise à jour portent des
  ruines. Une section neuve (session suivante) en est pleine.

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
  rapporte **+2 points d'armure**. **Refondu le 2026-11-11** : le musée n'expose plus que des
  pièces rares, et PalaMod y tient deux ailes — « Reliques de PalaMod » (11 pièces, +2 armure et
  +15 % d'efficacité de minage) et « Donjons et pillage » (9 pièces, +4 points de vie). Détail :
  `docs/BALANCE.md` § 6 duodecies.
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
  utilisations). La défense d'éléphant y est au centre des cornes (un bloc d'os jusqu'au
  2026-11-17) ; les anciens substituts des cornes à l'établi sont retirés. Laissées : détecteurs
  de claim, runes de donjon (non portés).

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

Les lucky blocks et les événements de Paladium n'existent pas ici. La pierre aléatoire est le gros
lot de la **box Paladium** (6 pour mille, l'ancienne caisse rare) ; la **box Endium** (l'ancienne
caisse légendaire) en donne quatre d'un coup (lot commun), les six pierres d'un coup (lot rare), et
le **gant complet** (7 pour mille). Depuis le regroupement des caisses dans les boxes (§ 33), les
boxes **Halloween** et **Trixium** en donnent aussi, d'une à six. Une entrée de box accepte des
composants (`components`), ce qui permet le gant garni, et un lot de plusieurs objets (`items`).
Voir BALANCE § 6 quater.

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
infusée, têtes de thirsty et de prédator, totem fleuri, jetpack, défense d'éléphant,
pièce de lune, têtes de wither passive et suprême. Le double XP y est depuis le 2026-11-19 : la potion
de Double XP de 30 min, et la Triple dès le palier 50 (§ 36).

### Fin du palier

- **Réussite** : sac livré (ou jeté au sol s'il ne tient pas), XP du métier du donjon
  `600 + 40 × palier` (wiki : « XP Métier » sans chiffre), palier noté, retour au point d'entrée.
- **Échec** (choix du joueur) : la **mort** fait sortir — soigné, ramené, **inventaire gardé**,
  sac perdu, clé non rendue. De même `/donjon quitter` et la déconnexion. Sans plus personne
  dedans, le palier est échoué et la région effacée.
- Les parties ne vivent qu'en mémoire : un arrêt du serveur ramène tout le monde, sans rien
  livrer ; qui se reconnecte dans la dimension sans partie y est renvoyé à son entrée.

### Depuis le 2026-11-14 : modes, boss Dark, nouvelles salles

Tout ce qui précède décrit le **mode Normal**. Les modes Facile et Difficile, les boss Dark (paliers
en 0 du Difficile), les salles d'élite, de piège et de survie, les douze élites et six capacités de
boss de plus sont décrits dans `docs/CONTENU_DOHY.md` (« Les donjons façon V12 ») ; les boss de
métier, qui se battent dans une salle de boss de ces donjons, au même endroit (« Les boss de
métier »).

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
- **`/profil`** et une vignette du menu `/menu` (le bouton « Profil RPG » au-dessus de l'inventaire
  est parti avec le nouvel inventaire du 2026-11-11, qui ouvre le menu principal) ;
- l'écran de Paladium (refait le 2026-11-24 d'après la capture du joueur, images de PalaRPG) : à
  gauche le nom, le niveau, l'XP et les statistiques en lignes à icônes, onglets **Perso** (l'arbre
  seul) et **Global** (arbre, runes de l'armure portée, vie, résistances et dégâts de l'équipement
  de donjon par donjon) ; à droite l'arbre sur sa croix de lumière — Esquive en haut, Attaque à
  gauche, Critique à droite, Défense en bas —, **Valider** et **Reset** au centre, le compteur des
  points en haut à droite ; l'arbre glisse à la souris (molette ou glisser) ;
- **dix nœuds par branche** (`RpgTree`) : une racine près du centre, puis deux bras qui
  s'écartent ; un nœud se prend quand son parent est pris. Coûts 2, 3, 4, 4, 6, 6 et quatre à 10 :
  65 par branche, comme avant. Ce qu'ils donnent, en points de pourcentage :

  | Branche | Racine → bouts | Total |
  |---|---|---|
  | Attaque | 2, 2, 3, 3, 3, 3, 4, 4, 4, 4 | 32 % |
  | Esquive | 1, 1, 1, 1, 2, 2, 2, 2, 2, 2 | 16 % |
  | Critique | 2, 2, 2, 2, 2, 2, 3, 3, 3, 3 | 24 % |
  | Défense | 2, 2, 2, 2, 2, 2, 3, 3, 3, 3 | 24 % |

  Un clic choisit un nœud, un second le rend (avec ce qui en descend) ; **Valider** achète,
  **Reset** annule les choix ; sans choix en cours, Reset remet l'arbre à zéro contre **25 000 $**
  (ajout : le wiki ne dit pas si un choix validé se défait). L'ancien arbre (huit nœuds en ligne)
  est rendu une fois, à la connexion : les points reviennent, un message le dit.

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

- **Le trixium ne vient plus seulement de la mine** : la commande de trixium des contrats en paie
  16 par jour (`docs/BALANCE.md` § 6 terdecies), soit 480 sur un mois sans manque.
- **Une saison par mois réel**, du 1er à 5 h au 1er suivant à 5 h, heure de Paris (la bascule à
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

---

## 27. Succès (chapitre 23)

Sources : `AchievementsManager.registerAchievements` (PalaMod) et `CommonProxy` (PalaJobs), relus
par **`tools/extract_achievements.py`**. L'outil rejoue au bytecode les chaînes de builders
(`CraftAchievement.builder().id(…).name(…)…build()`, `MultiAchievement.addSubAchievement`,
`register()`) : **139 succès, 315 avec leurs étapes**, dans six catégories (`HOW_TO_START`,
`ECONOMY`, `JOBS`, `ATTACK_DEFENSE`, `FACTION`, `OTHERS`). La bibliothèque qui les affichait
(`fr.paladium.achievement`) n'est pas dans le jar ; le blog de la V8.5 dit seulement « ajout d'un
système de succès » et d'un module Succès dans Helios. **Aucune récompense n'est déclarée** dans
le code.

### En solo (choix du joueur)

- **Une prime en argent par succès**, selon sa difficulté : 10 000, 50 000, 250 000, 1 000 000 ou
  5 000 000 $. Plusieurs succès validés d'un coup (à la première connexion, par exemple) donnent
  un seul bilan dans le chat.
- **126 succès** : les 139 de PalaMod et PalaJobs, moins 13 qui reposent sur des systèmes
  absents (voir plus bas). Les noms, les descriptions et les objectifs sont ceux du code, sauf
  écart noté.
- **Écran** : `/succes` (ou `/achievements`, la commande de Paladium) et une carte du menu
  principal. Six catégories, puis leurs succès, avec les étapes en infobulle.

### Ce qui mesure, et le garde-fou des blocs posés

- **Les statistiques du jeu** quand elles suffisent : objets fabriqués, cassés (usure) et utilisés
  (blocs posés), créatures tuées, distance parcourue à pied.
- **Les blocs cassés** (minerais, récoltes) sont comptés à part, **hors blocs posés par un
  joueur**, comme les contrats et l'XP de métier. La statistique du jeu compterait un minerai posé
  puis recassé, et le minerai se rend lui-même : « casser 500 000 minerais » se validerait sur
  place. Une récolte est une culture mûre, ou une citrouille ou une pastèque qui a poussé.
- **Ce que signalent les objets et les machines** : sève tirée de l'extracteur, âmes du marteau de
  caverne, lingots donnés au totem, captures, Dancaroks, boss d'event (frappé, vaincu), Egg Hunt
  (Volcania frappé puis abattu, œuf pris), dynamites, withers invoqués, TNT en endium, pierres
  légendaires, pierre d'aggro sur un wither, home remover, ventes et achats au shop, argent
  dépensé, portails de l'alchimiste, chaudron formé, Dimension du Mineur (portail, session),
  voidstone de minage, duplication au marteau, terre fertilisée labourée, sorties du crusher et du
  cobblebreaker retirées à la main.
- **Ce qui se constate toutes les cinq secondes** : la dimension où l'on est, la plus grosse
  quantité portée de quelques objets (pollens, graines, fleurs, pépite d'endium, tête et marteau du
  grinder), l'armure complète en endium, les modificateurs du marteau en paladium, une pioche en
  paladium enchantée. Et chaque seconde : l'Unclaim Finder ou le Spawner Finder en main, la
  cagoule portée.
- **Dépenser** (`CresusWithdrawAchievement`) : tout débit consenti (achats au shop, boutique,
  améliorations, dépôts en banque de faction…), pas la pénalité de mort.

### Les 12 succès retirés

Mailbox (« Écrivain »), sorts (« Apprenti sorcier »), Palapass, échanges entre joueurs (trois
« Tradeur »), familier niveau 100, blocs camouflage d'un autre mod (« Expert du camouflage »),
glueballs (« Chewing gum maison »), colle (« Colleur »), bouteilles d'XP scellées (« Mise en
bouteille »), sac à dos de chasseur (« Transport dominical »). Aucun de ces systèmes n'existe
dans DoHy. « Élevage d'éléphants » (7 500 bambous, un bloc en vaut neuf) est revenu le
2026-11-17 avec les éléphants.

### Les écarts

| Succès | PalaMod | DoHy |
|---|---|---|
| Explorateur dimensionnel | visiter six serveurs (Egopolis… Minage) | visiter six dimensions : monde, Nether, End, Mineur, donjons, Warzone |
| Premier enchantement II | enchanter une pioche en paladium | avoir en main une pioche en paladium enchantée (table ou enclume) |
| Une belle omelette II | utiliser le parchemin de dragon | être là quand Volcania tombe (pas de parchemin) |
| Transdimensionnelle III | la commande `/dimension` trois fois | terminer trois sessions |
| Chercheur assisté | utiliser 10 fois un spawner finder | 10 min en main (le finder de DoHy s'use à la seconde) |
| Zoo original | zombie, creeper, tortue, crabe, escargot, dauphin, éléphant | crabe, escargot → panda, perroquet (capturables) ; l'éléphant depuis le 2026-11-17 |
| Petite proie, dur combat | les quatre mini-boss de PalaJobs | les cinq boss d'event |
| Extermina-home | tuer 10 fantômes du home remover | effacer 10 monstres avec le home remover (celui de DoHy efface les monstres du chunk) |
| Doux chez soi | posséder un village de faction | bâtir la réserve d'or |
| Niveaux de faction 10 à 100 | niveau de la faction | 10 % à 100 % de l'arbre de faction débloqué |
| Tout pile le bon nombre, Oula… | déposer 100 000 / 500 000 trixium | 5 000 / 25 000 (un joueur seul, pas des milliers) |
| Fleuriste curieux | dont la fleur de chance (lucky blocks) | les trois fleurs de PalaMod et les treize vanilla |
| Unités de sève | 1 000 par fiole tirée (`ExtractSeveAchievement`) | pareil |

## 28. Armure de voyage

Relevé dans `ItemArmorTravel` : six pièces, toutes au matériau fer vanilla (défense, durabilité
et réparation au fer). La cagoule était portée depuis le pillage (chapitre 22) ; les cinq autres
le sont le 2026-11-11, avec leurs recettes de `PRegister_Craft` et leur couche portée
(`getArmorTexture`).

| Pièce | Effet | Recette |
|---|---|---|
| Bottes de voyage | monter d'un bloc sans sauter (`stepHeight = 1`) | 2 plumes, 2 lingots de titane |
| Jambières de voyage | vitesse III | 3 cuirs, 2 plumes, 2 lingots de titane |
| Plastron de saut | saut III | 2 plumes, 4 cuirs, 2 lingots de fer |
| Casque en slime | aucune chute | 3 boules de slime, 2 diamants |
| Casque de plongée | l'air reste plein sous l'eau (respiration aquatique) | 8 lingots de titane autour d'une vitre |

Les effets passent par `ArmorEffects`, comme ceux des autres armures. Écart : le casque en slime
freinait aussi la chute quand la tête était dans un bloc plein (sauter pour remonter) ; ce cas de
bord n'est pas porté. Les bottes, les jambières, le plastron et le casque en slime sont la
récompense du Fermier au niveau 55, qui est maintenant distribuée.


## 29. Donjons : les modèles de Paladium

Les monstres des donjons étaient des monstres vanilla renommés. Ils portent maintenant les
modèles de Paladium (`config/rpgentities`, fournis par le joueur le 2026-11-11) : les six monstres
de chaque donjon (le petit noyé de Manelios en plus), les vingt mini-boss et les vingt boss —
**65 modèles**, leurs animations, leurs textures par palier (jaune, orange, rouge, noir, comme
la couleur du nom) et leurs sons.

**Le monstre reste un monstre vanilla.** Rien ne change à son IA, à sa boîte de collision ni
aux combats. Le serveur lui pose une *apparence* à l'apparition (`RpgSkins`, attachement
synchronisé `palamod:rpg_skin` : modèle, couleur, échelle) ; le client annule son rendu
(`RenderLivingEvent.Pre`) et dessine le modèle à sa place (`client/rpg`).

| Étape | Où | Ce qui est fait |
|---|---|---|
| Conversion | `tools/rpg_entities.py` | Géométrie Bedrock copiée telle quelle ; animations normalisées (le molang est échantillonné à 20 Hz, les images clés redondantes retirées) ; textures animées découpées en images ; sons réencodés en mono 44,1 kHz (54 Mo → 13 Mo — le jeu ne place dans l'espace qu'un son mono) |
| Géométrie | `RpgGeometry` | Montée en `ModelPart` vanilla à la lecture : conventions de `bedrock_model.py`, UV par face, boîtes tournées en pièces enfants |
| Animation | `RpgAnimator` | D'après ce que le jeu synchronise déjà : attente et marche mélangées selon la vitesse ; attaque (bras balancé, arc bandé, sort, attaque propre du golem, du ravageur, du zoglin, du gardien) ; blessure ; mort, jouée dans la seconde que le jeu laisse ; apparition |
| Sons | `RpgSkins` | Les cris vanilla (attente, blessure, mort) d'un monstre habillé sont remplacés par ceux de son modèle ; ses pas sont tus ; l'attaque joue quand son coup porte |

Tout est dans l'espace de noms `palarpg` (assets et `data/palarpg/rpg_skins.json`, la table
« quel modèle pour quel monstre »), régénérable par
`python3 -I tools/rpg_entities.py <rpgentities> <jar PalaRPG extrait>`.

**Tailles.** Un monstre a la taille de son modèle (le colosse, grossi par son attribut
d'échelle, l'est aussi à l'écran). Un boss garde la taille de Paladium, ramenée à ce que la
salle contient (6,5 blocs de haut, 11 de large : les dragons font 20 blocs d'envergure).
**Écart assumé** : la boîte de collision d'un boss reste celle de son monstre vanilla, plus
petite que le modèle ; on le frappe au corps.

**Attribution des modèles.** Les noms des boss viennent du wiki, les modèles de leurs dossiers
(`talikus_warhog` pour le Sanglier de guerre, `nimbria_spinner` pour la Fileuse…). Deux
correspondances sont des choix : le mage de Manelios est `manelios_totem` (`manelios_mage`,
7,5 blocs de haut, est le boss « Mage de Manelios », comme `vitalys_boss_mage`) ; les
« Jumeaux noyés » sont `manelios_shark`. Les projectiles et les effets de Paladium (nuages,
portails secondaires, tentacules) ne sont pas portés : les attaques restent celles du monstre
vanilla et des capacités de boss du chapitre 18.

**Non repris** : les musiques des salles (non demandées), le sac de frappe.

**Avec Flywheel** (et ses dérivés : Crankshaft, Vanillin). Ces moteurs dessinent les créatures
vanilla par instances, sans passer par le rendu du jeu : `RenderLivingEvent.Pre` n'est jamais
émis, et le monstre de donjon restait un zombie ordinaire. `FlywheelCompat` enveloppe, par
réflexion, le « visualiseur » des types de créature qui portent une apparence : pour une créature
habillée il ne crée rien et rend la main au jeu, pour les autres il laisse faire l'original.
Sans Flywheel, la classe ne fait rien. Vérifié avec Crankshaft 1.6.0 chargé (captures : sans
l'adaptation, six zombies ; avec, les six modèles). L'extraction des états de rendu passe sous
verrou, Crankshaft la faisant sur plusieurs fils.

**Avec Sodium.** Les modèles de Paladium donnent une zone de texture à chaque face ; on les pose
en remplaçant les faces des boîtes vanilla (`Cube.polygons`). Sodium garde, à la construction de
chaque boîte, sa propre copie dépliée « en croix » et dessine celle-là : les faces remplacées
étaient ignorées et les textures sortaient mélangées. `RpgEntityModel` écrit donc ses sommets
lui-même (`draw`, par `submitCustomGeometry`), avec le même calcul que `Cube.compile`, et ne
dépend plus de qui accélère le rendu des boîtes. Vérifié avec Sodium 0.9.2 et Crankshaft chargés
ensemble (captures : ancien rendu, textures mélangées ; nouveau, correctes). **Perdu au passage** :
le contour d'un monstre de donjon sous l'effet Surbrillance, que `submitModel` dessinait.

## 30. Donjons : salles décorées, coffre, jarres, marchands

Demandé le 2026-11-11 : des donjons plus beaux, avec les ressources de Paladium. Le wiki ne
décrit pas les salles ; il donne à chaque donjon un métier, les textures un élément, et les
monstres de `rpgentities` une allure. Les thèmes en découlent (`RoomTheme`) :

| Donjon | Thème | Sol | Murs | Aux murs | Au plafond | Meubles |
|---|---|---|---|---|---|---|
| Talikus (Fermier) | grange et serres | terre battue, mousse, fleurs | épicéa sur torchis, piliers de chêne | serres vitrées (azalées) | poutres de chêne noir, racines, fleurs à spores | bottes de foin, citrouilles, pastèques |
| Nimbria (Mineur) | galerie de mine | ardoise, tuf, une voie ferrée | briques d'ardoise, étais de chêne | **filons de minerais de Paladium** (améthyste, titane, paladium) et d'or, diamant, émeraude | étais, stalactites | blocs de minerai brut, améthystes |
| Vitalys (Chasseur, monstres de feu) | forge | pierre noire, braises affleurantes | briques du Nether, basalte poli | vitraux orange sur de la lave | lianes pleureuses | colonnes de basalte, os |
| Manelios (Alchimiste) | temple englouti | prismarine, lanternes de mer affleurantes | prismarine, colonnes de quartz | aquariums (eau, coraux) | cristaux d'améthyste | prismarine, cristaux |

Communs à toutes : une bordure et un médaillon au sol, une assise et un couronnement aux murs,
une fenêtre haute par travée (murs nord, est, ouest ; le mur sud, où s'ouvrent les portes, reste
nu), un plafond à caissons — une poutre dans l'axe de chaque pilier, une lanterne pendue à
chaque croisement, au bout d'une chaîne plus longue dans la salle du boss. La salle de boost a
son autel entre quatre lanternes, la salle du marchand un étal. Les obstacles gardent leur rôle
de couvert ; plantes, mousse et rails se traversent. **Rien ne change au jeu** : mêmes
dimensions, mêmes points d'apparition (une case occupée est évitée), mêmes boutons d'énigme
(les fenêtres sont au-dessus). Les niches des fenêtres dépassent de deux blocs ; la salle les
efface avec le reste.

### Le décor animé de PalaRPG

Le coffre, la jarre et les deux marchands viennent du jar PalaRPG 2.0.0
(`assets/palarpg/models/blocks/dungeon`, `…/entities/dungeon/merchant`), modèles, animations et
sons. Ils passent par le rendu des monstres (§ 29) : `RpgSkin.pose` nomme l'animation jouée, et
la changer côté serveur la relance chez les clients (`"open>open_idle"` : la première une fois,
puis la seconde).

- **Coffre** (`DungeonProps`) : à la fin d'une salle, à la place du coffre vanilla. Commun après
  un combat, rare après une énigme ou un mini-boss, légendaire après un boss — la couleur ne
  change pas le contenu. Clic droit : le premier joueur l'ouvre, il reste ouvert ; chacun son
  butin, comme avant. Sons de PalaRPG (apparition, ouverture par rareté).
- **Jarres** : trois à six par salle de combat, d'énigme ou de boss, contre les murs. Un coup
  (ou un clic droit) la brise ; **une fois sur quatre** elle lâche un tirage de la réserve du
  donjon, dans le sac (voir `BALANCE.md`).
- **Marchands** : Mammon ou Mephisto, au hasard, à la place du villageois. Même boutique.

Coffre et jarres sont portés par une entité immobile, muette et invulnérable (un cochon sans
IA dont on ne voit que le modèle), qui n'est pas un monstre et ne compte pas pour finir la salle.
**Écart** : PalaRPG en fait des blocs ; ici ce sont des objets posés, ce qui évite un rendu de
block entity et réutilise les animations telles quelles. Le « hub » de PalaRPG n'est pas porté.

## 31. Le clicker

Demandé le 2026-11-11, d'après le wiki (« Le Clicker ») et le mod d'origine, **PalaClicker 6.1.2**,
trouvé dans le client Paladium du joueur. On clique un aliment pour nourrir les namuus ; les
**ClicCoins** achètent des bâtiments qui en produisent chaque seconde, des améliorations, et les
offres tournantes du Clic Shop. `/clicker`, ou la carte « Clicker » du menu.

### Les règles — relevées au bytecode du jar

| Règle | Valeur | Source |
|---|---|---|
| Valeur d'un clic | `1 × (1 + somme des améliorations du clic)` : 1, 2, 4… jusqu'à 2²⁵ | `PlayerClickerData.getClickValue` |
| Cadence | un clic par 50 ms au plus (vingt par seconde) | `BBPacketClickerClick` |
| Production d'un bâtiment | `0,1 × 1,8^rang × exemplaires × (1 + somme de ses améliorations)` par seconde | `ClickerBuilding.getRPS`, `PlayerClickerData.getRPS` |
| Production totale | 0,5 par seconde, plus les bâtiments ; seulement en jeu chez Paladium, **aussi hors ligne dans DoHy** (voir plus bas) | `ClickerTask` |
| Prix d'un bâtiment | prix de base × 1,1^exemplaires, 99 exemplaires au plus | `ClickerBuilding.getPrice`, `CSPacketClickerBuildingBuy` |
| Prix d'une offre | prix + un tiers du prix par achat déjà fait | `ClickerShopItem.getPrice` |
| Rayons du Clic Shop | semaine : 8 offres, 168 h ; jour : 4 offres, 24 h ; métier : 4 offres, 168 h | `ClickerShopType` |
| Tirage d'un rayon | autant de tirages pondérés que de places, parmi les offres que les bâtiments du joueur ouvrent (`requirement`) et n'ont pas retirées (`forbidden`) ; un doublon ne compte pas | `ShopGlobalConfig.getRandomItem` |

Les sept types d'amélioration (`ClickerUpgrade.apply`) et leurs conditions
(`checkConditions` : pièces récoltées, jours de saison, bâtiments possédés, amélioration possédée) :

| Type | Effet | Exemplaires demandés (relevés, depuis le 2026-11-15) |
|---|---|---|
| Clic (25) | double le clic | l'amélioration précédente |
| Production globale (10) | +10 % sur tout | la précédente |
| Connaissance de terrain (4) | +1 % par niveau du métier, sur sa catégorie | aucun |
| Amélioration de bâtiment (70) | +100 % | 3 (niveau 1), 7 (niveau 2) — le wiki disait 5 et 10 |
| Production nombreuse (35) | +1 % par exemplaire | 2 — le wiki disait 7 |
| Production postérieure (26) | +1 % par exemplaire du bâtiment précédent | 4 |
| Amélioration de catégorie (25) | +50 % (+10 % pour les quatre outils en paladium) | relevée : 1 ou 3 exemplaires d'un bâtiment ; sinon (wiki) un de chaque bâtiment de la catégorie |

### La production hors ligne (DoHy, 2026-11-14)

Demandé par le joueur : « modifie le clicker pour qu'il fonctionne même joueur déconnecté ». Chez
Paladium, `ClickerTask` ne crédite que les joueurs connectés. Dans DoHy, la production est **versée à
la seconde réelle** : le clicker garde jusqu'à quelle seconde elle est payée (`paid`), chaque seconde
de jeu verse ce qui s'est écoulé (un serveur qui rame ne la ralentit plus), et **à la connexion
toute l'absence est rattrapée** au rythme des bâtiments, serveur éteint compris — **trente jours au
plus**, garde-fou contre une horloge qui saute. Le chat dit ce que l'absence a rapporté (au-delà
d'une minute). Un clicker d'avant ce changement repart de la première connexion qui suit : rien
n'est versé pour le temps d'avant.

### Les tables — reconstituées

Le jar ne contient pas les tables : chez Paladium, ce sont trois fichiers du serveur.
`tools/clicker_data.py` écrit `data/palamod/clicker.json` à partir de trois sources.

- **Les traductions du client** (`config/translations/fr_FR.trad`) : les identifiants et les noms
  des 35 bâtiments, des 21 catégories et des 195 améliorations. Ce sont elles qui disent
  lesquelles existent (26 bâtiments seulement ont une production postérieure).
- **Les relevés de la communauté** ([BroMinee/PaladiumClicker](https://github.com/BroMinee/PaladiumClicker),
  `src/assets`) : le prix des 35 bâtiments ; le prix et les conditions des 25 améliorations du
  clic, des 10 globales, des 4 de terrain, de celles des seize premiers bâtiments, et de huit
  catégories.
- **Le wiki** : l'effet de chaque type et le nombre d'exemplaires demandé (tableau ci-dessus).

**Ce qui est prolongé ou choisi**, faute de relevé :

- Bâtiments 17 à 35 : le prix de leurs améliorations suit le multiple que les relevés suivent dès
  le quatrième bâtiment (×9, ×19, ×4, ×2,25 du prix du bâtiment), et leur jour avance de 5,5 par
  bâtiment — le dernier tombe au jour 147, celui de la dernière amélioration globale relevée.
- Catégories : huit sont relevées, les treize autres sont déduites du nom des bâtiments (le
  Kraken est « Créature hostile », « Profondeur » et « Sous l'eau »). Une amélioration de
  catégorie sans prix relevé vaut quatre fois le dernier bâtiment de sa catégorie. Les quatre
  outils en paladium gardent leur relevé (+10 %, un seul bâtiment demandé).
- Quand le wiki et les relevés divergent sur un nombre d'exemplaires (5 et 10 contre 3 et 7 pour
  les bâtiments, 7 contre 2 pour la production nombreuse), **les relevés l'emportent depuis le
  2026-11-15** (le joueur : « pour le clicker aide-toi de » l'optimiseur PalaTracker, qui les
  reprend). Avant, le wiki l'emportait. Les catégories relevées gardent aussi leur condition
  (trois exemplaires d'un bâtiment pour « Technique de persuasion », « Manteau épais », « Potion
  contre le mal de mer » et « Lame de hache en Endium »).
- Les icônes : PalaClicker montre un objet par bâtiment et par amélioration, donné par le
  serveur. Ici, un objet du jeu ou du mod qui dit la même chose (une pioche en bois pour la Mine
  abandonnée, la pioche en paladium pour « Pioche en Paladium »).
- La production de base suit la formule du jar (`0,1 × 1,8^rang`), pas les relevés, qui s'en
  écartent de moins de 1 %.

### L'optimiseur PalaTracker (2026-11-15)

[palatracker.bromine.fr/clicker-optimizer](https://palatracker.bromine.fr/clicker-optimizer), du même
auteur que les relevés. Ses neuf tables (bâtiments, clic, améliorations de bâtiment, de catégorie,
globales, nombreuses, postérieures, de terrain, métiers) sont **identiques octet pour octet** à
celles de `BroMinee/PaladiumClicker` déjà utilisées (empreintes comparées le 2026-11-15). Ses
formules sont celles de DoHy : production `base × (1 + 0,1 × globales + 0,01 × niveau du métier
(terrain) + améliorations de bâtiment + 0,01 × exemplaires (nombreuse) + 0,01 × exemplaires du
précédent (postérieure) + catégories) × exemplaires`, base `0,1 × 1,8^rang`, prix `× 1,1` par
exemplaire, conditions « pièces récoltées », « jour de saison », « exemplaires d'un bâtiment ».
Seuls les nombres d'exemplaires différaient (voir plus haut) : ils suivent maintenant les relevés.

### Le Clic Shop

Ses offres ne sont relevées nulle part : la table est écrite pour DoHy (`SHOP` dans le script),
avec les objets du mod — lingots, diamants, bouteilles d'XP, clés de donjon, bouteilles d'XP de
métier, puis endium. Chaque offre s'ouvre avec un bâtiment et coûte une part de son prix de
base ; les premières se retirent quand le joueur avance. Détail et garde-fous : `BALANCE.md`
§ 6 sexies.

### Écarts avec l'original

1. **La production ne commence qu'à la première ouverture.** L'original crédite 0,5 par seconde
   à tout joueur connecté, clicker ouvert ou non.
2. **Il faut posséder un bâtiment pour acheter le suivant.** L'original ne faisait que le cacher
   dans l'écran ; son serveur acceptait l'achat.
3. **La « saison » est l'âge du clicker du joueur**, en jours réels depuis sa première ouverture.
   Chez Paladium c'est une date commune à tout le serveur.
4. **Un achat du Clic Shop se confirme d'un second clic**, au lieu d'une fenêtre.
5. Le classement « ClicCoins gagnés » et l'onglet du profil ne sont pas portés.
6. **Le Clic Shop se remplit sans attendre** (demandé le 2026-11-14). Chez Paladium, un rayon
   n'est tiré qu'à sa rotation : tiré à la première ouverture, quand aucun bâtiment n'ouvre encore
   d'offre, il restait vide une semaine (offre de la semaine, offre métier) ou un jour (offre du
   jour). Ici, un rayon incomplet prend aussitôt les offres qui s'ouvrent — achats et minuterie
   gardés ; un rayon vide qui reçoit ses premières offres commence une rotation entière. Une offre
   déjà tirée sort du tirage : le rayon n'a de place vide que s'il y a moins d'offres ouvertes que
   de places (chez Paladium, un doublon laissait une place vide). Un rayon vide dit quel bâtiment
   l'ouvre (« S'ouvre avec Mine des nains »).

### L'écran

`ClickerScreen` reprend la disposition de `UIClicker` (portefeuille, production et aliment à
gauche ; améliorations sous leur flèche et Clic Shop au centre ; bâtiments à droite) et ses
textures (pièce, halo, étals, namuu). Le serveur n'envoie pas une vue par clic : les clics
partent par paquets toutes les 200 ms, et entre deux vues (une toutes les trois secondes, et
après chaque achat) le compteur avance côté client, puis se recale. Les bâtiments après le
prochain à acheter restent cachés (« ??? »), comme le dit le wiki.

## 32. Contrôleur de spawners

Demandé le 2026-11-11, d'après le wiki (« Contrôleur de spawners », « Spawner customisé ») et le
mod d'origine, **PalaSpawner 7.0.0**, trouvé dans le client Paladium du joueur ; les plans des
structures et les recettes sont dans PalaMod (`modules/aspawner`). Tout est dans
`palamod/hunter` : `SpawnerBlueprint`, `SpawnerTier`, `SpawnerControllerBlock(Entity)`,
`SpawnerLoot`, `SpawnerUpgrade(Item)`, `SpawnerControllerEvents`.

### Les structures — `First` à `FourthSpawnerBluePrint`

Quatre plans recopiés étage par étage, chacun contenant le précédent. Le centre est un bloc de
paladium vert ; le contrôleur est au deuxième étage, sur un bloc de paladium, au milieu d'un
bord du carré central de 7 × 7. La structure se bâtit dans les quatre sens.

| Palier | Taille | Obsidienne | Obsidiennes infusées | Spawners comptés |
|---|---|---|---|---|
| I | 7 × 7 × 3 | 20 | 4 diamant | 27 |
| II | 11 × 11 × 3 | 36 | + 8 améthyste | 54 |
| III | 13 × 13 × 4 | 72 | + 12 titane | 81 |
| IV | 17 × 17 × 5 | 132 | + 16 paladium | 108 |

Toutes demandent en plus 5 blocs de paladium, 1 bloc de paladium vert et 16 sables des âmes. Le
wiki donne 13 × 13 × 4 au palier IV ; le plan du jar dit 17 × 17 × 5, et les quantités du wiki
(132 obsidiennes) sont celles du plan. Les plans sont essayés du plus grand au plus petit, comme
PalaMod les enregistre : une structure IV dont on casse un coin redevient une structure III.

**Ajout** : cliquer un contrôleur dont la structure est incomplète dit combien de blocs sont en
place, lequel manque et où, et fait scintiller l'endroit. Un plan de 17 × 17 × 5 ne se devine pas.
Accroupi, main vide, le même message porte sur le palier suivant d'une structure déjà valide.

### Le contrôleur — `TileEntitySpawnerController`

- **Une vérification par seconde** : la structure, puis les spawners customisés du volume de
  7 × 7 × 3 au-dessus du centre (la colonne du contrôleur exceptée), jusqu'au plafond du palier.
  Un spawner sous 100 âmes compte dans le plafond mais ne produit rien ; un spawner d'un palier
  supérieur à la structure travaille au palier de la structure ; un spawner au-delà du plafond
  est lié lui aussi et ne fait plus rien.
- **Les vagues**, par palier de spawner : toutes les 12 / 15 / 15 / 7 secondes, 2 à 3 / 3 à 5 /
  5 à 7 / 6 à 11 créatures par spawner. Un seul tirage par groupe de spawners de la même créature
  et du même palier, multiplié par leur nombre.
- **Mode manuel** : les créatures s'accumulent sur une créature immobile posée sur le contrôleur,
  son nombre au-dessus. Un coup en tue une (un coup toutes les dix ticks au plus) : butin de sa
  table vanilla — l'enchantement Butin de l'arme compte —, 5 points d'XP (2 pour un slime) et
  l'XP de métier d'une créature tuée. Le butin va dans un coffre de la structure s'il y en a un,
  sinon aux pieds du joueur. Clic droit : créature suivante.
- **Mode automatique** : le butin va droit dans les coffres, sans XP. Sans coffre ayant une case
  libre, la vague attend. Changer de mode vide les créatures en attente.
- **Les coffres** : cinq cases en ligne au niveau du sol de la structure, une case au-delà du bord
  du carré central, sous le contrôleur (`getInventoriesLocations`). Tout inventaire convient.
- **Un seul contrôleur par tronçon** travaille (celui de plus petite position) ; l'interface ne
  s'ouvre que sur une structure valide.
- Le contrôleur ne travaille que dans un tronçon chargé : il faut un joueur à proximité.

### Les améliorations — `SpawnerUpgradeRegistry`

Deux cases libres, deux verrouillées ; une amélioration par case.

| Amélioration | Effet | Métier |
|---|---|---|
| Vitesse | un dixième de temps en moins entre deux vagues | Mineur 85 |
| Butin | 10 % de chances, chacune, qu'une créature rende son butin deux fois (pas un slime) | Chasseur 75 |
| Slime | 0,11 % de chances, chacune et par créature de la vague, d'un slime en plus | Alchimiste 75 |
| Capacité | ouvre les deux cases verrouillées et autorise les doublons ; une seule | Alchimiste 90 |

Sans capacité : deux améliorations différentes. Avec : trois autres, doublons permis (trois
vitesses : 30 % de temps en moins). Retirer la capacité fait tomber ce qu'elle seule permettait.
Le niveau de métier est celui du **dernier joueur à avoir ouvert le contrôleur** (`putJobData`),
relevé à l'ouverture ; les objets ont aussi leur verrou ordinaire dans `data/jobs.json`.

### Les recettes — `PPalaSpawnerCommonProxy.registerCrafts`

- **Obsidiennes infusées** : quatre du métal aux coins (diamant, lingot d'améthyste, de titane,
  de paladium), quatre de l'obsidienne du palier précédent (obsidienne ordinaire pour le I) en
  croix, un bloc de paladium au centre.
- **Contrôleur** : six obsidiennes en haut et en bas, deux paladiums compressés, un spawner vide
  au centre.
- **Vitesse, butin, slime** : la même grille, avec au centre un sucre, une épée en paladium, un
  slimepad.
- **Capacité** : quatre obsidiennes aux coins, un crâne de wither squelette en haut, deux
  pépites d'endium, le modificateur « plus d'améliorations » du grinder au centre, un spawner
  vide en bas.

### Écarts avec l'original

- **La créature du mode manuel** est la créature elle-même, sans IA, muette et invulnérable,
  et non une entité à part. Elle n'est jamais relue du disque : le contrôleur la repose.
- **Le butin** vient de la table vanilla de la créature, pas d'une copie du butin 1.7.10. En mode
  automatique, sans joueur, il n'y a donc pas de butin rare (fer, carotte, pomme de terre du
  zombie) ; l'original en donnait dans 2,5 % des cas.
- **Hors structure**, le spawner customisé garde son comportement vanilla (voir §13). Posé dans
  le volume d'une structure valide, il est lié à la pose, sans attendre la seconde suivante.
- Les six créatures restent celles du marteau de caverne : zombie, squelette, araignée, araignée
  venimeuse, slime, sorcière.
- Les minuteries des vagues repartent au chargement du tronçon.
- En difficulté Paisible, la créature du mode manuel disparaît comme tout monstre : seul le mode
  automatique y est utilisable.

## 33. Boxes de la boutique

Demandé le 2026-11-11 (« la boutique », puis « boxes animées »). Le mod d'origine est
**PalaShop 7.8.0**, trouvé dans le client Paladium du joueur : six boxes sous
`assets/palashop/boxes/<nom>` (améthyste, paladium, endium, trixium, saharia, halloween), chacune
avec un modèle Bedrock de 78 à 112 os, une texture de 128 × 128, des animations au format
GeckoLib et une bande-son par classe de lot. Tout est dans `palamod/box` : `BoxKind`,
`BoxBlock`, `BoxBlockEntity`, `BoxTimings`, `BoxEvents`, `PalaBoxes`.

### Ce qui est repris

- **Les six modèles**, leur texture et cinq animations chacun : l'attente (`idle`) et les quatre
  ouvertures complètes (`fast_common`, `fast_rare`, `fast_epic`, `fast_legendary`), avec leurs
  rayons, particules et faisceaux, qui sont des os du modèle. Les ouvertures « lentes », en
  plusieurs temps, que Paladium pilotait depuis sa roulette en surimpression, ne sont pas reprises.
- **Les quatre bandes-son** de chaque box (`sound/fast/<classe>.ogg`), soit 24 sons
  `palarpg:box.<nom>.fast_<classe>`.
- **Les repères** `open` et `close` de la ligne de temps de chaque animation : le moment où le lot
  se montre et celui où le coffre se referme. `tools/rpg_entities.py` les écrit dans
  `data/palarpg/boxes.json`, que le serveur lit (`BoxTimings`) — il n'a pas les animations.
  Ils sont les mêmes pour les six boxes :

| Classe du lot | Durée | Le lot se montre | Le coffre se referme |
|---|---|---|---|
| Commune | 7 s | 0,9 s | 6,25 s |
| Rare | 7 s | 1,85 s | 6,3 s |
| Épique | 10 s | 4,15 s | 9,25 s |
| Légendaire | 13 s | 4,45 s | 12,25 s |

- **Les vignettes** de la boutique (1000 × 1000), réduites à 32 × 32 pour l'icône de l'objet
  (`tools/palamod_assets.py`, `shrink_icon`).

### Ce que c'est dans DoHy

Dans Paladium, la box est une entité (`EntityBox`) que la boutique fait apparaître devant le
joueur, le temps d'une ouverture payée en argent réel. Ici, **les six boxes sont les loot boxes du
mod** : chacune a sa clé, son prix et sa table de butin (`data/crates.json`, sous l'identifiant de
la box). Il y a deux façons d'en ouvrir une, avec la même clé et le même tirage (`Crates.pull`) :

- **à la roulette** : la carte de la box à la boutique (section BOXES), `/crate open <box>`, ou
  clic droit dans le vide avec la clé ;
- **sur son coffre posé** : un bloc acheté une fois à la boutique (25 000 $, section COFFRES À
  POSER) et gardé, qui joue l'animation de Paladium.

| Box | Clé | Thème | Avant |
|---|---|---|---|
| Améthyste | 100 000 $ | matériaux du début | la caisse commune |
| Saharia | 1 M$ | pillage | — (ouvrait la caisse commune) |
| Paladium | 5 M$ | endium, voyage | la caisse rare |
| Halloween | 20 M$ | chasse et withers | — (ouvrait la caisse légendaire) |
| Trixium | 50 M$ | voyage, endium en lingots | — (ouvrait la caisse rare) |
| Endium | 150 M$ | panoplies, pierres, gant | la caisse légendaire |

**Le regroupement (2026-11-11).** Il y avait trois caisses de DoHy (commune, rare, légendaire),
ouvertes à la roulette, et six boxes de PalaMod qui les ouvraient deux par deux. Le joueur a
demandé de tout regrouper dans les boxes de PalaMod, avec **six butins** et **la roulette
gardée**. Les trois caisses sont donc devenues les boxes d'améthyste, de paladium et d'endium —
mêmes prix, mêmes tables — et Saharia, Halloween et Trixium ont reçu chacune une table et un prix
(`BALANCE.md` § 6 quater). Les six portent des lots de NTM NEXT, qui sortent du tirage si le mod
n'est pas là. Rien n'est perdu d'un monde d'avant :

- les objets `crate_key_common`, `_rare` et `_legendary` restés dans un coffre ou un inventaire
  deviennent `crate_key_amethyst`, `_paladium` et `_endium` au chargement (alias de registre,
  `PalaBoxes`) ;
- les clés encore comptées sous les anciens noms sont converties en objets à la connexion
  (`CrateKeys.convert`) ;
- `/crate open rare` et les anciens noms restent compris (`Crates.canonical`).

Sur le coffre posé :

- **Clic droit** : s'il y a une clé de cette box dans l'inventaire, le lot est tiré et donné tout de suite, le coffre
  joue l'animation de la classe du lot avec sa bande-son, le lot apparaît au-dessus au
  repère `open` (et le message du gain tombe à ce moment), puis disparaît au repère `close`.
- **Accroupi, main vide, clic droit** : achète une clé de cette box.
- Un coffre déjà en train de s'ouvrir refuse un second clic ; sans la bonne clé, il dit le prix.

### Comment c'est fait

- **Le bloc ne dessine rien** (`RenderShape.INVISIBLE`, modèle réduit à sa texture d'éclats).
  C'est un cube plein qui reçoit les clics, non occultant pour ne pas assombrir ce qu'il contient.
- **Le coffre est une créature** : un cochon sans IA, sans gravité, muet et invulnérable, au
  centre du bloc, qui porte le modèle par `RpgSkin` — le rendu des monstres de donjon (§ 29) le
  dessine et l'anime, et il passe donc par les mêmes compatibilités Crankshaft et Sodium. Sa
  boîte de collision tient dans le cube : on ne peut ni le viser ni le pousser.
- **Rien n'est sauvegardé** : ni le coffre ni le lot montré ne reviennent du disque
  (`BoxEvents.onJoin`), la block entity refait le coffre à son premier tick. Le lot est déjà
  dans l'inventaire au moment où l'animation commence : un monde quitté en pleine ouverture ne
  perd rien.
- **Rejouer une animation** : la pose envoyée est `n<compteur>>fast_<classe>>idle`. Le premier
  terme n'existe pas dans le modèle et est sauté ; il ne sert qu'à changer la chaîne, pour que
  deux lots de même classe à la suite relancent l'animation chez le client.
- **Le lot montré** est un `Display.ItemDisplay` à mi-taille, tourné vers qui le regarde. Ces
  réglages n'ont pas de mutateur public en 26.2 : ils passent par les données de l'entité
  (`load`), l'objet par `getSlot(0)`. De l'argent se montre par une émeraude.

### Les clés — des objets (2026-11-11)

Les clés étaient un compteur attaché au joueur. Ce sont six objets, un par box :
`palamod:crate_key_amethyst`, `_saharia`, `_paladium`, `_halloween`, `_trixium` et `_endium`
(`CrateKeyItem`), avec l'illustration de la clé de chaque box dans PalaShop (`boxes/<nom>/item.png`).
`CrateKeys` est le seul endroit qui les compte, les prend et les donne :

- la boutique et `/crate buy` **donnent l'objet** ; ce qui ne tient pas dans l'inventaire tombe
  aux pieds ;
- la roulette et les coffres posés **prennent la clé dans l'inventaire**, celle de la main d'abord ;
- **clic droit dans le vide** avec une clé : ouvre sa box à la roulette ;
- une clé n'ouvre que sa box : une clé Saharia ne fait rien sur le coffre Halloween ;
- une table ajoutée par un pack de données n'a pas d'objet : ses clés restent comptées
  (`BoostData.keys`), et tout le reste marche pareil.

Elles se trouvent en butin, chacune à sa source : coffre de la note des donjons (Améthyste,
Paladium, Trixium, Endium), boss d'événement (Améthyste, Paladium, Halloween), base ennemie pillée
(Saharia) — `BALANCE.md` § 6 nonies. Et au marché, en promotion (§ 34).

Vérifié en jeu par captures (regroupement, 2026-11-11) : les six tables chargées, toutes
tirables ; les trois anciens identifiants de clé rechargés sous les nouveaux (registre et pile
sauvegardée) ; 3 communes, 1 rare et 2 légendaires comptées → 3 clés Améthyste, 1 Paladium,
2 Endium, compteur vide ; achat sous l'ancien nom (`rare`) ; refus d'une box inconnue ; la
boutique et ses deux sections ; la roulette des trois boxes neuves ; un vrai clic du client, clé
Saharia en main, sur le coffre Saharia ; une clé Saharia refusée par le coffre Halloween ; la
fréquence des clés sur 40 000 coffres de note par cas. Non vérifié : la clé Saharia d'une base
pillée, la clé Halloween d'un boss, et l'annonce d'une clé au marché.

### Les rayons — une seconde passe de rendu

Les effets de l'ouverture sont des os du modèle, animés comme le reste : rayon de la couleur de
la classe (vert, bleu, violet, rouge), faisceaux, anneau, éclats. Les **rayons** sont faits de
deux boîtes : une boîte dont la texture est un dégradé d'opacité, dans une coque à peine plus
grande, de taille **négative** (donc retournée), d'une couleur claire et opaque. Dessiné comme un
monstre de donjon — en découpe, toutes faces visibles — cela donne un bloc plein de la couleur
de la coque : c'est ce que montraient les premières captures.

`entity/index.json` porte donc, pour un modèle, la liste de ses os lumineux (`glow` : les os
`vfx` des boxes, l'onde de Saharia, l'aura d'Halloween ; le générateur l'écrit). Eux et leurs
descendants sont sautés par le dessin ordinaire et dessinés à part (`RpgEntityModel.drawGlow`,
second `submitCustomGeometry` de `RpgClient.onRender`) :

- en `entityTranslucent`, pour que le dégradé soit un dégradé ;
- en **pleine lumière**, pour qu'un rayon éclaire la nuit ;
- **sans les faces qui tournent le dos à la caméra**, triées à la main puisque le modèle écrit
  ses sommets lui-même : de la coque retournée il ne reste que le fond, derrière le rayon.

Aucun monstre de donjon ne déclare d'os lumineux : leur rendu est inchangé. Sept d'entre eux ont
un os nommé `vfx` ; les passer dans cette seconde passe se ferait en une ligne de générateur,
mais demande de regarder chaque modèle.

### Vérifié en jeu (captures, 2026-11-11)

Sans mod de rendu, puis avec les **Crankshaft 1.6.0 et Sodium 0.9.2** du joueur : les six boxes
au repos ; les quatre ouvertures (lot montré au bon repère : à 1,5 s seule la box commune montre
le sien) ; rayons translucides, de face, de côté, de dos, de nuit ; un vrai clic droit du client
sur le bloc (le viseur touche le bloc, pas le coffre) avec une clé légendaire achetée accroupi
— clé prise, lot donné, message du gain au repère ; refus sans clé ; box cassée en pleine
ouverture (coffre et lot montré disparaissent) ; section BOXES de la boutique.

Non vérifié : **les sons** (la machine de captures n'a pas de carte son — les 24 fichiers sont
convertis et déclarés, rien de plus), l'achat d'une box depuis la boutique jusqu'à l'inventaire,
et le rechargement d'un monde avec une box posée.

### Écarts avec l'original

- Le butin de chaque box est celui de DoHy : les lots de PalaShop (cosmétiques, grades, points
  boutique) n'ont pas d'équivalent ici. Les thèmes de Saharia, Halloween et Trixium sont un choix
  de DoHy, pas un relevé.
- Pas de roulette en surimpression ni de vue « caméra » : l'ouverture se regarde dans le monde.
- Le modèle est dessiné de 0,45 à 0,65 fois sa taille d'origine selon la box, pour tenir sur un bloc.

## 34. Marché — l'hôtel des ventes, pour un joueur seul

Demandé le 2026-11-11 (« un marché remixé pour le solo »). Le mod d'origine est **Market 7.1.0**
(`palamarket`), trouvé dans le client Paladium du joueur : un hôtel des ventes **entre joueurs**
(`/market`), dont le client ne contient que l'écran et les paquets — ni prix, ni objets, tout
vient du serveur de Paladium. Il n'y a donc rien à porter : c'est une conception, faite avec le
joueur. Ses choix : un **hôtel des ventes simulé**, qui vend des **raretés de PalaMod** et des
**clés et boxes**, et **n'achète rien** (la vente reste au shop et aux contrats). PalaTrade
(l'échange direct entre deux joueurs) n'a pas de sens en solo et n'est pas porté.

Tout est dans `fr.atraxterra.dohy.auction` : `AuctionConfig` (la réserve et les acheteurs),
`AuctionHouse` (les annonces du jour, l'achat), `AuctionSales` (ce que le joueur met en vente),
`AuctionData` (les achats du jour et les ventes du joueur), `AuctionController` (l'écran).

Le 2026-11-19, le joueur a demandé « un système d'Auction House si c'est possible en solo » :
l'hôtel des ventes **achète** maintenant aussi (plus bas, « Vendre »), et il est devenu l'onglet
Hôtel des ventes du commerce (`INTERFACES.md`, lot 11).

### Ce que le joueur voit

`/hdv`, `/market`, ou l'onglet Hôtel des ventes du commerce : ses propres ventes, puis douze
annonces de vendeurs fictifs, tirées dans quatre rayons et montrées en une seule section.

| Rayon | Annonces par jour | Ce qu'on y trouve |
|---|---|---|
| Reliques | 3 | pierres légendaires, gant d'endium, enderchest de poche, fabrique à nuage, amulette du chasseur, endium |
| Donjons | 4 | fragments et runes, essences des quatre éléments, équipement basique et renforcé des quatre donjons |
| Chasse et wither | 2 | améliorations de spawner, obsidienne infusée, pierre de capture, gemmes de wither |
| Clés et boxes | 3 | les six clés, les six coffres à poser |

Chaque annonce a un **lot** (combien d'objets par achat), un **stock** (combien de lots), un
vendeur et un **prix**. La bulle donne la cote de l'objet et l'écart du prix à cette cote ; une
bonne affaire a un liseré vert et sa remise en pastille, un vendeur gourmand un liseré rouge.
Les annonces changent **chaque jour**, à la bascule des contrats ; « Nouvelles annonces » les
remplace tout de suite contre de l'argent.

### Comment les annonces sont faites

- **La réserve** est `data/auction.json`, écrit par `tools/market_offers.py` : 89 offres, avec
  leur cote, leurs bornes de lot et de stock, et leur poids dans le rayon. La **cote** est la
  valeur boutique de `tools/crate_values.py` (prix d'achat au shop, sinon coût des ingrédients,
  sinon cote fixée) ; ce qui n'en avait pas en reçoit une dans le script, avec sa raison
  (`QUOTES` : runes, essences, équipement de donjon).
- **Le tirage** : par rayon, autant d'offres que d'annonces, sans remise, au poids. Une offre
  dont l'objet n'existe pas dans la partie sort du tirage.
- **Le prix** est la cote, fois le lot, fois un facteur : bonne affaire (15 % des annonces,
  0,70 à 0,88), prix courant (65 %, 0,98 à 1,25), vendeur gourmand (20 %, 1,30 à 1,60), arrondi à
  trois chiffres. Les **clés et les boxes** ont leurs propres bornes, 0,75 à 1,00 : jamais plus
  cher qu'à la boutique.
- **Rien n'est sauvegardé des annonces** : elles se recalculent à l'identique de la graine du
  monde, du jour et du nombre de renouvellements payés. Seuls les achats du jour et ce compteur
  sont gardés (`AuctionData`, conservé à la mort).
- **Le renouvellement payé** : 250 000 $, puis le double à chaque fois, quatre fois par jour.
  Il remplace toutes les annonces, achetées ou non.
- `/dohy reload` relit la réserve.

### Vendre (2026-11-19)

En solo, personne en face : des **acheteurs fictifs** prennent ce que le joueur met en vente. Choix
du joueur : des acheteurs qui achètent plus ou moins vite selon le prix (plutôt que des enchères
contre des PNJ), et seulement **ce que le shop refuse** (ce que le shop rachète reste au shop).

- **Ce qu'ils prennent** : les mêmes raretés que celles des vendeurs fictifs (la réserve), sauf ce
  que le shop rachète (la pépite d'endium) et ce qui se fabrique surtout avec des matières du shop
  (`buyers: false` dans la réserve) — le vendre ici reviendrait à vendre ces matières sans que leur
  prix baisse. `tools/market_offers.py` calcule, recette par recette, la part de la cote qui vient
  de matières que le shop vend ou rachète ; au-delà de 30 %, non. Sont ainsi écartés : l'amulette
  du chasseur, le gant et le lingot d'endium, les pierres de capture, les quatre améliorations de
  spawner, les deux obsidiennes infusées, les trois gemmes de wither (leurs têtes viennent de crânes
  de wither squelette). Restent 78 objets : pierres légendaires, fabrique à nuage, enderchest de
  poche, runes et fragments, essences, équipement de donjon basique et renforcé, clés et coffres à
  poser.
- **Le prix** : le prix conseillé est la cote fois **0,6** (**0,35** pour les clés et les coffres à
  poser, qui s'ouvrent plutôt qu'ils ne se revendent), fois la quantité et l'usure (une pièce à
  moitié usée vaut moitié). Trois prix : **vente rapide** (0,85 fois le conseillé, vingt minutes
  en moyenne), **prix conseillé** (trois heures), **prix fort** (1,15 fois, vingt-sept heures : il
  part dans 83 % des cas avant l'échéance). Pas de taxe : le prix affiché est ce que le joueur
  reçoit.
- **Le temps** : la durée de vente suit une loi exponentielle de la moyenne du prix choisi, tirée à
  la mise en vente ; l'heure où l'objet part est gardée (temps réel, partie fermée comprise, réglée
  à la connexion suivante). Au bout de **48 heures**, un invendu attend que le joueur le récupère.
  **Six annonces** à la fois (les invendus pas encore récupérés comptent). Une annonce se retire à
  tout moment.
- **L'argent** arrive au moment de la vente, avec une notification et une ligne dans le chat ; une
  vente réussie reste affichée une journée. Le règlement (`AuctionSales.settle`) passe toutes les
  cinq secondes pour les joueurs en ligne, et à la connexion.
- `/dohy hdv vendre` (opérateur) vend tout de suite toutes les annonces en cours : pour essayer.

### Ce qui est laissé de côté

- L'équipement de donjon **légendaire et antique** et les **sacs** : ce sont les récompenses des
  paliers 65 à 100, et les vendre viderait les donjons de leur but. Ils n'ont pas de cote : les
  acheteurs ne les prennent pas non plus.
- Les enchères (le joueur a choisi les acheteurs à prix fixe), et les tiroirs (`Lucky Drawer`) de
  Paladium.

### Vérifié en jeu (captures, 2026-11-11)

L'écran (rayons, cartes, pastilles de remise, bulle, défilement) ; les annonces identiques à
deux calculs de suite ; un achat (argent débité, objet dans l'inventaire, stock à zéro, carte
« vendu ») ; les refus (plus de stock, pas assez d'argent, annonce inexistante) ; les quatre
renouvellements à 250 k, 500 k, 1 M et 2 M, puis le refus du cinquième ; la carte du menu et
la commande `/market`. Non vérifié : le changement d'annonces au passage d'un jour réel.

## 35. Tiroirs : Utility Drawers, et la clé en paladium (2026-11-18)

Paladium jouait avec **Storage Drawers** installé à côté de PalaMod ; Berskar n'a pas de tiroirs
à lui. Deux portages en 26.2 ont été essayés avec Berskar et NTM : **Vault Drawers** d'abord,
écarté, puis **Utility Drawers** (CurseForge, projet 1582712), retenu par le joueur. C'est un mod
à part, à mettre sur le serveur **et** sur le client ; Berskar n'en dépend pas.

### Ce que Berskar apporte à Utility Drawers

- **Ses noms en français** : `assets/utilitydrawers/lang/fr_fr.json`, les 97 clés du mod, qui
  n'a que l'anglais. Sans accents, comme le reste du kit ; les bois suivent les noms de Minecraft
  (chêne, sapin, bouleau, acajou, acacia, chêne noir, palétuvier, cerisier, chêne pâle, bambou,
  carmin, biscornu). Ce que le mod écrit en dur reste en anglais (le titre « Drawer » de
  l'interface, « (Empty) » dans l'info-bulle, « Search... » à l'écran de stockage).
- **La fiche « Les tiroirs »** (Technique) : son guide en jeu demande le mod GuideMe, absent. Elle
  se découvre en ramassant un tiroir, une base, un compacteur ou une interface.
  `check_encyclopedia.py` connaît désormais les objets d'un mod optionnel (`OPTIONAL_ITEMS`,
  relevés dans son jar).

### Utility Drawers 1.2.6, essayé avec Berskar et NTM (captures, 2026-11-18)

Fichier `utilitydrawers-1.2.6-26.2.jar` (2 540 354 octets, SHA-256 `56859f71…374117`), sur le
serveur dédié puis le client, avec NTM NEXT et NTM Space. Tout ce qui suit a marché :

- le démarrage, sans erreur ; ses 87 recettes ; la pose de tous les blocs ;
- le dépôt au clic droit, le retrait au clic gauche (un objet, une pile accroupi), **l'objet et le
  nombre dessinés sur la façade** ; le compacteur (45 lingots de fer → 5 blocs, 45 lingots,
  405 pépites) ; l'interface du tiroir (clic droit main vide) ;
- **un tiroir plein cassé garde son contenu** : reposé, il avait encore ses 127 pierres ;
- l'amélioration en fer posée au clic droit (capacité × 4) ; l'eau et la lave dans un tiroir à
  fluide ;
- le réseau : la télécommande liée à l'interface (accroupi), cinq tiroirs reliés, puis un
  entonnoir sur l'interface qui a rangé 8 pierres et 4 bûches dans les bons tiroirs ; l'écran de
  stockage posé contre l'interface, qui montre tout le réseau ;
- les noms en français dans le jeu, et la fiche.

Deux remarques. Deux clics droits en moins de 300 ms rangent tout ce que le joueur porte du même
objet ; le délai est compté **par joueur**, pas par tiroir (`LAST_CLICK_TIME`). Et le fluide
paraît sombre sur la façade dans les captures, en rendu logiciel ; l'écran de stockage le montre
bien.

### La clé en paladium — `ItemPaladiumKey`, pour Vault Drawers seulement

- Clic droit sur un tiroir fermé par une clé personnelle : il s'ouvre à tous, et la clé part (le
  créatif la garde). Sur un tiroir ouvert, ou sur un autre bloc, un message et rien d'autre. Les
  messages sont ceux de PalaMod, sans accents ni la faute de « vérrouillé ». Une par pile.
- **Recette du relevé** : quatre lingots de paladium autour de la clé personnelle de Storage
  Drawers, devenue `vault_drawers:personal_key`. Elle ne se charge qu'avec Vault Drawers
  (`neoforge:mod_loaded`) : sans lui, l'ingrédient n'existe pas et 26.2 jetterait la recette.
- **Chasseur 85** : le déblocage était déjà dans `jobs.json`.
- Le pont passe par la réflexion sur `IProtectable` de Vault Drawers (`VaultDrawersCompat`). Le
  clic est pris avant le tiroir (`RightClickBlock`) : fermé, celui-ci ignore tout joueur qui n'en
  est pas le propriétaire, la clé comprise. Priorité basse, pour que le verrou de métier passe
  d'abord.
- **Avec Utility Drawers, elle ne sert à rien** : ce mod n'a pas de propriétaire, son verrou ne
  fixe que l'objet d'une case et la télécommande le lève pour n'importe qui. Sans Vault Drawers,
  la clé reste dans l'onglet créatif mais ne se fabrique pas. Même avec lui, sur une partie jouée
  seul, elle ne sert que si quelqu'un d'autre a fermé un tiroir.
- **L'autre recette au paladium qui touche aux tiroirs n'est pas portée** : elle fait
  l'amélioration de répétition de la baguette des bâtisseurs de faction (paladium compressé autour
  de l'amélioration émeraude), et cette baguette n'existe pas dans Berskar.

### Vault Drawers 0.0.0-beta.9, essayé d'abord et écarté (captures, 2026-11-18)

Fichier `vault_drawers-26.2-neoforge-26.2.0.57-0.0.0-beta.9.jar` (1 067 619 octets, SHA-256
`9e6a1297…5b3c8`), sur le serveur dédié puis le client, avec NTM NEXT et NTM Space.

- **Ce qui marche** : le démarrage, sans erreur ; ses 125 recettes ; la pose de tous les blocs ;
  le dépôt au clic droit, le retrait au clic gauche ; les améliorations posées au clic droit
  (émeraude : 2 048 → 131 072) ; la clé personnelle, qui refuse le dépôt à un autre joueur. La clé
  en paladium : refusée sans le niveau, puis le tiroir ouvert, la clé consommée et les trois
  messages.
- **Ce qui ne marche pas** :
  - **rien n'est dessiné sur la façade**, ni l'objet ni le nombre : on ne voit pas ce que
    contient un tiroir ;
  - **casser un tiroir plein le détruit** : il lâche un seul objet de son contenu et ses
    améliorations, ni le tiroir ni le reste (192 pierres → 1) ;
  - **l'interface** (Maj + clic droit main vide) n'existe pas côté client. Le serveur la croit
    ouverte, et l'inventaire ne se met plus à jour à l'écran tant qu'on reste à moins de 8 blocs
    du tiroir (rien n'est perdu) ;
  - d'après son journal et son code : le contrôleur ne relie pas les tiroirs, et une fermeture à
    clé n'est pas sauvegardée (elle disparaît au redémarrage).
- NTM porte des mixins pour Storage Drawers (`com.jaquadro…`) ; ils ne trouvent pas Vault
  Drawers, dont le paquet diffère : de simples avertissements au démarrage.

---

## 36. Trônes de métier et potions d'XP (2026-11-19)

Demandés le 2026-11-19 (« ajoute les trônes et le Lucky pass », puis « ajoute aussi les potions
de double et triple xp métiers »). Les trônes avaient été laissés de côté à la demande du joueur
(§ 11 et suivants) ; la potion de Double XP était notée absente (bonbons, table des boss des
donjons). Le Lucky Pass suit, avec les lucky blocks qu'il distribue.

### Les trônes — `BlockThroneBase`, `TileEntity*Throne`, `ModelJobThrone` (PalaJobs)

**Ce que dit le code**

- Quatre blocs, un par métier : `miner_throne`, `farmer_throne`, `hunter_throne`,
  `alchemist_throne` (« Trône Mineur », « Trône Farmer »…). Matériau roche, dureté 1, onglet des
  métiers. Verrou d'usage au **niveau 95** (`JobsBridge.canUse` au clic droit).
- **Recette** (`CraftRegistry`) : `EOE / OFO / EOE`, E le fragment d'endium, F le bloc de findium,
  O l'objet du métier — l'obsidienne anti-aggro (Mineur), le seedplanter en paladium vert
  (Fermier), la corne en paladium (Chasseur), le bloc de portail en paladium (Alchimiste).
- **Pose** : quatre orientations, face à celui qui pose. Le premier qui pose un trône y grave son
  nom (`owner` du NBT de l'objet, recopié dans la tile entity) ; le rendu l'affiche en rouge à
  3,2 blocs, tourné vers la caméra.
- **Clic droit** : on s'assoit (`EntitySittableBlock`, 0,6 bloc). **Casse** : le trône tombe avec
  son nom (`onBlockHarvested`, `getDrops` vide).
- **Modèle** : un export Blockbench pour `ModelBase`, 17 boîtes sur une texture de 128 × 128
  (`textures/blocks/thrones/<métier>.png`), dont un dôme tourné de 45° et deux accoudoirs inclinés
  de 22,5°. Il fait 2,9 blocs de haut et 1,8 de large. Boîte du bloc : de 0,5 à 2 blocs.
- `BlockThroneBase` implémente `ILuckyStatsOwnedReward` : le trône était aussi une récompense
  des Lucky Stats.

**Ce que fait DoHy**

- Les quatre blocs sous les mêmes identifiants, la recette, le nom gravé (composant
  `palamod:throne_owner`, gardé par le butin), le nom en rouge à moins de 24 blocs.
- **Le modèle est dessiné par la block entity**, comme le TESR d'origine : la géométrie est
  recopiée boîte par boîte (`tools/throne_assets.py` → `ThroneGeometry`), avec la même texture.
  Un modèle de bloc ne sort pas de -16..32 : le trône n'y tiendrait pas. L'objet, lui, est un
  modèle en 3D descendu d'un bloc et recentré par ses transformations d'affichage.
- **S'asseoir** : un siège invisible (`palamod:throne_seat`) posé sur le coussin, à 9 pixels ; le
  regard reste à 105° du trône, comme dans une barque ; on se relève devant lui. Le siège se
  sauvegarde (26.2 refuse de faire monter un joueur sur une entité qui ne se sauvegarde pas) et
  disparaît dès qu'il n'a plus de passager.
- **Le verrou** : celui de la table des métiers, déjà relevé (`jobs.json`) — il s'applique à la
  **pose** et au clic droit, comme pour tout objet verrouillé de DoHy (le craft n'est jamais
  verrouillé, § 2).
- **Écart** : la boîte va du sol à 1,5 bloc (une clôture) au lieu de 0,5 à 2 : en 26.2, une boîte
  plus haute qu'un bloc ne se vise pas au-delà du premier.

### Les potions d'XP — `ItemBoostDoubleXp` (PalaMod)

**Ce que dit le code** : « Potion de Double XP », à l'unité, bue en 32 ticks. Quatre durées par
métadonnée : 30 min, 1 h, 2 h, 4 h. Elle double l'XP de **tous** les métiers
(`JobsManager.setDoubleXpUntil`) ; bue pendant un double XP en cours, sa durée s'ajoute au reste.
Le temps est réel (`System.currentTimeMillis`) : il file hors ligne. Il n'y a pas de Triple XP dans
le jar (seulement une potion x10 de 5 minutes, dans les lucky blocks de décembre).

**Ce que fait DoHy**

- `palamod:double_xp_potion`, la texture de PalaMod ; `palamod:triple_xp_potion`, propre à DoHy :
  la même fiole, le liquide passé à l'or, un « 3 » à la place du « 2 ». La durée est un composant
  (`palamod:boost_minutes`) ; l'onglet a les quatre durées de chaque.
- **Un effet** (`palamod:double_job_xp`, `palamod:triple_job_xp`) au lieu d'une date : il ne
  compte que le temps de jeu, comme les boosters. Ni le lait ni la mort ne l'enlèvent (la date de
  PalaMod ne craignait ni l'un ni l'autre).
- **Le multiplicateur** s'applique dans `JobApi.grant`, à toute l'XP de métier, bonus compris
  (amélioration *Apprentissage*, amulettes) : blocs, cuisson, bouteilles d'XP, bonbons. Le x3
  passe avant le x2 ; les deux s'écoulent ensemble.
- **Où** : la boutique (Double 1 h et 4 h, Triple 1 h et 4 h ; `BALANCE.md` § 6 duovicies) et la
  table des boss des donjons (Double 30 min, poids 3 ; Triple 30 min dès le palier 50, poids 1).

## 37. Lucky blocks et Lucky Pass (2026-11-19)

Demandés le 2026-11-19 avec les trônes (« ajoute les trônes et le Lucky pass »). Le joueur a
choisi d'**ajouter les lucky blocks** que le pass distribue, et un pass **toujours actif** : un
lancer gratuit par jour, sans achat. Premier lot : trois lucky blocks, l'écran d'ouverture et
75 des 439 événements ; les autres suivront par lots, puis les lucky blocks de saison.

### Les lucky blocks — `BlockLuckyBlock`, `TileEntityLuckyBlock`, `UILuckyBlock`, `PLuckyBlock`

**Ce que dit le code**

- Un bloc par sorte : dureté 1, résistance 1 000, se casse à la main, tombe lui-même avec son NBT,
  des particules de poussière rouge. Le jar en compte quatorze : paladium (111 événements),
  endium (56), findium (130), le noir, et dix de saison.
- **Ouverture** : un clic droit ouvre l'écran, une machine à sous à trois rouleaux. « OUVRIR »
  envoie `PacketGetLuckyEvent` ; le serveur tire l'événement et le garde dans la tile entity ; le
  client anime les rouleaux (le premier part à 100 ms, puis un toutes les 600 ms, 5 s chacun,
  arrivée en *Back*). Le bouton passe de OUVRIR à PASSER puis à FERMER ; l'écran se ferme seul
  5 s après l'arrêt. À la fermeture, `PacketUseLuckyBlock` : le bloc disparaît, un feu d'artifice,
  puis `event.perform(player, x, y, z)`. Fermé avant le tirage, il ne se passe rien.
- **Tirage** : le poids d'un événement est `LuckyConst / rareté` (une rareté de 10 sort cent fois
  plus qu'une de 1 000). Les bons événements pèsent **1,25 fois plus** pour qui a le Lucky Pass
  (`PLuckyBlock$2`). Une règle de pitié force parfois la Consolation.

**Ce que fait DoHy**

- `palamod:paladium_lucky_block`, `endium_lucky_block`, `findium_lucky_block`, les textures de
  PalaMod. Résistance d'obsidienne (1 200) : une explosion ne les ouvre pas. Le butin les rend
  avec leur événement (`palamod:lucky_event`) : casser et reposer ne retire pas au sort.
- **L'écran** reprend la machine (l'avant-plan de PalaMod) et ses temps, les icônes des
  événements sur les rouleaux, le compteur « DECOUVERTS x / 75 », les pastilles BONUS ou MALUS et
  NOUVEAU au premier tirage d'un événement. Espace ou Entrée valent le bouton, un clic sur la
  machine passe l'animation. Le serveur garde la main : un bloc ne s'ouvre que pour un joueur à
  la fois, à moins de 10 blocs, et l'événement part quand l'écran se ferme, comme chez PalaMod.
  Une notification dit ce qui est sorti.
- **Le bonus du pass** : tout le monde a le pass, donc le × 1,25 vaut pour tous les tirages.
- **Ce qui manque encore** compte dans le tirage : les bons événements sont repesés pour que la
  part des bons reste celle de PalaMod, bonus compris — **51,5 %** au paladium, **44,4 %** au
  findium ; l'endium n'a que des bons (`LuckyEvents.goodScale`, chiffres de
  `tools/lucky_assets.py --stats`). Au paladium, les 53 événements portés couvrent 59 % du poids
  des bons et des malus : les chances de chacun restent proches de celles de PalaMod. Au findium,
  ils couvrent 23 % des bons et 66 % des malus : les bons sont multipliés par 3,6, et
  « Où donner de la tête » (rareté 4) sort dans **41 %** des cas, « Doggy Army » dans 12 %. Ces
  écarts fondent à chaque lot porté.

**Les 75 événements portés** (rareté entre parenthèses) :

- **Bons, paladium et endium (27)** : Body Guard (10), Ouaiche t'es relou (10), Pala-Pillone (10),
  Arc en armure (20), Puits (30), Ruée vers les minerais (30), Mineral Shower (50), Quelle
  dinguerie ! (50), SuperMan (50), Walk in Music (50), Consolation (100), Spawner (100), Caballo de
  la muerte (200), Diamond Beacon (200), Amethyst Beacon (400), Endium Beacon (400), Fast Learner
  (500), Titane Beacon (800), Explosif (900), Trop chanceux pour ce monde (900), Legendary Stone
  (1 000), Paladium Beacon (1 200), Mega Fast Learner (2 400), Et paf + de sous (5 000),
  Inéluctable (5 000), Rodshild (5 000), Mega-Inéluctable (12 500).
- **Malus, paladium (26)** : BOOM (20), Enfermé (20), Geyser (20), La-Haut (20), StarFish (20),
  Boom (30), C'est un traquenard ! (30), Piggy Rodeo (30), Pssssssss (30), 0 + 0 = La tête à Toto
  (40), Allumer le feu (40), Instant break up (40), Sur la Lune (40), Zombie Hero (40), Bunny Life
  (50), Ne pas casser (50), Tout feu tout flamme (50), MEGABOOM (70), ArachnoTrap (80), ObsiTrap
  (80), Badaboum (100), High attitude (100), One Shot (100), Et paf plus de sous (5 000), Le
  seum… (10 000), Le malus le plus rare… (30 000).
- **Bons, findium (13)** : Doggy Army (50), 20 000 lieues sous terre (100), Danse maintenant (100),
  Prêt à tout (100), Et ! Les phans ! (200), Pala est là (200), Bien mais pas trop (300),
  DoubleXP (300), L'ami des loups (300), Try Again (400), Over 9000 (1 000), Pioche de Légende
  (1 000), Endium Block (9 000).
- **Malus, findium (9)** : Où donner de la tête (4), Sol glacé (50), A-Bee-me (100), C'est pas
  passé loin (100), Long time no see (100), Mélange de grand-mère (100), Prendre du recul (100),
  Si j'étais toi je bougerais (100), Sonic (100).

Les effets de PalaMod qui n'ont pas d'équivalent sont des effets à part, sans action propre :
SuperMan (vol forcé), Walk in Music (notes de musique), Tout feu tout flamme (flammes), Bunny
Life (sauts forcés), Où donner de la tête (la tête disparaît du modèle, sans mixin), Sol glacé
(on glisse), Sonic (plus de saut), Prendre du recul (troisième personne forcée), Prêt à tout
(1 000 $ par enclume reçue). Leurs icônes viennent des événements.

**Écarts**, voulus ou forcés :

- **Endium Beacon** bâtit sa pyramide en **lapis**, comme le code de PalaMod (le nom promettait
  de l'endium).
- **Enfermé** remplit la cage d'eau : DoHy n'a pas l'acide. **ObsiTrap** laisse libres les deux
  blocs du joueur, qui serait sinon étouffé dans l'obsidienne.
- **20 000 lieues sous terre** creuse une poche de deux blocs à six blocs du fond du monde et y
  téléporte ; au-dessus du vide (End), il ne fait rien.
- **Puits** : la foudre n'est qu'une image. **Danse maintenant** : dix éclairs pour l'image, les
  dégâts (5) et le feu posés à la main, sans mettre le feu au sol.
- **Caballo de la muerte** est un cheval squelette déjà dressé, au nom du joueur.
- **Fast Learner** donne un niveau dans un métier, comme le code ; son message en promettait cinq.
- **MEGABOOM** est la TNT en endium de DoHy ; **Piggy Rodeo** fait exploser le cochon
  (puissance 15) au bout de dix secondes.
- **Instant break up** détruit deux outils, celui en main et le premier de l'inventaire, comme
  le code.
- **Les coffres piégés** de « C'est un traquenard ! » sont gardés en mémoire : un redémarrage les
  rend inoffensifs. **Les gardes du corps** sont reconnus à leurs étiquettes d'entité.
- **Les explosions** n'ont pas de joueur pour source : elles ne cassent rien dans un claim, comme
  les autres explosions de DoHy. Un lucky block ne modifie que les blocs qu'on a le droit de
  modifier (hors claim ennemi, sans block entity).
- La règle de pitié de la Consolation n'est pas reprise.

### Le Lucky Pass — `UILuckyPass`, `PlayerLuckyPassProperties`, `PacketWinLuckyPass`

**Ce que dit le code** : un lancer par jour pour qui a le pass (`hasLuckyPass`). Trois
cases, chacune vide ou un lucky block : 60 % des lancers donnent **un** bloc (la case du
milieu), 30 % **deux**, 10 % **trois** — un bloc et demi en moyenne. La sorte se tire au
poids : paladium 4 000, findium 3 500, endium 500, noir 300, et 170 pour chacun des dix de
saison.

**Ce que fait DoHy**

- **Toujours actif**, gratuit : un lancer par jour, le jour des contrats (`ContractApi.today()`),
  donc remis à zéro à la même heure qu'eux. Le lancer se garde avec le joueur (`palamod:lucky`,
  conservé à la mort) ; un serveur qui redémarre ne le rend pas.
- **L'écran** : trois rouleaux de cases, un lucky block ou une croix, « UN LANCER GRATUIT PAR
  JOUR », le nombre de lancers ; le résultat, puis « Prochain lancer dans 7 h 12 ». Les blocs vont
  dans l'inventaire, au sol s'il est plein.
- **La sorte** se tire entre les trois portés : paladium 50 %, findium 43,75 %, endium 6,25 %. Le
  noir et ceux de saison viendront avec leurs événements.
- **Où** : le bouton LUCKY de l'inventaire (à côté de PASS, sous le craft — la place qu'il avait
  sur la maquette de Paladium), la carte « Lucky Pass » du menu principal (PRET tant que le
  lancer du jour n'est pas joué), `/luckypass` ou `/lucky`.
- Le Lucky Pass est, pour l'instant, la **seule source** de lucky blocks (avec Try Again et
  Quelle dinguerie !, qui en donnent d'autres).

### Code

- `palamod/lucky/` : `PalaLucky` (blocs, objets, block entity, composants, effets, données),
  `LuckyType`, `LuckyEvent`, `LuckyEvents` (catalogue, tirage, équilibre), `GoodEvents`,
  `BadEvents`, `FindiumEvents`, `LuckyWorld` (poser, déposer, foudre, TNT, droits), `LuckyTasks`
  (événements étalés dans le temps), `LuckyWorldEvents` (gardes, lapin, coffres piégés, faux
  creepers, faux paladium, vol), `LuckySession`, `LuckyPass`, les vues `LuckyBlockView` et
  `LuckyPassView`, `FakePaladiumBlock`.
- Côté client : `client/lucky/LuckyBlockScreen`, `LuckyPassScreen`, `LuckyClientEvents` (les
  effets cosmétiques, la tête cachée).
- `tools/lucky_assets.py` génère blockstates, modèles, butin et noms, `--jar` copie les textures,
  `--check` (en CI) vérifie les événements contre `tools/data/lucky_events.json` — le relevé des
  439 événements du jar — et `--stats` donne l'équilibre.

## 38. Lucky blocks, deuxième lot (2026-11-19)

La suite du § 37, demandée le même jour (« Continue ») : **50 événements de plus**, 125 sur 439. Ce lot
prend tous ceux qui posent un effet d'écran ou de rendu, et les farces qui agissent sur le monde sans
objet, bloc ou créature propres à PalaMod. Les autres attendent leur contenu (lot suivant).

### Les effets — `CustomPotion` de `PLuckyBlock`, `EventsManager`

**Ce que dit le code**, effet par effet, et **ce que fait DoHy** :

| Événement | Effet de PalaMod | Ici |
|---|---|---|
| T'as un problème nan ? (20) | `RAYMAN`, sans fin : le corps (`bipedBody.isHidden`) disparaît, bras et jambes flottent | la partie « body » cachée au rendu |
| Fantôme (100) | `GHOST`, sans fin : corps, bras et jambes cachés, il reste la tête | idem |
| Le monde à l'envers (50) | `ROTATE`, sans fin : le joueur se voit retourné (rotation de 180°), à ses propres yeux seulement | le drapeau « tête en bas » de Minecraft (celui de Dinnerbone) |
| Steve Jobs, Tous les mêmes (200) | `STEVE`, sans fin : le skin par défaut | idem |
| Sans lunette (100) | `FLOU`, 1 min : un voile gris presque opaque (`floufilter`, 87 %) | un aplat de la même couleur |
| Vision nocturne (500) | `GREEN`, sans fin : un voile vert (47 %), aucune vision de nuit malgré le nom | idem |
| I love cats (200) | `CAT`, sans fin : une photo de chat au milieu de l'écran, large de la moitié de l'écran | un chat dessiné pour DoHy, aux mêmes proportions |
| #TeamTree (100) | `TREE`, 1 min : à chaque tic, une pousse de chêne en haut de la colonne du joueur | seulement là où une pousse tient (PalaMod la posait même sur la pierre) |
| Boom les mobs (100) | `BOOM_MOBS`, sans fin : un coup direct envoie le monstre (ni joueur ni boss) de 30 à 59 blocs plus haut | idem ; la chute fait le reste |
| Explosif (100) | `EXPLOSE`, 1 min : la touche d'accroupissement fait exploser le joueur (puissance 5, une fois par seconde au plus) ; il est la source et n'est pas blessé | dès qu'il commence à s'accroupir ; rien ne casse là où il n'a pas le droit |
| Inversion (20) | `PacketUseInversion`, 3 min : avant et arrière, gauche et droite, saut et accroupi échangés (`CustomMovementInput.confused`) | un effet, donc une icône, au lieu d'un drapeau caché |
| Divise XP (300) | 15 min : l'XP de métier divisée par deux (`DiviseXP.isXpDivised`) | l'effet « Divise XP », appliqué après les bonus |
| On voit pas le fond du bol (80) | une soupe ; le prochain repas coiffe d'une citrouille, et sa vue reste jusqu'à la connexion suivante | une citrouille sculptée ; le casque revient dans l'inventaire au lieu d'être écrasé ; la vue reste, effet « Le fond du bol » |

Les durées de PalaMod de « 999 999 999 tics » sont des effets sans fin. Un seau de lait les retire,
comme chez PalaMod. Les icônes viennent des images des événements.

### Les farces — paladium et endium

- **Spam TP** (50) : quatre fois, toutes les 25 secondes, de la fumée et le joueur repart 201 blocs
  au-dessus du sol. Ici, jamais plus haut que le haut du monde.
- **Ghast dans l'âme** (60) : quatre boules de feu partent du bloc vers les points cardinaux et
  explosent en puissance 10 là où elles touchent. Sans lanceur, rien ne casse dans un claim.
- **Nether activé !** (100) : 10 x 11 colonnes passent à la netherrack ; trois fois sur dix elles
  prennent feu, trois fois sur cent un zombie-cochon y apparaît.
- **Hasta la vista** (200) : six messages, une seconde d'écart, puis `PacketCrash` faisait **planter
  le client**. Ici le joueur est renvoyé du monde avec ces mots : même effet, sans risque pour la
  sauvegarde.
- **Paladin** (500) : « Bravo ! Vous avez gagné un grade paladin »… et la vérité au survol.
- **TeamKiller** (800) : dix lapins tueurs.
- **T ki ?** (1000) : trente secondes chez un autre joueur, invincible, puis retour. Seul, un message.
- **La dirt, c'est bien** (1000) : les 40 cases (sac et armure) deviennent de la terre pendant dix
  secondes. Ici, ce qui a été ramassé entre-temps est rendu aussi, et l'inventaire revient même si le
  joueur meurt ou se déconnecte dans l'intervalle.
- **Silence, ça tourne** (2000) : cinq secondes, puis le joueur « dit » sa position dans le chat. Ici
  une ligne du serveur à son nom.
- **Armaguedon** (2000) : dix vagues de dix grosses dynamites (mèche de 30), dix blocs au-dessus du
  sol, dans un carré de 20 blocs.
- **Tête de wither** (100, bon) : la gemme du Funny Wither, nommée « Tête de wither gentil ».
- **Princesse** (200, bon) : un cube d'argile de 9 blocs pousse, case après case, dans des cœurs ;
  vidé sur 7 cinq secondes plus tard. Un bloc qui tomberait sur le joueur le fait monter.
- **Base déco** (1000, bon) : le joueur part au hasard à ±15 000 blocs, 13 blocs au-dessus du sol,
  dans une coque d'obsidienne de 18 blocs vidée sur 15. Ici, toujours dans la bordure du monde.

### Les farces — findium

- **Villa-nul** (100) : `EntityGodVillager` de type 3, qui ne vend rien. Ici un villageois benêt
  nommé « Villa-nul ».
- **Reste à ta place** (100), **Reste à la maison** (700) : un home pris au hasard. DoHy n'a pas de
  homes de joueur : ce sont ceux de la faction. Sans home, le premier le dit, le second ramène au
  spawn.
- **Bataille de regard** (100) : trente secondes les yeux dans ceux d'un enderman sans IA, soigné à
  chaque fois, qui compte les secondes ; le joueur est remis en place toutes les deux tics.
- **Terrain miné** (150) : le bloc laisse place à de la terre, du sable, de la pierre… qui explose
  en puissance 15 si on le casse sans Toucher de soie. Gardé en mémoire, comme chez PalaMod : un
  redémarrage le désamorce.
- **Père Castor** (150) : un livre de 34 pages, « Une petite histoire… » (Thésée et le Minotaure).
  Ici la même histoire, racontée avec d'autres mots, en dix pages.
- **Pétard mouillé** (150) : trente détonations pour rien, une par seconde.
- **End** (200) : vingt endermen. **Professeur Chaos** (200) : vingt échanges de cases au hasard.
- **Ne bois pas ça** (200, en bon et en malus) : une bouteille sans étiquette, six effets de quatre
  minutes — force, célérité, résistance au feu, vitesse, vie supplémentaire, absorption, ou cécité,
  nausée, fatigue, faim, lenteur, poison. Ici une vraie potion couleur d'eau dont l'infobulle est
  cachée : les deux se ressemblent.
- **Have Fun** (200) : une boîte d'obsidienne durcie autour du joueur, avec les bizarreries du code
  (sol et plafond de 4 x 4, murs qui descendent trois blocs sous les pieds).
- **L'ennemi des loups** (300) : vingt loups enragés. **Rantamplan** (400) : cinq loups apprivoisés.
- **Coordonnées de Fuze** (300) : un livre de « bigh4ck3r »… et un lien qui n'a rien de coordonnées.
- **Ça fait haut** (300) : une cage de verre de 249 à 254 (moins si le monde est plus bas), le joueur
  dedans.
- **Tour à mob** (300) : de 34 à 46 zombies à l'épée et squelettes à l'arc, en fer, plus rapides
  (0,43) et plus forts (8).
- **Injouable** (500) : Absorption XXI, trente secondes (84 cœurs).
- **Je crois que c'est interdit** (800) : un wagonnet vide.
- **La tête sur les épaules** (1000) : la tête d'un autre joueur en ligne, prévenu ; seul, la sienne.
- **Invocateur** (1000) : neuf spawners dans les premières cases d'air autour du joueur (squelette,
  zombie, araignée, creeper, blaze ou enderman). PalaMod ne les posait qu'aux coordonnées positives,
  un oubli ; ici partout.
- **Inventaire plein** (5000) : vingt piles de terre dans le sac, le reste perdu.
- **Duplicata** (10 000) : un exemplaire de plus de l'objet en main ; la main vide, un exemplaire de
  chaque objet du sac. Ni les tiroirs, ni les lucky blocks, ni les coffres-forts.

### Pas dans ce lot

- **Les événements à contenu** (le gros du reste) : lampes, plantes, instruments, TNT mignonnes,
  jetpack, grappin, lasso, Wi-Fi, armure de cochon, boutique de Fuze… chacun demande son objet, son
  bloc ou sa créature. Lots suivants.
- **Des effets sans équivalent fidèle simple** : Aimanté (les entités tremblent vers le joueur, côté
  client seulement), 404 Not Found (tous les blocs prennent la texture manquante), 56K (les paquets du
  client retardés d'une demi-seconde), T'as une mauvaise mine (le joueur teinté de vert), Mission
  commando (la caméra un bloc plus bas), Cow Player (une vache à la place du joueur), T'as mangé
  quoi ? (des pas qui pètent, un son de PalaMod). Ils pourront venir plus tard.
- **Adieu faction** (2000) : renvoie un membre de la faction, ou fait partir le joueur seul. En solo,
  cela dissoudrait sa faction (claims, homes, banque) : pas porté.
- **Tp All** : des demandes de téléportation à tous les joueurs ; rien en solo.

### Équilibre

`tools/lucky_assets.py --stats` après ce lot :

| | Portés | Masse des bons | Masse des malus | Bons repesés |
|---|---|---|---|---|
| Paladium | 72 / 111 | 60,9 % | 78,9 % | × 1,62 |
| Endium | 30 / 56 | 60,9 % | — | × 1,25 |
| Findium | 53 / 130 | 36,6 % | 86,4 % | × 2,95 |

La part des bons reste celle de PalaMod (51,5 % au paladium, 44,4 % au findium). Au findium, « Où
donner de la tête » tombe de 41 % à 31 % (27 % chez PalaMod). Au paladium, les malus portés
couvrent maintenant plus que les bons : chaque bon sort un peu plus souvent que chez PalaMod (× 1,62
au lieu de × 1,25), sans que la part des bons change.

## 39. Lucky blocks, troisième lot (2026-11-19)

La suite du § 38, demandée le même jour (« Enchaîne ») : **22 événements de plus**, 147 sur 439. Ce
lot prend ceux dont le contenu existe déjà dans DoHy — coffres en paladium et en endium, golems et
boîte à golem, pierre de capture, totem de fertilité, obsidienne durcie, Lucky Pass — ou dans le
jeu. Code : `ContentEvents` (paladium et endium), `FindiumContent`, `LuckyMaze`,
`LuckyContentEvents` (le coffre maudit, l'énigme, le voyant) et l'écran `OneMillionScreen`.

### Paladium et endium

- **Expala-osion** (150) : une petite explosion qui ne casse rien (puissance 1), puis 64 fois deux
  lingots de paladium qui tombent de six blocs, un lâcher tous les deux tics : 128 lingots.
- **Carte au trésor** (200, bon) : une feuille de papier nommée « Carte au trésor : x, y, z », vers
  un point au hasard à 20 000 blocs au plus (dans la bordure du monde). Un coffre en paladium y
  attend, un lingot par case : 108. Le chunk est généré d'abord, pour trouver la surface.
- **Carte au trésor** (200, malus) : la même carte ; au bout, une pancarte « [LuckyBlock] / Cheh
  y'a rien ».
- **Jimmy Hendryx** et **Nez en trompette** (50) : la guitare et le cor de Custom NPCs, deux
  accessoires sans effet. Ici un bloc musical « Guitare de Jimmy » (posé sur de la laine, il joue
  de la guitare) et une corne de chèvre « Trompette ».
- **Réflexe** (200, malus) : un deltaplane de moins s'il y en a un dans l'inventaire, un seau
  d'eau, puis cinq secondes de montée jusqu'à 500 blocs plus haut, dans des étincelles : il faut
  poser l'eau avant le sol.
- **Labyrinthe** (200, malus) : 16 × 16 cases d'obsidienne à la hauteur 100 — sol en 99, couloirs
  de deux blocs, plafond en 102 —, creusées par une marche au hasard (10 000 pas au plus), sans
  sortie. Fatigue de minage X sans fin, et les commandes refusées : « Puisse le sort vous être
  favorable, mais ici dans ce labyrinthe, la triche n'est point autorisée ! ». On en sort par le
  lait (puis la pioche), la reconnexion ou la mort. **Écart** : le joueur arrive au milieu d'une
  case ; PalaMod le posait dans un mur, sous le plafond.
- **Coffre maudit** (1000, malus) — « Triforce » chez PalaMod : un coffre en endium vide, marqué.
  L'ouvrir tue (dégâts magiques sans limite), cinquante éclairs tombent dessus, et le coffre
  disparaît vingt secondes plus tard. Le casser au lieu de l'ouvrir donne un coffre en endium
  ordinaire, comme chez PalaMod. **Écarts** : le nom et l'image changent (l'image reprenait
  l'emblème d'une autre licence) ; les éclairs sont pour l'œil — chez PalaMod, ils brûlaient les
  objets tombés du joueur mort.

### Findium

- **Je t'aime tôt** (100) : un totem de fertilité, plein de 64 lingots de paladium.
- **Bou-bam** (150) : une colonne de bambou jusqu'à la hauteur 255, tant qu'il y a de l'air. Le
  bambou de PalaMod (module chasseur) devient celui du jeu, posé sans prévenir les voisins pour
  tenir même sur la pierre.
- **Boule de Crystal** (200) : un lancer de Lucky Pass offert, et un voyant — un villageois prêtre,
  immobile — qui ouvre le Lucky Pass d'un clic puis s'en va. Chez PalaMod, un « God Villager ».
- **Paladium Bizarre** (200) : un lingot de paladium marqué `STRANGE` ; ici, une ligne « Il a l'air
  bizarre... » le montre.
- **Coucou** (300) : un golem gardien niveau 40 au joueur (Golemancie, Utilitaire, Régime novateur,
  Gourmand, Stockeur), 64 blocs de paladium dans son inventaire, et ce message : « Tue moi si tu
  l'oses ».
- **N'en parle à personne** (300) : LuckyMan « partage un dossier qui a fuité »… et son second
  message est vide. **Écart** : l'image, logo d'une plateforme vidéo chez PalaMod, est un dossier
  dessiné ici.
- **1 000 000 joueurs** (300, compté bon par PalaMod) : l'écran d'un faux gagnant (« Vous avez gagné
  un full endium !!!! ») et 5 000 $ de moins, jamais sous zéro. À la fermeture : « Oups... On dirait
  que vous vous êtes fait arnaquer ». L'écran est dessiné pour DoHy : l'image de PalaMod est faite
  de photos.
- **Tête d'enclume** (500, malus) : onze vagues, toutes les 15 tics, d'enclumes qui tombent de dix
  blocs au-dessus du joueur, sur 10 × 10 blocs, trois cases sur dix. **Écart** : seulement dans
  l'air ; PalaMod remplaçait les blocs.
- **Creepesque** (500) : une pierre de capture qui garde un creeper.
- **Déclassement** (600, malus) : chaque pièce d'armure tombe au sol, et une pièce en cuir, aux
  mêmes enchantements et au même nom, prend sa place (sans l'usure de l'originale).
- **Père Fouras** (800) : une énigme en quatre lignes, trois essais dans le chat — la réponse ne part
  pas aux autres. « La pensée » (avec ou sans accent, « penser » aussi) rapporte 10 lingots.
- **Roulette** (800) : un lancer de Lucky Pass offert ; l'écran s'ouvre.
- **Base abandonnée** (1000) : un sol de 10 × 10 en obsidienne durcie sous le joueur, et quatre
  murs de onze blocs de haut, de six sous ses pieds à quatre au-dessus de sa tête, sans toit.
- **Jackpot** (1300) : une boîte à golem toute montée (25 cadres, l'eau angélique au centre, la
  boîte sur une face) et trois mini golems niveau 12 dedans. **Écart** : PalaMod leur donnait
  Farmeur et Palado-créateur, sans Golem en boîte, sans laquelle une boîte ne les fait pas
  travailler ; ici les deux branches vont jusqu'au bout et les golems produisent.

### Le Lucky Pass et ses lancers offerts

`setHasLuckyEventBypassNow` ouvrait chez PalaMod un écran à part pour un lancer gratuit. Ici, les
lancers offerts s'ajoutent à celui du jour (`LuckyData.pass_bonus`) : le lancer du jour part
d'abord, le lancer offert ne change pas le jour.

### Correction du deuxième lot

**Base déco** lisait la hauteur avant que le chunk d'arrivée soit chargé : Minecraft rendait alors
le fond du monde, et la base se construisait sous terre (sur le monde plat des captures, la
différence ne se voyait pas). Le chunk est maintenant généré d'abord.

### Pas encore portés

- **Les événements à contenu neuf**, le gros du reste : lampes, plantes, instruments, TNT mignonnes,
  objets de PalaMod (grappin, lasso, jetpack, potions...), créatures (ballons, colombe, chauve-souris
  nerveuse, mouton piquant, zoo, monstres d'XP).
- **Corvée générale** (4000) : un boss au hasard. Les boss de DoHy ne vivent qu'avec leur combat de
  faction.
- **Golem classieux**, **Golem crasseux** : des skins de golem, absents de DoHy. **Coffre fort**,
  méga et ultra : le coffre-fort de PalaMod n'est pas dans DoHy.
- **La statue des admins** et **le grade Divinity** : de vraies personnes, un grade de serveur.
  **My name is Fuze III**, **Fuze personnel**, **To doum doum** : des disques de musique.
- **Ton heure a sonné** (700) : il tue le joueur, compté bon par PalaMod.
- Les effets sans portage fidèle simple (§ 38).

### Équilibre

`tools/lucky_assets.py --stats` après ce lot :

| | Portés | Masse des bons | Masse des malus | Bons repesés |
|---|---|---|---|---|
| Paladium | 80 / 111 | 66,6 % | 80,4 % | × 1,51 |
| Endium | 34 / 56 | 66,6 % | — | × 1,25 |
| Findium | 67 / 130 | 49,7 % | 87,1 % | × 2,19 |

La part des bons reste celle de PalaMod (51,5 % au paladium, 44,4 % au findium). Espérance d'un
bloc (`BALANCE.md` § 6 tervicies) : paladium ~5 800 $, endium ~11 300 $, findium ~1 800 $ ; Lucky
Pass ~6 600 $ par jour. Les nouveaux bons, de valeur modeste, diluent les gros lots : une Diamond
Beacon tous les 250 jours de lancers, un Endium Block tous les 5 000.

## 40. Lucky blocks, quatrième lot (2026-11-19)

La suite du § 39 (« Continue ») : **16 événements de plus**, 163 sur 439. Ce lot porte ceux qui
demandaient du contenu neuf : dix-neuf objets des lucky blocks de PalaMod (`luckyblock/items`), le
faux bloc de paladium vert, la vision du casque de spéléologue — et les deux pièges au slimepad, qui
existait déjà (§ 20). Code : `LuckyItems` (les objets, Saut III des AirMin, la brique de lait qui se
remplit), `CalciumItem`, `CouacSwordItem`, `CaveHelmetVision`, `LuckyItemEvents` (les événements) ;
les fichiers viennent de `tools/lucky_assets.py`.

### Paladium

- **J'ai glissé chef** (100, malus) : une nappe de 7 × 7 slimepads à hauteur des pieds, le joueur
  dedans, et « Il ne me semblait pas avoir vu de panneau sol glissant pourtant ? ».
- **Tour de slime** (20, malus) : un puits de dix slimepads cerclé de pierre, un pic en paladium au
  sommet, pointe en bas (la métadonnée 0 de `BlockSpike`), et le joueur posé un bloc plus haut : les
  slimepads le lancent droit dans le pic, qui le blesse à chaque contact (15 points : sans armure,
  deux coups suffisent). Casser un slimepad arrête la poussée.

### Findium

- **Grouik Grouik** (Helmet, Chestplate, Leggings, Boots — 1000 chacun) : une pièce de l'armure de
  cochon dans l'inventaire, et « Grouik Grouik! » en rose.
- **C'est cheat un peu** (1500) : le casque de spéléologue.
- **Homme invisible** (800) : une pièce de l'armure invisible, au hasard.
- **Juste pour flex** (400) : une pièce de la fausse armure en endium — les bottes deux fois sur
  cinq (`nextInt(5)`, et 0 retombe sur elles).
- **Cagoule** (200) : la cagoule noire. **Air Min** (200) : les AirMin. **Dose de Calcium** (100) :
  la brique de lait. **Poulay** (300) : l'épée Couac. **Coup de turbo** (3000) : une pomme de
  vitesse. Les objets « dans l'inventaire » tombent au sol s'il est plein.
- **Faux billets** (100, malus) : cinq billets.
- **Ouaiche t'es très relou** (7000) : le faux bloc de paladium vert, qui saute quinze fois comme le
  rouge (étincelles vertes) et rend un vrai bloc de paladium vert.

### Les objets

| Objet | Chez PalaMod | Ici |
|---|---|---|
| Armure de cochon | `PArmorMaterial.pig`, réparée au titane | les valeurs du paladium (§ 10), le son du cuir |
| Casque de spéléologue | même matériau, `setMaxDamage(300)` | 300 points, un par seconde de port : cinq minutes |
| Armure invisible | même matériau, couches transparentes | pareil : on ne la voit pas sur le joueur |
| Fausse armure en endium | fer vanilla, icônes et couches de l'endium, réparée au lingot de paladium | pareil, sans le bonus de set de l'endium |
| Cagoule noire | casque en fer, réparé au fer | pareil |
| AirMin | bottes en fer, irréparables, Saut III (`PotionEffect(jump, 60, 2)`) | pareil |
| Brique de lait | un seau de lait de dix gorgées ; taper une vache la remplit | pareil |
| Pomme de vitesse | nourriture 100, saturation 0,6, Vitesse VIII 10 s, par 16 | pareil, toujours mangeable |
| Billet | par 5, « Ce sont des billets factices, prend garde à toi. » (Maj) | la phrase toujours visible |
| Épée Couac | épée en paladium, `palamod:chicken` à chaque coup | le cri de la poule du jeu |

Le **casque de spéléologue** (`CaveHelmetListener`) change, pour son seul porteur, les neuf blocs
qui le portent en cave blocks — le verre troué qui montre les grottes (§ 11) — et chaque bloc
redevient lui-même deux secondes après le dernier passage. La case visée est celle de PalaMod
(`floor(posY) - 2`, posY à hauteur d'yeux côté client), jamais un conteneur. **Écarts** : le serveur
envoie au porteur un faux bloc puis le vrai, celui du moment, au lieu de changer le monde du client ;
seuls les blocs à collision pleine sont montrés (un cave block à la place d'une dalle ferait buter le
joueur sur un bloc que le serveur ne voit pas).

### Écarts

- **Noms et images** : « Flash McQueen » devient **Coup de turbo** (l'image était le numéro d'une
  voiture de dessin animé), « Michael Jordans » devient **Air Min** (le nom des bottes chez PalaMod ;
  l'image était le logo d'une marque de baskets), et **J'ai glissé chef** montre un panneau de sol
  glissant au lieu d'une peau de banane reprise d'un jeu de course. Les trois images sont dessinées
  ici, comme l'icône du billet (PalaMod prenait celle d'un autre mod, Custom NPCs). La petite marque
  sombre sur le côté des AirMin portées est effacée.
- Le slimepad posé par les événements regarde au sud, comme la métadonnée 0 de PalaMod.

### Pas dans ce lot

- **Le déguisement en cochon** : l'armure de cochon complète faisait dessiner le joueur en cochon
  (`EventsManager`, côté client). Le rendu des joueurs de 26.2 ne s'y prête pas simplement ; les
  quatre pièces restent une armure.
- Les autres événements à contenu neuf : lampes, plantes, instruments, TNT mignonnes, objets de
  PalaMod (grappin, lasso, jetpack, potions...), créatures (ballons, colombe, chauve-souris nerveuse,
  mouton piquant, zoo, monstres d'XP) ; puis les lucky blocks noir et saisonniers.
- Ce que listait déjà le § 39 (boss, skins de golem, coffres-forts, disques, vraies personnes).

### Équilibre

`tools/lucky_assets.py --stats` après ce lot :

| | Portés | Masse des bons | Masse des malus | Bons repesés |
|---|---|---|---|---|
| Paladium | 82 / 111 | 66,6 % | 86,0 % | × 1,61 |
| Endium | 34 / 56 | 66,6 % | — | × 1,25 |
| Findium | 81 / 130 | 59,6 % | 89,1 % | × 1,87 |

La part des bons reste celle de PalaMod. Les nouveaux objets n'ont pas de prix de revente : ils ne
comptent pas dans l'espérance (`BALANCE.md` § 6 tervicies), qui reste ~5 800 $ au paladium et
~11 300 $ à l'endium, et descend à ~1 500 $ au findium ; Lucky Pass ~6 400 $ par jour. Une Diamond
Beacon tous les 250 jours de lancers, un Endium Block tous les 6 000.

## 41. Lucky blocks, cinquième lot : le noir et les lucky blocks du mois (2026-11-19)

« Enchaîne, reviens quand tu as totalement tout fini » : **266 événements de plus, 429 sur 439**.
Les 68 derniers événements classiques portables (paladium, endium, findium), puis les **onze lucky
blocks saisonniers** de PalaMod — le noir et ceux de mars, Pâques, mai, juin, juillet, août,
septembre, Halloween, novembre et Noël — avec leurs 198 événements. Commits `5a` à `5l` sur
`feat/lucky-blocks-5`.

### Les lucky blocks saisonniers

- **Onze blocs** (`PalaLucky`, `LuckyType` : les types s'ajoutent à la fin, leur numéro est gardé
  dans les données des joueurs), chacun sa couleur de PalaMod (`LuckyType.Util`), éclaircie à l'écran
  quand elle est trop sombre (le violet de novembre, le bordeaux de mai).
- **Ils sortent du Lucky Pass**, toute l'année, avec les poids de PalaMod sur 10 000 : paladium 4 000,
  findium 3 500, endium 500, noir 300, chaque mois 170. Les 2 000 du saisonnier, jusqu'ici répartis
  sur les trois types, vont à leurs blocs. PalaMod ne les vendait qu'en leur saison ; ici aucun
  calendrier.
- **Leurs événements ne sortent que d'eux** (`Catalog.season`), et la part des bons est celle de
  PalaMod, comme pour les autres (`LuckyEvents.goodScale`).
- **Leur modèle 3D** (corrigé le 2026-11-24) : les neuf blocs du mois (mars, Pâques, mai, juin,
  juillet, août, septembre, novembre, Noël) sont dessinés dans PalaMod par une TESR et un modèle
  exporté de Blockbench (nœud et rubans du bloc de Noël, oreilles de Pâques, cadre d'août…), sur une
  texture-patron de 64 ou 128 pixels. Cette texture avait été ramenée à 16 pixels comme celle d'un
  cube : le bloc n'était plus qu'un cube presque transparent. `tools/blockbench_json.py` rejoue
  maintenant chaque modèle (son constructeur au bytecode : pièces, boîtes, enfants, rotations) en
  modèle de bloc de 26.2, rotations sur trois axes comprises (permises depuis 25w46a), et
  `lucky_assets.py` garde leur texture entière (`tools/data/lucky_block_models.json`). L'icône d'un
  modèle qui déborde du bloc est réduite pour tenir dans sa case. Le noir garde son cube.

### La boîte à outils (`SeasonKit`)

Les événements du mois parlent, comptent et construisent ; tout passe par une boîte commune :

- **Dialogues** : un personnage pose une question, chaque réponse est une ligne cliquable du chat
  (`/dohy_lucky_reponse`) ; une nouvelle question remplace l'ancienne.
- **Défis** : une barre de boss compte le temps ; une étape tourne à chaque tic et dit gagné, perdu
  ou en cours ; un seul défi à la fois par joueur.
- **PNJ** : une créature du jeu nommée, immobile et invulnérable, qui part au bout de quelques
  minutes ; un clic droit l'appelle (`EntityPlayerNPC`).
- **Salles à code** (les coffres du mois) : une salle de bedrock de 6 × 6 × 6 autour du joueur,
  éclairée par une lumière invisible, un chiffre du code devant chaque mur, à hauteur des yeux,
  une porte qui s'ouvre quand le code est tapé dans le chat, cinq minutes puis tout disparaît.
- Créatures nommées et réglées (vie, dégâts, butin en plus), chutes adoucies, effets propres
  (`SeasonEffects`) : étoiles, plus d'oxygène, gravité de Jupiter, caméra retournée, écran piraté,
  la vie en rose, danse, « coin coin », cinéma muet, sol qui s'enflamme, cache-œil, scorbut, parler
  pirate, coup de soleil, cuisson, foin plein l'écran, cours d'anglais, Hell Yeah, Spooky Spook,
  inventaire explosif, grand froid, solitaire.

### Mois par mois

- **Mars** (15, l'espace) : pluie d'astéroïdes, rencontre du troisième type, petits hommes verts,
  entraînement d'astronaute, trou noir, télescope et son quiz, casque d'étoiles, pile galactique,
  fusée supersonique, potions de gravité, pierre de survie.
- **Pâques** (20) : chasse aux œufs, poule aux œufs d'or, lapin-garou, poisson d'avril (24 heures),
  œufs en chocolat, cuir arc-en-ciel, cadeaux piégés ou pas, Hackerman, ça fait flipper.
- **Mai** (19) : la vie en rose, champ de fleurs, cage dorée, Ça va faire BOOM (le méga creeper),
  repas de roi, test de force, va te laver (un bain de lave à prendre), fête du travail, le chevalier
  sombre, le seau d'eau du pro.
- **Juin** (18, la musique) : super danseur, battle de danse, cinéma muet, disque d'or, jukebox,
  « coin coin », réveille-toi, enflamme le dance-floor, maladie du pouet-pouet.
- **Juillet** (20, les pirates) : bouteille à la mer, carte au trésor enterré, roi des pirates,
  bateau fantôme, parler pirate, scorbut et oranges, ordinateur à décrypter (le code du coffre),
  marin d'eau douce, potion d'invulnérabilité.
- **Août** (19, l'été) : vacances sur une île, château de sable et son majordome, volley, glaces,
  crème solaire, coup de soleil, ventilateur, tapis de course, sable mouvant, soldes d'été.
- **Septembre** (21, le travail) : astronaute — une capsule, le décollage, une minute sur la Lune —,
  test d'anglais (une faute et le jeu passe en anglais cinq minutes), secret professionnel (trente
  minutes sans un mot), le travail c'est la santé, le temps c'est de l'argent, la bonne paye, le
  marchand de sable, la boucherie (60 zombies en une minute), le tribunal du diable, la carrière,
  l'épreuve du forgeron, le vrai boss final.
- **Halloween** (17, tous bons) : dix fantômes, six zombies à tête de citrouille, golem citrouille,
  Hell Yeah (le sol s'embrase), Spooky Spook (ouvrir son inventaire fait hurler), l'entonnoir du
  trick or treat (des lingots de paladium en cinq minutes, sinon un mauvais événement au hasard),
  Jack-o-lantern, et les objets : ciseaux à citrouille (huit visages), gants tisseurs, balai de
  sorcière, pierre d'Halloween, cape fantôme, tarte infinie, huit lucky blocks en paladium.
- **Novembre** (15) : onze événements repris des autres mois sous leur nom de novembre
  (`Catalog.action`), l'inventaire explosif, le sauteur professionnel (Saut XLI une minute), la
  tempête de sable, le kit du petit alchimiste (64 blocs de chaudron, son cœur, neuf seaux d'eau
  angélique).
- **Noël** (18) : la vache de Noël et son lait chocolaté, le crédit à la consommation (quitte ou
  double, 10 000 $ au plus), trop mangé, trop bu, grand froid (de la neige sous les pas), privé de
  Noël (un bloc d'obsidienne renforcée autour du joueur), le traîneau, freeze (trente secondes figé),
  l'armée de neige, le Père Noescroc (le cadeau choisi n'est qu'un bloc de terre qui en porte le
  nom), le village de Noël (le God Villager, § 6 terdecies de `BALANCE.md`), et la hache du roi des
  forêts, la pierre de Noël, les cadeaux, la carte de vœux, la maquette.
- **Noir** (16) : sifflet de train, sherpa (des vaches en laisse), l'énigme (10 000 $ ou 5 000 $ de
  moins), pistolet à eau, petit chien chien, bottes supersoniques, missile météo, solitaire,
  cauchemardesque, bras extensible, dauphin tueur, flasque d'alchimiste, Pa-rtout-ladium (135
  minerais de paladium autour du joueur), une offre en or, Easy Cash, Cosmetico.

### Les objets

| Objet | Chez PalaMod | Ici |
|---|---|---|
| Couteau de boucher | épée en paladium, +25 dégâts aux zombies | pareil |
| Offre d'emploi, dossier de métier | XP de métier ; les métiers d'un joueur | 2 000 XP dans un métier au hasard ; ses propres métiers |
| Amulette démoniaque | force et résistance au feu | une minute, cinq minutes de recharge |
| Ciseaux à citrouille | huit visages de `BlockPumpkinCustom` | huit citrouilles sculptées, orientables |
| Gants tisseurs (`ItemSpiderGlove`) | un arc qui lance une toile | une toile lancée, qui reste trente secondes |
| Balai de sorcière | porté : deux minutes de vol, douze heures de recharge | tenu, clic droit |
| Pierre d'Halloween | un fantôme qui aveugle les joueurs ; six usages, 30 minutes | touche aussi les monstres (et les ralentit) |
| Tarte infinie (`BlockHalloweenTrade`) | 64 tartes à la citrouille par jour | par joueur |
| Hache du roi des forêts | une bûche coupée donne un sapin de Noël | pareil |
| Pierre de Noël | fait le vide dans un rayon de 4, une fois par jour | pareil, jamais dans un claim ennemi |
| Cadeau, sac de cadeaux, cadeau multicolore | un objet au hasard ; 1 à 3 cadeaux ; trois lucky blocks | les objets qui existent ici |
| Sifflet de train | un bloc à notes qui siffle | la flûte du jeu |
| Pistolet à eau, bottes supersoniques | jet d'eau ; pas de géant et flammes | éteint et repousse ; Vitesse II et flammes |
| Missile météo | change le temps | pareil, s'il a le ciel dégagé |
| Bras extensible (`ItemGadgeto`) | recette avec un outil | tenu avec l'outil dans l'autre main : +3 blocs de portée |
| Flasque d'alchimiste | une fiole de six gorgées | six gorgées, un bon effet au hasard |

### Écarts

- **Structures** : les schematics du serveur Event (salle de classe, cockpit et Lune, boucherie,
  tribunal, agence de voyage, maison du Père Noescroc, village) ne sont pas dans le jar : les décors
  utiles sont reconstruits autour du joueur et repartent ensuite, les autres sont omis.
- **Réponses** : OUI/NON, ARGENT/TEMPS et les choix des PNJ deviennent des lignes à cliquer. Le test
  d'anglais, l'énigme du noir, le crédit à la consommation et les codes se tapent toujours dans le
  chat ; l'énigme ne se répond plus sur un panneau.
- **Créatures** : les créatures de PalaMod (fantômes, golem citrouille, vache de Noël, armée de
  neige, gardes du corps, PNJ) sont des créatures du jeu renommées et réglées.
- **Noms et images** : « Spider-Steve » devient **Tisse ta toile**, « Lucky Luck » **Pistolet à
  eau**, « Sonic Boots » **Bottes supersoniques**, « Gogo Gadgeto Grip » **Bras extensible**,
  « Milkado » **Chocolat chaud**, « MLG pro » **Pro du seau d'eau** ; leurs images, celles du
  chevalier sombre, du sauteur professionnel et du petit chien chien sont dessinées ici (elles
  reprenaient un personnage, un logo ou un jeu connus). Le disque d'Halloween est un disque du jeu.
- **Seul sur le serveur** : la prime d'« Enfin une prime » envoie aussi trois chasseurs de primes ;
  « Solitaire » cache les autres joueurs et ne change rien quand on est seul.
- **Argent de la boutique** : les 25 PB d'Easy Cash deviennent 25 000 $, le cosmétique de Cosmetico
  un chapeau de gala.

### Pas portés

Dix événements classiques, qui mettaient en scène le serveur Paladium et ses joueurs : la statue des
admins, le grade Endium, les deux disques et la boutique de Fuze, « To doum doum », « Adieu
faction », le spam de /tpahere, et les deux skins de golem (amélioration et golem crasseux).

### Équilibre

`tools/lucky_assets.py --stats` après ce lot : tous les événements des saisonniers sont portés ; au
paladium 107 / 111, à l'endium 53 / 56, au findium 124 / 130. La part des bons reste celle de
PalaMod (de 37 % en novembre à 100 % à Halloween ; 90 % au noir).

Les nouveaux événements donnent surtout des objets, des effets et des épreuves, sans prix de
revente : l'espérance en argent (`BALANCE.md` § 6 tervicies) baisse — ~3 600 $ au paladium,
~6 900 $ à l'endium, ~230 $ au findium, quelques centaines au noir et à Halloween, presque rien aux
autres mois — et le Lucky Pass vaut **~2 800 $ par jour** au lieu de ~6 400. Une Diamond Beacon
tous les 475 jours de lancers. Le levier est `LuckyType` (les poids) si le pass doit rapporter plus.

## 42. Grades et Premium (2026-11-20)

Demandés le 2026-11-20 (« Peux-tu ajouter les grades et toutes les fonctionnalités associées ? »).
Choix du joueur : les grades **s'achètent à la Boutique avec l'argent du jeu**, ils sont
**permanents**, et **Premium** s'y ajoute pour **30 jours**, à racheter.

### Ce que dit Paladium

Les grades ne sont pas dans le jar : c'est le serveur (ses plugins et ses permissions) qui les
porte. Le jar n'en connaît que deux traces : le coffre de l'Ender en paladium, agrandi selon une
permission (4 rangées par défaut, 5 au plus, § 11), et les lucky blocks « grade Paladin » (un faux,
§ 38) et « grade Divinity » (non porté, § 39). Les avantages viennent donc du wiki de Paladium, page
« Java | Par grade » (lue le 2026-11-20) : ils donnent « du confort », jamais de performances.

| | Héros | Légende | Divinité | Premium (en plus) |
|---|---|---|---|---|
| Homes par serveur faction | 5 | 10 | 15 | +5 |
| RTP | 5 | 10 | 10 | +3 par mois |
| Cases d'Ender Chest | 18 | 27 | 36 | +9 |
| XP de métier | +5 % | +10 % | +15 % | +5 % |
| Points du Palapass | +5 % | +10 % | +15 % | +5 % |
| XP vanilla | +10 % | +20 % | +30 % | +10 % |
| Prix d'un AP, location d'une MDF | -10 % | -20 % | -30 % | -10 % |
| Taxes du market | — | -10 % | -20 % | -10 % |
| Anti-AFK | 10 min | 15 min | 20 min | +10 min |
| Macros NEI | — | 5 | 10 | +3 |

Tous les grades : pseudo et chat en couleur, accès prioritaire aux serveurs faction, pas de perte
d'XP à la mort, 10 places de vente en plus au market. Légende et Divinité : les emojis, la
téléportation instantanée (`/spawn`, `/tpa`, `/tpahere`). Commandes : Héros `/craft`, `/minage`,
`/farmland`, `/serverhistory`, `/re` ; Légende en plus `/feed`, `/furnace` (sans XP de métier),
`/tptoggle`, `/rangement`, `/me` ; Divinité en plus `/condense`, `/hat`, `/deathlocate`, `/skin`.
Premium (abonnement mensuel, cumulable) : le pseudo en couleur sans grade, un home depuis n'importe
quel serveur, le serveur de test, un message de connexion, `/ec`, `/go`, `/fly` (au lobby), `/nv`.
La FAQ précise que les grades classiques durent le temps acheté ; Premium, jusqu'à la résiliation.

### Ce que fait DoHy

- **L'achat** : la section GRADES en tête de l'onglet Boutique du commerce, et la page des grades
  (`/grade`, entrée **Grade** de la barre latérale, groupe Personnage). Héros 10 M, Légende 100 M,
  Divinité 1 G, une fois pour toutes ; monter d'un grade coûte la différence. Premium 15 M les
  30 jours, en temps réel (il court partie fermée), prolongé de 30 jours à chaque achat, 360 au
  plus. Prix et raisons : `BALANCE.md` § 6 quatervicies.
- **La page des grades** : les quatre cartes d'achat (avec leur bulle, ce que donne le grade), TES
  COMMANDES (une carte par commande ; celles qu'on a se lancent d'un clic, les autres disent le
  grade qu'il faut), CE QUE DONNE CHAQUE GRADE (une ligne par avantage, les valeurs des quatre
  niveaux et de Premium, la sienne en pastille), et les commandes de tous.
- **Les valeurs** sont celles du tableau, avec les écarts plus bas. Sans grade : 2 homes, 1 `/rtp`
  par mois, les 27 cases du jeu.
- **Homes** : `/sethome [nom]`, `/home [nom]`, `/homes` (cliquables), `/delhome nom`. Dans
  l'Overworld, le Nether, l'End et la Terre ; pas au spawn ni à la Mine (remise à neuf toutes les
  cinq heures). Au-dessus de sa limite (fin de Premium), on garde ses homes, sans en poser de
  nouveau.
- **Téléportations** : `/home`, `/rtp`, `/minage` et les `/tpa` attendent **3 secondes sans
  bouger ni prendre de coup**, comme sur Paladium ; **tout de suite à partir de Légende**. Jamais
  en combat (`CombatTag`), ni depuis un donjon, une arène, Arrakis ou la dimension du Mineur, qui
  ont leurs propres sorties.
- **`/rtp`** : avec la carte de la Terre, le saut en parachute du puits du spawn (`EarthJump`) ;
  sinon un sol sec entre 800 et 4 000 blocs du spawn du monde. Un `/rtp` raté n'est pas compté ;
  le compte repart chaque mois.
- **`/tpa joueur`**, **`/tpahere joueur`**, **`/tpaccept`**, **`/tpdeny`** (boutons cliquables,
  une minute pour répondre) ; **`/tptoggle`** (Légende) refuse les demandes.
- **Commandes de grade** : `/craft` (un établi partout), `/minage` (la Mine sans passer par le
  spawn ; `/spawn` puis la borne RETOUR ramènent là où on était), `/feed` (toutes les 5 minutes),
  `/furnace` (la pile en main cuite d'un coup, sans XP), `/rangement` (les 27 cases du sac
  regroupées et triées, la barre d'action ne bouge pas), `/condense` (tout ce que l'établi
  compresse par neuf : lingots, minerais bruts, pépites, blé...), `/hat` (l'objet en main sur la
  tête, sauf casque maudit), `/deathlocate`. Premium : `/ec` (l'Ender Chest partout, sauf en
  combat), `/fly` (au spawn seulement ; le vol s'en va dès qu'on en sort, avec une chute lente),
  `/nv` (vision nocturne sans fin, remise à chaque réapparition).
- **Les bonus** : l'XP de métier dans `JobApi.grant`, additionnée aux autres bonus (Apprentissage,
  amulettes) avant les potions ; l'XP du pass sur ses quêtes (`PassApi.complete`) ; l'XP vanilla
  sur tous les points gagnés (`PlayerXpEvent.XpChange`, une fraction d'orbe tirée au sort) ; l'XP
  gardée à la mort (tous les grades, pas Premium seul) ; 10 annonces de plus à l'hôtel des ventes
  (16 au lieu de 6) ; la taxe du marché international réduite.
- **L'Ender Chest** : les 27 cases du jeu et une rangée par grade (Héros 36, Légende 45, Divinité
  54), une pour Premium : **63 cases au plus**, sur 7 rangées. Tous les coffres de l'Ender du
  joueur montrent le même contenu (le coffre du jeu, celui en paladium, l'enderchest de poche,
  `/ec`) ; les rangées en plus sont gardées sur le joueur, la première étant celle du coffre en
  paladium. Si la place diminue (fin de Premium), ce qui ne tient plus revient dans l'inventaire.
- **Le pseudo** : « [Légende] Pseudo », dans la couleur du grade, dans le chat et les messages de
  mort ; « [Premium] » sans grade. La liste des joueurs (touche Tab) colore le pseudo et ajoute la
  pastille du grade (« LEGENDE+ » avec Premium). **Le chat** : `&a`, `&l`... à partir de Héros
  (pas le texte illisible `&k`) ; 22 emojis (`:coeur:`, `:etoile:`, `:epee:`...) à partir de
  Légende. **L'arrivée** d'un joueur Premium est annoncée à tous ; trois jours avant la fin, il est
  prévenu ; à la fin, ses avantages s'en vont.
- **Administration** : `/dohy grade <joueur> <aucun|heros|legende|divinite>`,
  `/dohy premium <joueur> <jours>` (0 l'enlève).
- **Les insignes** : quatre icônes dessinées (`tools/grade_assets.py`), ni gagnables ni
  vendables ; elles servent aux menus et à l'encyclopédie (fiche « Les grades »).

### Écarts

- **L'Ender Chest** : le jeu en donne déjà 27 cases sans grade ; DoHy garde ce socle et ajoute une
  rangée par niveau, comme Paladium (une rangée de plus par grade).
- **Les taxes du market** : l'hôtel des ventes de DoHy ne prend pas de taxe ; la seule taxe de
  vente est celle du marché international (de 15 à 6 % selon le rang de la nation), et c'est elle
  que le grade réduit : -30 % au plus, Divinité et Premium, soit 4,2 % au rang Superpuissance.
- **La téléportation instantanée** : `/spawn` est déjà instantané pour tous dans DoHy ; l'attente
  de trois secondes ne vaut que pour les nouvelles téléportations.
- **Pas d'équivalent** : l'accès prioritaire, l'anti-AFK, les macros NEI, `/go`, `/re`,
  `/serverhistory`, le serveur de test et le home depuis n'importe quel serveur (un seul monde) ;
  la remise sur un AP ou une MDF (rien à louer dans DoHy) ; `/skin` (pas d'apparences d'armure) ;
  `/me` est la commande du jeu, ouverte à tous. `/farmland` mène à la farmland du Chasseur (lot 24e,
  `docs/ORGANISATIONS.md`).
- **Un grade ne s'arrête pas** : sur Paladium, les grades classiques durent le temps acheté ; ici,
  ils sont permanents (choix du joueur). Seul Premium est un abonnement.

## 43. Les kits (2026-11-23)

Demandés le 2026-11-23 (« ajoute un système de kit de départ comme sur paladium »). Pas de choix à
faire : le kit de départ arrive de lui-même, les autres se prennent quand on veut.

### Ce que dit Paladium

Comme les grades, les kits ne sont pas dans le jar : c'est le serveur qui les donne (`/kit`). Le
wiki n'en publie pas le contenu ; il dit seulement que le grade Bedrock comprend un « kit basique ».
Le contenu et les délais de DoHy sont donc **calibrés sur sa propre économie** (`BALANCE.md`
§ 6 trigesies), pas recopiés.

### Ce que fait DoHy

| Kit | Pour qui | Délai | Contenu |
|---|---|---|---|
| Kit de départ | tous | une seule fois, **donné à la première connexion** | épée, pioche, hache et pelle en fer ; armure de fer complète ; 32 steaks, 32 torches, un lit, un établi, un four ; une clé de box Améthyste ; 10 000 $ |
| Kit joueur | tous | 24 h | 16 steaks, 16 torches |
| Kit Héros | Héros et plus | 7 jours | clé Améthyste, potion d'XP de métier ×2 (1 h), 32 steaks, 2 pommes dorées |
| Kit Légende | Légende et plus | 7 jours | clé Saharia, potion ×3 (1 h), 32 carottes dorées, 4 pommes dorées |
| Kit Divinité | Divinité | 7 jours | clé Paladium, deux potions ×3 (1 h), 8 pommes dorées, une pomme dorée enchantée |
| Kit Premium | Premium en cours | 7 jours | clé Améthyste, potion ×2 (1 h), 16 carottes dorées |

- **Où** : `/kit` (la liste), `/kit <kit>` (le prendre directement, comme sur Paladium), l'entrée
  **Kits** de la barre latérale (groupe Personnage, avec une pastille quand un kit attend), et
  l'**intendant**, le PNJ de la plateforme d'arrivée du spawn (`CONTENU_DOHY.md`, « Les
  guichetiers »).
- **L'écran** : une carte par kit — **Prêt** (un clic le prend), **Dans 5 h 12 min**, **Déjà
  pris**, ou grisée avec le grade qu'il faut — et son contenu dans la bulle ; en tête, ton grade, le
  nombre de kits prêts et le temps avant le prochain.
- **Le kit de départ** arrive dans le sac à la première connexion d'un nouveau joueur (moins d'une
  minute de jeu, la même règle que l'arrivée au spawn). Un joueur qui jouait avant les kits est
  seulement prévenu à chaque connexion (« Kit de départ t'attend : /kit depart. ») jusqu'à ce qu'il
  le prenne.
- **Les délais** sont en heures réelles, partie fermée comprise. Un grade donne aussi les kits des
  grades d'en dessous, chacun avec son propre délai. Les kits pris sont gardés à la mort
  (attachement `kits`).
- **Le kit entier, ou rien** : il faut assez de cases libres dans le sac (une par pile du kit) ;
  sinon le kit attend, rien ne tombe par terre. Pas de kit en plein combat (`CombatTag`).
- **Administration** : `/kit reset <joueurs>` (opérateurs) efface les kits pris.
- **Le fichier** : `data/kits.json` (copié dans le jar en `data/dohy/kits.json`), lu au démarrage
  du monde ; un objet d'un mod absent est écarté avec une ligne au journal. Chaque kit : `id`,
  `name`, `icon`, `color`, `once`, `first_join`, `cooldown_hours`, `grade`, `premium`, `money`,
  `items`.

### Code

- `kits/PlayerKits` (le fichier, les règles, donner), `PlayerKitsMenu` (l'écran `dohy:kits`),
  `DoHyKits` (l'attachement `kits`, `/kit`, la première connexion) ; la pastille dans
  `ui/NavBadges`, l'entrée dans `client/ui/shell/Nav` et `HubController`.
- L'intendant : `spawn/SpawnNpc.Role.KITS`.

### Écarts

- **Le contenu est celui de DoHy** : Paladium ne le publie pas. Les clés de box et les potions d'XP
  font des kits de grade un petit revenu hebdomadaire (de 2 à 6 % de la valeur du grade par mois) ;
  le kit joueur ne donne que de quoi manger et s'éclairer.
- **Pas de kit de faction ni de kit d'événement** : rien dans DoHy n'en appelle un pour l'instant.

---

## 44. Les objets de déblocage qui manquaient (chapitre 25, 2026-11-24)

Demandés le 2026-11-24 (« Fais 1 (en utilisant les fichiers a ta disposition et les textures, aide
toi du wiki pour l'utilisation) ajoute aussi un support jei pour les crafts dans la palamachine »).
Onze des douze verrous de métier qui n'avaient pas d'objet en ont un ; le douzième, la **cage à
familier**, arrive avec les familiers (lot suivant).

Relevé au bytecode dans PalaMod 10.3.0 (`ItemEndiumNametag`, `ItemInfernalKnocker`,
`ItemHunterCompass`, `BlockEndiumTotem`, `TileEntityEndiumTotem`, `BlockSacrificeHotel`,
`BlockPerlinpinpinPower`, `PRegister_Craft`) et PalaJobs 8.0.0 (`EntityBambooBoat`,
`ModelBambooBoat`, `BlockColoredGrassSeeds`, `BlockColoredGrass`, `BlockColoredTallGrass`,
`CraftRegistry`, le racinier) ; l'usage au wiki. Textures : pack officiel et PalaJobs. Modèles :
la table d'alchimiste traduite de son fichier Techne (`ModelAlchemyCreator`, 38 pièces), le bateau
de `ModelBambooBoat`.

### Les verrous

| Métier | Niveau | Objet |
|---|---|---|
| Chasseur | 10 | Bateau en bambou |
| Chasseur | 45 | Knocker infernal |
| Chasseur | 90 | Étiquette en endium |
| Chasseur | 100 | Totem en endium, autel de sacrifice, poudre de perlimpinpin, boussoles de chasseur (5 paliers) |
| Fermier | 45 | Graines d'herbe colorée (10 couleurs) |
| Fermier | 50 | Graine de racinier |
| Fermier | 55 | Hautes herbes colorées (10 couleurs) |
| Alchimiste | 15 | Table d'alchimiste |

Une famille de variantes (couleurs, paliers) passe par un tag : `#palamod:colored_grass_seeds`,
`#palamod:colored_tall_grass`, `#palamod:hunter_compass`.

### Le rituel d'endium — `BlockEndiumTotem`, `TileEntityEndiumTotem`

- **La structure**, relevée dans le code : le **totem** au centre ; quatre **autels de sacrifice**
  à trois blocs, au nord, au sud, à l'est et à l'ouest ; **40 poudres de perlimpinpin** en contour
  de croix grecque autour (x de −2 à 2 à z = ±5, z de −2 à 2 à x = ±5, et les quatre coudes de −4
  à −2 et de 2 à 4 à ±2). La poudre se pose à plat et dessine un trait ou une croix selon ses voisines.
- **Les offrandes** : un autel est servi par un joueur **debout dessus**, ou (pour le solo) par
  une **pierre de capture pleine** posée d'un clic droit ; accroupi, main vide, on la reprend
  tant qu'aucun rituel ne tourne.
- **Le rituel** démarre quand la croix est fermée, les quatre autels servis et un joueur dans la
  zone (±10 blocs). La poudre étincelle ; au bout de 5 s, le totem appelle des **zombies en armure
  complète en paladium** (un toutes les 3 s, quatre debout au plus, sans butin d'armure). Une barre
  de boss compte les **25 zombies** à tuer.
- **Rompu** si une poudre ou un autel est cassé, si une offrande s'en va, ou si plus personne
  n'est dans la zone depuis **10 secondes** (un mot prévient à la sortie : on peut poursuivre un
  zombie hors de la croix) : le message dit pourquoi, les zombies repartent, le compteur revient à
  zéro, les offrandes restent. Un spectateur ne tient pas le rituel.
- **À 25** : les joueurs debout sur les autels meurent (le sacrifice), les pierres sont
  consommées. Un clic sur le totem : 3 s de flammes, puis le totem laisse un **coffre** avec un
  **pépite d'endium** au milieu (case 13).
- Recettes : totem = venin de serpent, plume de perroquet, écailles de tortue / fer de chèvre,
  table d'enchantement, fragment de crâne de wither / crochet de méduse, coquille d'escargot, bave
  de panda ; autel = charbon mixé de paladium, livre / amulette du chasseur, mini golem, amulette
  de voyance / 3 obsidiennes ; poudre (×1) = glowstone, lingot de paladium, sucre / lingot, boule de
  slime, lingot / poudre à canon, lingot, redstone.

### Objets du chasseur

- **Étiquette en endium** : sur une créature déjà nommée, le nom passe en aqua et la créature ne
  disparaît plus. Trois créatures par joueur : la quatrième rend son nom d'origine à la première.
  Une créature morte sort de la liste. Recette : pépite d'endium / findium, étiquette, findium /
  lingot de paladium, findium, lingot de paladium.
- **Knocker infernal** : une épée de bois (4 de dégâts) qui projette la cible au loin dans le sens du
  regard (la poussée du code : `-sin(yaw) × 3 × 0,5`, 0,1, `cos(yaw) × 3 × 0,5`), withers compris ;
  pas enchantable. Recette en colonne : lingot de paladium, orbe de recul, bâton en paladium.
- **Bateau en bambou** : trois fois plus rapide qu'un bateau (le code de PalaJobs : vitesse 1,05
  et accélération 0,21 au lieu de 0,35 et 0,07). Ici : une poussée de 0,08 ajoutée aux 0,04 du
  bateau quand on rame des deux mains. Modèle et texture de PalaJobs : la texture ne dessine que le
  fond (la place des bords est transparente), c'est donc un **radeau**, comme sur Paladium ; la
  planche et le siège sont à la hauteur du radeau de 26.2. Recette : 5 blocs de bambou en U.
- **Boussoles de chasseur** (amethyste, titane, paladium, endium, légendaire) : les 32 images de
  chaque palier. Accroupi, un clic droit sur une créature en fait la **proie** ; accroupi dans le
  vide, on l'efface (la boussole suit alors le monstre le plus proche). Chaque seconde l'aiguille
  pointe la plus proche de l'espèce dans la portée du palier — **48, 96, 192, 384 et 768 blocs** —
  et la barre d'action dit à combien elle est. Recettes : findium, unclaim finder (vert, orange,
  rouge), lingot du palier, boussole ; l'endium ajoute une pépite, la légendaire remplace la
  boussole par un paladium compressé.

### La table d'alchimiste — `alchemy_creator`, `AlchemyCreatorRecipes`

- Un bloc orienté au modèle de PalaMod. **Deux ateliers dans le même écran** : potions (4 entrées,
  10 s) et flèches (4 entrées, seize flèches en 1 s). Les entrées sont en position fixe : un
  ingrédient, une verrue du Nether, un second ingrédient, une fiole vide ou une flèche.
- Potions : **poison** (œil d'araignée, verrue, patate empoisonnée), **feu** (poudre de blaze,
  verrue, boule de feu), **wither** (fragment de crâne de wither, verrue, os), **maladie** (poudre
  de blaze, verrue, œil d'araignée : potion jetable, nausée II 10 s).
- Les trois premières se boivent et **imprègnent** le buveur 100 s (2 000 ticks) : chaque coup
  qu'il porte ajoute wither I 5 s, poison I 0,5 s ou 2 s de feu.
- Flèches ×16 : **poison** (poison II 6 s), **wither** (wither I 9 s), **lenteur** (œil fermenté,
  verrue, carotte : lenteur III 5 s), **switch** (deux perles ; déjà portée), **givre** (bloc de
  neige, verrue, boule de slime : effet Givre 20 s, qui **empêche le rebond du slimepad**). Comme
  dans le code, l'effet ne part que des flèches tirées à l'**arc en paladium**.
- Recette de la table : poudre de blaze, paladium compressé, poudre de blaze / lingot de titane,
  cœur en paladium, lingot de titane / 3 lingots de titane.

### Herbes colorées — `BlockColoredGrassSeeds`, `BlockColoredGrass`, `BlockColoredTallGrass`

- **Dix couleurs** (noir, rouge, vert foncé, marron, bleu, violet, jaune, orange, rose, vert). La
  graine = colorant + graine de blé, sans forme.
- Plantée sur de la **terre labourée**, à la lumière (9 au moins), elle pousse comme une culture
  (plus vite sur terre humide) ; quand elle pousse, la terre devient **herbe colorée** de sa
  couleur et la graine disparaît. La poudre d'os la fait pousser d'un coup. L'herbe colorée ne se
  récupère pas (elle rend de la terre), comme le dit le wiki.
- **Hautes herbes colorées** = graine colorée + herbe : décoration, sur terre, terre labourée ou
  herbe colorée.
- Textures de PalaJobs (dessus, flanc et graines), teintes par couleur comme l'herbe vanilla.

### Le racinier

- **Graine de racinier** : graine de blé / bois mort, seau d'eau angélique, bois mort / 3 buissons
  morts. Sur terre labourée, elle pousse en trois temps (la formule de culture de 1.7.10), puis
  **germe** : le feuillage à sa place, trois bûches de racinier dessous, et la **racine** qui
  descend d'un bloc par seconde en une seule colonne, jusqu'à la bedrock.
- Elle s'arrête sur ce qu'elle ne peut pas traverser : la liste du code est la bedrock et
  l'obsidienne (`JobsConstants.ROOT_BLACKLISTED_BLOCKS`) ; ici aussi l'obsidienne pleureuse, les
  blocs incassables et tout bloc qui porte des données (coffre, machine). Le **feuillage rougit**
  quand elle est arrêtée — à la bedrock comme à l'obsidienne : c'est la **profondeur** de la racine
  qui dit s'il y a un bunker. L'obstacle parti, elle **repart** (le code revoit le bloc du dessous
  chaque seconde) et le feuillage reverdit.
- La racine est fine, on passe au travers et on y grimpe. Casser le feuillage (ou une racine)
  retire la colonne en dessous ; feuillage et racine ne rendent rien. Bûche (orientable, dans
  `#logs`) et planches (4 par bûche) de racinier.

### JEI

- **Si JEI est installé**, deux catégories : la **palamachine** (le centre, les quatre coins et la
  sortie, comme dans la machine) et la **table d'alchimiste** (potions et flèches), avec leur bloc
  comme poste de fabrication. L'API de JEI n'est là qu'à la compilation : sans JEI, rien ne
  change.

### Code

- `palamod/unlock/` : `PalaUnlocks` (registres), `EndiumRitual`, `EndiumTotemBlock(Entity)`,
  `SacrificeAltarBlock(Entity)`, `PerlinpinpinPowderBlock`, `EndiumNametagItem`,
  `InfernalKnockerItem`, `BambooBoat`, `HunterCompassItem`, `AlchemyCreatorBlock(Entity)`,
  `AlchemyCreatorRecipes`, `ImbuePotionItem`, `PalaArrowItem`, `UnlockEffect`, `UnlockEvents`,
  `GrassColor`, `ColoredGrassSeedBlock`, `ColoredTallGrassBlock`, `RootSeedsBlock`,
  `RootLeavesBlock(Entity)`, `RootColumnBlock`, `PetHooks` (l'attache des familiers).
- Client : `client/unlock/UnlockClient` (écran, teintes, rendu du bateau),
  `client/jei/DoHyJeiPlugin`, `PalamachineCategory`, `AlchemyCreatorCategory`.
- Assets : `tools/unlock_assets.py` (≈ 400 fichiers, `--check` en CI) ; `tools/techne_json.py`
  traduit un modèle Techne en modèle de bloc.
- Encyclopédie : quatre fiches (rituel d'endium, objets chasseur, table alchimiste, herbes et
  racinier).

### Écarts

- **Les offrandes en solo** : une pierre de capture pleine sert d'offrande ; l'original voulait
  quatre joueurs sur les autels. **Les zombies** viennent du totem ; l'original comptait ceux de la
  nuit.
- **La boussole** : la cible venait du serveur de Paladium (canal `huntercompass`), elle n'est pas
  dans le mod. Ici elle suit une proie choisie ; le palier ne change que la portée.
- **La table d'alchimiste** montre ses deux ateliers ensemble au lieu de deux onglets.
- **Le racinier** : la racine se traverse et s'escalade ; elle s'arrête aussi devant un bloc à
  données, pour ne pas manger un coffre.
- **Le bloc de bambou** est celui de vanilla ; **la boule de glu bleue**, pas portée, est remplacée
  par une boule de slime dans la recette de la poudre.
- **Le verrou du Fermier 55** visait l'herbe colorée elle-même, qui ne se récupère pas : il porte
  sur les hautes herbes colorées, pour qu'il verrouille quelque chose.
