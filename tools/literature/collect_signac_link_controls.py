# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture full-package and literal historical-function LinkPeaks controls before adapter implementation."""
import argparse
import copy
import json
import math
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
def recipes():
    rng=random.Random(19371);cells=['cell-'+str(i+1) for i in range(16)];peaks=[];counts=[];meta=[]
    for chromosome,n,origin in [('chr1',12,9000),('chr10',24,48000),('chr2',32,49000),('chr3',16,30000)]:
        for k in range(n):
            start=origin+k*100;end=start+rng.randrange(20,91);peaks.append(dict(chromosome=chromosome,start=start,end=end));row=[rng.randrange(0,9) for _ in cells]
            if chromosome=='chr1' and k<4:row=[j+1 if k%2==0 else 17-j for j in range(16)]
            if chromosome=='chr2' and k<4:row=[(j%4+1)*(k+1) for j in range(16)]
            counts.append(row);meta.append(dict(gc=30+rng.random()*40,length=end-start+1))
    genes=[dict(chromosome='chr1',start=10000,end=10500,strand='+',gene_id='ENSG1',gene_name='G1'),dict(chromosome='chr2',start=50000,end=51000,strand='-',gene_id='ENSG2',gene_name='G2'),dict(chromosome='chr10',start=50000,end=50500,strand='+',gene_id='ENSG3',gene_name='G3')]
    expression=[[1+j/5 for j in range(16)],[0.3+(j%4)*1.3 for j in range(16)],[float(j//4) for j in range(16)]];base=dict(cells=cells,peaks=peaks,counts=counts,meta=meta,genes=genes,expression=expression)
    cases=[]
    def add(name,**kwargs):
        case=dict(id=name,dataset='base',method='pearson',distance=1500,min_distance=None,min_cells=3,n_sample=8,pvalue_cutoff=1,score_cutoff=0.05,gene_id=False,seed=5103,genes_use=None,precomputed_count=False);case.update(kwargs);cases.append(case)
    for method in ['pearson','spearman']:
        for sample in [6,10]:
            for cutoff in [0.05,1]:add(f'{method}-{sample}-{cutoff}',method=method,n_sample=sample,pvalue_cutoff=cutoff)
    add('zero-p-cutoff',pvalue_cutoff=0);add('score0',score_cutoff=0);add('score-high',score_cutoff=0.95);add('no-score-passes',score_cutoff=2);add('min-distance',min_distance=500);add('negative-min-distance',min_distance=-10);add('zero-min-distance',min_distance=0);add('no-distance',distance=0);add('no-cells-pass',min_cells=16);add('background-too-small',n_sample=200);add('background-sd-undefined',n_sample=1);add('gene-id',gene_id=True);add('gene-subset',genes_use=['G3','G1']);add('precomputed-count',precomputed_count=True);add('invalid-method',method='unsupported');add('negative-expression-spearman',dataset='negative-expression',method='spearman');add('negative-expression-spearman-stored',dataset='stored-negative-expression',method='spearman');add('constant-cis',dataset='constant-cis');add('missing-peak-metadata',dataset='missing-metadata');add('missing-background-values',dataset='missing-background');add('no-trans-peaks',dataset='single-chromosome')
    add('minimum-larger-than-distance',min_distance=3000);datasets=dict(base=base)
    negative=copy.deepcopy(base);negative['expression'][0]=[float(j-8) if j not in [2,6,12] else 0.0 for j in range(16)];datasets['negative-expression']=negative;stored=copy.deepcopy(negative);stored['keep_stored_zeros']=True;datasets['stored-negative-expression']=stored
    constant=copy.deepcopy(base);constant['counts'][0]=[4]*16;datasets['constant-cis']=constant
    missing=copy.deepcopy(base);missing['missing_metadata']=True;datasets['missing-metadata']=missing
    missing_bg=copy.deepcopy(base)
    for i in range(12,len(peaks)):missing_bg['meta'][i]['gc']=None
    datasets['missing-background']=missing_bg
    single=copy.deepcopy(base)
    for field in ['peaks','counts','meta']:single[field]=single[field][:12]
    datasets['single-chromosome']=single
    return dict(datasets=datasets,cases=cases,profiles=['1.17.1-package','1.14.0-source'])
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-LinkPeaks-controls-in-progress'))
    try:
        data=recipes();save(out/'cases.json',data);names=['tools/literature/collect_signac_link_controls.py','tools/literature/collect_signac_link_controls.R','tools/literature/prepare_signac_link_sources.py'];names.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/resources/org/jlinalg/omics/signac-links').rglob('*') if p.is_file());sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/collect_signac_link_controls.R'),str(out/'cases.json'),str(out/'original.json'),str(out)]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=360,check=False)
        save(out/'original.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;original=json.loads((out/'original.json').read_bytes());assert len(original['results'])==len(data['cases'])*2 and all(pin(ROOT/n)==v for n,v in sources.items());successful=sum('error' not in v['result']['value'] for v in original['results']);assert successful==44,('Unexpected source/audit successes',successful);save(out/'COMPLETED.json',dict(status=original['status'],source_pins=sources,cases=pin(out/'cases.json'),original=pin(out/'original.json'),profiles=data['profiles'],controls=len(original['results']),successful_controls=successful,expected_source_failures=16,full_current_package_executed=True,historical_packages_executed=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=original['status'],controls=len(original['results']),successful=successful)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_LINKPEAKS_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original LinkPeaks collection checks')
    main()
