#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the separately retained direct original R scripts under an explicit provider."""
from pathlib import Path
import argparse,os,shutil,subprocess,sys,tempfile,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,environment,load,pin,provider,save
from RValues import load_tree

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 for name in ['provider','fixture','direct-stochastic','direct-dynamical','out']:parser.add_argument('--'+name,type=Path,required=True)
 args=parser.parse_args();out=args.out.absolute();owned=False;calls=[];cases=[]
 try:
  if sys.platform!='linux':raise OSError('Use the original Linux/WSL provider')
  if out!=out.resolve():raise ValueError('Canonical NEW original-reference output required')
  for path in [HERE,args.provider.resolve(),args.fixture.resolve(),args.direct_stochastic.resolve(),args.direct_dynamical.resolve()]:
   if out.is_relative_to(path) or path.is_relative_to(out):raise ValueError('Reference output overlaps protected source/provider/input')
  out.mkdir(exist_ok=False);owned=True;print('VERIFYING_COMPLETE_RUNTIME_BEFORE_DIRECT_ORIGINAL_REFERENCES',flush=True);ready=provider(args.provider)
  fixture_pins={p.relative_to(args.fixture).as_posix():pin(p) for p in args.fixture.rglob('*') if p.is_file()}
  sources={p.name:pin(p) for p in HERE.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',sources)
  for mode,original in [('stochastic',args.direct_stochastic),('dynamical',args.direct_dynamical)]:
   case=out/mode;case.mkdir();script=case/'DirectOriginal.R';script.write_bytes(original.read_bytes());script_pin=pin(original)
   if pin(script)!=script_pin:raise ValueError('Direct original scientific script bytes differ')
   shutil.copytree(args.fixture,case/'fixture');results=case/'original-results';results.mkdir()
   for name in ['owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache']:(case/name).mkdir()
   native=Path(tempfile.mkdtemp(prefix='ovor-',dir='/tmp'));save(case/'native-temporary.json',dict(path=str(native),original_native_root_deleted=False,scope='Available files after unchanged direct R process exit; original R performs its normal session-temp cleanup'))
   command=[ready['rscript'],'--vanilla',str(script),ready['library'],str(case/'fixture'),str(results)];start=time.monotonic()
   with (case/'process.stdout').open('xb') as so,(case/'process.stderr').open('xb') as se:
    child=subprocess.Popen(command,cwd=case,env=environment(ready,case,temporary_directory=native),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(out/'LIVE.json',dict(mode=mode,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_DIRECT_ORIGINAL_CHILD_RUNNING',mode,child.pid,round(time.monotonic()-start),flush=True)
   row=dict(mode=mode,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start,direct_R_script=script_pin);calls.append(row);save(out/'PROCESSES.json',calls)
   shutil.copytree(native,case/'available-native-temporary',symlinks=True)
   if code:raise ValueError('Original direct reference failed: '+mode+'; retain complete diagnostics and partial objects')
   marker=(results/'ORIGINAL-COMPLETE.txt').read_text().strip()
   if marker!='COMPLETE_ORIGINAL_'+mode.upper()+'_SCE_160_CELLS_12_GENES' and not (mode=='stochastic' and marker=='COMPLETE_ORIGINAL_STOCHASTIC_ANY_160_CELLS_12_GENES'):raise ValueError('Complete original population marker differs: '+marker)
   value=load_tree(results/'complete-value-tree')
   copied={p.relative_to(case/'fixture').as_posix():pin(p) for p in (case/'fixture').rglob('*') if p.is_file()}
   if copied!=fixture_pins or pin(original)!=script_pin:raise ValueError('Original scientific input/script population changed')
   cases.append(dict(mode=mode,cells=160,genes=12,complete_original_R_value_nodes=value['index']['node_count'],original_R_result=pin(results/'COMPLETE-ORIGINAL-RESULT.rds'),original_inputs=pin(results/'COMPLETE-INPUTS.rds'),caller_parameters=pin(results/'COMPLETE-CALLER-PARAMETERS.rds'),direct_R_script_unchanged=True));save(out/'COMPLETE-CASES.json',cases)
  print('VERIFYING_COMPLETE_RUNTIME_AFTER_DIRECT_ORIGINAL_REFERENCES',flush=True);provider(args.provider)
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise ValueError('Executed helper source changed: '+name)
  save(out/'COMPLETED.json',dict(status='PASS_TWO_UNWRAPPED_COMPLETE_ORIGINAL_R_REFERENCES_EXPLICIT_PROVIDER',cases=cases,calls=calls,source_pins=sources,original_fixture_files=fixture_pins,adapter_worker_executed=False,analysis_request_adapter_executed=False,author_runtime_recovered=False,original_algorithms_modified=False,new_family_credit=0,whole_R_wrapper_qualified=False,whole_goal_complete=False,warning_review_required=True,raw_console_diagnostic_counts_complete=False))
  print('PASS_TWO_UNWRAPPED_COMPLETE_ORIGINAL_R_REFERENCES_EXPLICIT_PROVIDER',flush=True);return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_COMPLETE_ORIGINAL_REFERENCES_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),calls=calls,completed_cases=cases,required_action=ACTION))
  print('FAIL2_COMPLETE_ORIGINAL_REFERENCES_UNADMITTED',repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
