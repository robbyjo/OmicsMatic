# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently verify frozen native chromVAR controls, source bindings, user populations and parent/catalog."""
import json,re
from pathlib import Path
from signac_scan_evidence import unpack
from validate_mashr_native import pin
from audit_chromvar_shared_build import audit as audit_shared_build
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';EVIDENCE=ROOT/'docs/literature/chromvar-deviations-development'
def read(p):return json.loads(p.read_bytes())
def main():
    target=BASE/'chromvar-final-integrity-1.json';assert not target.exists();record=read(EVIDENCE/'VALIDATION.json');assert record['status']=='passed-native-chromVAR-prepared-background-development' and pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items());files=unpack(EVIDENCE/'controls.zip',record);assert len(files)==record['logical_files'] and all(files['current-source/'+n]==(ROOT/n).read_bytes() for n in record['source_pins'])
    prefix='build/literature-review/signac-runtime/'
    def archived(name):return json.loads(files[prefix+name])
    native=archived('chromvar-native-matrix-2/COMPLETED.json');workflow=archived('chromvar-workflow-matrix-1/COMPLETED.json');recovery=archived('chromvar-recovery-1/COMPLETED.json');examples=archived('chromvar-examples-1/COMPLETED.json');integration=archived('chromvar-scan-matrix-probe-1/COMPLETED.json')
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==320 and v['skipped']==0 for v in native['platform_validation']) and native['original_method_calls']==306
    assert len(workflow['original_statistical_comparisons'])==86 and len(workflow['retained_source_failures'])==16 and workflow['captured_verifications']==258 and workflow['inputs_and_requests_unchanged']
    expected=set()
    for family,folder in [('basic','chromvar-deviations-original-3'),('extended','chromvar-extended-original-1')]:
        for v in archived(folder+'/original.json')['results']:expected.add((family,v['id'][0],v['profile'][0]))
    actual={(v['family'],v['case'],v['profile']) for v in workflow['original_statistical_comparisons']+workflow['retained_source_failures']};assert len(expected)==len(actual)==102 and expected==actual
    for run in workflow['original_statistical_comparisons']:
        root=run['analysis_path'];assert json.loads(files[root+'/analysis-status.json'])['status']=='completed-with-warnings' and run['original_statistics_match'] and run['captured_verifications']==3;diagnostics=json.loads(files[root+'/diagnostics.json']);assert diagnostics['motifs']>0 and diagnostics['cells']>0
        warning=files[root+'/WARNINGS.md'].decode('utf8');assert 'EXPLICIT_PREPARED_BACKGROUND' in warning and 'SOURCE_SINGLE_PEAK_PRECISION' in warning
    for run in workflow['retained_source_failures']:assert run['no_result_admitted'] and run['analysis_path']+'/deviations.tsv' not in files
    assert len(recovery['rehashed_rejections'])==18 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections']) and recovery['unlisted_nested_file_rejections']==3 and recovery['copied_verifications']==3 and recovery['existing_output_preserved'] and len(recovery['retained_failure_gates'])==4 and recovery['failed_capture_rejections']==12
    assert (examples['literal_DEVIATIONS_requests'],examples['additional_fresh_platform_runs'],examples['original_statistical_comparisons'],examples['captured_verifications'])==(3,2,5,16) and examples['literal_VERIFY_request_executed'] and examples['inputs_and_requests_unchanged']
    assert integration['actual_original_public_computeDeviations_executed'] and integration['original_statistics_match'] and integration['Unicode_cell_IDs'] and integration['captured_verifications']==3 and (integration['peaks'],integration['cells'],integration['motifs'])==(80,12,3)
    before=archived('chromvar-native-matrix-1/FAILURE.json');assert before['status']=='FAILED_NATIVE_CHROMVAR_CONTROLS';arithmetic=archived('chromvar-arithmetic-probe-1/original.json');assert arithmetic['base_matches'] is True and arithmetic['reciprocal_matches'] is False and arithmetic['value_kinds']['NaN_div_NA']=='NaN' and arithmetic['value_kinds']['NA_div_NaN']=='NA'
    runtime=json.loads(files['runtime-content-inventory.json']);assert (runtime['base_files'],runtime['private_files'],runtime['motif_private_files'])==(23902,939,1417) and runtime['base_unchanged'] and runtime['private_unchanged'] and runtime['motif_private_unchanged'];catalog=json.loads(files['unchanged-catalog.json']);assert len(catalog['checksums'])==402 and (catalog['papers'],catalog['new_integrations'],catalog['including_existing'],catalog['missing'],catalog['reviewed_uses'])==(250,68,71,259,827)
    for n,v in catalog['checksums'].items():assert pin(ROOT/'docs/literature/omics-2023-2026'/n)==v
    parent=ROOT/'docs/literature/signac-scan-development';prior=read(parent/'VALIDATION.json');assert len(prior['source_pins'])==385 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'VALIDATION.json')==record['parent_scan_validation'] and pin(parent/'controls.zip')==record['parent_scan_archive']
    artifact=record['artifact'];assert artifact['bytes']==164869429 and artifact['sha256']=='c74988bfe206cdc0e098b2ede00c0710a9db0d58f7d7b322631e530ab0484df9'
    for a in [artifact,record['native_API_artifact']]:assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']}
    observation=archived('chromvar-freeze-attempt-1/canonical-observation.json');assert observation==audit_shared_build() and pin(BASE/'chromvar-freeze-attempt-1/canonical-observation.json')==record['canonical_observation'] and observation['canonical_executable_at_validation']==record['canonical_executable_at_validation'] and observation['canonical_executable_at_freeze']==record['canonical_executable_at_freeze'] and record['canonical_executable_changed_since_validation'] and record['canonical_shared_build_audited'] and not record['canonical_complete_test_claim'] and record['native_analysis_requires_no_R'] and record['all_three_R_libraries_unchanged']
    assert (record['original_method_calls'],record['focused_admission_properties'],record['source_profile_case_protocols'],record['case_populations'])==(306,14,102,51)
    assert (record['native_user_request_comparisons'],record['retained_source_failure_gates'],record['native_user_request_captured_verifications'],record['rehashed_corruption_rejections'])==(86,16,258,54)
    assert not any(record[k] for k in ['background_sampling_performed','motif_scanning_performed','historical_whole_packages_executed','chromVAR_1_5_0_executed','full_Signac_workflow_available','catalog_implementation_credit','root_dispatch_available','released','pushed','deployed','whole_goal_complete'])
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text();links=0
    for doc in [ROOT/'docs/literature/CHROMVAR_DEVIATIONS_DEVELOPMENT.md',ROOT/'examples/chromvar-deviations/README.md']:
        for link in re.findall(r'\]\(([^)]+)\)',doc.read_text(encoding='utf8')):
            if not re.match(r'^[a-zA-Z]+:',link):assert (doc.parent/link.split('#',1)[0]).resolve().exists(),(doc,link);links+=1
    result=dict(status='passed-independent-current-chromVAR-native-development-integrity',archive=record['archive'],physical_archive_members=record['archive_members'],logical_files=record['logical_files'],current_source_pins=len(record['source_pins']),native_tests_per_platform=320,platforms=3,original_method_calls=306,native_user_request_comparisons=86,retained_source_failures=16,captured_verifications=258,rehashed_corruption_rejections=54,additional_failure_gates=4,checked_example_verifications=16,scanned_matrix_original_public_interface_comparison=True,Unicode_cell_IDs_verified=True,native_analysis_requires_no_R=True,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_shared_build_audited=True,complete_canonical_test_claim=False,prior_scan_sources_unchanged=True,literature_pages_withheld=True,local_document_links_checked=links,pushed=False,whole_goal_complete=False);save=target.write_text;save(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent native milestone verification')
    main()
