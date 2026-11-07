# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual native fragment classes against portable full-package original controls on three JVMs."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-fragment-source-control-validation-in-progress'))
    try:
        build=json.loads((BASE/'fragments-build-2/BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-fragments';capture=json.loads((fixture/'CAPTURE.json').read_bytes());assert pin(fixture/'original-controls.zip')==capture['archive'] and all(pin(ROOT/n)==v for n,v in capture['source_pins'].items())
        names=['src/test/java/org/jlinalg/sequence/SignacFragmentsTest.java','tools/literature/validate_signac_fragments.py',*[p.relative_to(ROOT).as_posix() for p in fixture.iterdir() if p.is_file()]];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        def command(name,argv):
            argv=list(map(str,argv))
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240,check=False)
            save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        runner=out/'SignacFragmentChecks.java';runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class SignacFragmentChecks{public static void main(String[] a)throws Exception{
for(Class<?> c:new Class<?>[]{org.jlinalg.sequence.SignacFragments.class,org.jlinalg.sequence.SignacFragmentQc.class})if(!c.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong actual fragment JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SignacFragmentsTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));if(s.getTestsFoundCount()!=728||s.getTestsSucceededCount()!=728||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native fragment checks");System.out.println("COMPLETE_NATIVE_SIGNAC_FRAGMENT_CHECKS 728 NO_SKIPS");}}
''',encoding='utf8',newline='\n')
        classes=out/'test-classes';classes.mkdir();command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,ROOT/names[0]])
        cp=[classes,jar,ROOT/'src/test/resources',*libraries]
        platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def test(spec):
            name,java,convert,separator=spec;command(name,[*java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'SignacFragmentChecks',convert(jar)])
            assert 'COMPLETE_NATIVE_SIGNAC_FRAGMENT_CHECKS 728 NO_SKIPS' in (out/(name+'.stdout')).read_text();return dict(name=name,tests=728,passed=728,skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(test,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        result=dict(status='passed-native-Signac-fragment-counting-quantification-QC-and-binarization-controls',source_pins=sources,artifact=build['artifact'],platform_validation=runs,count_original_cases=63,matrix_original_cases=64,nucleosome_original_cases=33,binarization_original_cases=36,original_rounding_scalars=20100,historical_binaries_executed=False,public_workflow_available=False,catalog_implementation_credit=False)
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],platform_validation=runs)))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_FRAGMENT_CONTROL_VALIDATION',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete fragment control checks')
    main()
