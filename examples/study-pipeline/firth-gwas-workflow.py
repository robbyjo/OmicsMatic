"""Offline Firth study acceptance and exact conditional null calibration audit.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
Requires NumPy, SciPy and PyYAML. Point agreement is distinct from tail calibration.
"""
import argparse,csv,hashlib,json,math,shutil,subprocess,sys
from pathlib import Path
import numpy as np
import scipy
from scipy import optimize,special,stats
import yaml

CASES=('balanced','complete-separation','nuisance-separation','imbalanced','quasi-separation','offset','dosage','iteration-limit','collinear','missing-dosage','extreme-separation','missing-offset')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,header,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(data)
def cli(jar,*args):
    process=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True)
    assert process.returncode in (0,3),process.stdout+process.stderr
    return process.stdout+process.stderr
def artifacts(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return [(p,yaml.safe_load((p/'manifest.yaml').read_text(encoding='utf-8'))) for p in sorted((project/'runs'/status['run_id']/'operations').iterdir())]
def one(project,kind):
    found=[(p,m) for p,m in artifacts(project) if m['operation_type']==kind];assert len(found)==1,(project,kind);return found[0]
def pin(root,jar):
    (root/'software').mkdir();target=root/'software'/('jlinalg-'+sha(jar)+'.jar');shutil.copyfile(jar,target);shutil.copyfile(Path(__file__),root/'workflow-verifier-source.py')
    references=Path(__file__).resolve().parents[2]/'src/test/resources/firth-logistic';shutil.copytree(references,root/'references')
    write(root/'execution-software.json',{'jar':target.relative_to(root).as_posix(),'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),
        'reference_sha256':{p.name:sha(p) for p in (root/'references').iterdir() if p.is_file()},'python':sys.version,'numpy':np.__version__,'scipy':scipy.__version__,'pyyaml':yaml.__version__});return target
def generate(root,name):
    source={'iteration-limit':'complete-separation','collinear':'balanced','missing-dosage':'dosage','extreme-separation':'complete-separation','missing-offset':'offset'}.get(name,name)
    data=rows(root/'references'/(source+'.tsv'));p=len([c for c in data[0] if c.startswith('x')]);n=len(data)
    y=[int(r['y']) for r in data];z=[float(r['x2']) if p==3 else 0 for r in data];g=[float(r['x'+str(p)]) for r in data];offset=[float(r['offset']) for r in data]
    if name=='extreme-separation':n=2000;y=[int(i<1000) for i in range(n)];g=y.copy();z=[0]*n;offset=[0]*n
    if name=='collinear':z=g.copy()
    if name=='missing-dosage':g[0]=None
    if name=='missing-offset':offset[0]=None
    project=root/name;project.mkdir();table(project/'phenotype.tsv',['id','y','z','o'],reversed([['a'+str(i),'disease' if y[i] else 'healthy',z[i],offset[i]] for i in range(n)]))
    vcf=['##fileformat=VCFv4.2','##FORMAT=<ID=DS,Number=1,Type=Float,Description="Dosage">','\t'.join(['#CHROM','POS','ID','REF','ALT','QUAL','FILTER','INFO','FORMAT']+['a'+str(i) for i in range(n)])]
    for position,variant,values in [(100,'v',g),(200,'low-mac',[int(i<19) for i in range(n)])]:vcf.append('\t'.join(['1',str(position),variant,'A','G','.','PASS','.','DS']+['.' if value is None else str(value) for value in values]))
    (project/'genotype.vcf').write_text('\n'.join(vcf)+'\n',encoding='utf-8')
    association={'model':'glm','family':'binomial','binary_test':'firth-lrt','case_value':'disease','control_value':'healthy','threads':2,'minimum_sample_call_rate':0,'offset_column':'o'}
    if name=='iteration-limit':association['firth_maximum_iterations']=1
    analysis={'mode':'manual','goals':['association','candidate_prioritization'],'association':association,'harmonization':{'effect_outliers':{'enabled':False}}}
    study={'schema_version':1,'project':{'id':'firth-'+name,'title':'Synthetic Firth '+name,'description':'Offline independent point/numerical checks. No biological result or universal sparse-tail calibration claim.'},
        'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[{'id':'a','cohort':'synthetic','type':'gwas','stage':'processed_matrix','species':'human','genome_build':'GRCh38','feature_id_type':'variant','files':{'matrix':'genotype.vcf','phenotype':'phenotype.tsv'},'columns':{'sample_id':'id'}}],
        'hypotheses':[{'id':'H','description':'Synthetic independent Bernoulli Firth association.','outcome':'y','covariates':(['z'] if p==3 else []),'inputs':{'discovery':['a']},'analysis':analysis}]}
    write(project/'study.yaml',study);write(project/'truth.json',{'source':source,'n':n,'y':y,'z':z if p==3 else None,'g':g,'offset':offset});return project
def close(actual,expected,label,rtol=3e-6,atol=1e-9):assert math.isclose(float(actual),float(expected),rel_tol=rtol,abs_tol=atol),(label,actual,expected)
def independent_fit(truth):
    kept=[i for i,value in enumerate(truth['offset']) if value is not None];y=np.array(truth['y'])[kept];g=np.array([np.nan if truth['g'][i] is None else truth['g'][i] for i in kept]);g[np.isnan(g)]=np.nanmean(g)
    x=np.column_stack([np.ones(len(y))]+([np.array(truth['z'])[kept]] if truth['z'] is not None else [])+[g]);offset=np.array([truth['offset'][i] for i in kept])
    def objective(beta):
        eta=x@beta+offset;w=np.exp(-np.abs(eta))/(1+np.exp(-np.abs(eta)))**2;sign,logdet=np.linalg.slogdet(x.T@(w[:,None]*x))
        return np.inf if sign<=0 else float(np.sum(np.logaddexp(0,eta)-y*eta)-.5*logdet)
    fit=optimize.minimize(objective,np.zeros(x.shape[1]),jac='3-point',method='BFGS',options={'gtol':1e-7,'maxiter':1000})
    restricted=optimize.minimize(lambda b:objective(np.r_[b,0]),fit.x[:-1],jac='3-point',method='BFGS',options={'gtol':1e-7,'maxiter':1000})
    assert np.linalg.norm(fit.jac,ord=np.inf)<2e-5 and np.linalg.norm(restricted.jac,ord=np.inf)<2e-5,(fit.message,restricted.message)
    eta=x@fit.x+offset;w=np.exp(-np.abs(eta))/(1+np.exp(-np.abs(eta)))**2;se=math.sqrt(np.linalg.inv(x.T@(w[:,None]*x))[-1,-1]);lr=max(0,2*(restricted.fun-fit.fun))
    return {'beta':fit.x[-1],'se':se,'full_pll':-fit.fun,'null_pll':-restricted.fun,'lr':lr,'p_value':stats.chi2.sf(lr,1)}
def verify_case(root,name):
    project=root/name;truth=json.loads((project/'truth.json').read_text(encoding='utf-8'));folder,manifest=one(project,'association');assert manifest['state']=='complete'
    raw=rows(folder/'association.tsv');assert len(raw)==2;row,low=raw;assert row['id']=='v' and low['id']=='low-mac';assert low['status']=='filtered' and low['filter_reason']=='BELOW_MINIMUM_MAC';assert not low['test_method']
    kept=[i for i,value in enumerate(truth['offset']) if value is not None]
    assert row['test_method']=='firth_penalized_likelihood_ratio' and row['statistic_type']=='chi_square';assert row['standard_error_method']=='ordinary_fisher_at_penalized_estimate';close(row['n_samples'],len(kept),'analyzed N');close(row['n_cases'],sum(truth['y'][i] for i in kept),'case N')
    assert 'not a Wald test' in manifest['details']['required_manuscript_caveat'] and 'No finite-sample' in manifest['details']['required_manuscript_caveat']
    standard=rows(folder/'standard-results/a.tsv');checks=2
    if name in ('iteration-limit','collinear'):
        assert row['status']=='failed' and not row['p_value'] and not row['fdr_bh'] and not standard;assert manifest['details']['evidence_disposition']=='not_estimable'
        assert 'iteration_limit' in row['convergence_reason'] if name=='iteration-limit' else row['convergence_reason']=='fit_error'
        return {'case':name,'status':'explicit_fit_failure','checks':checks}
    assert row['status']=='ok' and row['fit_converged']==row['null_fit_converged']=='true';assert len(standard)==1
    for field in ['test_method','statistic_type','calibration','estimator','standard_error_method','fit_converged','null_fit_converged','fit_iterations','null_fit_iterations','penalized_log_likelihood','null_penalized_log_likelihood','convergence_reason','log_p_value']:assert standard[0][field]==row[field];checks+=1
    reference=independent_fit(truth)
    for field,key in [('beta','beta'),('standard_error','se'),('penalized_log_likelihood','full_pll'),('null_penalized_log_likelihood','null_pll'),('statistic','lr'),('p_value','p_value')]:close(row[field],reference[key],'SciPy objective '+field);checks+=1
    if name not in ('missing-dosage','extreme-separation','missing-offset'):
        expected=next(r for r in rows(root/'references/reference.tsv') if r['case']==truth['source'])
        for field,key in [('beta','beta'),('standard_error','se'),('penalized_log_likelihood','full_pll'),('null_penalized_log_likelihood','null_pll'),('statistic','lr'),('p_value','p_value')]:close(row[field],expected[key],'Upstream R '+field);checks+=1
    elif name=='missing-dosage':close(row['called'],truth['n']-1,'preimputation called');checks+=1
    elif name=='extreme-separation':assert float(row['p_value'])==0 and float(row['log_p_value']) < -1000 and math.isfinite(float(row['log_p_value']));checks+=1
    else:
        assert len(rows(one(project,'participant_qc')[0]/'analysis-phenotype.tsv'))==truth['n']-1
        assert any(r['sample_id']=='a0' and r['reason']=='missing_outcome_or_covariate' for r in rows(one(project,'participant_qc')[0]/'sample-exclusions.tsv'));checks+=2
    close(row['fdr_bh'],row['p_value'],'one retained test BH');checks+=1
    return {'case':name,'status':'estimated','checks':checks}
def calibration():
    result=[]
    for n,cases,carriers in [(1000,5,20),(1000,10,20),(10000,10,20),(1000,100,20),(1000,5,100),(120,24,24)]:
        def pll(a,p0,p1):return a*math.log(p1)+(carriers-a)*math.log1p(-p1)+(cases-a)*math.log(p0)+(n-carriers-cases+a)*math.log1p(-p0)+.5*math.log(carriers*(n-carriers)*p0*(1-p0)*p1*(1-p1))
        p0=(cases+1)/(n+2);support=[]
        for a in range(max(0,cases-(n-carriers)),min(cases,carriers)+1):
            lr=max(0,2*(pll(a,(cases-a+.5)/(n-carriers+1),(a+.5)/(carriers+1))-pll(a,p0,p0)))
            support.append({'case_carriers':a,'firth_asymptotic_p':float(stats.chi2.sf(lr,1)),'exact_null_mass':float(stats.hypergeom.pmf(a,n,cases,carriers))})
        close(sum(r['exact_null_mass'] for r in support),1,'exact null mass')
        rates={str(alpha):sum(r['exact_null_mass'] for r in support if r['firth_asymptotic_p']<=alpha) for alpha in [.05,.01,.001]}
        result.append({'n':n,'cases':cases,'heterozygous_carriers':carriers,'conditional_null_rejection_rates':rates,'support':support})
    assert result[0]['conditional_null_rejection_rates']['0.05']>.09
    return {'exact_enumeration':True,'cases':result,'interpretation':'Fixed case count and binary heterozygote indicator, intercept only. Closed-form Firth full/constrained objectives plus exact hypergeometric null mass. Demonstrates non-universal asymptotic tail calibration; not a benchmark for covariate-adjusted or related-sample designs.'}
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text(encoding='utf-8'));assert sha(jar)==software['jar_sha256']==sha(root/software['jar']);assert sha(Path(__file__))==software['verifier_sha256']==sha(root/'workflow-verifier-source.py')
    for name,digest in software['reference_sha256'].items():assert sha(root/'references'/name)==digest
    cases=[verify_case(root,name) for name in CASES];write(root/'calibration-enumeration.json',calibration());write(root/'verification.json',{'passed':True,'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'cases':cases,'scope':'Point/numerical and workflow checks; exact null enumeration exposes finite-sample calibration limits. No claim of universal sparse-tail or biological validity.'});return cases
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['run','verify']);p.add_argument('directory',type=Path);p.add_argument('--jar',type=Path,required=True);a=p.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir();jar=pin(root,jar)
        for name in CASES:
            project=generate(root,name);(project/'execution.log').write_text(cli(jar,'auto',project/'study.yaml','--offline'),encoding='utf-8');assert all(m['state'] in ('complete','not_applicable') for f,m in artifacts(project)),[(m['operation_type'],m['state']) for f,m in artifacts(project)]
            (project/'replay.log').write_text(cli(jar,'auto',project/'study.yaml','--offline'),encoding='utf-8');assert all(m.get('reused_from_cache') is True for f,m in artifacts(project) if m['operation_type']!='report')
            (project/'verify.log').write_text(cli(jar,'verify','--project',project),encoding='utf-8')
    cases=verify(root,jar);print(json.dumps({'passed':True,'cases':len(cases),'checks':sum(c['checks'] for c in cases),'jar_sha256':sha(jar),'directory':str(root)}))
if __name__=='__main__':main()
