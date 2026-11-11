# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete prior original process/input/report captures for portable workflow-component tests."""
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    source=ROOT/'build/literature-review/meme-runtime/tomtom-prepared-development-1/matrix-1';proof=json.loads((source/'COMPLETED.json').read_bytes());assert proof['native_fits']==49 and proof['full_report_fits']==20 and proof['text_fits']==29
    original=source/'captured';inventory=json.loads((source/'NATIVE-INVENTORY.json').read_bytes());actual={}
    for p in original.rglob('*'):
        mode=p.lstat().st_mode
        if stat.S_ISDIR(mode):value=dict(kind='directory')
        else:assert stat.S_ISREG(mode);value=dict(kind='file',**pin(p))
        actual[p.relative_to(original).as_posix()]=value
    assert set(actual)==set(inventory)
    for name,value in inventory.items():assert {k:v for k,v in value.items() if k!='mode'}==actual[name]
    root=json.loads((original/'COMPLETED.json').read_bytes());cases=[];files={}
    for row in root['protocols']:
        name=row['id'];folder=original/name;assert (folder/'CONTROL-COMPLETED.json').is_file()
        query=row['command'][-2];producer=query[:query.rindex('/inputs/')];cases.append(dict(id=name,mode=row['mode'],producer_input_root=producer,command=row['command'],pairs=row['complete_declared_pairs'],rows=row['complete_reported_rows']))
        for sub in ['prepared','process']:
            for p in (folder/sub).rglob('*'):
                if p.is_file():files[name+'/'+sub+'/'+p.relative_to(folder/sub).as_posix()]=p.read_bytes()
    files['EXPECTED.json']=(json.dumps(dict(schema_version=1,cases=cases,source_completion=pin(source/'COMPLETED.json'),scope='Complete original49 controlled input/process/output families, not user workflow admission or current-host tickets. Every original HTML scientific display payload and unchanged captured input ledger is available independently.'),indent=2)+'\n').encode()
    out=ROOT/'src/test/resources/org/jlinalg/cli/tomtom-reports';assert not out.exists();out.mkdir(parents=True);archive=out/'original-prepared-reports.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    (out/'manifest.json').write_text(json.dumps(dict(archive=pin(archive),entries=len(files),cases=49,full_reports=20,text_reports=29,scope='Whole selected unchanged original process/report/input families. Original registry/native health/user workflow admission stays separate.'),indent=2)+'\n',encoding='utf-8',newline='\n');(out/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n');print(json.dumps(dict(archive=pin(archive),entries=len(files),cases=49)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original process/report fixture checks')
    main()
