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
- [ ] Vérifier `pack.mcmeta` : `pack_format` 88 + `supported_formats [88, 107]` est une hypothèse, les deux formats divergeant depuis 26.x. À confirmer au premier lancement.

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
- **Catégorie COMPONENT** : liste et prix d'achat à établir (phase 3).
- **Table de loot des caisses** : non écrite.
- **Barème d'XP du niveau global** par type de jalon : à fixer quand les contrats tournent.
- **`minecraft:potent_sulfur`** classé farmable sur la base des tags de geyser de 26.2 — **à vérifier en jeu** : si l'éruption ne produit pas de gain net, le reclasser en `FOUND`.

---

## Journal des sessions

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

**Les dix phases du plan sont faites.** Restent, hors plan :
- les perks de skill non encore appliqués (liste dans le journal au démarrage) ;
- l'équilibrage des intérêts de banque, constat chiffré plus bas ;
- la liste d'items de DonutSMP (`donut.build/auction`), demandée puis mise de côté.

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
| `displayClientMessage` | **supprimé** → `player.connection.send(new ClientboundSetActionBarTextPacket(...))` |

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
