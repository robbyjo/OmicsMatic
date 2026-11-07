# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official SMR documentation, repository metadata and exact source; no install or analysis."""
import argparse,datetime,io,json,tarfile,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--commit');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();assert a.commit is None or (len(a.commit)==40 and all(c in '0123456789abcdef' for c in a.commit));out.mkdir(parents=True);helper=Path(__file__).resolve();(out/'intake-source.py').write_bytes(helper.read_bytes());state=dict(status='SKIP3_SMR_SOURCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,source_pins={helper.relative_to(ROOT).as_posix():pin(helper)},whole_goal_complete=False,required_action='Preserve downloads and HTTP receipts. Obtain the exact permitted official source/runtime or skip dependent SMR/HEIDI analysis. Do not substitute versions or treat intake as an implementation.');save(out/'STARTED.json',dict(time_UTC=datetime.datetime.now(datetime.timezone.utc).isoformat(),source_pins=state['source_pins'],explicit_commit=a.commit))
    def fetch(url,name,limit):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'}),timeout=90) as response:
            raw=response.read(limit+1);assert len(raw)<=limit;(out/name).write_bytes(raw);save(out/(name+'.http.json'),dict(request_url=url,final_url=response.url,status=response.status,file=pin(out/name),headers={k:response.headers[k] for k in ['Content-Type','Content-Length','ETag','Last-Modified','Date'] if k in response.headers}));return raw
    try:
        metadata=json.loads(fetch('https://api.github.com/repos/JianYang-Lab/SMR/commits/'+(a.commit or 'main'),'commit.json',2000000));commit=metadata['sha'];assert len(commit)==40 and (not a.commit or commit==a.commit);fetch('https://api.github.com/repos/JianYang-Lab/SMR/tags?per_page=100','tags.json',2000000);archive=fetch('https://codeload.github.com/JianYang-Lab/SMR/tar.gz/'+commit,'source.tar.gz',100000000);source=out/'source';source.mkdir();members={};total=0
        with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
            for entry in tar:
                path=PurePosixPath(entry.name);assert path.parts[0]=='SMR-'+commit and not path.is_absolute() and '..' not in path.parts
                if entry.isdir():continue
                assert entry.isfile() and len(path.parts)>1 and entry.size<=100000000;total+=entry.size;assert total<=500000000;name=PurePosixPath(*path.parts[1:]).as_posix();assert name not in members;blob=tar.extractfile(entry).read();assert len(blob)==entry.size;target=source/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob);members[name]=pin(target)
        assert 'LICENSE' in members and 'CMakeLists.txt' in members;save(out/'SOURCE.json',dict(schema_version=1,official_repository='https://github.com/JianYang-Lab/SMR',commit=commit,commit_metadata=pin(out/'commit.json'),archive=pin(out/'source.tar.gz'),source_members=members,source_license='GPL-2.0 repository license; inspect per-file notices before implementation',paper_commit_identified=False));state.update(status='captured-official-SMR-source-not-analysis',commit=commit,members=len(members),source_receipt=pin(out/'SOURCE.json'))
        try:fetch('https://yanglab.westlake.edu.cn/software/smr/','official-manual.html',10000000);state['official_manual']=pin(out/'official-manual.html')
        except Exception as error:state['manual_warning']=str(error)
    except Exception as error:state['detail']=str(error)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+state['required_action']+'\n',encoding='utf8',newline='\n');print(json.dumps({k:v for k,v in state.items() if k!='source_pins'}));return 0 if state['status']=='captured-official-SMR-source-not-analysis' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official source checks')
    raise SystemExit(main())
