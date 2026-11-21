from pathlib import Path
import ast,hashlib,json,shutil,sys,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';B=T/'build/gcta-catalog-1';Q=T/'docs/literature/gcta-catalog-checkpoint';Q.mkdir();sys.path.insert(0,str(T/'tools/literature'));from gcta_catalog import evidence as e,model as m
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
files={}
for folder in ['controls-1','controls-2','controls-3','inspect-1','apply-1','verify-fresh-1','cli-controls-1','preview-ui-1','installed-ui-1','independent-readback-1','source-intake-1']:
    for p in (B/folder).rglob('*'):
        if p.is_file():files['current/'+p.relative_to(B).as_posix()]=p
for p in (B/'plan-1').iterdir():
    if p.is_file():files['current/plan-1/'+p.name]=p
for p in (R/'build/gcta-catalog-start-20261120-1').iterdir():
    if p.is_file():files['start/'+p.name]=p
inventory={};blobs={}
for name,p in sorted(files.items()):
    raw=p.read_bytes();sha=hashlib.sha256(raw).hexdigest();inventory[name]=dict(bytes=len(raw),sha256=sha);blobs.setdefault(sha,p)
with zipfile.ZipFile(Q/'checks.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for sha,p in sorted(blobs.items()):archive.write(p,'blobs/'+sha)
save(Q/'INVENTORY.json',inventory)
helpers=['start_gcta_catalog_20261120.py','inspect_gcta_admission_inputs_20261120.py','prepare_gcta_catalog_sources_20261120.py','run_gcta_catalog_controls_20261120.py','run_gcta_catalog_controls_v2_20261120.py','run_gcta_catalog_controls_v3_20261120.py','check_gcta_catalog_preview_ui_20261120.py','check_gcta_catalog_installed_ui_20261120.py','readback_gcta_catalog_20261120.py','check_gcta_catalog_cli_controls_20261120.py','package_gcta_catalog_checkpoint_20261120.py'];dest=T/'tools/literature/gcta_catalog/development';dest.mkdir()
for name in helpers:
    source=R/'build'/name;ast.parse(source.read_text(encoding='utf-8'));shutil.copyfile(source,dest/name)
source_names=e.OWN+['tools/literature/verify_gcta_catalog_checkpoint.py']+[p.relative_to(T).as_posix() for p in dest.iterdir()]
canonical=[*m.META,m.DATA+'/SHA256SUMS',e.PROOF,e.COMPAT,m.COVERAGE]+[p.relative_to(T).as_posix() for folder in [T/e.FOLDER,T/'docs/literature/gcta-catalog-sources',T/'docs/literature/gcta-catalog-controls'] for p in folder.iterdir() if p.is_file()]
verified=json.loads((B/'verify-fresh-1/COMPLETED.json').read_bytes());assert verified['result']['integrations']==141 and verified['current_manifest']['entries']==829
save(Q/'CHECKS.json',dict(schema_version=1,status='PASS_GCTA_ADMISSION_CURRENT_OWNER_POSTCHECKS',source_inputs={n:pin(T/n) for n in source_names},canonical_after={n:pin(T/n) for n in canonical},archive=pin(Q/'checks.zip'),inventory=pin(Q/'INVENTORY.json'),logical_files=len(inventory),unique_blobs=len(blobs),verified_result=verified['result'],source_control_tests_per_host=23,control_hosts=2,CLI_refusals=5,preview_and_installed_UI_checked=True,old_failed_attempts_preserved=True,scientific_sources_changed=False,scientific_models_executed=False,whole_goal_complete=False,goal_status='active',worktrees_preserved=30,private_transfer_required=True,site_Pages_publication=False,released=False))
print('Saved GCTA catalog checkpoint',len(inventory),'logical files;',len(blobs),'blobs;',pin(Q/'checks.zip')['bytes'],'archive bytes',flush=True)
