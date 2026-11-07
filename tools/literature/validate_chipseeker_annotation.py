# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual-JAR typed annotation batches against every complete original return/error control."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, RSCRIPT, pin, save
from validate_mashr_native import JAVA21


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True); parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--versions', nargs='+', default=['1.22.1', '1.26.2', '1.34.1', '1.48.1'])
    parser.add_argument('--jvms', nargs='+', choices=['windows-java21', 'windows-java25'], default=['windows-java21', 'windows-java25'])
    parser.add_argument('--case-limit', type=int)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists(); out.mkdir()
    original = ROOT / 'build/literature-review/chipseeker-runtime/annotation-original-2'
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ('bytes', 'sha256')}
    for name, expected in build['source_pins'].items():assert pin(ROOT / name) == expected, name
    bindings = {Path(__file__).resolve().relative_to(ROOT).as_posix(): pin(Path(__file__))}
    def read(path):
        bindings[path.relative_to(ROOT).as_posix()] = pin(path)
        return json.loads(path.read_bytes())
    libraries = read(original / 'STARTED.json')['libraries']
    fixture = original / 'fixture'
    for path in [fixture / 'peaks.tsv', fixture / 'synthetic-txdb.sqlite']:
        bindings[path.relative_to(ROOT).as_posix()] = pin(path)
    javas = {'windows-java21': JAVA21 / 'java.exe', 'windows-java25': Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')}
    runs = []; total_returned = total_errors = total_triples = 0
    for jvm in args.jvms:
        for version in args.versions:
            assert version in {'1.22.1', '1.26.2', '1.34.1', '1.48.1'}
            folder = original / version; gold = read(folder / 'controls/ORIGINAL.json'); plan = read(folder / 'PLAN.json')
            by_id = {case['id']: case for case in gold['cases']}
            current_libraries = libraries
            if version != '1.48.1':
                current_libraries = [read(folder / 'reused-installation.json')['path'], *libraries]
            for kind, expected_code in [('returned', 0), ('original-errors', 2)]:
                selected = [c for c in plan['cases'] if (by_id[c['id']]['source_error'] is None) == (kind == 'returned')]
                if args.case_limit is not None:
                    assert args.case_limit > 0; selected = selected[:args.case_limit]
                directory = out / jvm / version / kind; directory.mkdir(parents=True)
                analyses = []
                for case in selected:
                    c = dict(case); c['annotation_priority'] = c.pop('priority'); c['same_strand'] = False
                    analyses.append(c)
                request = dict(schema_version=1, mode='ANNOTATE', version=version, peaks=str(fixture / 'peaks.tsv'),
                    txdb=str(fixture / 'synthetic-txdb.sqlite'), txdb_sha256=pin(fixture / 'synthetic-txdb.sqlite')['sha256'],
                    reference_build='SYNTHETIC_CHIPSEEKER_CONTROL_V1', coordinates='one-based-inclusive',
                    out='result', rscript=str(RSCRIPT), r_libraries=current_libraries, analyses=analyses,
                    timeout_seconds=300, maximum_output_bytes=67108864)
                request_file = directory / 'request.json'; save(request_file, request)
                command = [str(javas[jvm]), '-Xmx768m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-cp', str(jar),
                           'org.jlinalg.cli.ChipSeekerAnnotationCli', '--request', str(request_file)]
                with (directory / 'stdout.log').open('xb') as stdout, (directory / 'stderr.log').open('xb') as stderr:
                    process = subprocess.Popen(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
                    try:
                        code = process.wait(timeout=700)
                    except subprocess.TimeoutExpired:
                        if process.poll() is None:
                            cleanup = subprocess.run(['taskkill.exe', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
                            (directory / 'forced-owned-cleanup.stdout').write_bytes(cleanup.stdout)
                            (directory / 'forced-owned-cleanup.stderr').write_bytes(cleanup.stderr)
                        save(directory / 'TIMEOUT.json', dict(owned_pid=process.pid, result_admitted=False))
                        raise
                run = dict(jvm=jvm, version=version, kind=kind, command=command, exit_code=code, original_cases=len(selected))
                save(directory / 'process.json', run)
                assert code == expected_code, str(directory) + ': inspect retained actual-JAR stdout/stderr and WARNINGS.md'
                bundle = directory / 'result'; state = json.loads((bundle / 'analysis-status.json').read_bytes())
                worker = json.loads((bundle / 'worker/worker-report.json').read_bytes())
                assert len(worker['cases']) == len(selected) and len(worker['guarded_source_functions']) >= 17
                if kind == 'returned':
                    assert state['result_admitted'] is True and state['status'] == 'completed-with-warnings'
                    report = json.loads((bundle / 'report.json').read_bytes())
                    assert len(report['analyses']) == len(selected) and all(p['native_nearest_audit'] == 'passed' for p in report['analyses'])
                    for case in selected:
                        gold_case = folder / 'controls' / case['id']; actual = bundle / 'results' / case['id']
                        expected = read(gold_case / 'original-output.json')
                        assert json.loads((actual / 'original-output.json').read_bytes()) == expected, (jvm, version, case['id'], 'full-original-annotation/detail/distribution mismatch')
                        nearest = read(gold_case / 'original-nearest.json')
                        assert json.loads((actual / 'original-nearest.json').read_bytes()) == nearest, (jvm, version, case['id'], 'complete-original-nearest/ordered-features mismatch')
                        assert json.loads((actual / 'analysis-status.json').read_bytes())['result_admitted'] is True
                        assert len((actual / 'query-ledger.tsv').read_text(encoding='utf-8').splitlines()) == 71
                        assert all((actual / p).is_file() for p in ('original-csAnno.rds', 'original-nearest.rds', 'original-ordered-features.rds', 'original-cell-kinds.json'))
                        total_triples += len(nearest['index_tokens'])
                    total_returned += len(selected)
                else:
                    assert state['result_admitted'] is False and not (bundle / 'results').exists()
                    assert len(worker['cases']) == len(selected)
                    for case in worker['cases']:
                        assert case['status'] == 'FAIL2_ORIGINAL_ERROR'
                        assert case['original_error']['message'] == by_id[case['id']]['source_error']['message']
                        assert case['id'] in (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
                    total_errors += len(selected)
                assert 'REQUIRED ACTION' in (bundle / 'WARNINGS.md').read_text(encoding='utf-8')
                runs.append(run); print(json.dumps({k: v for k, v in run.items() if k != 'command'}), flush=True)
    assert all(pin(ROOT / name) == expected for name, expected in bindings.items())
    assert all(pin(ROOT / name) == expected for name, expected in build['source_pins'].items())
    save(out / 'COMPLETED.json', dict(status='passed-actual-JAR-original-annotation-return-and-error-controls', artifact=build['artifact'],
        source_pins=bindings, runs=runs, original_returned_configurations=total_returned, original_error_configurations=total_errors,
        native_retained_triples=total_triples, all_152_returned_48_error_controls_per_jvm=args.case_limit is None and len(args.versions)==4,
        workflow_or_catalog_admitted=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source or original-output checks')
    main()
