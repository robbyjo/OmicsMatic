# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit individual Seurat roles against all 250 XML identities and P160's PDF."""
import argparse
import copy
import gzip
import hashlib
import importlib.metadata
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
BASE = '9574a7b1c9e20a99b704c91d9512c7137f7935e3'
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_omics_review import paragraphs, validate_supporting_evidence
from seurat_catalog.reviews import REVIEWS, CONTEXTS


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def supplement(path, paper, xml):
    expected = json.loads((Path(__file__).with_name('P160-SUPPLEMENT.json')).read_bytes())
    require(paper['paper_id'] == expected['paper_id'] and paper['doi'] == expected['doi'],
            'Supplement paper identity differs')
    media = [node for node in xml.iter('supplementary-material') if node.get('id') == expected['media_id']]
    require(len(media) == 1 and len(media[0].findall('media')) == 1,
            'Primary XML does not identify exactly one selected supplement')
    require(media[0].find('media').get('{http://www.w3.org/1999/xlink}href') == expected['filename'],
            'Primary XML supplement filename differs')
    if path is None or not Path(path).is_file():
        raise FileNotFoundError('Supply the exact permitted P160 author supplement PDF with --supplement-pdf; its table label alone is insufficient')
    path = Path(path)
    with path.open('rb') as stream:
        actual = dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
    require(actual == expected['pdf'], 'P160 author supplement PDF bytes differ')
    try:
        import pypdfium2 as pdfium
    except ImportError as error:
        raise FileNotFoundError('pypdfium2 5.13.0 is required for the selected source-PDF extraction; supply it in a permitted private Python environment') from error
    require('pypdfium2 ' + importlib.metadata.version('pypdfium2') == expected['extractor'],
            'Source PDF extractor version differs; retain the pinned audit environment')
    document = pdfium.PdfDocument(str(path))
    texts = {}
    try:
        require(len(document) == expected['total_pdf_pages'] == 83, 'P160 PDF page population differs')
        for number in [1] + [row['pdf_page'] for row in expected['selected_pages']]:
            page = document[number - 1]
            textpage = page.get_textpage()
            try:
                texts[number] = textpage.get_text_range()
            finally:
                textpage.close()
                page.close()
        require(paper['doi'] in texts[1], 'P160 PDF title page DOI differs')
        for row in expected['selected_pages']:
            text = texts[row['pdf_page']]
            actual = dict(pdf_page=row['pdf_page'], characters=len(text), bytes=len(text.encode()),
                          sha256=digest(text.encode()), Seurat_mentions=text.lower().count('seurat'))
            require(actual == row, 'Selected P160 source PDF page differs: ' + str(row['pdf_page']))
    finally:
        document.close()
    return expected, texts


def audit(cache, papers, definition, supplement_pdf):
    require(len(papers) == 250 and len(REVIEWS) == 50 and set(CONTEXTS) == {'P083'},
            'Require 250 identities, 50 individual roles and one background context')
    existing = {p['paper_id'] for p in papers if any(t['tool_id'] == 'seurat' for t in p['tools'])}
    require(len(existing) == 49 and set(REVIEWS) == existing | {'P160'},
            'The reviewed predecessor and single new Seurat observation differ')
    identities, roles, named, contexts = [], [], [], []
    supplement_metadata = None
    for paper in papers:
        raw = gzip.decompress((Path(cache) / (paper['pmcid'] + '.xml.gz')).read_bytes())
        require(digest(raw) == paper['xml_sha256'], 'Primary XML differs: ' + paper['paper_id'])
        identities.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
                               xml_sha256=paper['xml_sha256'], xml_bytes=len(raw)))
        xml = ET.fromstring(raw)
        body = paragraphs(xml)
        by_index = {p['paragraph_index']: p for p in body}
        hits = []
        for p in body:
            match = re.search(definition['pattern'], p.get('match_text', p['text']), re.I)
            if match:
                hits.append(dict(paragraph_index=p['paragraph_index'], section=p['section'],
                                 paragraph_sha256=digest(p['text'].encode()), matched_text=match[0]))
        if hits:
            named.append(dict(paper_id=paper['paper_id'], paragraphs=hits))
        id_ = paper['paper_id']
        if id_ not in REVIEWS and id_ not in CONTEXTS:
            require(not hits, 'Named Seurat paper has no individual decision: ' + id_)
            continue
        decision = REVIEWS.get(id_, CONTEXTS.get(id_))
        selected = []
        require(len(decision['paragraph_indices']) == len(set(decision['paragraph_indices'])),
                'Repeated selected source paragraph: ' + id_)
        for index in decision['paragraph_indices']:
            require(index in by_index, 'Missing selected primary paragraph: ' + id_)
            source = by_index[index]
            require(re.search(definition['pattern'], source.get('match_text', source['text']), re.I),
                    'Selected primary paragraph does not name Seurat: ' + id_)
            selected.append(dict(paragraph_index=index, section=source['section'],
                                 paragraph_sha256=digest(source['text'].encode())))
        if id_ in CONTEXTS:
            contexts.append(dict(paper_id=id_, pmcid=paper['pmcid'], doi=paper['doi'],
                                 source_xml_sha256=paper['xml_sha256'], anchors=selected,
                                 **copy.deepcopy(decision)))
            continue
        validate_supporting_evidence(body, [dict(tool_id='seurat', supporting_evidence=selected[1:])],
                                     {'seurat': definition})
        pdf_texts = {}
        if id_ == 'P160':
            supplement_metadata, pdf_texts = supplement(supplement_pdf, paper, xml)
            require(decision['supplementary_method_pages'] == [r['pdf_page'] for r in supplement_metadata['selected_pages']],
                    'P160 selected method page population differs')
        for edition in decision['reported_editions']:
            require(set(edition['paragraph_indices']).issubset(decision['paragraph_indices']),
                    'Edition points outside selected XML evidence: ' + id_)
            for index in edition['paragraph_indices']:
                require(edition['literal'] in by_index[index]['text'],
                        'Reported edition literal absent: ' + id_ + '/' + edition['literal'])
            for page in edition.get('pdf_pages', []):
                require(page in pdf_texts and edition['literal'] in pdf_texts[page],
                        'Reported edition literal absent from selected PDF page')
            require(edition['paragraph_indices'] or edition.get('pdf_pages'),
                    'Edition needs explicit source context')
        uses = [u for u in paper['tools'] if u['tool_id'] == 'seurat']
        require(len(uses) == (0 if id_ == 'P160' else 1), 'Unexpected predecessor use population: ' + id_)
        previous = uses[0] if uses else None
        if previous:
            index = previous['evidence_paragraph_index']
            require(index in by_index and digest(by_index[index]['text'].encode()) == previous['evidence_paragraph_sha256'],
                    'Predecessor anchor stale: ' + id_)
        role = dict(paper_id=id_, pmcid=paper['pmcid'], doi=paper['doi'], fulltext_url=paper['fulltext_url'],
                    source_xml_sha256=paper['xml_sha256'], anchors=selected, **copy.deepcopy(decision),
                    positive_use=True, package_execution_established=decision['role'] != 'method-reimplementation',
                    existing_observation=id_ != 'P160', new_observation=id_ == 'P160',
                    previous_anchor_index=previous['evidence_paragraph_index'] if previous else None,
                    previous_anchor_sha256=previous['evidence_paragraph_sha256'] if previous else None,
                    existing_review_preserved=id_ == 'P061', author_runtime_executed=False,
                    exact_author_settings_established=False, complete_author_workflow_qualified=False,
                    whole_paper_reproduced=False)
        if id_ == 'P160':
            role['supplementary_primary_evidence'] = copy.deepcopy(supplement_metadata)
        roles.append(role)
    require(len(identities) == 250 and len(roles) == 50 and len(contexts) == 1,
            'Individual review population incomplete')
    require({row['paper_id'] for row in named} == set(REVIEWS) | set(CONTEXTS)
            and sum(len(row['paragraphs']) for row in named) == 197,
            'Named primary XML census differs')
    require({row['paper_id'] for row in roles if not row['package_execution_established']} == {'P047', 'P061'},
            'Adapted-method roles must not become direct package runs')
    return dict(status='PASS_ALL250_XML_IDENTITIES_50_SEURAT_SOURCE_ROLES_AND_P160_AUTHOR_SUPPLEMENT',
                papers_scanned=250, primary_files=identities, roles=roles, excluded_contexts=contexts,
                named_body_hits=named, named_xml_paragraphs=197, named_xml_papers=51,
                positive_roles=50, direct_package_roles=48, method_reimplementation_roles=2,
                existing_reviews_retained=1, new_review_records=49,
                observations_added=[dict(paper_id='P160', tool_id='seurat', evidence_type='author-supplementary-pdf')],
                observations_removed=[], source_pdf_documents_checked=1, source_pdf_pages_checked=7,
                source_pdf_binding=supplement_metadata, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--supplement-pdf', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        from metal_catalog.model import registry
        papers = json.loads(subprocess.check_output(['git', '--no-replace-objects', 'show', BASE +
                           ':docs/literature/omics-2023-2026/papers.json'], cwd=ROOT))
        result = audit(args.cache, papers, next(t for t in registry() if t['id'] == 'seurat'),
                       args.supplement_pdf)
        (args.out / 'COMPLETED.json').write_bytes(encoded(result))
        print(json.dumps({k: v for k, v in result.items() if k not in
                         ['primary_files', 'roles', 'named_body_hits', 'excluded_contexts', 'source_pdf_binding']}))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        failure = dict(status='SKIP3_SOURCE_AUDIT_SKIPPED' if code == 3 else 'FAIL2_NO_COMPLETE_SOURCE_AUDIT',
                       error=repr(error), required_action='Supply the exact permitted frozen XML/PDF sources and PDF extractor, or correct the named source decision/integrity failure. Preserve this attempt and use NEW output paths. No catalog mutation occurred.')
        (args.out / 'FAILURE.json').write_bytes(encoded(failure))
        print(failure['status'] + ': ' + str(error), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
