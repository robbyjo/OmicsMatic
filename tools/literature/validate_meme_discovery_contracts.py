# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual candidate's new scientific request/raw-input contracts on three JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, pin, linux


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/meme-runtime') and not out.exists(); out.mkdir()
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
    libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9
    source = ROOT / 'src/test/java/org/jlinalg/cli/MemeDiscoveryContractsTest.java'
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/meme-discovery-sites/original-site-contracts.zip'
    empty_fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/meme-discovery-empty/original-empty-contracts.zip'
    bound = {p.relative_to(ROOT).as_posix(): pin(p) for p in [source, Path(__file__), fixture, empty_fixture, *libraries]}
    for path in [source, Path(__file__)]:
        target = out / 'source-snapshot' / path.relative_to(ROOT); target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(path.read_bytes())
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    classes = out / 'test-classes'; classes.mkdir()
    runner = out / 'RunMemeDiscoveryContracts.java'
    runner.write_text('''import java.io.PrintWriter;
import java.io.File;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMemeDiscoveryContracts {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.cli.MemeDiscoveryRequest","org.jlinalg.cli.MemeDiscoveryInputs","org.jlinalg.cli.MemeToolsCli"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact: "+name);
  var request=LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.MemeDiscoveryContractsTest")).build();
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);var s=listener.getSummary();
  s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=91||s.getTestsSucceededCount()!=91||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete MEME input contract checks");
 }
}
''', encoding='utf8', newline='\n')
    command = [str(JAVA21 / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', str(classes), str(source), str(runner)]
    r = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(r.stdout); (out / 'compile.stderr').write_bytes(r.stderr)
    assert r.returncode == 0, r.stderr.decode('utf8', errors='replace')
    entries = [classes, jar, ROOT / 'src/test/resources', *libraries]
    cp = os.pathsep.join(map(str, entries))
    common = ['-Xmx256m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8']
    commands = [('windows-java21', [str(JAVA21 / 'java.exe'), *common, '-cp', cp, 'RunMemeDiscoveryContracts', str(jar)]),
        ('windows-java25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe', *common, '-cp', cp, 'RunMemeDiscoveryContracts', str(jar)]),
        ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java', *common, '-cp', ':'.join(linux(p) for p in entries), 'RunMemeDiscoveryContracts', linux(jar)])]
    def run(item):
        label, argv = item
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            r = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=180)
        assert r.returncode == 0, label + ' failed; read retained stdout/stderr'
        print(label + ': 91 tests passed without skips', flush=True)
        return dict(JVM=label, command=argv, exit_code=r.returncode, tests=91, skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(run, commands))
    assert all(pin(ROOT / name) == wanted for name, wanted in bound.items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    (out / 'COMPLETED.json').write_text(json.dumps(dict(status='passed-actual-JAR-MEME-input-ownership-and-complete-explicit-XML-health-reference-contracts', artifact=build['artifact'], source_pins=bound,
        tests_per_JVM=91, JVMs=results, canonical_executable_unchanged=canonical, upstream_fitting_in_this_test=False, implementation_credit=False), indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable scientific source/artifact bindings')
    main()
