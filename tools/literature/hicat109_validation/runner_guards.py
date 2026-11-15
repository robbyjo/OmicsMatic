#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual public HICAT commands and one retained post-return recording fault."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,json,os,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
MODULE=ROOT/'tools/literature/scrattchhicat109'
sys.path.insert(0,str(MODULE))
from Runtime import ACTION,check,check_output,environment,inventory,load,pin,save,sources

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--old-runtime',required=True,type=Path)
    parser.add_argument('--controls',required=True,type=Path);parser.add_argument('--manager',required=True,type=Path)
    parser.add_argument('--source-cache',required=True,type=Path);parser.add_argument('--cache-dir',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args();ready=load(args.runtime/'READY.json');out=args.out.absolute();owned=False
    try:
        out=check_output(out,[MODULE,args.runtime,args.old_runtime,args.controls,ready['workspace']])
        out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nThe recording-fault guard pauses only its owned Python runner, retains the original R status under a new name, then resumes the same process. No source, original operation or runtime is patched.\n')
        before=sources();check(ready);save(out/'SOURCES.json',before)
        request=args.controls/'inputs/dense-default.rds';calls=[]
        def invoke(name,command,expected):
            with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
                child=subprocess.Popen(list(map(str,command)),cwd=out,stdout=so,stderr=se,stdin=subprocess.DEVNULL)
                save(out/(name+'-LIVE.json'),dict(pid=child.pid,command=list(map(str,command))))
                try:code=child.wait(timeout=1800)
                except subprocess.TimeoutExpired:
                    child.terminate();child.wait(timeout=30);raise
            save(out/(name+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=code,expected=expected))
            if code!=expected:raise ValueError('Unexpected public exit: '+name+' '+str(code))
            calls.append(dict(name=name,exit_code=code));print('PUBLIC_GUARD',name,'exit',code,flush=True)
            return out/name
        def command(name,provider=None,input_path=None):
            return [sys.executable,'-B',MODULE/'run.py','--runtime',provider or args.runtime,
              '--request',input_path or request,'--out',out/name]
        def positive(name):
            path=invoke(name,command(name,input_path=args.controls/'inputs'/(name+'.rds')),0)
            assert load(path/'COMPLETED.json')['status']=='PASS_PRIVATE_ORIGINAL_HICAT109_COMPLETE_RETURN_AND_RECORDS'
            return path
        positives=['dense-default','sparse-dgC-default','zero-count-column-sparse','large-10001-real']
        with ThreadPoolExecutor(max_workers=3) as executor:list(executor.map(positive,positives))
        for name in ['missing-gene','plot-with-ggplot2']:
            path=invoke(name,command(name,input_path=args.controls/'inputs'/(name+'.rds')),2)
            assert not (path/'COMPLETED.json').exists()
            assert load(path/'original/R-status.json')['analysis_started'] is True
            assert (path/'original/failed-original-rng.rds').is_file()
        path=out/'dense-default';original=inventory(path)
        invoke('existing-output',command('dense-default'),2)
        assert inventory(path)==original,'Existing analysis was overwritten'
        invoke('old-provider-code',command('old-provider-code',provider=args.old_runtime),2)
        invoke('missing-provider',command('missing-provider',provider=out/'absent-provider'),3)
        assert not (out/'missing-provider').exists(),'Unverified runtime output was created'
        invoke('invalid-wall',command('invalid-wall')+['--wall-seconds','0'],2)
        protected=Path(ready['library'])/'forbidden-analysis-output'
        protected_command=command('protected-output');protected_command[-1]=protected
        invoke('protected-output',protected_command,2);assert not protected.exists()
        install=[sys.executable,'-B',MODULE/'install_runtime.py','--manager',args.manager]
        invoke('install-forbidden',install+['--out',out/'install-forbidden'],3)
        invoke('offline-cache-missing',install+['--allow-install','--out',out/'offline-cache-missing'],3)
        missing_manager=[sys.executable,'-B',MODULE/'install_runtime.py','--manager',out/'no-pixi','--allow-install',
          '--source-cache',args.source_cache,'--cache-dir',args.cache_dir,'--out',out/'missing-manager']
        invoke('missing-manager',missing_manager,3)
        # Reproduce a recording failure after a real unchanged original return.
        name='post-return-recording-fault';folder=out/name;stopped=False
        with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
            child=subprocess.Popen(list(map(str,command(name))),cwd=out,stdout=so,stderr=se,stdin=subprocess.DEVNULL)
            save(out/(name+'-LIVE.json'),dict(pid=child.pid,command=list(map(str,command(name)))))
            deadline=time.monotonic()+900
            try:
                while not (folder/'LIVE.json').exists():
                    if child.poll() is not None or time.monotonic()>deadline:raise ValueError('Runner did not reach its live original child')
                    time.sleep(.02)
                os.kill(child.pid,signal.SIGSTOP);stopped=True
                status=folder/'original/R-status.json'
                while True:
                    if time.monotonic()>deadline:raise ValueError('Original R child did not record its complete status')
                    try:
                        original_status=load(status);break
                    except (FileNotFoundError,json.JSONDecodeError):time.sleep(.02)
                assert original_status['exit_code']==0 and original_status['complete_original_return_recorded']
                retained=status.with_name('R-status.retained-original-recording-fault.json')
                status.rename(retained)
                save(out/'RECORDING-FAULT.json',dict(owned_runner_pid=child.pid,original_status=pin(retained),
                  original_status_retained_at=str(retained),action='Retain original status by same-directory rename while only the owned runner is stopped; resume the same runner.',
                  original_algorithm_or_runtime_modified=False,original_complete_return=pin(folder/'original/complete-original-return.rds')))
            finally:
                if stopped and child.poll() is None:os.kill(child.pid,signal.SIGCONT)
            code=child.wait(timeout=300)
        save(out/(name+'-PROCESS.json'),dict(exit_code=code,expected=2))
        assert code==2 and not (folder/'COMPLETED.json').exists()
        failure=load(folder/'FAILURE.json')
        assert failure['status']=='FAIL2_HICAT109_RESULT_UNADMITTED' and 'FileNotFoundError' in failure['cause']
        assert (folder/'original/complete-original-return.rds').is_file()
        calls.append(dict(name=name,exit_code=code))
        # Compare complete admitted public-runner values, source conditions and RNG.
        rscript=Path(ready['r_prefix'])/'bin/Rscript'
        for name in ['home','tmp']:(out/name).mkdir()
        with (out/'complete-public-comparison.stdout').open('xb') as so,(out/'complete-public-comparison.stderr').open('xb') as se:
            result=subprocess.run([str(rscript),'--vanilla',str(Path(__file__).with_name('CompareRunner.R')),
              ready['library'],str(args.controls/'cases'),str(out)],cwd=out,env=environment(ready,out),stdout=so,stderr=se,timeout=300)
        if result.returncode:raise ValueError('Public complete-object comparisons differ')
        check(ready)
        if sources()!=before:raise ValueError('Public adapter changed during guard validation')
        save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_PUBLIC_HICAT109_RUNNER_POLICY_FAILURE_AND_POSTRETURN_RECORDING_GUARDS',
          calls=sorted(calls,key=lambda x:x['name']),calls_exercised=len(calls),complete_original_records_retained=True,
          recording_failure_original_return_retained_but_unadmitted=True,source_or_runtime_redistributed=False,
          whole_tool_qualified=False,new_family_credit=0,whole_goal_complete=False))
        print('PASS_ACTUAL_PUBLIC_HICAT109_RUNNER_GUARDS',len(calls),flush=True);return 0
    except BaseException as error:
        print('FAIL2_PUBLIC_HICAT109_GUARD_VALIDATION',repr(error),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        return 2

if __name__=='__main__':raise SystemExit(main())
