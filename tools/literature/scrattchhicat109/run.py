#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run one unchanged private HICAT modified-DoubletFinder call and retain it whole."""
from pathlib import Path
import argparse,os,shutil,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,load,save,profile,sources,inventory,environment,check_output,check

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--runtime',required=True,type=Path);p.add_argument('--request',required=True,type=Path)
    p.add_argument('--out',required=True,type=Path);p.add_argument('--wall-seconds',type=int,default=3600)
    args=p.parse_args();out=args.out.absolute();owned=False;child=None;launched=False
    try:
        provider=args.runtime.resolve();request=args.request.resolve()
        ready=load(provider/'READY.json')
        protected=[HERE,provider,ready['workspace'],ready['r_prefix'],ready['library'],*ready['source_paths'].values(),request]
        out=check_output(out,protected);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text('# Original private HICAT call\n\n'+ACTION+'\n',encoding='utf-8')
        if sys.platform!='linux':raise Unavailable('Original HICAT analysis requires permitted Linux/WSL')
        if args.wall_seconds<1:raise ValueError('Positive wall limit required')
        check(ready);before=sources()
        for name in ['inputs','original','home','tmp']:(out/name).mkdir()
        original=pin(request);shutil.copyfile(request,out/'inputs/request.rds')
        if pin(out/'inputs/request.rds')!=original or pin(request)!=original:raise ValueError('Input changed during exact snapshot capture')
        selected=profile()
        save(out/'INPUTS.json',dict(request=dict(path=str(request),**original),provider=pin(provider/'READY.json'),
          adapter_sources=before,source_profile=selected['source_profile_name'],
          license_discrepancy_preserved=True,source_or_runtime_redistribution_authorized=False))
        control=dict(profile=selected['source_profile_name'],library=ready['library'],
          base_library=str(Path(ready['r_prefix'])/'lib/R/library'),source_paths=ready['source_paths'],
          dependency_versions=selected['dependency_versions'],namespace_functions={'scrattch.hicat':360,'RANN':1},
          audit=str(HERE/'AuditNamespace.R'),request=str(out/'inputs/request.rds'),output=str(out/'original'))
        save(out/'control.json',control)
        command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'Worker.R'),str(out/'control.json')]
        started=time.monotonic()
        with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
            child=subprocess.Popen(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
            launched=True
            save(out/'LIVE.json',dict(pid=child.pid,command=command))
            while True:
                try:code=child.wait(timeout=10);break
                except subprocess.TimeoutExpired:
                    print('SAME_ORIGINAL_HICAT_CHILD_RUNNING',child.pid,flush=True)
                    if time.monotonic()-started>args.wall_seconds:
                        os.killpg(child.pid,signal.SIGTERM)
                        try:child.wait(timeout=5)
                        except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
                        raise Unavailable('Original execution reached declared wall limit; dependent analysis skipped; partials retained')
        save(out/'PROCESS.json',dict(command=command,exit_code=code))
        status=load(out/'original/R-status.json')
        if code==3:raise Unavailable('Original prerequisite unavailable; inspect all original records')
        if code or status['exit_code'] or not status['complete_original_return_recorded']:
            raise ValueError('Original HICAT call failed; no result admitted; inspect complete R conditions and consoles')
        for name in ['complete-original-return.rds','complete-original-rng.rds','complete-original-conditions.rds',
          'before-original-source-audit.rds','after-original-source-audit.rds','complete-original-caller-context.rds']:
            if not (out/'original'/name).is_file():raise ValueError('Incomplete original record population: '+name)
        check(ready)
        if sources()!=before or pin(out/'inputs/request.rds')!=original or pin(request)!=original:
            raise ValueError('Captured code/input changed during analysis')
        save(out/'MANIFEST.json',dict(original=inventory(out/'original'),inputs=inventory(out/'inputs'),
          raw_consoles={n:pin(out/n) for n in ['original.stdout','original.stderr']},
          complete_original_objects_retained=True,raw_native_diagnostic_totals_available=False))
        result=dict(status='PASS_PRIVATE_ORIGINAL_HICAT109_COMPLETE_RETURN_AND_RECORDS',source_profile=selected['source_profile_name'],
          original_status=status,complete_original_conditions_retained=True,original_values_or_IDs_repaired=False,
          source_or_runtime_redistributed=False,whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.pending.json',result);os.replace(out/'COMPLETED.pending.json',out/'COMPLETED.json')
        print(result['status'],flush=True);return 0
    except BaseException as error:
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
        code=3 if isinstance(error,Unavailable) or (not launched and isinstance(error,OSError) and not isinstance(error,FileExistsError)) else 2
        label='SKIP3_HICAT109_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HICAT109_RESULT_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                if (out/'COMPLETED.json').exists():(out/'COMPLETED.json').rename(out/'COMPLETED-UNADMITTED.json')
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),complete_result_admitted=False,required_action=ACTION))
                (out/'WARNINGS.md').write_text(label+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:print('FAIL2_RESULT_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
