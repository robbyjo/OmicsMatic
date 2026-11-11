# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual final root routing and the delivered example; retain science-parent identity evidence."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import sys
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--artifact-build',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    proof=json.loads((args.artifact_build/'COMPLETED.json').read_bytes());jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert all(pin(ROOT/name)==expected for name,expected in proof['source_pins'].items())
    example=ROOT/'examples/chipseeker/create_example.py';input_dir=example.parent/'synthetic-inputs'
    bindings={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),example,*sorted(p for p in input_dir.iterdir() if p.is_file())]}
    def run(label,command,expected=0):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=700)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode,expected_exit=expected));assert r.returncode==expected,label+': read retained stdout/stderr'
    java21=str(JAVA21/'java.exe');java25='C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'
    for label,java in [('java21',java21),('java25',java25)]:
        for mode in [[],['NEAREST'],['ANNOTATE']]:run(label+'-help-'+('-'.join(mode) or 'facade'),[java,'-jar',str(jar),'chipseeker-tools',*mode,'--help'])
    run('root-example-annotation',[sys.executable,'-X','utf8',str(example),'--out',str(out/'annotation-example'),'--jar',str(jar),'--java',java21,
        '--runtime-setup',str(ROOT/'build/literature-review/chipseeker-runtime/reference-setup-4'),'--run'])
    actual=out/'annotation-example/annotation-result'
    assert json.loads((actual/'analysis-status.json').read_bytes())['result_admitted'] is True
    gold=ROOT/'build/literature-review/chipseeker-runtime/annotation-original-2/1.48.1/controls/transcript-promoter--2000-1000/original-output.json'
    bindings[gold.relative_to(ROOT).as_posix()]=pin(gold)
    expected=json.loads(gold.read_bytes());assert json.loads((actual/'results/genomic-annotation/original-output.json').read_bytes())==expected
    with (actual/'results/genomic-annotation/selected-query-ordinals.tsv').open(encoding='utf-8',newline='') as stream:selection=list(csv.DictReader(stream,delimiter='\t'))
    assert [int(r['input_ordinal']) for r in selection]==[r['input_ordinal'] for r in expected['annotations'] if r['annotation'].startswith('Promoter')]
    run('root-example-native',[sys.executable,'-X','utf8',str(example),'--out',str(out/'native-example'),'--jar',str(jar),'--java',java21,'--mode','NEAREST','--run'])
    native=out/'native-example/nearest-result';assert json.loads((native/'analysis-status.json').read_bytes())['result_admitted'] is True
    run('prepare-linux-native',[sys.executable,'-X','utf8',str(example),'--out',str(out/'linux-native'),'--jar',str(jar),'--mode','NEAREST'])
    run('linux-root-native',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx256m','-jar',linux(jar),'chipseeker-tools','NEAREST','--request',linux(out/'linux-native/nearest-request.json')])
    assert json.loads((out/'linux-native/nearest-result/analysis-status.json').read_bytes())['result_admitted'] is True
    for mode in ['NEAREST','ANNOTATE']:
        request=json.loads((out/('native-example/nearest-request.json' if mode=='NEAREST' else 'annotation-example/annotation-request.json')).read_bytes())
        # Resolve input paths before moving a request, preserving source inputs.
        if mode=='NEAREST':
            for key in ['input','features']:request['options'][key]=str(out/'native-example'/request['options'][key])
            request['options']['out']=str(native)
        else:
            for key in ['peaks','txdb']:request[key]=str(out/'annotation-example'/request[key])
            request['out']=str(actual)
        path=out/(mode+'-existing.json');save(path,request)
        target=native if mode=='NEAREST' else actual
        before={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()}
        run(mode+'-existing-refused',[java21,'-jar',str(jar),'chipseeker-tools',mode,'--request',str(path)],2)
        assert before=={p.relative_to(target).as_posix():pin(p) for p in target.rglob('*') if p.is_file()}
    missing=json.loads((out/'annotation-example/annotation-request.json').read_bytes())
    for key in ['peaks','txdb']:missing[key]=str(out/'annotation-example'/missing[key])
    missing.update(out=str(out/'missing-r-result'),rscript=str(out/'MISSING-Rscript.exe'));save(out/'missing-r.json',missing)
    run('root-dependent-skip',[java21,'-jar',str(jar),'chipseeker-tools','ANNOTATE','--request',str(out/'missing-r.json')],3)
    assert json.loads((out/'missing-r-result/analysis-status.json').read_bytes())['result_admitted'] is False
    assert all(pin(ROOT/name)==expected for name,expected in bindings.items())
    assert all(pin(ROOT/name)==expected for name,expected in proof['source_pins'].items())
    save(out/'COMPLETED.json',dict(status='passed-actual-root-and-delivered-ChIPseeker-examples',artifact=proof['artifact'],source_pins=bindings,
        original_annotation_runs=1,native_example_runs=2,help_routes=6,unchanged_existing_output_refusals=2,dependency_skips=1,
        selected_source_rows=len(selection),genomic_labels_equal_full_original=True,backend_parent=proof['parent_artifact'],catalog_or_release_admitted=False))
    print('Final root: genuine original annotation, delivered native/annotation helpers, Linux native route, help, dependency skip and unchanged existing outputs passed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable final-root/original-output checks')
    main()
