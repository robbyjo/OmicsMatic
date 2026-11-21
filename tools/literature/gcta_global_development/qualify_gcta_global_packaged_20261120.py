from pathlib import Path
import concurrent.futures,hashlib,json,shutil,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';G=B/'normal-gradle-1';O=B/'packaged-global-1';O.mkdir();JAR=G/'project-build/cli/omicsmatic-0.4.0.jar';OLD=R/'build/gc-5/build/gcta-mtcojo-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
record=json.loads((G/'COMPLETED.json').read_bytes());assert record['exit_code']==0 and sum(v['tests'] for v in record['reports'].values())==27
for name,w in record['inputs'].items():assert pin(T/name)==w,name
with zipfile.ZipFile(OLD) as old,zipfile.ZipFile(JAR) as new:
    assert len(new.namelist())==len(set(new.namelist()))==len(old.namelist())==23548 and set(new.namelist())==set(old.namelist())
    changed=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert changed==['org/jlinalg/cli/JLinAlgCli.class'],changed
payloads=dict(unchanged_payloads=23547,changed=changed,added=[],removed=[]);save(O/'PAYLOADS.json',payloads)
deps=O/'junit';shutil.copytree(R/'build/gc-4/build/gcta-qualification-1/packaged-qualification-1/junit',deps);classes=O/'test-classes';classes.mkdir();source=G/'project-build/classes/java/test'
for folder,pattern in [('cli','GctaGlobalDispatchTest*.class'),('cli','GctaRemlSummaryAuditTest*.class'),('cli','GctaWorkflowCliTest*.class'),('cli','MtcojoConditionalTest*.class'),('cli','GctaMtcojoContractsTest*.class'),('cli','GctaMtcojoWorkflowTest*.class'),('genetics','GctaHardcallGrmTest*.class'),('genetics','GenomicRelationshipMatrixTest*.class')]:
    for p in (source/'org/jlinalg'/folder).glob(pattern):
        target=classes/p.relative_to(source);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
for name in ['org/jlinalg/cli/gcta1941-reml-captures.zip','org/jlinalg/genetics/gcta1941-hardcall-originals.zip','org/jlinalg/cli/mtcojo1941-workflow-captures.zip','org/jlinalg/genetics/mtcojo1941-conditional-originals.zip']:
    target=O/'resources'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/'src/test/resources'/name,target)
J=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin');launcher=O/'launcher';launcher.mkdir();command=[str(J/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',str(deps/'*'),'-d',str(launcher),str(T/'tools/literature/gcta_workflows/PackagedGctaGlobalTests.java')];p=subprocess.run(command,capture_output=True,timeout=60);(O/'javac.stdout').write_bytes(p.stdout);(O/'javac.stderr').write_bytes(p.stderr);assert p.returncode==0
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def java(host):return ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/java'] if host=='linux21' else [str(J/'java.exe') if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe']
captures=[];PRE=R/'build/gc-3/build/gcta-workflows-1/normal-workflow-runs-1'
for host in ['windows21','linux21']:
    for row in json.loads((PRE/host/'RESULTS.json').read_bytes()):
        if row['exit_code']==0:captures.append(PRE/host/row['name'])
assert len(captures)==56
PRE=R/'build/gc-5/build/gcta-mtcojo-1/normal-workflow-runs-1'
for host in ['windows','linux']:
    for row in json.loads((PRE/host/'RESULTS.json').read_bytes()):captures.append(PRE/host/row['name'])
PRE=R/'build/gc-6/build/mtcojo-qualification-1/execution-paths-1'
for host in ['windows21','windows25','linux21']:
    for row in json.loads((PRE/host/'RESULTS.json').read_bytes()):
        if row['exit_code']==0:captures.append(PRE/host/(row['name']+'-bundle'))
assert len(captures)==82
def task(host):
    folder=O/host;folder.mkdir();get=lambda p:linux(p) if host=='linux21' else str(p);classpath=(':'.join(linux(p) for p in [JAR,launcher,classes,O/'resources',deps/'*']) if host=='linux21' else ';'.join(str(p) for p in [JAR,launcher,classes,O/'resources',deps/'*']));command=java(host)+['-cp',classpath,'PackagedGctaGlobalTests']
    with (folder/'tests.stdout').open('xb') as stdout,(folder/'tests.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
    assert p.returncode==0,(host,(folder/'tests.stdout').read_text(errors='replace')[-2500:]);rows=[]
    for i,capture in enumerate(captures):
        command=java(host)+['-jar',get(JAR),'gcta-tools','--verify',get(capture)]
        with (folder/(str(i)+'.stdout')).open('xb') as stdout,(folder/(str(i)+'.stderr')).open('xb') as stderr:v=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        row=dict(capture=str(capture),command=command,exit_code=v.returncode);rows.append(row);(folder/'PROGRESS.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8');assert v.returncode==0,(host,capture)
    save(folder/'RESULTS.json',rows);print('PASS GCTA global packaged',host,'27 tests;82 prior captures verified through global command',flush=True);return host,dict(tests=27,test_exit=p.returncode,global_capture_verifications=rows)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(task,['windows21','windows25','linux21']))
save(O/'COMPLETED.json',dict(status='PASS_27_PACKAGED_TESTS_AND_82_GLOBAL_CAPTURE_VERIFICATIONS_PER_JVM',producer=pin(JAR),previous_producer=pin(OLD),payloads=payloads,results=results,new_catalog_credit=0))
