"""Offline finite-Cox-MPLE workflow: exact local proofs, independent risk-cone LPs.

run NEW_DIRECTORY --jar EXECUTABLE_JAR; verify DIRECTORY --jar EXECUTABLE_JAR
Requires sibling survival-workflow.py, Python/PyYAML/NumPy/SciPy. Synthetic
numerical/contract checks only; no patient data, external network or LLM calls.
"""
import argparse,csv,hashlib,importlib.util,json,math,shutil
from fractions import Fraction
from pathlib import Path
import numpy as np
from scipy.optimize import linprog

base_path=Path(__file__).with_name('survival-workflow.py')
spec=importlib.util.spec_from_file_location('cox_workflow_base',base_path);base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
sha=base.sha;load=base.load;dump=base.dump;table=base.table

def rewrite(path,rows):
    with path.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(rows[0]),delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(rows)

def transform(project,mode):
    for cohort in ['a','b','v']:
        phen=table(project/f'{cohort}-phenotype.tsv')
        if mode=='joint':
            for row in phen:row['time']=str(math.ceil(4*float(row['time']))/4);row['entry']='0';row['age']=str(int(row['id'][1:])%3)
        elif mode=='null':
            for row in phen:row['age']=str(-float(row['time']))
        elif mode=='scale':
            for row in phen:row['age']=str(float(row['age'])*1e100)
        rewrite(project/f'{cohort}-phenotype.tsv',phen);by_id={r['id']:r for r in phen};matrix=table(project/f'{cohort}-matrix.tsv')
        for row in matrix:
            for id_ in list(row)[1:]:
                if row['feature_id']=='g1' and mode in ['monotone','joint']:row[id_]=str(-float(by_id[id_]['time']) if mode=='monotone' else -4*float(by_id[id_]['time'])-10*float(by_id[id_]['age']))
                elif mode=='scale' and row['feature_id'] in ['g0','g1'] and row[id_]!='NA':row[id_]=str(float(row[id_])*1e-100)
        rewrite(project/f'{cohort}-matrix.tsv',matrix)

def execute(root,jar):
    root.mkdir(parents=True);dump(root/'software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'base_verifier_sha256':sha(base_path)})
    receipts=[]
    def run(project,expected,label='initial'):
        r=base.run(project,jar,label);receipts.append(r);dump(root/'execution-receipts.json',receipts);assert r['state']==expected,(r,(project/f'{label}-stderr.txt').read_text());return r
    native=root/'native';base.generate(native);first=run(native,'completed');run(native,'completed','replay')
    for name,mode,adjust,controls in [('monotone-lrt','monotone',False,{}),('monotone-wald','monotone',False,{'ties':'breslow','test':'wald'}),('joint-monotone','joint',True,{}),('null-monotone','null',True,{}),('pair-bound','',False,{'maximum_likelihood_pair_visits':1}),('scaled-finite','scale',True,{})]:
        p=root/name;base.generate(p,controls=controls,adjust=adjust)
        if mode:transform(p,mode)
        run(p,'completed' if mode=='scale' else 'waiting_for_input')
    e=next(e for e in base.entries(native,first) if e['operation_type']=='association' and e['details']['dataset']=='a');proofs=[load(p) for p in (base.location(native,e)/'survival-likelihood-certificates').glob('*.yaml')]
    limit=sum(p['work'] for p in proofs if p['role']=='null' or p['feature_id']=='g0');p=root/'shared-work-bound';base.generate(p,controls={'maximum_likelihood_work':limit});run(p,'waiting_for_input')
    supplied=root/'supplied';supplied.mkdir();s=load(native/'study.yaml')
    for d in s['datasets']:
        e=next(e for e in base.entries(native,first) if e['operation_type']=='association' and e['details']['dataset']==d['id']);name=d['id']+'.tsv';shutil.copyfile(base.location(native,e)/f'standard-results/{name}',supplied/name)
        d.update(stage='association_results',files={'results':name},columns={'result_metadata':{k:k for k in ['model','effect_scale','effect_units','test_method','estimator','standard_error_method','statistic_type']}})
    s['hypotheses'][0]['analysis']['association']={'model':'cox'};dump(supplied/'study.yaml',s);run(supplied,'completed')
    p=root/'ai-context';s=base.generate(p);s['hypotheses'][0]['analysis']['goals'].append('impact_assessment');dump(p/'study.yaml',s);run(p,'waiting_for_input')
    verify(root,jar)

def design(project,cohort,feature,dimension):
    phen={r['id']:r for r in table(project/f'{cohort}-phenotype.tsv')};matrix=table(project/f'{cohort}-matrix.tsv');ids=list(matrix[0])[1:];rows=[phen[id_] for id_ in ids]
    if feature is None:x=np.array([[float(r['age'])] for r in rows]) if dimension else np.empty((len(ids),0))
    else:
        values=np.array([float(next(row for row in matrix if row['feature_id']==feature)[id_]) if next(row for row in matrix if row['feature_id']==feature)[id_]!='NA' else np.nan for id_ in ids]);values[np.isnan(values)]=np.nanmean(values)
        x=np.column_stack(([float(r['age']) for r in rows],values)) if dimension==2 else values[:,None]
    pairs=[(i,j) for i,r in enumerate(rows) if r['event']=='1' for j,s in enumerate(rows) if i!=j and r['sex']==s['sex'] and float(s['entry'])<float(r['time'])<=float(s['time']) and np.any(x[i]!=x[j])]
    return x,pairs

def oracle(x,pairs):
    if x.shape[1]==0:return 'FINITE_MAXIMUM'
    assert pairs
    scaled=x/np.max(np.abs(x),axis=0);d=np.array([scaled[i]-scaled[j] for i,j in pairs]);assert np.linalg.matrix_rank(d)==x.shape[1]
    lp=linprog(-d.sum(axis=0),A_ub=-d,b_ub=np.zeros(len(d)),bounds=[(-1,1)]*x.shape[1],method='highs');assert lp.success
    return 'MONOTONE_LIKELIHOOD' if -lp.fun>1e-7 else 'FINITE_MAXIMUM'

def verify(root,jar):
    software=load(root/'software.json');assert sha(jar)==software['jar_sha256'] and sha(Path(__file__))==software['verifier_sha256'] and sha(base_path)==software['base_verifier_sha256']
    receipts=load(root/'execution-receipts.json');assert len(receipts)==11;checks=proof_checks=exact_witness_checks=support_checks=0
    for r in receipts:
        p=root/r['project'];records=base.entries(p,r)
        for e in records:
            if e['operation_type']!='association':continue
            directory=base.location(p,e);sources={key.split(':')[1] for key in e['input_sha256'] if key.startswith('dataset:') and key.endswith(':matrix')};assert len(sources)==1;cohort=e.get('details',{}).get('dataset') or next(iter(sources))
            for path in (directory/'survival-likelihood-certificates').glob('*.yaml'):
                proof=load(path);x,pairs=design(p,cohort,None if proof['role']=='null' else proof['feature_id'],proof['dimension'])
                assert proof['schema_version']==2 and proof['certificate_method']=='adaptive_support_exact_full_risk_direction'
                support=proof['supporting_source_rows'];assert len(set(support))==len(support) and all(0<=i<len(x) for i in support)
                for term in proof['extra_weights']+proof['basis_corrections']:assert term['event_row'] in support and term['risk_row'] in support
                if proof['status']!='UNRESOLVED':assert oracle(x,pairs)==proof['status'],(p.name,proof);proof_checks+=1
                if proof['status']=='FINITE_MAXIMUM' and proof['dimension']:
                    support_pairs=[(i,j) for i,j in pairs if i in support and j in support]
                    assert len(support_pairs)==proof['contrasts']
                    # An independent LP checks the actual supporting contrasts,
                    # as well as the full-model cone checked above.
                    assert oracle(x,support_pairs)=='FINITE_MAXIMUM';support_checks+=1
                if proof['status']=='MONOTONE_LIKELIHOOD':
                    assert proof['full_direction_verified'] is True and proof['full_risk_queries']>0
                    b=[Fraction(0)]*proof['dimension']
                    for c in proof['exact_direction']:b[c['column']]=Fraction(int(c['numerator']),int(c['denominator']))
                    dots=[sum((Fraction(float(x[i,k]))-Fraction(float(x[j,k])))*b[k] for k in range(x.shape[1])) for i,j in pairs]
                    assert all(v>=0 for v in dots) and any(v>0 for v in dots);exact_witness_checks+=len(dots)
            raw=directory/'association.tsv'
            if raw.exists():
                proofs={sha(path):load(path) for path in (directory/'survival-likelihood-certificates').glob('*.yaml')}
                for row in table(raw):
                    if row['likelihood_proof_sha256']!='NA':assert proofs[row['likelihood_proof_sha256']]['status']==row['likelihood_existence']
                    if row['status']=='ok':assert row['likelihood_existence']=='FINITE_MAXIMUM'
            if r['project'].startswith('monotone-') or r['project']=='joint-monotone':
                assert e['details']['primary_selection_eligible'] is False
                row=next(row for row in table(raw) if row['id']=='g1');assert row['likelihood_existence']=='MONOTONE_LIKELIHOOD' and row['p_value']==row['beta']=='NA'
            if r['project']=='null-monotone':assert not raw.exists()
            if r['project']=='shared-work-bound' and cohort=='a':
                rows=table(raw);assert rows[0]['status']=='ok' and rows[1]['likelihood_existence']=='UNRESOLVED';assert e['details']['primary_selection_eligible'] is False
        if r['project']=='native':checks+=base.numerical(p,r)
    p=root/'supplied';assert not any(e['operation_type'] in ['association','participant_qc'] for e in base.entries(p,next(r for r in receipts if r['project']=='supplied')));assert not list(p.glob('runs/**/survival-likelihood-certificates/*.yaml'))
    baseline=next(r for r in receipts if r['project']=='native');scaled=next(r for r in receipts if r['project']=='scaled-finite')
    for e in base.entries(root/'native',baseline):
        if e['operation_type']!='association':continue
        cohort=e['details']['dataset'];other=next(o for o in base.entries(root/'scaled-finite',scaled) if o['operation_type']=='association' and o['details']['dataset']==cohort)
        old={row['feature_id']:row for row in table(base.location(root/'native',e)/f'standard-results/{cohort}.tsv')}
        for row in table(base.location(root/'scaled-finite',other)/f'standard-results/{cohort}.tsv'):
            for key in ['beta','se','p_value']:
                value=float(row[key])*(1e-100 if key in ['beta','se'] else 1);assert math.isclose(value,float(old[row['feature_id']][key]),rel_tol=1e-8,abs_tol=1e-12);checks+=1
        old_ph={row['feature_id']:row for row in table(base.location(root/'native',e)/'survival-diagnostics.tsv')}
        for row in table(base.location(root/'scaled-finite',other)/'survival-diagnostics.tsv'):
            for key in ['ph_statistic','ph_p_value','global_ph_statistic','global_ph_p_value']:
                assert math.isclose(float(row[key]),float(old_ph[row['feature_id']][key]),rel_tol=1e-6,abs_tol=1e-10);checks+=1
    packets=list((root/'ai-context').glob('runs/**/comparisons/**/evidence.yaml'));assert packets
    # Identical native inputs in separate JVMs must retain certificate hashes and standard tables.
    ai=next(r for r in receipts if r['project']=='ai-context');cross_process_checks=0
    for e in base.entries(root/'native',baseline):
        if e['operation_type']!='association':continue
        cohort=e['details']['dataset'];other=next(o for o in base.entries(root/'ai-context',ai) if o['operation_type']=='association' and o['details']['dataset']==cohort)
        left=base.location(root/'native',e);right=base.location(root/'ai-context',other)
        assert sha(left/f'standard-results/{cohort}.tsv')==sha(right/f'standard-results/{cohort}.tsv');cross_process_checks+=1
        for path in (left/'survival-likelihood-certificates').glob('*.yaml'):assert sha(path)==sha(right/'survival-likelihood-certificates'/path.name);cross_process_checks+=1
    for path in packets:
        text=path.read_text();assert 'likelihood_existence' in text
        for forbidden in ['PATIENT_ROW_SENTINEL','exact_direction','basis_corrections','approximate_direction','event_row','risk_row','supporting_source_rows']:assert forbidden not in text,forbidden
    for path in (root/'ai-context').glob('runs/**/comparisons/**/metadata.yaml'):
        m=load(path);assert m['request_sent'] is False and m['request_attempted'] is False
    report={'status':'passed','cases':len(receipts),'states':{s:sum(r['state']==s for r in receipts) for s in sorted({r['state'] for r in receipts})},'independent_numeric_checks':checks,'independent_risk_cone_LP_checks':proof_checks,'independent_support_LP_checks':support_checks,'exact_Fraction_witness_contrasts':exact_witness_checks,'cross_process_certificate_and_table_checks':cross_process_checks,'jar_sha256':sha(jar),'real_data_or_tail_calibration_claimed':False,'external_requests':0,'LLM_requests':0};dump(root/'verification.json',report);print(json.dumps(report))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['run','verify']);p.add_argument('directory',type=Path);p.add_argument('--jar',type=Path,required=True);a=p.parse_args();(execute if a.action=='run' else verify)(a.directory.resolve(),a.jar.resolve())
