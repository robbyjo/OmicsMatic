# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native prepared-background chromVAR scoring without claiming upstream sampling/full Signac."""
import hashlib,json,zipfile
from pathlib import Path,PurePosixPath
from signac_scan_evidence import pack,unpack
from validate_mashr_native import pin
from audit_chromvar_shared_build import audit as audit_shared_build
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';TARGET=ROOT/'docs/literature/chromvar-deviations-development';PARENT=ROOT/'docs/literature/signac-scan-development'
def read(p):return json.loads(p.read_bytes())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(p):return {f.relative_to(p).as_posix():pin(f) for f in sorted(p.rglob('*')) if f.is_file()}
def main():
    assert not TARGET.exists(),'Preserve existing frozen evidence'
    names=['chromvar-native-build-2/BUILD.json','chromvar-workflow-build-1/BUILD.json','chromvar-native-matrix-2/COMPLETED.json','chromvar-workflow-matrix-1/COMPLETED.json','chromvar-recovery-1/COMPLETED.json','chromvar-examples-1/COMPLETED.json','chromvar-request-probe-1/COMPLETED.json','chromvar-scan-matrix-probe-1/COMPLETED.json','chromvar-deviations-original-3/COMPLETED.json','chromvar-extended-original-1/COMPLETED.json'];records=[read(BASE/n) for n in names];api,build,native,workflow,recovery,examples,probe,integration,basic,extended=records
    for record in records:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert native['artifact']==api['artifact'] and build['parent_artifact']==api['artifact'] and all(r['artifact']==build['artifact'] for r in [workflow,recovery,examples,probe,integration])
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==320 and v['skipped']==0 for v in native['platform_validation']) and native['original_method_calls']==306 and native['focused_admission_properties']==14
    assert len(workflow['original_statistical_comparisons'])==86 and len(workflow['retained_source_failures'])==16 and workflow['captured_verifications']==258 and workflow['inputs_and_requests_unchanged']
    assert len(recovery['rehashed_rejections'])==18 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections']) and recovery['unlisted_nested_file_rejections']==3 and recovery['copied_verifications']==3 and recovery['existing_output_preserved'] and len(recovery['retained_failure_gates'])==4 and recovery['failed_capture_rejections']==12
    assert (examples['literal_DEVIATIONS_requests'],examples['additional_fresh_platform_runs'],examples['original_statistical_comparisons'],examples['captured_verifications'])==(3,2,5,16) and examples['literal_VERIFY_request_executed'] and examples['inputs_and_requests_unchanged']
    assert probe['original_statistics_match'] and probe['captured_verifications']==3 and integration['actual_original_public_computeDeviations_executed'] and integration['original_statistics_match'] and integration['Unicode_cell_IDs'] and integration['captured_verifications']==3 and (integration['peaks'],integration['cells'],integration['motifs'])==(80,12,3)
    assert (basic['profile_case_protocols'],extended['profile_case_protocols'])==(52,50) and not basic['algorithm_reexecuted']
    for record,folder in [(basic,'chromvar-deviations-original-3'),(extended,'chromvar-extended-original-1')]:assert pin(BASE/folder/'cases.json')==record['cases'] and pin(BASE/folder/'original.json')==record['original']
    fixture_path=ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-deviations';fixture=read(fixture_path/'CAPTURE.json');assert pin(fixture_path/'original-controls.zip')==fixture['archive'] and all(pin(ROOT/n)==v for n,v in fixture['source_pins'].items())
    prior=read(PARENT/'VALIDATION.json');assert len(prior['source_pins'])==385 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(PARENT/'controls.zip')==prior['archive'] and api['parent_artifact']==prior['artifact']
    for previous,current,added in [(api['parent_artifact'],api['artifact'],api['added_classes']+api['added_resources']),(api['artifact'],build['artifact'],build['added_classes'])]:
        assert all(pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']} for a in [previous,current])
        with zipfile.ZipFile(ROOT/previous['path']) as a,zipfile.ZipFile(ROOT/current['path']) as b:assert b.testzip() is None and len(b.namelist())==len(set(b.namelist())) and len(set(b.namelist())-set(a.namelist()))==added and all(a.read(n)==b.read(n) for n in a.namelist())
    canonical_observation=read(BASE/'chromvar-freeze-attempt-1/canonical-observation.json');assert canonical_observation==audit_shared_build() and canonical_observation['canonical_executable_at_validation']==prior['canonical_executable_unchanged']==build['canonical_executable_unchanged']
    print('Checking unchanged base, Signac-private and motif-private runtime contents',flush=True)
    before=read(BASE/'reference-setup-1/BASE-BEFORE.json');base=inventory(ROOT/'build/omics-sctransform-r');assert base==before and len(base)==23902
    parent_files=unpack(PARENT/'controls.zip',prior);previous_runtime=json.loads(parent_files['runtime-content-inventory.json']);private=inventory(BASE/'reference-setup-1/R-lib');motif=inventory(BASE/'motif-reference-setup-1/R-lib');assert private==previous_runtime['private_file_pins'] and len(private)==939 and motif==previous_runtime['motif_private_file_pins'] and len(motif)==1417
    catalog=ROOT/'docs/literature/omics-2023-2026';catalog_pins={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts;assert pin(catalog/name)['sha256']==digest;catalog_pins[name]=pin(catalog/name)
    summary=read(catalog/'summary.json');counts=summary['implementation_status_counts'];assert len(catalog_pins)==402 and summary['papers']==250 and summary['distinct_named_tools']==387 and summary['paper_tool_observations']==2442 and (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(16,52,259) and len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text();versions=read(BASE/'chromvar-runtime-versions-2/COMPLETED.json');assert len(versions['runs'])==3 and all(v['exit_code']==0 for v in versions['runs'])
    sources=dict(prior['source_pins'])
    for record in records+[fixture]:
        for name,value in record['source_pins'].items():
            if name in sources:assert sources[name]==value
            sources[name]=value
    for name in ['tools/literature/freeze_chromvar_development.py','tools/literature/verify_chromvar_development.py','tools/literature/audit_chromvar_shared_build.py']:sources[name]=pin(ROOT/name)
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/CHROMVAR_DEVIATIONS_DEVELOPMENT.md').read_text();files={}
    def add(path,name=None):
        assert not path.is_symlink();name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    retained=[]
    for folder in sorted(BASE.glob('chromvar-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for path in sorted(folder.rglob('*')):
                if path.is_file() and path.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in path.relative_to(folder).parts:add(path)
    assert {'chromvar-deviations-original-1','chromvar-deviations-original-2','chromvar-deviations-original-3','chromvar-native-build-1','chromvar-native-build-2','chromvar-native-matrix-1','chromvar-native-matrix-2','chromvar-arithmetic-probe-1','chromvar-example-preparation-1','chromvar-runtime-versions-1','chromvar-runtime-versions-2'}.issubset(retained)
    for run in examples['runs']:
        for path in (ROOT/run['analysis_path']).rglob('*'):
            if path.is_file():add(path)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(PARENT/name)
    files['unchanged-catalog.json']=(json.dumps(dict(checksums=catalog_pins,papers=250,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827),indent=2)+'\n').encode()
    files['runtime-content-inventory.json']=(json.dumps(dict(base_unchanged=True,base_files=23902,private_unchanged=True,private_files=939,private_file_pins=private,motif_private_unchanged=True,motif_private_files=1417,motif_private_file_pins=motif,complete_host_DLL_closure=False),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS — NATIVE CHROMVAR

PREPARED_BACKGROUND_ONLY — DEVIATIONS and VERIFY require Java only. The supplied peaks-by-iterations index matrix must match the exact count peak population. No GC/intensity sampling, motif scanning or analysis-time installation occurs. Synthetic example backgrounds are not matched biological backgrounds. Record and review actual upstream editions, algorithms, settings, seed, reference and motif provenance.

SKIP3_FOR_UPSTREAM — If separately required upstream preparation software is unavailable/forbidden, skip that dependent analysis. Provide/install exact requirements only where permitted into separate private paths; otherwise preserve the skip. Native prepared scoring itself does not need R.

FAIL2 — Invalid input, source errors, exceeded capacities and failed verification admit no result. Preserve captures and partials, inspect WARNINGS.md, diagnostics, status and requests/inputs, correct the named cause and retry into NEW paths. Existing outputs are refused. Failed captures cannot be promoted by VERIFY.

SOURCE_NORM_AND_GROUPS — norm=TRUE sums per-cell fractions, not their mean. Declared factor levels, unused levels and missing assignments remain explicit; groups do not silently become a new population. Original singleton-axis grouped norm=FALSE failures are retained. Explicit expectations are not normalized, imputed or repaired.

SOURCE_VALUE_KINDS — NA, NaN, Inf and -Inf are distinct and retained. Expected counts below one become source NA in the full core. Empty annotations have NA scores/overlap. Zero background variance and original single-peak sequential double means can produce Inf/-Inf from rounding residuals. These are not validated discoveries; review every affected row and the supplied populations.

SOURCE_MEMBERSHIP_AND_SHAPES — Repeated annotation members retain multiplicity; fraction metadata can exceed one. Pattern matrix duplicates use logical union. Every peak needs positive pooled fragments for the full core; its one-cell/multiple-annotation output-dimension failure is retained. No automatic filtering or threshold relaxation occurs.

SCOPE — Native formulas were compared to unchanged 1.16.0/1.34.1 source functions on recorded current R/Matrix dependencies. Historical whole packages, chromVAR1.5.0, upstream sampling and complete Signac are not certified. Native current/legacy/grouped examples and fresh Java25/Linux analyses are tested. Scores are not regulatory causality or biological validation.

EVIDENCE_AND_PUBLICATION — controls.zip deduplicates SHA-256 contents and maps every logical path in logical-files.json, preserving failed attempts. Hashes prove integrity, not authenticity or complete host/DLL closure. Prior source pins, three R libraries, catalog and withheld literature Pages remain unchanged. The shared canonical JAR changed after validation: twelve study classes changed, four study-cleanup classes and directory entries were added, and all other file entries, including chromVAR, are byte-identical to the tested development JAR. canonical-observation.json preserves this exact audit and the failed freeze assertion. No complete test claim is made for that shared rebuild; all scientific tests remain bound to the pinned development artifact. This is local development, with no root route, catalog credit, release, push or deployment.
'''
    TARGET.mkdir();(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');archive=TARGET/'controls.zip';print('Freezing native controls, user captures and all retained attempts',flush=True);layout=pack(archive,files);assert archive.stat().st_size<100_000_000
    result=dict(schema_version=1,status='passed-native-chromVAR-prepared-background-development',source_pins=sources,artifact=build['artifact'],native_API_artifact=api['artifact'],parent_scan_validation=pin(PARENT/'VALIDATION.json'),parent_scan_archive=pin(PARENT/'controls.zip'),archive=pin(archive),**layout,platform_validation=native['platform_validation'],original_method_calls=306,focused_admission_properties=14,source_profile_case_protocols=102,case_populations=51,native_user_request_comparisons=86,retained_source_failure_gates=16,native_user_request_captured_verifications=258,rehashed_corruption_rejections=54,unlisted_nested_file_rejections=3,copied_baseline_verifications=3,additional_failure_gates=4,failed_capture_rejections=12,literal_DEVIATIONS_requests=3,additional_fresh_platform_runs=2,checked_original_comparisons=5,checked_example_verifications=16,literal_VERIFY_request_executed=True,scanned_matrix_original_public_interface_comparison=True,scanned_matrix_peaks=80,scanned_matrix_cells=12,scanned_matrix_motifs=3,scanned_matrix_verifications=3,Unicode_cell_IDs_verified=True,native_analysis_requires_no_R=True,retained_attempt_directories=retained,all_three_R_libraries_unchanged=True,base_library_files=23902,private_library_files=939,motif_private_library_files=1417,prior_scan_source_pins=385,prior_scan_sources_unchanged=True,canonical_executable_at_validation=build['canonical_executable_unchanged'],canonical_executable_at_freeze=canonical_observation['canonical_executable_at_freeze'],canonical_executable_changed_since_validation=True,canonical_shared_build_audited=True,canonical_complete_test_claim=False,canonical_observation=pin(BASE/'chromvar-freeze-attempt-1/canonical-observation.json'),catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,literature_pages_withheld=True,background_sampling_performed=False,motif_scanning_performed=False,historical_whole_packages_executed=False,chromVAR_1_5_0_executed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,root_dispatch_available=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,warnings_document=pin(TARGET/'WARNINGS.md'))
    save(TARGET/'VALIDATION.json',result);assert all(pin(ROOT/n)==v for n,v in sources.items());print(json.dumps(dict(status=result['status'],archive=result['archive'],physical_members=result['archive_members'],logical_files=result['logical_files'],source_pins=len(sources))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full native milestone checks')
    main()
