# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one original ggpubr family and eight source reviews; retain every earlier record."""
from collections import Counter
import copy,html,json,re
from rstatix_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,rawpin,pin
from ggpubr_catalog.source import CURATION,INTAKE,EXPECTED,reviews
EVIDENCE='docs/literature/ggpubr-catalog-integration'
GUIDE='docs/literature/GGPUBR_CATALOG_INTEGRATION.md'
PROOF=DATA+'/ggpubr-catalog-validation.json'
COMPATIBILITY=DATA+'/ggpubr-reviews-shared-source-compatibility.json'
COVERAGE=DATA+'/ggpubr040060-role-coverage.json'
WARNING='''
## Current ggpubr coverage: warnings and required actions

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,156 individually reviewed uses; 1,515 uses await individual review.** All 250 exact primary XMLs were searched for ggpubr: nine paper-family uses and 12 named paragraphs. Eight uses gain source reviews; P040's existing observation and review remain unchanged. P241's Web resources link is not another analysis execution. The fixed cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23; no time extension was needed. Full semantic review and implementation remain open.

**109 new scoped integrations are available: 20 native Java subsets and 89 upstream workflows; 112 including 3 existing adapters. 264 named families remain missing.** ggpubr gains one upstream family credit for two separate original editions, 0.4.0 and 0.6.0. The Python source entry point runs the complete installed original namespace through the matching private Linux/WSL R 4.3.3 provider, original ggplot2 3.4.3 and original rstatix 0.7.2. It is not a standalone JAR or a new native Java ggpubr implementation. Canonical executable and root dispatch remain unchanged. Read GGPUBR_CATALOG_INTEGRATION.md and GGPUBR_WORKFLOW_DEVELOPMENT.md for exact commands and limits; earlier component guides retain pre-admission snapshots.

**Paper roles, versions and grouping remain literal.** P040/P054 report 0.6.0; P098/P101/P195 report 0.4.0; P058/P087/P231/P241 editions are unreported. Never infer a specific ggpubr function, plot, correction or figure assignment from a generic package list. P098 establishes violin plots; its nearby Wilcoxon statement does not name the implementing package. P101 explicitly assigns tests to base R/agricolae, not automatically to ggpubr. P195's nearby Mann-Whitney test is not specifically assigned to ggpubr. The selected R/dependency profile does not establish historical author-runtime equivalence. Pairing order, variance assumptions, comparison families, directions, label positions and complete scientific populations remain caller decisions. Coloring scatter points alone does not establish a group mapping for stat_cor. No paper reproduction is claimed.

**SKIP3 skips dependent analysis** when required source, runtime, provider, input, literal export/package, permitted download, installation or execution is unavailable/forbidden. Install only where permitted, supply exact prerequisites and retry NEW paths. Use a persistent permitted Linux/WSL installation directory; temporary providers can disappear between invocations. Windows original execution skips. Select the literal edition and matching provider; unsupported request editions and provider contradictions refuse admission. A READY provider is not an analysis result. Trusted R scripts require explicit ALLOW execution/trusted-code policies and run with account permissions, not an OS sandbox or total CPU/RSS containment guarantee. Owned-group supervision excludes child-created separate sessions. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Preserve exact inputs, every complete/partial output, full original plot/built/grob objects, all layer rows/columns, tables, conditions/errors/warnings, logs, manifest and status. Correct the named cause and retry NEW paths. Original ggplot Computation failed in warnings refuse admission even when direct R renders files and exits zero. Silent empty layers still require caller eligibility and expected-row checks; do not invent a correlation or p-value. Ordinary warnings remain visible and require review; original warn=2 still fails. Unknown condition totals from interrupted or truncated worker diagnostics are unavailable, not zero; retain the raw record and recovery report. Missing/extra/truncated files, early quit, changed inputs, symlinks and capacity violations refuse admission. Do not drop failed cases, repair source algorithms or label partial populations complete. File/marker checks do not independently prove arbitrary R-code or biological validity.

**Qualification retains full original evidence.** Each edition matched 79 direct-original cases: 54 plots, 25 tables, 416 table cells, 171,084 layer cells and all 134 SVG/PNG/TSV files, with 294 complete result files. All 72 Linux workflow controls, 2 Windows skips, 16 Linux setup refusals, 2 Windows setup skips, two complete public examples and four frozen-verifier refusal tests are retained. Full original RDS objects and construction PDFs remain available; opaque environment-pointer/RDS-byte parity and PDF clock-metadata parity are not claimed. Exact negative-test ZIP bytes are retained losslessly using the unchanged base archive plus deltas. Hashes establish integrity, not authenticity or whole-paper validity. This catalog gate executes no new original analysis or installation.

**Other retained restrictions still apply.** rstatix 0.7.2 uses a separate Linux/WSL R 4.5.3 profile; P054's literal wilcoxon_test is not silently repaired to wilcox_test, and native one-way Levene does not supply the full R workflow. QuPath 0.5.1 H-DAB controls do not independently qualify other editions, fluorescence/proximity, RNAscope, registration or custom StarDist models. P204's reported AllelicSeries 1.1.2 remains unresolved; candidate 0.1.1.2 earns no paper-edition credit. P054's wesanderson palette/count/MFOD1 mapping remain unknown. ImageJ plugins/distributions remain separate, and histogram matching is not an intensity-quantification method. Consult retained tool guides for all earlier limitations.
'''
def notice():
 paragraphs=['<p>'+re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',html.escape(p),flags=re.S)+'</p>' for p in WARNING.strip().split('\n\n')[1:]]
 return '<section id="ggpubr-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Current toolbox coverage: warnings and required actions</h2>'+''.join(paragraphs)+'</section>'
NOTICE=notice()
COMMON_SCOPE='Original ggpubr 0.4.0 and 0.6.0 require separate matching private Linux/WSL R 4.3.3/ggplot2 3.4.3/rstatix 0.7.2 providers and the Python source entry point, not a standalone JAR. Explicit caller grouping, statistical eligibility, complete populations and trusted R code remain required. Historical author-runtime equivalence and paper reproduction are unproven.'
ROLE_LIMITS={
 'P040':'Reported 0.6.0: stat_compare_means for Wilcoxon signed-rank and unpaired t-tests, stat_cor for Pearson correlation. Author groups, pairing order, variance assumptions and correction families remain unresolved. rstatix IMC tests, GeomxTools LMMs and GraphPad analyses are separate. Existing observation and review are unchanged.',
 'P054':'Reported 0.6.0 among plotting packages. No specific ggpubr function, statistical annotation, palette, layout or per-figure assignment is established. Reported R 4.2.3 to4.4.0 does not prove the selected R 4.3.3/dependency profile; Illustrator/Inkscape composition and other plotting libraries remain separate.',
 'P058':'Edition unreported. Three distinct named contexts cover QuPath fluorescence, manual RNA-ISH puncta and CellPose soma/SPP1 measurements. Wilcoxon export, rank-sum versus signed-rank, pairing, groups and families remain unresolved; three paragraphs are not three proven command runs. Acquisition/segmentation remain separate.',
 'P087':'Edition unreported: stat_compare_means for unpaired two-sample t comparisons of per-patient cell abundance. Variance assumptions, adjustment families and exact patient/lineage comparisons remain author inputs. MiloR, Seurat/MAST and separate rstatix bulk analyses remain separate.',
 'P098':'Reported 0.4.0: violin plots of promoter-mark enrichment across PDX subgroups. The nearby two-tailed Wilcoxon statement does not identify its implementing package; no ggpubr test or R version is inferred from that paragraph. UCSC bigWig extraction/aggregation remain separate.',
 'P101':'Reported 0.4.0 in a general plot/statistics library list. Wilcox/correlation tests explicitly assigned to base R and LSD/Bonferroni to agricolae LSD.test are not reassigned to ggpubr. No exact ggpubr operation is identified; reported R 4.1.0 differs from selected R 4.3.3.',
 'P195':'Reported 0.4.0 as a visualization library, with R 4.0.3 reported. A nearby Mann-Whitney statement does not name the implementing package. PRDM6 array reuse, NMF, RobusTAD and Hi-C analyses are separate; no exact plot or test is inferred.',
 'P231':'Edition unreported in a visualization/manipulation package list with R 4.4.0. No individual plot, operation, figure assignment or statistical test is inferred; other data tools remain separate.',
 'P241':'Edition unreported: plots generated with ggplot2 or ggpubr, with no per-figure assignment or exact function. Paragraph111 is a Web resources link, not another execution; rstatix statistics remain separate.'}
def entry(root,old):
 if old['id']!='ggpubr' or old['implementation_status']!='missing' or old['paper_count']!=9 or old['papers']!=list(EXPECTED):raise ValueError('Unexpected predecessor ggpubr family')
 result=copy.deepcopy(old);program='tools/literature/ggpubr_workflow/run.py'
 result.update(current_coverage='upstream-adapter-validated',existing_source=program,
  coverage_note='Separate unchanged original ggpubr 0.4.0/0.6.0 trusted-script workflows with private Linux/WSL R 4.3.3 providers. Exact source roles, edition/design gaps, failed statistical layers, empty-layer eligibility and full diagnostics remain explicit.',
  implementation_status='upstream-adapter-validated',implementation_source=program,
  implementation_scope='Explicit Python source entry point, not a standalone JAR: complete installed original ggpubr 0.4.0 and 0.6.0 namespaces with separate matching private Linux/WSL R 4.3.3, original ggplot2 3.4.3 and rstatix 0.7.2 providers. Full caller formulas, group mappings and options stay in unchanged original R. Retain every declared plot/table/layer/object and all partial failures. Known statistical-layer failure warnings refuse admission; silent empty layers require explicit caller eligibility checks. Unknown author editions/settings, generic mentions and historical runtimes are not filled in by examples. Read GGPUBR_CATALOG_INTEGRATION.md. One scoped upstream family credit for both editions; no whole-tool or paper reproduction.',
  validation_note='Per edition: all79 original cases matched direct original R, including54 plots, 25 tables, 416 table cells, 171,084 layer cells, full portable labels/layouts/conditions/RNG states and all 134 SVG/PNG/TSV files;294 result files retained. Original full plot/built/grob RDS and construction PDFs remain unchanged.72 Linux workflow controls, 2 Windows skips, 16 Linux setup refusals, 2 Windows setup skips, two complete public examples,11,513 base evidence members and59 auxiliary members are retained. Four frozen-verifier refusals and exact lossless negative-ZIP reconstruction passed.109/122 exports,281/329 direct functions and 12/15 ggpubr ggproto objects were source-checked. Earlier grouping/device harness, temporary-provider and interrupted-diagnostic failures are retained. No opaque environment-pointer/RDS-byte or construction-PDF clock parity is claimed.',
  source_roles_reviewed=9,new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False,paper_exact_unknown_settings_available=False,
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=program,
  development_candidate_sha256=pin(root/program)['sha256'],development_candidate_kind='python-source-entrypoint-requires-qualified-original-R-provider',
  development_build='tools/literature/ggpubr/provider.py',development_adapter_cli='python3 tools/literature/ggpubr_workflow/run.py --request caller-request.json',
  selected_upstream_versions=['0.4.0','0.6.0'],selected_original_R_version='4.3.3',selected_dependency_versions={'ggplot2':'3.4.3','rstatix':'0.7.2'},
  workflow_validation='../ggpubr-workflow-development/VALIDATION.json',source_role_coverage='ggpubr040060-role-coverage.json',
  paper_role_coverage={r['paper_id']:r['use_summary']+' '+ROLE_LIMITS[r['paper_id']]+' '+COMMON_SCOPE for r in reviews(root)},
  uncovered_roles=['Other or unreported author editions and historical R/dependency builds','Unknown author groups, pairing, variance assumptions, direction, correction families and exact plots',
   'Generic package listings and ggplot2-or-ggpubr statements do not identify an exact function or figure','Caller statistical eligibility, missing/nonfinite input handling and complete scientific populations',
   'Author preprocessing, imaging/segmentation, data-reuse analyses, biological validation and paper reproduction'])
 return result

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Exactly eight predecessor metadata paths required')
 inventory=json.loads(before[META[0]]);papers=json.loads(before[PAPERS]);document=json.loads(before[REVIEWS]);summary=json.loads(before[META[1]])
 if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(432,250,2671,1148):raise ValueError('Unexpected predecessor catalog population')
 index=next(i for i,t in enumerate(inventory) if t['id']=='ggpubr');inventory[index]=entry(root,inventory[index])
 known={(r['pmcid'],r['tool_id']) for r in document['reviews']}
 if len(known)!=1148:raise ValueError('Duplicate previous individual review')
 if {p['paper_id'] for p in papers for t in p['tools'] if t['tool_id']=='ggpubr'}!=set(EXPECTED):raise ValueError('Previous ggpubr paper-family population differs')
 for review in reviews(root):
  paper=next(p for p in papers if p['paper_id']==review['paper_id']);indices=[i for i,t in enumerate(paper['tools']) if t['tool_id']=='ggpubr']
  if len(indices)!=1:raise ValueError('Duplicate/missing ggpubr observation')
  if review['paper_id']=='P040':
   if (paper['pmcid'],'ggpubr') not in known:raise ValueError('Existing P040 review missing')
   continue
  if (paper['pmcid'],'ggpubr') in known:raise ValueError('Unexpected already-reviewed ggpubr use')
  old=paper['tools'][indices[0]];row=copy.deepcopy(old);row['source_extraction_before_review']=copy.deepcopy(old);anchor=review['supporting_evidence'][0]
  row.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],evidence_paragraph_sha256=anchor['paragraph_sha256'],
   evidence_selection='individually-reviewed-explicit-primary-analysis-anchor',review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',
   evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+paper['pmcid']+'/ (section: '+anchor['section']+')',
   **{k:copy.deepcopy(review[k]) for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence','named_anchor_classification']})
  paper['tools'][indices[0]]=row;appended=copy.deepcopy(review);appended.pop('paper_id');document['reviews'].append(appended)
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory))
 counts=dict(summary['implementation_status_counts']);counts['missing']-=1;counts['upstream-adapter-validated']+=1
 if updated['implementation_status_counts']!=counts or (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])!=(20,89,264):raise ValueError('Unexpected integration credit')
 page=before[META[4]].decode('utf8');match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Predecessor rendered data differs')
 page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
 historical='<details><summary>Previous rstatix checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 if page.count(PRIOR_NOTICE)!=1 or page.count('<p id="overview"></p>')!=1:raise ValueError('Predecessor notice/overview differs')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warnings=before[META[5]].decode('utf8');marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warnings.count(PRIOR_NOTICE)!=1 or warnings.count(marker)!=1:raise ValueError('Predecessor warning notice differs')
 warnings=warnings.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1)
 raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [ggpubr source roles, original workflow commands and recovery](../GGPUBR_CATALOG_INTEGRATION.md). Validate with `python tools/literature/ggpubr_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-ggpubr-catalog-check --rescan-sources`. The full semantic review and implementation objective remain open.\n\n'
 return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:raw,REVIEWS:encoded(document),
  META[2]:(intro+'<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n'+before[META[2]].decode('utf8')+'\n</details>\n').encode('utf8'),
  META[3]:(WARNING+'\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode('utf8')+before[META[3]]+b'\n</details>\n',META[4]:page.encode('utf8'),META[5]:warnings.encode('utf8')}

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact one-upstream-workflow and eight-review admission')
 return dict(papers=250,named_tools=432,observations=2671,reviewed_uses=1156,unreviewed_uses=1515,new_scoped_integrations=109,missing=264,
  unchanged_previous_families=431,unchanged_previous_observations=2663,unchanged_previous_reviews=1148,enriched_existing_observations=8,
  added_observations=0,added_reviews=8,new_scoped_integration_credit=1,native_family_credit_added=0,existing_P040_review_unchanged=True,whole_goal_complete=False)
