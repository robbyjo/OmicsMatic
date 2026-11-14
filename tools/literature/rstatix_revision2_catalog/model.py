# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Route the existing rstatix family to revision2; preserve every scientific/source review and all credits."""
from collections import Counter
import copy,html,json,re
from ggpubr_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin
from rstatix_catalog.source import EXPECTED,CURATION,INTAKE,reviews,scan,validate

EVIDENCE='docs/literature/rstatix-revision2-catalog-integration'
GUIDE='docs/literature/RSTATIX_REVISION2_CATALOG.md'
PROOF=DATA+'/rstatix-revision2-catalog-validation.json'
COVERAGE=DATA+'/rstatix072-revision2-role-coverage.json'
WORKFLOW='docs/literature/rstatix072-workflow-revision2'
PROGRAM='tools/literature/rstatix072_workflow_v2/run.py'
OLD_PROGRAM='tools/literature/rstatix072_workflow/run.py'
RECOVERY='Use supervisor revision2: python3 tools/literature/rstatix072_workflow_v2/run.py --request caller-request.json. It requires the same permitted private Linux/WSL R4.5.3/rstatix0.7.2 provider and trusted complete-population script; it is not a standalone JAR. Incomplete condition totals are unavailable, not zero, with warning_review_required true. Preserve raw worker reports, original conditions, controller tracebacks, complete/partial outputs and inventories; resolve the named cause and retry NEW paths. Read RSTATIX_REVISION2_CATALOG.md and RSTATIX072_WORKFLOW_REVISION2.md. This supervisor revision adds zero integration or review credit and resolves no missing author settings.'
WARNING='''
## Current rstatix revision2 routing: warnings and required actions

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,156 individually reviewed uses; 1,515 uses await individual review.** This update changes the existing rstatix workflow route and recovery instructions only. All 250 paper identities, all 2,671 observations, all 1,156 reviews and all six rstatix source readings remain unchanged. The fixed cohort is 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23. Full semantic review and implementation remain open.

**109 scoped new integrations remain available: 20 native Java subsets and 89 upstream workflows; 112 including 3 existing adapters. 264 named families remain missing.** Supervisor revision2 adds zero family, native-method or review credit. Use python3 tools/literature/rstatix072_workflow_v2/run.py with the same exact request schema and a NEW output path. It uses the unchanged original rstatix0.7.2 worker and a permitted private Linux/WSL R4.5.3 provider; it is not a standalone JAR. Native Java one-way Levene remains separate. Canonical executable and root dispatch remain unchanged. Read RSTATIX_REVISION2_CATALOG.md and RSTATIX072_WORKFLOW_REVISION2.md for commands and recovery.

**Interrupted-worker diagnostics require review.** The historical supervisor correctly refused a truncated worker report but could omit WARNINGS.md and MANIFEST.json. Revision2 retains the raw malformed report, partial results, warning/inventory records and WORKER_DIAGNOSTIC_ERROR.txt. Incomplete warning, message and error counts are unavailable, not zero: JSON null, condition_counts_complete false and warning_review_required true. SUPERVISOR_ERROR.txt retains controller tracebacks. No raw report is repaired. Inspect every original condition and full process.stdout/process.stderr, correct the named cause and retry NEW paths.

**Filesystem and monitoring failures admit no complete result.** A disappearing temporary child directory no longer aborts monitoring; missing owned roots, persistent capacity violations and forbidden links still fail. If the OS or a blocked path prevents a warning/status/inventory file, the controller attempts other records independently and prints the named recording failure and required action to its complete console log. Preserve that log and every available partial file; resolve the filesystem prerequisite and retry NEW paths. Unwritable diagnostics cannot be guaranteed. Never treat an incomplete inventory as complete.

**SKIP3 skips dependent analysis** when matching source, runtime, provider, input, literal export/package, permitted installation/download or execution is unavailable/forbidden. Install only where permitted, supply exact prerequisites and retry NEW paths. Windows original execution skips; use native Linux/WSL Python/R. Analysis installs nothing; a READY provider is not an analysis result. Explicit ALLOW execution/trusted-code policies and trusted caller code are required. Account permissions are not an OS sandbox or total CPU/RSS guarantee; same-group supervision excludes child-created separate sessions. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Preserve complete/partial original objects, input identities, warnings/errors, raw malformed diagnostics, logs, inventories and status. Correct the named cause and retry NEW paths. All original warnings remain visible; warn=2 still fails. Early zero exit, missing/extra/truncated results, changed inputs, forbidden links, malformed counts and capacity violations refuse admission. Do not drop cases, repair original algorithms or label partial populations complete. Files/markers and saved hashes do not prove arbitrary-code scientific validity, authenticity, author-runtime equivalence or biological truth.

**Paper roles and editions stay literal.** All six existing rstatix readings and 12 named primary paragraphs are preserved. Five papers report0.7.2; P241 reports no edition or exact test. P054 literally prints wilcoxon_test, while selected0.7.2 exports wilcox_test: the missing literal export skips analysis, with no spelling repair. Author comparisons, pairing, variance assumptions, direction and correction families remain inputs; P032's test/correction method remains unknown. Original p-value rounding, full RDS objects, formulas, grouping, conditions and RNG behavior remain original R behavior. Selected R4.5.3/car3.1-5 differs from historical author profiles. No paper is reproduced.

**Qualification retains the full population and every failed attempt.** All248 original calls,250 declared result files,444 rows and5,664 table cells match direct original R and the predecessor at tolerance zero. All31 earlier controls plus8 malformed-report and2 blocked-recording cases pass:41 Linux workflow controls, one Windows refusal, seven Windows/Linux monitor controls, the complete seven-operation public example and four frozen-verifier refusals. The main evidence has5,185 members, both link identities, blocked empty-directory identities and all failed attempts; an auxiliary control archive is retained. Original worker/statistical sources and earlier scientific verifiers remain unchanged. This catalog gate executes no new original analysis or installation.

**Other retained restrictions still apply.** ggpubr original Computation failed in warnings refuse admission despite rendered files/zero exit; silent empty layers require caller eligibility checks. ggpubr uses separate R4.3.3 providers for0.4.0/0.6.0, not rstatix's R4.5.3 profile. QuPath0.5.1 H-DAB controls do not qualify other editions, fluorescence/proximity, RNAscope, registration or custom StarDist models. P204's reported AllelicSeries1.1.2 remains missing; candidate0.1.1.2 earns no reported-edition credit. Unknown wesanderson palettes/counts/MFOD1 mapping and separate ImageJ distributions/plugins remain unresolved. Read the retained guides for all earlier limits.
'''

def notice():
 paragraphs=['<p>'+re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',html.escape(p),flags=re.S)+'</p>' for p in WARNING.strip().split('\n\n')[1:]]
 return '<section id="rstatix-revision2-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Current toolbox coverage: rstatix revision2 and required actions</h2>'+''.join(paragraphs)+'</section>'
NOTICE=notice()

def entry(root,old):
 if old['id']!='rstatix' or old['implementation_status']!='upstream-adapter-validated' or old['development_candidate']!=OLD_PROGRAM or old['paper_count']!=6 or old['papers']!=list(EXPECTED):raise ValueError('Unexpected historical rstatix route')
 row=copy.deepcopy(old)
 row.update(existing_source=PROGRAM,implementation_source=PROGRAM,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],
  development_adapter_cli='python3 '+PROGRAM+' --request caller-request.json',
  coverage_note='Supervisor revision2 for unchanged original rstatix0.7.2: retained warning/inventory recovery after incomplete reports, unavailable condition totals and independent recording attempts. Same private Linux/WSL R4.5.3 provider; native one-way Levene and unknown author settings remain separate. Zero additional integration/review credit.',
  implementation_scope=old['implementation_scope'].replace('Read RSTATIX_CATALOG_INTEGRATION.md.','Read RSTATIX_REVISION2_CATALOG.md and RSTATIX072_WORKFLOW_REVISION2.md.')+' '+RECOVERY,
  validation_note='Retained earlier qualification: '+old['validation_note']+' Current supervisor revision2: all248 original calls and250 result files match both direct original R and predecessor,444 rows/5,664 cells;41 Linux controls, one Windows refusal, seven monitor controls, one complete seven-operation example and four frozen-verifier refusals. All5,185 base evidence members, both link identities, blocked empty directories, controller tracebacks and every failed attempt are retained. No new statistical method, source-role reading or paper reproduction is claimed.',
  workflow_validation='../rstatix072-workflow-revision2/VALIDATION.json',historical_workflow_validation=old['workflow_validation'],source_role_coverage='rstatix072-revision2-role-coverage.json',
  supervisor_revision=2,supervisor_revision_integration_credit=0,supervisor_revision_review_credit=0,
  historical_development_candidate=old['development_candidate'],historical_development_candidate_sha256=old['development_candidate_sha256'],
  paper_role_coverage={n:text+' '+RECOVERY for n,text in old['paper_role_coverage'].items()})
 return row

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Exactly eight predecessor catalog metadata inputs required')
 inventory=json.loads(before[META[0]]);papers=json.loads(before[PAPERS]);summary=json.loads(before[META[1]]);document=json.loads(before[REVIEWS])
 if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(432,250,2671,1156):raise ValueError('Unexpected current population')
 if (summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])!=(20,89,264):raise ValueError('Unexpected current integration counts')
 i=next(i for i,r in enumerate(inventory) if r['id']=='rstatix');inventory[i]=entry(root,inventory[i])
 if dict(Counter(r['implementation_status'] for r in inventory))!=summary['implementation_status_counts']:raise ValueError('Routing must not change any integration credit')
 page=before[META[4]].decode();m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or json.loads(m[2])!=dict(papers=papers,tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Predecessor rendered data differs')
 page=page[:m.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c')+page[m.end(2):]
 historical='<details><summary>Previous ggpubr catalog checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 if page.count(PRIOR_NOTICE)!=1 or page.count('<p id="overview"></p>')!=1:raise ValueError('Predecessor current notice differs')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warning=before[META[5]].decode();marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warning.count(PRIOR_NOTICE)!=1 or warning.count(marker)!=1:raise ValueError('Predecessor warnings differ')
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1)
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [current rstatix commands, unchanged source roles and recovery](../RSTATIX_REVISION2_CATALOG.md). Validate with `python tools/literature/rstatix_revision2_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-rstatix-revision2-catalog-check --rescan-sources`. Full semantic review and implementation remain open.\n\n'
 after=dict(before)
 after.update({META[0]:encoded(inventory),META[2]:(intro+'<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),META[3]:(WARNING+'\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()})
 return after

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact zero-credit rstatix revision2 routing')
 return dict(papers=250,named_tools=432,observations=2671,reviewed_uses=1156,unreviewed_uses=1515,new_scoped_integrations=109,missing=264,unchanged_other_families=431,unchanged_observations=2671,unchanged_reviews=1156,added_observations=0,added_reviews=0,new_scoped_integration_credit=0,native_family_credit_added=0,whole_goal_complete=False)
