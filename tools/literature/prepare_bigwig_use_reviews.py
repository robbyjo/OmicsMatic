# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare five converter-specific primary-source reviews; do not modify the current catalog/review registry."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
NOTES={
 'P013':('Converted bedtools genomecov bedGraph of one-base Micro-C contact junctions to bigWig, then used that track as a deepTools sample input.', 'The converter does not construct junction coordinates, run genomecov or perform downstream deepTools analyses. Converter edition, chromosome-size input, precision, compression and zoom settings are unstated; the linked binary directory is not a pinned edition.'),
 'P050':('Sorted MACS2 ChIP-seq pileup bedGraph with sort -k1,1 -k2,2n, then converted it with BedGraphToBigWig for genome-browser visualization and downstream deepTools gene-centred analysis.', 'The source specifies the T. gondii TGME49 reference and the sort command, but no exact converter edition or chromosome-size input. The sort locale is unstated. MACS2 peak calling, deepTools binning/clustering and nf-core/HOMER analyses are separate steps, not converter options.'),
 'P110':('Generated bigWig tracks with UCSC bedGraphToBigWig, reported as v4, after Nanopore modified-base processing and partitioning sites into AT, AA, AC and AG contexts.', 'Preserve the literal reported v4: its relationship to converter release versus BBI file format is unresolved; do not relabel it as current converter2.10. Reference assemblies are listed separately, while the converter chromosome-size input and options are unstated. Guppy/modbam2bed calls, sequence-context extraction and deepTools methylation summaries are separate steps.'),
 'P138':('Converted MACS2 ATAC-seq bedGraph pileup tracks to bigWig after processing mm10 embryo reads and calling peaks on separate and merged biological replicates.', 'Converter edition, exact chromosome-size input and conversion options are unstated. The reported MACS2 shift/extension/duplicate settings and replicate-confidence peak selection do not belong to bedGraphToBigWig. The converter does not merge reads, call peaks or establish replicate reproducibility.')
}


def main():
    audit=json.loads((ROOT/'build/literature-review/bigwig-native/primary-use-audit-1/PRIMARY-USES.json').read_bytes());assert audit['papers_scanned']==250 and len(audit['records'])==5;current=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes());draft=[]
    for row in audit['records']:
        assert len(row['matches'])==1 and len(row['catalogued'])==1;anchor=row['catalogued'][0];assert anchor['evidence_paragraph_sha256']==row['matches'][0]['sha256'];prior=[r for r in current['reviews'] if r['tool_id']=='ucsc-bedgraphtobigwig' and r['pmcid']==row['pmcid']]
        if row['paper_id']=='P163':assert len(prior)==1;review=prior[0]
        else:
            assert not prior;summary,boundary=NOTES[row['paper_id']];review=dict(pmcid=row['pmcid'],doi=row['doi'],tool_id='ucsc-bedgraphtobigwig',evidence_paragraph_sha256=anchor['evidence_paragraph_sha256'],use_summary=summary,implementation_scope_note=boundary,version_reported_near_name='v4' if row['paper_id']=='P110' else None,usage_context='analysis-method-description',supporting_evidence=[])
        draft.append(dict(paper_id=row['paper_id'],review=review))
    target=ROOT/'tools/literature/bigwig-use-reviews.json';assert not target.exists();target.write_text(json.dumps(draft,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(status='drafted-source-reviews-not-applied',source_roles=5,new_reviews=4,prior_review_unchanged=True,current_reviews=len(current['reviews']))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary review checks')
    main()
