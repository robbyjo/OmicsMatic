#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise refusal/retention policies and optional actual original R worker controls."""
from pathlib import Path
import argparse,copy,math,struct,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,load,pin,save
from RValues import load_tree

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--base-request',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--run-original-controls',action='store_true');args=parser.parse_args()
 out=args.out.absolute();owned=False;rows=[];calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW protocol test output required')
  out.mkdir(exist_ok=False);owned=True;base=load(args.base_request);ready=load(Path(base['provider_directory'])/'READY.json')
  sources={p.name:pin(p) for p in HERE.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',sources)
  original_inputs={value:pin(Path(value)) for value in base['inputs'].values()}
  def invoke(name,command):
   command=list(map(str,command));started=time.monotonic()
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(case=name,pid=child.pid,command=command))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_PROTOCOL_CHILD_RUNNING',name,child.pid,round(time.monotonic()-started),flush=True)
   calls.append(dict(case=name,command=command,exit_code=code,elapsed_seconds=time.monotonic()-started));save(out/'PROCESSES.json',calls)
   return code
  def request_case(name,alter,expected,*,raw=None,preexisting=False,external_out=False):
   doc=copy.deepcopy(base);doc['out']=str(out/(name+'-result'));alter(doc);path=out/(name+'-request.json')
   if raw is None:save(path,doc)
   else:path.write_text(raw(doc),encoding='utf-8',newline='\n')
   result=Path(doc['out'])
   if preexisting:result.mkdir();(result/'KEEP.txt').write_text('Preserve existing user bytes\n',encoding='utf-8')
   code=invoke(name,[sys.executable,'-B',HERE/'run.py','--request',path])
   if code!=expected:raise AssertionError(name+' exit '+str(code)+' != '+str(expected))
   if expected:
    if result.is_dir() and (result/'analysis-status.json').is_file():
     status=load(result/'analysis-status.json')
     if status['result_admitted'] or status['exit_code']!=expected:raise AssertionError('Partial/forbidden result admitted: '+name)
     if not (result/'WARNINGS.md').is_file():raise AssertionError('Prominent warning missing: '+name)
    elif not external_out and result.exists() and not preexisting:raise AssertionError('Unexpected output without failure status: '+name)
    raw_output=(out/(name+'.stdout')).read_text(errors='replace')+(out/(name+'.stderr')).read_text(errors='replace')
    if 'Required action:' not in raw_output:raise AssertionError('Recovery action absent from console: '+name)
   if preexisting and (result/'KEEP.txt').read_text()!='Preserve existing user bytes\n':raise AssertionError('Existing output changed')
   rows.append(dict(case=name,expected_exit=expected,observed_exit=code,result=str(result)));save(out/'CASES.json',rows);return result
  for policy in ['execution_policy','trusted_code_policy']:
   result=request_case('forbid-'+policy,lambda d,key=policy:d.update({key:'FORBID'}),3)
   if (result/'process-live.json').exists():raise AssertionError('Forbidden worker launched')
  request_case('missing-provider',lambda d:d.update(provider_directory=str(out/'absent-provider')),3)
  request_case('duplicate-key',lambda d:None,2,raw=lambda d:'{"version":"1.20.0",'+__import__('json').dumps(d)[1:])
  request_case('boolean-capacity',lambda d:d['limits'].update(maximum_files=True),2)
  request_case('existing-output',lambda d:None,2,preexisting=True)
  request_case('input-overlap',lambda d:d.update(out=next(iter(d['inputs'].values()))),2,external_out=True)
  missing=out/'missing-backend-provider';missing.mkdir();copy_ready=copy.deepcopy(ready)
  copy_ready['library']=str(out/'absent-private-library');copy_ready['backend']=copy_ready['library']+'/velociraptor/basilisk/env'
  save(missing/'READY.json',copy_ready)
  (missing/'CONTROL_ONLY.txt').write_text('Deliberately incomplete readiness input for a missing-backend refusal. This is not a real qualified provider.\n',encoding='utf-8')
  result=request_case('missing-installed-backend',lambda d:d.update(provider_directory=str(missing)),3)
  if (result/'process-live.json').exists():raise AssertionError('Missing backend caused analysis/lazy installation')
  for name,allow in [('install-forbidden',False),('download-forbidden',True)]:
   target=out/name;cmd=[sys.executable,'-B',HERE/'install_runtime.py','--r-base',Path(ready['r_prefix']).parents[2],'--python-base',Path(ready['standalone_python']).parents[1],'--out',target]
   if allow:cmd.append('--allow-install')
   code=invoke(name,cmd)
   if code!=3 or not (target/'WARNINGS_FAILURE.md').is_file() or (target/'LIVE.json').exists():raise AssertionError('Forbidden setup did not skip before installer launch: '+name)
   rows.append(dict(case=name,expected_exit=3,observed_exit=code,result=str(target)));save(out/'CASES.json',rows)
  if args.run_original_controls:
   failure_script=out/'original-failure.R';failure_script.write_text('available_original_value <- readRDS(file.path(input_directory,"inputs.rds"))\nstop("CONTROL original failure: literal NA and beta β")\n',encoding='utf-8',newline='\n')
   def error_case(d):d.update(script=str(failure_script),expected_outputs={})
   result=request_case('original-error',error_case,2);status=load(result/'diagnostics/worker-status.json')
   if status['error_count']!=1 or status['phase']!='ANALYSIS' or not status['original_R_temporary_tree_retained']:raise AssertionError('Original error/phase/native temporary evidence incomplete')
   for name in ['complete-original-error-and-calls.rds','complete-available-caller-scope.rds','complete-conditions.rds','complete-Python-last-error.rds']:
    if not (result/'diagnostics'/name).is_file() or not (result/'diagnostics'/name).stat().st_size:raise AssertionError('Original failure object missing: '+name)
   if not (result/'native-temporary-files.json').is_file():raise AssertionError('Durable native temporary inventory missing')
   warning_script=out/'original-warnings.R';warning_script.write_text('''original_input <- readRDS(file.path(input_directory,"inputs.rds"))
warning("CONTROL original warning: literal NA and beta β")
message("CONTROL original message: Gamma Γ")
analysis_result <- list(grid=velociraptor::gridVectors(matrix(c(0,0,1,1),ncol=2,byrow=TRUE),matrix(0,nrow=2,ncol=2),resolution=4L,return.intermediates=TRUE),literal_identifiers=c("NA",NA_character_,"beta_β","Gamma_Γ"),original_nonfinite_values=c(NA_real_,NaN,Inf,-Inf))
analysis_complete <- "CONTROL_COMPLETE_ORIGINAL_GRID_AND_NONFINITE"
''',encoding='utf-8',newline='\n')
   def warning_case(d):d.update(script=str(warning_script),expected_outputs={},required_exports=['gridVectors'],expected_return='CONTROL_COMPLETE_ORIGINAL_GRID_AND_NONFINITE')
   result=request_case('original-warning-nonfinite',warning_case,0);status=load(result/'analysis-status.json');worker=load(result/'diagnostics/worker-status.json')
   if not status['result_admitted'] or not status['warning_review_required'] or status['raw_console_diagnostic_counts_complete'] or worker['warning_count']<2 or worker['message_count']<1:raise AssertionError('Original warning/nonfinite review semantics were lost')
   values=load_tree(result/'results/result-value-tree');matched=False
   for node in values['index']['nodes']:
    if node['type']=='double' and node['length']==4 and node['payload'] is not None:
     data=(result/'results/result-value-tree'/node['payload']['path']).read_bytes();numbers=struct.unpack('<4d',data)
     if math.isnan(numbers[0]) and math.isnan(numbers[1]) and numbers[2:]==(math.inf,-math.inf):matched=True
   if not matched:raise AssertionError('Original complete NA/NaN/Inf values missing')
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise AssertionError('Executed source changed: '+name)
  for name,wanted in original_inputs.items():
   if pin(Path(name))!=wanted:raise AssertionError('Original input changed: '+name)
  result=dict(status='PASS_ORIGINAL_VELOCIRAPTOR_PROTOCOL_CONTROLS',cases=rows,case_count=len(rows),original_worker_controls_executed=args.run_original_controls,calls=calls,source_pins=sources,original_inputs_unchanged=True,new_family_credit=0,whole_R_wrapper_qualified=False,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],len(rows),flush=True);return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_PROTOCOL_CONTROLS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=rows,calls=calls,required_action=ACTION))
  print('FAIL2_PROTOCOL_CONTROLS_UNADMITTED',repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
