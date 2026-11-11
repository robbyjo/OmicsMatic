# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official Rsubread source/binary archives and safely inspect their complete file populations."""
import argparse,concurrent.futures,hashlib,io,json,tarfile,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_RSUBREAD_SOURCE_INTAKE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False);base='https://bioconductor.org/packages/3.23/bioc/'
    try:
        urls={'source':base+'src/contrib/Rsubread_2.26.0.tar.gz','windows':base+'bin/windows/contrib/4.6/Rsubread_2.26.0.zip'}
        def fetch(item):
            kind,url=item;home=out/kind;home.mkdir()
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-intake'}),timeout=180) as response:raw=response.read(67108865);assert len(raw)<=67108864;final=response.url
            archive=home/Path(url).name;archive.write_bytes(raw);save(home/'download.json',dict(requested_url=url,final_url=final,**pin(archive)));members={};root=home/'unpacked';root.mkdir()
            def write(name,content):
                path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and len(path.parts)>1 and path.parts[0]=='Rsubread' and '\\' not in name and ':' not in name;relative=PurePosixPath(*path.parts[1:]).as_posix();assert relative not in members and len(content)<67108864;file=root/relative;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(content);members[relative]=pin(file)
            if kind=='source':
                with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archivefile:
                    for member in archivefile.getmembers():
                        if member.isdir():continue
                        assert member.isfile();content=archivefile.extractfile(member).read();assert len(content)==member.size;write(member.name,content)
            else:
                with zipfile.ZipFile(io.BytesIO(raw)) as archivefile:
                    assert archivefile.testzip() is None
                    for member in archivefile.infolist():
                        if member.is_dir():continue
                        assert (member.external_attr>>16)&0o170000!=0o120000;write(member.filename,archivefile.read(member))
            description=(root/'DESCRIPTION').read_text(encoding='utf8');assert 'Package: Rsubread\n' in description and 'Version: 2.26.0\n' in description and 'License: GPL (>=3)' in description
            if kind=='source':assert 'R/featureCounts.R' in members and 'src/readSummary.c' in members
            else:assert 'libs/x64/Rsubread.dll' in members
            record=dict(version='2.26.0',archive=dict(path=archive.relative_to(ROOT).as_posix(),**pin(archive)),download=pin(home/'download.json'),members=members,license_declaration='DESCRIPTION: GPL (>=3)',namespace_patched=False);save(home/'SOURCE.json',record);return kind,record
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:records=dict(pool.map(fetch,urls.items()))
        state.update(status='passed-official-Rsubread2260-source-and-Windows-binary-intake-only',archives=records,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete archive/version/license checks')
    raise SystemExit(main())
