#!/usr/bin/env python3
"""Explicit private admission of original IGV2.19.8 and a supplied Java21 JDK.

Downloads and installation require separate flags. No global software is changed.
The original archive,helper,license and selected JDK executable/module/DLL/config
family are authenticated. Windows graphics,fonts and system ABI remain host inputs.
"""
from __future__ import annotations
import argparse,ctypes,datetime as dt,hashlib,json,os,platform,shutil,stat,subprocess,urllib.request,uuid,zipfile
from pathlib import Path,PurePosixPath

HERE=Path(__file__).resolve().parent
LOCK=HERE/'igv-runtime-lock.json'
MARKER='.jlinalg-igv.json'
RECOVERY=('Required action: on Windows x86_64 supply a full Java21 JDK and usable Windows '
          'graphics/fonts;run python tools/literature/install_igv.py --home NEW_ASCII_PRIVATE_HOME '
          '--cache OFFICIAL_ARCHIVE_CACHE --java FULL_JAVA21_EXECUTABLE --allow-install;add '
          '--allow-download only when permitted. Supply the admitted home to igv-tools. '
          'If dependencies or installation are unavailable/forbidden,SKIP this analysis until '
          'an administrator supplies an admitted runtime. Preserve changed homes/caches and '
          'failed attempts;never re-seal modified software. Use ASCII runtime/report/temp paths;'
          'data/output paths travel inside UTF8 JSON. Correct the named issue and use a new path.')

def digest(path):
    value=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):value.update(block)
    return value.hexdigest()

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def safe(path,ascii_only=False):
    path=Path(path).absolute()
    if ascii_only and not str(path).isascii():raise ValueError('Require ASCII-named runtime/report/JDK paths: '+str(path))
    for part in [path,*path.parents]:
        if part.is_symlink() or (hasattr(part,'is_junction') and part.is_junction()):raise ValueError('Linked/junction paths are unsupported: '+str(part))
    if path.exists() and path.resolve()!=path:raise ValueError('Aliased runtime/JDK path: '+str(path))
    return path

def java_identity(executable):
    executable=safe(executable,True)
    if executable.name.lower()!='java.exe' or executable.parent.name.lower()!='bin':raise ValueError('Supply the full Java21 JDK bin/java.exe')
    home=executable.parent.parent
    required=['release','lib/modules','lib/jvm.cfg','lib/jrt-fs.jar','bin/java.exe','bin/javac.exe','bin/server/jvm.dll','bin/awt.dll','bin/fontmanager.dll','conf/security/java.security']
    members=set(home/name for name in required)
    members.update(path for path in (home/'bin').rglob('*') if path.is_file() and path.suffix.lower() in {'.exe','.dll'})
    members.update(path for path in (home/'conf').rglob('*') if path.is_file())
    if not 10<=len(members)<=300:raise ValueError('Unexpected Java core file count')
    result={}
    for path in sorted(members):
        safe(path,True)
        if not path.is_file():raise ValueError('Required full JDK member unavailable: '+str(path))
        result[str(path)]=digest(path)
    return executable,result

def verify(home,lock,lock_sha,expected_java=None):
    safe(home,True);marker=home/MARKER
    if not marker.is_file() or marker.stat().st_size>1048576:raise ValueError('Missing/oversized IGV admission marker')
    marker_sha=digest(marker);record=json.loads(marker.read_text(encoding='utf-8'));expected=dict(lock['files']);expected['runtime-lock.json']=lock_sha
    if record.get('profile')!=lock['profile'] or record.get('home')!=str(home) or record.get('runtime_lock_sha256')!=lock_sha or record.get('files')!=expected:raise ValueError('IGV admission profile/home/file family differs')
    files={}
    for path in home.rglob('*'):
        safe(path)
        if path.is_file() and path!=marker:files[path.relative_to(home).as_posix()]=digest(path)
        if len(files)>3000:raise ValueError('Excessive IGV inventory')
    if files!=expected:raise ValueError('IGV original/helper inventory changed,missing or unlisted')
    java=Path(record.get('java_executable',''))
    if expected_java is not None and java!=expected_java:raise ValueError('Selected JDK differs from existing admission')
    executable,core=java_identity(java)
    if record.get('java_files')!=core or digest(marker)!=marker_sha:raise ValueError('Admitted Java core or marker changed;preserve home')
    return marker_sha,executable

def probe(home,java,report,lock):
    work=report/'actual-probe';work.mkdir();user=work/'user';user.mkdir();scratch=work/'scratch';scratch.mkdir();buffer=ctypes.create_unicode_buffer(32768)
    if ctypes.windll.kernel32.GetWindowsDirectoryW(buffer,len(buffer))<=0:raise OSError('Windows directory lookup failed')
    windows=Path(buffer.value);env=os.environ.copy()
    for key in list(env):
        if key.upper() in {'JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH'} or key.upper().startswith(('IGV_','AWS_','GOOGLE_','LD_','DYLD_')):env.pop(key)
    env.update(SystemRoot=str(windows),WINDIR=str(windows),PATH=str(java.parent)+';'+str(windows/'System32'),HOME=str(user),USERPROFILE=str(user),TEMP=str(scratch),TMP=str(scratch));ctypes.windll.kernel32.SetErrorMode(0x1|0x2|0x8000)
    command=[str(java),'-Xmx1g','-Dfile.encoding=UTF-8','-Duser.language=en','-Duser.country=US','-Duser.home='+str(user),'-Djava.io.tmpdir='+str(scratch),'-Djava.net.useSystemProxies=false','-Dsun.java2d.uiScale=1',*lock['jvm_arguments'],'--module-path',str(home/'lib'),'--add-modules','org.igv',str(home/'jlinalg-worker/IgvWorker.java'),'--probe']
    r=subprocess.run(command,cwd=work,env=env,capture_output=True,timeout=90,creationflags=subprocess.CREATE_NO_WINDOW);(work/'probe.stdout').write_bytes(r.stdout);(work/'probe.stderr').write_bytes(r.stderr)
    if r.returncode:raise PermissionError('Original IGV/Java21/graphics probe failed;inspect actual-probe/probe.stderr')
    actual=json.loads(r.stdout.decode('utf-8'))
    if actual.get('igv')!='2.19.8' or actual.get('build')!='14 (14)' or actual.get('java_feature')!=21 or actual.get('headless') is not False or actual.get('os_arch') not in {'amd64','x86_64'}:raise ValueError('Actual original/Java/graphics identity differs')
    return dict(command=command,exit_code=r.returncode,actual=actual,stdout_sha256=digest(work/'probe.stdout'),stderr_sha256=digest(work/'probe.stderr'),scope='Actual original identity/source compiler/hidden graphics-frame probe;scientific rendering requires the project native audit')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--java',required=True,type=Path);parser.add_argument('--report-dir',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args(argv)
    report=safe(args.report_dir or args.cache/('igv-install-attempt-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+uuid.uuid4().hex[:8]),True);report.mkdir(parents=True,exist_ok=False);record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),allow_install=args.allow_install,allow_download=args.allow_download,installer_sha256=digest(__file__));code=2
    try:
        raw=LOCK.read_bytes();lock=json.loads(raw);lock_sha=hashlib.sha256(raw).hexdigest();record['runtime_lock_sha256']=lock_sha
        if platform.system()!='Windows' or platform.machine().lower() not in {'amd64','x86_64'}:raise PermissionError('Only the Windows x86_64 profile is admitted')
        home=safe(args.home,True);cache=safe(args.cache,True);java=safe(args.java,True);record['home']=str(home)
        if home.exists():
            record['reused_existing_home']=True;record['marker_sha256'],java=verify(home,lock,lock_sha,java);record['probe']=probe(home,java,report,lock);verify(home,lock,lock_sha,java)
        else:
            if not args.allow_install:raise PermissionError('Private installation requires --allow-install')
            java,java_files=java_identity(java);cache.mkdir(parents=True,exist_ok=True);archive=cache/lock['archive_name']
            if archive.exists():
                safe(archive)
                if not archive.is_file() or archive.stat().st_size!=lock['archive_bytes'] or digest(archive)!=lock['archive_sha256']:raise ValueError('Cached original IGV archive is corrupt;preserved')
            else:
                if not args.allow_download:raise PermissionError('Official archive absent;download requires --allow-download')
                partial=report/'archive.partial'
                with urllib.request.urlopen(urllib.request.Request(lock['archive_url'],headers={'User-Agent':'JLinAlg-IGV-private-installer/1'}),timeout=120) as source,partial.open('xb') as output:
                    size=0
                    for block in iter(lambda:source.read(1048576),b''):
                        size+=len(block)
                        if size>lock['archive_bytes']:raise ValueError('Original IGV download exceeds pinned byte count')
                        output.write(block)
                if partial.stat().st_size!=lock['archive_bytes'] or digest(partial)!=lock['archive_sha256']:raise ValueError('Original IGV download hash/size differs;retained')
                if archive.exists():raise ValueError('Cache appeared during download;preserved')
                with partial.open('rb') as source,archive.open('xb') as target:shutil.copyfileobj(source,target)
            staged=report/'private-staging';staged.mkdir();seen=set();total=0
            with zipfile.ZipFile(archive) as bundle:
                for entry in bundle.infolist():
                    relative=PurePosixPath(entry.filename)
                    if not entry.filename.startswith(lock['archive_prefix']) or relative.is_absolute() or any(p in {'..','.'} for p in relative.parts) or '\\' in entry.filename or ':' in entry.filename or stat.S_ISLNK(entry.external_attr>>16):raise ValueError('Unsafe original archive path')
                    if entry.is_dir():continue
                    name=entry.filename[len(lock['archive_prefix']):]
                    if name.casefold() in seen or name not in lock['original_files']:raise ValueError('Duplicate/unexpected original IGV member')
                    seen.add(name.casefold());total+=entry.file_size
                    if total>200*1048576 or entry.file_size>80*1048576:raise ValueError('Original extraction bound exceeded')
                    data=bundle.read(entry)
                    if hashlib.sha256(data).hexdigest()!=lock['original_files'][name]:raise ValueError('Original member hash differs: '+name)
                    target=staged.joinpath(*PurePosixPath(name).parts);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            if len(seen)!=len(lock['original_files']):raise ValueError('Original archive incomplete')
            helpers={lock['worker_file']:HERE.parent.parent/'src/main/resources/org/jlinalg/omics/IgvWorker.java',lock['license_file']:HERE/'licenses/igv-2.19.8-LICENSE.txt'}
            for relative,source in helpers.items():
                if digest(source)!=lock['files'][relative]:raise ValueError('Bundled helper/license differs from pinned lock')
                target=staged/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
            (staged/'runtime-lock.json').write_bytes(raw);files=dict(lock['files']);files['runtime-lock.json']=lock_sha
            marker=dict(profile=lock['profile'],home=str(home),runtime_lock_sha256=lock_sha,java_executable=str(java),java_files=java_files,files=files,installed_utc=dt.datetime.now(dt.timezone.utc).isoformat());write(staged/MARKER,marker)
            # The final home identity is sealed before publication;probe originals at
            # staging,then publish only if every member and selected JDK is unchanged.
            record['probe']=probe(staged,java,report,lock)
            for relative,sha in files.items():
                if digest(staged/relative)!=sha:raise ValueError('IGV staging changed during probe')
            if java_identity(java)[1]!=java_files:raise ValueError('Selected JDK changed during probe')
            home.parent.mkdir(parents=True,exist_ok=True);safe(home,True)
            if home.exists():raise ValueError('Home appeared during installation;preserved')
            staged.rename(home);record['marker_sha256'],_=verify(home,lock,lock_sha,java)
        record.update(status='passed-private-original-runtime',host_boundary='Windows graphics/fonts/system ABI are host inputs outside the Java core seal;no general OS/network sandbox claim');code=0
    except PermissionError as failure:record.update(status='skipped-unavailable-dependency',error=str(failure));code=3
    except Exception as failure:record.update(status='failed-retained',error=str(failure))
    record.update(exit_code=code,completed_utc=dt.datetime.now(dt.timezone.utc).isoformat(),required_action=RECOVERY);write(report/'verification.json',record);(report/'WARNINGS.md').write_text('# IGV INSTALLER '+record['status'].upper()+'\n\n'+record.get('error','Actual identity/graphics probe passed;scientific output still requires native audit. Windows graphics/fonts/system ABI remain host inputs.')+'\n\n'+RECOVERY+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(status=record['status'],exit_code=code,report=str(report),error=record.get('error'))));return code

if __name__=='__main__':raise SystemExit(main())
