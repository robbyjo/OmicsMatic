# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one original Python workflow, five existing roles and one newly named missing R wrapper."""
from collections import Counter
import copy,html,json,re
from cell2location_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin
from scvelo_catalog.source import EXPECTED,CURATION,INTAKE,DEFINITION,EXTRACTION,reviews
EVIDENCE='docs/literature/scvelo-catalog-integration'
WORKFLOW='docs/literature/scvelo024025-development'
GUIDE='docs/literature/SCVELO_CATALOG_INTEGRATION.md'
PROGRAM='tools/literature/scvelo024025/run.py'
PROOF=DATA+'/scvelo-catalog-validation.json'
COVERAGE=DATA+'/scvelo024025-role-coverage.json'
COMPATIBILITY=DATA+'/scvelo-reviews-shared-source-compatibility.json'
WARNING='''
## Original scVelo workflow: warnings and required actions

**Current catalog: 250 papers, 433 named software families, 2,672 observations and 1,168 individually reviewed uses; 1,504 uses await review.** The fixed71 Nature,104 Nature Genetics and75 AJHG papers remain unchanged. Five existing scVelo observations were individually reviewed. The separately named velociraptor R wrapper adds one missing family, one observation and one review; it has zero implementation credit. Seven family-specific anchors occupy six distinct primary paragraphs.

**111 scoped new integrations: 20 native Java subsets and 91 original upstream workflows; 114 including three existing adapters. 263 named families remain missing.** This adds one upstream scVelo family credit and zero native Java credit. The full semantic review and implementation goal remains open.

Use python3 tools/literature/scvelo024025/run.py --request caller-request.json with an exact matching original0.2.4 or0.2.5 Linux/WSL CPU provider and complete trusted Python script. This is a Python source entry point, not a standalone JAR or root dispatch route. Install only where permitted using install.py --version 0.2.4 or --version 0.2.5 with explicit --allow-install --allow-download and NEW paths. Analysis installs nothing. Native Windows original execution skips.

**P070 prints0.24; its dependent reported-edition analysis remains SKIP3.** The exact PyPI endpoint returnedHTTP404; do not relabel it0.2.4. **P228 uses the separate velociraptor R interface, which remains missing and unqualified here.** Its selected original1.20.0 source declares scvelo==0.3.3; direct Python0.2.x providers do not qualify that R preprocessing/conversion/environment path. Neither wrapper nor Python author edition/mode is inferred. Preserve the Velocyto name paired with a velociraptor URL as a source/link discrepancy. P051's exact count-threshold keyword is unreported; min_shared_counts=30 is an explicit caller choice in the larger control.

**Review original NaN loss-history padding and every delivered warning/error.** Complete synthetic original-versus-adapter checks cover54 stages,2,124 arrays,25,165,036 elements,2,272 result files and10 figures at tolerance zero. The stochastic/PAGA and dynamical/latent-time examples retain160 cells/12 genes; two larger stochastic controls retain96 cells and select2,000 HVGs from2,100 input genes with50 neighbors/30 PCs. These are integration controls, not author settings, convergence, independent statistical validity or paper reproduction. Original scVelo suppresses UserWarning during import; zero delivered warnings is not absence of numerical issues. Visible, ignored and promoted warning policies remain distinct.

**Four earlier SVGs were overwritten and cannot be recovered. Original temporary paths are not durable evidence.** At archival review168 original analysis temporary directories and one unit fixture were unavailable; all13 recorded regular temporary-file copies/268,435,479 bytes remain verified in saved analysis bundles. Use native-temporary copies and retained node identities, not historical /tmp paths. Historical original_root_retained flags record presence at controller retention time, not later availability. The original pack worker stopped during final read-only rechecking; separate full archive/source recovery is retained without rewriting its incomplete history.

**SKIP3 skips dependent analysis** for missing/forbidden exact prerequisites, installation/download or execution. Supply only permitted requirements and retry NEW paths. **FAIL2 admits no complete result.** Retain all available original objects, NaN/Inf, raw worker reports, complete process.stdout/process.stderr, diagnostics/conditions.json, WARNINGS.md, analysis-status.json, MANIFEST.json, native-temporary-inventory.json and every SUPERVISOR_ERROR.txt, WORKER_DIAGNOSTIC_ERROR.txt or NATIVE_TEMPORARY_ERROR.txt. Unknown totals are unavailable, never zero: condition_counts_complete false and warning_review_required true. If recording is blocked, retain the complete console and resolve the named filesystem requirement. Never drop failed cases or change saved hashes. Trusted scripts run with account permissions; owned process-group and capacity supervision is not an OS sandbox or total CPU/RSS enclosure.

Other restrictions remain explicit: Cell Ranger and Cell Ranger ARC are excluded by user request; cell2location's reported0.7a0 remains unqualified by0.1.3; rstatix retains revision2 recovery, ggpubr retains failed/empty layer eligibility limits, and other existing tool restrictions are unchanged.
'''
def paragraph(value):return re.sub(r'\*\*(.*?)\*\*',r'<strong>\1</strong>',html.escape(value.strip()))
NOTICE='<section id="scvelo-required-actions" role="note"><h2>Original scVelo workflow: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+'</section>'

def entry(root,old):
 if old['id']!='scvelo' or old['implementation_status']!='missing' or old['papers']!=list(EXPECTED):raise ValueError('Unexpected predecessor scVelo family')
 row=copy.deepcopy(old);row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note='Original0.2.4/0.2.5 CPU trusted-script workflow; complete retained scientific state and exact-edition providers. Printed0.24 and the R velociraptor interface remain separately unqualified.',implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,
  implementation_scope='Explicit Python source entry point with unchanged original0.2.4/0.2.5 Linux/WSL CPU packages and complete namespace/source/runtime captures. Original preprocessing, moments, stochastic/PAGA, dynamical recovery, graph, pseudotime/latent time, projection and plots remain original. Full populations, literal identifiers, category order, NaN/Inf, models, conditions and partial outputs are retained. Printed0.24 and the separately missing R velociraptor interface remain SKIP3 dependent analyses. Unknown author editions/options are not inferred; not a standalone JAR or paper reproduction.',
  validation_note='54 independent stages,2,124 arrays/25,165,036 elements,2,272 result files and10 figures; typed graphs and all NPY bytes match direct original execution at tolerance zero.100 Linux workflow controls,1 Windows refusal,7 monitor controls,10 setup refusals,10 native temporary controls,12 narrow mapping cases,8 portable-recorder groups,11 scientific-comparator refusals and6 archive-reader refusals passed. Both public installers and four complete fresh-provider examples were exercised. Complete source members106/402 and original package files63/57 retained; each provider has45 installed/import-visible distributions.30,983 logical files,4 links and3,205 directories retained in nine bounded ZIPs. Four overwritten historical SVGs remain irrecoverable.168 original analysis /tmp roots are unavailable; all13 recorded regular copies/268,435,479 bytes remain verified. Loss-padding NaNs and original UserWarning filters remain unchanged. Stopped packer history and independent complete recovery remain retained; no opaque pickle/HDF5/SVG byte parity or convergence claimed.',
  source_roles_reviewed=5,new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],development_candidate_kind='python-source-entrypoint-requires-exact-original-Linux-CPU-provider',development_build='tools/literature/scvelo024025/install.py',development_adapter_cli='python3 '+PROGRAM+' --request caller-request.json',selected_upstream_versions=['0.2.4','0.2.5'],selected_python_version='3.9.25',reported_0_24_execution_qualified=False,velociraptor_interface_qualified=False,workflow_validation='../scvelo024025-development/VALIDATION.json',source_role_coverage='scvelo024025-role-coverage.json',
  paper_role_coverage={r['paper_id']:r['use_summary']+' Reported settings: '+json.dumps(r['reported_settings'],ensure_ascii=False)+'. '+r['implementation_scope_note'] for r in reviews(root) if r['tool_id']=='scvelo'},
  uncovered_roles=['Reported0.24 exact runtime remains unresolved; do not relabel0.2.4','Separate velociraptor R preprocessing, conversion and declared0.3.3 Python environment','Unknown author editions, environments, input layers, defaults, threshold keyword and exact scientific settings','Independent statistical/biological validity, actual convergence, untested operations and complete paper reproduction'])
 return row

def wrapper(root):
 row=json.loads((root/DEFINITION).read_bytes());row.update(paper_count=1,papers=['P228'],implementation_status='missing',implementation_source=None,implementation_scope='Separately named R wrapper; original R preprocessing, SingleCellExperiment/AnnData conversion and declared0.3.3 Python environment remain unqualified. Direct Python0.2.x providers are not an implementation of this interface. Skip dependent analysis until exact permitted prerequisites and original workflow are qualified.',validation_note='One explicit primary methods anchor reviewed across all250 XMLs. Private R dependency setup and source intake are not velocity analysis; original Git-source configure failure is retained. Author wrapper/Python editions, mode and calls remain unknown.',new_scoped_integration_credit=0,native_family_credit_added=0,project_adapter_available=False,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=False,source_roles_reviewed=1,paper_role_coverage={'P228':reviews(root)[-1]['use_summary']+' '+reviews(root)[-1]['implementation_scope_note']},whole_tool_qualified=False,whole_paper_reproduced=False)
 return row

def reviewed_observation(old,review,paper):
 row=copy.deepcopy(old);row['source_extraction_before_review']=copy.deepcopy(old);anchor=next(a for a in review['supporting_evidence'] if a['paragraph_index']==review['evidence_paragraph_index'])
 row.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_selection='individually-reviewed-explicit-primary-methods-anchor',review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+paper['pmcid']+'/ (section: '+anchor['section']+')',**{k:copy.deepcopy(review[k]) for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence','named_anchor_classification','reported_settings','uncertainties']})
 if 'related_underlying_scvelo_results' in review:row['related_underlying_scvelo_results']=copy.deepcopy(review['related_underlying_scvelo_results'])
 return row

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Exactly eight predecessor catalog metadata paths required')
 inventory=json.loads(before[META[0]]);papers=json.loads(before[PAPERS]);document=json.loads(before[REVIEWS]);summary=json.loads(before[META[1]])
 if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(432,250,2671,1162):raise ValueError('Unexpected exact predecessor populations')
 i=next(i for i,t in enumerate(inventory) if t['id']=='scvelo');inventory[i]=entry(root,inventory[i])
 if any(t['id']=='velociraptor' for t in inventory):raise ValueError('Wrapper already registered')
 inventory.append(wrapper(root));known={(r['pmcid'],r['tool_id']) for r in document['reviews']}
 if len(known)!=1162:raise ValueError('Duplicate previous source review')
 for review in reviews(root):
  paper=next(p for p in papers if p['paper_id']==review['paper_id']);indices=[i for i,t in enumerate(paper['tools']) if t['tool_id']==review['tool_id']]
  if (paper['pmcid'],review['tool_id']) in known:raise ValueError('An admitted source role would be duplicated')
  if review['tool_id']=='velociraptor':
   if indices:raise ValueError('Wrapper observation already exists')
   paper['tools'].append(reviewed_observation(json.loads((root/EXTRACTION).read_bytes()),review,paper))
  else:
   if len(indices)!=1:raise ValueError('Missing/duplicate existing scVelo observation')
   paper['tools'][indices[0]]=reviewed_observation(paper['tools'][indices[0]],review,paper)
  added=copy.deepcopy(review);added.pop('paper_id');document['reviews'].append(added)
 updated=copy.deepcopy(summary);updated.update(paper_tool_observations=2672,distinct_named_tools=433,implementation_status_counts=dict(Counter(t['implementation_status'] for t in inventory)))
 counts=dict(summary['implementation_status_counts']);counts['upstream-adapter-validated']+=1
 if updated['implementation_status_counts']!=counts or (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])!=(20,91,263):raise ValueError('Unexpected implementation credit')
 page=before[META[4]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Predecessor embedded catalog data differs')
 page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
 historical='<details><summary>Previous cell2location catalog checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 if page.count(PRIOR_NOTICE)!=1 or page.count('<p id="overview"></p>')!=1:raise ValueError('Current predecessor notice/overview differs')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warning=before[META[5]].decode();marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warning.count(PRIOR_NOTICE)!=1 or warning.count(marker)!=1:raise ValueError('Current predecessor warnings differ')
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1);raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [scVelo commands, current source roles and recovery](../SCVELO_CATALOG_INTEGRATION.md). Validate with `python tools/literature/scvelo_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-scvelo-catalog-check --rescan-sources`. The full semantic review and implementation objective remain open.\n\n'
 return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:raw,REVIEWS:encoded(document),META[2]:(intro+'<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),META[3]:(WARNING+'\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact one-workflow/five-existing-roles/one-missing-wrapper admission')
 return dict(papers=250,named_tools=433,observations=2672,reviewed_uses=1168,unreviewed_uses=1504,new_scoped_integrations=111,missing=263,unchanged_previous_families=431,unchanged_previous_observations=2666,unchanged_previous_reviews=1162,enriched_existing_observations=5,added_observations=1,added_reviews=6,new_scoped_integration_credit=1,native_family_credit_added=0,new_registry_definitions=1,new_missing_wrapper_implementation_credit=0,whole_goal_complete=False)
