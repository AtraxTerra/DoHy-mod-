# État d'avancement

> Ce fichier est la **mémoire du projet**. Une session Claude ne garde rien d'une fois sur l'autre :
> lire `PROGRESS.md`, `docs/DESIGN.md` et `docs/BALANCE.md` suffit à reprendre le travail.
> **À mettre à jour à chaque fin de session.**

Dernière mise à jour : 2026-11-13

---

## Cible technique

| | |
|---|---|
| Minecraft | 26.2 (*Chaos Cubed*, sorti le 16 juin 2026) |
| Loader | **NeoForge 26.2.0.93** (stable) |
| Java | **25** (la table d'installation NeoForge qui dit 21 est obsolète) |
| Gradle | **9.1** minimum (premier Gradle avec le toolchain Java 25) |
| Build | ModDevGradle **2.0.148**, plugin `net.neoforged.moddev` |
| Mappings | mojmap, **pas de remap** |
| Pack formats | assets **88**, data **107** (ils divergent depuis 26.x) |
| Mod optionnel | NTM NEXT 1.0.x (26.2, NeoForge) — phase 10 |
| Mode de jeu | **Serveur dédié qui tourne 24 h/24** (dit par le joueur le 2026-11-13) ; solo jusque-là |

**Le joueur joue désormais sur un serveur dédié qui tourne 24 h/24** (dit le 2026-11-13 ; jusque-là,
solo uniquement, dit le 2026-11-11). Seul ou avec d'autres : pas encore dit. Le mod reste pensé
pour un joueur — pas de fonction qui n'a de sens qu'à plusieurs (échange entre joueurs, hôtel des
ventes de joueurs, classements) ; quand un mod de Paladium est multijoueur par nature, on en porte
ce qui se joue seul, ou on le remixe pour un joueur, comme la faction (chapitres 22a et 22b).
**Ce qui change, c'est la façon d'écrire** : tout doit tourner sur un serveur dédié (rien de
`client/` hors du client), ce qui avance hors ligne suit l'horloge réelle (`RealClock`, à l'heure
de Paris), et ce qui touche physiquement au monde n'agit que près d'un joueur connecté — jamais
sur une base laissée seule. Installation et réglages : `docs/CONTENU_DOHY.md`, « Sur un serveur
dédié ».

**Choix d'architecture : un seul module NeoForge**, pas de découpage multiloader Architectury. La cible est NeoForge uniquement ; le surcoût de scaffolding common/fabric/forge ne se rentabilise que si le mod doit sortir sur plusieurs loaders.

---

## Fait

- [x] `docs/DESIGN.md` — spec des 8 systèmes, anti-exploits, ordre d'implémentation, décisions tranchées
- [x] `docs/BALANCE.md` — modèle de prix, courbes des métiers, progression attendue, sinks
- [x] `data/rates.csv` — 152 items avec leur taux d'obtention horaire et leur tier
- [x] `tools/gen_prices.py` — générateur de prix + vérification de cohérence $/h par tier
- [x] `data/prices.csv` / `data/generated/prices.json` — générés, 152 items
- [x] `data/job_progression.json` — courbe, primes de palier et sources d'XP des 4 métiers (ex-`skills.json`, 8 skills puis 9 jusqu'au 2026-11-08 ; perks remplacés par les primes le 2026-11-11)
- [x] `data/collections.json` — 2 courbes de paliers, récompenses, exceptions
- [x] `data/contracts.json` — pool de 18 quotidiens + 10 hebdos, demande du jour, bonus de première vente

- [x] `.gitignore` Gradle / IDEA
- [x] Projet Gradle NeoForge 26.2 : `settings.gradle`, `build.gradle`, `gradle.properties`, `neoforge.mods.toml`, `pack.mcmeta`, classe `@Mod` vide
- [x] GitHub Actions : job de compilation + job de cohérence des données

## À faire — phase 0 (reste)

- [ ] **Faire passer la CI au vert.** C'est la seule validation possible des signatures 26.2 : ni JDK 25 ni accès aux dépôts Maven NeoForge depuis l'environnement de dev.
- [ ] **Générer le wrapper Gradle** : `gradle wrapper --gradle-version 9.1.0` à lancer en local (impossible ici, `services.gradle.org` est injoignable). En attendant, la CI épingle Gradle 9.1.0 via `setup-gradle` et lance `gradle build` au lieu de `./gradlew build`.
- [x] ~~Vérifier `pack.mcmeta`~~ → **tranché le 2026-11-07, sur preuve.** `gradle packMeta` (tâche ajoutée au chapitre 2) lit le jar de Minecraft et celui de NeoForge : MC 26.2 annonce `resource 88.0` / `data 107.1`, et le jar universel de NeoForge déclare `min_format` = `max_format` = `[107, 1]`. Les deux clés sont des paires `[majeur, mineur]` et l'ancienne forme `pack_format` + `supported_formats` est abandonnée. On copie ce que le loader ship.

## À faire — phases 1 à 10

Ordre détaillé et justifié dans `docs/DESIGN.md` §11. Résumé :

1. [x] Monnaie, persistance, HUD, **backups**
2. [x] **Tracking des blocs posés** (chunk attachment) — bloquant pour 5 et 6
3. [x] Shop complet : catalogue de prix, `/worth`, `/sell`, **`/shop` en coffre et `/sellall`**
4. [x] Prix dynamiques et oubli
5. [x] Collections
6. [x] Skills et perks
7. [x] Banque et pénalité de mort
8. [x] Contrats et demande du jour
9. [x] Boosters, caisses, autosell
10. [x] NTM NEXT

---

## Conversion du PalaMod — chapitres

Travail demandé en plus des dix phases : porter le PalaMod de Paladium (Forge 1.7.10) sur ce
mod, en NeoForge 26.2. Un chapitre = une PR.

Sources : dépôt `AtraxTerra/PalaV26` (7 mods + le pack de textures officiel) et
`PalaMod-10.3.0.pala`. Spec extraite au bytecode dans [`docs/PALAMOD.md`](docs/PALAMOD.md) —
**c'est ce fichier qui fait foi, pas le wiki**, qui est incomplet et faux par endroits.

| Chapitre | Contenu | État |
|---|---|---|
| 1 | **Métiers** fusionnés dans les skills, table des 70 verrous, interface, `/metiers` | fait |
| 1b | **Les métiers remplacent les skills** : 4 progressions, 5 skills retirés, XP migrée, 204 récompenses en objets de Paladium | fait |
| 2 | **Minerais et matériaux** `palamod` : 6 minerais + génération, 7 blocs, 7 particules, lingots, compressés, 4 charbons mixés, onglet créatif | fait |
| 3 | **Cultures** : 4 plantes, graines, fruits, lot rare, XP d'agriculture | fait |
| 4 | **Machines** : cobblebreaker 4 paliers, crusher | fait |
| 4b | **Interfaces de machines** : menu et écran propres, barre de progression | fait |
| 5a | **Machines du fermier** : totem de fertilité, 5 seedplanters | fait |
| 5b | **Machines de craft** : palamachine (chapitre 16 ; le grinder est au chapitre 12, la palaforge au chapitre 8) | fait |
| 6 | **Intégration économique** : 24 matières de PalaMod au catalogue (onglet PalaMod), aile de musée, contrats, audit des machines (recette d'or du chaudron retirée, plancher de 5 % des blocs de masse), cultures de PalaMod enfin comptées | fait |
| 7 | **Équipement** : 19 outils, 5 houes à rayon, 6 armures, leurs effets, 49 recettes | fait |
| 9 | **Fermier** : abattage et incassabilité de la hache en endium, eau angélique (fluide qui soigne, seau) — sans herbes colorées, racinier ni trône | fait |
| 10 | **Chasseur** : canne à pêche en paladium, épée en endium qui ne frappe plus à bout, épée et pierre de capture, marteau de caverne, spawner cassé, spawner customisé (âmes, paliers, créatures empilées) — sans contrôleur, familiers, améliorations ni trône | fait |
| 11 | **Alchimiste** : 4 arbres de PalaMod (générés), sève (fiole, extracteur, tanks), chaudron multibloc, potion de foudre, fleurs et pollens, portails à sève et leurs clés, bibliothèque mystique et table de l'Alchimiste (anti chute, grass breaker) — sans table d'alchimiste, totem fleuri, boules de glu ni trône | fait |
| 12 | **Grinder** : multibloc, forge des têtes et des outils en paladium, modificateurs ; marteaux, broadswords, fastswords ; buisson à XP (généré) et baies ; pioche des dieux (100 niveaux, Toucher de soie, fusion automatique) | fait |
| 13 | **Golem gardien** : golem (40 niveaux, arbre à trois voies, choix, gardien de golem), machine à croquettes, boîte à golem et mini golems, pomme en paladium — sans renommeur, cosmétiques, alerte Discord ni trône | fait |
| 14 | **Monture** : le Dancarok (capture, gemme, niveaux, vie et énergie sur le joueur, invocation, conduite, cornes qui creusent), l'enclos (gestionnaire, fondation, barrière, couveuse, soins aux bandages), l'orbe de régénération ; les coffres manquants (paladium, paladium vert, endium, coffre de l'End en paladium, coffre en or) — sans Ravirok, Tedarok, palamachine ni éléphant | fait |
| 15 | **Dimension du Mineur** : obsidienne wither (chargée au paladium, obtenue au piston renforcé), portail du Mineur (propriétaire, Mineur 90), dimension `palamod:miner` (sections de 5 000, 1 000 points, 3 jours, minerais ×3, nuit fixe, sans monstres ni chute), écrans d'entrée et de fin, minerai et Pierre d'Invocation ; la pépite d'endium ne naît plus que dans la dimension — sans boss ni ambiance client | fait |
| 16 | **Pour le PvP** : palamachine (création et réparation des sticks, anneaux et cornes, enchantements gardés), 7 sticks (effets du code, recharge par type sur le joueur), orbes de force et de saut, 6 anneaux (armure portée, colonne d'anneaux des coffres, capacités de réparation), aile et deltaplane, slimepad, spike en paladium — sans spikes des autres métaux ni modèle 3D du deltaplane | fait |
| 17 | **Pierres légendaires** : 7 pierres (téléportation, invisibilité, fortune, pouvoir — résistance au feu 30 min —, métier, chaos, et l'aléatoire qui s'ouvre), recharge de 24 h sur la pierre et verrou de 10 min par joueur ; **gant en endium** (établi, grinder, défilement, 12 h complet, Infinity Gauntlet) ; dans les caisses rare et légendaire | fait |
| 18 | **Donjons, cœur jouable** : 4 clés et l'écran des 100 paliers, groupe de 4, dimension `palamod:dungeon`, salles construites (combat, énigme, boost, marchande, mini-boss en 5, boss en 0), 6 types de monstres renforcés au palier, 40 boss nommés et leurs capacités, coffres individuels, sacs de donjon (9/18/27/36 piles), butin du wiki, XP de métier, mort = palier échoué ; bouteilles d'XP de métier — sans équipement, essences, notes, sorts, runes ni arbre (chapitres 19 à 21) | fait |
| 19 | **Donjons, équipement** : 3 armures et 3 épées par donjon (basique, renforcée, légendaire ; fortes surtout en donjon, à plein dans le leur), set antique du palier 100, récompenses uniques du wiki, essences par élément et tier (réparation à l'enclume), note D à S et son coffre — textures de Palamod Recreated | fait |
| 20 | **Donjons, runes et arbre** : niveau RPG (XP des salles, 100 niveaux), points (niveau, paliers, métiers 1/5), arbre de 4 branches × 8 nœuds en écran de coffre (bouton d'inventaire, `/profil`, `/menu`), statistiques RPG en donjon seulement ; runes commune, rare, épique (coffres, palamachine, enclume, grinder) — sorts et mana sautés au choix du joueur | fait |
| 20b | **Stuff antique** : icônes et armure portée en 3D de Palamod Recreated (modèles traduits de leur bytecode), vol et invisibilité au set complet hors donjon (patchnotes V11), bonus de donjon dedans | fait |
| 20c | **Retours en jeu** : pierre aléatoire qui disparaissait, entonnoirs sous le cobblebreaker et le crusher (sorties seulement), entonnoir en paladium qui aspire au sol, minerais qui se rendent eux-mêmes (fonte au four et au haut fourneau) | fait |
| 21a | **Interfaces, lot 1** : kit d'interface DoHy UI (style Paladium, polices TrueType, vues et actions réseau), écran des métiers avec les contrats du jour, fiche d'un métier à onglets, shop à la maquette de l'Admin Shop — voir [docs/INTERFACES.md](docs/INTERFACES.md) | fait |
| 21b | **Interfaces, lot 2** : menus génériques (`MenuView`) pour le menu principal, la banque, les contrats, la boutique, les améliorations, les collections et le musée | fait |
| 21c | **Interfaces, lot 3** : donjons (paliers, autel de boost, marchande, sac), profil RPG, monture (fiche, enclos, soins), golem (accueil, arbre, choix), dimension du Mineur (entrée, bilan), table de l'alchimiste | fait |
| 21d | **Interfaces, lot 4** : écran de conteneur du kit, les neuf machines (crusher avec ses quatre bacs), coffres en paladium, inventaires en coffre avec leurs propres types de menu (vente, musée, monture, golem, coffre de l'End en paladium) | fait |
| 22a | **Faction solo, cœur** : création (100 lingots de paladium), claims qui protègent la base contre le monde, banque de faction, F homes, ELO et ligues du lundi, onglets sur le kit, `/f` — voir [docs/FACTION.md](docs/FACTION.md) | fait |
| 22b | **Faction solo, arbre de technologie** : 4 branches du wiki (coûts, durées réelles, récompenses), conditions remixées solo, déblocages d'objets pour les lots suivants, écrans de l'arbre | fait (en validation) |
| 8 | **Mineur** : excavatrices, outils de construction, aimant, Dollars Stone, Voidstone minage, compresseur à cobblestone, entonnoir, coffres, Palafurnace, Palaforge, cave blocks, God Villager, pioche et minerai d'endium — sans `/nether` ni trône | fait |

**Décisions prises avec le joueur** (2026-11-07) :

1. **Fusion métier/skill** plutôt que deux progressions parallèles. `mining` → Mineur,
   `combat` → Chasseur, `farming` → Fermier, et un skill `alchemy` créé pour l'Alchimiste.
   Une seule barre d'XP par activité, rien à migrer, aucun risque de dérive entre deux
   compteurs. Coût assumé : la table d'XP de PalaJobs (20 niveaux) n'est pas utilisée.
   → **Dépassé le 2026-11-08** : les métiers ont **remplacé** les skills (chapitre 1b). Les
   cinq skills sans métier sont retirés, sources et perks compris ; chaque niveau verse
   l'argent et les perks de DoHy **et** les objets de Paladium ; l'XP des quatre skills
   conservés est migrée.
2. **Namespace `palamod`** et non `dohy`, avec les identifiants de Paladium. Le pack de
   textures officiel s'applique alors tel quel, sans copie ni renommage dans le jar.
3. **Chiffres manquants calibrés**, puis remplacés par les vrais dès que le jar de PalaMod a
   été fourni. Résultat : presque plus rien n'est calibré — voir `docs/PALAMOD.md`.

**Le chapitre 2 est un prérequis non demandé mais nécessaire** : le cobblebreaker sort des
particules de minerai, le crusher consomme des charbons mixés et rend des lingots. Sans ces
items, les machines des chapitres 3 et 4 n'ont ni entrée ni sortie.

---

## Où regarder quand « rien ne se passe »

**Le chat ne dit pas tout, et le jeu avale les erreurs serveur.** Une exception levée dans un
gestionnaire d'interaction est journalisée puis *supprimée* (`Failed to handle packet ...,
suppressing error`) : le joueur voit un clic sans effet. Le log est donc la première chose à
lire, pas la dernière.

| Quoi | Où |
|---|---|
| Instance de test | `C:\Users\lucas\AppData\Roaming\PrismLauncher\instances\26.2\minecraft` |
| Log du jeu | `…\minecraft\logs\latest.log` |
| Jar installé | `…\minecraft\mods\dohy-0.1.0+mc26.2.jar` |

Et **vérifier le jar avant de soupçonner le code** : `javap -p -c` sur les classes concernées
dit en dix secondes quelle version tourne réellement. Le 8 novembre, le jar testé avait la
PR #37 sans la #36 ; une heure de recherche dans du code déjà correct.

---

## Bloqué

| Sujet | Blocage | Débloqué par |
|---|---|---|
| Build en local (environnement Claude) | JDK 21 seulement, et `maven.neoforged.net` / `plugins.gradle.org` / `services.gradle.org` injoignables | Rien à faire : la CI GitHub est la boucle de validation. Ne pas chercher à compiler dans la session. |

---

## Points ouverts à trancher

- ~~**IDs d'attributs 26.2** : les perks de `job_progression.json` utilisent des ids plausibles qui doivent être validés contre l'API réelle.~~ → **sans objet depuis le 2026-11-11** : les perks sont devenus des primes en argent, plus aucun attribut n'est touché par les métiers.
- **Trocs de villageois en registres de datapack** (nouveauté 26.1 : `data/<ns>/villager_trade/*.json` et `trade_set/*.json`). **Piste à évaluer en phase 3** : plutôt que de plafonner le prix de vente du lapis, de la perle de l'ender et du reste, on peut retirer ou renchérir directement le troc fautif par datapack — et rendre leurs vrais prix à ces items. C'est traiter la cause au lieu du symptôme, et ça rendrait les plafonds inutiles.
- **HUD et GUI en mode retenu** : 26.2 a supprimé `GuiGraphics`. Le HUD de la monnaie et le GUI du shop doivent être écrits directement en `extract*` + `GuiGraphicsExtractor`. L'API exacte d'enregistrement d'une couche de HUD côté NeoForge 26.2 est à confirmer en CI (l'ancien `RegisterGuiLayersEvent` n'est pas garanti sous cette forme). → **tranché le 2026-11-08** : relevé par sonde et écrit — `extractBackground` et `extractContents` (il n'y a plus de `renderLabels`), `blit` avec `RenderPipelines.GUI_TEXTURED`, `centeredText` pour le texte, `getLeftPos` / `getTopPos` (`getGuiLeft` / `getGuiTop` sont marqués pour suppression), `RegisterMenuScreensEvent` avec témoins de type explicites. Voir `MachineScreen` et `docs/PALAMOD.md`.
- **Plafonds villageois** : ~~à confirmer~~ → **tranché le 2026-11-03 : on garde les plafonds.** Ne pas relancer `gen_prices.py` avec `--no-villager-caps`. Audit encore à compléter sur : briques, argile, papier, verre teinté, charbon, fer.
- ~~**Catégorie COMPONENT** : liste et prix d'achat à établir (phase 3).~~ → **tranché le 2026-11-05 : la catégorie est supprimée.** Elle n'a jamais contenu d'item (aucune ligne de `rates.csv`, aucune règle de `tag_prices.json`), donc l'onglet du shop était vide et l'aile de musée correspondante ne pouvait pas se compléter. L'onglet est remplacé par la boutique ; la remise d'achat de −10 % est passée sur l'aile NTM.
- ~~**Table de loot des caisses** : non écrite.~~ → **écrite le 2026-11-11** (BALANCE § 6 quater).
- **Barème d'XP du niveau global** par type de jalon : à fixer quand les contrats tournent.
- **`minecraft:potent_sulfur`** classé farmable sur la base des tags de geyser de 26.2 — **à vérifier en jeu** : si l'éruption ne produit pas de gain net, le reclasser en `FOUND`.

---

## Journal des sessions

### 2026-11-13 (suite 21) — sur un serveur qui tourne 24 h/24

**Dit par le joueur** : « je joue désormais sur un serveur qui tourne 24h/24 », puis « je peux
désactiver le server pause ».

**Revu** : aucun code client hors de `client/` (ni import, ni `Minecraft.getInstance()`), aucune
hypothèse bloquante d'un seul joueur (les tableaux de l'atrium prennent le premier joueur du
spawn, c'est tout), cartes par joueur bornées au nombre de joueurs, sauvegardes de secours en
trois copies glissantes. Les vérifications en jeu de la CI tournent déjà sur un serveur dédié.

**Fait** (`feat/serveur-24h`) : un seul fuseau pour tout le mod, `RealClock.zone()`, à l'heure
de Paris par défaut (option `-Ddohy.fuseau=…`). Avant, six endroits prenaient le fuseau de la
machine : contrats et commande trixium, classement du lundi, events de faction, saisons du
trixium, dates des menus de nation et de la pierre de golem. Le journal donne l'heure retenue au
démarrage (`util/ClockReport` : `RealClock` doit rester compilable sans Minecraft, la CI le
teste seul) ; menus et docs disent « heure de Paris ». Doc : `docs/CONTENU_DOHY.md`, « Sur un
serveur dédié » (installation, heure, pause d'un serveur vide).

**Vérifié** : build vert ; `RealClockTest` étendu au fuseau (Paris sans option ou avec une option
inconnue, nom IANA, « systeme »), qui passe aussi sur une machine en UTC. Le joueur a désactivé
la pause du serveur vide (`pause-when-empty-seconds=-1`) : le monde tourne sans lui.

### 2026-11-13 (suite 20) — l'atlas

**Demande** : « je vire Xaero et tu crées ton atlas comme JourneyMap ». Choix du joueur : minimap,
grande carte plein écran, points de passage, calques DoHy ; fond : le globe d'avance et le
détail exploré. Puis : « pour les textures prends les mêmes textures que pour le palamod » —
les panneaux, boutons, polices et icônes du kit de DoHy, celui des écrans de PalaMod.
Description : `docs/CONTENU_DOHY.md`, « L'atlas ».

**Fait** (`feat/atlas`) : `earth/EarthAtlas` (le globe compressé, les lieux, les pays tenus et
la capitale, envoyés au client ; les pays tenus revus toutes les 5 s et renvoyés s'ils changent) ;
`client/atlas/` — `AtlasScanner` (relevé des chunks), `AtlasTiles`/`AtlasMap` (dalles fines et
d'ensemble, sur le disque), `AtlasGlobe` (carte politique et calque des pays tenus),
`AtlasView` (le dessin commun), `AtlasHud` (minimap, points dans le monde), `AtlasScreen` (M),
`WaypointScreen`, `Waypoints` (points et point de mort), `AtlasClient` (touches M, N, =, -).

**Vérifié en jeu** (client de CI, grille Earth24k sur un monde ordinaire) : globe reçu (256 pays
tenus par les nations, 121 lieux — ceux de NTM manquent à la CI), minimap avec coordonnées et
« France » ; grande carte au bloc près, puis à 96 blocs par pixel (le monde entier, les pays,
les gisements) ; deux points posés, visibles sur la carte et dans le monde (« Base (94 m) ») ;
dalles écrites sur le disque.

### 2026-11-13 (suite 19) — la faune revient

**Demande** : « fais également une passe sur le spawn des animaux ». Choix du joueur : il y en a
trop peu ; revoir les animaux des mods. Description : `docs/CONTENU_DOHY.md`, « La faune ».

**Constat** : les animaux paisibles viennent surtout de la génération des chunks (aucune sur une
carte faite d'avance) ; ensuite le jeu en ajoute toutes les 20 s, plafonné à une dizaine, et la
chasse vide un coin pour de bon. Animaux des mods de la partie : le Dancarok (DoHy, son propre
tirage depuis la suite 17), les pigeons de NTM NEXT (plaines et savanes, groupes de 5 à 10) et
ses sacs plastiques en mer ; aucun autre mod de la liste n'en ajoute.

**Fait** (`feat/faune`) : `world/Wildlife` — toutes les 20 s, moins de 24 animaux à 96 blocs d'un
joueur de l'overworld : deux groupes au plus, de quatre au plus, à 32-80 blocs hors de vue, tirés
dans la liste du biome (mods compris, Dancarok exclu), chaque bête selon la règle de son espèce.

**Vérifié en jeu** (client de CI) : chaque coin vidé de ses animaux puis 12 passages (4 min de
jeu) — plaine : 28 (poules, vaches, cochons, tortues de la plage voisine), plein en quatre
passages ; forêt : 27 (moutons, poules, cochons) ; désert : 21 lapins ; plaine enneigée : 26
(lapins, renards, vaches…). Plus rien au-delà de 24 : le restockage s'arrête.

### 2026-11-13 (suite 18) — la Terre : vraies mines géantes, tout selon la géographie, pétrole en nappes

**Demande** : « un gros changement sur la génération des ressources sur la Terre ». Choix du
joueur : vraies mines géantes, tout selon la géographie (charbon, fer, cuivre, zinc, quartz
compris), pétrole et gaz en nappes (NTM NEXT et Create Diesel Generators). Puis : « envoie le jar
et le datapack ».
Description : `docs/CONTENU_DOHY.md`, « Les ressources ».

**Constat** : la carte Earth24k porte déjà, posés par WorldPainter, du fer, de l'or, du diamant,
du charbon et du quartz du Nether à peu près partout (relevé sur sept régions de l'archive : 50 à
70 quartz par chunk partout, 80 fers au Brésil, 60 charbons en Chine, de l'or en Europe) : la
géographie des gisements s'y noyait.

**Fait** (`feat/terre-ressources`) :
- `EarthBodies` : nettoyage de chaque chunk à sa première visite (la liste `clear`), puis
  245 gisements géants posés à leur vraie place — mines (ellipsoïdes bosselés de trois rangs,
  allongés et tournés), cheminées de kimberlite, nappes de pétrole et de gaz, sables bitumineux,
  géodes de quartz d'AE2 —, et le pétrole des chunks de champ pour Create Diesel Generators (par
  réflexion : `OilChunksSavedData.setChunkOilAmount`).
- `EarthDeposits` : marque `earth_ground` en plus de `earth_deposits` ; un chunk servi avant est
  repris une fois (nettoyage, gisements, filons) ; blocs écrits dans les sections (172 ms par
  chunk avec `Level.setBlock`, 1 ms ainsi) ; 15 ms de service au plus par tick. Charbon, fer,
  cuivre et quartz par pays (vieux bassins d'Europe compris), les autres filons à 60 %.
- `/gisements [ressource]` ; au passage d'une frontière, les ressources du pays.
- `tools/earth_deposits.py` : la table des gisements géants, vérifiée sur la grille de la carte
  (chaque centre dans son pays, ou en mer pour les champs au large ; aucun chevauchement).
- Datapack (`datapack-terre`) : tous les minerais du jeu retirés, zinc de Create coupé, pétrole
  au hasard de Create Diesel Generators interdit dans l'overworld.

**Vérifié en jeu** (client de CI, grille Earth24k sur un monde ordinaire ; NTM NEXT n'est plus
téléchargeable par la CI, le pétrole y était remplacé par du béton noir pour l'essai) :
Kiruna — 40 % de fer dans la roche du gisement, blocs de fer brut au cœur ; bassin permien —
85 % de pétrole dans la nappe ; Jwaneng — cheminée de tuf, 430 diamants ; Tchad (aucune
ressource) — 25 chunks servis, plus un seul minerai du jeu ; Escondida — 5 604 minerais retirés
et 6 945 blocs posés en 9 chunks, 8 ms en tout. `/gisements` et les ressources par pays
répondent. Monde créé avec le datapack : il se charge (un premier essai l'avait refusé :
`neoforge:none` n'existe pas en 26.2, le zinc de Create est coupé par un `remove_features` vide), et
les minerais du jeu n'y sont plus générés (85 minerais à retirer sur 9 chunks, contre 5 600 sans
lui). Le pétrole des chunks pour Create Diesel Generators n'a pas pu être essayé (mod absent
de la CI) : appel vérifié sur le bytecode du mod (statique, `ServerLevel, ChunkPos, int`).

### 2026-11-13 (suite 17) — PalaMod : les feuilles tombent, les Dancaroks apparaissent

**Demande** : « Vérifie l'apparition des dancarok et la dégradation rapides des feuilles des arbres
du palamod ». Le joueur a **Leaves Be Gone** ; sa carte de la Terre est déjà générée.

**Constat** : les feuilles de PalaMod étaient des blocs simples (comme l'original) : elles ne
tombaient jamais, et Leaves Be Gone ne traite que les vraies feuilles. Le Dancarok n'apparaissait
que par la règle vanilla (poids 2 parmi 46) : à la génération des chunks — il n'y en a pas sur une
carte faite d'avance — et par le tirage des créatures plafonné à une dizaine de bêtes, que les
moutons et les vaches remplissent.

**Fait** (`feat/feuilles-dancarok`) :
- `PalaLeavesBlock` : vraies feuilles ; distance par les 26 voisines (les patrons sont clairsemés :
  un cinquième de leurs feuilles ne touche rien par une face), bûches et feuilles de la même
  essence seulement ; défaut « distance 1 » pour les feuilles sauvegardées avant, qui tiennent
  jusqu'à ce qu'un voisin bouge ; particules de feuilles à la couleur de l'essence. Les pixels
  transparents des quatre textures ont une teinte sombre (rendu sans découpe).
- `PalaTreeFeature` pose les feuilles avec leur distance aux bûches posées ; bûches entamées
  dans `#minecraft:logs`.
- Tag de biomes `palamod:dancarok_spawns` (plaines, tournesols, prairie, savanes,
  `#c:is_savanna`), règle vanilla au poids 3, et `DancarokSpawner` : une fois par minute, une
  chance sur huit par joueur, un sauvage à 32-64 blocs hors de vue s'il n'y en a aucun à 128.

**Vérifié en jeu** (client de CI avec Leaves Be Gone et Puzzles Lib de Modrinth) : un jacaranda
posé (97 feuilles, toutes à distance 1 ou 2) ne perd rien en 10 s de ticks aléatoires ×333 ;
abattus, le jacaranda, un chêne et une colonne aux feuilles « d'avant » sont nus **en moins de
52 ticks** ; un bouquet « d'avant » sans tronc reste, puis tombe en entier quand on en casse une
feuille ; un bouquet posé à la main reste. Bûche de PalaMod : distance vanilla 0 (le tag
`#minecraft:logs` sert bien en 26.2). Plaine : 55 animaux et 1 Dancarok à la génération ; ce
sauvage retiré, `DancarokSpawner` en pose un au premier essai, à 46 blocs, sur l'herbe ; un second
essai est refusé (un sauvage à 128 blocs).

### 2026-11-13 (suite 16) — les villes des capitales retirées

**Demande** : « supprime les structures villes ». Choix du joueur : **retirer du mod seulement** ;
les villes déjà bâties dans son monde Earth24kChaosCubed restent telles quelles (les effacer aurait
voulu dire remettre ces chunks d'origine depuis la carte, et perdre ce qu'il y a construit).

**Fait** (`feat/sans-villes`) : `EarthCities`, `CityBuilder`, `CityLayouts`, `CityMonuments`,
`CityBlocks`, `data/dohy/earth/capitals.json` et `tools/earth_capitals.py` supprimés ; plus de
pose au chargement des chunks, plus de titre d'entrée de ville ; le message de capitale et la
carte « Capitale » de l'écran des pays ne nomment plus de ville. La capitale de faction, la
conquête et la carte du spawn ne changent pas. Les marques `earth_city` (chunks) et
`earth_cities` (monde) restent enregistrées, sans rien écrire, pour que les sauvegardes qui les
portent se chargent sans erreur ; elles disparaissent à la sauvegarde suivante.

### 2026-11-13 (suite 15) — refonte : nation, marché international, notation

**Demande** : « Une petite refonte du système de factions, de pays, et des notations, avec un
marché international qui varie en gros stock ». Précisée par quatre choix : la faction est une
nation ; un stock mondial par ressource ; vente possible mais plafonnée ; notation simplifiée, qui
compte les pays et le commerce, classe face aux nations, avec des récompenses revues.
Description : `docs/FACTION.md` (lot 22j) ; chiffrage : `docs/BALANCE.md` § 6 vicies.

**Fait** (`feat/nation-marche`, sur `feat/terre-capitales`) :
- `NationRank` : six rangs selon les pays tenus, qui fixent la taxe du marché (15 à 6 %).
- Page de faction : rang, section « Territoire » (capitale, pays, cible, voisins) sur une carte
  de la Terre, carte « Marché international ».
- `faction/market/WorldMarket` et `WorldMarketMenu` : 25 ressources, cours = (R / stock)^0,6
  borné à [0,4 ; 2], paiement à l'intégrale, saturation et pénurie, achat à 2,8 × le cours
  (plancher 2,6 × la base), gros lots des nations toutes les dix minutes de jeu, retour à la
  référence, dépêches ; `/f marche`.
- `FactionNotation` / `NotationConfig` / `data/notations.json` : cinq critères (activité 20,
  économie 20, commerce 15, militaire 20, territoire 25), classement face aux douze nations (note
  tirée de la graine et de la semaine, plus leur part de pays gardés), récompenses : bourse,
  points de conquête, lots du podium, claim de la première place.

**Vérifié en jeu** (client de CI) : rangs Faction → Cité → État et taxe 15 → 14 → 12 % ; vente de
1 000 fer puis 1 000 = 24 648 $, le devis des 2 000 d'un coup ; saturation à -60 %, le marché
refuse et le fer reste en main ; achat de diamants jusqu'à la pénurie (+99 %), refus ensuite ;
boucle achat-revente : -453 304 $ ; fer bradé : achat 39 $ pièce contre une vente à 30 $ au plus ;
quatre lots de nations avec leurs dépêches ; notation : commerce 13,5 / 15 pour 1,09 M $,
territoire 6,8 / 25, 13e sur 13 ; lundi forcé : bourse 50 750 $, 102 points sur la Belgique,
volume remis à zéro ; les trois écrans.

**Non vérifié** : un vrai lundi (la bascule est forcée dans l'essai), le retour des stocks sur
plusieurs heures de jeu, et l'équilibre sur une vraie partie — le marché ajoute un débouché à la
boutique, à surveiller.

### 2026-11-13 (suite 14) — la Terre : les capitales

**Demande** : « Peux-tu pour les capitales ajouter une reproduction des villes ? », précisée en
trois temps : des villes jouables agrandies dans les capitales des pays ; « rends-les
réalistes » — le plan de la vraie ville pour les grandes ; « et bâtiment réaliste et grand ».
Description : `docs/CONTENU_DOHY.md`, « Les capitales ».

**Fait** (`feat/terre-capitales`, sur `feat/terre-pays`) :
- `tools/earth_capitals.py` → `capitals.json` : 215 capitales (Natural Earth), taille selon la
  population, style selon la région, décalage des villes qui se chevauchent.
- `CityBuilder` : le plan d'un chunk (terrain à plat, rues, îlots, parcs, immeubles, tours,
  maisons, fleuve et ponts, avenue) ; grands immeubles à cheval sur deux îlots ; six styles.
- `CityLayouts` : le plan des onze grandes capitales (fleuve, parcs, avenue, monuments placés).
- `CityMonuments` : 11 grands monuments, 11 petits (cathédrale, arc, palais, église à coupole,
  grande roue, donjon, temple, temple rond, tour de télévision, enceinte, colonne), un palais
  national pour les autres capitales.
- `EarthCities` : file des chunks, 4 000 blocs par tick, hauteur de ville gardée sur le monde,
  monument quand sa place est posée et chargée ; jamais de chargement forcé de chunk.
- Titre d'entrée, hologrammes des noms, nom de la ville pour la capitale de faction.

**Vérifié** : hors jeu, au prévisualiseur (`/home/claude/city_preview`), chaque monument, les six
styles, le fleuve et ses ponts ; en jeu (client de CI, grille Earth24k sur un monde ordinaire) :
Paris, Londres, Le Caire, Tokyo, Rome et Luxembourg posées entières en 4 à 8 secondes chacune,
monuments et noms compris, aucun bloc inconnu en 26.2, titre d'entrée. Un premier essai figeait
le serveur près de cinq minutes au Caire (relief) : la pose ne force plus aucun chargement de
chunk et dégage de haut en bas, et le même essai passe en 8 secondes.

**Non vérifié** : la carte Earth24k elle-même (relief réel, villes en montagne), et les sept
autres grandes capitales en jeu depuis les grands immeubles (elles l'étaient avant). Sur l'eau,
un grand immeuble peut n'avoir que sa moitié si l'autre îlot est noyé.

### 2026-11-12 (suite 13) — la Terre : les pays à prendre

**Demande** : « Et ensuite le 1. Pays à prendre » — ta faction revendique un pays entier de la
grille, avec une capitale ; le message de frontière dit qui le tient ; les autres pays sont tenus
par des nations rivales gérées par le jeu, prises par les events de faction ; réutiliser les
claims et la notation. (L'idée 2, des revenus de territoire, n'a pas été retenue.)
Description : `docs/CONTENU_DOHY.md`, « Les pays à prendre » ; `docs/FACTION.md`, lot 22i ;
chiffrage : `docs/BALANCE.md` § 6 novodecies.

**Fait** (`feat/terre-pays`) :
- `earth/EarthNations` : l'atlas de la partie (taille de chaque pays, son cœur, ses voisins à
  300 blocs environ, sur une grille de carreaux de 64 blocs), et le partage du monde entre les
  douze factions fictives des events par régions (k-moyennes sur le globe, tirées de la graine).
  Seuls les pays pris par des joueurs sont sauvegardés (`earth_nations`, sur le monde de la
  carte) ; la capitale, la cible et les points d'une faction vont sur le joueur (`earth_nation`).
- Capitale dans un claim ; première cible : son pays ; ensuite les voisins de ses terres, et les
  îles sans voisin. Au départ d'un event, la nation qui tient une cible est mise parmi les trois
  rivaux (`EventRun.want`) ; à la fin, l'ELO gagné devant elle compte en points ; le pays tombe
  quand son prix est atteint (200 + 37 × √cases, arrondi).
- Un claim de plus par pays ; un pays vaut quatre claims dans le patrimoine de la notation.
- Frontières : « France — Aux mains d'Arcanum · ta cible (227 / 3 200) » ; accords « d'Arcanum »,
  « des Corbeaux ». Carte du monde du spawn : pays pris en or, capitales en rouge
  (`SpawnApi.paintNations`), et le détenteur dans la barre d'action.
- Onglet « Pays » de la faction (`NationMenu`) et `/f pays` (`capitale`, `viser`, `renoncer`,
  `abandonner`, `liste`, `donner` pour les opérateurs). Dissoudre la faction rend ses pays.

**Vérifié en jeu** (client de CI, grille Earth24k sur un monde ordinaire) : partage en douze
nations (Arcanum : l'Europe, 49 pays ; Kraken : Canada et États-Unis ; Nordheim : Russie et Asie
centrale…), prix (France 3 200, Belgique 900, Russie 22 000) ; capitale refusée hors claim, posée
à Paris ; viser la Belgique refusé tant qu'on n'a pas de pays ; à chaque event, Arcanum présente
parmi les rivaux ; Boss gagné devant elle : +227 points ; Boss perdu derrière elle : rien ; KOTH
gagné : la France tombe (titre, annonce), un claim de plus ; ensuite Japon refusé (trop loin),
Bermudes (île isolée) et Belgique acceptés, 54 pays à viser, les plus proches d'abord ; carte du
spawn : la France en or, Paris en rouge ; dissolution : registre vide, carte repeinte. Les events
de l'essai sont lancés pour de bon, et leurs scores posés à la main avant la fin.

**Non vérifié** : un event joué jusqu'au bout avec la cible (la fin est la même que dans l'essai),
et la carte Earth24k elle-même (le CI n'a que la grille).

### 2026-11-12 (suite 12) — la Terre : zones irradiées et bases de lancement

**Demande** : « Avec NTM NEXT et hbmspace : 6. Zones irradiées réelles. Tchernobyl et Fukushima en
zone de radiation NTM, avec des minerais rares en récompense du risque. 7. Bases de lancement. Les
fusées de hbmspace ne décollent que depuis Baïkonour, Cap Canaveral, Kourou ou Jiuquan. » Puis :
« Et ensuite le 1. Pays à prendre » (lot suivant).
Description : `docs/CONTENU_DOHY.md`, sections « Les zones irradiées » et « Les bases de
lancement » ; chiffrage : `docs/BALANCE.md` § 6 octodecies.

**Lu dans les sources** (sans rien exécuter) : NTM NEXT 1.0.3 (`Warfactory-Official/ntm-next`)
irradie les biomes par des données (`hbm:biome_radiation` : `targets`, `rad_per_second`,
`wet_multiplier`) et les blocs par d'autres (`hbm:rad_source`) ; 1 000 RAD tuent. NTM NEXT: Space
(`Warfactory-Official/ntm-next-space`, mod `hbmspace`) fait décoller ses fusées d'un pas de tir
(`hbmspace:launch_pad_rocket`) ; ses autres lanceurs sont `transporter_rocket`,
`orbital_station_launcher` et `dyson_launcher`. Aucun des deux n'est une dépendance de compilation.

**Fait** (`feat/terre-lieux`, sur `feat/terre`) :
- `earth/EarthSites` : six lieux par latitude et longitude (`sites.json`, écrit par
  `tools/earth_deposits.py`), servis à l'arrivée des chunks (marque `earth_sites`).
- Zones : biomes `dohy:exclusion_zone` et `dohy:exclusion_core` posés sur les terres, irradiés par
  NTM NEXT via `data/dohy/hbm/biome_radiation/` (0,5 et 4 RAD/s) ; au cœur, la terre et les arbres
  morts de NTM NEXT ; au réacteur, une enceinte éventrée et un tas de corium ; cinq gisements de
  Sellafield réservés aux zones (champ `sites` des gisements).
- Bases : une aire de béton par base, et la pose des quatre lanceurs refusée ailleurs sur la carte,
  avec la base la plus proche, sa distance et sa direction.
- Un titre à l'entrée d'un lieu.
- Les centres sont ceux du chunk sec le plus proche, dans le bon pays, sur Earth24k : la centrale
  de Tchernobyl tombait dans le chunk de la Pripiat (et un premier choix en Biélorussie), Cap
  Canaveral et Fukushima en mer.

**Vérifié en jeu** (client de CI, sans NTM NEXT ni Space : ni l'un ni l'autre n'a de jar publié
que la CI puisse prendre) : les deux biomes enregistrés et posés (cœur à 10 blocs, zone à 25,
plaine hors zone, mer intacte), herbe rousse au cœur ; aire de Baïkonour (152 bétons, cercle
jaune, étiquette) ; titres d'entrée ; refus de pose loin d'une base avec « La plus proche :
Baikonour, a 4188 blocs a l'est », et pose acceptée sur l'aire — essayés avec une pierre d'aimant
inscrite comme lanceur, pour le test seulement.

**Non vérifié** : tout ce qui demande NTM NEXT — la dose réelle dans les biomes, la terre morte,
le corium, les minerais de Sellafield — et Space (les vrais blocs de lanceur, et le jar 1.0.0 du
joueur, quand les sources lues sont en 0.1.0).

### 2026-11-12 (suite 11) — la Terre : pays, saut, gisements, carte du monde au spawn

**Demande** : sur la carte `Earth24kChaosCubed` (WorldPainter) de l'instance du joueur, une
génération « entre l'esprit de NationsGlory et une Terre réaliste », le joueur qui apparaît au
spawn de DoHy, une carte du monde au spawn, un trou où sauter pour être téléporté au hasard sur
la carte, et un message quand on change de pays.
Description : `docs/CONTENU_DOHY.md`, section « La Terre » ; chiffrage : `docs/BALANCE.md`
§ 6 octodecies.

**La carte, mesurée sur place** (lecture seule des régions, sur le PC du joueur) : 1 560 régions,
7,3 Go, jamais ouverte en jeu ; chunks à l'étape `liquid_carvers`. Un relevé terre/mer par chunk
(1 664 × 960) a été ajusté sur les côtes de Natural Earth : **projection de Miller, 68,6755 blocs
par degré, Greenwich en x = 1,33, équateur en z = 700,35**, 99,0 % de concordance hors
Antarctique (un décalage d'un demi-chunk fait déjà perdre 0,2 point). La carte n'a pas été copiée
hors du PC : seul ce relevé l'a été, pour le calage.

**Règles tenues** : la configuration de NationsGlory n'est pas publique et n'a pas été prise dans
le dossier du client ; la répartition suit les producteurs réels. La carte elle-même n'est pas
sur GitHub (7,3 Go, œuvre d'un tiers, dépôt public) : seul le datapack y est.

**Fait** (`feat/terre`) :
- `earth/` : `EarthData` (calage et grille des pays, lus du datapack avant le chargement des
  mondes), `EarthEvents` (titre au passage d'une frontière), `EarthJump` (saut en parachute sur
  une terre tirée au hasard), `EarthDeposits` (28 gisements posés à l'arrivée des chunks).
- Spawn version 6 : la dalle de la carte du monde et le puits (`SpawnBuilder.worldMap`), la
  barre d'action qui dit le pays sous les pieds. Sans carte de la Terre, le spawn ne change pas.
- `mixin/SerializableChunkDataMixin` : voir ci-dessous.
- Datapack de la carte sur la branche `datapack-terre` : calage, grille des 256 pays, retrait de
  l'or, du diamant, de l'émeraude, du lapis et de la redstone du jeu, interrupteur
  `overworld_ores` de NTM NEXT coupé.

**Le piège de la carte, trouvé en chemin.** En 26.2 (version de données 4903), l'étape
`liquid_carvers` n'existe plus : `ChunkStatus.byName` la rend `empty`, les correcteurs de données
ne touchent pas un chunk daté 4786, et le jeu **régénère par-dessus** le chunk dessiné. Essayé en
jeu sur un chunk complet recopié ailleurs à cette étape : biome changé (plaine → rivière), 53 %
de blocs identiques seulement. Avec le mixin (l'étape renommée `carvers` dans les données du
chunk, à l'entrée de `SerializableChunkData.parse` et `getChunkStatusFromTag`) : biome conservé,
97 % des hauteurs et 85 % des blocs identiques — le reste est la décoration, qui est justement ce
que l'étape demande. Un premier mixin sur `ChunkStatus.byName` n'avait rien changé : l'étape n'est
pas lue par cette méthode.

**Vérifié en jeu** (client de CI, monde ordinaire avec le jeu de données de la carte) :
- dix points du globe retrouvés dans le bon pays (Paris, Berlin, Almaty, Riyad, New York, Rio,
  Tokyo, Reykjavik ; haute mer au milieu de l'Atlantique) et aller-retour latitude/longitude ;
- spawn reconstruit en version 1006 avec la dalle (46 s), carte lisible depuis la plateforme,
  « Kazakhstan » dans la barre d'action en marchant dessus ;
- saut par le puits : lâché à 96 blocs au-dessus de la Russie, chute lente, posé sans dégât ;
- frontières : « Allemagne — Tu quittes : France », puis les eaux internationales ;
- retrait des minerais rares : aucun diamant, lapis ni or à Paris ; gisements : 1 010 diamants
  sur 81 chunks au Botswana, 380 émeraudes en Colombie, 1 326 lapis en Afghanistan.

**Non vérifié** : la carte elle-même en jeu (le PC du joueur était éteint) — son `level.dat`
WorldPainter, le temps de décoration de ses chunks, le rendu de ses côtes face à la grille ;
les 20 gisements de NTM NEXT et de Create (mods absents du client de CI : seule leur mise à
l'écart « minerai absent » a été vue) ; l'interrupteur `overworld_ores` de NTM NEXT (lu dans ses
sources, version 1.0.3, pas essayé) ; un monde sans datapack après ce lot (le spawn y repasse en
version 6, sans dalle).

**26.2, relevé** : `ChunkPos.toLong()` n'existe plus ; `SerializableChunkData.copyOf(level,
chunk).write()` rend les données d'un chunk ; `SimpleRegionStorage.write(ChunkPos, Supplier)` ;
les statuts sont `empty … carvers, features, initialize_light, light, spawn, full`.

### 2026-11-12 (suite 10) — le tracteur, ses outils, le silo et la pompe

**Demande** : « pour le tracteur et silo j'ai des fichiers et des assets à te fournir » (le
dossier du client NationsGlory), puis, la reprise de ces fichiers ayant été refusée : « d'accord,
mais cela doit être compatible avec NTM NEXT ».
Description : `docs/CONTENU_DOHY.md`, section « Le tracteur, ses outils, le silo et la pompe » ;
chiffrage : `docs/BALANCE.md` § 6 sexdecies.

**Règle tenue** : rien n'est repris du client NationsGlory — ni code, ni modèle, ni texture ; le
dossier n'a été ni ouvert ni copié. Le comportement suit le wiki public ; le modèle et la texture
sont décrits boîte par boîte dans `tools/tractor_model.py`, les icônes et les blocs dans
`tools/dohy_assets.py`.

**Fait** (`feat/tracteur-silo`) :
- **`Tractor`**, une entité conduite (même patron que le Dancarok : `PlayerRideableJumping`,
  `tickRidden`, `getRiddenInput`). Réservoir en unités, brûlé à la distance ; un outil à la fois,
  large de trois blocs, qui agit 2,4 blocs derrière : charrue, semoir, épandeur, moissonneuse.
  La moisson passe par `destroyBlock` au nom du conducteur, et `TractorEvents` (priorité la plus
  basse sur `BlockDropsEvent`) range le butin dans la trémie après que l'XP, les contrats et les
  collections l'ont lu.
- **Silo** (`SiloBlockEntity`) : 16 384 céréales. Une « vitrine » d'une demi-pile par céréale pour
  les entonnoirs et les mods, une réserve comptée derrière ; capacité d'objets, faces comme les
  machines.
- **Pompe** (`FuelPumpBlockEntity`) : une `FluidStacksResourceHandler` qui ne prend que les
  carburants de la table, offerte à la capacité de fluide (tuyaux de NTM NEXT), et qui remplit
  les tracteurs à portée.
- **NTM NEXT** : carburants `hbm:*` par la capacité de fluide des objets (bidons) et des blocs ;
  recettes de machine d'assemblage, recettes d'établi sous la condition inverse.
- Réglages : bloc `farming` et `tractor_level` de `data/agriculture.json`.
- **Trouvé en passant** : les céréales de DoHy et les cultures de PalaMod n'étaient pas dans le tag
  `minecraft:maintains_farmland`. Loin de l'eau, la terre labourée redevenait de la terre sous la
  culture, qui sautait. Elles y sont (`tools/palamod_assets.py`).

**Vérifié en jeu** (client de CI, un champ de 3 × 32 conduit au clavier, joueur en survie) :
- pose refusée au niveau 0 de Fermier, acceptée au niveau 12 ; le composant de carburant de
  l'objet est repris par l'entité, et rendu au démontage ;
- plein à la main : charbon (+250), seau de lave (+250, le seau vide reste en main), seau d'eau
  refusé ;
- pompe : par la capacité de fluide, l'eau est refusée (0 mB) et la lave acceptée ; un seau à la
  main la remplit ; elle verse ses 4 000 mB de lave dans le tracteur (+1 000 unités) ;
- labour : 96 cases sur 96, 79 unités pour 40 blocs ; semis : la semence hors climat (avoine en
  tempéré) est sautée, le maïs puis le blé sont semés ; épandage : une poudre d'os par pousse ;
- moisson : le grain dans la trémie, les semences rendues au semoir, **l'XP de Fermier créditée**
  (+140 pour 45 cultures) ; une pile reprise par l'écran ; la trémie vidée dans le silo à l'arrivée ;
- silo : entonnoir du dessus (le blé et le maïs entrent, la pierre reste), entonnoir du dessous
  vers un coffre, capacité d'objets (entrée par le côté, refus par le dessous et pour la pierre),
  achat d'un lot (16 maïs, 1 216 $ au cours du moment), prise d'une pile, « Tout déposer » ;
- touches : saut tenu six ticks → l'outil s'arrête ; le regard tourné à l'arrêt ne fait pas
  tourner le tracteur, en roulant il prend le cap ; la touche d'inventaire ouvre son écran ;
- démontage : un coup debout ne fait rien ; accroupi, tout est rendu.

**Non vérifié** : tout ce qui demande NTM NEXT installé (ses bidons, ses tuyaux sur la pompe, ses
convoyeurs sur le silo, ses sept recettes d'assemblage) — seules les capacités de NeoForge par
lesquelles il passe ont été essayées ; la marche arrière ; un silo plein ; la sauvegarde et le
rechargement d'un tracteur chargé ; plusieurs tracteurs près d'une même pompe.

**26.2, relevé** : `Mob.interact(Player, InteractionHand, Vec3)` ; `Options.hideGui` n'existe plus ;
`FluidUtil.interactWithFluidHandler` à quatre arguments est dépréciée, la variante à cinq prend un
`TransactionContext` ; `EntityType.Builder.passengerAttachments(Vec3...)` existe ; un codec de
record s'arrête à seize champs (`tractor_level` est donc à côté du bloc `farming`).

### 2026-11-12 (suite 9) — le spawn : le faire vivre, y arriver, finir le décor

**Demande** : « fais 1, 2 et 3 ensemble » — une raison de revenir au spawn (récompense, classements,
départs vers les donjons), y arriver à la première connexion et y réapparaître, et les éléments
des images d'inspiration laissés de côté.
Description : `docs/CONTENU_DOHY.md`, section « Le spawn » ; chiffrage : `docs/BALANCE.md` § 6 septdecies.

**Fait** (`feat/spawn-vie`, `SpawnBuilder` version 5) :
- **Récompense quotidienne** (`SpawnDaily`, borne RÉCOMPENSE) : une somme par jour réel, à prendre
  au spawn, sur une série de sept jours (42 000 $ la semaine au niveau global 0, même multiplicateur
  que les contrats). Pièce jointe `spawn_daily`. Pas de clé offerte : voir BALANCE.
- **Départs** (`SpawnPortals`, borne DONJONS) : les quatre donjons — la borne met la clé en main et
  ouvre le choix du palier —, les événements de faction, le profil RPG.
- **Tableaux de l'atrium** (`SpawnBoards`) : classement du concours de trixium et profil du joueur,
  recalculés toutes les cinq secondes. Le mod se jouant seul, un classement « des plus riches »
  n'aurait eu qu'une ligne : le concours a déjà ses 59 rivaux.
- **Arrivée** : un joueur de moins d'une minute de jeu est envoyé au spawn à sa connexion (pièce
  jointe `spawn_welcomed`) ; sans lit ni ancre, on y réapparaît après une mort. Les deux se coupent
  dans `data/spawn.json` (`SpawnConfig`).
- **Décor** : tapis rouge, deux bassins de brique, murs de tonneaux (qui ne s'ouvrent pas), salon à
  écran et deux bibliothèques au premier étage, jardinières et bancs au second.
- Deux pictogrammes de borne de plus (`tools/dohy_assets.py`).

**Choix à signaler** : pas de cartes aux murs (il faudrait des objets-cartes dans des cadres, donc
des entités à entretenir) — les deux tableaux tiennent ce rôle. Un joueur déjà en jeu avant ce lot
n'est pas envoyé au spawn à sa prochaine connexion : on ne l'arrache pas à sa base.

**Vérifié hors jeu** : accessibilité à pied de tous les lieux, y compris les deux nouvelles bornes,
la coursive arrière, la bibliothèque, le salon et le second étage.

**Vérifié en jeu** (client de la CI) : dans un monde neuf, le joueur arrive au spawn sans rien
faire ; la borne RÉCOMPENSE ouvre son écran, la prise crédite 2 000 $ (série 1), la seconde est
refusée ; la borne DONJONS ouvre les départs, un clic sur Talikus avec la clé dans l'inventaire met
la clé en main, range le bâton tenu à sa place et ouvre l'écran des paliers ; les deux tableaux
portent leur texte ; un tonneau ne s'ouvre pas ; une mort dans l'Overworld sans lit fait réapparaître
au spawn, et `/spawn` ramène dans l'Overworld.

**Non vérifié** : la série sur plusieurs jours réels (le changement de jour et la remise à zéro
sont écrits sur l'horloge des contrats, pas essayés) ; la réapparition avec un lit (elle ne doit
rien changer) ; un départ de donjon complet depuis le spawn puis le retour (seule l'ouverture de
l'écran des paliers est essayée) ; les événements de faction ouverts depuis la borne.

### 2026-11-12 (suite 8) — le spawn : l'intérieur d'après les inspirations

**Demande** : dix images « en inspirations » — les intérieurs d'un bâtiment brutaliste (atrium au
sol de brique avec jardinières, plafonds à caissons, galeries à mains courantes de bois, grands
escaliers à limons pleins, murs verts, verrière, plantes en pot) et une vue extérieure avec un
tablier devant l'entrée. Elles servent d'inspiration : rien n'est recopié bloc à bloc.

**Fait** (même branche `feat/spawn-silhouette`, `SpawnBuilder` version 4) :
- Atrium : sol de brique quadrillé de pierre, jardin central (massif, quatre jardinières, deux
  bancs), deux murs verts de trente blocs de part et d'autre de la lame de lumière, verdure qui
  retombe des galeries.
- Finitions : murs intérieurs carrelés (andésite polie) à assises de pierre taillée, plafonds à
  caissons croisés tous les trois blocs, garde-corps de pierre à main courante d'épicéa, lanternes
  suspendues sous les linteaux des galeries, bancs de bois.
- Escaliers : sept marches de pierre taillée (vrais blocs d'escalier) entre deux limons pleins, à
  la place des demi-marches en dalles.
- Nef : colonnes de pierre taillée, une plante en pot au pied de chacune ; stèles carrelées.
- Dehors : l'allée s'évase en tablier devant le porche.

**Vérifié hors jeu** (outil de prévisualisation local, qui sait maintenant monter les blocs
d'escalier) : tous les lieux restent accessibles à pied sans sauter ; aucune étiquette dans un bloc.

**Vérifié en jeu** (client de la CI) : construction en 12 secondes, règles, ouverture d'une box,
borne SHOP, chute, retour ; reconstruction en 6 secondes, 22 textes flottants. Vues de la nef, de
l'atrium, de la verrière, d'un escalier et des galeries contrôlées.

**Non vérifié** : le rendu à grande distance d'affichage ; la reconstruction d'un vrai spawn d'une
version antérieure gardé dans une sauvegarde ; la tenue du feuillage dans la durée n'a été vue que
sur quelques minutes (il est posé « persistant », il ne devrait pas dépérir).

### 2026-11-12 (suite 7) — le spawn : une silhouette plus proche de la référence

**Demande** : « parfait, j'aimerais qu'il ressemble à celui de la photo ». Aucune nouvelle image
n'accompagnait le message : la référence est celle du lot précédent. Le bâtiment s'en rapproche par
ses traits d'ensemble ; il reste un dessin fait ici, pas une copie bloc à bloc (la photo, petite,
ne le permettrait d'ailleurs pas).

**Fait** (`feat/spawn-silhouette`, `SpawnBuilder` version 3) :
- Masse haute : deux épaules basses et des contreforts en pente sur les flancs et à l'arrière.
- Gradins : un corbeau sous chaque débord, une couvertine sur chaque garde-corps, davantage de
  bandeaux de pierre taillée.
- Socle : deux bandes plantées en marches, tenues par des murs courbes (une seule derrière, pour
  laisser passer devant les cheminées et les cuves).
- Pylône à pilastres ; le signe « D » passe sur une dalle debout au bout des passerelles.
- Parvis : terre, herbe, épicéas et lampadaires à la place de la dalle de béton ; allée dallée.

**Vérifié hors jeu** (outil de prévisualisation local) : sans sauter, on atteint toujours les six
box, les sept bornes, les galeries, les terrasses, l'anneau, les passerelles, le toit du troisième
gradin, le belvédère et l'arrière du parvis ; aucune étiquette dans un bloc.

**Vérifié en jeu** (client de la CI) : construction en 14 secondes, règles, ouverture d'une box,
borne SHOP, chute, retour au bloc de départ ; reconstruction depuis une version marquée ancienne en
6 secondes, restes effacés, 22 textes flottants. Vues extérieures contrôlées.

**Non vérifié** : le rendu à grande distance d'affichage ; la reconstruction d'un vrai spawn de
version 1 ou 2 gardé dans une sauvegarde.

### 2026-11-12 (suite 6) — le spawn : un bâtiment brutaliste

**Demande** : après avoir vu le spawn, le joueur a joint l'image d'un bâtiment brutaliste (des
terrasses courbes empilées, une masse haute à contreforts, un anneau sur pilotis, un pylône et ses
galeries) : « j'aimerais plutôt un bâtiment comme ça avec un intérieur brutaliste ». L'image donne
le caractère ; le bâtiment est dessiné ici, il n'en est pas la copie.
Description : `docs/CONTENU_DOHY.md`, section « Le spawn ».

**Fait** (`feat/spawn-batiment`) :
- `SpawnBuilder`, version 2 : le hall ouvert, la tour et les pavillons sont remplacés par un seul
  bâtiment — trois gradins à bouts arrondis, masse haute à ailettes, anneau sur pilotis, pylône et
  trois passerelles ; dedans, nef, atrium de 46 blocs sous verrière avec les six box dans des
  niches, deux travées de bornes, deux étages de galeries, escaliers, terrasses. Parvis ovale,
  jardinières, épicéas ; les cheminées passent derrière. 313 000 blocs.
- `SpawnApi` : un spawn d'une version passée est vidé avant d'être rebâti — tout le volume où une
  version a pu bâtir, pitons compris (six tranches de x par tick).

**Vérifié hors jeu** (un outil de prévisualisation local, non versionné, qui compile `SpawnBuilder`
contre des classes factices) : sans sauter, on atteint depuis la plateforme les six box, les sept
bornes, les deux galeries, les trois terrasses, l'anneau, les trois passerelles et le belvédère du
pylône ; aucune étiquette n'est dans un bloc.

**Vérifié en jeu** (client de la CI) : construction en 14 secondes ; casse et dégâts refusés ;
22 textes flottants, 6 coffres de box ; une box ouverte avec sa clé ; la borne SHOP ouvre le shop ;
une chute ramène sur la plateforme ; `/spawn` ramène au bloc de départ. **Reconstruction** : avec
la version remise à 1 et cinq blocs étrangers posés (dont un à l'emplacement d'un ancien piton et
un dans l'atrium), le spawn est vidé puis rebâti en 5 secondes, les cinq blocs ont disparu, les box
et les bornes sont en place, 22 textes flottants (pas de double). Vues de jour et de nuit contrôlées.

**Non vérifié** : le rendu à grande distance d'affichage ; la reconstruction d'un vrai spawn de
version 1 gardé dans une sauvegarde (l'essai part d'un spawn de version 2 marqué 1).

### 2026-11-12 (suite 5) — le spawn

**Demande** : « une dimension "spawn" comme sur paladium et nationglory, où on peut ouvrir des
box, sur un thème brutaliste, industriel ». Le joueur a joint une image de spawn de serveur comme
référence d'ambiance (un complexe sur des pitons au-dessus des nuages, une plateforme ronde, un
pont, des mâts) : elle n'est pas recopiée, le lieu est dessiné ici dans le thème demandé.
Description : `docs/CONTENU_DOHY.md`, section « Le spawn ».

**Fait** (`feat/spawn`) :
- Dimension `dohy:spawn` (vide, ciel et horloge de l'Overworld, nuages à 168) ; `/spawn` et carte
  « Spawn » du menu pour y aller et en revenir (`ReturnPoint`, pièce jointe `spawn_return`).
- `SpawnBuilder` : le lieu est calculé (289 000 blocs), posé par tranches de 5 000 par tick ; sa
  version est gardée sur la dimension (`spawn_built`).
- Hall des six box, bornes (`dohy:terminal`) du shop, de la boutique, du marché, de la banque, des
  contrats, du musée et du retour, étiquettes flottantes (`dohy:hologram`).
- Règles : ni casse ni pose hors créatif, aucun dégât, retour sur la plateforme après une chute.

**Vérifié en jeu** (client de la CI) : construction en 9 secondes à la première visite, arrivée sur
la plateforme ; casser et poser refusés en survie ; 8 points de dégâts sans effet ; 21 textes
flottants et 6 coffres de box présents ; une box d'améthyste ouverte avec sa clé (lot donné, clé
prise) ; la borne SHOP ouvre le shop ; une chute dans le vide ramène sur la plateforme ; `/spawn`
ramène au bloc de départ ; le second aller est immédiat. Vues de jour et de nuit contrôlées.

**Non vérifié** : le rendu à grande distance d'affichage (le client de test voit à une centaine
de blocs) ; une reconstruction après changement de version (le vidage de l'ancien volume est écrit,
pas essayé).

### 2026-11-12 (suite 4) — les machines de l'agriculture en automates, contrats et pain d'épices

**Demande** : « enchaîne 1 et 2 » — automatiser le moulin et la machine à pain, puis brancher
l'agriculture sur les contrats et le métier de Fermier. Le joueur a validé en jeu le lot précédent
(« c'est testé, parfait ») ; le tracteur et le silo attendent des fichiers qu'il fournira.

**Fait** (`feat/agri-automatisation`) :
- `AgriMachineBlockEntity` devient un `WorldlyContainer` : six cases d'entrée, trois de sortie,
  entonnoir dessus ou de côté pour entrer, dessous pour sortir. Une sorte d'objet n'occupe qu'une
  case d'entrée. La capacité d'objets de NeoForge (`Capabilities.Item.BLOCK`,
  `WorldlyContainerWrapper`) est enregistrée pour les tuyaux des mods.
- Cycle automatique : une farine en 10 ticks, une fournée en 40, sans XP ni pain en plus. Le
  biogénérateur prend ses céréales par entonnoir. Une machine cassée rend son contenu.
- Écrans : section « Dans la machine » (charger, tout reprendre, reprendre une pile).
- Travail à la main : XP de Fermier (2 par farine, 8 à 40 par fournée).
- `ContractType.PRODUCE` et `AgriProduction` : trois contrats du jour, un de la semaine ; la
  production automatique compte pour celui qui a posé la machine. Dix commandes de trixium de plus
  (52).
- **Pain d'épices** : Fermier niveau 50, à la main seulement (`min_level` d'une fournée).

**Vérifié en jeu** (client de la CI) :
- chaîne entonnoir → moulin → entonnoir : 30 maïs, 12 blés, 6 kamuts → 10, 4 et 2 farines dans
  l'entonnoir du dessous, 1 920 FE dépensés, la terre reste dans l'entonnoir du dessus ;
- chaîne de la machine à pain : 16 farines de maïs → 16 pains, un pain de sorgho dont l'œuf est
  arrivé par la capacité d'objets ; le rayon de miel (pain d'épices) n'entre pas ; 5 200 FE ;
- capacité d'objets : terre et rayon de miel refusés, rien n'entre par dessous, rien ne sort par
  dessus ;
- biogénérateur : 20 avoines par entonnoir → 8 000 FE ;
- contrats : la chaîne seule les fait avancer (16 farines sur 120, 17 pains sur 60), l'XP de
  Fermier reste à 0 ; à la main +2 XP par farine, +12 par fournée ; contrat fini → 15 300 $ ;
- « Charger » ne prend qu'une pile par sorte et laisse la terre ; reprendre une pile, tout
  reprendre ; machine cassée → contenu au sol ;
- pain d'épices : refusé au niveau 45, cuit au niveau 50 (+20 XP), 6 → 10 PV, faim +8, effets ;
- commandes de trixium : 52, aucune répétition d'un jour sur l'autre sur 520 jours.

**Non vérifié** : un tuyau ou un convoyeur de NTM NEXT réel (la capacité d'objets répond comme à
un tuyau, mais NTM n'est pas sur la machine de test).

**À savoir** : le pool des commandes de trixium passant de 42 à 52, la commande du jour de la mise
à jour peut ne pas être celle affichée la veille.

### 2026-11-12 (suite 3) — l'agriculture, et le cours de l'heure

**Demandes** : « la partie agriculture (dans un onglet à part du palamod) », « aide toi du wiki de
nationglory », « ajoute aussi les pains et items à la vente, leurs prix doit varier chaque heure ».
Le joueur a aussi demandé, trois fois, les images du wiki : **refusé**, les sprites de
NationsGlory ne sont pas recopiés ; tout est dessiné par `tools/dohy_assets.py`. Description :
`docs/CONTENU_DOHY.md` ; chiffres : `docs/BALANCE.md` § 6 sexdecies.

**Fait** (`feat/agriculture`, posée sur `feat/serveur-trading`) :
- **Dix céréales** (neuf sous `dohy`, plus le blé) : six liées à un **climat** lu sur la température
  du biome (`Climate`), trois **OGM** qui poussent partout et ne rendent aucune semence.
- **Machine à OGM en recherche** (`GmoResearch`) : recette secrète de 9 objets en quantités
  précises, tirée du monde, du joueur et de la souche ; un essai consomme tout ; raté, il donne la
  part juste et un indice ; la recette mute après 2 000 graines. Carnet attaché au joueur
  (`AgriData`, attachement `agriculture`).
- **Moulin, machine à pain, biogénérateur** (`AgriMachineBlock`) : énergie FE de NeoForge comme le
  serveur de trading, recettes NTM NEXT sous condition et recettes d'établi sinon.
- **Dix pains** (`Bread`) et quatre effets neufs (`AgriEffects`) : vol de vigueur, dernier rempart,
  immunité, flair du mineur.
- **Élevage** : la ration passe à 4 céréales par bête (anti-arbitrage).
- **Cours de l'heure** (`economy/market/HourlyCourse`, bloc `course` de `economy.json`) : facteur
  0,7 à 1,4 par objet et par heure réelle, à la vente et à l'achat, branché dans `DynamicPrice` (donc
  shop, `/worth`, coffre de vente, autosell). Nouvelle catégorie de shop `AGRI`.
- `tools/check_agri_prices.py` en CI : aucune des 50 recettes ne paie en achetant ses ingrédients.

**Vérifié en jeu** (client de la CI, sans NTM) :
- climats : `fillbiome` plaine / taïga / jungle → tempéré / froid / chaud ; chaque semence ne se
  plante que dans son climat, les OGM partout ; une avoine posée de force en plaine reste à l'âge 0
  après 5 000 ticks et refuse la poudre d'os ;
- butin sur 300 cultures : naturelle 1,5 grain et 3,1 semences ; OGM 3 grains, **0 semence** ;
  jeune, la semence revient ; 1 500 herbes rendent une soixantaine de semences du climat local ;
- les dix pains : faim, soin (6 → 8, 9 ou 10 PV) et effets conformes ; le pain de kamut lève
  poison et wither, garde la rapidité, et un poison redonné tombe en une seconde ;
- dernier rempart : un coup de 500 laisse 2 PV, le suivant tue ; vol de vigueur II : 20 % des
  dégâts infligés ; flair : 300 pierres dans le même tick, un seul minerai ;
- élevage : 22 céréales, 5 bêtes nourries (20 céréales, 1 dose d'eau), rien sans eau ;
- machines : le biogénérateur chargé à la main alimente ses deux voisines à parts égales
  (3 400 FE chacune après 17 céréales) ; capacité d'énergie comme un
  câble (20 000 FE acceptés par le moulin, extraction refusée ; biogénérateur : insertion refusée,
  extraction simulée puis annulée sans effet, extraction validée) ; moulin : 63 maïs et 3 blés → 22 farines
  pour 2 640 FE ; les dix fournées, énergie comprise ; 200 fournées au niveau 45 → 821 pains ; à sec,
  refus ;
- recherche : refus sous le niveau 20, composition vide, objets manquants (rien de consommé) ;
  intrus → devinette ; quantité loin → fourchette ; dans la fourchette → « un peu plus » ; ingrédient
  manquant → devinette ; bonne composition → 8 graines, recette connue, +660 XP ; mutation à
  2 000 graines → nouvelle recette, carnet vidé ;
- cours : sur 20 000 heures, de 0,70 à 1,40, moyenne 1,05 ; vente de 64 pains et achat de 10 maïs
  au cours ; un grain d'OGM ne s'achète pas ; le blé (hors cours) n'est pas touché.

**Non vérifié** : un câble NTM NEXT réel et la machine d'assemblage (pas de NTM sur la machine de
test) ; le passage réel d'une heure à l'autre (le tirage est testé sur des heures simulées).

**Pas dans ce lot** : le tracteur et le silo de NationsGlory (le shop tient lieu de silo).

### 2026-11-12 (suite 2) — le serveur de trading, compatible NTM NEXT

**Demande** : « ajouter la machine "serveur" qui génère de l'argent passif et qui est compatible
avec NTM NEXT en terme de craft et d'alimentation en énergie ». Le joueur a donné le dépôt
(`Warfactory-Official/ntm-next`). Description : `docs/CONTENU_DOHY.md` ; chiffres :
`docs/BALANCE.md` § 6 quindecies.

**Fait** (`feat/serveur-trading`, posée sur `feat/notations`) :
- Premier contenu sous l'espace de noms **`dohy`** : `tech/DoHyTech`, onglet créatif
  « DoHy — machines », bloc `dohy:trading_server`.
- **Énergie** : capacité FE de NeoForge (`net.neoforged.neoforge.transfer.energy.EnergyHandler`,
  `Capabilities.Energy.BLOCK`), réception seule, journal de transaction (`SnapshotJournal`).
  C'est ce que les câbles de NTM NEXT alimentent (`PowerNetwork`, `ForeignEnergyEndpoint` :
  5 HE = 1 FE). Aucune dépendance à NTM.
- **Recette** : type `hbm:assembly_machine` sous condition `neoforge:mod_loaded`, et recette
  d'établi sous la condition inverse.
- **Production** : 9 000 $ par heure, caisse de 8 h, 4 serveurs à la fois par joueur.
- `tools/dohy_assets.py` : textures dessinées en code, modèles, états de bloc, butin, recettes,
  noms. `data/trading_server.json` pour les réglages.

**Vérifié en jeu** (client de la CI, sans NTM) : la capacité d'énergie répond sur le bloc ; une
insertion simulée puis abandonnée ne laisse rien ; l'extraction est refusée ; 100 000 FE acceptés ;
quatre serveurs produisent (17 $ en 5 s, soit 0,125 $ par tick), le cinquième attend puis prend la
place du premier une fois celui-ci cassé ; l'encaissement crédite le joueur ; casser le bloc rend
la caisse ; la recette d'établi est chargée, celle d'assemblage ignorée sans erreur.

**Non vérifié** : **tout ce qui demande NTM NEXT en jeu** — un câble NTM branché sur le serveur,
et la recette dans la machine d'assemblage. NTM NEXT ne publie pas de jar sur GitHub (artefacts
sur `repo.warfactory.co`, compilation avec un compilateur C++26), il n'a pas été monté dans la CI.
La compatibilité repose sur la lecture de ses sources (commit `05496ec4`, 2026-11-11).

**À savoir** :
- NeoForge 26.2 : l'énergie est dans `neoforge.transfer.energy` (`EnergyHandler`,
  `SimpleEnergyHandler`), avec des transactions (`Transaction.openRoot()`), plus dans
  `IEnergyStorage`. `event.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> …)`.
- Une recette de machine NTM tire son nom du dernier segment de son fichier : il doit rester
  unique entre mods (`ass.dohy_trading_server`).
- La liste des serveurs « en service » est en mémoire, vidée à l'arrêt du serveur de jeu.

### 2026-11-12 (suite) — la notation de la semaine

**Demande** : après lecture du wiki de NationsGlory, « la partie notation me convient ». Détail :
`docs/FACTION.md` § Notation de la semaine (22h), chiffres dans `docs/BALANCE.md` § 6 quaterdecies.

**Fait** (`feat/notations`) :
- `faction/FactionNotation` : une note sur 100 en sept critères (activité, économie, métiers,
  contrats, militaire, patrimoine, trésor), close le lundi à minuit **avant** la ligue (le critère
  militaire lit les victoires de la semaine, que la ligue remet à zéro). Bourse de 2 500 $ le
  point à la banque de faction.
- `faction/NotationConfig` + `data/notations.json` : plafonds, échelles, valeur du point, rangs.
- Compteurs dans les stats de la faction, préfixe `note:`. Branchements : `FactionWeekly`
  (bascule, jour joué), `FactionApi.withdraw` (retrait déduit des gains), `ContractApi.complete`
  et `TrixiumOrder.complete` (contrats).
- Écran : carte « Notation » sur la page de faction, page `FactionMenus.Notation`.

**Vérifié en jeu** (client de la CI) : faction neuve notée 2,3 ; une semaine remplie notée 47,6 ;
un retrait de 300 000 $ de la banque de faction ne change pas le critère d'économie, seulement le
trésor ; au lundi, note 48, bourse de 120 000 $ et récompense de ligue de 25 000 $ versées
(400 000 → 545 000 $), compteurs de semaine remis à zéro, critères d'état conservés ; trois lundis
d'absence donnent une seule notation ; un second passage le même lundi ne fait rien.

**Non vérifié** : la bascule un vrai lundi à minuit (les lundis sont simulés en reculant le
dernier lundi traité, comme pour la ligue). Les valeurs — 2 500 $ le point, les plafonds — sont
un premier réglage, pas le fruit d'une semaine de jeu.

**À savoir** :
- Sans faction, pas de notation : tout vit dans `FactionData`.
- Les gains de la semaine se lisent sur `lifetime_earned` du joueur, relevé au début de la
  semaine (`note:earned_start`, stocké +1 pour distinguer « pas encore relevé » de zéro).
- Les succès et les primes de niveau créditent le joueur : ils comptent dans l'économie de la
  semaine, comme une vente.

### 2026-11-12 — la commande de trixium, et le God Villager au bout de sept jours

**Demande** : « dans les contrats, ajoutes-en un pour obtenir du trixium, où l'item demandé change
tous les jours à midi », puis « ajoute aussi le God Villager issu des métiers ». Le God Villager
existait déjà (Mineur 95, une chance sur 500 000 000 par tick) ; question posée, réponse du
joueur : **le lier au contrat**. Chiffres et raisons : `docs/BALANCE.md` § 6 terdecies.

**Fait** (`feat/contrat-trixium`, posée sur `feat/musee-rares`) :
- `contract/TrixiumOrder` : une commande par jour, un objet à livrer, payée 16 trixium et 15 XP
  globale. Sa bascule est à **midi** (`trixium_order.reset_hour`), pas à 5 h.
- **L'objet du jour se calcule** depuis le numéro du jour : 42 commandes battues une fois par tour
  (`java.util.Random` semé par le numéro du tour), chacune passe une fois en 42 jours, jamais
  deux jours de suite. Rien n'est sauvegardé pour lui.
- **Livraison** depuis l'écran des contrats (section « COMMANDE DE TRIXIUM », seule carte
  cliquable de l'écran) : elle prend dans les 36 cases du sac ce que le joueur porte ; une
  livraison partielle compte.
- **Série** : sept commandes de suite appellent `GodVillager.spawn`, sans condition de niveau,
  puis la série repart de zéro. Un jour manqué remet la série à zéro et ne retire rien d'autre.
- `ContractData` : quatre champs (`order_period`, `order_progress`, `order_done_period`,
  `order_streak`). Les anciens fichiers retombent sur « rien livré, aucune série ».
- `data/contracts.json` → `trixium_order` : le pool, calibré à un quart d'heure de production.

**Vérifié en jeu** (client de la CI) : les 42 objets existent ; sur 420 jours, aucune répétition
d'un jour sur l'autre et dix tours complets ; livraison en deux fois (5 puis 5 diamants, 45 rendus
sur 50) ; 16 trixium et 15 XP à la livraison ; second clic refusé ; le lendemain, la commande
repart de zéro et la série tient ; à la septième, le God Villager apparaît et la série repart ;
après trois jours sans livrer, la série affichée est 0 et la livraison suivante la remet à 1.

**Non vérifié** : la bascule à midi **en temps réel** (les jours sont simulés en reculant l'état
sauvegardé ; l'heure elle-même vient de `RealClock`, déjà utilisé par les contrats). L'échange
avec le God Villager n'a pas été rejoué : il n'a pas changé.

**À savoir** :
- Un nom d'objet dans une carte doit faire partie d'**un seul composant** passé à
  `MenuView.text` : collé à du texte brut, il arrive en JSON à l'écran (vu à la première capture).
- Changer la taille du pool décale l'objet du jour : l'ordre dépend du nombre de commandes.
- 480 trixium par mois sans manquer un jour : entre la 10e et la 30e place du concours. À revoir
  si le concours devient trop facile.

### 2026-11-11 (suite) — le musée n'expose plus que des pièces rares

**Demande** : « mets à jour la partie Musée en faisant un tri, en ne mettant uniquement des items
rares, et pour NTM NEXT : Gadget, Fat Man, Little Boy et Heavy Mike ». Détail et tableau des
ailes : `docs/BALANCE.md` § 6 duodecies.

**Fait** (`feat/musee-rares`) :
- Le musée ne lit plus le catalogue de prix. `data/museum.json` nomme les pièces une à une, par
  aile (`wings`), avec pour chacune une ligne « où la trouver » (`hints`) affichée sur la vitrine
  vide. Cinq ailes — trésors du monde (6), trophées (6), reliques de PalaMod (11), donjons et
  pillage (9), NTM NEXT (4) — et le panneau mécène (l'œuf du dragon). 36 pièces, 37 avec l'œuf,
  contre les 176 objets du catalogue avant, 241 avec NTM.
- **NTM NEXT** : `hbm:nuke_gadget`, `hbm:nuke_boy`, `hbm:nuke_man`, `hbm:nuke_mike`. « Heavy
  Mike » est l'**Ivy Mike** de NTM (`block.hbm.nuke_mike`).
- Les sept bonus de l'ancien musée sont tous gardés, à la même valeur, répartis sur les nouvelles
  ailes. 100 XP globale par don au lieu de 25 : 3 700 XP au total, contre 4 400 avant.
- `MuseumConfig` : une aile garde la liste des pièces **qui existent dans ce jeu** (calculée au
  démarrage du serveur). Une aile sans pièce ne s'ouvre pas — c'est le cas de NTM NEXT sans NTM.
- En-tête de l'écran : pièces exposées, ailes complètes (hors mécène), XP par don. La « valeur »
  des dons disparaît, les reliques n'ayant pas de prix au catalogue.
- `tools/check_museum.py`, branché dans la CI.

**Vérifié en jeu** (client de la CI, sans NTM) : les 32 pièces vanilla et PalaMod existent toutes ;
le guichet prélève un exemplaire de chaque pièce manquante et rend le reste (3 étoiles du Nether
déposées, 2 rendues ; le blé, les diamants et les lingots de paladium rendus intacts) ; une pièce
déjà exposée n'est pas reprise ; l'aile des trophées complète donne +1,5 dégât (1,0 → 2,5), l'œuf
+2 chance ; un don de l'ancien musée (blé, pierre) reste enregistré et ne compte plus ; 12 dons
font 1 200 XP, niveau global 2.

**Non vérifié** : l'aile NTM NEXT en jeu — la machine de test n'a pas NTM. Les identifiants des
quatre bombes sont lus dans les sources de NTM NEXT 1.0.3 (`ModBlocks`, `Reg.blockItem` : l'objet
porte le nom du bloc), et le journal confirme le chemin « mod absent » (« pièces absentes de ce
jeu, ignorées : [hbm:nuke_gadget, …] », 4 ailes ouvertes sur 5). La bulle d'indice d'une vitrine
vide n'a pas été capturée (elle demande un survol).

**À savoir** :
- Un monde d'avant garde ses dons, mais **perd les bonus des anciennes ailes** : elles n'existent
  plus. Les dons qui sont encore des pièces (étoile du Nether, élytres…) comptent d'office.
- Une pièce nommée dans deux ailes n'est gardée que par la première (`Wing.resolved`).
- Une faute de frappe dans un identifiant `minecraft:` ou d'un autre mod ne se voit que dans le
  journal du serveur ; pour `palamod:`, `check_museum.py` l'arrête.
- Le 2026-11-11 vers 20 h 30 UTC, quatre runs GitHub Actions sont restés « queued » sans job
  (incident côté GitHub) : un nouveau push relance, l'ancien run reste orphelin.

### 2026-11-11 (suite) — les caisses regroupées dans les six boxes de PalaMod

**Demande** : « mettre à jour les loot box en regroupant toutes les caisses dans celles du
PalaMod ». Deux questions posées, deux choix du joueur : **six boxes, six butins** (plutôt que
trois boxes et trois habillages) et **la roulette gardée**. Détail : `docs/PALAMOD.md` § 33,
`docs/BALANCE.md` § 6 quater, octies et nonies.

**Fait** (`feat/boxes-six`) :
- `data/crates.json` : six tables sous l'identifiant de la box. Améthyste, paladium et endium
  reprennent telles quelles les caisses commune, rare et légendaire ; Saharia (1 M, le pillage),
  Halloween (20 M, la chasse et les withers) et Trixium (50 M, le voyage et l'endium) sont neuves.
  `tools/crate_values.py` : les six rendent de 84,5 à 90,3 % de leur prix. Les trois neuves ont
  reçu ensuite leurs lots **NTM NEXT** (« et les loot de NTM NEXT dans ces box ? ») : plutonium,
  lithium et pièces de machine pour Saharia, contrôleurs et moteurs en desh pour Halloween et
  Trixium, 14,5 % du tirage, répartis entre les classes.
- Six clés (`crate_key_<box>`), l'illustration de chaque box de PalaShop. `BoxKind.crate()` : une
  box ouvre sa propre table.
- Boutique : la section CAISSES disparaît ; BOXES (clés et roulette) et COFFRES À POSER.
- Sources de clés : donjons (Trixium à partir du palier 60), boss d'événement (Halloween, 1 %),
  **bases ennemies pillées (Saharia)**. Marché : les six clés au rayon.
- Un monde d'avant : alias de registre pour les trois anciens objets, conversion des compteurs,
  anciens noms compris par `/crate`.

**À savoir** :
- `DeferredRegister.addAlias(ancien, nouveau)` (sondé) : une pile sauvegardée sous l'ancien
  identifiant se recharge sous le nouveau. À poser dans un bloc `static` après les enregistrements.
- `tools/check_assets.py` lit les noms d'objets **en toutes lettres** dans `registerItem("...")` :
  un nom composé (`"crate_key_" + ...`) est lu « crate_key_ » et le contrôle échoue.
- Les non-empilables d'un lot (`count` > 1) partent pile par pile (`Crates.give`) : garder leur
  nombre petit. La pierre de capture, l'enderchest de poche et les pierres légendaires en sont.

### 2026-11-11 (suite) — des ruines à spawner dans la dimension du Mineur

**Demande** : « dans la dimension mineur, ajoute des structures communes dans lesquelles on peut
trouver des spawners (sorcière, squelettes, zombie, vaches, cochon, creeper) ». Détail :
`docs/PALAMOD.md` § 18 « Les ruines à spawner », `docs/BALANCE.md` § 6 undecies.

**Fait** (`feat/dimminer-spawners`, par-dessus `feat/bases-refonte`) : `MinerRuinFeature`, deux
features enregistrées (`palamod:miner_ruin` en surface, `palamod:miner_crypt` sous terre), six
créatures et six dessins ; placements et biome dans `tools/palamod_assets.py`. Les monstres nés d'un
spawner sont admis dans la dimension (`DimMinerEvents.onJoin`, `Mob.getSpawnType()`). Le marteau des
cavernes prend aussi la vache, le cochon et le creeper.

**À savoir** :
- Un spawner se configure en génération par `spawner.getSpawner().load(null, pos, TagValueInput…)`
  avec un `SpawnData` encodé par son codec (`NbtOps`) : c'est le seul moyen d'y mettre des
  `custom_spawn_rules`. `setEntityId` ne pose que la créature. Niveau `null` : sinon le spawner
  demande un `sendBlockUpdated` en pleine génération.
- En génération, ne pas appeler `BlockEntity.setChanged()` (charge le chunk, risque d'interblocage).
- La dimension a `monster_spawn_light_level` et `monster_spawn_block_light_limit` à 0 et une
  lumière du ciel de 4 : sans règles propres, un spawner de monstres n'y marche que sous un toit, et
  un spawner de bêtes jamais (il leur faut plus de 8 de lumière et de l'herbe).
- **`custom_spawn_rules` ne marche pas sous terre dans une dimension de nuit** : mesuré, 0 case
  valide sur 98 dans une salle fermée (lumière de bloc 3, ciel 0) avec des plages 0 à 15, contre 57
  à 95 sur 98 en surface. La règle lit la lumière du ciel assombrie (négative sous terre la nuit),
  et le codec refuse une plage hors de 0 à 15. D'où : règles propres en surface, règles ordinaires
  sous terre. Le symptôme : un spawner qui flambe, `Delay` bloqué à 0, et rien qui apparaît.
- Tester le marteau des cavernes demande Chasseur 25 (`JobGateEvents`) et une session de
  dimension active (`DimMinerEvents.onBreak`) : sans eux, `destroyBlock` rend faux sans autre signe
  qu'un message dans le chat.

### 2026-11-11 (suite) — la refonte des bases ennemies

**Demande** : « une grosse refonte des bases ennemies, plus complexes à raid, plus grosses et avec
des designs variés, la présence de machines comme boîtes à golem, cobblebreaker, grinder… des bases
de type farm, stockage, démonstration de puissance… », puis « un système de régénération du terrain
après la fin d'un raid, environ 5 min ». Détail : `docs/FACTION.md` § Bases ennemies (22g).

**Fait** (`feat/bases-refonte`, par-dessus `feat/marche`) :
- `EnemyBaseStyle` : cinq genres (citadelle, domaine agricole, entrepôt, usine, démonstration de
  force), choisis dans l'onglet ou par `/f base generer [1-3] [genre]` ; `EnemyBase.style`.
- `EnemyBaseTier` agrandi (61, 93 et 129 blocs de côté ; 40 000, 200 000 et 800 000 $) ;
  `EnemyBaseBuilder` réécrit : douve, enceinte à couches, porterie, quartiers tirés au sort, seconde
  enceinte, fossé, spikes, salle forte propre au genre. Dix-neuf modules de quartier ; grinders et
  boîtes à golem **montés** (vérifié par `GrinderStructure` / `GolemBoxStructure.isComplete`).
- `EnemyBaseRegen` : relevé du terrain avant le chantier (`<monde>/dohy/bases/<x>_<z>.terrain`),
  retour du terrain 5 minutes après le pillage ou l'abandon, par tranches de 15 ms par tick.
- Cinq tables de butin de quartier (`chests/enemy_vrac`, `_grenier`, `_entrepot`, `_atelier`,
  `_arsenal`), générées par `tools/palamod_assets.py`.

**À savoir** :
- Les blocs de la boîte à golem s'appellent `guardian_box_block` et `guardian_box_frame`, pas
  `golem_box` : `EnemyBaseBuilder.state(chemin, repli)` pose le repli sans rien dire si le nom est
  faux. Le relevé des captures (`ShotBridge.survey`) compte les structures complètes : s'y fier.
- `FactionApi.claimAt` rend `ClaimOwner.NONE` pour un chunk **non chargé** : après la livraison
  d'une base (tickets relâchés, joueur à 9 000 blocs), c'est normal, pas un claim perdu.
- Poser un bloc sans effet de bord : `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE |
  UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | UPDATE_SKIP_ON_PLACE` (sondé le 2026-11-11) — sans le
  troisième, remplacer un tonneau à table de butin tire son butin et le jette au sol.
- **Retour du terrain, vérifié** : comparaison bloc à bloc d'un carré de claims avant le chantier et
  après le retour (6,8 M de blocs) — 463 écarts, tous du monde qui vit (varech qui pousse, colonnes de
  bulles, lave qui coule) ; trois demandes réelles pillées puis effacées, balayage serveur sans aucun
  reste. **Une fois sur quatre** (capture 44, une seule passe à l'époque), l'image d'après montrait
  encore un chunk de la base ; non reproduit ensuite, et le serveur n'avait rien gardé les autres
  fois. Cause non trouvée. Depuis : seconde passe, et le journal donne les blocs reposés à chaque
  passe et les chunks revendiqués laissés (`Terrain de la base des … revenu : …`). Si un morceau
  reste affiché en jeu, regarder cette ligne, puis s'éloigner et revenir (le client recharge le chunk).
- Le harnais de captures abandonnait à 9 000 ticks : monté à 30 000 sur `tmp/ui-shots43`. Un chantier
  synchrone gèle le serveur plusieurs secondes : attendre sa fin (`ShotHarness.until`) avant la
  capture, sinon les images sont décalées d'une scène.

### 2026-11-11 (suite) — le marché

**Demande** : « un marché remixé pour le solo ». Le Market de Paladium est un hôtel des ventes
entre joueurs, dont le jar ne contient que l'écran : rien à porter, tout à concevoir. Choix du
joueur (quatre questions) : **hôtel des ventes simulé**, **raretés de PalaMod** et **clés et
boxes** à l'achat, **aucune vente**. Détail : `docs/PALAMOD.md` § 34, `docs/BALANCE.md` § 6 decies.

**Fait** (`feat/marche`, par-dessus `feat/cles-objets`) : `fr.atraxterra.dohy.auction` — douze
annonces par jour en quatre rayons, tirées de `data/auction.json` (`tools/market_offers.py`, 89
offres cotées), recalculées de la graine du monde et du jour ; achat, stock, renouvellement payé ;
`/market`, carte du menu, `/dohy reload`. Le paquet s'appelle `auction` parce que `economy.market`
est déjà la pression de vente du shop.

**À savoir** : un nom d'objet envoyé à un écran passe par `MenuView.text(Component)`, qui rend
une chaîne marquée que le client traduit. Lui coller du texte (« x3 ») la casse et le titre sort
vide : composer le `Component` d'abord. Et la branche de captures doit **patcher** `build.gradle`
(la ligne `programArguments`) au lieu de reprendre l'ancien fichier, sinon un fichier de données
ajouté par le lot n'est pas copié dans le jeu.

### 2026-11-11 (suite) — les clés de caisse en objets

**Demande** : après les boxes, le joueur a retenu deux suites sur quatre proposées : les clés en
objets, et un marché remixé pour le solo (lot suivant). Choix du joueur : **tout en objets**, le
compteur ne sert plus qu'à la conversion.

**Fait** (`feat/cles-objets`) : `CrateKeyItem`, `CrateKeys` (compter, prendre, donner,
convertir), trois icônes tirées de PalaShop ; boutique, roulette, boxes et `/crate` passent par
`CrateKeys`. Clés en butin : coffre de la note des donjons, boss d'événement (`BALANCE.md`
§ 6 nonies). Détail et vérifications : `docs/PALAMOD.md` § 33.

### 2026-11-11 (suite) — les boxes animées de la boutique

**Demande** : « que me conseilles-tu pour mon mod ? », puis « la boutique », puis, parmi quatre
pistes, « boxes animées », avec la consigne de retenir que le joueur joue **uniquement en solo**
(notée en tête de ce fichier). Le mod d'origine, PalaShop 7.8.0, était dans le client Paladium du
joueur. Détail : `docs/PALAMOD.md` § 33, `docs/BALANCE.md` § 6 octies.

**Fait** (`feat/boxes`) : `palamod/box` — six blocs à poser (améthyste, paladium, endium,
saharia, trixium, halloween) qui ouvrent les caisses de DoHy avec les mêmes clés. Le coffre animé
est une créature immobile tenue par la block entity et dessinée par le rendu des monstres de
donjon ; le lot se montre au-dessus (`Display.ItemDisplay`) au repère `open` de l'animation.
`Crates.pull` sépare le tirage de sa présentation (roulette ou box). Section BOXES de la boutique
(25 000 $). `tools/rpg_entities.py` prend le jar PalaShop en troisième argument ;
`tools/palamod_assets.py` réduit les vignettes en icônes.

**Rendu** : les rayons d'ouverture sont une boîte translucide dans une coque retournée. Dessinés
comme le reste (découpe, sans tri des faces), ils sortaient en blocs pleins. Les os lumineux d'un
modèle (`glow` dans `entity/index.json`) passent maintenant par une seconde passe, translucide, en
pleine lumière, sans les faces qui tournent le dos à la caméra (`RpgEntityModel.drawGlow`).
Seules les boxes en déclarent : les monstres de donjon ne changent pas.

**Vérifié en jeu** par captures, sans mod de rendu puis avec les Crankshaft et Sodium du joueur :
repos, quatre ouvertures, rayons de jour et de nuit, vrai clic du client, achat d'une clé, refus
sans clé, casse en pleine ouverture, section de la boutique. **Non vérifié** : les sons (pas de
carte son en CI), l'achat d'une box depuis la boutique, le rechargement d'un monde avec une box
posée — à regarder en premier si le joueur signale quelque chose.

**À savoir** : la machine de captures a ses paquets graphiques déjà installés ; l'étape `apt` du
workflow `ui-shots` a bloqué onze minutes un jour où les dépôts d'Ubuntu ne répondaient pas, et
n'est plus lancée que si `Xvfb` manque (branche de captures uniquement).

### 2026-11-11 (suite) — monstres de donjon invisibles chez le joueur : Crankshaft

**Signalement** : « pour les mobs des donjons rien n'a changé ». Le jar était le bon (65 modèles
chargés au journal du serveur). L'instance du joueur a **Crankshaft 1.6.0** (dérivé de Flywheel,
Vanillin embarqué), qui dessine les créatures vanilla par instances et n'émet jamais
`RenderLivingEvent.Pre`. Reproduit en CI en chargeant ce jar, puis corrigé par
`client/rpg/FlywheelCompat` (voir `docs/PALAMOD.md` § 29). Second signalement, « textures
buggées » : **Sodium** redessine chaque boîte de modèle avec sa propre découpe et ignore les
faces remplacées ; `RpgEntityModel` écrit maintenant ses sommets lui-même. Les deux défauts ont
été reproduits puis corrigés en CI avec les jars du joueur (`ci-mods/` sur la branche de
captures ; le harnais passe l'écran d'avertissements de NeoForge). **À retenir** : un rendu qui marche
en CI peut ne rien faire dans le modpack du joueur ; son instance Prism est lisible
(`mods`, `logs/latest.log`) et c'est là qu'il faut regarder d'abord.

### 2026-11-11 (suite) — le contrôleur de spawners

**Demande** : « ajoute la partie spawner controller et met à jour les recettes et mécaniques
associés en t'aidant des fichiers et du wiki ». Le mod d'origine, PalaSpawner 7.0.0, était dans
le client Paladium du joueur ; les plans des quatre structures et les recettes sont dans PalaMod
(`modules/aspawner`). Détail : `docs/PALAMOD.md` § 32 (et § 13 mis à jour), `docs/BALANCE.md`
§ 6 septies.

**Fait** (`feat/spawner-controller`) : les quatre obsidiennes infusées, le contrôleur (deux
modes, quatre cases d'amélioration), les quatre améliorations, leurs neuf recettes d'origine ;
`SpawnerBlueprint` (plans recopiés, quatre sens, diagnostic du bloc manquant) ; l'état `linked`
du spawner customisé. `MachineMenu.Buttons` et `MachineKitScreen.Button` : une machine peut
maintenant avoir un bouton.

**Vérifié en jeu** (captures) : structures I et IV reconnues, IV rétrogradée en III en cassant un
coin, refus et bloc manquant annoncé ; vagues toutes les 12 s (10,8 s avec une vitesse) ; mode
manuel (créature, compteur, coup : butin, 5 XP, XP de Chasseur, créature suivante) ; mode
automatique (butin réparti dans deux coffres) ; règles des cases (doublon et case verrouillée
refusés sans capacité) ; spawners liés puis déliés quand la structure tombe. Les gestes du client
aussi : coup et clic droit sur la créature, clic sur le contrôleur, bouton de mode, refus dit une
seule fois. Non vérifié : les verrous de métier des améliorations.

**À savoir pour la suite** : hors structure le spawner customisé garde son comportement vanilla
(demande antérieure du joueur) ; l'original n'y faisait rien. Le contrôleur ne travaille que
tronçon chargé. Les chiffres de production sont ceux de Paladium (`SpawnerTier`).

### 2026-11-11 (suite) — le clicker

**Demande** : « ajoute le clicker, aide-toi du wiki et des fichiers à ta disposition ». Le mod
d'origine, PalaClicker 6.1.2, était dans le client Paladium du joueur (`.paladium/mods`), avec ses
traductions (`config/translations/fr_FR.trad`) : règles relevées au bytecode, tables
reconstituées par `tools/clicker_data.py` (traductions, relevés de la communauté, wiki). Détail :
`docs/PALAMOD.md` § 31, `docs/BALANCE.md` § 6 sexies.

**Fait** (`feat/clicker`) : `palamod/clicker` (tables, données du joueur, règles, vue),
`ClickerScreen`, la carte « Clicker » du menu et `/clicker`. 35 bâtiments, 195 améliorations,
40 offres de Clic Shop.

**À savoir pour la suite** : la production ne tourne qu'en jeu, comme l'original — en solo, un
joueur occasionnel n'ira pas au bout des 35 bâtiments. Une production hors ligne serait un écart
de plus ; elle n'est pas faite.

### 2026-11-11 (suite) — donjons de Paladium, totem, optimisation

Cinq lots, une PR chacun, empilés dans cet ordre après les caisses (#79) et les corrections (#80) :

1. **Armure de voyage** (`feat/armure-voyage`, PALAMOD § 28) : bottes, jambières, plastron de saut,
   casques en slime et de plongée — la récompense du Fermier 55 est enfin distribuée.
2. **Modèles des donjons** (`feat/donjons-modeles`, § 29) : les 65 modèles Bedrock de
   `config/rpgentities` (monstres, mini-boss, boss), animations, couleurs par palier, sons. Le
   monstre reste vanilla ; le client dessine le modèle à sa place (`client/rpg`). Convertisseur :
   `tools/rpg_entities.py`, espace de noms `palarpg`.
3. **Totem de fertilité** (`feat/totem-fertilite`, § 5) : le mât sculpté de `ModelTotem` à la
   place du cube, en modèle de bloc ; le visage luit selon le carburant (`charge`), il regarde
   celui qui le pose (`facing`).
4. **Donjons embellis** (`feat/donjons-embellis`, § 30) : un thème de salle par donjon
   (`RoomTheme`), le coffre et les jarres de PalaRPG, les marchands Mammon et Mephisto.
5. **Optimisation** (`feat/optimisation`) — ce qui a été mesuré à la lecture du code, tous les
   gestionnaires de tick passés en revue (14 par joueur, 15 par serveur) :
   - `EndiumCaveVision` parcourait les block entities de 81 chunks toutes les 10 ticks **pour
     chaque joueur**, cave block ou non : elle ne cherche plus que s'il y en a un de chargé dans
     la zone (registre tenu par `EndiumCaveBlockEntity`). C'était le seul coût par joueur non
     borné du mod.
   - Le totem relit ses 225 blocs avant chaque poussée (10 s) et non chaque seconde.
   - Animations des monstres : images clés redondantes retirées à la conversion, 12 Mo → 8 Mo.
   - Sons des monstres : 54 Mo → 13 Mo (mono 44,1 kHz) ; le jar passe d'environ 62 Mo prévus à
     une vingtaine.
   - HUD : solde reformaté seulement quand il change ; finder actif cherché une fois par tick.
   Le reste est déjà cadencé (toutes les 20 à 1 200 ticks) ou sort tôt ; rien d'autre à reprendre.

**Vérifié en jeu** (captures CI) : les 65 modèles dans une galerie, un palier réel de Talikus et
de Nimbria, les quatre thèmes de salle, le coffre ouvert, les jarres, les deux marchands, le
totem à ses quatre niveaux de charge. **Non vérifié** : les sons (la CI n'a pas de carte son),
les animations d'attaque et de mort en mouvement (une capture est une image fixe), le gain réel
de l'optimisation sur un serveur chargé.

**Pièges relevés** : `Blocks.CHAIN` est devenu `IRON_CHAIN` ; un bloc de couleur se prend par
`Blocks.STAINED_GLASS.pick(DyeColor)` ; `RenderLivingEvent.Pre` annulé supprime aussi le nom
au-dessus de la tête (on le resoumet, la caméra se lit dans
`gameRenderer.gameRenderState().levelRenderState.cameraRenderState`) ; `ffmpeg` tire au hasard le
numéro de série Ogg sans `-fflags +bitexact` (même son, autre fichier à chaque génération).

### 2026-11-11 (suite) — corrections : recettes et bugs

**Demande** : « corrige des bugs, des recettes manquantes ». Méthode : journaux du client et du
serveur, outils de contrôle, puis rapprochement de chaque recette portée avec le relevé de
PalaMod (grille, rendement, et même objet moderne pour un même ingrédient d'origine).

**Recettes** (détail : `docs/PALAMOD.md` § 9) : compressés à 8 blocs autour d'un lingot, sans
retour ; endium compressé à 4 fragments et 5 blocs de paladium (il en coûtait 729 pépites) ;
vitre de coffre-fort en vitres (elle masquait le bloc de grotte) ; épée de capture avec ses quatre
sticks ; orbe de régénération au livre ; pépite ↔ 9 fragments d'endium ; lingot → 9 particules
(sauf fer et or) ; grès → sable, obsidienne renforcée → obsidienne. Auto-test sur serveur dédié :
346 recettes sur 346 fonctionnelles (plus les 3 spéciales), palamachine 39/39, grinder 19/19,
chaudron 4/4.

**Bugs** :
- la texture de la machine à croquettes (64 × 46) coupait les mipmaps de **tout** l'atlas des
  blocs (« dropping miplevel from 4 to 1 ») : allongée à 64 × 64, UV recalculés ;
- récompenses de métier jamais distribuées : le bonbon de métier devient 5 bouteilles d'XP du
  métier (niveau 95, les quatre métiers), la défense un bloc d'os (Chasseur, 80) ;
- en solo, changer de monde sans quitter le jeu gardait les messages de caisse en attente et
  les chantiers de base ennemie de l'ancien monde (une demande restait « en cours ») ;
- le fichier de licence de la police (`montserrat_OFL.txt`) avait une majuscule refusée par le
  jeu.

### 2026-11-11 (suite) — caisses : butin PalaMod et NTM, et une roulette

**Fait** : les trois caisses gardent leurs prix (100 k, 5 M, 150 M) et reçoivent un butin PalaMod
(paladium, paladium vert, trixium, findium, endium, panoplies et outils enchantés, pierres
légendaires, gant complet) et NTM NEXT (lingots par tag, pièces de machine), en quatre classes :
commun, rare, épique, légendaire. Une entrée accepte un tag (`c:ingots/lead`) et un lot de plusieurs
objets (`items`) ; une entrée dont un objet manque sort du tirage. Chaque palier rend 80 à 95 % de
son prix en valeur boutique, avec et sans NTM : `tools/crate_values.py`. L'ouverture affiche une
roulette (`CrateScreen`) qui s'arrête sur le lot tiré par le serveur, couleur et son selon la
classe ; le message du gain attend la fin, et un lot épique ou légendaire est annoncé à tous.
Vérifié : avec NTM, les 55 entrées sont tirables ; sans, 41. Détail dans `docs/BALANCE.md`
(§ 6 quater) et `docs/INTERFACES.md` (lot 6).

### 2026-11-11 (suite) — inventaire : les menus à portée de clic

**Fait** : sur la maquette fournie, l'inventaire du joueur passe au kit (`KitInventoryScreen`) et
ouvre les menus sans commande : menu principal (bouton rouge), shop, contrats, banque, faction,
métiers (colonne de droite), concours Trixium (à la place du Lucky Pass). Le créatif garde son
inventaire. Le bouton « Profil RPG » de l'ancien inventaire est retiré, le profil reste au menu
principal et à `/profil`. Détail dans `docs/INTERFACES.md` (lot 5).

### 2026-11-11 (suite) — métiers : les bonus deviennent des primes

**Fait** : sur demande, les 80 perks des métiers (attributs, double drop, XP, récolte, butin)
sont remplacés par une **prime de palier** tous les 5 niveaux : deux fois l'argent du niveau, en
plus de cet argent. 8,4 M par métier maxé. L'onglet « Bonus » de la fiche devient « Primes » et
liste les vingt paliers, versés ou à venir. `JobPerks` et `JobEffects` sont retirés ; la chance
de double drop du musée reste.

**Rattrapage** : le dernier palier payé est noté par métier (`JobData`). À la première
connexion, un joueur reçoit d'un coup les primes des paliers qu'il avait déjà franchis ; aucune
ne peut être payée deux fois.

### 2026-11-11 — NTM NEXT : minerais et pièces de machine

**Fait** : le shop NTM passe de 223 items à 65, sur demande. Deux familles : les minerais (dix
matières brutes à la vente, leurs lingots à l'achat) et 45 pièces de machine à l'achat
(plaques, fils, bobines, tuyaux, boulons, moteurs, circuits jusqu'à l'unité de contrôle). La
rareté suit la cote *Difficulty of Production* du wiki de NTM, sur huit crans qui gardent les
quatre anciens prix aux crans pairs. Les minerais se rattachent par sous-tag exact, plus par
mot-clé. Le prix d'une pièce est 115 % du coût de ses ingrédients par la recette la moins chère,
calculé par `tools/gen_ntm_prices.py` sur les recettes du mod.

**Corrigé au passage** : la matière brute de schrabidium se vendait 51 203 l'unité, alors que son
minerai ne naît que d'une explosion nucléaire sur de l'uranium. Elle n'est plus au shop.

**Cotes choisies faute de wiki** : titane (Easy), cuivre rouge, carbone (Easy), silicium
(Easy-Medium), caoutchouc (Medium). Les premières à revoir.

### 2026-11-10 (suite) — interfaces, lot 2

**Fait** : une vue de menu générique (`MenuView` / `MenuScreen` : titre, cartouche de chiffres,
sections de cartes en grille, lignes ou vignettes) et sept contrôleurs qui reprennent les règles
des menus en coffre, supprimés : menu principal, banque, contrats, boutique, améliorations,
collections (catégories puis vignettes, prestige au clic droit), musée (ailes puis vitrines).

**Vérifié** : les neuf écrans capturés en jeu, ouverts par le vrai serveur intégré (solde et
épargne crédités, catalogue et collections réels) ; captures envoyées au joueur.

### 2026-11-10 — interfaces, lot 1

**Demande du joueur** : refonte complète des interfaces au style de Paladium, sur ses captures
(métiers, Admin Shop, autocrafter, crusher, cobblebreaker, grinder). Choix : le kit dans DoHy
(pas de mod séparé), les quêtes quotidiennes = les contrats du jour, quatre lots validés sur
captures. Les liens envoyés n'étaient pas lisibles d'ici ; les captures les ont remplacés.

**Fait** : le kit (`client/ui`), les vues et le transport (`ui/view`), les contrôleurs du shop et
des métiers, trois écrans. Les polices et images viennent des jars Paladium-UI et PalaJobs
(`tools/ui_assets.py`).

**Vérifié** : un vrai client lancé sous Xvfb sur GitHub, dans un monde du serveur dédié, ouvre les
écrans par le serveur intégré et en prend des captures ; le joueur a validé les maquettes
(« C'est parfait »), puis les écrans branchés au serveur (catalogue réel, fiche du Mineur).

**Appris (26.2)** : l'écran courant est `minecraft.gui.screen()` / `gui.setScreen()`, la cible de
rendu `gameRenderer.mainRenderTarget()` ; les composants d'objet ne sont liés qu'une fois un monde
chargé (« Components not bound yet » à l'écran titre) ; les polices TrueType se déclarent en
`"type": "ttf"` et s'utilisent par `Style.withFont(new FontDescription.Resource(id))` ; le client
demande une sauvegarde avant d'ouvrir un monde à dimensions de datapack.

### 2026-11-09 (suite) — retours en jeu

**Retour du joueur** : affichage, chapitres 15 à 20 et donjons bons. Quatre problèmes, tous
reproduits puis vérifiés corrigés par un auto-test temporaire sur serveur dédié :
- **pierre aléatoire** : consommée sans rien donner, en survie comme en créatif. En 26.2, le clic
  remet dans la main ce que la pile est devenue ; la pierre tirée, ajoutée à l'inventaire, tombait
  dans cette case vide et était écrasée. Elle passe désormais par le résultat du clic
  (`ItemUtils.createFilledResult`) ;
- **entonnoir sous un cobblebreaker** : il vidait la cobblestone de l'entrée avant les
  particules. `MachineBlockEntity.canTakeItem` laisse chaque machine nommer ses sorties ;
  cobblebreaker et crusher n'en donnent pas d'autres. La palamachine et le grinder restaient
  fermés aux entonnoirs, comme dans l'original ;
- **entonnoir en paladium** : il marchait d'inventaire à inventaire, mais n'aspirait rien au sol,
  et prenait aussi l'entrée d'un cobblebreaker. Il aspire maintenant les objets au-dessus de lui
  quand il n'y a pas d'inventaire, et respecte les sorties des machines ;
- **minerais** : les sept (améthyste, titane, paladium, paladium vert, findium, trixium, pépites
  d'endium) se rendent eux-mêmes, avec 14 recettes de four et de haut fourneau. La Fortune ne
  joue plus qu'à la pioche des dieux.

### 2026-11-09 (suite) — stuff antique et vérification des recettes

**Demande** : « porte le stuff Antique, les bonnes textures et les bons effets tel que sur le
wiki », puis « vérifie que toutes les recettes nécessaires sont fonctionnelles ».

**Le wiki ne décrit pas l'antique.** Recherche : wiki (catégories donjons, items, armures,
événements), patchnotes V9 à V12, site de Paladium. Seules les patchnotes V11 parlent de ses effets
(invisibilité, vol) et de son rendu à part. Choix du joueur : vol et invisibilité au set complet
hors donjon, et le bonus de donjon gardé dedans ; l'armure 3D de Palamod Recreated portée en 26.2.
Détail : `docs/PALAMOD.md` § 25.

**Modèles 3D** : quatre `createBodyLayer` de Blockbench traduits de leur bytecode par un petit
interpréteur de pile (`tools/model2java.py`), 70 cubes. En 26.2, le chapeau est sous la tête, et
NeoForge recopie les poses du modèle d'armure d'origine sur le modèle rendu.

**Vérifié au démarrage, par un auto-test temporaire** (joueur factice) : vol et invisibilité au set
complet hors donjon ; chute lente quand une pièce part en plein vol ; une vraie potion
d'invisibilité gardée ; en donjon, ni vol ni invisibilité mais le bonus de donjon. **Pas vérifié** :
le rendu 3D, qui ne se voit qu'en client.

**Recettes** :
- les 218 recettes JSON (195 avec forme, 19 sans forme, 1 cuisson, 3 spéciales) se chargent : le
  serveur démarre sans aucune erreur de recette, et `check_assets.py` contrôlait déjà objets,
  grilles et clés ;
- aucun conflit entre nos recettes (même grille, miroir compris, ou mêmes ingrédients sans forme),
  et les 9 recettes faites seulement d'objets vanilla n'imitent aucune recette vanilla ;
- palamachine : 36 recettes, tous les noms enregistrés, aucune entrée en double ; grinder (19) et
  chaudron (4) : objets typés, contrôlés à la compilation ;
- une seule recette inutilisable en pratique : `green_paladium_ingot_from_particles`, la particule
  de paladium vert ne s'obtenant nulle part (chez Paladium, elle sortait du lucky block d'août, non
  porté). Laissée telle quelle ;
- auto-test temporaire sur serveur dédié, recette par recette : chacune reconnaît ses propres
  ingrédients, donne un résultat et n'est masquée par aucune autre. 218/218 (l'eau angélique et les
  trois spéciales — mini-golem, sa réparation, gant en endium — avec de vrais objets : potion de
  régénération, pierre de golem avec ses données, pierre légendaire), palamachine 36/36, grinder
  19/19, chaudron 4/4.

### 2026-11-09 (suite) — chapitre 20 : runes et arbre de compétences

**Demande** : « Saute sorts et mana ». Le chapitre prévu pour les sorts est abandonné ; les runes
et l'arbre deviennent le chapitre 20 et ferment les donjons. Deux choix du joueur : statistiques
RPG **en donjon seulement**, et **1 point tous les 5 niveaux de métier** (le wiki : 1 par niveau,
soit 400 points pour les seuls métiers). Détail : `docs/PALAMOD.md` § 24.

**Le wiki ne définit rien des statistiques** : ni ce que sont l'attaque, l'esquive, le critique et
la défense, ni les nœuds de l'arbre, ni les statistiques des runes, ni leurs valeurs. Tout est de
DoHy, calibré pour que l'arbre entier coûte exactement les 260 points qu'on peut gagner.

**Accès** : le bouton « Profil RPG » du wiki est ajouté à l'inventaire (un écran client qui
envoie un message au serveur), en plus de `/profil` et du menu.

**Vérifié au démarrage, par un auto-test temporaire** : tirages et améliorations de runes
jusqu'au tier 4, fusion des lignes partagées, courbe de niveau, points depuis les trois sources,
statistiques et plafonds avec runes portées, recettes de la palamachine, runes tirées dans les
coffres. Le bouton d'inventaire, l'écran de l'arbre, l'enclume et le grinder restent à éprouver
en jeu.

### 2026-11-09 (suite) — chapitre 19 : équipement de donjon, essences, notes

**Demande** : « Chapitre 19 ». Trois choix du joueur : un équipement fort **surtout en donjon**
(hors donjon, il vaut un métal ; dedans, il réduit les dégâts des monstres et l'épée frappe plus
fort), les textures de **Palamod Recreated** (mod de fans trouvé dans les fichiers fournis,
crédité), et un **coffre selon la note**. Détail : `docs/PALAMOD.md` § 23.

**Palamod Recreated** a les douze sets, leurs couches et quinze essences en cinq éléments (dont la
glace d'un cinquième donjon, Frigoria, absent du wiki). Ses statistiques sont des valeurs MCreator
identiques d'un donjon à l'autre : non reprises.

**Vérifié au démarrage, par un auto-test temporaire** : réductions et bonus par tier, à moitié hors
de son donjon, plafond, set antique ; réparations acceptées et refusées ; récompenses uniques des
quatre donjons ; coffres de note ; notes selon la vie perdue.

### 2026-11-09 (suite) — chapitre 18 : donjons, le cœur jouable

**Demande** : « les donjons », depuis le wiki (« Univers des donjons »). Découpage avec le
joueur en quatre chapitres : 18 cœur jouable, 19 équipement, essences et notes, 20 sorts et mana,
21 runes et arbre. Choix du joueur : salles générées par le code, monstres vanilla renforcés, mort
= palier échoué (inventaire gardé), clé consommée. Détail : `docs/PALAMOD.md` § 22.

**Pas de code d'origine.** Les donjons sont de la V12, après la 10.3.0 : le wiki donne les règles,
les noms et les tables de butin, et ne donne ni taux, ni quantités, ni statistiques, ni contenu des
salles boost et marchande, ni emplacement des boss. Tous ces chiffres sont de DoHy et documentés.

**26.2 a encore bougé** : les blocs et objets teints sont des `ColorCollection`
(`Blocks.STAINED_GLASS.pick(DyeColor.RED)`), les étiquettes d'entité sont `entityTags()`, et
`EntityInteractSpecific` n'existe plus.

**Une entité tout juste ajoutée n'est pas visible** de `ServerLevel.getEntity(UUID)` tant que son
chunk n'a pas été promu : la croire morte finissait la vague avant le combat. Les parties gardent
donc leurs monstres par référence, et chaque salle tient ses chunks forcés (libérés à
l'effacement, et au démarrage du serveur après un arrêt brutal).

**Vérifié au démarrage, par un auto-test temporaire** : salles, monstres, boss, butin, déroulé
d'un palier jusqu'à la sortie, région effacée. Le jeu avec un joueur reste à éprouver en jeu.

### 2026-11-09 (suite) — chapitre 17 : pierres légendaires et gant

**Demande** : « les pierres de puissance et le gant ». Ce sont les *legendary stones* de PalaMod et
l'*Endium Gauntlet* qui les porte. Détail : `docs/PALAMOD.md` § 21.

**Deux décisions du joueur.** Obtention : les caisses (la pierre aléatoire dans la rare et la
légendaire, le gant complet dans la légendaire), à la place des lucky blocks. Pierre de pouvoir :
l'eau sulfurique dont elle protégeait n'étant pas portée, elle donne 30 minutes de résistance au
feu.

**Le code contredit le wiki sur les verrous.** Le chaos se verrouille 10 minutes par joueur, pas
une heure (l'heure n'est que dans l'infobulle) ; et le verrou d'une heure des quatre autres
effets n'était jamais écrit — seules les recharges de 24 h par pierre comptent.

**Vérifié au démarrage** : le gant garni et la pierre datée se posent par commande avec leurs
composants, les caisses se chargent avec le gant complet. L'activation demande un joueur : non
testée en jeu.

### 2026-11-09 (suite) — chapitre 16 : pour le PvP

**Demande** : « ajoute les sticks, les rings, hang glider, leurs effets, craft », depuis la page
du wiki « Java – Pour le PvP ». Les sticks et les anneaux ne se fabriquent qu'à la palamachine,
qui n'était pas portée (chapitre 5b) : elle vient avec. Détail : `docs/PALAMOD.md` § 20.

**Le wiki et le code divergent, et c'est le code qui est porté** : vitesse VII et non III pour le
stick de vitesse (amplificateur 6), recharges de 10 à 40 s, et un anneau réparé à la palamachine
**garde** ses enchantements (`ench` recopié), quand le wiki dit qu'il les perd.

**Les anneaux comptent de deux façons.** Portée, une armure consomme sa capacité point par point
(8 000 à 12 000) ; au coffre, par remise à neuf complète — les « 3 » des outils en paladium sont
trois remises à neuf. Le coffre en endium ne répare que l'endium, avec des anneaux en endium. Les
coffres prennent leur colonne d'anneaux, laissée de côté au chapitre 14.

**Où tourne la physique.** Le deltaplane et le slimepad poussent un joueur : c'est son client
qui calcule son mouvement, la poussée y est donc appliquée (`isLocalInstanceAuthoritative`), le
serveur ne gardant que l'état et l'annulation de la chute. Les sticks de saut, eux, partent du
serveur avec le paquet de vitesse, comme l'original.

**Vérifié au démarrage, par RCON** : création d'un stick, réparation qui garde Solidité III et
passe de 4 à 5, refus de la sixième ; coffre en paladium qui répare les deux rangées du haut et
pas la troisième, capacité décomptée à la remise à neuf ; coffre en endium qui répare l'épée en
endium et pas celle en paladium ; spike qui tue un cochon ; slimepad qui projette un objet vers
l'est. Les sticks et le deltaplane demandent un joueur : non testés en jeu.

### 2026-11-09 — chapitre 6 : PalaMod entre dans l'économie

**Demande** : « la refonte de l'économie ». Portée choisie par le joueur : intégrer PalaMod au
modèle de DoHy, le reste de l'économie ne bouge pas. Détail : `docs/PALAMOD.md` § 19.

**Aucun objet de PalaMod n'avait de prix.** Les règles par tag ne voient que les tags `c:`, et
PalaMod n'en déclare aucun. Vingt-quatre matières premières entrent au catalogue, dans un sixième
onglet ; le reste (241 objets fabriqués) n'a pas de prix, comme les objets fabriqués vanilla.

**L'audit des machines** a été fait avant de chiffrer, et trois décisions sont du joueur :
- la recette du chaudron poudre lumineuse → 3 pépites d'or est **retirée** (argent infini : la
  poudre s'achète 4 $, les pépites se revendent 27 $) ;
- les blocs de masse ont un **plancher de 5 %** : la corne du Dancarok en casse 154 par coup ;
- le cobblebreaker garde ses diamants (1,6/h par machine), limité par la pierre fournie.

**Un trou d'intégration trouvé en route** : les cultures de PalaMod plantées à la main étaient des
blocs posés, donc sans XP de Fermier, sans collection, sans contrat — depuis le chapitre 3.


### 2026-11-08 (suite) — chapitre 13 : le golem gardien

**Demande** : le golem, sa boîte, les croquettes et leur machine. Relevés et écarts :
`docs/PALAMOD.md` § 16. Source : le mod GuardianGolem 6.1.0, ponté par PalaMod.

**Un mod dans le mod.** Le golem a son entité, son IA, un arbre de 30 compétences, cinq choix,
un gardien qui garde sa pierre à sa mort, une machine et une boîte multibloc. Une bonne part de
l'arbre ne faisait rien dans l'original (démembrement vide, limites par chunk inexistantes,
auto-crafter jamais ajouté) : ces branches ne sont pas portées, et les descriptions disent ce que
fait le code.

**Les fautes corrigées sont des fautes de logique**, pas d'équilibrage : le chargement à mi-vie à
chaque rechargement du chunk, le golem rendu passif par un choix, les golems d'un même maître qui
s'entretuaient, la machine à croquettes qui consommait sans lave.

**Le premier mob du mod.** Le modèle et la texture sont ceux du golem de fer de 1.7.10 :
le golem de fer de 26.2 les reprend tels quels, avec le moteur de rendu par états.

### 2026-11-08 (suite) — chapitre 12 : le grinder

**Demande** : le grinder, puis le buisson à XP et la pioche des dieux. Le golem, sa boîte, les
croquettes et leur machine viennent au chapitre suivant. Relevés et écarts : `docs/PALAMOD.md`
§ 15.

**Le grinder est un cube, pas une recette.** 26 blocs et une source de lave, dans n'importe
quelle direction autour du contrôleur. PalaMod ne revérifiait la structure qu'à l'ouverture ;
une machine au cube cassé continuait de forger. Elle s'arrête maintenant.

**La pioche des dieux avait un trou à chaque palier.** Deux inégalités strictes : pile sur un
seuil, aucun niveau ne répondait, la pioche retombait au niveau 1 et perdait ses enchantements le
temps d'un bloc. Le niveau est maintenant le plus haut seuil dépassé, et les enchantements ne
changent qu'au changement de palier — le Toucher de soie survit.

**Un relevé qui n'avait pas tout vu.** `tools/extract_crafts.py` sautait les lignes de grille
faites d'espaces : le cadre du grinder (`YZY / ␣␣␣ / YZY`) sortait sur deux lignes. Corrigé ;
aucune recette déjà portée n'a changé. Les recettes du marteau de caverne et de l'épée de
capture retrouvent leurs ingrédients d'origine.

### 2026-11-08 (suite) — chapitre 11 : l'Alchimiste

**Demande** : porter l'Alchimiste. Périmètre choisi par le joueur : arbres et sève, chaudron,
portails, enchantements mystiques. Écartés : table d'alchimiste, totem fleuri, boules de glu,
trône. Relevés et écarts : `docs/PALAMOD.md` § 14.

**Tout tient aux arbres.** La sève vient de quatre arbres de PalaMod que DoHy n'avait pas, et que
PalaMod ne générait que sur son serveur de minage. Ils sont portés et générés au rythme
d'origine ; sans eux, aucun objet du métier ne s'obtient.

**Les « portails » sont des autels.** Rien ne téléporte : la sève jetée s'accumule dans la clé et
ressort en lingots. Quatre bugs de l'original y sont corrigés (contenants détruits, trou vidé
de tout, un coin non vérifié, l'endium qui donnait des pépites sans pollen par tank).

**Premier mixin du mod** (`dohy.mixins.json`) : l'anti-chute n'a aucun événement pour se brancher,
`Player.isStayingOnGroundSurface` est le seul point qui décide. La méthode a été vérifiée à la
sonde, et `LocalPlayer` ne la redéfinit pas.

**Les recettes de `CraftMod`** rejoignent `data/palamod_crafts.json` : le relevé de
`tools/extract_crafts.py` sur un cinquième registre, les 282 recettes précédentes inchangées.

### 2026-11-08 (suite) — chapitre 10 : le Chasseur

**Demande** : porter les objets du Chasseur. Périmètre choisi par le joueur : canne à pêche en
paladium, épée en endium, épée et pierre de capture, marteau de caverne, spawner cassé, âmes et
spawner customisé. Sans contrôleur ni le reste ; le spawner se règle sur le wiki. Consignes en
route : **le spawner posé garde le fonctionnement vanilla** ; les paliers restent, plus de
créatures par palier (**3 / 6 / 9 / 12**) ; ses créatures **s'empilent en une seule** avec leur
nombre au-dessus, et tuer une pile n'en tue **qu'une à la fois**. Relevés et écarts :
`docs/PALAMOD.md` § 13.

**Le spawner qui ne s'arrêtait jamais.** Première version : la vague était annulée à
l'apparition. Lu au bytecode, `BaseSpawner.serverTick` ne relance son délai que si l'ajout au
monde a été tenté ; annulée plus tôt, la vague recommence au tick suivant, quatre fois par tick.
La fusion se fait donc à l'entrée dans le monde, après quoi le spawner attend ses 10 à 40 s.

**Noms de 26.2 trouvés à la sonde** : les types d'entités sont dans `EntityTypes`, ceux de block
entity dans `BlockEntityTypes` ; un bloc à soi reçoit la block entity du spawner par
`BlockEntityTypeAddBlocksEvent`.

### 2026-11-08 (suite) — chapitre 9 : le Fermier

**Demande** : porter les objets débloqués du Fermier. Consignes du joueur en route : **aucun
trône, pour aucun métier**, et l'économie viendra **après** tous les métiers.

Le Fermier était déjà presque complet (houes, seedplanters, cultures, crusher, totem, armure en
endium). Les herbes colorées et le racinier ont été portés, puis **retirés à la demande du
joueur** (« nul et moche ») : il garde la hache en endium et demande l'**eau angélique**. Les
relevés au bytecode de ces deux objets sont résumés dans `docs/PALAMOD.md` § 12.

**Premier retour en jeu** : l'eau angélique se traversait comme de l'air et ne soignait pas. Il
manquait `FluidType.Properties.isWaterLike(true)` : en 26.2 c'est ce drapeau, et non le seul fait
d'être un fluide, qui donne la physique de l'eau. Le soin lit maintenant le fluide dans le monde.

**Le rendu des fluides a changé en 26.2.** `IClientFluidTypeExtensions` n'a plus de textures ;
un fluide se dessine par un `FluidModel.Unbaked` (textures fixe, coulante, voile) enregistré dans
`RegisterFluidModelsEvent`. Trouvé à la sonde, en lisant comment le jeu construit l'eau et la
lave dans `FluidStateModelSet`.

**Sources disponibles chez le joueur** (dossiers connectés depuis l'app de bureau) : le dossier
`.paladium` en 1.7.10 (mods `.pala` complets, configs) et l'instance PrismLauncher 26.2 où il
teste le mod — ses `logs/latest.log` sont lisibles pour déboguer un retour en jeu.

### 2026-11-08 (suite) — chapitre 8 : le Mineur

**Demande** : « fais totalement le chapitre mineur, n'ajoute pas le /nether ». En cours de
route : « saute le trône ». Tout le reste des objets débloqués du Mineur est porté, avec les
prérequis que le wiki ne liste pas (coffres en améthyste et en titane, Palafurnace et son
amélioration, cave block). Détail et écarts : `docs/PALAMOD.md` § 11.

**Le relevé des verrous ratait 26 objets.** `extract_jobs.py` ne lisait que les registres
`palamod/api` ; trônes, spawner et houes à rayon sont ailleurs. 99 verrous au lieu de 73, et
**les houes à rayon sont maintenant verrouillées** (Fermier 10 à 100) — changement de
comportement hors du chapitre.

**Ce qui n'est pas porté** : `/nether` (demandé), le trône (demandé ; son verrou reste inerte),
l'amélioration de vitesse du spawner (aucun système de spawner), les cases d'anneaux des
coffres, l'obsidienne wither. Le minerai d'endium, qui ne naissait que dans la dimension de
minage, est placé au fond de l'overworld.

**Deux choix d'économie.** La Dollars Stone vend par le marché de DoHy au prix dynamique, par
lots d'une seconde, au lieu de 0,10 $ fixes. Les XP codées en dur (potion de dureté, God
Villager) sont des XP de PalaJobs, converties au rapport des deux courbes (0,357).

**Trois pièges 26.2 vus à la compilation ou à la sonde** : les constantes d'entités sont dans
`EntityTypes`, plus dans `EntityType` ; `Entity.getTags()` est devenu `entityTags()` ; et le
champ `render_type` des modèles n'est plus utile — depuis 26.1 le jeu choisit la passe cutout
d'après les pixels de la texture.

**À vérifier en jeu** (la CI ne voit ni le rendu ni les paquets) : le contour bleu du cave
block en endium, le modèle JSON des coffres et leur orientation, la flamme de la Palafurnace et
de la Palaforge, et l'entonnoir en paladium sur une chaîne.

### 2026-11-08 (suite) — les métiers remplacent les skills

**Demande** : « remplace les skills du DoHy par les métiers ». Trois décisions prises avec le
joueur avant d'écrire une ligne :

1. **Les cinq skills sans métier sont supprimés** — bûcheronnage, pêche, forge, construction,
   nucléaire — avec leurs sources d'XP et leurs perks. Partent avec eux l'abattage, la
   trouvaille de pêche, la chute amortie et la remise d'enclume.
2. **Récompenses : les deux.** Chaque niveau verse l'argent et les perks de DoHy, **et** les
   objets que Paladium donnait à ce palier.
3. **L'XP est migrée** : minage → Mineur, combat → Chasseur, agriculture → Fermier,
   alchimie → Alchimiste.

**Ce qui a changé dans le code.** Le paquet `skill` n'existe plus : `Job` est la seule
progression, avec `JobData` (pièce jointe `dohy:jobs`), `JobProgression`
(`job_progression.json`, l'ancien `skills.json` réduit aux quatre métiers), `JobPerks`,
`JobEffects`, `JobXpEvents`, `JobBossBar`. `JobsMenu` fusionne les deux écrans : grille des quatre
métiers, puis une fiche à trois onglets — déblocages, perks, récompenses. `/skills` ouvre les
métiers pour ceux qui l'ont dans les doigts. La vignette « Skills » du menu central est devenue
« Métiers ».

**La migration** (`JobMigration`) : l'ancienne pièce jointe `dohy:skills` reste enregistrée en
lecture seule. À la première connexion, son XP est versée dans `dohy:jobs` puis elle est
supprimée. Le joueur reçoit au passage les **objets** des paliers qu'il avait déjà franchis —
l'argent de ces niveaux avait été payé, les objets n'existaient pas. Les contrats
`SKILL_LEVELS` en cours sont relus sous leur nouveau nom `JOB_LEVELS`.

**Les récompenses, au bytecode.** 280 `Reward.builder` dans les quatre registerers : 204 objets,
76 montants d'argent. Seuls les objets sont portés (DoHy paie déjà le niveau). Piège trouvé :
la pioche, l'épée et l'armure enchantées sont construites hors de la chaîne et passées par une
variable locale — un relevé naïf en perd six. `extract_jobs.py` suit maintenant les
`astore`/`aload` et les `func_77966_a`. Les enchantements sont traduits par la lettre finale des
champs SRG, qui suit l'ordre de déclaration (`_p` efficacité, `_r` solidité, `_k` châtiment).

**Un bug d'extraction corrigé en route** : `extract_jobs.py` prenait le dernier entier empilé
avant l'appel comme niveau — la méta ou la quantité de l'`ItemStack` quand il y en avait une.
La boussole du chasseur tombait au niveau 15 au lieu de 100, et les quatre outils de construction
(quatre métas d'un même objet) ne faisaient qu'un verrou. 73 verrous au lieu de 70.

**Un perk inerte réparé** : les trois perks « récolte accrue » de l'Alchimiste ne s'appliquaient
à rien (l'effet ne lisait que l'agriculture). Ils portent maintenant sur la verrue du Nether
mûre — mûre seulement, sinon planter puis casser la dupliquerait.

**L'argent total baisse mécaniquement** : 4 métiers maxés font 80 M au lieu des 160 M des 8 skills,
la courbe par niveau n'a pas bougé. Les objets s'y ajoutent : 1 050 lingots de paladium et
autant de trixium par métier maxé — leur poids réel dépendra des prix PalaMod, chapitre 6.

### 2026-11-08 (suite) — onze recettes perdues, et des interfaces aux couleurs de Paladium

**Signalement** : pas de recette pour les pioches ni les charbons mixés ; il manque le
cobblebreaker et ses améliorations ; les interfaces sont « assez moches ».

**Les recettes perdues, lues dans le log** : onze rejetées au chargement,
`Invalid pattern: each row must be the same width`. Ma faute, pas celle du jeu : **javap rogne
les espaces de fin** des chaînes, même dans le pool de `javap -v`, et la grille `" Y "` sortait
`" Y"`. L'extracteur lit maintenant le pool de constantes dans le `.class` lui-même. Et les
recettes du chapitre 5a, que j'avais **recopiées à la main** depuis le relevé encore faux, sortent
désormais toutes du relevé : un seul émetteur, une seule source. Corriger l'extracteur corrige
toutes les recettes.

Deux garde-fous : le générateur refuse une grille non rectangulaire, et `check_assets.py` vérifie
largeurs, cases sans clé et clés inutilisées — les trois motifs de rejet de 26.2. Vérifié en
recassant la recette de la pioche.

**Le cobblebreaker, avec sa chaîne complète.** Il demande la voidstone et le modificateur de
fortune. J'avais refusé de porter la voidstone comme ingrédient inerte ; elle arrive donc **avec
son comportement** : une poubelle de poche (`VoidStoneInventory` ne garde rien), qui refuse les
outils et elle-même. Les modificateurs, eux, sont des `Item` nus dans PalaMod — c'est le grinder
qui les lit — donc les porter dès maintenant n'est pas un mensonge. À savoir : **l'amélioration
améthyste consomme un cobblebreaker**, c'est l'original.

**Un vrai défaut de sécurité trouvé en route.** Les cases de `MachineMenu` étaient des `Slot`
vanilla, dont `mayPlace` répond oui à tout : seul le shift-clic consultait le filtre. Un clic
direct pouvait bloquer un bac de sortie du cobblebreaker avec de la terre — ou laisser tomber sa
pioche dans la voidstone, qui la détruit. Le filtre s'applique maintenant aux deux gestes.

**Les interfaces.** Paladium ne dessinait pas d'images de fond : Paladium-UI composait par code,
en aplats sombres. Sa palette est relevée dans `ColorConstant` (`#1E1D23`, `#40404D`, `#EF3926`) et
reprise : panneau sombre, accent rouge sous le titre, flèche blanche comme
`arrow_furnace_filler.png`, coins de couleur par métal sur les sorties du cobblebreaker (la rareté
se lit de gauche à droite), silhouettes fantômes dans les cases d'entrée. Deux défauts de
disposition corrigés : la case de charbon du crusher mordait sur « Inventaire » (le crusher reprend
la géométrie exacte du fourneau vanilla), et la flèche du cobblebreaker était décentrée de 4,5 px.

Les libellés vanilla sont écrits en `0xFF404040` codé en dur dans `extractLabels` (relevé au
javap) : illisibles sur fond sombre. La méthode est protégée, donc surchargée avec les couleurs de
Paladium-UI.

### 2026-11-08 (suite) — chapitre 7 : l'equipement, et la formule d'armure qu'on ne peut pas lire

**Demande du joueur** : armures, epees, pioches, houes, haches et leurs effets, en s'aidant des
deux PalaMod, du wiki et des mods fournis. Le chapitre passe avant 5b et 6 a sa demande.

**Trois sources, et la troisieme etait inattendue.** `PToolMaterial` et `PArmorMaterial` pour les
chiffres, `PRegister_Items` pour la construction de chaque piece — et **PalaJobs** pour les
houes. Elles n'existent pas dans le registre principal de PalaMod : elles appartiennent au
metier de fermier (`ItemRadiusHoe`), et c'est en suivant leurs textures dans le pack que je les
ai trouvees. Leur geometrie est celle des seedplanters : on laboure le carre qu'on plantera.

**Ce qui se porte exactement.** Durabilite, efficacite, enchantabilite, et les degats de l'epee :
`4 + bonus` en 1.7.10, `1 + bonus + 3` en 26.2, meme formule. La durabilite des armures aussi,
`ArmorType.getDurability` multipliant par les memes bases {11, 16, 15, 13}.

**Ce qui ne se porte pas, et c'est le point du chapitre.** Les armures Paladium totalisent 36 a
46 points. En 1.7.10, Forge plafonne l'absorption a 100 % des 25 points : chacune rendait
**invulnerable aux coups**. Le jeu reel ne marche evidemment pas comme ca — le calcul de degats
de Paladium vit dans un plugin serveur. J'ai cherche `applyArmorCalculations`, `ISpecialArmor`
et `ArmorProperties` dans les dix jars fournis : rien. Cette formule-la, on ne la lira pas.

La defense est donc la **seule valeur calibree** du chapitre, et la calibration est dans le code
avec les valeurs d'origine a cote : ancree sur le diamant (identique dans les deux versions),
projetee **emplacement par emplacement** jusqu'au plafond de 30. La premiere version projetait
set par set, et rendait le casque en titane moins bon que celui en amethyste alors que les deux
valent 9 — une fonction croissante appliquee piece par piece ne peut pas faire ca.

A savoir avant de tester : **26.2 ecrase le haut de gamme.** Contre un coup de 10, tous les sets
Paladium sont au plafond de 80 %. Ce n'est pas trahir l'original — en 1.7.10 cote client ils
etaient tous a 100 %. La progression se lit sur les gros coups (61 → 66 → 75 → 80 % a 30
degats), la durabilite et les effets. Si l'ecart parait trop faible, le levier est la robustesse.

**Les effets, et deux choses qu'on ne devinerait pas.** Les bottes en endium mixe donnent une
**immunite** aux chutes, pas une reduction : `PotionFeatherFalling`, une potion propre a PalaMod,
lue par `PotionHandler` qui ramene les degats de chute a zero. Et la panoplie complete d'endium
rend **invisible** — or les verrous du chapitre 1 repartissent ses quatre pieces entre les quatre
metiers, au niveau 100. **L'invisibilite est la recompense des quatre metiers au maximum.** Deux
effets du code d'endium (force, vitesse II) sont du code mort, enjambes par un `goto` ; ils ne
s'appliquaient jamais et ne sont pas portes.

**Deux fautes silencieuses de plus, et leurs garde-fous.** Une armure dont la definition
d'equipement ou une couche manque est portee, protege — et ne se voit pas. Et en 26.2 ce sont des
**tags d'item** qui decident si Tranchant va sur une epee : une piece absente de `#swords` ou
`#head_armor` refuse ses enchantements sans erreur, ce qui pour du PvP Paladium serait un trou
beant. `check_assets.py` verifie les deux chaines ; verifie en les cassant.

**Les recettes viennent du relevé, pas d'une transcription.** `data/palamod_crafts.json` →
une table de correspondance explicite → 49 recettes. Un nom inconnu fait echouer le generateur.

**Une seule erreur de compilation sur tout le chapitre** : `LivingEntity.getSlotForHand`
n'existe pas en 26.2. `ToolMaterial`, `ArmorMaterial`, `humanoidArmor`, les effets renommes
(`SPEED` et `HASTE`, plus `MOVEMENT_SPEED` / `DIG_SPEED`), `LivingFallEvent` et le labour NeoForge
ont tous compile du premier coup — parce que chacun avait ete releve a la sonde avant d'etre ecrit.

**Une erreur de ma part a corriger dans le recit.** J'avais annonce les 15 recettes du chapitre 5a
dans la PR #38. Elle a ete mergee a 11 h 15, le commit des recettes a ete pousse un quart d'heure
apres : il n'y est jamais entre. Il voyage dans la PR de ce chapitre.

### 2026-11-08 (suite) — chapitre 5a : le totem, et l'ambiguite tranchee

**Le chapitre 5 est coupe en deux, et je dis pourquoi.** Le totem et les seedplanters sont des
machines de champ : ils travaillent sur la terre fertilisee du chapitre 3 et completent le
travail du fermier. Le palaforge, le grinder et la palamachine sont des machines de craft : ils
demandent un registre de recettes (`GrinderRecipe`, `PRegister_Craft`) et, pour le grinder, un
multiblock avec son blueprint. Deux surfaces techniques sans rien en commun ; les livrer
ensemble aurait donne un chapitre long et a moitie profond. 5a est complet, 5b suit.

**1. L'ambiguite du chapitre 4 est tranchee au bytecode.** J'avais refuse de deviner si
`waiting = 1000` et `timeforgrowth = 10000` etaient des millisecondes ou des ticks — un facteur
20 sur l'equilibrage. Le tick d'origine repond en une instruction :

```
 4: invokestatic System.currentTimeMillis():J   → now
36: ldc2_w 10000l ; ladd                        → timeforgrowth = now + 10000
66: ldc2_w 1000l  ; ladd                        → waiting = now + 1000
```

Ce sont des **millisecondes**. Donc 10 s entre deux poussees de croissance, 1 s entre deux
balayages. L'autre lecture aurait donne 10 000 ticks, soit huit minutes par poussee : un totem
inutilisable, et le genre de chiffre qu'on ne remet pas en cause avant des semaines de jeu.

**2. Une decouverte absente du wiki : le totem ne travaille que sur la terre fertilisee.** Le
balayage ne retient que les `BlockConnectedDirt`, et le boost revérifie la condition. Le totem
et la terre fertilisee sont un couple : un totem alimente pose sur un champ ordinaire ne fait
rien, et rien dans le jeu ne le dit. C'est pour ca que l'affichage de la zone (maj + clic droit)
ne montre pas le carre de 15x15 mais **les cases fertilisees** — montrer le carre entier aurait
laisse croire que tout le carre est accelere. Le balayage et l'affichage partagent la meme
methode, sinon l'affichage finirait par mentir.

**3. Pas d'interface pour le totem, et c'est un choix.** Son etat tient en deux nombres : des
lingots, et des poussees restantes. Une fenetre a une seule case aurait demande une troisieme
texture d'interface, une disposition, une entree dans le manifeste — pour moins d'information
qu'une ligne de chat. Les trois gestes d'origine sont conserves : lingot en main pour
alimenter, main vide pour consulter, accroupi pour voir la zone.

**4. Trois ecarts avec l'original, tous volontaires et notes dans la classe.**

- **Le temps est compte en ticks de jeu.** L'original lit l'horloge du systeme, donc il tourne
  quand le monde est en pause et brule du carburant pendant qu'on regarde un menu. Les durees
  sont identiques, 200 et 20 ticks.
- **La zone est rebalayee a chaque cycle.** L'original ne rebalaye que `if (blockList.isEmpty())`,
  et la liste ne faisant que grossir, elle n'est balayee qu'une fois : de la terre fertilisee
  posee **apres** le totem n'etait jamais prise en compte. C'est un piege, pas un equilibrage.
  225 blocs toutes les 10 s ne coutent rien.
- **Le carburant paie les croissances reelles, pas les tentatives.** L'original incremente son
  compteur quand la plante *pourrait* pousser (`IGrowable.canGrow`). On compare ici l'etat avant
  et apres : plus mesurable, legerement plus avantageux pour le joueur, et surtout ca evite de
  dependre d'une signature de `BonemealableBlock` qu'il aurait fallu deviner.

**5. Les seedplanters, et un mecanisme qu'on n'aurait pas invente.** Les graines doivent etre
dans **la case juste a droite** du planteur. Ce n'est pas arbitraire : ca laisse choisir la
plante sans interface et rend le geste reproductible. Releve a la sonde pour le lire en 26.2 :
`Inventory.getSelectedSlot()`, `Inventory.SELECTION_SIZE`.

Rayons de `PRegister_Items` : 3, 5, 7, 12, 12 → carres de 2x2, 4x4, 6x6, 12x12, 12x12. Le
paladium vert a le **meme rayon que l'endium pour un niveau plus bas** (90 contre 100) : version
alternative, pas palier superieur. Conserve.

La plante se deduit du `BlockItem` de la graine — `eggplant_seeds` porte `eggplant_crop` comme
`wheat_seeds` porte `wheat` — donc aucune table de correspondance a tenir, et une culture
ajoutee plus tard marche sans toucher le planteur.

**6. Deux jeux de niveaux se contredisent dans PalaMod, et ce n'est pas une faute de portage.**
Le champ `lvlmin` de l'item (5/10/15/20/17) et le niveau de `FarmerRegisterer` (3/6/15/18/20)
ne concordent que pour le paladium. C'est le registre de metier qui gouverne le verrou, et
c'est lui que `data/jobs.json` porte depuis le chapitre 1 ; `lvlmin` n'est qu'une infobulle.
Note des deux cotes pour que le prochain qui trouve l'ecart sache qu'il est d'origine.

**7. Un ecart visuel assume.** L'original dessine le totem avec un renderer de block entity
(`ModelTotem`, `RenderTotem`) : un mat sculpte, pas un cube. Ici c'est un cube texture avec
`blocks/totem.png`. Un renderer en monde est la partie vraiment difficile du rendu 26.2, et le
chapitre 4b vient tout juste d'introduire le premier ecran du mod. A reprendre si le cube gene.

**8. Un petit refactor que le totem a force.** `MachineBlock` tiquait sur `MachineBlockEntity`,
qui impose un type de menu et une disposition de cases. Le totem travaille a chaque tick sans
avoir d'interface : il aurait fallu lui inventer les deux. D'ou l'interface `ServerTicking`, que
le ticker teste a la place.

Les six entrees de `data/jobs.json` qui portaient sur ces blocs cessent d'etre inertes. Le
decompte de `/metiers audit` passe de 61 a 55.

### 2026-11-08 (suite) — « rien ne se passe » : l'erreur que le jeu avale

**Signalement** : clic droit sur un cobblebreaker et sur un crusher, rien, aucun message.

**La cause est dans le log, en une ligne**, et nulle part ailleurs :

```
java.lang.IllegalArgumentException: Container size 7 is smaller than expected 9
	at net.minecraft.world.inventory.ChestMenu.<init>
	at fr.atraxterra.dohy.palamod.machine.MachineBlockEntity.createMenu(MachineBlockEntity.java:151)
	at fr.atraxterra.dohy.palamod.machine.PalaMachineEvents.onRightClickBlock
[Server thread/ERROR] Failed to handle packet ServerboundUseItemOnPacket, suppressing error
```

Le coffre du chapitre 4 ouvrait un `GENERIC_9x1`, qui exige **neuf** cases ; le cobblebreaker
en a sept, le crusher trois. `checkContainerSize` jetait, et **le jeu supprime l'exception** :
« suppressing error ». 183 occurrences pour le cobblebreaker, 111 pour le crusher, et pas un
mot a l'ecran. C'est la pire variante du defaut qui revient a chaque chapitre — compile,
charge, demarre, et echoue sans trace visible.

**Ce n'etait pas le code courant.** Le jar installe avait la PR #37 (exemption creatif) mais
**pas** la #36 (interfaces) : la branche du creatif partait d'un `main` d'avant le merge des
interfaces, donc un build de cette branche gardait l'ancien `createMenu` a `ChestMenu`.
Verifie au `javap` sur le jar installe : ni `MachineMenu`, ni `PalaMenus`, et un
`invokespecial ChestMenu.<init>` dans `createMenu`. `main` a ete fusionne dans la branche pour
que les deux correctifs voyagent ensemble.

**Le garde-fou, pour que ce ne soit pas une lecon jetable.** L'invariant viole etait : la taille
de l'inventaire doit egaler le nombre de cases du menu. Il reviendra au chapitre 5, ou chaque
machine ajoute un `SLOT_COUNT` et une disposition. `check_assets.py` compare donc le
`SLOT_COUNT` de chaque `*BlockEntity` au nombre de cases de son interface. Verifie en passant
le cobblebreaker a 6 :

```
::error::gui : CobblebreakerBlockEntity.java declare SLOT_COUNT=6 mais l'interface a 7 cases. L'ouverture echouera en silence, l'erreur etant supprimee par le jeu
```

**Et la sonde voit enfin les membres proteges.** Deux fois dans la journee j'ai bute sur des
crochets invisibles a la reflexion : `renderLabels` (supprime, remplace par `extractContents`)
et `getRenderShape`. La reflexion ne montre que le public, et ne dit **jamais** ce qu'une
methode retourne. `gradle bytecodeProbe -Pjavap=<classe> -Pmember=<methode>` lance
`javap -p -c -constants` sur le classpath : membres proteges et corps des methodes. C'est la
technique qui a servi a relever le PalaMod depuis son jar, maintenant disponible pour Minecraft
lui-meme.

### 2026-11-08 (suite) — le creatif ne passe plus par les verrous

**Demande du joueur** : en creatif, pouvoir ouvrir et utiliser les items qui demandent un
niveau de metier.

**Fait**, et la ou il fallait. L'exemption est dans `JobGateEvents`, **pas** dans
`JobGate.allows`. La distinction n'est pas cosmetique : `allows` repond « ce joueur a-t-il le
niveau ? », et le Hub, `JobsMenu` et `/metiers` s'en servent pour compter les deverrouillages
et afficher la progression. Exempter la-bas aurait affiche **« 70/70 » a un administrateur de
niveau 1**, et masque sa vraie progression des qu'il repasse en survie — un bug de confiance
dans l'interface, pour une commodite de construction. Ici, `allows` dit toujours la verite et
seule l'application choisit de ne pas s'en servir.

Trois gestionnaires, trois appels a `exempt(player)`, qui teste `player.isCreative()` —
`isCreative()` releve a la sonde avec `hasInfiniteMaterials()` et `gameMode()`, pas pris de
memoire.

**La faute que ca ouvre, et qui arrivera au chapitre 5.** Les seedplanters, le totem et le
grinder ajouteront des verrous. Un quatrieme gestionnaire ecrit sur le modele des trois
autres mais sans le test d'exemption **compile, se charge, tourne** — et bloque les
administrateurs en creatif sans un mot dans les logs. Personne ne le remarque avant qu'un
joueur s'en plaigne.

`tools/check_job_gates.py` lit les gestionnaires `@SubscribeEvent` de la classe et exige que
chacun appelle `exempt(`. Il verifie aussi les deux autres facons de casser ca : une `exempt()`
qui ne teste plus aucun mode de jeu, et l'exemption migree dans `allows`. Verifie en provoquant
les trois ; les trois remontent nommement, dont le cas du chapitre 5 ecrit tel qu'il le sera :

```
::error::onSeedplanterUse n'appelle pas exempt() : ce verrou s'appliquerait aussi en creatif, et ca ne se voit qu'en jeu
::error::exempt() ne teste aucun mode de jeu : elle ne peut donc plus reconnaitre un joueur en creatif
::error::JobGate.allows teste isCreative : l'exemption appartient a JobGateEvents. Ici elle afficherait « tout deverrouille » dans /metiers et masquerait la vraie progression
```
### 2026-11-08 (suite) — des interfaces a nous, et ce que le coffre cachait

**Demande du joueur** : des interfaces customs au lieu du coffre vanilla. Option 2 retenue —
un vrai `AbstractContainerMenu` cote serveur et un `AbstractContainerScreen` cote client.

**Ce que le coffre cachait, et pourquoi c'etait un vrai probleme.** Le chapitre 4 adossait
l'inventaire des machines a un `ChestMenu` pour n'ecrire aucune ligne de code client. La limite
n'etait pas esthetique : **un coffre ne synchronise que des objets**. Aucun entier ne transite,
donc aucune jauge, aucune cadence. Le cobblebreaker a quatre paliers dont le seul effet est la
vitesse — et le joueur qui depensait un lingot de paladium pour passer de 400 a 100 ticks
**n'avait aucun moyen de voir que son amelioration avait pris**, sinon le chronometre. Une
machine qui cache son unique variable d'amelioration est une machine qui ne recompense pas.

**1. Le canal : `ContainerData`, deux entiers.** C'est le seul chemin qui synchronise des
entiers d'un menu ouvert sans ecrire de paquet reseau — `broadcastChanges` les envoie quand ils
changent. Le socle expose l'avancement du cycle **et sa duree totale**. La duree y figure parce
que celle du cobblebreaker depend de son palier, que le client ne peut pas deduire ; l'ecran ne
fait que le rapport des deux et n'a donc rien a savoir d'aucune machine. `cycleDuration()` rend
zero quand la machine ne peut pas tourner, ce qui donne a l'ecran « aucune progression » sans
qu'il connaisse les conditions de chacune.

**2. Un menu generique, deux dispositions.** `MachineMenu` est le meme pour les deux machines ;
seule change sa `MachineLayout`. Le cobblebreaker met son entree en haut et ses six bacs en
rangee, **ranges du metal le plus courant au plus rare de gauche a droite** — l'ordre dans
lequel ils se remplissent, donc la rarete lisible sans explication. Le crusher reprend la
disposition du fourneau vanilla (matiere, combustible, resultat), que tout le monde sait lire.

**3. Le shift-clic a fallu le filtrer.** Sans `canPlaceItem`, une pierre pouvait partir dans un
bac de sortie du cobblebreaker, **hors d'atteinte de la machine**. Les cases de sortie declarent
donc `input = false` et chaque machine filtre ce qu'elle accepte. Le crusher, lui, accepte tous
les fruits en haut et tous les charbons melanges en bas sans verifier qu'ils vont ensemble :
c'est le couple que `matchingRecipe` exige, et filtrer plus tot empecherait de preparer la
machine en deux gestes.

**4. Le sens de remplissage n'est pas l'axe.** La fleche du cobblebreaker est verticale et se
remplit **du haut vers le bas**, parce qu'elle pointe vers le bas ; la flamme du crusher est
verticale aussi mais **monte**. Reutiliser un seul booleen pour les deux remplissait la fleche a
l'envers. `Sprite` porte donc `vertical` (l'axe) et `fillUp` (le sens), separement.

**5. Le probleme reel de ce chapitre : deux sources pour un meme chiffre.** Les positions des
cases vivent forcement a deux endroits — `tools/gen_gui.py` dessine les creux dans la texture,
`MachineLayout` les declare au menu. Un decalage de deux pixels donne une interface ou les
objets flottent a cote de leur creux, et **rien ne le signale** : ni la compilation, ni le
chargement, ni un demarrage reussi. C'est la meme classe de faute que les textures violettes du
chapitre 2 et le crash d'initialisation statique d'hier : correcte pour le compilateur, fausse
en jeu.

Traitee de la meme facon : le generateur emet `data/gui_layout.json`, et `check_assets.py`
compare ce manifeste aux constantes Java — positions, roles, coordonnees et taille des sprites,
dimensions du panneau, inventaire du joueur, et la taille 256x256 que `MachineScreen` suppose a
chaque `blit`. Verifie en cassant volontairement trois choses : un slot decale de 2 px, un role
`fuel` declare non-entree, un `ARROW_FULL_V` desaccorde. Les trois sont remontees nommement.

**Ce que 26.2 change pour un ecran.** `GuiGraphics` n'existe plus : le rendu d'interface est
passe en mode retenu, un ecran **extrait** son etat dans un `GuiGraphicsExtractor` que le moteur
rend ensuite. `renderBg` devient `extractBackground`, et `blit` prend un pipeline
(`RenderPipelines.GUI_TEXTURED`) en premier argument. C'est le premier ecran du mod, et le
risque reste contenu : un ecran de conteneur ne touche pas au pipeline de soumission en monde,
qui est la vraie difficulte de cette version.

**Le compteur du crusher, branche apres la sonde.** Le crusher accumule des fruits par recette
avant de rendre quoi que ce soit — **jusqu'a 128 pour l'orangeblue**. C'etait l'etat le plus
volumineux du chapitre et il n'etait visible nulle part : un joueur ne pouvait pas savoir s'il
en etait au troisieme ou au cent-vingtieme fruit. Je ne l'ai pas branche a l'aveugle : une
sonde a d'abord liste `AbstractContainerScreen`, `GuiGraphicsExtractor` et `Font`.

Ce qu'elle a donne, et qu'aucune memoire ne pouvait donner :

- **`renderLabels` n'existe plus.** Le texte d'un ecran de conteneur se place dans
  `extractContents`, qui dessine les cases et les libelles. Ecrire le compteur dans
  `extractBackground` l'aurait mis **sous les objets**.
- **`getGuiLeft` / `getGuiTop` sont marques pour suppression**, remplaces par `getLeftPos` /
  `getTopPos`. Le premier jet compilait avec deux avertissements ; corrige.
- `GuiGraphicsExtractor.centeredText(Font, String, int x, int y, int color)`, ou `x` est le
  centre. Et `getFont()` est public sur l'ecran.

Le compteur affiche le bac de la recette chargee ; machine vide, celui qui est le plus avance,
plutot que rien — un joueur qui revient apres une pause veut savoir ou il en etait, pas
decouvrir un zero qui lui ferait croire son travail perdu. Tous les bacs a zero : rien
d'affiche, parce qu'un « 0/0 » vaut moins que le vide. La position du compteur est verifiee par
le meme garde-fou que les cases.

### 2026-11-08 (suite) — le mod ne demarrait plus, et pourquoi la CI ne pouvait pas le voir

**Crash au lancement du chapitre 4** : `ExceptionInInitializerError` pendant la construction du
mod. Cause trouvee en lisant le code, pas le rapport — celui-ci ne porte pas la trace de
l'initialiseur.

Dans `PalaMachines`, le champ `BLOCK_ITEMS` appelait `blockItems()`, qui parcourait
`ORDERED_BLOCKS` **declare trois lignes plus bas**. Les champs statiques s'initialisent dans
l'ordre du fichier : la liste valait encore `null`.

**Ce qui rend ce bug mauvais, c'est qu'il est invisible a la compilation.** Une lecture
*directe* d'un champ declare plus bas est refusee par le compilateur (« illegal forward
reference ») ; **passer par un appel de methode contourne la regle**. Le jar se construit, la CI
est verte, et le jeu refuse de demarrer. Reproduit a l'identique sur un exemple minimal de six
lignes, puis verifie que la forme corrigee, elle, est bien rejetee a la compilation.

**Deux parades, posees ensemble.**

1. **La liste est passee en argument** a `blockItems(ORDERED_BLOCKS)`. Le compilateur retrouve
   alors la forward reference : remonter le champ devient une **erreur de compilation** au lieu
   d'un plantage au demarrage.
2. **`tools/check_static_init.py`** suit le meme chemin que la JVM — champ statique, methode
   appelee, champs qu'elle lit — et signale ceux declares plus bas. Verifie en remettant la
   faute : il la nomme et dit quoi faire.

Lecon pour la suite : la CI de ce projet valide la **compilation**, pas le **chargement**. Tout
ce qui se passe entre les deux — ordre d'initialisation statique, enregistrements, datapacks —
ne se voit qu'en jeu. Les controles de `tools/` existent pour couvrir cet angle mort, un cas a
la fois.


### 2026-11-08 (suite) — chapitre 4 : cobblebreaker et crusher

**Le socle des block entities est pose**, et avec un choix qui tient tout le chapitre.

**1. L'interface d'une machine est un coffre vanilla.** Une machine demande normalement un
`AbstractContainerMenu` **et un ecran cote client** — donc du code de rendu, que ce mod evite
depuis le depart. En adossant l'inventaire de la machine a un `ChestMenu`
(`ChestMenu(MenuType, int, Inventory, Container, int)`, verifie a la sonde), on obtient une
interface fonctionnelle, compatible shift-clic et entonnoirs, **sans une ligne de code client**.

Ce qu'on y perd : pas de barre de progression dessinee. Les machines disent donc leur etat
autrement — et c'est pour ca que **le palier du cobblebreaker est une propriete d'etat du
bloc** : la machine change de texture quand on l'ameliore, donc le joueur voit son palier de
l'exterieur. Ce n'est pas cosmetique, c'est ce qui remplace la jauge.

**2. Le cobblebreaker, au tick pres.** Cadences 400 / 266 / 160 / 100 ticks, et un tirage sur
250 dont le wiki ne dit pas un mot :

| Tranche | Lot | Chance |
|---|---|---|
| [0 ; 0,111[ | paladium | 0,044 % |
| [0,111 ; 3[ | titane | 1,16 % |
| [3 ; 7[ | amethyste | 1,60 % |
| [7 ; 12[ | diamant | 2,00 % |
| [12 ; 20[ | or | 3,20 % |
| [20 ; 30[ | fer | 4,00 % |
| [30 ; 250[ | **rien** | **88 %** |

Une pierre sur huit rend quelque chose. Il faut en moyenne **20 250 pierres** pour un lingot de
paladium : le cobblebreaker est une rente de fond, pas une source de metal. Les multiplicateurs
annonces par le wiki (x1,5 / x2,5 / x4) tombent exactement juste a partir des cadences, ce qui
les confirme l'un l'autre.

**3. Le crusher, et une adaptation assumee.** 100 ticks par fruit, quatre recettes conformes au
wiki. L'original demandait de **cliquer un bouton** pour vider un bac plein ; sans interface
dessinee, le lot tombe automatiquement dans l'emplacement de sortie. Meme nombre de fruits pour
le meme nombre de lingots, un clic en moins.

Les deux machines ne traitent pas le manque de place pareil, et c'est voulu : le cobblebreaker
perd son lot quand le bac est plein (comportement d'origine, le cout est une pierre), le
crusher refuse de broyer le dernier fruit si le lot ne tient pas (le cout serait des dizaines
de minutes de recolte).

**4. Le totem passe au chapitre suivant, et je dis pourquoi.** Ses deux constantes de cadence
(`waiting` 1000, `timeforgrowth` 10000) sont **ambigues** : millisecondes ou ticks, un facteur
20 d'ecart sur l'equilibrage. Il vaut mieux l'etablir que le deviner.

**5. Deux defauts trouves dans l'outillage, et corriges.**

- `tag()` **ecrasait** au lieu d'accumuler. Minerais, blocs de stockage et machines ecrivent
  tous dans `mineable/pickaxe` : le dernier appel effacait les precedents. Un tag efface ne
  produit aucune erreur — juste un bloc qui se casse a la main a vitesse normale.
- `check_assets.py` ne lisait que le paquet racine (`glob` au lieu de `rglob`). Les machines
  vivant dans un sous-paquet, **tous leurs assets etaient declares morts a tort**. Le controle
  a donc attrape sa propre lacune, ce qui est exactement son role.

**6. Les verrous de metier cessent d'etre inertes.** `data/jobs.json` nommait
`palamod:cobblebreaker` et `palamod:crusher` depuis le chapitre 1 ; aucune ligne de logique de
verrou n'a ete ecrite ici. `PalaMachineEvents` lit `isCanceled()` avant d'ouvrir l'interface,
sinon le refus s'afficherait et l'interface s'ouvrirait quand meme.


### 2026-11-08 (suite) — terre fertilisee, et bascule sur le pack officiel

**Deux corrections demandees par le joueur apres essai en jeu.**

**1. La terre fertilisee manquait, et c'etait une omission.** Contrairement au totem, c'est un
bloc simple sans block entity : elle appartenait bien au chapitre des cultures. Son mecanisme
est dans `BlockConnectedDirt` et n'est **nulle part sur le wiki** — a chaque tick aleatoire, le
bloc fait passer **deux ticks de croissance supplementaires** a la plante du dessus. Avec celui
qu'elle recoit deja, elle avance environ **trois fois plus vite**.

Deux variantes, comme le booleen `tilled` d'origine : la brute porte l'herbe, les fleurs et les
pousses d'arbre, la labouree porte les cultures, et la houe fait passer de l'une a l'autre.
**Les deux accelerent** — la methode n'est pas conditionnee au booleen, donc la version brute
nourrit aussi les arbres.

Trois API trouvees a la sonde avant d'ecrire : `UseHoeEvent` est remplace par
`BlockToolModificationEvent` (avec `ItemAbilities.HOE_TILL`), NeoForge a **conserve**
`canSustainPlant` (il rend un `TriState`), et `BlockState.randomTick` est public — ce qui
compte, parce que la methode `randomTick` du bloc est protegee et donc inaccessible depuis une
autre classe. C'est par la que le serveur tique lui-meme les blocs.

Casser l'une ou l'autre variante rend de la terre **brute** : un coup de houe mal place ne
coute rien.

**Une seule recette inventee dans tout le portage, et elle est signalee.** Le PalaMod n'en
donne aucune — sur le serveur, la terre fertilisee s'achetait. Plutot que de la laisser
inaccessible hors creatif : un bloc d'os entoure de huit terres donne huit blocs. Thematique
(des os, donc de l'engrais), calibre pour une ferme, et dans un fichier de datapack donc
modifiable sans recompiler. Elle n'accelere que la croissance, jamais le rendement.

**2. Les textures viennent desormais du pack officiel, plus du jar.** 60 des 64 textures en
sortent ; le jar ne sert plus que pour les quatre que le pack n'a pas (`connectedDirt`,
`trixium_block`, `compressed_endium`, `particle_green_paladium`).

Benefice inattendu : **le pack corrige des defauts du jar**. C'est lui qui porte le bon
`endium_block` en 16x16, la ou le jar traine trois homonymes dont deux en 15x15 — le piege du
correctif precedent. Le repli n'est jamais silencieux : le generateur compte la provenance et
nomme chaque fichier repris au jar.


### 2026-11-08 — chapitre 3 : les cultures, et un mecanisme absent du wiki

**Le vrai mecanisme des cultures n'est pas celui que le wiki decrit.** Lu dans
`BaseBlockCrops.getDrops` : **a maturite, un tirage sur 10 000 decide entre un lingot et le
fruit** — le lingot *remplace* le fruit. Aubergine 8 % → lingot d'amethyste, cerfeuil 5 % →
titane, kiwano 2 % → paladium, orangeblue 0,25 % → pepite d'endium.

L'agriculture est donc une **voie d'acces aux metaux a part entiere**, parallele au minage. Ca
explique enfin pourquoi le crusher est verrouille sur le Fermier et non sur le Mineur, ce qui
semblait incoherent au chapitre 1.

**L'orangeblue ne s'auto-entretient pas** : un second tirage, sur 30, ne rend sa graine que
6,67 % du temps. Delibere — c'est la seule source d'endium du jeu.

**Une culture immature ne lache rien**, pas meme sa graine. Plus dur que le ble vanilla, et
reproduit tel quel : casser une pousse par erreur coute la graine.

**Le compromis de portage, et ce qu'on fait pour le payer.** `CropBlock` est cable sur `AGE_7`
(8 stades) en 26.2 et n'expose plus de `getAgeProperty()`. L'aubergine et le cerfeuil ont 8
stades d'origine : exacts. Le kiwano et l'orangeblue en ont 10 — les ramener a 8 les ferait
murir 20 % plus vite, ce qui n'est pas anodin pour la seule source d'endium. La compensation
porte sur la **cadence** : `PalaCropBlock` ignore 20 % des ticks pour ces deux cultures, donc le
*temps total* de maturation ne bouge pas. Huit etapes visibles, meme attente.

**XP d'agriculture ajoutee, et c'est necessaire et non cosmetique.** Sans elle la chaine de
deblocage se mordrait la queue : un joueur a Fermier 35 qui passe aux aubergines ne gagnerait
plus rien et devrait retourner au ble pour atteindre le cerfeuil au 60. Valeurs **calibrees**
(8 / 15 / 30 / 60) contre les reperes de `skills.json` — vanilla 2 a 3, cultures rares 40.
La condition de maturite de `SkillEvents` s'applique deja sans modification : elle teste
`AGE_7 >= 7`, ce que ces cultures utilisent.

**Le garde-fou d'assets est remplace par une vraie validation de chaine.** L'ancien comparait
des noms ; les cultures n'ont ni item de bloc ni texture a leur nom, ce qui aurait demande des
cas particuliers. `tools/check_assets.py` remonte desormais la chaine comme le jeu la remonte —
enregistrement Java → blockstate → modele → texture, plus definition d'item, butin et noms
francais. **Aucune** des fautes qu'il cherche ne fait echouer la compilation. Verifie en cassant
les trois cas a la main : texture de modele absente, definition d'item supprimee, blockstate
pointant sur un modele inexistant. Les trois sont remontes.

**Trois API changees en 26.2, trouvees a la sonde avant d'ecrire** : `ItemNameBlockItem`
n'existe plus (les graines sont de simples `BlockItem`, le detour par le nom n'a plus de raison
d'etre maintenant que l'identifiant est explicite) ; `BlockEntityType$Builder` non plus (il faut
le constructeur) ; et `noCollision` s'ecrit avec **un seul « s »** — la faute de frappe
historique `noCollission` a ete corrigee. Cette derniere aurait coute un aller-retour de CI.

**Decoupage revu** : le totem de fertilite et les cinq seedplanters passent au chapitre 4. Ils
sont annonces dans les cultures mais ce sont des **block entities et de l'automatisation** —
meme surface d'API que le cobblebreaker et le crusher (`BlockEntityType`, tickers,
`ValueInput`/`ValueOutput`). Les regrouper par technologie plutot que par theme fait un
chapitre 4 coherent, et laisse celui-ci livrable tout de suite. Les cultures sans totem sont
deja jouables de bout en bout : semer, attendre, recolter.


### 2026-11-07 (suite) — chapitre 2 : les minerais, et deux formats établis sur preuve

**Prérequis non demandé mais nécessaire** : le cobblebreaker rend des particules, le crusher
consomme des charbons mixés et rend des lingots. Sans ces items, les chapitres machines et
cultures n'ont ni entrée ni sortie.

**37 objets** sous le namespace `palamod` : 6 minerais avec leur génération, 7 blocs de
stockage, 7 particules, 5 lingots + findium/trixium/pépite/fragment d'endium, 4 compressés,
4 charbons mixés, un onglet créatif.

**1. Les assets sont générés, pas écrits.** `tools/palamod_assets.py` produit **206 fichiers**
depuis le jar de PalaMod. Un item demande trois fichiers, un bloc six ; à la main c'est une
faute de frappe garantie, et une faute de frappe dans un chemin de modèle **ne casse pas la
compilation** — elle donne une texture violette découverte des semaines plus tard.

Les textures viennent du **jar de PalaMod**, pas du pack Faithful : ce sont les textures par
défaut du mod, le pack officiel les remplace ensuite. C'est tout l'intérêt du namespace
`palamod`.

**2. Deux formats tranchés sur preuve, pas devinés.** La tâche `gradle packMeta`, ajoutée à la
sonde, lit le jar de Minecraft et celui de NeoForge au lieu de supposer :

- `pack.mcmeta` : MC 26.2 annonce `resource 88.0` / `data 107.1`, et le jar universel de
  NeoForge déclare `min_format` = `max_format` = `[107, 1]`. Ce sont des paires
  `[majeur, mineur]`, et l'ancienne forme `pack_format` + `supported_formats` est abandonnée.
  **Ça ferme un point resté ouvert depuis la phase 0** — et ce n'était pas cosmétique : un
  format refusé fait rejeter le pack entier, donc tout le chapitre.
- Dossiers de données **au singulier** : `loot_table/`, `recipe/`, `tags/`, `worldgen/`, et
  `assets/<ns>/items/` pour les définitions d'item. Listés dans le jar vanilla, pas supposés.

La sonde affiche aussi désormais la **valeur** des constantes et non juste leur type. Ça n'a
pas suffi ici — le `<clinit>` de `SharedConstants` touche au jeu et part en
`NoClassDefFoundError` — d'où la lecture directe du jar.

**3. Le fer, l'or et le diamant n'ont pas de lingot propre.** Leurs particules se craftent en
items **vanilla**. Un fer parallèle au fer du jeu casserait tout l'équilibrage du shop, qui
raisonne sur `minecraft:iron_ingot`. Seules les matières que Paladium ajoute vraiment ont leur
lingot.

**4. Les chiffres viennent du code, sauf un, et c'est dit.** Dureté, résistance et niveau de
pioche sont relevés dans `PWorld` ; les tailles de filon dans `BaseWorldGenerator` (améthyste 8,
titane 6, paladium 4, le reste 3). **Les hauteurs d'origine sont écartées volontairement** : en
1.7.10 le monde allait de 0 à 255 et la deepslate n'existait pas, donc les recopier
concentrerait les minerais au mauvais endroit d'un monde qui descend à −64. Nombre par chunk et
profondeur sont calibrés sur l'analogue vanilla de chaque niveau de pioche.

**5. Garde-fou de CI.** Les assets sont générés, l'enregistrement Java est écrit à la main :
les deux listes peuvent dériver, et une dérive **ne casse pas la compilation**. Un item
enregistré sans définition d'item sort violet ; un asset sans enregistrement est un fichier
mort. La CI compare les deux sens, vérifie les tables de butin, et refuse de passer à vide si
les motifs ne reconnaissent plus les fabriques.

**6. Trouvaille** : le code a **sept** particules de minerai, le wiki en annonce six. La
particule de paladium vert existe, sa texture est dans le jar, elle est enregistrée.

**Pas encore fait, volontairement** : les recettes des charbons mixés. Le wiki donne leurs
rendements (or ×6, améthyste ×8, titane ×8, paladium ×1) mais pas leurs ingrédients. Elles
arrivent au chapitre des machines, où le `CraftRegistry` de PalaMod les donnera — les inventer
ici serait un chiffre de plus à défaire.

**Le seul point non vérifiable sans lancer le jeu** : le chemin du modificateur de biome
NeoForge (`data/palamod/neoforge/biome_modifier/ores.json`). Le jar de NeoForge n'en embarque
aucun, donc rien à comparer. Si le chemin est faux, seule la génération des minerais est
muette — le reste du chapitre tient.

### 2026-11-07 — chapitre 1 : les métiers, et la vérité prise au bytecode

**Demandé** : convertir le PalaMod en 26.2, par chapitres, en commençant par les métiers.

**1. Les métiers sont un nom donné aux skills, pas un système de plus.** Choix du joueur
entre trois options. La conséquence pratique est qu'il n'y a **rien à sauvegarder** :
`JobApi.level()` délègue à `SkillApi.levelOf()`, donc aucun attachment, aucune migration, et
aucun moyen que les deux chiffres divergent. Un neuvième skill, `alchemy`, est créé pour
l'Alchimiste, qui était le seul métier sans équivalent.

**2. Le wiki ne suffisait pas, et il se trompe.** La première version de `data/jobs.json`
était écrite depuis le wiki. Le joueur a ensuite fourni `PalaMod-10.3.0.pala` : les vraies
tables y sont, `addBlacklistedCraft(niveau, item)` et `addBlacklistedUsage(niveau, item)`,
72 et 53 entrées. Deux scripts (`tools/extract_jobs.py`, `tools/palamod_jobs.py`) les lisent
au bytecode et génèrent le datapack — **70 verrous**, jamais écrits à la main.

Écart trouvé au passage : le wiki place le totem de fertilité au niveau 55, le code au 70.

**3. Le facteur d'échelle est vérifié, pas supposé.** PalaMod compte 20 niveaux par métier,
les skills 100 : les niveaux sont multipliés par 5. Deux contrôles indépendants contre le
wiki Java, lui déjà gradué sur 100 : cobblebreaker niveau 5 → 25, annoncé 25 ; crusher
niveau 8 → 40, annoncé 40. Deux correspondances exactes.

**4. Le verrou ne bloque pas le craft, et c'est un choix.** Paladium le bloquait
(`JobsConstants.CRAFTING_NOT_ALLOWED`). La sonde d'API a établi que
`PlayerEvent.ItemCraftedEvent` **n'implémente pas `ICancellableEvent`** en 26.2. Il ne reste
que des contournements, et tous détruisent les ingrédients déjà consommés — principe 4,
rien d'irréversible. Donc on verrouille la **pose** (`BlockEvent.EntityPlaceEvent`, annulable,
l'annulation rend l'item) et l'**usage** (`PlayerInteractEvent.RightClickBlock` et
`RightClickItem`). Une machine qu'on ne peut ni poser ni actionner est verrouillée pour de
bon ; l'objet attend dans un coffre, ce qui est plus doux que l'original sans être plus
permissif sur ce qui compte.

Anti-spam du refus : un clic droit maintenu part plusieurs fois par seconde, le message
noierait le chat. 40 ticks par joueur, indexés par UUID comme `SkillBossBar`.

**5. Les chapitres suivants n'écriront aucune logique de verrou.** Ils enregistrent leurs
blocs et ajoutent une ligne au datapack. Corollaire assumé : les 70 entrées nomment des items
qui n'existent pas encore, donc elles sont **inertes**. Le chargement les signale et
`/metiers audit` les liste — même filet que `UpgradeApi.EFFECTS` et
`SkillConfig.IMPLEMENTED`, posé pour la même raison : un verrou déclaré et jamais appliqué
fait croire que la porte est fermée.

**6. Garde ajouté dans `SkillsMenu`.** La grille était câblée pour huit skills
(`SLOTS_GRID.length == 8`) en lisant `Skill.values()[index]` : le neuvième aurait disparu de
l'écran **sans aucune erreur**. Grille passée en 3×3, et un débordement s'annonce désormais
au lieu de se taire.

**7. Sondes d'API lancées avant d'écrire**, six classes puis quatre. Confirmé :
`PlayerBrewedPotionEvent` existe et porte `getStack()` ; `RightClickBlock` et
`RightClickItem` sont annulables et exposent `getUseBlock`/`getUseItem` en `TriState` ;
`ItemStack.is(TagKey)` existe. C'est ce qui a permis de trancher le point 4 avant d'écrire
une ligne, au lieu de le découvrir à la compilation.

Spec complète de ce qui a été extrait : [`docs/PALAMOD.md`](docs/PALAMOD.md).

### 2026-11-06 (fin) — zéro perk inerte, et un puits à argent permanent

Carte blanche donnée, hors « raisons de quitter la base ».

**1. Les 33 perks inertes sont réglés — il en reste 0 sur 160.**

Six types sont désormais implémentés dans `skill/SkillEffects` :
`harvest_bonus`, `tree_chop`, `loot_bonus`, `treasure_chance`,
`no_fall_damage_below`, `anvil_cost_reduction`.

Six types sont **inimplémentables en 26.2** et ont été remplacés dans le
datapack par des perks équivalents qui fonctionnent. Ce que la sonde a établi :
pas d'évènement de durabilité, pas de `BonemealEvent`, **pas de
`BlockEvent.BreakEvent`** (il a disparu), et la vitesse d'un four ne se rattache
à aucun joueur. Donc :

| Type retiré | Devient | Pourquoi ce choix |
|---|---|---|
| `fishing_speed` | `double_drop_chance` sur les prises | Même effet économique, et le libellé ne mente pas |
| `tool_durability` | `attribute block_break_speed` | Un outil entretenu travaille plus vite |
| `smelt_speed` | `attribute armor` | Un forgeron porte ce qu'il forge |
| `smelt_fuel_efficiency` | `attribute armor_toughness` | idem |
| `scaffold_assist` | `attribute step_height` | Monter d'un bloc sans sauter : ce que l'échafaudage apportait |
| `bonemeal_efficiency` | `harvest_bonus` | Rejoint une échelle implémentée |

**Piège évité dans `tree_chop`** : `destroyBlock` refait passer chaque bûche par
`BlockDropsEvent`, donc l'abattage se serait rappelé pour chaque bûche qu'il
venait de casser — récursion exponentielle sur un grand arbre. Verrou statique
`felling`. Les *autres* effets continuent de s'appliquer aux bûches abattues
(XP et collections comptent l'arbre entier) ; seule la propagation est bloquée.

L'abattage respecte aussi le suivi des blocs posés : un plancher de bois à côté
de l'arbre ne part pas avec lui.

**Duplication supprimée** : `SkillsMenu` tenait sa propre liste des types actifs
en face de `SkillConfig.IMPLEMENTED`. Deux listes auraient dérivé, et c'est
l'écran qui aurait fini par mentir. Une seule source désormais,
`SkillConfig.isImplemented`.

**2. Arbre d'améliorations permanentes** (`upgrade/`), huit branches,
24 paliers, 14,83 G pour tout monter.

C'était le trou de conception le plus sérieux du mod : tous les autres puits à
argent sont consommables ou ponctuels — booster, caisse, assurance — et
l'autosell à 500 M est un achat **unique**. Passé le mois 4, l'argent n'avait
plus de destination.

**Recalibré une fois** : la première version chiffrait **328 G** (escalade ×4,
cinq paliers sur certaines branches), soit plus de seize mille heures même à
20 M/h. Les derniers paliers auraient été du contenu mort — pire que pas de
palier. Ramené à ×2,6 avec moins de paliers ; le premier palier de chaque
branche tient entre 25 M et 400 M, donc accessible dans les premiers mois.

**Une branche abandonnée parce qu'impossible** : elle devait ajouter des rangées
au coffre de vente, mais un menu de coffre plafonne à six rangées et le coffre
de vente y est déjà. Devenue « Negoce » (remise d'achat).

**La règle posée** : `UpgradeApi.EFFECTS` liste les huit effets que le code lit
réellement, et le chargement du datapack prévient pour tout effet absent. Même
filet que pour les perks, posé pour la même raison — une amélioration payée
plusieurs milliards doit faire quelque chose.

Deux garde-fous d'équilibrage : « Résilience du marché » est volontairement la
plus chère car elle atténue le seul vrai frein de l'économie, et son maximum
fait **mordre la borne `MAX_FLOOR`** de 0,75 (plancher cumulé 0,85 sur un
`FOUND`) — le frein survit donc à tous les achats possibles, par construction.
Et « Plafond de banque » relève le plafond et **jamais le taux**, sinon attendre
redeviendrait plus rentable que jouer.

### 2026-11-06 (soir) — barre de skills, 100 niveaux, 25 paliers

**1. Barre de boss pour les skills** (`skill/SkillBossBar`). Le point technique
qui décide tout : `ServerBossEvent` est **entièrement côté serveur** — le jeu
envoie les paquets, le client vanilla dessine. C'est donc le seul affichage
riche en haut de l'écran qui n'ajoute pas une ligne de code client, et le mod
reste avec un seul morceau de rendu (le HUD de solde), ce qui le met à l'abri
des refontes de rendu comme celle de 26.2.

Sondé avant d'écrire, et bien vu : le constructeur exige un **UUID**
(`ServerBossEvent(UUID, Component, BossBarColor, BossBarOverlay)`), ce que la
signature habituelle à trois arguments ne laissait pas deviner. Valeurs
d'énumération confirmées aussi : 7 couleurs, 5 recouvrements.

Une barre par joueur, renommée au changement d'activité. S'efface après 5 s sans
XP, 8 s sur une montée de niveau. État transitoire, hors sauvegarde — une barre
est de l'affichage, pas une donnée de jeu.

Les entrées sont retrouvées **par UUID** et non gardées comme références de
joueur : un joueur déconnecté n'est plus dans la liste, son entrée est jetée. Pas
besoin d'écouter la déconnexion, et aucune référence à une entité morte ne
traîne. `removeAllPlayers()` évité — il n'était pas dans la sonde.

**2. Skills à 100 niveaux**, avec recalibrage obligatoire. Garder `lvl^1.8`
aurait donné **961 h pour un seul skill** à 15 k XP/h, contre 140 h pour le
niveau 50 — trois fois la saison entière pour un huitième du contenu. Exposants
descendus : XP 1,8 → **1,6**, argent 1,5 → **1,4**.

Résultat : le 50 devient un jalon de milieu de partie (69 h au lieu de 140), le
100 l'horizon longue durée (412 h). Les 8 skills au maximum rapportent 160 M
cumulés, toujours sous les 500 M de l'autosell — le principe « un skill est un
bonus, pas un revenu » revérifié chiffre en main.

**20 perks par skill** au lieu de 10. Les 10 ajoutés n'utilisent **que les trois
types réellement appliqués** (`attribute`, `double_drop_chance`, `xp_bonus`) :
un perk annoncé dans l'interface mais sans effet en jeu est pire que pas de
perk. `nuclear` n'en avait **aucun** — il reçoit une échelle complète de 20.

Garde ajouté dans `SkillsMenu` : 20 perks pour 21 emplacements, ça tient de
justesse, donc le dernier emplacement annonce le débordement plutôt que de
laisser des perks disparaître sans bruit si la table s'allonge.

**3. Collections à 25 paliers.** Tête raide conservée (×2 à ×5 sur les dix
premiers), queue dense ajoutée (×1,11 sur les quinze suivants) : la tête est la
progression, la queue est la durée de vie. Un palier de queue vaut 10 à 60 h
d'activité ciblée. Les valeurs de tête n'ont pas bougé, pour ne pas déplacer le
palier des collections déjà entamées.

Le bonus de plancher passe en **courbe concave** jusqu'à 0,30 : `+0,060` au
premier palier, `+0,003` au dernier. Il ne suit délibérément pas le nombre de
paliers — vingt-cinq crans de 0,03 auraient donné 0,75, soit la borne entière
(voir `MAX_FLOOR`, resserrée ce matin) consommée par les seules collections.

Les exceptions gardent leur longueur propre : un item rare a moins de paliers,
donc moins de bonus.

### 2026-11-06 — dix paliers, vente libre, NTM par rareté

**1. Dix paliers de collection** au lieu de six. Trouvables jusqu'à 50 000,
farmables jusqu'à 5 M. Les quatre ajoutés sont des objectifs de durée de vie.

Le bonus de plancher ne monte que de **0,03 par palier** (au lieu de 0,05) :
c'est la variable qui peut éteindre le frein des prix dynamiques, donc elle est
la dernière à augmenter quand on allonge une échelle.

**2. Le verrou de vente par collection est retiré** (demandé). Tout item
vendable l'est dès la première unité. `sell_requires_collection` dans
`collections.json` le restaure sans toucher au code — la logique est conservée,
pas supprimée.

Conséquence traitée : le palier 0 ne donnait *que* ce déverrouillage. Il porte
désormais un bonus de plancher comme les autres, sinon il devenait vide.

**3. Bug trouvé en chemin** : le plancher cumulé (tier + collection + prestige)
était borné à **0,95**, c'est-à-dire nulle part. Un `RARE` à collection complète
et prestige maximal atteignait 0,90 — à ce niveau la dix-millième unité vendue
rapporte encore le plein tarif, ce qui est exactement ce que les prix dynamiques
doivent empêcher. La borne existait donc sans couvrir le cas qu'elle visait.
Resserrée à **0,75** (`DynamicPrice.MAX_FLOOR`), vérifié chiffré.

**4. NTM NEXT passe à deux dimensions.** Un tag ne dit que la *forme* d'un item,
donc un lingot de schrabidium coûtait un lingot de cuivre. Le prix devient
**ancre de la rareté × facteur de la forme**.

| Rareté | Matières | Lingot (achat) | Brute (vente) |
|---|---|---|---|
| Commune | uranium, titane, cuivre, plomb, aluminium, fluorite, soufre | 4 500 | 100 |
| Peu commune | thorium, tungstène, béryllium, cobalt, lithium | 112 500 | 800 |
| Rare | terres rares, niobium | 2,80 M | 6 400 |
| Très rare | schrabidium, australium, osmiridium | 70,00 M | 51 200 |

**Deux échelles, et c'est le point de conception.** L'achat monte de ×25 par
cran (4 500 × 25³ = 70,3 M, d'où les 70 M demandés), la vente de ×8 seulement.
Un achat est un puits qu'on ne peut pas exploiter ; une vente est un revenu
qu'on peut industrialiser. L'asymétrie va de ×30 au commun à ×911 au très rare.

Le rattachement se fait par **mot-cherché dans le chemin de l'identifiant**, pas
par identifiant complet : on ne sait pas si le mod nomme ses items
`uranium_ingot` ou `ingot_uranium`, et les deux contiennent « uranium ». Le
mot-clé le plus long gagne, sinon l'ordre d'itération d'une map décidrait du
prix de `rare_earth_ingot`.

**Australium et osmiridium ne se vendent jamais**, pas même en brut : le joueur
a lui-même signalé qu'ils « ne viennent que de sources spécifiques », donc ils
sont *productibles*, donc vendables = imprimante à billets. C'est exactement
l'exploit que la règle « seule la matière brute se vend » ferme ailleurs. Le
schrabidium a un minerai, sa brute reste vendable.

**Validation chiffrée** : la colonne commune reproduit l'ancien barème déjà
calibré (brute 3 000, lingot 4 500, plaque 7 200, nugget 500, cristal 13 500) —
bon signe que le modèle est le bon. Neutralité 9 nuggets = 1 lingot tenue à
0,01 % près aux quatre raretés. Revenu réel de la brute très rare calculé sur la
courbe dynamique : 2,62 M pour 64 unités, 21,2 M pour 1 024, plancher atteint à
304 unités écoulées.

Une matière inconnue retombe sur la règle de forme, au prix d'une matière
courante : si NTM ajoute un métal, le mod continue de fonctionner.

### 2026-11-05 — les cycles passent en temps réel

**Signalé par le joueur** : « si je me déconnecte un soir à 20h et que le timer
des contrats est de 24h, j'aimerais qu'il s'actualise ». Ce n'était pas une
préférence mais un **bug de conception** : `reroll_on_playtime_hours: 24`
comptait 24 h **de jeu**, soit douze soirées à son rythme. Les contrats « du
jour » se renouvelaient donc deux fois par mois.

**Tranché avec le joueur** (trois questions) : renouvellement à **heure fixe**
plutôt que 24 h après le dernier tirage, intérêts de banque en temps réel **avec
taux recalibré**, et remontée des prix en temps réel.

**La règle retenue** — deux horloges, et le choix dépend de ce qu'on mesure :

| Temps réel (cycles) | Temps de jeu (consommé en session) |
|---|---|
| Contrats du jour et de la semaine | Durée d'un booster (20 min) |
| Demande du jour | Intervalle de l'autosell (5 min) |
| Bonus de première vente | |
| Intérêts d'épargne | |
| Oubli de la pression sur les prix | |

Un booster payé 300 M ne doit pas fondre parce que la connexion a sauté, et
l'autosell n'a pas de sens joueur absent. Tout le reste modélise le monde, pas
l'activité du joueur, donc tourne monde fermé.

**Clés de période, pas comptes à rebours** — `util/RealClock` : on stocke la clé
du jour (ou de la semaine) où le tirage a eu lieu, et on retire dès que la clé
courante diffère. Trois conséquences voulues : le renouvellement tombe à heure
fixe donc il est anticipable, sauter trois jours ne produit qu'un seul tirage et
non trois de rattrapage, et se connecter à 19h mardi trouve bien les contrats de
mardi. Bascule à **5 h** locales : à minuit, une soirée qui dépasse l'heure
verrait ses contrats se renouveler sous son nez.

**Rattrapage hors ligne** — `economy/OfflineCatchUp`, branché à la connexion.
`ContractData.lastSeenMinute` est réécrit chaque minute en jeu, donc une
déconnexion brutale ne fait pas recompter une période déjà créditée. L'oubli du
marché se calcule en une fois : le facteur est multiplicatif, donc l'élever à la
puissance du nombre de minutes donne exactement le résultat de la boucle, sans
la boucle. Plafond de trente jours — pas de l'équilibrage, une protection
d'horloge contre une date système changée ou une sauvegarde recopiée.

**Recalibrage de la banque** : 2 %/jour de jeu → **0,25 %/jour réel**, plafond
2 M → **250 k**. Le rapport est celui d'une journée entière à une journée de jeu
(~3 h), soit ~12. Sans ce recalibrage, attendre devenait douze fois plus
rentable que jouer — précisément ce que le compteur en temps de jeu voulait
éviter. Le revenu par soirée est inchangé ; il ne dépend plus du moment où on
joue.

**Constat au passage** : la crainte d'origine sur l'oubli des prix (« le temps
réel avantage le week-end ») était l'inverse de la réalité. En temps de jeu, la
remontée exigeait *elle aussi* d'être en jeu, donc un prix écrasé le dimanche le
restait douze soirées et il fallait grinder sa propre remontée.

**Migration des sauvegardes** : les champs du codec sont renommés
(`daily_rolled_at` → `daily_period`, etc.), donc un fichier existant retombe sur
`-1` et provoque un tirage neuf à la première connexion. C'est le comportement
voulu.

### 2026-11-05 — refonte des interfaces

**Demandé** : une interface pour les contrats avec les noms et icônes des items
requis (« je n'aime pas trop voir `minecraft:stone_block` »), une interface
d'achat des boosters quitte à remplacer la catégorie Composants du shop, des
interfaces pour les skills et les collections, et un revamp de l'ensemble pour
que tout se ressemble, façon SkyBlock.

**Socle commun** — `ui/Gui.java` et `ui/DisplayMenu.java`. Sept écrans écrits
chacun à sa façon finissent par avoir sept conventions : le joueur réapprend
chaque fenêtre et une correction de style demande sept modifications. Le socle
fixe une fois pour toutes :

- `Gui.displayName(Identifier)` → le nom traduit de l'item, et seulement si
  l'item est absent du registre, le chemin embelli. **C'est le correctif de la
  demande : plus un seul identifiant brut dans une interface.**
- `Gui.button` / `filler` / `bar` / `field` / `hint` / `action` / `good` / `bad`
  — les briques de vignette, et une barre de progression en caractères pleins.
- `DisplayMenu` — conteneur d'affichage pur : `super.clicked` n'est jamais
  appelé, `quickMoveStack` rend vide. La sûreté (aucun item déplacé, dupliqué ou
  perdu) est acquise par héritage et non re-décidée par écran. `frame()` encadre
  l'écran, `footer()` remplit la dernière rangée, `open()` enchaîne vers un
  autre écran.

**Écrans neufs** : `ui/Hub` (`/menu`, le menu central, chaque vignette portant
le chiffre qui dit s'il faut y aller), `contract/ContractsMenu`,
`skill/SkillsMenu` (grille des huit skills + fiche détaillée avec les perks),
`collection/CollectionsMenu` (catégories → grille, prestige au clic droit),
`boost/BoutiqueMenu` (boosters, caisses, assurance, autosell, banque).

**Noms de contrats** : `label` et `icon` facultatifs sur chaque entrée de
`contracts.json`, renseignés pour les 28 contrats, portés par
`ContractConfig.Entry` puis `ActiveContract`. `ContractDisplay` les utilise avec
une dérivation de secours à trois niveaux — intitulé du datapack, nom de l'item
ou de l'entité visée, verbe générique du type. `ContractApi.describe` y délègue
pour que le chat et l'interface nomment un contrat de la même façon.

**Trouvaille** : la catégorie `COMPONENT` n'a jamais contenu d'item. L'onglet du
shop était vide depuis la phase 3, et l'aile de musée correspondante ne pouvait
jamais se compléter — sa remise d'achat de −10 % était donc inatteignable.
`ShopCategory` passe à cinq onglets, la boutique prend la place libérée, et la
remise est passée sur l'aile NTM (deux récompenses pour l'aile la plus longue du
mod).

**Corrigé au passage** : dans `ShopMenu.buy`, le garde testait `unitPrice <= 0`
après un `Math.max(1, …)` — donc jamais vrai. Un item à `buy_price` nul pouvait
être acheté pour 1. Le garde teste désormais `entry.buyPrice()`.
`Boosters.buy` annonçait son prix en chiffres bruts, sans `MoneyFormat`.

**Écrans migrés** sur le socle : `ShopMenu`, `BankMenu`, `MuseumBrowseMenu`.
`SellChestMenu` et `MuseumMenu` restent des conteneurs de dépôt réels — ils ne
peuvent pas hériter de `DisplayMenu`, qui refuse tout mouvement d'item.

**Commandes** : `/menu` et `/boutique` ; `/contracts`, `/skills` et
`/collections` ouvrent maintenant leur interface, le texte passant sous `list`,
`list` et `held`.

**Vérification locale** : `javac` 21 sans classpath Minecraft, en filtrant les
erreurs de résolution — ça ne valide rien de l'API 26.2 mais ça élimine les
fautes de syntaxe avant de dépenser un cycle de CI. À refaire à chaque session.

### 2026-11-04 — phase 10, NTM NEXT

**Lu dans le source du mod, pas deviné.** NTM NEXT déclare proprement les tags de
convention : `c:raw_materials` (14 types), `c:ingots` (92), `c:dusts` (77),
`c:nuggets` (48), `c:plates` (13), `c:gems` (12), `c:crystals` (2). Ses items
suivent `hbm:ingot_uranium`, `hbm:raw_titanium`.

**Conséquence de conception : une règle par tag, pas une ligne par item.** Lister
des centaines d'items serait à refaire à chaque version du mod. Et si le mod n'est
pas installé, les tags sont vides et rien n'est généré — **aucune dépendance de
compilation, aucun chargement conditionnel, aucune classe du mod référencée**.

**Décision d'équilibrage : NTM ne vend que sa matière brute.** Ses chaînes de
traitement multiplient la matière ; rendre lingots, poudres ou plaques vendables
transformerait ce rendement en revenu infini. Le reste est en achat seul. Exploit
fermé par construction, sans audit recette par recette.

`prices.json` reste toujours prioritaire sur une règle de tag, et l'ordre des
règles dans le fichier fait la priorité.

`/dohy scan [namespace]` liste les items enregistrés sans prix : c'est l'outil
pour intégrer **n'importe quel** mod futur depuis la réalité du registre.

Signature 26.2 ajoutée à la table : `ResourceKey.location()` a disparu avec le
renommage en `Identifier` — passer par `Registry.getKey(value)`.

### 2026-11-04 — interface de banque, phases 8 et 9

**Interface de banque** (demande du joueur). Coffre de 3 rangées, montants
proposés en parts du solde (10 %, 50 %, tout) plutôt que saisis au clavier.
`/bank` ouvre l'interface, `/bank info` garde l'affichage texte.

**Phase 8, contrats.** Une seule horloge pour tout le mod : un compteur de
**minutes de jeu** dans `ContractData`, qui n'avance que serveur en marche. Rien
n'expire pendant que le monde est fermé — c'est ce qui permet de n'avoir aucune
série à entretenir.
Le test de cible d'un contrat est **fourni par l'appelant** et non résolu dans
l'API : seul l'appelant dispose du sujet, un bloc sait tester ses propres tags et
un compteur de distance n'a pas de cible. `#minecraft:hostile` n'étant pas un tag
vanilla, il est traité à part via l'interface `Enemy`.
Les termes d'un contrat sont recopiés au tirage, pas relus à chaque progression :
si le datapack change, un contrat déjà accepté garde ses termes.
Bonus (demande ×2,5, première vente ×1,5) appliqués **après** le prix dynamique,
la pression enregistrée restant celle de la quantité réellement vendue.

**Phase 9, boosters, caisses, autosell.**
Boosters non cumulables, 20 min de jeu, avec un rappel à 5 minutes de la fin — un
booster payé des millions ne doit pas expirer inaperçu.
Caisses : tables de butin dans le datapack, calibrées pour rendre un peu moins
que leur prix en moyenne, donc un puits et non une source.
**Autosell sur coffre vanilla lié**, pas sur un bloc du mod : un bloc dédié
demanderait enregistrement, modèle, texture et table de butin pour une mécanique
qui n'a besoin que d'une position. Le passage **ne charge jamais le chunk** —
forcer le chargement ferait tourner une ferme à distance en permanence.

### 2026-11-05 — longévité : prestige et musée

Deux systèmes choisis pour allonger la durée de vie du monde, hors plan initial.

**Prestige des collections.** Une collection complète se remet à zéro contre un
gain permanent. Le gain porte sur le **S0** — la quantité écoulable avant que le
prix tombe — et non sur le prix de base : augmenter le prix serait inflationniste
sans fin, élargir le S0 est auto-limité puisque le plafond reste le prix de base.
Mesuré sur le blé : 4 350 unités écoulables avant de tomber à 50 % du prix au
prestige 0, contre 15 224 au prestige 5. Soit **3,5× le débit, zéro inflation**.
Le coût est réel (le compteur repart à zéro, les bonus de plancher sont à
regagner) ; la vente reste débloquée, un droit acquis ne se reperd pas. Plafond à
5, configurable.

**Le musée.** Un exemplaire de chaque item, donné définitivement. **Par aile et
non par item** : 152 micro-bonus seraient imperceptibles et illisibles, donc six
ailes dont seule la complétion paie, chaque don donnant de l'XP globale.
Décision du joueur : les items du tier `RARE` sont **exclus** des ailes
obligatoires — l'œuf du dragon est unique par monde et exiger son sacrifice
transformerait une collection en source de regret. Ils forment le panneau mécène,
optionnel. La règle porte sur le **tier**, pas sur une liste d'ids, donc elle
reste juste quand le catalogue grandit.
Récompenses limitées aux mécanismes déjà prouvés en jeu : attributs, double drop,
remise d'achat.

**Vitrine navigable** (demande du joueur, style Hypixel). Accueil avec les ailes,
barre de progression, récompense annoncée et valeur exposée ; puis une grille par
aile. Le choix d'affichage qui fait l'intérêt de la vue : une pièce exposée
apparaît telle quelle, une pièce **manquante en vitrail**, donc on voit le trou et
non la liste de ce qu'on a déjà.

**Les dix phases du plan sont faites.** Restent, hors plan :
- les perks de skill non encore appliqués (liste dans le journal au démarrage) ;
- ~~l'équilibrage des intérêts de banque~~ → **corrigé le 2026-11-05** : 2 %/jour
  de jeu plafonné à 2 M (10 M épargnés = 16,7 k par soirée, contre 4,2 k avant), et
  tous les chiffres de la banque et de la mort déplacés dans `data/economy.json`,
  rechargeable à chaud ;
- la liste d'items de DonutSMP (`donut.build/auction`), demandée puis mise de côté ;
- d'autres idées de longévité évoquées et non retenues pour l'instant : chantiers
  de construction (réutiliserait le suivi de blocs posés), événements sur
  l'horloge de jeu, contrats d'exploration géolocalisés, NTM en campagne verrouillée
  par niveau global.

### 2026-11-04 — phases 6 et 7

**Phase 6, skills.** L'XP totale est stockée, pas le niveau : rééquilibrer la
courbe recalcule les niveaux sans migration.
Les modificateurs d'attribut sont **transitoires** et recalculés à chaque
connexion depuis les niveaux. Un modificateur permanent survit à la sauvegarde,
donc le moindre bug de recalcul donnerait des bonus qui s'empilent à l'infini au
fil des connexions, sans retour possible. Même raison pour laquelle une montée de
niveau recalcule tout plutôt qu'un delta.
Les perks d'un même type ne s'additionnent pas, seul le plus haut palier compte.
Les huit attributs de `skills.json` existent bien en 26.2 (sonde) : aucun à
remplacer.
**Perks actifs** : `attribute`, `double_drop_chance`, `xp_bonus`. Les autres sont
chargés, ignorés, et listés dans un avertissement unique au chargement.

**Phase 7, banque et mort.** Pénalité indexée sur les **gains cumulés**, plafonnée
à `50 000 + 1 % des gains`. Trois premières morts gratuites.
Les nouveaux champs de `PlayerEconomy` sont en `optionalFieldOf` : les sauvegardes
antérieures se chargent toujours.
Écart assumé vs `DESIGN.md` : l'assurance est une charge achetée par commande, pas
un objet consommable — un objet demanderait enregistrement, modèle et texture pour
une mécanique qui n'en a pas besoin.

**À rééquilibrer, constat chiffré.** Les intérêts tels que spécifiés sont
négligeables : le plafond de 50 k/jour de jeu mord dès **10 M épargnés**, et une
soirée de 2 h rapporte donc au plus **4 166 $** — 17 % d'une seule récolte de blé.
L'intention de `BALANCE.md` était qu'une soirée sans grind fasse progresser ;
c'est raté d'un facteur 5 à 6. Les boutons sont `Bank.DAILY_RATE` et
`Bank.DAILY_CAP`. Décision laissée au joueur.

### 2026-11-04 — phase 5, collections

Le premier palier débloque la vente de l'item. Levier de rythme : on ne peut pas
tout vendre dès la première minute, et il devient utile de miner autre chose que
l'optimum du moment.

**Problème repéré en relisant la spec, et corrigé.** La règle « un bloc posé ne
rapporte rien » (phase 2) aurait **interdit l'agriculture** : une culture doit
être plantée pour être récoltée, donc le blé n'aurait jamais compté. Le tag
`#dohy:placement_exempt` liste les blocs qui mûrissent sur place. L'exemption est
sans danger — on ne duplique pas du blé en le plantant, on investit des graines
et du temps. La canne à sucre, le bambou et le cactus n'y sont **pas**
volontairement : seule la partie poussée au-dessus du bloc planté compte, ce qui
est exactement le rendement.

**Comptage à la source, pas au ramassage.** Compter au ramassage serait plus
simple et plus complet, mais ouvrirait un exploit immédiat : jeter une pile puis
la ramasser, en boucle. Compter à la casse, à la mort de mob et à la pêche exclut
de surcroît les achats au shop par construction, sans marquer les items achetés.

Limite assumée : les drops de mob ne comptent que si la mort est attribuable à un
joueur (directement, ou via `getLastHurtByMob`). Une ferme purement passive —
chute, suffocation — ne fera pas progresser les collections. Attribuer au joueur
le plus proche serait faux par principe.

Repli en dur si `collections.json` manque : sans paliers, **aucun item ne serait
vendable**, puisque le premier palier débloque la vente.

### 2026-11-04 — phase 4, prix dynamiques

Le paiement est l'**intégrale** du prix unitaire sur la quantité vendue, pas une
multiplication. C'est le point non évident de cette phase : vendre 10 000 blés
d'un coup au prix d'avant-vente serait l'inverse exact de l'effet recherché,
puisqu'il suffirait de tout stocker puis de tout écouler en une fois pour ignorer
la pression. Forme fermée, vérifiée numériquement contre une somme unité par
unité ; l'arrondi n'intervient qu'une fois à la fin, car arrondir chaque unité
fait perdre jusqu'à 12 % sur les items bon marché au plancher.

**Tous les chemins de vente agrègent par item avant de tarifer** — coffre de
vente, shop, `/sellall`, `/sell`. Sans ça, cinq piles du même item partiraient
toutes au prix d'avant-vente et l'agrégation serait contournée par accident.

**L'oubli est en temps de jeu**, appliqué chaque minute avec le facteur
équivalent à 10 % par 20 min. En temps réel, un week-end de grind écraserait les
prix et les cinq jours ouvrés les feraient remonter sans que le joueur y soit
pour rien.

Simulation retenue (blé, base 15) : 1ʳᵉ récolte de 2 000 → 23 963 $, 2ᵉ → 17 425 $,
3ᵉ → 14 194 $. Après une soirée de 2 h le prix unitaire remonte de 6,53 à 8,47 ;
après un week-end de 10 h, à 13,96 soit 93 % de la base.
Point à surveiller : les items `FOUND` (S0 = 64) atteignent le plancher vite — un
dump de 5 stacks de diamants demande ~12 h de jeu pour revenir à la base. C'est
voulu (forcer la diversification), mais si ça pèse, **S0 est le bouton**, pas la
formule.

### 2026-11-03 (suite) — phases 2 et 3

**Phase 2 — suivi des blocs posés.** Attachment de chunk, positions compactées en
`long` dans un ensemble fastutil, sérialisation conditionnelle, et jamais de
chargement forcé de chunk depuis un gestionnaire d'événement de bloc.
Point de conception à retenir : le verdict « ce bloc était-il posé ? » est
**mémoïsé par événement**, parce que plusieurs systèmes vont le demander pour la
même casse et que consommer la marque priverait le second appelant de la réponse.
La mémoïsation rend l'ordre des gestionnaires indifférent, sans toucher aux
priorités du bus.
Commandes de diagnostic `/dohy placed` et `/dohy chunk` : le suivi est invisible
par nature, et sans moyen de l'interroger une régression ne se verrait qu'au
moment où un skill distribue de l'XP sur des blocs reposés.

**Phase 3 — catalogue de prix.** Lu via le gestionnaire de ressources du serveur
depuis `data/dohy/prices.json`, donc remplaçable par un datapack. Le fichier du
jar est **copié au build** depuis `data/generated/prices.json` : une seule source
de vérité, et la CI vérifie que la copie a bien atterri dans le jar — si elle
casse, le mod se charge mais plus rien ne se vend, un symptôme qui ne ressemble
pas à sa cause.
`/worth`, `/sell`, `/sell stack`, `/dohy reload`.
**Phase 3b — le shop.** Choix d'implémentation retenu avec le joueur : **shop en
coffre** plutôt qu'écran custom. `ShopMenu` est une sous-classe de `ChestMenu`
déclarée avec le type vanilla `GENERIC_9x6`, donc **aucun code client** — ni
écran, ni `MenuType`, ni paquet. Le rendu en mode retenu de 26.2, qui est la plus
grosse inconnue du projet, est ainsi entièrement contourné pour un premier shop
fonctionnel.
Le conteneur ne contient que des copies d'affichage et `super.clicked` n'est
jamais appelé : aucun item ne peut être déplacé, dupliqué ou perdu.
`/sellall` passe par `getNonEquipmentItems()`, qui exclut l'équipement porté —
c'est la vérification qui avait fait reporter la commande.
Si un écran custom devient souhaitable (afficher les prix dynamiques, un
historique), c'est à ce moment-là qu'il faudra affronter `extractBackground`.

**Coffre de vente** (demande du joueur, hors plan initial). `SellChestMenu`, même
présentation de coffre, mais conteneur **réel** : les clics sont délégués à
`super`, donc vanilla gère tout le déplacement d'items. À la fermeture, le
vendable est payé et le reste rendu, au sol si l'inventaire est plein.
Deux protections qui méritent d'être connues : une garde contre le double
règlement (`removed` peut être appelé plusieurs fois, et payer deux fois le même
dépôt serait une machine à argent), et un rattrapage qui restitue le contenu si
le règlement lève une exception — elle surviendrait au pire moment, les items
étant déjà sortis du conteneur mais pas encore rendus.
`/sell` sans argument ouvre le coffre ; `/sell hand` et `/sell stack` restent.

Signatures 26.2 ajoutées à la table ci-dessous : `BlockEvent.BreakEvent`,
`LevelChunk.markUnsaved`, `Registry.getOptional`.

**Phases 2 et 3 validées en jeu** : `/worth`, `/dohy reload`, `/dohy placed`.
Les phases 1 à 3 sont donc confirmées de bout en bout, catalogue compris.

### 2026-11-03 (suite) — phase 1

Monnaie livree : `PlayerEconomy` immuable (codec de persistance + codec reseau),
`Economy` comme seul point de mutation pour que la synchronisation ne puisse pas
etre oubliee, `MoneyFormat`, HUD en mode retenu avec animation de gain,
sauvegardes de secours en trois copies glissantes, et `/money` avec ses
sous-commandes d'administration.

**Sonde d'API : six signatures de 26.2 que personne ne pouvait deviner.**
Elles sont consignees ici parce qu'elles vont se reposer a chaque phase :

| Sujet | 26.2 |
|---|---|
| `ResourceLocation` | renomme **`Identifier`**, fabrique `fromNamespaceAndPath` |
| `GuiGraphics` | **supprime**. Une couche de HUD est `GuiLayer.render(GuiGraphicsExtractor, DeltaTracker)` |
| `@EventBusSubscriber` | plus de `modid` ni de `bus`, seul `value = Dist` subsiste ; le bus est deduit du type de l'evenement |
| Builder d'attachment | `serialize(MapCodec)`, pas `Codec` ; `sync(StreamCodec)` existe nativement |
| `GameProfile` | devenu un **record** : `name()` |
| Permissions | `CommandSourceStack.hasPermission(int)` supprime → `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| `net.minecraft.Util` | n'existe plus |
| `Options.hideGui` | n'existe plus — et c'est sans conséquence, le gestionnaire de couches ne rend rien quand l'interface est masquée (**vérifié en jeu**) |
| `BlockEvent.BreakEvent` | **supprimé** → `BlockDropsEvent` (position, état, casseur, drops, outil) |
| `LevelChunk` | `markUnsaved()` / `isUnsaved()` |
| `Registry` | `getOptional(Identifier)` → `Optional<T>`, `get(...)` → `Optional<Holder.Reference<T>>` |
| Permissions | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| `ClickType` | renommé **`ContainerInput`** (enum : PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, PICKUP_ALL, QUICK_CRAFT) ; `clicked(int, int, ContainerInput, Player)` |
| `Inventory` | **`getNonEquipmentItems()`** → `NonNullList<ItemStack>`, exclut l'équipement porté |
| `ChestMenu` | constructeur public `(MenuType, int, Inventory, Container, int rows)` → sous-classable avec `MenuType.GENERIC_9x6` |
| `ServerPlayer.playNotifySound` | signature changée → passer par `Level.playSound` |
| `ResourceKey.location()` | **supprimé** → `Registry.getKey(value)` |
| `Item.getName()` | exige une `ItemStack` → `new ItemStack(item).getHoverName()` |
| `Level.random` | protégé → `Entity.getRandom()` |
| `EntityType.is(TagKey)` | absent → `Registry.get(id).map(h -> h.is(tag))` |
| Blocs colorés | **unifiés** en 26.x : `GRAY_STAINED_GLASS_PANE` n'existe plus (`STAINED_GLASS_PANE`, `DYE` sont des items uniques) |
| `ServerPlayer.server` / `getServer()` | inaccessibles → ouvrir un menu par appel direct |
| `SoundEvents` | certains sont des `Holder` (`.value()`), d'autres des `SoundEvent` direct — vérifier au cas par cas |
| `displayClientMessage` | **supprimé** → `player.connection.send(new ClientboundSetActionBarTextPacket(...))` |
| `ServerBossEvent` | ctor **exige un UUID** : `(UUID, Component, BossBarColor, BossBarOverlay)` — pas de variante à 3 arguments (sondé le 2026-11-06) |
| `BossEvent` | `setProgress(float)`, `setName`, `setColor`, `setOverlay`, `addPlayer`, `removePlayer` présents ; `removeAllPlayers` **non confirmé**, évité |
| `BossBarColor` / `BossBarOverlay` | PINK BLUE RED GREEN YELLOW PURPLE WHITE / PROGRESS NOTCHED_6 NOTCHED_10 NOTCHED_12 NOTCHED_20 (sondé le 2026-11-06) |
| `MenuType.GENERIC_9x1` … `9x6` | les **six** existent, typées `MenuType<ChestMenu>` (sondé le 2026-11-05) |
| `Player.closeContainer()` | **présent** (sondé le 2026-11-05) |
| `EntityType.getDescription()` | **présent**, rend un `Component` (sondé le 2026-11-05) |
| `Identifier.tryParse` | **non vérifié** — fabriquer avec `fromNamespaceAndPath` après découpe sur `:` (voir `ContractDisplay.parse`) |

**Phase 1 validée en jeu** : solde affiché, crédit et animation de gain,
`/money hide`, persistance au rechargement du monde, conservation à la mort, et
masquage par F1 — l'hypothèse sur le gestionnaire de couches était la bonne.
Le HUD a été déplacé au centre du bord droit à la demande du joueur.
**Sauvegarde de secours confirmée en jeu** : `<monde>/dohy/economy-backup-1.json`
est bien écrit. Le filet sous la progression longue est donc en place.

### 2026-11-03
Première session. Cadrage complet et production du doc + data.
Décisions notables, toutes justifiées dans `DESIGN.md` §12 :
- Catégorie « trouvable » par ID d'item, pas par tag posé sur l'ItemStack
- Oubli des prix en temps de **jeu**, pas en temps réel (sinon les soirées de semaine sont stérilisées)
- Pénalité de mort indexée sur les **gains cumulés**, plafonnée
- **Tout item craftable est en achat seul**, jamais vendable → supprime l'audit de 2 000 recettes
- Prix **générés** depuis un modèle $/h, jamais écrits à la main
Aucun code écrit dans un premier temps : le dépôt n'était pas encore accessible.

Puis, accès obtenu, PR #1 ouverte avec le doc et la data, et phase 0 posée sur `feat/socle-neoforge` :
- Un seul module NeoForge (décision ci-dessus), ModDevGradle 2.0.148, NeoForge 26.2.0.93, Java 25
- CI à deux jobs : compilation, et **cohérence des données** (si `rates.csv` change sans régénération, le build échoue)
- Correction d'une erreur de la session : NeoForge 26.2 n'est **pas** en beta, 26.2.0.93 est stable. Les hébergeurs listaient des builds `-beta` périmés.
- Trois contraintes 26.2 relevées et inscrites dans `DESIGN.md` §13 : rendu en mode retenu (`GuiGraphics` supprimé), `ChatFormatting` vidé au profit de `Style`, et sauvegarde via `ValueInput`/`ValueOutput` au lieu de `CompoundTag`.
