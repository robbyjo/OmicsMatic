#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual public SPOTlight CLI outcomes and preserve complete failed analyses."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['runtime','requests','manager','out']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
 try:
  ready=load(a.runtime/'READY.json');check(ready);before=sources()
  stale=out/'owned-stale-provider';stale.mkdir();row=load(a.runtime/'READY.json');row['schema']='owned-invalid-schema';save(stale/'READY.json',row)
  calls=out/'calls';calls.mkdir();results=[]
  def invoke(name,request,expected,extra=(),mode=None,runtime=None):
   target=out/name;script=VALIDATION/'instrumented_runner.py' if mode else MODULE/'run.py'
   command=[sys.executable,'-B',str(script)]+([mode] if mode else [])+['--runtime',str(runtime or a.runtime),'--request',str(a.requests/(request+'.rds')),'--out',str(target),*map(str,extra)]
   with (calls/(name+'.stdout')).open('xb') as so,(calls/(name+'.stderr')).open('xb') as se:
    process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(calls/(name+'-LIVE.json'),dict(pid=process.pid,command=command))
    try:code=process.wait(timeout=1200)
    except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=30);raise
   save(calls/(name+'-PROCESS.json'),dict(exit_code=code,command=command))
   if code!=expected:raise ValueError('Unexpected actual public exit: '+name+' '+str(code))
   if expected:
    if (target/'COMPLETED.json').exists() or load(target/'FAILURE.json')['complete_result_admitted']:raise ValueError('Failed public call admitted a result: '+name)
   else:
    if load(target/'COMPLETED.json')['status']!='PASS_ORIGINAL_SPOTLIGHT103_COMPLETE_RETURN_AND_RECORDS':raise ValueError('Public positive did not complete')
   print('ACTUAL_PUBLIC_SPOTLIGHT103_GUARD',name,code,flush=True)
   return dict(name=name,exit_code=code,passed=True)
  cases=[
   ('offline-success','matrix.default',0,[],None,None),
   ('original-warning-success','SCE.labels.warning',0,[],None,None),
   ('original-analysis-error','error.model',2,[],None,None),
   ('missing-provider-skip','matrix.default',3,[],None,out/'missing-provider'),
   ('stale-provider-fails','matrix.default',2,[],None,stale),
   ('real-original-child-timeout','matrix.default',3,['--wall-seconds','1'],None,None),
   ('owned-recording-fault','matrix.default',2,[],'recording-fault',None),
   ('owned-captured-input-tamper','matrix.default',2,[],'captured-input-tamper',None),
   ('real-zero-exit-missing-status','matrix.default',2,[],'missing-status',None)]
  with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda row:invoke(*row),cases))
  for name in ['owned-recording-fault','owned-captured-input-tamper','real-zero-exit-missing-status']:
   if not (out/name/'original/complete-original-return.rds').is_file():raise ValueError('Postreturn guard did not preserve the real original return: '+name)
  timeout=load(out/'real-original-child-timeout/FAILURE.json')
  if timeout['actual_original_process_exit_code'] is None or 'wall limit' not in timeout['cause']:raise ValueError('Actual original child timeout was not exercised')
  positive=out/'offline-success';preserved=inventory(positive)
  command=[sys.executable,'-B',str(MODULE/'run.py'),'--runtime',str(a.runtime),'--request',str(a.requests/'matrix.default.rds'),'--out',str(positive)]
  with (calls/'existing-output.stdout').open('xb') as so,(calls/'existing-output.stderr').open('xb') as se:code=subprocess.run(command,stdout=so,stderr=se,timeout=60).returncode
  if code!=2 or inventory(positive)!=preserved:raise ValueError('Existing complete output was not preserved')
  results.append(dict(name='existing-complete-output-refused-and-preserved',exit_code=code,passed=True))
  command=[sys.executable,'-B',str(MODULE/'install_runtime.py'),'--manager',str(a.manager),'--out',str(out/'forbidden-install')]
  with (calls/'forbidden-install.stdout').open('xb') as so,(calls/'forbidden-install.stderr').open('xb') as se:code=subprocess.run(command,stdout=so,stderr=se,timeout=60).returncode
  if code!=3 or (out/'forbidden-install/COMPLETED.json').exists():raise ValueError('Forbidden installation did not skip')
  results.append(dict(name='installation-without-permission-skipped',exit_code=3,passed=True))
  warning=(calls/'original-warning-success.stdout').read_text()
  if 'ORIGINAL_SPOTLIGHT_WARNING:' not in warning or 'Grouping cells' not in (out/'original-warning-success/WARNINGS.md').read_text():raise ValueError('Actual original warning was not displayed prominently')
  if 'arg' not in (out/'original-analysis-error/WARNINGS.md').read_text():raise ValueError('Original error cause was not displayed prominently')
  check(ready)
  if sources()!=before:raise ValueError('Protected runtime/source/seed changed during public guards')
  save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_PUBLIC_SPOTLIGHT103_REFERENCE_AND_FAILURE_GUARDS',guards=results,source_or_dependency_algorithms_patched=False,
   owned_injections_modify_only_captured_input_or_recording_behavior=True,complete_original_postreturn_objects_preserved=True,new_family_credit=0,whole_goal_complete=False))
  print('PASS_ACTUAL_PUBLIC_SPOTLIGHT103_REFERENCE_AND_FAILURE_GUARDS',len(results),flush=True);return 0
 except BaseException as error:
  save(out/'FAILURE.json',dict(status='FAIL2_SPOTLIGHT103_PUBLIC_GUARDS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION));print('FAIL2_SPOTLIGHT103_PUBLIC_GUARDS_UNADMITTED',repr(error),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
