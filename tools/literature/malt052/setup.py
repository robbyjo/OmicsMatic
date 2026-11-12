#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install pinned MALT0.5.2 locally, or its three-jar read-only provider; execute no package scripts."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,platform,signal,subprocess,sys,tarfile,time,traceback,urllib.request
URL='https://conda.anaconda.org/bioconda/noarch/malt-0.5.2-hdfd78af_1.tar.bz2'
PACKAGE=dict(bytes=141110092,sha256='3cdf55e01405164cfa9c5f1435cbb5d66f305028cf5b9fec8a2bed66665d0dd2')
RUNTIME_SHA='0549790002c48ffb828ea4958fdf3d7e2cbf050873833672bcb7bfe771222ac2'
JARS={'malt.jar':(218319,'b2683d6c7306fdec55b8a7b634aa409197799e3a7c339e5be9b2797ac3a06928'),'megan.jar':(39305470,'b43c06b4503536ef2811380ea2589c7c6a641148bba920d72a7de846216219ec'),'jloda.jar':(1486889,'00b68ab83e81f11a0c62c9fb7c6566ee57e9d4ae2772f587208920c878c2c475')}
ACTION='Required action: allow this exact public MALT0.5.2 package download, or supply its complete pinned archive with --archive. Original alignment requires permitted Linux x86_64 and util-linux prlimit/setsid; use a native Linux filesystem to preserve original file modes. Windows/macOS may install --reader-only for Java21/25 saved replay. Choose a NEW --out directory. If download/install/execution is unavailable or forbidden, skip the dependent analysis. Retain diagnostics and do not substitute another version or interpret a skip as a biological finding.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def inventory(out):
 values={}
 for root in [out/'jre',out/'jars']:
  if root.exists():
   for p in sorted(root.rglob('*')):
    n=p.relative_to(out).as_posix()
    if p.is_symlink():values[n]=dict(type='symlink',target=os.readlink(p))
    elif p.is_file():values[n]=dict(type='file',mode=oct(p.stat().st_mode&0o777),**pin(p))
 p=out/'License.txt';values[p.name]=dict(type='file',mode=oct(p.stat().st_mode&0o777),**pin(p));return values
def inventory_sha(values):return hashlib.sha256(''.join(n+'\tsymlink\t'+p['target']+'\n' if p['type']=='symlink' else n+'\tfile\t'+str(p['bytes'])+'\t'+p['sha256']+'\t'+p['mode']+'\n' for n,p in sorted(values.items())).encode()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--archive',type=Path);p.add_argument('--reader-only',action='store_true');a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_MALT052_SETUP',scientific_analysis_executed=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise OSError('Canonical unlinked NEW output required')
  out.mkdir(parents=True,exist_ok=False,mode=0o700);owned=True
  if not a.reader_only and (sys.platform!='linux' or platform.machine() not in ['amd64','x86_64']):raise OSError('Original bundled Java14 fitting requires Linux x86_64; use --reader-only for saved replay elsewhere')
  archive=a.archive.resolve() if a.archive else out/'malt-0.5.2.tar.bz2'
  if not a.archive:
   with urllib.request.urlopen(urllib.request.Request(URL,headers={'User-Agent':'OmicsMatic-MALT052-permitted-provider'}),timeout=90) as response,archive.open('xb') as target:
    total=0
    for block in iter(lambda:response.read(1048576),b''):
     total+=len(block)
     if total>PACKAGE['bytes']:raise ValueError('Complete provider exceeds pinned size')
     target.write(block)
  if pin(archive)!=PACKAGE:raise ValueError('Complete provider size/SHA256 differs')
  seen=set();links=[];expanded=0;members=0
  with tarfile.open(archive,'r|bz2') as t:
   for m in t:
    members+=1
    if members>10000:raise ValueError('Package member capacity exceeded')
    selected=(m.name=='opt/malt-0.5.2/License.txt' or m.name.startswith(('opt/malt-0.5.2/jre/','opt/malt-0.5.2/jars/'))) if not a.reader_only else m.name in ['opt/malt-0.5.2/License.txt',*['opt/malt-0.5.2/jars/'+n for n in JARS]]
    if not selected:continue
    q=PurePosixPath(m.name)
    if q.is_absolute() or '..' in q.parts or '\\' in m.name or ':' in m.name:raise ValueError('Unsafe provider member')
    n=PurePosixPath(*q.parts[2:]);target=out.joinpath(*n.parts)
    if n.as_posix() in seen or not target.resolve().is_relative_to(out):raise ValueError('Duplicate/escaping provider member')
    seen.add(n.as_posix())
    if m.isdir():target.mkdir(parents=True,exist_ok=True);continue
    target.parent.mkdir(parents=True,exist_ok=True)
    if m.issym():
     link=PurePosixPath(m.linkname)
     if link.is_absolute() or '\\' in m.linkname or ':' in m.linkname or not (target.parent/m.linkname).resolve().is_relative_to(out/'jre'):raise ValueError('Provider link escapes original JRE')
     links.append((target,m.linkname));continue
    if not m.isfile() or m.size<0 or m.size>268435456 or m.mode!=m.mode&0o777 or m.mode&0o002:raise ValueError('Unsupported provider file/type/mode')
    expanded+=m.size
    if expanded>1073741824:raise ValueError('Complete selected provider exceeds capacity')
    with t.extractfile(m) as source,target.open('xb') as dest:
     shutil_count=0
     for block in iter(lambda:source.read(1048576),b''):dest.write(block);shutil_count+=len(block)
    if shutil_count!=m.size:raise ValueError('Truncated provider member')
    target.chmod(m.mode)
  for target,link in links:target.symlink_to(link)
  if any(not p.exists() or not p.resolve().is_relative_to(out/'jre') for p,_ in links):raise ValueError('Incomplete/escaping original JRE link')
  for n,(size,digest) in JARS.items():
   if pin(out/'jars'/n)!=dict(bytes=size,sha256=digest):raise ValueError('Pinned reader jar differs: '+n)
  before=inventory(out);results=[]
  if a.reader_only:
   if set(before)!={'License.txt',*['jars/'+n for n in JARS]}:raise ValueError('Reader-only inventory differs')
  else:
   if len(before)!=370 or inventory_sha(before)!=RUNTIME_SHA:raise ValueError('Complete original program/Java14 file bytes, modes or links differ')
   for tool in ['/usr/bin/prlimit','/usr/bin/setsid']:
    if not os.access(tool,os.X_OK):raise OSError('Required permitted util-linux tool unavailable: '+tool)
   logs=out/'qualification';logs.mkdir();home=logs/'home';home.mkdir();env=dict(PATH=str(out/'jre/bin')+':/usr/bin:/bin',JAVA_HOME=str(out/'jre'),HOME=str(home),LC_ALL='C',LANG='C',TZ='UTC')
   for name,args in [('version',['-version']),('blastn-help',['-cp',str(out/'jars/*'),'malt.MaltRun','--mode','BlastN','--help']),('build-help',['-cp',str(out/'jars/*'),'malt.MaltBuild','--help'])]:
    cmd=['/usr/bin/prlimit','--as=34359738368','--cpu=60','--fsize=16777216','--nofile=1024','--',str(out/'jre/bin/java'),'-Xmx1g','-Djava.awt.headless=true','-Duser.home='+str(home),*args];start=time.time()
    with (logs/(name+'.stdout')).open('xb') as so,(logs/(name+'.stderr')).open('xb') as se:
     child=subprocess.Popen(cmd,cwd=logs,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True);save(logs/(name+'.live.json'),dict(pid=child.pid,pgid=child.pid,command=cmd))
     try:code=child.wait(timeout=30)
     except subprocess.TimeoutExpired:
      os.killpg(child.pid,signal.SIGTERM)
      try:child.wait(timeout=5)
      except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=10)
      raise OSError('Original help probe timed out; provider is unqualified')
    try:os.killpg(child.pid,0);live=True
    except ProcessLookupError:live=False
    result=dict(command=cmd,exit_code=code,owned_process_group_live_after=live,elapsed_seconds=time.time()-start);save(logs/(name+'.process.json'),result);results.append(result)
    if live or code!=0:raise OSError('Original provider help failed; retain full diagnostics')
  if pin(archive)!=PACKAGE or inventory(out)!=before:raise ValueError('Provider package or extracted files changed')
  save(out/'RUNTIME_INVENTORY.json',before);state.update(status='READY_MALT052_READER_ONLY_PROVIDER_BYTES' if a.reader_only else 'READY_PINNED_MALT052_JAVA14_PROVIDER_HELP_ONLY',source_url=URL,package=PACKAGE,reader_only=a.reader_only,selected_members=len(before),runtime_inventory_sha256=None if a.reader_only else RUNTIME_SHA,help_processes=results,installation_scripts_executed=False,scientific_provider_qualified=False);save(out/'SETUP.json',state);(out/'WARNINGS.md').write_text('# MALT0.5.2 prerequisite provider only\n\nREADY confirms bytes and optional help; it does not establish analysis equivalence. Original0.5.2 accession-map indexing remains unqualified. A reader-only provider cannot run original alignment.\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(state['status'],'\n'+ACTION,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state.update(status='SKIP3_MALT052_PROVIDER_UNAVAILABLE' if code==3 else 'FAIL2_MALT052_PROVIDER_INTEGRITY',cause=repr(e),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(e)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
