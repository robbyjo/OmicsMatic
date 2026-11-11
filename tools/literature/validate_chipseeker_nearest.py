# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the actual candidate native API with unchanged original source controls on selected JVMs."""
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
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    proof=json.loads((args.artifact_build/'COMPLETED.json').read_bytes());jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    source=ROOT/'src/test/java/org/jlinalg/cli/ChipSeekerNearestFeaturesTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/genetics/chipseeker-nearest/original-controls-v2.json.gz'
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [source,fixture,Path(__file__),*libraries]}
    for p in [source,Path(__file__)]:
        target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunChipSeekerNearest.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunChipSeekerNearest {
 public static void main(String[] args)throws Exception {
  if(!Class.forName("org.jlinalg.genetics.ChipSeekerNearestFeatures").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong candidate artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.ChipSeekerNearestFeaturesTest")).build(),listener);
  var result=listener.getSummary();result.printTo(new PrintWriter(System.out));result.printFailuresTo(new PrintWriter(System.out));
  if(result.getTestsFoundCount()!=202||result.getTestsSucceededCount()!=202||result.getTestsSkippedCount()!=0||result.getTestsAbortedCount()!=0||result.getTestsFailedCount()!=0||result.getContainersFailedCount()!=0)throw new AssertionError("Incomplete original/native component comparisons");
 }
}
''',encoding='utf8',newline='\n')
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(source),str(runner)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr)
    assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    entries=[classes,jar,ROOT/'src/test/resources',*libraries];common=['-Xmx256m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8']
    commands={'windows-java21':[str(JAVA21/'java.exe'),*common,'-cp',os.pathsep.join(map(str,entries)),'RunChipSeekerNearest',str(jar)],
        'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',*common,'-cp',os.pathsep.join(map(str,entries)),'RunChipSeekerNearest',str(jar)],
        'linux-java21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java',*common,'-cp',':'.join(linux(p) for p in entries),'RunChipSeekerNearest',linux(jar)]}
    def run(label):
        command=commands[label]
        with (out/(label+'.stdout')).open('xb') as a,(out/(label+'.stderr')).open('xb') as b:
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=180)
        row=dict(JVM=label,command=command,exit_code=r.returncode);save(out/(label+'.process.json'),row)
        assert r.returncode==0,label+': read retained stdout/stderr; component not admitted'
        print(label+': 202 tests passed without skips',flush=True);return dict(row,tests=202,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(args.jvms)) as pool:rows=list(pool.map(run,args.jvms))
    assert all(pin(ROOT/n)==wanted for n,wanted in pins.items())
    save(out/'COMPLETED.json',dict(status='passed-actual-JAR-ChIPseeker-nearest-component-reference-checks',artifact=proof['artifact'],source_pins=pins,
        tests_per_JVM=202,JVMs=rows,original_comparisons_per_JVM=128,original_boundary_refusals_per_JVM=64,original_whole_package_loaded=False,workflow_admitted=False,implementation_credit=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/candidate bindings')
    main()
