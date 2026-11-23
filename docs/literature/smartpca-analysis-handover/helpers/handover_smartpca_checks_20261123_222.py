from pathlib import Path
import json, os, subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-2';out=B/'handover-checks-1';out.mkdir()
normal=B/'handover-normal-build-1';jar=normal/'project-build/cli/omicsmatic-0.4.0.jar'
assert pin(jar)=={k:json.loads((normal/'RESULT.json').read_bytes())['producer'][k] for k in ('bytes','sha256')}
source=out/'PackagedSmartpca.java'
source.write_text('''import java.nio.file.Path;
public final class PackagedSmartpca {
 public static void main(String[] args)throws Exception {
  Path jar=Path.of(args[0]).toRealPath();
  for(String name:new String[]{"org.jlinalg.genetics.SmartpcaPrepared","org.jlinalg.genetics.SmartpcaOutliers","org.jlinalg.genetics.SmartpcaShrinkage","org.jlinalg.genetics.SmartpcaAnalysis","org.jlinalg.cli.SmartpcaWorkflowContracts","org.jlinalg.cli.SmartpcaWorkflowData","org.jlinalg.cli.SmartpcaWorkflowFiles","org.jlinalg.cli.SmartpcaWorkflowSource","org.jlinalg.cli.SmartpcaWorkflowEvaluation","org.jlinalg.cli.SmartpcaWorkflowTables"}) {
   if(!Path.of(Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar))throw new AssertionError("Wrong producer: "+name);
  }
  for(String name:new String[]{"org.jlinalg.genetics.SmartpcaExtensionsTest","org.jlinalg.genetics.SmartpcaAnalysisReferenceTest"}) {
   var method=Class.forName(name).getDeclaredMethod("main",String[].class);method.setAccessible(true);method.invoke(null,(Object)new String[0]);
  }
  System.out.println("PASS exact packaged producer; five extension methods plus sixteen finite/two refused original program cases");
 }
}
''',encoding='utf-8')
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 matches=list((cache/module/version).rglob('*.jar'));assert len(matches)==1;junit.append(matches[0])
def linux(path):s=Path(path).as_posix();return '/mnt/'+s[0].lower()+s[2:]
def execute(folder,label,args):
 p=subprocess.run(args,cwd=T,capture_output=True,timeout=300);(folder/(label+'.stdout')).write_bytes(p.stdout);(folder/(label+'.stderr')).write_bytes(p.stderr);save(folder/(label+'.json'),dict(arguments=args,exit_code=p.returncode));return p
rows=[]
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=out/host;folder.mkdir();classes=folder/'classes';classes.mkdir();cp=[classes,normal/'project-build/classes/java/test',T/'src/test/resources',jar,*junit]
 if host.startswith('windows'):
  compile_args=[home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-d',str(classes),str(source)]
  run_args=[home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'PackagedSmartpca',str(jar)]
 else:
  prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--']
  compile_args=prefix+[home+'/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-d',linux(classes),linux(source)]
  run_args=prefix+[home+'/bin/java','-ea','-cp',':'.join(map(linux,cp)),'PackagedSmartpca',linux(jar)]
 compiled=execute(folder,'javac',compile_args);assert compiled.returncode==0,compiled.stderr
 run=execute(folder,'java',run_args);rows.append(dict(host=host,compiler_exit=compiled.returncode,test_exit=run.returncode));assert run.returncode==0,run.stderr
readers=[]
for name in ('verify_smartpca_component_checkpoint.py','verify_mrpresso_catalog_checkpoint.py'):
 p=execute(out,name,[os.sys.executable,'-X','utf8','-B',str(T/'tools/literature'/name)]);readers.append(dict(reader=name,exit_code=p.returncode));assert p.returncode==0,p.stderr
save(out/'RESULT.json',dict(status='PASS_PACKAGED_NUMERICAL_COMPONENTS_AND_FROZEN_READERS',producer=pin(jar),hosts=rows,predecessor_readers=readers,workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(hosts=rows,readers=readers),indent=2))
