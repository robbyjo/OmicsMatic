from pathlib import Path
import json,subprocess,zipfile,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1';D=B/'final-suite-setup-1';D.mkdir()
jar=B/'normal-build-4/project-build/cli/omicsmatic-0.4.0.jar';expected='af72b8ccaa263eca2aad69ff96e871829981a6475dc598a8c5c5b704dba6f0e6';assert pin(jar)['sha256']==expected
prior=B/'normal-build-3/project-build/cli/omicsmatic-0.4.0.jar'
with zipfile.ZipFile(prior) as a,zipfile.ZipFile(jar) as b:
 left={x for x in a.namelist() if not x.endswith('/')};right={x for x in b.namelist() if not x.endswith('/')};assert left==right
 modified=sorted(x for x in left if a.read(x)!=b.read(x));assert modified==['org/jlinalg/cli/MrPressoWorkflowAudit.class'],modified
 save(D/'PAYLOAD-COMPATIBILITY.json',dict(previous=pin(prior),current=pin(jar),payloads=len(left),modified=modified,
  scientific_classes_and_resources_byte_identical=True))
native='/var/tmp/omicsmatic-mrpresso-qualification-20261123-168/omicsmatic-0.4.0.jar'
linux_src='/mnt/d/projects/JLinAlg/build/fu-1/build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'
code='''from pathlib import Path
import hashlib,json,shutil,sys
source=Path(sys.argv[1]);target=Path(sys.argv[2]);target.parent.mkdir(mode=0o700)
shutil.copyfile(source,target)
with target.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
assert digest==sys.argv[3]
print(json.dumps(dict(status='EXACT_PRIVATE_PRODUCER_COPY',path=str(target),bytes=target.stat().st_size,sha256=digest,existing_runtimes_modified=False)))
'''
command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-c',code,linux_src,native,expected]
p=subprocess.run(command,cwd=T,capture_output=True);(D/'copy.stdout').write_bytes(p.stdout);(D/'copy.stderr').write_bytes(p.stderr)
save(D/'COPY-COMMAND.json',dict(command=command,exit_code=p.returncode));assert p.returncode==0,p.stderr
save(D/'LINUX-PRODUCER.json',json.loads(p.stdout))
script=(R/'build/run_mrpresso_workflow_references_20261123_165.py').read_text()
script=script.replace("D=B/'workflow-original-references-1';D.mkdir()","D=B/'workflow-original-references-2';D.mkdir()")
script=script.replace("jar=B/'normal-build-3/","jar=B/'normal-build-4/").replace('86bcb46ca7d5034546b13be5d25e52b804e1cdd6d81e355c01424fed675ab4c9',expected)
old="command=[*hosts[host],'-Xmx512m','-jar',address(jar,host),'mrpresso-tools',*args]"
new="command=[*hosts[host],'-Xmx512m','-jar',('"+native+"' if host=='linux21' else address(jar,host)),'mrpresso-tools',*args]"
assert old in script;script=script.replace(old,new)
runner=R/'build/run_mrpresso_final_references_20261123_168.py';runner.write_text(script,encoding='utf-8');(D/'runner.py').write_bytes(runner.read_bytes())
save(D/'RESULT.json',dict(status='PREPARED',producer=pin(jar),runner=pin(runner),private_linux_path=native,new_family_credit=0,new_reviews=0))
print('Final producer staged with byte-identical scientific payloads; exact private Linux copy verified. Starting all45 source cases on three JVMs.',flush=True)
raise SystemExit(subprocess.call([sys.executable,'-X','utf8','-B',str(runner)],cwd=T))
