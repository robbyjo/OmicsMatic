# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the tested original-runtime DESeq2 development workflow; no catalog promotion."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/deseq2-runtime'
EVIDENCE=ROOT/'docs/literature/deseq2-inference-development'


def read(path):return json.loads(path.read_bytes())


def main():
    assert not EVIDENCE.exists();EVIDENCE.mkdir()
    matrix=read(BASE/'inference-workflow-matrix-1/COMPLETED.json');contracts=read(BASE/'inference-contract-matrix-1/COMPLETED.json');public=read(BASE/'inference-public-examples-1/COMPLETED.json')
    assert matrix['actual_original_runtime_requests']==26 and matrix['manual_Java_only_verifies']==39
    assert matrix['comparisons']==dict(numeric_values=264436,nonfinite_kind_values=2972,maximum_relative_error=0.0)
    assert contracts['tests_per_JVM']==38 and len(contracts['runs'])==3 and all(r['passed']==38 and r['skipped']==0 for r in contracts['runs'])
    assert public['literal_examples']==3 and public['original_runtime_requests']==4 and public['manual_verifies']==9 and public['maximum_relative_error']==0
    assert public['forbidden_install_skipped_without_library_creation'] and public['existing_runtime_refused_and_preserved']
    gradle=read(BASE/'inference-contract-gradle-1/RESULT.json');assert gradle['tests']==38 and gradle['failures']==gradle['errors']==gradle['skipped']==0
    for record in [matrix,contracts,public]:
        for name,expected in record['source_pins'].items():assert pin(ROOT/name)==expected,'Source changed after validation: '+name
    native=read(ROOT/'docs/literature/deseq2-normalization-development/VALIDATION.json')
    assert all(pin(ROOT/name)==expected for name,expected in native['source_pins'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==matrix['canonical_artifact_unchanged']
    source_artifact=ROOT/matrix['artifact']['path'];artifact_pin=pin(source_artifact);assert artifact_pin==contracts['artifact']==public['artifact']
    frozen=BASE/'inference-owned-artifact-1';assert not frozen.exists();frozen.mkdir();owned=frozen/source_artifact.name;shutil.copyfile(source_artifact,owned);assert pin(owned)==artifact_pin
    extras=read(BASE/'inference-extra-setup-1/RESULT.json');clean=read(ROOT/'build/deseq2-user-runtime/RESULT.json')
    assert extras['status']=='INSTALLED_OPTIONAL_ORIGINAL_SHRINKAGE_REFERENCE_ONLY' and extras['preexisting_libraries_unchanged']
    assert clean['status']=='INSTALLED_DESEQ2_REFERENCE_RUNTIME_NOT_USER_ANALYSIS' and clean['preexisting_libraries_unchanged']
    for runtime in [BASE/'inference-extra-setup-1',ROOT/'build/deseq2-user-runtime']:
        before=read(runtime/'BASES-BEFORE.json');after=read(runtime/'BASES-AFTER.json');assert before==after
        for root,files in after.items():
            for name,expected in files.items():assert pin(Path(root)/name)==expected,'Inventoried prior R-library file changed'
    summary=read(ROOT/'docs/literature/omics-2023-2026/summary.json')
    assert summary['papers']==250 and summary['distinct_named_tools']==387 and summary['implementation_status_counts']['missing']==258
    assert summary['implementation_status_counts']['native-subset-validated']==16 and summary['implementation_status_counts']['upstream-adapter-validated']==53
    warnings='''# DEVELOPMENT: ORIGINAL DESEQ2 WORKFLOW; CATALOG PROMOTION PENDING

This is a tested standalone adapter to original DESeq2 1.52.0 on R4.6.1, not whole-paper or historical-environment reproduction. The full 250-paper implementation goal remains incomplete. Main-dispatcher integration, semantic review of 47 DESeq2 paper uses and catalog promotion remain pending. There is no new release, GitHub push or deployment.

## SKIP3: dependent analysis skipped

Supply the named Rscript, exact R4.6.1/DESeq2 1.52.0 edition, declared private libraries and requested optional packages only where installation/execution is permitted. If it is forbidden or unavailable, skip the dependent request. Read runtime versions, source-guard diagnostics and logs. Preserve each attempt and choose NEW output paths. The user-facing private installer refuses existing library/report paths and reports forbidden installs without creating a library.

## FAIL2: no result admitted

Any partial table in a failed attempt is evidence only. Read WARNINGS.md, analysis-status.json, worker/worker-report.json and stdout/stderr. Correct named count/unit/identity/covariate/rank/contrast/capacity/process errors and retry into NEW outputs. Existing results are preserved. Timeout cleanup stops only the owned worker tree. Neither an incompatible source function nor a failing statistical method is silently substituted.

## Scientific behavior requiring review

Raw counts are not TPM/CPM. Fractional estimates require explicit estimated_counts and R_round; no unit conversion is inferred. Technical replicate collapse is explicit, requires consistent modeled covariates and does not infer biological replication. Size factors use the complete supplied input universe before explicit keep_genes selection. Dataset poscounts centering and its override of supplied means are retained, not replaced by the low-level Java function's defaults.

Original NA/NaN/Inf and Cook/independent-filtering effects remain visible. A missing adjusted p-value is not repaired or treated as a successful null test. Review original result metadata and gene estimates. Original normal shrinkage rejects interaction designs. The tested glmGamPoi LRT factor contrast failed in the original source package; explicit coefficient selection worked. Inspect worker/coefficients.tsv, make a deliberate valid selection and rerun rather than expecting an automatic fallback. LRT p-values describe the model comparison, not merely the displayed fold change.

## Verification and runtime limits

VERIFY checks capture integrity, source/parameter correspondence, identities, prepared counts, normalization and published/worker consistency without R. It does not rerun negative-binomial inference, iterative factor optimization, transforms or shrinkage. Coordinated fabricated tables plus rehashing cannot be authenticated by this capture. Hashes are not signatures, biological validation or complete native-DLL source authentication. R/Java memory is not a hard aggregate bound; review explicit capacities and available resources.

The source guard checks 16 loaded R function bodies/formals against retained original sources. Current dependency versions are recorded; historical paper editions are not assumed equivalent. Quantifier import/length offsets, pseudobulk assembly and wrapper-specific pipelines remain separate work. Linux Java-only verification passed; Linux original-runtime fitting and the full project test suite were not run for this milestone.

## Retained development attempts

Initial generated reference count strings contained formatting padding, which strict admission rejected. A subsequent successful original fit was refused by the coefficient index audit for the same generated padding; the formatter was corrected without changing numeric values. An R test-harness partial match of extra$sf to extra$sf_type caused reference setup failures; exact indexing fixed the harness. The original glmGamPoi factor-contrast failure remains preserved rather than relabeled as success. All those attempts and later independent comparisons are retained.

Use docs/literature/DESEQ2_INFERENCE_DEVELOPMENT.md and examples/deseq2-inference/README.md for exact setup, requests, output interpretation and recovery.
'''
    (EVIDENCE/'WARNINGS.md').write_text(warnings,encoding='utf-8',newline='\n')
    sources={}
    for record in [matrix,contracts,public]:sources.update({n:v for n,v in record['source_pins'].items() if not n.startswith('build/')})
    for p in [Path(__file__),ROOT/'tools/literature/setup_deseq2_shrinkage.py',ROOT/'tools/literature/install_deseq2_shrinkage.R',ROOT/'src/main/java/org/jlinalg/sequence/Deseq2SizeFactors.java',EVIDENCE/'WARNINGS.md',ROOT/'docs/literature/deseq2-normalization-development/VALIDATION.json']:
        sources[p.relative_to(ROOT).as_posix()]=pin(p)
    paths={ROOT/name for name in sources}
    for folder in ['inference-extra-setup-1','inference-worker-probe-1','inference-smoke-1','inference-smoke-2','inference-smoke-3','inference-reference-1','inference-reference-2','inference-reference-3','inference-contract-gradle-1','inference-contract-matrix-1','inference-workflow-matrix-1','inference-public-examples-1']:
        for p in (BASE/folder).rglob('*'):
            if p.is_file() and 'R-lib' not in p.parts and 'downloads' not in p.parts and p.suffix!='.jar':paths.add(p)
    for p in (ROOT/'build/deseq2-user-runtime').rglob('*'):
        if p.is_file() and 'R-lib' not in p.parts and 'downloads' not in p.parts:paths.add(p)
    for r in public['runs']:paths.update(p for p in (ROOT/r['analysis']).rglob('*') if p.is_file())
    paths.add(BASE/'inference-initial-build-1/PRESERVED.json')
    entries={p.relative_to(ROOT).as_posix():pin(p) for p in sorted(paths)}
    archive=EVIDENCE/'controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(paths):z.writestr(p.relative_to(ROOT).as_posix(),p.read_bytes())
        z.writestr('BUNDLE-FILES.json',json.dumps(entries,indent=2)+'\n')
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist())==len(entries)+1 and len(set(z.namelist()))==len(z.namelist())
        for name,expected in entries.items():
            raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected
    result=dict(schema_version=1,status='passed-original-DESeq2-typed-inference-development',source_pins=sources,
        artifact=dict(path=owned.relative_to(ROOT).as_posix(),**artifact_pin),development_build_artifact=matrix['artifact'],
        archive=dict(path='controls.zip',**pin(archive)),archive_members=len(entries)+1,
        parent_native_validation=pin(ROOT/'docs/literature/deseq2-normalization-development/VALIDATION.json'),
        parent_packaged_file_entries_unchanged=True,original_reference_cases=13,
        original_runtime_requests=26,manual_Java_only_verifies=39,comparisons=matrix['comparisons'],
        typed_contract_tests_normal_Gradle=38,typed_contract_tests_per_JVM=38,typed_contract_JVMs=contracts['runs'],
        actual_dependency_skips=2,actual_timeout_cleanup=True,original_source_failure_controls=2,failed_or_skipped_capture_refusals=5,
        matrix_existing_output_refusals=2,literal_examples=3,clean_runtime_original_requests=4,clean_runtime_manual_verifies=9,
        literal_finite_numeric_values=public['finite_numeric_values'],literal_nonfinite_kind_values=public['nonfinite_kind_values'],literal_maximum_relative_error=public['maximum_relative_error'],
        literal_existing_output_refusals=3,clean_installer_forbidden_skipped=True,existing_runtime_install_refused_and_preserved=True,
        prior_R_library_files_inventoried_and_unchanged=extras['preexisting_files'],clean_runtime_setup=pin(ROOT/'build/deseq2-user-runtime/RESULT.json'),
        canonical_artifact_unchanged=matrix['canonical_artifact_unchanged'],source_function_body_formal_guards=16,
        root_dispatch_available=False,catalog_implementation_credit=False,DESeq2_catalog_status='method-overlap',
        papers=250,new_credited_integrations=69,including_existing_adapters=72,missing_tools=258,
        historical_whole_packages_executed=False,full_paper_reproduction=False,quantifier_import_pipeline_available=False,
        independent_Java_NB_replay=False,installed_DLL_source_authentication=False,Linux_original_runtime_fit_tested=False,
        full_project_test_suite_run=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        warnings_document=pin(EVIDENCE/'WARNINGS.md'))
    save(EVIDENCE/'VALIDATION.json',result)
    print(json.dumps({k:result[k] for k in ['status','artifact','archive','archive_members','original_runtime_requests','new_credited_integrations','missing_tools']}))


if __name__=='__main__':main()
