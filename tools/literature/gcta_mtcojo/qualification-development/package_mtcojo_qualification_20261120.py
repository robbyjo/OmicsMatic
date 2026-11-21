from pathlib import Path
import ast,hashlib,json,shutil,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-6';B=T/'build/mtcojo-qualification-1';Q=T/'docs/literature/mtcojo-qualification';Q.mkdir(exist_ok=False);BASE='46611ae55e9b0778df6a117fd8be76ae468c27b5'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
helpers=['start_mtcojo_qualification_20261120.py','qualify_mtcojo_execution_paths_20261120.py','qualify_mtcojo_execution_paths_v2_20261120.py','audit_mtcojo_execution_paths_20261120.py','check_mtcojo_examples_20261120.py','check_mtcojo_replay_20261120.py','package_mtcojo_qualification_20261120.py']
dest=T/'tools/literature/gcta_mtcojo/qualification-development';dest.mkdir()
for name in helpers:
    source=R/'build'/name;ast.parse(source.read_text(encoding='utf-8'));shutil.copyfile(source,dest/name)
files={}
for p in sorted(B.rglob('*')):
    relative=p.relative_to(B)
    if p.is_file() and 'parent-alias' not in relative.parts:files['current/'+relative.as_posix()]=p
for p in (R/'build/mtcojo-qualification-start-20261120-1').iterdir():
    if p.is_file():files['start/'+p.name]=p
observations=json.loads((B/'execution-paths-1/COMPLETED.json').read_bytes())
for host,rows in observations['results'].items():
    for row in rows:
        if row['exit_code']==0:files['original-references/'+host+'/'+row['name']+'.mtcojo.cma']=Path(row['original_reference'])
inventory={};blobs={}
for name,p in sorted(files.items()):
    raw=p.read_bytes();sha=hashlib.sha256(raw).hexdigest();inventory[name]=dict(bytes=len(raw),sha256=sha);blobs.setdefault(sha,p)
with zipfile.ZipFile(Q/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for sha,p in sorted(blobs.items()):archive.write(p,'blobs/'+sha)
save(Q/'INVENTORY.json',inventory)
source_names=sorted(p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('Gcta*.java'))+['src/main/java/org/jlinalg/genetics/MtcojoConditional.java','src/main/java/org/jlinalg/cli/JLinAlgCli.java']
for name in source_names:assert (T/name).read_bytes()==subprocess.check_output(['git','show',BASE+':'+name],cwd=T),name
source_names+=sorted(p.relative_to(T).as_posix() for p in (T/'examples/mtcojo').iterdir() if p.is_file())
source_names+=['tools/literature/gcta_mtcojo/verify_qualification.py']+[p.relative_to(T).as_posix() for p in dest.iterdir()]
old=json.loads((T/'docs/literature/gcta-mtcojo-development/PROFILE.json').read_bytes());jar=Path(old['normal_producer_private_path']);assert pin(jar)==old['normal_producer']
save(Q/'PROFILE.json',dict(schema_version=1,status='QUALIFIED_PREPARED_MTCOJO_1941_EXECUTION_PATHS_NOT_WHOLE_TOOL_OR_PAPER',base=BASE,source_inputs={n:pin(T/n) for n in source_names},archive=pin(Q/'evidence.zip'),inventory=pin(Q/'INVENTORY.json'),logical_files=len(inventory),unique_blobs=len(blobs),normal_producer=old['normal_producer'],normal_producer_private_path=str(jar),normal_producer_owner=BASE,production_sources_and_JAR_unchanged=True,execution_observations=57,replay_observations=18,original_table_comparisons=24,source_scope='Prepared diploid complete inputs; exact GCTA1.94.1 GSMR/supplied MTCOJO, no liability conversion',prior_tests_per_JVM=24,prior_JVMs=3,prior_capture_checks=168,original_native_conditional_fields_per_JVM=300000,native_example_hosts=3,original_example_hosts=2,initial_failed_helper_and_substring_metadata_preserved=True,new_catalog_credit=0,scoped_integrations=140,native_integrations=26,upstream_integrations=114,reviewed_uses=1548,pending_uses=1166,manifest_entries=826,global_command_registered=False,catalog_admission_complete=False,whole_goal_complete=False,goal_status='active',worktrees_preserved=28,private_transfer_required=True,site_modified=False,released=False))
print('Saved mtCOJO qualification',len(inventory),'logical files;',len(blobs),'blobs;',pin(Q/'evidence.zip')['bytes'],'archive bytes',flush=True)
