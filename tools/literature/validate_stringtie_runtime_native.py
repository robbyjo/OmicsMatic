# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build exact Java17 overlay and exercise portable receipts plus real supervised Linux health."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin
from validate_stringtie_gtf import save

TESTS = ['org.jlinalg.cli.StringTieSetupEvidenceTest', 'org.jlinalg.cli.StringTieBamTest', 'org.jlinalg.cli.StringTieSetupExportTest', 'org.jlinalg.sequence.StringTieGtfTest', 'org.jlinalg.sequence.StringTieTablesTest', 'org.jlinalg.sequence.StringTieNormalizationTest']
VERSIONS = ['1.3.0', '1.3.6', '2.1.2', '2.1.7', '2.2.1', '3.0.0', '3.0.3']

def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True); parser.add_argument('--linux-root', required=True)
    args = parser.parse_args(); out = args.out.resolve(); base = ROOT / 'build/literature-review/stringtie-runtime'; native = args.linux_root
    assert out.is_relative_to(base.resolve()) and not out.exists() and native.startswith('/var/tmp/omicsmatic-stringtie-native-') and len(native.split('/')) == 4
    out.mkdir(); save(out / 'INCOMPLETE.json', dict(status='NATIVE_RUNTIME_VALIDATION_IN_PROGRESS', linux_root=native))
    try:
        previous = json.loads((ROOT / 'docs/literature/stringtie-intake-setup-development/VALIDATION.json').read_bytes())
        assert all(pin(ROOT / n) == p for n, p in previous['source_pins'].items())
        parent = ROOT / previous['artifact']['path']; assert pin(parent) == {k: previous['artifact'][k] for k in ['bytes', 'sha256']}
        names = ['src/main/java/org/jlinalg/cli/' + n + '.java' for n in ['StringTieSetupEvidence', 'StringTieHealth', 'StringTieRuntime']]
        names += ['src/test/java/org/jlinalg/cli/StringTieSetupEvidenceTest.java', 'src/test/java/org/jlinalg/cli/StringTieRuntimeChecks.java', 'src/test/resources/org/jlinalg/cli/stringtie-runtime-receipts.zip', 'tools/literature/freeze_stringtie_runtime_receipts.py', 'tools/literature/validate_stringtie_runtime_native.py']
        sources = dict(previous['source_pins']); sources.update({n: pin(ROOT / n) for n in names}); save(out / 'SOURCE-PINS.json', sources)
        for n in sources:
            destination = out / 'source-snapshot' / n; destination.parent.mkdir(parents=True, exist_ok=True); destination.write_bytes((ROOT / n).read_bytes())
        libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9 and all(pin(ROOT / n) == p for n, p in previous['dependency_pins'].items())

        def command(label, argv, timeout=240):
            argv = list(map(str, argv))
            with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
                result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode)); assert result.returncode == 0, (label, result.returncode)

        compiled = out / 'source-compiled'; compiled.mkdir()
        command('compile-exact-source', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', parent, '-d', compiled, *[ROOT / n for n in names[:3]]])
        classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob('*.class')}; assert classes
        jar = out / 'omicsmatic-0.3.7-stringtie-runtime-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as new:
            assert not set(classes).intersection(old.namelist())
            for info in old.infolist(): new.writestr(info, old.read(info.filename))
            for n, path in sorted(classes.items()):
                info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16; new.writestr(info, path.read_bytes())
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist())) and set(new.namelist()) - set(old.namelist()) == set(classes)
            assert all(new.read(n) == old.read(n) for n in old.namelist()); unchanged = len(old.namelist())
        runner = out / 'StringTieNativeTests.java'
        runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class StringTieNativeTests {public static void main(String[] args)throws Exception {
for(String name:new String[]{"org.jlinalg.cli.StringTieSetupEvidence","org.jlinalg.cli.StringTieHealth","org.jlinalg.cli.StringTieRuntime"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual runtime JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=1335||s.getTestsSucceededCount()!=1335||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native runtime checks");System.out.println("COMPLETE_STRINGTIE_NATIVE_CHECKS 1335 NO_SKIPS");}}
'''.replace('SELECTORS', ','.join('selectClass("' + n + '")' for n in TESTS)), encoding='utf-8', newline='\n')
        tests = out / 'test-classes'; tests.mkdir()
        command('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', tests, runner,
            ROOT / names[4], *[ROOT / 'src/test/java' / (n.replace('.', '/') + '.java') for n in TESTS]])
        command('javadoc', [JAVA21 / 'javadoc.exe', '-quiet', '-private', '-Xdoclint:all,-missing', '-Werror', '-encoding', 'UTF-8', '-classpath', jar, '-d', out / 'javadoc', *[ROOT / n for n in names[:3]]])
        cp = [tests, jar, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep), ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]
        def test(item):
            name, java, convert, separator = item; command(name, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, cp)), 'StringTieNativeTests', convert(jar)])
            assert 'COMPLETE_STRINGTIE_NATIVE_CHECKS 1335 NO_SKIPS' in (out / (name + '.stdout')).read_text(); return dict(platform=name, passed=1335, skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool: runs = list(pool.map(test, platforms))
        native_script = out / 'native-driver.py'; native_script.write_text(NATIVE, encoding='utf-8', newline='\n')
        command('native-original-health', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', linux(native_script), native, ':'.join(map(linux, cp[:2])), linux(out)], timeout=1500)
        native_result = json.loads((out / 'NATIVE.json').read_bytes()); assert native_result['fresh_original_health_fits'] == 8
        for name, java, convert, separator in platforms[:2]:
            command(name + '-unsupported', [*java, '-cp', separator.join(map(convert, cp[:2])), 'org.jlinalg.cli.StringTieRuntimeChecks', 'unsupported', 'linux-x86_64-stringtie-3.0.3', str(out / 'nonexistent-home'), str(out / 'nonexistent-report'), str(out / (name + '-unsupported-work'))])
        assert all(pin(ROOT / n) == p for n, p in sources.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical_executable_unchanged']
        result = dict(status='passed-native-exact-StringTie-runtime-admission', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), source_pins=sources, dependency_pins=previous['dependency_pins'],
            parent_artifact=previous['artifact'], new_classes={n: pin(p) for n, p in sorted(classes.items())}, unchanged_parent_entries=unchanged, platform_validation=runs, native=native_result,
            canonical_executable_unchanged=previous['canonical_executable_unchanged'], scientific_runtime_admission_available=True, public_fitting_workflow_available=False, user_analysis_admitted=False,
            implementation_credit=False, released=False, pushed=False, deployed=False, whole_goal_complete=False)
        save(out / 'COMPLETED.json', result); (out / 'INCOMPLETE.json').unlink(); print(json.dumps({k: result[k] for k in ['status', 'artifact', 'platform_validation', 'native']}))
    except BaseException as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_NO_RUNTIME_VALIDATION_ADMITTED', error_type=type(error).__name__, error=str(error), required_action='Preserve the attempt and native partials. Read original stdout/stderr/ledgers, correct the cause and rerun into NEW paths. No user fit is admitted.'))
        (out / 'WARNINGS.md').write_text('# FAIL2: StringTie native validation failed\n\n' + str(error) + '\n\nPreserve this attempt. Read FAILURE.json and retained process logs; correct the cause and rerun into NEW paths. No user analysis is admitted.\n', encoding='utf-8', newline='\n'); raise

NATIVE = r'''
import hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
base,cp,out=sys.argv[1:];base=Path(base);out=Path(out);assert not base.exists();base.mkdir(mode=0o755)
old=Path('/var/tmp/omicsmatic-stringtie-setup-20261103-1');versions=['1.3.0','1.3.6','2.1.2','2.1.7','2.2.1','3.0.0','3.0.3'];records=[]
def run(label,mode,version,home,report,uid=None):
 work=base/('work-'+label)
 command=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-cp',cp,'org.jlinalg.cli.StringTieRuntimeChecks',mode,'linux-x86_64-stringtie-'+version,str(home),str(report),str(work)]
 if uid:command=['/usr/bin/setpriv','--reuid='+str(uid),'--regid='+str(uid),'--clear-groups',*command]
 with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
  result=subprocess.run(command,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C'},stdout=stdout,stderr=stderr,timeout=160,check=False)
 record=dict(label=label,mode=mode,command=command,exit_code=result.returncode,work=str(work));records.append(record)
 (out/(label+'.process.json')).write_text(json.dumps(record,indent=2)+'\n');assert result.returncode==0,(label,result.returncode)
 if mode=='admit':shutil.copytree(work,out/'native-evidence'/label)
for version in versions:run('health-'+version,'admit',version,old/('home-'+version),old/('install-'+version))
run('changed-admission','tamper','3.0.3',old/'home-3.0.3',old/'recheck-3.0.3')
run('missing-home','missing-home','3.0.3',base/'not-installed',old/'install-3.0.3')
run('wrong-setup','wrong-setup','3.0.3',old/'home-3.0.3',old/'install-1.3.0')
run('capacity','capacity','3.0.3',old/'home-3.0.3',old/'install-3.0.3')
run('cancel','cancel','3.0.3',old/'home-3.0.3',old/'install-3.0.3')
# Real permission denial by an unprivileged UID, with a writable owned work parent.
permitted=base/'permission-work';permitted.mkdir(mode=0o700);os.chown(permitted,65534,65534)
protected=base/'forbidden-home';protected.mkdir(mode=0o700)
saved=base;base=permitted
run('forbidden-home','forbidden-home','3.0.3',protected,old/'install-3.0.3',65534)
base=saved
for record in records:
 if record['mode']!='admit':shutil.copytree(record['work'],out/'native-evidence'/record['label'])
(out/'NATIVE.json').write_text(json.dumps(dict(status='passed-seven-edition-fresh-supervised-health-and-retained-negative-controls',linux_root=str(base),fresh_original_health_fits=8,all_seven_editions=True,actual_permission_skip_uid=65534,records=records,user_analysis_admitted=False),indent=2)+'\n')
'''

if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete validation checks')
    main()
