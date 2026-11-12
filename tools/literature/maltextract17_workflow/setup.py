#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install pinned original MaltExtract1.7 and its complete Linux Java8 runtime."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,platform,subprocess,sys,tarfile,traceback,urllib.error,urllib.request
HOPS_URL='https://conda.anaconda.org/bioconda/noarch/hops-0.35-hdfd78af_1.tar.bz2'
HOPS_SHA='79105a2c3a447d31dc1b00ff3c74505bfe56854cf302346b6347a0eb1fee2e2d'
JAVA_URL='https://conda.anaconda.org/conda-forge/linux-64/openjdk-8.0.144-zulu8.23.0.3_2.tar.bz2'
JAVA_SHA='fc842165b76a859c2a4800eb2945279fee55ec85af91fdde326f823c12fecdf5'
SELECTED={'share/hops-0.35-1/MaltExtract1.7.jar':('MaltExtract1.7.jar',10770756,'8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca'),'share/hops-0.35-1/LICENSE':('LICENSE-HOPS.txt',35128,'3743f7a4ab5132f7dbeb88e68097657ab8374e74b46b6402efbe80ccfb1b6488')}
ACTION='Required action: use a permitted Linux x86_64 environment with a NEW runtime directory on its native filesystem, preserving POSIX modes and links. Allow the exact public HOPS0.35/OpenJDK8.0.144 downloads, or provide complete pinned archives with --hops-archive and --java-archive. If download/install/execution is unavailable or forbidden, skip dependent original MaltExtract analysis. Retain diagnostics; do not substitute a runtime or a biological absence claim. Setup/version/help do not qualify an analysis.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def destination(root,name):
 p=PurePosixPath(name)
 if p.is_absolute() or not p.parts or '\\' in name or ':' in name or any(t in ['.','..'] for t in p.parts):raise ValueError('Unsafe package member')
 result=root.joinpath(*p.parts)
 if not result.resolve().is_relative_to(root):raise ValueError('Package member escapes owned runtime')
 return result
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--hops-archive',type=Path);p.add_argument('--java-archive',type=Path);a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_ORIGINAL_MALTEXTRACT_RUNTIME_SETUP',analysis_qualified=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW provider required')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise OSError('Pinned original execution provider requires Linux x86_64')
  out.chmod(0o700);packages={}
  for name,provided,url,sha,maximum in [('hops',a.hops_archive,HOPS_URL,HOPS_SHA,100000000),('openjdk8',a.java_archive,JAVA_URL,JAVA_SHA,268435456)]:
   source=provided.resolve() if provided else out/(name+'.tar.bz2')
   if not provided:
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-pinned-MaltExtract-original-runtime'}),timeout=90) as response,source.open('xb') as target:
     if response.status!=200:raise OSError('Required package HTTP'+str(response.status))
     total=0
     for data in iter(lambda:response.read(1048576),b''):
      total+=len(data)
      if total>maximum:raise ValueError('Complete download exceeds capacity')
      target.write(data)
   if not source.is_file():raise FileNotFoundError('Complete required package missing: '+str(source))
   value=pin(source)
   if value['bytes']>maximum or value['sha256']!=sha:raise ValueError('Complete pinned package differs: '+name)
   packages[name]=(source,value,url)
  files={};seen=set();links=[];expanded=0;count=0
  with tarfile.open(packages['openjdk8'][0],'r|bz2') as tar:
   for member in tar:
    count+=1;expanded+=member.size
    if count>10000 or member.size<0 or expanded>536870912:raise ValueError('Complete Java package exceeds inventory/expansion capacity')
    if not member.name.startswith('jre/'):continue
    if member.name in seen:raise ValueError('Repeated Java package member')
    seen.add(member.name);target=destination(out,member.name)
    if member.isdir():target.mkdir(parents=True,exist_ok=True);continue
    target.parent.mkdir(parents=True,exist_ok=True)
    if member.issym():
     link=PurePosixPath(member.linkname)
     if link.is_absolute() or '\\' in member.linkname or ':' in member.linkname or not (target.parent/member.linkname).resolve().is_relative_to(out/'jre'):raise ValueError('Unsafe Java runtime link')
     links.append((target,member.linkname));continue
    if not member.isfile():raise ValueError('Unsupported Java package member')
    source=tar.extractfile(member)
    if source is None:raise ValueError('Incomplete Java member')
    with source,target.open('xb') as dest:
     for data in iter(lambda:source.read(1048576),b''):dest.write(data)
    if target.stat().st_size!=member.size:raise ValueError('Truncated Java member')
    target.chmod(0o755 if member.mode&0o111 else 0o644);files[member.name]=dict(kind='file',**pin(target),mode=oct(target.stat().st_mode&0o777))
  for target,link in links:
   if not (target.parent/link).is_file():raise ValueError('Java link target unavailable')
   target.symlink_to(link);files[target.relative_to(out).as_posix()]=dict(kind='symlink',target=link)
  if len(files)!=140:raise ValueError('Complete original JRE inventory differs')
  seen=set();expanded=count=0
  with tarfile.open(packages['hops'][0],'r|bz2') as tar:
   for member in tar:
    count+=1;expanded+=member.size
    if count>10000 or member.size<0 or expanded>134217728:raise ValueError('Complete HOPS package exceeds capacity')
    if member.name not in SELECTED:continue
    name,size,sha=SELECTED[member.name]
    if name in seen or not member.isfile() or member.size!=size:raise ValueError('Incomplete/repeated selected HOPS member')
    source=tar.extractfile(member)
    if source is None:raise ValueError('Selected HOPS member missing')
    target=out/name
    with source,target.open('xb') as dest:
     for data in iter(lambda:source.read(1048576),b''):dest.write(data)
    if pin(target)!=dict(bytes=size,sha256=sha):raise ValueError('Pinned original HOPS member differs')
    target.chmod(0o644);files[name]=dict(kind='file',**pin(target),mode=oct(target.stat().st_mode&0o777));seen.add(name)
  if seen!={value[0] for value in SELECTED.values()}:raise ValueError('Original program/license missing')
  lines=[]
  for name,value in sorted(files.items()):
   lines.append(name+'\tsymlink\t'+value['target']+'\n' if value['kind']=='symlink' else name+'\tfile\t'+str(value['bytes'])+'\t'+value['sha256']+'\t'+value['mode']+'\n')
  digest=hashlib.sha256(''.join(lines).encode()).hexdigest();env=dict(PATH='/usr/bin:/bin',LC_ALL='C.UTF-8',LANG='C.UTF-8',TZ='UTC',HOME=str(out),TMPDIR=str(out));probes=[]
  for label,command in [('java-version',[str(out/'jre/bin/java'),'-version']),('program-help',[str(out/'jre/bin/java'),'-Xmx512m','-XX:ParallelGCThreads=1','-Djava.awt.headless=true','-jar',str(out/'MaltExtract1.7.jar'),'-h'])]:
   r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);probes.append(dict(command=command,exit_code=r.returncode));text=r.stdout+r.stderr
   if r.returncode or label=='java-version' and b'1.8.0_144' not in text or label=='program-help' and b'MaltExtract version1.7' not in text:raise OSError('Original prerequisite probe failed; retain complete diagnostics')
  if any(pin(source)!=value for source,value,url in packages.values()):raise ValueError('Source package changed during setup')
  state.update(status='READY_PINNED_ORIGINAL_MALTEXTRACT17_LINUX_JAVA8_RUNTIME',members=files,inventory_sha256=digest,packages={name:dict(pin=value,url=url) for name,(source,value,url) in packages.items()},probes=probes,licenses=dict(HOPS='GPL >=3; retain LICENSE-HOPS.txt',OpenJDK='Retain unchanged jre/LICENSE and jre/THIRD_PARTY_README'),analysis_qualified=False)
  save(out/'RUNTIME_READY.json',state);(out/'WARNINGS.md').write_text('# Original execution prerequisite only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],digest,flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(OSError,urllib.error.URLError)) and not isinstance(error,FileExistsError) else 2;state.update(status='SKIP3_ORIGINAL_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_RUNTIME_SETUP',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable exact provider checks')
 raise SystemExit(main())
