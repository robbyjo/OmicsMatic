"""Exact preceding metadata, unchanged historical owners and qualified limma bytes."""
from pathlib import Path
import json,subprocess,sys,uuid,zipfile
from limma_catalog import primary as p,model as m
from limma_dispatch_transition import DISPATCH,dispatcher_before

ROOT=Path(__file__).resolve().parents[3]
BASE='d0e615d97d6234161115dd9a974e63a164489e70'
WORKFLOW_OWNER='521b04bee9289b6113a0562d0d8a8366a0f9dbdf'
FOLDER='docs/literature/limma-catalog-admission'
PROOF=m.DATA+'/limma-catalog-validation.json'
COMPAT=m.DATA+'/limma-catalog-shared-source-compatibility.json'
OLD_PROOF=m.DATA+'/gcta-catalog-validation.json'
CONTROLS='docs/literature/limma-catalog-controls'
OWN=['tools/literature/limma_catalog/'+n for n in ['__init__.py','primary.py','model.py','evidence.py','catalog.py','test_model.py','test_transaction.py','ADMISSION.md']]
OWN+=['tools/literature/close_limma_catalog.py','tools/literature/check_limma_catalog_ui.cjs',m.GUIDE]
require=p.require
pin=p.pin

def git(root,*a):return subprocess.check_output(['git','--no-replace-objects',*a],cwd=root)

def sources():return {n:pin((ROOT/n).read_bytes()) for n in OWN}

def qualification():
    from verify_limma_global import verify
    result=verify(ROOT)
    require(result['saved_tests_per_JVM']==78 and result['JVMs']==3 and result['reviewed_source_labels']==23,'Different qualified limma scope')
    files=['docs/literature/limma-global-development/'+n for n in ['PROFILE.json','INVENTORY.json','evidence.zip','SOURCE-ROLES.json','SOURCE-CENSUS.json','AUTHOR-CODE-WITNESSES.json']]
    files+=['tools/literature/'+n for n in ['verify_limma_global.py','limma_dispatch_transition.py','limma_primary.py','limma_source_roles.py','check_limma_source_roles.py','limma_global_context.py']]
    return dict(result=result,proof_files={n:pin((ROOT/n).read_bytes()) for n in files},fresh_Java_R_or_model_execution=False)

def admission_qualification():
    result=qualification();controls=json.loads((ROOT/CONTROLS/'CONTROLS.json').read_bytes())
    require(controls['status']=='PASS_LIMMA_SOURCE_MODEL_TRANSACTION_CONTROLS_TWO_HOSTS' and controls['source_code']==sources(),'Changed or missing current-source controls')
    require(set(controls['results'])=={'windows','linux'} and all(r['exit_code']==0 and r['tests']==18 and r['fresh_source_controls']==15 for r in controls['results'].values()),'Incomplete control host')
    path=ROOT/CONTROLS/'checks.zip';require(pin(path.read_bytes())==controls['archive'],'Changed control archive')
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist())==len(set(archive.namelist())) and set(archive.namelist())==set(controls['files']),'Different control member population')
        for n,w in controls['files'].items():require(pin(archive.read(n))==w,'Changed control member')
    result['controls']=controls
    result['proof_files'].update({CONTROLS+'/'+n:pin((ROOT/CONTROLS/n).read_bytes()) for n in ['CONTROLS.json','checks.zip']})
    return result

def baseline(before):
    require(set(before)==set(m.META),'Exactly eight preceding metadata files required')
    for n,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+n),'Different exact catalog predecessor: '+n)
    current=(ROOT/DISPATCH).read_bytes();require(current==git(ROOT,'show',BASE+':'+DISPATCH),'Changed qualified dispatcher');dispatcher_before(current)
    require(not git(ROOT,'diff','--name-only','--diff-filter=MDRT',BASE,'--','src').decode().splitlines(),'Changed scientific/Java source')
    return dict(base_commit=BASE,prepared_head=git(ROOT,'rev-parse','HEAD').decode().strip(),before={n:pin(v) for n,v in before.items()},dispatcher=pin(current),manifest=pin((ROOT/m.DATA/'SHA256SUMS').read_bytes()))

def retained(before,current,receipt):
    from gcta_catalog import model as oldmodel,primary as oldprimary,evidence as oldevidence
    from gcta_catalog.catalog import read_before
    proof=json.loads((ROOT/OLD_PROOF).read_bytes())
    require(proof['after']=={n:pin(raw) for n,raw in before.items()},'Different retained GCTA metadata')
    prior=dispatcher_before(current)
    require(proof['qualified_dispatcher']==pin(prior),'Different retained qualified dispatcher inverse')
    for group in ['source_code','evidence']:
        for n,w in proof[group].items():require(pin((ROOT/n).read_bytes())==w,'Changed preceding owner source/evidence: '+n)
    result=oldmodel.check(read_before(ROOT/oldevidence.FOLDER/'metadata-before.zip'),before,oldprimary.frozen(),proof['qualification']['result'])
    require(result==proof['result'],'Changed preceding pure catalog model')
    require(receipt['status']=='PASS_UNCHANGED_GLOBAL_LIMMA_AND_PRECEDING_CATALOG_OWNERS' and receipt['historical_head']==BASE
        and receipt['working_tree_unchanged'] and not receipt['source_overlay_used'] and all(r['exit_code']==0 for r in receipt['processes'].values()),'Unchanged preceding-owner execution required')
    context=receipt['context']
    require(context['historical_head']==WORKFLOW_OWNER and context['historical_status_unchanged'] and context['fresh_all250_preceding_source_audit']
        and not context['old_reader_overlay_used'] and not context['old_hashes_refreshed'] and context['current']['current_pure_model_check']==result,'Different retained exact preceding context')
    require(receipt['qualification']==qualification()['result'],'Different historical/current qualified limma bytes')
    return dict(status='PASS_EXACT_PRECEDING_GCTA_METADATA_AND_UNCHANGED_GLOBAL_WORKFLOW_OWNERS',old_proof=pin((ROOT/OLD_PROOF).read_bytes()),
        previous_source_files=len(proof['source_code']),previous_evidence_files=len(proof['evidence']),old_metadata_files=8,old_reviews_retained=1575,
        old_reader_overlay_used=False,old_hashes_refreshed=False,receipt=pin(p.encoded(receipt)))

def run_predecessor(historical,before,out,cache):
    historical=historical.resolve()
    require(historical!=ROOT and historical.is_dir(),'Use the separate unchanged global limma owner')
    require(git(historical,'rev-parse','HEAD').decode().strip()==BASE and not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Preserve dirty work; use the exact clean preceding owner')
    for n,raw in before.items():require((historical/n).read_bytes()==raw,'Historical canonical data differs')
    require((ROOT/DISPATCH).read_bytes()==(historical/DISPATCH).read_bytes(),'Historical dispatcher differs')
    workflow=historical.parent/'lm-4'
    require(git(workflow,'rev-parse','HEAD').decode().strip()==WORKFLOW_OWNER,'Exact preceding workflow/catalog source owner is required')
    dest=historical/'build'/('limma-catalog-predecessor-'+uuid.uuid4().hex)
    commands={
        'context':[sys.executable,'-B',str(historical/'tools/literature/limma_global_context.py'),'--out',str(dest),'--historical-checkout',str(workflow),'--primary-cache',str(cache)],
        'global':[sys.executable,'-B',str(historical/'tools/literature/verify_limma_global.py'),'--out',str(dest)+'-global'],
    }
    rows={}
    for name,command in commands.items():
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        rows[name]=dict(command=command,exit_code=process.returncode);require(process.returncode==0,'Preceding owner failed: '+name)
    require(not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Historical working tree changed')
    receipt=dict(status='PASS_UNCHANGED_GLOBAL_LIMMA_AND_PRECEDING_CATALOG_OWNERS',historical_head=BASE,processes=rows,
        working_tree_unchanged=True,source_overlay_used=False,context=json.loads((dest/'RESULT.json').read_bytes()),
        qualification=json.loads(Path(str(dest)+'-global/RESULT.json').read_bytes()))
    retained(before,(ROOT/DISPATCH).read_bytes(),receipt)
    (out/'PREDECESSOR-PROCESS.json').write_bytes(p.encoded(dict(processes=rows,evidence_output=str(dest))))
    return receipt
