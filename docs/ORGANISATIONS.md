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
| 24c | Objets, blocs et textures : plantes, produits, conditionnement, caisses, balance, matériel de laboratoire, protections, bidons, laboratoire clandestin, table de conditionnement, coffres sécurisés — chacun branché sur une entreprise | à faire |
| 24d | Interactions : lois des pays, descentes de police, opérations contre le crime, rivalités qui coûtent, alliances qui rapportent, contrats entre organisations, notifications | à faire |
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
| La Confrérie du Lotus | Asie | vend des caisses (550 $, −15 % alliée), rachète le surplus |
| Le Cartel des Récifs | Océanie | vend matière première et produits (−15 % alliée), rachète le surplus |

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

## Lots suivants — la conception retenue

### 24c — objets et textures

Textures 16×16 originales, à la palette du mod. Chaque objet sert : les plantes se cultivent,
les produits se transforment dans des machines (laboratoire, table de conditionnement,
presse), se stockent dans des caisses et des coffres sécurisés, se vendent par le réseau de
distribution ou au receleur. La fabrication reste **abstraite** : des étapes de jeu (récolter,
traiter, conditionner) sans procédé réel.

### 24d — interactions

Lois : dans un pays que tient ta nation, chaque activité est légale, tolérée ou interdite ;
ailleurs, la loi de la nation qui le tient. Descentes de police selon le risque (amende,
saisie, fermeture, cadre arrêté). Un État peut lancer des opérations contre les organisations
criminelles de ses pays. Contrats entre organisations (livrer tant de produit avant une date).
Notifications (toasts et journal) pour chaque événement.

### 24e — la farmland

D'après le wiki de Paladium : une carte construite et indestructible, avec un lac, une forêt,
un coin du Nether et un coin de l'End ; PvP inactif, PvE actif ; on peut y mourir mais la faim
ne baisse pas ; les commandes de faction n'y marchent pas. Y apparaissent les créatures du
Chasseur : hostiles vanilla (zombies, squelettes, araignées, araignées venimeuses, sorcières,
creepers, slimes, piglins zombifiés, cubes de magma, ghasts, squelettes wither, endermen),
passives (vaches, champimeuh, lapins, moutons, poules, chevaux, ânes, loups, villageois) et
celles du mod qui existent ici (éléphant ; chèvres, pandas, tortues, dauphins, perroquets en
vanilla). Les animaux y vivent dix minutes s'ils ne sont pas attaqués.
