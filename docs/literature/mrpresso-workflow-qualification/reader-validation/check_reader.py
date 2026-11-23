from pathlib import Path
import json,os,shutil,subprocess
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'qualification-reader-controls-1';D.mkdir()
packet='docs/literature/mrpresso-workflow-qualification';P=T/packet;sources=json.loads((P/'SOURCES.json').read_bytes());evidence=json.loads((P/'EVIDENCE.json').read_bytes())
reader='tools/literature/verify_mrpresso_workflow_checkpoint.py';original_archive=pin(P/evidence['archive']);records=[]
def run(host,label,root,expected):
 script=root/reader
 if host=='windows':command=['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-B',str(script)]
 else:
  value=script.as_posix();command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-B','/mnt/'+value[0].lower()+value[2:]]
 p=subprocess.run(command,cwd=T,capture_output=True);(D/(label+'.'+host+'.stdout')).write_bytes(p.stdout);(D/(label+'.'+host+'.stderr')).write_bytes(p.stderr)
 row=dict(host=host,case=label,exit_code=p.returncode,expected=expected);records.append(row);save(D/'PROGRESS.json',records);assert p.returncode==expected,(row,p.stderr)
for host in ('windows','linux'):run(host,'valid',T,0)
for label,alter in [
 ('false-goal-completion',lambda status:status.__setitem__('whole_goal_complete',True)),
 ('unearned-family-credit',lambda status:status.__setitem__('new_family_credit',1)),
 ('forged-packaged-count',lambda status:status.__setitem__('packaged_commands',310)),
 ('different-scientific-source',None)]:
 root=D/label;root.mkdir()
 for name in sources:
  dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
 target=root/packet;target.mkdir(parents=True)
 for name in ('STATUS.json','SOURCES.json','EVIDENCE.json'):shutil.copyfile(P/name,target/name)
 os.link(P/evidence['archive'],target/evidence['archive'])
 if alter:
  path=target/'STATUS.json';status=json.loads(path.read_bytes());alter(status);save(path,status)
 else:
  path=root/'src/main/java/org/jlinalg/mrpresso/MrPressoLinearModel.java';path.write_bytes(path.read_bytes()+b'\n// Different source context, not this qualification.\n')
 for host in ('windows','linux'):run(host,label,root,2)
assert pin(P/evidence['archive'])==original_archive
save(D/'RESULT.json',dict(status='PASS',reader=pin(T/reader),cases=records,negative_scenarios_per_host=4,
 archive_copies_are_read_only_hardlinks=True,original_source_and_archive_unchanged=True,
 scientific_reexecution_claim=False,new_family_credit=0,new_reviews=0))
print('PASS saved qualification reader on Windows/Linux: valid checkpoint and four contradictory qualification/source claims per host.')
