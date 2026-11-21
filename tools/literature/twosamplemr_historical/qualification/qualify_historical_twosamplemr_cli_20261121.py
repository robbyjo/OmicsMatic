from pathlib import Path
import copy,ctypes,hashlib,json,os,shutil,subprocess,time
R=Path('D:/projects/JLinAlg');T=R/'build/mr-6';B=T/'build/twosamplemr-historical-qualification-1';P=R/'build/mr-5/build/twosamplemr-historical-1';O=B/'cli-controls-1';O.mkdir();J=P/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar';V=R/'build/twosamplemr-historical-runtime-20261121-1';java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
negative=O/'private-source-guard-control';negative.mkdir();src=negative/'source';upstream=V/'versions/0.6.8/upstream';shutil.copytree(upstream,src);library=negative/'library';library.mkdir();changed=src/'R/mr.R';before=pin(changed)
with changed.open('a',encoding='utf-8',newline='\n') as f:f.write('\n# Deliberate isolated negative integrity control: never qualify this package.\nmr_ivw <- function(...) stop("DELIBERATE_SOURCE_GUARD_CONTROL_MUST_NOT_EXECUTE")\n')
save(negative/'MUTATION.json',dict(purpose='Negative integrity control only; source guard must reject before an MR call',source= str(upstream),modified_path='R/mr.R',before=before,after=pin(changed),other_source_files_unchanged=all(pin(p)==pin(src/p.relative_to(upstream)) for p in upstream.rglob('*') if p.is_file() and p.relative_to(upstream).as_posix()!='R/mr.R')))
command=['C:/Program Files/R/R-4.6.1/bin/R.exe','CMD','INSTALL','--no-multiarch','--library='+str(library),str(src)];env=dict(os.environ,LANG='C',LC_ALL='C',R_LIBS=os.pathsep.join(map(str,[V/'library',R/'build/twosamplemr-runtime-intake-20261121-1/library'])),R_LIBS_USER=str(library),R_PROFILE_USER=str(negative/'no-profile'),R_ENVIRON_USER=str(negative/'no-environ'))
save(negative/'COMMAND.json',dict(command=command,source_not_a_qualified_runtime=True))
with (negative/'stdout').open('xb') as stdout,(negative/'stderr').open('xb') as stderr:
    proc=subprocess.Popen(command,cwd=negative,env=env,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);save(negative/'STARTED.json',dict(pid=proc.pid));code=proc.wait(timeout=180)
save(negative/'TERMINAL.json',dict(exit_code=code));assert code==0
ctypes.windll.kernel32.OpenProcess.restype=ctypes.c_void_p;ctypes.windll.kernel32.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_ulong];ctypes.windll.kernel32.CloseHandle.argtypes=[ctypes.c_void_p]
base=json.loads((P/'java-pilot-2/0.5.7/mr-default.json').read_bytes());cases=[('missing-Rscript',3),('missing-library',3),('missing-input',3),('unqualified-profile',3),('wrong-edition',3),('missing-package',3),('source-mismatch',2),('input-capacity',2),('output-capacity',2),('work-capacity',2),('rows-capacity',2),('old-unsupported-argument',2),('unused-input',2),('kept-nonfinite',2),('empty-source-overlap',2),('owned-timeout',2)];results={}
for name,expected in cases:
    folder=O/name;folder.mkdir();r=copy.deepcopy(base);r['out']=str(folder/'analysis')
    if name=='missing-Rscript':r['rscript']=str(folder/'missing.exe')
    elif name=='missing-library':r['r_libraries']=[str(folder/'missing-library')]
    elif name=='missing-input':r['harmonised']=str(folder/'missing.tsv')
    elif name=='unqualified-profile':r['profile']='TwoSampleMR0.5.7-UNKNOWN'
    elif name=='wrong-edition':r['r_libraries'][0]=str(V/'versions/0.6.8/library')
    elif name=='missing-package':
        empty=folder/'empty-library';empty.mkdir();r['r_libraries']=[str(empty)]
    elif name=='source-mismatch':
        r=json.loads((P/'java-pilot-2/0.6.8/mr-default.json').read_bytes());r.update(out=str(folder/'analysis'));r['r_libraries'][0]=str(library)
    elif name=='input-capacity':r['maximum_input_bytes']=10
    elif name=='output-capacity':r['maximum_output_bytes']=1000
    elif name=='work-capacity':r['maximum_work']=1
    elif name=='rows-capacity':r['maximum_rows']=1
    elif name=='old-unsupported-argument':
        r=json.loads((P/'java-pilot-2/0.4.9/format-exposure.json').read_bytes());r.update(out=str(folder/'analysis'));r['format_options']['log_pval']=False
    elif name=='unused-input':r['outcome']=str(folder/'never-read.tsv')
    elif name=='kept-nonfinite':
        lines=Path(r['harmonised']).read_text(encoding='utf-8').splitlines();header=lines[0].split('\t');rows=[x.split('\t') for x in lines[1:]];row=next(x for x in rows if x[header.index('mr_keep')]=='TRUE');row[header.index('beta.exposure')]='NaN';p=folder/'kept-nonfinite.tsv';p.write_text('\n'.join([lines[0],*['\t'.join(x) for x in rows]])+'\n',encoding='utf-8',newline='\n');r['harmonised']=str(p)
    elif name=='empty-source-overlap':
        r=json.loads((P/'java-pilot-2/0.5.7/harmonise-1.json').read_bytes());r['out']=str(folder/'analysis');lines=Path(r['outcome']).read_text(encoding='utf-8').splitlines();at=lines[0].split('\t').index('SNP');rows=[x.split('\t') for x in lines[1:]]
        for row in rows:row[at]='NO_OVERLAP_'+row[at]
        p=folder/'outcome.tsv';p.write_text('\n'.join([lines[0],*['\t'.join(x) for x in rows]])+'\n',encoding='utf-8',newline='\n');r['outcome']=str(p)
    elif name=='owned-timeout':
        marker=folder/'owned-child-pid.txt';runtime=folder/'lifetime-only-Rscript.cmd';runtime.write_text('@echo off\r\n"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command "[System.IO.File]::WriteAllText(\''+str(marker)+'\', $PID.ToString()); Start-Sleep -Seconds 300"\r\n',encoding='utf-8',newline='');r['rscript']=str(runtime);r['timeout_seconds']=3
    save(folder/'request.json',r);command=[java,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalCli','--request',str(folder/'request.json')];save(folder/'COMMAND.json',dict(command=command,expected_exit=expected,lifetime_harness_only=name=='owned-timeout'))
    with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:
        proc=subprocess.Popen(command,cwd=T,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);save(folder/'STARTED.json',dict(pid=proc.pid));code=proc.wait(timeout=120)
    save(folder/'TERMINAL.json',dict(exit_code=code));capture=folder/'analysis';state=json.loads((capture/'analysis-status.json').read_bytes()) if (capture/'analysis-status.json').exists() else {};manifest=json.loads((capture/'manifest.json').read_bytes()) if (capture/'manifest.json').exists() else {};result=dict(exit_code=code,expected_exit=expected,results_admitted=state.get('results_admitted',False),status=state.get('status'),error=state.get('error'))
    assert code==expected and not result['results_admitted'],(name,result)
    stderr=(folder/'stderr').read_text(encoding='utf-8',errors='replace');assert ('SKIP3' if expected==3 else 'FAIL2') in stderr and ('DEPENDENT ANALYSIS SKIPPED' if expected==3 else 'NO COMPLETE RESULT') in stderr,name
    if name=='source-mismatch':
        assert 'Original installed body/formals differ from tagged source: mr_ivw' in result['error'];report=json.loads((capture/'worker/report.json').read_bytes());assert not report['calls'];result['no_original_model_invoked']=True
    if name=='empty-source-overlap':
        report=json.loads((capture/'worker/report.json').read_bytes());assert report['calls'][0]['complete'] is True;result['original_empty_return_retained']=True
    if name=='owned-timeout':
        assert marker.exists();pid=int(marker.read_text());handle=ctypes.windll.kernel32.OpenProcess(0x100000,False,pid)
        if handle:
            waited=ctypes.windll.kernel32.WaitForSingleObject(handle,0);ctypes.windll.kernel32.CloseHandle(handle);assert waited==0,'Owned child still live'
        cleanup=manifest['owned_process_cleanup'];assert cleanup and all(not x['alive_after_cleanup'] for x in cleanup);result.update(owned_child_pid=pid,owned_child_terminal=True,owned_cleanup=cleanup)
    results[name]=result;save(folder/'RESULT.json',result);print(name,result,flush=True)
save(O/'RESULT.json',dict(status='PASS_16_MISSING_FAILED_SOURCE_LIFETIME_CONTROLS_NO_ADMISSION',results=results,source_mutation_private=True,new_catalog_credit=0,whole_goal_complete=False))
