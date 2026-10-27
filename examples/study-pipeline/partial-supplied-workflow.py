"""Offline acceptance of partial supplied stages and separate native completion.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar DIRECTORY/software/jlinalg.jar.
Uses the adjacent pooled-variant-workflow.py synthetic fixture generator. No
ANNOVAR, AlphaGenome prediction, or biological/scientific validation is performed.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import yaml

CASES=('partial-with-inputs','partial-missing-inputs','manual-retain','complete-reuse','partial-pool')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,data):path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def rows(path):
    with path.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
def fixture():
    folder=Path(__file__).resolve().parent
    path=folder/'pooled-fixture-source.py'
    if not path.exists():path=folder/'pooled-variant-workflow.py'
    spec=importlib.util.spec_from_file_location('pooled_fixture',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module,path
def execute(jar,project):
    result=subprocess.run(['java','-jar',str(jar),'study','auto',str(project/'study.json'),'--offline'],capture_output=True,text=True,encoding='utf-8')
    with (project/'execution.log').open('a',encoding='utf-8') as f:f.write(result.stdout+result.stderr+'\n')
    assert result.returncode==0,(project,result.returncode,result.stderr)
def operations(project):
    status=yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return [(p.parent,yaml.safe_load(p.read_text(encoding='utf-8'))) for p in (project/'runs'/status['run_id']/'operations').glob('*/manifest.yaml')]
def verify(root,jar):
    software=json.loads((root/'execution-software.json').read_text(encoding='utf-8'))
    assert sha(jar)==software['jar_sha256'];assert sha(root/'software/workflow-verifier-source.py')==software['verifier_sha256']==sha(Path(__file__))
    assert sha(root/'software/pooled-fixture-source.py')==software['fixture_sha256']
    records=[];checks=3;hashes=0
    for case in CASES:
        project=root/case
        for name,digest in json.loads((project/'original-input-hashes.json').read_text(encoding='utf-8')).items():assert sha(project/name)==digest;hashes+=1
        ops=operations(project)
        for directory,m in ops:
            for name,digest in m.get('output_sha256',{}).items():assert sha(directory/name)==digest;hashes+=1
        partial=[(p,m) for p,m in ops if m['operation_type']=='supplied_partial'];scores=[(p,m) for p,m in ops if m['operation_type']=='variant_scoring']
        if case=='complete-reuse':
            assert not partial;assert len(scores)==1
            p,m=scores[0];assert m['state']=='complete';assert m['details']['execution_origin']=='externally_produced'
            assert m['details']['replaces_native_stage'] is True;assert m['details']['result_rows']==15
            assert len(rows(p/'variant-scores.tsv'))==15;checks+=7
        else:
            assert len(partial)==1;p,m=partial[0];details=m['details']
            assert details['reported_status']=='partial';assert details['replaces_native_stage'] is False;assert details['primary_selection_eligible'] is False
            assert not (p/'variant-scores.tsv').exists();assert not (p/'meta-analysis.tsv').exists()
            assert (p/'supplied-results-limited.tsv').exists();assert 'partial analysis' in details['required_manuscript_caveat'];checks+=8
            if case=='manual-retain':
                assert not scores;assert m['state']=='complete';assert not details['native_completion_operations'];checks+=3
            elif case=='partial-pool':
                assert m['state']=='waiting_for_input';assert not details['native_completion_operations']
                assert 'No native completion can be scheduled' in str(details['needs'])
                assert not any(m['operation_type']=='meta_analysis' for _,m in ops)
                assert len(scores)==1 and scores[0][1]['state']=='waiting_for_input';checks+=5
            else:
                assert m['state']=='complete';assert details['result_rows']==3;assert len(scores)==1
                p,native=scores[0];assert native['details'].get('execution_origin')!='externally_produced'
                assert details['native_completion_operations']==[native['operation_id']];checks+=5
                if case=='partial-with-inputs':
                    assert native['state']=='complete';assert len(rows(p/'variant-evidence.tsv'))==15
                    assert len(rows(p/'variant-consequences.tsv'))==5;assert len(rows(p/'alphagenome-scores.tsv'))==10
                    assert len({r['variant_id'] for r in rows(p/'variant-evidence.tsv')})==5;checks+=5
                else:
                    assert native['state']=='waiting_for_input';assert native['details']['needs'];checks+=2
        records.append({'case':case,'partial_imports':[{'state':m['state'],'rows':m['details'].get('result_rows')} for _,m in partial],
            'score_operations':[{'state':m['state'],'origin':m['details'].get('execution_origin','native')} for _,m in scores]})
    result={'state':'passed','jar_sha256':sha(jar),'cases':records,'assertions':checks,'verified_input_and_output_hashes':hashes,
        'limits':['Synthetic offline import and completion routing only; no external functional predictor or biological calibration.','Partial stages are retained separately; native completion recomputes the registered stage, without merging unverified per-row fragments.']}
    save(root/'acceptance.json',result);return result
def run(root,jar):
    root.mkdir();software=root/'software';software.mkdir();shutil.copyfile(jar,software/'jlinalg.jar');jar=software/'jlinalg.jar'
    module,source=fixture();shutil.copyfile(Path(__file__),software/'workflow-verifier-source.py');shutil.copyfile(source,software/'pooled-fixture-source.py')
    save(root/'execution-software.json',{'jar_sha256':sha(jar),'verifier_sha256':sha(Path(__file__)),'fixture_sha256':sha(source)})
    module.generate(root,'auto');execute(jar,root/'auto');module.generate(root,'score-reuse')
    original=root/'score-reuse';names=json.loads((original/'original-input-hashes.json').read_text(encoding='utf-8'))
    for case in CASES:
        p=root/case;p.mkdir()
        for name in names:shutil.copyfile(original/name,p/name)
        document=json.loads((p/'study.json').read_text(encoding='utf-8'));a=document['hypotheses'][0]['analysis']
        if case=='partial-pool':
            a['supplied_results']=a['supplied_results'][:1];a['supplied_results'][0]['reported_status']='partial'
        elif case!='complete-reuse':
            a['supplied_results'][1]['reported_status']='partial';data=rows(p/'completed-scores.tsv');first=data[0]['variant_id']
            module.table(p/'completed-scores.tsv',[r for r in data if r['variant_id']==first])
        if case=='partial-with-inputs':
            base=json.loads((root/'auto/study.json').read_text(encoding='utf-8'));a['variant_followup']=base['hypotheses'][0]['analysis']['variant_followup']
            for name in ('reference.fa','annotations.tsv','scores.tsv'):shutil.copyfile(root/'auto'/name,p/name)
        if case=='manual-retain':a['mode']='manual';a['goals']=['finding_inventory']
        save(p/'study.json',document);save(p/'original-input-hashes.json',{f.name:sha(f) for f in p.iterdir() if f.is_file() and f.name!='original-input-hashes.json'})
        execute(jar,p)
    return verify(root,jar)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=('run','verify'));parser.add_argument('directory',type=Path);parser.add_argument('--jar',required=True,type=Path)
    args=parser.parse_args();result=(run if args.action=='run' else verify)(args.directory.resolve(),args.jar.resolve());print(json.dumps(result,indent=2))
