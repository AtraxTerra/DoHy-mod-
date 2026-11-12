# Datapack « DoHy — la Terre » (carte Earth24k)

Ce datapack allume, pour la carte **Earth24kChaosCubed**, ce que le mod DoHy sait faire d'une carte
de la Terre : le pays où l'on se trouve et un message au passage d'une frontière, le saut en
parachute depuis le spawn, la carte du monde au spawn, et les minerais posés là où le monde réel
les exploite.

La carte elle-même n'est pas ici : seulement son calage et la grille des pays.

## Installation

1. Mettre à jour le mod : le jar de DoHy doit contenir le lot « Terre » (PR #108 ou plus récent).
   **Sans lui, ne pas ouvrir la carte en 26.2** : le jeu la régénère par-dessus (voir plus bas).
2. Garder l'archive `Earth24kChaosCubed.zip` de côté : c'est l'original intact.
3. Télécharger [`dohy_terre.zip`](https://github.com/AtraxTerra/DoHy-mod-/raw/datapack-terre/dohy_terre.zip)
   et le déposer **tel quel**, sans le décompresser, dans
   `saves/Earth24kChaosCubed/datapacks/` (créer le dossier `datapacks` s'il n'existe pas).
4. Lancer le jeu et ouvrir le monde. Le datapack s'active tout seul.

## Ce qu'il contient

| Fichier | Rôle |
|---|---|
| `data/dohy/earth/earth.json` | Le calage de la carte : projection de Miller, 68,6755 blocs par degré, Greenwich en x = 1,33, équateur en z = 700,35 ; la liste des 256 pays et territoires. |
| `data/dohy/earth/countries.bin` | La grille des pays, une case pour 8 blocs (Natural Earth, domaine public). |
| `data/dohy/neoforge/biome_modifier/terre_minerais_rares.json` | Retire de la génération l'or, le diamant, l'émeraude, le lapis et la redstone : ils ne viennent plus que des gisements. |
| `data/hbm/hbm/world_config/worldgen.json` | Coupe les minerais d'overworld de NTM NEXT (son propre interrupteur) : ils ne viennent plus que des gisements. |

La table des gisements est dans le mod (`data/dohy/earth/deposits.json`). Pour la changer sans
recompiler, ajouter ce fichier au datapack : il remplace celui du mod.

## À savoir

- **Les chunks de cette carte attendent leur décoration** (étape `liquid_carvers` de WorldPainter).
  Minecraft 26.2 ne connaît plus cette étape et la lit comme « vide » : sans le correctif de DoHy,
  il repasse sa propre génération par-dessus la carte. Avec le mod, chaque chunk reçoit ses arbres
  et ses minerais à la première visite, puis ses gisements.
- Les gisements sont posés à l'arrivée d'un chunk, une seule fois. Un minerai d'un mod absent est
  ignoré.
- Ce qui a été essayé l'a été sur un monde ordinaire avec ces données, pas sur la carte elle-même.
  Premier lancement : vérifier en créatif, près du point d'apparition, que le relief est bien celui
  de la carte, avant de jouer pour de bon.

## Sources

- Pays : [Natural Earth](https://www.naturalearthdata.com/), `ne_10m_admin_0_countries`, domaine public.
- Calage : mesuré sur la carte (relevé terre/mer par chunk ajusté sur les côtes de Natural Earth,
  99 % de concordance hors Antarctique).
