# SVA and ComBat: source corrections and next implementation

The full 250-paper implementation goal remains active. This source correction
adds **zero** native/upstream families. The effective catalog contains **178
scoped families =35 native +143 upstream**, 453 labels, 492 provider definitions,
2,734 observations, **1,854 individually reviewed uses and 880 pending uses**.
There are **247 missing labels**. The frozen cohort remains 71 Nature, 104 Nature
Genetics and 75 AJHG papers from 2023-10-23 through 2026-10-23. The current
registry is `docs/literature/sva-combat-catalog-correction/REGISTRY.json`.

## Corrected source decisions

| Paper / label | Source decision | Remaining implementation boundary |
| --- | --- | --- |
| [P086](https://doi.org/10.1038/s41588-025-02419-4) / ComBat | Recover the previously missed `ComBat_seq` spelling using the unchanged collector with an explicit underscore-aware definition. | The paper attributes this function to DESeq2 and describes VST/z-score data. Upstream `sva::ComBat_seq` uses a negative-binomial count model. The actual input units and author command are unresolved; no valid execution is inferred. |
| [P108](https://doi.org/10.1038/s41588-023-01560-2) / ComBat and sva | Continuous VST gene/isoform expression, site-batch correction, sva 3.46.0. Two named labels describe one ComBat operation. | Exact ComBat options/design and inputs remain open. The later HCP factors are separate and do not establish an SVA factor-estimation run. |
| [P128](https://doi.org/10.1038/s41588-026-02722-8) / ComBat | Longitudinal hippocampal-volume harmonization with biological covariates. The cited method is Beer et al., NeuroImage 220, 117129. | A longitudinal mixed model must account for repeated subjects. Ordinary continuous ComBat is insufficient. Exact package/commit and subject/time/random-effect specification remain unknown. |
| [P176](https://doi.org/10.1016/j.ajhg.2025.12.002) / sva | The primary RNA-seq paragraph describes surrogate-variable adjustment; linked author source calls `svaseq` on DESeq2-normalized counts with genotype full/intercept null models. | Author package version and complete recipe are unresolved. The inspected two-file author tree has no license declaration; source was read only, never executed or redistributed. |
| [P185](https://doi.org/10.1016/j.ajhg.2024.10.017) / sva | SVA 3.46.0 screening of methylation M-values reports no significant surrogate variables after batch adjustment. | The entry point, factor-count estimator, design, seed and permutation settings are unreported. Do not infer `num.sv`, BE or Leek, or claim independent reproduction of the reported outcome. |
| [P206](https://doi.org/10.1016/j.ajhg.2024.09.002) / removed sva observation | SVA is a retrotransposon insertion annotation target, not the software. Preserve its raw observation in the exclusion ledger and predecessor ZIP. | No software implementation credit or positive review is awarded. |

The complete primary XML census finds 12 matching paragraphs in nine papers,
including five positive paragraphs that support six label observations. Seven
other paragraphs are repeat biology or background alternatives. Three additional
abstract/back structures are retained as context. The ordinary verb “combat” in
P029's abstract is excluded. This is an expanded primary-text census; it does not
claim complete supplementary-method review or discovery of every unrecognized
alias.

`ROLES.json` preserves each original raw observation, including null automatic
version fields; separately reviewed versions do not overwrite them. The source
provenance records complete XML identities, paragraph/reference hashes,
read-only upstream source identities and Git-blob/line-range hashes for the
author script. Full article text, original source and raw traces remain private.
All 1,848 existing reviews, unrelated observations, cohort identities and prior
implementation credit are preserved by the exact catalog transition.

## Warnings and required actions

**P086 analysis is unresolved.** Obtain the actual author input and command.
Do not round z-scores, invert VST, relabel normalized data as raw counts or use
continuous ComBat as a substitute. `ComBat_seq` and `svaseq` have different input
contracts. This audit installs nothing and admits no new analysis/runtime.

**Existing Java methods remain method-overlap.** The native continuous ComBat
implementation is not an implementation of the count or longitudinal model.
The generic native SVA estimator, including its Java random-number generator,
is not certified as the original R procedure. Original upstream functions need
separate source/version binding, full output checking and fresh replay.

**longCombat's inspected licensing declarations differ.** Its DESCRIPTION says
MIT, while its `LICENSE.txt` contains Artistic License 2.0. Both declarations and
hashes are retained without choosing one silently. No longCombat runtime was
installed or executed. Resolve applicable source terms before distributing an
integration that copies upstream code.

For a missing/forbidden source, runtime or input, report **SKIP3** and skip the
dependent analysis. Supply the named permitted prerequisite and retry NEW
output paths. For changed/invalid evidence, model/QC failure or cancellation,
report **FAIL2** and admit no partial result. Retain failed attempts, fix the
named cause and retry NEW paths. Never refresh an old scientific seal to admit a
different producer or input.

## Verification

From the repository root, with the retained private XML cache and task evidence:

```text
python -B tools/literature/sva_combat_source_audit.py --xml-cache <private-xml-cache> --private <private-tool-audit-903>
python -B tools/literature/sva_combat_catalog.py
```

Omit `--private` only when validating the complete original-XML census without
the separate upstream/author-source evidence. Its result explicitly records
that private upstream checks were not performed. The catalog verifier checks
the exact archived predecessor, preserves the earlier chain, derives inventory
membership and totals, validates all 912 catalog checksums and confirms that no
implementation credit changed. Earlier standalone catalog verifiers describe
their historical checkpoints; run this cumulative verifier for the current one.

## Next work

1. Qualify original sva 3.46.0 continuous ComBat and appropriate `svaseq` /
   factor-screening functions, with explicit parameters and input units. Keep
   P108's dual package/function labels distinct from independent executions.
2. Add a count-specific ComBat_seq workflow with raw-count validation, explicit
   batch/design/shrinkage policies and preservation of upstream row handling,
   integer output, diagnostics and failure behavior. P086's author workflow
   remains unresolved until its actual inputs are established.
3. Inspect and qualify the separate longitudinal mixed-model implementation;
   never route P128 to ordinary ComBat. Preserve subject, time and random effects.
4. Continue the 880 pending source reviews and high-impact missing integrations.
   Native Java implementations are preferred where practical; retain source
   differences instead of substituting generic semantics.

The previous FlashPCA source-built runtimes, producing JARs, failed official
static binaries and all prior scientific evidence remain retained. This
checkpoint requests no release and keeps the existing literature Pages hold.
