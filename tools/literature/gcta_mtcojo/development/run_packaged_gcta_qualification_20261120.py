from pathlib import Path
import concurrent.futures,hashlib,json,shutil,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-4';B=T/'build/gcta-qualification-1';G=B/'normal-gradle-2';O=B/'packaged-qualification-1';O.mkdir(exist_ok=False)
PRE=R/'build/gc-3/build/gcta-workflows-1';jar=G/'project-build/cli/omicsmatic-0.4.0.jar';oldjar=PRE/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
record=json.loads((G/'COMPLETED.json').read_bytes());assert record['exit_code']==0 and sum(v['tests'] for v in record['reports'].values())==18
for name,wanted in record['inputs'].items():assert pin(T/name)==wanted,name
with zipfile.ZipFile(oldjar) as old,zipfile.ZipFile(jar) as new:
    assert len(old.namelist())==len(new.namelist())==len(set(new.namelist()))==23541
    assert set(old.namelist())==set(new.namelist())
    changed=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert changed==['org/jlinalg/cli/GctaWorkflowAudit.class'],changed
payloads=dict(old_payloads_unchanged=23540,changed_classes=changed,added=[],removed=[]);save(O/'PAYLOADS.json',payloads);print('PASS23540 unchanged producer payloads;only model export audit class changed',flush=True)
deps=O/'junit';shutil.copytree(B/'summary-red-baseline-2/junit',deps)
classes=O/'test-classes';classes.mkdir();testroot=G/'project-build/classes/java/test'
for folder,pattern in [('cli','GctaRemlSummaryAuditTest*.class'),('cli','GctaWorkflowCliTest*.class'),('genetics','GctaHardcallGrmTest*.class'),('genetics','GenomicRelationshipMatrixTest*.class')]:
    for p in (testroot/'org/jlinalg'/folder).glob(pattern):
        dest=classes/p.relative_to(testroot);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
for name in ['org/jlinalg/cli/gcta1941-reml-captures.zip','org/jlinalg/genetics/gcta1941-hardcall-originals.zip']:
    dest=O/'resources'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/'src/test/resources'/name,dest)
J=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin');launcher=O/'launcher-classes';launcher.mkdir()
command=[str(J/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',str(deps/'*'),'-d',str(launcher),str(T/'tools/literature/gcta_workflows/PackagedQualificationTests.java')]
p=subprocess.run(command,capture_output=True,timeout=60);(O/'javac.stdout').write_bytes(p.stdout);(O/'javac.stderr').write_bytes(p.stderr);assert p.returncode==0,(O/'javac.stderr').read_text()
def linux(p):return '/mnt/d/'+p.relative_to(Path('D:/')).as_posix()
def prefix(host,elements):return ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/java','-cp',':'.join(linux(p) for p in elements)] if host=='linux21' else [str(J/'java.exe') if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe','-cp',';'.join(str(p) for p in elements)]
def task(host):
    base=O/host;base.mkdir();command=prefix(host,[jar,launcher,classes,O/'resources',deps/'*'])+['PackagedQualificationTests']
    with (base/'tests.stdout').open('xb') as stdout,(base/'tests.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=O,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
    assert p.returncode==0,(host,(base/'tests.stdout').read_text(errors='replace')[-3000:]);assert 'GCTA_QUALIFICATION_REGRESSION_PASS tests=18' in (base/'tests.stdout').read_text()
    results=[]
    for original_host in ['windows21','linux21']:
        for row in json.loads((PRE/'normal-workflow-runs-1'/original_host/'RESULTS.json').read_bytes()):
            if row['exit_code']!=0:continue
            path=PRE/'normal-workflow-runs-1'/original_host/row['name'];name=original_host+'-'+row['name'];command2=prefix(host,[jar])+['org.jlinalg.cli.GctaWorkflowCli','--verify',linux(path) if host=='linux21' else str(path)]
            with (base/(name+'.stdout')).open('xb') as stdout,(base/(name+'.stderr')).open('xb') as stderr:v=subprocess.run(command2,cwd=O,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
            result=dict(capture=str(path),command=command2,exit_code=v.returncode);results.append(result);save(base/'PROGRESS.json',results);assert v.returncode==0,(host,name)
    assert len(results)==56;save(base/'RESULTS.json',dict(packaged_tests_command=command,packaged_tests_exit=p.returncode,existing_captures=results));print('PASS',host,'18 packaged tests;56 complete existing captures verify unchanged',flush=True);return host,dict(packaged_tests=18,existing_capture_checks=results,exit_code=p.returncode)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(task,['windows21','windows25','linux21']))
save(O/'COMPLETED.json',dict(status='PASS_18_PACKAGED_TESTS_AND_56_EXISTING_CAPTURES_PER_JVM',jar=pin(jar),prior_producer=pin(oldjar),payloads=payloads,results=results,source_target='Java17 compiled by Windows JDK21',independent_Linux_source_compilation='SKIP3: Linux javac absent',new_catalog_credit=0))
