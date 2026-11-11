# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete explicit local-library original-workflow controls; no biological/paper settings inferred."""
import argparse,json,math,random,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);genes=['G'+str(i).zfill(3) for i in range(80)];columns=['S'+str(i) for i in range(8)];rng=random.Random(918247);values=[[float(10+i%11+(18 if i<15 and j<4 or 50<i<70 and j>=4 else 0)+rng.randrange(0,9)) for j in range(8)] for i in range(80)];sets={'up':genes[:15],'down':genes[51:70],'mix':genes[8:29],'outside':['ABSENT1','ABSENT2','ABSENT3'],'full':genes};rank=[[math.cos(i/9)+2-i/21] for i in range(80)];summary={}
    for edition in ['1.0.4','1.1.2','1.1.3','1.1.8']:
        home=out/edition;home.mkdir();analyses=[]
        def add(name,method,raw,options):
            prepared=home/(name+'.prepared.json');save(prepared,dict(schema_version=1,numeric_dtype='float64',gene_sets=sets,**raw));analyses.append(dict(id=name,method=method,interface='original-class',global_seed=1781,prepared=linux(prepared),options=options))
        add('enrichr-local','enrichr',dict(query=genes[:8]+[genes[0],'OUTSIDE'],background=genes),dict(organism='human',cutoff=0.05))
        common=dict(min_size=3,max_size=40,threads=1,seed=1781,weight=1.0)
        add('prerank','prerank',dict(ids=genes,columns=['statistic'],values=rank),dict(**common,permutation_num=31,ascending=False))
        for permutation_type in ['phenotype','gene_set']:add('gsea-'+permutation_type,'gsea',dict(ids=genes,columns=columns,values=values,classes=['A']*4+['B']*4),dict(**common,permutation_num=31,ascending=False,permutation_type=permutation_type,method='signal_to_noise'))
        sscommon=dict(min_size=3,max_size=40,threads=1,seed=1781,weight=0.25,permutation_num=0,ascending=False,correl_norm_type='rank')
        for norm in ['rank','log_rank']:add('ssgsea-'+norm,'ssgsea',dict(ids=genes,columns=columns,values=values),dict(**sscommon,sample_norm_method=norm))
        if edition!='1.0.4':
            for kernel in ['Gaussian','Poisson',None]:add('gsva-'+str(kernel).lower(),'gsva',dict(ids=genes,columns=columns,values=values),dict(**common,kcdf=kernel,mx_diff=True,abs_rnk=False))
        request=home/'request.json';save(request,dict(edition=edition,python_hash_seed=0,maximum_matrix_cells=1000000,analyses=analyses));summary[edition]=dict(request=pin(request),cases=len(analyses),prepared={p.name:pin(p) for p in home.glob('*.prepared.json')})
    save(out/'COMPLETED.json',dict(status='passed-explicit-four-edition-local-library-workflow-fixtures-only',editions=summary,total_cases=sum(v['cases'] for v in summary.values()),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},implementation_credit=False,whole_goal_complete=False));print('Prepared',sum(v['cases'] for v in summary.values()),'explicit original controls across all five API families; no paper options inferred')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete fixture checks')
    main()
