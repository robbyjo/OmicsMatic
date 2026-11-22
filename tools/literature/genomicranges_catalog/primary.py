"""Audit every frozen primary XML; publish summaries and pointers, not full text."""
from pathlib import Path
import copy
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET

from collect_omics_review import paragraphs, words
from gsea_catalog.primary import identities, EXTRACTOR_SHA
from genomicranges_catalog.decisions import SPECS, UNBOUND

ROOT = Path(__file__).resolve().parents[3]
FOLDER = 'docs/literature/genomicranges-catalog-sources'
NAMED = re.compile(r'(?<![A-Za-z0-9_])GenomicRanges(?![A-Za-z0-9_])', re.I)
BROADER = re.compile(r'(?<![A-Za-z0-9_])(?:GRanges|findOverlaps|subsetByOverlaps|distanceToNearest|get_out_of_bound_index)(?![A-Za-z0-9_])', re.I)


def require(condition, detail):
    if not condition:
        raise ValueError(detail)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def pointer(block):
    return dict(paragraph_index=block['paragraph_index'], section=block['section'],
                paragraph_sha256=pin(block['text'].encode('utf-8'))['sha256'])


def audit(cache):
    require(pin((ROOT / 'tools/literature/collect_omics_review.py').read_bytes())['sha256'] == EXTRACTOR_SHA,
            'Changed frozen paragraph extractor')
    sources = identities()
    candidates, broader, tables, selected, private = [], [], [], {}, []
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
        named, related = [], []
        for block in blocks.values():
            text = block.get('match_text', block['text'])
            for pattern, target in [(NAMED, named), (BROADER, related)]:
                matches = list(pattern.finditer(text))
                if matches:
                    target.append(dict(**pointer(block), matching_text_sha256=pin(text.encode('utf-8'))['sha256'],
                                       matches=[dict(literal=m.group(), character_offset=m.start()) for m in matches]))
        if named:
            candidates.append(dict(**identity, anchors=named))
            selected[identity['paper_id']] = (identity, blocks)
        if related:
            broader.append(dict(paper_id=identity['paper_id'], anchors=related))
        for table in tree.iter('table-wrap'):
            text = words(table)
            if NAMED.search(text) or BROADER.search(text):
                tables.append(dict(paper_id=identity['paper_id'], table_id=table.get('id'),
                                   text_sha256=pin(text.encode('utf-8'))['sha256']))
    require({r['paper_id'] for r in candidates} == {r['paper_id'] for r in SPECS},
            'Named candidate census changed; review every new/missing source before admission')
    roles = []
    for spec in SPECS:
        identity, blocks = selected[spec['paper_id']]
        first = blocks[spec['paragraphs'][0]]
        require(pointer(first)['paragraph_sha256'] == spec['paragraph_sha256'], 'Changed selected full paragraph')
        require(NAMED.search(first.get('match_text', first['text'])) is not None, 'Named provider anchor missing')
        support = [blocks[index] for index in spec['paragraphs']]
        joined = '\n'.join(b['text'] for b in support)
        for function in spec['functions']:
            require(function in joined, 'Named function missing: ' + function)
        for edition in spec['reported_editions']:
            require(edition in joined, 'Reported edition missing: ' + edition)
        role = copy.deepcopy(spec)
        role.update(tool_id='genomicranges', pmcid=identity['pmcid'], doi=identity['doi'],
                    xml_sha256=identity['xml_sha256'], source_pointers=[pointer(b) for b in support],
                    provider='GenomicRanges', whole_paper_reproduced=False)
        if spec['paper_id'] == 'P094':
            auxiliary = ROOT / 'tools/literature/genomicranges_catalog/P094-AUXILIARY.json'
            data = json.loads(auxiliary.read_bytes())
            require(data['paper_id'] == 'P094' and not data['author_code_executed']
                    and not data['paper_executed_commit_confirmed'], 'False author execution claim')
            require(data['recorded_session']['GenomicRanges'] == '1.58.0'
                    and data['recorded_session']['IRanges'] == '2.40.0', 'Changed inspected recorded session')
            role['linked_author_code'] = dict(metadata_file=auxiliary.relative_to(ROOT).as_posix(),
                                             metadata_pin=pin(auxiliary.read_bytes()), repository=data['repository'],
                                             commit=data['commit'], recorded_session=data['recorded_session'],
                                             paper_executed_commit_confirmed=False, author_code_executed=False)
        roles.append(role)
        private.append(dict(paper_id=spec['paper_id'], paragraphs=support))
    unbound = sorted({r['paper_id'] for r in broader} - set(selected))
    require(unbound == [UNBOUND['paper_id']], 'Broader unbound candidates changed; inspect before attribution')
    result = dict(schema_version=1, status='PASS_FULL250_PRIMARY_GENOMICRANGES_ROLE_AUDIT',
                  papers_checked=250, extractor_sha256=EXTRACTOR_SHA,
                  identities=[{k: row[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']} for row in sources],
                  named_candidates=candidates, broader_candidates=broader, table_candidates=tables,
                  unbound_decisions=[copy.deepcopy(UNBOUND)], roles=roles,
                  pending_roles=[r['paper_id'] for r in SPECS if not r.get('preserve_existing_review')],
                  existing_reviews_preserved=['P094', 'P242'], new_family_credit=0,
                  catalog_modified=False, upstream_runtime_qualified=False, whole_goal_complete=False)
    return result, private


def validate(result):
    require(result['status'] == 'PASS_FULL250_PRIMARY_GENOMICRANGES_ROLE_AUDIT'
            and result['papers_checked'] == 250 and result['extractor_sha256'] == EXTRACTOR_SHA,
            'Different primary audit scope')
    expected = [{k: row[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']} for row in identities()]
    require(result['identities'] == expected, 'Changed ordered primary identities')
    require(len(result['roles']) == len(SPECS) == 7 and result['table_candidates'] == [], 'Changed source census')
    for role, spec in zip(result['roles'], SPECS, strict=True):
        require(all(role[key] == value for key, value in spec.items()), 'Changed individually read interpretation')
        require(role['source_pointers'][0]['paragraph_sha256'] == spec['paragraph_sha256']
                and [p['paragraph_index'] for p in role['source_pointers']] == spec['paragraphs'],
                'Changed exact full-paragraph pointers')
        require(role['tool_id'] == 'genomicranges' and role['provider'] == 'GenomicRanges'
                and role['whole_paper_reproduced'] is False, 'False provider or broad claim')
    require(result['pending_roles'] == ['P013', 'P032', 'P051', 'P137', 'P158']
            and result['existing_reviews_preserved'] == ['P094', 'P242'], 'Changed new/prior review split')
    require(result['new_family_credit'] == 0 and result['catalog_modified'] is False
            and result['upstream_runtime_qualified'] is False and result['whole_goal_complete'] is False,
            'A source audit alone cannot admit a runtime or complete the goal')


def frozen():
    raw = (ROOT / FOLDER / 'CENSUS.json').read_bytes()
    require(pin(raw) == json.loads((ROOT / FOLDER / 'CENSUS-PIN.json').read_bytes())['census'], 'Changed frozen census bytes')
    result = json.loads(raw)
    validate(result)
    return result
