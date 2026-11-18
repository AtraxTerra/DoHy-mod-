"""Temporaire, pour l'essai : le Flywheel embarque de Create Fly prend l'espace de noms
de ressources create_flywheel au lieu de flywheel, celui de CrankShaft.

Usage : python3 -I ci/relocate_flywheel.py <clone de Mod-ported, branche port/neoforge-26.2>
"""
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
assets = root / 'src/main/resources/assets'
(assets / 'flywheel').rename(assets / 'create_flywheel')

shaders = 0
refs = 0
for path in sorted(assets.rglob('*')):
    if path.is_file() and path.suffix in ('.glsl', '.vert', '.frag', '.comp'):
        text = path.read_text(encoding='utf-8')
        new, count = re.subn(r'(?<![A-Za-z0-9_])flywheel:', 'create_flywheel:', text)
        if count:
            path.write_text(new, encoding='utf-8')
            shaders += 1
            refs += count
print('shaders modifies :', shaders, '; references :', refs)

java = root / 'src/main/java/com/zurrtum/create/client'
edits = {
    'flywheel/impl/Flywheel.java': [('MOD_ID = "flywheel"', 'MOD_ID = "create_flywheel"')],
    'flywheel/backend/compile/FlwProgramsReloader.java': [('super("flywheel")', 'super("create_flywheel")')],
    'mixin/ClientPackSourceMixin.java': [('"flywheel",', '"create_flywheel",')],
    'flywheel/impl/BackendArgument.java': [('"flywheel:off"', '"create_flywheel:off"')],
}
for name, pairs in edits.items():
    path = java / name
    text = path.read_text(encoding='utf-8')
    for old, new in pairs:
        if text.count(old) != 1:
            raise SystemExit('%s : %r trouve %d fois' % (name, old, text.count(old)))
        text = text.replace(old, new)
    path.write_text(text, encoding='utf-8')
    print('modifie :', name)

left = []
for path in sorted(java.rglob('*.java')):
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if re.search(r'"flywheel:|fromNamespaceAndPath\("flywheel"', line):
            left.append('%s:%d: %s' % (path.relative_to(root), number, line.strip()))
print('restes :', len(left))
for line in left:
    print('  ', line)
