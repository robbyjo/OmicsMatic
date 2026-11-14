# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind all QuPath roles to the complete, exact 250-paper primary-source census."""
import gzip, hashlib, json, re, xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, validate_supporting_evidence

CURATION = 'tools/literature/qupath-paper-use-reviews.json'
INTAKE = 'tools/literature/qupath051/paper-use-intake.json'
QUALIFIED_CENSUS = 'docs/literature/qupath051-workflow-development/CENSUS.json'
PATTERN = r'(?<![A-Za-z0-9_])QuPath(?![A-Za-z0-9_])'
EXPECTED = {'P009':[116,117], 'P020':[33], 'P021':[77,78], 'P024':[77], 'P040':[36],
            'P049':[40], 'P056':[55], 'P058':[36,37,73], 'P098':[75], 'P158':[30],
            'P161':[70], 'P226':[23]}

def reviews(root):
    intake = json.loads((root / INTAKE).read_bytes())
    rows = json.loads((root / CURATION).read_bytes())['reviews']
    if len(rows) != 12 or [r['paper_id'] for r in rows] != list(EXPECTED):
        raise ValueError('Incomplete or reordered individual QuPath source reviews')
    for original, row in zip(intake['reviews'], rows, strict=True):
        if any(row.get(k) != v for k,v in original.items()):
            raise ValueError('Qualified pre-admission source reading changed')
        anchor = original['supporting_evidence'][0]
        if (row['tool_id'], row['evidence_paragraph_index'], row['evidence_paragraph_sha256']) != ('qupath', anchor['paragraph_index'], anchor['paragraph_sha256']):
            raise ValueError('Individual review does not use its named analysis anchor')
    return rows

def scan(root, papers):
    rows, matches = [], []
    for paper in papers:
        raw = gzip.decompress((root / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        sha = hashlib.sha256(raw).hexdigest()
        if sha != paper['xml_sha256']: raise ValueError('Exact primary XML differs: ' + paper['paper_id'])
        xml = ET.fromstring(raw)
        dois = [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type') == 'doi' and e.text]
        if paper['doi'].lower() not in dois: raise ValueError('Primary DOI differs')
        indices = []
        for p in paragraphs(xml):
            if re.search(PATTERN, p['match_text'], re.I):
                indices.append(p['paragraph_index'])
                matches.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=sha,
                    paragraph_index=p['paragraph_index'], section=p['section'],
                    paragraph_sha256=hashlib.sha256(p['text'].encode('utf8')).hexdigest()))
        rows.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=sha, named_paragraphs=indices))
    result = dict(status='PASS_ALL250_PINNED_PRIMARY_SOURCES_AND_TWELVE_QUPATH_FAMILY_REVIEWS', papers=len(rows),
        qupath_papers=sum(bool(r['named_paragraphs']) for r in rows), named_primary_paragraphs=len(matches),
        census=rows, matches=matches, whole_paper_semantic_review_complete=False, new_tool_credit=0)
    validate(result,papers,reviews(root),root)
    return result

def validate(census, papers, rows, root=None):
    if census['status'] != 'PASS_ALL250_PINNED_PRIMARY_SOURCES_AND_TWELVE_QUPATH_FAMILY_REVIEWS' or (census['papers'],census['qupath_papers'],census['named_primary_paragraphs']) != (250,12,16):
        raise ValueError('Incomplete primary-source census')
    if len(census['census']) != 250 or len(census['matches']) != 16 or len(papers) != 250:
        raise ValueError('Incomplete census population')
    if len(rows) != 12 or [r['paper_id'] for r in rows] != list(EXPECTED): raise ValueError('Incomplete individual source review population')
    actual = {}
    for paper, record in zip(papers,census['census'],strict=True):
        if any(paper[k] != record[k] for k in ['paper_id','pmcid','doi','xml_sha256']): raise ValueError('Census identity differs')
        if record['named_paragraphs']: actual[paper['paper_id']] = record['named_paragraphs']
    if actual != EXPECTED: raise ValueError('Named primary paragraph population differs')
    named = {(m['paper_id'],m['paragraph_index']):m for m in census['matches']}
    if len(named) != 16 or set(named) != {(p,i) for p,indices in EXPECTED.items() for i in indices}: raise ValueError('Duplicate or missing primary anchor')
    by = {p['paper_id']:p for p in papers}
    for match in census['matches']:
        if any(match[k] != by[match['paper_id']][k] for k in ['pmcid','doi','xml_sha256']): raise ValueError('Primary anchor identity differs')
    for row in rows:
        paper = by[row['paper_id']]
        if any(row[k] != paper[k] for k in ['pmcid','doi']) or row['primary_xml_sha256'] != paper['xml_sha256'] or row['tool_id'] != 'qupath':
            raise ValueError('Individual source identity differs')
        if [a['paragraph_index'] for a in row['supporting_evidence']] != EXPECTED[row['paper_id']]: raise ValueError('Individual review omits or reorders named supports')
        classes = row['named_anchor_classification']
        for support in row['supporting_evidence']:
            anchor = named[(row['paper_id'],support['paragraph_index'])]
            if any(support[k] != anchor[k] for k in ['paragraph_sha256','section']): raise ValueError('Stale source support')
        expected_classes = {str(i):'image-analysis-role' for i in EXPECTED[row['paper_id']]}
        if row['paper_id'] == 'P021': expected_classes['78'] = 'downstream-output-table-analysis'
        if row['paper_id'] == 'P058': expected_classes['73'] = 'acknowledgement-only'
        if classes != expected_classes: raise ValueError('Acknowledgement/output reuse cannot be another image-analysis execution')
        anchor = named[(row['paper_id'],row['evidence_paragraph_index'])]
        if row['evidence_paragraph_sha256'] != anchor['paragraph_sha256'] or classes[str(row['evidence_paragraph_index'])] != 'image-analysis-role':
            raise ValueError('Primary review does not anchor image analysis')
        if root is not None:
            raw = gzip.decompress((root / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
            validate_supporting_evidence(paragraphs(ET.fromstring(raw)),[row],{'qupath':{'pattern':PATTERN}})
    if root is not None and census != json.loads((root / QUALIFIED_CENSUS).read_bytes()):
        raise ValueError('Fresh census differs from the qualified complete primary-source population')
    return dict(primary_sources=250,reviewed_paper_family_uses=12,named_primary_paragraphs=16,
                image_analysis_anchors=14,acknowledgement_anchors=1,downstream_output_table_anchors=1)
