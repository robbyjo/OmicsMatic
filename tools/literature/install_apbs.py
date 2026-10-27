#!/usr/bin/env python3
"""Explicitly install the untouched official APBS3.5.0 private Windows profile.

The complete archive and every installed member are authenticated. Analysis never
installs software. This installer runs a real one-charge finite-difference probe.
No registration, global PATH changes or Python packages are needed by APBS.
"""
from __future__ import annotations
import argparse,ctypes,datetime as dt,hashlib,json,math,os,platform,re,shutil,stat,subprocess,urllib.request,uuid,zipfile
from pathlib import Path,PurePosixPath

HERE=Path(__file__).resolve().parent
LOCK=HERE/'apbs-runtime-lock.json'
MARKER='.jlinalg-apbs.json'
RECOVERY=('Required action: on Windows x86_64 run python tools/literature/install_apbs.py '
          '--home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --allow-install '
          '(add --allow-download only when permitted). Reuse an unchanged admitted home '
          'or supply the exact pinned APBS-3.5.0.Windows.zip in the cache. If installation '
          'is forbidden or this profile is unavailable, SKIP the APBS analysis. Preserve '
          'changed homes and failed attempts; correct the named issue and use new output paths. '
          'Linux/macOS/alternate versions are not admitted by this Windows profile.')

def digest(path):
    value=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):value.update(block)
    return value.hexdigest()

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def reject_links(path):
    path=Path(path).absolute()
    for part in [path,*path.parents]:
        if part.is_symlink() or (hasattr(part,'is_junction') and part.is_junction()):raise ValueError('Linked/junction paths are unsupported: '+str(part))
    return path

def verify(home,lock,lock_sha):
    reject_links(home);marker=home/MARKER
    if not marker.is_file() or marker.stat().st_size>1048576:raise ValueError('Missing/oversized APBS admission marker')
    record=json.loads(marker.read_text(encoding='utf-8'));marker_sha=digest(marker)
    for key in ['tool','version','profile','archive_sha256']:
        if record.get(key)!=lock[key]:raise ValueError('APBS profile mismatch: '+key)
    expected=dict(lock['reference_files']);expected[lock['license_file']]=lock['license_sha256']
    if record.get('runtime_lock_sha256')!=lock_sha or record.get('files')!=expected:raise ValueError('APBS lock/member map mismatch')
    found={}
    for path in home.rglob('*'):
        reject_links(path)
        if path.is_file() and path!=marker:found[path.relative_to(home).as_posix()]=digest(path)
        if len(found)>4096:raise ValueError('Excessive APBS members')
    if found!=expected or digest(marker)!=marker_sha:raise ValueError('APBS runtime integrity failed: changed,missing or unlisted members')
    return marker_sha

def environment(home):
    buffer=ctypes.create_unicode_buffer(32768)
    if ctypes.windll.kernel32.GetWindowsDirectoryW(buffer,len(buffer))<=0:raise OSError('Windows directory lookup failed')
    windows=Path(buffer.value)
    if not (windows/'System32/kernel32.dll').is_file():raise OSError('Windows System32 dependency is unavailable')
    ctypes.windll.kernel32.SetErrorMode(0x0001|0x0002|0x8000)
    env=os.environ.copy()
    for key in list(env):
        if key.upper().startswith(('PYTHON','APBS','LD_','DYLD_')) or key.upper() in {'CYGWIN','MSYSTEM','LIBRARY_PATH'}:env.pop(key)
    env.update(PATH=str(home/'bin')+';'+str(windows/'System32')+';'+str(windows),LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
    return env

def smoke(home,work,lock):
    work.mkdir();env=environment(home);program=home/lock['executable'];records=[]
    def run(label,arguments,expected):
        command=[str(program),*arguments];r=subprocess.run(command,cwd=work,env=env,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
        (work/(label+'.stdout')).write_bytes(r.stdout);(work/(label+'.stderr')).write_bytes(r.stderr)
        records.append(dict(stage=label,command=command,exit_code=r.returncode,stdout_sha256=digest(work/(label+'.stdout')),stderr_sha256=digest(work/(label+'.stderr'))))
        if r.returncode!=expected:raise RuntimeError('Original APBS '+label+' returned '+str(r.returncode)+';inspect retained logs')
        return (r.stdout+r.stderr).decode('utf-8',errors='replace')
    banner=run('version',['--version'],lock['informational_exit_code'])
    if not re.search(r'\bAPBS\s+3\.5\.0\b',banner):raise ValueError('Original APBS version mismatch')
    (work/'source.pqr').write_text('ATOM 1 ION ION 1 0 0 0 1 2\n',encoding='ascii',newline='\n')
    script='read\n mol pqr source.pqr\nend\nelec name jlinalg\n mg-manual\n dime 33 33 33\n glen 20 20 20\n gcent 0 0 0\n mol 1\n lpbe\n bcfl sdh\n pdie 2\n sdie 80\n chgm spl2\n srfm mol\n srad 1.4\n swin 0.3\n sdens 10\n temp 298.15\n calcenergy total\n calcforce no\n write pot dx potential\nend\nquit\n'
    (work/'input.in').write_text(script,encoding='ascii',newline='\n');output=run('solver',['input.in'],lock['scientific_exit_code'])
    energy=re.findall(r'Total electrostatic energy\s*=\s*([+\-0-9.eE]+)\s*kJ/mol',output)
    if len(energy)!=1 or not math.isclose(float(energy[0]),567.9348802024,rel_tol=2e-6):raise ValueError('Original one-charge energy differs from the independent reference')
    raw=(work/'potential.dx').read_text(encoding='ascii');header=re.search(r'items\s+(\d+)\s+data follows\s*\n',raw)
    if not header or int(header[1])!=35937:raise ValueError('Original smoke grid has unexpected dimensions')
    tokens=raw[header.end():].split();values=[float(x) for x in tokens[:35937]]
    if len(values)!=35937 or not all(map(math.isfinite,values)) or min(values)<=0 or max(values)<100:raise ValueError('Invalid/nonfinite original smoke potential')
    records.append(dict(stage='finite-scientific-grid-audit',grid_points=len(values),potential_sha256=digest(work/'potential.dx'),raw_total_energy_kj_per_mol=float(energy[0]),scope='Raw electrostatic energy includes grid self-energy;not solvation free energy'))
    return records

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--report-dir',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args(argv)
    report=(args.report_dir or args.cache/('install-attempt-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+uuid.uuid4().hex[:8])).absolute();reject_links(report);report.mkdir(parents=True,exist_ok=False)
    record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),allow_install=args.allow_install,allow_download=args.allow_download,home=str(args.home.absolute()),installer_sha256=digest(__file__));code=2
    try:
        raw=LOCK.read_bytes();lock=json.loads(raw);lock_sha=hashlib.sha256(raw).hexdigest();record['runtime_lock_sha256']=lock_sha
        if platform.system()!='Windows' or platform.machine().lower() not in {'amd64','x86_64'}:raise PermissionError('Only the tested Windows x86_64 host is admitted')
        home=reject_links(args.home);cache=reject_links(args.cache)
        if home.exists():
            record['reused_existing_home']=True;record['marker_sha256']=verify(home,lock,lock_sha);record['probes']=smoke(home,report/'smoke',lock);verify(home,lock,lock_sha)
        else:
            if not args.allow_install:raise PermissionError('Private installation is not allowed without --allow-install')
            cache.mkdir(parents=True,exist_ok=True);archive=cache/lock['archive_name']
            if archive.exists():
                reject_links(archive)
                if not archive.is_file() or archive.stat().st_size!=lock['archive_bytes'] or digest(archive)!=lock['archive_sha256']:raise ValueError('Cached APBS archive is corrupt;preserved without replacement')
            else:
                if not args.allow_download:raise PermissionError('Pinned archive is absent and download is not allowed without --allow-download')
                temporary=report/'archive.partial'
                with urllib.request.urlopen(urllib.request.Request(lock['archive_url'],headers={'User-Agent':'JLinAlg-APBS-private-installer/1'}),timeout=120) as source,temporary.open('xb') as destination:
                    count=0
                    for block in iter(lambda:source.read(1048576),b''):
                        count+=len(block)
                        if count>lock['archive_bytes']:raise ValueError('Download exceeds pinned byte count')
                        destination.write(block)
                if temporary.stat().st_size!=lock['archive_bytes'] or digest(temporary)!=lock['archive_sha256']:raise ValueError('APBS download hash/size mismatch;retained for diagnosis')
                if archive.exists():raise ValueError('Cache archive appeared during download;existing file preserved')
                shutil.copyfile(temporary,archive)
            staged=report/'private-staging';staged.mkdir();seen=set();total=0
            with zipfile.ZipFile(archive) as bundle:
                for entry in bundle.infolist():
                    name=entry.filename;relative=PurePosixPath(name)
                    if not name.startswith(lock['archive_prefix']) or relative.is_absolute() or '..' in relative.parts or '\\' in name or ':' in name or stat.S_ISLNK(entry.external_attr>>16):raise ValueError('Unsafe APBS archive member')
                    name=name[len(lock['archive_prefix']):]
                    if entry.is_dir():continue
                    if name.casefold() in seen or name not in lock['reference_files']:raise ValueError('Duplicate/unexpected APBS archive member')
                    seen.add(name.casefold());total+=entry.file_size
                    if total>100*1048576 or entry.file_size>40*1048576:raise ValueError('APBS extraction bound exceeded')
                    data=bundle.read(entry)
                    if hashlib.sha256(data).hexdigest()!=lock['reference_files'][name]:raise ValueError('Original archive member hash differs: '+name)
                    target=staged.joinpath(*PurePosixPath(name).parts);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            if len(seen)!=len(lock['reference_files']):raise ValueError('Original archive is incomplete')
            notice=HERE/'licenses/apbs-3.5.0-LICENSE.md'
            if digest(notice)!=lock['license_sha256']:raise ValueError('APBS license notice differs from lock')
            target=staged/lock['license_file'];target.parent.mkdir();shutil.copyfile(notice,target)
            files=dict(lock['reference_files']);files[lock['license_file']]=lock['license_sha256']
            marker={key:lock[key] for key in ['tool','version','profile','archive_sha256']};marker.update(runtime_lock_sha256=lock_sha,files=files,installed_utc=dt.datetime.now(dt.timezone.utc).isoformat());write(staged/MARKER,marker)
            verify(staged,lock,lock_sha);record['probes']=smoke(staged,report/'smoke',lock);verify(staged,lock,lock_sha)
            home.parent.mkdir(parents=True,exist_ok=True);reject_links(home)
            if home.exists():raise ValueError('Requested home appeared during installation;existing home preserved')
            staged.rename(home);record['marker_sha256']=verify(home,lock,lock_sha)
        record.update(status='passed-private-original-runtime',scientific_scope='Original one-charge solver smoke only;project adapter and model validity require their separate audits');code=0
    except PermissionError as failure:record.update(status='skipped-unavailable-dependency',error=str(failure));code=3
    except Exception as failure:record.update(status='failed-retained',error=str(failure))
    record.update(exit_code=code,completed_utc=dt.datetime.now(dt.timezone.utc).isoformat(),required_action=RECOVERY);write(report/'verification.json',record)
    (report/'WARNINGS.md').write_text('# APBS INSTALLER '+record['status'].upper()+'\n\n'+record.get('error','The original Windows executable is 32bit x86;conservative grids and the host Windows ABI apply. Version/help exit13 is expected only for informational probes.')+'\n\n'+RECOVERY+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(status=record['status'],exit_code=code,report=str(report),error=record.get('error'))));return code

if __name__=='__main__':raise SystemExit(main())
