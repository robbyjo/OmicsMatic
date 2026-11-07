# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test native LSI arithmetic, sparse intake and prior normalization using the actual candidate JAR."""
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
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-checks-in-progress'))
    try:
        build=json.loads((BASE/'lsi-build-5/BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        assert all(pin(ROOT/n)==v for n,v in build['parent_source_pins'].items())
        overlay=json.loads((BASE/'lsi-request-build-3/BUILD.json').read_bytes());assert overlay['parent_artifact']==build['artifact'] and all(pin(ROOT/n)==v for n,v in overlay['source_pins'].items())
        jar=ROOT/overlay['artifact']['path'];assert pin(jar)=={k:overlay['artifact'][k] for k in ['bytes','sha256']}
        names=['src/test/java/org/jlinalg/sequence/SignacLsiTest.java','src/test/java/org/jlinalg/sequence/SignacMatrixIOTest.java','src/test/java/org/jlinalg/sequence/SignacNormalizationTest.java','src/test/java/org/jlinalg/cli/SignacLsiRequestCliTest.java','tools/literature/validate_signac_lsi_numerics.py']
        pins={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',pins)
        for n in names:
            target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
        libs=sorted(LIBS.glob('*.jar'));assert len(libs)==9
        runner=out/'SignacLsiChecks.java';runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class SignacLsiChecks{public static void main(String[] a)throws Exception{
for(Class<?> c:new Class<?>[]{org.jlinalg.sequence.SignacNormalization.class,org.jlinalg.sequence.SignacLsi.class,org.jlinalg.sequence.SignacMatrixIO.class,org.jlinalg.cli.SignacLsiToolsCli.class,org.jlinalg.cli.SignacLsiRequestCli.class})if(!c.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong actual JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SignacNormalizationTest"),selectClass("org.jlinalg.sequence.SignacLsiTest"),selectClass("org.jlinalg.sequence.SignacMatrixIOTest"),selectClass("org.jlinalg.cli.SignacLsiRequestCliTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));if(s.getTestsFoundCount()!=1650||s.getTestsSucceededCount()!=1650||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete checks");System.out.println("COMPLETE_NATIVE_SIGNAC_LSI_CHECKS 1650 NO_SKIPS");}}
''',encoding='utf-8',newline='\n')
        def command(name,args):
            args=list(map(str,args));p=subprocess.run(args,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False)
            (out/(name+'.stdout')).write_bytes(p.stdout);(out/(name+'.stderr')).write_bytes(p.stderr);save(out/(name+'.process.json'),dict(command=args,exit_code=p.returncode));assert p.returncode==0,(name,p.returncode)
        tests=out/'classes';tests.mkdir();command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libs])),'-d',tests,runner,*[ROOT/n for n in names[:4]]])
        cp=[tests,jar,ROOT/'src/test/resources',*libs]
        platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def run(spec):
            name,java,convert,sep=spec;command(name,[*java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',sep.join(map(convert,cp)),'SignacLsiChecks',convert(jar)])
            assert 'COMPLETE_NATIVE_SIGNAC_LSI_CHECKS 1650 NO_SKIPS' in (out/(name+'.stdout')).read_text()
            command(name+'-help',[*java,'-cp',convert(jar),'org.jlinalg.cli.SignacLsiToolsCli','--help'])
            command(name+'-request-help',[*java,'-cp',convert(jar),'org.jlinalg.cli.SignacLsiRequestCli','--help']);return dict(name=name,tests=1650,passed=1650,skipped=0,standalone_help=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        save(out/'COMPLETED.json',dict(status='passed-native-Signac-LSI-and-sparse-intake-checks',source_pins=pins,artifact=overlay['artifact'],dependency_pins={p.relative_to(ROOT).as_posix():pin(p) for p in libs},platform_validation=runs,upstream_runtime_validation=False))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(runs))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_LSI_VALIDATION',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable native checks')
    main()
