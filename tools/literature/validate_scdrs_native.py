# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare native scDRS components with three unchanged historical sources on three JVMs."""
import argparse
import csv
import json
import math
import os
import struct
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux
from intake_scdrs_reference import VERSIONS
from build_mtag_native import DEPENDENCY

RUNNER = '''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class ScdrsTestRunner {
 public static void main(String[] args) {
  var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.genetics.ScdrsScoresTest")).build();
  var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);launcher.execute(request);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out,true));s.printFailuresTo(new PrintWriter(System.out,true));
  if(s.getTestsFoundCount()!=8||s.getTestsSucceededCount()!=8||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)System.exit(2);
 }
}
'''


class Unavailable(RuntimeError):
    pass


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--setup', type=Path, required=True)
    p.add_argument('--build21', type=Path, required=True)
    p.add_argument('--build25', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    (out / 'capture-at-attempt.py').write_bytes((ROOT / 'tools/literature/capture_scdrs_native_reference.py').read_bytes())
    state = dict(status='FAIL2_NATIVE_SCDRS_VALIDATION', new_catalog_credit=False,
                 full_adapter_validated=False, whole_goal_complete=False,
                 double_scaled_tolerance=5e-12, float32_max_ulps=2,
                 probabilities_require_exact_source_float32=True)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    save(out / 'STARTED.json', state)

    def run(label, command, expected=0, timeout=600):
        save(out / (label + '.command.json'), dict(command=command))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(command, cwd=ROOT, env=dict(os.environ), stdin=subprocess.DEVNULL,
                                    stdout=stdout, stderr=stderr, timeout=timeout)
        save(out / (label + '.process.json'), dict(exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, (out / (label + '.stderr')).read_bytes()[-2000:])

    try:
        setup = json.loads((a.setup / 'COMPLETED.json').read_bytes())
        assert setup['status'] == 'PASS_THREE_ORIGINAL_SCDRS_SOURCE_IMPORTS_SETUP_ONLY'
        assert pin(ROOT / 'tools/literature/setup_scdrs_reference.py') == setup['installer_pin']
        preflight = subprocess.run(['wsl.exe', '-d', setup['distribution'], '--', '/usr/bin/test', '-x', setup['python']],
                                   stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
        if preflight.returncode:
            raise Unavailable('Private scDRS Python unavailable; repeat permitted setup in NEW private paths')
        clean = setup['clean_environment']
        run('dependency-freeze', clean + [setup['python'], '-m', 'pip', 'freeze', '--all'], timeout=60)
        assert pin(out / 'dependency-freeze.stdout') == setup['dependency_freeze']
        build_records = {}
        for tag, directory in [('javac21', a.build21), ('javac25', a.build25)]:
            record = json.loads((directory / 'COMPLETED.json').read_bytes())
            assert record['status'] == 'PASS_NATIVE_SCDRS_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY'
            assert record['compiler'] == tag
            for name, wanted in record['source_pins'].items():
                assert pin(ROOT / name) == wanted, name
            for artifact in record['artifacts'].values():
                assert pin(ROOT / artifact['path']) == {k: artifact[k] for k in ['bytes', 'sha256']}
            build_records[tag] = record
        intake = BASE / 'source-intake-1'
        captures = {}
        for version, commit in VERSIONS.items():
            profile = setup['source_inventory'][version]
            assert profile['commit'] == commit
            for name, wanted in profile['files'].items():
                assert pin(intake / version / 'source' / name) == wanted, (version, name)
            capture = out / ('reference-' + version)
            run('reference-' + version, clean + [setup['python'], linux(ROOT / 'tools/literature/capture_scdrs_native_reference.py'),
                '--source', linux(intake / version / 'source'), '--out', linux(capture)])
            record = json.loads((capture / 'COMPLETED.json').read_bytes())
            assert record['version'] == version and len(record['cases']) == 7
            assert sum(c['actual_score_cell'] for c in record['cases']) == 3
            captures[version] = record
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        junit = []
        for group, artifact, version in [('org.junit.jupiter', 'junit-jupiter-api', '5.13.4'), ('org.junit.jupiter', 'junit-jupiter-engine', '5.13.4'),
            ('org.junit.platform', 'junit-platform-engine', '1.13.4'), ('org.junit.platform', 'junit-platform-launcher', '1.13.4'),
            ('org.junit.platform', 'junit-platform-commons', '1.13.4'), ('org.apiguardian', 'apiguardian-api', '1.1.2'), ('org.opentest4j', 'opentest4j', '1.3.0')]:
            matches = list((cache / group / artifact / version).glob('*/*.jar'))
            assert len(matches) == 1
            target = out / 'junit' / matches[0].name
            target.parent.mkdir(exist_ok=True)
            target.write_bytes(matches[0].read_bytes())
            junit.append(target)
        runner = out / 'ScdrsTestRunner.java'
        runner.write_text(RUNNER, encoding='utf-8', newline='\n')
        classes = out / 'junit-classes'
        classes.mkdir()
        jdk21 = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
        api21 = ROOT / build_records['javac21']['artifacts']['api']['path']
        run('junit-compile', [str(jdk21 / 'javac.exe'), '--release', '17', '-sourcepath', '', '-Xlint:all', '-Werror',
            '-encoding', 'UTF-8', '-cp', ';'.join(map(str, [api21, DEPENDENCY, *junit])), '-d', str(classes),
            str(ROOT / 'src/test/java/org/jlinalg/genetics/ScdrsScoresTest.java'), str(runner)], timeout=120)
        summaries = []

        def f32(x):
            return struct.unpack('>f', struct.pack('>f', x))[0]

        def ulps(x, y):
            # Ordered bits for finite float32, treating signed zero equally.
            def bits(v):
                i = struct.unpack('>i', struct.pack('>f', v))[0]
                return 0x80000000 - i if i < 0 else i
            return 0 if x == y else abs(bits(x) - bits(y))

        for platform, tag, java in [('windows21', 'javac21', [str(jdk21 / 'java.exe')]),
            ('windows25', 'javac25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']),
            ('linux21', 'javac21', ['wsl.exe', '-d', setup['distribution'], '--', '/usr/bin/java'])]:
            record = build_records[tag]
            api = ROOT / record['artifacts']['api']['path']
            consumer = ROOT / record['artifacts']['consumer']['path']
            convert = linux if platform == 'linux21' else str
            sep = ':' if platform == 'linux21' else ';'
            cp = sep.join(convert(f) for f in [consumer, api, DEPENDENCY])
            compared = 0
            max_scaled = 0
            max_ulps = 0
            for version, capture in captures.items():
                for case in capture['cases']:
                    directory = out / ('reference-' + version)
                    output = out / (platform + '-' + version + '-' + case['case'] + '.tsv')
                    run(output.stem, java + ['-cp', cp, 'org.jlinalg.cli.ScdrsReferenceProbe',
                        convert(directory / case['input']), convert(output)], timeout=120)
                    expected = json.loads((directory / case['expected']).read_bytes())
                    seen = set()
                    with output.open(newline='') as stream:
                        for row in csv.DictReader(stream, delimiter='\t'):
                            kind, i, j = row['kind'], int(row['row']), int(row['column'])
                            key = kind, i, j
                            assert key not in seen
                            seen.add(key)
                            e = expected[kind][i][j] if isinstance(expected[kind][i], list) else expected[kind][i]
                            value = float.fromhex(row['value_hex'])
                            assert math.isfinite(value) and math.isfinite(e)
                            if kind == 'scores':
                                error = ulps(value, e)
                                max_ulps = max(max_ulps, error)
                                assert error <= 2, (platform, version, case['case'], key, value, e, error)
                                if j in [2, 3]:
                                    assert value == e, ('discrete source probability changed', platform, version, case['case'], key, value, e)
                            elif kind in ['group', 'correlation'] and i > 0:
                                assert f32(value) == e, ('downstream float32 differs', platform, version, case['case'], key, value, e)
                            else:
                                error = abs(value - e) / max(1, abs(value), abs(e))
                                max_scaled = max(max_scaled, error)
                                assert error <= 5e-12, (platform, version, case['case'], key, value, e, error)
                            compared += 1
                    required = sum(sum(len(row) for row in v) if isinstance(v[0], list) else len(v) for v in expected.values())
                    assert len(seen) == required
            run(platform + '-junit', java + ['-cp', sep.join(convert(f) for f in [classes, api, DEPENDENCY, *junit]), 'ScdrsTestRunner'], timeout=120)
            summaries.append(dict(platform=platform, numerical_values_compared=compared, junit_passed=8,
                max_double_scaled_error=max_scaled, max_float32_ulps=max_ulps))
        state.update(status='PASS_NATIVE_SCDRS_COMPONENTS_THREE_HISTORICAL_SOURCES_THREE_JVMS',
            summaries=summaries, original_versions=list(VERSIONS), reference_cases_per_version=7,
            actual_score_cell_references=9, builds=build_records,
            source_capture_helper=pin(ROOT / 'tools/literature/capture_scdrs_native_reference.py'),
            setup_receipt=pin(a.setup / 'COMPLETED.json'))
    except Unavailable as error:
        state.update(status='SKIP3_NATIVE_SCDRS_REFERENCE_UNAVAILABLE', cause=repr(error))
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(canonical) == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause',
        'Numerical components only. Full scDRS adapter and catalog admission remain unfinished.')
        + '\n\nREQUIRED ACTION: Preserve this attempt. Resolve the named source, dependency, capacity or numerical cause; '
        'retry into a NEW path. No partial result is admitted by a failed validation.\n', encoding='utf-8', newline='\n')
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable differential validation assertions')
    raise SystemExit(main())
