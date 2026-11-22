from pathlib import Path
import hashlib,json,os,shutil,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');B=T/'build/fusion-1/workflow-normal-build-1';B.mkdir();project=B/'project-build';(project/'dependencies').mkdir(parents=True)
def pin(p):
 with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
dep=T/'build/dependencies/jdistlib-all-0.10.2.jar';assert pin(dep)['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81';shutil.copyfile(dep,project/'dependencies'/dep.name)
init=B/'init.gradle';init.write_text("allprojects { layout.buildDirectory.set(file('"+project.as_posix()+"')) }\n",encoding='utf-8')
paths=[p for folder in ['src/main/java','src/main/resources','src/test/java','src/test/resources'] for p in (T/folder).rglob('*') if p.is_file()]+[T/'build.gradle'];pins={p.relative_to(T).as_posix():pin(p) for p in sorted(paths)};(B/'SOURCE-INPUTS.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8')
for name in pins:
 if '/fusion/' in name or '/FusionWorkflow' in name or '/fusion-9346c122/' in name or name.endswith('/JLinAlgCli.java') or name.endswith('/OwnedProcesses.java'):
  dest=B/'source-context'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat';args=[gradle,'--offline','--no-daemon','--console=plain','--init-script',str(init),'-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m','test','--tests','org.jlinalg.fusion.*','--tests','org.jlinalg.xwas.*','--tests','org.jlinalg.cli.FusionWorkflowContractsTest','executableJar'];env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot');(B/'COMMAND.json').write_text(json.dumps(dict(arguments=args,JAVA_HOME=env['JAVA_HOME'],source_files=len(pins)),indent=2)+'\n',encoding='utf-8');started=time.time()
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:
 p=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid,started_unix=started),indent=2)+'\n');code=p.wait()
jar=project/'cli/omicsmatic-0.4.0.jar';result=dict(exit_code=code,source_inputs_unchanged=all(pin(T/n)==v for n,v in pins.items()),seconds=time.time()-started,development_jar=pin(jar) if jar.is_file() else None)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
if code:print((B/'stdout').read_text(errors='replace')[-2000:],(B/'stderr').read_text(errors='replace')[-2000:],flush=True)
assert code==0 and result['source_inputs_unchanged']