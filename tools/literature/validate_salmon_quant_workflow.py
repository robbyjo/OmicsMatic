# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete actual-JAR original Salmon bulk workflows and preserve every rejected attempt."""
import argparse
import concurrent.futures
import copy
import json
import os
from pathlib import Path
import re
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
        raise RuntimeError('Never disable complete original-workflow checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--versions', nargs='+', choices=['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'], default=['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'])
    parser.add_argument('--matrix-proof', type=Path)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists() and len(set(args.versions)) == len(args.versions)
    out.mkdir()
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    prior = json.loads((BASE / 'quant-workflow-development-1/matrix-1/COMPLETED.json').read_bytes())
    parent = ROOT / prior['artifact']['path']
    assert pin(parent) == {k: prior['artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [n for n in old.namelist() if n.endswith('.class')]
        assert all(current.read(n) == old.read(n) for n in legacy)
        added = [n for n in current.namelist() if n.endswith('.class') and n not in legacy]
        assert len(added) == 4 and all(n.startswith(tuple('org/jlinalg/cli/' + name for name in ['SalmonQuantWorkflow', 'SalmonToolsCli', 'SalmonSetupExport'])) for n in added)
    sources = dict(prior['source_pins'])
    for name in ['src/main/java/org/jlinalg/cli/SalmonQuantWorkflow.java', 'src/test/java/org/jlinalg/cli/SalmonQuantWorkflowBoundaryTest.java',
                 'src/main/java/org/jlinalg/cli/SalmonToolsCli.java', 'src/main/java/org/jlinalg/cli/SalmonSetupExport.java',
                 'src/test/java/org/jlinalg/cli/SalmonToolsCliTest.java', 'src/test/java/org/jlinalg/cli/SalmonSetupExportTest.java', Path(__file__).relative_to(ROOT).as_posix()]:
        sources[name] = pin(ROOT / name)
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    classes = out / 'classes'
    classes.mkdir()
    tests = sorted(ROOT / name for name in sources if name.startswith('src/test/java/') and Path(name).name.startswith('Salmon') and name.endswith('Test.java'))
    assert len(tests) == 18
    selectors = ','.join('selectClass("' + t.relative_to(ROOT / 'src/test/java').as_posix()[:-5].replace('/', '.') + '")' for t in tests)
    runner = out / 'SalmonQuantWorkflowChecks.java'
    runner.write_text('''package org.jlinalg.cli;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.CancellationException;
import htsjdk.samtools.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonQuantWorkflowChecks {
 static void write(Path p,Object v)throws IOException{Files.writeString(p,NextflowJson.json(v)+"\\n",StandardOpenOption.CREATE_NEW);}
 static Map<String,Object> receipt(String id,SalmonQuantWorkflow.Result r){return Map.of("id",id,"input",r.inputs().document(),"output",r.output().document(),"manifest",SalmonIndexWorkflow.fingerprint(r.manifest()),"declared_retained_population_admitted",r.inputs().declaredRetainedPopulationMatchesSource());}
 static void reject(String id,Path recipe,SalmonRuntime.Admitted admitted,Path root,int code,Runnable check)throws Exception{
  try{SalmonQuantWorkflow.startWithAdmitted(recipe,admitted,root,check);throw new AssertionError("Rejected attempt returned a result");}
  catch(IOException failed){if((failed instanceof SalmonRuntime.Unavailable?3:2)!=code||Files.exists(root.resolve("COMPLETED.json")))throw new AssertionError("Wrong rejection",failed);}
  var f=NextflowFiles.object(NextflowFiles.json(root.resolve("FAILURE.json"),1048576));if(NextflowFiles.integer(f.get("exit_code"),2,3)!=code)throw new AssertionError("Wrong failure diagnostic");
  System.err.println("FIT_REJECTED "+id+" "+code);
 }
 public static void main(String[] args)throws Exception{
  for(String n:new String[]{"SalmonQuantWorkflow","SalmonQuantInputs","SalmonQuantAudit","SalmonIndexWorkflow","SalmonRuntime","SalmonToolsCli","SalmonSetupExport"})if(!Class.forName("org.jlinalg.cli."+n).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  if(args[1].equals("tests")){
   var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
   if(s.getTestsFoundCount()!=446||s.getTestsSucceededCount()!=446||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete workflow cases");System.out.println("COMPLETE_SALMON_CASES 446 NO_SKIPS");return;
  }
  if(args[1].equals("verify")){if(SalmonToolsCli.run(new String[]{"--verify",args[2]},System.out,System.err)!=0)throw new AssertionError("Public portable verification failed");return;}
  if(!args[1].equals("native"))throw new AssertionError("Unknown operation");
  Path specification=Path.of(args[2]),root=Path.of(args[3]);var specPin=NextflowFiles.pin(specification,8388608,()->{});var spec=NextflowFiles.object(NextflowFiles.json(specification,8388608));
  Files.createDirectory(root);Files.createDirectory(root.resolve("recipes"));String version=NextflowFiles.text(spec.get("version")),profile="linux-x86_64-salmon-"+version;Path home=Path.of(NextflowFiles.text(spec.get("home"))),setup=Path.of(NextflowFiles.text(spec.get("setup")));
  Path indexRecipe=root.resolve("recipes/index.json");write(indexRecipe,spec.get("index_recipe"));System.err.println("CLEAN_INDEX_START "+version);
  var index=SalmonIndexWorkflow.start(indexRecipe,home,setup,root.resolve("clean-index"),()->{});if(!index.audit().ledger().decoys().matchesDeclaredPopulation())throw new AssertionError("Clean declared population mismatch");System.err.println("CLEAN_INDEX_COMPLETE "+version);
  System.err.println("FIT_RUNTIME_START "+version);var admitted=SalmonRuntime.admit(profile,home,setup,root.resolve("admission"),SalmonRuntime.Limits.defaults(),()->{});System.err.println("FIT_RUNTIME_COMPLETE "+version);
  Path conversions=Files.createDirectory(root.resolve("input-conversion")),bam=conversions.resolve("alignments.bam"),sam=Path.of(NextflowFiles.text(spec.get("original_sam")));
  var samPin=NextflowFiles.pin(sam,16777216,()->{});try(var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(sam);var writer=new SAMFileWriterFactory().makeBAMWriter(reader.getFileHeader(),true,bam);var it=reader.iterator()){while(it.hasNext())writer.addAlignment(it.next());}
  var fits=new ArrayList<Object>();
  for(Object value:NextflowFiles.list(spec.get("fits"))){var row=NextflowFiles.object(value);String id=NextflowFiles.text(row.get("id"));if(!id.matches("[a-z0-9-]{1,80}"))throw new AssertionError("Unsafe owned ID");
   Object recipe=row.get("recipe");if(id.equals("alignment-bam-vb-5")){var r=new LinkedHashMap<Object,Object>(NextflowFiles.object(recipe));r.put("alignments",Map.of("path",bam.toString(),"format","BAM","layout","PAIRED"));recipe=r;}
   Path path=root.resolve("recipes/"+id+".json");write(path,recipe);System.err.println("FIT_START "+version+" "+id);
   var result=SalmonQuantWorkflow.startWithAdmitted(path,admitted,root.resolve(id),()->{});SalmonQuantWorkflow.verify(result.root(),()->{});
   if(result.output().table().rows().size()!=NextflowFiles.integer(row.get("targets"),1,1000)||result.inputs().declaredRetainedPopulationMatchesSource()!=Boolean.TRUE.equals(row.get("declared")))throw new AssertionError("Incomplete fit population");
   fits.add(receipt(id,result));write(root.resolve(id+"-OBSERVED.json"),receipt(id,result));System.err.println("FIT_COMPLETE "+version+" "+id);
  }
  Path strict=root.resolve("recipes/strict-declared.json");write(strict,spec.get("strict_recipe"));reject("strict-declared",strict,admitted,root.resolve("strict-declared"),3,()->{});if(Files.exists(root.resolve("strict-declared/quant-process")))throw new AssertionError("Skipped population launched a user fit");
  Path insufficient=root.resolve("recipes/insufficient-assigned.json");write(insufficient,spec.get("insufficient_recipe"));reject("insufficient-assigned",insufficient,admitted,root.resolve("insufficient-assigned"),2,()->{});
  var process=NextflowFiles.object(NextflowFiles.json(root.resolve("insufficient-assigned/quant-process/process.json"),1048576));if(NextflowFiles.integer(process.get("exit_code"),0,255)==0||!NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty())throw new AssertionError("Expected original failure did not terminate");
  Map<String,Object> cliFit=Map.of();
  if(version.equals("1.9.0")){
   Path capacity=root.resolve("recipes/bootstrap-capacity.json");write(capacity,spec.get("capacity_recipe"));reject("bootstrap-capacity",capacity,admitted,root.resolve("bootstrap-capacity"),2,()->{});if(Files.exists(root.resolve("bootstrap-capacity/quant-process")))throw new AssertionError("Capacity failure launched a fit");
   Path late=root.resolve("recipes/late-finalization.json"),lateRoot=root.resolve("late-finalization");write(late,spec.get("late_recipe"));reject("late-finalization",late,admitted,lateRoot,2,()->{if(Files.exists(lateRoot.resolve("COMPLETED.json")))throw new CancellationException("Controlled post-completion finalization cancellation");});
   if(!Files.exists(lateRoot.resolve("OUTPUT-AUDIT.json"))||!Files.exists(lateRoot.resolve("WARNINGS-before-failure.md")))throw new AssertionError("Late original output/warnings lost");
   Path unexpected=root.resolve("recipes/unexpected-finalization.json"),unexpectedRoot=root.resolve("unexpected-finalization");write(unexpected,spec.get("late_recipe"));reject("unexpected-finalization",unexpected,admitted,unexpectedRoot,2,()->{if(Files.exists(unexpectedRoot.resolve("COMPLETED.json")))throw new AssertionError("Controlled unexpected post-completion guard error");});
   if(!Files.exists(unexpectedRoot.resolve("OUTPUT-AUDIT.json"))||!Files.exists(unexpectedRoot.resolve("WARNINGS-before-failure.md")))throw new AssertionError("Unexpected-error prefix lost");
   System.err.println("PUBLIC_CLI_START "+version);Path cliRoot=root.resolve("cli-alignment");var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();int code;
   try(var o=new PrintStream(stdout,true,java.nio.charset.StandardCharsets.UTF_8);var e=new PrintStream(stderr,true,java.nio.charset.StandardCharsets.UTF_8)){code=SalmonToolsCli.run(new String[]{"--recipe",root.resolve("recipes/alignment-sam-em-3.json").toString(),"--home",home.toString(),"--setup-report",setup.toString(),"--out",cliRoot.toString()},o,e);}
   Files.write(root.resolve("cli.stdout"),stdout.toByteArray(),StandardOpenOption.CREATE_NEW);Files.write(root.resolve("cli.stderr"),stderr.toByteArray(),StandardOpenOption.CREATE_NEW);if(code!=0)throw new AssertionError("Public original CLI failed, exit "+code);
   cliFit=receipt("cli-alignment",SalmonQuantWorkflow.verify(cliRoot,()->{}));System.err.println("PUBLIC_CLI_COMPLETE "+version);
  }
  admitted.verify(()->{});if(!samPin.equals(NextflowFiles.pin(sam,16777216,()->{}))||!specPin.equals(NextflowFiles.pin(specification,8388608,()->{})))throw new AssertionError("Original protocol input/specification changed");
  write(root.resolve("OBSERVED.json"),Map.of("status","complete-controlled-original-bulk-workflows-not-biological-validation","version",version,"fits",fits,"cli_fit",cliFit,"strict_population_skipped",true,"original_insufficient_mass_rejected",true));
  System.out.println(NextflowJson.json(Map.of("version",version,"fits",fits,"cli_fit",cliFit,"complete_native_files",SalmonIndexWorkflow.inventory(root,index.recipe().limits().process(),()->{}))));
 }
}
'''.replace('SELECTORS', selectors), encoding='utf-8', newline='\n')

    def command(name, argv, required=True, timeout=3600):
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
        return command(name, [*binary, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', separator.join(map(convert, paths)), 'org.jlinalg.cli.SalmonQuantWorkflowChecks', convert(jar), operation, *values], required=required)

    if args.matrix_proof:
        matrix_proof = args.matrix_proof.resolve()
        assert matrix_proof.is_relative_to(BASE.resolve())
        saved = json.loads(matrix_proof.read_bytes())
        assert saved['artifact'] == dict(path=jar.relative_to(ROOT).as_posix(), **artifact) and saved['source_pins'] == sources and saved['focused_tests_per_jvm'] == 446
        matrix = dict(reused_complete_matrix=matrix_proof.relative_to(ROOT).as_posix(), proof=pin(matrix_proof), runs=saved['matrix'])
    else:
        def focused(platform):
            result = java(platform[0] + '-tests', platform, 'tests')
            print(platform[0] + ':446 actual-JAR cases passed', flush=True)
            return result
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            matrix = list(pool.map(focused, platforms))
    remote = subprocess.check_output(['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', 'import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-bulk-workflows-",dir="/var/tmp"))']).decode().strip()
    assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-bulk-workflows-[A-Za-z0-9_-]+', remote)
    write(out / 'OWNER.json', dict(owned_linux_root=remote))
    health = ROOT / 'src/main/resources/org/jlinalg/omics/salmon-runtime-setup/health-inputs'
    original_sam = BASE / 'reference-protocols-3/inputs/alignments.sam'
    setups = {r['version']: r for r in json.loads((BASE / 'managed-setup-development-2/COMPLETED.json').read_bytes())['rechecks']}
    input_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [original_sam, *health.glob('*')] if p.is_file()}
    runs = []
    for version in args.versions:
        area = remote + '/' + version
        limits = dict(wall_seconds=900, cpu_seconds=600, address_space_bytes=8589934592, file_bytes=536870912, workspace_bytes=2147483648, files=16384, threads=256, sampled_rss_bytes=6442450944)
        index_recipe = dict(schema_version=1, profile='linux-x86_64-salmon-' + version, fasta=linux(health / 'gentrome.fa'), encoding='PLAIN_UTF8', headers='SOURCE_WHITESPACE_TOKEN', decoys=linux(health / 'decoys.txt'), k=31,
                            threads=2, keep_duplicates=True, keep_fixed_fasta=version != '1.0.0', poly_a='SOURCE_DEFAULT', limits=limits)

        def recipe(mode, optimizer='EM', bootstraps=0, library='ISR', seq=False, gc=False, pos=False):
            alignment = mode == 'TRANSCRIPTOME_ALIGNMENTS'
            first = linux(health / 'reads_1.fastq')
            raw = dict(schema_version=1, profile='linux-x86_64-salmon-' + version, mode=mode, index_bundle=None if alignment else area + '/clean-index', reads=None, alignments=None, reference=None,
                       library_type=library, optimizer=optimizer, sequence_bias=seq, gc_bias=gc, position_bias=pos, bootstraps=bootstraps, minimum_assigned_fragments=10, fragment_prior=None, threads=2,
                       population='REQUIRE_DECLARED_NONDECOYS', limits=copy.deepcopy(limits))
            if alignment:
                raw['alignments'] = dict(path=linux(original_sam), format='SAM', layout='PAIRED')
                raw['reference'] = dict(path=linux(health / 'transcripts.fa'), encoding='PLAIN_UTF8', headers='SOURCE_WHITESPACE_TOKEN')
            else:
                raw['reads'] = dict(first=first, second=linux(health / 'reads_2.fastq') if mode == 'PAIRED_FASTQ' else None, alphabet='IUPAC_DNA_RNA_OR_X', pairing='TERMINAL_MATE_SUFFIX')
                if mode == 'SINGLE_FASTQ':
                    raw['fragment_prior'] = dict(mean=250, sd=25)
            return raw

        fits = []
        for name, raw in [('paired-em-bias-50', recipe('PAIRED_FASTQ', bootstraps=50, library='A', seq=True, gc=True, pos=True)),
                          ('paired-vb-bias-100', recipe('PAIRED_FASTQ', optimizer='VARIATIONAL_BAYES', bootstraps=100, seq=True, gc=True, pos=True)),
                          ('single-em-5', recipe('SINGLE_FASTQ', bootstraps=5, library='SR', seq=True)), ('paired-vb-0', recipe('PAIRED_FASTQ', optimizer='VARIATIONAL_BAYES', library='IU')),
                          ('alignment-sam-em-3', recipe('TRANSCRIPTOME_ALIGNMENTS', bootstraps=3)), ('alignment-bam-vb-5', recipe('TRANSCRIPTOME_ALIGNMENTS', optimizer='VARIATIONAL_BAYES', bootstraps=5))]:
            fits.append(dict(id=name, recipe=raw, targets=40, declared=True))
        mixed_index = BASE / 'index-workflow-development-1/control-1' / version
        mixed_reads = BASE / 'index-edge-controls-3' / version / 'mixed-keep/inputs/reads.fastq'
        input_pins[mixed_reads.relative_to(ROOT).as_posix()] = pin(mixed_reads)
        input_pins[(mixed_index / 'manifest.json').relative_to(ROOT).as_posix()] = pin(mixed_index / 'manifest.json')
        mixed = recipe('SINGLE_FASTQ', library='U'); mixed['index_bundle'] = linux(mixed_index); mixed['reads']['first'] = linux(mixed_reads); mixed['fragment_prior'] = dict(mean=40, sd=5); mixed['minimum_assigned_fragments'] = 1
        mixed['population'] = 'ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER'; fits.append(dict(id='explicit-source-single-em-0', recipe=mixed, targets=7, declared=False))
        strict = copy.deepcopy(mixed); strict['population'] = 'REQUIRE_DECLARED_NONDECOYS'
        insufficient = recipe('TRANSCRIPTOME_ALIGNMENTS'); insufficient['minimum_assigned_fragments'] = 1000000
        capacity = recipe('PAIRED_FASTQ', bootstraps=50); capacity['limits']['bootstrap_values'] = 1
        setup = setups[version]
        specification = dict(version=version, home=setup['home'], setup=linux(ROOT / setup['report']), original_sam=linux(original_sam), index_recipe=index_recipe, fits=fits,
                             strict_recipe=strict, insufficient_recipe=insufficient, capacity_recipe=capacity, late_recipe=recipe('TRANSCRIPTOME_ALIGNMENTS', bootstraps=3))
        spec_file = out / ('spec-' + version + '.json'); write(spec_file, specification); spec_pin = pin(spec_file)
        print(version + ':starting complete original index/fit controls', flush=True)
        process = java('native-' + version, platforms[2], 'native', linux(spec_file), area, required=False)
        destination = out / (version if process['exit_code'] == 0 else version + '-FAILED')
        copy_code = 'from pathlib import Path;import sys,shutil;s=Path(sys.argv[1]);t=Path(sys.argv[2]);assert s.is_dir() and not t.exists();shutil.copytree(s,t,copy_function=shutil.copyfile)'
        command('copy-' + version, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', copy_code, area, linux(destination)])
        if process['exit_code'] != 0:
            write(out / (version + '-FAILED.json'), dict(process=process, specification=spec_pin, files={p.relative_to(destination).as_posix(): pin(p) for p in destination.rglob('*') if p.is_file()}))
            raise AssertionError((version, 'complete failed attempt retained'))
        summary = json.loads((out / ('native-' + version + '.stdout')).read_bytes())
        assert set(summary['complete_native_files']) == {p.relative_to(destination).as_posix() for p in destination.rglob('*')}
        for name, row in summary['complete_native_files'].items():
            assert (destination / name).is_dir() == (row['kind'] == 'directory')
            if row['kind'] == 'file':
                assert pin(destination / name) == {k: row[k] for k in ['bytes', 'sha256']}
        assert len(summary['fits']) == 7 and sum(r['output']['complete_targets'] for r in summary['fits']) == 247 and sum(r['output']['complete_bootstrap_values'] for r in summary['fits']) == 6520
        portable = []
        for fit in fits:
            for platform in platforms[:2]:
                portable.append(java('portable-' + platform[0] + '-' + version + '-' + fit['id'], platform, 'verify', str(destination / fit['id'])))
        if version == '1.9.0':
            assert summary['cli_fit']['output']['complete_targets'] == 40 and summary['cli_fit']['output']['complete_bootstrap_values'] == 120
            for platform in platforms[:2]:
                portable.append(java('portable-' + platform[0] + '-' + version + '-cli-alignment', platform, 'verify', str(destination / 'cli-alignment')))
        assert pin(spec_file) == spec_pin
        runs.append(dict(version=version, native_process=process, fits=summary['fits'], cli_fit=summary['cli_fit'], portable=portable, strict_population_skipped=True, original_insufficient_mass_rejected=True,
                         bootstrap_capacity_rejected_before_fit=version == '1.9.0', late_finalization_revoked=version == '1.9.0', unexpected_finalization_error_revoked=version == '1.9.0', observed=pin(destination / 'OBSERVED.json')))
        print(version + ':seven original fits, complete source/declared scope and Windows portable replay passed; rejected attempts retained', flush=True)
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert all(pin(ROOT / name) == value for name, value in sources.items()) and all(pin(ROOT / name) == value for name, value in input_pins.items())
    write(out / 'COMPLETED.json', dict(status='passed-selected-original-Salmon-bulk-workflows-and-complete-portable-replay', selected_editions=args.versions, artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          focused_tests_per_jvm=446, matrix=matrix, runs=runs, source_pins=sources, input_pins=input_pins, unchanged_parent_classes=len(legacy), added_classes=added, canonical_executable_unchanged=canonical,
          admitted_controlled_fit_rows=247 * len(runs) + (40 if '1.9.0' in args.versions else 0), complete_bootstrap_values=6520 * len(runs) + (120 if '1.9.0' in args.versions else 0),
          scope='Fresh original dense index and bulk paired/single mapping, SAM and labeled BAM conversion workflows; one controlled synthetic population reused across editions. '
          'Explicit source-runtime views preserve their false declared-retained coverage and complete ledger. Strict mismatches skip before fitting; original insufficient-mass failures are unadmitted. '
          'Late cancellation, unexpected guard error and capacity controls are present for1.9.0, plus one actual public CLI alignment fit. Rejected partial output is excluded from admitted totals. '
          'Runtime health fits are separate and do not calibrate user inference. Complete actual-JAR Java APIs, standalone CLI and portable replay do not supply a public root CLI route, whole-paper reproduction or biological validation.',
          standalone_cli_control_present='1.9.0' in args.versions, public_root_cli_available=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
