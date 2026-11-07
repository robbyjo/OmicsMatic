# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently verify frozen scan evidence, actual result populations and the unchanged catalog/parent."""
import hashlib,json,re,zipfile
from signac_scan_evidence import unpack
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
EVIDENCE=ROOT/'docs/literature/signac-scan-development'
def read(p):return json.loads(p.read_bytes())
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())

def main():
    target=BASE/'scan-final-integrity-1.json';assert not target.exists();record=read(EVIDENCE/'VALIDATION.json');assert record['status']=='passed-guarded-Signac-scan-enrichment-development'
    assert pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    files=unpack(EVIDENCE/'controls.zip',record)
    class LogicalView:
        def read(self,name):return files[name]
        def namelist(self):return list(files)
    z=LogicalView()
    if files:
        assert len(z.namelist())==len(set(z.namelist()))==record['logical_files'] and set(z.namelist())==set(record['logical_file_pins'])
        for name,want in record['logical_file_pins'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and path.as_posix()==name and '\\' not in name
            raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==want
        assert all(z.read('current-source/'+n)==(ROOT/n).read_bytes() for n in record['source_pins'])
        prefix='build/literature-review/signac-runtime/'
        def archived(name):return json.loads(z.read(prefix+name))
        workflow=archived('scan-workflow-boundary-verification-1/COMPLETED.json');original=archived('scan-original-7/original.json');recovery=archived('scan-pfm-recovery-2/COMPLETED.json');examples=archived('scan-pfm-examples-boundary-1/COMPLETED.json');native=archived('scan-pfm-boundary-audit-after-1/COMPLETED.json')
        assert len(workflow['actual_original_fits'])==75 and len(workflow['retained_original_failure_gates'])==21 and workflow['captured_verifications']==225 and workflow['source_inputs_unchanged']
        expected={(v['profile'],v['id']) for v in original['results']};got={(v['profile'],v['case']) for v in workflow['actual_original_fits']+workflow['retained_original_failure_gates']};assert len(expected)==len(got)==96 and expected==got
        assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==211 and v['skipped']==0 for v in native['platform_validation'])
        for run in workflow['actual_original_fits']:
            assert run['actual_original_R_fit'] and run['original_enrichment_and_RNG_states_match'] and run['captured_verifications']==3
            root=run['analysis_path'];status=json.loads(z.read(root+'/analysis-status.json'));assert status['status']=='completed-with-warnings'
            assert z.read(root+'/effective-pfms.tsv')==z.read(root+'/worker/effective-pfms.tsv')
            diagnostics=json.loads(z.read(root+'/diagnostics.json'));assert diagnostics['PFM_constructor_profile']=='TFBSTools1.50.0-mode-integer' and diagnostics['effective_PFM_positions']>0
            assert 'UPSTREAM_PFM_CONSTRUCTOR' in z.read(root+'/WARNINGS.md').decode('utf8')
            if run['case']=='fractional-pfm':assert diagnostics['upstream_PFM_changed_counts']>0 and 'UPSTREAM_PFM_INTEGER_COERCION' in z.read(root+'/WARNINGS.md').decode('utf8')
        assert len(recovery['rehashed_rejections'])==34 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections']) and recovery['unlisted_nested_file_rejections']==3 and recovery['copied_verifications']==3 and recovery['existing_output_preserved']
        assert len(recovery['actual_dependency_skips'])==2 and all(v['exit_code']==3 and v['no_result_admitted'] for v in recovery['actual_dependency_skips'])
        assert len(recovery['actual_owned_process_checks'])==2 and all(v['actual_live_R_observed'] and v['direct_PID_and_creation_time_recheck'] and v['owned_processes_stopped'] and v['no_result_admitted'] for v in recovery['actual_owned_process_checks'])
        assert (examples['literal_FIT_requests'],examples['original_enrichment_and_RNG_comparisons'],examples['generated_native_enrichment_comparisons'],examples['captured_verifications'])==(3,4,3,22) and examples['actual_PFM_export_and_Java_import'] and examples['retained_source_Unicode_motif_ID_failure']
        constructor=archived('scan-pfm-constructor-1/COMPLETED.json');assert constructor['controls']==10
        history=archived('scan-workflow-matrix-2/COMPLETED.json');assert history['completed_fits_reused_without_reexecution']==56 and history['new_original_fits']==19 and history['captured_verifications']==225
        invalid=archived('scan-original-1/INVALIDATED.json');assert invalid
        failed_id=prefix+'scan-examples-1/Unicode β analysis/';assert json.loads(z.read(failed_id+'analysis-status.json'))['status']=='failed' and failed_id+'enrichment.tsv' not in z.namelist()
        assert 'Motif names in data matrix and PWM list are inconsistent' in z.read(failed_id+'WARNINGS.md').decode('utf8')
        runtime=json.loads(z.read('runtime-content-inventory.json'));assert (runtime['base_files'],runtime['private_files'],runtime['motif_private_files'])==(23902,939,1417) and runtime['base_unchanged'] and runtime['private_unchanged'] and runtime['motif_private_unchanged']
        catalog=json.loads(z.read('unchanged-catalog.json'));assert len(catalog['checksums'])==402 and (catalog['new_integrations'],catalog['including_existing'],catalog['missing'],catalog['reviewed_uses'],catalog['papers'])==(68,71,259,827,250)
        for n,v in catalog['checksums'].items():assert pin(ROOT/'docs/literature/omics-2023-2026'/n)==v
    parent=ROOT/'docs/literature/signac-motif-development';prior=read(parent/'VALIDATION.json');assert len(prior['source_pins'])==277 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'VALIDATION.json')==record['parent_motif_validation'] and pin(parent/'controls.zip')==record['parent_motif_archive']
    artifact=record['artifact'];assert artifact['bytes']==164805475 and artifact['sha256']=='b76cf00c52356e9eb589c1640fc693d10a12d5f6dd9af49786cc951da9853961'
    for a in [artifact,record['native_input_artifact'],record['native_audit_artifact']]:assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_unchanged'] and record['effective_PFM_slots_captured_and_audited'] and record['original_PFM_constructor_controls']==10
    assert (record['original_controls'],record['successful_original_controls'],record['retained_original_failures'],record['actual_workflow_original_comparisons'],record['workflow_captured_verifications'])==(96,75,21,75,225)
    assert (record['rehashed_corruption_rejections'],record['unlisted_nested_file_rejections'],record['actual_dependency_skips'],record['actual_owned_process_checks'])==(102,3,2,2)
    assert not any(record[k] for k in ['full_Signac_workflow_available','catalog_implementation_credit','root_dispatch_available','released','pushed','deployed','whole_goal_complete','historical_whole_packages_executed','independent_MOODS_KDE_RNG_replay','complete_runtime_DLL_host_closure_certified'])
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    docs=[ROOT/'docs/literature/SIGNAC_SCAN_DEVELOPMENT.md',ROOT/'examples/signac-scan/README.md'];links=0
    for doc in docs:
        for link in re.findall(r'\]\(([^)]+)\)',doc.read_text(encoding='utf8')):
            if not re.match(r'^[a-zA-Z]+:',link):assert (doc.parent/link.split('#',1)[0]).resolve().exists(),(doc,link);links+=1
    result=dict(status='passed-independent-current-Signac-scan-development-integrity',archive=record['archive'],archive_members=record['archive_members'],current_source_pins=len(record['source_pins']),native_tests_per_platform=211,platforms=3,original_controls=96,actual_upstream_comparisons=75,captured_verifications=225,rehashed_corruption_rejections=102,extra_inventory_rejections=3,checked_original_comparisons=4,generated_native_enrichment_comparisons=3,checked_example_verifications=22,original_constructor_controls=10,actual_dependency_skips=2,actual_owned_process_checks=2,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_unchanged=True,prior_motif_sources_unchanged=True,literature_pages_withheld=True,local_document_links_checked=links,pushed=False,whole_goal_complete=False)
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result),flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent evidence verification')
    main()
