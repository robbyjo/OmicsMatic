# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact original celldex1.14 source/runtime identities and owned reference caches."""
from pathlib import Path
import importlib.util,os,sys
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
UTILITY=ROOT/'tools/literature/singler_workflows/Runtime.py'
NAMESPACE_AUDIT=ROOT/'tools/literature/singler_workflows/AuditNamespace.R'
spec=importlib.util.spec_from_file_location('celldex_recording_utilities',UTILITY)
common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
pin,load,save,inventory,check_output=common.pin,common.load,common.save,common.inventory,common.check_output
class Unavailable(OSError):pass
CORE=['Runtime.py','Audit.R','Worker.R','run.py','adopt_runtime.py','install_runtime.py','source-profile.json','dependencies.toml','dependencies.lock']
ACTION=('SKIP3 skips dependent analysis when exact permitted source, runtime, dependencies, Linux/network isolation, installation, reference data or download permission is unavailable. '
 'FAIL2 admits no complete result, including original errors, native crashes and recording failure after an original return. '
 'Read WARNINGS.md, FAILURE.json, original/R-status.json, complete original conditions/RNG and both raw consoles. '
 'Preserve original inputs, identifiers, NA/NaN/Inf, complete returns, reference cache/assets and prior evidence. Supply the named permitted requirement or resolve the stated input/filesystem cause and retry NEW paths. '
 'Reference downloads require explicit --allow-download; offline children run in a network namespace. Analysis never installs runtime software. '
 'P051 literally describes MouseRNAseq ImmGen references via celldex1.14.0, without establishing exact functions, options or data snapshots. '
 'Project reference selections and comparison environments are not recovered author settings. Cell annotation is a separate SingleR operation. The full 250-paper goal remains open.')

def profile():return load(HERE/'source-profile.json')
def sources():return {p.relative_to(ROOT).as_posix():pin(p) for p in [*[HERE/n for n in CORE],UTILITY,NAMESPACE_AUDIT]}
def source_tree(paths):
    packages=profile()['packages']
    if set(paths)!=set(packages):raise ValueError('Every complete declared original source package is required')
    for name,entry in packages.items():
        actual=inventory(paths[name])
        if any(row['kind']!='file' for row in actual.values()):raise ValueError('Original sources require complete regular files: '+name)
        expected={n:{k:v[k] for k in ['bytes','sha256']} for n,v in entry['original_regular_members'].items()}
        if {n:{k:v[k] for k in ['bytes','sha256']} for n,v in actual.items()}!=expected:raise ValueError('Complete original source differs: '+name)
def environment(ready,out):
    out=Path(out);prefix=Path(ready['r_prefix'])
    return dict(HOME=str(out/'home'),TMPDIR=str(out/'tmp'),PATH=str(prefix/'bin')+':/usr/bin:/bin',LANG='C.UTF-8',
      R_LIBS_USER=ready['library'],R_LIBS_SITE=str(prefix/'lib/R/library'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
      LD_LIBRARY_PATH=str(prefix/'lib'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',RCPP_PARALLEL_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
def control(ready):
    return dict(library=ready['library'],base_library=str(Path(ready['r_prefix'])/'lib/R/library'),source_paths=ready['source_paths'],
      r_version=profile()['r_version'],package_versions={k:v['version'] for k,v in profile()['packages'].items()},namespace_audit=str(NAMESPACE_AUDIT))
def check(ready):
    if ready.get('schema')!='omicsmatic-original-celldex114-v1':raise ValueError('Exact celldex1.14.0 provider schema required')
    if ready['adapter_sources']!=sources():raise ValueError('Adapter source changed; retain the old provider and audit a NEW provider')
    provider=Path(ready['provider']);done=load(provider/'COMPLETED.json')
    if done['status']!='PASS_ORIGINAL_CELLDEX114_PROVIDER_NOT_ANALYSIS' or done['provider']!=pin(provider/'READY.json'):raise Unavailable('Exact original runtime audit incomplete')
    source_tree(ready['source_paths']);workspace=Path(ready['workspace'])
    for source,target in [('dependencies.toml','pixi.toml'),('dependencies.lock','pixi.lock')]:
        if pin(HERE/source)!=pin(workspace/'runtime'/target):raise ValueError('Declared complete dependency lock/manifest differs')
    for key,path in [('r-prefix-files',ready['r_prefix']),('private-library-files',ready['library'])]:
        if pin(provider/(key+'.json'))!=ready[key] or inventory(path)!=load(provider/(key+'.json')):raise ValueError('Complete protected runtime differs: '+key)
def validate_cache(path):
    root=Path(path).resolve()
    if not root.is_dir():raise Unavailable('Supply a complete permitted reference-cache directory')
    for p in root.rglob('*'):
        if p.is_symlink() and (Path(os.readlink(p)).is_absolute() or not p.resolve().is_relative_to(root)):
            raise ValueError('Reference cache must be self-contained; preserve external/absolute links and prepare an explicit complete copy')
    return inventory(root)
