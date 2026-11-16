#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze once or read saved scoped Sambamba replay/spill qualification records."""
from pathlib import Path
import argparse,hashlib,json,zipfile

ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/sambamba-execution-qualification'
PREVIOUS=ROOT/'docs/literature/sambamba-execution-development'
RETAINED=['adversarial-1','spill-controls-1']
NOTICE=('Bounded original-runtime operations only. No new catalog credit, paper-scale capacity, independent BAI qualification, '
        'binary authenticity or whole-tool/paper claim. Negative controls deliberately retain incorrect bundles and invalidated receipts. '
        'Require genuine original-runtime replay; skip dependent analysis if a permitted prerequisite is unavailable. '
        'The current-tree catalog source review and the full250-paper objective remain open. Read SAMBAMBA_EXECUTION_QUALIFICATION.md.')

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(path.read_bytes())
def load(path):return json.loads(path.read_bytes())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2,sort_keys=True);f.write('\n')
def inventory(base):
    result={}
    for name in RETAINED:
        root=base/name
        if not root.is_dir() or root.is_symlink():raise ValueError('Missing/aliased retained root: '+name)
        for p in sorted(root.rglob('*')):
            if p.is_symlink():raise ValueError('Unexpected retained symlink: '+str(p))
            if p.is_file():result[p.relative_to(base).as_posix()]=pin(p)
    return result
def archive_check(path,identities,extra=None):
    extra=extra or {}
    with zipfile.ZipFile(path) as z:
        if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(identities)|set(extra):raise ValueError('Complete archive membership differs: '+str(path))
        for name,identity in {**identities,**extra}.items():
            if rawpin(z.read(name))!=identity:raise ValueError('Archived member identity differs: '+name)
def previous():
    proof=load(PREVIOUS/'VALIDATION.json')
    files={'validation-records.zip':'validation_records','own-source-snapshot.zip':'own_source_snapshot',
           'SOURCE-PINS.json':'source_pins','IMPLEMENTATION-IDENTITIES.json':'implementation_identities'}
    for name,key in files.items():
        if pin(PREVIOUS/name)!=proof[key]:raise ValueError('Historical execution checkpoint differs: '+name)
    # Guide changes are explicit documentary continuation. Scientific/test sources stay exact.
    for name,identity in proof['tested_sources'].items():
        if pin(ROOT/name)!=identity:raise ValueError('Previously exercised Java implementation/test source changed: '+name)
    return dict(validation=pin(PREVIOUS/'VALIDATION.json'),files={n:pin(PREVIOUS/n) for n in files},
                earlier_fixture_cases=32,earlier_genuine_replays=32,earlier_complete_BAM_comparisons=96,
                earlier_original_guide_retained_in_source_archive=True)

def freeze(base,jar):
    if EVIDENCE.exists():raise FileExistsError('Retain the frozen checkpoint; use a separately versioned qualification')
    history=previous();adversarial=load(base/'adversarial-1/COMPLETED.json');spill=load(base/'spill-controls-1/COMPLETED.json')
    if adversarial['status']!='PASS_ORIGINAL_REPLAY_ADVERSARIAL_AND_LATE_RECORDING_CONTROLS' or len(adversarial['checks'])!=15:raise ValueError('Complete adversarial controls unavailable')
    if not all(adversarial[k] for k in ['source_bundle_preserved','counterfeit_saved_payload_passed','counterfeit_original_replay_rejected','late_verified_completion_invalidated','captured_input_replay_after_original_move']):raise ValueError('Named adversarial control did not pass')
    if any(c['exit_code']!=c['expected_exit_code'] for c in adversarial['checks']):raise ValueError('Unexpected adversarial process exit')
    if spill['status']!='PASS_BOTH_EDITIONS_ACTUAL_SPILL_THREAD_COMPRESSION_AND_COMPLETE_RECORD_CONTROLS' or len(spill['cases'])!=6 or len(spill['checks'])!=18:raise ValueError('Complete spill matrix unavailable')
    if spill['original_mark_remove_processes']!=24 or spill['complete_BAM_comparisons_across_settings']!=8 or any(c['exit_code'] for c in spill['checks']):raise ValueError('Original spill execution failed')
    for c in spill['cases']:
        if c['records']!=60000 or c['expected_duplicates']!=59998 or c['retained_records']!=2:raise ValueError('Independent complete flag expectation differs')
        if c['variant'].startswith('spill-'):
            if len(c['spill_witnesses'])!=4 or any(v['pairing_spill_bams']<=2 or v['pair_sort_chunks']<=1 for v in c['spill_witnesses'].values()):raise ValueError('Actual original spill was not observed')
    candidate=pin(jar)
    if candidate!=adversarial['candidate'] or candidate!=spill['candidate']:raise ValueError('Actual development JAR identity differs')
    classes=load(PREVIOUS/'IMPLEMENTATION-IDENTITIES.json')
    with zipfile.ZipFile(jar) as z:
        current={n:rawpin(z.read(n)) for n in z.namelist() if n.startswith(('org/jlinalg/cli/SambambaWorkflow','org/jlinalg/alignment/SambambaBamAudit')) and n.endswith('.class')}
        if current!=classes:raise ValueError('Actual scientific/replay classes differ from earlier fixture/guard build')
    own={}
    for prefix in ['src/main/java/org/jlinalg/cli/SambambaWorkflow','src/test/java/org/jlinalg/cli/SambambaWorkflow']:
        for p in ROOT.glob(prefix+'*.java'):own[p.relative_to(ROOT).as_posix()]=pin(p)
    for p in Path(__file__).parent.iterdir():
        if p.is_file():own[p.relative_to(ROOT).as_posix()]=pin(p)
    for name in ['src/main/java/org/jlinalg/alignment/SambambaBamAudit.java',
                 'tools/literature/sambamba_validation/make_cases.py',
                 'docs/literature/SAMBAMBA_EXECUTION_PROFILE.md','docs/literature/SAMBAMBA_EXECUTION_QUALIFICATION.md',
                 'examples/sambamba-execution/request.properties']:
        own[name]=pin(ROOT/name)
    identities=inventory(base);EVIDENCE.mkdir()
    (EVIDENCE/'.gitattributes').write_bytes(b'* -text\n')
    (EVIDENCE/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+NOTICE+'\n').encode())
    save(EVIDENCE/'SOURCE-PINS.json',own);save(EVIDENCE/'PRIVATE-RECORDS.json',dict(roots=RETAINED,files=identities))
    with zipfile.ZipFile(EVIDENCE/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in own:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    archive_check(EVIDENCE/'own-source-snapshot.zip',own,{'LICENSE-project.txt':pin(ROOT/'LICENSE')})
    with zipfile.ZipFile(EVIDENCE/'validation-records.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in identities:z.write(base/name,name)
    archive_check(EVIDENCE/'validation-records.zip',identities)
    if inventory(base)!=identities:raise ValueError('Private records changed during capture')
    save(EVIDENCE/'CONTROLS.json',dict(adversarial=adversarial,spill=spill,earlier_checkpoint=history,
         scientific_implementation_unchanged=True,candidate=candidate,new_catalog_credit=0,
         accepted_analyses_must_pass_genuine_replay=True,negative_control_bundles_are_not_results=True,
         bai_independently_qualified=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))
    files={p.name:pin(p) for p in EVIDENCE.iterdir() if p.is_file()}
    save(EVIDENCE/'STATUS.json',dict(status='RECORDED_SCOPED_SAMBAMBA_REPLAY_AND_SPILL_QUALIFICATION',public_files=files,
        private_files=len(identities),project_license=pin(ROOT/'LICENSE'),candidate=candidate,
        notices=NOTICE,new_catalog_credit=0,paper_scale_capacity_implemented=False,
        current_tree_catalog_source_qualification_pending=True,whole_goal_complete=False))

def verify(base=None):
    proof=load(EVIDENCE/'STATUS.json');entries=list(EVIDENCE.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in entries) or {p.name for p in entries}!=set(proof['public_files'])|{'STATUS.json'}:raise ValueError('Complete public checkpoint membership differs')
    for name,identity in proof['public_files'].items():
        if pin(EVIDENCE/name)!=identity:raise ValueError('Public checkpoint identity differs: '+name)
    own=load(EVIDENCE/'SOURCE-PINS.json');private=load(EVIDENCE/'PRIVATE-RECORDS.json')
    archive_check(EVIDENCE/'own-source-snapshot.zip',own,{'LICENSE-project.txt':proof['project_license']})
    archive_check(EVIDENCE/'validation-records.zip',private['files']);previous()
    current=all((ROOT/name).is_file() and pin(ROOT/name)==identity for name,identity in own.items())
    count=0
    if base is not None:
        actual=inventory(base)
        if actual!=private['files'] or len(actual)!=proof['private_files']:raise ValueError('Complete retained private qualification records differ')
        count=len(actual)
    return dict(status='PASS_COMPLETE_PRIVATE_SAMBAMBA_QUALIFICATION_READBACK' if base else 'PASS_PUBLIC_SAMBAMBA_QUALIFICATION_READBACK',
        current_project_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,
        private_files_read=count,actual_new_scientific_analysis_executed=False,
        original_runtime_fixture_and_guard_implementation_preserved=True,
        qualification_scope='bounded_original_runtime_replay_and_observed_spill_controls',
        independent_BAI_qualification=False,new_catalog_credit=0,whole_goal_complete=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--private-base',type=Path);p.add_argument('--freeze',action='store_true');p.add_argument('--jar',type=Path);a=p.parse_args()
    try:
        base=a.private_base.resolve(strict=True) if a.private_base else None
        if a.freeze:
            if base is None or a.jar is None:raise ValueError('Freeze requires --private-base and --jar')
            freeze(base,a.jar.resolve(strict=True))
        elif a.jar is not None:raise ValueError('--jar is only for an explicit one-time freeze')
        print(json.dumps(verify(base),indent=2),flush=True)
    except Exception as e:
        print('FAIL2_SAMBAMBA_QUALIFICATION_RECORDS_UNVERIFIED',repr(e),NOTICE,flush=True);raise SystemExit(2)
