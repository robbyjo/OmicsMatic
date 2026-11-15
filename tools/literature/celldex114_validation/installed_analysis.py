#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual installed-runtime reference retrieval and independent complete original comparison."""
from pathlib import Path
import argparse,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'celldex114';sys.path.insert(0,str(MODULE))
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['runtime','request','out']:p.add_argument('--'+n,type=Path,required=True)
 p.add_argument('--reference-cache',type=Path);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
 try:
  ready=load(a.runtime/'READY.json');check(ready);before=sources();seed=validate_cache(a.reference_cache) if a.reference_cache else None
  for n in ['home','tmp']:(out/n).mkdir()
  source_names=[VALIDATION/'DirectReference.R',VALIDATION/'Compare.R',Path(__file__)];extra={f.relative_to(ROOT).as_posix():pin(f) for f in source_names}
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
  if a.allow_download:command+=['--allow-download']
  if a.reference_cache:command+=['--reference-cache',a.reference_cache]
  run('actual-public',command)
  if a.reference_cache:shutil.copytree(a.reference_cache,out/'independent-cache',symlinks=True)
  else:(out/'independent-cache').mkdir()
  c=dict(control(ready),request=str(a.request.resolve()),cache=str(out/'independent-cache'),output=str(out/'independent-original'))
  save(out/'direct-control.json',c);rscript=Path(ready['r_prefix'])/'bin/Rscript'
  run('independent-original',([] if a.allow_download else ['/usr/bin/unshare','--net'])+[rscript,'--vanilla',VALIDATION/'DirectReference.R',out/'direct-control.json'])
  run('compare-entire-original',[rscript,'--vanilla',VALIDATION/'Compare.R',ready['library'],out/'independent-original',out/'public/original',out/'COMPARISON.json','mouse.default'])
  comparison=load(out/'COMPARISON.json')
  if comparison['status']!='PASS_COMPLETE_ORIGINAL_CELLDEX_REFERENCE_COMPARISON' or not comparison['returned']:raise ValueError('Installed original reference did not match completely')
  if a.allow_download and a.reference_cache is None:
   if load(out/'public/complete-cache-before.json') or not load(out/'public/complete-cache-after.json'):raise ValueError('Fresh-download branch was not actually exercised')
  check(ready)
  if sources()!=before or {f.relative_to(ROOT).as_posix():pin(f) for f in source_names}!=extra:raise ValueError('Protected code changed during installed analysis')
  if seed is not None and validate_cache(a.reference_cache)!=seed:raise ValueError('Original protected reference cache changed')
  save(out/'COMPLETED.json',dict(status='PASS_ACTUAL_INSTALLED_CELLDEX114_REFERENCE_AND_COMPLETE_INDEPENDENT_ORIGINAL',
   reference_download_allowed=a.allow_download,seed_cache_supplied=a.reference_cache is not None,full_original_returns_conditions_rng_and_all_assays_compared=True,
   original_source_or_dependency_algorithms_patched=False,commands=commands,new_family_credit=0,whole_goal_complete=False))
  print('PASS_ACTUAL_INSTALLED_CELLDEX114_REFERENCE_AND_COMPLETE_INDEPENDENT_ORIGINAL',flush=True);return 0
 except BaseException as error:
  save(out/'FAILURE.json',dict(status='FAIL2_CELLDEX114_INSTALLED_ANALYSIS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION));print('FAIL2_CELLDEX114_INSTALLED_ANALYSIS_UNADMITTED',repr(error),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
