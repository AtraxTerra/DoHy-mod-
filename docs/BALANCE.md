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

| Item | Prix du modèle | Plafonné à | Troc |
|---|---|---|---|
| perle de l'ender | 83 | **10** | 5 ém. → 1 |
| glowstone | 42 | **1** | 1 ém. → 2 |
| terracotta | 33 | **2** | 1 ém. → 1 |
| redstone | 28 | **1** | 1 ém. → 4 |
| quartz | 20 | **2** | 1 ém. → 1 |
| flèche | 17 | **1** | 1 ém. → 16 |
| lapis | 15 | **2** | 1 ém. → 1 |

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

Le butin vient de PalaMod et, pour les trois boxes de métal, de NTM NEXT (lingots par tag, pièces
de machine), plus de l'argent. Les trois boxes à thème n'ont pas de lot NTM : elles rendent la
même chose avec et sans.

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
| Saharia (1 M) | 89,1 % | 89,1 % |
| Paladium (5 M) | 87,5 % | 88,0 % |
| Halloween (20 M) | 86,3 % | 86,3 % |
| Trixium (50 M) | 88,3 % | 88,3 % |
| Endium (150 M) | 84,5 % | 90,2 % |

### Les pierres légendaires, box par box

La pierre est cotée 20 M. Trois boxes de plus en donnent, ce qui les rend moins rares qu'avant —
mais jamais moins chères qu'au marché, où elles passent deux jours sur cinq entre 14 M et 32 M
(§ 6 decies) :

| Box | Pierres par clé, en moyenne | Soit, par pierre |
|---|---|---|
| Paladium | 0,006 | ~830 M |
| Halloween | 0,136 | ~150 M |
| Trixium | 0,30 | ~170 M |
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

## 6 decies. Marché

Le marché **ne paie jamais** : aucune boucle d'argent n'y est possible, c'est un puits. Ce qu'il
faut tenir, ce sont trois choses.

**Pas de revente gagnante.** Un objet se revend au shop à son prix de base, le quart de sa cote.
L'annonce la moins chère est à 0,70 fois la cote : acheter au marché pour revendre perd toujours
au moins 64 %.

**Les clés.** Une clé en promotion coûte de 75 à 100 % de son prix. La box rend ~88 % de ce
prix en valeur boutique et ~22 % en liquide : même à 75 %, ouvrir pour revendre perd de l'argent.
Ce que la promotion change, c'est le prix d'accès — une clé Endium à 112 M au lieu de 150 M,
quand elle passe (une annonce sur quarante-trois du rayon). Les six clés sont au rayon, d'autant
plus rares que la box est chère (`KEY_OFFERS`).

**Les pierres légendaires.** Cotées 20 M (§ 6 quater), elles étaient le gros lot de la box
Paladium (alors « caisse rare ») et ne s'achetaient nulle part. Elles sont maintenant en vente environ **deux jours sur cinq**,
entre 14 M et 32 M. C'est le changement d'équilibrage du marché, voulu par le joueur (« raretés
de PalaMod ») : la pierre devient un achat de milieu de partie plutôt qu'un coup de chance. Si
c'est trop, baisser les poids des pierres dans `tools/market_offers.py`.

| Réglage | Valeur | Où |
|---|---|---|
| Annonces par jour | 3 + 4 + 2 + 3 | `CATEGORIES` |
| Bonne affaire / courant / gourmand | 15 % / 65 % / 20 % | `prices` |
| Renouvellement payé | 250 k, doublé, 4 par jour (3,75 M au plus) | `per_reroll`, `max_rerolls` |
| Cotes sans valeur boutique | fragment de rune 40 k et 150 k, rune 170 k, 620 k, 2,5 M ; essence 20 k, 80 k, 300 k ; pièce de donjon 400 k et 2 M ; capacité de spawner 450 k | `QUOTES` |

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

## 7. Ce qui n'est pas encore chiffré

- **NTM NEXT** : la grille est cohérente, pas éprouvée. Les cotes que le wiki ne donne pas
  (titane, cuivre rouge, carbone, silicium, caoutchouc) sont les premières à revoir si une
  partie montre un écart.
- ~~**COMPONENT**~~ : catégorie supprimée le 2026-11-05, elle n'a jamais contenu d'item
- ~~**Caisses**~~ : tables écrites le 2026-11-11, devenues six boxes le même jour (§ 6 quater)
- **Niveau global** : barème d'XP par type de jalon à fixer une fois les contrats en jeu
