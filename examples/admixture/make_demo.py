#!/usr/bin/env python3
"""Generate a deterministic synthetic ADMIXTURE demonstration in a new directory."""
import argparse,copy,hashlib,json,random
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('out',type=Path);args=parser.parse_args();out=args.out.absolute()
    if out.exists():parser.error('Output already exists; preserve it and choose a new directory')
    out.mkdir(parents=True);rng=random.Random(20261027);markers=1200;k=3;p=[[rng.uniform(.05,.95) for _ in range(k)] for _ in range(markers)]
    training=[[1. if j==i//12 else 0. for j in range(k)] for i in range(36)];target=[]
    for _ in range(12):
        row=[rng.random()+.01 for _ in range(k)];total=sum(row);target.append([x/total for x in row])
    def data(name,q,prefix):
        n=len(q);packed=bytearray([0x6c,0x1b,1])
        for frequencies in p:
            row=[]
            for sample in q:
                probability=sum(a*b for a,b in zip(sample,frequencies));g=int(rng.random()<probability)+int(rng.random()<probability);row.append(None if rng.random()<.02 else g)
            if all(x is None for x in row):row[0]=0
            for start in range(0,n,4):packed.append(sum(({None:1,0:0,1:2,2:3}[g])<<(2*j) for j,g in enumerate(row[start:start+4])))
        (out/(name+'.bed')).write_bytes(packed);(out/(name+'.bim')).write_text(''.join(f'1 m{j+1} 0 {j+1} A C\n' for j in range(markers)),encoding='utf-8',newline='\n');(out/(name+'.fam')).write_text(''.join(f'demo {prefix}{i+1:03d} 0 0 {1+i%2} -9\n' for i in range(n)),encoding='utf-8',newline='\n')
    data('cohort',training,'s');data('targets',target,'t')
    (out/'populations.tsv').write_text('fid\tiid\tpopulation\n'+''.join(f'demo\ts{i+1:03d}\t'+('Synthetic_'+str(i//12+1) if i%12<4 else '-')+'\n' for i in range(36)),encoding='utf-8',newline='\n')
    for name in ['fit','supervised','project','cv','bootstrap']:
        recipe=json.loads((Path(__file__).parent/(name+'.json')).read_text(encoding='utf-8'));recipe['plink_prefix']='targets' if name=='project' else 'cohort';recipe['assembly']='synthetic-demo-ordered-markers';recipe['input_review']=dict(organism='controlled synthetic demonstration',marker_scope='1200 numeric-contig synthetic markers',allele_harmonization='identical synthetic BIM allele/order metadata; P counts C as BIMallele2',ld_pruning='independently simulated markers; no biological LD claim',sample_eligibility='independently simulated synthetic participants; new target IDs for projection',reference_eligibility='synthetic three-group training labels; no real social identity',ploidy_coding='explicit diploid 0/1/2 hardcalls');recipe['limits']=dict(samples=100,markers=2000,genotype_cells=200000,parameter_cells=10000,input_bytes=1048576,retained_bytes=16*1048576,line_bytes=16384,output_bytes=16*1048576);(out/(name+'.json')).write_text(json.dumps(recipe,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'README.txt').write_text('Synthetic deterministic demonstration only. Run fit.json to fit-output before project.json. Use separate new output directories for other recipes. Original runtime admission is required. No real participants or inferred social identities are represented.\n',encoding='utf-8',newline='\n');sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest();(out/'SHA256SUMS').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in sorted(out.iterdir(),key=lambda f:f.name) if f.is_file()),encoding='ascii',newline='\n');print(str(out))
if __name__=='__main__':main()
