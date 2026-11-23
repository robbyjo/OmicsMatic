from pathlib import Path
import datetime,hashlib,json,subprocess,sys,urllib.request
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261122-mrpresso-intake-153';B=T/'build/mrpresso-1'
BASE='e763d9ce33e8f8f2ef7dc6fd7bf0d8fc3c885e51'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==BASE
assert not git('status','--porcelain=v1','-uall',cwd=T).strip()
assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==BASE
O.mkdir();B.mkdir();save(O/'WORKTREES-BEFORE.json',snapshot())
indexes={}
for row in snapshot():
    cwd=Path(row['worktree']);path=Path(git('rev-parse','--git-path','index',cwd=cwd).decode().strip());path=path if path.is_absolute() else cwd/path
    indexes[row['worktree']]=dict(path=str(path.resolve()),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes);git('switch','-c','codex/mrpresso-workflows-20261122',cwd=T)
D=B/'upstream-intake-1';D.mkdir();source=D/'source';source.mkdir()
def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-bound-MRPRESSO-intake','Cache-Control':'no-cache'}),timeout=45) as response:
        raw=response.read(16*1024*1024+1);assert len(raw)<=16*1024*1024;return raw
commit_raw=fetch('https://api.github.com/repos/rondolab/MR-PRESSO/commits/master');(D/'COMMIT.json').write_bytes(commit_raw)
commit=json.loads(commit_raw)['sha'];assert len(commit)==40
tree_raw=fetch('https://api.github.com/repos/rondolab/MR-PRESSO/git/trees/'+commit+'?recursive=1');(D/'TREE.json').write_bytes(tree_raw);tree=json.loads(tree_raw)
assert not tree.get('truncated');wanted=[e for e in tree['tree'] if e['type']=='blob' and (e['path'] in ('DESCRIPTION','NAMESPACE','README.md','LICENSE','LICENSE.txt','COPYING') or e['path'].startswith(('R/','man/')))]
files={}
for row in wanted:
    name=row['path'];assert not Path(name).is_absolute() and '..' not in Path(name).parts
    raw=fetch('https://raw.githubusercontent.com/rondolab/MR-PRESSO/'+commit+'/'+name)
    assert row['size']==len(raw) and hashlib.sha1(('blob '+str(len(raw))+'\0').encode()+raw).hexdigest()==row['sha']
    path=source/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);files[name]=pin(path)
assert b'License: GPL-3' in (source/'DESCRIPTION').read_bytes() and b'Version: 1.0' in (source/'DESCRIPTION').read_bytes()
assert 'R/mr_presso.R' in files
result=dict(status='PINNED_ORIGINAL_MRPRESSO_SOURCE_NOT_YET_QUALIFIED',repository='https://github.com/rondolab/MR-PRESSO',commit=commit,
    checked_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),files=files,package_version='1.0',license='GPL-3',
    source_selection='R implementation, package metadata, manual and README only; no publication FullResults or bundled study data downloaded.',
    paper_edition_established=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
save(D/'STATUS.json',result)
for label,exe in [('windows','C:/Program Files/R/R-4.6.1/bin/Rscript.exe')]:
    p=subprocess.run([exe,'--vanilla','-e','cat(R.version.string,"\\n");cat(paste(RNGkind(),collapse="/"),"\\n");cat(R.home(),"\\n");cat(requireNamespace("jsonlite",quietly=TRUE),"\\n")'],cwd=T,capture_output=True,timeout=30)
    (D/(label+'-runtime.stdout')).write_bytes(p.stdout);(D/(label+'-runtime.stderr')).write_bytes(p.stderr);assert p.returncode==0,p.stderr
save(O/'SOURCE-INTAKE.json',result)
print(json.dumps(dict(status=result['status'],commit=commit,files=len(files),source_bytes=sum(v['bytes'] for v in files.values()))),flush=True)
