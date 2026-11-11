# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original source/build receipts for portable native-admission evidence tests."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    source=ROOT/'build/literature-review/meme-runtime/linux-build-3';out=ROOT/'src/test/resources/org/jlinalg/cli/meme-build-evidence';assert not out.exists()
    proof=json.loads((source/'COMPLETED.json').read_bytes());assert proof['status']=='built-original-Linux-MEME-Suite-reference-not-scientifically-admitted' and proof['unchanged_original_C_and_H']==615
    for step in proof['steps']:assert step['exit_code']==0 and not step['timed_out'] and pin(source/(step['name']+'.log'))==step['log_pin']
    files={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file()};assert len(files)<32 and all(not p.is_symlink() for p in files.values())
    out.mkdir(parents=True);archive=out/'original-build-evidence.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,path.read_bytes())
    (out/'manifest.json').write_text(json.dumps(dict(archive=pin(archive),files={name:pin(p) for name,p in files.items()},scope='Complete unchanged original source/build receipt and logs. No binaries, native current-host admission or user fit. Producer Linux paths remain opaque strings during portable evidence audit.'),indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n');print(json.dumps(dict(archive=pin(archive),files=len(files))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original build-log checks')
    main()
