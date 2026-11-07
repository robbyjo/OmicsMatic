# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate complete original transports and semantic corruption refusals from the actual workflow JAR on3 JVMs."""
import argparse,concurrent.futures,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,LIBS,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;test=ROOT/'src/test/java/org/jlinalg/cli/SoupXWorkflowTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-workflow';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),test,*fixture.glob('*'),*libraries]}
    for name in pins:
        if name.startswith('build/'):continue
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunSoupWorkflow.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.SummaryGeneratingListener;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunSoupWorkflow{public static void main(String[] a)throws Exception{if(!Class.forName("org.jlinalg.cli.SoupXWorkflowCli").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SoupXWorkflowTest")).build(),listener);var r=listener.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));if(r.getTestsFoundCount()!=30||r.getTestsSucceededCount()!=30||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete SoupX workflow audits");}}
''',encoding='utf8',newline='\n');command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(test),str(runner)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');entries=[classes,jar,ROOT/'src/test/resources',*libraries];commands={'windows-java21':[str(JAVA21/'java.exe'),'-Xmx1g','-cp',os.pathsep.join(map(str,entries)),'RunSoupWorkflow',str(jar)],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx1g','-cp',os.pathsep.join(map(str,entries)),'RunSoupWorkflow',str(jar)],'linux-java21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx1g','-cp',':'.join(linux(p) for p in entries),'RunSoupWorkflow',linux(jar)]}
    def check(item):
        name,command=item;r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return dict(name=name,exit_code=r.returncode,tests=30,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:reports=list(pool.map(check,commands.items()))
    assert all(r['exit_code']==0 for r in reports),'Inspect saved JVM output; keep failed attempts';assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-portable-SoupX-original-transport-and-semantic-audit-tests',artifact=build['artifact'],source_pins=pins,JVMs=reports,original_worker_transports=12,original_scientific_scope_not_shrunk=True,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Passed30 complete SoupX transport/semantic checks on each of3 JVMs')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable semantic/source checks')
    main()
