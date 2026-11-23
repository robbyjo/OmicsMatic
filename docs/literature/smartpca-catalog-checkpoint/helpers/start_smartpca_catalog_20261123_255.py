from pathlib import Path
import json
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-catalog-255';B=T/'build/smartpca-catalog-255'
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='efe2e19dd1f4e5add6a6dd09b949e900368dea74' and not git('status','--porcelain=v1',cwd=T)
O.mkdir();B.mkdir();owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners)
indexes={}
for row in owners:
 path=Path(git('rev-parse','--path-format=absolute','--git-path','index',cwd=Path(row['worktree'])).decode().strip());indexes[row['worktree']]=dict(path=str(path),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes)
git('switch','-c','codex/smartpca-catalog-20261123',cwd=T)
save(O/'START.json',dict(previous_goal_turn='progress',base='efe2e19dd1f4e5add6a6dd09b949e900368dea74',scoped_families=153,reviewed_uses=1691,pending_uses=1031,whole_goal_complete=False))
print('Started isolated smartpca catalog stage; 19 worktree owners inventoried.')
