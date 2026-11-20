"""Run the unchanged Scanpy owner in its exact checkout; read saved SuSiE proof."""
from pathlib import Path
import json
import os
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
import zipfile
from scanpy_catalog import catalog as previous
from scanpy_catalog import evidence as old
from susie_catalog.model import META,DATA
from susie_catalog.coverage import encoded,require
from susie_source_roles import MissingSource
import susie_source_roles

ROOT=Path(__file__).resolve().parents[3]
BASE='0e20bb5017303382a78e041ff8810139232e8336'
SCANPY_STAGE=BASE
pin,rawpin,git=old.pin,old.rawpin,old.git
WORKFLOW='docs/literature/susie-workflow-checks'
FOUNDATION=sorted(set(old.FOUNDATION+previous.OWN+[
    'tools/literature/verify_scanpy_catalog.py','tools/literature/susie_source_roles.py',
    'tools/literature/verify_susie_workflow.py',WORKFLOW+'/QUALIFICATION.json',WORKFLOW+'/evidence.zip',
    WORKFLOW+'/CENSUS.json',WORKFLOW+'/READBACK.json']))


def baseline():
    before={n:git('show',BASE+':'+n) for n in META}
    proof=json.loads(git('show',BASE+':'+previous.PROOF))
    require(proof['status']==previous.STATUS and proof['after']=={n:rawpin(b) for n,b in before.items()},
            'Actual Git baseline differs from completed Scanpy admission')
    return before


def predecessor(cache,pdf,author,before,out,checkout,enrichr_checkout,coloc_checkout):
    for root,label,commit in [(checkout,'Scanpy',SCANPY_STAGE),(enrichr_checkout,'Enrichr',old.ENRICHR_STAGE),
                              (coloc_checkout,'COLOC',old.old.COLOC_STAGE)]:
        if root is None or not Path(root).is_dir():raise MissingSource('Restore separate exact clean '+label+' checkout at '+commit)
    checkout=Path(checkout).resolve();out=Path(out);out.mkdir(parents=True,exist_ok=False)
    path=ROOT/previous.PROOF
    require(path.read_bytes()==git('show',BASE+':'+previous.PROOF),'Original Scanpy proof changed')
    proof=json.loads(path.read_bytes())
    require(proof['after']=={n:rawpin(b) for n,b in before.items()},'Scanpy after-stage differs')
    require(proof['source_code']=={n:pin(ROOT/n) for n in previous.OWN}
        and proof['foundation_sources']==previous.foundation(),'Scanpy source foundation differs')
    for n,w in proof['evidence'].items():require(pin(ROOT/n)==w,'Changed retained Scanpy evidence: '+n)
    def historical(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=checkout)
    require(historical('rev-parse','HEAD').decode().strip()==SCANPY_STAGE,'Wrong historical Scanpy commit')
    require(not historical('status','--porcelain=v1','-z','--untracked-files=all'),'Historical Scanpy checkout must be clean')
    for n,b in before.items():require((checkout/n).read_bytes()==b,'Historical metadata differs: '+n)
    for n in previous.OWN:require((checkout/n).read_bytes()==git('show',SCANPY_STAGE+':'+n),'Changed historical Scanpy owner: '+n)
    target=checkout/'build'/('susie-original-scanpy-owner-'+uuid.uuid4().hex)
    args=[sys.executable,'-B','-m','scanpy_catalog.catalog','--verify','--primary-cache',str(Path(cache).resolve()),
        '--supplement-pdf',str(Path(pdf).resolve()),'--author-archive',str(Path(author).resolve()),
        '--historical-checkout',str(Path(enrichr_checkout).resolve()),'--coloc-checkout',str(Path(coloc_checkout).resolve()),'--out',str(target)]
    process=subprocess.run(args,cwd=checkout,env=dict(os.environ,PYTHONPATH=str(checkout/'tools/literature')),capture_output=True)
    (out/'original.stdout').write_bytes(process.stdout);(out/'original.stderr').write_bytes(process.stderr)
    (out/'PROCESS.json').write_bytes(encoded(dict(exit_code=process.returncode,historical_commit=SCANPY_STAGE,
        original_module='scanpy_catalog.catalog',source_overlay=False)))
    if process.returncode==3:raise MissingSource('Historical original owner skipped a prerequisite: '+process.stderr.decode(errors='replace')[-3000:])
    require(process.returncode==0,'Original Scanpy owner failed; inspect retained original.stderr')
    result=json.loads((target/'COMPLETED.json').read_bytes())
    require(result['status']==previous.STATUS and result['new_scoped_integrations']==137
        and result['reviewed_uses']==1483 and result['current_manifest']['entries']==815,'Wrong original Scanpy disposition')
    require(not historical('status','--porcelain=v1','-z','--untracked-files=all'),'Historical Scanpy source changed during verification')
    (out/'ORIGINAL-COMPLETED.json').write_bytes(encoded(result))
    return dict(status='PASS_ORIGINAL_SCANPY_OWNER_IN_EXACT_CLEAN_HISTORICAL_CHECKOUT',historical_commit=SCANPY_STAGE,
        proof=pin(path),original_verifier_exit=0,original_result=result,historical_full_marker_audit=result['historical_full_marker_audit'],
        source_XML_identities_checked=250,current_metadata_before_stage_matched=True,original_scientific_programs_reexecuted=False,
        scientific_source_overlay_used=False,previous_fingerprints_refreshed=False)


def source_census(cache):
    path=ROOT/WORKFLOW/'CENSUS.json'
    require(path.read_bytes()==git('show',BASE+':'+WORKFLOW+'/CENSUS.json'),'Frozen SuSiE census changed')
    census=susie_source_roles.audit(cache)
    require(census==json.loads(path.read_bytes()),'Fresh 250-identity/20-role census differs')
    return census


def qualification():
    from verify_susie_workflow import verify as saved
    report=saved(ROOT);proof=json.loads((ROOT/WORKFLOW/'QUALIFICATION.json').read_bytes())
    with zipfile.ZipFile(ROOT/WORKFLOW/'evidence.zip') as z:
        index=json.loads(z.read('INDEX.json'))
        def receipt(name):return json.loads(z.read('blobs/'+index['receipts'][name]['sha256']))
        final=receipt('handover-final-checks-2/COMPLETED.json')
        comp=receipt('original-model-comparison-2/COMPLETED.json')
        process=receipt('process-controls-1/COMPLETED.json')
        require(final['normal_jar']=={k:proof['producer'][k] for k in ['bytes','sha256']}
            and final['normal1_to_normal2_changed_members']==['org/jlinalg/cli/SusieWorkflowCli.class']
            and final['replay_fields_exact_except_call'] is True and not final['replay_original_objects_modified']
            and not final['scientific_R_source_changed'],'Wrong producer or replay evidence')
        require(len(final['examples'])==5 and all(r['run_exit']==r['verify_exit']==0 for r in final['examples'])
            and {r['model'] for r in final['examples']}=={'INDIVIDUAL','RSS_Z','RSS_BETA_SE','SUFFICIENT','SUFFICIENT_BETA_SE'},'Wrong example qualification')
        require(comp['cases']==41 and all(r['passed'] and not r['differences'] and r['maximum_numeric_absolute_error']==0 for r in comp['models'].values()),'Original comparisons differ')
        require(process['wrong_version_exit']==3 and process['actual_models_replayed']==1 and process['synthetic_controls']==2,'Missing process/version controls')
        # The final repeat is a JVM saved-capture run, not Linux R fitting.
        suites=[]
        for name,wanted in index['receipts'].items():
            if name.startswith('handover-final-checks-2/reports-linux21/') and name.endswith('.xml'):
                suites.append(ET.fromstring(z.read('blobs/'+wanted['sha256'])))
        totals={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','errors','failures','skipped']}
        require(totals==dict(tests=73,errors=0,failures=0,skipped=0) and final['linux_retest_archived_before_process_exit'],
                'Missing immediately archived Linux JVM repeat')
    return dict(status='PASS_SUSIE_ORIGINAL_THREE_EDITION_WORKFLOW_SAVED_QUALIFICATION',normal_tests_per_JVM=73,
        original_model_cases=41,converged_accepted=38,nonconverged_refused=3,maximum_model_numeric_absolute_error=0,
        actual_replay_fields_exact_except_call=True,example_input_interfaces=5,global_dispatch_added=False,
        linux_R_scientific_runtime_qualified=False,author_runtime_executed=False,new_family_credit=0,
        readback=report,producer=proof['producer'],source_files=proof['source_files'],
        artifacts={p.relative_to(ROOT).as_posix():pin(p) for p in sorted((ROOT/WORKFLOW).iterdir()) if p.is_file()},
        fresh_JVM_execution=False,fresh_upstream_execution=False,whole_tool_qualified=False,whole_paper_reproduced=False)
