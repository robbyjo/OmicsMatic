#!/usr/bin/env python3
"""Freeze hash-verified synthetic outputs from 56 original PRS-CS reference runs."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original',type=Path,required=True)
    parser.add_argument('--checks',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    source=json.loads((args.original/'verification.json').read_text(encoding='utf-8'))
    checks=json.loads(args.checks.read_text(encoding='utf-8'))
    lock=json.loads(Path(__file__).with_name('prscs-runtime-lock.json').read_text(encoding='utf-8'))
    assert source['status'].startswith('passed-original-three-profile') and len(source['steps'])==56
    assert checks['status']=='passed-independent-original-identity-family-scaling-and-draw-mean-contracts'
    assert checks['source_sha256']==sha(args.original/'verification.json')
    for profile,files in source['source_files_sha256'].items():
        assert files=={name:item['sha256'] for name,item in lock['profiles'][profile]['files'].items()}
    args.out.mkdir(parents=True,exist_ok=True)
    for name,value in source['input_files_sha256'].items():
        original=args.original/'input'/name
        assert sha(original)==value
        dest=args.out/name
        if dest.exists():
            assert sha(dest)==value
        else:
            dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(original,dest)
    outputs=args.out/'original-outputs'
    outputs.mkdir(exist_ok=False)
    cases=[]
    for run in source['steps']:
        assert run['exit_code']==0
        profile=run['profile']
        # Old tags are case sensitive; current source uppercases either spelling.
        beta=[v for v in run['command'] if v.startswith('--beta_std=')]
        assert beta==['--beta_std='+str(run['standardized'])]
        if profile!='1.0.0':
            assert '--write_psi=True' in run['command']
        item={key:run[key] for key in ['label','profile','format','shrinkage','standardized','output']}
        item['files_sha256']={}
        for name,value in run['files_sha256'].items():
            path=args.original/run['label']/name
            assert sha(path)==value
            if name.startswith('result_pst_'):
                target=outputs/run['label']/name
                target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(path,target)
                item['files_sha256'][name]=value
        assert len(item['files_sha256'])==(2 if profile=='1.0.0' else 4)
        cases.append(item)
    proof=dict(scope='Synthetic supplied LD/SNP/summary tables only;56 actual unmodified original scientific processes. No source code,real cohort data or downloaded LD panel is redistributed. Hash and independent family/unit/draw-mean checks passed before freezing. Short40/20/5 schedules are tests,not recommendations or convergence evidence.',producer_sha256=sha(__file__),original_proof_sha256=sha(args.original/'verification.json'),independent_proof_sha256=sha(args.checks),source_files_sha256=source['source_files_sha256'],iterations=40,burnin=20,thin=5,retained=4,seed=17,sample_size=20000,a=1,b=.5,fixed_phi=.01,cases=cases,totals=checks['totals'])
    (args.out/'posterior-provenance.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
    paths=sorted(p for p in args.out.rglob('*') if p.is_file() and p.name!='SHA256SUMS')
    (args.out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.relative_to(args.out).as_posix()+'\n' for p in paths),encoding='utf-8')
    print(json.dumps(dict(cases=len(cases),files=len(paths),checksums_sha256=sha(args.out/'SHA256SUMS'))))


if __name__=='__main__':
    main()
