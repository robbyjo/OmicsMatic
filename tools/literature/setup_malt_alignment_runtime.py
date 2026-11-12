# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a pinned Linux x86_64 MALT0.4.1/Java8 alignment runtime into a NEW directory."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,platform,subprocess,sys,tarfile,traceback,urllib.error,urllib.request
MALT_URL='https://conda.anaconda.org/bioconda/noarch/malt-0.41-1.tar.bz2'
MALT_SHA='9757ceb54dbf9b193e05b0c43d5faa19b9457ab070f1e568120ce5f695debc51'
JAVA_URL='https://conda.anaconda.org/conda-forge/linux-64/openjdk-8.0.144-zulu8.23.0.3_2.tar.bz2'
JAVA_SHA='fc842165b76a859c2a4800eb2945279fee55ec85af91fdde326f823c12fecdf5'
JARS={'opt/malt-0.41/jars/MALT.jar':('MALT.jar',4641119,'9106de3134a8f9d96bcd35986ee7a61738a1b51fca1683bb32bb108e82e92cf4'),'opt/malt-0.41/jars/data.jar':('data.jar',24731618,'718a23ad8defbe34a35c9b0967c5fa616900adba34e8b961fb5531cd2382d2e3')}
ACTION='Required action: use Linux x86_64 (or an allowed WSL Linux environment), allow the exact public MALT0.4.1/OpenJDK8.0.144 downloads or supply both complete pinned archives with --malt-archive and --java-archive. Choose a NEW --out directory. If download/install/execution is unavailable or forbidden, skip the dependent MALT alignment; retain all diagnostics and do not substitute a different runtime or a negative biological finding.'

def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as stream:
  for raw in iter(lambda:stream.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def member_path(root,name):
 path=PurePosixPath(name)
 if path.is_absolute() or not path.parts or any(x in ['.','..'] for x in path.parts) or '\\' in name or ':' in name:raise ValueError('Unsafe original package member '+name)
 target=root.joinpath(*path.parts)
 if not target.resolve().is_relative_to(root):raise ValueError('Package member escapes NEW runtime')
 return target

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--malt-archive',type=Path);p.add_argument('--java-archive',type=Path);a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_MALT_ALIGNMENT_RUNTIME_SETUP',alignment_qualified=False,scientific_analysis_executed=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise OSError('Require canonical unlinked runtime output path')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise OSError('Pinned original Java8 alignment provider requires Linux x86_64')
  archives={}
  for name,provided,url,digest,maximum in [('malt',a.malt_archive,MALT_URL,MALT_SHA,134217728),('openjdk8',a.java_archive,JAVA_URL,JAVA_SHA,268435456)]:
   source=provided.resolve() if provided else out/(name+'.tar.bz2')
   if not provided:
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-MALT-pinned-alignment-runtime'}),timeout=90) as response,source.open('xb') as stream:
     if response.status!=200:raise OSError('Runtime package HTTP'+str(response.status))
     total=0
     for raw in iter(lambda:response.read(1048576),b''):
      total+=len(raw)
      if total>maximum:raise ValueError('Complete runtime download exceeds byte capacity')
      stream.write(raw)
   if not source.is_file():raise FileNotFoundError('Complete pinned runtime archive missing: '+str(source))
   identity=pin(source)
   if identity['bytes']>maximum or identity['sha256']!=digest:raise ValueError('Complete '+name+' package byte/hash mismatch')
   archives[name]=(source,identity,url)
  extracted={};seen=set();count=0;expanded=0;links=[]
  with tarfile.open(archives['openjdk8'][0],'r|bz2') as tar:
   for member in tar:
    count+=1;expanded+=member.size
    if count>10000 or member.size<0 or expanded>536870912:raise ValueError('Original Java package member/byte capacity exceeded')
    if not member.name.startswith('jre/'):continue
    if member.name in seen:raise ValueError('Duplicate original Java runtime member')
    seen.add(member.name);target=member_path(out,member.name)
    if member.isdir():target.mkdir(parents=True,exist_ok=True);continue
    target.parent.mkdir(parents=True,exist_ok=True)
    if member.issym():
     link=PurePosixPath(member.linkname)
     if link.is_absolute() or '\\' in member.linkname or ':' in member.linkname:raise ValueError('Absolute original Java link refused')
     resolved=(target.parent/member.linkname).resolve()
     if not resolved.is_relative_to(out/'jre'):raise ValueError('Java runtime link escapes JRE')
     links.append((target,member.linkname));continue
    if not member.isfile():raise ValueError('Unsupported original Java member type')
    source=tar.extractfile(member)
    if source is None:raise ValueError('Incomplete Java member')
    with source,target.open('xb') as stream:
     for raw in iter(lambda:source.read(1048576),b''):stream.write(raw)
    if target.stat().st_size!=member.size:raise ValueError('Truncated Java runtime member')
    target.chmod(0o755 if member.mode&0o111 else 0o644);extracted[member.name]=dict(kind='file',**pin(target),mode=oct(target.stat().st_mode&0o777))
  for target,link in links:
   if not (target.parent/link).is_file():raise ValueError('Original Java link target missing')
   target.symlink_to(link);extracted[target.relative_to(out).as_posix()]=dict(kind='symlink',target=link)
  if len(extracted)!=140:raise ValueError('Complete original JRE inventory differs: '+str(len(extracted)))
  with tarfile.open(archives['malt'][0],'r|bz2') as tar:
   seen_jars=set();count=0;expanded=0
   for member in tar:
    count+=1;expanded+=member.size
    if count>10000 or member.size<0 or expanded>268435456:raise ValueError('MALT package member/byte capacity exceeded')
    if member.name not in JARS:continue
    name,size,digest=JARS[member.name]
    if name in seen_jars or not member.isfile() or member.size!=size:raise ValueError('Unexpected original MALT member')
    source=tar.extractfile(member);target=out/name
    if source is None:raise ValueError('Incomplete original MALT member')
    with source,target.open('xb') as stream:
     for raw in iter(lambda:source.read(1048576),b''):stream.write(raw)
    if pin(target)!=dict(bytes=size,sha256=digest):raise ValueError('Original MALT jar differs')
    target.chmod(0o644);seen_jars.add(name);extracted[name]=dict(kind='file',**pin(target),mode='0o644')
   if len(seen_jars)!=2:raise ValueError('Incomplete original MALT program/data')
  for source,identity,url in archives.values():
   if pin(source)!=identity:raise ValueError('Source runtime archive changed during setup')
  java=out/'jre/bin/java';env=dict(PATH='/usr/bin:/bin',LC_ALL='C',LANG='C',TZ='UTC',HOME=str(out),TMPDIR=str(out))
  probes=[('java-version',[str(java),'-version'],0),('malt-help',[str(java),'-Djava.awt.headless=true','-cp',str(out/'MALT.jar')+':'+str(out/'data.jar'),'malt.MaltRun','--help'],0)];results=[]
  for label,command,expected in probes:
   run=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=90);(out/(label+'.stdout')).write_bytes(run.stdout);(out/(label+'.stderr')).write_bytes(run.stderr);results.append(dict(command=command,exit_code=run.returncode))
   text=run.stdout+run.stderr
   if run.returncode!=expected or label=='java-version' and b'1.8.0_144' not in text or label=='malt-help' and (b'MALT (version 0.4.1' not in text or b'--mode' not in text):raise OSError('Original '+label+' prerequisite probe failed; inspect complete diagnostics')
  state.update(status='READY_PINNED_MALT041_LINUX_JAVA8_ALIGNMENT_RUNTIME',alignment_qualified=False,packages={name:dict(pin=identity,url=url) for name,(source,identity,url) in archives.items()},licenses=dict(MALT='GPLv3',OpenJDK='GPLv2; retain bundled LICENSE and THIRD_PARTY_README'),members=extracted,probes=results,warnings=['This installs the exact legacy upstream runtime and checks version/help only. A successful installation is not alignment/scientific qualification. Original MALT help exits0; full diagnostics are retained.','No global Java, Conda environment, released executable or private workflow image is modified. Use a separately qualified typed alignment profile and complete declared database.']);save(out/'INSTALLATION.json',state);(out/'WARNINGS.md').write_text('# PINNED MALT ALIGNMENT PREREQUISITES ONLY\n\n'+'\n\n'.join(state['warnings'])+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],str(out),'\n'+ACTION,flush=True);return 0
 except Exception as error:
  skip=isinstance(error,(OSError,urllib.error.URLError));state.update(status='SKIP3_MALT_ALIGNMENT_RUNTIME_UNAVAILABLE' if skip else 'FAIL2_MALT_ALIGNMENT_RUNTIME_SETUP',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return 3 if skip else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable exact runtime checks')
 raise SystemExit(main())
