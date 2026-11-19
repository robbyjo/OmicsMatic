# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only 250-paper XML/author-code audit; never admit metadata or run science."""
from pathlib import Path
from collections import Counter
import argparse
import ast
import copy
import gzip
import hashlib
import json
import re
import subprocess
import sys
import traceback
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
from plink_catalog.reviews import REVIEWS, CONTEXTS, RAW_CONTEXTS

BASE = '119326ac18b46a9a0c2e60441541f9199003e820'
DATA = 'docs/literature/omics-2023-2026'
NEW = {'P088/plink', 'P130/plink2', 'P212/king', 'P229/king'}
KINDS = {'direct-package-analysis', 'named-estimator-host-unspecified',
         'hosted-plink2-estimator', 'author-code-workflow',
         'direct-package-processing-operations-unspecified', 'direct-and-FUMA-hosted-analysis',
         'hosted-snprelate-estimator', 'simulation-data-preparation-and-analysis',
         'direct-package-data-preparation', 'generic-name-in-release2-workflow',
         'reported-cohort-QC-processing'}
METHOD_ONLY = {'named-estimator-host-unspecified', 'hosted-plink2-estimator',
               'hosted-snprelate-estimator', 'generic-name-in-release2-workflow'}
COMPONENTS = {'plink-qc', 'plink-pruning', 'plink-pca', 'plink-glm', 'plink-relatedness',
              'native-score', 'native-ld-clump', 'native-ld-statistics', 'king232',
              'king-historical', 'native-king-counts', 'native-king-thresholds'}
COMPOUND = re.compile(r'\bPLINK\s+(?:v\.?\s*)?1\.9\s+and\s+2\.0\b', re.I)
ACTION = ('SKIP3: source audit and dependent catalog admission are skipped when a named '
          'source file/Git predecessor is unavailable. Restore the exact XML cache and '
          'P114 author archive named by their hashes/URLs, or retain the skip. FAIL2: no '
          'catalog result admitted; preserve the attempt, correct the named source, '
          'identity, role, edition or integrity issue and retry NEW paths. This program '
          'never executes author scripts, scientific software or an installer, and '
          'never rewrites the catalog or old review/source guards.')


class MissingSource(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ValueError(message)


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode('utf-8')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def before():
    result = {}
    for name in [DATA + '/papers.json', DATA + '/tool-inventory.json', DATA + '/summary.json',
                 'tools/literature/reviewed-uses.json']:
        process = subprocess.run(['git', '--no-replace-objects', 'show', BASE + ':' + name],
                                 cwd=ROOT, capture_output=True)
        if process.returncode:
            raise MissingSource('Frozen Git predecessor ' + BASE + ':' + name + ' unavailable')
        result[name] = process.stdout
    return result


def check_decision_keys():
    tree = ast.parse((Path(__file__).parent / 'reviews.py').read_text(encoding='utf-8'))
    for name in ['REVIEWS', 'CONTEXTS', 'RAW_CONTEXTS']:
        node = next(n.value for n in tree.body if isinstance(n, ast.Assign)
                    and any(isinstance(t, ast.Name) and t.id == name for t in n.targets))
        keys = [ast.literal_eval(k) for k in node.keys]
        require(len(keys) == len(set(keys)), 'Duplicate manual source decision: ' + name)
    require(not set(REVIEWS).intersection(CONTEXTS), 'Positive/context decisions overlap')


def validate_editions(decision, by_index):
    selected = decision['paragraph_indices']
    require(selected and len(selected) == len(set(selected)), 'Duplicate/empty selected anchors')
    for index in selected:
        require(index in by_index, 'Selected source paragraph absent: ' + str(index))
    for edition in decision['reported_editions']:
        require(set(edition) == {'literal', 'paragraph_indices', 'context'}, 'Edition fields differ')
        require(isinstance(edition['literal'], str) and edition['literal'] and edition['context'],
                'Empty edition literal/context')
        require(edition['paragraph_indices'] and set(edition['paragraph_indices']) <= set(selected),
                'Edition anchor is outside the selected source role')
        literal = edition['literal']
        for index in edition['paragraph_indices']:
            semantic = ' '.join(by_index[index].get('match_text', by_index[index]['text']).split())
            # match_text excludes attached bibliography labels. Do not accept
            # 1.9 as 1.90 or turn a neighboring reference number into a build.
            pattern = r'(?<![0-9])(?<![0-9]\.)' + re.escape(literal) + r'(?![0-9A-Za-z]|\.[0-9])'
            require(re.search(pattern, semantic, re.I) is not None,
                    'Reported edition literal not in its citation-free source: ' + literal + ' at paragraph ' + str(index))


def verify_author_archive(archive, binding):
    if archive is None or not archive.is_file():
        raise MissingSource('P114 author archive unavailable: ' + binding['archive_url'] +
                            '; SHA256 ' + binding['archive_sha256'])
    require(archive.stat().st_size == binding['archive_bytes'], 'P114 author archive size changed')
    raw = archive.read_bytes()
    require(digest(raw) == binding['archive_sha256'], 'P114 author archive SHA256 changed')
    require('md5:' + hashlib.md5(raw).hexdigest() == binding['archive_published_md5'],
            'P114 published archive checksum differs')
    with zipfile.ZipFile(archive) as source:
        require(len(source.namelist()) == len(set(source.namelist())), 'Duplicate author archive member')
        for member in binding['members']:
            payload = source.read(member['member'])
            require(len(payload) == member['bytes'] and digest(payload) == member['sha256'],
                    'Selected P114 author source changed: ' + member['member'])
            lines = payload.decode('utf-8').splitlines()
            require(len(lines) == member['line_count'], 'Author source line population differs')
            for number, wanted in member['selected_line_sha256'].items():
                require(digest(lines[int(number) - 1].encode()) == wanted,
                        'Selected author command/comment line differs')
    require(binding['scientific_code_executed'] is False and binding['script_execution_observed'] is False
            and binding['complete_author_workflow_qualified'] is False,
            'Source intake must not claim author execution/qualification')
    return copy.deepcopy(binding)


def audit(source_cache, author_archive):
    check_decision_keys()
    frozen = before()
    papers = json.loads(frozen[DATA + '/papers.json'])
    inventory = {r['id']: r for r in json.loads(frozen[DATA + '/tool-inventory.json'])}
    previous = json.loads(frozen['tools/literature/reviewed-uses.json'])['reviews']
    verify_fixed_cohort(ROOT, ROOT / DATA, papers)
    require(len(papers) == 250 and len(previous) == 1339
            and sum(len(p['tools']) for p in papers) == 2696, 'Frozen predecessor population differs')
    prior = {(r['pmcid'], r['tool_id']): r for r in previous}
    patterns = {k: re.compile(inventory[k]['pattern'], re.I) for k in ['plink', 'plink2', 'king']}
    binding = json.loads((Path(__file__).parent / 'P114-AUTHOR-CODE.json').read_bytes())
    author = verify_author_archive(author_archive, binding)
    primary, named, role_rows, excluded, old_keys, unclassified = [], [], [], [], set(), []
    non_tools = []
    for paper in papers:
        location = source_cache / (paper['pmcid'] + '.xml.gz')
        if not location.is_file():
            raise MissingSource('Missing frozen primary XML: ' + str(location))
        with gzip.open(location, 'rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024, 'Primary XML byte capacity exceeded')
        require(digest(raw) == paper['xml_sha256'], 'Frozen XML hash differs: ' + paper['paper_id'])
        xml = ET.fromstring(raw)
        dois = [n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type') == 'doi']
        require(len(dois) == 1 and dois[0].lower() == paper['doi'].lower(), 'Primary DOI differs')
        primary.append({**{k: paper[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']}, 'xml_bytes': len(raw)})
        ps = paragraphs(xml)
        by_index = {p['paragraph_index']: p for p in ps}
        for p in ps:
            p['paragraph_sha256'] = digest(p['text'].encode())
        hits = {tool: [p for p in ps if pattern.search(p.get('match_text', p['text']))]
                for tool, pattern in patterns.items()}
        compound = [p for p in ps if COMPOUND.search(p.get('match_text', p['text']))]
        for p in compound:
            if p['paragraph_index'] not in {q['paragraph_index'] for q in hits['plink2']}:
                hits['plink2'].append(p)
        if paper['paper_id'] == 'P229':
            p = by_index[10]
            require(re.search(r'checked for close relatives using king v\.1\.4',
                              p.get('match_text', p['text'])) is not None,
                    'Lowercase KING execution evidence absent')
            require(p['paragraph_index'] not in {q['paragraph_index'] for q in hits['king']},
                    'Historical lowercase KING extraction boundary changed')
            hits['king'].append(p)
        for p in ps:
            semantic = p.get('match_text', p['text'])
            if re.search(r'\bPLINK(?:[12]\b|\b)', semantic, re.I) and not any(
                    p['paragraph_index'] in {q['paragraph_index'] for q in hits[t]} for t in ['plink', 'plink2']):
                unclassified.append(dict(paper_id=paper['paper_id'], paragraph_index=p['paragraph_index'], name='PLINK'))
            if re.search(r'\bKING\b', semantic, re.I) and p['paragraph_index'] not in {q['paragraph_index'] for q in hits['king']}:
                raw_key = paper['paper_id'] + '/king/' + str(p['paragraph_index'])
                if raw_key not in RAW_CONTEXTS:
                    unclassified.append(dict(paper_id=paper['paper_id'], paragraph_index=p['paragraph_index'], name='KING'))
                else:
                    context = RAW_CONTEXTS[raw_key]
                    require(context['surface'] in p['text'], 'Non-tool name context changed: ' + raw_key)
                    non_tools.append(dict(paper_id=paper['paper_id'], tool_id='king',
                                          paragraph_index=p['paragraph_index'], paragraph_sha256=p['paragraph_sha256'],
                                          **context, positive_use=False, executable_established=False))
        for tool in patterns:
            key = paper['paper_id'] + '/' + tool
            observation = next((t for t in paper['tools'] if t['tool_id'] == tool), None)
            if observation:
                old_keys.add(key)
                anchor = by_index[observation['evidence_paragraph_index']]
                require(anchor['paragraph_sha256'] == observation['evidence_paragraph_sha256'], 'Old observation anchor changed')
            if hits[tool]:
                named.append(dict(paper_id=paper['paper_id'], tool_id=tool, paragraphs=[
                    {k: p[k] for k in ['paragraph_index', 'section', 'paragraph_sha256']}
                    for p in sorted(hits[tool], key=lambda p: p['paragraph_index'])]))
            if key in CONTEXTS:
                require(observation is None, 'Context would silently remove an old observation')
                context = CONTEXTS[key]
                require(set(context['paragraph_indices']) == {p['paragraph_index'] for p in hits[tool]},
                        'Unreviewed context paragraph: ' + key)
                excluded.append(dict(paper_id=paper['paper_id'], tool_id=tool, **copy.deepcopy(context),
                                     positive_use=False, package_execution_established=False))
                continue
            if key not in REVIEWS:
                require(not hits[tool] and observation is None, 'Unreviewed named source role: ' + key)
                continue
            decision = REVIEWS[key]
            require(decision['role'] in KINDS, 'Unknown source role kind')
            require(set(decision['qualified_component_overlap']) <= COMPONENTS, 'Unknown implementation component')
            try:
                validate_editions(decision, by_index)
            except ValueError as error:
                raise ValueError(key + ': ' + str(error)) from error
            old_review = prior.get((paper['pmcid'], tool))
            require(decision['preserve_prior_review'] is (old_review is not None), 'Prior review preservation differs: ' + key)
            if old_review:
                require(old_review['evidence_paragraph_sha256'] == observation['evidence_paragraph_sha256'],
                        'Prior source anchor differs')
            else:
                require(decision['use_summary'] and decision['required_caution'], 'New role lacks individual explanation')
            row = copy.deepcopy(decision)
            if row['use_summary'] is None:
                row['use_summary'] = old_review['use_summary']
            if row['required_caution'] is None:
                row['required_caution'] = old_review['implementation_scope_note']
            row.update({k: paper[k] for k in ['paper_id', 'pmcid', 'doi', 'fulltext_url']})
            row.update(tool_id=tool, source_xml_sha256=paper['xml_sha256'], positive_use=True,
                       package_execution_established=decision['role'] not in METHOD_ONLY,
                       existing_observation=observation is not None, new_observation=observation is None,
                       previous_anchor_index=observation['evidence_paragraph_index'] if observation else None,
                       previous_anchor_sha256=observation['evidence_paragraph_sha256'] if observation else None,
                       author_runtime_executed=False, author_script_executed=False,
                       exact_author_settings_established=False, complete_author_workflow_qualified=False,
                       whole_paper_reproduced=False,
                       anchors=[{k: by_index[i][k] for k in ['paragraph_index', 'section', 'paragraph_sha256']}
                                for i in decision['paragraph_indices']])
            if old_review:
                row['prior_review_object_sha256'] = digest(encoded(old_review))
            if key == 'P114/plink':
                require('10.5281/zenodo.20802211' in by_index[64]['text'], 'Paper no longer links the author archive')
                row['author_code_evidence'] = author
            if key == 'P130/plink2':
                require(COMPOUND.search(by_index[47].get('match_text', by_index[47]['text'])) is not None,
                        'Compound release evidence absent')
            if key == 'P088/plink':
                require('versions of PLINK were used for quality control' in by_index[59]['text'],
                        'Explicit reported QC narrative absent')
            if key == 'P212/king':
                require('We assessed family relationships with KING' in by_index[8]['text'],
                        'Explicit cohort relationship analysis absent')
            role_rows.append(row)
    require(not unclassified, 'Raw tool names outside the reviewed census: ' + repr(unclassified))
    require(len(old_keys) == 92 and set(REVIEWS) == old_keys | NEW, 'Positive role/addition population differs')
    require({r['paper_id'] + '/' + r['tool_id'] for r in named} == set(REVIEWS) | set(CONTEXTS),
            'Named source/context census differs')
    require(len(role_rows) == 96 and len(excluded) == 7, 'Source census count differs')
    require({r['paper_id'] + '/king/' + str(r['paragraph_index']) for r in non_tools} == set(RAW_CONTEXTS),
            'Non-tool proper-name census differs')
    return dict(schema_version=1, status='PASS_ALL250_IDENTITIES_96_PLINK_FAMILY_ROLES_7_CONTEXTS_8_NON_TOOL_NAMES',
                predecessor=BASE, predecessor_documents={k: dict(bytes=len(v), sha256=digest(v)) for k, v in frozen.items()},
                papers_scanned=250, positive_roles=96, tool_counts=dict(Counter(r['tool_id'] for r in role_rows)),
                existing_reviews_retained=sum(r['preserve_prior_review'] for r in role_rows), new_review_records=74,
                observations_added=[dict(paper_id=k.split('/')[0], tool_id=k.split('/')[1]) for k in sorted(NEW)],
                observations_removed=[], primary_files=primary, named_body_hits=named, roles=role_rows,
                excluded_contexts=excluded, non_tool_name_contexts=non_tools,
                named_paragraph_bindings=sum(len(r['paragraphs']) for r in named),
                author_code_archives_checked=1, author_code_members_checked=len(author['members']),
                canonical_metadata_modified=False, analyses_executed=False, new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-cache', type=Path, required=True)
    parser.add_argument('--author-archive', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=False)
    try:
        census = audit(args.source_cache.resolve(), args.author_archive.resolve())
        (output / 'CENSUS.json').write_bytes(encoded(census))
        status = dict(status=census['status'], papers=250, roles=96, contexts=7,
                      non_tool_name_contexts=8, new_review_records=74, catalog_admitted=False, required_action=ACTION)
        code = 0
    except MissingSource as error:
        status = dict(status='SKIP3_SOURCE_AUDIT_SKIPPED', error=str(error),
                      catalog_admitted=False, analyses_executed=False, required_action=ACTION)
        code = 3
    except Exception as error:
        status = dict(status='FAIL2_SOURCE_AUDIT_NOT_ADMITTED', error=repr(error),
                      detail=traceback.format_exc(), catalog_admitted=False,
                      analyses_executed=False, required_action=ACTION)
        code = 2
    (output / 'PRIMARY-STATUS.json').write_bytes(encoded(status))
    print(json.dumps(status, ensure_ascii=False), flush=True)
    return code


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print('FAIL2: preserve existing output and retry a NEW path: ' + repr(error) + '. ' + ACTION,
              file=sys.stderr)
        raise SystemExit(2)
