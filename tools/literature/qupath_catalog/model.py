# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one original runtime, enrich eleven old uses, preserve the already-reviewed P040 use."""
from collections import Counter
import copy,html,json,re
from palette_allelic_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,rawpin,pin
from qupath_catalog.source import CURATION,INTAKE,EXPECTED,reviews

EVIDENCE = 'docs/literature/qupath-catalog-integration'
GUIDE = 'docs/literature/QUPATH_CATALOG_INTEGRATION.md'
PROOF = DATA + '/qupath-catalog-validation.json'
COMPATIBILITY = DATA + '/qupath-reviews-shared-source-compatibility.json'
COVERAGE = DATA + '/qupath051-role-coverage.json'
WARNING = '''
## Current QuPath coverage: warnings and required actions

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,143 individually reviewed uses; 1,528 uses await individual review.** All 250 exact primary XMLs were searched for QuPath: 12 paper-family uses and 16 named paragraphs. Eleven uses gain source reviews; P040's existing review is preserved unchanged. An acknowledgement and a downstream output-table paragraph do not establish additional image-analysis executions. The fixed cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23; no time extension was needed. The full semantic review and implementation objective remain open.

**107 new scoped integrations are available: 20 native Java subsets and 87 upstream workflows; 110 including 3 existing adapters. 266 named families remain missing.** QuPath gains one upstream workflow credit. The explicit standalone Java controller invokes the complete unchanged original QuPath 0.5.1 headless Groovy engine and its embedded Java 21.0.2. It needs a permitted private Windows/Linux x86_64 original provider, a trusted caller script and complete declared inputs/outputs. The canonical executable and root dispatch are unchanged. Read QUPATH_CATALOG_INTEGRATION.md and QUPATH051_WORKFLOW_DEVELOPMENT.md for copyable setup, execution and recovery commands.

**P009 and P040 report QuPath 0.5.1; other reported editions remain separate.** P020 reports 0.6.0, P049 0.4.3, P058 0.4.1 and P158 literally v.0.2.3 to v.0.4.3. Six other papers do not report an edition. Do not substitute 0.5.1 silently. The scientific controls cover six synthetic H-DAB jobs, 72 detections and all 13 complete portable exports per controller on Windows Java 21/25 and Linux Java 21. They do not independently qualify fluorescence/proximity, RNAscope puncta, image registration, custom classifiers or author pipelines. P158's custom StarDist extension/model is not supplied by the stock provider. P040's numeric stain vectors and author ROIs remain unknown, and its printed area-unit wording is retained. No paper is reproduced by this admission; no native segmentation port or whole-tool qualification is claimed.

**SKIP3 skips dependent analysis** when required downloads, installation, original runtime, source, provider, extension/model, display or execution are missing/forbidden. Install only where permitted; supply the named prerequisites, verify the complete native-platform provider and retry NEW paths. A READY setup record is not an analysis result. Source-only checkouts and controller JARs do not contain the original runtime/JRE. Groovy runs with account permissions and requires explicit trusted-code and execution policies. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Retain every input, full image/hierarchy/table, original returned object, condition, status, process log and failed case; resolve the stated cause and retry NEW paths. Review every original warning using WARNINGS.md, ORIGINAL_CONDITIONS.tsv and full logs. The controller requires original exit zero, no selected-format ERROR headers, exactly one completion marker and the entire declared result population. Do not admit partial outputs, drop blank/failed images or relabel a zero-exit logged error successful. Hashes establish integrity, not authenticity, independent statistical validity or biological correctness. This catalog audit executes no new original analysis or installation.

**Other retained limits still apply.** P204's reported AllelicSeries 1.1.2 remains missing; the separate 0.1.1.2 candidate earns zero credit, and selected native burden components do not supply full COAST/SKAT-O or binary/discrete calibration. P054's wesanderson palette/count/MFOD1 mapping remain unknown; viridisLite and R graphics remain separate. ImageJ distributions/plugins are separate, and histogram matching must not be used for intensity quantification. Consult the retained checkpoint guides for all earlier tool-specific restrictions.
'''

def notice():
    parts = ['<p>' + re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',html.escape(p),flags=re.S) + '</p>'
             for p in WARNING.strip().split('\n\n')[1:]]
    return ('<section id="qupath-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">'
            '<h2>Current toolbox coverage: warnings and required actions</h2>' + ''.join(parts) + '</section>')

NOTICE = notice()
ROLE_LIMITS = {
 'P009':'Reported 0.5.1. The selected original script engine is available; the H-DAB controls do not independently qualify this DAPI fluorescence/proximity pipeline. Manual thresholds, source channels, author ROIs, 50-micrometer T-cell and 20-micrometer nearest-NSC choices, self-exclusion and section/mouse aggregation must be supplied explicitly.',
 'P020':'Reported 0.6.0 differs from selected 0.5.1. No edition equivalence is established. Author foreground pixel classifiers, manual stroma exclusions, thresholds and pathologist decisions are not recovered.',
 'P021':'Edition unreported. Selected 0.5.1 is an explicit caller choice. H-DAB controls do not qualify RNAscope subcellular puncta detection or recover author scripts/thresholds. Paragraph 78 analyzes exported tables downstream in R; it is not another QuPath execution. Fiji stitching/alignment remains separate.',
 'P024':'Edition unreported. Selected 0.5.1 is an explicit caller choice. H-DAB controls do not qualify the DAPI/RNAscope puncta pipeline. Manual ROIs, segmentation/spot thresholds and author scripts remain unknown; Fiji stitching, custom Python cell-type summaries, CLR and group tests remain separate.',
 'P040':'Reported 0.5.1. The complete original engine is available and synthetic H-DAB positive-cell controls passed. Numeric stain vectors and author ROI selections remain unknown; the 200-by-200-micrometer paper ROIs are not reconstructed. Paper threshold values 0.08/0.16/0.24 remain literal; printed area-unit wording is not silently repaired to application units. No exact paper invocation or biological reproduction is established. The previous individual review remains unchanged.',
 'P049':'Reported 0.4.3 differs from selected 0.5.1. H-DAB controls do not qualify multiplex RNA-FISH/subcellular spots. LIF series, RGC ROIs, single-measurement/composite classifier choices, thresholds and trained resources remain separate.',
 'P056':'Edition unreported. The source names IHC/RNAscope quantification without specifying an algorithm, thresholds or export structure. The selected original 0.5.1 engine is available; no missing settings or paper pipeline is inferred from this generic mention.',
 'P058':'Reported 0.4.1 differs from selected 0.5.1; no other edition is inferred for the second analysis paragraph. Author polygon ROIs, GFP/mScarlet classifier thresholds and coronal-series scaling remain separate; H-DAB controls do not qualify the fluorescence pipeline. Paragraph 73 acknowledges advice and is not another execution. Downstream ggpubr/Wilcoxon tests are separate.',
 'P098':'Edition unreported. The source establishes blinded tumor IHC quantification without an identified algorithm or thresholds. The selected original 0.5.1 engine is available; blinding is not an algorithm setting and no author pipeline is inferred.',
 'P158':'Literal reported range v.0.2.3 to v.0.4.3 excludes selected 0.5.1. Stock QuPath does not recover the custom StarDist extension/classifier, trained model or manual intensity thresholds. H-DAB controls do not qualify that nuclei pipeline. Supply permitted dependencies or skip the dependent analysis; downstream Python summaries remain separate.',
 'P161':'Edition unreported. H&E slide alignment and pathologist neighborhood annotations are source roles, but the registration algorithm, transforms and annotation decisions are unknown. Selected 0.5.1 H-DAB controls do not qualify image registration or author annotation. GeoPandas, SCANPY, CellPhoneDB and CytoSignal remain separate.',
 'P226':'Edition unreported. H2B-mRuby nuclei detection and 2-micrometer perinuclear expansion require the author morphology/fluorescence filters, whose thresholds are unknown. Selected 0.5.1 H-DAB controls do not qualify this fluorescence pipeline. Nikon NIS Elements rolling-ball preprocessing remains separate.'
}

def entry(root,old):
    if old['id'] != 'qupath' or old['implementation_status'] != 'missing' or old['paper_count'] != 12 or old['papers'] != list(EXPECTED):
        raise ValueError('Unexpected previous QuPath population/status')
    result = copy.deepcopy(old)
    jar = 'docs/literature/qupath051-workflow-development/qupath051-windows21-development.jar'
    result.update(current_coverage='upstream-adapter-validated',existing_source='src/main/java/org/jlinalg/cli/QuPath051WorkflowCli.java',
        coverage_note='Original QuPath 0.5.1 headless Groovy workflow with a qualified Java controller; complete native-platform provider and trusted caller script required. Scientific controls are synthetic H-DAB; other editions, author ROIs/stains/models and fluorescence/RNAscope/registration pipelines remain separately unresolved.',
        implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/QuPath051WorkflowCli.java',
        implementation_scope='Complete unchanged original QuPath 0.5.1 headless script engine, embedded Java 21.0.2 and lossless declared inputs/results through an explicit standalone Java controller. Requires a permitted private Windows/Linux x86_64 provider and trusted caller script. Scientific qualification: six synthetic H-DAB image jobs; author fluorescence/proximity, RNAscope, registration, StarDist models and other editions are separate. Read QUPATH_CATALOG_INTEGRATION.md. This is one scoped upstream workflow, not a native segmentation port or reproduced paper.',
        validation_note='Each Windows Java 21/25 and Linux Java 21 controller matched all 13 complete portable exports across six jobs and 72 detections against independent direct original runs. All 12 raw/fixture QPDATA files per controller are retained; opaque QPDATA byte parity is not claimed. Windows/Linux protocol controls: 15/19; setup/archive controls: 6+5 per OS; three actual private installations including one public network setup. All 1,381 archived files and four frozen-verifier refusals are retained. No GUI or paper reproduction is qualified.',
        source_roles_reviewed=12,new_scoped_integration_credit=1,whole_tool_qualified=False,whole_paper_reproduced=False,
        paper_exact_unknown_settings_available=False,project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
        development_candidate_available=True,development_candidate=jar,development_candidate_sha256=pin(root/jar)['sha256'],
        development_build='tools/literature/qupath051/build.py',development_adapter_cli='java -jar ' + jar + ' --request caller-request.json',
        workflow_validation='../qupath051-workflow-development/VALIDATION.json',source_role_coverage='qupath051-role-coverage.json',
        selected_upstream_version='0.5.1',selected_source_commit='4e6bfc31904be7ee39c10d457149cb962002e1f2',
        paper_role_coverage={r['paper_id']:r['use_summary'] + ' ' + ROLE_LIMITS[r['paper_id']] for r in reviews(root)},
        uncovered_roles=['Other reported editions and unreported author runtime identities','Author images, ROIs, numeric stain vectors, manual thresholds and exact scripts',
            'Fluorescence/proximity, RNAscope puncta and image registration independent scientific qualification',
            'Custom StarDist extension/model and trained classifiers','GUI user experience and biological/paper reproduction'])
    return result

def transition(root,before):
    if set(before) != set(META): raise ValueError('Exactly eight predecessor metadata paths required')
    inventory = json.loads(before[META[0]]);papers = json.loads(before[PAPERS]);document = json.loads(before[REVIEWS]);summary = json.loads(before[META[1]])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews'])) != (432,250,2671,1132):
        raise ValueError('Unexpected predecessor catalog population')
    index = next(i for i,t in enumerate(inventory) if t['id'] == 'qupath');inventory[index] = entry(root,inventory[index])
    rows = reviews(root);known = {(r['pmcid'],r['tool_id']) for r in document['reviews']}
    if len(known) != 1132: raise ValueError('Duplicate previous individual review')
    observed = {p['paper_id'] for p in papers for t in p['tools'] if t['tool_id'] == 'qupath'}
    if observed != set(EXPECTED): raise ValueError('Previous paper-family population differs')
    for review in rows:
        paper = next(p for p in papers if p['paper_id'] == review['paper_id'])
        matches = [i for i,t in enumerate(paper['tools']) if t['tool_id'] == 'qupath']
        if len(matches) != 1: raise ValueError('Duplicate QuPath paper observation')
        if review['paper_id'] == 'P040':
            if (paper['pmcid'],'qupath') not in known: raise ValueError('Existing P040 review missing')
            continue
        if (paper['pmcid'],'qupath') in known: raise ValueError('Unexpected already-reviewed QuPath use')
        old = paper['tools'][matches[0]];row = copy.deepcopy(old);row['source_extraction_before_review'] = copy.deepcopy(old)
        anchor = review['supporting_evidence'][0]
        row.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],
            evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_selection='individually-reviewed-explicit-primary-analysis-anchor',
            review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',
            evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/' + paper['pmcid'] + '/ (section: ' + anchor['section'] + ')',
            **{k:copy.deepcopy(review[k]) for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence','named_anchor_classification']})
        paper['tools'][matches[0]] = row
        appended = copy.deepcopy(review);appended.pop('paper_id');document['reviews'].append(appended)
    updated = copy.deepcopy(summary);updated['implementation_status_counts'] = dict(Counter(r['implementation_status'] for r in inventory))
    counts = dict(summary['implementation_status_counts']);counts['missing'] -= 1;counts['upstream-adapter-validated'] += 1
    if updated['implementation_status_counts'] != counts or (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing']) != (20,87,266):
        raise ValueError('Unexpected integration credit')
    page = before[META[4]].decode('utf8');match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2]) != dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):
        raise ValueError('Predecessor rendered data differs')
    page = page[:match.start(2)] + json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c') + page[match.end(2):]
    historical = '<details><summary>Previous palette and allelic-series checkpoint (historical)</summary>' + PRIOR_NOTICE + '</details>'
    if page.count(PRIOR_NOTICE) != 1 or page.count('<p id="overview"></p>') != 1: raise ValueError('Predecessor notice/overview differs')
    page = page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE + '<p id="overview"></p>',1)
    warnings = before[META[5]].decode('utf8');marker = '<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    if warnings.count(PRIOR_NOTICE) != 1 or warnings.count(marker) != 1: raise ValueError('Predecessor warning notice differs')
    warnings = warnings.replace(PRIOR_NOTICE,historical,1).replace(marker,marker + NOTICE,1)
    raw = encoded(papers)
    if before[PAPERS].count(b'\n') == before[PAPERS].count(b'\r\n'): raw = raw.replace(b'\n',b'\r\n')
    intro = '# 250-paper omics and GWAS toolbox catalog\n\n' + WARNING + '\nRead [QuPath source roles, commands and recovery](../QUPATH_CATALOG_INTEGRATION.md). Validate with `python tools/literature/qupath_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-qupath-catalog-check --rescan-sources`. The full semantic review and implementation objective remain open.\n\n'
    return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:raw,REVIEWS:encoded(document),
        META[2]:(intro + '<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n' + before[META[2]].decode('utf8') + '\n</details>\n').encode('utf8'),
        META[3]:(WARNING + '\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode('utf8') + before[META[3]] + b'\n</details>\n',
        META[4]:page.encode('utf8'),META[5]:warnings.encode('utf8')}

def check_transition(root,before,after):
    expected = transition(root,before)
    if set(after) != set(META) or any(after[n] != expected[n] for n in META):
        raise ValueError('Catalog differs from exact one-workflow and eleven-review admission')
    return dict(papers=250,named_tools=432,observations=2671,reviewed_uses=1143,unreviewed_uses=1528,new_scoped_integrations=107,missing=266,
        unchanged_previous_families=431,unchanged_previous_observations=2660,unchanged_previous_reviews=1132,
        enriched_existing_observations=11,added_observations=0,added_reviews=11,new_scoped_integration_credit=1,
        existing_P040_review_unchanged=True,whole_goal_complete=False)
