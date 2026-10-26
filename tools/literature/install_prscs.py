#!/usr/bin/env python3
"""Install a private pinned PRS-CS source/runtime profile only with explicit permission."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LOCK_PATH = Path(__file__).with_name('prscs-runtime-lock.json')
LOCK = json.loads(LOCK_PATH.read_text(encoding='utf-8'))
MARKER = '.jlinalg-prscs.json'
BOOTSTRAP = "import os,runpy,sys;source=sys.argv.pop(1);sys.path.insert(0,source);sys.argv[0]=os.path.join(source,'PRScs.py');runpy.run_path(sys.argv[0],run_name='__main__')"
RECOVERY = ('Required action: supply a permitted Windows x64 host and CPython 3.12 for the current source profile. '
            'Where installation is permitted run python tools/literature/install_prscs.py '
            '--profiles SELECTED_PROFILES --python CPYTHON_312_EXECUTABLE --home NEW_PRIVATE_HOME --allow-install; '
            'add --allow-download only for permitted missing official source/wheel/runtime downloads. '
            'Supply the sealed home to the analysis. Preserve changed/unmanaged homes and corrupt caches; use new paths. '
            'If installation or the required software is forbidden/unavailable, skip the dependent analysis (exit 3). '
            'Historical 1.0.0/1.1.0 profiles require an explicitly selected unsupported Python 3.10 runtime; '
            'use them only for offline historical reproduction. No reference panel is downloaded.')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def linked(path):
    path = Path(path)
    return path.is_symlink() or bool(getattr(path.lstat(), 'st_file_attributes', 0) & 0x400)


def private_path(path):
    path = Path(path).absolute()
    for member in [path, *path.parents]:
        if member.is_symlink() or member.exists() and linked(member):
            raise ValueError('Linked private directory/ancestor is unsupported: '+str(member))
    return path.resolve()


def paths(args):
    result = {key: private_path(getattr(args, key)) for key in ['home','cache','report']}
    values = list(result.values())
    for i, a in enumerate(values):
        for b in values[i+1:]:
            if a.is_relative_to(b) or b.is_relative_to(a):
                raise ValueError('Runtime home, cache and report must be separate nonoverlapping directories')
    return result


def tree(directory):
    """Seal every runtime file except ignored Python caches; analysis bypasses caches."""
    result = {}
    for path in sorted(Path(directory).rglob('*')):
        if linked(path):
            raise ValueError('Linked runtime member: '+str(path))
        relative = path.relative_to(directory)
        if '__pycache__' in relative.parts or relative.as_posix() == MARKER:
            continue
        if path.is_file():
            if len(result) >= 30000:
                raise ValueError('Runtime inventory exceeds 30000 files')
            result[relative.as_posix()] = sha(path)
        elif not path.is_dir():
            raise ValueError('Unsupported runtime member: '+str(path))
    return result


def base_tree(directory):
    """Current venv reads these external stdlib/native files; exclude site-packages."""
    directory = Path(directory)
    selected = set()
    for pattern in ['python*.exe','python*.dll','python*.zip']:
        selected.update(p for p in directory.glob(pattern) if p.is_file())
    for folder in ['DLLs','Lib']:
        for path in (directory/folder).rglob('*'):
            relative = path.relative_to(directory)
            if 'site-packages' in relative.parts or '__pycache__' in relative.parts:
                continue
            if path.is_file() and path.suffix.lower() in {'.py','.pyd','.dll','.zip'}:
                selected.add(path)
    if len(selected) > 10000:
        raise ValueError('External Python base exceeds 10000 runtime files')
    return {p.relative_to(directory).as_posix(): sha(p) for p in sorted(selected)}


def available(home, profiles=None):
    try:
        home = private_path(home)
        marker = home/MARKER
        if not marker.is_file() or marker.stat().st_size > 8*1048576 or linked(marker):
            return False
        metadata = json.loads(marker.read_text(encoding='utf-8'))
        if metadata['runtime_lock_sha256'] != sha(LOCK_PATH) or metadata['profile'] != LOCK['profile']:
            return False
        if not metadata['profiles'] or not set(metadata['profiles']) <= set(LOCK['profiles']):
            return False
        if profiles and not set(profiles) <= set(metadata['profiles']):
            return False
        families = {LOCK['profiles'][p]['python'] for p in metadata['profiles']}
        if ('current' in families) != bool(metadata.get('base_python')):
            return False
        for family in families:
            executable = 'current/Scripts/python.exe' if family == 'current' else 'legacy/python.exe'
            if executable not in metadata['files'] or not (home/executable).is_file():
                return False
        if 'legacy' in families and sha(home/'legacy/python310._pth') != LOCK['legacy_python']['derived_pth_sha256']:
            return False
        if tree(home) != metadata['files']:
            return False
        for profile in metadata['profiles']:
            for name, artifact in LOCK['profiles'][profile]['files'].items():
                if sha(home/'sources'/profile/name) != artifact['sha256']:
                    return False
        if metadata.get('base_python'):
            base = metadata['base_python']
            if base['version'][:2] != [3,12] or not (Path(base['prefix'])/'python.exe').is_file() or not base['files']:
                return False
            config = dict(line.split('=',1) for line in (home/'current/pyvenv.cfg').read_text().splitlines() if '=' in line)
            config = {k.strip():v.strip() for k,v in config.items()}
            if config.get('include-system-site-packages') != 'false' or str(Path(config.get('home','')).resolve()) != base['real_prefix']:
                return False
            if str(Path(base['prefix']).resolve()) != base['real_prefix'] or base_tree(base['prefix']) != base['files']:
                return False
        return True
    except (OSError, ValueError, KeyError, TypeError):
        return False


def run(command, report, label, timeout=300):
    stdout, stderr = report/(label+'.stdout'), report/(label+'.stderr')
    environment = os.environ.copy()
    for name in ['PYTHONPATH','PYTHONHOME','PYTHONSTARTUP','PYTHONINSPECT','PYTHONWARNINGS']:
        environment.pop(name, None)
    for name in ['OMP_NUM_THREADS','OPENBLAS_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS']:
        environment[name] = '1'
    environment['HDF5_PLUGIN_PRELOAD'] = '::'
    environment['HDF5_PLUGIN_PATH'] = ''
    with stdout.open('wb') as out, stderr.open('wb') as err:
        process = subprocess.Popen([str(v) for v in command], cwd=report, env=environment, stdout=out, stderr=err)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            subprocess.run([str(Path(os.environ.get('SystemRoot','C:/Windows'))/'System32/taskkill.exe'),
                            '/PID',str(process.pid),'/T','/F'], capture_output=True, timeout=30)
            process.wait(timeout=30)
            raise RuntimeError('Owned setup process timed out; inspect '+str(stderr))
    record = dict(command=[str(v) for v in command], owned_pid=process.pid, exit_code=code,
                  stdout_sha256=sha(stdout), stderr_sha256=sha(stderr))
    if code:
        raise RuntimeError('Setup step '+label+' failed with exit '+str(code)+'; inspect '+str(stderr))
    return record


def python_flags(python, report, label):
    cache = report/(label+'-unused-cache')
    cache.mkdir()
    return [str(python),'-I','-B','-X','utf8','-X','pycache_prefix='+str(cache)]


def probe(home, selected, report):
    records = []
    for family in sorted({LOCK['profiles'][p]['python'] for p in selected}):
        python = home/'current/Scripts/python.exe' if family == 'current' else home/'legacy/python.exe'
        script = 'import json,sys,struct,numpy,scipy,h5py;print(json.dumps(dict(implementation=sys.implementation.name,version=list(sys.version_info[:3]),bits=8*struct.calcsize("P"),base_prefix=sys.base_prefix,numpy=numpy.__version__,scipy=scipy.__version__,h5py=h5py.__version__)))'
        records.append(run(python_flags(python, report, family+'-imports')+['-c',script], report, family+'-imports',60))
        info = json.loads((report/(family+'-imports.stdout')).read_text(encoding='utf-8'))
        wanted = {p['name']: p['version'] for p in LOCK['packages'][family]}
        if info['implementation'] != 'cpython' or info['bits'] != 64 or {k:info[k] for k in wanted} != wanted:
            raise RuntimeError('Original runtime import versions differ from the exact profile')
        expected_version = [3,12] if family == 'current' else [3,10,11]
        if info['version'][:len(expected_version)] != expected_version:
            raise RuntimeError('Original runtime Python version differs from the selected profile')
    for profile in selected:
        family = LOCK['profiles'][profile]['python']
        python = home/'current/Scripts/python.exe' if family == 'current' else home/'legacy/python.exe'
        records.append(run(python_flags(python, report, profile+'-help')+['-c',BOOTSTRAP,str(home/'sources'/profile),'--help'],report,profile+'-help',60))
        text = (report/(profile+'-help.stdout')).read_text(encoding='utf-8')
        if 'PRS-CS: a polygenic prediction method' not in text or '--ref_dir=' not in text:
            raise RuntimeError('Original usage probe differs for '+profile)
    return records


def artifact(cache, item, allow_download):
    path = cache/item['cache_name']
    if path.exists():
        if linked(path) or not path.is_file() or sha(path) != item['sha256']:
            raise RuntimeError('Changed/linked cached artifact preserved: '+str(path))
        return path
    if not allow_download:
        raise RuntimeError('Missing cached artifact and download permission absent: '+str(path))
    cache.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix='.partial-',dir=cache)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        total = 0
        request = urllib.request.Request(item['url'], headers={'User-Agent':'JLinAlg-pinned-PRS-CS-setup'})
        with urllib.request.urlopen(request,timeout=60) as response, temporary.open('wb') as stream:
            while block := response.read(1048576):
                total += len(block)
                if total > item.get('maximum_bytes',100000000):
                    raise RuntimeError('Official artifact exceeds its declared byte limit')
                stream.write(block)
        if sha(temporary) != item['sha256']:
            raise RuntimeError('Official artifact checksum differs; partial cache retained at '+str(temporary))
        if path.exists():
            raise RuntimeError('Cached target appeared during download; both files preserved')
        temporary.rename(path)
        return path
    except Exception:
        # Preserve failed downloads and existing cached targets for inspection.
        raise


def install(args, directories, selected, report):
    home, cache = directories['home'], directories['cache']
    if home.exists():
        if not available(home, selected):
            raise RuntimeError('Existing runtime is changed, unmanaged or lacks the selected profiles; preserved '+str(home))
        records = probe(home, selected, report)
        if not available(home, selected):
            raise RuntimeError('Runtime changed during reuse probes')
        return dict(reused=True, steps=records)
    if not args.allow_install:
        raise RuntimeError('Installation permission absent; no runtime home created')
    families = {LOCK['profiles'][p]['python'] for p in selected}
    needed = []
    for profile in selected:
        needed.extend(LOCK['profiles'][profile]['files'].values())
    for family in sorted(families):
        needed.extend(dict(p,cache_name=p['filename'],maximum_bytes=100000000) for p in LOCK['packages'][family])
    if 'legacy' in families:
        needed.append(LOCK['legacy_python'])
    cached = {item['cache_name']: artifact(cache,item,args.allow_download) for item in needed}
    steps = []
    if 'current' in families:
        flags = python_flags(args.python,report,'base-python')
        steps.append(run(flags+['-c','import json,sys,struct;print(json.dumps(dict(implementation=sys.implementation.name,version=list(sys.version_info[:3]),bits=8*struct.calcsize("P"),base_prefix=sys.base_prefix)))'],report,'base-python',60))
        info = json.loads((report/'base-python.stdout').read_text())
        if info['implementation'] != 'cpython' or info['version'][:2] != [3,12] or info['bits'] != 64:
            raise RuntimeError('Current source requires supplied CPython 3.12 x64; use --python with that interpreter')
    home.mkdir(parents=True, exist_ok=False)
    for profile in selected:
        folder = home/'sources'/profile
        folder.mkdir(parents=True)
        for name, item in LOCK['profiles'][profile]['files'].items():
            shutil.copy2(cached[item['cache_name']],folder/name)
    for family in sorted(families):
        requirements = report/(family+'-requirements.txt')
        requirements.write_text(''.join(p['name']+'=='+p['version']+' --hash=sha256:'+p['sha256']+'\n' for p in LOCK['packages'][family]))
        if family == 'current':
            steps.append(run([args.python,'-I','-B','-m','venv','--without-pip',home/'current'],report,'create-current',120))
            command = [args.python,'-I','-B','-m','pip','--disable-pip-version-check','--python',home/'current/Scripts/python.exe','install','--no-compile']
        else:
            folder = home/'legacy'; folder.mkdir()
            with zipfile.ZipFile(cached[LOCK['legacy_python']['cache_name']]) as archive:
                if len(archive.infolist()) > 100 or sum(i.file_size for i in archive.infolist()) > 100000000:
                    raise RuntimeError('Original embedded Python archive inventory exceeds bounds')
                for entry in archive.infolist():
                    name = entry.filename
                    if entry.is_dir() or len(Path(name).parts) != 1 or ':' in name or '\\' in name or name in {'.','..'} or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                        raise RuntimeError('Escaping/linked embedded Python archive member')
                    archive.extract(entry, folder)
            (folder/'python310._pth').write_bytes(LOCK['legacy_python']['pth_text'].encode('utf-8'))
            command = [args.python,'-I','-B','-m','pip','--disable-pip-version-check','install','--target',folder/'Lib/site-packages','--platform','win_amd64','--python-version','3.10','--implementation','cp','--abi','cp310','--no-deps','--no-compile']
        command += ['--no-index','--find-links',cache,'--require-hashes','--only-binary=:all:','-r',requirements]
        steps.append(run(command,report,'install-'+family,600))
    steps.extend(probe(home,selected,report))
    metadata = dict(profile=LOCK['profile'],runtime_lock_sha256=sha(LOCK_PATH),profiles=selected,files=tree(home))
    if 'current' in families:
        prefix = info['base_prefix']
        metadata['base_python'] = dict(prefix=prefix,real_prefix=str(Path(prefix).resolve()),version=info['version'],files=base_tree(prefix))
    (home/MARKER).write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    if not available(home,selected):
        raise RuntimeError('Final sealed runtime admission failed; preserve this home and use a new path')
    return dict(reused=False,steps=steps)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles', required=True, help='Comma-separated source-fbc9ec0,1.1.0,1.0.0')
    parser.add_argument('--home', type=Path, default=ROOT/'build/omics-prscs-original')
    parser.add_argument('--cache', type=Path, default=ROOT/'build/omics-prscs-download')
    parser.add_argument('--report', type=Path, default=ROOT/'build/literature-review/prscs-runtime/dependency')
    parser.add_argument('--python', type=Path, default=Path(sys.executable))
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args()
    selected = args.profiles.split(',')
    try:
        if len(set(selected)) != len(selected) or not set(selected) <= set(LOCK['profiles']):
            raise ValueError('Choose exact nonduplicated original profiles from the bundled lock')
        directories = paths(args)
    except (OSError,ValueError) as error:
        print('PRS-CS SETUP NOT COMPLETED: '+str(error)+'. '+RECOVERY,file=sys.stderr)
        return 2
    directories['report'].mkdir(parents=True,exist_ok=True)
    report = Path(tempfile.mkdtemp(prefix='attempt-',dir=directories['report']))
    record = dict(status='running',profile=LOCK['profile'],runtime_lock_sha256=sha(LOCK_PATH),profiles=selected,
                  started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),home=str(directories['home']),attempt=str(report),
                  allow_install=args.allow_install,allow_download=args.allow_download,scientific_analysis_validated=False)
    warnings = []
    if any(LOCK['profiles'][p]['python']=='legacy' for p in selected):
        warnings.append('UNSUPPORTED LEGACY PYTHON: explicitly selected historical profiles use Python 3.10.11, which reached end-of-life on 2026-10-01. Use only for offline historical reproduction; use the current source/CPython 3.12 profile for new analyses. No original scientific source is patched.')
    try:
        if platform.system() != 'Windows' or platform.machine().upper() not in {'AMD64','X86_64'}:
            raise RuntimeError('This admitted original runtime profile requires Windows x64; do not reinterpret it as another host profile')
        record.update(install(args,directories,selected,report))
        record['status'] = 'available-setup-probes-passed-not-scientific-validation'
        code = 0
    except Exception as error:
        record.update(status='skipped-dependency-unavailable',error=str(error))
        warnings.append('PRS-CS SETUP SKIPPED: '+str(error))
        code = 3
    text = '# PRS-CS '+('SETUP AVAILABLE — REVIEW BEFORE ANALYSIS' if code==0 else 'SETUP SKIPPED')+'\n\n'+'\n\n'.join(warnings)+'\n\n'+RECOVERY+'\n\nSetup/version probes do not establish scientific analysis validation. Inspect retained attempt logs.\n'
    (report/'WARNINGS.md').write_text(text,encoding='utf-8')
    (report/'dependency-status.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    for name in ['WARNINGS.md','dependency-status.json']:
        shutil.copy2(report/name,directories['report']/name)
    print(json.dumps(dict(status=record['status'],home=record['home'],report=str(report),exit_code=code)))
    print('PRS-CS WARNINGS: '+str(report/'WARNINGS.md'),file=sys.stderr)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
