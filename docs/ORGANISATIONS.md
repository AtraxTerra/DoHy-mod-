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
| 24b | Entreprises : le modèle commun (propriétaire, catégorie, niveau, capital, coûts, revenus, employés, stock, lieu, risque, améliorations), les catégories légales et illégales, les revenus horaires hors ligne, l'influence par pays, les six organisations criminelles, l'onglet Entreprises | à faire |
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
- **Rompre** un pacte ou une alliance est une trahison (grief de 8 ou 12) ; un accord tombe
  sans suite. Renouveler prolonge depuis l'échéance.
- **Rivalité déclarée** : le traité tombe, la nation s'en fâche (grief), son humeur monte de
  12 ; ses pays rapportent un quart de points de conquête en plus.
- **Dans l'IA de guerre** (`WarStrategy`) : la diplomatie s'ajoute à l'humeur (`mood`), un pacte
  ou une alliance empêche l'ultimatum et la guerre (`think`) et retire la nation des
  coalitions (`coalition`).

### L'interface

« Ma nation » garde huit onglets : Vue d'ensemble, Territoire, **Organisation** (régime, postes,
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

## Lots suivants — la conception retenue

### 24b — entreprises

- **Le modèle commun** : propriétaire (ta nation), nom, catégorie, légale ou non, niveau 1 à 5,
  capital investi, coûts de fonctionnement par heure (loyer, employés, entretien), revenus par
  heure, employés PNJ (nombre, salaire), lieutenant à sa tête (un cadre), stock, localisation
  (le pays et le chunk de son siège), risque de 0 à 100, améliorations.
- **Catégories légales** : ferme, mine et carrière, usine, commerce, banque, transport.
- **Catégories illégales**, chacune avec sa chaîne abstraite : stupéfiants (plantation,
  laboratoire, entrepôt, réseau de distribution), armement (usine clandestine, commerce,
  contrebande), blanchiment (entreprise de façade), contrebande et trafic, casino clandestin,
  racket, recel.
- **Revenus horaires** comme les mines (une semaine au plus hors ligne) : niveau × catégorie ×
  régime × influence dans le pays × conditions (saison, catastrophe, guerre) − coûts.
- **Influence par pays** : pays tenus, claims, entreprises ; elle change les revenus et le risque.
- **Six organisations criminelles** PNJ, chacune avec ses régions et sa spécialité ; humeur,
  accords d'approvisionnement, alliances, guerres de territoire qui coûtent des revenus.

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
