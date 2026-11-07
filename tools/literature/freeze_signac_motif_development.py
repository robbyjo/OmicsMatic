# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native motif enrichment and separate upstream reference probes, without full Signac credit."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-motif-development'

def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}

def main():
    assert not TARGET.exists(),'Preserve earlier evidence and select an explicitly new milestone target'
    api=read(BASE/'motif-build-1/BUILD.json');build=read(BASE/'motif-workflow-build-1/BUILD.json');native=read(BASE/'motif-matrix-1/COMPLETED.json');workflow=read(BASE/'motif-workflow-1/COMPLETED.json');recovery=read(BASE/'motif-recovery-1/COMPLETED.json');examples=read(BASE/'motif-examples-1/COMPLETED.json');original=read(BASE/'motif-original-2/COMPLETED.json');fixture=read(ROOT/'src/test/resources/org/jlinalg/sequence/signac-motifs/CAPTURE.json');setup=read(BASE/'motif-reference-setup-1/COMPLETED.json');probe=read(BASE/'motif-runtime-probe-2/COMPLETED.json');records=[api,build,native,workflow,recovery,examples,original,fixture,setup,probe]
    for record in records:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert native['artifact']==api['artifact'] and workflow['artifact']==recovery['artifact']==examples['artifact']==build['artifact'] and build['parent_artifact']==api['artifact']
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==245 and v['skipped']==0 for v in native['platform_validation'])
    assert (original['controls'],original['successful_controls'],original['retained_source_failures'])==(144,111,33) and original['legacy_access_bridge']
    assert pin(BASE/'motif-original-2/cases.json')==original['cases'] and pin(BASE/'motif-original-2/original.json')==original['original']
    assert len(workflow['original_statistical_comparisons'])==111 and len(workflow['retained_explicit_row_failure_gates'])==8 and workflow['captured_verifications']==333 and workflow['inputs_unchanged'] and not workflow['upstream_background_selection_performed']
    assert len(recovery['rehashed_rejections'])==12 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections']) and recovery['unlisted_nested_file_rejections']==3 and recovery['copied_verifications']==3 and recovery['existing_destination_preserved'] and len(recovery['retained_failure_gates'])==3 and recovery['failed_capture_rejections']==9
    assert examples['literal_ENRICH_requests']==3 and examples['additional_fresh_platform_runs']==2 and examples['original_statistical_comparisons']==5 and examples['captured_verifications']==16 and examples['literal_VERIFY_request_executed'] and examples['inputs_and_requests_unchanged'] and len(examples['runs'])==5
    assert setup['frozen_libraries_unchanged'] and not setup['user_analysis_admitted'] and len(setup['new_private_file_pins'])==1417 and len(setup['download_pins'])==23
    assert probe['status']=='passed-actual-original-motif-scanning-stats-and-chromVAR-runtime-probe' and probe['results']['parallel_backend']=='SerialParam' and not probe['user_analysis_admitted']
    for name in ['region_stats','scan','add_motifs','chromvar']:assert 'error' not in probe['results']['results'][name]['value']
    assert probe['results']['results']['scan']['value']['dimensions']==[80,2] and probe['results']['results']['chromvar']['value']['dimensions']==[2,12] and probe['results']['results']['chromvar']['value']['finite']
    intake=read(BASE/'motif-dependency-source-2/COMPLETED.json');assert len(intake['records'])==4 and all(v['status']=='retained-official-source-not-runtime-admission' for v in intake['records']);assert pin(ROOT/'tools/literature/intake_signac_motif_dependencies.py')==read(BASE/'motif-dependency-source-2/SOURCE-SNAPSHOT.json')
    fixture_path=ROOT/'src/test/resources/org/jlinalg/sequence/signac-motifs/original-controls.zip';assert pin(fixture_path)==fixture['archive']
    for artifact in [api['parent_artifact'],api['artifact'],build['artifact']]:assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
    for previous,current,added in [(api['parent_artifact'],api['artifact'],api['added_classes']+api['added_resources']),(api['artifact'],build['artifact'],build['added_classes'])]:
        with zipfile.ZipFile(ROOT/previous['path']) as old,zipfile.ZipFile(ROOT/current['path']) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and len(set(new.namelist())-set(old.namelist()))==added and all(old.read(n)==new.read(n) for n in old.namelist())
    parent=ROOT/'docs/literature/signac-link-development';prior=read(parent/'VALIDATION.json');assert len(prior['source_pins'])==200 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(parent/'controls.zip')==prior['archive'] and api['parent_artifact']==prior['artifact']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged']==prior['canonical_executable_unchanged']
    print('Rechecking the base, Signac-private and new motif-private runtime contents',flush=True)
    base=inventory(ROOT/'build/omics-sctransform-r');assert base==read(BASE/'reference-setup-1/BASE-BEFORE.json') and len(base)==23902
    with zipfile.ZipFile(parent/'controls.zip') as z:previous_private=json.loads(z.read('runtime-content-inventory.json'))['private_file_pins']
    private=inventory(BASE/'reference-setup-1/R-lib');assert private==previous_private and len(private)==939
    motif_private=inventory(BASE/'motif-reference-setup-1/R-lib');assert motif_private==setup['new_private_file_pins'] and len(motif_private)==1417
    assert inventory(BASE/'motif-reference-setup-1/report/downloads')==setup['download_pins']
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
    profiles=read(ROOT/'src/main/resources/org/jlinalg/omics/signac-motifs/SOURCE-PROFILES.json')
    for profile in profiles['records']:
        for function in profile['functions']:
            name=function['original_source'];value=function['original_source_pin'];assert pin(ROOT/name)==value
            if name in sources:assert sources[name]==value
            sources[name]=value
    for name in ['tools/literature/freeze_signac_motif_development.py','tools/literature/verify_signac_motif_development.py','tools/literature/intake_signac_motif_dependencies.py','docs/literature/SIGNAC_MOTIFS_DEVELOPMENT.md','build/literature-review/signac-runtime/primary-use-audit-3/PRIMARY-USES.json']:sources[name]=pin(ROOT/name)
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/SIGNAC_MOTIFS_DEVELOPMENT.md').read_text();files={}
    def add(path,name=None):
        assert not path.is_symlink();name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    retained=[]
    for folder in sorted(BASE.glob('motif-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for path in sorted(folder.rglob('*')):
                relative=path.relative_to(folder)
                if path.is_file() and path.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in relative.parts and 'R-lib' not in relative.parts and 'downloads' not in relative.parts:add(path)
    assert {'motif-original-1','motif-original-2','motif-runtime-probe-1','motif-runtime-probe-2','motif-dependency-source-1','motif-dependency-source-2','motif-reference-setup-1'}.issubset(retained)
    for record in examples['runs']:
        for path in sorted((ROOT/record['analysis_path']).rglob('*')):
            if path.is_file():add(path)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(parent/name)
    files['runtime-content-inventory.json']=(json.dumps(dict(status='content-integrity-not-complete-portable-runtime-admission',base_unchanged=True,base_files=23902,private_unchanged=True,private_files=939,private_file_pins=private,motif_private_unchanged=True,motif_private_files=1417,motif_private_file_pins=motif_private,download_pins=setup['download_pins'],binary_runtime_archives_included=False),indent=2)+'\n').encode()
    files['unchanged-catalog.json']=(json.dumps(dict(checksums=catalog_pins,summary=summary,reviewed_uses=827,new_integrations=68,including_existing=71,missing=259),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

INPUT_SCOPE — Native ENRICH requires a prepared peaks-by-motifs occurrence/count matrix and explicit query/background rows. It needs Java only. It does not scan FASTA/PFM, compute reference GC, select KDE-matched backgrounds or calculate per-cell chromVAR. Review exact matrix orientation, IDs, database/scan thresholds, assembly and selection provenance. Genome/motif labels are declarations, not certification.

SOURCE_INFERENCE — Original FindMotifs uses the background alone in phyper(observed-1,backgroundCount,backgroundRows-backgroundCount,queryRows,lower.tail=FALSE), not a pooled query-plus-background Fisher population. Zero p can occur when motifs are absent from the supplied background. Review source inference and populations; enrichment is not regulatory causality or per-cell TF activity.

NONFINITE_SOURCE_OUTPUTS — Empty populations, zero denominators and invalid source hypergeometric parameters can yield NaN/Inf. They remain explicit, with warnings, and must not be interpreted as valid discoveries. Counts greater than one are summed as upstream but can make the population invalid. Review affected rows and matrix meaning; no repairs or automatic threshold/selection changes occur.

SOURCE_FAMILY_AND_DUPLICATES — All motifs enter the complete finite correction family before ordering. Source1.6 has no p.adjust column and ignores correction choices. Query/background duplicates remain repeated rows; numeric matrix duplicates sum while pattern duplicates use logical union. Explicit numeric zeros remain stored. Review the requested profile, multiplicities, correction and storage provenance.

FAIL2 — Missing rows, malformed inputs, identity conflicts, exceeded work and failed verification admit no result. Preserve attempts, read captured requests/inputs, diagnostics, status and warnings; correct the named cause and rerun into NEW outputs. Existing destinations are refused. Hashes and native replay are not signatures or authenticity certification.

SKIP3_FOR_UPSTREAM — Unavailable/forbidden scanning or chromVAR dependencies require skipping those dependent analyses. Provide/install exact requirements only where permitted, into new private paths. The current isolated reference setup succeeded without changing frozen libraries. Native explicit enrichment and VERIFY do not require R or install software.

HISTORICAL_SCOPE — Reference controls use unchanged historical functions with an explicitly recorded strict Seurat slot-to-layer access bridge on current dependencies, not historical whole-package binaries. Signac1.16 changed KDE weighting. The successful chromVAR reference probe uses current chromVAR1.34.1, not the paper's1.16.0/1.5.0 runtime. Original source archives are retained for further work. Upstream user workflows and historical reproduction remain incomplete.

RETAINED_ATTEMPTS — Unbridged motif-original1 retains removed-slot API failures; bridged motif-original2 captures111 successes/33 failures. Runtime-probe1 hit a missing-snow child launch; only its identity-verified owned R tree was stopped and checked gone. Serial runtime-probe2 passed scanning, sequence statistics and finite chromVAR reference scores. Source-intake1 HTTP403 failures and corrected-User-Agent intake2 successes are both retained. No failed attempt is silently replaced by success.

DEVELOPMENT_SCOPE — Native ENRICH/VERIFY is a standalone local candidate. Scanner/GC-background/chromVAR/differential-accessibility user workflows and full Signac roles remain pending. The catalog stays68 new integrations (71 including existing),259 missing; Signac receives no full-role credit. Cell Ranger remains excluded. Canonical executable and parent sources are preserved. No root dispatch, release, push, deployment or whole-goal completion occurred. Read [the guide](../SIGNAC_MOTIFS_DEVELOPMENT.md) and [checked examples](../../../examples/signac-motifs/README.md).
'''
    TARGET.mkdir();(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');files['WARNINGS.md']=warnings.encode();archive=TARGET/'controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    assert archive.stat().st_size<100000000
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(files) and all(z.read(n)==v for n,v in files.items())
    result=dict(schema_version=1,status='passed-native-Signac-motif-enrichment-development',source_pins=sources,artifact=build['artifact'],native_API_artifact=api['artifact'],parent_link_validation=pin(parent/'VALIDATION.json'),parent_link_archive=pin(parent/'controls.zip'),archive=pin(archive),archive_members=len(files),archive_file_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},platform_validation=native['platform_validation'],original_controls=144,successful_original_controls=111,retained_original_failures=33,legacy_access_bridge_explicit=True,historical_whole_packages_executed=False,native_workflow_original_comparisons=111,native_workflow_captured_verifications=333,missing_row_failure_gates=8,rehashed_corruption_rejections=36,unlisted_nested_file_rejections=3,copied_baseline_verifications=3,additional_retained_failure_gates=3,failed_capture_rejections=9,literal_ENRICH_requests=3,additional_fresh_platform_runs=2,checked_original_comparisons=5,literal_VERIFY_requests=1,checked_example_captured_verifications=16,isolated_upstream_setup_succeeded=True,original_scanning_sequence_stats_chromVAR_reference_probe_passed=True,upstream_reference_probe_only=True,public_upstream_user_workflows_available=False,exact_historical_chromVAR_executed=False,official_dependency_source_archives=4,retained_attempt_directories=retained,base_library_unchanged=True,base_library_files=23902,private_library_unchanged=True,private_library_files=939,new_motif_private_library_unchanged_after_probes=True,new_motif_private_library_files=1417,catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,canonical_executable_unchanged=build['canonical_executable_unchanged'],prior_link_source_pins=200,prior_link_sources_unchanged=True,literature_pages_withheld=True,independent_R_KDE_or_RNG_replay=False,complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,warnings_document=pin(TARGET/'WARNINGS.md'),root_dispatch_available=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(TARGET/'VALIDATION.json',result);assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];print(json.dumps(dict(status=result['status'],archive=result['archive'],members=len(files),source_pins=len(sources))),flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full native motif evidence checks')
    main()
