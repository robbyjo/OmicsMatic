"""Read the unfinished clumping checkpoint; run no Java, R or PLINK analyses."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def safe(name):
    p=PurePosixPath(name)
    require(not p.is_absolute() and '..' not in p.parts and '\\' not in name,'Unsafe checkpoint path')
    return p.parts
def verify(root):
    q=root/'docs/literature/twosamplemr-clump-handover';profile=json.loads((q/'PROFILE.json').read_bytes())
    require(profile['status']=='UNFINISHED_LOCAL_CLUMPING_DEVELOPMENT_HANDOVER' and profile['new_catalog_credit']==0 and not profile['whole_goal_complete'] and not profile['java_adapter_complete'] and not profile['production_sources_changed'],'Different checkpoint scope')
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged']}.items():
        require(pin(root.joinpath(*safe(name)))==wanted,'Changed source/catalog file: '+name)
    for name in ['TwoSampleMrClumpCli.java','TwoSampleMrClumpRuntime.java','TwoSampleMrClumpContracts.java','TwoSampleMrClumpFiles.java','TwoSampleMrClumpAudit.java']:
        require(not (root/'src/main/java/org/jlinalg/cli'/name).exists(),'Unqualified clumping draft entered production source tree')
    require(pin(q/'evidence.zip')==profile['archive'] and pin(q/'INVENTORY.json')==profile['inventory'],'Changed checkpoint archive/inventory')
    inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
    require(len(inventory)==profile['logical_files'] and len(unique)==profile['unique_blobs'],'Different evidence population')
    with zipfile.ZipFile(q/'evidence.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+s for s in unique},'Unsafe/changed archive members')
        total=0
        for sha in unique:
            require(len(sha)==64 and all(c in '0123456789abcdef' for c in sha),'Malformed blob SHA256')
            size=archive.getinfo('blobs/'+sha).file_size;total+=size
            require(size<=16*1024*1024 and total<=128*1024*1024,'Evidence exceeds declared capacity')
            require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed evidence blob')
        for name,wanted in inventory.items():
            safe(name);require(len(archive.read('blobs/'+wanted['sha256']))==wanted['bytes'],'Different evidence size')
        def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
        latest=read('current/original-controls-5/RESULT.json')['results']
        require(len(latest)==25 and sum(v['exit_code']==0 for v in latest.values())==21,'Different latest R prototype controls')
        require({n for n,v in latest.items() if v['exit_code']!=0}=={'r2-one','coincident-window-zero','bad-reference-chromosome','space path'},'Different failed controls')
        require(all(v.get('restored') is True and v.get('observer_failures')==[] for n,v in latest.items() if n!='space path'),'Namespace restoration/observer failure')
        proof=read('current/independent-original-1/RESULT.json')
        require(proof['all_pass'] and proof['complete_original_returns']==53 and len(proof['results'])==21 and all(v['exit_code']==0 for v in proof['results'].values()),'Different earlier direct-original comparison')
    return dict(status=profile['status'],logical_files=len(inventory),unique_blobs=len(unique),latest_R_controls=25,latest_R_successes=21,expected_failed_controls=4,earlier_full_original_returns_and_RNG_identical=53,latest_suite_independently_repeated=False,new_catalog_credit=0,java_adapter_complete=False,whole_goal_complete=False,fresh_Java_R_PLINK_execution=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    result=verify(Path(__file__).resolve().parents[2]);a.out.mkdir(parents=True,exist_ok=False)
    (a.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
if __name__=='__main__':main()
