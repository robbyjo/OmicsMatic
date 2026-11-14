#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install original velociraptor and its original managed backend in a NEW private library."""
from pathlib import Path,PurePosixPath
import argparse,os,shutil,subprocess,sys,tarfile,tempfile,time,traceback,urllib.request
sys.dont_write_bytecode=True
from Runtime import HERE,PROFILE,ACTION,Unavailable,environment,load,pin,save,same,verify_source

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for name in ['r-base','python-base','out']:parser.add_argument('--'+name,required=True,type=Path)
 parser.add_argument('--source-archive',type=Path);parser.add_argument('--wheelhouse',type=Path)
 parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true')
 args=parser.parse_args();out=args.out.absolute();owned=False;calls=[];native=None
 try:
  protected=[HERE,args.r_base.resolve(),args.python_base.resolve()]
  protected.extend(path.resolve() for path in [args.source_archive,args.wheelhouse] if path is not None)
  if out!=out.resolve():raise ValueError('Canonical NEW private installation output required')
  if any(out.is_relative_to(path) or path.is_relative_to(out) for path in protected):raise ValueError('Output overlaps protected source/base/archive/wheelhouse')
  out.mkdir(exist_ok=False);owned=True
  if not args.allow_install:raise Unavailable('Installation is forbidden without --allow-install; dependent analysis skipped')
  if sys.platform!='linux':raise Unavailable('Use the declared original Linux/WSL R/Python runtime')
  if not args.allow_download and (args.source_archive is None or args.wheelhouse is None):raise Unavailable('Download forbidden: supply both exact original local --source-archive and complete --wheelhouse, or explicit --allow-download')
  for name in ['pixi.toml','pixi.lock']:same(args.r_base/name,pin(HERE/('reference-'+name)))
  rprefix=args.r_base.resolve()/'.pixi/envs/default';pybase=args.python_base.resolve();library=out/'library'
  ready=dict(r_prefix=str(rprefix),library=str(library),base_library=str(rprefix/'lib/R/library'),backend=str(library/'velociraptor/basilisk/env'),standalone_python=str(pybase/'bin/python3.12'))
  for exe in [rprefix/'bin/R',rprefix/'bin/Rscript',pybase/'bin/python3.12']:
   if not exe.is_file() or not os.access(exe,os.X_OK):raise Unavailable('Original prerequisite executable unavailable: '+str(exe))
  for name in ['owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache','pip-cache']:(out/name).mkdir()
  native=Path(tempfile.mkdtemp(prefix='ovri-',dir='/tmp'))
  save(out/'native-temporary.json',dict(path=str(native),retained_without_deletion=True,durable_copy='native-temporary'))
  env=environment(ready,out,temporary_directory=native)
  env.update(PIP_CACHE_DIR=str(out/'pip-cache'),PIP_CONSTRAINT=str(HERE/'reference-python-constraints.txt'),PIP_DEFAULT_TIMEOUT='30',PIP_RETRIES='2')
  if args.allow_download:env.pop('PIP_NO_INDEX',None);env['PIP_INDEX_URL']='https://pypi.org/simple'
  else:
   wheelhouse=args.wheelhouse.resolve()
   if not wheelhouse.is_dir():raise Unavailable('Complete permitted wheelhouse missing')
   wheels={p.name:pin(p) for p in wheelhouse.iterdir() if p.is_file() and not p.is_symlink()}
   if len(wheels)!=len(list(wheelhouse.iterdir())):raise ValueError('Wheelhouse must contain only regular distribution files')
   save(out/'supplied-wheelhouse-files.json',wheels);env['PIP_FIND_LINKS']=str(wheelhouse)
  source_pins={p.name:pin(p) for p in HERE.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',source_pins)
  profile=load(PROFILE);archive=out/'velociraptor_1.20.0.tar.gz'
  if args.source_archive:
   same(args.source_archive,profile['R_archive']);shutil.copyfile(args.source_archive,archive)
  else:
   request=urllib.request.Request(profile['original_R_source_url'],headers={'User-Agent':'OmicsMatic-original-velociraptor-setup/1'})
   with urllib.request.urlopen(request,timeout=30) as response:
    archive.write_bytes(response.read(profile['R_archive']['bytes']+1));save(out/'original-download.json',dict(url=response.url,headers=dict(response.headers)))
  same(archive,profile['R_archive']);source_parent=out/'source';source_parent.mkdir();source=source_parent/'velociraptor'
  with tarfile.open(archive) as stream:
   entries=stream.getmembers();names=[entry.name for entry in entries]
   if len(names)!=len(set(names)):raise ValueError('Duplicate original archive member')
   for entry in entries:
    name=PurePosixPath(entry.name)
    if not name.parts or name.parts[0]!='velociraptor' or name.is_absolute() or any(part in ['.','..'] for part in name.parts) or '\\' in entry.name:raise ValueError('Unsafe original source archive member')
    target=source_parent.joinpath(*name.parts)
    if entry.isdir():target.mkdir(parents=True,exist_ok=True)
    elif entry.isfile():
     target.parent.mkdir(parents=True,exist_ok=True)
     with stream.extractfile(entry) as file:target.write_bytes(file.read())
     target.chmod(entry.mode)
    else:raise ValueError('Unsupported original archive node')
  verify_source(source);library.mkdir()
  def run(label,command):
   command=list(map(str,command));start=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_ORIGINAL_INSTALL_CHILD_RUNNING',label,child.pid,round(time.monotonic()-start),flush=True)
   calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start));save(out/'PROCESSES.json',calls)
   if code:raise Unavailable('Original prerequisite installation unavailable: '+label+'; inspect complete installer logs, retain every available partial file and skip dependent analysis')
  run('original-R-CMD-INSTALL',[rprefix/'bin/R','CMD','INSTALL','--library='+str(library),source])
  verify_source(source)
  run('audit-new-original-runtime',[sys.executable,'-B',HERE/'adopt_runtime.py','--r-base',args.r_base.resolve(),'--r-library',library,'--r-source',source,'--python-base',pybase,'--out',out/'provider'])
  for name,wanted in source_pins.items():same(HERE/name,wanted)
  result=dict(status='INSTALLED_AND_AUDITED_ORIGINAL_PRIVATE_VELOCIRAPTOR120_RUNTIME_NOT_ANALYSIS',provider_directory=str(out/'provider'),provider=pin(out/'provider/READY.json'),original_R_source=profile['R_archive'],source_pins=source_pins,calls=calls,download_allowed=args.allow_download,original_algorithms_modified=False,analysis_executed=False,new_family_credit=0,whole_goal_complete=False,
   upstream_failed_installation_cleanup_policy='The original installer can remove its own failed NEW package/environment; raw logs, original archive/source and every available partial file remain retained. Historical prefixes are never removed.')
  save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text('# Original private runtime installed; no analysis qualified\n\n'+ACTION+'\n\n'+result['upstream_failed_installation_cleanup_policy']+'\n',encoding='utf-8',newline='\n')
  print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
  result=dict(status='SKIP3_ORIGINAL_INSTALLATION_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_INSTALLATION_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),calls=calls,result_admitted=False,upstream_may_remove_failed_NEW_package=True,historical_prefixes_deleted=False,required_action=ACTION)
  if owned:
   try:save(out/'FAILURE.json',result)
   except OSError as recording:code=2;print('FAIL2_INSTALLATION_STATUS_RECORDING_UNAVAILABLE',repr(recording),flush=True)
   try:(out/'WARNINGS_FAILURE.md').write_text('# '+result['status']+'\n\n'+repr(error)+'\n\n'+ACTION+'\n\nThe original installer may remove its own failed NEW package/environment; retain all raw logs, sources and every available partial file. No historical prefix is deleted.\n',encoding='utf-8',newline='\n')
   except OSError as recording:code=2;print('FAIL2_INSTALLATION_WARNING_RECORDING_UNAVAILABLE',repr(recording),flush=True)
  print(result['status'],repr(error),ACTION,flush=True);return code
 finally:
  if owned and native is not None:
   try:shutil.copytree(native,out/'native-temporary',symlinks=True)
   except BaseException as error:
    # The original native tree is deliberately retained. Never claim complete
    # temporary capture when its durable copy failed.
    print('WARNING_SETUP_NATIVE_TEMPORARY_COPY_INCOMPLETE',repr(error),'Original native root retained:',str(native),ACTION,flush=True)
    try:save(out/'NATIVE_TEMPORARY_COPY_WARNING.json',dict(status='WARNING_DURABLE_NATIVE_TEMPORARY_COPY_INCOMPLETE',cause=repr(error),native_root=str(native),native_root_deleted=False,required_action=ACTION))
    except OSError as recording:print('FAIL2_SETUP_TEMPORARY_WARNING_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
if __name__=='__main__':raise SystemExit(main())
