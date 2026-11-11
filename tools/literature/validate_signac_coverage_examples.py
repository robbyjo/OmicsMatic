# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run literal and portable native coverage calls against original composed plot data."""
import argparse
import csv
import math
from signac_coverage_validation import *


def table(path):
    with path.open(encoding='utf8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))


def compare(actual,expected):
    assert len(actual)==len(expected)
    for a,e in zip(actual,expected):
        assert a['group']==e['group'] and int(a['position'])==e['position'] and int(a['cut_count'])==e['count']
        for old,new in [('norm.value','normalized'),('coverage','coverage')]:
            x,y=e[old],float(a[new]);assert math.isfinite(y) and abs(x-y)<=2e-12*max(1,abs(x))


def compare_annotations(target,original):
    for old,new in [('labels','annotation-labels.tsv'),('exons','annotation-exons.tsv'),('plus','annotation-plus.tsv'),('minus','annotation-minus.tsv')]:
        actual=table(target/new);expected=original[old];assert len(actual)==len(expected)
        for a,e in zip(actual,expected):
            for key in ['strand','gene_name','tx_id','gene_biotype']:assert a[key]==e[key]
            assert a['chromosome']==e['seqnames'] and int(a['dodge'])==e['dodge']
            for key in ['start','end']+(['width','position'] if old=='labels' else []):assert abs(float(a[key])-e[key])<=1e-11*max(1,abs(e[key]))
            if old!='labels':assert a['type']==e['type']


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);examples=ROOT/'examples/signac-coverage';reference=BASE/'coverage-examples-original-2'
    assert (reference/'exit.txt').read_text()=='0'
    originals={v['example']:v for v in read(reference/'original/RESULT.json')['results']}
    assert len(originals)==3 and all('error' not in v['result']['value'] for v in originals.values())
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_coverage_validation.py',*examples.iterdir()] if p.is_file()}
    save(out/'SOURCE-PINS.json',dict(artifact=artifact,source_pins=sources,original_reference=pin(reference/'original/RESULT.json')))
    results=[];svgs={}
    try:
        for platform in ['windows-java21','windows-java25','linux-java21']:
            convert=linux if platform=='linux-java21' else str
            for case in ['gene','transcript','peak-assay']:
                label=platform+'-'+case;original_request=examples/(case+'-request.json');r=read(original_request)
                if platform=='windows-java21':request=original_request;target=(examples/r['out']).resolve()
                else:
                    target=out/(label+'-结果');r['out']=convert(target)
                    for key in ['cell_metadata','annotation']:r[key]=convert((examples/r[key]).resolve())
                    r['sources']=[{k:convert((examples/v).resolve()) for k,v in s.items()} for s in r['sources']]
                    request=out/(label+'.json');save(request,r)
                assert not target.exists();invoke(out,label,request,platform=platform)
                state=read(target/'analysis-status.json');assert state['results_admitted'] and state['status']=='completed-with-warnings'
                expected=originals[case]['result']['value'];compare(table(target/'coverage.tsv'),expected['rows']);compare_annotations(target,expected['annotation'])
                verify(out,label,target)
                svg=(target/'coverage.svg').read_bytes()
                if case in svgs:assert svg==svgs[case]
                else:svgs[case]=svg
                results.append(dict(platform=platform,example=case,path=target.relative_to(ROOT).as_posix(),rows=len(expected['rows']),files=files(target)))
        invoke(out,'literal-verify',examples/'verify-request.json')
        r=read(examples/'empty-request.json');target=(examples/r['out']).resolve();assert not target.exists()
        invoke(out,'literal-empty-failure',examples/'empty-request.json',2)
        assert not read(target/'analysis-status.json')['results_admitted'] and not (target/'COMPLETED.json').exists()
        assert 'SOURCE_UNPLOTTABLE_COVERAGE' in (out/'literal-empty-failure.stderr').read_text(encoding='utf8')
        verify(out,'literal-empty-failure',target,2)
        assert pin(JAR)==artifact and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-nine-actual-native-coverage-analyses-against-three-original-composed-plots',
             artifact=artifact,source_pins=sources,runs=results,analyses=9,independent_original_examples=3,
             portable_verifications=30,literal_verify_example=True,expected_empty_sampling_failure=True,
             empty_failure_files=files(target),SVG_bytes_identical_across_JVMs=True,raster_pixels_independently_verified=False,
             native_peak_assay_counts=[1202,600,1200,600],biological_validation=False))
        print('9 native coverage analyses matched original composed plot data/model geometry;30 portable verifications passed')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_NATIVE_COVERAGE_EXAMPLES',error=str(e)));raise


if __name__=='__main__':main()
