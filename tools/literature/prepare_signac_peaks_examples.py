# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create explicit sorted synthetic standalone peak examples from frozen reference inputs."""
from signac_peaks_validation import *


def main():
    out=ROOT/'examples/signac-peaks';assert not out.exists();out.mkdir()
    source=BASE/'peaks-macs-original-1/inputs'
    lines=[line for n in ['a.tsv','b.tsv'] for line in (source/n).read_bytes().splitlines(keepends=True)]
    lines.sort(key=lambda x:(x.split(b'\t')[0],int(x.split(b'\t')[1]),int(x.split(b'\t')[2])))
    (out/'fragments.tsv').write_bytes(b''.join(lines));(out/'low.tsv').write_bytes((source/'low.tsv').read_bytes())
    (out/'groups.tsv').write_text('cell\tgroup\ncell1\tA\ncell2\tA\ncell3\tB\ncell4\tB\n')
    common=dict(schema_version=1,mode='POOLED',source_profile='Signac-1.9.0',macs2_version='2.2.7.1',
                macs2_home='../../build/literature-review/signac-runtime/peaks-reference-setup-5/2.2.7.1',
                genome_build='synthetic-control-not-a-biological-result',effective_genome_size=1000000,
                sources=[dict(fragments='fragments.tsv')],name='fixture',timeout_seconds=120,maximum_output_bytes=100000000)
    for case in ['pooled','grouped','bedpe','broad','empty']:
        r=dict(common,out='../../build/signac-peaks-'+case+'-demo-NEW')
        if case=='grouped':r.pop('name');r.update(mode='GROUPED',cell_groups='groups.tsv',combine_peaks=True)
        if case=='bedpe':r['format']='BEDPE'
        if case=='broad':r['broad']=True
        if case=='empty':r.update(sources=[dict(fragments='low.tsv')],qvalue=1e-20)
        save(out/(case+'-request.json'),r)
    save(out/'verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-peaks-grouped-demo-NEW'))
    save(out/'feature-request.json',dict(schema_version=1,mode='FEATURE_MATRIX',sources=[dict(path='fragments.tsv')],
         regions='../../build/signac-peaks-grouped-demo-NEW/regions.tsv',cells=['cell1','cell2','cell3','cell4'],
         genome_build=common['genome_build'],keep_all_features=True,process_n=2000,sort_records=10000,
         out='../../build/signac-peaks-feature-demo-NEW'))
    save(out/'INPUT-PROVENANCE.json',dict(status='synthetic-only',records=len(lines),
         inputs={p.relative_to(ROOT).as_posix():pin(p) for p in [source/'a.tsv',source/'b.tsv',source/'low.tsv']},
         transformation='Stable byte-record sort of a.tsv plus b.tsv by chromosome/start/end; no row/field changes. This explicit preparation also meets FeatureMatrix sorted input requirements.',
         generated={n:pin(out/n) for n in ['fragments.tsv','low.tsv','groups.tsv']}))
    (out/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    print('Created5 literal synthetic peak requests and a region-to-FeatureMatrix chain')


if __name__=='__main__':main()
