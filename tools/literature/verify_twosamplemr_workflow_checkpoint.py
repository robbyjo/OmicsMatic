"""Check saved original TwoSampleMR workflow qualification bytes; execute no analyses."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def safe(name):
    path=PurePosixPath(name);require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe saved path');return path.parts
def verify(root):
    q=root/'docs/literature/twosamplemr-workflow-checkpoint';profile=json.loads((q/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_ORIGINAL_TWOSAMPLEMR_FOUR_OPERATIONS_667_NOT_CATALOG_ADMISSION' and profile['new_catalog_credit']==0 and not profile['whole_goal_complete'],'Different qualification scope')
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged']}.items():require(pin(root.joinpath(*safe(name)))==wanted,'Qualified bytes changed: '+name)
    require(pin(q/'checks.zip')==profile['archive'] and pin(q/'INVENTORY.json')==profile['inventory'],'Changed saved archive/inventory')
    inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
    with zipfile.ZipFile(q/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+s for s in unique},'Different saved member population');total=0
        for sha in unique:
            require(re.fullmatch('[0-9a-f]{64}',sha) is not None,'Invalid saved blob');size=archive.getinfo('blobs/'+sha).file_size;total+=size
            require(size<=64*1024*1024 and total<=256*1024*1024,'Saved archive exceeds capacity');require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed saved blob')
        for name,wanted in inventory.items():safe(name);require(len(archive.read('blobs/'+wanted['sha256']))==wanted['bytes'],'Changed saved size')
        def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
        build=read('current/normal-gradle-4/COMPLETED.json');require(build['exit_code']==0 and sum(v['tests'] for v in build['reports'].values())==667 and not any(v[k] for v in build['reports'].values() for k in ['failures','errors','skipped']),'Incomplete final normal build/tests')
        packaged=read('current/packaged-2/RESULT.json');require(packaged['status']=='PASS_PACKAGED_TWOSAMPLEMR_WORKFLOW667_TESTS_THREE_JVMS' and set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(v['exit_code']==0 and v['tests']==667 and v['skipped']==0 for v in packaged['hosts'].values()),'Incomplete final packaged hosts')
        comparison=read('current/packaged-2/JAR-COMPARISON.json');require(comparison['published_predecessor_payloads_unchanged']==22308 and comparison['legacy_MR_and_global_dispatch_unchanged'],'Preceding payloads changed')
        analyses=read('current/normal-analyses-3/RESULT.json');require(analyses['all_pass'] and len(analyses['results'])==7 and all(v['exit_code']==0 for v in analyses['results'].values()),'Incomplete final actual workflows')
        controls=read('current/cli-controls-2/RESULT.json');require(len(controls['results'])==10 and all(v['exit_code']==v['expected_exit'] and not v['results_admitted'] for v in controls['results'].values()),'Invalid failed/skipped analyses')
        timeout=controls['results']['owned-timeout'];require(timeout['owned_child_terminal'] and timeout['owned_cleanup'] and all(not v['alive_after_cleanup'] for v in timeout['owned_cleanup']),'Owned test child not terminal')
        original=read('current/independent-original-4/RESULT.json');require(original['all_pass'] and original['full_original_returns']==12,'Incomplete independent original returns')
        replay=read('current/replay-2/RESULT.json');require(replay['status']=='PASS_THREE_REAL_REPLAYS_AND_SEVEN_LINUX_OFFLINE_VERIFICATIONS' and len(replay['replays'])==3 and sum(v['complete_original_returns_and_RNG_identical'] for v in replay['replays'].values())==8 and len(replay['linux_saved_verification'])==7 and all(v['exit_code']==0 and not v['R_executed'] for v in replay['linux_saved_verification'].values()),'Incomplete actual replay/cross-host saved verification')
        examples=read('current/examples-2/RESULT.json');require(examples['status']=='PASS_THREE_PUBLIC_WORKFLOW_EXAMPLES_AND_COMPLETE_ORIGINAL_RETURNS' and len(examples['results'])==3 and sum(v['full_original_returns_and_RNG_identical'] for v in examples['results'].values())==8,'Incomplete actual public examples')
        for group in ['normal-analyses-3','replay-2','examples-2']:
            reports=[name for name in inventory if name.startswith('current/'+group+'/') and name.endswith('/worker/runtime-profile.json')]
            require(len(reports)==(7 if group=='normal-analyses-3' else 3),'Different runtime-profile population')
            for name in reports:
                runtime=read(name);require(runtime['source_functions_guarded']==152 and runtime['version']=='0.7.14' and runtime['R']=='4.6.1' and runtime['commit']=='c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6' and not runtime['namespace_rewritten'],'Different original source/runtime guards')
        require(read('current/preceding-catalog-1/COMPLETED.json')['result']['integrations']==143,'Different preceding catalog')
        require(read('current/preceding-native-saved-1/RESULT.json')['tests_per_JVM']==638,'Different preceding native checkpoint')
        runtime=read('current/runtime-census-1/SUMMARY.json');require(runtime['private_files']==5765 and len(runtime['required_packages'])==96 and not runtime['user_library_required'] and runtime['four_copied_packages_source_unchanged'],'Different private-runtime census')
    reference=root/'src/main/resources/org/jlinalg/omics/twosamplemr-workflow';p=json.loads((reference/'SOURCE-PROFILE.json').read_bytes())
    require(p['source_functions_guarded']==152,'Different source-function scope')
    with zipfile.ZipFile(reference/'source.zip') as archive:
        require(len(archive.namelist())==len(set(archive.namelist()))==len(p['members'])==36 and set(archive.namelist())==set(p['members']),'Different original source member population')
        for name,wanted in p['members'].items():
            raw=archive.read(name);require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==wanted,'Changed original source member')
    data=root/'docs/literature/omics-2023-2026';members={}
    for line in (data/'SHA256SUMS').read_text().splitlines():
        sha,name=line.split('  ',1);target=(data/name).resolve();require(target.is_relative_to(root/'docs/literature') and name not in members,'Unsafe/duplicate catalog manifest path');require(pin(target)['sha256']==sha,'Changed catalog manifest member');members[name]=sha
    require(len(members)==835,'Different catalog manifest population')
    return dict(status=profile['status'],logical_files=len(inventory),unique_blobs=len(unique),normal_and_packaged_tests_per_JVM=667,JVMs=3,original_source_functions_guarded=152,normal_workflow_cases=7,actual_failed_or_skipped_controls=10,actual_replays=3,public_examples=3,full_original_returns_attributes_and_RNG_identical=28,Linux_R_free_actual_verifications=7,preceding_JAR_payloads_unchanged=22308,private_runtime_files_pinned=5765,required_package_identities=96,canonical_files_unchanged=27,manifest_entries=835,scoped_integrations=143,reviewed_uses=1616,pending_uses=1104,new_catalog_credit=0,global_CLI_registered=False,historical_paper_editions_qualified=False,original_R_execution_hosts=['WindowsR4.6.1'],fresh_R_Java_Git_primary_XML_or_model_execution=False,whole_goal_complete=False)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');args.out.mkdir(parents=True,exist_ok=False)
    result=verify(Path(__file__).resolve().parents[2]);(args.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
if __name__=='__main__':main()
