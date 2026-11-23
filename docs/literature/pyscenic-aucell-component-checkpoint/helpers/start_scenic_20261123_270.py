from pathlib import Path
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-scenic-270';B=T/'build/scenic-1'
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='db45f34b42d0cff4d6393a9b1e460ce5bb5a67e1' and not git('status','--porcelain=v1',cwd=T)
O.mkdir();B.mkdir();owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners);indexes={}
for row in owners:
 path=Path(git('rev-parse','--path-format=absolute','--git-path','index',cwd=Path(row['worktree'])).decode().strip());indexes[row['worktree']]=dict(path=str(path),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes);git('switch','-c','codex/scenic-workflows-20261123',cwd=T)
save(O/'START.json',dict(previous_goal_turn='progress',base='db45f34b42d0cff4d6393a9b1e460ce5bb5a67e1',scoped_families=154,reviewed_uses=1697,pending_uses=1025,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print('Started SCENIC source/runtime stage; all19 owners inventoried, zero credit before qualification/admission.')
