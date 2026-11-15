#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run one unchanged BIGCAT operation in a fresh selected original R process."""
from pathlib import Path
import argparse,os,shutil,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,load,save,sources,inventory,profile,check,environment,check_output,overlap,control

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    for name in ['runtime','request','assets','out']:parser.add_argument('--'+name,required=True,type=Path)
    parser.add_argument('--workspace',type=Path,help='Optional separate NEW mutable workspace; default OUT/workspace')
    parser.add_argument('--wall-seconds',type=int,default=3600)
    args=parser.parse_args();out=args.out.absolute();owned=False;child=None;launched=False;status=None;workspace=None
    def stop_child():
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
    def warnings(label,cause=None):
        text='# '+label+'\n\n'+('Cause: '+cause+'\n\n' if cause else '')
        if status is not None:
            text+='Original R handler counts: '+str(status['R_handler_counts'])+'. Raw/native totals are unknown.\n\n'
            if status.get('original_return_is_null'):text+='**Original function returned NULL; no clustering result is claimed.**\n\n'
        if (out/'original/condition-summary.json').is_file():
            for entry in load(out/'original/condition-summary.json')['groups']:
                if entry['kind'] in ['warning','error','recording_error']:
                    text+='- '+entry['kind']+' ('+str(entry['count'])+'): '+entry['message']+'\n'
        if ready is not None:
            text+='\n## Selected source profile limits\n\n'
            text+='\n'.join('- '+item for item in profile(ready['profile'])['known_limits'])+'\n'
        text+='\n'+ACTION+'\n'
        (out/'WARNINGS.md').write_text(text,encoding='utf-8',newline='\n')
    try:
        runtime=args.runtime.resolve();request=args.request.resolve();assets=args.assets.resolve()
        roots=[HERE,runtime,request,assets]
        try:ready=load(runtime/'READY.json')
        except FileNotFoundError:ready=None
        if ready is not None:roots += [ready[k] for k in ['workspace','r_prefix','library']]+list(ready['source_paths'].values())
        out=check_output(out,roots)
        workspace=check_output(args.workspace or out/'workspace',roots)
        if out.is_relative_to(workspace) or (workspace.is_relative_to(out) and workspace!=out/'workspace'):
            raise ValueError('Use OUT/workspace or a disjoint NEW workspace')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if sys.platform!='linux':raise Unavailable('Selected original BIGCAT analysis requires permitted Linux/WSL')
        if args.wall_seconds<1:raise ValueError('Positive wall-seconds required')
        if ready is None:raise Unavailable('Runtime READY.json missing; install/adopt a compatible explicit source profile')
        check(ready);selected=profile(ready['profile'])
        for name in ['home','tmp','original']:(out/name).mkdir()
        workspace.mkdir(parents=True,exist_ok=False)
        before_assets=inventory(assets,links=False);before_request=pin(request);before_sources=sources()
        shutil.copyfile(request,out/'request.rds')
        if pin(out/'request.rds')!=before_request:raise ValueError('Request changed during capture')
        save(out/'ASSETS-BEFORE.json',before_assets)
        save(out/'INPUTS.json',dict(request=before_request,assets=str(assets),workspace=str(workspace),
          provider=pin(runtime/'READY.json'),adapter_sources=before_sources,profile=ready['profile']))
        declaration=control(ready,out,output=str(out/'original'),request=str(out/'request.rds'),
          assets=str(assets),workspace=str(workspace))
        save(out/'control.json',declaration)
        command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'Worker.R'),str(out/'control.json')]
        began=time.monotonic()
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=workspace,env=environment(ready,out),stdin=subprocess.DEVNULL,
              stdout=stdout,stderr=stderr,start_new_session=True);launched=True
            save(out/'LIVE.json',dict(pid=child.pid,command=command,started_unix=time.time()))
            while True:
                try:code=child.wait(timeout=min(10,args.wall_seconds));break
                except subprocess.TimeoutExpired:
                    print('SAME_ORIGINAL_BIGCAT_CHILD_RUNNING',child.pid,flush=True)
                    if time.monotonic()-began>args.wall_seconds:raise TimeoutError('Declared wall limit exceeded; retain partials and retry NEW paths')
        save(out/'PROCESS.json',dict(command=command,exit_code=code,elapsed_seconds=time.monotonic()-began))
        status=load(out/'original/R-status.json')
        after_assets=inventory(assets,links=False);save(out/'ASSETS-AFTER.json',after_assets)
        check(ready)
        if after_assets!=before_assets or pin(request)!=before_request or sources()!=before_sources:
            raise ValueError('Protected assets/request/adapter changed during analysis')
        if code!=status['exit_code']:raise ValueError('Original child status disagrees with process exit')
        warnings(status['status'])
        if code:
            save(out/'PARTIAL-ORIGINAL-FILES.json',inventory(out/'original',links=False))
            save(out/'PARTIAL-WORKSPACE-FILES.json',inventory(workspace,links=False))
            save(out/'FAILED.json',dict(status=status,complete_result_admitted=False,required_action=ACTION))
            print(status['status'],ACTION,flush=True);return 3 if code==3 else 2
        if status['phase']!='COMPLETE' or not status['complete_original_RDS_result']:
            raise ValueError('Original process returned without complete evidence')
        original_files=inventory(out/'original',links=False)
        for name in ['complete-result.rds','complete-conditions.rds','complete-rng-before-after.rds','complete-original-asset-links.rds']:
            if name not in original_files:raise ValueError('Complete original record missing: '+name)
        save(out/'ORIGINAL-FILES.json',original_files)
        save(out/'WORKSPACE-FILES.json',inventory(workspace,links=False))
        save(out/'MANIFEST.json',dict(files=inventory(out)))
        completed=dict(status='PASS_ORIGINAL_BIGCAT_COMPLETE_RETURN_AND_ASSETS',profile=ready['profile'],
          original_result=pin(out/'original/complete-result.rds'),original_manifest=pin(out/'ORIGINAL-FILES.json'),
          workspace_manifest=pin(out/'WORKSPACE-FILES.json'),manifest=pin(out/'MANIFEST.json'),
          original_R_status=status,complete_result_admitted=True,live_pointer_persistence_qualified=False,
          new_family_credit=0,whole_goal_complete=False,required_action=ACTION)
        save(out/'COMPLETED.pending.json',completed)
        os.replace(out/'COMPLETED.pending.json',out/'COMPLETED.json')
        print('PASS_ORIGINAL_BIGCAT_COMPLETE_RETURN_AND_ASSETS',ACTION,flush=True)
        return 0
    except BaseException as error:
        stop_child()
        code=3 if not launched and isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_BIGCAT_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_BIGCAT_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/('FINALIZATION_ERROR.json' if launched else 'FAILURE.json'),dict(status=label,
                  cause=repr(error),traceback=traceback.format_exc(),complete_result_admitted=False,required_action=ACTION))
                warnings(label,repr(error))
            except BaseException as recording:code=2;print('FAIL2_BIGCAT_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
