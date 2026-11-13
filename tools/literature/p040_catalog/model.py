# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append newly recognized software uses without rewriting prior source records."""
from pathlib import Path
from collections import Counter
import copy,html,json,re
from batchelor129_catalog.model import META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,rawpin,pin
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE='docs/literature/p040-software-catalog'
REGISTRY='tools/literature/p040-named-software-registry.json'
CURATION='tools/literature/p040-software-use-reviews.json'
GUIDE='docs/literature/P040_SOFTWARE_REVIEW.md'
PHENO='docs/literature/rphenograph099-workflow-development'
CANDIDATE_NOTE='A complete tested Rphenograph 0.99.1 candidate and native Java coefficients are now available separately. P040 reports 0.1.1 and k=40; that identity remains unresolved. The reported edition remains missing; no automatic substitution or new catalog credit.'
WARNING='''
## Current software census, candidate versions and required actions

**Current catalog: 250 papers, 429 named software families, 2,668 paper-tool observations and 1,079 reviewed uses.** Twenty-two names omitted by the old registry add186 source-anchored observations across the same 250 papers. Twenty-two P040 uses and two P082 author-inventory statements have individual reviews. The 162 other new summaries still require individual review. All 2,482 previous observations and 1,055 reviews remain unchanged. The full semantic review and 250-paper objective remain open.

**Coverage remains 103 new scoped integrations: 19 native Java and 84 upstream; 106 including 3 existing adapters. Missing rises to 267 because 22 previously omitted names are now visible.** This amendment awards no implementation badge. Fiji/ImageJ appears in 51 papers; GraphPad Prism in 46 and FlowJo in 27. Image processing, flow cytometry and statistical software are recorded with distinct source roles. Acquisition software requires the relevant instrument/vendor runtime; supplied downstream data can be analyzed using separately qualified tools.

**Rphenograph 0.99.1 is available as an explicit tested development candidate, while the reported 0.1.1 remains unresolved and missing.** The complete original RANN/Jaccard/Louvain workflow and native Java coefficients passed source controls, 86 packaged checks and eight installer refusals, with complete saved models and original failures retained. The source's k=1 error and empty/NaN graph are never repaired or admitted. Do not substitute versions or infer the paper's manual annotation decisions. Read P040_SOFTWARE_REVIEW.md and the versioned RPHENOGRAPH099_WORKFLOW_DEVELOPMENT.md qualification guide. Older guide/catalog totals are retained qualification snapshots; current totals are stated here.

**SKIP3: skip only dependent analysis if a required permitted runtime, download, installation, license or execution cannot be supplied.** Obtain the exact permitted provider and caller-prepared inputs; install only where allowed. No software installation or original analysis is performed by this catalog audit. Cell Ranger remains excluded by the user. A company disclosure of DeepCell, Inc. is not counted as software use.

**FAIL2: admit no result on source, input, numeric, timeout, capacity or process failures.** Keep complete inputs, WARNINGS.md, status, original logs and any unadmitted models. Correct the named cause and retry NEW output. Existing Batchelor/FastMNN workflows remain unchanged: FACTORS checks every factor and zero reconstructed entries; ALL_VALUES checks the complete streamed reconstruction. Original unnamed-weight failures remain preserved; named batch_weights is a distinct explicit request. Source hashes establish integrity, not producer authentication or biological correctness.
'''
def notice():
 parts=[]
 for paragraph in WARNING.strip().split('\n\n')[1:]:
  value=html.escape(paragraph);value=re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',value,flags=re.S);parts.append('<p>'+value+'</p>')
 return '<section id="p040-software-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Current software coverage: warnings and required actions</h2>'+''.join(parts)+'</section>'
NOTICE=notice()

def assets(root):
 return json.loads((root/EVIDENCE/'CENSUS.json').read_bytes()),json.loads((root/EVIDENCE/'ADDITIONS.json').read_bytes()),json.loads((root/REGISTRY).read_bytes())

def validate_assets(root,papers):
 census,add,definitions=assets(root);ids={r['id'] for r in definitions};by={p['paper_id']:p for p in papers}
 if len(definitions)!=22 or len(ids)!=22 or census['status']!='PASS_250_PINNED_PRIMARY_P040_SOFTWARE_NAME_CENSUS' or census['papers']!=250 or census['name_pairs']!=186 or census['matched_paragraphs']!=273 or len(census['census'])!=250 or len(census['matches'])!=273:raise ValueError('Incomplete new-software population')
 for old,row in zip(papers,census['census'],strict=True):
  if any(old[k]!=row[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Census paper identity differs')
 named={(m['paper_id'],m['tool_id'],m['paragraph_index'],m['paragraph_sha256'],m['section']) for m in census['matches']}
 if set(add)!= {'paper_tool_additions','reviews'} or set(add['paper_tool_additions'])!=set(by) or sum(map(len,add['paper_tool_additions'].values()))!=186 or len(add['reviews'])!=24 or add['reviews']!=json.loads((root/CURATION).read_bytes()):raise ValueError('Incomplete additions/reviews')
 pairs=set()
 for paper,rows in add['paper_tool_additions'].items():
  for row in rows:
   key=(paper,row['tool_id']);anchor=(*key,row['evidence_paragraph_index'],row['evidence_paragraph_sha256'],row['evidence_section'])
   if key in pairs or row['tool_id'] not in ids or anchor not in named:raise ValueError('Duplicate or unanchored observation')
   if 'evidence_text' in row or 'evidence_sentence' in row:raise ValueError('Full research text must not be published as catalog metadata')
   pairs.add(key)
 if len(pairs)!=186 or ('P250','deepcell_mesmer') in pairs or len(add['paper_tool_additions']['P040'])!=22:raise ValueError('Company/duplicate/P040 classification differs')
 seen=set();index={p['pmcid']:p for p in papers}
 for review in add['reviews']:
  paper=index[review['pmcid']];key=(paper['paper_id'],review['tool_id']);row=next((r for r in add['paper_tool_additions'][key[0]] if r['tool_id']==key[1]),None)
  if key in seen or row is None or review['doi']!=paper['doi'] or review['evidence_paragraph_sha256']!=row['evidence_paragraph_sha256']:raise ValueError('Duplicate or unanchored individual review')
  for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence']:
   if review[k]!=row[k]:raise ValueError('Review/observation differs: '+k)
  for support in review['supporting_evidence']:
   if (*key,support['paragraph_index'],support['paragraph_sha256'],support['section']) not in named:raise ValueError('Supporting source does not name this software')
  seen.add(key)
 if {p for p,t in seen}!={'P040','P082'} or sum(p=='P082' for p,t in seen)!=2:raise ValueError('Individual review scope differs')
 return census,add,definitions

def rphenograph_entry(root,old):
 row=copy.deepcopy(old);path='docs/literature/rphenograph099-workflow-development.jar'
 row.update(coverage_note=CANDIDATE_NOTE,implementation_scope='The literal paper edition 0.1.1 remains missing. A separately selected original 0.99.1 full clustering workflow and native Java coefficient implementation are qualified as development candidates. Exact paper identity/settings, manual annotation, subclustering and biological validity remain unresolved. Read P040_SOFTWARE_REVIEW.md; do not substitute editions.',validation_note='The 0.99.1 candidate has 863 complete original C++ coefficient cases / 105,921 rows on three platforms, five full original controls, four complete returned graph/community/component comparisons, 86 packaged checks, eight installer refusals, two private original source installations and 80 public saved replay checks. The original k=1 failure, empty/NaN graph and raw-model byte differences remain retained. This does not recover 0.1.1 or earn reported-edition coverage.',development_candidate_available=True,development_candidate=path,development_candidate_sha256=pin(root/path)['sha256'],development_build='tools/literature/rphenograph099/build.py',development_adapter_cli='java -jar rphenograph099-workflow-development.jar --request request.json (explicit version0.99.1 only)',native_candidate_component='src/main/java/org/jlinalg/singlecell/RphenographJaccard099.java',available_candidate_versions=['0.99.1'],reported_version_unresolved='0.1.1',paper_role_coverage={'P040':CANDIDATE_NOTE+' Original k=1 fails Not a matrix.; empty/nonfinite graphs are retained but unadmitted. Manual marker annotation remains separate.'})
 return row

def current_entries(root,before):
 papers=json.loads(before[PAPERS]);census,add,defs=validate_assets(root,papers);old=json.loads(before[META[0]]);pheno=rphenograph_entry(root,next(r for r in old if r['id']=='rphenograph'));new=[]
 for definition in defs:
  paper_ids=[p['paper_id'] for p in papers if any(t['tool_id']==definition['id'] for t in add['paper_tool_additions'][p['paper_id']])]
  new.append(dict(**definition,paper_count=len(paper_ids),papers=paper_ids,implementation_status='missing',implementation_source='',implementation_scope='Named source roles are catalogued; no original runtime or source-qualified implementation is established here. Acquisition/data-reuse roles and unresolved versions/functions remain explicit. Read P040_SOFTWARE_REVIEW.md.',validation_note='All 250 exact primary XML sources were searched for this name. Existing use records were preserved. New name detection is not executable, whole-tool, whole-paper or biological validation.',source_roles_reviewed=sum(r['tool_id']==definition['id'] for r in add['reviews']),new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False,paper_exact_unknown_settings_available=False))
 return pheno,new

CURRENT_README='''# 250-paper omics and GWAS toolbox catalog

**Current checkpoint: 429 named software families,2,668 observations,1,079 reviewed uses.** The fixed 250-paper cohort contains 71 Nature, 104 Nature Genetics and 75 AJHG papers from 2023-10-23 through 2026-10-23; no time extension was needed. P040's Methods review identified 22 omitted names; a full 250-source census adds 186 paper-tool observations. Twenty-two P040 uses and two P082 author-inventory statements have individual reviews;162 other new summaries require individual review. Every prior 2,482 observation and 1,055 review is unchanged. The full semantic review remains open.

**103 new scoped integrations remain available:** 19 native Java subsets and 84 tested upstream adapters; 106 including 3 existing adapters. **267 named tools remain missing**, including the 22 newly recognized names. No badge is awarded by a name census or source review. Cell Ranger remains excluded at the user's request.

**Rphenograph 0.99.1 is separately available as a tested development candidate.** It includes complete original RANN/Jaccard/Louvain execution and native Java coefficients, complete model retention and prominent diagnostics. The paper reports0.1.1; that identity remains unresolved and missing. No automatic edition substitution, paper reproduction or manual annotation is claimed. Read [P040_SOFTWARE_REVIEW.md](../P040_SOFTWARE_REVIEW.md) for current source roles and candidate status. Component qualification guides and previous catalog checkpoints are retained versioned snapshots; current totals are stated on this page.

**SKIP3 skips only dependent analyses** if a required permitted runtime, license, download, installation or execution is unavailable or forbidden. Supply the documented exact provider on a permitted host. **FAIL2 admits no result:** retain complete inputs, WARNINGS.md, status, logs and unadmitted models; correct the named cause and retry NEW output. Acquisition-software roles require the corresponding instrument/vendor runtime; separately qualified downstream tools can use supplied data. No original analysis or software installation occurs during this catalog audit.

Current records: [inventory](tool-inventory.json), [papers](papers.json), [summary](summary.json), [warnings](WARNINGS.md). Validate with `python tools/literature/p040_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-p040-check`; add `--rescan-sources` to read all 250 exact cached XML sources. Historical generators must not overwrite this cumulative catalog. The full 250-paper implementation objective remains active.

<details>
<summary>Retained previous catalog checkpoints (historical)</summary>

'''

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Only eight catalog/review metadata paths may have predecessors')
 old=json.loads(before[META[0]]);summary=json.loads(before[META[1]]);papers=json.loads(before[PAPERS]);reviews=json.loads(before[REVIEWS])
 if len(old)!=407 or len(papers)!=250 or sum(len(p['tools']) for p in papers)!=2482 or len(reviews['reviews'])!=1055:raise ValueError('Unexpected predecessor population')
 census,add,defs=validate_assets(root,papers);ids={r['id'] for r in defs}
 if ids&{r['id'] for r in old}:raise ValueError('A newly recognized name already exists')
 pheno,new=current_entries(root,before);inventory=[copy.deepcopy(pheno if r['id']=='rphenograph' else r) for r in old]+new
 for p in papers:p['tools'].extend(copy.deepcopy(add['paper_tool_additions'][p['paper_id']]))
 reviews['reviews'].extend(copy.deepcopy(add['reviews']));updated=copy.deepcopy(summary);updated['paper_tool_observations']=2668;updated['distinct_named_tools']=429;updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory));expected=dict(summary['implementation_status_counts']);expected['missing']+=22
 if updated['implementation_status_counts']!=expected or expected['missing']!=267 or expected['upstream-adapter-validated']!=84 or expected['native-subset-validated']!=19:raise ValueError('No new scoped integration or unrelated status change is allowed')
 page=before[META[4]].decode('utf8');m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or json.loads(m[2])!={'papers':json.loads(before[PAPERS]),'tools':old,'summary':summary} or page.count(PRIOR_NOTICE)!=1:raise ValueError('Unexpected predecessor rendered data/notice')
 embedded=json.dumps({'papers':papers,'tools':inventory,'summary':updated},ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+embedded+page[m.end(2):];page=page.replace(PRIOR_NOTICE,'',1)
 if page.count('<p id="overview"></p>')!=1:raise ValueError('Unexpected overview anchor')
 page=page.replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warnings=before[META[5]].decode('utf8');marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warnings.count(PRIOR_NOTICE)!=1 or warnings.count(marker)!=1:raise ValueError('Unexpected warning notice/return link')
 warnings=warnings.replace(PRIOR_NOTICE,'',1).replace(marker,marker+NOTICE,1)
 paper_raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_raw=paper_raw.replace(b'\n',b'\r\n')
 return {META[0]:encoded(inventory),META[1]:encoded(updated),META[2]:(CURRENT_README+before[META[2]].decode('utf8')+'\n</details>\n').encode('utf8'),META[3]:(WARNING+'\n<details>\n<summary>Retained previous warnings and checkpoints (historical)</summary>\n\n').encode('utf8')+before[META[3]]+b'\n</details>\n',META[4]:page.encode('utf8'),META[5]:warnings.encode('utf8'),PAPERS:paper_raw,REVIEWS:encoded(reviews)}

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact new-software append and Rphenograph candidate metadata update')
 return dict(unchanged_previous_observations=2482,unchanged_previous_reviews=1055,added_observations=186,added_reviews=24,added_missing_software_names=22,papers=250,named_tools=429,observations=2668,reviewed_uses=1079,new_scoped_integrations=103,missing=267,new_scoped_integration_credit=0,whole_goal_complete=False)
