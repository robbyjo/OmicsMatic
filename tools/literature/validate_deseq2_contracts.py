# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run DESeq2 typed-contract tests against the actual JAR without R on three JVMs."""
import argparse
import concurrent.futures
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve()
    base=ROOT/'build/literature-review/deseq2-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir()
    jar=base/'inference-development/omicsmatic-0.3.7-deseq2-inference-development.jar';artifact=pin(jar)
    libs=sorted((ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'));assert len(libs)==9
    jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
    source=ROOT/'src/test/java/org/jlinalg/cli/Deseq2InferenceContractsTest.java';bound={p.relative_to(ROOT).as_posix():pin(p) for p in [source,Path(__file__),*libs]}
    runner=out/'RunDeseq2Contracts.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunDeseq2Contracts {
 private RunDeseq2Contracts() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"Deseq2InferenceCli","Deseq2Contracts","Deseq2Inputs","Deseq2Runtime","Deseq2Outputs"})
   if(!Class.forName("org.jlinalg.cli."+name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact loaded");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.Deseq2InferenceContractsTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=38||s.getTestsSucceededCount()!=38||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete DESeq2 contract tests");
 }
}
''',encoding='utf-8')
    classes=out/'classes';classes.mkdir()
    def command(name,argv):
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(out/(name+'.process.json'),dict(command=list(map(str,argv)),exit_code=r.returncode));assert r.returncode==0,name+' failed; inspect retained logs'
        return dict(platform=name,tests=38,passed=38,skipped=0)
    try:
        command('compile',[jdk/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libs])),'-d',classes,runner,source])
        entries=[jar,classes,*libs];cp=os.pathsep.join(map(str,entries));linux=lambda p:'/mnt/d/'+Path(p).resolve().relative_to(Path('D:/')).as_posix()
        runs=[('windows-java21',[jdk/'java.exe','-Xmx256m','-cp',cp,'RunDeseq2Contracts',jar]),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx256m','-cp',cp,'RunDeseq2Contracts',jar]),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx256m','-cp',':'.join(map(linux,entries)),'RunDeseq2Contracts',linux(jar)])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda row:command(*row),runs))
        assert pin(jar)==artifact and all(pin(ROOT/n)==v for n,v in bound.items())
        save(out/'COMPLETED.json',dict(status='passed-actual-JAR-typed-DESeq2-contract-tests-three-JVMs',artifact=artifact,source_pins=bound,runs=results,tests_per_JVM=38,R_executed=False))
        print('38 packaged contract tests passed on each of three JVMs')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_DESEQ2_CONTRACT_MATRIX',error=str(e)));raise


if __name__=='__main__':main()
