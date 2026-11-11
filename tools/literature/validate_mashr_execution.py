#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate the actual native mash execution JAR, source references and file commands.

No installation, source-package rebuild, study-bundle admission or publication.
"""
import argparse
import concurrent.futures
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from validate_mashr_native import ROOT,BASE,JAVA21,LIBS,NAMES,pin,linux

TESTS=295
EXAMPLES=ROOT/'examples/mashr-native-execution'
OWNED={
    'shrinkage/MashFit':['','1','Candidate','Fit','Limits','Listener','NullEstimate','Options','ResidualMode','Status','Step','Work'],
    'shrinkage/MashGaussian':['','Basis','Component','Evaluation','Limits','Mixture','Model','Options','Posterior'],
    'shrinkage/MashMixtureWeights':['','Evaluation','Fit','Iteration','Limits','Options','Sum','Work'],
    'cli/MashNativeExecution':['','Data','Evidence','EvidenceFailure','IO','Limits','Pin','Population','Result','Sink'],
    'cli/MashWorkflowAudit':['','Field','Limits','Population','Result','Statistics'],
}


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def table(path):return [[float(x) for x in line.split('\t')] for line in path.read_text().splitlines()]


def near(observed,expected):
    assert len(observed)==len(expected),(observed,expected)
    for a,b in zip(observed,expected):assert math.isfinite(a) and abs(a-b)<=1e-10+1e-9*abs(b),(a,b)


def verify_manifest(folder):
    names=set()
    for line in (folder/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);assert name not in names;names.add(name)
        assert (folder/name).resolve().is_relative_to(folder.resolve()) and pin(folder/name)['sha256']==digest,name
    assert names=={p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file()}-{'SHA256SUMS'}


def analytic_prediction(fit,result):
    proportions=[r[0] for r in table(fit/'model/proportions.tsv')];near(proportions,[.9,.1])
    near(table(fit/'posterior/mean.tsv')[0],[2000/201]*2);near(table(fit/'posterior/sd.tsv')[0],[math.sqrt(100/201)]*2)
    ll=[-.5*(2*math.log(2*math.pi)+13),-.5*(2*math.log(2*math.pi)+math.log(201)+13-100/201)]
    scores=[math.log(p)+x for p,x in zip(proportions,ll)];maximum=max(scores);loglik=maximum+math.log(sum(math.exp(s-maximum) for s in scores));weight=math.exp(scores[1]-loglik)
    conditional=-100/201;mean=weight*conditional;variance=weight*100/201+weight*(1-weight)*conditional**2
    negative=weight*.5*math.erfc(conditional/math.sqrt(2*100/201));zero=1-weight;lfsr=zero+min(negative,weight-negative)
    expected={'mean':[mean]*2,'sd':[math.sqrt(variance)]*2,'negative':[negative]*2,'zero':[zero]*2,'lfsr':[lfsr]*2,
              'weights':[1-weight,weight],'component-loglik':ll,'covariance-column-major':[variance]*4,
              'standardized-residual-mean':[2-mean,-3-mean],'loglik':[loglik,loglik,1]}
    for name,values in expected.items():near(table(result/'posterior'/f'{name}.tsv')[0],values)
    return dict(proportions=proportions,prediction_mean=mean,prediction_variance=variance,checks=sum(map(len,expected.values()))+6)


def main():
    if not __debug__:raise RuntimeError('Never disable scientific/provenance assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args()
    jar,out=args.artifact.resolve(),args.out.resolve()
    if not jar.is_relative_to(BASE.resolve()) or not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists():raise ValueError('Require a mashr development JAR and NEW private output')
    out.mkdir(parents=True)
    try:
        libs=sorted(LIBS.glob('*.jar'))
        if len(libs)!=9 or not (JAVA21/'javac.exe').is_file() or not shutil.which('wsl.exe'):
            (out/'WARNINGS.md').write_text('# SKIP3: JVM validation dependency unavailable\n\nRequired action: provide JDK21/Java25, WSL Java21 and the nine cached JUnit libraries. Retry into a NEW directory. No validation passed.\n',encoding='utf-8');return 3
        names=[*NAMES,'org.jlinalg.shrinkage.MashFitTest','org.jlinalg.cli.MashWorkflowAuditTest','org.jlinalg.cli.MashNativeExecutionTest']
        tests=[ROOT/'src/test/java'/Path(*n.split('.')).with_suffix('.java') for n in names]
        mains=[ROOT/'src/main/java/org/jlinalg'/Path(n).with_suffix('.java') for n in OWNED]
        fixtures=[*sorted((ROOT/'src/test/resources/org/jlinalg/shrinkage/mashr-original').glob('*.zip')),ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip',ROOT/'src/test/resources/org/jlinalg/cli/mashr-native/residual-reference.zip']
        extra=[Path(__file__),ROOT/'tools/literature/validate_mashr_native.py',ROOT/'tools/literature/collect_mashr_residual_reference.py',ROOT/'tools/literature/mashr-residual-reference.R',ROOT/'tools/literature/freeze_mashr_residual_reference.py',
               ROOT/'tools/literature/mashr-development.init.gradle',ROOT/'src/main/resources/org/jlinalg/omics/mashr-workflow/analysis.R',ROOT/'src/main/resources/org/jlinalg/omics/mashr-BSD-NOTICE.txt',ROOT/'build/dependencies/jdistlib-all-0.10.2.jar',
               *sorted((ROOT/'src/test/resources/org/jlinalg/cli').glob('mashr-*/.gitattributes')),*sorted(EXAMPLES.rglob('*.tsv'))]
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in [*tests,*mains,*fixtures,*extra,*libs]}
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        assert canonical_pin==dict(bytes=162107844,sha256='bd1a3b2448d7016ae4ded3f30a6c5ae92e05a0078ae688f4b93d046cc355eeb2')
        assert pin(ROOT/'build/dependencies/jdistlib-all-0.10.2.jar')['sha256']=='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
        artifact=pin(jar);expected={'org/jlinalg/'+name+('$'+inner if inner else '')+'.class' for name,inners in OWNED.items() for inner in inners}
        with zipfile.ZipFile(jar) as archive:
            actual={n for n in archive.namelist() if n.endswith('.class') and (n.startswith('org/jlinalg/shrinkage/') or n.startswith('org/jlinalg/cli/MashNativeExecution') or n.startswith('org/jlinalg/cli/MashWorkflowAudit'))}
            assert actual==expected,(actual-expected,expected-actual)
            owned={n:dict(bytes=len(archive.read(n)),sha256=hashlib.sha256(archive.read(n)).hexdigest()) for n in sorted(actual)}
            for name in actual:assert archive.read(name)[6:8]==b'\x00=',name
            for name in ['org/jlinalg/omics/mashr-workflow/analysis.R','org/jlinalg/omics/mashr-BSD-NOTICE.txt']:assert archive.read(name)==(ROOT/'src/main/resources'/name).read_bytes()
        runner=out/'RunMashExecutionChecks.java'
        runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMashExecutionChecks {
 private RunMashExecutionChecks() { }
 public static void main(String[] args) throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));
  System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM edition");
  for(String name:new String[]{"org.jlinalg.cli.MashNativeExecution","org.jlinalg.cli.MashWorkflowAudit","org.jlinalg.shrinkage.MashFit","org.jlinalg.shrinkage.MashGaussian","org.jlinalg.shrinkage.MashMixtureWeights","jdistlib.accelerator.CpuComputeBackend"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact loaded: "+name);
  var request=LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build();var listener=new SummaryGeneratingListener();
  LauncherFactory.create().execute(request,listener);var summary=listener.getSummary();
  summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=COUNT||summary.getTestsSucceededCount()!=COUNT||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native execution matrix");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in names)).replace('COUNT',str(TESTS)),encoding='utf-8',newline='\n')
        classes=out/'classes';classes.mkdir()

        def command(argv,prefix,timeout=600):
            with prefix.with_suffix('.stdout').open('xb') as stdout,prefix.with_suffix('.stderr').open('xb') as stderr:
                result=subprocess.run(argv,cwd=ROOT,env=dict(os.environ,JLINALG_MASHR_GAUSSIAN_CONTROLS=''),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout,check=False)
            record=dict(argv=argv,exit_code=result.returncode);write(prefix.with_suffix('.json'),record);return record

        compiled=command([str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libs])),'-d',str(classes),str(runner),*map(str,tests)],out/'compile',120)
        assert compiled['exit_code']==0,'Inspect compile.stderr'
        entries=[classes,jar,ROOT/'src/test/resources',*libs]
        platforms=[('windows-java21',[str(JAVA21/'java.exe')],'21',str,os.pathsep),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'25',str,os.pathsep),
                   ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],'21',linux,':')]

        def execute(platform):
            name,java,version,path,separator=platform;folder=out/name;folder.mkdir()
            checked=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,entries)),'RunMashExecutionChecks',path(jar),version],folder/'junit')
            assert checked['exit_code']==0,name+': inspect junit.stdout/stderr'
            text=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for count,label in [(TESTS,'tests found'),(TESTS,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(count)+' '+label+r'\s*\]',text),(name,label)
            base=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',path(jar),'org.jlinalg.cli.MashNativeExecution']
            fit=folder/'fit';fitted=command([*base,'--input',path(EXAMPLES/'fit-input'),'--out',path(fit)],folder/'fit-command');assert fitted['exit_code']==0
            request=folder/'predict-input';shutil.copytree(EXAMPLES/'predict-input',request);shutil.copytree(fit/'model',request/'model')
            result=folder/'prediction';predicted=command([*base,'--input',path(request),'--out',path(result)],folder/'predict-command');assert predicted['exit_code']==0
            verify_manifest(fit);verify_manifest(result);analytic=analytic_prediction(fit,result)
            for prefix in ['fit-command','predict-command']:
                assert 'DEVELOPMENT ONLY' in (folder/(prefix+'.stderr')).read_text()
                assert 'NATIVE_MASH_DEVELOPMENT_COMPLETE' in (folder/(prefix+'.stdout')).read_text()
            bad=folder/'invalid-input';shutil.copytree(EXAMPLES/'fit-input',bad);(bad/'standard-errors.tsv').write_text('feature\tA\tB\n0001\t0\t1\n',encoding='utf-8',newline='\n')
            failure=folder/'invalid-output';invalid=command([*base,'--input',path(bad),'--out',path(failure)],folder/'invalid-command');assert invalid['exit_code']==2
            assert not (failure/'result.tsv').exists() and not (failure/'SHA256SUMS').exists() and (failure/'FAILURE.txt').is_file()
            for warning in [(failure/'WARNINGS.md').read_text(),(folder/'invalid-command.stderr').read_text()]:assert 'FAIL2' in warning and 'Required action' in warning and 'NEW output directory' in warning
            print(name+': '+str(TESTS)+' tests; real fit/predict/FAIL2 commands passed',flush=True)
            return dict(name=name,junit=checked,commands=[fitted,predicted,invalid],analytic=analytic)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(execute,platforms))
        assert pin(jar)==artifact and pin(canonical)==canonical_pin and all(pin(ROOT/name)==value for name,value in bound.items())
        record=dict(schema_version=1,status='passed-actual-JAR-native-mash-file-execution-matrix',tests_per_JVM=TESTS,heap_mib=128,runs=runs,
                    artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_and_dependency_pins=bound,packaged_owned_classes=owned,
                    canonical_artifact_unchanged=canonical_pin,original_R_rerun_in_matrix=False,public_runtime_or_bundle_admitted=False,whole_paper_reproduced=False,
                    files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()})
        write(out/'COMPLETED.json',record);return 0
    except Exception as error:
        write(out/'FAILURE.json',dict(status='FAIL2_NATIVE_MASH_EXECUTION_MATRIX',message=str(error)))
        (out/'WARNINGS.md').write_text('# FAIL2: native execution validation incomplete\n\n'+str(error)+'\n\nRequired action: preserve this attempt; inspect compile and platform logs. Correct the named failure and retry into a NEW directory. No partial validation passed.\n',encoding='utf-8',newline='\n');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:print('FAIL2_NATIVE_MASH_EXECUTION_MATRIX: '+str(error),file=sys.stderr);sys.exit(2)
