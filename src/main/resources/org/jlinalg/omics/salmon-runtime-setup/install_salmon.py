#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit private Salmon setup with original synthetic health; never user analysis."""
import argparse
import copy
from decimal import Decimal
import gzip
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import signal
import stat
import struct
import subprocess
import sys
import tarfile
import time
import urllib.request

LOCK_SHA='5e76aae9e640caf15ab995a314151c4d5503f4afddc112858fabdb9b1e9d8076'
HEALTH_SHA='b99b4fd8dcbcb92bae0312293d3e7f73a549a2a81a045b8b1ac812ba98f72778'
MARKER='.omicsmatic-salmon.json'
ACTION='Required action: inspect WARNINGS.md, FAILURE.json when present, all launch/process receipts and original stdout/stderr. This is synthetic runtime setup only; it does not admit a user analysis. SKIP3 means a required permitted host/runtime/resource is unavailable: skip dependent analysis and complete permitted setup. FAIL2 means input, integrity, process, numerical, capacity or finalization failure: no result is admitted. Preserve every partial home/report/download and use NEW paths after correction. Install only with --allow-install; download only with --allow-download or supply complete --archive-dir files. Use Linux x86_64 Python 3.12+ with -I -B and a filesystem that preserves the declared modes. Hashes prove integrity, not authenticity. Cell Ranger remains excluded.'


class Unavailable(Exception):pass


def write(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n')
def pin(path,maximum=1073741824):
    if path.is_symlink() or not path.is_file():raise ValueError('Require a canonical regular file: '+str(path))
    before=path.stat()
    if before.st_size>maximum:raise ValueError('Complete file exceeds capacity: '+str(path))
    with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    after=path.stat()
    if (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns,before.st_ctime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns,after.st_ctime_ns):raise ValueError('File changed during complete hash: '+str(path))
    return dict(bytes=after.st_size,sha256=digest)
def document(path,expected=None):
    raw=path.read_bytes()
    if len(raw)>8388608 or expected is not None and hashlib.sha256(raw).hexdigest()!=expected:raise ValueError('JSON capacity/hash differs: '+str(path))
    def pairs(items):
        result={}
        for k,v in items:
            if k in result:raise ValueError('Duplicate JSON key: '+k)
            result[k]=v
        return result
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda v:(_ for _ in ()).throw(ValueError('Nonfinite JSON: '+v)))
def private(path):
    result=Path(os.path.abspath(path))
    if not str(result).isascii() or any(c.isspace() or ord(c)<32 or ord(c)==127 for c in str(result)) or result.parent==result:raise ValueError('Require private absolute ASCII paths without whitespace')
    if any(p.is_symlink() for p in [result,*result.parents]):raise ValueError('Linked private path/ancestor: '+str(result))
    return result
def inventory(root,excluded=()):
    result={};total=0
    for p in sorted(root.rglob('*')):
        name=p.relative_to(root).as_posix()
        if name in excluded:continue
        info=p.lstat()
        if stat.S_ISDIR(info.st_mode):result[name]=dict(kind='directory',mode=stat.S_IMODE(info.st_mode))
        elif stat.S_ISREG(info.st_mode):
            value=pin(p);total+=value['bytes'];result[name]=dict(kind='file',mode=stat.S_IMODE(info.st_mode),**value)
        else:raise ValueError('Linked or special node in complete setup population: '+name)
        if len(result)>4096 or total>2147483648:raise ValueError('Complete setup population exceeds file/byte capacity')
    return result
def verify_payload(home,profile):
    actual=inventory(home,(MARKER,));expected={name:{k:v for k,v in row.items() if k!='archive_mode'} for name,row in profile['members'].items()}
    if actual!=expected:raise ValueError('Complete runtime payload bytes, types, modes or namespace differ')
    if not os.access(home/'bin/salmon',os.R_OK|os.X_OK):raise Unavailable('Original runtime is not readable/executable; supply a permitted private Linux home')
    return actual


def acquire(package,report,archive_dir,cache,allow_download):
    candidates=[]
    if archive_dir is not None:candidates.append(private(archive_dir)/package['filename'])
    if cache is not None:candidates.append(private(cache)/package['filename'])
    for candidate in candidates:
        if candidate.exists():
            if pin(candidate)!={k:package[k] for k in ['bytes','sha256']}:raise ValueError('Complete pinned runtime archive differs: '+str(candidate))
            return candidate
    if not allow_download:raise Unavailable('Exact archive is absent and downloading is not authorized; supply --archive-dir or permitted --allow-download')
    downloads=report/'downloads';downloads.mkdir();partial=downloads/(package['filename']+'.partial');expected=package['bytes'];received=0
    request=urllib.request.Request(package['url'],headers={'User-Agent':'OmicsMatic-Salmon-setup/1.0'})
    try:
        with urllib.request.urlopen(request,timeout=180) as response,partial.open('xb') as target:
            if not response.url.startswith('https://'):raise ValueError('Non-HTTPS runtime redirect')
            while block:=response.read(1048576):
                received+=len(block)
                if received>expected:raise ValueError('Runtime download exceeds exact archive size')
                target.write(block)
    except OSError as error:raise Unavailable('Runtime archive acquisition unavailable: '+str(error)) from error
    if pin(partial)!={k:package[k] for k in ['bytes','sha256']}:raise ValueError('Complete downloaded archive hash/size differs; retain the partial')
    destination=downloads/package['filename'];partial.rename(destination);return destination


def extract(archive,home,profile):
    consumed=pin(archive)
    if consumed!={k:profile['archive'][k] for k in ['bytes','sha256']}:raise ValueError('Original runtime archive changed before extraction')
    if home.exists():raise ValueError('Preserve partial/unregistered home and choose NEW paths')
    home.mkdir(mode=0o700)
    with tarfile.open(archive) as t:
        members=t.getmembers();expected=profile['members'];selected=[];seen=set()
        for member in members:
            name=PurePosixPath(member.name)
            if name.is_absolute() or '..' in name.parts or '\\' in member.name or name.parts[0]!=profile['archive_root']:raise ValueError('Runtime archive path differs')
            if len(name.parts)==1:
                if not member.isdir():raise ValueError('Invalid archive root')
                continue
            relative=PurePosixPath(*name.parts[1:]).as_posix()
            if relative in seen or relative not in expected:raise ValueError('Extra/duplicate runtime archive member')
            seen.add(relative);row=expected[relative]
            if member.mode&0o6000 or member.mode&0o777!=row.get('archive_mode',row['mode']):raise ValueError('Original archive member mode differs')
            if row['kind']=='file':
                if not member.isfile() or member.size!=row['bytes']:raise ValueError('Original archive file type/size differs')
            elif row['kind']=='directory':
                if not member.isdir():raise ValueError('Original archive directory type differs')
            else:raise ValueError('Unexpected linked runtime profile; no unreviewed link extraction')
            clone=copy.copy(member);clone.name=relative;clone.mode=row['mode'];clone.uid=clone.gid=None;clone.uname=clone.gname=None;selected.append(clone)
        if seen!=set(expected):raise ValueError('Incomplete runtime archive population')
        t.extractall(home,members=selected,filter='data')
    if pin(archive)!=consumed:raise ValueError('Original runtime archive changed while being consumed; preserve the partial home')
    for name,row in profile['members'].items():
        p=home/name
        try:p.chmod(row['mode'])
        except OSError as error:raise Unavailable('Cannot preserve required runtime permissions; use a permitted native Linux filesystem: '+str(error)) from error
        if stat.S_IMODE(p.stat().st_mode)!=row['mode']:raise Unavailable('Filesystem did not preserve runtime modes; use a permitted native Linux filesystem')
    return verify_payload(home,profile)


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
    for sig,seconds in [(signal.SIGTERM,5),(signal.SIGKILL,2)]:
        if group_live(process.pid,start):
            try:os.killpg(process.pid,sig)
            except ProcessLookupError:pass
        deadline=time.monotonic()+seconds
        while group_live(process.pid,start) and time.monotonic()<deadline:time.sleep(.05)
    process.wait(timeout=5);remaining=group_live(process.pid,start)
    if remaining:raise RuntimeError('Owned Salmon setup processes remain alive: '+str(remaining))
    return remaining
def execute(command,env,report,label,timeout_seconds=300,allow_nonzero=False):
    guarded=['/usr/bin/prlimit','--as=8589934592','--cpu=180','--fsize=536870912','--nofile=4096','--core=0','--',*map(str,command)]
    process=None;code=None;failure=None;start_ticks=None;remaining=[];live_before=[];started=time.monotonic()
    try:
        with (report/(label+'.stdout')).open('xb') as stdout,(report/(label+'.stderr')).open('xb') as stderr:
            process=subprocess.Popen(guarded,cwd=report,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True)
            start_ticks=int((Path('/proc')/str(process.pid)/'stat').read_text().rsplit(')',1)[1].split()[19])
            write(report/(label+'.launch.json'),dict(pid=process.pid,start_ticks=start_ticks,process_group=process.pid,command=guarded))
            code=process.wait(timeout=timeout_seconds)
    except BaseException as error:failure=error
    finally:
        if process is not None:
            try:
                live_before=group_live(process.pid,start_ticks if start_ticks is not None else 0)
                remaining=cleanup_group(process,start_ticks if start_ticks is not None else 0)
                if code==0 and live_before and failure is None:failure=RuntimeError('Original setup parent exited while owned workers remained; no complete result is admitted')
            except BaseException as cleanup:failure=RuntimeError(str(failure)+'; cleanup failed: '+str(cleanup))
        write(report/(label+'.process.json'),dict(command=guarded,environment=env,exit_code=code,elapsed_seconds=time.monotonic()-started,error=str(failure) if failure else None,owned_live_before_cleanup=live_before,forced_cleanup=bool(live_before),owned_live_after_cleanup=remaining,status='completed-process-only' if failure is None and code==0 else 'FAIL2-retained-no-result'))
    if failure is not None:raise failure
    if code!=0 and not allow_nonzero:raise RuntimeError('Original Salmon '+label+' failed; inspect complete retained stdout/stderr')
    return code


def helper_pins():
    result={}
    for name in ['/usr/bin/prlimit','/usr/bin/ldd','/usr/bin/bash','/usr/bin/setsid']:
        p=Path(name)
        if not p.exists() or not os.access(p,os.R_OK|os.X_OK):raise Unavailable('Required existing Linux helper unavailable/forbidden: '+name)
        real=p.resolve(strict=True);result[name]=dict(real_path=str(real),mode=stat.S_IMODE(real.stat().st_mode),**pin(real))
    return result
def libraries(home,env,report):
    code=execute(['/usr/bin/ldd',home/'bin/salmon'],env,report,'libraries',allow_nonzero=True);stdout=(report/'libraries.stdout').read_text();stderr=(report/'libraries.stderr').read_text()
    if 'not found' in stdout+stderr or 'Permission denied' in stdout+stderr:raise Unavailable('Required runtime library unavailable/forbidden; inspect libraries.stdout/stderr and supply a compatible permitted Linux host')
    if code!=0:raise RuntimeError('Runtime library inspection failed; inspect retained libraries.stdout/stderr')
    if stderr.strip():raise ValueError('Unexpected library inspection stderr: '+stderr)
    rows={};vdso=False
    for line in stdout.splitlines():
        line=line.strip()
        if re.fullmatch(r'linux-vdso\.so\.1\s+\(0x[0-9a-fA-F]+\)',line):vdso=True;continue
        match=re.fullmatch(r'(\S+)\s+=>\s+(/\S+)\s+\(0x[0-9a-fA-F]+\)',line)
        if match:name,path=match.groups()
        else:
            match=re.fullmatch(r'(/\S+)\s+\(0x[0-9a-fA-F]+\)',line)
            if not match:raise Unavailable('Unresolved/unsupported runtime library binding: '+line)
            path=match.group(1);name='@loader'
        if name in rows:raise ValueError('Duplicate library binding: '+name)
        real=Path(path).resolve(strict=True);rows[name]=dict(reported_path=path,real_path=str(real),mode=stat.S_IMODE(real.stat().st_mode),**pin(real))
    if not vdso or '@loader' not in rows:raise ValueError('Incomplete Linux library binding report')
    return rows


def health_audit(folder,expected,targets,version):
    meta=document(folder/'aux_info/meta_info.json');names=[r['name'] for r in targets];lines=(folder/'quant.sf').read_text().splitlines()
    if not lines or lines[0]!='Name\tLength\tEffectiveLength\tTPM\tNumReads':raise ValueError('Original complete quantification header differs')
    rows=[line.split('\t') for line in lines[1:]]
    if len(rows)!=len(targets) or [r[0] for r in rows]!=names:raise ValueError('Complete original quantified target population differs')
    for row,target in zip(rows,targets):
        if len(row)!=5 or row[1]!=str(target['length']) or any(not math.isfinite(float(v)) or float(v)<0 for v in row[2:]) or float(row[2])<=0:raise ValueError('Original quantification length/domain differs')
    for field,value in [('salmon_version',version),('num_processed',expected['processed_fragments']),('num_mapped',expected['mapped_fragments']),('num_valid_targets',expected['valid_targets']),('num_decoy_targets',expected['decoy_targets']),('num_bootstraps',expected['bootstraps']),('samp_type','bootstrap'),('opt_type',expected['optimizer']),('quant_errors',[])]:
        if meta.get(field)!=value:raise ValueError('Original health metadata differs: '+field)
    if abs(sum(Decimal(r[4]) for r in rows)-expected['mapped_fragments'])>Decimal('0.0005')*len(rows):raise ValueError('Complete original health count total differs')
    if abs(sum(Decimal(r[3]) for r in rows)-1000000)>Decimal('0.0000005')*len(rows)+Decimal('0.0000001'):raise ValueError('Complete original health TPM total differs')
    original_names=gzip.decompress((folder/'aux_info/bootstrap/names.tsv.gz').read_bytes())
    if original_names!=('\t'.join(names)+'\n').encode():raise ValueError('Complete original bootstrap identities differ')
    raw=gzip.decompress((folder/'aux_info/bootstrap/bootstraps.gz').read_bytes());values=len(names)*expected['bootstraps']
    if len(raw)!=8*values or any(not math.isfinite(v[0]) or v[0]<0 for v in struct.iter_unpack('<d',raw)):raise ValueError('Complete original bootstrap shape/domain differs')
    return dict(status='passed-original-setup-health-shape-and-totals-only',targets=len(rows),bootstrap_values=values,meta_info=meta,complete_original_result_files=inventory(folder),user_analysis_admitted=False)


def install(args,report):
    if platform.system()!='Linux' or platform.machine() not in {'x86_64','amd64'} or sys.version_info<(3,12):raise Unavailable('Setup requires permitted Linux x86_64 Python3.12+; use Python inside WSL on Windows')
    if not sys.flags.isolated or not sys.dont_write_bytecode:raise Unavailable('Run setup with python3 -I -B')
    kit=private(args.kit);lock=document(kit/'salmon-runtime-lock.json',LOCK_SHA);health=document(kit/'health-lock.json',HEALTH_SHA)
    if args.profile not in lock['profiles']:raise ValueError('Unknown exact Salmon profile')
    profile=lock['profiles'][args.profile];home=private(args.home)
    if home==report or home in report.parents or report in home.parents:raise ValueError('Runtime home and report must be separate nonoverlapping private directories')
    if not home.parent.is_dir():raise Unavailable('Create a permitted canonical private home parent first')
    script_before=pin(Path(__file__).resolve());helpers=helper_pins();request=dict(profile=args.profile,home=str(home),kit=str(kit),allow_install=args.allow_install,allow_download=args.allow_download,archive_dir=str(args.archive_dir) if args.archive_dir else None,installer=script_before,lock_sha256=LOCK_SHA,health_lock_sha256=HEALTH_SHA,user_analysis_admitted=False)
    write(report/'request.json',request)
    for name,row in health['files'].items():
        if pin(kit/'health-inputs'/name)!=row:raise ValueError('Pinned synthetic health input differs: '+name)
    if home.exists():
        if not (home/MARKER).is_file():raise ValueError('Existing unregistered/partial home is preserved; choose NEW paths')
        marker=document(home/MARKER)
        if marker.get('profile')!=args.profile or marker.get('lock_sha256')!=LOCK_SHA:raise ValueError('Existing private runtime marker differs; preserve it and use NEW paths')
        verify_payload(home,profile);installed=False
    else:
        if not args.allow_install:raise Unavailable('Exact private Salmon home is absent and installation is not authorized; use permitted --allow-install or an admitted existing home')
        archive=acquire(profile['archive'],report,args.archive_dir,args.cache,args.allow_download);payload=extract(archive,home,profile)
        write(home/MARKER,dict(schema_version=1,status='ORIGINAL_PAYLOAD_ONLY',profile=args.profile,lock_sha256=LOCK_SHA,version=profile['version'],archive={k:profile['archive'][k] for k in ['filename','url','bytes','sha256']},payload=payload,installer=script_before,permission_normalizations=profile['permission_normalizations'],scientific_runtime_admitted=False,user_analysis_admitted=False));installed=True
    env=dict(PATH='/usr/bin:/bin',HOME=str(report),TMPDIR=str(report),LANG='C',LC_ALL='C',TZ='UTC',LD_LIBRARY_PATH=str(home/'lib'),SALMON_NO_VERSION_CHECK='1')
    execute([home/'bin/salmon','--version'],env,report,'version')
    if (report/'version.stdout').read_text().strip()!='salmon '+profile['version'] or (report/'version.stderr').read_bytes():raise ValueError('Original exact version receipt differs')
    bindings=libraries(home,env,report);inputs=report/'inputs';inputs.mkdir()
    for name,row in health['files'].items():shutil.copyfile(kit/'health-inputs'/name,inputs/name)
    (report/'health').mkdir();audits=[]
    for case in health['commands']:
        command=[value.replace('@HOME@',str(home)).replace('@REPORT@',str(report)) for value in case['command']]
        execute(command,env,report,case['label'])
        if 'output' in case:audits.append(dict(label=case['label'],audit=health_audit(report/case['output'],case['expected'],health['targets'],profile['version'])))
    for row in bindings.values():
        real=Path(row['reported_path']).resolve(strict=True)
        if str(real)!=row['real_path'] or pin(real)!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Original current-host library changed during health')
    if helper_pins()!=helpers or pin(Path(__file__).resolve())!=script_before:raise ValueError('Setup helper/installer changed during health')
    verify_payload(home,profile)
    for name,row in health['files'].items():
        if pin(inputs/name)!=row or pin(kit/'health-inputs'/name)!=row:raise ValueError('Synthetic health input changed during execution')
    write(report/'health.json',dict(status='passed-two-original-optimizer-bias-and-bootstrap-health-controls',profile=args.profile,version=profile['version'],audits=audits,scientific_runtime_admitted=False,user_analysis_admitted=False))
    (report/'WARNINGS.md').write_text('# Salmon setup only — user analysis not admitted\n\n'+health['source_warning']+'\n\nOriginal positional-bias crashes on the separate four-target development fixture remain unresolved. This complete 40-target setup control succeeds; do not silently disable requested bias correction or admit failed/partial original outputs.\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
    manifest=dict(schema_version=1,profile=args.profile,version=profile['version'],home=str(home),installed=installed,marker=pin(home/MARKER),helpers=helpers,library_bindings=bindings,
        lock_sha256=LOCK_SHA,health_lock_sha256=HEALTH_SHA,installer=script_before,files=inventory(report),scientific_runtime_admitted=False,user_analysis_admitted=False)
    write(report/'manifest.json',manifest);write(report/'COMPLETED.json',dict(status='COMPLETE_SETUP_ONLY',profile=args.profile,manifest=pin(report/'manifest.json'),scientific_runtime_admitted=False,user_analysis_admitted=False))
    print(json.dumps(dict(status='COMPLETE_SETUP_ONLY',profile=args.profile,home=str(home),report=str(report),installed=installed,user_analysis_admitted=False)))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--profile',required=True);parser.add_argument('--home',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--kit',type=Path,default=Path(__file__).resolve().parent);parser.add_argument('--archive-dir',type=Path);parser.add_argument('--cache',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args();report=None;fresh=False
    try:
        report=private(args.report)
        if report.exists():raise ValueError('Preserve existing setup report and choose a NEW report path')
        if not report.parent.is_dir():raise Unavailable('Create a permitted canonical report parent first')
        report.mkdir(mode=0o700);fresh=True;install(args,report);return 0
    except BaseException as error:
        code=3 if isinstance(error,(Unavailable,PermissionError,FileNotFoundError)) else 2;status='SKIP3 — SETUP/ANALYSIS SKIPPED' if code==3 else 'FAIL2 — NO RESULT ADMITTED'
        detail=str(error) or type(error).__name__;print('Salmon '+status+': '+detail+'\n'+ACTION,file=sys.stderr)
        if fresh and report is not None and report.is_dir():
            if (report/'COMPLETED.json').exists():(report/'COMPLETED.json').rename(report/'COMPLETED-REVOKED.json')
            if (report/'WARNINGS.md').exists():(report/'WARNINGS.md').rename(report/'WARNINGS-PREFINAL.md')
            write(report/'FAILURE.json',dict(status=status,exit_code=code,error=detail,required_action=ACTION,user_analysis_admitted=False))
            (report/'WARNINGS.md').write_text('# Salmon '+status+'\n\n'+detail+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        return code


if __name__=='__main__':sys.exit(main())
