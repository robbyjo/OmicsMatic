#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install the original R-only plotting frontend over the exact locked plot dependency base."""
from pathlib import Path,PurePosixPath
import argparse,os,shutil,subprocess,sys,tarfile,tempfile,time,traceback,urllib.request
sys.dont_write_bytecode=True
from Runtime import HERE,Unavailable,inventory,load,pin,save,same,verify_source
from PlotRuntime import PLOT_ACTION,environment
from run import capture_native

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=PLOT_ACTION);parser.add_argument('--r-base',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--source-archive',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args()
 out=args.out.absolute();owned=False;native=None;calls=[];code=2;result=None
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW original plot-library output required')
  for protected in [HERE,args.r_base.resolve(),*([args.source_archive.resolve()] if args.source_archive is not None else [])]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/base/archive')
  out.mkdir(exist_ok=False);owned=True
  if not args.allow_install:raise Unavailable('Installation forbidden without --allow-install; skip dependent figure analysis')
  if sys.platform!='linux':raise Unavailable('Use the declared original Linux/WSL plot dependency profile')
  if args.source_archive is None and not args.allow_download:raise Unavailable('Exact local original source or explicit --allow-download required')
  for name in ['pixi.toml','pixi.lock']:same(args.r_base/name,pin(HERE/('reference-plots-'+name)))
  prefix=args.r_base.resolve()/'.pixi/envs/default';rscript=prefix/'bin/Rscript'
  if not rscript.is_file() or not os.access(rscript,os.X_OK):raise Unavailable('Original Rscript unavailable')
  sources=['install_plots.py','PlotRuntime.py','Runtime.py','run.py','FinalRecords.py','RValues.py','AuditPlots.R','CheckPlots.R','source-profile.json','reference-plots-pixi.toml','reference-plots-pixi.lock']
  source_pins={name:pin(HERE/name) for name in sources};save(out/'SOURCE-PINS.json',source_pins)
  for name in ['owned-home','tmp','unused-external-cache','library','original-source']:(out/name).mkdir()
  native=Path(tempfile.mkdtemp(prefix='vpri-',dir='/tmp'));save(out/'native-temporary.json',dict(path=str(native),original_native_root_deleted=False))
  profile=load(HERE/'source-profile.json');archive=out/'velociraptor_1.20.0.tar.gz'
  if args.source_archive:same(args.source_archive,profile['R_archive']);shutil.copyfile(args.source_archive,archive)
  else:
   with urllib.request.urlopen(urllib.request.Request(profile['original_R_source_url'],headers={'User-Agent':'OmicsMatic-original-R-only-figures/1'}),timeout=30) as response:archive.write_bytes(response.read(profile['R_archive']['bytes']+1));save(out/'ORIGINAL-DOWNLOAD.json',dict(url=response.url,headers=dict(response.headers)))
  same(archive,profile['R_archive'])
  with tarfile.open(archive) as tar:
   entries=tar.getmembers();names=[entry.name for entry in entries]
   if len(names)!=len(set(names)):raise ValueError('Duplicate original source archive member')
   for entry in entries:
    name=PurePosixPath(entry.name)
    if name.is_absolute() or not name.parts or name.parts[0]!='velociraptor' or any(p in ['.','..'] for p in name.parts) or '\\' in entry.name:raise ValueError('Unsafe original source member')
    relative=name.parts[1:];target=out/'original-source';target=target.joinpath(*relative)
    if entry.isdir():target.mkdir(parents=True,exist_ok=True)
    elif entry.isfile():
     target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(entry).read());target.chmod(entry.mode)
    else:raise ValueError('Unsupported original archive node')
  verify_source(out/'original-source')
  ready=dict(r_prefix=str(prefix),rscript=str(rscript),base_library=str(prefix/'lib/R/library'),library=str(out/'library'),source_root=str(out/'original-source'),runtime_roots=dict(R_prefix=str(prefix),private_R_library=str(out/'library')))
  env=environment(ready,out,temporary_directory=native)
  def execute(label,command,child_env):
   command=list(map(str,command));started=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=child_env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:status=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_R_ONLY_PLOT_INSTALL_CHILD_RUNNING',label,child.pid,round(time.monotonic()-started),flush=True)
   calls.append(dict(label=label,command=command,exit_code=status,elapsed_seconds=time.monotonic()-started));save(out/'PROCESSES.json',calls)
   if status:raise Unavailable('Original plot prerequisite stage unavailable: '+label+'; inspect complete raw logs and skip dependent figures')
  # Supported original manager setting: no install-time Python backend. During
  # later R-only use SYSTEM_DIR=1 refuses missing backends instead of lazy setup.
  install_env=dict(env,BASILISK_USE_SYSTEM_DIR='0')
  execute('original-R-CMD-INSTALL-no-fitting-backend',[prefix/'bin/R','CMD','INSTALL','--library='+ready['library'],archive],install_env)
  if (out/'library/velociraptor/basilisk/env').exists():raise ValueError('R-only setup unexpectedly created a fitting backend')
  execute('original-R-only-plot-namespace',[rscript,'--vanilla',HERE/'CheckPlots.R',HERE/'AuditPlots.R',ready['library'],ready['source_root'],out],env)
  first=inventory(ready['runtime_roots']);second=inventory(ready['runtime_roots'])
  if first!=second:save(out/'FIRST-INVENTORY.json',first);save(out/'SECOND-INVENTORY.json',second);raise ValueError('Plot dependency prefix changed during capture')
  verify_source(out/'original-source')
  for name,wanted in source_pins.items():same(HERE/name,wanted)
  capture_native(native,out)
  ready.update(status='READY_ORIGINAL_VELOCIRAPTOR120_R_ONLY_FIGURE_PROFILE_NOT_STUDY_QUALIFICATION',source_profile=pin(HERE/'source-profile.json'),preparation_sources=source_pins,runtime_inventory=first,readiness=pin(out/'PLOT-READINESS.json'),provides_Python_fitting_backend=False,original_source_functions=11,registered_original_methods=7,plot_packages=dict(ggplot2='4.0.4',patchwork='1.3.2',metR='0.19.0'),analysis_executed=False,new_family_credit=0,whole_goal_complete=False)
  save(out/'READY.json',ready);code=0;result=dict(status=ready['status'],calls=calls,required_action=PLOT_ACTION);save(out/'COMPLETED.json',result)
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;result=dict(status='SKIP3_ORIGINAL_R_ONLY_PLOT_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_R_ONLY_PLOT_SETUP_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),calls=calls,result_admitted=False,required_action=PLOT_ACTION)
  if owned:
   try:save(out/'FAILURE.json',result)
   except OSError as recording:code=2;print('FAIL2_PLOT_SETUP_STATUS_RECORDING_UNAVAILABLE',repr(recording),PLOT_ACTION,flush=True)
  if owned and native is not None and not (out/'native-temporary').exists():
   try:capture_native(native,out)
   except BaseException as recording:print('WARNING_PLOT_SETUP_NATIVE_CAPTURE_INCOMPLETE',repr(recording),'Original root retained:',str(native),PLOT_ACTION,flush=True)
 if owned:
  try:(out/'WARNINGS.md').write_text('# '+result['status']+'\n\n'+result.get('cause','')+'\n\n'+PLOT_ACTION+'\n\nThe upstream installer can remove its own failed NEW package. Preserve all archives, raw logs, available partial files and historical prefixes.\n',encoding='utf-8',newline='\n')
  except OSError as recording:code=2;print('FAIL2_PLOT_SETUP_WARNING_RECORDING_UNAVAILABLE',repr(recording),PLOT_ACTION,flush=True)
 print(result['status'],result.get('cause',''),PLOT_ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
