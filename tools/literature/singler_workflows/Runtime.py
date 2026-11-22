# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source identities and complete inventories for original SingleR releases."""
from pathlib import Path
import hashlib,json,os,stat

HERE=Path(__file__).resolve().parent
CORE=['Runtime.py','AuditNamespace.R','Worker.R','run.py','adopt_runtime.py','install_runtime.py','source-profiles.json']
ACTION=('SKIP3 skips dependent analysis when the exact permitted source, runtime, dependency, '
 'installation, download or Linux execution requirement is unavailable. FAIL2 admits no complete result, '
 'including recording failure after an original return. Read WARNINGS.md, FAILURE.json, '
 'original/R-status.json, complete-original-conditions.rds and both complete raw consoles. '
 'Preserve original inputs, complete original returns, conditions, RNG, literal identifiers, NA/NaN/Inf '
 'and partial files. Resolve the named cause and retry NEW writable paths. '
 'Select the exact intended release; the paper literal "2.41" is unresolved and is never normalized to 2.4.1. '
 'References and labels must be supplied explicitly as trusted original R objects; no current online '
 'reference is downloaded or substituted during analysis. RDS inputs are executable trusted inputs, not a sandbox. '
 'Use of complete source-built annotation calls does not establish the authors\' reference snapshot, '
 'settings, manual decisions, environment or reproduction of a paper. The full 250-paper goal remains open.')

class Unavailable(OSError):pass

def pin(path):
    d=hashlib.sha256();size=0
    with Path(path).open('rb') as s:
        for chunk in iter(lambda:s.read(1048576),b''):d.update(chunk);size+=len(chunk)
    return dict(bytes=size,sha256=d.hexdigest())

def unique(pairs):
    result={}
    for k,v in pairs:
        if k in result:raise ValueError('Duplicate JSON key: '+k)
        result[k]=v
    return result

def load(path):return json.loads(Path(path).read_bytes(),object_pairs_hook=unique)

def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as s:
        s.write(json.dumps(value,ensure_ascii=False,allow_nan=False,indent=2)+'\n')

def profiles():return load(HERE/'source-profiles.json')['profiles']

def profile(edition):
    rows=profiles()
    if edition not in rows:raise Unavailable('No qualified original source profile for literal edition '+repr(edition)+'; no version substitution permitted')
    return rows[edition]

def sources(edition):
    profile(edition)
    names=CORE+['dependencies/'+edition+'.toml','dependencies/'+edition+'.lock']
    return {n:pin(HERE/n) for n in names}

def inventory(root):
    root=Path(root)
    if not root.is_dir() or root.is_symlink():raise Unavailable('Required regular directory absent: '+str(root))
    result={}
    def failed(e):raise e
    for parent,dirs,files in os.walk(root,followlinks=False,onerror=failed):
        for n in sorted(dirs+files):
            p=Path(parent)/n;s=p.lstat();rel=p.relative_to(root).as_posix()
            if stat.S_ISLNK(s.st_mode):result[rel]=dict(kind='link',target=os.readlink(p))
            elif stat.S_ISREG(s.st_mode):result[rel]=dict(kind='file',executable=bool(s.st_mode&0o111),**pin(p))
            elif not stat.S_ISDIR(s.st_mode):raise ValueError('Unsupported protected file kind: '+str(p))
    return result

def source_tree(edition,paths):
    selected=profile(edition)['packages']
    if set(paths)!=set(selected):raise ValueError('All declared complete original source paths are required')
    for package,entry in selected.items():
        actual=inventory(paths[package])
        if any(v['kind']!='file' for v in actual.values()):raise ValueError('Original source tree must contain regular files only: '+package)
        pins={n:{k:v[k] for k in ['bytes','sha256']} for n,v in actual.items()}
        expected={n:{k:v[k] for k in ['bytes','sha256']} for n,v in entry['original_regular_members'].items()}
        if pins!=expected:raise ValueError('Complete original source tree differs: '+package)

def check_output(out,protected):
    out=Path(out).absolute()
    if out!=out.resolve():raise ValueError('Use a canonical NEW output path')
    for original in protected:
        p=Path(original).resolve()
        if out.is_relative_to(p) or p.is_relative_to(out):raise ValueError('Output overlaps a protected path: '+str(p))
    return out

def environment(ready,out):
    prefix=Path(ready['r_prefix']);out=Path(out)
    env={k:v for k,v in os.environ.items() if k not in ['R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE',
      'LD_LIBRARY_PATH','LD_PRELOAD','PYTHONHOME','PYTHONPATH']}
    env.update(HOME=str(out/'home'),TMPDIR=str(out/'tmp'),R_LIBS_USER=ready['library'],
      R_LIBS_SITE=str(prefix/'lib/R/library'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
      PATH=str(prefix/'bin')+os.pathsep+env.get('PATH',''),LD_LIBRARY_PATH=str(prefix/'lib'),
      OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',RCPP_PARALLEL_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
    return env

def check(ready,edition):
    selected=profile(edition)
    if ready.get('schema')!='omicsmatic-original-singler-provider-v1' or ready.get('edition')!=edition:
        raise ValueError('Provider edition/schema differs from the explicit selected edition')
    if ready['adapter_sources']!=sources(edition):raise ValueError('Provider binds different adapter code; audit a NEW provider')
    source_tree(edition,ready['source_paths'])
    provider=Path(ready['provider']);workspace=Path(ready['workspace'])
    completed=load(provider/'COMPLETED.json')
    if completed['status']!='PASS_ORIGINAL_SINGLER_PROVIDER_NOT_ANALYSIS' or completed['provider']!=pin(provider/'READY.json'):
        raise Unavailable('Provider audit did not complete')
    for suffix in ['toml','lock']:
        if pin(workspace/'runtime'/('pixi.'+suffix))!=pin(HERE/'dependencies'/(edition+'.'+suffix)):
            raise ValueError('Selected exact dependency manifest/lock differs')
    for name,location in [('r-prefix-files',ready['r_prefix']),('private-library-files',ready['library'])]:
        if pin(provider/(name+'.json'))!=ready[name] or inventory(location)!=load(provider/(name+'.json')):
            raise ValueError('Complete protected runtime inventory differs: '+name)
    return selected
