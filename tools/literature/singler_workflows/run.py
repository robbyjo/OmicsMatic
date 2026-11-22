#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged original SingleR annotation with explicit release and references."""
from pathlib import Path
import argparse,os,shutil,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,load,save,profile,sources,inventory,environment,check_output,check

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--edition',required=True);p.add_argument('--runtime',required=True,type=Path)
    p.add_argument('--request',required=True,type=Path);p.add_argument('--out',required=True,type=Path)
    p.add_argument('--wall-seconds',type=int,default=3600)
    a=p.parse_args();out=a.out.absolute();owned=False;child=None;launched=False;process_exit=None
    try:
        provider=a.runtime.resolve();request=a.request.resolve()
        out=check_output(out,[HERE,provider,request]);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf-8')
        selected=profile(a.edition)
        if sys.platform!='linux':raise Unavailable('Original source-built SingleR execution requires permitted Linux/WSL')
        if a.wall_seconds<1:raise ValueError('Positive execution wall limit required')
        ready=load(provider/'READY.json')
        check_output(out,[ready['workspace'],ready['r_prefix'],ready['library'],*ready['source_paths'].values()])
        check(ready,a.edition);before=sources(a.edition)
        for name in ['inputs','original','home','tmp']:(out/name).mkdir()
        original=pin(request);shutil.copyfile(request,out/'inputs/request.rds')
        if pin(out/'inputs/request.rds')!=original or pin(request)!=original:raise ValueError('Input changed during exact snapshot capture')
        save(out/'INPUTS.json',dict(request=dict(path=str(request),**original),provider=pin(provider/'READY.json'),
          adapter_sources=before,edition=a.edition,reference_data_downloaded_or_substituted=False))
        control=dict(edition=a.edition,r_version=selected['r_version'],library=ready['library'],
          base_library=str(Path(ready['r_prefix'])/'lib/R/library'),source_paths=ready['source_paths'],
          package_versions={n:v['version'] for n,v in selected['packages'].items()},
          audit=str(HERE/'AuditNamespace.R'),request=str(out/'inputs/request.rds'),output=str(out/'original'))
        save(out/'control.json',control)
        command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'Worker.R'),str(out/'control.json')]
        started=time.monotonic()
        with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
            child=subprocess.Popen(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
            launched=True;save(out/'LIVE.json',dict(pid=child.pid,command=command))
            while True:
                try:code=child.wait(timeout=10);break
                except subprocess.TimeoutExpired:
                    print('SAME_ORIGINAL_SINGLER_CHILD_RUNNING',child.pid,flush=True)
                    if time.monotonic()-started>a.wall_seconds:
                        os.killpg(child.pid,signal.SIGTERM)
                        try:child.wait(timeout=5)
                        except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
                        raise Unavailable('Original execution reached declared wall limit; dependent analysis skipped; partial files retained')
        process_exit=code;save(out/'PROCESS.json',dict(command=command,exit_code=code))
        status_path=out/'original/R-status.json'
        if not status_path.is_file():
            raise ValueError('Original R process ended without a completion status (exit '+str(code)+'). '
              'A native crash or early termination may prevent condition/RNG-after recording. '
              'No result is admitted. Read PROCESS.json and complete original.stderr; resolve the named exact runtime/input failure and retry NEW paths.')
        status=load(status_path)
        if code==3:raise Unavailable('Original prerequisite unavailable; inspect complete original records')
        if code or status['exit_code'] or not status['complete_original_return_recorded']:
            raise ValueError('Original SingleR call failed; no result admitted; read R-status.json error.message and complete original conditions/consoles')
        for name in ['complete-original-return.rds','complete-original-rng.rds','complete-original-conditions.rds',
          'before-original-source-audit.rds','after-original-source-audit.rds','complete-original-caller-context.rds',
          'complete-original-rng-before.rds']:
            if not (out/'original'/name).is_file():raise ValueError('Incomplete original record population: '+name)
        check(ready,a.edition)
        if sources(a.edition)!=before or pin(out/'inputs/request.rds')!=original or pin(request)!=original:
            raise ValueError('Captured code/input changed during original analysis')
        save(out/'MANIFEST.json',dict(original=inventory(out/'original'),inputs=inventory(out/'inputs'),
          raw_consoles={n:pin(out/n) for n in ['original.stdout','original.stderr']},
          complete_original_objects_retained=True,raw_native_diagnostic_totals_available=False))
        result=dict(status='PASS_ORIGINAL_SINGLER_COMPLETE_RETURN_AND_RECORDS',edition=a.edition,original_status=status,
          complete_original_conditions_retained=True,original_values_or_IDs_repaired=False,
          reference_data_downloaded_or_substituted=False,serialized_training_external_pointers_are_reusable=False,
          whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.pending.json',result);os.replace(out/'COMPLETED.pending.json',out/'COMPLETED.json')
        print(result['status'],a.edition,flush=True);return 0
    except BaseException as error:
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
        if child is not None and child.poll() is not None:process_exit=child.returncode
        code=3 if isinstance(error,Unavailable) or (not launched and isinstance(error,OSError) and not isinstance(error,FileExistsError)) else 2
        label='SKIP3_SINGLER_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_SINGLER_RESULT_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                if (out/'COMPLETED.json').exists():(out/'COMPLETED.json').rename(out/'COMPLETED-UNADMITTED.json')
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),
                  complete_result_admitted=False,actual_original_process_exit_code=process_exit,
                  original_R_status_available=(out/'original/R-status.json').is_file(),
                  original_rng_after_record_available=any((out/'original'/n).is_file() for n in ['complete-original-rng.rds','failed-original-rng.rds']),
                  required_action=ACTION))
                (out/'WARNINGS.md').write_text(label+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:print('FAIL2_RESULT_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
