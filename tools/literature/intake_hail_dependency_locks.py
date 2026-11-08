# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resolve only the already installed reference dependency archives to exact official PyPI URLs/hashes for reproducible private setup."""
import argparse,concurrent.futures,json,urllib.parse,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_EXACT_HAIL_DEPENDENCY_INTAKE_INCOMPLETE',implementation_credit=False,whole_goal_complete=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))});(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());requests={};editions={};files={}
    try:
        for version,setup in [('0.2.139','reference-setup-139-2'),('0.2.130','reference-setup-130-2')]:
            report=ROOT/'build/literature-review/hail-runtime'/setup/'pip-report.json';data=json.loads(report.read_bytes());packages=[]
            for item in data['install']:
                name=item['metadata']['name'];release=item['metadata']['version'];info=item['download_info'];filename=Path(urllib.parse.unquote(urllib.parse.urlparse(info['url']).path)).name;digest=info['archive_info']['hashes']['sha256'];key=name.lower().replace('_','-'),release;requests[key]=name,release;packages.append(dict(name=name,version=release,filename=filename,sha256=digest));path=urllib.parse.unquote(urllib.parse.urlparse(info['url']).path)
                if path.startswith('/mnt/d/'):local=Path('D:/'+path[len('/mnt/d/'):]);assert pin(local)['sha256']==digest;files[(key,filename)]=local
            editions[version]=dict(reference_install_report=dict(path=report.relative_to(ROOT).as_posix(),**pin(report)),packages=packages)
        bootstrap=[dict(name='pip',version='24.3.1',filename='pip-24.3.1-py3-none-any.whl'),dict(name='setuptools',version='80.9.0',filename='setuptools-80.9.0-py3-none-any.whl'),dict(name='wheel',version='0.45.1',filename='wheel-0.45.1-py3-none-any.whl')]
        for item in bootstrap:requests[(item['name'],item['version'])]=item['name'],item['version']
        def fetch(item):
            key,(name,version)=item;url='https://pypi.org/pypi/'+urllib.parse.quote(name,safe='')+'/'+urllib.parse.quote(version,safe='')+'/json'
            with urllib.request.urlopen(url,timeout=60) as response:raw=response.read(16777217)
            assert len(raw)<=16777216;path=out/(key[0]+'-'+version+'.pypi.json');path.write_bytes(raw);metadata=json.loads(raw);assert metadata['info']['version']==version;return key,dict(url=url,metadata_file=path.name,metadata_pin=pin(path),data=metadata)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:metadata=dict(pool.map(fetch,sorted(requests.items())))
        def resolve(item):
            key=item['name'].lower().replace('_','-'),item['version'];record=metadata[key];matches=[value for value in record['data']['urls'] if value['filename']==item['filename']];assert len(matches)==1,(key,item['filename']);found=matches[0];assert not found['yanked'],'Exact reference archive was yanked: '+str(key)
            if 'sha256' in item:assert item['sha256']==found['digests']['sha256'],'Installed reference archive identity differs from official PyPI'
            else:item['sha256']=found['digests']['sha256']
            item.update(url=found['url'],bytes=found['size'],metadata_url=record['url'],metadata_pin=record['metadata_pin']);assert found['url'].startswith('https://files.pythonhosted.org/')
            if (key,item['filename']) in files:assert pin(files[(key,item['filename'])])=={k:item[k] for k in ['bytes','sha256']}
        for edition in editions.values():
            for item in edition['packages']:resolve(item)
        for item in bootstrap:resolve(item)
        lock=dict(schema_version=1,bootstrap=bootstrap,editions=editions,scope='Exact Linux x86_64 Python3.12/Hail139 and Python3.10/Hail130 reference archives. Download/install only where explicitly permitted; no analysis installs, source/runtime substitution or cloud execution.');save(out/'dependency-locks.json',lock);state.update(status='PASS_EXACT_OFFICIAL_HAIL_REFERENCE_DEPENDENCY_ARCHIVE_LOCKS_ONLY',distinct_package_versions=len(metadata),edition_archive_counts={key:len(value['packages']) for key,value in editions.items()},lock=pin(out/'dependency-locks.json'));print(state['status'],state['distinct_package_versions'],state['edition_archive_counts'])
    except Exception as error:state.update(error=repr(error),required_action='No new installation or dependent analysis is admitted. Preserve metadata and reference archives, diagnose the named unavailable/yanked/changed exact package artifact and retry intake in NEW paths. Use an already validated matching archive cache or keep analysis skipped; never silently choose another version or hash.');(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(error)+'\n',encoding='utf8');print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable reference archive identity checks')
    raise SystemExit(main())
