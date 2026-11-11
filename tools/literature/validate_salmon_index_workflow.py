# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate the actual-JAR complete original Salmon index workflow and portable contracts."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, LIBS, linux, pin
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete workflow checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir()
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    prior = json.loads((BASE / 'index-ledger-development-1/matrix-1/COMPLETED.json').read_bytes())
    parent = ROOT / prior['artifact']['path']
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [n for n in old.namelist() if n.endswith('.class')]
        assert all(current.read(n) == old.read(n) for n in legacy)
        added = [n for n in current.namelist() if n.endswith('.class') and n not in legacy]
        assert len(added) == 21 and all(n.startswith(tuple('org/jlinalg/cli/' + c for c in ['SalmonReads', 'SalmonReferences', 'SalmonIndexRecipe', 'SalmonIndexAudit', 'SalmonIndexWorkflow'])) for n in added)
    tests = sorted([*ROOT.glob('src/test/java/org/jlinalg/cli/Salmon*Test.java'), *ROOT.glob('src/test/java/org/jlinalg/sequence/Salmon*Test.java')])
    assert len(tests) == 11
    sources = dict(prior['source_pins'])
    for path in [*tests, *ROOT.glob('src/main/java/org/jlinalg/cli/Salmon*.java'), Path(__file__), ROOT / 'tools/literature/freeze_salmon_index_input_fixtures.py',
                 *ROOT.glob('src/test/resources/org/jlinalg/cli/salmon-index-inputs/*')]:
        if path.is_file():
            sources[path.relative_to(ROOT).as_posix()] = pin(path)
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonIndexWorkflowChecks.java'
    selectors = ','.join('selectClass("' + t.relative_to(ROOT / 'src/test/java').as_posix()[:-5].replace('/', '.') + '")' for t in tests)
    runner.write_text('''package org.jlinalg.cli;
import java.io.*;import java.nio.file.*;import java.util.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonIndexWorkflowChecks {
 public static void main(String[] args)throws Exception {
  for(String n:new String[]{"SalmonIndexWorkflow","SalmonIndexAudit","SalmonIndexRecipe","SalmonReads","SalmonReferences","SalmonRuntime"})if(!Class.forName("org.jlinalg.cli."+n).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  if(args[1].equals("tests")){
   var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);
   var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
   if(s.getTestsFoundCount()!=344||s.getTestsSucceededCount()!=344||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete focused cases");
   System.out.println("COMPLETE_SALMON_CASES 344 NO_SKIPS");return;
  }
  if(args[1].equals("build")){
   System.err.println("INDEX_WORKFLOW_START "+args[2]);var result=SalmonIndexWorkflow.start(Path.of(args[3]),Path.of(args[4]),Path.of(args[5]),Path.of(args[6]),()->{});
   if(result.audit().ledger().decoys().matchesDeclaredPopulation()||result.audit().ledger().decoys().disagreements().size()!=1)throw new AssertionError("Disagreement erased");
   SalmonIndexWorkflow.verify(result.root(),()->{});System.out.println(NextflowJson.json(Map.of("profile",result.recipe().profile(),"audit",result.audit().summary(),"complete_native_files",SalmonIndexWorkflow.inventory(result.root(),result.recipe().limits().process(),()->{}))));
   System.err.println("INDEX_WORKFLOW_COMPLETE "+args[2]);return;
  }
  if(args[1].equals("verify")){
   var result=SalmonIndexWorkflow.verify(Path.of(args[2]),()->{});System.out.println("COMPLETE_PORTABLE_INDEX_ONLY "+result.recipe().profile()+" EXPRESSION_NOT_ADMITTED");return;
  }
  throw new AssertionError("Unknown control operation");
 }
}
'''.replace('SELECTORS', selectors), encoding='utf-8', newline='\n')

    def command(name, argv, required=True, timeout=2400):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout)
        receipt = dict(command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        if required:
            assert result.returncode == 0, (name, result.returncode)
        return receipt

    libraries = sorted(LIBS.glob('*.jar'))
    assert len(libraries) == 9
    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', os.pathsep.join(map(str, [jar, *libraries])), '-d', classes, runner, *tests])
    paths = [classes, jar, ROOT / 'src/test/resources', *libraries]
    platforms = [('windows-java21', [JAVA21 / 'java.exe'], str, os.pathsep), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str, os.pathsep),
                 ('linux-java21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java'], linux, ':')]

    def java(name, platform, operation, *values, required=True):
        _, binary, convert, separator = platform
        return command(name, [*binary, '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, paths)), 'org.jlinalg.cli.SalmonIndexWorkflowChecks', convert(jar), operation, *values], required=required)

    def focused(platform):
        result = java(platform[0] + '-tests', platform, 'tests')
        print(platform[0] + ':344 actual-JAR cases passed', flush=True)
        return result

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        matrix = list(pool.map(focused, platforms))
    remote = subprocess.check_output(['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', 'import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-index-workflow-",dir="/var/tmp"))']).decode().strip()
    assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-index-workflow-[A-Za-z0-9_-]+', remote)
    write(out / 'OWNER.json', dict(owned_linux_root=remote))
    setups = json.loads((BASE / 'managed-setup-development-2/COMPLETED.json').read_bytes())
    runs = []
    for setup in setups['rechecks']:
        version = setup['version']
        source = BASE / 'index-edge-controls-3' / version / 'mixed-keep/inputs'
        recipe = dict(schema_version=1, profile='linux-x86_64-salmon-' + version, fasta=linux(source / 'reference.fa'), encoding='PLAIN_UTF8', headers='SOURCE_WHITESPACE_TOKEN',
                      decoys=linux(source / 'decoys.txt'), k=15, threads=2, keep_duplicates=True, keep_fixed_fasta=version != '1.0.0', poly_a='SOURCE_DEFAULT',
                      limits=dict(wall_seconds=900, cpu_seconds=600, address_space_bytes=8589934592, file_bytes=536870912, workspace_bytes=1073741824, files=8192, threads=256, sampled_rss_bytes=6442450944))
        recipe_file = out / ('recipe-' + version + '.json')
        write(recipe_file, recipe)
        process = java('build-' + version, platforms[2], 'build', version, linux(recipe_file), setup['home'], linux(ROOT / setup['report']), remote + '/' + version, required=False)
        destination = out / (version if process['exit_code'] == 0 else version + '-FAILED')
        copy = 'from pathlib import Path;import sys,shutil;s=Path(sys.argv[1]);t=Path(sys.argv[2]);assert s.is_dir() and not t.exists();shutil.copytree(s,t,copy_function=shutil.copyfile)'
        command('copy-' + version, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', copy, remote + '/' + version, linux(destination)])
        if process['exit_code'] != 0:
            write(out / (version + '-FAILED.json'), dict(process=process, files={p.relative_to(destination).as_posix(): pin(p) for p in destination.rglob('*') if p.is_file()}))
            raise AssertionError((version, 'full failed attempt retained'))
        summary = json.loads((out / ('build-' + version + '.stdout')).read_bytes())
        assert set(summary['complete_native_files']) == {p.relative_to(destination).as_posix() for p in destination.rglob('*')}
        for name, row in summary['complete_native_files'].items():
            assert (destination / name).is_dir() == (row['kind'] == 'directory')
            if row['kind'] == 'file':
                assert pin(destination / name) == {k: row[k] for k in ['bytes', 'sha256']}
        portable = [java('portable-' + platform[0] + '-' + version, platform, 'verify', str(destination)) for platform in platforms[:2]]
        assert summary['audit']['expression_analysis_admitted'] is False and summary['audit']['declared_source_disagreements'] == 1
        runs.append(dict(version=version, native_process=process, portable=portable, audit=summary['audit'], manifest=pin(destination / 'manifest.json')))
        print(version + ':complete original index, full reference ledger and Windows portable verification passed; expression remains unadmitted', flush=True)

    # Reseal byte receipts deliberately: semantic replay must still reject these
    # internally inconsistent bundles. This is not an authenticity test.
    negatives = []
    base = out / '1.9.0'
    for kind in ['late-table-junk', 'wrong-audit', 'erased-ledger-disagreement', 'live-worker', 'failed-marker', 'extra-empty-directory']:
        clone = out / ('negative-' + kind)
        shutil.copytree(base, clone)
        if kind == 'late-table-junk':
            with (clone / 'index/ctable.bin').open('ab') as target:
                target.write(b'\x00')
        elif kind == 'wrong-audit':
            value = json.loads((clone / 'INDEX-AUDIT.json').read_bytes()); value['raw_references'] += 1; write(clone / 'INDEX-AUDIT.json', value)
        elif kind == 'erased-ledger-disagreement':
            path = clone / 'REFERENCE-LEDGER.jsonl'; rows = [json.loads(line) for line in path.read_text().splitlines()]
            assert any(r['index_id'] == 'shortAtK' and r['original_runtime_decoy'] for r in rows)
            for row in rows:
                if row['index_id'] == 'shortAtK':
                    row['original_runtime_decoy'] = False
            path.write_text(''.join(json.dumps(r, sort_keys=True, separators=(',', ':')) + '\n' for r in rows), encoding='utf-8', newline='\n')
        elif kind == 'live-worker':
            value = json.loads((clone / 'index-process/process.json').read_bytes()); value['owned_live_after_cleanup'] = [12345]; write(clone / 'index-process/process.json', value)
        elif kind == 'failed-marker':
            write(clone / 'FAILURE.json', dict(status='FAIL2', error='controlled late failure'))
        else:
            (clone / 'unlisted-empty-directory').mkdir()
        manifest = json.loads((clone / 'manifest.json').read_bytes())
        for name, row in manifest['files'].items():
            if row['kind'] == 'file':
                row.update(pin(clone / name))
        write(clone / 'manifest.json', manifest)
        complete = json.loads((clone / 'COMPLETED.json').read_bytes()); complete['manifest'] = pin(clone / 'manifest.json'); write(clone / 'COMPLETED.json', complete)
        result = java('negative-' + kind, platforms[0], 'verify', str(clone), required=False)
        assert result['exit_code'] != 0
        negatives.append(dict(kind=kind, process=result, rejected=True))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-complete-original-Salmon-index-workflow-and-portable-contracts', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          focused_tests_per_jvm=344, matrix=matrix, builds=runs, negative_controls=negatives, source_pins=sources, unchanged_parent_classes=len(legacy), added_classes=added,
          canonical_executable_unchanged=canonical, scope='Five supervised original index builds over the same controlled raw population, not independent biological datasets. '
          'All raw reference records and original retained/source-decoy populations are preserved. Index-only completion permits a disagreement ledger; it does not admit expression quantification. '
          'Portable Windows checks open captured members only. Resealed inconsistent negative controls check contracts, not execution authenticity.',
          public_quantification_adapter_available=False, user_expression_analysis_admitted=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
