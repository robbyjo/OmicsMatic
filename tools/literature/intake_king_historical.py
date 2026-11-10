# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Privately retain original historical KING assets linked by the author's history page; no analysis."""
import argparse
import datetime
import json
import shutil
import tarfile
import traceback
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state=dict(status='SKIP3_HISTORICAL_KING_UNAVAILABLE',analysis_performed=False,new_catalog_credit=False,
               whole_goal_complete=False,original_redistribution_planned=False)
    records=[]
    try:
        history=BASE/'king-source-intake-1/history.shtml'
        text=history.read_text(encoding='utf8')
        for name in ['Linux-king227.tar.gz','Windows-king227.zip','KING2.2.7code.tar.gz',
                     'Linux-king20.tar.gz','Ubuntu-king20.tar.gz','KING2.0code.tar.gz']:
            relative='executables/'+name
            assert relative in text,'Historical asset must be linked in captured original history'
            url='https://www.kingrelatedness.com/'+relative
            request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-private-research-reference'})
            with urllib.request.urlopen(request,timeout=90) as response,(out/name).open('xb') as target:
                total=0
                for chunk in iter(lambda:response.read(1048576),b''):
                    total+=len(chunk);assert total<=100000000
                    target.write(chunk)
                receipt=dict(url=url,final_url=response.geturl(),status=response.status,
                             fetched_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),**pin(out/name))
            save(out/(name+'.receipt.json'),receipt);records.append(receipt)
            folder=out/(name.removesuffix('.tar.gz').removesuffix('.zip')+'-original');folder.mkdir()
            expanded=0
            def member_path(name,size):
                nonlocal expanded
                relative=PurePosixPath(name)
                assert not relative.is_absolute() and '..' not in relative.parts and not any(c in name for c in '\\:\0')
                expanded+=size;assert size>=0 and expanded<=1000000000
                path=folder/Path(*relative.parts);path.parent.mkdir(parents=True,exist_ok=True)
                return path
            if name.endswith('.zip'):
                with zipfile.ZipFile(out/name) as archive:
                    assert len(archive.infolist())<=4000
                    for m in archive.infolist():
                        if m.is_dir():continue
                        assert (m.external_attr >> 16)&0o170000 != 0o120000
                        with archive.open(m) as source,member_path(m.filename,m.file_size).open('xb') as dest:shutil.copyfileobj(source,dest)
            else:
                with tarfile.open(out/name) as archive:
                    assert len(archive.getmembers())<=4000
                    for m in archive.getmembers():
                        if m.isdir():continue
                        assert m.isfile()
                        with archive.extractfile(m) as source,member_path(m.name,m.size).open('xb') as dest:shutil.copyfileobj(source,dest)
            save(out/(name+'.members.json'),{f.relative_to(folder).as_posix():pin(f) for f in sorted(folder.rglob('*')) if f.is_file()})
            print(name,'private original bytes',receipt['bytes'],flush=True)
        state.update(status='PASS_PRIVATE_HISTORICAL_KING227_20_INTAKE_ONLY',records=records,history_pin=pin(history),producer=pin(Path(__file__)))
    except Exception as error:
        state.update(cause=repr(error),records=records)
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    state['required_action']='Keep all original assets private. Admit no historical analysis from intake alone. If prerequisites cannot be supplied or installation is forbidden, skip that exact dependent historical analysis; do not silently substitute2.3.2.'
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# PRIVATE ORIGINAL SOFTWARE - DO NOT REDISTRIBUTE\n\n'+state['status']+'\n\n'+state['required_action']+'\n',encoding='utf8')
    print(state['status'],state.get('cause',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable archive preservation and private-source controls')
    raise SystemExit(main())
