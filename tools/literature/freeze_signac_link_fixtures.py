# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original LinkPeaks results and exact seeded background replay as native test evidence."""
import hashlib
import json
from pathlib import Path
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
DEST=ROOT/'src/test/resources/org/jlinalg/sequence/signac-links'
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def main():
    assert not DEST.exists();original=BASE/'links-original-4';record=json.loads((original/'COMPLETED.json').read_bytes());assert record['successful_controls']==44 and record['expected_source_failures']==16 and all(pin(ROOT/n)==v for n,v in record['source_pins'].items());files={}
    for name in ['cases.json','original.json','COMPLETED.json','original.process.json','original.stdout','original.stderr','session-info.txt']:files[name]=(original/name).read_bytes()
    sources=dict(record['source_pins']);sources[Path(__file__).relative_to(ROOT).as_posix()]=pin(Path(__file__))
    for name in sources:files['source-snapshot/'+name]=(ROOT/name).read_bytes()
    DEST.mkdir(parents=True);(DEST/'.gitattributes').write_text('*.zip -text\n*.json -text\n',encoding='utf8',newline='\n');archive=DEST/'original-controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    capture=dict(status='frozen-full-original-LinkPeaks-and-literal-source-controls',source_pins=sources,archive=pin(archive),members=len(files),original_controls=60,successful_original_controls=44,expected_source_failures=16,full_current_package_executed=True,historical_packages_executed=False)
    (DEST/'CAPTURE.json').write_text(json.dumps(capture,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(dict(status=capture['status'],archive=capture['archive'],members=len(files))))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original LinkPeaks fixture checks')
    main()
