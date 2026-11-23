from pathlib import Path
import json
from handover_inventory_20261122_111 import git,save,snapshot,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-mrpresso-catalog-178';O.mkdir()
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='6dda7a239967c68266c786de5678a156e3196658'
assert not git('status','--porcelain=v1',cwd=T)
owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners);indexes={}
for owner in owners:
 p=Path(git('rev-parse','--git-path','index',cwd=owner['worktree']).decode().strip())
 if not p.is_absolute():p=Path(owner['worktree'])/p
 indexes[owner['worktree']]={'path':str(p),'identity':pin(p)}
save(O/'INDEXES-BEFORE.json',indexes)
git('switch','-c','codex/mrpresso-catalog-20261123',cwd=T)
save(O/'START.json',dict(status='MRPRESSO_CATALOG_ADMISSION_STARTED',base='6dda7a239967c68266c786de5678a156e3196658',
 scoped_native_workflow_qualified=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print('Started guarded six-role/one-native-family MR-PRESSO catalog work; all owner snapshots retained.')
