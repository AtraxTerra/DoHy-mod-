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

## 6 bis. NTM NEXT — rareté de la matière × forme

Un tag ne dit que la **forme** d'un item. La première version ne voyait donc que
ça, et un lingot de schrabidium coûtait un lingot de cuivre — absurde dans un mod
où toute la progression tient à la rareté des matières. Le prix a maintenant deux
dimensions : **ancre de la rareté × facteur de la forme**.

### Les quatre raretés

| Rareté | Matières | Lingot à l'achat | Matière brute à la vente |
|---|---|---|---|
| Commune | uranium, titane, cuivre, plomb, aluminium, fluorite, soufre | 4 500 | 100 |
| Peu commune | thorium, tungstène, béryllium, cobalt, lithium | 112 500 | 800 |
| Rare | terres rares, niobium | 2,80 M | 6 400 |
| Très rare | schrabidium, australium, osmiridium | **70,00 M** | 51 200 |

**Deux échelles différentes, et c'est voulu.** L'achat monte de **×25 par cran**
(4 500 × 25³ = 70,3 M, d'où les 70 M demandés pour le très rare). La vente ne monte
que de **×8**. Un achat est un puits à argent qu'on ne peut pas exploiter, donc il
peut être extrême ; une vente est un revenu qu'on peut industrialiser. L'asymétrie
qui en résulte va de ×30 pour le commun à **×911 pour le très rare** — acheter la
pièce qui manque reste possible, s'industrialiser au shop jamais.

### Les sept formes

Facteur relatif au lingot : matière brute 0,667 · gemme 0,6 · lingot 1,0 ·
plaque 1,6 · poudre 0,533 · nugget 1/9 · cristal 3,0.

| Forme | Commune | Peu commune | Rare | Très rare |
|---|---|---|---|---|
| matière brute | 3,00 k | 75,00 k | 1,87 M | 46,67 M |
| nugget | 500 | 12,50 k | 311,08 k | 7,78 M |
| poudre | 2,40 k | 60,00 k | 1,49 M | 37,33 M |
| lingot | 4,50 k | 112,50 k | 2,80 M | 70,00 M |
| plaque | 7,20 k | 180,00 k | 4,48 M | 112,00 M |
| cristal | 13,50 k | 337,50 k | 8,40 M | 210,00 M |

Trois propriétés vérifiées numériquement :

- **La colonne commune reproduit l'ancien barème** déjà calibré (brute 3 000,
  lingot 4 500, plaque 7 200, nugget 500, cristal 13 500). Rien ne change pour les
  matières courantes — un bon signe que le modèle est le bon.
- **L'échelle reste cohérente** : brute sous le lingot (le traitement ajoute de la
  valeur), plaque au-dessus (faite de lingots).
- **9 nuggets = 1 lingot**, à 0,01 % près aux quatre raretés (arrondi). Sans cette
  neutralité, on achèterait la forme la moins chère pour la convertir.

### Australium et osmiridium ne se vendent jamais

Ils **ne se minent pas** : ils sortent de procédés, donc ils sont *productibles*.
Les rendre vendables, même sous forme brute, ferait d'une installation une
imprimante à billets — exactement l'exploit que la règle « seule la matière brute
se vend » ferme ailleurs. Le schrabidium, lui, a un minerai : sa matière brute
reste vendable.

### Ce que rapporte vraiment la matière brute très rare

51 203 l'unité au tarif plein, mais le prix dynamique freine (`FOUND` : S0 = 64,
plancher 35 %) :

| Vendu d'un coup | Recette | Par unité | % du prix de base |
|---|---|---|---|
| 16 | 0,76 M | 47 804 | 93 % |
| 64 | 2,62 M | 40 899 | 80 % |
| 256 | 7,40 M | 28 919 | 56 % |
| 1 024 | 21,20 M | 20 706 | 40 % |
| 4 096 | 76,26 M | 18 617 | 36 % |

Le plancher mord à partir de 304 unités écoulées. C'est une belle récompense
d'expédition, pas une rente.

### Les matières inconnues retombent au commun

Une matière que `tag_prices.json` ne connaît pas garde l'ancien comportement et le
prix d'une matière courante. Si NTM ajoute un métal, le mod continue de
fonctionner, au défaut prudent.

## 7. Ce qui n'est pas encore chiffré

- **NTM NEXT** : les quatre raretés couvrent 17 matières nommées ; les autres
  retombent au commun. À compléter si le mod en apporte d'autres qui méritent un cran.
- ~~**COMPONENT**~~ : catégorie supprimée le 2026-11-05, elle n'a jamais contenu d'item
- **Caisses** : table de loot à écrire
- **Niveau global** : barème d'XP par type de jalon à fixer une fois les contrats en jeu
