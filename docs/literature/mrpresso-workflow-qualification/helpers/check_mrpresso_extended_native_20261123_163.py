from pathlib import Path
import concurrent.futures,json,os,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1';D=B/('extended-component-checks-'+sys.argv[1]);D.mkdir()
source=D/'source';source.mkdir();classes=D/'classes';classes.mkdir();resource=D/'resources/org/jlinalg/mrpresso';resource.mkdir(parents=True)
inputs=[*(T/'src/main/java/org/jlinalg/mrpresso').glob('*.java'),*(T/'src/test/java/org/jlinalg/mrpresso').glob('*.java')]
for p in inputs:shutil.copyfile(p,source/p.name)
for p in (T/'src/test/resources/org/jlinalg/mrpresso').glob('*.zip'):shutil.copyfile(p,resource/p.name)
runner=source/'MrPressoExtendedProbe.java';runner.write_text('''package org.jlinalg.mrpresso;
public final class MrPressoExtendedProbe {
 public static void main(String[] args)throws Exception {
  int count=0;
  for(Class<?> type:java.util.List.of(MrPressoRandomTest.class,MrPressoOriginalTest.class,MrPressoExtendedReferenceTest.class)){
   Object instance=type.getDeclaredConstructor().newInstance();
   for(var method:type.getDeclaredMethods())if(method.isAnnotationPresent(org.junit.jupiter.api.Test.class)){
    System.out.println("RUN "+type.getSimpleName()+"."+method.getName());
    try{method.invoke(instance);}catch(java.lang.reflect.InvocationTargetException failure){throw new RuntimeException(method.getName(),failure.getCause());}count++;
   }
  }
  System.out.println("PASS "+count+" component methods; Java="+System.getProperty("java.version")+"; OS="+System.getProperty("os.name"));
 }
}
''',encoding='utf-8')
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');producer=T/'build/mrpresso-1/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar';jars=[producer]
assert pin(producer)['sha256']=='0e44e4609a0895c2d2f67e5034325e8563531015b8ae6f59775ff7deb513ce85'
for group,artifact in [('org.junit.jupiter','junit-jupiter-api'),('org.junit.platform','junit-platform-commons'),('org.opentest4j','opentest4j'),('org.apiguardian','apiguardian-api')]:
 found=sorted((cache/group/artifact).rglob('*.jar'));assert found;jars.append(found[-1])
args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*[str(p) for p in sorted(source.glob('*.java'))]]
p=subprocess.run(args,cwd=T,capture_output=True,timeout=30);(D/'compile.stdout').write_bytes(p.stdout);(D/'compile.stderr').write_bytes(p.stderr);save(D/'COMPILE.json',dict(command=args,exit_code=p.returncode));assert p.returncode==0,p.stderr
def linux(path):
 value=Path(path).as_posix();return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value
def run(host):
 paths=[classes,D/'resources',*jars]
 if host=='linux21':command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx512m','-cp',':'.join(map(linux,paths)),'org.jlinalg.mrpresso.MrPressoExtendedProbe']
 else:
  java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
  command=[java,'-Xmx512m','-cp',os.pathsep.join(map(str,paths)),'org.jlinalg.mrpresso.MrPressoExtendedProbe']
 with (D/(host+'.stdout')).open('xb') as out,(D/(host+'.stderr')).open('xb') as err:
  p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(host+'.PROCESS.json'),dict(pid=p.pid,command=command));code=p.wait()
 row=dict(host=host,exit_code=code);save(D/(host+'.RESULT.json'),row);print(json.dumps(row),flush=True);return row
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:records=list(pool.map(run,('windows21','windows25','linux21')))
assert all(pin(p)==pin(source/p.name) for p in inputs)
save(D/'RESULT.json',dict(status='PASS' if all(x['exit_code']==0 for x in records) else 'FAIL_RETAINED_NATIVE_COMPARISON',cases=records,
 sources={p.name:pin(p) for p in sorted(source.iterdir())},fixtures={p.name:pin(p) for p in resource.iterdir()},original_public_api_calls=45,
 whole_workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
assert all(x['exit_code']==0 for x in records),'Inspect the retained cross-host failures'
