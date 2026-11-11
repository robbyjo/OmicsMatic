# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline source/archive and independent-original comparison audit; never refit/install."""
import csv
import hashlib
import io
import json
import math
import sys
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack

PREFIX = 'build/literature-review/signac-runtime/'


def compare(original, observed, mode):
    expected = list(csv.DictReader(io.StringIO(original.decode('utf8')), delimiter='\t'))
    actual = list(csv.DictReader(io.StringIO(observed.decode('utf8')), delimiter='\t'))
    assert len(expected) == len(actual), 'Independent original row count differs'
    finite, nonfinite, maximum = 0, 0, 0
    for a, b in zip(expected, actual):
        assert b['feature'] == a['' if mode == 'PAIR' else 'gene']
        assert b['contrast'] == ('pairwise' if mode == 'PAIR' else a['cluster'])
        fields = [('p_val', 'p_value'), ('p_val_adj', 'p_adj'),
                  (next(k for k in a if k.startswith('avg_')), 'fold_change'),
                  ('pct.1', 'pct_1'), ('pct.2', 'pct_2')]
        for old, new in fields:
            x, y = a[old], b[new]
            if x in ['NA', 'NaN', 'Inf', '-Inf']:
                assert x == y, 'Original nonfinite kind differs'
                nonfinite += 1
            else:
                p, q = float(x), float(y)
                assert math.isfinite(q) and (p == 0) == (q == 0)
                error = abs(p - q) / max(abs(p), 1e-300)
                assert error <= 1e-9, 'Independent original numeric comparison differs'
                maximum = max(maximum, error)
                finite += 1
    return dict(rows=len(expected), finite_fields=finite, nonfinite_kinds=nonfinite, maximum_relative_error=maximum)


def main():
    if not __debug__:
        raise RuntimeError('Run with assertions enabled; never Python -O')
    folder = ROOT / 'docs/literature/seurat-differential-development'
    proof = json.loads((folder / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'passed-standalone-typed-original-Seurat-marker-development'
    assert not (folder / 'INCOMPLETE.json').exists()
    assert pin(folder / 'controls.zip') == proof['archive']
    assert pin(folder / 'WARNINGS.md') == proof['warnings_document']
    files = unpack(folder / 'controls.zip', proof)

    def raw(name):
        return files[PREFIX + name]

    def obj(name):
        return json.loads(raw(name))

    def family(path, pins):
        assert {n.removeprefix(path + '/') for n in files if n.startswith(path + '/')} == set(pins)
        for n, wanted in pins.items():
            b = files[path + '/' + n]
            assert dict(bytes=len(b), sha256=hashlib.sha256(b).hexdigest()) == wanted
        seal = json.loads(files[path + '/capture-files.json'])
        assert seal['authentication'] is False and seal['schema_version'] == 1
        assert seal['files'] == {n: p['sha256'] for n, p in pins.items() if n != 'capture-files.json'}

    for name, wanted in proof['source_pins'].items():
        assert pin(ROOT / name) == wanted, name
        b = files['current-source/' + name]
        assert dict(bytes=len(b), sha256=hashlib.sha256(b).hexdigest()) == wanted
    assert proof['catalog_counts'] == dict(papers=250, tools=387, observations=2444,
                                          new_scoped_integrations=70, including_existing=73, missing=258)
    assert proof['Signac_catalog_status'] == 'missing' and proof['Seurat_catalog_status'] == 'method-overlap'
    for key in ['native_statistical_p_value_replay', 'native_seeded_sampling_replay', 'root_dispatch_added',
                'catalog_implementation_credit', 'historical_whole_packages_executed', 'Linux_R_fitting_executed',
                'installed_DLL_source_authentication', 'whole_namespace_or_generic_dispatch_authentication',
                'biological_accuracy_certified', 'full_paper_reproduction', 'full_project_test_suite_run',
                'released', 'pushed', 'deployed', 'whole_goal_complete']:
        assert proof[key] is False, key
    assert proof['standalone_typed_original_analysis_interface'] and proof['coordinated_forgery_can_pass']
    base = obj('differential-workflow-matrix-2/COMPLETED.json')
    extended = obj('differential-extended-workflow-1/COMPLETED.json')
    profile = json.loads(files['current-source/src/main/resources/org/jlinalg/omics/seurat-differential-reference/SOURCE-PROFILE.json'])
    comparisons, partials, failures = [], 0, 0
    for matrix, source_folder, reference_folder, extended_mode in [
            (base, 'differential-requests-2', 'differential-original-reference-3/original', False),
            (extended, 'differential-extended-requests-1', 'differential-extended-reference-1/original', True)]:
        matrix_folder = 'differential-extended-workflow-1' if extended_mode else 'differential-workflow-matrix-2'
        for run in matrix['runs']:
            name, platform = run['case'], run['platform']
            path = PREFIX + matrix_folder + '/' + platform + '-' + name + '-结果'
            family(path, run['files'])
            state = json.loads(files[path + '/analysis-status.json'])
            warnings = files[path + '/WARNINGS.md'].decode('utf8')
            if name == 'pair_failure':
                failures += 1
                assert state['results_admitted'] is False and state['status'] == 'FAIL2_NO_RESULT_ADMITTED'
                assert 'contrasts can be applied only to factors with 2 or more levels' in state['error']
                assert path + '/markers.tsv' not in files and 'FAIL2' in warnings
                continue
            assert state['results_admitted'] is True and state['status'] == 'completed-with-warnings'
            partial = name == 'all_partial'
            assert state['complete_comparison_set'] is not partial
            assert state['failed_source_comparisons'] == (1 if partial else 0)
            if partial:
                partials += 1
                assert warnings.startswith('# PARTIAL SOURCE RESULT')
                assert 'PARTIAL_SOURCE_COMPARISON_SET' in warnings and 'SOURCE_COMPARISON_NOT_TESTED' in warnings
            worker = json.loads(files[path + '/worker/worker-report.json'])
            assert worker['source_guards'] == {n: True for n in profile['functions']}
            assert worker['versions'] == profile['required_packages']
            assert worker['independent_native_statistical_replay'] is False
            request = obj(source_folder + '/' + name + '.json')
            ref_folder, ref_name = reference_folder, name
            if name == 'native_tfidf':
                ref_folder, ref_name = 'differential-original-reference-3/original', 'pair_wilcox'
            elif name == 'counts_mode':
                ref_name = 'pair_count_means'
            observed = compare(raw(ref_folder + '/' + ref_name + '.tsv'), files[path + '/markers.tsv'], request['mode'])
            assert observed == run['comparison'], 'Stored original comparison summary differs'
            comparisons.append(observed)
    assert len(comparisons) == proof['matrix_returned_tables'] == 30
    assert partials == proof['matrix_partial_comparison_sets'] == 2
    assert failures == proof['matrix_expected_source_failures'] == 2
    assert sum(c['finite_fields'] for c in comparisons) == proof['matrix_finite_fields'] == 15270
    assert max(c['maximum_relative_error'] for c in comparisons) == proof['matrix_maximum_relative_error']
    assert (proof['original_matrix_requests'], proof['matrix_portable_successful_verifications'],
            proof['matrix_portable_failed_capture_refusals']) == (32, 90, 6)
    for label in ['differential-workflow-matrix-2', 'differential-extended-workflow-1']:
        before = obj(label + '/BASES-BEFORE.json')
        assert before == obj(label + '/BASES-AFTER.json') and sum(len(v) for v in before.values()) == 27958
    contracts = obj('differential-contract-matrix-1/COMPLETED.json')
    assert contracts['tests_per_JVM'] == proof['actual_JAR_contract_tests_per_JVM'] == proof['normal_Gradle_contract_tests'] == 36
    assert len(contracts['JVMs']) == 3 and all(r['passed'] == 36 and r['skipped'] == 0 for r in contracts['JVMs'])
    recovery = obj('differential-recovery-1/COMPLETED.json')
    assert len(recovery['controls']) == proof['recovery_controls'] == 8
    for run in recovery['controls']:
        family(PREFIX + 'differential-recovery-1/' + run['control'] + '-analysis', run['files'])
        assert run['state']['results_admitted'] is False
        if run['control'] == 'worker-timeout':
            assert len(run['observed_children']) == 4 and all(not p['alive_after_cleanup'] for p in run['observed_children'])
    integrity = obj('differential-integrity-1/COMPLETED.json')
    assert integrity['semantic_resealed_refusals'] == proof['semantic_resealed_refusals'] == 14
    assert integrity['coordinated_forgery_can_pass'] and integrity['coordinated_forgery_verified_on_JVMs'] == 3
    assert integrity['forged_fixture_is_scientific_result'] is False
    for run in integrity['runs']:
        path = PREFIX + 'differential-integrity-1/' + run['control'] + '-TEST-ONLY'
        family(path, run['files'])
        assert obj('differential-integrity-1/' + run['control'] + '.process.json')['exit_code'] == 2
    assert len(obj('differential-source-guard-negative-3/COMPLETED.json')['runs']) == proof['negative_source_body_and_formal_refusals'] == 2
    examples = obj('differential-examples-1/COMPLETED.json')
    assert examples['original_requests'] == proof['documented_original_examples'] == 4
    assert examples['portable_verifications'] == proof['documented_example_portable_verifications'] == 12
    for run in examples['runs']:
        family(run['analysis'], run['files'])
    gates = obj('differential-parent-website-1/COMPLETED.json')
    assert gates['catalog_checksum_entries'] == proof['catalog_checksum_entries_verified'] == 416
    assert len(gates['gates']) == 3 and all(g['exit_code'] == 0 for g in gates['gates'])
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_preserved']
    artifact = ROOT / proof['artifact']['path']
    if artifact.exists():
        assert pin(artifact) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
        parent = ROOT / 'build/literature-review/signac-runtime/differential-summary-development/omicsmatic-0.3.7-seurat-marker-summary-development.jar'
        assert pin(parent) == proof['parent']
        with zipfile.ZipFile(artifact) as new, zipfile.ZipFile(parent) as old:
            assert not proof['changed_parent_entries']
            assert all(new.read(n) == old.read(n) for n in old.namelist())
            assert sorted(set(new.namelist()) - set(old.namelist())) == proof['new_entries']
            for n in proof['new_entries']:
                if not n.endswith('/'):
                    assert new.read(n) == files['actual-new-entries/' + n]
    print(json.dumps(dict(status='passed-offline-standalone-Seurat-marker-development',
                          source_pins=len(proof['source_pins']), logical_files=proof['logical_files'],
                          matrix_tables_compared=30, matrix_finite_fields=15270,
                          coordinated_forgery_can_pass=True, catalog_implementation_credit=False)))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('SEURAT DEVELOPMENT EVIDENCE FAIL2: ' + str(error) +
              '\nRequired action: preserve evidence, correct the named source/archive mismatch and repeat verification. '
              'Do not admit partial or fabricated results.', file=sys.stderr)
        sys.exit(2)
