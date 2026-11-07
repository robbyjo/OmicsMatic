# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze guarded original FASTA/PFM scanning plus native audits, without full Signac credit."""
import hashlib,json,zipfile
from signac_scan_evidence import pack
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'docs/literature/signac-scan-development'
PARENT=ROOT/'docs/literature/signac-motif-development'
def read(p):return json.loads(p.read_bytes())
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(p):return {f.relative_to(p).as_posix():pin(f) for f in sorted(p.rglob('*')) if f.is_file()}

def main():
    assert not TARGET.exists(),'Preserve existing frozen evidence'
    names=['scan-input-build-2/BUILD.json','scan-audit-build-1/BUILD.json','scan-workflow-build-4/BUILD.json','scan-pfm-boundary-audit-after-1/COMPLETED.json','scan-original-7/COMPLETED.json','scan-worker-probe-1/COMPLETED.json','scan-pfm-boundary-after-1/COMPLETED.json','scan-workflow-boundary-verification-1/COMPLETED.json','scan-pfm-recovery-2/COMPLETED.json','scan-pfm-examples-boundary-1/COMPLETED.json']
    records=[read(BASE/n) for n in names];inputs,audit,build,native,original,worker,probe,workflow,recovery,examples=records
    for record in records:
        if record is worker:
            for n,v in record['source_pins'].items():assert pin(ROOT/n)==v or pin(BASE/'scan-worker-probe-1/source-snapshot'/n)==v
        else:assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    assert native['artifact']==build['artifact'] and workflow['artifact']==recovery['artifact']==examples['artifact']==probe['artifact']==build['artifact']
    assert audit['parent_artifact']==inputs['artifact'] and build['parent_artifact']==audit['artifact']
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==211 and v['skipped']==0 for v in native['platform_validation'])
    assert (original['controls'],original['successful_controls'],original['retained_source_failures'])==(96,75,21)
    assert pin(BASE/'scan-original-7/cases.json')==original['cases'] and pin(BASE/'scan-original-7/original.json')==original['original']
    assert len(worker['runs'])==4 and all(v['original_enrichment_statistics_and_RNG_match'] for v in worker['runs'])
    assert probe['actual_R_fit_completed'] and probe['compared_identical_upstream_results_with_before_capture'] and probe['captured_verifications']==3
    # Exact workflow field assertions live in the independent checker too.
    assert workflow['status']=='passed-full-original-scan-enrichment-user-workflow-matrix'
    assert len(workflow['actual_original_fits'])==75 and len(workflow['retained_original_failure_gates'])==21 and workflow['captured_verifications']==225 and workflow['source_inputs_unchanged']
    assert len(recovery['rehashed_rejections'])==34 and all(v['rejected_platforms']==3 for v in recovery['rehashed_rejections'])
    assert recovery['unlisted_nested_file_rejections']==3 and recovery['copied_verifications']==3 and recovery['existing_output_preserved']
    assert len(recovery['actual_dependency_skips'])==2 and all(v['exit_code']==3 and v['no_result_admitted'] for v in recovery['actual_dependency_skips'])
    assert len(recovery['actual_owned_process_checks'])==2 and all(v['actual_live_R_observed'] and v['direct_PID_and_creation_time_recheck'] and v['owned_processes_stopped'] and v['no_result_admitted'] for v in recovery['actual_owned_process_checks'])
    assert (examples['literal_FIT_requests'],examples['original_enrichment_and_RNG_comparisons'],examples['generated_native_enrichment_comparisons'],examples['captured_verifications'])==(3,4,3,22)
    assert examples['actual_PFM_export_and_Java_import'] and examples['fresh_Java25_Unicode_fit'] and examples['retained_source_Unicode_motif_ID_failure'] and examples['inputs_and_requests_unchanged']
    prior=read(PARENT/'VALIDATION.json');assert len(prior['source_pins'])==277 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(PARENT/'controls.zip')==prior['archive'] and inputs['parent_artifact']==prior['artifact']
    fixture=read(ROOT/'src/test/resources/org/jlinalg/sequence/signac-scan/CAPTURE.json');assert all(pin(ROOT/n)==v for n,v in fixture['source_pins'].items())
    assert pin(ROOT/'src/test/resources/org/jlinalg/sequence/signac-scan/original-controls.zip')==fixture['archive']
    for old,new in [(inputs['parent_artifact'],inputs),(inputs['artifact'],audit),(audit['artifact'],build)]:
        artifact=new['artifact'];assert pin(ROOT/old['path'])=={k:old[k] for k in ['bytes','sha256']} and pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(ROOT/old['path']) as a,zipfile.ZipFile(ROOT/artifact['path']) as b:
            assert b.testzip() is None and len(b.namelist())==len(set(b.namelist())) and len(set(b.namelist())-set(a.namelist()))==new['added_classes']+new['added_resources'] and all(a.read(n)==b.read(n) for n in a.namelist())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical_executable_unchanged']==build['canonical_executable_unchanged']
    print('Checking all three immutable R library contents',flush=True)
    base=inventory(ROOT/'build/omics-sctransform-r');private=inventory(BASE/'reference-setup-1/R-lib');motif=inventory(BASE/'motif-reference-setup-1/R-lib')
    assert base==read(BASE/'reference-setup-1/BASE-BEFORE.json') and len(base)==23902
    with zipfile.ZipFile(PARENT/'controls.zip') as z:parent_runtime=json.loads(z.read('runtime-content-inventory.json'))
    assert private==parent_runtime['private_file_pins'] and len(private)==939 and motif==parent_runtime['motif_private_file_pins'] and len(motif)==1417
    catalog=ROOT/'docs/literature/omics-2023-2026';catalog_pins={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts;assert pin(catalog/name)['sha256']==digest;catalog_pins[name]=pin(catalog/name)
    summary=read(catalog/'summary.json');counts=summary['implementation_status_counts'];assert len(catalog_pins)==402 and summary['papers']==250 and summary['distinct_named_tools']==387 and summary['paper_tool_observations']==2442 and (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(16,52,259)
    assert len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827 and 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    sources=dict(prior['source_pins'])
    constructor=read(BASE/'scan-pfm-constructor-1/COMPLETED.json');pfm_fixture=read(ROOT/'src/test/resources/org/jlinalg/sequence/signac-pfm-construction/CAPTURE.json');assert constructor['controls']==10 and pin(ROOT/'src/test/resources/org/jlinalg/sequence/signac-pfm-construction/original-controls.zip')==pfm_fixture['archive']
    for record in [v for v in records if v is not worker]+[fixture,constructor,pfm_fixture]:
        for name,value in record['source_pins'].items():
            if name in sources:assert sources[name]==value
            sources[name]=value
    profiles=read(ROOT/'src/main/resources/org/jlinalg/omics/signac-scan/SOURCE-PROFILES.json')
    for profile in profiles['records']:
        for function in profile['functions']:
            name=function['original_source'];value=function['original_source_pin'];assert pin(ROOT/name)==value
            if name in sources:assert sources[name]==value
            sources[name]=value
    for name in ['tools/literature/freeze_signac_scan_development.py','tools/literature/verify_signac_scan_development.py','tools/literature/signac_scan_evidence.py','tools/literature/test_signac_scan_evidence.py','docs/literature/SIGNAC_SCAN_DEVELOPMENT.md']:sources[name]=pin(ROOT/name)
    for path in (ROOT/'examples/signac-scan').rglob('*'):
        if path.is_file():sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/SIGNAC_SCAN_DEVELOPMENT.md').read_text();files={}
    def add(path,name=None):
        assert not path.is_symlink();name=name or path.relative_to(ROOT).as_posix();raw=path.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    retained=[]
    for folder in sorted(BASE.glob('scan-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for path in sorted(folder.rglob('*')):
                relative=path.relative_to(folder)
                if path.is_file() and path.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in relative.parts:add(path)
    assert {'scan-original-1','scan-original-2','scan-original-3','scan-original-4','scan-original-5','scan-original-6','scan-original-7','scan-request-probe-1','scan-examples-1','scan-examples-2'}.issubset(retained)
    for run in examples['runs']+examples['native_runs']:
        for path in sorted((ROOT/run['analysis_path']).rglob('*')):
            if path.is_file():add(path)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(PARENT/name)
    files['runtime-content-inventory.json']=(json.dumps(dict(base_files=23902,base_unchanged=True,base_inventory_sha256=pin(BASE/'reference-setup-1/BASE-BEFORE.json')['sha256'],private_files=939,private_unchanged=True,private_file_pins=private,motif_private_files=1417,motif_private_unchanged=True,motif_private_file_pins=motif,complete_host_DLL_closure=False),indent=2)+'\n').encode()
    files['unchanged-catalog.json']=(json.dumps(dict(checksums=catalog_pins,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,papers=250),indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS

SKIP3 — Missing or forbidden R/package dependencies skip the dependent analysis. Supply the exact permitted profile in separate private libraries and retry into NEW outputs; otherwise retain the skip. Analysis never downloads or installs software. Wrong versions fail admission.

FAIL2 — Invalid input, original source errors, exceeded work, timeouts, interruption or failed audits admit no result. Preserve the capture, read WARNINGS.md, diagnostics, manifest, status and worker logs, correct the named cause and use NEW paths. Existing destinations are refused. Never use retained partials as results.

EXPLICIT_PROFILE — Historical Signac functions run unchanged on recorded current dependencies, with scoped Seurat access and older FaFile API bridges. They are not historical whole-package installations. motifmatchr1.34.0 is explicit; paper-era scanner/chromVAR execution is pending. Original AddMotifs scanner defaults are fixed. Do not infer paper-exact thresholds or collection editions.

PFM_INTEGER_CONSTRUCTION — The original TFBSTools1.50.0 constructor truncates nonnegative fractional counts toward zero. Compare raw declarations with effective-pfms.tsv and diagnostics. Every effective count/background/ID/position is checked natively. Zero effective columns are not repaired; the original scanner admits or rejects them. Counts beyond finite R integer range fail original construction.

SOURCE_REFERENCE — GC counts literal C/G over full declared width including ambiguity; dinucleotides count adjacent canonical bases. No trimming, chromosome aliasing, Tn5 shift or ambiguity expansion occurs. Missing reference statistics remain unavailable and out-of-reference ranges fail.

MISSING_CHROMOSOME_PLACEHOLDER — Modern source can insert one TRUE matrix entry for the final missing row/final motif. This is an identified source placeholder, NOT an observed motif hit. Older source failures and all-missing failures remain preserved.

SOURCE_INFERENCE — The hypergeometric population is the background alone. NaN/Inf and source zero probabilities remain explicit. Review populations, multiplicities and the full correction family. Enrichment is not per-cell TF activity or regulatory causality.

UNICODE_ID_RESTRICTION — Unicode paths/cell IDs pass. Adding Unicode to motif IDs fails the original matrix/PWM name-consistency check under the pinned C locale; that FAIL2 is retained. Do not silently rename identities. Display-name suffixes retain upstream locale behavior.

VERIFY_LIMITS — Native audits independently check reference statistics, counts, hit geometry/membership and enrichment. They do not independently reproduce MOODS calibration, R KDE/RNG, every locale suffix rule or complete host/DLL closure. Hashes prove integrity, not authenticity. Unconsumed manifest fields are not an authentication guarantee.

EVIDENCE_ARCHIVE — controls.zip stores each SHA-256 content once and maps every original logical path in logical-files.json. All failed attempts and bytes remain present. Use the strict signac_scan_evidence.unpack reader with VALIDATION.json to review logical files; the physical object names are content addresses. Hashes are integrity checks, not signatures.

DEVELOPMENT_SCOPE — Actual upstream FIT is tested on Windows R4.6.1. Java native logic/captured VERIFY pass Windows Java21/25 and Linux Java21. No Linux upstream FIT, root CLI route, release, push, deployment or complete Signac/catalog credit is claimed. Per-cell chromVAR and other paper roles remain open. Literature Pages remain withheld.
'''
    TARGET.mkdir();(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');archive=TARGET/'controls.zip'
    print('Freezing original controls, failed attempts and final user workflow captures',flush=True)
    layout=pack(archive,files)
    assert archive.stat().st_size<100000000,'Preserve any oversized attempt and partition explicitly'
    result=dict(schema_version=1,status='passed-guarded-Signac-scan-enrichment-development',source_pins=sources,artifact=build['artifact'],native_input_artifact=inputs['artifact'],native_audit_artifact=audit['artifact'],parent_motif_validation=pin(PARENT/'VALIDATION.json'),parent_motif_archive=pin(PARENT/'controls.zip'),archive=pin(archive),**layout,platform_validation=native['platform_validation'],original_controls=96,successful_original_controls=75,retained_original_failures=21,historical_guarded_worker_baselines=4,original_PFM_constructor_controls=10,additional_integer_boundary_controls=6,corrected_boundary_fit_admitted=True,exact_one_class_boundary_delta_verified=True,effective_PFM_slots_captured_and_audited=True,actual_workflow_original_comparisons=75,workflow_captured_verifications=225,rehashed_corruption_rejections=102,unlisted_nested_file_rejections=3,copied_baseline_verifications=3,actual_dependency_skips=2,actual_owned_process_checks=2,literal_FIT_requests=3,checked_original_enrichment_and_RNG_comparisons=4,generated_native_enrichment_comparisons=3,checked_example_verifications=22,actual_PFM_export_and_Java_import=True,retained_Unicode_motif_ID_source_failure=True,retained_attempt_directories=retained,base_library_files=23902,private_library_files=939,motif_private_library_files=1417,all_three_R_libraries_unchanged=True,prior_motif_source_pins=277,prior_motif_sources_unchanged=True,canonical_executable_unchanged=build['canonical_executable_unchanged'],catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,literature_pages_withheld=True,historical_whole_packages_executed=False,independent_MOODS_KDE_RNG_replay=False,complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,root_dispatch_available=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,warnings_document=pin(TARGET/'WARNINGS.md'))
    save(TARGET/'VALIDATION.json',result);assert all(pin(ROOT/n)==v for n,v in sources.items());print(json.dumps(dict(status=result['status'],archive=result['archive'],archive_members=len(files),source_pins=len(sources))),flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence checks')
    main()
