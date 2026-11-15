# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One SPOTlight family credit and one coverage note; preserve all primary facts."""
from collections import Counter
import copy,json,re
from celldex114_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph

MODULE='tools/literature/spotlight103_catalog'
PROGRAM='tools/literature/spotlight103/run.py'
GUIDE='docs/literature/SPOTLIGHT103_CATALOG_INTEGRATION.md'
EVIDENCE='docs/literature/spotlight103-catalog-integration'
WORKFLOW='docs/literature/spotlight103-deconvolution-validation'
PROOF=DATA+'/spotlight103-catalog-validation.json'
COMPATIBILITY=DATA+'/spotlight103-reviews-shared-source-compatibility.json'
NOTE=('Project availability: complete unchanged original SPOTlight1.0.3 R/C++ deconvolution from upstream commit0a85246f93167f869de704166627fe6679fe03b5, selected by the project. '
 'Complete SPOTlight, trainNMF and runDeconvolution calls and an explicit training/deconvolution composition preserve full original objects, conditions, RNG, IDs and NA/NaN/Inf. '
 'In this source, model ns/std use the same routine, min_prop does not filter proportions and extra arguments may be ignored; the original return does not certify NMF convergence. '
 'Original zero-signal spot NaN values and source errors remain unchanged. The reported >=10% neutrophil labeling is a separate downstream choice with no inferred reference column. '
 'Both full process-specific C++ backtraces are retained; source-origin frames and all other condition fields/calls are compared, without requiring ASLR addresses or wrapper frames to match. '
 'Analysis runs offline and installs nothing. SKIP3 skips dependent analysis for missing/forbidden prerequisites; FAIL2 admits no complete result, including recording failure after an original return. '
 'Exact author commit, references, markers, settings, seed and environment remain unknown. SingleR annotation and celldex retrieval are separate operations. '
 'Whole-tool validity, model convergence, biological accuracy and paper reproduction remain unqualified.')
WARNING='''
## SPOTlight 1.0.3: warnings and required actions

**250 papers, 441 named software families, 2,682 observations and 1,187 reviewed uses; 1,495 uses await review.** The fixed 2023-10-23 through 2026-10-23 cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers. Only the existing P051 SPOTlight implementation coverage note changes. Primary facts, source versions, settings and anchors remain unchanged. The semantic review and implementation goal remains open.

**120 scoped new integrations: 20 native Java subsets and 100 original upstream workflows; 123 including three existing adapters. 262 named families remain missing.** SPOTlight adds one upstream family credit and no native credit. The BD Rhapsody analysis pipeline remains missing. Spotlight Therapeutics is a company and the ordinary verb spotlight is not a software use.

**Original arguments are not guarantees:** in this pinned 1.0.3 source, model ns/std use the same native routine, min_prop does not filter proportions and extra arguments can be accepted without effect. The original return does not certify NMF convergence; an iteration-limited return remains possible. Original all-zero spots can return NaN. Inspect original diagnostics, retain all values and validate fitness for use. The paper's >=10% labeling is a separate downstream analysis decision with no inferred neutrophil column.

P051 reports SPOTlight 1.0.3 in two distinct deconvolution contexts (source paragraphs114/119). The selected original commit0a85246f93167f869de704166627fe6679fe03b5 declares that version and contains its own C++ NMF/NNLS routines; the attempted Bioconductor1.0.3 archive returned404. This exact project source and R4.4.3 comparison environment do not establish the authors' exact commit, inputs, references, markers, settings, seed or environment. SingleR annotation and celldex reference retrieval remain separate operations.

**SKIP3 skips dependent analysis** for the named missing or forbidden exact source, runtime, Linux/network isolation or installation/download requirement. Supply that permitted requirement and retry NEW paths. Installation requires --allow-install; software networking additionally requires --allow-download. Analysis installs and downloads nothing. **FAIL2 admits no complete result**, including original errors, native crashes, altered captured inputs, missing status and postreturn recording failure. Read WARNINGS.md, FAILURE.json, original/R-status.json, all original conditions/RNG and both consoles. Resolve the stated cause, retain originals/partials and retry NEW paths. Only final COMPLETED.json admits a recorded operation.

Twenty-nine independent comparisons produced 20 complete returns and nine original errors. Complete original returns, attributes, intermediate models and RNG matched. All condition fields/calls and original native-origin frames were compared. Both complete C++ backtraces are retained; ASLR addresses and wrapper-dependent frames are not required to be byte-identical. Eleven actual Linux public guards, two Windows prerequisite skips, both public installers and both complete installed-runtime analyses passed. Matching errors never count as successful deconvolution. Full synthetic records and earlier failed attempts are retained. Saved-record readback differs from fresh scientific computation; neither establishes convergence, biological validity, whole-tool validity or paper reproduction.

P233's SingleR2.41 remains unresolved and skipped. Cell Ranger/ARC remain excluded by user request. Earlier source, license and runtime restrictions remain in their guides and historical notices. The full250-paper goal remains open.
'''
QUICKSTART='''<details id="spotlight103-runtime-guide"><summary>SPOTlight commands and recovery guide</summary>
<p>Use permitted Linux/WSL, an existing permitted Pixi executable and NEW paths. Runtime installation and analysis are separate.</p>
<pre>python3 -B tools/literature/spotlight103/install_runtime.py --manager /permitted/pixi --allow-install --allow-download --cache-dir /permitted/cache --out /private/NEW-install</pre>
<p>Create a trusted request RDS for edition1.0.3 with original inputs, markers, groups, arguments and explicit intended RNG. The source guide gives a complete constructor. No author settings or labels are guessed.</p>
<pre>python3 -B tools/literature/spotlight103/run.py --runtime /private/NEW-install-provider --request /trusted/request.rds --out /results/NEW-deconvolution</pre>
<p>The original child runs offline; analysis never installs software. Read all original warnings, conditions/RNG and raw consoles. Original min_prop does not apply the paper's labeling threshold. See repository documents <code>docs/literature/SPOTLIGHT103_CATALOG_INTEGRATION.md</code> and <code>docs/literature/SPOTLIGHT103_DECONVOLUTION_WORKFLOWS.md</code>.</p></details>'''
NOTICE='<section id="spotlight103-required-actions" role="note"><h2>SPOTlight 1.0.3: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+QUICKSTART+'</section>'
STYLE='\n<style id="spotlight103-warning-wrap">#spotlight103-required-actions { overflow-wrap: anywhere; min-width: 0; } #spotlight103-required-actions pre { white-space: pre-wrap; overflow-wrap: anywhere; }</style>\n'

def entry(root,old):
 if old['id']!='spotlight' or old['implementation_status']!='missing' or old['papers']!=['P051']:raise ValueError('Exactly the existing missing P051 SPOTlight family required')
 result=copy.deepcopy(old)
 result.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
  coverage_note='Original pinned SPOTlight1.0.3 full NMF/NNLS deconvolution, complete original objects and source failures; original unapplied options and uncertified convergence retained.',
  implementation_scope=NOTE,validation_note='29controls:20complete returns/9original errors; full original returns/RNG, condition fields/calls and native origin compared; full process-specific C++ traces retained.11Linux public guards,2Windows skips,both installers and installed analyses. Saved full synthetic record readback passed. No model convergence, biological, whole-tool or paper claim.',
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=PROGRAM,
  development_candidate_sha256=pin(root/PROGRAM)['sha256'],development_candidate_kind='python-source-entrypoint-original-Linux-WSL-spatial-deconvolution',
  development_build='tools/literature/spotlight103/install_runtime.py',development_adapter_cli='python3 '+PROGRAM+' --runtime /permitted/provider --request /trusted/request.rds --out /NEW/output',
  selected_upstream_versions=['1.0.3'],selected_R_versions=['4.4.3'],selected_environment_is_author_environment=False,author_exact_source_commit_established=False,original_NMF_convergence_certified=False,
  author_reference_snapshots_established=False,author_caller_options_established=False,workflow_validation='../spotlight103-deconvolution-validation/QUALIFICATION.json',current_documentation=GUIDE,
  paper_role_coverage={'P051':primary_review(root)['use_summary']+' '+NOTE},source_roles_reviewed=1,
  synthetic_records_complete=True,biological_accuracy_established=False,new_scoped_integration_credit=1,native_family_credit_added=0,
  whole_tool_qualified=False,whole_paper_reproduced=False)
 return result
def primary_review(root):
 rows=[r for r in json.loads((root/REVIEWS).read_bytes())['reviews'] if r['tool_id']=='spotlight']
 if len(rows)!=1 or rows[0]['paper_id']!='P051' or rows[0]['version_reported_near_name']!='1.0.3' or [x['paragraph_index'] for x in rows[0]['supporting_evidence']]!=[114,119]:raise ValueError('Exactly the source-specific P0511.0.3 reading required')
 return rows[0]
def transition(root,before):
 if set(before)!=set(META):raise ValueError('Eight complete preceding catalog inputs required')
 inv,summary=json.loads(before[META[0]]),json.loads(before[META[1]]);papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
 if (len(inv),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(441,250,2682,1187):raise ValueError('Preceding complete populations differ')
 for i,row in enumerate(inv):
  if row['id']=='spotlight':inv[i]=entry(root,row)
 indices=[i for i,r in enumerate(document['reviews']) if r['tool_id']=='spotlight']
 if len(indices)!=1:raise ValueError('Exactly one existing SPOTlight review required')
 document['reviews'][indices[0]]['implementation_scope_note']=NOTE
 observations=[t for p in papers for t in p['tools'] if t['tool_id']=='spotlight']
 if len(observations)!=1:raise ValueError('Exactly one existing SPOTlight observation required')
 observations[0]['implementation_scope_note']=NOTE
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inv))
 counts=updated['implementation_status_counts']
 if (counts['missing'],counts['upstream-adapter-validated'],counts['native-subset-validated'])!=(262,100,20):raise ValueError('Exactly one upstream credit required')
 page,warning=before[META[4]].decode(),before[META[5]].decode();m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or json.loads(m[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Complete preceding embedded catalog differs')
 page=page[:m.start(2)]+json.dumps(dict(papers=papers,tools=inv,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[m.end(2):]
 historical='<details><summary>Previous celldex checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]):raise ValueError('Exact preceding warning panels required')
 if page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Expected insertion points required')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [SPOTlight source roles, commands and recovery](../SPOTLIGHT103_CATALOG_INTEGRATION.md). Current cumulative entry point: `python tools/literature/spotlight103_catalog/catalog.py --verify` with a NEW output path. This checks saved source/evidence; fresh computation is separate. The full goal remains open.\n\n'
 pb=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):pb=pb.replace(b'\n',b'\r\n')
 return {META[0]:encoded(inv),META[1]:encoded(updated),PAPERS:pb,REVIEWS:encoded(document),
  META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
  META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}
def check_transition(root,before,after):
 if set(after)!=set(META) or transition(root,before)!=after:raise ValueError('Catalog differs from exact one-family/one-coverage-note transition')
 return dict(papers=250,named_tools=441,observations=2682,reviewed_uses=1187,unreviewed_uses=1495,new_scoped_integrations=120,missing=262,
  added_observations=0,added_reviews=0,new_registry_definitions=0,new_scoped_integration_credit=1,native_family_credit_added=0,whole_goal_complete=False)
