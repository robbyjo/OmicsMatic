"""Exact admitted predecessor, unchanged global command and source ownership."""
from pathlib import Path
import io,json,subprocess,sys,uuid,zipfile
from gsea_catalog import model as m
from gsea_catalog.primary import require,pin
from gsea_dispatch_transition import DISPATCH,dispatcher_before
from verify_broad_gsea_global import verify as global_qualification

ROOT=Path(__file__).resolve().parents[3]
BASE='b89c57376177b964205aa219fe5efd378dfa6a9e'
HISTORICAL_HEAD='0de612c6182b87d42a1d574af5f06a99630a0924'
FOLDER='docs/literature/gsea-catalog-admission'
PROOF=m.DATA+'/gsea-catalog-validation.json'
COMPAT=m.DATA+'/gsea-catalog-shared-source-compatibility.json'
OLD_PROOF=m.DATA+'/edger-catalog-validation.json'
GUIDES=[m.GUIDE,'docs/vignettes/broad-gsea-tools.md']
OWN=['tools/literature/gsea_catalog/'+n for n in ['__init__.py','decisions.py','primary.py','model.py','evidence.py','catalog.py','test_source.py','test_model.py','test_admission.py','ADMISSION.md']]
OWN+=['tools/literature/'+n for n in ['close_gsea_catalog.py','gsea_dispatch_transition.py','verify_broad_gsea_global.py','check_gsea_catalog_ui.cjs']]
OWN+=['docs/literature/gsea-catalog-sources/'+n for n in ['.gitattributes','IDENTITIES.json','AUXILIARY.json','CENSUS.json','P096-author-code.zip','README.md']]
OWN+=GUIDES
CONTROLS='docs/literature/gsea-catalog-controls'

def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=root)
def sources():return {n:pin((ROOT/n).read_bytes()) for n in OWN}

def native_qualification():
    result=global_qualification();require(result['global_RUN_VERIFY_REPLAY'] and result['previous_captures_VERIFY_REPLAY'],'Global native workflow qualification missing')
    files=['docs/literature/gsea-global-development/QUALIFICATION.json','docs/literature/gsea-global-development/checks.zip',
        'docs/literature/gsea-workflow-development/QUALIFICATION.json','docs/literature/gsea-workflow-development/checks.zip','docs/literature/gsea-workflow-development/workflow-results.zip']
    return dict(result=result,proof_files={n:pin((ROOT/n).read_bytes()) for n in files},fresh_Java_or_scientific_model_execution=False)

def qualification():
    result=native_qualification();controls=json.loads((ROOT/CONTROLS/'CONTROLS.json').read_bytes())
    require(controls['status']=='PASS_GSEA_CATALOG_SOURCE_MODEL_AND_TRANSACTION_CONTROLS_TWO_HOSTS'
        and controls['tests_per_host']==28 and controls['source_code']==sources(),'Missing or changed current-source admission controls')
    require(set(controls['results'])=={'windows','linux'} and all(r['exit_code']==0 and r['tests']==28 for r in controls['results'].values()),'Failed source/model/transaction control host')
    raw=(ROOT/CONTROLS/'checks.zip').read_bytes();require(pin(raw)==controls['archive'],'Changed admission-control archive')
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        require(len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(controls['files']),'Different control evidence population')
        for n,w in controls['files'].items():require(pin(z.read(n))==w,'Changed control evidence: '+n)
        require(z.testzip() is None,'Corrupt control archive')
    result['admission_controls']=controls;result['proof_files'].update({CONTROLS+'/'+n:pin((ROOT/CONTROLS/n).read_bytes()) for n in ['CONTROLS.json','checks.zip']});return result

def baseline(before):
    require(set(before)==set(m.META),'Exactly eight preceding metadata files required')
    for n,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+n),'Canonical predecessor differs from explicit base: '+n)
    current=(ROOT/DISPATCH).read_bytes();require(current==git(ROOT,'show',BASE+':'+DISPATCH),'Qualified global dispatcher changed')
    prior=dispatcher_before(current)
    return dict(base_commit=BASE,prepared_head=git(ROOT,'rev-parse','HEAD').decode().strip(),before={n:pin(raw) for n,raw in before.items()},
        qualified_dispatcher=pin(current),preceding_edger_dispatcher=pin(prior),previous_manifest=pin((ROOT/m.DATA/'SHA256SUMS').read_bytes()))

def retained(before,current,receipt):
    prior=dispatcher_before(current);proof=json.loads((ROOT/OLD_PROOF).read_bytes())
    require(proof['after']=={n:pin(raw) for n,raw in before.items()},'Before is not the retained admitted edgeR catalog')
    require(proof['dispatcher_after']==pin(prior),'Qualified global GSEA inverse does not recover admitted edgeR dispatcher')
    try:git(ROOT,'cat-file','-e',HISTORICAL_HEAD+'^{commit}')
    except subprocess.CalledProcessError as error:raise FileNotFoundError('Restore exact historical Git commit '+HISTORICAL_HEAD+' with a permitted fetch.') from error
    changed=set(git(ROOT,'diff','--no-ext-diff','--name-only','--diff-filter=MDRT',HISTORICAL_HEAD,'--','src','tools/literature').decode().splitlines())
    require(changed<={DISPATCH,m.META[5]},'Tracked preceding scientific/control source changed: '+', '.join(sorted(changed-{DISPATCH,m.META[5]})))
    for n,w in proof['source_code'].items():require(pin((ROOT/n).read_bytes())==w,'Preceding owner source changed: '+n)
    for n,w in proof['evidence'].items():require(pin((ROOT/n).read_bytes())==w,'Preceding owner evidence changed: '+n)
    require(receipt['status']=='PASS_UNCHANGED_EDGER_OWNER_IN_EXACT_ADMITTED_CHECKOUT' and receipt['historical_head']==HISTORICAL_HEAD
        and receipt['exit_code']==0 and receipt['source_overlay_used'] is False and receipt['working_tree_unchanged'] is True,'Unchanged admitted predecessor execution required')
    completed=receipt['completed'];require(completed['status']==proof['status'] and completed['result']==proof['result'] and completed['current_manifest']['entries']==823,'Predecessor verification result differs')
    return dict(status='PASS_EXACT_BEFORE_CATALOG_UNCHANGED_EDGER_OWNER_AND_THREE_LINE_GLOBAL_TRANSITION',old_proof=pin((ROOT/OLD_PROOF).read_bytes()),
        old_owner_sources=len(proof['source_code']),old_evidence_files=len(proof['evidence']),metadata_files=8,manifest_entries=823,
        unchanged_old_owner_receipt=pin(m.encoded(receipt)),no_old_reader_source_overlay=True,no_historical_model_refit_claim=True)

def run_predecessor(historical,before,out,primary_cache):
    historical=historical.resolve();require(historical!=ROOT.resolve(),'Use the separate exact unchanged predecessor checkout')
    if not historical.is_dir():raise FileNotFoundError('Supply the exact permitted admitted edgeR checkout at '+HISTORICAL_HEAD)
    require(git(historical,'rev-parse','HEAD').decode().strip()==HISTORICAL_HEAD,'Historical checkout must be the exact admitted edgeR checkpoint')
    status=git(historical,'status','--porcelain=v1','-z','--untracked-files=all');require(not status,'Preserve historical unrelated changes; use a clean exact checkout')
    for n,raw in before.items():require((historical/n).read_bytes()==raw,'Historical canonical metadata differs: '+n)
    current=(ROOT/DISPATCH).read_bytes();require(dispatcher_before(current)==(historical/DISPATCH).read_bytes(),'Historical dispatcher does not match qualified inverse')
    oldout=historical/'build'/('gsea-admission-predecessor-'+uuid.uuid4().hex)
    command=[sys.executable,'-B',str(historical/'tools/literature/close_edger_catalog.py'),'--verify','--primary-cache',str(primary_cache),'--out',str(oldout)]
    process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,capture_output=True,timeout=600)
    (out/'predecessor.stdout').write_bytes(process.stdout);(out/'predecessor.stderr').write_bytes(process.stderr)
    (out/'PREDECESSOR-PROCESS.json').write_bytes(m.encoded(dict(command=command,exit_code=process.returncode,evidence_output=str(oldout))))
    require(process.returncode==0,'Unchanged predecessor verification failed; inspect retained logs/NEW output')
    require(git(historical,'status','--porcelain=v1','-z','--untracked-files=all')==status and git(historical,'rev-parse','HEAD').decode().strip()==HISTORICAL_HEAD,'Historical tracked state changed')
    receipt=dict(status='PASS_UNCHANGED_EDGER_OWNER_IN_EXACT_ADMITTED_CHECKOUT',historical_head=HISTORICAL_HEAD,command=command,exit_code=0,evidence_output=str(oldout),
        source_overlay_used=False,working_tree_unchanged=True,completed=json.loads((oldout/'COMPLETED.json').read_bytes()))
    retained(before,current,receipt);return receipt
