# Datapack « DoHy — la Terre » (carte Earth24k)

Ce datapack allume, pour la carte **Earth24kChaosCubed**, ce que le mod DoHy sait faire d'une carte
de la Terre : le pays où l'on se trouve et un message au passage d'une frontière, le saut en
parachute depuis le spawn, la carte du monde au spawn, et les **ressources là où le monde réel les
exploite** : de vraies mines géantes, les champs de pétrole et de gaz, et des filons par pays.

La carte elle-même n'est pas ici : seulement son calage et la grille des pays.

## Installation

1. Mettre à jour le mod : le jar de DoHy doit contenir le lot « ressources de la Terre » (PR #116
   ou plus récent). **Sans le mod, ne pas ouvrir la carte en 26.2** : le jeu la régénère par-dessus
   (voir plus bas).
2. Garder l'archive `Earth24kChaosCubed.zip` de côté : c'est l'original intact.
3. Décompresser la carte dans `saves/`, puis déposer
   [`dohy_terre.zip`](https://github.com/AtraxTerra/DoHy-mod-/raw/datapack-terre/dohy_terre.zip)
   **tel quel**, sans le décompresser, dans `saves/Earth24kChaosCubed/datapacks/` (créer le dossier
   `datapacks` s'il n'existe pas).
4. Lancer le jeu et ouvrir le monde. Le datapack s'active tout seul.

Le mieux est de partir d'une carte neuve : les ressources se posent à la première visite de chaque
chunk. Sur une partie déjà commencée, un chunk déjà visité est repris une fois — ses minerais
d'origine retirés, puis les nouveaux posés ; un minerai qu'on y aurait posé à la main part avec.

## Ce qu'il contient

| Fichier | Rôle |
|---|---|
| `data/dohy/earth/earth.json` | Le calage de la carte : projection de Miller, 68,6755 blocs par degré, Greenwich en x = 1,33, équateur en z = 700,35 ; la liste des 256 pays et territoires. |
| `data/dohy/earth/countries.bin` | La grille des pays, une case pour 8 blocs (Natural Earth, domaine public). |
| `data/dohy/neoforge/biome_modifier/terre_minerais.json` | Retire de la génération tous les minerais du jeu (charbon, fer, cuivre, or, redstone, diamant, lapis, émeraude) : ils ne viennent plus que des gisements. |
| `data/hbm/hbm/world_config/worldgen.json` | Coupe les minerais d'overworld de NTM NEXT (son propre interrupteur). |
| `data/create/neoforge/biome_modifier/zinc_ore.json` | Coupe le zinc de Create : il vient des pays et des mines de zinc. |
| `data/createdieselgenerators/tags/worldgen/biome/deny_oil_biomes.json` | Plus de pétrole au hasard pour la pompe de Create Diesel Generators : seuls les vrais champs en ont. |

La table des ressources est dans le mod (`data/dohy/earth/deposits.json`). Pour la changer sans
recompiler, ajouter ce fichier au datapack : il remplace celui du mod.

## Les ressources sur la carte

- **Rien n'est plus semé partout.** La carte Earth24k portait déjà du fer, de l'or, du diamant, du
  charbon et du quartz du Nether à peu près partout : chaque chunk en est nettoyé à sa première
  visite, avec les minerais que le jeu ou les mods y auraient encore posés.
- **Filons par pays** : charbon, fer, cuivre et quartz dans les pays qui les exploitent ou les ont
  exploités (les vieux bassins d'Europe compris) ; l'or, le diamant, l'émeraude, le lapis, la
  redstone et les minerais de NTM NEXT et de Create comme avant, en filons moins nombreux.
- **245 gisements géants**, de vraies mines et de vrais champs à leur place : Kiruna, Carajás, le
  Pilbara, Escondida, Chuquicamata, Grasberg, le Witwatersrand, Muruntau, Jwaneng, Mir, la Ruhr, le
  Kouzbass, Cigar Lake, Bayan Obo, Ghawar, Burgan, Samotlor, le bassin permien, la mer du Nord… La
  commande `/gisements [ressource]` donne les plus proches, leur direction et leur distance ; au
  passage d'une frontière, le chat dit les ressources du pays.

## À savoir

- **Les chunks de cette carte attendent leur décoration** (étape `liquid_carvers` de WorldPainter).
  Minecraft 26.2 ne connaît plus cette étape et la lit comme « vide » : sans le correctif de DoHy,
  il repasse sa propre génération par-dessus la carte. Avec le mod, chaque chunk reçoit ses arbres
  à la première visite, puis ses ressources.
- Un minerai d'un mod absent est ignoré ; sans AE2, les mines de quartz n'ont pas de géodes.
- Ce qui a été essayé l'a été sur un monde ordinaire avec ces données, pas sur la carte elle-même.
  Premier lancement : vérifier en créatif, près du point d'apparition, que le relief est bien celui
  de la carte, avant de jouer pour de bon.

## Sources

- Pays : [Natural Earth](https://www.naturalearthdata.com/), `ne_10m_admin_0_countries`, domaine public.
- Calage : mesuré sur la carte (relevé terre/mer par chunk ajusté sur les côtes de Natural Earth,
  99 % de concordance hors Antarctique).
- Mines et champs : positions publiques des sites réels, à quelques kilomètres près ; leur taille
  dit leur rang mondial, pas un tonnage.
