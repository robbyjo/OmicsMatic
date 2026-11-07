# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real three-edition MACS calls, original output comparisons and portable verification."""
import argparse
import concurrent.futures
import shutil
from signac_peaks_validation import *


def main():
    p = argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--reuse-captures',type=Path)
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    previous=args.reuse_captures.resolve() if args.reuse_captures else None
    if previous is not None:assert previous.is_relative_to(BASE) and previous.is_dir() and previous!=out
    artifact = pin(JAR)
    sources = {p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__), ROOT/'tools/literature/signac_peaks_validation.py']}
    groups = out/'groups.tsv';groups.write_text('cell\tgroup\ncell1\tA\ncell2\tA\ncell3\tB\ncell4\tB\n')
    cases = ['pooled','gzip','support','bedpe','broad','grouped','grouped-uncombined','empty','empty-input']

    def run(version, case):
        label = version+'-'+case;target = out/(label+'-结果')
        called_target=previous/target.name if previous is not None else target
        r = base_request(called_target,version)
        if version != '2.2.7.1':r['source_profile']='Signac-1.14.0'
        refs = ['pooled-BED'];labels = ['fixture'];expected = 0
        input = BASE/'peaks-macs-original-1/inputs'
        if case == 'gzip':
            r['sources']=[dict(fragments=linux(input/n)) for n in ['a.tsv.gz','b.tsv.gz']];refs=['pooled-gzip-BED']
        if case == 'support':
            r['sources']=[dict(fragments=linux(input/n)) for n in ['a-support.tsv','b-support.tsv']];refs=['support-does-not-weight']
        if case == 'bedpe':r['format']='BEDPE';refs=['pooled-BEDPE']
        if case == 'broad':r['broad']=True;refs=['pooled-broad']
        if case.startswith('grouped'):
            r.pop('name');r.update(mode='GROUPED',cell_groups=linux(groups),combine_peaks=case=='grouped')
            refs=['group-A','group-B'];labels=['A','B']
        if case == 'empty':r.update(sources=[dict(fragments=linux(input/'low.tsv'))],qvalue=1e-20);refs=['no-significant-peaks']
        if case == 'empty-input':r['sources']=[dict(fragments=linux(input/'empty.tsv'))];expected=2
        request=out/(label+'.json')
        if previous is None:save(request,r);invoke(out,label,request,expected)
        else:
            old=read(previous/(label+'.json'));old_groups=previous/'groups.tsv'
            if 'cell_groups' in r:r['cell_groups']=linux(old_groups)
            assert old==r and read(called_target/'source-request.json')==r
            assert read(previous/(label+'.process.json'))['exit_code']==expected
            save(request,r);shutil.copytree(called_target,target)
            for suffix in ['.stdout','.stderr','.process.json']:shutil.copyfile(previous/(label+suffix),out/(label+'-original-call'+suffix))
        state=read(target/'analysis-status.json');assert state['results_admitted']==(expected==0)
        if expected:
            assert 'FAIL2' in state['status'] and not (target/'COMPLETED.json').exists()
            assert 'Fragment source contains no data records' in state['error'] and not (target/'process').exists()
            verify(out,label,target,2)
            return dict(version=version,case=case,status='expected-empty-input-refusal-before-MACS-no-result',verifications=3,files=files(target))
        actual=table(target/'peaks.tsv');assert actual==expected_peaks(version,refs,labels,case=='broad'),label
        if case=='grouped':
            combined=table(target/'combined-peaks.tsv');assert len(combined)==3
            assert [v['peak_called_in'] for v in combined]==['A','B','A,B']
        if case=='grouped-uncombined':assert not (target/'combined-peaks.tsv').exists() and not (target/'regions.tsv').exists()
        verify(out,label,target)
        return dict(version=version,case=case,status='matched-independent-original',rows=len(actual),verifications=3,files=files(target))

    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            futures=[pool.submit(run,v,c) for v in VERSIONS for c in cases]
            results=[f.result() for f in futures]
        assert pin(JAR)==artifact and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-real-three-edition-typed-Signac-peaks-matrix',artifact=artifact,
             source_pins=sources,runs=results,actual_analyses=27,successful_analyses=24,expected_empty_input_refusals=3,
             newly_executed_analyses=27 if previous is None else 0,reused_captures_from=str(previous) if previous else None,
             successful_original_MACS_calls=30,valid_empty_peak_tables=3,portable_verifications=81,biological_validation=False))
        print('27 real typed peak analyses:24 original matches/3 early empty-input refusals;81 portable verifications')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_TYPED_PEAK_MATRIX',error=str(e)));raise


if __name__=='__main__':main()
