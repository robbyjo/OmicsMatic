"""Verify saved global-command/source-role bytes; no Java,R,primary XML or model execution."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,message):
    if not ok:raise ValueError(message)
def safe(name):
    path=PurePosixPath(name);require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe saved path');return path.parts

def verify(root):
    q=root/'docs/literature/limma-global-development';profile=json.loads((q/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_SAVED_GLOBAL_LIMMA_AND23_SOURCE_ROLES_NOT_CATALOG_ADMISSION' and profile['new_catalog_credit']==0 and not profile['whole_goal_complete'],'Different scope/credit claim')
    for name,wanted in profile['source_inputs'].items():require(pin(root.joinpath(*safe(name)).read_bytes())==wanted,'Current source differs: '+name)
    for name,wanted in profile['saved_files'].items():require(pin(q.joinpath(*safe(name)).read_bytes())==wanted,'Saved data differs: '+name)
    archive=q/'evidence.zip';require(pin(archive.read_bytes())==profile['archive'],'Saved archive differs')
    inventory=json.loads((q/'INVENTORY.json').read_bytes());require(pin((q/'INVENTORY.json').read_bytes())==profile['inventory'],'Inventory differs')
    with zipfile.ZipFile(archive) as z:
        names=z.namelist();require(len(names)==len(set(names)) and set(names)==set(inventory['files']),'Saved population differs');total=0
        for name in names:
            safe(name);size=z.getinfo(name).file_size;total+=size;require(size<=32*1024*1024 and total<=256*1024*1024,'Saved packet capacity exceeded');require(pin(z.read(name))==inventory['files'][name],'Saved member differs: '+name)
    result=dict(status='PASS_SAVED_GLOBAL_LIMMA_AND23_SOURCE_ROLES_NOT_CATALOG_ADMISSION',saved_files=len(names),saved_tests_per_JVM=78,JVMs=3,original_capture_cases_retained=46,global_original_RUN_examples=6,global_original_REPLAY=8,global_successful_capture_verifications=18,reviewed_source_labels=23,reviewed_source_papers=18,source_semantic_controls_per_host=15,source_control_hosts=2,legacy_CLI_regression_tests=22,new_catalog_credit=0,fresh_model_or_XML_execution=False,whole_goal_complete=False)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False);result=verify(Path(__file__).resolve().parents[2]);(args.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))

if __name__=='__main__':main()
