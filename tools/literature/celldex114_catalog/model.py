# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One celldex family credit and one coverage note; preserve all primary facts."""
from collections import Counter
import copy,json,re
from singler_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph

MODULE='tools/literature/celldex114_catalog'
PROGRAM='tools/literature/celldex114/run.py'
GUIDE='docs/literature/CELLDEX114_CATALOG_INTEGRATION.md'
EVIDENCE='docs/literature/celldex114-catalog-integration'
WORKFLOW='docs/literature/celldex114-reference-validation'
PROOF=DATA+'/celldex114-catalog-validation.json'
COMPATIBILITY=DATA+'/celldex114-reviews-shared-source-compatibility.json'
NOTE=('Project availability: complete unchanged original celldex1.14.0 reference retrieval through a source-built R package and explicit managed cache. '
 'Project-selected ImmGenData, MouseRNAseqData, dated fetchReference/fetchMetadata and query controls retain full returns, labels, IDs, conditions, RNG and lazy backing files. '
 'Legacy, ontology and Ensembl modes use original semantics; mapping warnings and original gene removal are retained. '
 'Cached Hub operations require explicit original LOCAL caller options for offline success; unchanged defaults can still contact the service and fail offline. '
 'Reference networking and runtime installation have separate permission flags; no source, identifier, warning-policy or version fallback is added. '
 'The literal MouseRNAseq ImmGen wording, human-neutrophil heading and unknown exact author functions/snapshots/options/environment remain unchanged. '
 'Original biological reference records remain complete and private; software licensing does not relicense the underlying datasets. '
 'SKIP3 skips dependent retrieval for missing/forbidden requirements; FAIL2 admits no complete result, including recording failure. '
 'SingleR annotation and SPOTlight deconvolution are separate. Whole-tool validity and paper reproduction remain unqualified.')
WARNING='''
## celldex 1.14.0: warnings and required actions

**250 papers, 441 named software families, 2,682 observations and 1,187 reviewed uses; 1,495 uses await review.** The fixed 2023-10-23 through 2026-10-23 cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers. Paper identities, reported versions and primary settings remain unchanged. Only the existing P051 celldex implementation coverage note changes. The full semantic review and implementation goal remains open.

**119 scoped new integrations: 20 native Java subsets and 99 original upstream workflows; 122 including three existing adapters. 263 named families remain missing.** celldex adds one upstream family credit and no native credit. SPOTlight and the BD Rhapsody analysis pipeline remain separate missing families. ImmGen is a biological reference, and Celldex Therapeutics is a company, not another software use.

P051 reports celldex 1.14.0 and literally describes MouseRNAseq ImmGen reference profiles. Exact functions, snapshots, gene-ID options and the author environment are not established; the human-neutrophil heading and reference wording are retained. The source-built 1.14.0 target uses explicit R4.4.3/Bioconductor3.20 comparison dependencies, selected by the project. Reference retrieval, SingleR annotation and SPOTlight deconvolution are separate operations.

**Offline Hub retrieval needs explicit original LOCAL caller options and a complete cache.** Legacy/ontology and Ensembl paths can contact Hub services even with cached files under their unchanged defaults. Record ExperimentHub and AnnotationHub LOCAL=TRUE in the trusted request when local-only retrieval is intended. The adapter never silently changes those defaults. Original Ensembl conversion can warn and remove unmapped or duplicate mapped genes; read every original warning. Keep lazy-reference backing files at their recorded paths, or explicitly request the original realization option before calling the function.

**SKIP3 skips dependent retrieval** for a named missing or forbidden exact runtime, source, reference, installation, network-isolation or download requirement. Supply that permitted requirement and retry NEW paths. Runtime installation needs --allow-install; reference downloads separately need --allow-download. **FAIL2 admits no complete result**, including original errors, native crashes and recording failures after an original return. Read WARNINGS.md, FAILURE.json, original/R-status.json, complete conditions/RNG and both raw consoles. Resolve the stated input/filesystem cause, preserve all originals/partials and retry NEW paths. Only final COMPLETED.json admits the public operation; a leftover RDS or successful R exit is insufficient.

Sixteen comparisons with explicit LOCAL options produced 12 complete returns and four original errors; complete objects, conditions/RNG and independently materialized lazy assays matched. Earlier default-offline runs had seven returns and nine original errors, including five Hub failures, all retained without results. Twelve actual Linux public guards, two Windows prerequisite skips, both installer branches and both installed-runtime analyses passed. The fresh-download installed analysis started with an empty cache. These checks establish no whole-tool validity, biological accuracy or paper reproduction.

**Complete reference-data records remain local and unchanged.** Public source/receipt checks differ from full private-record readback and fresh computation. The complete private index identifies 4,469 retained files; no reference matrix is redistributed or assigned the software's license. Use the documented permitted inputs/runtime to repeat the controls. P233's SingleR 2.41 remains unresolved and skipped; Cell Ranger/ARC remain excluded by user request. Earlier source, license and runtime restrictions remain in their guides and historical notices.
'''
QUICKSTART='''<details id="celldex114-runtime-guide"><summary>celldex commands and recovery guide</summary>
<p>Use permitted Linux/WSL, an existing permitted Pixi executable and NEW paths. Installation retrieves software; the analysis flag permits reference-data networking separately.</p>
<pre>python3 -B tools/literature/celldex114/install_runtime.py --manager /permitted/pixi --allow-install --allow-download --cache-dir /permitted/cache --out /private/NEW-install</pre>
<p>Create a trusted request RDS for edition 1.14.0 with the original operation/arguments and intended RNG. For cached Hub retrieval, explicitly set request$hub_options to list(ExperimentHub=list(LOCAL=TRUE), AnnotationHub=list(LOCAL=TRUE)). These are recorded caller choices, not inferred paper settings. The source guide provides the complete request constructor.</p>
<pre>python3 -B tools/literature/celldex114/run.py --runtime /private/NEW-install-provider --request /trusted/request.rds --reference-cache /preserved/cache --out /results/NEW-reference</pre>
<p>Add --allow-download only where reference networking is permitted. Otherwise an isolated offline child uses the complete copied cache. Read the original return, warnings, conditions/RNG and raw consoles. For full commands and recovery, open repository documents <code>docs/literature/CELLDEX114_CATALOG_INTEGRATION.md</code> and <code>docs/literature/CELLDEX114_REFERENCE_WORKFLOWS.md</code>.</p></details>'''
NOTICE='<section id="celldex114-required-actions" role="note"><h2>celldex 1.14.0: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+QUICKSTART+'</section>'
STYLE='\n<style id="celldex114-warning-wrap">#celldex114-required-actions { overflow-wrap: anywhere; min-width: 0; } #celldex114-required-actions pre { white-space: pre-wrap; overflow-wrap: anywhere; }</style>\n'
def entry(root,old):
 if old['id']!='celldex' or old['implementation_status']!='missing' or old['papers']!=['P051']:raise ValueError('Exactly the existing missing P051 celldex family required')
 result=copy.deepcopy(old)
 result.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
  coverage_note='Original celldex1.14.0 reference retrieval with dated/legacy backends, explicit local Hub options, complete original objects and source failures retained.',
  implementation_scope=NOTE,validation_note='16 explicit-LOCAL controls:12complete returns/4original errors; whole objects/conditions/RNG and lazy payloads.12Linux public guards,2Windows prerequisite skips,both actual installers and installed-runtime analyses.4469private files fully read back; reference matrices not redistributed. Whole-tool/paper reproduction unqualified.',
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=PROGRAM,
  development_candidate_sha256=pin(root/PROGRAM)['sha256'],development_candidate_kind='python-source-entrypoint-original-Linux-WSL-reference-retrieval',
  development_build='tools/literature/celldex114/install_runtime.py',development_adapter_cli='python3 '+PROGRAM+' --runtime /permitted/provider --request /trusted/request.rds --reference-cache /complete/cache --out /NEW/output',
  selected_upstream_versions=['1.14.0'],selected_R_versions=['4.4.3'],selected_environment_is_author_environment=False,author_reference_functions_established=False,
  author_reference_snapshots_established=False,author_caller_options_established=False,workflow_validation='../celldex114-reference-validation/QUALIFICATION.json',current_documentation=GUIDE,
  paper_role_coverage={'P051':primary_review(root)['use_summary']+' '+NOTE},source_roles_reviewed=1,
  reference_data_redistribution_license_established=False,reference_matrices_redistributed=False,new_scoped_integration_credit=1,native_family_credit_added=0,
  whole_tool_qualified=False,whole_paper_reproduced=False)
 return result
def primary_review(root):
 rows=[r for r in json.loads((root/REVIEWS).read_bytes())['reviews'] if r['tool_id']=='celldex']
 if len(rows)!=1 or rows[0]['paper_id']!='P051' or rows[0]['version_reported_near_name']!='1.14.0' or [x['paragraph_index'] for x in rows[0]['supporting_evidence']]!=[119]:raise ValueError('Exactly the source-specific P0511.14.0 reading required')
 return rows[0]
def transition(root,before):
 if set(before)!=set(META):raise ValueError('Eight complete preceding catalog inputs required')
 inv,summary=json.loads(before[META[0]]),json.loads(before[META[1]]);papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
 if (len(inv),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(441,250,2682,1187):raise ValueError('Preceding complete populations differ')
 for i,row in enumerate(inv):
  if row['id']=='celldex':inv[i]=entry(root,row)
 indices=[i for i,r in enumerate(document['reviews']) if r['tool_id']=='celldex']
 if len(indices)!=1:raise ValueError('Exactly one existing celldex review required')
 document['reviews'][indices[0]]['implementation_scope_note']=NOTE
 observations=[t for p in papers for t in p['tools'] if t['tool_id']=='celldex']
 if len(observations)!=1:raise ValueError('Exactly one existing celldex observation required')
 observations[0]['implementation_scope_note']=NOTE
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inv))
 counts=updated['implementation_status_counts']
 if (counts['missing'],counts['upstream-adapter-validated'],counts['native-subset-validated'])!=(263,99,20):raise ValueError('Exactly one upstream credit required')
 page,warning=before[META[4]].decode(),before[META[5]].decode();m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or json.loads(m[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Complete preceding embedded catalog differs')
 page=page[:m.start(2)]+json.dumps(dict(papers=papers,tools=inv,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[m.end(2):]
 historical='<details><summary>Previous SingleR checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]):raise ValueError('Exact preceding warning panels required')
 if page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Expected insertion points required')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [celldex source roles, commands and recovery](../CELLDEX114_CATALOG_INTEGRATION.md). Current cumulative entry point: `python tools/literature/celldex114_catalog/catalog.py --verify` with a NEW output path. This checks saved source/evidence; fresh computation and private data checks are separate. The full goal remains open.\n\n'
 pb=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):pb=pb.replace(b'\n',b'\r\n')
 return {META[0]:encoded(inv),META[1]:encoded(updated),PAPERS:pb,REVIEWS:encoded(document),
  META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
  META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}
def check_transition(root,before,after):
 if set(after)!=set(META) or transition(root,before)!=after:raise ValueError('Catalog differs from exact one-family/one-coverage-note transition')
 return dict(papers=250,named_tools=441,observations=2682,reviewed_uses=1187,unreviewed_uses=1495,new_scoped_integrations=119,missing=263,
  added_observations=0,added_reviews=0,new_registry_definitions=0,new_scoped_integration_credit=1,native_family_credit_added=0,whole_goal_complete=False)
