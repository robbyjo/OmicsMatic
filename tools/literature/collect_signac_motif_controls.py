# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original motif enrichment with explicit and matched backgrounds before native implementation."""
import argparse
import copy
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

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def recipes():
    previous=json.loads((BASE/'links-original-4/cases.json').read_bytes())['datasets']['base'];rng=random.Random(49134);matrix=[]
    for i in range(len(previous['peaks'])):matrix.append([0,1,int(i<15),int(i>=20),int(i%2==0),int(i in [0,3]),int(i<15),int(rng.random()<0.25),int(i%3==0),int(i%5==0)])
    base=dict(peaks=previous['peaks'],counts=previous['counts'],cells=previous['cells'],metadata=previous['meta'],motifs=['M'+str(i) for i in range(10)],motif_names=['TF α','all','query','background','even','rare','query duplicate','random','third','fifth'],matrix=matrix);datasets=dict(base=base)
    counts=copy.deepcopy(base);counts['matrix']=[[v*(3 if j==2 else 2) for j,v in enumerate(row)] for row in counts['matrix']];datasets['counts']=counts
    one=copy.deepcopy(base);one['motifs']=one['motifs'][2:3];one['motif_names']=one['motif_names'][2:3];one['matrix']=[row[2:3] for row in one['matrix']];datasets['one']=one
    zero=copy.deepcopy(base);zero['motifs']=[];zero['motif_names']=[];zero['matrix']=[[] for row in matrix];datasets['zero']=zero
    missing=copy.deepcopy(base);missing['metadata'][25]['gc']=None;datasets['missing-background-stat']=missing
    missingq=copy.deepcopy(base);missingq['metadata'][1]['gc']=None;datasets['missing-query-stat']=missingq
    constant=copy.deepcopy(base)
    for row in constant['metadata']:row['gc']=50.0
    datasets['constant-stat']=constant;cases=[]
    def add(name,**kwargs):
        case=dict(id=name,dataset='base',query=list(range(12)),background=list(range(12,84)),adjustment='BH',features_match=['GC.percent'],seed=8921);case.update(kwargs);cases.append(case)
    for method in ['BH','BY','holm','hochberg','hommel','bonferroni','none','fdr']:add('explicit-'+method,adjustment=method)
    add('all-background',background=None);add('overlapping-background',background=list(range(6,46)));add('repeated-query',query=[0,0,1,3,3,4]);add('repeated-background',background=[12,12,13,14,15,16,17,18,18,19,20,21]);add('single-query',query=[0]);add('empty-query',query=[]);add('empty-background',background=[]);add('query-larger-than-background',background=list(range(12,17)));add('counts-matrix',dataset='counts');add('one-motif',dataset='one');add('zero-motifs',dataset='zero');add('missing-explicit-query',query=[0,'missing']);add('missing-explicit-background',background=[12,'missing']);add('invalid-adjustment',adjustment='invalid')
    for size in [8,20,10000]:add('matched-'+str(size),background=size)
    add('matched-two-statistics',background=20,features_match=['GC.percent','sequence.length']);add('matched-count-statistic',background=20,features_match=['GC.percent','count']);add('matched-no-statistics',background=20,features_match=[]);add('matched-missing-statistic',background=20,features_match=['absent']);add('matched-missing-query',background=20,query=[0,0,1,2,'missing']);add('matched-all-missing-query',background=20,query=['missing']);add('matched-NA-background',background=20,dataset='missing-background-stat');add('matched-NA-query',background=20,dataset='missing-query-stat');add('matched-constant-stat',background=20,dataset='constant-stat');add('matched-zero',background=0);add('matched-negative',background=-1)
    return dict(datasets=datasets,cases=cases,profiles=['1.6.0-source','1.9.0-source','1.14.0-source','1.17.1-package'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--legacy-access-bridge',action='store_true');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-motif-enrichment-controls-in-progress'))
    try:
        data=recipes();data['legacy_access_bridge']=args.legacy_access_bridge;save(out/'cases.json',data);names=['tools/literature/collect_signac_motif_controls.py','tools/literature/collect_signac_motif_controls.R','tools/literature/prepare_signac_motif_sources.py','src/main/resources/org/jlinalg/omics/signac_motif_legacy_access.R'];names += [p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/resources/org/jlinalg/omics/signac-motifs').rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_signac_motif_controls.R'),str(out/'cases.json'),str(out/'original.json'),str(out)]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:code=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=360,check=False).returncode
        save(out/'original.process.json',dict(command=argv,exit_code=code));assert code==0;original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==len(data['cases'])*len(data['profiles'])
        assert all('replay_error' not in row for row in original['results']),'Sampler replay scaffold failed; do not classify this as an original source failure'
        assert all(pin(ROOT/n)==v for n,v in sources.items());success=sum('error' not in row['result']['value'] for row in original['results']);save(out/'COMPLETED.json',dict(status='captured-original-motif-enrichment-and-matched-background-controls',source_pins=sources,cases=pin(out/'cases.json'),original=pin(out/'original.json'),profiles=data['profiles'],controls=len(original['results']),successful_controls=success,retained_source_failures=len(original['results'])-success,legacy_access_bridge=args.legacy_access_bridge,full_current_package_executed=True,historical_packages_executed=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-original-motif-enrichment-controls',controls=len(original['results']),successful=success)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_MOTIF_CONTROLS',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original motif controls')
    main()
