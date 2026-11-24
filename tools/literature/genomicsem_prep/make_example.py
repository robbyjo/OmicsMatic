"""Create synthetic GenomicSEM preparation inputs and explicit NEW-output requests.

No analysis, downloads, installations or paper-data reproduction is performed.
The LD/annotation data are artificial and are not a reference resource.
"""
from pathlib import Path
import argparse,csv,gzip,json,math,random

PROFILE='genomicsem-preparation-da95d43-r461-win64-lavaan0619-v1'

def make(runtime,addons,out):
    out=out.absolute();out.mkdir();rng=random.Random(769);traits=['T1','T2','T3','T4'];null=[None]*4
    for folder in ('inputs','ld','wld','strat','freq'):(out/folder).mkdir()
    def table(name,header,rows):
        path=out/name;opener=gzip.open if name.endswith('.gz')else open
        with opener(path,'wt',encoding='utf-8',newline='')as f:
            w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(rows)
    ref=[];raw=[[]for _ in traits];ld=[]
    for i in range(5000):
        chromosome=i//1250+1;bp=1001+(i%1250)*100;maf=rng.uniform(.08,.45);a1,a2=('A','G')if i%2==0 else('C','T');identity='rsSynthetic'+str(i+1);row=[identity,chromosome,bp,maf,a1,a2];ref.append(row)
        l1,l2,l3=rng.uniform(5,40),rng.uniform(1,12),rng.uniform(1,8);ld.append((l1,l2,l3));sample=[92000+2000*j+500*math.sin((i+1)/23)for j in range(4)]
        s=[[([.55,.6,.65,.7][a]*[.55,.6,.65,.7][b]+(.3 if a==b else 0))for b in range(4)]for a in range(4)]
        c=[[(1 if a==b else .05)+(l1+l2)/400000*math.sqrt(sample[a]*sample[b])*s[a][b]for b in range(4)]for a in range(4)];lower=[[0.]*4 for _ in traits]
        for a in range(4):
            for b in range(a+1):
                x=c[a][b]-sum(lower[a][k]*lower[b][k]for k in range(b));lower[a][b]=math.sqrt(x)if a==b else x/lower[b][b]
        noise=[rng.gauss(0,1)for _ in traits];z=[sum(lower[a][b]*noise[b]for b in range(a+1))for a in range(4)]
        for j in range(4):
            se=1/math.sqrt(sample[j]*2*maf*(1-maf));beta=z[j]*se;alleles=[a1,a2]
            if j==1 and i<17:alleles.reverse();beta=-beta
            raw[j].append([identity,*alleles,beta,se,math.erfc(abs(z[j])/math.sqrt(2)),sample[j],maf,.99])
    table('inputs/reference.tsv',['SNP','CHR','BP','MAF','A1','A2'],ref);table('inputs/hm3.tsv',['SNP','A1','A2'],[[r[0],r[4],r[5]]for r in ref])
    for j,t in enumerate(traits):
        table('inputs/'+t+'.tsv',['SNP','A1','A2','BETA','SE','P','N','MAF','INFO'],raw[j])
        table('inputs/fusion-'+str(j+1)+'.tsv',['FILE','ID','HSQ','TWAS.Z','PERM.PV','PERM.N'],[['weights/Panel1/Gene1.wgt.RDat','Gene1',.3,1.5+(j+1)*.05,.02,1000],['weights/Panel1/Gene2.wgt.RDat','Gene2',.4,-2+(j+1)*.05,0,1000],['weights/Panel2/Gene1.wgt.RDat','Gene1',.3,(j+1)*.05,1,1000]])
    for ch in range(1,5):
        indices=range((ch-1)*1250,ch*1250)
        table(f'ld/{ch}.l2.ldscore.gz',['CHR','SNP','BP','L2'],[[ch,ref[i][0],ref[i][2],ld[i][0]+ld[i][1]]for i in indices]);(out/f'ld/{ch}.l2.M_5_50').write_text('100000\n')
        table(f'wld/{ch}.l2.ldscore.gz',['CHR','SNP','BP','L2'],[[ch,ref[i][0],ref[i][2],ld[i][0]]for i in indices])
        table(f'strat/baseline.{ch}.l2.ldscore.gz',['CHR','SNP','BP','baseL2','groupL2','continuousL2'],[[ch,ref[i][0],ref[i][2],*ld[i]]for i in indices]);(out/f'strat/baseline.{ch}.l2.M_5_50').write_text('100000\t40000\t50000\n')
        table(f'strat/baseline.{ch}.annot.gz',['CHR','BP','SNP','base','group','continuous'],[[ch,ref[i][2],ref[i][0],1,[1,0,0,1,0][i%5],.1+.8*(i%1250)/1249]for i in indices]);table(f'freq/reference.{ch}.frq',['CHR','SNP','MAF'],[[ch,ref[i][0],ref[i][3]]for i in indices])
    files=['inputs/'+t+'.tsv'for t in traits];munged=['munge-run/products/munged/'+t+'.sumstats.gz'for t in traits]
    def request(name,op,paths,options):
        common=dict(trait_names=traits,seed=769,upstream_behavior_reviewed=True,warning_policy='RETAIN_AND_FLAG',unavailable_output_policy='FAIL');common.update(options)
        q=dict(schema_version=1,mode='RUN',profile=PROFILE,operation=op,runtime_home=str(runtime.absolute()),runtime_addons=None if addons is None else str(addons.absolute()),out=name+'-run',bundle='.',paths=paths,options=common);(out/(name+'-request.json')).write_text(json.dumps(q,indent=2)+'\n',encoding='utf-8')
    request('munge','MUNGE',dict(files=files,hm3='inputs/hm3.tsv'),dict(N=null,info_filter=.9,maf_filter=.01,column_names={},parallel=False,cores=1))
    request('ldsc','LDSC',dict(traits=munged,ld='ld',wld='wld'),dict(sample_prevalence=null,population_prevalence=null,separate_weights=True,chromosomes=4,blocks=40,standardized=True,select=False,chisq_max=100))
    request('sumstats','SUMSTATS',dict(files=files,reference='inputs/reference.tsv'),dict(N=null,OLS=[True]*4,linprob=[False]*4,se_logit=[False]*4,betas=[False]*4,info_filter=.9,maf_filter=.01,keep_indel=False,parallel=False,cores=1,remove_ambiguous=False,direction_filter=False))
    request('fusion','READ_FUSION',dict(files=['inputs/fusion-'+str(j)+'.tsv'for j in range(1,5)]),dict(N=[100000,110000,120000,130000],binary=[False,False,True,True],permutation=False))
    request('stratified','S_LDSC',dict(traits=munged,ld_prefixes=['strat/baseline.'],weights_prefix='wld/',frequency_prefix='freq/reference.'),dict(sample_prevalence=null,population_prevalence=null,blocks=40,exclude_continuous=True))
    (out/'model.txt').write_text('F =~ T1 + T2 + T3 + T4\n')
    request('enrich','ENRICH',dict(stratified='stratified-run/products/stratified',model='model.txt'),dict(annotations='ALL',parameters=['F~~F','T1~~T1'],fix='regressions',std_lv=False,remove_flanking=True,tau=False,include_baseline=True,tolerance=None,fixed_parameters=[],smoothing_policy='FAIL'))
    (out/'README.txt').write_text('Synthetic demonstration only; artificial LD and annotations are not a research reference. Run munge, ldsc, sumstats, fusion, stratified, enrich in that order. No analysis was performed by the generator.\n');return out

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--addons',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    try:print(make(a.runtime,a.addons,a.out));return 0
    except Exception as e:print('FAIL2: example not complete: '+str(e));return 2

if __name__=='__main__':raise SystemExit(main())
