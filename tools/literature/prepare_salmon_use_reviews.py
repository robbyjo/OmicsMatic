# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind eight manually reviewed Salmon use descriptions to exact primary paragraphs."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# The descriptions below were reviewed against the complete primary-source text.
DESCRIPTIONS = {
    'P108': (44, '1.5.2', [47, 51],
        'Quantified GTEx v8 RNA-seq for 48 tissues with Salmon 1.5.2 mapping-based quantification against GENCODE v38 transcripts plus the full GRCh38 genome as decoys, with mapping validation, sequence/GC bias correction and 50 inferential bootstraps. Developmental and adult brain panels instead used Salmon 1.8.0 with 50 bootstraps and the standard EM algorithm; adult brain explicitly inferred library type automatically.',
        'The 1.5.2 and 1.8.0 runs are separate analyses; the citation after1.8.0 is not a patch digit. GTEx optimizer/library-type choices and complete commands remain unstated. Adult brain names GRCh38 patch 13. Retain decoy/reference identities and inferential replicates. tximeta aggregation, TMM/DESeq2 transformations, residualization, fishpond variance and isoform fractions are separate downstream operations.'),
    'P151': (48, None, [],
        'Mapped human and mouse RNA-seq reads to transcripts associated with hg38 and mm10 using Salmon. Imported transcript counts and collapsed to gene counts with tximport for separate DESeq2 differential analyses.',
        'The Salmon edition, transcript annotation, index/decoy construction, library type and complete options are unstated. Genome assembly labels do not establish genome-coordinate alignments. A separate TL1A experiment reused downloaded gene counts rather than documenting another Salmon run. Differential tests and tximport aggregation remain separate.'),
    'P190': (32, None, [37],
        'Used Salmon transcript TPM and normalized isoform abundance within each gene to form percentage phenotypes for sQTL comparisons. MAJIQTL combines these isoform representations with MAJIQ splice-junction/event quantifications.',
        'The Salmon edition, reference/index, mapping mode, optimizer and complete command are unstated. TPM, estimated fragment counts and within-gene isoform percentages have distinct units. Salmon transcript estimates do not implement MAJIQ local splicing/IR detection, MOCCASIN correction, weighted gene testing or the downstream composite models.'),
    'P197': (13, '1.0.0', [],
        'Used Salmon 1.0.0 in the reported SAF Pattern workflow to map paired-end 150 bp RNA-seq reads against GRCh38 genome/transcriptome resources before DESeq2 differential expression.',
        'The phrase SAF Pattern does not provide a complete command or library-type code. Exact annotation, decoys, bias corrections, aggregation and the stated gene-count/FPKM derivation are unspecified; do not invent them or treat FPKM as raw counts. DESeq2 and clusterProfiler steps remain separate.'),
    'P201': (8, '1.9.0', [],
        'Obtained transcript TPM with Salmon 1.9.0 and supplied these abundances to SUPPA2 to calculate sample-wise alternative-splicing PSI for events generated from GRCh38 Ensembl release 110 annotations.',
        'The citation 40 is not part of the 1.9.0 edition. The stated Ensembl annotation is used for SUPPA2 events and does not fully specify the Salmon index. Library type, mapping/bias/optimizer options and commands are unstated. SUPPA2 event generation, PSI denominators, missingness and expression filters are downstream, not Salmon quantification.'),
    'P223': (10, None, [],
        'Used Salmon to generate read-count estimates from short-read RNA-seq in a workflow that also used HISAT2, StringTie and Ballgown. Performed differential expression with edgeR and gene-symbol assignment with biomaRt.',
        'No Salmon edition, index/reference annotation, command, quantification mode or gene-aggregation procedure is stated. The paper does not establish whether Salmon consumed raw reads or transcriptome alignments from the other tools. Salmon estimates remain distinct from separately extracted Rsubread junction counts and downstream differential expression.'),
    'P226': (19, '1.8.0', [],
        'After FastQC and Trimmomatic 0.39, mapped trimmed RNA-seq reads to a human v38 transcriptome index with Salmon 1.8.0 for transcriptomic analysis of TMEM184B.',
        'The paper calls downstream values transcript counts in the form of transcripts per million and names DESeq2 on Galaxy. TPM is a relative abundance measure, not raw integer counts; the exact DESeq2 input/transformation is unresolved and must not be silently equated. Exact transcript annotation, decoys, library type, inference and bias options are unstated; differential testing, Panther and plotting are separate.'),
    'P227': (35, '1.1.0', [9, 57, 64],
        'Built a GRCh38 cDNA Salmon 1.1.0 index and generated transcript-level RNA-seq quantifications, then obtained transcript/gene matrices with tximport 1.14.2 and annotation/cache packages. The captured linked rna_seq_interval_5591 Salmon module specifies paired ISR reads, sequence/GC/position bias corrections, --useVBOpt and 100 bootstraps. Examined ROPN1B transcript-specific misexpression in inversion carriers.',
        'The linked module is separately captured code evidence, not proof that every paper run used that exact mutable-branch state or external conda/index contents. STAR/featureCounts gene counts remain distinct from Salmon transcript estimates. The reported total TPM across all transcripts does not unambiguously define a within-gene percentage denominator; do not replace it with a generic isoform fraction. 4778 count samples and 4732 RNA-seq participants are different reported populations.'),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit', type=Path, required=True); parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); audit = json.loads(args.audit.read_bytes()); assert audit['source_papers_scanned'] == 250
    out = args.out.resolve(); assert out == ROOT / 'tools/literature/salmon-use-reviews.json' and not out.exists()
    rows = []
    for paper in audit['papers']:
        primary, version, support, summary, note = DESCRIPTIONS[paper['paper_id']]
        by_index = {p['paragraph_index']:p for p in paper['matches']}
        assert primary in by_index and all(i in by_index for i in support)
        assert all(hashlib.sha256(p['text'].encode()).hexdigest() == p['sha256'] for p in by_index.values())
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='salmon', evidence_paragraph_sha256=by_index[primary]['sha256'],
            use_summary=summary, implementation_scope_note=note, version_reported_near_name=version,
            supporting_evidence=[dict(paragraph_index=i, paragraph_sha256=by_index[i]['sha256'], section=by_index[i]['section']) for i in support])
        if paper['paper_id'] == 'P108':
            # The registry deliberately requires capital-S Salmon to avoid fish.
            # The adult-brain paragraph writes lowercase salmon; link its exact
            # method details to the named developmental-brain quantification.
            pointer = next(p for p in review['supporting_evidence'] if p['paragraph_index'] == 51)
            pointer.update(evidence_role='method-details',
                named_anchor=dict(paragraph_index=47, paragraph_sha256=by_index[47]['sha256'], section=by_index[47]['section']),
                shared_terms=['mapping-based', 'tximeta', 'isoform'],
                relation_note='The adult-brain paragraph explicitly names lowercase salmon1.8.0, mapping-based quantification and the same GENCODE/GRCh38 decoy resources as the named developmental-brain method. It separately supplies EM, automatic library inference, bias correction and50 bootstrap details; these are not propagated to GTEx.')
        rows.append(dict(paper_id=paper['paper_id'], primary_paragraph=primary, reported_version=version, review=review))
    assert len(rows) == 8
    out.write_text(json.dumps(rows, indent=2)+'\n', encoding='utf-8', newline='\n')
    print('Eight manually reviewed descriptions pinned; six supporting paragraphs. Catalog unchanged.')


if __name__ == '__main__': main()
