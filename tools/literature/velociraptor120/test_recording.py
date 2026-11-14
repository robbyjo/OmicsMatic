#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real R runs whose final record destinations are deliberately unavailable."""
from pathlib import Path
import argparse,copy,os,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,load,pin,save


def main():
 parser=argparse.ArgumentParser(description=__doc__)
 for name in ['numeric-request','figure-request','figure-provider','out']:parser.add_argument('--'+name,type=Path,required=True)
 args=parser.parse_args();out=args.out.absolute();owned=False;rows=[]
 try:
  if sys.platform!='linux' or out!=out.resolve():raise ValueError('Use Linux/WSL and a canonical NEW output')
  for protected in [HERE,args.numeric_request.resolve(),args.figure_request.resolve(),args.figure_provider.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps a protected path')
  out.mkdir(exist_ok=False);owned=True;sources={p.name:pin(p) for p in HERE.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',sources)
  bases={'numeric':load(args.numeric_request),'figures':load(args.figure_request)}
  bases['figures']['provider_directory']=str(args.figure_provider)
  originals={name:pin(Path(name)) for base in bases.values() for name in base['inputs'].values()}
  for mode,base in bases.items():
   cases=[('warning',['WARNINGS.md']),('manifest',['MANIFEST.json']),('status',['analysis-status.json'])]
   if mode=='numeric':cases.append(('all-final-records',['WARNINGS.md','MANIFEST.json','analysis-status.json','FINALIZATION_ERROR.json']))
   for label,blocked in cases:
    name=mode+'-'+label;case=out/name;case.mkdir();target=case/'analysis';doc=copy.deepcopy(base)
    if mode=='numeric':
     body='analysis_result <- readRDS(file.path(input_directory,"inputs.rds"))\n';doc.update(required_exports=['gridVectors'],required_packages={})
    else:
     body='call <- readRDS(file.path(input_directory,"call.rds"))\nset.seed(as.integer(call$render$seed))\nanalysis_result <- do.call(getExportedValue("velociraptor",call$export),call$arguments)\n'
    body+='analysis_complete <- "COMPLETE_RECORDING_FAULT_CONTROL"\n'
    for filename in blocked:body+='fault.path <- file.path(dirname(result_directory),"'+filename+'")\nstopifnot(!file.exists(fault.path),dir.create(fault.path))\n'
    script=case/'Analysis.R';script.write_text(body,encoding='utf-8',newline='\n');doc.update(script=str(script),out=str(target),expected_outputs={},expected_return='COMPLETE_RECORDING_FAULT_CONTROL');request=case/'request.json';save(request,doc)
    entry=HERE/('run.py' if mode=='numeric' else 'figures.py');command=[sys.executable,'-B',str(entry),'--request',str(request)];started=time.monotonic()
    with (case/'controller.stdout').open('xb') as so,(case/'controller.stderr').open('xb') as se:
     child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(case=name,pid=child.pid,command=command,started_unix=time.time()))
     while True:
      try:code=child.wait(timeout=20);break
      except subprocess.TimeoutExpired:print('SAME_RECORDING_CONTROL_CHILD_RUNNING',name,child.pid,round(time.monotonic()-started),flush=True)
    stdout=(case/'controller.stdout').read_text(errors='replace');terminal=[line for line in stdout.splitlines() if line.startswith(('FAIL2','PASS','SKIP3'))]
    if code!=2 or not terminal or not terminal[-1].startswith('FAIL2') or 'NEW output directory' not in stdout:raise AssertionError('Recording failure lacked terminal FAIL2 and actionable recovery: '+name)
    if not (target/'diagnostics/analysis-started.json').is_file():raise AssertionError('Original R analysis was not actually entered')
    worker=load(target/'diagnostics/worker-status.json')
    if worker['exit_code']!=0:raise AssertionError('Fault control did not reach complete original computation: '+name)
    status_path=target/'analysis-status.json';status=load(status_path) if status_path.is_file() else None
    if 'analysis-status.json' not in blocked:
     if not status or status['exit_code']!=2 or status['result_admitted'] or not status['status'].startswith('FAIL2'):raise AssertionError('Saved failure status admitted a result')
     if status['complete_manifest']!=('MANIFEST.json' not in blocked):raise AssertionError('Complete manifest availability was misreported')
    elif not status_path.is_dir():raise AssertionError('Blocked original status destination was altered')
    warning=target/'WARNINGS.md'
    if 'WARNINGS.md' not in blocked:
     message=warning.read_text()
     if not message.startswith('# FAIL2') or 'Complete result admitted: False' not in message:raise AssertionError('Warnings retained a stale PASS or admission')
    elif not warning.is_dir():raise AssertionError('Blocked warning destination was altered')
    manifest=target/'MANIFEST.json';verified=0
    if 'MANIFEST.json' not in blocked:
     records=load(manifest)['files'];regular_files={p.relative_to(target).as_posix():p for p in target.rglob('*') if p.is_file() and not p.is_symlink() and p.relative_to(target).as_posix() not in ['MANIFEST.json','analysis-status.json']}
     if set(regular_files)!={name for name,row in records.items() if 'kind' not in row}:raise AssertionError('Final complete regular-file population differs')
     for filename,path in regular_files.items():
      if pin(path)!=records[filename]:raise AssertionError('Stale complete manifest hash: '+filename)
      verified+=1
     for filename,row in records.items():
      if row.get('kind')=='symlink' and os.readlink(target/filename)!=row['target']:raise AssertionError('Literal link changed')
    if label=='status':
     fallback=load(target/'FINALIZATION_ERROR.json')
     if fallback['exit_code']!=2 or fallback['result_admitted'] or fallback['required_status_recorded']:raise AssertionError('Fallback record falsely claimed successful final admission')
    row=dict(case=name,exit_code=code,final_status_line=terminal[-1],blocked_records=blocked,complete_worker_before_recording_failure=True,complete_regular_file_hashes_verified=verified,saved_status=status)
    rows.append(row);save(out/'CASES.json',rows);print('PASS_ACTUAL_RECORDING_REFUSAL',name,flush=True)
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise AssertionError('Executed source changed: '+name)
  for name,wanted in originals.items():
   if pin(Path(name))!=wanted:raise AssertionError('Original input changed: '+name)
  save(out/'COMPLETED.json',dict(status='PASS_SEVEN_ACTUAL_ORIGINAL_R_FINAL_RECORDING_REFUSALS',cases=rows,source_pins=sources,original_inputs_unchanged=True,new_family_credit=0,whole_goal_complete=False));return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_RECORDING_CONTROLS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=rows,required_action=ACTION))
  print('FAIL2_RECORDING_CONTROLS_UNADMITTED',repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
