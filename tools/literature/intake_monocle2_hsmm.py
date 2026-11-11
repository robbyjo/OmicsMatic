# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the original missing Bioconductor experiment-data dependency; install nothing."""
import argparse
import gzip
import hashlib
import io
from pathlib import Path
import tarfile
import urllib.request
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir()
    repository='https://bioconductor.org/packages/3.16/data/experiment/src/contrib'
    def download(url,path,capacity):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-Monocle2-dependency-audit'}),timeout=180) as response:
            raw=response.read(capacity+1);assert len(raw)<=capacity;metadata=dict(url=url,resolved_url=response.url,response_headers=dict(response.headers))
        path.write_bytes(raw);return raw,dict(metadata,**pin(path))
    raw,index=download(repository+'/PACKAGES.gz',out/'PACKAGES.gz',16777216)
    entry=next(row for row in gzip.decompress(raw).decode('utf8').split('\n\n') if row.startswith('Package: HSMMSingleCell\n'))
    fields={row.split(': ',1)[0]:row.split(': ',1)[1] for row in entry.splitlines() if ': ' in row};version=fields['Version'];filename='HSMMSingleCell_'+version+'.tar.gz'
    raw,archive=download(repository+'/'+filename,out/filename,134217728);assert hashlib.md5(raw).hexdigest()==fields['MD5sum']
    with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
        member=tar.getmember('HSMMSingleCell/DESCRIPTION');assert member.isfile();description=tar.extractfile(member).read()
    assert ('\nVersion: '+version+'\n').encode() in description;(out/'DESCRIPTION').write_bytes(description)
    save(out/'SOURCE.json',dict(status='captured-original-missing-experiment-data-dependency',package='HSMMSingleCell',version=version,repository_record=entry,index=index,archive=archive,description=pin(out/'DESCRIPTION'),source_pin=pin(Path(__file__)),installed_nothing=True,analysis_admitted=False))
    print('Captured original HSMMSingleCell',version,'archive',len(raw),'bytes; no analysis admitted')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable dependency/source checks')
    main()
