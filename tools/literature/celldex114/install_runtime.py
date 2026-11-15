#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install locked comparison dependencies and unchanged original celldex1.14.0 sources."""
from pathlib import Path,PurePosixPath
import argparse,os,shutil,signal,subprocess,sys,tarfile,time,traceback,urllib.request
sys.dont_write_bytecode=True
from Runtime import *
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
 for name in ['manager','out']:p.add_argument('--'+name,type=Path,required=True)
 for name in ['source-cache','cache-dir']:p.add_argument('--'+name,type=Path)
 p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--wall-seconds',type=int,default=7200)
 a=p.parse_args();out=a.out.absolute();owned=False;child=None;calls=[];began=time.monotonic()
 def run(label,command,env):
  nonlocal child
  if time.monotonic()-began>=a.wall_seconds:raise Unavailable('Declared installation time limit reached before launching '+label)
  command=list(map(str,command))
  with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
   child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
   save(out/(label+'-LIVE.json'),dict(pid=child.pid,command=command))
   while child.poll() is None:
    remaining=a.wall_seconds-(time.monotonic()-began)
    if remaining<=0:raise Unavailable('Declared installation time limit reached; inspect logs and retry NEW paths')
    try:child.wait(timeout=min(10,remaining))
    except subprocess.TimeoutExpired:
     if time.monotonic()-began>a.wall_seconds:raise Unavailable('Declared installation time limit reached; inspect logs and retry NEW paths')
     print('SAME_CELLDEX_INSTALL_CHILD_RUNNING',label,child.pid,flush=True)
  item=dict(command=command,exit_code=child.returncode);calls.append(item);save(out/(label+'-PROCESS.json'),item)
  if child.returncode:raise Unavailable('Exact required installation step failed: '+label+'; dependent analysis skipped')
 try:
  protected=[HERE,a.manager.resolve()]+[x.resolve() for x in [a.cache_dir,a.source_cache] if x is not None]
  out=check_output(out,protected);out.mkdir(parents=True,exist_ok=False);owned=True;(out/'WARNINGS.md').write_text(ACTION+'\n')
  if not a.allow_install:raise Unavailable('Installation requires explicit --allow-install')
  if sys.platform!='linux':raise Unavailable('Original celldex runtime requires permitted Linux/WSL')
  if a.wall_seconds<1:raise ValueError('Positive installation time limit required')
  if not a.allow_download and (a.cache_dir is None or a.source_cache is None):raise Unavailable('Offline installation needs complete exact --cache-dir and --source-cache')
  if not a.manager.is_file() or not os.access(a.manager,os.X_OK):raise Unavailable('Supply an existing permitted executable Pixi')
  provider=out.parent/(out.name+'-provider')
  if provider.exists():raise FileExistsError('Provider exists; preserve it and use NEW installation paths')
  before=sources()
  for n in ['runtime','home','tmp','library','source-original','build-original','downloads','pixi-home']:(out/n).mkdir()
  for source,target in [('dependencies.toml','pixi.toml'),('dependencies.lock','pixi.lock')]:shutil.copyfile(HERE/source,out/'runtime'/target)
  archives={};originals={}
  for name,entry in profile()['packages'].items():
   target=out/'downloads'/entry['archive_filename'];cache=a.source_cache/entry['archive_filename'] if a.source_cache else None
   if cache is not None and cache.is_file():shutil.copyfile(cache,target);origin=dict(source='permitted-exact-cache',path=str(cache))
   else:
    if not a.allow_download:raise Unavailable('Exact source archive missing and downloads forbidden: '+entry['archive_filename'])
    request=urllib.request.Request(entry['url'],headers={'User-Agent':'OmicsMatic-original-celldex-installer'})
    with urllib.request.urlopen(request,timeout=90) as response,target.open('xb') as f:
     payload=response.read(entry['archive']['bytes']+1);f.write(payload);origin=dict(source='permitted-download',url=entry['url'],final_url=response.url,status=response.status,headers=dict(response.headers))
   if pin(target)!=entry['archive']:raise ValueError('Exact original source archive differs: '+name)
   save(out/(name+'-DOWNLOAD.json'),dict(**origin,archive=pin(target)));archives[name]=target
   original=out/'source-original'/name;original.mkdir();seen=set()
   with tarfile.open(target,'r:gz') as tar:
    for m in tar.getmembers():
     path=PurePosixPath(m.name)
     if path.is_absolute() or '..' in path.parts or '\\' in m.name or path.parts[0]!=name:raise ValueError('Unsafe original source member')
     if len(path.parts)==1:
      if not m.isdir():raise ValueError('Source archive root must be a directory')
      continue
     relative=PurePosixPath(*path.parts[1:]).as_posix()
     if relative in seen:raise ValueError('Duplicate original archive member')
     seen.add(relative);dest=original/relative
     if m.isdir():dest.mkdir(parents=True,exist_ok=True)
     elif m.isfile():
      dest.parent.mkdir(parents=True,exist_ok=True)
      with tar.extractfile(m) as src,dest.open('xb') as dst:shutil.copyfileobj(src,dst)
      dest.chmod(m.mode&0o777)
     else:raise ValueError('Unsupported original source member type')
   originals[name]=str(original)
  source_tree(originals)
  cache=a.cache_dir.resolve() if a.cache_dir else out/'pixi-cache';cache.mkdir(exist_ok=True)
  env=dict(HOME=str(out/'home'),TMPDIR=str(out/'tmp'),PATH='/usr/bin:/bin',PIXI_HOME=str(out/'pixi-home'),PIXI_CACHE_DIR=str(cache),PIXI_NO_PROGRESS='true',PIXI_NO_PATH_UPDATE='1')
  command=[a.manager,'install','--locked','--manifest-path',out/'runtime/pixi.toml']+([] if a.allow_download else ['--offline'])
  run('locked-dependencies',command,env);prefix=out/'runtime/.pixi/envs/default';ready=dict(r_prefix=str(prefix),library=str(out/'library'));env=environment(ready,out)
  for name in ['GenomeInfoDbData','celldex']:
   build=out/'build-original'/name;shutil.copytree(originals[name],build)
   run('original-'+name,[prefix/'bin/R','CMD','INSTALL','--no-multiarch','--library='+str(out/'library'),build],env)
  source_tree(originals)
  if sources()!=before:raise ValueError('Installer/adapter changed during installation')
  command=[sys.executable,'-B',HERE/'adopt_runtime.py','--workspace',out,'--out',provider]
  for name,path in originals.items():command+=['--source',name+'='+path]
  run('adopt-original-runtime',command,env)
  save(out/'COMPLETED.json',dict(status='PASS_INSTALLED_ORIGINAL_CELLDEX114_NOT_REFERENCE_ANALYSIS',provider=str(provider),source_archives={n:pin(p) for n,p in archives.items()},calls=calls,new_family_credit=0))
  print('PASS_INSTALLED_ORIGINAL_CELLDEX114_NOT_REFERENCE_ANALYSIS',flush=True);return 0
 except BaseException as error:
  if child is not None and child.poll() is None:
   os.killpg(child.pid,signal.SIGTERM)
   try:child.wait(timeout=5)
   except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;label='SKIP3_CELLDEX114_INSTALL_UNAVAILABLE' if code==3 else 'FAIL2_CELLDEX114_INSTALL_UNADMITTED'
  print(label,repr(error),ACTION,flush=True)
  if owned:
   try:save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),calls=calls,dependent_analysis_skipped=True,complete_result_admitted=False,required_action=ACTION))
   except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
  return code
if __name__=='__main__':raise SystemExit(main())
