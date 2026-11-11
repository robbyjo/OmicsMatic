# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare native nearest assignments with full original-package TxDb extraction controls."""
import argparse
import csv
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_mashr_native import JAVA21, linux

JAVA = r'''package org.jlinalg.cli;
import java.io.*;import java.nio.file.*;import java.util.*;
import org.jlinalg.genetics.ChipSeekerNearestFeatures;
import org.jlinalg.genetics.ChipSeekerNearestFeatures.*;
public final class RunChipSeekerTxDbNearest {
 static Map<?,?> map(Object x)throws Exception{return NextflowFiles.object(x);}
 static List<?> list(Object x)throws Exception{return NextflowFiles.list(x);}
 static int number(Object x)throws Exception{return (int)NextflowFiles.integer(x,0,Integer.MAX_VALUE);}
 static String text(Object x)throws Exception{return NextflowFiles.text(x);}
 static void check(boolean v,String text){if(!v)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception {
  check(ChipSeekerNearestFeatures.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()),"Wrong native artifact");
  var input=map(NextflowFiles.json(Path.of(args[1]),16000000));int total=0,cases=0;
  for(Object value:list(input.get("cases"))){var c=map(value);String id=text(c.get("id"));var peaks=new ArrayList<Peak>();var features=new ArrayList<Feature>();
   for(Object v:list(c.get("peaks"))){var p=map(v);peaks.add(new Peak(text(p.get("contig")),number(p.get("start")),number(p.get("end"))));}
   for(Object v:list(c.get("features"))){var f=map(v);features.add(new Feature(text(f.get("contig")),number(f.get("start")),number(f.get("end")),text(f.get("strand")).charAt(0)));}
   var options=map(c.get("options"));var request=new Options((Boolean)options.get("ignore_overlap"),(Boolean)options.get("ignore_upstream"),(Boolean)options.get("ignore_downstream"),Overlap.valueOf(text(options.get("overlap")).toUpperCase(Locale.ROOT)));
   var result=ChipSeekerNearestFeatures.annotate(peaks,features,request,new Limits(peaks.size(),Math.max(1,features.size()),50000000L),()->{});
   var retained=result.sourceRetained();var indices=list(c.get("retained_input_ordinals"));var original=list(c.get("index_tokens"));var distances=list(c.get("distance_tokens"));
   check(retained.size()==indices.size()&&retained.size()==original.size()&&retained.size()==distances.size(),id+": population mismatch");
   check(result.rows().size()==peaks.size(),id+": missing complete native disposition ledger");
   for(int i=0;i<retained.size();i++){var row=retained.get(i);String token=text(distances.get(i));double expected=token.equals("Inf")?Double.POSITIVE_INFINITY:token.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(token);
    check(row.inputIndex()+1==number(indices.get(i)),id+": source-retained query order differs");
    check(row.sourceFeatureIndex()==Integer.parseInt(text(original.get(i))),id+": source feature ordinal differs");
    check(row.distanceToTss()==expected||(Double.isNaN(row.distanceToTss())&&Double.isNaN(expected)),id+": distance differs at input "+(row.inputIndex()+1));total++;
   }cases++;
  }System.out.println("Full original TxDb nearest comparisons: "+cases+" returned cases, "+total+" retained triples; genomic labels remain upstream.");
 }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); original = args.original.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    completed = json.loads((original / 'COMPLETED.json').read_bytes())
    assert completed['status'] == 'captured-four-original-ChIPseeker-annotation-editions-not-adapter-admission'
    record = json.loads((original / 'RESULT.json').read_bytes())
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ('bytes', 'sha256')}
    production = ROOT / 'src/main/java/org/jlinalg/genetics/ChipSeekerNearestFeatures.java'
    assert pin(production) == build['source_pins'][production.relative_to(ROOT).as_posix()]
    out.mkdir(); bindings = {p.relative_to(ROOT).as_posix(): pin(p) for p in (Path(__file__).resolve(), production)}
    def read(path):
        bindings[path.relative_to(ROOT).as_posix()] = pin(path)
        return json.loads(path.read_bytes())
    def table(path):
        bindings[path.relative_to(ROOT).as_posix()] = pin(path)
        with path.open(encoding='utf-8', newline='') as stream:
            return list(csv.DictReader(stream, delimiter='\t'))
    peaks = table(original / 'fixture/peaks.tsv')
    for peak in peaks:
        peak['start'] = int(peak['start']); peak['end'] = int(peak['end'])
    cases = []
    for edition in record['editions']:
        folder = original / edition['version']; source = read(folder / 'controls/ORIGINAL.json')
        plan = {c['id']: c for c in read(folder / 'PLAN.json')['cases']}
        for result in source['cases']:
            if result['source_error'] is not None:
                continue
            path = folder / 'controls' / result['id']
            nearest = read(path / 'original-nearest.json'); features = table(path / 'prepared-features.tsv')
            for feature in features:
                feature['start'] = int(feature['start']); feature['end'] = int(feature['end'])
            cases.append(dict(id=edition['version'] + '/' + result['id'], peaks=peaks, features=features,
                options=plan[result['id']], **{k: nearest[k] for k in ('retained_input_ordinals', 'index_tokens', 'distance_tokens')}))
    assert len(cases) == completed['successes']
    fixture = out / 'controls.json'; save(fixture, dict(cases=cases)); source = out / 'RunChipSeekerTxDbNearest.java'
    source.write_text(JAVA, encoding='utf-8', newline='\n'); classes = out / 'classes'; classes.mkdir()
    command = [str(JAVA21 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), str(source)]
    compile_result = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(compile_result.stdout); (out / 'compile.stderr').write_bytes(compile_result.stderr)
    assert compile_result.returncode == 0, compile_result.stderr.decode('utf-8', errors='replace')
    common = ['-Xmx256m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8']
    commands = {'windows-java21': [str(JAVA21 / 'java.exe'), *common, '-cp', str(classes) + ';' + str(jar), 'org.jlinalg.cli.RunChipSeekerTxDbNearest', str(jar), str(fixture)],
        'windows-java25': ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe', *common, '-cp', str(classes) + ';' + str(jar), 'org.jlinalg.cli.RunChipSeekerTxDbNearest', str(jar), str(fixture)],
        'linux-java21': ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java', *common, '-cp', linux(classes) + ':' + linux(jar), 'org.jlinalg.cli.RunChipSeekerTxDbNearest', linux(jar), linux(fixture)]}
    runs = []
    for label, command in commands.items():
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=120)
        run = dict(jvm=label, command=command, exit_code=process.returncode); save(out / (label + '.process.json'), run)
        assert process.returncode == 0, label + ': inspect retained comparison logs'
        print((out / (label + '.stdout')).read_text(), end='', flush=True); runs.append(run)
    assert all(pin(ROOT / path) == wanted for path, wanted in bindings.items())
    save(out / 'COMPLETED.json', dict(status='passed-native-nearest-against-four-full-original-TxDb-annotation-editions',
        returned_cases=len(cases), retained_triples_per_jvm=sum(len(c['index_tokens']) for c in cases),
        source_annotation_errors_excluded=completed['original_errors'], artifact=build['artifact'], source_pins=bindings,
        generated_test_source=pin(source), fixture=pin(fixture), jvms=runs,
        full_genomic_labels_implemented_in_Java=False, workflow_or_catalog_admitted=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/candidate checks')
    main()
