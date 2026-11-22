"""Build the normal JAR and compare the native fdensity API with actual reference cases."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
def pin(path):
    with path.open('rb') as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jdistlib-cache',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    project=out/'project-build';classpath=out/'CLASSPATH.txt';init=out/'isolate.gradle';init.write_text("allprojects { p -> if (p == rootProject) p.layout.buildDirectory.set(file('"+project.as_posix()+"')) }\ngradle.projectsEvaluated { rootProject.tasks.register('qtltoolsFdensityClasspath') { dependsOn rootProject.tasks.named('testClasses'); doLast { file('"+classpath.as_posix()+"').text = rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n",encoding='utf-8',newline='\n')
    cached=args.jdistlib_cache.resolve(strict=True);wanted=pin(cached);assert wanted['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81';target=project/'dependencies/jdistlib-all-0.10.2.jar';target.parent.mkdir(parents=True);shutil.copyfile(cached,target);assert pin(target)==wanted
    provenance=json.loads((ROOT/'src/test/resources/org/jlinalg/genetics/qtltools-fdensity/PROVENANCE.json').read_bytes());assert (provenance['cases'],provenance['successful_cases'],provenance['retained_original_errors'])==(576,569,7);assert pin(ROOT/'src/test/resources/org/jlinalg/genetics/qtltools-fdensity/original-controls.tsv.gz')==provenance['fixture']
    names=['src/main/java/org/jlinalg/genetics/QtltoolsFunctionalDensity.java','src/main/java/org/jlinalg/genetics/GenomicIntervals.java','src/test/java/org/jlinalg/genetics/QtltoolsFunctionalDensityTest.java','src/test/java/org/jlinalg/genetics/GenomicIntervalsTest.java','src/test/resources/org/jlinalg/genetics/qtltools-fdensity/original-controls.tsv.gz','src/test/resources/org/jlinalg/genetics/qtltools-fdensity/PROVENANCE.json','tools/literature/qtltools_fdensity/build.py'];names += [path.relative_to(ROOT).as_posix() for path in Path(__file__).parent.glob('*.java')]+[path.relative_to(ROOT).as_posix() for path in Path(__file__).parent.glob('*.py') if path.name!='build.py']
    gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat';command=[gradle,'--no-daemon','--offline','--console=plain','--max-workers=2','--no-configuration-cache','--project-cache-dir',str(out/'project-cache'),'--init-script',str(init),'qtltoolsFdensityClasspath','executableJar',':test','--tests','org.jlinalg.genetics.QtltoolsFunctionalDensityTest','--tests','org.jlinalg.genetics.GenomicIntervalsTest']
    save(out/'STARTED.json',dict(command=command,started_unix=time.time(),source_inputs={name:pin(ROOT/name) for name in names},dependency_cache=dict(path=str(cached),pin=wanted)))
    for name in names:
        path=out/'source'/name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,path)
    env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot',GRADLE_OPTS='-Dorg.gradle.daemon=false -Dfile.encoding=UTF-8')
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1200)
    save(out/'PROCESS.json',dict(exit_code=process.returncode))
    for name,wanted in json.loads((out/'STARTED.json').read_bytes())['source_inputs'].items():assert pin(ROOT/name)==wanted,'Source changed during build: '+name
    if process.returncode:print((out/'stdout').read_text(errors='replace')[-5500:],(out/'stderr').read_text(errors='replace')[-1500:]);return process.returncode
    suites={}
    for path in (project/'test-results/test').glob('TEST-*.xml'):
        node=ET.fromstring(path.read_bytes());suites[node.attrib['name']]={key:int(node.attrib[key]) for key in ['tests','failures','errors','skipped']}
    assert len(suites)==2 and all(row['tests']>0 and row['failures']==row['errors']==row['skipped']==0 for row in suites.values());jar=project/'cli/omicsmatic-0.4.0.jar'
    save(out/'RESULT.json',dict(status='PASS_NORMAL_JAR_NATIVE_QTLTOOLS_FDENSITY_API',tests=sum(row['tests'] for row in suites.values()),suites=suites,producer=pin(jar),test_classpath=classpath.read_text(),reference_cases=576,reference_successes=569,reference_errors_retained=7,reference_output_bins=provenance['output_bins'],CLI_implemented=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS normal JAR and',sum(row['tests'] for row in suites.values()),'focused tests',flush=True);return 0
if __name__=='__main__':raise SystemExit(main())
