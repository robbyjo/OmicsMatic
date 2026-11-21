"""Build current normal executable and run the focused native/original-source controls."""
from pathlib import Path
import argparse,json,os,subprocess,time,xml.etree.ElementTree as ET
from setup import ROOT,pin,require,save

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve()
    require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW workspace build output');out.mkdir(parents=True,exist_ok=False)
    init=out/'isolate.gradle';project_build=out/'project-build';classpath=out/'CLASSPATH.txt'
    init.write_text("allprojects { p -> if (p == rootProject) p.layout.buildDirectory.set(file('"+project_build.as_posix()+"')) }\ngradle.projectsEvaluated { rootProject.tasks.register('monocle100QualificationClasspath') { dependsOn rootProject.tasks.named('testClasses'); doLast { file('"+classpath.as_posix()+"').text = rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n",encoding='utf-8',newline='\n')
    gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat'
    tests=['Monocle100WeightsTest','Monocle100WeightsCliTest','RegressionMoranTest','RegressionMoranCliTest','MonocleRootCliTest','JLinAlgCliTest']
    command=[gradle,'--no-daemon','--offline','--console=plain','--max-workers=2','--no-configuration-cache','--project-cache-dir',str(out/'project-cache'),'--init-script',str(init),'monocle100QualificationClasspath','executableJar',':test']
    for test in tests:command+=['--tests','org.jlinalg.cli.'+test]
    sources=[path for pattern in ['src/main/java/org/jlinalg/**/Monocle100*.java','src/test/java/org/jlinalg/cli/Monocle100*.java','src/test/resources/org/jlinalg/singlecell/monocle100/*','tools/literature/monocle100/*'] for path in ROOT.glob(pattern) if path.is_file()]
    save(out/'STARTED.json',dict(command=command,source_inputs={path.relative_to(ROOT).as_posix():pin(path) for path in sources},started_unix=time.time()))
    environment=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot',GRADLE_OPTS='-Dorg.gradle.daemon=false -Dfile.encoding=UTF-8')
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,env=environment,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1200)
    save(out/'PROCESS.json',dict(exit_code=process.returncode))
    if process.returncode:print((out/'stdout').read_text(errors='replace')[-5000:],(out/'stderr').read_text(errors='replace')[-2000:]);return process.returncode
    suites={}
    for path in (project_build/'test-results/test').glob('TEST-*.xml'):
        node=ET.fromstring(path.read_bytes());suites[node.attrib['name']]={key:int(node.attrib[key]) for key in ['tests','failures','errors','skipped']}
    require(len(suites)==len(tests) and all(row['tests']>0 and row['failures']==row['errors']==row['skipped']==0 for row in suites.values()),'Incomplete focused normal-build controls')
    jar=project_build/'cli/omicsmatic-0.4.0.jar';require(jar.is_file(),'Missing normal executable producer')
    save(out/'RESULT.json',dict(status='PASS_NORMAL_JAR_MONOCLE100_SOURCE_CONTROLS',tests=sum(row['tests'] for row in suites.values()),suites=suites,producer=pin(jar),test_classpath=classpath.read_text(),fresh_original_R_execution=False))
    print('PASS normal producer',sum(row['tests'] for row in suites.values()),'focused tests');return 0

if __name__=='__main__':raise SystemExit(main())
