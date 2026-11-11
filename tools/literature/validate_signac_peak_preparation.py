# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check native grouped-peak preparation against originals on three actual JAR JVMs."""
import argparse
import concurrent.futures
import json
import os
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import unpack
from validate_deseq2_inference import JAVA21, JAVA25, linux


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    jar = base / 'peaks-preparation-development/omicsmatic-0.3.7-signac-peak-preparation-development.jar'
    parent = base / 'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
        assert len(new.namelist()) == len(set(new.namelist())) and new.testzip() is None
        changed = [n for n in old.namelist() if n not in new.namelist() or old.read(n) != new.read(n)]
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert not changed and added and all(n.startswith('org/jlinalg/sequence/SignacPeakPreparation') for n in added)
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/signac-peak-preparation'
    capture = json.loads((fixture / 'CAPTURE.json').read_bytes())
    assert pin(fixture / 'original-controls.zip') == capture['archive']
    assert pin(fixture / 'original-cases.json') == capture['cases']
    unpack(fixture / 'original-controls.zip', capture)
    libs = sorted((ROOT / 'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'))
    assert len(libs) == 9
    source = ROOT / 'src/test/java/org/jlinalg/cli/SignacPeakPreparationTest.java'
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), source,
        ROOT / 'src/main/java/org/jlinalg/sequence/SignacPeakPreparation.java', *fixture.iterdir(), *libs] if p.is_file()}
    runner = out / 'RunPeakPreparation.java'
    runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunPeakPreparation {
 private RunPeakPreparation() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"SignacPeakPreparation","SignacFragments"})if(!Class.forName("org.jlinalg.sequence."+name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SignacPeakPreparationTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=32||s.getTestsSucceededCount()!=32||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native peak preparation tests");
 }
}
''', encoding='utf8')
    classes = out / 'classes'
    classes.mkdir()

    def command(label, argv):
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=180)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode))
        assert result.returncode == 0, label + ' failed; inspect retained logs'
        return dict(JVM=label, tests=32, passed=32, skipped=0)

    try:
        command('compile', [Path(JAVA21).with_name('javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libs])), '-d', classes, runner, source])
        entries = [jar, classes, ROOT / 'src/test/resources', *libs]
        cp = os.pathsep.join(map(str, entries))
        runs = [('windows-java21', [JAVA21, '-Xmx256m', '-cp', cp, 'RunPeakPreparation', jar]),
                ('windows-java25', [JAVA25, '-Xmx256m', '-cp', cp, 'RunPeakPreparation', jar]),
                ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java',
                                  '-Xmx256m', '-cp', ':'.join(map(linux, entries)), 'RunPeakPreparation', linux(jar)])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(lambda row: command(*row), runs))
        assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        assert all(pin(ROOT / n) == value for n, value in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-native-grouped-peak-preparation-three-JVMs',
                                         artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
                                         parent=pin(parent), changed_parent_entries=changed, added_entries=added,
                                         canonical_executable_preserved=canonical, source_pins=sources,
                                         tests_per_JVM=32, JVMs=results, original_split_cases=7,
                                         actual_MACS_peak_fits=False, catalog_implementation_credit=False))
        print('32 actual-JAR peak-preparation tests passed on each of3 JVMs; parent entries preserved')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_NATIVE_PEAK_PREPARATION_MATRIX', error=str(error)))
        raise


if __name__ == '__main__':
    main()
