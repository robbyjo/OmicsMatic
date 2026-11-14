# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append two source-bound families and award one qualified native subset."""
from collections import Counter
import copy, html, json, re
from imagej_catalog.model import ROOT, META, PAPERS, REVIEWS, DATA, NOTICE as PRIOR_NOTICE, encoded, rawpin, pin
from palette_allelic_catalog.source import REGISTRY, CURATION, definitions

EVIDENCE = 'docs/literature/palette-allelic-catalog'
GUIDE = 'docs/literature/PALETTE_ALLELIC_CATALOG.md'
PROOF = DATA + '/palette-allelic-catalog-validation.json'
COMPATIBILITY = DATA + '/palette-allelic-reviews-shared-source-compatibility.json'
COVERAGE = DATA + '/palette-allelic-role-coverage.json'
WARNING = '''
## Current palette and allelic-series coverage: warnings and required actions

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,132 individually reviewed uses.** Two newly recorded paper-family uses cover all 27 named primary paragraphs in the exact 250-source census. All 430 previous families, 2,669 observations and 1,130 reviews remain unchanged. The fixed cohort is 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23; no time extension was needed. The full semantic review and implementation objective remain open.

**106 new scoped integrations are available: 20 native Java subsets and 86 upstream workflows; 109 including 3 existing adapters. 267 named families remain missing.** Only wesanderson gains new implementation credit here. Its native scalar 0.3.7 palette engine and explicit standalone executable have complete original controls. The separate original R provider needs its documented permitted R 4.5.3 runtime. The canonical executable and root dispatch are unchanged. Component guides retain their pre-admission qualification snapshots; this catalog records the later admission.

**P054 reports wesanderson 0.3.7 for MFOD1 ontology colors, but no palette, color count, category mapping or discrete/continuous call.** Do not infer these choices. The paper's R 4.2.3 to R 4.4.0 differs from the original reference R 4.5.3. Native scalar colors do not supply R graphics or general R-language coercion. The separately named viridisLite 0.4.2 gradients are not covered by this admission. Read WESANDERSON037_WORKFLOW_DEVELOPMENT.md and PALETTE_ALLELIC_CATALOG.md for exact operations and explicit commands.

**P204 reports AllelicSeries 1.1.2; the separately tested 0.1.1.2 candidate is not proven equivalent. The reported edition remains missing and earns zero credit.** The candidate runs unchanged original R exports, including full COAST/COASTSS/SKAT-O, in single-call or batch workflows. Native Java supplies selected summary burden components, not full COAST/SKAT-O. Quantitative-trait derivation does not establish binary/discrete calibration or ancestry robustness. Supplied effects/SEs, MAF, LD and annotations must already align; no author preprocessing, missing-LD assumption, 20K-gene timing or paper reproduction is inferred. Read ALLELICSERIES0112_BATCH.md and its provider instructions; explicitly choose this candidate only when appropriate.

**SKIP3 skips dependent analysis** when required downloads, installation, source, compiler, runtime, provider, display or execution are missing/forbidden. Supply only permitted prerequisites using the linked guides and retry NEW paths. Native palette generation needs Java, not R. Original AllelicSeries execution needs the ready private Linux/WSL R 4.5.3 provider. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Retain inputs, original returned objects/errors, all conditions, complete indexes, status and warnings; resolve the named cause and retry NEW paths. Review every original warning and justify or correct the input/model choice. A low-level scalar zero returned before association computation, an ambiguous zero, an original error or a nonfinite return is retained but unadmitted. A genuinely computed probability zero with explicit positive SEs is preserved. Failed batches retain successful entries but admit no complete batch. Do not remove failed genes or relabel a partial batch as complete. ImageJ distributions/plugins remain separate; histogram matching must not be used for intensity quantification. Hashes establish integrity, not producer authentication or biological correctness. This catalog audit executes no new original analysis or installation.
'''

def notice():
    parts = ['<p>' + re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', html.escape(p), flags=re.S) + '</p>'
             for p in WARNING.strip().split('\n\n')[1:]]
    return ('<section id="palette-allelic-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">'
            '<h2>Current toolbox coverage: warnings and required actions</h2>' + ''.join(parts) + '</section>')

NOTICE = notice()

def reviews(root):
    rows = json.loads((root / CURATION).read_bytes())['reviews']
    if len(rows) != 2 or [(r['paper_id'],r['tool_id'],r['version_reported_near_name']) for r in rows] != [('P054','wesanderson','0.3.7'),('P204','allelicseries','1.1.2')]:
        raise ValueError('Individual review identity or literal reported edition differs')
    return rows

def entries(root):
    wes, allelic = copy.deepcopy(definitions(root))
    palette_review, allelic_review = reviews(root)
    shared = dict(paper_count=1, source_roles_reviewed=1, whole_tool_qualified=False, whole_paper_reproduced=False,
                  paper_exact_unknown_settings_available=False, canonical_adapter_available=False, root_dispatch_available=False)
    jar = 'docs/literature/wesanderson037-workflow-development/wesanderson037-native-windows21-development.jar'
    wes.update(**shared, papers=['P054'], implementation_status='native-subset-validated', implementation_source=wes['existing_source'],
        implementation_scope='Native Java wesanderson 0.3.7 bounded scalar discrete/continuous palette vectors, attributes and complete saved-result verification; 24 immutable palettes. Explicit standalone executable; original R 4.5.3 provider is separate. P054 palette/count/MFOD1 mapping and call are unknown. R graphics, general R coercion and viridisLite gradients are outside the native subset. Read PALETTE_ALLELIC_CATALOG.md.',
        validation_note='Three platforms each matched 6,108 original API cases and 224,445 returned colors, with 3,397 original failures retained; 72 CLI cases and 63 complete saved replays per platform. Two actual private original installations, 9 setup refusals and Windows/Linux offline native rebuilds passed. Earlier 604 mismatches and corrected-source controls remain retained; no paper palette was inferred.',
        project_adapter_available=True, development_candidate_available=True, development_candidate=jar,
        development_candidate_sha256=pin(root / jar)['sha256'], development_build='tools/literature/wesanderson037/build.py',
        development_adapter_cli='java -jar ' + jar + ' generate --name GrandBudapest1 --n 4 --type discrete --out NEW-output',
        workflow_validation='../wesanderson037-workflow-development/VALIDATION.json', source_role_coverage='palette-allelic-role-coverage.json',
        new_scoped_integration_credit=1, paper_role_coverage={'P054':wes['coverage_note'] + ' ' + palette_review['implementation_scope_note']},
        uncovered_roles=['Unknown paper palette, color count, discrete/continuous call and MFOD1 category mapping',
                         'Author R4.2.3 to R4.4.0 runtime and supplementary plotting code', 'R graphics and general R-language coercion', 'Separate viridisLite continuous gradients'])
    entry = 'tools/literature/allelicseries0112/batch.py'
    allelic.update(**shared, papers=['P204'], implementation_status='missing', implementation_source='',
        implementation_scope='The reported AllelicSeries 1.1.2 edition remains missing. Explicit 0.1.1.2 candidate: unchanged original R single-call and batch exports, including COAST/COASTSS/SKAT-O; selected native Java summary burden components are separate. Requires the ready private Linux/WSL R 4.5.3 provider; the Python entry point is source, not a standalone JAR. Quantitative traits, aligned supplied inputs and original model limitations apply. No 20K-gene benchmark or author preprocessing is reproduced. Read PALETTE_ALLELIC_CATALOG.md and ALLELICSERIES0112_BATCH.md.',
        validation_note='Candidate 0.1.1.2 only: 1,026 complete batch calls compared with an independent unchanged original package, including 1,022 complete returned objects and 4 original errors; all identifiers, conditions and RNG states compared. All 16 original exports, 8 batch refusals, 5 scalar/real-zero controls and lossless tables passed. Native 539 cases/2,623 values matched on three Java platforms. These controls do not establish reported 1.1.2 equivalence or binary/discrete calibration.',
        project_adapter_available=False, development_candidate_available=True, development_candidate=entry,
        development_candidate_sha256=pin(root / entry)['sha256'], development_candidate_kind='python-source-entrypoint-requires-qualified-provider',
        development_build='tools/literature/allelicseries0112/provider.py',
        development_adapter_cli='python tools/literature/allelicseries0112/batch.py --provider READY-provider --function COASTSS --arguments-rds aligned-gene-requests.rds --allow-analysis --out NEW-output',
        native_candidate_component='src/main/java/org/jlinalg/genetics/AllelicSeries0112.java',
        available_candidate_versions=['0.1.1.2'], reported_version_unresolved='1.1.2', paper_build_equivalence_proven=False,
        workflow_validation='../allelicseries0112-batch-development/VALIDATION.json', source_role_coverage='palette-allelic-role-coverage.json',
        new_scoped_integration_credit=0, paper_role_coverage={'P204':allelic['coverage_note'] + ' ' + allelic_review['implementation_scope_note']},
        uncovered_roles=['Reported1.1.2 edition and author runtime/settings', 'Author input preparation, allele/variant alignment, annotation and LD provenance',
                         'Binary/discrete trait calibration and ancestry-specific model validation', 'Paper20K-gene performance and biological reproduction'])
    return wes, allelic

def transition(root, before):
    if set(before) != set(META): raise ValueError('Exactly eight predecessor metadata paths required')
    inventory = json.loads(before[META[0]]); papers = json.loads(before[PAPERS]); old_reviews = json.loads(before[REVIEWS]); summary = json.loads(before[META[1]])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(old_reviews['reviews'])) != (430,250,2669,1130):
        raise ValueError('Unexpected predecessor catalog population')
    added = entries(root)
    if {r['id'] for r in inventory} & {r['id'] for r in added}: raise ValueError('Named software family already present')
    inventory += list(added)
    named = {(m['paper_id'],m['tool_id'],m['paragraph_index']):m for m in json.loads((root / EVIDENCE / 'CENSUS.json').read_bytes())['matches']}
    for review in reviews(root):
        paper = next(p for p in papers if p['paper_id'] == review['paper_id'])
        tool = next(t for t in added if t['id'] == review['tool_id'])
        if any(t['tool_id'] == tool['id'] for t in paper['tools']): raise ValueError('Paper-family use already present')
        a = named[(paper['paper_id'],tool['id'],review['evidence_paragraph_index'])]
        paper['tools'].append(dict(tool_id=tool['id'],tool=tool['name'],analysis_role=tool['role'],
            evidence_section=a['section'],evidence_paragraph_index=a['paragraph_index'],matched_text=a['matched_names'][0],
            evidence_paragraph_sha256=a['paragraph_sha256'],evidence_selection='individually-reviewed-explicit-primary-analysis-anchor',
            review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',
            **{k:copy.deepcopy(review[k]) for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence']},
            evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/' + paper['pmcid'] + '/ (section: ' + a['section'] + ')'))
        row = copy.deepcopy(review); row.pop('paper_id'); old_reviews['reviews'].append(row)
    updated = copy.deepcopy(summary)
    updated.update(paper_tool_observations=2671,distinct_named_tools=432,implementation_status_counts=dict(Counter(r['implementation_status'] for r in inventory)))
    counts = dict(summary['implementation_status_counts']);counts['native-subset-validated'] += 1;counts['missing'] += 1
    if updated['implementation_status_counts'] != counts or counts['native-subset-validated'] != 20 or counts['missing'] != 267:
        raise ValueError('Unexpected integration credit')
    page = before[META[4]].decode('utf8'); m = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not m or json.loads(m[2]) != {'papers':json.loads(before[PAPERS]),'tools':json.loads(before[META[0]]),'summary':summary}:
        raise ValueError('Predecessor rendered data differs')
    page = page[:m.start(2)] + json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c') + page[m.end(2):]
    historical = '<details><summary>Previous image-analysis checkpoint (historical)</summary>' + PRIOR_NOTICE + '</details>'
    if page.count(PRIOR_NOTICE) != 1 or page.count('<p id="overview"></p>') != 1: raise ValueError('Predecessor notice/overview differs')
    page = page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE + '<p id="overview"></p>',1)
    warnings = before[META[5]].decode('utf8'); marker = '<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    if warnings.count(PRIOR_NOTICE) != 1 or warnings.count(marker) != 1: raise ValueError('Predecessor warning notice differs')
    warnings = warnings.replace(PRIOR_NOTICE,historical,1).replace(marker,marker + NOTICE,1)
    paper_raw = encoded(papers)
    if before[PAPERS].count(b'\n') == before[PAPERS].count(b'\r\n'): paper_raw = paper_raw.replace(b'\n',b'\r\n')
    intro = '# 250-paper omics and GWAS toolbox catalog\n\n' + WARNING + '\nRead [current source roles, executable commands and recovery](../PALETTE_ALLELIC_CATALOG.md). Validate with `python tools/literature/palette_allelic_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-palette-allelic-check`; `--rescan-sources` checks all250 exact cached primary XMLs. Full semantic review and implementation remain open.\n\n'
    return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:paper_raw,REVIEWS:encoded(old_reviews),
        META[2]:(intro + '<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n' + before[META[2]].decode('utf8') + '\n</details>\n').encode('utf8'),
        META[3]:(WARNING + '\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode('utf8') + before[META[3]] + b'\n</details>\n',
        META[4]:page.encode('utf8'),META[5]:warnings.encode('utf8')}

def check_transition(root, before, after):
    expected = transition(root,before)
    if set(after) != set(META) or any(after[n] != expected[n] for n in META):
        raise ValueError('Catalog differs from exact two-family source-role and one-native-subset admission')
    return dict(papers=250,named_tools=432,observations=2671,reviewed_uses=1132,new_scoped_integrations=106,missing=267,
                unchanged_previous_families=430,unchanged_previous_observations=2669,unchanged_previous_reviews=1130,
                added_observations=2,added_reviews=2,new_scoped_integration_credit=1,allelicseries_credit=0,whole_goal_complete=False)
