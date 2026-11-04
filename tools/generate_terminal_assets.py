#!/usr/bin/env python3
"""Generate Economy's original terminal art and data (no third-party image assets).

All pixel layouts and models are authored here for this project. The generated
textures are deterministic 16 px artwork; rerun from any working directory.
Wide-machine models face north; CELL 0/1 is bottom left/right and 2/3 top
left/right as viewed from their front. Java owns wide-machine teardown drops.
"""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/economy'
DATA = ROOT / 'data/economy'
COLORS = {
    'white': (222, 227, 228), 'orange': (215, 113, 49), 'magenta': (175, 76, 170),
    'light_blue': (91, 165, 198), 'yellow': (224, 190, 62), 'lime': (126, 176, 63),
    'pink': (213, 137, 164), 'gray': (77, 86, 92), 'light_gray': (159, 167, 169),
    'cyan': (49, 139, 146), 'purple': (118, 75, 165), 'blue': (62, 91, 172),
    'brown': (130, 91, 65), 'green': (75, 119, 67), 'red': (180, 67, 67),
    'black': (43, 50, 57),
}
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
DARK = (27, 39, 48)
STEEL = (130, 149, 156)
MINT = (100, 222, 188)


def output(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    def chunk(kind, content):
        return struct.pack('>I', len(content)) + kind + content + struct.pack('>I', zlib.crc32(kind + content))
    raw = b''.join(b'\x00' + bytes(v for pixel in row for v in pixel) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def shade(color, n):
    return tuple(max(0, min(255, channel + n)) for channel in color)


def rect(pixels, x1, y1, x2, y2, color):
    for y in range(y1, y2):
        for x in range(x1, x2):
            pixels[y][x] = color


def panel(color, size=16):
    p = [[shade(color, ((x * 13 + y * 7) % 5) - 2) for x in range(size)] for y in range(size)]
    rect(p, 0, 0, size, 1, shade(color, 28))
    rect(p, 0, 0, 1, size, shade(color, 18))
    rect(p, 0, size - 1, size, size, shade(color, -30))
    rect(p, size - 1, 0, size, size, shade(color, -25))
    return p


def machine_front(color, teller=False):
    p = panel(color, 32)
    # Recessed display bezel, glass highlights, menu indicators and bar graph.
    rect(p, 4, 3, 28, 16, DARK)
    rect(p, 5, 4, 27, 15, (35, 80, 88) if not teller else (39, 84, 65))
    rect(p, 6, 5, 26, 6, (80, 139, 143))
    rect(p, 7, 8, 16, 9, MINT)
    rect(p, 7, 10, 13, 11, MINT)
    rect(p, 7, 12, 17, 13, (73, 161, 153))
    for x, height in [(20, 3), (22, 5), (24, 7)]:
        rect(p, x, 13 - height, x + 1, 13, MINT)
    for y in (7, 10, 13):
        rect(p, 2, y, 3, y + 1, STEEL)
        rect(p, 29, y, 30, y + 1, STEEL)
    # Separate payout and deposit slots, keypad, secure lower door and vents.
    rect(p, 4, 18, 17, 22, shade(color, -40))
    rect(p, 5, 19, 16, 20, DARK)
    rect(p, 5, 21, 16, 22, STEEL)
    rect(p, 5, 24, 15, 25, DARK)
    rect(p, 5, 25, 15, 26, shade(color, 24))
    rect(p, 20, 18, 28, 27, DARK)
    for y in range(19, 25, 2):
        for x in range(21, 27, 2):
            rect(p, x, y, x + 1, y + 1, STEEL)
    rect(p, 21, 25, 23, 26, (187, 91, 78))
    rect(p, 25, 25, 27, 26, MINT)
    rect(p, 3, 28, 29, 29, shade(color, -35))
    for x in range(7, 26, 3):
        rect(p, x, 30, x + 1, 31, DARK)
    if teller:
        rect(p, 2, 16, 30, 17, (190, 162, 83))
        rect(p, 6, 23, 14, 24, (190, 162, 83))
        rect(p, 17, 19, 18, 21, (190, 162, 83))
    return p


def register_front(color):
    p = panel(color)
    rect(p, 2, 3, 14, 7, DARK)
    rect(p, 3, 4, 13, 6, (41, 87, 87))
    rect(p, 4, 4, 10, 5, MINT)
    rect(p, 2, 9, 14, 14, shade(color, -25))
    rect(p, 3, 10, 13, 11, DARK)
    rect(p, 7, 12, 9, 13, STEEL)
    return p


def register_top(color):
    p = panel(color)
    rect(p, 1, 1, 15, 8, DARK)
    rect(p, 2, 2, 14, 7, (37, 83, 87))
    rect(p, 3, 3, 11, 4, MINT)
    rect(p, 3, 5, 8, 6, MINT)
    for y in (10, 12, 14):
        for x in (3, 5, 7, 9):
            rect(p, x, y, x + 1, y + 1, STEEL)
    rect(p, 12, 10, 14, 12, MINT)
    rect(p, 12, 13, 14, 15, (187, 91, 78))
    return p


def cube(start, end, front='#front', uv=None):
    faces = {side: {'texture': '#body'} for side in ('up', 'down', 'east', 'west', 'south')}
    faces['north'] = {'texture': front}
    if uv:
        faces['north']['uv'] = uv
    return {'from': start, 'to': end, 'faces': faces}


DISPLAY = {
    'gui': {'rotation': [25, 225, 0], 'translation': [0, 0, 0], 'scale': [0.75, 0.75, 0.75]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
}


def textures(name, color, wide=False, teller=False):
    png(ASSETS / f'textures/block/{name}_body.png', panel(color))
    if wide:
        front = machine_front(color, teller)
        # The four front quadrants use conventional visual left/right ordering.
        for cell, (x, y) in enumerate(((0, 16), (16, 16), (0, 0), (16, 0))):
            png(ASSETS / f'textures/block/{name}_front_{cell}.png', [row[x:x + 16] for row in front[y:y + 16]])
    else:
        png(ASSETS / f'textures/block/{name}_front.png', register_front(color))
        png(ASSETS / f'textures/block/{name}_top.png', register_top(color))


def model(name, wide=False):
    body = f'economy:block/{name}_body'
    variants = {}
    if wide:
        for cell in range(4):
            output(ASSETS / f'models/block/{name}_{cell}.json', {
                'textures': {'body': body, 'front': f'economy:block/{name}_front_{cell}', 'particle': body},
                'elements': [cube([0, 0, 3], [16, 16, 15])],
            })
            for facing, rotation in FACINGS.items():
                variants[f'cell={cell},facing={facing}'] = {'model': f'economy:block/{name}_{cell}', 'y': rotation}
        elements = []
        texture_map = {'body': body, 'particle': body}
        for cell, (x, y) in enumerate(((8, 0), (0, 0), (8, 8), (0, 8))):
            texture_map[f'front{cell}'] = f'economy:block/{name}_front_{cell}'
            elements.append(cube([x, y, 4], [x + 8, y + 8, 12], f'#front{cell}', [0, 0, 16, 16]))
        output(ASSETS / f'models/block/{name}_inventory.json', {'textures': texture_map, 'elements': elements, 'display': DISPLAY})
        output(ASSETS / f'models/item/{name}.json', {'parent': f'economy:block/{name}_inventory'})
        loot = {'type': 'minecraft:block', 'pools': []}
    else:
        base = cube([1, 0, 2], [15, 7, 14])
        base['faces']['up'] = {'texture': '#top', 'uv': [0, 8, 16, 16]}
        screen = cube([1, 7, 7], [15, 12, 14])
        screen['faces']['north'] = {'texture': '#top', 'uv': [0, 0, 16, 8]}
        screen['faces']['up'] = {'texture': '#body'}
        output(ASSETS / f'models/block/{name}.json', {
            'textures': {'body': body, 'front': f'economy:block/{name}_front', 'top': f'economy:block/{name}_top', 'particle': body},
            'elements': [base, screen], 'display': DISPLAY,
        })
        for facing, rotation in FACINGS.items():
            variants[f'facing={facing}'] = {'model': f'economy:block/{name}', 'y': rotation}
        output(ASSETS / f'models/item/{name}.json', {'parent': f'economy:block/{name}'})
        loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'economy:{name}'}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    output(ASSETS / f'blockstates/{name}.json', {'variants': variants})
    output(DATA / f'loot_table/blocks/{name}.json', loot)


def recipe(name, pattern, keys):
    output(DATA / f'recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'redstone',
        'pattern': pattern, 'key': {letter: {'item': f'minecraft:{item}'} for letter, item in keys.items()},
        'result': {'id': f'economy:{name}', 'count': 1}})


def main():
    atms = ['atm'] + [f'{color}_atm' for color in COLORS]
    registers = ['register'] + [f'{color}_register' for color in COLORS]
    for family, names in [('atm', atms), ('register', registers)]:
        for name in names:
            color_name = name.removesuffix('_' + family) if name != family else None
            textures(name, COLORS[color_name] if color_name else (99, 116, 128), wide=family == 'atm')
            model(name, wide=family == 'atm')
        output(DATA / f'tags/item/{family}s.json', {'replace': False, 'values': [f'economy:{name}' for name in names]})
        output(DATA / f'tags/block/{family}s.json', {'replace': False, 'values': [f'economy:{name}' for name in names]})
        for color in COLORS:
            name = f'{color}_{family}'
            output(DATA / f'recipe/{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'redstone',
                'ingredients': [{'tag': f'economy:{family}s'}, {'item': f'minecraft:{color}_dye'}],
                'result': {'id': f'economy:{name}', 'count': 1}})
    textures('teller', (113, 139, 125), wide=True, teller=True)
    model('teller', wide=True)
    recipe('atm', ['IGI', 'RCR', 'III'], {'I': 'iron_ingot', 'G': 'glass_pane', 'R': 'redstone', 'C': 'chest'})
    recipe('register', [' G ', 'IRI', ' C '], {'I': 'iron_ingot', 'G': 'glass_pane', 'R': 'redstone', 'C': 'chest'})
    recipe('teller', ['IGI', 'RCR', 'IHI'], {'I': 'iron_ingot', 'G': 'glass_pane', 'R': 'redstone', 'C': 'chest', 'H': 'hopper'})
    names = atms + registers + ['teller']
    output(DATA / 'tags/item/terminals.json', {'replace': False, 'values': [f'economy:{name}' for name in names]})
    output(DATA / 'tags/block/terminals.json', {'replace': False, 'values': [f'economy:{name}' for name in names]})
    output(ROOT / 'data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': [f'economy:{name}' for name in names]})
    validate(names)
    print(f'Generated and validated {len(names)} terminal variants, 35 recipes, original textures and models.')


def validate(names):
    for folder in (ASSETS / 'blockstates', ASSETS / 'models/block', ASSETS / 'models/item'):
        for path in folder.glob('*.json'):
            if not any(path.stem == name or path.stem.startswith(name + '_') for name in names):
                continue
            value = json.loads(path.read_text())
            for match in __import__('re').findall(r'"(?:parent|model)": "economy:([^\"]+)"', json.dumps(value)):
                assert (ASSETS / 'models' / (match + '.json')).exists(), (path, match)
            for key, texture in value.get('textures', {}).items():
                if not texture.startswith('#'):
                    assert texture.startswith('economy:'), (path, texture)
                    assert (ASSETS / 'textures' / (texture.split(':')[1] + '.png')).exists(), (path, texture)
    for name in names:
        assert (ASSETS / f'blockstates/{name}.json').exists()
        assert (ASSETS / f'models/item/{name}.json').exists()
        assert (DATA / f'loot_table/blocks/{name}.json').exists()
        assert (DATA / f'recipe/{name}.json').exists()


if __name__ == '__main__':
    main()
