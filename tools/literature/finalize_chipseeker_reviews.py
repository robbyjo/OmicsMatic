# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Finish seven source-bound usage drafts against the implemented annotation/native contracts."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence

SCOPES={
'P020': 'The GRCh38 ChIP-seq analysis reports the 1.26.2 edition and -2 kb/+1 kb promoter definition, but not the precise TxDb/annotation resource, remaining annotation arguments or feature priorities. The original 1.26.2 adapter supports this explicit promoter window and genomic labels on a caller-supplied matching TxDb; the native prepared method separately audits nearest TSS assignments. No unreported settings are inferred, and differential binding, normalization and the complete ChIP_Zwart pipeline are not reproduced.',
'P043': 'The precise ChIPseeker function, gene/TxDb resource and anchor/orientation of the 3 kb flanks are unstated. The exact 1.34.1 adapter exposes original annotation and flanking-gene options, but this description does not identify a particular tssRegion, all-neighbor rule or nearest-gene-only call. Expression filtering, Wilcoxon accessibility testing, cCRE-gene correlation and HOMER enrichment remain separate analyses. Caller choices and original failures stay explicit.',
'P044': 'The paper reports ChIPseeker 1.22.1 and mm10 alignment, but omits the exact TxDb/resource, function, promoter window, gene-versus-transcript level and feature priorities. The original 1.22.1 adapter can produce genomic labels and distribution tables from explicit matching inputs; those unknown preparation/options are not inferred. Figure pixels and the full CUT&Tag pipeline are not reproduced.',
'P124': 'The ChIPseeker edition, exact hg38 annotation/TxDb resource, gene-versus-transcript level, distance/strand/overlap options and promoter window are unstated. The adapter requires an explicit supported edition and caller-supplied database; original genomic labels and nearest results are retained, with a separate scoped native nearest audit. Peak calling, union-peak count construction and DESeq2 inference are separate and are not supplied by this annotation command.',
'P133': 'GENCODE v30 is the annotation release, not the ChIPseeker software version. The software edition, TxDb preparation, gene-versus-transcript level, promoter window and remaining arguments are unstated. The original annotation adapter requires the caller to preserve the hg38/GENCODE v30 resource identity and declare an edition/settings; native nearest mapping alone does not construct that annotation population. The upstream ATAC, count and differential-testing pipeline is not reproduced.',
'P139': 'The exact ChIPseeker edition, annotation-package release, annotatePeak options and precise enhancer mask for the caQTL/no-effect sets are unstated. The adjacent paragraph specifies distal/intron categories for the separate random-reference enhancer set; do not silently transfer that mask or infer exact software label strings for every set. The adapter retains original annotations and supports caller-declared literal prefix selection, with no invented enhancer rule. Posterior thresholds, SNP-to-peak-center distance, matched downsampling and correlation are separate analyses.',
'P195': 'The software edition, exact annotation package/release, function, gene-versus-transcript level and promoter/priorities are unstated. Preserve the reported UCSC.hg38.knownGene label without inventing a more specific package identity. Original annotation of explicit prepared anchors and a matching caller-supplied TxDb is available; Juicer contact-map processing, QC and prior-method domain/boundary/loop/compartment calling remain separate. This adapter does not call Hi-C loops or reconstruct those pipelines.'}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    target=ROOT/'tools/literature/chipseeker-use-reviews.json';before=target.read_bytes();(out/'drafts-before.json').write_bytes(before)
    drafts=json.loads(before);assert {d['paper_id'] for d in drafts}==set(SCOPES)
    registry={r['id']:r for r in json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes())}
    source_rows=[]
    for draft in drafts:
        review=draft['review'];audit=json.loads((ROOT/'build/literature-review/chipseeker-runtime/paper-role-audit-1'/(draft['paper_id']+'.json')).read_bytes())
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(review['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==audit['xml_sha256']
        ps=paragraphs(ET.fromstring(raw));by={p['paragraph_index']:p for p in ps};primary=by[draft['primary_paragraph']]
        assert hashlib.sha256(primary['text'].encode('utf-8')).hexdigest()==review['evidence_paragraph_sha256']
        review['implementation_scope_note']=SCOPES[draft['paper_id']]
        if draft['paper_id']=='P139':
            review['use_summary']='Applied ChIPseeker annotatePeak with TxDb.Hsapiens.UCSC.hg19.knownGene annotations to retain enhancers for caQTL/no-effect SNP sets. Separately sampled 25,000 reference peaks and retained distal/intron categories, yielding approximately 21,000 putative reference enhancers.'
            detail=by[33];assert detail['section']==primary['section'] and 'distal' in detail['text'] and 'intron' in detail['text']
            pointer=lambda paragraph:dict(paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode('utf-8')).hexdigest())
            review['supporting_evidence']=[dict(**pointer(detail),evidence_role='method-details',named_anchor=pointer(primary),shared_terms=['enhancers'],
                relation_note='The adjacent paragraph continues enhancer categorization in the same Methods section and specifies the random-reference selection. It does not establish that the same mask applies to every caQTL/no-effect set.')]
        validate_supporting_evidence(ps,[review],registry)
        source_rows.append(dict(paper_id=draft['paper_id'],pmcid=review['pmcid'],doi=review['doi'],xml_sha256=audit['xml_sha256'],
            primary_paragraph=draft['primary_paragraph'],paragraph_sha256=review['evidence_paragraph_sha256'],supporting_pointers=len(review['supporting_evidence'])))
    save(target,drafts)
    save(out/'COMPLETED.json',dict(status='finalized-seven-primary-bound-ChIPseeker-review-drafts-not-applied',source=pin(Path(__file__)),drafts=pin(target),source_proof=source_rows,
        supporting_pointers=1,prior_review_table_modified=False,implementation_count_modified=False))
    print('Finalized seven drafts, including one checked P139 method-detail pointer; prior review table/catalog unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-anchor checks')
    main()
