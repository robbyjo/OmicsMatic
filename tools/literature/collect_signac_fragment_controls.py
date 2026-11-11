# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain full original package fragment controls before implementing native source contracts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess
import urllib.request
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def cases():
    rng=random.Random(731940)
    base=[('chr1',0,1,'A',7),('chr1',99,100,'A',2),('chr1',100,200,'A',3),('chr1',100,200,'A',9),('chr1',200,201,'B',1),('chr1',250,397,'A',1),('chr1',400,548,'B',2),('chr1',600,893,'A',1),('chr1',900,1194,'B',1),('chr1',1200,1346,'C',5),('chr1',1400,1695,'C',2),('chr2',5,152,'C',4),('chr2',300,301,'D',1),('chrM',10,110,'M',1)]
    second=[('chr1',50,196,'A',1),('chr1',100,248,'B',2),('chr1',200,493,'D',4),('chr2',600,601,'E',8)]
    randoms=[]
    for chromosome in ['chr1','chr2']:
        for k in range(180):
            start=rng.randrange(0,4000);randoms.append((chromosome,start,start+rng.choice([1,2,146,147,148,200,293,294,295,500]),rng.choice(['A','B','C','D']),rng.randrange(0,16)))
    randoms.sort(key=lambda r:(r[0],r[1]))
    files=[dict(id='boundary',records=base),dict(id='second',records=second),dict(id='random',records=randoms),dict(id='six-column',records=[(*r,'+' if k%2==0 else '-') for k,r in enumerate(base)]),dict(id='only-boundary',records=[('chr1',10,157,'A',1),('chr1',200,494,'B',1)])]
    count_cases=[]
    for file in files:
        for cells in [None,['Z','C','A','C']]:
            for lines in [0,1,2,5,10,999]:count_cases.append(dict(id=f"{file['id']}-{'all' if cells is None else 'whitelist'}-{lines}",files=[file['id']],cells=cells,lines=lines))
    for lines in [0,1,5]:count_cases.append(dict(id=f'pooled-{lines}',files=['boundary','second'],cells=None,lines=lines))
    regions=[dict(chromosome=c,start=s,end=e) for c,s,e in [('chr1',1,1),('chr1',100,100),('chr1',101,200),('chr1',200,200),('chr1',201,201),('chr1',99,101),('chr1',100,201),('chr1',1,2000),('chr1',3000,3100),('chr2',1,500),('chrM',1,100),('chrAbsent',10,20)]]
    mapping=[dict(cell='object-B',barcode='B'),dict(cell='object-A',barcode='A'),dict(cell='object-Z',barcode='Z')]
    matrix_cases=[]
    for file in ['boundary','six-column','random']:
        for mode in ['discover','explicit','mapped']:
            for keep in [False,True]:
                for chunk in [1,4,2000]:
                    cells=None if mode=='discover' else ['Z','C','A'] if mode=='explicit' else ['object-Z','object-A','object-B']
                    matrix_cases.append(dict(id=f'{file}-{mode}-{keep}-{chunk}',sources=[dict(file=file,map=mapping if mode=='mapped' else None)],regions=regions,cells=cells,keep=keep,chunk=chunk,
                                             object_cells=(['object-Z','object-A','object-B','not-mapped'] if mode=='mapped' else ['Z','C','A','B','not-mapped']) if chunk==2000 and keep else None,nucleosome_lines=[0,1,5]))
    for keep in [False,True]:
        matrix_cases.append(dict(id=f'multi-disjoint-{keep}',sources=[dict(file='boundary',map=[dict(cell='s1-A',barcode='A'),dict(cell='s1-B',barcode='B')]),dict(file='second',map=[dict(cell='s2-A',barcode='A'),dict(cell='s2-D',barcode='D')])],regions=regions,cells=['s2-D','s1-A','s2-A','s1-B','unknown'],keep=keep,chunk=4,object_cells=['s2-D','s1-A','s2-A','s1-B','unknown'],nucleosome_lines=[0,2]))
    def edge(name,sources,regions=regions,cells=None,keep=False,chunk=2000,object_cells=None):matrix_cases.append(dict(id=name,sources=sources,regions=regions,cells=cells,keep=keep,chunk=chunk,object_cells=object_cells,nucleosome_lines=[0,1]))
    source=[dict(file='boundary',map=None)]
    empty=[dict(chromosome='chr1',start=5000,end=5100)]
    edge('no-overlap-discover',source,regions=empty)
    edge('no-overlap-explicit',source,regions=empty,cells=['Z','A'])
    edge('no-common-chromosome',source,regions=[dict(chromosome='chrAbsent',start=1,end=2)],cells=['A'],keep=True)
    edge('mapping-filter', [dict(file='boundary',map=[dict(cell='left',barcode='A')]),dict(file='second',map=[dict(cell='right',barcode='B')])],cells=['right'])
    edge('shared-cell-quantification',[dict(file='boundary',map=[dict(cell='same',barcode='A')]),dict(file='second',map=[dict(cell='same',barcode='A')])],cells=['same'])
    edge('omitted-requested',[dict(file='boundary',map=[dict(cell='A',barcode='A')])],cells=['Z','A'])
    edge('empty-chunks',source,regions=[*empty,dict(chromosome='chr1',start=5200,end=5201),dict(chromosome='chrAbsent',start=1,end=2)],keep=True,chunk=1)
    edge('undefined-nucleosome',[dict(file='only-boundary',map=None)],object_cells=['A','B'])
    return dict(files=files,count_cases=count_cases,matrix_cases=matrix_cases)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-fragment-controls-in-progress'))
    try:
        data=cases();save(out/'cases.json',data)
        for file in data['files']:(out/(file['id']+'.tsv')).write_text('# synthetic fragment controls\n'+''.join('\t'.join(map(str,r))+'\n' for r in file['records']),encoding='utf8',newline='\n')
        names=['tools/literature/collect_signac_fragment_controls.R','tools/literature/collect_signac_fragment_controls.py']
        pins={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',pins)
        for n in names:
            target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
        request=urllib.request.Request('https://svn.r-project.org/R/trunk/src/nmath/fround.c',headers={'User-Agent':'OmicsMatic scientific source audit'});raw=urllib.request.urlopen(request,timeout=60).read();(out/'original-fround.c').write_bytes(raw)
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        cmd=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/names[0]),str(out/'cases.json'),str(out),str(out/'original.json')]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:r=subprocess.run(cmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300,check=False)
        save(out/'original.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0
        result=json.loads((out/'original.json').read_bytes());assert len(result['counts'])==len(data['count_cases']) and len(result['quantification'])==len(data['matrix_cases'])
        assert all(pin(ROOT/n)==v for n,v in pins.items())
        save(out/'COMPLETED.json',dict(status=result['status'],source_pins=pins,cases=pin(out/'cases.json'),original=pin(out/'original.json'),rounding=pin(out/'rounding.tsv'),fround_source=pin(out/'original-fround.c'),count_cases=len(result['counts']),matrix_cases=len(result['quantification']),nucleosome_cases=len(result['nucleosome']),historical_binaries_executed=False))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],count_cases=len(result['counts']),matrix_cases=len(result['quantification']),nucleosome_cases=len(result['nucleosome']))))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_FRAGMENT_CONTROL_COLLECTION',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable original control checks')
    main()
