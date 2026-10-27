"""Offline packaged S-LDSC numerical, reuse, QC and finding-inventory acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.
Synthetic LD scores establish numerical behavior only. The independent NumPy QR
oracle is also checked against a pinned upstream LDSC revision by the fixture
generator. No real GWAS, biological calibration or AI scientific appraisal runs.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import random
import shutil
import subprocess
import numpy as np
import yaml

MODES=('native','fixed','shuffled','allele-flip','chi-qc','region-qc','official-meta','official-meta-auto','supplied','supplied-selected','weak','coverage-loss','selected-summary','wrong-count','wrong-build','missing-reference','bad-overlap','work-limit','collinear','duplicate-allele')
WAITING={'weak','coverage-loss','selected-summary','wrong-count','wrong-build','missing-reference','bad-overlap','work-limit','collinear','duplicate-allele'}
TARGETS=('TARGET_A','TARGET_B')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def rows(path):
    with path.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def table(path,items):
    with path.open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(items[0]),delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(items)
def cli(jar,*args):
    result=subprocess.run(['java','-jar',str(jar),'study',*map(str,args)],capture_output=True,text=True,encoding='utf-8')
    assert result.returncode==0,(args,result.returncode,result.stdout,result.stderr)
    return result.stdout
def operations(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return [(p.parent,yaml.safe_load(p.read_text(encoding='utf-8'))) for p in sorted((project/'runs'/status['run_id']/'operations').glob('*/manifest.yaml'))]
def one(project,kind):
    found=[(p,d) for p,d in operations(project) if d['operation_type']==kind]
    assert len(found)==1,(project,kind,len(found));return found[0]
def policy():
    return {'summary':{'file':'summary.tsv','coverage':'complete','tested_snps':483,'population':'synthetic-reference','genome_build':'GRCh38','calibration':'one_df_chi_square','model':'synthetic numerical reference, not a fitted biological GWAS','qc':'Synthetic full input; no real assay or genotype QC claimed','ascertainment':'quantitative','provenance':{'purpose':'numerical acceptance'}},
      'reference':{'population':'synthetic-reference','genome_build':'GRCh38','release':'synthetic-fixture-1','common_variant_definition':'Synthetic binary reference moments; no human frequency claims','regression_snp_definition':'All 483 synthetic regression rows','annotation_model':'ALL + two overlapping binary targets + one binary nuisance'},
      'ld_scores':'ld-scores.tsv','annotations':'annotations.tsv','overlap':'overlap.tsv','blocks':13,'minimum_snps':100,'minimum_chromosomes':20,'adjustment':'BY'}

def generate(root,mode):
    p=root/mode;p.mkdir();fixtures=root/'software/fixtures'
    for name in ('summary.tsv','ld-scores.tsv','annotations.tsv','overlap.tsv'):shutil.copyfile(fixtures/name,p/name)
    config=policy();supplied=[];summary=rows(p/'summary.tsv');ld=rows(p/'ld-scores.tsv')
    if mode=='fixed':config['intercept']='one'
    if mode=='shuffled':random.Random(201).shuffle(summary);random.Random(302).shuffle(ld);table(p/'summary.tsv',summary);table(p/'ld-scores.tsv',ld)
    if mode=='allele-flip':
        for r in summary:r['ref'],r['alt']=r['alt'],r['ref']
        table(p/'summary.tsv',summary)
    if mode=='chi-qc':summary[0]['chi_square']='999';table(p/'summary.tsv',summary)
    if mode=='region-qc':
        summary[0].update({'chromosome':'6','position':'27000000'});ld[0].update({'chromosome':'6','position':'27000000'});table(p/'summary.tsv',summary);table(p/'ld-scores.tsv',ld)
    if mode=='weak':
        for r in summary:r['chi_square']='1'
        table(p/'summary.tsv',summary)
    if mode=='coverage-loss':
        for r in summary[:70]:r['ref']='C'
        table(p/'summary.tsv',summary)
    if mode=='selected-summary':config['summary']['coverage']='selected_only'
    if mode=='wrong-count':config['summary']['tested_snps']=484
    if mode=='wrong-build':config['summary']['genome_build']='GRCh37'
    if mode=='missing-reference':config['overlap']='missing.tsv'
    if mode=='bad-overlap':
        data=rows(p/'overlap.tsv');data[1]['TARGET_B']=str(int(data[1]['TARGET_B'])+1);table(p/'overlap.tsv',data)
    if mode=='work-limit':config['maximum_work']=1
    if mode=='collinear':
        for r in ld:r['ld_TARGET_B']=r['ld_TARGET_A']
        table(p/'ld-scores.tsv',ld)
    if mode=='duplicate-allele':
        duplicate=dict(ld[0]);duplicate.update({'variant_id':'same-physical-snp','ref':'G','alt':'A'});ld.append(duplicate);table(p/'ld-scores.tsv',ld)
    if mode in ('official-meta','official-meta-auto'):
        config['summary'].pop('file');config['summary']['statistic']='beta_se'
        data=[]
        for r in summary:
            z=math.sqrt(float(r['chi_square']));data.append({'feature_id':r['variant_id'],'beta':str(z),'se':'1','p_value':str(math.erfc(z/math.sqrt(2))),'n':r['n'],'chromosome':r['chromosome'],'position':r['position'],'ref':r['ref'],'alt':r['alt']})
        table(p/'meta.tsv',data);supplied=[{'id':'external-meta','kind':'meta_analysis','files':{'results':'meta.tsv'},'scope':{'genome_build':'GRCh38','ancestry':'synthetic-reference','modality':'gwas'},'method':{'name':'External synthetic Wald summary'},'family':{'id':'all-snps','coverage':'complete','tested_count':483,'adjustment':'BH'}}]
    if mode in ('supplied','supplied-selected'):
        native,_=one(root/'native','gwas_enrichment');data=rows(native/'gwas-enrichment.tsv')
        if mode=='supplied-selected':data=data[:1]
        table(p/'supplied.tsv',data);supplied=[{'id':'external-enrichment','kind':'gwas_enrichment','files':{'results':'supplied.tsv'},'scope':{'genome_build':'GRCh38','ancestry':'synthetic-reference','modality':'gwas'},'method':{'name':'External synthetic S-LDSC; imported without reproduction'},'family':{'id':'all-targets','coverage':'selected_only' if mode=='supplied-selected' else 'complete','tested_count':2,'adjustment':'BY','adjusted_p_from_full_family':True}}]
        config={}  # Already-completed stages do not require original LD files or policy.
        for name in ('summary.tsv','ld-scores.tsv','annotations.tsv','overlap.tsv'):(p/name).unlink()
    analysis={'mode':'manual','goals':['gwas_enrichment','impact_assessment'],'gwas_enrichment':config,'supplied_results':supplied}
    study={'schema_version':1,'project':{'id':'sldsc-'+mode,'title':'Synthetic S-LDSC '+mode,'description':'Numerical/reuse verification only; AI appraisal deliberately unavailable'},'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':[],'hypotheses':[{'id':'H','description':'Prespecified synthetic functional-annotation GWAS enrichment','analysis':analysis}]}
    if mode=='official-meta-auto':
        analysis['mode']='auto';analysis['goals']=['impact_assessment'];study['project']['objective']='exploration'
    write(p/'study.json',study);write(p/'original-input-hashes.json',{str(f.relative_to(p)):sha(f) for f in sorted(p.iterdir()) if f.is_file()})

def oracle(root,project):
    path=root/'software/sldsc-reference.py';spec=importlib.util.spec_from_file_location('pinned_sldsc_reference',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    config=policy();summary={r['variant_id']:r for r in rows(project/'summary.tsv')};ld=rows(project/'ld-scores.tsv');anno=rows(project/'annotations.tsv');ids=[r['term_id'] for r in anno];m=np.array([float(r['count']) for r in anno]);ov={r['term_id']:r for r in rows(project/'overlap.tsv')};overlap=np.array([[float(ov[x][y]) for y in ids] for x in ids]);kept=[]
    for r in ld:
        s=summary.get(r['variant_id']);assert s is not None
        if int(r['chromosome'])==6 and 25000000<=int(r['position'])<=34000000:continue
        chi=float(s['chi_square'])
        if chi>=80:continue
        kept.append((int(r['chromosome']),int(r['position']),r['variant_id'],chi,float(s['n']),float(r['weight_ld']),[float(r['ld_'+x]) for x in ids]))
    kept.sort();n=len(kept);blocks=np.searchsorted(np.linspace(0,n,14,dtype=int)[1:-1],np.arange(n),side='right')
    result=module.reference(np.array([r[3] for r in kept]),np.array([r[6] for r in kept]),np.array([r[5] for r in kept]),np.array([r[4] for r in kept]),m,overlap,blocks,project.name!='fixed')
    return ids,result,kept,blocks

def adjusted(probabilities):
    items=sorted(probabilities.items(),key=lambda x:x[1]);m=len(items);factor=sum(1/i for i in range(1,m+1));out={};running=1.
    for i in range(m-1,-1,-1):key,p=items[i];running=min(running,p*m/(i+1)*factor);out[key]=running
    return out

def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text());assert sha(jar)==software['jar_sha256'];assert sha(root/'software/gwas-enrichment-workflow.py')==software['verifier_sha256'];assert sha(Path(__file__).resolve())==software['verifier_sha256'],'Use the pinned verifier for retained historical bundles'
    assert sha(root/'software/sldsc-reference.py')==software['reference_generator_sha256']
    for name,digest in software['fixtures_sha256'].items():assert sha(root/'software/fixtures'/name)==digest
    checks=0;seals={};case_records=[];total_findings=0
    def near(a,b,tolerance=3e-8,absolute=1e-15):
        nonlocal checks
        assert math.isclose(float(a),float(b),rel_tol=tolerance,abs_tol=absolute),(a,b);checks+=1
    for mode in MODES:
        project=root/mode;records=operations(project);folder,manifest=one(project,'gwas_enrichment');expected_state='waiting_for_input' if mode in WAITING else 'complete';assert manifest['state']==expected_state,(mode,manifest['state'],manifest['summary']);checks+=1
        assert not any(d['operation_type'] in ('association','participant_qc') for _,d in records)
        for name,digest in json.loads((project/'original-input-hashes.json').read_text()).items():assert sha(project/name)==digest;checks+=1
        for directory,d in records:
            for name,digest in d['output_sha256'].items():assert sha(directory/name)==digest;seals[str((directory/name).relative_to(root))]=digest
        inventory,inventory_manifest=one(project,'finding_inventory');assert inventory_manifest['state']=='complete';findings=[json.loads(line) for line in (inventory/'finding-inventory.jsonl').read_text(encoding='utf-8').splitlines()];enriched=[x for x in findings if x['feature_id'].startswith('gwas_enrichment:')];total_findings+=len(enriched)
        if mode in WAITING:
            assert not enriched
            if (folder/'gwas-enrichment.tsv').exists():assert all(r['adjusted_p']=='NA' and r['status']=='family_unresolved' for r in rows(folder/'gwas-enrichment.tsv'))
            if mode=='coverage-loss':assert len(rows(folder/'snp-qc-exclusions.tsv'))==70
            checks+=2
        elif mode in ('supplied','supplied-selected'):
            assert manifest['details']['analysis_executed_by_jlinalg'] is False;assert not (folder/'ld-scores.tsv').exists();assert len(enriched)==(1 if mode=='supplied-selected' else 2)
            native=rows(one(root/'native','gwas_enrichment')[0]/'gwas-enrichment.tsv');got=rows(folder/'gwas-enrichment.tsv')
            for r in got:
                ref=next(v for v in native if v['term_id']==r['term_id']);near(r['adjusted_p'],ref['adjusted_p']);near(r['enrichment'],ref['enrichment'])
            assert all('Externally produced' in str(f) for f in enriched);checks+=4
        else:
            assert len(enriched)==2;config=json.loads((project/'study.json').read_text())['hypotheses'][0]['analysis']['gwas_enrichment'];assert manifest['details']['official_family_complete'] is True
            if mode in ('official-meta','official-meta-auto'):
                reference=json.loads((root/'software/fixtures/reference.json').read_text())['cases'][0]['expected'];ids=['ALL','TARGET_A','TARGET_B','NUISANCE'];kept=rows(project/'summary.tsv');blocks=None
                meta,md=one(project,'meta_analysis');assert md['details']['analysis_executed_by_jlinalg'] is False;assert manifest['details']['source_origin']=='official_scoped_results';checks+=2
                if mode=='official-meta-auto':
                    resolved=yaml.safe_load((project/'resolved-study.yaml').read_text());a=resolved['hypotheses'][0]['analysis'];receipt=resolved['_resolution']['followup_applicability']['H']
                    assert 'gwas_enrichment' in a['goals'] and receipt['source_id']=='external-meta' and receipt['automatic_goal_merge'] is True
                    plan=yaml.safe_load(next((project/'plans').glob('*/analysis-plan.yaml')).read_text())
                    enrichment=next(op for op in plan['operations'] if op['id']==manifest['operation_id'])
                    assert md['operation_id'] in enrichment['depends_on'];checks+=3
            else:ids,reference,kept,blocks=oracle(root,project)
            got=rows(folder/'gwas-enrichment.tsv');ref_prob={key:reference['p_values'][ids.index(key)] for key in TARGETS};q=adjusted(ref_prob)
            mapping={'enrichment':'enrichments','enrichment_se':'enrichment_se','heritability_proportion':'proportions','heritability_proportion_se':'proportion_se','category_heritability':'category','per_snp_contrast':'contrasts','per_snp_contrast_se':'contrast_se','p_value':'p_values'}
            for r in got:
                j=ids.index(r['term_id']);assert r['status']=='ok' and r['adjustment']=='BY'
                for col,key in mapping.items():near(r[col],reference[key][j],absolute=1e-300 if col=='p_value' else 1e-15)
                near(r['adjusted_p'],q[r['term_id']],absolute=1e-300)
            for r in rows(folder/'sldsc-coefficients.tsv'):
                j=ids.index(r['term_id']);near(r['coefficient'],reference['coefficients'][j]);near(r['coefficient_se'],math.sqrt(reference['coefficient_covariance'][j*4+j]))
            for r in rows(folder/'sldsc-block-deletes.tsv'):near(r['coefficient'],reference['delete_coefficients'][int(r['block'])][ids.index(r['term_id'])]);near(r['total_heritability'],reference['delete_totals'][int(r['block'])])
            near(manifest['details']['total_heritability'],reference['total']);near(manifest['details']['total_heritability_se'],reference['total_se']);near(manifest['details']['intercept'],reference['intercept']);assert len(rows(folder/'regression-snps.tsv'))==len(kept)
            if blocks is not None:assert [int(r['block']) for r in rows(folder/'regression-snps.tsv')]==blocks.tolist()
            for f in enriched:
                observation=f['observations'][0];assert observation['adjustment']=='BY';assert 'observed-scale' in observation['required_manuscript_caveat'];assert 'enrichment_se' in observation;assert 'not pathway activation' in observation['required_manuscript_caveat'];context=observation['gwas_enrichment_context'];near(context['total_heritability'],reference['total']);assert context['regression_snps']==len(kept);assert context['reference']['genome_build']=='GRCh38';assert 'file' not in context['source_declarations'];assert context['official_family_complete'] is True
            if mode in ('chi-qc','region-qc'):assert len(rows(folder/'snp-qc-exclusions.tsv'))==1
            checks+=8
        if mode not in WAITING:
            assert manifest.get('reused_from_cache') is True,mode
            if mode=='official-meta-auto':
                assert one(project,'meta_analysis')[1].get('reused_from_cache') is True
                assert one(project,'literature_search')[1].get('reused_from_cache',False) is False
            else:assert inventory_manifest.get('reused_from_cache') is True,mode
            checks+=2
        else:
            assert manifest.get('reused_from_cache',False) is False,mode
            checks+=1
        cli(jar,'verify','--project',project);case_records.append({'case':mode,'analysis_state':manifest['state'],'imported':mode.startswith('supplied'),'enrichment_findings':len(enriched)})
    report={'passed':True,'cases':case_records,'checks':checks,'sealed_outputs':len(seals),'enrichment_finding_observations':total_findings,'jar_sha256':software['jar_sha256'],'verifier_sha256':software['verifier_sha256'],'independent_numpy_qr':True,'pinned_upstream_reference':'2fdeeb3b44379408794154993dbd6101b8946b7e','supplied_stages_reused_without_refit':True,'complete_and_selected_source_fdr_retained':True,'complete_manual_enrichment_and_inventory_replayed_from_cache':True,'automatic_meta_inference_completed_enrichment':True,'automatic_completed_statistics_reused_pending_branches_retried':True,'unresolved_enrichment_retried_instead_of_cached':True,'study_verify_all_cases':True,'real_gwas_or_biological_calibration':False,'ai_scientific_appraisal':False,'full_pipeline_conformance':False}
    output=root/'verification.json'
    if output.exists():assert json.loads(output.read_text())==report
    else:write(output,report)
    digest_path=root/'sealed-output-sha256.json'
    if digest_path.exists():assert json.loads(digest_path.read_text())==seals
    else:write(digest_path,seals)
    print(json.dumps(report,indent=2))

def pin(root,jar):
    folder=root/'software';folder.mkdir();target=folder/jar.name;shutil.copyfile(jar,target);shutil.copyfile(Path(__file__).resolve(),folder/'gwas-enrichment-workflow.py');repo=Path(__file__).resolve().parents[2];shutil.copyfile(repo/'src/test/python/sldsc-reference.py',folder/'sldsc-reference.py');shutil.copytree(repo/'src/test/resources/sldsc',folder/'fixtures')
    write(root/'execution-software.json',{'jar':str(target.relative_to(root)),'jar_sha256':sha(target),'verifier_sha256':sha(folder/'gwas-enrichment-workflow.py'),'reference_generator_sha256':sha(folder/'sldsc-reference.py'),'fixtures_sha256':{p.name:sha(p) for p in sorted((folder/'fixtures').iterdir()) if p.is_file()}});return target

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=='run':
        root.mkdir(parents=True,exist_ok=False);jar=pin(root,jar)
        for mode in MODES:
            generate(root,mode);cli(jar,'auto',root/mode/'study.json','--offline','--actor-type','external','--actor-id','synthetic-sldsc-acceptance')
            cli(jar,'auto',root/mode/'study.json','--offline','--actor-type','external','--actor-id','synthetic-sldsc-acceptance')
    verify(root,jar)
