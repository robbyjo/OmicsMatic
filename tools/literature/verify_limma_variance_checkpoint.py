#!/usr/bin/env python3
"""Verify saved native limma variance evidence bytes; no R, Java, Git or model execution."""
from pathlib import Path, PurePosixPath
from collections import Counter
import argparse, hashlib, json, zipfile

def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def safe(name):
    p=PurePosixPath(name)
    assert not p.is_absolute() and '..' not in p.parts and '\\' not in name and ':' not in name,name
    return p
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);root=Path(__file__).resolve().parents[2];q=root/'docs/literature/limma-variance-checkpoint'
    try:
        packet=json.loads((q/'CHECKS.json').read_bytes())
        for name,w in {**packet['source_inputs'],**packet['canonical_unchanged']}.items():safe(name);assert pin(root/name)==w,'Source/canonical bytes differ: '+name
        assert pin(q/'checks.zip')==packet['archive'] and pin(q/'INVENTORY.json')==packet['inventory']
        inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
        with zipfile.ZipFile(q/'checks.zip') as z:
            assert len(z.namelist())==len(set(z.namelist()))==len(unique) and set(z.namelist())=={'blobs/'+v for v in unique}
            assert sum(i.file_size for i in z.infolist())<=128*1024*1024 and all(i.file_size<=32*1024*1024 for i in z.infolist())
            for sha in unique:assert len(sha)==64 and all(c in '0123456789abcdef' for c in sha) and hashlib.sha256(z.read('blobs/'+sha)).hexdigest()==sha
            for name,w in inventory.items():
                safe(name);raw=z.read('blobs/'+w['sha256']);assert len(raw)==w['bytes']
                if args.restore:
                    target=out/'evidence'/name;target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as f:f.write(raw)
            def read(name):return json.loads(z.read('blobs/'+inventory[name]['sha256']))
            original=read('current/original-variance-controls-1/REFERENCE.json')
            assert original['cases']==372 and original['original_errors']==7 and original['original_warning_cases']==36
            assert original['source_guard']=='exact original namespace bodies and formals equal recovered source functions'
            assert read('current/original-variance-controls-1/PROCESS.json')['exit_code']==0
            build=read('current/normal-gradle-1/COMPLETED.json');assert build['exit_code']==0
            report=build['reports']['org.jlinalg.differential.LimmaVarianceModerationTest'];assert (report['tests'],report['failures'],report['errors'],report['skipped'])==(379,0,0,0)
            qualified=read('current/packaged-tests-2/COMPLETED.json');assert qualified['status']=='PASS_NORMAL_JAR_LIMMA_VARIANCE_ON_THREE_JVMS'
            assert qualified['tests_per_JVM']==379 and qualified['original_fields_per_JVM']==23873 and qualified['normal_scientific_classes_from_JAR']
            assert qualified['payloads']['prior_payloads_unchanged']==23548 and not qualified['payloads']['changed'] and not qualified['payloads']['removed']
            assert set(qualified['results'])=={'windows21','windows25','linux21'} and qualified['jar']==packet['producer']
            assert all(v['exit_code']==0 for host in qualified['results'].values() for v in host.values())
            assert qualified['catalog_credit']==0 and not qualified['full_limma_models_or_modern_unequal_df_qualified']
            assert read('current/source-review-2/COMPLETED.json')['status']=='PASS_FRESH_ALL250_IDENTITIES_AND_20_ROLE_PROPOSALS'
        census=json.loads((root/'docs/literature/limma-source-review-proposal/CENSUS.json').read_bytes())
        assert census['papers_scanned']==250 and census['candidate_papers']==len(census['reviews'])==20 and census['no_limma_addition_for_edger_only_P237']
        assert not census['canonical_metadata_modified'] and census['new_family_credit']==census['new_review_credit']==0
        tools=json.loads((root/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes());counts=Counter(t['implementation_status'] for t in tools)
        assert counts['native-subset-validated']==26 and counts['upstream-adapter-validated']==115 and counts['validated-family-alias']==1
        reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];assert len(reviews)==1575
        result=dict(status='PASS_SAVED_LIMMA_VARIANCE_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(unique),original_cases=372,tests_per_JVM=379,JVMs=3,original_fields_per_JVM=23873,prior_JAR_payloads_unchanged=23548,source_role_candidates=20,scoped_families=141,reviewed=1575,pending=1140,new_credit=0,R_Java_Git_primary_XML_or_models_executed=False,whole_goal_complete=False)
        (out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
    except BaseException as e:
        (out/'FAILURE.txt').write_text(repr(e)+'\n',encoding='utf-8',newline='\n');raise
if __name__=='__main__':main()
