from pathlib import Path
import hashlib,json,os,shutil,subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';D=T/'build/scenic-1/packaged-check-2';D.mkdir()
normal=json.loads((T/'build/scenic-1/normal-build-2/RESULT.json').read_bytes());jar=Path(normal['producer']['path']);assert pin(jar)=={k:normal['producer'][k] for k in ('bytes','sha256')}
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');junit=[]
for module,version in [('org.junit.jupiter/junit-jupiter-api','5.13.4'),('org.junit.platform/junit-platform-commons','1.13.4'),('org.apiguardian/apiguardian-api','1.1.2'),('org.opentest4j/opentest4j','1.3.0')]:
 matches=list((cache/module/version).rglob('*.jar'));assert len(matches)==1;junit.append(matches[0])
wrapper=D/'PackagedScenicCheck.java';wrapper.write_text('''package org.jlinalg.enrichment;
import java.nio.file.Path;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.Map;
import java.util.List;
import org.jlinalg.gprofiler.GprofilerJson;
public final class PackagedScenicCheck {
 private PackagedScenicCheck() { }
 @SuppressWarnings("unchecked") public static void main(String[] arguments)throws Exception {
  Path expected=Path.of(arguments[0]).toRealPath();
  for(Class<?> type:List.of(PyScenicAucell.class,CisTargetRecovery.class,SignatureScores.class,GprofilerJson.class))if(!Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(expected))throw new AssertionError("Wrong engine origin: "+type);
  String base="/org/jlinalg/omics/pyscenic-ce41b61/";Map<String,Object> source;
  try(var in=PyScenicAucell.class.getResourceAsStream(base+"SOURCE.json")){if(in==null)throw new AssertionError("Source manifest absent");source=(Map<String,Object>)GprofilerJson.read(in.readAllBytes());}
  int files=0;
  for(Object record:(List<Object>)source.get("sources"))for(var entry:((Map<String,Object>)((Map<String,Object>)record).get("files")).entrySet()){
   var wanted=(Map<String,Object>)entry.getValue();byte[] raw;try(var in=PyScenicAucell.class.getResourceAsStream(base+entry.getKey())){if(in==null)throw new AssertionError("Resource absent: "+entry.getKey());raw=in.readAllBytes();}
   if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)).equals(wanted.get("sha256")))throw new AssertionError("Resource differs: "+entry.getKey());files++;
  }
  if(files!=13)throw new AssertionError("Source/notice resource population differs");
  PyScenicAucellReferenceTest.main(new String[0]);Class.forName("PyScenicAucellExample").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
  System.out.println("PASS exact packaged engine origins and13 original source/license resources");
 }
}
''',encoding='utf-8')
source=[T/'src/test/java/org/jlinalg/enrichment/PyScenicAucellReferenceTest.java',T/'examples/pyscenic-aucell/PyScenicAucellExample.java',wrapper];pins={str(p):pin(p) for p in source};hosts=[]
def linux(p):s=Path(p).as_posix();return '/mnt/'+s[0].lower()+s[2:]
for host,home in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'),('windows25','C:/Program Files/Java/jdk-25'),('linux21','/usr')]:
 folder=D/host;folder.mkdir();classes=folder/'classes';classes.mkdir();cp=[classes,T/'src/test/resources',jar,*junit]
 if host.startswith('windows'):
  compiler=[home+'/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,source)];execute=[home+'/bin/java.exe','-ea','-cp',os.pathsep.join(map(str,cp)),'org.jlinalg.enrichment.PackagedScenicCheck',str(jar)]
 else:
  prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--'];compiler=prefix+[home+'/bin/javac','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',':'.join(map(linux,cp)),'-d',linux(classes),*map(linux,source)];execute=prefix+[home+'/bin/java','-ea','-cp',':'.join(map(linux,cp)),'org.jlinalg.enrichment.PackagedScenicCheck',linux(jar)]
 results=[]
 for label,command in [('javac',compiler),('java',execute)]:
  p=subprocess.run(command,cwd=T,capture_output=True,timeout=180);(folder/(label+'.stdout')).write_bytes(p.stdout);(folder/(label+'.stderr')).write_bytes(p.stderr);save(folder/(label+'.json'),dict(command=command,exit_code=p.returncode));results.append(p.returncode);assert p.returncode==0,p.stderr.decode(errors='replace')[-3000:]
 hosts.append(dict(host=host,compiler_exit=results[0],test_exit=results[1],focused_methods=7,example_calls=1,source_resources_checked=13,engine_origins_checked=4));print(json.dumps(hosts[-1]),flush=True)
assert all(pin(Path(n))==wanted for n,wanted in pins.items())
save(D/'COMPLETED.json',dict(status='PASS_EXACT_PACKAGED_NATIVE_AUCELL_COMPONENT_THREE_JVMS',producer=normal['producer'],hosts=hosts,source_pins=pins,full_SCENIC_workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
