# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate exact native Signac normalization classes in a private JAR on three JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    save(out/'INCOMPLETE.json',dict(status='native-normalization-validation-in-progress'))
    try:
        previous=json.loads((ROOT/'docs/literature/stringtie-main-workflow/VALIDATION.json').read_bytes())
        assert all(pin(ROOT/n)==v for n,v in previous['source_pins'].items()),'Frozen parent source changed'
        parent=ROOT/previous['artifact']['path'];assert pin(parent)=={k:previous['artifact'][k] for k in ['bytes','sha256']}
        assert all(pin(ROOT/n)==v for n,v in previous['dependency_pins'].items())
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-normalization'
        capture=json.loads((fixture/'CAPTURE.json').read_bytes());assert pin(fixture/'original-controls.zip')==capture['archive']
        assert all(pin(ROOT/n)==v for n,v in capture['source_pins'].items())
        names=['src/main/java/org/jlinalg/sequence/SignacNormalization.java','src/test/java/org/jlinalg/sequence/SignacNormalizationTest.java',
               'tools/literature/validate_signac_normalization.py','tools/literature/freeze_signac_normalization_fixtures.py',
               'tools/literature/install_signac_reference.R','tools/literature/setup_signac_reference.py',
               'tools/literature/audit_signac_paper_uses.py','tools/literature/intake_signac_reference.py','tools/literature/validate_mashr_native.py']
        names += [p.relative_to(ROOT).as_posix() for p in sorted(fixture.iterdir()) if p.is_file()]
        sources={n:pin(ROOT/n) for n in names};sources.update(capture['source_pins']);save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,argv,timeout=180):
            argv=list(map(str,argv))
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                process=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout,check=False)
            save(out/(name+'.process.json'),dict(command=argv,exit_code=process.returncode));assert process.returncode==0,(name,process.returncode)
        compiled=out/'source-compiled';compiled.mkdir()
        command('compile-exact-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',parent,'-d',compiled,ROOT/names[0]])
        classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert classes and all(n.startswith('org/jlinalg/sequence/SignacNormalization') and n.endswith('.class') for n in classes)
        jar=out/'omicsmatic-0.3.7-signac-normalization-development.jar'
        with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar,'x') as new:
            assert not set(classes).intersection(old.namelist())
            for info in old.infolist():new.writestr(info,old.read(info.filename))
            for name,path in sorted(classes.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;new.writestr(info,path.read_bytes())
        with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
            assert set(new.namelist())-set(old.namelist())==set(classes) and all(old.read(n)==new.read(n) for n in old.namelist())
            assert all(new.read(n)==p.read_bytes() for n,p in classes.items());unchanged=len(old.namelist())
        runner=out/'SignacNormalizationChecks.java'
        runner.write_text('''import java.io.File;import java.io.PrintWriter;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class SignacNormalizationChecks {public static void main(String[] args)throws Exception {
if(!org.jlinalg.sequence.SignacNormalization.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual native Signac JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SignacNormalizationTest")).build(),listener);
var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=1605||s.getTestsSucceededCount()!=1605||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native Signac checks");
System.out.println("COMPLETE_NATIVE_SIGNAC_NORMALIZATION_CHECKS 1605 NO_SKIPS");}}
''',encoding='utf-8',newline='\n')
        tests=out/'test-classes';tests.mkdir()
        command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',tests,runner,ROOT/names[1]])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',ROOT/names[0]])
        classpath=[tests,jar,ROOT/'src/test/resources',*libraries]
        platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),
                   ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def test(spec):
            name,java,convert,separator=spec
            command(name,[*java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,classpath)),'SignacNormalizationChecks',convert(jar)])
            assert 'COMPLETE_NATIVE_SIGNAC_NORMALIZATION_CHECKS 1605 NO_SKIPS' in (out/(name+'.stdout')).read_text()
            return dict(name=name,tests=1605,passed=1605,skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(test,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items())
        assert all(pin(ROOT/n)==v for n,v in previous['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical_executable_unchanged']
        result=dict(schema_version=1,status='passed-native-Signac-normalization-and-feature-selection-foundation',source_pins=sources,
                    parent_validation=pin(ROOT/'docs/literature/stringtie-main-workflow/VALIDATION.json'),parent_artifact=previous['artifact'],
                    dependency_pins=previous['dependency_pins'],artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),
                    exact_new_source_classes=len(classes),unchanged_parent_entries=unchanged,new_classes={n:pin(p) for n,p in sorted(classes.items())},
                    platform_validation=runs,full_original_package_version='1.17.1',original_normalization_cases=1152,original_selection_cases=396,original_metadata_cases=36,
                    historical_extracted_source_editions=['1.6.0','1.7.0','1.9.0','1.14.0','1.17.1'],historical_packages_installed=False,
                    historical_assay_wrapper_skips=['1.6.0','1.7.0','1.9.0'],strict_Java17_source=True,javadoc_passed=True,
                    canonical_executable_unchanged=previous['canonical_executable_unchanged'],public_workflow_available=False,
                    catalog_implementation_credit=False,biological_accuracy_certified=False,
                    warnings=['Historical wrappers1.6/1.7/1.9 require a compatible older SeuratObject runtime; retained SKIP3 diagnostics are not passed checks.',
                              'Only native count normalization and feature metadata/selection are implemented here; full Signac remains missing.',
                              'Fractional counts and nonpositive/nonfinite IDF or scale are outside this native API domain; infinite output is retained with an explicit skip-downstream warning.'])
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],platform_validation=runs,artifact=result['artifact'])))
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='FAILED_NO_NATIVE_OR_CATALOG_CREDIT',error=str(error)));raise

if __name__=='__main__':main()
