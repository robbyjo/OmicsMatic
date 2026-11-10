# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every native MTAG component cell against unchanged original Python2 functions on three JVMs."""
import argparse
import csv
import json
import math
import os
import random
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_mtag_reference import BASE, COMMIT, linux
from build_mtag_native import DEPENDENCY, DEPENDENCY_SHA

REFERENCE = r'''from __future__ import print_function
import sys, json, os, traceback
sys.path.insert(0, sys.argv[1])
import mtag
import numpy as np
assert mtag.__version__ == '1.0.8'
manifest = json.load(open(sys.argv[2]))
out = {}
for case in manifest:
    lines = open(os.path.join(os.path.dirname(sys.argv[2]), case['input'])).read().splitlines()
    _, m, t = lines[0].split('\t'); m, t = int(m), int(t)
    def matrix(start, rows):
        return np.array([[float.fromhex(v) for v in line.split('\t')] for line in lines[start:start+rows]])
    z, n = matrix(3,m), matrix(3+m,m)
    omega, sigma = matrix(3+2*m,t), matrix(3+2*m+t,t)
    try:
        beta, se, factor = mtag.mtag_analysis(z,n,omega,sigma)
        score = beta/se
        p = 2 * mtag.scipy.stats.norm.cdf(-np.abs(score))
        gmm = mtag.gmm_omega(z,n,sigma)
        out[case['case']] = dict(status='ORIGINAL_RETURNED', beta=beta.tolist(),se=se.tolist(),factor=factor.tolist(),z=score.tolist(),p=p.tolist(),gmm=gmm.tolist())
    except Exception as error:
        out[case['case']] = dict(status='ORIGINAL_ERROR', error=repr(error), traceback=traceback.format_exc())
with open(sys.argv[3], 'w') as stream:
    json.dump(out, stream, sort_keys=True, indent=2, allow_nan=True)
print('Original unchanged functions evaluated %d cases' % len(manifest))
'''
RUNNER = '''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class MtagTestRunner {
    public static void main(String[] args) {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.genetics.MtagEstimatorTest")).build();
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);launcher.execute(request);
        var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
        if(summary.getTestsFoundCount()!=8||summary.getTestsSucceededCount()!=8||summary.getTestsSkippedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)System.exit(2);
    }
}
'''


class Unavailable(RuntimeError):
    pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--setup', type=Path, required=True)
    parser.add_argument('--build21', type=Path, required=True)
    parser.add_argument('--build25', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_NATIVE_MTAG_REFERENCE_VALIDATION', new_catalog_credit=False, whole_goal_complete=False,
                 full_mtag_workflow_validated=False, max_scaled_tolerance=5e-12, nonzero_p_relative_tolerance=5e-10)
    protected = {str(ROOT / 'build/cli/omicsmatic-0.3.7.jar'): pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')}
    save(out / 'STARTED.json', state)

    def run(label, command, expected=0, timeout=300):
        result = subprocess.run(command, cwd=ROOT, env=dict(os.environ), stdin=subprocess.DEVNULL, capture_output=True, timeout=timeout)
        (out / (label + '.stdout')).write_bytes(result.stdout)
        (out / (label + '.stderr')).write_bytes(result.stderr)
        save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, result.stderr[-2500:])

    try:
        setup = json.loads((args.setup / 'COMPLETED.json').read_bytes())
        assert setup['status'] == 'PASS_ORIGINAL_MTAG108_REFERENCE_PREPARED_ONLY' and setup['source_commit'] == COMMIT
        preflight = subprocess.run(['wsl.exe', '-d', setup['distribution'], '--', '/usr/bin/test', '-x', setup['python']],
                                   stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
        save(out / 'runtime-preflight.json', dict(python=setup['python'], exit_code=preflight.returncode))
        if preflight.returncode:
            raise Unavailable('The recorded original Python2 runtime is unavailable; skip dependent reference comparisons and recreate the private runtime where permitted')
        source = BASE / 'source-intake-1/source'
        for name, wanted in setup['source_inventory'].items():
            assert pin(source / name) == wanted
            protected[str(source / name)] = wanted
        assert pin(DEPENDENCY)['sha256'] == DEPENDENCY_SHA
        builds = {}
        for label, path in [('windows21', args.build21), ('windows25', args.build25)]:
            document = json.loads((path / 'COMPLETED.json').read_bytes())
            assert document['status'] == 'PASS_NATIVE_MTAG_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY'
            for name, wanted in document['source_pins'].items():
                assert pin(ROOT / name) == wanted
                protected[str(ROOT / name)] = wanted
            for artifact in document['artifacts'].values():
                assert pin(ROOT / artifact['path']) == {k: artifact[k] for k in ['bytes', 'sha256']}
                protected[str(ROOT / artifact['path'])] = pin(ROOT / artifact['path'])
            builds[label] = path.resolve()
        inputs = out / 'inputs'
        inputs.mkdir()
        rng = random.Random(20261110)
        cases = []

        def add(name, z, n, omega, sigma, native=True):
            m, t = len(z), len(omega)
            lines = ['MTAG108\t%d\t%d' % (m, t), '\t'.join('trait%d' % i for i in range(t)), '\t'.join('variant%d' % i for i in range(m))]
            for matrix in [z, n, omega, sigma]:
                for row in matrix:
                    assert len(row) == t
                    lines.append('\t'.join('NaN' if math.isnan(float(v)) else float(v).hex() for v in row))
            path = inputs / (name + '.tsv')
            path.write_text('\n'.join(lines) + '\n', encoding='utf8', newline='\n')
            cases.append(dict(case=name, input=path.name, native_expected=native, variants=m, traits=t, pin=pin(path)))

        def spd(t, scale):
            l = [[(1 + rng.random()) if i == j else rng.uniform(-.4, .4) if j < i else 0 for j in range(t)] for i in range(t)]
            a = [[0.] * t for _ in range(t)]
            for i in range(t):
                for j in range(i + 1):
                    a[i][j] = a[j][i] = scale * sum(l[i][k] * l[j][k] for k in range(t))
            return a

        add('one-trait-and-tail', [[0], [2], [-3], [38]], [[100], [400], [1000], [100]], [[.005]], [[1.44]])
        add('independent-traits', [[2, -3], [0, 1]], [[100, 400], [500, 300]], [[.005, 0], [0, .002]], [[1, 0], [0, 1]])
        add('negative-genetic-correlation', [[2, -3], [-4, 1]], [[1000, 2000], [1200, 1800]], [[.005, -.002], [-.002, .003]], [[1.1, .2], [.2, 1.2]])
        for t, counts in [(1, [1, 11, 101]), (2, [1, 11, 101]), (3, [1, 11, 101]), (5, [1, 11, 101]), (8, [1, 11]), (16, [1, 7]), (32, [1, 3])]:
            for m in counts:
                add('random-%d-%d' % (t, m), [[rng.gauss(0, 2) for _ in range(t)] for _ in range(m)],
                    [[rng.uniform(2000, 300000) for _ in range(t)] for _ in range(m)], spd(t, 1e-5), spd(t, .5))
        add('complete-5000', [[rng.gauss(0, 2) for _ in range(3)] for _ in range(5000)], [[10000, 12000, 15000] for _ in range(5000)], spd(3, 1e-4), spd(3, .5))
        add('unsupported-perfect-correlation', [[2, 3]], [[100, 200]], [[1, 1], [1, 1]], [[1, 0], [0, 1]], False)
        add('unsupported-asymmetric-covariance', [[2, 3]], [[100, 200]], [[1, .1], [.2, 1]], [[1, 0], [0, 1]], False)
        add('unsupported-nonfinite-z', [[float('nan')]], [[100]], [[1]], [[1]], False)
        add('unsupported-zero-n', [[2]], [[0]], [[1]], [[1]], False)
        save(inputs / 'manifest.json', cases)
        worker = out / 'original_reference.py'
        worker.write_text(REFERENCE, encoding='utf8', newline='\n')
        prefix = ['wsl.exe', '-d', setup['distribution'], '--']
        clean = prefix + ['/usr/bin/env', '-i', 'PATH=/usr/bin:/bin', 'LC_ALL=C', 'OPENBLAS_NUM_THREADS=1', 'OMP_NUM_THREADS=1',
                          'MKL_NUM_THREADS=1', 'PYTHONHASHSEED=0', 'PYTHONDONTWRITEBYTECODE=1']
        run('original-reference', clean + [setup['python'], '-B', linux(worker), linux(source), linux(inputs / 'manifest.json'), linux(out / 'original-reference.json')])
        original = json.loads((out / 'original-reference.json').read_bytes())
        assert set(original) == {c['case'] for c in cases}
        cache = Path('C:/Users/User/.gradle/caches/modules-2/files-2.1')
        dependencies = []
        for group, artifact, version in [('org.junit.jupiter', 'junit-jupiter-api', '5.13.4'), ('org.junit.jupiter', 'junit-jupiter-engine', '5.13.4'),
            ('org.junit.platform', 'junit-platform-engine', '1.13.4'), ('org.junit.platform', 'junit-platform-launcher', '1.13.4'),
            ('org.junit.platform', 'junit-platform-commons', '1.13.4'), ('org.apiguardian', 'apiguardian-api', '1.1.2'), ('org.opentest4j', 'opentest4j', '1.3.0')]:
            matches = list((cache / group / artifact / version).glob('*/*.jar'))
            assert len(matches) == 1, (artifact, version)
            dependencies += matches
        runner = out / 'MtagTestRunner.java'
        runner.write_text(RUNNER, encoding='utf8', newline='\n')
        test_classes = out / 'test-classes'
        test_classes.mkdir()
        cp = [builds['windows21'] / 'mtag-api.jar', DEPENDENCY, *dependencies]
        javahome = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
        run('junit-compile', [str(javahome / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-sourcepath', '', '-cp', ';'.join(map(str, cp)), '-d', str(test_classes), str(ROOT / 'src/test/java/org/jlinalg/genetics/MtagEstimatorTest.java'), str(runner)])
        summaries = []
        for platform in ['windows21', 'windows25', 'linux21']:
            build = builds['windows25' if platform == 'windows25' else 'windows21']
            home = Path('C:/Program Files/Common Files/Oracle/Java/javapath') if platform == 'windows25' else javahome
            java = prefix + ['/usr/bin/java'] if platform == 'linux21' else [str(home / 'java.exe')]
            paths = [build / 'mtag-api.jar', build / 'mtag-consumer.jar', DEPENDENCY]
            classpath = ':'.join(map(linux, paths)) if platform == 'linux21' else ';'.join(map(str, paths))
            directory = out / platform
            directory.mkdir()
            count, refusals, max_scaled, max_p_relative = 0, 0, 0., 0.
            for case in cases:
                input_path, output_path = inputs / case['input'], directory / (case['case'] + '.tsv')
                command = java + ['-cp', classpath, 'org.jlinalg.genetics.MtagReferenceProbe',
                    linux(input_path) if platform == 'linux21' else str(input_path), linux(output_path) if platform == 'linux21' else str(output_path)]
                run(platform + '-' + case['case'], command, 0 if case['native_expected'] else 2)
                if not case['native_expected']:
                    assert not output_path.exists()
                    refusals += 1
                    continue
                ref = original[case['case']]
                assert ref['status'] == 'ORIGINAL_RETURNED'
                with output_path.open(encoding='utf8', newline='') as stream:
                    rows = list(csv.DictReader(stream, delimiter='\t'))
                expected_count = 5 * case['variants'] * case['traits'] + case['traits'] ** 2
                assert len(rows) == expected_count
                seen = set()
                for row in rows:
                    kind, i, j = row['kind'], int(row['row']), int(row['column'])
                    assert (kind, i, j) not in seen
                    seen.add((kind, i, j))
                    a, b = float.fromhex(row['value_hex']), ref[kind][i][j]
                    assert math.isfinite(a) and math.isfinite(b)
                    scaled = abs(a - b) / max(1., abs(b))
                    assert scaled <= state['max_scaled_tolerance'], (platform, case['case'], kind, i, j, a, b)
                    max_scaled = max(max_scaled, scaled)
                    if kind == 'p':
                        if b == 0:
                            assert a == 0, (case['case'], a, b)
                        else:
                            relative = abs(a - b) / b
                            assert relative <= state['nonzero_p_relative_tolerance'], (case['case'], a, b)
                            max_p_relative = max(max_p_relative, relative)
                    count += 1
                for kind in ['beta', 'se', 'factor', 'z', 'p', 'gmm']:
                    rr = case['traits'] if kind == 'gmm' else case['variants']
                    assert {(kind, i, j) for i in range(rr) for j in range(case['traits'])}.issubset(seen)
            test_cp = [test_classes, build / 'mtag-api.jar', DEPENDENCY, *dependencies]
            junit_classpath = ':'.join(map(linux, test_cp)) if platform == 'linux21' else ';'.join(map(str, test_cp))
            run(platform + '-junit', java + ['-cp', junit_classpath, 'MtagTestRunner'])
            summaries.append(dict(platform=platform, compared_cells=count, refused_cases=refusals, junit_passed=8, max_scaled_error=max_scaled, max_nonzero_p_relative_error=max_p_relative))
        for path, wanted in protected.items():
            assert pin(Path(path)) == wanted, path
        for case in cases:
            assert pin(inputs / case['input']) == case['pin']
        state.update(status='PASS_NATIVE_MTAG_ESTIMATOR_AND_GMM_COMPONENT_ONLY', cases=cases, platforms=summaries,
                     source_commit=COMMIT, original_outcomes={k: v['status'] for k, v in original.items()}, canonical_executable_unchanged=True,
                     source_pins={str(Path(p).relative_to(ROOT)).replace('\\', '/'): v for p, v in protected.items()},
                     original_reference_pin=pin(out / 'original-reference.json'))
        print(json.dumps(dict(status=state['status'], cases=len(cases), platforms=summaries)))
    except Unavailable as error:
        state.update(status='SKIP3_NATIVE_MTAG_REFERENCE_UNAVAILABLE', error=repr(error))
        print(state['status'] + ': ' + repr(error))
    except Exception as error:
        state['error'] = repr(error)
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
        print(state['status'] + ': ' + repr(error))
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\nCOMPONENT ONLY: MTAG remains missing at the catalog workflow level.\n\n'
        + state.get('error', 'Original outputs and every compared cell are retained; native refusal cases admit no result.')
        + '\n\nREQUIRED ACTION: preserve all attempts. Supply permitted exact runtimes when unavailable; skip dependent analysis until then. Resolve named source/numerical/input causes without repair, and retry NEW paths. Complete harmonization, QC, LDSC, covariance adjustment, maxFDR and typed project workflow validation remain necessary before full tool admission.\n', encoding='utf8', newline='\n')
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete-cell, original-source or protected-artifact checks')
    raise SystemExit(main())
