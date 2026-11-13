#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install unchanged AllelicSeries0.1.1.2 and fixed original dependencies in a NEW private library."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,sys,tarfile,time,traceback,urllib.request
HERE=Path(__file__).resolve().parent
ACTION='Required action: use permitted Linux/WSL R4.5.3 with the documented base-package versions and C/C++ toolchain. Supply --allow-install and exact --source-cache archives or --allow-download; installation stays in a NEW private output library. Missing/forbidden prerequisites SKIP3 skips dependent analysis. FAIL2 admits no setup or result; inspect raw logs and correct the named integrity/readiness cause, then retry NEW paths. The selected original package is0.1.1.2; the paper literally reports1.1.2 and exact build equivalence is unproven. Do not relabel versions, patch original software or claim paper reproduction. Setup performs no paper analysis.'

def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--r-prefix',required=True,type=Path);p.add_argument('--source-cache',type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,a.r_prefix.resolve(),a.source_cache.resolve() if a.source_cache else HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source, runtime or cache')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise OSError('Installation permission absent; dependent analysis skipped')
  if sys.platform!='linux':raise OSError('Use the qualified Linux/WSL original-source profile')
  if not a.source_cache and not a.allow_download:raise OSError('Provide exact permitted archives or download permission')
  prefix=a.r_prefix.resolve();rscript=prefix/'bin/Rscript';r=prefix/'bin/R';base=prefix/'lib/R/library'
  if not rscript.is_file() or not r.is_file() or not base.is_dir():raise OSError('Complete R prefix unavailable')
  profile=json.loads((HERE/'source-profile.json').read_bytes());helper_pins={n:pin(HERE/n) for n in ['provider.py','check_runtime.R','source-profile.json']};base_pins={str(f):pin(f) for f in [rscript,*sorted(base.glob('*/DESCRIPTION'))]}
  library=out/'library';library.mkdir();(out/'tmp').mkdir();archives=out/'archives';archives.mkdir()
  env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']}
  env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_PROFILE_USER='/dev/null',R_ENVIRON_USER='/dev/null',R_LIBS=str(library)+':'+str(base),R_LIBS_USER=str(library),R_LIBS_SITE='',LC_ALL='C.UTF-8',TMPDIR=str(out/'tmp'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',MAKEFLAGS='-j1')
  def run(label,command,prerequisite=False,seconds=1800):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    proc=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,pid=proc.pid,command=command,started_unix=start))
    # Observation intervals are not evidence of exit. Keep the same live child.
    while True:
     try:code=proc.wait(timeout=seconds);break
     except subprocess.TimeoutExpired:save(out/'LIVE.json',dict(label=label,pid=proc.pid,command=command,started_unix=start,still_running=True,observed_elapsed_seconds=time.time()-start))
   row=dict(label=label,command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if code:raise OSError('Permitted prerequisite/source installation unavailable at '+label) if prerequisite else ValueError('Original namespace integrity/readiness failed at '+label)
  run('R-version',[rscript,'--version'],True,60)
  if not re.search(r'\b4\.5\.3\b',(out/'R-version.stdout').read_text()+(out/'R-version.stderr').read_text()):raise OSError('Selected R4.5.3 unavailable')
  # Verify installed base versions without installing into or altering that library.
  probe='p<-commandArgs(TRUE); stopifnot(packageDescription("glue",lib.loc=p[1])$Version=="1.8.1",packageDescription("Rcpp",lib.loc=p[1])$Version=="1.1.2",packageDescription("Matrix",lib.loc=p[1])$Version=="1.7-6")'
  run('base-profile',[rscript,'--vanilla','-e',probe,str(base)],True,60)
  deps=profile['private_dependency_sources'];target=dict(name=profile['package'],version=profile['version'],url=profile['url'],archive='AllelicSeries_0.1.1.2.tar.gz',**profile['archive'])
  for row in [*deps,target]:
   archive=archives/row['archive'];expected={k:row[k] for k in ['bytes','sha256']};local=a.source_cache/row['archive'] if a.source_cache else None
   if local and local.is_file():shutil.copyfile(local,archive)
   elif a.allow_download:
    with urllib.request.urlopen(urllib.request.Request(row['url'],headers={'User-Agent':'OmicsMatic-private-original-AllelicSeries/1'}),timeout=90) as response:archive.write_bytes(response.read(expected['bytes']+1))
   else:raise OSError('Exact permitted archive unavailable: '+row['archive'])
   if pin(archive)!=expected:raise ValueError('Original archive integrity mismatch: '+row['archive'])
   with tarfile.open(archive) as tar:
    members=[m for m in tar.getmembers() if not m.isdir()];names=[m.name for m in members]
    if len(names)!=len(set(names)):raise ValueError('Duplicate original archive member')
    for m in members:
     if not m.isfile() or m.name.startswith('/') or '\\' in m.name or any(s in ['', '.', '..'] for s in m.name.split('/')):raise ValueError('Unsafe original archive member')
    if row['name']=='AllelicSeries':
     if set(names)!=set(profile['source_files']):raise ValueError('Complete original target source population differs')
     for m in members:
      raw=tar.extractfile(m).read()
      if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=profile['source_files'][m.name]:raise ValueError('Original target source differs: '+m.name)
      path=out/'source'/m.name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
   run('install-'+row['name'],[r,'CMD','INSTALL','--no-multiarch','--library='+str(library),archive],True)
  run('namespace-readiness',[rscript,'--vanilla',HERE/'check_runtime.R',library,base,out/'source/AllelicSeries',out])
  ready=(out/'readiness.txt').read_text().splitlines()
  if ready[:2]!=['READY_ORIGINAL_NAMESPACE_NOT_ANALYSIS','0.1.1.2'] or sorted(ready[3:])!=sorted(profile['function_exports']):raise ValueError('Complete original export/readiness population differs')
  for name,wanted in profile['source_files'].items():
   if pin(out/'source'/name)!=wanted:raise ValueError('Original target source changed during install')
  if any(pin(Path(name))!=wanted for name,wanted in base_pins.items()) or any(pin(HERE/name)!=wanted for name,wanted in helper_pins.items()):raise ValueError('Pre-existing runtime or provider code changed')
  installed={p.relative_to(library).as_posix():pin(p) for p in sorted(library.rglob('*')) if p.is_file()}
  result=dict(status='READY_PRIVATE_ORIGINAL_ALLELICSERIES0112_NOT_ANALYSIS',package='AllelicSeries',version='0.1.1.2',reported_paper_version='1.1.2',paper_build_equivalence_proven=False,rscript=str(rscript),r_prefix=str(prefix),base_library=str(base),library=str(library),source_profile=pin(HERE/'source-profile.json'),source_pins=helper_pins,source_functions_checked=int(ready[2]),function_exports=ready[3:],installed_library=installed,base_metadata=base_pins,calls=calls,source_modified=False,paper_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'READY.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(OSError,subprocess.TimeoutExpired)) else 2;value=dict(status='SKIP3_ORIGINAL_ALLELICSERIES_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_ALLELICSERIES_PROVIDER',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False,required_action=ACTION)
  if owned:save(out/'FAILURE.json',value);(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
