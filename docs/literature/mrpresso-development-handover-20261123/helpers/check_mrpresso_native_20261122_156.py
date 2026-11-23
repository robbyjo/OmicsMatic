from pathlib import Path
import concurrent.futures,json,os,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1';D=B/('native-component-checks-'+sys.argv[1]);D.mkdir()
source=D/'source';source.mkdir();classes=D/'classes';classes.mkdir();resource=D/'resources/org/jlinalg/mrpresso';resource.mkdir(parents=True)
inputs=[*(T/'src/main/java/org/jlinalg/mrpresso').glob('*.java'),*(T/'src/test/java/org/jlinalg/mrpresso').glob('*.java')]
for path in inputs:shutil.copyfile(path,source/path.name)
for path in (T/'src/test/resources/org/jlinalg/mrpresso').glob('*.zip'):shutil.copyfile(path,resource/path.name)
runner=source/'MrPressoNativeProbe.java';runner.write_text('''package org.jlinalg.mrpresso;
public final class MrPressoNativeProbe {
 public static void main(String[] args)throws Exception {
  var random=new MrPressoRandomTest();random.matchesOriginalR461UniformNormalsSamplingAndEveryContinuationState();random.explicitProfileAndStateAreRequiredAndDefensivelyCopied();
  new MrPressoOriginalTest().comparesWholeFiniteNativeAnalysisAndRngAgainstTwentyOriginalPublicApiReturns();
  System.out.println("PASS three component tests; twelve original R stream cases and25 original MR-PRESSO calls; Java="+System.getProperty("java.version")+"; OS="+System.getProperty("os.name"));
 }
}
''',encoding='utf-8')
cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');producer=T/'build/squidpy-1/workflow-normal-build-145/project-build/cli/omicsmatic-0.4.0.jar';jars=[producer]
assert pin(producer)['sha256']=='05c3c3d7c4a043fa5233a7a0a1c92d1dba4d27ba57d732e5dc353603fe345c9d'
for group,artifact in [('org.junit.jupiter','junit-jupiter-api'),('org.junit.platform','junit-platform-commons'),('org.opentest4j','opentest4j'),('org.apiguardian','apiguardian-api')]:
    found=sorted((cache/group/artifact).rglob('*.jar'));assert found;jars.append(found[-1])
compileargs=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*[str(p) for p in sorted(source.glob('*.java'))]]
p=subprocess.run(compileargs,cwd=T,capture_output=True,timeout=30);(D/'compile.stdout').write_bytes(p.stdout);(D/'compile.stderr').write_bytes(p.stderr)
save(D/'COMPILE.json',dict(command=compileargs,exit_code=p.returncode));assert p.returncode==0,p.stderr
def linux(path):
    value=str(path).replace('\\','/');return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value
def run(host):
    paths=[classes,D/'resources',*jars]
    if host=='linux21':args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','java','-Xmx512m','-cp',':'.join(map(linux,paths)),'org.jlinalg.mrpresso.MrPressoNativeProbe']
    else:
        java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
        args=[java,'-Xmx512m','-cp',os.pathsep.join(map(str,paths)),'org.jlinalg.mrpresso.MrPressoNativeProbe']
    with (D/(host+'.stdout')).open('xb') as out,(D/(host+'.stderr')).open('xb') as err:
        p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(host+'.PROCESS.json'),dict(pid=p.pid,command=args));code=p.wait()
    record=dict(host=host,exit_code=code);save(D/(host+'.RESULT.json'),record);print(json.dumps(record),flush=True)
    return record
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:records=list(pool.map(run,('windows21','windows25','linux21')))
assert all(pin(p)==pin(source/p.name) for p in inputs)
save(D/'RESULT.json',dict(status='PASS' if all(r['exit_code']==0 for r in records) else 'FAIL_RETAINED_NATIVE_COMPARISON',cases=records,
    sources={p.name:pin(p) for p in sorted(source.iterdir())},fixtures={p.name:pin(p) for p in resource.iterdir()},
    original_public_API_calls=25,finite_source_returns_for_native_comparison=20,original_errors=4,source_aliased_return_separately_unreportable=1,
    normal_project_build=False,packaged_CLI_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
assert all(r['exit_code']==0 for r in records),'Inspect retained per-host native/source comparison failures'
