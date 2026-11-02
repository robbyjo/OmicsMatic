#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual-JAR public proxy command, prior native tests and original-function controls."""
import argparse, concurrent.futures, hashlib, json, os, re, subprocess, sys, zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from validate_mashr_native import ROOT, BASE, JAVA21, LIBS, NAMES, linux, pin
COUNT=477
PREFIX='org/jlinalg/cli/MashSummaryCli'
SOURCE='src/main/java/org/jlinalg/cli/MashSummaryCli.java'
NEW={PREFIX+x+'.class' for x in ['$1','$ProxyCounts','$ProxyDeclaration','$ProxyFamily']}
# Every nested class carries nest/line metadata from this single changed source.
CHANGED={PREFIX+x+'.class' for x in ['', '$Settings', '$Computed', '$Native', '$Counts', '$Sink', '$Verification', '$Unavailable', '$Groups']}


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    assert __debug__, 'Do not disable evidence assertions'
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        prior=json.loads((ROOT/'docs/literature/mashr-composite/VALIDATION.json').read_text(encoding='utf-8'))
        old_sources={**prior['protected_prior_source_pins'],**prior['source_pins']}
        unchanged={n:v for n,v in old_sources.items() if n!=SOURCE};assert all(pin(ROOT/n)==v for n,v in unchanged.items())
        snapshot=BASE/'proxy-command-development-1/before-extension/MashSummaryCli.java';assert pin(snapshot)==old_sources[SOURCE] and pin(ROOT/SOURCE)!=old_sources[SOURCE]
        names=[*NAMES,'org.jlinalg.shrinkage.MashFitTest','org.jlinalg.cli.MashWorkflowAuditTest','org.jlinalg.cli.MashNativeExecutionTest','org.jlinalg.cli.MashToolsCliTest','org.jlinalg.shrinkage.MashSharingTest','org.jlinalg.shrinkage.MashSharingOriginalTest','org.jlinalg.cli.MashSummaryCliTest','org.jlinalg.shrinkage.MashCompositeScoresTest','org.jlinalg.shrinkage.MashCompositeOriginalTest','org.jlinalg.cli.MashProxySummaryCliTest']
        tests=[ROOT/'src/test/java'/Path(*n.split('.')).with_suffix('.java') for n in names]
        sources=[*tests,Path(__file__),ROOT/SOURCE,ROOT/'src/main/java/org/jlinalg/shrinkage/MashCompositeScores.java',ROOT/'src/test/resources/org/jlinalg/shrinkage/mashr-composite/original-controls.zip']
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources};artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==prior['canonical_executable_unchanged']
        old_jar=ROOT/prior['artifact']['path'];assert pin(old_jar)=={k:prior['artifact'][k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(jar) as current,zipfile.ZipFile(old_jar) as old:
            def owned(n):return n.endswith('.class') and (n.startswith('org/jlinalg/shrinkage/') or n.startswith('org/jlinalg/cli/Mash') or n in ['org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SimpleJson.class'])
            old_names={n for n in old.namelist() if owned(n)};actual={n for n in current.namelist() if owned(n)}
            assert actual==old_names|NEW,(actual-(old_names|NEW),(old_names|NEW)-actual)
            changed={n for n in old_names if current.read(n)!=old.read(n)};assert changed==CHANGED,changed
            classes={n:dict(bytes=len(current.read(n)),sha256=hashlib.sha256(current.read(n)).hexdigest()) for n in sorted(actual)}
            assert all(current.read(n)[6:8]==b'\x00=' for n in actual)
        runner=out/'RunMashProxyCommandChecks.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMashProxyCommandChecks {
 private RunMashProxyCommandChecks() { }
 public static void main(String[] args)throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM");
  for(String name:new String[]{"org.jlinalg.cli.MashSummaryCli","org.jlinalg.shrinkage.MashCompositeScores","org.jlinalg.shrinkage.MashSharing","org.jlinalg.shrinkage.MashSignScores","org.jlinalg.shrinkage.MashGaussian","org.jlinalg.cli.MashToolsCli","jdistlib.accelerator.CpuComputeBackend"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong JAR: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=COUNT||summary.getTestsSucceededCount()!=COUNT||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete proxy/source/native validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in names)).replace('COUNT',str(COUNT)),encoding='utf-8',newline='\n')
        def command(argv,folder,label,timeout=1200):
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(list(map(str,argv)),cwd=ROOT,env=dict(os.environ,JLINALG_MASHR_GAUSSIAN_CONTROLS=''),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout,check=False)
            record=dict(argv=list(map(str,argv)),exit_code=result.returncode);write(folder/(label+'.json'),record);return record
        classes_dir=out/'classes';classes_dir.mkdir();compiled=command([JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes_dir,runner,*tests],out,'compile',120);assert compiled['exit_code']==0,'Inspect compile.stderr'
        paths=[classes_dir,jar,ROOT/'src/test/resources',*libraries]
        platforms=[('windows-java21',[JAVA21/'java.exe'],'21',str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],'25',str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],'21',linux,':')]
        def run(spec):
            name,java,version,path,separator=spec;folder=out/name;folder.mkdir();result=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'RunMashProxyCommandChecks',path(jar),version],folder,'junit')
            assert result['exit_code']==0,name+': inspect complete junit logs';log=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for n,label in [(COUNT,'tests found'),(COUNT,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(n)+' '+label+r'\s*\]',log),(name,label)
            print(name+': '+str(COUNT)+' actual-JAR tests passed',flush=True);return dict(name=name,process=result)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
        assert all(pin(ROOT/n)==v for n,v in bound.items()) and all(pin(ROOT/n)==v for n,v in unchanged.items()) and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-three-JVM-native-proxy-summary-command-matrix',tests_per_JVM=COUNT,new_command_tests=37,original_R_library_cases=30,heap_mib=128,runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=bound,unchanged_prior_source_pins=unchanged,changed_prior_source=dict(path=SOURCE,before=old_sources[SOURCE],after=bound[SOURCE],snapshot=str(snapshot.relative_to(ROOT).as_posix())),owned_classes=classes,protected_packaged_classes_unchanged=len(old_names)-len(CHANGED),changed_packaged_classes=sorted(CHANGED),new_packaged_classes=sorted(NEW),canonical_executable_unchanged=canonical,catalog_promoted=False,whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_PROXY_COMMAND_MATRIX',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_PROXY_COMMAND_MATRIX: '+str(failure),file=sys.stderr);sys.exit(2)
