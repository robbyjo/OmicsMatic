# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze tested upstream background/native GC/deviation workflows, failures, skips and verification limits."""
import json,zipfile
from pathlib import Path
from validate_mashr_native import pin
from signac_scan_evidence import pack
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';TARGET=ROOT/'docs/literature/chromvar-background-development'
def read(p):return json.loads(p.read_bytes())
def main():
    assert not TARGET.exists(),'Preserve prior frozen evidence'
    parent_path=ROOT/'docs/literature/chromvar-gc-development/VALIDATION.json';parent=read(parent_path);ancestor_path=ROOT/'docs/literature/chromvar-deviations-development/VALIDATION.json';ancestor=read(ancestor_path)
    assert all(pin(ROOT/n)==v for r in [parent,ancestor] for n,v in r['source_pins'].items()) and len(ancestor['source_pins'])==437 and pin(parent_path.parent/'controls.zip')==parent['archive']
    paths=['background-workflow-build-2/BUILD.json','background-request-probe-3/COMPLETED.json','background-workflow-matrix-1/COMPLETED.json','background-reference-chain-1/COMPLETED.json','background-recovery-1/COMPLETED.json','background-examples-1/COMPLETED.json'];records=[read(BASE/p) for p in paths];build,probe,matrix,chain,recovery,examples=records
    assert build['parent_artifact']==parent['artifact'] and all(r['artifact']==build['artifact'] for r in records)
    assert probe['original_background_indices_and_RNG_match'] and probe['captured_verifications']==3 and probe['Unicode_paths_and_IDs']
    assert (len(matrix['original_background_and_RNG_comparisons']),len(matrix['retained_source_failures']),matrix['captured_verifications'],matrix['failed_capture_rejections'])==(18,12,54,36) and matrix['inputs_and_requests_unchanged']
    assert chain['actual_original_indexed_reference_formula_and_public_background_and_deviations'] and chain['native_reference_GC_used'] and len(chain['runs'])==2 and chain['captured_verifications']==12
    assert (len(recovery['rehashed_single_change_rejections']),recovery['total_rehashed_rejections'],recovery['unlisted_nested_rejections'],recovery['copied_verifications'],recovery['known_coordinated_forgery_structurally_accepted'],recovery['actual_dependency_skips'],len(recovery['retained_gates']),recovery['failed_capture_rejections'])==(14,42,3,3,3,2,7,21) and recovery['known_forgery_is_NOT_an_analysis_result'] and recovery['actual_owned_timeout_cleanup'] and recovery['existing_output_preserved']
    assert (examples['literal_BACKGROUND_requests'],examples['literal_DEVIATIONS_requests'],examples['literal_VERIFY_requests'],examples['original_comparisons'],examples['captured_verifications'],examples['existing_output_refusals'])==(2,2,4,7,21,4) and examples['additional_fresh_Java25_BACKGROUND'] and examples['additional_fresh_Java25_and_Linux_DEVIATIONS'] and examples['inputs_and_requests_unchanged']
    gradle=read(BASE/'background-gradle-native-1/COMPLETED.json');assert gradle['tests']==gradle['passed']==369 and gradle['skipped']==0 and gradle['process']['exit_code']==0
    versions=read(BASE/'background-jvm-versions-1/COMPLETED.json');assert sorted(v['major'] for v in versions['runs'])==[21,21,25] and all(v['exit_code']==0 for v in versions['runs'])
    sources={}
    for r in records:
        for n,v in r['source_pins'].items():assert pin(ROOT/n)==v and (n not in sources or sources[n]==v);sources[n]=v
    for n in ['tools/literature/freeze_chromvar_background.py','tools/literature/verify_chromvar_background.py']:sources[n]=pin(ROOT/n)
    for a in [build['artifact'],parent['artifact']]:assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(ROOT/parent['artifact']['path']) as a,zipfile.ZipFile(ROOT/build['artifact']['path']) as b:assert b.testzip() is None and len(b.namelist())==len(set(b.namelist())) and len(set(b.namelist())-set(a.namelist()))==12 and all(a.read(n)==b.read(n) for n in a.namelist())
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_preserved']==parent['canonical_executable_preserved']
    catalog=ROOT/'docs/literature/omics-2023-2026';catalog_pins={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,n=line.split('  ',1);assert pin(catalog/n)['sha256']==digest;catalog_pins[n]=pin(catalog/n)
    summary=read(catalog/'summary.json');counts=summary['implementation_status_counts'];assert len(catalog_pins)==402 and (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(250,387,2442,16,52,259)
    assert len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827 and 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    assert build['artifact']['sha256'] in (ROOT/'docs/literature/CHROMVAR_BACKGROUND_DEVELOPMENT.md').read_text(encoding='utf8')
    files={}
    def add(p,name=None):
        assert not p.is_symlink();name=name or p.relative_to(ROOT).as_posix();raw=p.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for n in sources:add(ROOT/n,'current-source/'+n)
    retained=[]
    for folder in sorted(BASE.glob('background-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for p in sorted(folder.rglob('*')):
                if p.is_file() and p.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in p.relative_to(folder).parts:add(p)
    assert {'background-workflow-build-1','background-workflow-build-2','background-request-probe-1','background-request-probe-2','background-request-probe-3','background-workflow-matrix-1','background-reference-chain-1','background-recovery-1','background-examples-1','background-gradle-native-1','background-jvm-versions-1'}.issubset(retained)
    for run in examples['runs']:
        for p in (ROOT/run['analysis_path']).rglob('*'):
            if p.is_file():add(p)
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md','GRADLE-VALIDATION.json','gradle-check.zip']:add(parent_path.parent/name,'parent-native-GC/'+name)
    add(ancestor_path,'parent-native-deviations/VALIDATION.json');files['unchanged-catalog.json']=(json.dumps(catalog_pins,indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS — CHROMVAR BACKGROUNDS

SKIP3 — Original BACKGROUND requires R 4.6.1, chromVAR1.34.1, Matrix1.7.5, nabor0.5.0, Rcpp1.1.2, RcppArmadillo15.6.2-1 (R version form15.6.2.1), jsonlite2.0.0. Supply the exact permitted runtime/private libraries through the four declared runtime fields. No installation is performed here. If unavailable or installation is forbidden, preserve the skip and omit dependent scoring without a valid separately prepared background. Two actual dependency skips were tested, including a real R worker with a missing package.

FAIL2 — Source covariance/rank failures, undefined bias, nonpositive pooled counts, invalid input/reference/index, exceeded capacities, worker failure and timeout admit no result. Preserve every attempt, inspect status, manifest, WARNINGS and worker logs; correct the named cause and retry into NEW paths. Existing outputs are refused. Real timeout cleanup of the owned worker tree is retained. Failed/skipped captures cannot pass VERIFY.

GC_AND_IDENTITIES — Native GC=(C+G)/(A+C+G+T), excluding ambiguity/gaps/masks. All-ambiguous sequences retain NaN; no row removal, imputation or aliasing occurs. Counts, bias/ranges and outputs bind to exact ordered peak/cell populations. R exchanges audited numeric row indices so original Unicode names remain intact in Java-captured inputs and named outputs. Use an ASCII command-line request-file path for this environment's WindowsJava25; tested Unicode paths/IDs reside inside UTF-8 requests.

SOURCE_REPLAY — Unchanged 1.16.0/1.34.1 R cores and byte-identical retained utils.cpp sources are used on recorded current dependencies. The source and current public Matrix call must agree exactly in indices and full seeded RNG states. Historical whole packages, chromVAR1.5.0, installed-DLL source authentication and independent native RNG replay are not certified. Actual upstream FIT clients were WindowsJava21/25; portable VERIFY/native follow-ups were also tested on LinuxJava21.

VERIFY_IS_STRUCTURAL — VERIFY recomputes native count/reference/GC preparation and checks source/request/input/protocol/settings, range/order, worker/published agreement, recorded RNG-state agreement, warnings and inventory. It does not rerun R sampling. The explicit adversarial synthetic NOT-A-RESULT fixture shows coordinated changes to worker and published indices followed by rehashing are accepted on all three JVMs. Hashes establish integrity, not authenticity or proof of execution; never claim independent sampler reproduction from VERIFY.

FOLLOW_UP — background.tsv supplies explicit one-based indices for native ChromVarRequestCli. prepared-inputs.json is a request fragment: add NEW out, motif_collection, motif_labels and annotation_peaks or motif_matrix. Rebase its relative count/feature/cell/background paths if writing the complete request elsewhere. Keep new requests outside sealed captures. Complete current/legacy checked examples provide actual inputs, no placeholders.

EVIDENCE_AND_PUBLICATION — controls.zip uses deduplicated SHA-256 objects plus a logical-file map and retains failures, skips, corruption probes and all successful controls. Native normal-build tests passed369; source-population requests matched18 successes and retained12 failures. This is local development, with no root route, catalog promotion, release, push, deployment, biological-validity claim or complete Signac/whole-paper reproduction. Prior source pins, catalog, shared canonical JAR and withheld literature Pages are preserved. Catalog remains68 new scoped integrations,71 including existing tools,259 missing.
'''
    TARGET.mkdir();(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');archive=TARGET/'controls.zip';layout=pack(archive,files);assert archive.stat().st_size<100000000
    result=dict(schema_version=1,status='passed-original-chromVAR-background-native-GC-and-deviation-workflow-development',source_pins=sources,artifact=build['artifact'],parent_artifact=parent['artifact'],parent_validation=pin(parent_path),parent_archive=parent['archive'],ancestor_deviation_validation=pin(ancestor_path),archive=pin(archive),**layout,original_background_and_RNG_request_comparisons=18,retained_source_failure_requests=12,captured_request_verifications=54,failed_source_capture_rejections=36,reference_GC_background_deviation_source_profiles=2,reference_chain_verifications=12,rehashed_single_change_rejections=42,unlisted_nested_file_rejections=3,copied_verifications=3,known_coordinated_forgery_structurally_accepted=3,forged_fixture_is_NOT_an_analysis_result=True,actual_dependency_skips=2,additional_failure_gates=5,recovery_failed_capture_rejections=21,actual_owned_timeout_cleanup=True,existing_outputs_preserved=True,literal_BACKGROUND_requests=2,literal_DEVIATIONS_requests=2,literal_VERIFY_requests=4,checked_example_original_comparisons=7,checked_example_verifications=21,checked_existing_output_refusals=4,fresh_Windows_Java25_original_BACKGROUND=True,fresh_Windows_Java25_and_Linux_native_DEVIATIONS=True,normal_Gradle_native_tests=369,normal_Gradle_passed=369,normal_Gradle_skipped=0,complete_project_test_suite_run=False,upstream_FIT_clients=['windows-java21','windows-java25'],captured_VERIFY_platforms=['windows-java21','windows-java25','linux-java21'],canonical_executable_preserved=canonical,parent_GC_30_source_pins_unchanged=True,ancestor_437_source_pins_unchanged=True,catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,reviewed_uses=827,literature_pages_withheld=True,end_user_background_development_adapter_available=True,native_reference_GC_and_native_deviation_chain_available=True,independent_native_RNG_replay=False,installed_DLL_source_authentication=False,historical_whole_packages_executed=False,chromVAR_1_5_0_executed=False,full_Signac_workflow_available=False,root_dispatch_available=False,catalog_implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,retained_attempt_directories=retained,warnings_document=pin(TARGET/'WARNINGS.md'))
    (TARGET/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in sources.items());print(json.dumps(dict(status=result['status'],archive=result['archive'],physical_members=layout['archive_members'],logical_files=layout['logical_files'],source_pins=len(sources))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full background milestone checks')
    main()
