from pathlib import Path
import ast,hashlib,json,shutil,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';Q=T/'docs/literature/gcta-global-development';Q.mkdir(exist_ok=False)
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
helpers=['start_gcta_global_20261120.py','prepare_gcta_global_route_20261120.py','build_gcta_global_20261120.py','check_gcta_global_transition_20261120.py','qualify_gcta_global_packaged_20261120.py','check_gcta_global_workflows_20261120.py','check_gcta_global_workflows_v2_20261120.py','refresh_gcta_global_source_intake_20261120.py','check_gcta_global_source_roles_20261120.py','check_gcta_global_source_roles_v2_20261120.py','check_gcta_global_source_roles_v3_20261120.py','extend_gcta_global_source_aliases_20261120.py','read_gcta_expanded_contexts_20261120.py','expand_gcta_global_census_20261120.py','package_gcta_global_checkpoint_20261120.py','intake_gcta_census_20261120.py','run_mtcojo_gradle_20261120.py','start_mtcojo_qualification_20261120.py']
dest=T/'tools/literature/gcta_global_development';dest.mkdir()
for name in helpers:
    source=R/'build'/name;ast.parse(source.read_text(encoding='utf-8'));shutil.copyfile(source,dest/name)
files={}
def add(p,n):assert p.is_file() and n not in files;files[n]=p
for folder in ['dispatcher-edit-1','transition-controls-1','global-workflows-1','global-workflows-2','source-role-review-1','source-role-review-2','source-role-review-3']:
    for p in (B/folder).rglob('*'):
        if p.is_file():add(p,'current/'+p.relative_to(B).as_posix())
for folder in ['normal-gradle-1','packaged-global-1']:
    for p in (B/folder).iterdir():
        if p.is_file():add(p,'current/'+p.relative_to(B).as_posix())
    children=['sources','project-build/test-results/test'] if folder=='normal-gradle-1' else ['windows21','windows25','linux21']
    for child in children:
        for p in (B/folder/child).rglob('*'):
            if p.is_file() and (child!='project-build/test-results/test' or p.suffix=='.xml'):add(p,'current/'+p.relative_to(B).as_posix())
for folder,file in [('source-intake-1','FRESH-SOURCE-READBACK.json'),('source-alias-followup-1','READBACK.json'),('source-intake-expanded-1','READBACK.json')]:add(B/folder/file,'current/'+folder+'/'+file)
for p in (R/'build/gcta-global-start-20261120-1').iterdir():
    if p.is_file():add(p,'start/'+p.name)
for p in (R/'build/gc-6/build/gcta-global-predecessor-check-1').iterdir():
    if p.is_file():add(p,'unchanged-predecessor/'+p.name)
inventory={};blobs={}
for n,p in sorted(files.items()):
    raw=p.read_bytes();sha=hashlib.sha256(raw).hexdigest();inventory[n]=dict(bytes=len(raw),sha256=sha);blobs.setdefault(sha,p)
with zipfile.ZipFile(Q/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for sha,p in sorted(blobs.items()):archive.write(p,'blobs/'+sha)
save(Q/'INVENTORY.json',inventory)
shutil.copyfile(B/'source-role-review-3/SOURCE-ROLES.json',Q/'SOURCE-ROLES.json');shutil.copyfile(B/'source-intake-expanded-1/READBACK.json',Q/'SOURCE-CENSUS.json')
build=json.loads((B/'normal-gradle-1/COMPLETED.json').read_bytes());assert build['exit_code']==0 and sum(r['tests'] for r in build['reports'].values())==27
names=list(build['inputs'])+['tools/literature/verify_gcta_global.py','tools/literature/gcta_source_roles.py','tools/literature/gsea_dispatch_transition.py','tools/literature/gcta_workflows/PackagedGctaGlobalTests.java']+[p.relative_to(T).as_posix() for p in dest.iterdir()]
for n in names:
    if n.startswith('src/main/') and n!='src/main/java/org/jlinalg/cli/JLinAlgCli.java':assert (T/n).read_bytes()==subprocess.check_output(['git','show','cfeee186d5068a2af3be33ef9fb69508feefdd17:'+n],cwd=T),n
producer=build['JARs']['cli/omicsmatic-0.4.0.jar'];assert pin(B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar')==producer
save(Q/'PROFILE.json',dict(schema_version=1,status='QUALIFIED_GCTA_GLOBAL_ROUTE_AND_REVIEWED_SOURCE_ROLES_CATALOG_ADMISSION_OPEN',base='cfeee186d5068a2af3be33ef9fb69508feefdd17',source_inputs={n:pin(T/n) for n in names},archive=pin(Q/'evidence.zip'),inventory=pin(Q/'INVENTORY.json'),source_roles=pin(Q/'SOURCE-ROLES.json'),source_census=pin(Q/'SOURCE-CENSUS.json'),logical_files=len(inventory),unique_blobs=len(blobs),normal_producer=producer,normal_producer_private_path=str(B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar'),scientific_sources_unchanged=True,changed_payloads=['org/jlinalg/cli/JLinAlgCli.class'],unchanged_payloads=23547,tests_per_JVM=27,JVMs=3,global_capture_verifications=246,actual_global_invocations=75,original_table_comparisons=12,reviewed_source_label_roles=27,executed_papers=19,source_candidates=23,nonexecution_candidates=4,proposed_observations_added=2,proposed_observations_removed=1,canonical_source_reviews_unchanged=1548,current_observations=2714,current_pending_uses=1166,new_catalog_credit=0,scoped_integrations=140,native_integrations=26,upstream_integrations=114,manifest_entries=826,global_command_registered=True,catalog_admission_complete=False,whole_goal_complete=False,goal_status='active',worktrees_preserved=29,private_transfer_required=True,site_modified=False,released=False))
print('Saved GCTA global checkpoint',len(inventory),'logical files;',len(blobs),'blobs;',pin(Q/'evidence.zip')['bytes'],'bytes;normal producer',producer,flush=True)
