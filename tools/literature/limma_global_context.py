"""Prove the unchanged preceding catalog and the additive limma dispatcher relationship."""
from pathlib import Path
import argparse,json,subprocess,sys,uuid
from limma_dispatch_transition import BASE,DISPATCH,dispatcher_before,pin,require
from gcta_catalog import primary as p,model as m,evidence as e
from gcta_dispatch_transition import dispatcher_before as before_gcta
from seurat_catalog.catalog import clean_manifest

ROOT=Path(__file__).resolve().parents[2]

def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=root)

def current_before():
    before={name:(ROOT/name).read_bytes() for name in m.META}
    for name,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+name),'Changed preceding canonical metadata: '+name)
    current=(ROOT/DISPATCH).read_bytes();old=dispatcher_before(current)
    require(old==git(ROOT,'show',BASE+':'+DISPATCH),'Different qualified preceding dispatcher')
    before_gcta(old)
    require(git(ROOT,'diff','--name-only',BASE,'--','src/main').decode().splitlines()==[DISPATCH],'Changed scientific source beyond the root route')
    qualified=json.loads((ROOT/'docs/literature/limma-workflow-checkpoint/CHECKS.json').read_bytes())
    for name,wanted in qualified['source_inputs'].items():
        if name.startswith(('src/main/','src/test/','tools/literature/limma_workflow/')):
            require(pin((ROOT/name).read_bytes())==wanted,'Changed qualified scientific/test/observer source: '+name)
    # Recompute the preceding deterministic metadata transition on current bytes.
    proof=json.loads((ROOT/e.PROOF).read_bytes());require(proof['source_code']==e.sources(),'Changed preceding catalog source')
    for name,wanted in proof['evidence'].items():require(pin((ROOT/name).read_bytes())==wanted,'Changed preceding catalog evidence')
    old_before=e.ROOT/e.FOLDER/'metadata-before.zip'
    from gcta_catalog.catalog import read_before
    result=m.check(read_before(old_before),before,p.frozen(),proof['qualification']['result'])
    require(result['integrations']==141 and result['reviewed_uses']==1575 and result['pending_uses']==1140,'Changed preceding scope/counts')
    manifest=clean_manifest();require(manifest['entries']==829,'Different preceding manifest')
    return dict(status='PASS_CURRENT_PRECEDING_METADATA_AND_EXACT_LIMMA_GCTA_SOURCE_INVERSES',catalog_files={n:pin(v) for n,v in before.items()},dispatcher_before=pin(old),dispatcher_after=pin(current),current_pure_model_check=result,current_manifest=manifest,old_reader_overlay_used=False,old_hashes_refreshed=False)

def execute(out,historical,cache):
    current=current_before();historical=historical.resolve()
    require(historical!=ROOT and git(historical,'rev-parse','HEAD').decode().strip()==BASE and not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Use the separate clean preceding owner; preserve dirty work')
    status=git(historical,'status','--porcelain=v1','-z','--untracked-files=all')
    for name,wanted in current['catalog_files'].items():require(pin((historical/name).read_bytes())==wanted,'Historical canonical data differs')
    require((historical/DISPATCH).read_bytes()==dispatcher_before((ROOT/DISPATCH).read_bytes()),'Different exact historical dispatcher')
    dest=historical/'build'/('limma-global-predecessor-'+uuid.uuid4().hex)
    commands={
      'catalog':[sys.executable,'-B',str(historical/'tools/literature/close_gcta_catalog.py'),'--verify','--primary-cache',str(cache),'--out',str(dest)],
      'workflow':[sys.executable,'-B',str(historical/'tools/literature/verify_limma_workflow_checkpoint.py')],
    }
    rows={}
    for name,command in commands.items():
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        rows[name]=dict(command=command,exit_code=process.returncode);require(process.returncode==0,'Preceding owner failed: '+name)
    completed=json.loads((dest/'COMPLETED.json').read_bytes());require(completed['result']==current['current_pure_model_check'] and completed['fresh_source_audit_performed'],'Different fresh preceding catalog result')
    require(git(historical,'status','--porcelain=v1','-z','--untracked-files=all')==status,'Historical source/status changed')
    result=dict(status='PASS_CURRENT_SOURCE_INVERSE_AND_UNCHANGED_PRECEDING_CATALOG_WORKFLOW_OWNERS',current=current,processes=rows,historical_head=BASE,historical_root=str(historical),historical_new_evidence=str(dest),historical_status_unchanged=True,fresh_all250_preceding_source_audit=True,old_reader_overlay_used=False,old_hashes_refreshed=False,new_catalog_credit=0,whole_goal_complete=False)
    (out/'RESULT.json').write_bytes(p.encoded(result));return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--historical-checkout',type=Path,required=True);parser.add_argument('--primary-cache',type=Path,required=True);args=parser.parse_args()
    require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use new workspace build output');args.out.mkdir(parents=True,exist_ok=False)
    execute(args.out,args.historical_checkout,args.primary_cache);print('PASS exact limma route inverse,current preceding metadata,and unchanged original owners')

if __name__=='__main__':main()
