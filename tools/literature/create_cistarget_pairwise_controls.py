# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create48 NumPy reduction-boundary controls, including constant positive and nearly flat AUCs."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True);cases=[];lines=[]
    for kind in range(4):
        for m in [3,7,8,9,16,17,31,64,127,128,129,1000]:
            n=1 if kind==0 else 5 if kind==1 else 2;weights=[1.] if kind==0 else [1.,.25,0.,1.5,.125] if kind==1 else [1e-14,1.] if kind==2 else [1.,.23456789]
            ranks=[[0] if kind==0 else [0,1,2,3,4] if kind==1 else [row%2,9] if kind==2 else [0,0] for row in range(m)]
            bound=1 if kind==3 else 10;auc=.01 if kind==3 else .05;id='pairwise-'+str(kind)+'-'+str(m)
            case=dict(id=id,profile='CTXCORE_0_2_0',total_features=100,motifs=m,selected_features=n,auc_parameter=auc,recovery_bound=bound,weights=weights,rankings=ranks);cases.append(case)
            lines.append(' '.join(map(str,[id,case['profile'],100,m,n,auc,bound])));lines.append(' '.join(format(v,'.17g') for v in weights));lines.extend(' '.join(map(str,row)) for row in ranks)
    (out/'controls.txt').write_text('\n'.join(lines)+'\n',encoding='utf8');save(out/'cases.json',cases)
    save(out/'COMPLETED.json',dict(status='PASS48_CISTARGET_NUMPY_REDUCTION_BOUNDARY_CONTROL_FAMILIES_ONLY',cases=48,files={p.name:pin(p) for p in out.iterdir() if p.is_file()},source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False));print('Created48 complete pairwise/near-flat/one-point families')


if __name__=='__main__':main()
