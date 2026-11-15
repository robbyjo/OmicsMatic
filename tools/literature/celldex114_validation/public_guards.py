#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual public celldex CLI outcomes and preserve complete failed analyses."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'celldex114';sys.path.insert(0,str(MODULE))
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['runtime','requests','reference-cache','stale-provider','manager','out']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
 try:
  ready=load(a.runtime/'READY.json');check(ready);before=sources();seed=validate_cache(a.reference_cache)
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
    if load(target/'COMPLETED.json')['status']!='PASS_RECORDED_ORIGINAL_CELLDEX114_REFERENCE_OPERATION':raise ValueError('Public positive did not complete')
   print('ACTUAL_PUBLIC_CELLDEX_GUARD',name,code,flush=True)
   return dict(name=name,exit_code=code,passed=True)
  cases=[
   ('offline-lazy-success','immgen.lazy',0,['--reference-cache',a.reference_cache],None,None),
   ('original-ontology-error','invalid.ontology',2,[],None,None),
   ('empty-cache-offline-skip','immgen.default',3,[],None,None),
   ('remote-listing-offline-skip','remote.listing',3,[],None,None),
   ('missing-provider-skip','pure.text.query',3,[],None,out/'missing-provider'),
   ('stale-provider-fails','pure.text.query',2,[],None,a.stale_provider),
   ('real-original-child-timeout','immgen.metadata',2,['--reference-cache',a.reference_cache,'--wall-seconds','1'],None,None),
   ('owned-recording-fault','immgen.metadata',2,['--reference-cache',a.reference_cache],'recording-fault',None),
   ('owned-captured-input-tamper','immgen.metadata',2,['--reference-cache',a.reference_cache],'captured-input-tamper',None),
   ('real-zero-exit-missing-status','immgen.metadata',2,['--reference-cache',a.reference_cache],'missing-status',None)]
  with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda row:invoke(*row),cases))
  for name in ['owned-recording-fault','owned-captured-input-tamper','real-zero-exit-missing-status']:
   if not (out/name/'original/complete-original-return.rds').is_file():raise ValueError('Postreturn guard did not preserve the real original return: '+name)
  timeout=load(out/'real-original-child-timeout/FAILURE.json')
  if not timeout['child_launched'] or 'time limit' not in timeout['cause']:raise ValueError('Actual original child timeout was not exercised')
  positive=out/'offline-lazy-success';preserved=inventory(positive)
  command=[sys.executable,'-B',str(MODULE/'run.py'),'--runtime',str(a.runtime),'--request',str(a.requests/'immgen.lazy.rds'),'--out',str(positive)]
  with (calls/'existing-output.stdout').open('xb') as so,(calls/'existing-output.stderr').open('xb') as se:code=subprocess.run(command,stdout=so,stderr=se,timeout=60).returncode
  if code!=2 or inventory(positive)!=preserved:raise ValueError('Existing complete output was not preserved')
  results.append(dict(name='existing-complete-output-refused-and-preserved',exit_code=code,passed=True))
  command=[sys.executable,'-B',str(MODULE/'install_runtime.py'),'--manager',str(a.manager),'--out',str(out/'forbidden-install')]
  with (calls/'forbidden-install.stdout').open('xb') as so,(calls/'forbidden-install.stderr').open('xb') as se:code=subprocess.run(command,stdout=so,stderr=se,timeout=60).returncode
  if code!=3 or (out/'forbidden-install/COMPLETED.json').exists():raise ValueError('Forbidden installation did not skip')
  results.append(dict(name='installation-without-permission-skipped',exit_code=3,passed=True))
  check(ready)
  if sources()!=before or validate_cache(a.reference_cache)!=seed:raise ValueError('Protected runtime/source/seed changed during public guards')
  save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_PUBLIC_CELLDEX114_REFERENCE_AND_FAILURE_GUARDS',guards=results,source_or_dependency_algorithms_patched=False,
   owned_injections_modify_only_captured_input_or_recording_behavior=True,complete_original_postreturn_objects_preserved=True,new_family_credit=0,whole_goal_complete=False))
  print('PASS_ACTUAL_PUBLIC_CELLDEX114_REFERENCE_AND_FAILURE_GUARDS',len(results),flush=True);return 0
 except BaseException as error:
  save(out/'FAILURE.json',dict(status='FAIL2_CELLDEX114_PUBLIC_GUARDS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION));print('FAIL2_CELLDEX114_PUBLIC_GUARDS_UNADMITTED',repr(error),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
