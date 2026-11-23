from pathlib import Path
import json,os,shutil,subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-1';out=B/'component-check-2';out.mkdir()
fixture=T/'src/test/resources/org/jlinalg/enrichment/pyscenic-original-aucell-reference.zip';shutil.copyfile(B/'original-aucell-reference-2/original-aucell-reference.zip',fixture)
sources=['src/main/java/org/jlinalg/enrichment/PyScenicAucell.java','src/test/java/org/jlinalg/enrichment/PyScenicAucellReferenceTest.java'];pins={name:pin(T/name) for name in sources+[fixture.relative_to(T).as_posix()]};save(out/'SOURCES.json',pins)
for name in pins:
 target=out/'source-context'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,target)
parent=T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar';assert pin(parent)['sha256']=='534da0fe4f10959a622c4d5f74fee35dc959c6d48e2730f9861dc83790973522'
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 matches=list((cache/module/version).rglob('*.jar'));assert len(matches)==1;junit.append(matches[0])
def linux(path):s=Path(path).as_posix();return '/mnt/'+s[0].lower()+s[2:]
hosts=[]
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'classes';classes.mkdir();cp=[classes,T/'src/test/resources',parent,*junit]
 if host.startswith('windows'):
  compile_args=[home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*[str(T/name) for name in sources]]
  run_args=[home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'org.jlinalg.enrichment.PyScenicAucellReferenceTest']
 else:
  prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--'];compile_args=prefix+[home+'/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',':'.join(map(linux,cp)),'-d',linux(classes),*[linux(T/name) for name in sources]]
  run_args=prefix+[home+'/bin/java','-ea','-cp',':'.join(map(linux,cp)),'org.jlinalg.enrichment.PyScenicAucellReferenceTest']
 def run(name,args):
  p=subprocess.run(args,cwd=T,capture_output=True,timeout=180);(folder/(name+'.stdout')).write_bytes(p.stdout);(folder/(name+'.stderr')).write_bytes(p.stderr);save(folder/(name+'.json'),dict(command=args,exit_code=p.returncode));return p
 compiled=run('javac',compile_args);row=dict(host=host,compiler_exit=compiled.returncode)
 if compiled.returncode==0:
  checked=run('java',run_args);row['test_exit']=checked.returncode
  if checked.returncode:print(host,checked.stderr.decode(errors='replace')[-3500:])
 else:print(host,compiled.stderr.decode(errors='replace')[-3500:])
 hosts.append(row);save(folder/'RESULT.json',row)
 assert all(pin(T/name)==wanted for name,wanted in pins.items())
save(out/'RESULT.json',dict(hosts=hosts,source_pins=pins,workflow_qualified=False,catalog_credit=0,new_reviews=0,whole_goal_complete=False));print(json.dumps(hosts,indent=2));raise SystemExit(0 if all(row.get('test_exit')==0 for row in hosts) else 2)
