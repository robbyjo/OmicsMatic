# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Native coverage capacity/input recovery and a genuinely restricted Java-module skip."""
import argparse
import concurrent.futures
import gzip
from signac_coverage_validation import *


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);examples=ROOT/'examples/signac-coverage'
    source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_coverage_validation.py']}
    base=read(examples/'gene-request.json')
    for key in ['annotation','cell_metadata']:base[key]=str(examples/base[key])
    base['sources']=[{k:str(examples/v) for k,v in s.items()} for s in base['sources']]
    source=(examples/'fragments.tsv').read_bytes()
    (out/'bad.tsv.gz').write_bytes(gzip.compress(source)[:-7])
    rows=source.splitlines(keepends=True);rows[0],rows[2]=rows[2],rows[0];(out/'unsorted.tsv').write_bytes(b''.join(rows))
    (out/'duplicate-cells.tsv').write_bytes((examples/'cells.tsv').read_bytes()+b'objectB\tB\t100\n')
    (out/'zero-counts.tsv').write_text('cell\tgroup\tn_count\nobjectB\tB\t0\nobjectA\tA\t0\nabsent\tB\t0\n')
    (out/'unicode-cells.tsv').write_text('cell\tgroup\tn_count\nobjectB\tB(β)\t100\nobjectA\t群组 & A<\t200\nabsent\tB(β)\t350\n',encoding='utf8')
    (out/'chr2.tsv').write_text('chr2\t10\t20\ta\t1\n')
    cases=[('missing-source',2),('duplicate-cells',2),('invalid-sort',2),('corrupt-gzip',2),('signal-cap',2),
           ('smoothing-cap',2),('annotation-cap',2),('segment-cap',2),('output-cap',2),('input-cap',2),
           ('plot-height',2),('empty-sampling',2),('zero-factors',2),('all-pruned',2),('partial-prune',0),
           ('Unicode-labels',0),('no-desktop-runtime',3)]
    def run(case,expected):
        r=dict(base);target=out/(case+'-结果');r['out']=str(target)
        if case=='missing-source':r['sources']=[dict(fragments=str(out/'unavailable.tsv'))]
        if case=='duplicate-cells':r['cell_metadata']=str(out/'duplicate-cells.tsv')
        if case=='invalid-sort':r['sources']=[dict(base['sources'][0],fragments=str(out/'unsorted.tsv'))]
        if case=='corrupt-gzip':r['sources']=[dict(base['sources'][0],fragments=str(out/'bad.tsv.gz'))]
        for name,key,value in [('signal-cap','maximum_signal_values',1),('smoothing-cap','maximum_smoothing_operations',1),
                               ('annotation-cap','maximum_annotations',1),('segment-cap','maximum_segments',1),
                               ('output-cap','maximum_output_bytes',1),('input-cap','maximum_input_bytes',1),
                               ('plot-height','maximum_plot_height',300),('empty-sampling','downsample_rate',0)]:
            if case==name:r[key]=value
        if case=='zero-factors':r['cell_metadata']=str(out/'zero-counts.tsv')
        if case=='Unicode-labels':r['cell_metadata']=str(out/'unicode-cells.tsv')
        if case in ['all-pruned','partial-prune']:
            missing=dict(base['sources'][0],fragments=str(out/'chr2.tsv'))
            r['sources']=[missing,*base['sources']] if case=='all-pruned' else [*base['sources'],missing,*base['sources']]
        request=out/(case+'.json');save(request,r)
        if case=='no-desktop-runtime':
            argv=list(map(str,[JAVA21,'--limit-modules','java.base,java.xml,java.logging,java.sql,java.management','-Djava.awt.headless=true','-Xmx768m','-cp',JAR,CLI,'--request',request]))
            with (out/(case+'.stdout')).open('xb') as a,(out/(case+'.stderr')).open('xb') as b:p=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=120)
            save(out/(case+'.process.json'),dict(command=argv,exit_code=p.returncode,expected_exit_code=3));assert p.returncode==3,'restricted Java runtime must skip dependent plotting; inspect logs'
            assert 'SKIP3' in (out/(case+'.stderr')).read_text(encoding='utf8')
        else:invoke(out,case,request,expected)
        if target.exists():
            state=read(target/'analysis-status.json');assert state['results_admitted']==(expected==0)
            if expected:assert not (target/'COMPLETED.json').exists() and ('SKIP3' if expected==3 else 'FAIL2') in state['status']
            if case=='partial-prune':
                assert (target/'coverage.tsv').read_bytes()==(ROOT/'build/signac-coverage-gene-demo-NEW/coverage.tsv').read_bytes()
                assert 'SOURCE_PERSISTENT_CHROMOSOME_PRUNING' in (target/'WARNINGS.md').read_text(encoding='utf8')
            if case=='Unicode-labels':assert '群组 &amp; A&lt;' in (target/'coverage.svg').read_text(encoding='utf8')
            verify(out,case,target,2 if expected else 0)
        return dict(case=case,expected_exit_code=expected,files=files(target) if target.exists() else {},analysis_path=target.relative_to(ROOT).as_posix())
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda c:run(*c),cases))
        target=out/'recovered-NEW-结果';r=dict(base,out=str(target));request=out/'recovered.json';save(request,r);invoke(out,'recovered',request);verify(out,'recovered',target)
        assert (target/'coverage.tsv').read_bytes()==(ROOT/'build/signac-coverage-gene-demo-NEW/coverage.tsv').read_bytes()
        assert pin(JAR)==artifact and all(pin(ROOT/n)==v for n,v in source_pins.items())
        save(out/'COMPLETED.json',dict(status='passed-native-coverage-input-capacity-module-skip-recovery',artifact=artifact,source_pins=source_pins,
             controls=17,expected_failures=14,successes=2,expected_runtime_skips=1,runs=results,recovery_in_NEW_output=True,
             recovery_files=files(target),external_R_processes_launched=0,software_installed=False))
        print('17 native coverage recovery controls passed, including restricted-Java SKIP3 and fresh-output recovery')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_NATIVE_COVERAGE_RECOVERY',error=str(e)));raise


if __name__=='__main__':main()
