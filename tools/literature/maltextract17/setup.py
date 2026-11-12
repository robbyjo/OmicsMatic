#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Obtain the exact GPL HOPS0.35 MaltExtract1.7 embedded reader in a NEW directory."""
from pathlib import Path
import argparse,hashlib,json,tarfile,traceback,urllib.error,urllib.request

URL='https://conda.anaconda.org/bioconda/noarch/hops-0.35-hdfd78af_1.tar.bz2'
ARCHIVE=dict(bytes=73005231,sha256='79105a2c3a447d31dc1b00ff3c74505bfe56854cf302346b6347a0eb1fee2e2d')
MEMBERS={
 'share/hops-0.35-1/MaltExtract1.7.jar':('MaltExtract1.7.jar',10770756,'8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca'),
 'share/hops-0.35-1/LICENSE':('LICENSE-HOPS.txt',35128,'3743f7a4ab5132f7dbeb88e68097657ab8374e74b46b6402efbe80ccfb1b6488'),
 'info/index.json':('package-index.json',403,'4fbf27458e934ca8cc97c4abd6475755418c9d5ec895c8193c2ade1ffe976a6b'),
 'info/about.json':('package-about.json',6035,'48746287b3ed523cb6dfa6b761c5fa8c4ff6f2d8e906c5691d677758bf3c4113')}
ACTION='Required action: allow this exact public HOPS0.35 archive download or supply the complete pinned archive with --archive; choose a NEW --out directory. Install/download only where permitted. If unavailable or forbidden, skip dependent RMA decoding and extraction; retain diagnostics. This supplies an original embedded reader for the native Java component. It does not install Java8, execute the original MaltExtract program, qualify whole HOPS/MaltExtract or reproduce a biological paper.'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--archive',type=Path);a=p.parse_args();out=a.out.absolute();owned=False
 state=dict(status='FAIL2_MALTEXTRACT17_READER_SETUP',scientific_analysis_executed=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW provider path required')
  out.mkdir(parents=True,exist_ok=False);owned=True;archive=a.archive.resolve() if a.archive else out/'hops-0.35-hdfd78af_1.tar.bz2'
  if not a.archive:
   with urllib.request.urlopen(urllib.request.Request(URL,headers={'User-Agent':'OmicsMatic-pinned-MaltExtract-reader'}),timeout=90) as response,archive.open('xb') as target:
    if response.status!=200:raise OSError('Runtime package HTTP'+str(response.status))
    total=0
    for data in iter(lambda:response.read(1048576),b''):
     total+=len(data)
     if total>ARCHIVE['bytes']:raise ValueError('Complete archive exceeds pinned byte count')
     target.write(data)
  if not archive.is_file():raise FileNotFoundError('Complete supplied package is unavailable')
  if pin(archive)!=ARCHIVE:raise ValueError('Complete pinned package bytes/hash differ')
  seen=set();expanded=0;count=0;selected={}
  with tarfile.open(archive,'r|bz2') as tar:
   for member in tar:
    count+=1;expanded+=member.size
    if count>10000 or member.size<0 or expanded>134217728:raise ValueError('Complete package inventory/expansion capacity exceeded')
    if member.name not in MEMBERS:continue
    target_name,size,digest=MEMBERS[member.name]
    if member.name in seen or not member.isfile() or member.size!=size:raise ValueError('Unexpected duplicate/nonregular/incomplete selected package member')
    source=tar.extractfile(member)
    if source is None:raise ValueError('Selected original member is unavailable')
    target=out/target_name
    with source,target.open('xb') as stream:
     for data in iter(lambda:source.read(1048576),b''):stream.write(data)
    actual=pin(target)
    if actual!=dict(bytes=size,sha256=digest):raise ValueError('Complete selected member bytes differ: '+target_name)
    seen.add(member.name);selected[target_name]=actual
  if seen!=set(MEMBERS):raise ValueError('Original provider package is incomplete')
  if json.loads((out/'package-index.json').read_bytes())['license']!='GPL >=3':raise ValueError('Original package license declaration differs')
  if pin(archive)!=ARCHIVE:raise ValueError('Original package changed during setup')
  state.update(status='READY_PINNED_MALTEXTRACT17_EMBEDDED_RMA_READER_ONLY',package_url=URL,archive=ARCHIVE,files=selected,declared_license='GPL >=3',new_tool_credit=0)
  save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Reader prerequisite only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],str(out),flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(OSError,urllib.error.URLError)) and not isinstance(error,FileExistsError) else 2
  state.update(status='SKIP3_READER_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_MALTEXTRACT17_READER_SETUP',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable pinned prerequisite checks')
 raise SystemExit(main())
