# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run full original-transport/family/precision/mutation checks on three JVMs; no installed SMR required by the tests."""
import argparse,concurrent.futures,json,os,re,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,LIBS,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;test=ROOT/'src/test/java/org/jlinalg/cli/SmrWorkflowTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/mr/smr/workflow-controls-v2.zip';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),test,fixture,*libraries]}
    with zipfile.ZipFile(fixture) as z:expected_tests=len(json.loads(z.read('INDEX.json'))['protocols'])+29
    for name in pins:
        if name.startswith('build/'):continue
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunSmrWorkflow.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.SummaryGeneratingListener;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunSmrWorkflow{public static void main(String[] a)throws Exception{if(!Class.forName("org.jlinalg.cli.SmrWorkflowCli").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong workflow candidate JAR");var l=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SmrWorkflowTest")).build(),l);var r=l.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));if(r.getTestsFoundCount()!=Long.parseLong(a[1])||r.getTestsSucceededCount()!=r.getTestsFoundCount()||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete original-family/precision/contract controls");}}
''',encoding='utf8',newline='\n');command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(test),str(runner)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');entries=[classes,jar,ROOT/'src/test/resources',*libraries];commands={'windows-java21':[str(JAVA21/'java.exe'),'-Xmx2g','-cp',os.pathsep.join(map(str,entries)),'RunSmrWorkflow',str(jar),str(expected_tests)],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx2g','-cp',os.pathsep.join(map(str,entries)),'RunSmrWorkflow',str(jar),str(expected_tests)],'linux-java21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx2g','-cp',':'.join(linux(f) for f in entries),'RunSmrWorkflow',linux(jar),str(expected_tests)]}
    def run(item):
        name,command=item;r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return dict(name=name,exit_code=r.returncode,tests=expected_tests,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:reports=list(pool.map(run,commands.items()))
    assert all(r['exit_code']==0 for r in reports),'Inspect retained full JVM failures';assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-full-original-SMR-transports-and-adversarial-native-audit-checks',artifact=build['artifact'],source_pins=pins,JVMs=reports,tests_per_JVM=expected_tests,adapter_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Passed original transports and adversarial controls on all3 JVMs; runtime/setup/catalog gates separate')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable artifact/source checks')
    main()
