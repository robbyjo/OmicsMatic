# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create deterministic prepared-RNA controls; no expression normalization is inferred."""
import argparse,random
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

LABELS=[('1.0.0',['CELLCHAT_1_0_0']),('1.1.3',['CELLCHAT_1_1_3_FAST','CELLCHAT_1_1_3_SLOW']),
 ('2.1.2',['CELLCHAT_2_1_2_RNA']),('2.2.0-2025-initial',['CELLCHAT_2_2_0_RNA']),
 ('2.2.0-2025-release',['CELLCHAT_2_2_0_RNA']),('2.2.0-2026-description',['CELLCHAT_2_2_0_RNA'])]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    lines=[];records=[]
    for label,profiles in LABELS:
      for profile in profiles:
       methods=['TRI_MEAN','TRUNCATED_MEAN','MEDIAN']+(['THRESHOLDED_MEAN'] if label.startswith('2.') else [])
       for dataset in range(3):
        for method in methods:
         for population in [False,True]:
          case='C'+str(len(records)+1).zfill(3);nc=18;ng=3
          genes=['L','R','L1','L2','R1','R2','A','B','C','I','Z','N']
          rng=random.Random(91000+dataset)
          data=[[float(rng.choice([0,0,1,2,4,8,16])) for _ in range(nc)] for _ in genes]
          if dataset==1:
              data=[[float((r*11+c*7)%17) for c in range(nc)] for r in range(len(genes))]
              data=[[x*1024 for x in row] for row in data]
          if dataset==2:data=[[8.0 if r!=10 else 0.0 for c in range(nc)] for r in range(len(genes))]
          data[10]=[0.0]*nc
          # Non-contiguous cell order, unequal group sizes, explicit group order.
          groups=[2,0,1,2,0,2,1,0,2,0,1,2,0,2,0,1,2,2]
          pairs=[['LR','L','R','-','-','-','-'],['COMPLEX','LC','RC','-','-','-','-'],
           ['COREG','L','R','-','-','CO_A','CO_I'],['AG_ANT','LC','R','AG','ANT','CO_A','CO_I'],
           ['ZERO','Z','R','-','-','-','-'],['UNMEASURED','L','R','ABSENT','ABSENT','ABSENT','ABSENT'],
           ['DUPLICATE','L','R','DUP','DUP','DUP','DUP'],['R_COMPLEX','L','RC','-','-','-','-']]
          complexes={'LC':['L1','L2'],'RC':['R1','R2']}
          if label.startswith('2.'):
              complexes['SINGLE']=['L1'];pairs.append(['ONE_SUBUNIT','SINGLE','R','-','-','-','-'])
          cofactors={'CO_A':['C','absent_gene'],'CO_I':['I'],'AG':['A','B'],
           'ANT':['I','B'],'ABSENT':['absent_gene'],'DUP':['A','A','absent_gene','B']}
          hill=[1.,2.,0.75][dataset];kh=[0.5,0.25,0.5][dataset];trim=0.2 if method!='THRESHOLDED_MEAN' else 0.5
          nb=19;seed=8600+dataset
          lines.append(' '.join(map(str,[case,label,profile,len(genes),nc,ng,method,trim,kh,hill,int(population),len(pairs),len(complexes),len(cofactors),nb,seed])))
          for gene,row in zip(genes,data):lines.append(gene+' '+' '.join(map(repr,row)))
          lines.append(' '.join(map(str,groups)))
          lines.extend(' '.join(pair) for pair in pairs)
          for resource in [complexes,cofactors]:
              lines.extend(name+' '+str(len(parts))+' '+' '.join(parts) for name,parts in resource.items())
          records.append(dict(id=case,label=label,profile=profile,dataset=dataset,average=method,population_size=population,nboot=nb))
    (out/'cases.txt').write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n');save(out/'cases.json',records)
    save(out/'COMPLETED.json',dict(status='PASS_PREPARED_CELLCHAT_RNA_CONTROLS_ONLY',cases=len(records),cases_pin=pin(out/'cases.txt'),source=pin(Path(__file__)),analysis_admitted=False,implementation_credit=False))
    print('Prepared CellChat source-bound cases='+str(len(records)))

if __name__=='__main__':raise SystemExit(main())
