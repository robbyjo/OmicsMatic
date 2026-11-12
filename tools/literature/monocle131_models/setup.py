#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a NEW private data/source layer over the locked R4.3/Monocle3 1.3.1 dependency base."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,os,shutil,subprocess,tarfile,traceback,urllib.request
ROOT=Path(__file__).resolve().parents[3];MODULE=Path(__file__).resolve().parent
PROFILE=ROOT/'src/main/resources/org/jlinalg/omics/monocle131-models/source-profile.json'
GENOME=dict(bytes=12284235,sha256='311fe287842e6161f24fc1faaa824a6d5ce88afcbf4c0203f5e309456e667bc2')
GENOME_URL='https://bioconductor.org/packages/3.18/data/annotation/src/contrib/GenomeInfoDbData_1.2.11.tar.gz'
ACTION='Required action: retain RESULT.json, WARNINGS.md and logs. Supply the existing locked private R4.3.3/Monocle3 1.3.1 base from tools/literature/monocle133/setup_reference.py. Select --allow-install only where permitted; provide exact --source-root and --genome-data-package or permit their pinned downloads with --allow-download. Missing/forbidden prerequisites are SKIP3 and dependent analysis must be skipped. Source/hash/profile contradictions are FAIL2. Retry NEW paths; never modify old libraries, patch scientific source or substitute another Monocle edition. Readiness is not an analysis result.'
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
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--base',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--source-root',type=Path);p.add_argument('--genome-data-package',type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--distribution',default='JLinAlg-Omics-SAIGE-20261026');a=p.parse_args();out=a.out.resolve();owned=False;state=dict(status='SKIP3_MONOCLE131_MODEL_RUNTIME_UNAVAILABLE',analysis_admitted=False,required_action=ACTION)
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') or out.exists():raise ValueError('Require NEW output inside build/literature-review/monocle-runtime')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise PermissionError('Required private installation/copy is not authorized; analysis skipped')
  profile=json.loads(PROFILE.read_bytes());base=a.base.resolve();receipt=json.loads((base/'RESULT.json').read_bytes())
  if receipt['status']!='INSTALLED_PRIVATE_R43_MONOCLE131_DEPENDENCY_BASE_NOT_133_ANALYSIS':raise ValueError('Base is not the exact historical R4.3/Monocle1.3.1 dependency layer')
  prefix=base/'.pixi/envs/default';locked=ROOT/'tools/literature/monocle133/reference-pixi.lock'
  if pin(base/'pixi.lock')!=pin(locked):raise ValueError('Historical dependency lock differs')
  base_pins={str(f):pin(f) for f in [base/'pixi.lock',prefix/'bin/Rscript']}
  def fetch(url,target,wanted):
   if not a.allow_download:raise PermissionError('Needed download not permitted: '+url)
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-Monocle131-runtime'}),timeout=90) as r,target.open('xb') as f:
    total=0
    for b in iter(lambda:r.read(1048576),b''):
     total+=len(b)
     if total>wanted['bytes']:raise ValueError('Pinned source download exceeds exact size')
     f.write(b)
   if pin(target)!=wanted:raise ValueError('Pinned source download integrity differs: '+url)
  source=out/'source';source.mkdir()
  if a.source_root:
   donor=a.source_root.resolve()
   for n,wanted in profile['source_members'].items():
    if pin(donor/n)!=wanted:raise ValueError('Original source member differs: '+n)
    target=source/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(donor/n,target)
  else:
   download=json.loads((MODULE/'source-download.json').read_bytes());archive=out/'monocle3-source.tar.gz';fetch(download['url'],archive,download['archive']);found=set()
   with tarfile.open(archive,'r:gz') as t:
    for item in t.getmembers():
     if item.isdir():continue
     path=PurePosixPath(item.name);parts=path.parts
     if not item.isfile() or item.issym() or item.islnk() or path.is_absolute() or len(parts)<2 or parts[0]!='monocle3-'+profile['commit'] or '..' in parts or '\\' in item.name:raise ValueError('Unsafe original source archive member')
     name=PurePosixPath(*parts[1:]).as_posix()
     if name not in profile['source_members'] or name in found or item.size!=profile['source_members'][name]['bytes']:raise ValueError('Unexpected complete original source member')
     found.add(name);target=source/name;target.parent.mkdir(parents=True,exist_ok=True)
     with t.extractfile(item) as f:raw=f.read(16777217)
     target.write_bytes(raw)
   if found!=set(profile['source_members']):raise ValueError('Incomplete original source archive population')
  for n,wanted in profile['source_members'].items():
   if pin(source/n)!=wanted:raise ValueError('Captured original source differs: '+n)
  lib=out/'R-lib';lib.mkdir()
  for name in ['home','tmp','cache']:(out/name).mkdir()
  environment=['/usr/bin/env','PATH='+linux(prefix/'bin')+':/usr/bin:/bin','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache'),'R_ENVIRON_USER=/dev/null','R_PROFILE_USER=/dev/null','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1','LANG=C','LC_ALL=C']
  def run(label,cmd):
   command=(['wsl.exe','-d',a.distribution,'--user','root','--exec'] if os.name=='nt' else [])+['/usr/bin/timeout','--signal=TERM','--kill-after=15','600',*environment,*map(str,cmd)];r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=660);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
   if r.returncode:raise OSError('Required installation/readiness unavailable at '+label+'; read complete retained logs')
  if a.genome_data_package:
   donor=a.genome_data_package.resolve();description=(donor/'DESCRIPTION').read_text(encoding='utf8')
   if '\nVersion: 1.2.11\n' not in '\n'+description:raise ValueError('GenomeInfoDbData1.2.11 is required')
   pins={p.relative_to(donor).as_posix():pin(p) for p in donor.rglob('*') if p.is_file()};target=lib/'GenomeInfoDbData'
   for n,wanted in pins.items():
    q=target/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(donor/n,q)
    if pin(q)!=wanted or pin(donor/n)!=wanted:raise ValueError('Data-package source changed during capture')
   save(out/'DATA-DONOR.json',dict(path=str(donor),files=pins))
  else:
   archive=out/'GenomeInfoDbData_1.2.11.tar.gz';fetch(GENOME_URL,archive,GENOME);run('install-original-data',[linux(prefix/'bin/R'),'CMD','INSTALL','--library='+linux(lib),linux(archive)])
  run('check-original-namespace',[linux(prefix/'bin/Rscript'),'--vanilla',linux(MODULE/'check_runtime.R'),linux(out),linux(source),linux(PROFILE),linux(lib),linux(prefix/'lib/R/library')])
  ready=json.loads((out/'READINESS.json').read_bytes())
  if ready['status']!='READY_ORIGINAL_MONOCLE131_MODEL_NAMESPACE_NOT_ANALYSIS':raise ValueError('Exact original namespace readiness did not pass')
  if any(pin(Path(n))!=wanted for n,wanted in base_pins.items()):raise ValueError('Existing dependency layer changed')
  state.update(status='READY_PRIVATE_ORIGINAL_MONOCLE131_MODEL_RUNTIME_NOT_ANALYSIS',base_pins=base_pins,readiness=pin(out/'READINESS.json'),rscript=linux(prefix/'bin/Rscript'),libraries=[linux(lib),linux(prefix/'lib/R/library')],source_root=linux(source),source_members=profile['source_members'])
 except Exception as e:
  state.update(cause=repr(e),traceback=traceback.format_exc())
  if not isinstance(e,OSError):state['status']='FAIL2_MONOCLE131_MODEL_RUNTIME_INTEGRITY'
 if owned:save(out/'RESULT.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
 print(state['status'],state.get('cause',''),'\n'+ACTION+'\nWarnings: '+str(out/'WARNINGS.md'),flush=True);return 0 if state['status'].startswith('READY_') else 2 if state['status'].startswith('FAIL2') else 3
if __name__=='__main__':raise SystemExit(main())
