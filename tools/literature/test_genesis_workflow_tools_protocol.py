# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay extended GENESIS development bundles and reject resealed scientific changes without running R."""
import argparse
import csv
import json
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import DISTRO, linux

BASE = ROOT / 'build/literature-review/genesis-runtime'
CASES = ['true-binomial-mixed-null', 'binomial-SPA', 'binomial-BinomiRare', 'gaussian-GxE', 'gaussian-two-stage-association']
ACTION = 'SKIP saved replay when the selected Java runtimes are unavailable or forbidden. Supply permitted Java17+ runtimes and retry NEW paths. FAIL2: preserve controls and logs and resolve the named scientific/integrity cause. This helper runs no R, installs nothing and grants no catalog or whole-goal credit.'


class Unavailable(RuntimeError):
    pass


def rows(file):
    with file.open(encoding='utf8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        return reader.fieldnames, list(reader)


def change(file, column, value, row=0):
    header, data = rows(file)
    data[row][column] = value(data[row][column]) if callable(value) else value
    with file.open('w', encoding='utf8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=header, delimiter='\t', lineterminator='\n')
        writer.writeheader(); writer.writerows(data)


def frame_change(home, stem, field, value, source_row=1):
    worker = home / 'worker'
    _, schema = rows(worker / (stem + '.schema.tsv'))
    column = next(row['column'] for row in schema if bytes.fromhex(row['name_utf8_hex']).decode('utf8') == field)
    path = worker / (stem + '.cells.tsv'); _, cells = rows(path)
    position = next(i for i, row in enumerate(cells) if row['row'] == str(source_row) and row['column'] == column)
    change(path, 'value_hex', value, row=position)


def inventory(home):
    return {p.relative_to(home).as_posix(): pin(p) for p in sorted(home.rglob('*')) if p.is_file() and p != home / 'COMPLETED.json'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--controls', type=Path, required=True)
    p.add_argument('--jars', type=Path, nargs=2, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args(); out = a.out.resolve(); controls = a.controls.resolve(); jars = [x.resolve() for x in a.jars]
    assert not out.exists() and out.is_relative_to(BASE); out.mkdir(parents=True)
    state = dict(status='FAIL2_EXTENDED_GENESIS_SAVED_PROTOCOL', original_analysis_executed=False, new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION)
    code = 2
    try:
        protected = {str(f): pin(f) for f in [ROOT / 'build/cli/omicsmatic-0.3.7.jar', *jars, Path(__file__)]}
        for name in CASES:
            home = controls / name
            assert json.loads((home / 'COMPLETED.json').read_bytes())['result_admitted'] is True
            protected.update({str(f): pin(f) for f in home.rglob('*') if f.is_file()})
        hosts = [
            ('windows21', ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe', '-Xmx2g', '-jar', str(jars[0])]),
            ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe', '-Xmx2g', '-jar', str(jars[1])]),
            ('linux21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx2g', '-jar', linux(jars[0])])]
        records = []

        def verify(home, label, expected):
            for host, command in hosts:
                arguments = command + ['--verify', linux(home) if host.startswith('linux') else str(home)]
                try:
                    run = subprocess.run(arguments, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
                except OSError as error:
                    raise Unavailable('Selected Java cannot be launched: ' + repr(error)) from error
                stem = label + '-' + host
                (out / (stem + '.stdout')).write_bytes(run.stdout); (out / (stem + '.stderr')).write_bytes(run.stderr)
                record = dict(label=label, host=host, expected_exit=expected, exit_code=run.returncode, command=arguments)
                save(out / (stem + '.json'), record); records.append(record)
                assert run.returncode == expected, (stem, run.stdout[-2500:], run.stderr[-5000:])
                if expected == 2:
                    assert b'Complete saved bundle byte inventory differs' not in run.stderr, 'Resealed mutation must exercise a stronger check than the byte inventory'
            print('PASS', label, flush=True)

        for name in CASES:
            verify(controls / name, 'saved-' + name, 0)
        bump = lambda value: (float.fromhex(value) + .125).hex()
        mutants = [
            ('two-stage-residual', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'residual_hex', bump)),
            ('two-stage-average-rank', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'average_rank_hex', '0x1p+0')),
            ('two-stage-unit-quantile', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'unit_score_hex', bump)),
            ('two-stage-variance', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'variance_hex', '0x1p+2')),
            ('two-stage-scale', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'scale_hex', '0x1p+2')),
            ('two-stage-outcome', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'normalised_outcome_hex', bump)),
            ('two-stage-sample-alias', CASES[4], lambda h: change(h / 'worker/original-normalisation.tsv', 'sample_alias', '1')),
            ('two-stage-stage1-design', CASES[4], lambda h: change(h / 'worker/original-stage1-design.values.tsv', 'value_hex', '0x1p+2')),
            ('two-stage-stage1-raw-object-missing', CASES[4], lambda h: (h / 'worker/original-stage1-null-model.rds').unlink()),
            ('association-sample-index', CASES[1], lambda h: change(h / 'worker/original-association-sample-selection.tsv', 'input_sample_row', '1')),
            ('SPA-probability', CASES[1], lambda h: frame_change(h, 'original-association', 'SPA.pval', '0x1p+1')),
            ('SPA-convergence', CASES[1], lambda h: frame_change(h, 'original-association', 'SPA.converged', '0x1p+1')),
            ('SPA-ordinary-score-control', CASES[1], lambda h: frame_change(h, 'original-score-control', 'Score.Stat', bump)),
            ('SPA-optional-version', CASES[1], lambda h: change(h / 'worker/original-optional-package-versions.tsv', 'version', '0.0.0')),
            ('BR-conditional-probability', CASES[2], lambda h: change(h / 'worker/original-association-probabilities.tsv', 'probability_hex', bump)),
            ('BR-source-PMF', CASES[2], lambda h: change(h / 'worker/original-poibin-control-pmfs.tsv', 'probability_hex', '0x1p-1')),
            ('BR-source-PMF-support', CASES[2], lambda h: change(h / 'worker/original-poibin-control-pmfs.tsv', 'successes', '999')),
            ('BR-carrier-count', CASES[2], lambda h: frame_change(h, 'original-association', 'n.carrier', '0x1p+10')),
            ('BR-case-carrier-count', CASES[2], lambda h: frame_change(h, 'original-association', 'n.D.carrier', '0x1p+10')),
            ('BR-carrier-expectation', CASES[2], lambda h: frame_change(h, 'original-association', 'expected.n.D.carrier', '0x1p+10')),
            ('BR-mid-P', CASES[2], lambda h: frame_change(h, 'original-association', 'mid.pval', '0x1p+1')),
            ('GxE-joint-statistic', CASES[3], lambda h: frame_change(h, 'original-association', 'Joint.Stat', bump)),
            ('GxE-interaction-estimate', CASES[3], lambda h: frame_change(h, 'original-association', 'Est.G:PRS', bump)),
            ('GxE-probability', CASES[3], lambda h: frame_change(h, 'original-association', 'GxE.pval', '0x1p+1')),
            ('effective-test', CASES[3], lambda h: change(h / 'worker/original-association-status.tsv', 'effective_test', 'Score')),
            ('mixed-engine', CASES[0], lambda h: change(h / 'worker/original-model-status.tsv', 'effective_mixed', 'FALSE')),
            ('model-not-converged', CASES[0], lambda h: change(h / 'worker/original-model-status.tsv', 'converged', 'FALSE')),
            ('scientific-version', CASES[0], lambda h: change(h / 'worker/original-package-versions.tsv', 'version', '2.16.1')),
            ('prominent-warnings', CASES[1], lambda h: (h / 'WARNINGS.md').write_text('Completed.\n', encoding='utf8')),
        ]
        mutations = out / 'resealed-scientific-mutations'; mutations.mkdir()
        for name, source, mutate in mutants:
            home = mutations / name; shutil.copytree(controls / source, home); mutate(home)
            done = json.loads((home / 'COMPLETED.json').read_bytes()); done['files'] = inventory(home); save(home / 'COMPLETED.json', done)
            before = {str(f): pin(f) for f in home.rglob('*') if f.is_file()}
            verify(home, 'reject-' + name, 2)
            assert all(pin(Path(n)) == v for n, v in before.items()), 'Replay changed rejected evidence'
        assert all(pin(Path(n)) == v for n, v in protected.items()), 'Replay changed protected evidence'
        state.update(status='PASS_EXTENDED_GENESIS_SAVED_REPLAYS_AND_RESEALED_SCIENTIFIC_REFUSALS', records=records,
                     positive_verifications=len(CASES) * len(hosts), resealed_scientific_refusals=len(mutants) * len(hosts), protected=protected)
        code = 0
    except Unavailable as error:
        state.update(status='SKIP3_EXTENDED_GENESIS_REPLAY_UNAVAILABLE', cause=repr(error)); code = 3
    except Exception as error:
        state['cause'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Saved native contracts only; no original fit or independent estimator refit.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', '')[:7000], flush=True)
    return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source/scientific preservation checks')
    raise SystemExit(main())
