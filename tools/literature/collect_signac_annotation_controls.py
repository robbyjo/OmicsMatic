# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original TSS enrichment, extension, annotation collapse and gene activity evidence."""
import argparse
import json
import os
from pathlib import Path
import random
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def region(c,s,e=None,strand='+'):return dict(chromosome=c,start=s,end=s if e is None else e,strand=strand)
def recipes():
    records=[]
    for anchor in [2000,2500,3000,3500,4000]:
        for delta in range(-3,4):
            start=anchor+delta;records.append(('chr1',start,start+1,'A' if delta%2==0 else 'B',1+abs(delta)))
            records.append(('chr1',start-20,start,'C',17))
    records.extend([('chr1',1,6000,'D',5),('chr1',2999,3001,'D',0),('chr1',10,11,'A',1),('chr2',3000,3001,'B',7),('chrM',3000,3001,'M',4),('MT',3000,3001,'M',1)])
    records.sort(key=lambda v:(v[0],v[1]));rng=random.Random(63920);randoms=[]
    for chromosome in ['chr1','chr2']:
        for k in range(180):
            start=rng.randrange(1,7000);randoms.append((chromosome,start,start+rng.choice([1,2,20,100,500,2000]),rng.choice(['A','B','C','D']),rng.randrange(0,20)))
    randoms.sort(key=lambda v:(v[0],v[1]));files=[dict(id='endpoints',records=records),dict(id='random',records=randoms),dict(id='center-only',records=[('chr1',2999,3000,'A',4)]),dict(id='second',records=[('chr1',1999,2002,'A',3),('chr1',2999,3002,'B',1),('chr2',3999,4002,'A',5)]),dict(id='chr1-only',records=[row for row in records if row[0]=='chr1']),dict(id='chr2-only',records=[('chr2',2999,3000,'A',4),('chr2',3999,4002,'A',5)])]
    positions=[region('chr1',3000),region('chr2',4000,strand='-'),region('chr1',3500,3502,'*'),region('chrAbsent',2000),region('chrM',3000),region('MT',3000)]
    cases=[]
    for file in ['endpoints','random','center-only']:
        for fast in [True,False]:
            for chunk in [1,4,2000]:cases.append(dict(id=f'{file}-{fast}-{chunk}',sources=[dict(file=file,map=None)],cells=['D','B','A','C','Z'],positions=positions,fast=fast,process_n=chunk,extension=1000,n=None))
    def tss(name,**kwargs):
        case=dict(id=name,sources=[dict(file='endpoints',map=None)],cells=['D','B','A','C','Z'],positions=positions,fast=True,process_n=2000,extension=1000,n=None);case.update(kwargs);cases.append(case)
    for fast in [True,False]:
        for extension in [50,500,501,1500]:tss(f'extension-{fast}-{extension}',fast=fast,extension=extension)
        tss(f'left-trim-{fast}',positions=[region('chr1',10),region('chr1',3000)],fast=fast)
        tss(f'right-trim-{fast}',positions=[region('chr1',6900),region('chr1',3000)],fast=fast,seqlengths={'chr1':7000})
        tss(f'only-mito-{fast}',positions=[region('chrM',3000),region('MT',3000)],fast=fast)
        tss(f'absent-{fast}',positions=[region('chrAbsent',3000)],fast=fast)
        tss(f'first-n-{fast}',positions=[region('chrM',3000),*positions],n=1,fast=fast)
        tss(f'clamp-n-{fast}',n=100,fast=fast)
        tss(f'cells-argument-{fast}',selected_cells=['A','B'],fast=fast)
        mapping=[dict(cell='object-A',barcode='A'),dict(cell='object-Z',barcode='Z'),dict(cell='object-B',barcode='B')]
        tss(f'mapped-{fast}',cells=['object-B','object-Z','object-A'],sources=[dict(file='endpoints',map=mapping)],fast=fast)
        tss(f'subset-mapping-{fast}',cells=['outside','object-A','object-B','object-Z'],sources=[dict(file='endpoints',map=mapping)],fast=fast)
        tss(f'multi-disjoint-{fast}',cells=['second-A','first-B','first-A','second-B'],sources=[dict(file='endpoints',map=[dict(cell='first-A',barcode='A'),dict(cell='first-B',barcode='B')]),dict(file='second',map=[dict(cell='second-A',barcode='A'),dict(cell='second-B',barcode='B')])],fast=fast)
        tss(f'multi-shared-{fast}',cells=['A','B','C','D','Z'],sources=[dict(file='endpoints',map=None),dict(file='second',map=None)],fast=fast)
        tss(f'multi-pruning-{fast}',cells=['first','second'],sources=[dict(file='chr1-only',map=[dict(cell='first',barcode='A')]),dict(file='chr2-only',map=[dict(cell='second',barcode='A')])],fast=fast)
    def annotation(c,s,e,g,name,biotype='protein_coding',strand='+'):return dict(chromosome=c,start=s,end=e,strand=strand,gene_id=g,gene_name=name,gene_biotype=biotype)
    annotations=[annotation('chr1',3000,3100,'g2','DUP'),annotation('chr1',2800,2900,'g2','ignored-later',strand='-'),annotation('chr1',100,200,'g1','DUP'),annotation('chr1',500,600,'g3','DUP.1'),annotation('chr2',4000,4100,'g4',None,strand='-'),annotation('chr1',5000,5100,'g5','',biotype='lincRNA'),annotation('chrAbsent',3000,3500,'g6','absent'),annotation('chr1',10,50,'g7','star',strand='*'),annotation('chr1',6000,6200,'g8','mixed',biotype='lincRNA'),annotation('chr1',6050,6250,'g8','later-coding'),annotation('chr1',1000,1499,'g9','width500'),annotation('chr1',2000,2498,'g10','width499')]
    genes=[]
    def gene(name,**kwargs):
        case=dict(id=name,sources=[dict(file='endpoints',map=None)],cells=['D','B','A','C','Z'],annotation=annotations,features=None,biotypes=['protein_coding'],max_width=500000,upstream=2000,downstream=0,gene_id=False,process_n=2000);case.update(kwargs);genes.append(case)
    for chunk in [1,4,2000]:
        for ids in [True,False]:gene(f'default-{chunk}-{ids}',process_n=chunk,gene_id=ids)
    gene('all-biotypes',biotypes=None);gene('lincRNA',biotypes=['lincRNA']);gene('missing-biotype',biotypes=['unavailable']);gene('symbol-filter',features=['DUP.2','g4']);gene('id-filter',features=['g2','g4'],gene_id=True);gene('empty-feature-list',features=[]);gene('unavailable-features',features=['unavailable']);gene('width500',max_width=500);gene('width499',max_width=499);gene('width1',max_width=1);gene('no-width-filter',max_width=None);gene('no-extension',upstream=0);gene('both-extensions',upstream=100,downstream=50);gene('right-trim',seqlengths={'chr1':6300,'chr2':4200},upstream=2000,downstream=500)
    gene('multi-chromosome-gene',annotation=[annotation('chr1',2000,2100,'multi','MULTI'),annotation('chr2',4000,4100,'multi','MULTI')],upstream=0)
    gene('coordinate-aliases',annotation=[annotation('chr1',2000,2100,'left','LEFT'),annotation('chr1',2000,2100,'right','RIGHT')],upstream=0)
    gene('coordinate-aliases-multi',annotation=[annotation('chr1',2000,2100,'left','LEFT'),annotation('chr1',2000,2100,'right','RIGHT')],upstream=0,sources=[dict(file='endpoints',map=None),dict(file='second',map=None)])
    gene('mapped',sources=[dict(file='endpoints',map=[dict(cell='object-B',barcode='B'),dict(cell='object-A',barcode='A'),dict(cell='object-Z',barcode='Z')])],cells=['object-Z','object-A','object-B'])
    gene('multi-disjoint',sources=[dict(file='endpoints',map=[dict(cell='one-A',barcode='A')]),dict(file='second',map=[dict(cell='two-B',barcode='B')])],cells=['two-B','one-A','unknown'])
    gene('empty-annotations',annotation=[])
    return dict(files=files,tss_cases=cases,gene_cases=genes)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-Signac-annotation-controls-in-progress'))
    try:
        data=recipes();save(out/'cases.json',data)
        for file in data['files']:(out/(file['id']+'.tsv')).write_text('# synthetic original TSS/gene controls\n'+''.join('\t'.join(map(str,row))+'\n' for row in file['records']),encoding='utf8',newline='\n')
        names=['tools/literature/collect_signac_annotation_controls.py','tools/literature/collect_signac_annotation_controls.R'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        cmd=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/names[1]),str(out/'cases.json'),str(out),str(out/'original.json')]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:r=subprocess.run(cmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=360,check=False)
        save(out/'original.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0
        original=json.loads((out/'original.json').read_bytes());assert len(original['tss'])==len(data['tss_cases']) and len(original['gene_activity'])==len(data['gene_cases'])
        assert all(pin(ROOT/n)==v for n,v in sources.items());save(out/'COMPLETED.json',dict(status=original['status'],source_pins=sources,cases=pin(out/'cases.json'),original=pin(out/'original.json'),tss_cases=len(original['tss']),gene_cases=len(original['gene_activity']),historical_binaries_executed=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=original['status'],tss_cases=len(original['tss']),gene_cases=len(original['gene_activity']))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_ANNOTATION_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original annotation controls')
    main()
