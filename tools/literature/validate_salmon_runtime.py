# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual-JAR native Salmon admission and fresh original health on declared Linux homes."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'
BASE = ROOT / 'build/literature-review/salmon-runtime'


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete native runtime checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--versions', nargs='+', choices=['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'], default=['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'])
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    assert len(args.versions) == len(set(args.versions))
    out.mkdir()
    artifact = pin(jar)
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    parent = BASE / 'setup-native-development-1/package-3/omicsmatic-0.3.7-salmon-development.jar'
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [n for n in old.namelist() if n.endswith('.class')]
        assert all(current.read(n) == old.read(n) for n in legacy)
        added = [n for n in current.namelist() if n.endswith('.class') and n not in legacy]
        assert len(added) == 7 and all(n.startswith(('org/jlinalg/cli/SalmonRuntime', 'org/jlinalg/cli/SalmonHealth')) for n in added)
    names = ['src/main/java/org/jlinalg/cli/SalmonRuntime.java', 'src/main/java/org/jlinalg/cli/SalmonHealth.java', 'src/test/java/org/jlinalg/cli/SalmonHealthTest.java',
             'src/main/java/org/jlinalg/cli/RsemProcess.java', 'src/main/java/org/jlinalg/cli/RsemElf.java', 'src/main/java/org/jlinalg/cli/RepeatHostFiles.java',
             'src/main/java/org/jlinalg/cli/RsemJson.java', 'src/main/java/org/jlinalg/cli/NextflowFiles.java', 'src/main/java/org/jlinalg/cli/NextflowJson.java',
             'src/main/java/org/jlinalg/cli/SimpleJson.java', Path(__file__).relative_to(ROOT).as_posix()]
    sources = {name: pin(ROOT / name) for name in names}
    prior = json.loads((BASE / 'setup-native-development-1/matrix-1/COMPLETED.json').read_bytes())
    for name, value in prior['source_pins'].items():
        assert pin(ROOT / name) == value
        sources[name] = value
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonRuntimeChecks.java'
    runner.write_text('''package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonRuntimeChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.cli.SalmonRuntime","org.jlinalg.cli.SalmonHealth","org.jlinalg.cli.SalmonSetupEvidence"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  if(args[1].equals("tests")) {
   var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SalmonHealthTest"),selectClass("org.jlinalg.cli.SalmonSetupEvidenceTest"),selectClass("org.jlinalg.sequence.SalmonQuantificationTest"),selectClass("org.jlinalg.sequence.SalmonInferentialReplicatesTest")).build(),listener);
   var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
   if(s.getTestsFoundCount()!=162||s.getTestsSucceededCount()!=162||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native Salmon runtime checks");
   System.out.println("COMPLETE_NATIVE_SALMON_CASES 162 NO_SKIPS");return;
  }
  String mode=args[1],profile=args[2];Path home=Path.of(args[3]),setup=Path.of(args[4]),work=Path.of(args[5]);
  var limits=SalmonRuntime.Limits.defaults();
  if(mode.equals("missing"))home=work.getParent().resolve("absent-home");
  if(mode.equals("wrong-profile"))profile=profile.endsWith("1.8.0")?"linux-x86_64-salmon-1.1.0":"linux-x86_64-salmon-1.8.0";
  if(mode.equals("image-capacity"))limits=new SalmonRuntime.Limits(limits.host(),limits.process(),limits.setup(),1);
  var injected=new AtomicBoolean();
  Runnable cancellation=()->{
   if(mode.equals("cancel-after-version")&&Files.exists(work.resolve("probes/version/process.json")))throw new java.util.concurrent.CancellationException("controlled native cancellation after clean original version process");
   if(mode.equals("late-finalization")&&Files.exists(work.resolve("metadata/health.json"))&&injected.compareAndSet(false,true))try{Files.writeString(work.resolve("COMPLETED.json"),"{\\"controlled_untrusted_collision\\":true}\\n",StandardOpenOption.CREATE_NEW);}catch(IOException e){throw new java.io.UncheckedIOException(e);}
  };
  try {
   var admitted=SalmonRuntime.admit(profile,home,setup,work,limits,cancellation);admitted.verify(()->{});
   if(!mode.equals("admit"))throw new AssertionError("Negative control admitted a runtime");
   System.out.println(NextflowJson.json(Map.of("status","passed-native-runtime-health-only","profile",admitted.profile(),"version",admitted.version(),"work",work.toString(),"complete_health_rows",admitted.health().stream().mapToInt(r->r.table().rows().size()).sum(),"complete_bootstrap_values",admitted.health().stream().mapToLong(r->r.replicates().values()).sum(),"user_analysis_admitted",false,"complete_native_files",SalmonRuntime.tree(work,limits.process(),()->{}))));
  } catch(IOException expected) {
   if(mode.equals("admit"))throw expected;
   if(mode.equals("missing")!= (expected instanceof SalmonRuntime.Unavailable))throw new AssertionError("Wrong SKIP3/FAIL2 classification",expected);
   if(Files.exists(work.resolve("COMPLETED.json")))throw new AssertionError("Failed admission retained a completion marker",expected);
   String warning=Files.readString(work.resolve("WARNINGS.md"));if(!warning.contains("Required action:")||!warning.contains(mode.equals("missing")?"SKIP3":"FAIL2"))throw new AssertionError("Missing prominent recovery");
   if(mode.equals("late-finalization")&&(!injected.get()||!Files.exists(work.resolve("COMPLETED-REVOKED.json"))||!Files.exists(work.resolve("health/health-vb/quant.sf"))))throw new AssertionError("Late failure did not preserve complete original health and revoke the marker");
   System.out.println(NextflowJson.json(Map.of("status","passed-expected-native-admission-rejection","mode",mode,"error",expected.toString(),"exit_class",mode.equals("missing")?3:2,"work",work.toString(),"user_analysis_admitted",false,"complete_native_files",SalmonRuntime.tree(work,SalmonRuntime.Limits.defaults().process(),()->{}))));
  }
 }
}
''', encoding='utf-8', newline='\n')
    libraries = sorted(LIBS.glob('*.jar'))
    assert len(libraries) == 9

    def command(name, argv, timeout=900, require_zero=True):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout)
        record = dict(command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), record)
        if require_zero:
            assert result.returncode == 0, (name, result.returncode)
        return record

    test_sources = [ROOT / 'src/test/java/org/jlinalg/cli/SalmonHealthTest.java', ROOT / 'src/test/java/org/jlinalg/cli/SalmonSetupEvidenceTest.java',
                    ROOT / 'src/test/java/org/jlinalg/sequence/SalmonQuantificationTest.java', ROOT / 'src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java']
    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *test_sources])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                 ('linux-java21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java'], linux, ':')]

    def matrix(spec):
        name, java, path, separator = spec
        process = command(name, [*java, '-Xmx128m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(path, paths)), 'org.jlinalg.cli.SalmonRuntimeChecks', path(jar), 'tests'])
        assert 'COMPLETE_NATIVE_SALMON_CASES 162 NO_SKIPS' in (out / (name + '.stdout')).read_text()
        print(name + ':162 native cases pass without skips', flush=True)
        return dict(name=name, tests=162, passed=162, process=process)

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        matrix_runs = list(pool.map(matrix, platforms))
    remote = subprocess.check_output(['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', 'import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-native-admission-",dir="/var/tmp"))']).decode().strip()
    assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-native-admission-[A-Za-z0-9_-]+', remote)
    write(out / 'OWNER.json', dict(owned_linux_root=remote))
    prior_setup = json.loads((BASE / 'managed-setup-development-2/COMPLETED.json').read_bytes())
    homes = {r['version']: r for r in prior_setup['rechecks']}
    cp = ':'.join(map(linux, paths))

    def run(label, version, mode):
        row = homes[version]
        owned = remote + '/' + label
        process = command(label, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', cp,
                          'org.jlinalg.cli.SalmonRuntimeChecks', linux(jar), mode, 'linux-x86_64-salmon-' + version, row['home'], linux(ROOT / row['report']), owned], 2400, False)
        if process['exit_code'] != 0:
            destination = out / (label + '-FAILED')
            code = 'from pathlib import Path;import shutil,sys;source=Path(sys.argv[1]);target=Path(sys.argv[2]);assert not target.exists();shutil.copytree(source,target,copy_function=shutil.copyfile)'
            command(label + '-failed-copy', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', code, owned, linux(destination)])
            write(out / (label + '-FAILED-CAPTURE.json'), dict(status='terminal-native-admission-failure-retained-no-result', source_area=owned,
                  files={p.relative_to(destination).as_posix(): pin(p) for p in destination.rglob('*') if p.is_file()}, process=process, user_analysis_admitted=False))
            raise AssertionError((label, process['exit_code'], 'complete failed native files retained'))
        summary = json.loads((out / (label + '.stdout')).read_bytes())
        assert summary['user_analysis_admitted'] is False
        if mode == 'admit':
            assert summary['status'] == 'passed-native-runtime-health-only' and summary['complete_health_rows'] == 80 and summary['complete_bootstrap_values'] == 6000
        else:
            assert summary['status'] == 'passed-expected-native-admission-rejection'
        destination = out / label
        code = 'from pathlib import Path;import shutil,sys;source=Path(sys.argv[1]);target=Path(sys.argv[2]);assert not target.exists();shutil.copytree(source,target,copy_function=shutil.copyfile)'
        command(label + '-copy', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', code, owned, linux(destination)])
        actual = {p.relative_to(destination).as_posix() for p in destination.rglob('*')}
        assert actual == set(summary['complete_native_files'])
        for name, value in summary['complete_native_files'].items():
            path = destination / name
            if value['kind'] == 'directory':
                assert path.is_dir()
            else:
                assert pin(path) == {k: value[k] for k in ['bytes', 'sha256']}
        print(label + ':complete original native evidence retained; user analysis remains unadmitted', flush=True)
        return dict(label=label, version=version, mode=mode, process=process, summary=summary)

    runs = []
    for version in args.versions:
        runs.append(run('admit-' + version, version, 'admit'))
    negative = [run(mode, args.versions[0], mode) for mode in ['missing', 'wrong-profile', 'image-capacity', 'cancel-after-version', 'late-finalization']]
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == row for name, row in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-actual-JAR-native-Salmon-admission-for-declared-editions-and-negative-controls', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          selected_editions=args.versions, native_matrix=matrix_runs, original_admissions=runs, negative_controls=negative, source_pins=sources,
          unchanged_parent_classes=len(legacy), new_classes=added, canonical_executable_unchanged=canonical,
          scope='Native complete payload/marker/setup rechecks, current ELF/helper/loader closure, host-file and process/CPU identities, fresh original index/EM/VB health, '
                'complete native tables/bootstrap streams and retained process supervision. Tiny synthetic population below burn-in; no user fit, public CLI or biological certificate.',
          public_quantification_adapter_available=False, user_analysis_admitted=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
