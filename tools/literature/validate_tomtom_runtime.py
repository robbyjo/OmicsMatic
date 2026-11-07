# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact-source packaged native Tomtom source/host/Perl/fresh-health admission controls."""
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
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');prior=json.loads((ROOT/'docs/literature/meme-prepared-comparison-development/VALIDATION.json').read_bytes());parent=ROOT/prior['artifact']['path']
    assert pin(parent)=={k:prior['artifact'][k] for k in ['bytes','sha256']} and canonical==prior['canonical_executable_unchanged']
    source_names=['MemeXml','MemeMotifInputs','TomtomResults'];cli_names=['TomtomRecipe','TomtomInputs','MemeRuntimeFiles','MemeBuildEvidence','MemePerl','MemeNativeHost','TomtomRuntime']
    main_sources=[ROOT/'src/main/java/org/jlinalg/sequence'/(n+'.java') for n in source_names]+[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in cli_names]
    test_names=['MemeXmlTest','MemeMotifInputsTest','TomtomResultsTest','TomtomTextResultsTest','TomtomPreparedInputsTest','TomtomPreparedRunControls','MemeBuildEvidenceTest','MemePerlTest','TomtomRuntimeTest','TomtomRuntimeControls']
    tests=[ROOT/'src/test/java/org/jlinalg/cli'/(n+'.java') for n in test_names]
    expected_new={}
    for name,suffixes in {'MemeRuntimeFiles':['','$Limits','$1'],'MemeBuildEvidence':['','$Context'],'MemePerl':['','$Receipt'],'MemeNativeHost':[''],'TomtomRuntime':['','$Unavailable','$Limits','$Admitted','$Context']}.items():
        for suffix in suffixes:expected_new['org/jlinalg/cli/'+name+suffix+'.class']=True
    resources=[ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-lock.json',ROOT/'src/main/resources/org/jlinalg/omics/.gitattributes',ROOT/'src/main/resources/META-INF/NOTICE-meme-runtime.txt']
    resources.extend(p for p in (ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-setup').iterdir() if p.is_file())
    # Gradle's default copy policy omits .gitattributes; the new resource
    # directory itself has an empty JAR marker. Both repository metadata files
    # remain source-pinned below, without pretending they are runtime assets.
    added_resources={p.relative_to(ROOT/'src/main/resources').as_posix() for p in resources if p.name!='.gitattributes'}
    added_directories={'org/jlinalg/omics/meme-runtime-setup/'}
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist()))
        assert set(old.namelist())<=set(current.namelist()) and all(old.read(n)==current.read(n) for n in old.namelist())
        additions=set(current.namelist())-set(old.namelist());assert additions==set(expected_new)|added_resources|added_directories,sorted(additions)
        for name in added_directories:assert current.getinfo(name).is_dir() and current.read(name)==b''
        for p in resources:
            if p.name!='.gitattributes':assert current.read(p.relative_to(ROOT/'src/main/resources').as_posix())==p.read_bytes()
        unchanged=len(old.namelist())
    paths=[*main_sources,*tests,*resources,Path(__file__),ROOT/'tools/literature/meme-development.init.gradle',ROOT/'tools/literature/prepare_meme_runtime_lock.py',ROOT/'tools/literature/freeze_meme_build_fixtures.py']
    for name in ['meme-suite','tomtom-contracts','tomtom-inputs']:paths.extend(p for p in (ROOT/'src/test/resources/org/jlinalg/sequence'/name).iterdir() if p.is_file())
    paths.extend(p for p in (ROOT/'src/test/resources/org/jlinalg/cli/meme-build-evidence').iterdir() if p.is_file())
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
    classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert len(classes)==49 and set(expected_new)<=set(classes)
    with zipfile.ZipFile(jar) as z:
        names={n for n in z.namelist() if any(n.startswith(prefix) and n.endswith('.class') for prefix in ['org/jlinalg/sequence/'+n for n in source_names]+['org/jlinalg/cli/'+n for n in cli_names])};assert set(classes)==names
        for name,p in classes.items():assert p.read_bytes()==z.read(name),'Exact-source class differs: '+name
    test_classes=out/'test-classes';test_classes.mkdir();runner=out/'TomtomRuntimeChecks.java'
    runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class TomtomRuntimeChecks {public static void main(String[] args)throws Exception {
 for(String name:new String[]{"org.jlinalg.sequence.MemeXml","org.jlinalg.sequence.MemeMotifInputs","org.jlinalg.sequence.TomtomResults","org.jlinalg.cli.TomtomRecipe","org.jlinalg.cli.TomtomInputs","org.jlinalg.cli.MemeRuntimeFiles","org.jlinalg.cli.MemeBuildEvidence","org.jlinalg.cli.MemePerl","org.jlinalg.cli.MemeNativeHost","org.jlinalg.cli.TomtomRuntime"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
 var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.MemeXmlTest"),selectClass("org.jlinalg.cli.MemeMotifInputsTest"),selectClass("org.jlinalg.cli.TomtomResultsTest"),selectClass("org.jlinalg.cli.TomtomTextResultsTest"),selectClass("org.jlinalg.cli.TomtomPreparedInputsTest"),selectClass("org.jlinalg.cli.MemeBuildEvidenceTest"),selectClass("org.jlinalg.cli.MemePerlTest"),selectClass("org.jlinalg.cli.TomtomRuntimeTest")).build(),listener);
 var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));if(summary.getTestsFoundCount()!=206||summary.getTestsSucceededCount()!=206||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete runtime/evidence/component checks");System.out.println("COMPLETE_TOMTOM_RUNTIME_COMPONENT_CHECKS 206 NO_SKIPS NO_USER_ANALYSIS_ADMISSION");}}
''',encoding='utf-8',newline='\n')
    command('compile-packaged-tests',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',test_classes,runner,*tests])
    cp=[test_classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def run(platform):
        name,java,convert,sep=platform;receipt=command(name,[*java,'-Xmx512m','-XX:ActiveProcessorCount=2','-cp',sep.join(map(convert,cp)),'TomtomRuntimeChecks',convert(jar)]);assert 'COMPLETE_TOMTOM_RUNTIME_COMPONENT_CHECKS 206 NO_SKIPS NO_USER_ANALYSIS_ADMISSION' in (out/(name+'.stdout')).read_text();print(name+':206 actual-JAR checks passed without skips',flush=True);return dict(name=name,tests=206,passed=206,skipped=0,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
    build=BASE/'linux-build-3';home=json.loads((build/'COMPLETED.json').read_bytes())['prefix'];native='/var/tmp/omicsmatic-tomtom-runtime-'+uuid.uuid4().hex
    write(out/'NATIVE-OWNER.json',dict(linux_root=native,home=home,build_evidence=linux(build),status='launching-fresh-native-runtime-controls-no-user-analysis'))
    native_run=command('linux-native-runtime-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx768m','-XX:ActiveProcessorCount=2','-cp',':'.join(map(linux,cp)),'org.jlinalg.cli.TomtomRuntimeControls',linux(jar),home,linux(build),native],timeout=3600,require_zero=False)
    exporter='''from pathlib import Path
import json,shutil,sys
sys.path.insert(0,sys.argv[1])
from collect_meme_original_controls import census
source,dest=Path(sys.argv[2]),Path(sys.argv[3]);assert source.is_dir() and not dest.exists()
before=census(source);shutil.copytree(source,dest);after=census(dest);assert set(before)==set(after)
for name,value in before.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in after[name].items() if k!='mode'}
assert census(source)==before
(dest.parent/'NATIVE-INVENTORY.json').write_text(json.dumps(before,indent=2)+'\\n')
print('Retained complete native runtime/control capture:',len(before),'nodes')
'''
    command('capture-native-runtime-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',exporter,linux(ROOT/'tools/literature'),native,linux(out/'captured')])
    assert native_run['exit_code']==0 and 'COMPLETE_TOMTOM_NATIVE_RUNTIME_CONTROLS ADMISSIONS2 HEALTH4 DOWNSTREAM2 NO_USER_ANALYSIS_OR_CATALOG_CREDIT' in (out/'linux-native-runtime-controls.stdout').read_text()
    native_proof=json.loads((out/'captured/COMPLETED.json').read_bytes());assert (native_proof['native_admissions'],native_proof['fresh_health_fits'],native_proof['downstream_synthetic_fits'])==(2,4,2)
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exact-source-three-JVM-Tomtom-native-source-host-Perl-health-admission',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,runs=runs,exact_source_bytecode_classes=49,added_classes=sorted(expected_new),added_resources=sorted(added_resources),added_resource_directories=sorted(added_directories),unchanged_prior_archive_entries=unchanged,original_parent=prior['artifact'],canonical_executable_unchanged=canonical,
        native_process=native_run,native_root=native,native_admissions=2,fresh_health_fits=4,downstream_synthetic_fits=2,runtime_admitted=True,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        scope='Exact original source/build/payload and declared current-host ELF/helper/library/loader configuration plus RPATH and full Perl search/module/native XML closure only for requested full reports. Each capability has two fresh fixed-reference health fits. Genuine current-host tickets supervise downstream synthetic calls; stale evidence and mismatched capabilities fail before launch. This copied record is not a ticket. Explicit portable installer/export, complete requested report semantics, portable final bundle, public CLI/example and catalog gates remain open.'))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete exact-source/native admission checks')
    main()
