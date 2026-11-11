# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise structural/adversarial LOFTEE output-audit contracts on three JVMs; no plugin prediction claim."""
import argparse,concurrent.futures,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,LIBS,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/loftee-runtime') and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;test=ROOT/'src/test/java/org/jlinalg/cli/LofteeVcfAuditTest.java';original_test=ROOT/'src/test/java/org/jlinalg/cli/LofteeOriginalAuditTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/genetics/loftee-original/controls-v1.zip';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),test,original_test,fixture,*libraries]}
    for name in pins:
        if name.startswith('build/'):continue
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunLofteeAudit.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.SummaryGeneratingListener;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunLofteeAudit{public static void main(String[] a)throws Exception{if(!Class.forName("org.jlinalg.genetics.LofteeVcfAudit").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong audit candidate JAR");var l=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.LofteeVcfAuditTest"),selectClass("org.jlinalg.cli.LofteeOriginalAuditTest")).build(),l);var r=l.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));if(r.getTestsFoundCount()!=43||r.getTestsSucceededCount()!=r.getTestsFoundCount()||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete structural/adversarial audit checks");}}
''',encoding='utf8',newline='\n');command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(test),str(original_test),str(runner)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');entries=[classes,jar,ROOT/'src/test/resources',*libraries];commands={'windows-java21':[str(JAVA21/'java.exe'),'-cp',os.pathsep.join(map(str,entries)),'RunLofteeAudit',str(jar)],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-cp',os.pathsep.join(map(str,entries)),'RunLofteeAudit',str(jar)],'linux-java21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-cp',':'.join(linux(f) for f in entries),'RunLofteeAudit',linux(jar)]}
    def run(item):
        name,command=item;r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return dict(name=name,exit_code=r.returncode,tests=43,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:reports=list(pool.map(run,commands.items()))
    assert all(r['exit_code']==0 for r in reports),'Inspect retained JVM failures';assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-structural-and-adversarial-LOFTEE-output-audit-checks',artifact=build['artifact'],source_pins=pins,JVMs=reports,tests_per_JVM=43,original_plugin_predictions_validated=False,LOFTEE_annotation_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Passed43 audit-contract/original-transport checks on each of3 JVMs; real LOFTEE annotation remains unvalidated')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable artifact/source checks')
    main()
