"""Recheck all 250 primary XML sources; never export their full paragraphs."""
from pathlib import Path
import copy
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, words
from gsea_catalog.primary import identities, EXTRACTOR_SHA
from gprofiler_catalog.decisions import SPECS

ROOT = Path(__file__).resolve().parents[3]
NAMED = re.compile(r'(?<![A-Za-z0-9_])(?:g:Profiler|gprofiler2?|gProfileR)(?![A-Za-z0-9_])', re.I)
BROADER = re.compile(r'g:GOSt|gprofiler_archive3|g:SCS|\bg_SCS\b|\bgost\s*\(', re.I)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pointer(block):
    return dict(paragraph_index=block['paragraph_index'], section=block['section'],
                paragraph_sha256=pin(block['text'].encode('utf-8'))['sha256'])


def audit(cache):
    require(pin((ROOT / 'tools/literature/collect_omics_review.py').read_bytes())['sha256'] == EXTRACTOR_SHA,
            'Changed frozen primary paragraph extractor')
    sources = identities()
    require(len(sources) == 250, 'Require the full frozen 250-paper corpus')
    selected, named, tables, broader = {}, [], [], []
    for identity in sources:
        path = Path(cache) / (identity['pmcid'] + '.xml.gz')
        require(path.stat().st_size <= 64 * 1024 * 1024, 'Compressed XML exceeds capacity')
        with gzip.open(path, 'rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024 and pin(raw)['sha256'] == identity['xml_sha256'],
                'Changed primary XML: ' + identity['paper_id'])
        tree = ET.fromstring(raw)
        dois = [x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type') == 'doi']
        require(len(dois) == 1 and dois[0].lower() == identity['doi'].lower(), 'Changed primary DOI')
        blocks = {b['paragraph_index']: b for b in paragraphs(tree)}
        matches = []
        for block in blocks.values():
            text = block.get('match_text', block['text'])
            witnesses = list(NAMED.finditer(text))
            if witnesses:
                matches.append(dict(**pointer(block), matching_text_sha256=pin(text.encode())['sha256'],
                                    witnesses=[dict(literal=m.group(), offset=m.start()) for m in witnesses]))
        if matches:
            named.append(dict(**identity, anchors=matches))
            selected[identity['paper_id']] = identity, blocks
        related = [pointer(b) for b in blocks.values() if BROADER.search(b.get('match_text', b['text']))]
        if related:
            broader.append(dict(paper_id=identity['paper_id'], anchors=related))
        for table in tree.iter('table-wrap'):
            text = words(table)
            if NAMED.search(text) or BROADER.search(text):
                tables.append(dict(paper_id=identity['paper_id'], table_id=table.get('id'),
                                   text_sha256=pin(text.encode())['sha256']))
    require(set(selected) == {s['paper_id'] for s in SPECS} and tables == [],
            'Named/table census changed; inspect every new or missing source before admission')
    roles = []
    for spec in SPECS:
        identity, blocks = selected[spec['paper_id']]
        support = [blocks[i] for i in spec['paragraphs']]
        require(all(NAMED.search(b.get('match_text', b['text'])) for b in support), 'Missing named provider anchor')
        text = '\n'.join(b['text'] for b in support)
        for version in spec['reported_builds'] + ([spec['reported_client']] if spec['reported_client'] else []):
            require(version in text, 'Reported edition absent from source: ' + version)
        role = copy.deepcopy(spec)
        role.update(tool_id='gprofiler', pmcid=identity['pmcid'], doi=identity['doi'],
                    xml_sha256=identity['xml_sha256'], source_pointers=[pointer(b) for b in support],
                    runtime_qualified=False, whole_paper_reproduced=False)
        roles.append(role)
    return dict(schema_version=1, status='PASS_FULL250_PRIMARY_GPROFILER_SOURCE_AUDIT_ONLY',
                papers_checked=250, extractor_sha256=EXTRACTOR_SHA,
                identities=[{k:s[k] for k in ['paper_id','pmcid','doi','xml_sha256']} for s in sources],
                named_candidates=named, broader_candidates=broader,
                unbound_broader_candidates=[row for row in broader if row['paper_id'] not in selected],
                table_candidates=tables, roles=roles,
                new_family_credit=0, canonical_catalog_modified=False,
                runtime_qualified=False, whole_goal_complete=False)
