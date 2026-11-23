from pathlib import Path
import json,os,subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-3';out=B/'host-checks-1';out.mkdir();normal=B/'normal-build-3';jar=normal/'project-build/cli/omicsmatic-0.4.0.jar'
assert pin(jar)['sha256']=='f0629b7d3a6cb6ec9c099343aa35aa7af08e50093a098d3ef74747325520ed48'
source=out/'WorkflowHostChecks.java';source.write_text('''import java.nio.file.Files;
import java.nio.file.Path;
public final class WorkflowHostChecks {
 public static void main(String[] args)throws Exception {
  Path jar=Path.of(args[0]).toRealPath(),root=Files.createDirectory(Path.of(args[1]));
  for(String name:new String[]{"SmartpcaToolsCli","SmartpcaWorkflowAudit","SmartpcaWorkflowEvaluation","SmartpcaWorkflowTables","SmartpcaWorkflowSource","SmartpcaWorkflowData","SmartpcaWorkflowContracts","SmartpcaWorkflowFiles"})
   if(!Path.of(Class.forName("org.jlinalg.cli."+name).getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar))throw new AssertionError("Producer differs: "+name);
  Class<?> type=Class.forName("org.jlinalg.cli.SmartpcaWorkflowTest");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var temporary=type.getDeclaredField("temporary");temporary.setAccessible(true);int checked=0;
  for(var method:type.getDeclaredMethods())if(method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
   var instance=constructor.newInstance();temporary.set(instance,Files.createDirectory(root.resolve(method.getName())));method.setAccessible(true);method.invoke(instance);checked++;
  }
  if(checked!=15)throw new AssertionError("Changed workflow test population: "+checked);
  for(String name:new String[]{"org.jlinalg.genetics.SmartpcaExtensionsTest","org.jlinalg.genetics.SmartpcaAnalysisReferenceTest"}) {
   var method=Class.forName(name).getDeclaredMethod("main",String[].class);method.setAccessible(true);method.invoke(null,(Object)new String[0]);
  }
  System.out.println("PASS 15 workflow methods and both original-reference component entry points against exact producer");
 }
}
''',encoding='utf-8')
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 matches=list((cache/module/version).rglob('*.jar'));assert len(matches)==1;junit.append(matches[0])
def linux(path):s=Path(path).as_posix();return '/mnt/'+s[0].lower()+s[2:]
def execute(folder,label,arguments):
 p=subprocess.run(arguments,cwd=T,capture_output=True,timeout=180);(folder/(label+'.stdout')).write_bytes(p.stdout);(folder/(label+'.stderr')).write_bytes(p.stderr);save(folder/(label+'.json'),dict(arguments=arguments,exit_code=p.returncode));assert p.returncode==0,p.stderr;return p
hosts=[]
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'classes';classes.mkdir();cp=[classes,normal/'project-build/classes/java/test',T/'src/test/resources',jar,*junit]
 if host.startswith('windows'):
  compile_args=[home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,junit)),'-d',str(classes),str(source)]
  run_args=[home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'WorkflowHostChecks',str(jar),str(folder/'test-evidence')]
  prefix=[home+'/bin/java.exe','-ea','-cp',str(jar),'org.jlinalg.cli.SmartpcaToolsCli'];convert=lambda p:Path(p).as_posix()
 else:
  launcher=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--']
  compile_args=launcher+[home+'/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',':'.join(map(linux,junit)),'-d',linux(classes),linux(source)]
  run_args=launcher+[home+'/bin/java','-ea','-cp',':'.join(map(linux,cp)),'WorkflowHostChecks',linux(jar),linux(folder/'test-evidence')]
  prefix=launcher+[home+'/bin/java','-ea','-cp',linux(jar),'org.jlinalg.cli.SmartpcaToolsCli'];convert=linux
 execute(folder,'javac',compile_args);execute(folder,'methods',run_args)
 for name in ('run','shrinkage','replay'):
  raw=json.loads((T/'examples/smartpca-native'/(name+'.json')).read_bytes())
  if name=='replay':raw.update(analysis=convert(folder/'example-run'),out=convert(folder/'example-replay'))
  else:
   raw['out']=convert(folder/('example-'+name))
   for key,value in raw['inputs'].items():
    if key in ('genotypes','markers','samples'):raw['inputs'][key]=convert(T/'examples/smartpca-native'/value)
  request=folder/('request-'+name+'.json');save(request,raw)
  execute(folder,'example-'+name,prefix+['--request',convert(request)]);execute(folder,'verify-example-'+name,prefix+['--verify',raw['out']])
 hosts.append(dict(host=host,workflow_methods=15,component_entrypoints=2,copyable_examples=3,example_run_verify_commands=6,all_exit_codes_zero=True))
readers=[]
for name in ('verify_smartpca_component_checkpoint.py','verify_smartpca_analysis_handover.py','verify_mrpresso_catalog_checkpoint.py'):
 execute(out,name,[os.sys.executable,'-X','utf8','-B',str(T/'tools/literature'/name)]);readers.append(dict(reader=name,exit_code=0))
save(out/'COMPLETED.json',dict(status='PASS_THREE_JVM_METHODS_EXAMPLES_AND_FROZEN_READERS',hosts=hosts,predecessor_readers=readers,producer=pin(jar),whole_goal_complete=False,new_family_credit=0,new_reviews=0))
print(json.dumps(dict(hosts=hosts,readers=readers),indent=2))
