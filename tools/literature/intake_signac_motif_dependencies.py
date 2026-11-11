# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain official original motif source packages for current versus paper-era source comparison."""
import argparse
import hashlib
import json
from pathlib import Path,PurePosixPath
import tarfile
import urllib.request
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();rows=[];source=Path(__file__).read_bytes();(out/'source-used.py').write_bytes(source);save(out/'SOURCE-SNAPSHOT.json',dict(bytes=len(source),sha256=hashlib.sha256(source).hexdigest()))
    for package,version,bioc in [('chromVAR','1.16.0','3.14'),('motifmatchr','1.16.0','3.14'),('chromVAR','1.34.1','3.23'),('motifmatchr','1.34.0','3.23')]:
        url=f'https://bioconductor.org/packages/{bioc}/bioc/src/contrib/{package}_{version}.tar.gz';folder=out/(package+'-'+version);folder.mkdir();row=dict(package=package,version=version,url=url,analysis_admitted=False)
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-method-reproduction/1.0'}),timeout=90) as response:
                raw=response.read(30_000_001);row['final_url']=response.url;assert len(raw)<=30_000_000
            archive=folder/(package+'_'+version+'.tar.gz');archive.write_bytes(raw);row['archive']=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());total=0;members=0
            with tarfile.open(archive,'r:gz') as tar:
                for member in tar.getmembers():
                    path=PurePosixPath(member.name);assert not path.is_absolute() and '..' not in path.parts and path.parts[0]==package
                    if member.isdir():continue
                    assert member.isfile() and not member.issym() and not member.islnk() and member.size<=10_000_000;total+=member.size;members+=1;assert total<=100_000_000 and members<=5000;value=tar.extractfile(member).read();target=folder/'source'/member.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(value)
            desc=(folder/'source'/package/'DESCRIPTION').read_text(encoding='utf8');assert ('Version: '+version+'\n') in desc;row.update(status='retained-official-source-not-runtime-admission',source_members=members)
        except Exception as error:row.update(status='SOURCE_UNAVAILABLE_DEPENDENT_HISTORICAL_ANALYSIS_SKIPPED',error=str(error),required_action='Provide the named official source/archive where permitted before claiming that exact historical source profile. Keep current-version results explicitly separate; preserve this failed attempt.')
        save(folder/'RESULT.json',row);rows.append(row);save(out/'PROGRESS.json',rows);print(json.dumps(dict(package=package,version=version,status=row['status'])),flush=True)
    save(out/'COMPLETED.json',dict(status='completed-official-motif-source-intake-with-explicit-skips',records=rows,user_analysis_admitted=False))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source archive admission checks')
    main()
