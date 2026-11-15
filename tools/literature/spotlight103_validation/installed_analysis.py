#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual installed-runtime reference retrieval and independent complete original comparison."""
from pathlib import Path
import argparse,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['runtime','request','out']:p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
 try:
  ready=load(a.runtime/'READY.json');check(ready);before=sources()
  for n in ['home','tmp']:(out/n).mkdir()
  source_names=[VALIDATION/'DirectOriginal.R',VALIDATION/'Compare.R',Path(__file__)];extra={f.relative_to(ROOT).as_posix():pin(f) for f in source_names}
  save(out/'SOURCES.json',dict(before,**extra));commands=[]
  def run(label,command):
   command=list(map(str,command))
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(out/(label+'-LIVE.json'),dict(pid=child.pid,command=command))
    try:code=child.wait(timeout=1800)
    except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=30);raise
   record=dict(command=command,exit_code=code);save(out/(label+'-PROCESS.json'),record);commands.append(record)
   if code:raise ValueError('Actual installed reference step failed: '+label+'; read complete logs')
  command=[sys.executable,'-B',MODULE/'run.py','--runtime',a.runtime,'--request',a.request,'--out',out/'public']
  run('actual-public',command)
  c=dict(control(ready),edition='1.0.3',request=str(a.request.resolve()),output=str(out/'independent-original'))
  save(out/'direct-control.json',c);rscript=Path(ready['r_prefix'])/'bin/Rscript'
  run('independent-original',['/usr/bin/unshare','--net']+[rscript,'--vanilla',VALIDATION/'DirectOriginal.R',out/'direct-control.json'])
  run('compare-entire-original',[rscript,'--vanilla',VALIDATION/'Compare.R',ready['library'],out/'independent-original',out/'public/original',out/'COMPARISON.json','matrix.default'])
  comparison=load(out/'COMPARISON.json')
  if comparison['status']!='PASS_COMPLETE_ORIGINAL_SPOTLIGHT103_COMPARISON' or not comparison['returned']:raise ValueError('Installed original reference did not match completely')
  check(ready)
  if sources()!=before or {f.relative_to(ROOT).as_posix():pin(f) for f in source_names}!=extra:raise ValueError('Protected code changed during installed analysis')
  save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_INSTALLED_SPOTLIGHT103_REFERENCE_AND_COMPLETE_INDEPENDENT_ORIGINAL',
   network_disabled=True,full_original_returns_conditions_calls_and_rng_compared=True,
   original_source_or_dependency_algorithms_patched=False,commands=commands,new_family_credit=0,whole_goal_complete=False))
  print('PASS_ACTUAL_INSTALLED_SPOTLIGHT103_REFERENCE_AND_COMPLETE_INDEPENDENT_ORIGINAL',flush=True);return 0
 except BaseException as error:
  save(out/'FAILURE.json',dict(status='FAIL2_SPOTLIGHT103_INSTALLED_ANALYSIS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION));print('FAIL2_SPOTLIGHT103_INSTALLED_ANALYSIS_UNADMITTED',repr(error),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
