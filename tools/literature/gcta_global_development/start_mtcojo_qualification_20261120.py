from pathlib import Path
import importlib.util,json,subprocess
R=Path('D:/projects/JLinAlg');P=R/'build/git-publish/20261120-mtcojo-handover-71';T=R/'build/gc-6';O=R/'build/mtcojo-qualification-start-20261120-1';BASE='46611ae55e9b0778df6a117fd8be76ae468c27b5'
O.mkdir(exist_ok=False)
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=R)
def save(n,v):
    with (O/n).open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
assert json.loads((P/'PUBLISHED.json').read_bytes())['head']==BASE and git('ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
spec=importlib.util.spec_from_file_location('inventory',O.parent/'git-publish/20261115-all-main-3/inventory.py');inv=importlib.util.module_from_spec(spec);spec.loader.exec_module(inv)
previous=json.loads((P/'WORKTREES-EXPECTED.json').read_bytes());assert len(previous)==27
for row in previous:assert inv.snapshot(Path(row['root']))==row,row['root']
save('WORKTREES-BEFORE.json',previous)
command=['git','worktree','add','-b','codex/gcta-mtcojo-qualification-20261120',str(T),BASE]
with (O/'worktree.stdout').open('xb') as stdout,(O/'worktree.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=R,stdout=stdout,stderr=stderr)
assert p.returncode==0,'Inspect diagnostics before retrying a mutation'
for row in previous:assert inv.snapshot(Path(row['root']))==row,row['root']
assert not subprocess.check_output(['git','status','--porcelain=v1','-z','--untracked-files=all'],cwd=T)
save('COMPLETED.json',dict(status='PASS_NEW_MTCOJO_QUALIFICATION_WORKTREE',worktree=str(T),baseline=BASE,prior_worktrees_preserved=27,current_worktrees=28,prior_goal_turn_classification='progress: native/original mtCOJO development, 24 packaged tests on three JVMs, eight normal/original comparisons, handover and verified GitHub main publication',goal_status='active',whole_goal_complete=False))
print('Created qualification checkout;27 predecessors unchanged;28 total',flush=True)
