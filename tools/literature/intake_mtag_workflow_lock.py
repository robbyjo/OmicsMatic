# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the tested original MTAG source and all33 archived Python2 packages for a portable private setup."""
import argparse
import json
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,COMMIT


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    setup=BASE/'reference-setup-4';profile=json.loads((setup/'COMPLETED.json').read_bytes())
    assert profile['status']=='PASS_ORIGINAL_MTAG108_REFERENCE_PREPARED_ONLY' and profile['source_commit']==COMMIT
    plan=json.loads((setup/'environment-plan.stdout').read_bytes())['actions']['FETCH']
    installed=json.loads((setup/'installed-packages.stdout').read_bytes())['packages']
    assert len(plan)==len(installed)==33
    by_url={r['url']:r for r in plan};packages=[]
    for r in installed:
        observed=by_url[r['url']];assert r['md5']==observed['md5'] and r['name']==observed['name'] and len(r['sha256'])==64
        assert r['url'].startswith('https://conda.anaconda.org/conda-forge/label/cf202003/')
        packages.append(dict(name=r['name'],version=r['version'],build=r['build_string'],url=r['url'],filename=observed['fn'],
                             bytes=observed['size'],sha256=r['sha256'],md5=r['md5'],license=observed['license']))
    source=BASE/'source-intake-1';record=json.loads((source/'COMPLETED.json').read_bytes())
    assert record['commit']==COMMIT and pin(source/'upstream.zip')==record['archive']
    request=urllib.request.Request('https://api.github.com/repos/mamba-org/micromamba-releases/releases/tags/2.10.0-0',headers={'User-Agent':'OmicsMatic-private-runtime-intake/1.0'})
    with urllib.request.urlopen(request,timeout=60) as response:raw=response.read()
    (out/'official-micromamba-release.json').write_bytes(raw);release=json.loads(raw)
    assets=[r for r in release['assets'] if r['name']=='micromamba-linux-64'];assert len(assets)==1
    url=assets[0]['browser_download_url'];assert url.startswith('https://github.com/mamba-org/micromamba-releases/releases/download/2.10.0-0/')
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-private-runtime-intake/1.0'}),timeout=90) as response:raw=response.read(33554433)
    assert len(raw)<=33554432
    binary=out/'micromamba-linux-64';binary.write_bytes(raw)
    assert pin(binary)==profile['helper_pin'],'Official release binary differs from tested private micromamba; retain mismatch and do not manufacture a matching runtime'
    lock=dict(schema_version=1,tool='MTAG',version='1.0.8',source_commit=COMMIT,platform='linux-x86_64',
        source=dict(url=record['url'],**record['archive'],files=record['files']),packages=sorted(packages,key=lambda r:r['name']),
        versions={k:v for k,v in profile['versions'].items() if k!='python'},python_version='2.7.15',
        bootstrap=dict(version='2.10.0',url=url,**pin(binary)),licensed_use='Original MTAG GPL3; package licenses retained per pinned archive. Hashes provide integrity, not producer authenticity.',
        setup_only_no_analysis=True)
    save(out/'mtag-workflow-lock.json',lock)
    dest=ROOT/'src/main/resources/org/jlinalg/omics/mtag/mtag-workflow-lock.json';assert not dest.exists();dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((out/'mtag-workflow-lock.json').read_bytes())
    save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_MTAG_SOURCE_33_PACKAGES_AND_OFFICIAL_BOOTSTRAP_LOCK_ONLY',lock=pin(dest),intake_helper=pin(Path(__file__)),analysis_executed=False,new_catalog_credit=False))
    print('PASS original MTAG,33 archived packages and official bootstrap locked; no analysis')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/package/bootstrap identity checks')
    main()
