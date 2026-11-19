#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the saved Scanpy workflow proof without running Java, Python packages or network models."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
ACTION=("SKIP3: restore the exact named permitted source/evidence dependency and retry NEW output; "
        "skip the dependent audit until then. FAIL2: retain changed/invalid evidence and diagnostics, "
        "correct the cause and retry NEW output. Do not refresh historical hashes. Saved proof is not "
        "a fresh scientific rerun, biological validation or authentication against coordinated forgery.")


class Missing(Exception):pass


def read(path,cap=32000000):
    try:
        if not path.is_file() or path.is_symlink():raise Missing('Required regular source/evidence unavailable: '+str(path))
        if path.stat().st_size>cap:raise ValueError('Evidence byte capacity exceeded: '+str(path))
        raw=path.read_bytes()
    except (FileNotFoundError,PermissionError) as error:raise Missing('Restore the exact readable permitted dependency '+str(path)) from error
    if len(raw)>cap:raise ValueError('Evidence grew beyond capacity')
    return raw


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def name(value):
    p=PurePosixPath(value)
    if p.is_absolute() or '..' in p.parts or '\\' in value or str(p)!=value:raise ValueError('Unsafe saved relative name')
    return p


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True)
    parser.add_argument('--bundle',default=str(ROOT/'docs/literature/scanpy-workflow-checks'))
    parser.add_argument('--restore-capture',action='store_true');args=parser.parse_args()
    out=Path(args.out).resolve()
    if out.exists():print('FAIL2: output exists; preserve it and choose NEW output. '+ACTION,file=sys.stderr);return 2
    out.mkdir(parents=True);folder=Path(args.bundle).resolve()
    try:
        raw=read(folder/'QUALIFICATION.json');q=json.loads(raw);r=json.loads(read(folder/'READBACK.json'))
        if q['status']!='PASS_SCANPY_ORDERED_WORKFLOW_SAVED_QUALIFICATION' or pin(raw)!=r['qualification']:raise ValueError('Qualification identity/status changed')
        for relative,wanted in q['sources'].items():
            if pin(read(ROOT/str(name(relative))))!=wanted:raise ValueError('Current source differs from saved qualification: '+relative)
        archive=read(folder/'evidence.zip')
        if pin(archive)!=r['evidence_archive'] or pin(archive)!=q['evidence_archive']:raise ValueError('Evidence archive identity changed')
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            names=z.namelist()
            if len(names)!=len(set(names)) or set(names)!=set(q['evidence_payloads']):raise ValueError('Evidence payload population changed')
            if sum(i.file_size for i in z.infolist())>128000000:raise ValueError('Evidence decompression capacity exceeded')
            payloads={}
            for n in names:
                name(n);raw=z.read(n)
                if pin(raw)!=q['evidence_payloads'][n]:raise ValueError('Evidence payload changed: '+n)
                payloads[n]=raw
        for jvm in ['windows21','windows25','linux21']:
            suites=[ET.fromstring(raw) for n,raw in payloads.items() if n.startswith('junit/'+jvm+'/') and n.endswith('.xml')]
            totals={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
            if totals!=dict(tests=20,failures=0,errors=0,skipped=0):raise ValueError('Three-JVM test proof differs')
        comparisons=json.loads(payloads['original/COMPARISONS.json'])
        if comparisons['status']!='PASS_SEPARATELY_WRITTEN_ORIGINAL_SCANPY_13_STEP_COMPARISON' or len(comparisons['comparisons'])!=52 or any(c['maximum_absolute_difference']!=0 for c in comparisons['comparisons']):raise ValueError('Original 52-array comparison differs')
        provided=json.loads(payloads['provided/COMPARISONS.json'])
        if provided['status']!='PASS_ORIGINAL_FOUR_STEP_PROVIDED_OBJECT_COMPARISON' or len(provided['arrays'])!=6 or any(c['maximum_absolute_difference']!=0 for c in provided['arrays'].values()):raise ValueError('Provided-object original comparison differs')
        if b'Ran 21 tests' not in payloads['worker-controls/stderr'] or not payloads['worker-controls/stderr'].rstrip().endswith(b'OK'):raise ValueError('Worker control proof differs')
        if args.restore_capture:
            for n,raw in payloads.items():
                if n.startswith(('capture/','provided-capture/')):
                    target=out/str(name(n));target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as stream:stream.write(raw)
        result=dict(status='PASS_SAVED_SCANPY_WORKFLOW_EVIDENCE',sources=len(q['sources']),payloads=len(payloads),tests_per_JVM=20,
                    original_array_annotation_comparisons=58,worker_controls=21,capture_restored=args.restore_capture,
                    fresh_scientific_models_run=False,whole_tool_qualified=False,new_family_credit=0,whole_goal_complete=False,required_action=ACTION)
        (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result));return 0
    except Missing as error:return failure(out,3,'SKIP3_DEPENDENT_AUDIT_SKIPPED',str(error))
    except Exception as error:return failure(out,2,'FAIL2_NO_ADMITTED_AUDIT',str(error))


def failure(out,code,status,error):
    result=dict(status=status,error=error,results_admitted=False,required_action=ACTION)
    (out/'FAILED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(status+': '+error+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
