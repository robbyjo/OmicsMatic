# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect official GENESIS source/Windows binary availability and license before a private runtime setup; no installation or analysis."""
import argparse
import datetime
import html.parser
import json
import re
import tarfile
import traceback
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'
ACTION=('GENESIS analyses remain unimplemented/unadmitted by this intake. If the required exact runtime or permitted installation '
    'cannot be supplied, prominently skip dependent PC-AiR/PC-Relate/model analyses and retain the named cause. '
    'Resolve prerequisites in a NEW private library only where permitted; do not update shared R libraries, '
    'substitute ordinary PCA or a KING coefficient matrix for GENESIS, or claim a current release reproduces reported2.16.1/2.26.0 paper versions.')


class Links(html.parser.HTMLParser):
    def __init__(self):super().__init__();self.links=[]
    def handle_starttag(self,tag,attrs):
        if tag=='a':self.links.extend(value for key,value in attrs if key=='href')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='SKIP3_GENESIS_OFFICIAL_RUNTIME_INTAKE_UNAVAILABLE',installation_performed=False,analysis_performed=False,
        new_catalog_credit=False,whole_goal_complete=False,required_action=ACTION);receipts=[];code=3
    try:
        assert a.allow_download,'Download permission is required; no dependency substitution'
        def download(url,name,maximum):
            target=out/name;request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-official-GENESIS-intake'})
            with urllib.request.urlopen(request,timeout=90) as response,target.open('xb') as stream:
                count=0
                for raw in iter(lambda:response.read(1048576),b''):
                    count+=len(raw);assert count<=maximum;stream.write(raw)
                receipt=dict(url=url,final_url=response.geturl(),status=response.status,fetched_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),**pin(target))
            receipts.append(receipt);save(out/(name+'.receipt.json'),receipt);return target,receipt
        page,page_receipt=download('https://bioconductor.org/packages/release/bioc/html/GENESIS.html','GENESIS.html',2000000)
        links=Links();links.feed(page.read_text(encoding='utf8'))
        binaries=sorted({urllib.parse.urljoin(page_receipt['final_url'],url) for url in links.links if re.search(r'/GENESIS_[0-9.]+\.zip$',url)})
        sources=sorted({urllib.parse.urljoin(page_receipt['final_url'],url) for url in links.links if re.search(r'/GENESIS_[0-9.]+\.tar\.gz$',url)})
        assert len(binaries)==len(sources)==1,(binaries,sources)
        version=re.search(r'GENESIS_([0-9.]+)\.zip$',binaries[0]).group(1)
        assert sources[0].endswith('/GENESIS_'+version+'.tar.gz')
        binary,_=download(binaries[0],'GENESIS_'+version+'.zip',100000000)
        with zipfile.ZipFile(binary) as archive:
            assert archive.testzip() is None
            description=archive.read('GENESIS/DESCRIPTION').decode('utf8')
        assert re.search(r'(?m)^Version: '+re.escape(version)+r'\s*$',description)
        assert re.search(r'(?m)^License: GPL-3\s*$',description),'Review the named changed license before using a different distribution'
        assert re.search(r'(?m)^Built: R 4\.6\.',description),'Require compatible Windows R4.6 binary; do not silently compile/substitute'
        (out/'binary-DESCRIPTION').write_text(description,encoding='utf8',newline='\n')
        source,_=download(sources[0],'GENESIS_'+version+'.tar.gz',100000000)
        expanded=out/'original-source';expanded.mkdir()
        with tarfile.open(source,'r:gz') as archive:
            total=0;members=archive.getmembers();assert len(members)<=20000
            for item in members:
                path=PurePosixPath(item.name);assert not path.is_absolute() and '..' not in path.parts and not any(c in item.name for c in '\\:\0')
                assert path.parts[0]=='GENESIS' and (item.isdir() or item.isfile()) and item.size<=100000000
                total+=item.size;assert total<=300000000
                target=expanded.joinpath(*path.parts);assert target.resolve().is_relative_to(expanded.resolve())
                if item.isdir():target.mkdir(parents=True,exist_ok=True)
                else:
                    target.parent.mkdir(parents=True,exist_ok=True)
                    with archive.extractfile(item) as stream:target.write_bytes(stream.read())
        source_description=(expanded/'GENESIS/DESCRIPTION').read_text(encoding='utf8')
        assert re.search(r'(?m)^Version: '+re.escape(version)+r'\s*$',source_description) and re.search(r'(?m)^License: GPL-3\s*$',source_description)
        state.update(status='PASS_OFFICIAL_GENESIS_SOURCE_AND_WINDOWS_BINARY_INTAKE_ONLY',version=version,license='GPL-3',receipts=receipts,
            binary_description=pin(out/'binary-DESCRIPTION'),source_files={f.relative_to(expanded).as_posix():pin(f) for f in sorted(expanded.rglob('*')) if f.is_file()},producer=pin(Path(__file__)))
        code=0
    except Exception as error:
        state.update(cause=repr(error),receipts=receipts);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('version',''),state.get('cause','')[:2500],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official version/license/archive checks')
    raise SystemExit(main())
