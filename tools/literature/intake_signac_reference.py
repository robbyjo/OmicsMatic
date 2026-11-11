# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain official CRAN Signac source editions and bounded source files for method reproduction."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path,PurePosixPath
import tarfile
import urllib.request

ROOT=Path(__file__).resolve().parents[2]
VERSIONS=['1.6.0','1.7.0','1.9.0','1.14.0']
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/signac-runtime').resolve()) and not out.exists();out.mkdir(parents=True)
    def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
    save(out/'INCOMPLETE.json',dict(status='OFFICIAL_SIGNAC_SOURCE_INTAKE_IN_PROGRESS'))
    acquired=[]
    def fetch(url,target,limit):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-method-reproduction/1.0'})
        with urllib.request.urlopen(request,timeout=90) as response:
            raw=response.read(limit+1);assert len(raw)<=limit;receipt=dict(url=url,final_url=response.geturl(),**pin(raw))
        target.write_bytes(raw);acquired.append(receipt);save(out/'DOWNLOAD-PROGRESS.json',acquired);return raw,receipt
    try:
        raw,index_receipt=fetch('https://cloud.r-project.org/src/contrib/PACKAGES.gz',out/'CRAN-PACKAGES.gz',8388608)
        package=next(block for block in gzip.decompress(raw).decode().split('\n\n') if block.startswith('Package: Signac\n'));fields={line.split(': ',1)[0]:line.split(': ',1)[1] for line in package.splitlines() if ': ' in line and not line.startswith(' ')};current=fields['Version'];assert current.startswith('1.') and all(x.isdigit() for x in current.split('.'))
        save(out/'CRAN-Signac-record.json',dict(fields=fields,raw_DCF=package,index=index_receipt,current_CRAN_edition=current));editions=[]
        for version in dict.fromkeys(VERSIONS+[current]):
            folder=out/version;folder.mkdir();filename='Signac_'+version+'.tar.gz';url=('https://cloud.r-project.org/src/contrib/' if version==current else 'https://cran.r-project.org/src/contrib/Archive/Signac/')+filename
            archive,receipt=fetch(url,folder/filename,33554432)
            if version==current and 'MD5sum' in fields:assert hashlib.md5(archive).hexdigest()==fields['MD5sum']
            files={};total=0
            with tarfile.open(folder/filename,'r:gz') as tar:
                members=tar.getmembers();assert len(members)<=10000
                for member in members:
                    name=PurePosixPath(member.name);assert not name.is_absolute() and '..' not in name.parts and '\\' not in member.name and name.parts[0]=='Signac' and not member.issym() and not member.islnk()
                    if member.isdir():continue
                    assert member.isfile() and member.size<=33554432;total+=member.size;assert total<=268435456
                    # Keep all package source metadata/R/C++/tests/docs. Large
                    # bundled R data remain in the original unchanged archive.
                    if len(name.parts)==2 or name.parts[1] in ['R','src','tests','man']:
                        with tar.extractfile(member) as stream:body=stream.read(member.size+1)
                        assert len(body)==member.size;target=folder/'source'/Path(*name.parts);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(body);files[str(name)]=pin(body)
            description=(folder/'source/Signac/DESCRIPTION').read_text(encoding='utf-8');assert '\nVersion: '+version+'\n' in description and (folder/'source/Signac/LICENSE').is_file()
            editions.append(dict(version=version,archive=receipt,source_files=files,current_CRAN=version==current,publisher_MD5_checked=version==current and 'MD5sum' in fields));save(out/'SOURCE-PROGRESS.json',editions)
        result=dict(status='retained-official-CRAN-Signac-sources-not-runtime-or-public-integration',editions=editions,current_CRAN_edition=current,source_paper_editions=VERSIONS,acquisition=acquired,source_files_extracted=True,upstream_binary_installed=False,implementation_credit=False,warning='Archive SHA256 values establish local integrity, not signatures. Current CRAN MD5 is checked when provided; historical editions have no publisher digest assertion. Full package setup, original-method controls, native contracts and public workflows remain to be validated.')
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],editions=[e['version'] for e in editions])))
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='SIGNAC_SOURCE_INTAKE_FAILED_NO_RUNTIME_OR_RESULT',error=str(error),required_action='Preserve downloads and partials; inspect source/download logs, correct the named cause and retry into NEW paths. No analysis is admitted.'));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source intake checks')
    main()
