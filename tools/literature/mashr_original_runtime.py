#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact private R/package admission and owned, sampled process monitoring."""
import hashlib
import importlib
import json
import os
from pathlib import Path,PurePosixPath
import platform
import stat
import subprocess
import sys
import time
import zipfile

LOCK=Path(__file__).with_name('mashr-original-runtime-lock.json')
ACTION='Required action: preserve all inputs, partial downloads, installed files and logs. Missing/forbidden exact dependencies or monitoring capability are SKIP3: skip dependent analysis until permitted setup succeeds. Invalid input, integrity, process, convergence, capacity or cancellation errors are FAIL2: no result admitted. Correct the named cause and retry into NEW home/report/output paths. No edition substitution, user-library fallback or population truncation. Setup completion alone does not admit an analysis. Fingerprints establish integrity, not authenticity or source-to-binary reproduction.'
class Unavailable(Exception):pass
class ProcessFailure(ValueError):
    def __init__(self,message,result):super().__init__(message);self.result=result


def pin(path):
    path=canonical(path)
    if not path.is_file():raise ValueError('Missing/nonregular runtime file: '+str(path))
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def write(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')


def canonical(path):
    path=Path(path).absolute()
    for part in [path,*path.parents]:
        if part.is_symlink() or getattr(part,'is_junction',lambda:False)():raise ValueError('Linked runtime/evidence path: '+str(part))
    if path.resolve()!=path:raise ValueError('Noncanonical runtime/evidence path: '+str(path))
    return path


def disjoint(*paths):
    paths=[canonical(p) for p in paths]
    for i,p in enumerate(paths):
        for q in paths[i+1:]:
            if p.is_relative_to(q) or q.is_relative_to(p):raise ValueError('Runtime, cache, source and output trees must be disjoint: '+str(p)+' / '+str(q))


def load_lock():
    if not __debug__:raise RuntimeError('Never disable scientific/runtime assertions')
    if LOCK.stat().st_size>4_194_304:raise ValueError('Runtime lock exceeds capacity')
    lock=json.loads(LOCK.read_text(encoding='utf-8'));assert lock['schema_version']==1 and lock['profile']=='windows-x86_64-R4.6.1-mashr0.2.79'
    return lock


def platform_admission():
    if sys.platform!='win32' or platform.machine().upper() not in ['AMD64','X86_64'] or platform.python_implementation()!='CPython' or sys.version_info<(3,11):raise Unavailable('Require permitted Windows AMD64, CPython3.11+ and exact R4.6.1; this profile does not silently select another host or edition')


def census(root,maximum=2_147_483_648,entries=20000):
    root=canonical(root);result={};total=0;count=0
    if not root.is_dir():raise Unavailable('Required runtime directory unavailable: '+str(root))
    for base,dirs,files in os.walk(root,followlinks=False):
        for name in [*dirs,*files]:
            try:item=canonical(Path(base)/name)
            except FileNotFoundError:continue
            count+=1
            if count>entries:raise ValueError('Complete runtime/evidence entry capacity exceeded')
            if item.is_file():
                total+=item.stat().st_size
                if total>maximum:raise ValueError('Complete runtime/evidence byte capacity exceeded')
                result[item.relative_to(root).as_posix()]=pin(item)
            elif not item.is_dir():raise ValueError('Nonregular runtime/evidence member')
    return dict(sorted(result.items()))


def verify_rhome(rhome,lock):
    rhome=canonical(rhome);script=rhome/'bin/Rscript.exe'
    if not script.is_file():raise Unavailable('Required exact Rscript.exe unavailable; install the permitted R4.6.1 distribution separately before dependent analysis')
    if pin(script)!=lock['rscript']:raise Unavailable('Rscript binary differs from the tested exact R4.6.1 profile; no fallback or automatic R installation selected')
    full=census(rhome);selected={n:v for n,v in full.items() if n.startswith(tuple(lock['rhome_runtime_roots']))}
    if selected!=lock['rhome_runtime_files']:raise Unavailable('Installed R runtime files differ from the exact admitted profile, including required base/recommended packages; resolve the R4.6.1 build before dependent analysis')
    return full


def verify_home(home,lock):
    home=canonical(home);library=census(home/'library');monitor=census(home/'monitor',16_777_216,2000)
    if library!=lock['package_files']:raise ValueError('Installed private R package file graph/checksums differ from the official locked binaries')
    if monitor!=lock['monitor']['files']:raise ValueError('Installed private process monitor differs from the official locked wheel')
    return dict(library=library,monitor=monitor)


def load_monitor(home,lock):
    platform_admission();sys.dont_write_bytecode=True;directory=canonical(Path(home)/'monitor')
    if census(directory,16_777_216,2000)!=lock['monitor']['files']:raise ValueError('Monitor integrity mismatch before code load')
    sys.path.insert(0,str(directory))
    module=importlib.import_module('psutil')
    if module.__version__!=lock['monitor']['version'] or not canonical(module.__file__).is_relative_to(directory):raise Unavailable('Exact private psutil process monitor could not be loaded; use an isolated CPython invocation')
    module.Process().memory_info();return module


def extract(archive,destination,package=None):
    destination=canonical(destination);destination.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        seen=set();total=0
        if len(z.infolist())>30000:raise ValueError('Archive entry capacity exceeded')
        for item in z.infolist():
            name=item.filename;path=PurePosixPath(name)
            if path.is_absolute() or '\\' in name or ':' in name or any(p in ['.','..'] for p in path.parts) or (item.external_attr>>16)&0o170000==0o120000:raise ValueError('Unsafe package archive member')
            if name.casefold() in seen:raise ValueError('Case-aliased/duplicate package archive member')
            seen.add(name.casefold())
            if package and (not path.parts or path.parts[0]!=package):raise ValueError('Package archive top-level name differs')
            target=canonical(destination.joinpath(*path.parts))
            if not target.is_relative_to(destination):raise ValueError('Package archive member escapes private home')
            total+=item.file_size
            if total>536_870_912 or item.file_size>134_217_728:raise ValueError('Complete package archive allocation capacity exceeded')
            if item.is_dir():target.mkdir(parents=True,exist_ok=True);continue
            target.parent.mkdir(parents=True,exist_ok=True)
            with z.open(item) as source,target.open('xb') as out:
                count=0
                while block:=source.read(1_048_576):
                    count+=len(block)
                    if count>item.file_size:raise ValueError('Archive member expanded beyond declared size')
                    out.write(block)
                if count!=item.file_size:raise ValueError('Truncated package archive member')


def environment(rhome,home,scratch):
    system=Path(os.environ.get('SystemRoot','C:/Windows'));scratch=canonical(scratch);scratch.mkdir(parents=True,exist_ok=True)
    return dict(SystemRoot=str(system),WINDIR=str(system),COMSPEC=str(system/'System32/cmd.exe'),PATH=str(Path(rhome)/'bin/x64')+os.pathsep+str(system/'System32'),
        R_HOME=str(rhome),R_LIBS_USER=str(Path(home)/'library'),R_LIBS_SITE='',R_DEFAULT_PACKAGES='datasets,utils,grDevices,graphics,stats,methods',R_USER=str(scratch),
        HOME=str(scratch),USERPROFILE=str(scratch),TMPDIR=str(scratch),TMP=str(scratch),TEMP=str(scratch),LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')


def workspace_size(root,maximum,entries):
    # This is a sampled, changing workspace, not an immutable evidence census.
    # Observe each entry once without following links. resolve()+stat() races
    # with legitimate deletion/recreation on Windows and can reject a file that
    # was already gone. The caller admits the root canonically before launch.
    total=count=0
    for base,dirs,files in os.walk(root,followlinks=False):
        for name in [*dirs,*files]:
            item=Path(base)/name
            try:observed=item.lstat()
            except FileNotFoundError:continue
            if stat.S_ISLNK(observed.st_mode) or getattr(observed,'st_file_attributes',0)&getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',1024):raise ValueError('Linked process workspace member: '+str(item))
            count+=1
            if count>entries:raise ValueError('Owned process workspace entry capacity exceeded')
            if stat.S_ISREG(observed.st_mode):total+=observed.st_size
            elif not stat.S_ISDIR(observed.st_mode):raise ValueError('Nonregular process workspace member: '+str(item))
            if total>maximum:raise ValueError('Owned process workspace byte capacity exceeded')
    return total


def run_owned(command,root,name,env,monitor,limits=None):
    limits=limits or dict(wall_seconds=600,cpu_seconds=600,rss_bytes=4_294_967_296,processes=16,threads=64,log_bytes=16_777_216,workspace_bytes=268_435_456,entries=20000,poll_seconds=.1)
    root=canonical(root);stdout_path=root/(name+'.stdout');stderr_path=root/(name+'.stderr');start=time.monotonic();known={};observed={};peaks=dict(rss_bytes=0,processes=0,threads=0,workspace_bytes=0);samples=0;failure=None
    process=None;owner=None;spawn_attempted=False
    def discover():
        try:
            if owner is not None and owner.is_running():
                for p in [owner,*owner.children(recursive=True)]:known.setdefault((p.pid,p.create_time()),p)
        except monitor.NoSuchProcess:pass
    def sample():
        nonlocal samples
        discover()
        rss=threads=live=0
        for key,p in known.items():
            try:
                if p.is_running():
                    live+=1;rss+=p.memory_info().rss;threads+=p.num_threads();t=p.cpu_times();observed[key]=max(observed.get(key,0),t.user+t.system)
            except monitor.NoSuchProcess:pass
        samples+=1;peaks['rss_bytes']=max(peaks['rss_bytes'],rss);peaks['threads']=max(peaks['threads'],threads);peaks['processes']=max(peaks['processes'],live)
        peaks['workspace_bytes']=max(peaks['workspace_bytes'],workspace_size(root,limits['workspace_bytes'],limits['entries']))
        if time.monotonic()-start>limits['wall_seconds'] or sum(observed.values())>limits['cpu_seconds'] or rss>limits['rss_bytes'] or live>limits['processes'] or threads>limits['threads']:raise ValueError('Owned process exceeded declared sampled time/memory/process/thread capacity')
        if stdout_path.stat().st_size+stderr_path.stat().st_size>limits['log_bytes']:raise ValueError('Owned process log capacity exceeded')
    def terminate():
        # psutil Process keeps its creation identity; is_running guards PID reuse.
        discover()
        for p in reversed(list(known.values())):
            try:
                if p.is_running():p.kill()
            except monitor.NoSuchProcess:pass
        if process is not None and process.poll() is None:process.kill()
        if process is not None:process.wait(timeout=30)
        _,alive=monitor.wait_procs(list(known.values()),timeout=30)
        if alive:raise RuntimeError('Owned processes could not be confirmed terminal; retain logs and resolve before continuing')
    try:
        with stdout_path.open('xb') as stdout,stderr_path.open('xb') as stderr:
            spawn_attempted=True
            process=subprocess.Popen(list(map(str,command)),cwd=root,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                owner=monitor.Process(process.pid);known[(owner.pid,owner.create_time())]=owner
            except monitor.NoSuchProcess:pass
            while process.poll() is None:sample();time.sleep(limits['poll_seconds'])
            sample()
            if not known:raise ValueError('Owned process creation identity was not observed; no monitored process admitted')
            if any(p.is_running() for p in known.values()):raise ValueError('Original parent exited while a sampled owned descendant remained live')
    except BaseException as error:
        failure=error
        try:terminate()
        except Exception as cleanup:failure=RuntimeError(str(error)+'; descendant cleanup: '+str(cleanup))
    result=dict(argv=list(map(str,command)),exit_code=process.returncode if process else None,wall_seconds=time.monotonic()-start,owned_parent_pid=process.pid if process else None,
        sampled_cpu_seconds=sum(observed.values()),samples=samples,peaks=peaks,limits=limits,observed_process_identities=[dict(pid=k[0],creation_time=k[1]) for k in known],
        output_paths=[stdout_path.name,stderr_path.name],error=(str(failure) or type(failure).__name__) if failure else None,sampling_is_not_OS_sandbox=True)
    write(root/(name+'.process.json'),result)
    if failure:
        if spawn_attempted and process is None and isinstance(failure,OSError):raise Unavailable('Required original executable could not be started in this operating environment: '+str(failure)) from failure
        if isinstance(failure,monitor.AccessDenied):raise Unavailable('Required owned-process monitoring unavailable; '+str(failure)) from failure
        raise failure
    if process.returncode!=0:raise ProcessFailure('Original process failed; inspect complete '+name+'.stdout/.stderr',result)
    return result
