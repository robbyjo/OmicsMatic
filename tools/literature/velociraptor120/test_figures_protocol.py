#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual original R-only figure refusal and incomplete-worker controls."""
from pathlib import Path
import argparse,copy,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,load,pin,save
from PlotRuntime import PLOT_ACTION,provider

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--base-request',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.absolute();owned=False;cases=[];calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW figure-control output required')
  out.mkdir(exist_ok=False);owned=True;base=load(args.base_request);ready=load(Path(base['provider_directory'])/'READY.json')
  names=['figures.py','FiguresWorker.R','PlotRuntime.py','AuditPlots.R','Runtime.py','run.py','RValues.py','source-profile.json','install_plots.py','test_figures_protocol.py'];sources={name:pin(HERE/name) for name in names};save(out/'SOURCE-PINS.json',sources)
  inputs={name:pin(Path(name)) for name in base['inputs'].values()}
  def execute(label,command):
   command=list(map(str,command));started=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_FIGURE_PROTOCOL_CHILD_RUNNING',label,child.pid,round(time.monotonic()-started),flush=True)
   calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-started));save(out/'PROCESSES.json',calls);return code
  def one(name,alter,expected):
   doc=copy.deepcopy(base);doc['out']=str(out/(name+'-result'));alter(doc);request=out/(name+'-request.json');save(request,doc)
   code=execute(name,[sys.executable,'-B',HERE/'figures.py','--request',request]);result=Path(doc['out']);status=load(result/'analysis-status.json')
   if code!=expected or status['exit_code']!=expected or status['result_admitted'] or not status['warning_review_required'] or not (result/'WARNINGS.md').is_file():raise AssertionError('Figure failure/permission policy admitted an incomplete result or lost warnings: '+name)
   if 'Required action:' not in (out/(name+'.stdout')).read_text(errors='replace'):raise AssertionError('Clear recovery guidance absent: '+name)
   cases.append(dict(case=name,expected_exit=expected,observed_exit=code,status=status));save(out/'CASES.json',cases);return result
  for policy in ['execution_policy','trusted_code_policy']:
   result=one('forbid-'+policy,lambda d,key=policy:d.update({key:'FORBID'}),3)
   if (result/'process-live.json').exists():raise AssertionError('Forbidden figure worker launched')
  result=one('missing-provider',lambda d:d.update(provider_directory=str(out/'missing-provider-directory')),3)
  if (result/'process-live.json').exists():raise AssertionError('Missing figure provider caused worker launch')
  result=one('fitting-export-in-R-only-profile',lambda d:d.update(required_exports=['scvelo']),3)
  if (result/'process-live.json').exists():raise AssertionError('R-only figure profile launched a fitting request')
  target=out/'installation-forbidden';code=execute('installation-forbidden',[sys.executable,'-B',HERE/'install_plots.py','--r-base',Path(ready['r_prefix']).parents[2],'--out',target])
  if code!=3 or not (target/'WARNINGS.md').is_file() or (target/'LIVE.json').exists():raise AssertionError('Forbidden R-only installation did not skip before installer launch')
  cases.append(dict(case='installation-forbidden',expected_exit=3,observed_exit=code));save(out/'CASES.json',cases)
  wrong=out/'wrong-return.R';wrong.write_text('analysis_result <- readRDS(file.path(input_directory,"call.rds"))\nanalysis_complete <- "CONTROL_WRONG_GRAPH_RETURN"\n',encoding='utf-8',newline='\n')
  result=one('non-graph-return',lambda d:d.update(script=str(wrong),expected_return='CONTROL_WRONG_GRAPH_RETURN'),2);worker=load(result/'diagnostics/worker-status.json')
  if worker['complete_original_graph_serialized'] or worker['complete_original_SVG_and_PNG_rendered'] or not (result/'diagnostics/complete-available-caller-scope.rds').is_file():raise AssertionError('Non-graph return lost original caller state or was silently accepted as a figure')
  early=out/'early-exit.R';early.write_text('writeLines("Caller entered before incomplete zero exit",file.path(result_directory,"before-exit.txt"))\nquit(save="no",status=0L,runLast=FALSE)\n',encoding='utf-8',newline='\n')
  result=one('early-zero-exit',lambda d:d.update(script=str(early),expected_outputs={'before-exit.txt':1}),2);status=load(result/'analysis-status.json')
  if not (result/'results/before-exit.txt').is_file() or status['R_handler_condition_counts_complete'] or any(status[key] is not None for key in ['R_handler_warnings','R_handler_messages','R_handler_errors']):raise AssertionError('Premature successful process exit lost partial evidence or reported unknown counts as zero')
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise AssertionError('Executed source changed: '+name)
  for name,wanted in inputs.items():
   if pin(Path(name))!=wanted:raise AssertionError('Original caller input changed: '+name)
  provider(Path(base['provider_directory']))
  report=dict(status='PASS_SEVEN_R_ONLY_FIGURE_PERMISSION_TYPE_AND_INCOMPLETE_WORKER_CONTROLS',cases=cases,calls=calls,source_pins=sources,original_inputs_and_runtime_unchanged=True,new_family_credit=0,whole_R_wrapper_qualified=False,whole_goal_complete=False)
  save(out/'COMPLETED.json',report);print(report['status'],flush=True);return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_R_ONLY_FIGURE_PROTOCOL_CONTROLS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=cases,calls=calls,required_action=PLOT_ACTION))
  print('FAIL2_R_ONLY_FIGURE_PROTOCOL_CONTROLS_UNADMITTED',repr(error),PLOT_ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
