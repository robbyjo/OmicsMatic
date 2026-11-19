# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Full metadata transition after, and separate from, the frozen source proposal."""
from collections import Counter
import copy
import json
import re

from plink_catalog import model as source
from plink_catalog.primary import encoded, require
from seurat_catalog.model import META, DATA, NOTICE as PREVIOUS_NOTICE

GUIDE = 'docs/literature/PLINK_CATALOG_INTEGRATION.md'
COVERAGE = DATA + '/plink-source-role-coverage.json'
PROGRAMS = {'plink': 'src/main/java/org/jlinalg/cli/PlinkToolsCli.java',
            'plink2': 'src/main/java/org/jlinalg/cli/PlinkToolsCli.java',
            'king': 'src/main/java/org/jlinalg/cli/KingToolsCli.java'}
SCOPES = {
 'plink': 'Pinned original PLINK 1.9.0 additive autosomal hard-call GLM, explicit QC, LD pruning and genetic PCA; separately qualified bounded GENOME relatedness with declared native pair checks.',
 'plink2': 'Pinned original PLINK 2.0.0-b.1.2 additive autosomal hard-call GLM including Firth/fallback reportability, explicit QC, LD pruning and exact/randomized genetic PCA; separately qualified KING tables, triangular matrices and cutoff workflows.',
 'king': 'Exact original standalone KING 2.3.2 bounded kinship/degree/duplicate/unrelated workflows and Linux IBD-coordinate transport; separately qualified historical 2.2.7 and 2.0 kinship/duplicates and exact native cutoff arithmetic. Original 2.0 source-discrepant results are rejected.',
}
NOTE = ('Scoped original adapters and separately qualified native helpers are available. '
        'The 96 source roles distinguish standalone software, hosted estimators and unspecified hosts. '
        'KING 1.4, PLINK 1.07 CNV, source-specific dosage/X/nonhuman/ROH/meta/LD-matrix and other listed gaps '
        'remain outside these qualified workflows. Native helper overlap does not prove an author recipe, '
        'historical edition equivalence or whole-tool/paper reproduction.')
CURRENT = ('250 papers, 446 named families, 2,700 observations, 1,413 individually reviewed positive uses '
           'and 1,287 awaiting individual review. There are 134 new scoped integrations (24 native Java and '
           '110 upstream), 259 missing and 41 method-overlap families; 137 integrations include three existing adapters. '
           'Four source-supported observations and 74 reviews are added; all 1,339 earlier reviews are retained. '
           'The full implementation and semantic-review goal remains open.')
ACTION = ('SKIP3: skip the dependent analysis/audit when an exact permitted source, runtime or validation dependency '
          'is unavailable or forbidden. Restore the named prerequisite or leave the skip visible. '
          'FAIL2: admit no complete result; retain inputs, partials and errors, correct the cause and retry NEW outputs. '
          'Do not repair IDs, alleles, source inequalities, nonfinite values, statistics or algorithms. '
          'Four legacy PLINK verifiers need the explicit frozen fixture project; their initial working-checkout '
          'failures remain retained. Two historical full marker-verifier mains remain SKIP3 for their missing exact old JAR. '
          'Both literature reports remain withheld from Pages; this admission creates no release.')
NOTICE = ('<aside id="plink-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
          'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
          '<h2>PLINK, PLINK 2 and KING: scoped workflows and 96 reviewed roles</h2><p>' + CURRENT +
          '</p><p>' + NOTE + '</p><p><strong>Warnings and required actions:</strong> ' + ACTION +
          '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE +
          '">Individual source roles, qualified scopes and recovery instructions</a>.</p></aside>')


def registry():
    from seurat_catalog.model import registry as parent
    rows = parent()
    require(len(rows) == 485, 'Registry population differs')
    for row in rows:
        if row['id'] in source.FAMILIES:
            require(row['current_coverage'] == 'method-overlap', 'Different family predecessor')
            row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAMS[row['id']],
                       coverage_note=SCOPES[row['id']] + ' ' + NOTE)
    return rows


def transition(before, census, qualification):
    require(set(before) == set(META), 'Exactly eight predecessor documents required')
    require(qualification['status'] == 'PASS_RETAINED_PLINK_COMPONENT_EVIDENCE_AND_EXACT_FIXTURES'
            and qualification['evidence_files'] == 6615 and qualification['fixture_files'] == 125
            and qualification['new_family_credit'] == 0 and qualification['full_catalog_admitted'] is False,
            'Qualified component evidence is required before family admission')
    proposal = source.transition({n: before[n] for n in source.CORE}, census)
    after = dict(before, **proposal)
    inventory = json.loads(proposal[source.CORE[1]])
    for row in inventory:
        tool = row['id']
        if tool not in source.FAMILIES:
            continue
        roles = [r for r in census['roles'] if r['tool_id'] == tool]
        require(row['implementation_status'] == 'method-overlap', 'Family already admitted')
        # Retire obsolete development-only hints; the pinned qualification is authoritative.
        for key in ['development_candidate_jar', 'canonical_executable_contains_adapter', 'development_cli']:
            row.pop(key, None)
        row.update(current_coverage='upstream-adapter-validated', implementation_status='upstream-adapter-validated',
            existing_source=PROGRAMS[tool], implementation_source=PROGRAMS[tool],
            coverage_note=SCOPES[tool] + ' ' + NOTE, implementation_scope=SCOPES[tool] + ' ' + NOTE,
            validation_note='Current candidate original GLM/QC/pruning/PCA/CI/scoring and retained KING evidence; explicit exact frozen fixture restoration preserves initial failures. New relatedness workflow qualification is retained separately. No independent Firth estimator or historical author recipe is claimed.',
            project_adapter_available=True, source_roles_reviewed=len(roles),
            source_role_coverage=COVERAGE.rsplit('/', 1)[-1], current_documentation=GUIDE,
            component_qualification='../plink-component-qualification/QUALIFICATION.json',
            relatedness_qualification='../plink-relatedness-development/QUALIFICATION.json',
            qualified_component_entrypoints=(['published standalone king232/king-historical Java JARs'] if tool == 'king' else
                                            ['qualified development JAR: plink-tools', 'org.jlinalg.cli.PlinkRelatednessCli']),
            paper_role_qualification={r['paper_id']: copy.deepcopy({k: r[k] for k in [
                'role', 'reported_editions', 'qualified_component_overlap', 'remaining_roles',
                'package_execution_established', *source.REPRODUCTION]}) for r in roles},
            upstream_family_credit_added=1, native_family_credit_added=0, new_scoped_integration_credit=1,
            whole_tool_qualified=False, whole_paper_reproduced=False)
    summary = json.loads(proposal[source.CORE[2]])
    counts = dict(Counter(r['implementation_status'] for r in inventory))
    require(counts['upstream-adapter-validated'] == 110 and counts['native-subset-validated'] == 24
            and counts['method-overlap'] == 41 and counts['missing'] == 259, 'False family integration credit')
    summary['implementation_status_counts'] = counts
    after[source.CORE[1]], after[source.CORE[2]] = encoded(inventory), encoded(summary)
    page = before['site/literature-review.html'].decode()
    match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    require(match is not None and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]),
            tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), 'Embedded predecessor data differs')
    payload = json.dumps(dict(papers=json.loads(after[META[0]]), tools=inventory, summary=summary),
                         ensure_ascii=False).replace('<', '\\u003c')
    page = page[:match.start(2)] + payload + page[match.end(2):]
    for name, text in [('site/literature-review.html', page),
                       ('site/literature-warnings.html', before['site/literature-warnings.html'].decode())]:
        require(text.count(PREVIOUS_NOTICE) == 1 and 'id="plink-catalog-notice"' not in text,
                'Earlier notice differs or admission already present')
        text = text.replace(PREVIOUS_NOTICE, '<details><summary>Previous Seurat catalog checkpoint (historical)</summary>' +
                            PREVIOUS_NOTICE + '</details>', 1)
        body = re.search(r'<body[^>]*>', text, re.I)
        require(body is not None, 'Missing HTML body')
        after[name] = (text[:body.end()] + NOTICE + text[body.end():]).encode()
    for name, title in [(META[3], '250-paper omics and GWAS toolbox catalog'),
                        (META[4], 'Current literature warnings and required actions')]:
        after[name] = ('# ' + title + '\n\n## PLINK-family scoped admission\n\n' + CURRENT + '\n\n' + NOTE +
            '\n\n' + ACTION + '\n\nRead [the individual source crosswalk and scope](../PLINK_CATALOG_INTEGRATION.md).'
            '\n\n<details><summary>Catalog before PLINK-family admission (historical)</summary>\n\n' +
            before[name].decode() + '\n</details>\n').encode()
    return after


def check(before, after, census, qualification):
    require(after == transition(before, census, qualification), 'Different source-bound catalog transition')
    # The immutable core transition has already checked all identities, source
    # decisions, old review objects and unrelated observations before promotion.
    old, new = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in old if r['id'] not in source.FAMILIES] ==
            [r for r in new if r['id'] not in source.FAMILIES], 'Unrelated inventory changed')
    return dict(papers=250, named_tools=446, registry_definitions=485, observations=2700,
        reviewed_uses=1413, unreviewed_uses=1287, positive_plink_family_roles=96,
        existing_reviews_retained=1339, new_review_records=74, observations_added=4, observations_removed=0,
        new_scoped_integrations=134, native_integrations=24, upstream_integrations=110,
        including_existing_adapters=137, missing=259, method_overlap=41,
        new_scoped_integration_credit=3, upstream_family_credit_added=3, native_family_credit_added=0,
        whole_goal_complete=False)
