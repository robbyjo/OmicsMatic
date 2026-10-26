"""Offline supplied-GWAS to LD redundancy to unsent impact-dossier acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
The Python oracle reconstructs reference-window graphs independently; P/FDR and
external meta-analysis inputs remain source-bound. Requires PyYAML. No model calls.
"""
import argparse,csv,hashlib,json,math,pathlib,shutil,subprocess
import yaml
MODES=('complete','thresholded','inclusive-boundary','strict-boundary','short-window','missing-pair','unknown-pair','partial-pairs','allele-conflict','population-conflict','bounds','shared-variant','different-build','missing-resume','changed-reference')
WAIT={'strict-boundary','short-window','missing-pair','unknown-pair','allele-conflict','population-conflict','bounds'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def cli(jar,*args):
    p=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8');assert p.returncode in (0,3),p.stdout+p.stderr
def operations(p):
    s=yaml.safe_load((p/'state/project-status.yaml').read_text(encoding='utf-8'));return [(f.parent,yaml.safe_load(f.read_text(encoding='utf-8'))) for f in sorted((p/'runs'/s['run_id']/'operations').glob('*/manifest.yaml'))]
def one(p,kind,h=None):
    result=[(f,m) for f,m in operations(p) if m['operation_type']==kind and (h is None or m['hypothesis']==h)];assert len(result)==1,(p,kind,h,len(result));return result[0]
def bh(data):
    order=sorted(data,key=lambda r:float(r['p_value']));q=1
    for i in range(len(order)-1,-1,-1):q=min(q,float(order[i]['p_value'])*len(order)/(i+1));order[i]['adjusted_p']=str(q)
def generate(root,mode):
    p=root/mode;p.mkdir();hypotheses=[];refs=[];count=2 if mode in ('shared-variant','different-build') else 1
    for n in range(count):
        h='H'+str(n+1);build='GRCh37' if mode=='different-build' and n==1 else 'GRCh38';prefix='rs' if n==0 else 'alias';positions=[100,250,400,550 if mode=='changed-reference' else 2000,3000,4000];variants=[{'variant_id':prefix+str(i+1),'chromosome':'1','position':str(position),'ref':'A','alt':'G'} for i,position in enumerate(positions)];table(p/(h+'-variants.tsv'),variants)
        pairs=[{'variant_a':prefix+'1','variant_b':prefix+'2','r_squared':'.81'},{'variant_a':prefix+'2','variant_b':prefix+'3','r_squared':'.81'},{'variant_a':prefix+'1','variant_b':prefix+'3','r_squared':'.49'}]
        if mode=='strict-boundary':pairs[0]['r_squared']=pairs[1]['r_squared']='.82'
        if mode=='missing-pair':pairs=pairs[:1]
        if mode=='unknown-pair':pairs[1]['r_squared']='NA'
        if mode=='changed-reference':pairs.append({'variant_a':prefix+'3','variant_b':prefix+'4','r_squared':'.01'})
        table(p/(h+'-pairs.tsv'),pairs)
        if mode=='missing-resume':shutil.move(p/(h+'-pairs.tsv'),p/(h+'-pending-pairs.tsv'))
        data=[]
        for i,v in enumerate(variants):data.append({'feature_id':v['variant_id'],'beta':'.1','se':'.1','p_value':'.9' if i==1 else str((i+1)*1e-6),'adjusted_p':'NA','chromosome':'1','position':v['position'],'reference_allele':'C' if mode=='allele-conflict' and i==0 else 'A','alternate_allele':'T' if mode=='allele-conflict' and i==0 else 'G','genome_build':build,'n':'1000'})
        bh(data);table(p/(h+'-meta.tsv'),data)
        imported={'id':'provided-meta','kind':'meta_analysis','files':{'results':h+'-meta.tsv'},'scope':{'modality':'gwas','genome_build':build,'coordinate_system':'1-based-closed','population':'AFR' if mode=='population-conflict' else 'EUR','species':'human'},'method':{'name':'Externally supplied synthetic meta-analysis','parameters':{'model':'fixed'}},'family':{'id':h+'-all-tests','coverage':'complete','tested_count':len(data),'adjustment':'BH','alpha':.05,'adjusted_p_from_full_family':True},'provenance':{'results_sha256':sha(p/(h+'-meta.tsv'))}}
        hypotheses.append({'id':h,'description':'Synthetic external GWAS source; no biological findings.','outcome':'synthetic-trait-'+h,'analysis':{'mode':'manual','goals':['impact_assessment'],'supplied_results':[imported]}})
        r={'id':'panel'+str(n+1),'genome_build':build,'population':'EUR','release':'synthetic-reference-1','hypotheses':[h],'variants':h+'-variants.tsv','pairs':h+'-pairs.tsv','coverage':'complete_window','window_bp':200}
        if mode in ('thresholded','inclusive-boundary','strict-boundary'):r.update(coverage='thresholded_window',minimum_reported_r_squared=.81 if 'boundary' in mode else .2,reporting_comparison='greater_than' if mode=='strict-boundary' else 'greater_than_or_equal')
        if mode=='short-window':r['window_bp']=199
        if mode=='partial-pairs':r['coverage']='positive_edges_only'
        refs.append(r)
    policy={'minimum_r_squared':.81 if 'boundary' in mode else .8,'maximum_distance_bp':200,'references':refs}
    if mode=='partial-pairs':policy['require_complete_coverage']=False
    if mode=='bounds':policy['maximum_pairs']=1
    study={'schema_version':1,'project':{'id':'finding-grouping-'+mode,'title':'Synthetic LD finding redundancy','description':'Software and mathematical verification only; no scientific/AI appraisal.','finding_grouping':policy},'resources':{'network':'offline','downloads':'deny'},'assistance':{'enabled':False,'impact_max_payload_chars':2000000,'impact_batch_size':1000},'datasets':[],'hypotheses':hypotheses};write(p/'study.json',study)
    stable={str(f.relative_to(p)):sha(f) for f in p.iterdir() if f.is_file() and not f.name.endswith('-pairs.tsv')};write(p/'stable-input-sha256.json',stable)
def stage_record(p):
    inventory,im=one(p,'finding_inventory');meta=[]
    for folder,m in operations(p):
        if m['operation_type']=='meta_analysis':meta.append({'hypothesis':m['hypothesis'],'canonical_sha256':sha(folder/'meta-analysis.tsv'),'reused_from_cache':m.get('reused_from_cache',False),'analysis_executed_by_jlinalg':m['details']['analysis_executed_by_jlinalg']})
    return {'grouping_complete':im['details']['finding_grouping']['complete'],'inventory_reused':im.get('reused_from_cache',False),'inventory_input_sha256':im['input_sha256'],'inventory_manifest_sha256':sha(inventory/'manifest.yaml'),'meta':meta,'run_id':yaml.safe_load((p/'state/project-status.yaml').read_text())['run_id']}
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];seals={};checks=0;payloads=[];cases=[]
    for mode in MODES:
        p=root/mode;study=json.loads((p/'study.json').read_text());policy=study['project']['finding_grouping'];inventory,im=one(p,'finding_inventory');assert im['state']=='complete';summary=im['details']['finding_grouping'];assert summary['complete']==(mode not in WAIT),(mode,summary);assert im.get('reused_from_cache') is True
        for name,digest in json.loads((p/'stable-input-sha256.json').read_text()).items():assert sha(p/name)==digest
        for folder,m in operations(p):
            for name,digest in m.get('output_sha256',{}).items():assert sha(folder/name)==digest;seals[str((folder/name).relative_to(root))]=digest
            assert m['operation_type'] not in ('association','rare_variant_scores','rare_variant_analysis')
        data=[json.loads(line) for line in (inventory/'finding-inventory.jsonl').read_text().splitlines()];assert len(data)==6*len(study['hypotheses']);assert sum(f['candidate'] for f in data)==5*len(study['hypotheses'])
        for h in study['hypotheses']:
            meta,mm=one(p,'meta_analysis',h['id']);assert mm['details']['analysis_executed_by_jlinalg'] is False;canonical=rows(meta/'meta-analysis.tsv');original={r['feature_id']:r for r in rows(p/(h['id']+'-meta.tsv'))}
            for r in canonical:assert math.isclose(float(r['adjusted_p']),float(original[r['feature_id']]['adjusted_p']),rel_tol=1e-11)
        if mode not in WAIT:
            # No P values enter this oracle: all supplied reference nodes and qualifying edges participate.
            graph={};keys={}
            for ref in policy['references']:
                aliases={r['variant_id']:(ref['genome_build'],r['chromosome'],int(r['position']),r['ref'],r['alt']) for r in rows(p/ref['variants'])};keys.update({(h,alias):key for h in ref['hypotheses'] for alias,key in aliases.items()})
                for key in aliases.values():graph.setdefault(key,set())
                for r in rows(p/ref['pairs']):
                    a,b=aliases[r['variant_a']],aliases[r['variant_b']]
                    if r['r_squared'] not in ('NA','NaN','.') and a[1]==b[1] and abs(a[2]-b[2])<=policy['maximum_distance_bp'] and float(r['r_squared'])>=policy['minimum_r_squared']:graph[a].add(b);graph[b].add(a)
            components={};number=0
            for key in graph:
                if key in components:continue
                number+=1;pending=[key]
                while pending:
                    node=pending.pop()
                    if node in components:continue
                    components[node]=number;pending.extend(graph[node]-components.keys())
            observed={}
            for f in data:
                key=keys[f['hypothesis'],f['feature_id']];group=f['redundancy_groups'];assert len(group)==1;expected=components[key]
                if expected in observed:assert observed[expected]==group[0]
                else:assert group[0] not in observed.values();observed[expected]=group[0]
                assert f['ld_grouping']['canonical_variant']==':'.join(map(str,key));assert f['story_group']=='feature:'+f['feature_id'];checks+=1
        else:assert all(not f.get('redundancy_groups') for f in data)
        impact,am=one(p,'impact_assessment');assert am['state']=='waiting_for_input'
        if mode in WAIT:assert not list(impact.rglob('evidence.yaml'))
        else:
            evidence=list(impact.rglob('evidence.yaml'));assert len(evidence)==1;payload=yaml.safe_load(evidence[0].read_text());assert len(payload['findings'])==5*len(study['hypotheses']);assert 'private_patient_' not in json.dumps(payload) and str(root) not in json.dumps(payload)
            for f in payload['findings']:
                for source in f['ld_grouping']['source_ids']:assert source in payload['source_catalog'];assert 'artifact' not in payload['source_catalog'][source]
                assert any(c['kind']=='ld_grouping' for c in f['caveats'])
            payloads.append({'case':mode,'findings':len(payload['findings']),'sha256':sha(evidence[0])})
        for metadata in impact.rglob('metadata.yaml'):
            m=yaml.safe_load(metadata.read_text());assert m['request_attempted'] is False and m['request_sent'] is False
        if mode in ('missing-resume','changed-reference'):
            history=json.loads((p/'reference-history.json').read_text());a,b,c=history;assert a['grouping_complete']==(mode=='changed-reference') and b['grouping_complete'] is True and b['inventory_reused'] is False and c['inventory_reused'] is True;assert a['meta'][0]['canonical_sha256']==b['meta'][0]['canonical_sha256']==c['meta'][0]['canonical_sha256'];assert b['meta'][0]['reused_from_cache'] is True and c['meta'][0]['reused_from_cache'] is True;assert a['inventory_input_sha256']!=b['inventory_input_sha256']
        cli(jar,'verify','--project',p);cases.append({'case':mode,'grouping_complete':summary['complete'],'candidates':sum(f['candidate'] for f in data)})
    report={'passed':True,'cases':cases,'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'independent_group_memberships_checked':checks,'sealed_outputs':len(seals),'actual_unsent_impact_payloads':payloads,'all_eligible_candidates_in_payloads_when_grouping_ready':True,'source_meta_fdr_preserved':True,'no_statistical_fits':True,'missing_or_changed_ld_only_recomputes_inventory_and_downstream':True,'model_requests_attempted':False,'scientific_appraisal_executed':False,'full_pipeline_conformance':False}
    for name,d in [('verification.json',report),('sealed-output-sha256.json',seals)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==d
        else:write(root/name,d)
    print(json.dumps(report))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=pathlib.Path);ap.add_argument('--jar',type=pathlib.Path,required=True);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name;shutil.copyfile(pathlib.Path(__file__),software/'finding-grouping-workflow.py');write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'verifier_sha256':sha(software/'finding-grouping-workflow.py'),'pinned_before_execution':True})
        for mode in MODES:
            generate(root,mode);p=root/mode;cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-ld-grouping');history=[stage_record(p)]
            if mode=='missing-resume':shutil.copyfile(p/'H1-pending-pairs.tsv',p/'H1-pairs.tsv')
            if mode=='changed-reference':shutil.copyfile(p/'H1-pairs.tsv',p/'H1-initial-pairs.tsv');(p/'H1-pairs.tsv').write_text((p/'H1-pairs.tsv').read_text().replace('rs3\trs4\t.01','rs3\trs4\t.81'),encoding='utf-8')
            cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-ld-grouping');history.append(stage_record(p));cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-ld-grouping');history.append(stage_record(p));write(p/'reference-history.json',history)
    verify(root,jar)
