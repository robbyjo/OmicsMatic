#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit SingleR only after complete frozen readback and fresh original replay."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from singler_catalog.model import *
from singler_catalog.source import census
from hicat109_catalog.bridge import metadata_view
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta

BASELINE='96fc65d3e37917728901f88906cd15c3b386702e'
WORKFLOW='docs/literature/singler-workflow-validation'
ACTION=('Read SINGLER_CATALOG_INTEGRATION.md and all original warnings. Literal 2.41 stays unresolved and skips dependent analysis. '
 'SKIP3 skips missing/forbidden requirements; FAIL2 admits no complete result, including recording failure. '
 'Preserve all original values, identifiers, NA/NaN/Inf, complete returns/conditions/RNG, consoles and prior evidence. '
 'Synthetic source controls do not reproduce a paper. The full 250-paper goal remains open.')

def compatibility(before,after):
    row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
    if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact preceding reviewed-use bytes cannot be recovered')
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='singler-seven-source-reviews',
      scope='Seven new source reviews and four enriched existing observations; only exact reviewed-use metadata is reversible. Scientific overlays forbidden.',paths=[row])

def predecessor(root,before):
    proof=json.loads((root/DATA/'hicat109-catalog-validation.json').read_bytes())
    if proof['status']!='PASS_HICAT109_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION' or {n:rawpin(v) for n,v in before.items()}!=proof['after_files']:
        raise ValueError('Exact complete admitted HICAT predecessor required')
    for kind in ['source_pins','evidence_pins']:
        for name,identity in proof[kind].items():
            if pin(root/name)!=identity:raise ValueError('Preceding source/evidence differs: '+name)
    # No fresh computation is inferred from unchanged evidence. Reject changes
    # to any preceding tracked implementation relative to the qualified main.
    excluded=[*META,GUIDE,PROOF,COMPATIBILITY,EVIDENCE,WORKFLOW,MODULE,
      'tools/literature/singler_workflows','tools/literature/singler_validation',
      'tools/literature/singler_source_audit','docs/literature/singler-workflow-development']
    command=['git','diff','--exit-code','--diff-filter=CDMRTUXB',BASELINE,'--','.']+[':(exclude)'+n for n in excluded]
    result=subprocess.run(command,cwd=root,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:raise ValueError('Preceding tracked source changed or baseline unavailable; preserve it and run a separately reviewed predecessor integration')
    from hicat109_catalog.catalog import load_catalog_registry as previous_registry
    registry=previous_registry(metadata_view(root,before))
    if len(registry)!=476:raise ValueError('All 476 predecessor definitions required')
    return dict(status='PASS_EXACT_UNCHANGED_PREDECESSOR_SOURCE_AND_ADMITTED_EVIDENCE_NOT_FRESH_SCIENTIFIC_REPLAY',
      qualified_main=BASELINE,registry_definitions=476,preceding_scientific_calls_repeated=False,
      scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False)

def load_catalog_registry(root=ROOT):
    before=load_before(root) if (root/PROOF).exists() else {n:(root/n).read_bytes() for n in META}
    from hicat109_catalog.catalog import load_catalog_registry as previous_registry
    rows=previous_registry(metadata_view(root,before))
    if len(rows)!=476:raise ValueError('Complete predecessor registry required')
    target=next(r for r in rows if r['id']=='singler')
    current=entry(root,next(r for r in json.loads(before[META[0]]) if r['id']=='singler'))
    for key in ['current_coverage','existing_source','coverage_note']:target[key]=current[key]
    rows+=definitions(root)
    if len(rows)!=479 or len({r['id'] for r in rows})!=479:raise ValueError('Exactly three distinct new definitions required')
    return rows

def load_before(root):
    with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as archive:
        if len(archive.namelist())!=8 or set(archive.namelist())!=set(META):raise ValueError('Eight exact preceding metadata inputs required')
        return {n:archive.read(n) for n in META}

def workflow(root):
    proof=json.loads((root/WORKFLOW/'QUALIFICATION.json').read_bytes())
    if proof['status']!='PASS_SCOPED_FIVE_ORIGINAL_SINGLER_RELEASES_AND_COMPLETE_RECORDING' or (proof['original_controls'],proof['complete_original_returns'],proof['original_failures_without_result'])!=(250,232,18):
        raise ValueError('Complete five-release SingleR qualification required')
    for name,identity in proof['source_files'].items():
        if pin(root/name)!=identity:raise ValueError('Qualified SingleR source differs: '+name)
    if pin(root/WORKFLOW/'complete-validation-records.zip')!=proof['evidence_bundle']:raise ValueError('Frozen SingleR index differs')
    for name,identity in proof['evidence_payloads'].items():
        if pin(root/WORKFLOW/name)!=identity:raise ValueError('Frozen SingleR payload differs: '+name)
    return proof

def qualification(root):
    proof=workflow(root);receipt=json.loads((root/EVIDENCE/'FRESH-REPLAY.json').read_bytes())
    if receipt['status']!='PASS_FULL_FROZEN_SINGLER_RECORDS_AND_250_FRESH_ORIGINAL_REPLAYS' or receipt['original_controls']!=250 or not receipt['whole_input_regeneration_verified']:
        raise ValueError('250 fresh original controls and whole-input regeneration required')
    readback=json.loads((root/EVIDENCE/'WINDOWS-READBACK.json').read_bytes())
    if readback['status']!='PASS_COMPLETE_FROZEN_SINGLER_RECORD_INTEGRITY_NOT_NEW_SCIENTIFIC_COMPUTATION' or readback['logical_files']!=proof['evidence_layout']['logical_files']:
        raise ValueError('Complete Windows frozen-record readback required')
    archive=root/EVIDENCE/'fresh-original-replay-records.zip'
    with zipfile.ZipFile(archive) as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or z.testzip() is not None:raise ValueError('Fresh replay archive integrity failure')
        if json.loads(z.read('COMPLETED.json'))!=receipt:raise ValueError('Complete fresh replay receipt differs')
        for version in proof['original_releases']:
            report=json.loads(z.read('fresh/'+version+'/FROZEN-SCIENCE-COMPARISONS.json'))
            if report['status']!='PASS_BOTH_FROZEN_CHANNELS_VS_COMPLETE_FRESH_ORIGINALS' or len(report['cases'])!=50 or not all(c['passed'] and all(c['checks'].values()) for c in report['cases'].values()):
                raise ValueError('Complete fresh whole-object comparisons missing: '+version)
    return dict(status='PASS_FIVE_ORIGINAL_SINGLER_RELEASES_COMPLETE_FROZEN_READBACK_AND_FRESH_REPLAY',original_controls=250,
      complete_returns=232,original_failures_without_result=18,logical_files=proof['evidence_layout']['logical_files'],
      source_files=len(proof['source_files']),fresh_original_replay_records=pin(archive),
      both_frozen_complete_channels_compared=True,complete_conditions_and_rng_compared=True,source_algorithms_patched=False,
      literal_2_41_resolved=False,whole_paper_reproduced=False)

def archive_replay(root,target,readback):
    receipt=json.loads((target/'COMPLETED.json').read_bytes())
    if receipt['status']!='PASS_FULL_FROZEN_SINGLER_RECORDS_AND_250_FRESH_ORIGINAL_REPLAYS':raise ValueError('Fresh original replay has not completed')
    proof=workflow(root)
    for name,identity in proof['source_files'].items():
        for directory in [target,readback]:
            if pin(directory/'records/qualified-project-sources'/name)!=identity:
                raise ValueError('The actual replay/readback used different complete qualified sources: '+name)
    evidence=root/EVIDENCE
    with zipfile.ZipFile(evidence/'fresh-original-replay-records.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(target.rglob('*')):
            if not p.is_file() or p.relative_to(target).parts[0]=='records':continue
            if p.is_symlink():raise ValueError('Unexpected replay record symlink')
            z.write(p,p.relative_to(target).as_posix())
    (evidence/'FRESH-REPLAY.json').write_bytes((target/'COMPLETED.json').read_bytes())
    (evidence/'WINDOWS-READBACK.json').write_bytes((readback/'COMPLETED.json').read_bytes())

def apply(root,out,fresh_replay,readback):
    if any((root/n).exists() for n in [EVIDENCE,PROOF,COMPATIBILITY]):raise ValueError('Admission evidence exists; retain it and verify')
    before={n:(root/n).read_bytes() for n in META};prior=predecessor(root,before)
    workflow(root);source_census=census(root);after=transition(root,before);result=check_transition(root,before,after)
    evidence=root/EVIDENCE;evidence.mkdir(exist_ok=False)
    (evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    archive_replay(root,fresh_replay,readback);science=qualification(root)
    with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,raw in before.items():z.writestr(n,raw)
    for name,value in [('CENSUS.json',source_census),('PREDECESSOR.json',prior),('QUALIFICATION.json',science)]:
        (evidence/name).write_bytes(encoded(value))
    (evidence/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode())
    (root/COMPATIBILITY).write_bytes(encoded(compatibility(before,after)))
    for name,raw in before.items():
        if (root/name).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite permitted: '+name)
    (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},**result)))
    for name,raw in after.items():(root/name).write_bytes(raw)
    source_names=sorted(p.relative_to(root).as_posix() for p in (root/MODULE).iterdir() if p.is_file())+[GUIDE]
    evidence_names=sorted(p.relative_to(root).as_posix() for p in evidence.iterdir() if p.is_file())+[COMPATIBILITY]
    evidence_names+=sorted(p.relative_to(root).as_posix() for p in (root/WORKFLOW).iterdir() if p.is_file())
    proof=dict(status='PASS_SINGLER_FIVE_RELEASE_SCOPED_WORKFLOW_AND_SEVEN_PRIMARY_REVIEWS',**result,
      before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(root/n) for n in META},
      source_pins={n:pin(root/n) for n in source_names},evidence_pins={n:pin(root/n) for n in evidence_names},
      admitted_tool_ids=['singler'],new_missing_tool_ids=[d['id'] for d in definitions(root)],
      previous_scientific_calls_repeated=False,preceding_scientific_verifiers_changed=False)
    (root/PROOF).write_bytes(encoded(proof));return proof

def verify(root=ROOT,rescan=False):
    proof=json.loads((root/PROOF).read_bytes())
    if proof['status']!='PASS_SINGLER_FIVE_RELEASE_SCOPED_WORKFLOW_AND_SEVEN_PRIMARY_REVIEWS':raise ValueError('Unsupported SingleR admission')
    for kind in ['source_pins','evidence_pins','after_files']:
        for name,identity in proof[kind].items():
            if pin(root/name)!=identity:raise ValueError('Qualified catalog source/evidence differs: '+name)
    before=load_before(root)
    if {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata differs')
    previous=predecessor(root,before);after={n:(root/n).read_bytes() for n in META}
    result=check_transition(root,before,after)
    if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Historical review inverse differs')
    if rescan and census(root)!=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes()):raise ValueError('Complete original primary census differs')
    return dict(status=proof['status'],**result,registry_definitions=len(load_catalog_registry(root)),
      component=qualification(root),predecessor=previous['status'],primary_sources_rescanned=rescan,
      fresh_scientific_calls_repeated_by_this_verification=False,whole_paper_reproduced=False)

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    p.add_argument('--out',required=True,type=Path);p.add_argument('--rescan-sources',action='store_true')
    p.add_argument('--fresh-replay',type=Path);p.add_argument('--windows-readback',type=Path)
    a=p.parse_args();out=a.out.resolve();owned=False
    try:
        if not out.is_relative_to(ROOT/'build'):raise ValueError('Use a NEW build output path')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if a.apply and (a.fresh_replay is None or a.windows_readback is None):raise ValueError('Both actual fresh replay and Windows readback are required for admission')
        result=apply(ROOT,out,a.fresh_replay,a.windows_readback) if a.apply else verify(rescan=a.rescan_sources)
        (out/('APPLIED.json' if a.apply else 'COMPLETED.json')).write_bytes(encoded(result))
        (out/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_SINGLER_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_SINGLER_CATALOG_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:(out/'FAILURE.json').write_bytes(encoded(dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
            except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
