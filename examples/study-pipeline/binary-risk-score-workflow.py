"""Offline native binary-score, frozen reuse and leakage acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
Requires NumPy, SciPy, PyYAML. Independent convex objectives use BFGS or
nonnegative coefficient splitting, not the native coordinate optimizer.
All data are synthetic. No clinical/population absolute-risk claim is made.
"""
import argparse,csv,hashlib,json,math,shutil,subprocess,sys
from pathlib import Path
import numpy as np
from scipy import optimize,special
import scipy
import yaml

CASES=('native','ridge','lasso','refit','model-reuse','evaluation-scramble','model-selection-overlap','group-overlap','single-class','iteration-limit','work-limit','limited-related','missing-related')
WAIT={'model-selection-overlap','group-overlap','single-class','iteration-limit','work-limit','limited-related','missing-related'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,header,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(data)
def cli(jar,*args):
    p=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True);assert p.returncode in (0,3),p.stdout+p.stderr;return p.stdout+p.stderr
def artifacts(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'));return [(p,yaml.safe_load((p/'manifest.yaml').read_text(encoding='utf-8'))) for p in sorted((project/'runs'/status['run_id']/'operations').iterdir())]
def one(project):
    found=[v for v in artifacts(project) if v[1]['operation_type']=='risk_score_evaluation'];assert len(found)==1;return found[0]
def pin(root,jar):
    (root/'software').mkdir();target=root/'software'/('jlinalg-'+sha(jar)+'.jar');shutil.copyfile(jar,target);shutil.copyfile(__file__,root/'workflow-verifier-source.py');shutil.copytree(Path(__file__).resolve().parents[2]/'src/test/resources/binary-score',root/'references')
    write(root/'execution-software.json',{'jar':target.relative_to(root).as_posix(),'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'references_sha256':{p.name:sha(p) for p in (root/'references').iterdir()},'python':sys.version,'numpy':np.__version__,'scipy':scipy.__version__});return target
def generate(root,name):
    project=root/name;project.mkdir();data=[]
    for i,r in enumerate(rows(root/'references/input.tsv')):
        role=r['partition'];y=r['y'];x0=r['x0'];group='f'+str(i//2)
        if name=='evaluation-scramble' and role=='evaluation':y=str(1-int(y));x0='10000000000'
        if name=='single-class' and role=='training':y='0'
        if name=='model-selection-overlap' and role=='tuning':role='evaluation'
        if name=='group-overlap' and i==130:group='f0'
        data.append(['p'+str(i),role,group,y,x0,r['x1'],r['x2'],r['c0'],'A' if float(r['c1'])==0 else 'B',.7*float(r['x1'])+.3*float(r['c0'])+math.cos(i),1])
    table(project/'participants.tsv',['sample_id','partition','family','y','x0','x1','x2','c0','c1','related','constant'],data)
    p={'file':'participants.tsv','family':'binary','outcome':'y','features':['x0','x1','x2'],'covariates':['c0','c1'],'categorical_covariates':['c1'],'group_columns':['family'],'resampling_group':'family','lambdas':[.2,.02],'alpha':.5,'binary_baseline_lambda':.3,'missing':'training_mean','refit':name=='refit','bootstraps':0,'subgroups':['c1'],'related_phenotypes':[{'column':'related'}],'preprocessing_provenance':'Synthetic raw predictors, no outcome feature selection or fitted external preprocessing'}
    if name=='ridge':p.update(alpha=0,lambdas=[.02])
    if name=='lasso':p.update(alpha=1,lambdas=[.02])
    if name=='iteration-limit':p['binary_maximum_iterations']=1
    if name=='work-limit':p['max_work']=5000
    if name=='limited-related':p['related_phenotypes'].append({'column':'constant'})
    if name=='missing-related':p['related_phenotypes'].append({'column':'not_measured'})
    if name in ('model-reuse','model-selection-overlap'):
        source=one(root/'native')[0];target=project/'source-model';target.mkdir();model=yaml.safe_load((source/'score-model.yaml').read_text(encoding='utf-8'));shutil.copyfile(source/'score-model.yaml',target/'score-model.yaml');shutil.copyfile(source/model['training_ids'],target/model['training_ids']);p.update(mode='model',model='source-model/score-model.yaml',external_independence='Synthetic external-model evaluation. Local IDs and family blocks are checked, including tuning selection.');p.pop('binary_baseline_lambda')
    write(project/'study.yaml',{'schema_version':1,'project':{'id':'binary-'+name,'title':'Synthetic binary '+name,'description':'Offline explicit score fitting, frozen application and independent held-out metric checks.'},'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[],'hypotheses':[{'id':'H','description':'Independent binary prediction development and evaluation with family blocks','outcome':'y','analysis':{'mode':'manual','goals':['risk_score_evaluation'],'risk_score':p}}]});return project
def solve(y,z,l1,l2):
    n,p=z.shape
    def loss(b):return np.mean(np.logaddexp(0,z@b)-y*(z@b))+.5*np.dot(l2,b*b)+np.dot(l1,abs(b))
    def gradient(b):return z.T@(special.expit(z@b)-y)/n+l2*b
    init=np.zeros(p);init[0]=special.logit(y.mean())
    if np.all(l1==0):r=optimize.minimize(loss,init,jac=gradient,method='BFGS',options={'gtol':1e-11,'maxiter':20000});b=r.x
    else:
        def f(v):b=v[:p]-v[p:];return np.mean(np.logaddexp(0,z@b)-y*(z@b))+.5*np.dot(l2,b*b)+np.dot(l1,v[:p]+v[p:])
        def g(v):a=gradient(v[:p]-v[p:]);return np.r_[a+l1,-a+l1]
        r=optimize.minimize(f,np.r_[np.maximum(init,0),np.maximum(-init,0)],jac=g,bounds=[(0,None)]*(2*p),method='L-BFGS-B',options={'gtol':1e-12,'ftol':1e-15,'maxiter':20000,'maxls':100});b=r.x[:p]-r.x[p:]
    g=gradient(b);kkt=max(np.where(abs(b)<1e-7,np.maximum(0,abs(g)-l1),abs(g+np.sign(b)*l1)));assert kkt<3e-7,(r.message,kkt);return b,float(loss(b))
def reference(data,p,indices,lam):
    y=np.array([float(data[i]['y']) for i in indices]);x=np.array([[float(data[i][v]) if data[i][v]!='NA' else np.nan for v in ['x0','x1','x2']] for i in indices]);c=np.array([[1,float(data[i]['c0']),int(data[i]['c1']=='B')] for i in indices]);means=np.nanmean(x,axis=0);x=np.where(np.isnan(x),means,x);scales=x.std(axis=0);scales[scales==0]=1;cm=c.mean(axis=0);cm[0]=0;cs=c.std(axis=0);cs[cs==0]=1;cstd=(c-cm)/cs;z=np.c_[cstd,(x-means)/scales]
    l1=np.r_[np.zeros(3),np.full(3,lam*p['alpha'])];l2=np.r_[0,.3,.3,np.full(3,lam*(1-p['alpha']))];b,obj=solve(y,z,l1,l2);base,bobj=solve(y,cstd,np.zeros(3),l2[:3]);cw=b[:3]/cs;cw[0]-=np.dot(cm[1:],cw[1:]);bw=base/cs;bw[0]-=np.dot(cm[1:],bw[1:]);fw=b[3:]/scales
    return {'feature_weights':fw,'feature_means':means,'feature_scales':scales,'score_intercept':-np.dot(means,fw),'covariate_weights':cw,'baseline_weights':bw,'objective':obj,'baseline_objective':bobj}
def apply(model,data):
    x=np.array([[float(r[v]) if r[v]!='NA' else np.nan for v in ['x0','x1','x2']] for r in data]);x=np.where(np.isnan(x),model['feature_means'],x);c=np.array([[1,float(r['c0']),int(r['c1']=='B')] for r in data]);score=x@np.array(model['feature_weights'])+model['score_intercept'];return score,special.expit(c@model['covariate_weights']+score),special.expit(c@model['baseline_weights'])
def near(a,b,label,atol=3e-6):assert np.allclose(a,b,rtol=1e-5,atol=atol),(label,a,b)
def verify_case(root,name):
    project=root/name;folder,m=one(project);assert m['state']==('waiting_for_input' if name in WAIT else 'complete'),(name,m['state'],m.get('summary'));checks=1
    if name in WAIT and name not in ('limited-related','missing-related'):assert not (folder/'participant-scores.tsv').exists() and not (folder/'score-model.yaml').exists();return {'case':name,'checks':checks+2,'state':'waiting_for_input'}
    assert m['details']['score_evaluation_complete'] is True;checks+=1
    data=rows(project/'participants.tsv');policy=json.loads((project/'study.yaml').read_text())['hypotheses'][0]['analysis']['risk_score'];score_table=rows(folder/'participant-scores.tsv');y=np.array([float(r['y']) for r in data]);evaluation=np.array([i for i,r in enumerate(data) if r['partition']=='evaluation']);p=policy
    if name=='model-reuse':model=yaml.safe_load((folder/'supplied-score-model.yaml').read_text(encoding='utf-8'));assert m['details']['model_refitted'] is False;parameters=m['details']['method_report']['parameters'];assert 'lambdas' not in parameters and parameters['source_frozen_model']['alpha']==model['alpha'] and parameters['source_frozen_model']['lambda']==model['lambda'];assert (folder/'participant-scores.tsv').read_bytes()==(one(root/'native')[0]/'participant-scores.tsv').read_bytes();checks+=2
    else:
        training=[i for i,r in enumerate(data) if r['partition']=='training'];tuning=[i for i,r in enumerate(data) if r['partition']=='tuning'];losses=[]
        for lam in p['lambdas']:
            fit=reference(data,p,training,lam);s,pr,ba=apply(fit,data);eta=np.array([[1,float(data[i]['c0']),int(data[i]['c1']=='B')] for i in tuning])@fit['covariate_weights']+s[tuning];losses.append(float(np.mean(np.logaddexp(0,eta)-y[tuning]*eta)))
        selected=p['lambdas'][int(np.argmin(losses))];expected=reference(data,p,training+tuning if p['refit'] else training,selected);model=yaml.safe_load((folder/'score-model.yaml').read_text(encoding='utf-8'));assert model['lambda']==selected and model['development_rows']==(130 if p['refit'] else 80);assert model['development_identity_rows']==130;checks+=3
        for key in ['feature_weights','feature_means','feature_scales','score_intercept','covariate_weights','baseline_weights']:near(model[key],expected[key],key);checks+=1
        native_tuning=rows(folder/'tuning.tsv');near([float(r['tuning_log_loss']) for r in native_tuning],losses,'tuning loss');near(m['details']['binary_full_fit']['objective'],expected['objective'],'full objective',1e-10);checks+=2
        if name=='evaluation-scramble':original=yaml.safe_load((one(root/'native')[0]/'score-model.yaml').read_text(encoding='utf-8'));assert all(model[key]==original[key] for key in ['feature_weights','feature_means','covariate_weights','baseline_weights','lambda']);checks+=1
    s,pr,ba=apply(model,data);near([float(r['score']) for r in score_table],s,'scores');near([float(r['prediction']) for r in score_table],pr,'probabilities');near([float(r['baseline']) for r in score_table],ba,'frozen comparator');checks+=3
    metrics={r['metric']:float(r['estimate']) for r in rows(folder/'risk-score-evaluation.tsv') if r['subgroup_column']=='all'};expected={'brier':np.mean((y[evaluation]-pr[evaluation])**2),'baseline_brier':np.mean((y[evaluation]-ba[evaluation])**2)}
    for key,values in [('log_loss',pr),('baseline_log_loss',ba)]:clipped=np.clip(values[evaluation],1e-15,1-1e-15);expected[key]=np.mean(-y[evaluation]*np.log(clipped)-(1-y[evaluation])*np.log1p(-clipped))
    def auc(values):
        positive=values[evaluation][y[evaluation]==1];negative=values[evaluation][y[evaluation]==0];delta=positive[:,None]-negative[None,:];return np.mean((delta>0)+.5*(delta==0))
    expected.update(auc=auc(pr),score_auc=auc(s),baseline_auc=auc(ba))
    for key,value in expected.items():near(metrics[key],value,key,1e-7);checks+=1
    assert 'sampling prevalence' in m['details']['required_manuscript_caveat']
    if name in ('limited-related','missing-related'):assert not (folder/'related-phenotypes.tsv').exists();assert all(r['adjusted_p']=='NA' for r in rows(folder/'related-phenotypes-limited.tsv'));checks+=2
    else:assert len(rows(folder/'related-phenotypes.tsv'))==1;checks+=1
    return {'case':name,'checks':checks,'state':m['state']}
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256']==sha(root/software['jar']);assert sha(Path(__file__))==software['verifier_sha256']==sha(root/'workflow-verifier-source.py')
    for name,digest in software['references_sha256'].items():assert sha(root/'references'/name)==digest
    cases=[verify_case(root,name) for name in CASES];sealed=0
    for name in CASES:
        for folder,m in artifacts(root/name):
            for path,digest in m.get('output_sha256',{}).items():assert sha(folder/path)==digest;sealed+=1
    write(root/'verification.json',{'passed':True,'cases':cases,'checks':sum(c['checks'] for c in cases),'sealed_outputs':sealed,'jar_sha256':software['jar_sha256'],'verifier_sha256':software['verifier_sha256'],'scope':'Synthetic software, convex point, held-out metric and leakage acceptance. No clinical utility or population absolute-risk certification.'});print(json.dumps({'passed':True,'cases':len(cases),'checks':sum(c['checks'] for c in cases),'sealed_outputs':sealed}))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);a=parser.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.mode=='run':
        root.mkdir();jar=pin(root,jar)
        for name in CASES:
            p=generate(root,name);(p/'workflow.log').write_text(cli(jar,'auto',p/'study.yaml','--offline'),encoding='utf-8');(p/'replay.log').write_text(cli(jar,'auto',p/'study.yaml','--offline'),encoding='utf-8');(p/'verify.log').write_text(cli(jar,'verify','--project',p),encoding='utf-8')
    verify(root,jar)
if __name__=='__main__':main()
