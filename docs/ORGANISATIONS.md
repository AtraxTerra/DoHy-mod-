# Organisations, entreprises et économie souterraine (chapitre 24)

> Demande du joueur (2026-11-22) : fusionner factions et pays en un système politique unifié
> (hiérarchie, diplomatie, alliances, pactes, guerres, territoires, influence), ajouter des
> entreprises légales et illégales (stupéfiants, armement, blanchiment, contrebande, marché
> noir, racket, casinos, façades, recel) avec leur économie, les faire interagir avec les
> nations et les territoires, les intégrer à l'interface, créer les objets et leurs textures, et
> une dimension « farmland » du Chasseur.
>
> **Choix du joueur** : des **PNJ**, pour jouer seul — les membres, grades et employés sont des
> personnages non joueurs qui travaillent pour lui (« PNJ, pour jouer seul »).
>
> Ce chapitre s'appuie sur le lot 23a (`docs/NATION.md`) : la faction **est** la nation. Les
> arbres de technologie NTM (lots 23b à 23i) restent prévus après ce chapitre.

## Ce qui existait et sert de socle

| Système | Où | Réutilisé pour |
|---|---|---|
| La nation (faction solo) : claims, banque nationale, arbre, ligues | `faction/` | le propriétaire des entreprises, la caisse |
| Les pays de la carte de la Terre, capitale, conquête | `earth/EarthNations` | la localisation, l'influence, les lois |
| Les douze nations rivales : humeur, ultimatums, guerres, sièges, coalitions | `faction/war/` | la diplomatie, les autorités |
| Les mines de la Terre, versement horaire hors ligne | `earth/mine/` | le modèle des revenus horaires |
| La réserve d'or, le marché international | `faction/reserve`, `faction/market` | la finance, le commerce |
| Le marché noir du receleur, la marchandise volée et ses griefs | `blackmarket/` | la revente des produits illégaux, le recel |
| L'horloge réelle à l'heure de Paris, le journal d'absence | `RealClock`, `faction/absence` | les échéances, les notifications |

## Les lots

| Lot | Contenu | État |
|---|---|---|
| 24a | Régime de la nation, hiérarchie de cadres PNJ (grades, postes, salaires, loyauté), diplomatie avec les douze nations (pacte, accord commercial, alliance, rivalité), onglets Organisation et Diplomatie | fait |
| 24b | Entreprises : le modèle commun (propriétaire, catégorie, niveau, capital, coûts, revenus, employés, stock, lieu, risque, améliorations), les catégories légales et illégales, les revenus horaires hors ligne, l'influence par pays, les six organisations criminelles, l'onglet Entreprises | fait |
| 24c | Objets, blocs et textures : plantes, produits, conditionnement, caisses, balance, matériel de laboratoire, protections, bidons, laboratoire clandestin, table de conditionnement, coffres sécurisés — chacun branché sur une entreprise | fait |
| 24d | Interactions : lois des pays, descentes de police, opérations contre le crime, rivalités qui coûtent, alliances qui rapportent, contrats entre organisations, notifications | fait |
| 24e | La farmland : dimension du Chasseur où n'apparaissent que les créatures du métier | à faire |

## Lot 24a — régime, cadres et diplomatie (fait, 2026-11-22)

### Le régime

La nation choisit ce qu'elle est. Le premier choix est libre ; ensuite, une fois par semaine.

| Régime | Légal | Illégal | Risque | Confiance des nations | Crainte |
|---|---|---|---|---|---|
| Groupe indépendant (départ) | ×1,00 | ×1,00 | ×1,00 | 0 | 0 |
| État officiel | ×1,15 | ×0,85 | ×1,20 | +8 | 0 |
| Organisation criminelle | ×0,90 | ×1,25 | ×0,85 | −8 | +3 |
| Faction militaire | ×0,95 | ×1,05 | ×1,00 | −4 | +10 |

Les multiplicateurs de revenus et de risque servent aux entreprises (lot 24b). La confiance et
la crainte pèsent dès maintenant sur l'humeur des nations (pondérées par leur honneur et leur
prudence).

### La hiérarchie : cadres PNJ

- **Cinq grades** : Recrue (150 $/j), Membre (300), Lieutenant (600), Capitaine (1 200),
  Haut cadre (2 500) ; le talent (1 à 5) module le salaire de ×0,9 à ×1,3. Monter coûte une
  semaine du nouveau salaire et demande d'avoir servi (0, 1, 2, 3 puis 4 jours au grade).
- **Cinq postes**, chacun avec un grade minimal :

  | Poste | Grade | Effet |
  |---|---|---|
  | Bras droit | Haut cadre | les autres postes +15 %, leur loyauté s'use moitié moins |
  | Trésorier | Capitaine | salaires (et frais, lot 24b) jusqu'à −20 % |
  | Général | Capitaine | les nations craignent de t'attaquer (jusqu'à −10 d'humeur) |
  | Diplomate | Capitaine | traités jusqu'à −30 %, seuils +10, une ou deux places de plus |
  | Chef des opérations | Capitaine | risque des affaires illégales jusqu'à −30 % (lot 24b) |

  L'efficacité d'un poste suit le talent et l'écart de grade, moitié moins si le cadre n'est
  plus loyal (moins de 30).
- **Recrutement** : trois candidats par jour (grade Recrue à Lieutenant, talent, caractère),
  prime d'embauche de deux jours de salaire. 3 cadres au plus, un de plus par palier du
  Gouvernement.
- **Caractères** : fidèle (sa loyauté monte deux fois plus vite), ambitieux (sans promotion
  depuis deux semaines, il s'impatiente), mercenaire (un salaire en retard compte double),
  prudent (sa loyauté bouge moitié moins).
- **La paie** : chaque heure pleine, même hors ligne (une semaine au plus), depuis la banque
  nationale. Payés, +0,5 de loyauté par jour ; impayés, −5 et une dette. À zéro, le cadre
  s'en va. Pendant un siège, rien ne sort de la banque : la dette court.

### La diplomatie

Un traité par nation, signé contre un cadeau d'usage (prix × avidité de la nation, moins le
diplomate). La nation refuse au-dessus d'un seuil d'hostilité, en pleine crise (ultimatum,
guerre), ou si tu l'as déclarée rivale.

| Traité | Seuil | Prix de base | Durée | Effets |
|---|---|---|---|---|
| Pacte de non-agression | 50 | 20 000 $ | 14 j | ni ultimatum ni guerre, hors des coalitions, humeur −10 |
| Accord commercial | 40 | 15 000 $ | 14 j | taxe du marché −1 point, humeur −6 (revenus de ses pays au lot 24b) |
| Alliance | 25 | 60 000 $ | 30 j | pas de guerre, taxe −2 points, humeur −18 ; demande un pacte ou un accord, qu'elle remplace |

- Deux traités au plus, trois avec un diplomate, quatre avec un diplomate haut cadre. La taxe
  du marché ne descend pas sous 2 % par les traités.
- **Rompre** un pacte ou une alliance est une trahison : l'hostilité monte aussitôt de 12 (20
  pour une alliance) et la nation en garde un grief ; un accord tombe sans suite. Renouveler
  prolonge depuis l'échéance.
- **Rivalité déclarée** : le traité tombe, l'hostilité monte de 10 (et un grief), son humeur
  tend à monter de 12 ; ses pays rapportent un quart de points de conquête en plus.
- **Dans l'IA de guerre** (`WarStrategy`) : la diplomatie s'ajoute à l'humeur (`mood`), un pacte
  ou une alliance empêche l'ultimatum et la guerre (`think`) et retire la nation des
  coalitions (`coalition`).

### L'interface

« Ma nation » avait huit onglets au lot 24a (neuf depuis le lot 24b, avec **Entreprises**) : Vue d'ensemble, Territoire, **Organisation** (régime, postes,
cadres, candidats, paramètres, journal), **Diplomatie** (traités, les douze nations, la guerre
et le pillage), Technologies, Économie, Events, Classement. Fiches : un cadre (promotion,
poste, renvoi), une nation (les trois traités, la rivalité, son caractère). La guerre, les
bases ennemies et les paramètres deviennent des pages de détail de leur onglet.

Commandes : `/f organisation`, `/f diplomatie` ; pour les opérateurs,
`/f organisation avancer <heures>` fait passer le temps (paie, service, traités) pour essayer.

### Données

`Organisation`, pièce jointe du joueur gardée à la mort : régime et sa date, cadres, candidats
et leur date, traités, rivales, paie réglée jusqu'à, dette, journal (24 lignes). Dissoudre la
nation la remet à zéro.

## Lot 24b — les entreprises (fait, 2026-11-22)

### Le modèle commun

Une entreprise (`Enterprise`) appartient à la nation : un nom (`/f entreprises renommer`), un
**secteur**, une drogue pour une plantation ou un laboratoire, un **niveau** de 1 à 5, le
**capital** investi (ouverture, niveaux, améliorations), son **siège** (le pays et le point où
le joueur se tenait), ses **employés PNJ**, ses **améliorations**, une fermeture éventuelle
(descente, sabotage, grève) et ce qu'elle a rapporté. Son **gérant** est un cadre libre de
l'organisation (lot 24a) : son poste vaut `@id`, si bien qu'un cadre tient un poste **ou** une
gérance, jamais les deux.

| Réglage | Valeur |
|---|---|
| Entreprises tenues | 4, plus 2 par palier du Gouvernement (16 au plus) |
| Niveau 1 → 5 | production ×1 · 1,8 · 2,6 · 3,4 · 4,2 ; frais ×1 · 1,5 · 2 · 2,5 · 3 |
| Prix d'un niveau | 0,8 · 1,2 · 1,8 · 2,6 fois le prix d'ouverture (vers 2, 3, 4, 5) |
| Employés | *n* par niveau selon le secteur, 12 $/h chacun ; une équipe incomplète produit de 30 % à 100 % |
| Gérant | production +20 % × sa force, risque −20 % × sa force (talent, grade, moitié sous 30 de loyauté) |
| Améliorations | équipement +20 % · logistique/planques frais −20 % · assurance (catastrophes et guerre deux fois moins) ou guetteurs (risque −25 %) · formation (salaires −25 %, production +10 %) |
| Fermer | rend 40 % du capital (à la banque, ou en argent sale pour une affaire illégale) |

### Les secteurs

| Secteur | Prix | Chaque heure (niveau 1) | Frais/h | Condition, particularité |
|---|---|---|---|---|
| Ferme | 20 000 | 420 $ propres | 120 | la saison (croissance des cultures à sa latitude) |
| Carrière | 35 000 | 650 $ | 200 | +20 % avec une mine de la Terre de la nation dans le pays |
| Usine | 60 000 | 1 100 $ | 380 | 20 d'influence dans le pays |
| Commerce | 30 000 | 560 $ | 160 | l'influence compte double (60 % à 180 %) |
| Banque | 120 000 | 1 900 $ | 500 | un pays que tient la nation |
| Transport | 45 000 | 750 $ | 260 | +3 %/niveau aux usines et commerces (25 % au plus), −5 %/niveau de risque à la contrebande |
| Plantation | 30 000 | matière première (cannabis 24, coca 14, pavot 12 u/h) | 150 | le climat : cannabis jusqu'à 60°, coca sous les tropiques, pavot entre 15 et 45° |
| Laboratoire | 60 000 | transforme 16 u/h | 300 | matière de ses plantations, sinon achetée |
| Entrepôt | 25 000 | +800 places par stock et par niveau | 100 | saisies divisées par deux |
| Réseau | 40 000 | vend 24 u/h au prix de la rue | 220 | les produits les plus chers d'abord |
| Atelier d'armes | 90 000 | 4 caisses/h | 450 | 2 fers du coffre de la banque par caisse, sinon 150 $ sales de pièces |
| Trafic d'armes | 70 000 | vend 5 caisses/h (900 $, +40 % en guerre) | 300 | |
| Contrebande | 50 000 | 900 $ sales | 250 | +10 % par pays où la nation fait affaire (doublé au plus) |
| Société écran | 45 000 | blanchit 3 000 $/h, +300 $ propres | 180 | commission 15 % (−2 points par niveau) |
| Casino | 80 000 | 1 300 $ sales, blanchit 1 500 $/h | 400 | commission 20 % |
| Racket | 35 000 | 900 $ sales × influence/50 | 150 | 30 d'influence |
| Recel | 30 000 | 600 $ sales | 120 | le receleur du spawn paie +5 %/niveau (25 % au plus) |

Les drogues, toutes **abstraites** (des unités, aucun procédé) :

| Drogue | Matière première | Fournisseur | Gros | Rue | Risque |
|---|---|---|---|---|---|
| Cannabis | feuilles | 30 $ | 120 $ | 200 $ | +0 |
| Cocaïne | feuilles de coca | 70 $ | 260 $ | 420 $ | +8 |
| Héroïne | pavot | 80 $ | 290 $ | 460 $ | +10 |
| Synthèse | précurseurs (90 $ sans accord, 60 $ avec) | — | 210 $ | 330 $ | +5 |

Chaque stock tient 300 unités, plus ce qu'ajoutent les entrepôts ; le surplus se revend au
fournisseur avec un accord (75 % du gros, 70 % de la matière, 400 $ la caisse), se perd sinon.

### L'heure

`Payout` fait tourner les entreprises heure pleine par heure pleine, **une semaine au plus hors
ligne** (comme les mines) : pousser et monter, transformer, rapporter, vendre, ranger,
payer la part des alliés, les frais, blanchir, puis la police. Une entreprise ouverte pendant
l'heure ne compte qu'à la suivante. La même simulation, sans police, donne l'aperçu des menus.

Ce qui fait la production d'une entreprise : niveau, équipe, améliorations, gérant, **régime**
(légal ou illégal), **influence**, **saison** (ferme, plantation), **catastrophe** en cours
dans le pays (−50 % pour ce qui a des murs, des champs ou des machines, −20 % sinon), et la
nation qui tient le pays : la tienne +10 % (légal), alliance +20 %, accord commercial +15 %,
rivale −15 %, guerre −40 % (légal) ou +10 % (illégal).

Le versement : l'argent propre et le blanchi, moins les frais, vont à la banque nationale en
une ligne (« Entreprises (n h) »). Des frais que ni la banque ni l'argent sale ne couvrent
mettent les employés en grève 6 h. Un toast chaque heure, un message au retour, et une ligne
« Entreprises » dans le rapport d'absence.

### Argent sale, risque et descentes

L'argent sale paie d'abord les affaires illégales, les accords et les tributs ; il se blanchit
par les sociétés écran (les moins chères d'abord) puis les casinos. Le **risque** d'une affaire
illégale (sur 100, 95 au plus) : son secteur et sa drogue, +3 par niveau, +0,5 par employé, ×
le régime, × le pays (le tien −25 %, une alliance −10 %, une rivale +10 %, une guerre +20 %), ×
l'influence (120 % à 60 %), − le chef des opérations (30 % au plus), − le gérant, − les
guetteurs, + 15 en guerre de territoire, + jusqu'à 15 pour l'argent sale qui dort (5 par
200 000 $). Chaque heure, une **descente** a une chance de 5·10⁻⁷ × risque^2,5 (1 %/h vers
60) : fermée 24 h, la moitié de son stock saisie (un quart avec un entrepôt), 15 % de l'argent
sale saisi, 250 000 $ au plus ; deux descentes au plus par versement.

### L'influence

Calculée, jamais gardée (`Influence`), de 0 à 100 : tenir le pays +40, capitale +15, claims +1
(15 au plus), entreprises +3 par niveau (30 au plus), traité avec la nation qui le tient (pacte
+3, accord +6, alliance +10), rivale −10, guerre −15, organisation criminelle de la région (−10
en guerre, +5 alliée). Revenus de 80 % à 140 %, risque de 120 % à 60 %.

### Les six organisations criminelles

Une par région (le cœur du pays décide : `Region`), toutes inventées :

| Organisation | Région | Spécialité (avec un accord) |
|---|---|---|
| Le Syndicat des Docks | Amérique du Nord | contrebande +25 % (+35 % alliée) |
| La Compagnie du Condor | Amérique du Sud | recel +25 % (+35 %), receleur +5 % |
| La Famille Valcorte | Europe | casinos : +25 % de blanchiment, −5 points de commission |
| La Caravane Écarlate | Afrique et Moyen-Orient | réseaux : +25 % de ventes (+35 %) |
| La Confrérie du Lotus | Asie | vend des caisses à tes trafics (550 $, −15 % alliée ; la moitié de ce qu'ils peuvent encore vendre), rachète le surplus |
| Le Cartel des Récifs | Océanie | vend matière première et produits (−15 % alliée ; des produits pour la moitié de ce que tes réseaux peuvent encore écouler), rachète le surplus |

Chaque jour, une organisation **neutre** s'échauffe de 2 × la présence (niveaux des affaires
illégales dans sa région) × (0,5 + son agressivité), puis revient de 3 vers 30 − l'estime du
régime (crime : 15 ; État : 40). Neutre, elle coûte −10 % aux affaires de sa région. À **75**,
c'est la **guerre de territoire** (7 jours) : −40 %, +15 de risque, un sabotage probable par
jour (fermée 12 h). Un **accord** (14 jours, 30 000 $ selon son avidité, sous 60 d'hostilité)
la calme et la met au service de la nation ; une **alliance** (30 jours, 90 000 $, sur un accord
et sous 25) donne +15 % et −10 % de risque sur son territoire contre 5 % des gains de la
région. Rompre fâche (+25 ou +40, selon sa rancune). En guerre : un **tribut** l'arrête, une
**riposte** (30 000 $, de 10 à 90 % selon le général et le régime) aussi, et l'affaiblit 14 jours ;
ratée, deux jours de guerre en plus et une affaire fermée.

### L'interface

Un neuvième onglet **Entreprises** (pastille : GUERRE, FERMEE ou le nombre d'entreprises) :
tableau de bord (revenus propres et sales, frais, blanchiment, stocks, prochain versement), les
entreprises (niveau, pays, revenu, risque), « Ouvrir et gérer », les six organisations, le
journal. Pages : le catalogue de ce qu'on peut ouvrir là où l'on est (prix, estimation, risque,
raison d'un refus), la fiche d'une entreprise (activité, frais, lieu, état, niveau, employés,
gérant, fermeture, améliorations, et le détail de chaque facteur), les comptes (courbe des
48 dernières heures, 7 jours, argent sale, stocks, chaque entreprise), le territoire et
l'influence pays par pays, une organisation (relation, gestes, poids, affaires sur son
territoire). La vue d'ensemble, l'Économie et le Territoire y renvoient ; l'onglet
Organisation montre les gérants.

Commandes : `/f entreprises`, `/f entreprises renommer <id> <nom>` ; pour les opérateurs,
`/f entreprises avancer <heures>`.

### Données

`Holdings`, pièce jointe du joueur gardée à la mort : entreprises, argent sale, stocks, heure
payée, relations avec les organisations, 7 jours de comptes, 48 heures de revenus et de frais,
journal (30 lignes), dernier versement. Dissoudre la nation la remet à zéro.

## Lot 24c — objets, blocs et textures (fait, 2026-11-22)

Paquet `underground/`, onglet créatif « Berskar — économie souterraine ». Textures 16×16
**originales**, dessinées par `tools/underground_assets.py` (qui écrit aussi modèles, états de
blocs, définitions d'objets, butins, recettes et tags). Tout reste **abstrait** : des étapes de
jeu, aucun procédé.

| Objet ou bloc | D'où il vient | À quoi il sert |
|---|---|---|
| Graines de cannabis, de coca, de pavot | l'herbe sauvage (une sur quarante, celles qui poussent là), les plantes mûres, les fournisseurs (Comptes) | se plantent à la latitude de leur drogue (cannabis jusqu'à 60°, coca sous les tropiques, pavot entre 15 et 45° ; sans carte, la température du biome) |
| Plant de cannabis, arbuste de coca, pavot | les graines | huit stades ; mûrs, ils donnent feuilles ou capsules et des graines |
| Feuilles de cannabis, de coca, capsule de pavot | les plantes | le séchoir, le laboratoire ; une unité de matière dans les stocks |
| Bidon de précurseurs | les fournisseurs (90 $ sans accord, 60 $ avec le Cartel des Récifs) | le laboratoire ; une unité de matière de synthèse |
| Herbe séchée, cocaïne, héroïne, pilules, cristaux | le séchoir, le laboratoire | la table de conditionnement ; une portion (les cristaux deux) |
| Sachet vide | 3 papiers → 6 | la table de conditionnement |
| Sachets (herbe, cocaïne, héroïne, pilules, cristaux) | la table : 4 portions + 1 sachet, la balance s'use | 4 portions (cristaux 8) dans les stocks ; 8 sachets font un colis |
| Colis (herbe, cocaïne, héroïne, synthèse) | la table : 8 sachets + 1 papier | 32 portions dans les stocks |
| Balance de précision | recette | obligatoire pour peser un sachet, 256 pesées |
| Matériel de laboratoire | recette | la recette du laboratoire, et sur soi pour faire des cristaux |
| Masque et combinaison de protection | recettes (cuir) | portés, ils arrêtent les vapeurs : sans rien, nausée et poison ; avec une pièce, nausée |
| Pièces usinées, caisse d'armes scellée | l'établi : 3 fers → 2 pièces ; 4 pièces + 2 fers → 1 caisse | une caisse dans les stocks, que les trafics d'armes vendent |
| Séchoir | recette (bâtons, ficelle) | 4 feuilles de cannabis, 2 minutes, 3 portions d'herbe ; une fournée à la fois |
| Laboratoire clandestin | recette (matériel, alambic, chaudron) | herbe (4 feuilles → 3), cocaïne et héroïne (4 + 1 bidon → 3), pilules (2 bidons → 4), cristaux (3 bidons → 3, matériel sur soi) |
| Table de conditionnement | recette | sachets et colis |
| Établi d'armurier | recette | pièces et caisses scellées |
| Planque sécurisée | recette | 27 cases, à son seul propriétaire, résiste aux explosions ; à la fermeture, verse aux stocks ce qu'elle peut, garde le reste |

Les comptes de l'onglet Entreprises gagnent deux sections : **Sortir des stocks** (un colis,
un sachet ou une caisse, en objet) et **Fournisseurs** (16 bidons, 8 graines, payés en argent
sale d'abord). Les trois postes ont leur écran (clic gauche une fois, clic droit jusqu'à seize
fois) ; la planque a celui des coffres du kit.

### Postes en relief

Les cinq blocs sont des **modèles à éléments**, comme l'alambic ou le lutrin du jeu, et leur
forme de collision suit leurs pièces (tournée comme le poste, `TurnedShapes`) :

- **Laboratoire clandestin** : un meuble d'acier à deux portes et bandeau de danger, un plan de
  travail noir, une hotte grillagée au fond ; dessus, une plaque chauffante et son ballon, un
  pont de distillation vers un erlenmeyer, deux tubes à essai sur leur portoir, un bidon de
  précurseurs.
- **Table de conditionnement** : une table de chêne à étagère ; dessus, la balance (écran vert,
  plateau, un peu de poudre), trois sachets fermés d'un trait rouge, un colis au ruban, un
  rouleau ; dessous, des cartons à plat et des sachets vides.
- **Établi d'armurier** : un caisson à trois tiroirs, un plateau d'acier, un étau (vis et barre,
  une pièce serrée), une caisse scellée cerclée, un bac de pièces, un marteau ; sur l'étagère,
  une caisse à outils.
- **Planque sécurisée** : un coffre-fort sur pieds, porte en saillie, gonds, cadran, poignée en
  croix et fente de dépôt.
- **Séchoir** : quatre montants de pin, trois ficelles et, chargé, trois rangées de bottes
  pendues tête en bas (vertes puis sèches), décalées pour qu'on voie entre elles.

Les grandes pièces n'ont pas d'uv : le jeu les cale sur leur position, et chaque poste se
dessine comme un plan (façade, flanc, dessus). Les petits objets prennent leur matière dans une
planche commune de 32×32 (`underground_parts`).

### Avec Create et NTM NEXT

Toutes ces recettes portent leur condition `neoforge:mod_loaded` : sans le mod, elles
n'existent pas. Les rapports sont ceux des postes ; l'avantage de la machine, c'est qu'elle
travaille seule, sans balance ni vapeurs.

| Machine | Ce qu'elle fait |
|---|---|
| Create — bassin chauffé (mélangeur) | 4 feuilles de cannabis → 3 herbes ; 4 feuilles de coca + 1 bidon → 3 cocaïne ; 4 capsules + 1 bidon → 3 héroïne |
| Create — bassin surchauffé | 3 bidons → 3 cristaux |
| Create — presse sur bassin | 2 bidons → 4 pilules ; 4 portions + 1 sachet vide → 1 sachet (les cinq) ; 8 sachets + 1 papier → 1 colis (les quatre) |
| Create — presse | 1 papier → 2 sachets vides |
| Create — scie | 1 plaque de fer → 1 pièce usinée (75 %) |
| Create — chaîne d'assemblage | plaque de fer, 2 × (pièce, pièce, presse) → 1 caisse scellée (objet de passage : la caisse en cours de montage) |
| NTM — usine chimique | 1 plaque d'acier + 500 mB de gaz de pétrole → 2 bidons de précurseurs |
| NTM — presse, matrice plate | 1 cristal → 2 pilules (deux unités, comme lui) |
| NTM — assembleuse | 2 plaques d'acier → 3 pièces usinées ; 6 pièces + 2 plaques → 2 caisses scellées |

Au laboratoire, les masques à gaz et les casques hazmat de NTM comptent comme masque, ses
plastrons hazmat comme combinaison (tags `dohy:lab_masks` et `dohy:lab_suits`).

La planque s'ouvre aux machines : tapis et entonnoirs de Create, convoyeurs de NTM, entonnoirs
du jeu y mettent ce que les stocks prennent (et rien d'autre), et rien n'en sort par une
machine. Chaque minute, si son propriétaire est en ligne, elle verse d'elle-même aux stocks ce
qu'elle a reçu, autant qu'il y a de place ; le journal des entreprises en fait le compte toutes
les dix minutes. Les plantes sont des cultures comme les autres : la moissonneuse de Create les
récolte et les replante.

## Lot 24d — lois, police et contrats (fait, 2026-11-23)

### Les lois

Sept sujets de loi, chacun pour une famille d'affaires illégales ; chacun est **interdit**,
**toléré** ou **légal** dans un pays.

| Sujet | Affaires | Peut être légal | Loi commune |
|---|---|---|---|
| Cannabis | plantations et laboratoires de cannabis | oui | interdit |
| Drogues dures | coca, pavot, synthèse, réseaux de rue, entrepôts | oui | interdit |
| Armes | ateliers et trafics d'armes | oui | interdit |
| Jeux d'argent | casinos | oui | toléré |
| Contrebande | routes de contrebande | non | toléré |
| Racket et recel | racket, recel | non | interdit |
| Blanchiment | sociétés écrans | non | toléré |

| Statut | Risque | Frais | Argent |
|---|---|---|---|
| Interdit | ×1 | ×1 | sale |
| Toléré | ×0,6 | ×1,15 (pots-de-vin) | sale |
| Légal | ×0,3 (de simples contrôles) | ×1 | **propre**, ×0,8 (taxes et concurrence) |

- **Chez toi** : une loi pour tous les pays que tient ta nation (`/f lois`), la loi commune tant
  que rien n'est voté ; une loi change au plus une fois par jour, d'un cran (clic gauche :
  assouplir, clic droit : durcir).
- **Ailleurs** : la loi de la nation qui tient le pays, tirée de son caractère (l'honneur la
  rend stricte, l'avidité et la ruse coulante, l'agressivité aime les armes), et le zèle de sa
  police : ×(0,8 + 0,5 × honneur), de ×0,88 à ×1,25. Hors de toute nation, la loi commune.

| Nation | Cannabis | Drogues | Armes | Jeu | Contrebande | Racket | Blanchiment | Police |
|---|---|---|---|---|---|---|---|---|
| Les Corbeaux | légal | toléré | interdit | légal | toléré | interdit | toléré | ×0,98 |
| Nordheim | toléré | interdit | toléré | toléré | interdit | interdit | interdit | ×1,13 |
| Ombre Pourpre | légal | toléré | interdit | légal | toléré | toléré | toléré | ×0,88 |
| Les Loups Gris | toléré | interdit | interdit | toléré | interdit | interdit | interdit | ×1,15 |
| Valkyr | interdit | interdit | interdit | interdit | interdit | interdit | interdit | ×1,25 |
| Kraken | légal | toléré | toléré | légal | toléré | interdit | interdit | ×1,00 |
| Solaris | interdit | interdit | interdit | toléré | interdit | interdit | interdit | ×1,23 |
| Arcanum | toléré | interdit | interdit | toléré | toléré | interdit | toléré | ×1,10 |
| Les Titans | toléré | interdit | toléré | toléré | interdit | interdit | interdit | ×1,08 |
| Fer de Lance | toléré | interdit | toléré | interdit | interdit | interdit | interdit | ×1,10 |
| Basilic | légal | toléré | interdit | toléré | toléré | toléré | toléré | ×0,93 |
| Aube Rouge | légal | toléré | légal | toléré | interdit | toléré | interdit | ×0,95 |

Ce que tes lois font autour de toi, dès que ta nation tient un pays :

- **Les nations** : chaque cran de laxisme de plus que leur propre loi fâche, +1,2 × (0,5 +
  honneur) d'humeur, 10 au plus (`Diplomacy.moodShift`).
- **Les organisations criminelles** des régions où tu tiens des pays, chaque jour : chaque
  activité tolérée −0,4 d'hostilité ; cannabis ou drogues légales +1 (leur marché disparaît),
  armes ou jeu légaux +0,5.

### La police

Un passage de la police a désormais trois gravités (`Police`) :

| Gravité | Amende (du capital de l'affaire) | Fermeture | Saisies | Gérant |
|---|---|---|---|---|
| Contrôle | 2 % (1 000 à 50 000 $) | — | — | — |
| Descente | 5 % (2 000 à 100 000 $) | 24 h | moitié des stocks (quart avec un entrepôt), 15 % de l'argent sale (250 000 $ au plus) | — |
| Coup de filet | 10 % (5 000 à 200 000 $) | 72 h | une fois et demie, 25 % de l'argent sale (400 000 $ au plus) | arrêté 3 jours |

La gravité suit le risque : coup de filet de 0 à 35 % (risque 35 à 70), contrôle de 50 à 10 %.
Une affaire légale n'a que des contrôles ; une organisation alliée de la région change le coup
de filet en descente. L'amende se paie en argent sale d'abord, la banque nationale pour le
reste.

**Le gérant arrêté** part en prison trois jours (son poste vaut `#prison:<heure>`) : il ne gère
plus rien, sa loyauté perd 5. Sa **caution** (cinq jours de salaire, depuis la banque) le fait
sortir aussitôt, loyauté +5 ; sans caution, il sort à l'heure dite, loyauté −10. En prison, ni
poste, ni gérance, ni promotion ; la paie continue.

### Les opérations

**Contre toi.** Chaque jour, une nation dont la loi interdit (ou tolère, avec moins de poids)
des affaires de ta nation dans ses pays peut annoncer une opération, une par semaine au plus,
jamais une alliée : chance 5 % × exposition (une affaire interdite compte 1, tolérée 0,4 ;
4 au plus) × (0,5 + honneur) × (0,5 + hostilité / 100), moitié moins avec un accord, trois
quarts avec un pacte, une fois et demie si rivale ou en guerre. Elle frappe **12 heures** plus
tard (24 si une organisation alliée de la région t'a prévenu) toutes les affaires visées encore
ouvertes, sans limite, plus durement (coup de filet +25 points, jamais un simple contrôle).
D'ici là, depuis la section **Menaces** de l'onglet Entreprises :

- **l'acheter** : (20 000 + 8 000 par affaire visée) × (0,6 + avidité) $, argent sale d'abord ;
  chance 25 % + 60 % × avidité − 30 % × honneur, +20 % avec une organisation alliée dans ses pays,
  moitié moins si tu l'as déclarée rivale (5 à 90 %) ; ratée, l'argent est perdu et l'opération
  frappe un quart plus fort ; impossible en guerre ;
- **se mettre au vert** : les affaires visées ferment jusqu'à six heures après l'opération, qui
  ne trouve rien.

**Les tiennes.** Un État (État officiel ou faction militaire) qui tient des pays dans la région
d'une organisation peut y mener une **opération de police** : 40 000 $ propres, une par semaine
et par organisation ; chance 30 % + 10 % par pays tenu (3 au plus) + 20 % × général + 10 % pour
l'État officiel − 25 % × agressivité (+10 % affaiblie), de 10 à 90 %. S'entendre avec elle puis
la frapper, c'est la trahir (hostilité +25 ou +40 selon sa rancune). Réussie : affaiblie 14 jours,
sa guerre s'arrête, 20 000 à 50 000 $ de biens saisis à la banque, et deux semaines durant les
nations apprécient (−3 × (0,5 + honneur) d'humeur). Ratée : hostilité +20, et une fois sur deux
elle ferme une de tes affaires de la région 12 h.

### Rivalités qui coûtent, alliances qui rapportent

- Une organisation **hostile** (50 et plus, neutre, avec tes affaires dans sa région) se sert,
  chaque jour avec une chance de (hostilité − 50) % : 15 % du plus gros stock de produits ou de
  caisses, ou 5 % de l'argent sale (20 000 $ au plus).
- Une organisation **alliée** prévient des opérations dans sa région (24 h au lieu de 12), aide
  à les acheter (+20 %), évite les coups de filet et paie mieux ses contrats.
- Une nation **alliée** ne lance jamais d'opération contre toi ; un accord en divise la chance par
  deux ; une rivale ou une guerre la multiplie par 1,5 et une rivale se laisse moins acheter.

### Les contrats

Chaque jour, une organisation qui n'est pas en guerre avec toi peut proposer un contrat (22 %,
+18 % avec un accord, −12 % au-delà de 50 d'hostilité ; un à la fois par organisation, trois
propositions au plus) : livrer 32 à 128 unités d'un produit, ou 4 à 16 caisses, selon sa
spécialité (Docks : caisses ou synthèse ; Condor : cocaïne ; Valcorte : synthèse ou cocaïne ;
Caravane : cannabis ; Lotus : caisses ou héroïne ; Récifs : n'importe quel produit).

| Étape | Délai | Effet |
|---|---|---|
| Proposé | 2 jours pour accepter | refuser ne coûte rien |
| En cours | 3 jours pour livrer | « Livrer » prend d'abord ce que tu portes (colis, sachets, caisses de ce produit, jamais plus que ce qui manque), puis les stocks |
| Rempli | — | prix de gros × 1,35 (× 1,55 alliée) en argent sale, hostilité −8 |
| Manqué | — | hostilité +12 |

Les offres sont tirées chaque jour, mais les délais se vérifient **à chaque heure** : une offre
expire et un contrat est manqué dans l'heure qui suit son échéance, et on ne peut plus accepter
une offre ni livrer passé le délai.

### Les fournisseurs, quand l'argent sale manque

Les achats aux fournisseurs (pièces d'armes faute de fer, matière première des laboratoires,
héroïne des Récifs, caisses du Lotus) se paient en argent sale d'abord. Une nation aux lois
souples ne gagne parfois plus d'argent sale (casinos et réseaux légaux, blanchiment qui vide le
solde) : sans recours, ses ateliers et ses laboratoires s'arrêteraient pour de bon, faute de quoi
payer les pièces qui relanceraient leurs ventes. La **banque nationale** prend donc le relais,
**25 % plus cher** (il faut maquiller le paiement), sans jamais passer à découvert ; la carte
« Achats aux fournisseurs » montre la part payée par la banque, comptée dans les frais.

### Les notifications

Chaque événement passe par le journal des entreprises, le chat et une notification : contrôle,
descente, coup de filet, gérant arrêté, opération annoncée, opération menée (ou qui ne trouve
rien), vol, racket, contrat proposé, contrat manqué. Les gestes (acheter, se mettre au vert,
accepter, livrer, opération de l'État, loi votée, caution) répondent dans le chat et s'inscrivent
au journal.

### L'interface

- **Lois** (`faction_laws`, `/f lois`, carte « Lois » du tableau de bord) : tes sept lois (clic
  gauche assouplir, clic droit durcir), celles des douze nations et la loi commune, ce que
  changent les lois ; la pastille dit la loi en couleur (légale en vert, tolérée en jaune,
  interdite en rouge).
- **Tableau de bord** : une section **Menaces** (pour chaque opération : l'acheter, se mettre au
  vert) en tête, une section **Contrats** (accepter, refuser, livrer).
- **Une organisation** : ses contrats, le geste **Opération de police**.
- **Une nation** (Diplomatie) : ses lois et le zèle de sa police, ce que tes lois font à son humeur.
- **Le catalogue** : la loi du pays où tu te tiens sur chaque affaire illégale, le risque estimé
  en tient compte.
- **La fiche d'une entreprise** : la loi et la police dans le détail des facteurs.
- **Organisation** : un cadre en prison (pastille PRISON) et sa caution.

### Données

`Justice`, dans `Holdings` (champ `justice`, absent des anciennes sauvegardes) : les lois votées
et leur date, les opérations (annoncées, achetées, esquivées, menées) et les contrats (proposés,
en cours, remplis, manqués, expirés), les douze derniers de chaque, la fin de l'effet de ta
dernière opération contre le crime. `Standing` garde la date de ta dernière opération contre
chaque organisation. Rien de nouveau dans `Organisation` : la prison est dans le poste du cadre.

## Lots suivants — la conception retenue

### 24e — la farmland

D'après le wiki de Paladium : une carte construite et indestructible, avec un lac, une forêt,
un coin du Nether et un coin de l'End ; PvP inactif, PvE actif ; on peut y mourir mais la faim
ne baisse pas ; les commandes de faction n'y marchent pas. Y apparaissent les créatures du
Chasseur : hostiles vanilla (zombies, squelettes, araignées, araignées venimeuses, sorcières,
creepers, slimes, piglins zombifiés, cubes de magma, ghasts, squelettes wither, endermen),
passives (vaches, champimeuh, lapins, moutons, poules, chevaux, ânes, loups, villageois) et
celles du mod qui existent ici (éléphant ; chèvres, pandas, tortues, dauphins, perroquets en
vanilla). Les animaux y vivent dix minutes s'ils ne sont pas attaqués.
