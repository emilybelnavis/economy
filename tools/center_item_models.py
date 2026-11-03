#!/usr/bin/env python3
"""Fit machine icons to a 14px square and centre their projected GUI bounds.

Minecraft's GUI context is shared by inventory slots and the hotbar. ItemTransform
uses T * Rx * Ry * Rz * S * T(-8,-8,-8), with JSON translation in model pixels.
Only display.gui changes; world geometry, UVs, and other display contexts are preserved.
"""
from pathlib import Path
import itertools,json,math
ROOT=Path(__file__).resolve().parents[1]
RESOURCE_DIRS=[ROOT/'src/main/resources',ROOT/'src/generated/resources']

def path_for(ref):
 namespace,name=ref.split(':',1)
 for base in RESOURCE_DIRS:
  p=base/'assets'/namespace/'models'/(name+'.json')
  if p.exists():return p
 raise FileNotFoundError(ref)

def resolved(ref,trail=()):
 if ref in trail:raise ValueError('Cyclic model: '+ref)
 m=json.loads(path_for(ref).read_text());parent=m.get('parent','')
 result=resolved(parent,trail+(ref,)) if parent.startswith('economy:') else {}
 return {**result,**m,'display':{**result.get('display',{}),**m.get('display',{})}}

def rotate(vertex,rotation):
 # Quaternionf.rotationXYZ equals Rx * Ry * Rz; apply rightmost rotation first.
 x,y,z=vertex;rx,ry,rz=map(math.radians,rotation)
 x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
 x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
 y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
 return x,y,z

def vertices(model):
 for element in model['elements']:
  if 'rotation' in element:raise ValueError('Element rotation needs additional handling')
  for v in itertools.product(*zip(element['from'],element['to'])):yield v

def transformed(model,gui):
 scale=gui.get('scale',[1,1,1]);rotation=gui.get('rotation',[0,0,0]);translation=gui.get('translation',[0,0,0])
 for v in vertices(model):
  rotated=rotate(tuple((v[i]-8)*scale[i] for i in range(3)),rotation)
  yield tuple(rotated[i]+translation[i] for i in range(3))

def bounds(points):
 points=list(points)
 return [min(p[i] for p in points) for i in range(3)],[max(p[i] for p in points) for i in range(3)]

def centred_gui(model):
 old=model.get('display',{}).get('gui',{});rotation=old.get('rotation',[30,225,0])
 unit={'rotation':rotation,'scale':[1,1,1],'translation':[0,0,0]}
 lo,hi=bounds(transformed(model,unit));scale=14/max(hi[0]-lo[0],hi[1]-lo[1])
 translation=[-((lo[i]+hi[i])/2)*scale for i in range(3)]
 return {'rotation':rotation,'translation':[round(n,6) for n in translation],'scale':[round(scale,6)]*3}

def main():
 manifest=json.loads((ROOT/'tools/terminal_variants.json').read_text())
 names=['atm','cash_register']+[ref.split(':')[1] for ref in manifest['ids']]
 for name in names:
  ref='economy:item/'+name;model=resolved(ref);gui=centred_gui(model)
  path=path_for(ref);data=json.loads(path.read_text());data.setdefault('display',{})['gui']=gui
  path.write_text(json.dumps(data,indent=2)+'\n')
  lo,hi=bounds(transformed(model,gui))
  if any(abs((lo[i]+hi[i])/2)>0.0001 for i in range(2)):raise AssertionError(name+' offset')
  if max(hi[0]-lo[0],hi[1]-lo[1])>14.0001:raise AssertionError(name+' overflows')
 print(f'Centred {len(names)} block item icons; 14px maximum bounds inside a 16px slot.')

if __name__=='__main__':main()
