# État d'avancement

> Ce fichier est la **mémoire du projet**. Une session Claude ne garde rien d'une fois sur l'autre :
> lire `PROGRESS.md`, `docs/DESIGN.md` et `docs/BALANCE.md` suffit à reprendre le travail.
> **À mettre à jour à chaque fin de session.**

Dernière mise à jour : 2026-11-03

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
| Mode de jeu | Solo, monde normal |

**Choix d'architecture : un seul module NeoForge**, pas de découpage multiloader Architectury. La cible est NeoForge uniquement ; le surcoût de scaffolding common/fabric/forge ne se rentabilise que si le mod doit sortir sur plusieurs loaders.

---

## Fait

- [x] `docs/DESIGN.md` — spec des 8 systèmes, anti-exploits, ordre d'implémentation, décisions tranchées
- [x] `docs/BALANCE.md` — modèle de prix, courbes de skills, progression attendue, sinks
- [x] `data/rates.csv` — 152 items avec leur taux d'obtention horaire et leur tier
- [x] `tools/gen_prices.py` — générateur de prix + vérification de cohérence $/h par tier
- [x] `data/prices.csv` / `data/generated/prices.json` — générés, 152 items
- [x] `data/skills.json` — 8 skills, courbes, sources d'XP, 10 perks chacun
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
| 2 | **Minerais et matériaux** `palamod` : 6 minerais + génération, 7 blocs, 7 particules, lingots, compressés, 4 charbons mixés, onglet créatif | fait |
| 3 | **Cultures** : 4 plantes, graines, fruits, lot rare, XP d'agriculture | fait |
| 4 | **Machines** : cobblebreaker 4 paliers, crusher | fait |
| 5 | **Machines, suite** : totem de fertilité, 5 seedplanters, palafurnace, grinder, palamachine | à faire |
| 6 | **Intégration économique** : prix, collections, aile de musée, audit anti-exploit | à faire |

**Décisions prises avec le joueur** (2026-11-07) :

1. **Fusion métier/skill** plutôt que deux progressions parallèles. `mining` → Mineur,
   `combat` → Chasseur, `farming` → Fermier, et un skill `alchemy` créé pour l'Alchimiste.
   Une seule barre d'XP par activité, rien à migrer, aucun risque de dérive entre deux
   compteurs. Coût assumé : la table d'XP de PalaJobs (20 niveaux) n'est pas utilisée.
2. **Namespace `palamod`** et non `dohy`, avec les identifiants de Paladium. Le pack de
   textures officiel s'applique alors tel quel, sans copie ni renommage dans le jar.
3. **Chiffres manquants calibrés**, puis remplacés par les vrais dès que le jar de PalaMod a
   été fourni. Résultat : presque plus rien n'est calibré — voir `docs/PALAMOD.md`.

**Le chapitre 2 est un prérequis non demandé mais nécessaire** : le cobblebreaker sort des
particules de minerai, le crusher consomme des charbons mixés et rend des lingots. Sans ces
items, les machines des chapitres 3 et 4 n'ont ni entrée ni sortie.

---

## Bloqué

| Sujet | Blocage | Débloqué par |
|---|---|---|
| Build en local (environnement Claude) | JDK 21 seulement, et `maven.neoforged.net` / `plugins.gradle.org` / `services.gradle.org` injoignables | Rien à faire : la CI GitHub est la boucle de validation. Ne pas chercher à compiler dans la session. |

---

## Points ouverts à trancher

- **IDs d'attributs 26.2** : les perks de `skills.json` utilisent des ids plausibles (`minecraft:mining_efficiency`, `minecraft:block_interaction_range`…) qui doivent être validés contre l'API réelle. À faire à la première compilation.
- **Trocs de villageois en registres de datapack** (nouveauté 26.1 : `data/<ns>/villager_trade/*.json` et `trade_set/*.json`). **Piste à évaluer en phase 3** : plutôt que de plafonner le prix de vente du lapis, de la perle de l'ender et du reste, on peut retirer ou renchérir directement le troc fautif par datapack — et rendre leurs vrais prix à ces items. C'est traiter la cause au lieu du symptôme, et ça rendrait les plafonds inutiles.
- **HUD et GUI en mode retenu** : 26.2 a supprimé `GuiGraphics`. Le HUD de la monnaie et le GUI du shop doivent être écrits directement en `extract*` + `GuiGraphicsExtractor`. L'API exacte d'enregistrement d'une couche de HUD côté NeoForge 26.2 est à confirmer en CI (l'ancien `RegisterGuiLayersEvent` n'est pas garanti sous cette forme).
- **Plafonds villageois** : ~~à confirmer~~ → **tranché le 2026-11-03 : on garde les plafonds.** Ne pas relancer `gen_prices.py` avec `--no-villager-caps`. Audit encore à compléter sur : briques, argile, papier, verre teinté, charbon, fer.
- ~~**Catégorie COMPONENT** : liste et prix d'achat à établir (phase 3).~~ → **tranché le 2026-11-05 : la catégorie est supprimée.** Elle n'a jamais contenu d'item (aucune ligne de `rates.csv`, aucune règle de `tag_prices.json`), donc l'onglet du shop était vide et l'aile de musée correspondante ne pouvait pas se compléter. L'onglet est remplacé par la boutique ; la remise d'achat de −10 % est passée sur l'aile NTM.
- **Table de loot des caisses** : non écrite.
- **Barème d'XP du niveau global** par type de jalon : à fixer quand les contrats tournent.
- **`minecraft:potent_sulfur`** classé farmable sur la base des tags de geyser de 26.2 — **à vérifier en jeu** : si l'éruption ne produit pas de gain net, le reclasser en `FOUND`.

---

## Journal des sessions

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
