# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every original chromVAR numeric field/kind and focused admission gates on three JVMs."""
import argparse,concurrent.futures,json,os,subprocess
from pathlib import Path
from validate_mashr_native import JAVA21,LIBS,linux,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='chromvar-native-build-1');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in a.build and '\\' not in a.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-chromVAR-control-matrix-in-progress'))
    try:
        build=json.loads((BASE/a.build/'BUILD.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        fixture=ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-deviations';capture=json.loads((fixture/'CAPTURE.json').read_bytes());assert pin(fixture/'original-controls.zip')==capture['archive'] and all(pin(ROOT/n)==v for n,v in capture['source_pins'].items());names=['src/test/java/org/jlinalg/cli/ChromVarDeviationsTest.java','tools/literature/validate_chromvar_native.py']+[p.relative_to(ROOT).as_posix() for p in fixture.iterdir() if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        runner=out/'ChromVarChecks.java';runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class ChromVarChecks{public static void main(String[] args)throws Exception{if(!org.jlinalg.sequence.ChromVarDeviations.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual chromVAR candidate");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.ChromVarDeviationsTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));if(s.getTestsFoundCount()!=320||s.getTestsSucceededCount()!=320||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete original chromVAR controls");System.out.println("COMPLETE_NATIVE_CHROMVAR_CHECKS 320 NO_SKIPS");}}
''',encoding='utf8',newline='\n');classes=out/'test-classes';classes.mkdir();command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,ROOT/names[0]])
        cp=[classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def test(spec):
            name,java,convert,separator=spec;command(name,[*java,'-Xmx512m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'ChromVarChecks',convert(jar)]);assert 'COMPLETE_NATIVE_CHROMVAR_CHECKS 320 NO_SKIPS' in (out/(name+'.stdout')).read_text();return dict(name=name,tests=320,passed=320,skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(test,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-native-chromVAR-original-core-and-intermediate-controls',source_pins=sources,artifact=build['artifact'],platform_validation=runs,original_method_calls=306,focused_admission_properties=14,source_profile_case_protocols=102,case_populations=51,historical_whole_packages_executed=False,background_sampling_performed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-native-chromVAR-original-core-and-intermediate-controls',platform_validation=runs)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native method controls')
    main()
