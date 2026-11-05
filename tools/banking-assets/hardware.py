"""Original CC0 bank hardware surfaces and future hardware model resources.

Run through the asset package generator; no runtime registrations are created.
"""
import copy
import json
from pathlib import Path
from art import ASSETS, BANKS, Canvas, INK, PAPER, color, contrast, mark, output, shade

HERE = Path(__file__).resolve().parent
KINDS = ('atm', 'teller', 'kiosk')
# Cell order follows the actual existing blockstates, not old tooling comments.
SOURCES = {
    'atm': ('atm_right', 'atm', 'atm_upper_right', 'atm_upper'),
    'teller': ('bank_teller_station_right', 'bank_teller_station',
               'bank_teller_station_upper_right', 'bank_teller_station_upper'),
}


def surface(bank, kind):
    """Readable display art; every glyph and motif is authored in this package."""
    w = 32 if kind == 'kiosk' else 64
    c = Canvas(w, 64, bank['primary'])
    fg = contrast(bank['primary'])
    c.rect(1, 1, w-1, 63, shade(bank['primary'], -18))
    c.rect(2, 2, w-2, 22, bank['primary'])
    c.blit(mark(bank['key'], bank['accent']), (w-16)//2, 4)
    for i, line in enumerate(bank['label']):
        c.text(line, (w-(len(line)*4-1))//2, 25+i*7, fg)
    c.rect(3, 42, w-3, 53, bank['accent'])
    c.text(bank['currency'], (w-(len(bank['currency'])*4-1))//2, 45, contrast(bank['accent']))
    caption = {'atm':'ATM', 'teller':'TELLER', 'kiosk':'INFO'}[kind]
    c.text(caption, (w-(len(caption)*4-1))//2, 56, fg)
    return c


def material(bank, role):
    base = bank['primary'] if role in ('shell','dark') else bank['accent']
    if role == 'dark': base = shade(base, -42)
    if role == 'metal': base = '#A7ADB3'
    c = Canvas(16, 16, base)
    c.rect(0, 0, 16, 1, shade(base, 25))
    c.rect(0, 15, 16, 16, shade(base, -28))
    if role == 'vent':
        for y in range(3,14,3): c.rect(2,y,14,y+1,INK)
    elif role == 'slot':
        c.rect(1,5,15,11,INK); c.rect(2,5,14,6,bank['accent'])
    elif role == 'keypad':
        for y in (3,7,11):
            for x in (3,7,11): c.rect(x,y,x+2,y+2,PAPER)
    elif role == 'trim':
        c.rect(0,5,16,11,bank['accent'])
    return c


def box(bounds, texture, uv=None):
    return {'from': bounds[:3], 'to': bounds[3:], 'faces': {
        side: {'uv': uv or [0,0,16,16], 'texture': '#'+texture}
        for side in ('north','south','east','west','up','down')}}


def kiosk_cell(upper):
    # A slender information totem: pedestal, rear spine, angled-width display bezel.
    if upper:
        elements = [box([4,0,7,12,16,11],'shell'),
                    box([2,2,3,14,15,8],'trim'),
                    box([3,3,2.8,13,14,3],'front', [0,0,16,16])]
    else:
        elements = [box([2,0,3,14,2,13],'metal'),
                    box([5,2,7,11,16,11],'shell'),
                    box([4,13,6,12,16,12],'trim')]
    return {'parent':'minecraft:block/block', 'elements':elements}


def generate():
    records = []
    preview = Canvas(23*76, 208, '#D8D4C9')
    for index, bank in enumerate(BANKS):
        record = {'key':bank['key'], 'currency':bank['currency']}
        for ki, kind in enumerate(KINDS):
            stem = bank['key']+'_'+kind
            front = surface(bank,kind)
            textures = {}
            for role in ('front','shell','trim','metal','dark','keypad','slot','vent'):
                art = front if role=='front' else material(bank,role)
                path = ASSETS/'textures/block/banking'/f'{stem}_{role}.png'
                art.save(path)
                art.svg(HERE/'sources/hardware'/f'{stem}_{role}.svg')
                textures[role] = 'economy:block/banking/'+stem+'_'+role
            # Existing screen/glass UV splits retain their exact original orientation.
            textures.update(screen=textures['front'],glass=textures['front'],
                            chart=textures['front'],particle=textures['shell'])
            cells = []
            all_elements = []
            for cell in range(2 if kind=='kiosk' else 4):
                model = (kiosk_cell(cell==1) if kind=='kiosk' else
                         json.loads((ASSETS/'models/block'/f'{SOURCES[kind][cell]}.json').read_text()))
                model['textures'] = textures
                model['render_type'] = 'minecraft:cutout'
                model_id = 'economy:block/banking/'+stem+'_'+str(cell)
                output(ASSETS/'models/block/banking'/f'{stem}_{cell}.json',model)
                cells.append(model_id)
                for element in copy.deepcopy(model['elements']):
                    dx = 0 if kind=='kiosk' else (16 if cell%2==0 else 0)
                    dy = cell*16 if kind=='kiosk' else (16 if cell>=2 else 0)
                    for end in ('from','to'):
                        element[end][0] += dx
                        element[end][1] += dy
                    all_elements.append(element)
            inventory = {'parent':'minecraft:block/block','textures':textures,
                'render_type':'minecraft:cutout','elements':all_elements,
                'display': {'gui':{'rotation':[25,225,0], 'translation':[0,-2,0],
                                    'scale':[0.35,0.35,0.35]},
                            'ground':{'translation':[0,3,0],'scale':[0.2,0.2,0.2]},
                            'fixed':{'scale':[0.4,0.4,0.4]}}}
            inv_id = 'economy:block/banking/'+stem+'_inventory'
            output(ASSETS/'models/block/banking'/f'{stem}_inventory.json',inventory)
            output(ASSETS/'models/item/banking'/f'{stem}.json',{'parent':inv_id})
            record[kind] = {'textures': textures, 'models':cells,
                'inventory_model':inv_id, 'item_model':'economy:item/banking/'+stem,
                'cells':len(cells), 'facing':'north-front; rotate all cells together with horizontal facing',
                'geometry_sources': list(SOURCES[kind]) if kind!='kiosk' else ['original slender information totem'],
                'placement_notes': ('lower anchor and upper satellite; one wide, two high, one deep' if kind=='kiosk'
                    else 'existing detailed 2x2 geometry; cell order right, base, upper_right, upper; preserve existing support and single-drop semantics'),
                'label':bank['label'], 'display_currency':bank['currency']}
            # Contact sheet conveys intended distinct silhouettes at small pixel scale.
            x=index*76+6; y=ki*68+2
            if kind=='kiosk':
                preview.rect(x+21,y+40,x+35,y+61,bank['primary'])
                preview.rect(x+13,y+59,x+43,y+63,INK)
                preview.blit(front,x+14,y,28,48)
            elif kind=='atm':
                preview.rect(x+2,y+2,x+58,y+64,bank['primary'])
                preview.blit(front,x+9,y+5,42,42)
                preview.rect(x+10,y+51,x+50,y+55,INK)
                for vy in (59,61):preview.rect(x+10,y+vy-5,x+50,y+vy-4,shade(bank['primary'],-40))
            else:
                preview.rect(x,y+40,x+60,y+64,bank['primary'])
                preview.rect(x,y+37,x+60,y+41,bank['accent'])
                preview.rect(x+2,y+1,x+4,y+38,INK);preview.rect(x+56,y+1,x+58,y+38,INK)
                preview.blit(front,x+7,y+1,46,36)
        records.append(record)
    preview.save(HERE/'previews/hardware-contact.png')
    return {'banks':records}

if __name__ == '__main__':
    generate()
