# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the official GPL Presto1.0.0 tag for CellChat's explicit fast Wilcoxon profile."""
import argparse,io,json,tarfile,traceback,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

COMMIT='7636b3d0465c468c35853f82f1717d3a64b3c8f6'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    state=dict(status='SKIP3_CELLCHAT_PRESTO_SOURCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_download,'Download is not authorized; skip dependent fast-Wilcoxon analysis'
        metadata=ROOT/'build/literature-review/cellchat-runtime/presto-metadata-1/tags.json';tags=json.loads(metadata.read_bytes());assert any(t['name']=='1.0.0' and t['commit']['sha']==COMMIT for t in tags)
        url='https://codeload.github.com/immunogenomics/presto/tar.gz/'+COMMIT
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-CellChat-presto-reference/1.0'})
        with urllib.request.urlopen(request,timeout=90) as response:raw=response.read(67108865);receipt=dict(url=url,resolved_url=response.url,status=response.status)
        assert len(raw)<=67108864;archive=out/'presto-original.tar.gz';archive.write_bytes(raw);source=out/'source';source.mkdir();members={}
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
            infos=tar.getmembers();assert len(infos)<=10000 and sum(m.size for m in infos)<=268435456
            for member in infos:
                if member.isdir():continue
                assert member.isfile() and not member.issym() and not member.islnk() and member.size<=33554432
                prefix='presto-'+COMMIT+'/';assert member.name.startswith(prefix);relative=member.name[len(prefix):];path=PurePosixPath(relative)
                assert relative and relative==path.as_posix() and '..' not in path.parts and not path.is_absolute() and not any(c in relative for c in '\\:\0\r\n') and relative not in members
                file=source/relative;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(tar.extractfile(member).read());members[relative]=pin(file)
        text=(source/'DESCRIPTION').read_text(encoding='utf8');assert '\nVersion: 1.0.0\n' in text and '\nLicense: GPL-3\n' in text
        save(out/'original-members.json',members);save(out/'download.json',dict(**receipt,**pin(archive)))
        state.update(status='PASS_ORIGINAL_PRESTO_1_0_0_SOURCE_ONLY',commit=COMMIT,version='1.0.0',repository='immunogenomics/presto',archive=pin(archive),members=pin(out/'original-members.json'),description=pin(source/'DESCRIPTION'),metadata=pin(metadata),source=pin(Path(__file__)))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable upstream tag/archive integrity checks')
    raise SystemExit(main())
