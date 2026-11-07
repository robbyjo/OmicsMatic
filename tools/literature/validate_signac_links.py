# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the native LinkPeaks auditor against original coefficients, backgrounds and ranges on three JVMs."""
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
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--build',default='links-build-3');args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in args.build and '\\' not in args.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-LinkPeaks-auditor-control-matrix-in-progress'))
    try:
        build=json.loads((BASE/args.build/'BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-links';capture=json.loads((fixture/'CAPTURE.json').read_bytes());assert pin(fixture/'original-controls.zip')==capture['archive'] and all(pin(ROOT/n)==v for n,v in capture['source_pins'].items());names=['src/test/java/org/jlinalg/cli/SignacPeakLinksTest.java','tools/literature/validate_signac_links.py',*[p.relative_to(ROOT).as_posix() for p in fixture.iterdir() if p.is_file()]];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        def command(name,argv):
            argv=list(map(str,argv))
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180,check=False)
            save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        runner=out/'SignacLinkChecks.java';runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class SignacLinkChecks{public static void main(String[] a)throws Exception{for(Class<?> c:new Class<?>[]{org.jlinalg.sequence.SignacPeakLinks.class,org.jlinalg.sequence.SignacLinkIO.class})if(!c.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong actual LinkPeaks JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.SignacPeakLinksTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));if(s.getTestsFoundCount()!=109||s.getTestsSucceededCount()!=109||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete LinkPeaks audit controls");System.out.println("COMPLETE_NATIVE_SIGNAC_LINK_CHECKS 109 NO_SKIPS");}}
''',encoding='utf8',newline='\n');classes=out/'test-classes';classes.mkdir();command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,ROOT/names[0]])
        cp=[classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def test(spec):
            name,java,convert,separator=spec;command(name,[*java,'-Xmx384m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'SignacLinkChecks',convert(jar)]);assert 'COMPLETE_NATIVE_SIGNAC_LINK_CHECKS 109 NO_SKIPS' in (out/(name+'.stdout')).read_text();return dict(name=name,tests=109,passed=109,skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(test,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-native-LinkPeaks-independent-auditor-controls',source_pins=sources,artifact=build['artifact'],platform_validation=runs,original_successful_cases=44,original_source_failure_cases_retained=16,full_current_package_executed_for_controls=True,historical_packages_executed=False,public_LinkPeaks_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-native-LinkPeaks-independent-auditor-controls',platform_validation=runs)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_LINKPEAKS_AUDITOR_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native LinkPeaks audit checks')
    main()
