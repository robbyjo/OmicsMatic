#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit the qualified native workflow; retain the newly reviewed iDEP family as missing."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tpmcalculator_catalog.model import *
from tpmcalculator_catalog.source import audit as census
from melodi_catalog.catalog import verify as previous_verify,load_catalog_registry as previous_registry
from hicat109_catalog.bridge import metadata_view
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
from tpmcalculator_validation import freeze_workflow_records as workflow_records
from tpmcalculator_validation import freeze_core_records as core_records

if not __debug__:raise RuntimeError('Do not disable source/evidence assertions with Python optimization')
ACTION=('Read TPMCALCULATOR_CATALOG_INTEGRATION.md and TPMCALCULATOR_NATIVE_WORKFLOW.md. '
 'P050 version/options and the exact iDEP.96 deployed edition remain unknown. No author settings or whole-paper result is inferred. '
 'SKIP3 skips missing/forbidden prerequisites; FAIL2 admits no complete result, including recording failure after native computation. '
 'Preserve complete original inputs and partials; resolve the named cause and retry NEW output paths. The full250-paper goal remains open.')

def load_before(root):
    with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as archive:
        if len(archive.namelist())!=8 or set(archive.namelist())!=set(META):raise ValueError('Eight exact preceding metadata files required')
        return {name:archive.read(name) for name in META}

def compatibility(before,after):
    row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
    if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Complete preceding review bytes cannot be reconstructed')
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='tpmcalculator-and-idep-source-roles',
        scope='Append two source-specific P050 reviews; preserve every preceding review byte through an exact inverse. No scientific source overlays.',paths=[row])

def qualification(root):
    # The unchanged original verifiers read the actual science tree, never a reconstructed science overlay.
    if Path(root/WORKFLOW).resolve()!=workflow_records.EVIDENCE.resolve():raise ValueError('Qualified workflow must resolve to this actual scientific tree')
    core=core_records.verify()
    current=workflow_records.verify()  # full ZIP payloads, original table bytes, process returns, guards, replay inventories
    if current['actual_focused_tests']!=141 or current['original_cases_per_host']!=64 or current['public_failure_controls_per_host']!=9:
        raise ValueError('Complete native public qualification required')
    for folder,proof in [('docs/literature/tpmcalculator-native-development',core),(WORKFLOW,current)]:
        for name,identity in proof['source_files'].items():
            if pin(root/name)!=identity:raise ValueError('Qualified native source differs: '+name)
        for name,identity in proof['evidence_files'].items():
            if pin(root/folder/name)!=identity:raise ValueError('Complete preserved native evidence differs: '+name)
    return dict(status='PASS_NATIVE_TPMCALCULATOR_SOURCE_AND_COMPLETE_PUBLIC_RECORD_READBACK',
        core_qualification=pin(root/'docs/literature/tpmcalculator-native-development/QUALIFICATION.json'),
        workflow_qualification=pin(root/WORKFLOW/'QUALIFICATION.json'),actual_focused_tests=141,
        actual_original_cases_per_host=64,actual_public_failure_controls_per_host=9,actual_public_processes_per_host=137,
        cross_host_replays_each_direction=5,self_contained_application_checks_each_host=5,
        full_public_record_payloads_read_back=True,fresh_native_calls_repeated_by_catalog_gate=False,
        native_family_credit=1,author_version_or_options_established=False,whole_tool_qualified=False,whole_paper_reproduced=False)

def load_catalog_registry(root=ROOT):
    before=load_before(root) if (root/PROOF).exists() else {name:(root/name).read_bytes() for name in META}
    rows=copy.deepcopy(previous_registry(metadata_view(root,before)))
    if len(rows)!=484 or any(row['id']=='idep' for row in rows):raise ValueError('Exact484-definition predecessor without iDEP required')
    row=next(row for row in rows if row['id']=='tpmcalculator')
    row.update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=NOTE)
    rows.append(copy.deepcopy(DEFINITION))
    if len(rows)!=485 or len({row['id'] for row in rows})!=485:raise ValueError('Exactly one new registry definition required')
    return rows

def apply(root,out):
    if any((root/name).exists() for name in [EVIDENCE,PROOF,COMPATIBILITY]):raise ValueError('Preserve and verify the existing admission')
    before={name:(root/name).read_bytes() for name in META}
    previous=previous_verify(metadata_view(root,before),rescan=True)
    if tuple(previous[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,445,2689,1195,121,265):raise ValueError('Exact complete MELODI predecessor required')
    science=qualification(root);source_census=census(root);after=transition(root,before);result=check_transition(root,before,after)
    evidence=root/EVIDENCE;evidence.mkdir(exist_ok=False)
    (evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in before.items():archive.writestr(name,raw)
    for name,value in [('PREDECESSOR.json',previous),('QUALIFICATION.json',science),('CENSUS.json',source_census)]:
        (evidence/name).write_bytes(encoded(value))
    (evidence/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode())
    (root/COMPATIBILITY).write_bytes(encoded(compatibility(before,after)))
    for name,raw in before.items():
        if (root/name).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite allowed: '+name)
    (out/'PREPARED.json').write_bytes(encoded(dict(before_files={name:rawpin(raw) for name,raw in before.items()},after_files={name:rawpin(raw) for name,raw in after.items()},**result)))
    for name,raw in after.items():(root/name).write_bytes(raw)
    source_names=sorted(path.relative_to(root).as_posix() for path in (root/MODULE).iterdir() if path.is_file())+[GUIDE]
    evidence_names=sorted(path.relative_to(root).as_posix() for path in evidence.rglob('*') if path.is_file())+[COMPATIBILITY]
    proof=dict(status='PASS_NATIVE_TPMCALCULATOR_AND_IDEP_PRIMARY_CATALOG_ADMISSION',**result,admitted_tool_ids=['tpmcalculator'],
        before_files={name:rawpin(raw) for name,raw in before.items()},after_files={name:pin(root/name) for name in META},
        source_pins={name:pin(root/name) for name in source_names},evidence_pins={name:pin(root/name) for name in evidence_names},
        scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False,fresh_scientific_calls_repeated_by_catalog_gate=False,
        complete_public_record_payloads_rechecked_by_catalog_gate=True)
    (root/PROOF).write_bytes(encoded(proof));return proof

def verify(root=ROOT,rescan=False):
    proof=json.loads((root/PROOF).read_bytes())
    if proof['status']!='PASS_NATIVE_TPMCALCULATOR_AND_IDEP_PRIMARY_CATALOG_ADMISSION':raise ValueError('Unsupported native TPMCalculator admission')
    for kind in ['source_pins','evidence_pins','after_files']:
        for name,identity in proof[kind].items():
            if pin(root/name)!=identity:raise ValueError('Qualified catalog source/evidence differs: '+name)
    before=load_before(root)
    if {name:rawpin(raw) for name,raw in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata differs')
    after={name:(root/name).read_bytes() for name in META};result=check_transition(root,before,after)
    if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact review inverse differs')
    previous=previous_verify(metadata_view(root,before),rescan=rescan)
    if rescan and census(root)!=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes()):raise ValueError('Complete primary census differs')
    return dict(status=proof['status'],**result,registry_definitions=len(load_catalog_registry(root)),component=qualification(root),predecessor=previous['status'],
        primary_sources_rescanned=rescan,fresh_scientific_calls_repeated_by_catalog_gate=False,
        complete_public_record_payloads_rechecked_by_catalog_gate=True,whole_paper_reproduced=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--rescan-sources',action='store_true')
    args=parser.parse_args();out=args.out.resolve();owned=False
    try:
        if not out.is_relative_to(ROOT/'build'):raise ValueError('Use a NEW build output directory')
        out.mkdir(parents=True,exist_ok=False);owned=True
        result=apply(ROOT,out) if args.apply else verify(rescan=args.rescan_sources)
        (out/('APPLIED.json' if args.apply else 'COMPLETED.json')).write_bytes(encoded(result));print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_TPMCALCULATOR_CATALOG_UNAVAILABLE' if code==3 else 'FAIL2_TPMCALCULATOR_CATALOG_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:(out/'FAILURE.json').write_bytes(encoded(dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
        return code

if __name__=='__main__':raise SystemExit(main())
