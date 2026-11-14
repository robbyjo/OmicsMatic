#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install locked dependencies and unchanged original source in a NEW private directory."""
from pathlib import Path,PurePosixPath
import argparse,os,shutil,subprocess,sys,tarfile,time,traceback,urllib.request
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,load,pin,save,source_tree

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 p.add_argument('--manager',required=True,type=Path);p.add_argument('--out',required=True,type=Path)
 p.add_argument('--source-archive',type=Path);p.add_argument('--cache-dir',type=Path)
 p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true')
 a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  protected=[HERE,a.manager.resolve()]+([a.source_archive.resolve()] if a.source_archive else [])+([a.cache_dir.resolve()] if a.cache_dir else [])
  if out!=out.resolve() or any(out.is_relative_to(x) or x.is_relative_to(out) for x in protected):raise ValueError('Use a NEW canonical installation output separate from sources/manager/cache')
  out.mkdir(exist_ok=False);owned=True
  if not a.allow_install:raise Unavailable('Installation is forbidden without --allow-install; dependent analysis skipped')
  if sys.platform!='linux':raise Unavailable('Use Linux/WSL for the selected original dependency profile')
  if not a.manager.is_file() or not os.access(a.manager,os.X_OK):raise Unavailable('Supply a permitted executable Pixi manager with --manager')
  if not a.allow_download and a.source_archive is None:raise Unavailable('Downloads forbidden: supply exact local --source-archive and a complete permitted --cache-dir for offline installation, or adopt a preinstalled exact runtime')
  for n in ['runtime','source','home','tmp','pixi-home']:(out/n).mkdir()
  cache=a.cache_dir.resolve() if a.cache_dir else out/'pixi-cache';cache.mkdir(exist_ok=True)
  profile=load(HERE/'source-profile.json');archive=out/'DoubletFinder-original.tar.gz'
  if a.source_archive:shutil.copyfile(a.source_archive.resolve(),archive)
  else:
   request=urllib.request.Request(profile['url'],headers={'User-Agent':'OmicsMatic-original-DoubletFinder206-setup/1'})
   with urllib.request.urlopen(request,timeout=30) as response:
    archive.write_bytes(response.read(profile['archive']['bytes']+1));save(out/'DOWNLOAD.json',dict(url=response.url,headers=dict(response.headers)))
  if pin(archive)!=profile['archive']:raise ValueError('Original pinned source archive differs; no package installation admitted')
  with tarfile.open(archive) as stream:
   names=stream.getnames()
   if len(names)!=len(set(names)):raise ValueError('Duplicate original archive member')
   for member in stream.getmembers():
    name=PurePosixPath(member.name)
    if name.is_absolute() or any(x in ['.','..'] for x in name.parts) or '\\' in member.name:raise ValueError('Unsafe original source member')
    if len(name.parts)==1:
     if not member.isdir():raise ValueError('Original archive top level is not a directory')
     continue
    relative=PurePosixPath(*name.parts[1:]);target=out/'source'/relative
    if member.isdir():target.mkdir(parents=True,exist_ok=True)
    elif member.isfile():
     target.parent.mkdir(parents=True,exist_ok=True)
     with stream.extractfile(member) as f:target.write_bytes(f.read())
     target.chmod(member.mode)
    else:raise ValueError('Unsupported original source member')
  source_tree(out/'source')
  for name in ['pixi.toml','pixi.lock']:shutil.copyfile(HERE/('reference-'+name),out/'runtime'/name)
  env=dict(os.environ,PIXI_HOME=str(out/'pixi-home'),PIXI_CACHE_DIR=str(cache),PIXI_NO_PATH_UPDATE='1',HOME=str(out/'home'),TMPDIR=str(out/'tmp'),R_LIBS_USER=str(out/'runtime/library'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  env.pop('R_HOME',None)
  def run(label,command):
   start=time.monotonic();command=list(map(str,command))
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_ORIGINAL_PRIVATE_INSTALL_CHILD_RUNNING',label,child.pid,flush=True)
   calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start));save(out/'PROCESSES.json',calls)
   if code:raise Unavailable('Original permitted installation failed: '+label+'; inspect complete logs and skip dependent analysis')
  save(out/'INSTALLATION-INPUTS.json',dict(manager=pin(a.manager),source_archive=pin(archive),manifest=pin(out/'runtime/pixi.toml'),lock=pin(out/'runtime/pixi.lock'),installation_allowed=a.allow_install,download_allowed=a.allow_download,cache=str(cache)))
  run('locked-dependency-install',[a.manager.resolve(),'install','--locked','--manifest-path',out/'runtime/pixi.toml']+([] if a.allow_download else ['--offline']))
  (out/'runtime/library').mkdir()
  run('original-R-CMD-INSTALL',[out/'runtime/.pixi/envs/default/bin/R','CMD','INSTALL','--library='+str(out/'runtime/library'),out/'source'])
  source_tree(out/'source')
  run('initialize-original-graphics-font-cache',[out/'runtime/.pixi/envs/default/bin/Rscript','--vanilla',HERE/'InitFonts.R',out/'font-readiness'])
  run('audit-original-runtime',[sys.executable,'-B',HERE/'adopt_runtime.py','--prefix',out/'runtime','--source',out/'source','--out',out/'provider'])
  save(out/'COMPLETED.json',dict(status='PASS_INSTALLED_AND_AUDITED_ORIGINAL_PRIVATE_RUNTIME_NOT_ANALYSIS',provider=str(out/'provider'),provider_identity=pin(out/'provider/READY.json'),calls=calls,download_allowed=a.allow_download,analysis_executed=False,new_family_credit=0,whole_goal_complete=False))
  (out/'WARNINGS.md').write_text('# Original private runtime installed; analysis still requires qualification\n\n'+ACTION+'\n',encoding='utf-8');print('PASS_INSTALLED_AND_AUDITED_ORIGINAL_PRIVATE_RUNTIME_NOT_ANALYSIS',flush=True);return 0
 except BaseException as e:
  code=3 if isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2;status='SKIP3_ORIGINAL_INSTALLATION_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_INSTALLATION_UNADMITTED'
  if owned:
   try:
    save(out/'FAILURE.json',dict(status=status,cause=repr(e),traceback=traceback.format_exc(),calls=calls,result_admitted=False,required_action=ACTION))
    (out/'WARNINGS_FAILURE.md').write_text('# '+status+'\n\n'+repr(e)+'\n\n'+ACTION+'\n\nThe original installer may clean its own failed NEW package. Retain raw logs and every available partial file; no historical prefix is removed.\n',encoding='utf-8')
   except BaseException as recording:code=2;status='FAIL2_INSTALLATION_RECORDING_UNAVAILABLE';print(status,repr(recording),flush=True)
  print(status,repr(e),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
