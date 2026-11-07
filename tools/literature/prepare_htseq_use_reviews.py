# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Draft eight source-specific HTSeq roles, including the recovered HT-seq alias."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort

ROOT=Path(__file__).resolve().parents[2]
HERE=ROOT/'tools/literature'
DATA=ROOT/'docs/literature/omics-2023-2026'
BASE=ROOT/'build/literature-review/htseq-runtime'
NOTES={
 'P005':('Used HT-seq 0.6.0 to determine RNA-seq read counts after STAR alignment to mouse mm10. Subsequent gene filtering and differential expression used separate expression criteria and edgeR.',
    'HT-seq is the explicitly reported spelling of HTSeq in this Methods paragraph. The exact counting flags, annotation and strand/counting-unit choices are unstated. The native interval core is not the old counting command. FPKM filters, edgeR tests, correlations, clustering and Enrichr remain separate.', '0.6.0',[]),
 'P020':('Used HTSeq count 0.5.3 to calculate gene-level read counts after HISAT2 alignment to human hg38/GRCh38. DESeq2 and downstream gene-set analyses were separate.',
    'Paired-end sequencing and a stranded library do not establish the effective HTSeq strand flag or read-versus-fragment counting unit. The complete annotation/count command is unstated. Preserve the reported 0.5.3 edition; the current native interval core does not certify this historical counting environment.', '0.5.3',[]),
 'P044':('Used HTSeq-count 0.13.5 with GENCODE mouse vM25 annotation to assign HISAT2-aligned reads to genes. Converted the resulting counts to FPKM and log(FPKM+1) for sample-similarity analysis.',
    'Retain the supplied mm10/GENCODE vM25 identity and declare the effective counting flags rather than infer defaults. FPKM and log transformation are downstream operations, not raw-count outputs of the interval core. Historical whole-package equivalence and the complete paper pipeline are unverified.', '0.13.5',[]),
 'P099':('Used custom Python scripts with the HTSeq package to build peak-by-cell accessibility matrices. A peak was accessible in a cell when at least one read overlapped it. The paper describes both a merged peak-set benchmark and cell-type peak sets extended from summits.',
    'This is custom binary read-presence counting, not evidence that the standard htseq-count gene command was used. The HTSeq edition, effective barcode/read filters and complete ATAC script are unstated. Inspected EasySci repository references identify RNA gene/exon processing; they do not establish the exact ATAC script. Peak calling, score-per-million filtering and interval merging remain separate.',None,[45]),
 'P113':('Used HTSeq 0.11.1 to count exon-mapped RNA-seq reads by gene. A separate EU-seq workflow used HTSeq to count reads in defined gene-body regions with time-dependent extents of 30, 90 and 240 kb from the TSS.',
    'The RNA-seq step reports 0.11.1; the EU-seq step does not separately state an edition. TSS orientation/window construction, alignment/MAPQ/rRNA-tRNA filtering, TPM and DESeq2/custom fold-change normalization are separate. The native interval core requires already-derived blocks and regions and does not infer these paper-specific preparations.', '0.11.1',[78]),
 'P121':('Used HTSeq 0.13.5 to count reads intersecting ChIP-seq intervals after requiring properly paired reads with MAPQ greater than 20.',
    'Preserve the strict MAPQ>20 filter; a minimum-MAPQ option that retains 20 is not the same rule. A proper-pair flag alone does not establish whether reads or fragments were the counting unit. The effective HTSeq API/command and multi-overlap policy are unstated. Peak selection, depth normalization, z scores, clustering and enrichment remain separate.', '0.13.5',[]),
 'P148':('Used HTSeq to quantify gene-level read counts against a UCSC human transcriptome reference after STAR alignment to GRCh37/hg19. Described strand-aware FPKM calculation and Wilcoxon differential testing separately.',
    'The HTSeq edition, precise UCSC annotation and effective counting flags are unstated. Preserve genome/annotation identities and declare strand/counting units explicitly. FPKM normalization and Wilcoxon inference are downstream operations; the native interval core does not perform them.',None,[]),
 'P195':('Used the HTSeq count function, reported as version 0.13, to prepare a count table after HISAT2 alignment to hg38 and SAMtools sorting/conversion. Differential expression used limma with voom transformation.',
    'Version 0.13 is a major/minor statement, not evidence of the 0.13.5 patch release. HISAT2 RF strandness does not establish the HTSeq strand argument. Effective annotation, pairing and count flags remain explicit unknowns; limma-voom and BEDTools overlaps are separate. Do not substitute DESeq2 or silently select a historical patch version.', '0.13',[]),
}


def sha(raw):return hashlib.sha256(raw).hexdigest()


def main():
    papers=json.loads((DATA/'papers.json').read_bytes())
    verify_fixed_cohort(ROOT,DATA,papers)
    by={p['paper_id']:p for p in papers}
    audit=json.loads((BASE/'primary-use-audit-1/PRIMARY-USES.json').read_bytes())
    assert audit['papers_scanned']==250 and audit['source_papers']==8 and audit['catalogued_papers']==7
    rows={r['paper_id']:r for r in audit['records']}
    registry={r['id']:r for r in json.loads((HERE/'tool-registry.json').read_bytes())}
    drafts=[]
    addition=None
    for pid,(summary,scope,version,supporting) in NOTES.items():
        paper=by[pid];row=rows[pid]
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert sha(raw)==paper['xml_sha256']==row['xml_sha256']
        ps=paragraphs(ET.fromstring(raw));indexed={p['paragraph_index']:p for p in ps}
        index=row['selected']['evidence_paragraph_index'] if row['selected'] else 54
        anchor=indexed[index];digest=sha(anchor['text'].encode('utf8'))
        if row['selected']:assert digest==row['selected']['evidence_paragraph_sha256']
        else:
            assert pid=='P005' and 'HT-seq (v.0.6.0)' in anchor['text']
            addition=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],
                paragraph_index=index,section=anchor['section'],paragraph_sha256=digest,exact_named_alias='HT-seq',reported_version='0.6.0')
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='htseq',evidence_paragraph_sha256=digest,
            use_summary=summary,implementation_scope_note=scope,version_reported_near_name=version,
            usage_context='analysis-method-description',supporting_evidence=[dict(paragraph_index=i,section=indexed[i]['section'],paragraph_sha256=sha(indexed[i]['text'].encode('utf8'))) for i in supporting])
        validate_supporting_evidence(ps,[review],registry)
        drafts.append(dict(paper_id=pid,review=review))
    assert len(drafts)==8 and addition
    for name,value in [('htseq-use-reviews.json',drafts),('htseq-source-additions.json',dict(schema_version=1,
        status='checked-one-exact-HTSeq-hyphenated-software-name',declarations=[addition],
        frozen_collector_sha256=sha((HERE/'collect_omics_review.py').read_bytes()),
        reviewed_collector_sha256=sha((HERE/'collect_reviewed_omics.py').read_bytes()),
        registry_changed=False,implementation_credit=False))]:
        path=HERE/name;assert not path.exists();path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
    print('Drafted8 HTSeq roles with P005 alias and P099/P113 supporting pointers; no catalog or implementation credit changed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source role checks')
    main()
