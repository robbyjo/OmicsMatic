"""Deterministic artificial PRSice workflow inputs; no participant data."""
from pathlib import Path
import math, random

def generate(directory):
    f=Path(directory);f.mkdir();rng=random.Random(8052026);n=96;m=24
    def gen(count,rate):return [int(rng.random()<rate)+int(rng.random()<rate)for _ in range(count)]
    target=[gen(n,.15+.025*(v%10))for v in range(m)];target[1]=target[0].copy();target[2]=[2-g for g in target[0]];target[3]=target[0].copy();target[18]=[1]*n;target[19]=[2]*n;target[0][21]=-1;target[1][22]=-1;target[5][23]=-1
    reference=[gen(64,.2+.025*(v%8))for v in range(m)];reference[18]=[1]*64;reference[19]=[2]*64
    chrom=[1 if v<12 else 2 for v in range(m)];pos=[1000+1000*(v%12)if v!=3 else 300000 for v in range(m)];effects=[(-1 if v%2 else 1)*(.025+.008*v)for v in range(m)];pv=[1e-6,.001,.01,.002,.005,.02,.04,.2,.3,.5,.7,.9,1e-5,.003,.008,.03,.06,.1,.15,.25,.4,.6,.8,1];flip=[v%5==2 for v in range(m)]
    def text(name,value):(f/name).write_text(value,encoding='utf-8',newline='\n')
    def bed(prefix,rows,count,fid,children):
        text(prefix+'.fam',''.join(f'{fid} S{i:03} '+('S000 S001'if children and i>=count-4 else'0 0')+f' {1+i%2} {1+i%2}\n'for i in range(count)));text(prefix+'.bim',''.join(f'{chrom[v]} rs{v+1} 0 {pos[v]} A G\n'for v in range(m)));raw=bytearray(b'\x6c\x1b\x01')
        for row in rows:
            for start in range(0,count,4):raw.append(sum({2:0,-1:1,1:2,0:3}[row[i]]<<(2*(i-start))for i in range(start,min(count,start+4))))
        (f/(prefix+'.bed')).write_bytes(raw)
    bed('target',target,n,'F',True);bed('reference',reference,64,'RF',False)
    base='SNP CHR BP A1 A2 BETA P INFO MAF\n'+''.join(f'rs{v+1} {chrom[v]} {pos[v]} '+('G A'if flip[v]else'A G')+f' {effects[v]:.17g} {pv[v]:.17g} {(.9 if v==20 else .98):.17g} {(.05 if v==21 else .2):.17g}\n'for v in range(m));text('base.tsv',base)
    age=[rng.gauss(0,1)for _ in range(n)];phenotype=[];binary=[]
    for i in range(n):
        g=max(0,target[0][i]);phenotype.append(.3*g+.6*age[i]+.15*(i%3)+rng.gauss(0,.8));prob=1/(1+math.exp(-(.25*g+.35*age[i]-.35)));binary.append(2 if rng.random()<prob else 1)
    text('phenotypes.tsv','FID IID Y CASE\n'+''.join(f'F S{i:03} '+('-9 -9'if i==6 else f'{phenotype[i]:.17g} {binary[i]}')+'\n'for i in range(n)))
    text('covariates.tsv','FID IID AGE BATCH\n'+''.join(f'F S{i:03} '+('NA'if i==10 else f'{age[i]:.17g}')+f' B{i%3}\n'for i in range(n)))
    text('target.keep',''.join(f'F S{i:03}\n'for i in range(n)if i not in(30,31,32)));text('reference.keep',''.join(f'RF S{i:03}\n'for i in range(50)))
    text('prepared.tsv','SNP A1 A2 WEIGHT\n'+''.join(f'rs{v+1} '+('G A'if flip[v]else'A G')+f' {effects[v]:.17g}\n'for v in range(6)))
    text('prepared-or.tsv','SNP A1 A2 WEIGHT\n'+''.join(f'rs{v+1} '+('G A'if flip[v]else'A G')+f' {math.exp(effects[v]):.17g}\n'for v in range(6)))
    text('base-complement.tsv','SNP CHR BP A1 A2 BETA P INFO MAF\n'+''.join(f'rs{v+1} {chrom[v]} {pos[v]} '+('C T'if flip[v]else'T C')+f' {effects[v]:.17g} {pv[v]:.17g} .98 .2\n'for v in range(m)))

CASES=('unclumped-score','target-clump-score','reference-clump-score','reference-frequency','target-keep','linear','binary','linear-standardized','binary-control-standardized','clump-only','noncumulative','grid','reference-frequency-unclumped','std-keep-gaps','control-std-keep-gaps','prepared','prepared-or','complement','linear-permutation','binary-logistic-permutation')

def request(name,runtime,bundle,out):
    if name not in CASES and name!='reference-cache-skip':raise ValueError('Unknown fixture case '+name)
    ref=name.startswith('reference-');clump=name in ('target-clump-score','reference-clump-score','reference-frequency','reference-cache-skip','target-keep','linear','binary','clump-only');predict=name in ('linear','binary','linear-standardized','binary-control-standardized','control-std-keep-gaps','linear-permutation','binary-logistic-permutation');binary=name in ('binary','binary-control-standardized','control-std-keep-gaps','binary-logistic-permutation');control=name in ('binary-control-standardized','control-std-keep-gaps');cov=predict and name!='binary-control-standardized';prepared=name.startswith('prepared')
    inputs={'base':'base-complement.tsv'if name=='complement'else'prepared-or.tsv'if name=='prepared-or'else'prepared.tsv'if prepared else'base.tsv',**{f'target_{e}':f'target.{e}'for e in ('bed','bim','fam')}}
    if ref:inputs.update({f'reference_{e}':f'reference.{e}'for e in ('bed','bim','fam')});inputs['reference_keep']='reference.keep'
    if name in ('target-keep','std-keep-gaps','control-std-keep-gaps'):inputs['target_keep']='target.keep'
    if predict:inputs['phenotype']='phenotypes.tsv'
    if cov:inputs['covariates']='covariates.tsv'
    operation='CLUMP'if name=='clump-only'else'PREDICT'if predict else'SCORE'
    parameters=dict(assembly='GRCh37 artificial coordinates',target_population='artificial target with four children',reference_population='independent artificial reference'if ref else None,ignore_fid=False,id_delimiter='_',base=dict(kind='PREPARED_WEIGHTS'if prepared else'GWAS',effect_scale='OR'if name=='prepared-or'else'BETA',columns=dict(snp='SNP',chromosome=None if prepared else'CHR',position=None if prepared else'BP',a1='A1',a2='A2',effect='WEIGHT'if prepared else'BETA',pvalue=None if prepared else'P')),clumping=dict(mode=('REFERENCE'if ref else'TARGET')if clump else'NONE',r2=.2 if clump else None,window_kb=50 if clump else None,pvalue=1 if clump else None),filters=dict(target=dict(maf=0,missing=1),reference=dict(maf=0,missing=1 if name in ('reference-clump-score','reference-cache-skip')else .99)if ref else None,base_info=None,base_maf=None),alleles=dict(palindromic='DROP',strand='ALLOW_ORIGINAL_COMPLEMENT_WITH_WARNING'if name=='complement'else'EXACT'),scoring=None if operation=='CLUMP'else dict(model='add',missing='MEAN_IMPUTE',scale='con-std'if control else'std'if name in ('linear-standardized','std-keep-gaps')else'sum',cumulative=name!='noncumulative',frequency_source='REFERENCE'if name in ('reference-frequency','reference-frequency-unclumped','reference-cache-skip')else'TARGET',thresholds=dict(kind='GRID',lower=.000001,upper=.1,interval=.01,include_full=True)if name=='grid'else dict(kind='LEVELS',levels=[1]if prepared else[1e-7,.0001,.01,.05,.5,1])),phenotype=dict(kind='BINARY'if binary else'QUANTITATIVE',source='FILE',column='CASE'if binary else'Y',prevalence=None)if predict else None,regression=dict(covariates=['AGE','BATCH']if cov else[],categorical=['BATCH']if cov else[],permutations=10 if name.endswith('permutation')else 0,logistic_permutations=name=='binary-logistic-permutation',evaluation_role='IN_SAMPLE_TUNING')if predict else None,include_nonfounders=name not in ('std-keep-gaps','control-std-keep-gaps'),seed=42,threads=1,memory_mib=512)
    return dict(schema_version=1,mode='RUN',profile='prsice-2.3.5-9dbf55e',operation=operation,runtime_home=str(runtime),bundle=str(bundle),inputs=inputs,parameters=parameters,policies=dict(unmatched_variants='RETAIN_WITH_WARNING',incomplete_samples='RETAIN_WITH_WARNING',original_warnings='RETAIN_WITH_WARNING',solver_convergence='SOURCE_UNREPORTED_WITH_WARNING',standardization='ORIGINAL_MASK_WITH_WARNING'),limits={},out=str(out))
