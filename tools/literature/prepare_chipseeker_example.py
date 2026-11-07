# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Deliver the captured explicit synthetic TxDb and ordered features for the actual root example."""
import csv
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    target=ROOT/'examples/chipseeker/synthetic-inputs';assert not target.exists();target.mkdir(parents=True)
    original=ROOT/'build/literature-review/chipseeker-runtime/annotation-original-2'
    proof=json.loads((original/'RESULT.json').read_bytes());fixture=original/'fixture'
    for name,expected in proof['fixture_files'].items():assert pin(fixture/name)==expected
    for name in ['synthetic-txdb.sqlite','peaks.tsv','transcripts.tsv','splicings.tsv','chrominfo.tsv','metadata.tsv']:
        (target/name).write_bytes((fixture/name).read_bytes())
    nearest=json.loads((original/'1.48.1/controls/transcript-promoter--3000-3000/original-nearest.json').read_bytes())
    with (target/'features.tsv').open('w',encoding='utf-8',newline='') as stream:
        writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(['feature_id','contig','start','end','strand'])
        for f in nearest['features']:writer.writerow([f['tx_name'],f['seqnames'],f['start'],f['end'],f['strand']])
    save(target/'PROVENANCE.json',dict(scope='Explicit synthetic TxDb:5 transcripts,13 exons,70 queries. No real genome/experiment/paper.',
        fixture_source='tools/literature/prepare_chipseeker_annotation_fixture.R',builder_source_guard='txdbmaker1.8.0 makeTxDb body/formals matched official source',
        native_feature_ids='Original transcript names from the exact ordered getGene(by=transcript) extraction; duplicate gene-group names remain separately in original references.',
        fixture_sqlite=pin(target/'synthetic-txdb.sqlite')))
    print('Copied synthetic inputs and exact source-ordered transcript features; no real data or analysis generated')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable fixture identity checks')
    main()
