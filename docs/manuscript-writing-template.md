# Writing a coherent human omics manuscript

Template: **2026-11-14.6**. The executable source of the complete prompt is
[`manuscript-writing-v1.txt`](../src/main/resources/org/jlinalg/study/manuscript-writing-v1.txt).
`StudyManuscriptPolicy` loads that resource for drafting and editorial review and
records its version and SHA-256. This guide explains the rationale, the paper
survey, and how to inspect the implemented behavior. The prompt is a writing
specification; statistical definitions and frozen study evidence remain authoritative.

## What the supplied Splicing-BMI manuscript showed

The reviewed PDF was the October 7, 2026, 12-page revision supplied by the user.
This review concerns that document's writing and presentation, not whether its
historical analysis is the current scientific result.

* The Introduction moves immediately from transcript regulation to EPB41, PCBP2,
  Runx1 and pancreatic cells. The reader has not yet been told the broader adiposity
  problem, what human transcriptomic studies have established, or which unresolved
  question requires an isoform-level measurement.
* The next paragraphs organize themselves around individual gene papers. They
  meet paragraph-role labels but do not form a persuasive argument from background
  to question to design. A collection of relevant references is not a rationale.
* Generic contrasts and qualifications recur in almost every section. Some
  limitations are essential, especially model comparability, heterogeneity and
  analysis-population uncertainty. Their repetition as broad disclaimers obscures
  the specific implications that the reader actually needs.
* Editorial reasons, source-status statements, parameter-review headings and
  software audit commentary appear among scientific paragraphs. Printing collapsed
  HTML details exposes additional labels. Those are useful review records but
  interrupt an article.
* **Table 1 exists on page 5.** It describes phenotype-file populations and is
  explicitly provisional. It does not establish membership in each association
  analysis. A study-population table must identify its population, not merely
  occupy a numbered table slot. The software also had a separate gap: the
  study-wide AI draft did not directly receive local participant-summary outputs.

Improved prose cannot repair historical sample membership, model specifications,
or statistical results. Those need their own verified inputs. The attached PDF
was not rewritten or treated as the current study contract.

## Structural survey of twelve human transcriptomics papers

This is a purposive editorial comparison, not a systematic review or a scientific
quality ranking. It covers phenotype-association studies, blood transcriptomics,
multi-tissue resources and regulatory-genetics studies from 2010 through 2025.
Introductions/backgrounds, section order, and population reporting were inspected
in full-text public articles; Discussion framing was inspected where relevant.
Ten articles were retrieved through Europe PMC XML. The FHS RNA-source study and
GTEx 2020 article were inspected through PMC web text; GTEx 2017 was inspected
through both XML and web text. Retrieval was on November 14, 2026. Inaccessible
candidate papers were not counted. No private manuscript text was sent to a model
or external literature service.

The notes below are original observations about organization, not statements that
the papers support the new study's specific findings. Their scientific claims,
historical prevalence estimates, novelty claims and causal terminology should not
be copied into another manuscript.

| Paper | How the Introduction develops the question | Transferable lesson and population reporting |
| --- | --- | --- |
| [Whole-blood expression and obesity (2010)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3014865/) | Begins with genetic and environmental susceptibility to obesity, then introduces transcription and the rationale for using blood, ending with the comparison to be tested. | Explain the phenotype-to-measurement connection before gene results. Table 1 supplies demographic and phenotypic characteristics. Its early result preview is a journal/style choice, not a required template feature. |
| [FHS comparison of blood RNA sources (2012)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3289123/) | Establishes the accessibility and uses of blood RNA, explains differences among sample compartments, identifies a design gap, and specifies primary and secondary goals. | A tissue or assay rationale is part of the argument. Methods identify the sampled cohort/visit and selections; early Results refer to clinical characteristics in Table 1. |
| [Whole-blood transcriptome associations with BMI in two cohorts (2015)](https://pmc.ncbi.nlm.nih.gov/articles/PMC4608219/) | Moves from adiposity-related health burden through genetic complexity and limits of GWAS interpretation to expression profiling and the two-cohort design. | A reader encounters the reason for the study before pathways. General Results introduce the cohorts and Table 1. Results and Discussion are combined, illustrating that the reasoning matters more than identical headings. |
| [Integrated genome-wide eQTL analysis (2017)](https://pmc.ncbi.nlm.nih.gov/articles/PMC5264466/) | Explains why noncoding GWAS findings motivate gene-regulatory analysis, then describes limitations of earlier studies and the contribution of the current resource. | A short Introduction can still connect problem, measurement, gap and objectives. Table 1 reports demographic characteristics. Resource objectives need not be invented directional hypotheses. |
| [GTEx genetic effects across tissues (2017)](https://pmc.ncbi.nlm.nih.gov/articles/PMC5776756/) | Introduces variation in regulation, the limitation of studying only accessible tissues, the expanded resource, and the analyses it enables. | Explain why the sampling design addresses the gap. Study design precedes detailed QTL findings; cohort/tissue information can be communicated through design figures and supporting material rather than a universal main Table 1. |
| [GTEx regulatory atlas (2020)](https://pmc.ncbi.nlm.nih.gov/articles/PMC7737656/) | Starts with the interpretation of genetic variation, introduces the tissue resource as a response, and then states the scope of the new release. | Broad biological motivation can be concise. The first results section describes donor and sample selection before molecular discoveries. Methods placement follows the journal rather than a universal IMRaD order. |
| [LIFE population blood transcriptome (2021)](https://pmc.ncbi.nlm.nih.gov/articles/PMC7931910/) | Establishes blood as an accessible source, surveys prior applications, explains limited coverage of population variability, and introduces the cohort and analytic objectives. | Explain what the larger and more heterogeneous population enables. The article points to participant characteristics in Table 1 before presenting the transcriptomic patterns. |
| [eQTLGen blood cis/trans analysis (2021)](https://pmc.ncbi.nlm.nih.gov/articles/PMC8432599/) | Explains what cis effects illuminate, why distal and cumulative effects matter, what makes them difficult to detect, and why a large consortium can help. | Each paragraph supplies a reason for the next analysis. Early Results establish the design and scale; the Discussion interprets the contribution and concrete replication limitations. |
| [FHS whole-genome and RNA sequencing (2022)](https://pmc.ncbi.nlm.nih.gov/articles/PMC9686236/) | Progresses from GWAS interpretation to eQTLs, prior sample/measurement limitations, and explicit resource objectives enabled by sequencing. | Introduce the measurement before its technical advantages. The main Table 1 presents molecular findings, showing that table numbering is not itself a universal standard for population reporting. |
| [MAGE diverse human transcriptomes (2024)](https://pmc.ncbi.nlm.nih.gov/articles/PMC11291278/) | Connects gene-expression diversity to phenotypic variation, identifies representation and previous-resource limitations, and introduces a resource designed to address them. | State the consequence of the gap. The first results section describes the population and dataset before association results; population information is prominently organized in Figure 1. |
| [Trans-PCO gene-set regulatory mapping (2024)](https://pmc.ncbi.nlm.nih.gov/articles/PMC11019359/) | Moves from unexplained regulatory effects to statistical obstacles, compares existing approaches, and motivates the proposed method and human-data application. | A methods-led paper can start broadly enough to establish its purpose, then get technical. Specific limitations are useful when they explain design choices, rather than serving as generic warnings after every result. |
| [Genetic/transcriptomic integration at obesity loci (2025)](https://pmc.ncbi.nlm.nih.gov/articles/PMC12583137/) | Establishes the obesity problem, identifies the locus-to-gene gap, motivates transcriptomic integration and tissue choice, then states the study design. | This is a close comparator for the logic of a BMI Introduction. Detailed gene interpretation follows the foundation. Its main Table 1 summarizes associations, so our population-Table-1 requirement is a deliberate product choice, not a claimed universal journal rule. |

The recurring editorial lesson is **problem -> relevant knowledge -> consequential
gap -> measurement/design rationale -> objective**. The precise order of the two
middle functions varies naturally. This is an inference from the comparison,
not a formally measured readability result. None of the examples justifies
copying a rigid paragraph count, a claim of novelty, or stronger causal language.

## Detailed template and composition process

Use the complete [runtime prompt](../src/main/resources/org/jlinalg/study/manuscript-writing-v1.txt)
for drafting. It covers preparation, title/abstract, Introduction, Methods,
Results/Table 1, Discussion, caveat placement, style, and evidence/output rules.
The following describes how to apply it in a study.

### 1. Prepare an argument, not a list of sections

Before drafting, fill these questions from reviewed evidence:

1. What biological or clinical problem makes the question worth asking?
2. What do the closest human studies already tell us?
3. What exact uncertainty remains, and why does it matter?
4. What biological process are we trying to understand, what do we actually measure,
   and why is that measure a suitable proxy for this question?
5. Why can this tissue, population and design address that uncertainty?
6. What are the declared primary objectives and secondary/exploratory questions?
7. What could resolving the question change in scientific understanding or enable
   in subsequent research, and why does that make the study worth doing?
8. What answer do the actual results support, with what material limitations?

Keep this outline in the working/review records. Test the logical connections
before writing polished sentences. If the only available literature consists of
hit-gene papers, request broader background and directly comparable human studies.
Do not assemble a background from whatever citations happen to be available.
Preserve the scientist's stated motivation. If it is missing from the reviewed
context, request it explicitly; neither a phenotype label nor a list of analysis
goals establishes why the study was conceived. This writing change does not add
unreviewed private project prose to external model packets.

**Persuasive motivation and potential impact are required.** The reader should
understand why the question needs answering, why this measurement/design is useful
for answering it, and what a credible answer could teach us or make possible.
Evidence and a chain of reasoning earn the importance; stronger adjectives do not.
Potential impact is a prospective scientific contribution, not a result preview or
an unsupported clinical promise. Mechanical paragraph-role checks cannot certify
any of these qualities.

### 2. Introduction: default five-paragraph plan

**Paragraph 1: biological problem.** Establish the phenotype and the specific
biological question. Explain why understanding it matters. Avoid a stock opening
followed by a sudden jump into a gene. Current prevalence figures require current
sources and are optional; they do not substitute for a scientific rationale.

**Paragraph 2: knowledge and molecular resolution.** Explain what relevant human
studies have found. Introduce gene abundance, transcript usage or the appropriate
measurement in accessible terms. Explain why this measurement could reveal
information not represented by the earlier measurements.

**Paragraph 3: question-to-measurement bridge.** Connect the biological question
to the process of interest and measured quantity or proxy. Explain why this choice
is informative and how its interpretability helps address the question. Compare
a relevant alternative fairly and only on supported dimensions. Tissue, sampling
and replication strengthen this argument; they must not become a substitute for
the scientific motivation.

**Paragraph 4: consequential gap and potential impact.** Use the closest studies
to explain what remains unknown about the same question. State what resolving it
could change or enable: distinguish explanations, link an association to a specific
molecular feature, or guide a discriminating experiment. The anticipated impact
must follow from the measurement and gap. A free-standing paragraph about possible
applications is not that bridge. Include contrary evidence where it changes the
rationale; assay or sample-size differences alone do not establish novelty.

**Paragraph 5: close the motivating argument.** State the declared biological
objective, the chosen measurement, and the broad design used to address the gap.
The reader should see how the anticipated contribution follows from this design.
Keep hypothesis history accurate. Avoid detailed
analysis settings and a preview of selected genes. A discovery or resource paper
can have objectives rather than a fabricated prespecified directional hypothesis.

Four paragraphs are often enough by combining related functions. Longer sections
are permitted when justified. The four existing role names remain supported; the
first role can contain problem and knowledge paragraphs. The role order is still
checked, but paragraph counts are planning targets. Topic sentences read together
must form an argument. Repeated transition words cannot repair unrelated content.

### Splicing-BMI: the clarified scientific motivation

The scientist clarified that the motivating question is whether splicing plays
a role in BMI/obesity biology. Isoform ratios were chosen as an interpretable proxy
because an association can be related directly to current annotated transcripts.
The previous example overemphasized blood sampling and reproducibility, without
making those choices serve this biological question. The revised argument is:

1. Splicing could be relevant to adiposity, so understanding its phenotype
   relationship is a worthwhile biological question.
2. Studying that question requires a measurable representation of transcript
   processing beyond total gene abundance.
3. Relative usage of annotated transcripts provides a proxy with transcript-level
   interpretability, supporting hypotheses about specific RNA products and their
   follow-up. Junction/intron-event methods offer a complementary representation.
4. It remains to be established which of these transcript-interpretable changes
   relate to BMI/obesity in the study populations. Resolving that question could
   connect broad phenotype associations to specific RNA-processing hypotheses
   and make subsequent functional experiments more focused.
5. The objectives and cohort design follow from that question and expected
   contribution; replication and genetic follow-up support its investigation.

This is a scientist-supplied motivation, not a claim that the study was
prospectively registered with a directional hypothesis or that its results prove
causal involvement. The transcript-level interpretation rationale is consistent
with the [isoform-ratio study](https://www.nature.com/articles/s41467-025-64336-8).
The [LeafCutter paper](https://www.nature.com/articles/s41588-017-0004-9) explains
the complementary value of annotation-free intron-excision quantification.
These sources support a question-specific measurement choice, not universal
superiority, inability to interpret junctions, or an unperformed head-to-head
comparison. Ratios also reflect annotation/quantification choices and other RNA
processing mechanisms; investigating a role for splicing is distinct from having
established a causal mechanism. “Instrumental” in the motivating question should
not be mistaken for the instrumental-variable assumptions of MR.

Keep the scientific question constant through the last three paragraphs. Test
each transition explicitly: why does this question require this measurement;
why does that measurement make the remaining gap consequential; and how does the
final objective address it? Read those paragraphs without the Results. If they
could be reordered freely, or explain only what was done rather than why it was
worth doing, revise them before accepting the Introduction.

### 3. Methods and participant characteristics

Describe cohorts, actual visits, selection and tissue first, followed by molecular
measurement, QC and performed analyses. Give the exact estimand, scale, model,
covariates, relatedness adjustment, multiple-testing and replication definitions.
State software versions, resource versions and effective parameters. Include
recorded reasons for unusual choices. Avoid turning standard choices into an
extended defense or mixing execution history into scientific prose.

Table 1 should identify its population and include cohort/design-stage columns,
analysis N and unit, age, the recorded sex/gender variable, main phenotype,
relevant covariates, units, denominators and missingness. Native summaries currently
support means/sample SDs and binary proportions; the prompt must not invent
medians/IQRs or categories unavailable from the supplied artifacts. Those would
require a separate supported summary calculation. Population-specific or
feature-specific sample sizes must be distinguished from a shared overall N.

Missing summaries produce a visible Table 1 placeholder and `needs_input` review.
Unverified phenotype-file populations stay labeled provisional. Confirmed sample
membership and completeness are separate questions. The table is inserted locally
from retained summary artifacts. Participant rows and demographic values are not
automatically added to a model packet by this change.

### 4. Results: people and question before genes

Open with the population, analytical scope and Table 1. Present primary evidence
and replication before downstream interpretation, following Methods order.
Within each result, orient the reader to the comparison, give the estimate and
uncertainty, and report the robustness result that matters. Use explicit
denominators and distinguish transcripts, genes, loci and participants. Keep
important null or discordant results visible. Do not turn Results into a ranking
of P-values, a sequence of operation status messages, or a literature review.

### 5. Discussion: a persuasive synthesis and a justified next step

Begin by answering the study question. Develop the selected principal findings
in a biologically meaningful order. Each may span several connected paragraphs.
The observation, literature, mechanism, counterevidence, alternatives, relevance,
next experiments and limitations are reasoning functions, not mandatory repeated
subheadings. Preserve the full discussion in supplements when necessary while
keeping the main argument understandable on its own.

Persuasiveness must come from the reasoning and evidence. Return to the
Introduction's motivation and explain the specific knowledge added relative to
the closest primary studies: what can now be understood, distinguished or tested?
Replication, refinement and informative null results can all be worthwhile
contributions. Novelty adjectives, lists of significant results and unsupported
clinical promises do not establish scientific value.

Tie the findings together rather than summarize each analysis in isolation.
Explain agreement, complementary information and unresolved tension, and how the
combination changes the answer to the study question. Account for shared samples
and dependent analyses; do not count them as independent confirmation or force
discordant findings into a single mechanism. The conclusion should follow from
this synthesis and answer the question that motivated the Introduction.

Highlight something unexpected **only if** an observed result departs from a
documented hypothesis or a source-supported prior expectation. Explain the
expectation, the departure, its scientific implication, alternative biological
or methodological explanations, and the test that would distinguish them. Label
post hoc explanations. If the evidence does not support a surprise, omit that
claim; the study need not be surprising to be worthwhile.

The default closing sequence is **implications → limitations → prioritized next
steps → conclusion**. Anchor potential impact in an actual finding and explain
what it could enable under a meaningful condition. Discuss grounded weaknesses
constructively: for each material gap, identify the affected inference and what
remains supported, then link feasible follow-up to that uncertainty. Explain how
different outcomes would change the interpretation and which constraints would
remain. Some weaknesses can only be reduced or require another design. Do not
promise to overcome all limitations or replace them with generic pessimism.
Prioritize follow-up by scientific value and the most consequential uncertainty;
proposed work must remain clearly prospective.

For Splicing-BMI, the Discussion should return to the motivating question about
splicing in BMI/obesity biology and the interpretability of isoform ratios against
annotated transcripts. Explain how the actual supplied findings advance that
question together, what the measurement resolves and what it leaves uncertain.
Do not convert associations into proof that splicing causes obesity, or transcript
interpretability into demonstrated superiority over junction methods. Any proposed
functional, longitudinal or cross-assay follow-up should address a specific
unresolved link in the observed evidence, rather than appear as a generic wish
list. This example defines the argument; it supplies no new study result.

Give a specific limitation at the point where it changes interpretation, and
consolidate shared limitations. For example:

| Habit to avoid | Better editorial action |
| --- | --- |
| Repeating that associations do not establish causation after every finding | Use association-appropriate verbs throughout and explain the actual design limitation and its consequence in one developed passage. |
| Announcing that the second finding matters because it occupies an impact slot | State the biological interpretation and the evidence that makes it consequential. Keep impact ranking in review metadata. |
| Repeating that a protein paper is not validation of an isoform ratio | Explain once which molecular level each study measured, what connection is supported, and which specific experiment would test the remaining link. |
| Adding a drug-target disclaimer without any therapeutic claim | Remove the gratuitous therapeutic framing through an attributed edit. Retain genuine constraints when therapeutic implications are discussed. |
| Replacing a source-specific limitation with vague hedging | State the concrete problem, such as incomplete regional coverage, and its effect on the inference. Preserve any mandatory caveat. |

These examples are editing instructions, not factual claims about the supplied
study. The renderer never deletes cautionary sentences from authored text. It
moves automatically generated editorial labels into a review copy. Rewriting an
existing manuscript still requires an attributed revision request.

### 6. Mathematical notation

Use concise numerical display precision in all manuscript sections, including
literal prose and equations. Structured tables and evidence bindings already use
`StudyReportNumbers`: normally two decimal places, with three significant figures
in scientific notation for small or large values. Apply the same writing rule to
unbound prose; keep justified precision where needed to interpret small effects
or correlations, and never turn a nonzero P value into zero.

For example, describe a scaling constant as **approximately 1.48**, rather than
printing `1.482602218505602`; its full precision remains in the implementation and
provenance. Exact decision cutoffs, counts, coordinates, identifiers and source
exports remain unchanged. The typography review flags decimal literals with at
least four fractional digits and more than four significant digits in prose and
equations, excluding code, URLs and embedded identifiers. This is a review cue,
not a blind rounding operation: revise an estimate or approximate constant in
context, and preserve a deliberately exact threshold. The cue does not certify
all short values or text embedded in figure images; inspect the PDF as well.

Use **≤** and **≥** in scientific prose. The renderer also converts their ASCII
spellings in prose without changing the authored revision, evidence or numeric
values. Strict inequalities remain strict; code and URLs are preserved.

Author inline equations with `\(...\)` and display equations with `\[...\]`
(or `$$...$$`). Single-dollar delimiters are not supported, to avoid confusing
currency with math. Use TeX fractions, summations, roots, Greek letters, subscripts
and superscripts. For example, **only when this is the actual pooling method**:

```tex
\[\hat{\beta}=\frac{\sum_{i=1}^{K}w_i\hat{\beta}_i}{\sum_{i=1}^{K}w_i},\qquad
  w_i=\frac{1}{\mathrm{SE}(\hat{\beta}_i)^2},\qquad
  \mathrm{SE}(\hat{\beta})=\sqrt{\frac{1}{\sum_{i=1}^{K}w_i}}.\]
```

Define the index, number of cohorts, estimates and standard errors. This replaces
`sum(w beta)/sum(w)` with an actual summation and fraction; it does not authorize
changing the estimator or inventing missing definitions. Use notation such as
`\(P \le 5\times10^{-8}\)`, `\(r^2\)` and
`\(30\,\mathrm{kg}/\mathrm{m}^{2}\)` for thresholds, powers and units.
In JSON, escape each backslash; YAML single-quoted or block strings preserve TeX
backslashes. Keep citations outside math delimiters.

Native revisions, their supplements and AI drafts embed **KaTeX 0.19.0** and render
native MathML locally, using the browser's math font. No remote scripts, fonts or
model calls are required. The pinned library, MIT license and verified distribution
hashes are under `src/main/resources/org/jlinalg/study/math/katex/`. Its
[rendering options](https://katex.org/docs/options.html) support MathML output and
bounded, untrusted input; links, images and HTML commands are rejected here.

The `mathematics_review` records equations and flags raw formula pseudocode or
unmatched delimiters. Ambiguous inherited formulas require an attributed edit
after checking the source method. Unsupported TeX remains visible with an error
instead of being silently removed. Inspect the rendered HTML in a current browser
with MathML support, then print/export to PDF **after** every equation has
`data-math-status="complete"` and the document has
`data-manuscript-math="complete"`. Check the PDF visually for glyphs and clipping.
A text-only HTML-to-PDF converter that does not run scripts and render MathML
cannot export these equations correctly. Typesetting does not certify mathematics.

## Implementation and version behavior

The main manuscript presents the science. Internal version histories, template
versions, hashes, audit trails and execution receipts stay in separate review
records. The reading copy has no audit navigation or template-version banner.
The scientific supplement contains authored supplementary notes and participant
details, plus one short link to supporting records. Full operation manifests and
audit listings remain in `evidence-review.html` (or
`publication-evidence-review.html` for native scaffolds).
Use one concise **Data and code availability** statement with verified locations
and actual access conditions near the end. Software/resource versions and
technical reproducibility details belong in Supplementary Methods or linked
documentation; an optional provenance paragraph in the scientific supplement
should be brief. Existing authored prose is preserved until an attributed edit
relocates it; the renderer does not silently strip sentences.

The Discussion should be constructively optimistic: connect a finding to what it
could enable, why that prospect matters and what specific validation would advance
it. Prefer a meaningful condition such as “If confirmed in an independent sample,
this pattern could help distinguish…” to stacked hedges and repeated disclaimers.
The implication must have an evidence anchor. Keep consequential limitations and
end with the supported contribution and a promising next question.

Write the **structured abstract after the body is finalized**, then insert it once
after the title, before Introduction. Use **Background, Methods, Results,
Conclusion**. It must reconcile the objective, design, population, principal
quantitative findings and implication with the finished article. The software
checks structure and flags an unchanged abstract when a native revision changes
the rendered body; it cannot prove authorship chronology or scientific accuracy.
Native requests use four `section: abstract` blocks with `abstract_role` values
`background`, `methods`, `results`, `conclusion`. A legacy unstructured abstract
is preserved and flagged. Missing abstracts render as four labeled input
placeholders, with `abstract-review.yaml` reporting `needs_input`.

For example, append the four abstract blocks to a revision request after writing
the body (the renderer places them first):

```yaml
- id: abstract-background
  section: abstract
  abstract_role: background
  text: "[SCIENTIST INPUT REQUIRED: biological problem, gap and objective.]"
  sources: []
  rationale: Summarize the finalized Introduction.
```

Repeat for `methods`, `results` and `conclusion` using the matching final content.
Use `section: availability` for the short availability statement. Abstract and
availability text are excluded from the main-text word budget. Absent availability
information remains an input placeholder; no repository or public deposit is invented.

* Drafting and editorial prompts use the same bundled writing resource. The
  version and exact prompt hash are saved in `manuscript-writing-policy.yaml`,
  alongside `manuscript-writing-prompt.txt` in new draft/report/revision outputs.
* Native revisions write `manuscript.html` as the reading copy and
  `manuscript-review.html` as the annotated copy. Scientific prose, numerical
  bindings, citations, supporting relationships and authored caveats are retained.
  The annotated copy retains parameter inventories, source status, editorial
  rationale and software provenance. Both copies are covered by bundle hashes.
* Native reports similarly distinguish `publication-draft.html` (an explicit
  writing scaffold) from `publication-review.html` (evidence and review details).
  A deterministic scaffold does not pretend to supply a researched narrative.
* AI drafts use a `[TABLE_1]` marker; the renderer inserts the native local table.
  If the marker is missing, it inserts the table after the Results heading, or
  appends it when no Results heading exists. The model's response remains in the
  interaction record and is HTML-escaped. The fallback is reported for review;
  it does not certify that the response contains correct population prose.
* `participant-characteristics-review.yaml` distinguishes missing/provisional
  populations. `manuscript-readability-review.yaml` records lexical repetition
  cues and a human reading checklist. These are review aids; no heuristic score
  approves a manuscript or silently rewrites it.
  The existing native table displays continuous means/SDs and binary counts/
  percentages. The broader writing specification also allows supplied median/IQR
  summaries; adding that representation to the native table is separate work.
  The review checklist requires a scientist to confirm all needed variables,
  units, denominators and analysis membership.
* Every complete manuscript requires a structured abstract. New outputs provide
  `abstract-review.yaml`; native outputs also provide `abstract-template.yaml`.
  AI drafts move a supplied abstract to the front and keep it separate from Table 1
  placement and main-text word counting. Duplicate abstract blocks remain flagged
  and their content is retained together for review. Submission metadata such as authors, ethics,
  contributions, funding and conflicts still requires supplied, reviewed content;
  this change does not fabricate or certify those declarations.
* The writing-policy identity participates in report, impact and manuscript cache
  keys. Updated policy cannot silently reuse those older operation outputs.
  Historical bundles remain immutable; create a new revision to apply new prose
  or rendering. Statistical stages retain their existing cache rules.
* Introduction and manuscript-writing review records now include
  `motivation_review`, with `status: substantive_review_required` and
  `assessed_by_structural_checks: false`. It records the required argument and
  skeptical-reader questions, including potential impact. Even correctly ordered,
  cited paragraphs cannot automatically pass this review. Drafting prompts require
  a persuasive argument and a final bridge check; editorial prompts request the
  weakest bridge, missing impact argument and an attributed replacement. This is
  an explicit writing/review requirement, not an automated semantic score.
* Results/Discussion and manuscript-writing review records include
  `discussion_review`, with `status: substantive_review_required` and
  `assessed_by_structural_checks: false`. Its reader questions cover scientific
  contribution, integration of results, implications, documented surprises,
  limitation-to-inference links, discriminating follow-up and the final answer.
  Editorial review must identify the weakest inference or transition and propose
  an attributed replacement, or identify missing evidence when prose cannot fix
  it. Even structurally complete drafts cannot automatically pass this review.
* New Discussion templates place `limitations` before `next_steps`. Existing
  revisions with those two roles in the former order remain structurally accepted
  and are not silently reordered. Principal findings still precede implications,
  and the overall conclusion still closes the argument. Both orders require
  substantive review of how the limitations motivate the next steps.

The default remains an adjustable 8,000-word main-text target and 3-5 findings.
`word_limit_mode: strict` enforces an explicitly selected journal ceiling. A
section allowance is not a quota, and additional prose should earn its space.

## Review before using a generated article

Read the Introduction without the Results at hand. It should make sense to a
colleague unfamiliar with the selected genes. Then read only the topic sentences,
followed by the first Results paragraph and Table 1. Ask whether the motivation,
question, participants, measurement and contribution are clear. Check that every
important limitation remains, while generic disclaimers and audit commentary no
longer dominate. Read the Discussion's opening and conclusion together: do they
answer the motivating question and make the contribution clear? Then check how
the findings support that answer, whether any claimed surprise has a documented
expectation, and why the proposed next step follows from the evidence and its
limits. Finally verify claims, values and citations against their sources.

The automated tests exercise prompt routing/versioning, paragraph flexibility,
table insertion and escaping, provisional populations, and preservation of prose
and audit artifacts. They do not establish that a live model will write a good
manuscript. That requires a generated draft and substantive human review.

For an offline packaged check, run
`python examples/study-pipeline/manuscript-writing-workflow.py NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`.
The fixture creates artificial inputs, renders both copies, verifies the revision
bundle and checks local Table 1, retained caveats, prompt identity and unchanged
source hashes. It makes no provider calls and tests no biological interpretation.

For optional browser/PDF verification, run
`node examples/study-pipeline/manuscript-math-browser-check.cjs MANUSCRIPT_HTML OUTPUT_DIRECTORY`
against the fixture's `revision/artifacts/manuscript.html`. This check requires
Playwright and Chromium (or `MANUSCRIPT_BROWSER` pointing to an installed Edge or
Chromium executable). It verifies MathML fractions, sums and roots, checks for
remote requests and visible errors on invalid equations, and exports a PDF plus
an equation image for visual inspection. These tools are needed for this export
check, not for the Java manuscript command.
