#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create the exact Windows R/mashr/monitor profile from verified official artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
TARGET=ROOT/'tools/literature/mashr-original-runtime-lock.json'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    if not __debug__:raise RuntimeError('Never disable artifact provenance checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--census',type=Path,required=True);parser.add_argument('--receipt',type=Path,required=True);parser.add_argument('--target',type=Path,default=TARGET);args=parser.parse_args()
    reference,census,receipt=[getattr(args,n).resolve() for n in ['reference','census','receipt']]
    target=args.target.resolve();assert target.is_relative_to(ROOT) and not target.exists() and not receipt.exists();receipt.mkdir(parents=True)
    original=json.loads((reference/'COMPLETED.json').read_text());assert original['status']=='COMPLETED_PRIVATE_REFERENCE_SETUP_ONLY'
    packages=[];package_files={}
    for p in original['packages']:
        assert pin(reference/'cache'/p['file'])=={k:p[k] for k in ['bytes','sha256']}
        packages.append({k:p[k] for k in ['package','version','url','file','bytes','sha256']})
        with zipfile.ZipFile(reference/'cache'/p['file']) as archive:
            for entry in archive.infolist():
                if entry.is_dir():continue
                content=archive.read(entry);assert entry.filename not in package_files
                package_files[entry.filename]=dict(bytes=len(content),sha256=hashlib.sha256(content).hexdigest())
    assert len(packages)==17
    url='https://pypi.org/pypi/psutil/7.0.0/json'
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-mashr-runtime-intake/1'}),timeout=60) as response:raw=response.read(2_097_153)
    assert len(raw)<=2_097_152;metadata=json.loads(raw);assert metadata['info']['name']=='psutil' and metadata['info']['version']=='7.0.0'
    (receipt/'psutil-7.0.0-pypi.json').write_bytes(raw)
    helper=[]
    for filename in ['psutil-7.0.0-cp37-abi3-win_amd64.whl','psutil-7.0.0.tar.gz']:
        candidates=[p for p in metadata['urls'] if p['filename']==filename];assert len(candidates)==1
        p=candidates[0];assert p['url'].startswith('https://files.pythonhosted.org/') and not p['yanked']
        helper.append(dict(file=filename,url=p['url'],bytes=p['size'],sha256=p['digests']['sha256']))
    for item in helper:
        with urllib.request.urlopen(urllib.request.Request(item['url'],headers={'User-Agent':'OmicsMatic-mashr-runtime-intake/1'}),timeout=60) as response:content=response.read(item['bytes']+1)
        assert len(content)==item['bytes'] and hashlib.sha256(content).hexdigest()==item['sha256'];(receipt/item['file']).write_bytes(content)
    monitor_files={}
    with zipfile.ZipFile(receipt/helper[0]['file']) as archive:
        for entry in archive.infolist():
            if entry.is_dir():continue
            content=archive.read(entry);monitor_files[entry.filename]=dict(bytes=len(content),sha256=hashlib.sha256(content).hexdigest())
    runtime=json.loads(census.read_text())['files']['rhome'];roots=('bin/','etc/','library/','modules/','share/')
    selected={n:v for n,v in runtime.items() if n.startswith(roots)};assert len(selected)>2000
    lock=dict(schema_version=1,profile='windows-x86_64-R4.6.1-mashr0.2.79',R_version='4.6.1',mashr_version='0.2.79',ashr_version='2.2-63',
        rscript={k:original['rscript'][k] for k in ['bytes','sha256']},rhome_runtime_roots=list(roots),rhome_runtime_files=selected,
        R_base_dependencies=original['base_dependencies'],packages=packages,package_files=package_files,monitor=dict(name='psutil',version='7.0.0',python='CPython 3.11+ Windows AMD64; current validation uses3.12',artifacts=helper,files=monitor_files),
        provenance=dict(reference_setup=pin(reference/'COMPLETED.json'),reference_runtime_census=pin(census),psutil_metadata_url=url,psutil_metadata=pin(receipt/'psutil-7.0.0-pypi.json'),
                        R_home_fingerprint='Observed installed R4.6.1 distribution runtime files; not an installer signature or source-to-binary proof.',source_to_binary_reproduction_verified=False),
        policy='Exact private Windows runtime. Explicit install/download flags; missing/forbidden dependencies SKIP3 and no dependent analysis. No edition substitution or user-library fallback.')
    target.write_text(json.dumps(lock,indent=2)+'\n',encoding='utf-8',newline='\n')
    (receipt/'RECEIPT.json').write_text(json.dumps(dict(status='captured-exact-runtime-and-official-monitor-artifact-lock',lock=pin(target),packages=len(packages),R_runtime_files=len(selected),monitor=helper),indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(lock=pin(target),packages=len(packages),R_runtime_files=len(selected),monitor=helper)))


if __name__=='__main__':main()
