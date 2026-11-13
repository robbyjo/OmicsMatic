#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create explicit synthetic H-DAB controls; these are not recovered paper inputs or settings."""
from pathlib import Path
import argparse,copy,json,math,struct,zlib

def png(path,blank=False):
    width=height=256;raw=bytearray();h=(.65111,.70119,.29049);dab=(.26917,.56824,.77759)
    for y in range(height):
        raw.append(0)
        for x in range(width):
            nuclear=0.;cyto=0.
            if not blank:
                ix=min(3,max(0,int(x//64)));iy=min(3,max(0,int(y//64)));cx=32+64*ix;cy=32+64*iy;distance=(x-cx)**2+(y-cy)**2
                if distance<=100:nuclear=1.1
                if distance<=400:cyto=(.03,.12,.20,.32)[(ix+iy)%4]
            raw.extend(max(0,min(255,int(round(255*10**(-a*nuclear-b*cyto))))) for a,b in zip(h,dab,strict=True))
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(raw),9))+chunk(b'IEND',b''))

def create(out):
    out.mkdir(parents=True,exist_ok=False);png(out/'cells.png');png(out/'blank.png',True)
    params=dict(detectionImageBrightfield='Hematoxylin OD',requestedPixelSizeMicrons=.5,backgroundRadiusMicrons=8.,backgroundByReconstruction=True,
        medianRadiusMicrons=0.,sigmaMicrons=1.5,minAreaMicrons=10.,maxAreaMicrons=200.,threshold=.1,maxBackground=2.,watershedPostProcess=True,
        excludeDAB=False,cellExpansionMicrons=4.,includeNuclei=True,smoothBoundaries=True,makeMeasurements=True,
        thresholdCompartment='Cytoplasm: DAB OD mean',thresholdPositive1=.08,thresholdPositive2=.16,thresholdPositive3=.24,singleThreshold=False)
    jobs=[]
    for name,image,roi,changes in [('all-16','cells.png',[0,0,256,256],{}),('blank','blank.png',[0,0,256,256],{}),
        ('left-half','cells.png',[0,0,128,256],{}),('nucleus-score','cells.png',[0,0,256,256],dict(thresholdCompartment='Nucleus: DAB OD mean',cellExpansionMicrons=0.)),
        ('unsmoothed','cells.png',[0,0,256,256],dict(smoothBoundaries=False)),('binary-threshold','cells.png',[0,0,256,256],dict(singleThreshold=True))]:
        p=copy.deepcopy(params);p.update(changes);jobs.append(dict(id=name,image=image,roi=roi,pixel_size_microns=.5,parameters=p))
    settings=dict(stains={'Name':'H-DAB explicit synthetic fixture','Stain 1':'Hematoxylin','Values 1':'0.65111 0.70119 0.29049','Stain 2':'DAB','Values 2':'0.26917 0.56824 0.77759','Background':'255 255 255'},jobs=jobs,
        source_scope='All images, ROIs, stains, parameters and fixture UUIDs are explicit synthetic controls. Numeric stain vectors and author ROIs are not recovered from P040.')
    (out/'settings.json').write_text(json.dumps(settings,indent=2)+'\n',encoding='utf8',newline='\n');return settings

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();create(args.out.resolve());print('Created six complete synthetic QuPath jobs',flush=True)
