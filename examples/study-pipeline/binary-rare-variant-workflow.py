"""Offline packaged conditional binary rare-set, coverage and supplied-stage checks.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
Synthetic independent participants and fixed nulls only; no biological, generic
binary mixed-model, genome-wide calibration or external-service claim.
"""
import argparse,csv,hashlib,json,math,pathlib,shutil,subprocess
import yaml

MODES=('native','cohort-private','monte-carlo','known-offset','coverage','intersection','unknown-slope','related','minimum-cohorts','supplied')
WAITING={'coverage','unknown-slope','related','minimum-cohorts'}
TESTS={'burden','skat','skat-o','acat-v','acat-o'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,x):p.write_text(json.dumps(x,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def cli(jar,*args):
    r=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8')
    assert r.returncode==0,(args,r.returncode,r.stdout,r.stderr)
    return r.stdout
def operations(p):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text(encoding='utf-8'))
    return [(m.parent,yaml.safe_load(m.read_text(encoding='utf-8'))) for m in sorted((p/'runs'/state['run_id']/'operations').glob('*/manifest.yaml'))]
def one(p,kind):
    matches=[x for x in operations(p) if x[1]['operation_type']==kind];assert len(matches)==1,(p,kind,len(matches));return matches[0]
def generate(root,mode):
    p=root/mode;p.mkdir();datasets=[]
    analysis={'mode':'manual','goals':['rare_variant_analysis','impact_assessment'],'association':{'model':'glm','family':'binomial','link':'logit'},'multiple_testing':{'method':'BY','alpha':.05},'rare_variants':{'enabled':True,'trait_id':'disease','minimum_info':.8,'masks':[{'id':'coding','consequences':['coding'],'maximum_maf':.01}],'binary':{'mode':'exact','strata_column':'stratum','maximum_work':100000000,'maximum_total_work':1000000000}}}
    h={'id':'H','outcome':'disease','covariates':['stratum'],'description':'Fixed-null synthetic numerical verification, not a biological result.','inputs':{'discovery':['a','b'],'replication':['v']},'analysis':analysis}
    b=analysis['rare_variants']['binary']
    if mode=='monte-carlo':b.update(mode='monte_carlo',pilot_draws=200,draws=2000)
    if mode=='known-offset':b.update(offset_column='offset',fixed_effects=True,known_effects_provenance={'producer':'synthetic fixed design; not outcome-fitted'})
    if mode=='intersection':b['variant_coverage']='intersection'
    if mode=='unknown-slope':h['covariates'].append('age')
    if mode=='minimum-cohorts':analysis['rare_variants']['minimum_cohorts']=4
    for cohort in ('a','b','v'):
        ids=[f'private_patient_{cohort}_{i}' for i in range(1000)]
        vcf=['##fileformat=VCFv4.2','##contig=<ID=1>','##INFO=<ID=R2,Number=1,Type=Float,Description="Quality">','##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">','\t'.join(['#CHROM','POS','ID','REF','ALT','QUAL','FILTER','INFO','FORMAT']+ids)]
        for position,carriers,info in ((100,{0},1),(110,{1},1),(120,{2,3},1),(130,set(range(40)),1),(140,{4},.1)):
            if mode in ('coverage','intersection') and cohort=='v' and position==110:continue
            if mode=='cohort-private' and cohort!='a' and position==110:carriers=set()
            vcf.append('\t'.join(['1',str(position),f'rs{position}','A','G','.','PASS',f'R2={info}','GT']+['0/1' if i in carriers else '0/0' for i in range(1000)]))
        (p/f'{cohort}.vcf').write_text('\n'.join(vcf)+'\n',encoding='utf-8')
        (p/f'{cohort}-people.tsv').write_text('id\tdisease\tstratum\toffset\tage\n'+''.join(f'{identity}\t{int(i<5)}\t{i%2}\t{.2*(i%3)-.1}\t{30+i%40}\n' for i,identity in enumerate(ids)),encoding='utf-8')
        (p/f'{cohort}-annotations.tsv').write_text('variant_id\tset_id\tconsequence\n'+''.join(f'1:{pos}:A:G\t'+('G1' if pos in (100,110) else 'G2')+'\tcoding\n' for pos in (100,110,120,130,140)),encoding='utf-8')
        d={'id':cohort,'cohort':f'cohort-{cohort}','type':'gwas','stage':'processed_matrix','genome_build':'GRCh38','annotation_release':'synthetic-1','files':{'matrix':f'{cohort}.vcf','phenotype':f'{cohort}-people.tsv','variant_annotation':f'{cohort}-annotations.tsv'}}
        if mode=='related':d['metadata']={'related_participants':True}
        datasets.append(d)
    if mode=='supplied':
        source,manifest=one(root/'native','rare_variant_analysis');shutil.copyfile(source/'rare-primary-results.tsv',p/'completed.tsv')
        analysis={'mode':'manual','goals':['rare_variant_analysis','impact_assessment'],'multiple_testing':{'method':'BY','alpha':.05},'supplied_results':[{'id':'completed-sets','kind':'rare_variant_analysis','files':{'results':'completed.tsv'},'scope':{'phenotype':'disease','cohorts':['cohort-a','cohort-b','cohort-v']},'method':{'name':'Frozen synthetic conditional binary sets','software':'JLinAlg','version':'local-checkpoint'},'provenance':{'source_results_sha256':sha(source/'rare-primary-results.tsv')},'limitations':[manifest['details']['required_manuscript_caveat']],'family':{'id':'all-sets-and-tests','coverage':'complete','tested_count':10,'adjustment':'BY'}}]}
        h={'id':'H','description':'Reuse completed binary tests without participant inputs or refitting.','analysis':analysis};datasets=[]
    write(p/'study.json',{'schema_version':1,'project':{'id':'binary-rare-'+mode,'title':'Synthetic conditional binary rare-set acceptance','description':'Software verification only; no biological findings.'},'resources':{'network':'offline','downloads':'deny'},'assistance':{'enabled':False,'impact_max_payload_chars':2000000,'impact_batch_size':1000},'datasets':datasets,'hypotheses':[h]})
    write(p/'input-sha256.json',{f.name:sha(f) for f in p.iterdir() if f.is_file()})
def adjustment(items):
    family=sorted((float(r['p_value']),r['feature_id'],r['mask_id'],r['test'],r) for r in items if r['p_value']!='NA');n=len(family);harmonic=sum(1/i for i in range(1,n+1));q=1.
    for rank in range(n,0,-1):
        value,_,_,_,row=family[rank-1];q=min(q,value*n*harmonic/rank);assert math.isclose(float(row['adjusted_p']),q,rel_tol=1e-10,abs_tol=1e-290),(row,q)
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];assert sha(root/'software/binary-rare-variant-workflow.py')==software['verifier_sha256'];records=[];seals={};checks=0
    for mode in MODES:
        p=root/mode
        for name,digest in json.loads((p/'input-sha256.json').read_text()).items():assert sha(p/name)==digest;checks+=1
        for folder,m in operations(p):
            for name,digest in m.get('output_sha256',{}).items():assert sha(folder/name)==digest;seals[str((folder/name).relative_to(root))]=digest
        folder,m=one(p,'rare_variant_analysis');assert m['state']==('waiting_for_input' if mode in WAITING else 'complete'),(mode,m['state'],m.get('summary'))
        if mode in ('unknown-slope','related'):
            assert all(d['state']=='waiting_for_input' for _,d in operations(p) if d['operation_type']=='rare_variant_scores');assert not (folder/'rare-results.tsv').exists()
        else:
            result=rows(folder/('rare-results.tsv' if mode!='supplied' else 'rare-results.tsv'));primary=[r for r in result if r.get('scope','primary')=='primary'];assert len(primary)==10;assert {r['test'] for r in primary}==TESTS
            if mode in WAITING:assert all(r['adjusted_p']=='NA' for r in primary)
            else:
                adjustment(primary)
                if mode!='supplied':
                    for scope in {r['scope'] for r in result}:adjustment([r for r in result if r['scope']==scope])
                    sensitivity=rows(folder/'rare-sensitivity-results.tsv');assert len(sensitivity)==30
                    for scope in {r['scope'] for r in sensitivity}:adjustment([r for r in sensitivity if r['scope']==scope])
                    for r in result:assert r['result_type']=='unsigned_set' and all(r[k]=='NA' for k in ('beta','se','direction'));assert int(r['n_cases'])+int(r['n_controls'])==int(r['n'])
                    assert int(primary[0]['n'])==3000 and int(primary[0]['n_cases'])==15
                    assert m['details']['cohort_influence']['primary_results_sha256']==sha(folder/'rare-results.tsv')
                    assert len(rows(folder/'rare-replication.tsv'))>0
                    for _,d in operations(p):
                        if d['operation_type']=='rare_variant_scores':assert d['details']['single_variant_mac_cutoff_applied'] is False;assert d['details']['retained_variants_with_mac_below_20']>=2
                    assert m.get('reused_from_cache') is True
                else:
                    assert m['details']['analysis_executed_by_jlinalg'] is False;assert not list(folder.glob('binary-fit-cache/*'))
                    expected=rows(one(root/'native','rare_variant_analysis')[0]/'rare-primary-results.tsv')
                    for r,e in zip(primary,expected):assert r['test']==e['test'] and math.isclose(float(r['p_value']),float(e['p_value']),rel_tol=1e-12) and math.isclose(float(r['adjusted_p']),float(e['adjusted_p']),rel_tol=1e-12)
                if mode=='cohort-private':
                    assert {r['n_variants'] for r in primary if r['feature_id']=='G1'}=={'2'}
                    for source,d in operations(p):
                        if d['operation_type']=='rare_variant_scores' and d['details']['dataset']!='a':
                            q=next(r for r in rows(source/'variant-qc.tsv') if r['variant_id']=='1:110:A:G');assert q['retained']=='true' and float(q['minor_allele_count'])==0
                if mode=='monte-carlo':
                    for r in primary:assert int(r['draws'])==2000 and math.isclose(float(r['p_value']),(int(r['tail_draws'])+1)/2001,rel_tol=1e-12)
            checks+=15
        inv,im=one(p,'finding_inventory');text=(inv/'finding-inventory.jsonl').read_text(encoding='utf-8');assert 'private_patient_' not in text;assert 'known_offset' not in text
        findings=[json.loads(line) for line in text.splitlines()];candidates=[f for f in findings if f['candidate']]
        if mode in WAITING:assert not candidates
        impact,_=one(p,'impact_assessment');payloads=list(impact.rglob('evidence.yaml'))
        if mode not in WAITING:
            assert len(payloads)==1;payload=yaml.safe_load(payloads[0].read_text(encoding='utf-8'));assert {f['finding_id'] for f in payload['findings']}=={f['finding_id'] for f in candidates};assert len(candidates)==2
            serialized=json.dumps(payload);assert 'private_patient_' not in serialized and 'profile_null_mean' not in serialized and 'conditional-genotypes.tsv' not in serialized
            for f in payload['findings']:
                primary=[o for o in f['observations'] if o.get('primary')];assert len(primary)==1 and primary[0]['adjustment']=='BY' and primary[0]['testing_denominator']==10
                if mode!='supplied':assert 'null_calibration' in primary[0] and 'log_p_value' in primary[0] and 'tail_probability_upper_95' in primary[0]
        for meta in impact.rglob('metadata.yaml'):
            declaration=yaml.safe_load(meta.read_text());assert declaration['request_sent'] is False and declaration['request_attempted'] is False
        cli(jar,'verify','--project',p);checks+=3;records.append({'case':mode,'state':m['state'],'reused_from_cache':m.get('reused_from_cache',False),'producer_origin':m.get('details',{}).get('execution_origin','native')})
    report={'passed':True,'cases':records,'checks':checks,'sealed_outputs':len(seals),'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'BY_full_scope_families_verified':True,'private_rows_absent_from_finding_inventory':True,'complete_candidate_handoff_without_private_rows':True,'model_requests_attempted':False,'supplied_binary_results_reused_without_refit':True,'real_scientific_calibration':False,'external_services_tested':False,'full_pipeline_conformance':False}
    for name,value in [('verification.json',report),('sealed-output-sha256.json',seals)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==value
        else:write(root/name,value)
    print(json.dumps(report,indent=2))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=pathlib.Path);ap.add_argument('--jar',type=pathlib.Path,required=True);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);(root/'software').mkdir();shutil.copyfile(jar,root/'software'/jar.name);jar=root/'software'/jar.name;shutil.copyfile(pathlib.Path(__file__).resolve(),root/'software/binary-rare-variant-workflow.py');write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'verifier_sha256':sha(root/'software/binary-rare-variant-workflow.py'),'pinned_before_execution':True})
        for mode in MODES:
            generate(root,mode)
            for _ in range(2):cli(jar,'auto',root/mode/'study.json','--offline','--actor-type','external','--actor-id','synthetic-binary-rare-acceptance')
    verify(root,jar)
