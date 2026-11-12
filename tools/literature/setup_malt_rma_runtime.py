# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install only the pinned original MALT RMA-reader jars into a NEW permitted directory."""
from pathlib import Path
import argparse,hashlib,json,tarfile,traceback,urllib.error,urllib.request
URL='https://conda.anaconda.org/bioconda/noarch/malt-0.41-1.tar.bz2'
PACKAGE_SHA256='9757ceb54dbf9b193e05b0c43d5faa19b9457ab070f1e568120ce5f695debc51'
MEMBERS={'opt/malt-0.41/jars/MALT.jar':('MALT.jar',4641119,'9106de3134a8f9d96bcd35986ee7a61738a1b51fca1683bb32bb108e82e92cf4'),'opt/malt-0.41/jars/data.jar':('data.jar',24731618,'718a23ad8defbe34a35c9b0967c5fa616900adba34e8b961fb5531cd2382d2e3')}
ACTION='Required action: allow the documented public-package download in a permitted environment, or supply --archive with the complete malt-0.41-1.tar.bz2 matching SHA256 '+PACKAGE_SHA256+'. Select a NEW --out directory. If installation is unavailable or forbidden, skip dependent RMA analysis. Retain failed attempts; do not substitute another version or interpret a skip as a biological finding.'
def pin(path):
 h=hashlib.sha256();size=0
 with path.open('rb') as stream:
  for raw in iter(lambda:stream.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--archive',type=Path);a=p.parse_args();out=a.out.resolve();owned=False;state=dict(status='FAIL2_MALT_RMA_RUNTIME_INSTALLATION',reader_only=True,alignment_runtime_installed=False,scientific_analysis_executed=False)
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True
  if a.archive:archive=a.archive.resolve()
  else:
   archive=out/'malt-0.41-1.tar.bz2';request=urllib.request.Request(URL,headers={'User-Agent':'OmicsMatic-MALT-RMA-runtime-setup'})
   with urllib.request.urlopen(request,timeout=90) as response,archive.open('xb') as stream:
    if response.status!=200:raise OSError('Public package returned HTTP'+str(response.status))
    size=0
    while True:
     raw=response.read(1048576)
     if not raw:break
     size+=len(raw)
     if size>134217728:raise ValueError('Package exceeds complete download byte capacity')
     stream.write(raw)
  if not archive.is_file():raise FileNotFoundError('Required complete local source package is unavailable: '+str(archive))
  if archive.stat().st_size>134217728:raise ValueError('Source package exceeds complete byte capacity')
  before=pin(archive)
  if before['sha256']!=PACKAGE_SHA256:raise ValueError('Complete source package SHA256 mismatch')
  found={};count=0;expanded=0
  with tarfile.open(archive,'r|bz2') as tar:
   for member in tar:
    count+=1;expanded+=member.size
    if count>10000 or member.size<0 or expanded>268435456:raise ValueError('Complete package member/expanded-byte capacity exceeded')
    if member.name in MEMBERS:
     name,size,digest=MEMBERS[member.name]
     if name in found or not member.isfile() or member.size!=size:raise ValueError('Unexpected/duplicate runtime member: '+member.name)
     source=tar.extractfile(member)
     if source is None:raise ValueError('Missing complete runtime member')
     target=out/name
     with source,target.open('xb') as stream:
      copied=0
      for raw in iter(lambda:source.read(1048576),b''):copied+=len(raw);stream.write(raw)
     value=pin(target)
     if copied!=size or value!=dict(bytes=size,sha256=digest):raise ValueError('Runtime member SHA256/size mismatch: '+name)
     found[name]=value
  if len(found)!=2 or pin(archive)!=before:raise ValueError('Incomplete or changed original distribution')
  warnings=['RMA READER ONLY: this installs the two exact GPLv3 upstream jars for the isolated reader adapter. It installs no executable launchers, legacy Java8 or complete MALT alignment runtime. The original Conda package declares openjdk >=8.0.144,<9 for its whole program; only RMA-reader interoperability is separately qualified on modern Java21/25 by this project.','Do not run alignment or infer original query coverage/biological findings from this installation receipt.']
  state.update(status='READY_PINNED_MALT041_RMA_READER_ONLY',source_url=URL,package=before,members=found,upstream_license='GPLv3',upstream_source='https://github.com/husonlab/malt',original_package_dependency='openjdk >=8.0.144,<9',warnings=warnings)
  save(out/'INSTALLATION.json',state);(out/'WARNINGS.md').write_text('# MALT RMA reader runtime only\n\n'+'\n\n'.join(warnings)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],str(out),flush=True);return 0
 except Exception as error:
  skipped=isinstance(error,(OSError,urllib.error.URLError));state.update(status='SKIP3_MALT_RMA_RUNTIME_INSTALLATION_UNAVAILABLE' if skipped else 'FAIL2_MALT_RMA_RUNTIME_INSTALLATION',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return 3 if skipped else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable runtime provenance checks')
 raise SystemExit(main())
