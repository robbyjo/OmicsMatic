#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install/verify exact nf-core source archives only; never execute a pipeline."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,stat,sys,tarfile,urllib.error,urllib.request,uuid

LOCK_SHA256='41ead51194221d7515e21b2bae7c1ae647e1824b0167e28cd36b95348722fdde'
MARKER='.omicsmatic-nfcore-source.json'
STATUS='VERIFIED_NFCORE_SOURCE_ONLY_NO_ANALYSIS'
ACTION=('Pipeline analysis is unavailable in this source-only command. Qualify the exact Nextflow engine, '
        'process software/containers, remote configurations, references, sample sheet and complete outputs '
        'in the separate workflow adapter. Do not replace an unreported paper edition with a pinned edition. '
        'Preserve failed/partial homes and diagnostics; correct the named cause and use NEW paths.')
class Unavailable(Exception):pass
def require(value,message):
    if not value:raise ValueError(message)
def encoded(v):return(json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode()
def put(path,v):
    with path.open('xb')as f:f.write(encoded(v))
def pin(path):
    before=path.stat(follow_symlinks=False)
    require(stat.S_ISREG(before.st_mode),'Require regular source file: '+str(path))
    with path.open('rb')as f:result=dict(bytes=before.st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
    after=path.stat(follow_symlinks=False)
    require((before.st_size,before.st_mtime_ns,before.st_ino)==(after.st_size,after.st_mtime_ns,after.st_ino),'Source changed while hashing: '+str(path))
    return result
def physical(path,existing=False):
    path=path.absolute()
    require(path.resolve()==path,'Use a normalized physical path without aliases: '+str(path))
    require(path.parent.is_dir(),'Missing canonical parent directory: '+str(path.parent))
    if existing:require(path.exists(),'Missing required path: '+str(path))
    return path
def lock(path):
    raw=path.read_bytes();require(hashlib.sha256(raw).hexdigest()==LOCK_SHA256,'Changed pinned source lock; restore the exact exported helper/lock pair')
    value=json.loads(raw);require(value['schema_version']==1 and value['status']=='PINNED_ORIGINAL_NFCORE_SOURCES_ONLY'and len(value['profiles'])==7,'Changed source-lock scope')
    return value
def member(name):
    path=PurePosixPath(name)
    require(bool(name)and not path.is_absolute()and path.as_posix()==name and '..'not in path.parts and '.'not in path.parts and not any(c in name for c in '\\:\0'),'Unsafe source member: '+name)
    return path
def expected_dirs(files):
    result=set()
    for name in files:
        for parent in member(name).parents:
            if parent.parts:result.add(parent.as_posix())
    return result
def check_source(home,profile,key):
    physical(home,True)
    if not home.is_dir():raise Unavailable('Source home is not an installed directory: '+str(home))
    marker=home/MARKER
    if not marker.is_file():raise Unavailable('No complete source-only installation marker: '+str(marker))
    require(not marker.is_symlink(),'Symlink marker is refused')
    expected=dict(schema_version=1,status=STATUS,profile=key,source_lock_sha256=LOCK_SHA256,analysis_admitted=False)
    require(json.loads(marker.read_bytes())==expected,'Changed source installation identity')
    require({p.name for p in home.iterdir()}=={'source',MARKER},'Changed source-home namespace')
    source=home/'source';names=set();dirs=set();departures=[]
    require(source.is_dir()and source.resolve()==source,'Missing or aliased source directory')
    for parent,subdirs,files in os.walk(source,followlinks=False):
        for name in subdirs+files:
            path=Path(parent)/name;relative=path.relative_to(source).as_posix();kind=path.lstat().st_mode
            require(path.resolve()==path and not stat.S_ISLNK(kind),'Aliased source entry: '+relative)
            if stat.S_ISDIR(kind):dirs.add(relative)
            else:
                require(stat.S_ISREG(kind)and relative in profile['files'],'Unexpected/nonregular source entry: '+relative)
                identity=profile['files'][relative];require(pin(path)=={k:identity[k]for k in('bytes','sha256')},'Changed original source file: '+relative);names.add(relative)
                if os.name=='posix'and bool(kind&0o111)!=identity['executable']:departures.append(relative)
    require(names==set(profile['files'])and dirs==expected_dirs(profile['files']),'Incomplete or expanded original source namespace')
    return dict(status=STATUS,profile=key,pipeline=profile['pipeline'],version=profile['version'],commit=profile['commit'],source_files=len(names),source_bytes=sum(p['bytes']for p in profile['files'].values()),permission_mode_departures=departures,exact_POSIX_modes_claimed=os.name=='posix'and not departures,analysis_admitted=False,required_next_steps=profile['required_next_steps'])
def obtain(profile,key,cache,allow_download):
    filename=key.replace('/','-')+'-'+profile['archive']['sha256']+'.tar.gz';target=cache/filename
    if target.exists():
        require(target.resolve()==target and pin(target)==profile['archive'],'Changed cached archive; preserve it and supply the exact original in a NEW cache')
        return target
    if not allow_download:raise Unavailable('Missing exact cached archive '+str(target)+'; place the exact official archive there or rerun with --allow-download where permitted')
    temporary=cache/(filename+'.partial-'+uuid.uuid4().hex)
    try:
        request=urllib.request.Request(profile['source_url'],headers={'User-Agent':'OmicsMatic-pinned-source-setup'})
        with urllib.request.urlopen(request,timeout=60)as response,temporary.open('xb')as out:
            require(response.url.startswith('https://'),'Refuse non-HTTPS source redirect')
            written=0
            while chunk:=response.read(1024*1024):
                written+=len(chunk);require(written<=profile['archive']['bytes'],'Downloaded archive exceeds exact pinned size');out.write(chunk)
    except(urllib.error.URLError,TimeoutError)as e:raise Unavailable('Official source download unavailable; preserve any partial and supply an exact permitted offline archive: '+str(e))from e
    require(pin(temporary)==profile['archive'],'Downloaded archive identity differs; preserve it, never refresh the expected hash')
    require(not target.exists(),'Concurrent cache writer; preserve partials and recheck')
    temporary.rename(target);return target
def extract(archive,home,profile,key):
    require(pin(archive)==profile['archive'],'Changed pinned archive before extraction')
    # Validate the complete archive before creating a source home or extracting any file.
    entries={};all_names=set();folded=set();total=0;directories=set()
    with tarfile.open(archive,'r:gz')as source:
        for info in source:
            original=member(info.name);parts=original.parts
            require(parts[0]==profile['archive_prefix'],'Wrong original archive prefix')
            require(info.name not in all_names and info.name.casefold()not in folded,'Duplicate/case-colliding original archive member');all_names.add(info.name);folded.add(info.name.casefold())
            if len(parts)==1:require(info.isdir(),'Invalid original archive root');continue
            relative='/'.join(parts[1:]);member(relative)
            if info.isdir():directories.add(relative);continue
            require(info.isfile()and relative in profile['files'],'Unsupported/extra original archive entry: '+relative)
            identity=profile['files'][relative];require(info.size==identity['bytes']and bool(info.mode&0o111)==identity['executable'],'Changed original source metadata')
            total+=info.size;entries[relative]=info
        require(set(entries)==set(profile['files'])and directories==expected_dirs(profile['files'])and total==sum(r['bytes']for r in profile['files'].values()),'Incomplete or expanded source archive')
        home.mkdir();root=home/'source';root.mkdir()
        for relative,info in entries.items():
            target=root/relative;target.parent.mkdir(parents=True,exist_ok=True)
            with source.extractfile(info)as src,target.open('xb')as out:
                while chunk:=src.read(1024*1024):out.write(chunk)
            os.chmod(target,0o755 if profile['files'][relative]['executable']else 0o644)
            require(pin(target)=={k:profile['files'][relative][k]for k in('bytes','sha256')},'Extracted source content mismatch')
    put(home/MARKER,dict(schema_version=1,status=STATUS,profile=key,source_lock_sha256=LOCK_SHA256,analysis_admitted=False))
    return check_source(home,profile,key)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for n in('profile','home','report','cache'):p.add_argument('--'+n,required=True,type=str if n=='profile'else Path)
    p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args();report=None
    try:
        profiles=lock(Path(__file__).with_name('source-lock.json'))['profiles']
        if a.profile not in profiles:raise Unavailable('Unsupported exact source profile; choose an ID from nfcore-tools --source-profiles; do not infer an edition')
        home=physical(a.home);report_path=physical(a.report);cache=physical(a.cache)
        for x,y in((home,report_path),(home,cache),(report_path,cache)):
            require(not x.is_relative_to(y)and not y.is_relative_to(x),'Home, report and cache must be disjoint')
        require(not report_path.exists(),'Preserve the existing setup report and choose a NEW report path')
        report_path.mkdir();report=report_path
        with(report/'WARNINGS.md').open('x',encoding='utf-8')as f:f.write('# NF-CORE SOURCE SETUP: NO PIPELINE ANALYSIS\n\n'+ACTION+'\n\n'+'\n'.join(profiles[a.profile]['required_next_steps'])+'\n')
        put(report/'REQUEST.json',dict(profile=a.profile,allow_install=a.allow_install,allow_download=a.allow_download,analysis_admitted=False))
        if home.exists():result=check_source(home,profiles[a.profile],a.profile)
        else:
            if not a.allow_install:raise Unavailable('Source installation is not enabled; use --allow-install where permitted or provide an already verified source home')
            if not cache.exists():cache.mkdir()
            require(cache.is_dir(),'Source cache must be a directory')
            result=extract(obtain(profiles[a.profile],a.profile,cache,a.allow_download),home,profiles[a.profile],a.profile)
        put(report/'COMPLETED.json',result);print(json.dumps(result,indent=2));return 0
    except(Unavailable,FileNotFoundError,PermissionError)as e:
        message='SKIP3: dependent source setup and pipeline analysis skipped. '+str(e)+'. '+ACTION;code=3
    except Exception as e:message='FAIL2: no complete verified source setup or analysis. '+repr(e)+'. '+ACTION;code=2
    if report is not None:put(report/'SKIPPED.json'if code==3 else report/'FAILURE.json',dict(status='SKIP3'if code==3 else'FAIL2',message=message,analysis_admitted=False))
    print(message,file=sys.stderr);return code
if __name__=='__main__':sys.exit(main())
