#!/usr/bin/env python3
"""Create small region-annotation examples from the locally pinned resources.

These are software fixtures, not study measurements or biological discoveries.
No resource download, reference aliasing or annotation rebuilding occurs.
"""
from pathlib import Path
import argparse
import json
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
import install_homer_named as named


def prepare(resources,out):
    resources=named.core.private(resources);out=named.core.private(out)
    if out.exists():raise ValueError('Example output exists; choose NEW directory')
    pins=named.resource_files(resources);out.mkdir(parents=True);cases=[]
    for genome,kind,stats in [('mm10','homer','omit'),('hg38','bed6','original-query-contigs')]:
        root=resources/'data/genomes'/genome;lengths={f[0]:int(f[1]) for f in [s.split('\t') for s in (root/'chrom.sizes').read_text().splitlines()]};selected={}
        with (root/(genome+'.full.annotation')).open() as source:
            for line in source:
                f=line.rstrip('\n').split('\t');a,b=int(f[2]),int(f[3]);category=f[5]
                if category in ['N','LINE','SINE','LTR','P','E','I'] and category not in selected and f[1] in lengths:
                    center=(a+b)//2
                    if a<=center-10 and center+10<=min(b,lengths[f[1]]) and center-10>=1:selected[category]=(f[1],center-10,center+10,'+' if f[4] in ['0','+'] else '-',category)
                if len(selected)==7:break
        if len(selected)!=7:raise ValueError('Pinned example categories incomplete')
        regions=[selected[k] for k in ['N','LINE','SINE','LTR','P','E','I']];chosen=0
        with (root/(genome+'.tss')).open() as source:
            for line in source:
                f=line.rstrip('\n').split('\t');anchor=int(f[2])+2000 if f[4] in ['0','+'] else int(f[3])-2000
                if '-HOMER' not in f[0] or f[1] not in lengths or not 18<=anchor<=lengths[f[1]]-17:continue
                for offset in [-17,0,17]:
                    for strand in ['+','-']:regions.append((f[1],anchor+offset,anchor+offset,strand,'raw-HOMER-ID-anchor'))
                chosen+=1
                if chosen==2:break
        for contig in ['chr1','chrM']:
            if contig in lengths:
                for point in [1,lengths[contig]]:regions.append((contig,point,point,'-','reference-boundary'))
        # A repeated Unicode ID and unknown strand exercise explicit ordinal/plus policies.
        regions.append((*regions[0][:3],'.','duplicate-source-ID-with-dot-strand'));name=genome+('.bed' if kind=='bed6' else '.pos');lines=[]
        for i,(contig,start,end,strand,role) in enumerate(regions):
            identity='example-α' if i in [0,len(regions)-1] else 'example-'+str(i+1)
            fields=[contig,start-1,end,identity,'1',strand] if kind=='bed6' else [identity,contig,start,end,strand,'1','0.25']
            lines.append('\t'.join(map(str,fields)))
        (out/name).write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
        cases.append(dict(genome=genome,peak_format=kind,peaks=name,annotation_stats=stats,records=len(regions),roles=[r[4] for r in regions]))
    named.core.write(out/'fixtures.json',dict(status='software-fixtures-derived-from-explicit-pinned-resources',cases=cases,resource_sha256=pins,scope='Seven feature categories, raw-HOMER TSS anchors with both query strands, reference endpoints and retained duplicate/dot-strand input. No paper measurements or biological inference.'))
    return cases


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--resource-root',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();print(json.dumps(prepare(a.resource_root,a.out)))
