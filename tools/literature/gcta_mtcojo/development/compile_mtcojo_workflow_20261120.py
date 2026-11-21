from pathlib import Path
import hashlib,json,shutil,subprocess,sys
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';O=B/('compile-'+(sys.argv[1] if len(sys.argv)>1 else '1'));O.mkdir(exist_ok=False)
jar=R/'build/gc-4/build/gcta-qualification-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';J=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin');classes=O/'classes';classes.mkdir()
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
names=sorted(p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('Gcta*.java'))
for name in names:
    dest=O/'sources'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
command=[str(J/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*[str(T/n) for n in names]]
with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
(O/'COMPLETED.json').write_text(json.dumps(dict(status='DEVELOPMENT_COMPILATION_ONLY_NOT_NORMAL_PRODUCER',command=command,exit_code=p.returncode,inputs={n:pin(T/n) for n in names},dependency_producer=pin(jar)),indent=2)+'\n',encoding='utf-8');print('mtCOJO development compilation',p.returncode,flush=True)
if p.returncode:print((O/'stderr').read_text(errors='replace')[-5000:],flush=True)
