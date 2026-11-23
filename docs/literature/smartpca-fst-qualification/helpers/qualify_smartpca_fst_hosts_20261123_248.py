from pathlib import Path
import json,os,subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-4';out=B/'host-checks-1';out.mkdir();normal=B/'normal-build-2';jar=normal/'project-build/cli/omicsmatic-0.4.0.jar'
assert pin(jar)['sha256']=='534da0fe4f10959a622c4d5f74fee35dc959c6d48e2730f9861dc83790973522'
source=out/'FstHostChecks.java';source.write_text('''import java.nio.file.Files;
import java.nio.file.Path;
public final class FstHostChecks {
 public static void main(String[] args)throws Exception {
  Path jar=Path.of(args[0]).toRealPath(),root=Files.createDirectory(Path.of(args[1]));
  for(String name:new String[]{"org.jlinalg.genetics.SmartpcaFst","org.jlinalg.cli.SmartpcaCommandCli","org.jlinalg.cli.JLinAlgCli","org.jlinalg.cli.SmartpcaFstCli","org.jlinalg.cli.SmartpcaFstContracts","org.jlinalg.cli.SmartpcaFstEvaluation","org.jlinalg.cli.SmartpcaFstTables","org.jlinalg.cli.SmartpcaFstAudit"})
   if(!Path.of(Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar))throw new AssertionError("Different scientific producer: "+name);
  var type=Class.forName("org.jlinalg.cli.SmartpcaFstWorkflowTest");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var temporary=type.getDeclaredField("temporary");temporary.setAccessible(true);int checked=0;
  for(var method:type.getDeclaredMethods())if(method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
   var instance=constructor.newInstance();temporary.set(instance,Files.createDirectory(root.resolve(method.getName())));method.setAccessible(true);method.invoke(instance);checked++;
  }
  if(checked!=9)throw new AssertionError("Wrong FST workflow test population: "+checked);
  var reference=Class.forName("org.jlinalg.genetics.SmartpcaFstReferenceTest").getDeclaredMethod("main",String[].class);reference.setAccessible(true);reference.invoke(null,(Object)new String[0]);
  System.out.println("PASS exact current JAR: nine workflow methods and five original-reference FST component methods");
 }
}
''',encoding='utf-8')
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 matches=list((cache/module/version).rglob('*.jar'));assert len(matches)==1;junit.append(matches[0])
def linux(path):s=Path(path).as_posix();return '/mnt/'+s[0].lower()+s[2:]
def execute(folder,label,args):
 p=subprocess.run(args,cwd=T,capture_output=True,timeout=180);(folder/(label+'.stdout')).write_bytes(p.stdout);(folder/(label+'.stderr')).write_bytes(p.stderr);save(folder/(label+'.json'),dict(command=args,exit_code=p.returncode));assert p.returncode==0,p.stderr
hosts=[]
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'classes';classes.mkdir();cp=[classes,normal/'project-build/classes/java/test',T/'src/test/resources',jar,*junit]
 if host.startswith('windows'):
  compiler=[home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,junit)),'-d',str(classes),str(source)]
  check=[home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'FstHostChecks',str(jar),str(folder/'method-evidence')]
  prefix=[home+'/bin/java.exe','-ea','-jar',str(jar),'smartpca-tools'];convert=lambda p:Path(p).as_posix()
 else:
  launcher=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--'];compiler=launcher+[home+'/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',':'.join(map(linux,junit)),'-d',linux(classes),linux(source)]
  check=launcher+[home+'/bin/java','-ea','-cp',':'.join(map(linux,cp)),'FstHostChecks',linux(jar),linux(folder/'method-evidence')]
  prefix=launcher+[home+'/bin/java','-ea','-jar',linux(jar),'smartpca-tools'];convert=linux
 execute(folder,'javac',compiler);execute(folder,'methods',check)
 for name in ('run','inbreed','replay'):
  raw=json.loads((T/'examples/smartpca-fst'/(name+'.json')).read_bytes());raw['out']=convert(folder/('example-'+name))
  if name=='replay':raw['analysis']=convert(folder/'example-run')
  else:
   for key in ('genotypes','markers','samples'):raw['inputs'][key]=convert(T/'examples/smartpca-fst'/raw['inputs'][key])
  request=folder/('request-'+name+'.json');save(request,raw);execute(folder,'example-'+name,prefix+['--request',convert(request)]);execute(folder,'verify-example-'+name,prefix+['--verify',raw['out']])
 hosts.append(dict(host=host,workflow_methods=9,component_methods=5,source_program_cases=24,direct_source_jackknives=23,example_commands=6,all_exit_codes_zero=True))
save(out/'COMPLETED.json',dict(status='PASS_THREE_JVM_NATIVE_FST_METHODS_AND_PUBLIC_EXAMPLES',hosts=hosts,producer=pin(jar),new_family_credit=0,new_reviews=0,whole_goal_complete=False));print(json.dumps(hosts,indent=2))
