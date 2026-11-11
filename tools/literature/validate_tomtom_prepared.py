# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact-source typed Tomtom preparation/text checks and fresh owned original synthetic runs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    jar,out=args.artifact.resolve(),args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');prior=json.loads((ROOT/'docs/literature/meme-native-input-development/VALIDATION.json').read_bytes());parent=ROOT/prior['artifact']['path']
    assert pin(parent)=={k:prior['artifact'][k] for k in ['bytes','sha256']} and canonical==prior['canonical_executable_unchanged']
    main_sources=[ROOT/'src/main/java/org/jlinalg/sequence'/n for n in ['MemeXml.java','MemeMotifInputs.java','TomtomResults.java']]+[ROOT/'src/main/java/org/jlinalg/cli'/n for n in ['TomtomRecipe.java','TomtomInputs.java']]
    tests=[ROOT/'src/test/java/org/jlinalg/cli'/n for n in ['MemeXmlTest.java','MemeMotifInputsTest.java','TomtomResultsTest.java','TomtomTextResultsTest.java','TomtomPreparedInputsTest.java','TomtomPreparedRunControls.java']]
    additions={'org/jlinalg/sequence/TomtomResults$TextAudit.class'}
    additions|={'org/jlinalg/cli/TomtomRecipe'+s+'.class' for s in ['', '$Mode','$Background','$Threshold','$Request']}
    additions|={'org/jlinalg/cli/TomtomInputs'+s+'.class' for s in ['', '$Original','$MotifFile','$Context','$1']}
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist()))
        assert set(current.namelist())-set(old.namelist())==additions and set(old.namelist())<=set(current.namelist())
        changed={name for name in old.namelist() if old.read(name)!=current.read(name)}
        assert changed and all(name.startswith('org/jlinalg/sequence/TomtomResults') for name in changed)
        unchanged=len(old.namelist())-len(changed)
    paths=[*main_sources,*tests,Path(__file__),ROOT/'tools/literature/meme-development.init.gradle']
    for name in ['meme-suite','tomtom-contracts','tomtom-inputs']:paths.extend(p for p in (ROOT/'src/test/resources/org/jlinalg/sequence'/name).iterdir() if p.is_file())
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in paths};write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        dest=out/'source-snapshot'/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/name).read_bytes())
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    def command(name,argv,timeout=900,require_zero=True):
        argv=list(map(str,argv))
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        result=dict(command=argv,exit_code=r.returncode);write(out/(name+'.json'),result)
        if require_zero:assert r.returncode==0,(name,r.returncode)
        return result
    compiled=out/'source-compiled';compiled.mkdir();command('compile-exact-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,*main_sources])
    expected_names=set(prior['added_classes'])|set() # The complete36-class set is bound directly to the five freshly compiled sources.
    classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert len(classes)==36 and additions<=set(classes)
    with zipfile.ZipFile(jar) as z:
        expected_names={n for n in z.namelist() if any(n.startswith(p) and n.endswith('.class') for p in ['org/jlinalg/sequence/MemeXml','org/jlinalg/sequence/MemeMotifInputs','org/jlinalg/sequence/TomtomResults','org/jlinalg/cli/TomtomRecipe','org/jlinalg/cli/TomtomInputs'])}
        assert set(classes)==expected_names
        for name,p in classes.items():assert p.read_bytes()==z.read(name),'Exact-source bytecode differs: '+name
    test_classes=out/'test-classes';test_classes.mkdir();runner=out/'TomtomPreparedChecks.java'
    runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class TomtomPreparedChecks {public static void main(String[] args)throws Exception {
 for(String name:new String[]{"org.jlinalg.sequence.MemeXml","org.jlinalg.sequence.MemeMotifInputs","org.jlinalg.sequence.TomtomResults","org.jlinalg.cli.TomtomRecipe","org.jlinalg.cli.TomtomInputs"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
 var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.MemeXmlTest"),selectClass("org.jlinalg.cli.MemeMotifInputsTest"),selectClass("org.jlinalg.cli.TomtomResultsTest"),selectClass("org.jlinalg.cli.TomtomTextResultsTest"),selectClass("org.jlinalg.cli.TomtomPreparedInputsTest")).build(),listener);
 var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));if(summary.getTestsFoundCount()!=180||summary.getTestsSucceededCount()!=180||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete typed preparation/text/output checks");
 System.out.println("COMPLETE_TOMTOM_PREPARED_CONTRACTS 180 NO_SKIPS NO_RUNTIME_ADMISSION");}}
''',encoding='utf-8',newline='\n')
    command('compile-packaged-tests',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',test_classes,runner,*tests])
    cp=[test_classes,jar,ROOT/'src/test/resources',*libraries]
    platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def run(platform):
        name,java,convert,sep=platform;receipt=command(name,[*java,'-Xmx512m','-XX:ActiveProcessorCount=2','-cp',sep.join(map(convert,cp)),'TomtomPreparedChecks',convert(jar)])
        assert 'COMPLETE_TOMTOM_PREPARED_CONTRACTS 180 NO_SKIPS NO_RUNTIME_ADMISSION' in (out/(name+'.stdout')).read_text();print(name+':180 actual-JAR focused checks passed without skips',flush=True);return dict(name=name,tests=180,passed=180,skipped=0,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
    build=BASE/'linux-build-3/COMPLETED.json';home=json.loads(build.read_bytes())['prefix'];native='/var/tmp/omicsmatic-tomtom-prepared-'+uuid.uuid4().hex
    write(out/'NATIVE-OWNER.json',dict(linux_root=native,home=home,build_proof=pin(build),status='launching-exact-owned-synthetic-component-controls-no-runtime-ticket'))
    native_run=command('linux-original-prepared-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-cp',':'.join(map(linux,cp)),'org.jlinalg.cli.TomtomPreparedRunControls',linux(jar),home,linux(build),native],timeout=1800,require_zero=False)
    exporter='''from pathlib import Path
import json,shutil,sys
sys.path.insert(0,sys.argv[1])
from collect_meme_original_controls import census
source,dest=Path(sys.argv[2]),Path(sys.argv[3]);assert source.is_dir() and not dest.exists()
before=census(source);shutil.copytree(source,dest);after=census(dest);assert set(before)==set(after)
for name,value in before.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in after[name].items() if k!='mode'}
assert census(source)==before
(dest.parent/'NATIVE-INVENTORY.json').write_text(json.dumps(before,indent=2)+'\\n')
print('Retained complete native synthetic component capture:',len(before),'nodes')
'''
    command('capture-native-original-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',exporter,linux(ROOT/'tools/literature'),native,linux(out/'captured')])
    assert native_run['exit_code']==0 and 'COMPLETE_PREPARED_TOMTOM_RUNS 49 FULL20 TEXT29 NO_RUNTIME_OR_USER_ANALYSIS_ADMISSION' in (out/'linux-original-prepared-controls.stdout').read_text()
    native_proof=json.loads((out/'captured/COMPLETED.json').read_bytes());assert native_proof['fits']==49 and len(native_proof['protocols'])==49
    assert all(not p['runtime_admitted'] and not p['user_analysis_admitted'] and not p['owned_live_after_cleanup'] for p in native_proof['protocols'])
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exact-source-three-JVM-typed-Tomtom-preparation-text-and-original-component-runs',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,runs=runs,exact_source_bytecode_classes=36,added_classes=sorted(additions),changed_classes=sorted(changed),unchanged_prior_archive_entries=unchanged,original_parent=prior['artifact'],canonical_executable_unchanged=canonical,
        native_original_run=native_run,native_root=native,native_fits=49,full_report_fits=20,text_fits=29,native_rows=sum(p['complete_reported_rows'] for p in native_proof['protocols']),native_pairs=sum(p['complete_declared_pairs'] for p in native_proof['protocols']),
        scope='Complete typed requested scientific settings and early capacities; unchanged combined raw input capture and source/model/path ledgers; original text table audit without XML substitution; fresh strict-owned original synthetic full/text runs. These component runs bind whole original payload and inputs, not a complete ELF/helper/loader/Perl host closure or current-host runtime health ticket. Full requested report semantics, native runtime admission/installer, public CLI/example, portable final bundle and catalog closure remain open.',
        runtime_admitted=False,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete exact-source/owned-control checks')
    main()
