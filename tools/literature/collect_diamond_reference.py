# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged original DIAMOND binaries on complete synthetic source-option controls."""
import argparse, collections, hashlib, json, os, shutil, subprocess, traceback
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    digest, count = hashlib.sha256(), 0
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1048576), b''):
            digest.update(block)
            count += len(block)
    return dict(bytes=count, sha256=digest.hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--controls', type=Path, required=True)
    a = p.parse_args()
    out, controls = a.out.resolve(), a.controls.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/diamond-runtime')
    out.mkdir(parents=True)
    shutil.copy2(Path(__file__).resolve(), out/'collector-source.py')
    shutil.copy2(ROOT/'tools/literature/diamond-source-lock.json', out/'source-lock-used.json')
    lock = json.loads((ROOT/'tools/literature/diamond-source-lock.json').read_bytes())
    fields = lock['output_fields']
    cases = json.loads((controls/'cases.json').read_bytes())
    before = {p.name: pin(p) for p in controls.iterdir() if p.is_file()}
    shutil.copytree(controls, out/'inputs')
    environment = {k: v for k, v in os.environ.items() if not k.startswith('LD_')}
    environment.update(PATH='/usr/bin:/bin', LANG='C', LC_ALL='C')
    records, source_pins, defaults = [], {}, []
    state = dict(status='FAIL2_ORIGINAL_DIAMOND_REFERENCE_CONTROLS', analysis_admitted=False, implementation_credit=False)
    try:
        for version in ['2.0.0', '2.0.15', '2.1.8']:
            exe = ROOT/'build/literature-review/diamond-runtime/source-intake-1'/version/'bin/diamond'
            assert pin(exe) == lock['versions'][version]['binary']
            source_pins[str(exe)] = pin(exe)
            home = out/version
            home.mkdir()

            def run(folder, cmd):
                folder.mkdir(parents=True)
                with (folder/'stdout').open('xb') as so, (folder/'stderr').open('xb') as se:
                    result = subprocess.run(cmd, cwd=folder, env=environment, stdin=subprocess.DEVNULL,
                                            stdout=so, stderr=se, timeout=180)
                save(folder/'process.json', dict(command=cmd, exit_code=result.returncode))
                return result.returncode

            databases = {}
            for name in sorted({c['reference'] for c in cases if version in c['versions']}):
                folder = home/('database-'+name)
                base = folder/'reference'
                cmd = [str(exe), 'makedb', '--in', str(out/'inputs'/name), '--db', str(base), '--threads', '1']
                assert run(folder, cmd) == 0
                database = base.with_suffix('.dmnd')
                assert database.exists()
                databases[name] = database
                assert run(folder/'dbinfo', [str(exe), 'dbinfo', '--db', str(database)]) == 0
            for case in cases:
                if version not in case['versions']:
                    continue
                folder = home/case['id']
                folder.mkdir()
                (folder/'tmp').mkdir()
                settings = case['settings']
                command = [str(exe), settings['mode'], '--db', str(databases[case['reference']]),
                    '--query', *[str(out/'inputs'/n) for n in case['queries']], '--out', str(folder/'hits.tsv'),
                    '--outfmt', '6', *fields, '--unal', '0', '--tmpdir', str(folder/'tmp')]
                for field, option in [('threads', 'threads'), ('evalue', 'evalue'), ('min_bitscore', 'min-score'),
                    ('max_target_seqs', 'max-target-seqs'), ('top_percent', 'top'), ('max_hsps', 'max-hsps'),
                    ('min_identity', 'id'), ('query_cover', 'query-cover'), ('subject_cover', 'subject-cover'),
                    ('matrix', 'matrix'), ('gap_open', 'gapopen'), ('gap_extend', 'gapextend'),
                    ('comp_based_stats', 'comp-based-stats'), ('masking', 'masking'), ('query_gencode', 'query-gencode'),
                    ('block_size', 'block-size'), ('index_chunks', 'index-chunks')]:
                    if settings[field] is not None:
                        command.extend(['--'+option, str(settings[field])])
                if settings['sensitivity'] != 'default':
                    command.append('--'+settings['sensitivity'])
                if settings['algorithm'] != 'auto':
                    command.extend(['--algo', settings['algorithm']])
                if settings['no_self_hits']:
                    command.append('--no-self-hits')
                target = folder/'execution'
                code = run(target, command)
                raw = (folder/'hits.tsv').read_bytes() if (folder/'hits.tsv').exists() else b''
                rows = [line.split('\t') for line in raw.decode('utf8').splitlines()]
                if code == 0:
                    assert all(len(row) == len(fields) for row in rows)
                records.append(dict(version=version, id=case['id'], exit_code=code, rows=len(rows),
                    output=pin(folder/'hits.tsv') if (folder/'hits.tsv').exists() else None))
                print(version, case['id'], code, len(rows), flush=True)
                if code == 0 and case['id'] in ['protein-sensitive-top50', 'translated-both-strands']:
                    # The extra audit fields must not replace the program's ordinary12-field result population.
                    ordinary = folder/'ordinary'
                    basic = command[:]
                    start = basic.index('--outfmt')
                    finish = basic.index('--unal')
                    basic[start:finish] = ['--outfmt', '6']
                    basic[basic.index('--out')+1] = str(ordinary/'hits.tsv')
                    assert run(ordinary, basic) == 0
                    actual = (ordinary/'hits.tsv').read_text().splitlines()
                    expected = ['\t'.join(row[:12]) for row in rows]
                    assert collections.Counter(actual) == collections.Counter(expected)
                    defaults.append(dict(version=version, id=case['id'], rows=len(rows),
                                         order_identical=actual == expected, full_population_equal=True))
        assert {p.name: pin(p) for p in controls.iterdir() if p.is_file()} == before
        assert all(pin(Path(n)) == v for n, v in source_pins.items())
        state.update(status='PASS_COMPLETE_ORIGINAL_DIAMOND_REFERENCE_DISPOSITIONS', records=records,
            successful_cases=sum(r['exit_code'] == 0 for r in records), original_failure_cases=sum(r['exit_code'] != 0 for r in records),
            ordinary_output_population_checks=defaults, input_pins=before, binary_pins=source_pins,
            helper_pin=pin(Path(__file__).resolve()), historical_paper_reproduced=False, whole_goal_complete=False)
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:700], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-output checks')
    raise SystemExit(main())
