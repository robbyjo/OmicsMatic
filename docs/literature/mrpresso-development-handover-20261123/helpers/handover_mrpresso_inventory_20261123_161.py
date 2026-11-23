from pathlib import Path
import json,sys
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1'
O=R/'build/git-publish/20261123-mrpresso-handover-161'
O.mkdir(exist_ok=True)
stage=sys.argv[1]
current=snapshot();save(O/f'WORKTREES-{stage}.json',current)
indexes={}
for owner in current:
    path=Path(git('rev-parse','--git-path','index',cwd=owner['worktree']).decode().strip())
    if not path.is_absolute():path=Path(owner['worktree'])/path
    indexes[owner['worktree']]={'path':str(path),'identity':pin(path) if path.exists() else None}
save(O/f'INDEXES-{stage}.json',indexes)
if stage=='BEFORE':
    baseline=json.loads((R/'build/git-publish/20261122-mrpresso-intake-153/WORKTREES-BEFORE.json').read_bytes())
else:baseline=json.loads((O/'WORKTREES-BEFORE.json').read_bytes())
before={x['worktree']:x for x in baseline};after={x['worktree']:x for x in current}
unchanged=[];changed=[]
for name,owner in before.items():
    (unchanged if after.get(name)==owner else changed).append(name)
save(O/f'PRESERVATION-{stage}.json',dict(unchanged_owners=unchanged,changed_owners=changed,new_owners=sorted(set(after)-set(before)),owned_checkout=str(T)))
print(json.dumps(dict(stage=stage,worktrees=len(current),dirty=[dict(path=x['worktree'],files=len(x['status'])) for x in current if x['status']],unchanged_owners=len(unchanged),changed_owners=changed),indent=2))
