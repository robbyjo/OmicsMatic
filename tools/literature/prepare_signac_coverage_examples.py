# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create literal native coverage examples with declared assay counts and annotation provenance."""
import csv
import json
from setup_deseq2_reference import ROOT,pin,save


def main():
    out=ROOT/'examples/signac-coverage';assert not out.exists();out.mkdir()
    source=ROOT/'build/literature-review/signac-runtime/coverage-original-edges-3/original/RESULT.json';original=json.loads(source.read_bytes())
    (out/'fragments.tsv').write_text(original['fragments'],encoding='utf8',newline='\n')
    (out/'cells.tsv').write_text('cell\tgroup\tn_count\nobjectB\tB\t100\nobjectA\tA\t200\nabsent\tB\t350\n')
    (out/'map.tsv').write_text('cell\tbarcode\nobjectB\tb\nobjectA\ta\nabsent\tmissing\n')
    header='chromosome\tstart\tend\tstrand\tgene_name\ttx_id\tgene_biotype\ttype\n'
    annotation='chr1\t101\t108\t+\tGeneA\ttx1\tprotein_coding\texon\nchr1\t111\t119\t+\tGeneA\ttx1\tprotein_coding\texon\n'
    (out/'annotation.tsv').write_text(header+annotation)
    (out/'transcript-annotation.tsv').write_text(header+annotation+'chr1\t102\t109\t+\tGeneA\ttx2\tprotein_coding\texon\nchr1\t116\t120\t+\tGeneA\ttx2\tprotein_coding\texon\n')
    common=dict(schema_version=1,mode='COVERAGE',source_profile='Signac-1.9.0',genome_build='synthetic-not-a-biological-result',
         region=dict(chromosome='chr1',start=100,end=120),sources=[dict(fragments='fragments.tsv',map='map.tsv')],
         cell_metadata='cells.tsv',annotation='annotation.tsv',window=1,downsample_rate=1,title='Synthetic accessibility and gene model')
    save(out/'gene-request.json',dict(common,out='../../build/signac-coverage-gene-demo-NEW'))
    save(out/'transcript-request.json',dict(common,out='../../build/signac-coverage-transcript-demo-NEW',source_profile='Signac-1.14.0',annotation='transcript-annotation.tsv',annotation_mode='transcript'))
    save(out/'empty-request.json',dict(common,out='../../build/signac-coverage-empty-demo-NEW',downsample_rate=0))
    save(out/'verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-coverage-gene-demo-NEW'))
    # Peak-assay nCount is derived from the previous real native FeatureMatrix example, not raw read support.
    assay=ROOT/'build/signac-peaks-feature-demo-NEW';cells=(assay/'cells.tsv').read_text().splitlines();totals=[0]*len(cells)
    lines=[line for line in (assay/'matrix.mtx').read_text().splitlines() if not line.startswith('%')]
    for line in lines[1:]:row,column,value=line.split();totals[int(column)-1]+=int(float(value))
    assert cells==['cell1','cell2','cell3','cell4'] and totals==[1202,600,1200,600]
    (out/'peak-assay-cells.tsv').write_text('cell\tgroup\tn_count\n'+''.join(c+'\t'+('A' if i<2 else 'B')+'\t'+str(totals[i])+'\n' for i,c in enumerate(cells)))
    (out/'peak-assay-annotation.tsv').write_text(header+'chr1\t9930\t9970\t+\tSyntheticA\ttxA\tsynthetic\texon\nchr1\t10020\t10080\t+\tSyntheticA\ttxA\tsynthetic\texon\n')
    save(out/'peak-assay-request.json',dict(schema_version=1,mode='COVERAGE',source_profile='Signac-1.9.0',genome_build=common['genome_build'],
         region=dict(chromosome='chr1',start=9700,end=10300),sources=[dict(fragments='../signac-peaks/fragments.tsv')],
         cell_metadata='peak-assay-cells.tsv',annotation='peak-assay-annotation.tsv',window=25,downsample_rate=1,
         title='Synthetic called-peak assay coverage',out='../../build/signac-coverage-peak-assay-demo-NEW'))
    save(out/'INPUT-PROVENANCE.json',dict(status='synthetic-only-no-biological-inference',original_complete_coverage_control=pin(source),
         example_count_values='100,200,350 are original synthetic object nCount values; an absent fragment cell remains in normalization.',
         added_transcript='tx2 is an explicitly synthetic second isoform of GeneA.',
         peak_assay_counts=dict(assay_inputs={p.relative_to(ROOT).as_posix():pin(p) for p in [assay/'matrix.mtx',assay/'cells.tsv']},
             cells=cells,n_counts=totals,derivation='Exact MatrixMarket column sums in declared cell order; not raw fragment counts or read-support sums.'),
         generated_inputs={p.name:pin(p) for p in out.iterdir() if p.suffix=='.tsv'}))
    (out/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n');print('Prepared native gene/transcript/peak-assay examples and an expected empty-sampling failure')


if __name__=='__main__':main()
