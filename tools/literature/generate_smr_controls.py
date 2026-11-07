# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate explicit synthetic SMR genotype/GWAS/QTL inputs with complete byte/precision provenance."""
import argparse,json,math,random,struct
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);rng=random.Random(190731);n=512;m=32;haps=[]
    for h in range(n*2):
        anchor=int(rng.random()<.31);haps.append([anchor]+[anchor if rng.random()<(.96 if j==1 else .82 if j<20 else .2) else int(rng.random()<.31) for j in range(1,m)])
    calls=[[haps[2*i][j]+haps[2*i+1][j] for i in range(n)] for j in range(m)];freq=[sum(v)/(2*n) for v in calls];positions=[1000000+1000*j for j in range(m)];chromosomes=[1]*28+[2]*4;alleles=[('A','G') if j%2==0 else ('C','T') for j in range(m)];ids=['rsSynthetic'+str(j+1) for j in range(m)];stride=(n+3)//4;bed=bytearray(b'\x6c\x1b\x01')
    for values in calls:
        block=bytearray(stride)
        for i,value in enumerate(values):block[i//4]|={2:0,1:2,0:3}[value]<<(2*(i%4))
        bed+=block
    (out/'reference.bed').write_bytes(bed);(out/'reference.bim').write_text(''.join(f'{chromosomes[j]}\t{ids[j]}\t0\t{positions[j]}\t{alleles[j][0]}\t{alleles[j][1]}\n' for j in range(m)),encoding='utf8',newline='\n');(out/'reference.fam').write_text(''.join(f'family{i+1}\tsample{i+1}\t0\t0\t0\t-9\n' for i in range(n)),encoding='utf8',newline='\n')
    def corr(a,b):
        ma=sum(a)/len(a);mb=sum(b)/len(b);return sum((x-ma)*(y-mb) for x,y in zip(a,b))/math.sqrt(sum((x-ma)**2 for x in a)*sum((y-mb)**2 for y in b))
    ld=[[corr(a,b) for b in calls] for a in calls];gwas=[]
    for j in range(m):
        effect=.1*ld[0][j]+(.018 if j in [4,7,12] else 0);se=.012
        gwas.append([ids[j],*alleles[j],freq[j],effect,se,math.erfc(abs(effect/se)/math.sqrt(2)),100000])
    def tsv(path,headers,rows):(out/path).write_text('\t'.join(headers)+'\n'+''.join('\t'.join(format(v,'.17g') if isinstance(v,float) else str(v) for v in row)+'\n' for row in rows),encoding='utf8',newline='\n')
    tsv('gwas.ma',['SNP','A1','A2','freq','b','se','p','n'],gwas);qtl=[]
    definitions=[('probe-strong',1,1010000,'GENE_STRONG',.5,1),('probe-negative',1,1010000,'GENE_NEGATIVE',-.4,1),('probe-weak',1,1010000,'GENE_WEAK',.025,1),('probe-low-count',1,1010000,'GENE_SMALL',.5,3),('probe-zero-outcome',2,1029000,'GENE_CHR2',.5,1)]
    for name,chrom,bp,gene,effect,modulus in definitions:
        for j in range(m):
            if chromosomes[j]!=chrom or (modulus==3 and j>=2):continue
            b=effect*(ld[0][j] if chrom==1 else ld[28][j]);se=.02 if name!='probe-weak' else .03;pval=math.erfc(abs(b/se)/math.sqrt(2));qtl.append([ids[j],chromosomes[j],positions[j],*alleles[j],freq[j],name,chrom,bp,gene,'+',b,se,pval])
    tsv('qtl.qfile',['SNP','Chr','BP','A1','A2','Freq','Probe','Probe_Chr','Probe_bp','Gene','Orientation','b','se','p'],qtl);save(out/'GENERATED.json',dict(status='generated-synthetic-SMR-inputs-no-analysis',seed=190731,reference_samples=n,reference_variants=m,probes=len(definitions),qtl_rows=len(qtl),genotype_A1_dosages=calls,allele_frequencies=freq,independent_LD=ld,probe_definitions=definitions,original_ids_preserved=True,independent_biological_validity=False,required_action='These synthetic controls test software behavior only. Preserve exact alleles, reference sample order and source float32 QTL storage; do not substitute this LD/reference for biological analysis.',inputs={p.name:pin(p) for p in sorted(out.iterdir()) if p.is_file()},source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))}));print('Generated512 samples,32 variants,5 probes and',len(qtl),'literal QTL records; no model executed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable control provenance checks')
    main()
