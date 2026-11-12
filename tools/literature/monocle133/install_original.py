#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build pinned original Monocle3 1.3.3 in a new library over a separate R4.3 dependency base."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,time,traceback,urllib.request
ROOT=Path(__file__).resolve().parents[3]
ACTION='Required action: retain original source, new library and all install/readiness diagnostics. Where permitted, provide the exact R4.3 dependency base and matching original GenomeInfoDbData and Monocle3 1.3.3 source; otherwise SKIP3 dependent historical analysis. This installs only into NEW private paths, changes no existing R library and never patches or substitutes original scientific source. Namespace/source readiness does not admit graph learning or reproduce a paper.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def linux(p):
 p=p.resolve();assert p.drive.lower()=='d:';return '/mnt/d/'+p.relative_to(Path('D:/')).as_posix()
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--base',required=True,type=Path);p.add_argument('--source-candidate',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir();state=dict(status='SKIP3_EXACT_MONOCLE133_RUNTIME_UNAVAILABLE',analysis_admitted=False,required_action=ACTION)
 try:
  if not a.allow_install:raise PermissionError('Required installation is not authorized')
  base=a.base.resolve();receipt=json.loads((base/'RESULT.json').read_bytes());assert receipt['status']=='INSTALLED_PRIVATE_R43_MONOCLE131_DEPENDENCY_BASE_NOT_133_ANALYSIS';prefix=base/'.pixi/envs/default';base_pins={str(p):pin(p) for p in [base/'pixi.lock',prefix/'bin/Rscript']}
  candidate=a.source_candidate.resolve();source=json.loads((candidate/'SOURCE.json').read_bytes());locked=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/monocle133-graph/source-profile.json').read_bytes())
  if source['version']!=locked['version'] or source['commit']!=locked['commit'] or source['source_members']!=locked['source_members']:raise ValueError('Pinned original Monocle3 1.3.3 source receipt/member population differs')
  if pin(candidate/'source.tar.gz')!=source['archive']:raise ValueError('Complete original source archive checksum differs')
  for n,value in locked['source_members'].items():
   if pin(candidate/'source'/n)!=value:raise ValueError('Pinned original scientific source differs: '+n)
  stage=out/'source-stage/monocle3';stage.mkdir(parents=True)
  for n,value in source['source_members'].items():
   target=(stage/n).resolve();assert target.is_relative_to(stage);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(candidate/'source'/n,target);assert pin(target)==value
  lib=out/'R-lib';lib.mkdir();[ (out/n).mkdir() for n in ['home','tmp','cache'] ]
  metadata=prefix/'share/bioconductor-data-packages/dataURLs.json';entry=json.loads(metadata.read_bytes())['genomeinfodbdata-1.2.11'];assert entry['fn']=='GenomeInfoDbData_1.2.11.tar.gz';save(out/'GENOMEINFODBDATA-SOURCE.json',dict(recipe_data=pin(metadata),record=entry));archive=out/entry['fn'];attempts=[]
  for url in entry['urls']:
   try:
    if not url.startswith('https://'):continue
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-Monocle-runtime'})
    with urllib.request.urlopen(request,timeout=90) as response:raw=response.read(134217729);resolved=response.url
    if len(raw)>134217728 or hashlib.md5(raw).hexdigest()!=entry['md5']:raise ValueError('Complete original GenomeInfoDbData size/MD5 differs')
    archive.write_bytes(raw);attempts.append(dict(url=url,resolved_url=resolved,pin=pin(archive),upstream_md5=entry['md5']));break
   except Exception as error:attempts.append(dict(url=url,cause=repr(error)))
  save(out/'GENOMEINFODBDATA-FETCH.json',attempts)
  if not archive.is_file():raise OSError('Original GenomeInfoDbData download unavailable; all mirror diagnostics retained')
  environment=['/usr/bin/env','PATH='+linux(prefix/'bin')+':/usr/bin:/bin','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache'),'R_LIBS='+linux(lib),'R_LIBS_USER='+linux(lib),'R_LIBS_SITE=','R_ENVIRON_USER=/dev/null','R_PROFILE_USER=/dev/null','R_MAKEVARS_USER=/dev/null','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1','LANG=C','LC_ALL=C']
  def run(label,arguments,maximum):
   command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15',str(maximum),*environment,*map(str,arguments)];started=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    process=subprocess.Popen(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.started.json'),dict(command=command,pid=process.pid,started_unix=started));code=process.wait(timeout=maximum+60)
   save(out/(label+'.process.json'),dict(command=command,pid=process.pid,exit_code=code,elapsed_seconds=time.time()-started));print(label,'exit',code,flush=True)
   if code:raise OSError('Exact original runtime setup failed at '+label+'; inspect retained stdout/stderr')
  save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),source_receipt=source,base_pins=base_pins,private_library=linux(lib),initial_base_namespace_probe='SKIP3: GenomeInfoDbData absent after disabled data post-link; no graph analysis run',analysis_admitted=False))
  run('install-original-GenomeInfoDbData',[linux(prefix/'bin/R'),'CMD','INSTALL','--library='+linux(lib),linux(archive)],600)
  run('install-original-monocle133',[linux(prefix/'bin/R'),'CMD','INSTALL','--library='+linux(lib),linux(stage)],1200)
  script=ROOT/'tools/literature/monocle133/check_original.R';run('check-original-namespace',[linux(prefix/'bin/Rscript'),'--vanilla',linux(script),linux(out),linux(lib),linux(prefix/'lib/R/library'),linux(stage)],300)
  ready=json.loads((out/'READINESS.json').read_bytes());assert ready['status']=='PASS_EXACT_ORIGINAL_MONOCLE133_NAMESPACE_AND_SOURCE_NOT_ANALYSIS';assert all(pin(Path(n))==v for n,v in base_pins.items());assert all(pin(candidate/'source'/n)==v for n,v in source['source_members'].items())
  state.update(status='READY_EXACT_ORIGINAL_MONOCLE133_NAMESPACE_NOT_ANALYSIS',readiness=pin(out/'READINESS.json'),original_source_receipt=pin(candidate/'SOURCE.json'),source_members=source['source_members'],installed_members={p.relative_to(lib).as_posix():pin(p) for p in lib.rglob('*') if p.is_file()},base_pins=base_pins,rscript=linux(prefix/'bin/Rscript'),libraries=[linux(lib),linux(prefix/'lib/R/library')])
 except Exception as error:
  state.update(cause=repr(error),traceback=traceback.format_exc())
  if not isinstance(error,OSError):state['status']='FAIL2_ORIGINAL_MONOCLE133_SOURCE_INSTALL_INTEGRITY'
 save(out/'RESULT.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('cause',''),'\n'+ACTION+'\nWarnings: '+str(out/'WARNINGS.md'),flush=True);return 0 if state['status'].startswith('READY_') else 2 if state['status'].startswith('FAIL2') else 3
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original source/install checks')
 raise SystemExit(main())
