"""Prove the exact PEER route inverse and execute the unchanged preceding catalog owner."""
from pathlib import Path
import argparse,json,subprocess,sys,uuid
from peer_dispatch_transition import BASE,DISPATCH,dispatcher_before,pin,require
from limma_catalog import primary as p,model as m,evidence as e
from limma_catalog.catalog import read_before
from seurat_catalog.catalog import clean_manifest

ROOT=Path(__file__).resolve().parents[2]

def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=root)

def current_before():
    before={n:(ROOT/n).read_bytes() for n in m.META}
    for n,raw in before.items():require(raw==git(ROOT,'show',BASE+':'+n),'Changed preceding canonical bytes: '+n)
    current=(ROOT/DISPATCH).read_bytes();prior=dispatcher_before(current)
    require(prior==git(ROOT,'show',BASE+':'+DISPATCH),'Different exact qualified preceding dispatcher')
    require(git(ROOT,'diff','--name-only','--diff-filter=MDRT',BASE,'--','src/main').decode().splitlines()==[DISPATCH],'Changed old scientific source beyond the root route')
    packet=json.loads((ROOT/'docs/literature/limma-catalog-checkpoint/CHECKS.json').read_bytes())
    for n,w in packet['source_inputs'].items():require(pin(prior if n==DISPATCH else (ROOT/n).read_bytes())==w,'Changed preceding qualified source: '+n)
    proof=json.loads((ROOT/e.PROOF).read_bytes());require(proof['source_code']==e.sources(),'Changed preceding catalog source owner')
    for n,w in proof['evidence'].items():require(pin((ROOT/n).read_bytes())==w,'Changed preceding owner evidence: '+n)
    old=read_before(ROOT/e.FOLDER/'metadata-before.zip');result=m.check(old,before,p.frozen(),proof['qualification']['result'])
    require(result['integrations']==142 and result['reviewed_uses']==1598 and result['pending_uses']==1119,'Different preceding family/review counts')
    manifest=clean_manifest();require(manifest['entries']==832,'Different preceding manifest')
    return dict(status='PASS_PRECEDING_LIMMA_METADATA_AND_EXACT_PEER_ROUTE_INVERSE',catalog_files={n:pin(v) for n,v in before.items()},dispatcher_before=pin(prior),dispatcher_after=pin(current),current_pure_model_check=result,current_manifest=manifest,old_reader_overlay_used=False,old_hashes_refreshed=False)

def execute(out,historical,cache,code_cache):
    current=current_before();historical=historical.resolve()
    require(historical!=ROOT and git(historical,'rev-parse','HEAD').decode().strip()==BASE and not git(historical,'status','--porcelain=v1','-z','--untracked-files=all'),'Use the separate exact clean preceding owner; preserve dirty work')
    status=git(historical,'status','--porcelain=v1','-z','--untracked-files=all')
    for n,w in current['catalog_files'].items():require(pin((historical/n).read_bytes())==w,'Historical metadata differs')
    require((historical/DISPATCH).read_bytes()==dispatcher_before((ROOT/DISPATCH).read_bytes()),'Historical dispatcher differs')
    dest=historical/'build'/('peer-global-predecessor-'+uuid.uuid4().hex)
    commands={
        'catalog':[sys.executable,'-B',str(historical/'tools/literature/close_limma_catalog.py'),'--verify','--primary-cache',str(cache),'--author-code-cache',str(code_cache),'--out',str(dest)],
        'saved':[sys.executable,'-B',str(historical/'tools/literature/verify_limma_catalog_checkpoint.py'),'--out',str(dest)+'-saved'],
    };rows={}
    for name,command in commands.items():
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=historical,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        rows[name]=dict(command=command,exit_code=process.returncode);require(process.returncode==0,'Unchanged preceding owner failed: '+name)
    completed=json.loads((dest/'COMPLETED.json').read_bytes());require(completed['result']==current['current_pure_model_check'] and completed['fresh_source_audit_performed'],'Different fresh preceding catalog result')
    require(git(historical,'status','--porcelain=v1','-z','--untracked-files=all')==status,'Historical source/status changed')
    result=dict(status='PASS_PEER_SOURCE_INVERSE_AND_UNCHANGED_PRECEDING_LIMMA_OWNER',current=current,processes=rows,historical_head=BASE,historical_root=str(historical),historical_new_evidence=str(dest),historical_status_unchanged=True,fresh_all250_preceding_source_audit=True,old_reader_overlay_used=False,old_hashes_refreshed=False,new_catalog_credit=0,whole_goal_complete=False)
    (out/'RESULT.json').write_bytes(p.encoded(result));return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--historical-checkout',type=Path,required=True);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--author-code-cache',type=Path,required=True);args=parser.parse_args()
    require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False)
    execute(args.out,args.historical_checkout,args.primary_cache,args.author_code_cache);print('PASS exact PEER route inverse, preceding metadata and unchanged original owner')

if __name__=='__main__':main()
