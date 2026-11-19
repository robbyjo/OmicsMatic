"""Deterministic Enrichr catalog proposal; source/dependency checks are separate."""
from collections import Counter
import copy
import hashlib
import json
import re
from enrichr_catalog.coverage import build, encoded, require
from coloc_catalog.model import META, DATA, NOTICE as PREVIOUS_NOTICE

GUIDE = 'docs/literature/ENRICHR_CATALOG_INTEGRATION.md'
COVERAGE = DATA + '/enrichr-source-role-coverage.json'
PROGRAM = 'src/main/java/org/jlinalg/cli/EnrichrWorkflowCli.java'
SCOPE = ('Java file workflow for the current human/mouse Enrichr upstream service: prepared literal gene lists, '
         'server-default or explicit custom backgrounds, exact library download, raw/partial capture, all returned '
         'terms and numeric tokens, semantic warnings and offline VERIFY/REPLAY. Statistical computation is upstream.')
NOTE = ('Twenty-five reviewed source roles include 23 enrichment-analysis roles, one library-resource use and '
        'one unresolved function/package attribution. Historical clients/library snapshots, Julia local recalculation, '
        'FGSEA/intersections, original inputs/options and complete author workflows remain explicit gaps. '
        'The service does not reveal its final scoring population; custom normalization is unobserved. '
        'Use a current source build: the published 0.4.0 binary predates this command.')
CURRENT = ('250 papers, 446 named families, 2,703 observations, 1,462 individually reviewed positive uses '
           'and 1,241 awaiting individual review. There are 136 new scoped integrations (25 native Java and '
           '111 upstream), 259 missing and 39 method-overlap definitions; 139 integrations include three existing '
           'adapters. Twenty-three Enrichr reviews and three source-supported observations are appended; all '
           '1,439 earlier reviews, including P061/P163, are retained. The full implementation and semantic-review goal remains open.')
ACTION = ('SKIP3: skip dependent analysis/audit when the exact permitted source, runtime, service or validation '
          'dependency is unavailable; name what to restore and leave the skip visible. FAIL2: no complete result; '
          'retain raw inputs, partials and errors, correct the cause and retry NEW outputs. Do not repair IDs, '
          'source inequalities, P values, nonfinite values, libraries or algorithms silently. Inspect WARNINGS.md '
          'and analysis-status.json. Positive source infinite scores and P=0 are explicit warnings, not capped values. '
          'Earlier fixture failures and two historical marker-verifier SKIP3 gaps remain retained. '
          'Both literature reports stay withheld from Pages; no release is created.')
NOTICE = ('<aside id="enrichr-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
          'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>Enrichr: current-service workflow and 25 reviewed source roles</h2><p>' + CURRENT + '</p><p>' + SCOPE + ' ' + NOTE +
          '</p><p><strong>Warnings and required actions:</strong> ' + ACTION +
          '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE + '">Individual source roles and recovery</a>.</p></aside>')
NEW_NAMES = {'P048': 'Enrichr', 'P051': 'EnrichR', 'P134': 'EnrichR'}


def registry():
    from coloc_catalog.model import registry as previous
    rows = previous()
    require(len(rows) == 485, 'Different effective registry population')
    row = next(t for t in rows if t['id'] == 'enrichr')
    require(row['current_coverage'] == 'method-overlap', 'Different registry predecessor')
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=SCOPE + ' ' + NOTE)
    return rows


def validate(before, census, qualification):
    require(set(before) == set(META), 'Exactly eight predecessor documents required')
    require(qualification['status'] == 'PASS_ENRICHR_NORMAL_SOURCE_BUILD_WORKFLOW_AND_EXAMPLES'
            and qualification['normal_tests_per_JVM'] == 42 and qualification['live_source_rows_compared'] == 600
            and qualification['historical_client_parity'] is False and qualification['new_family_credit'] == 0,
            'Qualified normal workflow/examples required')
    coverage = build(census)
    papers = json.loads(before[META[0]]); inventory = json.loads(before[META[1]]); reviewed = json.loads(before[META[5]])
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2700
            and len(reviewed['reviews']) == 1439, 'Different cumulative predecessor population')
    old = [r for r in reviewed['reviews'] if r['tool_id'] == 'enrichr']
    require(len(old) == 2 and {r['pmcid'] for r in old} == {'PMC10719113', 'PMC11525197'}, 'Old Enrichr reviews differ')
    row = next(t for t in inventory if t['id'] == 'enrichr')
    require(row['implementation_status'] == 'method-overlap' and row['paper_count'] == 22, 'Different/already admitted family')
    for role in coverage['roles']:
        p = next(p for p in papers if p['paper_id'] == role['paper_id'])
        require(p['pmcid'] == role['pmcid'] and p['doi'] == role['doi'] and p['xml_sha256'] == role['source_xml_sha256'],
                'Fixed paper identity differs')
        require(sum(t['tool_id'] == 'enrichr' for t in p['tools']) == (0 if role['newly_found_observation'] else 1),
                'Missing/duplicate observation')
        if role['existing_review_preserved']:
            prior = next(r for r in old if r['pmcid'] == role['pmcid'])
            require(hashlib.sha256(encoded(prior)).hexdigest() == role['previous_review_object_sha256'], 'Old review differs')
    return coverage


def transition(before, census, qualification):
    coverage = validate(before, census, qualification)
    papers = json.loads(before[META[0]]); inventory = json.loads(before[META[1]])
    summary = json.loads(before[META[2]]); reviewed = json.loads(before[META[5]])
    for role in coverage['roles']:
        p = next(p for p in papers if p['paper_id'] == role['paper_id']); anchor = role['anchors'][0]
        if role['newly_found_observation']:
            use = dict(tool_id='enrichr', tool='Enrichr', analysis_role='Gene-set enrichment',
                       matched_text=NEW_NAMES[p['paper_id']], extraction_score=0)
            p['tools'].append(use)
        else:
            use = next(t for t in p['tools'] if t['tool_id'] == 'enrichr')
        if not role['existing_review_preserved']:
            # Flat editions are attached only to the explicitly named enrichR R client.
            versions = list(dict.fromkeys(e['literal'] for e in role['reported_editions'])) if role['declared_host'] == 'enrichR-R' else []
            use.update(evidence_section=anchor['section'], evidence_paragraph_index=anchor['paragraph_index'],
                       evidence_paragraph_sha256=anchor['paragraph_sha256'],
                       evidence_locator=p['fulltext_url'] + ' (section: ' + anchor['section'] + ')',
                       review_status='usage-summary-reviewed; paper-wide-completeness-review-open',
                       evidence_selection='individually-reviewed-source-role',
                       usage_context=('figure-legend-analysis-description' if role['role'] == 'analysis-caption' else
                                      'library-resource-description' if role['role'] == 'library-resource' else
                                      'function-host-attribution-unresolved' if p['paper_id'] == 'P182' else 'analysis-method-description'),
                       use_summary=role['use_summary'], implementation_scope_note=role['required_caution'],
                       version_reported_near_name=versions[0] if len(versions) == 1 else None)
            pointers = copy.deepcopy(role['anchors'][1:])
            if pointers: use['supporting_evidence'] = pointers
            else: use.pop('supporting_evidence', None)
            record = {k: p[k] for k in ['pmcid', 'doi']}
            record.update(tool_id='enrichr', evidence_paragraph_sha256=anchor['paragraph_sha256'],
                          use_summary=role['use_summary'], implementation_scope_note=role['required_caution'])
            if len(versions) == 1: record['version_reported_near_name'] = versions[0]
            if pointers: record['supporting_evidence'] = pointers
            reviewed['reviews'].append(record)
        use['source_role_qualification'] = {k: copy.deepcopy(role[k]) for k in [
            'role', 'declared_host', 'reported_editions', 'reported_library_labels', 'use_summary', 'required_caution',
            'qualified_component_overlap', 'remaining_author_roles', 'reported_enrichment_analysis',
            'enrichment_service_execution_verified', 'historical_client_parity', 'historical_library_parity',
            'complete_author_workflow_qualified', 'exact_author_settings_established', 'author_runtime_executed', 'whole_paper_reproduced']}
    row = next(t for t in inventory if t['id'] == 'enrichr')
    row.update(current_coverage='upstream-adapter-validated', implementation_status='upstream-adapter-validated',
               previous_method_overlap_source=row['existing_source'], existing_source=PROGRAM, implementation_source=PROGRAM,
               coverage_note=SCOPE + ' ' + NOTE, implementation_scope=SCOPE + ' ' + NOTE,
               validation_note='Normal strict Gradle JAR: 42 workflow/API tests per JVM on Windows Java 21/25 and Linux Java 21; live standard/custom/library commands, independent 600-row nine-field token comparisons, cross-host offline verification/replay and documented synthetic examples.',
               paper_count=25, papers=[r['paper_id'] for r in coverage['roles']], source_roles_reviewed=25,
               enrichment_analysis_roles=23, library_resource_roles=1, unresolved_function_host_roles=1,
               source_role_coverage=COVERAGE.rsplit('/', 1)[-1], current_documentation=GUIDE,
               workflow_qualification='../enrichr-current-checks/QUALIFICATION.json',
               selected_service_profiles=['enrichr-current-human-mouse-workflow-v1'], source_build_required=True,
               released_binary_contains_workflow_adapter=False, public_CLI_available=True,
               qualified_component_entrypoints=['enrichr-tools --request RUN/VERIFY/REPLAY', 'enrichr-tools --verify'],
               paper_role_qualification={p['paper_id']: copy.deepcopy(next(t for t in p['tools'] if t['tool_id'] == 'enrichr')['source_role_qualification']) for p in papers if any(t['tool_id'] == 'enrichr' for t in p['tools'])},
               native_family_credit_added=0, upstream_family_credit_added=1, new_scoped_integration_credit=1,
               statistical_computation='upstream-service', whole_tool_qualified=False, whole_paper_reproduced=False)
    counts = dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['native-subset-validated'] == 25 and counts['upstream-adapter-validated'] == 111
            and counts['method-overlap'] == 39 and counts['missing'] == 259, 'False integration credit')
    summary['implementation_status_counts'] = counts; summary['paper_tool_observations'] = 2703
    after = dict(before)
    for name, value in [(META[0], papers), (META[1], inventory), (META[2], summary), (META[5], reviewed)]:
        raw = encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'): raw = raw.replace(b'\n', b'\r\n')
        after[name] = raw
    page = before[META[6]].decode(); match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Embedded data differs')
    payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
    page = page[:match.start(2)] + payload + page[match.end(2):]
    for name, text in [(META[6], page), (META[7], before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE) == 1 and 'id="enrichr-catalog-notice"' not in text, 'Different/already admitted notice')
        text = text.replace(PREVIOUS_NOTICE, '<details><summary>Previous COLOC catalog checkpoint (historical)</summary>' + PREVIOUS_NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', text, re.I); require(body is not None, 'HTML body missing')
        after[name] = (text[:body.end()] + NOTICE + text[body.end():]).encode()
    for name, title in [(META[3], '250-paper omics and GWAS toolbox catalog'), (META[4], 'Current literature warnings and required actions')]:
        after[name] = ('# ' + title + '\n\n## Enrichr: current-service workflow and 25 source roles\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n' + NOTE + '\n\n' + ACTION +
                       '\n\nRead [the source crosswalk and exact scope](../ENRICHR_CATALOG_INTEGRATION.md).\n\n<details><summary>Catalog before Enrichr admission (historical)</summary>\n\n' + before[name].decode() + '\n</details>\n').encode()
    return after


def check(before, after, census, qualification):
    require(after == transition(before, census, qualification), 'Different deterministic source-bound transition')
    a, b = json.loads(before[META[0]]), json.loads(after[META[0]])
    for old, new in zip(a, b, strict=True):
        require({k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}, 'Fixed paper identity changed')
        require([t for t in old['tools'] if t['tool_id'] != 'enrichr'] == [t for t in new['tools'] if t['tool_id'] != 'enrichr'], 'Unrelated observation/order changed')
        if old['paper_id'] in ['P061', 'P163']:
            require(next(t for t in old['tools'] if t['tool_id'] == 'enrichr') == {k: v for k, v in next(t for t in new['tools'] if t['tool_id'] == 'enrichr').items() if k != 'source_role_qualification'}, 'Old Enrichr observation changed')
    old, new = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([t for t in old if t['id'] != 'enrichr'] == [t for t in new if t['id'] != 'enrichr'], 'Unrelated inventory changed')
    ar, br = json.loads(before[META[5]]), json.loads(after[META[5]])
    require(br['reviews'][:1439] == ar['reviews'] and len(br['reviews']) == 1462 and all(r['tool_id'] == 'enrichr' for r in br['reviews'][1439:]), 'Old reviews or append credit differ')
    require({k: v for k, v in ar.items() if k != 'reviews'} == {k: v for k, v in br.items() if k != 'reviews'}, 'Other review metadata changed')
    require(len({(r['pmcid'], r['tool_id']) for r in br['reviews']}) == 1462, 'Duplicate review identity')
    counts = Counter(t['tool_id'] for p in b for t in p['tools']); require(len(counts) == 446 and sum(counts.values()) == 2703, 'Observation population differs')
    for row in new:
        require(row['paper_count'] == counts[row['id']] and row['papers'] == [p['paper_id'] for p in b if any(t['tool_id'] == row['id'] for t in p['tools'])], 'Inventory membership differs')
    return dict(papers=250, named_tools=446, registry_definitions=485, observations=2703, reviewed_uses=1462,
                unreviewed_uses=1241, existing_reviews_retained=1439, new_review_records=23, positive_enrichr_roles=25,
                enrichment_analysis_roles=23, library_resource_roles=1, unresolved_function_host_roles=1,
                observations_added=3, observations_removed=0, new_scoped_integrations=136, native_integrations=25,
                upstream_integrations=111, including_existing_adapters=139, missing=259, method_overlap=39,
                new_scoped_integration_credit=1, native_family_credit_added=0, upstream_family_credit_added=1,
                whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False)
