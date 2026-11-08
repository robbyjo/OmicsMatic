# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the exact original package solve and independently checked BioPerl source archive for restoration."""
import argparse,hashlib,io,json,urllib.request,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/loftee-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);(out/'preparer-at-attempt.py').write_bytes(Path(__file__).read_bytes());profile_path=ROOT/'src/main/resources/org/jlinalg/omics/loftee-workflow/SOURCE-PROFILE.json';profile=json.loads(profile_path.read_bytes());original=base/'reference-setup-1';plan=json.loads((original/'environment-plan.stdout').read_bytes());rows=plan['actions']['LINK'];assert len(rows)==224 and rows==plan['actions']['FETCH'];packages=[]
    for row in rows:
        packages.append({k:row[k] for k in ['name','version','build','fn','url','sha256','md5','size','license']})
    url='https://codeload.github.com/bioperl/bioperl-live/zip/refs/tags/release-1-6-924'
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-exact-private-dependency-setup/1.0'}),timeout=90) as response:
        raw=response.read(67108865);assert len(raw)<=67108864;archive=out/'bioperl-release-1-6-924.zip';archive.write_bytes(raw);save(out/'bioperl-download.json',dict(url=url,final_url=response.url,http_status=response.status,archive=pin(archive)))
    prefix='bioperl-live-release-1-6-924/'
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        assert len(set(archive.namelist()))==len(archive.namelist()) and archive.testzip() is None
        for name,value in profile['bioperl']['files'].items():
            assert value['kind']=='file' and 'prefix_relocation' not in value;data=archive.read(prefix+name);assert dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())=={k:value[k] for k in ['bytes','sha256']},name
    mamba=json.loads((original/'micromamba-download.json').read_bytes());lock=dict(schema_version=1,profile=profile['profile'],source_profile=pin(profile_path),package_count=len(packages),download_bytes=sum(p['size'] for p in packages),packages=packages,micromamba=dict(url=mamba['final_url'],archive=mamba['archive'],version=(original/'micromamba-version.stdout').read_text(encoding='utf8').strip()),bioperl=dict(url=url,archive=pin(out/'bioperl-release-1-6-924.zip'),member_prefix=prefix,checked_source_files=len(profile['bioperl']['files'])),installation_scope='private-original-dependencies-only-no-human-resources-no-analysis',host_policy='Exact captured Linux host dependencies required; any drift skips dependent analysis rather than repinning.')
    target=ROOT/'docs/literature/loftee-workflow-development/INSTALL-LOCK.json';assert not target.exists();target.parent.mkdir(parents=True,exist_ok=True);save(target,lock);save(out/'COMPLETED.json',dict(status='prepared-exact-install-lock-not-installation',lock=dict(path=target.relative_to(ROOT).as_posix(),**pin(target)),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__)),profile_path.relative_to(ROOT).as_posix():pin(profile_path)},original_plan=pin(original/'environment-plan.stdout'),bioperl_checked_files=820,implementation_credit=False));print('Locked224 original packages and820 exact BioPerl files; no installation or analysis')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact package/source checks')
    main()
