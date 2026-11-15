#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run original celldex1.14.0 reference retrieval with an owned cache and explicit networking."""
from pathlib import Path
import argparse,os,shutil,signal,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 p.add_argument('--runtime',type=Path,required=True);p.add_argument('--request',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
 p.add_argument('--reference-cache',type=Path,help='Complete read-only seed cache containing gypsum/, ExperimentHub/ and/or AnnotationHub/; copied into NEW output')
 p.add_argument('--allow-download',action='store_true',help='Permit original reference-data retrieval networking, never software installation')
 p.add_argument('--wall-seconds',type=int,default=3600);a=p.parse_args();out=a.out.absolute();owned=False;child=None;launched=False;exit_code=None;original_warnings=None
 def stop():
  if child is not None and child.poll() is None:
   os.killpg(child.pid,signal.SIGTERM)
   try:child.wait(timeout=5)
   except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
 try:
  protected=[HERE,a.runtime.resolve(),a.request.resolve()]+([a.reference_cache.resolve()] if a.reference_cache else [])
  out=check_output(out,protected);out.mkdir(parents=True,exist_ok=False);owned=True;(out/'WARNINGS.md').write_text(ACTION+'\n')
  if sys.platform!='linux':raise Unavailable('Original celldex requires permitted Linux/WSL; Windows execution was skipped without an R child')
  if a.wall_seconds<1:raise ValueError('Positive wall-seconds required')
  ready=load(a.runtime.resolve()/'READY.json');check(ready);before=sources()
  check_output(out,[ready['workspace'],*ready['source_paths'].values()])
  for n in ['home','tmp']:(out/n).mkdir()
  request_pin=pin(a.request);shutil.copyfile(a.request,out/'request.rds')
  if pin(out/'request.rds')!=request_pin:raise ValueError('Complete request copy differs')
  seed=None
  if a.reference_cache:
   seed=validate_cache(a.reference_cache);shutil.copytree(a.reference_cache.resolve(),out/'reference-cache',symlinks=True)
   if inventory(out/'reference-cache')!=seed:raise ValueError('Complete seed cache copy differs')
  else:(out/'reference-cache').mkdir()
  cache_before=inventory(out/'reference-cache');save(out/'complete-cache-before.json',cache_before)
  env=environment(ready,out);isolation=[]
  if not a.allow_download:
   isolation=['/usr/bin/unshare','--net']
   with (out/'network-isolation.stdout').open('xb') as so,(out/'network-isolation.stderr').open('xb') as se:
    probe=subprocess.run(isolation+['/usr/bin/true'],env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=30)
   save(out/'NETWORK-ISOLATION.json',dict(command=isolation+['/usr/bin/true'],exit_code=probe.returncode))
   if probe.returncode:raise Unavailable('Offline execution requires a permitted working network namespace; dependent retrieval skipped')
  c=dict(control(ready),request=str(out/'request.rds'),output=str(out/'original'),cache=str(out/'reference-cache'),audit=str(HERE/'Audit.R'),allow_download=a.allow_download)
  save(out/'control.json',c);save(out/'INPUTS.json',dict(request=request_pin,provider=pin(a.runtime/'READY.json'),adapter_sources=before,
   reference_download_allowed=a.allow_download,offline_network_namespace=not a.allow_download,seed_cache_supplied=a.reference_cache is not None))
  command=isolation+[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'Worker.R'),str(out/'control.json')]
  with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
   child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True);launched=True
   save(out/'LIVE.json',dict(pid=child.pid,command=command));began=time.monotonic()
   while child.poll() is None:
    remaining=a.wall_seconds-(time.monotonic()-began)
    if remaining<=0:raise TimeoutError('Original celldex process reached its declared time limit; no complete result admitted')
    try:child.wait(timeout=min(10,remaining))
    except subprocess.TimeoutExpired:
     if time.monotonic()-began>a.wall_seconds:raise TimeoutError('Original celldex process reached its declared time limit; no complete result admitted')
     print('SAME_ORIGINAL_CELLDEX_PROCESS_RUNNING',child.pid,flush=True)
   exit_code=child.returncode
  save(out/'PROCESS.json',dict(command=command,exit_code=exit_code))
  after=inventory(out/'reference-cache');save(out/'complete-cache-after.json',after)
  if pin(a.request)!=request_pin or pin(out/'request.rds')!=request_pin:raise ValueError('Protected or captured original request changed')
  if seed is not None and validate_cache(a.reference_cache)!=seed:raise ValueError('Protected original seed cache changed')
  check(ready)
  if sources()!=before:raise ValueError('Protected adapter sources changed during reference retrieval')
  status_path=out/'original/R-status.json'
  if (out/'original/PREREQUISITE-SKIPPED.json').is_file():raise Unavailable(load(out/'original/PREREQUISITE-SKIPPED.json')['required_action'])
  if not status_path.is_file():raise ValueError('Original process exited '+str(exit_code)+' before complete R status was recorded; later state is unknown')
  status=load(status_path)
  if 'warning_messages' not in status:raise ValueError('Complete original R warning record missing; inspect original conditions and raw consoles')
  original_warnings=status['warning_messages']
  for message in original_warnings:print('ORIGINAL_CELLDEX_R_WARNING:',message,flush=True)
  if original_warnings:(out/'WARNINGS.md').write_text('ORIGINAL R WARNINGS ('+str(len(original_warnings))+')\n\n'+'\n\n'.join(original_warnings)+'\n\n'+ACTION+'\nNative/raw diagnostic totals are not inferred from R warning counts; inspect both complete raw consoles.\n')
  original_error=status.get('error') or {}
  if status.get('unavailable_reference_transport'):raise Unavailable('Original reference transport unavailable: '+str(original_error.get('message'))+'; offline execution needs the complete exact cache, or explicitly permit the required download')
  if exit_code or not status['original_function_returned']:raise ValueError('Original celldex error: '+str(original_error.get('message'))+'; no result admitted. Read the complete original conditions and consoles')
  for n in ['complete-original-return.rds','complete-original-conditions.rds','complete-original-rng.rds','complete-original-rng-before.rds','complete-caller-context.rds','complete-precall-source-audit.rds','complete-postcall-source-audit.rds']:
   if not (out/'original'/n).is_file():raise ValueError('Complete original record missing: '+n)
  files={f.relative_to(out).as_posix():pin(f) for f in out.rglob('*') if f.is_file()}
  save(out/'COMPLETE-FILES.json',files)
  save(out/'COMPLETED.json',dict(status='PASS_RECORDED_ORIGINAL_CELLDEX114_REFERENCE_OPERATION',operation=status['operation'],
   complete_return_conditions_and_rng_preserved=True,reference_download_allowed=a.allow_download,offline_network_namespace=not a.allow_download,
   complete_reference_cache_preserved=True,manifest=pin(out/'COMPLETE-FILES.json'),source_algorithms_patched=False,author_reference_snapshot_established=False,new_family_credit=0))
  print('PASS_RECORDED_ORIGINAL_CELLDEX114_REFERENCE_OPERATION',status['operation'],flush=True);return 0
 except BaseException as error:
  stop();code=3 if isinstance(error,Unavailable) or (not launched and isinstance(error,OSError) and not isinstance(error,FileExistsError)) else 2
  label='SKIP3_CELLDEX114_REFERENCE_UNAVAILABLE' if code==3 else 'FAIL2_CELLDEX114_REFERENCE_UNADMITTED'
  print(label,repr(error),ACTION,flush=True)
  if owned:
   try:
    save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),child_launched=launched,
     original_process_exit_code=child.poll() if child is not None else exit_code,original_R_warning_messages=original_warnings,complete_result_admitted=False,required_action=ACTION))
    (out/'WARNINGS.md').write_text(label+'\n'+repr(error)+'\n'+ACTION+'\n'+('Original R warning state unavailable; inspect any retained conditions and raw consoles.\n' if original_warnings is None else 'Original R warnings ('+str(len(original_warnings))+'):\n'+'\n\n'.join(original_warnings)+'\n'))
   except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True);code=2
  return code
if __name__=='__main__':raise SystemExit(main())
