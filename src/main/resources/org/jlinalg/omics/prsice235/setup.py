#!/usr/bin/env python3
"""Stage the pinned unmodified PRSice binary in a NEW private runtime directory."""
from pathlib import Path
import argparse, hashlib, json, os, platform, stat, subprocess, sys, urllib.request, zipfile

def identity(path):
    digest=hashlib.sha256();count=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block);count+=len(block)
    return dict(bytes=count,sha256=digest.hexdigest())

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--platform',choices=('windows','linux'),required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--archive',type=Path);p.add_argument('--download',action='store_true',help='Explicit permission to download the exact official release ZIP');p.add_argument('--stage-only',action='store_true',help='Stage bytes without executing; analysis still requires the matching permitted host');a=p.parse_args()
    if bool(a.archive)==a.download:p.error('Choose exactly one of --archive FILE or --download')
    assets=Path(__file__).resolve().parent;profile=json.loads((assets/'SOURCE.json').read_text(encoding='utf-8'));assert profile['profile']=='prsice-2.3.5-9dbf55e'
    target=a.out.absolute();owned=False
    try:
        if target.exists() or target.is_symlink():raise ValueError('Output must be NEW; existing runtime/evidence is preserved')
        if target.parent.resolve()!=target.parent:raise ValueError('Use a physical output parent without aliases')
        archive_name='PRSice_win64.zip' if a.platform=='windows'else'PRSice_linux.zip';executable_name='PRSice_win64.exe'if a.platform=='windows'else'PRSice_linux'
        if a.archive and not a.archive.is_file():raise FileNotFoundError('Missing permitted archive; dependent analysis skipped. Supply the exact release ZIP or use --download only where permitted')
        target.mkdir();owned=True;archive=a.archive.resolve()if a.archive else target/archive_name
        if a.download:
            url='https://github.com/choishingwan/PRSice/releases/download/2.3.5/'+archive_name
            request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-PRSice-runtime-setup/1'})
            with urllib.request.urlopen(request,timeout=60)as source,archive.open('xb')as output:
                count=0
                while True:
                    block=source.read(1024*1024)
                    if not block:break
                    count+=len(block)
                    if count>profile['runtime_archives'][archive_name]['bytes']:raise ValueError('Download exceeds pinned archive size; partial download retained')
                    output.write(block)
        if identity(archive)!=profile['runtime_archives'][archive_name]:raise ValueError('Original archive differs from exact release pin; restore the expected asset, never refresh old hashes')
        executable=target/executable_name
        with zipfile.ZipFile(archive)as zipped:
            members=zipped.infolist()
            if len(members)>100 or len({m.filename for m in members})!=len(members):raise ValueError('Duplicate or excessive ZIP namespace')
            for m in members:
                if m.filename.startswith('/')or'\\'in m.filename or':'in m.filename or any(x in ('','.','..')for x in m.filename.split('/'))or stat.S_ISLNK(m.external_attr>>16):raise ValueError('Unsafe ZIP member')
            member=zipped.getinfo(executable_name)
            if member.file_size!=profile['runtime_executables'][a.platform]['bytes']:raise ValueError('Executable member size differs')
            with zipped.open(member)as source,executable.open('xb')as output:
                count=0
                while True:
                    block=source.read(1024*1024)
                    if not block:break
                    count+=len(block)
                    if count>member.file_size:raise ValueError('ZIP member expanded beyond its pin')
                    output.write(block)
        if identity(executable)!=profile['runtime_executables'][a.platform]:raise ValueError('Original binary differs from exact pin')
        if a.platform=='linux':executable.chmod(executable.stat().st_mode|stat.S_IXUSR)
        for name in ('SOURCE.json','LICENSE-upstream.txt'):(target/name).write_bytes((assets/name).read_bytes())
        record=dict(status='STAGED_NOT_EXECUTED',platform=a.platform,archive=identity(archive),executable=identity(executable),analysis_executed=False)
        if not a.stage_only:
            host='windows'if os.name=='nt'else'linux'if sys.platform.startswith('linux')else'unsupported'
            if host!=a.platform or platform.machine().lower()not in ('amd64','x86_64'):raise PermissionError('Staged runtime cannot execute on this host. Use the matching x86_64 host or retain the skip; --stage-only explicitly stages without execution')
            env={k:v for k,v in os.environ.items()if k in ('SystemRoot','WINDIR','COMSPEC')};env.update(LANG='C',LC_ALL='C',TMPDIR=str(target),TEMP=str(target),TMP=str(target))
            with(target/'version.stdout.log').open('xb')as out,(target/'version.stderr.log').open('xb')as err:run=subprocess.run([str(executable),'--version'],cwd=target,env=env,stdout=out,stderr=err,timeout=30)
            text=(target/'version.stdout.log').read_text(encoding='utf-8')+(target/'version.stderr.log').read_text(encoding='utf-8')
            if run.returncode!=0 or '2.3.5 (2021-09-20)'not in text:raise ValueError('Original version check failed; do not use partial runtime')
            if identity(executable)!=record['executable']:raise ValueError('Executable changed during version check')
            record['status']='READY_ORIGINAL_VERSION_CHECKED'
        (target/'SETUP.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(record['status']);print('No analysis was run. Keep this runtime and raw logs private; use prsice-tools with an explicit scientific request.');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError,OSError))and not isinstance(error,ValueError)else 2
        message=('SKIP3_DEPENDENT_ANALYSIS_SKIPPED'if code==3 else'FAIL2_RUNTIME_NOT_ADMITTED')+': '+str(error)+'\nRequired action: retain partial evidence, obtain the exact permitted runtime on the matching host, and retry NEW output. If download/install/execute is prohibited, skip the dependent analysis.\n'
        if owned:(target/'FAILURE.md').write_text(message,encoding='utf-8')
        print(message,file=sys.stderr);return code
if __name__=='__main__':sys.exit(main())
