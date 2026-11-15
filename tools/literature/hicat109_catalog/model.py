# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One existing HICAT family and one coverage note; change no primary facts."""
from collections import Counter
import copy,json,re
from bigcat_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph
from hicat109_catalog.source import MODULE,TOOL,NOTES,reviews,original_reviews
from hicat109_catalog.bridge import EVIDENCE

PROGRAM='tools/literature/scrattchhicat109/run.py'
GUIDE='docs/literature/HICAT109_CATALOG_INTEGRATION.md'
PROOF=DATA+'/hicat109-catalog-validation.json'
COVERAGE=DATA+'/hicat109-role-coverage.json'
COMPATIBILITY=DATA+'/hicat109-reviews-shared-source-compatibility.json'
WARNING='''
## HICAT 1.0.9: warnings and required actions

**250 papers, 438 named families, 2,679 observations and 1,180 individually reviewed uses; 1,499 uses await review.** The fixed 2023-10-23 through 2026-10-23 cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers. All paper identities, reported editions, primary settings, source anchors and uncertainties are unchanged. Only one existing P059 HICAT implementation coverage note changes, in its observation and review. The later HICAT predecessor mentions do not establish additional current HICAT analyses.

**117 scoped new integrations: 20 native Java subsets and 97 upstream workflows; 120 including three existing adapters. 262 named families remain missing.** HICAT adds one upstream family credit and zero native credits. The independent Java strict-cutoff selector is a component of this same family, not a second HICAT implementation. The full 250-paper review and implementation goal remains open. Scoped source controls do not establish whole-tool validity or paper reproduction.

P059 reports modified DoubletFinder in scrattch.hicat 1.0.9 and removal of cells with score strictly greater than 0.3. Equality is retained; the Java helper reports unavailable numeric comparisons as UNKNOWN without imputing scores. The 62 housekeeping genes describe a separate QC score, not an established doublet-scoring gene panel. The selected unchanged doubletFinder function comes from prepublication commit 24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4. The actual author commit, exact function, caller arguments, gene panel, seed and environment remain unknown. Project source/environment choices and synthetic inputs are not recovered author settings. Standard DoubletFinder pANN and BIGCAT marker-doublet detection are separate methods.

**Keep upstream source and runtime private.** HICAT DESCRIPTION labels GPL-3, while LICENSE contains different Allen Institute terms including a noncommercial redistribution restriction. Both are preserved without resolving the discrepancy. The project supplies its own adapter, source identities, synthetic scientific records and independently written Java comparison rule. It supplies no original HICAT source tree, archive, installed library, runtime binary or source-derived native scoring port. Complete native compilation consoles remain private and unchanged because their diagnostics contain source excerpts; the public receipt gives their complete hashes and paths. Complete original scientific objects, analysis conditions, RNG and raw analysis consoles remain in the public numeric/control records.

Twenty-six direct-original/worker controls have 14 complete returns and 12 original failures, including a 10,001-real-cell sampled-PCA control, dense/dgC/dgT inputs, odd neighbourhoods, literal/duplicate/colliding IDs and source failure boundaries. Both actual public installer branches and their installed-runtime analyses passed, as did 15 Linux public-runner guards and the Windows prerequisite skip. The packaged Java selector matches 17,697 literal R comparisons and 13,824 successful original control scores on Windows and Linux; its permanent project JUnit test passed. The 1,337 logical evidence files have 636 unique payloads and 36 qualified source identities. Two unchanged copies were accepted and six paired fabricated scientific copies rejected after logical inventory resealing; those tests do not claim ZIP-envelope resealing. Fresh replay regenerates every complete project request and compares complete original returns, conditions/errors and RNG. It performs synthetic scoring, not paper reproduction.

**Original errors remain errors.** Plotting fails on unqualified aes without ggplot2 attached and on the original misspelled artifical.doublet.score return with ggplot2 attached. Default plotting is FALSE. Original matrix-dimension errors at k=1/2 and singleton artificial cells, absent genes, zero artificial cells, fewer than ten real cells, NA/Inf counts and warning-as-error partial matching remain failures. A dense zero-count-column control fails while its sparse counterpart returns. No source repair, sparse-to-dense coercion, identifier correction or fallback is performed. An expected-error test passing does not admit a scientific result. Original help and native compiler warnings remain retained; unavailable native diagnostic totals are never assumed zero.

Use python3 tools/literature/scrattchhicat109/run.py with an exact completed private --runtime provider, a trusted --request RDS containing the original count matrix, explicit gene panel and whole RNG state, and a NEW --out directory. This is a Python source entry point and independent Java API, not a root Java CLI subcommand. The installer requires permitted Linux/WSL and explicit --allow-install. Add --allow-download only where permitted; otherwise provide both exact source archives and a complete permitted package cache. Analysis itself downloads and installs nothing. RDS requests are trusted R inputs, not a code sandbox.

**SKIP3 skips dependent analysis** when an exact required source, dependency, permission, installation, download or Linux execution is unavailable or forbidden. **FAIL2 admits no complete result**, including recording failure after the original operation returned. Only a final public-runner COMPLETED.json admits that recorded analysis. Preserve whole objects/conditions/RNG, IDs, NA/NaN/Inf and partials; read WARNINGS.md, FAILURE.json, original/R-status.json and all raw consoles. For source errors, error.message in the original status contains the unmodified R message. Supply the exact permitted prerequisite or resolve the named input/filesystem cause and retry NEW paths. A leftover RDS or successful R process is insufficient.

HICAT predecessor clustering, whole-package behavior and biological decisions remain unqualified. PyArrow remains a separate missing interface with P250's development-only 14.0.2 declaration. BIGCAT retains its two-profile scope, original harmonization/default failures, explicit caller choices and live-object limitations. Mapping retains prepared-reference scope and private dependency restrictions. Arrow's invalidated C++ build50 evidence remains unqualified. DoubletFinder SCT convergence and reported 3.0 remain unresolved. Cell Ranger/ARC stay excluded by user request. All earlier verifiers, scientific programs, evidence and hashes remain unchanged.
'''
NOTICE='<section id="hicat109-required-actions" role="note"><h2>HICAT 1.0.9: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+'</section>'
STYLE='\n<style id="hicat109-warning-wrap">#hicat109-required-actions { overflow-wrap: anywhere; min-width: 0; }</style>\n'

def entry(root,old):
    if old['id']!=TOOL or old['implementation_status']!='missing' or old['papers']!=['P059']:
        raise ValueError('Exactly the existing single P059 HICAT family required')
    row=copy.deepcopy(old)
    row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,
      coverage_note='Selected original HICAT 1.0.9 modified-DoubletFinder scoring with private source-built RANN and independent Java strict-score selection; author settings and source failures retained.',
      implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,
      implementation_scope=NOTES['P059'],validation_note='26 original controls:14 complete returns/12 original failures; whole values, names, conditions and RNG compared, including10001real cells. Both public installs and actual installed analyses,15Linux public guards,Windows SKIP3,17697literal R comparisons and13824actual original scores through packaged Java on Windows25/Linux21,permanentJUnit passed.1337logical records/636unique payloads/36source identities;2unchanged copies accepted,6paired logical-inventory-resealed corruptions rejected. Fresh original replay is required. Whole-tool and paper reproduction unqualified;SKIP3 skips dependent analysis andFAIL2 admits no complete result.',
      project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
      development_candidate_available=True,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],
      development_candidate_kind='python-source-entrypoint-private-original-Linux-WSL-runtime-plus-independent-Java-predicate',
      development_build='tools/literature/scrattchhicat109/install_runtime.py',
      development_adapter_cli='python3 '+PROGRAM+' --runtime /private/completed-provider --request /trusted/request.rds --out /NEW/output',
      selected_upstream_versions=['1.0.9'],selected_source_profiles=['dev-zy-prepublication-2022'],
      selected_source_commits=['24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4'],selected_R_versions=['4.2.3'],
      selected_environment_is_author_environment=False,author_source_commits_established=False,author_exact_function_established=False,
      author_doublet_gene_panel_established=False,paper_62_housekeeping_QC_is_separate=True,source_roles_reviewed=1,
      workflow_validation='../scrattchhicat109-workflow-validation/QUALIFICATION.json',
      native_component_source='src/main/java/org/jlinalg/singlecell/DoubletScoreThreshold.java',
      native_component_scope='Independent strict comparison and original-row selection on supplied scores; no HICAT score calculation or source-derived native scoring port.',
      source_role_coverage='hicat109-role-coverage.json',current_documentation=GUIDE,
      paper_role_coverage={'P059':reviews(root)[0]['use_summary']+' '+NOTES['P059']},
      uncovered_roles=['Actual author source commit, exact function, caller inputs/options, gene panel and environment',
        'Historical predecessor clustering and other unselected HICAT functions','Original failures remain failures; no result admitted',
        'Whole-package validity, biological decisions and paper reproduction'],
      upstream_license_labels_resolved=False,upstream_source_or_runtime_redistributed=False,
      new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False)
    return row

def transition(root,before):
    if set(before)!=set(META):raise ValueError('Exactly eight complete predecessor metadata inputs required')
    inventory,summary=json.loads(before[META[0]]),json.loads(before[META[1]])
    papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(438,250,2679,1180):
        raise ValueError('Complete preceding populations differ')
    indices=[i for i,r in enumerate(inventory) if r['id']==TOOL]
    if len(indices)!=1:raise ValueError('One existing HICAT family required')
    inventory[indices[0]]=entry(root,inventory[indices[0]])
    expected=original_reviews(root)[0]
    old=[r for r in document['reviews'] if r['tool_id']==TOOL and r['paper_id']=='P059']
    if len(old)!=1 or old[0]!=expected:raise ValueError('Exact preceding HICAT primary review required')
    old[0]['implementation_scope_note']=NOTES['P059']
    observation=[r for p in papers if p['paper_id']=='P059' for r in p['tools'] if r['tool_id']==TOOL]
    if len(observation)!=1 or observation[0]['implementation_scope_note']!=expected['implementation_scope_note']:
        raise ValueError('Exact preceding HICAT observation required')
    observation[0]['implementation_scope_note']=NOTES['P059']
    updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory))
    wanted=copy.deepcopy(summary['implementation_status_counts']);wanted['missing']-=1;wanted['upstream-adapter-validated']+=1
    if updated['implementation_status_counts']!=wanted or (wanted['missing'],wanted['upstream-adapter-validated'],wanted['native-subset-validated'])!=(262,97,20):
        raise ValueError('Exactly one upstream HICAT credit and no native family credit required')
    page=before[META[4]].decode();warning=before[META[5]].decode()
    match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):
        raise ValueError('Exact preceding complete embedded catalog required')
    page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
    marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    historical='<details><summary>Previous BIGCAT checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
    if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]):raise ValueError('Exact preceding warning panels and heads required')
    if page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Expected warning insertion points required')
    page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
    warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
    intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [HICAT source roles, commands and recovery](../HICAT109_CATALOG_INTEGRATION.md). Current cumulative entry point: `python tools/literature/hicat109_catalog/catalog.py --verify --rescan-sources` with all documented permitted providers and NEW output paths. The full goal remains open.\n\n'
    paper_bytes=encoded(papers)
    if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_bytes=paper_bytes.replace(b'\n',b'\r\n')
    return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:paper_bytes,REVIEWS:encoded(document),
      META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
      META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
    if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Catalog differs from exact one-family/one-note HICAT transition')
    oldi,newi=json.loads(before[META[0]]),json.loads(after[META[0]])
    if [r for r in oldi if r['id']!=TOOL]!=[r for r in newi if r['id']!=TOOL]:raise ValueError('An unrelated family changed')
    for old,new in zip(json.loads(before[REVIEWS])['reviews'],json.loads(after[REVIEWS])['reviews']):
        expected=copy.deepcopy(old)
        if old['tool_id']==TOOL:expected['implementation_scope_note']=NOTES[old['paper_id']]
        if new!=expected:raise ValueError('Primary review facts or another review changed')
    for old,new in zip(json.loads(before[PAPERS]),json.loads(after[PAPERS])):
        expected=copy.deepcopy(old)
        if old['paper_id']=='P059':next(r for r in expected['tools'] if r['tool_id']==TOOL)['implementation_scope_note']=NOTES['P059']
        if new!=expected:raise ValueError('Primary paper facts or another observation changed')
    return dict(papers=250,named_tools=438,observations=2679,reviewed_uses=1180,unreviewed_uses=1499,
      new_scoped_integrations=117,missing=262,unchanged_previous_families=437,unchanged_previous_observations=2678,
      unchanged_previous_reviews=1179,enriched_existing_observations=1,revised_existing_reviews=1,
      added_observations=0,added_reviews=0,new_registry_definitions=0,new_scoped_integration_credit=1,
      native_family_credit_added=0,whole_goal_complete=False)
