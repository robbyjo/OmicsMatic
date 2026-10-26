"""Offline grouped nested score validation with independent NumPy/SciPy fits.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR
Generated quantitative/binary fixtures, no network/model requests. Independent
ridge references refit every inner/outer/final preprocessing and model. This
does not validate biological generalization, upstream leakage or clinical use.
"""
import argparse,csv,hashlib,json,math,shutil,subprocess,sys
from pathlib import Path
import numpy as np
from scipy import optimize,special
import scipy,yaml

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):
    s=p.read_text(encoding='utf-8');return json.loads(s) if s.lstrip().startswith(('{','[')) else yaml.safe_load(s)
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,header,data):
    with p.open('w',encoding='utf-8',newline='') as f:w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(data)
def artifacts(project,run_id):return [((project/e['manifest']).parent,e) for e in load(project/'index/evidence-summary.json')['entries'] if e['run_id']==run_id]
def one(project,r):
    found=[v for v in artifacts(project,r['run_id']) if v[1]['operation_type']=='risk_score_evaluation'];assert len(found)==1;return found[0]

def generate(root,name,source=None):
    project=root/name;project.mkdir();binary=name.startswith('binary');data=[]
    for i in range(144):
        x0=math.sin(i*.53);x1=math.cos(i*.71);age=(i*7%31)/31.;outer=1+i//2%3;inner=1+i//6%3
        y=float(i*17%29<13+3*x0) if binary else 2+.8*x0-.4*x1+.3*age+.1*(i%2)+.15*math.cos(i*.31)
        role='training' if i<64 else 'tuning' if i<96 else 'evaluation';sex='A' if i%2==0 else 'B'
        if name.endswith('evaluation-change') and role=='evaluation':y=1-y if binary else y+12;x0=10+3*x0
        if name.endswith('group-crossing') and i==0:outer=2
        if name.endswith('unseen-category') and i<2:sex='C'
        if name.endswith('single-class-fold') and i<96:y=float(outer==1)
        if name.endswith('subnormal-loss'):y=5e-324 if i%7==0 else 0.
        data.append([f'PRIVATE_PERSON_{i}',role,f'PRIVATE_FAMILY_{i//2}',y,x0,'NA' if i%19==0 else x1,age,sex,outer,inner])
    table(project/'participants.tsv',['sample_id','partition','family','y','x0','x1','age','sex','outer','inner'],data)
    p={'file':'participants.tsv','family':'binary' if binary else 'quantitative','outcome':'y','features':['x0','x1'],'covariates':['age','sex'],'categorical_covariates':['sex'],
       'group_columns':['family'],'resampling_group':'family','lambdas':[.2,.02],'alpha':0.,'bootstraps':0,'missing':'training_mean','minimum_samples':4,
       'nested_validation':{'outer_folds':3,'inner_folds':3,'outer_fold_column':'outer','inner_fold_column':'inner'},'preprocessing_provenance':'Generated raw independent predictors; no outcome-based upstream selection'}
    if name.endswith('fit-limit'):p['nested_validation']['maximum_fits']=1
    if name.endswith('audit-limit'):p['nested_validation']['maximum_fold_rows']=1
    if name.endswith('work-limit'):p['max_work']=10000
    if name.endswith('reuse'):
        out,_=source;target=project/'source-model';target.mkdir();model=load(out/'score-model.yaml');shutil.copyfile(out/'score-model.yaml',target/'score-model.yaml');shutil.copyfile(out/model['training_ids'],target/model['training_ids'])
        p.pop('nested_validation');p.update(mode='model',model='source-model/score-model.yaml',external_independence='Generated external evaluation was absent from every source development fold; source selection metadata is retained, not rerun.')
    study={'schema_version':1,'project':{'id':name,'title':'Synthetic nested '+name,'description':'Numerical/workflow fixtures, no real study or scientific calibration.'},'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[],
           'hypotheses':[{'id':'H','description':'Explicit grouped nested development and separate evaluation','outcome':'y','analysis':{'mode':'manual','goals':['risk_score_evaluation','finding_inventory'],'risk_score':p}}]}
    write(project/'study.yaml',study);return project

def execute(root,jar):
    root.mkdir(parents=True);software=root/'software';software.mkdir();pinned=software/'jlinalg.jar';shutil.copyfile(jar,pinned);shutil.copyfile(__file__,software/'verifier.py')
    write(root/'execution-software.json',{'jar_sha256':sha(pinned),'verifier_sha256':sha(Path(__file__)),'numpy':np.__version__,'scipy':scipy.__version__,'python':sys.version});receipts=[]
    def run(project,expected,replay=False):
        names=[project/'study.yaml',project/'participants.tsv']+list((project/'source-model').glob('*'));inputs={p.relative_to(project).as_posix():sha(p) for p in names if p.is_file()}
        command=['java','-jar',str(pinned),'study',*(['run'] if replay else ['auto',str(project/'study.yaml')]),'--project',str(project),'--offline'];r=subprocess.run(command,capture_output=True,text=True,encoding='utf-8');label='replay' if replay else 'initial'
        (project/(label+'-stdout.yaml')).write_text(r.stdout,encoding='utf-8');(project/(label+'-stderr.txt')).write_text(r.stderr,encoding='utf-8');result=yaml.safe_load(r.stdout);receipt={'project':project.name,'label':label,'state':result.get('state'),'run_id':result.get('run_id'),'exit_code':r.returncode,'command':command,'inputs_sha256':inputs};receipts.append(receipt);write(root/'execution-receipts.json',receipts)
        assert r.returncode in (0,3) and receipt['state']==expected,(receipt,r.stdout,r.stderr);assert all(sha(project/name)==digest for name,digest in inputs.items());return receipt
    sources={}
    for family in ['quantitative','binary']:
        p=generate(root,family+'-native');r=run(p,'completed');sources[family]=one(p,r)
        if family=='quantitative':run(p,'completed',True)
        run(generate(root,family+'-evaluation-change'),'completed');run(generate(root,family+'-reuse',sources[family]),'completed')
    for name in ['quantitative-group-crossing','quantitative-unseen-category','binary-single-class-fold','quantitative-fit-limit','quantitative-audit-limit','binary-work-limit','quantitative-subnormal-loss']:run(generate(root,name),'waiting_for_input')
    verify(root,pinned)

def fit(data,indices,penalty,binary):
    records=[data[i] for i in indices];y=np.array([float(r['y']) for r in records]);x=np.array([[float(r[v]) if r[v]!='NA' else np.nan for v in ['x0','x1']] for r in records]);means=np.nanmean(x,axis=0);x=np.where(np.isfinite(x),x,means)
    c=np.array([[1.,float(r['age']),float(r['sex']=='B')] for r in records]);scales=np.sqrt(np.mean((x-means)**2,axis=0));scales[scales==0]=1;z=(x-means)/scales
    if not binary:
        base=np.linalg.lstsq(c,y,rcond=None)[0];residual=y-c@base;w=np.linalg.solve(z.T@z/len(y)+penalty*np.eye(2),z.T@(residual-residual.mean())/len(y));raw=w/scales
        return {'means':means,'weights':raw,'intercept':residual.mean()-means@raw,'base':base,'cov':base}
    cm=np.mean(c[:,1:],axis=0);cs=np.sqrt(np.mean((c[:,1:]-cm)**2,axis=0));cs[cs==0]=1;bc=np.column_stack((np.ones(len(y)),(c[:,1:]-cm)/cs));design=np.column_stack((bc,z))
    def solve(a,penalties):
        def objective(b):eta=a@b;return np.mean(np.logaddexp(0,eta)-y*eta)+.5*np.dot(penalties,b*b)
        def gradient(b):return a.T@(special.expit(a@b)-y)/len(y)+penalties*b
        result=optimize.minimize(objective,np.r_[special.logit(y.mean()),np.zeros(a.shape[1]-1)],jac=gradient,method='BFGS',options={'gtol':1e-11,'maxiter':20000});assert np.max(abs(gradient(result.x)))<1e-7,(result.message,gradient(result.x));return result.x
    full=solve(design,np.r_[0.,1.,1.,penalty,penalty]);base=solve(bc,np.r_[0.,1.,1.]);raw=full[3:]/scales;cov=full[:3].copy();cov[1:]/=cs;cov[0]-=cm@cov[1:];base[1:]/=cs;base[0]-=cm@base[1:]
    return {'means':means,'weights':raw,'intercept':-means@raw,'base':base,'cov':cov}

def apply(model,data,indices,binary):
    records=[data[i] for i in indices];x=np.array([[float(r[v]) if r[v]!='NA' else np.nan for v in ['x0','x1']] for r in records]);x=np.where(np.isfinite(x),x,model['means']);c=np.array([[1.,float(r['age']),float(r['sex']=='B')] for r in records]);score=model['intercept']+x@model['weights'];base=c@model['base'];eta=c@model['cov']+score
    return score,special.expit(eta) if binary else eta,special.expit(base) if binary else base,eta
def loss(model,data,indices,binary):
    _,pred,_,eta=apply(model,data,indices,binary);y=np.array([float(data[i]['y']) for i in indices]);return float(np.mean(np.logaddexp(0,eta)-y*eta) if binary else np.mean((y-pred)**2))

def numerical(project,out):
    data=rows(project/'participants.tsv');ids={r['sample_id']:i for i,r in enumerate(data)};binary=project.name.startswith('binary');members=rows(out/'nested-fold-membership.tsv');reported=rows(out/'nested-tuning.tsv');checks=0
    def near(a,b,label):
        nonlocal checks
        assert np.allclose(a,b,rtol=1e-5,atol=3e-7),(project.name,label,a,b);checks+=np.size(a)
    def assignment(scope,outer):
        found={ids[r['sample_id']]:int(r['fold']) for r in members if r['scope']==scope and int(r['outer_fold'])==outer};groups={}
        for i,f in found.items():assert data[i]['partition']!='evaluation';old=groups.setdefault(data[i]['family'],f);assert old==f
        return found
    outer=assignment('outer',-1);assert len(outer)==96;expected_predictions={};selected_final=None;final_model=None
    for scope,fold in [('outer',i) for i in range(1,4)]+[('final',0)]:
        assigned=assignment(scope+'_inner',fold);pool=[i for i in range(96) if scope=='final' or outer[i]!=fold];assert set(assigned)==set(pool);errors=[]
        for penalty in [.2,.02]:
            total=0
            for inner in range(1,4):
                train=[i for i in pool if assigned[i]!=inner];test=[i for i in pool if assigned[i]==inner];assert set(data[i]['family'] for i in train).isdisjoint(data[i]['family'] for i in test);model=fit(data,train,penalty,binary);expected=loss(model,data,test,binary);total+=expected*len(test)/len(pool)
                r=next(r for r in reported if r['scope']==scope and int(r['outer_fold'])==fold and r['inner_fold']==str(inner) and float(r['lambda'])==penalty);near(float(r['loss']),expected,'inner loss')
            r=next(r for r in reported if r['scope']==scope and int(r['outer_fold'])==fold and r['inner_fold']=='pooled' and float(r['lambda'])==penalty);near(float(r['loss']),total,'pooled loss');errors.append(total)
        selected=[.2,.02][int(np.argmin(errors))];model=fit(data,pool,selected,binary)
        saved=load(out/(f'nested-models/outer-{fold}.yaml' if scope=='outer' else 'score-model.yaml'));assert float(saved['lambda'])==selected
        for field,key in [('feature_means','means'),('feature_weights','weights'),('score_intercept','intercept'),('baseline_weights','base')]:near(np.array(saved[field]),model[key],field)
        if binary:near(np.array(saved['covariate_weights']),model['cov'],'covariate weights')
        if scope=='outer':
            test=[i for i in outer if outer[i]==fold];score,pred,baseline,eta=apply(model,data,test,binary)
            for j,i in enumerate(test):expected_predictions[i]=(score[j],pred[j],baseline[j],eta[j])
        else:selected_final=selected;final_model=model
    for r in rows(out/'nested-outer-predictions.tsv'):
        i=ids[r['sample_id']];expected=expected_predictions[i]
        for k,field in enumerate(['score','prediction','baseline']+(['linear_predictor'] if binary else [])):near(float(r[field]),expected[k],field)
    report=load(out/'nested-validation.yaml');assert report['evaluation_used_for_selection'] is False and report['performed_fit_calls']==28;near(float(report['selected_final_lambda']),selected_final,'final penalty')
    actual=rows(out/'participant-scores.tsv');score,pred,baseline,_=apply(final_model,data,list(range(144)),binary)
    for r in actual:
        i=ids[r['sample_id']]
        for field,expected in [('score',score[i]),('prediction',pred[i]),('baseline',baseline[i])]:near(float(r[field]),expected,field)
    yy=np.array([float(r['y']) for r in data[96:]]);metric='brier' if binary else 'rmse';expected=float(np.mean((yy-pred[96:])**2));expected=expected if binary else math.sqrt(expected);r=next(r for r in rows(out/'risk-score-evaluation.tsv') if r['metric']==metric and r['subgroup_column']=='all');near(float(r['estimate']),expected,'external metric')
    return int(checks)

def verify(root,jar):
    pins=load(root/'execution-software.json');assert sha(jar)==pins['jar_sha256'] and sha(Path(__file__))==pins['verifier_sha256'];receipts=load(root/'execution-receipts.json');assert len(receipts)==14;checks=0
    for r in receipts:
        project=root/r['project'];assert all(sha(project/k)==v for k,v in r['inputs_sha256'].items());out,entry=one(project,r)
        for directory,e in artifacts(project,r['run_id']):
            for name,digest in e.get('output_sha256',{}).items():assert sha(directory/name)==digest
        if r['state']=='waiting_for_input':assert entry['state']=='waiting_for_input' and not (out/'score-model.yaml').exists() and not (out/'nested-evaluation.tsv').exists() and not (out/'risk-score-evaluation.tsv').exists();continue
        if r['project'].endswith('native') and r['label']=='initial':checks+=numerical(project,out)
        if r['label']=='replay':assert load(out/'manifest.yaml')['reused_from_cache'] is True
        if r['project'].endswith('evaluation-change'):
            family=r['project'].split('-')[0];source=next(z for z in receipts if z['project']==family+'-native' and z['label']=='initial');original,_=one(root/source['project'],source)
            for name in ['score-model.yaml','nested-validation.yaml','nested-fold-membership.tsv','nested-tuning.tsv','nested-outer-predictions.tsv']:assert sha(out/name)==sha(original/name),name
        if r['project'].endswith('reuse'):
            family=r['project'].split('-')[0];source=next(z for z in receipts if z['project']==family+'-native' and z['label']=='initial');original,_=one(root/source['project'],source);assert sha(out/'participant-scores.tsv')==sha(original/'participant-scores.tsv');assert not (out/'nested-validation.yaml').exists();assert entry['details']['model_refitted'] is False
        for directory,e in artifacts(project,r['run_id']):
            if e['operation_type']=='finding_inventory':
                text=(directory/'finding-inventory.jsonl').read_text();assert 'nested_validation_context' in text if r['project'].endswith(('native','evaluation-change')) else True
                for forbidden in ['PRIVATE_PERSON_','PRIVATE_FAMILY_','feature_means','feature_weights','nested-fold-membership.tsv','nested-outer-predictions.tsv']:assert forbidden not in text,forbidden
    result={'status':'passed','runs':len(receipts),'completed':sum(r['state']=='completed' for r in receipts),'intentional_holds':sum(r['state']=='waiting_for_input' for r in receipts),'independent_numeric_checks':checks,'evaluation_isolation_checks':10,'frozen_model_reuse_cases':2,'jar_sha256':sha(jar),'external_requests':0,'LLM_requests':0,'real_data_or_scientific_generalization_claimed':False};write(root/'verification.json',result);print(json.dumps(result))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['run','verify']);p.add_argument('directory',type=Path);p.add_argument('--jar',type=Path,required=True);a=p.parse_args();(execute if a.action=='run' else verify)(a.directory.resolve(),a.jar.resolve())
