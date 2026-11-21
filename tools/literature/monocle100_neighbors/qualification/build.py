"""Build the normal executable and run original/native plus neighbor admission controls."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[4]
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    project=out/'project-build';classpath=out/'CLASSPATH.txt';init=out/'isolate.gradle'
    init.write_text("allprojects { p -> if (p == rootProject) p.layout.buildDirectory.set(file('"+project.as_posix()+"')) }\ngradle.projectsEvaluated { rootProject.tasks.register('monocle100NeighborsClasspath') { dependsOn rootProject.tasks.named('testClasses'); doLast { file('"+classpath.as_posix()+"').text = rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n",encoding='utf-8',newline='\n')
    tests=['Monocle100NeighborsContractsTest','Monocle100NeighborsCaptureTest','Monocle100WeightsTest','Monocle100WeightsCliTest','RegressionMoranTest','RegressionMoranCliTest','MonocleRootCliTest','JLinAlgCliTest']
    gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat'
    command=[gradle,'--no-daemon','--offline','--console=plain','--max-workers=2','--no-configuration-cache','--project-cache-dir',str(out/'project-cache'),'--init-script',str(init),'monocle100NeighborsClasspath','executableJar',':test']
    for name in tests:command+=['--tests','org.jlinalg.cli.'+name]
    inputs=[p for pattern in ['src/main/java/org/jlinalg/cli/Monocle100Neighbors*.java','src/main/resources/org/jlinalg/omics/monocle100-neighbors/**/*','src/test/java/org/jlinalg/cli/Monocle100Neighbors*.java','src/test/resources/org/jlinalg/cli/monocle100-neighbors.zip','tools/literature/monocle100_neighbors/qualification/*'] for p in ROOT.glob(pattern) if p.is_file()]
    save(out/'STARTED.json',dict(command=command,started_unix=time.time(),source_inputs={p.relative_to(ROOT).as_posix():pin(p) for p in inputs}))
    for path in inputs:
        target=out/'source'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
    environment=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot',GRADLE_OPTS='-Dorg.gradle.daemon=false -Dfile.encoding=UTF-8')
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,env=environment,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1200)
    save(out/'PROCESS.json',dict(exit_code=process.returncode))
    for name,wanted in json.loads((out/'STARTED.json').read_bytes())['source_inputs'].items():assert pin(ROOT/name)==wanted,'Source changed during build: '+name
    if process.returncode:print((out/'stdout').read_text(errors='replace')[-5000:],(out/'stderr').read_text(errors='replace')[-2000:]);return process.returncode
    suites={}
    for path in (project/'test-results/test').glob('TEST-*.xml'):
        node=ET.fromstring(path.read_bytes());suites[node.attrib['name']]={key:int(node.attrib[key]) for key in ['tests','failures','errors','skipped']}
    assert len(suites)==len(tests) and all(row['tests']>0 and row['failures']==row['errors']==row['skipped']==0 for row in suites.values())
    jar=project/'cli/omicsmatic-0.4.0.jar';save(out/'RESULT.json',dict(status='PASS_NORMAL_JAR_MONOCLE100_NEIGHBOR_CONTROLS',tests=sum(row['tests'] for row in suites.values()),suites=suites,producer=pin(jar),test_classpath=classpath.read_text(),fresh_original_execution=False))
    print('PASS normal executable and',sum(row['tests'] for row in suites.values()),'focused tests',flush=True);return 0
if __name__=='__main__':raise SystemExit(main())
