#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install an exact public R4.0.5/HOPS provider in a NEW private Linux prefix."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,platform,shutil,signal,subprocess,sys,tarfile,time,traceback,urllib.error,urllib.parse,urllib.request
HERE=Path(__file__).resolve().parent
ACTION='Required action: use a permitted Linux x86_64 host, a NEW native-filesystem output prefix, and the exact locked public packages. Allow this isolated install/download, or supply the complete pinned package cache. If installation/download/execution is unavailable or forbidden, skip dependent HOPS analysis. Preserve every failed prefix and diagnostic; never substitute versions or overwrite a prior environment. Provider probes alone admit no scientific analysis. The eager image and protected GATK program are not needed or bundled.'
SELECTED={'share/hops-0.35-1/hops0.35.jar':('hops0.35.jar',63917101,'a44e0894fc2efa768034c618ae7574526eda74a5b2af3b42425f49a3bdb2ca4a'),'share/hops-0.35-1/MaltExtract1.7.jar':('MaltExtract1.7.jar',10770756,'8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca'),'bin/postprocessing.AMPS.r':('postprocessing.AMPS.r',15666,'7912dfcede0084134d1d677368f851a4d55dadeb0b7eaa591fcfe2e9c6cbdf62'),'share/hops-0.35-1/LICENSE':('LICENSE-HOPS.txt',35128,'3743f7a4ab5132f7dbeb88e68097657ab8374e74b46b6402efbe80ccfb1b6488')}

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);n+=len(block)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def install(command,env,out,timeout=1200):
 """Bound this install's own process group; never signal unrelated processes."""
 started=time.time();timed_out=False
 with (out/'install.stdout').open('xb') as so,(out/'install.stderr').open('xb') as se:
  child=subprocess.Popen(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
  save(out/'INSTALL_LIVE.json',dict(command=command,pid=child.pid,process_group=child.pid,started_unix=started))
  try:code=child.wait(timeout=timeout)
  except subprocess.TimeoutExpired:
   timed_out=True
   try:os.killpg(child.pid,signal.SIGTERM)
   except ProcessLookupError:pass
   try:code=child.wait(timeout=5)
   except subprocess.TimeoutExpired:
    try:os.killpg(child.pid,signal.SIGKILL)
    except ProcessLookupError:pass
    code=child.wait(timeout=5)
   # A cooperative parent may exit before its group children; terminate the
   # same owned group as well. No other PID/group is enumerated or signalled.
   try:os.killpg(child.pid,signal.SIGKILL)
   except ProcessLookupError:pass
 save(out/'INSTALL_PROCESS.json',dict(command=command,exit_code=code,elapsed_seconds=time.time()-started,timed_out=timed_out,owned_process_group=child.pid))
 if timed_out:raise OSError('Pinned installation exceeded its wall-time capacity; owned installer group stopped; retain this prefix and retry a NEW one only after resolving the cause')
 if code:raise OSError('Exact offline R dependency installation failed; inspect install.stdout/stderr')

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--package-cache',type=Path);p.add_argument('--hops-archive',type=Path);a=p.parse_args();out=a.out.absolute();owned=False;state=dict(status='FAIL2_HOPS035_PUBLIC_PROVIDER_SETUP',analysis_qualified=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW prefix required')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64'] or not a.allow_install:raise OSError('Explicit permitted Linux x86_64 installation required')
  out.chmod(0o700);lock=json.loads((HERE/'r-runtime-lock.json').read_bytes());assert lock['schema']=='HOPS035_R405_PUBLIC_RUNTIME_LOCK_V1' and len(lock['packages'])==102
  if shutil.disk_usage(out).free<6*1024**3:raise OSError('Reserve at least6GiB free for complete pinned packages, compiler dependencies and runtime')
  cache=out/'packages';cache.mkdir();records=[]
  def acquire(url,filename,expected):
   target=cache/filename
   if a.package_cache and (a.package_cache/filename).is_file():shutil.copyfile(a.package_cache/filename,target)
   else:
    if not a.allow_download:raise OSError('Exact package unavailable without permitted download: '+filename)
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-HOPS035-public-runtime'})
    with urllib.request.urlopen(request,timeout=60) as source,target.open('xb') as dest:
     size=0
     for block in iter(lambda:source.read(1048576),b''):
      size+=len(block)
      if size>expected['bytes']:raise ValueError('Package exceeds locked complete size: '+filename)
      dest.write(block)
   actual=pin(target)
   if actual!=expected:raise ValueError('Exact public package bytes/hash differ: '+filename)
   return target
  def package(row):
   parsed=urllib.parse.urlsplit(row['url']);filename=row['fn']
   if parsed.scheme!='https' or parsed.netloc!='conda.anaconda.org' or not parsed.path.startswith('/conda-forge/') or Path(filename).name!=filename or parsed.path.rsplit('/',1)[1]!=filename:raise ValueError('Unexpected public package source')
   target=acquire(row['url'],filename,dict(bytes=row['size'],sha256=row['sha256']));return dict(name=row['name'],file=filename,pin=pin(target),url=row['url'])
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
   for i,row in enumerate(pool.map(package,lock['packages']),1):records.append(row);print('Verified public R package',i,'of102',row['name'],flush=True)
  m=lock['micromamba'];archive=acquire(m['url'],Path(urllib.parse.urlsplit(m['url']).path).name,dict(bytes=m['bytes'],sha256=m['sha256']));bootstrap=out/'bootstrap';bootstrap.mkdir()
  with tarfile.open(archive) as tar:
   member=tar.getmember('bin/micromamba');assert member.isfile() and member.size<67108864
   with tar.extractfile(member) as src,(bootstrap/'micromamba').open('xb') as dest:shutil.copyfileobj(src,dest,1048576)
   (bootstrap/'micromamba').chmod(0o755)
   for member in tar:
    if member.isfile() and member.name.startswith('info/licenses/'):
     relative=Path(member.name)
     if '..' in relative.parts:raise ValueError('Unsafe bootstrap license path')
     target=bootstrap/relative;target.parent.mkdir(parents=True,exist_ok=True)
     with tar.extractfile(member) as src,target.open('xb') as dest:shutil.copyfileobj(src,dest,1048576)
  if a.hops_archive:
   hops=a.hops_archive.resolve()
   if pin(hops)!=dict(bytes=lock['hops']['bytes'],sha256=lock['hops']['sha256']):raise ValueError('Pinned HOPS archive differs')
  else:hops=acquire(lock['hops']['url'],'hops-0.35-hdfd78af_1.tar.bz2',dict(bytes=lock['hops']['bytes'],sha256=lock['hops']['sha256']))
  program=out/'program';program.mkdir()
  with tarfile.open(hops) as tar:
   for source,(name,size,sha) in SELECTED.items():
    member=tar.getmember(source);assert member.isfile() and member.size==size
    with tar.extractfile(member) as src,(program/name).open('xb') as dest:shutil.copyfileobj(src,dest,1048576)
    if pin(program/name)!=dict(bytes=size,sha256=sha):raise ValueError('Pinned HOPS program member differs')
  # Explicit local URLs avoid solving or silently selecting a newer dependency.
  specs=out/'explicit.local.txt';specs.write_text('@EXPLICIT\n'+''.join((cache/r['fn']).as_uri()+'#'+r['md5']+'\n' for r in lock['packages']),encoding='utf8',newline='\n')
  own_home=out/'home';own_home.mkdir();env=dict(PATH='/usr/bin:/bin',HOME=str(own_home),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',MAMBA_ROOT_PREFIX=str(out/'mamba'),MAMBA_ALWAYS_COPY='true')
  command=[str(bootstrap/'micromamba'),'--no-rc','create','--offline','--yes','--always-copy','--root-prefix',str(out/'mamba'),'--prefix',str(out/'r'),'--file',str(specs)]
  install(command,env,out)
  probe='p<-c("getopt","gridBase","gridExtra","jsonlite","gtable");cat(jsonlite::toJSON(list(R=as.character(getRversion()),packages=setNames(lapply(p,function(x)as.character(packageVersion(x))),p)),auto_unbox=TRUE))'
  command=[str(out/'r/bin/Rscript'),'--vanilla','-e',probe];r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(out/'probe.stdout').write_bytes(r.stdout);(out/'probe.stderr').write_bytes(r.stderr);save(out/'PROBE_PROCESS.json',dict(command=command,exit_code=r.returncode))
  if r.returncode:raise OSError('Installed R provider failed its complete package/version probe')
  observed=json.loads(r.stdout)
  if observed!=dict(R='4.0.5',packages=lock['expected_packages']):raise ValueError('Installed exact R/package versions differ')
  members={}
  for base in [out/'r',program]:
   for path in sorted(base.rglob('*')):
    name=path.relative_to(out).as_posix()
    if path.is_symlink():members[name]=dict(kind='symlink',target=os.readlink(path))
    elif path.is_file():members[name]=dict(kind='file',**pin(path),mode=oct(path.stat().st_mode&0o777))
  save(out/'RUNTIME_READY.json',dict(status='READY_PINNED_PUBLIC_R405_HOPS035_PROVIDER_ANALYSIS_NOT_YET_QUALIFIED',lock=pin(HERE/'r-runtime-lock.json'),packages=records,observed=observed,members=members,inventory_sha256=hashlib.sha256(json.dumps(members,sort_keys=True,separators=(',',':')).encode()).hexdigest(),hops_distribution='0.35-hdfd78af_1',launcher_reports_version='0.33',analysis_qualified=False,restricted_eager_image_required=False,protected_gatk_bundled=False));(out/'WARNINGS.md').write_text('# Provider setup only\n\nHOPS distribution0.35 contains a launcher reporting0.33. Runtime setup/package probes do not qualify scientific analysis.\n\nThe pinned gcc_impl_linux-64 9.4.0 h03d3576_15 package includes the relative compiler compatibility link x86_64-conda-linux-gnu/lib/libstdc++.so.6.0.28 -> ../../lib/libstdc++.so.6.0.28. It dangles with the newer locked libstdcxx-ng package. The literal link is preserved, not repaired. This provider is qualified for R postprocessing only; working GCC9 compilation is not claimed.\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print('READY_PINNED_PUBLIC_R405_HOPS035_PROVIDER_ANALYSIS_NOT_YET_QUALIFIED',flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;state.update(status='SKIP3_PUBLIC_HOPS_R_PROVIDER_UNAVAILABLE' if code==3 else 'FAIL2_PUBLIC_HOPS_PROVIDER_SETUP',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable pinned dependency checks')
 raise SystemExit(main())
