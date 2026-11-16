#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit the scoped original Sambamba adapter and three precise primary-source roles."""
from pathlib import Path
import argparse,copy,json,subprocess,sys,traceback,uuid,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from sambamba_catalog.model import *
from sambamba_catalog.source import census,load
from catalog_source_transition_v2 import bridge as previous
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
if not __debug__:raise RuntimeError('Do not disable scientific/source evidence assertions')
STATUS='PASS_SCOPED_ORIGINAL_SAMBAMBA_AND_PRIMARY_ROLE_CATALOG_ADMISSION'
ACTION=('Build current source; frozen published0.4.0 assets do not contain the new command. Read SAMBAMBA_CATALOG_INTEGRATION.md. '
        'P082 reported0.6.5 is unsupported and its operation unresolved; skip that dependent analysis, never substitute0.6.6. '
        'Retain original0.6.6 score-overflow failures and inconsistent sidecars; use a separate audited index or separately qualified edition/tool for a new analysis, or skip. '
        'Missing/forbidden prerequisites require SKIP3; FAIL2 admits no complete result, including late recording failure. Preserve originals and retry NEW paths. '
        'Historical sources, verifiers, source identities and release freezes stay unchanged. The full250-paper goal remains open.')
def load_before(root):
    with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
        if len(z.namelist())!=8 or set(z.namelist())!=set(META) or z.testzip() is not None:raise ValueError('Exact complete eight-file predecessor required')
        return {name:z.read(name) for name in META}
def extension_pins(root):
    names=[]
    for directory in [MODULE,EVIDENCE]:
        if (root/directory).exists():
            for path in (root/directory).rglob('*'):
                if path.is_symlink():raise ValueError('Consumer source/evidence alias refused')
                if path.is_file():names.append(path.relative_to(root).as_posix())
    names.extend(name for name in [GUIDE,PROOF,COMPATIBILITY] if (root/name).is_file())
    return {name:pin(root/name) for name in sorted(names)}
def compatibility(before,after):
    change=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
    if reverse_delta(after[REVIEWS],change)!=before[REVIEWS]:raise ValueError('Every preceding review byte must be exactly recoverable')
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='sambamba',
        scope='Update one P110 role and add P082/P198 source reviews. Metadata only; no scientific source overlay.',paths=[change])
def streaming_catalog_readback(root,before):
    """New consumer of immutable records, with an explicit historical metadata view.

    The original standalone reader deliberately requires an unchanged catalog.
    Never replace its functions, root, source pins or refusal with this result.
    """
    folder=root/'docs/literature/sambamba-streaming-workflow-development';proof=load(folder/'STATUS.json')
    entries=list(folder.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in entries) or {p.name for p in entries}!=set(proof['public_files'])|{'STATUS.json'}:raise ValueError('Complete immutable streaming evidence membership differs')
    for name,identity in proof['public_files'].items():
        if pin(folder/name)!=identity:raise ValueError('Immutable streaming public evidence differs: '+name)
    own=load(folder/'SOURCE-PINS.json')
    def archive_check(path,expected):
        with zipfile.ZipFile(path) as z:
            if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(expected):raise ValueError('Complete streaming archive membership differs: '+str(path))
            for name,identity in expected.items():
                if rawpin(z.read(name))!=identity:raise ValueError('Immutable streaming archive member differs: '+name)
    archive_check(folder/'own-source-snapshot.zip',dict(own,**{'LICENSE-project.txt':proof['project_license']}))
    archive_check(folder/'validation-metadata.zip',load(folder/'METADATA-ARCHIVE-PINS.json'))
    for name,identity in own.items():
        if (root/name).is_symlink() or pin(root/name)!=identity:raise ValueError('Actual streaming scientific source differs: '+name)
    for name,identity in load(root/'docs/literature/sambamba-execution-development/VALIDATION.json')['tested_sources'].items():
        if pin(root/name)!=identity:raise ValueError('Frozen v1 source/test differs: '+name)
    expected=load(root/'docs/literature/sambamba-workflow-development/CATALOG-METADATA-PINS.json')
    if set(before)!=set(META) or {name:rawpin(raw) for name,raw in before.items()}!=expected:raise ValueError('Exact eight historical streaming catalog metadata bytes required')
    if proof['status']!='PASS_SCOPED_ORIGINAL_SAMBAMBA_STREAMING_V2_WORKFLOW_CONTROLS':raise ValueError('Exact original streaming qualification required')
    return dict(status='PASS_SEPARATE_CATALOG_CONSUMER_OF_IMMUTABLE_STREAMING_RECORDS',current_project_sources_match_snapshot=True,
        historical_catalog_metadata_exactly_reconstructed=True,original_standalone_reader_passes_changed_catalog=False,
        source_functions_or_Git_overlaid=False,complete_private_raw_files_checked=0,public_readback_checks_large_raw_data=False,
        fresh_scientific_execution_performed=False,largest_recorded_records_per_BAM=proof['largest_actual_records_per_BAM'],
        BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False)

def qualification(root,records_out,before):
    if Path(root).resolve()!=ROOT:raise ValueError('Actual scientific source tree required, never a reconstructed source overlay')
    records_out.mkdir(exist_ok=False);results={}
    readers=[('native_bam_component','sambamba_workflows/check_development.py'),('replay_and_spill','sambamba_execution_validation/qualification_records.py'),('per_file_bai_controls','bai_validation/records.py')]
    for name,reader in readers:
        command=[sys.executable,'-X','utf8','-B',str(root/'tools/literature'/reader)]
        with (records_out/(name+'.stdout')).open('xb') as so,(records_out/(name+'.stderr')).open('xb') as se:
            process=subprocess.run(command,cwd=root,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
        (records_out/(name+'.process.json')).write_bytes(encoded(dict(command=command,exit_code=process.returncode,scientific_verifier_or_import_environment_modified=False)))
        if process.returncode:raise ValueError('Unchanged public scientific record reader refused: '+reader)
        results[name]=load(records_out/(name+'.stdout'))
    results['streaming_v2']=streaming_catalog_readback(root,before)
    (records_out/'streaming-catalog-consumer.json').write_bytes(encoded(results['streaming_v2']))
    if any(row['current_project_sources_match_snapshot'] is not True for row in results.values()):raise ValueError('A preserved original/native source snapshot differs')
    if results['streaming_v2']['largest_recorded_records_per_BAM']!=4000000 or results['per_file_bai_controls']['wide_pairs']!=51 or results['per_file_bai_controls']['wide_mapped_queries']!=30070:raise ValueError('Complete scientific qualification scope differs')
    application=load(root/'docs/literature/application-source-transition-20261116/QUALIFICATION.json')
    if application['target']!='a97510234fa2fb992341d5d6ad9bf2741204a639' or application['application_checks']['fresh_successful_original_mark_remove_phases']!=8:raise ValueError('Tested normal CLI source transition required')
    return dict(status='PASS_SCOPED_SAMBAMBA_SOURCE_AND_COMPLETE_PUBLIC_RECORD_READBACK',components=results,
        source_transition=pin(root/'docs/literature/application-source-transition-20261116/QUALIFICATION.json'),backend_source=pin(root/PROGRAM),dispatcher_source=pin(root/DISPATCH),
        reported_supported_editions=['0.6.6','0.8.2'],unsupported_declared_editions=['0.6.5'],raw_large_data_publicly_packaged=False,
        public_readback_repeats_original_classification=False,original_failures_and_old_receipts_preserved=True,independent_duplicate_classifier=False,
        upstream_family_credit=1,native_family_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False)
def load_catalog_registry(root=ROOT):
    rows=copy.deepcopy(previous.load_catalog_registry(root))
    if len(rows)!=485 or len({row['id'] for row in rows})!=485:raise ValueError('Complete485-definition predecessor required')
    row=next(row for row in rows if row['id']=='sambamba')
    row.update(role='Alignment processing and duplicate marking/removal',current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE)
    return rows
def call_predecessor(root,before,out,parent,baseline,rescan):
    folder=out/'predecessor';folder.mkdir(exist_ok=False)
    result=previous.verify(root,parent,baseline,folder,rescan,catalog_metadata=before,extensions=extension_pins(root))
    if result['new_scoped_integrations']!=122 or result['missing']!=265 or result['new_scoped_integration_credit']!=0:raise ValueError('Exact complete predecessor scope required')
    (folder/'COMPLETED.json').write_bytes(encoded(result));return result
def apply(root,out,parent,baseline,author_records,unsupported_records):
    if any((root/name).exists() for name in [EVIDENCE,PROOF,COMPATIBILITY]):raise ValueError('Preserve and verify existing admission')
    before={name:(root/name).read_bytes() for name in META}
    predecessor=call_predecessor(root,before,out,parent,baseline,True);science=qualification(root,out/'scientific-readbacks',before);primary=census(root,json.loads(before[PAPERS]))
    author=load(author_records/'REVIEW.json');unsupported=load(unsupported_records/'COMPLETED.json')
    if author['commit']!='34dba322b1be72b40ca4c0e76fe31fada5668f66' or len(author['selected_source_files'])!=9 or author['sambamba_occurrences']!=0 or author['actual_operation_resolved']:
        raise ValueError('Exact bounded author-code review required; do not infer a missing operation')
    if unsupported['status']!='PASS_SAVED_065_REJECTION_OBSERVATION_AFTER_VALIDATOR_CASE_REVIEW' or unsupported['unsupported_edition_accepted'] or unsupported['production_changed']:
        raise ValueError('Real unsupported-edition refusal and retained validator failure review required')
    after=transition(root,before);result=check_transition(root,before,after)
    folder=root/EVIDENCE;folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in before.items():z.writestr(name,raw)
    for name,value in [('PREDECESSOR.json',predecessor),('QUALIFICATION.json',science),('CENSUS.json',primary),('AUTHOR-CODE-REVIEW.json',author),('UNSUPPORTED-EDITION-REVIEW.json',unsupported),('REGISTRY.json',load_catalog_registry(root))]:
        (folder/name).write_bytes(encoded(value))
    (folder/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());(root/COMPATIBILITY).write_bytes(encoded(compatibility(before,after)))
    if any((root/name).read_bytes()!=raw for name,raw in before.items()):raise ValueError('Concurrent metadata change; no overwrite allowed')
    (out/'PREPARED.json').write_bytes(encoded(dict(before_files={name:rawpin(raw) for name,raw in before.items()},after_files={name:rawpin(raw) for name,raw in after.items()},**result)))
    for name,raw in after.items():(root/name).write_bytes(raw)
    sources=sorted(path.relative_to(root).as_posix() for path in (root/MODULE).iterdir() if path.is_file())+[GUIDE]
    evidence=sorted(path.relative_to(root).as_posix() for path in folder.iterdir() if path.is_file())+[COMPATIBILITY]
    proof=dict(status=STATUS,**result,admitted_tool_ids=['sambamba'],before_files={name:rawpin(raw) for name,raw in before.items()},after_files={name:pin(root/name) for name in META},
        source_pins={name:pin(root/name) for name in sources},evidence_pins={name:pin(root/name) for name in evidence},old_scientific_sources_overridden=False,
        old_verifiers_or_Git_monkeypatched=False,source_build_required=True,released_binary_contains_adapter=False,P082_analysis_implemented=False,
        preceding_whole_catalog_reverified=True,fresh_scientific_calls_repeated_by_catalog_gate=False)
    (root/PROOF).write_bytes(encoded(proof));return proof
def verify(root=ROOT,parent=None,baseline=None,out=None,rescan=False):
    proof=load(root/PROOF)
    if proof['status']!=STATUS:raise ValueError('Exact scoped Sambamba admission required')
    for kind in ['source_pins','evidence_pins','after_files']:
        for name,identity in proof[kind].items():
            if pin(root/name)!=identity:raise ValueError('Catalog source/evidence differs: '+name)
    own_actual={path.relative_to(root).as_posix() for path in (root/MODULE).iterdir() if path.is_file()}
    if own_actual!={name for name in proof['source_pins'] if name.startswith(MODULE+'/')}:raise ValueError('Complete consumer implementation membership differs')
    before=load_before(root)
    if {name:rawpin(raw) for name,raw in before.items()}!=proof['before_files']:raise ValueError('Complete predecessor metadata differs')
    after={name:(root/name).read_bytes() for name in META};result=check_transition(root,before,after)
    if load(root/COMPATIBILITY)!=compatibility(before,after):raise ValueError('Exact inverse review journal differs')
    if load_catalog_registry(root)!=load(root/EVIDENCE/'REGISTRY.json'):raise ValueError('Complete derived registry differs')
    if rescan and census(root)!=load(root/EVIDENCE/'CENSUS.json'):raise ValueError('Full primary-source census differs')
    predecessor=call_predecessor(root,before,out,parent,baseline,rescan);science=qualification(root,out/'scientific-readbacks',before)
    for name,identity in proof['after_files'].items():
        if pin(root/name)!=identity:raise ValueError('Metadata changed during verification')
    return dict(status=STATUS,**result,predecessor=predecessor['status'],scientific_qualification=science,primary_sources_rescanned=rescan,
        exact_predecessor_metadata_reconstructed=True,all_other_reviews_and_tools_unchanged=True,preceding_scientific_sources_overridden=False,
        fresh_scientific_calls_repeated_by_catalog_gate=False,source_build_required=True,released_binary_contains_adapter=False,whole_paper_reproduced=False)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    p.add_argument('--parent-checkout',type=Path,required=True);p.add_argument('--baseline-checkout',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');p.add_argument('--author-code-records',type=Path);p.add_argument('--unsupported-edition-records',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        target=a.out.resolve()
        if not target.is_relative_to(ROOT/'build'):raise ValueError('Use NEW owned build output')
        target.mkdir(parents=True,exist_ok=False);out=target;(out/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+ACTION+'\n').encode())
        if a.apply:
            if a.author_code_records is None or a.unsupported_edition_records is None:raise previous.PrerequisiteUnavailable('Apply requires retained author-code and unsupported-edition control records')
            result=apply(ROOT,out,a.parent_checkout,a.baseline_checkout,a.author_code_records,a.unsupported_edition_records)
        else:result=verify(ROOT,a.parent_checkout,a.baseline_checkout,out,a.rescan_sources)
        (out/('APPLIED.json' if a.apply else 'COMPLETED.json')).write_bytes(encoded(result));print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,(previous.PrerequisiteUnavailable,FileNotFoundError)) else 2;label='SKIP3_SAMBAMBA_CATALOG_UNAVAILABLE' if code==3 else 'FAIL2_SAMBAMBA_CATALOG_UNADMITTED'
        if out is not None:
            try:
                for marker in ['COMPLETED.json','APPLIED.json']:
                    if (out/marker).exists():(out/marker).rename(out/('INVALIDATED-'+uuid.uuid4().hex+'-'+marker))
                (out/'FAILURE.json').write_bytes(encoded(dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
            except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        print(label,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
