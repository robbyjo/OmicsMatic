# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise DIAMOND skip/failure actions and rehashed semantic bundle refusals."""
import argparse, copy, hashlib, json, os, shutil, subprocess, traceback
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'build/literature-review/diamond-runtime'


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--bundle', type=Path, required=True)
    a = p.parse_args()
    out, jar, bundle = a.out.resolve(), a.jar.resolve(), a.bundle.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    shutil.copy2(Path(__file__).resolve(), out/'tester-source.py')
    request = json.loads((bundle/'source-request.json').read_bytes())
    profile = json.loads((bundle/'runtime-profile.json').read_bytes())
    records = []
    state = dict(status='FAIL2_DIAMOND_PROTOCOL_CONTROLS', implementation_credit=False, whole_goal_complete=False)
    env = {k: v for k, v in os.environ.items() if not k.startswith('LD_')}
    env.update(PATH='/usr/bin:/bin', LANG='C', LC_ALL='C')

    def run(folder, args, expected):
        with (folder/'stdout').open('xb') as so, (folder/'stderr').open('xb') as se:
            process = subprocess.run(['/usr/bin/java', '-jar', str(jar), *args],
                env=env, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=150)
        save(folder/'process.json', dict(arguments=args, exit_code=process.returncode))
        assert process.returncode == expected, (folder, expected, process.returncode)
        return (folder/'stderr').read_text()

    try:
        for name, code in [('missing-profile', 3), ('missing-executable', 3), ('missing-runtime-file', 3),
            ('changed-runtime-file', 2), ('wrong-runtime-edition', 2), ('unknown-request-field', 2),
            ('input-capacity', 2), ('alignment-row-capacity', 2), ('malformed-fastq', 2), ('wall-time', 2)]:
            folder = out/name
            folder.mkdir()
            r, runtime = copy.deepcopy(request), copy.deepcopy(profile)
            r['out'] = str(folder/'analysis')
            r['runtime_profile'] = str(folder/'runtime-profile.json')
            if name == 'missing-profile':
                r['runtime_profile'] = str(folder/'missing-profile.json')
            elif name == 'missing-executable':
                old = runtime['executable']
                runtime['executable'] = str(folder/'missing-exact-binary')
                runtime['files'][runtime['executable']] = runtime['files'].pop(old)
            elif name == 'missing-runtime-file':
                runtime['files'][str(folder/'missing-selected-library')] = dict(bytes=1, sha256='0'*64)
            elif name == 'changed-runtime-file':
                key = next(k for k in runtime['files'] if k != runtime['executable'])
                runtime['files'][key]['sha256'] = '0'*64
            elif name == 'wrong-runtime-edition':
                runtime['source_label'] = '2.1.8' if r['analysis']['source_label'] != '2.1.8' else '2.0.0'
            elif name == 'unknown-request-field':
                r['silent_fallback'] = True
            elif name == 'input-capacity':
                r['limits']['input_bytes'] = 1
            elif name == 'alignment-row-capacity':
                r['limits']['max_alignment_rows'] = 0
            elif name == 'malformed-fastq':
                f = folder/'malformed.fq'
                f.write_text('@bad\nACGT\n+\nIII\n')
                r['analysis']['queries'] = [str(f)]
            elif name == 'wall-time':
                r['limits']['wall_seconds'] = 1
            save(folder/'runtime-profile.json', runtime)
            save(folder/'request.json', r)
            stderr = run(folder, ['--request', str(folder/'request.json')], code)
            assert ('SKIP3' if code == 3 else 'FAIL2') in stderr and 'REQUIRED ACTIONS' in stderr
            analysis = folder/'analysis'
            if analysis.exists():
                disposition = json.loads((analysis/'COMPLETED.json').read_bytes())
                assert disposition['result_admitted'] is False
                assert not (analysis/'results').exists() and not (analysis/'worker').exists()
                assert 'REQUIRED ACTION' in (analysis/'WARNINGS.md').read_text()
            if name == 'wall-time':
                assert 'wall-time' in stderr and list((analysis/'unadmitted-worker').glob('*.started.json'))
                for started in (analysis/'unadmitted-worker').glob('*.started.json'):
                    pid = json.loads(started.read_bytes())['pid']
                    assert not Path('/proc', str(pid)).exists(), ('Owned process still live', pid)
            records.append(dict(case=name, exit_code=code, result_admitted=False, prominent_actions=True))
            print(name, 'PASS', flush=True)

        for name in ['missing-file', 'extra-file', 'scope', 'actions', 'profile-binding', 'scientific-command',
            'coordinate', 'identity', 'input-census', 'query-disposition', 'material-warning', 'source-lock']:
            folder = out/('tamper-'+name)
            folder.mkdir()
            target = folder/'bundle'
            shutil.copytree(bundle, target)
            if name == 'missing-file':
                (target/'input-records.jsonl').unlink()
            elif name == 'extra-file':
                (target/'unexpected.txt').write_text('extra\n')
            elif name in ['scope', 'actions']:
                done = json.loads((target/'COMPLETED.json').read_bytes())
                done['scope' if name == 'scope' else 'required_action'] = 'incorrect'
                save(target/'COMPLETED.json', done)
            elif name == 'profile-binding':
                runtime = json.loads((target/'runtime-profile.json').read_bytes())
                runtime['files'][runtime['executable']]['sha256'] = '0'*64
                save(target/'runtime-profile.json', runtime)
            elif name == 'scientific-command':
                execution = json.loads((target/'execution.json').read_bytes())
                cmd = execution['steps'][-1]['command']
                cmd[cmd.index('--evalue')+1] = '0.5'
                save(target/'execution.json', execution)
            elif name in ['coordinate', 'identity']:
                path = target/'results/hits.tsv'
                rows = path.read_text().splitlines()
                first = rows[0].split('\t')
                first[6 if name == 'coordinate' else 2] = '9999999' if name == 'coordinate' else '1.0'
                rows[0] = '\t'.join(first)
                path.write_text('\n'.join(rows)+'\n')
            elif name in ['input-census', 'query-disposition']:
                path = target/('input-records.jsonl' if name == 'input-census' else 'query-dispositions.jsonl')
                path.write_text(path.read_text()+'{}\n')
            elif name == 'material-warning':
                path = target/'WARNINGS.md'
                path.write_text(path.read_text().replace('ORIGINAL OUTPUT PRECISION:', 'REMOVED WARNING:'))
            elif name == 'source-lock':
                lock = json.loads((target/'source-lock.json').read_bytes())
                lock['output_fields'] = lock['output_fields'][:12]
                save(target/'source-lock.json', lock)
            # Rebuild the inventory to exercise semantic validation beyond a stale checksum.
            # Missing/extra population controls intentionally retain their original inventory.
            if name not in ['missing-file', 'extra-file']:
                done = json.loads((target/'COMPLETED.json').read_bytes())
                done['files'] = {p.relative_to(target).as_posix(): pin(p) for p in sorted(target.rglob('*'))
                    if p.is_file() and p.name != 'COMPLETED.json'}
                save(target/'COMPLETED.json', done)
            stderr = run(folder, ['--verify', str(target)], 2)
            assert 'FAIL2' in stderr and 'REQUIRED ACTIONS' in stderr
            records.append(dict(case='tamper-'+name, exit_code=2, rehashed=name not in ['missing-file', 'extra-file']))
            print('tamper-'+name, 'PASS', flush=True)
        state.update(status='PASS_DIAMOND_PROTOCOL_SKIP_FAILURE_AND_REHASHED_REFUSALS', records=records,
            dependency_skips=3, operational_failures=7, verification_refusals=12, artifact=pin(jar),
            verification_scope='Source/transport consistency and fail-closed admission only; not producer authentication.')
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:500], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable admission/refusal checks')
    raise SystemExit(main())
