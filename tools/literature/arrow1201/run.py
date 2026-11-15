#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Load caller-declared Parquet chunks or query a Dataset with unchanged R Arrow 12.0.1."""
from pathlib import Path
import argparse, os, shutil, signal, subprocess, sys, time, traceback
sys.dont_write_bytecode=True
from Runtime import HERE, ACTION, Unavailable, pin, load, save, inventory, sources, check, environment

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--runtime',required=True,type=Path)
    parser.add_argument('--request',required=True,type=Path)
    parser.add_argument('--assets',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    parser.add_argument('--wall-seconds',type=int,default=3600)
    args=parser.parse_args();out=args.out.absolute();owned=False;child=None;launched=False
    def stop_child():
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid,signal.SIGKILL);child.wait()
    try:
        runtime,request,assets=args.runtime.resolve(),args.request.resolve(),args.assets.resolve()
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in [HERE,runtime,request,assets]):
            raise ValueError('Use a NEW canonical output separate from protected source/provider/request/assets')
        # Read the declaration before creating anything inside a possibly
        # protected prefix. Missing providers still receive a SKIP3 record below.
        try:declared=load(runtime/'READY.json')
        except FileNotFoundError:declared=None
        if declared is not None:
            for key in ['r_prefix','source','workspace']:
                protected=Path(declared[key]).resolve()
                if out.is_relative_to(protected) or protected.is_relative_to(out):
                    raise ValueError('Output overlaps protected original runtime/source workspace')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if sys.platform!='linux':raise Unavailable('Run this selected original provider in permitted Linux/WSL')
        if args.wall_seconds<1:raise ValueError('Positive wall-seconds required')
        ready=load(runtime/'READY.json');check(ready)
        if any(out.is_relative_to(Path(ready[k])) or Path(ready[k]).is_relative_to(out) for k in ['r_prefix','source','workspace']):
            raise ValueError('Output overlaps protected original runtime/source workspace')
        before_sources=sources();before_assets=inventory(assets,links=False);before_request=pin(request)
        for name in ['home','tmp','original']:(out/name).mkdir()
        shutil.copyfile(request,out/'request.rds')
        if pin(out/'request.rds')!=before_request:raise ValueError('Request changed during capture')
        save(out/'ASSETS-BEFORE.json',before_assets)
        save(out/'INPUTS.json',dict(request=before_request,assets=str(assets),provider=pin(runtime/'READY.json'),adapter_sources=before_sources))
        control=dict(source=ready['source'],audit=str(HERE/'AuditRuntime.R'),request=str(out/'request.rds'),
                     assets=str(assets),output=str(out/'original'))
        save(out/'control.json',control)
        command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'Worker.R'),str(out/'control.json')]
        began=time.monotonic()
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,
                                   stdout=stdout,stderr=stderr,start_new_session=True)
            launched=True;save(out/'LIVE.json',dict(pid=child.pid,command=command))
            while True:
                try:code=child.wait(timeout=min(10,args.wall_seconds));break
                except subprocess.TimeoutExpired:
                    print('ORIGINAL_ARROW1201_CHILD_RUNNING',child.pid,flush=True)
                    if time.monotonic()-began>args.wall_seconds:raise TimeoutError('Declared wall limit exceeded; partials retained')
        save(out/'PROCESS.json',dict(command=command,exit_code=code,elapsed_seconds=time.monotonic()-began))
        status=load(out/'original/R-status.json')
        after_assets=inventory(assets,links=False);save(out/'ASSETS-AFTER.json',after_assets)
        check(ready)
        if after_assets!=before_assets or pin(request)!=before_request or sources()!=before_sources:
            raise ValueError('Protected assets/request/adapter changed during original analysis')
        if status['exit_code']!=code:raise ValueError('Original child status disagrees with process exit')
        (out/'WARNINGS.md').write_text('# Review every original warning and error\n\n'+status['status']+'\n\n'+ACTION+'\n',encoding='utf-8')
        if code:
            save(out/'FAILED.json',dict(status=status,complete_result_admitted=False,required_action=ACTION))
            print(status['status'],ACTION,flush=True)
            return 3 if code==3 else 2
        if status['phase']!='COMPLETE' or not status['complete_original_RDS_result']:
            raise ValueError('Original process returned without complete evidence')
        manifest=inventory(out/'original',links=False)
        if 'complete-result.rds' not in manifest or 'complete-conditions.rds' not in manifest:
            raise ValueError('Complete original result/conditions missing')
        save(out/'ORIGINAL-FILES.json',manifest)
        save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_ARROW1201_COMPLETE_COLLECTED_R_OBJECT',
            result=pin(out/'original/complete-result.rds'),original_manifest=pin(out/'ORIGINAL-FILES.json'),
            original_R_status=status,complete_collected_RDS=True,live_pointer_identity_claimed=False,
            new_family_credit=0,whole_goal_complete=False,required_action=ACTION))
        print('PASS_ORIGINAL_ARROW1201_COMPLETE_COLLECTED_R_OBJECT',flush=True)
        return 0
    except BaseException as error:
        stop_child()
        code=3 if not launched and isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_ARROW1201_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ARROW1201_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FINALIZATION_ERROR.json' if launched else out/'FAILURE.json',
                     dict(status=status,cause=repr(error),traceback=traceback.format_exc(),complete_result_admitted=False,required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:
                code=2;print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':
    raise SystemExit(main())
