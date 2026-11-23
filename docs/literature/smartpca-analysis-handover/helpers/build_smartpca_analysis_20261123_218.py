from pathlib import Path
import json,os,shutil,subprocess,sys
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-2';out=B/'analysis-check-1';out.mkdir()
fixture=T/'src/test/resources/org/jlinalg/genetics/smartpca-original-outlier-reference.zip'
shutil.copyfile(B/'original-outlier-reference-1/original-outlier-reference.zip',fixture)
sources=['src/main/java/org/jlinalg/genetics/'+name+'.java' for name in ('SmartpcaOutliers','SmartpcaShrinkage','SmartpcaAnalysis')]+['src/test/java/org/jlinalg/genetics/SmartpcaExtensionsTest.java','src/test/java/org/jlinalg/genetics/SmartpcaAnalysisReferenceTest.java']
pins={name:pin(T/name) for name in sources+[fixture.relative_to(T).as_posix()]};save(out/'SOURCES.json',pins)
for name in pins:
 dest=out/'source-context'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
parent=T/'build/smartpca-1/normal-build-1/project-build/cli/omicsmatic-0.4.0.jar';assert pin(parent)['sha256']=='c07f1fc6955ff438fbcc2eb23a5620066815b817e21a1d17c7ac3e60d39ec29f'
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 matches=list((cache/module/version).rglob('*.jar'));assert len(matches)==1;junit.append(matches[0])
def linux(path):text=Path(path).as_posix();return '/mnt/'+text[0].lower()+text[2:] if len(text)>1 and text[1]==':' else text
results=[]
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'classes';classes.mkdir();cp=[classes,T/'src/test/resources',parent,*junit]
 if host.startswith('windows'):
  compile_command=[home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*[str(T/name) for name in sources]]
  run_command=[home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'org.jlinalg.genetics.SmartpcaAnalysisReferenceTest']
 else:
  prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--']
  compile_command=prefix+[home+'/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',':'.join(map(linux,cp)),'-d',linux(classes),*[linux(T/name) for name in sources]]
  run_command=prefix+[home+'/bin/java','-ea','-cp',':'.join(map(linux,cp)),'org.jlinalg.genetics.SmartpcaAnalysisReferenceTest']
 def execute(label,command):
  process=subprocess.run(command,cwd=T,capture_output=True,timeout=300)
  (folder/(label+'.stdout')).write_bytes(process.stdout);(folder/(label+'.stderr')).write_bytes(process.stderr)
  save(folder/(label+'.json'),dict(command=command,exit_code=process.returncode));return process
 compiled=execute('javac',compile_command);row=dict(host=host,compiler_exit=compiled.returncode)
 if compiled.returncode==0:
  checked=execute('java',run_command);row['test_exit']=checked.returncode
  if checked.returncode:print(host,checked.stderr.decode(errors='replace')[-5000:])
 else:print(host,compiled.stderr.decode(errors='replace')[-5000:])
 save(folder/'RESULT.json',row);results.append(row)
 assert all(pin(T/name)==identity for name,identity in pins.items())
save(out/'RESULT.json',dict(hosts=results,source_pins=pins,workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(results,indent=2));raise SystemExit(0 if all(row.get('test_exit')==0 for row in results) else 2)
