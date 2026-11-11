# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check public Signac dispatch and native normalization in the actual JAR on three JVMs."""
import argparse
import concurrent.futures
import os
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux

BASE = ROOT / 'build/literature-review/signac-runtime'
JAR = BASE / 'tools-development/omicsmatic-0.3.7-signac-tools-development.jar'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    artifact = pin(JAR)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    canonical_pin = pin(canonical)
    parent = BASE / 'coverage-tools-development/omicsmatic-0.3.7-signac-coverage-development.jar'
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(JAR) as new:
        assert len(new.namelist()) == len(set(new.namelist())) and new.testzip() is None
        changed = [n for n in old.namelist() if n not in new.namelist() or old.read(n) != new.read(n)]
        added = sorted(set(new.namelist()) - set(old.namelist()))
        save(out / 'parent-entries.json', dict(parent=pin(parent), changed=changed, added=added))
        assert changed == ['org/jlinalg/cli/JLinAlgCli.class'], changed
        assert added and all(n.startswith(('org/jlinalg/cli/SignacNormalizationCli',
            'org/jlinalg/cli/SignacToolsCli', 'org/jlinalg/omics/signac-normalize/',
            'org/jlinalg/omics/signac-tools/')) for n in added), added
    libraries = sorted((ROOT / 'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'))
    assert len(libraries) == 9
    tests = [ROOT / 'src/test/java' / name for name in [
        'org/jlinalg/sequence/SignacNormalizationTest.java',
        'org/jlinalg/sequence/SignacMatrixIOTest.java',
        'org/jlinalg/cli/SignacNormalizationContractsTest.java',
        'org/jlinalg/cli/SignacToolsDispatchTest.java']]
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/signac-normalization/original-controls.zip'
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), *tests, fixture, *libraries]}
    runner = out / 'RunSignacTools.java'
    runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunSignacTools {
 private RunSignacTools() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.SignacNormalization","org.jlinalg.sequence.SignacMatrixIO","org.jlinalg.cli.SignacNormalizationCli","org.jlinalg.cli.SignacToolsCli","org.jlinalg.cli.JLinAlgCli"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SignacNormalizationTest"),selectClass("org.jlinalg.sequence.SignacMatrixIOTest"),selectClass("org.jlinalg.cli.SignacNormalizationContractsTest"),selectClass("org.jlinalg.cli.SignacToolsDispatchTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=1679||s.getTestsSucceededCount()!=1679||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete Signac tools tests");
 }
}
''', encoding='utf8', newline='\n')
    classes = out / 'classes'
    classes.mkdir()

    def command(label, arguments):
        argv = list(map(str, arguments))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=360)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=process.returncode))
        assert process.returncode == 0, label + ': inspect retained logs'
        return dict(JVM=label, tests=1679, passed=1679, skipped=0)

    try:
        command('compile', [Path(JAVA21).with_name('javac.exe'), '--release', '17', '-encoding', 'UTF-8',
            '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [JAR, *libraries])), '-d', classes, runner, *tests])
        entries = [JAR, classes, ROOT / 'src/test/resources', *libraries]
        cp = os.pathsep.join(map(str, entries))
        commands = [('windows-java21', [JAVA21, '-Xmx768m', '-cp', cp, 'RunSignacTools', JAR]),
            ('windows-java25', [JAVA25, '-Xmx768m', '-cp', cp, 'RunSignacTools', JAR]),
            ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java',
                '-Xmx768m', '-cp', ':'.join(map(linux, entries)), 'RunSignacTools', linux(JAR)])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(lambda item: command(*item), commands))
        assert pin(JAR) == artifact and pin(canonical) == canonical_pin
        assert all(pin(ROOT / name) == value for name, value in pins.items())
        save(out / 'COMPLETED.json', dict(status='passed-actual-JAR-Signac-tools-three-JVMs',
            artifact=artifact, source_pins=pins, tests_per_JVM=1679, JVMs=results,
            parent_existing_entries_changed=changed, prior_scientific_classes_preserved=True,
            canonical_executable_preserved=canonical_pin, historical_whole_packages=False,
            original_runtime_workflow_matrix_required_separately=True))
        print('1679 actual-JAR Signac tools tests passed on each of3 JVMs; only existing root dispatcher class changed')
    except Exception as failure:
        save(out / 'FAILURE.json', dict(status='FAIL2_SIGNAC_TOOLS_NATIVE_MATRIX', error=str(failure)))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable packaged source/contract checks')
    main()
