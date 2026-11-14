#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify remaining entry, per-file, aggregate-byte and R-node capacities with actual R writes."""
from pathlib import Path
import argparse,copy,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,load,pin,save


def main():
 parser=argparse.ArgumentParser(description=__doc__)
 for name in ['base-request','out']:parser.add_argument('--'+name,type=Path,required=True)
 args=parser.parse_args();out=args.out.absolute();owned=False;rows=[]
 try:
  if sys.platform!='linux' or out!=out.resolve():raise ValueError('Linux/WSL and canonical NEW output required')
  if out.is_relative_to(HERE) or HERE.is_relative_to(out):raise ValueError('Output overlaps source')
  out.mkdir(exist_ok=False);owned=True;base=load(args.base_request);sources={p.name:pin(p) for p in HERE.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',sources)
  originals={name:pin(Path(name)) for name in base['inputs'].values()}
  controls=[('entries',dict(maximum_files=256),'for (i in seq_len(500L)) writeLines("retained",file.path(result_directory,paste0("entry-",i,".txt")))','entry capacity'),
   ('per-file',dict(maximum_file_bytes=16777216),'writeBin(raw(20971520L),file.path(result_directory,"large.bin"))','byte capacity'),
   ('aggregate-bytes',dict(maximum_workspace_bytes=33554432,maximum_file_bytes=67108864),'writeBin(raw(41943040L),file.path(result_directory,"large.bin"))','byte capacity'),
   ('R-nodes',dict(maximum_R_nodes=3),'','COMPLETE_VALUE_CAPTURE')]
  for label,limits,body,expected in controls:
   case=out/label;case.mkdir();target=case/'analysis';doc=copy.deepcopy(base)
   script=case/'Analysis.R';script.write_text('available_input <- readRDS(file.path(input_directory,"inputs.rds"))\nwriteLines("Actual original caller entered analysis",file.path(result_directory,"before-capacity.txt"))\n'+body+'\n'+('Sys.sleep(15)\n' if body else '')+'analysis_result <- available_input\nanalysis_complete <- "COMPLETE_WORKSPACE_CAPACITY_CONTROL"\n',encoding='utf-8',newline='\n')
   doc.update(script=str(script),out=str(target),expected_outputs={},required_exports=['gridVectors'],required_packages={},expected_return='COMPLETE_WORKSPACE_CAPACITY_CONTROL');doc['limits'].update(limits);request=case/'request.json';save(request,doc)
   command=[sys.executable,'-B',str(HERE/'run.py'),'--request',str(request)];started=time.monotonic()
   with (case/'controller.stdout').open('xb') as so,(case/'controller.stderr').open('xb') as se:
    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(case=label,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_WORKSPACE_CONTROL_CHILD_RUNNING',label,child.pid,round(time.monotonic()-started),flush=True)
   status=load(target/'analysis-status.json')
   if code!=2 or status['exit_code']!=2 or status['result_admitted'] or not status['complete_manifest']:raise AssertionError('Capacity failure lost complete available files or admitted a result')
   if not (target/'diagnostics/analysis-started.json').is_file() or not (target/'results/before-capacity.txt').is_file():raise AssertionError('Capacity refused setup before actual caller ran')
   if label=='R-nodes':
    worker=load(target/'diagnostics/worker-status.json');capture=target/'results/result-value-tree'
    if worker['phase']!=expected or worker['complete_value_capture'] or not (capture/'original.rds').is_file() or not (capture/'CAPTURE-FAILURE.rds').is_file() or (capture/'INDEX.json').exists():raise AssertionError('Incomplete R-node capture was admitted or original RDS lost')
   else:
    if expected not in status['status'] or not load(target/'process-exit.json')['owned_process_group_terminated']:raise AssertionError('Actual owned capacity interruption missing')
    if status['R_handler_condition_counts_complete'] or any(status[key] is not None for key in ['R_handler_warnings','R_handler_messages','R_handler_errors']):raise AssertionError('Interrupted diagnostics were falsely complete/zero')
   if not (target/'WARNINGS.md').read_text().startswith('# FAIL2'):raise AssertionError('Prominent failure warning missing')
   row=dict(case=label,exit_code=code,limits=limits,status=status);rows.append(row);save(out/'CASES.json',rows);print('PASS_ACTUAL_WORKSPACE_REFUSAL',label,flush=True)
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise AssertionError('Executed source changed: '+name)
  for name,wanted in originals.items():
   if pin(Path(name))!=wanted:raise AssertionError('Original input changed: '+name)
  save(out/'COMPLETED.json',dict(status='PASS_FOUR_ACTUAL_ORIGINAL_R_WORKSPACE_AND_NODE_CAPACITIES',cases=rows,source_pins=sources,original_inputs_unchanged=True,new_family_credit=0,whole_goal_complete=False));return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_WORKSPACE_CONTROLS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=rows,required_action=ACTION))
  print('FAIL2_WORKSPACE_CONTROLS_UNADMITTED',repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
