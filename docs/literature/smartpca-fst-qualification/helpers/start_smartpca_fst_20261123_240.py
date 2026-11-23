from pathlib import Path
from handover_inventory_20261122_111 import git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-4';O=R/'build/git-publish/20261123-smartpca-fst-240'
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='eb167472e9e71cc51483e338ee3b8d32ca7bdcd8' and not git('status','--porcelain=v1',cwd=T)
B.mkdir();O.mkdir();owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners);indexes={}
for owner in owners:
 path=Path(git('rev-parse','--git-path','index',cwd=owner['worktree']).decode().strip())
 if not path.is_absolute():path=Path(owner['worktree'])/path
 indexes[owner['worktree']]=dict(path=str(path),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes);git('switch','-c','codex/smartpca-fst-20261123',cwd=T)
save(O/'START.json',dict(previous_goal_turn='progress',base='eb167472e9e71cc51483e338ee3b8d32ca7bdcd8',new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print('Started source-specific smartpca FST; all owner snapshots retained.')
