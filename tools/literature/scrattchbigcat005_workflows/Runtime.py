# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Unchanged BIGCAT sources, explicit runtime profiles and complete local inventories."""
from pathlib import Path
import copy,hashlib,json,os,stat

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
CORE=['Runtime.py','AuditRuntime.R','AuditEntry.R','Request.R','Worker.R','run.py','adopt_runtime.py',
      'install_runtime.py','profiles.json','reference2023-pixi.toml','reference2023-pixi.lock',
      'reference2022-pixi.toml','reference2022-pixi.lock','OriginalCalls.R','CrossValidation.R','Constellation.R','OriginalAssets.R']
ACTION=(
 'Review WARNINGS.md, every raw console and complete original R conditions. '
 'Missing or forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result, '
 'including final recording failure. Resolve the named cause and retry NEW writable output/workspace paths, '
 'retaining inputs and partial files. Choose the source profile explicitly; no fallback or source repair occurs. '
 'The authors exact Git commits, P058 package edition and caller environments are unknown. '
 'The earlier profile lacks impute_cross_big and needs explicit caller sets for harmonization; '
 'the later profile retains its original harmonization indexing error. Review source warnings and all '
 'NA/NaN/Inf values. The five-SPN selection, when requested, follows integration at a caller-chosen '
 'hierarchy level; it is not a clustering parameter or a reproduced correspondence analysis. '
 'Raw/native diagnostic totals and live-pointer persistence are not qualified. '
 'Complete FBM and plot R objects are retained, but cross-process class-definition or cyclic-environment '
 'identity is not guaranteed. Review the retained comparison differences and file-backed payloads. '
 'No whole-tool, biological, paper-reproduction or full-250-paper completion claim is made.')

class Unavailable(OSError):pass

def pin(path):
    digest=hashlib.sha256();size=0
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda:stream.read(1048576),b''):
            digest.update(chunk);size+=len(chunk)
    return dict(bytes=size,sha256=digest.hexdigest())

def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(json.dumps(value,ensure_ascii=False,allow_nan=False,indent=2)+'\n')

def unique(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate JSON key: '+key)
        result[key]=value
    return result

def load(path):return json.loads(Path(path).read_bytes(),object_pairs_hook=unique)

def profiles():
    result=load(HERE/'profiles.json');entry=result['arrow_source_profile']
    path=ROOT/entry['path']
    if pin(path)!={k:entry[k] for k in ['bytes','sha256']}:
        raise ValueError('Pinned original Arrow source declaration differs; do not refresh historical hashes')
    return result

def profile(name):
    selected=profiles()['profiles'].get(name)
    if selected is None:raise ValueError('Unknown explicit BIGCAT source profile: '+str(name))
    return copy.deepcopy(selected)

def source_packages(name):
    declaration=profiles();selected=profile(name);arrow=copy.deepcopy(declaration['arrow_source'])
    arrow['original_members']=load(ROOT/declaration['arrow_source_profile']['path'])['original_regular_members']
    return selected['source_packages']+[arrow]

def sources():
    result={name:pin(HERE/name) for name in CORE}
    declaration=profiles()['arrow_source_profile']
    result['dependency:'+declaration['path']]=pin(ROOT/declaration['path'])
    return result

def walk_error(error):raise error

def inventory(root,links=True):
    root=Path(root)
    if not root.is_dir() or root.is_symlink():raise Unavailable('Required regular directory missing: '+str(root))
    result={}
    for parent,dirs,files in os.walk(root,followlinks=False,onerror=walk_error):
        for name in sorted(dirs+files):
            path=Path(parent)/name;info=path.lstat();relative=path.relative_to(root).as_posix()
            if stat.S_ISLNK(info.st_mode):
                if not links:raise ValueError('Assets must not contain symbolic links: '+str(path))
                result[relative]=dict(kind='link',target=os.readlink(path))
            elif stat.S_ISREG(info.st_mode):
                result[relative]=dict(kind='file',executable=bool(info.st_mode&0o111),**pin(path))
            elif not stat.S_ISDIR(info.st_mode):raise ValueError('Unsupported file kind: '+str(path))
    return result

def source_tree(name,source_paths):
    expected={p['name']:p for p in source_packages(name)}
    if set(source_paths)!=set(expected):raise ValueError('Complete declared source package paths required')
    for package,entry in expected.items():
        actual=inventory(source_paths[package],links=False)
        identities={n:{k:v[k] for k in ['bytes','sha256']} for n,v in actual.items()}
        if identities!=entry['original_members']:
            raise ValueError('Complete unchanged source differs: '+package)

def check_artifacts(prefix,name):
    expected=profile(name)['required_conda_artifacts'];actual={}
    for path in (Path(prefix)/'conda-meta').glob('*.json'):
        row=load(path)
        if row.get('name') in expected:
            if row['name'] in actual:raise ValueError('Duplicate package artifact metadata')
            actual[row['name']]=row
    for package,identity in expected.items():
        if any(actual.get(package,{}).get(k)!=v for k,v in identity.items()):
            raise Unavailable('Exact declared recipe-unpatched artifact required: '+package+' '+identity['build'])

def overlap(a,b):return a.is_relative_to(b) or b.is_relative_to(a)

def check_output(path,protected):
    path=Path(path).absolute()
    if path!=path.resolve() or any(overlap(path,Path(p).resolve()) for p in protected):
        raise ValueError('Use a NEW canonical output separate from every protected path')
    return path

def environment(ready,out):
    prefix=Path(ready['r_prefix']);out=Path(out)
    env=dict(os.environ)
    for key in ['R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE','LD_PRELOAD','LD_LIBRARY_PATH','PYTHONHOME','PYTHONPATH']:
        env.pop(key,None)
    env.update(HOME=str(out/'home'),TMPDIR=str(out/'tmp'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
      R_LIBS_USER=ready['library'],R_LIBS_SITE=str(prefix/'lib/R/library'),
      PATH=str(prefix/'bin')+os.pathsep+env.get('PATH',''),LD_LIBRARY_PATH=str(prefix/'lib'),
      OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',RCPP_PARALLEL_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
    return env

def control(ready,out,**extra):
    selected=profile(ready['profile'])
    return dict(profile=ready['profile'],selected_R=selected['R'],namespace_functions=selected['namespace_functions'],
      operations=selected['operations'],arrow_from_source=selected['arrow_from_source'],
      workflow_operations=selected['workflows'],original_call_helpers=str(HERE/'OriginalCalls.R'),
      cross_validation_helpers=str(HERE/'CrossValidation.R'),constellation_helpers=str(HERE/'Constellation.R'),
      original_asset_helpers=str(HERE/'OriginalAssets.R'),
      library=ready['library'],base_library=str(Path(ready['r_prefix'])/'lib/R/library'),source_paths=ready['source_paths'],
      audit=str(HERE/'AuditRuntime.R'),request_helpers=str(HERE/'Request.R'),**extra)

def check(ready):
    if ready.get('schema')!='omicsmatic-original-bigcat005-provider-v1':raise ValueError('Unsupported BIGCAT provider')
    selected=profile(ready['profile'])
    if ready['adapter_sources']!=sources():raise ValueError('Provider binds different adapter sources; adopt a NEW provider')
    workspace=Path(ready['workspace']);prefix=Path(ready['r_prefix']);library=Path(ready['library'])
    for key,filename in [('manifest',selected['manifest']),('lock',selected['lock'])]:
        installed=workspace/'runtime'/('pixi.toml' if key=='manifest' else 'pixi.lock')
        if pin(installed)!=pin(HERE/filename):raise ValueError('Selected dependency '+key+' differs')
    check_artifacts(prefix,ready['profile']);source_tree(ready['profile'],ready['source_paths'])
    provider=Path(ready['provider'])
    admission=load(provider/'COMPLETED.json')
    if admission['status']!='PASS_ORIGINAL_BIGCAT_RUNTIME_NOT_ANALYSIS' or admission['provider']!=pin(provider/'READY.json'):
        raise Unavailable('Provider adoption did not finish successfully')
    for name,path in [('r-prefix-files',prefix),('library-files',library)]:
        saved=provider/(name+'.json')
        if pin(saved)!=ready[name] or inventory(path)!=load(saved):
            raise ValueError('Complete protected runtime inventory differs: '+name)
