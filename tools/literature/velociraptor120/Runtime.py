# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Private original-runtime identities; readiness is not analysis qualification."""
from pathlib import Path
import hashlib,json,os,stat

HERE=Path(__file__).resolve().parent
PROFILE=HERE/'source-profile.json'
ACTION=('Required action: provision the unchanged original velociraptor 1.20.0 in a private '
 'Linux/WSL R 4.5.3 library with its install-time scvelo 0.3.3 Python 3.12.10 backend. '
 'Use the declared NumPy 2.2.6/Pandas 2.2.3/SciPy 1.16.3 development dependency profile; it is not an '
 'author environment. Missing or forbidden prerequisites/execution SKIP3 skip dependent '
 'analysis. FAIL2 admits no complete result. Preserve original inputs, full RDS objects, '
 'value trees, conditions, Python errors, raw consoles and every available partial file; '
 'resolve the named cause and retry NEW paths. Never discard cells/genes, repair values '
 'or identifiers, waive differences, or change saved hashes to obtain a pass. Review all '
 'warnings and nonfinite values. Earlier SciPy 1.18.1 results have retained pseudotime '
 'differences: its original eigensolver uses OS entropy without a supplied rng; old '
 'results are not overwritten and no equality requirement is waived. Native R '
 'stack-imbalance diagnostics have an unresolved '
 'cause; R condition totals are not complete raw-console diagnostic totals. Trusted R '
 'scripts run with account permissions; the adapter is not an OS sandbox. No paper '
 'settings or convergence are inferred, and the full 250-paper objective remains open.')

class Unavailable(OSError):pass

def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb') as stream:
  for raw in iter(lambda:stream.read(1048576),b''):digest.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=digest.hexdigest())

def save(path,value):
 Path(path).write_text(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8',newline='\n')

def unique(pairs):
 result={}
 for key,value in pairs:
  if key in result:raise ValueError('Duplicate JSON key: '+key)
  result[key]=value
 return result

def load(path):
 return json.loads(Path(path).read_bytes(),object_pairs_hook=unique,
  parse_constant=lambda token:(_ for _ in ()).throw(ValueError('Nonfinite JSON token: '+token)))

def regular(path):
 path=Path(path)
 if not path.is_file() or path.is_symlink():raise Unavailable('Regular prerequisite unavailable: '+str(path))

def same(path,expected):
 regular(path)
 if pin(path)!=expected:raise ValueError('Protected original/source bytes changed: '+str(path))

def inventory(roots):
 """Hash every prefix file/link, including bytecode that Python could execute."""
 roots={name:Path(path) for name,path in roots.items()};result={}
 for name,root in roots.items():
  if not root.is_dir() or root!=root.resolve():raise Unavailable('Canonical complete runtime root missing: '+str(root))
  files={};pending=[root]
  while pending:
   folder=pending.pop()
   for entry in os.scandir(folder):
    path=Path(entry.path);relative=path.relative_to(root).as_posix()
    mode=entry.stat(follow_symlinks=False).st_mode
    if stat.S_ISLNK(mode):
     target=path.resolve(strict=False)
     row=dict(kind='symlink',target=os.readlink(path),resolved=str(target))
     if not target.exists():row['dangling']=True
     elif target.is_file():row['target_file']=pin(target)
     elif target.is_dir():
      if not any(target.is_relative_to(r) for r in roots.values()):raise Unavailable('Runtime directory link leaves fully inventoried roots: '+str(path))
      row['target_directory_covered']=True
     else:raise Unavailable('Runtime link target is not a regular file/directory: '+str(path))
     files[relative]=row
    elif stat.S_ISDIR(mode):pending.append(path)
    elif stat.S_ISREG(mode):files[relative]=dict(kind='file',**pin(path))
    else:raise Unavailable('Unsupported runtime filesystem node: '+str(path))
   # Every candidate is already provisioned; analysis never invokes an installer.
  result[name]=files
 return result

def verify_source(source):
 source=Path(source);profile=load(PROFILE)
 observed={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file() or p.is_symlink()}
 if set(observed)!=set(profile['R_source_files']):raise ValueError('Complete original R source population differs')
 for name,wanted in profile['R_source_files'].items():same(observed[name],wanted)

def environment(ready,out,*,temporary_directory=None):
 out=Path(out);prefix=Path(ready['r_prefix']);library=Path(ready['library'])
 env={key:value for key,value in os.environ.items() if not key.startswith(('R_','PYTHON','BASILISK_','RETICULATE_','PIP_','UV_','LD_')) and key not in ['VIRTUAL_ENV','CONDA_PREFIX','R_HOME']}
 env.update(PATH=str(prefix/'bin')+':'+str(Path(ready['backend'])/'bin')+':/usr/bin:/bin',
  R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
  R_LIBS=str(library)+':'+ready['base_library'],R_LIBS_USER=str(library),R_LIBS_SITE='',
  HOME=str(out/'owned-home'),TMPDIR=str(temporary_directory if temporary_directory is not None else out/'tmp'),MPLCONFIGDIR=str(out/'matplotlib-cache'),NUMBA_CACHE_DIR=str(out/'numba-cache'),
  PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',BASILISK_USE_SYSTEM_DIR='1',BASILISK_NO_DESTROY='1',
  BASILISK_NO_PYENV='1',BASILISK_CUSTOM_PYTHON_3_12_10=ready['standalone_python'],
  BASILISK_EXTERNAL_DIR=str(out/'unused-external-cache'),PIP_NO_INDEX='1',PIP_DISABLE_PIP_VERSION_CHECK='1',
  LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMBA_NUM_THREADS='1')
 return env

def provider(directory):
 directory=Path(directory);regular(directory/'READY.json');ready=load(directory/'READY.json')
 if ready.get('status')!='READY_PRIVATE_ORIGINAL_VELOCIRAPTOR120_NOT_ANALYSIS' or ready.get('source_profile')!=pin(PROFILE):raise ValueError('Original velociraptor provider identity differs')
 required={'r_prefix','library','source_root','backend','standalone_python','rscript','base_library'}
 if not required.issubset(ready):raise ValueError('Incomplete original runtime paths')
 for key in required:
  path=Path(ready[key])
  # Python's executable is intentionally an original venv link; its target is inventoried.
  if not path.is_absolute():raise ValueError('Absolute original runtime paths required')
 if Path(ready['rscript'])!=Path(ready['r_prefix'])/'bin/Rscript' or Path(ready['base_library'])!=Path(ready['r_prefix'])/'lib/R/library' or Path(ready['backend'])!=Path(ready['library'])/'velociraptor/basilisk/env':raise ValueError('Original install-time runtime layout differs')
 for exe in [ready['rscript'],ready['standalone_python'],str(Path(ready['backend'])/'bin/python')]:
  if not Path(exe).is_file() or not os.access(exe,os.X_OK):raise Unavailable('Required original executable unavailable: '+exe)
 for name,wanted in ready['preparation_sources'].items():same(HERE/name,wanted)
 verify_source(ready['source_root'])
 observed=inventory(ready['runtime_roots'])
 if observed!=ready['runtime_inventory']:raise ValueError('Complete original runtime file/link population or content differs')
 same(directory/'readiness.json',ready['readiness'])
 return ready
