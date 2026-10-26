"""Offline packaged exact-conditional GWAS acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
References enumerate individual outcome subsets with rational weights. Null
calibration enumerates fixed-margin hypergeometric supports; no simulations,
asymptotic tests or claims about arbitrary covariates/relatedness are used.
Requires PyYAML; raw research inputs and shared resources are never modified.
"""
import argparse,csv,hashlib,json,math,shutil,subprocess,sys
from fractions import Fraction
from pathlib import Path
import yaml

REFERENCES=('ternary','stratified','offsets','missing','uninformative','constant-stratum-genotype','major-allele')
CASES=REFERENCES+('test-only','effect-iteration-limit','work-limit','mixed-state-limit','null-1000-5-20','null-1000-10-20','null-120-24-24')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,data):p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,header,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(data)
def cli(jar,*args):
    p=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True)
    assert p.returncode in (0,3),p.stdout+p.stderr
    return p.stdout+p.stderr
def operations(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return [(p,yaml.safe_load((p/'manifest.yaml').read_text(encoding='utf-8'))) for p in sorted((project/'runs'/status['run_id']/'operations').iterdir())]
def one(project,kind):
    values=[v for v in operations(project) if v[1]['operation_type']==kind];assert len(values)==1,(project,kind);return values[0]
def pin(root,jar):
    (root/'software').mkdir();target=root/'software'/('jlinalg-'+sha(jar)+'.jar');shutil.copyfile(jar,target);shutil.copyfile(__file__,root/'workflow-verifier-source.py')
    shutil.copytree(Path(__file__).resolve().parents[2]/'src/test/resources/exact-conditional-logit',root/'references')
    write(root/'execution-software.json',{'jar':target.relative_to(root).as_posix(),'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'reference_sha256':{p.name:sha(p) for p in (root/'references').iterdir() if p.is_file()},'python':sys.version,'pyyaml':yaml.__version__});return target
def generate(root,name):
    folder=root/name;folder.mkdir();association={'model':'glm','family':'binomial','binary_test':'exact-conditional','threads':2,'minimum_sample_call_rate':0,'offset_column':'o'}
    if name.startswith('null-'):
        n,cases,carriers=map(int,name.split('-')[1:]);y=[int(i<cases) for i in range(n)];s=[0]*n;o=[0]*n;variants=[]
        for a in range(max(0,cases-(n-carriers)),min(cases,carriers)+1):variants.append(('a'+str(a),[int(i<a or cases<=i<cases+carriers-a) for i in range(n)]))
        association['exact_effect_maximum_parameters']=0;truth={'null_margins':[n,cases,carriers]}
    else:
        source=name if name in REFERENCES else 'stratified';data=rows(root/'references'/(source+'.tsv'));y=[int(r['y']) for r in data];s=[int(r['stratum']) for r in data];o=[float(r['offset']) for r in data];g=[None if r['g']=='NA' else int(r['g']) for r in data];n=len(y);variants=[('v',g)];truth={'reference':source}
        association.update(minimum_mac=0,maximum_marker_missing=1)
        if len(set(s))>1:association['exact_strata_column']='stratum'
        if name=='test-only':association['exact_effect_maximum_parameters']=0
        if name=='effect-iteration-limit':association['firth_maximum_iterations']=1
        if name=='work-limit':association['exact_maximum_work']=1
        if name=='mixed-state-limit':association['exact_maximum_states']=5;variants.append(('small',[int(i<4 and i%2==0) for i in range(n)]))
    table(folder/'phenotype.tsv',['id','y','stratum','o'],reversed([['a'+str(i),y[i],s[i],o[i]] for i in range(n)]))
    vcf=['##fileformat=VCFv4.2','##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">','\t'.join(['#CHROM','POS','ID','REF','ALT','QUAL','FILTER','INFO','FORMAT']+['a'+str(i) for i in range(n)])]
    for i,(identifier,g) in enumerate(variants):vcf.append('\t'.join(['1',str(100+i),identifier,'A','G','.','PASS','.','GT']+['./.' if v is None else ['0/0','0/1','1/1'][v] for v in g]))
    (folder/'genotype.vcf').write_text('\n'.join(vcf)+'\n',encoding='utf-8')
    analysis={'mode':'manual','goals':['association','candidate_prioritization'],'association':association,'harmonization':{'effect_outliers':{'enabled':False}}}
    write(folder/'study.yaml',{'schema_version':1,'project':{'id':'exact-'+name,'title':'Synthetic exact '+name,'description':'Offline finite conditional null acceptance. No biological finding or arbitrary covariate/kinship calibration claim.'},'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[{'id':'a','cohort':'synthetic','type':'gwas','stage':'processed_matrix','species':'human','genome_build':'GRCh38','feature_id_type':'variant','files':{'matrix':'genotype.vcf','phenotype':'phenotype.tsv'},'columns':{'sample_id':'id'}}],'hypotheses':[{'id':'H','description':'Independent Bernoulli hard-call finite conditional test.','outcome':'y','inputs':{'discovery':['a']},'analysis':analysis}]})
    truth.update(n=n,cases=sum(y));write(folder/'truth.json',truth);return folder
def near(a,b,label):assert math.isclose(float(a),float(b),rel_tol=1e-10,abs_tol=1e-12),(label,a,b)
def null_support(n,cases,carriers):
    denominator=math.comb(n,cases)
    mass={a:Fraction(math.comb(carriers,a)*math.comb(n-carriers,cases-a),denominator) for a in range(max(0,cases-(n-carriers)),min(cases,carriers)+1)}
    p={a:sum((v for v in mass.values() if v<=value*Fraction(10000001,10000000)),Fraction()) for a,value in mass.items()}
    return mass,p
def verify_case(root,name):
    project=root/name;folder,m=one(project,'association');raw=rows(folder/'association.tsv');standard=rows(folder/'standard-results/a.tsv');truth=json.loads((project/'truth.json').read_text(encoding='utf-8'));checks=0
    for row in raw:
        assert row['test_method']=='exact_conditional_genotype_score' and row['statistic_type']=='conditional_centered_allele_score' and row['df_numerator']=='';checks+=3
    assert 'fixed known offsets' in m['details']['required_manuscript_caveat'];checks+=1
    if name in ('work-limit','mixed-state-limit'):
        assert m['state']=='waiting_for_input' and m['details']['primary_selection_eligible'] is False
        assert any(r['exact_status']=='budget_exceeded' and not r['p_value'] for r in raw)
        assert all(not r['fdr_bh'] for r in raw) and (folder/'request.yaml').is_file()
        if name=='mixed-state-limit':assert len(standard)==1 and any(r['status']=='ok' for r in raw)
        assert one(project,'candidate_prioritization')[1]['state']!='complete'
        return {'case':name,'checks':checks+5,'status':'incomplete_family_fdr_withheld'}
    assert m['state']=='complete' and len(raw)==len(standard)
    if 'null_margins' in truth:
        mass,p=null_support(*truth['null_margins']);rates={}
        for r in raw:near(r['p_value'],p[int(r['id'][1:])],'fixed-margin exact P');assert r['result_type']=='p_value_only';checks+=2
        for alpha in (.05,.01,.001):
            rate=sum((mass[int(r['id'][1:])] for r in raw if float(r['p_value'])<=alpha),Fraction());assert float(rate)<=alpha+1e-12;rates[str(alpha)]=float(rate);checks+=1
        return {'case':name,'checks':checks,'status':'conditional_null_enumerated','rejection_rates':rates,'scope':'Fixed-margin binary heterozygote genotype, intercept only; no estimated offsets or relatedness'}
    expected=next(r for r in rows(root/'references/reference.tsv') if r['case']==truth['reference']);r=raw[0];s=standard[0]
    assert r['status']=='ok' and r['exact_status']=='complete';near(r['p_value'],expected['p_value'],'rational enumeration P');near(r['log_p_value'],expected['log_p_value'],'rational log P');near(r['conditional_null_mean'],expected['mean'],'null mean');near(r['conditional_null_variance'],expected['variance'],'null variance');checks+=6
    for field in ['test_method','statistic_type','calibration','log_p_value','result_type','effect_estimation_status','exact_status','exact_alternative','conditional_score','conditional_null_mean','conditional_null_variance','exact_score_direction','test_n_samples','test_n_cases','test_n_controls','test_strata','informative_strata']:
        assert s[field]==r[field],field;checks+=1
    near(s['n'],expected['observations'],'test n');near(s['qc_n_samples'],truth['n'],'QC n');near(s['n_cases'],expected['cases'],'test cases');near(r['fdr_bh'],r['p_value'],'one test BH');checks+=4
    if name in ('test-only','effect-iteration-limit'):assert r['result_type']=='p_value_only' and not r['beta'] and s['beta']==s['se']=='NA';checks+=3
    assert one(project,'candidate_prioritization')[1]['state']=='complete';checks+=1
    return {'case':name,'checks':checks,'status':'exact_test_retained','result_type':r['result_type']}
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text(encoding='utf-8'));assert sha(jar)==software['jar_sha256']==sha(root/software['jar']);assert sha(Path(__file__))==software['verifier_sha256']==sha(root/'workflow-verifier-source.py')
    for name,digest in software['reference_sha256'].items():assert sha(root/'references'/name)==digest
    cases=[verify_case(root,name) for name in CASES]
    for name in CASES:
        for folder,m in operations(root/name):
            for relative,digest in m.get('output_sha256',{}).items():assert sha(folder/relative)==digest,(folder,relative)
    write(root/'verification.json',{'passed':True,'cases':cases,'checks':sum(c['checks'] for c in cases),'jar_sha256':software['jar_sha256'],'verifier_sha256':software['verifier_sha256'],'scope':'Offline packaged numeric/method/QC/FDR/state and output integrity checks. No public release, biological result, or AI appraisal.'})
    print(json.dumps({'passed':True,'cases':len(cases),'checks':sum(c['checks'] for c in cases),'jar_sha256':software['jar_sha256']}))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',required=True,type=Path);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.mode=='run':
        root.mkdir();jar=pin(root,jar)
        for name in CASES:
            project=generate(root,name);(project/'workflow-log.txt').write_text(cli(jar,'auto',project/'study.yaml','--offline'),encoding='utf-8')
            (project/'replay-log.txt').write_text(cli(jar,'auto',project/'study.yaml','--offline'),encoding='utf-8')
            # Diagnostics/reports over retryable dependencies can legitimately rerun.
            assert all(m.get('reused_from_cache') is True for _,m in operations(project) if m['operation_type'] in ('validate_harmonize','participant_qc','association','candidate_prioritization') and m['state'] in ('complete','not_applicable'))
            (project/'verify-log.txt').write_text(cli(jar,'verify','--project',project),encoding='utf-8')
    verify(root,jar)
if __name__=='__main__':main()
