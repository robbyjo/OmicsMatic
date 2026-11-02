#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Provision an exact private original Nextflow engine and compatible Java runtime.

Setup runs a tiny synthetic DAG, not user analysis. Installation and downloading
are independent opt-ins. Complete partial homes, downloads and logs are retained.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path,PurePosixPath
import platform
import shutil
import signal
import stat
import subprocess
import sys
import tarfile
import time
import urllib.request

HERE=Path(__file__).resolve().parent
LOCK=HERE/'nextflow-managed-runtime-lock.json'
LOCK_SHA256='594b179c0bc08e63f6d06af92b7243b9a3dc1b42b03adb4646261dda1373e720'
MARKER='.omicsmatic-nextflow.json'
ACTION=('Skip dependent Nextflow analysis until exact runtime setup and the separate user-analysis adapter are available. '
 'On a permitted Linux x86_64 host (Linux Python in WSL on Windows), use Python3.11+ and NEW canonical ASCII home/report paths. '
 'Run python3 install_nextflow.py --profile linux-x86_64-nextflow-24.04.4 --home NEW_PRIVATE_HOME '
 '--report NEW_SETUP_REPORT --cache CACHE --allow-install. Add --allow-download only where permitted, '
 'or place the exact engine all-in-one distribution and locked Java archive in CACHE or --archive-dir. '
 'Select the exact requested edition; supported editions are21.04.1,22.10.6,24.04.4. Do not infer an unreported engine edition from an nf-core pipeline version. '
 'Require exit0, COMPLETED.json and complete unchanged hashes. Preserve failed/partial homes, downloads, logs and diagnostics; '
 'correct the named cause and rerun to NEW paths. Setup health is synthetic runtime evidence, not user-analysis or whole-paper admission. Cell Ranger remains excluded.')
HEALTH=r'''nextflow.enable.dsl=2
params.outdir=null
process EMIT {
 input:
 tuple val(label),val(sequence)
 output:
 path("${label}.txt")
 script:
 """
 printf '%s\\n' '$sequence' > '${label}.txt'
 """
}
process GATHER {
 publishDir params.outdir,mode:'copy'
 input:
 path parts
 output:
 path 'result.txt'
 script:
 'cat *.txt | LC_ALL=C sort > result.txt'
}
workflow {
 EMIT(Channel.of(tuple('a','ACGT'),tuple('b','TTAA')))
 GATHER(EMIT.out.collect())
}
'''
HEALTH_CONFIG="process.executor='local'\nprocess.cpus=1\nprocess.memory='64 MB'\nprocess.maxForks=2\nexecutor.cpus=2\nexecutor.memory='1 GB'\n"


class Unavailable(Exception):pass


def json_read(path,maximum=2_097_152):
    raw=path.read_bytes()
    if len(raw)>maximum:raise ValueError('Metadata byte capacity exceeded: '+str(path))
    def pairs(values):
        result={}
        for key,value in values:
            if key in result:raise ValueError('Duplicate JSON key: '+key)
            result[key]=value
        return result
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Nonfinite JSON value')))


def write(path,value):
    with path.open('xb') as stream:stream.write((json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode())


def pin(path,maximum=268_435_456):
    before=path.stat(follow_symlinks=False)
    if not stat.S_ISREG(before.st_mode) or before.st_size>maximum:raise ValueError('Nonregular or oversized file: '+str(path))
    with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    after=path.stat(follow_symlinks=False)
    if (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns):raise ValueError('File changed while hashing: '+str(path))
    return dict(bytes=before.st_size,sha256=digest)


def private(path):
    path=Path(os.path.abspath(path))
    if not str(path).isascii() or any(c.isspace() or ord(c)<32 or ord(c)==127 for c in str(path)):raise Unavailable('Private runtime/report/cache paths require ASCII without whitespace')
    for parent in [path,*path.parents]:
        if parent.is_symlink():raise ValueError('Linked private path/ancestor: '+str(parent))
    return path


def inventory(root,exclude=()):
    result={};total=0
    for path in sorted(root.rglob('*')):
        name=path.relative_to(root).as_posix()
        if name in exclude:continue
        info=path.lstat()
        if stat.S_ISDIR(info.st_mode):continue
        if stat.S_ISLNK(info.st_mode):
            target=os.readlink(path)
            if not path.resolve(strict=True).is_relative_to(root):raise ValueError('Runtime/report link escapes its root: '+name)
            result[name]=dict(kind='symlink',target=target)
        elif stat.S_ISREG(info.st_mode):
            total+=info.st_size
            if total>1_073_741_824:raise ValueError('Complete runtime/report byte capacity exceeded')
            result[name]=dict(kind='file',**pin(path),mode=stat.S_IMODE(info.st_mode))
        else:raise ValueError('Nonregular runtime/report member: '+name)
        if len(result)>10000:raise ValueError('Runtime/report file capacity exceeded')
    return result


def fetch(cache,archive_dir,package,allow_download):
    name=package.get('name') or package['url'].rsplit('/',1)[1]
    if '/' in name or '\\' in name or name in {'.','..'}:raise ValueError('Unsafe locked asset filename')
    expected={k:package[k] for k in ['bytes','sha256']};target=cache/name
    if target.exists():
        if pin(target)!=expected:raise ValueError('Changed/corrupt cached asset; preserve it: '+str(target))
        return target
    source=archive_dir/name if archive_dir else None
    if source is not None and source.exists():
        if source.is_symlink() or pin(source)!=expected:raise ValueError('Offline asset differs: '+str(source))
        with source.open('rb') as src,target.open('xb') as dst:shutil.copyfileobj(src,dst,1_048_576)
    else:
        if not allow_download:raise Unavailable('Exact cached/offline dependency is absent and download is not allowed: '+name)
        partial=cache/(name+'.partial')
        if partial.exists():raise ValueError('Preserve incomplete download and choose a new cache: '+str(partial))
        request=urllib.request.Request(package['url'],headers={'User-Agent':'OmicsMatic-Nextflow-runtime-setup'})
        count=0
        with partial.open('xb') as out,urllib.request.urlopen(request,timeout=45) as response:
            while chunk:=response.read(1_048_576):
                count+=len(chunk)
                if count>expected['bytes']:raise ValueError('Download exceeded complete locked size')
                out.write(chunk)
        if pin(partial)!=expected:raise ValueError('Downloaded dependency differs; partial retained')
        os.link(partial,target);partial.unlink()
    if pin(target)!=expected:raise ValueError('Complete cached asset differs after capture')
    return target


def extract_java(archive,home,locked):
    destination=home/'java';destination.mkdir();expected=locked['members'];seen=set()
    with tarfile.open(archive,'r:gz') as source:
        members=source.getmembers()
        if len(members)>10000:raise ValueError('Java archive member capacity exceeded')
        for member in members:
            parts=PurePosixPath(member.name).parts
            if not parts or parts[0]!=locked['package']['archive_root'] or '..' in parts:raise ValueError('Unsafe/unexpected Java archive path')
            relative=PurePosixPath(*parts[1:]).as_posix()
            if relative=='.' or member.isdir():continue
            if relative in seen or relative not in expected:raise ValueError('Duplicate/unlocked Java archive member')
            seen.add(relative);wanted=expected[relative];target=destination/relative;target.parent.mkdir(parents=True,exist_ok=True)
            if wanted['kind']=='symlink':
                if not member.issym() or member.linkname!=wanted['target']:raise ValueError('Java link differs')
                os.symlink(member.linkname,target)
            else:
                if not member.isfile() or member.size!=wanted['bytes'] or member.mode&0o777!=wanted['mode']:raise ValueError('Java file metadata differs')
                with source.extractfile(member) as src,target.open('xb') as dst:shutil.copyfileobj(src,dst,1_048_576)
                os.chmod(target,wanted['mode'])
                if pin(target)!=dict(bytes=wanted['bytes'],sha256=wanted['sha256']):raise ValueError('Java file bytes differ')
    actual=inventory(destination)
    if seen!=set(expected) or content_inventory(actual)!=content_inventory(expected):raise ValueError('Complete Java runtime inventory differs')
    for name,value in expected.items():
        if value['kind']=='file' and value['mode']&0o111 and not os.access(destination/name,os.X_OK):raise Unavailable('Required Java executable is forbidden: '+name)


def content_inventory(values):
    # Archive permissions and effective filesystem permissions are separate.
    # DrvFS may expose0777 despite chmod; never relax a byte or link-target pin.
    return {name:{key:value for key,value in row.items() if key!='mode'} for name,row in values.items()}


def mode_departures(home,locked):
    result={}
    for name,expected in locked['java']['members'].items():
        if expected['kind']=='file':
            actual=stat.S_IMODE((home/'java'/name).stat().st_mode)
            if actual!=expected['mode']:result['java/'+name]=dict(archive_mode=expected['mode'],effective_mode=actual)
    actual=stat.S_IMODE((home/'nextflow').stat().st_mode)
    if actual!=0o755:result['nextflow']=dict(requested_mode=0o755,effective_mode=actual)
    return result


def helpers(locked):
    result={}
    for name in locked['required_helpers']:
        requested=Path(name)
        if not requested.is_file() or not os.access(requested,os.X_OK):raise Unavailable('Required permitted helper is absent: '+name)
        real=requested.resolve(strict=True);result[name]=dict(real_path=str(real),**pin(real),mode=stat.S_IMODE(real.stat().st_mode))
    return result


def environment(home,report):
    return dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',NXF_HOME=str(report/'nxf-home'),CAPSULE_CACHE_DIR=str(home/'capsule'),NXF_JAVA_HOME=str(home/'java'),NXF_OFFLINE='true',NXF_DISABLE_CHECK_LATEST='true',NXF_ANSI_LOG='false',NXF_PLUGINS_DEFAULT='false',NXF_ENABLE_SECRETS='false',NXF_OPTS='-Xms32m -Xmx512m -XX:ReservedCodeCacheSize=128m')


def group_live(group,start):
    found=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdecimal():continue
        try:
            fields=(path/'stat').read_text().rsplit(')',1)[1].split()
            if int(fields[2])==group and int(fields[3])==group and int(fields[19])>=start and fields[0] not in {'Z','X'}:found.append(dict(pid=int(path.name),start_ticks=int(fields[19])))
        except (OSError,ValueError,IndexError):continue
    return found


def cleanup_group(process,start):
    if group_live(process.pid,start):
        try:os.killpg(process.pid,signal.SIGTERM)
        except ProcessLookupError:pass
    deadline=time.monotonic()+5
    while group_live(process.pid,start) and time.monotonic()<deadline:time.sleep(.05)
    if group_live(process.pid,start):
        try:os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError:pass
    process.wait(timeout=5)
    deadline=time.monotonic()+2
    while group_live(process.pid,start) and time.monotonic()<deadline:time.sleep(.05)
    remaining=group_live(process.pid,start)
    if remaining:raise RuntimeError('Owned Nextflow setup processes remain alive: '+str(remaining))
    return remaining


def execute(command,env,work,label,timeout_seconds=300):
    if not 1<=timeout_seconds<=300:raise ValueError('Invalid setup wall-time capacity')
    invocation=['/usr/bin/prlimit','--as=8589934592','--cpu=180','--fsize=536870912','--nofile=4096','--',*map(str,command)]
    started=time.monotonic();process=None;code=None;failure=None;start_ticks=None;remaining=[]
    try:
        with (work/(label+'.stdout')).open('xb') as out,(work/(label+'.stderr')).open('xb') as err:
            process=subprocess.Popen(invocation,cwd=work,env=env,stdout=out,stderr=err,start_new_session=True)
            start_ticks=int((Path('/proc')/str(process.pid)/'stat').read_text().rsplit(')',1)[1].split()[19])
            write(work/(label+'.launch.json'),dict(pid=process.pid,start_ticks=start_ticks,process_group=process.pid,command=invocation))
            code=process.wait(timeout=timeout_seconds)
    except BaseException as error:failure=error
    finally:
        if process is not None:
            try:remaining=cleanup_group(process,start_ticks if start_ticks is not None else 0)
            except BaseException as cleanup:
                if failure is None:failure=cleanup
                else:failure=RuntimeError(str(failure)+'; owned cleanup also failed: '+str(cleanup))
        write(work/(label+'.process.json'),dict(command=invocation,exit_code=code,elapsed_seconds=time.monotonic()-started,status='completed-process-only' if failure is None and code==0 else 'FAIL2-retained-no-result',error=str(failure) if failure else None,owned_live_after_cleanup=remaining))
    if failure is not None:raise failure
    if code!=0:raise RuntimeError('Original Nextflow '+label+' failed; read retained stdout/stderr and engine log')


def health(home,report,version):
    env=environment(home,report);engine=home/'nextflow'
    execute(['/usr/bin/bash',engine,'-version'],env,report,'version')
    version_text=(report/'version.stdout').read_text()+(report/'version.stderr').read_text()
    if '\n      version '+version+' build ' not in version_text:raise ValueError('Exact original engine version text differs')
    work=report/'health';work.mkdir();(work/'main.nf').write_text(HEALTH,encoding='utf-8',newline='\n');(work/'nextflow.config').write_text(HEALTH_CONFIG,encoding='utf-8',newline='\n')
    execute(['/usr/bin/bash',engine,'-C',work/'nextflow.config','-log',work/'nextflow.log','run',work/'main.nf','-ansi-log','false','-with-trace',work/'trace.tsv','-work-dir',work/'work','--outdir',work/'published'],env,work,'health')
    import csv
    rows=list(csv.DictReader((work/'trace.tsv').open(),delimiter='\t'))
    if len(rows)!=3 or any(row['status']!='COMPLETED' or row['exit']!='0' for row in rows) or (work/'published/result.txt').read_bytes()!=b'ACGT\nTTAA\n':raise ValueError('Complete synthetic DAG result or task family differs')
    write(report/'health.json',dict(status='passed-three-task-original-Nextflow-DAG',version=version,task_count=3,workflow=pin(work/'main.nf'),config=pin(work/'nextflow.config'),trace=pin(work/'trace.tsv'),output=pin(work/'published/result.txt'),user_analysis_admitted=False))


def verify_home(home,locked,profile):
    marker=json_read(home/MARKER)
    if marker['schema_version']!=1 or marker['lock_sha256']!=LOCK_SHA256 or marker['profile']!=profile or marker['version']!=locked['profiles'][profile]['version']:raise ValueError('Runtime marker/edition/lock differs')
    if marker['payload']!=inventory(home,[MARKER]):raise ValueError('Runtime payload changed or contains unmanaged files')
    if {key:value for key,value in marker['payload']['nextflow'].items() if key!='mode'}!={'kind':'file',**{k:locked['profiles'][profile]['engine'][k] for k in ['bytes','sha256']}}:raise ValueError('Exact engine payload differs')
    if content_inventory({name[5:]:value for name,value in marker['payload'].items() if name.startswith('java/')})!=content_inventory(locked['java']['members']):raise ValueError('Locked Java payload differs')
    if marker['permission_mode_departures']!=mode_departures(home,locked):raise ValueError('Effective permission-mode audit differs')
    if not os.access(home/'nextflow',os.X_OK) or not os.access(home/'java/bin/java',os.X_OK):raise Unavailable('Required engine/Java execution is forbidden')
    return marker


def install(args):
    home,report,cache=map(private,[args.home,args.report,args.cache]);archive_dir=private(args.archive_dir) if args.archive_dir else None
    locations=[home,report,cache]
    if any(a==b or a.is_relative_to(b) or b.is_relative_to(a) for i,a in enumerate(locations) for b in locations[i+1:]):raise ValueError('Runtime home, report and cache must be disjoint')
    report.mkdir(parents=True,exist_ok=False);owned=True
    try:
        if sys.platform!='linux' or platform.machine().lower() not in {'x86_64','amd64'} or sys.version_info<(3,11):raise Unavailable('Require Linux x86_64 Python3.11+ for this exact upstream runtime')
        if pin(LOCK)['sha256']!=LOCK_SHA256:raise ValueError('Bundled managed-runtime lock changed')
        locked=json_read(LOCK);profile=locked['profiles'].get(args.profile)
        if profile is None:raise Unavailable('Requested exact Nextflow engine edition is unsupported; no substitution')
        helper_pins=helpers(locked);cache.mkdir(parents=True,exist_ok=True)
        write(report/'request.json',dict(schema_version=1,profile=args.profile,home=str(home),allow_install=args.allow_install,allow_download=args.allow_download,lock_sha256=LOCK_SHA256,installer=pin(Path(__file__)),user_analysis_admitted=False,required_action=ACTION))
        existing=home.exists()
        if existing:before=verify_home(home,locked,args.profile)
        else:
            if not args.allow_install:raise Unavailable('Installation is not allowed and the exact managed home is absent')
            engine=fetch(cache,archive_dir,profile['engine'],args.allow_download);java=fetch(cache,archive_dir,locked['java']['package'],args.allow_download)
            home.mkdir(parents=True,exist_ok=False);shutil.copyfile(engine,home/'nextflow');os.chmod(home/'nextflow',0o755);extract_java(java,home,locked['java'])
        health(home,report,profile['version'])
        if helpers(locked)!=helper_pins:raise ValueError('A required helper changed during setup')
        if existing:
            if verify_home(home,locked,args.profile)!=before:raise ValueError('Existing exact runtime changed during health recheck')
        else:
            marker=dict(schema_version=1,tool='Nextflow',profile=args.profile,version=profile['version'],lock_sha256=LOCK_SHA256,payload=inventory(home),permission_mode_departures=mode_departures(home,locked),user_analysis_admitted=False)
            write(home/MARKER,marker);verify_home(home,locked,args.profile)
        permission_warning=''
        departures=mode_departures(home,locked)
        if departures:permission_warning='\n\n**Effective filesystem modes differ from archived/requested POSIX modes for '+str(len(departures))+' files.** Exact file bytes and link targets remain checked; required execution is permitted. Inspect the marker permission_mode_departures audit. If exact POSIX mode preservation is required, use a filesystem supporting those modes and rerun to new home/report paths.'
        with (report/'WARNINGS.md').open('x',encoding='utf-8') as out:out.write('# Nextflow SETUP ONLY\n\nThree synthetic tasks completed. No user analysis was run or admitted.'+permission_warning+'\n\nRequired action: '+ACTION+'\n')
        manifest=dict(schema_version=1,status='COMPLETE_SETUP_ONLY',profile=args.profile,version=profile['version'],home=str(home),host=dict(os='Linux',architecture=platform.machine(),kernel=platform.release()),lock_sha256=LOCK_SHA256,marker=pin(home/MARKER),helpers=helper_pins,files=inventory(report),user_analysis_admitted=False)
        write(report/'manifest.json',manifest);write(report/'COMPLETED.json',dict(status='COMPLETE_SETUP_ONLY',manifest=pin(report/'manifest.json'),user_analysis_admitted=False))
        print(json.dumps(dict(status='COMPLETE_SETUP_ONLY',profile=args.profile,home=str(home),report=str(report),user_analysis_admitted=False)));return 0
    except BaseException as failure:
        code=3 if isinstance(failure,Unavailable) else 2;label='SKIP3_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_RESULT_ADMITTED'
        if owned:
            write(report/'FAILURE.json',dict(status=label,exit_code=code,error=str(failure),home=str(home),user_analysis_admitted=False,required_action=ACTION))
            with (report/'FAILURE.md').open('x',encoding='utf-8') as out:out.write('# Nextflow '+label+'\n\n'+str(failure)+'\n\nRequired action: '+ACTION+'\n')
        print('Nextflow '+label+': '+str(failure)+'\nRequired action: '+ACTION,file=sys.stderr);return code


def main():
    def interrupted(signum,frame):raise KeyboardInterrupt('Nextflow setup interrupted by signal '+str(signum))
    signal.signal(signal.SIGTERM,interrupted);signal.signal(signal.SIGINT,interrupted)
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--profile',required=True);parser.add_argument('--home',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--archive-dir',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');return install(parser.parse_args())


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('Nextflow FAIL2_NO_RESULT_ADMITTED: '+str(failure)+'\nRequired action: '+ACTION,file=sys.stderr);sys.exit(2)
