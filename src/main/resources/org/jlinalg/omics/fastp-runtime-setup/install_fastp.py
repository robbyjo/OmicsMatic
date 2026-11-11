#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Private pinned fastp Linux setup; a synthetic health check admits no user analysis."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import signal
import stat
import subprocess
import sys
import urllib.request

ROOT=Path(__file__).resolve().parents[2]
LOCK_PATH=Path(__file__).with_name('fastp-runtime-lock.json')
LOCK_SHA256='06760eb5048a8cb095d6440603cf941c86947a0e93393c111148c0b387745cfe'
MARKER='.omicsmatic-fastp.json'
ACTION=('Skip dependent analysis while the required permitted runtime is unavailable. '
        'Use Linux x86_64 Python3.11+ (Linux Python inside WSL on Windows), a NEW private home/report, '
        'and run python3 tools/literature/install_fastp.py --profile linux-x86_64-fastp-0.23.2 '
        '--home NEW_HOME --report NEW_REPORT --cache CACHE --allow-install. '
        'Add --allow-download only where permitted, or place the exact pinned binary in CACHE/fastp.VERSION. '
        'Select the exact requested supported edition; never resolve a paper\'s unspecified patch silently. '
        'Preserve changed homes, corrupt caches, partial downloads and failed outputs; correct the cause and use NEW paths. '
        'If installation/execution is forbidden, keep dependent analysis skipped. Require COMPLETED.json and inspect warnings. '
        'This development setup and tiny synthetic smoke test do not provide a public fastp analysis workflow or whole-paper reproduction. '
        'Deduplication accuracy6 needs24GiB for its Bloom buffer plus overhead; do not lower accuracy silently. Cell Ranger remains excluded.')


class Unavailable(Exception):
    """Missing permission or host capability requires skipped dependent analysis."""


def digest(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def strict_json(raw):
    def pairs(items):
        result={}
        for key,value in items:
            if key in result:raise ValueError('Duplicate JSON key: '+key)
            result[key]=value
        return result
    def bad(value):raise ValueError('Nonfinite JSON value: '+value)
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=bad)


def lock():
    raw=LOCK_PATH.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=LOCK_SHA256:raise ValueError('Pinned fastp lock differs')
    result=strict_json(raw)
    if [p['version'] for p in result['profiles']]!=['0.22.0','0.23.2','0.25.0','1.0.1']:raise ValueError('Unexpected fastp editions')
    return result


def private(path):
    path=Path(os.path.abspath(path))
    if any(ord(c)<32 or ord(c)==127 for c in str(path)):raise ValueError('Control character in private path')
    for parent in [path,*path.parents]:
        if parent.is_symlink():raise ValueError('Linked private path or ancestor: '+str(parent))
    return path


def fingerprint(path,maximum):
    path=private(path); before=path.stat(follow_symlinks=False)
    if not stat.S_ISREG(before.st_mode) or before.st_nlink!=1 or not 0<=before.st_size<=maximum:raise ValueError('Require bounded unlinked regular file: '+str(path))
    pin=dict(bytes=before.st_size,sha256=digest(path)); after=path.stat(follow_symlinks=False)
    if (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns):raise ValueError('File changed while hashing: '+str(path))
    return pin


def write_json(path,value):
    with path.open('xb') as out:out.write((json.dumps(value,indent=2)+'\n').encode())


def identity(profile,license_pin):
    return dict(schema_version=1,profile=profile['profile'],version=profile['version'],lock_sha256=LOCK_SHA256,
                files={'fastp':{k:profile['binary'][k] for k in ['bytes','sha256']},'LICENSE':license_pin},
                status='PINNED_DEVELOPMENT_RUNTIME_ONLY',user_analysis_admitted=False,source_to_binary_reproduction_verified=False)


def verify_home(home,expected):
    actual={p.name for p in home.iterdir()}
    if actual!={'fastp','LICENSE',MARKER}:raise ValueError('Changed/unmanaged runtime census; preserve it and select a NEW home')
    fingerprint(home/MARKER,65536)
    if strict_json((home/MARKER).read_bytes())!=expected:raise ValueError('Runtime marker differs')
    for name,pin in expected['files'].items():
        if fingerprint(home/name,pin['bytes'])!=pin:raise ValueError('Runtime file differs: '+name)


def execute(binary,arguments,work,label):
    import resource
    def capacities():
        resource.setrlimit(resource.RLIMIT_AS,(3*1024**3,3*1024**3));resource.setrlimit(resource.RLIMIT_CPU,(60,60));resource.setrlimit(resource.RLIMIT_FSIZE,(16*1024**2,16*1024**2));resource.setrlimit(resource.RLIMIT_NOFILE,(64,64))
    command=[str(binary),*arguments]
    with (work/(label+'.stdout')).open('xb') as stdout,(work/(label+'.stderr')).open('xb') as stderr:
        process=subprocess.Popen(command,cwd=work,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=capacities)
        try:code=process.wait(timeout=120)
        except BaseException:
            try:os.killpg(process.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            process.wait();raise
    write_json(work/(label+'.command.json'),dict(command=command,exit_code=code,address_space_bytes=3*1024**3,cpu_seconds=60,output_file_bytes=16*1024**2,wall_seconds=120))
    if code!=0:raise RuntimeError('Original fastp '+label+' failed; inspect retained stdout/stderr')


def smoke(home,report,version):
    execute(home/'fastp',['--version'],report,'version')
    if ((report/'version.stdout').read_bytes()+(report/'version.stderr').read_bytes()).strip()!=('fastp '+version).encode():raise ValueError('Original version text differs')
    sequence='ACGCTAGCATCGACTGATCGACTAGCATAGCTACGATCGATGCTAGCTAGCATGCATCGATGCTAGCATCGATGCATCGATGCTAGCATCGATGCATCGATG'
    raw=''.join('@fixture:'+str(i)+'\n'+sequence+'\n+\n'+'I'*len(sequence)+'\n' for i in range(1,33)).encode()
    (report/'reads.fq').write_bytes(raw)
    execute(home/'fastp',['--in1','reads.fq','--out1','processed.fq','--json','fastp.json','--html','fastp.html','--thread','1','--dont_overwrite'],report,'health')
    if (report/'processed.fq').read_bytes()!=raw:raise ValueError('Complete tiny synthetic FASTQ population differs')
    fingerprint(report/'fastp.json',1048576); result=strict_json((report/'fastp.json').read_bytes())
    for stage in ['before_filtering','after_filtering']:
        for key,value in [('total_reads',32),('total_bases',32*len(sequence)),('q20_bases',32*len(sequence)),('q30_bases',32*len(sequence))]:
            if result['summary'][stage][key]!=value:raise ValueError('Tiny synthetic report count differs: '+stage+'/'+key)


def install(args):
    home=private(args.home); report=private(args.report); cache=private(args.cache)
    if home==report or home in report.parents or report in home.parents or home==cache or home in cache.parents or cache in home.parents or cache==report or cache in report.parents or report in cache.parents:raise ValueError('Home/report/cache must be disjoint')
    report.mkdir(parents=True,exist_ok=False)
    record=dict(status='setup-in-progress',profile=args.profile,home=str(home),allow_install=args.allow_install,allow_download=args.allow_download,user_analysis_admitted=False,required_action=ACTION)
    code=2
    try:
        data=lock();matches=[p for p in data['profiles'] if p['profile']==args.profile]
        if len(matches)!=1:raise Unavailable('Unsupported fastp edition/profile; no implicit substitution')
        if sys.platform!='linux' or platform.machine().lower() not in {'x86_64','amd64'} or sys.version_info<(3,11):raise Unavailable('Require Linux x86_64 Python3.11+; use Linux Python inside WSL on Windows')
        profile=matches[0];license_path=ROOT/data['license']['path'];license_pin=fingerprint(license_path,65536)
        if license_pin['sha256']!=data['license']['sha256']:raise ValueError('Original MIT license differs')
        expected=identity(profile,license_pin);record.update(version=profile['version'],binary_source=profile['binary']['url'],lock_sha256=LOCK_SHA256)
        if home.exists():verify_home(home,expected);record['runtime_reused']=True
        else:
            if not args.allow_install:raise Unavailable('Installation not permitted: --allow-install is required')
            cache.mkdir(parents=True,exist_ok=True);payload=cache/('fastp.'+profile['version'])
            if not payload.exists():
                if not args.allow_download:raise Unavailable('Download not permitted: provide exact offline cache or --allow-download where permitted')
                partial=report/'download.part'
                request=urllib.request.Request(profile['binary']['url'],headers={'User-Agent':'OmicsMatic-fastp-development-setup/1'})
                with urllib.request.urlopen(request,timeout=60) as source,partial.open('xb') as out:
                    total=0
                    while chunk:=source.read(1024*1024):
                        total+=len(chunk)
                        if total>profile['binary']['bytes']:raise ValueError('Original binary exceeds pinned byte count')
                        out.write(chunk)
                if fingerprint(partial,profile['binary']['bytes'])!={k:profile['binary'][k] for k in ['bytes','sha256']}:raise ValueError('Downloaded binary differs; partial retained without execution')
                with partial.open('rb') as source,payload.open('xb') as out:
                    while chunk:=source.read(1024*1024):out.write(chunk)
            expected_binary={k:profile['binary'][k] for k in ['bytes','sha256']}
            if fingerprint(payload,profile['binary']['bytes'])!=expected_binary:raise ValueError('Cached binary differs; preserve it without execution')
            home.mkdir(parents=True,exist_ok=False)
            with payload.open('rb') as source,(home/'fastp').open('xb') as out:
                while chunk:=source.read(1024*1024):out.write(chunk)
            (home/'fastp').chmod(0o755)
            with (home/'LICENSE').open('xb') as out:out.write(license_path.read_bytes())
            write_json(home/MARKER,expected);verify_home(home,expected)
        smoke(home,report,profile['version']);verify_home(home,expected)
        record.update(status='passed-development-runtime-and-tiny-synthetic-health-only',runtime_identity=expected,whole_paper_reproduced=False)
        write_json(report/'setup.json',record)
        files={p.name:fingerprint(p,32*1024**2) for p in sorted(report.iterdir()) if p.is_file()}
        write_json(report/'manifest.json',dict(schema_version=1,files=files));write_json(report/'COMPLETED.json',dict(status='COMPLETE_SETUP_ONLY',manifest=fingerprint(report/'manifest.json',1048576),user_analysis_admitted=False));code=0
    except Unavailable as failure:record.update(status='SKIP3-dependency-unavailable-or-not-permitted',error=str(failure));code=3
    except Exception as failure:record.update(status='FAIL2-setup-integrity-or-execution',error=str(failure));code=2
    if code:write_json(report/'FAILURE.json',record)
    print(json.dumps(dict(status=record['status'],profile=args.profile,exit_code=code,error=record.get('error'),report=str(report),required_action=ACTION),indent=2));return code


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--profile',required=True);p.add_argument('--home',required=True,type=Path);p.add_argument('--report',required=True,type=Path);p.add_argument('--cache',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');args=p.parse_args()
    try:return install(args)
    except Exception as failure:print('FASTP SETUP FAIL2: '+str(failure)+'\nRequired action: '+ACTION,file=sys.stderr);return 2


if __name__=='__main__':raise SystemExit(main())
