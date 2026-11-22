# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact original sources and private HICAT/RANN runtime inventories."""
from pathlib import Path
import hashlib,json,os,stat

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
CORE=['Runtime.py','AuditNamespace.R','Worker.R','run.py','adopt_runtime.py','install_runtime.py',
  'source-profile.json','dependency-pixi.toml','dependency-pixi.lock']
ACTION=('Review WARNINGS.md, complete original R conditions and every raw console. '
 'Missing or forbidden exact source/dependency/permission/install/download/Linux execution requirements '
 'SKIP3 skip dependent analysis. FAIL2 admits no complete result, including recording failure. '
 'Preserve original inputs, complete return/conditions/RNG, literal IDs, NA/NaN/Inf and partials; '
 'resolve the named cause and retry NEW writable paths. Use the explicitly selected original 1.0.9 source '
 'and private source-built RANN 2.6.1; no fallback or source repair occurs. '
 'HICAT DESCRIPTION and LICENSE have conflicting labels; preserve both and keep the runtime/source private '
 'under their original terms. This adapter authorizes no source-derived native port or public runtime/source bundling. '
 'The separate Java selector implements only the factual strict score>0.3 rule on supplied values. '
 'Author commit, gene panel, caller options and environment are unknown; the 62 housekeeping genes describe a QC score that '
 'is a separate paper procedure. No whole-tool, biological, paper-reproduction or full 250-paper completion claim.')

class Unavailable(OSError):pass
def pin(path):
    digest=hashlib.sha256();size=0
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda:stream.read(1048576),b''):digest.update(chunk);size+=len(chunk)
    return dict(bytes=size,sha256=digest.hexdigest())
def unique(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate JSON key: '+key)
        result[key]=value
    return result
def load(path):return json.loads(Path(path).read_bytes(),object_pairs_hook=unique)
def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(json.dumps(value,ensure_ascii=False,allow_nan=False,indent=2)+'\n')
def profile():return load(HERE/'source-profile.json')
def sources():return {name:pin(HERE/name) for name in CORE}
def inventory(root):
    root=Path(root)
    if not root.is_dir() or root.is_symlink():raise Unavailable('Required regular directory absent: '+str(root))
    result={}
    def failed(error):raise error
    for parent,dirs,files in os.walk(root,followlinks=False,onerror=failed):
        for name in sorted(dirs+files):
            path=Path(parent)/name;info=path.lstat();relative=path.relative_to(root).as_posix()
            if stat.S_ISLNK(info.st_mode):result[relative]=dict(kind='link',target=os.readlink(path))
            elif stat.S_ISREG(info.st_mode):result[relative]=dict(kind='file',executable=bool(info.st_mode&0o111),**pin(path))
            elif not stat.S_ISDIR(info.st_mode):raise ValueError('Unsupported file kind: '+str(path))
    return result
def source_tree(source_paths):
    packages=profile()['packages']
    if set(source_paths)!=set(packages):raise ValueError('Both complete original source paths are required')
    for name,entry in packages.items():
        actual=inventory(source_paths[name])
        if any(v['kind']!='file' for v in actual.values()):raise ValueError('Original sources must be regular files')
        identities={n:{k:v[k] for k in ['bytes','sha256']} for n,v in actual.items()}
        expected={n:{k:v[k] for k in ['bytes','sha256']} for n,v in entry['original_regular_members'].items()}
        if identities!=expected:raise ValueError('Complete original source differs: '+name)
def check_output(out,protected):
    out=Path(out).absolute()
    if out!=out.resolve():raise ValueError('Use a canonical NEW output path')
    for original in protected:
        path=Path(original).resolve()
        if out.is_relative_to(path) or path.is_relative_to(out):raise ValueError('Output overlaps a protected path: '+str(path))
    return out
def environment(ready,out):
    out=Path(out);prefix=Path(ready['r_prefix'])
    env={k:v for k,v in os.environ.items() if k not in ['R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE',
      'LD_LIBRARY_PATH','LD_PRELOAD','PYTHONHOME','PYTHONPATH']}
    env.update(HOME=str(out/'home'),TMPDIR=str(out/'tmp'),R_LIBS_USER=ready['library'],
      R_LIBS_SITE=str(prefix/'lib/R/library'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
      PATH=str(prefix/'bin')+os.pathsep+env.get('PATH',''),LD_LIBRARY_PATH=str(prefix/'lib'),
      OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',RCPP_PARALLEL_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
    return env
def check(ready):
    if ready.get('schema')!='omicsmatic-private-original-hicat109-provider-v1':raise ValueError('Unsupported private HICAT provider')
    if ready['adapter_sources']!=sources():raise ValueError('Provider binds different code; adopt a NEW provider')
    source_tree(ready['source_paths'])
    workspace=Path(ready['workspace']);provider=Path(ready['provider'])
    completion=load(provider/'COMPLETED.json')
    if completion['status']!='PASS_PRIVATE_ORIGINAL_HICAT109_PROVIDER_NOT_ANALYSIS' or completion['provider']!=pin(provider/'READY.json'):
        raise Unavailable('Provider adoption did not complete')
    for filename in ['toml','lock']:
        if pin(workspace/'runtime'/('pixi.'+filename))!=pin(HERE/('dependency-pixi.'+filename)):
            raise ValueError('Exact selected dependency lock/manifest differs')
    for name,location in [('r-prefix-files',ready['r_prefix']),('private-library-files',ready['library'])]:
        if pin(provider/(name+'.json'))!=ready[name] or inventory(location)!=load(provider/(name+'.json')):
            raise ValueError('Complete protected runtime inventory differs: '+name)
