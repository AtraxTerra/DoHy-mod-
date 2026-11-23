"""Resume factuel de donnees de Minecraft 26.2, lu dans son jar (CI, temporaire).

Ne recopie aucun fichier : pour chaque echange de villageois, ce qu'il demande et ce qu'il
donne ; pour quelques tags, leurs membres ; pour le soufre, les chemins et les sources.
"""
import json
import sys
import zipfile

jar = sys.argv[1]
out = []


def leaves(node, path=''):
    if isinstance(node, dict):
        for key, value in node.items():
            yield from leaves(value, f'{path}.{key}' if path else key)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from leaves(value, f'{path}[{index}]')
    else:
        yield path, node


with zipfile.ZipFile(jar) as archive:
    names = archive.namelist()
    out.append(f'jar {jar} : {len(names)} entrees')
    for folder in ('data/minecraft/villager_trade/', 'data/minecraft/trade_set/'):
        files = sorted(n for n in names if n.startswith(folder) and n.endswith('.json'))
        out.append(f'\n== {folder} : {len(files)} fichiers')
        for name in files:
            try:
                data = json.loads(archive.read(name))
            except ValueError:
                out.append(f'{name} : illisible')
                continue
            facts = []
            for path, value in leaves(data):
                last = path.split('.')[-1].split('[')[0]
                if last in ('id', 'item', 'items', 'count', 'min', 'max', 'tag', 'type', 'value', 'chance',
                            'max_uses', 'xp', 'price_multiplier', 'trades', 'level') \
                        or (isinstance(value, str) and value.startswith(('minecraft:', '#minecraft:'))):
                    facts.append(f'{path}={value}')
            out.append(name[len(folder):] + ' : ' + ' ; '.join(facts))
    out.append('\n== tags de blocs')
    for name in sorted(n for n in names if n.startswith('data/minecraft/tags/block/') and n.endswith('.json')):
        short = name[len('data/minecraft/tags/block/'):-5]
        if any(word in short for word in ('dirt', 'grass', 'moss', 'soil', 'substrate', 'sulfur', 'geyser', 'mud',
                                          'sand', 'farmland', 'supports', 'replaceable', 'overworld')):
            values = json.loads(archive.read(name)).get('values', [])
            out.append(f'{short} : ' + ', '.join(v if isinstance(v, str) else v.get('id', str(v)) for v in values))
    out.append('\n== soufre et geysers : chemins')
    for name in names:
        if ('sulfur' in name or 'geyser' in name) and not name.endswith('.png'):
            out.append(name)
    out.append('\n== qui donne du soufre puissant (butin, recettes)')
    for name in names:
        if name.startswith(('data/minecraft/loot_table/', 'data/minecraft/recipe/')) and name.endswith('.json'):
            text = archive.read(name).decode('utf-8', 'replace')
            if 'potent_sulfur' in text:
                data = json.loads(text)
                facts = [f'{p}={v}' for p, v in leaves(data)
                         if isinstance(v, (int, float, str)) and (not isinstance(v, str) or 'minecraft:' in v
                                                                   or p.endswith(('type', 'count', 'min', 'max')))]
                out.append(name + ' : ' + ' ; '.join(facts[:40]))
print('\n'.join(out))
