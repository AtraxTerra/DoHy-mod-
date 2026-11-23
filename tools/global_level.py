#!/usr/bin/env python3
"""Le niveau global : ce que donne chaque jalon, et le rythme qu'il prend (docs/BALANCE.md, 6 novovicies).

Lit data/contracts.json, data/collections.json, data/job_progression.json, data/museum.json et
data/rates.csv, et dit :

- le bareme : l'XP de chaque type de jalon, et ce qu'il en reste au plus ;
- le rythme d'une saison de seize semaines, source par source, pour un joueur seul a 20 h par
  semaine (docs/BALANCE.md, paragraphe 3) : tous les contrats du jour et de la semaine, la commande
  de trixium chaque jour, les quatre metiers a parts egales (15 000 XP de metier par heure, le
  calibrage de job_progression.json), les 37 pieces du musee donnees au fil de la saison ;
- les collections, faute de mieux, par un modele grossier : chaque heure de jeu fait avancer trois
  objets a leur rythme du catalogue (la pierre et deux minerais d'une heure de mine), sur un
  roulement de quarante objets tires du catalogue a proportion de ses tiers, hors blocs de masse ;
  c'est un ordre de grandeur, pas une prevision ;
- et la corne du Dancarok sur un bloc de masse (100 000 blocs par heure, la borne haute du
  paragraphe 1 de BALANCE.md).

L'ancien bareme (la queue des paliers jusqu'a 9 390, les blocs de masse comptes) est rappele a
cote, pour mesurer ce que le nouveau corrige.

Usage :
    python3 -I tools/global_level.py
"""
import csv
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

XP_PER_LEVEL = 500            # ContractApi.GLOBAL_XP_PER_LEVEL
REWARD_PER_LEVEL = 0.35       # ContractApi.REWARD_PER_GLOBAL_LEVEL
JOB_XP_PER_LEVEL = 20         # JobApi : 20 XP globale par niveau de metier
HOURS_PER_WEEK = 20           # docs/BALANCE.md, paragraphe 3
WEEKS = 16                    # une saison de quatre mois
JOB_XP_PER_HOUR = 15_000      # calibrage de job_progression.json
ITEMS_PER_HOUR = 3            # objets qui avancent ensemble pendant une heure de jeu
ROTATION = 40                 # objets travailles au fil de la saison
HORN_PER_HOUR = 100_000       # la corne sur un bloc de masse, borne haute

OLD_TAIL = [170, 230, 310, 410, 540, 720, 960, 1270, 1700, 2260, 3000, 3990, 5310, 7060, 9390]


def load(name):
    with open(os.path.join(ROOT, 'data', name), encoding='utf-8') as handle:
        return json.load(handle)


def job_level(job_xp, curve):
    """Le niveau de metier atteint avec cette XP (xp_base * niveau ^ xp_exponent par niveau)."""
    level = 0
    spent = 0.0
    while level < curve['max_level']:
        cost = curve['xp_base'] * (level + 1) ** curve['xp_exponent']
        if spent + cost > job_xp:
            break
        spent += cost
        level += 1
    return level


def tier_xp(count, tiers, rewards):
    return sum(rewards[index] for index, threshold in enumerate(tiers) if count >= threshold)


def main():
    contracts = load('contracts.json')
    collections = load('collections.json')
    curve = load('job_progression.json')['curve']
    museum = load('museum.json')
    rewards = [entry['global_xp'] for entry in collections['rewards_per_tier']]
    old_rewards = rewards[:10] + OLD_TAIL
    prestige = collections['prestige']
    exceptions = collections['exceptions']
    mass = sorted(item for item, rule in exceptions.items() if rule.get('global_xp_disabled'))
    pieces = sum(len(wing.get('items', [])) for wing in museum['wings'])
    daily = contracts['daily']
    weekly = contracts['weekly']
    trixium = contracts['trixium_order']

    print('== Le bareme (un niveau global = %d XP ; +%d %% de recompense de contrat par niveau)'
          % (XP_PER_LEVEL, round(REWARD_PER_LEVEL * 100)))
    print('  contrat du jour          %5d XP  (%d par jour)' % (daily['global_xp'], daily['count']))
    print('  contrat de la semaine    %5d XP' % weekly['global_xp'])
    print('  commande de trixium      %5d XP  (une par jour)' % trixium['global_xp'])
    print('  niveau de metier         %5d XP  (4 metiers x %d niveaux : %d XP au plus)'
          % (JOB_XP_PER_LEVEL, curve['max_level'], 4 * curve['max_level'] * JOB_XP_PER_LEVEL))
    print('  don au musee             %5d XP  (%d pieces : %d XP au plus)'
          % (museum['global_xp_per_donation'], pieces, pieces * museum['global_xp_per_donation']))
    print('  palier de collection     %d a %d XP (tete %s, queue %d a %d)'
          % (rewards[0], rewards[-1], '-'.join(str(r) for r in rewards[:10]), rewards[10], rewards[-1]))
    print('                           un objet mene au bout : %d XP (avant : %d)' % (sum(rewards), sum(old_rewards)))
    print('  prestige de collection   %5d XP  (%d au plus par objet)' % (prestige['global_xp'], prestige['max_level']))
    print('  sans XP globale          %d blocs de masse : %s' % (len(mass), ', '.join(m.split(':')[1] for m in mass)))

    rates = []
    with open(os.path.join(ROOT, 'data', 'rates.csv'), encoding='utf-8') as handle:
        for row in csv.DictReader(handle):
            item = row['item_id'].strip()
            if item in mass:
                continue
            rates.append((item, row['tier'].strip(), float(row['rate_per_hour'])))
    curves = collections['curves']
    found = curves['found']['tiers']
    farmable = curves['farmable']['tiers']
    # Le roulement : des objets du catalogue a proportion de ses tiers, au rythme median de chacun.
    by_tier = {}
    for item, tier, rate in rates:
        by_tier.setdefault(tier, []).append(rate)
    rotation = []
    for tier, values in sorted(by_tier.items()):
        values.sort()
        share = round(ROTATION * len(values) / len(rates))
        for index in range(share):
            rotation.append((tier, values[(index * len(values)) // max(1, share)]))

    print()
    print('== Une saison de %d semaines a %d h par semaine (XP globale cumulee, niveau)' % (WEEKS, HOURS_PER_WEEK))
    print('  semaine  contrats  metiers  musee  collections   total  niveau  recompense des contrats')
    for week in (1, 2, 4, 8, 12, 16):
        days = 7 * week
        contract_xp = days * daily['count'] * daily['global_xp'] + week * weekly['global_xp'] \
            + days * trixium['global_xp']
        hours = HOURS_PER_WEEK * week
        job_xp = 4 * JOB_XP_PER_LEVEL * job_level(hours / 4 * JOB_XP_PER_HOUR, curve)
        museum_xp = min(pieces, round(pieces * week / WEEKS)) * museum['global_xp_per_donation']
        item_hours = hours * ITEMS_PER_HOUR / max(1, len(rotation))
        collection_xp = 0
        deepest = -1
        for tier, rate in rotation:
            tiers = found if tier in ('FOUND', 'RARE') else farmable
            count = rate * item_hours
            collection_xp += tier_xp(count, tiers, rewards)
            deepest = max(deepest, sum(1 for threshold in tiers if count >= threshold) - 1)
        total = contract_xp + job_xp + museum_xp + collection_xp
        level = total // XP_PER_LEVEL
        print('  %7d  %8d  %7d  %5d  %11d  %6d  %6d  x%.2f  (palier le plus haut : %d)'
              % (week, contract_xp, job_xp, museum_xp, collection_xp, total, level,
                 1 + REWARD_PER_LEVEL * level, deepest))
    print('  Le jeu ordinaire ne touche pas la queue des paliers (au-dela du dixieme) en une saison :'
          ' le bareme de la queue ne change rien a ce rythme, il ferme les fontaines.')

    print()
    print('== La corne du Dancarok sur un bloc de masse (%d blocs par heure)' % HORN_PER_HOUR)
    for hours in (1, 3):
        count = HORN_PER_HOUR * hours
        old = tier_xp(count, found, old_rewards)
        new = 0
        print('  %d h : %d blocs, ancien bareme %d XP (%d niveaux), bareme actuel %d XP'
              % (hours, count, old, old // XP_PER_LEVEL, new))
    print('  et chaque prestige (300 000 blocs) rendait %d XP de plus, la collection repartant a zero'
          % prestige['global_xp'])


if __name__ == '__main__':
    main()
