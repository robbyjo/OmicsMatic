#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read saved Sambamba development records; does not qualify an execution adapter."""
from pathlib import Path
import argparse,hashlib,json,zipfile

ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/sambamba-workflow-development'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def filepin(path):return pin(Path(path).read_bytes())
def load(path):return json.loads(Path(path).read_bytes())
def verify(base=None):
    proof=load(EVIDENCE/'STATUS.json');members=list(EVIDENCE.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in members):raise ValueError('Public checkpoint has a nonregular member')
    if {p.name for p in members}!=set(proof['public_files'])|{'STATUS.json'}:raise ValueError('Complete checkpoint membership differs')
    for name,identity in proof['public_files'].items():
        if filepin(EVIDENCE/name)!=identity:raise ValueError('Saved public checkpoint differs: '+name)
    own=load(EVIDENCE/'SOURCE-PINS.json')
    with zipfile.ZipFile(EVIDENCE/'own-source-snapshot.zip') as z:
        if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(own)|{'LICENSE-project.txt'}:raise ValueError('Complete own-source archive differs')
        for name,identity in own.items():
            if pin(z.read(name))!=identity:raise ValueError('Saved own-source member differs: '+name)
        if pin(z.read('LICENSE-project.txt'))!=proof['project_license']:raise ValueError('Own-source license differs')
    current=all((ROOT/name).is_file() and filepin(ROOT/name)==identity for name,identity in own.items())
    count=0
    if base is not None:
        private=load(EVIDENCE/'PRIVATE-RECORDS.json');actual={}
        for name in private['roots']:
            parent=base/name
            if not parent.is_dir() or parent.is_symlink():raise ValueError('Missing/aliased retained root: '+name)
            for path in sorted(parent.rglob('*')):
                if path.is_symlink():raise ValueError('Unqualified retained alias: '+str(path))
                if path.is_file():actual[path.relative_to(base).as_posix()]=filepin(path)
        if actual!=private['files']:raise ValueError('Complete private source/runtime/output/report records differ')
        count=len(actual)
        if count!=proof['private_files']:raise ValueError('Private file count differs')
    return dict(status='PASS_COMPLETE_PRIVATE_SAMBAMBA_DEVELOPMENT_READBACK' if base else 'PASS_PUBLIC_SAMBAMBA_DEVELOPMENT_READBACK',
        current_project_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,
        private_files_read=count,fresh_original_analysis_executed=False,execution_adapter_qualified=False,
        independent_duplicate_classifier=False,new_catalog_credit=0,whole_goal_complete=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--private-base',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(a.private_base.resolve(strict=True) if a.private_base else None),indent=2),flush=True)
    except Exception as e:
        print('FAIL2_SAMBAMBA_DEVELOPMENT_RECORDS_UNVERIFIED',repr(e),'Retain the originals and resolve the named cause. No execution adapter, complete tool, paper reproduction or new catalog credit is admitted.',flush=True)
        raise SystemExit(2)
