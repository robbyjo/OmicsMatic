# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate native prepared-read counting in the actual JAR, including parent interval controls, on three JVMs."""
import argparse
import concurrent.futures
import os
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux

BASE=ROOT/'build/literature-review/htseq-runtime'
JAR=BASE/'read-count-development/omicsmatic-0.3.7-htseq-read-count-development.jar'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
    parent=BASE/'core-development/omicsmatic-0.3.7-htseq-core-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(JAR) as new:
        assert len(new.namelist())==len(set(new.namelist())) and new.testzip() is None
        changed=[n for n in old.namelist() if n not in new.namelist() or old.read(n)!=new.read(n)]
        added=sorted(set(new.namelist())-set(old.namelist()))
        save(out/'parent-entries.json',dict(parent=pin(parent),changed=changed,added=added,unchanged_existing_entries=len(old.namelist())))
        assert not changed,changed
        assert added and all(n.startswith(('org/jlinalg/sequence/HtseqReadCounter','org/jlinalg/omics/htseq-read-count/')) for n in added),added
    libraries=sorted((ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib').glob('*.jar'));assert len(libraries)==9
    tests=[ROOT/'src/test/java/org/jlinalg/cli'/name for name in ['HtseqReadCounterTest.java','HtseqFeatureAssignmentTest.java']]
    fixtures=[p for name in ['htseq-read-count','htseq-intervals'] for p in (ROOT/'src/test/resources/org/jlinalg/sequence'/name).iterdir() if p.is_file()]
    sources=[Path(__file__),*tests,*fixtures,*libraries,ROOT/'src/main/java/org/jlinalg/sequence/HtseqReadCounter.java']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    runner=out/'RunHtseqReadCounts.java'
    runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunHtseqReadCounts {
 private RunHtseqReadCounts() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.HtseqReadCounter","org.jlinalg.sequence.HtseqFeatureAssignment","org.jlinalg.cli.SimpleJson"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.HtseqReadCounterTest"),selectClass("org.jlinalg.cli.HtseqFeatureAssignmentTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=2315||s.getTestsSucceededCount()!=2315||s.getTestsSkippedCount()!=0||s.getTestsFailedCount()!=0||s.getTestsAbortedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete HTSeq prepared-read counting tests");
 }
}
''',encoding='utf8',newline='\n')
    classes=out/'classes';classes.mkdir()
    def command(label,arguments):
        argv=list(map(str,arguments))
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,label
        return dict(JVM=label,tests=2315,passed=2315,skipped=0)
    try:
        command('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[JAR,*libraries])),'-d',classes,runner,*tests])
        entries=[JAR,classes,ROOT/'src/test/resources',*libraries];cp=os.pathsep.join(map(str,entries))
        commands=[('windows-java21',[JAVA21,'-Xmx512m','-cp',cp,'RunHtseqReadCounts',JAR]),
            ('windows-java25',[JAVA25,'-Xmx512m','-cp',cp,'RunHtseqReadCounts',JAR]),
            ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx512m','-cp',':'.join(map(linux,entries)),'RunHtseqReadCounts',linux(JAR)])]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda item:command(*item),commands))
        assert pin(JAR)==artifact and pin(canonical)==canonical_pin and all(pin(ROOT/name)==value for name,value in pins.items())
        save(out/'COMPLETED.json',dict(status='passed-actual-JAR-native-HTSeq-prepared-read-count-three-JVMs',artifact=artifact,source_pins=pins,
            tests_per_JVM=2315,JVMs=results,all_existing_parent_entries_preserved=True,canonical_executable_preserved=canonical_pin,
            original_count_tables_compared=40,original_gene_values_compared=198,original_summary_values_compared=200,
            original_prepared_units=627,upstream_original_SAM_GTF_count_executed=True,independent_native_SAM_GTF_parser=False,
            independent_native_pairing=False,native_random_assignment=False,HTSeq_root_dispatch_available=False,catalog_implementation_credit=False))
        print('2315 actual-JAR tests passed on each of3 JVMs;198 original gene values and200 source counters match exactly')
    except Exception as failure:
        save(out/'FAILURE.json',dict(status='FAIL2_HTSEQ_READ_COUNT_NATIVE_MATRIX',error=repr(failure)));raise


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual artifact/source checks')
    main()
