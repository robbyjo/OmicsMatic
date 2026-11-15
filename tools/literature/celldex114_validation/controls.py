#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent full original reference calls versus celldex worker, with network disabled."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'celldex114';sys.path.insert(0,str(MODULE))
from Runtime import *
CASES=['immgen.default','mouse.default','immgen.lazy','mouse.realized','immgen.metadata','mouse.metadata','immgen.legacy','mouse.legacy',
 'immgen.nonmissing.ontology','mouse.no.ontology','mouse.ensembl','invalid.ontology','unsupported.argument','missing.reference.version','invalid.local.subpath','pure.text.query']
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['runtime','reference-cache','out']:p.add_argument('--'+n,type=Path,required=True)
 p.add_argument('--jobs',type=int,default=2);p.add_argument('--hub-local',action='store_true',help='Record original supported LOCAL=TRUE caller options for both Hubs');a=p.parse_args();out=a.out.absolute();owned=False
 try:
  ready=load(a.runtime/'READY.json');out=check_output(out,[VALIDATION,MODULE,a.reference_cache,ready['workspace'],a.runtime]);out.mkdir(parents=True,exist_ok=False);owned=True
  if a.jobs not in [1,2,3]:raise ValueError('Use one to three independent controls')
  check(ready);before=sources();seed=validate_cache(a.reference_cache)
  control_sources=[MODULE/'MakeRequests.R',*[VALIDATION/n for n in ['DirectReference.R','Compare.R','controls.py']]]
  control_pins={p.relative_to(ROOT).as_posix():pin(p) for p in control_sources};save(out/'SOURCES.json',dict(before,**control_pins))
  for n in ['home','tmp','cases']:(out/n).mkdir()
  rscript=Path(ready['r_prefix'])/'bin/Rscript'
  def run(label,command,folder,expected=(0,)):
   with (folder/(label+'.stdout')).open('xb') as so,(folder/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(list(map(str,command)),cwd=folder,env=environment(ready,folder),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(folder/(label+'-LIVE.json'),dict(pid=child.pid,command=list(map(str,command))))
    try:code=child.wait(timeout=1800)
    except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=30);raise
   save(folder/(label+'-PROCESS.json'),dict(exit_code=code,command=list(map(str,command))))
   if code not in expected:raise ValueError('Complete original comparison step failed: '+str(folder.name)+'/'+label)
   return code
  run('make-requests',[rscript,'--vanilla',MODULE/'MakeRequests.R',out/'requests']+(['explicit-local'] if a.hub_local else []),out)
  def one(name):
   folder=out/'cases'/name;folder.mkdir()
   for n in ['home','tmp']:(folder/n).mkdir()
   shutil.copytree(a.reference_cache,folder/'cache',symlinks=True)
   original=dict(control(ready),request=str(out/'requests'/(name+'.rds')),cache=str(folder/'cache'),output=str(folder/'direct'))
   save(folder/'direct-control.json',original)
   run('direct',['/usr/bin/unshare','--net',rscript,'--vanilla',VALIDATION/'DirectReference.R',folder/'direct-control.json'],folder,(0,2))
   adapted=dict(original,output=str(folder/'worker'),audit=str(MODULE/'Audit.R'),allow_download=False);save(folder/'worker-control.json',adapted)
   run('worker',['/usr/bin/unshare','--net',rscript,'--vanilla',MODULE/'Worker.R',folder/'worker-control.json'],folder,(0,2))
   code=run('compare',[rscript,'--vanilla',VALIDATION/'Compare.R',ready['library'],folder/'direct',folder/'worker',folder/'COMPARISON.json',name],folder,(0,2))
   print('PAIRED_ORIGINAL_CELLDEX_CONTROL',name,code,flush=True);return name,load(folder/'COMPARISON.json')
  with ThreadPoolExecutor(max_workers=a.jobs) as pool:results=dict(pool.map(one,CASES))
  save(out/'COMPARISONS.json',results)
  check(ready)
  if sources()!=before or validate_cache(a.reference_cache)!=seed:raise ValueError('Protected sources or original seed cache changed')
  if {p.relative_to(ROOT).as_posix():pin(p) for p in control_sources}!=control_pins:raise ValueError('Independent fixture/direct/comparison driver code changed during qualification')
  if any(r['status']!='PASS_COMPLETE_ORIGINAL_CELLDEX_REFERENCE_COMPARISON' for r in results.values()):raise ValueError('Complete original objects/conditions/RNG differ; preserve all differences and inspect')
  if a.hub_local:
   expected_errors={'invalid.ontology','unsupported.argument','missing.reference.version','invalid.local.subpath'}
   if {n for n,r in results.items() if not r['returned']}!=expected_errors:raise ValueError('Intended positive reference cases did not all return; matching original errors is not successful retrieval')
  save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_CELLDEX114_REAL_REFERENCE_CONTROLS_NOT_PAPER_REPRODUCTION',controls=len(results),
   complete_returns=sum(r['returned'] for r in results.values()),original_failures_without_result=sum(not r['returned'] for r in results.values()),
   entire_original_objects_conditions_and_rng_compared=True,entire_lazy_assay_payloads_compared=True,network_disabled_for_all_original_calls=True,
   explicit_caller_LOCAL_Hub_options=a.hub_local,intended_positive_cases_required_and_verified=a.hub_local,
   source_or_dependency_algorithms_patched=False,author_reference_snapshot_established=False,new_family_credit=0,whole_goal_complete=False))
  print('PASS_COMPLETE_ORIGINAL_CELLDEX114_REAL_REFERENCE_CONTROLS_NOT_PAPER_REPRODUCTION',flush=True);return 0
 except BaseException as error:
  print('FAIL2_CELLDEX114_CONTROL_QUALIFICATION_UNADMITTED',repr(error),ACTION,flush=True)
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_CELLDEX114_CONTROL_QUALIFICATION_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
  return 2
if __name__=='__main__':raise SystemExit(main())
