# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare complete numerical-kernel controls, including threshold/tail ties, complex minima, DEG maxima, column collisions, overflow and Unicode ordering."""
import argparse,copy,json,random,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);cases=[]
    def case(name,labels,values,**extra):
        n=len(labels);proteins=list(range(101,101+len(values)));c=dict(id=name,proteins=proteins,cells=['cell-'+str(i) for i in range(n)],cell_types=labels,expression=values,complexes=[],interactions=[dict(id='I'+str(i),first=proteins[i],second=proteins[(i+1)%len(proteins)]) for i in range(len(proteins))],microenvironments=[],threshold=.1,separator='|',permutations=24,seed=4711,pvalue_cutoff=.05,degs=None,precision='float64');c.update(extra);cases.append(c);return c
    labels=['B','A','C','A','B','C']*2;values=[[((i+2)*(j+3))%11 if (i+j)%4 else 0 for j in range(len(labels))] for i in range(5)];base=case('complex-minima-deg-maxima-and-original-shuffle',labels,values,complexes=[dict(id=900,proteins=[101,102]),dict(id=700,proteins=[102,103,103])],permutations=100,full_original_shuffle=True,degs=[dict(protein=101,cell_type='B'),dict(protein=102,cell_type='A'),dict(protein=102,cell_type='A'),dict(protein=9999,cell_type='A'),dict(protein=101,cell_type='outside')]);base['interactions'] += [dict(id='C1',first=900,second=103),dict(id='C2',first=700,second=900),dict(id='self',first=700,second=700)]
    labels=['A']*10+['B']*10;case('strict-expression-threshold-equality',labels,[[1]+[0]*9+[0,1]+[0]*8,[0,2]+[0]*8+[0,0,2]+[0]*7,[1]*20,[0]*20],threshold=.1)
    c=copy.deepcopy(base);c.update(id='zero-cutoff-original-falsy-mask',pvalue_cutoff=0.0,full_original_shuffle=False,permutations=24);cases.append(c)
    c=copy.deepcopy(base);c.update(id='method1-no-permutations-or-degs',permutations=0,degs=None,full_original_shuffle=False);cases.append(c)
    c=copy.deepcopy(base);c.update(id='overlapping-duplicate-microenvironment-rows',full_original_shuffle=False,microenvironments=[dict(cell_type='C',environment='z'),dict(cell_type='B',environment='z'),dict(cell_type='C',environment='z'),dict(cell_type='A',environment='a'),dict(cell_type='B',environment='a')]);cases.append(c)
    labels=['A|B','C','A','B|C']*3;case('encoded-pair-column-collisions',labels,[[1+i%3 for i in range(12)],[4-i%3 for i in range(12)]])
    labels=['z','é','🐭','\ue000','NA','A']*2;c=case('unicode-and-literal-NA-axes',labels,[[1+i%4 for i in range(12)],[4-i%4 for i in range(12)]]);c['cells']=['NA','cell "quoted"','cell space','cell-α',*['cell-'+str(i) for i in range(4,12)]]
    case('one-cell-type-all-permutation-ties',['only']*6,[[1,3,2,4,5,7],[3,1,2,5,4,7]])
    case('bankers-rounded-one-of-sixteen-rank',['A','B','C','D']*3,[[1 if i%4==0 else 0 for i in range(12)],[3 if i%4==3 else 0 for i in range(12)]],permutations=0,degs=None)
    case('literal-finite-input-overflow',['A','A','B','B'],[[sys.float_info.max,sys.float_info.max,0,0],[0,0,sys.float_info.max,sys.float_info.max],[1,0,0,1]],permutations=8)
    c=case('float32-prepared-source-values',['A','B']*5,[[1e12,1,3.125,.1,2e12,.3,8,3.75,1e-20,2],[1,2,3,4,5,6,7,8,9,10]],precision='float32');c['interactions'] += [dict(id='self-1',first=101,second=101)]
    case('sequential-double-sum-rounding',['A']*5+['B']*5,[[9007199254740992,1,1,1,1,9007199254740992,2,2,2,2],[1]*10])
    r=random.Random(7221);case('dense-packing-and-many-interaction-identities',['C','A','B']*7,[[r.randrange(0,23) for _ in range(21)] for i in range(7)],interactions=[dict(id='I'+str(i),first=101+i%7,second=101+(i*3+1)%7) for i in range(47)],complexes=[dict(id=501,proteins=[101,102]),dict(id=502,proteins=[103,104])],degs=[dict(protein=101,cell_type='C')])
    save(out/'cases.json',dict(cases=cases));(out/'generator-at-attempt.py').write_bytes(Path(__file__).read_bytes());save(out/'COMPLETED.json',dict(status='PASS_LITERAL_CELLPHONEDB_PREPARED_KERNEL_INPUTS_ONLY',cases=len(cases),source_pin=pin(Path(__file__)),implementation_credit=False,whole_goal_complete=False));print('Prepared',len(cases),'complete numerical-kernel controls')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete control preparation')
    main()
