from pathlib import Path
import hashlib,json,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';D=T/'docs/literature/squidpy-workflow-handover-20261122'
O=R/'build/git-publish/20261122-squidpy-workflow-handover-136';C=O/'reader-controls-1';C.mkdir()
reader=D/'verify_checkpoint.py';cases=[]
def execute(label,args,expected):
 p=subprocess.run([str(a) for a in args],cwd=T,capture_output=True)
 (O/(label+'.stdout')).write_bytes(p.stdout);(O/(label+'.stderr')).write_bytes(p.stderr)
 assert (p.returncode==0)==expected,(label,p.returncode,p.stderr)
 row=dict(case=label,exit_code=p.returncode,expected_admitted=expected,stdout=pin(O/(label+'.stdout')),stderr=pin(O/(label+'.stderr')))
 cases.append(row);return p
execute('development-reader-windows',[sys.executable,'-X','utf8','-B',reader],True)
execute('development-reader-linux',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd','/mnt/d/projects/JLinAlg/build/fu-1','--','/usr/bin/python3','-B','docs/literature/squidpy-workflow-handover-20261122/verify_checkpoint.py'],True)
modified={}
for label in ('changed-draft','resealed-false-prototype-count','resealed-false-final-count'):
 target=C/label;shutil.copytree(D,target)
 if label=='changed-draft':
  path=target/'drafts/src/main/java/org/jlinalg/cli/SquidpyWorkflowCli.java';path.write_bytes(path.read_bytes()+b'\n// deliberately changed negative control\n')
 else:
  path=target/('STATUS.json' if 'prototype' in label else 'FINAL-STATUS.json');obj=json.loads(path.read_bytes())
  if 'prototype' in label:obj['prototype_contract_tests']=14
  else:obj['unexpected_failures']=0
  save(path,obj)
  manifest=json.loads((target/'FILES.json').read_bytes());manifest[path.name]=pin(path);save(target/'FILES.json',manifest)
 execute(label,[sys.executable,'-X','utf8','-B',reader,'--checkpoint',target],False)
 modified[label]=dict(path=path.relative_to(target).as_posix(),content=pin(path),manifest=pin(target/'FILES.json'))
execute('native-reader',[sys.executable,'-X','utf8','-B','tools/literature/verify_squidpy_native_checkpoint.py','--jar','build/squidpy-1/native-normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'],True)
result=dict(status='DEVELOPMENT_PRESERVATION_READERS_PASS_AND_THREE_CONTRADICTIONS_REFUSED',cases=cases,
    negative_control_modifications=modified,source_or_original_analysis_reexecuted=False,
    native_producer_rechecked=True,full_adapter_qualified=False,private_control_copies=str(C),
    cleanup='Retained while publishing; duplicate copied controls may be retired after exact replacement verification. Original scientific captures and runtime prefixes must remain.')
save(D/'READER-CONTROLS.json',result)
save(D/'FILES.json',{p.relative_to(D).as_posix():pin(p) for p in sorted(D.rglob('*')) if p.is_file() and p.name!='FILES.json' and '__pycache__' not in p.parts})
print(json.dumps(dict(status=result['status'],checks=len(cases),negative_controls=3)),flush=True)
