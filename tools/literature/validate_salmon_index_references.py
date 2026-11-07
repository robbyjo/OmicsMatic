# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check complete original Salmon reference tables and all prior audits against an actual JAR on three JVMs."""
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
        raise RuntimeError('Never disable complete reference-table checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir()
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    runtime = json.loads((BASE / 'runtime-admission-development-1/control-3/COMPLETED.json').read_bytes())
    assert runtime['selected_editions'] == ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'] and len(runtime['original_admissions']) == 5
    parent = ROOT / runtime['artifact']['path']
    assert pin(parent) == {k: runtime['artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [name for name in old.namelist() if name.endswith('.class')]
        assert all(current.read(name) == old.read(name) for name in legacy)
        added = [name for name in current.namelist() if name.endswith('.class') and name not in legacy]
        assert len(added) == 8 and all(name.startswith('org/jlinalg/sequence/SalmonIndexReferences') for name in added)
    names = ['src/main/java/org/jlinalg/sequence/SalmonIndexReferences.java', 'src/test/java/org/jlinalg/sequence/SalmonIndexReferencesTest.java',
             'src/main/resources/META-INF/NOTICE-salmon-index.txt', 'tools/literature/freeze_salmon_index_reference_fixtures.py',
             'src/test/resources/org/jlinalg/sequence/salmon-index-references/original-reference-tables.zip', 'src/test/resources/org/jlinalg/sequence/salmon-index-references/provenance.json',
             Path(__file__).relative_to(ROOT).as_posix()]
    sources = dict(runtime['source_pins'])
    sources.update({name: pin(ROOT / name) for name in names})
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'SOURCE-PINS.json', sources)
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonIndexReferenceChecks.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonIndexReferenceChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.SalmonIndexReferences","org.jlinalg.cli.SalmonRuntime","org.jlinalg.cli.SalmonHealth","org.jlinalg.sequence.SalmonQuantification","org.jlinalg.sequence.SalmonInferentialReplicates"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SalmonIndexReferencesTest"),selectClass("org.jlinalg.cli.SalmonHealthTest"),selectClass("org.jlinalg.cli.SalmonSetupEvidenceTest"),selectClass("org.jlinalg.sequence.SalmonQuantificationTest"),selectClass("org.jlinalg.sequence.SalmonInferentialReplicatesTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=203||s.getTestsSucceededCount()!=203||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native Salmon reference checks");
  System.out.println("COMPLETE_SALMON_CASES 203 ORIGINAL_REFERENCE_TABLES 5 REFERENCES 205 POSITIONS 220 NO_SKIPS");
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

    tests = [ROOT / 'src/test/java/org/jlinalg/sequence/SalmonIndexReferencesTest.java', ROOT / 'src/test/java/org/jlinalg/cli/SalmonHealthTest.java',
             ROOT / 'src/test/java/org/jlinalg/cli/SalmonSetupEvidenceTest.java', ROOT / 'src/test/java/org/jlinalg/sequence/SalmonQuantificationTest.java',
             ROOT / 'src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java']
    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *tests])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                 ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

    def check(spec):
        name, java, path, separator = spec
        receipt = command(name, [*java, '-Xmx128m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(path, paths)), 'SalmonIndexReferenceChecks', path(jar)])
        assert 'COMPLETE_SALMON_CASES 203 ORIGINAL_REFERENCE_TABLES 5 REFERENCES 205 POSITIONS 220 NO_SKIPS' in (out / (name + '.stdout')).read_text()
        print(name + ':203 actual-JAR cases and complete original reference tables pass without skips', flush=True)
        return dict(name=name, tests=203, passed=203, index_reference_tests=41, previous_audit_tests=162, original_reference_tables=5, complete_references=205, complete_positions=220, process=receipt)

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        runs = list(pool.map(check, platforms))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-three-JVM-complete-original-Salmon-reference-table-decoding', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          runs=runs, source_pins=sources, unchanged_parent_classes=len(legacy), new_classes=added, canonical_executable_unchanged=canonical,
          scope='Complete original Cereal names/extensions/positions and complete original/normalized length vectors; exact UTF-8 names/order, unsigned values, orientation, ID projection, '
                'start bounds, full EOF, file hashes, capacities/cancellation and staged-consumer/closure behavior. Five original indexes reuse one41-reference synthetic population. '
                'No whole-graph, contig-end, raw-FASTA equivalence, decoy/exclusion ledger, original edge protocol, public quantification or biological certificate is claimed.',
          public_quantification_adapter_available=False, user_analysis_admitted=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
