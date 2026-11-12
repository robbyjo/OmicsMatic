#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a separate R4.3/Monocle1.3.1 dependency base for exact historical Monocle3 source workflows."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,traceback
ROOT=Path(__file__).resolve().parents[3]
ACTION='Required action: retain this directory, pixi.toml/pixi.lock and installer stdout/stderr. Provision only permitted dependencies into NEW private paths. If download/install/execution is unavailable or forbidden, skip dependent historical Monocle analysis. This dependency base contains original Monocle3 1.3.1 and is NOT a 1.3.3 graph-learning runtime; install and validate the separately pinned original 1.3.3 source before that analysis. Never substitute editions or patch original scientific source to force success.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def linux(p):
 p=p.resolve();assert p.drive.lower()=='d:';return '/mnt/d/'+p.relative_to(Path('D:/')).as_posix()
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir();state=dict(status='SKIP3_HISTORICAL_MONOCLE_DEPENDENCY_BASE_UNAVAILABLE',analysis_admitted=False,graph133_runtime_admitted=False,required_action=ACTION)
 try:
  if not a.allow_install:raise PermissionError('Installation not authorized; dependent analysis skipped')
  if shutil.disk_usage(out).free<8*1024**3:raise OSError('Reserve at least8GiB for the independent environment')
  bootstrap=ROOT/'build/literature-review/saige-runtime/pixi-bootstrap-1/pixi';bootstrap_pin=pin(bootstrap);manifest=out/'pixi.toml'
  manifest.write_text('''[workspace]
name = "private-historical-monocle3-reference"
version = "0.1.0"
channels = ["conda-forge", "bioconda"]
platforms = ["linux-64"]

[dependencies]
r-base = "==4.3.3"
r-monocle3 = {version = "==1.3.1", build = "r43h4ac6f70_0", channel = "bioconda"}
r-igraph = ">=1.3.5,<2.1"
r-jsonlite = "*"
r-rhpcblasctl = "*"
r-future = ">=1.23"
r-digest = ">=0.6.28"
r-openssl = "*"
r-rcpphnsw = ">=0.3.0"
r-terra = ">=1.4_17"
cxx-compiler = "*"
make = "*"
''',encoding='utf8',newline='\n')
  reference_manifest=Path(__file__).with_name('reference-pixi.toml');reference_lock=Path(__file__).with_name('reference-pixi.lock');lock_pin=pin(reference_lock)
  if pin(manifest)!=pin(reference_manifest):raise ValueError('Dependency manifest differs from the qualified reference recipe')
  shutil.copyfile(reference_lock,out/'pixi.lock')
  for name in ['home','pixi-home','pixi-cache','tmp']:(out/name).mkdir()
  save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),bootstrap=bootstrap_pin,manifest=pin(manifest),target_source_version='1.3.3',dependency_package_version='1.3.1',analysis_admitted=False,graph133_runtime_admitted=False))
  command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','1800','/usr/bin/env','PIXI_NO_PATH_UPDATE=1','HOME='+linux(out/'home'),'PIXI_HOME='+linux(out/'pixi-home'),'PIXI_CACHE_DIR='+linux(out/'pixi-cache'),'TMPDIR='+linux(out/'tmp'),linux(bootstrap),'install','--locked','--manifest-path',linux(manifest)]
  started=time.time()
  with (out/'installer.stdout').open('xb') as so,(out/'installer.stderr').open('xb') as se:
   process=subprocess.Popen(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'PROCESS-STARTED.json',dict(command=command,pid=process.pid,started_unix=started));code=process.wait(timeout=1860)
  save(out/'process.json',dict(command=command,pid=process.pid,exit_code=code,elapsed_seconds=time.time()-started));assert pin(bootstrap)==bootstrap_pin
  if code:raise OSError('Private dependency installation failed or was unavailable; inspect complete retained installer logs')
  if not (out/'.pixi/envs/default/bin/Rscript').is_file():raise OSError('Installer returned success without the required Rscript')
  if pin(out/'pixi.lock')!=lock_pin or pin(reference_lock)!=lock_pin:raise ValueError('Qualified dependency lock changed during installation')
  state.update(status='INSTALLED_PRIVATE_R43_MONOCLE131_DEPENDENCY_BASE_NOT_133_ANALYSIS',lock=pin(out/'pixi.lock'),manifest=pin(manifest),rscript=linux(out/'.pixi/envs/default/bin/Rscript'))
 except Exception as error:
  state.update(cause=repr(error),traceback=traceback.format_exc())
  if not isinstance(error,OSError):state['status']='FAIL2_HISTORICAL_MONOCLE_DEPENDENCY_SETUP_INTEGRITY'
 save(out/'RESULT.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('cause',''),'\n'+ACTION+'\nWarnings: '+str(out/'WARNINGS.md'),flush=True);return 0 if state['status'].startswith('INSTALLED_') else 2 if state['status'].startswith('FAIL2') else 3
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/install integrity checks')
 raise SystemExit(main())
