# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate packaged root Salmon commands with an exact route-only delta from the original-tested candidate."""
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
from catalog_source_integrity import digest, reverse_delta, verify_source


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete root-dispatch checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--platforms', nargs='+', choices=['windows-java21', 'windows-java25', 'linux-java21'], default=['windows-java21', 'windows-java25', 'linux-java21'])
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists() and len(args.platforms) == len(set(args.platforms))
    out.mkdir()
    prior_file = BASE / 'quant-workflow-development-2/control-2/COMPLETED.json'
    prior = json.loads(prior_file.read_bytes())
    assert prior['status'] == 'passed-selected-original-Salmon-bulk-workflows-and-complete-portable-replay' and prior['selected_editions'] == ['1.9.0']
    parent = ROOT / prior['artifact']['path']
    assert pin(parent) == {k: prior['artifact'][k] for k in ['bytes', 'sha256']}
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        assert set(old.namelist()) == set(current.namelist())
        changed = [n for n in old.namelist() if old.read(n) != current.read(n)]
        assert changed == ['org/jlinalg/cli/JLinAlgCli.class']
        unchanged = len(old.namelist()) - 1
    data = ROOT / 'docs/literature/omics-2023-2026'
    bridge_file = data / 'salmon-dispatch-shared-source-compatibility.json'
    bridge = json.loads(bridge_file.read_bytes())
    assert bridge['status'] == 'passed-dispatch-catalog-only-source-deltas' and len(bridge['paths']) == 1
    relative = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    before = BASE / 'dispatch-development-1/JLinAlgCli-before.java'
    assert reverse_delta((ROOT / relative).read_bytes(), bridge['paths'][0]) == before.read_bytes()
    verify_source(ROOT, data, relative, digest(before.read_bytes()))
    sources = dict(prior['source_pins'])
    for path in [ROOT / relative, before, bridge_file, ROOT / 'tools/literature/salmon_dispatch_bridge.py', ROOT / 'src/test/java/org/jlinalg/cli/SalmonDispatchTest.java', Path(__file__)]:
        sources[path.relative_to(ROOT).as_posix()] = pin(path)
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    tests = sorted(ROOT / name for name in sources if name.startswith('src/test/java/') and Path(name).name.startswith('Salmon') and name.endswith('Test.java'))
    assert len(tests) == 19
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonPublicDispatchChecks.java'
    selectors = ','.join('selectClass("' + t.relative_to(ROOT / 'src/test/java').as_posix()[:-5].replace('/', '.') + '")' for t in tests)
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonPublicDispatchChecks {
 public static void main(String[] args)throws Exception{
  for(String n:new String[]{"JLinAlgCli","SalmonToolsCli","SalmonQuantWorkflow","SalmonQuantAudit","SalmonQuantInputs","SalmonIndexWorkflow","SalmonRuntime"})if(!Class.forName("org.jlinalg.cli."+n).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=449||s.getTestsSucceededCount()!=449||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete root command cases");
  System.out.println("COMPLETE_PUBLIC_SALMON_CASES 449 NO_SKIPS");
 }
}
'''.replace('SELECTORS', selectors), encoding='utf-8', newline='\n')

    def command(name, argv, required=True):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=1200)
        receipt = dict(command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        if required:
            assert result.returncode == 0, (name, result.returncode)
        return receipt

    libraries = sorted(LIBS.glob('*.jar'))
    assert len(libraries) == 9
    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *tests])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = dict(zip(['windows-java21', 'windows-java25', 'linux-java21'], [([JAVA21 / 'java.exe'], str, os.pathsep), ([Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                     (['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]))
    native = BASE / 'quant-workflow-development-2/control-2/1.9.0'

    def check(name):
        java, convert, separator = platforms[name]
        focused = command(name + '-tests', [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, paths)), 'SalmonPublicDispatchChecks', convert(jar)])
        root_calls = [command(name + '-root-help', [*java, '-jar', convert(jar), 'salmon-tools', '--help']), command(name + '-root-profiles', [*java, '-jar', convert(jar), 'salmon-tools', '--runtime-profiles'])]
        profiles = json.loads((out / (name + '-root-profiles.stdout')).read_bytes())
        assert profiles == ['linux-x86_64-salmon-' + v for v in ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0']]
        portable = []
        for row in prior['runs'][0]['fits'] + [prior['runs'][0]['cli_fit']]:
            result = command(name + '-verify-' + row['id'], [*java, '-Xmx512m', '-jar', convert(jar), 'salmon-tools', '--verify', convert(native / row['id'])])
            portable.append(dict(id=row['id'], declared_retained_population_admitted=row['declared_retained_population_admitted'], process=result))
            if not row['declared_retained_population_admitted']:
                assert 'declared retained coverage=false' in (out / (name + '-verify-' + row['id'] + '.stdout')).read_text() and 'Salmon WARNING:' in (out / (name + '-verify-' + row['id'] + '.stderr')).read_text()
        failed = command(name + '-reject-failed-prefix', [*java, '-jar', convert(jar), 'salmon-tools', '--verify', convert(native / 'late-finalization')], required=False)
        assert failed['exit_code'] == 2 and 'FAIL2' in (out / (name + '-reject-failed-prefix.stderr')).read_text()
        print(name + ':449 actual-JAR cases and executable root help/profiles/whole-family verification passed', flush=True)
        return dict(name=name, tests=449, passed=449, skipped=0, focused=focused, root_calls=root_calls, portable=portable, rejected_prefix=failed)

    with concurrent.futures.ThreadPoolExecutor(max_workers=len(args.platforms)) as pool:
        runs = list(pool.map(check, args.platforms))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical and all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-selected-platform-Salmon-root-command-and-exact-dispatch-compatibility', platforms=args.platforms, runs=runs,
          artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact), original_tested_parent=prior['artifact'], source_pins=sources, exact_dispatch_bridge=pin(bridge_file),
          only_changed_archive_entry=changed[0], unchanged_archive_entries=unchanged, canonical_executable_unchanged=canonical,
          scope='Only the exact dispatcher route/help insertion changes packaged content. Every scientific class/resource is byte-identical to the original-tested candidate. '
          'Root executable verification covers all seven API fit modes plus the original public CLI alignment fit, retaining explicit source/declared distinctions and failed-prefix rejection. '
          'This selected-platform record does not assert unrun platforms, release/deployment, whole-paper reproduction or biological accuracy.', released=False, deployed=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
