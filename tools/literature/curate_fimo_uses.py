"""Review FIMO roles, stated statistical thresholds and separate custom workflows."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

ROOT = Path(__file__).resolve().parents[2]
USES = {
    'P003': (150, [], None,
        'Scanned hg38 with FIMO for JASPAR POU2F1-SOX2 MA1962.1 and FOSL1-JUND MA1142.1 sites at P < 1e-5. Selected sites overlapping cell-type-specific Micro-C TAD boundaries with bedtools, then replaced motifs with random sequence for Akita in silico mutagenesis.',
        'Keep the two supplied motif identifiers, hg38, P-value threshold and boundary selection distinct. Scanning does not implement TAD calling, the 20-kb cell-type boundary comparison, random-sequence mutagenesis, Akita or DeepExplainer contribution logos. Version, background and strand options are unstated.'),
    'P007': (55, [53, 56, 71], None,
        'Generated reference and alternative allele FASTA sequences centred on each variant, with length set by the longest supplied motif, and ran fimo --text --thresh 1e-4 for TFs expressed in chondrocytes. To compare both alleles when one lacked a reported match, separately recomputed PWM scores for all sequences and calibrated P values empirically against background scores; differential binding was human minus ancestral log score. A separate coding-TF analysis reported FDR > 0.05 for ZFAT/ZFHX4.',
        'Original FIMO text output does not compute q-values. Preserve the explicit P-value scan separately from the source custom score completion and empirical background tail, whose high correlation is not exact FIMO statistical equivalence. The separate coding-TF FDR statement does not establish that the text command produced q-values or specify its correction family. Motif length centring, expressed-TF selection and PBM 8-mer intensity comparisons are separate operations; do not fill a missing allele match with zero.'),
    'P053': (79, [91], '5.5.3',
        'Used FIMO 5.5.3 with JASPAR CORE vertebrate motifs to scan hg38 sequences of cCREs and differentially methylated regions. Averaged conservation, divergence or PhastCons scores at motif occurrences and used TFClass annotations. Later averaged motif PhastCons within each peak and weighted peak conservation by cell-type peak activity.',
        'The motif-occurrence coordinates feed distinct conservation summaries and cell-type weights. FIMO scanning does not supply PhastCons tracks, TFClass labels or those downstream summaries. P/q thresholds, background, strand and pseudocount options are unstated; no defaults are inferred.'),
    'P068': (64, [], None,
        'Detected PRDM9 motifs using FIMO and the PRDM9 A/A PWM supplied by Hinch et al. Scanned recombinant reads and a separate control set of 100,000 randomly sampled CCS reads; retained matches with q < 0.01.',
        'The stated cutoff is a q-value, not a P-value. The sampled CCS control cohort is distinct from a zero-order nucleotide background model. Preserve the original tested-site/motif family, sampling and both read cohorts; do not derive adjusted values from thresholded hits alone. Version, strand and pseudocount options are unstated.'),
    'P153': (68, [25], '5.5.9',
        'Quantified the effect of a variant on a transcription-factor motif using JASPAR motifs and FIMO 5.5.9 match scores. The results show TP73 MA0861.1 aligned to reference and alternative rs7696969 sequences and report log-odds-based motif-match P values.',
        'Score comparison is downstream of scE2G causal gene/cell-type prioritization. Do not infer a motif scan cutoff, background, strand choice or a causal effect from this use; enhancer-gene links, fine-mapping, PoPS and regulatory interpretation remain separate.'),
    'P215': (21, [40], None,
        'Quantified transcription-factor motif presence with FIMO on the background of ARID1A-bound regions lost in CSS cells, including the ZIC-family motif in lost enhancers. A separate HOMER de novo motif-enrichment analysis used shuffled input sequences and E < 0.001.',
        'Do not transfer HOMER shuffled-background enrichment or its E-value cutoff to FIMO occurrence scanning. The source names a genomic region cohort for FIMO, not an explicit nucleotide-frequency background file. No FIMO version, P/q threshold or strand/pseudocount options are given; cell-state comparisons and binding validation remain separate.'),
    'P217': (12, [30], '5.5.4 (MEME Suite)',
        'Used FIMO from MEME Suite 5.5.4 with JASPAR and HOCOMOCO PWMs to scan supplied sequences and retain motif occurrences at P < 0.001. Compared predicted transcription-factor binding in wild-type and TMEM216 promoter-variant sequence contexts, alongside separate expression and reporter experiments.',
        'Preserve P < 0.001 and the supplied motif databases; no q-value threshold is stated. Promoter annotations, reference/alternate sequence construction, luciferase, expression, cilia phenotypes and experimental significance tests are separate from FIMO. Background, strand and pseudocount settings are unstated.'),
}


def main():
    papers = {p['paper_id']: p for p in json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    packet = json.loads((ROOT / 'build/literature-review/fimo-runtime/paper-evidence.json').read_text(encoding='utf-8'))
    evidence = {(row['paper_id'], row['source']['paragraph_index']): row for row in packet}
    registry = json.loads(Path(__file__).with_name('tool-registry.json').read_text(encoding='utf-8'))
    tool = next(row for row in registry if row['id'] == 'fimo')
    path = Path(__file__).with_name('reviewed-uses.json'); document = json.loads(path.read_text(encoding='utf-8'))
    added = 0; pointers = 0
    for pid, (primary, supports, version, summary, boundary) in USES.items():
        paper = papers[pid]; raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']; sources = paragraphs(ET.fromstring(raw))
        for number in [primary, *supports]:
            row = evidence[pid, number]; assert sources[number - 1] == row['source']
            assert row['pmcid'] == paper['pmcid'] and row['doi'] == paper['doi']
            assert hashlib.sha256(row['source']['text'].encode()).hexdigest() == row['paragraph_sha256']
            assert re.search(tool['pattern'], row['source']['match_text'], re.I)
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='fimo', evidence_paragraph_sha256=evidence[pid, primary]['paragraph_sha256'],
                      use_summary=summary, implementation_scope_note=boundary)
        if version: review['version_reported_near_name'] = version
        if supports:
            review['supporting_evidence'] = [dict(paragraph_index=n, section=evidence[pid, n]['source']['section'], paragraph_sha256=evidence[pid, n]['paragraph_sha256']) for n in supports]
        pointers += len(supports)
        existing = [r for r in document['reviews'] if (r['pmcid'], r['tool_id']) == (review['pmcid'], review['tool_id'])]
        if existing: assert len(existing) == 1 and existing[0] == review
        else: document['reviews'].append(review); added += 1
    path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(dict(reviewed_papers=len(USES), reviews_added=added, new_supporting_pointers=pointers, total_reviews=len(document['reviews']), implementation_credit='none;FIMO validation pending')))


if __name__ == '__main__': main()
