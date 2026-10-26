"""Pinned offline acceptance for supplied sample QC/PCA and missing native PCA.

Run: python -B external-genotype-qc-workflow.py run NEW_DIR --jar EXECUTABLE_JAR
Verify: python -B external-genotype-qc-workflow.py verify DIR --jar PINNED_JAR
Synthetic NumPy source reports are not actual PLINK or cohort-QC evidence.
Requires NumPy, SciPy, PyYAML. All impact payloads remain unsent.
"""
import argparse,copy,csv,hashlib,importlib.util,json,math,os,re,shutil,subprocess,sys
from pathlib import Path
import numpy as np
import scipy
from scipy.stats import t
import yaml

MODES=['supplied-complete','supplied-imputed','supplied-qc-only','phenotype-subset',
       'diagnostic-pcs','qc-without-pca','native-missing-pca','native-subset-basis',
       'partial-pca','partial-qc','wrong-matrix','wrong-data-hash','wrong-sample-coverage',
       'duplicate-pc','missing-pc','imputed-missing-info','qc-only-imputed-no-info',
       'import-bound-resume','changed-source-edition','mixed-cohorts',
       'completed-pcs-no-basis','native-missing-basis','qc-only-no-basis']
HELD={'partial-pca','partial-qc','wrong-matrix','wrong-data-hash','wrong-sample-coverage',
      'duplicate-pc','missing-pc','imputed-missing-info','native-missing-basis'}
CHECKS=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8')
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(p,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
def close(actual,expected,label,probability=False):
    actual=float(actual);expected=float(expected);tol=5e-7*(abs(expected) if probability else max(1,abs(expected)))
    assert math.isfinite(actual) and abs(actual-expected)<=tol,(label,actual,expected)
    if probability and expected>0:assert abs(math.log(actual)-math.log(expected))<5e-7,(label,actual,expected)
    CHECKS.append({'label':label,'actual':actual,'expected':expected,'relative_probability_check':probability})
def module(root):
    p=root/'software/genotype-sample-qc-workflow.py';spec=importlib.util.spec_from_file_location('pinned_native_sample_qc',p);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def strings(value):
    if isinstance(value,str):yield value
    elif isinstance(value,dict):
        for key,item in value.items():yield from strings(key);yield from strings(item)
    elif isinstance(value,list):
        for item in value:yield from strings(item)
def contract(project,phase):
    doc=load(project/'study.json');a=doc['hypotheses'][0]['analysis']
    if project.name=='import-bound-resume' and phase>0:a['genotype_sample_qc']['maximum_import_cells']=50000000
    if project.name=='changed-source-edition' and phase>0:doc['datasets'][0]['files'].update(sample_qc_report='external-qc-v2.yaml',sample_qc_pcs='external-pcs-v2.tsv')
    return doc

def generate(root,mode,base):
    imputed=mode in {'supplied-imputed','imputed-missing-info','qc-only-imputed-no-info'}
    initial=base.generate(root,'imputed-panel' if imputed else 'native');project=root/mode
    assert initial.parent==root and project.parent==root and not project.exists();initial.rename(project)
    study=load(project/'study.json');d=study['datasets'][0];a=study['hypotheses'][0]['analysis'];p=a['genotype_sample_qc']
    p.update(source='supplied',pca_source='supplied')
    if mode in {'supplied-complete','supplied-imputed'}:p.update(maximum_samples=3,maximum_pca_work=1)
    if mode in {'supplied-qc-only','qc-only-imputed-no-info','qc-only-no-basis'}:a['goals']=['impact_assessment']
    if mode=='diagnostic-pcs':p['include_pcs']=False
    if mode in {'qc-without-pca','qc-only-no-basis'}:p.update(include_pcs=False,pca_components=0)
    if mode in {'native-missing-pca','native-subset-basis','native-missing-basis'}:p.update(pca_source='native',maximum_pair_word_work=1)
    if mode=='import-bound-resume':p['maximum_import_cells']=1
    if mode=='phenotype-subset':
        pheno=rows(project/'phenotype.tsv');pheno[-1]['y']='NA';table(project/'phenotype.tsv',pheno)
    if mode in {'imputed-missing-info','qc-only-imputed-no-info'}:
        path=project/'main.vcf';path.write_text(path.read_text(encoding='utf-8').replace('R2=0.99','.'),encoding='utf-8')
    gold=rows(root/'software/expected-pcs.tsv');raw=rows(root/'software/hard-calls.tsv');samples=[];pcs=[]
    for row in gold:
        id_value=row['sample_id'];keep=row['pca_basis']=='true';basis=keep and not (mode=='native-subset-basis' and id_value=='S04')
        samples.append({'sample_id':id_value,'status':'complete','decision':'keep' if keep else 'exclude','reason':'' if keep else 'relatedness','observed_call_rate':sum(r[id_value]!='NA' for r in raw)/len(raw),'pca_basis':str(basis).lower()})
        pcs.append({'sample_id':id_value,'PC1':row['GPC1'],'PC2':row['GPC2']})
    if mode in {'completed-pcs-no-basis','native-missing-basis','qc-only-no-basis'}:
        for row in samples:row.pop('pca_basis')
    table(project/'external-samples.tsv',list(reversed(samples)));table(project/'external-pcs.tsv',list(reversed(pcs)))
    report={'schema_version':1,'analysis_id':'PRIVATE_EXTERNAL_METHOD_SENTINEL','dataset_id':'a',
            'analysis_matrix_sha256':sha(project/'main.vcf'),'genome_build':'GRCh38','status':'complete','sample_universe':'complete',
            'sample_qc':{'status':'complete','software':'synthetic-NumPy-source','software_version':np.__version__,'call_rate_scope':'observed_autosomal_hard_calls','minimum_call_rate':.98,'samples':64},
            'relatedness':{'status':'complete','software':'NumPy-reference','software_version':np.__version__,'estimator':'KING_between_family','support':'Synthetic complete source-pair reference, not actual cohort or installed PLINK execution.','kinship_threshold':2**-3.5,'action':'exclude','minimum_shared_markers':2000,'minimum_pair_heterozygotes':100,'evaluated_samples':64,'evaluated_pairs':2016,'related_pairs':4,'unresolved_pairs':0,'independent_basis_complete':True,'retained_population_compatible':True},
            'pca':{'status':'complete','software':'NumPy','software_version':np.__version__,'basis':'Original synthetic 62-member unrelated basis; fixed source scores.','basis_samples':62,'columns':['PC1','PC2']},
            'file_sha256':{'sample_qc_samples':sha(project/'external-samples.tsv'),'sample_qc_pcs':sha(project/'external-pcs.tsv')},
            'limitations':['PRIVATE_SOURCE_LIMITATION_SENTINEL: synthetic inputs, no scientific appraisal.'],
            'provenance':{'private_notes':'PRIVATE_SOURCE_PROVENANCE_SENTINEL; participant S00 is synthetic.'}}
    if mode in {'partial-pca','native-missing-pca','native-subset-basis','native-missing-basis'}:report['status']='partial';report['pca']['status']='partial'
    if mode in {'completed-pcs-no-basis','native-missing-basis','qc-only-no-basis'}:report['relatedness']['independent_basis_complete']=False
    if mode=='partial-qc':report['status']='partial';report['sample_qc']['status']='partial'
    if mode=='wrong-matrix':report['analysis_matrix_sha256']='0'*64
    if mode=='wrong-data-hash':report['file_sha256']['sample_qc_samples']='0'*64
    if mode=='wrong-sample-coverage':table(project/'external-samples.tsv',samples[:-1]);report['file_sha256']['sample_qc_samples']=sha(project/'external-samples.tsv')
    if mode in {'duplicate-pc','missing-pc'}:
        changed=pcs+[pcs[0]] if mode=='duplicate-pc' else pcs[:-1];table(project/'external-pcs.tsv',changed);report['file_sha256']['sample_qc_pcs']=sha(project/'external-pcs.tsv')
    write(project/'external-qc.yaml',report);d['files'].update(sample_qc_report='external-qc.yaml',sample_qc_samples='external-samples.tsv',sample_qc_pcs='external-pcs.tsv')
    if mode=='mixed-cohorts':
        second=copy.deepcopy(d);second['id']='b';second['files']={k:v for k,v in second['files'].items() if not k.startswith('sample_qc_')};study['datasets'].append(second);study['hypotheses'][0]['inputs']['discovery'].append('b')
        p.update(source='native',pca_source='native',dataset_sources={'a':{'source':'supplied'}})
        a['primary_result']={'kind':'association','dataset_id':'a','rationale':'Prespecified synthetic QC source-routing control; no pooling or replication claim.'}
    write(project/'study.json',study);write(project/'input-provenance.json',{p.name:sha(p) for p in project.iterdir() if p.is_file() and p.name!='input-provenance.json'})
    return project

def amend(project,jar,mode,base):
    state=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    if mode=='import-bound-resume':changes={'update':{'hypotheses':[{'id':'H','analysis':{'genotype_sample_qc':{'maximum_import_cells':50000000}}}]}}
    else:
        original=load(project/'external-qc.yaml');data=rows(project/'external-pcs.tsv')
        for row in data:row['PC1']=str(float(row['PC1'])+.125)
        table(project/'external-pcs-v2.tsv',data);report=copy.deepcopy(original);report['analysis_id']='PRIVATE_EXTERNAL_METHOD_EDITION_2';report['file_sha256']['sample_qc_pcs']=sha(project/'external-pcs-v2.tsv');report['provenance']['previous_report_sha256']=sha(project/'external-qc.yaml');report['pca']['parameters']={'postprocessing':'Synthetic source PC1 coordinate translation; verify faithful reuse and source invalidation.'};write(project/'external-qc-v2.yaml',report)
        write(project/'new-source-provenance.json',{p:sha(project/p) for p in ['external-pcs-v2.tsv','external-qc-v2.yaml']})
        files=copy.deepcopy(load(project/'study.json')['datasets'][0]['files']);files.update(sample_qc_report='external-qc-v2.yaml',sample_qc_pcs='external-pcs-v2.tsv');changes={'update':{'datasets':[{'id':'a','files':files}]}}
    request=project/'amendment-complete-external-qc.yaml';write(request,{'schema_version':1,'amendment':{'id':'complete-external-qc','title':'Synthetic explicit external QC completion/source edition','base_study_hash':state['resolved_study_hash'],'proposed_by':{'type':'external','id':'synthetic-external-qc'},'source':{'type':'synthetic-acceptance','id':mode},'rationale':'Complete missing work or bind a new immutable source edition; preserve all base/raw inputs. No human attestation.'},'changes':changes})
    helper=project/'amendment-driver';helper.mkdir();source=helper/'ExternalQcAmendment.java';source.write_text('import java.nio.file.Path; import org.jlinalg.study.*; public final class ExternalQcAmendment { private ExternalQcAmendment() {} public static void main(String[] a) throws Exception { StudyAmendments.apply(Path.of(a[0]),Path.of(a[1]),new StudyProject.Actor("external","synthetic-external-qc",null)); } }',encoding='utf-8')
    result=subprocess.run(['javac','-Xlint:all','-Werror','-cp',str(jar),'-d',str(helper),str(source)],capture_output=True,text=True);assert result.returncode==0,result.stderr
    base.cli(jar,'amend',request,'--project',project,'--check');result=subprocess.run(['java','-cp',str(jar)+os.pathsep+str(helper),'ExternalQcAmendment',str(request),str(project)],capture_output=True,text=True);assert result.returncode==0,result.stdout+result.stderr
    write(project/'amendment-software.json',{'actor_type':'external','human_attestation':False,'source_sha256':sha(source),'class_sha256':sha(source.with_suffix('.class'))})

def native_reference(root,project,doc,phase,dataset,base):
    # A derived oracle view, never a modification to the analysis's immutable inputs.
    view=root/'oracle-views'/project.name/str(phase)/dataset['id'];view.mkdir(parents=True,exist_ok=True)
    external=dataset['id']=='a';sample={r['sample_id']:r for r in rows(project/'external-samples.tsv')}
    pheno=[r for r in rows(project/'phenotype.tsv') if not external or sample[r['id']]['decision']=='keep']
    table(view/'phenotype.tsv',pheno);shutil.copyfile(project/'main.vcf',view/'main.vcf');shutil.copyfile(project/'panel.vcf',view/'panel.vcf')
    mini=copy.deepcopy(doc);mini['datasets']=[copy.deepcopy(dataset)];mini['datasets'][0]['files']['phenotype']='phenotype.tsv';mini['datasets'][0]['files']['matrix']='main.vcf';mini['datasets'][0]['files']['qc_genotypes']='panel.vcf';write(view/'study.json',mini)
    ref=base.reference(view,0)
    if not external:return ref
    anchors=[i for i,id_value in enumerate(ref['ids']) if sample[id_value]['pca_basis']=='true'];g=np.array([[r[id_value] for id_value in ref['ids']] for r in ref['panel']]);mean=np.nanmean(g[:,anchors],axis=1);scale=np.sqrt(mean*(1-mean/2));valid=scale>1e-6;z=np.nan_to_num((g[valid]-mean[valid,None])/scale[valid,None],nan=0);basis=z[:,anchors]
    values,vectors=np.linalg.eigh(basis.T@basis/len(basis));order=np.argsort(values)[::-1];values=values[order];vectors=vectors[:,order];k=doc['hypotheses'][0]['analysis']['genotype_sample_qc']['pca_components'];weights=basis@vectors[:,:k]/(len(basis)*values[:k]);scores=z.T@weights
    for col in range(k):
        sign=-1 if weights[np.argmax(abs(weights[:,col])),col]<0 else 1;weights[:,col]*=sign;scores[:,col]*=sign
    ref.update(anchors=anchors,means=mean[valid],scales=scale[valid],pca_markers=np.flatnonzero(valid),values=values[:k],weights=weights,scores=scores);return ref

def verify_phase(root,project,folder,phase,base):
    mode=project.name;doc=contract(project,phase);policy=doc['hypotheses'][0]['analysis']['genotype_sample_qc'];held=mode in HELD or mode=='import-bound-resume' and phase==0
    run=yaml.safe_load((folder.parent/'run-manifest.yaml').read_text(encoding='utf-8'));plan=yaml.safe_load((project/'plans'/run['plan_id']/'analysis-plan.yaml').read_text(encoding='utf-8'));assert run['plan_hash']==plan['plan_hash'];planned={o['id']:o for o in plan['operations']};fit_rows=0
    artifacts=[(p,yaml.safe_load((p/'manifest.yaml').read_text(encoding='utf-8'))) for p in sorted(folder.iterdir()) if (p/'manifest.yaml').is_file()];qcs=[(p,m) for p,m in artifacts if m['operation_type']=='participant_qc'];assert len(qcs)==len(doc['datasets'])
    association_ops=[(p,m) for p,m in artifacts if m['operation_type']=='association'];assert len(association_ops)==(len(doc['datasets']) if 'association' in doc['hypotheses'][0]['analysis']['goals'] else 0)
    if held:assert all(m['state']!='complete' for p,m in association_ops)
    for qc,m in qcs:
        dataset=next(d for d in doc['datasets'] if d['id']==m['details']['dataset_id']);external=dataset['id']=='a';summary=yaml.safe_load((qc/'genotype-qc-summary.yaml').read_text(encoding='utf-8'));assert m['state']==('waiting_for_input' if held else 'complete')
        assert summary['complete']==(not held) and summary['inputs_stable'] is True and summary['association_markers_removed'] is False
        method=json.dumps(m['details'].get('method_report',{}));assert all(s not in method for s in ['PRIVATE_EXTERNAL_METHOD','PRIVATE_SOURCE_LIMITATION','PRIVATE_SOURCE_PROVENANCE','PRIVATE_PARTICIPANT_SENTINEL'])
        assert (qc/'analysis-phenotype.tsv').exists()==(not held)
        hybrid=mode in {'native-missing-pca','native-subset-basis','native-missing-basis'} and external
        fitted=hybrid and not held
        if external:
            assert summary['external_qc_reexecuted'] is False and summary['native_relatedness_computed'] is False
            assert not (qc/'genotype-qc/pairs.tsv').exists() and not (qc/'genotype-qc/initial-call-rates.tsv').exists()
            assert (qc/'genotype-qc/marker-inventory.tsv').exists()==fitted and (qc/'genotype-qc/pc-weights.tsv').exists()==fitted
            assert summary['pca_computed']==fitted
            if not held:assert summary['execution_origin']==('mixed_external_qc_native_pca' if hybrid else 'externally_produced')
            if mode in {'completed-pcs-no-basis','native-missing-basis','qc-only-no-basis'}:assert summary['external_sample_qc_complete'] is True and summary['external_basis_membership_present'] is False and 'external_basis_samples' not in summary
            if mode=='native-missing-basis':assert len(rows(qc/'sample-exclusions.tsv'))==2 and m['details']['candidate_samples_for_diagnostics']==62
        if held:continue
        selected=rows(qc/'analysis-phenotype.tsv');source_samples={r['sample_id']:r for r in rows(project/'external-samples.tsv')};pheno=rows(project/'phenotype.tsv');ids=[r['id'] for r in pheno if r['y']!='NA' and r['age']!='NA' and (source_samples[r['id']]['decision']=='keep' if external else r['id'] not in {'S01','S02'})];assert [r['id'] for r in selected]==ids
        assert summary['analysis_samples']==len(ids)
        native=hybrid or not external
        if native:
            ref=native_reference(root,project,doc,phase,dataset,base);actual=rows(qc/'genotype-qc/panel.tsv');assert [(r['variant_id'],r['disposition'],r['ld_partner']) for r in actual]==[(i,s,p or '') for i,s,p,r2 in ref['dispositions']]
            observed=rows(qc/'genotype-qc/pcs.tsv');assert [r['sample_id'] for r in observed]==ref['ids']
            for i,row in enumerate(observed):
                for k in range(2):close(row['GPC'+str(k+1)],ref['scores'][i,k],[mode,phase,dataset['id'],row['sample_id'],'PC',k])
            weights=rows(qc/'genotype-qc/pc-weights.tsv');assert len(weights)==len(ref['pca_markers'])
            for j,row in enumerate(weights):
                assert row['variant_id']==ref['panel'][ref['pca_markers'][j]]['variant_id']
                close(row['basis_dosage_mean'],ref['means'][j],[mode,phase,'mean',row['variant_id']]);close(row['basis_scale'],ref['scales'][j],[mode,phase,'scale',row['variant_id']])
                for k in range(2):close(row['GPC'+str(k+1)],ref['weights'][j,k],[mode,phase,'weight',row['variant_id'],k])
            expected_scores={id_value:ref['scores'][i] for i,id_value in enumerate(ref['ids'])}
        elif policy['pca_components']>0:
            scores={r['sample_id']:r for r in rows(project/dataset['files']['sample_qc_pcs'])};actual=rows(qc/'genotype-qc/pcs.tsv');assert [r['sample_id'] for r in actual]==ids
            for row in actual:
                for k in range(2):assert float(row['GPC'+str(k+1)])==float(scores[row['sample_id']]['PC'+str(k+1)]);close(row['GPC'+str(k+1)],scores[row['sample_id']]['PC'+str(k+1)],[mode,phase,row['sample_id'],'source_PC',k])
            expected_scores={id_value:np.array([float(scores[id_value]['PC1']),float(scores[id_value]['PC2'])]) for id_value in ids}
        assert ('GPC1' in selected[0])==policy['include_pcs']
        original_pheno={r['id']:r for r in pheno}
        for row in selected:
            assert float(row['y'])==float(original_pheno[row['id']]['y']) and float(row['age'])==float(original_pheno[row['id']]['age'])
            if policy['include_pcs']:
                for k in range(2):
                    if native:close(row['GPC'+str(k+1)],expected_scores[row['id']][k],[mode,phase,dataset['id'],row['id'],'model_PC',k])
                    else:assert float(row['GPC'+str(k+1)])==expected_scores[row['id']][k]
        associations=[(p,m) for p,m in association_ops if planned[m['operation_id']]['dataset_id']==dataset['id']]
        if 'association' not in doc['hypotheses'][0]['analysis']['goals']:assert not associations;continue
        assert len(associations)==1;path,fit=associations[0];assert fit['state']=='complete'
        operation=planned[fit['operation_id']];assert operation['requires_prefit_qc'] is True and m['operation_id'] in operation['depends_on'] and operation['datasets']==[dataset['id']]
        actual=rows(path/'standard-results'/(''+dataset['id']+'.tsv'));_,variants=base.read_vcf(project/'main.vcf');eligible={r['variant_id']:r for r in variants if min(sum(r[id_value] for id_value in ids),2*len(ids)-sum(r[id_value] for id_value in ids))>=20};assert {r['feature_id'] for r in actual}==set(eligible)
        y=np.array([float(original_pheno[id_value]['y']) for id_value in ids]);cov=np.column_stack([np.ones(len(ids)),np.array([float(original_pheno[id_value]['age']) for id_value in ids])])
        if policy['include_pcs']:cov=np.column_stack([cov,np.array([expected_scores[id_value] for id_value in ids])])
        for row in actual:
            x=np.array([eligible[row['feature_id']][id_value] for id_value in ids]);design=np.column_stack([cov,x]);beta=np.linalg.lstsq(design,y,rcond=None)[0];res=y-design@beta;df=len(y)-design.shape[1];variance=np.linalg.inv(design.T@design)*(res@res)/df;se=math.sqrt(variance[-1,-1]);p=2*t.sf(abs(beta[-1]/se),df)
            mac=min(sum(x),2*len(ids)-sum(x));fit_rows+=1
            for field,value in [('beta',beta[-1]),('se',se),('p_value',p),('n',len(ids)),('called',len(ids)),('allele_number',2*len(ids)),('minor_allele_count',mac),('minor_allele_frequency',mac/(2*len(ids))),('df_denominator',df),('statistic',beta[-1]/se)]:close(row[field],value,[mode,phase,dataset['id'],'association',row['feature_id'],field],field=='p_value')
    inventory,_=base.one(folder,'finding_inventory');body=(inventory/'finding-inventory.jsonl').read_text(encoding='utf-8');candidates={json.loads(line)['finding_id'] for line in body.splitlines() if json.loads(line)['candidate']};impact,_=base.one(folder,'impact_assessment');seen=set();payloads=[]
    private=['PRIVATE_EXTERNAL_METHOD','PRIVATE_SOURCE_LIMITATION','PRIVATE_SOURCE_PROVENANCE','PRIVATE_PARTICIPANT_SENTINEL',*[r['id'] for r in rows(project/'phenotype.tsv')]]
    assert all(s not in body for s in private)
    for path in impact.rglob('evidence.yaml'):
        payload=yaml.safe_load(path.read_text(encoding='utf-8'));encoded=json.dumps(payload);assert all(s not in encoded for s in private);assert all(not re.search(r'(?i)[a-z]:[\\/]',s) and str(root).replace('\\','/') not in s.replace('\\','/') for s in strings(payload));assert all('artifact' not in entry for entry in payload['source_catalog'].values())
        for finding in payload['findings']:
            seen.add(finding['finding_id']);contexts=[o for o in finding['observations'] if o.get('kind')=='genotype_sample_qc'];assert contexts
            for context in contexts:
                assert context['primary'] is False and context['source_ids']
                if context['dataset_id']=='a':assert context['native_relatedness_computed'] is False and context['external_qc_reexecuted'] is False and context['external_limitations_review_required'] is True and 'not independently reproduced' in context['required_manuscript_caveat']
                for source in context['source_ids']:
                    entry=payload['source_catalog'][source];assert entry['operation_type']=='participant_qc';producer=next(p for p,m in qcs if m['operation_id']==entry['operation_id']);assert entry['sha256']==sha(producer/'genotype-qc-summary.yaml')
        metadata=yaml.safe_load(next(path.parent.rglob('metadata.yaml')).read_text(encoding='utf-8'));assert metadata['request_attempted'] is False and metadata['request_sent'] is False;payloads.append(str(path.relative_to(root)))
    assert seen==candidates
    if held:assert not candidates
    if not held and 'association' in doc['hypotheses'][0]['analysis']['goals']:assert candidates,(mode,phase,'Missing native positive control')
    if phase==2:assert all(m.get('reused_from_cache') is True for p,m in qcs)
    if mode=='changed-source-edition' and phase==1:
        assert all(m.get('reused_from_cache',False) is False for p,m in qcs)
        original=project/load(project/'history.json')[0];old=base.one(original,'participant_qc')[1];assert all(m['cache_key']!=old['cache_key'] for p,m in qcs)
    return {'case':mode,'phase':phase,'qc_operations':len(qcs),'held':held,'native_association_rows':fit_rows,'unsent_payloads':payloads}

def verify(root,jar):
    CHECKS.clear();software=load(root/'execution-software.json');assert sha(jar)==software['jar_sha256'];base=module(root)
    for name,digest in software['source_sha256'].items():assert sha(root/'software'/name)==digest
    results=[];seals={}
    for mode in software['cases']:
        project=root/mode
        for name,digest in load(project/'input-provenance.json').items():assert sha(project/name)==digest
        if (project/'new-source-provenance.json').exists():
            for name,digest in load(project/'new-source-provenance.json').items():assert sha(project/name)==digest
        for phase,relative in enumerate(load(project/'history.json')):
            folder=project/relative;results.append(verify_phase(root,project,folder,phase,base))
            for path in folder.iterdir():
                m=yaml.safe_load((path/'manifest.yaml').read_text(encoding='utf-8'));assert m['state']!='failed',(mode,path,m.get('summary'))
                for name,digest in m['output_sha256'].items():assert sha(path/name)==digest;seals[str((path/name).relative_to(root))]=digest
        base.cli(jar,'verify','--project',project)
    assert sum(r['native_association_rows'] for r in results)==45
    write(root/'independent-numerical-checks.json',CHECKS);write(root/'sealed-artifacts.json',seals);write(root/'verification.json',{'passed':True,'synthetic_only':True,'jar_sha256':sha(jar),'cases':results,'numerical_checks':len(CHECKS),'native_association_rows':45,'sealed_artifacts':len(seals),'all_model_requests_unsent':True})
    print(json.dumps({'cases':len(software['cases']),'phases':len(results),'numerical_checks':len(CHECKS),'sealed_artifacts':len(seals)}))

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('action',choices=['run','verify']);ap.add_argument('directory',type=Path);ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--cases',nargs='+',choices=MODES);a=ap.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name
        own=Path(__file__).resolve();shutil.copyfile(own,software/own.name);shutil.copyfile(own.with_name('genotype-sample-qc-workflow.py'),software/'genotype-sample-qc-workflow.py')
        for source in (own.parents[2]/'src/test/resources/genotype-sample-qc').iterdir():
            if source.is_file():shutil.copyfile(source,software/source.name)
        write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'source_sha256':{p.name:sha(p) for p in software.iterdir() if p.suffix!='.jar'},'cases':a.cases or MODES,'pinned_before_execution':True,'python':sys.version,'numpy':np.__version__,'scipy':scipy.__version__});base=module(root)
        for mode in a.cases or MODES:
            project=generate(root,mode,base);command=['auto',project/'study.json','--offline','--actor-type','external','--actor-id','synthetic-external-qc'];base.cli(jar,*command);history=[str(base.latest(project).relative_to(project))]
            if mode in {'import-bound-resume','changed-source-edition'}:
                amend(project,jar,mode,base);base.cli(jar,*command);history.append(str(base.latest(project).relative_to(project)));base.cli(jar,*command);history.append(str(base.latest(project).relative_to(project)))
            write(project/'history.json',history)
    verify(root,jar)
if __name__=='__main__':main()
