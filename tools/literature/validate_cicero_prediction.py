# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual Java-only prediction JAR on three JVMs; independently audit every decision and byte."""
import argparse
import concurrent.futures
import copy
import csv
import io
import json
import os
from pathlib import Path
import random
import subprocess
from setup_deseq2_reference import ROOT, pin, save
from validate_mashr_native import JAVA21, LIBS, linux
from signac_scan_evidence import byte_pin


def audit(folder):
    return audit_bytes({p.relative_to(folder).as_posix(): p.read_bytes() for p in folder.rglob('*') if p.is_file()})


def audit_bytes(files):
    state = json.loads(files['analysis-status.json'])
    assert state['result_admitted'] and state['status'] == 'PASS_ACCESSIBILITY_SUBSET'
    request = json.loads(files['inputs/request.json'])
    def read(name):
        rows = files['inputs/' + name].decode('utf8').split('\n')
        if rows[-1] == '':
            rows.pop()
        return [line.removesuffix('\r').split('\t') for line in rows]
    predictions, metadata, accessible = read('predictions.tsv'), read('peak-metadata.tsv'), read('accessible-peaks.tsv')
    fragments = files['inputs/predictions.tsv'].split(b'\n')
    original = [line + b'\n' for line in fragments[:-1]] + ([fragments[-1]] if fragments[-1] else [])
    one_based = request['coordinate_system'] == 'one-based-closed'
    def interval(row, at):
        return row[at], int(row[at + 1]) - int(one_based), int(row[at + 2])
    peaks = {row[0]: interval(row, 1) for row in metadata[1:]}
    groups = {cell: [(row[1], interval(row, 2)) for row in accessible[1:] if row[0] == cell] for cell in request['cell_types']}
    def hits(query, group):
        return [name for name, peak in group if peak[0] == query[0] and max(0, min(peak[2], query[2]) - max(peak[1], query[1])) >= request['minimum_overlap_bp']]
    expected, retained = [], 0
    for ordinal, (cell, group) in enumerate(groups.items(), 1):
        output = [original[0]]
        for row, raw in zip(predictions[1:], original[1:]):
            source = hits(peaks[row[1]], group)
            target = hits(peaks[row[2]], group) if request['accessibility_scope'] == 'both-endpoints' else None
            keep = bool(source) and (target is None or bool(target))
            expected.append([cell, row[0], str(bool(source)).lower(), source[0] if source else '', str(len(source)),
                'not-tested' if target is None else str(bool(target)).lower(), '' if not target else target[0],
                'not-tested' if target is None else str(len(target)), 'retained' if keep else 'excluded'])
            if keep:
                output.append(raw)
                retained += 1
        assert files['results/cell-type-%04d.tsv' % ordinal] == b''.join(output)
    actual = [line.split('\t') for line in files['membership.tsv'].decode('utf8').removesuffix('\n').split('\n')]
    assert actual[1:] == expected
    counts = list(csv.DictReader(io.StringIO(files['counts.tsv'].decode('utf8')), delimiter='\t'))
    assert [row['cell_type'] for row in counts] == list(groups)
    for cell, row in zip(groups, counts):
        assert int(row['input_predictions']) == len(predictions) - 1
        assert int(row['retained']) == sum(r[0] == cell and r[-1] == 'retained' for r in expected)
        assert int(row['excluded']) + int(row['retained']) == len(predictions) - 1
    provenance = json.loads(files['provenance.json'])
    assert not provenance['gene_expression_tested'] and not provenance['original_prediction_rows_rewritten']
    assert provenance['generated_table_bytes'] == len(files['membership.tsv']) + sum(len(raw) for n, raw in files.items() if n.startswith('results/') and n.endswith('.tsv'))
    assert 'PREDICTION PAYLOAD NOT VALIDATED' in files['WARNINGS.md'].decode('utf8')
    for name, key in [('predictions.tsv', 'predictions'), ('peak-metadata.tsv', 'peak_metadata'), ('accessible-peaks.tsv', 'accessible_peaks')]:
        assert byte_pin(files['inputs/' + name])['sha256'] == provenance['input_sha256'][key]
    assert byte_pin(files['inputs/request.json'])['sha256'] == provenance['request_sha256']
    assert 'REQUIRED ACTION:' in files['WARNINGS.md'].decode('utf8')
    return dict(decisions=len(expected), retained=retained, original_prediction_bytes_preserved=True, all_memberships_independently_checked=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir()
    build = json.loads((args.build / 'COMPLETED.json').read_bytes())
    artifact = build['artifact']
    jar = ROOT / artifact['path']
    assert pin(jar) == {k: artifact[k] for k in ['bytes', 'sha256']}
    test = ROOT / 'src/test/java/org/jlinalg/cli/CiceroPredictionTest.java'
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), test]}
    for name in pins:
        p = out / 'source-snapshot' / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes((ROOT / name).read_bytes())
    libraries = sorted(LIBS.glob('*.jar'))
    assert len(libraries) == 9
    classes = out / 'test-classes'
    classes.mkdir()
    runner = out / 'RunPrediction.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunPrediction {
 public static void main(String[] args)throws Exception {
  if(!Class.forName("org.jlinalg.cli.CiceroPredictionCli").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong candidate JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.CiceroPredictionTest")).build(),listener);
  var r=listener.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));
  if(r.getTestsFoundCount()!=7||r.getTestsSucceededCount()!=7||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete tests");
 }
}
''', encoding='utf8', newline='\n')
    command = [str(JAVA21 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', str(classes), str(test), str(runner)]
    r = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(r.stdout)
    (out / 'compile.stderr').write_bytes(r.stderr)
    assert r.returncode == 0, r.stderr.decode('utf8', errors='replace')
    randomizer = random.Random(20261107)
    meta = []
    for i in range(400):
        start = randomizer.randrange(10000)
        meta.append(['s' + str(i), 'chrβ' if i % 2 else 'chr1', str(start), str(start + randomizer.randrange(1, 100))])
    predictions = [['p' + str(i), 's' + str(randomizer.randrange(400)), 's' + str(randomizer.randrange(400)), ['NA', 'NaN', 'Inf', '-0', '0.125'][i % 5], 'false' if i % 2 else 'true', ' literal"β \u2028\v' if i % 3 else ''] for i in range(800)]
    accessible = []
    for i in range(500):
        start = randomizer.randrange(10000)
        accessible.append(['β-cell' if i % 2 else ' quote" cell ', 'a' + str(i), 'chrβ' if i % 3 else 'chr1', str(start), str(start + randomizer.randrange(1, 150))])
    # Deliberate exact boundary, duplicate coordinates, unannotated targets and full long domain.
    meta.extend([['edge', 'X', '10', '20'], ['max', 'Y', str(2**63 - 2), str(2**63 - 1)]])
    predictions.extend([[' edge ', 'edge', 'edge', 'NA', 'false', ''], ['long', 'max', 'max', 'Inf', 'false', '']])
    accessible.extend([['β-cell', 'touch', 'X', '20', '30'], ['β-cell', 'last-base', 'X', '19', '20'], ['β-cell', 'duplicate-coordinate', 'X', '19', '20'], ['β-cell', 'max', 'Y', str(2**63 - 2), str(2**63 - 1)]])
    def write(path, rows, ending='\n', final=True):
        path.write_bytes((ending.join('\t'.join(row) for row in rows) + (ending if final else '')).encode('utf8'))
    def run_jvm(label):
        folder = out / label
        folder.mkdir()
        java = [str(JAVA21 / 'java.exe')] if label == 'windows-java21' else ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'] if label == 'windows-java25' else ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java']
        native = label == 'linux-java21'
        pth = linux if native else str
        entries = [classes, jar, *libraries]
        command = [*java, '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp', (':' if native else os.pathsep).join(pth(p) for p in entries), 'RunPrediction', pth(jar)]
        def execute(command, name, code):
            with (folder / (name + '.stdout')).open('xb') as a, (folder / (name + '.stderr')).open('xb') as b:
                run = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=120)
            save(folder / (name + '.process.json'), dict(command=command, exit_code=run.returncode, expected=code))
            assert run.returncode == code, label + '/' + name + ': inspect retained diagnostics'
        execute(command, 'contracts', 0)
        reports = []
        cases = ['source-only', 'both-endpoints', 'minimum-two', 'one-based', 'unmapped-target', 'empty-accessible',
                 'duplicate-accessible', 'unknown-source', 'unknown-target', 'duplicate-prediction', 'malformed-interval',
                 'mixed-endings', 'input-capacity', 'row-capacity', 'decision-capacity', 'operation-capacity', 'undeclared-cell',
                 'output-table-capacity', 'late-operation-capacity']
        for name in cases:
            case = folder / name
            case.mkdir()
            p, m, a = copy.deepcopy(predictions), copy.deepcopy(meta), copy.deepcopy(accessible)
            request = dict(schema_version=1, mode='SUBSET', predictions='predictions.tsv', peak_metadata='peaks.tsv', accessible_peaks='accessible.tsv', out='result', coordinate_system='zero-based-half-open', accessibility_scope='both-endpoints' if name in ['both-endpoints', 'unknown-target'] else 'source-only', cell_types=['β-cell', ' quote" cell ', 'empty'], minimum_overlap_bp=2 if name == 'minimum-two' else 1)
            if name == 'one-based':
                request['coordinate_system'] = 'one-based-closed'
                for row in m:
                    row[2] = str(int(row[2]) + 1)
                for row in a:
                    row[3] = str(int(row[3]) + 1)
            if name == 'unmapped-target':
                p[0][2] = 'unmapped target gene'
            if name == 'empty-accessible':
                a = []
            if name == 'duplicate-accessible':
                a.append(a[0])
            if name == 'unknown-source':
                p[0][1] = 'missing'
            if name == 'unknown-target':
                p[0][2] = 'missing'
            if name == 'duplicate-prediction':
                p.append(p[0])
            if name == 'malformed-interval':
                m[0][3] = m[0][2]
            if name == 'undeclared-cell':
                a[0][0] = 'undeclared'
            if name == 'output-table-capacity':
                request['maximum_output_table_bytes'] = 1
            if name == 'late-operation-capacity':
                request['maximum_operations'] = json.loads((folder / 'source-only/result/provenance.json').read_bytes())['operation_count'] - 1
            for case_name, key in [('input-capacity', 'maximum_input_bytes'), ('row-capacity', 'maximum_rows'), ('decision-capacity', 'maximum_decisions'), ('operation-capacity', 'maximum_operations')]:
                if name == case_name:
                    request[key] = 1
            write(case / 'predictions.tsv', [['prediction_id', 'source_peak_id', 'target_id', 'coaccess', 'gene_detected', 'original_note'], *p], '\r\n', False)
            write(case / 'peaks.tsv', [['peak_id', 'chromosome', 'start', 'end'], *m])
            write(case / 'accessible.tsv', [['cell_type', 'peak_id', 'chromosome', 'start', 'end'], *a])
            if name == 'mixed-endings':
                raw = (case / 'predictions.tsv').read_bytes().replace(b'\r\n', b'\n', 1)
                (case / 'predictions.tsv').write_bytes(raw)
            save(case / 'request.json', request)
            code = 0 if name in cases[:6] else 2
            execute([*java, '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp', pth(jar), 'org.jlinalg.cli.CiceroPredictionCli', '--request', pth(case / 'request.json')], name, code)
            state = json.loads((case / 'result/analysis-status.json').read_bytes())
            assert state['result_admitted'] is (code == 0) and (case / 'result/results').exists() is (code == 0)
            assert 'REQUIRED ACTION:' in (case / 'result/WARNINGS.md').read_text(encoding='utf8')
            report = dict(id=name, exit_code=code, result_admitted=state['result_admitted'])
            if code == 0:
                report.update(audit(case / 'result'))
            else:
                assert state['status'] == 'FAIL2_NO_RESULT_ADMITTED'
                report['cause'] = state['cause']
                if name == 'late-operation-capacity':
                    for n in [1, 2]:
                        file = 'cell-type-%04d.tsv' % n
                        assert (case / 'result/unadmitted-partial-results' / file).read_bytes() == (folder / 'source-only/result/results' / file).read_bytes()
                    report['earlier_completed_cell_types_rejected'] = 2
            reports.append(report)
        print(label + ': seven contracts and 19 independent runtime protocols passed', flush=True)
        return dict(JVM=label, tests=7, skipped=0, protocols=reports)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        reports = list(pool.map(run_jvm, ['windows-java21', 'windows-java25', 'linux-java21']))
    assert all(pin(ROOT / n) == v for n, v in [*pins.items(), *build['source_pins'].items()])
    assert pin(jar) == {k: artifact[k] for k in ['bytes', 'sha256']}
    save(out / 'COMPLETED.json', dict(status='passed-three-JVM-native-Cicero-prediction-and-complete-byte-membership-audit', artifact=artifact,
        source_pins=pins, JVMs=reports, original_R_fitting_executed=False, implementation_credit=False, whole_goal_complete=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/decision checks')
    main()
