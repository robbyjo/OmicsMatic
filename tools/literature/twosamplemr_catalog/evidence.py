"""Exact catalog predecessor and genuine unchanged global-owner qualification."""
from pathlib import Path
import json,os,subprocess,sys,uuid,zipfile
from twosamplemr_catalog import primary as p,model as m
from twosamplemr_dispatch_transition import DISPATCH,dispatcher_before

ROOT=Path(__file__).resolve().parents[3]
BASE='59be97062b0773fb9aad75038651cebdc0ea52c1'
CATALOG_OWNER='ac75e6958d7cf4cf6ec6372ec0548e1dcb1dcee1'
FOLDER='docs/literature/twosamplemr-catalog-admission'
PROOF=m.DATA+'/twosamplemr-catalog-validation.json'
COMPAT=m.DATA+'/twosamplemr-catalog-shared-source-compatibility.json'
OLD_PROOF=m.DATA+'/peer-catalog-validation.json'
CONTROLS='docs/literature/twosamplemr-catalog-controls'
OWN=['tools/literature/twosamplemr_catalog/'+n for n in ['__init__.py','primary.py','model.py','evidence.py','catalog.py','test_model.py','test_transaction.py','ADMISSION.md']]
OWN+=['tools/literature/close_twosamplemr_catalog.py','tools/literature/check_twosamplemr_catalog_ui.cjs',m.GUIDE]
OWNER=None
require=p.require
pin=p.pin

def configure_owner(path):
    global OWNER
    OWNER=Path(path).resolve() if path is not None else None

def git(root,*args):return subprocess.check_output([os.environ.get('TWOSAMPLEMR_CATALOG_GIT','git'),'--no-replace-objects',*args],cwd=root)
def sources():return {n:pin((ROOT/n).read_bytes()) for n in OWN}

def qualified_owner():
    owner=OWNER if OWNER is not None else ROOT.parent/'mr-8'
    if not owner.is_dir():raise FileNotFoundError('Restore a separate exact clean checkout of '+BASE+' and pass --historical-checkout; no old metadata/source/hash overlay is used. Missing: '+str(owner))
    require(owner.resolve()!=ROOT.resolve() and git(owner,'rev-parse','HEAD').decode().strip()==BASE,'Exact separate qualified global owner required')
    require(not git(owner,'status','--porcelain=v1','-z','--untracked-files=all'),'Preserve dirty owners; choose exact clean qualified owner')
    return owner

def qualification():
    owner=qualified_owner();folder='docs/literature/twosamplemr-global-checkpoint';profile=json.loads((owner/folder/'PROFILE.json').read_bytes())
    require((ROOT/folder/'PROFILE.json').read_bytes()==(owner/folder/'PROFILE.json').read_bytes(),'Changed saved global owner profile')
    for n,w in profile['source_inputs'].items():require(pin((ROOT/n).read_bytes())==w and (ROOT/n).read_bytes()==(owner/n).read_bytes(),'Changed qualified scientific/global source: '+n)
    # The old reader runs against the real, unchanged old checkout. Its canonical
    # files remain before admission; current after-state is checked by our model.
    from verify_twosamplemr_global_checkpoint import verify
    result=verify(owner);require(result['packaged_tests_per_JVM']==858 and result['global_profiles']==7 and result['source_roles']==14,'Different global qualification')
    files=[folder+'/'+n for n in ['PROFILE.json','INVENTORY.json','checks.zip','OMITTED.json']]
    files+=['tools/literature/'+n for n in ['verify_twosamplemr_global_checkpoint.py','twosamplemr_dispatch_transition.py','twosamplemr_global_context.py','twosamplemr_primary.py','twosamplemr_source_roles.py','check_twosamplemr_sources.py']]
    files+=[p.CENSUS,p.ATTRIBUTION]
    return dict(result=result,proof_files={n:pin((ROOT/n).read_bytes()) for n in files},qualified_owner_commit=BASE,current_sources_match_qualified_owner=True,old_reader_executed_in_real_unchanged_checkout=True,old_reader_overlay_used=False,old_hashes_refreshed=False,fresh_Java_R_or_model_execution=False)

def admission_qualification():
    result=qualification();controls=json.loads((ROOT/CONTROLS/'CONTROLS.json').read_bytes())
    require(controls['status']=='PASS_TWOSAMPLEMR_SOURCE_MODEL_TRANSACTION_CONTROLS_TWO_HOSTS' and controls['source_code']==sources(),'Missing/changed exact-source controls')
    require(set(controls['results'])=={'windows','linux'} and all(v['exit_code']==0 and v['tests']==30 and v['fresh_source_controls']==16 for v in controls['results'].values()),'Incomplete control hosts')
    path=ROOT/CONTROLS/'checks.zip';require(pin(path.read_bytes())==controls['archive'],'Changed control archive')
    with zipfile.ZipFile(path) as z:
        require(len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(controls['files']),'Changed control population')
        for n,w in controls['files'].items():require(pin(z.read(n))==w,'Changed control member')
    result['controls']=controls;result['proof_files'].update({CONTROLS+'/'+n:pin((ROOT/CONTROLS/n).read_bytes()) for n in ['CONTROLS.json','checks.zip']});return result

def baseline(before):
    require(set(before)==set(m.META),'Exactly eight before metadata files required')
    for n,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+n),'Different exact catalog predecessor: '+n)
    current=(ROOT/DISPATCH).read_bytes();require(current==git(ROOT,'show',BASE+':'+DISPATCH),'Changed qualified global dispatcher');dispatcher_before(current)
    require(not git(ROOT,'diff','--name-only','--diff-filter=MDRT',BASE,'--','src').decode().splitlines(),'Changed qualified Java/scientific source')
    return dict(base_commit=BASE,prepared_head=git(ROOT,'rev-parse','HEAD').decode().strip(),before={n:pin(v) for n,v in before.items()},dispatcher=pin(current),manifest=pin((ROOT/m.DATA/'SHA256SUMS').read_bytes()))

def retained(before,current,receipt):
    from peer_catalog import model as previous,primary as primary,evidence as evidence
    from peer_catalog.catalog import read_before
    proof=json.loads((ROOT/OLD_PROOF).read_bytes());require(proof['after']=={n:pin(raw) for n,raw in before.items()},'Different retained PEER metadata')
    require(proof['qualified_dispatcher']==pin(dispatcher_before(current)),'Different retained PEER dispatcher inverse')
    for group in ['source_code','evidence']:
        for n,w in proof[group].items():require(pin((ROOT/n).read_bytes())==w,'Changed preceding source/evidence: '+n)
    result=previous.check(read_before(ROOT/evidence.FOLDER/'metadata-before.zip'),before,primary.frozen(),proof['qualification']['result']);require(result==proof['result'],'Different retained pure PEER model')
    require(receipt['status']=='PASS_UNCHANGED_GLOBAL_TWOSAMPLEMR_AND_PRECEDING_PEER_OWNERS' and receipt['historical_head']==BASE and receipt['working_tree_unchanged'] and not receipt['source_overlay_used'] and all(v['exit_code']==0 for v in receipt['processes'].values()),'Unchanged qualified-owner execution required')
    context=receipt['context'];require(context['historical_head']==CATALOG_OWNER and context['historical_status_unchanged'] and context['fresh_all250_preceding_source_audit'] and not context['source_overlay_used'] and not context['old_hashes_refreshed'] and context['current']['current_pure_model_check']==result,'Different preceding catalog context')
    require(receipt['qualification']==qualification()['result'],'Different historical/current global qualification')
    return dict(status='PASS_EXACT_PEER_METADATA_AND_UNCHANGED_TWOSAMPLEMR_GLOBAL_OWNER',old_proof=pin((ROOT/OLD_PROOF).read_bytes()),previous_source_files=len(proof['source_code']),previous_evidence_files=len(proof['evidence']),old_metadata_files=8,old_reviews_retained=1616,old_reader_overlay_used=False,old_hashes_refreshed=False,receipt=pin(p.encoded(receipt)))

def run_predecessor(historical,before,out,cache,code_cache):
    configure_owner(historical);historical=qualified_owner()
    for n,raw in before.items():require((historical/n).read_bytes()==raw,'Historical before metadata differs')
    require((ROOT/DISPATCH).read_bytes()==(historical/DISPATCH).read_bytes(),'Historical dispatcher differs')
    oldcatalog=historical.parent/'pr-2';require(git(oldcatalog,'rev-parse','HEAD').decode().strip()==CATALOG_OWNER,'Exact preceding PEER catalog owner required')
    dest=historical/'build'/('twosamplemr-catalog-predecessor-'+uuid.uuid4().hex)
    commands={'context':[sys.executable,'-B',str(historical/'tools/literature/twosamplemr_global_context.py'),'--out',str(dest),'--historical-checkout',str(oldcatalog),'--primary-cache',str(cache)],
        'global':[sys.executable,'-B',str(historical/'tools/literature/verify_twosamplemr_global_checkpoint.py'),'--out',str(dest)+'-global']};rows={}
    for name,command in commands.items():
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        rows[name]=dict(command=command,exit_code=process.returncode);require(process.returncode==0,'Unchanged preceding owner failed: '+name)
    require(not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Historical working tree changed')
    receipt=dict(status='PASS_UNCHANGED_GLOBAL_TWOSAMPLEMR_AND_PRECEDING_PEER_OWNERS',historical_head=BASE,processes=rows,working_tree_unchanged=True,source_overlay_used=False,context=json.loads((dest/'RESULT.json').read_bytes()),qualification=json.loads(Path(str(dest)+'-global/RESULT.json').read_bytes()))
    retained(before,(ROOT/DISPATCH).read_bytes(),receipt);(out/'PREDECESSOR-PROCESS.json').write_bytes(p.encoded(dict(processes=rows,evidence_output=str(dest))));return receipt
