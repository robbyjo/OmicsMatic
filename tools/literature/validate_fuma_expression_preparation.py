"""Check the actual expression-preparation JAR on three JVMs and preserve prior class bytes."""
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
from validate_mashr_native import ROOT,JAVA21,LIBS,linux,pin
from validate_fuma_reference_workflow import TESTS as PRIOR_TESTS,owned,write

BASE=ROOT/'build/literature-review/fuma-runtime'
TESTS=[*PRIOR_TESTS,'org.jlinalg.enrichment.FumaExpressionPreparationTest','org.jlinalg.enrichment.FumaExpressionPreparationRTest','org.jlinalg.cli.FumaExpressionPrepareCliTest']
COUNT=148
NEW=['org/jlinalg/enrichment/FumaExpressionPreparation','org/jlinalg/cli/FumaExpressionPrepareCli']
CHANGED=['org/jlinalg/cli/FumaToolsCli','org/jlinalg/cli/FumaWorkflowCli']


def main():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();jar,out=a.artifact.resolve(),a.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        prior_path=ROOT/'docs/literature/fuma-reference-workflow/VALIDATION.json';prior=json.loads(prior_path.read_text(encoding='utf-8'));old_jar=ROOT/prior['artifact']['path']
        assert pin(old_jar)=={k:prior['artifact'][k] for k in ['bytes','sha256']}
        assert pin(prior_path.parent/prior['archive']['path'])=={k:prior['archive'][k] for k in ['bytes','sha256']}
        replaced={'src/main/java/'+n+'.java' for n in CHANGED}
        assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items() if n not in replaced)
        artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==prior['canonical_executable_unchanged']
        tests=[ROOT/'src/test/java'/Path(*n.split('.')).with_suffix('.java') for n in TESTS]
        files=[*tests,*[ROOT/'src/main/java'/(n+'.java') for n in NEW+CHANGED],Path(__file__),ROOT/'tools/literature/validate_fuma_reference_workflow.py',ROOT/'tools/literature/validate_mashr_native.py',ROOT/'tools/literature/collect_fuma_expression_preparation_controls.R',ROOT/'tools/literature/freeze_fuma_expression_controls.py',ROOT/'tools/literature/fuma-development.init.gradle',ROOT/'src/test/resources/org/jlinalg/enrichment/fuma-expression-preparation/independent-R-controls.zip',ROOT/'src/test/resources/org/jlinalg/enrichment/fuma-expression-preparation/.gitattributes',ROOT/'docs/literature/FUMA_EXPRESSION_PREPARATION.md',*sorted((ROOT/'examples/fuma-expression-preparation').glob('*'))]
        sources={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        with zipfile.ZipFile(old_jar) as old,zipfile.ZipFile(jar) as current:
            before={n for n in old.namelist() if n.endswith('.class')};after={n for n in current.namelist() if n.endswith('.class')};assert before<=after
            added=after-before;changed={n for n in before if old.read(n)!=current.read(n)}
            assert added and all(owned(n,NEW) for n in added),added
            assert changed and all(owned(n,CHANGED) for n in changed),changed
            classes={n:dict(bytes=len(current.read(n)),sha256=hashlib.sha256(current.read(n)).hexdigest()) for n in sorted(added|changed)};assert all(current.read(n)[6:8]==b'\x00=' for n in classes)
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        runner=out/'RunFumaExpressionChecks.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunFumaExpressionChecks {
 private RunFumaExpressionChecks() { }
 public static void main(String[] args)throws Exception {
  System.out.println("java.version="+System.getProperty("java.version"));System.out.println("os.name="+System.getProperty("os.name"));
  if(!System.getProperty("java.specification.version").equals(args[1]))throw new AssertionError("Wrong JVM");
  for(String name:new String[]{"org.jlinalg.enrichment.FumaExpressionPreparation","org.jlinalg.cli.FumaExpressionPrepareCli","org.jlinalg.cli.FumaToolsCli","org.jlinalg.cli.FumaWorkflowCli"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong artifact: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=COUNT||s.getTestsSucceededCount()!=COUNT||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete expression/core/workflow validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in TESTS)).replace('COUNT',str(COUNT)),encoding='utf-8',newline='\n')
        def command(argv,folder,label,timeout=180):
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:p=subprocess.run(list(map(str,argv)),cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=timeout,check=False)
            record=dict(argv=list(map(str,argv)),exit_code=p.returncode);write(folder/(label+'.json'),record);return record
        compiled=out/'classes';compiled.mkdir();result=command([JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',compiled,runner,*tests],out,'compile',120);assert result['exit_code']==0,'Inspect compile.stderr'
        paths=[compiled,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],'21',str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],'25',str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'],'21',linux,':')]
        def run(spec):
            name,java,version,path,sep=spec;folder=out/name;folder.mkdir();result=command([*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',sep.join(map(path,paths)),'RunFumaExpressionChecks',path(jar),version],folder,'junit');assert result['exit_code']==0,name+': inspect complete logs';log=(folder/'junit.stdout').read_text(encoding='utf-8',errors='replace')
            for n,label in [(COUNT,'tests found'),(COUNT,'tests successful'),(0,'tests skipped'),(0,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(n)+' '+label+r'\s*\]',log),(name,label)
            print(name+': '+str(COUNT)+' packaged expression/reference/core/CLI tests passed',flush=True);return dict(name=name,process=result)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-three-JVM-packaged-FUMA-expression-preparation',tests_per_JVM=COUNT,new_tests_per_JVM=44,prior_tests_per_JVM=104,heap_mib=128,runs=runs,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,unchanged_prior_packaged_classes=len(before)-len(changed),changed_prior_packaged_classes=sorted(changed),new_packaged_classes=sorted(added),owned_classes=classes,prior_artifact=prior['artifact'],canonical_executable_unchanged=canonical,independent_R_cases_per_JVM=16,independent_R_gene_rows_per_JVM=192,original_FUMA_preprocessing_runtime=False,catalog_promoted=False,whole_goal_complete=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_EXPRESSION_PREPARATION_MATRIX',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_EXPRESSION_PREPARATION_MATRIX: '+str(failure)+'\nRequired action: preserve the failed attempt, inspect complete source/class/compiler/test diagnostics and rerun to new paths.',file=sys.stderr);sys.exit(2)
