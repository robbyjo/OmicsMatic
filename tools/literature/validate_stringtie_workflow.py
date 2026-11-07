# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build exact Java17 workflow overlay; execute real user commands and portable verification."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin
from validate_stringtie_gtf import save

TESTS = ['org.jlinalg.cli.StringTieRecipeTest', 'org.jlinalg.cli.StringTieSetupEvidenceTest', 'org.jlinalg.cli.StringTieBamTest', 'org.jlinalg.cli.StringTieSetupExportTest', 'org.jlinalg.sequence.StringTieGtfTest', 'org.jlinalg.sequence.StringTieTablesTest', 'org.jlinalg.sequence.StringTieNormalizationTest']
NAMES = ['StringTieRecipe', 'StringTieInputs', 'StringTieOutput', 'StringTieCapturedRuntime', 'StringTieWorkflow', 'StringTieToolsCli']

def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True); parser.add_argument('--linux-root', required=True); parser.add_argument('--reuse', type=Path)
    args = parser.parse_args(); out = args.out.resolve(); base = ROOT / 'build/literature-review/stringtie-runtime'; native = args.linux_root
    assert out.is_relative_to(base.resolve()) and not out.exists() and native.startswith('/var/tmp/omicsmatic-stringtie-workflow-') and len(native.split('/')) == 4
    out.mkdir(); save(out / 'INCOMPLETE.json', dict(status='STRINGTIE_WORKFLOW_VALIDATION_IN_PROGRESS', linux_root=native))
    try:
        previous = json.loads((ROOT / 'docs/literature/stringtie-runtime-development/VALIDATION.json').read_bytes())
        assert all(pin(ROOT / n) == p for n, p in previous['source_pins'].items())
        parent = ROOT / previous['artifact']['path']; assert pin(parent) == {k: previous['artifact'][k] for k in ['bytes', 'sha256']}
        names = ['src/main/java/org/jlinalg/cli/' + n + '.java' for n in NAMES] + ['src/test/java/org/jlinalg/cli/StringTieRecipeTest.java', 'tools/literature/validate_stringtie_workflow.py']
        reuse = args.reuse.resolve() if args.reuse else None
        if reuse:
            assert reuse.is_relative_to(base.resolve()) and reuse != out and (reuse / 'bundles').is_dir()
            prior_sources = json.loads((reuse / 'SOURCE-PINS.json').read_bytes()); assert all(pin(ROOT / n) == prior_sources[n] for n in names[:7])
        sources = dict(previous['source_pins']); sources.update({n: pin(ROOT / n) for n in names}); save(out / 'SOURCE-PINS.json', sources)
        for n in sources:
            destination = out / 'source-snapshot' / n; destination.parent.mkdir(parents=True, exist_ok=True); destination.write_bytes((ROOT / n).read_bytes())
        libraries = sorted(LIBS.glob('*.jar')); assert len(libraries) == 9 and all(pin(ROOT / n) == p for n, p in previous['dependency_pins'].items())
        def command(label, argv, timeout=240, expected=0):
            argv = list(map(str, argv))
            with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
                result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            save(out / (label + '.process.json'), dict(command=argv, exit_code=result.returncode, expected_exit=expected)); assert result.returncode == expected, (label, result.returncode, expected)
        compiled = out / 'source-compiled'; compiled.mkdir()
        command('compile-exact-source', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', parent, '-d', compiled, *[ROOT / n for n in names[:6]]])
        classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob('*.class')}; assert classes
        jar = out / 'omicsmatic-0.3.7-stringtie-workflow-development.jar'
        if reuse: shutil.copyfile(reuse / jar.name, jar)
        else:
            with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as new:
                assert not set(classes).intersection(old.namelist())
                for info in old.infolist(): new.writestr(info, old.read(info.filename))
                for n, path in sorted(classes.items()):
                    info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16; new.writestr(info, path.read_bytes())
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist())) and set(new.namelist()) - set(old.namelist()) == set(classes)
            assert all(new.read(n) == old.read(n) for n in old.namelist()) and all(new.read(n)==p.read_bytes() for n,p in classes.items()); unchanged = len(old.namelist())
        if reuse: assert pin(jar) == pin(reuse / jar.name), 'Only byte-identical rebuilt candidates may reuse complete per-fit results'
        if reuse:
            for bundle in sorted((reuse/'bundles').iterdir()):
                assert bundle.is_dir() and (bundle/'COMPLETED.json').is_file() and not(bundle/'INCOMPLETE.json').exists() and not(bundle/'FAILURE.json').exists()
                assert json.loads((bundle/'COMPLETED.json').read_bytes())['manifest']==pin(bundle/'manifest.json')
                shutil.copytree(bundle,out/'bundles'/bundle.name)
        runner = out / 'StringTieWorkflowTests.java'
        runner.write_text('''import java.io.File;import java.io.PrintWriter;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class StringTieWorkflowTests {public static void main(String[] args)throws Exception {
for(String name:new String[]{"org.jlinalg.cli.StringTieRecipe","org.jlinalg.cli.StringTieWorkflow","org.jlinalg.cli.StringTieToolsCli"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual workflow JAR");
var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
if(s.getTestsFoundCount()!=1353||s.getTestsSucceededCount()!=1353||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete workflow checks");System.out.println("COMPLETE_STRINGTIE_WORKFLOW_CHECKS 1353 NO_SKIPS");}}
'''.replace('SELECTORS', ','.join('selectClass("' + n + '")' for n in TESTS)), encoding='utf-8', newline='\n')
        tests = out / 'test-classes'; tests.mkdir()
        command('compile-tests', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', tests, runner, *[ROOT / 'src/test/java' / (n.replace('.', '/') + '.java') for n in TESTS]])
        command('javadoc', [JAVA21 / 'javadoc.exe', '-quiet', '-private', '-Xdoclint:all,-missing', '-Werror', '-encoding', 'UTF-8', '-classpath', jar, '-d', out / 'javadoc', *[ROOT / n for n in names[:6]]])
        cp = [tests, jar, ROOT / 'src/test/resources', *libraries]
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep), ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux, ':')]
        def test(item):
            name, java, convert, separator = item; command(name, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, cp)), 'StringTieWorkflowTests', convert(jar)])
            assert 'COMPLETE_STRINGTIE_WORKFLOW_CHECKS 1353 NO_SKIPS' in (out / (name + '.stdout')).read_text(); return dict(platform=name, passed=1353, skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool: runs = list(pool.map(test, platforms))
        native_script = out / 'native-driver.py'; native_script.write_text(NATIVE, encoding='utf-8', newline='\n')
        command('native-user-workflows', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', linux(native_script), native, linux(jar), linux(out), linux(reuse) if reuse else '-'], timeout=2400)
        native_result = json.loads((out / 'NATIVE.json').read_bytes()); assert native_result['complete_fit_bundles'] == 24
        bundles = sorted((out / 'bundles').iterdir()); assert len(bundles) == 24
        def portable(item):
            name, java, convert, separator = item
            for bundle in bundles: command(name + '-portable-' + bundle.name, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', convert(jar), 'org.jlinalg.cli.StringTieToolsCli', '--verify', convert(bundle)], timeout=90)
            return dict(platform=name, complete_captured_verifications=len(bundles))
        # Linux verification is performed on an actual copied native-filesystem
        # bundle and byte-identical copied JAR. Avoid thousands of incidental
        # DrvFs metadata round trips without reducing any scientific/file check.
        portable_zip=out/'portable-bundles.zip'
        with zipfile.ZipFile(portable_zip,'x',compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((out/'bundles').rglob('*')):
                assert not path.is_symlink()
                if path.is_file():archive.write(path,path.relative_to(out/'bundles').as_posix())
        portable_driver=out/'portable-linux.py';portable_driver.write_text(PORTABLE,encoding='utf-8',newline='\n')
        def linux_portable():
            command('linux-native-copy-portable', ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(portable_driver),native,linux(jar),linux(portable_zip),linux(out)],timeout=600)
            return dict(platform='linux-java21',complete_captured_verifications=24,copied_native_filesystem=True,byte_identical_copied_JAR=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            futures=[pool.submit(portable,p) for p in platforms[:2]]+[pool.submit(linux_portable)];portable_runs=[f.result() for f in futures]
        for name, java, convert, separator in platforms[:2]:
            command(name + '-native-skip', [*java, '-cp', convert(jar), 'org.jlinalg.cli.StringTieToolsCli', '--recipe', convert(out / 'not-opened.json'), '--home', convert(out / 'missing-home'), '--setup-report', convert(out / 'missing-setup'), '--out', convert(out / (name + '-not-created'))], expected=3)
            assert not (out / (name + '-not-created')).exists()
        assert all(pin(ROOT / n) == p for n, p in sources.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical_executable_unchanged']
        result = dict(status='passed-typed-StringTie-short-read-assembly-estimation-merge-and-portable-verification', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), source_pins=sources, dependency_pins=previous['dependency_pins'],
            parent_artifact=previous['artifact'], new_classes={n: pin(p) for n, p in sorted(classes.items())}, unchanged_parent_entries=unchanged, platform_validation=runs, portable_validation=portable_runs, native=native_result, reused_attempt=None if reuse is None else reuse.relative_to(ROOT).as_posix(),
            canonical_executable_unchanged=previous['canonical_executable_unchanged'], standalone_public_fitting_workflow_available=True, root_dispatch_available=False, implementation_credit=False, released=False, pushed=False, deployed=False, whole_goal_complete=False)
        save(out / 'COMPLETED.json', result); (out / 'INCOMPLETE.json').unlink(); print(json.dumps({k: result[k] for k in ['status', 'artifact', 'platform_validation', 'portable_validation']}))
    except BaseException as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_NO_WORKFLOW_VALIDATION_ADMITTED', error_type=type(error).__name__, error=str(error), required_action='Preserve the attempt and native partials. Read stdout/stderr and original logs, correct the cause and retry into NEW paths. No integration credit is admitted.'))
        (out / 'WARNINGS.md').write_text('# FAIL2: StringTie workflow validation failed\n\n' + str(error) + '\n\nPreserve this attempt. Read FAILURE.json and retained original logs; correct the cause and retry into NEW paths.\n', encoding='utf-8', newline='\n'); raise

NATIVE = r'''
import copy,hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
base,jar,out=map(Path,sys.argv[1:4]);reuse=Path(sys.argv[4]) if sys.argv[4]!='-' else None;assert not base.exists();base.mkdir(mode=0o700)
root=Path('/mnt/d/projects/JLinAlg');old=Path('/var/tmp/omicsmatic-stringtie-setup-20261103-1');original=root/'build/literature-review/stringtie-runtime/original-controls-1';records=[];completed=[];reused=[];fresh=[]
def pin(path):
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def invoke(label,args,expected=0):
 command=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-cp',str(jar),'org.jlinalg.cli.StringTieToolsCli',*map(str,args)]
 with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
  process=subprocess.run(command,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C'},stdout=stdout,stderr=stderr,timeout=180,check=False)
 record=dict(label=label,command=command,exit_code=process.returncode,expected_exit=expected);records.append(record);(out/(label+'.process.json')).write_text(json.dumps(record,indent=2)+'\n');(out/'NATIVE-PROGRESS.json').write_text(json.dumps(records,indent=2)+'\n');assert process.returncode==expected,(label,process.returncode,expected)
def recipe(version,mode):
 return dict(schema_version=1,profile='linux-x86_64-stringtie-'+version,mode=mode,bam=None if mode=='MERGE' else str(original/'inputs/reads.bam'),reference_sizes=str(original/'inputs/reference.sizes'),guide=str(original/'inputs/guide.gtf') if mode in ['ESTIMATE','MERGE'] else None,merge_inputs=[],genome_build='synthetic-two-contigs-not-human',strand='UNSTRANDED',bam_policies=None if mode=='MERGE' else dict(missing_NH='ERROR',missing_XS='ERROR',sort_header='REQUIRE_COORDINATE'),population='COMPLETE' if mode=='ESTIMATE' else None,threads=1,parameters=dict(trimming='ORIGINAL_DEFAULT',minimum_length=None,minimum_isoform_fraction=None,minimum_coverage=None,minimum_FPKM=None,minimum_TPM=None),limits={})
def fit(label,document,expected_case=None,home=None,expected=0):
 source=base/(label+'.json');source.write_text(json.dumps(document,indent=2)+'\n');bundle=base/label;version=document['profile'].removeprefix('linux-x86_64-stringtie-')
 candidate=reuse/'bundles'/label if reuse else None
 if expected==0 and candidate and candidate.is_dir():
  complete=json.loads((candidate/'COMPLETED.json').read_bytes());assert complete['manifest']==pin(candidate/'manifest.json') and not(candidate/'INCOMPLETE.json').exists() and not(candidate/'FAILURE.json').exists();prior=json.loads((candidate/'metadata/recipe.json').read_bytes())
  assert all(prior[k]==document[k] for k in document if k!='merge_inputs')
  if document['mode']=='MERGE':
   old_pins=json.loads((candidate/'metadata/source-pins.json').read_bytes());assert all(pin(Path(p))==old_pins['inputs/merge-%04d.gtf'%(i+1)]['pin'] for i,p in enumerate(document['merge_inputs']))
  bundle=candidate;reused.append(label)
 else:
  invoke(label,['--recipe',source,'--home',home or old/('home-'+version),'--setup-report',old/('install-'+version),'--out',bundle],expected)
  if not expected:fresh.append(label)
 if expected:
  assert (bundle/'INCOMPLETE.json').exists() and not(bundle/'COMPLETED.json').exists();assert json.loads((bundle/'FAILURE.json').read_bytes())['status'].startswith('SKIP3' if expected==3 else 'FAIL2');shutil.copytree(bundle,out/'negative-evidence'/label);return bundle
 completion=json.loads((bundle/'COMPLETED.json').read_bytes());assert completion['manifest']==pin(bundle/'manifest.json') and not(bundle/'INCOMPLETE.json').exists()
 if expected_case:
  science=lambda p:b''.join(line for line in p.read_bytes().splitlines(keepends=True) if not line.startswith(b'#'))
  assert science(bundle/'fit/transcripts.gtf')==science(original/version/expected_case/'transcripts.gtf'),(label,'original GTF scientific rows differ')
  if document['mode']=='ESTIMATE':
   for name in ['gene-abundance.tsv','t_data.ctab','e_data.ctab','i_data.ctab','e2t.ctab','i2t.ctab']:assert (bundle/'fit'/name).read_bytes()==(original/version/expected_case/name).read_bytes()
 if not(out/'bundles'/label).exists():shutil.copytree(bundle,out/'bundles'/label)
 completed.append(label);return bundle
for version in ['1.3.0','1.3.6','2.1.2','2.1.7','2.2.1','3.0.0','3.0.3']:
 assembly=fit(version+'-assembly',recipe(version,'ASSEMBLE'),'assembly')
 fit(version+'-estimate',recipe(version,'ESTIMATE'),'estimate')
 d=recipe(version,'MERGE');d['merge_inputs']=[str(assembly/'fit/transcripts.gtf'),str(original/version/'guided/transcripts.gtf')];d['parameters']['minimum_length']=150;fit(version+'-merge',d,'merge')
d=recipe('3.0.3','ASSEMBLE');d['guide']=str(original/'inputs/guide.gtf');fit('3.0.3-guided',d,'guided')
d=recipe('3.0.3','ESTIMATE');d['bam']=str(original/'inputs/empty.bam');empty=fit('3.0.3-empty-estimate',d,'empty-estimate');denominator=json.loads((empty/'metadata/OUTPUT-AUDIT.json').read_bytes())['TPM_denominator'];assert denominator['resolution']=='NO_POSITIVE_NUMERATOR' and denominator['positive_numerators']==denominator['positive_outputs']==0
d=recipe('3.0.0','ASSEMBLE');d['strand']='FR_FIRSTSTRAND';d['parameters'].update(trimming='DISABLE',minimum_length=150,minimum_isoform_fraction=0.3);fit('3.0.0-paper171-documented-rf',d,'paper171-documented-rf')
fit('missing-runtime',recipe('3.0.3','ASSEMBLE'),home=base/'not-installed',expected=3)
d=recipe('3.0.3','ASSEMBLE');d['limits']['bam_records']=1;fit('BAM-capacity',d,expected=2)
assert len(completed)==24
(out/'NATIVE.json').write_text(json.dumps(dict(status='passed-24-actual-public-command-fits-with-independent-original-output-comparisons',linux_root=str(base),complete_fit_bundles=len(completed),independent_original_comparisons=len(completed),fresh_native_runtime_health_fits=len(fresh),fresh_fit_labels=fresh,byte_identical_candidate_reused_fit_labels=reused,records=records),indent=2)+'\n')
'''

PORTABLE = r'''
import hashlib,json,shutil,stat,subprocess,sys,zipfile
from pathlib import Path,PurePosixPath
base,jar,archive,out=map(Path,sys.argv[1:]);target=base/'portable-copy';native_jar=base/'portable-candidate.jar';assert not target.exists() and not native_jar.exists();target.mkdir(mode=0o700)
def pin(path):
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
source_pin=pin(jar);shutil.copyfile(jar,native_jar);assert pin(native_jar)==source_pin
with zipfile.ZipFile(archive) as zipped:
 assert zipped.testzip() is None and len(zipped.namelist())==len(set(zipped.namelist()))
 for entry in zipped.infolist():
  name=PurePosixPath(entry.filename);assert not name.is_absolute() and '..' not in name.parts and '\\' not in entry.filename and not entry.is_dir() and stat.S_IFMT(entry.external_attr>>16)==stat.S_IFREG
  path=target.joinpath(*name.parts);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(zipped.read(entry))
records=[]
for bundle in sorted(target.iterdir()):
 label='linux-java21-portable-'+bundle.name;command=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-cp',str(native_jar),'org.jlinalg.cli.StringTieToolsCli','--verify',str(bundle)]
 with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(command,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C'},stdout=stdout,stderr=stderr,timeout=90,check=False)
 row=dict(command=command,exit_code=process.returncode,expected_exit=0);records.append(row);(out/(label+'.process.json')).write_text(json.dumps(row,indent=2)+'\n');assert process.returncode==0,(label,process.returncode)
assert len(records)==24 and pin(jar)==source_pin==pin(native_jar)
(out/'LINUX-PORTABLE.json').write_text(json.dumps(dict(status='passed-all24-complete-native-filesystem-copied-bundle-verifications',artifact=source_pin,native_artifact=str(native_jar),native_copies=str(target),complete_verifications=24,records=records),indent=2)+'\n')
'''

if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete workflow validation')
    main()
