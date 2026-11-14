# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one scoped original R workflow; preserve every other paper, use and review."""
from collections import Counter
import copy,html,json,re
from scvelo_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin
from velociraptor_catalog.source import CURATION,review
EVIDENCE='docs/literature/velociraptor-catalog-integration'
WORKFLOW='docs/literature/velociraptor120-workflow-development'
GUIDE='docs/literature/VELOCIRAPTOR120_CATALOG_INTEGRATION.md'
PROGRAM='tools/literature/velociraptor120/run.py'
PROOF=DATA+'/velociraptor-catalog-validation.json'
COVERAGE=DATA+'/velociraptor120-role-coverage.json'
COMPATIBILITY=DATA+'/velociraptor-reviews-shared-source-compatibility.json'
WARNING='''
## Original velociraptor R workflow: warnings and required actions

**Current catalog: 250 papers, 433 named software families, 2,672 observations and 1,168 individually reviewed uses; 1,504 uses await review.** The fixed71 Nature,104 Nature Genetics and75 AJHG papers remain unchanged. The existing P228 velociraptor reading retains its primary anchors, role, unknown author settings and uncertainties. Only implementation coverage changes; no paper, observation or review is added.

**112 scoped new integrations: 20 native Java subsets and 92 original upstream workflows; 115 including three existing adapters. 262 named families remain missing.** This adds one upstream velociraptor family credit and zero additional native Java credit. The full semantic review and implementation objective remains open.

Use python3 tools/literature/velociraptor120/run.py --request caller-request.json with an exact permitted original1.20.0 Linux/WSL provider and complete trusted R script. Original R preprocessing, all7 S4 signatures, conversion and declared scvelo0.3.3 backend remain original. Separate figures.py executes original R-only figure functions over supplied complete fits/projections without providing or initializing a Python fitting backend. These are source entry points, not bundled Java CLI commands. Install/download only where permitted with explicit flags and NEW paths; analysis installs nothing. Native Windows analysis and installation skip.

**Original R native stack diagnostics remain unresolved.** Review every raw console and original condition. R handler counts are not universal raw/native/Python diagnostic totals; unknown totals remain unavailable, never zero.13 complete numeric comparisons cover3,732 nodes/2,617 atomic payloads/2,457,351 bytes without equality waivers.4 complete SVG/PNG pairs and8 serialized-graph replays matched original bytes;2 original named-coordinate errors remain failed analyses.36 controls and6 complete public examples passed. These establish selected integration paths, not the entire package, biological validity, convergence or paper reproduction. Graph environment/pointer identity and opaque RDS byte identity are not claimed.

**P228 does not report wrapper/Python editions, velocity mode or exact calls.** The selected R4.5.3/Python3.12.10/NumPy2.2.6/Pandas2.2.3/SciPy1.16.3 profile is a project choice, not recovered author settings. Preserve the Velocyto name beside a velociraptor URL as a source/link discrepancy. The native Java grid component computes neither RNA velocity nor latent time and adds no second family credit. Original named-coordinate DFrame failures are not repaired by renaming axes; separate positive streamline controls use unchanged original PCA coordinates and matching vectors.

**SKIP3 skips dependent analysis** for missing/forbidden prerequisites, installation, download or execution. **FAIL2 admits no complete result**, including final recording failures. Do not infer success from a leftover RDS or figure. Read exit code, analysis-status.json, WARNINGS.md and MANIFEST.json. If status cannot be saved, the console ends with FAIL2 and FINALIZATION_ERROR.json records failure where writable. If all records are blocked, preserve the full console, resolve the named filesystem cause and retry a NEW writable directory. Never overwrite historical evidence, drop failed cases, repair identifiers/NA/NaN/Inf or change saved hashes. Retain full RDS/value trees, graphs, conditions/calls, raw logs, available caller state and native temporary copies; live IPC streams and terminated process memory are not reconstructed. Trusted scripts run with account permissions; this is not an OS sandbox or total CPU/RSS enclosure.

Other restrictions remain unchanged: Cell Ranger/ARC are excluded by user request; printed scVelo0.24 remains unresolved; four historical direct-Python SVGs remain irrecoverable and the wrapper does not recover them. Earlier dependency/profile failures, within-original SciPy1.18.1 pseudotime variability, the failed first example comparator and earlier source revisions remain retained. The first frozen dossier remains in place; a separate current dossier captures stronger complete-example replay without refreshing historical hashes.
'''
NOTICE='<section id="velociraptor-required-actions" role="note"><h2>Original velociraptor R workflow: warnings and required actions</h2>'+''.join('<p>'+html.escape(p.strip()).replace('**','')+'</p>' for p in WARNING.strip().split('\n\n')[1:])+'</section>'

def entry(root,old):
 if old['id']!='velociraptor' or old['implementation_status']!='missing' or old['papers']!=['P228']:raise ValueError('Expected separately missing R interface')
 reading=review(root);row=copy.deepcopy(old)
 row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note='Selected original1.20.0 R workflow with original preprocessing/conversion/managed0.3.3 backend and separate R-only figures; unknown author editions/options remain unknown.',implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,implementation_scope=reading['implementation_scope_note'],validation_note='13 complete numeric cases across all7 S4 signatures;3,732 nodes/2,617 atomic payloads/2,457,351 bytes and strict R-value/argument equality.4 complete figure pairs/8 graph replays;2 matching original errors.32 Linux/R controls,4 Windows skips,6 complete public examples. Original46-file R/63-file Python sources and runtime inventories retained. Original native diagnostics remain unresolved; no whole-package/convergence/biological/paper claim.',new_scoped_integration_credit=1,native_family_credit_added=0,project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],development_candidate_kind='python-source-entrypoint-requires-exact-original-Linux-WSL-R-Python-provider',development_build='tools/literature/velociraptor120/install_runtime.py',development_adapter_cli='python3 '+PROGRAM+' --request caller-request.json',selected_upstream_versions=['1.20.0'],selected_Python_backend_version='0.3.3',selected_R_version='4.5.3',selected_python_version='3.12.10',selected_environment_is_author_environment=False,workflow_validation='../velociraptor120-workflow-development/VALIDATION.json',source_role_coverage='velociraptor120-role-coverage.json',paper_role_coverage={'P228':reading['use_summary']+' '+reading['implementation_scope_note']},uncovered_roles=['Unknown author wrapper/backend editions, mode, input layers and exact calls','Unresolved original native stack diagnostics require complete raw-console review','Untested operations/platforms/versions and graph pointer/environment identity','Independent scientific/biological validity, actual convergence and paper reproduction'],whole_tool_qualified=False,whole_paper_reproduced=False)
 return row

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Exactly eight predecessor metadata inputs required')
 inventory=json.loads(before[META[0]]);summary=json.loads(before[META[1]]);papers=json.loads(before[PAPERS]);document=json.loads(before[REVIEWS])
 if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(433,250,2672,1168):raise ValueError('Unexpected predecessor population; preserve the complete collections')
 indices=[i for i,row in enumerate(inventory) if row['id']=='velociraptor']
 if len(indices)!=1:raise ValueError('Missing/duplicate wrapper family')
 inventory[indices[0]]=entry(root,inventory[indices[0]]);reading=review(root)
 p=next(p for p in papers if p['paper_id']=='P228');observations=[r for r in p['tools'] if r['tool_id']=='velociraptor'];reviews=[r for r in document['reviews'] if r['tool_id']=='velociraptor']
 if len(observations)!=1 or len(reviews)!=1:raise ValueError('Missing/duplicate existing wrapper use')
 for row in [observations[0],reviews[0]]:row['implementation_scope_note']=reading['implementation_scope_note']
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(t['implementation_status'] for t in inventory));expected=copy.deepcopy(summary['implementation_status_counts']);expected['upstream-adapter-validated']+=1;expected['missing']-=1
 if updated['implementation_status_counts']!=expected or (expected['native-subset-validated'],expected['upstream-adapter-validated'],expected['missing'])!=(20,92,262):raise ValueError('Unexpected implementation credit')
 if len(document['reviews'])!=1168 or len(papers)!=250 or len(inventory)!=433:raise ValueError('Complete collections changed size')
 page=before[META[4]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Predecessor embedded catalog differs')
 page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
 historical='<details><summary>Previous scVelo catalog checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 if page.count(PRIOR_NOTICE)!=1 or page.count('<p id="overview"></p>')!=1:raise ValueError('Predecessor notice/overview differs')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warning=before[META[5]].decode();marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warning.count(PRIOR_NOTICE)!=1 or warning.count(marker)!=1:raise ValueError('Predecessor warning notice differs')
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1)
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [velociraptor commands, source role and recovery](../VELOCIRAPTOR120_CATALOG_INTEGRATION.md). Validate with `python tools/literature/velociraptor_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-velociraptor-catalog-check --rescan-sources`. The full goal remains open.\n\n'
 paper_bytes=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_bytes=paper_bytes.replace(b'\n',b'\r\n')
 return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:paper_bytes,REVIEWS:encoded(document),META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact one-existing-wrapper admission')
 return dict(papers=250,named_tools=433,observations=2672,reviewed_uses=1168,unreviewed_uses=1504,new_scoped_integrations=112,missing=262,unchanged_previous_families=432,unchanged_previous_observations=2671,unchanged_previous_reviews=1167,enriched_existing_observations=1,revised_existing_reviews=1,added_observations=0,added_reviews=0,new_scoped_integration_credit=1,native_family_credit_added=0,new_registry_definitions=0,whole_goal_complete=False)
