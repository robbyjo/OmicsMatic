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
| 5 | Further common statistical methods | Prism appears in 46 papers. Map the actual models and fill remaining repeated statistical/interval needs in Java. |

Other frequent gaps include bcl2fastq and Space Ranger (ten papers each),
IMPUTE and MaxQuant (six each). Runtime and source-use restrictions need
tool-specific assessment. Software-name frequency alone does not establish
that the project needs an entire vendor GUI or instrument-control stack.

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

The validated LDAK development checkpoint is preserved at local commit
`e2bf00a9ccf03911ec27f818ef5c6a617e5602f1`, with its matching reader, closed
evidence and failed attempts. Its six-role catalog preview remains pending
while implementation priority changes. Current admitted counts remain 127
integrations and 260 missing families; no new family credit is awarded for
this priority change or a standalone statistical component.
