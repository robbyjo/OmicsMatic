"""Offline cross-mask gene/set omnibus, scoped reuse and missing-input acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
Requires NumPy, mpmath and PyYAML. Synthetic software verification only.
"""
import argparse,csv,hashlib,importlib.util,json,math,shutil,subprocess
from pathlib import Path
import mpmath as mp
import numpy as np
import yaml

CASES=('primary','all-tests','acat','boundary','gene-grouping','missing-component','ineligible','failed-component','unmapped','underflow','acat-underflow','supplemental','provided','provided-selected','provided-incomplete','provided-scopes','cohort-scopes','missing-map-resume','native')
HELD={'missing-component','failed-component','unmapped'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,x):p.write_text(json.dumps(x,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def save(p,data,fields=None):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields or list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def cli(jar,p,*args):
    r=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8')
    with (p/'commands.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps({'args':list(map(str,args)),'exit_code':r.returncode,'stdout':r.stdout,'stderr':r.stderr})+'\n')
    assert r.returncode==0,(args,r.stdout,r.stderr)
def operations(p):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text(encoding='utf-8'));return [(f.parent,yaml.safe_load(f.read_text(encoding='utf-8'))) for f in sorted((p/'runs'/state['run_id']/'operations').glob('*/manifest.yaml'))]
def one(p,kind):
    found=[x for x in operations(p) if x[1]['operation_type']==kind];assert len(found)==1,(p,kind,len(found));return found[0]
def adjust(logs,method='BY'):
    # Independent NumPy step-up correction in natural-log space.
    if not len(logs):return []
    values=np.asarray(logs);order=np.argsort(values,kind='stable');n=len(values);factor=sum(1/i for i in range(1,n+1)) if method=='BY' else 1
    ranked=np.minimum.accumulate((values[order]+math.log(n*factor)-np.log(np.arange(1,n+1)))[::-1])[::-1];out=np.empty(n);out[order]=np.minimum(0,ranked);return out.tolist()
def combined(logs,method):
    if method=='bonferroni_min' or 0 in logs:return min(0,min(logs)+math.log(len(logs)))
    # Direct high-precision Cauchy transformation, independent of Java signed logs.
    with mp.workdps(650):
        t=mp.fsum(mp.cot(mp.pi*mp.exp(mp.mpf(str(x)))) for x in logs)/len(logs)
        value=mp.atan(1/t)/mp.pi if t>0 else 1-mp.atan(-1/t)/mp.pi if t<0 else mp.mpf('.5')
        return float(mp.log(value))
def definition(kind,file,n,selected=False):return {'id':'completed','kind':kind,'files':{'results':file,'feature_annotation':'genes.tsv'},'scope':{'phenotype':'synthetic disease','species':'human'},'method':{'name':'Synthetic registered source tests','software':'fixture','unit':'gene'},'family':{'id':'full-source-family','coverage':'selected_only' if selected else 'complete','tested_count':n,'adjustment':'BY','adjusted_p_from_full_family':True},'limitations':['PRIVATE_OMNIBUS_SENTINEL']}
def generate(root,case):
    p=root/case;native=case=='native'
    if native:
        spec=importlib.util.spec_from_file_location('binary_fixture',root/'software/binary-rare-variant-workflow.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);module.generate(root,'native');s=json.loads((p/'study.json').read_text());a=s['hypotheses'][0]['analysis'];a['rare_variants']['masks'].append({**a['rare_variants']['masks'][0],'id':'coding-second'});genes=[{'feature_id':f'G{i}','gene_id':f'ENSG{i}','gene_symbol':f'G{i}','mapping_status':'missing_location'} for i in (1,2)]
        for d in s['datasets']:d['species']='human'
    else:
        p.mkdir();a={'mode':'manual','goals':['rare_set_omnibus','impact_assessment'],'multiple_testing':{'method':'BY','alpha':.05}};s={'schema_version':1,'project':{'id':'rare-omnibus-'+case,'title':'Synthetic registered unit omnibus acceptance','description':'No biological, causal or external-service conclusions.'},'datasets':[],'hypotheses':[{'id':'H','description':'Synthetic registered omnibus software verification','outcome':'synthetic disease','analysis':a}]};genes=[{'feature_id':f's{i}','gene_id':f'ENSG{i}','gene_symbol':f'G{i}','mapping_status':'missing_location'} for i in range(1,7)]
        data=[]
        for i in range(1,7):
            for mask in ('lof','missense'):
                for test in ('acat-o','burden'):
                    value=([1e-6,.0002,.02,.3,.5,.8][i-1] if mask=='lof' else .2) if test=='acat-o' else (1e-10 if i==5 else .9)
                    log=math.log(value)
                    if case in ('underflow','acat-underflow') and i==1 and mask=='lof' and test=='acat-o':value=0.;log=-1000.
                    if case=='boundary' and i==1 and mask=='missense' and test=='acat-o':value=1.;log=0.
                    if case in ('missing-component','ineligible') and i==2 and mask=='missense':continue
                    data.append({'feature_id':f's{i}','mask_id':mask,'test':test,'p_value':str(value),'log_p_value':str(log),'adjusted_p':'NA','log_adjusted_p':'NA','is_primary_test':str(test=='acat-o').lower(),'status':'ok'})
        q=adjust([float(r['log_p_value']) for r in data])
        for r,value in zip(data,q):r['adjusted_p']=str(math.exp(value));r['log_adjusted_p']=str(value)
        if case=='failed-component':
            for r in data:
                if (r['feature_id'],r['mask_id'],r['test'])==('s2','missense','acat-o'):r.update(status='model_failed',p_value='NA',log_p_value='NA',adjusted_p='NA',log_adjusted_p='NA')
        save(p/'rare.tsv',data);a['supplied_results']=[definition('rare_variant_analysis','rare.tsv',len(data))]
        if case.startswith('provided'):
            values=[{'scope':scope,'feature_id':f'ENSG{i}','p_value':str(.001*i),'adjusted_p':str(.004*i)} for scope in (('primary','discovery','cohort:v') if case=='provided-scopes' else ('primary',)) for i in (1,2)]
            save(p/'completed.tsv',values);d=definition('rare_set_omnibus','completed.tsv',20000 if case in ('provided-selected','provided-incomplete') else 2,case=='provided-selected')
            if case=='provided-scopes':d['method']['scope_families']={scope:{**d['family'],'id':scope} for scope in ('discovery','cohort:v')}
            a['supplied_results']=[d];genes=[{**r,'feature_id':r['gene_id']} for r in genes]
        if case=='cohort-scopes':
            a.pop('supplied_results');a['goals'].insert(0,'rare_variant_analysis');a['rare_variants']={'tests':['skat'],'primary_test':'skat','unsigned_pooling':'acat','trait_id':'synthetic disease'};s['hypotheses'][0]['inputs']={'discovery':['a','b'],'replication':['v']};a['replication']={'adjustment_scope':'discovery_subset'}
            for j,cohort in enumerate(('a','b','v')):
                save(p/f'{cohort}.tsv',[{'feature_id':r['feature_id'],'mask_id':r['mask_id'],'test':'skat','p_value':str(min(.99,float(r['p_value'])*(j+1)))} for r in data if r['test']=='acat-o'])
                s['datasets'].append({'id':cohort,'cohort':cohort,'type':'gwas','stage':'set_association_results','species':'human','genome_build':'GRCh38','files':{'results':f'{cohort}.tsv'},'metadata':{'complete_testing_family':True,'set_definition_id':'synthetic-v1','trait_id':'synthetic disease'}})
    save(p/'genes.tsv',genes)
    a['rare_omnibus']={'selection':'supplemental' if case=='supplemental' else 'primary','unit':'gene' if case in ('gene-grouping','unmapped','missing-map-resume') else 'set','method':'acat' if case in ('acat','boundary','acat-underflow') else 'bonferroni_min','component_tests':'all' if case=='all-tests' else 'primary','adjustment':'BY'}
    if case in ('gene-grouping','unmapped','missing-map-resume'):
        a['rare_omnibus']['gene_map']='unit-map.tsv';mapping=genes.copy()
        if case=='gene-grouping':mapping=[{**r,'gene_id':'ENSG1','gene_symbol':'G1'} if r['feature_id']=='s2' else r for r in mapping]
        if case=='unmapped':mapping=mapping[:-1]
        if case!='missing-map-resume':save(p/'unit-map.tsv',mapping)
    if case=='ineligible':a['rare_omnibus']['component_eligibility']='eligibility.tsv';save(p/'eligibility.tsv',[{'scope':'*','feature_id':'s2','mask_id':'missense','test':'acat-o','reason':'no_eligible_variants'}])
    if native:a['goals'].append('rare_set_omnibus');a['rare_omnibus']['unit']='gene';a['rare_omnibus']['gene_map']='genes.tsv'
    if case in ('primary','gene-grouping','provided','provided-selected','provided-incomplete'):
        a['goals']+=['gene_ontology','literature_search'];a.setdefault('rare_variants',{})['followup']={'minimum_term_genes':1};a['operations']={'gene_ontology':{'result_source':'rare_sets'}};a['bioinformatics']={'feature_source':'rare_sets','phenotype_terms':['synthetic disease'],'literature_terms':['rare genetic variants']}
        db=p/'fixture-resources/enrichment/go-human';db.mkdir(parents=True);(db/'data.gmt').write_text('PAIR\tSynthetic first pair\tG1\tG2\nROOT\tSynthetic complete background\tG1\tG2\tG3\tG4\tG5\tG6\n',encoding='utf-8');(db/'manifest.properties').write_text('format_version=1\nprovider=GO\nspecies=human\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt='+sha(db/'data.gmt')+'\n',encoding='utf-8')
    s['resources']={'network':'offline','downloads':'deny','directory':'fixture-resources'};s['assistance']={'enabled':False,'impact_max_payload_chars':2000000,'impact_batch_size':1000};write(p/'study.json',s)
    write(p/'original-input-hashes.json',{f.relative_to(p).as_posix():sha(f) for f in p.rglob('*') if f.is_file() and f.name!='original-input-hashes.json'})
def history(p):
    _,om=one(p,'rare_set_omnibus');raw=[m for _,m in operations(p) if m['operation_type']=='rare_variant_analysis'];return {'state':om['state'],'omnibus_reused':om.get('reused_from_cache',False),'source_reused':raw[0].get('reused_from_cache',False) if raw else None}
def verify(root,jar):
    pins=json.loads((root/'software.json').read_text());assert pins['jar_sha256']==sha(jar);assert pins['verifier_sha256']==sha(Path(__file__));sealed={};records=[];comparisons=0
    for case in CASES:
        p=root/case
        for name,digest in json.loads((p/'original-input-hashes.json').read_text()).items():assert sha(p/name)==digest
        if case=='missing-map-resume':
            events=json.loads((p/'history.json').read_text());assert events[0]['state']=='waiting_for_input';assert events[1]['state']=='complete' and not events[1]['omnibus_reused'] and events[1]['source_reused'];assert events[2]['omnibus_reused'];assert sha(p/'unit-map.tsv')==json.loads((p/'delivered-input-hashes.json').read_text())['unit-map.tsv']
        for folder,m in operations(p):
            for name,digest in m.get('output_sha256',{}).items():assert sha(folder/name)==digest;sealed[(folder/name).relative_to(root).as_posix()]=digest
        folder,m=one(p,'rare_set_omnibus');assert m['state']==('waiting_for_input' if case in HELD else 'complete'),(case,m['state'],m.get('summary'));t=rows(folder/'rare-omnibus-results.tsv')
        if case.startswith('provided'):
            assert m['details']['analysis_executed_by_jlinalg'] is False;assert not (folder/'omnibus-components.tsv').exists();assert not any(x['operation_type']=='rare_variant_analysis' for _,x in operations(p));source=rows(p/'completed.tsv');assert [(r['scope'],r['feature_id'],r['adjusted_p']) for r in t]==[(r['scope'],r['feature_id'],r['adjusted_p']) for r in source]
        else:
            source=rows(folder/'source-rare-results.tsv');assert m['details']['source_results_sha256']==sha(folder/'source-rare-results.tsv');components=rows(folder/'omnibus-components.tsv');groups={};expected={}
            for r in components:groups.setdefault((r['scope'],r['unit_id']),[]).append(r)
            source_by={(r.get('scope','primary'),r['feature_id'],r['mask_id'],r['test']):r for r in source}
            for r in t:
                key=(r['scope'],r['feature_id']);parts=groups[key];logs=[]
                for part in parts:
                    if part['disposition']=='included':
                        src=source_by[(part['scope'],part['source_set_id'],part['mask_id'],part['test'])];v=float(src['log_p_value']) if src.get('log_p_value','NA')!='NA' else math.log(float(src['p_value']));assert abs(float(part['source_log_p_value'])-v)<1e-10;logs.append(v)
                assert int(r['expected_components'])==len(parts);assert int(r['component_count'])==len(logs)
                if r['status']=='ok':
                    v=combined(logs,'acat' if case in ('acat','boundary','acat-underflow') else 'bonferroni_min');assert math.isclose(float(r['log_p_value']),v,rel_tol=1e-11,abs_tol=1e-10),(case,r,v);expected[key]=v;comparisons+=1
            for scope in {r['scope'] for r in t}:
                scoped=[r for r in t if r['scope']==scope]
                if case in HELD:assert all(r['adjusted_p']=='NA' for r in scoped)
                else:
                    q=adjust([expected[(scope,r['feature_id'])] for r in scoped if r['status']=='ok']);i=0
                    for r in scoped:
                        if r['status']!='ok':continue
                        assert math.isclose(float(r['log_adjusted_p']),q[i],rel_tol=1e-11,abs_tol=1e-10);i+=1;comparisons+=1
            if case=='gene-grouping':assert len(t)==5 and int(next(r for r in t if r['feature_id']=='ENSG1')['component_count'])==4
            if case=='boundary':assert next(r for r in t if r['feature_id']=='s1')['effective_method']=='bonferroni_min_boundary_fallback'
            if case in ('underflow','acat-underflow'):assert next(r for r in t if r['feature_id']=='s1')['p_value']=='0.0';assert float(next(r for r in t if r['feature_id']=='s1')['log_adjusted_p']) < -990
            if case in ('cohort-scopes','native'):
                replication=rows(folder/'rare-omnibus-replication.tsv');assert all(r['same_sign']=='NA' for r in replication)
                discovery=[r for r in t if r['scope']=='discovery' and float(r['adjusted_p'])<.05];targets={r['feature_id']:r for r in t if r['scope']=='cohort:v'}
                family=discovery if case=='cohort-scopes' else list(targets.values());q=adjust([float(targets[r['feature_id']]['log_p_value']) for r in family]);expected_q={r['feature_id']:value for r,value in zip(family,q)}
                for r in replication:assert int(r['validation_testing_denominator'])==len(family);assert math.isclose(float(r['validation_log_adjusted_p']),expected_q[r['feature_id']],abs_tol=1e-10);comparisons+=1
        inventory=one(p,'finding_inventory')[0];payload=(inventory/'finding-inventory.jsonl').read_text();assert 'PRIVATE_OMNIBUS_SENTINEL' not in payload
        if case in ('primary','gene-grouping','provided','provided-selected','provided-incomplete'):
            projection,pm=one(p,'rare_set_followup');assert pm['state']=='complete';assert pm['details']['source_unit_omnibus'] is True;assert pm['details']['source_sha256']==sha(folder/'rare-omnibus-results.tsv')
            go,gm=one(p,'gene_ontology');assert gm['state']==('waiting_for_input' if case in ('provided-selected','provided-incomplete') else 'complete')
            if case not in ('provided-selected','provided-incomplete'):
                anno={r['feature_id']:r['gene_symbol'] for r in rows(projection/'feature-annotation.tsv')};universe={anno[r['feature_id']] for r in t if r['scope']=='primary' and r['status']=='ok'};selected={anno[r['feature_id']] for r in t if r['scope']=='primary' and r['status']=='ok' and float(r['adjusted_p'])<.05}
                output=rows(go/'gene-ontology.tsv');logs=[]
                for r in output:
                    members=({'G1','G2'} if r['term_id']=='PAIR' else set(f'G{i}' for i in range(1,7)))&universe;k=len(members&selected);N=len(universe);n=len(selected);M=len(members);pvalue=sum(math.comb(M,x)*math.comb(N-M,n-x)/math.comb(N,n) for x in range(k,min(M,n)+1) if 0<=n-x<=N-M);assert math.isclose(float(r['p_value']),pvalue,abs_tol=1e-12);assert int(r['universe_genes'])==N;assert int(r['selected_genes'])==n;logs.append(math.log(pvalue));comparisons+=1
                for r,q in zip(output,adjust(logs)):assert math.isclose(float(r['adjusted_p']),math.exp(q),abs_tol=1e-12);comparisons+=1
            literature=one(p,'literature_search')[0];selection=rows(literature/'literature-selection.tsv');assert all(json.loads(r['selection_context'])['kind']=='rare_unit_omnibus' for r in selection)
        for line in payload.splitlines():
            finding=json.loads(line)
            for observation in finding.get('observations',[]):
                if observation.get('operation_type')=='rare_variant_analysis' and case!='supplemental':assert not observation.get('primary',False)
                if observation.get('operation_type')=='rare_set_omnibus' and case=='supplemental':assert not observation.get('primary',False)
        cli(jar,p,'verify','--project',p);records.append({'case':case,'state':m['state'],'rows':len(t)})
    report={'passed':True,'cases':records,'numerical_checks':comparisons,'sealed_artifacts':len(sealed),'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'source_family_preserved':True,'missing_components_not_nulls':True,'gene_and_mask_families_separate':True,'native_and_supplied_reuse':True,'real_null_calibration':False,'biological_appraisal':False,'full_pipeline_conformance':False}
    for name,data in [('verification.json',report),('sealed-artifacts.json',sealed)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==data
        else:write(root/name,data)
    print(json.dumps(report,indent=2))
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/'jlinalg.jar');jar=software/'jlinalg.jar'
        for name in ('rare-omnibus-workflow.py','binary-rare-variant-workflow.py'):shutil.copyfile(Path(__file__).with_name(name),software/name)
        write(root/'software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(software/'rare-omnibus-workflow.py'),'native_generator_sha256':sha(software/'binary-rare-variant-workflow.py'),'pinned_before_execution':True})
        for case in CASES:
            generate(root,case);p=root/case;cli(jar,p,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-omnibus-acceptance')
            if case=='missing-map-resume':
                events=[history(p)];shutil.copyfile(p/'genes.tsv',p/'unit-map.tsv');write(p/'delivered-input-hashes.json',{'unit-map.tsv':sha(p/'unit-map.tsv')});cli(jar,p,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-omnibus-acceptance');events.append(history(p))
            cli(jar,p,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-omnibus-acceptance')
            if case=='missing-map-resume':events.append(history(p));write(p/'history.json',events)
    verify(root,jar)
