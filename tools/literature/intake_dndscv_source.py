# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture a resolved official dndscv source commit, full package and per-file hashes; no install/analysis."""
import argparse,datetime,hashlib,io,json,tarfile,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/dndscv-runtime') and not out.exists();out.mkdir(parents=True);helper=Path(__file__).resolve();(out/'intake-source.py').write_bytes(helper.read_bytes());state=dict(status='SKIP3_DNDSCV_SOURCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,source_pins={helper.relative_to(ROOT).as_posix():pin(helper)},required_action='Preserve this attempt and its downloaded bytes. If downloads are permitted, obtain the named official exact commit/archive and retry NEW paths. Otherwise skip dependent original dndscv analysis. No source/version substitution, installation or scientific result is admitted by intake.');save(out/'STARTED.json',dict(time_UTC=datetime.datetime.now(datetime.timezone.utc).isoformat(),official_repository='https://github.com/im3sanger/dndscv',source_pins=state['source_pins']))
    def fetch(url,name,limit):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
        with urllib.request.urlopen(request,timeout=90) as response:
            raw=response.read(limit+1);assert len(raw)<=limit,'Download exceeds declared capacity';(out/name).write_bytes(raw);save(out/(name+'.http.json'),dict(request_url=url,final_url=response.url,status=response.status,headers={k:response.headers[k] for k in ['Content-Type','Content-Length','ETag','Last-Modified','Date'] if k in response.headers},file=pin(out/name)))
        return raw
    try:
        commit=json.loads(fetch('https://api.github.com/repos/im3sanger/dndscv/commits/master','commit.json',2000000));sha=commit['sha'];assert len(sha)==40 and all(c in '0123456789abcdef' for c in sha);archive=fetch('https://codeload.github.com/im3sanger/dndscv/tar.gz/'+sha,'source.tar.gz',200000000);members={};source=out/'source';source.mkdir();total=0
        with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
            for member in tar:
                path=PurePosixPath(member.name);assert path.parts[0]=='dndscv-'+sha and not path.is_absolute() and '..' not in path.parts
                if member.isdir():continue
                assert member.isfile() and len(path.parts)>1 and member.size<=200000000;total+=member.size;assert total<=600000000;name=PurePosixPath(*path.parts[1:]).as_posix();assert name not in members;target=source/name;target.parent.mkdir(parents=True,exist_ok=True);raw=tar.extractfile(member).read();assert len(raw)==member.size;target.write_bytes(raw);members[name]=pin(target)
        assert all(n in members for n in ['DESCRIPTION','NAMESPACE','LICENSE.txt','R/dndscv.R']) and b'License: GPL-3' in (source/'DESCRIPTION').read_bytes();save(out/'SOURCE.json',dict(schema_version=1,official_repository='https://github.com/im3sanger/dndscv',commit=sha,commit_metadata=pin(out/'commit.json'),archive=pin(out/'source.tar.gz'),source_members=members,license='GPL-3',paper_commit_identified=False,version_label_alone_identifies_source=False));state.update(status='captured-official-dndscv-source-not-analysis',commit=sha,members=len(members),source_receipt=pin(out/'SOURCE.json'))
    except Exception as error:state['detail']=str(error)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+state['required_action']+'\n',encoding='utf8',newline='\n');print(json.dumps({k:v for k,v in state.items() if k!='source_pins'}));return 0 if state['status']=='captured-official-dndscv-source-not-analysis' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source checks')
    raise SystemExit(main())
