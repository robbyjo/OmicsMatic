# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete owned Java DIAMOND execution with unchanged original source controls."""
import argparse, collections, hashlib, json, os, shutil, subprocess, traceback
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'build/literature-review/diamond-runtime'


def pin(path):
    h, n = hashlib.sha256(), 0
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            h.update(block)
            n += len(block)
    return dict(bytes=n, sha256=h.hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--reference', required=True, type=Path)
    parser.add_argument('--case', action='append')
    parser.add_argument('--version', action='append')
    parser.add_argument('--existing-database', action='store_true')
    parser.add_argument('--wall-seconds', type=int, default=120)
    args = parser.parse_args()
    out, jar, reference = args.out.resolve(), args.jar.resolve(), args.reference.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    shutil.copy2(Path(__file__).resolve(), out/'validator-source.py')
    artifact = pin(jar)
    canonical = pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    cases = json.loads((reference/'inputs/cases.json').read_bytes())
    records = []
    state = dict(status='FAIL2_DIAMOND_ADAPTER_SOURCE_COMPARISON', implementation_credit=False, whole_goal_complete=False)
    environment = {k: v for k, v in os.environ.items() if not k.startswith('LD_')}
    environment.update(PATH='/usr/bin:/bin', LANG='C', LC_ALL='C')
    try:
        for version in args.version or ['2.0.0', '2.0.15', '2.1.8']:
            for case in cases:
                if version not in case['versions'] or args.case and case['id'] not in args.case:
                    continue
                folder = out/version/case['id']
                folder.mkdir(parents=True)
                expected = reference/version/case['id']
                source_process = json.loads((expected/'execution/process.json').read_bytes())
                request = dict(schema_version=1, out=str(folder/'analysis'),
                    runtime_profile=str(BASE/f'runtime-profile-{version}-1/runtime-profile.json'),
                    analysis=dict(schema_version=1, source_label=version,
                        database=dict(kind='diamond-db' if args.existing_database else 'protein-fasta',
                            path=str(reference/version/('database-'+case['reference'])/'reference.dmnd') if args.existing_database else str(reference/'inputs'/case['reference'])),
                        queries=[str(reference/'inputs'/q) for q in case['queries']], search=case['settings']),
                    limits=dict(input_bytes=10000000, decoded_bytes=10000000, output_bytes=200000000,
                        wall_seconds=args.wall_seconds, max_files=1000, max_records=10000, max_record_length=100000,
                        max_line_characters=1000000, max_alignment_rows=100000))
                save(folder/'request.json', request)

                def run(name, command):
                    with (folder/(name+'.stdout')).open('xb') as so, (folder/(name+'.stderr')).open('xb') as se:
                        process = subprocess.run(command, env=environment, stdin=subprocess.DEVNULL,
                            stdout=so, stderr=se, timeout=args.wall_seconds+30)
                    save(folder/(name+'.process.json'), dict(command=command, exit_code=process.returncode))
                    return process.returncode

                code = run('adapter', ['/usr/bin/java', '-jar', str(jar), '--request', str(folder/'request.json')])
                original_code = source_process['exit_code']
                record = dict(version=version, case=case['id'], original_exit=original_code, adapter_exit=code)
                analysis = folder/'analysis'
                disposition = json.loads((analysis/'COMPLETED.json').read_bytes())
                if original_code != 0:
                    assert code == 2 and disposition['result_admitted'] is False
                    assert not (analysis/'results').exists() and not (analysis/'worker').exists()
                    failed = json.loads((analysis/'unadmitted-worker/search.process.json').read_bytes())
                    # Java reports POSIX signals as128+signal rather than Python's negative signal number.
                    assert failed['exit_code'] == (128-original_code if original_code < 0 else original_code)
                    assert failed['owned_processes_terminal'] is True
                    text = (analysis/'WARNINGS.md').read_text()
                    assert 'FAIL2' in text and 'REQUIRED ACTION' in text
                    record['original_failure_preserved_without_result'] = True
                else:
                    assert code == 0 and disposition['result_admitted'] is True, (folder, code)
                    actual = (analysis/'results/hits.tsv').read_text().splitlines()
                    direct = (expected/'hits.tsv').read_text().splitlines()
                    assert collections.Counter(actual) == collections.Counter(direct), (folder, 'complete alignment population differs')
                    assert run('verify', ['/usr/bin/java', '-jar', str(jar), '--verify', str(analysis)]) == 0
                    provenance = json.loads((analysis/'provenance.json').read_bytes())
                    assert provenance['native_reference_population_checked'] is (not args.existing_database)
                    record.update(rows=len(actual), complete_population_equal=True,
                        source_order_equal=actual == direct, native_transport_replay=True, provenance=provenance)
                records.append(record)
                save(folder/'VALIDATION.json', record)
                print(version, case['id'], 'source', original_code, 'adapter', code, 'PASS', flush=True)
        assert records and artifact == pin(jar) and canonical == pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        state.update(status='PASS_COMPLETE_ORIGINAL_DIAMOND_ADAPTER_COMPARISONS', records=records,
            successful_alignments=sum(r['adapter_exit'] == 0 for r in records),
            preserved_original_failure_cases=sum(r['adapter_exit'] != 0 for r in records),
            artifact=dict(path=str(jar), **artifact), canonical_unchanged=canonical,
            scope='Complete synthetic source-option alignment populations and native transport replay only; no independent alignment refit or paper/biological reproduction.')
    except Exception as error:
        state.update(error=repr(error), records=records)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:500], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete original-source comparison checks')
    raise SystemExit(main())
