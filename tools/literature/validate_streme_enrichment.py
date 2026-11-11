# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an additive development JAR and run exact Fisher/original-output checks on three JVMs."""
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


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    parent_record = json.loads((ROOT / 'docs/literature/htseq-tools-development/VALIDATION.json').read_bytes())
    parent = ROOT / parent_record['artifact']['path']
    assert pin(parent) == {k: parent_record['artifact'][k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    main_sources = [ROOT / 'src/main/java/org/jlinalg/sequence' / n for n in ['StremeEnrichment.java', 'StremeFisherAudit.java']]
    test = ROOT / 'src/test/java/org/jlinalg/cli/StremeEnrichmentTest.java'
    paths = [*main_sources, test, Path(__file__), ROOT / 'tools/literature/freeze_streme_enrichment_fixtures.py']
    paths += list((ROOT / 'src/test/resources/org/jlinalg/sequence/streme-enrichment').iterdir())
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())

    def run(name, command, timeout=900):
        command = list(map(str, command))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout)
        receipt = dict(command=command, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        if result.returncode:
            raise RuntimeError(name + ' failed: inspect retained stdout/stderr in ' + str(out))
        return receipt

    try:
        classes = out / 'scientific-classes'
        classes.mkdir()
        run('compile-scientific', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                                   '-cp', parent, '-d', classes, *main_sources])
        compiled = {p.relative_to(classes).as_posix(): p for p in classes.rglob('*.class')}
        expected = {'org/jlinalg/sequence/StremeEnrichment' + suffix + '.class' for suffix in ['', '$Table', '$Limits', '$FisherTail', '$Work']}
        expected |= {'org/jlinalg/sequence/StremeFisherAudit' + suffix + '.class' for suffix in ['', '$UnsupportedInput', '$Limits', '$Motif', '$Audit']}
        assert set(compiled) == expected
        artifact = out / 'omicsmatic-0.3.7-streme-enrichment-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(artifact, 'x') as new:
            assert set(compiled).isdisjoint(old.namelist())
            for item in old.infolist():
                new.writestr(item, old.read(item.filename))
            for name, path in sorted(compiled.items()):
                new.writestr(name, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(artifact) as new:
            assert len(new.namelist()) == len(set(new.namelist())) and new.testzip() is None
            assert all(old.read(n) == new.read(n) for n in old.namelist())
            assert set(new.namelist()) - set(old.namelist()) == expected
            for name, path in compiled.items():
                assert path.read_bytes() == new.read(name)
            unchanged_entries = len(old.namelist())
        libraries = sorted(LIBS.glob('*.jar'))
        assert len(libraries) == 9
        tests = out / 'test-classes'
        tests.mkdir()
        runner = out / 'StremeEnrichmentChecks.java'
        runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class StremeEnrichmentChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.StremeEnrichment","org.jlinalg.sequence.StremeFisherAudit","org.jlinalg.sequence.MemeXml"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.StremeEnrichmentTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=2220||s.getTestsSucceededCount()!=2220||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete STREME statistical component contracts");
  System.out.println("STREME_ENRICHMENT_2220_PASSED_NO_SKIPS_COMPONENT_ONLY");
 }
}
''', encoding='utf-8', newline='\n')
        run('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                              '-cp', os.pathsep.join(map(str, [artifact, *libraries])), '-d', tests, runner, test])
        cp = [tests, artifact, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep),
                     ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

        def platform_run(spec):
            name, java, convert, separator = spec
            receipt = run(name, [*java, '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, cp)),
                                 'StremeEnrichmentChecks', convert(artifact)])
            assert 'STREME_ENRICHMENT_2220_PASSED_NO_SKIPS_COMPONENT_ONLY' in (out / (name + '.stdout')).read_text()
            print(name + ': 2220 exact-source actual-JAR statistical checks passed', flush=True)
            return dict(name=name, tests=2220, succeeded=2220, skipped=0, process=receipt)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            runs = list(pool.map(platform_run, platforms))
        assert all(pin(ROOT / name) == wanted for name, wanted in sources.items())
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        write(out / 'COMPLETED.json', dict(schema_version=1, status='passed-exact-STREME-Fisher-statistical-component',
              artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)), parent_artifact=parent_record['artifact'],
              source_pins=sources, runs=runs, new_exact_source_classes=len(compiled), unchanged_parent_entries=unchanged_entries,
              independent_exact_tables=2182, original_XML_documents=12, supported_Fisher_documents=8,
              independently_checked_original_tables=28, unsupported_original_objectives=4,
              original_editions=['5.5.4', '5.5.9'], canonical_executable_unchanged=canonical,
              discovery_fit_available=False, site_or_score_audit_available=False, full_output_family_admitted=False,
              public_execution_adapter_available=False, implementation_credit=False, released=False, pushed=False, deployed=False,
              required_action='Use this only as an independent exact statistical component. Preserve no-holdout score/sentinel semantics. Complete source-specific input, discovery, site, score, runtime, process and output-family gates before catalog credit.'))
    except Exception as failure:
        write(out / 'FAILURE.json', dict(status='failed-STREME-statistical-component-no-result-admitted', error=str(failure),
              source_pins=sources, implementation_credit=False))
        raise


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact-source artifact validation')
    main()
