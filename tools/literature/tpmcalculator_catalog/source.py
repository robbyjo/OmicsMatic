# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""All-250 primary census and separate P050 TPMCalculator / iDEP readings."""
import gzip, hashlib, json, re, xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

PATTERNS = {
    'tpmcalculator': r'(?<![A-Za-z0-9_])(?:TPMCalculator|TPMCaltulator)(?![A-Za-z0-9_])',
    'idep': r'(?<![A-Za-z0-9_])iDEP(?:\.[0-9]+(?:\.[0-9]+)*)?(?![A-Za-z0-9_])'}
MISSING_NOTE = ('Source-reviewed iDEP web-interface use only; no iDEP installation, runtime or implementation credit. '
    'Preserve the literal iDEP.96 without normalizing it to a release or inferring a source commit, deployed snapshot, DESeq2 version or complete defaults. '
    'Assess that exact edition, its license and permitted runtime before dependent analysis. SKIP3 skips unavailable/forbidden prerequisites; FAIL2 admits no complete result.')
DEFINITION = dict(id='idep', name='iDEP', pattern=PATTERNS['idep'], role='Interactive differential-expression web workflow',
                  current_coverage='missing', existing_source=None, coverage_note=MISSING_NOTE)
XML_SHA = '2fd4b3123b1b5930fcd5d6e8d5b6bc21edd6f3bcb572a76cbb50e802b77d2cef'
PARAGRAPH_SHA = '57c44a55cc007a9521bd21d67f94f8eebda2fe6ab80afe7fe9bbb5f9cb4a719a'

def audit(root):
    papers = json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    if len(papers) != 250 or len({p['pmcid'] for p in papers}) != 250:
        raise ValueError('All250 distinct primary sources required')
    census = []; target = None
    for paper in papers:
        raw = gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        if hashlib.sha256(raw).hexdigest() != paper['xml_sha256']:
            raise ValueError('Primary XML differs: '+paper['paper_id'])
        xml = ET.fromstring(raw)
        if paper['doi'].lower() not in [n.text.lower() for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type') == 'doi' and n.text]:
            raise ValueError('Primary DOI differs')
        ps = paragraphs(xml)
        row = {key: paper[key] for key in ['paper_id', 'pmcid', 'doi', 'xml_sha256', 'fulltext_url', 'license']}
        row['anchors'] = {}
        for name, pattern in PATTERNS.items():
            hits = [p for p in ps if re.search(pattern, p['match_text'], re.I)]
            if [p['paragraph_index'] for p in hits] != ([45] if paper['paper_id'] == 'P050' else []):
                raise ValueError('Complete primary name population differs: '+paper['paper_id']+'/'+name)
            row['anchors'][name] = [dict(paragraph_index=p['paragraph_index'], section=p['section'],
                paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in hits]
        if paper['paper_id'] == 'P050':
            target = paper, next(p for p in ps if p['paragraph_index'] == 45)
        census.append(row)
    paper, paragraph = target
    if paper['xml_sha256'] != XML_SHA or hashlib.sha256(paragraph['text'].encode()).hexdigest() != PARAGRAPH_SHA:
        raise ValueError('Exact P050 RNA-seq source required')
    for literal in ['iDEP.96', 'DESeq2 and default settings', 'Transcripts were quantified and normalized using TPMCalculator',
                    'Subread version 2.0.1', 'subread-align -d 50 -D 600–sortReadsByCoordinates', 'PRJNA921935']:
        if literal not in paragraph['text']:
            raise ValueError('Reported source literal differs: '+literal)
    anchor = dict(paragraph_index=45, section=paragraph['section'], paragraph_sha256=PARAGRAPH_SHA)
    common = dict(paper_id='P050', pmcid=paper['pmcid'], doi=paper['doi'], primary_xml_sha256=XML_SHA,
        evidence_paragraph_index=45, evidence_paragraph_sha256=PARAGRAPH_SHA, usage_context='analysis-method-description',
        supporting_evidence=[anchor], uncertainties=['Exact author inputs, TPMCalculator version/options and deployed iDEP source snapshot remain unreported.'])
    reviews = [dict(common, tool_id='tpmcalculator', version_reported_near_name=None,
        use_summary='Quantified and normalized RNA-seq transcripts with TPMCalculator. The paragraph separately reports Subread2.0.1 alignment, featureCounts gene read counts and DESeq2 defaults within iDEP.96; none establishes TPMCalculator options or its version.',
        reported_settings=dict(TPMCalculator_version=None, TPMCalculator_options=None, exact_GTF=None, output_selection=None,
            library_preparation='strand-specific paired-end 2x150 bp', biological_replicates_per_condition=3,
            upstream_Subread_version='2.0.1', upstream_alignment_options_literal='subread-align -d 50 -D 600–sortReadsByCoordinates',
            alignment_reference='ToxoDB-49 T. gondii ME49', BioProject='PRJNA921935', library_strandedness_is_not_a_TPMCalculator_option=True),
        implementation_scope_note='Native project comparison profile; no author edition, options or paper reproduction inferred.'),
        dict(common, tool_id='idep', version_reported_near_name='iDEP.96',
        use_summary='Conducted differential expression analysis with DESeq2 and default settings within the iDEP.96 web interface. Preserve that literal interface identity; the DESeq2 version, exact deployed source and complete effective defaults are not reported.',
        reported_settings=dict(interface_version_literal='iDEP.96', normalized_release=None, deployed_source_commit=None,
            differential_expression_method='DESeq2', settings_literal='default settings', DESeq2_version=None, complete_effective_defaults=None,
            upstream_count_source='featureCounts from Subread', transcript_TPMCalculator_step_is_separate=True),
        implementation_scope_note=MISSING_NOTE)]
    return dict(status='PASS_ALL250_PRIMARY_TPMCALCULATOR_AND_IDEP_SOURCE_ROLES', papers=250, census=census,
                reviews=reviews, primary_texts_retained_privately_not_relicensed_as_code=True,
                whole_paper_completeness_established=False, whole_goal_complete=False)
