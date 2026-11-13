#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Provision original FastMNN1.29.0 into NEW private Linux paths; perform no analysis."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tarfile,time,traceback
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
PROFILE=ROOT/'src/main/resources/org/jlinalg/omics/fastmnn129/source-profile.json'
ACTION='Required action: run on permitted Linux with Python3.11+, a complete exact locked dependency base or explicit --provision-base/--pixi, and --allow-install. --allow-download is required for source fetching or dependency-base provisioning; local --source-root avoids source downloading. Installation is private and performs no analysis. If prerequisites/download/install/execution are unavailable or forbidden, SKIP3 skips dependent analysis. FAIL2 means resolve the named input/source/integrity cause without patching original software. Retain diagnostics and retry NEW output paths. P040 reports method1.29; exact paper patch/call and biological validity remain unproven.'
class Unavailable(OSError):pass
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def copy_source(source,dest,expected):
 if source.is_symlink() or not source.is_dir():raise ValueError('Canonical complete source directory required')
 entries={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file() or p.is_symlink()}
 if set(entries)!=set(expected):raise ValueError('Complete pristine source population differs; compiler working trees are separate')
 dest.mkdir()
 for name,wanted in expected.items():
  original=entries[name]
  if original.is_symlink() or pin(original)!=wanted:raise ValueError('Original source fingerprint differs: '+name)
  target=dest/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,target)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);g=p.add_mutually_exclusive_group(required=True);g.add_argument('--base',type=Path);g.add_argument('--provision-base',action='store_true');p.add_argument('--pixi',type=Path);p.add_argument('--source-root',type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Use canonical NEW output paths')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if os.name!='posix' or not __import__('sys').platform.startswith('linux'):raise Unavailable('Original runtime provisioning requires Linux, including permitted WSL with Linux paths')
  if not a.allow_install:raise Unavailable('Installation permission is absent; no dependency installation or analysis launched')
  if a.provision_base and (not a.allow_download or not a.pixi):raise Unavailable('Dependency provisioning requires explicit --allow-download and a permitted --pixi executable')
  if a.base and not a.base.resolve().is_dir():raise Unavailable('Existing complete locked dependency base is unavailable')
  profile=json.loads(PROFILE.read_bytes());expected=profile['source_members']
  base=out/'dependency-base' if a.provision_base else a.base.resolve()
  if a.base and (out.is_relative_to(base) or base.is_relative_to(out)):raise ValueError('Output and pre-existing dependency base must not overlap')
  def run(label,command,env=None,seconds=900,prerequisite=False):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=seconds)
   row=dict(label=label,command=command,exit_code=result.returncode,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if result.returncode:raise Unavailable('Required provisioning failed at '+label+'; inspect retained logs') if prerequisite else ValueError('Readiness/integrity failed at '+label+'; inspect retained logs')
  if a.provision_base:
   base.mkdir();shutil.copyfile(HERE/'reference-pixi.toml',base/'pixi.toml');shutil.copyfile(HERE/'reference-pixi.lock',base/'pixi.lock')
   env=dict(os.environ);env.update(PIXI_NO_PATH_UPDATE='1',PIXI_HOME=str(out/'pixi-home'),PIXI_CACHE_DIR=str(out/'pixi-cache'),TMPDIR=str(out/'tmp'));(out/'tmp').mkdir()
   run('dependency-install',[a.pixi.resolve(),'install','--locked','--manifest-path',base/'pixi.toml'],env,1800,True)
  if pin(base/'pixi.lock')!=pin(HERE/'reference-pixi.lock') or pin(base/'pixi.toml')!=pin(HERE/'reference-pixi.toml'):raise ValueError('Dependency manifest/lock differs from the exact selected profile')
  prefix=base/'.pixi/envs/default';rscript=prefix/'bin/Rscript';rcommand=prefix/'bin/R'
  if not rscript.is_file() or not rcommand.is_file():raise Unavailable('Locked private R runtime is incomplete')
  before={str(path):pin(path) for path in [base/'pixi.lock',base/'pixi.toml',rscript]}
  if a.source_root:copy_source(a.source_root.resolve(),out/'source',expected)
  else:
   if not a.allow_download:raise Unavailable('Supply local pristine --source-root or explicit --allow-download')
   git=shutil.which('git')
   if not git:raise Unavailable('Permitted Git is unavailable for the fixed official source download')
   run('source-clone',[git,'clone','--no-checkout',profile['source_repository'],out/'git-source'],prerequisite=True)
   run('source-archive',[git,'-c','core.autocrlf=false','-c','core.eol=lf','-C',out/'git-source','archive','--format=tar','--output',out/'source.tar',profile['source_commit']])
   archive=out/'source.tar'
   if archive.stat().st_size>67108864:raise ValueError('Complete source archive exceeds byte capacity')
   staged=out/'source-download';staged.mkdir()
   with tarfile.open(archive) as tar:
    members=[m for m in tar.getmembers() if not m.isdir()]
    if len(members)!=len(expected) or len({m.name for m in members})!=len(members) or {m.name for m in members}!=set(expected):raise ValueError('Official source archive population differs')
    for m in members:
     if not m.isfile() or m.size!=expected[m.name]['bytes'] or m.size>16777216:raise ValueError('Invalid original source member')
     target=staged/m.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(m).read(16777217))
   copy_source(staged,out/'source',expected)
  # Never compile inside the preserved original source tree.
  copy_source(out/'source',out/'source-build',expected);library=out/'R-lib';library.mkdir()
  env=dict(os.environ);env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',LANG='C.UTF-8',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  run('original-install',[rcommand,'CMD','INSTALL','--no-multiarch','--library='+str(library),out/'source-build'],env,900,True)
  for name,wanted in expected.items():
   if pin(out/'source'/name)!=wanted or pin(out/'source-build'/name)!=wanted:raise ValueError('An original source file changed during installation: '+name)
  shutil.copyfile(PROFILE,out/'source-profile.json');shutil.copyfile(HERE/'check_runtime.R',out/'check_runtime.R')
  libraries=[library,prefix/'lib/R/library'];run('runtime-readiness',[rscript,'--vanilla',out/'check_runtime.R',out,out/'source-profile.json',*libraries],env)
  ready=json.loads((out/'readiness.json').read_bytes())
  if ready['status']!='READY_PRIVATE_ORIGINAL_FASTMNN129_NOT_ANALYSIS' or ready['source_functions_checked']!=105 or ready['analysis_admitted']:raise ValueError('Unsupported namespace readiness claim')
  if any(pin(Path(name))!=value for name,value in before.items()):raise ValueError('Pre-existing dependency identity changed')
  result=dict(status=ready['status'],rscript=str(rscript),libraries=list(map(str,libraries)),source_root=str(out/'source'),source_commit=profile['source_commit'],version='1.29.0',readiness=pin(out/'readiness.json'),source_profile=pin(out/'source-profile.json'),base_pins=before,calls=calls,analysis_admitted=False,paper_patch_recovered=False,required_action=ACTION,whole_goal_complete=False)
  save(out/'RESULT.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_FASTMNN_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_FASTMNN_PROVISIONING',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False,required_action=ACTION)
  if owned:save(out/'FAILURE.json',value);(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
