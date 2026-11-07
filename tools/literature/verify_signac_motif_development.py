# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently check frozen motif evidence, scope, source snapshots and unchanged parent/catalog."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
EVIDENCE=ROOT/'docs/literature/signac-motif-development'

def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def main():
    target=BASE/'motif-final-integrity-1.json';assert not target.exists();record=read(EVIDENCE/'VALIDATION.json');assert record['status']=='passed-native-Signac-motif-enrichment-development'
    assert pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    with zipfile.ZipFile(EVIDENCE/'controls.zip') as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==record['archive_members'] and set(z.namelist())==set(record['archive_file_pins'])
        for name,want in record['archive_file_pins'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==want
        assert all(z.read('current-source/'+name)==(ROOT/name).read_bytes() for name in record['source_pins'])
        runtime=json.loads(z.read('runtime-content-inventory.json'));assert runtime['base_unchanged'] and runtime['private_unchanged'] and runtime['motif_private_unchanged'] and (runtime['base_files'],runtime['private_files'],runtime['motif_private_files'])==(23902,939,1417)
        catalog=json.loads(z.read('unchanged-catalog.json'));assert len(catalog['checksums'])==402 and (catalog['new_integrations'],catalog['including_existing'],catalog['missing'],catalog['reviewed_uses'])==(68,71,259,827)
        for name,value in catalog['checksums'].items():assert pin(ROOT/'docs/literature/omics-2023-2026'/name)==value
        workflow=json.loads(z.read('build/literature-review/signac-runtime/motif-workflow-1/COMPLETED.json'));assert len(workflow['original_statistical_comparisons'])==111 and len(workflow['retained_explicit_row_failure_gates'])==8 and workflow['captured_verifications']==333 and not workflow['upstream_background_selection_performed']
        probe=json.loads(z.read('build/literature-review/signac-runtime/motif-runtime-probe-2/COMPLETED.json'));assert probe['results']['parallel_backend']=='SerialParam' and probe['results']['results']['chromvar']['value']['finite'] and probe['results']['results']['chromvar']['value']['dimensions']==[2,12] and not probe['user_analysis_admitted']
        intake=json.loads(z.read('build/literature-review/signac-runtime/motif-dependency-source-2/COMPLETED.json'));assert len(intake['records'])==4
        for source in intake['records']:
            name='build/literature-review/signac-runtime/motif-dependency-source-2/'+source['package']+'-'+source['version']+'/'+source['package']+'_'+source['version']+'.tar.gz';raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==source['archive']
    parent=ROOT/'docs/literature/signac-link-development';prior=read(parent/'VALIDATION.json');assert len(prior['source_pins'])==200 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'VALIDATION.json')==record['parent_link_validation'] and pin(parent/'controls.zip')==record['parent_link_archive']
    artifact=record['artifact'];assert artifact['bytes']==164699354 and artifact['sha256']=='277ef4bab9d2f9a77316298afba9d0090ff32e8311dc56212b57e9d2fe0fe036' and pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']};api=record['native_API_artifact'];assert pin(ROOT/api['path'])=={k:api[k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_unchanged'] and len(record['platform_validation'])==3 and all(v['tests']==v['passed']==245 and v['skipped']==0 for v in record['platform_validation'])
    assert (record['original_controls'],record['successful_original_controls'],record['retained_original_failures'],record['native_workflow_original_comparisons'],record['native_workflow_captured_verifications'])==(144,111,33,111,333)
    assert (record['rehashed_corruption_rejections'],record['unlisted_nested_file_rejections'],record['missing_row_failure_gates'],record['additional_retained_failure_gates'],record['failed_capture_rejections'])==(36,3,8,3,9)
    assert (record['literal_ENRICH_requests'],record['additional_fresh_platform_runs'],record['checked_original_comparisons'],record['literal_VERIFY_requests'],record['checked_example_captured_verifications'])==(3,2,5,1,16)
    assert record['upstream_reference_probe_only'] and not any(record[k] for k in ['full_Signac_workflow_available','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete','root_dispatch_available','independent_R_KDE_or_RNG_replay','public_upstream_user_workflows_available','exact_historical_chromVAR_executed'])
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text();result=dict(status='passed-independent-current-Signac-motif-development-integrity',archive=record['archive'],archive_members=record['archive_members'],current_source_pins=len(record['source_pins']),native_tests_per_platform=245,platforms=3,original_controls=144,native_workflow_original_comparisons=111,captured_verifications=333,rehashed_corruption_rejections=36,additional_inventory_rejections=3,checked_original_comparisons=5,checked_example_verifications=16,upstream_reference_probe_only=True,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_unchanged=True,prior_link_sources_unchanged=True,literature_pages_withheld=True,pushed=False,whole_goal_complete=False);target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent motif integrity verification')
    main()
