from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';attempt=sys.argv[1] if len(sys.argv)>1 else '1';assert attempt.isdecimal();O=B/('normal-gradle-'+attempt);O.mkdir()
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(n,v):
    with (O/n).open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
seed=R/'build/lm-2/build/limma-native-1/normal-gradle-1/project-build/dependencies/jdistlib-all-0.10.2.jar';assert pin(seed)['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
build=O/'project-build';(build/'dependencies').mkdir(parents=True);shutil.copyfile(seed,build/'dependencies'/seed.name)
init=O/'isolate.gradle';init.write_text("gradle.beforeProject { p -> p.layout.buildDirectory.set(new File('"+build.as_posix()+"')); p.afterEvaluate { p.tasks.withType(Test).configureEach { systemProperty 'jlinalg.limma.testOutput', '"+(O/'test-output').as_posix()+"' } } }\ngradle.projectsEvaluated { rootProject.tasks.register('limmaQualificationClasspath') { doLast { new File('"+(O/'test-runtime-classpath.txt').as_posix()+"').text = rootProject.configurations.testRuntimeClasspath.files.collect { it.absolutePath }.sort().join('\\n') + '\\n' } } }\n",encoding='utf-8',newline='\n')
gradle=Path('C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat');jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot')
command=[str(gradle),'--no-daemon','--offline','--console=plain','--max-workers=2','--no-configuration-cache','--project-cache-dir',str(O/'project-cache'),'--init-script',str(init),'limmaQualificationClasspath','executableJar',':test','--tests','org.jlinalg.cli.LimmaWorkflowTest']
names=[p.relative_to(T).as_posix() for p in sorted((T/'src/main/java/org/jlinalg/cli').glob('LimmaWorkflow*.java'))]+['src/main/resources/org/jlinalg/omics/limma_workflow.R','src/test/java/org/jlinalg/cli/LimmaWorkflowTest.java','src/test/resources/org/jlinalg/cli/limma-workflow-originals.zip','tools/literature/limma_workflow/stock_reference.R','build.gradle']+[p.relative_to(T).as_posix() for p in sorted((T/'src/main/resources/org/jlinalg/omics/limma-workflow').glob('*'))]
sources=O/'sources';sources.mkdir()
for n in names:
    target=sources/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/n,target)
inputs={n:pin(T/n) for n in names};save('COMMAND.json',dict(command=command,cwd=str(T),JAVA_HOME=str(jdk),inputs=inputs))
with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:
    p=subprocess.Popen(command,cwd=T,env=dict(os.environ,JAVA_HOME=str(jdk)),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);save('PROCESS.json',dict(pid=p.pid));print('Started normal limma workflow build PID',p.pid,flush=True);code=p.wait()
reports={}
for path in (build/'test-results/test').glob('TEST-*.xml'):
    root=ET.parse(path).getroot();reports[root.attrib['name']]=dict(tests=int(root.attrib['tests']),failures=int(root.attrib['failures']),errors=int(root.attrib['errors']),skipped=int(root.attrib['skipped']),failed_cases=[c.attrib['name'] for c in root.findall('testcase') if c.find('failure') is not None],stdout=root.findtext('system-out',''))
save('COMPLETED.json',dict(exit_code=code,reports=reports,source_inputs=inputs,JARs={p.relative_to(build).as_posix():pin(p) for p in build.rglob('omicsmatic-*.jar')}));print('Normal limma workflow build finished',code,json.dumps(reports),flush=True)
