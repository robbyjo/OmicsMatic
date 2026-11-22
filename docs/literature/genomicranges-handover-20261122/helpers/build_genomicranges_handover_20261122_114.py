from pathlib import Path
import json,os,shutil,subprocess,xml.etree.ElementTree as ET
from prepare_genomicranges_20261122_114 import R,T,B
from handover_inventory_20261122_111 import pin,save
D=B/'normal-gradle-handover-1';D.mkdir();P=D/'project-build'
init=D/'isolate.gradle'
init.write_text("allprojects { p -> if (p == rootProject) p.layout.buildDirectory.set(file('"+P.as_posix()+"')) }\n",encoding='utf-8')
cache=R/'build/qt-1/build/qtltools-fdensity-1/normal-gradle-2/project-build/dependencies/jdistlib-all-0.10.2.jar'
assert pin(cache)['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
target=P/'dependencies/jdistlib-all-0.10.2.jar';target.parent.mkdir(parents=True);shutil.copyfile(cache,target)
names=['src/main/java/org/jlinalg/genetics/GenomicRangeGeometry.java','src/test/java/org/jlinalg/genetics/GenomicRangeGeometryReferenceTest.java','build.gradle']
names += [p.relative_to(T).as_posix() for p in (T/'src/test/resources/org/jlinalg/genetics/genomicranges-geometry/1.64.0').iterdir()]
sources={n:pin(T/n) for n in names}
for n in names:
 p=D/'source'/n;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/n,p)
command=['C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat','--no-daemon','--offline','--console=plain','--max-workers=2','--no-configuration-cache','--project-cache-dir',str(D/'project-cache'),'--init-script',str(init),'executableJar',':test']
for name in ['org.jlinalg.genetics.GenomicRangeGeometryReferenceTest','org.jlinalg.genetics.GenomicIntervalsTest','org.jlinalg.cli.SkatWorkflowRuntimeTest','org.jlinalg.cli.SkatWorkflowCliTest','org.jlinalg.settest.RSourceColumnSumTest','org.jlinalg.cli.Acat091ReferenceTest','org.jlinalg.cli.AcatToolsCliTest','org.jlinalg.settest.RLongDoubleSumTest','org.jlinalg.settest.Acat091Test','org.jlinalg.settest.AcatTestsTest','org.jlinalg.settest.AcatLogTest']:
 command += ['--tests',name]
save(D/'STARTED.json',{'command':command,'source_inputs':sources,'scope':'Narrow new geometry component and unchanged SKAT/ACAT/interval regressions;not GenomicRanges adapter qualification','new_catalog_credit':0})
env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot',GRADLE_OPTS='-Dorg.gradle.daemon=false -Dfile.encoding=UTF-8')
with (D/'stdout').open('xb') as out,(D/'stderr').open('xb') as err:
 p=subprocess.run(command,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err,timeout=1200)
save(D/'PROCESS.json',{'exit_code':p.returncode});assert sources=={n:pin(T/n) for n in names}
suites={}
for f in (P/'test-results/test').glob('TEST-*.xml'):
 node=ET.fromstring(f.read_bytes());suites[node.attrib['name']]={k:int(node.attrib[k]) for k in ['tests','failures','errors','skipped']}
result={'exit_code':p.returncode,'suites':suites,'tests':sum(x['tests'] for x in suites.values()),'failures':sum(x['failures']+x['errors'] for x in suites.values()),'new_catalog_credit':0,'GenomicRanges_adapter_qualified':False,'whole_goal_complete':False}
jar=P/'cli/omicsmatic-0.4.0.jar'
if jar.is_file():result['development_jar']=pin(jar)
save(D/'RESULT.json',result);print(json.dumps(result),flush=True)
if p.returncode:print((D/'stdout').read_text(errors='replace')[-4000:]+(D/'stderr').read_text(errors='replace')[-2000:])
raise SystemExit(p.returncode)
