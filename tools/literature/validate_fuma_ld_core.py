#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Packaged LD/frequency/window controls on three JVMs, with prior classes exact."""
import argparse,concurrent.futures,hashlib,json,os,re,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from validate_mashr_native import ROOT,JAVA21,LIBS,linux,pin
BASE=ROOT/'build/literature-review/fuma-runtime/ld-reference-development-1'
COUNT=56
TESTS=['org.jlinalg.genetics.HardcallLdTest','org.jlinalg.genetics.HardcallLdOriginalTest','org.jlinalg.genetics.FumaLdWindowTest','org.jlinalg.genetics.FumaLocusDefinitionTest']
NEW={'org/jlinalg/genetics/HardcallLd'+s+'.class' for s in ['', '$Limits','$Frame','$MinorAllele','$Frequencies','$Status','$Pair','$Calls']}|{'org/jlinalg/genetics/FumaLocusDefinition$LdWindow.class','org/jlinalg/genetics/FumaLocusDefinition$PairCoverage.class'}


def write(p,j):p.write_text(json.dumps(j,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        prior=json.loads((ROOT/'docs/literature/mashr-proxy-command/VALIDATION.json').read_text(encoding='utf-8'));old_jar=ROOT/prior['artifact']['path'];assert pin(old_jar)=={k:prior['artifact'][k] for k in ['bytes','sha256']}
        tests=[ROOT/'src/test/java'/Path(*n.split('.')).with_suffix('.java') for n in TESTS]
        files=[*tests,ROOT/'src/main/java/org/jlinalg/genetics/HardcallLd.java',ROOT/'src/main/java/org/jlinalg/genetics/FumaLocusDefinition.java',ROOT/'src/main/java/org/jlinalg/genetics/PlinkBedDataset.java',Path(__file__),ROOT/'tools/literature/collect_hardcall_ld_controls.py',ROOT/'tools/literature/fuma-development.init.gradle',ROOT/'src/test/resources/org/jlinalg/genetics/hardcall-ld/original-controls.zip',ROOT/'src/test/resources/org/jlinalg/genetics/hardcall-ld/.gitattributes']
        sources={p.relative_to(ROOT).as_posix():pin(p) for p in files};artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==prior['canonical_executable_unchanged']
        with zipfile.ZipFile(old_jar) as old,zipfile.ZipFile(jar) as current:
            old_names={n for n in old.namelist() if n.endswith('.class')};actual={n for n in current.namelist() if n.endswith('.class')};assert actual==old_names|NEW,(actual-(old_names|NEW),(old_names|NEW)-actual)
            changed={n for n in old_names if old.read(n)!=current.read(n)};assert changed and all(n.startswith('org/jlinalg/genetics/FumaLocusDefinition') for n in changed),changed
            classes={n:dict(bytes=len(current.read(n)),sha256=hashlib.sha256(current.read(n)).hexdigest()) for n in sorted(changed|NEW)}
            assert all(current.read(n)[6:8]==b'\x00=' for n in classes)
        runner=out/'RunFumaLdChecks.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunFumaLdChecks {
 private RunFumaLdChecks() { }
 public static void main(String[] args)throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM");
  for(String name:new String[]{"org.jlinalg.genetics.HardcallLd","org.jlinalg.genetics.FumaLocusDefinition","org.jlinalg.genetics.PlinkBedDataset"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=COUNT||s.getTestsSucceededCount()!=COUNT||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete LD/window/original validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in TESTS)).replace('COUNT',str(COUNT)),encoding='utf-8',newline='\n')
        def command(argv,folder,label,timeout=180):
            with (folder/(label+'.stdout')).open('xb') as a,(folder/(label+'.stderr')).open('xb') as b:p=subprocess.run(list(map(str,argv)),cwd=ROOT,stdout=a,stderr=b,stdin=subprocess.DEVNULL,timeout=timeout,check=False)
            j=dict(argv=list(map(str,argv)),exit_code=p.returncode);write(folder/(label+'.json'),j);return j
        compiled=out/'classes';compiled.mkdir();result=command([JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',compiled,runner,*tests],out,'compile',120);assert result['exit_code']==0,'Inspect compile.stderr'
        paths=[compiled,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],'21',str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],'25',str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],'21',linux,':')]
        def run(spec):
            name,java,version,path,sep=spec;folder=out/name;folder.mkdir();p=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',sep.join(map(path,paths)),'RunFumaLdChecks',path(jar),version],folder,'junit');assert p['exit_code']==0,name+': inspect complete junit logs';log=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for n,label in [(COUNT,'tests found'),(COUNT,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(n)+' '+label+r'\s*\]',log),(name,label)
            print(name+': '+str(COUNT)+' packaged LD/source/window tests passed',flush=True);return dict(name=name,process=p)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-three-JVM-packaged-hardcall-LD-and-window-core',tests_per_JVM=COUNT,new_tests=32,prior_locus_tests=24,original_cases=10,original_matrix_values_per_JVM=7568,original_frequency_rows_per_JVM=228,heap_mib=128,runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,prior_artifact=prior['artifact'],unchanged_prior_packaged_classes=len(old_names)-len(changed),changed_prior_packaged_classes=sorted(changed),new_packaged_classes=sorted(NEW),owned_classes=classes,canonical_executable_unchanged=canonical,reference_preparation_cli_available=False,catalog_promoted=False,whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_LD_CORE_MATRIX',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_LD_CORE_MATRIX: '+str(failure)+'\nRequired action: retain the failed attempt, inspect the named class/compile/test mismatch and rerun to new paths.',file=sys.stderr);sys.exit(2)
