# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact official Bioconductor Monocle2 source for differentialGeneTest review; install nothing."""
import argparse
import gzip
import hashlib
import io
from pathlib import Path,PurePosixPath
import tarfile
import urllib.request
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--version',choices=['2.40.0','2.26.0'],default='2.40.0');a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir()
    version=a.version;release={'2.40.0':'3.23','2.26.0':'3.16'}[version];repo='https://bioconductor.org/packages/'+release+'/bioc/src/contrib'
    def download(url,name,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-source-review'}),timeout=180) as response:
            raw=response.read(maximum+1);assert len(raw)<=maximum
            metadata=dict(url=url,resolved_url=response.url,response_headers=dict(response.headers))
        (out/name).write_bytes(raw);return raw,dict(metadata,**pin(out/name))
    raw,index=download(repo+'/PACKAGES.gz','PACKAGES.gz',16777216)
    entries=gzip.decompress(raw).decode('utf8').split('\n\n');entry=next(e for e in entries if e.startswith('Package: monocle\n'))
    assert '\nVersion: '+version+'\n' in entry;save(out/'REPOSITORY.json',dict(index=index,exact_package_record=entry))
    raw,archive=download(repo+'/monocle_'+version+'.tar.gz','monocle_'+version+'.tar.gz',134217728);members={}
    with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
        for item in tar.getmembers():
            path=PurePosixPath(item.name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in item.name and path.parts[0]=='monocle'
            if item.isdir():continue
            assert item.isfile() and not item.issym() and not item.islnk() and item.size<=33554432
            name=PurePosixPath(*path.parts[1:]).as_posix();assert name not in members
            target=(out/'source'/name).resolve();assert target.is_relative_to(out/'source');target.parent.mkdir(parents=True,exist_ok=True)
            content=tar.extractfile(item).read();assert len(content)==item.size;target.write_bytes(content);members[name]=pin(target)
    description=(out/'source/DESCRIPTION').read_text(encoding='utf8');assert '\nVersion: '+version+'\n' in description
    # Repository MD5 is an additional transport check; SHA-256 pins all retained bytes.
    md5=next(line.split(': ',1)[1] for line in entry.splitlines() if line.startswith('MD5sum: '));assert hashlib.md5(raw).hexdigest()==md5
    save(out/'SOURCE.json',dict(status='captured-exact-official-Monocle2-source-not-analysis-admission',version=version,repository=repo,archive=archive,source_members=members,
        source_pin=pin(Path(__file__)),license='Artistic-2.0',paper_P099_exact_patch_reported=False,whole_historical_environment_reproduced=False,installed_nothing=True,implementation_credit=False))
    print('Captured original monocle',version,len(members),'files; no installation or analysis admission')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official source checks')
    main()
