# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual-JAR Salmon full-table arithmetic and prior replicate decoding on three JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable complete-population checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--artifact',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    jar=a.artifact.resolve();out=a.out.resolve();base=(ROOT/'build/literature-review/salmon-runtime').resolve();assert jar.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');parent=base/'native-development-1/package-1/omicsmatic-0.3.7-salmon-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        legacy=[n for n in old.namelist() if n.endswith('.class')];assert all(current.read(n)==old.read(n) for n in legacy)
        added=[n for n in current.namelist() if n.endswith('.class') and n not in legacy];assert len(added)==8 and all(n.startswith('org/jlinalg/sequence/SalmonQuantification') for n in added)
    source_names=['src/main/java/org/jlinalg/sequence/SalmonQuantification.java','src/test/java/org/jlinalg/sequence/SalmonQuantificationTest.java',
        'src/main/java/org/jlinalg/sequence/SalmonInferentialReplicates.java','src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java',
        'tools/literature/freeze_salmon_quantification_fixtures.py','tools/literature/salmon-development.init.gradle',Path(__file__).relative_to(ROOT).as_posix(),
        'src/test/resources/org/jlinalg/sequence/salmon-quantification/original-quantification.zip','src/test/resources/org/jlinalg/sequence/salmon-quantification/provenance.json']
    sources={name:pin(ROOT/name) for name in source_names};classes=out/'classes';classes.mkdir();runner=out/'SalmonQuantificationChecks.java'
    runner.write_text('''import java.io.*;
import java.util.*;
import java.util.stream.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonQuantificationChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.SalmonQuantification","org.jlinalg.sequence.SalmonInferentialReplicates"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SalmonQuantificationTest"),selectClass("org.jlinalg.sequence.SalmonInferentialReplicatesTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=87||s.getTestsSucceededCount()!=87||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete quantification audit checks");
  System.out.println("COMPLETE_ORIGINAL_QUANTIFICATION_TABLES 25 ROWS 1000 NATIVE_CASES 87");
 }
}
''',encoding='utf-8',newline='\n')
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    def command(name,argv):
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        receipt=dict(command=list(map(str,argv)),exit_code=r.returncode);write(out/(name+'.json'),receipt);assert r.returncode==0,(name,r.returncode);return receipt
    command('compile',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,ROOT/source_names[1],ROOT/source_names[3]])
    paths=[classes,jar,ROOT/'src/test/resources',*libraries]
    platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def check(spec):
        name,java,path,separator=spec;receipt=command(name,[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'SalmonQuantificationChecks',path(jar)]);log=(out/(name+'.stdout')).read_text()
        assert 'COMPLETE_ORIGINAL_QUANTIFICATION_TABLES 25 ROWS 1000 NATIVE_CASES 87' in log
        print(name+':87 cases and25 complete original tables pass without skips',flush=True);return dict(name=name,tests=87,passed=87,quantification_tests=60,prior_replicate_tests=27,original_successful_tables=25,complete_original_rows=1000,original_failed_prefix_retained=True,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,platforms))
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==row for name,row in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-three-JVM-complete-Salmon-quantification-table-and-rounding-audits',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),runs=runs,source_pins=sources,
        unchanged_parent_classes=len(legacy),new_classes=added,canonical_executable_unchanged=canonical,
        scope='Complete expected transcript identity/order/length, every original printed token, count-sum and per-target TPM compatibility intervals with explicit decimal/binary64 uncertainty. All1000 original rows across25 source quantifications are retained. These are necessary arithmetic checks, not a unique joint latent-vector, fitted model, process-completion, reference/index or biological certificate; a real crashed-process arithmetic prefix is explicitly retained as unadmitted.',
        public_quantification_adapter_available=False,scientific_runtime_admitted=False,Salmon_catalog_status='missing',whole_paper_reproduced=False))


if __name__=='__main__':main()
