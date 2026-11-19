# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact cumulative Seurat role/scope admission, including one PDF-backed use."""
import copy
from collections import Counter
import json
import re
from metal_catalog.model import META, DATA, NOTICE as PREVIOUS_NOTICE
from seurat_catalog.primary import ROOT, encoded, require
from seurat_catalog.reviews import REVIEWS, CONTEXTS

GUIDE = 'docs/literature/SEURAT_CATALOG_INTEGRATION.md'
COVERAGE = DATA + '/seurat-source-role-coverage.json'
PROGRAM = 'src/main/java/org/jlinalg/cli/SeuratWorkflowCli.java'
NOTE = ('Scoped original Seurat 5.6.0 RNA construction, LogNormalize/VST or SCT v2, typed regression/scaling, '
        'PCA, NN/SNN, clustering and optional UMAP/t-SNE with run/verify/replay; separately qualified '
        'Wilcoxon/LR prepared-layer markers and native normalization/scaling/SNN/marker summaries. '
        'Fifty source-specific roles comprise 48 direct package roles and two adapted-method roles. '
        'Anchor integration, transfer/WNN, MAST, module scoring, plots, spatial/protein assay IO and '
        'other listed author steps remain outside these qualified workflows. '
        'Component overlap is not a complete author recipe, historical runtime or whole-paper reproduction.')
CURRENT = ('250 papers, 446 named families, 2,696 paper-tool observations, 1,339 individually reviewed positive uses '
           'and 1,357 awaiting individual review. There are 131 new scoped integrations (24 native Java and '
           '107 upstream), 259 missing definitions, 44 method-overlap definitions and 134 integrations '
           'including three existing adapters. The frozen paper cohort is unchanged; one P160 Seurat use '
           'is added from its author supplement. The full implementation and semantic-review goal remains open.')
ACTION = ('SKIP3: the dependent analysis is skipped for an unavailable or forbidden exact permitted runtime, '
          'package, backend or source. Supply the named prerequisite in a permitted private environment or '
          'leave it skipped. FAIL2: no complete result is admitted. Retain inputs, partials and diagnostics, '
          'correct the named model/input/integrity/process/capacity cause and use NEW outputs. '
          'Require results_admitted true and successful verification. Do not silently change IDs, '
          'populations, nonfinite values, P values or algorithms. Preserve exact inputs: tiny covariate '
          'differences can substantially change t-SNE coordinates. The public 0.4.0 release predates '
          'these source-build workflows. No whole-tool, historical author-runtime or biological validation is claimed.')
NOTICE = ('<aside id="seurat-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
          'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>Seurat: scoped RNA workflows and 50 individually reviewed roles</h2><p>' + CURRENT +
          '</p><p>' + NOTE + '</p><p><strong>Warnings and required actions:</strong> ' + ACTION +
          '</p><p>P047/P061 adapt methods; P093 is a benchmark, P200 visualizes an existing atlas, '
          'and P160 is backed by its supplementary methods. P083 remains background. '
          '<a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE +
          '">Source crosswalk, supported scope and recovery steps</a>.</p></aside>')


def registry():
    from metal_catalog.model import registry as parent
    rows = parent()
    require(len(rows) == 485, 'Registry population changed')
    row = next(t for t in rows if t['id'] == 'seurat')
    require(row['current_coverage'] == 'method-overlap', 'Unexpected Seurat predecessor definition')
    row.update(role='Single-cell RNA workflows and prepared-layer marker inference; scoped native components',
               current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=NOTE)
    return rows


def validate_census(census, papers):
    require(census['status'] == 'PASS_ALL250_XML_IDENTITIES_50_SEURAT_SOURCE_ROLES_AND_P160_AUTHOR_SUPPLEMENT',
            'Primary source audit status differs')
    expected = dict(papers_scanned=250, named_xml_paragraphs=197, named_xml_papers=51,
                    positive_roles=50, direct_package_roles=48, method_reimplementation_roles=2,
                    existing_reviews_retained=1, new_review_records=49, source_pdf_documents_checked=1,
                    source_pdf_pages_checked=7, observations_removed=[], whole_goal_complete=False)
    require(all(census.get(k) == v for k, v in expected.items()), 'Primary source scope/counts differ')
    require(census['observations_added'] == [dict(paper_id='P160', tool_id='seurat', evidence_type='author-supplementary-pdf')],
            'Only the proved P160 observation may be added')
    require(len(census['primary_files']) == 250, 'All primary XML identities required')
    for p, row in zip(papers, census['primary_files'], strict=True):
        require(all(row[k] == p[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256'])
                and row['xml_bytes'] > 0, 'Primary identity changed: ' + p['paper_id'])
    require([r['paper_id'] for r in census['roles']] == [p['paper_id'] for p in papers if p['paper_id'] in REVIEWS],
            'Require the ordered 50 individual roles')
    named = {r['paper_id']: r['paragraphs'] for r in census['named_body_hits']}
    require(set(named) == set(REVIEWS) | set(CONTEXTS) and sum(map(len, named.values())) == 197,
            'Named body context population differs')
    binding = json.loads((ROOT / 'tools/literature/seurat_catalog/P160-SUPPLEMENT.json').read_bytes())
    require(census['source_pdf_binding'] == binding, 'Supplement identity/page evidence differs')
    for row in census['roles']:
        id_ = row['paper_id']
        decision = REVIEWS[id_]
        p = next(p for p in papers if p['paper_id'] == id_)
        require(all(row[k] == v for k, v in decision.items()), 'Manual source role/edition/scope differs: ' + id_)
        require(all(row[k] == p[k] for k in ['pmcid', 'doi', 'fulltext_url'])
                and row['source_xml_sha256'] == p['xml_sha256'], 'Role paper identity differs')
        require(row['positive_use'] is True
                and row['package_execution_established'] is (decision['role'] != 'method-reimplementation')
                and row['existing_observation'] is (id_ != 'P160') and row['new_observation'] is (id_ == 'P160')
                and row['existing_review_preserved'] is (id_ == 'P061'), 'Role admission semantics differ')
        require(all(row[k] is False for k in ['author_runtime_executed', 'exact_author_settings_established',
                    'complete_author_workflow_qualified', 'whole_paper_reproduced']), 'False author reproduction claim')
        require([a['paragraph_index'] for a in row['anchors']] == decision['paragraph_indices'],
                'Selected source anchor population changed')
        for anchor in row['anchors']:
            require(any(all(hit[k] == anchor[k] for k in ['paragraph_index', 'section', 'paragraph_sha256'])
                        for hit in named[id_]), 'Role anchor is absent from named census')
        if id_ == 'P160':
            require(row['supplementary_primary_evidence'] == binding and row['previous_anchor_index'] is None
                    and row['previous_anchor_sha256'] is None, 'New P160 use lacks exact PDF evidence')
        else:
            old = next(t for t in p['tools'] if t['tool_id'] == 'seurat')
            require((row['previous_anchor_index'], row['previous_anchor_sha256']) ==
                    (old['evidence_paragraph_index'], old['evidence_paragraph_sha256']), 'Previous anchor changed')
    contexts = census['excluded_contexts']
    require(len(contexts) == 1 and contexts[0]['paper_id'] == 'P083'
            and all(contexts[0][k] == v for k, v in CONTEXTS['P083'].items()), 'Background context changed')


def usage_context(role):
    return {'method-reimplementation': 'method-reimplementation',
            'comparative-benchmark': 'current-study-comparative-benchmark',
            'direct-package-data-wrangling': 'current-study-data-wrangling',
            'direct-package-visualization': 'current-study-visualization-of-existing-annotations'}.get(role, 'current-study-analysis')


def transition(before, census):
    require(set(before) == set(META), 'Exactly eight predecessor metadata documents required')
    papers = json.loads(before[META[0]])
    inventory = json.loads(before[META[1]])
    summary = json.loads(before[META[2]])
    reviews = json.loads(before['tools/literature/reviewed-uses.json'])
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2695
            and len(reviews['reviews']) == 1290, 'Cumulative predecessor population differs')
    old_reviews = [r for r in reviews['reviews'] if r['tool_id'] == 'seurat']
    require(len(old_reviews) == 1 and old_reviews[0]['pmcid'] == 'PMC10719113',
            'Exactly the existing P061 review must be retained')
    validate_census(census, papers)
    item = next(t for t in inventory if t['id'] == 'seurat')
    require(item['implementation_status'] == 'method-overlap' and item['paper_count'] == 49,
            'Seurat predecessor inventory differs')
    qualification = {}
    for role in census['roles']:
        id_ = role['paper_id']
        paper = next(p for p in papers if p['paper_id'] == id_)
        anchor = role['anchors'][0]
        qualification[id_] = {k: copy.deepcopy(role[k]) for k in ['role', 'package_execution_established',
            'reported_editions', 'qualified_component_overlap', 'remaining_roles',
            'complete_author_workflow_qualified', 'whole_paper_reproduced']}
        if id_ == 'P061':
            use = next(t for t in paper['tools'] if t['tool_id'] == 'seurat')
            require(use['use_summary'] == role['use_summary'] == old_reviews[0]['use_summary']
                    and use['implementation_scope_note'] == role['required_caution'] == old_reviews[0]['implementation_scope_note']
                    and use['usage_context'] == 'method-reimplementation', 'P061 existing review must remain exact')
            continue
        use = next((t for t in paper['tools'] if t['tool_id'] == 'seurat'), None)
        if id_ == 'P160':
            require(use is None, 'P160 observation already exists')
            use = dict(tool_id='seurat', tool='Seurat', matched_text='Seurat', extraction_score=0)
            paper['tools'].append(use)
        require(use is not None, 'Missing existing Seurat observation: ' + id_)
        version = next((e['literal'] for e in role['reported_editions']
                        if anchor['paragraph_index'] in e['paragraph_indices']), None)
        scope = NOTE + ' Source-specific boundary: ' + role['required_caution']
        use.update(analysis_role=role['role'], evidence_section=anchor['section'],
            evidence_paragraph_index=anchor['paragraph_index'], evidence_paragraph_sha256=anchor['paragraph_sha256'],
            evidence_locator=paper['fulltext_url'] + ' (section: ' + anchor['section'] + ')',
            usage_context=usage_context(role['role']), review_status='usage-summary-reviewed; paper-wide-completeness-review-open',
            evidence_selection='individually-reviewed-source-role', use_summary=role['use_summary'],
            implementation_scope_note=scope, version_reported_near_name=version,
            reported_editions_source_specific=copy.deepcopy(role['reported_editions']),
            supporting_evidence=copy.deepcopy(role['anchors'][1:]),
            qualified_component_overlap=copy.deepcopy(role['qualified_component_overlap']),
            remaining_author_roles=copy.deepcopy(role['remaining_roles']),
            package_execution_established=role['package_execution_established'],
            complete_author_workflow_qualified=False, exact_author_settings_established=False,
            author_runtime_executed=False, whole_paper_reproduced=False)
        if id_ == 'P160':
            use.update(analysis_evidence_type='author-supplementary-pdf',
                       primary_xml_anchor_purpose=role['primary_xml_anchor_purpose'],
                       supplementary_primary_evidence=copy.deepcopy(role['supplementary_primary_evidence']),
                       evidence_selection='individually-reviewed-author-supplementary-methods',
                       evidence_locator=role['supplementary_primary_evidence']['repository_url'] +
                           ' (author MOESM1 PDF pages 27-30, 34, 36, 55; XML paragraph 59 links supplement only)')
        review = {k: copy.deepcopy(use[k]) for k in ['tool_id', 'evidence_paragraph_sha256', 'use_summary',
                    'implementation_scope_note', 'usage_context', 'version_reported_near_name', 'supporting_evidence']}
        review.update(pmcid=paper['pmcid'], doi=paper['doi'])
        if id_ == 'P160':
            review.update(analysis_evidence_type=use['analysis_evidence_type'],
                          primary_xml_anchor_purpose=use['primary_xml_anchor_purpose'],
                          supplementary_primary_evidence=copy.deepcopy(use['supplementary_primary_evidence']))
        reviews['reviews'].append(review)
    definition = next(t for t in registry() if t['id'] == 'seurat')
    ids = [r['paper_id'] for r in census['roles']]
    item.update(role=definition['role'], current_coverage='upstream-adapter-validated',
        existing_source=PROGRAM, coverage_note=NOTE, implementation_status='upstream-adapter-validated',
        implementation_source=PROGRAM, implementation_scope=NOTE,
        validation_note='Six original RNA profiles and actual replay, 74 selected R function guards, six tests per Windows/Linux JVM and 26 controls. Separately preserved marker/native qualifications. Source roles are not complete author recipes; unsupported operations remain explicit.',
        paper_count=50, papers=ids, source_roles_reviewed=50, positive_roles_reviewed=50,
        direct_package_roles=48, method_reimplementation_roles=2, excluded_background_contexts=1,
        source_role_coverage=COVERAGE.split('/')[-1], paper_role_qualification=qualification,
        selected_project_editions=['5.6.0'], source_build_required=True,
        released_binary_contains_workflow_adapter=False, public_CLI_available=True,
        whole_tool_qualified=False, whole_paper_reproduced=False, new_scoped_integration_credit=1,
        native_family_credit_added=0, upstream_family_credit_added=1, current_documentation=GUIDE)
    counts = dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['upstream-adapter-validated'] == 107 and counts['native-subset-validated'] == 24
            and counts['missing'] == 259 and counts['method-overlap'] == 44, 'False integration credit')
    summary['implementation_status_counts'] = counts
    summary['paper_tool_observations'] = 2696
    after = dict(before)
    for name, value in [(META[0], papers), (META[1], inventory), (META[2], summary),
                        ('tools/literature/reviewed-uses.json', reviews)]:
        raw = encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'):
            raw = raw.replace(b'\n', b'\r\n')
        after[name] = raw
    page = before['site/literature-review.html'].decode()
    match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]),
            tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Embedded predecessor data differs')
    payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
    page = page[:match.start(2)] + payload + page[match.end(2):]
    for name, text in [('site/literature-review.html', page), ('site/literature-warnings.html', before['site/literature-warnings.html'].decode())]:
        require(text.count(PREVIOUS_NOTICE) == 1 and 'id="seurat-catalog-notice"' not in text,
                'Previous METAL notice differs or Seurat admission already present')
        text = text.replace(PREVIOUS_NOTICE, '<details><summary>Previous METAL catalog checkpoint (historical)</summary>' + PREVIOUS_NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', text, re.I)
        require(body is not None, 'HTML body missing')
        text = text[:body.end()] + NOTICE + text[body.end():]
        after[name] = text.encode()
    for name, title in [(META[3], '250-paper omics and GWAS toolbox catalog'), (META[4], 'Current literature warnings and required actions')]:
        after[name] = ('# ' + title + '\n\n## Seurat: scoped workflows and source-specific roles\n\n' + CURRENT +
            '\n\n' + NOTE + '\n\n' + ACTION + '\n\nP160 is newly supported by author supplementary methods; its XML table label alone was insufficient. '
            'P047/P061 remain adapted methodology, P093 is a benchmark and P200 visualizes existing atlas annotations. '
            'Read [the Seurat source crosswalk](../SEURAT_CATALOG_INTEGRATION.md). '
            'No website deployment or release is part of this admission.\n\n<details><summary>Catalog before Seurat admission (historical)</summary>\n\n' +
            before[name].decode() + '\n</details>\n').encode()
    return after


def check(before, after, census):
    require(after == transition(before, census), 'Catalog differs from exact source-reviewed Seurat transition')
    old, new = json.loads(before[META[0]]), json.loads(after[META[0]])
    for a, b in zip(old, new, strict=True):
        require({k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'},
                'Fixed paper identity changed')
        require([t for t in a['tools'] if t['tool_id'] != 'seurat'] ==
                [t for t in b['tools'] if t['tool_id'] != 'seurat'], 'Unrelated observation/order changed')
    require([t for t in json.loads(before[META[1]]) if t['id'] != 'seurat'] ==
            [t for t in json.loads(after[META[1]]) if t['id'] != 'seurat'], 'Unrelated inventory changed')
    a, b = json.loads(before['tools/literature/reviewed-uses.json']), json.loads(after['tools/literature/reviewed-uses.json'])
    require(b['reviews'][:1290] == a['reviews'] and len(b['reviews']) == 1339
            and all(r['tool_id'] == 'seurat' for r in b['reviews'][1290:]), 'Old reviews changed or append credit differs')
    require({k: v for k, v in a.items() if k != 'reviews'} == {k: v for k, v in b.items() if k != 'reviews'},
            'Unrelated review metadata changed')
    counts = Counter(t['tool_id'] for p in new for t in p['tools'])
    require(len(counts) == 446 and sum(counts.values()) == 2696, 'Derived observation population differs')
    for row in json.loads(after[META[1]]):
        require(row['paper_count'] == counts[row['id']] and row['papers'] ==
                [p['paper_id'] for p in new if any(t['tool_id'] == row['id'] for t in p['tools'])],
                'Inventory membership differs: ' + row['id'])
    return dict(papers=250, named_tools=446, registry_definitions=485, observations=2696,
                reviewed_uses=1339, unreviewed_uses=1357, positive_seurat_roles=50,
                direct_package_roles=48, method_reimplementation_roles=2, new_review_records=49,
                existing_reviews_retained=1290, observations_added=census['observations_added'],
                observations_removed=[], new_scoped_integrations=131, native_integrations=24,
                upstream_integrations=107, including_existing_adapters=134, missing=259, method_overlap=44,
                new_scoped_integration_credit=1, upstream_family_credit_added=1, native_family_credit_added=0,
                whole_goal_complete=False)
