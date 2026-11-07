# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native annotation/TSS APIs and workflows without promoting incomplete Signac roles."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-annotation-development'
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def main():
    assert not TARGET.exists(),'Preserve any earlier evidence and choose an explicitly new milestone target'
    api=read(BASE/'annotation-build-3/BUILD.json');build=read(BASE/'annotation-workflow-build-1/BUILD.json');native=read(BASE/'annotation-matrix-4/COMPLETED.json');workflow=read(BASE/'annotation-workflow-2/COMPLETED.json');examples=read(BASE/'annotation-examples-1/COMPLETED.json');original=read(BASE/'annotation-original-2/COMPLETED.json');fixture=read(ROOT/'src/test/resources/org/jlinalg/sequence/signac-annotations/CAPTURE.json');records=[api,build,native,workflow,examples,original,fixture]
    for record in records:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert native['artifact']==api['artifact'] and workflow['artifact']==examples['artifact']==build['artifact'] and build['parent_artifact']==api['artifact']
    assert api['added_classes']==16 and api['unchanged_parent_entries']==19682 and build['added_classes']==7 and build['unchanged_parent_entries']==19698
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==357 and v['skipped']==0 for v in native['platform_validation'])
    assert (original['tss_cases'],original['gene_cases'])==(50,26)
    assert len(workflow['original_comparisons'])==22 and workflow['captured_verifications']==66 and len(workflow['fresh_additional_platform_runs'])==6
    assert len(workflow['rehashed_rejections'])==10 and all(v['rejected_platforms']==3 for v in workflow['rehashed_rejections']) and workflow['uninventoried_nested_file_rejections']==3
    assert len(workflow['unicode_identity_runs'])==2 and all(v['exact_UTF8_ids_preserved'] and v['captured_verifications']==3 for v in workflow['unicode_identity_runs'])
    assert len(workflow['retained_failure_gates'])==7 and workflow['gene_to_LSI_actual_fits']==1 and workflow['gene_to_LSI_verifications']==3 and workflow['annotation_derived_TSS_actual_run'] and workflow['annotation_derived_TSS_verifications']==3
    assert workflow['inputs_preserved'] and workflow['sort_scratch_removed']
    # Prove this gate changed an actual count, not just a Matrix Market shape/header.
    clean=(BASE/'annotation-workflow-2/gene-default-2000-False/matrix.mtx').read_text().splitlines();changed=(BASE/'annotation-workflow-2/tamper-gene-counts/matrix.mtx').read_text().splitlines();assert clean[:2]==changed[:2] and clean[2].split()[:2]==changed[2].split()[:2] and int(changed[2].split()[2])==int(clean[2].split()[2])+1
    assert examples['native_runs']==examples['fresh_original_comparisons']==4 and examples['actual_LSI_fits']==1 and len(examples['runs'])==5 and all(v['actual_run'] and v['captured_verifications']==3 for v in examples['runs'])
    assert len(read(BASE/'annotation-examples-1/fresh-original/original.json')['results'])==4
    for artifact in [api['artifact'],build['artifact']]:assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(ROOT/api['artifact']['path']) as old,zipfile.ZipFile(ROOT/build['artifact']['path']) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and len(set(new.namelist())-set(old.namelist()))==7 and all(old.read(n)==new.read(n) for n in old.namelist())
    parent=ROOT/'docs/literature/signac-fragments-development';prior=read(parent/'VALIDATION.json');assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'controls.zip')==prior['archive']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged']==prior['canonical_executable_unchanged']
    print('Rechecking unchanged R runtime libraries after fresh original calculations and LSI fits',flush=True)
    base=inventory(ROOT/'build/omics-sctransform-r');assert base==read(BASE/'reference-setup-1/BASE-BEFORE.json') and len(base)==23902
    with zipfile.ZipFile(parent/'controls.zip') as z:previous_private=json.loads(z.read('runtime-content-inventory.json'))['private_file_pins']
    private=inventory(BASE/'reference-setup-1/R-lib');assert private==previous_private and len(private)==939
    catalog=ROOT/'docs/literature/omics-2023-2026';catalog_pins={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;assert pin(catalog/name)['sha256']==digest;catalog_pins[name]=pin(catalog/name)
    assert len(catalog_pins)==402;summary=read(catalog/'summary.json');counts=summary['implementation_status_counts'];assert summary['papers']==250 and summary['distinct_named_tools']==387 and summary['paper_tool_observations']==2442 and (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(16,52,259)
    assert len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827 and 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    sources=dict(prior['source_pins'])
    for record in records:
        for name,value in record['source_pins'].items():
            if name in sources:assert sources[name]==value
            sources[name]=value
    for name in ['tools/literature/freeze_signac_annotation_development.py','tools/literature/verify_signac_annotation_development.py','tools/literature/prepare_signac_annotation_examples.py','docs/literature/SIGNAC_ANNOTATION_DEVELOPMENT.md']:sources[name]=pin(ROOT/name)
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/SIGNAC_ANNOTATION_DEVELOPMENT.md').read_text();files={}
    def add(path,name=None):
        name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    retained=[]
    for folder in sorted(BASE.glob('annotation-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for path in sorted(folder.rglob('*')):
                if path.is_file() and path.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in path.relative_to(folder).parts:add(path)
    for record in examples['runs']:
        for path in sorted((ROOT/record['analysis_path']).rglob('*')):
            if path.is_file():add(path)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(parent/name)
    files['runtime-content-inventory.json']=(json.dumps(dict(status='content-integrity-not-complete-portable-runtime-admission',base_unchanged=True,base_files=23902,private_unchanged=True,private_files=939,private_file_pins=private),indent=2)+'\n').encode()
    files['unchanged-catalog.json']=(json.dumps(dict(checksums=catalog_pins,summary=summary,reviewed_uses=827,new_integrations=68,including_existing=71,missing=259),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

LEGACY_TSS_SOURCE_BOUNDARIES — Fast and profile TSS modes can differ in the original package. Preserve endpoint offsets, mode and populations. Do not substitute a generic overlap window, Tn5 shift or ATACqc and label it legacy TSSEnrichment. Inspect score kinds; infinity or source NaN-to-zero normalization is not a passing biological QC decision.

FAIL2 — Original missing-chromosome-only fast chunks, single-row profiles, trimmed profile indices and zero-background profile Matrix-class failures are retained. Invalid formats, inconsistent known/unknown sequence lengths, exceeded capacity and altered captured results also fail. Preserve attempts, correct the named cause and rerun to NEW output. Never silently skip TSSs, change modes or treat partial tables/failed verification as results. Review process_n and choose a justified single chunk where appropriate and feasible.

PROFILE_SOURCE_POPULATION_AND_PRUNING — Profile rows follow file/map order; duplicate cell labels attach the first score, with object percentiles computed after attachment. Background fallback still uses profile rows. The source prunes chromosomes cumulatively across files and combines strand matrices by row position. Review mappings, file order and chromosome inventories; do not pool or restore source-pruned counts silently. The legacy cells argument is ignored and warned; use an explicitly justified object cell universe instead.

ANNOTATION_UNION_AND_FIRST_METADATA — Gene activity unions annotation rows per gene ID, uses first strand/biotype/name and discards original sequence lengths. It is not longest-isoform selection. Coding filtering is after union for activity and before union for annotation-derived TSSs. Review annotation provenance and order. Strict max_width filtering precedes promoter extension.

IDENTICAL_COORDINATES_USE_FIRST_GENE_NAME — Source coordinate aliases can produce duplicate output gene names, all assigned the first name. Retain that warning and review the ambiguity. Downstream normalization/LSI requires unique IDs; an explicit, documented transformation is necessary or that analysis remains skipped/failed. Gene activity is an accessibility-derived fragment proxy, not measured RNA expression.

UTF-8 / COORDINATES — Keep request filenames/paths ASCII on the tested Windows launchers; put exact Unicode data paths and IDs inside UTF-8 JSON/tables. Position/annotation coordinates are one-based closed; fragments are BED zero-based half-open. Unknown sequence lengths preserve source negative offsets, while known linear lengths trim. The genome_build label is a declaration, not reference compatibility certification.

SKIP3 — Native TSS and gene activity need no R installation. Optional LSI requires the exact pinned R runtime and working native dependencies. If missing, provide/install the named runtime only where permitted and rerun to NEW output; otherwise retain the skip and skip dependent analysis. No analysis performs an automatic installation.

DEVELOPMENT SCOPE — Original Signac1.17.1 was executed; historical package binaries were not. Motifs/chromVAR, peak links, differential accessibility and full Signac integration remain incomplete. The catalog remains68 new integrations (71 including existing),259 missing. No full runtime DLL/host closure or biological QC certification is claimed. Prior sources, runtime-library content and canonical executable are preserved. No release, push, deployment or whole-goal completion occurred.
'''
    TARGET.mkdir();(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');files['WARNINGS.md']=warnings.encode();archive=TARGET/'controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    assert archive.stat().st_size<100000000
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    result=dict(schema_version=1,status='passed-native-Signac-TSS-and-gene-activity-development',source_pins=sources,artifact=build['artifact'],native_API_artifact=api['artifact'],parent_fragment_validation=pin(parent/'VALIDATION.json'),parent_fragment_archive=pin(parent/'controls.zip'),archive=pin(archive),archive_members=len(files),archive_file_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},platform_validation=native['platform_validation'],original_TSS_cases=50,original_gene_activity_cases=26,workflow_original_comparisons=22,workflow_captured_verifications=66,fresh_additional_platform_native_runs=6,rehashed_corruption_rejections=30,uninventoried_nested_file_rejections=3,unicode_identity_runs=2,unicode_captured_verifications=6,annotation_derived_TSS_workflow_runs=1,annotation_derived_TSS_workflow_verifications=3,exact_checked_native_examples=4,fresh_checked_full_original_comparisons=4,exact_checked_VERIFY_requests=1,checked_example_captured_verifications=15,gene_matrix_to_LSI_actual_fits=2,gene_matrix_to_LSI_captured_verifications=6,retained_attempt_directories=retained,base_library_unchanged=True,base_library_files=23902,private_library_unchanged=True,private_library_files=939,catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_executable_unchanged=build['canonical_executable_unchanged'],prior_fragment_sources_unchanged=True,literature_pages_withheld=True,historical_packages_executed=False,complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,warnings_document=pin(TARGET/'WARNINGS.md'),root_dispatch_available=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(TARGET/'VALIDATION.json',result);assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files),source_pins=len(sources))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full annotation evidence checks')
    main()
