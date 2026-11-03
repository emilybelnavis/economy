#!/usr/bin/env python3
"""Generate original trim palettes and terminal resources using only Python stdlib.
Run from any directory. Existing teal terminal geometry and body textures are inherited.
"""
import json, struct, zlib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/generated/resources'
COLORS = dict(white=(227,230,229), orange=(232,118,34), magenta=(183,67,188), light_blue=(91,174,216), yellow=(241,203,56), lime=(133,190,51), pink=(225,135,166), gray=(69,75,78), light_gray=(155,162,163), cyan=(32,144,157), purple=(119,67,174), blue=(51,78,171), brown=(121,83,56), green=(71,119,57), red=(181,53,49), black=(30,33,39))
def write(path, data):
    p=OUT/path; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(data,indent=2)+'\n')
def png(name, pixels, size=16):
    def chunk(t,b): return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    raw=b''.join(b'\0'+bytes(sum(row,())) for row in pixels)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
    p=OUT/f'assets/economy/textures/block/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
def model(name,data): write(f'assets/economy/models/{name}.json',data)
def states(name,atm=False):
    v={}
    for i,f in enumerate(('north','east','south','west')):
        for h in (('lower','upper') if atm else ('',)):
            v[f'facing={f}'+(f',half={h}' if h else '')]={'model':f'economy:block/{name}'+('_upper' if h=='upper' else ''),'y':i*90}
    write(f'assets/economy/blockstates/{name}.json',{'variants':v})
def loot(name,atm=False):
    conditions=[{'condition':'minecraft:survives_explosion'}]
    if atm: conditions.append({'condition':'minecraft:block_state_property','block':f'economy:{name}','properties':{'half':'lower'}})
    write(f'data/economy/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'economy:{name}'}],'conditions':conditions}]})
def cube(a,b,texture,overrides=None):
    faces={f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}
    for f,t in (overrides or {}).items(): faces[f]['texture']='#'+t
    return {'from':a,'to':b,'faces':faces}
def workstation(name,elements):
    tex={k:f'economy:block/terminal_{k}' for k in ('dark','metal','trim','screen','keypad','slot','vent')}
    tex.update(chart='economy:block/trading_chart',glass='economy:block/teller_glass',particle='economy:block/terminal_dark')
    model('block/'+name,{'parent':'minecraft:block/block','textures':tex,'elements':elements})
    model('item/'+name,{'parent':'economy:block/'+name})
    states(name);loot(name)
def main():
    display=json.loads((ROOT/'src/main/resources/assets/economy/models/item/cash_register.json').read_text())['display']
    entries=[]; lang={}
    for color,rgb in COLORS.items():
        # Original brushed trim, with a bright top edge and restrained dither.
        png('terminal_trim_'+color,[[tuple(max(0,min(255,c+(20 if y==0 else -19 if y==15 else (x*3+y*5)%7-3))) for c in rgb) for x in range(16)] for y in range(16)])
        for kind in ('atm','cash_register'):
            name=color+'_'+kind;trim={'trim':'economy:block/terminal_trim_'+color}
            model('block/'+name,{'parent':'economy:block/'+kind,'textures':trim})
            if kind=='atm':
                model('block/'+name+'_upper',{'parent':'economy:block/atm_upper','textures':trim})
                model('item/'+name,{'parent':'economy:item/atm','textures':trim})
            else: model('item/'+name,{'parent':'economy:block/'+name,'display':display})
            states(name,kind=='atm');loot(name,kind=='atm')
            write(f'data/economy/recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'tag':'economy:'+('atms' if kind=='atm' else 'cash_registers')},{'item':'minecraft:'+color+'_dye'}],'result':{'id':'economy:'+name,'count':1}})
            entries.append(name);lang['block.economy.'+name]=color.replace('_',' ').title()+' '+('ATM' if kind=='atm' else 'Cash Register')
    for kind,tag in [('atm','atms'),('cash_register','cash_registers')]:
        for folder in ('block','item'):
            write(f'data/economy/tags/{folder}/{tag}.json',{'replace':False,'values':['economy:'+kind]+['economy:'+c+'_'+kind for c in COLORS]})
    # Opaque glass-like cyan panel: highlights, diagonals, and a dark protective border.
    png('teller_glass',[[((91,184,193) if (x+y)%9<2 else (47,111,127)) if x not in (0,15) and y not in (0,15) else (27,62,74) for x in range(16)] for y in range(16)])
    chart=[[(12,23,32) for x in range(16)] for y in range(16)]
    for y in (3,7,11):
        for x in range(1,15):chart[y][x]=(29,49,59)
    for x,top,bottom,rise in [(2,8,13,True),(4,6,11,True),(6,5,10,False),(8,7,12,False),(10,4,9,True),(12,2,7,True),(14,3,8,False)]:
        c=(67,218,133) if rise else (232,79,87)
        for y in range(max(1,top-1),min(15,bottom+2)):chart[y][x]=c
        for y in range(top,bottom+1):chart[y][x-1]=c
    png('trading_chart',chart)
    c=cube
    teller=[c([0,0,2],[16,7,14],'dark'),c([0,7,1],[16,8,15],'metal'),c([0,6,1],[16,7,2],'trim'),c([0,8,8],[1,16,9],'metal'),c([15,8,8],[16,16,9],'metal'),c([0,15,8],[16,16,9],'metal'),c([1,11,8.2],[15,15,8.8],'glass'),c([1,8,8.2],[5,11,8.8],'glass'),c([11,8,8.2],[15,11,8.8],'glass'),c([5,8,7.7],[11,8.5,10],'metal'),c([11,8,11],[14,12,13],'dark',{'north':'screen'}),c([2,8,11],[6,8.5,14],'dark',{'up':'keypad'}),c([2,8,3],[5,8.3,6],'metal',{'up':'slot'})]
    stock=[c([0,0,2],[16,1,14],'dark'),c([1,1,10],[3,7,13],'metal'),c([13,1,10],[15,7,13],'metal'),c([0,7,1],[16,8,15],'metal'),c([1,8,11],[7,9,13],'dark'),c([3,9,11],[5,10,12],'metal'),c([0.5,10,10],[7.5,16,12],'dark',{'north':'chart'}),c([8.5,10,10],[15.5,16,12],'dark',{'north':'chart'}),c([10.5,8,11],[13.5,10,12],'metal'),c([2,8,3],[11,8.5,7],'dark',{'up':'keypad'}),c([12,8,4],[14,8.5,6],'dark'),c([11,1,3],[15,6.5,9],'dark',{'north':'vent'}),c([11.5,5,2.9],[12.5,6,3],'trim')]
    for name,elements,pattern,key in [('bank_teller_station',teller,['IGI','IRI','III'],{'I':'iron_ingot','G':'glass_pane','R':'redstone'}),('stock_trading_computer',stock,['GGG','RCR','III'],{'G':'glass_pane','R':'redstone','C':'comparator','I':'iron_ingot'})]:
        workstation(name,elements);entries.append(name);lang['block.economy.'+name]=name.replace('_',' ').title()
        write(f'data/economy/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':'minecraft:'+v} for k,v in key.items()},'result':{'id':'economy:'+name,'count':1}})
    manifest={'ids':['economy:'+n for n in entries],'lang':lang,'shapes_north':{'bank_teller_station':[[0,0,1,16,8,15],[0,8,8,16,16,9]],'stock_trading_computer':[[0,0,1,16,8,15],[0,8,10,16,16,13]]},'shape_rotation':'North is identity; east rotates 90 degrees around (8,8), south 180, west 270. Bounds are x1,y1,z1,x2,y2,z2 in block pixels.'}
    (ROOT/'tools/terminal_variants.json').write_text(json.dumps(manifest,indent=2)+'\n')
    # Keep resized machines authoritative after regenerating legacy palettes.
    wide = ROOT / 'tools/generate_wide_machines.py'
    if wide.exists():
        import runpy
        runpy.run_path(str(wide), run_name='__main__')
if __name__=='__main__': main()
