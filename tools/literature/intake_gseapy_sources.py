# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the four paper-reported GSEApy wheels and original sources from official PyPI records."""
import argparse,hashlib,json,sys,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

VERSIONS=['1.0.4','1.1.2','1.1.3','1.1.8']

def fetch(url):
    assert url.startswith(('https://pypi.org/','https://files.pythonhosted.org/'))
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1.0'}),timeout=90) as response:return response.read()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime') and not out.exists();out.mkdir(parents=True)
    result=dict(status='FAIL2_GSEAPY_SOURCE_INTAKE',versions={},implementation_credit=False,whole_goal_complete=False)
    try:
        for version in VERSIONS:
            home=out/version;home.mkdir();url='https://pypi.org/pypi/gseapy/'+version+'/json';raw=fetch(url);(home/'pypi.json').write_bytes(raw);record=json.loads(raw)
            wheels=[f for f in record['urls'] if f['filename'].endswith('.whl') and 'cp311' in f['filename'] and 'manylinux' in f['filename'] and 'x86_64' in f['filename']]
            assert len(wheels)==1,(version,[f['filename'] for f in wheels]);wheel=wheels[0];body=fetch(wheel['url']);assert hashlib.sha256(body).hexdigest()==wheel['digests']['sha256'] and len(body)==wheel['size'];archive=home/wheel['filename'];archive.write_bytes(body)
            members={}
            with zipfile.ZipFile(archive) as z:
                assert len(z.namelist())==len(set(z.namelist()))
                for name in z.namelist():
                    posix=PurePosixPath(name);assert not posix.is_absolute() and '..' not in posix.parts and '\\' not in name
                    if name.endswith('/') or not(name.startswith('gseapy/') or '.dist-info/' in name):continue
                    target=home/'wheel-source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name));members[name]=pin(target)
            result['versions'][version]=dict(pypi_url=url,metadata=pin(home/'pypi.json'),wheel=dict(filename=wheel['filename'],url=wheel['url'],**pin(archive)),members=members,reported_license=record['info'].get('license'),project_urls=record['info'].get('project_urls'))
            print('Captured original',version,wheel['filename'],len(members),'members',flush=True)
        result['status']='passed-four-paper-reported-official-GSEApy-source-wheels'
    except Exception as error:
        result['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    result['source_pins']={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())};save(out/'SOURCE.json',result)
    return 0 if result['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official archive checks')
    raise SystemExit(main())
