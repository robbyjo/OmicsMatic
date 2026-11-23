from pathlib import Path
import json,os,shutil,subprocess,sys
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-1';out=B/'component-check-1';out.mkdir()
resource=T/'src/main/resources/org/jlinalg/omics/smartpca-4f7e9f0';resource.mkdir(parents=True)
intake=B/'source-intake-1';reference=B/'original-program-reference-1'
for source,target in [(intake/'original-source/LICENSE.txt',resource/'LICENSE.txt'),(intake/'original-source.tar.gz',resource/'original-source.tar.gz')]:shutil.copyfile(source,target)
save(resource/'SOURCE.json',json.loads((intake/'SOURCE.json').read_bytes()))
fixtures=T/'src/test/resources/org/jlinalg/genetics';fixtures.mkdir(parents=True,exist_ok=True)
shutil.copyfile(reference/'original-reference.zip',fixtures/'smartpca-original-reference.zip')
attributes=T/'.gitattributes';attributes.write_bytes(attributes.read_bytes()+b'\n# Source-bound smartpca originals and prepared-component fixtures.\nsrc/main/resources/org/jlinalg/omics/smartpca-4f7e9f0/** -text whitespace=cr-at-eol\nsrc/main/java/org/jlinalg/genetics/SmartpcaPrepared.java -text whitespace=cr-at-eol\nsrc/test/java/org/jlinalg/genetics/SmartpcaPreparedTest.java -text whitespace=cr-at-eol\nsrc/test/resources/org/jlinalg/genetics/smartpca-original-reference.zip -text whitespace=cr-at-eol\ntools/literature/setup_smartpca_reference.py -text whitespace=cr-at-eol\ntools/literature/generate_smartpca_reference.py -text whitespace=cr-at-eol\n')
source_paths=['src/main/java/org/jlinalg/genetics/SmartpcaPrepared.java','src/test/java/org/jlinalg/genetics/SmartpcaPreparedTest.java',
 'src/test/resources/org/jlinalg/genetics/smartpca-original-reference.zip','tools/literature/setup_smartpca_reference.py','tools/literature/generate_smartpca_reference.py']
pins={p:pin(T/p) for p in source_paths};save(out/'SOURCES.json',pins)
for name in source_paths:
 target=out/'source-context'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,target)
parent=T/'build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'
assert pin(parent)['sha256']=='af72b8ccaa263eca2aad69ff96e871829981a6475dc598a8c5c5b704dba6f0e6'
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1')
junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 paths=list((cache/module/version).rglob('*.jar'));assert len(paths)==1;junit.append(paths[0])
def linux(p):
 p=Path(p).as_posix();return '/mnt/'+p[0].lower()+p[2:] if len(p)>1 and p[1]==':' else p
results=[]
for host,java_home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'classes';classes.mkdir()
 cp=[classes,T/'src/test/resources',parent,*junit]
 def execute(label,cmd):
  process=subprocess.run(cmd,cwd=T,capture_output=True,timeout=300)
  (folder/(label+'.stdout')).write_bytes(process.stdout);(folder/(label+'.stderr')).write_bytes(process.stderr)
  save(folder/(label+'.json'),dict(command=cmd,exit_code=process.returncode));return process
 if host.startswith('windows'):
  javac=[java_home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*[str(T/p) for p in source_paths[:2]]]
  java=[java_home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'org.jlinalg.genetics.SmartpcaPreparedTest']
 else:
  prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--']
  javac=prefix+['/usr/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',':'.join(map(linux,cp)),'-d',linux(classes),*[linux(T/p) for p in source_paths[:2]]]
  java=prefix+['/usr/bin/java','-ea','-cp',':'.join(map(linux,cp)),'org.jlinalg.genetics.SmartpcaPreparedTest']
 compiled=execute('javac',javac);result=dict(host=host,compiler_exit=compiled.returncode)
 if compiled.returncode==0:
  checked=execute('java',java);result['test_exit']=checked.returncode
  if checked.returncode:print(host,checked.stderr.decode(errors='replace')[-5000:])
 else:print(host,compiled.stderr.decode(errors='replace')[-5000:])
 save(folder/'RESULT.json',result);results.append(result)
 assert all(pin(T/p)==wanted for p,wanted in pins.items()),'Component source changed during checks'
save(out/'RESULT.json',dict(hosts=results,source_files=pins,original_comparisons_per_host=9,test_methods_per_host=7,
 workflow_qualified=False,catalog_credit=0,whole_goal_complete=False))
print(json.dumps(results,indent=2));raise SystemExit(0 if all(r.get('test_exit')==0 for r in results) else 2)
