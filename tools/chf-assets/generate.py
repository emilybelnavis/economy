#!/usr/bin/env python3
"""Original CHF pixel artwork: exact 64px shapes and hand-authored 3x5 lettering.

SPDX-License-Identifier: CC0-1.0
Writes only chf_*.png and their item models. No external art, fonts or dependencies.
"""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/economy'
FONT = {
    '0': ('111','101','101','101','111'), '1': ('010','110','010','010','111'),
    '2': ('111','001','111','100','111'), '3': ('111','001','111','001','111'),
    '4': ('101','101','111','001','001'), '5': ('111','100','111','001','111'),
    '6': ('111','100','111','101','111'), '7': ('111','001','010','010','010'),
    '8': ('111','101','111','101','111'), '9': ('111','101','111','001','111'),
    'C': ('111','100','100','100','111'), 'H': ('101','101','111','101','101'),
    'F': ('111','100','110','100','100'), '.': ('0','0','0','0','1'),
}
NOTES = {
    10: ((35, 74, 106), (199, 226, 236), (102, 155, 181)),
    20: ((73, 87, 47), (226, 231, 187), (146, 165, 96)),
    50: ((97, 53, 106), (234, 208, 235), (179, 119, 183)),
    100: ((112, 59, 38), (243, 219, 190), (199, 132, 92)),
    200: ((28, 86, 83), (194, 229, 218), (96, 164, 148)),
    1000: ((57, 65, 95), (217, 220, 237), (131, 143, 183)),
}
COINS = {
    5: (17, (122, 76, 46), (214, 158, 102), (247, 207, 153)),
    10: (18, (83, 92, 102), (186, 197, 207), (235, 241, 244)),
    20: (19, (119, 97, 43), (213, 191, 111), (248, 231, 168)),
    50: (20, (69, 91, 108), (168, 196, 212), (221, 239, 246)),
    100: (21, (130, 94, 37), (223, 187, 91), (255, 231, 163)),
    200: (22, (76, 85, 92), (191, 199, 202), (239, 236, 201)),
    500: (23, (82, 93, 104), (184, 198, 208), (235, 245, 249)),
}


def rgba(color):
    return tuple(color) + (255,) if len(color) == 3 else tuple(color)


def canvas():
    return [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]


def rect(p, x1, y1, x2, y2, color):
    for y in range(max(0, y1), min(64, y2)):
        for x in range(max(0, x1), min(64, x2)):
            p[y][x] = rgba(color)


def line(p, x1, y1, x2, y2, color):
    dx, dy = abs(x2-x1), -abs(y2-y1)
    sx, sy = (1 if x1 < x2 else -1), (1 if y1 < y2 else -1)
    error = dx+dy
    while True:
        rect(p, x1, y1, x1+1, y1+1, color)
        if x1 == x2 and y1 == y2:
            break
        twice = 2*error
        if twice >= dy:
            error += dy
            x1 += sx
        if twice <= dx:
            error += dx
            y1 += sy


def triangle(p, x1, y1, x2, y2, x3, y3, color):
    points = [(x1,y1), (x2,y2), (x3,y3)]
    for y in range(min(y1,y2,y3), max(y1,y2,y3)+1):
        intersections = []
        for (ax,ay),(bx,by) in zip(points, points[1:]+points[:1]):
            if ay != by and min(ay,by) <= y < max(ay,by):
                intersections.append(ax+(y-ay)*(bx-ax)/(by-ay))
        if len(intersections) >= 2:
            rect(p, int(min(intersections)), y, int(max(intersections))+1, y+1, color)


def text(p, label, x, y, color, scale=1):
    for char in label:
        glyph = FONT[char]
        for gy,row in enumerate(glyph):
            for gx,value in enumerate(row):
                if value == '1':
                    rect(p, x+gx*scale, y+gy*scale, x+(gx+1)*scale, y+(gy+1)*scale, color)
        x += (len(glyph[0])+1)*scale


def width(label, scale=1):
    return (sum(len(FONT[c][0])+1 for c in label)-1)*scale


def circle(p, cx, cy, radius, color):
    for y in range(cy-radius, cy+radius+1):
        for x in range(cx-radius, cx+radius+1):
            if (x-cx)**2+(y-cy)**2 <= radius**2:
                rect(p,x,y,x+1,y+1,color)


def note(value, palette):
    dark, pale, mid = palette
    p = canvas()
    rect(p,16,2,48,62,dark)
    rect(p,17,3,47,61,pale)
    rect(p,19,5,45,59,mid)
    rect(p,20,6,44,58,pale)
    text(p,'CHF',21,8,dark)
    # A geometric foil window and serial-like security bars, not real currency features.
    triangle(p,38,15,43,20,38,25,mid)
    triangle(p,38,15,33,20,38,25,dark)
    for x in range(21,31,2):
        rect(p,x,17,x+1,20+(x%3),mid)
    # Alpine silhouettes with asymmetric snowy ridges.
    triangle(p,19,39,29,23,40,39,dark)
    triangle(p,27,39,38,28,45,39,mid)
    triangle(p,25,30,29,23,33,30,(248,247,235))
    line(p,29,25,27,31,pale)
    # Three stepped river bands cross the foreground.
    for y in (40,43,46):
        line(p,20,y,27,y+2,mid)
        line(p,27,y+2,35,y,mid)
        line(p,35,y,43,y+1,mid)
    label = str(value)
    text(p,label,32-width(label,2)//2,49,dark,2)
    # Value-specific edge indexing also distinguishes otherwise similar silhouettes.
    for n in range(list(NOTES).index(value)+1):
        rect(p,17,5+2*n,19,6+2*n,dark)
    return p


def coin(minor, specification):
    radius, edge, metal, shine = specification
    p = canvas()
    circle(p,32,32,radius,edge)
    circle(p,32,32,radius-1,shine)
    circle(p,32,32,radius-3,metal)
    circle(p,32,32,radius-5,shine if minor == 200 else metal)
    # Broken upper arc and lower rim give pixel relief without realistic engraving.
    for dx in range(-radius+6,radius-5,3):
        y = 32-int(((radius-2)**2-dx*dx)**0.5)
        rect(p,32+dx,y,33+dx,y+1,edge)
    text(p,'CHF',32-width('CHF')//2,32-radius+6,edge)
    triangle(p,24,31,31,25,39,31,edge)
    line(p,25,31,31,27,shine)
    label = f'{minor/100:.2f}' if minor < 100 else str(minor//100)
    scale = 1 if minor < 100 else 2
    text(p,label,32-width(label,scale)//2,35,edge,scale)
    return p


def png(path, pixels):
    def chunk(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
    raw = b''.join(b'\0'+bytes(channel for pixel in row for channel in pixel) for row in pixels)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))
                     +chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))


def main():
    for kind, values, render in [('bill',NOTES,note),('coin',COINS,coin)]:
        for value,specification in values.items():
            item = f'chf_{kind}_{value}'
            png(ASSETS/'textures/item'/f'{item}.png',render(value,specification))
            model = {'parent':'minecraft:item/generated','textures':{'layer0':f'economy:item/{item}'}}
            target = ASSETS/'models/item'/f'{item}.json'
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(json.dumps(model,indent=2)+'\n')


if __name__ == '__main__':
    main()
