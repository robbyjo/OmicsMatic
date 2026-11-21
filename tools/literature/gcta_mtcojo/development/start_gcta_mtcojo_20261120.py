from pathlib import Path
import importlib.util,json,subprocess
R=Path('D:/projects/JLinAlg');P=R/'build/git-publish/20261120-gcta-models-70';T=R/'build/gc-5';O=R/'build/gcta-mtcojo-start-20261120-1';BASE='e62e64302995a8672a89b8bbfbb7b80d7c48822c'
O.mkdir(exist_ok=False)
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=R)
def save(n,v):
    with (O/n).open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
assert json.loads((P/'PUBLISHED.json').read_bytes())['head']==BASE and git('ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
spec=importlib.util.spec_from_file_location('inventory',O.parent/'git-publish/20261115-all-main-3/inventory.py');inv=importlib.util.module_from_spec(spec);spec.loader.exec_module(inv)
previous=json.loads((P/'WORKTREES-EXPECTED.json').read_bytes());assert len(previous)==26
for row in previous:assert inv.snapshot(Path(row['root']))==row,row['root']
save('WORKTREES-BEFORE.json',previous)
command=['git','worktree','add','-b','codex/gcta-mtcojo-20261120',str(T),BASE]
with (O/'worktree.stdout').open('xb') as stdout,(O/'worktree.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=R,stdout=stdout,stderr=stderr)
assert p.returncode==0,'Inspect saved diagnostics before repeating a mutation'
for row in previous:assert inv.snapshot(Path(row['root']))==row,row['root']
assert not subprocess.check_output(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=T)
save('COMPLETED.json',dict(status='PASS_NEW_MTCOJO_WORKTREE',worktree=str(T),baseline=BASE,prior_worktrees_preserved=26,current_worktrees=27,prior_goal_turn_classification='progress: verified REML repair, original exports, process controls and GitHub main publication',goal_status='active',whole_goal_complete=False))
print('Created mtCOJO checkout;26 predecessor worktrees unchanged;27 total',flush=True)
