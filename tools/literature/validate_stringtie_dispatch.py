# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact one-class root overlay plus real packaged StringTie fitting, verification and dependency skips."""
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
from validate_mashr_native import JAVA21,LIBS,linux,pin
from validate_stringtie_gtf import save
from validate_stringtie_workflow import TESTS

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--linux-root',required=True);args=parser.parse_args();out=args.out.resolve();base=ROOT/'build/literature-review/stringtie-runtime'
    assert out.is_relative_to(base.resolve()) and not out.exists() and args.linux_root.startswith('/var/tmp/omicsmatic-stringtie-root-') and len(args.linux_root.split('/'))==4;out.mkdir();save(out/'INCOMPLETE.json',dict(status='STRINGTIE_ROOT_VALIDATION_IN_PROGRESS'))
    try:
        prior=json.loads((ROOT/'docs/literature/stringtie-typed-workflow/VALIDATION.json').read_bytes());assert all(pin(ROOT/n)==p for n,p in prior['source_pins'].items());parent=ROOT/prior['artifact']['path'];assert pin(parent)=={k:prior['artifact'][k] for k in ['bytes','sha256']}
        names=['src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/test/java/org/jlinalg/cli/StringTieDispatchTest.java','tools/literature/validate_stringtie_dispatch.py','tools/literature/stringtie_dispatch_bridge.py'];sources=dict(prior['source_pins']);sources.update({n:pin(ROOT/n) for n in names});save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            dest=out/'source-snapshot'/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/n).read_bytes())
        def command(label,argv,timeout=180,expected=0):
            argv=list(map(str,argv))
            with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout,check=False)
            save(out/(label+'.process.json'),dict(command=argv,exit_code=process.returncode,expected_exit=expected));assert process.returncode==expected,(label,process.returncode,expected)
        baseline=out/'baseline';baseline.mkdir();(baseline/'JLinAlgCli.java').write_bytes((base/'root-dispatch-1/before-JLinAlgCli.java').read_bytes());baseline_classes=out/'baseline-classes';baseline_classes.mkdir()
        command('compile-parent-dispatcher',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',parent,'-d',baseline_classes,baseline/'JLinAlgCli.java'])
        with zipfile.ZipFile(parent) as old:assert (baseline_classes/'org/jlinalg/cli/JLinAlgCli.class').read_bytes()==old.read('org/jlinalg/cli/JLinAlgCli.class')
        compiled=out/'source-compiled';compiled.mkdir();command('compile-exact-dispatcher',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',parent,'-d',compiled,ROOT/names[0]])
        classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert set(classes)=={'org/jlinalg/cli/JLinAlgCli.class'}
        jar=out/'omicsmatic-0.3.7-stringtie-tools-development.jar'
        with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar,'x') as new:
            for info in old.infolist():new.writestr(info,classes[info.filename].read_bytes() if info.filename in classes else old.read(info.filename))
        with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and new.namelist()==old.namelist() and len(new.namelist())==len(set(new.namelist()))==19619
            assert {n for n in old.namelist() if old.read(n)!=new.read(n)}==set(classes);assert all(new.read(n)==p.read_bytes() for n,p in classes.items())
        test_names=TESTS+['org.jlinalg.cli.StringTieDispatchTest'];libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9 and all(pin(ROOT/n)==p for n,p in prior['dependency_pins'].items())
        runner=out/'StringTieRootTests.java';runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class StringTieRootTests {public static void main(String[] args)throws Exception {
for(String name:new String[]{"org.jlinalg.cli.JLinAlgCli","org.jlinalg.cli.StringTieToolsCli","org.jlinalg.cli.StringTieWorkflow"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual root JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=1361||s.getTestsSucceededCount()!=1361||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete root checks");System.out.println("COMPLETE_STRINGTIE_ROOT_CHECKS 1361 NO_SKIPS");}}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in test_names)),encoding='utf-8',newline='\n')
        tests=out/'test-classes';tests.mkdir();command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',tests,runner,*[ROOT/'src/test/java'/(n.replace('.','/')+'.java') for n in test_names]])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-private','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',ROOT/names[0]])
        cp=[tests,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def checks(item):
            name,java,convert,separator=item;command(name,[*java,'-Xmx512m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'StringTieRootTests',convert(jar)]);assert 'COMPLETE_STRINGTIE_ROOT_CHECKS 1361 NO_SKIPS' in (out/(name+'.stdout')).read_text()
            for label,options,expected in [('root-help',['--help'],0),('stringtie-help',['stringtie-tools','--help'],0),('profiles',['stringtie-tools','--runtime-profiles'],0),('missing-runtime',['stringtie-tools','--recipe','not-opened.json','--out',str(out/(name+'-not-created'))],3),('bad-flags',['stringtie-tools','--flags','-rf'],2)]:command(name+'-'+label,[*java,'-jar',convert(jar),*options],expected=expected)
            return dict(platform=name,passed=1361,skipped=0,actual_main_controls=5)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(checks,platforms))
        driver=out/'native-driver.py';driver.write_text(NATIVE,encoding='utf-8',newline='\n');command('actual-main-three-fits',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(driver),args.linux_root,linux(jar),linux(out)],timeout=900)
        for name,java,convert,separator in platforms[:2]:command(name+'-actual-main-copied-verify',[*java,'-Xmx512m','-jar',convert(jar),'stringtie-tools','--verify',convert(out/'bundles/estimation')])
        assert all(pin(ROOT/n)==p for n,p in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical_executable_unchanged']
        result=dict(schema_version=1,status='passed-public-root-StringTie-dispatch-and-actual-main-workflows',source_pins=sources,dependency_pins=prior['dependency_pins'],artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=prior['artifact'],parent_validation=pin(ROOT/'docs/literature/stringtie-typed-workflow/VALIDATION.json'),parent_archive=pin(ROOT/'docs/literature/stringtie-typed-workflow/controls.zip'),changed_classes={n:pin(p) for n,p in classes.items()},unchanged_parent_entries=19618,platform_validation=runs,actual_main_fits=3,actual_main_native_verifies=3,actual_main_copied_windows_verifies=2,native=json.loads((out/'NATIVE.json').read_bytes()),canonical_executable_unchanged=prior['canonical_executable_unchanged'],root_dispatch_available=True,scientific_classes_unchanged=True,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps({k:result[k] for k in ['status','artifact','platform_validation','actual_main_fits']}))
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='FAIL2_ROOT_VALIDATION_FAILED_NO_CREDIT',error=str(error),required_action='Preserve attempts and native partials, read logs, correct the cause and retry into NEW paths.'));raise

NATIVE=r'''
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
base,jar,out=map(Path,sys.argv[1:]);assert not base.exists();base.mkdir(mode=0o700);native_jar=base/'candidate.jar';shutil.copyfile(jar,native_jar)
def pin(p):
 with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
assert pin(jar)==pin(native_jar);example=Path('/var/tmp/omicsmatic-stringtie-example-20261103-1/example');home=Path('/var/tmp/omicsmatic-stringtie-setup-20261103-1/home-3.0.3');setup=home.parent/'install-3.0.3';records=[]
def run(label,options):
 command=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-jar',str(native_jar),'stringtie-tools',*map(str,options)]
 with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C'},stdout=stdout,stderr=stderr,timeout=180,check=False)
 row=dict(label=label,command=command,exit_code=r.returncode);records.append(row);(out/(label+'.process.json')).write_text(json.dumps(row,indent=2)+'\n');assert r.returncode==0,(label,r.returncode)
for request,label in [('assemble','assembly'),('estimate','estimation'),('merge','merged')]:
 document=json.loads((example/(request+'.json')).read_bytes())
 if request=='merge':document['merge_inputs']=[str(base/'assembly/fit/transcripts.gtf')]
 source=base/(request+'.json');source.write_text(json.dumps(document,indent=2)+'\n');bundle=base/label
 run(label,['--recipe',source,'--home',home,'--setup-report',setup,'--out',bundle]);run(label+'-verify',['--verify',bundle])
 science=lambda p:b''.join(row for row in p.read_bytes().splitlines(keepends=True) if not row.startswith(b'#'))
 assert science(bundle/'fit/transcripts.gtf')==science(example/label/'fit/transcripts.gtf')
 if request=='estimate':
  for name in ['gene-abundance.tsv','t_data.ctab','e_data.ctab','i_data.ctab','e2t.ctab','i2t.ctab']:assert (bundle/'fit'/name).read_bytes()==(example/label/'fit'/name).read_bytes()
 shutil.copytree(bundle,out/'bundles'/label)
assert pin(jar)==pin(native_jar)
(out/'NATIVE.json').write_text(json.dumps(dict(status='passed-three-actual-root-fits-and-verifies-matching-prior-standalone-results',linux_root=str(base),byte_identical_native_JAR=pin(native_jar),records=records),indent=2)+'\n')
'''

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete root dispatch validation')
    main()
