# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact private original runtime identities; no installation during analysis."""
from pathlib import Path
import hashlib,json,os,stat

HERE=Path(__file__).resolve().parent
CORE=['Runtime.py','adopt_runtime.py','run.py','Worker.R','AuditRuntime.R','CompareOriginal.R','source-profile.json','reference-pixi.toml','reference-pixi.lock']
ACTION=('Review every raw console and complete original R condition; raw/native diagnostic totals are unavailable, never zero. '
 'Use the unchanged original DoubletFinder 2.0.6 source and the declared private Linux/WSL R 4.5.3 dependency profile. '
 'Installation/download must be permitted separately; analysis never installs software. Missing or forbidden requirements SKIP3 skip dependent analysis. '
 'FAIL2 admits no complete result. Resolve the named cause and retry NEW writable output paths, retaining all original inputs, complete RDS objects, logs and available partial files. '
 'RDS requests must be trusted. Full-object verification requires the exact R runtime and strict CompareOriginal.R; Seurat closures are not flattened into portable numeric summaries. '
 'Do not repair cells, identifiers, NA/NaN/Inf, substitute an author edition, change old hashes or waive output differences. '
 'The selected profile is not an author environment. The modified scrattch.hicat method and the reported DoubletFinder 3.0 edition remain separate unresolved source audits. '
 'No whole-package, biological, paper-reproduction or full-250-paper completion claim is made.')

class Unavailable(OSError):pass

def pin(path):
 h=hashlib.sha256();size=0
 with Path(path).open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);size+=len(block)
 return dict(bytes=size,sha256=h.hexdigest())

def save(path,value):Path(path).write_text(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8',newline='\n')

def unique(pairs):
 out={}
 for key,value in pairs:
  if key in out:raise ValueError('Duplicate JSON key: '+key)
  out[key]=value
 return out

def load(path):return json.loads(Path(path).read_bytes(),object_pairs_hook=unique)

def inventory(root):
 root=Path(root);out={}
 if not root.is_dir() or root.is_symlink():raise Unavailable('Exact regular runtime directory missing: '+str(root))
 for parent,dirs,files in os.walk(root,followlinks=False):
  for name in sorted(dirs+files):
   path=Path(parent)/name;relative=path.relative_to(root).as_posix();mode=path.lstat().st_mode
   if stat.S_ISLNK(mode):out[relative]=dict(kind='link',target=os.readlink(path))
   elif stat.S_ISREG(mode):out[relative]=dict(kind='file',**pin(path))
   elif not stat.S_ISDIR(mode):raise ValueError('Unsupported runtime entry: '+str(path))
 return out

def sources():return {name:pin(HERE/name) for name in CORE}

def source_tree(source):
 wanted=load(HERE/'source-profile.json')['complete_original_regular_members']
 actual=inventory(source)
 if actual!={name:dict(kind='file',**value) for name,value in wanted.items()}:raise ValueError('Original complete source tree differs')

def environment(ready,out):
 env=dict(os.environ,PATH=str(Path(ready['r_prefix'])/'bin')+os.pathsep+os.environ.get('PATH',''),R_LIBS_USER=ready['library'],R_LIBS_SITE=ready['base_library'],R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',HOME=str(out/'home'),TMPDIR=str(out/'tmp'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',VECLIB_MAXIMUM_THREADS='1',BLIS_NUM_THREADS='1',R_DEFAULT_INTERNET_TIMEOUT='30')
 env.pop('R_HOME',None)
 return env

def check(ready):
 if ready['runner_sources']!=sources():raise ValueError('Qualified runner source differs; retain the old provider and qualify NEW source/provider paths')
 for name,key in [('pixi.toml','manifest'),('pixi.lock','lock')]:
  if pin(Path(ready['prefix'])/name)!=ready[key] or pin(HERE/('reference-'+name))!=ready[key]:raise ValueError('Selected original dependency lock or manifest differs')
 source_tree(Path(ready['source']))
 for key in ['r_prefix','library']:
  record=Path(ready['provider'])/(key+'-files.json')
  if pin(record)!=ready['inventory_files'][key]:raise ValueError('Saved original runtime inventory differs: '+key)
  if inventory(Path(ready[key]))!=load(record):raise ValueError('Installed original runtime files differ: '+key)
 if not os.access(Path(ready['r_prefix'])/'bin/Rscript',os.X_OK):raise Unavailable('Original Rscript executable unavailable')

