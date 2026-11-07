#!/usr/bin/env python3
"""Convert exact GenMC studio exports to original 1.21.1 Economy structure templates.

No building geometry is regenerated here. Exported cells are cropped/translated, native
sign text and registered service machines replace their reserved bays, and air is explicit.
"""
from pathlib import Path
import gzip
import hashlib
import json
import re
import struct

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'tools/village-banks/genmc'
TARGET = ROOT / 'src/main/resources/data/economy/structure/banks'
BANKS = json.loads((ROOT/'tools/banking-assets/brands.json').read_text())['banks']
STYLES = {'plains':'oak','desert':'jungle','savanna':'acacia','snowy':'spruce','taiga':'spruce'}

def string(value):
    encoded=value.encode('utf-8')
    return struct.pack('>H',len(encoded))+encoded

def payload(kind,value):
    if kind==1:return struct.pack('>b',value)
    if kind==3:return struct.pack('>i',value)
    if kind==8:return string(value)
    if kind==9:
        child,entries=value
        return bytes([child])+struct.pack('>i',len(entries))+b''.join(payload(child,e) for e in entries)
    if kind==10:return b''.join(bytes([tag])+string(key)+payload(tag,data) for key,(tag,data) in value.items())+b'\0'
    raise ValueError(kind)

def compound(value):return (10,value)
def state(value):
    base,_,rest=value.partition('[')
    result={'Name':(8,base if ':' in base else 'minecraft:'+base)}
    if rest:
        props=dict(part.split('=',1) for part in rest.removesuffix(']').split(','))
        result['Properties']=compound({key:(8,val) for key,val in sorted(props.items())})
    return result

def sign(bank,size):
    lines=['']
    for word in bank['name'].split():
        if len(lines[-1])+len(word)+1>15:lines.append(word)
        else:lines[-1]=(lines[-1]+' '+word).strip()
    lines=(lines+['Compact branch' if size=='compact' else 'Larger branch',bank['currency']])[:4]
    lines+=['']*(4-len(lines))
    text={'messages':(9,(8,[json.dumps({'text':s},separators=(',',':')) for s in lines])),
          'color':(8,'black'),'has_glowing_text':(1,0)}
    return {'id':(8,'minecraft:sign'),'front_text':compound(text),'back_text':compound(text),'is_waxed':(1,1)}

def export_style(style):
    plain=SOURCE/(style+'.json')
    archived=SOURCE/(style+'.json.gz')
    raw=plain.read_bytes() if plain.exists() else gzip.decompress(archived.read_bytes())
    data=json.loads(raw)
    assert data['format']=='genmcp-build' and len(data['build']['blocks'])>20000
    sparse={tuple(row[:3]):row[3] for row in data['build']['blocks']}
    results=[]
    for index,bank in enumerate(BANKS):
        for size in ['compact','larger']:
            large=size=='larger';w,h,d=(17,9,11) if large else (11,8,9)
            ox=2+(index%5)*36+(14 if large else 0);oz=2+(index//5)*16
            cells={(x,y,z):sparse.get((ox+x,1+y,oz+z),'minecraft:air') for y in range(h) for z in range(d) for x in range(w)}
            # Bay geometry is not a fake decorative terminal. Final output uses the real mod block.
            services=[(2,1,2),(14,1,2)] if large else [(2,1,2)]
            for ax,ay,az in services:
                for cell in range(4):
                    position=(ax-(cell&1),ay+(cell>>1),az)
                    assert cells[position] in ('air','minecraft:air'),(style,bank['key'],position,cells[position])
                    cells[position]=f'economy:branch_kiosk[bank={index+1},cell={cell},facing=north,wide=true]'
            palette=[];lookup={};blocks=[]
            for position,block in cells.items():
                if block not in lookup:lookup[block]=len(palette);palette.append(state(block))
                entry={'pos':(9,(3,list(position))),'state':(3,lookup[block])}
                if '_wall_sign' in block:entry['nbt']=compound(sign(bank,size))
                if block.startswith('economy:branch_kiosk') and ',cell=0,' in block:
                    entry['nbt']=compound({'id':(8,'economy:terminal')})
                blocks.append(entry)
            names=[s['Name'][1] for s in palette]
            assert not any(x in names for x in ['minecraft:magenta_concrete','minecraft:purple_concrete','minecraft:purple_glazed_terracotta'])
            assert all(not re.search(r'(oak|spruce|jungle|acacia|birch|dark_oak)_(planks|log|slab|door|wall_sign)',n) or STYLES[style] in n for n in names)
            root={'DataVersion':(3,3955),'author':(8,'Economy A7-04 / original GenMC mid-century collection'),
                  'size':(9,(3,[w,h,d])),'palette':(9,(10,palette)),'blocks':(9,(10,blocks)),'entities':(9,(10,[]))}
            encoded=b'\x0a'+string('')+payload(10,root)
            output=TARGET/style/bank['key']/(size+'.nbt');output.parent.mkdir(parents=True,exist_ok=True)
            packed=gzip.compress(encoded,mtime=0);output.write_bytes(packed)
            results.append({'style':style,'bank':bank['key'],'currency':bank['currency'],'size':size,
                            'resource':f'economy:banks/{style}/{bank["key"]}/{size}',
                            'dimensions':[w,h,d],'services':services,
                            'desk_slots':[[2,1,7],[12,1,7]] if large else [[7,1,5]],
                            'sha256':hashlib.sha256(packed).hexdigest(),'genmc_source_sha256':hashlib.sha256(raw).hexdigest()})
    # Preserve exact studio output in a compact deterministic source archive.
    archived.write_bytes(gzip.compress(raw,mtime=0))
    if plain.exists():plain.unlink()
    return results

def main():
    templates=[t for style in STYLES for t in export_style(style)]
    manifest={'version':1,'design':'mid-century modern','license':'CC0-1.0','source':'GenMC exact exported geometry; export_templates.py inserts real service machines and sign NBT',
              'wood_by_style':STYLES,'projects':json.loads((SOURCE/'projects.json').read_text()),'templates':templates}
    path=ROOT/'src/main/resources/data/economy/worldgen/bank_templates.json'
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(manifest,indent=2)+'\n')
    print(f'Converted {len(templates)} exact GenMC templates: all 23 banks, both sizes, all five styles; no colored placeholders.')

if __name__=='__main__':main()
