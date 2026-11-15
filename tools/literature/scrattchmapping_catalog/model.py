# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit the existing mapping family; preserve all primary facts and all other families."""
from collections import Counter
import copy, json, re
from doubletfinder_catalog.model import ROOT, META, PAPERS, REVIEWS, DATA, NOTICE as PRIOR_NOTICE, encoded, pin, rawpin, paragraph
from scrattchmapping_catalog.source import MODULE, TOOL, NOTES, reviews, original_reviews
from scrattchmapping_catalog.bridge import EVIDENCE

PROGRAM = 'tools/literature/scrattchmapping02/run.py'
GUIDE = 'docs/literature/SCRATTCH_MAPPING02_CATALOG_INTEGRATION.md'
PROOF = DATA + '/scrattchmapping-catalog-validation.json'
COVERAGE = DATA + '/scrattchmapping02-role-coverage.json'
COMPATIBILITY = DATA + '/scrattchmapping-reviews-shared-source-compatibility.json'
WARNING = '''
## scrattch.mapping: warnings and required actions

**250 papers, 438 named software families, 2,679 observations and 1,180 individually reviewed uses; 1,499 uses await review.** The 71 Nature, 104 Nature Genetics and 75 AJHG papers, fixed source window and primary facts remain unchanged. Only implementation coverage for the two existing mapping uses changes. P058's edition remains unknown; P059 reports v0.2.

**114 scoped new integrations: 20 native Java subsets and 94 original upstream workflows; 117 including three existing adapters. 265 named families remain missing.** This adds one upstream mapping family credit and no additional native family credit. The independent Java dense L2 component is one bounded component of this family. The full semantic review and 250-paper implementation objective remain open.

The selected original v0.2 public run_mapping_on_taxonomy workflow maps against complete caller-prepared taxonomy and Annoy assets, with correlation distance and serial blocks. It supports dense and dgCMatrix queries. Eleven fresh original controls cover three hierarchy depths, bootstrap iteration behavior and source failures: seven complete returns and four original errors without a result. Returned values and RNG states use strict R identity; complete original conditions use strict identity or identical complete native-R serialization. No original condition field is deleted or coerced. The earlier live-pointer identity comparison remains failed and retained, including an independent original-versus-original failure. RDS cannot retain a live C handler pointer address or recreate its execution context; neither is claimed.

**Review all warnings and nonfinite values.** Zero expression retains 180 original analysis warnings and four package-load warnings. A complete original return is not biological validity. R handler totals do not enumerate every raw/native diagnostic; those totals are unavailable, never zero. Duplicate IDs, a single query cell, missing markers and an empty query retain their original failures. Do not rename identifiers, impute missing markers, suppress warnings or change historical hashes to obtain a passing result.

Use python3 tools/literature/scrattchmapping02/run.py --runtime /permitted/provider --request /trusted/request.rds --assets /complete/reference-assets --out /NEW/output. This is a Python source entry point, not a bundled Java CLI command. The permitted-download and cache-only public installers each completed fourteen steps in separate private runtimes. The selected R 4.2.3, BiocNeighbors 1.16.0 and actual Python 3.10.22 profile is a project comparison environment, not a recovered author environment. Original marker/reference discovery, the MapMyCells service, downstream paper-specific summaries, unreported settings and biological/paper reproduction are not qualified. Keep the scrattch.io/scrattch.vis runtime private under their redistribution restrictions; their code is not bundled in project evidence.

**SKIP3 skips dependent analysis** if exact software, permission, download, installation or execution requirements are unavailable or forbidden. **FAIL2 admits no complete result**, including terminal recording failure. Inspect the exit code, analysis-status.json, WARNINGS.md, MANIFEST.json and FINALIZATION_ERROR.json where present. A leftover RDS is not success. Retain original inputs, complete RDS values/conditions, raw consoles and available partials; resolve the named prerequisite or filesystem cause and retry NEW paths. Static inventories remain strict. Live workspace scans tolerate only removed temporary paths; permission errors remain fatal. Trusted RDS inputs and intact absolute Annoy index paths are required.

**Separate unresolved tools remain missing.** Installing HICAT/BIGCAT dependencies does not implement their paper workflows. The modified scrattch.hicat 1.0.9 score workflow, BIGCAT, R arrow and PyArrow remain separate missing families. P058's BIGCAT edition is unknown; P059 reports 0.0.5. R arrow 12.0.1 is distinct from Java Parquet/DuckDB; PyArrow 14.0.2 is a P250 development declaration with an unestablished analysis operation.

Other critical restrictions remain: DoubletFinder SCT convergence is unqualified despite source parity (3,706 original warnings, including 12 iteration-limit warnings); reported DoubletFinder 3.0 remains unresolved. Deposited author code and its syntax/legacy discrepancies remain unchanged. Cell Ranger/ARC are excluded by user request. Earlier scVelo editions, native stack diagnostics and irrecoverable historical figures retain their recorded limitations. All previous scientific verifiers, original evidence and hashes remain unchanged.
'''
NOTICE = '<section id="scrattchmapping-required-actions" role="note"><h2>scrattch.mapping: warnings and required actions</h2>' + ''.join('<p>' + paragraph(p) + '</p>' for p in WARNING.strip().split('\n\n')[1:]) + '</section>'
STYLE = '\n<style id="scrattchmapping-warning-wrap">#scrattchmapping-required-actions { overflow-wrap: anywhere; min-width: 0; }</style>\n'


def entry(root, old):
    if old['id'] != TOOL or old['implementation_status'] != 'missing' or old['papers'] != ['P058', 'P059']:
        raise ValueError('Expected the two separately missing primary mapping uses')
    row = copy.deepcopy(old)
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM,
        coverage_note='Selected original v0.2 prepared-reference hierarchical mapping; P058 edition unknown and P059 caller details remain unreported.',
        implementation_status='upstream-adapter-validated', implementation_source=PROGRAM,
        implementation_scope='Unchanged original v0.2 public run_mapping_on_taxonomy with complete caller-prepared TaxFN/Annoy assets, correlation distance and serial blocks; dense/dgCMatrix queries. Full original bootstrap aggregation and saved returns/RNG/conditions. Scientific template/marker discovery, MapMyCells service, downstream paper-specific summaries, unknown paper options and biological reproduction are not qualified.',
        validation_note='11 fresh original controls: seven complete returns and four original errors with no result. Strict returned values/RNG; whole saved conditions use strict R identity or identical complete native-R serialization, with no original fields deleted/coerced. 1,248-member frozen evidence bundle; two 14-step private installs. 318 independent native L2 controls, 58,131 returned numeric values, 303 returns and 15 original errors. Review all warnings; zero expression retains 180 analysis warnings plus four package-load warnings. SKIP3 skips dependent analysis; FAIL2 admits no complete result, including recording failure.',
        project_adapter_available=True, canonical_adapter_available=False, root_dispatch_available=False,
        development_candidate_available=True, development_candidate=PROGRAM,
        development_candidate_sha256=pin(root / PROGRAM)['sha256'],
        development_candidate_kind='python-source-entrypoint-requires-exact-original-Linux-WSL-provider-and-prepared-Annoy-assets',
        development_build='tools/literature/scrattchmapping02/install_runtime.py',
        development_adapter_cli='python3 ' + PROGRAM + ' --runtime /permitted/provider --request /trusted/request.rds --assets /complete/reference-assets --out /NEW/output',
        selected_upstream_versions=['0.2'], selected_R_version='4.2.3', selected_Python_version='3.10.22',
        selected_environment_is_author_environment=False, source_roles_reviewed=2,
        native_component_source='src/main/java/org/jlinalg/singlecell/scrattch/ScrattchMapping02.java',
        workflow_validation='../scrattchmapping02-workflow-validation/QUALIFICATION.json',
        source_role_coverage='scrattchmapping02-role-coverage.json',
        paper_role_coverage={r['paper_id']: r['use_summary'] + ' ' + r['implementation_scope_note'] for r in reviews(root)},
        uncovered_roles=['Scientific reference/marker discovery and the MapMyCells service',
                         'P058 reported edition, exact calls/metric/markers and paper-specific summaries',
                         'P059 unreported exact calls, metric, tie policy, markers and caller settings',
                         'Live C pointer/execution-context identity, universal raw diagnostic totals and other operations/platforms',
                         'Independent biological validity and paper reproduction'],
        new_scoped_integration_credit=1, native_family_credit_added=0,
        whole_tool_qualified=False, whole_paper_reproduced=False)
    return row


def transition(root, before):
    if set(before) != set(META):
        raise ValueError('Exactly eight complete predecessor catalog inputs required')
    inventory, summary = json.loads(before[META[0]]), json.loads(before[META[1]])
    papers, document = json.loads(before[PAPERS]), json.loads(before[REVIEWS])
    if (len(inventory), len(papers), sum(len(p['tools']) for p in papers), len(document['reviews'])) != (438, 250, 2679, 1180):
        raise ValueError('Complete preceding paper/tool/review populations differ')
    i = next(i for i, row in enumerate(inventory) if row['id'] == TOOL)
    inventory[i] = entry(root, inventory[i])
    expected = {r['paper_id']: r for r in original_reviews(root)}
    for pid in ['P058', 'P059']:
        old = [r for r in document['reviews'] if r['tool_id'] == TOOL and r['paper_id'] == pid]
        if len(old) != 1 or old[0] != expected[pid]:
            raise ValueError('Exact preceding primary source review required: ' + pid)
        old[0]['implementation_scope_note'] = NOTES[pid]
        paper = next(p for p in papers if p['paper_id'] == pid)
        observations = [r for r in paper['tools'] if r['tool_id'] == TOOL]
        if len(observations) != 1 or observations[0]['implementation_scope_note'] != expected[pid]['implementation_scope_note']:
            raise ValueError('Exact preceding observation coverage required')
        observations[0]['implementation_scope_note'] = NOTES[pid]
    updated = copy.deepcopy(summary)
    updated['implementation_status_counts'] = dict(Counter(row['implementation_status'] for row in inventory))
    wanted = copy.deepcopy(summary['implementation_status_counts'])
    wanted['missing'] -= 1
    wanted['upstream-adapter-validated'] += 1
    if updated['implementation_status_counts'] != wanted or (wanted['missing'], wanted['upstream-adapter-validated'], wanted['native-subset-validated']) != (265, 94, 20):
        raise ValueError('Exactly one upstream family credit required')
    page = before[META[4]].decode()
    match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    if not match or json.loads(match[2]) != dict(papers=json.loads(before[PAPERS]), tools=json.loads(before[META[0]]), summary=summary):
        raise ValueError('Exact preceding complete embedded catalog required')
    page = page[:match.start(2)] + json.dumps(dict(papers=papers, tools=inventory, summary=updated), ensure_ascii=False).replace('<', '\\u003c') + page[match.end(2):]
    warning = before[META[5]].decode()
    marker = '<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    historical = '<details><summary>Previous DoubletFinder checkpoint (historical)</summary>' + PRIOR_NOTICE + '</details>'
    if any(text.count(PRIOR_NOTICE) != 1 or text.count('</head>') != 1 for text in [page, warning]):
        raise ValueError('Exact preceding warning panels/document heads required')
    if page.count('<p id="overview"></p>') != 1 or warning.count(marker) != 1:
        raise ValueError('Expected catalog insertion points required')
    page = page.replace(PRIOR_NOTICE, historical, 1).replace('<p id="overview"></p>', NOTICE + '<p id="overview"></p>', 1).replace('</head>', STYLE + '</head>', 1)
    warning = warning.replace(PRIOR_NOTICE, historical, 1).replace(marker, marker + NOTICE, 1).replace('</head>', STYLE + '</head>', 1)
    intro = '# 250-paper omics and GWAS toolbox catalog\n\n' + WARNING + '\nRead [mapping source roles, commands and recovery](../SCRATTCH_MAPPING02_CATALOG_INTEGRATION.md). Current complete verification: `python tools/literature/scrattchmapping_catalog/catalog.py --verify --rescan-sources --out build/literature-review/monocle-runtime/NEW-mapping-catalog-check --runtime /permitted/mapping-provider --prior-runtime /permitted/DoubletFinder-provider` (add `--wsl-distro NAME` on Windows). The full goal remains open.\n\n'
    paper_bytes = encoded(papers)
    if before[PAPERS].count(b'\n') == before[PAPERS].count(b'\r\n'):
        paper_bytes = paper_bytes.replace(b'\n', b'\r\n')
    return {META[0]: encoded(inventory), META[1]: encoded(updated), PAPERS: paper_bytes, REVIEWS: encoded(document),
        META[2]: (intro + '<details><summary>Previous checkpoints (historical)</summary>\n\n' + before[META[2]].decode() + '\n</details>\n').encode(),
        META[3]: (WARNING + '\n<details><summary>Previous warnings (historical)</summary>\n\n').encode() + before[META[3]] + b'\n</details>\n',
        META[4]: page.encode(), META[5]: warning.encode()}


def check_transition(root, before, after):
    if set(after) != set(META) or after != transition(root, before):
        raise ValueError('Catalog differs from the exact one-family coverage transition')
    oldi, newi = json.loads(before[META[0]]), json.loads(after[META[0]])
    if [r for r in oldi if r['id'] != TOOL] != [r for r in newi if r['id'] != TOOL]:
        raise ValueError('An unrelated family changed')
    oldr, newr = json.loads(before[REVIEWS])['reviews'], json.loads(after[REVIEWS])['reviews']
    if [r for r in oldr if r['tool_id'] != TOOL] != [r for r in newr if r['tool_id'] != TOOL]:
        raise ValueError('An unrelated reviewed use changed')
    for old, new in zip(json.loads(before[PAPERS]), json.loads(after[PAPERS])):
        expected = copy.deepcopy(old)
        if old['paper_id'] in NOTES:
            next(r for r in expected['tools'] if r['tool_id'] == TOOL)['implementation_scope_note'] = NOTES[old['paper_id']]
        if expected != new:
            raise ValueError('Paper identity, observation, primary fact or unrelated coverage changed')
    return dict(papers=250, named_tools=438, observations=2679, reviewed_uses=1180, unreviewed_uses=1499,
        new_scoped_integrations=114, missing=265, unchanged_previous_families=437,
        unchanged_previous_observations=2677, unchanged_previous_reviews=1178,
        enriched_existing_observations=2, revised_existing_reviews=2,
        added_observations=0, added_reviews=0, new_registry_definitions=0,
        new_scoped_integration_credit=1, native_family_credit_added=0, whole_goal_complete=False)
