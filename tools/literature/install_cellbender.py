#!/usr/bin/env python3
"""Install/reuse the exact privately validated Open-Source CellBender Linux runtime.

No OS packages or source builds are installed here. Every dependency is an exact
wheel from the validated original CPU package family. Missing wheels may be
downloaded only with --allow-download. A new isolated venv is always used.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import sysconfig
import urllib.request

HERE=Path(__file__).resolve().parent
LOCK_PATH=HERE/'cellbender-runtime-lock.json'
LOCK_BYTES=LOCK_PATH.read_bytes()
LOCK=json.loads(LOCK_BYTES)
LOCK_SHA=hashlib.sha256(LOCK_BYTES).hexdigest()
MARKER='.jlinalg-cellbender.json'
RECOVERY=('Required action: use Ubuntu24.04 Linux x86_64 CPython3.12 and Linux Java inside WSL on Windows. '
          'Where permitted run python3 tools/literature/install_cellbender.py --home NEW_PRIVATE_HOME '
          '--cache CACHE --report NEW_REPORT --allow-install; add --allow-download only for permitted missing official wheels. '
          'The pinned profile is originalCellBender0.4.0 and PyTorch2.8.0+cpu;CUDA is not available in this profile. '
          'Do not use redacted0.3.1,substitute historical releases,modify global environments or reseal changed/unmanaged homes. '
          'Missing,unsupported,changed or forbidden dependencies require SKIPPING the dependent analysis(exit3). '
          'Preserve failed homes/cache/logs;correct the named cause and choose new paths. Analysis never installs software.')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def private_path(path):
    path=Path(path).absolute()
    for entry in [path,*path.parents]:
        if entry.is_symlink():
            raise ValueError('Linked private directory/ancestor is unsupported: '+str(entry))
    return path.resolve()


def host():
    release={}
    path=Path('/etc/os-release')
    if path.is_file():
        for line in path.read_text().splitlines():
            if '=' in line:
                key,value=line.split('=',1);release[key]=value.strip('"')
    if (platform.system()!='Linux' or platform.machine().lower() not in LOCK['host']['machine']
            or platform.python_implementation()!='CPython' or list(sys.version_info[:2])!=[3,12]
            or release.get('ID')!='ubuntu' or release.get('VERSION_ID')!='24.04'):
        raise ValueError('This admitted profile requires Ubuntu24.04 Linux x86_64 and CPython3.12')
    return dict(os=platform.system(),machine=platform.machine(),python=platform.python_version(),
                distribution=release.get('ID'),distribution_version=release.get('VERSION_ID'))


def ignored(path):
    return '__pycache__' in path.parts or path.suffix in {'.pyc','.pyo'}


def tree(home):
    files={};links={}
    for parent,dirs,names in os.walk(home,followlinks=False):
        dirs[:]=[d for d in dirs if d!='__pycache__']
        for name in dirs+names:
            path=Path(parent)/name;relative=path.relative_to(home)
            if str(relative)==MARKER or ignored(relative):continue
            if path.is_symlink():links[relative.as_posix()]=os.readlink(path)
            elif path.is_file():files[relative.as_posix()]=sha(path)
            elif not path.is_dir():raise ValueError('Nonregular private runtime member: '+str(relative))
    if len(files)+len(links)>100000:raise ValueError('Private runtime inventory exceeds100000 entries')
    return dict(files=files,links=links)


def external_file(path):
    path=Path(path)
    if not path.is_file():raise ValueError('Required external runtime file unavailable: '+str(path))
    return dict(real_path=str(path.resolve()),sha256=sha(path))


def external_inventory(home):
    stdlib=Path(sysconfig.get_path('stdlib')).resolve()
    if not stdlib.is_relative_to(Path(sys.base_prefix).resolve()):raise ValueError('Unexpected base Python stdlib root')
    entries={};relative=[];elfs=[Path(sys.executable).resolve()]
    for parent,dirs,names in os.walk(stdlib,followlinks=False):
        dirs[:]=[d for d in dirs if d not in {'__pycache__','site-packages','dist-packages'}]
        for name in names:
            path=Path(parent)/name
            if ignored(path):continue
            if path.is_file():
                entries[str(path)]=external_file(path);relative.append(path.relative_to(stdlib).as_posix())
                if '.so' in path.name:elfs.append(path.resolve())
    for path in home.rglob('*'):
        if path.is_file() and not path.is_symlink() and re.search(r'\.so(?:\.\d+)*$',path.name):elfs.append(path)
    # NumPy's extension RPATH makes bundled companion libraries visible during
    # import. Inspecting a companion ELF alone lacks that parent-loader context.
    # This path is for ldd inspection only; analysis keeps the original loader.
    bundled=sorted({str(p.parent) for p in elfs if p.is_relative_to(home)})
    ldd_env={'PATH':'/usr/bin:/bin','LC_ALL':'C'}
    if bundled:ldd_env['LD_LIBRARY_PATH']=':'.join(sorted(bundled))
    entries[str(Path(sys.executable).resolve())]=external_file(Path(sys.executable).resolve())
    for path in sorted(set(elfs)):
        result=subprocess.run(['/usr/bin/ldd',str(path)],capture_output=True,text=True,timeout=30,
                              env=ldd_env)
        if result.returncode:raise ValueError('Cannot inspect linked ELF dependencies: '+str(path)+';'+result.stderr.strip())
        for line in result.stdout.splitlines():
            if 'not found' in line:raise ValueError('Missing shared library for '+str(path)+': '+line.strip())
            match=re.match(r'^\s*(?:\S+\s+=>\s+)?(/.+?)\s+\(0x[0-9a-f]+\)\s*$',line)
            if match:
                dependency=Path(match.group(1))
                if not dependency.resolve().is_relative_to(home):entries[str(dependency)]=external_file(dependency)
    entries['/etc/os-release']=external_file('/etc/os-release')
    absent=[str(Path(sys.base_prefix)/'lib'/('python'+str(sys.version_info[0])+str(sys.version_info[1])+'.zip'))]
    if any(Path(p).exists() for p in absent):raise ValueError('Unexpected base Python zip search path;this profile expects a directory stdlib')
    return dict(stdlib_root=str(stdlib),stdlib_members=sorted(relative),files=entries,absent_paths=absent,
                executable=str(Path(sys.executable).resolve()),python_version=list(sys.version_info[:3]))


def verify(home):
    marker=home/MARKER
    if not marker.is_file() or marker.is_symlink() or marker.stat().st_size>8*1048576:
        raise ValueError('Missing/invalid CellBender marker;preserve this unmanaged home')
    metadata=json.loads(marker.read_text(encoding='utf-8'))
    if (metadata.get('profile')!=LOCK['profile'] or metadata.get('runtime_lock_sha256')!=LOCK_SHA
            or metadata.get('home')!=str(home) or metadata.get('wheels')!={k:v['sha256'] for k,v in LOCK['wheels'].items()}):
        raise ValueError('CellBender marker differs from the admitted identity/home')
    for name,digest in LOCK['critical_package_files'].items():
        if sha(home/'venv/lib/python3.12/site-packages'/name)!=digest:raise ValueError('Original CellBender member differs: '+name)
    if tree(home)!=metadata.get('inventory'):raise ValueError('Private CellBender files or links changed;never re-seal this home')
    base=metadata.get('base_python',{})
    if not isinstance(base.get('files'),dict) or not base['files'] or len(base['files'])>100000:
        raise ValueError('Invalid base Python/ELF inventory')
    for name,entry in base['files'].items():
        if external_file(name)!=entry:raise ValueError('Base interpreter/library changed: '+name)
    if any(Path(p).exists() for p in base['absent_paths']):raise ValueError('A previously absent Python import path appeared')
    stdlib=Path(base['stdlib_root']);observed=[]
    for parent,dirs,names in os.walk(stdlib,followlinks=False):
        dirs[:]=[d for d in dirs if d not in {'__pycache__','site-packages','dist-packages'}]
        observed.extend((Path(parent)/n).relative_to(stdlib).as_posix() for n in names if not ignored(Path(n)) and (Path(parent)/n).is_file())
    if sorted(observed)!=base['stdlib_members']:raise ValueError('Base stdlib file inventory changed')
    return metadata


def clean_environment(report):
    env={k:v for k,v in os.environ.items() if not k.startswith(('PYTHON','CELLBENDER','LD_'))}
    env.update(PATH='/usr/bin:/bin',LC_ALL='C.UTF-8',HOME=str(report/'isolated-home'),
               OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
    Path(env['HOME']).mkdir(exist_ok=False)
    for key,name in [('MPLCONFIGDIR','mpl'),('IPYTHONDIR','ipython'),('JUPYTER_CONFIG_DIR','jupyter'),('XDG_CACHE_HOME','cache'),('TORCH_HOME','torch')]:env[key]=str(Path(env['HOME'])/name)
    return env


def run(command,label,report,env,timeout=300):
    with (report/(label+'.stdout')).open('xb') as out,(report/(label+'.stderr')).open('xb') as err:
        process=subprocess.Popen(command,stdout=out,stderr=err,cwd=report,env=env,start_new_session=True)
        try:code=process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            import signal
            os.killpg(process.pid,signal.SIGTERM)
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
            raise
    if code:raise ValueError(label+' failed with exit'+str(code)+';inspect '+str(report/(label+'.stderr')))


def install(args,report):
    info=host();home=private_path(args.home);cache=private_path(args.cache)
    for a,b in [(home,cache),(home,report),(cache,report)]:
        if a.is_relative_to(b) or b.is_relative_to(a):raise ValueError('Home,cache and report must not overlap')
    if home.exists():
        metadata=verify(home)
        return dict(status='reused-verified-runtime',profile=LOCK['profile'],host=info,home=str(home),
                    marker_sha256=sha(home/MARKER),files=len(metadata['inventory']['files']),base_files=len(metadata['base_python']['files']))
    if not args.allow_install:raise PermissionError('Runtime home absent;--allow-install was not supplied;no home created')
    reference=private_path(args.reference_wheels) if args.reference_wheels else None
    if reference and (reference.is_relative_to(home) or home.is_relative_to(reference)):
        raise ValueError('Reference wheels must not overlap the new home')
    cache.mkdir(parents=True,exist_ok=True)
    artifacts=dict(LOCK['wheels']);artifacts[LOCK['bootstrap_pip']['filename']]=LOCK['bootstrap_pip']
    def acquire(item):
        name,expected=item;target=cache/name
        if target.exists():
            if target.is_symlink() or not target.is_file() or sha(target)!=expected['sha256']:
                raise ValueError('Corrupt/linked cache member;preserve it and select a new cache: '+name)
        elif reference and (reference/name).is_file():
            source=reference/name
            if source.is_symlink() or sha(source)!=expected['sha256']:raise ValueError('Reference wheel differs: '+name)
            with source.open('rb') as src,target.open('xb') as dst:shutil.copyfileobj(src,dst)
        else:
            if not args.allow_download:raise PermissionError('Missing wheel and --allow-download was not supplied: '+name)
            if not expected['url'].startswith('https://'):raise ValueError('Pinned artifact URL is not HTTPS')
            with urllib.request.urlopen(urllib.request.Request(expected['url'],headers={'User-Agent':'OmicsMatic-pinned-CellBender-runtime/1.0'}),timeout=60) as source,target.open('xb') as dst:
                total=0
                while chunk:=source.read(1048576):
                    total+=len(chunk)
                    if total>expected['maximum_bytes']:raise ValueError('Download exceeds reviewed wheel bound: '+name)
                    dst.write(chunk)
            if sha(target)!=expected['sha256']:raise ValueError('Downloaded wheel checksum mismatch;preserve cache and select a new one: '+name)
        if target.stat().st_size!=expected['bytes']:raise ValueError('Pinned wheel size differs: '+name)
        return name
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=4) as executor:
        for name in executor.map(acquire,artifacts.items()):print('Verified artifact '+name,flush=True)
    home.mkdir(parents=True,exist_ok=False)
    env=clean_environment(report)
    run([sys.executable,'-I','-B','-m','venv','--without-pip','--copies',str(home/'venv')],'create-venv',report,env)
    requirements=home/'requirements-locked.txt'
    requirements.write_text(''.join(p['package']+'=='+p['version']+' --hash=sha256:'+p['sha256']+'\n' for p in LOCK['wheels'].values()),encoding='utf-8')
    python=home/'venv/bin/python'
    bootstrap='import runpy,sys;sys.path.insert(0,sys.argv.pop(1));runpy.run_module(\"pip\",run_name=\"__main__\")'
    run([str(python),'-I','-B','-c',bootstrap,str(cache/LOCK['bootstrap_pip']['filename']),'--disable-pip-version-check','install','--no-index','--no-deps','--require-hashes','--find-links',str(cache),'-r',str(requirements)],'install-pinned-wheels',report,env)
    site=home/'venv/lib/python3.12/site-packages'
    for name,digest in LOCK['critical_package_files'].items():
        if sha(site/name)!=digest:raise ValueError('Original CellBender package member differs: '+name)
    cache_prefix=report/'unused-pycache';cache_prefix.mkdir()
    probe="import sys,json,platform;from pathlib import Path;sys.path.insert(0,sys.argv[1]);sys.prefix=sys.exec_prefix=str(Path(sys.argv[1]).parents[2]);import importlib.metadata as m;import torch,pyro,numpy,scipy,pandas,anndata,h5py,tables,cellbender;print(json.dumps(dict(python=list(sys.version_info[:3]),implementation=platform.python_implementation(),packages={d.metadata['Name'].lower().replace('_','-'):d.version for d in m.distributions()},cuda_available=torch.cuda.is_available(),cellbender=m.version('cellbender'),torch=torch.__version__)))"
    run([str(python),'-I','-S','-B','-X','utf8','-X','pycache_prefix='+str(cache_prefix),'-c',probe,str(site)],'isolated-version-probe',report,env)
    versions=json.loads((report/'isolated-version-probe.stdout').read_text(encoding='utf-8'))
    if (versions['python'][:2]!=[3,12] or versions['implementation']!='CPython' or versions['cellbender']!='0.4.0'
            or versions['torch']!='2.8.0+cpu' or versions['cuda_available'] is not False or versions['packages']!=LOCK['packages']):
        raise ValueError('Actual isolated Python/package/CPU profile differs')
    (home/'runtime-lock.json').write_bytes(LOCK_BYTES)
    notices=home/'upstream-notices';notices.mkdir()
    for name,expected in LOCK['notices'].items():
        source=HERE/'licenses'/name
        if not source.is_file() or sha(source)!=expected['sha256']:
            raise ValueError('Required upstream notice differs: '+name)
        shutil.copy2(source,notices/name)
    metadata=dict(profile=LOCK['profile'],home=str(home),runtime_lock_sha256=LOCK_SHA,host=info,
                  wheels={k:v['sha256'] for k,v in LOCK['wheels'].items()},python='venv/bin/python',site_packages='venv/lib/python3.12/site-packages',
                  versions=versions,base_python=external_inventory(home),inventory=tree(home),
                  scientific_scope='Installation/version probe only. Original fitting and independent input/count/cell-call audits require separate validation.')
    (home/MARKER).write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    verify(home)
    return dict(status='installed-verified-runtime',profile=LOCK['profile'],host=info,home=str(home),marker_sha256=sha(home/MARKER),files=len(metadata['inventory']['files']),links=len(metadata['inventory']['links']),base_files=len(metadata['base_python']['files']),versions=versions)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',type=Path,required=True)
    parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--reference-wheels',type=Path)
    parser.add_argument('--allow-install',action='store_true')
    parser.add_argument('--allow-download',action='store_true')
    args=parser.parse_args()
    try:
        report=private_path(args.report);home=private_path(args.home);cache=private_path(args.cache)
        for a,b in [(home,cache),(home,report),(cache,report)]:
            if a.is_relative_to(b) or b.is_relative_to(a):raise ValueError('Home,cache and report must not overlap')
        if args.reference_wheels:
            reference=private_path(args.reference_wheels)
            if home.is_relative_to(reference) or reference.is_relative_to(home) or report.is_relative_to(reference) or reference.is_relative_to(report):
                raise ValueError('Read-only reference wheels must not overlap home/report')
        report.mkdir(parents=True,exist_ok=False)
    except Exception as error:
        print('CELLBENDER DEPENDENCY SETUP FAILED: '+str(error)+'\n'+RECOVERY,file=sys.stderr)
        return 3
    record=dict(status='started',recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),runtime_lock_sha256=LOCK_SHA,
                allow_install=args.allow_install,allow_download=args.allow_download,installer_sha256=sha(Path(__file__)))
    code=0
    try:record.update(install(args,report))
    except Exception as error:
        code=3;record.update(status='dependency-unavailable-analysis-must-skip',error=type(error).__name__+': '+str(error),required_action=RECOVERY)
    (report/'verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    (report/'WARNINGS.md').write_text('# CELLBENDER '+('DEPENDENCY UNAVAILABLE - SKIP ANALYSIS' if code else 'PRIVATE RUNTIME READY; SCIENTIFIC VALIDATION IS SEPARATE')+'\n\n'+(record.get('error','The pinned private original runtime/version probe passed. No analysis has been run by this installer.'))+'\n\n'+RECOVERY+'\n',encoding='utf-8')
    print(json.dumps(record,indent=2))
    return code


if __name__=='__main__':sys.exit(main())
