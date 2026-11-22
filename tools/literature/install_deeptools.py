"""Private pinned deepTools3.5.6 runtime; preserve failures and skip unavailable analyses."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
LOCK=Path(__file__).with_name('deeptools-runtime-lock.json')
BRIDGE=ROOT/'src/main/resources/org/jlinalg/omics/deeptools_bridge.py'
MARKER='.jlinalg-deeptools.json'

def digest(path):
    value=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):value.update(block)
    return value.hexdigest()

def cygpath(path):
    text=str(Path(path).resolve()).replace('\\','/')
    if len(text)<3 or text[1:3]!=':/':raise ValueError('Private Cygwin profile requires a local absolute drive path: '+text)
    return '/cygdrive/'+text[0].lower()+text[2:]

def environment(attempt,kind,font_cache=None):
    env=os.environ.copy()
    for name in ['PYTHONHOME','PYTHONPATH','MPLCONFIGDIR','MATPLOTLIBRC']:env.pop(name,None)
    env.update(PYTHONNOUSERSITE='1',PYTHONDONTWRITEBYTECODE='1',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MPLBACKEND='Agg')
    cache=attempt/'mpl-cache';cache.mkdir()
    if font_cache:
        templates=list(Path(font_cache).glob('fontlist*.json'))
        if len(templates)!=1:raise RuntimeError('Pinned Cygwin font-cache template unavailable; skip analysis and rebuild the prepared runtime')
        for template in templates:shutil.copyfile(template,cache/template.name)
    env['MPLCONFIGDIR']=cygpath(cache) if kind=='cygwin' else str(cache)
    return env

def execute(command,env,log,timeout=300):
    with log.open('w',encoding='utf-8') as stream:
        result=subprocess.run(command,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=timeout)
    if result.returncode:raise RuntimeError('Runtime command failed, exit'+str(result.returncode)+'; inspect '+str(log))

def members(roots):
    result={}
    for root in roots:
        root=Path(root).resolve()
        if not root.exists():raise RuntimeError('Declared runtime source absent: '+str(root))
        paths=[root] if root.is_file() else root.rglob('*')
        for path in paths:
            if path.is_file():result[str(path.resolve())]=digest(path)
    if not result:raise RuntimeError('Runtime seal contains no files')
    return result

def verified(profile):
    files=profile.get('files')
    return isinstance(files,dict) and bool(files) and all(Path(name).is_file() and digest(name)==value for name,value in files.items())

def probe(profile,home,attempt,lock):
    kind=profile['kind'];report=attempt/'runtime-probe.json'
    arguments=['--report',cygpath(report) if kind=='cygwin' else str(report)]
    script=cygpath(home/'deeptools_bridge.py') if kind=='cygwin' else str(home/'deeptools_bridge.py')
    execute([*profile['python_command'],script,*arguments],environment(attempt,kind,profile.get('font_cache_template')),attempt/'runtime-probe.log')
    if not report.is_file():raise RuntimeError('Runtime exited without a probe report; do not accept exit0 as success')
    data=json.loads(report.read_text(encoding='utf-8'))
    if data.get('status')!='probe-completed' or data.get('four_loaded_runtime_forks_and_eigenvalues')!='passed':raise RuntimeError('Runtime exited before dependency/fork completion; inspect runtime-probe.json/log')
    if data['versions']!=lock['critical_versions']:raise RuntimeError('Actual dependency versions differ from the tested pinned profile')
    if data['source_sha256']!=lock['source_modules_sha256']:raise RuntimeError('Original deepTools scientific source differs from the pinned wheel')
    if profile.get('expected_prefix') and data['prefix']!=profile['expected_prefix']:raise RuntimeError('Selected interpreter prefix differs from the prepared private environment')
    if data['executable_sha256']!=digest(profile['python_executable']):raise RuntimeError('Actual selected Python executable differs from the sealed interpreter')
    return data

def prepare_cygwin(spec,home):
    if spec.get('schema')!='jlinalg-deeptools-cygwin-prepared-v1':raise ValueError('Unrecognized prepared Cygwin profile')
    for name in ['python_cyg','python_executable','shell','expected_prefix','path_entries','python_paths','seal_roots','font_cache','matplotlib_data']:spec[name]
    python=Path(spec['python_executable']).resolve();shell=Path(spec['shell']).resolve()
    if not python.is_file() or not shell.is_file():raise RuntimeError('Prepared interpreter or Cygwin shell unavailable')
    if not spec['path_entries'] or not spec['python_paths']:raise ValueError('Prepared Cygwin profile must explicitly declare DLL and module paths')
    template=Path(spec['font_cache']).resolve()
    cache=json.loads(template.read_text(encoding='utf-8'))
    if cache.get('_version')!=390:raise RuntimeError('Prepared font cache must match the validated Matplotlib3.9.4/v390 profile')
    cache_home=home/'font-cache-template';cache_home.mkdir();shutil.copyfile(template,cache_home/template.name)
    fonts=[]
    for entry in cache.get('ttflist',[])+cache.get('afmlist',[]):
        name=entry['fname']
        if name.startswith('/cygdrive/') and len(name)>12:font=Path(name[10].upper()+':/'+name[12:])
        elif name.startswith('/usr/'):font=Path('C:/cygwin64')/name.lstrip('/')
        else:font=Path(name) if Path(name).is_absolute() else Path(spec['matplotlib_data'])/name
        if not font.is_file():raise RuntimeError('Prepared font-cache member unavailable: '+str(font))
        fonts.append(str(font.resolve()))
    if not fonts:raise RuntimeError('Prepared font cache contains no fonts')
    launcher=home/'launch.sh'
    text='set -euo pipefail\n'
    for key,value in {'PATH':':'.join(spec['path_entries']), 'PYTHONPATH':':'.join(spec['python_paths']),
                      'LD_LIBRARY_PATH':':'.join(spec.get('library_paths',[cygpath(python.parent)])),
                      'PYTHONNOUSERSITE':'1','PYTHONDONTWRITEBYTECODE':'1','OPENBLAS_NUM_THREADS':'1','OMP_NUM_THREADS':'1','MPLBACKEND':'Agg'}.items():
        text+='export '+key+'='+shlex.quote(value)+'\n'
    text+=shlex.quote(spec['python_cyg'])+' "$@"\n'
    with launcher.open('w',encoding='utf-8',newline='\n') as stream:stream.write(text)
    return dict(kind='cygwin',python_executable=str(python),expected_prefix=spec['expected_prefix'],
                python_command=[str(shell),'--noprofile','--norc',cygpath(launcher)],
                font_cache_template=str(cache_home),
                seal_roots=[*spec['seal_roots'],*fonts,str(cache_home),str(shell),str(python),str(launcher),str(home/'deeptools_bridge.py')],
                preparation_provenance=spec.get('provenance',{}))

def install(args):
    home=args.environment.resolve();report=args.report.resolve();report.mkdir(parents=True,exist_ok=True)
    attempt=report/'attempts'/dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ');attempt.mkdir(parents=True)
    lock=json.loads(LOCK.read_text(encoding='utf-8'));record=dict(environment=str(home),attempt=str(attempt),download_authorized=args.allow_download,source_build_authorized=args.allow_source_build,version=lock['version'],lock_sha256=digest(LOCK),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    try:
        if home.exists():
            profile=json.loads((home/MARKER).read_text(encoding='utf-8'))
            if profile.get('lock_sha256')!=digest(LOCK) or not verified(profile):raise RuntimeError('Existing unmanaged/changed runtime preserved; choose a new --environment')
            reused=True
        else:
            reused=False
            if args.prepared_cygwin:
                spec=json.loads(args.prepared_cygwin.read_text(encoding='utf-8'))
                home.mkdir(parents=True);shutil.copyfile(BRIDGE,home/'deeptools_bridge.py');profile=prepare_cygwin(spec,home)
            else:
                if not args.allow_download:raise PermissionError('Runtime absent; explicit --allow-download is required to install dependencies')
                if sys.platform=='win32':raise RuntimeError('This pinned native-Windows installer profile is unavailable. Use a prepared private Cygwin profile or a permitted supported Linux/macOS runtime; original native-Windows dependency attempts failed and are retained.')
                home.mkdir(parents=True);python=args.base_python or Path(sys.executable)
                execute([str(python),'-m','venv',str(home/'python')],os.environ.copy(),attempt/'create-venv.log')
                selected=home/'python/bin/python';shutil.copyfile(BRIDGE,home/'deeptools_bridge.py')
                command=[str(selected),'-m','pip','install','--disable-pip-version-check','--report',str(attempt/'pip-install.json')]
                if not args.allow_source_build:command+=['--only-binary=:all:']
                command += [name+'=='+version for name,version in lock['critical_versions'].items()]
                env=os.environ.copy();env.update(PIP_REQUIRE_VIRTUALENV='true',PYTHONNOUSERSITE='1')
                execute(command,env,attempt/'pip-install.log',3600)
                profile=dict(kind='native',python_executable=str(selected.resolve()),python_command=[str(selected)],seal_roots=[str(home/'python'),str(home/'deeptools_bridge.py')])
            profile.update(schema='jlinalg-deeptools-runtime-v1',version=lock['version'],lock_sha256=digest(LOCK),critical_versions=lock['critical_versions'],source_modules_sha256=lock['source_modules_sha256'])
        data=probe(profile,home,attempt,lock)
        if not reused:
            profile['files']=members(profile.pop('seal_roots'))
            if not verified(profile):raise RuntimeError('Runtime source changed while sealing')
            (home/MARKER).write_text(json.dumps(profile,indent=2)+'\n',encoding='utf-8')
        elif not verified(profile):raise RuntimeError('Runtime member changed during the availability probe')
        record.update(status='available-not-yet-adapter-validated',runtime_reused=reused,kind=profile['kind'],sealed_files=len(profile['files']),marker_sha256=digest(home/MARKER),probe_status=data['status'],four_loaded_runtime_forks=data['four_loaded_runtime_forks_and_eigenvalues'])
    except Exception as failure:
        record.update(status='skipped-install-not-permitted' if isinstance(failure,PermissionError) else 'skipped-dependency-unavailable',error=str(failure))
    record['required_action']='Skip affected original BAM/BigWig/matrix/plot analyses while unavailable. Correct the named dependency/integrity/startup error and inspect retained attempt logs. On supported Linux/macOS use --allow-download and, only where permitted, --allow-source-build. Windows requires an explicitly prepared private Cygwin profile with its validated immutable font-cache template and private libpython search path. Preserve changed/failed homes and use a new --environment; do not modify global installations. Native Java supplied-track and qnorm methods require no such runtime.'
    record['scope']='Private installation or sealing/reuse of explicitly prepared existing software only. Version/source/fork probes do not establish scientific/Java-adapter or full-paper parity. Failed partial homes/logs are retained; no OS/global package/PATH installation.'
    destination=report/'dependency-status.json'
    if destination.exists():
        old=json.loads(destination.read_text(encoding='utf-8'));record['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    for destination in [destination,attempt/'dependency-status.json']:destination.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    skipped=record['status'].startswith('skipped')
    (report/'WARNINGS.md').write_text(('# ANALYSIS SKIPPED - DEEPTOOLS RUNTIME UNAVAILABLE\n\n'+record.get('error','') if skipped else '# Runtime available; original scientific and Java-adapter validation are separate')+'\n\nRequired action: '+record['required_action']+'\n\n'+record['scope']+'\n',encoding='utf-8')
    print(json.dumps(record));return 3 if skipped else 0

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--environment',type=Path,default=ROOT/'build/omics-deeptools-runtime')
    parser.add_argument('--report',type=Path,default=ROOT/'build/literature-review/deeptools-dependency')
    parser.add_argument('--prepared-cygwin',type=Path)
    parser.add_argument('--base-python',type=Path)
    parser.add_argument('--allow-download',action='store_true')
    parser.add_argument('--allow-source-build',action='store_true')
    return install(parser.parse_args())

if __name__=='__main__':sys.exit(main())
