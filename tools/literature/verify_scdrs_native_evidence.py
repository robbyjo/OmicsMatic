# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped native scDRS evidence; optional Java replay requires no scDRS Python runtime."""
import argparse
import csv
import io
import json
import math
import os
import struct
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import unpack, byte_pin, safe
from intake_scdrs_reference import VERSIONS
from build_mtag_native import DEPENDENCY, DEPENDENCY_SHA

DEST = ROOT / 'docs/literature/scdrs-native-development'
BASE = ROOT / 'build/literature-review/scdrs-runtime'
STATUS = 'PASS_NATIVE_SCDRS_NUMERICAL_COMPONENT_EVIDENCE_ONLY'
SOURCE_FILES = [
    'src/main/java/org/jlinalg/genetics/ScdrsScores.java',
    'src/test/java/org/jlinalg/cli/ScdrsReferenceProbe.java',
    'src/test/java/org/jlinalg/genetics/ScdrsScoresTest.java',
    'src/main/resources/org/jlinalg/omics/scdrs/.gitattributes',
    'src/main/resources/org/jlinalg/omics/scdrs/scdrs-1.0.2-LICENSE.txt',
    'src/main/resources/org/jlinalg/omics/scdrs/numpy-1.23.5-LICENSE.txt',
    'docs/literature/SCDRS_DEVELOPMENT.md',
    *['tools/literature/' + n + '.py' for n in ['intake_scdrs_reference', 'intake_scdrs_numerical_sources',
        'setup_scdrs_reference', 'build_scdrs_native', 'capture_scdrs_native_reference', 'validate_scdrs_native',
        'capture_scdrs_source_edges', 'validate_scdrs_source_edges', 'seal_scdrs_native_evidence',
        'verify_scdrs_native_evidence']],
]


def f32(x):
    return struct.unpack('>f', struct.pack('>f', x))[0]


def compare(raw, expected):
    seen = set()
    for row in csv.DictReader(io.StringIO(raw.decode('utf-8')), delimiter='\t'):
        assert set(row) == {'kind', 'row', 'column', 'value_hex'}
        kind, i, j = row['kind'], int(row['row']), int(row['column'])
        assert kind in expected and i >= 0 and j >= 0
        key = kind, i, j
        assert key not in seen
        seen.add(key)
        item = expected[kind][i]
        if isinstance(item, list):
            e = item[j]
        else:
            assert j == 0
            e = item
        value = float.fromhex(row['value_hex'])
        assert math.isfinite(value) and math.isfinite(e)
        if kind == 'scores':
            assert value == e, ('saved float32 output differs', key, value, e)
        elif kind in ['group', 'correlation'] and i > 0:
            assert f32(value) == e, ('downstream float32 quantity differs', key, value, e)
        else:
            assert abs(value - e) / max(1, abs(value), abs(e)) <= 5e-12, (key, value, e)
    count = sum(sum(len(row) for row in v) if isinstance(v[0], list) else len(v) for v in expected.values())
    assert len(seen) == count
    return count


def inspect():
    proof = json.loads((DEST / 'validation.json').read_bytes())
    assert proof['status'] == STATUS and proof['source_commits'] == VERSIONS
    assert not proof['new_catalog_credit'] and not proof['full_adapter_validated'] and not proof['whole_goal_complete']
    assert proof['scdrs_remains_missing'] and proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    assert set(proof['source_pins']) == set(SOURCE_FILES)
    for name, wanted in proof['source_pins'].items():
        safe(name)
        assert pin(ROOT / name) == wanted, name
    for name, wanted in proof['artifacts'].items():
        safe(name)
        assert pin(DEST / name) == wanted, name
    assert pin(DEST / 'controls.zip') == proof['archive']
    files = unpack(DEST / 'controls.zip', proof['evidence'])
    for name, wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/' + name]) == wanted
    validation = json.loads(files['native-validation-2/COMPLETED.json'])
    assert validation['status'] == 'PASS_NATIVE_SCDRS_COMPONENTS_THREE_HISTORICAL_SOURCES_THREE_JVMS'
    assert len(validation['summaries']) == 3 and validation['actual_score_cell_references'] == 9
    total = 0
    for summary in validation['summaries']:
        assert summary['junit_passed'] == 8 and summary['max_float32_ulps'] == 0
        assert summary['max_double_scaled_error'] < 4e-16
        platform = summary['platform']
        count = 0
        for version in VERSIONS:
            capture = json.loads(files['native-validation-2/reference-' + version + '/COMPLETED.json'])
            assert len(capture['cases']) == 7 and capture['version'] == version
            assert sum(c['actual_score_cell'] for c in capture['cases']) == 3
            for case in capture['cases']:
                stem = 'native-validation-2/' + platform + '-' + version + '-' + case['case']
                process = json.loads(files[stem + '.process.json'])
                assert process['exit_code'] == 0
                expected = json.loads(files['native-validation-2/reference-' + version + '/' + case['expected']])
                count += compare(files[stem + '.tsv'], expected)
        assert count == summary['numerical_values_compared'] == 2522943
        junit = files['native-validation-2/' + platform + '-junit.stdout']
        assert b'8 tests successful' in junit and b'0 tests failed' in junit
        assert json.loads(files['native-validation-2/' + platform + '-junit.process.json'])['exit_code'] == 0
        total += count
    assert total == proof['numeric_comparisons'] == 7568829
    edges = json.loads(files['source-edge-controls-2/COMPLETED.json'])
    assert edges['status'] == 'PASS_THREE_ORIGINAL_SCDRS_CATEGORICAL_AND_ZERO_SD_EDGE_CONTROLS'
    for version, record in edges['records'].items():
        assert version in VERSIONS and not record['source_modified'] and not record['silent_encoding_or_source_repair']
        assert not record['identical_controls']['analysis_admitted']
        assert not record['identical_controls']['trait_finite'] and not record['identical_controls']['controls_finite']
        cat = record['categorical_covariates']
        if version != '1.0.2':
            assert cat['status'] == 'ORIGINAL_CATEGORICAL_PREPROCESS_ERROR' and cat['error_type'] == 'TypeError'
        else:
            assert cat['status'] == 'ORIGINAL_CATEGORICAL_PREPROCESS_RETURNED' and 'sex_M' in cat['covariate_columns']
    failed = json.loads(files['native-validation-1/COMPLETED.json'])
    assert failed['status'] == 'FAIL2_NATIVE_SCDRS_VALIDATION' and 'multiply sequence' in failed['cause']
    example = json.loads(files['guide-example-1/COMPLETED.json'])
    assert example['status'] == 'PASS_COMPLETE_SCDRS_GUIDE_JAVA_EXAMPLE_THREE_JVMS'
    assert example['guide'] == proof['source_pins']['docs/literature/SCDRS_DEVELOPMENT.md']
    intake = json.loads(files['upstream/COMPLETED.json'])
    for version, commit in VERSIONS.items():
        profile = intake['profiles'][version]
        assert profile['commit'] == commit
        for name, wanted in profile['files'].items():
            assert byte_pin(files['upstream/' + version + '/source/' + name]) == wanted
    for tag in ['21', '25']:
        build = json.loads(files['native-build' + tag + '-1/COMPLETED.json'])
        assert build['status'] == 'PASS_NATIVE_SCDRS_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY'
        for kind, info in build['artifacts'].items():
            wanted = {k: info[k] for k in ['bytes', 'sha256']}
            assert proof['artifacts']['scdrs-' + kind + '-javac' + tag + '.jar'] == wanted
    return proof, files


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--replay', action='store_true')
    p.add_argument('--java', default='java')
    p.add_argument('--out', type=Path)
    a = p.parse_args()
    proof, files = inspect()
    if not a.replay:
        assert a.out is None
        print(STATUS + '; scDRS remains missing; no catalog credit; goal open')
        return 0
    assert a.out is not None
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SCDRS_SAVED_COMPONENT_REPLAY', new_catalog_credit=False, upstream_runtime_refitted=False)
    try:
        assert pin(DEPENDENCY)['sha256'] == DEPENDENCY_SHA
        cp = os.pathsep.join(map(str, [DEST / 'scdrs-consumer-javac21.jar', DEST / 'scdrs-api-javac21.jar', DEPENDENCY]))
        count = 0
        for version in VERSIONS:
            capture = json.loads(files['native-validation-2/reference-' + version + '/COMPLETED.json'])
            for case in capture['cases']:
                stem = version + '-' + case['case']
                source = out / (stem + '.input.json')
                source.write_bytes(files['native-validation-2/reference-' + version + '/' + case['input']])
                output = out / (stem + '.tsv')
                command = [a.java, '-cp', cp, 'org.jlinalg.cli.ScdrsReferenceProbe', str(source), str(output)]
                result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
                (out / (stem + '.stdout')).write_bytes(result.stdout)
                (out / (stem + '.stderr')).write_bytes(result.stderr)
                save(out / (stem + '.process.json'), dict(command=command, exit_code=result.returncode))
                assert result.returncode == 0
                expected = json.loads(files['native-validation-2/reference-' + version + '/' + case['expected']])
                count += compare(output.read_bytes(), expected)
                assert output.read_bytes() == files['native-validation-2/windows21-' + stem + '.tsv']
        assert count == 2522943
        state.update(status='PASS_21_SAVED_SCDRS_COMPONENT_REPLAYS', numeric_comparisons=count,
                     results_byte_identical=True, upstream_runtime_required=False)
    except FileNotFoundError as error:
        state.update(status='SKIP3_SCDRS_REPLAY_PREREQUISITE_UNAVAILABLE', cause=repr(error))
    except Exception as error:
        state['cause'] = repr(error)
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Saved numerical component replay only.')
        + '\n\nREQUIRED ACTION: Restore the stated Java or pinned dependency prerequisite for SKIP3. For FAIL2, resolve '
        'the named integrity, input or numerical cause. Preserve this attempt and retry in a NEW output; no partial result is admitted.\n',
        encoding='utf-8', newline='\n')
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable complete saved evidence checks')
    raise SystemExit(main())
