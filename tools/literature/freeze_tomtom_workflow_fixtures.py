# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze both complete actual public Tomtom bundles, retaining their original producer paths."""
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    source=ROOT/'build/literature-review/meme-runtime/tomtom-workflow-development-1/matrix-1'
    proof=json.loads((source/'COMPLETED.json').read_bytes());assert proof['complete_public_workflows']==2 and len(proof['portable_actual_main_verifications'])==6
    captured=source/'captured';inventory=json.loads((source/'NATIVE-INVENTORY.json').read_bytes());actual={}
    for p in captured.rglob('*'):
        mode=p.lstat().st_mode
        if stat.S_ISDIR(mode):value=dict(kind='directory')
        else:assert stat.S_ISREG(mode);value=dict(kind='file',**pin(p))
        actual[p.relative_to(captured).as_posix()]=value
    assert set(actual)==set(inventory)
    for name,value in inventory.items():assert {k:v for k,v in value.items() if k!='mode'}==actual[name]
    files={};directories=[]
    for label in ['text','full']:
        folder=captured/'outputs'/label;assert (folder/'COMPLETED.json').is_file()
        for p in folder.rglob('*'):
            name=label+'/'+p.relative_to(folder).as_posix()
            if p.is_dir():directories.append(name)
            else:files[name]=p.read_bytes()
    files['EXPECTED.json']=(json.dumps(dict(schema_version=1,directories=sorted(directories),source_completion=pin(source/'COMPLETED.json'),scope='Two complete actual public original5.5.9 workflows; producer paths remain opaque. Copied evidence creates no current-host ticket or biological certificate.'),indent=2)+'\n').encode()
    out=ROOT/'src/test/resources/org/jlinalg/cli/tomtom-workflows';assert not out.exists();out.mkdir(parents=True);archive=out/'original-public-workflows.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    (out/'manifest.json').write_text(json.dumps(dict(archive=pin(archive),entries=len(files),complete_public_workflows=2,source_completion=pin(source/'COMPLETED.json')),indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n');print(json.dumps(dict(archive=pin(archive),entries=len(files),directories=len(directories))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete public workflow evidence checks')
    main()
