from pathlib import Path
import json, os, shutil, subprocess, time, zipfile, xml.etree.ElementTree as ET
from handover_inventory_20261122_111 import pin, save
R=Path('D:/projects/JLinAlg'); T=R/'build/fu-1'; B=T/'build/scenic-1/normal-build-1'; B.mkdir()
sources={p.relative_to(T).as_posix():pin(p) for base in ('src','build.gradle','settings.gradle','gradle.properties') for p in ([T/base] if (T/base).is_file() else (T/base).rglob('*')) if p.is_file()}
save(B/'SOURCE-INPUTS.json',sources)
for name in sources:
 if 'PyScenic' in name or 'pyscenic-' in name:
  target=B/'source-context'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,target)
build=B/'project-build'; dep=build/'dependencies/jdistlib-all-0.10.2.jar';dep.parent.mkdir(parents=True)
shutil.copyfile(R/'build/gp-1/build/dependencies/jdistlib-all-0.10.2.jar',dep)
assert pin(dep)['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
init=B/'init.gradle';init.write_text("allprojects { layout.buildDirectory.set(file('"+build.as_posix()+"')) }\n")
gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat'
args=[gradle,'--offline','--no-daemon','--console=plain','--init-script',str(init),'-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m','test']
for pattern in ('org.jlinalg.genetics.*','org.jlinalg.mrpresso.*','org.jlinalg.cli.MrPressoWorkflow*','org.jlinalg.cli.PlinkPca*','org.jlinalg.cli.SmartpcaWorkflow*','org.jlinalg.cli.SmartpcaFst*','org.jlinalg.confounding.*','org.jlinalg.enrichment.*'):args+=['--tests',pattern]
args+=['executableJar']; env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot',JLINALG_PLINK_HOME=str(R/'build/omics-plink-official'),JLINALG_LIFTOVER_REFERENCE=str(R/'build/literature-review/liftover-runtime'))
save(B/'COMMAND.json',dict(arguments=args,environment={k:env[k] for k in ('JAVA_HOME','JLINALG_PLINK_HOME','JLINALG_LIFTOVER_REFERENCE')}));started=time.monotonic()
with (B/'stdout').open('xb') as stdout,(B/'stderr').open('xb') as stderr:
 process=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);save(B/'PROCESS.json',dict(pid=process.pid,command=args));code=process.wait()
assert all(pin(T/p)==wanted for p,wanted in sources.items()),'Source changed during normal build'
result=dict(exit_code=code,seconds=time.monotonic()-started,source_unchanged=True,new_family_credit=0,new_reviews=0,workflow_qualified=False,whole_goal_complete=False)
if code==0:
 tests=dict(tests=0,failures=0,errors=0,skipped=0)
 for path in (build/'test-results/test').glob('TEST-*.xml'):
  root=ET.parse(path).getroot()
  for key in tests:tests[key]+=int(root.get(key,'0'))
 result['normal_tests']=tests;assert tests['failures']==tests['errors']==tests['skipped']==0
 jar=build/'cli/omicsmatic-0.4.0.jar';result['producer']=dict(path=str(jar),**pin(jar));prior=T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar'
 assert pin(prior)['sha256']=='534da0fe4f10959a622c4d5f74fee35dc959c6d48e2730f9861dc83790973522'
 with zipfile.ZipFile(prior) as a,zipfile.ZipFile(jar) as b:
  old=[n for n in a.namelist() if not n.endswith('/')];new=[n for n in b.namelist() if not n.endswith('/')]
  modified=[n for n in old if n not in new or a.read(n)!=b.read(n)];added=[n for n in new if n not in old]
  save(B/'PAYLOADS.json',dict(previous_payloads=len(old),modified=modified,added=added));assert modified==[],modified
  assert all(n.startswith(('org/jlinalg/enrichment/PyScenicAucell','org/jlinalg/omics/pyscenic-ce41b61/')) for n in added),added
save(B/'RESULT.json',result);print(json.dumps(result,indent=2))
if code:print((B/'stderr').read_text(errors='replace')[-5000:]);print((B/'stdout').read_text(errors='replace')[-2500:])
raise SystemExit(code)
