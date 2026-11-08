# DoHy

Mod d'économie et de progression pour **Minecraft 26.2 / NeoForge**, conçu pour une partie **solo** en monde normal.

Inspiré de DonutSMP (monnaie, shop à prix dynamiques, boosters), d'Hypixel SkyBlock (collections, jalons) et de Paladium (métiers, PalaMod), mais réécrit pour un seul joueur : tout ce qui n'a de sens qu'entre joueurs (hôtel des ventes, primes, duels, équipes) est écarté.

## Documents

| Fichier | Contenu | Fréquence de changement |
|---|---|---|
| [`docs/DESIGN.md`](docs/DESIGN.md) | Spec des systèmes, anti-exploits, ordre d'implémentation | rare |
| [`docs/BALANCE.md`](docs/BALANCE.md) | Tous les chiffres : modèle de prix, courbes, progression | souvent |
| [`PROGRESS.md`](PROGRESS.md) | État d'avancement, blocages, journal | chaque session |

## Données

```
data/
  rates.csv              <- SOURCE : taux d'obtention horaire par item (édité à la main)
  prices.csv             <- généré, lisible en tableur
  generated/prices.json  <- généré, consommé par le mod
  job_progression.json   <- courbe, sources d'XP et perks des métiers
  collections.json       <- paliers et récompenses
  contracts.json         <- contrats quotidiens et hebdo, demande du jour
tools/
  gen_prices.py          <- rates.csv -> prices.csv + prices.json
```

**Les prix ne s'écrivent jamais à la main.** Ils dérivent d'un modèle :

```
base_price = ( TARGET_RATE[tier] / rate_per_hour ) × MONEY_MULTIPLIER
```

Pour rééquilibrer toute l'économie d'un cran :

```bash
python3 tools/gen_prices.py --multiplier 0.6
```

Le script vérifie au passage que le revenu horaire reste constant à l'intérieur de chaque tier, et refuse un `rates.csv` contenant un tier inconnu.

## Principes

1. Le grind doit se **diversifier**, pas se répéter — c'est le rôle des prix dynamiques.
2. Les revenus passifs restent **petits** : les 4 métiers maxés rapportent 80 M, l'autosell en coûte 500 M.
3. **Un seul levier global** d'équilibrage.
4. Rien d'irréversible pour le joueur : aucune perte non plafonnée.
5. Tout ce qui est chiffré vit dans un **datapack**, pas dans le code.

## Rythme de jeu visé

~2 h en semaine le soir, beaucoup le week-end. Deux règles qui en découlent et qui traversent tout le projet :

- aucun système ne dépend d'une connexion quotidienne (pas de streak qui se casse) ;
- tous les compteurs sont en **temps de jeu**, jamais en temps réel — sinon le week-end écrase les prix et les soirées de semaine n'ont plus de revenu.

## État

Doc et data terminés. Aucun code écrit — voir [`PROGRESS.md`](PROGRESS.md).
