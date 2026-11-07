# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently inspect frozen background workflow coverage, real failure/skip controls, examples and limits."""
import json,re,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
from validate_mashr_native import pin
from signac_scan_evidence import unpack
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';EVIDENCE=ROOT/'docs/literature/chromvar-background-development'
def read(p):return json.loads(p.read_bytes())
def main():
    output=BASE/'background-final-integrity-1.json';assert not output.exists();record=read(EVIDENCE/'VALIDATION.json');assert record['status']=='passed-original-chromVAR-background-native-GC-and-deviation-workflow-development' and pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items());files=unpack(EVIDENCE/'controls.zip',record);assert len(files)==record['logical_files'] and all(files['current-source/'+n]==(ROOT/n).read_bytes() for n in record['source_pins'])
    prefix='build/literature-review/signac-runtime/'
    def archived(n):return json.loads(files[prefix+n])
    matrix=archived('background-workflow-matrix-1/COMPLETED.json');chain=archived('background-reference-chain-1/COMPLETED.json');recovery=archived('background-recovery-1/COMPLETED.json');examples=archived('background-examples-1/COMPLETED.json');build=archived('background-workflow-build-2/BUILD.json')
    expected={(r['id'],profile) for r in archived('background-original-controls-1/cases.json')['cases'] for profile in ['1.16.0-source','1.34.1-package']};runs=matrix['original_background_and_RNG_comparisons']+matrix['retained_source_failures'];assert len(expected)==len(runs)==30 and {(r['case'],r['profile']) for r in runs}==expected and len(matrix['original_background_and_RNG_comparisons'])==18 and len(matrix['retained_source_failures'])==12 and matrix['captured_verifications']==54 and matrix['failed_capture_rejections']==36
    for run in matrix['original_background_and_RNG_comparisons']:
        assert run['original_background_indices_and_RNG_match'] and run['captured_verifications']==3;root=run['analysis_path'];assert json.loads(files[root+'/manifest.json'])['results_admitted'] and json.loads(files[root+'/analysis-status.json'])['status']=='completed-with-warnings' and b'VERIFY_SCOPE' in files[root+'/WARNINGS.md'];assert root+'/background.tsv' in files
    for run in matrix['retained_source_failures']:
        root=run['analysis_path'];assert run['no_result_admitted'] and run['failed_capture_rejections']==3 and not json.loads(files[root+'/manifest.json'])['results_admitted'] and root+'/background.tsv' not in files
    assert chain['actual_original_indexed_reference_formula_and_public_background_and_deviations'] and chain['native_reference_GC_used'] and len(chain['runs'])==2 and chain['captured_verifications']==12 and all(r['original_reference_GC_backgrounds_RNG_and_scores_match'] for r in chain['runs'])
    original=archived('background-reference-chain-1/original-reference/original.json');assert original['original_public_getBackgroundPeaks_and_computeDeviations_executed'] and len(original['background'])==20 and len(original['bias'])==20 and len(original['z'])==3
    assert len(recovery['rehashed_single_change_rejections'])==14 and all(r['rejected_platforms']==3 for r in recovery['rehashed_single_change_rejections']) and recovery['total_rehashed_rejections']==42 and recovery['unlisted_nested_rejections']==3 and recovery['copied_verifications']==3 and recovery['known_coordinated_forgery_structurally_accepted']==3 and recovery['known_forgery_is_NOT_an_analysis_result'] and recovery['existing_output_preserved'] and recovery['failed_capture_rejections']==21
    limitation=archived('background-recovery-1/KNOWN_LIMITATION.json');assert limitation['status']=='EXPECTED_STRUCTURAL_ACCEPTANCE_OF_COORDINATED_FORGERY' and limitation['accepted_platforms']==3
    for platform in ['windows-java21','windows-java25','linux-java21']:assert archived('background-recovery-1/known-no-authentication-limit-'+platform+'.process.json')['exit_code']==0
    assert len(recovery['retained_gates'])==7 and sum(v['exit_code']==3 for v in recovery['retained_gates'])==2
    for gate in recovery['retained_gates']:
        root=gate['analysis_path'];m=json.loads(files[root+'/manifest.json']);assert not m['results_admitted'] and gate['no_result_admitted'] and root+'/background.tsv' not in files
        if gate['case']=='actual-R-missing-package':assert m['process_exit_code']==3 and b'skipped-dependency-unavailable' in files[root+'/worker/worker-status.tsv']
        if gate['case']=='actual-owned-timeout':assert m['owned_process_cleanup'] and all(not v['alive_after_cleanup'] for v in m['owned_process_cleanup'])
    assert (examples['literal_BACKGROUND_requests'],examples['literal_DEVIATIONS_requests'],examples['literal_VERIFY_requests'],examples['original_comparisons'],examples['captured_verifications'],examples['existing_output_refusals'])==(2,2,4,7,21,4)
    assert len(examples['runs'])==7 and all(v['original_comparison'] and v['captured_verifications']==3 for v in examples['runs']) and examples['additional_fresh_Java25_BACKGROUND'] and examples['additional_fresh_Java25_and_Linux_DEVIATIONS'] and examples['inputs_and_requests_unchanged']
    gradle=archived('background-gradle-native-1/COMPLETED.json');assert gradle['tests']==gradle['passed']==369 and gradle['skipped']==0 and gradle['process']['exit_code']==0
    for name,count in [('ChromVarGcBiasTest',49),('ChromVarDeviationsTest',320)]:
        suite=ET.fromstring(files[prefix+'background-gradle-native-1/TEST-org.jlinalg.cli.'+name+'.xml']);assert (int(suite.attrib['tests']),int(suite.attrib['failures']),int(suite.attrib['errors']),int(suite.attrib['skipped']))==(count,0,0,0)
    assert archived('background-request-probe-1/FAILURE.json')['status']=='FAILED_ACTUAL_BACKGROUND_REQUEST_PROBE' and b'invalid input found' in files[prefix+'background-request-probe-1/Unicode β analysis/worker/worker-warnings.tsv'] and archived('background-request-probe-2/DIAGNOSIS.json')['adapter_change_required'] is False and archived('background-request-probe-3/COMPLETED.json')['captured_verifications']==3
    parents=[ROOT/'docs/literature/chromvar-gc-development/VALIDATION.json',ROOT/'docs/literature/chromvar-deviations-development/VALIDATION.json'];assert pin(parents[0])==record['parent_validation'] and pin(parents[1])==record['ancestor_deviation_validation']
    for p in parents:
        parent=read(p);assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items())
    assert pin(parents[0].parent/'controls.zip')==record['parent_archive'] and len(read(parents[0])['source_pins'])==30 and len(read(parents[1])['source_pins'])==437
    for a in [record['artifact'],record['parent_artifact']]:assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']}
    assert record['artifact']==build['artifact'] and record['artifact']['sha256']=='9627cee2aea5a58248dfb5848800d644429de11dd952d4f9f30cc08f78409c34'
    with zipfile.ZipFile(ROOT/record['parent_artifact']['path']) as a,zipfile.ZipFile(ROOT/record['artifact']['path']) as b:assert all(a.read(n)==b.read(n) for n in a.namelist()) and len(set(b.namelist())-set(a.namelist()))==12
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_preserved'];catalog=json.loads(files['unchanged-catalog.json']);assert len(catalog)==402
    for n,v in catalog.items():assert pin(ROOT/'docs/literature/omics-2023-2026'/n)==v
    assert (record['new_integrations'],record['including_existing'],record['missing'],record['reviewed_uses'])==(68,71,259,827) and record['end_user_background_development_adapter_available'] and record['native_reference_GC_and_native_deviation_chain_available'] and record['forged_fixture_is_NOT_an_analysis_result']
    assert not any(record[k] for k in ['independent_native_RNG_replay','installed_DLL_source_authentication','historical_whole_packages_executed','chromVAR_1_5_0_executed','full_Signac_workflow_available','root_dispatch_available','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete'])
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text();links=0
    for doc in [ROOT/'docs/literature/CHROMVAR_BACKGROUND_DEVELOPMENT.md',ROOT/'examples/chromvar-background/README.md']:
        for link in re.findall(r'\]\(([^)]+)\)',doc.read_text(encoding='utf8')):
            if not re.match(r'^[a-zA-Z]+:',link):assert (doc.parent/link.split('#',1)[0]).resolve().exists();links+=1
    result=dict(status='passed-independent-original-chromVAR-background-workflow-integrity',archive=record['archive'],physical_members=record['archive_members'],logical_files=record['logical_files'],current_source_pins=len(record['source_pins']),original_background_RNG_comparisons=18,retained_source_failures=12,captured_verifications=54,failed_source_capture_rejections=36,reference_chain_verifications=12,rehashed_single_change_rejections=42,actual_dependency_skips=2,actual_owned_timeout_cleanup=True,known_no_authentication_limit_demonstrated=True,checked_example_original_comparisons=7,checked_example_verifications=21,normal_Gradle_tests_passed=369,parent_30_and_437_source_pins_unchanged=True,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,canonical_preserved=True,literature_pages_withheld=True,local_document_links_checked=links,root_dispatch_available=False,catalog_implementation_credit=False,pushed=False,whole_goal_complete=False)
    output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent background milestone verification')
    main()
