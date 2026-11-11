# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain official ChIPseeker source editions for a new native-method audit; execute/install nothing."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
import urllib.request

ROOT=Path(__file__).resolve().parents[2]
SOURCES=[('1.22.1','3.10'),('1.26.2','3.12'),('1.34.1','3.16'),('1.48.1','3.23')]


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir(parents=True)
    rows=[]
    for version,release in SOURCES:
        folder=out/version;folder.mkdir();url=f'https://bioconductor.org/packages/{release}/bioc/src/contrib/ChIPseeker_{version}.tar.gz'
        archive=folder/f'ChIPseeker_{version}.tar.gz';partial=folder/'source.partial'
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=120) as response,partial.open('xb') as target:
                headers=dict(response.headers);resolved=response.url;total=0
                while True:
                    block=response.read(1048576)
                    if not block:break
                    total+=len(block)
                    if total>104857600:raise ValueError('Original source archive exceeds intake capacity')
                    target.write(block)
            raw=partial.read_bytes()
            with tarfile.open(partial,'r:gz') as package:
                members={p.name:p for p in package.getmembers() if p.isfile()}
                description=package.extractfile(members['ChIPseeker/DESCRIPTION']).read().decode('utf8')
                assert '\nVersion: '+version+'\n' in description
                selected={n:package.extractfile(p).read() for n,p in members.items() if n.startswith('ChIPseeker/R/') or n in ['ChIPseeker/DESCRIPTION','ChIPseeker/NAMESPACE','ChIPseeker/NEWS','ChIPseeker/inst/LICENSE']}
                assert 'ChIPseeker/R/getNearestFeatureIndicesAndDistances.R' in selected
            partial.rename(archive)
            for name,body in selected.items():
                destination=folder/'selected-source'/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(body)
            record=dict(version=version,Bioconductor_release=release,source_url=url,resolved_url=resolved,response_headers=headers,
                archive=pin(raw),source_members={n:pin(b) for n,b in selected.items()},status='official-source-captured-no-runtime-or-analysis')
            save(folder/'SOURCE.json',record);rows.append(record);print(version+': official source retained',flush=True)
        except Exception as failure:
            record=dict(version=version,source_url=url,status='source-intake-failed-no-analysis',error=str(failure),required_action='Preserve this attempt. Correct the named source/download cause and retry into a NEW directory; skip dependent comparison until an exact source is available.')
            save(folder/'FAILURE.json',record);rows.append(record);print(version+': source intake failed; analysis skipped: '+str(failure),flush=True)
    save(out/'RESULT.json',dict(cases=rows,source=pin(Path(__file__).read_bytes()),installation_performed=False,analysis_admitted=False))
    if all(r['status']=='official-source-captured-no-runtime-or-analysis' for r in rows):
        save(out/'COMPLETED.json',dict(status='captured-four-official-ChIPseeker-source-editions-not-implemented',versions=[r['version'] for r in rows],installation_performed=False,analysis_admitted=False))
    else:raise SystemExit(2)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original edition/source checks')
    main()
