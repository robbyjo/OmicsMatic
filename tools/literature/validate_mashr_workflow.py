#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual-JAR numeric/workflow matrix; original R calls are retained references.

This gate does not install software, rerun original R, or admit a study bundle.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from validate_mashr_native import ROOT, BASE, JAVA21, LIBS, pin, linux, NAMES


def main():
    if not __debug__:raise RuntimeError('Run with assertions enabled, never Python -O')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args();jar,out=args.artifact.resolve(),args.out.resolve()
    if not jar.is_relative_to(BASE.resolve()) or not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists():raise ValueError('Require a mashr development JAR and NEW private output')
    out.mkdir(parents=True)
    try:
        libs=sorted(LIBS.glob('*.jar'))
        if len(libs)!=9 or not (JAVA21/'javac.exe').is_file() or not shutil.which('wsl.exe'):
            (out/'WARNINGS.md').write_text('# SKIP3: JVM validation dependencies unavailable\n\nRequired action: provide the declared JDK21/Java25, WSL Java21 and nine-library JUnit closure; retry into a NEW directory. No validation success is admitted.\n',encoding='utf-8',newline='\n');return 3
        names=[*NAMES,'org.jlinalg.cli.MashWorkflowAuditTest']
        tests=[ROOT/'src/test/java'/Path(*n.split('.')).with_suffix('.java') for n in names]
        extra=[ROOT/'src/main/java/org/jlinalg/cli/MashWorkflowAudit.java',ROOT/'src/main/java/org/jlinalg/shrinkage/MashGaussian.java',ROOT/'src/main/java/org/jlinalg/shrinkage/MashMixtureWeights.java',
               ROOT/'src/main/resources/org/jlinalg/omics/mashr-workflow/analysis.R',ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/.gitattributes',Path(__file__),ROOT/'tools/literature/validate_mashr_native.py']
        fixtures=[*sorted((ROOT/'src/test/resources/org/jlinalg/shrinkage/mashr-original').glob('*.zip')),ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip']
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in [*tests,*extra,*fixtures,*libs]}
        artifact=pin(jar);canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonicalPin=pin(canonical)
        with zipfile.ZipFile(jar) as z:
            assert z.read('org/jlinalg/omics/mashr-workflow/analysis.R')==extra[3].read_bytes()
            owned={n:dict(bytes=len(z.read(n)),sha256=hashlib.sha256(z.read(n)).hexdigest()) for n in z.namelist()
                   if n.endswith('.class') and (n.startswith('org/jlinalg/shrinkage/') or n.startswith('org/jlinalg/cli/MashWorkflowAudit'))}
            assert len(owned)==22
        runner=out/'RunMashWorkflowChecks.java'
        runner.write_text('''import java.io.PrintWriter;
import java.io.File;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMashWorkflowChecks {
 private RunMashWorkflowChecks() { }
 public static void main(String[] args) throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));
  System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Unexpected JVM edition");
  for(String name:new String[]{"org.jlinalg.cli.MashWorkflowAudit","org.jlinalg.shrinkage.MashGaussian","org.jlinalg.shrinkage.MashMixtureWeights","jdistlib.accelerator.CpuComputeBackend"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong JAR loaded: "+name);
  var request=LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build();var listener=new SummaryGeneratingListener();
  LauncherFactory.create().execute(request,listener);var summary=listener.getSummary();
  summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=220||summary.getTestsSucceededCount()!=220||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete mashr workflow validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in names)),encoding='utf-8',newline='\n')
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libs])),'-d',str(classes),str(runner),*map(str,tests)]
        with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=120,check=False)
        (out/'compile-command.json').write_text(json.dumps(dict(argv=command,exit_code=process.returncode),indent=2)+'\n',encoding='utf-8',newline='\n');assert process.returncode==0,'Inspect compile.stderr'
        entries=[classes,jar,ROOT/'src/test/resources',*libs];windows=os.pathsep.join(map(str,entries))
        runs=[('windows-java21',[str(JAVA21/'java.exe'),'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',windows,'RunMashWorkflowChecks',str(jar),'21']),
              ('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx128m','-XX:ActiveProcessorCount=2','-cp',windows,'RunMashWorkflowChecks',str(jar),'25']),
              ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','env','JLINALG_MASHR_GAUSSIAN_CONTROLS=','/usr/bin/java','-Xmx128m','-XX:ActiveProcessorCount=2','-cp',':'.join(map(linux,entries)),'RunMashWorkflowChecks',linux(jar),'21'])]
        def execute(row):
            name,command=row
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                result=subprocess.run(command,cwd=ROOT,env=dict(os.environ,JLINALG_MASHR_GAUSSIAN_CONTROLS=''),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=600,check=False)
            record=dict(name=name,argv=command,exit_code=result.returncode)
            (out/(name+'.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n')
            text=(out/(name+'.stdout')).read_text(encoding='utf-8',errors='replace');assert result.returncode==0,name+' failed; inspect its complete stdout/stderr'
            for count,label in [(220,'tests found'),(220,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(count)+' '+label+r'\s*\]',text),(name,label)
            print(name+': 220 passed',flush=True);return record
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:observed=list(pool.map(execute,runs))
        assert pin(jar)==artifact and pin(canonical)==canonicalPin and all(pin(ROOT/n)==v for n,v in bound.items())
        record=dict(schema_version=1,status='passed-actual-JAR-native-mashr-workflow-numeric-matrix',tests_per_JVM=220,heap_mib=128,
           artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_and_dependency_pins=bound,packaged_owned_classes=owned,runs=observed,
           canonical_artifact_unchanged=canonicalPin,original_R_rerun_in_matrix=False,public_runtime_or_bundle_admitted=False,whole_paper_reproduced=False,
           files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()})
        (out/'COMPLETED.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n');return 0
    except Exception as error:
        (out/'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_MASHR_WORKFLOW_MATRIX',message=str(error)),indent=2)+'\n',encoding='utf-8',newline='\n')
        (out/'WARNINGS.md').write_text('# FAIL2: workflow validation incomplete\n\n'+str(error)+'\n\nRequired action: preserve this attempt. Inspect compile and platform stdout/stderr. Correct the named failure and retry into a NEW directory; no partial success is admitted.\n',encoding='utf-8',newline='\n');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('MASHR WORKFLOW MATRIX FAIL2: '+str(failure),file=sys.stderr);sys.exit(2)
