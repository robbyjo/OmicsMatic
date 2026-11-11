# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay frozen native ranks and extended GENESIS development controls using Java only."""
import argparse
import csv
import io
import json
import os
import re
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source

HOME = ROOT / 'docs/literature/genesis-extended-workflow-development'
STATUS = 'PASS_FROZEN_NATIVE_RANK_AND_EXTENDED_GENESIS_DEVELOPMENT_CONTROLS_ONLY'
PREFIX = 'build/literature-review/genesis-runtime/'
ACTION = 'SKIP saved verification if permitted Java17+ is unavailable. Supply --java with a permitted executable or leave it skipped. This helper runs no R, downloads nothing and performs no model fits. FAIL2 admits no verified result: preserve the attempt and resolve the stated source/archive/scientific cause before retrying NEW paths. Development checks grant no tool catalog credit, biological validity, historical-paper reproduction or producer authentication.'


class Unavailable(RuntimeError):
    pass


def records(raw):
    return list(csv.DictReader(io.StringIO(raw.decode('utf8')), delimiter='\t'))


def inspect(home=HOME):
    home = Path(home); proof = json.loads((home / 'VALIDATION.json').read_bytes())
    assert proof['status'] == STATUS and proof['new_catalog_credit'] is False and proof['GENESIS_tool_still_missing'] is True and proof['whole_goal_complete'] is False
    assert proof['root_command_available'] is False
    for name, value in proof['source_pins'].items():
        if name == '.gitattributes':
            verify_source(ROOT, ROOT / 'docs/literature/omics-2023-2026', name, value['sha256'])
        else:
            assert pin(ROOT / name) == value, ('Changed development source; preserve this snapshot before refreshing validation', name)
    assert pin(home / 'controls.zip') == proof['archive']
    files = unpack(home / 'controls.zip', proof)
    for name, value in proof['source_pins'].items():
        assert byte_pin(files['current-source/' + name]) == value
    for name, value in proof['artifacts'].items():
        assert pin(home / name) == value
    rank = json.loads(files[PREFIX + 'rank-native-validation-2/COMPLETED.json'])
    assert rank['status'] == 'PASS_COMPLETE_NATIVE_GENESIS_RANK_REFERENCE_AND_API_CONTRACTS'
    assert rank['cases'] == 74 and len(rank['records']) == 3 and sum(r['tests'] for r in rank['unit']) == 24
    assert all(r['complete_scores_compared'] == 252088 and r['complete_native_successes'] == 70 and r['unsupported_nonfinite_refusals'] == 4 and r['maximum_scaled_error'] <= 2e-14 for r in rank['records'])
    protocol = json.loads(files[PREFIX + 'workflow-extended-protocol-1/COMPLETED.json'])
    assert protocol['status'] == 'PASS_EXTENDED_GENESIS_SAVED_REPLAYS_AND_RESEALED_SCIENTIFIC_REFUSALS'
    assert (protocol['positive_verifications'], protocol['resealed_scientific_refusals']) == (15, 87)
    execution = json.loads(files[PREFIX + 'workflow-e2e-controls-1/COMPLETED.json'])
    assert execution['status'] == 'PASS_EIGHT_ACTUAL_WORKFLOW_EXECUTION_AND_REFUSAL_CONTRACTS'
    assert [r['exit_code'] for r in execution['records']] == [3, 0, 2, 0, 0, 0, 0, 2]
    assert not execution['records'][0]['original_analysis_executed']
    for row in execution['records']:
        assert row['result_admitted'] is (row['exit_code'] == 0)
    for case in proof['saved_bundle_paths']:
        prefix = PREFIX + case + '/'; done = json.loads(files[prefix + 'COMPLETED.json'])
        assert done['result_admitted'] is True and done['original_analysis_executed'] is True
        captured = {n.removeprefix(prefix): byte_pin(v) for n, v in files.items() if n.startswith(prefix) and n != prefix + 'COMPLETED.json'}
        assert captured == done['files'], ('Incomplete admitted bundle', case)
    # The separately built consumer can use this matching, fully pinned workflow
    # artifact for the exact same native API and unchanged JDistlib classes.
    for compiler in [21, 25]:
        with zipfile.ZipFile(home / f'genesis-workflows-javac{compiler}.jar') as full, zipfile.ZipFile(home / f'genesis-rank-api-javac{compiler}.jar') as thin:
            name = 'org/jlinalg/genetics/GenesisRankNormalisation.class'
            assert full.read(name) == thin.read(name)
            assert {n: byte_pin(full.read(n)) for n in full.namelist() if n.startswith('jdistlib/') and not n.endswith('/')} == proof['vendored_jdistlib_entries']
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof, files


def compare_rank(files, native):
    base = PREFIX + 'rank-native-validation-2/'
    cases = json.loads(files[base + 'declared-cases.json'])
    disposition_rows = records((native / 'native-dispositions.tsv').read_bytes())
    assert len(disposition_rows) == len(cases) and len({r['case'] for r in disposition_rows}) == len(cases)
    dispositions = {r['case']: r for r in disposition_rows}; compared = 0; maximum = 0.; refused = 0
    for case in cases:
        row = dispositions[case['case']]
        if case['native_finite_profile_unsupported']:
            assert row['status'] == 'NATIVE_REFUSAL' and 'Nonfinite original residual' in bytes.fromhex(row['cause_utf8_hex']).decode('utf8')
            assert not (native / case['input']).exists(); refused += 1
            continue
        assert row['status'] == 'NATIVE_SUCCESS'
        original = records(files[base + 'original-reference/' + case['input']]); actual = records((native / case['input']).read_bytes())
        assert len(original) == len(actual) == case['rows']
        for source, got in zip(original, actual, strict=True):
            assert {k: v for k, v in source.items() if k not in ['average_rank_hex', 'score_hex']} == {k: v for k, v in got.items() if k not in ['average_rank_hex', 'score_hex']}
            assert float.fromhex(source['average_rank_hex']) == float.fromhex(got['average_rank_hex'])
            x, y = float.fromhex(source['score_hex']), float.fromhex(got['score_hex'])
            error = abs(x - y) / max(1, abs(x)); assert error <= 2e-14
            maximum = max(maximum, error); compared += 1
    assert compared == 252088 and refused == 4
    return dict(cases=len(cases), complete_scores_compared=compared, unsupported_nonfinite_refusals=refused, maximum_scaled_error=maximum)


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--home', type=Path, default=HOME)
    p.add_argument('--out', type=Path, required=True); p.add_argument('--java', type=Path)
    p.add_argument('--jar-compiler', choices=['21', '25'], default='21'); a = p.parse_args(); out = a.out.resolve(); home = a.home.resolve()
    if out.exists() or out.is_relative_to(home) or home.is_relative_to(out):
        print('FAIL2: choose a NEW output directory separate from frozen evidence. ' + ACTION); return 2
    out.mkdir(parents=True); code = 2
    state = dict(status='FAIL2_GENESIS_EXTENDED_DEVELOPMENT_REPLAY', original_R_analysis_executed=False, downloads_or_installation=False, new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION)
    try:
        proof, files = inspect(home)
        java = a.java or (Path(shutil.which('java')) if shutil.which('java') else None)
        if java is None or not java.is_file():
            raise Unavailable('Java17+ is missing; supply --java with a permitted executable')

        def run(command, label, timeout):
            try:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=timeout)
            except OSError as error:
                raise Unavailable('Java cannot be launched: ' + repr(error)) from error
            (out / (label + '.stdout')).write_bytes(result.stdout); (out / (label + '.stderr')).write_bytes(result.stderr)
            save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
            return result

        version = run([str(java), '-version'], 'java-version', 30)
        match = re.search(rb'version\s+"(\d+)', version.stdout + version.stderr)
        if version.returncode or not match or int(match[1]) < 17:
            raise Unavailable('A working Java17+ executable is required; inspect retained version output')
        protected = {str(p): pin(p) for p in home.iterdir() if p.is_file()}
        controls = out / 'retained-controls'; controls.mkdir(); selected = set()
        for name, raw in files.items():
            relative = None
            if any(name.startswith(PREFIX + case + '/') for case in proof['saved_bundle_paths']):
                relative = name.removeprefix(PREFIX)
            if name.startswith(PREFIX + 'rank-native-validation-2/inputs/'):
                relative = name.removeprefix(PREFIX)
            if relative is not None:
                target = controls / relative; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw); selected.add(name)
        jar = home / ('genesis-workflows-javac' + a.jar_compiler + '.jar'); saved = []
        for index, case in enumerate(proof['saved_bundle_paths']):
            result = run([str(java), '-Xmx2g', '-jar', str(jar), '--verify', str(controls / case)], 'saved-' + str(index), 180)
            assert result.returncode == 0, (case, result.stderr[-6000:])
            saved.append(dict(case=case, exit_code=result.returncode)); print('PASS_SAVED_GENESIS', case, flush=True)
        consumer = home / ('genesis-rank-consumer-javac' + a.jar_compiler + '.jar'); native = out / 'native-ranks'
        result = run([str(java), '-Xmx512m', '-cp', os.pathsep.join(map(str, [consumer, jar])), 'org.jlinalg.genetics.GenesisRankReferenceProbe', 'batch', str(controls / 'rank-native-validation-2/inputs/manifest.tsv'), str(native)], 'native-rank', 180)
        assert result.returncode == 0, result.stderr[-6000:]
        rank = compare_rank(files, native)
        assert all(pin(Path(n)) == value for n, value in protected.items())
        assert all(pin(controls / name.removeprefix(PREFIX)) == byte_pin(files[name]) for name in selected)
        state.update(status='PASS_NATIVE_RANK_AND_EXTENDED_GENESIS_SAVED_REPLAYS_WITHOUT_R', saved_workflows=saved, native_rank=rank, proof_pin=pin(home / 'VALIDATION.json'), archive=proof['archive']); code = 0
    except Unavailable as error:
        state.update(status='SKIP3_GENESIS_EXTENDED_JAVA_UNAVAILABLE', cause=repr(error)); code = 3
    except Exception as error:
        state['cause'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Saved native contracts passed; no R execution, model fit or independent estimator refit.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', '')[:6000], flush=True)
    return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source/archive/scientific replay checks')
    raise SystemExit(main())
