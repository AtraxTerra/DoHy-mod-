# DoHy — Document de conception

Mod d'économie et de progression pour une partie **solo**, monde normal.
Cible : **Minecraft 26.2 / NeoForge 26.2.0.93** (stable), **Java 25**, Gradle 9.1, ModDevGradle 2.0.148.
Inspirations : DonutSMP (économie, shop, prix dynamiques), Hypixel SkyBlock (collections, jalons) et Paladium (métiers, PalaMod).

---

## 0. Contexte de jeu — ce qui conditionne tout l'équilibrage

Le joueur unique a un rythme connu :

- **~2 h en semaine, le soir** → chaque session doit permettre d'atteindre **au moins un palier visible** (un niveau de métier, un palier de collection, un contrat quotidien). Jamais une soirée sans récompense.
- **Beaucoup de temps le week-end** → c'est là que se construisent les fermes, que se lancent les boosters et que se termminent les contrats hebdo.

Deux conséquences structurelles, qui reviennent partout dans ce document :

1. **Aucun système ne doit dépendre d'une connexion quotidienne.** Pas de streak qui se casse, pas de récompense perdue si on saute un soir.
2. **Deux horloges, et le choix se fait au cas par cas** — révisé le 2026-11-05.
   - **Temps réel, à heure fixe** pour tout ce qui est un *cycle* : contrats du jour et de la semaine, demande du jour, bonus de première vente, intérêts d'épargne, oubli des prix. La version précédente comptait en temps de jeu, et c'était une erreur de conception : 24 h **de jeu**, à 2 h par soirée, font douze jours. Des contrats « du jour » qui se renouvellent deux fois par mois ne renouvellent rien.
   - **Temps de jeu** pour ce qui se *consomme pendant une session* : durée d'un booster, intervalle de l'autosell. Un booster payé 300 M ne doit pas fondre parce que la connexion a sauté, et l'autosell n'a pas de sens joueur absent.
   - Le passage au temps réel n'avantage le week-end que si les taux restent ceux du temps de jeu. Ils ont donc été recalibrés — voir la banque dans `BALANCE.md`.

## 1. Principes directeurs

1. **Le grind doit se diversifier, pas se répéter.** C'est le rôle des prix dynamiques : la 10 000ᵉ pierre vendue ne vaut plus rien, donc il faut aller chercher autre chose.
2. **Les récompenses passives restent petites.** Métiers, banque et jalons sont des bonus, jamais la source principale de revenu.
3. **Un seul levier d'équilibrage global.** Tous les prix dérivent d'un multiplicateur unique (`MONEY_MULTIPLIER`). Si l'économie est trop généreuse, on change un chiffre.
4. **Rien n'est irréversible pour le joueur.** Pas de perte sèche non plafonnée, pas de système qui punit le fait de jouer.
5. **Tout ce qui est chiffré vit dans un datapack**, pas dans le code. Rééquilibrer ne doit pas demander une recompilation.

---

## 2. Monnaie

| Point | Décision |
|---|---|
| Type | `long` (un `int` plafonne à 2 147 483 647, incompatible avec l'objectif "1T") |
| Stockage | `AttachmentType` persistant sur le joueur, sérialisé par codec |
| Solde de départ | **0** |
| Affichage | Overlay HUD custom via `RegisterGuiLayersEvent` |
| Format | `k / M / B / T / Qa`, 2 décimales (`1.24M`), valeur exacte au survol dans `/money` |

**Pourquoi pas le scoreboard vanilla :** un score est un `int`. On peut contourner avec un `number_format` de type `fixed` (le texte affiché est alors fourni par le mod, le score n'étant qu'un dummy), mais l'overlay HUD donne le placement libre, les couleurs et les animations de gain sans entrer en conflit avec les scoreboards d'autres mods.

**À prévoir dès le début :**
- Animation de gain (`+1.2k` en vert au-dessus de la hotbar, fade sur 1,5 s)
- `/money hide` pour masquer le HUD
- **Sauvegarde du fichier économie toutes les 5 min + 3 backups glissants.** En solo, une corruption de save sans backup est une partie morte. Ce point est non négociable et doit être implémenté en même temps que la monnaie, pas après.

---

## 3. Shop

### 3.1 Catégories (affichage)

| Catégorie | Contenu | Sens |
|---|---|---|
| `FOOD` | Nourriture brute et cuite | Vente |
| `FOUND` | Non-renouvelable uniquement | Vente |
| `FARM` | Issu de ferme automatisable | Vente |
| `COMPONENT` | Composants de base | **Achat seul** |
| `NTM` | Minerais et pièces de machine NTM NEXT | Brut à la vente, lingots et pièces à l'achat, voir 3.4 |
| `MOB_DROP` | Drops de mobs | Vente |
| `PALAMOD` | Matières premières de PalaMod : minerais, lingots, cultures, bûches (chapitre 6, `PALAMOD.md` § 19) | Achat + vente, les bûches en vente seule |

### 3.2 Prix dynamique

```
prix_vente = base × clamp( (S₀ / (S₀ + vendu))^0.6 , plancher , 1.8 )
```

- `vendu` = quantité cumulée vendue par le joueur pour cet item
- `S₀` = demi-saturation, propre à chaque tier (voir `BALANCE.md`)
- **Oubli : `vendu` décroît de 10 % par 20 minutes de temps réel**, en ligne comme hors ligne (révisé le 2026-11-05 ; c'était en temps de jeu). La crainte d'origine — « un week-end de grind écrase les prix et les soirées suivantes n'ont plus de revenu » — était l'inverse de ce qui se produisait : en temps de jeu, la remontée demandait *elle aussi* d'être en jeu, donc les prix écrasés le dimanche le restaient pendant une douzaine de soirées. En temps réel, la nuit suffit à refaire une bonne partie du chemin.
- Plancher par tier (15 % à 50 %) : sans plancher, le grind devient littéralement gratuit et le joueur se sent volé.
- Plafond 180 % : récompense le fait de ne pas spammer un item.

### 3.3 Règles anti-exploit du shop

- **Tout item craftable est en achat seul, jamais vendable.** C'est la règle la plus importante du shop. Sinon il existe toujours une recette où la somme des prix de vente des composants est inférieure au prix de vente du produit, et c'est une machine à argent. Cette règle rend inutile la vérification manuelle de 2 000 recettes.
- **Minerai ≠ métal.** Le bloc de minerai à la pioche soie et le lingot sont deux marchandises distinctes, avec des tiers différents : le lingot est renouvelable (golems, noyés, troc), le bloc de minerai non.
- **Émeraudes à prix dérisoire (2 $).** Les villageois permettent d'échanger presque n'importe quoi contre des émeraudes ; les payer au prix fort revient à brancher le shop sur un générateur infini. Le taux d'obtention saisi dans `rates.csv` (12 000/h) est celui d'un hall de troc optimisé, pas d'un villageois isolé — c'est ce qui fait tomber le prix à 2 $ par le modèle plutôt que par une exception.
- **Boucle émeraude → item acheté → revente (à auditer en phase 3).** Un villageois qui vend un item contre `N` émeraudes crée un circuit rentable dès que `prix_vente(item) > N × prix_vente(émeraude)`. Exemple : un clerc vend 1 lapis pour 1 émeraude ; si le lapis se vend 45 $ et l'émeraude 2 $, chaque émeraude se transforme en 45 $.
  Règle : **tout item vendable qu'un villageois peut vendre doit être plafonné à `N × prix_émeraude`**, ou sorti de la vente. Liste à auditer en phase 3 : lapis, redstone, glowstone, quartz, charbon, fer, argile, briques, papier, cloche, verre teinté.
  Mitigation déjà appliquée : le taux du lapis a été relevé à 1 200/h pour refermer la boucle la plus évidente.
- Les drops de spawner comptent normalement : c'est le prix dynamique qui régule, pas une interdiction.
- **Blocs de masse : plancher de 5 %** (2026-11-09). Sable, ardoise, tuf, granite, diorite, andésite, calcite, netherrack, blackstone, end stone, terracotta, soufre, cinabre, pierre, cobble, basalte, gravier, sable des âmes : les outils de zone de PalaMod en cassent 9 par coup et la corne du Dancarok 154. Au plancher de leur tier (35 % pour un `FOUND`), chaque bloc restait une rente sans fond. Le prix de base ne change pas ; ni les paliers de collection ni le prestige ne relèvent ce plancher (`floor_bonus_disabled`).
- **Une conversion qui part d'un objet achetable doit rendre moins que son prix d'achat.** C'est la boucle émeraude du point précédent, généralisée : le chaudron de PalaMod changeait la poudre lumineuse (4 $ à l'achat) en trois pépites d'or (27 $) ; la recette est retirée. Pour la même raison, les bûches de PalaMod ne s'achètent pas : leur sève se change en lingots.

### 3.4 NTM NEXT

NTM NEXT est bien disponible en 26.2 NeoForge (port officiel de HBM's Nuclear Tech Mod, versions 1.0.x).

Le danger : les chaînes de traitement du mod (shredder, crystallizer, acidizer, centrifugeuse, breeder) **multiplient la matière** — plusieurs lingots par minerai, plus des sous-produits exploitables. Acheter un intermédiaire au-dessus du coût de son minerai d'entrée crée un revenu infini en quelques heures de jeu.

Règles :

1. **Deux familles au shop, rien d'autre** (tranché le 2026-11-11) : les **minerais** — dix
   matières brutes minées, vendables, et leurs lingots, achetables — et les **pièces de
   machine** — plaques, fils, bobines, tuyaux, boulons, moteurs, circuits — achetables. Ni
   alliage, ni plastique, ni isotope, ni intermédiaire.
2. **La matière brute se vend, le lingot et la pièce s'achètent.** Jamais l'inverse : la brute
   achetée puis traitée rendrait plus d'un lingot, le lingot vendu ferait du rendement des
   chaînes un revenu.
3. **La rareté vient du wiki du mod** (*Difficulty of Production*), huit crans, ×5 à l'achat,
   ×2,83 à la vente (`BALANCE.md` § 6 bis).
4. **Le prix d'une pièce se calcule sur ses recettes réelles**, lues dans les données du mod par
   `tools/gen_ntm_prices.py` : 115 % du coût de ses ingrédients, par la recette la moins chère.
5. **Minerais rattachés par sous-tag exact** (`c:raw_materials/<matière>`, `c:ingots/<matière>`),
   pièces par identifiant. Une matière hors catalogue n'a pas de prix ; un identifiant absent
   est ignoré.
6. Dépendance **aucune** : le mod tourne sans NTM installé, les tags sont alors vides.

À traiter **en dernier** (phase 10), quand l'équilibrage vanilla est stable. Un équilibrage NTM posé sur une base vanilla instable est à refaire.

---

## 4. `/worth`

- `x1`, `x64`, et **total si je vends tout l'inventaire**
- Prix actuel vs base en `↑/↓ %`
- Mini-historique 7 jours de jeu (sparkline texte, ex. `▁▂▄▃▅▇▆`)
- `/sellall <catégorie>` avec confirmation obligatoire au-delà de 100 k

---

## 5. Mort et banque

### 5.1 Pénalité

- **0 pour les 3 premières morts de la partie** (phase d'apprentissage)
- Ensuite : **8 % du solde liquide**, plafonné à `50 000 + 1 % des gains cumulés`
- **Indexée sur les gains cumulés, pas sur le solde instantané**

**Pourquoi ce choix :** punir le solde instantané apprend au joueur à ne jamais avoir de solde — il videra son compte avant chaque sortie et la pénalité ne touchera plus rien. Et sans plafond absolu, une mort bête dans la lave à 500 M est un abandon de partie, pas une leçon.

### 5.2 Banque

- Solde protégé de la pénalité de mort
- **3 % de frais au dépôt** (le sink qui justifie le système)
- **0,25 % par jour réel, plafonné à 250 k/jour**, intérêts courus même monde fermé (le chiffre d'origine, 0,5 %/jour de jeu plafonné à 50 k, était négligeable d'un facteur 5 ; voir `BALANCE.md` pour le calcul et pour le recalibrage du passage au temps réel)
- Rôle : garantir qu'une soirée de semaine sans grind fait quand même progresser

### 5.3 Assurance

Objet consommable acheté au shop, −50 % de perte à la mort. Transforme la punition en décision.

---

## 6. Boosters

| Booster | Prix | Durée |
|---|---|---|
| x2 | 2 M | 20 min de temps de jeu |
| x3 | 25 M | 20 min |
| x4 | 300 M | 20 min |

- **Appliqué après le calcul du prix dynamique, et le montant boosté ne compte pas dans la pression sur le prix.** Sinon le booster accélère le crash des prix et s'annule lui-même.
- Non cumulables. Timer persistant à la déconnexion.
- 20 min = un sixième d'une soirée : ça force à constituer un stock avant de déclencher. Le x4 est un pari de week-end, pas un achat de routine.

**Note de design :** en solo, un multiplicateur d'argent n'est qu'un changement d'échelle — sans concurrence, "gagner 4× plus vite" ne vaut quelque chose que si les sinks sont réellement chers. D'où les prix élevés et la durée courte : le booster doit être un **pari sur sa propre préparation**.

---

## 7. Métiers

Quatre métiers, ceux de Paladium : **Mineur**, **Chasseur**, **Fermier**, **Alchimiste**. Ils ont remplacé les neuf skills du départ (2026-11-08) : minage, combat, agriculture et alchimie sont devenus les quatre métiers avec leurs sources d'XP et leurs perks ; bûcheronnage, pêche, forge, construction et nucléaire ont été retirés avec leurs sources et leurs perks. L'XP des quatre premiers est reprise à la première connexion (`JobMigration`), celle des cinq autres abandonnée.

- 100 niveaux, courbe XP : `xp(lvl) = 100 × lvl^1.6` (`job_progression.json`)
- Récompense money par niveau : `750 × lvl^1.4` → volontairement **sous** le revenu de grind équivalent : c'est un bonus, pas une source
- **Prime de palier tous les 5 niveaux** : deux fois l'argent du niveau, versée une fois (`JobPrimes`). Elle a remplacé les perks le 2026-11-11 — vitesse de minage, portée, PV max, chances de double drop — sur demande : un palier paie, il ne modifie plus le personnage
- **Objets de Paladium aux paliers** (`jobs.json`, relevés dans les `Reward.builder` de PalaMod, niveaux ×5) : lingots de paladium et trixium à chaque palier, pioche et armure enchantées, têtes, pépite d'endium au 100. Un objet pas encore porté est inerte et listé par `/metiers audit`
- **Déblocages** : machines et outils de PalaMod, verrouillés par niveau (`jobs.json`)
- XP d'agriculture et d'alchimie **uniquement sur culture mature**

---

## 7 bis. Point d'extension pour les mods de contenu

PalaCore ajoute des minerais et de l'équipement, sans progression propre : il
s'appuie sur les métiers et l'économie d'ici. Le branchement passe par des
**tags**, et c'est délibéré — les tags fusionnent entre datapacks, les fichiers
de configuration non. Un mod tiers qui voudrait son XP en éditant `job_progression.json`
en écraserait la totalité.

Deux surfaces, toutes deux inertes sans le mod tiers :

- **XP de minage** : `#dohy:mining_xp/{low,mid,high,elite}`, déclarés vides ici
  et référencés dans `job_progression.json` à 30, 80, 180 et 350. À comparer à l'échelle
  existante : fer 12, diamant 90, émeraude 110, débris antiques 400.
- **Prix** : le mod range ses minerais dans les sous-tags `c:raw_materials/<matière>` et
  `c:ingots/<matière>` habituels, et la matière s'ajoute au bloc `materials` de
  `tag_prices.json` avec son cran. Le rattachement se fait par sous-tag exact : une matière non
  listée n'a pas de prix.

Les règles donnent gratuitement la bonne protection : brut vendable, lingot en achat seul. Le
jour où PalaCore livrera son broyeur, doubler le minerai ne pourra donc pas se convertir en
revenu.

Les matières de PalaCore ne sont plus listées depuis le 2026-11-11 : le catalogue se limite aux
dix métaux de NTM. Elles étaient cotées paladium Hard, paladium vert et endium Extremely Hard,
titane et findium Medium, améthyste Easy — les mêmes prix qu'avant, sur la nouvelle échelle. À
remettre le jour où le mod arrive.

## 8. Collections

Deux courbes, parce que 5 000 blocs se font en une soirée avec une ferme mais en trois semaines à la main :

**Vingt-cinq paliers par courbe** (six à l'origine, dix le 2026-11-05, vingt-cinq le 2026-11-06) :

| Type | Paliers |
|---|---|
| Trouvables | 10 · 50 · 100 · 500 · 1 500 · 2 500 · 5 000 · 10 000 · 25 000 · 50 000 · 60 k · 70 k · 80 k · 90 k · 100 k · 115 k · 130 k · 145 k · 160 k · 180 k · 200 k · 220 k · 240 k · 265 k · **300 k** |
| Farmables | 100 · 1 000 · 5 000 · 25 000 · 100 k · 250 k · 500 k · 1 M · 2,5 M · 5 M · 6 M · 7 M · 8 M · 9 M · 10 M · 11,5 M · 13 M · 14,5 M · 16 M · 18 M · 20 M · 22 M · 24 M · 26,5 M · **30 M** |

**Tête raide, queue dense**, et c'est voulu : les dix premiers paliers montent de ×2 à ×5 (la progression), les quinze suivants de ×1,11 (la durée de vie). Un palier de queue représente 10 à 60 h d'activité ciblée. Les valeurs de tête n'ont jamais bougé, pour ne pas déplacer le palier des collections déjà entamées.

Récompense par palier : **un bonus de plancher de prix** sur cet item, et de l'XP globale.

Le plancher suit une **courbe concave** jusqu'à 0,30 : `+0,060` au premier palier, `+0,003` au dernier. Il ne suit délibérément pas le nombre de paliers — vingt-cinq crans de 0,03 auraient donné 0,75, soit la borne entière consommée par les seules collections.

Les exceptions gardent leur longueur propre (l'œuf de dragon a un seul palier, l'élytre dix) : un item rare a moins de paliers, donc moins de bonus, ce qui est cohérent. Un item travaillé de longue date rapporte donc plus qu'un item découvert hier, à quantité égale — c'est ce qui donne une raison de miner autre chose que l'optimum du moment.

**Le déblocage de vente est retiré** (2026-11-05). Il fallait avoir récolté 10 ou 100 exemplaires avant de pouvoir vendre un item. C'était présenté ici comme « le meilleur levier de rythme du mod » ; à l'usage, ça retardait surtout la découverte du shop sans rien structurer — un joueur qui vient de miner son premier diamant doit pouvoir le vendre. `sell_requires_collection` dans `collections.json` restaure le verrou sans toucher au code.

**Le plancher cumulé est borné à 75 % du prix de base** (`DynamicPrice.MAX_FLOOR`). Plancher du tier + bonus de collection + bonus de prestige pouvaient atteindre 90 % pour un `RARE`, et la borne existante à 95 % ne couvrait donc pas le cas qu'elle devait couvrir : au-delà de 75 %, le frein des prix dynamiques n'existe plus et la dix-millième unité vendue rapporte encore le plein tarif.

Implémentation : une seule `Map<ItemId, Long>` dans l'attachment joueur, paliers générés par formule. Pas 200 fichiers.

---

## 8 bis. Barre de progression des métiers

Une **barre de boss** en haut de l'écran, façon SkyBlock : nom du métier, niveau, XP dans le niveau, gain qui vient d'arriver. Une couleur par métier pour reconnaître l'activité sans lire.

Pourquoi une barre de boss et pas une couche de HUD : c'est le seul affichage riche en haut de l'écran qui soit **entièrement côté serveur**. Le jeu envoie les paquets, le client vanilla la dessine. Le mod n'a donc toujours qu'un seul morceau de code client — le HUD de solde — ce qui le met à l'abri des refontes du rendu (26.2 en a déjà imposé une).

Une seule barre par joueur, renommée quand il change d'activité : miner puis récolter ne fait pas apparaître deux barres. Elle s'efface après **5 s** sans gain d'XP (**8 s** sur une montée de niveau), sinon elle mangerait le haut de l'écran en continu et se confondrait avec un vrai combat de boss.

État transitoire, hors sauvegarde : une barre est de l'affichage, pas une donnée de jeu.

---

## 8 ter. Améliorations permanentes

Huit branches achetées en argent, **définitives**, de plus en plus chères. Voir `BALANCE.md` pour le barème.

Elles existent parce que tous les autres puits à argent sont consommables ou ponctuels — booster, caisse, assurance — et que l'autosell à 500 M est un achat **unique**. Après le mois 4, l'argent n'avait plus de destination. C'est le trou de conception le plus sérieux qu'avait le mod.

Règle qui les gouverne : **un effet déclaré est un effet branché.** `UpgradeApi.EFFECTS` liste les effets que le code lit, et le chargement prévient pour tout effet absent. Ce filet a été posé après avoir découvert 33 perks déclarés et inertes ; une amélioration payée plusieurs milliards mérite au moins la même garantie.

---

## 9. Systèmes de rythme (adaptés au planning de jeu)

| Système | Détail | Rôle |
|---|---|---|
| **Demande du jour** | Un item tiré au hasard à **×2,5** pour la journée, jusqu'à la bascule de 5 h, annoncé à la connexion | Donne un objectif immédiat à une soirée de 2 h |
| **Contrats quotidiens** | 3 contrats faisables en ~2 h (ex. « miner 500 deepslate », « tuer 60 mobs ») | Structure la session de semaine |
| **Contrat hebdo** | 1 contrat lourd, infaisable en une soirée, grosse récompense | Structure le week-end |
| **Première vente du jour** | ×1,5 sur la première transaction | Récompense sans punir l'absence (pas de streak) |
| **Niveau global** | XP uniquement sur les **jalons** (niveau de métier, palier de collection, contrat hebdo). Récompense tous les 5 niveaux | Un seul chiffre qui résume la partie |
| **Loot boxes** | Six boxes de PalaMod, une clé chacune : Améthyste 100 k · Saharia 1 M · Paladium 5 M · Halloween 20 M · Trixium 50 M · Endium 150 M | Sink principal du milieu de partie |
| **Autosell** | Coffre lié qui vend son contenu. Déblocage très tard (500 M) | Transforme les fermes de week-end en revenu passif de semaine |
| **`/stats`** | Argent gagné à vie, blocs minés, mobs tués, temps de jeu, top 5 des ventes | Lisibilité de la progression longue |
| **`/ecoreload`** | Recharge prix et data sans relancer le jeu | Confort de dev et d'équilibrage |

---

## 10. Anti-exploit — le socle technique

### 10.1 Blocs posés par le joueur (bloquant)

Poser puis casser le même bloc doit donner **0 XP et 0 collection**. Minecraft ne marque pas les blocs posés par un joueur.

Solution : un **attachment de chunk** contenant l'ensemble des positions posées (`BlockEvent.EntityPlaceEvent` → ajout ; casse → consultation puis retrait).

C'est le morceau de code le plus important du mod. Il doit être écrit **avant** les métiers et les collections, pas après : les deux systèmes en dépendent entièrement, et les rajouter ensuite demande de tout retoucher.

Points de vigilance : sérialisation compacte (un `LongSet` de positions packées), purge des positions des chunks déchargés, et comportement sur les blocs déplacés par piston.

### 10.2 Récapitulatif des autres vecteurs

| Vecteur | Traitement |
|---|---|
| Craft pour revendre plus cher | Items craftables en achat seul (§3.3) |
| Villageois / émeraudes | Prix dérisoire via un taux d'obtention réaliste (§3.3) |
| Boucle émeraude → achat villageois → revente | Plafond `N × prix_émeraude` sur les items vendus par les villageois — **audit à faire en phase 3** (§3.3) |
| Chaînes NTM qui multiplient la matière | Intermédiaires non achetés, prix par équivalent-minerai (§3.4) |
| Fermes à haut débit | Régulées par le prix dynamique, pas interdites |
| Place-and-break | §10.1 |
| Bone meal sur culture non mature | XP sur culture mature uniquement (§7) |
| Minage de masse (outils de zone, corne du Dancarok, Dollars Stone) | Plancher de 5 % sur les blocs de masse (§3.3) |
| Machines de PalaMod (chaudron, crusher, golems, sève, cobblebreaker) | Audit de `PALAMOD.md` § 19 : recette d'or retirée, le reste est une ferme régulée par le prix dynamique |

---

## 11. Ordre d'implémentation

1. Monnaie, persistance, HUD, **backups**
2. **Tracking des blocs posés** (chunk attachment)
3. Shop statique, `/worth`, `/sellall`
4. Prix dynamiques et oubli
5. Collections
6. Métiers et primes de palier
7. Banque et pénalité de mort
8. Contrats et demande du jour
9. Boosters, caisses, autosell
10. **NTM NEXT en dernier**

L'ordre n'est pas négociable sur les points 1, 2 et 10 : les backups avant d'avoir des données à perdre, le tracking avant ce qui en dépend, NTM après stabilisation du vanilla.

---

## 12. Décisions tranchées (et pourquoi)

| Décision | Alternative écartée | Raison |
|---|---|---|
| Catégorie « trouvable » par **ID d'item** | Tag `DataComponentType` posé à la casse | Deux stacks aux composants différents ne fusionnent pas → inventaire ingérable, et le tag se perd au craft et au four |
| Cycles en **temps réel à heure fixe** | Temps de jeu (choix initial, **renversé le 2026-11-05**) | 24 h de jeu à 2 h par soirée font douze jours : un cycle en temps de jeu n'est pas un cycle. Le temps réel n'avantage le week-end que si on oublie de recalibrer les taux |
| Durée des boosters en **temps de jeu** | Temps réel | Une déconnexion ne doit pas consommer un achat de 300 M |
| Bascule quotidienne à **5 h** | Minuit | Une soirée qui dépasse minuit verrait ses contrats se renouveler sous son nez |
| Pénalité indexée sur les **gains cumulés** | Solde instantané | Le solde instantané apprend à ne pas avoir de solde |
| Items craftables **non vendables** | Audit recette par recette | 2 000 recettes à vérifier, et une seule oubliée suffit |
| Money `long` + HUD custom | Scoreboard vanilla | `int` plafonne à 2,1 G, incompatible avec l'objectif 1T |

---

## 13. Réserves assumées

- **NeoForge 26.2.0.93 est stable**, mais 26.2 est postérieur à mes connaissances fiables. Les signatures d'API devront être validées par la compilation en CI ; prévoir des corrections.
- **26.2 a réécrit le rendu en mode retenu.** `GuiGraphics` n'existe plus : les écrans n'affichent pas, ils *extraient* (`extractBackground`, `extractLabels`, écriture dans un `GuiGraphicsExtractor`, rendu différé par `GuiRenderer`). `MultiBufferSource` et `Tesselator` ont disparu au profit du pipeline SubmitNode. Cela concerne directement le HUD de la monnaie (§2) et le GUI du shop (§3) : à concevoir d'emblée en mode retenu, pas à porter après coup.
- **`ChatFormatting` a été vidé** en 26.2 : le formatage passe par `Style` (`withColor`, `withBold`…), les constantes de couleur par `TextColor`. Concerne tous les messages du mod.
- **La sauvegarde de données n'utilise plus `CompoundTag`** mais `ValueInput`/`ValueOutput` adossés à des codecs. Concerne directement les attachments du §2 et le §10.1.
- **Les trocs de villageois sont passés en registres de datapack** en 26.1 (`data/<ns>/villager_trade/*.json`, `trade_set/*.json`). C'est une piste plus propre que les plafonds de prix du §3.3 : à évaluer en phase 3, puisqu'on peut désormais retirer ou renchérir un troc par datapack au lieu de corriger ses conséquences.
- L'équilibrage de ce document est **cohérent, pas validé**. Aucun chiffre n'a été testé en jeu. Les retours de session sont la seule source de vérité : chaque écart constaté se corrige dans `BALANCE.md` et se répercute via `MONEY_MULTIPLIER` ou le taux de l'item concerné.
