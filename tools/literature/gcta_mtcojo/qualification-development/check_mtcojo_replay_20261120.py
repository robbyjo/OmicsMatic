from pathlib import Path
import concurrent.futures,hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-6';B=T/'build/mtcojo-qualification-1';A=B/'execution-paths-1';O=B/'replay-controls-1';O.mkdir();JAR=R/'build/gc-5/build/gcta-mtcojo-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def snapshot(p):return {v.relative_to(p).as_posix():pin(v) for v in p.rglob('*') if v.is_file()}
def prefix(host):return ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/java','-cp',linux(JAR),'org.jlinalg.cli.GctaWorkflowCli'] if host=='linux21' else ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-cp',str(JAR),'org.jlinalg.cli.GctaWorkflowCli']
parents={str(A/host/(name+'-bundle')):snapshot(A/host/(name+'-bundle')) for host in ['windows21','linux21'] for name in ['single-GSMR-gzip','multi-supplied-gzip']}
assert all(len(v)>70 for v in parents.values());save(O/'PARENTS-BEFORE.json',parents)
def task(host):
    folder=O/host;folder.mkdir();get=lambda p:linux(p) if host=='linux21' else str(p);sourcehost='windows21' if host=='linux21' else 'linux21';runtime=UP/('linux/download' if host=='linux21' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe');rows=[]
    def execute(name,r,expected,needle=None):
        path=folder/(name+'.request.json');save(path,r);command=prefix(host)+['--request',get(path)]
        with (folder/(name+'.stdout')).open('xb') as stdout,(folder/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        error=(folder/(name+'.stderr')).read_text(errors='replace');row=dict(name=name,command=command,exit_code=p.returncode,expected_exit=expected,cause_seen=needle is None or needle in error,expected_cause=needle);rows.append(row);(folder/'PROGRESS.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8');assert p.returncode==expected and row['cause_seen'],(host,name,p.returncode,error[-2000:]);return row
    for name in ['single-GSMR-gzip','multi-supplied-gzip']:
        parent=A/sourcehost/(name+'-bundle');out=folder/(name+'-replayed');r=dict(schema_version=1,mode='REPLAY',analysis=get(parent),out=get(out),runtime=get(runtime),allow_runtime_flavor_change=True);row=execute(name,r,0)
        command=prefix(host)+['--verify',get(out)]
        with (folder/(name+'.verify.stdout')).open('xb') as stdout,(folder/(name+'.verify.stderr')).open('xb') as stderr:v=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        assert v.returncode==0;row['verify_exit']=v.returncode
        target=A/host/(name+'-bundle');row['matches_target_host_original_tokens']=[line.split() for line in (target/'worker/result.mtcojo.cma').read_text().splitlines()]==[line.split() for line in (out/'worker/result.mtcojo.cma').read_text().splitlines()];assert row['matches_target_host_original_tokens']
        source_inputs={p.relative_to(parent/'inputs').as_posix():pin(p) for p in (parent/'inputs').rglob('*') if p.is_file()};target_inputs={p.relative_to(out/'inputs').as_posix():pin(p) for p in (out/'inputs').rglob('*') if p.is_file()};assert source_inputs==target_inputs;row['all_captured_input_bytes_preserved']=True
    parent=A/sourcehost/'multi-supplied-gzip-bundle';base=dict(schema_version=1,mode='REPLAY',analysis=get(parent),runtime=get(runtime));r=dict(base,out=get(folder/'without-flavor-consent'));execute('without-flavor-consent',r,2,'runtime flavor differs');assert not (folder/'without-flavor-consent').exists()
    for name,out in [('same-parent',parent),('nested-parent',parent/'should-not-exist'),('dotdot-parent',parent/'inputs/../should-not-exist')]:
        execute(name,dict(base,out=get(out),allow_runtime_flavor_change=True),2,'outside');assert not (parent/'should-not-exist').exists()
    alias=folder/'parent-alias'
    if host=='linux21':command=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/ln','-s',linux(parent),linux(alias)]
    else:command=['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-NonInteractive','-Command',"New-Item -ItemType Junction -Path '"+str(alias)+"' -Target '"+str(parent)+"' | Out-Null"]
    p=subprocess.run(command,capture_output=True,timeout=30);save(folder/'ALIAS-CREATE.json',dict(command=command,exit_code=p.returncode));assert p.returncode==0,p.stderr
    execute('alias-nested-parent',dict(base,out=get(alias/'should-not-exist'),allow_runtime_flavor_change=True),2,'outside');assert not (parent/'should-not-exist').exists()
    r=dict(base,out=get(folder/'missing-runtime'),runtime=get(folder/'absent-runtime'),allow_runtime_flavor_change=True);execute('missing-runtime',r,3,'runtime');state=json.loads((folder/'missing-runtime/analysis-status.json').read_bytes());assert state['results_admitted'] is False and state['status']=='SKIP3_DEPENDENT_ANALYSIS_SKIPPED'
    # RUN must also reject real-path aliases into a retained capture before writes.
    r=json.loads((A/host/'multi-supplied-gzip.request.json').read_bytes());r['out']=get(alias/'run-should-not-exist');execute('run-alias-nested-parent',r,2,'nested in a retained capture');assert not (parent/'run-should-not-exist').exists()
    save(folder/'RESULTS.json',rows);print('PASS mtCOJO cross-host replay and alias controls',host,len(rows),flush=True);return host,rows
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(task,['windows21','linux21']))
for p,w in parents.items():assert snapshot(Path(p))==w,p
save(O/'COMPLETED.json',dict(status='PASS_MTCOJO_ACTUAL_CROSS_HOST_REPLAY_AND_ALIAS_CONTROLS',results=results,producer=pin(JAR),parent_captures_unchanged=4,all_parent_file_hashes_unchanged=True,new_catalog_credit=0))
