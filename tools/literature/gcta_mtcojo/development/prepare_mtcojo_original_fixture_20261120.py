from pathlib import Path
import os
os.environ['OPENBLAS_NUM_THREADS']='1'
import hashlib,json,math
import numpy as np
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';B.mkdir(parents=True,exist_ok=True);O=B/'synthetic-inputs-1';O.mkdir(exist_ok=False)
rng=np.random.Generator(np.random.PCG64(20261120));n=512;m=10000;N=100000;chromosomes=np.minimum(np.arange(m)//455+1,22);names=[f'rsMT{i+1:07d}' for i in range(m)]
population_af=rng.uniform(.1,.4,m);calls=rng.binomial(2,population_af[:,None],size=(m,n)).astype('u1');af=calls.mean(axis=1)/2
coding=np.array([3,2,0],dtype='u1');packed=(coding[calls].reshape(m,n//4,4).astype('u2')<<np.array([0,2,4,6],dtype='u2')).sum(axis=2).astype('u1')
(O/'reference.bed').write_bytes(b'\x6c\x1b\x01'+packed.tobytes())
(O/'reference.fam').write_text(''.join(f'F{i+1:05d}\tI{i+1:05d}\t0\t0\t0\t-9\n' for i in range(n)),encoding='utf-8',newline='\n')
(O/'reference.bim').write_text(''.join(f'{chromosomes[i]}\t{names[i]}\t0\t{(i%455+1)*100000}\tA\tG\n' for i in range(m)),encoding='utf-8',newline='\n')
instruments1=np.arange(100,9100,300);instruments2=np.arange(210,9210,300)
effects1=np.sqrt(N*.10/m)*rng.normal(size=m);effects2=np.sqrt(N*.08/m)*rng.normal(size=m);effectsD=np.sqrt(N*.04/m)*rng.normal(size=m)
effects1[instruments1]+=rng.choice([-1,1],len(instruments1))*12;effects2[instruments2]+=rng.choice([-1,1],len(instruments2))*12
z=np.zeros((m,3));ld=np.zeros(m)
for chrom in range(1,23):
    index=np.flatnonzero(chromosomes==chrom);g=calls[index].astype(float);g-=g.mean(axis=1)[:,None];g/=np.sqrt(np.mean(g*g,axis=1))[:,None];corr=g@g.T/n
    ld[index]=np.sum(corr*corr,axis=1)
    noise=np.column_stack([g@rng.normal(size=n)/np.sqrt(n) for _ in range(3)])
    gx1=corr@effects1[index];gx2=corr@effects2[index];gd=corr@effectsD[index]
    z[index,1]=gx1+noise[:,1];z[index,2]=gx2+noise[:,2];z[index,0]=.35*gx1-.2*gx2+gd+noise[:,0]
    for folder in ['ref-ld','weights-ld']:
        d=O/folder;d.mkdir(exist_ok=True);(d/(str(chrom)+'.l2.ldscore')).write_text('CHR\tSNP\tBP\tL2\n'+''.join(f'{chrom}\t{names[i]}\t{(i%455+1)*100000}\t{ld[i]:.17g}\n' for i in index),encoding='utf-8',newline='\n')
    (O/'ref-ld'/(str(chrom)+'.l2.M_5_50')).write_text(str(len(index))+'\n',encoding='utf-8')
se=1/np.sqrt(N*2*af*(1-af))
for column,name in enumerate(['Y','X1','X2']):
    (O/(name+'.ma')).write_text('SNP\tA1\tA2\tfreq\tb\tse\tp\tN\n'+''.join(f'{names[i]}\tA\tG\t{af[i]:.17g}\t{z[i,column]*se[i]:.17g}\t{se[i]:.17g}\t{math.erfc(abs(z[i,column])/math.sqrt(2)):.17g}\t{N}\n' for i in range(m)),encoding='utf-8',newline='\n')
for model,traits in [('single',['Y','X1']),('multi',['Y','X1','X2'])]:
    (O/(model+'.traits')).write_text(''.join(f'{name}\t{name}.ma\n' for name in traits),encoding='utf-8',newline='\n')
    (O/(model+'.bxy')).write_text('X1\t0.35\n'+('X2\t-0.2\n' if model=='multi' else ''),encoding='utf-8',newline='\n')
pins={p.relative_to(O).as_posix():dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(O.rglob('*')) if p.is_file()}
record=dict(schema_version=1,seed=20261120,random_generator='NumPy PCG64',numpy=np.__version__,reference_samples=n,markers=m,chromosomes=22,summary_N=N,participant_data=False,
    preparation='Independent HWE A1 hard calls; literal source PLINK encoding; LD scores are full within-chromosome sums of squared empirical correlations from the same synthetic reference. GWAS Z are marginal Gaussian summaries from this block LD with independent sampling errors, polygenic and declared sparse instruments. No biological claim.',
    trait_order=['Y','X1','X2'],structural_simulation_coefficients=[.35,-.2],mean_chisq=np.mean(z*z,axis=0).tolist(),genome_wide_significant_variants=np.sum(np.abs(z)>5.45131,axis=0).tolist(),supplied_bxy_profile='Declared external values, not asserted to equal estimated marginal GSMR effects',files=pins)
(O/'FIXTURE.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:v for k,v in record.items() if k!='files'}),flush=True)
