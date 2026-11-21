from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';O=B/('normal-gradle-'+(sys.argv[1] if len(sys.argv)>1 else '1'));O.mkdir(exist_ok=False)
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
build=O/'project-build';(build/'dependencies').mkdir(parents=True);seed=R/'build/gc-4/build/gcta-qualification-1/normal-gradle-2/project-build/dependencies/jdistlib-all-0.10.2.jar';assert pin(seed)['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81';shutil.copyfile(seed,build/'dependencies'/seed.name)
init=O/'isolate.gradle';init.write_text("gradle.beforeProject { p -> p.layout.buildDirectory.set(new File('"+build.as_posix()+"')) }\n",encoding='utf-8',newline='\n')
gradle=Path(json.loads((R/'build/gc-4/build/gcta-qualification-1/normal-gradle-2/COMMAND.json').read_bytes())['command'][0]);jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot')
tests=['org.jlinalg.cli.GctaWorkflowCliTest','org.jlinalg.cli.GctaRemlSummaryAuditTest','org.jlinalg.genetics.GctaHardcallGrmTest','org.jlinalg.genetics.GenomicRelationshipMatrixTest','org.jlinalg.cli.MtcojoConditionalTest','org.jlinalg.cli.GctaMtcojoContractsTest']
command=[str(gradle),'--no-daemon','--offline','--console=plain','--max-workers=2','--project-cache-dir',str(O/'project-cache'),'--init-script',str(init),'executableJar',':test']
for name in tests:command+=['--tests',name]
names=sorted(p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('Gcta*.java'))+['src/main/java/org/jlinalg/genetics/MtcojoConditional.java','src/test/java/org/jlinalg/cli/MtcojoConditionalTest.java','src/test/java/org/jlinalg/cli/GctaMtcojoContractsTest.java','src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip','build.gradle']
for name in names:
    dest=O/'sources'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
inputs={n:pin(T/n) for n in names};save(O/'COMMAND.json',dict(command=command,cwd=str(T),JAVA_HOME=str(jdk),inputs=inputs))
with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:
    child=subprocess.Popen(command,cwd=T,env=dict(os.environ,JAVA_HOME=str(jdk)),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);save(O/'PROCESS.json',dict(pid=child.pid));print('Started mtCOJO normal build PID',child.pid,flush=True);exit_code=child.wait()
reports={}
for path in (build/'test-results/test').glob('TEST-*.xml'):
    root=ET.parse(path).getroot();reports[root.attrib['name']]=dict(tests=int(root.attrib['tests']),failures=int(root.attrib['failures']),errors=int(root.attrib['errors']),skipped=int(root.attrib['skipped']),failed_cases=[c.attrib['name'] for c in root.findall('testcase') if c.find('failure') is not None])
save(O/'COMPLETED.json',dict(exit_code=exit_code,reports=reports,inputs=inputs,JARs={p.relative_to(build).as_posix():pin(p) for p in build.rglob('omicsmatic-*.jar')}));print('MTCOJO normal build finished',exit_code,json.dumps(reports),flush=True)
