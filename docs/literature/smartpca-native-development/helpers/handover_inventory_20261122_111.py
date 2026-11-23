from pathlib import Path
import hashlib,json,os,stat,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/sk-1';O=R/'build/git-publish/20261122-handover-111'
O.mkdir(exist_ok=True)
def git(*args,cwd=R):
 p=subprocess.run(['git',*args],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if p.returncode:raise RuntimeError((args,p.stderr.decode(errors='replace')))
 return p.stdout
def save(path,obj):path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def pin(p):
 s=p.lstat()
 if stat.S_ISLNK(s.st_mode):return {'type':'symlink','target':os.readlink(p)}
 if not stat.S_ISREG(s.st_mode):return {'type':'nonregular','mode':s.st_mode}
 with p.open('rb') as f:return {'bytes':s.st_size,'sha256':hashlib.file_digest(f,'sha256').hexdigest()}
def entries(cwd):
 raw=git('status','--porcelain=v1','-z','-uall',cwd=cwd);items=raw.split(b'\0');out=[];i=0
 while i<len(items) and items[i]:
  item=items[i];code=item[:2].decode();name=item[3:].decode('utf-8');row={'status':code,'path':name};i+=1
  if 'R' in code or 'C' in code:row['previous_path']=items[i].decode('utf-8');i+=1
  p=Path(cwd)/name;row['content']=pin(p) if p.exists() or p.is_symlink() else None;out.append(row)
 return out
def snapshot():
 blocks=git('worktree','list','--porcelain').decode('utf-8').strip().split('\n\n');out=[]
 for block in blocks:
  d=dict(line.split(' ',1) if ' ' in line else (line,True) for line in block.splitlines());cwd=Path(d['worktree']);d['status']=entries(cwd);out.append(d)
 return out
if __name__=='__main__':
 snap=snapshot();save(O/'WORKTREES-BEFORE.json',snap)
 refs=git('for-each-ref','--format=%(refname) %(objectname)','refs/heads').decode().splitlines();missing=[]
 for row in refs:
  ref,sha=row.split();p=subprocess.run(['git','merge-base','--is-ancestor',sha,'refs/remotes/origin/main'],cwd=R,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
  if p.returncode:missing.append({'ref':ref,'sha':sha,'code':p.returncode})
 save(O/'BRANCH-REACHABILITY.json',{'main':git('rev-parse','refs/remotes/origin/main').decode().strip(),'not_reachable':missing,'local_branches':len(refs)})
 root=next(x for x in snap if Path(x['worktree'])==R);different=[];equal=[];excluded=[]
 for row in root['status']:
  name=row['path'];p=R/name
  if name.startswith('output/'):
   excluded.append(row);continue
  expected=subprocess.run(['git','show','refs/remotes/origin/main:'+name],cwd=R,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  if expected.returncode==0 and p.is_file() and p.read_bytes()==expected.stdout:equal.append(row)
  else:different.append(row)
 save(O/'ROOT-SNAPSHOT-PLAN.json',{'main':git('rev-parse','refs/remotes/origin/main').decode().strip(),'root_head':root['HEAD'],'unchanged_from_current_main':equal,'different_from_current_main':different,'excluded_private_generated_outputs':excluded})
 print(json.dumps({'worktrees':len(snap),'dirty':[(x['worktree'],len(x['status'])) for x in snap if x['status']],'root_equal_main':len(equal),'root_different_main':len(different),'excluded_output':len(excluded),'excluded_output_bytes':sum(x['content'].get('bytes',0) for x in excluded),'root_different_paths':[(x['path'],x['content'].get('bytes',0)) for x in different],'not_reachable':missing},indent=2))
