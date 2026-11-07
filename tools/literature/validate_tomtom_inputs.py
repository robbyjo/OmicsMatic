# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind raw motif inputs and revised source preprocessing to an exact-source three-JVM artifact."""
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
    old_proof=json.loads((ROOT/'docs/literature/meme-native-output-development/VALIDATION.json').read_bytes())
    parent=ROOT/old_proof['artifact']['path'];assert pin(parent)=={k:old_proof['artifact'][k] for k in ['bytes','sha256']}
    assert canonical==old_proof['canonical_executable_unchanged']
    prefix='org/jlinalg/sequence/'
    expected={prefix+'MemeXml'+s+'.class' for s in ['', '$Limits','$Node','$Document','$Builder']}
    expected|={prefix+'TomtomResults'+s+'.class' for s in ['', '$UnsupportedInput','$Motif','$Database','$Expected','$Backgrounds','$Limits','$Number','$Match','$Pair','$Audit','$Resolved','$Key','$XmlMatch']}
    additions={prefix+'MemeMotifInputs'+s+'.class' for s in ['', '$UnsupportedInput','$Limits','$Entry','$Document','$Builder']}
    # The deliberately revised source shifts LineNumberTable metadata in nested
    # classes too. Every class in this one source family is freshly compiled and
    # compared byte-for-byte below; all other prior archive entries stay exact.
    allowed_changes={name for name in expected if name.startswith(prefix+'TomtomResults')}
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert len(current.namelist())==len(set(current.namelist())) and current.testzip() is None
        assert set(current.namelist())-set(old.namelist())==additions
        assert set(old.namelist())<=set(current.namelist())
        changed={name for name in old.namelist() if old.read(name)!=current.read(name)}
        assert changed<=allowed_changes and {prefix+'TomtomResults.class',prefix+'TomtomResults$Motif.class'}<=changed,sorted(changed)
        unchanged_entries=len(old.namelist())-len(changed)
    tests=[ROOT/'src/test/java/org/jlinalg/cli'/name for name in ['MemeXmlTest.java','TomtomResultsTest.java','MemeMotifInputsTest.java']]
    main_sources=[ROOT/'src/main/java/org/jlinalg/sequence'/name for name in ['MemeXml.java','TomtomResults.java','MemeMotifInputs.java']]
    paths=[*main_sources,*tests,Path(__file__),ROOT/'tools/literature/meme-development.init.gradle',ROOT/'tools/literature/freeze_tomtom_input_fixtures.py',ROOT/'tools/literature/collect_tomtom_input_controls.py']
    for name in ['meme-suite','tomtom-contracts','tomtom-inputs']:paths.extend(p for p in (ROOT/'src/test/resources/org/jlinalg/sequence'/name).iterdir() if p.is_file())
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in paths};write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    def command(name,argv):
        argv=list(map(str,argv))
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
            result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        receipt=dict(command=argv,exit_code=result.returncode);write(out/(name+'.json'),receipt);assert result.returncode==0,(name,result.returncode);return receipt
    compiled=out/'source-compiled';compiled.mkdir()
    command('compile-exact-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,*main_sources])
    classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert set(classes)==expected|additions
    with zipfile.ZipFile(jar) as z:
        for name,path in classes.items():assert z.read(name)==path.read_bytes(),'Exact-source bytecode differs: '+name
    test_classes=out/'test-classes';test_classes.mkdir();runner=out/'TomtomInputChecks.java'
    runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class TomtomInputChecks {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.sequence.MemeXml","org.jlinalg.sequence.TomtomResults","org.jlinalg.sequence.MemeMotifInputs","org.jlinalg.sequence.MemeDnaMotifs"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.MemeXmlTest"),selectClass("org.jlinalg.cli.TomtomResultsTest"),selectClass("org.jlinalg.cli.MemeMotifInputsTest")).build(),listener);
  var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=106||summary.getTestsSucceededCount()!=106||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native input/output contracts");
  System.out.println("COMPLETE_TOMTOM_INPUT_CONTRACTS 106 NO_SKIPS NO_RUNTIME_ADMISSION");
 }
}
''',encoding='utf-8',newline='\n')
    command('compile-packaged-tests',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',test_classes,runner,*tests])
    cp=[test_classes,jar,ROOT/'src/test/resources',*libraries]
    platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def run(platform):
        name,java,convert,separator=platform;receipt=command(name,[*java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'TomtomInputChecks',convert(jar)])
        assert 'COMPLETE_TOMTOM_INPUT_CONTRACTS 106 NO_SKIPS NO_RUNTIME_ADMISSION' in (out/(name+'.stdout')).read_text()
        print(name+':106 complete actual-JAR input/output checks passed without skips',flush=True);return dict(name=name,tests=106,passed=106,skipped=0,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exact-source-three-JVM-Tomtom-native-input-and-output-contracts',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,runs=runs,
        exact_source_bytecode_classes=len(classes),added_classes=sorted(additions),changed_classes=sorted(changed),unchanged_prior_archive_entries=unchanged_entries,original_parent=old_proof['artifact'],canonical_executable_unchanged=canonical,
        original_input_cases=24,complete_new_original_input_output_contracts=20,new_original_reported_rows=80,regression_original_output_contracts=30,regression_original_reported_rows=6351,
        original_invalid_input_cases=2,original_HTML_successes_reader_unimplemented=2,
        scope='Native complete raw DNA minimal/learned text and MEME/STREME 5.5.9 XML inputs, unchanged raw identity, literal metadata/defaults, source row tolerance/normalization, zero/fractional sites, original STREME XML mappings, and full original XML/TSV comparison contracts. All raw declared records remain; source duplicate/ALLR/query-width exclusions, HTML embedded data, other XML editions, runtime/process/report-family gates, public execution/example and catalog credit remain open.',
        runtime_admitted=False,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete exact-source/actual-JAR checks')
    main()
