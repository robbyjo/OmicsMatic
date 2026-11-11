# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Literal physical count controls for source pseudocounts, filtering, imputation, standardization, ranks, axes and PCA. No analysis credit."""
import argparse,random
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);cases=[]
    def row(i,pairs,chrom='chr1',start=None):
        start=str(100+i*10) if start is None else str(start);return dict(id=chrom+':'+start+':'+str(int(start)+100)+':clu_'+str(i+1)+'_+',counts=pairs)
    def add(name,rows,blacklist=None,pcs=0):
        n=len(rows[0]['counts']);cases.append(dict(id=name,samples=['S'+str(n-i) for i in range(n)],rows=rows,blacklist=['X','Y'] if blacklist is None else blacklist,pcs=pcs,seed=20261108,limits=dict(maximum_samples=10000,maximum_input_rows=10000,maximum_input_cells=1000000,maximum_rank_comparisons=10000000)))
    base=[row(0,[[0,10],[2,10],[7,10],[10,10],[0,0]]),row(1,[[9,20],[15,20],[1,20],[5,20],[3,20]]),row(2,[[1,4],[3,4],[0,4],[4,4],[2,4]]),row(3,[[10,10],[0,10],[7,10],[2,10],[0,0]])]
    add('pseudocount-missing-and-axes',base,pcs=2)
    add('literal-blacklist-chromosomes',[row(0,[[0,10],[3,10]],'X'),row(1,[[0,10],[3,10]],'Y'),row(2,[[0,10],[3,10]],'chrX'),row(3,[[0,10],[3,10]],'chrY'),row(4,[[0,10],[3,10]],'1')],pcs=1)
    add('explicit-blacklist',[row(0,[[0,10],[3,10]],'X'),row(1,[[0,10],[3,10]],'chrX'),row(2,[[0,10],[3,10]],'contig_α')],blacklist=['chrX'],pcs=1)
    add('missingness-strict-forty-percent',[row(0,[[0,0],[0,0],[1,10],[5,10],[8,10]]),row(1,[[0,0],[0,0],[0,0],[1,10],[8,10]]),row(2,[[0,10],[3,10],[6,10],[9,10],[0,0]])])
    add('population-sd-filter-boundary',[row(0,[[0,99],[1,99]]),row(1,[[0,100],[1,100]]),row(2,[[10,10],[10,10]]),row(3,[[0,10],[10,10]])])
    add('near-machine-precision-sd-boundary',[row(0,[[494999999999999,999999999999999],[504999999999999,999999999999999]]),row(1,[[494999999999999,999999999999999],[505000000000000,999999999999999]]),row(2,[[0,10],[10,10]])])
    add('coordinate-text-and-output-order',[row(0,[[0,10],[3,10]],'chr2',20),row(1,[[0,10],[3,10]],'chr1',100),row(2,[[0,10],[3,10]],'chr1','020'),row(3,[[0,10],[3,10]],'chr1','00020')])
    add('single-retained-intron',[row(0,[[0,10],[10,10]])])
    add('large-original-counts-remain-exact',[row(0,[[0,9007199254740993],[9007199254740991,9007199254740993]]),row(1,[[9007199254740992,9007199254740993],[1,9007199254740993]]),row(2,[[0,3],[3,3]])],pcs=1)
    for kept in [2,3,7,8,9,10,11,12,127,128,129]:
        rows=[row(i,[[i%(5+(i%3)),20],[20-i%7,20],[i%9,20],[i%4,20]]) for i in range(kept)];add('rank-size-'+str(kept),rows,pcs=min(2,kept))
    add('unicode-codepoint-output-order',[row(0,[[0,10],[3,10]],'chr𝒜'),row(1,[[0,10],[3,10]],'chrＺ'),row(2,[[0,10],[3,10]],'chrΩ')])
    add('original-auto-randomized-pca',[row(i,[[((i+3*j)%19),20] for j in range(16)]) for i in range(600)],pcs=3)
    rng=random.Random(20261109)
    for n in [2,3,5,7,8,9,16,127,128,129,257]:
        rows=[]
        for i in range(16):
            pairs=[]
            for j in range(n):
                denominator=rng.choice([0,10,20,100,1000]);pairs.append([rng.randrange(denominator+1),denominator])
            rows.append(row(i,pairs,chrom=rng.choice(['chr1','chr2','X','chrX'])))
        add('pairwise-sample-count-'+str(n),rows,pcs=0)
    for index in range(24):
        n=3+index%7;rows=[]
        for i in range(5+index%11):
            pairs=[]
            for j in range(n):
                denominator=rng.choice([0,5,20,100]);pairs.append([rng.randrange(denominator+1),denominator])
            rows.append(row(i,pairs,chrom=rng.choice(['chr1','chr2','X','Y','chrX'])))
        # Guarantee at least one retained row while preserving all random exclusions.
        rows.append(row(100,[[0,20] if j%2==0 else [20,20] for j in range(n)]));add('seeded-phenotypes-'+str(index+1).zfill(2),rows)
    save(out/'cases.json',dict(schema_version=1,cases=cases));save(out/'COMPLETED.json',dict(status='PASS_LITERAL_LEAFCUTTER_PHENOTYPE_CONTROLS_ONLY',cases=len(cases),source_rows=sum(len(c['rows']) for c in cases),source_cells=sum(len(r['counts']) for c in cases for r in c['rows']),source_pin=pin(Path(__file__)),implementation_credit=False,whole_goal_complete=False));print('Generated',len(cases),'literal phenotype controls')

if __name__=='__main__':main()
