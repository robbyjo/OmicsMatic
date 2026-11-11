# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate exact metadata and separate declared/source Salmon target ledgers in actual JARs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete source/declared population checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir()
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    prior = json.loads((BASE / 'index-reference-development-1/matrix-1/COMPLETED.json').read_bytes())
    parent = ROOT / prior['artifact']['path']
    assert pin(parent) == {k: prior['artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [name for name in old.namelist() if name.endswith('.class')]
        assert all(current.read(name) == old.read(name) for name in legacy)
        added = [name for name in current.namelist() if name.endswith('.class') and name not in legacy]
        assert len(added) == 4 and all(name.startswith(('org/jlinalg/sequence/SalmonDecoyAssignment', 'org/jlinalg/cli/SalmonMetadata')) for name in added)
    names = ['src/main/java/org/jlinalg/cli/SalmonMetadata.java', 'src/main/java/org/jlinalg/sequence/SalmonDecoyAssignment.java',
             'src/main/java/org/jlinalg/cli/NextflowParameters.java', 'src/test/java/org/jlinalg/cli/SalmonMetadataTest.java',
             'src/test/java/org/jlinalg/cli/SalmonDecoyAssignmentTest.java', 'tools/literature/freeze_salmon_index_boundary_fixtures.py',
             'src/test/resources/org/jlinalg/cli/salmon-index-boundaries/original-boundaries.zip', 'src/test/resources/org/jlinalg/cli/salmon-index-boundaries/provenance.json',
             Path(__file__).relative_to(ROOT).as_posix()]
    sources = dict(prior['source_pins'])
    sources.update({name: pin(ROOT / name) for name in names})
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'SOURCE-PINS.json', sources)
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonIndexLedgerChecks.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonIndexLedgerChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.cli.SalmonMetadata","org.jlinalg.sequence.SalmonDecoyAssignment","org.jlinalg.sequence.SalmonIndexReferences","org.jlinalg.cli.SalmonRuntime","org.jlinalg.sequence.SalmonQuantification"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SalmonMetadataTest"),selectClass("org.jlinalg.cli.SalmonDecoyAssignmentTest"),selectClass("org.jlinalg.sequence.SalmonIndexReferencesTest"),selectClass("org.jlinalg.cli.SalmonHealthTest"),selectClass("org.jlinalg.cli.SalmonSetupEvidenceTest"),selectClass("org.jlinalg.sequence.SalmonQuantificationTest"),selectClass("org.jlinalg.sequence.SalmonInferentialReplicatesTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=241||s.getTestsSucceededCount()!=241||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native Salmon population ledger checks");
  System.out.println("COMPLETE_SALMON_CASES 241 DECLARED_SOURCE_BOUNDARY_COUNTEREXAMPLES 3 OMITTED_NAMES_PRESERVED NO_SKIPS");
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

    tests = [ROOT / 'src/test/java/org/jlinalg/cli/SalmonMetadataTest.java', ROOT / 'src/test/java/org/jlinalg/cli/SalmonDecoyAssignmentTest.java',
             ROOT / 'src/test/java/org/jlinalg/sequence/SalmonIndexReferencesTest.java', ROOT / 'src/test/java/org/jlinalg/cli/SalmonHealthTest.java',
             ROOT / 'src/test/java/org/jlinalg/cli/SalmonSetupEvidenceTest.java', ROOT / 'src/test/java/org/jlinalg/sequence/SalmonQuantificationTest.java',
             ROOT / 'src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java']
    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *tests])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                 ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

    def check(spec):
        name, java, path, separator = spec
        receipt = command(name, [*java, '-Xmx128m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(path, paths)), 'SalmonIndexLedgerChecks', path(jar)])
        assert 'COMPLETE_SALMON_CASES 241 DECLARED_SOURCE_BOUNDARY_COUNTEREXAMPLES 3 OMITTED_NAMES_PRESERVED NO_SKIPS' in (out / (name + '.stdout')).read_text()
        print(name + ':241 actual-JAR cases pass; declared/source omissions stay explicit', flush=True)
        return dict(name=name, tests=241, passed=241, metadata_tests=24, source_decoy_tests=14, prior_native_tests=203, original_source_boundary_counterexamples=3, process=receipt)

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        runs = list(pool.map(check, platforms))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-three-JVM-lossless-Salmon-metadata-and-separate-source-declared-populations', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          runs=runs, source_pins=sources, unchanged_parent_classes=len(legacy), new_classes=added, canonical_executable_unchanged=canonical,
          scope='Exact original JSON numeric tokens including uint64 sentinel18446744073709551615; source classifier uses short-reference count before the current record. '
                'Three original1.8.0 tables match the complete source-as-run population/arithmetic while all three requested-population coverage failures remain explicit. '
                'No omitted row is imputed or relabeled as a protected declared decoy. Source-as-run consistency is not complete requested-population admission.',
          public_quantification_adapter_available=False, user_analysis_admitted=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
