# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original LinkPeaks and native audit evidence without crediting incomplete full Signac roles."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-link-development'

def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}

def main():
    assert not TARGET.exists(),'Preserve any earlier freeze; choose an explicitly new milestone target'
    api=read(BASE/'links-build-3/BUILD.json');build=read(BASE/'links-workflow-build-3/BUILD.json');native=read(BASE/'links-matrix-1/COMPLETED.json');workflow=read(BASE/'links-workflow-2/COMPLETED.json');recovery=read(BASE/'links-recovery-2/COMPLETED.json');unicode=read(BASE/'links-unicode-2/COMPLETED.json');examples=read(BASE/'links-examples-1/COMPLETED.json');original=read(BASE/'links-original-4/COMPLETED.json');fixture=read(ROOT/'src/test/resources/org/jlinalg/sequence/signac-links/CAPTURE.json');records=[api,build,native,workflow,recovery,unicode,examples,original,fixture]
    for record in records:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert native['artifact']==api['artifact'] and workflow['artifact']==recovery['artifact']==unicode['artifact']==examples['artifact']==build['artifact'] and build['parent_artifact']==api['artifact']
    assert (api['added_classes'],api['added_resources'],api['unchanged_parent_entries'])==(17,27,19705) and (build['added_classes'],build['added_resources'],build['unchanged_parent_entries'])==(3,2,19749)
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==109 and v['skipped']==0 for v in native['platform_validation'])
    assert (original['controls'],original['successful_controls'],original['expected_source_failures'])==(60,44,16) and native['original_successful_cases']==44 and native['original_source_failure_cases_retained']==16
    assert pin(BASE/'links-original-4/cases.json')==original['cases'] and pin(BASE/'links-original-4/original.json')==original['original']
    assert len(workflow['actual_original_fits'])==44 and len(workflow['expected_source_failure_gates'])==16 and workflow['captured_verifications']==132 and workflow['inputs_unchanged']
    assert len(recovery['rehashed_rejections'])==18 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections']) and recovery['unlisted_nested_file_rejections']==3 and recovery['copied_verifications']==3 and recovery['existing_destination_preserved']
    assert len(recovery['dependency_skips'])==2 and all(v['exit_code']==3 and v['no_result_admitted'] for v in recovery['dependency_skips'])
    assert len(recovery['owned_process_checks'])==2 and all(v['actual_live_R_process_observed'] and v['direct_PID_and_creation_time_recheck'] and v['owned_processes_stopped'] and v['no_result_admitted'] for v in recovery['owned_process_checks'])
    for name in ['timeout','interrupt']:
        check=read(BASE/'links-recovery-2'/(name+'-direct-PID-recheck.json'));assert check['observed'] and not check['surviving_same_processes']
    assert unicode['actual_R_fit'] and unicode['exact_UTF8_cell_and_gene_IDs_preserved'] and unicode['original_link_numbers_and_RNG_states_match'] and unicode['captured_verifications']==3 and unicode['fresh_java_platform']=='windows-java25'
    assert examples['actual_original_fits']==examples['fresh_original_comparisons']==3 and examples['captured_verifications']==10 and examples['literal_VERIFY_request_executed'] and examples['inputs_and_requests_unchanged'] and len(examples['runs'])==3
    assert all(v['actual_original_fit'] and v['original_link_numbers_and_RNG_states_match'] and v['captured_verifications']==3 for v in examples['runs'])
    fixture_path=ROOT/'src/test/resources/org/jlinalg/sequence/signac-links/original-controls.zip';assert pin(fixture_path)==fixture['archive']
    for artifact in [api['parent_artifact'],api['artifact'],build['artifact']]:assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    for previous,current,added in [(api['parent_artifact'],api['artifact'],44),(api['artifact'],build['artifact'],5)]:
        with zipfile.ZipFile(ROOT/previous['path']) as old,zipfile.ZipFile(ROOT/current['path']) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and len(set(new.namelist())-set(old.namelist()))==added and all(old.read(n)==new.read(n) for n in old.namelist())
    parent=ROOT/'docs/literature/signac-annotation-development';prior=read(parent/'VALIDATION.json');assert len(prior['source_pins'])==127 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'controls.zip')==prior['archive'] and api['parent_artifact']==prior['artifact']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged']==prior['canonical_executable_unchanged']
    print('Rechecking the unchanged base and private R libraries',flush=True)
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
    profiles=read(ROOT/'src/main/resources/org/jlinalg/omics/signac-links/SOURCE-PROFILES.json')
    for profile in profiles['records']:
        for function in profile['functions']:
            name=function['original_source'];value=function['original_source_pin'];assert pin(ROOT/name)==value
            if name in sources:assert sources[name]==value
            sources[name]=value
    for name in ['tools/literature/freeze_signac_link_development.py','tools/literature/verify_signac_link_development.py','docs/literature/SIGNAC_LINKS_DEVELOPMENT.md']:sources[name]=pin(ROOT/name)
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/SIGNAC_LINKS_DEVELOPMENT.md').read_text();files={}
    def add(path,name=None):
        assert not path.is_symlink();name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    retained=[]
    for folder in sorted(BASE.glob('links-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for path in sorted(folder.rglob('*')):
                if path.is_file() and path.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in path.relative_to(folder).parts:add(path)
    assert {'links-original-1','links-original-2','links-original-3','links-original-4','links-unicode-1','links-unicode-2','links-recovery-1','links-recovery-2'}.issubset(retained)
    for record in examples['runs']:
        for path in sorted((ROOT/record['analysis_path']).rglob('*')):
            if path.is_file():add(path)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(parent/name)
    files['runtime-content-inventory.json']=(json.dumps(dict(status='content-integrity-not-complete-portable-runtime-admission',base_unchanged=True,base_files=23902,private_unchanged=True,private_files=939,private_file_pins=private),indent=2)+'\n').encode()
    files['unchanged-catalog.json']=(json.dumps(dict(checksums=catalog_pins,summary=summary,reviewed_uses=827,new_integrations=68,including_existing=71,missing=259),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

SKIP3 — A fresh LinkPeaks fit requires exact R4.6.1, Signac1.17.1, Matrix1.7.5, SeuratObject5.4.0, GenomicRanges1.64.0, GenomeInfoDb1.48.0 and jsonlite2.0.0, with working native dependencies and optional wrswoR absent for this base-sample.int profile. Provide/install a suitable separate private runtime only where permitted; otherwise retain the skip and skip dependent analyses. No analysis performs installation. Java-only captured verification remains available.

FAIL2 — Invalid inputs/identity changes, inadequate backgrounds, undefined numerical results, exceeded capacity, upstream errors, timeouts and failed verification admit no result. Read the request, WARNINGS.md, manifest, worker logs and effective settings. Preserve attempts, correct the named cause and rerun into NEW output. Do not silently align cells, change sparse storage, lower background sample sizes, switch profiles or relax thresholds. An empty successful table can be valid.

NOMINAL_SOURCE_P — p=pnorm(-abs(z)) has no factor of two or multiplicity adjustment. It is not an FDR value or proof of regulatory causality. Review study-specific inference. Eligibility, score and p cutoffs are strict.

HISTORICAL_SOURCE_ONLY — 1.14.0-source executes unchanged original functions using current pinned dependencies, not historical package binaries. Its query-only KDE weights and chromosome-prefix exclusion differ from 1.17.1-package. Keep the paper's source profile explicit. Native Spearman preserves stored-entry ranks, including signed values/explicit zeros; do not substitute dense Spearman.

SOURCE_DISTANCE_MATRIX_DIFFERENCE — min_distance larger than distance yields the source's symmetric outer difference because negative subtraction entries coerce to true. Negative minimum distance is ignored and warned. Review both requested thresholds rather than interpreting them as ordinary interval subtraction.

DECLARED_METADATA — GC, sequence length, optional count, genome build and annotation provenance are investigator declarations. The workflow does not inspect a reference genome. Review warnings about declared lengths/counts differing from widths/totals, exact paired cell order and canonical peak IDs. Gene activity is not measured RNA expression.

CAPTURED_VERIFICATION_LIMIT — Java independently checks allowed background pools/draw identities, coefficients, sample-SD z scores, nominal tails, ranges, decisions, inputs/exchange/settings/source bindings and saved RNG replay equality. It does not independently replay R KDE weights or RNG. Hashes are not signatures or authenticity certification. Complete runtime DLL/host closure and biological/regulatory interpretation are not certified.

RETAINED_ATTEMPTS — Original1 lacks the later audit fields; original2 has an invalid sparse-transpose audit scaffold, not new original-source failures; original3 failed a Python expected-count assertion despite valid calculations. Unicode1 failed safely under the old C-locale importer; Unicode2 passed the corrected UTF-8 importer. Recovery1 failed a Python encoding-name check after a timeout; recovery2 fixes it and directly rechecks observed PID/creation times. All attempts remain retained.

DEVELOPMENT_SCOPE — This standalone guarded LinkPeaks CLI and native auditor are validated local candidates. Motifs/chromVAR, differential accessibility and full Signac paper workflows remain incomplete. Catalog counts remain 68 new integrations (71 including existing),259 missing; Signac receives no full-role credit. Cell Ranger remains excluded. No canonical executable replacement, root dispatch, release, push, deployment or whole-goal completion occurred. Read [the guide](../SIGNAC_LINKS_DEVELOPMENT.md) and [the examples](../../../examples/signac-links/README.md) for commands and input/action details.
'''
    TARGET.mkdir();(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');files['WARNINGS.md']=warnings.encode();archive=TARGET/'controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    assert archive.stat().st_size<100000000
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    result=dict(schema_version=1,status='passed-original-Signac-LinkPeaks-and-native-auditor-development',source_pins=sources,artifact=build['artifact'],native_API_artifact=api['artifact'],parent_annotation_validation=pin(parent/'VALIDATION.json'),parent_annotation_archive=pin(parent/'controls.zip'),archive=pin(archive),archive_members=len(files),archive_file_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},platform_validation=native['platform_validation'],original_controls=60,successful_original_controls=44,expected_source_failures=16,workflow_actual_original_fits=44,workflow_failure_gates=16,workflow_captured_verifications=132,unicode_actual_R_fits=1,unicode_captured_verifications=3,rehashed_corruption_rejections=54,unlisted_nested_file_rejections=3,copied_baseline_verifications=3,actual_dependency_skips=2,actual_owned_process_cleanup_checks=2,direct_PID_and_creation_time_rechecks=True,exact_checked_FIT_requests=3,checked_original_comparisons=3,exact_checked_VERIFY_requests=1,checked_example_captured_verifications=10,retained_attempt_directories=retained,base_library_unchanged=True,base_library_files=23902,private_library_unchanged=True,private_library_files=939,catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_executable_unchanged=build['canonical_executable_unchanged'],prior_annotation_source_pins=127,prior_annotation_sources_unchanged=True,literature_pages_withheld=True,historical_packages_executed=False,independent_R_KDE_or_RNG_replay=False,complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,warnings_document=pin(TARGET/'WARNINGS.md'),root_dispatch_available=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(TARGET/'VALIDATION.json',result);assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files),source_pins=len(sources))),flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full LinkPeaks evidence checks')
    main()
