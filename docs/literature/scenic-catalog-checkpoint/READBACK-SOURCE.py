from pathlib import Path
from unittest.mock import patch
import importlib.util,json,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-catalog-355';D=B/'checkpoint-reader-1';P=T/'docs/literature/scenic-catalog-checkpoint';D.mkdir()
reader=T/'tools/literature/verify_scenic_catalog_checkpoint.py';jar=T/'build/scenic-3/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar';rows=[]
for host,args in [('windows',[sys.executable,'-X','utf8','-B',str(reader),'--jar',str(jar),'--cache',str(R/'build/literature-review/xml')]),('linux',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','env','GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE=/mnt/d/projects/JLinAlg/build/fu-1','python3','-B','/mnt/d/projects/JLinAlg/build/fu-1/tools/literature/verify_scenic_catalog_checkpoint.py','--jar','/mnt/d/projects/JLinAlg/build/fu-1/build/scenic-3/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar','--cache','/mnt/d/projects/JLinAlg/build/literature-review/xml'])]:
 p=subprocess.run(args,cwd=T,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(D/(host+'.stdout')).write_bytes(p.stdout);(D/(host+'.stderr')).write_bytes(p.stderr);assert p.returncode==0,(host,p.stderr)
 rows.append(dict(host=host,exit_code=p.returncode,measured=json.loads(p.stdout)));print(host+' checkpoint and fresh primary readback passed',flush=True)
sys.path.insert(0,str(reader.parent));spec=importlib.util.spec_from_file_location('scenic_admission_checkpoint_control',reader);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
original=Path.read_bytes;controls=[]
for label,target,change in [('false-credit',P/'STATUS.json',lambda raw:json.dumps(dict(json.loads(raw),new_reviews=6)).encode()),('changed-archive',P/'evidence.zip',lambda raw:raw[:-1]+bytes([raw[-1]^1])),('changed-source',T/'tools/literature/scenic_catalog/model.py',lambda raw:raw+b'\n# deliberate changed source\n')]:
 def altered(path):
  raw=original(path);return change(raw) if path==target else raw
 with patch.object(Path,'read_bytes',altered):
  try:module.verify(T)
  except ValueError as error:controls.append(dict(case=label,refused=True,error=str(error),physical_files_changed=False))
  else:raise AssertionError('Changed evidence accepted: '+label)
result=dict(status='PASS_SCENIC_CATALOG_CHECKPOINT_BOTH_HOSTS_AND_THREE_CHANGE_REFUSALS',reader=pin(reader),checks=rows,controls=controls,physical_source_or_evidence_changed=False,fresh_scientific_fit_executed=False)
save(D/'RESULT.json',result);save(P/'READBACK.json',result)
(P/'READBACK-SOURCE.py').write_bytes(Path(__file__).read_bytes());print(json.dumps(dict(hosts=2,change_controls_refused=3,new_family_credit=1,new_reviews=5)),flush=True)
