# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Probe original Salmon indexing edge semantics through native supervision and complete reference decoding."""
import argparse
import hashlib
import json
from pathlib import Path
import random
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def fixtures():
    rng = random.Random(2718281)
    def dna(n):
        result = ''.join(rng.choice('ACGT') for _ in range(n))
        assert not result.endswith('A' * 10)
        return result
    anchor, decoy = dna(160), dna(180)
    records = [('shortBefore', 'GTCCGTA'), ('longA', anchor), ('longDuplicate', anchor), ('lowercaseCopy', anchor.lower()),
               ('polyATail', dna(70) + 'A' * 12), ('clippedShort', 'TCGTCGA' + 'A' * 10), ('allA', 'A' * 25),
               ('ambiguous', 'ACGTNURYKMSWBDHVNX' + dna(80)), ('shortAtK', dna(15)),
               ('decoyLong', decoy), ('decoyDuplicate', decoy), ('decoyShort', 'GGTCCGATC')]
    raw = ''.join('>' + name + '\n' + sequence + '\n' for name, sequence in records)
    decoys = 'decoyLong\ndecoyDuplicate\ndecoyShort\n'
    reads = ''.join(f'@fixture-read-{i}\n{anchor[20:60]}\n+\n' + 'I' * 40 + '\n' for i in range(160))
    cases = []
    for keep in [False, True]:
        removed = {'allA', 'decoyDuplicate'} | (set() if keep else {'longDuplicate'})
        cases.append(dict(id='mixed-' + ('keep' if keep else 'default'), fasta=raw, decoys=decoys, keep_duplicates=keep, gencode=False,
                          expected_failure=False, expected_full_names=[n for n, _ in records if n not in removed], reads=reads))
        if keep:
            cases[-1]['expected_full_names_by_edition'] = {v: [n for n, _ in records if n != 'allA'] for v in ['1.0.0', '1.1.0']}
    gencode = [('ENST1.1|GENE1|one description', anchor), ('ENST2.2|GENE2|two description', anchor), ('ENST3.3|GENE3|short', 'GTCTGCA'), ('chrD description', decoy)]
    cases.append(dict(id='gencode-keep', fasta=''.join('>' + n + '\n' + s + '\n' for n, s in gencode), decoys='chrD\n', keep_duplicates=True, gencode=True,
                      expected_failure=False, expected_full_names=['ENST1.1', 'ENST2.2', 'ENST3.3', 'chrD'], reads=reads))
    amb = '>ambiguousA\n' + 'ACGTNURYKMSWBDHVNX' + dna(100) + '\n>anchor\n' + anchor + '\n'
    for label in ['a', 'b']:
        cases.append(dict(id='ambiguous-repeat-' + label, fasta=amb, decoys=None, keep_duplicates=True, gencode=False, expected_failure=False,
                          expected_full_names=['ambiguousA', 'anchor'], reads=None))
    cases.append(dict(id='decoy-before-transcript', fasta='>decoyLong\n' + decoy + '\n>longA\n' + anchor + '\n', decoys='decoyLong\n',
                      keep_duplicates=True, gencode=False, expected_failure=True, expected_full_names=None, reads=None))
    cases.append(dict(id='unknown-decoy', fasta='>longA\n' + anchor + '\n', decoys='absentDecoy\n', keep_duplicates=True, gencode=False,
                      expected_failure=True, expected_full_names=['longA'], observe_missing_decoy_old_editions=True, reads=None))
    return cases


def main():
    if not __debug__:
        raise RuntimeError('Never disable source edge-control checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--versions', nargs='+', choices=['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'], default=['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'])
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    out.mkdir()
    artifact = pin(jar)
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    prior = json.loads((BASE / 'index-reference-development-1/matrix-1/COMPLETED.json').read_bytes())
    parent = ROOT / prior['artifact']['path']
    assert pin(parent) == {k: prior['artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        legacy = [name for name in old.namelist() if name.endswith('.class')]
        assert all(current.read(name) == old.read(name) for name in legacy)
    source_names = [Path(__file__).relative_to(ROOT).as_posix(), 'src/main/java/org/jlinalg/cli/SalmonMetadata.java',
                    'src/main/java/org/jlinalg/sequence/SalmonDecoyAssignment.java', 'src/main/java/org/jlinalg/cli/NextflowParameters.java']
    sources = dict(prior['source_pins'])
    sources.update({name: pin(ROOT / name) for name in source_names})
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    cases = fixtures()
    write(out / 'fixture-spec.json', dict(status='synthetic-original-index-probes-not-user-analysis', cases=cases))
    fixture_pin = pin(out / 'fixture-spec.json')
    classes = out / 'classes'
    classes.mkdir()
    runner = out / 'SalmonIndexControls.java'
    runner.write_text('''package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.*;
public class SalmonIndexControls {
 static void write(Path p,Object v)throws IOException{Files.writeString(p,NextflowJson.json(v)+"\\n",StandardOpenOption.CREATE_NEW);}
 static Map<String,Object> fingerprint(NextflowFiles.Pin p){return Map.of("bytes",p.bytes(),"sha256",p.sha256());}
 static List<String> names(SalmonIndexReferences.Summary r){return r.references().stream().map(SalmonIndexReferences.Reference::name).toList();}
 static Map<String,Object> reference(SalmonIndexReferences.Reference r){return Map.of("ordinal",r.ordinal(),"name",r.name(),"original_length",r.originalLength(),"normalized_length",r.normalizedLength(),"contig_reference_id",r.contigReferenceId());}
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"org.jlinalg.cli.SalmonRuntime","org.jlinalg.sequence.SalmonIndexReferences","org.jlinalg.sequence.SalmonQuantification"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  String version=args[1];Path home=Path.of(args[2]),setup=Path.of(args[3]),root=Path.of(args[4]),specification=Path.of(args[5]);Files.createDirectory(root);
  var limits=SalmonRuntime.Limits.defaults();System.err.println("RUNTIME_HEALTH_START "+version);var admitted=SalmonRuntime.admit("linux-x86_64-salmon-"+version,home,setup,root.resolve("host"),limits,()->{});System.err.println("RUNTIME_HEALTH_COMPLETE "+version);
  var specPin=NextflowFiles.pin(specification,8388608,()->{});var spec=SalmonHealth.json(specification);var results=new ArrayList<Object>();
  for(Object item:NextflowFiles.list(spec.get("cases"))) {
   var test=NextflowFiles.object(item);String id=NextflowFiles.text(test.get("id"));if(!id.matches("[a-z0-9-]{1,80}"))throw new IOException("Unsafe controlled case ID");
   System.err.println("INDEX_CASE_START "+version+" "+id);Path area=root.resolve(id),input=area.resolve("inputs"),index=area.resolve("index");Files.createDirectory(area);Files.createDirectory(input);Files.createDirectory(index);
   Path fasta=input.resolve("reference.fa");Files.writeString(fasta,NextflowFiles.text(test.get("fasta")),StandardOpenOption.CREATE_NEW);
   var command=new ArrayList<>(List.of(home.resolve("bin/salmon").toString(),"--no-version-check","index","-t",fasta.toString(),"-i",index.toString(),"-k","15","-p","2"));
   if(Boolean.TRUE.equals(test.get("keep_duplicates")))command.add("--keepDuplicates");if(Boolean.TRUE.equals(test.get("gencode")))command.add("--gencode");
   if(!version.equals("1.0.0"))command.add("--keepFixedFasta");
   if(test.get("decoys")!=null){Path p=input.resolve("decoys.txt");Files.writeString(p,NextflowFiles.text(test.get("decoys")),StandardOpenOption.CREATE_NEW);command.add("-d");command.add(p.toString());}
   var inputTree=SalmonRuntime.tree(input,limits.process(),()->{});boolean observeMissing=Boolean.TRUE.equals(test.get("observe_missing_decoy_old_editions"))&&Set.of("1.0.0","1.1.0").contains(version);boolean expectedFailure=Boolean.TRUE.equals(test.get("expected_failure"))&&!observeMissing;IOException processFailure=null;
   try{RsemProcess.executeOwned(command,admitted.environment(),area.resolve("index-process"),limits.process(),()->{admitted.verify(()->{});if(!inputTree.equals(SalmonRuntime.tree(input,limits.process(),()->{})))throw new IOException("Protected original edge input changed");},()->{},List.of(input,index,admitted.work()));}
   catch(IOException failed){processFailure=failed;}
   if(!inputTree.equals(SalmonRuntime.tree(input,limits.process(),()->{})))throw new IOException("Original edge input changed");admitted.verify(()->{});
   var indexLedger=SalmonHealth.json(area.resolve("index-process/process.json"));long exit=((Number)indexLedger.get("exit_code")).longValue();
   System.err.println("INDEX_CASE_EXIT "+version+" "+id+" "+exit);var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("command",command);row.put("index_exit",exit);row.put("fixed_fasta_retention_option_available",!version.equals("1.0.0"));row.put("user_analysis_admitted",false);
   if(expectedFailure||(observeMissing&&processFailure!=null)){if(processFailure==null||exit==0)throw new AssertionError("Original invalid decoy case did not fail");row.put("status","original-index-rejected-no-result");row.put("error",processFailure.toString());}
   else {
    if(processFailure!=null)throw processFailure;var positions=new ArrayList<Object>();SalmonIndexReferences.Summary decoded;
    try(var table=Files.newInputStream(index.resolve("ctable.bin"));var complete=Files.newInputStream(index.resolve("complete_ref_lens.bin"));var normalized=Files.newInputStream(index.resolve("reflengths.bin"))){decoded=SalmonIndexReferences.read(table,complete,normalized,new SalmonIndexReferences.Limits(1000,100000,8192,1000000,33554432,16777216),(o,c,f,n,p,fw)->positions.add(Map.of("ordinal",o,"contig_reference",c,"full_reference",f,"name",n,"start",p,"forward",fw)),()->{});}
    Object expectedNames=test.get("expected_full_names");if(test.get("expected_full_names_by_edition") instanceof Map<?,?> editions&&editions.containsKey(version))expectedNames=editions.get(version);
    if(!names(decoded).equals(expectedNames))throw new AssertionError("Original index population differs from controlled input expectations: "+id+" "+names(decoded));
    row.put("status","complete-original-index-and-native-reference-table-observed");row.put("references",decoded.references().stream().map(SalmonIndexControls::reference).toList());row.put("positions",positions);row.put("index_info",SalmonMetadata.read(index.resolve("info.json"),8388608,100000));
    if(observeMissing){row.put("status","original-index-accepted-unmatched-decoy-declaration-no-user-result");row.put("requested_raw_decoy_coverage",false);row.put("required_action","Reject the unmatched declared name before analysis; an original zero exit does not establish decoy coverage.");}
    row.put("ctable",fingerprint(NextflowFiles.pin(index.resolve("ctable.bin"),33554432,()->{})));row.put("refseq",fingerprint(NextflowFiles.pin(index.resolve("refseq.bin"),33554432,()->{})));
    Path duplicates=index.resolve("duplicate_clusters.tsv");row.put("duplicate_clusters",Files.exists(duplicates)?SalmonHealth.utf8(duplicates):null);
    if(test.get("reads")!=null) {
     Path reads=input.resolve("reads.fastq"),quant=area.resolve("quant");Files.writeString(reads,NextflowFiles.text(test.get("reads")),StandardOpenOption.CREATE_NEW);Files.createDirectory(quant);
     var quantInputs=SalmonRuntime.tree(input,limits.process(),()->{});var indexTree=SalmonRuntime.tree(index,limits.process(),()->{});
     var quantCommand=List.of(home.resolve("bin/salmon").toString(),"--no-version-check","quant","-i",index.toString(),"-l","U","-r",reads.toString(),"--fldMean","40","--fldSD","5","--useEM","--minAssignedFrags","1","-p","2","-o",quant.toString());
     RsemProcess.executeOwned(quantCommand,admitted.environment(),area.resolve("quant-process"),limits.process(),()->{admitted.verify(()->{});if(!quantInputs.equals(SalmonRuntime.tree(input,limits.process(),()->{}))||!indexTree.equals(SalmonRuntime.tree(index,limits.process(),()->{})))throw new IOException("Protected index/reads changed during controlled quantification");},()->{},List.of(input,index,quant,admitted.work()));
     Set<String> decoys=test.get("decoys")==null?Set.of():Set.copyOf(NextflowFiles.text(test.get("decoys")).lines().toList());var targets=decoded.references().stream().filter(r->!decoys.contains(r.name())).map(r->new SalmonQuantification.Target(r.name(),r.originalLength())).toList();
     var retainedDecoys=new HashSet<>(decoys);retainedDecoys.retainAll(names(decoded));var info=SalmonMetadata.read(index.resolve("info.json"),8388608,100000);
     var ledger=SalmonDecoyAssignment.assign(decoded,(int)SalmonMetadata.integer(info.get("k"),31),SalmonMetadata.integer(info.get("num_decoys"),1000),SalmonMetadata.unsigned(info.get("first_decoy_index")),retainedDecoys,()->{});
     row.put("source_declared_decoy_disagreements",ledger.disagreements());row.put("original_runtime_num_decoys",ledger.originalRuntimeDecoys());row.put("declared_and_runtime_populations_agree",ledger.matchesDeclaredPopulation());
     row.put("complete_decoy_assignments",ledger.assignments().stream().map(r->Map.of("name",r.reference().name(),"short_reference",r.shortReference(),"declared_decoy",r.declaredDecoy(),"original_runtime_decoy",r.originalRuntimeDecoy())).toList());
     row.put("quant_command",quantCommand);row.put("quant_meta",SalmonHealth.json(quant.resolve("aux_info/meta_info.json")));row.put("expected_nondecoy_names",targets.stream().map(SalmonQuantification.Target::name).toList());
     var sourceTable=SalmonQuantification.read(new StringReader(SalmonHealth.utf8(quant.resolve("quant.sf"))),ledger.originalRuntimeNondecoys(),3,8388608,8192,()->{});long sourceAssigned=((Number)SalmonHealth.json(quant.resolve("lib_format_counts.json")).get("num_assigned_fragments")).longValue();SalmonQuantification.audit(sourceTable,sourceAssigned,()->{});
     if(((Number)SalmonHealth.json(quant.resolve("aux_info/meta_info.json")).get("num_decoy_targets")).longValue()!=ledger.originalRuntimeDecoys())throw new IOException("Original runtime decoy counter does not match the source ledger");
     row.put("source_as_run_audit","complete-source-compatible-population-and-arithmetic-pass-requested-coverage-separate");row.put("source_as_run_rows",sourceTable.rows().size());
     try {var table=SalmonQuantification.read(new StringReader(SalmonHealth.utf8(quant.resolve("quant.sf"))),targets,3,8388608,8192,()->{});long assigned=((Number)SalmonHealth.json(quant.resolve("lib_format_counts.json")).get("num_assigned_fragments")).longValue();var arithmetic=SalmonQuantification.audit(table,assigned,()->{});row.put("quant_audit","complete-independent-population-and-printed-arithmetic-pass");row.put("quant_rows",table.rows().size());row.put("assigned_fragments",assigned);row.put("printed_count_sum",arithmetic.printedCountSum());}
     catch(IOException discrepancy){row.put("quant_audit","source-boundary-discrepancy-retained-no-user-result");row.put("quant_audit_error",discrepancy.toString());}
    }
   }
   write(area.resolve("OBSERVATION.json"),row);results.add(row);System.err.println("INDEX_CASE_RECORDED "+version+" "+id);
  }
  admitted.verify(()->{});if(!specPin.equals(NextflowFiles.pin(specification,8388608,()->{})))throw new IOException("Control specification changed");
  write(root.resolve("OBSERVED.json"),Map.of("status","complete-original-index-edge-observations-not-user-analysis","version",version,"cases",results,"user_analysis_admitted",false));
  System.out.println(NextflowJson.json(Map.of("version",version,"cases",results.size(),"complete_native_files",SalmonRuntime.tree(root,limits.process(),()->{}),"user_analysis_admitted",false)));
 }
}
''', encoding='utf-8', newline='\n')

    def command(name, argv, timeout=2400, required=True):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout)
        receipt = dict(command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        if required:
            assert result.returncode == 0, (name, result.returncode)
        return receipt

    command('compile', [JAVA21 / 'javac.exe', '--release', '17', '-Xlint:all', '-Werror', '-cp', jar, '-d', classes, runner])
    remote = subprocess.check_output(['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', 'import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-index-edges-",dir="/var/tmp"))']).decode().strip()
    assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-index-edges-[A-Za-z0-9_-]+', remote)
    write(out / 'OWNER.json', dict(owned_linux_root=remote))
    setups = json.loads((BASE / 'managed-setup-development-2/COMPLETED.json').read_bytes())
    homes = {r['version']: r for r in setups['rechecks']}
    runs = []
    for version in args.versions:
        assert pin(out / 'fixture-spec.json') == fixture_pin
        home = homes[version]
        process = command(version, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', linux(classes) + ':' + linux(jar),
                                   'org.jlinalg.cli.SalmonIndexControls', linux(jar), version, home['home'], linux(ROOT / home['report']), remote + '/' + version, linux(out / 'fixture-spec.json')], required=False)
        destination = out / (version if process['exit_code'] == 0 else version + '-FAILED')
        copy_code = 'from pathlib import Path;import sys,shutil;source=Path(sys.argv[1]);target=Path(sys.argv[2]);assert source.is_dir() and not target.exists();shutil.copytree(source,target,copy_function=shutil.copyfile)'
        command(version + '-copy', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', copy_code, remote + '/' + version, linux(destination)])
        if process['exit_code'] != 0:
            write(out / (version + '-FAILED.json'), dict(status='original-index-edge-failure-retained-no-user-result', files={p.relative_to(destination).as_posix(): pin(p) for p in destination.rglob('*') if p.is_file()}, process=process))
            raise AssertionError((version, process['exit_code'], 'failed outputs retained'))
        summary = json.loads((out / (version + '.stdout')).read_bytes())
        assert set(summary['complete_native_files']) == {p.relative_to(destination).as_posix() for p in destination.rglob('*')}
        for name, value in summary['complete_native_files'].items():
            if value['kind'] == 'file':
                assert pin(destination / name) == {k: value[k] for k in ['bytes', 'sha256']}
        observed = json.loads((destination / 'OBSERVED.json').read_bytes())
        assert len(observed['cases']) == 7 and sum(c['index_exit'] == 0 for c in observed['cases']) in ({5, 6} if version in ['1.0.0', '1.1.0'] else {5})
        discrepancies = [c['id'] for c in observed['cases'] if c.get('quant_audit') == 'source-boundary-discrepancy-retained-no-user-result']
        print(version + ':7 original index edge observations retained; quantification discrepancies=' + str(discrepancies), flush=True)
        runs.append(dict(version=version, process=process, observation=pin(destination / 'OBSERVED.json'), quantification_discrepancies=discrepancies))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    assert pin(out / 'fixture-spec.json') == fixture_pin
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='completed-original-Salmon-index-edge-observations-with-explicit-discrepancy-status', selected_editions=args.versions, runs=runs,
          source_pins=sources, artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact), fixture=fixture_pin,
          no_quantification_discrepancies=all(not r['quantification_discrepancies'] for r in runs),
          scope='Seven original indexing protocols per edition: short/clipped/all-A/ambiguous/duplicate references, GENCODE and decoy failure/retention boundaries. '
                'Three successful index populations are followed by a controlled single-end original quantification and independent requested nondecoy population audit. '
                'Any requested-population discrepancy remains unadmitted; the exact complete source-as-run view is audited separately. '
                'Two other original health fits per edition reuse a synthetic population and are not independent biological datasets.',
          public_quantification_adapter_available=False, user_analysis_admitted=False, Salmon_catalog_status='missing', canonical_executable_unchanged=canonical))


if __name__ == '__main__':
    main()
