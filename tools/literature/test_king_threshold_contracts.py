# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run exact decimal KING boundary and existing native JUnit/reference contracts with cached JUnit and isolated compilers."""
import argparse, json, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'
RUNNER = '''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class ExactKingThresholdTestRunner {
  private ExactKingThresholdTestRunner() { }
  public static void main(String[] args) {
    var request = LauncherDiscoveryRequestBuilder.request()
      .selectors(DiscoverySelectors.selectClass("org.jlinalg.genetics.KingRobustKinshipTest"), DiscoverySelectors.selectClass("org.jlinalg.genetics.KingKinshipThresholdTest")).build();
    var listener = new SummaryGeneratingListener();
    var launcher = LauncherFactory.create(); launcher.registerTestExecutionListeners(listener);
    launcher.execute(request); var summary = listener.getSummary();
    summary.printTo(new PrintWriter(System.out, true)); summary.printFailuresTo(new PrintWriter(System.out, true));
    if (summary.getTestsFoundCount()!=19 || summary.getTestsSucceededCount()!=19 || summary.getTestsSkippedCount()!=0
        || summary.getTestsFailedCount()!=0 || summary.getContainersFailedCount()!=0) System.exit(2);
  }
}
'''


def linux(path):
    text = str(path.resolve()).replace('\\', '/')
    assert text[1:3] == ':/'
    return '/mnt/'+text[0].lower()+text[2:]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_EXACT_KING_THRESHOLD_CONTRACTS', new_catalog_credit=False, whole_goal_complete=False)
    try:
        before = pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        cache = Path('C:/Users/User/.gradle/caches/modules-2/files-2.1')
        dependencies = []
        for group, artifact, version in [('org.junit.jupiter', 'junit-jupiter-api', '5.13.4'),
            ('org.junit.jupiter', 'junit-jupiter-engine', '5.13.4'),
            ('org.junit.platform', 'junit-platform-engine', '1.13.4'),
            ('org.junit.platform', 'junit-platform-launcher', '1.13.4'),
            ('org.junit.platform', 'junit-platform-commons', '1.13.4'),
            ('org.apiguardian', 'apiguardian-api', '1.1.2'), ('org.opentest4j', 'opentest4j', '1.3.0')]:
            found = list((cache/group/artifact/version).glob('*/*.jar'))
            assert len(found) == 1, ('Missing exact cached test dependency', artifact, version)
            dependencies.extend(found)
        dependency_pins = {str(path): pin(path) for path in dependencies}
        sources = [ROOT/'src/main/java/org/jlinalg/genetics/KingRobustKinship.java',
            ROOT/'src/test/java/org/jlinalg/genetics/KingRobustKinshipTest.java',
            ROOT/'src/main/java/org/jlinalg/genetics/KingKinshipThreshold.java',
            ROOT/'src/test/java/org/jlinalg/genetics/KingKinshipThresholdTest.java']
        resource = ROOT/'src/test/resources/org/jlinalg/genetics/native-king-original-reference.zip'
        source_pins = {path.relative_to(ROOT).as_posix(): pin(path) for path in [*sources, resource, Path(__file__)]}
        for name in source_pins:
            target = out/'source-snapshot'/name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT/name, target)
        results = []
        classes_by_compiler = {}
        for compiler in [21, 25]:
            folder = out/('javac'+str(compiler))
            folder.mkdir()
            classes = folder/'classes'
            classes.mkdir()
            runner = folder/'ExactKingThresholdTestRunner.java'
            runner.write_text(RUNNER, encoding='utf8', newline='\n')
            tools = Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler == 21 else 'Common Files/Oracle/Java/javapath')
            cp = ';'.join(map(str, dependencies))
            command = [str(tools/'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
                '-cp', cp, '-d', str(classes), *map(str, sources), str(runner)]
            r = subprocess.run(command, capture_output=True, timeout=120)
            (folder/'compile.stdout').write_bytes(r.stdout)
            (folder/'compile.stderr').write_bytes(r.stderr)
            save(folder/'compile.json', dict(command=command, exit_code=r.returncode))
            assert r.returncode == 0, r.stderr[-2500:]
            destination = classes/resource.relative_to(ROOT/'src/test/resources')
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(resource, destination)
            command = [str(tools/'java.exe'), '-Xmx512m', '-cp', str(classes)+';'+cp, 'ExactKingThresholdTestRunner']
            r = subprocess.run(command, capture_output=True, timeout=120)
            (folder/'tests.stdout').write_bytes(r.stdout)
            (folder/'tests.stderr').write_bytes(r.stderr)
            save(folder/'tests.process.json', dict(command=command, exit_code=r.returncode))
            assert r.returncode == 0, r.stdout[-4500:]
            results.append(dict(compiler=compiler, platform='windows'+str(compiler), tests=19, skipped=0))
            classes_by_compiler[compiler] = classes
        command = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java', '-Xmx512m',
            '-cp', ':'.join(linux(path) for path in [classes_by_compiler[21], *dependencies]), 'ExactKingThresholdTestRunner']
        r = subprocess.run(command, capture_output=True, timeout=120)
        (out/'linux21.stdout').write_bytes(r.stdout)
        (out/'linux21.stderr').write_bytes(r.stderr)
        save(out/'linux21.process.json', dict(command=command, exit_code=r.returncode))
        assert r.returncode == 0, r.stdout[-4500:]
        results.append(dict(compiler=21, platform='linux21', tests=19, skipped=0))
        assert all(pin(ROOT/n) == value for n, value in source_pins.items())
        assert all(pin(Path(n)) == value for n, value in dependency_pins.items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == before
        state.update(status='PASS_EXACT_KING_THRESHOLD57_JUNIT_EXECUTIONS_AND2880_ORIGINAL_REFERENCE_PAIR_CHECKS',
            results=results, test_methods=19, test_executions=57, skipped=0, original_reference_pair_checks=2880,
            source_pins=source_pins, dependency_pins=dependency_pins, canonical_executable_unchanged=before,
            scope='Exact decimal strict/inclusive cutoff comparisons and existing between-family count/bit-mask input contracts; no source-output repair, sample selection, whole-tool or biological claim.')
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:3000], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable native contract/reference tests')
    raise SystemExit(main())
