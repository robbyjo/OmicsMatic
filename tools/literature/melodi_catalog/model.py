# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact paper-specific identity correction and seven newly reviewed observations."""
from collections import Counter
import copy,json,re
from spotlight103_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph
from melodi_catalog.source import DEFINITIONS,MISSING_NOTE,audit
MODULE='tools/literature/melodi_catalog';PROGRAM='src/main/java/org/jlinalg/cli/MelodiPrestoCli.java'
GUIDE='docs/literature/MELODI_PRESTO_SOURCE_AND_SERVICE.md';EVIDENCE='docs/literature/melodi-catalog-integration'
WORKFLOW='docs/literature/melodi-service-validation';PROOF=DATA+'/melodi-catalog-validation.json';COMPATIBILITY=DATA+'/melodi-reviews-shared-source-compatibility.json'
NOTE=('Project availability: standalone Java adapter to the live MELODI Presto service, with complete original request/payload/HTTP/response bytes, exact numeric lexemes and unknown fields retained. '
 'This is the literature-triple service, not the unrelated presto single-cell Wilcoxon/AUC R package. '
 'Live requests require explicit network permission and server_snapshot live-unversioned. Deployed source and database identities remain unknown; no current response replaces the paper access date3April2024. '
 'Exact sentence count metadata is retained; lower-bound, capped, inconsistent or Error-bearing responses admit no complete result. '
 'Saved-record verification checks all bytes and revalidates the full recorded response without sending another query or proving server authorship. '
 'SKIP3 skips missing/forbidden runtime/network/service prerequisites; FAIL2 admits no complete result, including recording failure after a server return. '
 'No native analytical, Wilcoxon, local SemMedDB, biological-novelty or paper-reproduction claim is made.')
WARNING='''
## MELODI Presto: corrected identity, warnings and required actions

**250 papers, 445 named families, 2,689 observations and 1,195 reviewed uses; 1,494 uses await review.** The fixed 2023-10-23 through 2026-10-23 cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers. The full semantic review and implementation goal remains open.

**P169 uses MELODI Presto for literature triples, not the presto single-cell Wilcoxon/AUC R package.** The erroneous missing-family observation is replaced under the explicit canonical identity melodi-presto, with the old observation retained in the predecessor evidence. PRESTO-Tango in P038 is a plasmid kit; JST PRESTO in P157 is funding. Neither is another software use. The unrelated R source was downloaded for assessment only and was never installed or executed for this candidate. Existing unrelated R-dependency evidence from earlier workflows remains intact.

**121 scoped new integrations: 20 native Java subsets and 101 upstream workflows; 124 including three existing adapters. 265 named families remain missing.** The Java service adapter adds one upstream credit and no native analytic credit. Four omitted families are newly catalogued: OncoScore, oncoEnrichR, IntOGen and HaploReg. Seven new observations plus the corrected existing use receive source-specific reviews. The missing count rises because those omissions are now recorded. The IntOGen 2023 pipeline in P042 differs from P169's reuse of a driver resource released 1 February 2020 and restricted to COADREAD; HaploReg versions 4.1/4.2 and an unreported version belong to different papers. oncoEnrichR 1.4.2.1 is separate from Enrichr. These four families receive no implementation credit.

**The live server snapshot is unknown.** P169 accessed MELODI Presto on3April2024. Supply server_snapshot live-unversioned explicitly for a current query; historical snapshot requests are rejected rather than silently replaced. A current response does not establish novelty, absence of evidence, causal links, a historical database or the authors' original gene-list result. Full semantic triples, literature payloads and historical/failed responses remain private under their original data provenance; software licensing does not relicense them.

**SKIP3 skips dependent analysis** for a named missing or forbidden runtime, permitted networking, service/access or output prerequisite. Supply the permitted requirement, inspect the complete HTTP diagnostics and retry NEW paths; analysis installs nothing. **FAIL2 admits no complete result**, including bad requests, original Error-bearing responses, non-JSON replies, count mismatches, lower-bound/capped totals, truncated data and recording failure after a server return. Preserve all originals and partials. Read WARNINGS.md, FAILURE.json, HTTP.json and complete response.bin, resolve the named cause and retry NEW paths. Only final COMPLETED.json admits a recorded operation. Network transmission requires --allow-network and sends the explicit query terms to the recorded server.

The adapter preserves every original response field and byte, including numeric precision, unknown fields, nulls, identifiers and the sentence Elasticsearch count object. It does not turn error records into empty biological results. Verification rechecks saved integrity and response structure without rerunning the service; hashes do not establish server authenticity or recover an earlier database. Client fixture tests cover all three routes and actual transport/failure boundaries; the source-qualified service receipts distinguish successful live operations from retained failed attempts. Cell Ranger/ARC remain excluded; SingleR2.41 remains unresolved. Whole-tool validity, native analytic replacement, biological accuracy and paper reproduction remain unqualified.
'''
QUICKSTART='''<details id="melodi-runtime-guide"><summary>MELODI Presto commands and recovery</summary>
<p>Use an existing permitted Java17+ runtime and the built project JAR. Create a trusted request with schema1, operation overlap/enrich/sentence, the exact original payload and server_snapshot live-unversioned. No author settings or historical database are inferred.</p>
<pre>java -cp build/libs/OmicsMatic-0.3.7.jar org.jlinalg.cli.MelodiPrestoCli run --request /trusted/request.json --out /results/NEW-response --allow-network</pre>
<p>Read every original diagnostic before use. Missing permitted prerequisites skip the dependent query. Error, capped and incomplete responses admit no result. Keep complete original files.</p>
<pre>java -cp build/libs/OmicsMatic-0.3.7.jar org.jlinalg.cli.MelodiPrestoCli verify --bundle /results/SAVED-response --out /validation/NEW-readback</pre>
<p>Verification sends no query and does not reproduce the historical study. Repository guide: <code>docs/literature/MELODI_PRESTO_SOURCE_AND_SERVICE.md</code>.</p></details>'''
NOTICE='<section id="melodi-required-actions" role="note"><h2>MELODI Presto: corrected identity, warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+QUICKSTART+'</section>'
STYLE='\n<style id="melodi-warning-wrap">#melodi-required-actions { overflow-wrap: anywhere; min-width: 0; } #melodi-required-actions pre { white-space: pre-wrap; overflow-wrap: anywhere; }</style>\n'

def qualified_entry(root,old):
 row=copy.deepcopy(old)
 row.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
  coverage_note='Java adapter to complete live MELODI Presto API responses; the original paper server/database snapshot is unknown.',implementation_scope=NOTE,
  validation_note='Actual packaged service calls and complete saved-record readback; real HTTP fixture guards cover all three routes, deadlines, exact counts, original errors, full precision and postreturn recording failure. Source/deployment and historical results are not inferred.',
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=PROGRAM,
  development_candidate_kind='standalone-Java-main-upstream-service-adapter',development_candidate_sha256=pin(root/PROGRAM)['sha256'],
  development_build='./gradlew jar',development_adapter_cli='java -cp build/libs/OmicsMatic-0.3.7.jar org.jlinalg.cli.MelodiPrestoCli run --request /trusted/request.json --out /NEW/output --allow-network',
  selected_environment_is_author_environment=False,author_deployed_commit_established=False,author_database_snapshot_established=False,
  native_analytical_credit_added=0,new_scoped_integration_credit=1,source_roles_reviewed=1,workflow_validation='../melodi-service-validation/QUALIFICATION.json',current_documentation=GUIDE,
  whole_tool_qualified=False,whole_paper_reproduced=False)
 return row

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Eight exact predecessor metadata files required')
 inv,summary=json.loads(before[META[0]]),json.loads(before[META[1]]);papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
 if (len(inv),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(441,250,2682,1187):raise ValueError('Exact complete SPOTlight predecessor required')
 old=next(r for r in inv if r['id']=='presto')
 if old['name']!='Presto' or old['implementation_status']!='missing' or old['papers']!=['P169']:raise ValueError('Exactly the erroneous one-paper Presto family required')
 oldobs=[(p,t) for p in papers for t in p['tools'] if t['tool_id']=='presto']
 if len(oldobs)!=1 or oldobs[0][0]['paper_id']!='P169' or oldobs[0][1]['evidence_paragraph_index']!=49:raise ValueError('Exactly the old P169 identity error required')
 if any(r['tool_id'] in {'presto',*[d['id'] for d in DEFINITIONS]} for r in document['reviews']):raise ValueError('New primary reviews must not overwrite existing reviewed facts')
 inv=[r for r in inv if r['id']!='presto'];oldobs[0][0]['tools'].remove(oldobs[0][1]);rows=copy.deepcopy(audit(root)['reviews'])
 for d in DEFINITIONS:
  reviews=[r for r in rows if r['tool_id']==d['id']];ids=sorted(r['paper_id'] for r in reviews)
  row=dict(copy.deepcopy(d),paper_count=len(ids),papers=ids,implementation_status='missing',implementation_source=None,implementation_scope=MISSING_NOTE,
   validation_note='All250 primary sources searched; use-specific roles, versions and reported settings reviewed. No runtime/implementation credit.',source_roles_reviewed=len(ids),new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False)
  if d['id']=='melodi-presto':row=qualified_entry(root,row)
  inv.append(row)
 for r in rows:
  if r['tool_id']=='melodi-presto':r['implementation_scope_note']=NOTE
  paper=next(p for p in papers if p['paper_id']==r['paper_id']);definition=next(d for d in DEFINITIONS if d['id']==r['tool_id'])
  if any(t['tool_id']==r['tool_id'] for t in paper['tools']):raise ValueError('Duplicate corrected/new software use')
  anchor=next(a for a in r['supporting_evidence'] if a['paragraph_index']==r['evidence_paragraph_index'])
  t=dict(tool_id=r['tool_id'],tool=definition['name'],analysis_role=definition['role'],evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],
   matched_text=definition['name'],extraction_score=15,usage_context=r['usage_context'],evidence_selection='individually-reviewed-primary-source-identity-and-role',
   evidence_paragraph_sha256=anchor['paragraph_sha256'],evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')',review_status='usage-summary-reviewed; paper-wide-completeness-review-open')
  for k in ['version_reported_near_name','use_summary','implementation_scope_note','supporting_evidence']:t[k]=copy.deepcopy(r[k])
  paper['tools'].append(t)
 document['reviews']+=rows;updated=copy.deepcopy(summary)
 updated.update(distinct_named_tools=len(inv),paper_tool_observations=sum(len(p['tools']) for p in papers),implementation_status_counts=dict(Counter(r['implementation_status'] for r in inv)))
 c=updated['implementation_status_counts']
 if (len(inv),updated['paper_tool_observations'],len(document['reviews']),c['missing'],c['upstream-adapter-validated'],c['native-subset-validated'])!=(445,2689,1195,265,101,20):raise ValueError('Exact corrected/source-specific populations differ')
 page,warning=before[META[4]].decode(),before[META[5]].decode();m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or json.loads(m[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Complete predecessor embedded catalog differs')
 page=page[:m.start(2)]+json.dumps(dict(papers=papers,tools=inv,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[m.end(2):]
 historical='<details><summary>Previous SPOTlight checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>';marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]) or page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Exact predecessor warning panels required')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
 warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [MELODI Presto identity, commands and recovery](../MELODI_PRESTO_SOURCE_AND_SERVICE.md). Current cumulative entry point: `python tools/literature/melodi_catalog/catalog.py --verify` with a NEW output path. Saved integrity checks do not rerun the service or recover a historical snapshot. The full goal remains open.\n\n'
 pb=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):pb=pb.replace(b'\n',b'\r\n')
 return {META[0]:encoded(inv),META[1]:encoded(updated),PAPERS:pb,REVIEWS:encoded(document),
  META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
  META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
 if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Catalog differs from exact Presto identity and companion-use transition')
 return dict(papers=250,named_tools=445,observations=2689,reviewed_uses=1195,unreviewed_uses=1494,new_scoped_integrations=121,missing=265,
  reclassified_existing_observations=1,added_observations=7,added_reviews=8,new_registry_definitions=5,inactive_R_Presto_definition_retained=True,
  new_scoped_integration_credit=1,native_family_credit_added=0,whole_goal_complete=False)
