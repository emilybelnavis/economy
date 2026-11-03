#!/usr/bin/env python3
"""Generate four local block tiles and complete item models from immutable 0.1.4 geometry.

Only models, blockstates, loot tables and the collision-shape manifest are written.
Coordinates and UVs are clipped together so stretched artwork remains continuous.
"""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'tools/model_sources'
MAIN = ROOT / 'src/main/resources'
GENERATED = ROOT / 'src/generated/resources'
COLORS = 'white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split()
PARTS = (('', 0, 0), ('_right', 16, 0), ('_upper', 0, 16), ('_upper_right', 16, 16))


def write(root, path, value):
    dest = root / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(value, indent=2) + '\n')


def source(name):
    return json.loads((SOURCES / (name + '.json')).read_text())


def scaled(elements, scale, offset_y=0):
    result = copy.deepcopy(elements)
    for element in result:
        for key in ('from', 'to'):
            element[key] = [v * scale[i] + (offset_y if i == 1 else 0) for i, v in enumerate(element[key])]
    return result


def clip(element, x, y):
    """Clip a cuboid; UV axes follow Minecraft FaceBakery's unrotated convention."""
    a, b = element['from'], element['to']
    lo = [max(a[0], x), max(a[1], y), a[2]]
    hi = [min(b[0], x + 16), min(b[1], y + 16), b[2]]
    if any(l >= h for l, h in zip(lo, hi)):
        return None
    result = copy.deepcopy(element)
    result['from'] = [lo[0] - x, lo[1] - y, lo[2]]
    result['to'] = [hi[0] - x, hi[1] - y, hi[2]]
    axes = {'north': ((0, -1), (1, -1)), 'south': ((0, 1), (1, -1)),
            'west': ((2, 1), (1, -1)), 'east': ((2, -1), (1, -1)),
            'up': ((0, 1), (2, 1)), 'down': ((0, 1), (2, -1))}
    for side, face in result['faces'].items():
        uv = face.get('uv', [0, 0, 16, 16])
        intervals = []
        for axis, sign in axes[side]:
            start = (lo[axis] - a[axis]) / (b[axis] - a[axis])
            end = (hi[axis] - a[axis]) / (b[axis] - a[axis])
            intervals.append((start, end) if sign == 1 else (1 - end, 1 - start))
        # Face rotations rotate the UV axes, including their direction.
        for _ in range(face.get('rotation', 0) // 90):
            u, v = intervals
            intervals = [(1 - v[1], 1 - v[0]), u]
        u, v = intervals
        face['uv'] = [uv[0] + (uv[2] - uv[0]) * u[0], uv[1] + (uv[3] - uv[1]) * v[0],
                      uv[0] + (uv[2] - uv[0]) * u[1], uv[1] + (uv[3] - uv[1]) * v[1]]
    return result


def states(root, name):
    variants = {}
    for rotation, facing in enumerate(('north', 'east', 'south', 'west')):
        for suffix, x, y in PARTS:
            key = f'facing={facing},half={"upper" if y else "lower"},right={str(bool(x)).lower()}'
            variants[key] = {'model': f'economy:block/{name}{suffix}', 'y': rotation * 90}
    write(root, f'assets/economy/blockstates/{name}.json', {'variants': variants})


def loot(root, name):
    write(root, f'data/economy/loot_table/blocks/{name}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': f'economy:{name}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'},
        {'condition': 'minecraft:block_state_property', 'block': f'economy:{name}',
         'properties': {'half': 'lower', 'right': 'false'}}]}]})


def machine(root, name, template, elements, display):
    for suffix, x, y in PARTS:
        model = {k: copy.deepcopy(v) for k, v in template.items() if k not in ('elements', 'display')}
        model['elements'] = [part for e in elements if (part := clip(e, x, y)) is not None]
        write(root, f'assets/economy/models/block/{name}{suffix}.json', model)
    item = {k: copy.deepcopy(v) for k, v in template.items() if k not in ('elements', 'display')}
    item.update(elements=elements, display=display)
    write(root, f'assets/economy/models/item/{name}.json', item)
    states(root, name)
    loot(root, name)


def main():
    lower, upper = source('atm_lower'), source('atm_upper')
    atm = scaled(lower['elements'], (2, 1, 1)) + scaled(upper['elements'], (2, 1, 1), 16)
    # Center the complete 32x32 machine on the item transform pivot (8,8,8).
    display = {
        'gui': {'rotation': [30, 225, 0], 'translation': [-4, -4, 0], 'scale': [0.36, 0.36, 0.36]},
        'ground': {'translation': [-2, 2, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'translation': [-4, -4, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [-3, 0, 0], 'scale': [0.25, 0.25, 0.25]},
        'thirdperson_lefthand': {'rotation': [75, 225, 0], 'translation': [-3, 0, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [-3, -2, 0], 'scale': [0.3, 0.3, 0.3]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [-3, -2, 0], 'scale': [0.3, 0.3, 0.3]}}
    machine(MAIN, 'atm', lower, atm, display)
    shapes = {'atm': [e['from'] + e['to'] for e in atm]}
    for name in ('bank_teller_station', 'stock_trading_computer'):
        template = source(name)
        elements = scaled(template['elements'], (2, 2, 1))
        machine(GENERATED, name, template, elements, display)
        shapes[name] = [e['from'] + e['to'] for e in elements]
    for color in COLORS:
        name = color + '_atm'
        trim = {'trim': f'economy:block/terminal_trim_{color}'}
        for suffix, _, _ in PARTS:
            write(GENERATED, f'assets/economy/models/block/{name}{suffix}.json',
                  {'parent': f'economy:block/atm{suffix}', 'textures': trim})
        write(GENERATED, f'assets/economy/models/item/{name}.json',
              {'parent': 'economy:item/atm', 'textures': trim})
        states(GENERATED, name)
        loot(GENERATED, name)
    write(ROOT, 'tools/machine_shapes.json', shapes)
    manifest_path = ROOT / 'tools/terminal_variants.json'
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text())
        manifest['shapes_north'] = shapes
        manifest['footprint'] = {'width': 2, 'height': 2, 'depth': 1}
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    generator = ROOT / 'tools/generate_machine_shapes.py'
    if generator.exists():
        import runpy
        runpy.run_path(str(generator), run_name='__main__')
    centering = ROOT / 'tools/center_item_models.py'
    if centering.exists():
        import runpy
        runpy.run_path(str(centering), run_name='__main__')


if __name__ == '__main__':
    main()
