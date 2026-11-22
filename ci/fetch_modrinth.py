"""Temporaire, pour l'essai : telecharge depuis Modrinth des jars par leur nom de fichier exact.

Usage : python3 -I ci/fetch_modrinth.py <dossier> <requete>=<fichier.jar> [...]
"""
import json
import pathlib
import sys
import time
import urllib.parse
import urllib.request

API = 'https://api.modrinth.com/v2'
UA = {'User-Agent': 'AtraxTerra/DoHy-mod- CI (essai JEI)'}


def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as response:
                return json.load(response)
        except Exception:
            if attempt == 3:
                raise
            time.sleep(3 + attempt * 3)


def find(query, filename):
    hits = get(f'{API}/search?' + urllib.parse.urlencode({'query': query, 'limit': 10})).get('hits', [])
    for hit in hits:
        versions = get(f'{API}/project/{hit["project_id"]}/version?'
                       + urllib.parse.urlencode({'loaders': json.dumps(['neoforge'])}))
        for version in versions:
            for item in version['files']:
                if item['filename'] == filename:
                    return hit['slug'], item['url'], item['size']
    return None


def main():
    target = pathlib.Path(sys.argv[1])
    target.mkdir(parents=True, exist_ok=True)
    missing = 0
    for pair in sys.argv[2:]:
        query, filename = pair.split('=', 1)
        found = find(query, filename)
        if found is None:
            print('introuvable sur Modrinth :', filename)
            missing += 1
            continue
        slug, url, size = found
        request = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(request, timeout=120) as response:
            (target / filename).write_bytes(response.read())
        print(f'{filename} ({slug}, {size} octets)')
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main())
