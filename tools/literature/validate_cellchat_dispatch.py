# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise routed complete bundles, dependency skips and unchanged predecessor flows."""
import argparse, concurrent.futures, copy, json, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_cistarget_recovery import linux, DISTRO
BASE = ROOT / 'build/literature-review/cellchat-runtime'
CASES = [
    ('inference', 'inference-controls-1.0.0-javac21-3/analysis'),
    ('inference', 'inference-controls-2.1.2-javac21-2/analysis'),
    ('legacy', 'legacy-typed-controls-7/analysis'),
    ('network', 'network-controls-javac21-parallel-2/analysis'),
    ('network', 'network-variant-fixed-k-1/analysis'),
    ('strength', 'strength-controls-3x4-javac21-2/analysis'),
    ('strength', 'strength-edge-asymptotic-zero-1/analysis'),
    ('workflow', 'typed-controls-8/analysis')]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_CELLCHAT_DISPATCH_CHECKS', new_catalog_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
        before = pin(canonical)
        artifacts = {}
        for co in [21, 25]:
            r = json.loads((BASE/f'catalog-root-build{co}-2/COMPLETED.json').read_bytes())
            assert r['status'] == 'PASS_CELLCHAT_ROOT_ALL_PREDECESSOR_AND_SCIENTIFIC_BYTES_UNCHANGED'
            assert all(pin(ROOT/n) == v for n, v in r['source_pins'].items())
            jar = ROOT/r['artifact']['path']
            assert pin(jar) == {k: r['artifact'][k] for k in ['bytes', 'sha256']}
            artifacts[co] = jar
        platforms = {
            'windows21': ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25': ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],
            'linux21': ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java']}

        def check(item):
            co, platform = item
            home = out/(f'javac{co}-'+platform)
            home.mkdir()
            convert = linux if platform == 'linux21' else lambda p: str(p.resolve())
            cp = (':' if platform == 'linux21' else ';').join(convert(p) for p in [artifacts[co], canonical])
            prefix = [*platforms[platform], '-Xmx2g', '-cp', cp, 'org.jlinalg.cli.JLinAlgCli']
            records = []

            def run(name, args, wanted=0):
                logs = home/name
                logs.mkdir()
                cmd = prefix+args
                result = subprocess.run(cmd, capture_output=True, timeout=600)
                (logs/'stdout').write_bytes(result.stdout)
                (logs/'stderr').write_bytes(result.stderr)
                row = dict(id=name, command=cmd, exit_code=result.returncode, expected_exit=wanted)
                save(logs/'process.json', row)
                records.append(row)
                assert result.returncode == wanted, (name, result.returncode, result.stderr[-2500:])
                return result

            assert b'cellchat-tools --help' in run('root-help', ['--help']).stdout
            assert b'Original 1.1.3' in run('cellchat-help', ['cellchat-tools', '--help']).stdout
            assert b'Required action:' in run('unknown-family', ['cellchat-tools', 'invented'], 2).stderr
            for family in ['inference', 'legacy', 'network', 'strength', 'workflow']:
                assert run('help-'+family, ['cellchat-tools', family, '--help']).stdout
                assert b'NO' in run('invalid-'+family, ['cellchat-tools', family, '--invented'], 2).stderr
                source = next(case for f, case in CASES if f == family)
                request = copy.deepcopy(json.loads((BASE/source/'source-request.json').read_bytes()))
                request['out'] = convert(home/('skipped-'+family))
                request['runtime_profile'] = convert(home/'absent-profile.json')
                file = home/('skip-'+family+'.json')
                save(file, request)
                skipped = run('skip-'+family, ['cellchat-tools', family, '--request', convert(file)], 3)
                assert b'SKIP3' in skipped.stderr and b'WARNINGS' in skipped.stderr
            for i, (family, case) in enumerate(CASES, 1):
                run(f'complete-{i}-{family}', ['cellchat-tools', family, '--verify', convert(BASE/case)])
            for family in ['cistarget-tools', 'mrmega-tools', 'deseq2-tools', 'signac-tools']:
                assert run('predecessor-help-'+family, [family, '--help']).stdout
            # A complete deterministic predecessor flow proves the shared root still forwards paths/settings.
            cis = ROOT/'build/literature-review/cistarget-runtime'
            case = json.loads((cis/'workflow-controls-1/cases.json').read_bytes())[0]
            inputs, native = cis/'workflow-controls-1'/case['id'], home/'predecessor-native'
            args = ['cistarget-tools']
            for key in ['settings', 'rankings', 'signature', 'annotations']:
                args.extend(['--'+key, convert(inputs/(key+('.json' if key == 'settings' else '.tsv')))])
            run('predecessor-full-cistarget', args+['--out', convert(native)])
            previous = cis/'workflow-validation-4'/(f'javac{co}-'+platform)/case['id']
            names = {p.name for p in previous.iterdir() if p.is_file()}
            assert names == {p.name for p in native.iterdir() if p.is_file()}
            assert all((native/n).read_bytes() == (previous/n).read_bytes() for n in names)
            save(home/'COMPLETED.json', dict(status='PASS_CELLCHAT_ROUTE_FLOWS_AND_PREDECESSOR',
                 compiler=co, platform=platform, complete_bundles=8, dependency_skips=5,
                 predecessor_full_flows=1, records=records))
            print('PASS', co, platform, '8 complete bundles,5 skips,predecessor flow', flush=True)
            return dict(compiler=co, platform=platform, complete_bundles=8, dependency_skips=5,
                        predecessor_full_flows=1, invocations=len(records))

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            runs = list(pool.map(check, [(co, platform) for co in artifacts for platform in platforms]))
        assert pin(canonical) == before
        state.update(status='PASS48_COMPLETE_CELLCHAT_ROOT_BUNDLES30_SKIPS6_PREDECESSOR_FLOWS', runs=runs,
            complete_root_bundle_checks=48, dependency_skip_checks=30, predecessor_full_flows=6,
            scientific_entries_changed=False, root_artifacts={p.relative_to(ROOT).as_posix(): pin(p) for p in artifacts.values()},
            canonical_executable_unchanged=before,
            source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(),
                ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java', ROOT/'src/main/java/org/jlinalg/cli/CellChatToolsCli.java']})
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:800], flush=True)
    return 0 if state['status'].startswith('PASS48_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable dispatch controls')
    raise SystemExit(main())
