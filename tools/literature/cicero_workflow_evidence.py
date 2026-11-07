# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify sealed synthetic Cicero controls and independently recalculate all admitted pairs."""
import csv
import gzip
import io
import json
import math
from collections import defaultdict
from pathlib import PurePosixPath
import zipfile
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source, reverse_delta

BASE = 'build/literature-review/monocle-runtime/'
FINAL = dict(build='cicero-workflow-build-8', baseline='cicero-workflow-validation-8',
             variants='cicero-workflow-variants-2', contracts='cicero-workflow-contracts-5',
             recovery='cicero-semantic-recovery-2', example='cicero-workflow-example-1')


def audit_connections(files, folder):
    """Recalculate the complete directed population, source factor order and sign reconciliation."""
    def get(name):
        return json.loads(files[folder + name])

    def table(name):
        result = get(name)
        if 'parts' not in result:
            return result
        columns = None
        rows = 0
        for part in result['parts']:
            assert PurePosixPath(part).name == part
            block = get(part)
            assert 'parts' not in block
            if columns is None:
                columns = [{**c, 'tokens': [], 'kinds': []} for c in block['columns']]
            assert len(columns) == len(block['columns'])
            for before, after in zip(columns, block['columns']):
                assert {k: v for k, v in before.items() if k not in ['tokens', 'kinds']} == {
                    k: v for k, v in after.items() if k not in ['tokens', 'kinds']}
                assert len(after['tokens']) == len(after['kinds']) == block['rows']
                before['tokens'].extend(after['tokens'])
                before['kinds'].extend(after['kinds'])
            rows += block['rows']
        assert rows == result['rows']
        return dict(rows=rows, columns=columns)

    contributions = defaultdict(list)
    levels = []
    seen = set()
    models = get('models.json')
    modeled = 0
    asymmetry = 0.0
    for window in models['windows']:
        if window['status'] != 'original-model':
            continue
        name = window['matrix_file']
        assert PurePosixPath(name).name == name
        model = get(name)
        assert model['errflag'] == 0
        ids = window['peak_ids']
        matrices = {}
        for key in ['covariance', 'penalty', 'w', 'wi']:
            matrix = model[key]
            assert matrix['row_ids'] == matrix['column_ids'] == ids
            values = [list(map(float, row)) for row in matrix['values']]
            assert len(values) == len(ids) and all(len(row) == len(ids) for row in values)
            assert all(math.isfinite(v) for row in values for v in row)
            matrices[key] = values
        w, wi = matrices['w'], matrices['wi']
        for name in ids:
            if name not in seen:
                levels.append(name)
                seen.add(name)
        for i, first in enumerate(ids):
            assert w[i][i] > 0 and wi[i][i] > 0
            for j, second in enumerate(ids):
                assert abs(w[i][j] - w[j][i]) <= 1e-12 * max(1, abs(w[i][j]))
                asymmetry = max(asymmetry, abs(wi[i][j] - wi[j][i]))
                if i != j:
                    value = (math.sqrt(1 / w[i][i]) * w[i][j]) * math.sqrt(1 / w[j][j])
                    assert math.isfinite(value)
                    contributions[(first, second)].append(value)
        modeled += 1
    connections = table('connections.json')
    columns = connections['columns']
    assert [c['name'] for c in columns] == ['Peak1', 'Peak2', 'coaccess']
    assert columns[0]['class'] == ['character'] and columns[1]['class'] == ['factor']
    assert columns[1]['levels'] == levels
    assert all(len(c['tokens']) == len(c['kinds']) == connections['rows'] for c in columns)
    order = {name: i for i, name in enumerate(levels)}
    expected = sorted(contributions, key=lambda pair: (pair[0].encode('utf8'), order[pair[1]]))
    actual = list(zip(columns[0]['tokens'], columns[1]['tokens']))
    assert actual == expected and len(actual) == len(set(actual))
    assert all(k == 'value' for c in columns[:2] for k in c['kinds'])
    unresolved = 0
    for pair, token, kind in zip(actual, columns[2]['tokens'], columns[2]['kinds']):
        values = contributions[pair]
        if any(v > 0 for v in values) and any(v < 0 for v in values):
            assert (token, kind) == ('NA', 'NA')
            unresolved += 1
        else:
            assert kind == 'value'
            value = float(token)
            expected_value = math.fsum(values) / len(values)
            assert math.isfinite(value) and abs(value - expected_value) <= 5e-13 * max(abs(expected_value), 1e-300)
    parsed = list(csv.reader(io.StringIO(files[folder + 'connections.tsv'].decode('utf8'), newline=''), delimiter='\t'))
    assert parsed == [[c['name'] for c in columns], *list(map(list, zip(*(c['tokens'] for c in columns))))]
    audit = get('java-audit.json')
    assert audit['native_axes_aggregation_knn_covariance_penalty_reconciliation_checked']
    assert audit['original_connection_rows'] == len(actual) and audit['original_models'] == modeled
    assert audit['unresolved_source_NA_retained'] == unresolved
    assert abs(audit['original_precision_max_asymmetry'] - asymmetry) <= 1e-16
    assert not audit['precision_symmetrized'] and not audit['original_solver_and_RNG_refitted_or_authenticated']
    return len(actual), unresolved


def verify():
    folder = ROOT / 'docs/literature/cicero-coaccessibility-development'
    data = ROOT / 'docs/literature/omics-2023-2026'
    p = json.loads((folder / 'VALIDATION.json').read_bytes())
    assert p['status'] == 'passed-original-Cicero-coaccessibility-development-controls'
    assert p['adapter_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete']
    for line in (folder / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1)
        assert name in ['controls.zip', 'WARNINGS.md', 'VALIDATION.json'] and pin(folder / name)['sha256'] == digest
    assert pin(folder / 'controls.zip') == p['archive'] and pin(folder / 'WARNINGS.md') == p['warnings_document']
    files = unpack(folder / 'controls.zip', p)
    for name, value in p['source_pins'].items():
        verify_source(ROOT, data, name, value['sha256'])
        assert byte_pin(files['current-source/' + name]) == value
    for name, value in p['parent_proofs'].items():
        assert pin(ROOT / name) == value
    get = lambda name: json.loads(files[BASE + name])
    proofs = {key: get(name + '/COMPLETED.json') for key, name in FINAL.items()}
    build = proofs['build']
    assert all(v['artifact'] == p['artifact'] for v in proofs.values())
    assert build['parent_artifact'] == p['parent_artifact']
    before = json.loads(files['artifact-parent-entries.json'])
    after = json.loads(files['artifact-candidate-entries.json'])
    assert all(after[n] == v for n, v in before.items()) and len(before) == p['unchanged_parent_entries']
    assert set(after) - set(before) == set(p['added_entries'])
    for name in p['added_entries']:
        assert byte_pin(files['candidate-owned-entry/' + name]) == after[name]
    for label, key in [('parent', 'parent_artifact'), ('candidate', 'artifact')]:
        path = ROOT / p[key]['path']
        if path.exists():
            assert pin(path) == {k: p[key][k] for k in ['bytes', 'sha256']}
            with zipfile.ZipFile(path) as z:
                assert len(z.namelist()) == len(set(z.namelist()))
                assert {n: byte_pin(z.read(n)) for n in z.namelist()} == (before if label == 'parent' else after)
    profile_root = 'current-source/src/main/resources/org/jlinalg/omics/cicero-workflow/'
    profile = json.loads(files[profile_root + 'SOURCE-PROFILE.json'])
    assert profile['version'] == '1.3.9' and profile['r_version'] == '4.6.1'
    for name, value in profile['files'].items():
        assert byte_pin(files[profile_root + name]) == value
    assert sum(map(len, profile['guarded_functions'].values())) == 17
    controls = get('cicero-native-controls-1/COMPLETED.json')
    fixture = files['current-source/src/test/resources/org/jlinalg/singlecell/cicero-coaccessibility/original-controls-v1.json.gz']
    assert byte_pin(fixture) == controls['fixture'] and byte_pin(gzip.decompress(fixture)) == controls['uncompressed']
    native = json.loads(gzip.decompress(fixture))
    assert native['original_package_loaded'] and len(native['controls']) == controls['controls'] == 498
    assert native == get('cicero-native-controls-1/controls.json')
    contracts = proofs['contracts']
    assert contracts['tests_per_JVM'] == 516 and len(contracts['JVMs']) == 3
    assert all(v['tests'] == 516 and v['exit_code'] == 0 and v['skipped'] == 0 for v in contracts['JVMs'])
    assert len(proofs['baseline']['comparisons']) == 2
    assert sum(v['rows'] for v in proofs['baseline']['comparisons'].values()) == 4344
    assert all(v['all_original_connections_models_calibration_aggregation_and_saved_axes_equal'] for v in proofs['baseline']['comparisons'].values())
    variants = proofs['variants']
    assert len(variants['comparisons']) == 6 and len(variants['protocols']) == 18
    assert all(v['all_original_connections_models_aggregation_counts_and_metadata_identical'] for v in variants['comparisons'].values())
    for row in variants['protocols']:
        prefix = FINAL['variants'] + '/' + row['id'] + '-result/'
        state = get(prefix + 'analysis-status.json')
        assert state['result_admitted'] is row['result_admitted'] is (row['exit_code'] == 0)
        assert 'REQUIRED ACTION:' in files[BASE + prefix + 'WARNINGS.md'].decode('utf8')
        assert any(n.startswith(BASE + prefix + 'results/') for n in files) is state['result_admitted']
        if row['owned_cleanup_checked']:
            assert all(not c['alive_after_cleanup'] for c in get(prefix + 'worker/process-receipt.json')['owned_process_cleanup'])
        if row['exit_code'] == 3:
            assert state['status'] == 'SKIP3_DEPENDENT_ANALYSIS_SKIPPED'
        elif row['exit_code'] == 2:
            assert state['status'] == 'FAIL2_NO_RESULT_ADMITTED'
    recovery = proofs['recovery']
    assert len(recovery['controls']) == 15 and not recovery['hash_guards_used_for_semantic_refusals']
    assert recovery['controls'][0]['exit_code'] == 0
    assert all(v['exit_code'] == v['expected'] == 2 and v['refusal'].startswith('NATIVE AUDIT REFUSED:') for v in recovery['controls'][1:])
    assert proofs['example']['comparison']['rows'] == 2172 and proofs['example']['comparison']['all_original_models_connections_and_aggregation_identical']
    totals = [0, 0]
    count = 0
    admitted = [FINAL[k] + '/' for k in ['baseline', 'variants', 'example']]
    for name in files:
        if name.endswith('/results/coaccess/models.json') or ('/results/' in name and name.endswith('/models.json')):
            if not any(name.startswith(BASE + prefix) for prefix in admitted):
                continue
            rows, missing = audit_connections(files, str(PurePosixPath(name).parent) + '/')
            totals[0] += rows
            totals[1] += missing
            count += 1
    assert count == p['audited_exports'] == 9 and totals == [p['audited_connection_rows'], p['retained_NA_rows']]
    delta = json.loads(files['current-source/docs/literature/omics-2023-2026/cicero-workflow-checkout-shared-source-compatibility.json'])
    assert len(delta['paths']) == 1 and delta['paths'][0]['path'] == '.gitattributes'
    reverse_delta(files['current-source/.gitattributes'], delta['paths'][0])
    for name in ['cicero-workflow-validation-1', 'cicero-workflow-validation-2', 'cicero-workflow-validation-3', 'cicero-workflow-validation-5']:
        assert BASE + name + '/DIAGNOSIS.txt' in files
    assert p['canonical_executable_unchanged'] == build['canonical_executable_unchanged']
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():
        assert pin(canonical) == p['canonical_executable_unchanged']
    print('Verified Cicero: %s complete connection rows (%s retained NA), 18 runtime protocols, 14 semantic refusals, 516 checks on each of three JVMs; parent entries preserved' % tuple(totals))
    return p


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/scientific evidence assertions')
    verify()
