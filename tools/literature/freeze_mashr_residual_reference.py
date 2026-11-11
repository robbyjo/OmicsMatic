#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original residual/singleton references, never native results."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]
TARGET=ROOT/'src/test/resources/org/jlinalg/cli/mashr-native/residual-reference.zip'


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    if not __debug__:raise RuntimeError('Never disable provenance checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,required=True);args=parser.parse_args()
    source=args.source.resolve();assert source.is_relative_to((ROOT/'build/literature-review/mashr-runtime').resolve())
    assert not TARGET.exists(),'Preserve the existing reference; do not overwrite it'
    record=json.loads((source/'REFERENCE.json').read_text())
    assert record['status']=='captured-original-residual-and-singleton-primitives' and record['source_workflows']==17
    assert record['parent_fixture']==pin(ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip')
    expected=set(record['files'])|{'REFERENCE.json'}
    assert {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}==expected
    for name,value in record['files'].items():assert pin(source/name)==value,name
    assert json.loads((source/'runtime-before.json').read_text())==json.loads((source/'runtime-after.json').read_text())
    assert json.loads((source/'process.json').read_text())['exit_code']==0
    assert len((source/'cases.txt').read_text().splitlines())==17
    TARGET.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(TARGET,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name in sorted(expected):
            assert '\\' not in name and '..' not in Path(name).parts and not (source/name).is_symlink()
            entry=zipfile.ZipInfo(name,(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16
            archive.writestr(entry,(source/name).read_bytes())
    print(json.dumps(dict(path=str(TARGET),**pin(TARGET))))


if __name__=='__main__':main()
