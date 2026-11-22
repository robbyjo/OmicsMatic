from pathlib import Path
import json,re
from prepare_genomicranges_20261122_114 import R,T,O,B
from handover_inventory_20261122_111 import save
states=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());owners=[{'path':x['worktree'],'branch':x.get('branch')} for x in states if re.search('dndscv|fuma|signac|rtrack',x.get('branch',''),re.I)];print('OWNERS',json.dumps(owners),flush=True)
path=T/'docs/literature/dndscv-workflow-development/VALIDATION.json';obj=json.loads(path.read_bytes());matches=[]
def walk(x,keys):
 if isinstance(x,dict):
  for k,v in x.items():walk(v,keys+[k])
 elif isinstance(x,list):
  for i,v in enumerate(x):walk(v,keys+[str(i)])
 elif isinstance(x,str) and ('library' in x.lower() or 'rscript' in x.lower()) and len(x)<500 and re.search(r'[A-Za-z]:[/\\]',x):matches.append({'keys':keys,'value':x})
walk(obj,[]);save(B/'EXISTING-RUNTIME-SCREENING.json',{'owners':owners,'dndscv_profile_path_candidates':matches});print('PROFILE_PATHS',json.dumps(matches[:35],indent=2),flush=True)
