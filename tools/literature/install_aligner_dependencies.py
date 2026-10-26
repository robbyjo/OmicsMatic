"""Private Windows aligners: official Bowtie 2 binaries or unmodified BWA source."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LOCK = Path(__file__).with_name('msys2-bwa-build-lock.json')
MARKER = '.jlinalg-aligner.json'
BOWTIE = {'version':'2.5.5','filename':'bowtie2-2.5.5-mingw-x86_64.zip',
    'url':'https://github.com/BenLangmead/bowtie2/releases/download/v2.5.5/bowtie2-2.5.5-mingw-x86_64.zip',
    'sha256':'77bfac3a84c54f4032ee851c19917802decc1b28083ce17a5f92a0c4d729bdba','size':56525994}
BWA = {'version':'0.7.19','filename':'bwa-0.7.19.tar.gz','url':'https://github.com/lh3/bwa/archive/refs/tags/v0.7.19.tar.gz',
    'sha256':'cdff5db67652c5b805a3df08c4e813a822c65791913eccfb3cf7d528588f37bc'}


def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def verified(environment, marker, expected):
    try:
        record=json.loads((environment/marker).read_text(encoding='utf-8'))
        if record['identity']!=expected or not record['files']:return False
        for name,sha in record['files'].items():
            path=(environment/name).resolve()
            if not path.is_relative_to(environment) or not path.is_file() or digest(path)!=sha:return False
        return True
    except (OSError,ValueError,KeyError):return False


def download(item,cache,report):
    path=cache/item['filename']
    if not path.exists():
        cache.mkdir(parents=True,exist_ok=True)
        with tempfile.NamedTemporaryFile(prefix='aligner-',suffix='.part',dir=report,delete=False) as stream:
            temporary=Path(stream.name)
            with urllib.request.urlopen(item['url'],timeout=60) as response:
                total=0
                while block:=response.read(1024*1024):
                    total+=len(block)
                    if total>item.get('size',300*1024*1024):raise ValueError('Archive exceeds pinned/maximum download size')
                    stream.write(block)
        if digest(temporary)!=item['sha256']:raise ValueError('Downloaded archive checksum mismatch: '+item['filename'])
        temporary.replace(path)
    if digest(path)!=item['sha256'] or ('size' in item and path.stat().st_size!=item['size']):
        raise ValueError('Cached archive integrity mismatch; retained: '+str(path))
    return path


def target(staging,name):
    relative=PurePosixPath(name)
    if relative.is_absolute() or '..' in relative.parts or '\\' in name or ':' in name:raise ValueError('Unsafe archive path: '+name)
    path=(staging/str(relative)).resolve()
    if not path.is_relative_to(staging):raise ValueError('Archive escapes private staging')
    return path


def extract_tar(tar,staging,package=None):
    links=[];total=0
    for entry in tar:
        name=entry.name
        if package and PurePosixPath(name).parts[0].startswith('.'):
            name='package-metadata/'+package+'/'+name
        path=target(staging,name)
        if entry.isdir():path.mkdir(parents=True,exist_ok=True);continue
        if entry.issym() or entry.islnk():links.append((path,entry.linkname,entry.issym()));continue
        if not entry.isfile():raise ValueError('Unsupported archive member: '+entry.name)
        total+=entry.size
        if total>2*1024**3 or entry.size>256*1024**2:raise ValueError('Excessive extraction size')
        path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists():
            # Packages can share identical directory metadata files, never replace different bytes.
            data=tar.extractfile(entry).read()
            if path.read_bytes()!=data:raise ValueError('Conflicting package file: '+name)
        else:
            with tar.extractfile(entry) as source,path.open('xb') as output:shutil.copyfileobj(source,output)
    for _ in range(len(links)+1):
        pending=[]
        for path,name,symbolic in links:
            source=((staging/name.lstrip('/')) if name.startswith('/') or not symbolic else path.parent/name).resolve()
            if not source.is_relative_to(staging):raise ValueError('Archive link escapes private runtime')
            if source.is_file():path.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,path)
            elif source.is_dir():path.mkdir(parents=True,exist_ok=True)
            else:pending.append((path,name,symbolic))
        if not pending:return
        if len(pending)==len(links):raise ValueError('Unresolved archive links: '+str(pending[:3]))
        links=pending


def mark(environment,marker,identity):
    files={p.relative_to(environment).as_posix():digest(p) for p in sorted(environment.rglob('*')) if p.is_file() and p.name!=marker}
    (environment/marker).write_text(json.dumps({'identity':identity,'files':files},indent=2)+'\n',encoding='utf-8')


def toolchain(path,cache,report,record):
    identity=digest(LOCK)
    if verified(path,'.jlinalg-build-toolchain.json',identity):return
    if path.exists():raise ValueError('Existing unmanaged/integrity-failed toolchain preserved; choose a new --toolchain')
    import zstandard
    path.parent.mkdir(parents=True,exist_ok=True)
    staging=Path(tempfile.mkdtemp(prefix='.jlinalg-bwa-toolchain-',dir=path.parent)).resolve()
    (staging/'tmp').mkdir()
    record['toolchain_staging']=str(staging)
    for package in json.loads(LOCK.read_text(encoding='utf-8'))['packages']:
        archive=download(package,cache,report)
        with archive.open('rb') as source,zstandard.ZstdDecompressor().stream_reader(source) as stream,tarfile.open(fileobj=stream,mode='r|') as tar:
            extract_tar(tar,staging,package['name'])
    mark(staging,'.jlinalg-build-toolchain.json',identity);staging.replace(path)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--profile',choices=['bowtie2','bwa'],required=True)
    parser.add_argument('--allow-download',action='store_true');parser.add_argument('--allow-source-build',action='store_true')
    parser.add_argument('--environment',type=Path);parser.add_argument('--cache',type=Path,default=ROOT/'build/omics-alignment-download')
    parser.add_argument('--toolchain',type=Path,default=ROOT/'build/omics-bwa-toolchain')
    parser.add_argument('--report',type=Path)
    args=parser.parse_args();profile=args.profile;item=BOWTIE if profile=='bowtie2' else BWA
    environment=(args.environment or ROOT/('build/omics-align-'+profile)).resolve()
    cache=args.cache.resolve();report=(args.report or ROOT/('build/literature-review/alignment-dependency/'+profile)).resolve();report.mkdir(parents=True,exist_ok=True)
    identity=item['sha256'] if profile=='bowtie2' else item['sha256']+':'+digest(LOCK)
    record={'profile':profile,'version':item['version'],'source':item['url'],'archive_sha256':item['sha256'],'environment':str(environment),
        'download_authorized':args.allow_download,'source_build_authorized':args.allow_source_build,'recorded_utc':dt.datetime.now(dt.timezone.utc).isoformat()}
    if verified(environment,MARKER,identity):record.update(status='available-not-yet-analysis-validated')
    elif not args.allow_download:record.update(status='skipped-install-not-permitted')
    elif environment.exists():record.update(status='skipped-existing-environment',error='Existing unmanaged/integrity-failed runtime is preserved')
    elif sys.platform!='win32' or platform.machine().lower() not in {'amd64','x86_64'}:record.update(status='skipped-platform-unavailable',error='This private installer supplies Windows x86_64 runtimes; install pinned upstream software natively on other hosts')
    elif profile=='bwa' and not args.allow_source_build:record.update(status='skipped-source-build-not-permitted')
    else:
        try:
            archive=download(item,cache,report);environment.parent.mkdir(parents=True,exist_ok=True)
            staging=Path(tempfile.mkdtemp(prefix='.jlinalg-aligner-',dir=environment.parent)).resolve();record['runtime_staging']=str(staging)
            if profile=='bowtie2':
                with zipfile.ZipFile(archive) as source:
                    total=0
                    for entry in source.infolist():
                        target(staging,entry.filename);total+=entry.file_size
                        if stat.S_ISLNK(entry.external_attr>>16) or total>1024**3:raise ValueError('Unsafe/excessive ZIP member')
                    source.extractall(staging)
                probe=[str(staging/'bowtie2-2.5.5-mingw-x86_64/bowtie2-align-s.exe'),'--version']
                result=subprocess.run(probe,capture_output=True,text=True,timeout=30);record['version_probe']={'exit_code':result.returncode,'stdout':result.stdout,'stderr':result.stderr}
                if result.returncode!=0 or ' version 2.5.5\n' not in result.stdout:raise ValueError('Bowtie 2 version/runtime probe failed')
            else:
                toolchain(args.toolchain.resolve(),cache,report,record)
                with tarfile.open(archive,'r:gz') as source:extract_tar(source,staging)
                source=staging/'bwa-0.7.19';build_env=os.environ.copy();build_env['PATH']=str(args.toolchain.resolve()/'usr/bin')+os.pathsep+build_env.get('PATH','')
                command=[str(args.toolchain.resolve()/'usr/bin/bash.exe'),'--noprofile','--norc','-c',"make -j2 CFLAGS='-g -Wall -Wno-unused-function -O3 -std=gnu11'"]
                record['build_command']=command;record['build_environment_path_prefix']=str(args.toolchain.resolve()/'usr/bin')
                with (report/'build.log').open('w',encoding='utf-8') as log:
                    result=subprocess.run(command,cwd=source,env=build_env,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
                record['build_exit_code']=result.returncode
                if result.returncode!=0:raise ValueError('Unmodified upstream BWA build failed; inspect build.log')
                binary=source/'bwa.exe'
                if not binary.is_file():raise ValueError('BWA build omitted bwa.exe')
                bindir=staging/'bin';bindir.mkdir();shutil.copy2(binary,bindir/'bwa.exe')
                (staging/'tmp').mkdir()
                for dll in (args.toolchain.resolve()/'usr/bin').glob('*.dll'):shutil.copy2(dll,bindir/dll.name)
                result=subprocess.run([str(bindir/'bwa.exe')],capture_output=True,text=True,timeout=30)
                record['version_probe']={'exit_code':result.returncode,'stdout':result.stdout,'stderr':result.stderr}
                if 'Version: 0.7.19-r1273' not in result.stderr:raise ValueError('BWA runtime/version probe failed')
                record['build_lock_sha256']=digest(LOCK);record['source_policy']='Unmodified upstream tagged source; language standard selected explicitly, no algorithm patches'
            mark(staging,MARKER,identity);staging.replace(environment);record.update(status='installed-not-yet-analysis-validated')
        except Exception as failure:record.update(status='skipped-dependency-unavailable',error=str(failure))
    skipped=record['status'].startswith('skipped')
    record['action']=('Skip the dependent analysis until this runtime is available. Where permitted run python tools/literature/install_aligner_dependencies.py --profile '+profile+' --allow-download'+(' --allow-source-build' if profile=='bwa' else '')+'. Inspect retained logs/cache/staging; preserve existing files and choose new runtime/toolchain directories after correcting the exact cause.' if skipped else 'Supply this private runtime to the typed aligner adapter. Installation and version probes are separate from analysis validation.')
    build_log=report/'build.log'
    if profile=='bwa' and build_log.exists():
        warnings=[line for line in build_log.read_text(encoding='utf-8',errors='replace').splitlines() if 'warning:' in line.lower()]
        if warnings:record['compiler_warnings_first_100']=warnings[:100];record['compiler_warning_count']=len(warnings)
    report_file=report/'dependency-status.json'
    if report_file.exists():
        previous=json.loads(report_file.read_text(encoding='utf-8'));record['history']=previous.get('history',[])+[{k:v for k,v in previous.items() if k!='history'}]
    report_file.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    warning_text=('# ALIGNER UNAVAILABLE - ANALYSIS SKIPPED\n\n'+record.get('error',record['status'])+'\n\nRequired action: '+record['action']+'\n') if skipped else '# Aligner runtime available\n\nActual analysis validation remains separate.\n'
    if record.get('compiler_warning_count'):
        warning_text+='\n## UPSTREAM COMPILER WARNINGS\n\n'+str(record['compiler_warning_count'])+' warnings remain in build.log. The unmodified source completed its build.\n\nRequired action: review the retained compiler warnings before changing compiler settings or using unvalidated operations/input types. Preserve the pinned source/toolchain; installation success does not establish scientific analysis validity.\n'
    (report/'WARNINGS.md').write_text(warning_text,encoding='utf-8')
    print(json.dumps(record));return 3 if skipped else 0


if __name__=='__main__':raise SystemExit(main())
