"""Deterministic 16x16 product pixels and resources; no image inputs or overlays.

Run from any directory. Only product resources and their atlas/language entries
are updated; the historical broad resource generator is not needed.
"""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/mobvats'
DATA = ROOT / 'data/mobvats'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def png(path, rows, colors):
    def chunk(kind, payload):
        return (struct.pack('>I', len(payload)) + kind + payload
                + struct.pack('>I', zlib.crc32(kind + payload)))
    pixels = b''.join(b'\0' + b''.join(bytes(colors[c]) for c in row) for row in rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                    + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b''))


# The same irregular silhouette connects the raw and browned artificial portions.
MEAT = [
    '................', '................', '......0000......', '....00122100....',
    '...0122332210...', '..012224432210..', '..012332222310..', '.0122343222310..',
    '.0122222332210..', '.0123322432210..', '..01222233210...', '..0012232210....',
    '....0011100.....', '......000.......', '................', '................',
]
GRANULES = [
    '................', '................', '.......00.......', '......0230......',
    '...00..010..00..', '..0230.....0230.', '..0110..00.0110.', '.......0230.....',
    '....00.0110.....', '..00230...00....', '.023110..0230...', '.0110.00.0110...',
    '.....0230..00...', '...000110.0210..', '..0210....0110..', '...00...........',
]
PALETTES = {
    'raw_meat': [(64, 28, 36), (100, 40, 48), (142, 60, 67), (177, 88, 89), (196, 139, 125)],
    'cooked_meat': [(49, 33, 28), (80, 48, 34), (115, 68, 42), (145, 92, 53), (174, 126, 77)],
    'fertilizer': [(45, 39, 24), (74, 62, 34), (113, 91, 49), (145, 122, 67)],
    'meat_block': [(75, 32, 40), (111, 45, 54), (145, 62, 70), (173, 87, 88), (190, 131, 116)],
    'waste_block': [(43, 41, 25), (64, 61, 33), (88, 77, 42), (107, 91, 49), (118, 111, 63)],
}

atlas_path = ROOT / 'assets/minecraft/atlases/blocks.json'
atlas = json.loads(atlas_path.read_text(encoding='utf-8'))
lang_path = ASSETS / 'lang/en_us.json'
lang = json.loads(lang_path.read_text(encoding='utf-8'))
for name, palette in PALETTES.items():
    block = name.endswith('_block')
    texture = ('blocks/' if block else 'items/') + name
    if block:
        # Tileable folded tissue / compacted clumps, with sparse pale inclusions.
        rows = [''.join(str((x // 3 + y // 2 + ((x + y * 3) % 7 == 0)) % 4)
                        if (x * 3 + y * 5) % 29 else '4' for x in range(16)) for y in range(16)]
        write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': f'mobvats:block/{name}'}}})
        write(ASSETS / f'models/block/{name}.json', {
            'parent': 'minecraft:block/cube_all', 'textures': {'all': 'mobvats:' + texture}})
        model = {'parent': f'mobvats:block/{name}'}
        write(DATA / f'loot_table/blocks/{name}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': 'mobvats:' + name}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    else:
        rows = GRANULES if name == 'fertilizer' else MEAT
        model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'mobvats:' + texture}}
    colors = {str(i): (*rgb, 255) for i, rgb in enumerate(palette)}
    colors['.'] = (0, 0, 0, 0)
    png(ASSETS / f'textures/{texture}.png', rows, colors)
    write(ASSETS / f'models/item/{name}.json', model)
    lang[('block' if block else 'item') + '.mobvats.' + name] = name.replace('_', ' ').title()
    source = {'type': 'minecraft:single', 'resource': 'mobvats:' + texture}
    if source not in atlas['sources']:
        atlas['sources'].append(source)
write(atlas_path, atlas)
write(lang_path, lang)
for kind, time in [('smelting', 200), ('smoking', 100), ('campfire_cooking', 600)]:
    write(DATA / f'recipe/cooked_meat_from_{kind}.json', {
        'type': 'minecraft:' + kind, 'category': 'food',
        'ingredient': {'item': 'mobvats:raw_meat'},
        'result': {'id': 'mobvats:cooked_meat', 'count': 1},
        'experience': 0.35, 'cookingtime': time})
