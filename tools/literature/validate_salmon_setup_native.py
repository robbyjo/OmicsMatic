# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify portable Salmon setup receipts using actual development-JAR classes on three JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete evidence checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    base = (ROOT / 'build/literature-review/salmon-runtime').resolve()
    assert jar.is_relative_to(base) and out.is_relative_to(base) and not out.exists()
    out.mkdir()
    artifact = pin(jar)
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    parent = base / 'quantification-development-1/package-1/omicsmatic-0.3.7-salmon-development.jar'
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [name for name in old.namelist() if name.endswith('.class')]
        assert all(current.read(name) == old.read(name) for name in legacy)
        added = [name for name in current.namelist() if name.endswith('.class') and name not in legacy]
        assert len(added) == 6 and all(name.startswith('org/jlinalg/cli/SalmonSetupEvidence') for name in added)
        kit = ROOT / 'src/main/resources/org/jlinalg/omics/salmon-runtime-setup'
        for path in kit.rglob('*'):
            if path.is_file():
                assert current.read(path.relative_to(ROOT / 'src/main/resources').as_posix()) == path.read_bytes()
    names = ['src/main/java/org/jlinalg/cli/SalmonSetupEvidence.java', 'src/test/java/org/jlinalg/cli/SalmonSetupEvidenceTest.java',
             'src/main/java/org/jlinalg/sequence/SalmonQuantification.java', 'src/test/java/org/jlinalg/sequence/SalmonQuantificationTest.java',
             'src/main/java/org/jlinalg/sequence/SalmonInferentialReplicates.java', 'src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java',
             'tools/literature/freeze_salmon_setup_fixtures.py', 'tools/literature/freeze_salmon_quantification_fixtures.py',
             'tools/literature/salmon-development.init.gradle', Path(__file__).relative_to(ROOT).as_posix(),
             'src/test/resources/org/jlinalg/cli/salmon-setup/original-setup-reports.zip', 'src/test/resources/org/jlinalg/cli/salmon-setup/provenance.json',
             'src/test/resources/org/jlinalg/sequence/salmon-quantification/original-quantification.zip', 'src/test/resources/org/jlinalg/sequence/salmon-quantification/provenance.json']
    names += sorted(path.relative_to(ROOT).as_posix() for path in kit.rglob('*') if path.is_file())
    sources = {name: pin(ROOT / name) for name in names}
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonSetupChecks.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonSetupChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.cli.SalmonSetupEvidence","org.jlinalg.sequence.SalmonQuantification","org.jlinalg.sequence.SalmonInferentialReplicates"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SalmonSetupEvidenceTest"),selectClass("org.jlinalg.sequence.SalmonQuantificationTest"),selectClass("org.jlinalg.sequence.SalmonInferentialReplicatesTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=139||s.getTestsSucceededCount()!=139||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native Salmon setup checks");
  System.out.println("COMPLETE_ORIGINAL_SETUP_REPORTS 6 HEALTH_ROWS 480 BOOTSTRAP_VALUES 36000 NATIVE_CASES 139");
 }
}
''', encoding='utf-8', newline='\n')
    libraries = sorted(LIBS.glob('*.jar'))
    assert len(libraries) == 9

    def command(name, argv):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900)
        receipt = dict(command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        assert result.returncode == 0, (name, result.returncode)
        return receipt

    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])),
                        '-d', classes, runner, ROOT / names[1], ROOT / names[3], ROOT / names[5]])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep),
                 ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                 ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

    def check(spec):
        name, java, path, separator = spec
        receipt = command(name, [*java, '-Xmx128m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(path, paths)), 'SalmonSetupChecks', path(jar)])
        assert 'COMPLETE_ORIGINAL_SETUP_REPORTS 6 HEALTH_ROWS 480 BOOTSTRAP_VALUES 36000 NATIVE_CASES 139' in (out / (name + '.stdout')).read_text()
        print(name + ':139 actual-JAR cases and6 complete original setup reports pass without skips', flush=True)
        return dict(name=name, tests=139, passed=139, setup_tests=52, prior_table_and_replicate_tests=87, original_reports=6,
                    complete_health_rows=480, complete_health_bootstrap_values=36000, process=receipt)

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        runs = list(pool.map(check, platforms))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == row for name, row in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-three-JVM-complete-portable-Salmon-setup-evidence-replay',
          artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact), runs=runs, source_pins=sources,
          unchanged_parent_classes=len(legacy), new_classes=added, canonical_executable_unchanged=canonical,
          scope='Six complete historical setup reports, five exact original editions, all commands/environments/clean exits/worker receipts, '
                'complete namespace and byte census including directories, pinned synthetic inputs, original metadata/index hash bindings, '
                'all480 quantification rows and36000 binary64 bootstrap values replayed through native Java. '
                'Original1.0.0 empty quantification SHA-512 fields are retained and explicitly reported unavailable; they are never filled from index metadata. '
                'Producer Linux paths remain text, including on Windows; no producer home/library/helper is opened. '
                'This verifies internal consistency, not authenticity, whole-index semantics, current-host state, model calibration or biological accuracy.',
          public_quantification_adapter_available=False, scientific_runtime_admitted=False, user_analysis_admitted=False,
          Salmon_catalog_status='missing', whole_paper_reproduced=False))


if __name__ == '__main__':
    main()
