# Implementation priorities

The user requested higher-impact tools on 17 November 2026. Priorities combine
frequency in the frozen 250-paper cohort, reusable analytical capability,
existing project code and practical runtime availability. A family marked
missing in the admitted catalog may already have tested components; the status
does not mean that every underlying analysis is absent.

| Priority | Work | Evidence and benefit |
|---|---|---|
| 1 | Native Tukey–Kramer comparisons | Twelve papers mention post-hoc Tukey tests; seven explicitly describe one-way ANOVA/Tukey. Existing Java ANOVA gains a reusable all-pairs follow-up. |
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
Its independent reference contains 4,557 comparisons across 36 models. Nine
Dunnett papers remain separately reviewed and pending, including repeated and
unequal-variance models. The same audit found five bare-Prism source pointers
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
while implementation priority changes. Current admitted counts remain 127
integrations and 260 missing families; no new family credit is awarded for
this priority change or a standalone statistical component.
