from pathlib import Path
import hashlib,json,shutil,subprocess
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-compile-5';B.mkdir();C=B/'classes';C.mkdir();res=T/'src/main/resources/org/jlinalg/omics/fusion-9346c122';p=res/'PROFILE.json';profile=json.loads(p.read_bytes());profile['members']['gemma-entry.sh']={'resource':'gemma-entry.sh'}
for entry in profile['members'].values():
 raw=(res/entry['resource']).read_bytes();entry.update(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
p.write_text(json.dumps(profile,indent=2)+'\n',encoding='utf-8')
resources=B/'resources/org/jlinalg/omics/fusion-9346c122';shutil.copytree(res,resources)
sources=sorted((T/'src/main/java/org/jlinalg/cli').glob('FusionWorkflow*.java'))+[T/'src/main/java/org/jlinalg/cli/JLinAlgCli.java']
for source in sources:
 dest=B/'source-context'/source.relative_to(T);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
jar=F/'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'
args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',str(jar),'-d',str(C),*map(str,sources)]
p=subprocess.run(args,cwd=T,capture_output=True);(B/'stdout').write_bytes(p.stdout);(B/'stderr').write_bytes(p.stderr);(B/'RESULT.json').write_text(json.dumps(dict(exit_code=p.returncode,source_files=len(sources),frozen_resources=True),indent=2)+'\n');print(p.returncode,p.stderr.decode(errors='replace'));assert p.returncode==0