from pathlib import Path
import concurrent.futures,copy,hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';O=B/'global-workflows-1';O.mkdir();JAR=B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';OLD=R/'build/gc-5/build/gcta-mtcojo-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';A=R/'build/gc-6/build/mtcojo-qualification-1/execution-paths-1';UP=R/'build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1'
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def transport(v):
    if isinstance(v,dict):return {k:transport(x) for k,x in v.items()}
    if isinstance(v,list):return [transport(x) for x in v]
    return linux(str(v).replace('\\','/')) if isinstance(v,str) and (v.startswith('D:/') or v.startswith('D:\\')) else v
def snapshot(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def task(host):
    folder=O/host;folder.mkdir();get=lambda p:linux(p) if host=='linux21' else str(p);java=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/java'] if host=='linux21' else ['C:/Program Files/Java/jdk-25/bin/java.exe' if host=='windows25' else 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'];root=java+['-jar',get(JAR)];legacy=java+['-jar',get(OLD)];results=[]
    def run(name,command,expected=0):
        with (folder/(name+'.stdout')).open('xb') as stdout,(folder/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        row=dict(name=name,command=command,exit_code=p.returncode,expected_exit=expected);results.append(row);(folder/'PROGRESS.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8');assert p.returncode==expected,(host,name,(folder/(name+'.stderr')).read_text(errors='replace')[-2000:]);return row
    run('old-root-help',legacy+['--help']);run('root-help',root+['--help']);lines=(folder/'root-help.stdout').read_bytes().splitlines(keepends=True);added=[line for line in lines if b'gcta-tools --help' in line];assert len(added)==1 and b''.join(line for line in lines if line not in added)==(folder/'old-root-help.stdout').read_bytes()
    for command in ['broad-gsea-tools','edger-tools','susie-tools','scanpy-tools']:
        run('old-'+command,legacy+[command,'--help']);run('current-'+command,root+[command,'--help']);assert (folder/('old-'+command+'.stdout')).read_bytes()==(folder/('current-'+command+'.stdout')).read_bytes();assert (folder/('old-'+command+'.stderr')).read_bytes()==(folder/('current-'+command+'.stderr')).read_bytes()
    run('command-help',root+['gcta-tools','--help']);run('standalone-help',java+['-cp',get(JAR),'org.jlinalg.cli.GctaWorkflowCli','--help']);assert (folder/'command-help.stdout').read_bytes()==(folder/'standalone-help.stdout').read_bytes();run('missing-request',root+['gcta-tools','--request',get(folder/'absent-request.json')],3)
    # Reuse the independently prepared, complete input bytes from qualified captures.
    template=json.loads((R/'build/gc-3/build/gcta-workflows-1/normal-workflow-runs-1/windows21/requests/native-grm.json').read_bytes()) if (R/'build/gc-3/build/gcta-workflows-1/normal-workflow-runs-1/windows21/requests/native-grm.json').is_file() else None
    # The public native fixture builder provides its own complete request; no guessed predecessor label.
    fixture=folder/'native fixture';command=['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(T/'examples/gcta-workflows/make_fixture.py'),'--out',str(fixture)];run('native-fixture',command);template=json.loads((fixture/'request.json').read_bytes());template['out']=str(folder/'native-run');template=transport(template) if host=='linux21' else template
    def request(name,r,expected=0):
        p=folder/(name+'.json');save(p,r);return run(name,root+['gcta-tools','--request',get(p)],expected)
    request('native-run',template);native=folder/'native-run';before=snapshot(native);run('native-verify',root+['gcta-tools','--verify',get(native)]);request('native-replay',dict(schema_version=1,mode='REPLAY',analysis=get(native),out=get(folder/'native-replay')));assert snapshot(native)==before
    comparisons=[]
    for name in ['single-GSMR-gzip','multi-supplied-gzip']:
        r=json.loads((A/'windows21'/(name+'.request.json')).read_bytes());r['out']=str(folder/name);r['runtime']=str(UP/('linux/download' if host=='linux21' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe'));r=transport(r) if host=='linux21' else r;request(name+'-run',r);capture=folder/name;before=snapshot(capture);run(name+'-verify',root+['gcta-tools','--verify',get(capture)]);replay=folder/(name+'-replayed');request(name+'-replay',dict(schema_version=1,mode='REPLAY',analysis=get(capture),out=get(replay)));assert snapshot(capture)==before
        baseline=A/host/(name+'-bundle')/'worker/result.mtcojo.cma'
        for label,target in [('run',capture),('replay',replay)]:
            current=target/'worker/result.mtcojo.cma';same=[s.split() for s in current.read_text().splitlines()]==[s.split() for s in baseline.read_text().splitlines()];assert same;comparisons.append(dict(model=name,mode=label,reference=str(baseline),table=str(current),all_original_table_tokens_equal=same))
    r=json.loads((A/'windows21'/'single-GSMR-low-IV.request.json').read_bytes());r['out']=str(folder/'failed-GSMR');r['runtime']=str(UP/('linux/download' if host=='linux21' else 'windows/files/gcta-1.94.1-win-x86_64/gcta-win-1.94.1.exe'));r=transport(r) if host=='linux21' else r;request('failed-GSMR',r,2);state=json.loads((folder/'failed-GSMR/analysis-status.json').read_bytes());record=json.loads((folder/'failed-GSMR/worker/analysis-command.json').read_bytes());assert state['results_admitted'] is False and record['exit_code']==0 and (folder/'failed-GSMR/worker/result.mtcojo.cma').is_file()
    r=json.loads((A/'windows21'/'multi-supplied-gzip.request.json').read_bytes());r['out']=str(folder/'missing-runtime');r['runtime']=str(folder/'absent-runtime');r=transport(r) if host=='linux21' else r;request('missing-runtime',r,3);state=json.loads((folder/'missing-runtime/analysis-status.json').read_bytes());assert state['results_admitted'] is False
    result=dict(invocations=results,root_help_exact_except_one_new_line=True,legacy_command_help_bytes_unchanged=4,standalone_and_global_help_equal=True,native_RUN_VERIFY_REPLAY=True,original_RUN_VERIFY_REPLAY=True,parent_bytes_preserved=True,table_comparisons=comparisons,zero_exit_NaN_source_refused=True,missing_runtime_SKIP3_without_fallback=True);save(folder/'RESULT.json',result);print('PASS actual GCTA global help/legacy/native/original/refusal workflows',host,len(results),'invocations',flush=True);return host,result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(task,['windows21','windows25','linux21']))
save(O/'COMPLETED.json',dict(status='PASS_ACTUAL_GCTA_GLOBAL_WORKFLOWS_THREE_JVMS',producer=pin(JAR),previous_producer=pin(OLD),results=results,new_catalog_credit=0))
