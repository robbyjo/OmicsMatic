# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check ambient arithmetic against actual original outputs and closed version-specific request contracts."""
import argparse,concurrent.futures,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,LIBS,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;tests=[ROOT/'src/test/java/org/jlinalg/cli/SoupXAmbientTest.java',ROOT/'src/test/java/org/jlinalg/cli/SoupXWorkflowRequestTest.java'];fixture=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-ambient';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*tests,*fixture.glob('*'),*libraries]}
    for name in pins:
        if name.startswith('build/'):continue
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunSoupAmbient.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.SummaryGeneratingListener;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunSoupAmbient{public static void main(String[] a)throws Exception{if(!Class.forName("org.jlinalg.singlecell.SoupXAmbient").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SoupXAmbientTest"),selectClass("org.jlinalg.cli.SoupXWorkflowRequestTest")).build(),listener);var r=listener.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));if(r.getTestsFoundCount()!=28||r.getTestsSucceededCount()!=28||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete ambient-core checks");}}
''',encoding='utf8',newline='\n');command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),*map(str,tests),str(runner)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');entries=[classes,jar,ROOT/'src/test/resources',*libraries];commands={'windows-java21':[str(JAVA21/'java.exe'),'-Xmx768m','-cp',os.pathsep.join(map(str,entries)),'RunSoupAmbient',str(jar)],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx768m','-cp',os.pathsep.join(map(str,entries)),'RunSoupAmbient',str(jar)],'linux-java21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx768m','-cp',':'.join(linux(p) for p in entries),'RunSoupAmbient',linux(jar)]}
    def check(item):
        name,command=item;r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return dict(name=name,exit_code=r.returncode,tests=28,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:reports=list(pool.map(check,commands.items()))
    assert all(row['exit_code']==0 for row in reports),'Inspect saved per-JVM output; preserve failed attempts';assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};save(out/'COMPLETED.json',dict(status='passed-SoupX-ambient-arithmetic-and-closed-request-contracts',source_pins=pins,artifact=artifact,JVMs=reports,original_input_populations=1,original_source_editions=2,original_profile_comparisons=8,original_manual_mask_and_Poisson_comparisons=6,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Passed28 ambient arithmetic/request checks on each of3 JVMs; execution adapter unfinished')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    main()
