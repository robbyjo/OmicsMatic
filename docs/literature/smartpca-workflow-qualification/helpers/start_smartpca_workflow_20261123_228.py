from pathlib import Path
from handover_inventory_20261122_111 import git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-3';O=R/'build/git-publish/20261123-smartpca-workflow-228'
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='05e2d615f7b1c6c791d2ed29ff3e45fa4517c5ff' and not git('status','--porcelain=v1',cwd=T)
B.mkdir();O.mkdir();owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners);indexes={}
for owner in owners:
 path=Path(git('rev-parse','--git-path','index',cwd=owner['worktree']).decode().strip())
 if not path.is_absolute():path=Path(owner['worktree'])/path
 indexes[owner['worktree']]=dict(path=str(path),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes)
git('switch','-c','codex/smartpca-workflow-20261123',cwd=T)
save(O/'START.json',dict(previous_goal_turn='progress',base='05e2d615f7b1c6c791d2ed29ff3e45fa4517c5ff',new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print('Started full file-workflow integration; preserved nineteen owner snapshots.')
