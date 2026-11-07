#!/usr/bin/env python3
"""Generate A7-03's original wide kiosk console and explicit legacy blockstates.

Only new wide model/header paths, kiosk item parents and its blockstate are written.
Existing narrow models and existing raster surfaces are never changed. Standard
library plus the project's original CC0 pixel primitives; no external art/fonts.
"""
import copy
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/economy'
SOURCE = ROOT / 'tools/banking-assets/sources/hardware/wide-kiosk'
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / 'tools/banking-assets'))
from art import BANKS, Canvas, INK, PAPER, contrast, mark, shade

FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
# Match existing world geometry: NORTH cell1 is WEST of its anchor. The model's
# full console therefore has cell0 on the right, then cell1 on the left.
CELL_ORIGINS = ((16, 0), (0, 0), (16, 16), (0, 16))
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
# Full console coordinates (32 wide x32 high); all Z extents remain inside1..15.
# North front: substantial side cheeks, recessed foot and solid rear assembly,
# waist-height projecting counter, continuous display and institution header.
ELEMENTS = [
    ([2, 0, 5], [30, 2, 14], 'metal'),
    ([3, 2, 7], [29, 12, 14], 'shell'),
    ([2, 2, 11], [30, 30, 15], 'shell'),
    ([0, 2, 4], [2, 30, 15], 'trim'),
    ([30, 2, 4], [32, 30, 15], 'trim'),
    ([2, 3, 6], [3, 12, 14], 'dark'),
    ([29, 3, 6], [30, 12, 14], 'dark'),
    ([1, 12, 1], [31, 14, 14], 'metal'),
    ([1, 14, 2], [31, 16, 13], 'trim'),
    ([3, 16, 7], [29, 18, 12], 'dark'),
    ([2, 18, 5], [30, 27, 11], 'dark'),
    ([3, 19, 4.8], [29, 26, 5], 'screen'),
    ([2, 27, 4], [30, 32, 11], 'trim'),
    ([3, 27.5, 3.8], [29, 31.5, 4], 'header'),
    ([5, 14, 2.8], [13, 14.2, 7], 'keypad'),
    ([21, 3, 6.8], [28, 10, 7], 'vent'),
]
DISPLAY = {
    'gui': {'rotation': [25, 225, 0], 'translation': [0, 0, 0], 'scale': [0.78, 0.78, 0.78]},
    'ground': {'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
}


def output(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def header(bank=None):
    background = bank['primary'] if bank else '#263B50'
    accent = bank['accent'] if bank else '#77C9B1'
    c = Canvas(64, 16, background)
    c.rect(0, 0, 64, 1, accent)
    c.rect(0, 15, 64, 16, shade(background, -30))
    if bank:
        c.blit(mark(bank['key'], accent), 1, 2, 12, 12)
        fg = contrast(background)
        for i, line in enumerate(bank['label']):
            assert len(line) <= 8, (bank['key'], line)
            c.text(line, 16, 2 + i * 7, fg)
        c.rect(49, 2, 50, 14, accent)
        c.text(bank['currency'], 51, 9, fg)
    else:
        c.text('ECONOMY', 5, 2, PAPER)
        c.text('KIOSK', 5, 9, PAPER)
        c.text('INFO', 43, 9, accent)
    return c


def clipped_box(start, end, texture, cell=None):
    """Clip a global cuboid to one cell and preserve continuous front/back UVs."""
    low, high = list(start), list(end)
    dx = dy = 0
    if cell is not None:
        dx, dy = CELL_ORIGINS[cell]
        low[0], high[0] = max(low[0], dx), min(high[0], dx + 16)
        low[1], high[1] = max(low[1], dy), min(high[1], dy + 16)
        if any(low[i] >= high[i] for i in range(3)):
            return None
    width, height, depth = [end[i] - start[i] for i in range(3)]
    u = [(low[0] - start[0]) / width * 16, (high[0] - start[0]) / width * 16]
    v = [(end[1] - high[1]) / height * 16, (end[1] - low[1]) / height * 16]
    z = [(low[2] - start[2]) / depth * 16, (high[2] - start[2]) / depth * 16]
    faces = {}
    for side in SIDES:
        # Do not emit artificial internal cut faces between adjacent cell models.
        axis, endpoint = {'west': (0, 0), 'east': (0, 1), 'down': (1, 0),
                          'up': (1, 1), 'north': (2, 0), 'south': (2, 1)}[side]
        if (low if endpoint == 0 else high)[axis] != (start if endpoint == 0 else end)[axis]:
            continue
        if side == 'north': uv = [16 - u[1], v[0], 16 - u[0], v[1]]
        elif side == 'south': uv = [u[0], v[0], u[1], v[1]]
        elif side == 'east': uv = [16 - z[1], v[0], 16 - z[0], v[1]]
        elif side == 'west': uv = [z[0], v[0], z[1], v[1]]
        else: uv = [u[0], z[0], u[1], z[1]]
        faces[side] = {'texture': '#' + texture, 'uv': uv}
    return {'from': [low[0] - dx, low[1] - dy, low[2]],
            'to': [high[0] - dx, high[1] - dy, high[2]], 'faces': faces}


def material_map(bank=None):
    old = ASSETS / ('models/block/banking/' + bank['key'] + '_kiosk_0.json' if bank else 'models/block/branch_kiosk_0.json')
    textures = json.loads(old.read_text())['textures']
    textures = copy.deepcopy(textures)
    textures['screen'] = 'economy:block/terminal_screen'
    textures['header'] = ('economy:block/banking/' + bank['key'] + '_kiosk_wide_header'
                          if bank else 'economy:block/terminal_wide_kiosk_header')
    return textures


def generate():
    # Geometry-only sources are separate from every legacy narrow model.
    for cell in range(4):
        elements = [part for start, end, texture in ELEMENTS
                    if (part := clipped_box(start, end, texture, cell)) is not None]
        model = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'elements': elements}
        output(ASSETS / f'models/block/branch_kiosk_wide_shape_{cell}.json', model)
    output(SOURCE / 'geometry.json', {'version': 1, 'license': 'CC0-1.0',
            'cell_origins': CELL_ORIGINS, 'elements': ELEMENTS,
            'notes': 'Original 2x2 console geometry. North front, cell0 right and cell1 left. Legacy narrow geometry remains unchanged.'})
    preview = Canvas(6 * 104, 4 * 116, '#D8D4C9')
    for appearance_index, bank in enumerate([None] + BANKS):
        stem = bank['key'] + '_kiosk_wide' if bank else 'branch_kiosk_wide'
        prefix = 'banking/' if bank else ''
        header_art = header(bank)
        header_art.save(ASSETS / ('textures/block/banking/' + bank['key'] + '_kiosk_wide_header.png'
                                  if bank else 'textures/block/terminal_wide_kiosk_header.png'))
        header_art.svg(SOURCE / f'{stem}_header.svg')
        px, py = (appearance_index % 6) * 104 + 4, (appearance_index // 6) * 116 + 4
        primary = bank['primary'] if bank else '#263B50'
        accent = bank['accent'] if bank else '#77C9B1'
        preview.rect(px + 6, py + 92, px + 90, py + 98, '#A7ADB3')
        preview.rect(px, py + 8, px + 6, py + 92, accent)
        preview.rect(px + 90, py + 8, px + 96, py + 92, accent)
        preview.rect(px + 6, py + 8, px + 90, py + 92, primary)
        preview.rect(px + 6, py + 22, px + 90, py + 46, INK)
        preview.rect(px + 9, py + 25, px + 87, py + 43, '#427C7B')
        preview.rect(px + 2, py + 48, px + 94, py + 58, '#A7ADB3')
        preview.rect(px + 2, py + 48, px + 94, py + 52, accent)
        preview.blit(header_art, px + 6, py + 8, 84, 14)
        preview.text(bank['currency'] if bank else 'INFO', px + 4, py + 104, INK)
        textures = material_map(bank)
        for cell in range(4):
            output(ASSETS / f'models/block/{prefix}{stem}_{cell}.json', {
                'parent': f'economy:block/branch_kiosk_wide_shape_{cell}', 'textures': textures,
                'render_type': 'minecraft:cutout'})
        inventory_elements = [clipped_box(*element) for element in ELEMENTS]
        for element in inventory_elements:
            for bound in ('from', 'to'):
                element[bound] = [value / 2 for value in element[bound]]
        inventory = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
                     'textures': textures, 'elements': inventory_elements, 'display': DISPLAY}
        # Inventory model bounds must remain within the documented -16..32 format.
        output(ASSETS / f'models/block/{prefix}{stem}_inventory.json', inventory)
        if bank:
            output(ASSETS / f'models/item/banking/{bank["key"]}_kiosk.json', {
                'parent': f'economy:block/banking/{stem}_inventory'})
    preview.save(ROOT / 'tools/banking-assets/previews/wide-kiosk-fronts.png')
    variants = {}
    for index, bank in enumerate([None] + BANKS):
        old = ('banking/' + bank['key'] + '_kiosk') if bank else 'branch_kiosk'
        wide = ('banking/' + bank['key'] + '_kiosk_wide') if bank else 'branch_kiosk_wide'
        for cell in range(4):
            for facing, rotation in FACINGS.items():
                for expanded in (False, True):
                    # Invalid narrow2/3 are visibly safe legacy models; Java rejects
                    # their integrity rather than silently expanding their geometry.
                    model = wide + '_' + str(cell) if expanded else old + '_' + str(min(cell, 1))
                    variants[f'bank={index},cell={cell},facing={facing},wide={str(expanded).lower()}'] = {
                        'model': 'economy:block/' + model, 'y': rotation}
    output(ASSETS / 'blockstates/branch_kiosk.json', {'variants': variants})
    item_path = ASSETS / 'models/item/branch_kiosk.json'
    item = json.loads(item_path.read_text())
    item['parent'] = 'economy:block/branch_kiosk_wide_inventory'
    output(item_path, item)
    validate()
    print('Generated 24 original wide kiosk appearances, 96 cell models, 24 inventory models, 24 headers and 768 explicit blockstate variants; legacy narrow models unchanged.')


def validate():
    variants = json.loads((ASSETS / 'blockstates/branch_kiosk.json').read_text())['variants']
    assert len(variants) == 24 * 4 * 4 * 2
    model_paths = list((ASSETS / 'models/block').glob('branch_kiosk_wide*.json'))
    model_paths += list((ASSETS / 'models/block/banking').glob('*_kiosk_wide*.json'))
    for path in model_paths:
        model = json.loads(path.read_text())
        parent = model.get('parent', '')
        if parent.startswith('economy:'):
            assert (ASSETS / 'models' / (parent.split(':', 1)[1] + '.json')).exists(), (path, parent)
        for texture in model.get('textures', {}).values():
            if texture.startswith('economy:'):
                assert (ASSETS / 'textures' / (texture.split(':', 1)[1] + '.png')).exists(), (path, texture)
        for element in model.get('elements', []):
            assert all(-16 <= number <= 32 for key in ('from', 'to') for number in element[key]), path
            assert all(element['from'][i] < element['to'][i] for i in range(3)), path
            for face in element['faces'].values():
                assert all(0 <= n <= 16 for n in face['uv']), path
    for variant in variants.values():
        assert (ASSETS / 'models' / (variant['model'].split(':', 1)[1] + '.json')).exists()


if __name__ == '__main__':
    generate()
