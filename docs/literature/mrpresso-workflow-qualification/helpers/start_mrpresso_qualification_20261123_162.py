from pathlib import Path
import json,subprocess
from handover_inventory_20261122_111 import snapshot,pin,save,git,entries
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-mrpresso-qualification-162';O.mkdir(exist_ok=True)
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='4853aece34ed8eac483059c7f6b26b18a7f2e7a6'
assert [x['path'] for x in entries(T)]==['tools/literature/mrpresso_workflow/extended_reference.R']
save(O/'OWNED-NEW-GENERATOR-AT-START.json',entries(T))
save(O/'WORKTREES-BEFORE.json',snapshot());indexes={}
for item in json.loads((O/'WORKTREES-BEFORE.json').read_bytes()):
 p=Path(git('rev-parse','--git-path','index',cwd=item['worktree']).decode().strip())
 if not p.is_absolute():p=Path(item['worktree'])/p
 indexes[item['worktree']]={'path':str(p),'identity':pin(p)}
save(O/'INDEXES-BEFORE.json',indexes)
reader=T/'docs/literature/mrpresso-development-handover-20261123/verify_checkpoint.py'
commands=[['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-B',str(reader)],
 ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-B','/mnt/d/projects/JLinAlg/build/fu-1/docs/literature/mrpresso-development-handover-20261123/verify_checkpoint.py']]
for i,command in enumerate(commands):
 p=subprocess.run(command,cwd=T,capture_output=True,timeout=45);(O/f'baseline-{i}.stdout').write_bytes(p.stdout);(O/f'baseline-{i}.stderr').write_bytes(p.stderr);assert p.returncode==0
git('switch','-c','codex/mrpresso-qualification-20261123',cwd=T)
save(O/'START.json',dict(previous_goal_turn='progress',reason='Published verified handover and complete current development checkpoint',
 base='4853aece34ed8eac483059c7f6b26b18a7f2e7a6',new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print('Started source-bound MR-PRESSO qualification; prior checkpoint readers passed on Windows/Linux; all owner states retained.')
