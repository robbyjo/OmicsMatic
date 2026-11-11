# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add exact-source MEME discovery components without changing any parent entry; test on three JVMs."""
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    parent_proof = json.loads((ROOT / 'docs/literature/streme-enrichment-development/VALIDATION.json').read_bytes())
    parent = ROOT / parent_proof['artifact']['path']
    assert pin(parent) == {k: parent_proof['artifact'][k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    main_sources = [ROOT / 'src/main/java/org/jlinalg/sequence' / n for n in ['MemeDiscoveryScoring.java', 'MemeDiscoverySites.java']]
    test = ROOT / 'src/test/java/org/jlinalg/cli/MemeDiscoverySitesTest.java'
    paths = [*main_sources, test, Path(__file__), ROOT / 'tools/literature/freeze_meme_site_fixtures.py',
             ROOT / 'tools/literature/audit_meme_site_references.py', ROOT / 'tools/literature/collect_meme_site_references.py']
    paths += list((ROOT / 'src/test/resources/org/jlinalg/sequence/meme-discovery-sites').iterdir())
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes((ROOT / name).read_bytes())

    def run(name, command):
        command = list(map(str, command))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            p = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900)
        row = dict(command=command, exit_code=p.returncode); write(out / (name + '.json'), row)
        if p.returncode:
            raise RuntimeError(name + ' failed; inspect retained stdout/stderr in ' + str(out))
        return row

    try:
        classes = out / 'scientific-classes'; classes.mkdir()
        run('compile-scientific', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                                  '-cp', parent, '-d', classes, *main_sources])
        compiled = {p.relative_to(classes).as_posix(): p for p in classes.rglob('*.class')}
        expected = {'org/jlinalg/sequence/MemeDiscoveryScoring' + s + '.class' for s in ['', '$Limits', '$Score', '$Hit', '$Tiling', '$Model', '$Work']}
        expected |= {'org/jlinalg/sequence/MemeDiscoverySites' + s + '.class' for s in ['', '$UnsupportedInput', '$Mode', '$Input', '$Expected', '$Limits', '$Site', '$Motif', '$Scan', '$Audit', '$Geometry', '$Budget']}
        assert set(compiled) == expected
        jar = out / 'omicsmatic-0.3.7-meme-discovery-sites-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as new:
            assert expected.isdisjoint(old.namelist())
            for entry in old.infolist():
                new.writestr(entry, old.read(entry.filename))
            for name, file in sorted(compiled.items()):
                new.writestr(name, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            assert len(new.namelist()) == len(set(new.namelist())) and new.testzip() is None
            assert all(old.read(n) == new.read(n) for n in old.namelist())
            assert set(new.namelist()) - set(old.namelist()) == expected
            assert all(new.read(n) == file.read_bytes() for n, file in compiled.items())
            unchanged = len(old.namelist())
        libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9
        tests = out / 'test-classes'; tests.mkdir(); runner = out / 'MemeDiscoverySiteChecks.java'
        runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class MemeDiscoverySiteChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.MemeDiscoveryScoring","org.jlinalg.sequence.MemeDiscoverySites","org.jlinalg.sequence.MemeXml"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.MemeDiscoverySitesTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=94||s.getTestsSucceededCount()!=94||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete complete-population MEME site/scoring contracts");
  System.out.println("MEME_DISCOVERY_SITES_94_PASSED_NO_SKIPS_COMPONENT_ONLY");
 }
}
''', encoding='utf-8', newline='\n')
        run('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                              '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', tests, runner, test])
        cp = [tests, jar, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep),
                     ('windows-java25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

        def platform_run(spec):
            name, java, convert, sep = spec
            receipt = run(name, [*java, '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', sep.join(map(convert, cp)), 'MemeDiscoverySiteChecks', convert(jar)])
            assert 'MEME_DISCOVERY_SITES_94_PASSED_NO_SKIPS_COMPONENT_ONLY' in (out / (name + '.stdout')).read_text()
            print(name + ': 94 actual-JAR MEME complete-site/scoring checks passed', flush=True)
            return dict(name=name, tests=94, succeeded=94, skipped=0, process=receipt)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            runs = list(pool.map(platform_run, platforms))
        assert all(pin(ROOT / n) == v for n, v in sources.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        write(out / 'COMPLETED.json', dict(schema_version=1, status='passed-exact-source-MEME-discovery-site-and-scoring-components',
              artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), parent_artifact=parent_proof['artifact'], source_pins=sources,
              runs=runs, exact_new_classes=19, unchanged_parent_entries=unchanged, canonical_executable_unchanged=canonical,
              original_families=42, original_motifs=84, contributing_sites=4136, sequence_diagrams=3040, selected_scan_hits=892,
              complete_independent_null_tail_cells=64284, runtime_profiles=['4.9.1-base', '4.9.1-patch2', '5.5.4', '5.5.9'],
              native_EM_fit_available=False, motif_significance_recomputed=False, full_report_family_admitted=False,
              public_execution_adapter_available=False, implementation_credit=False, released=False, pushed=False, deployed=False))
    except Exception as failure:
        write(out / 'FAILURE.json', dict(status='failed-MEME-discovery-site-component-validation-no-result-admitted', error=str(failure), source_pins=sources, implementation_credit=False))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact-source packaged validation')
    main()
