#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Three-JVM actual-JAR public mash fit/predict/admission/replay and failure checks."""
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
from validate_mashr_native import ROOT,BASE,JAVA21,LIBS,NAMES,pin,linux
from validate_mashr_execution import OWNED,analytic_prediction
from catalog_source_integrity import verify_source

COUNT=325
PUBLIC={'cli/MashBundle':['','Replay','Statistics','Verified'],'cli/MashFiles':['','1','2','Inventory','Pin'],'cli/MashLimits':[''],'cli/MashJson':[''],'cli/MashToolsCli':['','Unavailable']}


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable scientific/provenance assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args()
    jar,out=args.artifact.resolve(),args.out.resolve()
    if not jar.is_relative_to(BASE.resolve()) or not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists():raise ValueError('Require a mashr candidate and NEW private matrix output')
    out.mkdir(parents=True)
    try:
        libs=sorted(LIBS.glob('*.jar'))
        if len(libs)!=9 or not (JAVA21/'javac.exe').is_file() or not shutil.which('wsl.exe'):
            (out/'WARNINGS.md').write_text('# SKIP3: validation dependencies unavailable\n\nRequired action: provide the existing declared JDK21/Java25, WSL Java21 and nine-library JUnit closure; retry into a NEW directory. No validation passed.\n',encoding='utf-8');return 3
        previous_file=ROOT/'docs/literature/MASHR_NATIVE_EXECUTION_VALIDATION.json';previous=json.loads(previous_file.read_text());bound=dict(previous['source_pins'])
        assert all(pin(ROOT/n)==v for n,v in bound.items()),'A protected native source changed; require new scientific validation instead of refreshing old hashes'
        names=[*NAMES,'org.jlinalg.shrinkage.MashFitTest','org.jlinalg.cli.MashWorkflowAuditTest','org.jlinalg.cli.MashNativeExecutionTest','org.jlinalg.cli.MashToolsCliTest']
        tests=[ROOT/'src/test/java'/Path(*n.split('.')).with_suffix('.java') for n in names]
        extras=[Path(__file__),previous_file,ROOT/'tools/literature/mashr_dispatch_bridge.py',ROOT/'tools/literature/catalog_source_integrity.py',ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',ROOT/'src/main/java/org/jlinalg/cli/SimpleJson.java',ROOT/'docs/literature/omics-2023-2026/mashr-dispatch-shared-source-compatibility.json',
                *[ROOT/'src/main/java/org/jlinalg'/Path(n).with_suffix('.java') for n in PUBLIC],*sorted((ROOT/'src/main/resources/org/jlinalg/omics/mashr-native-example').rglob('*'))]
        extras=[p for p in extras if p.is_file()]
        for p in [*tests,*extras]:bound[p.relative_to(ROOT).as_posix()]=pin(p)
        bridge=json.loads((ROOT/'docs/literature/omics-2023-2026/mashr-dispatch-shared-source-compatibility.json').read_text())
        assert len(bridge['paths'])==1 and bridge['paths'][0]['path']=='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',bridge['paths'][0]['path'],bridge['paths'][0]['before_sha256'])
        artifact=pin(jar);canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical);assert canonical_pin==previous['canonical_artifact_unchanged']
        expected={'org/jlinalg/'+name+('$'+inner if inner else '')+'.class' for name,inners in dict(OWNED,**PUBLIC).items() for inner in inners}
        expected.update({'org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SimpleJson.class'})
        with zipfile.ZipFile(jar) as archive:
            actual={n for n in archive.namelist() if n.endswith('.class') and (n.startswith('org/jlinalg/shrinkage/') or n.startswith('org/jlinalg/cli/Mash') or n in {'org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SimpleJson.class'})}
            assert actual==expected,(actual-expected,expected-actual)
            owned={n:dict(bytes=len(archive.read(n)),sha256=hashlib.sha256(archive.read(n)).hexdigest()) for n in sorted(actual)}
            for n in actual:assert archive.read(n)[6:8]==b'\x00=',n
            resources=[p for p in extras if p.is_relative_to(ROOT/'src/main/resources') and p.name!='.gitattributes']
            assert len(resources)==11 and all(p.suffix=='.tsv' for p in resources)
            expected_resources={p.relative_to(ROOT/'src/main/resources').as_posix() for p in resources}
            assert {n for n in archive.namelist() if n.startswith('org/jlinalg/omics/mashr-native-example/') and not n.endswith('/')}==expected_resources
            for p in resources:assert archive.read(p.relative_to(ROOT/'src/main/resources').as_posix())==p.read_bytes()
        runner=out/'RunMashPublicChecks.java'
        runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMashPublicChecks {
 private RunMashPublicChecks() { }
 public static void main(String[] args) throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM edition");
  for(String name:new String[]{"org.jlinalg.cli.JLinAlgCli","org.jlinalg.cli.MashToolsCli","org.jlinalg.cli.MashBundle","org.jlinalg.cli.MashLimits","org.jlinalg.cli.MashFiles","org.jlinalg.cli.MashJson","org.jlinalg.cli.MashNativeExecution","org.jlinalg.shrinkage.MashFit","jdistlib.accelerator.CpuComputeBackend"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual artifact: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var summary=listener.getSummary();
  summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=COUNT||summary.getTestsSucceededCount()!=COUNT||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete public native mash validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in names)).replace('COUNT',str(COUNT)),encoding='utf-8',newline='\n')
        classes=out/'classes';classes.mkdir()
        def command(argv,prefix,timeout=600):
            with prefix.with_suffix('.stdout').open('xb') as stdout,prefix.with_suffix('.stderr').open('xb') as stderr:process=subprocess.run(argv,cwd=ROOT,env=dict(os.environ,JLINALG_MASHR_GAUSSIAN_CONTROLS=''),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout,check=False)
            result=dict(argv=argv,exit_code=process.returncode);write(prefix.with_suffix('.json'),result);return result
        compiled=command([str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libs])),'-d',str(classes),str(runner),*map(str,tests)],out/'compile',120);assert compiled['exit_code']==0,'Inspect compile.stderr'
        entries=[classes,jar,ROOT/'src/test/resources',*libs]
        platforms=[('windows-java21',[str(JAVA21/'java.exe')],'21',str,os.pathsep),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'25',str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],'21',linux,':')]
        def execute(platform):
            name,java,version,path,separator=platform;folder=out/name;folder.mkdir()
            tested=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,entries)),'RunMashPublicChecks',path(jar),version],folder/'junit');assert tested['exit_code']==0,name+': inspect full junit logs'
            text=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for n,label in [(COUNT,'tests found'),(COUNT,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(n)+' '+label+r'\s*\]',text),(name,label)
            base=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-jar',path(jar),'mashr-tools'];commands=[]
            def cli(label,arguments,expected=0):
                result=command([*base,*arguments],folder/label);commands.append(result);assert result['exit_code']==expected,(name,label);return result
            exported=folder/'exported';cli('export',['--export-example',path(exported)])
            fit=folder/'fit';cli('fit',['--input',path(exported/'fit-input'),'--out',path(fit)])
            retained=exported/'producer-input-retained';assert (exported/'fit-input').resolve().is_relative_to(out) and retained.resolve().is_relative_to(out);(exported/'fit-input').rename(retained)
            cli('fit-verify',['--verify',path(fit)])
            request=folder/'prediction-input';shutil.copytree(exported/'predict-input',request);shutil.copytree(fit/'execution/model',request/'model');prediction=folder/'prediction';cli('predict',['--input',path(request),'--out',path(prediction)]);cli('prediction-verify',['--verify',path(prediction)])
            analytic=analytic_prediction(fit/'execution',prediction/'execution')
            bad=folder/'invalid-input';shutil.copytree(retained,bad);(bad/'standard-errors.tsv').write_text('feature\tA\tB\n0001\t0\t1\n',encoding='utf-8',newline='\n');invalid=folder/'invalid-output';cli('invalid',['--input',path(bad),'--out',path(invalid)],2)
            unavailable=folder/'unavailable-input';shutil.copytree(retained,unavailable);settings=unavailable/'settings.tsv';settings.write_text(settings.read_text().replace('basis\tsupplied','basis\tcanonical_pca_ed'),encoding='utf-8',newline='\n');skipped=folder/'skipped-output';cli('unavailable',['--input',path(unavailable),'--out',path(skipped)],3)
            for result,label in [(invalid,'FAIL2'),(skipped,'SKIP3')]:assert not (result/'COMPLETED.json').exists() and (result/'FAILURE.json').is_file() and label in (result/'WARNINGS.md').read_text() and 'Required action' in (result/'WARNINGS.md').read_text()
            assert not (skipped/'execution').exists()
            for label,root in [('invalid','FAIL2'),('unavailable','SKIP3')]:assert root in (folder/(label+'.stderr')).read_text() and 'Required action' in (folder/(label+'.stderr')).read_text()
            print(name+': '+str(COUNT)+' tests; seven real public export/fit/verify/predict/FAIL2/SKIP3 commands passed',flush=True)
            return dict(name=name,junit=tested,commands=commands,analytic=analytic)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(execute,platforms))
        assert pin(jar)==artifact and pin(canonical)==canonical_pin and all(pin(ROOT/n)==v for n,v in bound.items())
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-actual-JAR-public-native-mash-workflow-matrix',tests_per_JVM=COUNT,heap_mib=128,runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=bound,owned_classes=owned,canonical_artifact_unchanged=canonical_pin,
                original_R_rerun=False,upstream_covariance_learning_integrated=False,whole_paper_reproduced=False,catalog_promoted=False,
                files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:
        write(out/'FAILURE.json',dict(status='FAIL2_MASH_PUBLIC_MATRIX',error=str(failure)))
        (out/'WARNINGS.md').write_text('# FAIL2: public mash validation incomplete\n\n'+str(failure)+'\n\nRequired action: preserve this attempt and inspect full compiler/platform logs. Correct the named cause and retry into a NEW output. No partial validation passed.\n',encoding='utf-8');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:print('FAIL2_MASH_PUBLIC_MATRIX: '+str(error),file=sys.stderr);sys.exit(2)
