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
3. [~] Catalogue de prix, `/worth`, `/sell` — **le GUI du shop et `/sellall` restent à faire**
4. [ ] Prix dynamiques et oubli
5. [ ] Collections
6. [ ] Skills et perks
7. [ ] Banque et pénalité de mort
8. [ ] Contrats et demande du jour
9. [ ] Boosters, caisses, autosell
10. [ ] NTM NEXT

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
**Pas encore fait** : le GUI du shop, et `/sellall` sur l'inventaire entier. La
disposition interne de l'inventaire a changé dans la ligne 26.x ; lire est sans
risque (l'interface `Container` est stable, c'est ce qu'utilise `/worth`), mais
supprimer des piles par le mauvais index détruirait l'équipement du joueur. À
sonder avant d'écrire.

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
