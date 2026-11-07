# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original SMR CMake dependency editions in a NEW private source tree; no system installs."""
import argparse,io,json,tarfile,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

EDITIONS=[('eigen','eigen-mirror/eigen','3.4.0'),('zlib','madler/zlib','v1.3.1'),('fmt','fmtlib/fmt','12.1.0'),('spdlog','gabime/spdlog','v1.15.3')]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);reports=[];state=dict(status='SKIP3_SMR_BUILD_DEPENDENCIES_UNAVAILABLE',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},analysis_admitted=False,implementation_credit=False,required_action='If dependency downloads are permitted, retry NEW paths with --allow-install. Otherwise skip dependent compilation/analysis. All original source archives, licenses and byte receipts remain; no shared or system installation is modified. Explicit source editions do not identify any historical paper environment.')
    def fetch(url,path,limit):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'}),timeout=90) as response:
            raw=response.read(limit+1);assert len(raw)<=limit;path.write_bytes(raw);save(path.with_name(path.name+'.http.json'),dict(url=url,final_url=response.url,status=response.status,file=pin(path)));return raw
    try:
        assert a.allow_install,'Dependency preparation not enabled'
        for name,repository,tag in EDITIONS:
            home=out/name;home.mkdir();metadata=json.loads(fetch('https://api.github.com/repos/'+repository+'/commits/'+tag,home/'commit.json',2000000));commit=metadata['sha'];archive=fetch('https://codeload.github.com/'+repository+'/tar.gz/'+commit,home/'source.tar.gz',50000000);source=home/'source';source.mkdir();members={};total=0
            with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
                for entry in tar:
                    path=PurePosixPath(entry.name);assert not path.is_absolute() and '..' not in path.parts
                    if entry.isdir():continue
                    assert entry.isfile() and len(path.parts)>1 and entry.size<=50000000;total+=entry.size;assert total<=250000000;relative=PurePosixPath(*path.parts[1:]).as_posix();assert relative not in members;target=source/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(entry).read());members[relative]=pin(target)
            receipt=dict(name=name,repository=repository,tag=tag,commit=commit,archive=pin(home/'source.tar.gz'),source_members=members);save(home/'SOURCE.json',receipt);reports.append(dict(name=name,source_receipt=pin(home/'SOURCE.json'),commit=commit,tag=tag,members=len(members)));print('Captured',name,tag,len(members),'original files',flush=True)
        state.update(status='prepared-original-private-SMR-build-dependencies-only',dependencies=reports)
    except Exception as error:state.update(detail=str(error),dependencies=reports)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+state['required_action']+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('detail',''));return 0 if state['status']=='prepared-original-private-SMR-build-dependencies-only' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/dependency checks')
    raise SystemExit(main())
