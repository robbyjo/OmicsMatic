# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Own mixed/missing/identity controls for original SNPRelate; collect failures without changing source inputs."""
import argparse
import json
import random
import subprocess
import sys
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/snprelate-runtime'


def cohort(prefix,n,m,kind):
    fam=[]
    for i in range(n):
        fid='家族' if i in [0,2,n-1] else 'F'+str(i)
        iid='shared' if i in [0,1] else ['NA','NaN','åbc','株'][i-3] if 3<=i<=6 else 's'+str(i)
        fam.append(f'{fid} {iid} 0 0 {i%2+1} -9\n')
    prefix.with_suffix('.fam').write_text(''.join(fam),encoding='utf8',newline='\n')
    rng=random.Random(20261111);bed=bytearray(b'\x6c\x1b\x01');bim=[]
    for v in range(m):
        chr=1 if v<m-4 or kind=='all-missing' else 23
        bp=1000+10*(v if chr==1 else v-(m-4))
        identifier='NA' if v==11 else 'v'+str(v)
        bim.append(f'{chr}\t{identifier}\t0\t{bp}\tA\tG\n')
        c=[rng.choice([0,0,1,1,2]) for _ in range(n)]
        choice=v%9
        if choice==0 or kind=='all-missing':c=[-1]*n
        elif choice==1:c=[0]*n
        elif choice==2:c=[2]*n
        elif choice==3:c=[1]*n
        elif choice==4:c=[-1,0,0,0,1,1,2,0,1,0]
        elif choice==5:c=[-1,0,1,2,0,1,2,0,1,-1]
        elif choice==6:c=[2,0,0,0,0,0,0,0,-1,-1]
        if kind=='zero-heterozygosity' and c[-1]>=0:c[-1]=2 if c[-1]==2 else 0
        codes=[{0:3,1:2,2:0,-1:1}[x] for x in c]
        for start in range(0,n,4):
            bed.append(sum((codes[start+j] if start+j<n else 0)<<(2*j) for j in range(4)))
    prefix.with_suffix('.bed').write_bytes(bed);prefix.with_suffix('.bim').write_text(''.join(bim),encoding='utf8',newline='\n')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[];source=ROOT/'tools/literature/collect_snprelate_king_reference.py'
    state=dict(status='FAIL2_ORIGINAL_SNPRELATE_EDGE_COLLECTION',new_catalog_credit=False,whole_goal_complete=False,project_analysis_admitted=False)
    try:
        inputs=out/'own-inputs';inputs.mkdir()
        for kind,n,m in [('mixed',10,129),('zero-heterozygosity',10,129),('all-missing',4,3)]:
            prefix=inputs/kind;cohort(prefix,n,m,kind)
            pins={e:pin(prefix.with_suffix('.'+e)) for e in ['bed','bim','fam']}
            for transport in ['bed-import','row-index']:
                folder=out/(kind+'-'+transport)
                command=[sys.executable,str(source),'--bfile',str(prefix),'--transport',transport,'--out',str(folder)]
                r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=300)
                (out/(kind+'-'+transport+'.stdout')).write_bytes(r.stdout);(out/(kind+'-'+transport+'.stderr')).write_bytes(r.stderr)
                done=json.loads((folder/'COMPLETED.json').read_bytes())
                record=dict(input=kind,transport=transport,exit_code=r.returncode,status=done['status'],reference=folder.relative_to(ROOT).as_posix(),input_pins=pins)
                records.append(record);print(json.dumps(record),flush=True)
                # Any unexpected transport/worker failure remains a failed collection;
                # declared per-case original errors are retained inside successful workers.
                assert r.returncode==0,(kind,transport,r.stdout[-1500:],r.stderr[-1500:])
                assert all(pin(prefix.with_suffix('.'+e))==value for e,value in pins.items())
        state.update(status='PASS_ORIGINAL_SNPRELATE_OWN_EDGE_REFERENCES_WITH_SOURCE_DISPOSITIONS',records=records,producer=pin(Path(__file__)),
                     original_inputs_modified=False,source_case_errors_preserved=True,all_transport_workers_completed=True)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2200],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-input and negative-control checks')
    raise SystemExit(main())
