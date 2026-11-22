from pathlib import Path
import json,subprocess
from handover_inventory_20261122_111 import R,snapshot,pin,save,git
T=R/'build/gr-1';O=R/'build/git-publish/20261122-genomicranges-114';BASE='9cc492314af013860818c2151d126425bf81c352';PRIOR=R/'build/sk-3';B=T/'build/genomicranges-1'
if __name__=='__main__':
 O.mkdir();before=snapshot();assert len(before)==69 and before==json.loads((O.parent/'20261122-skat-qualification-113/WORKTREES-AFTER.json').read_bytes())
 assert git('ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
 save(O/'WORKTREES-BEFORE.json',before);command=['git','worktree','add','-b','codex/genomicranges-workflows-20261122',str(T),BASE]
 save(O/'WORKTREE-STARTED.json',{'command':command,'previous_turn_classification':'PROGRESS:original SKAT prepared workflows qualified and admitted as one family;148 scoped families published with preserved source roles and successful checks.','full250_paper_objective_retained':True})
 with (O/'worktree.stdout').open('xb') as out,(O/'worktree.stderr').open('xb') as err:p=subprocess.run(command,cwd=R,stdin=subprocess.DEVNULL,stdout=out,stderr=err,timeout=240)
 save(O/'WORKTREE-PROCESS.json',{'exit_code':p.returncode});assert p.returncode==0
 B.mkdir(parents=True);print('PASS isolated owner70 from verified main;all69 prior owners exact',flush=True)
