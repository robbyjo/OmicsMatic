# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin author-published BPCells artifacts and Monocle's declared speedglm fork; do not install."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import tarfile
import urllib.request
import zipfile
from setup_deseq2_reference import ROOT, pin, save


def fetch(url, path, maximum=33554432):
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-source-audit'})
    with urllib.request.urlopen(request,timeout=90) as response:
        raw=response.read(maximum+1);resolved=response.url
        assert len(raw)<=maximum
    path.write_bytes(raw)
    return raw,resolved


def fields(text):
    result={};key=None
    for line in text.splitlines():
        if line.startswith((' ','\t')):
            assert key is not None;result[key]+=' '+line.strip()
        elif line:
            key,value=line.split(':',1);result[key]=value.strip()
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    records={}
    for kind,suffix in [('win.binary','bin/windows/contrib/4.6'),('source','src/contrib')]:
        index,url=fetch('https://bnprks.r-universe.dev/'+suffix+'/PACKAGES.gz',out/(kind+'-PACKAGES.gz'))
        rows=[fields(block) for block in gzip.decompress(index).decode('utf8').strip().split('\n\n') if block.strip()]
        row=next(r for r in rows if r['Package']=='BPCells')
        assert row['Version']=='0.3.1'
        filename=row['File'];assert '/' not in filename and '\\' not in filename
        archive,resolved=fetch('https://bnprks.r-universe.dev/'+suffix+'/'+row['Path'],out/filename)
        assert hashlib.sha256(archive).hexdigest()==row['SHA256'] and len(archive)==int(row['Filesize'])
        if kind=='win.binary':
            with zipfile.ZipFile(io.BytesIO(archive)) as z:
                description=z.read('BPCells/DESCRIPTION')
                assert len(z.namelist())==len(set(z.namelist()))
                assert all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts and '\\' not in n for n in z.namelist())
        else:
            with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
                match=[m for m in tar.getmembers() if m.name.endswith('/DESCRIPTION') and len(PurePosixPath(m.name).parts)==2]
                assert len(match)==1;description=tar.extractfile(match[0]).read()
        desc=out/('BPCells-'+kind+'-DESCRIPTION');desc.write_bytes(description)
        records['BPCells-'+kind]=dict(package='BPCells',version='0.3.1',type=kind,url=resolved,
            archive=dict(path=(out/filename).relative_to(ROOT).as_posix(),**pin(out/filename)),
            description=dict(path=desc.relative_to(ROOT).as_posix(),**pin(desc)),repository_record=row)
    meta,_=fetch('https://api.github.com/repos/cole-trapnell-lab/speedglm/commits/master',out/'speedglm-commit.json')
    commit=json.loads(meta)['sha'];assert re.fullmatch('[0-9a-f]{40}',commit)
    archive,resolved=fetch('https://codeload.github.com/cole-trapnell-lab/speedglm/tar.gz/'+commit,out/'speedglm-source.tar.gz')
    with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
        member=tar.getmember('speedglm-'+commit+'/DESCRIPTION');description=tar.extractfile(member).read()
    desc=out/'speedglm-DESCRIPTION';desc.write_bytes(description);version=fields(description.decode('utf8'))['Version']
    records['speedglm-source']=dict(package='speedglm',version=version,type='source',commit=commit,url=resolved,
        archive=dict(path=(out/'speedglm-source.tar.gz').relative_to(ROOT).as_posix(),**pin(out/'speedglm-source.tar.gz')),
        description=dict(path=desc.relative_to(ROOT).as_posix(),**pin(desc)))
    save(out/'SOURCE.json',dict(status='pinned-author-BPCells-and-declared-speedglm-fork',records=records,
        source_pins={'tools/literature/intake_monocle_current_dependencies.py':pin(Path(__file__))},
        installed_software=False,analysis_admitted=False))
    print(json.dumps({n:{k:r[k] for k in ['version','type','archive']} for n,r in records.items()}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable author-artifact hash and source checks')
    main()
