"""Exact preceding limma metadata and unchanged qualified PEER source owner."""
from pathlib import Path
import json,subprocess,sys,uuid,zipfile
from peer_catalog import primary as p,model as m
from peer_dispatch_transition import DISPATCH,dispatcher_before

ROOT=Path(__file__).resolve().parents[3]
BASE='7d51b1c1f8d1714bfa22c715423010baa71b6f18'
CATALOG_OWNER='c54d9186f74b92fc28c1250ce4b364771f8c708f'
FOLDER='docs/literature/peer-catalog-admission'
PROOF=m.DATA+'/peer-catalog-validation.json'
COMPAT=m.DATA+'/peer-catalog-shared-source-compatibility.json'
OLD_PROOF=m.DATA+'/limma-catalog-validation.json'
CONTROLS='docs/literature/peer-catalog-controls'
OWN=['tools/literature/peer_catalog/'+n for n in ['__init__.py','primary.py','model.py','evidence.py','catalog.py','test_model.py','test_transaction.py','ADMISSION.md']]
OWN+=['tools/literature/close_peer_catalog.py','tools/literature/check_peer_catalog_ui.cjs',m.GUIDE]
require=p.require
pin=p.pin

def git(root,*a):return subprocess.check_output(['git','--no-replace-objects',*a],cwd=root)
def sources():return {n:pin((ROOT/n).read_bytes()) for n in OWN}

def qualification():
    from verify_peer_workflow_checkpoint import verify
    result=verify(ROOT)
    require(result['packaged_tests_per_JVM']==26 and result['JVMs']==3 and result['source_reviewed_labels']==18,'Different qualified PEER scope')
    files=['docs/literature/peer-workflow-checkpoint/'+n for n in ['PROFILE.json','INVENTORY.json','checks.zip']]
    files+=['docs/literature/peer-source-review-proposal/CENSUS.json']
    files+=['tools/literature/'+n for n in ['verify_peer_workflow_checkpoint.py','peer_dispatch_transition.py','peer_primary.py','peer_source_roles.py','check_peer_sources.py','peer_global_context.py']]
    return dict(result=result,proof_files={n:pin((ROOT/n).read_bytes()) for n in files},fresh_Java_CPP_or_model_execution=False)

def admission_qualification():
    result=qualification();controls=json.loads((ROOT/CONTROLS/'CONTROLS.json').read_bytes())
    require(controls['status']=='PASS_PEER_SOURCE_MODEL_TRANSACTION_CONTROLS_TWO_HOSTS' and controls['source_code']==sources(),'Changed/missing current-source controls')
    require(set(controls['results'])=={'windows','linux'} and all(r['exit_code']==0 and r['tests']==22 and r['fresh_source_controls']==16 for r in controls['results'].values()),'Incomplete control host')
    path=ROOT/CONTROLS/'checks.zip';require(pin(path.read_bytes())==controls['archive'],'Changed control archive')
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist())==len(set(archive.namelist())) and set(archive.namelist())==set(controls['files']),'Different control member population')
        for n,w in controls['files'].items():require(pin(archive.read(n))==w,'Changed control member')
    result['controls']=controls;result['proof_files'].update({CONTROLS+'/'+n:pin((ROOT/CONTROLS/n).read_bytes()) for n in ['CONTROLS.json','checks.zip']})
    return result

def baseline(before):
    require(set(before)==set(m.META),'Exactly eight preceding metadata files required')
    for n,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+n),'Different exact catalog predecessor: '+n)
    current=(ROOT/DISPATCH).read_bytes();require(current==git(ROOT,'show',BASE+':'+DISPATCH),'Changed qualified PEER dispatcher');dispatcher_before(current)
    require(not git(ROOT,'diff','--name-only','--diff-filter=MDRT',BASE,'--','src').decode().splitlines(),'Changed scientific/Java source')
    return dict(base_commit=BASE,prepared_head=git(ROOT,'rev-parse','HEAD').decode().strip(),before={n:pin(v) for n,v in before.items()},dispatcher=pin(current),manifest=pin((ROOT/m.DATA/'SHA256SUMS').read_bytes()))

def retained(before,current,receipt):
    from limma_catalog import model as oldmodel,primary as oldprimary,evidence as oldevidence
    from limma_catalog.catalog import read_before
    proof=json.loads((ROOT/OLD_PROOF).read_bytes())
    require(proof['after']=={n:pin(raw) for n,raw in before.items()},'Different retained limma metadata')
    require(proof['qualified_dispatcher']==pin(dispatcher_before(current)),'Different retained dispatcher inverse')
    for group in ['source_code','evidence']:
        for n,w in proof[group].items():require(pin((ROOT/n).read_bytes())==w,'Changed preceding source/evidence: '+n)
    result=oldmodel.check(read_before(ROOT/oldevidence.FOLDER/'metadata-before.zip'),before,oldprimary.frozen(),proof['qualification']['result'])
    require(result==proof['result'],'Changed preceding pure limma catalog model')
    require(receipt['status']=='PASS_UNCHANGED_GLOBAL_PEER_AND_PRECEDING_LIMMA_OWNERS' and receipt['historical_head']==BASE
        and receipt['working_tree_unchanged'] and not receipt['source_overlay_used'] and all(r['exit_code']==0 for r in receipt['processes'].values()),'Unchanged preceding-owner execution required')
    context=receipt['context']
    require(context['historical_head']==CATALOG_OWNER and context['historical_status_unchanged'] and context['fresh_all250_preceding_source_audit']
        and not context['old_reader_overlay_used'] and not context['old_hashes_refreshed'] and context['current']['current_pure_model_check']==result,'Different exact preceding context')
    require(receipt['qualification']==qualification()['result'],'Different historical/current qualified PEER bytes')
    return dict(status='PASS_EXACT_PRECEDING_LIMMA_METADATA_AND_UNCHANGED_PEER_SOURCE_OWNER',old_proof=pin((ROOT/OLD_PROOF).read_bytes()),previous_source_files=len(proof['source_code']),previous_evidence_files=len(proof['evidence']),old_metadata_files=8,old_reviews_retained=1598,old_reader_overlay_used=False,old_hashes_refreshed=False,receipt=pin(p.encoded(receipt)))

def run_predecessor(historical,before,out,cache,code_cache):
    historical=historical.resolve()
    require(historical!=ROOT and historical.is_dir(),'Use the separate unchanged published PEER owner')
    require(git(historical,'rev-parse','HEAD').decode().strip()==BASE and not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Preserve dirty work; exact clean preceding owner required')
    for n,raw in before.items():require((historical/n).read_bytes()==raw,'Historical canonical data differs')
    require((ROOT/DISPATCH).read_bytes()==(historical/DISPATCH).read_bytes(),'Historical dispatcher differs')
    oldcatalog=historical.parent/'lm-6';require(git(oldcatalog,'rev-parse','HEAD').decode().strip()==CATALOG_OWNER,'Exact preceding limma catalog owner required')
    dest=historical/'build'/('peer-catalog-predecessor-'+uuid.uuid4().hex)
    commands={
        'context':[sys.executable,'-B',str(historical/'tools/literature/peer_global_context.py'),'--out',str(dest),'--historical-checkout',str(oldcatalog),'--primary-cache',str(cache),'--author-code-cache',str(code_cache)],
        'global':[sys.executable,'-B',str(historical/'tools/literature/verify_peer_workflow_checkpoint.py'),'--out',str(dest)+'-global'],
    };rows={}
    for name,command in commands.items():
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        rows[name]=dict(command=command,exit_code=process.returncode);require(process.returncode==0,'Unchanged preceding owner failed: '+name)
    require(not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Historical working tree changed')
    receipt=dict(status='PASS_UNCHANGED_GLOBAL_PEER_AND_PRECEDING_LIMMA_OWNERS',historical_head=BASE,processes=rows,working_tree_unchanged=True,source_overlay_used=False,context=json.loads((dest/'RESULT.json').read_bytes()),qualification=json.loads(Path(str(dest)+'-global/RESULT.json').read_bytes()))
    retained(before,(ROOT/DISPATCH).read_bytes(),receipt)
    (out/'PREDECESSOR-PROCESS.json').write_bytes(p.encoded(dict(processes=rows,evidence_output=str(dest))))
    return receipt
