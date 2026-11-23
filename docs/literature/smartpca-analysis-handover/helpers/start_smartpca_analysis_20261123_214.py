from pathlib import Path
from handover_inventory_20261122_111 import git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-2';O=R/'build/git-publish/20261123-smartpca-analysis-214'
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='df7ad70fdc1fe424a8674c5788cb4bb8d9f31243' and not git('status','--porcelain=v1',cwd=T)
B.mkdir();O.mkdir();owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners);indexes={}
for owner in owners:
 path=Path(git('rev-parse','--git-path','index',cwd=owner['worktree']).decode().strip())
 if not path.is_absolute():path=Path(owner['worktree'])/path
 indexes[owner['worktree']]=dict(path=str(path),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes)
git('switch','-c','codex/smartpca-analysis-20261123',cwd=T)
save(O/'START.json',dict(status='SMARTPCA_SOURCE_OUTLIER_SHRINKAGE_AND_WORKFLOW_INTEGRATION',previous_goal_turn='progress',
 base='df7ad70fdc1fe424a8674c5788cb4bb8d9f31243',new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print('Started smartpca analysis integration; all worktree owner snapshots retained.')
