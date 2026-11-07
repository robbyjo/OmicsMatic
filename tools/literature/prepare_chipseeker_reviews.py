# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare seven primary-bound ChIPseeker usage drafts; apply no catalog or implementation changes."""
import json
from setup_deseq2_reference import ROOT,pin,save


REVIEWS={
 'P020':('1.26.2',
  'Annotated genomic locations of ChIP-seq peaks with ChIPseeker 1.26.2, defining promoters from 2 kb upstream to 1 kb downstream of the transcription start site.',
  'The ChIP-seq pipeline aligns to GRCh38, but the exact annotation/TxDb resource, function arguments beyond the promoter definition and feature-priority choices are unstated. The pipeline default statement does not identify those omitted choices. Native prepared nearest-TSS mapping alone does not supply promoter/feature classification, differential binding, normalization or the complete ChIP_Zwart pipeline.'),
 'P043':('1.34.1',
  'Used ChIPseeker 1.34.1 for the genomic-location strategy linking TE-derived candidate cis-regulatory elements to genes within 3 kb flanking regions; cCRE-gene correlation was a separate strategy.',
  'The exact ChIPseeker function, gene/TxDb resource and anchor/orientation of the 3 kb flanks are unstated. Do not silently translate this into a particular tssRegion, all-neighbor rule or nearest-gene-only call. Expression filtering before GO tests, Wilcoxon differential accessibility and HOMER motif enrichment are separate analyses. The prepared nearest-feature subset does not establish this complete mapping strategy.'),
 'P044':('1.22.1',
  'Annotated the genomic distribution of CUT&Tag peaks with ChIPseeker 1.22.1 after mm10 alignment and MACS2 peak calling.',
  'The exact annotation/TxDb resource, function, promoter/TSS window, gene-versus-transcript level and genomic feature priorities are unstated. mm10 is reported for alignment; no unreported database release is inferred. Native nearest-feature distances do not reproduce genomic-distribution labels, plots or the full CUT&Tag pipeline.'),
 'P124':(None,
  'Used ChIPseeker to annotate genomic regions and retrieve the nearest gene around each ATAC-seq peak in the hg38 analysis; peak calling, union-peak counting and DESeq2 testing were separate steps.',
  'The ChIPseeker edition, annotation/TxDb resource, gene-versus-transcript level, distance/strand/overlap options and promoter window are unstated. The native subset requires caller-prepared ordered feature ranges and does not perform full genomic-region classification, peak calling, count construction or differential testing.'),
 'P133':(None,
  'Annotated ATAC-seq peaks with ChIPseeker using hg38 GENCODE version 30 annotations after the reported alignment, filtering, peak-calling and DESeq2 workflow.',
  'GENCODE v30 is an annotation release, not a ChIPseeker version. The software edition, TxDb preparation, gene-versus-transcript level, promoter/TSS window and remaining annotation arguments are unstated. A prepared nearest-feature API requires the caller to preserve the matching annotation population; it does not infer that population or reproduce genomic feature classes or the upstream ATAC workflow.'),
 'P139':(None,
  'Applied ChIPseeker annotatePeak with TxDb.Hsapiens.UCSC.hg19.knownGene annotations to retain enhancers while assembling the caQTL and no-effect SNP sets.',
  'The software edition, exact annotation package release, enhancer-label criterion and annotatePeak options are unstated. The caQTL/no-effect posterior thresholds and SNP-to-peak-center limit are separate selection rules, not ChIPseeker significance settings. The native nearest-feature component does not classify enhancers or reproduce this complete filtering procedure.'),
 'P195':(None,
  'Analyzed Hi-C loop-anchor coordinates with ChIPseeker using the reported UCSC.hg38.knownGene annotations after Juicer contact-map processing and resolution/long-range-contact quality checks.',
  'The software edition, exact annotation package/release, function, gene-versus-transcript level and promoter/feature-priority settings are unstated. Preserve the source annotation label without inventing a more specific TxDb package identity. Domain, boundary, loop and compartment analyses refer separately to prior methods; nearest-feature mapping does not call Hi-C loops or reproduce those procedures.')
}


def main():
    target=ROOT/'tools/literature/chipseeker-use-reviews.json';assert not target.exists()
    data=ROOT/'docs/literature/omics-2023-2026';protected=[data/'papers.json',data/'tool-inventory.json',data/'summary.json',ROOT/'tools/literature/reviewed-uses.json']
    before={p.relative_to(ROOT).as_posix():pin(p) for p in protected};drafts=[]
    existing=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];assert not any(r['tool_id']=='chipseeker' for r in existing)
    for pid,(version,summary,scope) in REVIEWS.items():
        audit=json.loads((ROOT/'build/literature-review/chipseeker-runtime/paper-role-audit-1'/(pid+'.json')).read_bytes());use=audit['current_use']
        drafts.append(dict(paper_id=pid,primary_paragraph=audit['primary']['paragraph_index'],review=dict(pmcid=audit['pmcid'],doi=audit['doi'],tool_id='chipseeker',
            evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=summary,implementation_scope_note=scope,
            version_reported_near_name=version,usage_context='genomic-annotation',supporting_evidence=[])))
    save(target,drafts)
    assert all(pin(ROOT/name)==wanted for name,wanted in before.items())
    print('Prepared seven source-bound ChIPseeker review drafts; no catalog, prior review or implementation count changed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source/catalog preservation checks')
    main()
