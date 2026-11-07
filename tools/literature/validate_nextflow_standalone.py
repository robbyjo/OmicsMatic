# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the six real portable attempts and sixteen corruption/metadata controls from an actual JAR."""
import argparse,concurrent.futures,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();jar=args.artifact.resolve();out=args.out.resolve();base=ROOT/'build/literature-review/nextflow-runtime';assert jar.is_relative_to(base.resolve()) and out.is_relative_to(base.resolve()) and not out.exists();out.mkdir()
    artifact=pin(jar);test=ROOT/'src/test/java/org/jlinalg/cli/NextflowStandaloneBundleTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/cli/nextflow-workflow/initial-controls.zip';sources={p.relative_to(ROOT).as_posix():pin(p) for p in [test,fixture,Path(__file__)]};libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    runner=out/'StandaloneChecks.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class StandaloneChecks {
 public static void main(String[] args)throws Exception {
  if(!Class.forName("org.jlinalg.cli.NextflowBundle").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.NextflowStandaloneBundleTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=22||s.getTestsSucceededCount()!=22||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsSkippedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete standalone bundle verification");
 }
}
''',encoding='utf-8',newline='\n')
    def command(label,argv):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
        record=dict(command=list(map(str,argv)),exit_code=result.returncode);write(out/(label+'.json'),record);return record
    classes=out/'classes';classes.mkdir();compiled=command('compile',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,test]);assert compiled['exit_code']==0,'Inspect compile.stderr'
    paths=[classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def check(spec):
        name,java,path,separator=spec;result=command(name,[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'StandaloneChecks',path(jar)]);print(name+': exit '+str(result['exit_code']),flush=True);return dict(name=name,result=result)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,platforms))
    assert pin(jar)==artifact and all(pin(ROOT/p)==value for p,value in sources.items());write(out/'results.json',dict(artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),runs=runs,source_pins=sources))
    assert all(r['result']['exit_code']==0 for r in runs),'Inspect retained full JUnit failure logs'
    write(out/'COMPLETED.json',dict(status='passed-22-portable-bundle-controls-on-three-JVMs',tests_per_JVM=22,runs=runs,artifact=artifact,source_pins=sources,whole_paper_reproduced=False))
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_NEXTFLOW_STANDALONE_MATRIX: '+str(failure)+'\nRequired action: retain the failure logs and fixture, correct the named verifier defect and rerun to NEW paths.',file=sys.stderr);sys.exit(2)
