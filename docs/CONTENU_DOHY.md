# Contenu propre à DoHy

Ce qui ne vient pas de Paladium. Le contenu repris de PalaMod vit sous l'espace de noms `palamod`
pour que son pack de textures s'applique (`docs/PALAMOD.md`) ; ce qui est décrit ici vit sous
`dohy`, a son propre onglet créatif, et **ses textures sont dessinées par
`tools/dohy_assets.py`** — rien n'est copié d'un autre mod.

## Serveur de trading (`dohy:trading_server`)

Inspiré des serveurs de trading de **NationsGlory**, où la compétence d'ingénieur consiste entre
autres à en installer un. Ici : une machine posée qui transforme de l'énergie en argent.

### Ce qu'elle fait

- **Sous tension, elle produit 9 000 $ par heure**, par tick, sans arrondi perdu. L'argent
  s'accumule dans sa caisse ; un clic sur la carte « Caisse » de son écran l'encaisse.
- **Caisse pleine (8 h de production, 72 000 $), elle s'arrête** : un revenu passif qui demande
  quand même de passer.
- **Quatre serveurs tournent à la fois par joueur.** Les suivants attendent (« En attente ») et
  prennent la place d'un serveur cassé ou déchargé. Le compte se fait sur les machines en mémoire :
  un serveur dans un chunk déchargé ne produit pas et ne prend pas de place.
- **Elle appartient à qui la pose.** Seul le propriétaire encaisse. Cassée, elle lui rend sa caisse.
- Ses voyants s'allument quand elle produit (état de bloc `lit`, lumière 7).

### Compatibilité NTM NEXT

DoHy ne dépend pas de NTM NEXT et n'appelle rien de son code. La compatibilité passe par deux
standards que NTM NEXT lit lui-même.

| | Comment | Vérifié |
|---|---|---|
| **Énergie** | Le bloc expose la capacité d'énergie de NeoForge (`Capabilities.Energy.BLOCK`, en FE), en réception seule, sur toutes ses faces. Les câbles de NTM NEXT cherchent cette capacité sur leurs voisins et y poussent leur courant en convertissant **5 HE en 1 FE** (`EnergyConversion`, `he_ratio` 5 / `fe_ratio` 1). Le serveur tire donc **250 HE par tick**. | La capacité, en jeu, par les mêmes appels qu'un câble (insertion en transaction, simulation annulée, extraction refusée). **Pas avec un câble NTM réel** : la machine de test n'a pas NTM. |
| **Recette** | `data/dohy/recipe/assembly_machine/ass.dohy_trading_server.json`, de type `hbm:assembly_machine` : 8 plaques d'acier (`#c:plates/steel`), 4 circuits avancés, 2 écrans cathodiques, 1 moteur, 4 lingots de paladium ; 600 ticks, 500 HE par tick. Elle porte la condition `neoforge:mod_loaded` `hbm`. | Le format, sur les 707 recettes d'assemblage de NTM NEXT et son codec (`GenericRecipe`). Sans NTM, la recette est bien ignorée sans erreur. **Pas dans la machine d'assemblage elle-même.** |

Sans NTM NEXT, une recette d'établi prend le relais (blocs de fer, bloc de redstone, diamants,
lingot de paladium, bloc d'or) ; il faut alors un autre mod qui fournit du FE pour l'alimenter.

### Code et réglages

- `tech/DoHyTech` (enregistrement, capacité), `TradingServerBlock`, `TradingServerBlockEntity`,
  `TradingServerMenu`, `TradingServerConfig`.
- `data/trading_server.json` : `income_per_hour`, `energy_per_tick`, `energy_capacity`,
  `energy_max_input`, `buffer_hours`, `max_per_player`.
- Le tag `minecraft:mineable/pickaxe` est écrit par `tools/palamod_assets.py`, qui y inscrit
  aussi les blocs d'ici.
