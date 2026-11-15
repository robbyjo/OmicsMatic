# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Private original 0.2 runtime identities; analysis never installs software."""
from pathlib import Path
import hashlib, json, os, stat

HERE = Path(__file__).resolve().parent
CORE = ['Runtime.py', 'AuditRuntime.R', 'AuditEntry.R', 'Worker.R', 'run.py', 'adopt_runtime.py',
        'upstream-source-profile.json', 'reference-pixi.toml', 'reference-pixi.lock',
        'conda-manager-pixi.toml', 'conda-manager-pixi.lock']
ACTION = (
    'Review WARNINGS.md, every raw console, and the complete original R conditions. '
    'Missing or forbidden requirements SKIP3 skip dependent analysis. FAIL2 admits no complete result. '
    'Resolve the named cause and retry NEW writable output paths, retaining original inputs and all partial files. '
    'Use trusted RDS requests and a complete prepared reference/index asset directory. '
    'This adapter does not retrieve Allen private taxonomies or infer paper parameters. '
    'Use unchanged scrattch.mapping 0.2 with the selected private Linux/WSL R 4.2.3 profile; '
    'this is not a recovered author environment. Analysis does not install or download. '
    'The scrattch.io and scrattch.vis dependencies restrict commercial redistribution; '
    'keep the private runtime separate from distributable project artifacts. '
    'Do not repair identifiers, NA/NaN/Inf, source behavior, or historical evidence hashes. '
    'All original warnings require review; raw/native diagnostic totals are unavailable, never zero. '
    'No whole-package, biological, paper-reproduction, or full-250-paper completion claim is made.')


class Unavailable(OSError):
    pass


def pin(path):
    digest = hashlib.sha256()
    size = 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def save(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(value, ensure_ascii=False, allow_nan=False, indent=2) + '\n')


def unique(pairs):
    value = {}
    for key, entry in pairs:
        if key in value:
            raise ValueError('Duplicate JSON key: ' + key)
        value[key] = entry
    return value


def load(path):
    return json.loads(Path(path).read_bytes(), object_pairs_hook=unique)


def walk_error(error):
    """Never silently omit an unreadable subtree from a complete inventory."""
    raise error


def live_walk_error(error):
    """A running original child can remove a temporary directory during a scan."""
    if not isinstance(error, FileNotFoundError):
        raise error


def inventory(root, links=True):
    root = Path(root)
    if not root.is_dir() or root.is_symlink():
        raise Unavailable('Required regular directory missing: ' + str(root))
    values = {}
    for parent, dirs, files in os.walk(root, followlinks=False, onerror=walk_error):
        for name in sorted(dirs + files):
            path = Path(parent) / name
            info = path.lstat()
            relative = path.relative_to(root).as_posix()
            if stat.S_ISLNK(info.st_mode):
                if not links:
                    raise ValueError('Reference assets must be regular files/directories: ' + str(path))
                values[relative] = dict(kind='link', target=os.readlink(path))
            elif stat.S_ISREG(info.st_mode):
                values[relative] = dict(kind='file', executable=bool(info.st_mode & 0o111), **pin(path))
            elif not stat.S_ISDIR(info.st_mode):
                raise ValueError('Unsupported file kind: ' + str(path))
    return values


def sources():
    return {name: pin(HERE / name) for name in CORE}


def source_tree(root):
    expected = load(HERE / 'upstream-source-profile.json')['complete_original_regular_members']
    actual = inventory(root, links=False)
    identities = {name: {k: v for k, v in entry.items() if k not in ['kind', 'executable']}
                  for name, entry in actual.items()}
    if identities != expected:
        raise ValueError('Complete unchanged original scrattch.mapping 0.2 source differs')


def environment(ready, out):
    prefix = Path(ready['r_prefix'])
    value = dict(os.environ, PATH=str(prefix / 'bin') + os.pathsep + os.environ.get('PATH', ''),
                 HOME=str(out / 'home'), TMPDIR=str(out / 'tmp'),
                 R_LIBS_USER=ready['library'], R_LIBS_SITE=ready['base_library'],
                 R_ENVIRON_USER='/dev/null', R_PROFILE_USER='/dev/null',
                 RETICULATE_PYTHON=str(prefix / 'bin/python'),
                 RETICULATE_CONDA=str(Path(ready['manager_prefix']) / 'bin/conda'),
                 RETICULATE_AUTOCONFIGURE='FALSE', RETICULATE_USE_MANAGED_VENV='no',
                 PYTHONDONTWRITEBYTECODE='1', CONDA_NO_PLUGINS='true', CONDA_SOLVER='classic',
                 OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1',
                 VECLIB_MAXIMUM_THREADS='1', BLIS_NUM_THREADS='1')
    for key in ['R_HOME', 'R_LIBS', 'PYTHONPATH', 'PYTHONHOME']:
        value.pop(key, None)
    return value


def check(ready):
    if ready['schema'] != 'omicsmatic-scrattchmapping02-original-runtime-v1':
        raise ValueError('Unrecognized runtime schema')
    if sources() != ready['runner_sources']:
        raise ValueError('Qualified adapter sources differ; retain old provider and qualify NEW paths')
    completed = load(Path(ready['provider']) / 'COMPLETED.json')
    if (completed['status'] != 'PASS_ORIGINAL_RUNTIME_AUDITED_NOT_ANALYSIS'
            or completed['provider'] != pin(Path(ready['provider']) / 'READY.json')):
        raise ValueError('Runtime provider has no matching complete audit receipt')
    for root, stem in [('prefix', 'reference-'), ('manager_workspace', 'conda-manager-')]:
        for filename in ['pixi.toml', 'pixi.lock']:
            if pin(Path(ready[root]) / filename) != pin(HERE / (stem + filename)):
                raise ValueError('Selected dependency manifest/lock differs: ' + root)
    source_tree(Path(ready['source']))
    for key in ['r_prefix', 'library', 'manager_prefix']:
        path = Path(ready['provider']) / (key + '-files.json')
        if pin(path) != ready['inventory_files'][key] or load(path) != inventory(Path(ready[key])):
            raise ValueError('Original runtime identity differs: ' + key)
    if not os.access(Path(ready['r_prefix']) / 'bin/Rscript', os.X_OK):
        raise Unavailable('Required original Rscript executable unavailable')
