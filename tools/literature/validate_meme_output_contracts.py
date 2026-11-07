# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify exact-source new Java classes and complete actual-JAR contracts on three JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    jar,out=args.artifact.resolve(),args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    prior=json.loads((ROOT/'docs/literature/salmon-bulk-workflow/VALIDATION.json').read_bytes());parent=ROOT/prior['artifact']['path']
    assert pin(parent)=={key:prior['artifact'][key] for key in ['bytes','sha256']} and canonical==prior['canonical_executable_unchanged']
    expected_names={'org/jlinalg/sequence/MemeXml'+suffix+'.class' for suffix in ['', '$Limits','$Node','$Document','$Builder']}
    expected_names|={'org/jlinalg/sequence/TomtomResults'+suffix+'.class' for suffix in ['', '$UnsupportedInput','$Motif','$Database','$Expected','$Backgrounds','$Limits','$Number','$Match','$Pair','$Audit','$Resolved','$Key','$XmlMatch']}
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert len(current.namelist())==len(set(current.namelist())) and current.testzip() is None
        assert all(name in current.namelist() and old.read(name)==current.read(name) for name in old.namelist())
        additions=set(current.namelist())-set(old.namelist());assert additions==expected_names,sorted(additions)
        unchanged_entries=len(old.namelist())
    tests=[ROOT/'src/test/java/org/jlinalg/cli'/name for name in ['MemeXmlTest.java','TomtomResultsTest.java']]
    main_sources=[ROOT/'src/main/java/org/jlinalg/sequence'/name for name in ['MemeXml.java','TomtomResults.java']]
    paths=[*main_sources,*tests,Path(__file__),ROOT/'tools/literature/meme-development.init.gradle',ROOT/'tools/literature/freeze_meme_format_fixtures.py',ROOT/'tools/literature/freeze_tomtom_contract_fixtures.py']
    for name in ['meme-suite','tomtom-contracts']:paths.extend(path for path in (ROOT/'src/test/resources/org/jlinalg/sequence'/name).iterdir() if path.is_file())
    sources={path.relative_to(ROOT).as_posix():pin(path) for path in paths}
    write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    def command(name,argv):
        argv=list(map(str,argv))
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
            result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        receipt=dict(command=argv,exit_code=result.returncode);write(out/(name+'.json'),receipt);assert result.returncode==0,(name,result.returncode);return receipt
    scientific=out/'source-compiled';scientific.mkdir()
    command('compile-exact-new-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',scientific,*main_sources])
    compiled={p.relative_to(scientific).as_posix():p for p in scientific.rglob('*.class')};assert set(compiled)==expected_names
    with zipfile.ZipFile(jar) as z:
        for name,path in compiled.items():assert path.read_bytes()==z.read(name),'Source/actual-JAR bytecode mismatch: '+name
    classes=out/'classes';classes.mkdir();runner=out/'MemeOutputContractChecks.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class MemeOutputContractChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.MemeXml","org.jlinalg.sequence.TomtomResults","org.jlinalg.sequence.MemeDnaMotifs"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.MemeXmlTest"),selectClass("org.jlinalg.cli.TomtomResultsTest")).build(),listener);
  var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=73||summary.getTestsSucceededCount()!=73||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native output contracts");
  System.out.println("COMPLETE_MEME_OUTPUT_CONTRACTS 73 NO_SKIPS NO_RUNTIME_ADMISSION");
 }
}
''',encoding='utf-8',newline='\n')
    command('compile-packaged-tests',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,*tests])
    cp=[classes,jar,ROOT/'src/test/resources',*libraries]
    platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def run(platform):
        name,java,convert,separator=platform;receipt=command(name,[*java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'MemeOutputContractChecks',convert(jar)])
        assert 'COMPLETE_MEME_OUTPUT_CONTRACTS 73 NO_SKIPS NO_RUNTIME_ADMISSION' in (out/(name+'.stdout')).read_text();print(name+':73 actual-JAR output contracts passed without skips',flush=True)
        return dict(name=name,tests=73,passed=73,skipped=0,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exact-source-three-JVM-MEME-native-output-contracts',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,runs=runs,
        exact_source_bytecode_classes=len(compiled),added_classes=sorted(additions),unchanged_prior_archive_entries=unchanged_entries,original_parent=prior['artifact'],canonical_executable_unchanged=canonical,
        complete_original_Tomtom_contracts=30,complete_original_reported_rows=6351,original_XML_documents=14,
        scope='Complete bounded XML plus original DNA Tomtom XML/TSV models, source pseudocount/background and float-consensus behavior, every reported probability and full declared pair ledgers. Original failures and source exclusions remain separate. Text-only output, source-exclusion ledgers, native input preprocessing/admission, runtime/process/report-family gates, public execution/example and catalog credit remain open.',
        runtime_admitted=False,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact-source/whole-contract verification')
    main()
