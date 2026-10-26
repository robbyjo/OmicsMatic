"""Offline conditional frozen-edge label tests, supplied reuse, and missing work.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR.
Requires NumPy, SciPy, PyYAML and the adjacent pinned preservation fixture.
Synthetic fixed graph assumptions only; no biological or real-AI validation.
"""
import argparse,csv,hashlib,importlib.util,itertools,json,math,shutil,subprocess
from collections import Counter
from pathlib import Path
import yaml
from scipy.stats import beta

CASES=('signed','unsigned','native','mixed','stratified','sampled','bh','bonferroni','nominal','work-bound','exact-bound','missing-rationale','unknown-source','missing-strata-resume','strata-revision','provided-null','provided-descriptive','invalid-source')
HELD={'work-bound','exact-bound','missing-rationale','unknown-source'}
SECRET='PRIVATE_NETWORK_NULL_SCIENTIFIC_REVIEW'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def load(path):return yaml.safe_load(path.read_text(encoding='utf-8'))
def table(path):
    with path.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def save(path,columns,rows):
    with path.open('w',encoding='utf-8',newline='') as stream:
        out=csv.writer(stream,delimiter='\t',lineterminator='\n');out.writerow(columns);out.writerows(rows)
def helper(path):
    spec=importlib.util.spec_from_file_location('network_preservation_fixture',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
def cli(jar,project,*args,invalid=False):
    result=subprocess.run(['java','-jar',str(jar),'study',*map(str,args),'--project',str(project)],capture_output=True,text=True,encoding='utf-8')
    with (project/'commands.jsonl').open('a',encoding='utf-8') as stream:stream.write(json.dumps({'args':list(map(str,args)),'exit_code':result.returncode,'stdout':result.stdout,'stderr':result.stderr})+'\n')
    assert result.returncode==(2 if invalid else 0),(args,result.stdout,result.stderr)
    return result.stdout
def operations(project):
    status=load(project/'state/project-status.yaml');return [(p.parent,load(p)) for p in (project/'runs'/status['run_id']/'operations').glob('*/manifest.yaml')]
def null_stage(project):return next((p,m) for p,m in operations(project) if m['operation_type']=='network_preservation_null')
def state(project):
    p,m=null_stage(project);return {'run_id':m['run_id'],'state':m['state'],'reused':m.get('reused_from_cache',False),'tests':str((p/'network-preservation-tests.tsv').relative_to(project)),
                                  'graph_and_module_reuse':{m['operation_id']:m.get('reused_from_cache',False) for _,m in operations(project) if m['operation_type'] in ('network_analysis','network_modules')}}
def old_attempts(project):return {str(p.relative_to(project)):sha(p) for p in (project/'runs').rglob('*') if p.is_file()}
def unchanged(project,prior):
    for name,digest in prior.items():assert sha(project/name)==digest,name

def generate(root,case,base):
    project=root/case
    base.generate(project,case if case in ('native','mixed') else 'unknown-coverage' if case=='unknown-source' else 'supplied')
    study=load(project/'study.json');analysis=study['hypotheses'][0]['analysis'];analysis['goals']=['network_preservation_null','impact_assessment']
    policy={'exchangeability_rationale':SECRET+' Synthetic conditional label group; no biological inference.','exact_maximum':100000,'adjustment':'BY'}
    if case=='unsigned':policy['statistic']='edge_overlap'
    if case in ('bh','bonferroni','nominal'):policy['adjustment']={'bh':'BH','bonferroni':'bonferroni','nominal':'NONE'}[case]
    if case=='sampled':policy.update(method='monte_carlo',draws=9999)
    if case=='work-bound':policy['max_work']=1
    if case=='exact-bound':policy.update(method='exact',exact_maximum=119)
    if case=='missing-rationale':policy.pop('exchangeability_rationale')
    if case in ('stratified','missing-strata-resume','strata-revision'):
        policy['strata_file']='strata.tsv'
        values=[(node,'core' if node in 'abc' else 'outside') for node in 'abcde']
        if case=='strata-revision':values=[(node,'all') for node in 'abcde']
        save(project/('deferred-strata.tsv' if case=='missing-strata-resume' else 'strata.tsv'),['node_id','stratum'],values)
    if case in ('provided-null','invalid-source'):
        source,_=null_stage(root/'signed');shutil.copyfile(source/'network-preservation-tests.tsv',project/'provided-tests.tsv')
        if case=='invalid-source':
            rows=table(project/'provided-tests.tsv');next(r for r in rows if r['disposition']=='tested')['p_value']='0';save(project/'provided-tests.tsv',list(rows[0]),[list(r.values()) for r in rows])
        analysis['supplied_results']=[d for d in analysis['supplied_results'] if d['kind']=='meta_analysis']
        analysis['supplied_results'].append({'id':'provided-null','kind':'network_preservation_null','files':{'results':'provided-tests.tsv'},'scope':{'species':'human','phenotype':'synthetic'},
                                             'method':{'name':'Previously completed conditional graph-label test'},'family':{'id':'source-conditional','coverage':'complete','tested_count':2,'adjustment':'BY','alpha':.05},'limitations':[SECRET]})
        study['datasets']=[];study['hypotheses'][0].pop('inputs',None)
    if case=='provided-descriptive':
        source,_=next((p,m) for p,m in operations(root/'signed') if m['operation_type']=='network_preservation');shutil.copyfile(source/'network-preservation.tsv',project/'provided-descriptive.tsv')
        analysis['supplied_results'].append({'id':'provided-description','kind':'network_preservation','files':{'results':'provided-descriptive.tsv'},'scope':{'species':'human','phenotype':'synthetic'},'method':{'name':'Previously completed descriptive module comparisons'}})
    analysis['network_preservation_null']=policy
    write(project/'study.json',study)
    write(project/'original-input-hashes.json',{p.name:sha(p) for p in project.iterdir() if p.is_file() and p.name not in ('original-input-hashes.json','commands.jsonl')})
    return project

def reference(row,graphs,base,strata):
    a,b=graphs[row['reference_graph']],graphs[row['target_graph']];universe=sorted(set(a['nodes'])&set(b['nodes']));members=set(base.reference_modules(a)[row['module_id']]);pattern={(x,y):1 if w>0 else -1 for x,y,w in a['edges'] if x in members and y in members and x in universe and y in universe}
    target={tuple(sorted((x,y))):1 if w>0 else -1 for x,y,w in b['edges'] if x in universe and y in universe}
    groups={}
    for node in universe:groups.setdefault(strata.get(node,'all'),[]).append(node)
    grouped=[groups[k] for k in sorted(groups)];histogram=Counter()
    for labels in itertools.product(*(itertools.permutations(group) for group in grouped)):
        permutation={node:label for group,assignment in zip(grouped,labels) for node,label in zip(group,assignment)}
        statistic=sum(tuple(sorted((permutation[x],permutation[y]))) in target and (row['statistic']=='edge_overlap' or target[tuple(sorted((permutation[x],permutation[y])))]==sign) for (x,y),sign in pattern.items())
        histogram[statistic]+=1
    observed=sum(pair in target and (row['statistic']=='edge_overlap' or sign==target[pair]) for pair,sign in pattern.items());return histogram,observed,len(universe),len(pattern)

def verify(root,jar):
    pins=load(root/'execution-software.json');assert sha(jar)==pins['jar_sha256']==sha(root/'software/jlinalg.jar')
    assert sha(Path(__file__))==pins['verifier_sha256'];assert sha(root/'software/network-preservation-workflow.py')==pins['base_fixture_sha256']
    base=helper(root/'software/network-preservation-workflow.py');checks=0;case_receipts=[];sealed={}
    for case in CASES:
        project=root/case
        for name,digest in load(project/'original-input-hashes.json').items():
            if not (case=='strata-revision' and name=='strata.tsv'):assert sha(project/name)==digest,(case,name)
        folder,manifest=null_stage(project)
        expected='failed' if case=='invalid-source' else 'waiting_for_input' if case in HELD else 'complete'
        assert manifest['state']==expected,(case,manifest)
        assert not any(m['operation_type']=='association' for _,m in operations(project))
        if case in ('provided-null','invalid-source'):assert not any(m['operation_type'] in ('network_analysis','network_modules','network_preservation') for _,m in operations(project))
        for run in (project/'runs').iterdir():
            for receipt in run.glob('operations/*/manifest.yaml'):
                m=load(receipt)
                for name,digest in m['output_sha256'].items():assert sha(receipt.parent/name)==digest;sealed[str((receipt.parent/name).relative_to(root))]=digest
        if case=='invalid-source':assert not (folder/'network-preservation-tests.tsv').exists();case_receipts.append({'case':case,'state':expected});continue
        rows=table(folder/'network-preservation-tests.tsv')
        if case=='provided-null':
            assert (folder/'network-preservation-tests.tsv').read_bytes()==(root/'signed'/state(root/'signed')['tests']).read_bytes()
            assert manifest['details']['analysis_executed_by_jlinalg'] is False and manifest['details']['primary_selection_eligible'] is False
        else:
            graphs=load(project/'expected-graphs.json');strata={r['node_id']:r['stratum'] for r in table(project/'strata.tsv')} if (project/'strata.tsv').exists() else {}
            pvalues={}
            for row in rows:
                if row['disposition']!='tested':assert row['p_value']==row['adjusted_p']=='NA';continue
                histogram,observed,n,edges=reference(row,graphs,base,strata);assert int(row['observed_statistic'])==observed and int(row['universe_nodes'])==n and int(row['reference_edges'])==edges
                if row['permutation_method']=='exact':
                    observed_hist={int(r['null_statistic']):int(r['count']) for r in table(folder/'network-null-statistic-counts.tsv') if all(r[k]==row[k] for k in ('reference_graph','target_graph','module_id','module_type'))}
                    assert observed_hist==histogram,(case,row,histogram,observed_hist)
                    states=sum(histogram.values());extreme=sum(count for value,count in histogram.items() if value>=observed)
                    assert int(row['permutations'])==states and int(row['extreme_count'])==extreme
                    p=extreme/states;assert math.isclose(float(row['p_value']),p,rel_tol=1e-12)
                    assert float(row['tail_lower_95'])==float(row['tail_upper_95'])==float(row['p_value'])
                else:
                    states=int(row['permutations']);extreme=int(row['extreme_count']);p=(extreme+1)/(states+1);assert p==float(row['p_value']) and p>0
                    low=0 if extreme==0 else beta.ppf(.025,extreme,states-extreme+1);high=1 if extreme==states else beta.ppf(.975,extreme+1,states-extreme)
                    assert math.isclose(float(row['tail_lower_95']),low,rel_tol=1e-10,abs_tol=1e-12) and math.isclose(float(row['tail_upper_95']),high,rel_tol=1e-10,abs_tol=1e-12)
                    exact_tail=sum(count for value,count in histogram.items() if value>=observed)/sum(histogram.values());assert abs(p-exact_tail)<.03
                pvalues[(row['reference_graph'],row['target_graph'],row['module_id'],row['module_type'])]=p;checks+=8
            if case in HELD:assert all(row['adjusted_p']=='NA' and row['significant']=='false' for row in rows)
            else:
                denominator=len(rows);method=rows[0]['adjustment'] if rows else 'BY';harmonic=sum(1/i for i in range(1,denominator+1)) if method=='BY' else 1;adjusted={};previous=1
                for rank,(key,p) in reversed(list(enumerate(sorted(pvalues.items(),key=lambda x:x[1]),1))):
                    previous=p if method=='NONE' else min(1,p*denominator) if method=='bonferroni' else min(previous,p*denominator*harmonic/rank);adjusted[key]=previous
                for row in rows:
                    assert int(row['family_size'])==denominator
                    key=tuple(row[k] for k in ('reference_graph','target_graph','module_id','module_type'))
                    if key in adjusted:assert math.isclose(float(row['adjusted_p']),adjusted[key],rel_tol=1e-12,abs_tol=1e-14);checks+=1
        if case in ('missing-strata-resume','strata-revision'):
            history=load(project/'history.json');assert history[1]['state']=='complete' and not history[1]['reused'] and all(history[1]['graph_and_module_reuse'].values());assert history[2]['reused']
            if case=='missing-strata-resume':assert history[0]['state']=='waiting_for_input'
            else:assert history[0]['state']=='complete' and history[0]['p_values']!=history[1]['p_values']
        inventory=next(p for p,m in operations(project) if m['operation_type']=='finding_inventory');dossiers=[json.loads(line) for line in (inventory/'finding-inventory.jsonl').read_text(encoding='utf-8').splitlines()]
        assert {d['feature_id'] for d in dossiers if d['candidate']}=={r['feature_id'] for r in table(project/'meta.tsv') if float(r['adjusted_p'])<.05},case
        if case not in ('invalid-source',):assert any(o.get('kind')=='network_label_null_context' and o['primary'] is False for d in dossiers for o in d['observations'])
        for o in [o for d in dossiers for o in d['observations'] if o.get('kind')=='network_label_null_context']:
            assert 'adjustment_values' in o and 'registered_alpha' in o
            assert ('NONE' in o['adjustment_values'])==(case=='nominal')
            assert 'NONE is nominal without FDR control' in o['required_manuscript_caveat']
        # Unsent aggregate packets and Methods must not include the local rationale or private source free text.
        assert list(project.glob('runs/**/comparisons/**/evidence.yaml')),case
        assert list(project.glob('runs/**/methods-inventory.yaml')),case
        for p in list(project.glob('runs/**/finding-inventory.jsonl'))+list(project.glob('runs/**/comparisons/**/evidence.yaml'))+list(project.glob('runs/**/methods-inventory.yaml')):
            assert SECRET not in p.read_text(encoding='utf-8'),p
            assert base.SECRET not in p.read_text(encoding='utf-8'),p
        cli(jar,project,'verify')
        case_receipts.append({'case':case,'state':expected,'comparisons':len(rows),'tested':sum(r['disposition']=='tested' for r in rows)})
    write(root/'sealed-artifacts.json',sealed)
    report={'passed':True,'cases':case_receipts,'independent_numeric_checks':checks,'sealed_operation_outputs':len(sealed),**pins,'network_requests':0,'llm_requests':0,'scientific_exchangeability_certified':False,'full_pipeline_conformance':False}
    write(root/'verification.json',report);print(json.dumps(report,indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/'jlinalg.jar');jar=software/'jlinalg.jar'
        shutil.copyfile(__file__,software/'network-null-workflow.py');shutil.copyfile(Path(__file__).with_name('network-preservation-workflow.py'),software/'network-preservation-workflow.py')
        write(root/'execution-software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(software/'network-null-workflow.py'),'base_fixture_sha256':sha(software/'network-preservation-workflow.py')})
        base=helper(software/'network-preservation-workflow.py')
        for case in CASES:
            project=generate(root,case,base);cli(jar,project,'auto',project/'study.json','--offline','--actor-type','external','--actor-id','synthetic-null-fixture',invalid=case=='invalid-source')
            if case in ('missing-strata-resume','strata-revision'):
                history=[state(project)];prior=old_attempts(project)
                if case=='missing-strata-resume':shutil.copyfile(project/'deferred-strata.tsv',project/'strata.tsv')
                else:
                    shutil.copyfile(project/'strata.tsv',project/'strata-original.tsv');save(project/'strata.tsv',['node_id','stratum'],[(node,'core' if node in 'abc' else 'outside') for node in 'abcde'])
                if case=='strata-revision':history[0]['p_values']=[r['p_value'] for r in table(project/history[0]['tests'])]
                cli(jar,project,'run','--allow-partial');unchanged(project,prior);history.append(state(project));history[-1]['p_values']=[r['p_value'] for r in table(project/history[-1]['tests'])]
                cli(jar,project,'run','--allow-partial');unchanged(project,prior);history.append(state(project));write(project/'history.json',history)
    verify(root,jar)
