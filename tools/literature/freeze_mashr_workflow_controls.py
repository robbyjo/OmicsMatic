#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete typed original mashr workflow controls, including failures."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'
TARGET=ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    if not __debug__:raise RuntimeError('Run with assertions enabled, never Python -O')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',required=True,type=Path);args=parser.parse_args()
    source=args.source.resolve();assert source.is_relative_to(BASE.resolve()) and not TARGET.exists()
    record=json.loads((source/'ORIGINAL-CONTROLS.json').read_text());assert record['status']=='original-workflow-controls-returned-awaiting-native-audit'
    assert len(record['protocols'])==26 and sum(p['exit_code']==0 for p in record['protocols'])==17
    assert all(p['exit_code']==p['expected_exit_code'] for p in record['protocols'])
    expected=set(record['files'])|{'ORIGINAL-CONTROLS.json'}
    assert {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}==expected
    evidence={}
    for name in sorted(expected):
        path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and path.as_posix()==name and '\\' not in name
        file=source/name;assert not file.is_symlink()
        if name in record['files']:assert pin(file)==record['files'][name],name
        evidence[name]=file.read_bytes()
    assert (source/'runtime-before.json').is_file() and (source/'runtime-after.json').is_file()
    before=json.loads((source/'runtime-before.json').read_text());after=json.loads((source/'runtime-after.json').read_text());assert before['files']==after['files']
    assert sum(map(len,before['files'].values()))==record['runtime_files']==5637
    for protocol in record['protocols']:
        folder=source/protocol['name']/'outputs';assert 'Required action' in (folder/'WARNINGS.md').read_text()
        assert not (folder/'COMPLETED.json').exists()
        if protocol['exit_code']:
            assert ('SKIP3' if protocol['exit_code']==3 else 'FAIL2') in (folder/'source-status.tsv').read_text()
    assert (source/'ed-iteration-limit/outputs/covariances/original-ed-covariances.rds').is_file()
    assert (source/'residual-iteration-limit/outputs/original-residual-em.rds').is_file()
    for boundary,attempt,cases in [('Rcpp-single-target-abort','original-workflow-controls-3',['single-target']),
                                   ('R-single-feature-dimension-failures','original-workflow-controls-4',['single-target','single-training'])]:
        earlier=BASE/attempt
        for name in ['analysis.R','collector.py','FAILURE.json','WARNINGS.md','runtime-before.json','runtime-after.json']:
            path=earlier/name
            if path.is_file():evidence['retained-source-boundaries/'+boundary+'/'+name]=path.read_bytes()
        for case in cases:
            for path in sorted((earlier/case).rglob('*')):
                if path.is_file():evidence['retained-source-boundaries/'+boundary+'/'+case+'/'+path.relative_to(earlier/case).as_posix()]=path.read_bytes()
    evidence['freeze-collector.py']=Path(__file__).read_bytes()
    evidence['mashr-0.2.79-original-source.tar.gz']=(BASE/'source-intake-1/cran-source.tar.gz').read_bytes()
    evidence['mashr-BSD-NOTICE.txt']=(BASE/'source-intake-1/github/LICENSE.md').read_bytes()
    evidence['README.txt']=b'Complete original mashr0.2.79 typed-workflow controls: 26 protocols, 17 original-process returns, 8 expected FAIL2 and 1 expected dependency SKIP3. All statuses remain unadmitted until separate native/bundle gates. Includes every input/output, package/Rhome fingerprints, commands, logs, exact failed ED/EM candidates, leakage controls, numeric IDs, alpha scaling, alternative optimizer and overlapping populations. Both original singleton paths fail; earlier native abort/R dimension failures are retained. The native Gaussian/mixture APIs support individual features; integrated native singleton workflow remains pending. No upstream executable, patient data, public managed runtime or whole-paper reconstruction is supplied.\n'
    inventory={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for n,raw in sorted(evidence.items())}
    evidence['inventory.json']=(json.dumps(inventory,indent=2)+'\n').encode()
    with zipfile.ZipFile(TARGET,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(evidence.items()):
            item=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));item.create_system=3;item.external_attr=0o100644<<16;item.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(item,raw)
    print(json.dumps(dict(archive=TARGET.relative_to(ROOT).as_posix(),files=len(inventory),**pin(TARGET)),indent=2))


if __name__=='__main__':main()
