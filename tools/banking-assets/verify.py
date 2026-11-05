#!/usr/bin/env python3
"""Check the catalog matrix, model graph, PNG payloads, localization and legacy resources."""
import hashlib, json, re, struct, zlib
from pathlib import Path
from art import ASSETS, ROOT
HERE=Path(__file__).resolve().parent
manifest=json.loads((ASSETS/'banking/art_manifest.json').read_text())
brands=json.loads((HERE/'brands.json').read_text())['banks']
source=(ROOT/'src/main/java/dev/emily/economy/institution/InstitutionCatalog.java').read_text()
rows=re.findall(r'bank\("([^"]+)", "([^"]+)", "([^"]+)", Tier\.(\w+), ([^)]+)\)',source)
assert len(rows)==23
expected=set()
for key,name,currency,tier,networks in rows:
    bank=next(b for b in brands if b['key']==key)
    nets=re.findall(r'Network\.(\w+)',networks)
    assert (bank['name'],bank['currency'],bank['tier'],bank['networks'])==(name,currency,tier,nets)
    for t in ([1,2] if tier=='HYBRID' else [2] if tier=='TIER2_ONLY' else [1]):
        expected.add(f'{key}_debit_t{t}')
        expected.update(f'{key}_credit_{n.lower()}_t{t}' for n in nets)
assert expected=={c['key'] for c in manifest['cards']} and len(expected)==74
assert len(manifest['networks'])==4 and len(manifest['issuers'])==23
assert all(not c['registered'] and not c['financial_authority'] for c in manifest['cards'])
lang=json.loads((ASSETS/'lang/en_us.json').read_text())
assert all(lang.get(k)==v for k,v in manifest['localization'].items())

def local(ref,kind,suffix):
    namespace,path=ref.split(':',1)
    assert namespace=='economy',ref
    p=ASSETS/kind/(path+suffix);assert p.is_file(),p
    return p

def png(path):
    raw=path.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n',path
    w,h,depth,mode=struct.unpack('>IIBB',raw[16:26]);assert depth==8 and mode==6,(path,mode)
    pos=8;payload=b''
    while pos<len(raw):
        n=struct.unpack('>I',raw[pos:pos+4])[0];kind=raw[pos+4:pos+8];data=raw[pos+8:pos+8+n]
        assert zlib.crc32(kind+data)&0xffffffff==struct.unpack('>I',raw[pos+8+n:pos+12+n])[0]
        if kind==b'IDAT':payload+=data
        pos+=n+12
    assert len(zlib.decompress(payload))==h*(1+w*4),path
    return w,h

seen=set()
def model(ref):
    if ref in seen:return
    seen.add(ref);data=json.loads(local(ref,'models','.json').read_text())
    parent=data.get('parent','')
    if parent.startswith('economy:'):model(parent)
    for texture in data.get('textures',{}).values():
        if texture.startswith('economy:'):png(local(texture,'textures','.png'))
    # Resolve face aliases across this local parent chain.
    textures={};chain=data
    while True:
        textures={**chain.get('textures',{}),**textures}
        p=chain.get('parent','')
        if not p.startswith('economy:'):break
        chain=json.loads(local(p,'models','.json').read_text())
    for element in data.get('elements',[]):
        for face in element.get('faces',{}).values():
            value=face['texture'];aliases=set()
            while value.startswith('#'):
                assert value not in aliases,(ref,value);aliases.add(value)
                value=textures[value[1:]]
            if value.startswith('economy:'):local(value,'textures','.png')

for c in manifest['cards']:
    model(c['model']);assert png(local(c['texture'],'textures','.png'))==(64,64)
for entry in manifest['issuers']+manifest['networks']+manifest['icons']:
    assert png(local(entry['texture'],'textures','.png'))==(16,16)
for bank in manifest['hardware']['banks']:
    assert bank['currency']==next(b['currency'] for b in brands if b['key']==bank['key'])
    for kind in ['atm','teller','kiosk']:
        family=bank[kind];assert family['display_currency']==bank['currency']
        assert len(family['models'])==(2 if kind=='kiosk' else 4)
        for ref in family['models']+[family['inventory_model'],family['item_model']]:model(ref)
        if kind!='kiosk':
            for ref,original in zip(family['models'],family['geometry_sources']):
                assert json.loads(local(ref,'models','.json').read_text())['elements']==json.loads((ASSETS/'models/block'/f'{original}.json').read_text())['elements']
assert png(local(manifest['agent']['texture'],'textures','.png'))==(64,64)
legacy={}
for key in manifest['legacy_card_ids']:
    for kind,suffix in [('textures','.png'),('models','.json')]:
        p=ASSETS/kind/'item'/(key+suffix);assert p.exists(),p
        legacy[str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
baseline=HERE/'legacy_baseline.json'
assert legacy==json.loads(baseline.read_text()),'Legacy collectible changed'
assert manifest==json.loads((HERE/'resource_inventory.json').read_text())
print(f'PASS: 23 issuers, 4 networks, 74 approved cards, 69 hardware families, {len(seen)} model nodes, legacy cards intact')
