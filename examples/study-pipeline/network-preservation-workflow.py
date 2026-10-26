"""Offline frozen-module transfer across native and supplied cohort graphs.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR.
Requires NumPy, SciPy and PyYAML. Synthetic data, no external/AI appraisal.
"""
import argparse,csv,hashlib,json,math,shutil,subprocess
from pathlib import Path
import numpy as np
from scipy.linalg import hadamard
from scipy.stats import norm,rankdata
import yaml

CASES=('supplied','native','mixed','partial-nodes','unknown-coverage','unknown-namespace','different-threshold','different-estimator','different-species','different-build','unweighted','bound','source-bound','one-graph','provided-preservation','provided-sensitivity','rounded-source','missing-target-resume')
HELD={'unknown-coverage','unknown-namespace','different-threshold','different-estimator','different-species','different-build','unweighted','bound','source-bound','one-graph'}
FIELDS=('reference_graph','target_graph','module_id','module_type','reference_nodes','shared_nodes','node_coverage','possible_pairs','reference_edges','target_edges','shared_edges','edge_retention','edge_jaccard','signed_agreement','thresholded_weight_cosine','disposition')
SECRET='PRIVATE_NETWORK_PRESERVATION_SENTINEL'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def table(p):
    with p.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def save(p,columns,rows):
    with p.open('w',encoding='utf-8',newline='') as stream:
        out=csv.writer(stream,delimiter='\t',lineterminator='\n');out.writerow(columns);out.writerows(rows)
def cli(jar,project,*args):
    r=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True)
    with (project/'commands.jsonl').open('a',encoding='utf-8') as stream:stream.write(json.dumps({'args':list(map(str,args)),'exit_code':r.returncode,'stdout':r.stdout,'stderr':r.stderr})+'\n')
    assert r.returncode==0,(args,r.stdout,r.stderr)
    return r.stdout
def definition(identity,kind,files,scope,method):return {'id':identity,'kind':kind,'files':files,'scope':scope,'method':method,'limitations':[SECRET]}
def original_graph(which,partial=False):
    nodes=list('abde' if partial and which=='b' else 'abcde')
    edges={('a','b'):.9,('a','c'):-.8,('b','c'):-.75,('d','e'):.82} if which=='a' else {('a','b'):.85,('a','c'):.9,('d','e'):-.82}
    return nodes,{pair:weight for pair,weight in edges.items() if set(pair)<=set(nodes)}
def vectors(which):
    h=hadamard(64);z,x,y,v,w,u=[h[:,i].astype(float) for i in range(1,7)]
    if which=='a':
        q=-.03/math.sqrt(.19);values=[10*z+x,5*z+.9*x+math.sqrt(.19)*y,-4*z-.8*x+q*y+math.sqrt(.36-q*q)*v,2*z+w,7*z+.82*w+math.sqrt(1-.82**2)*u]
    else:
        q=-.165/math.sqrt(.2775);values=[3*z+x,2*z+.85*x+math.sqrt(.2775)*y,5*z+.9*x+q*y+math.sqrt(.19-q*q)*v,2*z+w,7*z-.82*w+math.sqrt(1-.82**2)*u]
    return np.array(values),z
def generate(project,case):
    project.mkdir();datasets=[];supplied=[];expected={};namespaces={};references=[]
    save(project/'annotation.tsv',['feature_id','gene_symbol'],[(g,g.upper()) for g in 'abcde'])
    p=2*norm.sf(np.array([4.,3.,2.,1.,.1]));q=np.minimum.accumulate((p*5/np.arange(1,6))[::-1])[::-1];save(project/'meta.tsv',['feature_id','beta','se','p_value','adjusted_p'],[(g,b,1,float(pv),float(qv)) for g,b,pv,qv in zip('abcde',[4.,3.,2.,1.,.1],p,q)])
    meta=definition('pool','meta_analysis',{'results':'meta.tsv','feature_annotation':'annotation.tsv'},{'phenotype':'synthetic','species':'human'},{'name':'Synthetic external fixed effect results'});meta['family']={'id':'original-five','coverage':'complete','tested_count':5,'adjustment':'BH','adjusted_p_from_full_family':True};supplied.append(meta)
    if case!='provided-preservation':
        for which in ('a','b'):
            if which=='b' and case=='one-graph':continue
            native=case=='native' or case=='mixed' and which=='b'
            dataset={'id':which,'cohort':which.upper(),'type':'expression','species':'mouse' if case=='different-species' and which=='b' else 'human','genome_build':'GRCh37' if case=='different-build' and which=='b' else 'GRCh38','feature_id_type':'gene_symbol','annotation_release':'synthetic-2026','files':{'annotation':'annotation.tsv'}}
            if native:
                data,z=vectors(which);save(project/(which+'-matrix.tsv'),['feature_id']+[f'{which}{i}' for i in range(64)],[(g,*row) for g,row in zip('abcde',data)]);save(project/(which+'-pheno.tsv'),['id','z'],[(f'{which}{i}',value) for i,value in enumerate(z)])
                dataset['stage']='processed_matrix';dataset['files'].update({'matrix':which+'-matrix.tsv','phenotype':which+'-pheno.tsv'});alias='dataset:'+which
                design=np.column_stack([np.ones(64),z]);residual=data.T-design@np.linalg.lstsq(design,data.T,rcond=None)[0];rho=np.corrcoef(residual,rowvar=False);edges={(a,b):float(rho[i,j]) for i,a in enumerate('abcde') for j,b in enumerate('abcde') if j>i and abs(rho[i,j])>=.7};nodes=list('abcde')
                expected[alias]={'nodes':nodes,'edges':[[a,b,w] for (a,b),w in edges.items()],'native':True}
            else:
                nodes,edges=original_graph(which,case=='partial-nodes');save(project/(which+'-nodes.tsv'),['node_id'],[(g,) for g in nodes]);save(project/(which+'-edges.tsv'),['source','target','weight'],[(a,b,w) for (a,b),w in edges.items()]);save(project/(which+'-modules.tsv'),['node_id','module_id'],[(g,'core' if g in 'abc' else 'outside') for g in nodes]);
                method={'name':'Synthetic supplied cohort graph','edge_direction':'undirected','edge_semantics':'signed_correlation','edge_coverage':'complete_thresholded','annotation_release':'synthetic-2026','correlation':'spearman' if case=='different-estimator' and which=='b' else 'pearson','threshold':.8 if case=='different-threshold' and which=='b' else .7}
                if case=='unknown-coverage' and which=='b':method.pop('edge_coverage')
                if case=='unweighted' and which=='b':save(project/(which+'-edges.tsv'),['source','target'],list(edges))
                scope={k:dataset[k] for k in ('species','genome_build','feature_id_type')};scope.update({'phenotype':'synthetic','cohorts':[which.upper()],'datasets':[which]})
                source=definition('graph-'+which,'network_analysis',{'nodes':which+'-nodes.tsv','edges':which+'-edges.tsv','modules':which+'-modules.tsv','feature_annotation':'annotation.tsv'},scope,method);supplied.append(source);dataset.update({'stage':'analysis_reference','supplied_result_id':'graph-'+which,'files':{}});alias='supplied:graph-'+which
                expected[alias]={'nodes':nodes,'edges':[[a,b,None if case=='unweighted' and which=='b' else w] for (a,b),w in edges.items()],'native':False,'modules':{'supplied:core':[g for g in nodes if g in 'abc'],'supplied:outside':[g for g in nodes if g in 'de']}}
            datasets.append(dataset);namespaces[alias]='synthetic:symbol:2026'
            if which=='a':references=[alias]
    policy={'references':references,'graph_namespaces':{} if case=='unknown-namespace' else namespaces}
    if case=='bound':policy['max_comparisons']=1
    if case=='source-bound':policy['max_nodes']=4
    if case in ('provided-preservation','provided-sensitivity','rounded-source'):
        row=['supplied:graph-a','supplied:graph-b','supplied:core','supplied_module',3,3,1,3,3,2,2,2/3,2/3,.5,(.9*.85-.8*.9)/math.sqrt((.9**2+.8**2+.75**2)*(.85**2+.9**2)),'assessed']
        method={'name':'Synthetic external module transfer'}
        if case=='rounded-source':row[11]=row[12]=.67;method['precision_tolerance']=.005
        save(project/'provided.tsv',FIELDS,[row]);provided=definition('provided','network_preservation',{'results':'provided.tsv'},{'phenotype':'synthetic','species':'human'},method)
        if case=='provided-sensitivity':provided['role']='sensitivity'
        supplied.append(provided)
        if case=='provided-preservation':policy={}
    analysis={'mode':'manual','goals':['network_preservation','impact_assessment'],'network_preservation':policy,'network':{'correlation':'pearson','minimum_samples':8,'preprocessing':'Synthetic orthogonal features; no biological validity claimed.'},'supplied_results':supplied}
    hypothesis={'id':'H','description':'Compare frozen reference modules without selecting a primary graph.','outcome':'synthetic','analysis':analysis}
    if case in ('native','mixed'):hypothesis['covariates']=['z']
    if datasets:hypothesis['inputs']={'discovery':['a'],'replication':['b']} if len(datasets)==2 else {'discovery':['a']}
    study={'schema_version':1,'project':{'id':'network-preservation-'+case,'title':'Synthetic network preservation','description':'Software verification only.'},'resources':{'network':'offline','downloads':'deny','directory':'fixture-resources'},'cohorts':[{'id':'A'},{'id':'B'}],'datasets':datasets,'hypotheses':[hypothesis]}
    write(project/'study.json',study);write(project/'expected-graphs.json',expected)
    if case=='missing-target-resume':(project/'b-nodes.tsv').rename(project/'deferred-b-nodes.tsv')
    write(project/'original-input-hashes.json',{p.name:sha(p) for p in project.iterdir() if p.is_file()})
def operations(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'));return [(p.parent,yaml.safe_load(p.read_text(encoding='utf-8'))) for p in (project/'runs'/status['run_id']/'operations').glob('*/manifest.yaml')]
def history(project):
    ops=operations(project);pres=next(m for _,m in ops if m['operation_type']=='network_preservation');a=next((p,m) for p,m in ops if m['operation_type']=='network_analysis' and m['details']['artifact_id']=='graph-a');module=next(m for _,m in ops if m['operation_type']=='network_modules')
    return {'preservation_state':pres['state'],'preservation_reused':pres.get('reused_from_cache',False),'reference_graph_reused':a[1].get('reused_from_cache',False),'reference_modules_reused':module.get('reused_from_cache',False),'current_graph_manifest_sha256':sha(a[0]/'manifest.yaml'),'module_original_graph_manifest_sha256':module['details']['source_network_sha256'],'source_outputs_match':module['details']['source_network_output_sha256']==a[1]['output_sha256']}
def reference_modules(graph):
    if not graph['native']:return graph['modules']
    nodes=graph['nodes'];adj={n:set() for n in nodes}
    for a,b,_ in graph['edges']:adj[a].add(b);adj[b].add(a)
    remaining=set(nodes);components=[]
    while remaining:
        todo={next(n for n in nodes if n in remaining)};members=set()
        while todo:node=todo.pop();members.add(node);todo|=adj[node]-members
        components.append([n for n in nodes if n in members]);remaining-=members
    # Tiny independent exhaustive maximal-clique enumeration; no production algorithm reuse.
    import itertools
    cliques=[]
    for n in range(3,len(nodes)+1):
        for subset in itertools.combinations(nodes,n):
            s=set(subset)
            if all(b in adj[a] for a,b in itertools.combinations(s,2)) and not any(all(x in adj[a] for a in s) for x in set(nodes)-s):cliques.append(list(subset))
    return {**{f'component-{i+1}':v for i,v in enumerate(components)},**{f'clique-{i+1}':v for i,v in enumerate(cliques)}}
def verify(root,jar):
    pins=json.loads((root/'software.json').read_text(encoding='utf-8'));assert pins=={'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__))};assert sha(root/'software/jlinalg.jar')==sha(jar)
    artifacts={};comparisons=0;rows_checked=0;native_graphs=0;receipts=[]
    for case in CASES:
        project=root/case
        for name,digest in json.loads((project/'original-input-hashes.json').read_text(encoding='utf-8')).items():assert sha(project/name)==digest
        if case=='missing-target-resume':
            for name,digest in json.loads((project/'delivered-input-sha256.json').read_text(encoding='utf-8')).items():assert sha(project/name)==digest
            a,b,c=json.loads((project/'history.json').read_text(encoding='utf-8'));assert a['preservation_state']=='waiting_for_input' and b['preservation_state']=='complete' and not b['preservation_reused'] and c['preservation_reused'];assert b['reference_graph_reused'] and b['reference_modules_reused'] and b['source_outputs_match'] and b['current_graph_manifest_sha256']!=b['module_original_graph_manifest_sha256']
        ops=operations(project);pres=[(p,m) for p,m in ops if m['operation_type']=='network_preservation'];assert len(pres)==1;folder,manifest=pres[0];assert manifest['state']==('waiting_for_input' if case in HELD else 'complete'),(case,manifest)
        for p,m in ops:
            for name,digest in m.get('output_sha256',{}).items():assert sha(p/name)==digest;artifacts[(p/name).relative_to(root).as_posix()]=digest
        assert not any(m['operation_type']=='association' for _,m in ops)
        data=table(folder/'network-preservation.tsv');graphs=json.loads((project/'expected-graphs.json').read_text(encoding='utf-8'))
        for receipt in folder.glob('graph-*-manifest.yaml'):
            if '-modules-' in receipt.name:continue
            prefix=receipt.name.removesuffix('-manifest.yaml');producer=yaml.safe_load(receipt.read_text(encoding='utf-8'))
            for name in ('network-nodes.tsv','network-edges.tsv','network-analysis.yaml','supplied-analysis.yaml'):
                companion=folder/(prefix+'-'+name)
                if companion.exists():assert sha(companion)==producer['output_sha256'][name]
            module_receipt=folder/(prefix+'-modules-manifest.yaml')
            if module_receipt.exists():
                module=yaml.safe_load(module_receipt.read_text(encoding='utf-8'));assert module['details']['source_network_output_sha256']==producer['output_sha256'];assert sha(folder/(prefix+'-modules-network-modules.tsv'))==module['output_sha256']['network-modules.tsv']
        module_ops=[m for _,m in ops if m['operation_type']=='network_modules'];assert len(module_ops)==(0 if case in {'provided-preservation','rounded-source'} else 1),(case,module_ops)
        if case=='provided-preservation':assert not any(m['operation_type'] in {'network_analysis','network_modules'} for _,m in ops);assert manifest['details']['analysis_executed_by_jlinalg'] is False;assert len(data)==1 and data[0]['edge_retention']==str(2/3)
        else:
            for p,m in ops:
                if m['operation_type']!='network_analysis':continue
                external=m['details'].get('execution_origin')=='externally_produced';alias='supplied:'+m['details']['artifact_id'] if external else 'dataset:'+m['details']['dataset_id'];expected=graphs[alias]
                if not external:
                    native_graphs+=1;actual={(r['source'],r['target']):float(r['weight']) for r in table(p/'network-edges.tsv')};wanted={(a,b):w for a,b,w in expected['edges']};assert actual.keys()==wanted.keys()
                    for pair,w in wanted.items():assert math.isclose(actual[pair],w,abs_tol=1e-12);comparisons+=1
            if case in {'bound','source-bound','one-graph'}:assert not data;assert manifest['details']['method_report']['performed'] is False
            for row in data:
                reference,target=graphs[row['reference_graph']],graphs[row['target_graph']];members=reference_modules(reference)[row['module_id']];common=set(members)&set(target['nodes']);n=len(common);assert int(row['reference_nodes'])==len(members) and int(row['shared_nodes'])==n;assert math.isclose(float(row['node_coverage']),n/len(members),abs_tol=1e-14);rows_checked+=1;comparisons+=3
                if case in HELD:assert row['disposition']=='held_source_context' and all(row[k]=='NA' for k in FIELDS[8:15]);continue
                if n<3:assert row['disposition']=='below_minimum_nodes' and all(row[k]=='NA' for k in FIELDS[8:15]);continue
                assert row['disposition']=='assessed';a={(x,y):w for x,y,w in reference['edges'] if {x,y}<=common};b={(x,y):w for x,y,w in target['edges'] if {x,y}<=common};intersection=a.keys()&b.keys();union=a.keys()|b.keys();signed=sum(np.sign(a[e])==np.sign(b[e]) for e in intersection);cos=sum(a.get(e,0)*b.get(e,0) for e in union)/math.sqrt(sum(w*w for w in a.values())*sum(w*w for w in b.values()))
                expected={'possible_pairs':n*(n-1)//2,'reference_edges':len(a),'target_edges':len(b),'shared_edges':len(intersection),'edge_retention':len(intersection)/len(a),'edge_jaccard':len(intersection)/len(union),'signed_agreement':signed/len(intersection),'thresholded_weight_cosine':cos}
                for key,value in expected.items():
                    tolerance=.005 if case=='rounded-source' and key in {'edge_retention','edge_jaccard'} else 1e-12
                    assert math.isclose(float(row[key]),value,abs_tol=tolerance),(case,row,key,value);comparisons+=1
                if case=='rounded-source':assert row['edge_retention']==row['edge_jaccard']=='0.67' and manifest['details']['analysis_executed_by_jlinalg'] is False
        inventory=[p/'finding-inventory.jsonl' for p,m in ops if m['operation_type']=='finding_inventory'];assert len(inventory)==1;text=inventory[0].read_text(encoding='utf-8');assert SECRET not in text
        records=[json.loads(line) for line in text.splitlines()];assert len(records)==(0 if case=='different-species' else 5)
        if case=='different-species':assert any(m['operation_type']=='meta_analysis' and m['state']=='waiting_for_input' and not m['details']['primary_selection_eligible'] for _,m in ops)
        for record in records:
            context=[o for o in record['observations'] if o.get('kind')=='network_preservation_context'];assert context and all(o['primary'] is False for o in context)
            if case=='provided-sensitivity':assert any(o['scientific_role']=='sensitivity' for o in context)
        if case not in HELD:assert manifest.get('reused_from_cache') is True
        cli(jar,project,'verify','--project',project);receipts.append({'case':case,'state':manifest['state'],'comparisons':len(data)})
    write(root/'sealed-artifacts.json',artifacts);result={'passed':True,'cases':len(CASES),'case_receipts':receipts,'module_comparison_rows':rows_checked,'native_graphs_independently_rebuilt':native_graphs,'independent_comparisons':comparisons,'sealed_artifacts':len(artifacts),**pins,'external_requests':0,'real_AI_or_biological_validation':False,'full_pipeline_conformance':False};write(root/'verification.json',result);print(json.dumps(result,indent=2))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=Path);ap.add_argument('--jar',type=Path,required=True);args=ap.parse_args();root,jar=args.directory.resolve(),args.jar.resolve()
    if args.action=='run':
        root.mkdir(parents=True,exist_ok=False);(root/'software').mkdir();shutil.copyfile(jar,root/'software/jlinalg.jar');shutil.copyfile(__file__,root/'software/network-preservation-workflow.py');write(root/'software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__))});(root/'capabilities.yaml').write_text(cli(jar,root,'operations'),encoding='utf-8')
        for case in CASES:
            project=root/case;generate(project,case);cli(jar,project,'validate',project/'study.json');cli(jar,project,'auto',project/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture')
            phases=[]
            if case=='missing-target-resume':
                phases.append(history(project));shutil.copyfile(project/'deferred-b-nodes.tsv',project/'b-nodes.tsv');write(project/'delivered-input-sha256.json',{'b-nodes.tsv':sha(project/'b-nodes.tsv')});cli(jar,project,'auto',project/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture');phases.append(history(project))
            if case not in HELD:cli(jar,project,'auto',project/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture')
            if phases:phases.append(history(project));write(project/'history.json',phases)
    verify(root,jar)
