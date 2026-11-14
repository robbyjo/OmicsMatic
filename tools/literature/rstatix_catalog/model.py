# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one original workflow and five old-use reviews; retain P040 and all other records."""
from collections import Counter
import copy,html,json,re
from qupath_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,rawpin,pin
from rstatix_catalog.source import CURATION,INTAKE,EXPECTED,reviews

EVIDENCE='docs/literature/rstatix-catalog-integration'
GUIDE='docs/literature/RSTATIX_CATALOG_INTEGRATION.md'
PROOF=DATA+'/rstatix-catalog-validation.json'
COMPATIBILITY=DATA+'/rstatix-reviews-shared-source-compatibility.json'
COVERAGE=DATA+'/rstatix072-role-coverage.json'
WARNING='''
## Current rstatix coverage: warnings and required actions

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,148 individually reviewed uses; 1,523 uses await individual review.** All 250 exact primary XMLs were searched for rstatix: six paper-family uses and 12 named paragraphs. Five uses gain source reviews; P040's existing observation and review remain unchanged. P241's Web resources link is not another analysis execution. The fixed cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23; no time extension was needed. The full semantic review and implementation objective remain open.

**108 new scoped integrations are available: 20 native Java subsets and 88 upstream workflows; 111 including 3 existing adapters. 265 named families remain missing.** rstatix gains one upstream workflow credit. Its explicit Python source entry point executes the complete unchanged original rstatix 0.7.2 namespace through a permitted private Linux/WSL R 4.5.3 provider and trusted caller R script. It is not a standalone JAR. The separate native Java one-way Levene API/executable requires no R; it does not supply the other rstatix functions or earn a second family credit. Canonical executable and root dispatch remain unchanged. Read RSTATIX_CATALOG_INTEGRATION.md and RSTATIX072_WORKFLOW_DEVELOPMENT.md for commands, source roles and recovery. Earlier component guides retain their pre-admission snapshots.

**Original-runtime and paper settings remain explicit.** Five papers report rstatix 0.7.2; P241's edition/test/design are unreported. Selected R 4.5.3/car 3.1-5 does not prove equivalence to historical author runtimes, including P054's R 4.2.3 to 4.4.0. P054 literally prints wilcoxon_test; original 0.7.2 exports wilcox_test. Missing literal exports skip dependent analysis; no spelling repair is inferred. Unknown correction families, variance assumptions, pairing order, comparison groups, one-sided direction and group-versus-all dependence must be supplied or resolved. P032's test/correction method is unknown. Original p-value rounding, adjustment, conditions and full RDS objects are preserved; generic native tests are not substituted. Native Levene only covers prepared one-way mean/median-centered vectors, not arbitrary R formulas, grouped operations or custom center functions. No paper is reproduced by this admission.

**SKIP3 skips dependent analysis** when required source, Java/R runtime, provider, input, download, installation, literal export/package or execution is missing/forbidden. Install only where permitted, supply the named prerequisite and retry NEW paths. A READY provider or source entry point is not an analysis result. The original workflow requires explicit ALLOW execution/trusted-code policies. Trusted R scripts run with account permissions, not an OS sandbox or total CPU/RSS containment guarantee; same-group worker supervision does not cover child-created separate sessions. Windows original execution skips; use the qualified Linux/WSL provider. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Retain exact inputs, every complete/partial result, original R object/error/warning, full logs, conditions, manifest and status; correct the named cause and retry NEW paths. Review every original warning in WARNINGS.md, diagnostics/conditions.json, full condition RDS and process logs. Warnings are not muffled; warn=2 still fails. Exit zero after early quit, missing/extra/truncated results, input changes, symlinks and capacity violations refuse admission. Do not drop failed cases or label partial populations complete. Scripts must propagate scientific failures and check their own objects before declaring completion; file/marker checks cannot infer validity of arbitrary R objects, nonfinite values or designs. Native Levene refuses unresolved deviation variance, while retaining the original near-perfect-fit warning and huge statistic in control evidence. Hashes establish integrity, not authenticity, historical runtime equivalence or biological validity. This catalog audit executes no new original analysis or installation.

**Other retained limitations still apply.** QuPath 0.5.1 H-DAB controls do not independently qualify fluorescence/proximity, RNAscope, registration, other editions or custom StarDist models. P204's reported AllelicSeries 1.1.2 remains missing; candidate 0.1.1.2 earns zero credit, and native burden components do not supply full COAST/SKAT-O or binary/discrete calibration. P054's wesanderson palette/count/MFOD1 mapping remain unknown; viridisLite/R graphics are separate. ImageJ distributions/plugins are separate, and histogram matching must not be used for intensity quantification. Consult retained checkpoint guides for earlier tool-specific restrictions.
'''

def notice():
 paragraphs=['<p>'+re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',html.escape(p),flags=re.S)+'</p>' for p in WARNING.strip().split('\n\n')[1:]]
 return '<section id="rstatix-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Current toolbox coverage: warnings and required actions</h2>'+''.join(paragraphs)+'</section>'
NOTICE=notice()

COMMON_SCOPE='Selected original rstatix 0.7.2 is available through the explicit Python script entry point and private Linux/WSL R 4.5.3 provider; it is not a standalone JAR. Caller design, parameters, complete population and trusted script remain explicit. Historical author-R/dependency equivalence and paper reproduction are unproven.'
ROLE_LIMITS={
 'P032':'Reported 0.7.2. The test, correction method and comparison family for adjusted GSVA-score p-values remain unknown; no specific t/Wilcoxon test or Holm/BH choice is inferred. DESeq2 transformations, GSVA and plotting remain separate.',
 'P040':'Reported 0.7.2. The source establishes two-sided t-tests of IMC cell abundances, but exact comparison grouping, variance assumption and pairing remain unresolved. Do not infer Welch/equal variance or author design from the example. ggpubr, GeomxTools and GraphPad remain separate; the existing individual review is unchanged.',
 'P054':'Reported 0.7.2; all six analysis paragraphs are retained. The paper literally prints wilcoxon_test, but selected 0.7.2 exports wilcox_test; the literal missing export produces SKIP3 and no spelling repair is inferred. Pairing order, correction families, comparison groups, one-sided direction and group-versus-all dependence remain author inputs. Reported R 4.2.3 to 4.4.0 differs from selected R 4.5.3/car 3.1-5. The original engine handles grouped tests and custom R centers; native Java Levene only handles prepared one-way mean/median-centered vectors. Original rounding/adjustment behavior is retained.',
 'P087':'Reported 0.7.2. Original wilcox_test/BH is available for explicit ARLD/PBC OLR1 CPM comparisons; dataset-specific groups, adjustment family and exact/tie/correction options remain unresolved. stats Spearman tests, ggpubr abundance tests, MiloR and Seurat/MAST are separate. No normalization, sample exclusion or author-R equivalence is inferred.',
 'P098':'The primary paragraph explicitly reports 0.7.2 despite the earlier extractor missing it. Original t_test/Holm is available for supplied VST-normalized expression; variance assumption, exact comparisons and adjustment family remain author inputs. DESeq2 transformations/Wald/shrinkage, signature Wilcoxon tests and plotting remain separate; no raw-count or author-design analysis is inferred.',
 'P241':'Edition, test, design, settings and comparison family are unreported. Selected 0.7.2 is an explicit caller choice, not a recovered author runtime. Paragraph 113 is a Web resources link, not another execution. Do not infer a particular statistical test from this generic mention.'}

def entry(root,old):
 if old['id']!='rstatix' or old['implementation_status']!='missing' or old['paper_count']!=6 or old['papers']!=list(EXPECTED):raise ValueError('Unexpected predecessor rstatix family')
 result=copy.deepcopy(old);program='tools/literature/rstatix072_workflow/run.py';native='docs/literature/rstatix072-development-checkpoint/levene-native-windows21-development.jar'
 result.update(current_coverage='upstream-adapter-validated',existing_source=program,
  coverage_note='Unchanged original rstatix 0.7.2 trusted-script workflow with complete result/condition capture and a private Linux/WSL R4.5.3 provider. Native one-way Levene is separate. Unknown author designs, correction families, literal function spelling and historical runtime equivalence remain explicit.',
  implementation_status='upstream-adapter-validated',implementation_source=program,
  implementation_scope='Explicit Python source entry point, not a standalone JAR: complete unchanged original rstatix 0.7.2 namespace through a private Linux/WSL R 4.5.3 provider and trusted caller script. Full formulas/tidy grouping/caller arguments stay in original R; all declared outputs, conditions and failures are retained. Separate native Java Levene handles prepared one-way mean/median-centered vectors only. Original p-value rounding/adjustment remains literal; historical R/dependency, author designs/families and P054 wilcoxon_test spelling gaps are not resolved by examples. Read RSTATIX_CATALOG_INTEGRATION.md. One scoped upstream credit; no whole-tool or paper reproduction.',
  validation_note='All 248 original calls matched independent direct original R: complete returned objects/attributes/arguments/conditions/caller RNG and all 250 scientific result files, covering 444 rows and 5,664 cells. 31 Linux protocols and one Windows skip, public seven-operation example, all 2,411 workflow and 3,852 native/provider evidence members, and eight frozen-verifier refusals are retained. Native Levene: 196 computed cases/784 original scalars, ten refusals,29 API and six CLI checks on each Windows Java21/25 and Linux Java21 profile. Near-perfect-fit warnings/huge original statistics and earlier harness failures remain retained.',
  source_roles_reviewed=6,new_scoped_integration_credit=1,whole_tool_qualified=False,whole_paper_reproduced=False,paper_exact_unknown_settings_available=False,
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,development_candidate=program,
  development_candidate_sha256=pin(root/program)['sha256'],development_candidate_kind='python-source-entrypoint-requires-qualified-original-R-provider',
  development_build='tools/literature/rstatix072/provider.py',development_adapter_cli='python3 tools/literature/rstatix072_workflow/run.py --request caller-request.json',
  native_candidate_component='src/main/java/org/jlinalg/stats/LeveneTest.java',native_candidate_executable=native,native_candidate_sha256=pin(root/native)['sha256'],
  selected_upstream_version='0.7.2',selected_original_R_version='4.5.3',native_family_credit_added=0,
  workflow_validation='../rstatix072-workflow-development/VALIDATION.json',source_role_coverage='rstatix072-role-coverage.json',
  paper_role_coverage={r['paper_id']:r['use_summary']+' '+ROLE_LIMITS[r['paper_id']]+' '+COMMON_SCOPE for r in reviews(root)},
  uncovered_roles=['Unknown author comparisons, correction families, variance assumptions, pairing order and one-sided direction',
   'Literal printed wilcoxon_test versus exported wilcox_test; no spelling repair',
   'Historical author-R/dependency equivalence and unreported P241 edition/design',
   'Native general R formulas, grouped/custom-center Levene and other original tests',
   'Author preprocessing, exact scripts, biological validation and paper reproduction'])
 return result

def transition(root,before):
 if set(before)!=set(META):raise ValueError('Exactly eight predecessor metadata paths required')
 inventory=json.loads(before[META[0]]);papers=json.loads(before[PAPERS]);document=json.loads(before[REVIEWS]);summary=json.loads(before[META[1]])
 if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(432,250,2671,1143):raise ValueError('Unexpected predecessor catalog population')
 index=next(i for i,t in enumerate(inventory) if t['id']=='rstatix');inventory[index]=entry(root,inventory[index])
 known={(r['pmcid'],r['tool_id']) for r in document['reviews']}
 if len(known)!=1143:raise ValueError('Duplicate previous individual review')
 if {p['paper_id'] for p in papers for t in p['tools'] if t['tool_id']=='rstatix'}!=set(EXPECTED):raise ValueError('Previous rstatix paper-family population differs')
 for review in reviews(root):
  paper=next(p for p in papers if p['paper_id']==review['paper_id']);indices=[i for i,t in enumerate(paper['tools']) if t['tool_id']=='rstatix']
  if len(indices)!=1:raise ValueError('Duplicate/missing rstatix observation')
  if review['paper_id']=='P040':
   if (paper['pmcid'],'rstatix') not in known:raise ValueError('Existing P040 review missing')
   continue
  if (paper['pmcid'],'rstatix') in known:raise ValueError('Unexpected already-reviewed rstatix use')
  old=paper['tools'][indices[0]];row=copy.deepcopy(old);row['source_extraction_before_review']=copy.deepcopy(old);anchor=review['supporting_evidence'][0]
  row.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],evidence_paragraph_sha256=anchor['paragraph_sha256'],
   evidence_selection='individually-reviewed-explicit-primary-analysis-anchor',review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',
   evidence_locator='https://pmc.ncbi.nlm.nih.gov/articles/'+paper['pmcid']+'/ (section: '+anchor['section']+')',
   **{k:copy.deepcopy(review[k]) for k in ['use_summary','implementation_scope_note','version_reported_near_name','usage_context','supporting_evidence','named_anchor_classification']})
  paper['tools'][indices[0]]=row;appended=copy.deepcopy(review);appended.pop('paper_id');document['reviews'].append(appended)
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory))
 counts=dict(summary['implementation_status_counts']);counts['missing']-=1;counts['upstream-adapter-validated']+=1
 if updated['implementation_status_counts']!=counts or (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])!=(20,88,265):raise ValueError('Unexpected integration credit')
 page=before[META[4]].decode('utf8');match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Predecessor rendered data differs')
 page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
 historical='<details><summary>Previous QuPath checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
 if page.count(PRIOR_NOTICE)!=1 or page.count('<p id="overview"></p>')!=1:raise ValueError('Predecessor notice/overview differs')
 page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1)
 warnings=before[META[5]].decode('utf8');marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
 if warnings.count(PRIOR_NOTICE)!=1 or warnings.count(marker)!=1:raise ValueError('Predecessor warning notice differs')
 warnings=warnings.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1)
 raw=encoded(papers)
 if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
 intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [rstatix source roles, original/native commands and recovery](../RSTATIX_CATALOG_INTEGRATION.md). Validate with `python tools/literature/rstatix_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-rstatix-catalog-check --rescan-sources`. The full semantic review and implementation objective remain open.\n\n'
 return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:raw,REVIEWS:encoded(document),
  META[2]:(intro+'<details><summary>Retained previous catalog checkpoints (historical)</summary>\n\n'+before[META[2]].decode('utf8')+'\n</details>\n').encode('utf8'),
  META[3]:(WARNING+'\n<details><summary>Retained previous warnings (historical)</summary>\n\n').encode('utf8')+before[META[3]]+b'\n</details>\n',META[4]:page.encode('utf8'),META[5]:warnings.encode('utf8')}

def check_transition(root,before,after):
 expected=transition(root,before)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Catalog differs from exact one-upstream-workflow and five-review admission')
 return dict(papers=250,named_tools=432,observations=2671,reviewed_uses=1148,unreviewed_uses=1523,new_scoped_integrations=108,missing=265,
  unchanged_previous_families=431,unchanged_previous_observations=2666,unchanged_previous_reviews=1143,enriched_existing_observations=5,
  added_observations=0,added_reviews=5,new_scoped_integration_credit=1,native_family_credit_added=0,existing_P040_review_unchanged=True,whole_goal_complete=False)
