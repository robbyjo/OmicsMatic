# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare source-summary/ordering controls; no fake statistical-test validation."""
import argparse,itertools,json,math,random
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/signac-runtime') and not out.exists();out.mkdir()
    rng=random.Random(81029);base=[[rng.randrange(5) if rng.random()<.6 else 0 for _ in range(9)] for _ in range(16)]
    base[:6]=[[0]*9,[1]*9,[5]*4+[0]*5,[0]*4+[5]*5,[1,0,0,0,0,0,0,0,0],[0,0,0,0,1,0,0,0,0]]
    versions=['4.1.0','4.3.0','5.0.0','5.1.0','5.2.1','5.6.0'];cases=[]
    def add(version,matrix,slot,norm,pc,testing_slot=None,**overrides):
        row=dict(id='case'+str(len(cases)+1),version=version,matrix=matrix,group1=[0,1,2,3],group2=[4,5,6,7,8],slot=slot,testing_slot=testing_slot or slot,normalization=norm,pseudocount=pc,base=2,min_pct=0,min_diff_pct='-Inf',logfc_threshold=0,only_pos=False,p_values=[['NaN',.01,.01,.01,.01,.5,1,.2][i%8] for i in range(len(matrix))]);row.update(overrides);cases.append(row)
    for version,slot,norm,pc in itertools.product(versions,['counts','data','scale.data'],['UNRECORDED','LOG_NORMALIZE','OTHER'],[0,1]):
        add(version,base,slot,norm,pc)
    for version in versions:
        for cutoff in [0,.1,.25,1.0]:
            add(version,base,'data','UNRECORDED',1,min_pct=cutoff,min_diff_pct=.01,logfc_threshold=.25,only_pos=True)
        add(version,base,'counts','LOG_NORMALIZE',2,testing_slot='data',base=math.e,min_pct=.1)
        add(version,base,'data','LOG_NORMALIZE',1,testing_slot='scale.data',logfc_threshold=100)
        signed=[[(-1 if c%2 else 1)*v for c,v in enumerate(row)] for row in base]
        add(version,signed,'scale.data','UNRECORDED',1,only_pos=True)
        add(version,signed,'data','OTHER',0,testing_slot='scale.data',only_pos=True)
        extreme=[[710.0]*9,[-710.0]*9,[1e300]*9,[0]*9]
        add(version,extreme,'data','UNRECORDED',0,testing_slot='scale.data')
    target=out/'CASES.json';target.write_text(json.dumps(cases,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(dict(status='prepared-six-edition-source-summary-controls',cases=len(cases),statistical_tests_executed=False)))

if __name__=='__main__':main()
