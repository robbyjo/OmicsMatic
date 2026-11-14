#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install separate unchanged ggpubr0.4.0/0.6.0 editions with exact original ggplot2/rstatix sources."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,tarfile,time,traceback,urllib.request
HERE=Path(__file__).resolve().parent
ACTION='Required action: select literal --version0.4.0 or0.6.0, a permitted locked Linux/WSL R4.3.3 dependency base, explicit --allow-install and NEW private output. Supply all three exact --source-cache archives or permit downloads with --allow-download. Provision the base with reference-pixi.toml/reference-pixi.lock and pixi install --locked. Missing/forbidden source, runtime, download, installation or execution SKIP3 skips dependent analysis. FAIL2 admits no provider; retain diagnostics, resolve the named source/namespace contradiction and retry NEW paths without patching original code. The selected ggplot2 3.4.3/rstatix0.7.2/R4.3.3 profile does not prove every historical author dependency. This setup checks namespace readiness/fonts but runs no paper analysis or plot controls; a READY provider is not a scientific result.'
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,x):p.write_text(json.dumps(x,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--version',choices=['0.4.0','0.6.0'],required=True);p.add_argument('--base',type=Path,required=True);p.add_argument('--source-cache',type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,a.base.resolve(),a.source_cache.resolve() if a.source_cache else HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/runtime/cache')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise OSError('Installation is forbidden; dependent analysis skipped')
  if sys.platform!='linux':raise OSError('Use the selected native Linux/WSL dependency profile')
  if not a.source_cache and not a.allow_download:raise OSError('Exact source-cache archives or download permission required')
  base=a.base.resolve();prefix=base/'.pixi/envs/default';rscript=prefix/'bin/Rscript';r=prefix/'bin/R';rlib=prefix/'lib/R/library';library=out/'library';library.mkdir();(out/'tmp').mkdir();archives=out/'archives';archives.mkdir()
  for n in ['pixi.toml','pixi.lock']:
   if pin(base/n)!=pin(HERE/('reference-'+n)):raise ValueError('Exact locked dependency profile differs: '+n)
  if not rscript.is_file() or not r.is_file() or not rlib.is_dir():raise OSError('Complete selected R runtime is unavailable')
  helpers={n:pin(HERE/n) for n in ['provider.py','check_runtime.R','source-profiles.json','reference-pixi.toml','reference-pixi.lock']};snapshot=out/'setup-source-snapshot';snapshot.mkdir()
  for n in helpers:shutil.copyfile(HERE/n,snapshot/n)
  # Pin all R-library regular files, native R entry points and the exact lock.
  dependency_files=[base/'pixi.toml',base/'pixi.lock',rscript,prefix/'lib/R/bin/exec/R',prefix/'lib/R/lib/libR.so']
  dependency_files += [f for f in sorted(rlib.rglob('*')) if f.is_file() and not f.is_symlink()]
  dependency_pins={str(f):pin(f) for f in dependency_files};profile=json.loads((HERE/'source-profiles.json').read_bytes());source=out/'source';source.mkdir()
  selected=[profile['profiles']['ggplot2-3.4.3'],profile['profiles']['rstatix-0.7.2'],profile['profiles']['ggpubr-'+a.version]]
  env={k:v for k,v in os.environ.items() if not k.startswith('R_') and k not in ['LD_LIBRARY_PATH','LD_PRELOAD']};env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',R_LIBS=str(library)+':'+str(rlib),R_LIBS_USER=str(library),R_LIBS_SITE='',LC_ALL='C.UTF-8',TMPDIR=str(out/'tmp'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  def run(label,command,prerequisite=False):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,command=command,pid=child.pid,started_unix=start))
    while True:
     try:code=child.wait(timeout=30);break
     except subprocess.TimeoutExpired:print('SAME_ORIGINAL_SETUP_CHILD_RUNNING',label,child.pid,round(time.time()-start),flush=True)
   row=dict(label=label,command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if code:raise OSError('Required original runtime/installation unavailable: '+label) if prerequisite else ValueError('Original namespace/source readiness failed: '+label)
  run('R-version',[rscript,'--version'],True)
  for spec in selected:
   archive=archives/spec['archive_name'];local=a.source_cache/spec['archive_name'] if a.source_cache else None
   if local and local.is_file():shutil.copyfile(local,archive)
   elif a.allow_download:
    with urllib.request.urlopen(urllib.request.Request(spec['url'],headers={'User-Agent':'OmicsMatic-private-original-ggpubr/1'}),timeout=90) as f:archive.write_bytes(f.read(spec['archive']['bytes']+1))
   else:raise OSError('Exact required archive unavailable: '+spec['archive_name'])
   if pin(archive)!=spec['archive']:raise ValueError('Complete original archive differs: '+spec['archive_name'])
   with tarfile.open(archive) as t:
    members=[m for m in t.getmembers() if not m.isdir()];names=[m.name for m in members]
    if len(names)!=len(set(names)) or set(names)!=set(spec['source_files']):raise ValueError('Complete original source population differs')
    for m in members:
     if not m.isfile() or m.name.startswith('/') or '\\' in m.name or any(v in ['', '.', '..'] for v in m.name.split('/')):raise ValueError('Unsafe original source archive member')
     target=source/m.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(t.extractfile(m).read())
     if pin(target)!=spec['source_files'][m.name]:raise ValueError('Complete original source member differs: '+m.name)
   run('install-'+spec['package'],[r,'CMD','INSTALL','--no-multiarch','--library='+str(library),archive],True)
  run('namespace-readiness',[rscript,'--vanilla',HERE/'check_runtime.R',library,rlib,source,HERE/'source-profiles.json',a.version,out])
  ready=json.loads((out/'readiness.json').read_bytes())
  if ready['status']!='READY_UNCHANGED_ORIGINAL_GGPUBR_NAMESPACE_NOT_ANALYSIS' or ready['version']!=a.version or ready['R_version']!='4.3.3':raise ValueError('Selected original namespace readiness differs')
  for spec in selected:
   for n,wanted in spec['source_files'].items():
    if pin(source/n)!=wanted:raise ValueError('Original scientific source changed during setup')
  if any(pin(Path(n))!=wanted for n,wanted in dependency_pins.items()) or any(pin(HERE/n)!=wanted for n,wanted in helpers.items()):raise ValueError('Protected dependency/setup source changed')
  font_pins={font['path']:pin(Path(font['path'])) for font in ready['fonts']}
  installed={f.relative_to(library).as_posix():pin(f) for f in sorted(library.rglob('*')) if f.is_file()}
  result=dict(status='READY_PRIVATE_UNCHANGED_ORIGINAL_GGPUBR_NOT_ANALYSIS',version=a.version,R_version='4.3.3',ggplot2_version='3.4.3',rstatix_version='0.7.2',rscript=str(rscript),r_prefix=str(prefix),base_library=str(rlib),library=str(library),source_root=str(source),
   source_profile=pin(HERE/'source-profiles.json'),source_pins=helpers,dependency_files=dependency_pins,font_files=font_pins,installed_library=installed,readiness=pin(out/'readiness.json'),namespace_checks=ready['checks'],calls=calls,
   original_source_modified=False,paper_analysis_executed=False,plot_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'READY.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],a.version,flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_GGPUBR_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_GGPUBR_PROVIDER',cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
