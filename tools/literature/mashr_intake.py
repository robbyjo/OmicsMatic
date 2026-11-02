#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fetch official mashr0.2.79 source and CRAN binary metadata; no installation."""
import concurrent.futures,datetime,hashlib,json,tarfile,urllib.request
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/literature-review/mashr-runtime/source-intake-1'
URLS={'github-source.tar.gz':'https://github.com/stephenslab/mashr/archive/refs/tags/0.2.79.tar.gz','cran-source.tar.gz':'https://cran.r-project.org/src/contrib/mashr_0.2.79.tar.gz','windows-4.6-PACKAGES.gz':'https://cran.r-project.org/bin/windows/contrib/4.6/PACKAGES.gz'}
def get(row):
 name,url=row;target=OUT/name
 request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-scientific-source-intake/1'})
 with urllib.request.urlopen(request,timeout=120) as source,target.open('xb') as out:
  assert source.geturl().startswith('https://');h=hashlib.sha256();size=0
  while True:
   block=source.read(1048576)
   if not block:break
   size+=len(block);assert size<=67108864;h.update(block);out.write(block)
  return dict(path=name,url=url,final_url=source.geturl(),bytes=size,sha256=h.hexdigest())
def main():
 OUT.mkdir(parents=True,exist_ok=False)
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:rows=list(pool.map(get,URLS.items()))
 extracted={}
 for archive in ['github-source.tar.gz','cran-source.tar.gz']:
  folder=OUT/archive.split('-')[0];folder.mkdir();files={};total=0
  with tarfile.open(OUT/archive,'r:gz') as tar:
   members=tar.getmembers();assert len(members)<=20000
   for member in members:
    parts=PurePosixPath(member.name).parts;assert not member.name.startswith('/') and all(p not in ['.','..'] for p in parts)
    if len(parts)<2:continue
    name='/'.join(parts[1:]);selected=name in ['DESCRIPTION','NAMESPACE','LICENSE','LICENSE.md','COPYRIGHTS','configure','configure.win'] or name.startswith(('R/','src/','man/'))
    if not selected or member.isdir():continue
    assert member.isfile() and member.size<=16777216;total+=member.size;assert total<=67108864;target=folder/name;target.parent.mkdir(parents=True,exist_ok=True)
    with tar.extractfile(member) as source:raw=source.read(member.size+1)
    assert len(raw)==member.size;target.write_bytes(raw);files[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
  extracted[folder.name]=files
 shared=set(extracted['github'])&set(extracted['cran']);differences=[name for name in sorted(shared) if extracted['github'][name]!=extracted['cran'][name]]
 record=dict(status='downloaded-official-source-and-binary-index-no-install',recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),downloads=rows,extracted_source_pins=extracted,shared_source_files=len(shared),source_differences=differences,user_analysis_executed=False)
 (OUT/'intake.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps({k:v for k,v in record.items() if k!='extracted_source_pins'}))
if __name__=='__main__':main()
