"""Offline packaged Cox score/standalone acceptance with independent risk sums.

run NEW_DIRECTORY --jar EXECUTABLE_JAR; verify DIRECTORY --jar EXECUTABLE_JAR
Requires sibling survival-workflow.py and NumPy/SciPy/PyYAML. Generated software
fixtures only: no event-tail, scientific-assumption or biological validation.
The reference refits the nuisance null and explicitly profiles U and I; it does
not compare a one-step approximation with an alternative-model MLE.
"""
import argparse,csv,importlib.util,json,math,subprocess
from pathlib import Path
import numpy as np
from scipy.stats import norm

base_path=Path(__file__).with_name('survival-workflow.py')
spec=importlib.util.spec_from_file_location('cox_score_base',base_path)
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
sha=base.sha;dump=base.dump;load=base.load;table=base.table


def write_table(path,header,rows):
    with path.open('w',encoding='utf-8',newline='') as stream:
        w=csv.writer(stream,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(rows)


def fixture(project,adjust=True,unit=1.,genotype=False,monotone=False):
    project.mkdir();n=96;i=np.arange(n);start=i%3*.05 if adjust else np.zeros(n)
    stop=1+(i*17%23)/3.;event=i%4!=0;age=np.sin(i*.3);sex=i%2
    offset=.15*np.cos(i*.21) if adjust else np.zeros(n)
    g=np.array([i%3,(i*7%5)%3],dtype=float) if genotype else np.array([np.cos(i*.53),np.sin(i*.71)])
    if monotone:g[0]=-stop
    if not genotype:g[1,7]=np.nan
    write_table(project/'phenotype.tsv',['id','entry','time','event','age','sex','o'],
                [[f'S{j}',start[j],stop[j],int(event[j]),age[j]*unit,'B' if sex[j] else 'A',offset[j]] for j in range(n)])
    order=list(reversed(range(n)))
    if genotype:
        rows=['##fileformat=VCFv4.2','##contig=<ID=1>','##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">',
              '\t'.join(['#CHROM','POS','ID','REF','ALT','QUAL','FILTER','INFO','FORMAT']+[f'S{j}' for j in order])]
        for k,values in enumerate([*g,np.where(i<5,1,0)]):
            rows.append('\t'.join(['1',str(100*(k+1)),f'g{k}','A','G','.','PASS','.','GT']+[('0/0','0/1','1/1')[int(values[j])] for j in order]))
        (project/'matrix.vcf').write_text('\n'.join(rows)+'\n',encoding='utf-8')
    else:
        write_table(project/'matrix.tsv',['feature_id']+[f'S{j}' for j in order],
                    [[f'g{k}']+['NA' if np.isnan(values[j]) else values[j]*unit for j in order] for k,values in enumerate(g)])


def command(project,jar,ties='efron',adjust=True,genotype=False,coefficients=False):
    rhs='age+sex+offset(o)' if adjust else '1'
    lhs='Surv(entry,time,event)' if adjust else 'Surv(time,event)'
    args=['java','-jar',str(jar),'--pheno',str(project/'phenotype.tsv'),'--id','id',
          '--model','cox','--formula',lhs+'~'+rhs+('' if coefficients else '+<omics>'),
          '--ties',ties,'--threads','1','--out',str(project/'result.tsv')]
    if not coefficients:args+=['--omics',str(project/('matrix.vcf' if genotype else 'matrix.tsv'))]
    if not coefficients and not genotype:args+=['--omics-type','expression']
    if genotype:args+=['--conditional-gwas-summary','--score-genome-build','GRCh38','--score-block-size','10']
    return args


def execute(root,jar):
    root.mkdir(parents=True)
    dump(root/'software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'base_verifier_sha256':sha(base_path)})
    cases=[('unadjusted',False,1.,False,False,False,'efron',True),
           ('adjusted',True,1.,False,False,False,'efron',True),
           ('breslow',True,1.,False,False,False,'breslow',True),
           ('tiny-units',True,1e-100,False,False,False,'efron',True),
           ('negative-large-units',True,-1e100,False,False,False,'efron',True),
           ('monotone-alternative-score',False,1.,False,True,False,'efron',True),
           ('genotype-export',True,1.,True,False,False,'efron',True),
           ('genotype-unadjusted',False,1.,True,False,False,'breslow',True),
           ('coefficients',True,1.,False,False,True,'efron',True),
           ('infinite-coefficient',True,1.,False,False,True,'efron',False),
           ('infinite-null',True,1.,False,False,False,'efron',False),
           ('invalid-event',True,1.,False,False,False,'efron',False),
           ('no-censoring',True,1.,False,False,True,'efron',True)]
    receipts=[]
    for name,adjust,unit,genotype,monotone,coefficients,ties,success in cases:
        project=root/name;fixture(project,adjust,unit,genotype,monotone)
        if name in ['infinite-coefficient','infinite-null','invalid-event','no-censoring']:
            rows=table(project/'phenotype.tsv')
            for row in rows:
                if name.startswith('infinite'):row['age']=str(-float(row['time']))
                elif name=='invalid-event':row['event']='2' if row['event']=='1' else '0'
                else:row['event']='1'
            write_table(project/'phenotype.tsv',list(rows[0]),[list(r.values()) for r in rows])
        inputs={p.name:sha(p) for p in project.iterdir()};args=command(project,jar,ties,adjust,genotype,coefficients)
        r=subprocess.run(args,capture_output=True,text=True,encoding='utf-8')
        (project/'stdout.txt').write_text(r.stdout,encoding='utf-8');(project/'stderr.txt').write_text(r.stderr,encoding='utf-8')
        receipt={'project':name,'adjusted':adjust,'unit':unit,'genotype':genotype,'coefficients':coefficients,'ties':ties,'success_expected':success,
                 'exit_code':r.returncode,'command':args,'inputs_sha256':inputs}
        receipts.append(receipt);dump(root/'execution-receipts.json',receipts)
        assert (r.returncode==0)==success,(receipt,r.stderr)
        assert all(sha(project/k)==v for k,v in inputs.items())
    verify(root,jar)


def verify(root,jar):
    pins=load(root/'software.json');assert sha(jar)==pins['jar_sha256'] and sha(Path(__file__))==pins['verifier_sha256'] and sha(base_path)==pins['base_verifier_sha256']
    receipts=load(root/'execution-receipts.json');assert len(receipts)==13;checks=0
    for r in receipts:
        p=root/r['project'];assert all(sha(p/k)==v for k,v in r['inputs_sha256'].items())
        if not r['success_expected']:
            assert r['exit_code']!=0 and not (p/'result.tsv').exists() and not (p/'result.tsv.manifest.json').exists()
            error=(p/'stderr.txt').read_text();assert ('MONOTONE_LIKELIHOOD' in error) if r['project'].startswith('infinite') else ('0/1' in error or '0 (censored) or 1' in error)
            continue
        assert r['exit_code']==0
        phen=table(p/'phenotype.tsv');n=len(phen);unit=float(r['unit']);adjust=r['adjusted']
        start=np.array([float(v['entry']) for v in phen]);stop=np.array([float(v['time']) for v in phen]);event=np.array([v['event']=='1' for v in phen]);strata=np.zeros(n)
        x=np.array([[float(v['age'])/unit,1. if v['sex']=='B' else 0.] for v in phen]) if adjust else np.zeros((n,0))
        offset=np.array([float(v['o']) for v in phen]);beta,(_,u0,i0)=base.independent_fit(x,start,stop,event,strata,offset,r['ties'])
        result=table(p/'result.tsv');manifest=load(p/'result.tsv.manifest.json')
        assert manifest['cox_finite_maximum_status']=='FINITE_MAXIMUM'
        assert manifest['cox_event_coding']=='0=censored; 1=event; other codes rejected'
        assert manifest['cox_likelihood_certificate_method']=='adaptive_support_exact_full_risk_direction'
        if r['coefficients']:
            assert len(result)==2 and all(v['term']!='(Intercept)' for v in result)
            assert manifest['cox_effect_estimator']=='fitted_full_model_coefficient' and manifest['cox_finite_maximum_scope']=='full_independent_coefficient_model'
            inv=np.linalg.inv(i0)
            for j,row in enumerate(result):
                factor=unit if j==0 else 1.
                for key,expected in [('beta',beta[j]),('standard_error',math.sqrt(inv[j,j]))]:
                    actual=float(row[key])*(factor if key=='beta' else abs(factor));assert math.isclose(actual,expected,rel_tol=3e-6,abs_tol=2e-8),(r['project'],key,actual,expected);checks+=1
            continue
        assert 'one-step' in (p/'stdout.txt').read_text() and manifest['cox_effect_estimator']=='one_step_Ueff_over_Ieff_at_fitted_null'
        assert manifest['cox_finite_maximum_scope']=='nuisance_null_only; alternative_MPLE_not_required'
        q=x.shape[1]
        if r['genotype']:
            i=np.arange(n);variants=np.array([i%3,(i*7%5)%3],dtype=float).T
            assert len(result)==3 and result[2]['status']=='filtered' and float(result[2]['minor_allele_count'])==5
            assert result[2]['score_u']=='';assert manifest['minimum_mac']=='20.0'
        else:
            matrix=table(p/'matrix.tsv');values={row['feature_id']:{k:float(v)/unit if v!='NA' else np.nan for k,v in row.items() if k!='feature_id'} for row in matrix}
            variants=np.array([[values[f'g{j}'][row['id']] for j in range(2)] for row in phen]);variants=np.where(np.isfinite(variants),variants,np.nanmean(variants,axis=0))
            assert len(result)==2
        full=np.column_stack((x,variants));_,scores,info=base.likelihood(np.r_[beta,np.zeros(2)],full,start,stop,event,strata,offset,r['ties'])
        inv=np.linalg.inv(i0) if q else np.zeros((0,0));u=scores[q:]-info[q:,:q]@inv@scores[:q];v=info[q:,q:]-info[q:,:q]@inv@info[:q,q:]
        for j,row in enumerate(result[:2]):
            assert row['status']=='ok';b=u[j]/v[j,j];se=1/math.sqrt(v[j,j]);z=b/se
            for key,expected in [('beta',b),('standard_error',se),('p_value',2*norm.sf(abs(z)))]:
                actual=float(row[key])*(unit if key=='beta' and not r['genotype'] else abs(unit) if key=='standard_error' and not r['genotype'] else 1.)
                assert math.isclose(actual,expected,rel_tol=4e-6,abs_tol=3e-8),(r['project'],key,actual,expected);checks+=1
            if r['genotype']:
                for key,expected in [('score_u',u[j]),('score_variance',v[j,j]),('beta_score',b),('se_score',se)]:
                    assert math.isclose(float(row[key]),expected,rel_tol=4e-6,abs_tol=3e-8),(key,row);checks+=1
        if r['genotype']:
            cov=table(p/'result.tsv.score-cov.tsv');assert len(cov)==3
            for row,(j,k) in zip(cov,[(0,0),(0,1),(1,1)]):assert math.isclose(float(row['score_covariance']),v[j,k],rel_tol=4e-6,abs_tol=3e-8);checks+=1
            assert load(p/'result.tsv.score-manifest.json')['null_converged']=='true'
    report={'status':'passed','cases':len(receipts),'successful_commands':sum(r['success_expected'] for r in receipts),'intentional_rejections':sum(not r['success_expected'] for r in receipts),
            'independent_numeric_checks':checks,'jar_sha256':sha(jar),'external_requests':0,'LLM_requests':0,'real_data_or_tail_calibration_claimed':False}
    dump(root/'verification.json',report);print(json.dumps(report))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True)
    a=parser.parse_args();(execute if a.action=='run' else verify)(a.directory.resolve(),a.jar.resolve())
