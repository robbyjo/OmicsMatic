"""Deterministic bounded Scanpy admission and conservation of the cumulative catalog."""
from collections import Counter
import copy
import hashlib
import json
import re
from scanpy_catalog.coverage import NATIVE, build, encoded, require
from enrichr_catalog.model import META, DATA, NOTICE as PREVIOUS_NOTICE

GUIDE = 'docs/literature/SCANPY_CATALOG_INTEGRATION.md'
COVERAGE = DATA + '/scanpy-source-role-coverage.json'
PROGRAM = 'src/main/java/org/jlinalg/cli/ScanpyWorkflowCli.java'
SCOPE = ('Ordered original Scanpy 1.13.0 Linux CPU file workflow through scanpy-tools: explicit MatrixMarket '
         'orientation/typed metadata or supplied H5AD, original RUN, offline structural VERIFY and runtime REPLAY. '
         'Two qualified programs cover 15 operations. Explicit supplementary Java float64 dense/CSR APIs provide '
         'preprocessing (1.9.3/1.13.0), Pearson residual matrices (1.10.4/1.13.0) and dispersion HVGs '
         '(Seurat/Cell-Ranger flavors, 1.9.3/1.13.0, including batches). No automatic native/upstream fallback.')
NOTE = ('Twenty-two source roles are reviewed; P061 and all earlier reviews are preserved. Historical packages, '
        'author inputs/options, hosted workflows and whole papers are not reproduced. The 29 development operation '
        'names are not 29 qualified operations. Native dispersion HVGs do not implement Seurat-v3/Pearson HVGs; '
        'native Pearson matrices do not fit SCT. Other branch, dtype and cohort-capacity gaps remain explicit. '
        'Use a current source build: the published 0.4.0 binary predates these Scanpy entry points.')
CURRENT = ('250 papers, 446 named families, 2,703 observations, 1,483 individually reviewed positive uses '
           'and 1,220 awaiting individual review. There are 137 new scoped integrations (25 native Java and '
           '112 upstream), 259 missing and 38 method-overlap definitions; 140 integrations include three existing '
           'adapters. Twenty-one Scanpy reviews are appended without new observations; all 1,462 earlier reviews '
           'remain exact. Supplementary native components receive no duplicate family credit. '
           'The full implementation and semantic-review goal remains open.')
ACTION = ('SKIP3: skip dependent analysis/audit when an exact permitted source, runtime or input is unavailable '
          'or cannot be installed; identify what to restore and retain the skip. FAIL2: no complete result; '
          'preserve inputs, warnings, partials and diagnostics, correct invalid data/model arithmetic, integrity, '
          'capacity or process failures and retry NEW outputs. Do not repair IDs, dtype, thresholds, populations, '
          'nonfinite values or algorithms silently. Check native usable() before using a result; diagnostic masks '
          'are not admitted selections when it is false. Read WARNINGS.md and analysis-status.json. '
          'Two historical marker-verifier mains remain SKIP3 pending the exact original JAR. '
          'Both literature reports remain withheld from Pages; no release is created.')
NOTICE = ('<aside id="scanpy-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
          'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>Scanpy: qualified current workflow, native components and 22 source roles</h2><p>' + CURRENT +
          '</p><p>' + SCOPE + ' ' + NOTE + '</p><p><strong>Warnings and required actions:</strong> ' + ACTION +
          '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE + '">Individual source roles and recovery</a>.</p></aside>')


def registry():
    from enrichr_catalog.model import registry as previous
    rows = previous()
    require(len(rows) == 485, 'Different effective registry population')
    row = next(t for t in rows if t['id'] == 'scanpy')
    require(row['current_coverage'] == 'method-overlap', 'Different registry predecessor')
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM, coverage_note=SCOPE + ' ' + NOTE)
    return rows


def validate(before, census, qualification):
    require(set(before) == set(META), 'Exactly eight predecessor documents required')
    require(qualification['status'] == 'PASS_SCANPY_CURRENT_WORKFLOW_AND_EXPLICIT_NATIVE_COMPONENTS'
            and qualification['normal_global_tests_per_JVM'] == 62
            and qualification['native_preprocessing_tests_per_JVM'] == 426
            and qualification['native_pearson_tests_per_JVM'] == 735
            and qualification['native_hvg_tests_per_JVM_per_producer'] == 2574
            and qualification['qualified_upstream_operations'] == 15
            and qualification['development_allowlisted_operations'] == 29
            and qualification['workflow_original_array_annotation_comparisons'] == 58
            and qualification['global_original_array_comparisons'] == 52
            and qualification['historical_author_package_parity'] is False
            and qualification['author_runtime_executed'] is False
            and qualification['whole_tool_qualified'] is False and qualification['new_family_credit'] == 0,
            'Qualified current workflow/native components with explicit gaps required')
    coverage = build(census)
    papers = json.loads(before[META[0]]); inventory = json.loads(before[META[1]]); reviewed = json.loads(before[META[5]])
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p['tools']) for p in papers) == 2703
            and len(reviewed['reviews']) == 1462, 'Different cumulative predecessor population')
    old = [r for r in reviewed['reviews'] if r['tool_id'] == 'scanpy']
    require(len(old) == 1 and old[0]['pmcid'] == 'PMC10719113', 'Old Scanpy review differs')
    row = next(t for t in inventory if t['id'] == 'scanpy')
    require(row['implementation_status'] == 'method-overlap' and row['paper_count'] == 22,
            'Different/already admitted Scanpy family')
    for role in coverage['roles']:
        p = next(p for p in papers if p['paper_id'] == role['paper_id'])
        require(p['pmcid'] == role['pmcid'] and p['doi'] == role['doi'] and p['xml_sha256'] == role['source_xml_sha256'],
                'Fixed paper identity differs')
        require(sum(t['tool_id'] == 'scanpy' for t in p['tools']) == 1, 'Missing/duplicate Scanpy observation')
        if role['existing_review_preserved']:
            require(hashlib.sha256(encoded(old[0])).hexdigest() == role['previous_review_object_sha256'], 'Old Scanpy review changed')
    return coverage


def transition(before, census, qualification):
    coverage = validate(before, census, qualification)
    papers = json.loads(before[META[0]]); inventory = json.loads(before[META[1]])
    summary = json.loads(before[META[2]]); reviewed = json.loads(before[META[5]])
    for role in coverage['roles']:
        p = next(p for p in papers if p['paper_id'] == role['paper_id']); anchor = role['anchors'][0]
        use = next(t for t in p['tools'] if t['tool_id'] == 'scanpy')
        if not role['existing_review_preserved']:
            versions = list(dict.fromkeys(e['literal'] for e in role['reported_editions']))
            context = ('function-attribution-unspecified' if p['paper_id'] in ['P024','P104'] else
                       'hosted-software-stack-description' if p['paper_id'] == 'P083' else
                       'provided-integrated-object-reuse' if p['paper_id'] == 'P182' else 'analysis-method-description')
            use.update(evidence_section=anchor['section'], evidence_paragraph_index=anchor['paragraph_index'],
                       evidence_paragraph_sha256=anchor['paragraph_sha256'],
                       evidence_locator=p['fulltext_url'] + ' (section: ' + anchor['section'] + ')',
                       review_status='usage-summary-reviewed; paper-wide-completeness-review-open',
                       evidence_selection='individually-reviewed-source-role', usage_context=context,
                       use_summary=role['use_summary'], implementation_scope_note=role['required_caution'],
                       version_reported_near_name=versions[0] if len(versions) == 1 else None)
            pointers = copy.deepcopy(role['anchors'][1:])
            if pointers: use['supporting_evidence'] = pointers
            else: use.pop('supporting_evidence', None)
            record = {k: p[k] for k in ['pmcid', 'doi']}
            record.update(tool_id='scanpy', evidence_paragraph_sha256=anchor['paragraph_sha256'],
                          use_summary=role['use_summary'], implementation_scope_note=role['required_caution'])
            if len(versions) == 1: record['version_reported_near_name'] = versions[0]
            if pointers: record['supporting_evidence'] = pointers
            reviewed['reviews'].append(record)
        use['source_role_qualification'] = {k: copy.deepcopy(role[k]) for k in [
            'role', 'declared_host', 'reported_editions', 'reported_operations', 'use_summary', 'required_caution',
            'current_1130_tested_function_counterparts', 'counterparts_are_not_author_function_attribution',
            'native_profile_overlap', 'qualified_native_component_overlap', 'native_components_are_explicit_not_automatic_fallback',
            'remaining_author_roles', 'reusable_supplied_object_intake', 'full_reported_role_qualified',
            'complete_author_workflow_qualified', 'historical_package_parity', 'exact_author_settings_established',
            'author_runtime_executed', 'whole_paper_reproduced']}
    row = next(t for t in inventory if t['id'] == 'scanpy')
    row.update(current_coverage='upstream-adapter-validated', implementation_status='upstream-adapter-validated',
        previous_method_overlap_source=row['existing_source'], existing_source=PROGRAM, implementation_source=PROGRAM,
        coverage_note=SCOPE + ' ' + NOTE, implementation_scope=SCOPE + ' ' + NOTE,
        validation_note='Normal strict Gradle workflow: 62 global/workflow/Enrichr regression checks per JVM; 13-step and supplied-H5AD programs, 58 original array/annotation comparisons. Native preprocessing 426, Pearson 735 and dispersion-HVG 2574 checks per JVM; exact qualified scope and saved proof only.',
        source_roles_reviewed=22, source_role_coverage=COVERAGE.rsplit('/', 1)[-1], current_documentation=GUIDE,
        workflow_qualification='../scanpy-dispatch-checks/QUALIFICATION.json',
        native_component_qualifications=copy.deepcopy(NATIVE), source_build_required=True,
        released_binary_contains_workflow_adapter=False, public_CLI_available=True,
        qualified_component_entrypoints=['scanpy-tools --request RUN/VERIFY/REPLAY', 'scanpy-tools --verify',
            'ScanpyPreprocessing Java API', 'ScanpyPearsonResiduals Java API', 'ScanpyHighlyVariableGenes Java API'],
        qualified_upstream_operations=15, development_allowlisted_operations=29,
        paper_role_qualification={p['paper_id']: copy.deepcopy(next(t for t in p['tools'] if t['tool_id'] == 'scanpy')['source_role_qualification'])
                                  for p in papers if any(t['tool_id'] == 'scanpy' for t in p['tools'])},
        native_family_credit_added=0, upstream_family_credit_added=1, new_scoped_integration_credit=1,
        statistical_computation='upstream-workflow-plus-explicit-native-components',
        whole_tool_qualified=False, whole_paper_reproduced=False)
    counts = dict(Counter(t['implementation_status'] for t in inventory))
    require(counts['native-subset-validated'] == 25 and counts['upstream-adapter-validated'] == 112
            and counts['method-overlap'] == 38 and counts['missing'] == 259, 'False integration credit')
    summary['implementation_status_counts'] = counts
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
        require(text.count(PREVIOUS_NOTICE) == 1 and 'id="scanpy-catalog-notice"' not in text, 'Different/already admitted notice')
        text = text.replace(PREVIOUS_NOTICE, '<details><summary>Previous Enrichr catalog checkpoint (historical)</summary>' + PREVIOUS_NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', text, re.I); require(body is not None, 'HTML body missing')
        after[name] = (text[:body.end()] + NOTICE + text[body.end():]).encode()
    for name, title in [(META[3], '250-paper omics and GWAS toolbox catalog'), (META[4], 'Current literature warnings and required actions')]:
        after[name] = ('# ' + title + '\n\n## Scanpy: bounded current workflow, native APIs and 22 source roles\n\n' + CURRENT + '\n\n' + SCOPE + '\n\n' + NOTE + '\n\n' + ACTION +
            '\n\nRead [the source crosswalk and exact scope](../SCANPY_CATALOG_INTEGRATION.md).\n\n<details><summary>Catalog before Scanpy admission (historical)</summary>\n\n' + before[name].decode() + '\n</details>\n').encode()
    return after


def check(before, after, census, qualification):
    require(after == transition(before, census, qualification), 'Different deterministic source-bound transition')
    a, b = json.loads(before[META[0]]), json.loads(after[META[0]])
    for old, new in zip(a, b, strict=True):
        require({k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}, 'Fixed paper identity changed')
        require([t for t in old['tools'] if t['tool_id'] != 'scanpy'] == [t for t in new['tools'] if t['tool_id'] != 'scanpy'], 'Unrelated observation/order changed')
        require([t['tool_id'] for t in old['tools']] == [t['tool_id'] for t in new['tools']], 'Observation added/removed/reordered')
        if old['paper_id'] == 'P061':
            require(next(t for t in old['tools'] if t['tool_id'] == 'scanpy') == {k: v for k, v in next(t for t in new['tools'] if t['tool_id'] == 'scanpy').items() if k != 'source_role_qualification'}, 'Old Scanpy observation changed')
    old, new = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([t for t in old if t['id'] != 'scanpy'] == [t for t in new if t['id'] != 'scanpy'], 'Unrelated inventory changed')
    ar, br = json.loads(before[META[5]]), json.loads(after[META[5]])
    require(br['reviews'][:1462] == ar['reviews'] and len(br['reviews']) == 1483
            and all(r['tool_id'] == 'scanpy' for r in br['reviews'][1462:]), 'Old reviews or append credit differ')
    require({k: v for k, v in ar.items() if k != 'reviews'} == {k: v for k, v in br.items() if k != 'reviews'}, 'Other review metadata changed')
    require(len({(r['pmcid'], r['tool_id']) for r in br['reviews']}) == 1483, 'Duplicate review identity')
    counts = Counter(t['tool_id'] for p in b for t in p['tools'])
    require(len(counts) == 446 and sum(counts.values()) == 2703, 'Observation population differs')
    for row in new:
        require(row['paper_count'] == counts[row['id']] and row['papers'] == [p['paper_id'] for p in b if any(t['tool_id'] == row['id'] for t in p['tools'])], 'Inventory membership differs')
    return dict(papers=250, named_tools=446, registry_definitions=485, observations=2703, reviewed_uses=1483,
        unreviewed_uses=1220, existing_reviews_retained=1462, new_review_records=21, positive_scanpy_roles=22,
        observations_added=0, observations_removed=0, new_scoped_integrations=137, native_integrations=25,
        upstream_integrations=112, including_existing_adapters=140, missing=259, method_overlap=38,
        new_scoped_integration_credit=1, native_family_credit_added=0, upstream_family_credit_added=1,
        qualified_upstream_operations=15, development_allowlisted_operations=29,
        whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False)
