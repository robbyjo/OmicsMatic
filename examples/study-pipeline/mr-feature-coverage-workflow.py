"""Offline all-eligible MR with missing gene annotations and reviewed exact-feature locations.

Pins executable/verifier before execution. All data and source approvals are
synthetic software fixtures, not scientific source appraisal or human attestations.
"""
import argparse,csv,hashlib,json,math,os,pathlib,shutil,subprocess
import yaml

MODES=['mapped-reference','unannotated-reviewed','unannotated-pending','ambiguous-reviewed','missing-source','location-conflict','build-conflict','wrong-feature','unapproved','explicit-cap','no-eligible','many-pairs','location-resume','supplied-primary']
WAIT={'unannotated-pending','missing-source','location-conflict','build-conflict','wrong-feature','unapproved'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as h:return list(csv.DictReader(h,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as h:w=csv.DictWriter(h,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def cli(jar,*args):
    result=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,encoding='utf-8')
    if result.returncode:raise RuntimeError(result.stdout)
def operations(p):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text(encoding='utf-8'))
    for folder in (p/'runs'/state['run_id']/'operations').iterdir():
        if (folder/'manifest.yaml').is_file():yield folder,yaml.safe_load((folder/'manifest.yaml').read_text(encoding='utf-8'))
def one(p,kind):
    found=[(d,m) for d,m in operations(p) if m['operation_type']==kind];assert len(found)==1,(kind,p);return found[0]
def location(feature):return {'feature_id':feature,'genome_build':'GRCh38','chromosome':'1','start':100,'end':300,'coordinate_system':'1-based-closed','source_id':'synthetic-exact-feature-v1','citation':'Synthetic reference coordinates; no biological evidence.','rationale':'Exact simulated molecular feature, independently of unavailable gene names.'}
def adjust(values,denominator,method='BY'):
    factor=sum(1/i for i in range(1,denominator+1)) if method=='BY' else 1;out={};previous=1
    for rank,(key,p) in reversed(list(enumerate(sorted(values.items(),key=lambda x:(x[1],x[0])),1))):previous=min(previous,p*denominator*factor/rank);out[key]=previous
    return out
def generate(root,mode):
    p=root/mode;p.mkdir();count=105 if mode=='many-pairs' else 4;ids=['g'+str(i) for i in range(1,count+1)];files={'results':'results.tsv','ld':'ld.tsv','alleles':'alleles.tsv'}
    data=[{'feature_id':f,'beta':'.5','se':'.1','p_value':str(.5 if mode=='no-eligible' else (i+1)*1e-6),'N':'1000','patient_note':'private_patient_'+str(i)} for i,f in enumerate(ids)];table(p/'results.tsv',data)
    if mode in ('mapped-reference','ambiguous-reviewed','location-conflict'):
        files['annotation']='annotation.tsv';annotations=[{'feature_id':f,'gene_symbol':'SYNTH'+f,'gene_id':'ENSG'+f,'chromosome':'1','start':'100','end':'300','genome_build':'GRCh38'} for f in ids]
        if mode=='ambiguous-reviewed':annotations.append({**annotations[1],'gene_symbol':'OTHER','gene_id':'OTHER','chromosome':'2'})
        table(p/'annotation.tsv',annotations)
    table(p/'ld.tsv',[{'variant_id':'rs'+str(i),'rs1':int(i==1),'rs2':int(i==2),'rs3':int(i==3)} for i in range(1,4)])
    table(p/'alleles.tsv',[{'variant_id':'rs'+str(i),'effect_allele':'A','other_allele':'G'} for i in range(1,4)])
    pairs=[]
    for i,f in enumerate(ids,1):
        x=[];y=[]
        for snp,beta in enumerate((.1,.2,.3),1):
            by=beta*(.5 if i==1 else .1) if i<3 else (beta*(.05+i*.001) if i>4 else (.0002,-.0001,.0003)[snp-1])
            x.append({'variant_id':'rs'+str(snp),'chromosome':'1','position':snp*100,'effect_allele':'A','other_allele':'G','beta':beta,'se':'.01','p_value':'.5' if i==4 or i==2 and snp>1 else '1e-12'})
            y.append({**x[-1],'effect_allele':'G' if snp==2 else 'A','other_allele':'A' if snp==2 else 'G','beta':-by if snp==2 else by,'se':'.02','p_value':'.4'})
        for role,data in [('x',x),('y',y)]:files[role+str(i)]=role+str(i)+'.tsv';table(p/files[role+str(i)],data)
        def source(role,trait):return {'dataset_id':'a','file_role':role,'source_id':'synthetic-'+role,'citation':'Synthetic source; not scientific evidence.','genome_build':'GRCh38','ancestry':'European','sample_size':10000,'complete_region':True,'trait':trait,'feature_id':f,'tissue':'blood','assay':'sQTL'}
        pair={'id':'pair-'+f,'feature_id':f,'approved':True,'rationale':'Synthetic exact-feature MR software fixture.','sample_overlap':'none','sample_overlap_basis':'Independent simulated summaries.','instrument_validity_rationale':'Synthetic independent instruments; no causal claim.','region':{'chromosome':'1','start':1,'end':500},'exposure':source('x'+str(i),'splicing'),'outcome':source('y'+str(i),'BMI'),'ld':{**source('ld','LD'),'alleles_file_role':'alleles'}}
        if mode!='mapped-reference':pair['feature_location']=location(f)
        if mode=='unannotated-pending' and i==4 or mode=='location-resume' and i==1:pair.pop('feature_location',None)
        if i==1:
            if mode=='location-conflict':pair['feature_location']['start']=101
            if mode=='build-conflict':pair['feature_location']['genome_build']='GRCh37'
            if mode=='wrong-feature':pair['feature_location']['feature_id']='other-feature'
            if mode=='unapproved':pair['approved']=False
        if mode=='no-eligible' or mode=='missing-source' and i==4 or mode=='explicit-cap' and i>2:continue
        pairs.append(pair)
    analysis={'mode':'manual','goals':['mendelian_randomization','impact_assessment'],'multiple_testing':{'method':'BY','alpha':.05},'harmonization':{'effect_outliers':{'enabled':False}},'bioinformatics':{'genetic_pairs':pairs,'mr_testing':{'primary_estimator':'ivw-fixed','adjustment':'BY','alpha':.05}}}
    if mode=='explicit-cap':analysis['bioinformatics']['mr']={'max_features':2,'exposure_ancestry':'European'}
    if mode=='supplied-primary':
        q=adjust({r['feature_id']:float(r['p_value']) for r in rows(p/'results.tsv')},count)
        meta=[{**r,'adjusted_p':q[r['feature_id']]} for r in rows(p/'results.tsv')];table(p/'provided-meta.tsv',meta)
        analysis['supplied_results']=[{'id':'external-primary','kind':'meta_analysis','files':{'results':'provided-meta.tsv'},'scope':{'modality':'transcriptomics','genome_build':'GRCh38','coordinate_system':'1-based-closed','species':'human'},'method':{'name':'Synthetic external meta-analysis','parameters':{'model':'fixed'}},'family':{'id':'external-full-family','coverage':'complete','tested_count':count,'adjustment':'BY','alpha':.05,'adjusted_p_from_full_family':True},'provenance':{'results_sha256':sha(p/'provided-meta.tsv')}}]
    study={'schema_version':1,'project':{'id':'mr-coverage-'+mode,'title':'Synthetic full-coverage MR','description':'Software fixtures only; no biological source appraisal.'},'resources':{'network':'offline','downloads':'deny'},'assistance':{'enabled':False,'impact_max_payload_chars':2000000,'impact_batch_size':1000},'datasets':[{'id':'a','cohort':'synthetic','type':'transcriptomics','stage':'association_results','species':'human','genome_build':'GRCh38','coordinate_system':'1-based-closed','annotation_release':'synthetic-v1','files':files}],'hypotheses':[{'id':'H','description':'Synthetic exact molecular features with BMI.','outcome':'BMI','inputs':{'discovery':['a']},'analysis':analysis}]}
    write(p/'study.json',study);write(p/'initial-study.json',study);write(p/'stable-input-sha256.json',{f.name:sha(f) for f in p.iterdir() if f.is_file()})
def amend_location(p,jar):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text());study=json.loads((p/'study.json').read_text());pairs=study['hypotheses'][0]['analysis']['bioinformatics']['genetic_pairs'];pairs[0]['feature_location']=location('g1')
    amendment=p/'amendment-add-location.yaml';write(amendment,{'schema_version':1,'amendment':{'id':'add-location','title':'Add the fixed synthetic exact-feature location','base_study_hash':state['resolved_study_hash'],'proposed_by':{'type':'external','id':'synthetic-mr-coverage'},'source':{'type':'synthetic-acceptance','id':'reviewed-location-resume'},'rationale':'Synthetic software contract amendment only. Preserve source inputs and the base study; no human scientific approval is claimed.'},'changes':{'update':{'hypotheses':[{'id':'H','analysis':{'bioinformatics':{'genetic_pairs':pairs}}}]}}})
    helper=p/'amendment-driver';helper.mkdir();driver=helper/'MrFeatureLocationAmendment.java';driver.write_text('import java.nio.file.Path; import org.jlinalg.study.*; public final class MrFeatureLocationAmendment { private MrFeatureLocationAmendment() {} public static void main(String[] args) throws Exception { StudyAmendments.apply(Path.of(args[0]),Path.of(args[1]),new StudyProject.Actor("external","synthetic-mr-coverage",null)); } }',encoding='utf-8')
    compiled=subprocess.run(['javac','-Xlint:all','-Werror','-cp',str(jar),'-d',str(helper),str(driver)],capture_output=True,text=True);assert compiled.returncode==0,compiled.stderr
    cli(jar,'amend',amendment,'--project',p,'--check');applied=subprocess.run(['java','-cp',str(jar)+os.pathsep+str(helper),'MrFeatureLocationAmendment',str(amendment),str(p)],capture_output=True,text=True);assert applied.returncode==0,applied.stdout+applied.stderr
    write(p/'amendment-software.json',{'actor_type':'external','human_attestation':False,'source_sha256':sha(driver),'class_sha256':sha(helper/'MrFeatureLocationAmendment.class'),'amendment_sha256':sha(p/'amendments/amendment-add-location.yaml')})
def history(p):
    d,m=one(p,'mendelian_randomization');return {'run_id':yaml.safe_load((p/'state/project-status.yaml').read_text())['run_id'],'mr_state':m['state'],'family_status':m['details']['mr_testing']['status'],'mr_reused':m.get('reused_from_cache',False),'manifest_sha256':sha(d/'manifest.yaml')}
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];seals={};cases=[];checks=0;payloads=[]
    for mode in software.get('cases',MODES):
        p=root/mode;study=json.loads((p/'study.json').read_text());config=study['hypotheses'][0]['analysis'];count=105 if mode=='many-pairs' else 4;selected=[] if mode=='no-eligible' else ['g1','g2'] if mode=='explicit-cap' else ['g'+str(i) for i in range(1,count+1)]
        for name,digest in json.loads((p/'stable-input-sha256.json').read_text()).items():assert sha(p/name)==digest
        for folder,m in operations(p):
            for name,digest in m.get('output_sha256',{}).items():assert sha(folder/name)==digest;seals[str((folder/name).relative_to(root))]=digest
        folder,m=one(p,'mendelian_randomization');assert m['state']==('waiting_for_input' if mode in WAIT else 'complete'),(mode,m['state'],m['details'].get('needs'));policy=m['details']['mr_testing'];assert policy['eligible_features']==(0 if mode=='no-eligible' else count);assert policy['family_size']==len(selected);assert policy['eligibility_before_annotation'] is True
        results={r['feature_id']:r for r in rows(folder/'mr-primary-results.tsv')};assert set(results)==set(selected);expected={};pending={}
        for feature in selected:
            i=int(feature[1:]);missing=mode=='missing-source' and i==4 or mode=='unannotated-pending' and i==4 or mode in ('location-conflict','build-conflict','wrong-feature','unapproved') and i==1
            if missing:pending[feature]=True;continue
            x=rows(p/('x'+str(i)+'.tsv'));y={r['variant_id']:r for r in rows(p/('y'+str(i)+'.tsv'))};aligned=[]
            for r in x:
                beta=float(r['beta'])
                if float(r['p_value'])>5e-8 or (beta/float(r['se']))**2<10:continue
                outcome=y[r['variant_id']];sign=1 if r['effect_allele']==outcome['effect_allele'] else -1;aligned.append((beta,sign*float(outcome['beta']),float(outcome['se'])))
            if not aligned:continue
            precision=sum(b*b/(s*s) for b,y,s in aligned);beta=sum(b*y/(s*s) for b,y,s in aligned)/precision;se=math.sqrt(1/precision);probability=math.erfc(abs(beta/se)/math.sqrt(2));expected[feature]=probability
            for field,value in [('estimate',beta),('standard_error',se),('causal_p_value',probability)]:assert math.isclose(float(results[feature][field]),value,rel_tol=1e-9,abs_tol=1e-13),(mode,feature,field);checks+=1
        q=adjust(expected,len(selected))
        for feature,row in results.items():
            assert int(row['family_size'])==len(selected)
            if feature in q:assert math.isclose(float(row['adjusted_p']),q[feature],rel_tol=1e-9,abs_tol=1e-13);assert row['significant_primary']==str(not pending and q[feature]<.05).lower();checks+=1
            else:assert row['causal_p_value']==row['adjusted_p']=='NA';assert row['significant_primary']=='false'
        assert policy['unresolved_tests']==len(pending) and policy['estimable_primary_tests']==len(expected)
        dispositions=rows(folder/'mr-feature-dispositions.tsv');assert len(dispositions)==(0 if mode=='no-eligible' else count)
        association={r['feature_id']:float(r['p_value']) for r in rows(p/'results.tsv')};association_q=adjust(association,count)
        selection=yaml.safe_load((folder/'primary-result-selection.yaml').read_text())['selections']['H:official'];assert sha(p/selection['source_artifact'])==selection['source_sha256']
        for row in dispositions:
            assert row['association_testing_denominator']==str(count) and row['association_source_sha256']==selection['source_sha256'] and row['association_adjustment']=='BY'
            assert math.isclose(float(row['association_p_value']),association[row['feature_id']],rel_tol=1e-11)
            assert math.isclose(float(row['association_adjusted_p']),association_q[row['feature_id']],rel_tol=1e-11)
            checks+=2
        for feature,row in results.items():
            if feature in pending:assert row['disposition']=='unresolved_primary_test'
        records=m['details']['pairs'];location_records=[r for r in records if r.get('measured_feature_location',{}).get('origin')=='reviewed_pair_feature_location']
        assert all(r['measured_feature_location']['gene_annotation_inferred'] is False and r['primary_feature_selection']['gene_id']=='' for r in location_records)
        if mode=='many-pairs':assert len(config['bioinformatics']['genetic_pairs'])==105 and len(results)==105
        if mode=='location-resume':
            a,b,c=json.loads((p/'history.json').read_text());assert a['mr_state']=='waiting_for_input' and b['mr_state']=='complete' and b['mr_reused'] is False and c['mr_reused'] is True;assert sha(p/'study.json')==sha(p/'initial-study.json');amendment=json.loads((p/'amendment-software.json').read_text());assert amendment['actor_type']=='external' and amendment['human_attestation'] is False and sha(p/'amendment-driver/MrFeatureLocationAmendment.java')==amendment['source_sha256'] and sha(p/'amendments/amendment-add-location.yaml')==amendment['amendment_sha256']
        inventory,im=one(p,'finding_inventory');assert im['state']=='complete';findings=[json.loads(line) for line in (inventory/'finding-inventory.jsonl').read_text().splitlines()];assert sum(f['candidate'] for f in findings)==(0 if mode=='no-eligible' else count)
        for f in findings:
            coverage=[o for o in f['observations'] if o.get('kind')=='mr_feature_coverage_context']
            if f['candidate']:assert len(coverage)==1
            locations=[o for o in f['observations'] if o.get('kind')=='mr_feature_location_context' and o.get('origin')=='reviewed_pair_feature_location']
            if locations:assert any(c['kind']=='mr_feature_identity' for c in f['caveats'])
        impact,am=one(p,'impact_assessment')
        for path in impact.rglob('evidence.yaml'):
            payload=yaml.safe_load(path.read_text());assert 'private_patient_' not in json.dumps(payload);payloads.append({'case':mode,'findings':len(payload['findings']),'sha256':sha(path)})
        for path in impact.rglob('metadata.yaml'):assert yaml.safe_load(path.read_text())['request_sent'] is False and yaml.safe_load(path.read_text())['request_attempted'] is False
        if mode not in WAIT:assert m.get('reused_from_cache') is True
        if mode=='supplied-primary':meta,mm=one(p,'meta_analysis');assert mm['details']['analysis_executed_by_jlinalg'] is False;assert sha(p/'provided-meta.tsv')==config['supplied_results'][0]['provenance']['results_sha256']
        cli(jar,'verify','--project',p);cases.append({'case':mode,'mr_state':m['state'],'family_size':len(selected),'estimable':len(expected),'unresolved':len(pending),'location_records':len(location_records)})
    report={'passed':True,'cases':cases,'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'independent_estimate_p_and_adjustment_checks':checks,'sealed_outputs':len(seals),'actual_unsent_impact_payloads':payloads,'eligibility_precedes_annotation':True,'source_primary_FDR_preserved':True,'reviewed_exact_feature_locations_without_inferred_gene_names':True,'more_than_100_reviewed_pairs_executed':any(c['case']=='many-pairs' for c in cases),'scientific_appraisal_executed':False,'model_requests_attempted':False,'full_pipeline_conformance':False}
    for name,data in [('verification.json',report),('sealed-output-sha256.json',seals)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==data
        else:write(root/name,data)
    print(json.dumps(report))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=pathlib.Path);ap.add_argument('--jar',type=pathlib.Path,required=True);ap.add_argument('--cases',nargs='+',choices=MODES);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name;shutil.copyfile(pathlib.Path(__file__),software/'mr-feature-coverage-workflow.py');modes=a.cases or MODES;write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'verifier_sha256':sha(software/'mr-feature-coverage-workflow.py'),'pinned_before_execution':True,'cases':modes})
        for mode in modes:
            generate(root,mode);p=root/mode;cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-mr-feature-coverage');stages=[history(p)]
            if mode=='location-resume':amend_location(p,jar)
            cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-mr-feature-coverage');stages.append(history(p));cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-mr-feature-coverage');stages.append(history(p));write(p/'history.json',stages)
    verify(root,jar)
