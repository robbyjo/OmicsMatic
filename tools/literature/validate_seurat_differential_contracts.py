# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run marker request/intake contracts against the actual artifact on three JVMs."""
import argparse
import concurrent.futures
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    jar = base / 'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
    artifact = pin(jar)
    libs = sorted((ROOT / 'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'))
    assert len(libs) == 9
    source = ROOT / 'src/test/java/org/jlinalg/cli/SeuratDifferentialContractsTest.java'
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [source, Path(__file__), *libs]}
    runner = out / 'RunSeuratContracts.java'
    runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunSeuratContracts {
 private RunSeuratContracts() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"SeuratDifferentialCli","SeuratDifferentialContracts","SeuratDifferentialInputs","SeuratDifferentialRuntime","SeuratDifferentialOutputs"})
   if(!Class.forName("org.jlinalg.cli."+name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact loaded");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SeuratDifferentialContractsTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=36||s.getTestsSucceededCount()!=36||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete Seurat contract tests");
 }
}
''', encoding='utf8')
    classes = out / 'classes'
    classes.mkdir()

    def command(label, argv):
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=180)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode))
        assert result.returncode == 0, label + ' failed; inspect retained logs'
        return dict(JVM=label, tests=36, passed=36, skipped=0)

    try:
        command('compile', [Path(JAVA21).with_name('javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                            '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libs])),
                            '-d', classes, runner, source])
        entries = [jar, classes, *libs]
        cp = os.pathsep.join(map(str, entries))
        runs = [('windows-java21', [JAVA21, '-Xmx256m', '-cp', cp, 'RunSeuratContracts', jar]),
                ('windows-java25', [JAVA25, '-Xmx256m', '-cp', cp, 'RunSeuratContracts', jar]),
                ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java',
                                  '-Xmx256m', '-cp', ':'.join(map(linux, entries)), 'RunSeuratContracts', linux(jar)])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(lambda row: command(*row), runs))
        assert pin(jar) == artifact and all(pin(ROOT / n) == v for n, v in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-actual-JAR-Seurat-marker-contracts-three-JVMs',
                                         artifact=artifact, source_pins=sources, JVMs=results,
                                         tests_per_JVM=36, original_R_executed=False))
        print('36 actual-JAR marker contract tests passed on each of three JVMs')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_SEURAT_CONTRACT_MATRIX', error=str(error)))
        raise


if __name__ == '__main__':
    main()
