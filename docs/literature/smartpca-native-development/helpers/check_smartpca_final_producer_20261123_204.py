from pathlib import Path
import json,math,os,subprocess,zipfile
from handover_inventory_20261122_111 import pin,save
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-1';out=B/'final-producer-check-2';out.mkdir()
record=json.loads((B/'normal-build-1/RESULT.json').read_bytes());jar=Path(record['producer']['path']);assert pin(jar)=={k:record['producer'][k] for k in ('bytes','sha256')}
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 paths=list((cache/module/version).rglob('*.jar'));assert len(paths)==1;junit.append(paths[0])
example=T/'examples/smartpca-prepared/PreparedExample.java';source_before=pin(example);results=[];examples=[]
def linux(path):
 text=Path(path).as_posix();return '/mnt/'+text[0].lower()+text[2:] if len(text)>1 and text[1]==':' else text
# Compare instructions while retaining both complete disassemblies; Java debug
# metadata differs across Gradle/direct-javac builds and is not instruction code.
import re
javap='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javap.exe'
direct=B/'component-check-1/windows21/classes'
instruction_checks=[]
for path in sorted((direct/'org/jlinalg/genetics').glob('SmartpcaPrepared*.class')):
 name='org.jlinalg.genetics.'+path.stem;dumps=[]
 for label,cp in [('normal',jar),('direct',direct)]:
  p=subprocess.run([javap,'-c','-p','-constants','-classpath',str(cp),name],capture_output=True,timeout=60)
  assert p.returncode==0,p.stderr
  (out/(path.stem+'.'+label+'.javap')).write_bytes(p.stdout)
  dumps.append(re.sub(rb'#[0-9]+',b'#CP',p.stdout).replace(b'\r\n',b'\n'))
 assert dumps[0]==dumps[1],'Native instructions differ: '+name
 instruction_checks.append(name)
save(out/'INSTRUCTION-CHECKS.json',dict(status='PASS_SAME_INSTRUCTIONS_WITH_DIFFERENT_DEBUG_METADATA',classes=instruction_checks,
 constant_pool_indices_normalized_only=True,binary_equality_claim=False))
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'example-classes';classes.mkdir();test_classes=B/'component-check-1'/host/'classes'
 cp=[jar,test_classes,T/'src/test/resources',*junit]
 def execute(label,command):
  process=subprocess.run(command,cwd=folder,capture_output=True,timeout=300)
  (folder/(label+'.stdout')).write_bytes(process.stdout);(folder/(label+'.stderr')).write_bytes(process.stderr)
  save(folder/(label+'.json'),dict(command=command,cwd=str(folder),exit_code=process.returncode));assert process.returncode==0,(host,label,process.stderr.decode(errors='replace'))
  return process
 if host.startswith('windows'):
  compile_command=[home+'/bin/javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(example)]
  java=[home+'/bin/java.exe'];classpath=os.pathsep.join(map(str,cp));example_cp=os.pathsep.join(map(str,[jar,classes]))
 else:
  prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--'];compile_command=prefix+[home+'/bin/javac','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',linux(jar),'-d',linux(classes),linux(example)]
  java=prefix+[home+'/bin/java'];classpath=':'.join(map(linux,cp));example_cp=':'.join(map(linux,[jar,classes]))
 checked=execute('component-final-producer',java+['-ea','-Xlog:class+load=info:file=classload.log','-cp',classpath,'org.jlinalg.genetics.SmartpcaPreparedTest'])
 log=(folder/'classload.log').read_text();matching=[line for line in log.splitlines() if 'org.jlinalg.genetics.SmartpcaPrepared source:' in line]
 assert len(matching)==1 and 'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar' in matching[0].replace('\\','/'),matching
 execute('example-javac',compile_command);ran=execute('example-java',java+['-ea','-cp',example_cp,'PreparedExample'])
 lines=ran.stdout.decode().splitlines();assert len(lines)==6 and lines[0]=='sample\tstatus\tPC1\tPC2'
 for line in lines[1:]:
  cells=line.split('\t');assert cells[1]=='COMPUTED' and len(cells)==4 and all(math.isfinite(float(x)) for x in cells[2:])
 examples.append(ran.stdout.replace(b'\r\n',b'\n'));results.append(dict(host=host,component_methods=7,original_program_comparisons=9,example_samples=5,
  loaded_native_component_from_exact_final_JAR=True,all_commands_exit_zero=True))
assert len(set(examples))==1 and pin(example)==source_before
assert pin(jar)=={k:record['producer'][k] for k in ('bytes','sha256')}
save(out/'COMPLETED.json',dict(status='PASS_EXACT_NORMAL_PRODUCER_ON_THREE_JVMS_AND_ACTUAL_EXAMPLE',producer=record['producer'],profiles=results,
 example_source=source_before,native_component_qualified=True,workflow_qualified=False,catalog_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(status='PASS',profiles=results),indent=2))
