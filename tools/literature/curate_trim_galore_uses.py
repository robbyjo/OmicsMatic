"""Review the 11 frozen primary-paper uses without inferring trimming options."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

ROOT = Path(__file__).resolve().parents[2]
# Primary paragraph, supporting paragraphs, version, actual role, implementation boundary.
USES = {
    'P023': (64, [], '0.5.0',
        'Removed adapters from downloaded RPE1 and Jurkat ATAC-seq SRA reads with TrimGalore 0.5.0 before Bowtie2 alignment to hg19 and MACS3 hmmratac peak calling. Other cell types used supplied ENCODE peaks.',
        'Adapter sequences, quality and length options are unstated. The native finalization/hardtrim helper does not remove adapters or establish 0.5.0 parity. Keep supplied peaks, reference liftover, alignment, duplicate handling and peak calling separate.'),
    'P027': (36, [55], None,
        'Trimmed paired RIP-seq adapters with TrimGalore 0.6.2 before CLAPAnalysis. A distinct nf-core/rnasplice 1.0.4 workflow used TrimGalore 0.6.7 for adapter/low-quality RNA-seq filtering, followed by STAR and splicing analysis; FastQC versions differ between these workflows.',
        'Preserve the separate 0.6.2 RIP-seq and 0.6.7 nf-core contexts. No adapter or quality thresholds are stated here. Native post-Cutadapt operations alone do not implement CLAPAnalysis, Nextflow, nf-core, alignment or rMATS.'),
    'P037': (33, [47], '0.6.4',
        'Removed adapters and low-quality sequence using TrimGalore 0.6.4 for shotgun microbial DNA and RNA. Discarded the entire paired observation if either mate was shorter than 50 bases for DNA or 100 bases for RNA; used separate pre/post FastQC checks.',
        'Keep the DNA 50-base and RNA 100-base pair cutoffs distinct. The native helper can apply explicitly supplied final pair filters after Cutadapt, but adapter/quality processing, 0.6.4 behavior, kallisto quantification and MAG/rRNA selection remain separate.'),
    'P044': (50, [52], '0.6.7',
        'Used TrimGalore 0.6.7 for ATAC-seq adapter removal before BWA. A separate RNA-seq workflow explicitly used quality 20, FastQC, minimum length 20 and stringency 1 before HISAT2 and HTSeq quantification.',
        'Do not transfer the stated RNA options to the ATAC assay. Minimum length is a filter and stringency is adapter-overlap length. Native finalization supplies neither quality/adapter trimming nor FastQC; Tn5 shifts, mapping filters, counts and FPKM are downstream.'),
    'P046': (34, [], '0.6.6',
        "Trimmed paired WGBS reads using TrimGalore 0.6.6 with Illumina adapter removal, plus 15-base fixed removal from the 5-prime end of read 2 and 3-prime end of read 1; assessed reads with FastQC before and after trimming.",
        'This is WGBS; do not infer RRBS mode or its filled-in-base rules. The native ordinary finalization helper can apply the explicit fixed cuts to post-Cutadapt reads, subject to its original length guards. Adapter choice, actual 0.6.6 parity, Bismark alignment and bisulfite conversion analysis remain separate.'),
    'P065': (70, [72], '0.6.7',
        'Removed adapters with TrimGalore 0.6.7 in separate Omni-ATAC-seq and ChIP-seq workflows before bwa-mem2 mapping and subsequent assay-specific processing.',
        'Trimming options are unspecified. Native finalization is a bounded helper and does not remove adapters. Preserve the separate assay filtering, duplicate handling, fragment/shift rules and MACS2 peak stages.'),
    'P075': (52, [], '0.3.1',
        'Removed sequencing adapters with Trim Galore 0.3.1 before FLASH 1.2.11 paired-read reconstruction in single-allele MCC analysis; BLAT and custom SNP/ligation-junction analysis followed.',
        'Adapter removal does not merge read pairs. Native fixed-end/final filtering methods do not establish 0.3.1 defaults, FLASH overlap merging, alternate-genome mapping, duplicate selection or allele-specific normalization.'),
    'P099': (34, [], None,
        'After custom EasySci barcode and adapter extraction, applied an additional default TrimGalore step described as removing poly-A sequence and low-quality bases before STAR mapping, duplicate handling and cell assignment.',
        'The paper does not state a version or an explicit --poly_a flag. Preserve the reported default-step wording; do not equate it with the separate explicit poly-A mode or infer unreported adapter/quality settings. Native ordinary finalization does not perform EasySci demultiplexing, poly-A or quality trimming.'),
    'P122': (58, [], None,
        'Before Capture Micro-C mapping with HiC-Pro, reports trimming all raw reads to 50 bases with the wording "TrimGalore (Cutadapt, v0.6.10)".',
        'The software/version attribution and exact cropping flag are ambiguous in the source. Keeping the first 50 bases differs from a 50-base minimum-length filter. Native hardtrim5 supplies the former operation when explicitly selected, but no paper flag or historical Cutadapt version is inferred; contact processing and reference construction remain separate.'),
    'P163': (43, [45, 47], '0.3.7',
        'Used TrimGalore 0.3.7 for adapter removal in CUT&Tag, ChIP-seq and Omni-ATAC-seq workflows after FASTQ generation, with separate FastQC assessment before downstream mapping and peak analysis.',
        'The reported 35/75-base read lengths describe sequencing inputs rather than an instructed trim target. Adapter, paired and quality options are unstated. Native ordinary finalization/hardtrim does not remove adapters or establish 0.3.7 orchestration/defaults; assay-specific mapping, fragment handling and peaks remain separate.'),
    'P215': (11, [20], None,
        'Trimmed RNA-seq adapters with Trim Galore before Kallisto quantification. Separate ChIP-seq/ATAC-seq methods also report Trim Galore adapter trimming and FastQC assessment before alignment and assay-specific processing.',
        'No version or exact trimming thresholds are given. Do not transfer RNA assumptions to chromatin assays. The native helper requires an already completed Cutadapt phase; Kallisto, differential expression, BWA mapping, fragment/offset rules and peaks remain separate.'),
}


def main():
    papers = {p['paper_id']: p for p in json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    packet = json.loads((ROOT / 'build/literature-review/trimgalore-runtime/paper-evidence.json').read_text(encoding='utf-8'))
    evidence = {(p['paper_id'], p['source']['paragraph_index']): p for p in packet}
    assert len(evidence) == 18 and {p for p, _ in evidence} == set(USES)
    registry_path = Path(__file__).with_name('tool-registry.json')
    registry = json.loads(registry_path.read_text(encoding='utf-8'))
    tool = next(row for row in registry if row['id'] == 'trimgalore')
    assert tool['current_coverage'] == 'missing'
    assert tool['pattern'] in (r'(?<![A-Za-z0-9_])(?:Trim\s*Galore)(?![A-Za-z0-9_])', r'(?<![A-Za-z0-9_])(?:Trim[\s_]*Galore)(?![A-Za-z0-9_])')
    tool['pattern'] = r'(?<![A-Za-z0-9_])(?:Trim[\s_]*Galore)(?![A-Za-z0-9_])'
    new = []
    for pid, (primary, support, version, summary, boundary) in USES.items():
        paper = papers[pid]
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        sources = paragraphs(ET.fromstring(raw))
        for number in [primary, *support]:
            e = evidence[pid, number]
            assert e['doi'] == paper['doi'] and e['pmcid'] == paper['pmcid'] and sources[number - 1] == e['source']
            assert hashlib.sha256(e['source']['text'].encode()).hexdigest() == e['paragraph_sha256']
            assert re.search(tool['pattern'], e['source']['match_text'], re.I)
        row = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='trimgalore',
                   evidence_paragraph_sha256=evidence[pid, primary]['paragraph_sha256'], use_summary=summary, implementation_scope_note=boundary)
        if version is not None:
            row['version_reported_near_name'] = version
        if support:
            row['supporting_evidence'] = [dict(paragraph_index=n, section=evidence[pid, n]['source']['section'],
                                               paragraph_sha256=evidence[pid, n]['paragraph_sha256']) for n in support]
        new.append(row)
    # The same source attaches an ambiguous version to its wrapper/Cutadapt
    # wording. Qualify both displayed tool rows rather than leaving a misleading
    # standalone Cutadapt 0.6.10 execution claim beside the reviewed wrapper.
    p122 = papers['P122']; source = evidence['P122', 58]
    cutadapt = next(row for row in registry if row['id'] == 'cutadapt')
    assert re.search(cutadapt['pattern'], source['source']['match_text'], re.I)
    new.append(dict(pmcid=p122['pmcid'], doi=p122['doi'], tool_id='cutadapt',
        evidence_paragraph_sha256=source['paragraph_sha256'],
        use_summary='The Capture Micro-C methods describe trimming raw reads to 50 bases with the wording "TrimGalore (Cutadapt, v0.6.10)" before HiC-Pro mapping.',
        implementation_scope_note='The source does not establish a separate standalone Cutadapt command or resolve whether v0.6.10 labels the wrapper. Preserve this attribution ambiguity; no standalone Cutadapt 0.6.10 profile or cropping flag is inferred. A 50-base crop differs from minimum-length filtering; the native Trim Galore helper does not perform adapter alignment.',
        version_reported_near_name='0.6.10 (source wording; wrapper/Cutadapt attribution ambiguous)'))
    path = Path(__file__).with_name('reviewed-uses.json')
    document = json.loads(path.read_text(encoding='utf-8'))
    old = {(r['pmcid'], r['tool_id']): r for r in document['reviews']}
    added = updated = 0
    for row in new:
        key = row['pmcid'], row['tool_id']
        if key not in old:
            document['reviews'].append(row); added += 1
        elif old[key] != row:
            assert row['pmcid'] == papers['P163']['pmcid'] and old[key]['evidence_paragraph_sha256'] == row['evidence_paragraph_sha256']
            document['reviews'][document['reviews'].index(old[key])] = row; updated += 1
    tool['coverage_note'] = 'The original pre-expansion source inventory lacked this workflow. Current source adds a bounded original Perl0.6.7/0.6.10 adapter with Cutadapt5.2 and optional originalFastQC0.13.0, plus native ordinary post-Cutadapt finalization and first/last-base hardtrim. See docs/literature/omics-2023-2026/TRIM_GALORE_WORKFLOWS.md and its exact artifact/validation records. Historical versions,RRBS,explicit poly-A and whole-paper parity remain unvalidated.'
    path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    registry_path.write_text(json.dumps(registry, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(dict(added_reviews=added, updated_reviews=updated, reviewed_papers=len(USES), reviewed_tool_uses=len(new), primary_and_supporting_paragraphs=len(evidence), total_reviews=len(document['reviews']))))


if __name__ == '__main__':
    main()
