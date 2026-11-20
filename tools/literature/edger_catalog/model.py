"""Deterministic edgeR admission; preserve the exact unrelated catalog state."""
from collections import Counter
import copy
import html
import json
import re
from edger_catalog.primary import require
from edger_catalog.coverage import build
from susie_catalog.model import META, DATA
from susie_dispatch_catalog import NOTICE as PREVIOUS_NOTICE

GUIDE = 'docs/literature/EDGER_CATALOG_INTEGRATION.md'
COVERAGE = DATA + '/edger-source-role-coverage.json'
PROGRAM = 'src/main/java/org/jlinalg/cli/EdgeRWorkflowCli.java'
SCOPE = ('Global edger-tools and standalone EdgeRWorkflowCli call original edgeR 3.40.2/3.42.4/4.10.7 '
    'under explicit Windows R4.6.1 development profiles for prepared counts, filtering, normalization, '
    'dispersion and exact/LRT/explicit legacy-or-adjusted QL models. Global edger-normalize and standalone '
    'EdgeRNormalizationCli provide native Java TMM/TMMwsp/RLE/upper-quartile/none and raw/log CPM/RPKM '
    'for explicit source editions 3.32.1/3.36.0/3.40.2/3.42.4/4.10.7. Both offer closed RUN/VERIFY/REPLAY; '
    'native matrix operations do not add a second family credit.')
NOTE = ('Seventeen reviewed source roles include one added P088 package-role association with its precise '
    'operation unresolved. Literal functions, source editions and related development counterparts remain '
    'distinct from author settings/hosts and complete workflows. Full old 3.32.1/3.36.0 inference providers '
    'are unavailable; selected CPM kernels are not full packages. Exact 3.20.1/3.22.3/3.38.4 models, the '
    'power dispersion trend and technical-replicate collapse are unsupported. limma/voom and other packages '
    'remain separate. Build current source; the released 0.4.0 binary predates these workflows.')
CURRENT = ('250 papers, 446 named families, 2,705 observations, 1,520 individually reviewed positive uses '
    'and 1,185 pending. There are 139 scoped integrations (25 native Java and 114 upstream), 259 missing '
    'and 36 method-overlap definitions; 142 integrations include three existing adapters. All 1,503 earlier '
    'reviews remain exact. The full implementation and semantic-review goal remains unfinished.')
ACTION = ('SKIP3: dependent analysis skipped; supply the named exact permitted source/input/runtime/package '
    'or retain the skip if unavailable or installation is forbidden. FAIL2: no complete result; preserve '
    'inputs, original model objects, conditions, partials and logs, resolve the named data/design, numerical, '
    'convergence, integrity, capacity or process failure and retry NEW output. Two real old-edition null '
    'fits exhaust 251 iterations despite failed=FALSE and finite stock test tables; they are refused. '
    'Never silently repair IDs, alleles, populations, editions, thresholds, nonfinite values or algorithms. '
    'Read WARNINGS.md and analysis-status.json. Model VERIFY does not refit R or parse binary RDS. '
    'Historical author hosts and Linux R fitting are unqualified. Two old full marker-verifier JAR checks '
    'remain SKIP3. Cell Ranger stays excluded; both literature HTML reports stay withheld from Pages.')
NOTICE = ('<aside id="edger-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
    'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
    '<h2>edgeR: native normalization, original models and 17 reviewed roles</h2>'
    '<p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION)
    + '</p><p>' + html.escape(CURRENT) + '</p><p>' + html.escape(SCOPE + ' ' + NOTE)
    + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'
    + GUIDE + '">Source roles, exact scope and recovery</a>.</p></aside>')


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()


def registry():
    from susie_catalog.model import registry as previous
    rows = previous()
    require(len(rows) == 485, 'Different effective definition population')
    row = next(t for t in rows if t['id'] == 'edger')
    require(row['current_coverage'] == 'method-overlap', 'Different effective edgeR predecessor')
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=SCOPE + ' ' + NOTE)
    return rows


def validate(before, primary, qualification):
    require(set(before) == set(META), 'Exactly eight preceding metadata documents required')
    require(qualification['status'] == 'PASS_SAVED_GLOBAL_EDGER_ORIGINAL_NATIVE_SOURCE_COUNTERPARTS_AND_PARENT_GUARDS'
        and qualification['original_model_compositions'] == 33
        and qualification['exact_complete_object_comparisons'] == 338
        and qualification['native_compositions'] == 2 and qualification['real_source_exhaustion_refusals'] == 2
        and qualification['complete_captures_per_JVM'] == 37 and qualification['JVMs'] == 3
        and qualification['actual_parent_alias_guards'] == 10 and qualification['original_RDS_replay_exact'] is True
        and qualification['preceding_decoded_members_unchanged'] == 23484
        and qualification['changed_dispatcher_classes'] == 1
        and qualification['source_roles'] == 17 and qualification['literal_function_mentions'] == 9
        and qualification['source_role_controls_per_host'] == 15 and qualification['source_role_control_hosts'] == 2
        and qualification['failed_or_skipped_captures_refused_per_JVM'] == 3
        and qualification['exact_unavailable_profile_skips'] == 5 and qualification['missing_exact_runtime_skips'] == 1
        and qualification['current_canonical_credit_added_by_reader'] == 0,
        'Exact original/native global command qualification required')
    coverage = build(primary)
    papers = json.loads(before[META[0]])
    inventory = json.loads(before[META[1]])
    reviews = json.loads(before[META[5]])
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2704
        and len(reviews['reviews']) == 1503, 'Different cumulative predecessor population')
    require(not any(r['tool_id'] == 'edger' for r in reviews['reviews']), 'Prior edgeR reviews must not be overwritten')
    row = next(t for t in inventory if t['id'] == 'edger')
    require(row['implementation_status'] == row['current_coverage'] == 'method-overlap'
        and row['paper_count'] == 16, 'Different/already admitted edgeR family')
    require({p['paper_id'] for p in papers if any(t['tool_id'] == 'edger' for t in p['tools'])}
        == {r['paper_id'] for r in coverage['roles']} - {'P088'}, 'Different preceding edgeR observation set')
    for role in coverage['roles']:
        p = next(p for p in papers if p['paper_id'] == role['paper_id'])
        require(p['pmcid'] == role['pmcid'] and p['doi'] == role['doi'] and p['xml_sha256'] == role['source_xml_sha256'],
            'Frozen source identity differs')
        require(sum(t['tool_id'] == 'edger' for t in p['tools']) == (0 if role['paper_id'] == 'P088' else 1),
            'Missing/duplicate preceding edgeR observation')
    return coverage


def transition(before, primary, qualification):
    coverage = validate(before, primary, qualification)
    papers = json.loads(before[META[0]])
    inventory = json.loads(before[META[1]])
    summary = json.loads(before[META[2]])
    reviews = json.loads(before[META[5]])
    for role in coverage['roles']:
        p = next(p for p in papers if p['paper_id'] == role['paper_id'])
        anchor = role['primary_evidence']
        if role['paper_id'] == 'P088':
            use = dict(tool_id='edger', tool='edgeR', matched_text='edgeR', extraction_score=0)
            p['tools'].append(use)
        else:
            use = next(t for t in p['tools'] if t['tool_id'] == 'edger')
        versions = role['primary_anchor_reported_versions']
        caution = ' '.join(role['remaining_author_roles'])
        use.update(analysis_role=role['role'], evidence_section=anchor['section'],
            evidence_paragraph_index=anchor['paragraph_index'], evidence_paragraph_sha256=anchor['paragraph_sha256'],
            evidence_locator=p['fulltext_url'] + ' (section: ' + anchor['section'] + ')',
            evidence_selection='individually-reviewed-source-role',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',
            usage_context='code-availability-package-role-operation-unresolved' if role['paper_id'] == 'P088' else 'analysis-method-description',
            use_summary=role['usage_summary'], implementation_scope_note=caution,
            version_reported_near_name=versions[0] if len(versions) == 1 else None)
        pointers = [copy.deepcopy(a) for a in role['anchors'] if a['paragraph_index'] != anchor['paragraph_index']]
        if pointers:
            use['supporting_evidence'] = pointers
        else:
            use.pop('supporting_evidence', None)
        fields = ['role', 'reported_versions', 'primary_evidence', 'explicit_edgeR_functions',
            'native_matrix_counterparts', 'original_prepared_count_counterparts', 'matching_reported_native_editions',
            'matching_reported_model_source_profiles', 'unsupported_reported_model_editions',
            'reported_editions_without_any_qualified_component', 'operation_unresolved',
            'counterparts_are_not_author_function_attribution', 'original_author_inputs_and_settings_recovered',
            'author_R_and_dependency_profile_recovered', 'historical_author_host_reproduced',
            'complete_author_workflow_qualified', 'full_reported_role_qualified', 'remaining_author_roles',
            'author_runtime_executed', 'whole_paper_reproduced']
        use['source_role_qualification'] = {k: copy.deepcopy(role[k]) for k in fields}
        record = {k: p[k] for k in ['pmcid', 'doi']}
        record.update(tool_id='edger', evidence_paragraph_sha256=anchor['paragraph_sha256'],
            use_summary=role['usage_summary'], implementation_scope_note=caution)
        if len(versions) == 1:
            record['version_reported_near_name'] = versions[0]
        if pointers:
            record['supporting_evidence'] = pointers
        reviews['reviews'].append(record)
    row = next(t for t in inventory if t['id'] == 'edger')
    row.update(current_coverage='upstream-adapter-validated', implementation_status='upstream-adapter-validated',
        previous_method_overlap_source=row['existing_source'], existing_source=PROGRAM, implementation_source=PROGRAM,
        coverage_note=SCOPE + ' ' + NOTE, implementation_scope=SCOPE + ' ' + NOTE,
        validation_note='33 actual global original compositions and 338 exact stock complete-object comparisons; two real exhaustion refusals. Two native/original compositions; exact model/native replay. 37 complete and three failed/skipped captures per JVM on Windows21/25/Linux21, ten real replay-parent guards, five exact unsupported profiles and one missing-runtime skip. All 23484 decoded members outside the root dispatcher are unchanged. Windows R fitting only.',
        paper_count=17, papers=[p['paper_id'] for p in papers if any(t['tool_id'] == 'edger' for t in p['tools'])],
        source_roles_reviewed=17, source_role_coverage=COVERAGE.rsplit('/', 1)[-1], current_documentation=GUIDE,
        workflow_qualification='../edger-dispatch-checks/QUALIFICATION.json', source_build_required=True,
        released_binary_contains_workflow_adapter=False, public_CLI_available=True, global_CLI_dispatch_available=True,
        qualified_component_entrypoints=['edger-tools --request RUN/VERIFY/REPLAY', 'edger-tools --verify',
            'edger-normalize --request RUN/VERIFY/REPLAY', 'edger-normalize --verify',
            'org.jlinalg.cli.EdgeRWorkflowCli', 'org.jlinalg.cli.EdgeRNormalizationCli'],
        paper_role_qualification={p['paper_id']: copy.deepcopy(next(t for t in p['tools'] if t['tool_id'] == 'edger')['source_role_qualification'])
            for p in papers if any(t['tool_id'] == 'edger' for t in p['tools'])},
        native_family_credit_added=0, upstream_family_credit_added=1, new_scoped_integration_credit=1,
        statistical_computation='native-source-versioned-normalization-plus-original-edgeR-models',
        whole_tool_qualified=False, whole_paper_reproduced=False)
    counts = dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['native-subset-validated'] == 25 and counts['upstream-adapter-validated'] == 114
        and counts['method-overlap'] == 36 and counts['missing'] == 259, 'False family credit')
    summary['implementation_status_counts'] = counts
    summary['paper_tool_observations'] = 2705
    after = dict(before)
    for name, value in [(META[0], papers), (META[1], inventory), (META[2], summary), (META[5], reviews)]:
        raw = encoded(value)
        if before[name].count(b'\n') == before[name].count(b'\r\n'):
            raw = raw.replace(b'\n', b'\r\n')
        after[name] = raw
    page = before[META[6]].decode()
    match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]),
        tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Embedded preceding HTML data differs')
    payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace('<', '\\u003c')
    page = page[:match.start(2)] + payload + page[match.end(2):]
    for name, text in [(META[6], page), (META[7], before[META[7]].decode())]:
        require(text.count(PREVIOUS_NOTICE) == 1 and 'id="edger-catalog-notice"' not in text, 'Different/already admitted notice')
        text = text.replace(PREVIOUS_NOTICE, '<details><summary>Previous SuSiE global command checkpoint (historical)</summary>' + PREVIOUS_NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', text, re.I)
        require(body is not None, 'HTML body missing')
        after[name] = (text[:body.end()] + NOTICE + text[body.end():]).encode()
    for name, title in [(META[3], '250-paper omics and GWAS toolbox catalog'), (META[4], 'Current literature warnings and required actions')]:
        after[name] = ('# ' + title + '\n\n## edgeR: native operations, original models and 17 reviewed roles\n\n'
            + '**Warnings and required actions:** ' + ACTION + '\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n' + NOTE
            + '\n\nRead [the source crosswalk and exact scope](../EDGER_CATALOG_INTEGRATION.md).\n\n'
            + '<details><summary>Before edgeR admission (historical)</summary>\n\n' + before[name].decode() + '\n</details>\n').encode()
    return after


def check(before, after, primary, qualification):
    require(after == transition(before, primary, qualification), 'Different deterministic source-bound transition')
    a, b = json.loads(before[META[0]]), json.loads(after[META[0]])
    for old, new in zip(a, b, strict=True):
        require({k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}, 'Fixed paper identity changed')
        require([t for t in old['tools'] if t['tool_id'] != 'edger'] == [t for t in new['tools'] if t['tool_id'] != 'edger'], 'Unrelated tool observation/order changed')
        expected = [t['tool_id'] for t in old['tools']] + (['edger'] if old['paper_id'] == 'P088' else [])
        require([t['tool_id'] for t in new['tools']] == expected, 'Unreviewed addition/removal/reordering')
    old, new = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in old if r['id'] != 'edger'] == [r for r in new if r['id'] != 'edger'], 'Other 445 inventory rows changed')
    ar, br = json.loads(before[META[5]]), json.loads(after[META[5]])
    require(br['reviews'][:1503] == ar['reviews'] and len(br['reviews']) == 1520
        and all(r['tool_id'] == 'edger' for r in br['reviews'][1503:]), 'Earlier reviews or append credit changed')
    require({k: v for k, v in ar.items() if k != 'reviews'} == {k: v for k, v in br.items() if k != 'reviews'}, 'Other review metadata changed')
    require(len({(r['pmcid'], r['tool_id']) for r in br['reviews']}) == 1520, 'Duplicate review identity')
    counts = Counter(t['tool_id'] for p in b for t in p['tools'])
    require(len(counts) == 446 and sum(counts.values()) == 2705, 'Different observation population')
    for row in new:
        require(row['paper_count'] == counts[row['id']] and row['papers'] == [p['paper_id'] for p in b if any(t['tool_id'] == row['id'] for t in p['tools'])], 'Inventory membership differs')
    return dict(papers=250, named_tools=446, registry_definitions=485, observations=2705,
        reviewed_uses=1520, pending_uses=1185, existing_reviews_retained=1503, new_review_records=17,
        positive_edger_roles=17, observations_added=1, observations_removed=0,
        integrations=139, native_integrations=25, upstream_integrations=114, including_existing_adapters=142,
        missing=259, method_overlap=36, new_scoped_integration_credit=1, native_family_credit_added=0,
        upstream_family_credit_added=1, whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False)
