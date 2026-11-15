#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete independent original SPOTlight calls versus the production worker, offline."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,os,signal,subprocess,sys,traceback
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
from Runtime import *
CASES=['matrix.default','matrix.unscaled','matrix.std','matrix.minprop.high','matrix.unknown.argument','matrix.one.iteration','matrix.penalized',
 'groups.factor','markers.top','markers.S4','markers.literal.names','genes.reordered','matrix.sparse','SCE.explicit','SCE.labels.warning',
 'SCE.warning.error','matrix.delayed','train.full','train.deconvolve','deconvolve.fixed.model','deconvolve.zero.spot',
 'error.few.features','error.missing.markers','error.model','error.minprop','error.penalty','error.groups','error.missing.assay','error.mixture.features']
ERRORS={n for n in CASES if n.startswith('error.')}|{'SCE.warning.error'}

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for n in ['runtime','out']:p.add_argument('--'+n,type=Path,required=True)
 p.add_argument('--jobs',type=int,default=2);a=p.parse_args();out=a.out.absolute();owned=False
 try:
  ready=load(a.runtime/'READY.json');out=check_output(out,[VALIDATION,MODULE,ready['workspace'],a.runtime]);out.mkdir(parents=True,exist_ok=False);owned=True
  if a.jobs not in [1,2,3]:raise ValueError('Use one to three independent controls')
  check(ready);before=sources()
  control_sources=[MODULE/'MakeRequests.R',*[VALIDATION/n for n in ['DirectOriginal.R','Compare.R','controls.py']]]
  pins={p.relative_to(ROOT).as_posix():pin(p) for p in control_sources};save(out/'SOURCES.json',dict(before,**pins))
  for n in ['home','tmp','cases']:(out/n).mkdir()
  rscript=Path(ready['r_prefix'])/'bin/Rscript'
  def run(label,command,folder,expected=(0,)):
   command=list(map(str,command))
   with (folder/(label+'.stdout')).open('xb') as so,(folder/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=folder,env=environment(ready,folder),stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
    save(folder/(label+'-LIVE.json'),dict(pid=child.pid,command=command))
    try:code=child.wait(timeout=900)
    except BaseException:
     if child.poll() is None:
      os.killpg(child.pid,signal.SIGTERM)
      try:child.wait(timeout=5)
      except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
     raise
   save(folder/(label+'-PROCESS.json'),dict(exit_code=code,command=command))
   if code not in expected:raise ValueError('Complete original comparison step failed: '+str(folder.name)+'/'+label)
   return code
  run('make-requests',['/usr/bin/unshare','--net',rscript,'--vanilla',MODULE/'MakeRequests.R',out/'requests'],out)
  def one(name):
   folder=out/'cases'/name;folder.mkdir()
   for n in ['home','tmp']:(folder/n).mkdir()
   original=dict(control(ready),edition='1.0.3',request=str(out/'requests'/(name+'.rds')),output=str(folder/'direct'))
   save(folder/'direct-control.json',original)
   run('direct',['/usr/bin/unshare','--net',rscript,'--vanilla',VALIDATION/'DirectOriginal.R',folder/'direct-control.json'],folder,(0,2))
   (folder/'worker').mkdir();adapted=dict(original,output=str(folder/'worker'),audit=str(MODULE/'Audit.R'));save(folder/'worker-control.json',adapted)
   run('worker',['/usr/bin/unshare','--net',rscript,'--vanilla',MODULE/'Worker.R',folder/'worker-control.json'],folder,(0,2))
   code=run('compare',[rscript,'--vanilla',VALIDATION/'Compare.R',ready['library'],folder/'direct',folder/'worker',folder/'COMPARISON.json',name],folder,(0,2))
   print('PAIRED_ORIGINAL_SPOTLIGHT103_CONTROL',name,code,flush=True);return name,load(folder/'COMPARISON.json')
  with ThreadPoolExecutor(max_workers=a.jobs) as pool:results=dict(pool.map(one,CASES))
  save(out/'COMPARISONS.json',results);check(ready)
  if sources()!=before or {p.relative_to(ROOT).as_posix():pin(p) for p in control_sources}!=pins:raise ValueError('Qualified production/control source changed')
  if any(r['status']!='PASS_COMPLETE_ORIGINAL_SPOTLIGHT103_COMPARISON' for r in results.values()):raise ValueError('Complete original object/condition/RNG difference; retain and inspect')
  if {n for n,r in results.items() if not r['returned']}!=ERRORS:raise ValueError('Intended positive cases did not all return, or an intended original error differed')
  # Compare the complete original results across arguments the inspected source accepts but does not apply.
  semantic=[rscript,'--vanilla','-e',
   'a<-commandArgs(TRUE);base<-readRDS(file.path(a[1],"matrix.default/direct/return.rds"));for(n in c("matrix.std","matrix.minprop.high","matrix.unknown.argument"))stopifnot(identical(base,readRDS(file.path(a[1],n,"direct/return.rds"))));z<-readRDS(file.path(a[1],"deconvolve.zero.spot/direct/return.rds"));stopifnot(all(is.nan(z$mat[4,])));cat("PASS_ORIGINAL_UNAPPLIED_OPTIONS_AND_ZERO_SPOT_NAN\\n")',out/'cases']
  run('original-semantics',semantic,out)
  result=dict(status='PASS_COMPLETE_ORIGINAL_SPOTLIGHT103_CONTROLS_NOT_PAPER_REPRODUCTION',controls=len(results),complete_returns=sum(r['returned'] for r in results.values()),original_failures_without_result=len(ERRORS),
   entire_original_returns_and_rng_compared=True,all_conditions_calls_and_cpp_native_origin_compared=True,
   full_process_specific_cpp_backtraces_retained_but_not_required_equal=True,network_disabled_for_all_original_calls=True,
   original_unapplied_model_minprop_and_extra_argument_confirmed=True,original_zero_spot_NaN_retained=True,
   source_or_dependency_algorithms_patched=False,original_NMF_convergence_certified=False,biological_accuracy_established=False,new_family_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except BaseException as error:
  print('FAIL2_SPOTLIGHT103_CONTROL_QUALIFICATION_UNADMITTED',repr(error),ACTION,flush=True)
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_SPOTLIGHT103_CONTROL_QUALIFICATION_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
  return 2
if __name__=='__main__':raise SystemExit(main())
