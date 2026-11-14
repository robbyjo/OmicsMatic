#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install unchanged rstatix0.7.2 in a NEW private library over the exact locked Linux R4.5.3 base."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,tarfile,time,traceback,urllib.request
HERE=Path(__file__).resolve().parent
ACTION='Required action: use a permitted Linux/WSL locked R4.5.3 dependency base, explicit --allow-install and NEW output. Supply the exact original --source-archive or permit download with --allow-download. Provision dependencies separately using reference-pixi.toml and reference-pixi.lock with pixi install --locked. Missing/forbidden download, installation, source, runtime or execution SKIP3 skips dependent analysis. FAIL2 admits no provider; preserve raw logs, correct the named integrity/readiness cause and retry NEW paths. This installs original GPL-2 rstatix separately; it does not translate its R code into the GPL-3 Java library, run paper analysis or award catalog credit.'

def pin(path):
 h=hashlib.sha256();size=0
 with path.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--base',required=True,type=Path);p.add_argument('--source-archive',type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,a.base.resolve(),a.source_archive.resolve() if a.source_archive else HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/base/archive')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise OSError('Installation permission absent; dependent analysis skipped')
  if sys.platform!='linux':raise OSError('Use the qualified Linux/WSL original-package dependency base')
  if not a.source_archive and not a.allow_download:raise OSError('Exact original local source or download permission required')
  base=a.base.resolve();prefix=base/'.pixi/envs/default';rscript=prefix/'bin/Rscript';r=prefix/'bin/R';library=out/'library';rlib=prefix/'lib/R/library'
  for n in ['pixi.toml','pixi.lock']:
   if pin(base/n)!=pin(HERE/('reference-'+n)):raise ValueError('Exact dependency manifest/lock differs: '+n)
  if not rscript.is_file() or not r.is_file() or not rlib.is_dir():raise OSError('Complete locked R runtime unavailable')
  helpers={n:pin(HERE/n) for n in ['provider.py','check_runtime.R','source-profile.json','reference-pixi.toml','reference-pixi.lock']}
  dependency_pins={str(f):pin(f) for f in [base/'pixi.toml',base/'pixi.lock',rscript,*sorted(rlib.glob('*/DESCRIPTION'))]}
  profile=json.loads((HERE/'source-profile.json').read_bytes());archive=out/profile['archive_name'];(out/'tmp').mkdir();library.mkdir()
  if a.source_archive:shutil.copyfile(a.source_archive,archive)
  else:
   with urllib.request.urlopen(urllib.request.Request(profile['url'],headers={'User-Agent':'OmicsMatic-private-original-rstatix/1'}),timeout=90) as f:archive.write_bytes(f.read(profile['archive']['bytes']+1))
  if pin(archive)!=profile['archive']:raise ValueError('Complete original source archive differs')
  source=out/'source';source.mkdir()
  with tarfile.open(archive) as tar:
   members=[m for m in tar.getmembers() if not m.isdir()];names=[m.name for m in members]
   if len(names)!=len(set(names)) or set(names)!=set(profile['source_files']):raise ValueError('Complete original source population differs')
   for m in members:
    if not m.isfile() or m.name.startswith('/') or '\\' in m.name or any(s in ['', '.', '..'] for s in m.name.split('/')):raise ValueError('Unsafe original archive member')
    raw=tar.extractfile(m).read();dest=source/m.name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    if pin(dest)!=profile['source_files'][m.name]:raise ValueError('Original source member differs: '+m.name)
  env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']}
  env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',R_LIBS=str(library)+':'+str(rlib),R_LIBS_USER=str(library),R_LIBS_SITE='',LC_ALL='C.UTF-8',TMPDIR=str(out/'tmp'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  def run(label,command,prerequisite=False):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    proc=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,command=command,pid=proc.pid,started_unix=start))
    while True:
     try:code=proc.wait(timeout=30);break
     except subprocess.TimeoutExpired:print('SAME_SETUP_CHILD_RUNNING',label,proc.pid,round(time.time()-start),flush=True)
   row=dict(label=label,command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if code:raise OSError('Original runtime/installation unavailable: '+label) if prerequisite else ValueError('Original readiness failed: '+label)
  run('R-version',[rscript,'--version'],True)
  run('original-install',[r,'CMD','INSTALL','--no-multiarch','--library='+str(library),archive],True)
  run('namespace-readiness',[rscript,'--vanilla',HERE/'check_runtime.R',library,rlib,source/'rstatix',out])
  ready=json.loads((out/'readiness.json').read_bytes())
  if ready['status']!='READY_ORIGINAL_RSTATIX072_NAMESPACE_NOT_ANALYSIS' or sorted(ready['exports'])!=profile['namespace_exports']:raise ValueError('Complete original export identity differs')
  for name,wanted in profile['source_files'].items():
   if pin(source/name)!=wanted:raise ValueError('Original source changed during installation')
  if any(pin(Path(name))!=wanted for name,wanted in dependency_pins.items()) or any(pin(HERE/n)!=wanted for n,wanted in helpers.items()):raise ValueError('Protected dependency/setup source changed')
  installed={f.relative_to(library).as_posix():pin(f) for f in sorted(library.rglob('*')) if f.is_file()}
  result=dict(status='READY_PRIVATE_ORIGINAL_RSTATIX072_NOT_ANALYSIS',package='rstatix',version='0.7.2',rscript=str(rscript),r_prefix=str(prefix),base_library=str(rlib),library=str(library),
   source_profile=pin(HERE/'source-profile.json'),source_pins=helpers,dependency_pins=dependency_pins,installed_library=installed,source_root=str(source/'rstatix'),
   namespace_exports=ready['exports'],source_functions_checked=len(ready['source_functions_checked']),readiness=pin(out/'readiness.json'),calls=calls,
   original_source_modified=False,paper_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'READY.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_RSTATIX_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_PROVIDER',cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
