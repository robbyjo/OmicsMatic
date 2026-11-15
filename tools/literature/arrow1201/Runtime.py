# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Identities for a private original R Arrow 12.0.1 runtime; never installs at analysis time."""
from pathlib import Path
import hashlib, json, os, stat

HERE = Path(__file__).resolve().parent
CORE = ['Runtime.py','AuditRuntime.R','AuditEntry.R','Worker.R','run.py','adopt_runtime.py',
        'source-profile.json','reference-pixi.toml','reference-pixi.lock']
ACTION = ('Review WARNINGS.md, raw console logs and complete original R conditions. '
    'SKIP3 means a missing or forbidden prerequisite skipped dependent analysis. '
    'FAIL2 admits no complete result, including any recording failure. Resolve the stated cause '
    'and retry NEW writable paths, retaining original inputs and partial files. '
    'Copyable setup/request/retry commands are in tools/literature/arrow1201/README.md. '
    'Use a permitted Linux/WSL R Arrow 12.0.1 provider with matching Arrow C++ 12.0.1, '
    'the selected locked dependencies, and trusted RDS requests. '
    'Use caller-declared local Parquet assets and explicit query steps; the paper does not report '
    'its exact Arrow calls, storage schema, memory-map settings or query options. '
    'Complete collected R data frames are retained; live Dataset/Table C++ pointers are not persisted. '
    'Keep identifiers, NA/NaN/Inf, upstream errors and source behavior unchanged. '
    'Raw/native diagnostic totals are unavailable, never assumed zero. '
    'The adapter performs no installation or download during analysis. '
    'PyArrow, PacBio Arrow and scrattch.bigcat remain separate interfaces. '
    'No whole-tool, paper-reproduction, biological or full-250-paper completion claim is made.')

class Unavailable(OSError):
    pass

def pin(path):
    digest, size = hashlib.sha256(), 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())

def save(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(value, ensure_ascii=False, allow_nan=False, indent=2)+'\n')

def unique(pairs):
    value = {}
    for key, entry in pairs:
        if key in value:
            raise ValueError('Duplicate JSON key: '+key)
        value[key] = entry
    return value

def load(path):
    return json.loads(Path(path).read_bytes(), object_pairs_hook=unique)

def walk_error(error):
    raise error

def inventory(root, links=True):
    root = Path(root)
    if not root.is_dir() or root.is_symlink():
        raise Unavailable('Required regular directory unavailable: '+str(root))
    value = {}
    for parent, dirs, files in os.walk(root, followlinks=False, onerror=walk_error):
        for name in sorted(dirs+files):
            path = Path(parent)/name
            info = path.lstat()
            relative = path.relative_to(root).as_posix()
            if stat.S_ISLNK(info.st_mode):
                if not links:
                    raise ValueError('Local source/assets must not contain symbolic links: '+str(path))
                value[relative] = dict(kind='link', target=os.readlink(path))
            elif stat.S_ISREG(info.st_mode):
                value[relative] = dict(kind='file', executable=bool(info.st_mode & 0o111), **pin(path))
            elif not stat.S_ISDIR(info.st_mode):
                raise ValueError('Unsupported file kind: '+str(path))
    return value

def sources():
    return {name:pin(HERE/name) for name in CORE}

def source_tree(root):
    expected = load(HERE/'source-profile.json')['original_regular_members']
    actual = inventory(root, links=False)
    identities = {n:{k:v for k,v in entry.items() if k in ['bytes','sha256']} for n,entry in actual.items()}
    if identities != expected:
        raise ValueError('Complete original CRAN Arrow 12.0.1 source differs')

def check_artifacts(prefix):
    """An edition label alone cannot distinguish recipe-patched C++ builds."""
    expected=load(HERE/'source-profile.json').get('required_conda_artifacts')
    if not expected:
        raise Unavailable('Original C++ packaging has not been admitted; skip dependent analysis and retain the candidate evidence')
    actual={}
    for path in (Path(prefix)/'conda-meta').glob('*.json'):
        row=load(path)
        if row.get('name') in expected:
            if row['name'] in actual:raise ValueError('Duplicate original package metadata: '+row['name'])
            actual[row['name']]=row
    for name,identity in expected.items():
        row=actual.get(name,{})
        if any(row.get(key)!=value for key,value in identity.items()):
            raise Unavailable('Exact recipe-unpatched original artifact required: '+name+' '+identity['build']+
                '; found '+str(row.get('build'))+'. Same-version patched builds are not admitted. Prepare a NEW runtime with the current pinned lock')

def environment(ready, out):
    prefix = Path(ready['r_prefix'])
    env = dict(os.environ, PATH=str(prefix/'bin')+os.pathsep+os.environ.get('PATH',''),
        HOME=str(out/'home'), TMPDIR=str(out/'tmp'), R_LIBS_USER=str(prefix/'lib/R/library'),
        R_LIBS_SITE=str(prefix/'lib/R/library'), R_ENVIRON_USER='/dev/null', R_PROFILE_USER='/dev/null',
        OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1', BLIS_NUM_THREADS='1')
    for name in ['R_HOME','R_LIBS','PYTHONHOME','PYTHONPATH']:
        env.pop(name,None)
    return env

def check(ready):
    if ready['schema'] != 'omicsmatic-original-arrow1201-provider-v1':
        raise ValueError('Unrecognized original Arrow runtime schema')
    check_artifacts(Path(ready['r_prefix']))
    if ready['adapter_sources'] != sources():
        raise ValueError('Qualified adapter source bytes differ; qualify a NEW provider')
    if any((Path(ready['provider'])/name).exists() for name in ['FAILURE.json','FINALIZATION_ERROR.json']):
        raise ValueError('Provider has a failure marker; retain it and use a NEW complete provider')
    receipt = load(Path(ready['provider'])/'COMPLETED.json')
    if receipt['status'] != 'PASS_ORIGINAL_ARROW1201_RUNTIME_NOT_ANALYSIS' or receipt['provider'] != pin(Path(ready['provider'])/'READY.json'):
        raise ValueError('Original provider has no matching complete audit receipt')
    for name in ['pixi.toml','pixi.lock']:
        if pin(Path(ready['workspace'])/name) != pin(HERE/('reference-'+name)):
            raise ValueError('Selected original dependency manifest/lock differs')
    source_tree(Path(ready['source']))
    manifest = Path(ready['provider'])/'r-prefix-files.json'
    if pin(manifest) != ready['prefix_inventory'] or load(manifest) != inventory(Path(ready['r_prefix'])):
        raise ValueError('Original Arrow runtime files differ')
    if not os.access(Path(ready['r_prefix'])/'bin/Rscript',os.X_OK):
        raise Unavailable('Required original Rscript executable unavailable')
