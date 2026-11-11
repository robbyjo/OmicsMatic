# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original annotation/TSS outputs, indexed and plain inputs and implementation sources."""
import gzip
import hashlib
import json
from pathlib import Path
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
DEST=ROOT/'src/test/resources/org/jlinalg/sequence/signac-annotations'
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def main():
    assert not DEST.exists();original=BASE/'annotation-original-2';record=json.loads((original/'COMPLETED.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items());data=json.loads((original/'cases.json').read_bytes());files={}
    for name in ['cases.json','original.json','original.stdout','original.stderr','original.process.json','session-info.txt','COMPLETED.json']:files[name]=(original/name).read_bytes()
    for file in data['files']:
        name=file['id'];raw=(original/(name+'.tsv')).read_bytes();files['input/'+name+'.tsv']=raw;files['input/'+name+'.bgz']=(original/(name+'.tsv.gz')).read_bytes();files['input/'+name+'.bgz.tbi']=(original/(name+'.tsv.gz.tbi')).read_bytes();files['input/'+name+'.gz']=gzip.compress(raw,mtime=0)
    source=BASE/'source-intake-1/1.17.1/source/Signac/R'
    for name in ['region-enrichment.R','utilities.R']:files['original-source/'+name]=(source/name).read_bytes()
    files['original-source/MIT-LICENSE.txt']=(ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi/MIT-LICENSE.txt').read_bytes()
    sources=dict(record['source_pins']);sources[Path(__file__).relative_to(ROOT).as_posix()]=pin(Path(__file__))
    for name in sources:files['source-snapshot/'+name]=(ROOT/name).read_bytes()
    DEST.mkdir(parents=True);(DEST/'.gitattributes').write_text('*.zip -text\n*.json -text\n',encoding='utf8',newline='\n');archive=DEST/'original-controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    capture=dict(status='frozen-full-original-Signac-annotation-controls',source_pins=sources,archive=pin(archive),members=len(files),tss_cases=50,gene_cases=26,historical_binaries_executed=False)
    (DEST/'CAPTURE.json').write_text(json.dumps(capture,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(capture))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable fixture provenance checks')
    main()
