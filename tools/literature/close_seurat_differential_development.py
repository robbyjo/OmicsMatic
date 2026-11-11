# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze standalone original marker analysis without whole-tool/catalog promotion."""
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, inventory, pin, save
from signac_scan_evidence import pack

BASE = ROOT / 'build/literature-review/signac-runtime'


def read(path):
    return json.loads(path.read_bytes())


def main():
    folder = ROOT / 'docs/literature/seurat-differential-development'
    assert not folder.exists()
    base = read(BASE / 'differential-workflow-matrix-2/COMPLETED.json')
    extended = read(BASE / 'differential-extended-workflow-1/COMPLETED.json')
    contracts = read(BASE / 'differential-contract-matrix-1/COMPLETED.json')
    recovery = read(BASE / 'differential-recovery-1/COMPLETED.json')
    integrity = read(BASE / 'differential-integrity-1/COMPLETED.json')
    guards = read(BASE / 'differential-source-guard-negative-3/COMPLETED.json')
    examples = read(BASE / 'differential-examples-1/COMPLETED.json')
    gates = read(BASE / 'differential-parent-website-1/COMPLETED.json')
    assert (base['actual_original_requests'], base['portable_capture_verifications']) == (12, 36)
    assert not base['changed_parent_entries'] and base['comparisons']['finite_fields'] == 7030
    assert base['comparisons']['maximum_relative_error'] == 0
    assert (extended['actual_original_requests'], extended['admitted_source_tables'], extended['expected_source_failures'],
            extended['partial_source_comparison_sets'], extended['portable_successful_verifications'],
            extended['portable_failed_capture_refusals']) == (20, 18, 2, 2, 54, 6)
    assert extended['comparisons']['finite_fields'] == 8240
    assert extended['comparisons']['maximum_relative_error'] < 5e-15
    assert contracts['tests_per_JVM'] == 36 and len(contracts['JVMs']) == 3
    assert len(recovery['controls']) == 8 and recovery['existing_output_preserved']
    assert integrity['semantic_resealed_refusals'] == 14 and integrity['coordinated_forgery_can_pass']
    assert integrity['coordinated_forgery_verified_on_JVMs'] == 3 and not integrity['forged_fixture_is_scientific_result']
    assert len(guards['runs']) == 2 and not guards['statistical_tests_executed']
    assert (examples['original_requests'], examples['portable_verifications'], examples['existing_output_refusals']) == (4, 12, 4)
    assert gates['catalog_checksum_entries'] == 416 and len(gates['gates']) == 3
    for proof in [base, extended, contracts, recovery, integrity, guards, examples, gates]:
        assert all(pin(ROOT / n) == value for n, value in proof['source_pins'].items())
    junit = ET.parse(ROOT / 'build/test-results/test/TEST-org.jlinalg.cli.SeuratDifferentialContractsTest.xml').getroot()
    assert all(int(junit.get(k)) == v for k, v in dict(tests=36, skipped=0, failures=0, errors=0).items())
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    assert canonical == base['canonical_executable_preserved'] == gates['canonical_executable_preserved']
    artifact = base['artifact']
    assert pin(ROOT / artifact['path']) == {k: artifact[k] for k in ['bytes', 'sha256']}
    baseline = read(BASE / 'differential-workflow-matrix-2/BASES-BEFORE.json')
    assert baseline == read(BASE / 'differential-workflow-matrix-2/BASES-AFTER.json')
    assert baseline == read(BASE / 'differential-extended-workflow-1/BASES-BEFORE.json')
    assert baseline == read(BASE / 'differential-extended-workflow-1/BASES-AFTER.json')
    assert inventory() == baseline and sum(len(v) for v in baseline.values()) == 27958
    catalog = ROOT / 'docs/literature/omics-2023-2026'
    summary = read(catalog / 'summary.json')
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations']) == (250, 387, 2444)
    assert summary['implementation_status_counts']['missing'] == 258
    assert summary['implementation_status_counts']['native-subset-validated'] == 16
    assert summary['implementation_status_counts']['upstream-adapter-validated'] == 54
    tools = read(catalog / 'tool-inventory.json')
    assert next(t for t in tools if t['id'] == 'signac')['implementation_status'] == 'missing'
    assert next(t for t in tools if t['id'] == 'seurat')['implementation_status'] == 'method-overlap'
    names = [*sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('SeuratDifferential*.java')),
             ROOT / 'src/test/java/org/jlinalg/cli/SeuratDifferentialContractsTest.java',
             ROOT / 'src/main/resources/org/jlinalg/omics/seurat_differential.R',
             *sorted((ROOT / 'src/main/resources/org/jlinalg/omics/seurat-differential-reference').iterdir()),
             *sorted((ROOT / 'examples/seurat-differential').iterdir()),
             ROOT / 'docs/literature/SEURAT_DIFFERENTIAL_DEVELOPMENT.md']
    names += [ROOT / 'tools/literature' / n for n in [
        'close_seurat_differential_development.py', 'verify_seurat_differential_development.py',
        'validate_seurat_differential.py', 'validate_seurat_differential_extended.py',
        'validate_seurat_differential_contracts.py', 'validate_seurat_differential_recovery.py',
        'validate_seurat_differential_integrity.py', 'validate_seurat_differential_source_guard.py',
        'validate_seurat_differential_examples.py', 'validate_seurat_differential_gates.py',
        'probe_seurat_differential_source_guard.R', 'prepare_seurat_differential_examples.py',
        'prepare_seurat_differential_requests.py', 'prepare_seurat_differential_extended_requests.py',
        'prepare_seurat_differential_sources.py', 'collect_seurat_differential_extended.R',
        'probe_seurat_differential_cli.py', 'seurat-differential-development.init.gradle']]
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in names if p.is_file()}
    files = {}

    def add(path, name=None):
        assert not path.is_symlink()
        name = name or path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        assert name not in files or files[name] == raw
        files[name] = raw

    for n in sources:
        add(ROOT / n, 'current-source/' + n)
    families = [
        'differential-requests-1', 'differential-requests-2', 'differential-extended-requests-1',
        'differential-extended-reference-1', 'differential-original-reference-3',
        'differential-original-transport-probe-1', 'differential-smoke-1', 'differential-smoke-2',
        'differential-all-smoke-1', 'differential-latent-smoke-1',
        'differential-workflow-matrix-1', 'differential-workflow-matrix-2',
        'differential-extended-workflow-1', 'differential-contract-matrix-1',
        'differential-recovery-1', 'differential-integrity-1',
        'differential-source-guard-negative-1', 'differential-source-guard-negative-2',
        'differential-source-guard-negative-3', 'differential-examples-1',
        'differential-parent-website-1']
    for sub in families:
        assert (BASE / sub).is_dir(), sub
        for p in (BASE / sub).rglob('*'):
            if p.is_file():
                add(p)
    for run in examples['runs']:
        for p in (ROOT / run['analysis']).rglob('*'):
            if p.is_file():
                add(p)
    add(ROOT / 'build/test-results/test/TEST-org.jlinalg.cli.SeuratDifferentialContractsTest.xml', 'normal-Gradle-tests.xml')
    prior = []
    for number in [1, 2, 3]:
        path = BASE / ('differential-artifact-attempt-' + str(number))
        jars = list(path.glob('*.jar'))
        assert len(jars) == 1
        prior.append(dict(path=jars[0].relative_to(ROOT).as_posix(), **pin(jars[0])))
        for p in path.glob('*.json'):
            add(p)
        with zipfile.ZipFile(jars[0]) as archive:
            for name in archive.namelist():
                if name.startswith(('org/jlinalg/cli/SeuratDifferential', 'org/jlinalg/omics/seurat-differential-reference/', 'org/jlinalg/omics/seurat_differential.R')) and not name.endswith('/'):
                    files['prior-candidate-new-entries/' + str(number) + '/' + name] = archive.read(name)
    with zipfile.ZipFile(ROOT / artifact['path']) as archive:
        for name in base['new_entries']:
            if not name.endswith('/'):
                files['actual-new-entries/' + name] = archive.read(name)
    warning = '''# DEVELOPMENT: ORIGINAL SEURAT MARKER CAPTURE; BROADER TOOLS UNFINISHED

SKIP3 means skip dependent analysis when the exact permitted Rscript/library/package is unavailable. FAIL2 admits no result for malformed input, source/version/model/integrity/process/capacity/finalization failure. Preserve every attempt and inspect analysis-status.json, diagnostics.json, WARNINGS.md, worker report and logs. Correct the named cause and retry into NEW output paths; existing outputs and shared libraries must not be overwritten. Analysis never installs software.

PARTIAL SOURCE RESULT means some requested comparisons were not tested. Original FindAllMarkers can silently omit a failed group in its positive-only branch. The adapter records the error and prints a first-position partial banner. Missing markers in that group are not negative results. Require the comparison completeness your analysis needs.

VERIFY audits input/source/process bindings, preparation, summaries, selection, correction, ordering and required warning text. It does not refit Wilcoxon/LR p values, independently replay sampling, authenticate DLLs or certify biological validity. A retained TEST-ONLY coordinated-probability forgery passes all three JVM verifiers after consumed copies and hashes are updated. Never use that fixture as a scientific result. The guard covers42 functions in one retained source file, not the whole namespace or generic dispatch.

Use the exact R4.6.1/base-Seurat5.6.0 profile and declared private libraries. Presto and limma must be absent; an optional installation suggestion is not a missing dependency. Preserve original GLM and source arithmetic warnings, nonfinite kinds and exact identities. Count/data/scale layers, normalization history, biological replication and covariates are explicit declarations; no repair, pseudobulking or source-semantic replacement occurs. The worker file-byte cap is not a process-memory cap.

Read SEURAT_DIFFERENTIAL_DEVELOPMENT.md and examples/seurat-differential/README.md. Standalone development interface only: no root route or catalog credit. Signac remains missing, Seurat method-overlap;70new scoped integrations,73including existing adapters and258missing. No Linux R fits, historical whole-package/biological/full-paper validation, full-project test suite, release, push, deployment or whole-goal completion is claimed. Cell Ranger remains excluded.
'''
    folder.mkdir()
    (folder / 'WARNINGS.md').write_text(warning, encoding='utf8', newline='\n')
    save(folder / 'INCOMPLETE.json', dict(status='INCOMPLETE_SEURAT_DIFFERENTIAL_CHECKPOINT'))
    layout = pack(folder / 'controls.zip', files)
    result = dict(
        schema_version=1, status='passed-standalone-typed-original-Seurat-marker-development',
        source_pins=sources, archive=pin(folder / 'controls.zip'), warnings_document=pin(folder / 'WARNINGS.md'),
        **layout, artifact=artifact, parent=base['parent'], changed_parent_entries=base['changed_parent_entries'],
        new_entries=base['new_entries'], prior_candidate_artifacts_retained_locally=prior,
        canonical_executable_preserved=canonical, normal_Gradle_contract_tests=36,
        actual_JAR_contract_tests_per_JVM=36, JVMs=contracts['JVMs'],
        source_guard_functions=42, negative_source_body_and_formal_refusals=2,
        original_matrix_requests=32, matrix_returned_tables=30, matrix_expected_source_failures=2,
        matrix_partial_comparison_sets=2, matrix_portable_successful_verifications=90,
        matrix_portable_failed_capture_refusals=6, matrix_finite_fields=15270,
        matrix_maximum_relative_error=extended['comparisons']['maximum_relative_error'],
        recovery_controls=8, semantic_resealed_refusals=14,
        coordinated_forgery_can_pass=True, coordinated_forgery_probe_JVMs=3,
        documented_original_examples=4, documented_example_portable_verifications=12,
        documented_example_overwrite_refusals=4, documented_examples_finite_fields=examples['finite_fields'],
        prior_R_library_files_unchanged=27958, website_check_passed=True,
        catalog_checksum_entries_verified=416, catalog_snapshot_pins=gates['catalog_preserved'],
        catalog_counts=dict(papers=250, tools=387, observations=2444, new_scoped_integrations=70,
                            including_existing=73, missing=258),
        Signac_catalog_status='missing', Seurat_catalog_status='method-overlap',
        standalone_typed_original_analysis_interface=True, retained_failed_and_superseded_attempts=True,
        native_statistical_p_value_replay=False, native_seeded_sampling_replay=False,
        root_dispatch_added=False, catalog_implementation_credit=False,
        historical_whole_packages_executed=False, Linux_R_fitting_executed=False,
        installed_DLL_source_authentication=False, whole_namespace_or_generic_dispatch_authentication=False,
        biological_accuracy_certified=False, full_paper_reproduction=False,
        full_project_test_suite_run=False, released=False, pushed=False, deployed=False, whole_goal_complete=False)
    assert all(pin(ROOT / n) == value for n, value in sources.items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    save(folder / 'VALIDATION.json', result)
    (folder / 'INCOMPLETE.json').unlink()
    print(json.dumps(dict(status=result['status'], source_pins=len(sources), archive=result['archive'],
                          logical_files=layout['logical_files'], catalog_implementation_credit=False)))


if __name__ == '__main__':
    main()
