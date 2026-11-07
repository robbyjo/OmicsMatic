#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit permitted original MEME 5.5.9 source setup; no user analysis or package-manager changes."""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import signal
import subprocess
import sys
import time
import urllib.request
import uuid

ARCHIVE_SHA='0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12'
ARCHIVE_BYTES=56535171
SOURCE_URL='https://meme-suite.org/meme/meme-software/5.5.9/meme-5.5.9.tar.gz'
ACTION='Required action: read WARNINGS.md, FAILURE.json when present, builder.stdout/stderr and every original build-evidence log. Setup is not runtime admission or a user analysis. SKIP3: required permission, download, compiler, Perl, library or supported host is unavailable; skip dependent analysis and complete only permitted setup. FAIL2: input/hash, build, capacity, cancellation or cleanup failure; retain every partial archive, report and native area, correct the named cause and use NEW paths. Never relocate the compiled home: original binaries embed their prefix. Require this report and build-evidence COMPLETED.json, then supply the recorded home and build_evidence to tomtom-tools; that command performs fresh runtime health and complete user-fit validation.'


class Unavailable(Exception):pass
class Cancelled(Exception):pass


def write(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n')
def pin(path):
    if path.is_symlink() or not path.is_file():raise ValueError('Require a regular unlinked file: '+str(path))
    before=path.stat()
    with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    after=path.stat()
    if (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns,before.st_ctime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns,after.st_ctime_ns):raise ValueError('File changed during complete hash: '+str(path))
    return dict(bytes=after.st_size,sha256=digest)
def canonical(path):
    path=Path(os.path.abspath(path))
    if not re.fullmatch(r'/(?:[A-Za-z0-9._+-]+/)*[A-Za-z0-9._+-]+',str(path)) or any(p.is_symlink() for p in [path,*path.parents]):raise ValueError('Require canonical absolute ASCII setup paths without whitespace or relative segments')
    if any(p in {'.','..'} for p in path.parts):raise ValueError('Relative setup path segment')
    return path
def processes():
    result={}
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            fields=(path/'stat').read_text().rsplit(')',1)[1].split();result[int(path.name)]=dict(ppid=int(fields[1]),start=int(fields[19]),state=fields[0])
        except (OSError,ValueError,IndexError):continue
    return result
def descendants():
    rows=processes();owned={os.getpid()};changed=True
    while changed:
        changed=False
        for pid,row in rows.items():
            if pid not in owned and row['ppid'] in owned:owned.add(pid);changed=True
    return {pid:rows[pid] for pid in owned if pid!=os.getpid()}
def reap():
    while True:
        try:pid,_=os.waitpid(-1,os.WNOHANG)
        except ChildProcessError:return
        if pid==0:return
def cleanup():
    # Subreaping retains ownership even when the unchanged builder creates new sessions.
    for sig,seconds in [(signal.SIGTERM,3),(signal.SIGKILL,3)]:
        deadline=time.monotonic()+seconds
        while True:
            reap();rows=descendants()
            if not rows:return []
            current=processes()
            for pid,row in rows.items():
                if pid in current and row['start']==current[pid]['start']:
                    try:os.kill(pid,sig)
                    except ProcessLookupError:pass
            if time.monotonic()>=deadline:break
            time.sleep(.05)
    reap();return sorted(descendants())
def supervise(command,report,timeout=3600):
    if ctypes.CDLL(None,use_errno=True).prctl(36,1,0,0,0)!=0:raise Unavailable('Linux child-subreaper ownership is unavailable; no build launched')
    write(report/'launch.json',dict(command=list(map(str,command)),timeout_seconds=timeout,owned_linux_subreaper=True))
    process=None;code=None;failure=None;survivors=[]
    try:
        with (report/'builder.stdout').open('xb') as stdout,(report/'builder.stderr').open('xb') as stderr:
            environment={key:value for key,value in os.environ.items() if key not in {'PYTHONPATH','PYTHONHOME','LD_PRELOAD','LD_LIBRARY_PATH'}}
            process=subprocess.Popen(list(map(str,command)),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,env=environment,start_new_session=True)
            deadline=time.monotonic()+timeout
            while process.poll() is None:
                if time.monotonic()>=deadline:raise TimeoutError('Original source build exceeded its declared wall capacity')
                time.sleep(.05)
            code=process.returncode
    except BaseException as error:failure=error
    finally:
        # Do not let another TERM interrupt ownership cleanup.
        old={sig:signal.signal(sig,signal.SIG_IGN) for sig in [signal.SIGTERM,signal.SIGINT]}
        try:survivors=cleanup()
        finally:
            for sig,handler in old.items():signal.signal(sig,handler)
        if process is not None and process.returncode is None:
            try:process.wait(timeout=1)
            except subprocess.TimeoutExpired:survivors.append(process.pid)
        write(report/'process.json',dict(exit_code=code,owned_live_after_cleanup=sorted(set(survivors)),error=None if failure is None else str(failure)))
    if survivors:raise RuntimeError('Owned build workers survived cleanup: '+str(survivors))
    if failure is not None:raise failure
    if code!=0:raise RuntimeError('Original build exited '+str(code)+'; read complete builder and build-evidence logs')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--archive',type=Path);parser.add_argument('--allow-build',action='store_true');parser.add_argument('--allow-download',action='store_true');parser.add_argument('--research-nonprofit',action='store_true');args=parser.parse_args()
    report=canonical(args.report)
    if not report.parent.is_dir() or report.exists():raise ValueError('Use a NEW setup report with canonical existing parent')
    report.mkdir(mode=0o700);build=None
    def interrupted(signum,frame):raise Cancelled('Setup interrupted by signal '+str(signum))
    try:
        if not args.allow_build or not args.research_nonprofit:raise Unavailable('Explicit permitted --allow-build and --research-nonprofit are required. Read UPSTREAM-COPYING; this kit supplies no commercial permission')
        if platform.system()!='Linux' or platform.machine()!='x86_64' or sys.version_info<(3,12):raise Unavailable('Permitted Linux x86_64 Python3.12+ is required; on Windows run Linux Python inside WSL')
        for sig in [signal.SIGTERM,signal.SIGINT]:signal.signal(sig,interrupted)
        kit=canonical(Path(__file__).parent);sources=json.loads((kit/'KIT-SOURCES.json').read_bytes());expected=set(['setup_tomtom.py','UPSTREAM-COPYING','reference-builder/tools/literature/build_meme_linux_reference.py'])
        if set(sources)!=expected:raise ValueError('Complete fixed setup kit source population differs')
        for name,value in sources.items():
            if pin(kit/name)!=value:raise ValueError('Complete setup kit resource differs: '+name)
        write(report/'OWNER.json',dict(schema_version=1,status='PREPARING_SOURCE_BUILD_ONLY',kit=str(kit),kit_sources=sources,analysis_admitted=False))
        archive=canonical(args.archive) if args.archive is not None else report/'meme-5.5.9.tar.gz'
        if args.archive is None:
            if not args.allow_download:raise Unavailable('No --archive supplied and --allow-download absent; no network acquisition attempted')
            try:
                with urllib.request.urlopen(SOURCE_URL,timeout=60) as response,archive.open('xb') as output:
                    if not response.geturl().startswith('https://meme-suite.org/'):raise ValueError('Official archive redirected outside its declared HTTPS source')
                    count=0
                    while True:
                        block=response.read(1048576)
                        if not block:break
                        count+=len(block)
                        if count>ARCHIVE_BYTES:raise ValueError('Complete original archive exceeds its pinned length')
                        output.write(block)
            except (OSError,urllib.error.URLError) as error:raise Unavailable('Permitted official source download unavailable; retain the partial and supply a complete pinned --archive: '+str(error)) from error
        if pin(archive)!=dict(bytes=ARCHIVE_BYTES,sha256=ARCHIVE_SHA):raise ValueError('Exact official archive length/hash differs; no original build launched')
        # Keep the exact frozen builder and its original scientific receipts unchanged.
        builder=kit/'reference-builder/tools/literature/build_meme_linux_reference.py';build=kit/'reference-builder/build/literature-review/meme-runtime'/('source-setup-'+uuid.uuid4().hex)
        write(report/'BUILD-LOCATION.json',dict(build_evidence=str(build),archive=str(archive),archive_pin=pin(archive)))
        supervise([sys.executable,'-I','-B',builder,'--archive',archive,'--out',build,'--allow-build'],report)
        for name,value in sources.items():
            if pin(kit/name)!=value:raise ValueError('Complete setup kit source changed during build: '+name)
        if pin(archive)!=dict(bytes=ARCHIVE_BYTES,sha256=ARCHIVE_SHA):raise ValueError('Complete archive changed during source build')
        completion=json.loads((build/'COMPLETED.json').read_bytes())
        if completion['status']!='built-original-Linux-MEME-Suite-reference-not-scientifically-admitted' or completion['unchanged_original_C_and_H']!=615:raise ValueError('Original complete source/build receipt is unavailable')
        write(report/'COMPLETED.json',dict(schema_version=1,status='COMPLETE_ORIGINAL_SOURCE_SETUP_NOT_ANALYSIS',home=completion['prefix'],build_evidence=str(build),build_completion=pin(build/'COMPLETED.json'),owned_live_after_cleanup=[],analysis_admitted=False))
        (report/'WARNINGS.md').write_text('# SETUP ONLY — ANALYSIS NOT RUN\n\n'+ACTION+'\n',encoding='utf-8');print(json.dumps(dict(home=completion['prefix'],build_evidence=str(build),report=str(report))),flush=True);return 0
    except (Exception,KeyboardInterrupt) as failure:
        skip=isinstance(failure,(Unavailable,PermissionError));status='SKIP3_SETUP_SKIPPED' if skip else 'FAIL2_SETUP_NOT_ADMITTED'
        write(report/'FAILURE.json',dict(status=status,error=str(failure),build_evidence=None if build is None else str(build),analysis_admitted=False,required_action=ACTION));(report/'WARNINGS.md').write_text('# '+status+'\n\n'+str(failure)+'\n\n'+ACTION+'\n',encoding='utf-8');print(status+': '+str(failure)+'\n'+ACTION,file=sys.stderr,flush=True);return 3 if skip else 2


if __name__=='__main__':sys.exit(main())
