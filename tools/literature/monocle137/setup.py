#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install original Monocle1.3.7/Batchelor1.20 into NEW private paths over the locked R4.3 base."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,shutil,subprocess,tarfile,time,traceback,urllib.request
ROOT=Path(__file__).resolve().parents[3];MODULE=Path(__file__).resolve().parent;PROFILE=ROOT/'src/main/resources/org/jlinalg/omics/monocle137-workflow/source-profile.json'
GENOME=dict(bytes=12284235,sha256='311fe287842e6161f24fc1faaa824a6d5ce88afcbf4c0203f5e309456e667bc2');GENOME_URL='https://bioconductor.org/packages/3.18/data/annotation/src/contrib/GenomeInfoDbData_1.2.11.tar.gz'
ACTION='Required action: retain RESULT.json, WARNINGS.md, original source and install/readiness logs. Supply the documented locked private R4.3.3 dependency base. Explicitly permit installation and, if needed, pinned source/data downloads; otherwise SKIP3 skips dependent Monocle1.3.7/Batchelor1.20 analysis. Local source/data donors may replace downloads. Source/hash contradictions are FAIL2: correct them before retrying NEW paths; patch no scientific code, replace no edition and modify no existing library. Namespace readiness is not completed analysis or study reproduction.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def linux(p):
 p=p.resolve()
 if os.name!='nt':return str(p)
 if p.drive.lower()!='d:':raise OSError('This host recipe requires the D: workspace mounted as /mnt/d in WSL')
 return '/mnt/d/'+p.relative_to(Path('D:/')).as_posix()
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--base',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--monocle-source-root',type=Path);p.add_argument('--batchelor-source-root',type=Path);p.add_argument('--genome-data-package',type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--distribution',default='JLinAlg-Omics-SAIGE-20261026');a=p.parse_args();out=a.out.resolve();owned=False;state=dict(status='SKIP3_MONOCLE137_BATCHELOR120_RUNTIME_UNAVAILABLE',analysis_admitted=False,required_action=ACTION)
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') or out.exists():raise ValueError('Require NEW output under build/literature-review/monocle-runtime')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise PermissionError('Private installation is not authorized; dependent analysis skipped')
  profile=json.loads(PROFILE.read_bytes());base=a.base.resolve();receipt=json.loads((base/'RESULT.json').read_bytes())
  if receipt['status']!='INSTALLED_PRIVATE_R43_MONOCLE131_DEPENDENCY_BASE_NOT_133_ANALYSIS':raise ValueError('Require the exact historical R4.3 dependency base; its original1.3.1 package is not the new1.3.7 runtime')
  prefix=base/'.pixi/envs/default'
  if pin(base/'pixi.lock')!=pin(ROOT/'tools/literature/monocle133/reference-pixi.lock'):raise ValueError('Historical dependency lock differs')
  base_pins={str(f):pin(f) for f in [base/'pixi.lock',prefix/'bin/Rscript',prefix/'lib/R/library/monocle3/DESCRIPTION',prefix/'lib/R/library/batchelor/DESCRIPTION']}
  def fetch(url,target,wanted):
   if not a.allow_download:raise PermissionError('Required download not permitted: '+url)
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-monocle137-runtime'}),timeout=90) as r,target.open('xb') as f:
    total=0
    for b in iter(lambda:r.read(1048576),b''):
     total+=len(b)
     if total>wanted['bytes']:raise ValueError('Pinned download exceeds complete byte size')
     f.write(b)
   if pin(target)!=wanted:raise ValueError('Pinned download integrity differs: '+url)
  source=out/'source';source.mkdir();stage=out/'source-stage';stage.mkdir();lib=out/'R-lib';lib.mkdir()
  for name in ['home','tmp','cache']:(out/name).mkdir()
  for name,donor in [('monocle3',a.monocle_source_root),('batchelor',a.batchelor_source_root)]:
   entry=profile['sources'][name];members=entry['source_members'];target=source/name;target.mkdir()
   if donor:
    donor=donor.resolve()
    for n,wanted in members.items():
     if pin(donor/n)!=wanted:raise ValueError('Original source member differs: '+name+'/'+n)
     f=target/n;f.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(donor/n,f)
   else:
    archive=out/(name+'-source.tar.gz');fetch(entry['url'],archive,entry['archive']);found=set();expected_root='monocle3-'+entry['commit'] if name=='monocle3' else 'batchelor'
    with tarfile.open(archive,'r:gz') as t:
     for m in t.getmembers():
      if m.isdir():continue
      path=PurePosixPath(m.name)
      if not m.isfile() or m.issym() or m.islnk() or path.is_absolute() or len(path.parts)<2 or '..' in path.parts or '\\' in m.name or path.parts[0]!=expected_root:raise ValueError('Unsafe original source archive member')
      n=PurePosixPath(*path.parts[1:]).as_posix()
      if n not in members or n in found or m.size!=members[n]['bytes']:raise ValueError('Unexpected/incomplete original source population')
      found.add(n);f=target/n;f.parent.mkdir(parents=True,exist_ok=True)
      with t.extractfile(m) as value:raw=value.read(16777217)
      f.write_bytes(raw)
    if found!=set(members):raise ValueError('Incomplete original source member population')
   for n,wanted in members.items():
    if pin(target/n)!=wanted:raise ValueError('Captured original source differs: '+name+'/'+n)
   shutil.copytree(target,stage/name)
  environment=['/usr/bin/env','PATH='+linux(prefix/'bin')+':/usr/bin:/bin','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache'),'R_LIBS='+linux(lib),'R_LIBS_USER='+linux(lib),'R_LIBS_SITE=','R_ENVIRON_USER=/dev/null','R_PROFILE_USER=/dev/null','R_MAKEVARS_USER=/dev/null','MAKEFLAGS=-j1','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1','LANG=C','LC_ALL=C']
  def run(label,args,maximum):
   command=(['wsl.exe','-d',a.distribution,'--user','root','--exec'] if os.name=='nt' else [])+['/usr/bin/timeout','--signal=TERM','--kill-after=15',str(maximum),*environment,*map(str,args)];started=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    proc=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.started.json'),dict(command=command,pid=proc.pid,started_unix=started));code=proc.wait(timeout=maximum+60)
   save(out/(label+'.process.json'),dict(command=command,pid=proc.pid,exit_code=code,elapsed_seconds=time.time()-started))
   if code:raise OSError('Required original runtime build/readiness unavailable at '+label+'; inspect retained logs')
  save(out/'STARTED.json',dict(source_profile=pin(PROFILE),setup_source=pin(Path(__file__)),base_pins=base_pins,analysis_admitted=False))
  if a.genome_data_package:
   donor=a.genome_data_package.resolve();description=(donor/'DESCRIPTION').read_text(encoding='utf8')
   if '\nVersion: 1.2.11\n' not in '\n'+description:raise ValueError('GenomeInfoDbData1.2.11 required')
   pins={f.relative_to(donor).as_posix():pin(f) for f in donor.rglob('*') if f.is_file()};shutil.copytree(donor,lib/'GenomeInfoDbData')
   if any(pin(donor/n)!=wanted or pin(lib/'GenomeInfoDbData'/n)!=wanted for n,wanted in pins.items()):raise ValueError('Original data donor changed during capture')
   save(out/'DATA-DONOR.json',dict(path=str(donor),files=pins))
  else:
   genome=out/'GenomeInfoDbData_1.2.11.tar.gz';fetch(GENOME_URL,genome,GENOME);run('install-original-data',[linux(prefix/'bin/R'),'CMD','INSTALL','--library='+linux(lib),linux(genome)],600)
  for name in ['batchelor','monocle3']:run('install-original-'+name,[linux(prefix/'bin/R'),'CMD','INSTALL','--library='+linux(lib),linux(stage/name)],1200)
  run('check-original-namespaces',[linux(prefix/'bin/Rscript'),'--vanilla',linux(MODULE/'check_runtime.R'),linux(out),linux(lib),linux(prefix/'lib/R/library'),linux(source/'monocle3'),linux(source/'batchelor'),linux(PROFILE)],300)
  ready=json.loads((out/'READINESS.json').read_bytes())
  if ready['status']!='READY_ORIGINAL_MONOCLE137_BATCHELOR120_NAMESPACE_NOT_ANALYSIS':raise ValueError('Exact original namespace readiness did not pass')
  if any(pin(Path(n))!=wanted for n,wanted in base_pins.items()):raise ValueError('Existing dependency base changed')
  for name,entry in profile['sources'].items():
   if any(pin(source/name/n)!=wanted for n,wanted in entry['source_members'].items()):raise ValueError('Original source changed during installation: '+name)
  state.update(status='READY_PRIVATE_ORIGINAL_MONOCLE137_BATCHELOR120_NOT_ANALYSIS',rscript=linux(prefix/'bin/Rscript'),libraries=[linux(lib),linux(prefix/'lib/R/library')],sources={name:linux(source/name) for name in ['monocle3','batchelor']},readiness=pin(out/'READINESS.json'),base_pins=base_pins)
 except Exception as e:
  state.update(cause=repr(e),traceback=traceback.format_exc())
  if not isinstance(e,OSError):state['status']='FAIL2_MONOCLE137_BATCHELOR120_RUNTIME_SOURCE_INTEGRITY'
 if owned:save(out/'RESULT.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
 print(state['status'],state.get('cause',''),'\n'+ACTION+'\nWarnings: '+str(out/'WARNINGS.md'),flush=True);return 0 if state['status'].startswith('READY_') else 2 if state['status'].startswith('FAIL2') else 3
if __name__=='__main__':raise SystemExit(main())
