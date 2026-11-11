# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual-JAR complete Salmon quantification input/output admission helpers on three JVMs."""
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
        raise RuntimeError('Never disable complete admission checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir()
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    prior = json.loads((BASE / 'index-workflow-development-1/control-1/COMPLETED.json').read_bytes())
    parent = ROOT / prior['artifact']['path']
    assert pin(parent) == {k: prior['artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [n for n in old.namelist() if n.endswith('.class')]
        assert all(current.read(n) == old.read(n) for n in legacy)
        added = [n for n in current.namelist() if n.endswith('.class') and n not in legacy]
        assert len(added) == 24 and all(n.startswith(tuple('org/jlinalg/cli/' + c for c in ['SalmonQuantRecipe', 'SalmonAlignments', 'SalmonQuantAudit', 'SalmonQuantInputs'])) for n in added)
    tests = sorted([*ROOT.glob('src/test/java/org/jlinalg/cli/Salmon*Test.java'), *ROOT.glob('src/test/java/org/jlinalg/sequence/Salmon*Test.java')])
    assert len(tests) == 15
    sources = dict(prior['source_pins'])
    paths = [*tests, *ROOT.glob('src/main/java/org/jlinalg/cli/Salmon*.java'), Path(__file__), ROOT / 'tools/literature/freeze_salmon_alignment_inputs.py', ROOT / 'tools/literature/freeze_salmon_output_contract_fixtures.py']
    for folder in ['salmon-alignment-inputs', 'salmon-output-contracts']:
        paths.extend(ROOT.glob('src/test/resources/org/jlinalg/cli/' + folder + '/*'))
    for path in paths:
        if path.is_file():
            sources[path.relative_to(ROOT).as_posix()] = pin(path)
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonQuantAdmissionChecks.java'
    selectors = ','.join('selectClass("' + t.relative_to(ROOT / 'src/test/java').as_posix()[:-5].replace('/', '.') + '")' for t in tests)
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonQuantAdmissionChecks {
 public static void main(String[] args)throws Exception {
  for(String n:new String[]{"SalmonQuantRecipe","SalmonAlignments","SalmonQuantAudit","SalmonQuantInputs","SalmonIndexWorkflow","SalmonRuntime"})if(!Class.forName("org.jlinalg.cli."+n).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=425||s.getTestsSucceededCount()!=425||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete Salmon input/output contracts");
  System.out.println("COMPLETE_SALMON_CASES 425 NO_SKIPS NO_USER_FIT_ADMITTED");
 }
}
'''.replace('SELECTORS', selectors), encoding='utf-8', newline='\n')

    def command(name, argv):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900)
        receipt = dict(command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        assert result.returncode == 0, (name, result.returncode)
        return receipt

    libraries = sorted(LIBS.glob('*.jar'))
    assert len(libraries) == 9
    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *tests])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                 ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

    def check(platform):
        name, binary, convert, separator = platform
        receipt = command(name, [*binary, '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, paths)), 'SalmonQuantAdmissionChecks', convert(jar)])
        assert 'COMPLETE_SALMON_CASES 425 NO_SKIPS NO_USER_FIT_ADMITTED' in (out / (name + '.stdout')).read_text()
        print(name + ':425 actual-JAR complete Salmon input/output contract tests passed', flush=True)
        return dict(name=name, tests=425, passed=425, skipped=0, process=receipt)

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        runs = list(pool.map(check, platforms))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-three-JVM-complete-Salmon-quantification-input-output-contracts', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          runs=runs, source_pins=sources, unchanged_parent_classes=len(legacy), added_classes=added, canonical_executable_unchanged=canonical,
          scope='Complete unchanged transcriptome SAM/FASTA and a labeled BAM conversion:24240 physical records,8520 contiguous query groups,40 references. '
          'Twenty-five retained original mapping/alignment output families:1000 complete table rows,31600 complete bootstrap values, independent original assigned counters, exact typed options and source1.0.0 unavailable SHA512 links. '
          'Controlled inputs are reused across editions, not independent biological validation. Native input/output readers do not establish process completion or admit a user fit. '
          'Fresh complete user-fit workflow, public CLI, examples and catalog closure remain open.',
          public_quantification_adapter_available=False, user_analysis_admitted=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
