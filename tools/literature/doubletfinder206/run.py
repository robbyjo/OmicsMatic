#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run one unchanged original DoubletFinder operation with a complete trusted RDS request."""
from pathlib import Path
import re
import argparse,os,shutil,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,load,save,pin,check,inventory,environment

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['runtime','request','out']:parser.add_argument('--'+n,required=True,type=Path)
 parser.add_argument('--wall-seconds',type=int,default=1800);parser.add_argument('--maximum-files',type=int,default=50000);parser.add_argument('--maximum-workspace-bytes',type=int,default=2147483648)
 args=parser.parse_args();out=args.out.absolute();owned=False;child=None;code=2;cause='FAIL2_UNADMITTED';admitted=False;record=None
 def capacity():
  count=0;total=0
  for parent,dirs,names in os.walk(out,followlinks=False):
   for name in names:
    path=Path(parent)/name
    try:size=path.lstat().st_size
    except FileNotFoundError:continue # Original temporary files can disappear during a live scan.
    count+=1;total+=size
  if count>args.maximum_files or total>args.maximum_workspace_bytes:raise ValueError('Declared complete-output file/byte capacity exceeded; retain all partial files and retry NEW paths with adequate capacities')
 try:
  request=args.request.resolve();runtime=args.runtime.resolve()
  if out!=out.resolve() or any(out.is_relative_to(x) or x.is_relative_to(out) for x in [HERE,request,runtime]):raise ValueError('Use a NEW nonoverlapping canonical output')
  if min(args.wall_seconds,args.maximum_files,args.maximum_workspace_bytes)<1:raise ValueError('Positive explicit capacities required')
  out.mkdir(exist_ok=False);owned=True
  if sys.platform!='linux':raise Unavailable('Original DoubletFinder runtime requires Linux/WSL; no dependent analysis started')
  ready=load(runtime/'READY.json')
  if any(out.is_relative_to(Path(ready[key])) or Path(ready[key]).is_relative_to(out) for key in ['prefix','source']):raise ValueError('Output overlaps protected runtime/source')
  check(ready)
  for name in ['home','tmp','original']:(out/name).mkdir()
  request_pin=pin(request);shutil.copyfile(request,out/'request.rds')
  save(out/'INPUT.json',dict(path=str(request),identity=request_pin,runtime=str(runtime),runtime_identity=pin(runtime/'READY.json'),limits=dict(wall_seconds=args.wall_seconds,maximum_files=args.maximum_files,maximum_workspace_bytes=args.maximum_workspace_bytes)))
  control=dict(library=ready['library'],base_library=ready['base_library'],source=ready['source'],profile=str(HERE/'source-profile.json'),output=str(out/'original'),request=str(out/'request.rds'))
  save(out/'control.json',control)
  capacity()
  command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'Worker.R'),str(out/'control.json')];start=time.monotonic()
  with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
   child=subprocess.Popen(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
   save(out/'LIVE.json',dict(pid=child.pid,command=command,started_unix=time.time()))
   while True:
    try:result=child.wait(timeout=2);break
    except subprocess.TimeoutExpired:
     elapsed=time.monotonic()-start
     exceeded=elapsed>args.wall_seconds
     try:capacity()
     except ValueError:exceeded=True
     if exceeded:
      os.killpg(child.pid,signal.SIGTERM)
      try:child.wait(timeout=5)
      except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
      raise ValueError('Declared complete-output/time capacity exceeded; own child stopped, partial files retained; retry NEW paths with adequate capacities')
  save(out/'PROCESS.json',dict(command=command,exit_code=result,elapsed_seconds=time.monotonic()-start))
  capacity()
  if not (out/'original/R-status.json').is_file():raise ValueError('Original child exited '+str(result)+' without its required terminal R record; inspect full consoles and every retained original/partial file')
  record=load(out/'original/R-status.json')
  if result or record['status']!='PASS_ORIGINAL_DOUBLET_FINDER206_COMPLETE_R_OBJECT' or not record['complete_original_RDS_result']:raise ValueError('Original analysis failed; inspect complete original condition objects and consoles')
  if pin(request)!=request_pin or pin(out/'request.rds')!=request_pin:raise ValueError('Input changed during execution; preserve both versions and qualify NEW paths')
  check(ready)
  capacity()
  if not (out/'original/complete-result.rds').is_file():raise ValueError('Complete original RDS return missing')
  code=0;cause='PASS_ORIGINAL_DOUBLET_FINDER206_COMPLETE_R_OBJECT';admitted=True
 except BaseException as e:
  code=3 if child is None and isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2;cause=('SKIP3_ORIGINAL_REQUIREMENT_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_ANALYSIS_UNADMITTED')+': '+repr(e)
  if owned:
   try:save(out/'FAILURE.json',dict(status=cause,traceback=traceback.format_exc(),required_action=ACTION))
   except OSError as recording:code=2;cause='FAIL2_FAILURE_RECORDING_UNAVAILABLE: '+repr(recording)+'; '+cause
 finally:
  if child is not None and child.poll() is None:
   os.killpg(child.pid,signal.SIGTERM)
   try:child.wait(timeout=5)
   except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
  if owned:
   def warning():
    counts=None if record is None else record['R_handler_counts'];iteration=None if record is None else record['recognized_iteration_limit_warnings']
    text=('# '+cause+'\n\n**REVIEW REQUIRED. Original R handler totals: '+str(counts)+'. Recognized iteration-limit warnings: '+str(iteration)+'. Numerical convergence is not qualified.**\n\nComplete original R object admitted: '+str(admitted)+'. Raw diagnostic totals are unavailable, never zero.\n\n')
    summary=out/'original/condition-summary.json'
    if summary.is_file():
     for group in load(summary)['groups']:
      if group['kind'] in ['warning','error'] or re.search(r'could not find|not installed|falling back|install[.]',group['message'],re.I):
       text+='**Original '+group['kind']+' x '+str(group['count'])+'**\n\n'+''.join('    '+line+'\n' for line in group['message'].splitlines())+'\n'
    if iteration:
     text+='**Do not treat this output as a converged SCT analysis.** Preserve the original objects. Review the original estimator and data with the complete conditions. Any changed estimator or permitted dependency profile needs independent original controls and a NEW runtime/output; do not round artificial counts, suppress warnings or change prior evidence to obtain a pass.\n\n'
    text+=ACTION+'\n';(out/'WARNINGS.md').write_text(text,encoding='utf-8',newline='\n')
   def manifest():save(out/'MANIFEST.json',dict(files={n:v for n,v in inventory(out).items() if n not in ['MANIFEST.json','analysis-status.json']}))
   try:
    warning();manifest();save(out/'analysis-status.json',dict(status=cause,exit_code=code,result_admitted=admitted,R_handler_counts=None if record is None else record['R_handler_counts'],recognized_iteration_limit_warnings=None if record is None else record['recognized_iteration_limit_warnings'],convergence_qualified=False,raw_console_diagnostic_counts_complete=False,warning_review_required=True,complete_original_RDS_return=admitted,portable_value_tree_claimed=False,whole_tool_qualified=False,whole_goal_complete=False,required_action=ACTION))
   except BaseException as e:
    code=2;admitted=False;cause='FAIL2_FINAL_RECORDING_UNAVAILABLE: '+repr(e)+'; '+cause
    print(cause+'\nPreserve all original outputs; resolve the named recording error and retry NEW writable paths.\n'+ACTION,flush=True)
    for action in [lambda:save(out/'FINALIZATION_ERROR.json',dict(status=cause,result_admitted=False,exit_code=2,required_action=ACTION)),warning,manifest]:
     try:action()
     except BaseException as recording:print('FAIL2_FALLBACK_RECORDING_UNAVAILABLE',repr(recording),flush=True)
  print(cause,flush=True)
  print('REVIEW REQUIRED: original R handler totals:',None if record is None else record['R_handler_counts'],'recognized iteration-limit warnings:',None if record is None else record['recognized_iteration_limit_warnings'],'Numerical convergence is not qualified. See WARNINGS.md and complete-conditions.rds.',flush=True)
  print(ACTION,flush=True)
 return code
if __name__=='__main__':raise SystemExit(main())
