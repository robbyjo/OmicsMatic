"""Offline rare-set primary projection, enrichment, literature and supplied reuse.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
Uses synthetic data and offline audited requests. It performs no biological or
AI source appraisal and never treats a gene/mask FDR as a combined gene FDR.
Requires PyYAML; the verifier otherwise uses the Python standard library.
"""
import argparse,csv,hashlib,importlib.util,json,math,pathlib,shutil,subprocess
import yaml

MODES=('native','complete','selected-only','unmapped','ambiguous','empty-selection','wrong-species','work-limit','mixed','provided-enrichment')
WAIT={'selected-only','unmapped','ambiguous','wrong-species','work-limit'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,x):p.write_text(json.dumps(x,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def cli(jar,*args):
    r=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8');assert r.returncode==0,(args,r.stdout,r.stderr);return r.stdout
def operations(p):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text());return [(m.parent,yaml.safe_load(m.read_text())) for m in sorted((p/'runs'/state['run_id']/'operations').glob('*/manifest.yaml'))]
def one(p,kind):
    result=[x for x in operations(p) if x[1]['operation_type']==kind];assert len(result)==1,(p,kind,len(result));return result[0]
def by(data):
    ordered=sorted(enumerate(data),key=lambda x:float(x[1]['p_value']));h=sum(1/i for i in range(1,len(data)+1));q=1
    for rank in range(len(data),0,-1):
        _,row=ordered[rank-1];q=min(q,float(row['p_value'])*len(data)*h/rank);row['adjusted_p']=str(q)
def artifact(kind,file,count,annotation=False,selected=False):
    d={'id':'provided-'+kind.replace('_','-'),'kind':kind,'files':{'results':file},'scope':{'phenotype':'synthetic disease','cohorts':['synthetic-cohort'],'species':'human','genome_build':'GRCh38','coordinate_system':'1-based-closed'},'method':{'name':'Synthetic source result fixture','software':'fixture','version':'1'},'provenance':{'producer':'synthetic numerical protocol'},'limitations':['Synthetic software verification; no biological calibration or source appraisal.'],'family':{'id':kind+'-full-family','coverage':'selected_only' if selected else 'complete','tested_count':count,'adjustment':'BY','adjusted_p_from_full_family':True}}
    if annotation:d['files']['feature_annotation']='genes.tsv'
    return d
def generate(root,mode):
    p=root/mode
    if mode=='native':
        spec=importlib.util.spec_from_file_location('binary_fixture',root/'software/binary-rare-variant-workflow.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);module.generate(root,'native');s=json.loads((p/'study.json').read_text());a=s['hypotheses'][0]['analysis'];n=2
        for d in s['datasets']:d['species']='human';d['files']['annotation']='genes.tsv';d['coordinate_system']='1-based-closed'
    else:
        p.mkdir();n=30;data=[]
        for mask in ('lof','missense'):
            for i in range(n):
                for test in ('burden','acat-o'):
                    value=(1e-9 if i==4 else .9) if test=='burden' else (1e-5*(i+1) if i<2 else .8)
                    data.append({'feature_id':f'set{i}','mask_id':mask,'test':test,'p_value':str(value),'adjusted_p':'NA','is_primary_test':str(test=='acat-o').lower()})
        by(data);count=len(data)
        if mode=='selected-only':data=[r for r in data if r['feature_id'] in ('set0','set1') and r['is_primary_test']=='true']
        table(p/'rare.tsv',data);provided=artifact('rare_variant_analysis','rare.tsv',count,mode!='unmapped',mode=='selected-only')
        if mode=='wrong-species':provided['scope']['species']='mouse'
        a={'mode':'manual','multiple_testing':{'method':'BY','alpha':.05},'supplied_results':[provided]};s={'schema_version':1,'project':{'id':'rare-followup-'+mode,'title':'Synthetic rare-set downstream acceptance','description':'No biological findings or actual external-service/AI appraisal.'},'datasets':[],'hypotheses':[{'id':'H','outcome':'synthetic disease','description':'Complete only missing work using original source families.','analysis':a}]}
    a['goals']=['rare_variant_analysis','gene_ontology','pathway_enrichment','literature_search','impact_assessment'];a['rare_variants']={**a.get('rare_variants',{}),'followup':{'minimum_term_genes':1}}
    a['bioinformatics']={'feature_source':'auto','phenotype_terms':['synthetic disease'],'literature_terms':['rare genetic variants']};a['operations']={'gene_ontology':{'result_source':'rare_sets'},'pathway_enrichment':{'result_source':'rare_sets'}}
    if mode=='work-limit':a['rare_variants']['followup']['maximum_testing_rows']=1
    if mode=='empty-selection':
        a['multiple_testing']['alpha']=1e-10;a['bioinformatics']['fdr']=1e-10;a['supplied_results'][0]['family']['alpha']=1e-10
    genes=[]
    for i in range(n):
        ident=f'G{i+1}' if mode=='native' else f'set{i}';number=i+1 if mode=='native' else i;genes.append({'feature_id':ident,'gene_id':f'ENSG{1000+number}','gene_symbol':f'G{number}','chromosome':'1','start':str(100+20*i),'end':str(115+20*i),'genome_build':'GRCh38'})
    if mode=='ambiguous':genes[1]['gene_id']=genes[0]['gene_id'];genes[1]['gene_symbol']=genes[0]['gene_symbol']
    table(p/'genes.tsv',genes)
    for folder,provider,key,namespace in [('go-human','GO','gene_symbol','symbol'),('reactome-human','Reactome','gene_id','ensembl')]:
        db=p/'fixture-resources/enrichment'/folder;db.mkdir(parents=True);names=[g[key] for g in genes];lines=[['PAIR','Synthetic first pair',*names[:2]],['CONTROL','Synthetic control members',*(names[2:4] or names[-1:])],['ROOT','Synthetic complete background',*names]];(db/'data.gmt').write_text('\n'.join('\t'.join(x) for x in lines)+'\n',encoding='utf-8');(db/'manifest.properties').write_text(f'format_version=1\nprovider={provider}\nspecies=human\ngene_id_type={namespace}\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt={sha(db/"data.gmt")}\n',encoding='utf-8')
    if mode=='mixed':
        table(p/'meta.tsv',[{'feature_id':'set0','beta':'.1','se':'.1','p_value':'1e-6','adjusted_p':'3e-6'},{'feature_id':'other','beta':'.1','se':'.1','p_value':'.9','adjusted_p':'1'}]);a['supplied_results'].append(artifact('meta_analysis','meta.tsv',2,True))
    if mode=='provided-enrichment':
        source,_=one(root/'complete','gene_ontology');shutil.copyfile(source/'gene-ontology.tsv',p/'provided-go.tsv');provided_go=artifact('gene_ontology','provided-go.tsv',len(rows(p/'provided-go.tsv')));provided_go['depends_on']=['provided-rare-variant-analysis'];provided_go['provenance']['source_enrichment_sha256']=sha(p/'provided-go.tsv');a['supplied_results'].append(provided_go)
    s['resources']={'network':'offline','downloads':'deny','directory':'fixture-resources'};s['assistance']={'enabled':False,'impact_max_payload_chars':2000000,'impact_batch_size':1000};write(p/'study.json',s)
    write(p/'input-sha256.json',{str(f.relative_to(p)):sha(f) for f in p.rglob('*') if f.is_file() and f.name!='input-sha256.json'})
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];records=[];seals={};checks=0
    for mode in MODES:
        p=root/mode
        for name,digest in json.loads((p/'input-sha256.json').read_text()).items():assert sha(p/name)==digest;checks+=1
        for folder,m in operations(p):
            for name,digest in m.get('output_sha256',{}).items():assert sha(folder/name)==digest;seals[str((folder/name).relative_to(root))]=digest
        projected,pm=one(p,'rare_set_followup');assert pm['state']=='complete' and pm.get('reused_from_cache') is True;projection=rows(projected/'rare-followup-results.tsv');family=yaml.safe_load((projected/'rare-followup.yaml').read_text())['source_testing_family'];assert len(projection)==(2 if mode=='native' else 4 if mode=='selected-only' else 60);assert family['tested_count']==(10 if mode=='native' else 120)
        raw=rows(projected/'rare-source-results.tsv');source={(r['feature_id'],r['mask_id'],r['test']):r for r in raw if r.get('scope','primary')=='primary'}
        for r in projection:assert r['adjusted_p']==source[r['feature_id'],r['mask_id'],r['test']]['adjusted_p'];assert r['is_primary_test']=='true'
        if mode!='native':
            original={(r['feature_id'],r['mask_id'],r['test']):r for r in rows(p/'rare.tsv')}
            for r in projection:assert math.isclose(float(r['adjusted_p']),float(original[r['feature_id'],r['mask_id'],r['test']]['adjusted_p']),rel_tol=1e-12)
        for kind,name in [('gene_ontology','gene-ontology.tsv'),('pathway_enrichment','pathway-enrichment.tsv')]:
            folder,m=one(p,kind);assert m['state']==('waiting_for_input' if mode in WAIT else 'complete'),(mode,kind,m['summary'])
            if mode in WAIT:assert not (folder/name).exists()
            else:
                result=rows(folder/name);assert len(result)==(3 if mode=='native' else 6);assert len({r['enrichment_id'] for r in result})==len(result);expected=[dict(r) for r in result];by(expected)
                for r,e in zip(result,expected):assert math.isclose(float(r['adjusted_p']),float(e['adjusted_p']),rel_tol=1e-10);assert r['association_testing_denominator']==str(family['tested_count'])
                if mode=='empty-selection':assert all(r['selected_genes']=='0' and float(r['p_value'])==float(r['adjusted_p'])==1 for r in result)
                elif mode!='native':
                    for r in result:
                        if r['term_id']=='PAIR':assert math.isclose(float(r['p_value']),1/math.comb(30,2),rel_tol=1e-12)
                if mode=='provided-enrichment' and kind=='gene_ontology':assert m['details']['analysis_executed_by_jlinalg'] is False;assert not (folder/'rare-enrichment.yaml').exists()
                else:assert m['details']['enrichment_source']=='rare_sets';assert 'Gene length' in m['details']['required_manuscript_caveat']
            checks+=7
        lit,lm=one(p,'literature_search');assert lm['state']=='waiting_for_input';selection=rows(lit/'literature-selection.tsv');expected=0 if mode in ('unmapped','empty-selection') else 2;assert len(selection)==expected,(mode,len(selection))
        for r in selection:
            context=json.loads(r['selection_context']);assert context
            if mode=='mixed' and r['feature_id']=='set0':assert context['kind']=='separate_source_families'
            else:assert context['kind']=='rare_set_primary_tests';assert len(context['qualifying_source_tests'])==(1 if mode=='native' else 2)
        audits=list(lit.glob('queries/*/request.yaml'));assert audits
        for path in audits:
            doc=yaml.safe_load(path.read_text());assert doc['request_attempted'] is False
        inventory,_=one(p,'finding_inventory');text=(inventory/'finding-inventory.jsonl').read_text();assert 'private_patient_' not in text;findings=[json.loads(x) for x in text.splitlines()];enrichment=[f for f in findings if f.get('finding_type')=='enrichment'];assert len(enrichment)==(0 if mode in WAIT else 6 if mode=='native' else 12)
        for f in enrichment:
            primary=[row for row in f['observations'] if row.get('primary')]
            context=[row for row in f['observations'] if not row.get('primary')]
            assert len(primary)==1 and primary[0]['mask_id'] in ('coding','lof','missense')
            assert len(context)==1 and context[0]['kind']=='literature_coverage'
            assert context[0]['query_evidence_state']=='no_feature_search_receipts'
            assert f['literature_coverage']['unique_queries']==0
        if mode=='mixed':assert any(c['kind']=='source_dependence' for f in findings if f['feature_id']=='set0' for c in f['caveats'])
        if mode!='native':assert not any(m['operation_type'] in ('rare_variant_masks','rare_variant_scores') for _,m in operations(p))
        cli(jar,'verify','--project',p);records.append({'case':mode,'source_family':family['tested_count'],'literature_signals':len(selection),'enrichment_observations':len(enrichment)});checks+=10
    report={'passed':True,'cases':records,'checks':checks,'sealed_outputs':len(seals),'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'original_joint_association_adjustment_preserved':True,'joint_mask_by_term_BY_verified':True,'native_and_supplied_rare_sets_connected':True,'provided_enrichment_reused_missing_pathway_completed':True,'real_scientific_calibration':False,'external_requests_attempted':False,'ai_appraisal':False,'full_pipeline_conformance':False}
    for name,data in [('verification.json',report),('sealed-output-sha256.json',seals)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==data
        else:write(root/name,data)
    print(json.dumps(report,indent=2))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=pathlib.Path);ap.add_argument('--jar',type=pathlib.Path,required=True);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name;shutil.copyfile(pathlib.Path(__file__).resolve(),software/'rare-followup-workflow.py');shutil.copyfile(pathlib.Path(__file__).resolve().with_name('binary-rare-variant-workflow.py'),software/'binary-rare-variant-workflow.py');write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'verifier_sha256':sha(software/'rare-followup-workflow.py'),'native_generator_sha256':sha(software/'binary-rare-variant-workflow.py'),'pinned_before_execution':True})
        for mode in MODES:
            generate(root,mode)
            for _ in range(2):cli(jar,'auto',root/mode/'study.json','--offline','--actor-type','external','--actor-id','synthetic-rare-followup-acceptance')
    verify(root,jar)
