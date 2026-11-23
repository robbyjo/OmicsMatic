from pathlib import Path
import json,shutil,subprocess,sys
from handover_inventory_20261122_111 import pin,save
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-1';Q=T/'docs/literature/smartpca-native-development';out=B/'saved-reader-controls-1';out.mkdir()
before={str(p):pin(p) for p in [Q/'STATUS.json',*[T/name for name in json.loads((Q/'STATUS.json').read_bytes())['source_code']]]}
cases=[]
for name in ('false-count','wrong-source-identity','altered-archive'):
 target=out/name;target.mkdir()
 for filename in ('STATUS.json','EVIDENCE.json','evidence.zip'):shutil.copyfile(Q/filename,target/filename)
 if name!='altered-archive':
  state=json.loads((target/'STATUS.json').read_bytes())
  if name=='false-count':state['component_methods']=999
  else:state['source_code'][next(iter(state['source_code']))]['sha256']='0'*64
  save(target/'STATUS.json',state)
 else:
  raw=bytearray((target/'evidence.zip').read_bytes());raw[-1]^=1;(target/'evidence.zip').write_bytes(raw)
for host in ('windows','linux'):
 for name in ('valid','false-count','wrong-source-identity','altered-archive'):
  target=Q if name=='valid' else out/name;script=T/'tools/literature/verify_smartpca_component_checkpoint.py'
  if host=='windows':command=[sys.executable,'-X','utf8','-B',str(script),'--packet',str(target)]
  else:
   def linux(p):text=Path(p).as_posix();return '/mnt/'+text[0].lower()+text[2:]
   command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','python3','-B',linux(script),'--packet',linux(target)]
  process=subprocess.run(command,cwd=T,capture_output=True,timeout=120)
  (out/(host+'-'+name+'.stdout')).write_bytes(process.stdout);(out/(host+'-'+name+'.stderr')).write_bytes(process.stderr)
  expected=0 if name=='valid' else 1;assert process.returncode==expected,(host,name,process.stderr)
  if name!='valid':assert {'false-count':b'Changed checkpoint counts','wrong-source-identity':b'Changed checkpoint source:','altered-archive':b'Changed evidence archive/index'}[name] in process.stderr
  cases.append(dict(host=host,case=name,exit_code=process.returncode,expected=expected))
assert all(pin(Path(p))==wanted for p,wanted in before.items())
result=dict(status='PASS_VALID_AND_THREE_EXPLICIT_CHECKPOINT_CONTRADICTIONS_ON_TWO_HOSTS',checks=8,cases=cases,
 canonical_sources_unchanged=True,scientific_analysis_reexecuted=False,byte_integrity_not_authentication=True)
save(out/'COMPLETED.json',result);save(Q/'READER-CONTROLS.json',result)
state=json.loads((Q/'STATUS.json').read_bytes());state['source_code']['docs/literature/smartpca-native-development/READER-CONTROLS.json']=pin(Q/'READER-CONTROLS.json');save(Q/'STATUS.json',state)
print('PASS: eight saved-reader controls; canonical sources unchanged; no original analysis rerun.')
