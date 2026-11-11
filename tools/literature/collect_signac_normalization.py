# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original Signac controls in a NEW path; never change an existing package library."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')

def cases():
    rng=random.Random(724031)
    matrices=[]
    def matrix(name,dense,stored_zeros=False):
        rows=len(dense);cols=len(dense[0]);indices=[];values=[];pointers=[0]
        for col in range(cols):
            for row in range(rows):
                if dense[row][col]!=0 or stored_zeros:indices.append(row);values.append(dense[row][col])
            pointers.append(len(values))
        matrices.append(dict(id=name,features=['peak-α' if r==0 else f'chr1:{r*100}-{r*100+80}' for r in range(rows)],
                             cells=['cell-β' if c==0 else f'cell.{c}' for c in range(cols)],i=indices,p=pointers,x=values,
                             idf=[0.1+((r*17)%23)/3 for r in range(rows)]))
    matrix('hand-count-versus-document-frequency',[[1,2,0],[3,0,0],[0,4,8]])
    matrix('tied-feature-totals',[[1,0,0],[0,1,0],[0,0,1],[3,2,0],[0,0,0]])
    matrix('zero-row-and-cell-implicit',[[0,0],[1,0]])
    matrix('zero-row-and-cell-stored',[[0,0],[1,0]],True)
    matrix('all-zero-implicit',[[0,0],[0,0]])
    matrix('all-zero-stored',[[0,0],[0,0]],True)
    matrix('one-by-one-count',[[7]])
    matrix('one-by-one-empty-stored',[[0]],True)
    matrix('one-by-one-empty-implicit',[[0]])
    matrix('exact-integer-limit',[[9_007_199_254_740_990,0],[1,0],[0,1]])
    matrix('one-cell-many-features',[[0],[1],[2],[5],[5],[6],[0]])
    matrix('one-feature-many-cells',[[0,1,2,3,0,80]])
    for k in range(24):
        rows=rng.randrange(2,30);cols=rng.randrange(2,24)
        matrix(f'random-{k:02}',[[rng.randrange(1,50) if rng.random()<0.22 else 0 for _ in range(cols)] for _ in range(rows)],k%3==0)
    # Extreme valid parameters deliberately retain source infinities, with a native warning.
    matrices[-1]['idf']=[1e308]*len(matrices[-1]['features'])
    configurations=[dict(method=m,scale=s,supplied=b) for m in range(1,5) for s in [1e-5,1,1e4,1e308] for b in [False,True]]
    cutoffs=[dict(kind='ALL',value=0),*[dict(kind='COUNT',value=v) for v in [0,1,5,5.5,9_007_199_254_740_991]],
             *[dict(kind='PERCENTILE',value=v) for v in [0,5,50,100]],dict(kind='KEEP_EXISTING',value=0)]
    return dict(matrices=matrices,configurations=configurations,cutoffs=cutoffs)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    save(out/'INCOMPLETE.json',dict(status='collecting-original-reference-controls'))
    names=['tools/literature/collect_signac_normalization.R','tools/literature/collect_signac_normalization.py']
    names += [p.relative_to(ROOT).as_posix() for p in sorted((BASE/'source-intake-1').glob('*/source/Signac/R/preprocessing.R'))]
    pins={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',pins)
    for name in names:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    try:
        setup=json.loads((BASE/'reference-setup-1/RESULT.json').read_bytes());assert setup['status']=='INSTALLED_ORIGINAL_SIGNAC_REFERENCE_ONLY_NOT_ANALYSIS_ADMISSION' and setup['base_library_unchanged']
        data=cases();save(out/'cases.json',data)
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_PROFILE_USER','R_ENVIRON_USER']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1')
        command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/names[0]),str(out/'cases.json'),str(out/'original.json'),str(BASE/'reference-setup-1/R-lib'),str(BASE/'source-intake-1')]
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:
            process=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300,check=False)
        save(out/'original.process.json',dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Original reference failed; retain logs and retry only in NEW paths'
        result=json.loads((out/'original.json').read_bytes());assert result['status'].startswith('passed-full-original') and len(result['matrices'])==len(data['matrices'])
        assert all(pin(ROOT/name)==value for name,value in pins.items())
        save(out/'COMPLETED.json',dict(status=result['status'],source_pins=pins,cases=pin(out/'cases.json'),original=pin(out/'original.json'),
                                     matrices=len(data['matrices']),normalization_cases=len(data['matrices'])*len(data['configurations']),
                                     selection_cases=len(data['matrices'])*len(data['cutoffs']),extracted_editions=5,
                                     full_original_package_version='1.17.1',historical_packages_installed=False,user_analysis_admitted=False))
        (out/'INCOMPLETE.json').unlink();print((out/'COMPLETED.json').read_text())
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='FAILED_NO_NATIVE_CREDIT',error=str(error)));raise

if __name__=='__main__':main()
