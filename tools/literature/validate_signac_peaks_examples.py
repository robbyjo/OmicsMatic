# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run every literal peak example and quantify its combined regions using native FeatureMatrix."""
import argparse
from signac_peaks_validation import *


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--reuse-analyses-from',type=Path)
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    previous=args.reuse_analyses_from.resolve() if args.reuse_analyses_from else None
    if previous is not None:assert previous.is_relative_to(BASE) and previous.is_dir()
    artifact=pin(JAR);examples=ROOT/'examples/signac-peaks'
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_peaks_validation.py',*examples.iterdir()] if p.is_file()}
    save(out/'SOURCE-PINS.json',dict(artifact=artifact,source_pins=sources))
    def analysis(label,request,target,cli=CLI):
        if previous is None:
            assert not target.exists();invoke(out,label,request,cli=cli)
        else:
            assert read(target/'source-request.json')==read(request)
            receipt=read(previous/(label+'.process.json'));assert receipt['exit_code']==0
            assert receipt['command']==list(map(str,['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx768m','-cp',linux(JAR),cli,'--request',linux(request)]))
            save(out/(label+'-REUSED.process.json'),dict(original_run_directory=str(previous),original_process=receipt,files=files(target),not_a_new_analysis=True))
    def check(label,target,cli=CLI):
        request=out/(label+'-verify.json');save(request,dict(schema_version=1,mode='VERIFY',analysis=linux(target)))
        invoke(out,label+'-linux-verify',request,cli=cli)
        save(request.with_name(label+'-windows-verify.json'),dict(schema_version=1,mode='VERIFY',analysis=str(target)))
        for platform in ['windows-java21','windows-java25']:invoke(out,label+'-'+platform,request.with_name(label+'-windows-verify.json'),platform=platform,cli=cli)
    runs=[]
    try:
        for case in ['pooled','grouped','bedpe','broad','empty']:
            request=examples/(case+'-request.json');r=read(request);target=(examples/r['out']).resolve()
            analysis(case,request,target);check(case,target)
            refs=['group-A','group-B'] if case=='grouped' else [dict(pooled='pooled-BED',bedpe='pooled-BEDPE',broad='pooled-broad',empty='no-significant-peaks')[case]]
            labels=['A','B'] if case=='grouped' else ['fixture']
            assert table(target/'peaks.tsv')==expected_peaks('2.2.7.1',refs,labels,case=='broad')
            runs.append(dict(example=case,path=target.relative_to(ROOT).as_posix(),files=files(target)))
        invoke(out,'literal-verify',examples/'verify-request.json')
        cli='org.jlinalg.cli.SignacFragmentsRequestCli';request=examples/'feature-request.json';r=read(request)
        target=(examples/r['out']).resolve();analysis('feature',request,target,cli);check('feature',target,cli)
        regions=table(ROOT/'build/signac-peaks-grouped-demo-NEW/regions.tsv');cells=['cell1','cell2','cell3','cell4']
        expected=[[0]*4 for _ in regions]
        for line in (examples/'fragments.tsv').read_text().splitlines():
            chrom,start,end,cell,support=line.split('\t');start=int(start);end=int(end)
            for i,region in enumerate(regions):
                if chrom==region['chromosome'] and start<int(region['end']) and end>=int(region['start']):expected[i][cells.index(cell)]+=1
        lines=[l for l in (target/'matrix.mtx').read_text().splitlines() if not l.startswith('%')]
        shape=list(map(int,lines[0].split()));actual=[[0]*shape[1] for _ in range(shape[0])]
        for line in lines[1:]:i,j,value=line.split();actual[int(i)-1][int(j)-1]=int(float(value))
        assert shape[:2]==[3,4] and actual==expected and sum(map(sum,actual))==3602
        assert (target/'cells.tsv').read_text().splitlines()==cells
        assert pin(JAR)==artifact and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-five-literal-peak-examples-and-native-feature-chain',artifact=artifact,
             source_pins=sources,runs=runs,literal_peak_examples=5,literal_verify_example=True,portable_verifications=18,
             native_feature_matrix=dict(rows=3,columns=4,count_sum=3602,input_fragment_lines=6600,expected=expected,files=files(target)),
             newly_executed_analyses=6 if previous is None else 0,reused_analyses_from=str(previous) if previous else None,
             counts_independently_checked_by_complete_interval_scan=True,biological_validation=False))
        print('5 literal peak calls + literal VERIFY +3x4 native FeatureMatrix chain passed;18 portable verifications')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_TYPED_PEAK_EXAMPLES',error=str(e)));raise


if __name__=='__main__':main()
