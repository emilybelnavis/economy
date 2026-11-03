#!/usr/bin/env python3
"""Generate original pixel-art collectible sprites/models, without external dependencies.
Run from any directory: python3 tools/generate_collectibles.py
Textures/models are generated in src/generated/resources; names merge into authored en_us.
No real banknote scans, payment-network marks, or upstream artwork are used.
"""
from pathlib import Path
import json, math, struct, zlib
ROOT=Path(__file__).resolve().parents[1]
CATALOG=json.loads((ROOT/'tools/collectibles.json').read_text())
OUT=ROOT/'src/generated/resources'
FONT={
'0':['111','101','101','101','111'],'1':['010','110','010','010','111'],'2':['110','001','010','100','111'],'3':['110','001','010','001','110'],'4':['101','101','111','001','001'],'5':['111','100','110','001','110'],'6':['011','100','111','101','111'],'7':['111','001','010','010','010'],'8':['111','101','111','101','111'],'9':['111','101','111','001','110'],
'A':['010','101','111','101','101'],'B':['110','101','110','101','110'],'C':['011','100','100','100','011'],'D':['110','101','101','101','110'],'E':['111','100','110','100','111'],'F':['111','100','110','100','100'],'G':['011','100','101','101','011'],'H':['101','101','111','101','101'],'I':['111','010','010','010','111'],'J':['001','001','001','101','010'],'K':['101','101','110','101','101'],'L':['100','100','100','100','111'],'M':['101','111','111','101','101'],'N':['101','111','111','111','101'],'O':['010','101','101','101','010'],'P':['110','101','110','100','100'],'Q':['010','101','101','111','011'],'R':['110','101','110','101','101'],'S':['011','100','010','001','110'],'T':['111','010','010','010','010'],'U':['101','101','101','101','111'],'V':['101','101','101','101','010'],'W':['101','101','111','111','101'],'X':['101','101','010','101','101'],'Y':['101','101','010','010','010'],'Z':['111','001','010','100','111'],
'-':['000','000','111','000','000'],'.':['000','000','000','000','010'],' ':['000']*5}
def rgb(s):return tuple(bytes.fromhex(s))
def mix(c,other,t):return tuple(round(a*(1-t)+b*t) for a,b in zip(c,other))
class Canvas:
 def __init__(self,w=64,h=64,bg=(0,0,0,0)):self.w=w;self.h=h;self.p=[[bg]*w for _ in range(h)]
 def point(self,x,y,c):
  x,y=int(x),int(y)
  if 0<=x<self.w and 0<=y<self.h:self.p[y][x]=tuple(c) if len(c)==4 else tuple(c)+(255,)
 def rect(self,x,y,X,Y,c):
  for j in range(int(y),int(Y)):
   for i in range(int(x),int(X)):self.point(i,j,c)
 def line(self,x,y,X,Y,c):
  steps=max(abs(X-x),abs(Y-y),1)
  for i in range(int(steps)+1):self.point(round(x+(X-x)*i/steps),round(y+(Y-y)*i/steps),c)
 def ellipse(self,x,y,X,Y,c):
  cx,cy=(x+X)/2,(y+Y)/2;rx,ry=(X-x)/2,(Y-y)/2
  for j in range(int(y),int(Y)+1):
   for i in range(int(x),int(X)+1):
    if ((i-cx)/max(rx,.1))**2+((j-cy)/max(ry,.1))**2<=1:self.point(i,j,c)
 def polygon(self,points,c):
  for y in range(int(min(p[1] for p in points)),int(max(p[1] for p in points))+1):
   cuts=[]
   for a,b in zip(points,points[1:]+points[:1]):
    if min(a[1],b[1])<=y<max(a[1],b[1]):cuts.append(a[0]+(y-a[1])*(b[0]-a[0])/(b[1]-a[1]))
   cuts.sort()
   for a,b in zip(cuts[::2],cuts[1::2]):self.rect(math.ceil(a),y,math.floor(b)+1,y+1,c)
 def text(self,s,x,y,c,scale=1):
  for char in s.upper():
   for j,row in enumerate(FONT.get(char,FONT[' '])):
    for i,v in enumerate(row):
     if v=='1':self.rect(x+i*scale,y+j*scale,x+(i+1)*scale,y+(j+1)*scale,c)
   x+=4*scale
 def paste(self,other,x,y,scale=1):
  for j,row in enumerate(other.p):
   for i,col in enumerate(row):
    if col[3]:self.rect(x+i*scale,y+j*scale,x+(i+1)*scale,y+(j+1)*scale,col)
 def save(self,p):
  p.parent.mkdir(parents=True,exist_ok=True)
  def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
  raw=b''.join(b'\0'+bytes(sum(row,())) for row in self.p)
  p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
def motif(c,code,x,y,color,light):
 if code=='eur':
  # Three bridge arches and a river.
  c.rect(x,y+2,x+19,y+13,color)
  for n in range(3):c.ellipse(x+2+n*6,y+6,x+6+n*6,y+16,light)
  c.line(x-1,y+1,x+20,y+1,color);c.line(x,y+16,x+20,y+16,color)
 elif code=='hkd':
  for dx,w,h in [(0,4,10),(5,5,17),(11,4,13),(16,4,20)]:
   c.rect(x+dx,y+20-h,x+dx+w,y+20,color)
   for yy in range(y+22-h,y+18,3):c.line(x+dx+1,yy,x+dx+w-2,yy,light)
  c.line(x-1,y+21,x+21,y+21,color)
 elif code=='jpy':
  c.polygon([(x,y+20),(x+11,y+3),(x+22,y+20)],color)
  c.polygon([(x+7,y+9),(x+11,y+3),(x+15,y+9),(x+12,y+8),(x+10,y+10)],light)
  for dx,dy in [(1,3),(5,1),(7,5),(3,8)]:c.ellipse(x+dx,y+dy,x+dx+3,y+dy+3,color)
 elif code=='cny':
  c.polygon([(x,y+20),(x+7,y+9),(x+13,y+17),(x+20,y+5),(x+23,y+20)],mix(color,light,.5))
  for dy,w in [(4,7),(10,9),(16,11)]:
   c.polygon([(x+9,y+dy-3),(x+9+w//2,y+dy),(x+9-w//2,y+dy)],color)
   c.rect(x+7,y+dy,x+12,y+dy+3,color)
 elif code=='dkk':
  c.rect(x+4,y+2,x+6,y+19,color);c.rect(x+16,y+2,x+18,y+19,color)
  c.line(x,y+15,x+22,y+15,color)
  for dx in range(1,22):
   yy=y+5+round(7*math.sin((dx-5)*math.pi/24)**2)
   c.point(x+dx,yy,color);c.line(x+dx,yy,x+dx,y+15,color) if dx%3==0 else None
  c.line(x,y+21,x+22,y+21,color)
 elif code=='gbp':
  c.polygon([(x,y+5),(x+4,y+8),(x+9,y),(x+14,y+8),(x+18,y+5),(x+15,y+14),(x+3,y+14)],color)
  c.rect(x+3,y+15,x+16,y+17,color);c.rect(x+8,y+7,x+11,y+12,light)
 elif code=='aud':
  for dx,dy,r in [(3,3,3),(13,4,2),(7,13,3),(17,13,2)]:
   c.line(x+dx-r,y+dy,x+dx+r,y+dy,color);c.line(x+dx,y+dy-r,x+dx,y+dy+r,color)
  c.line(x+2,y+18,x+17,y+7,color)
 elif code=='nzd':
  c.line(x+3,y+18,x+15,y,color)
  for n in range(1,7):
   X=x+4+n;Y=y+17-n*2;c.line(X,Y,X-5,Y-2,color);c.line(X,Y,X+5,Y+1,color)
 elif code=='cad':
  pts=[(9,0),(12,5),(15,3),(15,8),(20,7),(17,13),(21,14),(11,17),(10,21),(9,17),(0,14),(4,12),(1,7),(6,8),(5,3),(8,5)]
  c.polygon([(x+a,y+b) for a,b in pts],color)
 else:
  c.polygon([(x,y+5),(x+8,y+8),(x+10,y+2),(x+12,y+8),(x+21,y+5),(x+16,y+12),(x+11,y+17),(x+5,y+12)],color)
  c.rect(x+9,y+8,x+12,y+13,light)
def bill(series,item):
 c=Canvas();code=series['code'];base=rgb(item['color']);dark=mix(base,(20,30,35),.65);light=mix(base,(255,249,222),.7)
 if code=='cad' and item.get('orientation', 'vertical')=='vertical':
  c.rect(17,2,47,62,dark);c.rect(18,3,46,61,light);c.rect(19,4,45,60,base)
  for y in range(7,57,4):c.line(19,y,44,y+3,mix(base,light,.4))
  c.rect(37,4,43,60,mix(base,(194,237,227),.75));c.rect(39,17,42,44,(0,0,0,0))
  c.text('CAD',21,7,dark);motif(c,'cad',20,21,dark,light)
  value=str(item['value']);c.text(value,21,46,dark,2 if len(value)<=2 else 1);c.text(value,21,54,light)
 else:
  c.rect(2,16,62,48,dark);c.rect(3,17,61,47,light);c.rect(4,18,60,46,base)
  for y in range(20,45,3):
   for x in range(5,59):
    yy=y+round(1.5*math.sin((x+y)*.45));c.point(x,yy,mix(base,light,.27))
  if code in ('aud','nzd'):
   c.rect(24,18,30,46,mix(base,(201,237,222),.8));c.rect(26,21,28,29,(0,0,0,0));c.rect(26,34,28,43,(0,0,0,0))
  elif code=='cad':
   c.rect(28,18,34,46,mix(base,(194,237,227),.75));c.rect(30,21,32,29,(0,0,0,0));c.rect(30,34,32,43,(0,0,0,0))
  elif code=='eur':c.rect(28,18,31,46,mix(base,(213,219,188),.7))
  elif code=='usd':
   c.rect(5,19,59,20,dark);c.rect(5,44,59,45,dark);c.ellipse(7,22,27,43,light)
  c.text(code.upper(),6,20,dark);motif(c,code,6,24 if code=='cad' else 26,dark,light)
  value=str(item['value']);sc=1 if len(value)>3 else 2;cx=58-len(value)*4*sc
  c.rect(cx-1,27,59,40,light);c.text(value,cx,28,dark,sc)
  c.text(value,6,40,dark);c.text('ECO',45,41,dark)
 return c
def coin(series,item,index):
 if series['code'] in ('hkd','jpy','cny','dkk'):return new_coin(series,item,index)
 c=Canvas();code=series['code'];n=item['minor_units'];r=21+(index%4)
 gold=rgb('d9b75e');silver=rgb('b9c8cf');copper=rgb('c88a62')
 metal=copper if n<10 and code in ('eur','gbp','usd') else gold if n>=100 or code=='eur' and n>=10 else silver
 dark=mix(metal,(35,32,34),.65);light=mix(metal,(255,255,240),.55)
 sides=7 if code=='gbp' and n in (20,50) else 12 if code=='aud' and n==50 else 0
 def shape(rad,color):
  if sides:c.polygon([(32+rad*math.cos(i*2*math.pi/sides-math.pi/2),32+rad*math.sin(i*2*math.pi/sides-math.pi/2)) for i in range(sides)],color)
  else:c.ellipse(32-rad,32-rad,32+rad,32+rad,color)
 shape(r+1,dark);shape(r,light);shape(r-2,metal);shape(r-4,dark)
 center=metal
 if (code in ('eur','gbp') and n>=100) or code=='cad' and n==200:center=silver if metal==gold else gold
 shape(r-5,center)
 for i in range(20):
  a=i*math.pi/10;c.point(32+(r-3)*math.cos(a),32+(r-3)*math.sin(a),dark)
 c.text(code.upper(),27,17,dark)
 label=item['label'];sc=3 if len(label)==1 else 2;c.text(label,32-(len(label)*4-1)*sc//2,25,dark,sc)
 c.text(item['unit'] if n<100 else 'ECO',32-(len(item['unit'] if n<100 else 'ECO')*4-1)//2,42,dark)
 # Each set has a different tiny mint mark.
 mark={'eur':0,'gbp':1,'aud':2,'nzd':3,'cad':4,'usd':5}[code]
 for i in range(mark+1):c.point(27+i*2,48,light)
 return c
def card(item,index):
 if item['kind']=='credit':return credit_card(item)
 c=Canvas();base=rgb(item['color']);dark=mix(base,(4,15,23),.6);light=mix(base,(238,245,229),.6)
 c.rect(3,14,61,50,dark);c.rect(4,13,60,51,dark);c.rect(4,14,60,50,base)
 for y in range(15,49):
  for x in range(5,59):
   if item['kind']=='credit':pattern=(x+y+index*3)//7%3==0
   else:pattern=(x//5+y//5+index)%4==0
   if pattern:c.point(x,y,mix(base,light,.18))
 c.text(item['issuer'].upper(),7,17,light)
 chip=rgb('d3b776');c.rect(8,27,18,35,chip);c.rect(10,28,16,34,rgb('e7d194'));c.line(8,31,17,31,rgb('997c47'));c.line(12,27,12,34,rgb('997c47'))
 # Contactless arcs and fictional geometric issuer emblem.
 for dx in (0,3,6):c.line(24+dx,28,26+dx,30,light);c.line(26+dx,30,26+dx,32,light);c.line(26+dx,32,24+dx,34,light)
 issuer=item['issuer']
 if issuer=='summit':
  c.polygon([(42,37),(47,26),(51,32),(54,28),(58,37)],light);c.polygon([(46,30),(47,27),(49,31)],base)
 elif issuer=='meridian':
  c.ellipse(43,26,57,39,light);c.rect(50,25,58,40,base);c.line(50,26,50,39,light)
 elif issuer=='aurora':
  for X,Y in [(46,29),(54,34)]:c.polygon([(X,Y-5),(X+2,Y-1),(X+5,Y),(X+2,Y+1),(X,Y+5),(X-2,Y+1),(X-5,Y),(X-2,Y-1)],light)
 elif issuer=='obsidian':
  for X in [44,49,54]:c.polygon([(X,28),(X+2,26),(X+3,35),(X+1,38)],light)
 elif issuer=='harbor':
  c.line(50,27,50,38,light);c.line(45,30,55,30,light);c.line(43,34,46,38,light);c.line(46,38,54,38,light);c.line(54,38,57,34,light);c.ellipse(48,25,52,29,light)
 elif issuer=='meadow':
  c.polygon([(43,38),(44,28),(56,25),(55,35),(48,39)],light);c.line(44,38,54,28,base)
 elif issuer=='tide':
  for Y in [28,33,38]:
   c.line(42,Y,46,Y-2,light);c.line(46,Y-2,53,Y+1,light);c.line(53,Y+1,57,Y-1,light)
 else:
  c.polygon([(43,35),(45,29),(48,32),(52,24),(56,34),(54,39),(46,39)],light);c.polygon([(48,37),(51,31),(53,37)],base)
 c.text('0000',8,38,light);c.text(item['kind'].upper(),8,44,light)
 c.rect(52,44,56,46,chip)
 return c

def new_coin(series,item,index):
 c=Canvas();code=series['code'];n=item['minor_units'];r=22+index%3
 metal=rgb('c0cbd0')
 if code=='jpy' and n in (5,500) or code=='dkk' and n>=1000:metal=rgb('ccb477')
 if code=='jpy' and n==10 or code=='dkk' and n==50:metal=rgb('bd8965')
 if code=='cny' and n==50:metal=rgb('d6b45d')
 dark=mix(metal,(30,32,36),.65);light=mix(metal,(255,251,229),.6)
 for rr,col in [(r+1,dark),(r,light),(r-2,metal),(r-4,dark),(r-5,metal)]:c.ellipse(32-rr,32-rr,32+rr,32+rr,col)
 for i in range(24):
  a=i*math.pi/12;c.point(32+(r-3)*math.cos(a),32+(r-3)*math.sin(a),dark)
 c.text(code,27,15,dark)
 hole=code=='jpy' and n in (5,50) or code=='dkk' and n in (100,200,500)
 label=item['label'];sc=2
 c.text(label,32-(len(label)*4-1)*sc//2,23 if hole else 26,dark,sc)
 if hole:
  c.ellipse(28,35,36,43,dark);c.ellipse(29,36,35,42,(0,0,0,0))
 c.text(item['unit'],32-(len(item['unit'])*4-1)//2,46 if hole else 42,dark)
 return c

def credit_card(item):
 c=Canvas();issuer=item['issuer'];navy=rgb('182d56');silver=rgb('c8d7e2');gold=rgb('d7b869')
 base={'summit':navy,'meridian':rgb('242b36'),'aurora':rgb('b9d5e3'),'obsidian':rgb('292e36')}[issuer]
 ink=navy if issuer=='aurora' else silver
 c.rect(3,14,61,50,mix(base,(5,12,20),.6));c.rect(4,13,60,51,mix(base,(5,12,20),.6));c.rect(4,14,60,50,base)
 if issuer=='summit':
  c.rect(4,38,60,42,gold);c.rect(4,43,60,47,navy)
  c.polygon([(42,33),(47,24),(51,30),(55,23),(58,33)],silver)
 elif issuer=='meridian':
  c.ellipse(39,25,50,37,rgb('d76c49'));c.ellipse(47,25,58,37,rgb('e7ad54'));c.ellipse(47,28,50,34,rgb('e88b43'))
 elif issuer=='aurora':
  for inset in (5,7):
   col=silver if inset==5 else navy
   c.line(inset,15+inset-5,63-inset,15+inset-5,col);c.line(inset,48-inset+5,63-inset,48-inset+5,col)
   c.line(inset,15+inset-5,inset,48-inset+5,col);c.line(63-inset,15+inset-5,63-inset,48-inset+5,col)
  c.ellipse(40,23,56,40,silver);c.polygon([(48,24),(51,29),(55,32),(51,35),(48,39),(45,35),(41,32),(45,29)],navy);c.rect(47,28,50,36,silver)
 else:
  c.ellipse(43,27,58,42,rgb('e5a15a'));c.rect(40,36,59,43,base)
  c.polygon([(35,42),(58,32),(56,37),(43,42)],silver)
 c.text(issuer,9,18,ink)
 c.rect(9,27,18,34,gold);c.line(9,30,17,30,rgb('9c824c'));c.line(13,27,13,33,rgb('9c824c'))
 c.text('0000',9,38,ink);c.text('CREDIT',9,44,ink)
 return c

def generate():
 sprites={};names={}
 for item in CATALOG['cards']:sprites[item['id']]=card(item,len(sprites));names[item['id']]=item['name']
 for series in CATALOG['series']:
  for index,item in enumerate(series['coins']):sprites[item['id']]=coin(series,item,index);names[item['id']]=item['name']
  for item in series['notes']:sprites[item['id']]=bill(series,item);names[item['id']]=item['name']
 for name,image in sprites.items():
  image.save(OUT/('assets/economy/textures/item/'+name+'.png'))
  p=OUT/('assets/economy/models/item/'+name+'.json');p.parent.mkdir(parents=True,exist_ok=True)
  p.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'economy:item/'+name}},indent=2)+'\n')
 langp=ROOT/'src/main/resources/assets/economy/lang/en_us.json';langp.parent.mkdir(parents=True,exist_ok=True)
 lang=json.loads(langp.read_text()) if langp.exists() else {}
 lang.update({'item.economy.'+key:name for key,name in names.items()});lang['tooltip.economy.collectible']='Collectible item — functionality coming later.'
 langp.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n')
 # Contact sheet made from the exact in-game textures, not a concept image.
 sheet=Canvas(1500,252+sum(380 if any(n.get('orientation')=='horizontal' for n in series['notes']) else 232 for series in CATALOG['series'])+50,(231,235,229,255));ink=rgb('243b42');muted=rgb('586969')
 sheet.text('ECONOMY - CARDS AND CURRENCY',38,28,ink,4)
 sheet.text('4 CREDIT CARDS',38,76,muted,2);sheet.text('4 BANK CARDS',780,76,muted,2)
 for i,item in enumerate(CATALOG['cards']):
  x=35+i*182;sheet.paste(sprites[item['id']],x,90,2);sheet.text(item['issuer'],x+8,198,ink,2)
 y=252
 for series in CATALOG['series']:
  sheet.text(series['code'].upper()+' - '+series['name'].upper(),38,y,ink,2)
  for j,item in enumerate([n for n in series['notes'] if n.get('orientation')!='horizontal']):sheet.paste(sprites[item['id']],60+j*175,y+10,2)
  for j,item in enumerate(series['coins']):sheet.paste(sprites[item['id']],60+j*175,y+150,1)
  horizontal=[n for n in series['notes'] if n.get('orientation')=='horizontal']
  if horizontal:
   sheet.text('HORIZONTAL CAD',38,y+225,muted,2)
   for j,item in enumerate(horizontal):sheet.paste(sprites[item['id']],60+j*175,y+215,2)
  y+=380 if horizontal else 232
 sheet.save(ROOT/'art/collectibles-preview.png')
 print(f'Generated {len(sprites)} sprites and models; preview in art/collectibles-preview.png')
if __name__=='__main__':generate()
