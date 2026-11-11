# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture named official SMR executable archives and repository history; no installation or analysis."""
import argparse,concurrent.futures,json,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

SOURCES=[('windows-1.3.1','https://yanglab.westlake.edu.cn/software/smr/download/smr-1.3.1-win-x86_64.zip'),('linux-1.3.1','https://yanglab.westlake.edu.cn/software/smr/download/smr-1.3.1-linux-x86_64.zip'),('linux-1.4.2','https://yanglab.westlake.edu.cn/software/smr/download/smr-1.4.2-linux-x86_64.zip'),('legacy-windows-1.3.1','https://cnsgenomics.com/software/smr/download/smr-1.3.1-win-x86_64.zip')]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);results=[]
    def fetch(item):
        identifier,url=item;home=out/identifier;home.mkdir();state=dict(id=identifier,url=url,status='SKIP3_OFFICIAL_RUNTIME_UNAVAILABLE',analysis_admitted=False)
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1'}),timeout=90) as response:
                raw=response.read(100000001);assert len(raw)<=100000000;path=home/'original.zip';path.write_bytes(raw);state.update(download=pin(path),response_status=response.status,final_url=response.url,headers={k:response.headers[k] for k in ['Content-Type','Content-Length','ETag','Last-Modified','Date'] if k in response.headers})
            members={};target=home/'original';target.mkdir()
            with zipfile.ZipFile(path) as z:
                assert len(z.namelist())==len(set(z.namelist()))
                for entry in z.infolist():
                    name=PurePosixPath(entry.filename);assert not name.is_absolute() and '..' not in name.parts and '\\' not in entry.filename and ':' not in entry.filename
                    if entry.is_dir():continue
                    assert entry.file_size<=200000000;file=target/entry.filename;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(z.read(entry));members[entry.filename]=pin(file)
            state.update(status='captured-official-runtime-archive-not-executed',members=members,license_claim='Official SMR download page states executable files are MIT; preserve bundled notices and source GPL-2 separately.')
        except Exception as error:state['detail']=str(error)
        save(home/'COMPLETED.json',state);return state
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(fetch,SOURCES))
    url='https://api.github.com/repos/JianYang-Lab/SMR/commits?per_page=100';history=None
    try:
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1','Accept':'application/vnd.github+json'}),timeout=60) as r:raw=r.read(5000001);assert len(raw)<=5000000;(out/'commits.json').write_bytes(raw);history=pin(out/'commits.json')
    except Exception as error:save(out/'history-failure.json',dict(url=url,detail=str(error)))
    save(out/'COMPLETED.json',dict(status='captured-official-download-attempts-not-adapter-validation',attempts=results,history=history,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False));print(json.dumps([dict(id=r['id'],status=r['status'],detail=r.get('detail'),download=r.get('download')) for r in results]))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable archive/source checks')
    main()
