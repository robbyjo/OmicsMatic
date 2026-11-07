# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently check the frozen annotation milestone, artifacts, source snapshots and current catalog."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
EVIDENCE=ROOT/'docs/literature/signac-annotation-development'
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def main():
    target=BASE/'annotation-final-integrity-1.json';assert not target.exists();record=read(EVIDENCE/'VALIDATION.json');assert record['status']=='passed-native-Signac-TSS-and-gene-activity-development'
    assert pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    with zipfile.ZipFile(EVIDENCE/'controls.zip') as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==record['archive_members'] and set(z.namelist())==set(record['archive_file_pins'])
        for name,want in record['archive_file_pins'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==want
        assert all(z.read('current-source/'+name)==(ROOT/name).read_bytes() for name in record['source_pins'])
        runtime=json.loads(z.read('runtime-content-inventory.json'));assert runtime['base_unchanged'] and runtime['private_unchanged'] and (runtime['base_files'],runtime['private_files'])==(23902,939)
        catalog=json.loads(z.read('unchanged-catalog.json'));assert len(catalog['checksums'])==402 and (catalog['new_integrations'],catalog['including_existing'],catalog['missing'],catalog['reviewed_uses'])==(68,71,259,827)
        for name,value in catalog['checksums'].items():assert pin(ROOT/'docs/literature/omics-2023-2026'/name)==value
    parent=ROOT/'docs/literature/signac-fragments-development';prior=read(parent/'VALIDATION.json');assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'VALIDATION.json')==record['parent_fragment_validation'] and pin(parent/'controls.zip')==record['parent_fragment_archive']
    artifact=record['artifact'];assert artifact['bytes']==164547699 and artifact['sha256']=='e3586d1e30a82f7168225fbed54ccea9daae62520f9f6e773d3a8c7258f8de64' and pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_unchanged'] and len(record['platform_validation'])==3 and all(v['tests']==v['passed']==357 and v['skipped']==0 for v in record['platform_validation'])
    assert (record['original_TSS_cases'],record['original_gene_activity_cases'],record['workflow_original_comparisons'],record['workflow_captured_verifications'],record['fresh_additional_platform_native_runs'])==(50,26,22,66,6)
    assert (record['rehashed_corruption_rejections'],record['uninventoried_nested_file_rejections'],record['unicode_identity_runs'],record['unicode_captured_verifications'])==(30,3,2,6)
    assert (record['exact_checked_native_examples'],record['fresh_checked_full_original_comparisons'],record['checked_example_captured_verifications'],record['gene_matrix_to_LSI_actual_fits'],record['gene_matrix_to_LSI_captured_verifications'])==(4,4,15,2,6)
    assert not any(record[k] for k in ['full_Signac_workflow_available','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete','root_dispatch_available'])
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    result=dict(status='passed-independent-current-Signac-annotation-development-integrity',archive=record['archive'],archive_members=record['archive_members'],current_source_pins=len(record['source_pins']),native_tests_per_platform=357,platforms=3,original_TSS_cases=50,original_gene_activity_cases=26,workflow_original_comparisons=22,captured_verifications=66,rehashed_corruption_rejections=30,additional_inventory_rejections=3,unicode_identity_runs=2,fresh_checked_original_comparisons=4,gene_to_LSI_actual_fits=2,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_unchanged=True,prior_fragment_sources_unchanged=True,literature_pages_withheld=True,pushed=False,whole_goal_complete=False)
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent annotation integrity verification')
    main()
