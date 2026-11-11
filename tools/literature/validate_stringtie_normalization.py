# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check exact new normalization classes in a private JAR on three JVMs; preserve all frozen parent entries."""
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
from validate_stringtie_gtf import save


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True); save(out / 'INCOMPLETE.json', dict(status='in-progress-normalization-validation'))
    try:
        previous = json.loads((ROOT / 'docs/literature/stringtie-tables-development/VALIDATION.json').read_bytes())
        assert all(pin(ROOT / name) == value for name, value in previous['source_pins'].items())
        parent = ROOT / previous['artifact']['path']; assert pin(parent) == {k: previous['artifact'][k] for k in ['bytes', 'sha256']}
        libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9
        assert all(pin(ROOT / name) == value for name, value in previous['dependency_pins'].items())
        sources = dict(previous['source_pins'])
        names = ['src/main/java/org/jlinalg/sequence/StringTieNormalization.java', 'src/test/java/org/jlinalg/sequence/StringTieNormalizationTest.java',
                 'tools/literature/validate_stringtie_normalization.py', 'tools/literature/collect_stringtie_normalization.py', 'tools/literature/freeze_stringtie_normalization_fixtures.py']
        names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-normalization').iterdir()) if p.is_file()]
        sources.update({name: pin(ROOT / name) for name in names}); save(out / 'SOURCE-PINS.json', sources)
        for name in sources:
            target = out / 'source-snapshot' / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes((ROOT / name).read_bytes())

        def command(name, argv, timeout=180):
            argv = list(map(str, argv))
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            save(out / (name + '.process.json'), dict(command=argv, exit_code=process.returncode)); assert process.returncode == 0, (name, process.returncode)

        compiled = out / 'source-compiled'; compiled.mkdir()
        command('compile-exact-source', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', parent, '-d', compiled, ROOT / names[0]])
        classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob('*.class')}
        assert classes and all(n.startswith('org/jlinalg/sequence/StringTieNormalization') and n.endswith('.class') for n in classes)
        jar = out / 'omicsmatic-0.3.7-stringtie-normalization-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as new:
            assert not set(classes).intersection(old.namelist())
            for info in old.infolist(): new.writestr(info, old.read(info.filename))
            for name, path in sorted(classes.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16; new.writestr(info, path.read_bytes())
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
            assert set(new.namelist()) - set(old.namelist()) == set(classes) and all(old.read(n) == new.read(n) for n in old.namelist())
            assert all(new.read(n) == p.read_bytes() for n, p in classes.items()); unchanged = len(old.namelist())
        runner = out / 'StringTieNormalizationChecks.java'
        runner.write_text('''import java.io.File;import java.io.PrintWriter;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class StringTieNormalizationChecks {public static void main(String[] args)throws Exception {
for(Class<?> type:new Class<?>[]{org.jlinalg.sequence.StringTieGtf.class,org.jlinalg.sequence.StringTieTables.class,org.jlinalg.sequence.StringTieNormalization.class})if(!type.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual normalization JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.StringTieGtfTest"),selectClass("org.jlinalg.sequence.StringTieTablesTest"),selectClass("org.jlinalg.sequence.StringTieNormalizationTest")).build(),listener);
var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=1256||s.getTestsSucceededCount()!=1256||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete normalization checks");
System.out.println("COMPLETE_STRINGTIE_NORMALIZATION_CHECKS 1256 NO_SKIPS");}}
''', encoding='utf-8', newline='\n')
        tests = out / 'test-classes'; tests.mkdir()
        command('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])),
                                  '-d', tests, runner, ROOT / names[1], ROOT / 'src/test/java/org/jlinalg/sequence/StringTieTablesTest.java', ROOT / 'src/test/java/org/jlinalg/sequence/StringTieGtfTest.java'])
        command('javadoc', [JAVA21 / 'javadoc.exe', '-quiet', '-Xdoclint:all,-missing', '-Werror', '-encoding', 'UTF-8', '-classpath', jar, '-d', out / 'javadoc', ROOT / names[0]])
        classpath = [tests, jar, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

        def test(spec):
            name, java, convert, separator = spec
            command(name, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, classpath)), 'StringTieNormalizationChecks', convert(jar)])
            assert 'COMPLETE_STRINGTIE_NORMALIZATION_CHECKS 1256 NO_SKIPS' in (out / (name + '.stdout')).read_text()
            return dict(name=name, tests=1256, passed=1256, skipped=0)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool: runs = list(pool.map(test, platforms))
        assert all(pin(ROOT / name) == value for name, value in sources.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical_executable_unchanged']
        result = dict(schema_version=1, status='passed-native-StringTie-edition-finalization-and-conservative-transcript-normalization-audits', source_pins=sources, dependency_pins=previous['dependency_pins'],
                      artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), parent_artifact=previous['artifact'],
                      parent_validation=pin(ROOT / 'docs/literature/stringtie-tables-development/VALIDATION.json'), parent_archive=pin(ROOT / 'docs/literature/stringtie-tables-development/controls.zip'),
                      new_classes={name: pin(path) for name, path in sorted(classes.items())}, exact_new_source_classes=len(classes), unchanged_parent_entries=unchanged,
                      platform_validation=runs, extracted_source_arithmetic_cases=1008, whole_tool_table_families=35, new_whole_StringTie_runs=0,
                      strict_Java17_source=True, javadoc_passed=True, canonical_executable_unchanged=previous['canonical_executable_unchanged'],
                      public_workflow_available=False, implementation_credit=False, exact_hidden_denominators_recovered=False, gene_inference_certified=False, biological_accuracy_certified=False,
                      released=False, pushed=False, deployed=False, whole_goal_complete=False,
                      remaining='Native exact edition arithmetic and conservative transcript readback/common-factor consistency. Bounds are not recovered denominators or confidence intervals. Zero/omitted cases remain unresolved explicitly. Alignment/count/coverage and gene-inference audits, exact runtime setup/admission, typed workflows, portable verification and catalog gates remain; StringTie stays missing.')
        save(out / 'COMPLETED.json', result); (out / 'INCOMPLETE.json').unlink()
        print(json.dumps({k: result[k] for k in ['status', 'artifact', 'exact_new_source_classes', 'unchanged_parent_entries', 'platform_validation']})); return 0
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='failed-normalization-validation-no-admitted-result', error_type=type(error).__name__, error=str(error),
                                      action='Read INCOMPLETE.json and original process/stdout/stderr. Preserve this attempt and partial candidate, correct the cause and repeat into NEW paths. No validation or integration result is admitted.'))
        (out / 'WARNINGS.md').write_text('# FAIL2: STRINGTIE NORMALIZATION VALIDATION FAILED\n\n' + str(error) + '\n\nPreserve this attempt, read FAILURE.json and original logs, correct the cause and retry into NEW paths. No result is admitted.\n', encoding='utf-8', newline='\n'); raise


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete original-edition normalization validation')
    raise SystemExit(main())
