from pathlib import Path
import json,os,subprocess,time,xml.etree.ElementTree as ET,zipfile
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-1/normal-build-1';follow=B/'configured-followup-1';follow.mkdir()
sources=json.loads((B/'SOURCE-INPUTS.json').read_bytes());assert all(pin(T/p)==v for p,v in sources.items())
def results(folder):
 totals=dict(tests=0,failures=0,errors=0,skipped=0);skips=[]
 for file in folder.glob('TEST-*.xml'):
  root=ET.parse(file).getroot()
  for key in totals:totals[key]+=int(root.get(key,'0'))
  for case in root.findall('testcase'):
   skipped=case.find('skipped')
   if skipped is not None:skips.append(dict(class_name=case.get('classname'),method=case.get('name').removesuffix('()'),reason=skipped.get('message')))
 return totals,skips
initial,skips=results(B/'project-build/test-results/test');assert initial==dict(tests=1871,failures=0,errors=0,skipped=5)
assert {r['class_name'] for r in skips}=={'org.jlinalg.cli.PlinkPcaCliTest','org.jlinalg.genetics.ChainLiftoverTest'}
save(B/'INITIAL-TESTS-WITH-SKIPS.json',dict(actual=initial,skips=skips,original_build_success=True,
 original_classifier_assertion_failed=True,classifier_cause='The initial helper required zero skips; five existing optional real-runtime/reference tests lacked their environment paths. Original XML/stdout remain unchanged.'))
plink=R/'build/omics-plink-official';chains=R/'build/literature-review/liftover-runtime'
runtime_paths=[plink/'.jlinalg-plink.json',plink/'plink1/plink.exe',plink/'plink2/plink2.exe',plink/'LICENSE',
 *[chains/name for name in ('hg19.chrom.sizes','hg38.chrom.sizes','hg19ToHg38.over.chain.gz','hg38ToHg19.over.chain.gz')]]
before={str(p):pin(p) for p in runtime_paths};save(follow/'RUNTIMES-BEFORE.json',before)
build=B/'project-build';init=follow/'init.gradle'
patterns=[r['class_name']+'.'+r['method'] for r in skips]
init.write_text("allprojects { layout.buildDirectory.set(file('"+build.as_posix()+"')); afterEvaluate { tasks.register('smartpcaConfiguredBaseline', org.gradle.api.tasks.testing.Test) {\n"
 "testClassesDirs = sourceSets.test.output.classesDirs; classpath = sourceSets.test.runtimeClasspath; useJUnitPlatform(); maxHeapSize = '1g'\n"
 "reports.junitXml.outputLocation.set(file('"+(follow/'test-results').as_posix()+"')); reports.html.outputLocation.set(file('"+(follow/'html').as_posix()+"'))\n"
 "filter { "+'; '.join("includeTestsMatching('"+p+"')" for p in patterns)+" }\n} } }\n",encoding='utf-8')
gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat'
args=[gradle,'--offline','--no-daemon','--console=plain','--init-script',str(init),'-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m','smartpcaConfiguredBaseline']
env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot',JLINALG_PLINK_HOME=str(plink),JLINALG_LIFTOVER_REFERENCE=str(chains))
save(follow/'COMMAND.json',dict(arguments=args,environment={k:env[k] for k in ('JAVA_HOME','JLINALG_PLINK_HOME','JLINALG_LIFTOVER_REFERENCE')}))
with (follow/'stdout').open('xb') as stdout,(follow/'stderr').open('xb') as stderr:
 process=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);save(follow/'PROCESS.json',dict(pid=process.pid,command=args));code=process.wait()
assert all(pin(Path(p))==v for p,v in before.items()),'Existing retained runtime/reference changed'
assert all(pin(T/p)==v for p,v in sources.items()),'Source changed during configured follow-up'
measured,remaining=results(follow/'test-results');save(follow/'RESULT.json',dict(exit_code=code,actual=measured,remaining_skips=remaining,retained_runtimes_unchanged=True))
assert code==0 and measured==dict(tests=5,failures=0,errors=0,skipped=0),(code,measured)
jar=build/'cli/omicsmatic-0.4.0.jar';prior=T/'build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'
with zipfile.ZipFile(prior) as a,zipfile.ZipFile(jar) as b:
 old=[n for n in a.namelist() if not n.endswith('/')];new=[n for n in b.namelist() if not n.endswith('/')]
 modified=[n for n in old if n not in new or a.read(n)!=b.read(n)];added=[n for n in new if n not in old]
 save(B/'PAYLOADS.json',dict(previous_payloads=len(old),modified=modified,added=added));assert modified==[],modified
 assert all(n.startswith(('org/jlinalg/genetics/SmartpcaPrepared','org/jlinalg/omics/smartpca-4f7e9f0/')) for n in added),added
result=dict(status='PASS_NORMAL_BUILD_1871_TESTS_WITH_FIVE_CONFIGURED_FOLLOWUPS',source_unchanged=True,
 normal_tests_initial=initial,configured_followup=measured,unique_tests_covered=1871,initial_skips_resolved=5,
 producer=dict(path=str(jar),**pin(jar)),all_previous_jar_payloads_unchanged=len(old),new_payloads=added,
 new_family_credit=0,new_reviews=0,workflow_qualified=False,whole_goal_complete=False)
save(B/'RESULT.json',result);print(json.dumps(result,indent=2))
