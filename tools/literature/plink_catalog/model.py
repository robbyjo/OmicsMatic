# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pure source-review transition proposal. No writer or family-credit admission."""
from collections import Counter
import copy
import json
from pathlib import Path

from plink_catalog import primary
from plink_catalog.reviews import REVIEWS, CONTEXTS, RAW_CONTEXTS

CORE = [primary.DATA + '/' + n for n in ['papers.json', 'tool-inventory.json', 'summary.json']]
CORE += ['tools/literature/reviewed-uses.json']
FAMILIES = {'plink', 'plink2', 'king'}
REPRODUCTION = ['author_runtime_executed', 'author_script_executed',
                'exact_author_settings_established', 'complete_author_workflow_qualified', 'whole_paper_reproduced']


def validate(before, census):
    require = primary.require
    require(set(before) == set(CORE), 'Four frozen core metadata documents required')
    require(census['predecessor'] == primary.BASE and census['status'] ==
            'PASS_ALL250_IDENTITIES_96_PLINK_FAMILY_ROLES_7_CONTEXTS_8_NON_TOOL_NAMES', 'Source census status/base differs')
    require(census['predecessor_documents'] == {n: dict(bytes=len(b), sha256=primary.digest(b)) for n, b in before.items()},
            'Census does not bind these actual predecessor bytes')
    papers = json.loads(before[CORE[0]])
    reviews = json.loads(before[CORE[3]])['reviews']
    require(len(papers) == 250 and len(reviews) == 1339 and sum(len(p['tools']) for p in papers) == 2696,
            'Core predecessor populations differ')
    by_paper = {p['paper_id']: p for p in papers}
    previous = {(r['pmcid'], r['tool_id']): r for r in reviews}
    require(len(census['primary_files']) == 250, 'Incomplete primary identity population')
    for paper, row in zip(papers, census['primary_files'], strict=True):
        require(all(paper[k] == row[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']) and row['xml_bytes'] > 0,
                'Source paper identity/order differs')
    named = {(r['paper_id'], r['tool_id']): r['paragraphs'] for r in census['named_body_hits']}
    require(set(named) == {tuple(k.split('/')) for k in set(REVIEWS) | set(CONTEXTS)},
            'Named source-role/context population differs')
    require(len(census['roles']) == 96 and len({(r['paper_id'], r['tool_id']) for r in census['roles']}) == 96,
            'Duplicate/missing reviewed role')
    require(census['new_review_records'] == 74 and census['existing_reviews_retained'] == 22
            and census['observations_removed'] == [] and not census['canonical_metadata_modified']
            and not census['analyses_executed'] and census['new_family_credit'] == 0,
            'Source census overclaims mutation, execution or credit')
    require(census['whole_goal_complete'] is False and census['tool_counts'] ==
            {'plink': 53, 'plink2': 26, 'king': 17}, 'Source family totals/completion claim differ')
    require(census['observations_added'] == [dict(paper_id=k.split('/')[0], tool_id=k.split('/')[1])
                                            for k in sorted(primary.NEW)], 'Source additions differ')
    binding = json.loads((Path(primary.__file__).parent / 'P114-AUTHOR-CODE.json').read_bytes())
    for row in census['roles']:
        key = row['paper_id'] + '/' + row['tool_id']
        require(key in REVIEWS, 'Unknown source role')
        decision = REVIEWS[key]; paper = by_paper[row['paper_id']]
        for k, value in decision.items():
            if value is not None:
                require(row[k] == value, 'Manual source decision differs: ' + key + '/' + k)
        require(all(row[k] == paper[k] for k in ['pmcid', 'doi', 'fulltext_url'])
                and row['source_xml_sha256'] == paper['xml_sha256'], 'Source role identity differs')
        require(row['positive_use'] is True and all(row[k] is False for k in REPRODUCTION),
                'False source/paper execution or completeness claim')
        require(row['package_execution_established'] is (row['role'] not in primary.METHOD_ONLY),
                'Standalone/hosted estimator distinction differs')
        old = next((t for t in paper['tools'] if t['tool_id'] == row['tool_id']), None)
        require(row['existing_observation'] is (old is not None) and row['new_observation'] is (key in primary.NEW),
                'Source observation semantics differ')
        require(row['previous_anchor_index'] == (old['evidence_paragraph_index'] if old else None)
                and row['previous_anchor_sha256'] == (old['evidence_paragraph_sha256'] if old else None),
                'Old observation anchor changed')
        old_review = previous.get((paper['pmcid'], row['tool_id']))
        require(row['preserve_prior_review'] is (old_review is not None), 'Prior-review preservation differs')
        if old_review:
            require(row['prior_review_object_sha256'] == primary.digest(primary.encoded(old_review)), 'Old review object changed')
            if decision['use_summary'] is None: require(row['use_summary'] == old_review['use_summary'], 'Retained use summary differs')
            if decision['required_caution'] is None: require(row['required_caution'] == old_review['implementation_scope_note'], 'Retained caution differs')
        require([a['paragraph_index'] for a in row['anchors']] == decision['paragraph_indices'], 'Role anchors differ')
        for anchor in row['anchors']:
            require(anchor in named[(row['paper_id'], row['tool_id'])], 'Anchor is not in the named source census')
        if key == 'P114/plink': require(row['author_code_evidence'] == binding, 'P114 author-code evidence differs')
    contexts = {r['paper_id'] + '/' + r['tool_id']: r for r in census['excluded_contexts']}
    require(set(contexts) == set(CONTEXTS), 'Non-execution contexts differ')
    for key, decision in CONTEXTS.items():
        require(all(contexts[key][k] == v for k, v in decision.items())
                and not contexts[key]['positive_use'] and not contexts[key]['package_execution_established'],
                'Context promoted to executable use')
    raw = {r['paper_id'] + '/king/' + str(r['paragraph_index']): r for r in census['non_tool_name_contexts']}
    require(set(raw) == set(RAW_CONTEXTS), 'Proper-name exclusions differ')
    for key, decision in RAW_CONTEXTS.items():
        require(all(raw[key][k] == v for k, v in decision.items()) and not raw[key]['positive_use']
                and not raw[key]['executable_established'], 'Proper name promoted to software')
    return papers, reviews


def transition(before, census):
    """Propose core review metadata only; full cumulative writer remains separate."""
    validate(before, census)
    documents = {n: json.loads(before[n]) for n in CORE}
    papers, inventory, summary, reviewed = (documents[n] for n in CORE)
    by_paper = {p['paper_id']: p for p in papers}
    for role in census['roles']:
        paper = by_paper[role['paper_id']]
        use = next((t for t in paper['tools'] if t['tool_id'] == role['tool_id']), None)
        anchor = role['anchors'][0]
        if use is None:
            definition = next(t for t in inventory if t['id'] == role['tool_id'])
            use = dict(tool_id=role['tool_id'], tool=definition['name'], analysis_role=definition['role'])
            paper['tools'].append(use)
        if not role['preserve_prior_review']:
            editions = list(dict.fromkeys(e['literal'] for e in role['reported_editions']))
            use.update(evidence_section=anchor['section'], evidence_paragraph_index=anchor['paragraph_index'],
                       matched_text={'P088/plink': 'PLINK', 'P130/plink2': 'PLINK 1.9 and 2.0',
                                     'P212/king': 'KING', 'P229/king': 'king', 'P114/plink': 'Plink-1.07-x86_64'}
                                    .get(role['paper_id'] + '/' + role['tool_id'], use.get('matched_text', role['tool_id'])),
                       usage_context=role['role'], review_status='usage-summary-reviewed; paper-wide-completeness-review-open',
                       version_reported_near_name=editions[0] if len(editions) == 1 else None,
                       evidence_selection='individually-reviewed-source-role', evidence_paragraph_sha256=anchor['paragraph_sha256'],
                       use_summary=role['use_summary'], implementation_scope_note=role['required_caution'],
                       evidence_locator=paper['fulltext_url'] + ' (section: ' + anchor['section'] + ')')
            pointers = [copy.deepcopy(a) for a in role['anchors'][1:]]
            if pointers: use['supporting_evidence'] = pointers
            else: use.pop('supporting_evidence', None)
            record = {k: paper[k] for k in ['pmcid', 'doi']}
            record.update(tool_id=role['tool_id'], evidence_paragraph_sha256=anchor['paragraph_sha256'],
                          use_summary=role['use_summary'], implementation_scope_note=role['required_caution'])
            if len(editions) == 1: record['version_reported_near_name'] = editions[0]
            if pointers: record['supporting_evidence'] = pointers
            if role.get('additional_primary_evidence'):
                record['additional_primary_evidence'] = copy.deepcopy(role['additional_primary_evidence'])
            reviewed['reviews'].append(record)
        # Keep the old review text/anchor unchanged while preserving fuller
        # current-stage scope notes, such as P084 multi-trait/dosage operations.
        use['source_role_qualification'] = {k: copy.deepcopy(role[k]) for k in [
            'role', 'use_summary', 'required_caution', 'reported_editions', 'qualified_component_overlap',
            'remaining_roles', 'package_execution_established', *REPRODUCTION]}
        if 'additional_primary_evidence' in role:
            use['source_role_qualification']['additional_primary_evidence'] = copy.deepcopy(role['additional_primary_evidence'])
    for tool in FAMILIES:
        row = next(t for t in inventory if t['id'] == tool)
        ids = [p['paper_id'] for p in papers if any(t['tool_id'] == tool for t in p['tools'])]
        row['papers'] = ids; row['paper_count'] = len(ids)
    summary['paper_tool_observations'] = sum(len(p['tools']) for p in papers)
    # No implementation-status promotion can be performed by this source model.
    after = {n: primary.encoded(v) for n, v in documents.items()}
    check(before, after, census)
    return after


def check(before, after, census):
    require = primary.require
    validate(before, census)
    old = {n: json.loads(before[n]) for n in CORE}; new = {n: json.loads(after[n]) for n in CORE}
    require({k: v for k, v in new[CORE[3]].items() if k != 'reviews'} ==
            {k: v for k, v in old[CORE[3]].items() if k != 'reviews'}, 'Review document policy changed')
    require(new[CORE[3]]['reviews'][:1339] == old[CORE[3]]['reviews'], 'The 1339-review prefix changed')
    require(len(new[CORE[3]]['reviews']) == 1413, 'Expected exactly 74 appended reviews')
    require(new[CORE[2]] == dict(old[CORE[2]], paper_tool_observations=2700), 'Source-only summary changes exceed four observations')
    changed = set(REVIEWS)
    roles = {(r['paper_id'], r['tool_id']): r for r in census['roles']}
    prior = {(r['pmcid'], r['tool_id']) for r in old[CORE[3]]['reviews']}
    appended = new[CORE[3]]['reviews'][1339:]
    required_appends = [r for r in census['roles'] if not r['preserve_prior_review']]
    require(len({(r['pmcid'], r['tool_id']) for r in appended}) == 74, 'Duplicate appended review')
    for record, role in zip(appended, required_appends, strict=True):
        require(all(record[k] == role[k] for k in ['pmcid', 'doi', 'tool_id', 'use_summary'])
                and record['implementation_scope_note'] == role['required_caution']
                and record['evidence_paragraph_sha256'] == role['anchors'][0]['paragraph_sha256'],
                'Appended source review differs from its individual decision')
    for p, q in zip(old[CORE[0]], new[CORE[0]], strict=True):
        require({k: v for k, v in p.items() if k != 'tools'} == {k: v for k, v in q.items() if k != 'tools'}, 'Paper identity/order changed')
        old_uses = {t['tool_id']: t for t in p['tools']}; new_uses = {t['tool_id']: t for t in q['tools']}
        require(len(new_uses) == len(q['tools']) and set(old_uses) <= set(new_uses), 'Duplicate/removed observation')
        require([t['tool_id'] for t in q['tools'][:len(p['tools'])]] == [t['tool_id'] for t in p['tools']], 'Old observation order changed')
        for tool, use in old_uses.items():
            if p['paper_id'] + '/' + tool not in changed: require(new_uses[tool] == use, 'Unrelated observation changed')
            elif (p['pmcid'], tool) in prior:
                require({k: v for k, v in new_uses[tool].items() if k != 'source_role_qualification'} == use,
                        'Previously reviewed observation text/anchor changed')
        require(set(new_uses) - set(old_uses) == {k.split('/')[1] for k in primary.NEW if k.split('/')[0] == p['paper_id']}, 'Unexpected observation added/removed')
        for tool, use in new_uses.items():
            role = roles.get((p['paper_id'], tool))
            if role is None: continue
            qualification = use['source_role_qualification']
            require(all(qualification[k] == role[k] for k in ['role', 'use_summary', 'required_caution',
                        'reported_editions', 'qualified_component_overlap', 'remaining_roles',
                        'package_execution_established', *REPRODUCTION]), 'Source scope qualification differs')
            if not role['preserve_prior_review']:
                require(use['use_summary'] == role['use_summary'] and use['implementation_scope_note'] == role['required_caution']
                        and use['evidence_paragraph_index'] == role['anchors'][0]['paragraph_index']
                        and use['evidence_paragraph_sha256'] == role['anchors'][0]['paragraph_sha256'],
                        'New source observation does not match the selected role')
    for row, changed_row in zip(old[CORE[1]], new[CORE[1]], strict=True):
        if row['id'] not in FAMILIES: require(row == changed_row, 'Unrelated inventory definition changed')
        else:
            require({k: v for k, v in row.items() if k not in ['papers', 'paper_count']} ==
                    {k: v for k, v in changed_row.items() if k not in ['papers', 'paper_count']}, 'Source proposal cannot promote implementation status')
            ids = [p['paper_id'] for p in new[CORE[0]] if any(t['tool_id'] == row['id'] for t in p['tools'])]
            require(changed_row['papers'] == ids and changed_row['paper_count'] == len(ids), 'Inventory source counts/order differ')
    require(sum(len(p['tools']) for p in new[CORE[0]]) == 2700, 'Proposed source observation count differs')
    return dict(papers=250, observations_before=2696, observations_after=2700, prior_reviews_preserved=1339,
                appended_reviews=74, reviews_after=1413, pending_after=1287,
                family_counts={'plink': 53, 'plink2': 26, 'king': 17}, new_family_credit=0,
                metadata_written=False, full_catalog_admitted=False, whole_goal_complete=False)
