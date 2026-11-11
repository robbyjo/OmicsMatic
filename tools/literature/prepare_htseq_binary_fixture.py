# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain actual original interval traces for explicit binary-presence comparisons."""
import gzip
import json
from setup_htseq_reference import ROOT,BASE,pin


def main():
    original=BASE/'binary-original-1';completion=json.loads((original/'COMPLETED.json').read_bytes())
    raw=(original/'RESULT.json').read_bytes();assert pin(raw)==completion['result'];data=json.loads(raw)
    good=[r for r in data['cases'] if r['matrix'] is not None]
    assert len(good)==32 and len(data['cases'])==34
    stats=dict(successful_matrices=32,original_refusals=2,prepared_reads=sum(r['matrix']['processed_reads'] for r in good),
        binary_nonzero_coordinates=sum(len(r['matrix']['entries']) for r in good),source_step_spans=sum(len(steps) for r in good for trace in r['trace'] for steps in trace['original_steps']))
    target=ROOT/'src/test/resources/org/jlinalg/sequence/htseq-binary-accessibility';assert not target.exists();target.mkdir(parents=True)
    (target/'original-matrices.json.gz').write_bytes(gzip.compress(raw,mtime=0));(target/'.gitattributes').write_text('*.gz binary\n*.json text eol=lf\n',encoding='ascii')
    (target/'PROVENANCE.json').write_text(json.dumps(dict(result=pin(raw),**stats,
        original_interval_library='HTSeq2.1.2',original_htseq_count=False,original_paper_custom_script_recovered=False,
        scope='Explicit >=1 read-presence reduction over actual original GenomicArrayOfSets steps. Reused tiny synthetic populations, not paper-exact cell/barcode/filter/peak preparation.'),indent=2)+'\n',encoding='utf8',newline='\n')
    profile=ROOT/'src/main/resources/org/jlinalg/omics/htseq-binary-accessibility';assert not profile.exists();profile.mkdir(parents=True)
    (profile/'SOURCE-PROFILE.json').write_text(json.dumps(dict(schema_version=1,primitive='binary peak-by-cell prepared-read presence',reference='HTSeq2.1.2 interval-set geometry',
        java_interval_dependency='org.jlinalg.sequence.HtseqFeatureAssignment',fixture=pin(raw),**stats,
        primary_paper='P099',doi='10.1038/s41588-023-01572-y',reported_HTSeq_edition=None,
        original_custom_author_script_recovered=False,original_gene_counter_substituted=False,
        independent_native_interval_and_binary_reduction=True,native_barcode_or_SAM_GTF_preparation=False,
        scope='A peak-cell value is1 iff any supplied read block overlaps that supplied peak. Axes/order/strand interpretation/filtering/read units/cell assignment are caller-declared. No peak calling/merging or UMI/differential model.'),indent=2)+'\n',encoding='utf8',newline='\n')
    (profile/'.gitattributes').write_text('* -text\n',encoding='ascii')
    print(json.dumps(dict(status='retained-original-binary-presence-controls',**stats)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original trace checks')
    main()
