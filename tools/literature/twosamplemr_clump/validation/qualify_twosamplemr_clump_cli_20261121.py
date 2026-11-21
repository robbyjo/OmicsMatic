from pathlib import Path
import copy,ctypes,hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-4';B=T/'build/twosamplemr-clump-adapter-1';O=B/'cli-controls-1';O.mkdir();J=B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'
ctypes.windll.kernel32.OpenProcess.restype=ctypes.c_void_p;ctypes.windll.kernel32.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_ulong];ctypes.windll.kernel32.CloseHandle.argtypes=[ctypes.c_void_p]
base=json.loads((B/'normal-analyses-1/basic.request.json').read_bytes());results={}
cases=[('missing-Rscript',3),('missing-library',3),('missing-PLINK',3),('missing-input',3),('unqualified-profile',3),('unqualified-PLINK',3),('wrong-PLINK-hash',2),('input-capacity',2),('output-capacity',2),('work-capacity',2),('owned-timeout',2)]
for name,expected in cases:
    out=O/name;out.mkdir();r=copy.deepcopy(base);r['out']=str(out/'analysis')
    if name=='missing-Rscript':r['rscript']=str(out/'missing.exe')
    elif name=='missing-library':r['r_libraries']=[str(out/'missing-library')]
    elif name=='missing-PLINK':r['plink_executable']=str(out/'missing.exe')
    elif name=='missing-input':r['instruments']=str(out/'missing.tsv')
    elif name=='unqualified-profile':r['profile']='TwoSampleMR0.5.7'
    elif name=='unqualified-PLINK':
        raw=bytearray(Path(base['plink_executable']).read_bytes());raw[-1]^=1;path=out/'never-executed-unqualified.exe';path.write_bytes(raw);r['plink_executable']=str(path);r['plink_sha256']=hashlib.sha256(raw).hexdigest()
    elif name=='wrong-PLINK-hash':r['plink_sha256']='0'*64
    elif name=='input-capacity':r['maximum_input_bytes']=10
    elif name=='output-capacity':r['maximum_output_bytes']=1000
    elif name=='work-capacity':r['maximum_work']=1
    elif name=='owned-timeout':
        marker=out/'owned-child-pid.txt';runtime=out/'lifetime-only-Rscript.cmd';runtime.write_text('@echo off\r\n"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command "[System.IO.File]::WriteAllText(\''+str(marker)+'\', $PID.ToString()); Start-Sleep -Seconds 300"\r\n',encoding='utf-8',newline='');r['rscript']=str(runtime);r['timeout_seconds']=3
    request=out/'request.json';request.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8',newline='\n');command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrClumpCli','--request',str(request)]
    (out/'COMMAND.json').write_text(json.dumps(dict(command=command,expected_exit=expected,unqualified_binary_never_executed=name=='unqualified-PLINK',timeout_case_is_lifetime_harness_only=name=='owned-timeout'),indent=2)+'\n',encoding='utf-8')
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=90,creationflags=subprocess.CREATE_NO_WINDOW)
    state=json.loads((out/'analysis/analysis-status.json').read_bytes()) if (out/'analysis/analysis-status.json').exists() else {};result=dict(exit_code=p.returncode,expected_exit=expected,results_admitted=state.get('results_admitted',False),status=state.get('status'),error=state.get('error'));results[name]=result
    assert p.returncode==expected and not result['results_admitted'],(name,result)
    if name=='owned-timeout':
        assert marker.exists();pid=int(marker.read_text());handle=ctypes.windll.kernel32.OpenProcess(0x100000,False,pid)
        if handle:
            waited=ctypes.windll.kernel32.WaitForSingleObject(handle,0);ctypes.windll.kernel32.CloseHandle(handle);assert waited==0,'Owned child remains alive'
        cleanup=json.loads((out/'analysis/manifest.json').read_bytes())['owned_process_cleanup'];assert cleanup and all(not row['alive_after_cleanup'] for row in cleanup);result.update(owned_child_pid=pid,owned_child_terminal=True,owned_cleanup=cleanup)
    print(name,result,flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(status='PASS_ELEVEN_MISSING_FAILED_LIFETIME_CONTROLS_NO_ADMISSION',results=results,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
