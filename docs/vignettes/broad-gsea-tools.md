# Native GSEA from the main command

The normal executable JAR exposes the native prepared-data workflow through
`broad-gsea-tools`. It supports RUN, VERIFY and REPLAY without installing an
external GSEA runtime. Start with the
[detailed workflow guide](gsea-native-workflow.md) and its
[synthetic example recipes](../../examples/native-gsea/README.md).

Copy the examples to a new working directory; every output directory must be
new. Replace `PATH` with the location of a newly built JAR:

```powershell
java -jar PATH/omicsmatic-0.4.0.jar broad-gsea-tools --help
java -jar PATH/omicsmatic-0.4.0.jar broad-gsea-tools --request NEW-WORK/run.json
java -jar PATH/omicsmatic-0.4.0.jar broad-gsea-tools --request NEW-WORK/verify.json
java -jar PATH/omicsmatic-0.4.0.jar broad-gsea-tools --request NEW-WORK/replay.json
```

Request-relative paths resolve beside the request file. Declare the source
profile, ranking/null/weight choices, signed decimal seed string, complete
supplied set family, universe/provenance and resource limits explicitly. This
prepared-data subset does not import vendor files, map identifiers, transform
expression, collapse probes or download collections. It does not reproduce
an author's undisclosed inputs/options or establish biological validity.

VERIFY recomputes the captured inputs in new output and compares every
scientific byte. REPLAY records lineage and preserves its parent. The global
route uses the same scientific classes as the independently qualified
standalone workflow. It also verifies/replays captures from that predecessor.
Its command integration is separate from unfinished GSEA catalog admission.

**SKIP3:** supply the named exact permitted prerequisite or keep that analysis
skipped. **FAIL2: NO COMPLETE RESULT:** retain inputs, partials, null prefixes
and diagnostics, correct the reported cause and retry new output. Missing
command syntax returns FAIL2. Do not replace nonfinite values, modify literal
identifiers, change algorithms or accept an incomplete family silently. If
the supervising JVM terminates before final status, no complete result exists.

Read the [current handover](../literature/HANDOVER_2026-11-20_GSEA_GLOBAL.md)
for exact scope, qualification, outstanding work and private transfer actions.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Aravind Subramanian et al. (2005) — Gene set enrichment analysis: a knowledge-based approach for interpreting genome-wide expression profiles](../CITATIONS.md#subramanian-gsea-2005) — [PMID: 16199517](https://pubmed.ncbi.nlm.nih.gov/16199517/) · [PMCID: PMC1239896](https://pmc.ncbi.nlm.nih.gov/articles/PMC1239896/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
