# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate the packaged native normalization on three JVMs, retaining every run."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import pin, save

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/deseq2-runtime'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
LIBS = ROOT / 'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib'
TESTS = ['org.jlinalg.cli.Deseq2SizeFactorsOriginalTest', 'org.jlinalg.cli.Deseq2NormalizationCliTest']


def linux(path):
    return '/mnt/d/' + Path(path).resolve().relative_to(Path('D:/')).as_posix()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--artifact', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); jar = args.artifact.resolve()
    assert out.is_relative_to(BASE) and not out.exists() and jar.is_relative_to(BASE)
    out.mkdir(); save(out / 'INCOMPLETE.json', {'status': 'native-DESeq2-normalization-validation-in-progress'})
    try:
        canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); artifact = pin(jar)
        libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9
        source_paths = [ROOT / 'src/test/java' / Path(*name.split('.')).with_suffix('.java') for name in TESTS]
        source_paths += [ROOT / 'src/main/java/org/jlinalg/sequence/Deseq2SizeFactors.java',
                         ROOT / 'src/main/java/org/jlinalg/cli/Deseq2NormalizationCli.java', Path(__file__),
                         ROOT / 'tools/literature/collect_deseq2_size_factors.R',
                         ROOT / 'tools/literature/setup_deseq2_reference.py',
                         ROOT / 'tools/literature/install_deseq2_reference.R']
        source_paths += list((ROOT / 'src/test/resources/org/jlinalg/sequence/deseq2-size-factors').glob('*'))
        bound = {p.relative_to(ROOT).as_posix(): pin(p) for p in [*source_paths, *libraries]}
        save(out / 'SOURCE-PINS.json', bound)
        parent = ROOT / 'build/literature-review/signac-runtime/chromvar-public-workflow-1/omicsmatic-0.3.7-chromvar-tools-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            old_names = {n for n in old.namelist() if not n.endswith('/')}
            new_names = {n for n in new.namelist() if not n.endswith('/')}
            changed = [n for n in sorted(old_names & new_names) if old.read(n) != new.read(n)]
            added = sorted(new_names - old_names); removed = sorted(old_names - new_names)
            save(out / 'parent-artifact-comparison.json', dict(parent=pin(parent), changed=changed, added=added, removed=removed))
            assert not changed and not removed, 'An existing packaged file changed; inspect retained comparison'
            assert len(added) == 7 and all(n.startswith(('org/jlinalg/sequence/Deseq2SizeFactors',
                                                       'org/jlinalg/cli/Deseq2NormalizationCli')) and n.endswith('.class') for n in added)
        runner = out / 'RunDeseq2NormalizationChecks.java'
        runner.write_text('''import java.io.PrintWriter;
import java.io.File;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunDeseq2NormalizationChecks {
 private RunDeseq2NormalizationChecks() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.Deseq2SizeFactors","org.jlinalg.cli.Deseq2NormalizationCli"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong JAR loaded: "+name);
  var request=LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build();
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);var summary=listener.getSummary();
  summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=119||summary.getTestsSucceededCount()!=119||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native DESeq2 normalization validation");
 }
}
'''.replace('SELECTORS', ','.join('selectClass("' + name + '")' for name in TESTS)), encoding='utf-8')
        classes = out / 'classes'; classes.mkdir()

        def command(name, argv, expected=0):
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                process = subprocess.run(list(map(str, argv)), cwd=ROOT, stdin=subprocess.DEVNULL,
                                         stdout=stdout, stderr=stderr, timeout=180, check=False)
            record = {'command': list(map(str, argv)), 'exit_code': process.returncode, 'expected_exit_code': expected}
            save(out / (name + '.process.json'), record)
            assert process.returncode == expected, name + ' failed; inspect retained logs'
            return record

        command('compile', [JDK / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                            '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *source_paths[:2]])
        entries = [jar, classes, ROOT / 'src/test/resources', *libraries]
        windows_cp = os.pathsep.join(map(str, entries))
        platforms = [('windows-java21', [str(JDK / 'java.exe'), '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', windows_cp], False),
                     ('windows-java25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', windows_cp], False),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', ':'.join(map(linux, entries))], True)]

        def execute(platform):
            name, argv, is_linux = platform
            tests = command(name + '-tests', [*argv, 'RunDeseq2NormalizationChecks', linux(jar) if is_linux else jar])
            command(name + '-help', [*argv, 'org.jlinalg.cli.Deseq2NormalizationCli', '--help'])
            local = out / name; local.mkdir()
            (local / 'counts.tsv').write_text('gene\tα\t样本\ngα\t1\t4\ngβ\t9\t4\n', encoding='utf-8', newline='\n')
            request = local / 'request.json'
            payload = {'schema_version': 1, 'mode': 'NORMALIZE', 'type': 'ratio', 'counts': 'counts.tsv', 'out': 'analysis'}
            save(request, payload)
            cli = [*argv, 'org.jlinalg.cli.Deseq2NormalizationCli', '--request', linux(request) if is_linux else request]
            command(name + '-main', cli)
            analysis = local / 'analysis'
            result = json.loads((analysis / 'analysis-status.json').read_bytes())
            assert result['status'] == 'completed-with-warnings' and result['usable_for_differential_fit'] and not result['differential_inference_performed']
            before = {p.name: pin(p) for p in analysis.iterdir() if p.is_file()}
            command(name + '-existing-output', cli, 2)
            assert before == {p.name: pin(p) for p in analysis.iterdir() if p.is_file()}
            (local / 'empty-controls.tsv').write_text('gene\n', encoding='utf-8')
            save(request, dict(payload, controls='empty-controls.tsv', out='undefined-analysis'))
            command(name + '-undefined-main', cli)
            undefined = json.loads((local / 'undefined-analysis/analysis-status.json').read_bytes())
            assert undefined['status'] == 'completed-with-unusable-factors' and not undefined['usable_for_differential_fit']
            assert 'UNDEFINED_SIZE_FACTOR' in (local / 'undefined-analysis/WARNINGS.md').read_text(encoding='utf-8')
            (local / 'fractional.tsv').write_text('gene\tx\ty\ng\t1.5\t2\n', encoding='utf-8')
            save(request, dict(payload, counts='fractional.tsv', out='failed-analysis'))
            command(name + '-fractional-main', cli, 2)
            failed = json.loads((local / 'failed-analysis/analysis-status.json').read_bytes())
            assert failed['status'] == 'FAIL2_NO_NORMALIZATION_RESULT_ADMITTED'
            assert '# FAIL2' in (local / 'failed-analysis/WARNINGS.md').read_text(encoding='utf-8')
            print(name + ': 119 tests and 5 actual main protocols passed', flush=True)
            return {'name': name, 'tests': 119, 'passed': 119, 'skipped': 0, 'actual_main_protocols': 5,
                    'test_command': tests, 'existing_output_preserved': True, 'fractional_counts_refused': True,
                    'undefined_factors_retained_and_flagged': True}

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            runs = list(pool.map(execute, platforms))
        assert all(pin(ROOT / path) == expected for path, expected in bound.items())
        assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        record = {'status': 'passed-native-DESeq2-size-factor-subset-actual-JAR-three-JVMs',
                  'artifact': {'path': jar.relative_to(ROOT).as_posix(), **artifact}, 'source_and_fixture_pins': bound,
                  'canonical_artifact_unchanged': canonical, 'original_package_cases': 76, 'tests_per_JVM': 119,
                  'actual_main_protocols': 15, 'runs': runs, 'parent_packaged_files_unchanged': True,
                  'new_classes_only': added, 'root_dispatch_available': False, 'full_DESeq2_inference_available': False,
                  'historical_whole_packages_executed': False, 'catalog_implementation_credit': False,
                  'released': False, 'pushed': False, 'deployed': False, 'whole_goal_complete': False}
        save(out / 'COMPLETED.json', record)
        return 0
    except Exception as error:
        save(out / 'FAILURE.json', {'status': 'FAIL2_NATIVE_DESEQ2_NORMALIZATION_VALIDATION', 'error': str(error)})
        raise


if __name__ == '__main__':
    raise SystemExit(main())
