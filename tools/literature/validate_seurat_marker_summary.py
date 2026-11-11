# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test the actual normal-Gradle native marker-summary JAR on three JVMs."""
import argparse,concurrent.futures,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux
BASE=ROOT/'build/literature-review/signac-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    jar=BASE/'differential-summary-development/omicsmatic-0.3.7-seurat-marker-summary-development.jar';parent=ROOT/'build/literature-review/deseq2-runtime/public-tools-development/omicsmatic-0.3.7-deseq2-tools-development.jar';artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    with zipfile.ZipFile(parent) as a,zipfile.ZipFile(jar) as b:
        assert len(b.namelist())==len(set(b.namelist())) and b.testzip() is None and set(a.namelist()).issubset(b.namelist());added=sorted(set(b.namelist())-set(a.namelist()));changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
        assert added and all(n.startswith('org/jlinalg/sequence/SeuratMarkerSummary') and n.endswith('.class') for n in added) and not changed,(added,changed)
    names=['src/main/java/org/jlinalg/sequence/SeuratMarkerSummary.java','src/test/java/org/jlinalg/cli/SeuratMarkerSummaryTest.java','tools/literature/validate_seurat_marker_summary.py','tools/literature/seurat-marker-summary-development.init.gradle'];names +=[p.relative_to(ROOT).as_posix() for p in (ROOT/'src/test/resources/org/jlinalg/sequence/seurat-marker-summary').iterdir() if p.is_file()];sources={n:pin(ROOT/n) for n in names}
    libs=sorted((ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'));assert len(libs)==9
    runner=out/'SeuratMarkerSummaryChecks.java';runner.write_text('''import java.io.File;import java.io.PrintWriter;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class SeuratMarkerSummaryChecks {public static void main(String[] args)throws Exception {
if(!org.jlinalg.sequence.SeuratMarkerSummary.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual marker-summary JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SeuratMarkerSummaryTest")).build(),listener);
var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=165||s.getTestsSucceededCount()!=165||s.getTestsSkippedCount()!=0||s.getTotalFailureCount()!=0)throw new AssertionError("Native summary test matrix failed");
System.out.println("PASSED_165_ACTUAL_JAR_MARKER_SUMMARY_TESTS");}}
''',encoding='utf8',newline='\n')
    compiled=out/'runner-classes';compiled.mkdir();testclasses=ROOT/'build/classes/java/test';resources=ROOT/'src/test/resources';classpath=[jar,compiled,testclasses,resources,*libs]
    def command(label,argv):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(out/(label+'.process.json'),dict(command=list(map(str,argv)),exit_code=r.returncode));assert r.returncode==0,label+' failed; inspect retained logs'
    command('compile-runner',[Path(JAVA21).parent/'javac.exe','--release','17','-encoding','UTF-8','-cp',';'.join(map(str,classpath)),'-d',compiled,runner])
    commands={name:[java,'-Xmx512m','-cp',';'.join(map(str,classpath)),'SeuratMarkerSummaryChecks',jar] for name,java in [('windows-java21',JAVA21),('windows-java25',JAVA25)]}
    commands['linux-java21']=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-cp',':'.join(linux(p) for p in classpath),'SeuratMarkerSummaryChecks',linux(jar)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        futures={name:pool.submit(command,name,argv) for name,argv in commands.items()}
        for name,future in futures.items():future.result();assert b'PASSED_165_ACTUAL_JAR_MARKER_SUMMARY_TESTS' in (out/(name+'.stdout')).read_bytes()
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='passed-native-six-edition-marker-summary-actual-JAR-matrix',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),parent=pin(parent),new_class_entries=added,changed_parent_entries=changed,source_pins=sources,canonical_executable_preserved=canonical,tests_per_JVM=165,JVMs=list(commands),source_summary_cases=162,source_postprocessing_matches=156,explicit_missing_identity_refusals=6,source_rounding_values=2638,statistical_tests_validated_by_native_cases=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,released=False,pushed=False,deployed=False))
    print('165 actual-JAR checks pass on each Windows21/25 and Linux21; all parent entries unchanged')

if __name__=='__main__':main()
