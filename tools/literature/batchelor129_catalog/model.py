# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One scoped Batchelor admission plus one source-verified Rphenograph addition."""
from pathlib import Path
from collections import Counter
import copy,html,json,re
from batchelor120_catalog.model import META,PAPERS,REVIEWS,DATA,encoded,rawpin,pin,NOTICE as PRIOR_NOTICE
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE='docs/literature/batchelor129-catalog-integration'
WORKFLOW='docs/literature/fastmnn129-workflow-development'
REGISTRY='tools/literature/rphenograph-named-software-registry.json'
GUIDE='docs/literature/BATCHELOR129_INTEGRATION.md'
PATTERN=r'(?<![A-Za-z0-9_])Rphenograph(?![A-Za-z0-9_])'
REGISTRY_ROW=dict(id='rphenograph',name='Rphenograph',pattern=PATTERN,role='Graph clustering of FastMNN-corrected imaging mass cytometry measurements',current_coverage='missing',existing_source='',coverage_note='P040 reports Rphenograph 0.1.1 with k = 40. All four inspected official repository commits report 0.99.1 under Artistic-2.0. Preserve the reported literal; exact paper version and runtime remain unrecovered. No Rphenograph source was executed or implementation qualified.')
P040_ROLE='Selected original Batchelor 1.29.0 fastMNN source candidate for the reported method 1.29, on complete caller-prepared signed asinh-protein data with explicit batches and parameters. All17 successful complete synthetic models and metadata match independent originals; two original unnamed-weight failures are preserved. Exact author patch/call, Ki-67 handling and settings remain unknown. This operation does not perform Rphenograph clustering or manual cell annotation. Read FASTMNN129_WORKFLOW_DEVELOPMENT.md.'
P051_ROLE='Selected original Monocle3 1.3.7 align_cds calling Batchelor 1.20 reducedMNN, followed by explicit graph/root ordering. This is an explicit interpretation of the reported integration role; the paper function, settings and roots remain unrecovered. It is distinct from P040 fastMNN. Read MONOCLE137_WORKFLOW_DEVELOPMENT.md.'
PHENO_SCOPE='Reported literal Rphenograph 0.1.1 and k = 40 are retained. The inspected official source history has four commits, all reporting 0.99.1 and Artistic-2.0; none recovers 0.1.1. No Rphenograph source was executed or runtime qualified. FastMNN correction does not implement the subsequent graph clustering, marker-based lineage annotation, subclustering or visual refinement. Preserve the version discrepancy; do not silently substitute another version. Read BATCHELOR129_INTEGRATION.md and the retained PHENOGRAPH_SOURCE_REVIEW.md intake.'
WARNING="""
## Current Batchelor coverage, Rphenograph gap and required actions

**Batchelor now has two qualified selected upstream workflows.** P040 has original 1.29.0 fastMNN on complete prepared signed protein/log-expression matrices, with native Java factor/reconstruction checks. P051 retains the selected Monocle 1.3.7/Batchelor 1.20 reducedMNN workflow. These distinct operations earn one scoped upstream integration badge, not whole-package or paper-reproduction credit. Read [BATCHELOR129_INTEGRATION.md](../BATCHELOR129_INTEGRATION.md).

**Rphenograph remains missing, with a version discrepancy.** P040 reports 0.1.1 and k = 40 after FastMNN correction. All four inspected official repository commits report 0.99.1. The reported version remains unrecovered; no source was executed or runtime qualified. Marker annotation, subclustering and visual refinement include additional human decisions. One newly recorded missing tool offsets the Batchelor admission: 103 new scoped integrations, 245 missing tools, 407 named tools. Every previous 2481 observation and 1054 review remains unchanged.

**SKIP3: skip dependent analysis when the exact permitted Linux runtime, download, installation or execution is unavailable or forbidden.** Use the separately documented setup helper only where allowed, then supply its actual Rscript/library/pristine-source paths. The JAR contains no original R runtime. Analysis installs nothing. Windows saved-result audits need no R; original analysis needs permitted Linux/WSL with Linux Java and paths. Read [FASTMNN129_WORKFLOW_DEVELOPMENT.md](../FASTMNN129_WORKFLOW_DEVELOPMENT.md).

**FAIL2: no result is admitted on input, source, numeric, capacity, timeout or process failure.** Retain complete inputs, WARNINGS.md, status, original logs and any unadmitted RDS. Correct the named cause and retry a NEW output. Original unnamed numeric weights fail in both retained IRLBA and EXACT controls; changing only PCA backend did not fix them. A named batch_weights dictionary is an explicit different request and must never be inserted automatically.

**FACTORS preserves the complete original lazy model and checks every factor; it evaluates zero reconstructed entries.** ALL_VALUES additionally streams and checks every reconstructed value. Neither mode authenticates a producer, infers author settings or validates biology. The custom tested R4.5.3 source build is distinct from the recommended R4.6 development environment. The prior review notes and notices are retained as historical records. Paper-wide semantic review and the full 250-paper objective remain open; Cell Ranger remains excluded at the user's request.
"""
def notice():
 parts=[]
 for text in WARNING.strip().split('\n\n')[1:]:
  value=html.escape(text);value=re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',value,flags=re.S)
  value=re.sub(r'\[([^]]+\.md)\]\(\.\./[^)]+\)',lambda m:'<code>docs/literature/'+m[1]+'</code> in the repository',value)
  parts.append('<p>'+value+'</p>')
 return '<section id="batchelor129-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Current Batchelor / Rphenograph: warnings and required actions</h2>'+''.join(parts)+'</section>'
NOTICE=notice()

def additions(before):
 papers=json.loads(before[PAPERS]);p=next(p for p in papers if p['paper_id']=='P040')
 if any(t['tool_id']=='rphenograph' for t in p['tools']):raise ValueError('Rphenograph observation already exists')
 use=dict(tool_id='rphenograph',tool='Rphenograph',analysis_role=REGISTRY_ROW['role'],evidence_section='Methods / Cell-type annotation',evidence_paragraph_index=29,matched_text='Rphenograph',extraction_score=15,usage_context='imaging-protein-graph-clustering',review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name='0.1.1',evidence_selection='individually-reviewed-paragraph',evidence_paragraph_sha256='3464c1c1e537533ef0ed58c20b82001f90306bb8c04d1e07696c0f2d084633c6',use_summary='Clustered FastMNN-corrected imaging mass cytometry measurements with k = 40; followed by marker-based lineage annotation, subclustering and visual label refinement.',evidence_locator=p['fulltext_url']+' (section: Methods / Cell-type annotation)',implementation_scope_note=PHENO_SCOPE,supporting_evidence=[])
 review=dict(pmcid=p['pmcid'],doi=p['doi'],tool_id='rphenograph',**{k:copy.deepcopy(use[k]) for k in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence']})
 return use,review

def entries(root,before):
 original=next(t for t in json.loads(before[META[0]]) if t['id']=='batchelor');batchelor=copy.deepcopy(original)
 fast=WORKFLOW+'/fastmnn129-workflow-development.jar';prior='docs/literature/monocle137-workflow-development/monocle137-workflow-jdk21.jar'
 batchelor.update(current_coverage='upstream-adapter-validated',existing_source='src/main/java/org/jlinalg/cli/FastMnn129WorkflowCli.java',coverage_note='Two qualified selected original upstream workflows: P040 FastMNN 1.29.0 source candidate and P051 Monocle 1.3.7/Batchelor 1.20 reducedMNN interpretation. Exact paper settings/patches and whole-package validity remain unproven.',implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/FastMnn129WorkflowCli.java',implementation_scope='Scoped original upstream integration covering both reviewed selected roles: P040 complete prepared protein/log-expression single-matrix fastMNN using selected 1.29.0 source; P051 selected Monocle 1.3.7 align_cds/reducedMNN 1.20 integration. Complete original models/metadata and native factor checks are retained. This does not recover paper settings, implement Rphenograph/manual annotation or qualify every Batchelor function. Read BATCHELOR129_INTEGRATION.md.',validation_note='FastMNN: 19 actual original controls, 17 full successful models/metadata byte-for-byte equal to independent originals, two original failures preserved, 133 packaged checks, seven setup refusals and 128 complete public replay checks. Separate P051 workflow retains 64 genes/480 cells, 108 packaged checks and 240 disconnected infinite pseudotimes. Original R fitting and saved replay are distinct; whole-paper and biological validity remain unproven.',project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_adapter_cli='java -jar fastmnn129-workflow-development.jar --request request.json',development_candidate=fast,development_candidate_sha256=pin(root/fast)['sha256'],development_build='tools/literature/fastmnn129/build.py',development_candidates={'P040':dict(path=fast,**pin(root/fast)),'P051':dict(path=prior,**pin(root/prior))},source_role_coverage='batchelor129-role-coverage.json',workflow_validation='../fastmnn129-workflow-development/VALIDATION.json',covered_selected_roles=['P040: selected original 1.29.0 fastMNN prepared-matrix candidate','P051: explicitly selected reducedMNN interpretation'],uncovered_roles=[],new_scoped_integration_credit=1,paper_role_coverage={'P040':P040_ROLE,'P051':P051_ROLE})
 missing=dict(**REGISTRY_ROW,paper_count=1,papers=['P040'],implementation_status='missing',implementation_source='',implementation_scope=PHENO_SCOPE,validation_note='All 250 pinned main-article paragraph populations were searched for Rphenograph/PhenoGraph. Only P040 paragraph 29 matched; its source hash, reported 0.1.1 and k = 40 are retained. Four official Git history commits all report 0.99.1. This is source-use curation, not an executable/biological qualification.',source_roles_reviewed=1,new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False,paper_exact_unknown_settings_available=False)
 return batchelor,missing

CURRENT_README="""# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: 103 new scoped integrations**: 19 native Java subsets and 84 tested upstream adapters; 106 including 3 existing adapters. **245 named tools remain missing.** Cell Ranger remains excluded at the user's request. The full goal is active.

**Batchelor / FastMNN now has two qualified selected workflows.** P040 has original 1.29.0 fastMNN on complete prepared protein/log-expression data, with native Java factor/reconstruction checks. P051 retains the selected Monocle 1.3.7/Batchelor 1.20 reducedMNN integration. Read [BATCHELOR129_INTEGRATION.md](../BATCHELOR129_INTEGRATION.md) and [the FastMNN workflow guide](../FASTMNN129_WORKFLOW_DEVELOPMENT.md) for exact source candidates, setup, explicit method choices and limitations. The paper patches/calls/settings remain unknown; this is one scoped upstream admission, not whole-package or paper reproduction.

**Rphenograph is newly recorded and remains missing.** P040 reports 0.1.1 with k = 40 after correction; all four inspected official source commits report 0.99.1. Preserve the reported literal and discrepancy. No Rphenograph code was executed or qualified. Adding this missing tool offsets the Batchelor admission, so missing stays 245. Current use records retain every previous 2481 observation and 1054 review unchanged, then append one Rphenograph observation/review.

The fixed cohort contains **250 papers**: 71 Nature, 104 Nature Genetics and 75 AJHG articles from **2023-10-23 through 2026-10-23**. No time extension was needed. There are **407 named tools, 2,482 paper-tool observations and 1,055 reviewed uses**, with 378 main-article supporting pointers, four supplementary supporting pointers and one separately typed supplementary primary observation. Paper-wide semantic completeness is unproven.

**SKIP3 — skip dependent analysis if the exact permitted runtime, download, installation or execution cannot be supplied.** Setup and analysis are separate; use the documented helper only where allowed. Analysis installs nothing. Windows supports saved-result audits; original analysis requires permitted Linux/WSL and Linux paths.

**FAIL2 — no result is admitted on input, source, numeric, timeout, capacity or process failure.** Keep complete inputs, status, WARNINGS.md, original logs and any unadmitted RDS. Correct the named cause and retry a NEW output. Original unnamed-weight errors are preserved; a named batch_weights dictionary is an explicit different request. FACTORS checks all lazy model factors and zero reconstructed entries; ALL_VALUES additionally checks the full streamed reconstruction. Hashes establish integrity, not producer authentication.

Current records: [inventory](tool-inventory.json),[papers](papers.json),[summary](summary.json),[warnings](WARNINGS.md),[current selected Batchelor coverage](batchelor129-role-coverage.json). Verify this amendment with `python tools/literature/batchelor129_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-catalog-check`; add `--rescan-sources` to read all 250 exact cached XML sources. The generic historical generators below do not reproduce this cumulative admission and must not overwrite the current catalog.

<details>
<summary>Retained prior catalog checkpoints and notices (historical)</summary>

"""

def transition(before,new_entries):
 if set(before)!=set(META):raise ValueError('Only eight exact catalog/review paths may have predecessors')
 inventory=json.loads(before[META[0]]);summary=json.loads(before[META[1]]);papers=json.loads(before[PAPERS]);reviews=json.loads(before[REVIEWS])
 if len(papers)!=250 or sum(len(p['tools']) for p in papers)!=2481 or len(reviews['reviews'])!=1054 or len(inventory)!=406:raise ValueError('Unexpected predecessor populations')
 if any(t['id']=='rphenograph' for t in inventory):raise ValueError('Unexpected prior Rphenograph entry')
 batchelor,missing=new_entries;new=copy.deepcopy(inventory);index=next(i for i,t in enumerate(new) if t['id']=='batchelor');new[index]=copy.deepcopy(batchelor);new.append(copy.deepcopy(missing))
 use,review=additions(before);next(p for p in papers if p['paper_id']=='P040')['tools'].append(use);reviews['reviews'].append(review)
 updated=copy.deepcopy(summary);updated['paper_tool_observations']=2482;updated['distinct_named_tools']=407;updated['implementation_status_counts']=dict(Counter(t['implementation_status'] for t in new));expected=dict(summary['implementation_status_counts']);expected['upstream-adapter-validated']+=1
 if updated['implementation_status_counts']!=expected or expected['upstream-adapter-validated']!=84 or expected['missing']!=245:raise ValueError('Only one scoped upstream admission and one new missing tool are allowed')
 page=before[META[4]].decode('utf8');m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or page.count(PRIOR_NOTICE)!=1:raise ValueError('Unexpected predecessor rendered notice')
 embedded=json.loads(m[2])
 if embedded!={'papers':json.loads(before[PAPERS]),'tools':inventory,'summary':summary}:raise ValueError('Rendered predecessor differs')
 embedded.update(papers=papers,tools=new,summary=updated);raw=json.dumps(embedded,ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+raw+page[m.end(2):];page=page.replace(PRIOR_NOTICE,'',1)
 if page.count('<p id="overview"></p>')!=1:raise ValueError('Unexpected overview anchor')
 page=page.replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 old="${t.implementation_scope_note?'<p><strong>Scope for this paper:</strong> '+esc(t.implementation_scope_note)+'</p>':''}"
 replacement="${t.implementation_scope_note?(byId.get(t.tool_id).paper_role_coverage?.[p.paper_id]?'<p><strong>Current selected workflow:</strong> '+esc(byId.get(t.tool_id).paper_role_coverage[p.paper_id])+'</p><details class=\"source-review-history\"><summary>Historical source-review implementation note (retained)</summary>'+esc(t.implementation_scope_note)+'</details>':'<p><strong>Scope for this paper:</strong> '+esc(t.implementation_scope_note)+'</p>'):''}"
 if page.count(old)!=1:raise ValueError('Unexpected paper scope renderer')
 page=page.replace(old,replacement,1)
 singular_old='<small>(${t.paper_count} papers)</small>'
 singular_new="<small>(${t.paper_count} ${t.paper_count===1?'paper':'papers'})</small>"
 if page.count(singular_old)!=1:raise ValueError('Unexpected tool-card count label')
 page=page.replace(singular_old,singular_new,1)
 warnings=before[META[5]].decode('utf8')
 if warnings.count(PRIOR_NOTICE)!=1:raise ValueError('Unexpected prior warning notice')
 warnings=warnings.replace(PRIOR_NOTICE,'',1)
 marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warnings.count(marker)!=1:raise ValueError('Unexpected warning return link')
 warnings=warnings.replace(marker,marker+NOTICE,1)
 paper_raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_raw=paper_raw.replace(b'\n',b'\r\n')
 return {META[0]:encoded(new),META[1]:encoded(updated),META[2]:(CURRENT_README+before[META[2]].decode('utf8')+'\n</details>\n').encode('utf8'),META[3]:(WARNING+'\n<details>\n<summary>Retained prior warnings and source checkpoints (historical)</summary>\n\n').encode('utf8')+before[META[3]]+b'\n</details>\n',META[4]:page.encode('utf8'),META[5]:warnings.encode('utf8'),PAPERS:paper_raw,REVIEWS:encoded(reviews)}

def check_transition(before,after,new_entries):
 expected=transition(before,new_entries)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact Batchelor admission and one Rphenograph addition')
 return dict(added_tool_ids=['rphenograph'],admitted_tool_ids=['batchelor'],unchanged_observations=2481,unchanged_reviews=1054,observations=2482,reviewed_uses=1055,named_tools=407,new_scoped_integrations=103,missing=245,new_scoped_integration_credit=1,whole_tool_credit=0,whole_goal_complete=False)
