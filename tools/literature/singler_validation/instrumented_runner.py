#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Owned failure-injection harness. Original code files/runtime/inputs stay intact."""
from pathlib import Path
import argparse,importlib.util,shutil,sys
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3];HERE=ROOT/'tools/literature/singler_workflows'
sys.path.insert(0,str(HERE))
from Runtime import load,save,pin,sources

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mode',required=True,choices=['recording-fault','early-zero-exit','native-signal','timeout','captured-input-tamper'])
    p.add_argument('--edition',required=True);p.add_argument('--runtime',type=Path,required=True)
    p.add_argument('--request',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();before=sources(a.edition);original=pin(a.request)
    spec=importlib.util.spec_from_file_location('owned_singler_failure_injection',HERE/'run.py')
    runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
    if a.mode=='recording-fault':
        real_save=runner.save
        def faulty_save(path,value):
            if Path(path).name=='MANIFEST.json':raise OSError('Deliberate owned recording fault after complete original return')
            return real_save(path,value)
        runner.save=faulty_save
    if a.mode in ['early-zero-exit','native-signal','timeout']:
        real_popen=runner.subprocess.Popen
        def changed_launch(command,**kwargs):
            # Mutate the owned launch list so LIVE/PROCESS record the actual
            # command. This guard runs no original SingleR analysis.
            expression={'early-zero-exit':'quit(status=0)','native-signal':'tools::pskill(Sys.getpid(),11L)','timeout':'Sys.sleep(30)'}[a.mode]
            command[:]=[command[0],'--vanilla','-e',expression]
            return real_popen(command,**kwargs)
        runner.subprocess.Popen=changed_launch
    if a.mode=='captured-input-tamper':
        real_check=runner.check;calls=0
        def changed_capture(ready,edition):
            nonlocal calls
            calls+=1
            if calls==2:
                captured=a.out/'inputs/request.rds'
                shutil.copyfile(captured,a.out/'request-before-deliberate-owned-tamper.rds')
                with captured.open('ab') as stream:stream.write(b'OWNED_UNADMITTED_TAMPER_FIXTURE')
            return real_check(ready,edition)
        runner.check=changed_capture
    sys.argv=[str(HERE/'run.py'),'--edition',a.edition,'--runtime',str(a.runtime),'--request',str(a.request),'--out',str(a.out)]
    if a.mode=='timeout':sys.argv+=['--wall-seconds','1']
    code=runner.main()
    if sources(a.edition)!=before or pin(a.request)!=original:raise ValueError('Protected source or original input changed during owned guard')
    observation=dict(status='OWNED_INSTRUMENTED_FAILURE_GUARD_NOT_AN_UNMODIFIED_RUNNER_SUCCESS',mode=a.mode,
      exit_code=code,source_files_and_original_request_unchanged=True,protected_runtime_mutated=False,
      original_return_recorded=(a.out/'original/complete-original-return.rds').is_file(),
      final_completion_present=(a.out/'COMPLETED.json').exists(),actual_original_analysis_executed=a.mode in ['recording-fault','captured-input-tamper'])
    save(a.out/'OWNED-GUARD-OBSERVATION.json',observation)
    if observation['final_completion_present']:raise ValueError('Failed injected run incorrectly admitted a complete result')
    return code

if __name__=='__main__':raise SystemExit(main())
