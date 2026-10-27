"""Offline pooled-GWAS SNP annotation, score import and missing-work acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar DIRECTORY/software/jlinalg.jar.
All inputs are synthetic. Imported consequences/scores do not execute ANNOVAR,
VEP, AlphaGenome, a biological GWAS, or an AI scientific appraisal.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
from statistics import NormalDist
import subprocess
import yaml

MODES=('auto','manual','annovar-import','analysis-reference','selected-export',
       'missing-reference','missing-refalt','identity-conflict','gene-level',
       'discovery-only','sensitivity','score-reuse','mixed-cohort','native-meta','indel-undeclared','indel-normalized')
WAITING={'selected-export','missing-reference','missing-refalt','identity-conflict','indel-undeclared'}
INAPPLICABLE={'gene-level','discovery-only','sensitivity'}
SCORE_FIELDS=('variant_id','mode','resource_version','model_version','scorer','gene_id','transcript_id','ontology_curie','track_id','raw_score','quantile_score','interval','source_record')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def rows(path):
    with path.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def table(path,data,fields=None):
    with path.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=fields or list(data[0]),delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(data)
def cli(jar,project,*args):
    command=['java','-jar',str(jar),'study',*map(str,args)]
    r=subprocess.run(command,capture_output=True,text=True,encoding='utf-8')
    with (project/'execution.log').open('a',encoding='utf-8') as stream:stream.write(json.dumps(command)+'\n'+r.stdout+r.stderr+'\n')
    assert r.returncode==0,(project,r.returncode,r.stdout,r.stderr)
def operations(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return [(p.parent,yaml.safe_load(p.read_text(encoding='utf-8'))) for p in sorted((project/'runs'/status['run_id']/'operations').glob('*/manifest.yaml'))]
def one(project,kind):
    found=[(p,m) for p,m in operations(project) if m['operation_type']==kind];assert len(found)==1,(project,kind,len(found));return found[0]
def bh(data):
    ordered=sorted((float(r['p_value']),r['feature_id']) for r in data);adjusted={};running=1.
    for j in range(len(ordered)-1,-1,-1):
        p,key=ordered[j];running=min(running,p*len(ordered)/(j+1));adjusted[key]=running
    return adjusted
def variants():
    data=[]
    for i,p in enumerate((.9,.001,.2,.6,.03)):
        data.append({'feature_id':f'v{i+1}','beta':str((1 if i%2 else -1)*NormalDist().inv_cdf(1-p/2)*.1),'se':'.1','p_value':str(p),'n':'1000',
            'chromosome':'1' if i<4 else '2','position':str(100+i if i<4 else 150),'ref':'A' if i<4 else 'C','alt':'G' if i<4 else 'T'})
    q=bh(data)
    for row in data:row['adjusted_p']=str(q[row['feature_id']])
    return data
def identity(row):return f"GRCh38:{row['chromosome']}:{row['position']}:{row['ref']}:{row['alt']}"
def generate(root,mode):
    project=root/mode;project.mkdir();data=variants();annotation_data=[dict(r) for r in data]
    if mode.startswith('indel-'):
        data[-1].update({'chromosome':'1','position':'110','ref':'A','alt':'AG'});annotation_data=[dict(r) for r in data]
    if mode=='mixed-cohort':annotation_data.append({'feature_id':'v6','chromosome':'1','position':'104','ref':'A','alt':'T','p_value':'.7','beta':'.1','se':'.1','n':'1000','adjusted_p':'.7'})
    if mode=='selected-export':data=data[:2]
    if mode=='identity-conflict':data[0]['feature_id']='GRCh38:1:999:A:G'
    expected=[{'feature_id':r['feature_id'],'variant_id':identity(r),'p_value':float(r['p_value']),'adjusted_p':float(r['adjusted_p'])} for r in data]
    if mode=='missing-refalt':
        for r in data:r['effect_allele']=r.pop('ref');r['other_allele']=r.pop('alt')
    table(project/'meta.tsv',data)
    (project/'reference.fa').write_text('>1\n'+'A'*2000+'\n>2\n'+'C'*2000+'\n',encoding='utf-8')
    consequences=[];scores=[]
    for i,row in enumerate(annotation_data):
        consequences.append({'genome_build':'GRCh38','chrom':row['chromosome'],'pos':row['position'],'ref':row['ref'],'alt':row['alt'],'gene':f'ENSG{i+1}','transcript':f'ENST{i+1}','consequence':'intron_variant' if i%2 else 'missense_variant'})
        for j,(scorer,tissue) in enumerate((('RNA_SEQ','UBERON:0002107'),('SPLICE_SITES','UBERON:0000178'))):
            scores.append(dict(zip(SCORE_FIELDS,(identity(row),'predict','synthetic-v1','synthetic-model',scorer,f'ENSG{i+1}',f'ENST{i+1}-{j}',tissue,f'track-{j}',str((i+1)*(.1 if j else -.1)),str(.7 if j else -.6),'synthetic-interval',f'source-{i}-{j}'))))
    table(project/'annotations.tsv',consequences);table(project/'scores.tsv',scores,SCORE_FIELDS)
    policy={'reference_fasta':'reference.fa','maximum_variants_per_batch':2,'consequences':{'engine':'import','annotations':'annotations.tsv','format':'canonical','release':'synthetic-v1'},'alphagenome':{'engine':'import','scores':'scores.tsv'}}
    if mode=='annovar-import':
        (project/'annovar.vcf').write_text('##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n'+''.join(f"{r['chromosome']}\t{r['position']}\t{identity(r)}\t{r['ref']}\t{r['alt']}\t.\tPASS\tGene.refGene=ENSG{i+1};ExonicFunc.refGene=missense_variant\n" for i,r in enumerate(annotation_data)),encoding='utf-8')
        policy['consequences'].update({'annotations':'annovar.vcf','format':'annovar-vcf'})
    if mode=='missing-reference':(project/'reference.fa').unlink()
    if mode=='indel-normalized':policy['reference_normalized']=True
    definition={'id':'external-meta','kind':'meta_analysis','files':{'results':'meta.tsv'},'scope':{'modality':'gwas','feature_id_type':'variant','genome_build':'GRCh38'},
                'method':{'name':'Synthetic external pooled Wald table; no biological calibration'},'family':{'id':'all-source-snps','coverage':'complete','tested_count':5,'adjustment':'BH','adjusted_p_from_full_family':True}}
    if mode=='selected-export':definition['family'].update({'coverage':'selected_only','tested_count':50})
    if mode=='gene-level':definition['scope']['feature_id_type']='gene_symbol'
    if mode=='discovery-only':definition['kind']='discovery_meta_analysis'
    if mode=='sensitivity':definition['role']='sensitivity'
    datasets=[];supplied=[definition]
    if mode=='analysis-reference':
        definition['scope'].pop('modality');definition['scope']['cohorts']=['A']
        datasets=[{'id':'a','cohort':'A','type':'gwas','stage':'analysis_reference','supplied_result_id':'external-meta','files':{}}]
    if mode=='mixed-cohort':
        cohort=[dict(data[0]),dict(annotation_data[-1])];table(project/'cohort.tsv',cohort)
        datasets=[{'id':'a','cohort':'A','type':'gwas','stage':'association_results','genome_build':'GRCh38','files':{'results':'cohort.tsv'}}]
    if mode=='native-meta':
        supplied=[]
        contract={'estimand':'per-alternate-allele quantitative trait effect','orientation':'alternate allele','effect_scale':'linear','effect_units':'trait SD per allele','phenotype_transformation':'standardized','feature_transformation':'additive dosage'}
        for key in ('a','b'):
            cohort=[]
            for r in data:
                row=dict(r);row.pop('adjusted_p');row['se']=str(float(r['se'])*math.sqrt(2));row['p_value']=str(math.erfc(abs(float(row['beta'])/float(row['se']))/math.sqrt(2)));row['effect_allele']=r['alt'];row['other_allele']=r['ref'];cohort.append(row)
            table(project/(key+'.tsv'),cohort)
            datasets.append({'id':key,'cohort':key.upper(),'type':'gwas','stage':'association_results','genome_build':'GRCh38','feature_id_type':'variant','effect_contract':contract,'files':{'results':key+'.tsv'}})
    if mode=='score-reuse':
        folder,_=one(root/'auto','variant_scoring');shutil.copyfile(folder/'variant-evidence.tsv',project/'completed-scores.tsv')
        supplied.append({'id':'completed-scores','kind':'variant_scoring','files':{'results':'completed-scores.tsv'},'scope':definition['scope'],'method':{'name':'Supplied synthetic functional context, imported without prediction'}})
        for name in ('reference.fa','annotations.tsv','scores.tsv'):(project/name).unlink()
        policy={}
    manual=mode in INAPPLICABLE or mode in ('manual','native-meta')
    analysis={'mode':'manual' if manual else 'auto','goals':['variant_scoring','finding_inventory'] if manual else ['finding_inventory'],'variant_followup':policy,'supplied_results':supplied}
    study={'schema_version':1,'project':{'id':'pooled-snp-'+mode,'title':'Synthetic pooled SNP '+mode,'description':'Offline input-route acceptance only; imported annotations/scores are synthetic, with no tool/model or AI scientific execution.','objective':'exploration'},
           'resources':{'directory':'fixture-resources','network':'offline','downloads':'deny'},'datasets':datasets,'hypotheses':[{'id':'H','description':'Complete missing SNP functional context from the registered supplied pool.','analysis':analysis}]}
    if mode=='native-meta':
        analysis['goals'].insert(0,'meta_analysis');analysis['operations']={'meta_analysis':{'model':'fixed'}}
        analysis['pooling']={'require_complete_contract':True,'effect_contract':contract,'dependence':{'method':'independent'}}
        study['hypotheses'][0].update({'outcome':'synthetic-y','inputs':{'discovery':['a'],'replication':['b']}})
    write(project/'study.json',study);write(project/'expected.json',{'source_rows':expected,'cohort_extra':1 if mode=='mixed-cohort' else 0})
    write(project/'original-input-hashes.json',{p.relative_to(project).as_posix():sha(p) for p in sorted(project.iterdir()) if p.is_file()})

def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text(encoding='utf-8'));assert sha(jar)==software['jar_sha256']
    assert sha(root/'workflow-verifier-source.py')==software['verifier_sha256']==sha(Path(__file__))
    checks=0;records=[];hashes=0
    for mode in MODES:
        p=root/mode;expected=json.loads((p/'expected.json').read_text(encoding='utf-8'))
        for name,digest in json.loads((p/'original-input-hashes.json').read_text(encoding='utf-8')).items():assert sha(p/name)==digest;hashes+=1
        ops=operations(p);assert not any(m['state']=='failed' or m['operation_type']=='association' for _,m in ops)
        folder,m=one(p,'variant_scoring');want='waiting_for_input' if mode in WAITING else 'not_applicable' if mode in INAPPLICABLE else 'complete';assert m['state']==want,(mode,m)
        if mode in INAPPLICABLE:
            assert not (folder/'variant-consequences.tsv').exists();checks+=2
        elif mode=='identity-conflict':
            assert not (folder/'variant-consequences.tsv').exists() and (folder/'primary-pooled-results.tsv').exists();checks+=2
        elif mode=='score-reuse':
            assert m['details']['analysis_executed_by_jlinalg'] is False and not (folder/'consequences').exists();checks+=2
            canonical=yaml.safe_load((folder/'supplied-analysis.yaml').read_text(encoding='utf-8'))['canonical_results'];provided=rows(folder/canonical)
            assert len(provided)==15 and {'gene_id','transcript_id','scorer','ontology_curie','raw_score','quantile_score'}.issubset(provided[0]);checks+=3
        else:
            inventory=rows(folder/'variant-inventory.tsv');pool=[r for r in inventory if r['input_source']=='official_meta_analysis'];assert len(pool)==len(expected['source_rows']);checks+=1
            assert all(r['dataset_id']=='' and r['source_operation_id'] for r in pool)
            assert m['details']['significance_filter_applied'] is False;checks+=2
            unavailable=mode in ('missing-reference','missing-refalt')
            for wanted in expected['source_rows']:
                got=next(r for r in pool if r['feature_id']==wanted['feature_id']);assert got['eligible']=='true'
                if mode=='missing-refalt':assert got['variant_id']=='NA' and got['disposition']=='identity_unresolved'
                else:assert got['variant_id']==wanted['variant_id']
                if not unavailable and not (mode=='indel-undeclared' and wanted['feature_id']=='v5'):assert got['consequence_status']=='annotated' and got['alphagenome_status']=='imported'
                checks+=3
            if mode=='selected-export':assert m['details']['complete_source_coverage'] is False and m['details']['pooled_sources'][0]['source_testing_family']['tested_count']==50;checks+=2
            else:assert m['details']['complete_source_coverage'] is True;checks+=1
            consequence_count=0 if unavailable else len(expected['source_rows'])+expected['cohort_extra']-(1 if mode=='indel-undeclared' else 0)
            assert len(rows(folder/'variant-consequences.tsv'))==consequence_count
            scores=rows(folder/'alphagenome-scores.tsv');assert len(scores)==2*consequence_count
            assert all(r['ontology_curie'] in ('UBERON:0002107','UBERON:0000178') and r['model_version']=='synthetic-model' for r in scores);checks+=3
            if mode=='mixed-cohort':assert m['details']['input_records']==7 and m['details']['unique_eligible_variants']==6;checks+=2
            if mode=='native-meta':assert m['details']['input_records']==15 and m['details']['unique_eligible_variants']==5 and m['details']['pooled_sources'][0]['source_coverage']=='native_scoped_pool';checks+=3
            if mode=='analysis-reference':
                resolved=yaml.safe_load((p/'resolved-study.yaml').read_text(encoding='utf-8'));assert resolved['_resolution']['followup_applicability']['H']['modality_origin']=='registered_analysis_references';checks+=1
            if mode=='auto':assert m.get('reused_from_cache') is True;checks+=1
        for directory,manifest in ops:
            for name,digest in manifest.get('output_sha256',{}).items():assert sha(directory/name)==digest;hashes+=1
        imported=[(directory,manifest) for directory,manifest in ops if manifest['operation_type'] in ('meta_analysis','discovery_meta_analysis','supplied_sensitivity')]
        assert len(imported)==1
        if mode=='native-meta':
            assert imported[0][1]['state']=='complete' and imported[0][1]['details'].get('analysis_executed_by_jlinalg') is not False
            canonical=rows(imported[0][0]/'meta-analysis.tsv')
        else:
            assert imported[0][1]['details']['analysis_executed_by_jlinalg'] is False
            source_metadata=yaml.safe_load((imported[0][0]/'supplied-analysis.yaml').read_text(encoding='utf-8'));canonical=rows(imported[0][0]/source_metadata['canonical_results'])
        for wanted in expected['source_rows']:
            got=next(r for r in canonical if r['feature_id']==wanted['feature_id'])
            adjusted=bh(canonical)[wanted['feature_id']] if mode=='native-meta' else float(got['adjusted_p'])
            assert math.isclose(adjusted,wanted['adjusted_p'],abs_tol=1e-14);checks+=1
            if mode=='native-meta':assert math.isclose(float(got['p_value']),wanted['p_value'],abs_tol=1e-14);checks+=1
        cli(jar,p,'verify','--project',p);records.append({'case':mode,'state':m['state'],'source_rows':len(expected['source_rows'])})
    report={'passed':True,'cases':records,'checks':checks,'file_hashes_verified':hashes,'jar_sha256':sha(jar),'verifier_sha256':software['verifier_sha256'],
            'all_available_eligible_snps_include_nonsignificant_rows':True,'pooled_and_cohort_queries_deduplicated':True,'original_FDR_preserved':True,'scorer_tissue_transcript_context_preserved':True,'selected_export_does_not_claim_full_GWAS_annotation':True,
            'completed_score_stage_reused_without_native_prediction':True,'reference_identity_namespace_and_primary_ownership_gates':True,'external_requests':0,'real_ANNOVAR_VEP_AlphaGenome_execution':False,'real_scientific_calibration':False,'full_pipeline_conformance':False}
    output=root/'verification.json'
    if output.exists():assert json.loads(output.read_text(encoding='utf-8'))==report
    else:write(output,report)
    print(json.dumps(report,indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=='run':
        root.mkdir();(root/'software').mkdir();target=root/'software/jlinalg.jar';shutil.copyfile(jar,target);jar=target;shutil.copyfile(Path(__file__).resolve(),root/'workflow-verifier-source.py')
        write(root/'execution-software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(root/'workflow-verifier-source.py')})
        for mode in MODES:generate(root,mode);cli(jar,root/mode,'auto',root/mode/'study.json','--offline')
        cli(jar,root/'auto','auto',root/'auto/study.json','--offline')
    verify(root,jar)
