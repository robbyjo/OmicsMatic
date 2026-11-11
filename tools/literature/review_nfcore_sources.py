# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Draft six nf-core roles and audit all 250 original XMLs; never change catalog or implementation credit."""
import argparse, gzip, hashlib, json, re, unicodedata, xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from collect_omics_review import paragraphs, words, matching_words
from catalog_fixed_cohort import verify_fixed_cohort

BASE = ROOT / 'build/literature-review/nfcore-runtime'
PATTERN = r'\bnf[\s-]*core\b|\bnf_core\b|\bnf-co\.re\b|github\.com/nf-core/'
ROLES = {
    'P002': dict(pipeline='eager', pipeline_version='2.4.4', nextflow_version='21.04.1',
        use='Used nf-core/eager2.4.4 with Nextflow21.04.1 for ancient T. pallidum screening and read processing, including human-read removal and candidate taxonomic assessment with HOPS/MALT.',
        boundary='Reference databases, ancient-DNA library metadata, candidate read assessment and unassessable samples require explicit handling. Preserve the reported mapping-quality filter and original damage/deduplication steps. A workflow version does not establish the author source commit.'),
    'P027': dict(pipeline='rnasplice', pipeline_version='1.0.4', nextflow_version='24.04.4',
        use='Used nf-core/rnasplice1.0.4 with Nextflow24.04.4 and rMATS4.1.2 for C. elegans alternative-splicing analysis against ce11/WBcel235, after the reported FastQC, trimming and STAR mapping steps.',
        boundary='Retain deltaPSI>=0.05 or deltaPSI<=-0.05 with P<0.05; do not replace the P criterion with an FDR criterion. Complete contrast design and input preparation remain explicit.'),
    'P032': dict(pipeline='chipseq', pipeline_version='2.0.0', nextflow_version=None,
        use='Used nf-core/chipseq2.0.0 with reported BWA mapping and default trimming/alignment/filtering for human and mouse ChIP-seq reads.',
        boundary='The Nextflow edition is unreported. Human-to-mouse spike-in normalization and the reported MACS2 background, threshold and blacklist choices require separate explicit handling; nearby tool versions do not establish every pipeline-internal version.'),
    'P050': dict(pipeline='chipseq', pipeline_version=None, nextflow_version=None,
        use='Used the nf-core ChIP-seq pipeline with reported standard parameters for intersample comparison; HOMER annotatePeaks provided genomic-feature distributions.',
        boundary='Pipeline and Nextflow versions are unreported. Do not infer them from publication date or neighboring Bowtie2/MACS2/deepTools mentions. Plotting, annotation and other downstream commands remain distinct steps.'),
    'P169': dict(pipeline='chipseq', pipeline_version='1.2.1', nextflow_version=None,
        use='Used nf-core/chipseq1.2.1 with reported default parameters to process ChIPmentation for six histone marks and CTCF across six colorectal-cancer cell lines.',
        boundary='The Nextflow edition and complete commands are unreported. Experimental ChIPmentation preparation is distinct from computational processing; the public yeast control fixture does not reproduce these human data.'),
    'P228': dict(pipeline='hic', pipeline_version='2.1.0', nextflow_version=None,
        use='Used nf-core/hic2.1.0 for Arima Hi-C processing against GRCh38 with HiCExplorer TAD calling and minimum mapping quality10, followed by the reported multiresolution contact-matrix and downstream differential analyses.',
        boundary='Retain --digestion arima, --tads_caller hicexplorer, --min_mapq 10 and --genome GRCh38. HiC-Pro/Bowtie2 settings, Cooler resolutions and balancing, diffHic/edgeR, differential TAD and compartment analyses require their own declared contracts. Nextflow edition is unreported.')}


def normalized(text):
    return unicodedata.normalize('NFKC', text).translate(str.maketrans({c: '-' for c in '‐‑‒–—−'}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    papers_path = data / 'papers.json'
    reviews_path = ROOT / 'tools/literature/reviewed-uses.json'
    before = dict(papers=pin(papers_path), reviews=pin(reviews_path))
    papers = json.loads(papers_path.read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    intake = json.loads((BASE / 'source-intake-1/COMPLETED.json').read_bytes())
    primary = {row['paper_id']: row for row in intake['primary_papers']}
    assert set(primary) == set(ROLES) and len(papers) == 250
    census, extra_body, evidence, drafts = [], [], [], []
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        xml = ET.fromstring(raw)
        assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type') == 'doi').lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        hits = [p for p in ps if re.search(PATTERN, normalized(p['match_text']), re.I)]
        for p in hits:
            census.append(dict(paper_id=paper['paper_id'], paragraph_index=p['paragraph_index'], section=p['section'],
                paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()))
        covered = {p['text'] for p in ps}
        for index, element in enumerate(xml.findall('.//body//p'), 1):
            text = words(element)
            if text not in covered and re.search(PATTERN, normalized(matching_words(element)), re.I):
                extra_body.append(dict(paper_id=paper['paper_id'], body_p_ordinal=index, text_sha256=hashlib.sha256(text.encode()).hexdigest(),
                    reason='Outside standard analysis paragraph extraction; inspect scope before treating as a method use.'))
        evidence.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256']))
        if paper['paper_id'] in primary:
            row = primary[paper['paper_id']]
            p = next(p for p in ps if p['paragraph_index'] == row['paragraph_index'])
            assert hashlib.sha256(p['text'].encode()).hexdigest() == row['paragraph_sha256']
            assert paper['xml_sha256'] == row['xml_sha256'] and p in hits
            drafts.append(dict(paper_id=paper['paper_id'], doi=paper['doi'], primary=row,
                **ROLES[paper['paper_id']], implementation_status='missing', catalog_credit=False,
                review_state='DRAFT_SOURCE_ROLE_ONLY_NOT_APPLIED'))
    observed = sorted({r['paper_id'] for r in census})
    current = sorted(p['paper_id'] for p in papers if any(t['tool_id'] in ['nfcore', 'nf-core'] for t in p['tools']))
    assert pin(papers_path) == before['papers'] and pin(reviews_path) == before['reviews']
    save(out / 'role-drafts.json', drafts)
    save(out / 'COMPLETED.json', dict(status='PASS250_SOURCE_HASHES_AND_NFCORE_CENSUS_DRAFT_ONLY', sources=evidence,
        pattern=PATTERN, matching_paragraphs=census, matching_papers=observed, existing_registry_papers=current,
        additional_candidate_papers=sorted(set(observed) - set(current)), body_hits_outside_standard_paragraphs=extra_body,
        role_drafts=pin(out / 'role-drafts.json'), reviewed_roles_added=0, catalog_unchanged=before,
        source_pin=pin(Path(__file__)), new_tool_integrations=0, current_new_integrations=99,
        original_pipeline_runtime_validation_pending=True, whole_goal_complete=False))
    print(json.dumps(dict(papers_verified=250, matching_papers=observed, current_registry_papers=current,
        matching_paragraphs=len(census), extra_body_hits=len(extra_body), drafts=len(drafts), catalog_changed=False)), flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable complete source, pointer or catalog-preservation checks')
    main()
