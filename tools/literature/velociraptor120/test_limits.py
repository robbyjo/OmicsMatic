#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual R wall-time interruption and complete-value capacity refusals."""
from pathlib import Path
import argparse,copy,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,load,pin,provider,save

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--base-request',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
 out=args.out.absolute();owned=False;cases=[];calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW capacity-test output required')
  out.mkdir(exist_ok=False);owned=True;base=load(args.base_request)
  source_names=['run.py','Runtime.py','OriginalWorker.R','AuditRuntime.R','RValues.R','RValues.py','source-profile.json','test_limits.py']
  sources={name:pin(HERE/name) for name in source_names};save(out/'SOURCE-PINS.json',sources)
  inputs={name:pin(Path(name)) for name in base['inputs'].values()}
  scripts={
   'wall-time':'''available_input <- readRDS(file.path(input_directory,"inputs.rds"))
writeLines("Original caller reached analysis before wall-time interruption",file.path(result_directory,"before-stop.txt"))
Sys.sleep(90)
analysis_result <- available_input
analysis_complete <- "CAPACITY_COMPLETE_ORIGINAL_RETURN"
''',
   'complete-R-value-bytes':'''analysis_result <- readRDS(file.path(input_directory,"inputs.rds"))
analysis_complete <- "CAPACITY_COMPLETE_ORIGINAL_RETURN"
'''}
  for name,script in scripts.items():
   document=copy.deepcopy(base);script_path=out/(name+'.R');script_path.write_text(script,encoding='utf-8',newline='\n');result=out/(name+'-result')
   document.update(script=str(script_path),out=str(result),expected_outputs={'before-stop.txt':1} if name=='wall-time' else {},required_exports=['gridVectors'],expected_return='CAPACITY_COMPLETE_ORIGINAL_RETURN')
   if name=='wall-time':document['limits']['timeout_seconds']=30
   else:document['limits']['maximum_R_value_bytes']=64
   request=out/(name+'-request.json');save(request,document);command=[sys.executable,'-B',str(HERE/'run.py'),'--request',str(request)];start=time.monotonic()
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(case=name,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_CAPACITY_CONTROL_CHILD_RUNNING',name,child.pid,round(time.monotonic()-start),flush=True)
   calls.append(dict(case=name,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start));save(out/'PROCESSES.json',calls)
   status=load(result/'analysis-status.json')
   if code!=2 or status['exit_code']!=2 or status['result_admitted'] or not status['warning_review_required'] or not status['complete_manifest']:raise AssertionError('Capacity failure admitted an incomplete result or lost prominent diagnostics')
   if not (result/'diagnostics/analysis-started.json').is_file():raise AssertionError('Original caller did not actually enter analysis before capacity control')
   if not (result/'WARNINGS.md').is_file() or not (result/'native-temporary-files.json').is_file():raise AssertionError('Warnings or complete available native temporary capture missing')
   if name=='wall-time':
    if not (result/'results/before-stop.txt').is_file() or 'wall-time' not in status['status']:raise AssertionError('Actual wall-time interruption was not observed')
    if status['R_handler_condition_counts_complete'] or any(status[key] is not None for key in ['R_handler_warnings','R_handler_messages','R_handler_errors']):raise AssertionError('Missing worker diagnostics were represented as complete/zero')
    if not load(result/'process-exit.json')['owned_process_group_terminated']:raise AssertionError('Owned worker termination not recorded')
   else:
    worker=load(result/'diagnostics/worker-status.json');capture=result/'results/result-value-tree'
    if worker['phase']!='COMPLETE_VALUE_CAPTURE' or worker['complete_value_capture'] or not (capture/'original.rds').is_file() or not (capture/'CAPTURE-FAILURE.rds').is_file() or (capture/'INDEX.json').exists():raise AssertionError('Complete-value capacity refusal lost the original/partial capture or admitted a truncated index')
    if (capture/'original.rds').stat().st_size<=64:raise AssertionError('Original complete RDS did not actually exceed the declared capacity')
    if not (result/'diagnostics/complete-available-caller-scope.rds').is_file():raise AssertionError('Available original caller state was not retained')
   cases.append(dict(case=name,observed_exit=code,status=status));save(out/'CASES.json',cases)
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise AssertionError('Executed source changed: '+name)
  for name,wanted in inputs.items():
   if pin(Path(name))!=wanted:raise AssertionError('Original input changed: '+name)
  print('VERIFYING_COMPLETE_RUNTIME_AFTER_CAPACITY_CONTROLS',flush=True);provider(Path(base['provider_directory']))
  result=dict(status='PASS_TWO_ACTUAL_ORIGINAL_R_CAPACITY_REFUSALS',cases=cases,calls=calls,source_pins=sources,original_inputs_and_runtime_unchanged=True,terminated_process_memory_or_live_IPC_state_recovered=False,whole_R_wrapper_qualified=False,new_family_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_CAPACITY_CONTROLS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=cases,calls=calls,required_action=ACTION))
  print('FAIL2_CAPACITY_CONTROLS_UNADMITTED',repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
