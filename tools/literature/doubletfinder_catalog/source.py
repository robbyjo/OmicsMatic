# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Identity-checked primary readings, candidate classifications and author-code audit."""
import ast, gzip, hashlib, json, re, zipfile
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, validate_supporting_evidence

MODULE = 'tools/literature/doubletfinder_catalog'
EXPECTED = {
    'doubletfinder': {'P053': [48, 86], 'P059': [85], 'P096': [40], 'P124': [52], 'P192': [17]},
    'scrattch-hicat': {'P059': [85, 87, 88, 91]},
    'scrattch-bigcat': {'P058': [50, 52, 58, 77, 85], 'P059': [6, 87, 88, 93, 96, 102, 107, 109, 114, 155, 162]},
    'scrattch-mapping': {'P058': [57, 77, 85], 'P059': [17, 111, 155, 162]},
    'arrow-r': {'P059': [87, 91]}, 'pyarrow': {'P250': [51]},
}
ARROWS = {'P037': [23], 'P059': [87, 91], 'P060': [20], 'P089': [8], 'P139': [10],
          'P156': [7], 'P172': [15, 16], 'P187': [32], 'P190': [55], 'P196': [30],
          'P215': [33], 'P243': [32], 'P250': [51]}


def load(root, name):
    return json.loads((root / MODULE / name).read_bytes())


def definitions(root):
    rows = load(root, 'definitions.json')
    if [r['id'] for r in rows] != list(EXPECTED)[1:]:
        raise ValueError('Five distinct named software definitions required')
    if any(r['current_coverage'] != 'missing' or r['existing_source'] is not None for r in rows):
        raise ValueError('Newly identified families have no implementation credit')
    return rows


def reviews(root):
    rows = load(root, 'reviews.json')['reviews']
    expected = [('P053', 'doubletfinder'), ('P059', 'doubletfinder'), ('P096', 'doubletfinder'),
                ('P124', 'doubletfinder'), ('P192', 'doubletfinder'), ('P059', 'scrattch-hicat'),
                ('P058', 'scrattch-bigcat'), ('P059', 'scrattch-bigcat'),
                ('P058', 'scrattch-mapping'), ('P059', 'scrattch-mapping'),
                ('P059', 'arrow-r'), ('P250', 'pyarrow')]
    if [(r['paper_id'], r['tool_id']) for r in rows] != expected:
        raise ValueError('Complete five existing and seven new primary readings required')
    for row in rows:
        if [a['paragraph_index'] for a in row['supporting_evidence']] != EXPECTED[row['tool_id']][row['paper_id']]:
            raise ValueError('Complete named-anchor population required')
    if [r['version_reported_near_name'] for r in rows[:5]] != [None, None, '3.0', None, None]:
        raise ValueError('Unknown and literal reported DoubletFinder editions must be retained')
    if rows[2]['reported_settings']['combined_removal_rule'] != 'DoubletFinder UNION (DoubletDecon INTERSECTION Scrublet)':
        raise ValueError('The paper-specific Boolean removal rule changed')
    if rows[8]['version_reported_near_name'] is not None or rows[11]['reported_settings']['analysis_operation'] is not None:
        raise ValueError('A declaration alone does not establish an analysis operation')
    if rows[9]['version_reported_near_name'] != '0.2':
        raise ValueError('Preserve the explicitly reported hierarchical mapping edition')
    return rows


def author_sources(root):
    members = load(root, 'author-code-members.json')
    with zipfile.ZipFile(root / MODULE / 'author-code.zip') as archive:
        if len(archive.namelist()) != len(members) or set(archive.namelist()) != set(members):
            raise ValueError('Complete author-code archive differs')
        for name, wanted in members.items():
            raw = archive.read(name)
            if {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()} != wanted:
                raise ValueError('Unmodified author source differs: ' + name)
        record = json.loads(archive.read('record.json'))
        if record['doi'] != '10.5281/zenodo.13321265' or record['metadata']['license']['id'] != 'cc-by-4.0':
            raise ValueError('Original author source provenance or license differs')
        files = record['files']
        if len(files) != 29:
            raise ValueError('Full 29 deposited scripts required')
        for item in files:
            raw = archive.read(item['key'])
            if len(raw) != item['size'] or 'md5:' + hashlib.md5(raw).hexdigest() != item['checksum']:
                raise ValueError('Publisher checksum mismatch: ' + item['key'])
        try:
            ast.parse(archive.read('Script_5.py'), filename='Script_5.py')
        except SyntaxError as error:
            if error.lineno != 21:
                raise ValueError('Original author-script parse discrepancy changed') from error
        else:
            raise ValueError('Original author-script syntax error was silently repaired')
        script = archive.read('Script_3.R')
        if hashlib.sha256(script).hexdigest() != reviews(root)[2]['deposited_author_code']['sha256']:
            raise ValueError('Deposited legacy DoubletFinder script differs')
    probe = load(root, 'selected-pK-type-probe.json')
    if probe['author_actual_pK_type_known'] or probe['paper_reported_edition_resolved']:
        raise ValueError('The selected-profile probe is not the author runtime')
    return dict(scripts=29, publisher_checksums_verified=True, author_scripts_executed=False,
                original_script5_syntax_error_line=21, author_actual_pK_type_known=False)


def scan(root, papers):
    rows = reviews(root)
    patterns = {'doubletfinder': r'(?<![A-Za-z0-9_])DoubletFinder(?![A-Za-z0-9_])'}
    patterns.update({d['id']: d['pattern'] for d in definitions(root)})
    patterns['arrow-candidate'] = r'(?<![A-Za-z0-9_])(?:arrow|pyarrow)(?![A-Za-z0-9_])'
    registry = {name: dict(pattern=value) for name, value in patterns.items()}
    census, candidates = [], []
    if len(papers) != 250 or len({p['pmcid'] for p in papers}) != 250:
        raise ValueError('All 250 distinct fixed primary sources required')
    for paper in papers:
        raw = gzip.decompress((root / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        if hashlib.sha256(raw).hexdigest() != paper['xml_sha256']:
            raise ValueError('Exact primary XML differs: ' + paper['paper_id'])
        xml = ET.fromstring(raw)
        if paper['doi'].lower() not in [x.text.lower() for x in xml.findall('front/article-meta/article-id') if x.get('pub-id-type') == 'doi' and x.text]:
            raise ValueError('Primary DOI differs')
        ps = paragraphs(xml)
        item = {k: paper[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']}
        item['anchors'] = {}
        for name, pattern in patterns.items():
            anchors = [dict(paragraph_index=p['paragraph_index'], section=p['section'],
                            paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest())
                       for p in ps if re.search(pattern, p['match_text'], re.I)]
            expected = ARROWS if name == 'arrow-candidate' else EXPECTED[name]
            if [a['paragraph_index'] for a in anchors] != expected.get(paper['paper_id'], []):
                raise ValueError('Fresh full-corpus anchor population differs: ' + paper['paper_id'] + '/' + name)
            item['anchors'][name] = anchors
            if name == 'arrow-candidate':
                kind = 'arrow-r-method' if paper['paper_id'] == 'P059' else 'pyarrow-declared-development-dependency' if paper['paper_id'] == 'P250' else 'ordinary-figure-arrow-not-software'
                candidates.extend(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], **a, classification=kind) for a in anchors)
        selected = [r for r in rows if r['paper_id'] == paper['paper_id']]
        validate_supporting_evidence(ps, selected, registry)
        for row in selected:
            if row['primary_xml_sha256'] != paper['xml_sha256'] or row['supporting_evidence'] != item['anchors'][row['tool_id']]:
                raise ValueError('Exact source anchors were dropped, added or reassigned')
            primary = next(a for a in row['supporting_evidence'] if a['paragraph_index'] == row['evidence_paragraph_index'])
            if primary['paragraph_sha256'] != row['evidence_paragraph_sha256']:
                raise ValueError('Primary review locator differs')
        census.append(item)
    if candidates != load(root, 'arrow-candidate-classification.json'):
        raise ValueError('Every broad Arrow candidate must retain its explicit classification')
    correction = load(root, 'scrublet-source-correction.json')
    if correction['anchor'] != rows[2]['supporting_evidence'][0] or 'Scrublet 0.2,' not in correction['after']['use_summary']:
        raise ValueError('The confirmed version-summary correction differs from its primary anchor')
    author_sources(root)
    return census
