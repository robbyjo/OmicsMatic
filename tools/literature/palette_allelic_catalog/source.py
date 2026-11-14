# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Census all 250 pinned primary XMLs and bind each complete reviewed name population."""
import gzip, hashlib, json, re, xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, validate_supporting_evidence

REGISTRY = 'tools/literature/palette-allelic-named-software-registry.json'
CURATION = 'tools/literature/palette-allelic-paper-use-reviews.json'
EXPECTED = {('P054', 'wesanderson'): [93], ('P204', 'allelicseries'): [3,10,11,29,31,36,37,39,40,41,42,43,44,45,47,48,49,50,51,52,54,55,56,57,58,64]}

def definitions(root):
    rows = json.loads((root / REGISTRY).read_bytes())
    if [r['id'] for r in rows] != ['wesanderson', 'allelicseries']:
        raise ValueError('Unexpected named software definitions')
    return rows

def scan(root, papers):
    defs = definitions(root); rows = []; matches = []
    for paper in papers:
        raw = gzip.decompress((root / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        sha = hashlib.sha256(raw).hexdigest()
        if sha != paper['xml_sha256']:
            raise ValueError('Exact primary XML differs: ' + paper['paper_id'])
        xml = ET.fromstring(raw)
        dois = [e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type') == 'doi']
        if paper['doi'].lower() not in [s.lower() for s in dois if s]:
            raise ValueError('Primary DOI differs')
        ps = paragraphs(xml); found = {}
        for definition in defs:
            indices = []
            for p in ps:
                spans = list(re.finditer(definition['pattern'], p['match_text'], re.I))
                if spans:
                    indices.append(p['paragraph_index'])
                    matches.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], tool_id=definition['id'],
                                        paragraph_index=p['paragraph_index'], section=p['section'],
                                        paragraph_sha256=hashlib.sha256(p['text'].encode('utf8')).hexdigest(),
                                        matched_names=[m.group() for m in spans]))
            if indices: found[definition['id']] = indices
        rows.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=sha,
                         primary_paragraphs=len(ps), matched_paragraphs_by_tool=found))
    result = dict(status='PASS_ALL250_PINNED_PALETTE_AND_ALLELIC_PRIMARY_SOURCES', papers=len(rows),
                  name_pairs=sum(len(r['matched_paragraphs_by_tool']) for r in rows), matched_paragraphs=len(matches), census=rows, matches=matches)
    validate(result, papers)
    return result

def validate(census, papers, reviews=None, root=None):
    if census['status'] != 'PASS_ALL250_PINNED_PALETTE_AND_ALLELIC_PRIMARY_SOURCES' or (census['papers'], census['name_pairs'], census['matched_paragraphs']) != (250, 2, 27):
        raise ValueError('Incomplete primary-source population')
    if len(census['census']) != 250 or len(census['matches']) != 27:
        raise ValueError('Incomplete source census')
    actual = {}
    for paper, row in zip(papers, census['census'], strict=True):
        if any(paper[k] != row[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']):
            raise ValueError('Census paper identity differs')
        for tool, indices in row['matched_paragraphs_by_tool'].items(): actual[(paper['paper_id'], tool)] = indices
    if actual != EXPECTED:
        raise ValueError('Unexpected named primary-source population; review every source before admission')
    named = {(m['paper_id'], m['tool_id'], m['paragraph_index']): m for m in census['matches']}
    if len(named) != 27 or set(named) != {(p,t,i) for (p,t), indices in EXPECTED.items() for i in indices}:
        raise ValueError('Duplicate or missing named primary anchor')
    by_paper = {p['paper_id']:p for p in papers}
    for match in census['matches']:
        if any(match[k] != by_paper[match['paper_id']][k] for k in ['pmcid','doi']) or not match['matched_names']:
            raise ValueError('Named anchor identity or matched name differs')
    if reviews is not None:
        if len(reviews) != 2 or {(r['paper_id'],r['tool_id']) for r in reviews} != set(EXPECTED):
            raise ValueError('Incomplete individual paper-family reviews')
        by = {p['paper_id']: p for p in papers}
        for review in reviews:
            p = by[review['paper_id']]; key = (review['paper_id'], review['tool_id'])
            if any(review[k] != p[k] for k in ['pmcid','doi']): raise ValueError('Individual review identity differs')
            supports = review['supporting_evidence']
            if [a['paragraph_index'] for a in supports] != EXPECTED[key]: raise ValueError('Review omits or reorders complete named population')
            for support in supports:
                anchor = named[(*key, support['paragraph_index'])]
                if any(support[k] != anchor[k] for k in ['paragraph_sha256','section']): raise ValueError('Stale source support')
            anchor = named[(*key, review['evidence_paragraph_index'])]
            if review['evidence_paragraph_sha256'] != anchor['paragraph_sha256']: raise ValueError('Primary review anchor differs')
            if root is not None:
                raw = gzip.decompress((root / 'build/literature-review/xml' / (p['pmcid'] + '.xml.gz')).read_bytes())
                validate_supporting_evidence(paragraphs(ET.fromstring(raw)), [review], {r['id']:r for r in definitions(root)})
    return dict(primary_sources=250, new_paper_family_reviews=2, named_primary_paragraphs=27,
                wesanderson_reviewed_papers=1, allelicseries_reviewed_papers=1)
