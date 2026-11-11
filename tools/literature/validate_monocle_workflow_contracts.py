# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the actual candidate workflow contracts with unchanged original source controls on selected JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,pin,linux
from setup_deseq2_reference import save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact-build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--jvms',nargs='+',choices=['windows-java21','windows-java25','linux-java21'],default=['windows-java21','windows-java25','linux-java21'])
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir()
    proof=json.loads((args.artifact_build/'COMPLETED.json').read_bytes());jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    source=ROOT/'src/test/java/org/jlinalg/cli/Monocle3WorkflowTest.java'
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [source,Path(__file__),*libraries]}
    for p in [source,Path(__file__)]:
        target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunMonocle3Workflow.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMonocle3Workflow {
 public static void main(String[] args)throws Exception {
  if(!Class.forName("org.jlinalg.cli.Monocle3WorkflowCli").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong candidate artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.Monocle3WorkflowTest")).build(),listener);
  var result=listener.getSummary();result.printTo(new PrintWriter(System.out));result.printFailuresTo(new PrintWriter(System.out));
  if(result.getTestsFoundCount()!=8||result.getTestsSucceededCount()!=8||result.getTestsSkippedCount()!=0||result.getTestsAbortedCount()!=0||result.getTestsFailedCount()!=0||result.getContainersFailedCount()!=0)throw new AssertionError("Incomplete workflow contract checks");
 }
}
''',encoding='utf8',newline='\n')
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(source),str(runner)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr)
    assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    entries=[classes,jar,ROOT/'src/test/resources',*libraries];common=['-Xmx256m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8']
    commands={'windows-java21':[str(JAVA21/'java.exe'),*common,'-cp',os.pathsep.join(map(str,entries)),'RunMonocle3Workflow',str(jar)],
        'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',*common,'-cp',os.pathsep.join(map(str,entries)),'RunMonocle3Workflow',str(jar)],
        'linux-java21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java',*common,'-cp',':'.join(linux(p) for p in entries),'RunMonocle3Workflow',linux(jar)]}
    def run(label):
        command=commands[label]
        with (out/(label+'.stdout')).open('xb') as a,(out/(label+'.stderr')).open('xb') as b:
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=180)
        row=dict(JVM=label,command=command,exit_code=r.returncode);save(out/(label+'.process.json'),row)
        assert r.returncode==0,label+': read retained stdout/stderr; component not admitted'
        print(label+': 8 tests passed without skips',flush=True);return dict(row,tests=8,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(args.jvms)) as pool:rows=list(pool.map(run,args.jvms))
    assert all(pin(ROOT/n)==wanted for n,wanted in pins.items())
    save(out/'COMPLETED.json',dict(status='passed-actual-JAR-Monocle3-workflow-contract-checks',artifact=proof['artifact'],source_pins=pins,
        tests_per_JVM=8,JVMs=rows,original_whole_package_loaded=False,workflow_admitted=False,implementation_credit=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/candidate bindings')
    main()
