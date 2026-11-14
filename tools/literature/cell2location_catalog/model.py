# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one exact original CPU workflow and six readings, preserving every other source record."""
from collections import Counter
import copy,html,json,re
from rstatix_revision2_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin
from cell2location_catalog.source import EXPECTED,CURATION,INTAKE,reviews
EVIDENCE='docs/literature/cell2location-catalog-integration'
WORKFLOW='docs/literature/cell2location013-development'
GUIDE='docs/literature/CELL2LOCATION_CATALOG_INTEGRATION.md'
PROOF=DATA+'/cell2location-catalog-validation.json'
COVERAGE=DATA+'/cell2location013-role-coverage.json'
PROGRAM='tools/literature/cell2location013/run.py'
COMMON='Use the exact original 0.1.3 Linux/WSL CPU source entry point with a qualified private provider and trusted complete-population Python script; not a standalone JAR. Install/download only where permitted. SKIP3 skips dependent analysis for missing/forbidden prerequisites; FAIL2 admits no complete result. Retain every original object, condition, raw malformed report and partial file; unknown condition totals are unavailable, not zero. Correct the named cause and retry NEW paths. Synthetic epochs/parameters establish integration only, not historical author defaults, convergence or paper reproduction.'
WARNING='''
## Original cell2location workflow: warnings and required actions

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,162 individually reviewed uses; 1,509 uses await review.** The fixed cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23. Six existing cell2location observations were enriched from all nine named primary paragraphs; no paper or observation was added.

**110 scoped new integrations: 20 native Java subsets and 90 original upstream workflows; 113 including 3 existing adapters. 263 named families remain missing.** This adds one upstream cell2location 0.1.3 family credit and zero native Java credit. The full semantic review and implementation goal remains open.

Use python3 tools/literature/cell2location013/run.py --request caller-request.json. This is an explicit Python source entry point with a separate qualified private Linux/WSL CPU Python 3.9.25/Torch 2.0.1+cpu/scvi-tools 0.20.3/Pyro 1.8.6 environment, not a standalone JAR or root dispatch route. Download/install only where permitted using the separate install.py with explicit --allow-install --allow-download and NEW paths. Setup creates no global Python executable links. Analysis installs nothing. Native Windows original execution skips.

**P096 reports 0.7a0, which is not qualified by 0.1.3; dependent historical-edition analysis remains SKIP3.** Preserve the paper's raw-reference wording and the author's log_normalised filename uncertainty; a filename does not prove matrix transformation. Preserve the reported NMF range 5 to 30 and the author code np.arange(5,30), which generates 5 through 29. Other unreported author editions/settings remain unknown. No edition, covariate argument, aggregation operator, input layer or mapping-score field is silently substituted.

**Review every warning/error before using results.** The complete synthetic example retains 17 original delivered warnings and 5 warning/error log records, including undefined NMF diagnostic divisions and explicit CPU/JAX and headless plotting messages. All 647 retained scientific arrays/65,212 elements and 17 independently recorded stages matched direct upstream execution at tolerance zero; 684 result files, all NMF models and both factor settings remain retained. This is not convergence or biological validation. The older shared-object graph limitation and failed recorder/setup/verifier attempts remain preserved with their original scope.

**SKIP3 skips dependent analysis** for missing/forbidden runtime, provider, input, original symbol/dependency, installation/download or execution. Supply only permitted exact prerequisites and retry NEW paths. **FAIL2 admits no complete result.** Retain all original objects, full process.stdout/process.stderr, diagnostics/conditions.json, raw worker-status.json, SUPERVISOR_ERROR.txt, WORKER_DIAGNOSTIC_ERROR.txt, WARNINGS.md, analysis-status.json and MANIFEST.json. Unknown totals are unavailable, not zero, with condition_counts_complete false and warning_review_required true. If the OS prevents recording, retain the complete console and resolve the named filesystem requirement. Never drop failed cases or relabel a partial population complete. Trusted scripts run with account permissions; owned worker supervision is not an OS sandbox or total CPU/RSS enclosure.

Other restrictions remain: Cell Ranger and Cell Ranger ARC are excluded by user request; rstatix uses the qualified revision2 recovery supervisor; ggpubr retains failed/empty statistical-layer eligibility limits; QuPath retains edition/image/model limits; reported AllelicSeries1.1.2 remains unresolved; ImageJ distributions/plugins and unspecified palette assignments stay separate. This admission changes none of those families or their source roles.
'''
NOTICE='<section id="cell2location-required-actions" role="note"><h2>Original cell2location workflow: warnings and required actions</h2>'+''.join('<p>'+html.escape(p.strip())+'</p>' for p in WARNING.strip().split('\n\n')[1:])+'</section>'

def entry(root,old):
 if old['id']!='cell2location' or old['implementation_status']!='missing' or old['papers']!=list(EXPECTED) or old['paper_count']!=6:raise ValueError('Unexpected predecessor cell2location family')
 result=copy.deepcopy(old)
 result.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note='Original 0.1.3 CPU trusted-script workflow;17 independent stage snapshots, complete scientific values and all diagnostics retained. Reported0.7a0 and unknown author editions/settings remain distinct.',implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,
  implementation_scope='Explicit Python source entry point, not a standalone JAR: complete installed original cell2location 0.1.3 namespace with a separate exact Linux/WSL CPU provider. Original negative-binomial reference regression, spatial mapping, posterior export, official save/load and NMF wrapper remain unchanged. Caller scripts retain exact model options, input provenance and full populations; no generic statistical replacement. P096 reports0.7a0, which remains unqualified by0.1.3: SKIP3 skips its dependent historical-edition analysis. Unreported author editions/settings remain unknown. '+COMMON,
  validation_note='All 17 independent stages/4,658 graph records,647 arrays and65,212 elements matched direct original CPU execution at tolerance zero. All684 result files, complete module/Pyro states, posterior summaries, histories, AnnData, four NMF models, both factors/restarts and caller RNG states retained.41 Linux workflow controls,1 Windows refusal,7 monitor controls,7 Linux/3 Windows setup refusals,10 comparator refusals and5 frozen-verifier refusals passed. Exact55-member source archive,44 installed package files and249 declarations checked.103 installed distributions and119 import-visible records/origins are distinct.30,129 logical files,2 links and1,299 directories retained losslessly. Original warnings/logs and old snapshot/console/metadata/setup/verifier failures retained. No opaque pickle/PDF byte parity, convergence or paper reproduction claimed.',
  source_roles_reviewed=6,new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,paper_exact_unknown_settings_available=False,project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],development_candidate_kind='python-source-entrypoint-requires-qualified-original-Linux-CPU-provider',development_build='tools/literature/cell2location013/install.py',development_adapter_cli='python3 '+PROGRAM+' --request caller-request.json',selected_upstream_versions=['0.1.3'],selected_python_version='3.9.25',selected_dependency_versions={'torch':'2.0.1+cpu','scvi-tools':'0.20.3','pyro-ppl':'1.8.6'},reported_0_7a0_execution_qualified=False,workflow_validation='../cell2location013-development/VALIDATION.json',source_role_coverage='cell2location013-role-coverage.json',
  paper_role_coverage={r['paper_id']:r['use_summary']+' Reported settings: '+json.dumps(r['reported_settings'],ensure_ascii=False)+'. '+r['implementation_scope_note']+' '+COMMON for r in reviews(root)},
  uncovered_roles=['Reported0.7a0 /historical0.07a commits are not qualified by the selected0.1.3 profile; dependent historical-edition analysis skipped','Other or unreported author editions, historical environments and GPU execution','Unknown count layers, raw-versus-transformed author matrices, preprocessing/aggregation operators and exact scoring fields','Unknown covariate argument/stage interpretation, priors, seed, fitting defaults and author-specific scientific eligibility','Author segmentation/alignment, biological conclusions, actual convergence and complete paper reproduction'])
 return result

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Exactly eight predecessor catalog metadata paths required')
 inventory=json.loads(before[META[0]]);papers=json.loads(before[PAPERS]);document=json.loads(before[REVIEWS]);summary=json.loads(before[META[1]])
 if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(432,250,2671,1156):raise ValueError('Unexpected exact predecessor populations')
 index=next(i for i,t in enumerate(inventory) if t['id']=='cell2location');inventory[index]=entry(root,inventory[index]);known={(r['pmcid'],r['tool_id']) for r in document['reviews']}
 if len(known)!=1156:raise ValueError('Duplicate existing individual source review')
 if {p['paper_id'] for p in papers for t in p['tools'] if t['tool_id']=='cell2location'}!=set(EXPECTED):raise ValueError('Complete existing paper-family population differs')
 for review in reviews(root):
  paper=next(p for p in papers if p['paper_id']==review['paper_id']);indices=[i for i,t in enumerate(paper['tools']) if t['tool_id']=='cell2location']
  if len(indices)!=1 or (paper['pmcid'],'cell2location') in known:raise ValueError('Duplicate/missing/already-reviewed cell2location observation')
  old=paper['tools'][indices[0]];row=copy.deepcopy(old);row['source_extraction_before_review']=copy.deepcopy(old);anchor=next(a for a in review['supporting_evidence'] if a['paragraph_index']==review['evidence_paragraph_index'])
  row.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_selection='individually-reviewed-explicit-primary-methods-anchor',review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+paper['pmcid']+'/ (section: '+anchor['section']+')',**{k:copy.deepcopy(review[k]) for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence','named_anchor_classification','reported_settings','uncertainties']})
  paper['tools'][indices[0]]=row;appended=copy.deepcopy(review);appended.pop('paper_id');document['reviews'].append(appended)
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(t['implementation_status'] for t in inventory));counts=dict(summary['implementation_status_counts']);counts['missing']-=1;counts['upstream-adapter-validated']+=1
 if counts!=updated['implementation_status_counts'] or (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])!=(20,90,263):raise ValueError('Unexpected integration credit')
 page=before[META[4]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Predecessor rendered data differs')
 page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
 historical='<details><summary>Previous rstatix revision2 catalog checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 if page.count(PRIOR_NOTICE)!=1 or page.count('<p id="overview"></p>')!=1:raise ValueError('Current predecessor notice/overview differs')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warning=before[META[5]].decode();marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warning.count(PRIOR_NOTICE)!=1 or warning.count(marker)!=1:raise ValueError('Current predecessor warnings differ')
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1);raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [cell2location commands, source roles and recovery](../CELL2LOCATION_CATALOG_INTEGRATION.md). Validate with `python tools/literature/cell2location_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-cell2location-catalog-check --rescan-sources`. The full semantic review and implementation objective remain open.\n\n'
 return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:raw,REVIEWS:encoded(document),META[2]:(intro+'<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),META[3]:(WARNING+'\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact one-upstream/six-review cell2location admission')
 return dict(papers=250,named_tools=432,observations=2671,reviewed_uses=1162,unreviewed_uses=1509,new_scoped_integrations=110,missing=263,unchanged_previous_families=431,unchanged_previous_observations=2665,unchanged_previous_reviews=1156,enriched_existing_observations=6,added_observations=0,added_reviews=6,new_scoped_integration_credit=1,native_family_credit_added=0,whole_goal_complete=False)
