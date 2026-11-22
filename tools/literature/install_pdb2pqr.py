#!/usr/bin/env python3
"""Explicit private installation of the pinned original PDB2PQR3.8.0 wheel family.

No structure is downloaded or sent to a service. Installation and public-wheel
download permissions are separate. Analysis never installs software. The private
venv,original package/data members and shared CPython base are all verified.
"""
from __future__ import annotations
import argparse,ctypes,datetime as dt,hashlib,json,os,platform,shutil,subprocess,sys,urllib.request,uuid
from pathlib import Path

HERE=Path(__file__).resolve().parent
LOCK_BYTES=(HERE/'pdb2pqr-runtime-lock.json').read_bytes()
LOCK=json.loads(LOCK_BYTES)
LOCK_SHA=hashlib.sha256(LOCK_BYTES).hexdigest()
MARKER='.jlinalg-pdb2pqr.json'
RECOVERY=('Required action: on Windows x86_64 supply CPython3.12 x64 and run python '
          'tools/literature/install_pdb2pqr.py --python CPYTHON312_EXECUTABLE --home '
          'NEW_PRIVATE_HOME --cache WHEEL_CACHE --allow-install;add --allow-download '
          'only when public-wheel downloads are permitted. Supply the admitted home '
          'to pdb2pqr-tools. If installation is forbidden or the exact dependencies '
          'are unavailable, SKIP preparation until an administrator supplies an admitted '
          'runtime. Existing/changed homes,cache files and failed reports are preserved. '
          'Never re-seal a changed home;correct the cause and use a new path. No '
          'automatic Python upgrade,global package change or registration is performed.')
BOOTSTRAP="import sys,runpy;sys.path.insert(0,sys.argv.pop(1));sys.argv[0]='pdb2pqr';runpy.run_module('pdb2pqr',run_name='__main__')"
PROBE="import sys,json,struct,importlib.metadata;sys.path.insert(0,sys.argv[1]);import pdb2pqr,propka,numpy;print(json.dumps(dict(implementation=sys.implementation.name,version=list(sys.version_info[:3]),bits=8*struct.calcsize('P'),base_prefix=sys.base_prefix,packages={p:importlib.metadata.version(p) for p in ['pdb2pqr','propka','numpy']})))"

def sha(path):
    digest=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):digest.update(block)
    return digest.hexdigest()

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def safe(path):
    path=Path(path).absolute()
    for parent in [path,*path.parents]:
        if parent.is_symlink() or (hasattr(parent,'is_junction') and parent.is_junction()):raise ValueError('Linked/junction runtime path is unsupported: '+str(parent))
    return path

def ignored(path):return '__pycache__' in path.parts or path.suffix.lower() in {'.pyc','.pyo'}

def tree(home):
    files={}
    for parent,dirs,names in os.walk(home,followlinks=False):
        for name in dirs:
            path=Path(parent)/name;safe(path)
        dirs[:]=[name for name in dirs if name!='__pycache__']
        for name in names:
            path=Path(parent)/name;relative=path.relative_to(home)
            if ignored(relative) or relative.as_posix()==MARKER:continue
            safe(path)
            if not path.is_file():raise ValueError('Nonregular runtime member: '+str(path))
            files[relative.as_posix()]=sha(path)
            if len(files)>30000:raise ValueError('Private runtime exceeds30000 files')
    return files

def base_tree(root):
    root=safe(root);selected=set()
    for path in root.iterdir():
        if path.is_file() and path.suffix.lower() in {'.exe','.dll','.zip','._pth'}:selected.add(path)
    for folder in ['DLLs','Lib']:
        for parent,dirs,names in os.walk(root/folder,followlinks=False):
            dirs[:]=[name for name in dirs if name not in {'site-packages','__pycache__'}]
            for name in names:
                path=Path(parent)/name
                if path.suffix.lower() in {'.py','.pyd','.dll','.zip'}:selected.add(path)
    if not selected or len(selected)>10000:raise ValueError('Invalid shared CPython base inventory size')
    result={}
    for path in sorted(selected):safe(path);result[path.relative_to(root).as_posix()]=sha(path)
    return result

def verify(home):
    safe(home);marker=home/MARKER
    if not marker.is_file() or marker.stat().st_size>8388608:raise ValueError('Missing/oversized PDB2PQR admission marker')
    data=json.loads(marker.read_text(encoding='utf-8'));before=sha(marker)
    if data.get('profile')!=LOCK['profile'] or data.get('runtime_lock_sha256')!=LOCK_SHA or data.get('home')!=str(home):raise ValueError('PDB2PQR marker differs from admitted profile/home')
    if data.get('wheels')!={name:row['sha256'] for name,row in LOCK['wheels'].items()}:raise ValueError('PDB2PQR wheel family differs')
    files=tree(home)
    if files!=data.get('files'):raise ValueError('Private PDB2PQR files changed or unlisted files appeared')
    for name,digest in LOCK['source_members'].items():
        if files.get(name)!=digest:raise ValueError('Original package/data member differs: '+name)
    if files.get('runtime-lock.json')!=LOCK_SHA:raise ValueError('Installed PDB2PQR lock differs')
    for name,digest in LOCK['notices'].items():
        if files.get('upstream-notices/'+name)!=digest:raise ValueError('Required upstream license notice differs')
    base=data.get('base_python',{});prefix=safe(base['prefix'])
    if str(prefix.resolve())!=base['real_prefix'] or base_tree(prefix)!=base['files']:raise ValueError('Shared CPython base changed;never silently re-admit it')
    if any(Path(name).exists() for name in base['absent_paths']):raise ValueError('A previously absent base import path appeared')
    configuration={}
    for line in (home/'venv/pyvenv.cfg').read_text().splitlines():
        if '=' in line:
            key,value=line.split('=',1);configuration[key.strip()]=value.strip()
    if configuration.get('include-system-site-packages')!='false' or str(Path(configuration.get('home','')).resolve())!=base['real_prefix']:raise ValueError('Private venv no longer uses the admitted isolated base')
    if sha(marker)!=before:raise ValueError('PDB2PQR marker changed during verification')
    return data

def environment(report):
    buffer=ctypes.create_unicode_buffer(32768)
    if not ctypes.windll.kernel32.GetWindowsDirectoryW(buffer,len(buffer)):raise OSError('Windows directory lookup failed')
    windows=Path(buffer.value);ctypes.windll.kernel32.SetErrorMode(0x0001|0x0002|0x8000)
    env={key:value for key,value in os.environ.items() if not key.upper().startswith(('PYTHON','PDB2PQR','PROPKA','APBS','LD_','DYLD_'))}
    empty=report/'empty-home';empty.mkdir()
    env.update(PATH=str(windows/'System32')+';'+str(windows),HOME=str(empty),USERPROFILE=str(empty),LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',PIP_CONFIG_FILE=os.devnull,PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_NO_CACHE_DIR='1')
    return env

def run(command,report,label,env,seconds=300):
    with (report/(label+'.stdout')).open('xb') as out,(report/(label+'.stderr')).open('xb') as err:
        process=subprocess.Popen(command,cwd=report,env=env,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)
        try:code=process.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            process.kill();process.wait(timeout=15);raise RuntimeError('Owned '+label+' timed out;inspect retained logs')
    if code:raise RuntimeError(label+' returned exit'+str(code)+';inspect '+str(report/(label+'.stderr')))
    return dict(stage=label,command=command,owned_pid=process.pid,exit_code=code,stdout_sha256=sha(report/(label+'.stdout')),stderr_sha256=sha(report/(label+'.stderr')))

def flags(python,report,label):
    prefix=report/(label+'-unused-pycache');prefix.mkdir()
    return [str(python),'-I','-S','-B','-X','utf8','-X','pycache_prefix='+str(prefix)]

def version_probes(home,report,env):
    python=home/'venv/Scripts/python.exe';site=home/'venv/Lib/site-packages';records=[]
    records.append(run(flags(python,report,'imports')+['-c',PROBE,str(site)],report,'imports',env,60));actual=json.loads((report/'imports.stdout').read_text(encoding='utf-8'))
    if actual['implementation']!='cpython' or actual['version'][:2]!=[3,12] or actual['bits']!=64 or actual['packages']!={'pdb2pqr':'3.8.0','propka':'3.5.1','numpy':'1.26.4'}:raise ValueError('Actual isolated interpreter/package versions differ from admitted profile')
    records.append(run(flags(python,report,'version')+['-c',BOOTSTRAP,str(site),'--version'],report,'version',env,60))
    if (report/'version.stdout').read_text().strip()!='pdb2pqr 3.8.0':raise ValueError('Original CLI version differs')
    return actual,records

def install(args,report):
    if platform.system()!='Windows' or platform.machine().lower() not in {'amd64','x86_64'}:raise PermissionError('This admitted profile requires Windows x86_64')
    home=safe(args.home);cache=safe(args.cache);python=safe(args.python)
    for a,b in [(home,cache),(home,report)]:
        if a.is_relative_to(b) or b.is_relative_to(a):raise ValueError('Home must not overlap cache or report')
    env=environment(report)
    if home.exists():
        data=verify(home);actual,probes=version_probes(home,report,env);verify(home)
        if actual!=data['versions']:raise ValueError('Actual isolated runtime version identity changed')
        return dict(status='reused-verified-private-runtime',home=str(home),marker_sha256=sha(home/MARKER),files=len(data['files']),base_files=len(data['base_python']['files']),versions=actual,probes=probes)
    if not args.allow_install:raise PermissionError('Private home is absent and --allow-install was not supplied')
    if not python.is_file():raise PermissionError('A supplied CPython3.12 x64 interpreter is required')
    probes=[run(flags(python,report,'base-info')+['-c',"import sys,struct,json;print(json.dumps(dict(implementation=sys.implementation.name,version=list(sys.version_info[:3]),bits=8*struct.calcsize('P'),prefix=sys.base_prefix,paths=sys.path)))"],report,'base-info',env,60)]
    info=json.loads((report/'base-info.stdout').read_text(encoding='utf-8'))
    if info['implementation']!='cpython' or info['version'][:2]!=[3,12] or info['bits']!=64:raise PermissionError('Supplied interpreter must be CPython3.12 x64;no implicit installation or upgrade occurs')
    base=Path(info['prefix']).absolute();base_files=base_tree(base);absent=sorted(set([str(Path(p).absolute()) for p in info['paths'] if p and not Path(p).exists()]+[str(base/'python312._pth')] if not (base/'python312._pth').exists() else []))
    cache.mkdir(parents=True,exist_ok=True)
    for name,row in LOCK['wheels'].items():
        target=cache/name
        if target.exists():
            safe(target)
            if not target.is_file() or target.stat().st_size!=row['bytes'] or sha(target)!=row['sha256']:raise ValueError('Corrupt/linked cached wheel preserved: '+name)
        else:
            if not args.allow_download:raise PermissionError('A pinned wheel is missing and --allow-download was not supplied: '+name)
            temporary=report/(name+'.partial')
            with urllib.request.urlopen(urllib.request.Request(row['url'],headers={'User-Agent':'JLinAlg-pinned-PDB2PQR-installer/1'}),timeout=120) as src,temporary.open('xb') as dst:
                size=0
                for block in iter(lambda:src.read(1048576),b''):
                    size+=len(block)
                    if size>row['bytes']:raise ValueError('Public wheel download exceeds pinned byte count')
                    dst.write(block)
            if temporary.stat().st_size!=row['bytes'] or sha(temporary)!=row['sha256']:raise ValueError('Public wheel hash/size mismatch;partial retained')
            with temporary.open('rb') as src,target.open('xb') as dst:shutil.copyfileobj(src,dst)
    home.mkdir(parents=True,exist_ok=False);venv=home/'venv'
    probes.append(run([str(python),'-I','-B','-m','venv','--without-pip',str(venv)],report,'create-venv',env))
    requirements=home/'requirements-locked.txt';requirements.write_text(''.join(row['name']+'=='+row['version']+' --hash=sha256:'+row['sha256']+'\n' for row in LOCK['wheels'].values()),encoding='utf-8',newline='\n')
    probes.append(run([str(python),'-I','-B','-m','pip','--python',str(venv/'Scripts/python.exe'),'--disable-pip-version-check','install','--no-index','--no-deps','--no-compile','--require-hashes','--find-links',str(cache),'-r',str(requirements)],report,'install-pinned-wheels',env))
    (home/'runtime-lock.json').write_bytes(LOCK_BYTES);notices=home/'upstream-notices';notices.mkdir()
    for name,digest in LOCK['notices'].items():
        source=HERE/'licenses'/name
        if sha(source)!=digest:raise ValueError('Required upstream license notice differs')
        shutil.copyfile(source,notices/name)
    actual,stages=version_probes(home,report,env);probes+=stages
    if base_tree(base)!=base_files:raise ValueError('Shared interpreter changed during private installation')
    files=tree(home)
    for name,digest in LOCK['source_members'].items():
        if files.get(name)!=digest:raise ValueError('Installed original package member differs: '+name)
    data=dict(schema_version=1,profile=LOCK['profile'],home=str(home),runtime_lock_sha256=LOCK_SHA,wheels={name:row['sha256'] for name,row in LOCK['wheels'].items()},python='venv/Scripts/python.exe',site_packages='venv/Lib/site-packages',files=files,versions=actual,base_python=dict(prefix=str(base),real_prefix=str(base.resolve()),files=base_files,absent_paths=absent,version=info['version']),installed_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    write(home/MARKER,data);verify(home)
    return dict(status='installed-verified-private-original-runtime',home=str(home),marker_sha256=sha(home/MARKER),files=len(files),base_files=len(base_files),versions=actual,probes=probes,scope='Original import/CLI version admission only;scientific preparation and native lineage audits are separate.')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--python',required=True,type=Path);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--report-dir',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args(argv)
    report=safe(args.report_dir or args.cache/('install-attempt-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+uuid.uuid4().hex[:8]));report.mkdir(parents=True,exist_ok=False);record=dict(started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),installer_sha256=sha(__file__),runtime_lock_sha256=LOCK_SHA,allow_install=args.allow_install,allow_download=args.allow_download)
    try:record.update(install(args,report));code=0
    except PermissionError as failure:record.update(status='skipped-unavailable-dependency',error=str(failure));code=3
    except Exception as failure:record.update(status='failed-retained',error=type(failure).__name__+': '+str(failure));code=2
    record.update(exit_code=code,completed_utc=dt.datetime.now(dt.timezone.utc).isoformat(),required_action=RECOVERY);write(report/'verification.json',record)
    (report/'WARNINGS.md').write_text('# PDB2PQR '+('PRIVATE RUNTIME READY;SCIENTIFIC VALIDATION IS SEPARATE' if code==0 else 'DEPENDENCY UNAVAILABLE - SKIP PREPARATION')+'\n\n'+record.get('error','Original package and interpreter identities passed. Force-field,pH,repair,optimization and atom-lineage decisions still require explicit scientific choices.')+'\n\n'+RECOVERY+'\n',encoding='utf-8',newline='\n');print(json.dumps({key:record[key] for key in ['status','exit_code','home','files','base_files','error'] if key in record}));return code

if __name__=='__main__':raise SystemExit(main())
