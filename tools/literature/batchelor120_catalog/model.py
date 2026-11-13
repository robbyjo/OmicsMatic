# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append the two reviewed Batchelor roles; retain the uncovered FastMNN role."""
from pathlib import Path
from collections import Counter
import copy,hashlib,html,json,re

ROOT=Path(__file__).resolve().parents[3]
DATA='docs/literature/omics-2023-2026'
EVIDENCE='docs/literature/batchelor-catalog-integration'
WORKFLOW='docs/literature/monocle137-workflow-development'
PAPERS=DATA+'/papers.json';REVIEWS='tools/literature/reviewed-uses.json'
META=tuple(DATA+'/'+n for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md'])+('site/literature-review.html','site/literature-warnings.html',PAPERS,REVIEWS)
REGISTRY='tools/literature/batchelor-named-software-registry.json'
PATTERN=r'(?<![A-Za-z0-9_])(?:batchelor|fastMNN|reducedMNN|mnnCorrect|multiBatchNorm|rescaleBatches|correctExperiments)(?![A-Za-z0-9_])'
REGISTRY_ROW=dict(id='batchelor',name='Batchelor / FastMNN',pattern=PATTERN,role='Batch correction and integration of single-cell expression or imaging-protein measurements',current_coverage='missing',existing_source='src/main/java/org/jlinalg/cli/Monocle137WorkflowCli.java',coverage_note='P051 Batchelor1.20.0 has a tested selected Monocle1.3.7/reducedMNN workflow. P040 reports FastMNN1.29 on transformed imaging-protein data, and its author processing notebook imports batchelor, but the correction call and exact package version were not recovered. The distinct FastMNN role remains missing; reducedMNN does not substitute for it.')
SCOPE_040='The paper reports fastMNN v.1.29 for imaging mass cytometry after transformation and QC, not raw RNA counts. The author processing notebook imports batchelor, supporting package association, but contains no recovered fastMNN call/sessionInfo; its cell-annotation notebook is empty at reviewed commit c8074a59e2fd6497e4e613eb2b2f9ea4f2a85e3d. Preserve the literal reported method version 1.29; no Batchelor package patch or execution settings are established. The tested Monocle1.3.7/Batchelor1.20 reducedMNN workflow does not implement this distinct FastMNN/protein role. No author code was executed or redistributed; repository license metadata is absent.'
SCOPE_051='Batchelor1.20.0 batch integration is reported before Monocle3 1.3.7 trajectories. A tested selected implementation uses original Monocle1.3.7 align_cds calling original Batchelor1.20 reducedMNN on caller-prepared coordinates, with complete source controls. This is an explicit interpretation: the paper integration function, commit, transformation, graph settings and roots are unreported. It does not recover the paper call, implement P040 FastMNN/protein processing, qualify all Batchelor operations or reproduce the study. Read BATCHELOR_INTEGRATION.md and MONOCLE137_WORKFLOW_DEVELOPMENT.md.'

def encoded(v):return (json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8')
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
 return dict(bytes=size,sha256=h.hexdigest())

def additions(before):
 papers=json.loads(before[PAPERS]);by={p['paper_id']:p for p in papers};uses={};reviews=[]
 specs=[('P040',28,'Methods / Processing and normalization of IMC data','fastMNN','1.29','5c7694140a5b174f4432e138890d7e19a0503c83d3e2ba3d30e2ad92a6e73dc7','Batch-corrected transformed imaging mass cytometry measurements by ablation run, including Ki-67 expression; used the corrected measurements for cell clustering and annotation.','imaging-protein-fastmnn',SCOPE_040,[dict(paragraph_index=29,section='Methods / Cell-type annotation',paragraph_sha256='3464c1c1e537533ef0ed58c20b82001f90306bb8c04d1e07696c0f2d084633c6')]),('P051',86,'Methods / Single-cell transcriptomics on mouse neutrophils / Velocyto analysis','Batchelor','1.20.0','314baa7c4aec361901f183a11e4d398633d423ea5504c110eb1a24f5e99ad517','Integrated single-cell samples to address batch effects before Monocle3 dimensional reduction, clustering and pseudotime.','single-cell-batch-integration',SCOPE_051,[])]
 for paper_id,index,section,matched,version,sha,summary,context,scope,support in specs:
  p=by[paper_id]
  if any(t['tool_id']=='batchelor' for t in p['tools']):raise ValueError('Batchelor observation already exists')
  use=dict(tool_id='batchelor',tool='Batchelor / FastMNN',analysis_role=REGISTRY_ROW['role'],evidence_section=section,evidence_paragraph_index=index,matched_text=matched,extraction_score=15,usage_context=context,review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,evidence_selection='individually-reviewed-paragraph',evidence_paragraph_sha256=sha,use_summary=summary,evidence_locator=p['fulltext_url']+' (section: '+section+')',implementation_scope_note=scope,supporting_evidence=support)
  uses[paper_id]=use
  reviews.append(dict(pmcid=p['pmcid'],doi=p['doi'],tool_id='batchelor',**{k:copy.deepcopy(use[k]) for k in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence']}))
 return uses,reviews

WARNING='''

## Batchelor and FastMNN: supported operation, remaining gap and required actions

**P051: a tested original Batchelor1.20 reducedMNN integration is available**
through the selected Monocle3 1.3.7 workflow. Read
[BATCHELOR_INTEGRATION.md](../BATCHELOR_INTEGRATION.md). It uses explicit inputs,
batch labels and roots, with separate LEARN and RUN admission. The original
paper integration function, inputs, settings and roots remain unreported.

**P040 FastMNN on imaging-protein data remains missing.** The paper reports
method version1.29; do not reinterpret it as a recovered Batchelor package
patch. The author notebook imports batchelor but supplies no recovered
correction call or session information; the annotation notebook is empty.
Do not replace fastMNN with reducedMNN or treat transformed protein signals
as RNA counts. This newly recognized role keeps Batchelor's overall status
missing, with a partial upstream adapter available. No new integration badge
is awarded. The checkpoint remains102 scoped integrations; missing tools rise
to245 because the previously omitted package is now visible.

**SKIP3: skip dependent analysis if the exact Linux R/Monocle/Batchelor runtime
cannot be supplied or installed where permitted.** The candidate JAR contains
no original runtime. Setup and analysis are separate; use the documented
helper with installation/download flags only on permitted hosts. Analysis
installs nothing. Windows users can use permitted WSL and Linux Java paths.

**FAIL2: no result is admitted on input, integrity, capacity, process or
scientific failures.** Preserve complete inputs, WARNINGS.md, status and
original process/RDS diagnostics; correct the named cause and retry a NEW
output path. Disconnected infinite pseudotime values remain present. LEARN
does not admit a pseudotime result. Do not infer roots or repair values to
conceal failures. Full Batchelor, study reproduction and biological accuracy
remain unproven; the250-paper goal remains active.
'''
def warning_paragraph(text):
 escaped=html.escape(text)
 escaped=re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',escaped,flags=re.S)
 escaped=escaped.replace('[BATCHELOR_INTEGRATION.md](../BATCHELOR_INTEGRATION.md)','<code>docs/literature/BATCHELOR_INTEGRATION.md</code> in the repository')
 return '<p>'+escaped+'</p>'
NOTICE='<section id="batchelor-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Batchelor / FastMNN: warnings and required actions</h2>'+''.join(warning_paragraph(p) for p in WARNING.strip().split('\n\n')[1:])+'</section>'

def entry(root):
 return dict(**REGISTRY_ROW,paper_count=2,papers=['P040','P051'],implementation_status='missing',implementation_source=REGISTRY_ROW['existing_source'],implementation_scope='Partial original upstream integration: Monocle3 1.3.7 align_cds -> Batchelor1.20 reducedMNN on explicitly prepared expression and batch labels, followed by UMAP/clustering/graph learning and explicit-root native ordering. P040 fastMNN on imaging-protein measurements, reported method version1.29, remains unimplemented. The whole named package therefore remains missing; no new scoped integration badge is awarded. Read BATCHELOR_INTEGRATION.md.',validation_note='The selected P051 interpretation passed complete original64-gene/480-cell pipeline controls, independent original reducedMNN alignment equality, PCA/LSI, residual adjustment, clustering and explicit-root checks. Disconnected control retains240 infinite cells. Public complete-archive replay passed Windows Java21/25 and Linux Java21. These controls do not establish the paper function/settings or validate P040 fastMNN/protein processing.',partial_upstream_adapter_available=True,project_adapter_available=False,canonical_adapter_available=False,root_dispatch_available=False,development_adapter_cli='java -jar monocle137-workflow-jdk21.jar',development_candidate=WORKFLOW+'/monocle137-workflow-jdk21.jar',development_candidate_sha256=pin(root/WORKFLOW/'monocle137-workflow-jdk21.jar')['sha256'],development_build='tools/literature/monocle137/build.py',source_roles_reviewed=2,source_role_coverage='batchelor-role-coverage.json',workflow_validation='../monocle137-workflow-development/VALIDATION.json',covered_selected_roles=['P051: explicitly selected reducedMNN interpretation'],uncovered_roles=['P040: fastMNN on imaging-protein measurements; reported method version1.29'],external_analysis_runtime_required=True,new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False,paper_exact_unknown_settings_available=False)

def transition(before,new_entry):
 if set(before)!=set(META):raise ValueError('Only eight exact catalog/review paths may have predecessors')
 inventory=json.loads(before[META[0]]);summary=json.loads(before[META[1]]);papers=json.loads(before[PAPERS]);reviews=json.loads(before[REVIEWS])
 if len(papers)!=250 or sum(len(p['tools']) for p in papers)!=2479 or len(reviews['reviews'])!=1052 or len(inventory)!=405 or any(t['id']=='batchelor' for t in inventory):raise ValueError('Unexpected predecessor populations')
 uses,added=additions(before)
 for p in papers:
  if p['paper_id'] in uses:p['tools'].append(uses[p['paper_id']])
 reviews['reviews'].extend(added)
 # Preserve every old inventory row and its relative order; insert at the end of the2-paper group.
 new=copy.deepcopy(inventory);position=next((i for i,t in enumerate(new) if t['paper_count']<2),len(new));new.insert(position,copy.deepcopy(new_entry))
 updated=copy.deepcopy(summary);updated['paper_tool_observations']=2481;updated['distinct_named_tools']=406;updated['implementation_status_counts']=dict(Counter(t['implementation_status'] for t in new))
 expected=dict(summary['implementation_status_counts']);expected['missing']+=1
 if updated['implementation_status_counts']!=expected or expected['missing']!=245:raise ValueError('An uncovered role must remain missing; no integration badge may change')
 readme=before[META[2]].decode('utf8')
 for old,replacement in [('**244 named tools remain missing.**','**245 named tools remain missing.**'),('There are **405 named tools, 2,479 paper-tool observations and 1,052 reviewed\nuses**, with 377 main-article supporting pointers','There are **406 named tools, 2,481 paper-tool observations and 1,054 reviewed\nuses**, with 378 main-article supporting pointers')]:
  if readme.count(old)!=1:raise ValueError('Unexpected current checkpoint text')
  readme=readme.replace(old,replacement,1)
 marker="Cell Ranger remains excluded at the user's request. The full goal is active."
 if readme.count(marker)!=1:raise ValueError('Missing goal/exclusion boundary')
 readme=readme.replace(marker,marker+'\n\n**Batchelor / FastMNN: two omitted roles are now catalogued.** P051 has a\ntested selected Batchelor1.20 reducedMNN integration through Monocle1.3.7.\nP040 FastMNN on imaging-protein data remains missing, so no new integration\nbadge is awarded. Read [BATCHELOR_INTEGRATION.md](../BATCHELOR_INTEGRATION.md)\nfor the precise distinction, setup, warnings and recovery actions. All2,479\nprior observations and1,052 prior reviews remain unchanged.',1)
 page=before[META[4]].decode('utf8');m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or 'id="batchelor-required-actions"' in page:raise ValueError('Unexpected predecessor review page')
 embedded=json.loads(m[2])
 if embedded!={'papers':json.loads(before[PAPERS]),'tools':inventory,'summary':summary}:raise ValueError('Rendered predecessor differs from authoritative files')
 embedded.update(papers=papers,tools=new,summary=updated);raw=json.dumps(embedded,ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+raw+page[m.end(2):];page=page.replace('<script id="review-data"',NOTICE+'<script id="review-data"',1)
 warnings=before[META[5]].decode('utf8')
 if warnings.count('</main>')!=1 or 'id="batchelor-required-actions"' in warnings:raise ValueError('Unexpected warning page')
 paper_raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_raw=paper_raw.replace(b'\n',b'\r\n')
 return {META[0]:encoded(new),META[1]:encoded(updated),META[2]:readme.encode('utf8'),META[3]:before[META[3]]+WARNING.encode('utf8'),META[4]:page.encode('utf8'),META[5]:warnings.replace('</main>',NOTICE+'</main>',1).encode('utf8'),PAPERS:paper_raw,REVIEWS:encoded(reviews)}

def check_transition(before,after,new_entry):
 expected=transition(before,new_entry)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from the exact two-role Batchelor addition')
 return dict(added_tool_ids=['batchelor'],added_roles=['P040','P051'],unchanged_observations=2479,unchanged_reviews=1052,observations=2481,reviewed_uses=1054,new_scoped_integrations=102,missing=245,new_scoped_integration_credit=0,whole_goal_complete=False)
