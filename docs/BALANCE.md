# DoHy — Équilibrage

Ce fichier contient **tous les chiffres**. Il change souvent ; `DESIGN.md` change rarement.
Tous les prix sont **générés**, jamais écrits à la main : `data/rates.csv` → `tools/gen_prices.py` → `data/prices.csv` + `data/generated/prices.json`.

---

## 1. Le modèle de prix

```
base_price = ( TARGET_RATE[tier] / rate_per_hour ) × MONEY_MULTIPLIER
```

- `rate_per_hour` = combien d'exemplaires un joueur solo compétent obtient en une heure, au stade où l'item apparaît (colonne de `rates.csv`, c'est la seule donnée réellement subjective)
- `TARGET_RATE[tier]` = revenu horaire visé pour ce tier
- Prix arrondis à 2 chiffres significatifs : `750` est plus lisible que `748`

**Conséquence voulue :** deux items du même tier rapportent le même $/h. Le bambou à 6 000/h et la pomme à 150/h rapportent autant à l'heure — ce qui départage, c'est l'effort d'installation, pas une prime arbitraire.

### Tiers

| Tier | Cible $/h | S₀ | Plancher | Contenu |
|---|---|---|---|---|
| `FOOD` | 12 000 | 2 000 | 15 % | viandes, poissons bruts |
| `FARM` | 18 000 | 2 000 | 15 % | tout l'automatisable, métaux renouvelables |
| `MOB` | 25 000 | 256 | 20 % | drops de mobs |
| `FOUND` | 40 000 | 64 | 35 % | non renouvelable |
| `RARE` | 60 000 | 8 | 50 % | élytres, œuf du dragon, nether star, totems |

**Pourquoi FOUND rapporte plus que FARM :** une ferme se construit une fois et produit indéfiniment ; un minerai trouvé ne revient jamais. Si le non-renouvelable ne payait pas mieux à l'heure, personne n'explorerait.

**Pourquoi S₀ chute avec le tier :** vendre 64 diamants doit déjà faire baisser le prix, alors que 2 000 blés c'est une récolte normale.

### Plafonds villageois

Un villageois qui vend un item contre `N` émeraudes impose un plafond : au-delà de `N × prix_émeraude`, le circuit *hall de troc → émeraudes → achat → revente* devient un revenu infini. Avec l'émeraude à 2 $, le générateur appliqué donne :

| Item | Prix du modèle | Plafonné à | Troc (et ce qu'il donne) |
|---|---|---|---|
| pomme | 120 | **1** | fermier : 1 ém. → 4 |
| perle de l'ender | 83 | **10** | clerc : 5 ém. → 1 |
| dripstone (bloc) | 60 | **1** | maçon : 1 ém. → 4 |
| cuir | 60 | **4** | tanneur : armure de cheval en cuir, 6 ém., broyée par Create → 3 cuirs en moyenne (sa selle, 6 ém., en donne 2,5 à la meule) |
| poudre de glowstone | 42 | **2** | clerc : 4 ém. → 1 glowstone, cassée à Fortune ou broyée par Create → 4 poudres au plus |
| terracotta | 33 | **2** | maçon : 4 dripstone pour 1 ém. → 4 argiles à la meule de Create → 1 bloc d'argile cuit |
| morue | 30 | **6** | pêcheur : seau de morue, 3 ém. |
| argile (boule) | 30 | **1** | maçon : 4 dripstone pour 1 ém., à la meule de Create |
| ficelle | 28 | **1** | berger : 1 laine pour 1 ém., broyée par Create → 2,5 ficelles en moyenne |
| redstone | 28 | **1** | clerc : 1 ém. → 2 |
| flèche | 17 | **1** | archer : 1 ém. → 16 |
| laine blanche | 15 | **2** | berger : 1 ém. → 1 (ses laines teintes aussi, lavées par Create) |
| charbon de bois | 15 | **2** | pêcheur : feu de camp, 2 ém., cassé → 2 charbons de bois |
| lapis | 15 | **2** | clerc : 1 ém. → 1 |

**Audit du 2026-11-23** sur les 388 trocs de 26.2 (les données du jeu, plus de table recopiée à la
main) et les recettes de Create 6.0.9 (Create Fly) : chaque vente d'un villageois, et ce que l'objet
acheté donne une fois cassé, fondu ou passé dans une machine de Create. La table d'avant se
trompait : la redstone se vend 2 pour une émeraude, le clerc vend la glowstone (le bloc) et non sa
poudre, le maçon ne vend ni quartz ni terracotta nature. Le **quartz** sort donc des plafonds et
retrouve son prix (20 $) ; la **terracotta** y reste, par la chaîne du dripstone. Sans objet : les
briques, le papier, le verre, la cloche (pas vendables au shop), le fer (une armure ou un outil de
fer fondu donne une pépite, déjà plafonnée par `CRAFT_CAPS`). Le marchand ambulant n'entre pas en
compte : il passe rarement, ses stocks sont limités, il ne se met pas en hall.

**Choix du joueur** (2026-11-23) : plafonner plutôt que retirer les trocs fautifs par datapack. Les
prix de la pomme, du cuir, de la ficelle et de l'argile s'effondrent ; c'était l'autre voie, et le
joueur l'a écartée. **Non vérifié** : le broyeur de NTM NEXT (des armures ou des outils de
villageois broyés en poudres de leur matière) — à voir en jeu.

### Blocs de masse

Vingt blocs que les outils de zone et la corne du Dancarok cassent par dizaines ou par centaines
(liste dans `DESIGN.md` § 3.3) gardent leur prix de base mais un **plancher de 5 %** (colonne
`floor` de `rates.csv`), sans bonus de collection ni de prestige. À la corne, 100 000 blocs
de granite par heure rapportaient 940 k au plancher de 35 % ; à 5 %, 135 k. C'est une borne
haute : les poches de granite s'épuisent et la pierre autour ne vaut que 0,15. Le joueur qui mine
à la main, lui, ne vend jamais assez d'un même bloc pour descendre si bas.

| Bloc | Prix | Plancher avant | Plancher après |
|---|---|---|---|
| granite, diorite, andésite, sable | 27 | 9,45 | 1,35 |
| tuf | 33 | 11,55 | 1,65 |
| ardoise, netherrack, end stone | 13 | 4,55 | 0,65 |
| cobblestone, pierre | 3 | 0,45 | 0,15 |

La colonne `buy_markup` est l'autre correction par item : `0` retire l'achat (les bûches de
PalaMod), une valeur supérieure à 4 le renchérit. En dessous de 4, le générateur refuse.

Désactivable : `python3 tools/gen_prices.py --no-villager-caps`. À ne faire que si la partie se joue **sans hall de troc** — sinon l'économie est cassée en une après-midi.

### Levier unique

`MONEY_MULTIPLIER` dans `gen_prices.py` (ou `--multiplier`). Économie trop généreuse → `0.6` et on régénère. Un seul chiffre, toute la cohérence conservée.

```bash
python3 tools/gen_prices.py --multiplier 0.6
```

### Prix constatés (multiplicateur 1.0)

| Item | Taux/h | Prix |
|---|---|---|
| bambou | 6 000 | 3 |
| blé | 1 200 | 15 |
| lingot de fer | 1 200 | 15 |
| poudre à canon | 600 | 42 |
| perle de l'ender | 300 | 83 |
| émeraude | 12 000 | 2 |
| diamant | 40 | 1 000 |
| débris antique | 8 | 5 000 |
| nether star | 4 | 15 000 |
| élytre | 2 | 30 000 |
| œuf du dragon | 1 | 60 000 |

---

## 2. Courbe des métiers

`xp(lvl) = 100 × lvl^1.6` · `money(lvl) = 750 × lvl^1.4` · **100 niveaux**

### Pourquoi la courbe a changé en passant de 50 à 100 niveaux

Garder `lvl^1.8` aurait donné **961 h pour un seul métier** à 15 k XP/h, contre
140 h pour le niveau 50 — soit trois fois la saison entière pour un huitième du
contenu. Les exposants descendent donc : l'XP de 1,8 à 1,6, l'argent de 1,5 à 1,4.

Le niveau 50 devient un **jalon de milieu de partie** (69 h au lieu de 140) et le
100 l'**horizon longue durée** (412 h). C'est la bonne forme : 100 niveaux doivent
être une progression plus fine, pas le même mur vingt fois plus haut.

| Niveau | XP du niveau | XP cumulé | Money du niveau | Money cumulé | Heures (à 15 k XP/h) |
|---|---|---|---|---|---|
| 5 | 1 313 | 3 215 | 7 139 | 18 583 | 0,2 |
| 10 | 3 981 | 17 353 | 18 839 | 88 111 | 1,2 |
| 15 | 7 616 | 47 814 | 33 234 | 224 566 | 3,2 |
| 25 | 17 247 | 174 545 | 67 948 | 742 060 | 11,6 |
| 35 | 29 547 | 412 627 | 108 832 | 1 641 887 | 27,5 |
| 50 | 52 282 | 1 031 699 | 179 316 | 3 825 801 | 68,8 |
| 65 | 79 554 | 2 028 785 | 258 905 | 7 141 893 | 135,3 |
| 75 | 100 023 | 2 935 452 | 316 335 | 10 044 097 | 195,7 |
| 90 | 133 902 | 4 702 225 | 408 320 | 15 516 666 | 313,5 |
| 100 | 158 489 | 6 175 195 | 473 218 | 19 954 551 | 411,7 |

Lecture pour le rythme de jeu visé :

- **Niveau 10 en ~1 h** → la première soirée donne déjà 2 primes de palier
- **Niveau 25 en ~12 h** → première semaine
- **Niveau 50 en ~69 h** → premier mois, et il reste la moitié de l'échelle
- **Niveau 100 en ~412 h** → ~21 semaines de jeu *entièrement consacrées à ce métier*. En pratique le temps se répartit, donc un favori se maxe sur une saison ou deux et les autres suivent.
- **Un métier maxé rapporte 20 M cumulés par ses niveaux, 28 M avec ses primes.** Les 4 métiers réunis ≈ **113 M**, à comparer aux 500 M de l'autosell : les métiers restent un bonus, jamais la source de revenu. C'est le principe 2 de `DESIGN.md`. Les 8 skills d'avant le remplacement (2026-11-08) en donnaient 160 M ; la courbe par niveau n'a pas bougé, il y a seulement moins de progressions.
- **Les objets de Paladium s'y ajoutent** (`jobs.json`) : 1 050 lingots de paladium et autant de trixium par métier maxé, plus une pioche, une épée et une armure en paladium enchantées. Aux prix du chapitre économique, environ 76 k de paladium et 840 k de trixium par métier : l'argent des niveaux reste la part principale.

Levier unique : `xp_base` dans `data/job_progression.json`. Le passer à 75 réduit tout de 25 %.

### Les sources : le barème de Paladium Bedrock (2026-11-15)

Depuis le 2026-11-15, les sources du Mineur, du Fermier et du Chasseur sont celles du calculateur
de PalaTracker pour Paladium Bedrock, **une XP de Bedrock pour une XP de DoHy**, aux niveaux de
Bedrock fois 5 (détail : `PALAMOD.md`, « Les métiers sur le barème de Paladium Bedrock » ; données :
`tools/bedrock_jobs.py`). La courbe ne change pas. Ce rapport est celui qui égalise le niveau 50
(1 031 699 XP ici, 1 061 305 pour le 10 de Bedrock) : avant, un palier demande jusqu'à deux fois
plus d'XP que sur Bedrock (le 25 : 174 545 contre 87 023), après, de moins en moins (le 100 :
6,2 M contre 35,8 M pour le 20). Les « 15 k XP/h » du tableau restent la référence de rythme ;
le rythme réel dépend maintenant du geste — la pierre ne rapporte plus rien, la fonte des lingots
de PalaMod beaucoup (150 XP le lingot de paladium dès le 40), les animaux et les monstres davantage
qu'avant. Garde-fous : un quarantième pour une créature de spawner, rien pour un petit.

### Primes de palier

Les perks ont été remplacés par de l'argent le 2026-11-11. Tous les 5 niveaux, le palier verse
une **prime de deux fois l'argent du niveau**, en plus de cet argent, arrondie à deux chiffres
significatifs (`prime_factor` et `prime_every_n_levels` dans `job_progression.json`).

| Niveau | Argent du niveau | Prime | Primes cumulées |
|---|---|---|---|
| 5 | 7 139 | 14 000 | 14 000 |
| 10 | 18 839 | 38 000 | 52 000 |
| 15 | 33 234 | 66 000 | 118 000 |
| 25 | 67 948 | 140 000 | 357 000 |
| 35 | 108 832 | 220 000 | 757 000 |
| 50 | 179 316 | 360 000 | 1 687 000 |
| 65 | 258 905 | 520 000 | 3 077 000 |
| 75 | 316 335 | 630 000 | 4 277 000 |
| 90 | 408 320 | 820 000 | 6 537 000 |
| 100 | 473 218 | 950 000 | 8 367 000 |

**8,4 M par métier maxé, 33 M pour les quatre.** Ce qui disparaît avec les perks : vitesse de
minage, portée, points de vie, armure et dégâts, chances de double drop sur les minerais et les
cultures (jusqu'à 35 %), récolte accrue, butin double, bonus d'XP de métier (jusqu'à +25 %). Le
musée garde sa propre chance de double drop, l'amélioration *Apprentissage* et les amulettes
leur bonus d'XP.

**Rattrapage** : un joueur qui avait déjà franchi des paliers reçoit leurs primes à sa première
connexion, en une fois. Le dernier palier payé est noté par métier, si bien qu'une prime ne se
paie jamais deux fois, même si la courbe est rééquilibrée.

### Renaissance (2026-11-20)

Au niveau 100, un métier peut renaître : retour au niveau 0, déblocages gardés, **+10 % d'XP et
d'argent par renaissance**, cumulés, **sans limite**, et les récompenses des niveaux **repayées en
entier** en remontant (choix du joueur). Le palier des primes repart de zéro avec l'XP : la règle
« une prime ne se paie qu'une fois » devient « une fois par tour ».

**Ce que vaut un tour** après *n* renaissances : 28,3 M (20 M d'argent de niveau, 8,4 M de primes)
× (1 + 0,1 *n*), plus les objets de Paladium des paliers (1 050 lingots de paladium et autant de
trixium, l'équipement enchanté). L'XP d'un tour ne change pas (6,2 M), mais le bonus la fait venir
1 + 0,1 *n* fois plus vite. L'argent par heure croît donc comme le **carré** de (1 + 0,1 *n*) :

| Renaissances | Un tour rapporte | Heures par tour à 15 k XP/h | Par heure | Heures par tour à 150 k XP/h | Par heure |
|---|---|---|---|---|---|
| 0 (le premier tour) | 28,3 M | 412 | 69 k | 41 | 688 k |
| 1 | 31,2 M | 374 | 83 k | 37 | 832 k |
| 5 | 42,5 M | 274 | 155 k | 27 | 1,5 M |
| 10 | 56,6 M | 206 | 275 k | 21 | 2,8 M |
| 20 | 85,0 M | 137 | 619 k | 14 | 6,2 M |

À 15 k XP/h (le rythme de référence, § 2), dix renaissances demandent ~3 000 heures : un horizon de
plusieurs saisons, et un métier qui reste un bonus. **Le point à surveiller est le Fermier avec
quatre bots** (§ 6 quinvicies) : ~150 k XP/h de betteraves le mènent au 100 en ~41 heures, soit
~690 k $ par heure de présence dès le premier tour (le revenu de fin de partie du § 3 est de 1 M) ;
dix renaissances en ~300 heures, puis près de 3 M par heure. C'est le choix du joueur (« en entier », « sans limite ») ; si
l'économie s'emballe, les leviers sont, du plus doux au plus fort : `JobRebirth.BONUS` (le bonus par
renaissance), la part de l'argent repayée en remontant (aujourd'hui entière : `JobApi.levelMoney`
et `JobPrimes.amount`), un plafond de renaissances, et pour le seul Fermier la taille des champs des
bots (`BotZone.MAX_SIDE`).

---

## 3. Progression attendue

Hypothèses : **2 h/soir en semaine (10 h), ~10 h le week-end** → **~20 h/semaine**.
Revenu effectif estimé : 25 k/h au début (le prix dynamique et les déplacements mangent la cible théorique), 200 k/h une fois les fermes en place, 1 M/h+ avec autosell et boosters.

| Jalon | Coût | Atteint vers | Heures cumulées |
|---|---|---|---|
| Premier palier de collection | — | soirée 1 | 2 |
| 3 contrats quotidiens + hebdo | — | semaine 1 | 20 |
| **Booster x2** | 2 M | fin semaine 2 | ~40 |
| **Box Saharia** | 1 M | semaine 2 (estimé) | ~30 |
| **Box Paladium** | 5 M | semaine 3-4 | ~70 |
| Banque rentable (dépôt > 10 M) | — | mois 2 | ~110 |
| **Booster x3** | 25 M | mois 2 | ~130 |
| **Box Halloween** | 20 M | mois 2 (estimé) | ~120 |
| **Box Trixium** | 50 M | mois 2-3 (estimé) | ~160 |
| **Box Endium** | 150 M | mois 3 | ~200 |
| **Booster x4** | 300 M | mois 3-4 | ~250 |
| **Autosell** | 500 M | mois 4 | ~300 |

Arc visé : **une saison de 4 mois**, avec un palier franchi chaque semaine et un gros déblocage par mois.

**À surveiller en jeu :**
- Si le x2 tombe avant la fin de la semaine 1 → baisser `MONEY_MULTIPLIER` à 0,6
- Si l'autosell n'est pas atteint au mois 6 → le monter à 1,4
- Si une soirée de semaine ne fait franchir aucun palier → le problème est dans les contrats, pas dans les prix

---

## 4. Sinks

### Banque — barème corrigé

Le barème initial était négligeable : plafond de 50 k par jour de jeu, qui mordait
dès **10 M épargnés**, soit 4 166 $ par soirée de 2 h. L'intention écrite était
qu'une soirée sans grind fasse progresser ; c'était raté d'un facteur 5 à 6.

Barème retenu dans un premier temps : **2 % par jour de temps de jeu, plafonné à 2 M**.

### Passage au temps réel — 2026-11-05

Les cycles sont passés en temps réel (voir `DESIGN.md`, principe 2). Si on garde
2 %/jour en changeant l'unité, le rendement est multiplié par le rapport entre un
jour entier et une journée de jeu — soit **environ 12** au rythme visé (~3 h de jeu
par jour en moyenne, 2 h en semaine et beaucoup le week-end). Attendre serait alors
devenu douze fois plus rentable que jouer : exactement ce que le compteur en temps
de jeu cherchait à empêcher.

Le taux est donc divisé par le même rapport, ce qui laisse le revenu d'une soirée
inchangé et le rend seulement indépendant du moment où on joue :

Barème retenu : **0,25 % par jour réel, plafonné à 250 k**.

| Épargné | Par jour réel | Par semaine | Ancien équivalent par soirée de 2 h |
|---|---|---|---|
| 1 M | 2 500 | 17 500 | 1 667 |
| 10 M | 25 k | 175 k | 16 667 |
| 50 M | 125 k | 875 k | 83 333 |
| 100 M et plus | 250 k (plafond) | 1,75 M | 166 667 |

Les intérêts courent monde fermé : ils sont rattrapés à la connexion, plafonnés à
trente jours d'un coup. Ce plafond n'est pas de l'équilibrage — trente jours
plafonnés font 7,5 M, c'est modeste — mais une protection d'horloge : une date
système changée ou une sauvegarde recopiée d'une machine à l'autre ne doit pas se
traduire en crédit absurde.

Volontairement négligeable en début de partie, comparable à une récolte au milieu,
et largement dépassé par le jeu actif en fin : **une épargne ne doit jamais
remplacer le jeu**.

Tous ces chiffres, ainsi que ceux de la pénalité de mort, vivent dans
`data/economy.json` et se rechargent par `/dohy reload`.

| Sink | Coût | Rôle |
|---|---|---|
| Frais de dépôt banque | 3 % | friction permanente |
| Assurance de mort | 50 k | récurrent, début/milieu |
| Clé commune | 100 k | récurrent |
| Booster x2 | 2 M | premier objectif réel |
| Clé rare | 5 M | milieu de partie |
| Booster x3 | 25 M | milieu |
| Clé légendaire | 150 M | fin |
| Booster x4 | 300 M | pari de week-end |
| Autosell | 500 M | objectif terminal |

Prix d'achat des items : `base_price × 4` (`BUY_MARKUP`). Racheter ce qu'on a vendu coûte 4× — pas de boucle rentable.

---

## 4 bis. Améliorations permanentes — le puits à argent qui ne s'épuise pas

### Le trou qu'elles bouchent

Tous les autres puits à argent sont **consommables ou ponctuels** : un booster se
dépense, une caisse est un pari, une assurance se consomme, et l'autosell à 500 M
est un achat **unique**. Passé ce point — vers le mois 4 — l'argent n'avait plus
de destination : on continuait à en gagner sans que ça change quoi que ce soit.
C'est comme ça que meurt une économie de jeu.

### Le barème

| Branche | Effet | Paliers | Prix |
|---|---|---|---|
| Negoce | −5 / −10 / −15 % à l'achat | 3 | 25 M · 65 M · 170 M |
| Patrimoine protégé | −20 / −40 / −60 % à la mort | 3 | 50 M · 130 M · 340 M |
| Plafond de banque | ×2 / ×4 / ×8 / ×16 | 4 | 80 M · 210 M · 540 M · 1,4 G |
| Carnet de contrats | +1 / +2 contrats par jour | 2 | 120 M · 360 M |
| Apprentissage | +15 / +30 / +50 % d'XP de métier | 3 | 150 M · 390 M · 1,0 G |
| Endurance des boosters | +10 / +25 / +45 min | 3 | 200 M · 520 M · 1,4 G |
| Cadence d'autosell | toutes les 4 / 3 / 2 min | 3 | 300 M · 780 M · 2,0 G |
| Résilience du marché | +3 / +6 / +10 points de plancher | 3 | 400 M · 1,1 G · 3,1 G |

**24 paliers, 14,83 G pour tout monter.** Environ un an de jeu à 15 M/h, deux ans
et demi à 6 M/h. Chaque palier coûte ~2,6× le précédent, et le **premier palier
de chaque branche tient entre 25 M et 400 M** — donc accessible dans les premiers
mois, ce qui est l'essentiel : les premiers paliers font la sensation de jeu, la
queue est l'horizon.

**Une première version chiffrait 328 G** (escalade ×4, cinq paliers sur certaines
branches), soit plus de seize mille heures même à 20 M/h. Les derniers paliers
auraient été du contenu mort — pire que pas de palier.

### Deux garde-fous

- **Résilience du marché est volontairement la plus chère** : c'est la seule
  branche qui atténue le vrai frein de l'économie. Son maximum porte le plancher
  cumulé d'un `FOUND` à 0,85, donc **la borne de 0,75 mord** (`MAX_FLOOR`) — le
  frein survit à tous les achats possibles, par construction.
- **Plafond de banque relève le plafond et jamais le taux.** Relever le taux
  aurait rendu l'attente plus rentable que le jeu, ce que tout le calibrage de la
  banque cherche à éviter.

### Un effet déclaré est un effet branché

`UpgradeApi.EFFECTS` liste les huit effets que le code lit réellement, et le
chargement du datapack **prévient pour tout effet absent de cette liste**. Même
filet que pour les perks de métier, posé pour la même raison : on a découvert 33
perks déclarés et inertes. Une amélioration payée plusieurs milliards doit faire
quelque chose.

Une neuvième branche était prévue — ajouter des rangées au coffre de vente — et a
été **abandonnée parce qu'elle est impossible** : un menu de coffre plafonne à six
rangées et le coffre de vente y est déjà. Elle est devenue Negoce.

---

## 5. Pénalité de mort

```
perte = 0                                     si morts < 3
perte = min( 0.08 × solde_liquide ,
             50 000 + 0.01 × gains_cumules )  sinon
perte = perte × 0.5                           si assurance
```

Valeurs constatées : à 1 M liquide et 3 M de gains cumulés → perte de 80 k, plafond à 80 k. À 500 M liquide et 2 B de gains → perte plafonnée à 20,05 M, soit 4 % du liquide au lieu de 8 %. Le plafond devient la contrainte dominante en fin de partie, ce qui est l'effet voulu : la mort reste une gêne, jamais un abandon.

---

## 6. Prix dynamiques — comportement attendu

```
prix = base × clamp( (S₀/(S₀+vendu))^0.6 , plancher , 1.8 )
```

| Vendu (tier FARM, S₀ = 2 000) | Multiplicateur |
|---|---|
| 0 | 1,00 |
| 500 | 0,87 |
| 2 000 | 0,66 |
| 10 000 | 0,38 |
| 50 000 | 0,19 |
| 120 000 + | 0,15 (plancher) |

Oubli : **−10 % de `vendu` par 20 min de temps réel**, soit retour à ~5 % du pic après 20 h — donc en une nuit, hors ligne comprise (révisé le 2026-11-05).

En temps de jeu, la remontée exigeait d'être en jeu : un prix écrasé le dimanche
restait écrasé une douzaine de soirées, et il fallait grinder sa propre remontée.
En temps réel, se reconnecter le lundi soir retrouve des prix largement refaits.

| Vendu (tier FOUND, S₀ = 64) | Multiplicateur |
|---|---|
| 0 | 1,00 |
| 64 | 0,66 |
| 256 | 0,44 |
| 1 000 | 0,35 (plancher) |

Un stack de diamants vendu d'un coup fait déjà tomber le prix à 66 %. Voulu : ça récompense l'étalement et la diversification.

---

## 6 bis. NTM NEXT — minerais et pièces de machine

Révisé le 2026-11-11. La première version mettait au shop tout ce que NTM range sous sept tags
de forme (brut, gemme, lingot, plaque, poudre, pépite, cristal) : **223 items**, isotopes,
déchets et alliages exotiques compris, et une matière inconnue y prenait le prix d'une matière
courante. Il n'en reste que deux familles, **65 items** :

- **les minerais** : dix matières brutes minées, à la vente, et leurs lingots, à l'achat ;
- **les pièces de machine** : plaques, fils, bobines, tuyaux, boulons, moteurs et circuits,
  à l'achat.

Ni alliage, ni plastique, ni isotope, ni matière exotique : ils se fabriquent. Une matière hors
du catalogue n'a pas de prix du tout. Le rattachement passe par le sous-tag exact
(`c:ingots/thorium` contient `ingot_th232`), plus par un mot-clé dans l'identifiant : l'ancien
système classait `ingot_schraranium` comme de l'uranium.

### Les huit crans du wiki

La rareté vient de la ligne *Difficulty of Production* du wiki du mod
([nucleartech.wiki](https://nucleartech.wiki)). L'achat est multiplié par **5** à chaque cran,
la vente par **2,83** seulement, pour la même raison qu'avant : un achat est un puits à argent
qu'on ne peut pas exploiter, une vente est un revenu.

| Cran | Lingot à l'achat | Matière brute à la vente |
|---|---|---|
| Very Easy | 900 | 35 |
| Easy | 4 500 | 100 |
| Easy-Medium | 22 500 | 283 |
| Medium | 112 500 | 800 |
| Medium-Hard | 562 500 | 2 267 |
| Hard | 2,80 M | 6 400 |
| Very Hard | 14,00 M | 18 000 |
| Extremely Hard | 70,00 M | 51 203 |

Les quatre anciennes raretés tombent exactement sur les crans pairs : commune = Easy, peu
commune = Medium, rare = Hard, très rare = Extremely Hard. Les 70 M du lingot très rare
demandés à l'origine sont conservés. Les crans intermédiaires du wiki s'intercalent entre eux.

### Les minerais

La matière brute **se vend et ne s'achète pas** : achetée puis traitée, elle donnerait plus
d'un lingot, donc un lingot moins cher que celui du shop. Le lingot **s'achète et ne se vend
pas** : vendu, il ferait du rendement des chaînes de traitement un revenu infini.

| Matière | Cote du wiki | Brut, vente | Lingot, achat |
|---|---|---|---|
| plomb | Very Easy | 35 | 900 |
| uranium | Easy | 100 | 4 500 |
| thorium | Easy | 100 | 4 500 |
| tungstène | Easy | 100 | 4 500 |
| béryllium | Easy | 100 | 4 500 |
| titane | *aucune* (Easy retenu) | 100 | 4 500 |
| aluminium | Easy-Medium | 283 | 22 500 |
| cobalt | Easy-Medium | 283 | 22 500 |
| plutonium | Easy-Medium | 283 | 22 500 |
| lithium | Medium | 800 | 112 500 |

Le wiki ne cote pas le titane ; c'est le minerai le plus répandu du mod (veines, amas,
gisements), d'où Easy. Par rapport à l'ancienne grille, thorium, tungstène et béryllium
descendent d'un cran, l'aluminium monte d'un, le plomb descend d'un.

**Le schrabidium n'est plus vendable.** L'ancienne grille vendait sa matière brute 51 203
l'unité au motif qu'il « a un minerai ». Lu dans le source : ce minerai ne se génère pas, il
naît d'une explosion nucléaire sur du minerai d'uranium (`ExplosionNukeGeneric`). Une bombe sur
un filon d'uranium aurait imprimé des millions.

### Ce que rapporte la matière brute

Le lithium, la plus chère des dix, sous le prix dynamique (`FOUND` : S0 = 64, plancher 35 %) :

| Vendu d'un coup | Recette | Par unité | % du prix de base |
|---|---|---|---|
| 16 | 12 001 | 750 | 94 % |
| 64 | 41 034 | 641 | 80 % |
| 256 | 115 916 | 453 | 57 % |
| 1 024 | 331 538 | 324 | 40 % |

Le haut de l'échelle de vente était à 51 203 l'unité ; il est à 800. C'est voulu : les
matières qui valaient ce prix ne se minent pas.

### Les pièces de machine

**45 pièces, en achat seul.** Leur prix est **115 % de ce que coûtent leurs ingrédients, par la
recette la moins chère du mod**, calculé hors du jeu par `tools/gen_ntm_prices.py` à partir des
recettes de NTM NEXT. Une pièce ne coûte donc jamais moins au shop que ce qu'il faut pour la
fabriquer : le détour se paie 15 %.

Ce que coûte un ingrédient :

- un item vanilla : son prix d'achat de `prices.csv` (lingot de fer 60, de cuivre 120, d'or 330) ;
- un métal du shop : son lingot ;
- un alliage ou un plastique, qui ne sont pas au shop : le prix du lingot de son cran, comme
  s'il y était. Compter leur recette ne marcherait pas : la plupart sortent d'une usine chimique
  dont les fluides n'ont pas de prix, et le polymère passerait pour gratuit.

| Matière de référence | Cran | Source |
|---|---|---|
| acier | Easy | wiki |
| cuivre rouge | Easy | *non coté* — cuivre et redstone au haut fourneau, comme l'acier |
| carbone | Easy | *non coté* — du charbon au four |
| acier rapide, niobium | Easy-Medium | wiki |
| silicium | Easy-Medium | *non coté* — du sable au four à arc |
| polymère | Medium | wiki |
| caoutchouc | Medium | *non coté* — il remplace le polymère dans plusieurs recettes |
| desh, tantale, tungstène magnétisé | Medium-Hard | wiki |

Les fluides et l'énergie ne comptent pas, d'où les 15 %. Les recettes de recyclage, à sortie
aléatoire ou derrière une option de NTM désactivée par défaut (mode 528, LBSM, mode cher) sont
ignorées.

| Famille | Pièces et prix |
|---|---|
| plaques | fer 69 · cuivre 140 · or 380 · plomb 1 000 · titane 5 200 · aluminium 26 000 |
| fils fins | cuivre 17 · or 47 · plomb 130 · tungstène 650 · aluminium 3 200 |
| fils denses | cuivre 140 · or 380 |
| bobines | or 450 · or torique 900 · cuivre 5 200 · tungstène 5 200 · cuivre torique 10 000 · tungstène magnétisé 650 000 |
| tuyaux | fer 210 · cuivre 410 · plomb 3 100 · acier 16 000 · aluminium 78 000 · acier rapide 78 000 · caoutchouc 390 000 |
| boulons | plomb 130 · tungstène 650 · acier 650 · acier rapide 3 200 |
| moteurs | moteur 7 900 · moteur en desh 1,6 M |
| circuits | circuit imprimé 110 · tube à vide 760 · afficheur à sept segments 1 800 · condensateur 3 000 · circuit analogique 9 200 · wafer de silicium imprimé 17 000 · micropuce 17 000 · circuit intégré 70 000 · condensateur au tantale 72 000 · carte de condensateurs 220 000 · circuit militaire 550 000 · boîtier d'unité de contrôle 790 000 · unité de contrôle 3,1 M |

Les écarts suivent les ingrédients : une bobine d'or (or vanilla, 330 le lingot) coûte moins
qu'une bobine de cuivre, faite de fil de cuivre rouge, un alliage de NTM au cran Easy. C'est la
règle de toute la grille NTM : ce qui ne demande que du vanilla reste bon marché, ce qui passe
par une machine de NTM se paie au prix de la machine qu'on n'a pas.

**Hors du shop** : le palier bismuth et au-delà (puces bismoïdes et quantiques, horloge atomique,
circuits bismoïde, quantique et cristallin, unités de contrôle avancée et quantique, moteur au
bismuth), contenu de fin de partie. Le fil dense de titane, celui de tungstène et les tuyaux
d'acier n'ont pas de recette dans NTM NEXT.

**À refaire si NTM change ses recettes** :

```bash
python3 -I tools/gen_ntm_prices.py <ntm-next>/neoforge/src/main/resources/data
```

## 6 ter. PalaMod

Vingt-quatre matières premières, au modèle commun (tableau complet et raisons dans
`PALAMOD.md` § 19). Les métaux renouvelables — améthyste 30, titane 45, paladium 72 — sont
`FARM` : crusher, cultures, sève et golems en produisent sans fin, comme les golems de fer
produisent le fer à 15. Ce qui ne sort que de la mine est `FOUND` : paladium vert 500, findium
400, trixium 800, minerais à la pioche soie de 100 à 1 600. La pépite d'endium est `RARE`,
40 000. Les rapports de conversion des machines sont tenus : au crusher, 15 titanes coûtent
16 améthystes et 15 paladiums 32 titanes, donc aucun prix ne rend une conversion gratuite.

## 6 quater. Loot boxes — le butin

Six boxes, celles de PalaMod, chacune avec sa clé, son prix et sa table (`data/crates.json`). Les
trois caisses d'avant — commune, rare, légendaire — sont devenues les boxes d'**améthyste**, de
**paladium** et d'**endium**, sans changer de prix ni de butin ; **Saharia**, **Halloween** et
**Trixium** s'intercalent, chacune avec un thème. Décidé par le joueur le 2026-11-11 (« six
boxes, six butins »).

| Box | Clé | Le thème | Gros lot |
|---|---|---|---|
| Améthyste | 100 k | les matériaux du début : paladium, paladium vert, trixium, findium, panoplie en paladium, pièces NTM | lingot d'endium, enderchest de poche |
| Saharia | 1 M | le pillage : TNT en endium, éponge et à wither, kit du pilleur, obsidiennes piégées, spikes, withers de siège | gemme de wither suprême, 5 lingots d'endium |
| Paladium | 5 M | l'endium en pépites et en outils, la poche du voyageur, les machines NTM avancées | pierre légendaire aléatoire |
| Halloween | 20 M | la chasse et les withers : rituels suprêmes, obsidiennes infusées, améliorations de spawner, pierre légendaire | cinq pierres légendaires |
| Trixium | 50 M | le voyage et l'endium en lingots : enderchests de poche, coffres et seedplanters en endium, pierres légendaires | 24 blocs d'endium |
| Endium | 150 M | panoplies en endium, pierres, usine NTM | gant en endium complet |

Le butin vient de PalaMod et de **NTM NEXT** (lingots par tag, pièces de machine), plus de
l'argent. Les six boxes portent des lots NTM :

| Box | Lots NTM NEXT | Part du tirage |
|---|---|---|
| Améthyste | lingots de plomb, d'uranium et d'aluminium, moteurs, circuits de base et avancés | 24 % |
| Saharia | 22 lingots de plutonium, pièces de machine (carte à condensateurs, circuits, moteurs), 11 lingots de lithium, 5 circuits avancés | 14,5 % |
| Paladium | lingots de lithium, machines avancées, contrôleurs | 26 % |
| Halloween | 3 contrôleurs, 6 moteurs en desh, l'automate (5 et 5), 16 contrôleurs | 14,5 % |
| Trixium | 8 contrôleurs, 16 moteurs en desh, la chaîne de montage (12 et 15), l'atelier (24 et 32) | 14,5 % |
| Endium | 24 contrôleurs, 48 moteurs en desh, l'usine (64 et 64) | 22 % |

### La règle

Chaque box rend **80 à 95 % de son prix en valeur boutique** : le prix d'achat au shop, ou, pour
un objet qui ne s'achète pas, ce que coûtent ses ingrédients au shop. `tools/crate_values.py` le
calcule et sort en erreur hors de la fourchette. En liquide, la box prend bien plus : un objet
se revend au prix de base, le quart du prix boutique, et seule une partie du butin est de l'argent.

| Classe | Chance | Lot moyen | Exemples |
|---|---|---|---|
| Commun | ~68 % | ~0,5 × le prix | 16 blocs de paladium, 12 TNT en endium, 22 lingots de lithium, 17 lingots d'endium |
| Rare | ~24 % | ~1,2 × | outils en paladium vert enchantés, arsenal de siège, trois pierres, les six pierres |
| Épique | ~7 % | ~2,5 × | 2 pépites d'endium, 64 TNT en endium, plastron en endium, 28 blocs d'endium |
| Légendaire | ~1 % | 5 à 14 × (2 × pour l'endium) | lingot d'endium, pierre légendaire aléatoire, gant complet, argent |

| Box | Avec NTM | Sans NTM |
|---|---|---|
| Améthyste (100 k) | 87,8 % | 89,0 % |
| Saharia (1 M) | 89,3 % | 90,3 % |
| Paladium (5 M) | 87,5 % | 88,0 % |
| Halloween (20 M) | 86,0 % | 87,4 % |
| Trixium (50 M) | 88,4 % | 89,5 % |
| Endium (150 M) | 84,5 % | 90,2 % |

### Les pierres légendaires, box par box

La pierre est cotée 20 M. Trois boxes de plus en donnent, ce qui les rend moins rares qu'avant —
mais jamais moins chères qu'au marché, où elles passent deux jours sur cinq entre 14 M et 32 M
(§ 6 decies) :

| Box | Pierres par clé, en moyenne (avec NTM) | Soit, par pierre |
|---|---|---|
| Paladium | 0,006 | ~830 M |
| Halloween | 0,12 | ~170 M |
| Trixium | 0,26 | ~190 M |
| Endium | 0,76 (et le gant : 0,04 de plus) | ~190 M |

### Les cotes fixées à la main

- **Pierre légendaire** : 20 M. Elle ne s'achète pas à la boutique ; c'est quatre clés Paladium,
  et le gros lot de la box Paladium.
- **Gant complet** : six pierres plus sa recette, ~123 M. C'est le lot légendaire de la box
  Endium, en dessous du prix de la clé : sa rareté (0,7 %) et ses effets font sa valeur.
- **Enderchest de poche** 1,5 M et **fabrique à nuage** 400 k : les récompenses du concours Trixium.
- **Enchantement** : 1 500 par niveau. Un outil en paladium enchanté vaut surtout son enchantement.

### Sans NTM NEXT

Une entrée dont un objet n'existe pas (mod absent, tag vide) sort du tirage, et les chances se
répartissent sur le reste. Les lots NTM sont répartis entre les classes pour que le rendement bouge
peu : les deux colonnes du tableau restent dans la fourchette.

## 6 quinquies. Donjons — les jarres

Une jarre brisée donne, une fois sur quatre, **un** tirage de la réserve du donjon (la même que
celle des coffres, bornée par le palier). Une salle en compte de 3 à 6 : en moyenne un tirage de
plus par salle de combat, à comparer aux `2 + palier / 50` tirages du coffre de chaque joueur. La
jarre est au premier qui la casse, pas à chacun : en groupe, le gain par joueur baisse d'autant.
Les récompenses uniques, le butin des boss et les notes ne passent jamais par une jarre.

## 6 sexies. Clicker — le Clic Shop

Le clicker a sa propre monnaie, les ClicCoins, qui ne se convertit pas en argent et ne sort du
clicker que par le Clic Shop. C'est donc lui, et lui seul, qu'il faut borner.

**La règle.** Une offre s'ouvre avec un bâtiment et coûte une part de son prix de base (de 0,2 à
2 fois) : acheter une offre, c'est renoncer à un bâtiment. Elle se paie un tiers de plus à chaque
achat et s'épuise après 1 à 3 achats par rotation.

**Ce que ça donne.** La production ne tourne qu'en jeu. À 1 h 30 par jour et quelques minutes de
clic, un joueur possède 9 bâtiments au jour 30 et 14 au jour 90 (simulation, achats au meilleur
retour) ; à 3 h par jour, 13 et 21. Les offres ouvertes à ces rangs :

| Rang atteint | Offres de la semaine les plus chères (valeur boutique) |
|---|---|
| 5 à 9 | 64 lingots de paladium (18,6 k), 16 diamants (64 k), une clé de Talikus ou de Nimbria (11 k) |
| 11 à 15 | 8 blocs de paladium (20,9 k), 16 lingots de paladium vert (32 k), clé de Vitalys |
| 21 et plus | 3 fragments d'endium (53 k), puis une pépite (160 k), puis un lingot d'endium (1,44 M) au rang 31 |

Huit offres par semaine, deux achats au plus de chacune : quelques centaines de milliers par
semaine au milieu du jeu, soit moins d'une heure de revenu. Le lingot d'endium demande le 31ᵉ
bâtiment (1 550 milliards de ClicCoins), hors de portée d'un joueur qui ne laisse pas tourner le
jeu. **Le Clic Shop est un bonus, pas un revenu** ; s'il devait le devenir, baisser `quantity`
dans `tools/clicker_data.py` suffit.

Le rayon des métiers vend des bouteilles d'XP (1 000 XP chacune) : 1, 3 puis 5 par offre, deux
achats par semaine et par métier tiré — au plus 40 000 XP par semaine, tous métiers confondus.

## 6 septies. Contrôleur de spawners

Les chiffres sont ceux de PalaSpawner, non retouchés. Ce que rend **un** spawner customisé
compté par un contrôleur, à son palier :

| Palier | Âmes | Créatures par vague | Vague | Créatures par heure |
|---|---|---|---|---|
| I | 100 | 2 à 3 | 12 s | 750 |
| II | 150 | 3 à 5 | 15 s | 960 |
| III | 200 | 5 à 7 | 15 s | 1 440 |
| IV | 250 | 6 à 11 | 7 s | 4 370 |

Une structure pleine (27 à 108 spawners) irait de 20 000 à 470 000 créatures par heure, soit
dix à deux cents fois les 2 000 chairs putréfiées par heure du modèle des prix. Trois choses
bornent ce chiffre :

- **les âmes** : une âme, c'est un spawner de donjon cassé au marteau (256 coups par marteau).
  Un spawner de palier I en demande 100, une structure IV pleine 27 000 — la ressource rare est
  là, et elle ne se fabrique pas ;
- **le prix plancher** : les butins de créatures saturent vite (`s0` = 256, plancher 20 %). Dix
  spawners de palier I en automatique, c'est 7 500 chairs par heure vendues 2,40 $ pièce au
  plancher : 18 000 $ par heure, moins qu'une heure d'activité ordinaire (24 000 $) ;
- **les coffres** : cinq inventaires ; pleins, la production s'arrête. Et le contrôleur ne
  travaille que tronçon chargé.

Le mode manuel donne en plus l'XP et l'XP de métier d'une créature tuée, à deux coups par
seconde au plus : 7 200 créatures par heure, clic tenu. C'est mieux qu'une ferme à créatures,
pas d'un autre ordre.

Si un serveur voyait ses prix de butin s'effondrer, les quatre lignes de `SpawnerTier` sont le
seul réglage à toucher.

## 6 octies. Les coffres à poser

Le coffre animé d'une box, posé chez soi, ouvre la même table que la roulette avec la même clé :
il ne change **aucune** espérance de gain (§ 6 quater). Ce qu'on paie est le coffre.

| | Prix |
|---|---|
| Un coffre à poser, n'importe lequel | 25 000 $, une fois |
| Une clé | celui de sa box : 100 k, 1 M, 5 M, 20 M, 50 M, 150 M |

25 000 $, c'est une heure d'activité ordinaire au début du jeu (24 000 $ par heure) et un quart
de clé Améthyste : assez pour être un achat, trop peu pour retarder la première box. Les six ont
le même prix : le coffre est un décor, la clé est le pari. `PalaBoxes.PRICE` est le seul réglage.

## 6 nonies. Clés en butin

Une clé vaut son prix à la boutique, mais ce qu'elle rapporte **en liquide** à qui revend tout
est bien moindre : la box rend ~88 % en valeur boutique et la revente se fait au quart. C'est ce
chiffre qui borne les sources.

| Clé | Prix | En liquide |
|---|---|---|
| Améthyste | 100 k | ~22 k |
| Saharia | 1 M | ~220 k |
| Paladium | 5 M | ~1,1 M |
| Halloween | 20 M | ~4,3 M |
| Trixium | 50 M | ~11 M |
| Endium | 150 M | ~33 M |

Chaque box à thème a sa source, en plus de la boutique et du marché.

**Coffre de la note d'un donjon réussi** (quatre tirages séparés) :

| Note | Clé Améthyste | Clé Paladium (palier 40 et plus) | Clé Trixium (palier 60 et plus) | Clé Endium (palier 80 et plus) |
|---|---|---|---|---|
| D, C | — | — | — | — |
| B | 10 % | — | — | — |
| A | 20 % | 0,5 % | 0,1 % | — |
| S | 35 % | 1,5 % | 0,4 % | 0,25 % |

En liquide, une note S rapporte donc en moyenne ~8 k avant le palier 40, ~24 k ensuite, ~68 k à
partir du palier 60, ~150 k à partir du palier 80. À quatre donjons par heure, c'est de 30 k à
600 k par heure : au-dessus d'une heure ordinaire du début de partie pour qui tient la note S, et
un cinquième du revenu de fin de partie (~3 M par heure quand la box Endium devient abordable).
Une note médiocre ne donne rien.

**Boss d'événement vaincu** : une clé Améthyste à chaque joueur qui l'a frappé, une clé Paladium
dans 5 % des cas, une clé **Halloween** dans 1 % — ~120 k en liquide par boss, pour un combat qui
peut durer trente minutes.

**Base ennemie pillée** : des clés **Saharia**, la box du pillage — de quoi préparer le raid
suivant. Un avant-poste en laisse une une fois sur quatre, une base une, une forteresse trois :
~56 k, ~220 k et ~670 k en liquide, à côté des 40 k, 200 k et 800 k versés à la banque de faction.
Le butin d'un raid double à peu près, en TNT plutôt qu'en argent.

Les réglages sont les quatre tableaux `KEY_*` de `DungeonRewards`, `PALADIUM_KEY_CHANCE` et
`HALLOWEEN_KEY_CHANCE` de `BossFight`, et `OUTPOST_KEY_CHANCE` de `EnemyBases`.

## 6 decies. Marché — l'hôtel des ventes

Jusqu'au 2026-11-19, le marché **ne payait jamais** : c'était un puits. Il **achète** maintenant
aussi, ce que le joueur met en vente (`PALAMOD.md` § 34, « Vendre ») : c'est une source d'argent,
tenue par trois règles.

**Pas de revente gagnante.** Les acheteurs paient au plus **0,69 fois la cote** (0,6 au prix
conseillé, fois 1,15 au prix fort). L'annonce la moins chère d'un vendeur fictif est à 0,70 fois
la cote, une clé en promotion à 0,75 et à la boutique à son prix plein : acheter pour revendre
perd toujours. Pour les clés et les coffres à poser, le facteur est même de **0,35** (au plus 0,40
fois leur prix). `tools/market_offers.py` le vérifie à chaque génération, et la CI aussi, contre
les bornes des annonces et contre le shop (remise la plus forte et cours le plus bas compris).
Seule exception, bornée : la marchandise volée du marché noir (0,5 à 0,7 de sa valeur, quatre
offres par jour, un grief contre la faction) peut se revendre un peu plus cher qu'achetée — un
écart de quelques pièces par jour, pas une machine.

**Pas de contournement du shop.** Ce qui se fabrique surtout avec des matières que le shop vend ou
rachète (lingots, obsidienne, étoile du Nether, pépites d'endium, crânes de wither squelette...)
n'est pas pris : le vendre ici, c'était vendre ces matières sans que leur prix baisse, à 2,4 fois
leur prix de base. Le script mesure, recette par recette, la part de la cote qui vient de telles
matières ; au-delà de 30 %, l'objet est marqué `buyers: false`. 14 objets de la réserve sont ainsi
écartés (toute la chasse et le wither, le gant et le lingot d'endium, l'amulette du chasseur), et
la pépite d'endium, que le shop rachète. Restent 78 objets dont la valeur vient du butin : pierres
légendaires, récompenses du trixium, runes, essences, équipement de donjon, clés, coffres à poser.

**Ce que ça rapporte.** Au prix conseillé, une pierre légendaire rapporte 12 M, une pièce de
donjon renforcée 1,2 M (basique 240 k), une rune épique 1,5 M, un fragment de rune commune 24 k ;
une clé Paladium 1,75 M, une clé Endium 52,5 M (ouverte, elle rend ~22 % de son prix en liquide :
la revendre est un peu mieux, pas trois fois mieux). Le receleur du marché noir prend les mêmes
pièces à 25-40 % tout de suite, trois par jour : il reste la vente immédiate, l'hôtel des ventes
la vente patiente. Le débit est celui du butin : six annonces à la fois, et ce que les donjons, les
boxes et les events donnent.

**Les clés.** Une clé en promotion coûte de 75 à 100 % de son prix. La box rend ~88 % de ce
prix en valeur boutique et ~22 % en liquide : même à 75 %, ouvrir pour revendre perd de l'argent,
et revendre la clé à l'hôtel des ventes (au plus 40 %) aussi. Ce que la promotion change, c'est le
prix d'accès — une clé Endium à 112 M au lieu de 150 M, quand elle passe (une annonce sur
quarante-trois du rayon). Les six clés sont au rayon, d'autant plus rares que la box est chère
(`KEY_OFFERS`).

**Les pierres légendaires.** Cotées 20 M (§ 6 quater), elles étaient le gros lot de la box
Paladium (alors « caisse rare ») et ne s'achetaient nulle part. Elles sont maintenant en vente environ **deux jours sur cinq**,
entre 14 M et 32 M. C'est le changement d'équilibrage du marché, voulu par le joueur (« raretés
de PalaMod ») : la pierre devient un achat de milieu de partie plutôt qu'un coup de chance. Si
c'est trop, baisser les poids des pierres dans `tools/market_offers.py`. Revendue, elle rapporte
de 10,2 M (vente rapide) à 13,8 M (prix fort).

| Réglage | Valeur | Où |
|---|---|---|
| Annonces par jour | 3 + 4 + 2 + 3 | `CATEGORIES` |
| Bonne affaire / courant / gourmand | 15 % / 65 % / 20 % | `PRICES` |
| Renouvellement payé | 250 k, doublé, 4 par jour (3,75 M au plus) | `per_reroll`, `max_rerolls` |
| Cotes sans valeur boutique | fragment de rune 40 k et 150 k, rune 170 k, 620 k, 2,5 M ; essence 20 k, 80 k, 300 k ; pièce de donjon 400 k et 2 M ; capacité de spawner 450 k | `QUOTES` |
| Prix conseillé des acheteurs | 0,6 fois la cote ; 0,35 pour les clés et coffres à poser | `BUYERS` |
| Vente rapide / conseillé / fort | 0,85 / 1 / 1,15 fois le conseillé ; 20 min / 3 h / 27 h en moyenne | `BUYERS["presets"]` |
| Annonces du joueur | 6 à la fois, invendu rendu au bout de 48 h | `BUYERS` |
| Part de matières du shop tolérée | 30 % | `FARMABLE_LIMIT` |

## 6 undecies. Ruines à spawner de la dimension du Mineur

Les ruines (`docs/PALAMOD.md` § 18) sont la source des **âmes** du marteau des cavernes ; leur
fréquence règle donc le rythme du spawner customisé.

- **Âmes** : un spawner customisé s'éveille à 100 âmes d'une même créature, et plafonne à 250. Six
  créatures à parts égales : il faut vider environ 600 ruines pour un premier palier d'une créature
  donnée, 1 500 pour le plafond. Mesuré : une ruine tous les 18 chunks en surface, une salle tous
  les 12 chunks sous terre. Une section de 5 000 × 5 000 (97 656 chunks) de terre ferme porterait
  donc environ 5 400 ruines et 7 900 salles, soit 900 et 1 300 par créature : une section suffit à
  monter plusieurs spawners, sans que cela se fasse en une soirée.
- **XP de Chasseur** : 1 250 XP de PalaJobs par spawner cassé à partir du niveau 40, soit environ
  446 XP d'ici (rapport 0,357). À une ruine par minute, 27 000 XP à l'heure : 0,4 % de la courbe
  entière (6 175 195 XP pour le niveau 100). Pas de quoi court-circuiter la chasse.
- **Argent** : aucun. Les ruines n'ont pas de coffre, et un spawner cassé ne rend rien sans marteau.
- **Risque** : les spawners de monstres tournent. La mort reste évitée dans la dimension (retour à
  l'arrivée) et une explosion n'y casse rien ; un creeper ne coûte donc que de la vie.

Leviers : `chance` dans `placed_feature/miner/ruin.json` (9) et `crypt.json` (10).

## 6 duodecies. Musée — des pièces rares seulement

Jusqu'au 2026-11-11 le musée demandait un exemplaire de **chaque objet du catalogue de prix** :
six ailes calquées sur les onglets du shop, 152 vitrines au départ, 176 avec PalaMod, 241 avec les
65 pièces de machine de NTM. L'essentiel se remplissait en vidant un coffre de blé et de pierre.
Il n'expose plus que ce qui se mérite. Les pièces sont **nommées une à une** dans
`data/museum.json` : la rareté ne se déduit pas d'un prix.

| Aile | Pièces | Ce qu'on y expose | Bonus de l'aile complète |
|---|---|---|---|
| Trésors du monde | 6 | élytres, cœur de la mer, pomme dorée enchantée, noyau lourd, éclat d'écho, éponge | +12 % de double drop |
| Trophées | 6 | étoile du Nether, tête de dragon, crâne de wither squelette, totem d'immortalité, trident, carapace de shulker | +1,5 dégât |
| Reliques de PalaMod | 11 | lingot d'endium, les six pierres légendaires, gant en endium, enderchest de poche, fabrique à nuage, amulette du chasseur | +2 armure, +15 % d'efficacité de minage |
| Donjons et pillage | 9 | les quatre épées légendaires de donjon, rune épique, les quatre têtes de wither (passif, assoiffé, prédateur, suprême) | +4 points de vie |
| NTM NEXT | 4 | The Gadget, Little Boy, Fat Man, Ivy Mike | +1 de portée, −10 % à l'achat |
| Mécène (optionnel) | 1 | œuf du dragon | +2 chance |

**36 pièces obligatoires, 37 avec l'œuf.**

- **Les bonus n'ont pas changé, ils ont changé d'aile.** Les sept bonus de l'ancien musée sont
  tous là, à la même valeur. Le double drop des « trouvables » va aux trésors, les dégâts des
  « drops de mobs » aux trophées, l'armure de l'aile PalaMod et l'efficacité des « fermes » aux
  reliques, les points de vie de la « nourriture » aux donjons, la portée et la remise restent sur
  NTM, la chance sur le mécène.
- **Ce que coûte une aile.** Les trésors et les trophées se trouvent sans rien acheter. Les
  reliques pèsent le plus : six pierres légendaires (le marché, la box Endium ou des pierres
  aléatoires), un lingot d'endium (1,44 M), un gant. Les donjons demandent le palier 65 des quatre
  donjons. L'aile NTM demande d'assembler quatre bombes atomiques et de ne pas s'en servir.
- **XP globale** : 100 par don au lieu de 25 (un niveau global = 500). 37 dons donnent 3 700 XP,
  à peu près ce que donnaient les 176 dons d'avant à 25 (4 400) : le musée pèse un poids comparable
  dans le niveau global.
- **L'œuf du dragon reste à part** : unique par monde, il se donne par choix. Les **pièces
  antiques** (palier 100 d'un donjon, une par donjon) ne sont pas demandées, pour la même raison.
- **Sans NTM NEXT**, les quatre bombes n'existent pas : l'aile ne s'ouvre pas, et le musée compte
  quatre ailes et 32 pièces. La remise d'achat de 10 % est alors hors d'atteinte, comme l'était
  l'ancienne aile NTM.
- **Un monde d'avant** : les dons déjà faits restent enregistrés. Ceux qui sont encore des pièces
  (étoile du Nether, élytres…) comptent ; les autres ne comptent plus. **Les bonus des anciennes
  ailes tombent** jusqu'à ce que les nouvelles soient complètes.

Leviers : la liste `items` de chaque aile, `rewards`, et `global_xp_per_donation`.
`tools/check_museum.py` vérifie le fichier (pièce inconnue, doublon, ligne d'indice manquante).

## 6 terdecies. Commande de trixium, et le God Villager au bout de sept jours

Demande du joueur (2026-11-12) : « dans les contrats, ajoutes-en un pour obtenir du trixium, où
l'item demandé change tous les jours à midi », puis « ajoute aussi le God Villager issu des
métiers » — lié à la commande, à son choix.

- **Une commande par jour** : un objet à livrer, payé **16 trixium** et 15 XP globale. Elle se
  livre depuis l'écran des contrats, qui prend dans le sac ce que le joueur porte ; une livraison
  partielle compte, on peut apporter en plusieurs fois.
- **Elle bascule à midi**, heure de Paris, et non à 5 h comme les autres contrats.
- **L'objet du jour se calcule**, il ne se tire pas : il se déduit du numéro du jour. Rien n'est
  sauvegardé, il ne peut pas être relancé, et il est le même après un redémarrage. Les
  commandes (42 à l'origine, 52 avec l'agriculture) sont battues une fois par tour : chacune
  passe une fois par tour, jamais deux jours de suite.
- **Quantités** : un quart d'heure de production au taux du catalogue (`rate_per_hour / 4`,
  arrondi en piles) — 256 blés, 320 bûches, 128 lingots d'améthyste, 64 lingots de paladium,
  10 diamants, 24 findiums… Au prix de vente, une commande pèse entre 640 $ (perles de l'ender)
  et 10 000 $ (diamants, lingots de paladium vert), 4 700 $ en moyenne.
- **Récompense** : 16 trixium, soit vingt minutes de minage pur (50 par heure) et 12 800 $ à la
  vente. La commande échange donc du temps de métier contre du temps de mine, avec un gain net.
  **Un mois sans rien manquer donne 480 trixium** : entre la 10e place du concours (600) et la
  30e (300), sans avoir miné. C'est le levier à surveiller ; le podium (≈ 2 500) reste un mois
  de mine.
- **La série** : sept commandes livrées de suite font apparaître le **God Villager** sur le
  joueur, puis le compte repart de zéro. Un jour manqué remet la série à zéro et **ne retire rien
  d'autre**. Par cette voie il ne demande pas le Mineur 95 de son apparition au hasard, qui reste
  inchangée (une chance sur 500 000 000 par tick). Son échange est le même : 64 blocs de
  paladium contre une pépite d'endium (40 000 $), et environ 5 350 XP de Mineur. Au mieux,
  **une pépite d'endium par semaine**, pour 576 lingots de paladium.

Leviers, dans `data/contracts.json` → `trixium_order` : `reset_hour`, `reward`, `reward_item`,
`god_villager_streak` (0 coupe la série), et le `pool`.

## 6 quaterdecies. Notation de la semaine — ce que vaut la bourse

Détail des critères : `docs/FACTION.md` § Notation de la semaine (22h).

- **2 500 $ le point**, à la banque de faction : 250 000 $ pour 100 points. À comparer au contrat
  de la semaine (110 000 $) et aux récompenses de ligue (25 000 $ en Bronze, 1,25 M $ en Diamant).
- **Une semaine ordinaire** — cinq soirs, 300 000 $ gagnés, une dizaine de contrats, deux
  victoires — tourne autour de 45 à 55 points en milieu de partie, soit 110 000 à 140 000 $ :
  un second contrat de la semaine.
- **Un début de partie** (aucun niveau de métier, banque vide, musée vide) plafonne vers 65 : les
  35 points des critères d'état sont à construire.
- **L'argent va à la banque de faction**, pas au joueur : il paie l'arbre de technologie, et un
  retrait ne compte pas comme un gain de la semaine suivante (sinon la bourse se noterait
  elle-même).
- **Échelles logarithmiques** pour l'économie et le trésor : multiplier ses gains par dix
  rapporte toujours le même nombre de points (6,7 pour l'économie, 3,3 pour le trésor). Le
  critère reste lisible à 50 000 $ comme à 5 M $ par semaine.

Leviers : `data/notations.json` — `bourse_per_point`, le `max` de chaque critère, les bornes
`zero_at` / `full_at`, `full_days`, `full_claims`, les trois valeurs de contrat, `per_win`.

## 6 quindecies. Serveur de trading — un revenu passif borné

Description : `docs/CONTENU_DOHY.md`.

- **9 000 $ par heure et par serveur**, soit un contrat du jour par heure de fonctionnement.
  **Quatre serveurs au plus** : 36 000 $ par heure, dans l'ordre de grandeur d'un métier de début
  de partie pratiqué activement, et très en dessous d'une ferme de fin de partie.
- **Il coûte de l'énergie** : 50 FE par tick, soit 250 HE par tick sur un réseau NTM NEXT, 1 000
  pour quatre. Il demande donc une production d'énergie installée, pas seulement sa recette.
- **Il ne tourne que chunk chargé**, et sa caisse plafonne à 8 h (72 000 $). En solo, le monde ne
  tourne que quand on joue : sur une soirée de 2 h, quatre serveurs rapportent 72 000 $.
- **Ce qu'il n'est pas** : l'autosell (500 M $), qui vend la production de fermes entières. Le
  serveur arrive bien plus tôt et rapporte bien moins.
- L'argent encaissé est un gain comme un autre : il compte dans le critère d'économie de la
  notation et dans les gains cumulés (pénalité de mort).

Leviers : `data/trading_server.json`. Si NTM rend l'énergie triviale, monter `energy_per_tick`
plutôt que baisser le revenu.

## 6 sexdecies. Agriculture — une filière, et des prix qui bougent

Description : `docs/CONTENU_DOHY.md`.

**Le cours de l'heure.** Chaque objet de la catégorie `AGRI` tire, chaque heure réelle, un facteur
entre 0,7 et 1,4 (moyenne de deux tirages : les extrêmes sont rares, la moyenne est 1,05). Il
multiplie la vente **et** l'achat, et s'ajoute à la pression de vente habituelle. Le shop garde sa
marge d'achat de 4 : au pire, acheter au plus bas et revendre au plus haut rend 1,4 / (0,7 × 4)
= 50 %.

**Les prix de base** (générés depuis `rates.csv`) :

| Objet | Vente | Achat |
|---|---|---|
| Céréale naturelle | 15 $ | 60 $ |
| Grain d'OGM | 60 $ | — |
| Farine | 50 $ (OGM : 200 $) | 200 $ (OGM : —) |
| Crème, saindoux | 100 $ | 400 $ |
| Pains simples | 60 à 70 $ | 240 à 280 $ |
| Pains d'orge, de soja, de tournesol, du mineur | 160 à 340 $ | 640 à 1 400 $ |
| Pains d'OGM | 800 à 1 100 $ | 4 800 à 6 600 $ (marge de 6) |

- **Transformer ajoute de la valeur** : 3 céréales (45 $) font une farine (50 $), une farine fait
  un pain simple (60 $). C'est peu, mais chaque étape repart d'une courbe de vente neuve : là où
  1 200 grains d'une même céréale écrasent leur prix, les mêmes en farine puis en pain ne
  l'écrasent pas.
- **Aucune recette n'est une pompe** : `tools/check_agri_prices.py` rejoue les 50 recettes
  (moulin, fournées, bêtes nourries) en achetant chaque ingrédient au cours le plus bas et en
  vendant le produit au plus haut. La ration de 4 céréales par bête vient de là : à 1 céréale, la
  crème revendue aurait payé plus que la céréale achetée.
- **Les pains d'OGM** valent cher parce qu'ils demandent une recherche (une trentaine d'essais,
  environ 1 500 objets courants), puis 9 ingrédients pour 8 graines, qui ne rendent que 2 à 4
  grains chacune et aucune semence. Ils sont au tier `FOUND` : leur prix s'effondre vite si on en
  vend beaucoup.
- **L'énergie** : une farine coûte 120 FE, un pain simple 250 FE, un pain d'OGM jusqu'à 1 200 FE.
  Une céréale brûlée en rend 400 : le biogénérateur mange à peu près une céréale par pain. Sur un
  réseau NTM NEXT, 120 FE font 600 HE.
- **Le pain du mineur** rend au plus un minerai toutes les 20 secondes, soit 15 par pain, pour
  une valeur moyenne d'environ 110 $ pièce. Sans ce délai, un outil de zone en aurait fait une
  rente.

**L'automate et le métier.** Le moulin et la machine à pain travaillent seuls quand on les
alimente par entonnoir (une farine en 10 ticks, une fournée en 40) ; en pratique c'est l'entonnoir
(un objet toutes les 8 ticks) et l'énergie qui limitent. **L'XP de Fermier ne vient que du travail
à la main** : 2 par farine, 12 par fournée de pain simple (soit 3 par pain, moins qu'une récolte),
8 à 12 pour un pain à effet, 30 à 40 pour un pain d'OGM, 20 pour le pain d'épices. Une chaîne
automatique ne fait donc pas monter le métier pendant que le joueur est ailleurs ; elle fait
avancer ses contrats, qui sont bornés.

**Contrats** : 300 céréales, 120 farines (360 céréales) ou 60 pains par jour, 600 pains par
semaine — la même échelle que « récolter 400 cultures ». **Commandes de trixium** : 52 au lieu de
42, les dix nouvelles au même calibrage (un quart d'heure de production).

**Le pain d'épices** (240 $) demande Fermier niveau 50 et ne se cuit qu'à la main : c'est la
récompense du métier, pas un produit de chaîne.

**Le tracteur** (description : `docs/CONTENU_DOHY.md`). Il ne change ni le prix d'une céréale ni
ce que rend une culture : il change **la surface qu'un joueur peut tenir**. Les prix sont
générés pour 1 200 céréales à l'heure ; au travail, un tracteur traite trois rangs à 5 blocs par
seconde, soit une quinzaine de cases par seconde — un champ de 64 × 64 (4 096 cases) se moissonne
en cinq minutes. Ce qui borne alors le revenu, dans l'ordre :

- **la pousse** : le tracteur ne fait pas mûrir plus vite (l'épandeur donne une poudre d'os par
  pousse et par passage, payée au prix de la poudre d'os) ;
- **la pression de vente** (§ 6) : chaque céréale a sa courbe, S₀ = 2 000. Vendues d'un coup,
  1 200 céréales rapportent 15 500 $ (86 % du prix de base en moyenne), 4 096 en rapportent
  42 100 $ (69 %), 10 000 en rapportent 78 600 $ (52 %). Trois fois et demie la récolte ne fait
  pas trois fois et demie le revenu, et l'oubli demande une nuit ;
- **le carburant** : 2 unités par bloc au travail, soit 0,67 unité par case. Le champ de 4 096
  cases coûte 2 700 unités par passage — 11 charbons, ou 2,7 seaux de diesel. C'est un coût
  d'usage, pas un mur : le mur est la recette (16 plaques d'acier, 2 moteurs, 1 circuit avec
  NTM NEXT) et le niveau 10 de Fermier, celui de la houe en améthyste qui récolte déjà en zone.

**L'XP de Fermier** d'une moisson au tracteur est celle d'une récolte à la main, case par case :
le métier monte donc avec la surface, comme avec les houes de zone de PalaMod. Si la courbe
s'avère trop rapide en jeu, le levier est `tractor_level`, puis `work_speed` (le débit) et
`move_cost` / `work_factor` (le coût), dans `data/agriculture.json`.

**Le silo** achète au prix d'achat de la boutique (marge de 4, cours de l'heure et remises
compris) : il n'ouvre aucun arbitrage que la boutique n'ouvre pas, `check_agri_prices.py` couvre
déjà ce cas. Les grains d'OGM, qui ne s'achètent pas, ne s'y achètent pas non plus.

Leviers : `data/agriculture.json` (recherche, machines, fournées, effets, élevage, bloc `farming`
du tracteur), bloc `course` de `data/economy.json`, lignes `dohy:` de `data/rates.csv`.

## 6 octodecies. La Terre — où sont les minerais

Description : `docs/CONTENU_DOHY.md`, section « La Terre ». Ne vaut que dans un monde dont un
datapack décrit la carte.

**Le principe.** Les prix ne bougent pas : ils sont générés depuis un rythme de production
(`rates.csv`) qui suppose qu'on trouve un minerai en creusant là où l'on est. Sur la Terre, ce
n'est plus vrai pour tout : ce qui change, c'est **le trajet**.

| Ressource | Sur la carte | Où |
|---|---|---|
| Charbon, fer, cuivre | partout (génération du jeu), plus riches dans leurs pays (4 filons de plus par chunk) | — |
| Or, diamant, émeraude, lapis, redstone | **gisements seulement** (1,5 à 4 filons par chunk) | 3 à 15 pays chacun ; et la dimension du Mineur |
| Métaux courants de NTM NEXT | gisements seulement (2 à 6 filons par chunk, proches de ses densités d'origine) | 10 à 21 pays chacun, sur chaque continent |
| Uranium, thorium, terres rares, lithium, cobalt, coltan | gisements seulement | 7 à 13 pays ou régions |
| Pétrole | ses poches rares partout (NTM NEXT), une poche pour 7 chunks dans les bassins pétroliers | 29 pays, 13 bassins dont 4 en mer |
| Minerais de PalaMod | inchangés (leur génération, et la dimension du Mineur) | — |

- **Un gisement est riche** : dans son pays, un minerai stratégique sort à la densité que NTM NEXT
  lui donne d'ordinaire partout. On ne trouve pas moins, on trouve *ailleurs*. Le revenu horaire
  d'un mineur installé sur un gisement est donc celui du chiffrage existant ; hors gisement, il
  est nul pour cette ressource, et c'est voulu.
- **La dimension du Mineur** reste la mine de secours des minerais du jeu et de PalaMod : ses
  1 000 points par session bornent déjà ce qu'on en sort (§ 6 undecies).
- **Le saut** est gratuit et aléatoire : il ne permet pas de choisir un gisement. Rejoindre le
  Kazakhstan depuis la France, c'est 5 000 blocs.
- Leviers : `tools/earth_deposits.py` (pays, rectangles, `veins`, `size`), puis `version` dans
  `deposits.json` pour resservir les chunks déjà visités.

**Les zones irradiées.** Leurs minerais de Sellafield ne sortent nulle part ailleurs sur la Terre.
Tchernobyl couvre une vingtaine de chunks, Fukushima une dizaine :

| Minerai | Filons par chunk | Blocs par filon | Sur Tchernobyl (≈ 20 chunks) |
|---|---|---|---|
| Schrabidium de Sellafield | 0,3 | 3 | ≈ 6 filons, ≈ 18 blocs |
| Radgem | 0,8 | 3 | ≈ 16 filons |
| Uranium brûlé | 2 | 5 | ≈ 40 filons |
| Diamant, émeraude de Sellafield | 1 et 0,8 | 4 et 3 | ≈ 20 et 16 filons |

- Le schrabidium est le matériau le plus cher de NTM NEXT ; il n'existait jusqu'ici que dans les
  cratères de Sellafield, les météorites et le Nether. Une zone en contient une poignée, une fois :
  les filons ne repoussent pas.
- **Le prix du risque** : 0,5 RAD/s dans la zone, 4 RAD/s au cœur (1 000 RAD tuent). Une heure de
  minage sans protection dans la zone, c'est 1 800 RAD : il faut la combinaison de NTM NEXT, ou le
  RadAway. Le cœur, sans combinaison, tue en quatre minutes. Leviers : `rad_per_second` dans
  `data/dohy/hbm/biome_radiation/`.
- **Le corium du réacteur** (vérifié le 2026-11-20, chasse aux bugs) : ces chiffres sont ceux du
  biome seul. Le corium posé au centre (150 RAD/s par bloc chez NTM NEXT) irradie les chunks
  voisins bien plus fort : un joueur sans combinaison y est mort en une dizaine de secondes, à
  une vingtaine de blocs du réacteur, en ouvrant un largage. Les largages tombent donc hors du
  cœur, au point le moins irradié de ceux qu'ils essaient, et l'annonce donne les RAD/s du point
  de chute (au-delà de 15 RAD/s : « combinaison indispensable »).
- **Le butin irradie qui le porte** : une caisse entière, c'est 15 RAD/s en médiane, plus de 20 dans
  40 % des cas, plus de 40 dans 16 % (gemme radioactive 25 RAD/s pièce, polonium 7,5 la pépite,
  plutonium brut 7,5). Celui qui ouvre la caisse en est prévenu.

**Les bases de lancement** ne rapportent rien : elles obligent à s'installer loin. De Paris,
Baïkonour est à environ 4 200 blocs, Kourou à 5 000.

## 6 novodecies. Pays à prendre — ce que coûte un pays

Un pays se paie en **points de conquête** : l'ELO gagné aux events de faction en finissant devant
la nation qui le tient (`docs/CONTENU_DOHY.md`, « Les pays à prendre »).

**Le prix** : 200 + 37 × √(cases de terre du pays sur la grille), arrondi à la centaine, 200 au
moins. La racine tient l'écart entre un micro-État et un continent dans un rapport de 1 à 110, au
lieu de 1 à 350 000 en surface. La grille est celle de la carte (projection de Miller) : les pays
du Nord y sont plus grands qu'en vrai, et donc un peu plus chers.

| Pays | Cases de terre | Points |
|---|---|---|
| Cité du Vatican, Monaco | 1 et 2 | 200 et 300 |
| Luxembourg | 33 | 400 |
| Belgique | 395 | 900 |
| Suisse, Pays-Bas | 531 et 605 | 1 100 |
| Royaume-Uni, Japon | 4 000 environ | 2 500 |
| Allemagne | 4 545 | 2 700 |
| France | 6 606 | 3 200 |
| Brésil | 53 728 | 8 800 |
| Chine | 80 852 | 10 700 |
| États-Unis | 108 560 | 12 400 |
| Canada | 207 352 | 17 000 |
| Russie | 346 282 | 22 000 |

Les 256 pays valent 459 700 points en tout ; la moitié coûte 1 100 ou moins, 84 coûtent 500 ou
moins (des îles, surtout).

**Ce que rapporte un event.** Quatre factions y jouent (toi et trois rivales) ; le pot se partage
par rang, chaque rang touchant 1,2 fois le suivant :

| Event | Pot | 1er | 2e | 3e | 4e |
|---|---|---|---|---|---|
| Boss | 700 | 227 | 187 | 156 | 130 |
| Egg Hunt | 3 000 | 968 | 804 | 670 | 558 |
| Totem, KOTH | 10 000 | 3 221 | 2 682 | 2 235 | 1 862 |

Ne compte que ce qui est gagné **devant** la nation qui tient le pays : 2e derrière une autre
rivale compte, derrière elle non.

**Le rythme.** Une semaine où l'on gagne tout (14 Boss, 2 Egg Hunt, le Totem et le KOTH) rapporte
environ 11 500 points ; en ne jouant que le soir (un Boss par jour, le KOTH du samedi), environ
4 800. La France tombe en un KOTH gagné, ou en quinze Boss ; la Russie demande deux semaines
pleines ; le monde entier, une quarantaine de semaines pleines. C'est le but long de la partie.

**Ce que donne un pays.** Un claim de plus (sans plafond : 256 pays, 256 claims), et quatre claims
de territoire dans la notation : quatre pays tenus remplissent la moitié « territoire » du
patrimoine (5 points sur 100, soit 12 500 $ de bourse par semaine). Pas d'argent : les revenus de
territoire n'ont pas été retenus. Le gain est d'abord la carte qui se dore.

Leviers : `EarthNations.cost` (la formule), `NEAR_BLOCKS` (la portée des voisins, quatre carreaux
de 64 blocs), `FactionNotation.COUNTRY_CLAIMS` (le poids d'un pays dans la notation).

## 6 vicies. Marché international

Règles : `docs/FACTION.md`, « Marché international ». Cours = (R / stock)^0,6, borné à [0,4 ; 2],
R = 4 × la demi-saturation de la fiche de prix.

**Ce que rapporte une vente** (fer, base 15 $, R = 8 000, taxe 12 % d'un État) :

| Vendu d'un coup, stock à la référence | Cours après | Reçu | Par lingot |
|---|---|---|---|
| 1 000 | -7 % | 12 700 $ | 12,7 $ |
| 2 000 | -13 % | 24 700 $ | 12,3 $ |
| 8 000 | -34 % | 84 400 $ | 10,5 $ |
| 28 800 (jusqu'à saturation) | -60 % | 222 000 $ | 7,7 $ |

Pour comparer, 8 000 lingots à la boutique (pression de vente personnelle) rapportent environ
67 700 $ : le marché international paie mieux le gros, tant qu'une nation n'a pas inondé le cours.
Les deux débouchés s'ajoutent : c'est voulu, la boutique pour le courant, le marché pour les
stocks. Le retour à la référence (10 % de l'écart par vingt minutes de jeu) borne le revenu par
ressource au même ordre que celui de la boutique.

**Pas de boucle d'argent** :
- vendre rapporte au plus 2 × la base, moins la taxe (au mieux 1,88 ×) ;
- acheter coûte au moins 2,6 × la base : acheter pour revendre au marché perd toujours au moins
  28 % ;
- à la boutique, la revente monte au plus à 2,5 × la base (demande du jour) et retombe aussitôt
  avec la pression de vente ; seule la première vente du jour (× 1,5 en plus) passe au-dessus de
  2,6, sur une vente ;
- la boutique vend à 4 × la base : rien à gagner à y acheter pour vendre au marché ;
- le marché saturé n'achète plus : pas de vente infinie au plancher.

**Les lots des nations** : 15 à 40 % de R toutes les dix minutes de jeu, sur une des 25
ressources. Une ressource donnée bouge donc en moyenne toutes les quatre heures de jeu, d'un
facteur 0,82 à 1,22 sur le cours quand elle part de la référence.

**Notation** (`data/notations.json`) : les nations rivales notent entre 38 et 80 (moyenne 59 avec
tous leurs pays). Une semaine moyenne du joueur (50 points) le place vers la 9e place, 65 points
dans le premier tiers, 80 en tête presque à coup sûr. Récompenses à 60 points : 150 000 $ de
bourse et 300 points de conquête (un Boss gagné en vaut 227) ; en 1re place, 600 points, trois
lots (30 000 $ au prix de base) et un claim.

Leviers : `WorldMarket` (`GOODS`, `K`, `MIN_FACTOR`, `MAX_FACTOR`, `REFERENCE_PER_S0`,
`BUY_MARKUP`, `BUY_FLOOR`, `MOVE_MINUTES`), `NationRank` (les taxes), `notations.json`.

## 6 unvicies. Create — ce que rapporte le broyage

Les roues de broyage donnent 1,75 fois la matière d'un minerai de PalaMod (9 particules, plus
75 % de chance de 9 autres ; la pierre et 75 % pour le findium et le trixium). La question est de
savoir si broyer puis vendre les lingots rapporte plus que vendre le minerai tel quel. Revenu d'une
vente d'une traite, courbe du § 6, sans l'oubli :

| Minerai | N | Vendre le minerai | Four, lingots vendus | Create, lingots vendus |
|---|---|---|---|---|
| Améthyste | 64 / 256 / 1 000 | 5 129 / 14 489 / 40 602 | 1 902 / 7 405 / 26 415 | 3 306 / 12 633 / 42 887 |
| Titane | 64 / 256 / 1 000 | 6 668 / 18 836 / 52 783 | 2 853 / 11 107 / 39 623 | 4 958 / 18 949 / 64 330 |
| Paladium | 64 / 256 / 1 000 | 13 849 / 39 122 / 109 626 | 4 565 / 17 771 / 63 396 | 7 934 / 30 319 / 102 928 |
| Paladium vert | 64 / 256 / 1 000 | 51 292 / 144 895 / 406 023 | 25 646 / 72 447 / 203 012 | 40 015 / 106 412 / 334 262 |
| Findium | 64 / 256 / 1 000 | 41 034 / 115 916 / 324 818 | 20 517 / 57 958 / 162 409 | 32 012 / 85 129 / 267 409 |
| Trixium | 64 / 256 / 1 000 | 82 067 / 231 832 / 649 637 | 41 034 / 115 916 / 324 818 | 64 024 / 170 258 / 534 818 |

Vendre le minerai reste le meilleur choix, sauf pour l'améthyste et le titane au-delà de quelques
centaines d'un coup : leurs lingots sont au tier `FARM` (S₀ = 2 000, des golems et des sèves en
donnent par centaines à l'heure), leurs minerais au tier `FOUND` (S₀ = 64), et 1 000 minerais
broyés rapportent 6 et 22 % de plus. C'est l'ordre de grandeur des fermes qui fixent ces prix ; le
broyage sert d'abord à fabriquer. Le minerai de pépites d'endium n'a pas de recette : une pépite vaut
40 000 $, 75 % de plus en ferait une rente.

**NTM NEXT** : les lingots et les poudres ne se vendent pas (§ 6 bis) ; broyer un minerai de NTM
donne plus de matière à fabriquer, jamais plus d'argent. La matière brute, qui se vend, ne gagne
rien à la roue (un broyé, comme chez Create).

**Céréales** : un tiers de chance de farine par grain à la meule, le `mill_ratio` de 3 du moulin
(`data/agriculture.json`) : la farine ne coûte pas moins de grain à la meule qu'au moulin.

**Zinc** : `create:raw_zinc`, `create:zinc_ore` et `create:deepslate_zinc_ore` au tier `FOUND`, au
rythme du fer (400 par heure : 100 $, 400 $ à l'achat). Le lingot n'a pas de prix. Le zinc brut
acheté 400 $, broyé puis lavé, rend 9 pépites (sans prix) et 0,75 poudre à canon (42 $) : pas de
boucle.

**Pépite de fer** : son taux de troc (400 par heure) lui donnait 45 $, et le lingot (15 $) en
donne 9 à l'établi : 405 $ par lingot. Le lavage de Create en faisait une rente automatique (un
minerai de fer acheté 400 $, broyé et lavé : 15,75 pépites en moyenne, 708 $). `gen_prices.py`
applique désormais des **plafonds d'établi** (`CRAFT_CAPS`) : le prix d'un objet ne dépasse pas
celui de sa source divisé par le nombre d'unités qu'elle donne, arrondi en dessous. La pépite de
fer passe à 1 $ (4 $ à l'achat). La pépite d'or (9 $, 81 $ les neuf contre 82 $ le lingot) était
déjà dans la règle.

Leviers : `tools/create_recipes.py` (rendements, minerais repris), `data/rates.csv` (zinc),
`CRAFT_CAPS` dans `tools/gen_prices.py`.

## 6 duovicies. Potions d'XP de métier — ce que vaut une heure doublée

La référence est celle du marché noir : **10 000 XP de métier valent 40 000 $** (l'argent des
niveaux qu'elles font franchir, et le temps de métier qu'elles remplacent), soit 4 $ l'XP. Au
rythme de référence de la courbe (15 000 XP par heure, § 2), une heure de Double XP rapporte
15 000 XP de plus, environ **60 000 $** ; une heure de Triple, 30 000 XP de plus, environ
**120 000 $**.

| Potion | Prix | XP en plus au rythme de référence | Ce qu'elle vaut | Prix / valeur |
|---|---|---|---|---|
| Double XP 1 h | 120 000 $ | 15 000 | 60 000 $ | 2,0 |
| Double XP 4 h | 400 000 $ | 60 000 | 240 000 $ | 1,7 |
| Triple XP 1 h | 300 000 $ | 30 000 | 120 000 $ | 2,5 |
| Triple XP 4 h | 1 000 000 $ | 120 000 | 480 000 $ | 2,1 |

La potion se paie donc environ **deux fois** ce qu'elle rapporte au joueur moyen : c'est un
achat de progression (les déblocages arrivent plus tôt), pas une source d'argent. Elle devient
rentable au-delà de 30 000 XP par heure — fonte de lingots de PalaMod, session de bonbons et de
bouteilles : la boire avant une grosse session est le bon geste. **Pas de boucle** : l'argent des
métiers est borné (28 M par métier maxé, primes comprises), et une potion ne fait que l'avancer.

Les prix tombent au milieu du jeu (200 000 $ par heure, § 3) : la Double d'une heure coûte un
peu plus d'une demi-heure de revenu, la Triple de quatre heures cinq heures. En fin de partie (1 M$ par heure et
plus), elles deviennent un réflexe ; les quatre métiers maxés, elles ne servent plus à rien.

**Donjons** : la Double de 30 min tombe dans la table des boss avec un poids de 3 (celui d'une
bouteille de 1 000 XP), la Triple de 30 min dès le palier 50 avec un poids de 1. Leviers :
`PalaXpPotions.SHOP` (prix et durées vendues), `DungeonRewards.bossPool` (poids).

## 6 tervicies. Lucky blocks — ce que vaut un lancer du Lucky Pass

Les valeurs sont au **prix de base** (`data/prices.csv`, ce qu'un objet se revend) et les chances
celles du tirage de DoHy, bonus du pass compris (`PALAMOD.md` § 37). Ne sont pas comptés : les
événements d'argent (± 10 % du solde, 0,01 à 0,03 % chacun), les pierres légendaires et le gant,
l'XP de métier, les effets, les blocs posés (obsidienne durcie, boîte à golem).

| Événement | Ce qu'il donne | Valeur | Paladium | Endium |
|---|---|---|---|---|
| Diamond Beacon | 83 blocs de diamant (747 diamants) et une balise | 747 000 $ | 0,28 % | 0,55 % |
| Quelle dinguerie ! | 10 lucky blocks en endium, qui ne refont pas le coup | ~58 000 $ | 1,1 % | 2,2 % |
| Paladium Beacon | 83 blocs de paladium | 53 800 $ | 0,05 % | 0,09 % |
| Trop chanceux pour ce monde | une pépite d'endium | 40 000 $ | 0,06 % | 0,12 % |
| Titane, Amethyst Beacon | 83 blocs | 33 600 $, 22 400 $ | 0,07 %, 0,14 % | 0,14 %, 0,27 % |
| Mineral Shower | 15 × 3 diamants, lingots de paladium ou de titane | 16 800 $ | 1,1 % | 2,2 % |
| Expala-osion | 128 lingots de paladium | 9 200 $ | 0,38 % | 0,73 % |
| Carte au trésor | un coffre en paladium de 108 lingots, à 20 000 blocs au plus | 7 800 $ | 0,28 % | 0,55 % |
| Pala-Pillone | 8 blocs de paladium | 5 200 $ | 5,7 % | 11,0 % |
| Consolation | 30 à 123 lingots de paladium | 5 500 $ | 0,56 % | 1,1 % |

Ce que vaut un lucky block, en moyenne : **3 600 $** en paladium, **6 900 $** en endium,
**230 $** en findium (cinquième lot ; 5 800 $, 11 300 $ et 1 500 $ avant lui). Au findium pèsent
l'Endium Block (3,24 M$, 0,004 %), DoubleXP (15 min, environ 15 000 $, 0,13 %), Coucou (un golem
et ses 64 blocs de paladium, 41 500 $, 0,13 %), Je t'aime tôt (64 lingots dans un totem, 4 600 $,
0,37 %) et les lancers offerts de la Roulette et de la Boule de Crystal (un lancer du pass, environ
2 800 $, 0,23 % à eux deux). (Troisième lot, `PALAMOD.md` § 39 : les nouveaux bons, modestes,
diluent les gros lots du paladium et de l'endium, dont la part des bons ne bouge pas. Quatrième
lot, § 40 : les objets des lucky blocks — armures, pomme de vitesse, brique de lait, épée Couac —
n'ont pas de prix de revente et ne comptent pour rien ici ; ils diluent encore le findium. Les deux
pièges au slimepad du paladium sont des malus sans argent : le repesage des bons les compense.) Le
diamant fait plus de la moitié de la valeur du paladium : c'est une loterie, pas un revenu.

**Cinquième lot** (`PALAMOD.md` § 41) : 68 événements classiques de plus et les onze lucky blocks
saisonniers. Presque tous donnent des objets, des effets ou des épreuves sans prix de revente : ils
diluent les gros lots (la part des bons ne bouge pas), et le cinquième des lancers qui va aux
saisonniers vaut peu d'argent — ~560 $ au noir (Easy Cash, 25 000 $ à 0,4 % ; l'énigme ;
Pa-rtout-ladium), ~530 $ à Halloween (huit lucky blocks en paladium à 1,1 %), presque rien aux
autres mois.

Le Lucky Pass donne un bloc et demi par jour, quatre sur dix en paladium, un sur cinq saisonnier :
**environ 2 800 $ par jour** (~6 400 $ avant le cinquième lot), une à deux minutes de revenu au
milieu du jeu (§ 3) ; ses lancers sont devenus une loterie d'objets plus qu'un revenu. Une Diamond
Beacon tous les **475 jours** de lancers ; l'Endium Block n'est plus qu'une curiosité.

**Pas de vraie boucle** : le pass est la seule source de lucky blocks ; Try Again en redonne trois
(0,09 % au findium), Quelle dinguerie ! dix, qui ne peuvent pas se relancer, la Roulette ou la
Boule de Crystal un lancer (un lancer sur quatre cents à peine en ramène un autre), le cadeau
multicolore de Noël trois, Case départ huit. Leviers : `LuckyPass.draw` (cases et sortes),
`LuckyType` (poids), `LuckyEvents` (`PALADIUM_GOOD`, `FINDIUM_GOOD`), la rareté de chaque
événement.

## 6 quatervicies. Grades — ce que coûte le confort

Les grades (`PALAMOD.md` § 42) s'achètent une fois pour toutes ; monter d'un grade coûte la
différence. Ils ne donnent pas d'argent : du confort (homes, Ender Chest, commandes), de l'XP, et
des ventes un peu plus faciles. Leur prix les place sur la progression du § 3.

| Grade | Prix | Monter depuis le précédent | Atteint vers |
|---|---|---|---|
| Héros | 10 M | — | semaine 3 (~60 h), entre la box Paladium et le booster x3 |
| Légende | 100 M | 90 M | mois 3, avec la box Endium |
| Divinité | 1 G | 900 M | fin de partie, après l'autosell : un horizon, comme les derniers paliers d'amélioration |
| Premium | 15 M les 30 jours | — | milieu de partie ; ensuite, un puits qui revient chaque mois |

**L'XP de métier** : +5, +10, +15 % (et +5 % avec Premium), **ajoutés** aux autres bonus
(Apprentissage +15 à +50 %, amulettes) avant les potions, qui multiplient le tout. Au rythme de
référence (15 000 XP par heure, § 2) et au prix du marché noir (4 $ l'XP, § 6 duovicies), +5 %
valent ~3 000 $ par heure, +20 % ~12 000 $ : Divinité avec Premium vaut à peu près Apprentissage I
(150 M) sur ce seul point. Aucune boucle : l'argent des métiers est borné, le bonus ne fait que
l'avancer.

**L'XP du pass** : +5 à +20 % sur les quêtes. Un mois moyen donne déjà ~51 600 XP pour 35 000
nécessaires (`tools/check_pass.py`) : le grade fait finir le pass plus tôt, pas gagner plus de
récompenses. **L'XP classique** : +10 à +40 %, pour les enchantements et le raccommodage ; rien qui
se vende.

**L'hôtel des ventes** : 16 annonces à la fois au lieu de 6. Le débit reste celui du butin (§ 6
decies), et les acheteurs paient au plus 0,69 fois la cote : plus d'annonces, pas de revente
gagnante.

**La taxe du marché international** : réduite de 10 % (Légende), 20 % (Divinité), 10 % de plus
avec Premium. Au mieux, Superpuissance (6 %) et Divinité avec Premium : 4,2 %. Une vente rapporte
alors au plus 2 × 0,958 = 1,92 fois la base, toujours sous les 2,6 fois de l'achat le moins cher :
**pas de boucle** (§ 6 vicies).

**Premium** : 15 M, c'est 75 heures au milieu de partie (200 k par heure), 15 heures à 1 M par
heure, une broutille au-delà. Il vient après les achats uniques comme un puits récurrent, à la
manière de l'assurance, mais pour du confort. Le temps est réel : un mois sans jouer le consomme
(c'est un abonnement, choix du joueur).

Leviers : `PlayerGrade` (prix et valeurs de chaque grade), `Premium` (prix, durée, bonus).

## 6 quinvicies. Bots de farming — un champ qui se récolte seul

Description : `docs/CONTENU_DOHY.md`. Comme le tracteur (§ 6 sexdecies), le bot ne change ni le
prix d'une culture ni ce qu'elle rend : il change **qui récolte**. Ce qui borne son revenu, dans
l'ordre :

- **la pousse** : il ne fait rien mûrir (ni poudre d'os, ni accélération). Une culture vanilla sur
  terre humide met de 25 à 50 minutes à mûrir selon la disposition des rangs ; son plus grand champ
  (48 × 48, 2 304 cases, ~2 050 cultures une fois l'eau posée) donne donc de **2 400 à 4 800
  récoltes à l'heure** ;
- **son débit** : mesuré en jeu, 80 récoltes et replantations en 51 secondes dans un champ serré
  de 9 × 9 (une toutes les 0,65 s, marche comprise), soit ~5 500 à l'heure. Un bot suit la pousse
  de son plus grand champ, guère plus : la limite de 48 × 48 est faite pour ça ;
- **la zone chargée** : il ne travaille que près d'un joueur. Ce n'est pas une rente qui tourne
  la nuit ni pendant qu'on est à la Mine : en solo, il récolte pendant qu'on fait autre chose à
  côté de sa base ;
- **la pression de vente** (§ 6) : chaque culture a sa courbe, S₀ = 2 000. Vendues d'un coup,
  2 400 céréales rapportent 27 800 $ (77 % du prix de base), 4 800 en rapportent 47 400 $ (66 %),
  9 600 (deux bots sur la même céréale) 76 500 $ (53 %). Quatre bots rapportent quatre fois plus
  seulement sur quatre cultures différentes, ou en passant par la farine et le pain, qui repartent
  chacun d'une courbe neuve.

**Le prix.** 100 k, fixé par le joueur le 2026-11-20 (5 M étaient prévus, au niveau de la box
Paladium). C'est quatre heures d'activité du début de partie (25 k$/h, § 3) : les bots deviennent
un outil de la première semaine, pas un jalon. À plein, un bot rapporte de 28 à 47 k$ par heure de
présence et se rembourse en deux à quatre heures près du champ ; les quatre (400 k), sur quatre
cultures, approchent les 200 k$ par heure des « fermes en place » du § 3 dès que les champs sont
plantés. Ce qui les borne n'est donc plus le prix mais le reste : la pousse, la présence, la
pression de vente et le plafond de quatre bots. La télécommande (10 k) n'est qu'un outil.

**L'XP de Fermier** (choix du joueur : comme à la main). Une récolte du bot vaut une récolte à la
main, mesurée en jeu au point près : 1 le blé, 3 la verrue, 5 le cacao ; 2 la pomme de terre
(Fermier 5), 3 la carotte (15), 8 la betterave (25), 2 la canne (45), 3 la citrouille (55), 5 la
pastèque (65). Les grades, les potions et le reste s'y appliquent comme à la main ; les baies
cueillies ne donnent rien, comme à la main ; propriétaire déconnecté, rien non plus. Un champ de
48 × 48 de betteraves à plein donne jusqu'à ~38 000 XP par heure, deux fois et demie le rythme de
référence (15 000 XP/h, § 2), et quatre bots le quadruplent : c'est l'échelle du tracteur (le
métier monte avec la surface), sans le conduire. **C'est le point à surveiller** : si le Fermier
monte trop vite, le levier le plus doux est la surface (`BotZone.MAX_SIDE`), puis le nombre de
bots. À 100 k, ce point arrive dès la première semaine.

**Rien ne se perd, rien ne se crée** : le bot ne ramasse que ce que rend la casse normale du bloc
(et la copie du double drop du musée, qui va désormais dans la liste des drops, pour lui comme pour
la trémie du tracteur) ; il replante avec une graine de la récolte ; sac et coffres pleins, il
s'arrête au lieu de jeter. Invulnérable, il ne meurt que par le vide ou `/kill`, et rend alors son
sac.

Leviers : `DoHyFarmBots.BOT_PRICE` et `REMOTE_PRICE`, `BotRegistry.MAX_PER_PLAYER` (quatre),
`BotZone.MAX_SIDE` (la surface), `FarmBot.HARVEST_PAUSE` (le débit), `FarmBot.MAX_CHESTS`.

## 6 sexvicies. Mines de la Terre — un revenu de territoire, borné par le voyage

Description : `docs/CONTENU_DOHY.md`. Les pays (§ 6 novodecies) ne rapportaient pas d'argent :
« les revenus de territoire n'ont pas été retenus ». Les mines sont ce revenu, demandé par le
joueur, versé à la banque de faction en argent et en minerais, chaque heure de temps réel, hors
ligne compris.

| Rang | Mines | Argent par heure | Minerai par heure | Capture |
|---|---|---|---|---|
| 1 | 47 (29 mines, 2 cheminées, 16 champs) | 1 500 $ (3 000 pour un champ) | 6 blocs (2 pour une cheminée) | 5 min |
| 2 | 100 (62 mines, 7 cheminées, 31 champs) | 800 $ (1 600) | 3 blocs (1) | 3 min |
| 3 | 98 (97 mines, 1 cheminée) | 400 $ | 2 blocs (1) | 2 min |

Les champs de pétrole et de gaz ne rendent pas d'objets (ni barils ni gaz en bouteille) : leur
argent double. Les blocs de minerai rendent ce que rendrait la pioche en diamant sans fortune ;
une cheminée de kimberlite est plafonnée à 1 ou 2 diamants par heure (un diamant vaut 1 000 $).

**Ce que ça donne.** Trois mines de rang 3 près de chez soi : 1 200 $ par heure, 28 800 $ par jour
et 144 blocs de minerai, pour trois balises (75 000 $) remboursées en moins de trois jours. Dix
mines de tous rangs : ~8 000 $ par heure, ~190 000 $ par jour, une heure d'activité du milieu de
partie (§ 3). **Toutes** (245) : 238 500 $ par heure, 5,7 M par jour et 566 blocs de minerai par
heure — cinq à six heures de jeu de fin de partie chaque jour, la récompense de qui a fait le tour
du monde.

**Ce qui le borne**, dans l'ordre :

- **le voyage** : la carte fait 40 000 blocs de tour (111,952 blocs par degré) ; chaque mine
  demande d'y aller, puis 2 à 5 minutes sur place, et une balise de 25 000 $ ;
- **la guerre** : chaque mine prise laisse un grief à la nation de sa région (5 à 12 selon le
  rang ; un pays en vaut 25) et grossit la banque qu'elle convoite ; un assaut réussi reprend la
  dernière mine de sa région et emporte une part de la banque, que les mines remplissent ;
- **le coffre de la banque** : 54 cases. Une semaine d'absence avec dix mines de fer de rang 1, c'est
  10 000 blocs : le coffre en garde 3 456, le reste est perdu. Les minerais ne s'accumulent pas
  sans fin ;
- **l'absence** : une semaine au plus est rattrapée ; au-delà, la mine ne produit plus ;
- **la pression de vente** (§ 6) : le fer, le cuivre et l'or bruts sont au tier `FOUND` (S₀ = 64,
  plancher 35 %) ; vendre la production d'une semaine d'un coup en écrase le prix. Les minerais
  servent d'abord à fabriquer (NTM, Create), l'argent est le revenu.

**Pas de boucle** : la balise coûte 25 000 $ et n'est jamais rendue par la capture ; une mine ne
produit rien de plus qu'elle-même ; deux factions de joueurs ne se disputent pas une mine.

Leviers : `EarthMines.moneyPerHour`, `blocksPerHour`, `captureSeconds`, `MAX_HOURS` (la semaine),
`DoHyMines.BEACON_PRICE`, `FactionVault.SLOTS`, et le grief (`EarthMines.grief`).

## 6 septvicies. La riposte contre les nations — ce que rend une place forte

Règles : `docs/FACTION.md`, « La riposte contre les nations (22l-2) ». Ce lot ne crée pas de
nouvelle source d'argent : il rend ce qu'on t'a pris, et rend les nations plus dures à battre.

**La restitution sort de son trésor.** Ce qu'une nation rend, c'est ton argent, qui dormait dans son
trésor depuis son pillage (il ne paie que ses recrues, ses soldats engagés, ses missiles et ses
réparations). Elle rend au plus ce qu'elle a ; le reste reste dû. Le butin de 30 % se prend ensuite
sur ce qui reste : une nation de 70 000 $ qui te rend 30 000 $ ne te laisse plus que 12 000 $ de
butin, au lieu de 21 000. La chute rapporte donc le plus quand on ne t'a rien pris — et quand on t'a
pris, elle te remet d'abord à flot.

**La contre-offensive ne se farme pas.**

- Une nation **vaincue se relève une semaine** : pas de nouvelle guerre contre elle avant.
- Chaque chute ampute son trésor de 40 % (30 % pour toi, 10 % en fumée), et il ne se reconstitue
  que de 1 % de son trésor de départ par heure (plus ses pays) : le butin suivant est maigre.
- **Déclarer coûte** : la nation riposte sur ta base (son assaut, à l'heure que tu fixes, peut
  te piller de nouveau), et chaque guerre déclarée compte 5 points de puissance deux semaines ;
  au-delà de 50, les nations s'allient.
- Une trêve ne se rompt que si elle te doit quelque chose.

**La coalition, en chiffres.**

| | Une nation seule | Une coalition |
|---|---|---|
| Soldats à l'assaut | 8 à 24 | + 4 à 10 de l'alliée (120 $ chacun sur son trésor) |
| Repris sur un assaut réussi | 1 pays, 1 mine | 2 pays, 2 mines (chacune ceux de sa région) |
| Sa place forte | selon son armée | + la moitié de l'armée de l'alliée |
| Ce que rend sa chute | ce qu'elle t'a pris | ce que les deux t'ont pris |

**Ta puissance**, sur trois parties types :

| Partie | Banque | Pays | Mines | Claims | Victoires | Puissance |
|---|---|---|---|---|---|---|
| Début | 20 000 $ (5) | 0 | 0 | 8 (2) | 0 | **7** |
| Milieu | 300 000 $ (15) | 2 (10) | 1 (5) | 25 (6) | 2 (6) | **42** |
| Fin | 3 M$ (25) | 6 (25) | 4 (20) | 40 (10) | 5 (15) | **95** |

Les coalitions arrivent au milieu de partie, quand la faction tient des terres sur la Terre et a
déjà battu des assauts : pas avant. Une guerre déclarée (+5) peut faire passer le seuil.

**L'IA qui apprend** rend chaque nation plus dure au fil des assauts, sans la rendre imbattable :
ses espions voient au plus quatre dixièmes de plus de tes pièges (une nation rusée les voit déjà
presque tous), sa troupe ne change que de quelques soldats (un quart des fantassins, au plus), et
déplacer ton coffre de plus de 48 blocs efface la carte de ses morts. Varier ses défenses paie :
la leçon suit la cause qui a tué le plus, chaque assaut comptant moitié moins au suivant.

Leviers : `WarStrategy.BEATEN_MS` (la semaine de la vaincue), `COALITION_AT`, `DISSOLVE_AT`,
`PACT_MS`, `CONTINGENT_MIN` et `CONTINGENT_MAX`, `threat` (les poids), `WarData.Lessons` (`DEATHS`,
`MOVED`, `insight`, la leçon : trois morts et plus du tiers), `Siege.compose` (ce que change une
leçon), le poids des tourelles dans `Siege.requestPlan`.

## 6 octovicies. Technologies de la nation — ce que coûte un palier

Règles : `docs/NATION.md`, chapitre 23. Tranché au lot 23i (2026-11-23) : **les chiffres des lots
23b et 23c restent**, parce que les trois limites d'un palier (les points, l'argent, les pièces)
tombent au même moment de la partie ; ce qui change, c'est **où l'on livre les pièces**. Le calcul
se refait avec `tools/tech_balance.py` (avec le jar de NTM NEXT pour les composants) :

```bash
python3 -I tools/tech_balance.py <mods>/hbm-neoforge-1.0.5-mc26.2.jar
```

**Les points.** Un laboratoire fait 3 600 points par heure de marche. Dans un domaine, un palier
remplace le bonus du précédent ; d'un domaine à l'autre, ils s'ajoutent : la vitesse de recherche
monte à +45 % (Industrie V +10, Nucléaire VI +15, Espace VI +20), l'apport des pays à +110 % (100 %
au plus, +10 % au Gouvernement V) : ×3,04 au plus fort, et trois laboratoires.

| Palier | Points | 1 labo, ×1 | 2 labos, ×1,25 (5 pays) | 3 labos, ×3,04 | Argent | Au revenu du moment |
|---|---:|---:|---:|---:|---:|---|
| II | 1 500 | 25 min | 10 min | 3 min | 25 k | 1 h à 25 k/h |
| III | 6 000 | 1,7 h | 40 min | 11 min | 100 k | 4 h à 25 k/h |
| IV | 20 000 | 5,6 h | 2,2 h | 36 min | 500 k | 2,5 h à 200 k/h |
| V | 60 000 | 16,7 h | 6,7 h | 1,8 h | 2 M | 2 h à 1 M/h |
| VI | 180 000 | 50 h | 20 h | 5,5 h | 10 M | 10 h à 1 M/h |

L'Espace a un cran d'avance (son I coûte un II, son VI 360 000 points et 20 M). Tout l'arbre des
six domaines nouveaux : **1 605 000 points et 75,75 M $**. Un joueur seul dont le laboratoire ne
manque jamais de recherche (un laboratoire, deux à 48 h de jeu, trois à 72 h — ce que demandent les
étapes du Gouvernement III et V) en vient à bout en **~127 h de jeu** ; au plus fort des bonus, les
points de tout l'arbre se font en 49 h de marche. Vérifié en jeu (lot 23i) : tout l'arbre acquis,
×1,45 sans carte de la Terre, trois laboratoires, trois recherches en parallèle.

**L'argent arrive au même moment.** Au revenu du paragraphe 3, les paliers II et III des six
domaines (750 k) se paient dans les quarante premières heures, les IV (3 M) entre 40 et 80 h, les
V (12 M avec l'Espace IV) vers 100 h, les VI (60 M avec l'Espace V et VI) au mois 3, quand le
revenu passe le million par heure : la Box Endium (150 M) et l'autosell (500 M) restent devant.
Points et argent finissent ensemble vers 130 à 200 h : **un domaine fini par mois de saison**, pas
plus tôt.

**Les pièces montent par composants, pas par temps de machine.** Ramenées à leurs composants par
les recettes par défaut de NTM NEXT :

| Palier | Pièces (cas courant) | Composants | Machines |
|---|---|---|---|
| II | 32 plaques d'acier, 16 bobines de cuivre | — | presse, établi |
| III | 16 circuits intégrés, 16 condensateurs | 64 puces, 16 condensateurs, 64 PCB | 6 min |
| IV | 16 circuits militaires, 32 micropuces | 288 puces, 64 condensateurs, 128 PCB | 16 min |
| V | 8 circuits polyvalents, 8 unités de contrôle | 384 puces, 448 condensateurs, 128 au tantale, 32 puces bismoïdes | 24 min |
| VI | 4 processeurs quantiques, 4 horloges atomiques | 60 puces, 64 puces bismoïdes, 16 puces quantiques | 17 min |

Le temps de machine (assembleuse et soudeuse) reste de quelques minutes : ce qui coûte, ce sont les
matières (polymère, silicium, niobium, tantale, puis bismuth, BSCCO, hélium 4) et les machines qui
les font — c'est-à-dire la progression de NTM elle-même. Le VI demande moins de composants que le V,
mais des composants de fin de partie. L'Industrie III (16 tubes à vide) et l'Agriculture III
(16 moteurs) sont plus légères que les autres III : la soudeuse des circuits intégrés n'arrive
qu'avec l'Industrie III, l'Agriculture III ne demande que l'Énergie II (`tools/tech_crafting.py`).

**Pourquoi rien ne bouge.** Baisser les points ferait finir l'arbre avant la fin du mois 2, quand
l'argent manque encore ; les monter laisserait les laboratoires seuls juges de la progression, alors
que la fin de l'arbre est déjà tenue par les composants de fin de partie. L'apport des pays
plafonne vite (vingt pays, deux semaines de conquête) : il récompense la conquête sans la rendre
obligatoire.

**Ce qui change : les pièces se livrent au laboratoire.** Une recherche les prend sur le joueur,
puis dans les laboratoires chargés de sa nation ; un entonnoir, un convoyeur ou un tuyau d'objets
de NTM les y amène depuis la chaîne qui les fabrique. Le laboratoire garde 18 piles (le plus gros
palier demande deux sortes de pièces, une pile de chaque au plus : de quoi en préparer neuf
d'avance), ne prend que les pièces des paliers et ne se vide pas : ni un tuyau ni
un entonnoir n'en tirent quoi que ce soit, il ne sert pas de coffre.

Leviers : `data/tech/*.json` (`cost` : `points`, `money`, `items`), `data/research_lab.json`
(`points_per_hour`, `buffer_points`, `country_percent`, `capital_percent`, `country_cap_percent`),
`ResearchLabBlockEntity.PART_SLOTS`.

## 6 novovicies. Niveau global — le barème des jalons (2026-11-23)

Outil : `python3 -I tools/global_level.py`. Un niveau global vaut 500 XP ; chaque niveau ajoute
35 % aux récompenses des contrats et à l'argent du jour du spawn, et tous les cinq niveaux une prime
(50 000 $ fois le rang du palier).

| Jalon | XP globale | Au plus |
|---|---|---|
| Contrat du jour | 15 | 3 par jour |
| Contrat de la semaine | 250 | 1 par semaine |
| Commande de trixium | 15 | 1 par jour |
| Niveau de métier | 20 | 4 métiers × 100 niveaux : 8 000 |
| Don au musée | 100 | 36 pièces : 3 600 |
| Palier de collection | tête 10 → 130 (paliers 1 à 10), queue 150 → 290 (+10 par cran) | 3 795 par objet mené au bout |
| Prestige de collection | 500 | 5 par objet |
| Blocs de masse (20) | **aucune** (ni paliers ni prestige) | — |

**Ce qui a changé.** La queue des paliers montait de 170 à **9 390** (×1,33 par cran) alors que
ses seuils ne montent que de ×1,11 : au palier 24 d'une courbe `FOUND`, un objet récolté valait
cinquante fois l'XP d'un objet du palier 9, et un seul palier dix-neuf niveaux globaux. Avec la
corne du Dancarok sur un bloc de masse (100 000 blocs par heure, § 1), trois heures menaient une
collection au bout : **37 815 XP, 75 niveaux**, une récompense de contrat multipliée par 27, et
chaque prestige rendait le tout, la collection repartant à zéro. Les blocs de masse ne donnent plus
d'XP globale (`global_xp_disabled` dans `collections.json`), la queue monte de dix en dix, et un
objet mené au bout rapporte 3 795 XP au lieu de 37 815. La tête ne change pas.

**Le rythme.** Pour un joueur seul à 20 h par semaine (§ 3), tous les contrats faits, les quatre
métiers à parts égales, le musée rempli au fil de la saison, et pour les collections un modèle
grossier (trois objets qui avancent ensemble par heure de jeu, sur un roulement de quarante) :

| Semaine | Contrats | Métiers | Musée | Collections | Niveau | Récompense des contrats |
|---|---|---|---|---|---|---|
| 1 | 670 | 1 360 | 200 | 650 | 5 | ×2,75 |
| 4 | 2 680 | 2 400 | 900 | 1 187 | 14 | ×5,9 |
| 8 | 5 360 | 3 200 | 1 800 | 1 494 | 23 | ×9,05 |
| 16 | 10 720 | 4 240 | 3 600 | 2 034 | 41 | ×15,35 |

Le jeu ordinaire ne touche pas la queue des paliers en une saison (le palier le plus haut du
modèle est le septième) : le nouveau barème ne change rien à ce rythme, il ferme les fontaines.
Au niveau 41, les contrats rapportent ~4,6 M par semaine, moins du quart du revenu attendu au
mois 4 (1 M/h, § 3) ; en semaine 1 (du niveau 0 au niveau 5), ils pèsent autant que la vente,
~0,5 M chacun : c'est le rôle qu'on leur donne, structurer les premières soirées. **À surveiller** : si le booster ×2 tombe en
semaine 1, c'est `money_base` des contrats qu'il faut baisser, pas l'XP.

Leviers : `data/collections.json` (`rewards_per_tier[].global_xp`, `prestige.global_xp`,
`exceptions[].global_xp_disabled`), `data/contracts.json` (`global_xp` des quotidiens, de l'hebdo
et de la commande de trixium), `data/museum.json` (`global_xp_per_donation`), `JobApi`
(20 par niveau de métier), `ContractApi` (500 par niveau, +35 %).

## 6 trigesies. Kits — ce que vaut un kit (2026-11-23)

Les kits (`PALAMOD.md` § 43) ne doivent ni remplacer le début de partie ni devenir un revenu : le
kit de départ fait gagner les deux premières soirées, les kits de grade donnent une petite raison
de revenir chaque semaine. Les valeurs « pour le joueur » sont celles des autres sections : une box
rend de 80 à 95 % de son prix (§ 6 quater, ~85 % ici), une potion d'XP de métier la moitié du sien
(§ 6 duovicies). Steaks, torches, pommes et carottes dorées, outils et armures **ne se vendent
pas** au shop (absents du catalogue) : ils n'apportent pas un dollar.

| Kit | Délai | Prix boutique | Valeur pour le joueur | Par mois (valeur) | Part du prix par mois |
|---|---|---|---|---|---|
| Départ | une fois | 100 k de clé, 10 k $, l'équipement de fer | ~95 k et 10 k $ | — | — |
| Joueur | 24 h | rien qui se vende | ~10 min de récolte par jour | — | — |
| Héros | 7 j | 220 k | ~145 k (clé 85 k, potion 60 k) | ~0,62 M | 6,2 % de 10 M |
| Légende | 7 j | 1,3 M | ~0,97 M (clé 850 k, potion 120 k) | ~4,2 M | 4,2 % de 100 M |
| Divinité | 7 j | 5,6 M | ~4,5 M (clé 4,25 M, deux potions 240 k, pomme enchantée 15 k) | ~19 M | 1,9 % de 1 G |
| Premium | 7 j | 220 k | ~145 k | ~0,62 M les 30 jours | 4,1 % de 15 M |

- **Le kit de départ** vaut ~105 k : environ quatre jours des trois contrats quotidiens au niveau
  0 (~27 k par jour, § 6 novovicies), le cinquième de ce que la vente rapporte la première semaine
  (~0,5 M). L'armure et les outils de fer épargnent la première heure de minage, sans plus : le
  PalaMod commence au-delà.
- **Les kits de grade** rendent de 2 à 6 % de la valeur du grade par mois : un grade ne se
  rembourse pas par ses kits (seize mois pour Héros), il reste du confort (§ 6 quatervicies). Un
  Divinité qui cumule tout (joueur, Héros, Légende, Divinité, Premium) reçoit ~5,8 M de valeur par
  semaine, l'équivalent de six heures de revenu en fin de partie (1 M par heure, § 3).
- **Pas de boucle** : un kit est lié au joueur et à l'horloge réelle ; ce qu'il donne n'achète
  rien qui le fasse revenir plus vite. Un ancien joueur qui n'a jamais pris le kit de départ le
  prend une fois, comme un nouveau.

Leviers : `data/kits.json` (contenu, délais, grades), relu au démarrage du monde.

## 6 untrigesies. Le chanvre de NTM NEXT (2026-11-23)

Une culture comme une autre : un champ de chanvre doit rapporter le revenu horaire du tier FARM
(18 000 $/h, § 1), pas plus. Une couronne repousse en une dizaine de minutes ; un joueur qui
tient un grand champ en casse environ 600 par heure. Elles rendent ensemble 600 fleurs, 1 800
fibres et 900 graines, et le revenu se partage entre les trois :

| Objet | Par heure | Part du revenu | Débit retenu (`rates.csv`) | Prix |
|---|---|---|---|---|
| Fibre | 1 800 | ~50 % | 3 600 | 5 $ |
| Graines | 900 | ~30 % | 2 600 | 6 $ (vente seule) |
| Fleur | 600 | ~17 % | 3 600 | 5 $ (achat 20 $) |
| Toile (4 fibres) | — | — | 900 | 20 $ |

- 600 × 5 + 1 800 × 5 + 900 × 6 = **17 400 $/h**, sous la cible : le chanvre ne bat pas les
  céréales, il les complète (et donne en plus de l'XP de Fermier, 6 par couronne).
- **Les graines ne s'achètent pas** : fourrage, elles feraient une pompe à crème (quatre graines
  achetées 28 $ au plus bas du cours, 4 × 28 × 0,7 = 78 $, pour une crème revendue 100 × 1,4 =
  140 $). `tools/check_agri_prices.py` les compte désormais dans l'élevage, comme la toile.
- **La toile** vaut ses quatre fibres : la tisser ne crée pas de valeur, elle sert au papier et aux
  tableaux. Le papier et les tableaux ne se vendent pas.
- **La fibre en ficelle** (1 pour 1) ne rapporte rien : la ficelle vaut 1 $, la fibre 5 $.
- Le biogénérateur tire 400 FE d'une graine comme d'une céréale : les graines (6 $) sont le
  combustible le moins cher de la ferme, sans arbitrage puisqu'elles ne s'achètent pas.

## 6 duotrigesies. Le calendrier de l'Avent — ce que vaut décembre (2026-11-24)

Un cadeau de saison : il doit faire plaisir chaque jour sans devenir un revenu, et le grand cadeau
récompenser la régularité. Valeurs « pour le joueur » des autres sections : une clé Améthyste
~85 k, une Saharia ~850 k (§ 6 quater), une potion d'XP de métier ~60 k (§ 6 duovicies), une
pépite d'endium 40 k (prix de base du catalogue) ; les objets de Noël (cadeaux, lucky blocks de
Noël, lait chaud, sapin, hache, pierre) n'ont pas de prix de revente et ne comptent pour rien
ici, comme les lucky blocks (§ 6 tervicies).

| Ce que donnent les 24 cases | Valeur |
|---|---|
| Argent liquide (5, 13, 23, 24 décembre) | 100 000 $ |
| Deux clés Améthyste (6 et 18) | ~170 k |
| Deux potions d'XP de métier (10 et 19) | ~120 k |
| Une pépite d'endium (20) | ~40 k |
| Lingots (4) | ~4 k |
| **Total** | **~0,43 M** |
| Le grand cadeau (une clé Saharia) | ~0,85 M |

- **~0,43 M sur le mois**, ~18 k par jour : moins qu'une journée de contrats au niveau 0
  (~27 k, § 6 novovicies), une demi-heure de revenu en fin de partie (1 M par heure, § 3). Le grand
  cadeau double la mise pour qui vient chaque jour.
- **Le rattrapage à 25 000 $** coûte plus que ce que rendent la plupart des cases : il ne vaut que
  pour les clés (~85 k), les potions (~60 k), la pépite (~40 k), les 50 000 $ du 23 et les 25 000 $
  du 24 — et pour le grand cadeau, qui exige les vingt-quatre. Manquer cinq jours et tout rattraper
  coûte 125 000 $ : le grand cadeau reste rentable (~0,85 M), sans rendre l'oubli gratuit.
- **Pas de boucle** : le calendrier est lié au joueur et à la date réelle ; rien de ce qu'il donne
  n'ouvre une case plus tôt. Une saison ouverte ne se rouvre pas (l'attachement garde l'année).

Leviers : `data/advent.json` (contenu des cases, grand cadeau, `catch_up_price`), relu au
démarrage du monde.

## 6 tritrigesies. La garnison — ce que coûte une armée (2026-11-24)

Description : `docs/FACTION.md` (22n). Une dépense de milieu et de fin de partie : elle doit
coûter assez pour qu'on la choisisse, et rendre assez (la base tenue, l'escorte) pour qu'on la
garde. Tout passe par la banque de la nation, jamais par la bourse du joueur.

| Ce qu'on achète | Prix | Par jour |
|---|---|---|
| Fantassin | 20 000 $ | 2 000 $ |
| Archer | 25 000 $ | 2 500 $ |
| Médecin | 35 000 $ | 3 500 $ |
| Capitaine (un seul) | 80 000 $ | 8 000 $ |
| Caserne niveau 2 (8 places) / 3 (12 places) | 100 000 $ / 300 000 $ | — |
| Équipement diamant / paladium (toute la garnison) | 150 000 $ / 450 000 $ | — |

- **Une première garde** : quatre fantassins, 80 000 $ et **8 000 $ par jour** — moins d'un tiers
  d'une journée de contrats au niveau 0 (~27 k, § 6 novovicies).
- **Une armée complète** (niveau 3 : un capitaine, deux médecins, quatre archers, cinq
  fantassins, en paladium) : ~1,35 M d'installation (350 k de recrues, 400 k de caserne, 600 k
  d'équipement) et **35 000 $ par jour** — une dizaine de minutes du revenu du milieu de partie
  (~190 k de l'heure, § 6 sexvicies), deux minutes de celui de fin de partie (1 M, § 3).
- **La solde ne court que joueur en ligne** : un jour d'absence ne coûte rien. En solo, c'est le
  juste prix : la garnison ne sert que quand tu es là (un assaut ne vient jamais en ton absence).
- **Les morts coûtent leur recrutement** : un assaut qui en tue trois coûte 60 000 à 100 000 $ ;
  la prime des soldats ennemis qu'ils abattent (25 à 400 $) ne rembourse pas une recrue. Le
  médecin et le repos à la caserne évitent l'essentiel des pertes.

**Ce qui le borne** : douze places au plus, la solde de chaque jour joué, la désertion (deux jours
sans solde, un soldat par jour), les morts, et la banque sous scellé pendant un assaut (ni
recrue, ni solde, ni équipement).

**Pas de boucle** : un soldat ne rapporte rien qu'un golem de fer ne rapporte (les monstres qu'il
tue laissent leur butin, sans expérience) ; il ne mine, ne récolte ni ne vend rien.

Leviers : `GarrisonRole` (prix, solde, vie, dégâts), `GarrisonTier` (multiplicateurs, prix),
`Garrison.CAPACITY` et `LEVEL_COST`, `Garrisons.PATIENCE` (jours avant désertion), `NEAR`/`FAR`
(rayon de déploiement), `REST_HEAL`.

## 6 quatertrigesies. Le gardien des cristaux — ce que vaut un combat (2026-11-24)

Description : `docs/PALAMOD.md` § 18. Le butin est celui de l'original : **de 0 à 2 pépites
d'endium** (une en moyenne), plus de 0 à Butin (2,5 en moyenne avec Butin III), et 5 XP. Une
pépite vaut ~40 k au catalogue (§ 6 ter).

- **Ce qu'il coûte** : une Pierre d'Invocation, qui ne manque pas (un filon de minerai par chunk
  dans la dimension, sans points de session), et l'accès à la dimension (Mineur 90).
- **Ce qu'il prend** : douze étourdissements pour six cristaux, et l'étourdissement ne vient que
  de la frappe au sol (15 à 25 % des attaques selon l'état), une attaque toutes les 6,5 s : ~66
  attaques à esquiver, **~7 minutes**, puis 1 000 PV à abattre. Le temps des cristaux ne se
  raccourcit pas avec l'équipement.
- **Ce qu'il rend** : ~40 k par combat, ~100 k avec Butin III, soit **~280 k à 700 k de l'heure**
  sans erreur — le revenu du milieu de partie (~190 k, § 6 sexvicies), en dessous de celui de fin
  de partie (1 M, § 3), pour un joueur Mineur 90. Le prix dynamique de la pépite baisse à mesure
  qu'on en vend.
- **Ce qui le borne** : une erreur le reforme (six cristaux, pleine vie) ; un seul à la fois à
  128 blocs ; les pierres se cassent à l'usage.

Leviers : `MinerBoss.Attack` (durées, chances), `STUN_TICKS`, `THORNS`, `ATTACK_DELAY`,
`dropCustomDeathLoot`.

## 7. Ce qui n'est pas encore chiffré

- **NTM NEXT** : la grille est cohérente, pas éprouvée. Les cotes que le wiki ne donne pas
  (titane, cuivre rouge, carbone, silicium, caoutchouc) sont les premières à revoir si une
  partie montre un écart.
- ~~**COMPONENT**~~ : catégorie supprimée le 2026-11-05, elle n'a jamais contenu d'item
- ~~**Caisses**~~ : tables écrites le 2026-11-11, devenues six boxes le même jour (§ 6 quater)
- ~~**Niveau global**~~ : barème fixé le 2026-11-23 (§ 6 novovicies)
