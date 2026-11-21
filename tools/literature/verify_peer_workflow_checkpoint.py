"""Check saved PEER qualification bytes; no Java, C++, Git, XML parsing or fitting."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def safe(name):
    p=PurePosixPath(name);require(not p.is_absolute() and '..' not in p.parts and '\\' not in name,'Unsafe saved path');return p.parts

def verify(root):
    q=root/'docs/literature/peer-workflow-checkpoint';profile=json.loads((q/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_SAVED_PEER_GLOBAL_ORIGINAL_DENSE_AND18_ROLE_PROPOSALS' and profile['new_catalog_credit']==0 and not profile['whole_goal_complete'],'Different qualification scope')
    for name,w in profile['source_inputs'].items():require(pin(root.joinpath(*safe(name)))==w,'Current qualified source differs: '+name)
    require(pin(q/'checks.zip')==profile['archive'] and pin(q/'INVENTORY.json')==profile['inventory'],'Saved archive/inventory differs')
    inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
    with zipfile.ZipFile(q/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+sha for sha in unique},'Different saved member population');total=0
        for sha in unique:
            require(re.fullmatch('[0-9a-f]{64}',sha) is not None,'Invalid saved hash');size=archive.getinfo('blobs/'+sha).file_size;total+=size;require(size<=64*1024*1024 and total<=256*1024*1024,'Saved capacity exceeded');require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed saved blob')
        for name,w in inventory.items():safe(name);require(len(archive.read('blobs/'+w['sha256']))==w['bytes'],'Different saved length')
        def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
        build=read('current/normal-gradle-4/COMPLETED.json');require(build['exit_code']==0 and sum(v['tests'] for v in build['reports'].values())==26 and not any(v[k] for v in build['reports'].values() for k in ['failures','errors','skipped']),'Incomplete normal producer/tests')
        tests=read('current/packaged-global-1/RESULT.json');require(tests['status']=='PASS_PACKAGED_PEER26_TESTS_THREE_JVMS' and set(tests['hosts'])=={'windows21','windows25','linux21'} and all(v['exit_code']==0 and v['tests']==26 and v['skipped']==0 for v in tests['hosts'].values()),'Incomplete packaged host tests')
        for host in ['windows','linux']:
            value=read('current/source-controls-'+host+'-1/RESULT.json');require(value['semantic_refusals']==16 and value['positive_papers']==18 and value['papers']==250,'Different source audit scope')
        controls=read('current/global-controls-1/RESULT.json');require(len(controls['inverse_refusals'])==7 and len(controls['direct_original_files_identical'])==42 and len(controls['replays'])==3,'Incomplete global example/replay/inverse checks')
        require(read('current/preceding-context-1/RESULT.json')['status']=='PASS_PEER_SOURCE_INVERSE_AND_UNCHANGED_PRECEDING_LIMMA_OWNER','Different preceding-owner context')
        require(read('current/original-controls-2/OBSERVER-EQUIVALENCE.json')['exact_original_files']==120,'Different original/observer comparison')
    return dict(status=profile['status'],logical_files=len(inventory),unique_blobs=len(unique),packaged_tests_per_JVM=26,JVMs=3,original_control_cases=24,original_completed_controls=6,explicit_fixed_iteration_partials=18,observer_unchanged_original_files=120,
        global_examples=6,global_example_verifications=6,actual_global_replays=3,source_reviewed_labels=18,source_reviewed_papers=18,source_semantic_controls_per_host=16,source_control_hosts=2,source_inverse_refusals=7,
        global_command_registered=True,legacy_native_Peer_stock_parity_qualified=False,new_catalog_credit=0,whole_goal_complete=False,fresh_Java_CPP_Git_XML_or_model_execution=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');args.out.mkdir(parents=True,exist_ok=False)
    result=verify(Path(__file__).resolve().parents[2]);(args.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))

if __name__=='__main__':main()
