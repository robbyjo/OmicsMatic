# Supplied-background HOMER genomic examples

Use Linux Java 17+, inside WSL on Windows, from the repository root. Set `JAR`
to the development executable in `homer-genome-phase-validation.json`, `HOMER_HOME`
to an admitted genomic home and `PROFILE` to its exact
`linux-x86_64-genome-5.1`, `linux-x86_64-genome-4.11` or
`linux-x86_64-genome-4.11.1` profile. Core and annotation homes have different scopes.
Replace `NEW_INPUTS` and every `OUT_*` token with a new absolute directory.

```sh
python3 examples/homer-genome-workflow/prepare_inputs.py --out NEW_INPUTS
INPUTS=/ABSOLUTE/PATH/TO/NEW_INPUTS
```

The generator writes deterministic seed-20261028 synthetic data, 64 target and
128 background regions, two models and five string-valued JSON option files.
It records every file hash in `provenance.json`. These are technical fixtures;
they are not paper data, a biological calibration or recommended sample sizes.
The first three recipes run known enrichment, de novo discovery, motif matching
and SVG/HTML reports through the original genomic wrapper. The last two request
one phase explicitly.

Given intervals, GC weighting, order-two normalization and both strands:

```sh
LC_ALL=C.UTF-8 LANG=C.UTF-8 java -Xmx1536m -jar "$JAR" homer-tools genome \
  --options-file "$INPUTS/given-gc-normalized.json" \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_GIVEN
```

HOMER positions with 0/1 strand and focus columns, size 200, CpG weighting,
normalization off, forward discovery, hypergeometric tests and retained overlap:

```sh
LC_ALL=C.UTF-8 LANG=C.UTF-8 java -Xmx1536m -jar "$JAR" homer-tools genome \
  --options-file "$INPUTS/numeric-cpg-forward.json" \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_NUMERIC
```

**Review the strand warning.** The option file explicitly selects
`known-strand-policy original-wrapper`: 4.11/4.11.1 retain BOTH-strand known
enrichment while normalization/discovery use FORWARD. Version 5.1 forwards the
choice correctly. For consistent forward-only known enrichment on legacy
versions, the default `require-match` policy skips this configuration; select
a suitable validated version explicitly instead of silently changing behavior.
Numeric size 200 yields 201 inclusive bases under the original resize rule.

Asymmetric -50,150 resizing, lowercase masking, GC weighting, order-one
normalization and removal of an additional overlapping background interval:

```sh
LC_ALL=C.UTF-8 LANG=C.UTF-8 java -Xmx1536m -jar "$JAR" homer-tools genome \
  --options-file "$INPUTS/asymmetric-masked-overlap.json" \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_ASYMMETRIC
```

Known enrichment only, retaining order-two normalization:

```sh
LC_ALL=C.UTF-8 LANG=C.UTF-8 java -Xmx1536m -jar "$JAR" homer-tools genome \
  --options-file "$INPUTS/known-only.json" \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_KNOWN
```

This selects `analysis known` and requires only `known-motifs`. Remove
`comparison-motifs`, `motif-length`, `mismatches` and `motifs-to-find`; these
unused discovery options fail validation. Known-only does not impose a
discovery seed-window or mismatch-neighborhood requirement. Its actual fixed-model
audit still has work and memory bounds.

Forward de novo discovery, known-motif matching and SVG/HTML reporting only:

```sh
LC_ALL=C.UTF-8 LANG=C.UTF-8 java -Xmx1536m -jar "$JAR" homer-tools genome \
  --options-file "$INPUTS/denovo-only.json" \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_DENOVO
```

This selects `analysis denovo` and requires `comparison-motifs`. Remove
`known-motifs` and `known-strand-policy`; these unused enrichment options fail
validation. Forward-only discovery works on all three versions without permission
for an unrequested legacy known-enrichment phase. Matching learned models to a
known database does not perform known-motif enrichment.

**An unrequested phase is not a negative finding.** It has `not-requested`
status and null motif counts in `session.json`, and no corresponding result
tables. `known-only` omits learned-model outputs; `denovo-only` omits known
enrichment outputs. Original requested reports remain available.

Read `WARNINGS.md`, `session.json`, all input/model/population ledgers and
original reports. Every source region remains represented, including excluded
N-rich regions, unsupported composition bins and overlapping background.
The last recipe should exercise all three exclusion categories.

Missing, forbidden or unsupported dependencies **skip the affected analysis,
exit 3**. Required action: use an admitted Linux x86_64 genomic runtime where
permitted, following [setup and recovery](../../tools/literature/HOMER_GENOMIC_RUNTIME.md).
Analysis never installs. Failures **exit 2** and quarantine provisional files;
correct the named cause and use a new output directory. Partial tables are not
completed results. Original q is not standard BH, and native learned-model
presence bounds do not reconstruct the optimizer.

Run all fifteen recipe operations and fifteen fresh direct original comparisons with
`tools/literature/verify_homer_genome_workflow.py`; see the
[workflow guide](../../docs/literature/omics-2023-2026/HOMER_GENOMIC_WORKFLOWS.md).
Generated backgrounds, named resources, peak/coverage and RNA workflows remain
unfinished. Cell Ranger remains excluded.
