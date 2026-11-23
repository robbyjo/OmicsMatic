from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time,zipfile
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1'/('normal-build-'+sys.argv[1]);B.mkdir()
context=B/'source-context'
for base,pattern in [('src/main/java/org/jlinalg/cli','MrPresso*.java'),('src/test/java/org/jlinalg/cli','MrPresso*.java'),('src/main/java/org/jlinalg/mrpresso','*.java'),('src/test/java/org/jlinalg/mrpresso','*.java')]:
    for path in (T/base).glob(pattern):
        dest=context/path.relative_to(T);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
for base in ('src/main/resources/org/jlinalg/omics/mrpresso-3e3c92d','src/test/resources/org/jlinalg/mrpresso'):
    for path in (T/base).rglob('*'):
        if path.is_file():dest=context/path.relative_to(T);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
path=T/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';dest=context/path.relative_to(T);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
inputs={p.relative_to(T).as_posix():pin(p) for base in ('src','build.gradle','settings.gradle','gradle.properties') for p in ([T/base] if (T/base).is_file() else (T/base).rglob('*')) if p.is_file()}
save(B/'SOURCE-INPUTS.json',inputs);build=B/'project-build';dep=build/'dependencies/jdistlib-all-0.10.2.jar';dep.parent.mkdir(parents=True)
shutil.copyfile(R/'build/gp-1/build/dependencies/jdistlib-all-0.10.2.jar',dep);assert pin(dep)['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
init=B/'init.gradle';init.write_text("allprojects { layout.buildDirectory.set(file('"+build.as_posix()+"')) }\n")
gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat'
args=[gradle,'--offline','--no-daemon','--console=plain','--init-script',str(init),'-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m','test']
for selected in ('org.jlinalg.mrpresso.*','org.jlinalg.cli.MrPressoWorkflow*','org.jlinalg.cli.SquidpyWorkflow*','org.jlinalg.squidpy.*','org.jlinalg.spatial.*','org.jlinalg.fusion.*','org.jlinalg.mr.ExtendedMendelianRandomizationTest','org.jlinalg.mr.MultivariateMendelianRandomizationTest'):args+=['--tests',selected]
args+=['executableJar'];env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot');save(B/'COMMAND.json',dict(arguments=args,JAVA_HOME=env['JAVA_HOME']));start=time.monotonic()
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:
    process=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(B/'PROCESS.json',dict(pid=process.pid,command=args));code=process.wait()
assert all(pin(T/n)==identity for n,identity in inputs.items()),'Source changed during native normal build'
result=dict(exit_code=code,seconds=time.monotonic()-start,source_unchanged=True,source_files=len(inputs),full_workflow_qualified=False,new_family_credit=0,new_reviews=0)
if code==0:
    jar=build/'cli/omicsmatic-0.4.0.jar';result['producer']=dict(path=str(jar),**pin(jar));prior=T/'build/squidpy-1/workflow-normal-build-145/project-build/cli/omicsmatic-0.4.0.jar'
    with zipfile.ZipFile(prior) as a,zipfile.ZipFile(jar) as b:
        old=[n for n in a.namelist() if not n.endswith('/')];modified=[n for n in old if n not in b.namelist() or a.read(n)!=b.read(n)];added=[n for n in b.namelist() if not n.endswith('/') and n not in a.namelist()]
        save(B/'PAYLOADS.json',dict(previous_payloads=len(old),modified=modified,added=added));assert modified==['org/jlinalg/cli/JLinAlgCli.class'],modified
        assert all(n.startswith(('org/jlinalg/mrpresso/','org/jlinalg/cli/MrPresso','org/jlinalg/omics/mrpresso-3e3c92d/')) for n in added),added
save(B/'RESULT.json',result);print(json.dumps(result),flush=True)
if code:print((B/'stderr').read_text(errors='replace')[-3500:],flush=True)
raise SystemExit(code)
