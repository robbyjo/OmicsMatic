# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise network dependency skips, owned failures and rehashed semantic refusals."""
import argparse, copy, csv, io, json, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT / 'build/literature-review/cellchat-runtime'
JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--baseline', type=Path, required=True)
    a = p.parse_args()
    out, jar, baseline = a.out.resolve(), a.jar.resolve(), a.baseline.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    request = json.loads((baseline / 'source-request.json').read_bytes())
    profile = json.loads((baseline / 'runtime-profile.json').read_bytes())
    bindings = {str(jar): pin(jar), str(Path(__file__).resolve()): pin(Path(__file__).resolve())}
    records = []
    state = dict(status='FAIL2_NETWORK_ADMISSION_CONTROLS', new_catalog_credit=False, whole_goal_complete=False)
    try:
        cases = []

        def case(name, fn, expected=2):
            r = copy.deepcopy(request)
            fn(r)
            cases.append((name, r, expected))

        def changed_profile(name, fn, expected):
            r = copy.deepcopy(profile)
            fn(r)
            save(out / (name + '.json'), r)
            case(name, lambda q: q.update(runtime_profile=str(out / (name + '.json'))), expected)

        case('missing-runtime', lambda r: r.update(runtime_profile=str(out / 'absent.json')), 3)
        changed_profile('missing-python', lambda r: r.update(python=str(out / 'absent-python.exe')), 3)
        changed_profile('missing-library', lambda r: r.update(libraries=[str(out / 'absent-library')]), 3)
        changed_profile('wrong-edition', lambda r: r.update(source_label='1.0.0'), 2)
        case('unknown-field', lambda r: r['analysis'].update(unapproved=True))
        case('unapproved-engine', lambda r: r['analysis']['embedding'].update(umap_method='uwot'))
        case('duplicate-types', lambda r: r['analysis'].update(types=['functional', 'functional']))
        case('feature-capacity', lambda r: r['limits'].update(max_features=4))
        changed_profile('runtime-hash', lambda r: r['files'][r['python']].update(sha256='0'*64), 2)
        owned = out / 'owned-source'
        shutil.copytree(Path(profile['source_root']), owned)
        file = owned / 'R/utilities.R'
        file.write_bytes(file.read_bytes() + b'\n# deliberate owned-copy source fault\n')
        changed_profile('source-hash', lambda r: r.update(source_root=str(owned)), 2)
        case('owned-timeout', lambda r: r['limits'].update(wall_seconds=1))
        features = [row['feature'] for row in csv.DictReader(
            (baseline / 'results/functional/points.tsv').open(encoding='utf8'), delimiter='\t')]
        case('original-empty-subset', lambda r: r['analysis'].update(types=['functional'],
             embedding={**r['analysis']['embedding'], 'pathway_remove': features}))
        for name, r, expected in cases:
            home = out / name
            home.mkdir()
            r['out'] = str(home / 'analysis')
            save(home / 'request.json', r)
            cmd = [JAVA, '-Xmx2g', '-jar', str(jar), '--request', str(home / 'request.json')]
            with (home / 'stdout').open('xb') as so, (home / 'stderr').open('xb') as se:
                result = subprocess.run(cmd, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=600)
            row = dict(id=name, command=cmd, exit_code=result.returncode, expected_exit=expected)
            records.append(row)
            save(home / 'process.json', row)
            stderr = (home / 'stderr').read_text(encoding='utf8')
            assert result.returncode == expected, (name, result.returncode, stderr[-1800:])
            assert 'WARNINGS / REQUIRED ACTIONS:' in stderr
            target = home / 'analysis'
            if target.exists():
                d = json.loads((target / 'COMPLETED.json').read_bytes())
                assert not d['result_admitted'] and d['status'].startswith('SKIP3' if expected == 3 else 'FAIL2')
                assert (target / 'WARNINGS.md').exists() and not (target / 'results').exists()
            if name == 'owned-timeout':
                pid = int(json.loads((target / 'process-started.json').read_bytes())['pid'])
                probe = subprocess.run(['powershell.exe', '-NoProfile', '-Command',
                    f'if (Get-Process -Id {pid} -ErrorAction SilentlyContinue) {{ exit 1 }} else {{ exit 0 }}'],
                    capture_output=True, timeout=30)
                assert probe.returncode == 0
                save(home / 'owned-process-terminal.json', dict(pid=pid, terminal=True))
            if name == 'original-empty-subset':
                report = json.loads((target / 'unadmitted-worker/worker-report.json').read_bytes())
                assert report['detail'] and report['detail'] in stderr
            print(name, result.returncode, flush=True)
        tampers = out / 'tampered'
        tampers.mkdir()
        variants = ['missing-file', 'extra-file', 'similarity-before', 'transposed-input',
                    'subset-decision', 'coordinate', 'cluster-center', 'rng-census',
                    'missing-rng-warning', 'missing-reproducibility-warning', 'missing-parent-diagnostic']
        for name in variants:
            target = tampers / name
            shutil.copytree(baseline, target)
            manifest = json.loads((target / 'COMPLETED.json').read_bytes())
            if name == 'missing-file':
                (target / 'results/functional/points.tsv').unlink()
            elif name == 'extra-file':
                (target / 'extra.txt').write_text('unexpected', encoding='utf8')
            elif name.startswith('missing-'):
                f = target / 'WARNINGS.md'
                prefix = {'missing-rng-warning': 'ORIGINAL PARALLEL RNG WARNING:',
                          'missing-reproducibility-warning': 'ORIGINAL UMAP REPRODUCIBILITY WARNING:',
                          'missing-parent-diagnostic': 'PARENT ORIGINAL SOURCE WARNING:'}[name]
                f.write_text('\n'.join(line for line in f.read_text(encoding='utf8').splitlines()
                                       if not line.startswith(prefix)) + '\n', encoding='utf8')
                manifest['files']['WARNINGS.md'] = pin(f)
            elif name == 'rng-census':
                f = target / 'results/worker-report.json'
                report = json.loads(f.read_bytes())
                report['original_parallel_rng_warnings'] += 1
                save(f, report)
                manifest['files']['results/worker-report.json'] = pin(f)
            else:
                leaf, field = {'similarity-before': ('similarity-before.tsv', 'value'),
                    'transposed-input': ('umap-input.tsv', None),
                    'subset-decision': ('feature-dispositions.tsv', 'included'),
                    'coordinate': ('points.tsv', 'UMAP1'),
                    'cluster-center': ('cluster-summary.tsv', 'mean_1')}[name]
                relative = 'results/functional/' + leaf
                f = target / relative
                rows = list(csv.reader(io.StringIO(f.read_text(encoding='utf8')), delimiter='\t'))
                at = 1 if field is None else rows[0].index(field)
                rows[1][at] = ('FALSE' if rows[1][at] == 'TRUE' else 'TRUE') if name == 'subset-decision' else str(float(rows[1][at]) + 0.5)
                buffer = io.StringIO(newline='')
                csv.writer(buffer, delimiter='\t', quoting=csv.QUOTE_ALL, lineterminator='\n').writerows(rows)
                f.write_text(buffer.getvalue(), encoding='utf8', newline='\n')
                manifest['files'][relative] = pin(f)
            if name not in ['missing-file', 'extra-file']:
                save(target / 'COMPLETED.json', manifest)
            cmd = [JAVA, '-Xmx2g', '-jar', str(jar), '--verify', str(target)]
            r = subprocess.run(cmd, capture_output=True, timeout=300)
            (tampers / (name + '.stdout')).write_bytes(r.stdout)
            (tampers / (name + '.stderr')).write_bytes(r.stderr)
            records.append(dict(id='tamper-' + name, command=cmd, exit_code=r.returncode, expected_exit=2))
            assert r.returncode == 2, (name, r.stdout, r.stderr)
            print('tamper-' + name, r.returncode, flush=True)
        assert all(pin(Path(n)) == v for n, v in bindings.items())
        state.update(status='PASS_NETWORK_SKIP_FAILURE_AND_REHASHED_REFUSALS',
            analysis_dispositions=len(cases), offline_tamper_refusals=len(variants),
            owned_timeout_terminal=True, source_pins=bindings, records=records)
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('error', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable admission regressions')
    raise SystemExit(main())
