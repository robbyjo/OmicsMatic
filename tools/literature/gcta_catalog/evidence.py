"""Explicit preceding catalog, unchanged old owner, current global qualification and source ownership."""
from pathlib import Path
import json,subprocess,sys,uuid
from gcta_catalog import primary as p,model as m
from gcta_dispatch_transition import DISPATCH,dispatcher_before

ROOT=Path(__file__).resolve().parents[3]
BASE='aed274a43aacaec95c59ef5f66dc03e791ea39be'
HISTORICAL_HEAD='cfeee186d5068a2af3be33ef9fb69508feefdd17'
FOLDER='docs/literature/gcta-catalog-admission'
PROOF=m.DATA+'/gcta-catalog-validation.json'
COMPAT=m.DATA+'/gcta-catalog-shared-source-compatibility.json'
OLD_PROOF=m.DATA+'/gsea-catalog-validation.json'
CONTROLS='docs/literature/gcta-catalog-controls'
OWN=['tools/literature/gcta_catalog/'+n for n in ['__init__.py','primary.py','model.py','evidence.py','catalog.py','test_source.py','test_model.py','test_transaction.py','ADMISSION.md']]
OWN+=['tools/literature/close_gcta_catalog.py','tools/literature/check_gcta_catalog_ui.cjs',m.GUIDE,'docs/vignettes/gcta-tools.md']

require=p.require
pin=p.pin
def git(root,*a):return subprocess.check_output(['git','--no-replace-objects',*a],cwd=root)
def sources():return {n:pin((ROOT/n).read_bytes()) for n in OWN}
def qualification():
    out=ROOT/'build/gcta-catalog-1'/('global-reader-'+uuid.uuid4().hex);command=[sys.executable,'-B',str(ROOT/'tools/literature/verify_gcta_global.py'),'--out',str(out)]
    process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);out.mkdir(parents=True,exist_ok=True);(out/'process.stdout').write_bytes(process.stdout);(out/'process.stderr').write_bytes(process.stderr);(out/'PROCESS.json').write_bytes(p.encoded(dict(command=command,exit_code=process.returncode)))
    require(process.returncode==0,'Current saved global qualification failed; inspect '+str(out));result=json.loads((out/'RESULT.json').read_bytes())
    require(result['status']=='PASS_SAVED_GCTA_GLOBAL_AND_27_REVIEWED_LABEL_ROLES_NOT_CATALOG_ADMISSION' and result['JVMs']==3 and result['saved_tests_per_JVM']==27 and result['saved_global_capture_verifications']==246,'Different qualified global scope')
    files=['docs/literature/gcta-global-development/'+n for n in ['PROFILE.json','INVENTORY.json','evidence.zip','SOURCE-ROLES.json','SOURCE-CENSUS.json']]
    files+=['tools/literature/verify_gcta_global.py','tools/literature/gcta_dispatch_transition.py','tools/literature/gcta_source_roles.py']
    return dict(result=result,proof_files={n:pin((ROOT/n).read_bytes()) for n in files},fresh_Java_or_model_execution=False)
def admission_qualification():
    result=qualification();controls=json.loads((ROOT/CONTROLS/'CONTROLS.json').read_bytes());require(controls['status']=='PASS_GCTA_SOURCE_MODEL_TRANSACTION_CONTROLS_TWO_HOSTS' and controls['source_code']==sources(),'Changed or missing current-source controls')
    require(set(controls['results'])=={'windows','linux'} and all(r['exit_code']==0 and r['tests']==23 for r in controls['results'].values()),'Failed/incomplete control host')
    import zipfile
    path=ROOT/CONTROLS/'checks.zip';require(pin(path.read_bytes())==controls['archive'],'Changed control archive')
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist())==len(set(archive.namelist())) and set(archive.namelist())==set(controls['files']),'Different control file population')
        for n,w in controls['files'].items():require(pin(archive.read(n))==w,'Changed control member')
    result['controls']=controls;result['proof_files'].update({CONTROLS+'/'+n:pin((ROOT/CONTROLS/n).read_bytes()) for n in ['CONTROLS.json','checks.zip']});return result
def baseline(before):
    require(set(before)==set(m.META),'Exactly eight preceding metadata files required')
    for n,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+n),'Different exact catalog predecessor: '+n)
    current=(ROOT/DISPATCH).read_bytes();require(current==git(ROOT,'show',BASE+':'+DISPATCH),'Changed qualified dispatcher');dispatcher_before(current)
    changed=git(ROOT,'diff','--name-only','--diff-filter=MDRT',BASE,'--','src').decode().splitlines();require(not changed,'Changed scientific/Java source')
    return dict(base_commit=BASE,prepared_head=git(ROOT,'rev-parse','HEAD').decode().strip(),before={n:pin(v) for n,v in before.items()},dispatcher=pin(current),manifest=pin((ROOT/m.DATA/'SHA256SUMS').read_bytes()))
def retained(before,current,receipt):
    proof=json.loads((ROOT/OLD_PROOF).read_bytes());prior=dispatcher_before(current)
    require(proof['after']=={n:pin(raw) for n,raw in before.items()} and proof['qualified_dispatcher']==pin(prior),'Different retained GSEA catalog/dispatcher')
    for group in ['source_code','evidence']:
        for n,w in proof[group].items():require(pin((ROOT/n).read_bytes())==w,'Changed previous owner source/evidence: '+n)
    require(receipt['status']=='PASS_UNCHANGED_GSEA_CATALOG_OWNER' and receipt['historical_head']==HISTORICAL_HEAD and receipt['exit_code']==0 and receipt['working_tree_unchanged'] and not receipt['source_overlay_used'],'Unchanged previous-owner execution required')
    require(receipt['completed']['status']==proof['status'] and receipt['completed']['result']==proof['result'] and receipt['completed']['current_manifest']['entries']==826,'Different preceding verification')
    return dict(status='PASS_EXACT_PRECEDING_GSEA_METADATA_OWNER_AND_GCTA_DISPATCH_INVERSE',old_proof=pin((ROOT/OLD_PROOF).read_bytes()),previous_source_files=len(proof['source_code']),previous_evidence_files=len(proof['evidence']),old_metadata_files=8,old_reviews_retained=1548,old_reader_overlay_used=False,old_hashes_refreshed=False,receipt=pin(p.encoded(receipt)))
def run_predecessor(historical,before,out):
    historical=historical.resolve();require(historical!=ROOT and historical.is_dir(),'Use the exact separate unchanged GSEA owner')
    require(git(historical,'rev-parse','HEAD').decode().strip()==HISTORICAL_HEAD and not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Preserve dirty work; use the exact clean previous owner')
    for n,raw in before.items():require((historical/n).read_bytes()==raw,'Historical canonical data differs')
    require(dispatcher_before((ROOT/DISPATCH).read_bytes())==(historical/DISPATCH).read_bytes(),'Historical dispatcher inverse differs')
    dest=historical/'build'/('gcta-catalog-predecessor-'+uuid.uuid4().hex);command=[sys.executable,'-B',str(historical/'tools/literature/close_gsea_catalog.py'),'--verify','--out',str(dest)]
    process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/'predecessor.stdout').write_bytes(process.stdout);(out/'predecessor.stderr').write_bytes(process.stderr);(out/'PREDECESSOR-PROCESS.json').write_bytes(p.encoded(dict(command=command,exit_code=process.returncode,evidence_output=str(dest))))
    require(process.returncode==0,'Previous-owner verification failed; preserve diagnostics');require(not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Historical working tree changed')
    receipt=dict(status='PASS_UNCHANGED_GSEA_CATALOG_OWNER',historical_head=HISTORICAL_HEAD,command=command,exit_code=0,evidence_output=str(dest),working_tree_unchanged=True,source_overlay_used=False,completed=json.loads((dest/'COMPLETED.json').read_bytes()));retained(before,(ROOT/DISPATCH).read_bytes(),receipt);return receipt
