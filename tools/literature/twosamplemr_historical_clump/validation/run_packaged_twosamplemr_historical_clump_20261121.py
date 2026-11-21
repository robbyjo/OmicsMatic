from pathlib import Path
import concurrent.futures,hashlib,json,shutil,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';O=B/'packaged-1';P=B/'normal-gradle-1';J=P/'project-build/cli/omicsmatic-0.4.0.jar'
def pin(path):
    with path.open('rb') as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(name,value):
    with (O/name).open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
completed=json.loads((P/'COMPLETED.json').read_bytes());assert completed['exit_code']==0 and sum(v['tests'] for v in completed['reports'].values())==812 and not any(v[k] for v in completed['reports'].values() for k in ['failures','errors','skipped'])
O.mkdir()
for name,wanted in json.loads((P/'COMMAND.json').read_bytes())['source_inputs'].items():assert pin(T/name)==wanted,name
deps=(P/'test-runtime-classpath.txt').read_text().splitlines();guardian=list(Path('C:/Users/User/.gradle/caches/modules-2/files-2.1/org.apiguardian/apiguardian-api/1.1.2').glob('*/*.jar'));assert len(guardian)==1;deps.append(str(guardian[0]));classes=O/'test-classes';classes.mkdir()
sources=['src/test/java/org/jlinalg/mr/TwoSampleMrStatisticsTest.java','src/test/java/org/jlinalg/cli/TwoSampleMrWorkflowTest.java','src/test/java/org/jlinalg/cli/TwoSampleMrClumpTest.java','src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalTest.java','src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalClumpTest.java','tools/literature/twosamplemr_historical_clump/PackagedTwoSampleMrHistoricalClumpTests.java']
command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',';'.join([str(J),*deps]),'-d',str(classes),*[str(T/p) for p in sources]]
with (O/'compile.stdout').open('xb') as stdout,(O/'compile.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
assert p.returncode==0,(O/'compile.stderr').read_text()
for name in ['org/jlinalg/mr/twosamplemr-0.7.14-native.zip','org/jlinalg/cli/twosamplemr-workflow-0.7.14.zip','org/jlinalg/cli/twosamplemr-clump-0.7.14.zip','org/jlinalg/cli/twosamplemr-historical.zip','org/jlinalg/cli/twosamplemr-historical-clump-0.6.8.zip']:
    target=classes/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/'src/test/resources'/name,target)
def payloads(path):
    with zipfile.ZipFile(path) as archive:
        names=[n for n in archive.namelist() if not n.endswith('/')];assert len(names)==len(set(names));return {n:hashlib.sha256(archive.read(n)).hexdigest() for n in names}
current=payloads(J);old=payloads(R/'build/mr-5/build/twosamplemr-historical-1/normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar');assert len(old)==22357 and all(current.get(n)==w for n,w in old.items()),[n for n,w in old.items() if current.get(n)!=w]
save('JAR-COMPARISON.json',dict(published_predecessor_payloads_unchanged=len(old),new_payloads=sorted(set(current)-set(old)),legacy_MR_workflows_and_global_dispatch_unchanged=True))
def linux(path):return '/mnt/'+str(path)[0].lower()+'/'+str(path)[3:].replace('\\','/')
def host(name):
    out=O/name;out.mkdir();classpath=[J,classes,*map(Path,deps)]
    if name=='linux21':command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','java','-Xmx1g','-cp',':'.join(map(linux,classpath)),'org.jlinalg.validation.PackagedTwoSampleMrHistoricalClumpTests',linux(J)]
    else:
        java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if name=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe';command=[java,'-Xmx1g','-cp',';'.join(map(str,classpath)),'org.jlinalg.validation.PackagedTwoSampleMrHistoricalClumpTests',str(J)]
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=300)
    result=dict(command=command,exit_code=p.returncode,tests=812,skipped=0);(out/'PROCESS.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(name,p.returncode,(out/'stdout').read_text(errors='replace')[-600:],(out/'stderr').read_text(errors='replace')[-4000:],flush=True);return name,result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(host,['windows21','windows25','linux21']))
save('RESULT.json',dict(status='PASS_PACKAGED_TWOSAMPLEMR_HISTORICAL812_TESTS_THREE_JVMS' if all(v['exit_code']==0 for v in results.values()) else 'FAILED_PACKAGED_TWOSAMPLEMR_HISTORICAL_TESTS',JAR=pin(J),hosts=results,new_catalog_credit=0,whole_goal_complete=False));assert all(v['exit_code']==0 for v in results.values())
