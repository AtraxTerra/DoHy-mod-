#!/usr/bin/env python3
"""Ce que coute chaque palier des technologies de la nation (chapitre 23, lot 23i, docs/BALANCE.md).

Lit data/tech/*.json et data/research_lab.json, et dit, domaine par domaine et palier par palier :

- les points, et les heures de marche qu'ils demandent : un laboratoire sans bonus (le debut),
  deux laboratoires avec cinq pays (Gouvernement III), trois laboratoires au plus fort des bonus
  (Gouvernement V, tous les bonus de vitesse, le plafond des pays) ;
- l'argent, et les heures de revenu qu'il represente au stade ou l'on cherche ce palier (les revenus
  de docs/BALANCE.md, paragraphe 3 : 25 k par heure au debut, 200 k une fois les fermes en place,
  1 M avec l'autosell) ;
- avec le jar de NTM NEXT, les pieces ramenees a leurs composants (micropuces, condensateurs,
  puces bismoides et quantiques...) et les secondes de machine qu'elles demandent (soudeuse,
  assembleuse), par les recettes par defaut de NTM : ni mode 528, ni LBSM, ni mode cher, ni
  recyclage, ni plan d'une autre famille.

Puis le total de l'arbre, et le temps de jeu qu'il demande a un joueur seul dont le laboratoire ne
manque jamais de recherche : un laboratoire jusqu'a 48 h de jeu (le Gouvernement III en demande 48),
deux jusqu'a 72 h (le V), trois ensuite.

Les domaines repris de l'ancien arbre (Gouvernement, Economie, Defense) gardent les couts de leurs
etapes (FactionTree) : ils ne sont pas comptes ici.

Usage :
    python3 -I tools/tech_balance.py [<hbm-neoforge.jar>]
"""
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI']

# Le revenu attendu au stade ou l'on cherche un palier (docs/BALANCE.md, paragraphe 3).
INCOME = {1: 25_000, 2: 25_000, 3: 25_000, 4: 200_000, 5: 1_000_000, 6: 1_000_000}

# Ce qui se ramene a ses composants ; le reste (plaques, bobines, moteurs...) se compte tel quel.
EXPAND = {'circuit_basic', 'circuit_advanced', 'circuit_controller', 'circuit_bismoid', 'circuit_quantum',
          'circuit_atomic_clock', 'circuit_controller_chassis'}
# Les composants que l'on montre ; les autres ingredients vont dans « matieres ».
SHOWN = ['circuit_chip', 'circuit_capacitor', 'circuit_capacitor_tantalium', 'circuit_chip_bismoid',
         'circuit_chip_quantum', 'circuit_pcb']
SHORT = {'circuit_chip': 'puces', 'circuit_capacitor': 'cond.', 'circuit_capacitor_tantalium': 'cond. Ta',
         'circuit_chip_bismoid': 'puces Bi', 'circuit_chip_quantum': 'puces Q', 'circuit_pcb': 'PCB'}
OFF_FLAGS = {'enable_528', 'lbsm_simple_crafting', 'expensive_mode'}


def load_domains():
    domains = []
    for path in sorted(glob.glob(os.path.join(ROOT, 'data', 'tech', '*.json'))):
        with open(path, encoding='utf-8') as handle:
            domains.append(json.load(handle))
    domains.sort(key=lambda d: d.get('order', 99))
    return domains


def load_lab():
    with open(os.path.join(ROOT, 'data', 'research_lab.json'), encoding='utf-8') as handle:
        return json.load(handle)


def bonus(domains, kind):
    """Le plus fort d'un bonus que la nation puisse avoir : dans un domaine, un palier remplace le
    precedent (Energie II, IV et VI : -10, -15 puis -25 %) ; d'un domaine a l'autre, ils s'ajoutent
    (TechPerks.of)."""
    total = 0
    for domain in domains:
        values = [entry.get('value', 0) for tier in domain['tiers'] for entry in tier.get('bonuses', [])
                  if entry.get('type') == kind]
        total += max(values) if values else 0
    return total


def best(domains, kind):
    """Le plus haut d'un bonus qui remplace le precedent (laboratoires, recherches en parallele)."""
    values = [entry.get('value', 0) for domain in domains for tier in domain['tiers']
              for entry in tier.get('bonuses', []) if entry.get('type') == kind]
    return max(values) if values else 0


# -- Les recettes de NTM NEXT ---------------------------------------------------------------------------

def default_recipes(jar):
    """Pour chaque objet a ramener a ses composants, et chaque composant montre, sa recette par defaut :
    (station, duree, sortie, entrees)."""
    found = {}
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not (name.startswith('data/hbm/recipe/') and name.endswith('.json')) or 'recycle' in name:
                continue
            try:
                recipe = json.loads(archive.read(name))
            except ValueError:
                continue
            flags = {c.get('name', '') for c in recipe.get('neoforge:conditions', []) if isinstance(c, dict)}
            if flags & OFF_FLAGS or recipe.get('blueprint_pools'):
                continue
            outputs = recipe.get('output_items') or ([recipe['result']] if recipe.get('result') else [])
            for output in outputs:
                if not isinstance(output, (str, dict)):
                    continue
                item = output if isinstance(output, str) else output.get('id', '')
                count = 1 if isinstance(output, str) else output.get('count', 1)
                item = item.split(' ')[0].replace('hbm:', '')
                if (item in EXPAND or item in SHOWN) and item not in found:
                    found[item] = (recipe.get('type', '').replace('hbm:', ''), recipe.get('duration', 0), count,
                                   ingredients(recipe))
    return found


def ingredients(recipe):
    counts = {}
    if recipe.get('type', '').endswith('crafting_shaped'):
        for row in recipe.get('pattern', []):
            for char in row:
                if char != ' ':
                    key = recipe['key'][char]
                    key = key if isinstance(key, str) else key.get('item', key.get('tag', '?'))
                    counts[key] = counts.get(key, 0) + 1
        return counts
    for field in ('input_items', 'pcb', 'solder'):
        for entry in recipe.get(field, []):
            key = entry.get('ingredient', '?')
            key = key if isinstance(key, str) else key.get('item', key.get('tag', '?'))
            counts[key] = counts.get(key, 0) + entry.get('count', 1)
    return counts


def expand(item, count, recipes, parts, seconds):
    """Ramene « count » de cet objet a ses composants ; ajoute les secondes de machine par station."""
    short = item.replace('hbm:', '')
    recipe = recipes.get(short)
    if recipe is None or short not in EXPAND:
        parts[short] = parts.get(short, 0) + count
        if recipe is not None:
            # Un composant montre se compte tel quel, mais sa machine travaille quand meme.
            station, duration, out, _ = recipe
            if duration:
                seconds[station] = seconds.get(station, 0) + -(-count // out) * duration / 20.0
        return
    station, duration, out, inputs = recipe
    crafts = -(-count // out)
    if duration:
        seconds[station] = seconds.get(station, 0) + crafts * duration / 20.0
    for ingredient, amount in inputs.items():
        expand(ingredient, crafts * amount, recipes, parts, seconds)


# -- Le rapport -------------------------------------------------------------------------------------------

def hours(points, labs, multiplier, per_hour):
    return points / (labs * per_hour * multiplier)


def clock(value):
    if value < 1:
        return f'{round(value * 60)} min'
    return f'{value:.1f} h'.replace('.', ',')


def money(value):
    if value >= 1_000_000:
        return f'{value / 1_000_000:g} M'.replace('.', ',')
    return f'{value // 1000} k'


def main(argv):
    domains = load_domains()
    lab = load_lab()
    per_hour = lab['points_per_hour']
    research = bonus(domains, 'research')
    per_country = bonus(domains, 'research_per_country')
    countries = lab['country_cap_percent'] * (100 + per_country) / 100
    top = (1 + research / 100) * (1 + countries / 100)
    labs_max = best(domains, 'labs')
    middle = 1 + 5 * lab['country_percent'] / 100
    recipes = default_recipes(argv[1]) if len(argv) > 1 else {}

    print(f'Laboratoire : {per_hour} points par heure ; au plus {labs_max} laboratoires, vitesse +{research} %, '
          f'pays +{countries:g} % : x{top:.2f} au plus fort.')
    print(f'Colonnes : 1 labo x1 ; 2 labos x{middle:.2f} (5 pays) ; {labs_max} labos x{top:.2f}.')
    print()
    total_points = 0
    total_money = 0
    for domain in domains:
        tiers = [t for t in domain['tiers'] if t.get('cost')]
        if not tiers:
            continue
        print(f"== {domain['name']}")
        for tier in tiers:
            cost = tier['cost']
            points = cost.get('points', 0)
            cash = cost.get('money', 0)
            total_points += points
            total_money += cash
            # L'Espace a un cran d'avance : son palier I coute un palier II, et se cherche au meme stade.
            stage = INCOME[tier['tier']] if domain['id'] != 'espace' else INCOME[min(6, tier['tier'] + 1)]
            line = (f"  {ROMAN[tier['tier']]:>3}  {points:>7} pts : {clock(hours(points, 1, 1, per_hour)):>7} · "
                    f"{clock(hours(points, 2, middle, per_hour)):>7} · {clock(hours(points, labs_max, top, per_hour)):>7}"
                    f"   {money(cash):>5} $ = {clock(cash / stage):>6} a {money(stage)}/h")
            pieces = ', '.join(f"{i['count']} {i['id'].split(':')[-1]}" for i in cost.get('items', []))
            print(line + '   ' + pieces)
            if recipes:
                parts = {}
                seconds = {}
                for entry in cost.get('items', []):
                    expand(entry['id'], entry['count'], recipes, parts, seconds)
                shown = ' · '.join(f'{parts[k]} {SHORT[k]}' for k in SHOWN if parts.get(k))
                machine = ' · '.join(f'{station} {clock(value / 3600)}' for station, value in sorted(seconds.items()))
                if shown or machine:
                    print(f"         composants : {shown or '-'}" + (f' ; machines : {machine}' if machine else ''))
        print()
    print(f"Tout l'arbre des domaines nouveaux : {total_points:,} points".replace(',', ' ')
          + f', {money(total_money)} $.')
    print(f'  au plus fort ({labs_max} labos x{top:.2f}) : {clock(hours(total_points, labs_max, top, per_hour))} '
          f'de marche')
    # Un joueur seul : un laboratoire jusqu'a 48 h, deux jusqu'a 72 h, trois ensuite ; les bonus montent.
    left = total_points
    played = 0.0
    for until, labs, multiplier in ((48, 1, 1.05), (72, 2, middle), (10_000, labs_max, (1 + top) / 2)):
        rate = labs * per_hour * multiplier
        span = until - played
        if left <= rate * span:
            played += left / rate
            left = 0
            break
        left -= rate * span
        played = until
    print(f'  un joueur seul (1 labo, 2 a 48 h, {labs_max} a 72 h ; bonus qui montent) : ~{played:.0f} h de jeu')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
