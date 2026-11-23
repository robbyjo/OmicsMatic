from pathlib import Path
import concurrent.futures,hashlib,json,os,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1';D=B/'random-component-checks-1';D.mkdir()
source=D/'source';source.mkdir();classes=D/'classes';classes.mkdir();fixture=D/'resources/org/jlinalg/mrpresso/r461-random-reference.zip';fixture.parent.mkdir(parents=True)
inputs=['src/main/java/org/jlinalg/mrpresso/MrPressoRandom.java','src/test/java/org/jlinalg/mrpresso/MrPressoRandomTest.java']
for name in inputs:shutil.copyfile(T/name,source/Path(name).name)
shutil.copyfile(T/'src/test/resources/org/jlinalg/mrpresso/r461-random-reference.zip',fixture)
runner=source/'MrPressoRandomProbe.java';runner.write_text('''package org.jlinalg.mrpresso;
public final class MrPressoRandomProbe {
 public static void main(String[] args)throws Exception {
  var test=new MrPressoRandomTest();test.matchesOriginalR461UniformNormalsSamplingAndEveryContinuationState();test.explicitProfileAndStateAreRequiredAndDefensivelyCopied();
  System.out.println("PASS two component tests; twelve original R stream cases; Java="+System.getProperty("java.version")+"; OS="+System.getProperty("os.name"));
 }
}
''',encoding='utf-8')
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');jars=[R/'build/gp-1/build/dependencies/jdistlib-all-0.10.2.jar']
for group,artifact in [('org.junit.jupiter','junit-jupiter-api'),('org.junit.platform','junit-platform-commons'),('org.opentest4j','opentest4j'),('org.apiguardian','apiguardian-api')]:
    found=sorted((cache/group/artifact).rglob('*.jar'));assert found,(group,artifact);jars.append(found[-1])
assert pin(jars[0])['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
classpath=os.pathsep.join(str(p) for p in jars)
compileargs=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',classpath,'-d',str(classes),*[str(p) for p in sorted(source.glob('*.java'))]]
p=subprocess.run(compileargs,cwd=T,capture_output=True,timeout=30);(D/'compile.stdout').write_bytes(p.stdout);(D/'compile.stderr').write_bytes(p.stderr)
save(D/'COMPILE.json',dict(command=compileargs,exit_code=p.returncode));assert p.returncode==0,p.stderr
def linux(path):
    value=str(path).replace('\\','/');return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value
def run(host):
    paths=[classes,D/'resources',*jars]
    if host=='linux21':args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','java','-cp',':'.join(map(linux,paths)),'org.jlinalg.mrpresso.MrPressoRandomProbe']
    else:
        java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
        args=[java,'-cp',os.pathsep.join(map(str,paths)),'org.jlinalg.mrpresso.MrPressoRandomProbe']
    result=subprocess.run(args,cwd=T,capture_output=True,timeout=30)
    (D/(host+'.stdout')).write_bytes(result.stdout);(D/(host+'.stderr')).write_bytes(result.stderr)
    record=dict(host=host,exit_code=result.returncode,command=args);save(D/(host+'.RESULT.json'),record)
    print(json.dumps(record),flush=True);assert result.returncode==0,result.stderr[-3000:];return record
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:records=list(pool.map(run,('windows21','windows25','linux21')))
assert all(pin(T/n)==pin(source/Path(n).name) for n in inputs)
save(D/'RESULT.json',dict(status='PASS_SOURCE_FROZEN_RANDOM_COMPONENT_THREE_JVMS',cases=records,R_stream_cases_per_JVM=12,component_methods_per_JVM=2,
    source={p.name:pin(p) for p in sorted(source.iterdir())},classes={p.relative_to(classes).as_posix():pin(p) for p in sorted(classes.rglob('*')) if p.is_file()},
    fixture=pin(fixture),dependencies={str(p):pin(p) for p in jars},normal_project_build=False,packaged_CLI_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
