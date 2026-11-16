#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read saved source-transition records; this is not a new gate or scientific analysis."""
from pathlib import Path
import argparse,json,sys,zipfile
sys.dont_write_bytecode=True
MODULE=Path(__file__).resolve().parent;ROOT=MODULE.parents[2];sys.path.insert(0,str(MODULE.parent))
from catalog_source_transition_v2.source import EVIDENCE,OWN,GUIDE,pin,rawpin,source_guard

REVIEW_ROOTS=['predecessor-check-1','linux-workspace-tests-1','source-intake-1','ast-review-1','guard-controls-1','guard-controls-2','preparation-1']
DISPATCH_ROOTS=['dispatch-validation-1','alias-runtime-1','dispatch-closure-1']
def load(path):return json.loads(path.read_bytes())
def archive_check(path,pins):
    with zipfile.ZipFile(path) as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or set(names)!=set(pins) or z.testzip() is not None:raise ValueError('Archive membership/CRC differs: '+str(path))
        for name,identity in pins.items():
            if name.startswith(('/','\\')) or '..' in Path(name).parts or rawpin(z.read(name))!=identity:raise ValueError('Archive member differs: '+name)
def inventory(review,dispatch):
    result={}
    for label,base,roots in [('review',review,REVIEW_ROOTS),('dispatch',dispatch,DISPATCH_ROOTS)]:
        for name in roots:
            directory=base/name
            if directory.is_symlink() or not directory.is_dir():raise ValueError('Missing/aliased retained root: '+str(directory))
            for path in sorted(directory.rglob('*')):
                relative=path.relative_to(directory)
                # Control checkout sources are separately identified by the exact
                # Git target and clean status, not copied as metadata payloads.
                if relative.parts and relative.parts[0]=='checkout':continue
                if path.is_symlink():raise ValueError('Retained alias refused: '+str(path))
                if path.is_file():result[label+'/'+name+'/'+relative.as_posix()]=pin(path)
    return result
def verify(review=None,dispatch=None,check_current=False):
    folder=ROOT/EVIDENCE;proof=load(folder/'QUALIFICATION.json')
    expected=set(proof['public_files'])|{'QUALIFICATION.json'};entries=list(folder.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in entries) or {p.name for p in entries}!=expected:raise ValueError('Public evidence membership differs')
    for name,identity in proof['public_files'].items():
        if pin(folder/name)!=identity:raise ValueError('Public evidence differs: '+name)
    own=proof['source_pins'];archive_check(folder/'own-source-snapshot.zip',dict(own,**{'LICENSE-project.txt':proof['project_license']}))
    archive_check(folder/'validation-metadata.zip',load(folder/'METADATA-PINS.json'))
    current=all((ROOT/name).is_file() and pin(ROOT/name)==identity for name,identity in own.items());count=0
    if (review is None)!=(dispatch is None):raise ValueError('Supply both private review and dispatch bases')
    if review is not None:
        actual=inventory(review,dispatch);expected_private=load(folder/'PRIVATE-RECORDS.json')['files']
        if actual!=expected_private:raise ValueError('Complete named new private records differ')
        count=len(actual)
    source=None
    if check_current:source=source_guard(ROOT,load(folder/'TARGET-TREE.json'))
    return dict(status='PASS_SAVED_RELEASE_DEVELOPMENT_SOURCE_RECORD_READBACK',current_verifier_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,
        complete_named_new_private_files_checked=count,current_application_source_tree_checked=check_current,current_source_check=source,
        original_parent_or_scientific_analysis_reexecuted=False,new_scoped_integration_credit=0,whole_goal_complete=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--private-review-base',type=Path);p.add_argument('--private-dispatch-base',type=Path);p.add_argument('--check-current-source',action='store_true');a=p.parse_args()
    try:print(json.dumps(verify(a.private_review_base.resolve(strict=True) if a.private_review_base else None,a.private_dispatch_base.resolve(strict=True) if a.private_dispatch_base else None,a.check_current_source),indent=2),flush=True)
    except Exception as error:print('FAIL2_SAVED_SOURCE_TRANSITION_RECORDS_UNVERIFIED',repr(error),'Retain the originals, read the named cause and supply the named prerequisites. No complete gate or scientific result is admitted.',flush=True);raise SystemExit(2)
