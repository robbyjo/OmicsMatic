# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently verify the frozen LinkPeaks evidence, source snapshots, artifacts and unchanged catalog."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
EVIDENCE=ROOT/'docs/literature/signac-link-development'

def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def main():
    target=BASE/'links-final-integrity-1.json';assert not target.exists();record=read(EVIDENCE/'VALIDATION.json');assert record['status']=='passed-original-Signac-LinkPeaks-and-native-auditor-development'
    assert pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    with zipfile.ZipFile(EVIDENCE/'controls.zip') as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==record['archive_members'] and set(z.namelist())==set(record['archive_file_pins'])
        for name,want in record['archive_file_pins'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==want
        assert all(z.read('current-source/'+name)==(ROOT/name).read_bytes() for name in record['source_pins'])
        runtime=json.loads(z.read('runtime-content-inventory.json'));assert runtime['base_unchanged'] and runtime['private_unchanged'] and (runtime['base_files'],runtime['private_files'])==(23902,939)
        catalog=json.loads(z.read('unchanged-catalog.json'));assert len(catalog['checksums'])==402 and (catalog['new_integrations'],catalog['including_existing'],catalog['missing'],catalog['reviewed_uses'])==(68,71,259,827)
        for name,value in catalog['checksums'].items():assert pin(ROOT/'docs/literature/omics-2023-2026'/name)==value
        workflow=json.loads(z.read('build/literature-review/signac-runtime/links-workflow-2/COMPLETED.json'));assert len(workflow['actual_original_fits'])==44 and len(workflow['expected_source_failure_gates'])==16 and workflow['captured_verifications']==132
        recovery=json.loads(z.read('build/literature-review/signac-runtime/links-recovery-2/COMPLETED.json'));assert len(recovery['rehashed_rejections'])==18 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections']) and len(recovery['dependency_skips'])==len(recovery['owned_process_checks'])==2
        for name in ['timeout','interrupt']:
            check=json.loads(z.read('build/literature-review/signac-runtime/links-recovery-2/'+name+'-direct-PID-recheck.json'));assert check['observed'] and not check['surviving_same_processes'] and all('CreationDate' in v for v in check['observed'])
        profiles=json.loads(z.read('current-source/src/main/resources/org/jlinalg/omics/signac-links/SOURCE-PROFILES.json'))
        for profile in profiles['records']:
            for function in profile['functions']:
                raw=z.read('current-source/'+function['original_source']);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==function['original_source_pin']
    parent=ROOT/'docs/literature/signac-annotation-development';prior=read(parent/'VALIDATION.json');assert len(prior['source_pins'])==127 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'VALIDATION.json')==record['parent_annotation_validation'] and pin(parent/'controls.zip')==record['parent_annotation_archive']
    artifact=record['artifact'];assert artifact['bytes']==164636719 and artifact['sha256']=='1679ec6d06a297d657ff06b3ddb1789fc4eee96164b0a5ec5d8c38d308f53a47' and pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    api=record['native_API_artifact'];assert pin(ROOT/api['path'])=={k:api[k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_unchanged'] and len(record['platform_validation'])==3 and all(v['tests']==v['passed']==109 and v['skipped']==0 for v in record['platform_validation'])
    assert (record['original_controls'],record['successful_original_controls'],record['expected_source_failures'],record['workflow_actual_original_fits'],record['workflow_failure_gates'],record['workflow_captured_verifications'])==(60,44,16,44,16,132)
    assert (record['rehashed_corruption_rejections'],record['unlisted_nested_file_rejections'],record['unicode_actual_R_fits'],record['unicode_captured_verifications'],record['actual_dependency_skips'],record['actual_owned_process_cleanup_checks'])==(54,3,1,3,2,2)
    assert (record['exact_checked_FIT_requests'],record['checked_original_comparisons'],record['exact_checked_VERIFY_requests'],record['checked_example_captured_verifications'])==(3,3,1,10)
    assert not any(record[k] for k in ['full_Signac_workflow_available','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete','root_dispatch_available','independent_R_KDE_or_RNG_replay'])
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    result=dict(status='passed-independent-current-Signac-LinkPeaks-development-integrity',archive=record['archive'],archive_members=record['archive_members'],current_source_pins=len(record['source_pins']),native_tests_per_platform=109,platforms=3,original_controls=60,workflow_actual_original_fits=44,workflow_failure_gates=16,captured_verifications=132,rehashed_corruption_rejections=54,additional_inventory_rejections=3,unicode_actual_R_fits=1,checked_original_comparisons=3,checked_example_verifications=10,actual_dependency_skips=2,actual_owned_process_cleanup_checks=2,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_unchanged=True,prior_annotation_sources_unchanged=True,literature_pages_withheld=True,pushed=False,whole_goal_complete=False)
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent LinkPeaks integrity verification')
    main()
