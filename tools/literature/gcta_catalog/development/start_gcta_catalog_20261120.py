from pathlib import Path
import importlib.util,json,subprocess
R=Path('D:/projects/JLinAlg');P=R/'build/git-publish/20261120-gcta-global-73';T=R/'build/gc-8';O=R/'build/gcta-catalog-start-20261120-1';BASE='aed274a43aacaec95c59ef5f66dc03e791ea39be';O.mkdir(exist_ok=False)
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=R)
def save(n,v):
    with (O/n).open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
assert json.loads((P/'PUBLISHED.json').read_bytes())['head']==BASE and git('ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
spec=importlib.util.spec_from_file_location('inventory',O.parent/'git-publish/20261115-all-main-3/inventory.py');inv=importlib.util.module_from_spec(spec);spec.loader.exec_module(inv)
previous=json.loads((P/'WORKTREES-EXPECTED.json').read_bytes());assert len(previous)==29
for row in previous:assert inv.snapshot(Path(row['root']))==row,row['root']
save('WORKTREES-BEFORE.json',previous)
command=['git','worktree','add','-b','codex/gcta-catalog-20261120',str(T),BASE]
with (O/'worktree.stdout').open('xb') as stdout,(O/'worktree.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=R,stdout=stdout,stderr=stderr)
assert p.returncode==0,'Inspect retained diagnostics before repeating a mutation'
for row in previous:assert inv.snapshot(Path(row['root']))==row,row['root']
assert not subprocess.check_output(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=T)
save('COMPLETED.json',dict(status='PASS_NEW_GCTA_CATALOG_WORKTREE',worktree=str(T),baseline=BASE,prior_worktrees_preserved=29,current_worktrees=30,prior_goal_turn_classification='progress: global GCTA route,27 tests per JVM,246 capture checks and expanded all250 source audit finding P169 and P090 role;verified main publication',goal_status='active',whole_goal_complete=False))
print('Created catalog checkout;29 predecessors unchanged;30 worktrees',flush=True)
