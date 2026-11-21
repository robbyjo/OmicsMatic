"""Check retained historical dirty-file bytes without restoring or running analyses."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,subprocess,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def safe(name):
    p=PurePosixPath(name);require(not p.is_absolute() and '..' not in p.parts and '\\' not in name,'Unsafe relative snapshot path')
def verify(root):
    q=root/'docs/literature/handover-2026-11-21-worktrees';profile=json.loads((q/'PROFILE.json').read_bytes())
    require(profile['status']=='HISTORICAL_DIRTY_WORKTREE_SNAPSHOTS_NOT_MAIN_BUILD' and not profile['ignored_files_included'] and not profile['whole_goal_complete'] and profile['new_catalog_credit']==0,'Different preservation scope')
    require(pin((q/'WORKTREES.json').read_bytes())==profile['worktrees'] and pin((q/'deltas.zip').read_bytes())==profile['archive'],'Changed snapshots')
    states=json.loads((q/'WORKTREES.json').read_bytes());require(len(states)==40 and len({v['root'] for v in states})==40,'Different predecessor worktrees')
    base=profile['base'];require(len(base)==40 and all(c in '0123456789abcdef' for c in base),'Invalid base object')
    subprocess.run(['git','--no-replace-objects','cat-file','-e',base+'^{commit}'],cwd=root,check=True,stdout=subprocess.DEVNULL)
    wanted_blobs={};expected_archive={};files=0;from_git=0;from_archive=0
    for state in states:
        require(len(state['head'])==40 and isinstance(state['branch'],str),'Malformed historical head/branch')
        bytes.fromhex(state['status_hex'])
        for name,wanted in state['files'].items():
            safe(name)
            if wanted is None:continue
            files+=1;storage=wanted['storage'];expected={k:wanted[k] for k in ['bytes','sha256']}
            if storage['type']=='git-blob':
                oid=storage['oid'];require(len(oid)==40 and all(c in '0123456789abcdef' for c in oid),'Invalid referenced blob')
                require(oid not in wanted_blobs or wanted_blobs[oid]==expected,'Conflicting Git blob pins');wanted_blobs[oid]=expected;from_git+=1
            else:
                require(storage['type']=='archive' and storage['member']=='blobs/'+wanted['sha256'],'Different archive mapping');expected_archive[storage['member']]=expected;from_archive+=1
        patch=state['index_patch'];require(patch['member']=='blobs/'+patch['sha256'],'Unsafe staged patch');expected_archive[patch['member']]={k:patch[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(q/'deltas.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(expected_archive) and set(names)==set(expected_archive),'Different delta archive population')
        total=0
        for name,wanted in expected_archive.items():
            safe(name);size=archive.getinfo(name).file_size;total+=size
            require(size<=64*1024*1024 and total<=128*1024*1024,'Delta archive exceeds capacity');require(pin(archive.read(name))==wanted,'Changed historical delta')
    # Batch reads avoid one process per file and keep memory bounded to one blob.
    with subprocess.Popen(['git','--no-replace-objects','cat-file','--batch'],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE) as proc:
        try:
            for oid,wanted in sorted(wanted_blobs.items()):
                proc.stdin.write((oid+'\n').encode('ascii'));proc.stdin.flush();header=proc.stdout.readline().decode('ascii').strip().split()
                require(header==[oid,'blob',str(wanted['bytes'])],'Missing or different historical Git blob')
                remaining=wanted['bytes'];digest=hashlib.sha256()
                while remaining:
                    raw=proc.stdout.read(min(remaining,1024*1024));require(bool(raw),'Truncated Git blob');digest.update(raw);remaining-=len(raw)
                require(proc.stdout.read(1)==b'\n' and digest.hexdigest()==wanted['sha256'],'Changed base Git bytes')
            proc.stdin.close();require(proc.wait(timeout=30)==0,'Git blob reader failed')
        except BaseException:
            proc.kill();proc.wait();raise
    require(files==profile['git_visible_files'] and from_git==profile['files_from_base_git_blobs'] and from_archive==profile['files_from_delta_archive'] and len(expected_archive)==profile['unique_archive_blobs'],'Different snapshot counts')
    return dict(status=profile['status'],predecessor_worktrees=40,git_visible_files=files,files_from_base_git=from_git,files_from_delta_archive=from_archive,unique_delta_blobs=len(expected_archive),restored_files=0,scientific_analyses_run=0,ignored_files_included=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    result=verify(Path(__file__).resolve().parents[2]);a.out.mkdir(parents=True,exist_ok=False)
    (a.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
if __name__=='__main__':main()
