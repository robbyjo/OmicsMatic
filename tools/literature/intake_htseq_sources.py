# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain official HTSeq release metadata, verified archives and bounded source trees."""
import argparse
import hashlib
import json
import tarfile
import urllib.request
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/htseq-runtime'


def pin(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    def save(path,value):
        path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
    downloads=[]
    def fetch(url,target,limit):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-method-reproduction/1.0'})
        with urllib.request.urlopen(request,timeout=60) as response:
            raw=response.read(limit+1)
            assert len(raw)<=limit
            receipt=dict(url=url,final_url=response.geturl(),**pin(raw))
        target.write_bytes(raw)
        downloads.append(receipt)
        save(out/'DOWNLOAD-PROGRESS.json',downloads)
        return raw
    save(out/'INCOMPLETE.json',dict(status='official-HTSeq-source-intake-in-progress'))
    try:
        index=json.loads(fetch('https://pypi.org/pypi/HTSeq/json',out/'PyPI-HTSeq.json',16777216))
        current=index['info']['version']
        assert all(x.isdigit() for x in current.split('.'))
        editions=[]
        for version in dict.fromkeys(['0.5.3','0.6.0','0.11.1','0.13.5',current]):
            folder=out/version
            folder.mkdir()
            metadata=json.loads(fetch('https://pypi.org/pypi/HTSeq/'+version+'/json',folder/'PyPI-release.json',16777216))
            assert metadata['info']['version']==version
            archives=[r for r in metadata['urls'] if r['packagetype']=='sdist']
            assert len(archives)==1,(version,'Expected one authoritative source archive')
            row=archives[0]
            assert row['url'].startswith('https://files.pythonhosted.org/')
            filename=Path(row['filename']).name
            raw=fetch(row['url'],folder/filename,67108864)
            assert pin(raw)['sha256']==row['digests']['sha256'] and len(raw)==row['size']
            files={}
            total=0
            with tarfile.open(folder/filename,'r:gz') as archive:
                members=archive.getmembers()
                assert len(members)<=20000
                for entry in members:
                    name=PurePosixPath(entry.name)
                    assert not name.is_absolute() and '..' not in name.parts and '\\' not in entry.name
                    assert not entry.issym() and not entry.islnk()
                    if entry.isdir():
                        continue
                    assert entry.isfile() and entry.size<=67108864
                    total+=entry.size
                    assert total<=536870912
                    with archive.extractfile(entry) as stream:
                        body=stream.read(entry.size+1)
                    assert len(body)==entry.size
                    target=folder/'source'/Path(*name.parts)
                    target.parent.mkdir(parents=True,exist_ok=True)
                    target.write_bytes(body)
                    files[name.as_posix()]=pin(body)
            editions.append(dict(version=version,current_PyPI_edition=version==current,
                archive=dict(filename=filename,url=row['url'],**pin(raw)),publisher_SHA256_checked=True,source_files=files))
            save(out/'SOURCE-PROGRESS.json',editions)
        result=dict(schema_version=1,status='retained-official-HTSeq-source-editions-not-installed',
            current_PyPI_edition=current,editions=editions,downloads=downloads,
            paper_edition_notes=['P005 reports0.6.0 using the spelling HT-seq, recovered by the whole-cohort census',
                'P020 explicitly reports0.5.3','P113 reports0.11.1','P044/P121 report0.13.5',
                'P195 major/minor0.13 is not an exact patch edition','P099/P148 edition unstated'],
            implementation_credit=False,upstream_runtime_installed=False,
            warning='SHA256 proves retained integrity and matches publisher metadata; it is not a signature. Installation, original-method controls, native code and public workflows require separate validation.')
        save(out/'COMPLETED.json',result)
        print(json.dumps(dict(status=result['status'],editions=[r['version'] for r in editions])))
    except BaseException as failure:
        save(out/'FAILURE.json',dict(status='FAIL2_HTSEQ_SOURCE_INTAKE',error=repr(failure)))
        raise


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable upstream archive checks')
    main()
