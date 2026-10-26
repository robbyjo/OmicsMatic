"""Offline synthetic genotype sample-QC acceptance, including downstream OLS.

Run: python -B genotype-sample-qc-workflow.py run NEW_DIR --jar EXECUTABLE_JAR
Verify: python -B genotype-sample-qc-workflow.py verify DIR --jar PINNED_JAR
Requires NumPy, SciPy, PyYAML. Inputs are synthetic; model requests stay unsent.
Pins the executable, verifier, raw fixtures and reference generator before use.
"""
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import numpy as np
import scipy
from scipy.stats import t
import yaml

MODES=['native','same-source','imputed-panel','dosage-only','review','exclusion-limit',
       'source-budget-resume','pair-budget','pca-budget','pc-collision','missing-panel-sample',
       'model-only','known-dependence','diagnostic-pcs','reordered-markers','region-exclusion',
       'phenotype-incomplete','early-bound','held-pc-group','provided-pcs','observed-low-call',
       'sex-chromosome-missing','all-call-rates-fail','imputed-no-quality','qc-only-imputed-no-quality']
HELD={'dosage-only','review','exclusion-limit','pair-budget','pca-budget','pc-collision',
      'missing-panel-sample','known-dependence','early-bound','held-pc-group',
      'all-call-rates-fail','imputed-no-quality'}
CHECKS=[]


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def rows(path):
    with path.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def table(path,data):
    with path.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(data)
def cli(jar,*args):
    result=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8')
    assert result.returncode==0,result.stdout+result.stderr
def latest(project):
    state=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return project/'runs'/state['run_id']/'operations'
def one(folder,kind,required=True):
    found=[(p,yaml.safe_load((p/'manifest.yaml').read_text(encoding='utf-8'))) for p in sorted(folder.iterdir()) if (p/'manifest.yaml').is_file()]
    found=[(p,m) for p,m in found if m['operation_type']==kind]
    assert len(found)<=1,(folder,kind)
    if required:assert len(found)==1,(folder,kind)
    return found[0] if found else None
def close(actual,expected,label,probability=False):
    actual=float(actual);tolerance=5e-7*abs(expected) if probability else 5e-7*max(1,abs(expected))
    assert math.isfinite(actual) and abs(actual-expected)<=tolerance,(label,actual,expected)
    if probability and expected>0:assert abs(math.log(actual)-math.log(expected))<5e-7,(label,actual,expected)
    CHECKS.append({'label':label,'actual':actual,'expected':float(expected),'relative_probability_check':probability})


def vcf(path,data,ids,imputed=False):
    text='##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t'+'\t'.join(ids)+'\n'
    for row in data:
        text+='\t'.join([str(row['chromosome']),str(row['position']),row['variant_id'],row['ref'],row['alt'],'.','PASS','R2=0.99','DS' if imputed else 'GT'])
        for id_value in ids:
            value=row[id_value]
            if value=='NA':cell='.' if imputed else './.'
            else:cell=str(float(value)*.98+.02) if imputed and row['variant_id']!='rare' else str(float(value)) if imputed else ['0/0','0/1','1/1'][int(value)]
            text+='\t'+cell
        text+='\n'
    path.write_text(text,encoding='utf-8')


def generate(root,mode):
    project=root/mode;project.mkdir();raw=rows(root/'software/hard-calls.tsv');ids=list(raw[0])[5:]
    pheno=rows(root/'software/phenotype.tsv')
    if mode=='pc-collision':
        for row in pheno:row['GPC1']='123'
    if mode=='phenotype-incomplete':pheno[-1]['y']='NA'
    if mode=='provided-pcs':
        supplied={r['sample_id']:r for r in rows(root/'software/expected-pcs.tsv')}
        for row in pheno:row.update(provided_PC1=supplied[row['id']]['GPC1'],provided_PC2=supplied[row['id']]['GPC2'])
    panel_ids=list(reversed(ids));panel_data=list(reversed(raw)) if mode=='reordered-markers' else raw
    if mode=='observed-low-call':panel_data=[{**r,'S04':'NA'} if i<150 else r for i,r in enumerate(raw)]
    if mode=='all-call-rates-fail':panel_data=[{**r,**{id_value:'NA' for id_value in ids}} for r in raw]
    if mode=='sex-chromosome-missing':panel_data=raw+[{'variant_id':'missingX'+str(i),'chromosome':'X','position':str(i+1),'ref':'A','alt':'G',**{id_value:'NA' for id_value in ids}} for i in range(150)]
    if mode=='missing-panel-sample':panel_ids=panel_ids[1:]
    vcf(project/'panel.vcf',panel_data,panel_ids)
    common=[]
    for row in raw[3:]:
        values=[int(row[id_value]) for id_value in ids if row[id_value]!='NA']
        if len(values)==len(ids) and min(sum(values),2*len(values)-sum(values))>=32:
            common.append({**row,'variant_id':'assoc'+str(len(common)+1)})
            if len(common)==3:break
    for row in pheno:
        if row['y']!='NA':row['y']=str(float(row['y'])+3*int(common[0][row['id']]))
    table(project/'phenotype.tsv',pheno)
    rare={'variant_id':'rare','chromosome':'1','position':'9999999','ref':'A','alt':'G',**{id_value:int(i in [10,20,30]) for i,id_value in enumerate(ids)}}
    imputed=mode in {'imputed-panel','dosage-only','observed-low-call','imputed-no-quality','qc-only-imputed-no-quality'}
    vcf(project/'main.vcf',common+[rare],ids,imputed)
    if mode in {'imputed-no-quality','qc-only-imputed-no-quality'}:
        path=project/'main.vcf';path.write_text(path.read_text(encoding='utf-8').replace('R2=0.99','.'),encoding='utf-8')
    policy={'genome_build':'GRCh38','rationale':'Synthetic software validation; no biological findings.',
            'relatedness_action':'exclude','include_pcs':True,'pca_components':2,'panel_markers':2048}
    if mode=='review':policy['relatedness_action']='review'
    if mode=='exclusion-limit':policy['maximum_excluded_fraction']=.001
    if mode=='source-budget-resume':policy['maximum_source_cells']=1
    if mode=='pair-budget':policy['maximum_pair_word_work']=1
    if mode=='pca-budget':policy['maximum_pca_work']=500000
    if mode in {'early-bound','held-pc-group'}:policy['maximum_samples']=10
    if mode=='model-only':policy['relatedness_action']='model'
    if mode=='diagnostic-pcs':policy['include_pcs']=False
    if mode=='provided-pcs':policy.update(include_pcs=False,pca_components=0)
    if mode=='region-exclusion':policy['excluded_regions']=[{'chromosome':'1','start':1,'end':10000,'rationale':'Synthetic registered exclusion, not a reference long-range LD map.'}]
    association={'model':'lmm' if mode=='model-only' else 'ols'}
    if imputed:association.update(genotype_kind='imputed',minimum_info=.8)
    files={'matrix':'panel.vcf' if mode=='same-source' else 'main.vcf','phenotype':'phenotype.tsv'}
    if mode not in {'same-source','dosage-only'}:files['qc_genotypes']='panel.vcf'
    if mode=='model-only':
        files['grm']='grm.tsv';table(project/'grm.tsv',[{'sample_id':id_value,**{other:int(i==j) for j,other in enumerate(ids)}} for i,id_value in enumerate(ids)])
    dataset={'id':'a','type':'gwas','stage':'processed_matrix','files':files,'columns':{'sample_id':'id'}}
    if mode=='known-dependence':dataset['metadata']={'related_participants':True}
    goals=['impact_assessment'] if mode in {'same-source','model-only','qc-only-imputed-no-quality'} else ['association','impact_assessment']
    study={'schema_version':1,'project':{'id':'sample-qc-'+mode,'title':'Synthetic genotype sample QC','description':'No biological evidence or actual model impact appraisal.'},
           'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[dataset],
           'hypotheses':[{'id':'H','description':'Registered synthetic sample QC and conditional association.','inputs':{'discovery':['a']},'outcome':'y','covariates':['age'],
            'analysis':{'mode':'manual','goals':goals,'association':association,'genotype_sample_qc':policy,'harmonization':{'effect_outliers':{'enabled':False}}}}]}
    if mode=='held-pc-group':study['hypotheses'][0]['analysis']['subgroups']=[{'id':'pc_group','column':'GPC1','cutoff':0}]
    if mode=='provided-pcs':study['hypotheses'][0]['covariates']=['age','provided_PC1','provided_PC2'];dataset['metadata']={'provided_pc_provenance':'Synthetic externally supplied NumPy reference PCs; not generated by the current QC operation.'}
    write(project/'study.json',study);write(project/'input-provenance.json',{p.name:sha(p) for p in project.iterdir() if p.is_file()})
    return project


def read_vcf(path):
    header=None;result=[]
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.startswith('##'):continue
        cells=line.split('\t')
        if line.startswith('#CHROM'):header=cells[9:];continue
        record={'variant_id':cells[2],'chromosome':cells[0],'position':int(cells[1]),'ref':cells[3],'alt':cells[4],'hard_calls':cells[8]=='GT'}
        for id_value,cell in zip(header,cells[9:]):record[id_value]=np.nan if '.' in cell else sum(map(int,cell.replace('|','/').split('/'))) if record['hard_calls'] else float(cell)
        # DS decimal points are numeric; only the missing token is unavailable.
        if not record['hard_calls']:
            for id_value,cell in zip(header,cells[9:]):record[id_value]=np.nan if cell=='.' else float(cell)
        result.append(record)
    return header,result


def reference(project,phase):
    study=json.loads((project/'study.json').read_text(encoding='utf-8'));analysis=study['hypotheses'][0]['analysis'];p=analysis['genotype_sample_qc'].copy()
    if project.name=='source-budget-resume' and phase>0:p['maximum_source_cells']=1000000000
    defaults={'minimum_maf':.05,'minimum_marker_call_rate':.98,'minimum_panel_markers':1000,'seed':17,'ld_window_bp':500000,'ld_r_squared':.2,'minimum_pair_markers':1000,'minimum_pair_heterozygotes':100,'kinship_threshold':2**-3.5,'maximum_excluded_fraction':.1,'pc_prefix':'GPC'}
    for key,value in defaults.items():p.setdefault(key,value)
    pheno=rows(project/'phenotype.tsv');main_ids,main=read_vcf(project/study['datasets'][0]['files']['matrix']);ids=[r['id'] for r in pheno if r['id'] in main_ids and r['y']!='NA' and r['age']!='NA']
    panel_ids,raw=read_vcf(project/study['datasets'][0]['files'].get('qc_genotypes',study['datasets'][0]['files']['matrix']))
    if not set(ids)<=set(panel_ids) or project.name in {'early-bound','held-pc-group'} or project.name=='source-budget-resume' and phase==0:return dict(ids=ids,p=p,panel=None)
    usable=[r for r in raw if r['hard_calls'] and r['chromosome'].isdigit() and 1<=int(r['chromosome'])<=22 and len(r['ref'])==len(r['alt'])==1 and not any(r['chromosome']==v['chromosome'] and v['start']<=r['position']<=v['end'] for v in p.get('excluded_regions',[]))]
    initial_rates={id_value:(sum(np.isfinite(r[id_value]) for r in usable),len(usable)) for id_value in ids}
    # An unavailable main imputation-quality prerequisite must not select participants.
    if project.name=='imputed-no-quality':return dict(ids=ids,p=p,panel=None,initial_rates=initial_rates)
    if usable:ids=[id_value for id_value in ids if initial_rates[id_value][0]/len(usable)>=.98]
    if not ids or project.name=='dosage-only':return dict(ids=ids,p=p,panel=None,initial_rates=initial_rates)
    eligible=[]
    for row in usable:
        g=np.array([row[id_value] for id_value in ids]);maf=min(np.nanmean(g)/2,1-np.nanmean(g)/2)
        excluded=any(row['chromosome']==r['chromosome'] and r['start']<=row['position']<=r['end'] for r in p.get('excluded_regions',[]))
        if row['hard_calls'] and .05<=maf and np.mean(np.isfinite(g))>=.98 and not excluded:
            key=f"{p['seed']}\n{int(row['chromosome'])}\n{row['position']}\n{row['ref']}\n{row['alt']}\n{row['variant_id']}"
            eligible.append((hashlib.sha256(key.encode()).hexdigest(),row))
    sampled=[row for _,row in sorted(eligible,key=lambda item:(item[0],item[1]['variant_id']))[:p['panel_markers']]];sampled.sort(key=lambda r:(int(r['chromosome']),r['position'],r['variant_id']))
    panel=[];dispositions=[]
    for row in sampled:
        partner=None;r_squared=None;a=np.array([row[id_value] for id_value in ids])
        for previous in reversed(panel):
            if previous['chromosome']!=row['chromosome'] or row['position']-previous['position']>p['ld_window_bp']:break
            b=np.array([previous[id_value] for id_value in ids]);complete=np.isfinite(a)&np.isfinite(b);r2=np.corrcoef(a[complete],b[complete])[0,1]**2
            if r2>p['ld_r_squared']:partner=previous['variant_id'];r_squared=r2;break
        if partner is None:panel.append(row)
        dispositions.append((row['variant_id'],'retained' if partner is None else 'ld_pruned',partner,r_squared))
    g=np.array([[r[id_value] for id_value in ids] for r in panel]);pairs=[];related=set();n=len(ids)
    for i in range(n):
        for j in range(i+1,n):
            observed=np.isfinite(g[:,i])&np.isfinite(g[:,j]);a=g[observed,i];b=g[observed,j];hi=np.sum(a==1);hj=np.sum(b==1);hh=np.sum((a==1)&(b==1));opposite=np.sum(abs(a-b)==2);same=np.sum(a==b)
            between=.5-(hi+hj-2*hh+4*opposite)/(4*min(hi,hj));within=(hh-2*opposite)/(hi+hj)
            if between>p['kinship_threshold']:related.add((i,j))
            pairs.append([ids[i],ids[j],int(np.sum(observed)),int(hi),int(hj),int(hh),int(opposite),int(same),float(within),float(between)])
    call=np.mean(np.isfinite(g),axis=0);anchors=[]
    for i in sorted(range(n),key=lambda i:(-call[i],ids[i])):
        if not any(tuple(sorted((i,j))) in related for j in anchors):anchors.append(i)
    anchors.sort();mean=np.nanmean(g[:,anchors],axis=1);scale=np.sqrt(mean*(1-mean/2));valid=scale>1e-6;z=np.nan_to_num((g[valid]-mean[valid,None])/scale[valid,None],nan=0);basis=z[:,anchors];values,vectors=np.linalg.eigh(basis.T@basis/basis.shape[0]);order=np.argsort(values)[::-1];values=values[order];vectors=vectors[:,order];k=p['pca_components'];weights=basis@vectors[:,:k]/(basis.shape[0]*values[:k]);scores=z.T@weights
    for col in range(k):
        sign=-1 if weights[np.argmax(abs(weights[:,col])),col]<0 else 1;weights[:,col]*=sign;scores[:,col]*=sign
    return dict(ids=ids,p=p,panel=panel,dispositions=dispositions,pairs=pairs,related=related,anchors=anchors,means=mean[valid],scales=scale[valid],pca_markers=np.flatnonzero(valid),values=values[:k],fractions=values[:k]/sum(np.maximum(0,values)),weights=weights,scores=scores,initial_rates=initial_rates)


def amend(project,jar):
    state=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'));request=project/'amendment-complete-qc.yaml'
    write(request,{'schema_version':1,'amendment':{'id':'complete-qc','title':'Synthetic complete QC work budget','base_study_hash':state['resolved_study_hash'],'proposed_by':{'type':'external','id':'synthetic-genotype-qc'},'source':{'type':'synthetic-acceptance','id':'source-budget-resume'},'rationale':'Increase the registered work budget; preserve base/raw inputs; no human attestation.'},'changes':{'update':{'hypotheses':[{'id':'H','analysis':{'genotype_sample_qc':{'maximum_source_cells':1000000000}}}]}}})
    helper=project/'amendment-driver';helper.mkdir();source=helper/'QcAmendment.java';source.write_text('import java.nio.file.Path; import org.jlinalg.study.*; public final class QcAmendment { private QcAmendment() {} public static void main(String[] a) throws Exception { StudyAmendments.apply(Path.of(a[0]),Path.of(a[1]),new StudyProject.Actor("external","synthetic-genotype-qc",null)); } }',encoding='utf-8')
    result=subprocess.run(['javac','-Xlint:all','-Werror','-cp',str(jar),'-d',str(helper),str(source)],capture_output=True,text=True);assert result.returncode==0,result.stderr
    cli(jar,'amend',request,'--project',project,'--check');result=subprocess.run(['java','-cp',str(jar)+os.pathsep+str(helper),'QcAmendment',str(request),str(project)],capture_output=True,text=True);assert result.returncode==0,result.stdout+result.stderr
    write(project/'amendment-software.json',{'actor_type':'external','human_attestation':False,'source_sha256':sha(source),'class_sha256':sha(source.with_suffix('.class'))})


def verify_stage(root,project,folder,phase):
    mode=project.name;qc,manifest=one(folder,'participant_qc');summary=yaml.safe_load((qc/'genotype-qc-summary.yaml').read_text(encoding='utf-8'));expected=reference(project,phase);held=mode in HELD or mode=='source-budget-resume' and phase==0
    assert manifest['state']==('waiting_for_input' if held else 'complete'),(mode,phase,manifest['state'])
    assert summary['complete']==(not held) and summary['analysis_samples']==(0 if held else len(expected['ids']) if mode=='model-only' else len(expected['anchors']))
    assert summary['association_markers_removed'] is False and summary['inputs_stable'] is True
    assert (qc/'analysis-phenotype.tsv').is_file()==(not held)
    if 'initial_rates' in expected:
        initial=rows(qc/'genotype-qc/initial-call-rates.tsv');assert {r['sample_id'] for r in initial}==set(expected['initial_rates'])
        for row in initial:
            called,total=expected['initial_rates'][row['sample_id']];close(row['observed_calls'],called,[mode,phase,row['sample_id'],'observed_calls']);close(row['eligible_sites'],total,[mode,phase,row['sample_id'],'eligible_sites'])
            if total:close(row['call_rate'],called/total,[mode,phase,row['sample_id'],'initial_call_rate'])
    if mode=='all-call-rates-fail':assert len(rows(qc/'sample-exclusions.tsv'))==64
    if mode in {'imputed-no-quality','qc-only-imputed-no-quality'}:
        root_summary=yaml.safe_load((qc/'prefit-qc.yaml').read_text(encoding='utf-8'));assert root_summary['observed_genotype_call_rate']['imputation_quality_status']==('absent' if held else 'not_required_for_requested_scope')
    if mode=='provided-pcs':assert summary['pca_status']=='not_requested' and summary['pca_computed'] is False and not (qc/'genotype-qc/pcs.tsv').exists() and 'not requested' in summary['method_report']['method']
    if expected['panel'] is not None:
        actual=rows(qc/'genotype-qc/panel.tsv');assert [(r['variant_id'],r['disposition'],r['ld_partner']) for r in actual]==[(id_value,status,partner or '') for id_value,status,partner,value in expected['dispositions']]
        assert summary['ld_pruned_panel_markers']==len(expected['panel'])
        if mode!='pair-budget':
            actual=rows(qc/'genotype-qc/pairs.tsv');assert len(actual)==len(expected['pairs'])
            fields=['shared_markers','heterozygotes_a','heterozygotes_b','both_heterozygous','opposite_homozygous','identical_calls','king_equation_9','king_between_equation_11']
            for row,ref in zip(actual,expected['pairs']):
                assert [row['sample_a'],row['sample_b']]==ref[:2]
                for field,value in zip(fields,ref[2:]):close(row[field],value,[mode,phase,row['sample_a'],row['sample_b'],field])
            assert summary['related_pairs']==len(expected['related']) and summary['unrelated_basis_samples']==len(expected['anchors'])
        if summary['pca_computed']:
            actual=rows(qc/'genotype-qc/pcs.tsv');assert [r['sample_id'] for r in actual]==expected['ids']
            for i,row in enumerate(actual):
                for k in range(expected['p']['pca_components']):close(row['GPC'+str(k+1)],expected['scores'][i,k],[mode,phase,row['sample_id'],'PC',k])
            actual=rows(qc/'genotype-qc/pc-weights.tsv');assert len(actual)==len(expected['pca_markers'])
            for j,row in enumerate(actual):
                assert row['variant_id']==expected['panel'][expected['pca_markers'][j]]['variant_id']
                close(row['basis_dosage_mean'],expected['means'][j],[mode,phase,'basis_mean',row['variant_id']]);close(row['basis_scale'],expected['scales'][j],[mode,phase,'basis_scale',row['variant_id']])
                for k in range(expected['p']['pca_components']):close(row['GPC'+str(k+1)],expected['weights'][j,k],[mode,phase,'weight',row['variant_id'],k])
            for k,value in enumerate(summary['pca_eigenvalues']):close(value,expected['values'][k],[mode,phase,'eigenvalue',k])
    association=one(folder,'association',False);association_checks=0
    if association and not held:
        path,fit_manifest=association;assert fit_manifest['state']=='complete';output=rows(path/'standard-results/a.tsv');selected=rows(qc/'analysis-phenotype.tsv');kept=[expected['ids'][i] for i in expected['anchors']];assert [r['id'] for r in selected]==kept
        raw_pheno={r['id']:r for r in rows(project/'phenotype.tsv')};_,variants=read_vcf(project/'main.vcf');retained_variants={r['variant_id']:r for r in variants if min(sum(r[id_value] for id_value in kept),2*len(kept)-sum(r[id_value] for id_value in kept))>=20};assert {r['feature_id'] for r in output}==set(retained_variants)
        y=np.array([float(raw_pheno[id_value]['y']) for id_value in kept]);source=json.loads((project/'study.json').read_text(encoding='utf-8'));names=source['hypotheses'][0]['covariates'];covariates=np.column_stack([np.ones(len(kept)),*[np.array([float(raw_pheno[id_value][name]) for id_value in kept]) for name in names]])
        if expected['p']['include_pcs']:covariates=np.column_stack([covariates,expected['scores'][expected['anchors']]])
        for row in output:
            x=np.array([retained_variants[row['feature_id']][id_value] for id_value in kept]);design=np.column_stack([covariates,x]);beta=np.linalg.lstsq(design,y,rcond=None)[0];residual=y-design@beta;df=len(y)-design.shape[1];cov=np.linalg.inv(design.T@design)*(residual@residual)/df;se=math.sqrt(cov[-1,-1]);p=2*t.sf(abs(beta[-1]/se),df)
            for field,value in [('beta',beta[-1]),('se',se),('p_value',p)]:close(row[field],value,[mode,phase,'native_association',row['feature_id'],field],field=='p_value');association_checks+=1
    elif association:assert association[1]['state']!='complete'
    inventory,_=one(folder,'finding_inventory');body=(inventory/'finding-inventory.jsonl').read_text(encoding='utf-8');assert 'PRIVATE_PARTICIPANT_SENTINEL' not in body and all(row['id'] not in body for row in rows(project/'phenotype.tsv'))
    candidates={json.loads(line)['finding_id'] for line in body.splitlines() if json.loads(line)['candidate']}
    impact,_=one(folder,'impact_assessment');payloads=[];seen=set()
    for payload_path in impact.rglob('evidence.yaml'):
        payload=yaml.safe_load(payload_path.read_text(encoding='utf-8'));body=json.dumps(payload);assert 'PRIVATE_PARTICIPANT_SENTINEL' not in body and 'S00' not in body
        assert str(root).replace('\\','/') not in body.replace('\\\\','/') and str(project).replace('\\','/') not in body.replace('\\\\','/')
        for finding in payload['findings']:
            seen.add(finding['finding_id'])
            contexts=[o for o in finding['observations'] if o.get('kind')=='genotype_sample_qc'];assert contexts
            for context in contexts:
                assert context['primary'] is False and context['complete'] is True and 'do not assign ancestry labels' in context['required_manuscript_caveat']
                for source in context['source_ids']:
                    assert source in payload['source_catalog'];entry=payload['source_catalog'][source];assert entry['operation_type']=='participant_qc' and entry['operation_id']==manifest['operation_id'] and entry['sha256']==sha(qc/'genotype-qc-summary.yaml') and 'artifact' not in entry
        payloads.append(str(payload_path.relative_to(root)))
    for metadata in impact.rglob('metadata.yaml'):
        audit=yaml.safe_load(metadata.read_text(encoding='utf-8'));assert audit['request_attempted'] is False and audit['request_sent'] is False
    assert seen==candidates,(mode,phase,seen,candidates)
    if association and not held:assert candidates,(mode,phase,'Missing native positive control')
    return {'case':mode,'phase':phase,'state':manifest['state'],'analysis_samples':summary['analysis_samples'],'related_pairs':summary['related_pairs'],'native_association_rows':association_checks//3,'unsent_payloads':payloads}


def verify(root,jar):
    CHECKS.clear();software=json.loads((root/'execution-software.json').read_text(encoding='utf-8'));assert sha(jar)==software['jar_sha256']
    for name,digest in software['source_sha256'].items():assert sha(root/'software'/name)==digest
    results=[];seals={}
    for mode in software['cases']:
        project=root/mode
        for name,digest in json.loads((project/'input-provenance.json').read_text(encoding='utf-8')).items():assert sha(project/name)==digest
        for phase,relative in enumerate(json.loads((project/'history.json').read_text(encoding='utf-8'))):
            folder=project/relative;results.append(verify_stage(root,project,folder,phase))
            for path in folder.iterdir():
                manifest=yaml.safe_load((path/'manifest.yaml').read_text(encoding='utf-8'));assert manifest['state']!='failed',(mode,path,manifest.get('summary'))
                for name,digest in manifest['output_sha256'].items():assert sha(path/name)==digest;seals[str((path/name).relative_to(root))]=digest
        cli(jar,'verify','--project',project)
    write(root/'independent-numerical-checks.json',CHECKS);write(root/'sealed-artifacts.json',seals);write(root/'verification.json',{'passed':True,'synthetic_only':True,'jar_sha256':sha(jar),'cases':results,'numerical_checks':len(CHECKS),'sealed_artifacts':len(seals),'all_model_requests_unsent':True})
    print(json.dumps({'cases':len(software['cases']),'phases':len(results),'numerical_checks':len(CHECKS),'sealed_artifacts':len(seals)}))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--cases',nargs='+',choices=MODES);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=='run':
        root.mkdir(parents=True,exist_ok=False);software=root/'software';software.mkdir();shutil.copyfile(jar,software/jar.name);jar=software/jar.name;shutil.copyfile(Path(__file__),software/Path(__file__).name)
        fixtures=Path(__file__).resolve().parents[2]/'src/test/resources/genotype-sample-qc'
        for source in fixtures.iterdir():
            if source.is_file():shutil.copyfile(source,software/source.name)
        write(root/'execution-software.json',{'jar':str(jar.relative_to(root)),'jar_sha256':sha(jar),'source_sha256':{p.name:sha(p) for p in software.iterdir() if p.suffix!='.jar'},'cases':args.cases or MODES,'pinned_before_execution':True,'python':sys.version,'numpy':np.__version__,'scipy':scipy.__version__})
        for mode in args.cases or MODES:
            project=generate(root,mode);command=['auto',project/'study.json','--offline','--actor-type','external','--actor-id','synthetic-genotype-qc'];cli(jar,*command);history=[str(latest(project).relative_to(project))]
            if mode=='source-budget-resume':
                amend(project,jar);cli(jar,*command);history.append(str(latest(project).relative_to(project)));cli(jar,*command);history.append(str(latest(project).relative_to(project)));assert one(latest(project),'participant_qc')[1]['reused_from_cache'] is True
            write(project/'history.json',history)
    verify(root,jar)


if __name__=='__main__':main()
