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

## 2. Courbe des skills

`xp(lvl) = 100 × lvl^1.8` · `money(lvl) = 750 × lvl^1.5`

| Niveau | XP du niveau | XP cumulé | Money du niveau | Money cumulé | Heures (à 15 k XP/h) |
|---|---|---|---|---|---|
| 5 | 1 812 | 4 195 | 8 385 | 21 154 | 0,3 |
| 10 | 6 310 | 25 783 | 23 717 | 107 004 | 1,7 |
| 15 | 13 091 | 76 805 | 43 571 | 283 556 | 5,1 |
| 20 | 21 971 | 168 087 | 67 082 | 570 597 | 11,2 |
| 25 | 32 832 | 309 751 | 93 750 | 984 825 | 20,7 |
| 30 | 45 585 | 511 426 | 123 238 | 1 540 964 | 34,1 |
| 40 | 76 508 | 1 131 515 | 189 737 | 3 131 229 | 75,4 |
| 50 | 114 326 | 2 099 046 | 265 165 | 5 436 527 | 139,9 |

Lecture pour le rythme de jeu visé :

- **Niveau 10 en moins de 2 h** → la première soirée donne déjà 2 perks et ~107 k
- **Niveau 25 en ~21 h** → atteint vers la fin du premier mois
- **Niveau 50 en ~140 h** → objectif de fin de saison, pas de fin de mois
- **Un skill maxé rapporte 5,4 M cumulés.** Les 8 skills réunis ≈ 43 M, à comparer aux 500 M de l'autosell : les skills restent un bonus, jamais la source de revenu. C'est le principe 2 de `DESIGN.md`, vérifié chiffre en main.

Levier unique : `xp_base` dans `data/skills.json`. Le passer à 75 réduit tout de 25 %.

---

## 3. Progression attendue

Hypothèses : **2 h/soir en semaine (10 h), ~10 h le week-end** → **~20 h/semaine**.
Revenu effectif estimé : 25 k/h au début (le prix dynamique et les déplacements mangent la cible théorique), 200 k/h une fois les fermes en place, 1 M/h+ avec autosell et boosters.

| Jalon | Coût | Atteint vers | Heures cumulées |
|---|---|---|---|
| Premier palier de collection | — | soirée 1 | 2 |
| 3 contrats quotidiens + hebdo | — | semaine 1 | 20 |
| **Booster x2** | 2 M | fin semaine 2 | ~40 |
| **Caisse rare** | 5 M | semaine 3-4 | ~70 |
| Banque rentable (dépôt > 10 M) | — | mois 2 | ~110 |
| **Booster x3** | 25 M | mois 2 | ~130 |
| **Caisse légendaire** | 150 M | mois 3 | ~200 |
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

## 6 bis. Prix d'achat NTM NEXT

Volontairement énormes. NTM NEXT est un mod dont tout l'intérêt est de **construire
la chaîne** : pouvoir acheter ses lingots avec l'argent de fermes vanilla
court-circuiterait 200 heures de contenu. L'achat reste possible pour la pièce qui
manque, jamais pour s'industrialiser.

| Tag | Prix de base | Marge | Prix d'achat |
|---|---|---|---|
| `c:raw_materials` | 100 | ×30 | 3 000 |
| `c:gems` | 67 | ×40 | 2 680 |
| `c:dusts` | 30 | ×80 | 2 400 |
| `c:ingots` | 45 | ×100 | 4 500 |
| `c:plates` | 60 | ×120 | 7 200 |
| `c:nuggets` | 5 | ×100 | 500 |
| `c:crystals` | 90 | ×150 | 13 500 |

Deux propriétés vérifiées :

- **L'échelle est cohérente** : matière brute 3 000 → lingot 4 500 (le traitement
  ajoute de la valeur) → plaque 7 200 (faite de lingots).
- **9 nuggets = 1 lingot exactement** (9 × 500 = 4 500). Sans cette neutralité, on
  achèterait la forme la moins chère pour la convertir.

Les **prix de vente sont inchangés** : seule la matière brute se vend, au même prix
qu'avant.

## 7. Ce qui n'est pas encore chiffré

- **NTM NEXT** (phase 10) : les taux dépendent des recettes réelles du mod, à extraire de son source
- **COMPONENT** : catégorie achat seul, liste et prix à établir en phase 3
- **Caisses** : table de loot à écrire
- **Niveau global** : barème d'XP par type de jalon à fixer une fois les contrats en jeu
