# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute all literal GENESIS examples through the root command, then replay admitted bundles on three JVMs."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import DISTRO, linux

ACTION = 'Missing permitted exact runtime or Java dependencies require SKIP3 and skipped dependent analysis. FAIL2: preserve the complete example attempt and correct the named input/source/process/integrity cause before retrying NEW paths. Known-error recipes must never admit a result. Read every WARNINGS.md and require exit0/result_admitted=true plus successful --verify for a usable result.'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ['root21', 'root25', 'examples', 'out']:
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--reuse-completed', type=Path, help='Reuse all thirteen fully completed original executions from the retained failed-path-assertion attempt; rerun saved verification without R')
    a = p.parse_args(); out = a.out.resolve(); assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    state = dict(status='FAIL2_GENESIS_ROOT_EXAMPLES', new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION); code = 2; records = []
    try:
        canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'; protected = {str(f): pin(f) for f in [canonical, Path(__file__)]}; artifacts = {}
        for compiler, folder in [(21, a.root21), (25, a.root25)]:
            build = json.loads((folder / 'COMPLETED.json').read_bytes()); assert build['status'] == 'PASS_GENESIS_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED'
            artifact = ROOT / build['artifact']['path']; assert pin(artifact) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}; artifacts[compiler] = artifact
            protected.update({str(ROOT / name): value for name, value in build['source_pins'].items()}); protected[str(artifact)] = pin(artifact)
        examples = a.examples.resolve(); prepared = json.loads((examples / 'COMPLETED.json').read_bytes())
        assert prepared['status'] == 'PASS_PREPARED13_LITERAL_GENESIS_RECIPES_AND11_SYNTHETIC_INPUTS_ONLY'
        for folder in ['inputs', 'requests']:
            protected.update({str(f): pin(f) for f in (examples / folder).iterdir() if f.is_file()})
        protected[str(examples / 'runtime-profile.json')] = pin(examples / 'runtime-profile.json')
        reuse = None
        if a.reuse_completed:
            reuse = json.loads((a.reuse_completed / 'COMPLETED.json').read_bytes())
            assert reuse['status'] == 'FAIL2_GENESIS_ROOT_EXAMPLES' and reuse['cause'] == "AssertionError('The declared original PCA result must be retained before native refusal')"
            assert len([r for r in reuse['records'] if r['label'].startswith('execute-')]) == 13
            protected[str(a.reuse_completed / 'COMPLETED.json')] = pin(a.reuse_completed / 'COMPLETED.json')
        hosts = [(21, 'windows'), (25, 'windows'), (21, 'linux')]

        def command(compiler, host, arguments):
            artifact = artifacts[compiler]
            if host == 'linux':
                return ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx2g', '-cp', linux(artifact) + ':' + linux(canonical), 'org.jlinalg.cli.JLinAlgCli', *arguments]
            java = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if compiler == 21 else 'Common Files/Oracle/Java/javapath/java.exe')
            return [str(java), '-Xmx2g', '-cp', str(artifact) + ';' + str(canonical), 'org.jlinalg.cli.JLinAlgCli', *arguments]

        def run(label, arguments, expected=0, compiler=21, host='windows', timeout=480):
            cmd = command(compiler, host, arguments); process = subprocess.run(cmd, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=timeout)
            (out / (label + '.stdout')).write_bytes(process.stdout); (out / (label + '.stderr')).write_bytes(process.stderr)
            record = dict(label=label, command=cmd, expected_exit=expected, exit_code=process.returncode); records.append(record); save(out / (label + '.process.json'), record); save(out / 'progress.json', records)
            assert process.returncode == expected, (label, process.returncode, process.stdout[-3000:], process.stderr[-6000:])
            print('PASS', label, flush=True); return process

        for compiler, host in hosts:
            for tool in ['genesis-tools', 'snprelate-tools', 'diamond-tools']:
                result = run(f'help-{tool}-{compiler}-{host}', [tool, '--help'], compiler=compiler, host=host)
                assert tool.encode() in result.stdout
            result = run(f'help-root-{compiler}-{host}', ['--help'], compiler=compiler, host=host); assert b'genesis-tools --help' in result.stdout
        admitted = []
        for case in prepared['cases']:
            name = case['case']; request = examples / case['request']; assert pin(request) == case['request_pin']
            if reuse is None:
                run('execute-' + name, ['genesis-tools', '--request', str(request)], case['expected_exit'])
            else:
                label = 'execute-' + name; record = next(r for r in reuse['records'] if r['label'] == label)
                assert record['command'] == command(21, 'windows', ['genesis-tools', '--request', str(request)])
                assert record['expected_exit'] == record['exit_code'] == case['expected_exit']
                for suffix in ['.stdout', '.stderr', '.process.json']:
                    raw = (a.reuse_completed / (label + suffix)).read_bytes(); (out / (label + suffix)).write_bytes(raw)
                records.append(record); print('REUSED_COMPLETED_ORIGINAL_EXECUTION', name, flush=True)
            bundle = examples / 'results' / name; done = json.loads((bundle / 'COMPLETED.json').read_bytes())
            assert done['original_analysis_executed'] is True and done['result_admitted'] is (case['expected_exit'] == 0)
            assert 'REQUIRED ACTION' in (bundle / 'WARNINGS.md').read_text(encoding='utf8')
            if case['expected_exit'] == 0:
                admitted.append(name)
                for compiler, host in hosts:
                    path = linux(bundle) if host == 'linux' else str(bundle)
                    run(f'verify-{name}-{compiler}-{host}', ['genesis-tools', '--verify', path], compiler=compiler, host=host, timeout=180)
            else:
                if name == 'known-cmp-refusal':
                    assert 'n.carrier' in done['cause']
                else:
                    assert (bundle / 'unadmitted-worker/original-result.rds').is_file(), 'Malformed original PCA object must be retained in the quarantined failed-worker directory'
                    assert 'is.matrix(result$vectors) is not TRUE' in (bundle / 'unadmitted-worker/original-error.txt').read_text(encoding='utf8')
                assert {p.relative_to(bundle).as_posix(): pin(p) for p in sorted(bundle.rglob('*')) if p.is_file() and p != bundle / 'COMPLETED.json'} == done['files']
        # A missing profile is a skipped original analysis through the root route.
        source = json.loads((examples / prepared['cases'][0]['request']).read_bytes())
        source['inputs'] = json.loads((examples / prepared['cases'][0]['request']).read_bytes())['inputs']
        # Resolve recipe-relative source inputs explicitly before moving this
        # negative request; only the intended prerequisite is changed.
        def absolute(value):
            if isinstance(value, dict):
                return {key: absolute(v) for key, v in value.items()}
            if isinstance(value, list):
                return [absolute(v) for v in value]
            if isinstance(value, str) and value.startswith('../inputs/'):
                return str((examples / 'requests' / value).resolve())
            return value
        source['inputs'] = absolute(source['inputs']); source['runtime_profile'] = str(out / 'absent-profile.json'); source['out'] = str(out / 'runtime-skip')
        request = out / 'runtime-skip.request.json'; save(request, source); run('root-runtime-skip', ['genesis-tools', '--request', str(request)], 3)
        skipped = json.loads((out / 'runtime-skip/COMPLETED.json').read_bytes()); assert skipped['original_analysis_executed'] is False and skipped['result_admitted'] is False
        assert len(admitted) == 11 and all(pin(Path(name)) == value for name, value in protected.items())
        state.update(status='PASS_GENESIS_ROOT13_LITERAL_ANALYSES11_ADMITTED2_REFUSED33_SAVED_REPLAYS12_HELPS1_SKIP', records=records,
                     admitted_cases=admitted, original_analysis_attempts=13, admitted_original_analyses=11, expected_scientific_refusals=2, saved_replays=33, root_help_checks=12,
                     prerequisite_skips=1, original_executions_reused=13 if reuse is not None else 0, reused_attempt=pin(a.reuse_completed / 'COMPLETED.json') if reuse is not None else None,
                     canonical_executable_unchanged=pin(canonical), source_pins={str(Path(__file__).relative_to(ROOT)): pin(Path(__file__))}, protected=protected)
        code = 0
    except Exception as error:
        state.update(cause=repr(error), records=records); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Actual original analyses and explicitly scoped saved checks completed; no historical-paper or whole-goal certificate.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n'); save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', '')[:6000], flush=True); return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact example/runtime/source and declared-result checks')
    raise SystemExit(main())
