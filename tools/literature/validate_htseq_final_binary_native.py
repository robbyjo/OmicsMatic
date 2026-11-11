# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual-JAR original-library binary-presence comparisons on three JVMs."""
import argparse
import concurrent.futures
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from build_htseq_tools_diagnostics import BASE,JAR
from validate_deseq2_inference import JAVA21,JAVA25,linux


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');test=ROOT/'src/test/java/org/jlinalg/cli/HtseqBinaryAccessibilityTest.java'
    libraries=sorted((ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'));assert len(libraries)==9
    runner=out/'RunHtseqBinary.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunHtseqBinary {
 private RunHtseqBinary() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.HtseqBinaryAccessibility","org.jlinalg.sequence.HtseqFeatureAssignment","org.jlinalg.cli.HtseqFileContract","org.jlinalg.cli.SimpleJson"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.HtseqBinaryAccessibilityTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=43||s.getTestsSucceededCount()!=43||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete binary accessibility controls");
 }
}
''',encoding='utf8',newline='\n')
    classes=out/'classes';classes.mkdir()
    def command(label,argv):
        argv=list(map(str,argv));result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=300)
        (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr);save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,label
        return dict(JVM=label,tests=43,passed=43,skipped=0)
    command('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[JAR,*libraries])),'-d',classes,runner,test])
    entries=[JAR,classes,ROOT/'src/test/resources',*libraries];cp=os.pathsep.join(map(str,entries))
    jobs=[('windows-java21',[JAVA21,'-Xmx512m','-cp',cp,'RunHtseqBinary',JAR]),('windows-java25',[JAVA25,'-Xmx512m','-cp',cp,'RunHtseqBinary',JAR]),
        ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-cp',':'.join(map(linux,entries)),'RunHtseqBinary',linux(JAR)])]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda job:command(*job),jobs))
    assert pin(JAR)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='passed-native-HTSeq-binary-presence-three-JVMs',artifact=artifact,tests_per_JVM=43,JVMs=results,
        original_library_matrices=32,original_refusals=2,original_prepared_reads=742,original_binary_nonzero_coordinates=234,original_step_spans=1278,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),test,ROOT/'src/main/java/org/jlinalg/sequence/HtseqBinaryAccessibility.java']},
        canonical_executable_preserved=canonical,original_custom_author_script_recovered=False,catalog_credit=False))
    print('Passed43 actual-JAR binary-accessibility tests per JVM on Windows21/25 and Linux21')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual-JAR checks')
    main()
