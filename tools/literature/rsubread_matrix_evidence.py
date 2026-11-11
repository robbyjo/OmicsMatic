# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete declared native matrix component; never grant whole-Rsubread or paper credit."""
import argparse,io,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

BASE='build/literature-review/rsubread-runtime/'
DIRECTORY='docs/literature/rsubread-matrix-development'
STATUS='passed-source-bound-native-Rsubread-matrix-component-on-three-JVMs'


def verify(emit=True,directory=None,require_current_catalog=False,archive_only=False):
    home=ROOT/DIRECTORY if directory is None else Path(directory);p=json.loads((home/'VALIDATION.json').read_bytes());assert p['status']==STATUS and p['native_matrix_component_available'];assert not any(p[key] for key in ['implementation_credit','full_adapter_available','production_runtime_supervision_available','independent_alignment_assignment','independent_junction_counting','whole_package_implemented','whole_paper_reproduced','whole_goal_complete','scientific_source_exception_added']);files={}
    for name,record in p['archives'].items():
        if 'parts' in record:
            parts=[]
            for item in record['parts']:assert Path(item['name']).name==item['name'] and pin(home/item['name'])==item['pin'];parts.append((home/item['name']).read_bytes())
            raw=b''.join(parts);assert byte_pin(raw)==record['pin'];current=unpack(io.BytesIO(raw),record)
        else:assert pin(home/name)==record['pin'];current=unpack(home/name,record)
        for member,raw in current.items():assert member not in files or files[member]==raw;files[member]=raw
    for name,value in p['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==value,name
        if not archive_only:assert pin(ROOT/name)==value,name
    built=json.loads(files[BASE+'native-build-1/COMPLETED.json']);standalone=json.loads(files[BASE+'standalone-build-1/COMPLETED.json']);assert built['status']=='passed-additive-native-Rsubread-count-matrix-component-only' and built['artifact']==p['additive_artifact'] and built['parent_artifact']==p['historical_additive_parent_artifact'];assert all(p['source_pins'][name]==value for name,value in built['source_pins'].items());assert standalone['status']=='passed-standalone-JDK-only-native-Rsubread-matrix-library' and standalone['artifact']==p['built_artifact'] and standalone['additive_comparison']['complete_class_family_byte_identical'];jar=home/'rsubread-count-matrix-api.jar';raw=files['packaged-api/rsubread-count-matrix-api.jar'];assert byte_pin(raw)=={key:p['artifact'][key] for key in ['bytes','sha256']}
    if not archive_only:assert pin(jar)==byte_pin(raw)
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        assert set(p['native_class_entries'])==set(built['added_entries'])
        assert set(z.namelist())==set(p['native_class_entries'])|{'META-INF/LICENSE','META-INF/MANIFEST.MF'}
        for name,value in p['native_class_entries'].items():assert byte_pin(z.read(name))==value==byte_pin(files[BASE+'native-build-1/classes/'+name])==byte_pin(files[BASE+'standalone-build-1/classes/'+name])
        assert byte_pin(z.read('META-INF/LICENSE'))==standalone['source_pins']['LICENSE']==byte_pin(files['standalone-documentation/LICENSE'])
    read=lambda name:json.loads(files[BASE+name+'/COMPLETED.json']);setup=read('reference-setup-3');source=read('reported-source-2');oldbuild=read('reported-build-1');reported=read('r441-reported-build-1');assert setup['status']=='passed-private-original-Rsubread2260-reference-installed-and-source-body-checked-only' and setup['original_function_body_equal'] and setup['original_formals_equal'];assert source['status']=='passed-official-ancestry-Rsubread2232-source-recovered-only' and source['source']['selected_commit']=='666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6' and len(source['source']['matching_commits'])==22 and not source['paper_binary_identified'];assert oldbuild['status']=='FAIL2_RSUBREAD2232_SOURCE_BUILD' and not oldbuild['analysis_admitted'];assert reported['status']=='passed-private-unmodified-Rsubread2232-source-build-on-R441-only' and reported['original_function_body_equal'] and reported['original_formals_equal'] and reported['captured_source_unchanged'] and len(reported['scientific_work_source_unchanged'])==105 and reported['protected_library_unchanged'] and not reported['paper_binary_identified'] and not reported['paper_source_commit_identified']
    profiles=[('2260','original-controls-3','matrix-native-1','passed-sixteen-complete-original-Rsubread2260-counting-and-ten-original-cbind-controls-only','passed-three-JVM-complete-original-Rsubread-matrices-and-cbind-component-only'),('2232-R441','original2232-controls-1','matrix2232-native-1','passed-sixteen-complete-original-Rsubread2232-R441-counting-and-ten-original-cbind-controls-only','passed-three-JVM-complete-original-Rsubread2232-R441-matrices-and-cbind-component-only')]
    for edition,controls,native,status,native_status in profiles:
        original=read(controls);test=read(native);assert original['status']==status and len(original['original_cases'])==16 and len(original['bind_cases'])==10 and original['original_error_controls']==1 and original['original_package_unchanged'] and not original['namespace_patched'];assert test['status']==native_status and test['artifact']==p['additive_artifact'] and test['original_evidence_unchanged'] and set(test['runs'])=={'windows21','windows25','linux21'}
        for run in test['runs'].values():assert (run['original_count_matrices'],run['original_cbind_positive'],run['original_cbind_errors'],run['strict_name_mismatch_rejections'],run['contract_rejections'],run['numeric_values_replayed'],run['names_replayed'])==(16,9,1,1,42,265,179) and not run['implementation_credit'] and not run['full_adapter_available']
        for case in original['original_cases']:
            prefix=BASE+controls+'/original/'+case['id']+'/'
            for name in ['complete-original-result.rds','complete-original-result.R','counts.matrix','arguments.rds','arguments.R','annotation.tsv','stat.tsv']:assert prefix+name in files
        assert files[BASE+controls+'/original/invalid-fragment-options/original-error.txt'].startswith(b'EXPECTED_ORIGINAL_ERROR')
        for report in [original,test]:
            for name,value in report['source_pins'].items():assert p['source_pins'][name]==value
    audit=read('primary-roles-1');assert audit['status']=='passed250-primary-XML-DOI-and2465-anchors-with-six-Rsubread-role-drafts-only' and (audit['source_papers'],audit['observations'],audit['roles'],audit['prior_roles'],audit['new_role_drafts'])==(250,2465,6,3,3) and not audit['catalog_changed'] and not audit['implementation_credit'];draft=json.loads(files[BASE+'primary-roles-1/role-drafts.json']);assert len(draft['reviews'])==6
    for name,value in p['catalog_snapshot'].items():assert byte_pin(files['catalog-snapshot/'+name])==value
    if require_current_catalog:
        for name,value in p['catalog_snapshot'].items():assert pin(ROOT/name)==value,name
    portable=read('standalone-native-1');assert portable['status']=='passed-JDK-only-both-original-Rsubread-matrix-reference-families' and portable['artifact']==p['built_artifact'] and not portable['prior_project_JAR_on_classpath'] and not portable['R_runtime_required'] and set(portable['runs'])=={'2260','2232-R441'}
    for family in portable['runs'].values():
        assert set(family)=={'windows21','windows25','linux21'}
        for run in family.values():assert (run['original_count_matrices'],run['original_cbind_positive'],run['original_cbind_errors'],run['contract_rejections'])==(16,9,1,42)
    if not archive_only:
        from prepare_rsubread_standalone import verify as source_derivation
        source_derivation()
    if require_current_catalog:assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged_at_checkpoint']
    assert p['original_count_matrices']==32 and p['successful_original_bindings']==18 and p['original_binding_errors']==2 and p['contract_rejections_per_family_per_jvm']==42 and not p['prior_project_JAR_required'] and not p['R_runtime_required_for_matrix_API']
    if emit:print(('Verified archived observations only; no current artifact/runtime/refit verified. ' if archive_only else 'Verified standalone JDK-only native matrix component. ')+'32 complete original count matrices,18 successful bindings, two original row-count errors and42 rejection controls per family on each of three JVMs. Whole Rsubread adapter and catalog credit remain unfinished.')
    return p,files


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--require-current-catalog',action='store_true');parser.add_argument('--archive-only',action='store_true');parser.add_argument('--export-controls',type=Path);a=parser.parse_args()
    if a.export_controls and not a.archive_only:parser.error('Export requires --archive-only: frozen observations do not certify a current runtime or new R fit')
    p,files=verify(require_current_catalog=a.require_current_catalog,archive_only=a.archive_only)
    if a.export_controls:
        out=a.export_controls.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
        for name,prefix in [('2260','original-controls-3'),('2232-R441','original2232-controls-1')]:
            marker=BASE+prefix+'/'
            for member,raw in files.items():
                if member.startswith(marker):file=out/name/member.removeprefix(marker);assert file.resolve().is_relative_to(out);file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(raw)
        print('Exported exact frozen original observations; no R fit or new biological analysis was run')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/archive/component gates')
    main()
