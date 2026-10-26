"""Complete missing MR features while retaining explicitly supplied external tests.

Synthetic source approvals exercise software only. No real AI, source appraisal
or human attestation is performed. Executable, generator and verifier are pinned
before execution. Default run covers all declared cases.
"""
import argparse,csv,hashlib,importlib.util,json,math,os,pathlib,shutil,subprocess
import yaml

MODES=['mixed','all-provided','no-raw-all-provided','missing-native-source','wrong-hash','incomplete-scope','conflicting-overlap','identical-alias','disjoint-sources','wrong-outcome','wrong-estimator','unknown-feature','association-filter','cap','provided-primary','amendment-resume','unestimable-source']
WAIT={'missing-native-source','wrong-hash','incomplete-scope','conflicting-overlap','wrong-outcome','wrong-estimator','unknown-feature'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def rows(p):
    with p.open(encoding='utf-8',newline='') as h:return list(csv.DictReader(h,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as h:w=csv.DictWriter(h,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def cli(jar,*args):
    result=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8')
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
def operations(p):
    state=yaml.safe_load((p/'state/project-status.yaml').read_text())
    for folder in (p/'runs'/state['run_id']/'operations').iterdir():
        if (folder/'manifest.yaml').exists():yield folder,yaml.safe_load((folder/'manifest.yaml').read_text())
def one(p,kind):
    found=[(d,m) for d,m in operations(p) if m['operation_type']==kind];assert len(found)==1,(p,kind);return found[0]
def adjusted(values,n):
    harmonic=sum(1/i for i in range(1,n+1));out={};previous=1
    for rank,(f,p) in reversed(list(enumerate(sorted(values.items(),key=lambda x:(x[1],x[0])),1))):previous=min(previous,p*n*harmonic/rank);out[f]=previous
    return out
def external_rows(all_features=False):
    result=[]
    for i,(beta,se,count) in enumerate([(.7,.2,3),(.2,.3,1),(.1,.1,3),(None,None,0)],1):
        if i>2 and not all_features:break
        result.append({'feature_id':'g'+str(i),'beta':'NA' if beta is None else beta,'se':'NA' if se is None else se,'p_value':'NA' if beta is None else math.erfc(abs(beta/se)/math.sqrt(2)),'adjusted_p':'NA' if beta is None else 1.,'method':'wald-ratio' if count==1 else 'ivw-fixed','n_instruments':count,'status':'not_estimable' if beta is None else 'complete','reason':'No usable instruments in the completed synthetic source' if beta is None else '', 'patient_note':'PRIVATE_PATIENT_SENTINEL'})
    return result
def supplied(p,id,file,features,analysis_id):return {'id':id,'kind':'mendelian_randomization','role':'contribution','files':{'results':file},'scope':{'feature_ids':features,'exposure':'isoform ratio','outcome':'BMI','ancestry':'European','genome_build':'GRCh38','tissue':'blood','assay':'irQTL'},'method':{'name':'Synthetic completed external MR','parameters':{'primary_estimator':'ivw-fixed','reuse_rationale':'Exact features were registered from association selection, independently of their MR P values.'}},'family':{'id':'external-500-tests','coverage':'selected_only','tested_count':500,'adjustment':'BY','adjusted_p_from_full_family':True},'provenance':{'analysis_id':analysis_id,'results_sha256':sha(p/file)},'limitations':['Synthetic source only; no biological or scientific validation.']}
def generate(root,mode,base):
    base.generate(root,mode);p=root/mode;study=json.loads((p/'study.json').read_text());h=study['hypotheses'][0];analysis=h['analysis'];bio=analysis['bioinformatics'];all_done=mode in ('all-provided','no-raw-all-provided');external=external_rows(all_done)
    if mode=='incomplete-scope':external=external[:1]
    if mode=='unknown-feature':external[1]['feature_id']='ghost'
    if mode=='wrong-estimator':
        for r in external:
            if r['n_instruments']!=1:r['method']='ivw-random'
    if mode=='unestimable-source':external=[{**r,'beta':'NA','se':'NA','p_value':'NA','adjusted_p':'NA','method':'ivw-fixed','n_instruments':0,'status':'not_estimable','reason':'Completed external source has no usable instruments'} for r in external]
    table(p/'external-mr.tsv',external);features=['g1','g2','g3','g4'] if all_done else ['g1','ghost'] if mode=='unknown-feature' else ['g1','g2'];provided=[supplied(p,'completed','external-mr.tsv',features,'analysis-external-v1')]
    if mode=='wrong-hash':provided[0]['provenance']['results_sha256']='0'*64
    if mode=='unestimable-source':provided[0]['reported_status']='not_estimable'
    if mode=='wrong-outcome':provided[0]['scope']['outcome']='another phenotype'
    if mode=='wrong-estimator':provided[0]['method']['parameters']['primary_estimator']='ivw-random'
    if mode=='identical-alias':provided.append({**provided[0],'id':'same-analysis-copy'})
    if mode=='conflicting-overlap':
        other=[{**external_rows()[0],'beta':.8,'p_value':math.erfc(4/math.sqrt(2))}];table(p/'other-mr.tsv',other);provided.append(supplied(p,'conflicting-primary','other-mr.tsv',['g1'],'different-analysis-v1'))
    if mode=='disjoint-sources':
        table(p/'external-mr.tsv',external[:1]);table(p/'second-mr.tsv',external[1:]);provided=[supplied(p,'first','external-mr.tsv',['g1'],'analysis-A'),supplied(p,'second','second-mr.tsv',['g2'],'analysis-B')]
    files=study['datasets'][0]['files']
    for feature in features:
        if feature.startswith('g'):
            for role in ['x'+feature[1:],'y'+feature[1:]]:files.pop(role,None)
    if mode in ('missing-native-source','amendment-resume'):
        bio['genetic_pairs'][2]['outcome']['file_role']='unavailable-outcome'
    if mode=='association-filter':
        data=rows(p/'results.tsv');data[0]['p_value']='.9';table(p/'results.tsv',data)
    if mode=='cap':bio['mr']={'max_features':1,'exposure_ancestry':'European'};bio['genetic_pairs']=bio['genetic_pairs'][:2]
    if mode in ('provided-primary','no-raw-all-provided'):
        data=rows(p/'results.tsv');q=adjusted({r['feature_id']:float(r['p_value']) for r in data},4);table(p/'provided-meta.tsv',[{**r,'adjusted_p':q[r['feature_id']]} for r in data]);provided.insert(0,{'id':'primary-meta','kind':'meta_analysis','files':{'results':'provided-meta.tsv'},'scope':{'modality':'transcriptomics','genome_build':'GRCh38','coordinate_system':'1-based-closed','species':'human'},'method':{'name':'Synthetic supplied primary','parameters':{'model':'fixed'}},'family':{'id':'association-4-tests','coverage':'complete','tested_count':4,'adjustment':'BY','alpha':.05,'adjusted_p_from_full_family':True},'provenance':{'results_sha256':sha(p/'provided-meta.tsv')}})
    if mode=='no-raw-all-provided':study['datasets']=[];h['inputs']={}
    analysis['supplied_results']=provided;study['project']['id']='mr-completion-'+mode;write(p/'study.json',study);write(p/'initial-study.json',study);write(p/'stable-input-sha256.json',{f.name:sha(f) for f in p.iterdir() if f.is_file() and f.name!='stable-input-sha256.json'})
def stage(p):
    d,m=one(p,'mendelian_randomization');imports=[{'id':x['details']['artifact_id'],'canonical_sha256':x['details'].get('canonical_sha256'),'reused':x.get('reused_from_cache',False)} for _,x in operations(p) if x['operation_type']=='supplied_contribution'];return {'mr_state':m['state'],'reused':m.get('reused_from_cache',False),'native_pairs_processed':m['details']['native_pairs_processed'],'imports':imports}
def amend(p,jar):
    study=json.loads((p/'study.json').read_text());pairs=study['hypotheses'][0]['analysis']['bioinformatics']['genetic_pairs'];pairs[2]['outcome']['file_role']='y3';state=yaml.safe_load((p/'state/project-status.yaml').read_text());a=p/'amendment-complete-source.yaml';write(a,{'schema_version':1,'amendment':{'id':'complete-source','title':'Complete the fixed synthetic missing source declaration','base_study_hash':state['resolved_study_hash'],'proposed_by':{'type':'external','id':'synthetic-mr-completion'},'source':{'type':'synthetic-acceptance','id':'MR-completion-resume'},'rationale':'Synthetic source amendment only; no human attestation. Base and external analyses remain immutable.'},'changes':{'update':{'hypotheses':[{'id':'H','analysis':{'bioinformatics':{'genetic_pairs':pairs}}}]}}})
    helper=p/'amendment-driver';helper.mkdir();driver=helper/'MrCompletionAmendment.java';driver.write_text('import java.nio.file.Path; import org.jlinalg.study.*; public final class MrCompletionAmendment { private MrCompletionAmendment() {} public static void main(String[] args) throws Exception { StudyAmendments.apply(Path.of(args[0]),Path.of(args[1]),new StudyProject.Actor("external","synthetic-mr-completion",null)); } }',encoding='utf-8');compiled=subprocess.run(['javac','-Xlint:all','-Werror','-cp',str(jar),'-d',str(helper),str(driver)],capture_output=True,text=True);assert compiled.returncode==0,compiled.stderr;source_sha=sha(driver);class_sha=sha(driver.with_suffix('.class'));cli(jar,'amend',a,'--project',p,'--check');run=subprocess.run(['java','-cp',str(jar)+os.pathsep+str(helper),'MrCompletionAmendment',str(a),str(p)],capture_output=True,text=True);assert run.returncode==0,run.stdout+run.stderr;assert source_sha==sha(driver) and class_sha==sha(driver.with_suffix('.class'));write(p/'amendment-software.json',{'source_sha256':source_sha,'class_sha256':class_sha,'actor_type':'external','human_attestation':False})
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];seals={};cases=[];checks=0;payloads=[]
    for mode in software['cases']:
        p=root/mode;study=yaml.safe_load((p/'resolved-study.yaml').read_text());analysis=study['hypotheses'][0]['analysis'];defs=[d for d in analysis['supplied_results'] if d.get('role')=='contribution'];declared={f for d in defs for f in d['scope']['feature_ids']};all_done=mode in ('all-provided','no-raw-all-provided');selected=['g1'] if mode=='cap' else ['g2','g3','g4'] if mode=='association-filter' else ['g1','g2','g3','g4']
        for name,digest in json.loads((p/'stable-input-sha256.json').read_text()).items():assert sha(p/name)==digest
        for folder,m in operations(p):
            for name,digest in m.get('output_sha256',{}).items():assert sha(folder/name)==digest;seals[str((folder/name).relative_to(root))]=digest
        folder,m=one(p,'mendelian_randomization');assert m['state']==('waiting_for_input' if mode in WAIT else 'complete'),(mode,m['state']);family=m['details']['mr_testing'];result={r['feature_id']:r for r in rows(folder/'mr-primary-results.tsv')};assert set(result)==set(selected);assert family['family_size']==len(selected)
        bad_supplied={'g1','g2'} if mode in ('wrong-hash','incomplete-scope','wrong-outcome','wrong-estimator') else {'g1'} if mode in ('conflicting-overlap','unknown-feature') else set();pending=bad_supplied&set(selected)
        if mode=='missing-native-source':pending.add('g3')
        expected={};external_expected={r['feature_id']:r for r in external_rows(all_done)}
        if mode=='unestimable-source':external_expected={f:{**r,'status':'not_estimable'} for f,r in external_expected.items()}
        for f in selected:
            row=result[f]
            if f in pending:assert row['causal_p_value']==row['adjusted_p']=='NA';continue
            if f in declared:
                supplied=external_expected[f]
                if supplied['status']=='not_estimable':assert row['causal_p_value']=='NA';continue
                beta,se,probability=[float(supplied[k]) for k in ['beta','se','p_value']]
            else:
                i=f[1:];x=rows(p/('x'+i+'.tsv'));y={r['variant_id']:r for r in rows(p/('y'+i+'.tsv'))};aligned=[]
                for r in x:
                    bx=float(r['beta'])
                    if float(r['p_value'])>5e-8 or (bx/float(r['se']))**2<10:continue
                    v=y[r['variant_id']];aligned.append((bx,float(v['beta'])*(1 if r['effect_allele']==v['effect_allele'] else -1),float(v['se'])))
                if not aligned:assert row['causal_p_value']=='NA';continue
                precision=sum(b*b/(s*s) for b,y,s in aligned);beta=sum(b*y/(s*s) for b,y,s in aligned)/precision;se=math.sqrt(1/precision);probability=math.erfc(abs(beta/se)/math.sqrt(2))
            for field,value in [('estimate',beta),('standard_error',se),('causal_p_value',probability)]:assert math.isclose(float(row[field]),value,rel_tol=1e-10,abs_tol=1e-13),(mode,f,field,row[field],value);checks+=1
            expected[f]=probability
        q=adjusted(expected,len(selected))
        for f,row in result.items():
            if f in q:assert math.isclose(float(row['adjusted_p']),q[f],rel_tol=1e-10,abs_tol=1e-13);checks+=1
            assert row['significant_primary']==str(f in q and not pending and q[f]<.05).lower()
        assert family['unresolved_tests']==len(pending)
        for f in declared:
            assert not (folder/('pair-'+f)).exists(),(mode,f,'covered feature was refitted')
        assert m['details']['native_pairs_processed']==(0 if all_done or mode=='cap' else 3 if mode=='unknown-feature' else 2)
        imports=[(d,x) for d,x in operations(p) if x['operation_type']=='supplied_contribution'];assert len(imports)==len(defs)
        for d,x in imports:
            assert x['details']['analysis_executed_by_jlinalg'] is False and x['details']['primary_selection_eligible'] is False
            if x['details'].get('contribution_reuse_eligible'):
                canonical=rows(d/x['details']['canonical_results']);source=next(a for a in defs if a['id']==x['details']['artifact_id']);original={r['feature_id']:r for r in rows(p/source['files']['results'])}
                for row in canonical:
                    for field in ['beta','se','p_value','adjusted_p']:assert row[field]==original[row['feature_id']][field]
                assert 'PRIVATE_PATIENT_SENTINEL' not in (d/x['details']['canonical_results']).read_text()
        inventory,im=one(p,'finding_inventory');assert im['state']=='complete';findings=[json.loads(line) for line in (inventory/'finding-inventory.jsonl').read_text().splitlines()];impact,am=one(p,'impact_assessment');seen=[]
        for path in impact.rglob('evidence.yaml'):
            payload=yaml.safe_load(path.read_text());encoded=json.dumps(payload);assert 'PRIVATE_PATIENT_SENTINEL' not in encoded and str(root) not in encoded;seen.extend(f['finding_id'] for f in payload['findings']);payloads.append({'case':mode,'findings':len(payload['findings']),'sha256':sha(path)})
            for f in payload['findings']:
                if any(o.get('reused_supplied_result') is True for o in f['observations']):assert any(c['kind']=='supplied_mr' for c in f['caveats'])
        assert set(seen)=={f['finding_id'] for f in findings if f['candidate']}
        for path in impact.rglob('metadata.yaml'):audit=yaml.safe_load(path.read_text());assert audit['request_attempted'] is False and audit['request_sent'] is False
        history=json.loads((p/'history.json').read_text())
        if mode=='amendment-resume':assert history[0]['mr_state']=='waiting_for_input' and history[1]['mr_state']=='complete' and history[2]['reused'] is True and sha(p/'study.json')==sha(p/'initial-study.json')
        if mode not in WAIT:assert m.get('reused_from_cache') is True
        cli(jar,'verify','--project',p);cases.append({'case':mode,'state':m['state'],'joint_family_size':len(selected),'joint_estimable_tests':len(expected),'unresolved':len(pending),'native_pairs_processed':m['details']['native_pairs_processed'],'contribution_imports':len(imports)})
    report={'passed':True,'cases':cases,'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],'generator_sha256':software['generator_sha256'],'independent_estimate_p_and_joint_by_checks':checks,'sealed_outputs':len(seals),'actual_unsent_impact_payloads':payloads,'provided_features_not_refitted':True,'original_source_adjustments_preserved':True,'joint_mr_family_recomputed_separately':True,'invalid_coverage_never_replaced_by_native_fallback':True,'scientific_appraisal_executed':False,'model_requests_attempted':False,'full_pipeline_conformance':False}
    for name,data in [('verification.json',report),('sealed-output-sha256.json',seals)]:
        if (root/name).exists():assert json.loads((root/name).read_text())==data
        else:write(root/name,data)
    print(json.dumps(report))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=pathlib.Path);ap.add_argument('--jar',type=pathlib.Path,required=True);ap.add_argument('--cases',nargs='+',choices=MODES);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name;shutil.copyfile(pathlib.Path(__file__),software/'mr-completion-workflow.py');shutil.copyfile(pathlib.Path(__file__).with_name('mr-feature-coverage-workflow.py'),software/'mr-feature-coverage-workflow.py');modes=a.cases or MODES;write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'verifier_sha256':sha(software/'mr-completion-workflow.py'),'generator_sha256':sha(software/'mr-feature-coverage-workflow.py'),'cases':modes,'pinned_before_execution':True})
        spec=importlib.util.spec_from_file_location('mr_coverage_fixture',software/'mr-feature-coverage-workflow.py');base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
        for mode in modes:
            generate(root,mode,base);p=root/mode;cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-MR-completion');history=[stage(p)]
            if mode=='amendment-resume':amend(p,jar)
            cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-MR-completion');history.append(stage(p));cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-MR-completion');history.append(stage(p));write(p/'history.json',history)
    verify(root,jar)
