# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One scoped implementation admission, seven new uses and one source correction."""
from collections import Counter
import copy, html, json, re
from velociraptor_catalog.model import ROOT, META, PAPERS, REVIEWS, DATA, NOTICE as PRIOR_NOTICE, encoded, pin, rawpin
from doubletfinder_catalog.source import MODULE, definitions, reviews, load
from doubletfinder_catalog.bridge import EVIDENCE

PROGRAM = 'tools/literature/doubletfinder206/run.py'
GUIDE = 'docs/literature/DOUBLET_FINDER_CATALOG_INTEGRATION.md'
PROOF = DATA + '/doubletfinder-catalog-validation.json'
COVERAGE = DATA + '/doubletfinder206-role-coverage.json'
COMPATIBILITY = DATA + '/doubletfinder-reviews-shared-source-compatibility.json'
WARNING = '''
## DoubletFinder catalog update: warnings and required actions

**250 papers, 438 named software families, 2,679 observations and 1,180 individually reviewed uses; 1,499 uses await review.** The fixed 71 Nature, 104 Nature Genetics and 75 AJHG papers remain unchanged. Five existing DoubletFinder uses are reviewed. Five newly identified families add seven observations: scrattch.hicat, scrattch.bigcat, scrattch.mapping, R arrow and PyArrow. All five remain missing. P096's earlier Scrublet summary incorrectly said 0.0.2; its primary paragraph reports 0.2. This correction preserves the previous metadata and changes no selected runtime.

**113 scoped new integrations: 20 native Java subsets and 93 original upstream workflows; 116 including three existing adapters. 266 named families remain missing.** DoubletFinder adds one upstream family credit, with no additional native family credit for its three Java components. This is selected workflow coverage, not the whole tool, biological validity, a reproduced paper or completion of the 250-paper objective. The remaining MILTON requirements are explicitly queued for paper-wide scope/registry review.

**SCT convergence is not qualified.** The synthetic original control produced 3,706 original R warnings: 3,694 fractional-count warnings and 12 iteration-limit warnings, plus 17 messages. Optional glmGamPoi was absent and the original native estimator ran. Inspect complete original conditions and raw consoles; raw/native diagnostic totals are unavailable, never zero. Do not treat source-parity output as a converged SCT analysis. Do not round artificial counts, suppress warnings, change saved hashes or overwrite a qualified profile. Any estimator/dependency change requires a NEW runtime/output and fresh original controls.

Use python3 tools/literature/doubletfinder206/run.py --runtime /permitted/provider --request /trusted/request.rds --out /NEW/output. This is an explicit Python source entry point for unchanged original 2.0.6 R operations, not a bundled Java CLI command. Native Java DoubletFinder206 supplies modelHomotypic, findPk summaries and classification of existing pANN scores; it does not fit a new doublet model. The selected R 4.5.3 / Seurat 5.5.1 profile is a project choice, not an author environment. Use the documented installer only where permitted with --allow-install and --allow-download. Native Windows original runtime analysis/installation skips; use an explicitly permitted Linux/WSL provider.

**P059's modified scrattch.hicat 1.0.9 score > 0.3 workflow remains unimplemented. P096's reported DoubletFinder 3.0 edition remains unresolved.** Skip those dependent reported-method analyses. Deposited author code is retained unchanged under CC-BY-4.0. Its legacy _v3 calls, direct as.numeric pK conversion, unused homotypic-adjusted expected count and fixed metadata column require reconciliation. The selected 2.0.6 pK probe returns a factor, but the author's actual pK type is unknown. The deposited Scrublet Script_5.py has a syntax error at line 21 and was neither executed nor repaired; the paper's Boolean fusion is not established by Script_6.R. Do not substitute 2.0.6 for the literal 3.0 edition or silently repair author code.

**New families remain separate.** P058's BIGCAT edition is unknown; do not borrow P059's 0.0.5. P059 reports scrattch.mapping 0.2 hierarchical marker/centroid assignment; its exact calls and metric are unknown. P058 uses Hierarchical Approximate Nearest Neighbour mapping with an unknown package edition. P059's R arrow 12.0.1 loads chunked Parquet expression and queries DEGs; existing DuckDB support is not this R frontend. P250 declares PyArrow 14.0.2 for MILTON development, without establishing a specific analysis operation. Twelve ordinary figure-arrow mentions are classified as nonsoftware. Generic KNN, clustering, Java Parquet support and the DoubletFinder adapter do not qualify these distinct interfaces.

**SKIP3 skips dependent analysis** when exact prerequisites, download, installation or execution are unavailable or forbidden. Supply only permitted prerequisites and retry NEW paths. **FAIL2 admits no complete result**, including terminal recording failure. Read the exit code, analysis-status.json, WARNINGS.md and MANIFEST.json; a leftover RDS is not success. If recording is blocked, preserve the full console and FINALIZATION_ERROR.json where writable, resolve the named filesystem cause and retry a NEW writable directory. Retain original inputs, literal identifiers, NA/NaN/Inf, complete RDS objects, conditions/calls, raw logs and all available partial outputs. Never refresh historical hashes to admit changed evidence. Trusted RDS input is required. Complete object comparison needs the exact original R provider; a portable hash check alone does not qualify Seurat objects.

Other restrictions remain: Cell Ranger/ARC are excluded by user request; earlier scVelo editions, irrecoverable figures and velociraptor native stack diagnostics retain their recorded limitations. All earlier scientific verifiers and evidence remain unchanged. The full semantic review and implementation goal remains open.
'''


def paragraph(text):
    return re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', html.escape(text.strip()))


NOTICE = '<section id="doubletfinder-required-actions" role="note"><h2>DoubletFinder: warnings and required actions</h2>' + ''.join('<p>' + paragraph(p) + '</p>' for p in WARNING.strip().split('\n\n')[1:]) + '</section>'
STYLE = '\n<style id="doubletfinder-warning-wrap">#doubletfinder-required-actions { overflow-wrap: anywhere; min-width: 0; }</style>\n'


def entry(root, old, readings):
    if old['id'] != 'doubletfinder' or old['implementation_status'] != 'missing' or old['papers'] != [r['paper_id'] for r in readings[:5]]:
        raise ValueError('Expected five separately missing DoubletFinder uses')
    row = copy.deepcopy(old)
    row.update(current_coverage='upstream-adapter-validated', existing_source=PROGRAM,
               coverage_note='Selected original 2.0.6 R operations plus three native Java components; modified HICAT and reported 3.0 remain unqualified.',
               implementation_status='upstream-adapter-validated', implementation_source=PROGRAM,
               implementation_scope='Selected unchanged original 2.0.6 Linux/WSL typed-RDS workflow for five public operations; exact permitted R provider required. Native modelHomotypic, findPk summary and classification of existing pANN scores are separately bounded components, not a new model fit. SCT convergence is not qualified. Unknown author settings and other editions remain unresolved. Not a bundled root CLI or paper reproduction.',
               validation_note='388 native reference cases (378 values/returns, 10 original errors); 3,535 numeric or classification values. 22 complete original R objects, two PNGs, two matching original errors and 18 actual protocol/recording controls. 3,706 original SCT warnings include 12 iteration-limit warnings; review all conditions and raw logs. Convergence is not qualified. Missing/forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result, including recording failure.',
               new_scoped_integration_credit=1, native_family_credit_added=0,
               project_adapter_available=True, canonical_adapter_available=False, root_dispatch_available=False,
               development_candidate_available=True, development_candidate=PROGRAM,
               development_candidate_sha256=pin(root / PROGRAM)['sha256'],
               development_candidate_kind='python-source-entrypoint-requires-exact-original-Linux-WSL-R-provider',
               development_build='tools/literature/doubletfinder206/install_runtime.py',
               development_adapter_cli='python3 ' + PROGRAM + ' --runtime /permitted/provider --request /trusted/request.rds --out /NEW/output',
               selected_upstream_versions=['2.0.6'], selected_R_version='4.5.3', selected_Seurat_version='5.5.1',
               selected_environment_is_author_environment=False, source_roles_reviewed=5,
               native_component_source='src/main/java/org/jlinalg/singlecell/DoubletFinder206.java',
               workflow_validation='../doubletfinder206-workflow-development/VALIDATION.json',
               source_role_coverage='doubletfinder206-role-coverage.json',
               paper_role_coverage={r['paper_id']: r['use_summary'] + ' ' + r['implementation_scope_note'] for r in readings[:5]},
               uncovered_roles=['Modified scrattch.hicat 1.0.9 algorithm and literal reported DoubletFinder 3.0 edition',
                                'Unknown author settings, exact runtime and legacy-script reconciliation',
                                'SCT convergence, independent scientific/biological validity and paper reproduction',
                                'Other operations, versions, platforms and raw/native diagnostic totals'],
               whole_tool_qualified=False, whole_paper_reproduced=False)
    return row


def observation(old, reading, paper, name, role):
    row = copy.deepcopy(old) if old is not None else dict(tool_id=reading['tool_id'], tool=name, analysis_role=role, matched_text=name)
    if old is not None:
        row['source_extraction_before_review'] = copy.deepcopy(old)
    primary = next(a for a in reading['supporting_evidence'] if a['paragraph_index'] == reading['evidence_paragraph_index'])
    row.update(evidence_section=primary['section'], evidence_paragraph_index=primary['paragraph_index'],
               evidence_paragraph_sha256=primary['paragraph_sha256'],
               evidence_selection='individually-reviewed-explicit-primary-source-anchor',
               review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',
               evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/' + paper['pmcid'] + '/ (section: ' + primary['section'] + ')')
    for key in ['use_summary', 'implementation_scope_note', 'version_reported_near_name', 'usage_context', 'supporting_evidence',
                'named_anchor_classification', 'reported_settings', 'uncertainties', 'reported_settings_from_deposited_code', 'deposited_author_code']:
        if key in reading:
            row[key] = copy.deepcopy(reading[key])
    return row


def transition(root, before):
    if set(before) != set(META):
        raise ValueError('Exactly eight complete catalog inputs required')
    inventory = json.loads(before[META[0]])
    summary = json.loads(before[META[1]])
    papers = json.loads(before[PAPERS])
    document = json.loads(before[REVIEWS])
    if (len(inventory), len(papers), sum(len(p['tools']) for p in papers), len(document['reviews'])) != (433, 250, 2672, 1168):
        raise ValueError('Unexpected complete predecessor collections')
    readings = reviews(root)
    i = next(i for i, row in enumerate(inventory) if row['id'] == 'doubletfinder')
    inventory[i] = entry(root, inventory[i], readings)
    for definition in definitions(root):
        if any(r['id'] == definition['id'] for r in inventory):
            raise ValueError('New software family is already present')
        ids = sorted(r['paper_id'] for r in readings if r['tool_id'] == definition['id'])
        inventory.append(dict(definition, paper_count=len(ids), papers=ids, implementation_status='missing',
                              implementation_source=None, implementation_scope=definition['coverage_note'],
                              validation_note='Primary source identities, named anchors and stated roles reviewed. No runtime analysis executed and no implementation credit.',
                              source_roles_reviewed=len(ids), new_scoped_integration_credit=0, native_family_credit_added=0,
                              project_adapter_available=False, canonical_adapter_available=False, root_dispatch_available=False,
                              whole_tool_qualified=False, whole_paper_reproduced=False))
    known = {(r['pmcid'], r['tool_id']) for r in document['reviews']}
    if len(known) != 1168:
        raise ValueError('Duplicate previous reviews')
    for reading in readings:
        key = (reading['pmcid'], reading['tool_id'])
        if key in known:
            raise ValueError('An existing review cannot be duplicated')
        paper = next(p for p in papers if p['paper_id'] == reading['paper_id'])
        indices = [i for i, row in enumerate(paper['tools']) if row['tool_id'] == reading['tool_id']]
        definition = next(t for t in inventory if t['id'] == reading['tool_id'])
        if reading['tool_id'] == 'doubletfinder':
            if len(indices) != 1:
                raise ValueError('Exactly one existing observation required')
            paper['tools'][indices[0]] = observation(paper['tools'][indices[0]], reading, paper, definition['name'], definition['role'])
        else:
            if indices:
                raise ValueError('New observation already present')
            paper['tools'].append(observation(None, reading, paper, definition['name'], definition['role']))
        document['reviews'].append(copy.deepcopy(reading))
        known.add(key)
    correction = load(root, 'scrublet-source-correction.json')
    indices = [i for i, r in enumerate(document['reviews']) if (r['pmcid'], r['tool_id']) == (correction['pmcid'], correction['tool_id'])]
    if len(indices) != 1 or document['reviews'][indices[0]] != correction['before']:
        raise ValueError('Expected exact earlier Scrublet reading required')
    document['reviews'][indices[0]] = copy.deepcopy(correction['after'])
    paper = next(p for p in papers if p['paper_id'] == 'P096')
    scrublet = next(r for r in paper['tools'] if r['tool_id'] == 'scrublet')
    if scrublet['use_summary'] != correction['before']['use_summary'] or scrublet['version_reported_near_name'] != '0.2':
        raise ValueError('Expected confirmed summary-only discrepancy')
    scrublet.update(use_summary=correction['after']['use_summary'], source_correction_note=correction['after']['source_correction_note'])
    updated = copy.deepcopy(summary)
    updated.update(paper_tool_observations=sum(len(p['tools']) for p in papers), distinct_named_tools=len(inventory),
                   implementation_status_counts=dict(Counter(t['implementation_status'] for t in inventory)))
    if (len(inventory), updated['paper_tool_observations'], len(document['reviews']), updated['implementation_status_counts']['missing'], updated['implementation_status_counts']['upstream-adapter-validated'], updated['implementation_status_counts']['native-subset-validated']) != (438, 2679, 1180, 266, 93, 20):
        raise ValueError('Unexpected source/use/integration credit')
    page = before[META[4]].decode()
    match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
    if not match or json.loads(match[2]) != dict(papers=json.loads(before[PAPERS]), tools=json.loads(before[META[0]]), summary=summary):
        raise ValueError('Exact predecessor embedded data required')
    page = page[:match.start(2)] + json.dumps(dict(papers=papers, tools=inventory, summary=updated), ensure_ascii=False).replace('<', '\\u003c') + page[match.end(2):]
    historical = '<details><summary>Previous velociraptor checkpoint (historical)</summary>' + PRIOR_NOTICE + '</details>'
    marker = '<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    warning = before[META[5]].decode()
    for text in [page, warning]:
        if text.count(PRIOR_NOTICE) != 1 or text.count('</head>') != 1:
            raise ValueError('Expected exact old warning panel and document head')
    if page.count('<p id="overview"></p>') != 1 or warning.count(marker) != 1:
        raise ValueError('Expected catalog warning insertion points')
    page = page.replace(PRIOR_NOTICE, historical, 1).replace('<p id="overview"></p>', NOTICE + '<p id="overview"></p>', 1).replace('</head>', STYLE + '</head>', 1)
    warning = warning.replace(PRIOR_NOTICE, historical, 1).replace(marker, marker + NOTICE, 1).replace('</head>', STYLE + '</head>', 1)
    intro = '# 250-paper omics and GWAS toolbox catalog\n\n' + WARNING + '\nRead [DoubletFinder commands, source roles and recovery](../DOUBLET_FINDER_CATALOG_INTEGRATION.md). Current verification: `python tools/literature/doubletfinder_catalog/catalog.py --verify --out build/literature-review/monocle-runtime/NEW-catalog-check --rescan-sources --runtime /permitted/provider` (add `--wsl-distro NAME` on Windows). The full goal remains open.\n\n'
    paper_bytes = encoded(papers)
    if before[PAPERS].count(b'\n') == before[PAPERS].count(b'\r\n'):
        paper_bytes = paper_bytes.replace(b'\n', b'\r\n')
    return {META[0]: encoded(inventory), META[1]: encoded(updated), PAPERS: paper_bytes, REVIEWS: encoded(document),
            META[2]: (intro + '<details><summary>Previous checkpoints (historical)</summary>\n\n' + before[META[2]].decode() + '\n</details>\n').encode(),
            META[3]: (WARNING + '\n<details><summary>Previous warnings (historical)</summary>\n\n').encode() + before[META[3]] + b'\n</details>\n',
            META[4]: page.encode(), META[5]: warning.encode()}


def check_transition(root, before, after):
    expected = transition(root, before)
    if set(after) != set(META) or any(after[n] != expected[n] for n in META):
        raise ValueError('Catalog differs from exact source-bound admission')
    oldi, newi = json.loads(before[META[0]]), json.loads(after[META[0]])
    if [r for r in oldi if r['id'] != 'doubletfinder'] != [r for r in newi if r['id'] not in {'doubletfinder', *[d['id'] for d in definitions(root)]}]:
        raise ValueError('Unrelated previous families changed')
    oldr, newr = json.loads(before[REVIEWS])['reviews'], json.loads(after[REVIEWS])['reviews']
    correction = ('PMC11631765', 'scrublet')
    if [r for r in oldr if (r['pmcid'], r['tool_id']) != correction] != [r for r in newr[:1168] if (r['pmcid'], r['tool_id']) != correction]:
        raise ValueError('Unrelated previous reviews changed')
    for old, new in zip(json.loads(before[PAPERS]), json.loads(after[PAPERS])):
        if {k: v for k, v in old.items() if k != 'tools'} != {k: v for k, v in new.items() if k != 'tools'}:
            raise ValueError('Paper identity or bibliography changed')
        ignored = {'doubletfinder'} | ({'scrublet'} if old['paper_id'] == 'P096' else set())
        added = {d['id'] for d in definitions(root)}
        if [r for r in old['tools'] if r['tool_id'] not in ignored] != [r for r in new['tools'] if r['tool_id'] not in ignored | added]:
            raise ValueError('Unrelated previous paper observation changed')
    return dict(papers=250, named_tools=438, observations=2679, reviewed_uses=1180, unreviewed_uses=1499,
                new_scoped_integrations=113, missing=266, unchanged_previous_families=432,
                unchanged_previous_observations=2666, unchanged_previous_reviews=1167,
                enriched_existing_observations=5, corrected_existing_observations=1,
                added_observations=7, added_reviews=12, corrected_existing_reviews=1,
                new_registry_definitions=5, new_scoped_integration_credit=1, native_family_credit_added=0,
                whole_goal_complete=False)
