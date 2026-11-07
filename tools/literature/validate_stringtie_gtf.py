# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package exact Java17 GTF-audit classes and run all checks from that JAR on three JVMs."""
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


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    save(out / 'INCOMPLETE.json', dict(status='in-progress-exact-source-native-GTF-validation'))
    try:
        libraries = sorted(LIBS.glob('*.jar'))
        if len(libraries) != 9 or not (JAVA21 / 'javac.exe').is_file():
            (out / 'WARNINGS.md').write_text('# SKIP3: NATIVE GTF VALIDATION DEPENDENCY UNAVAILABLE\n\nProvide the existing nine-library JUnit closure and JDK21, Java25 and WSL Java21 in a permitted environment. Skip dependent validation and retry into NEW paths. No success is admitted.\n', encoding='utf-8', newline='\n')
            return 3
        source_names = ['src/main/java/org/jlinalg/sequence/StringTieGtf.java', 'src/test/java/org/jlinalg/sequence/StringTieGtfTest.java',
                        'tools/literature/validate_stringtie_gtf.py', 'tools/literature/validate_mashr_native.py']
        source_names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'src/test/resources/org/jlinalg/sequence/stringtie-gtf').iterdir()) if p.is_file()]
        sources = {name: pin(ROOT / name) for name in source_names}
        save(out / 'SOURCE-PINS.json', sources)
        for name in sources:
            target = out / 'source-snapshot' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        dependency_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in libraries}
        parent_proof = json.loads((ROOT / 'docs/literature/bigwig-typed-workflow/VALIDATION.json').read_bytes())
        parent = ROOT / parent_proof['artifact']['path']
        assert pin(parent) == {k: parent_proof['artifact'][k] for k in ('bytes', 'sha256')}
        canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
        canonical_pin = pin(canonical)
        assert canonical_pin == parent_proof['canonical_executable_unchanged']

        def command(name, argv, timeout=120):
            argv = list(map(str, argv))
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            save(out / (name + '.process.json'), dict(command=argv, exit_code=process.returncode))
            assert process.returncode == 0, (name, process.returncode)

        compiled = out / 'source-compiled'
        compiled.mkdir()
        command('compile-exact-source', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                                         '-d', compiled, ROOT / source_names[0]])
        classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob('*.class')}
        assert classes and all(n.startswith('org/jlinalg/sequence/StringTieGtf') and n.endswith('.class') for n in classes)
        jar = out / 'omicsmatic-0.3.7-stringtie-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as new:
            assert len(old.namelist()) == len(set(old.namelist())) and not set(classes).intersection(old.namelist())
            for info in old.infolist():
                new.writestr(info, old.read(info.filename))
            for name, path in sorted(classes.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                new.writestr(info, path.read_bytes())
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
            assert set(new.namelist()) - set(old.namelist()) == set(classes)
            assert all(old.read(n) == new.read(n) for n in old.namelist())
            assert all(new.read(n) == p.read_bytes() for n, p in classes.items())
            unchanged = len(old.namelist())
        runner = out / 'StringTieGtfChecks.java'
        runner.write_text('''import java.io.File;import java.io.PrintWriter;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class StringTieGtfChecks {public static void main(String[] args)throws Exception {
if(!org.jlinalg.sequence.StringTieGtf.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual GTF-audit JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.StringTieGtfTest")).build(),listener);
var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=92||s.getTestsSucceededCount()!=92||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native GTF checks");
System.out.println("COMPLETE_STRINGTIE_GTF_CHECKS 92 NO_SKIPS");}}
''', encoding='utf-8', newline='\n')
        test_classes = out / 'test-classes'
        test_classes.mkdir()
        command('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                                  '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', test_classes,
                                  ROOT / source_names[1], runner])
        command('javadoc', [JAVA21 / 'javadoc.exe', '-quiet', '-Xdoclint:all,-missing', '-Werror', '-encoding', 'UTF-8',
                            '-d', out / 'javadoc', ROOT / source_names[0]])
        classpath = [test_classes, jar, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep),
                     ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

        def test(platform_spec):
            name, java, convert, separator = platform_spec
            command(name, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, classpath)),
                           'StringTieGtfChecks', convert(jar)])
            assert 'COMPLETE_STRINGTIE_GTF_CHECKS 92 NO_SKIPS' in (out / (name + '.stdout')).read_text()
            return dict(name=name, tests=92, passed=92, skipped=0)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            runs = list(pool.map(test, platforms))
        assert pin(canonical) == canonical_pin and all(pin(ROOT / name) == value for name, value in sources.items())
        assert all(pin(ROOT / name) == value for name, value in dependency_pins.items())
        proof = dict(schema_version=1, status='passed-exact-source-native-StringTie-GTF-structure-and-reference-audits',
                     source_pins=sources, dependency_pins=dependency_pins,
                     artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)),
                     parent_artifact=parent_proof['artifact'], unchanged_parent_entries=unchanged,
                     new_classes={name: pin(path) for name, path in sorted(classes.items())},
                     exact_source_classes=len(classes), platform_validation=runs,
                     original_GTF_protocols=41, retained_original_failed_protocols=8,
                     strict_Java17_source=True, javadoc_passed=True, canonical_executable_unchanged=canonical_pin,
                     public_workflow_available=False, implementation_credit=False, released=False, pushed=False,
                     deployed=False, whole_goal_complete=False,
                     remaining='Native GTF structure/reference foundation only. Exact original runtime setup/admission, complete alignment/abundance/gene/Ballgown audits, typed CLI, portable captured-workflow verification and catalog/UI gates remain. No StringTie integration credit or complete-paper/historical-edition equivalence.')
        save(out / 'COMPLETED.json', proof)
        (out / 'INCOMPLETE.json').unlink()
        print(json.dumps({k: proof[k] for k in ['status', 'artifact', 'exact_source_classes', 'unchanged_parent_entries', 'platform_validation', 'original_GTF_protocols']}))
        return 0
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='failed-native-StringTie-GTF-validation-no-admitted-result', error_type=type(error).__name__, error=str(error),
                                      action='Read the named process/stdout/stderr and INCOMPLETE.json. Preserve this attempt and partial candidate; correct the source, runtime or capacity cause and repeat into a NEW --out directory. Do not use this attempt as integration or analysis validation.'))
        (out / 'WARNINGS.md').write_text('# FAIL2: NATIVE GTF VALIDATION FAILED\n\n' + str(error) + '\n\nPreserve this attempt, inspect FAILURE.json and original logs, correct the cause and retry into NEW paths. No result is admitted.\n', encoding='utf-8', newline='\n')
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete exact-source GTF validation')
    raise SystemExit(main())
