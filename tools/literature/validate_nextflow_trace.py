# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the actual native trace JAR on three JVMs; preserve every prior class."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/nextflow-runtime';jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(base.resolve()) and out.is_relative_to(base.resolve()) and not out.exists();out.mkdir()
    try:
        previous=json.loads((ROOT/'docs/literature/fuma-expression-preparation-2/VALIDATION.json').read_bytes());prior=ROOT/previous['artifact']['path'];assert pin(prior)=={k:previous['artifact'][k] for k in ['bytes','sha256']}
        canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==previous['canonical_executable_unchanged']
        artifact=pin(jar);libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        files=[ROOT/'src/main/java/org/jlinalg/workflow/NextflowTraceAudit.java',ROOT/'src/test/java/org/jlinalg/workflow/NextflowTraceAuditTest.java',ROOT/'src/test/resources/org/jlinalg/workflow/nextflow/original-engine-controls.zip',ROOT/'src/test/resources/org/jlinalg/workflow/nextflow/.gitattributes',Path(__file__),ROOT/'tools/literature/nextflow-development.init.gradle',ROOT/'tools/literature/collect_nextflow_runtime_controls.py',ROOT/'tools/literature/freeze_nextflow_runtime_controls.py']
        sources={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        with zipfile.ZipFile(prior) as old,zipfile.ZipFile(jar) as current:
            old_names={n for n in old.namelist() if n.endswith('.class')};names={n for n in current.namelist() if n.endswith('.class')}
            extra=names-old_names;assert len(extra)==5 and all(n.startswith('org/jlinalg/workflow/NextflowTraceAudit') for n in extra)
            assert old_names<=names and all(old.read(n)==current.read(n) for n in old_names),'A preexisting packaged class changed'
            owned={n:dict(bytes=len(current.read(n)),sha256=hashlib.sha256(current.read(n)).hexdigest()) for n in sorted(extra)}
            assert all(current.read(n)[6:8]==b'\x00=' for n in extra)
        runner=out/'RunNextflowTraceChecks.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunNextflowTraceChecks {
 private RunNextflowTraceChecks() { }
 public static void main(String[] args)throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM");
  if(!Class.forName("org.jlinalg.workflow.NextflowTraceAudit").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual artifact");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.workflow.NextflowTraceAuditTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=30||s.getTestsSucceededCount()!=30||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native trace verification");
 }
}
''',encoding='utf-8',newline='\n')
        def command(argv,folder,label):
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(list(map(str,argv)),cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=180)
            result=dict(argv=list(map(str,argv)),exit_code=process.returncode);write(folder/(label+'.json'),result);return result
        classes=out/'classes';classes.mkdir();compiled=command([JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,ROOT/'src/test/java/org/jlinalg/workflow/NextflowTraceAuditTest.java'],out,'compile');assert compiled['exit_code']==0,'Inspect compile.stderr'
        paths=[classes,jar,ROOT/'src/test/resources',*libraries]
        platforms=[('windows-java21',[JAVA21/'java.exe'],'21',str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],'25',str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],'21',linux,':')]
        def check(spec):
            name,java,version,path,separator=spec;folder=out/name;folder.mkdir();result=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'RunNextflowTraceChecks',path(jar),version],folder,'junit');assert result['exit_code']==0,name+': inspect complete junit logs'
            log=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for count,label in [(30,'tests found'),(30,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(count)+' '+label+r'\s*\]',log),(name,label)
            print(name+':30 actual-JAR native trace checks passed',flush=True);return dict(name=name,process=result)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,platforms))
        assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
        result=dict(schema_version=1,status='passed-three-JVM-native-Nextflow-trace-foundation',tests_per_JVM=30,heap_mib=128,original_engine_controls=15,runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,unchanged_prior_packaged_classes=len(old_names),new_packaged_classes=owned,canonical_executable_unchanged=canonical,public_adapter_available=False,catalog_promoted=False,whole_paper_reproduced=False,scope='Bounded complete single-attempt trace summary using an independently declared row count. Does not certify unrecorded tasks, workflow outputs, runtime provenance or biological results. Repeated task IDs require a future attempt-aware contract.',files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()})
        write(out/'COMPLETED.json',result);return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_NEXTFLOW_TRACE_MATRIX',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_NEXTFLOW_TRACE_MATRIX: '+str(failure)+'\nRequired action: retain the failed attempt, inspect the named source/compile/test mismatch and rerun to new paths.',file=sys.stderr);sys.exit(2)
