#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual-JAR sharing/source controls plus the protected native tests on three JVMs."""
import argparse,concurrent.futures,hashlib,json,os,re,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from validate_mashr_native import ROOT,BASE,JAVA21,LIBS,NAMES,linux,pin

COUNT=371
NEW={'org/jlinalg/shrinkage/MashSharing'+s+'.class' for s in ['', '$Transform','$Boundary','$Status','$Limits','$Significance','$Cell','$Pairwise']}
NEW.update({'org/jlinalg/shrinkage/MashSignScores'+s+'.class' for s in ['', '$Score','$Result']})


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        original=ROOT/'docs/literature/mashr-native-workflow/VALIDATION.json';prior=json.loads(original.read_text(encoding='utf-8'));assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items())
        names=[*NAMES,'org.jlinalg.shrinkage.MashFitTest','org.jlinalg.cli.MashWorkflowAuditTest','org.jlinalg.cli.MashNativeExecutionTest','org.jlinalg.cli.MashToolsCliTest','org.jlinalg.shrinkage.MashSharingTest','org.jlinalg.shrinkage.MashSharingOriginalTest']
        tests=[ROOT/'src/test/java'/Path(*name.split('.')).with_suffix('.java') for name in names]
        sources=[*tests,Path(__file__),ROOT/'src/main/java/org/jlinalg/shrinkage/MashSharing.java',ROOT/'src/main/java/org/jlinalg/shrinkage/MashSignScores.java',ROOT/'src/test/resources/org/jlinalg/shrinkage/mashr-sharing/original-controls.zip']
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources};artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        old_jar=ROOT/prior['artifact']['path'];assert pin(old_jar)=={k:prior['artifact'][k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(jar) as current,zipfile.ZipFile(old_jar) as old:
            old_names={n for n in old.namelist() if n.endswith('.class') and (n.startswith('org/jlinalg/shrinkage/') or n.startswith('org/jlinalg/cli/Mash') or n in ['org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SimpleJson.class'])}
            actual={n for n in current.namelist() if n.endswith('.class') and (n.startswith('org/jlinalg/shrinkage/') or n.startswith('org/jlinalg/cli/Mash') or n in ['org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SimpleJson.class'])}
            assert actual==old_names|NEW,(actual-(old_names|NEW),(old_names|NEW)-actual)
            assert all(current.read(n)==old.read(n) for n in old_names),'A protected packaged native class changed'
            owned={n:dict(bytes=len(current.read(n)),sha256=hashlib.sha256(current.read(n)).hexdigest()) for n in sorted(actual)}
            assert all(current.read(n)[6:8]==b'\x00=' for n in actual)
        runner=out/'RunMashSharingChecks.java'
        runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMashSharingChecks {
 private RunMashSharingChecks() { }
 public static void main(String[] args)throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM");
  for(String name:new String[]{"org.jlinalg.shrinkage.MashSharing","org.jlinalg.shrinkage.MashSignScores","org.jlinalg.shrinkage.MashGaussian","org.jlinalg.cli.MashToolsCli","jdistlib.accelerator.CpuComputeBackend"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong JAR: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=COUNT||summary.getTestsSucceededCount()!=COUNT||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete sharing/source/native validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in names)).replace('COUNT',str(COUNT)),encoding='utf-8',newline='\n')
        def command(argv,folder,label,timeout=600):
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(list(map(str,argv)),cwd=ROOT,env=dict(os.environ,JLINALG_MASHR_GAUSSIAN_CONTROLS=''),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout,check=False)
            record=dict(argv=list(map(str,argv)),exit_code=result.returncode);write(folder/(label+'.json'),record);return record
        classes=out/'classes';classes.mkdir();compiled=command([JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,*tests],out,'compile',120);assert compiled['exit_code']==0,'Inspect compile.stderr'
        paths=[classes,jar,ROOT/'src/test/resources',*libraries]
        platforms=[('windows-java21',[JAVA21/'java.exe'],'21',str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],'25',str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],'21',linux,':')]
        def run(spec):
            name,java,version,path,separator=spec;folder=out/name;folder.mkdir();result=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'RunMashSharingChecks',path(jar),version],folder,'junit')
            assert result['exit_code']==0,name+': inspect full junit logs';log=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for n,label in [(COUNT,'tests found'),(COUNT,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(n)+' '+label+r'\s*\]',log),(name,label)
            print(name+': '+str(COUNT)+' actual-JAR tests passed',flush=True);return dict(name=name,process=result)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
        assert all(pin(ROOT/n)==v for n,v in bound.items()) and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-three-JVM-native-mash-sharing-development-matrix',tests_per_JVM=COUNT,new_tests=46,original_R_cases=32,heap_mib=128,runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=bound,protected_source_pins=prior['source_pins'],owned_classes=owned,protected_packaged_classes_unchanged=len(old_names),canonical_executable_unchanged=canonical,catalog_promoted=False,whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_SHARING_MATRIX',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_SHARING_MATRIX: '+str(failure),file=sys.stderr);sys.exit(2)
