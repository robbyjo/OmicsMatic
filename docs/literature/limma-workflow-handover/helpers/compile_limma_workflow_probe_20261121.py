from pathlib import Path
import hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'compile-probe-1';O.mkdir();classes=O/'classes';classes.mkdir();jar=R/'build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar'
sources=sorted((T/'src/main/java/org/jlinalg/cli').glob('LimmaWorkflow*.java'));command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),*[str(p) for p in sources]]
p=subprocess.run(command,cwd=T,capture_output=True,timeout=120);(O/'stdout').write_bytes(p.stdout);(O/'stderr').write_bytes(p.stderr)
(O/'COMPLETED.json').write_text(json.dumps(dict(command=command,exit_code=p.returncode,source_sha256={p.relative_to(T).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}),indent=2)+'\n',encoding='utf-8',newline='\n');print(p.returncode,p.stderr.decode(errors='replace')[-5000:],flush=True)
