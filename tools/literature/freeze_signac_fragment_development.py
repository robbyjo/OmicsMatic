# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve scoped native fragment evidence without changing the Signac catalog status."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-fragments-development'
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def main():
    assert not TARGET.exists(),'Retain the earlier milestone and choose a new explicit evidence target'
    api=read(BASE/'fragments-build-2/BUILD.json');build=read(BASE/'fragment-workflow-build-3/BUILD.json');native=read(BASE/'fragments-matrix-1/COMPLETED.json');workflow=read(BASE/'fragment-workflow-5/COMPLETED.json');examples=read(BASE/'fragment-examples-1/COMPLETED.json')
    original=read(BASE/'fragments-original-4/COMPLETED.json');binary=read(BASE/'binarize-original-2/COMPLETED.json');fixture=read(ROOT/'src/test/resources/org/jlinalg/sequence/signac-fragments/CAPTURE.json')
    records=[api,build,native,workflow,examples,original,binary,fixture]
    for record in records:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert build['parent_artifact']==api['artifact'] and native['artifact']==api['artifact'] and workflow['artifact']==examples['artifact']==build['artifact']
    assert build['added_classes']==7 and build['unchanged_parent_entries']==19675
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==728 and v['skipped']==0 for v in native['platform_validation'])
    assert (original['count_cases'],original['matrix_cases'],original['nucleosome_cases'],binary['cases_count'])==(63,64,33,36)
    assert len(workflow['original_comparisons'])==32 and workflow['captured_verifications']==96
    assert len(workflow['fresh_additional_platform_runs'])==8 and len(workflow['rehashed_rejections'])==9 and all(v['rejected_platforms']==3 for v in workflow['rehashed_rejections'])
    assert workflow['uninventoried_nested_file_rejections']==3 and len(workflow['failure_gates'])==5 and workflow['input_files_unchanged'] and workflow['owned_sort_scratch_removed']
    assert workflow['native_export_to_exact_package_LSI']==dict(actual_fits=1,captured_verifications=3,components=2)
    assert examples['native_runs']==4 and examples['actual_LSI_fits']==1 and len(examples['runs'])==5 and all(v['actual_run'] and v['captured_verifications']==3 for v in examples['runs'])
    for artifact in [api['artifact'],build['artifact']]:assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(ROOT/api['artifact']['path']) as old,zipfile.ZipFile(ROOT/build['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
        assert len(set(new.namelist())-set(old.namelist()))==7 and all(old.read(n)==new.read(n) for n in old.namelist())
    parent=ROOT/'docs/literature/signac-lsi-development';prior=read(parent/'VALIDATION.json')
    assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'controls.zip')==prior['archive']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged']==prior['canonical_executable_unchanged']
    print('Rechecking unchanged base and private R library contents',flush=True)
    base_expected=read(BASE/'reference-setup-1/BASE-BEFORE.json');base_after=inventory(ROOT/'build/omics-sctransform-r');assert base_expected==base_after and len(base_after)==23902
    with zipfile.ZipFile(parent/'controls.zip') as z:private_expected=json.loads(z.read('runtime-content-inventory.json'))['private_file_pins']
    private_after=inventory(BASE/'reference-setup-1/R-lib');assert private_expected==private_after and len(private_after)==939
    catalog=ROOT/'docs/literature/omics-2023-2026';catalog_pins={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;assert pin(catalog/name)['sha256']==digest;catalog_pins[name]=pin(catalog/name)
    assert len(catalog_pins)==402;summary=read(catalog/'summary.json');counts=summary['implementation_status_counts']
    assert summary['papers']==250 and summary['paper_tool_observations']==2442 and summary['distinct_named_tools']==387
    assert counts['native-subset-validated']==16 and counts['upstream-adapter-validated']==52 and counts['missing']==259
    assert len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    sources=dict(prior['source_pins'])
    for record in records:
        for name,value in record['source_pins'].items():
            if name in sources:assert sources[name]==value
            sources[name]=value
    for name in ['tools/literature/freeze_signac_fragment_development.py','tools/literature/verify_signac_fragment_development.py','tools/literature/prepare_signac_fragment_examples.py','docs/literature/SIGNAC_FRAGMENTS_DEVELOPMENT.md']:sources[name]=pin(ROOT/name)
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/SIGNAC_FRAGMENTS_DEVELOPMENT.md').read_text()
    files={}
    def add(path,name=None):
        name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    retained=[]
    for folder in sorted(BASE.iterdir()):
        if folder.is_dir() and folder.name.startswith(('fragments-','binarize-','fragment-workflow-','fragment-examples-')):
            retained.append(folder.name)
            for path in sorted(folder.rglob('*')):
                if path.is_file() and path.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in path.relative_to(folder).parts:add(path)
    for record in examples['runs']:
        for path in sorted((ROOT/record['analysis_path']).rglob('*')):
            if path.is_file():add(path)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(parent/name)
    files['runtime-content-inventory.json']=(json.dumps(dict(status='content-integrity-not-complete-runtime-admission',base_unchanged=True,base_files=23902,private_unchanged=True,private_files=939,private_file_pins=private_after),indent=2)+'\n').encode()
    files['unchanged-catalog.json']=(json.dumps(dict(checksums=catalog_pins,summary=summary,reviewed_uses=827,new_integrations=68,including_existing=71,missing=259),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

UPSTREAM_KEEP_ALL_CHUNK_BEHAVIOR — Original Signac 1.17.1 FeatureMatrix can retain only first-chunk counts when keep_all_features is true and multiple chunks are used. Native output reproduces it and warns. Review the intended analysis: retain the default false or choose a justified single chunk with process_n at least each source's local feature count. Do not silently repair historical results.

FAIL2 — Invalid input, sort/coordinate/mapping violations, undefined ECDF, corrupt gzip, exceeded bounds and altered captures do not admit successful native results. Retain failed folders and logs, correct the named cause, and rerun to NEW directories. Partial files in failed folders are diagnostic only; existing destinations are refused.

LEGACY QC — Strict mononucleosomal lengths are >147 and <294; nucleosome-free lengths are <147. NaN, NA and Inf are distinct, retained states. Inspect mappings, sampling and zero denominators; do not impute zero or treat an infinite ratio as passing QC. This deprecated legacy Signac metric is not TSS enrichment or the newer ATACqc implementation. Entirely undefined ECDF fails.

PREFIX_ONLY — max_lines is per file, includes records outside the whitelist and does not certify unread tail/compression integrity. Set 0 for full-file scanning where needed. FeatureMatrix counts whole fragment lines, not PCR/read-support weights or TSS endpoints. Raw multi-file counts pool matching barcodes; review sample barcode reuse.

STORED_ZERO_BINARIZED_TO_ONE — Every stored sparse slot becomes1, including explicit zeros, matching original BinarizeCounts. If a positive-count indicator is intended, remove stored zeros explicitly and record that input transformation first.

SKIP3 — Native fragment operations require no R installation. Optional downstream LSI requires the exact pinned R runtime and working native libraries. If missing, install/provide the named permitted runtime and rerun into NEW output, or retain the skip when installation is forbidden/unavailable. Never substitute an unvalidated SVD method or pretend skipped analysis completed.

UTF-8 / ASSEMBLY — Keep the request filename/path ASCII on the tested Windows launchers and put exact Unicode data paths/IDs inside UTF-8 JSON/tables. Regions are one-based closed, fragments are BED zero-based half-open. The required genome_build label records a declaration, not assembly compatibility certification. Review chromosome names and input provenance.

DEVELOPMENT SCOPE — Historical package binaries were not executed. No TSS enrichment, gene activity, motifs/chromVAR, peak links, differential accessibility, full Signac integration or biological QC decision is certified. The catalog remains68 new integrations (71 including existing),259 missing; Signac receives no implementation credit yet. The canonical executable and prior evidence stay unchanged. No release, deployment, push or whole-goal completion occurred.
'''
    TARGET.mkdir();(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');files['WARNINGS.md']=warnings.encode()
    archive=TARGET/'controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    assert archive.stat().st_size<100_000_000
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    result=dict(schema_version=1,status='passed-native-Signac-fragments-and-captured-development-workflows',source_pins=sources,artifact=build['artifact'],native_API_artifact=api['artifact'],parent_LSI_validation=pin(parent/'VALIDATION.json'),parent_LSI_archive=pin(parent/'controls.zip'),archive=pin(archive),archive_members=len(files),archive_file_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},platform_validation=native['platform_validation'],original_count_cases=63,original_feature_matrix_cases=64,original_nucleosome_cases=33,original_sparse_binarize_cases=36,original_rounding_scalars=20100,workflow_original_comparisons=32,workflow_captured_verifications=96,fresh_additional_platform_native_runs=8,rehashed_corruption_rejections=27,uninventoried_nested_file_rejections=3,exact_checked_native_examples=4,exact_checked_VERIFY_requests=1,checked_example_captured_verifications=15,native_export_to_LSI_actual_fits=2,native_export_to_LSI_captured_verifications=6,retained_attempt_directories=retained,base_library_unchanged=True,base_library_files=23902,private_library_unchanged=True,private_library_files=939,catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_executable_unchanged=build['canonical_executable_unchanged'],prior_LSI_sources_unchanged=True,literature_pages_withheld=True,historical_packages_executed=False,complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,warnings_document=pin(TARGET/'WARNINGS.md'),root_dispatch_available=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(TARGET/'VALIDATION.json',result)
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged']
    print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files),source_pins=len(sources))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete native fragment evidence validation')
    main()
