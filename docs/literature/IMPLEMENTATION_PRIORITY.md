# Implementation priorities

The user requested higher-impact tools on 17 November 2026. Priorities combine
frequency in the frozen 250-paper cohort, reusable analytical capability,
existing project code and practical runtime availability. A family marked
missing in the admitted catalog may already have tested components; the status
does not mean that every underlying analysis is absent.

The current 18 November tranche prioritizes LDSC, with 42 reviewed family roles,
after the separately qualified common-statistics and cytometry components.
The [new native Hsq command](../vignettes/ldsc-heritability.md) supplies prepared
unpartitioned one-step, fixed-intercept and two-step heritability fits, with
72 unchanged original-core references. The next
[native RG/Gencov command](../vignettes/ldsc-genetic-correlation.md) adds
two-trait heritability/covariance/correlation with 72 cross-trait original-core
references and explicit no-result statuses. The
[catalog integration](LDSC_CATALOG_INTEGRATION.md) admits one scoped native
family and reviews all 44 lexical candidates. It adds omitted uses in P019/P092,
removes comparison-only P151 and distinguishes two reused resources from study
execution. Stratified and wrapper-specific workflows remain separate; these
native methods do not reproduce all 42 papers.

The next [stratified Java API](../vignettes/stratified-ldsc.md) supplies prepared
joint partitioned fitting and binary-annotation overlap summaries. It follows
the selected source partitioned path's fixed initial weights. Forty-eight
original-core models, 22,596 fields and the same nine tests on Windows/Linux
are qualified separately. The [prepared-data workflow command](../vignettes/stratified-ldsc-workflow.md)
now captures complete inputs, model diagnostics and checked admission/failure
records. It passes 17 focused API/workflow tests on Windows and Linux, real
process controls including missing-runtime SKIP3, and all three public examples.
Input/annotation construction and source-specific stratified/wrapper choices
remain open. These method extensions add no second LDSC family credit.

| Priority | Work | Evidence and benefit |
|---|---|---|
| 1 | Remaining LDSC input preparation and source-specific workflows | The 42 reviewed family roles repeatedly require chromatin/eQTL annotations and enrichment. Reuse the qualified prepared-data command while resolving distinct annotation, stratified and wrapper requirements. |
| 2 | Remaining Monocle workflows | Nine papers; reuse existing native graph ordering and original Monocle 2/3 adapters while resolving the remaining model, version and custom-workflow gaps. |
| 3 | Reusable nf-core execution profiles | Six papers; existing sequencing components can support multiple pipeline families. Finish the named profiles and their actual execution gaps. |
| 4 | Shared cytometry analysis components | FlowJo appears in 27 papers and FACSDiva in 17. Distinguish acquisition/context mentions from computational uses; target reusable FCS, compensation and gating needs. |
| 5 | Further common statistical methods | Prism appears in 47 papers after the P215 source correction. Map the actual models and fill remaining repeated statistical/interval needs in Java. |

Other frequent gaps include bcl2fastq and Space Ranger (ten papers each),
IMPUTE and MaxQuant (six each). Runtime and source-use restrictions need
tool-specific assessment. Software-name frequency alone does not establish
that the project needs an entire vendor GUI or instrument-control stack.

The next repeated statistical gap is addressed by the
[native independent-sample Dunn command](../vignettes/dunn-comparisons.md).
The source review verifies all 250 primary XML identities, identifies eight
statistical-use papers and excludes an author-name hit. It distinguishes P122
Bonferroni, P167 Holm and unresolved model/family/outer-correction choices.
Its independent reference contains 4,557 comparisons across 36 models. The next
[Dunnett command](../vignettes/dunnett-control-comparisons.md) adds a native Java
ordinary one-way fit audit and original multcomp 1.4-29 inference for every
treatment versus one control. Six of the nine Dunnett source contexts are
ordinary-model component candidates. P006 remains ambiguous, P034 describes
repeated measures/Geisser-Greenhouse, and P217 has unequal-variance/variant
ambiguity; those roles remain pending. Exact author inputs and control settings
still prevent whole-paper attribution. The same audit found five bare-Prism source pointers
in P215 missing from the previous inventory. The
[canonical source correction](PRISM_SOURCE_CATALOG.md) now records all 47
papers. The five paragraphs support one new paper/tool observation and four
supporting pointers; they add no implementation badge. Previous qualification
snapshots retain their original pending-correction state and checksums.

The Tukey review found 14 papers with that name. P081 and P176 describe
box-plot conventions and are excluded from the post-hoc-test count. The
remaining twelve include one-way, factorial/nested and count-model contexts;
ordinary one-way support is attributed only where justified. The selected
one-way papers are P027, P031, P057, P062, P098, P218 and P226. The new
[Java method and command](../vignettes/posthoc-comparisons.md) do not resolve
every model-specific use or complete the GraphPad Prism family.
The [source crosswalk](tukey-kramer-development/SOURCE_REVIEW.json) records
all 28 matching paragraphs, their identities and the 14 paper classifications;
all 250 cached primary XML hashes were checked before this ranking was saved.

The [current Monocle command guide](MONOCLE_COMMANDS.md) consolidates the
separate exact-edition development workflows into the source-build interface.
Its runtime qualifications and remaining custom-method gaps are recorded
separately from the historical standalone checkpoints.
The [native residual Moran component](../vignettes/covariate-aware-moran.md)
adds fitted-design correction on explicit prepared weights/responses, with
108 original-spdep test references. It does not infer the paper's Monocle1.0.0
weight construction, historical inputs or remaining lineage/embedding choices.

The current nf-core tranche addresses two additional ATAC-seq roles in P050 and
P169. P169 reports atacseq 1.2.1; P050 leaves the edition unreported. The
[new ATAC guide](NFCORE_ATACSEQ121.md) records the original-source adapter,
native mapping checks and the completed 88-task reference execution, including
356 published files and portable verification on Windows and Linux. Remaining
nf-core pipeline roles still prevent family-level admission.
The original five-pipeline source lock and its historical checkpoints are retained.

The cytometry census verifies 48 matching paragraphs in 33 distinct papers;
the FlowJo27 and FACSDiva17 paper sets overlap. The new
[native stored-channel FCS API](../vignettes/fcs-stored-channels.md) supplies the
shared event-input component. It preserves explicit encoding/storage choices
and reports unsupported input forms. The next
[native cytometry command](../vignettes/cytometry-gating.md) adds explicit
compensation/unmixing, transforms and Gating-ML population gates. It runs the
complete original 10,000-event/30-gate reference file on Windows and Linux,
with every membership/count table byte-identical between platforms. Its
[bounded qualification and prominent warnings](cytometry-methods-development/README.md)
retain contradictory historical controls; no whole-suite or vendor badge is
claimed. This does not implement instrument acquisition, a vendor workspace,
descriptive intensity statistics, the FlowAI/UMAP/FlowSOM
plugin workflow in P051, or the distinct cell-cycle models in P122/P228.

The validated LDAK development checkpoint is preserved at local commit
`e2bf00a9ccf03911ec27f818ef5c6a617e5602f1`, with its matching reader, closed
evidence and failed attempts. Its six-role catalog preview remains pending
while implementation priority changes. Current admitted counts are 128 new
scoped integrations (24 native Java and 104 upstream) and 260 missing families.
The LDSC admission adds one family credit; standalone statistical components
and pending LDAK work receive no additional credit here.
