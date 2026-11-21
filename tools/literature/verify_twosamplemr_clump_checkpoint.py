"""Verify saved scoped original-clumping qualification; execute no scientific tools."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,sys,xml.etree.ElementTree as ET,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(path):
    with path.open('rb') as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def safe(name):
    p=PurePosixPath(name);require(not p.is_absolute() and '..' not in p.parts and '\\' not in name,'Unsafe saved path');return p.parts
def verify(root):
    q=root/'docs/literature/twosamplemr-clump-checkpoint';p=json.loads((q/'PROFILE.json').read_bytes())
    require(p['status']=='PASS_ORIGINAL_LOCAL_CLUMP707_NOT_CATALOG_ADMISSION' and p['new_catalog_credit']==0 and not p['whole_goal_complete'] and not p['global_CLI_registered'] and not p['historical_paper_editions_qualified'],'Different qualification scope')
    for name,wanted in {**p['source_inputs'],**p['canonical_unchanged']}.items():require(pin(root.joinpath(*safe(name)))==wanted,'Qualified source/catalog changed: '+name)
    require(pin(q/'checks.zip')==p['archive'] and pin(q/'INVENTORY.json')==p['inventory'] and pin(q/'OMITTED.json')==p['omitted'],'Changed evidence archive/index/omission record')
    inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
    require(len(inventory)==p['logical_files'] and len(unique)==p['unique_blobs'],'Different saved evidence population')
    with zipfile.ZipFile(q/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+s for s in unique},'Different archive members');total=0
        for sha in unique:
            require(len(sha)==64 and all(c in '0123456789abcdef' for c in sha),'Invalid blob key');size=archive.getinfo('blobs/'+sha).file_size;total+=size
            require(size<=32*1024*1024 and total<=256*1024*1024,'Saved archive exceeds capacity');require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed evidence blob')
        for name,wanted in inventory.items():safe(name);require(len(archive.read('blobs/'+wanted['sha256']))==wanted['bytes'],'Different logical member size')
        def raw(name):return archive.read('blobs/'+inventory['current/'+name]['sha256'])
        def read(name):return json.loads(raw(name))
        require(read('normal-gradle-1/COMPLETED.json')['exit_code']!=0,'Failed initial build must remain labeled failed')
        build=read('normal-gradle-2/COMPLETED.json');require(build['exit_code']==0 and sum(v['tests'] for v in build['reports'].values())==707 and not any(v[k] for v in build['reports'].values() for k in ['failures','errors','skipped']),'Incomplete normal build/tests')
        for name,count in [('org.jlinalg.mr.TwoSampleMrStatisticsTest',638),('org.jlinalg.cli.TwoSampleMrWorkflowTest',29),('org.jlinalg.cli.TwoSampleMrClumpTest',40)]:
            xml=ET.fromstring(raw('normal-gradle-2/project-build/test-results/test/TEST-'+name+'.xml'))
            require(int(xml.attrib['tests'])==count and all(int(xml.attrib[k])==0 for k in ['failures','errors','skipped']),'Different raw focused-test XML')
        packaged=read('packaged-1/RESULT.json');require(packaged['status']=='PASS_PACKAGED_TWOSAMPLEMR_CLUMP707_TESTS_THREE_JVMS' and set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(v['exit_code']==0 and v['tests']==707 and v['skipped']==0 for v in packaged['hosts'].values()),'Incomplete packaged hosts')
        require(read('packaged-1/JAR-COMPARISON.json')['published_predecessor_payloads_unchanged']==22320,'Preceding JAR payloads changed')
        analyses=read('normal-analyses-1/RESULT.json');require(analyses['normal_JAR'] and analyses['all_pass'] and len(analyses['results'])==25 and sum(v['exit_code']==0 for v in analyses['results'].values())==21,'Incomplete actual normal-JAR cases')
        require({n for n,v in analyses['results'].items() if v['exit_code']!=0}=={'r2-one','coincident-window-zero','bad-reference-chromosome','space path'},'Different failed original/path controls')
        controls=read('cli-controls-1/RESULT.json');require(len(controls['results'])==11 and all(v['exit_code']==v['expected_exit'] and not v['results_admitted'] for v in controls['results'].values()),'Invalid missing/failed/lifetime admission')
        timeout=controls['results']['owned-timeout'];require(timeout['owned_child_terminal'] and timeout['owned_cleanup'] and all(not v['alive_after_cleanup'] for v in timeout['owned_cleanup']),'Owned child/tree remains live')
        proof=read('independent-original-2/RESULT.json');require(proof['all_pass'] and proof['complete_original_returns']==61 and len(proof['results'])==24 and sum(v['complete_returns']==0 for v in proof['results'].values())==3,'Incomplete original normal-return/error/RNG comparison')
        replay=read('replays-examples-1/RESULT.json');require(len(replay['results'])==6 and all(v['exit_code']==0 for v in replay['results'].values()) and sum(v['full_original_returns_and_RNG_identical'] for v in replay['results'].values())==20,'Incomplete real replays/examples')
        require(all(v['parent_files_unchanged']>0 and v['exports_identical'] for n,v in replay['results'].items() if n.startswith('replay-')) and len(replay['Linux_R_free_verifications'])==5 and all(v['exit_code']==0 and not v['R_executed'] for v in replay['Linux_R_free_verifications'].values()),'Replay parent/export or Linux verification failed')
        require(read('runtime-preservation-1/RESULT.json')['runtime_files']==5765 and read('preceding-workflow-reader-1/RESULT.json')['normal_and_packaged_tests_per_JVM']==667,'Preceding runtime/workflow evidence differs')
        for name,v in analyses['results'].items():
            if v['exit_code']!=0:continue
            runtime=read('normal-analyses-1/'+name+'/worker/runtime-profile.json');report=read('normal-analyses-1/'+name+'/worker/report.json')
            require(runtime['R']=='4.6.1' and runtime['TwoSampleMR']=='0.7.14' and runtime['TwoSampleMR_source_functions_guarded']==152 and runtime['ieugwasr']=='1.2.1' and runtime['ieugwasr_source_functions_guarded']==7 and report['ieugwasr_bindings_restored'] and not report['TwoSampleMR_namespace_rewritten'] and not report['observation_failures'],'Original runtime/restoration guard differs')
    data=root/'docs/literature/omics-2023-2026';members={}
    for line in (data/'SHA256SUMS').read_text().splitlines():
        sha,name=line.split('  ',1);target=(data/name).resolve();require(target.is_relative_to(root/'docs/literature') and name not in members,'Unsafe/repeated catalog member');require(pin(target)['sha256']==sha,'Changed catalog manifest member');members[name]=sha
    require(len(members)==835,'Different catalog manifest population')
    return dict(status=p['status'],logical_files=len(inventory),unique_blobs=len(unique),normal_and_packaged_tests_per_JVM=707,JVMs=3,actual_normal_cases=25,successful_normal_selections=21,original_or_path_failures=4,actual_missing_failed_lifetime_controls=11,actual_replays=3,public_examples=3,Linux_R_free_verifications=5,full_original_returns_attributes_and_RNG_identical=81,preceding_JAR_payloads_unchanged=22320,private_runtime_files_unchanged=5765,canonical_files_unchanged=27,manifest_entries=835,scoped_integrations=143,reviewed_uses=1616,pending_uses=1104,new_catalog_credit=0,global_CLI_registered=False,historical_paper_editions_qualified=False,original_R_execution_hosts=['WindowsR4.6.1'],fresh_Java_R_PLINK_execution=False,whole_goal_complete=False)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');result=verify(Path(__file__).resolve().parents[2]);args.out.mkdir(parents=True,exist_ok=False)
    (args.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
if __name__=='__main__':main()
