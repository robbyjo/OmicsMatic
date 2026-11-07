# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the new closed HTSeq contract/table tests in the actual candidate on three JVMs."""
import argparse
import concurrent.futures
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from build_htseq_file_count_development import BASE,TARGET
from validate_deseq2_inference import JAVA21,JAVA25,linux


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(TARGET);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    libraries=sorted((ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'));assert len(libraries)==9
    test=ROOT/'src/test/java/org/jlinalg/cli/HtseqFileCountTest.java';runner=out/'RunHtseqFileCount.java'
    runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunHtseqFileCount {
 private RunHtseqFileCount() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"HtseqToolsCli","HtseqFileCounting","HtseqFileContract","HtseqCountTable","HtseqCountRuntime","RsemProcess","SimpleJson"})
   if(!Class.forName("org.jlinalg.cli."+name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.HtseqFileCountTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=217||s.getTestsSucceededCount()!=217||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete HTSeq file-count contract tests");
 }
}
''',encoding='utf8',newline='\n')
    classes=out/'classes';classes.mkdir()
    def command(label,argv):
        argv=list(map(str,argv));result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=300)
        (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,label
        return dict(JVM=label,tests=217,passed=217,skipped=0)
    command('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[TARGET,*libraries])),'-d',classes,runner,test])
    entries=[TARGET,classes,ROOT/'src/test/resources',*libraries];cp=os.pathsep.join(map(str,entries))
    jobs=[('windows-java21',[JAVA21,'-Xmx512m','-cp',cp,'RunHtseqFileCount',TARGET]),('windows-java25',[JAVA25,'-Xmx512m','-cp',cp,'RunHtseqFileCount',TARGET]),
        ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-cp',':'.join(map(linux,entries)),'RunHtseqFileCount',linux(TARGET)])]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda job:command(*job),jobs))
    assert pin(TARGET)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='passed-217-actual-JAR-HTSeq-file-count-contract-tests-three-JVMs',artifact=artifact,tests_per_JVM=217,JVMs=results,
        source_tables_audited=171,original_error_outputs_refused=14,canonical_executable_preserved=canonical,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),test,*libraries]},catalog_credit=False))
    print('Passed217 HTSeq contract tests per JVM on Windows21/25 and Linux21; no skips')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual-JAR checks')
    main()
