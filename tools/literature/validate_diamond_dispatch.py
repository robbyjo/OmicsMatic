# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate routed complete DIAMOND bundles across JVMs and unchanged predecessor routes."""
import argparse, collections, concurrent.futures, copy, json, shutil, subprocess, traceback
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    value = str(path.resolve()).replace('\\', '/')
    assert len(value) > 3 and value[1:3] == ':/'
    return '/mnt/'+value[0].lower()+value[2:]


def populations():
    folders = ['adapter-source2.0.0-2', 'adapter-source2.0.0-3',
        'adapter-source2.0.15-1', 'adapter-source2.1.8-1']
    records, cases = [], set()
    for folder in folders:
        report = json.loads((BASE/folder/'COMPLETED.json').read_bytes())
        # The first batch ended on a later, independently owned case timeout.
        # Earlier per-case complete comparisons remain individually validated.
        if folder == 'adapter-source2.0.0-2':
            assert report['status'] == 'FAIL2_DIAMOND_ADAPTER_SOURCE_COMPARISON' and len(report['records']) == 20
        else:
            assert report['status'] == 'PASS_COMPLETE_ORIGINAL_DIAMOND_ADAPTER_COMPARISONS'
        for row in report['records']:
            key = row['version'], row['case']
            assert key not in cases
            cases.add(key)
            home = BASE/folder/row['version']/row['case']
            assert json.loads((home/'VALIDATION.json').read_bytes()) == row
            done = json.loads((home/'analysis/COMPLETED.json').read_bytes())
            assert done['result_admitted'] is (row['adapter_exit'] == 0)
            records.append(dict(**row, home=home, bundle=home/'analysis'))
    original = json.loads((BASE/'reference-controls-2/COMPLETED.json').read_bytes())
    assert cases == {(r['version'], r['id']) for r in original['records']} and len(records) == 78
    assert sum(r['adapter_exit'] == 0 for r in records) == 72
    return records


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    shutil.copy2(Path(__file__).resolve(), out/'validator-source.py')
    state = dict(status='FAIL2_DIAMOND_ROOT_VERIFICATION', implementation_credit=False, whole_goal_complete=False)
    try:
        records = populations()
        canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
        before = pin(canonical)
        roots = {}
        for co in [21, 25]:
            build = json.loads((BASE/f'root-build{co}-1/COMPLETED.json').read_bytes())
            assert build['status'] == 'PASS_DIAMOND_ROOT_PREDECESSOR_AND_SCIENTIFIC_BYTES_UNCHANGED'
            assert all(pin(ROOT/n) == v for n, v in build['source_pins'].items())
            roots[co] = ROOT/build['artifact']['path']
            assert pin(roots[co]) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        platforms = [dict(id='windows21', compiler=21, prefix=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe']),
            dict(id='windows25', compiler=25, prefix=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']),
            dict(id='linux21', compiler=21, prefix=['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java'])]

        def check(platform):
            home = out/platform['id']
            home.mkdir()
            convert = linux if platform['id'] == 'linux21' else lambda p: str(p.resolve())
            cp = (':' if platform['id'] == 'linux21' else ';').join(convert(p) for p in [roots[platform['compiler']], canonical])
            prefix = platform['prefix']+['-Xmx2g', '-cp', cp, 'org.jlinalg.cli.JLinAlgCli']
            invocations = []

            def run(name, args, expected=0):
                folder = home/name
                folder.mkdir()
                command = prefix+args
                result = subprocess.run(command, capture_output=True, timeout=180)
                (folder/'stdout').write_bytes(result.stdout)
                (folder/'stderr').write_bytes(result.stderr)
                record = dict(case=name, command=command, exit_code=result.returncode, expected_exit=expected)
                save(folder/'process.json', record)
                invocations.append(record)
                assert result.returncode == expected, (platform['id'], name, result.returncode, result.stderr[-1600:])
                return result

            assert b'diamond-tools --help' in run('root-help', ['--help']).stdout
            assert b'non-frameshift' in run('diamond-help', ['diamond-tools', '--help']).stdout
            for row in records:
                name = row['version']+'-'+row['case']
                run('verify-'+name, ['diamond-tools', '--verify', convert(row['bundle'])], 0 if row['adapter_exit'] == 0 else 2)
            sample = next(r for r in records if r['adapter_exit'] == 0)
            request = json.loads((sample['home']/'request.json').read_bytes())
            request.update(out=convert(home/'skipped-analysis'), runtime_profile=convert(home/'absent-runtime-profile.json'))
            save(home/'skip-request.json', request)
            skipped = run('dependency-skip', ['diamond-tools', '--request', convert(home/'skip-request.json')], 3)
            assert b'SKIP3' in skipped.stderr and b'REQUIRED ACTIONS' in skipped.stderr
            previous = ROOT/'build/literature-review/cellchat-runtime'
            cases = [('inference', 'inference-controls-1.0.0-javac21-3/analysis'),
                ('legacy', 'legacy-typed-controls-7/analysis'), ('network', 'network-controls-javac21-parallel-2/analysis'),
                ('strength', 'strength-controls-3x4-javac21-2/analysis'), ('workflow', 'typed-controls-8/analysis')]
            for family, case in cases:
                run('predecessor-'+family, ['cellchat-tools', family, '--verify', convert(previous/case)])
            actual = 0
            if platform['id'] == 'linux21':
                for version, case in [('2.0.0', 'protein-sensitive-top50'), ('2.0.15', 'translated-both-strands'), ('2.1.8', 'long-exact-rounded-Evalue')]:
                    source = next(r for r in records if (r['version'], r['case']) == (version, case))
                    request = json.loads((source['home']/'request.json').read_bytes())
                    target = home/('actual-'+version)
                    request['out'] = convert(target)
                    path = home/('actual-'+version+'-request.json')
                    save(path, request)
                    run('actual-run-'+version, ['diamond-tools', '--request', convert(path)])
                    run('actual-verify-'+version, ['diamond-tools', '--verify', convert(target)])
                    expected = (BASE/'reference-controls-2'/version/case/'hits.tsv').read_text().splitlines()
                    assert collections.Counter((target/'results/hits.tsv').read_text().splitlines()) == collections.Counter(expected)
                    actual += 1
            result = dict(status='PASS_DIAMOND_ROOT_COMPLETE_BUNDLES_AND_ORIGINAL_DISPOSITIONS',
                platform=platform['id'], compiler=platform['compiler'], complete_bundle_checks=72,
                failed_bundle_refusals=6, dependency_skips=1, predecessor_complete_checks=5,
                actual_original_alignments=actual, records=invocations)
            save(home/'COMPLETED.json', result)
            print(platform['id'], 'PASS72 complete bundles,6 failed refusals,5 predecessor checks', flush=True)
            return {k: v for k, v in result.items() if k != 'records'}

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            runs = list(pool.map(check, platforms))
        assert pin(canonical) == before
        state.update(status='PASS216_DIAMOND_ROOT_COMPLETE_CHECKS18_REFUSALS3_SKIPS15_PREDECESSORS3_ALIGNMENTS',
            runs=runs, original_source_cases=78, successful_cases=72, preserved_original_failure_cases=6,
            complete_root_bundle_checks=216, failed_root_bundle_refusals=18, actual_root_dependency_skips=3,
            predecessor_complete_checks=15, actual_root_original_alignments=3,
            root_artifacts={p.relative_to(ROOT).as_posix(): pin(p) for p in roots.values()},
            source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java']},
            canonical_executable_unchanged=before, science_bytes_changed=False,
            scope='Complete saved source/transport verification and explicit original alignment controls; no independent alignment refit, producer authentication or historical paper reproduction.')
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:600], flush=True)
    return 0 if state['status'].startswith('PASS') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete routed bundle checks')
    raise SystemExit(main())
