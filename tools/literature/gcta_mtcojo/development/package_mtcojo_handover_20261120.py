from pathlib import Path
import ast,hashlib,json,shutil,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';Q=T/'docs/literature/gcta-mtcojo-development';Q.mkdir(exist_ok=False)
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
helpers=sorted((R/'build').glob('*mtcojo*20261120*.py'))+[R/'build/run_packaged_gcta_qualification_20261120.py']
dest=T/'tools/literature/gcta_mtcojo/development';dest.mkdir()
for source in helpers:
    ast.parse(source.read_text(encoding='utf-8'));shutil.copyfile(source,dest/source.name)
files={}
def include(p,name):
    assert p.is_file() and name not in files,(p,name);files[name]=p
for folder in ['synthetic-inputs-1','original-1.94.1-1','original-behavior-controls-1','prototype-workflow-1','native-reference-corpus-1','workflow-test-corpus-1','normal-workflow-runs-1']:
    for p in sorted((B/folder).rglob('*')):
        if p.is_file():include(p,'current/'+p.relative_to(B).as_posix())
for folder in ['compile-1','normal-gradle-1','normal-gradle-2','packaged-qualification-1']:
    base=B/folder
    for p in sorted(base.iterdir()):
        if p.is_file():include(p,'current/'+p.relative_to(B).as_posix())
    for child in (['sources'] if folder=='compile-1' else ['sources','project-build/test-results/test'] if folder.startswith('normal-gradle') else ['windows21','windows25','linux21']):
        for p in sorted((base/child).rglob('*')):
            if p.is_file() and (child!='project-build/test-results/test' or p.suffix=='.xml'):include(p,'current/'+p.relative_to(B).as_posix())
source=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1/source/files/jianyangqt-gcta-6da64e9'
for name in ['LICENSE','main/mtcojo.cpp','main/option.cpp','main/gcta.h']:
    if (source/name).is_file():include(source/name,'official-source/'+name)
include(R/'build/gcta-mtcojo-start-20261120-1/COMPLETED.json','start/COMPLETED.json')
inventory={};blobs={}
for name,p in sorted(files.items()):
    raw=p.read_bytes();sha=hashlib.sha256(raw).hexdigest();inventory[name]=dict(bytes=len(raw),sha256=sha);blobs.setdefault(sha,p)
with zipfile.ZipFile(Q/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for sha,p in sorted(blobs.items()):archive.write(p,'blobs/'+sha)
save(Q/'INVENTORY.json',inventory)
build=json.loads((B/'normal-gradle-2/COMPLETED.json').read_bytes());packaged=json.loads((B/'packaged-qualification-1/COMPLETED.json').read_bytes());normal=json.loads((B/'normal-workflow-runs-1/COMPLETED.json').read_bytes())
assert build['exit_code']==0 and sum(row['tests'] for row in build['reports'].values())==24
assert len(normal['results'])==2 and sum(len(r) for r in normal['results'].values())==8
names=list(build['inputs'])+['tools/literature/gcta_workflows/PackagedMtcojoTests.java']
names+=sorted(p.relative_to(T).as_posix() for p in (T/'tools/literature/gcta_mtcojo').rglob('*') if p.is_file())
profile=dict(schema_version=1,status='DEVELOPMENT_CHECKPOINT_FULL_MTCOJO_QUALIFICATION_UNFINISHED',source_tag='v1.94.1',source_commit='6da64e9d8be838d01c34efb974e69c72e861f4c2',source_inputs={n:pin(T/n) for n in names},archive=pin(Q/'evidence.zip'),inventory=pin(Q/'INVENTORY.json'),logical_files=len(inventory),unique_blobs=len(blobs),normal_producer=build['JARs']['cli/omicsmatic-0.4.0.jar'],normal_producer_private_path=str(B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'),saved_tests_per_JVM=24,saved_JVMs=['Windows21','Windows25','Linux21'],original_conditional_fields_per_JVM=300000,saved_prior_capture_checks=168,normal_original_table_comparisons=8,additional_original_behavior_runs=18,normal_payload_transition=packaged['payloads'],effect_scale='as_supplied',prevalence_conversion_claimed=False,source_target='Java17 compiled by Windows JDK21',independent_Linux_compilation='SKIP3: Linux javac absent',new_catalog_credit=0,scoped_integrations=140,native_integrations=26,upstream_integrations=114,reviewed_uses=1548,pending_uses=1166,manifest_entries=826,full_mtcojo_qualification_complete=False,whole_goal_complete=False,goal_status='active',worktrees_preserved=27,private_transfer_required=True,site_modified=False,released=False)
save(Q/'PROFILE.json',profile)
print('Saved mtCOJO handover',len(inventory),'logical files;',len(blobs),'blobs;',profile['archive']['bytes'],'archive bytes;',len(helpers),'helpers',flush=True)
