# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise closed file COUNT/VERIFY against five previously retained original count tables."""
import argparse
import concurrent.futures
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_htseq_reference import DISTRO,linux
from build_htseq_file_count_development import TARGET
from validate_deseq2_inference import JAVA21,JAVA25

BASE=ROOT/'build/literature-review/htseq-runtime'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--versions',nargs='+',default=['0.5.3','0.6.0','0.11.1','0.13.5','2.1.2']);parser.add_argument('--smoke',action='store_true')
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    profiles=json.loads((BASE/'count-reference-binding-2/COMPLETED.json').read_bytes())['profiles'];homes={r['version']:r['home'] for r in profiles}
    originals={}
    for name in ['historical-py27-counts-2','historical-py39-counts-1','count-original-1']:
        for row in json.loads((BASE/name/'COMPLETED.json').read_bytes())['cases']:originals[(row.get('version','2.1.2'),row['id'])]=row
    selected=['single-yes-union'] if args.smoke else ['single-no-union','paired-reverse-intersection-nonempty','source-default-minimum-mapq',
        'header-only-sam','eqx-sam','single-bam','paired-position-bam','single-nonunique-fraction','paired-nonunique-all','missing-id-error']
    available=sorted({key[1] for key in originals});save(out/'available-case-ids.json',available)
    if not args.smoke:
        # The current collector names its default-mapq control differently;
        # explicit min0/10 controls below retain their own exact source tables.
        assert all(name in available for name in selected),set(selected)-set(available)
    cases=[originals[(v,name)] for v in args.versions for name in selected if (v,name) in originals]
    assert cases and set(args.versions)=={row.get('version','2.1.2') for row in cases}
    artifact=pin(TARGET)
    def invoke(folder,label,request,jvm):
        request_file=folder/(label+'.json');save(request_file,request)
        tail=['-cp',linux(TARGET),'org.jlinalg.cli.HtseqToolsCli','--request',linux(request_file)]
        argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/java','-Xmx768m',*tail] if jvm=='linux-java21' else [JAVA21 if jvm=='windows-java21' else JAVA25,'-Xmx768m','-cp',str(TARGET),'org.jlinalg.cli.HtseqToolsCli','--request',str(request_file)]
        result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=600)
        (folder/(label+'.stdout')).write_bytes(result.stdout);(folder/(label+'.stderr')).write_bytes(result.stderr)
        save(folder/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));return result.returncode
    def run(row):
        version=row.get('version','2.1.2');spec=row['config'];folder=out/(version+'-'+row['id']);folder.mkdir()
        data=BASE/'count-original-1/data';alignment=data/spec['filename'];annotation=data/spec.get('annotation_filename','features.gtf')
        request=dict(schema_version=1,operation='COUNT',version=version,runtime_home=homes[version],alignment=linux(alignment),annotation=linux(annotation),
            format='bam' if alignment.suffix=='.bam' else 'sam',order=spec['order'],stranded=spec['stranded'],overlap=spec['overlap'],nonunique=spec['nonunique'],
            secondary=spec['secondary'],supplementary=spec['supplementary'],minimum_mapq=spec['minimum_mapq'],feature_type='exon',id_attribute='gene_id',
            genome_label='synthetic-chr1-chr2',annotation_label='original-controls-exon-gene_id',counting_unit='original-auto',out=linux(folder/'count-run'),
            max_input_bytes=1048576,max_output_bytes=16777216,max_count_rows=1000,timeout_seconds=120,memory_bytes=2147483648)
        code=invoke(folder,'count-request',request,'linux-java21');expected=0 if row['exit_code']==0 else 2
        assert code==expected,(version,row['id'],code,expected)
        source=folder/'count-run';status=json.loads((source/'analysis-status.json').read_bytes())
        if code==0:
            def tsv(path):return [line.split('\t') for line in path.read_text(encoding='utf8').splitlines()[1:]]
            assert tsv(source/'counts.tsv')==row['counts']['genes'] and tsv(source/'summary.tsv')==row['counts']['summary']
            checks=[]
            for jvm in ['windows-java21','windows-java25','linux-java21']:
                verify=dict(schema_version=1,operation='VERIFY',run=str(source) if jvm.startswith('windows') else linux(source),
                    out=str(folder/('verify-'+jvm)) if jvm.startswith('windows') else linux(folder/('verify-'+jvm)))
                assert invoke(folder,'verify-request-'+jvm,verify,jvm)==0,(version,row['id'],jvm)
                proof=json.loads((folder/('verify-'+jvm)/'verification.json').read_bytes());assert not proof['execution_authenticated'];checks.append(jvm)
            return dict(version=version,id=row['id'],status='passed-original-count-and-three-JVM-VERIFY',feature_rows=len(row['counts']['genes']),original_summary_fields=5,portable_verify_JVMs=checks)
        assert not status['result_admitted'] and not (source/'COMPLETED.json').exists() and not (source/'counts.tsv').exists()
        return dict(version=version,id=row['id'],status='retained-expected-original-source-failure',exit_code=code)
    # Source snapshots are deliberately complete; bound parallel disk/memory work.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(run,cases))
    assert pin(TARGET)==artifact
    save(out/'COMPLETED.json',dict(status='passed-actual-HTSeq-file-count-and-portable-VERIFY-controls',artifact=artifact,cases=results,
        original_analyses=sum(r['status'].startswith('passed-') for r in results),expected_source_failures=sum(r['status'].startswith('retained-') for r in results),
        verification_JVMs=['windows-java21','windows-java25','linux-java21'],full_project_test_suite_run=False,catalog_credit=False))
    print(json.dumps(dict(status='passed-HTSeq-file-count-workflow-controls',controls=len(results),results=results)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-table comparisons')
    main()
