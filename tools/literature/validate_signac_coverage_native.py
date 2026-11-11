# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run coverage/annotation originals and request contracts in the actual JAR on three JVMs."""
import argparse
import concurrent.futures
import os
import zipfile
from signac_coverage_validation import *


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    parent=BASE/'peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(JAR) as new:
        assert len(new.namelist())==len(set(new.namelist())) and new.testzip() is None
        changed=[n for n in old.namelist() if n not in new.namelist() or old.read(n)!=new.read(n)]
        added=sorted(set(new.namelist())-set(old.namelist()))
        save(out/'parent-entries.json',dict(parent=pin(parent),changed=changed,added=added))
        assert not changed and added and all(n.startswith(('org/jlinalg/cli/SignacCoverage','org/jlinalg/sequence/SignacCoverage','org/jlinalg/plot/SignacCoveragePlot','org/jlinalg/omics/signac-coverage/')) for n in added)
    libs=sorted((ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'));assert len(libs)==9
    tests=[ROOT/'src/test/java/org/jlinalg/cli'/n for n in ['SignacCoverageTest.java','SignacCoverageContractsTest.java']]
    fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-coverage'
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_coverage_validation.py',*tests,*fixture.iterdir(),*libs] if p.is_file()}
    runner=out/'RunCoverageWorkflow.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunCoverageWorkflow {
 private RunCoverageWorkflow() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.SignacCoverage","org.jlinalg.sequence.SignacCoverageAnnotations","org.jlinalg.sequence.SignacCoverageSampling","org.jlinalg.cli.SignacCoverageCli","org.jlinalg.plot.SignacCoveragePlot"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SignacCoverageTest"),selectClass("org.jlinalg.cli.SignacCoverageContractsTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=660||s.getTestsSucceededCount()!=660||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete coverage tests");
 }
}
''',encoding='utf8')
    classes=out/'classes';classes.mkdir()
    def command(label,argv):
        argv=list(map(str,argv))
        with (out/(label+'.stdout')).open('xb') as a,(out/(label+'.stderr')).open('xb') as b:p=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=240)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=p.returncode));assert p.returncode==0,label
        return dict(JVM=label,tests=660,passed=660,skipped=0)
    try:
        command('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[JAR,*libs])),'-d',classes,runner,*tests])
        entries=[JAR,classes,ROOT/'src/test/resources',*libs];cp=os.pathsep.join(map(str,entries))
        commands=[('windows-java21',[JAVA21,'-Djava.awt.headless=true','-Xmx768m','-cp',cp,'RunCoverageWorkflow',JAR]),('windows-java25',[JAVA25,'-Djava.awt.headless=true','-Xmx768m','-cp',cp,'RunCoverageWorkflow',JAR]),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Djava.awt.headless=true','-Xmx768m','-cp',':'.join(map(linux,entries)),'RunCoverageWorkflow',linux(JAR)])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda row:command(*row),commands))
        assert pin(JAR)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-actual-JAR-native-Signac-coverage-three-JVMs',artifact=artifact,source_pins=sources,
             tests_per_JVM=660,JVMs=results,parent_entries_preserved=True,canonical_executable_preserved=canonical,
             full_original_plot_data_cases=3,original_sampling_cases=46,source_annotation_geometries=270,historical_whole_environments=False))
        print('660 actual-JAR coverage/annotation tests passed on each of3 JVMs; every parent entry preserved')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_NATIVE_COVERAGE_MATRIX',error=str(e)));raise


if __name__=='__main__':main()
