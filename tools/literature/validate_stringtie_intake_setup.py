# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate complete BAM intake and portable setup export from an exact private development JAR."""
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

STANDALONE = r'''
import hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
source,base,out=map(Path,sys.argv[1:]);assert base.parent==Path('/var/tmp') and base.name.startswith('omicsmatic-stringtie-setup-')
kit=base/'standalone-exported-kit-1';home=base/'standalone-home-1';report=base/'standalone-report-1';cache=base/'standalone-cache-1';work=base/'standalone-work-1'
assert all(not p.exists() for p in [kit,home,report,cache,work]);shutil.copytree(source,kit);work.mkdir(mode=0o700)
def pin(p):
 with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
export=json.loads((kit/'export.json').read_bytes());assert export['status']=='EXPORTED_KIT_NOT_EXECUTED' and not export['installation_performed']
assert all(pin(kit/n)==v for n,v in export['files'].items())
command=['/usr/bin/python3','-I','-B',str(kit/'install_stringtie.py'),'--kit',str(kit),'--profile','linux-x86_64-stringtie-3.0.3','--home',str(home),'--report',str(report),'--cache',str(cache),'--archive-dir',str(base/'download-cache'),'--allow-install']
with (out/'standalone-installer.stdout').open('xb') as stdout,(out/'standalone-installer.stderr').open('xb') as stderr:
 r=subprocess.run(command,cwd=work,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C','HOME':str(work),'TMPDIR':str(work)},stdout=stdout,stderr=stderr,timeout=90,check=False)
assert r.returncode==0
completion=json.loads((report/'COMPLETED.json').read_bytes());assert completion['status']=='COMPLETE_SETUP_ONLY_NOT_ADMISSION' and completion['manifest']==pin(report/'manifest.json') and not(report/'INCOMPLETE.json').exists()
manifest=json.loads((report/'manifest.json').read_bytes());assert not manifest['scientific_runtime_admitted'] and not manifest['user_analysis_admitted']
assert all(pin(home/n)==v for n,v in manifest['payload']['files'].items()) and all(pin(report/n)==v for n,v in manifest['files'].items())
assert all(pin(kit/n)==v for n,v in export['files'].items());shutil.copytree(report,out/'standalone-report')
(out/'STANDALONE.json').write_text(json.dumps(dict(status='passed-actual-JAR-exported-kit-standalone-offline-private-install',command=command,exit_code=r.returncode,working_directory=str(work),kit=str(kit),home=str(home),report=str(report),exported_files=export['files'],scientific_runtime_admitted=False,user_analysis_admitted=False,repository_code_imports_required=False),indent=2)+'\n')
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve(); base = ROOT / 'build/literature-review/stringtie-runtime'
    assert out.is_relative_to(base.resolve()) and not out.exists(); out.mkdir(parents=True); save(out / 'INCOMPLETE.json', dict(status='INTAKE_SETUP_VALIDATION_IN_PROGRESS'))
    try:
        previous = json.loads((ROOT / 'docs/literature/stringtie-normalization-development/VALIDATION.json').read_bytes())
        assert all(pin(ROOT / name) == value for name, value in previous['source_pins'].items())
        parent = ROOT / previous['artifact']['path']; assert pin(parent) == {k: previous['artifact'][k] for k in ['bytes', 'sha256']}
        libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9 and all(pin(ROOT / n) == v for n, v in previous['dependency_pins'].items())
        names = ['src/main/java/org/jlinalg/cli/StringTieBam.java', 'src/main/java/org/jlinalg/cli/StringTieSetupExport.java',
                 'src/test/java/org/jlinalg/cli/StringTieBamTest.java', 'src/test/java/org/jlinalg/cli/StringTieSetupExportTest.java',
                 'tools/literature/validate_stringtie_intake_setup.py', 'tools/literature/prepare_stringtie_runtime_lock.py',
                 'tools/literature/install_stringtie.py', 'tools/literature/test_stringtie_setup.py']
        resource_root = ROOT / 'src/main/resources/org/jlinalg/omics/stringtie-runtime-setup'
        resources = {p.relative_to(ROOT / 'src/main/resources').as_posix(): p for p in sorted(resource_root.rglob('*')) if p.is_file() and p.name != '.gitattributes'}
        assert len(resources) == 13
        names.extend(p.relative_to(ROOT).as_posix() for p in sorted(resource_root.rglob('*')) if p.is_file())
        sources = dict(previous['source_pins']); sources.update({n: pin(ROOT / n) for n in names}); save(out / 'SOURCE-PINS.json', sources)
        for n in sources:
            destination = out / 'source-snapshot' / n; destination.parent.mkdir(parents=True, exist_ok=True); destination.write_bytes((ROOT / n).read_bytes())

        def command(name, argv, timeout=180):
            argv = list(map(str, argv))
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            save(out / (name + '.process.json'), dict(command=argv, exit_code=process.returncode)); assert process.returncode == 0, (name, process.returncode)

        compiled = out / 'source-compiled'; compiled.mkdir()
        command('compile-exact-source', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', parent, '-d', compiled, ROOT / names[0], ROOT / names[1]])
        classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob('*.class')}; assert classes and all(n.startswith(('org/jlinalg/cli/StringTieBam', 'org/jlinalg/cli/StringTieSetupExport')) for n in classes)
        jar = out / 'omicsmatic-0.3.7-stringtie-intake-setup-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as new:
            assert not (set(classes) | set(resources)).intersection(old.namelist())
            for info in old.infolist(): new.writestr(info, old.read(info.filename))
            for n, path in sorted({**classes, **resources}.items()):
                info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16; new.writestr(info, path.read_bytes())
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
            assert set(new.namelist()) - set(old.namelist()) == set(classes) | set(resources) and all(new.read(n) == old.read(n) for n in old.namelist())
            assert all(new.read(n) == p.read_bytes() for n, p in {**classes, **resources}.items()); unchanged = len(old.namelist())
        test_names = ['org.jlinalg.cli.StringTieBamTest', 'org.jlinalg.cli.StringTieSetupExportTest', 'org.jlinalg.sequence.StringTieGtfTest', 'org.jlinalg.sequence.StringTieTablesTest', 'org.jlinalg.sequence.StringTieNormalizationTest']
        runner = out / 'StringTieIntakeSetupChecks.java'
        runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class StringTieIntakeSetupChecks {public static void main(String[] args)throws Exception {
for(String name:new String[]{"org.jlinalg.cli.StringTieBam","org.jlinalg.cli.StringTieSetupExport","org.jlinalg.sequence.StringTieNormalization"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual intake/setup JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=1307||s.getTestsSucceededCount()!=1307||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete intake/setup checks");System.out.println("COMPLETE_STRINGTIE_INTAKE_SETUP_CHECKS 1307 NO_SKIPS");}}
'''.replace('SELECTORS', ','.join('selectClass("' + n + '")' for n in test_names)), encoding='utf-8', newline='\n')
        tests = out / 'test-classes'; tests.mkdir()
        command('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', tests, runner,
                                  *[ROOT / 'src/test/java' / (n.replace('.', '/') + '.java') for n in test_names]])
        command('javadoc', [JAVA21 / 'javadoc.exe', '-quiet', '-private', '-Xdoclint:all,-missing', '-Werror', '-encoding', 'UTF-8', '-classpath', jar, '-d', out / 'javadoc', ROOT / names[0], ROOT / names[1]])
        classpath = [tests, jar, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                     ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]

        def test(spec):
            name, java, convert, separator = spec
            command(name, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, classpath)), 'StringTieIntakeSetupChecks', convert(jar)])
            assert 'COMPLETE_STRINGTIE_INTAKE_SETUP_CHECKS 1307 NO_SKIPS' in (out / (name + '.stdout')).read_text()
            export = out / ('export-' + name); command(name + '-actual-main-export', [*java, '-cp', convert(jar), 'org.jlinalg.cli.StringTieSetupExport', '--out', convert(export)])
            document = json.loads((export / 'export.json').read_bytes()); assert len(document['files']) == 14 and document['status'] == 'EXPORTED_KIT_NOT_EXECUTED'
            assert all(pin(export / n) == v for n, v in document['files'].items()) and not (export / 'INCOMPLETE.txt').exists()
            assert set(p.relative_to(export).as_posix() for p in export.rglob('*') if p.is_file()) == set(document['files']) | {'export.json'}
            return dict(name=name, tests=1307, passed=1307, skipped=0, actual_main_kit_export=True)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool: runs = list(pool.map(test, platforms))
        setup = json.loads((base / 'setup-validation-1/COMPLETED.json').read_bytes()); assert setup['offline_installs'] == setup['unchanged_payload_rechecks'] == 7 and setup['download_status'].startswith('passed-two-')
        standalone = out / 'standalone-install.py'; standalone.write_text(STANDALONE, encoding='utf-8', newline='\n')
        command('standalone-offline-install', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', linux(standalone), linux(out / 'export-linux-java21'), setup['linux_base'], linux(out)])
        standalone_result = json.loads((out / 'STANDALONE.json').read_bytes()); assert not standalone_result['repository_code_imports_required'] and not standalone_result['user_analysis_admitted']
        assert all(pin(ROOT / n) == v for n, v in sources.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical_executable_unchanged']
        result = dict(schema_version=1, status='passed-native-complete-StringTie-BAM-intake-and-portable-exact-setup-export', source_pins=sources, dependency_pins=previous['dependency_pins'],
                      artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), parent_artifact=previous['artifact'], parent_validation=pin(ROOT / 'docs/literature/stringtie-normalization-development/VALIDATION.json'), parent_archive=pin(ROOT / 'docs/literature/stringtie-normalization-development/controls.zip'),
                      new_classes={n: pin(p) for n, p in sorted(classes.items())}, new_resources={n: pin(p) for n, p in sorted(resources.items())}, exact_new_source_classes=len(classes), unchanged_parent_entries=unchanged,
                      platform_validation=runs, actual_main_exports=3, standalone_export_install_passed=True, setup_validation=pin(base / 'setup-validation-1/COMPLETED.json'),
                      offline_installs=7, unchanged_payload_rechecks=7, exact_official_downloads=2, actual_permission_skip_uid=65534, strict_Java17_source=True, javadoc_passed=True,
                      canonical_executable_unchanged=previous['canonical_executable_unchanged'], public_setup_export_available=True, public_fitting_workflow_available=False,
                      scientific_runtime_admitted=False, user_analysis_admitted=False, implementation_credit=False, released=False, pushed=False, deployed=False, whole_goal_complete=False,
                      remaining='Validated complete raw BAM intake and standalone exact-payload setup/export. Current-host ELF/library/native health admission, typed StringTie assembly/estimation/merge fits, portable complete-fit verification and catalog gates remain. Source counts/coverage and biological inference are not certified. StringTie stays missing.')
        save(out / 'COMPLETED.json', result); (out / 'INCOMPLETE.json').unlink()
        print(json.dumps({k: result[k] for k in ['status', 'artifact', 'exact_new_source_classes', 'unchanged_parent_entries', 'platform_validation', 'actual_main_exports', 'standalone_export_install_passed']})); return 0
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='failed-intake-setup-validation-no-admitted-result', error_type=type(error).__name__, error=str(error), action='Read INCOMPLETE.json and original process/stdout/stderr. Preserve this attempt and any named private Linux homes. Correct the cause and repeat into NEW paths; no analysis or integration credit is admitted.'))
        (out / 'WARNINGS.md').write_text('# FAIL2: STRINGTIE INTAKE/SETUP VALIDATION FAILED\n\n' + str(error) + '\n\nPreserve attempts and private homes, read FAILURE.json and original logs, correct the cause and retry into NEW paths. No analysis is admitted.\n', encoding='utf-8', newline='\n'); raise


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete native intake/setup validation')
    raise SystemExit(main())
