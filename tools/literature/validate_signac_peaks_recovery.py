# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real grouped maps/collisions/empty cases and no-result/capacity recovery controls."""
import argparse
import concurrent.futures
from signac_peaks_validation import *


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_peaks_validation.py']}
    groups=out/'groups.tsv';groups.write_text('cell\tgroup\ncell1\tA\ncell2\tA\ncell3\tB\ncell4\tB\n')
    collision=out/'collision.tsv';collision.write_text('cell\tgroup\ncell1\tA B\ncell2\tA/B\ncell3\tA B\ncell4\tA/B\n')
    mapped=out/'mapped.tsv';mapped.write_text('cell\tgroup\nobject1\tA\nobject2\tA\nobject3\tB\nobject4\tB\n')
    mapping=out/'map.tsv';mapping.write_text('cell\tbarcode\n'+''.join('object%d\tcell%d\n'%(i,i) for i in range(1,5)))
    absent=out/'absent.tsv';absent.write_text('cell\tgroup\ncell1\tA\ncell2\tA\nnonexistent\tC\n')
    low=out/'low-groups.tsv';low.write_text('cell\tgroup\ncell1\tLow\n')
    input=BASE/'peaks-macs-original-1/inputs'
    corrupt=out/'bad.tsv.gz';corrupt.write_bytes((input/'a.tsv.gz').read_bytes()[:-7])
    cases=[('maps',0),('collision',0),('unicode-name',0),('bedgraph-SPMR',0),('empty-uncombined',0),
           ('empty-combined',2),('missing-group-source-failure',2),('input-cap',2),('records-cap',2),
           ('decoded-cap',2),('peak-cap',2),('rss-cap',2),('file-cap',2),('corrupt-gzip',2),
           ('wrong-edition',2),('unavailable-runtime',3),('windows-runtime',3)]
    def run(case,expected):
        target=out/(case+'-结果');r=base_request(target);platform='linux-java21'
        if case in ['maps','collision','empty-uncombined','empty-combined','missing-group-source-failure']:
            r.pop('name');r.update(mode='GROUPED',cell_groups=linux(groups))
        if case=='maps':r.update(cell_groups=linux(mapped));r['sources']=[dict(s,map=linux(mapping)) for s in r['sources']]
        if case=='collision':r['cell_groups']=linux(collision)
        if case=='unicode-name':r['name']="族 /O'Brien $(literal);"
        if case=='bedgraph-SPMR':r.update(bedgraph=True,SPMR=True)
        if case in ['empty-uncombined','empty-combined']:
            r.update(cell_groups=linux(low),sources=[dict(fragments=linux(input/'low.tsv'))],qvalue=1e-20,combine_peaks=case=='empty-combined')
        if case=='missing-group-source-failure':r['cell_groups']=linux(absent)
        for name,key,value in [('input-cap','maximum_input_bytes',1),('records-cap','maximum_fragment_records',1),('decoded-cap','maximum_decoded_bytes',32),('peak-cap','maximum_peak_rows',1),('rss-cap','maximum_sampled_rss_bytes',1),('file-cap','maximum_files',4)]:
            if case==name:r[key]=value
        if case=='corrupt-gzip':r['sources']=[dict(fragments=linux(corrupt))]
        if case=='wrong-edition':r['macs2_version']='2.2.9'
        if case=='unavailable-runtime':r['macs2_home']=linux(out/'permitted-exact-runtime-not-installed')
        if case=='windows-runtime':
            platform='windows-java21';r['out']=str(target);r['sources']=[dict(fragments=str(input/n)) for n in ['a.tsv','b.tsv']]
        request=out/(case+'.json');save(request,r);invoke(out,case,request,expected,platform)
        state=read(target/'analysis-status.json');assert state['results_admitted']==(expected==0)
        warnings=(target/'WARNINGS.md').read_text(encoding='utf8');assert 'Required action:' in warnings
        if expected:
            assert ('SKIP3' if expected==3 else 'FAIL2') in state['status'] and not (target/'COMPLETED.json').exists()
            for f in target.rglob('process.json'):assert read(f)['owned_live_after_cleanup']==[],f
            verify(out,case,target,2,('windows-java21',))
        else:
            actual=table(target/'peaks.tsv')
            if case=='maps':assert actual==expected_peaks('2.2.7.1',['group-A','group-B'],['A','B'])
            if case=='collision':
                assert actual==expected_peaks('2.2.7.1',['pooled-BED'],['A_B'])
                assert 'COLLISION' in warnings and read(target/'prepared-summary.json')['jobs'][0]['original_labels']==['A B','A/B']
            if case=='unicode-name':assert actual==expected_peaks('2.2.7.1',['pooled-BED'],["族__O'Brien_$(literal);"])
            if case=='bedgraph-SPMR':
                assert actual==expected_peaks('2.2.7.1',['pooled-BED'],['fixture'])
                assert all((target/('macs/g1/g1_'+n+'.bdg')).stat().st_size>0 for n in ['treat_pileup','control_lambda'])
            if case=='empty-uncombined':assert actual==[] and 'SOURCE_EMPTY_PEAK_TABLE' in warnings
            verify(out,case,target)
        return dict(case=case,expected_exit_code=expected,admitted=expected==0,files=files(target))
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda c:run(*c),cases))
        # Retry after unavailable dependency into a fresh destination, using the exact permitted home.
        target=out/'recovered-NEW-结果';r=base_request(target);request=out/'recovered.json';save(request,r)
        invoke(out,'recovered',request);verify(out,'recovered',target)
        assert table(target/'peaks.tsv')==expected_peaks('2.2.7.1',['pooled-BED'],['fixture'])
        assert pin(JAR)==artifact and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-real-peak-population-capacity-skip-failure-recovery',artifact=artifact,
             source_pins=sources,runs=results,requested_controls=17,successes=5,expected_failures=10,expected_skips=2,
             recovery_in_new_output=True,recovered_files=files(target),owned_children_left_live=0))
        print('17 peak population/recovery controls passed; exact-runtime recovery passed in NEW output')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_TYPED_PEAK_RECOVERY',error=str(e)));raise


if __name__=='__main__':main()
