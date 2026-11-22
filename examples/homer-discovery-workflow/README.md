# Original HOMER discovery examples

Use Linux Java, inside WSL on Windows, from the repository root. Set `JAR` to
the current executable in `homer-discovery-validation.json`, `HOMER_HOME` to an
admitted private home and `PROFILE` to its exact `linux-x86_64-5.1`,
`linux-x86_64-4.11` or `linux-x86_64-4.11.1` profile. Replace the four `OUT_*`
tokens with new output directories.

These are synthetic seed-20261028 fixtures: forty 120-base sequences per role,
with an ACGTAC signal implanted in targets. They are not a paper dataset,
biological calibration or recommended sample-size threshold.

Full sequence masking:

```sh
java -Xmx1g -jar "$JAR" homer-tools denovo \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_FULL \
  --targets examples/homer-discovery-workflow/targets.fa \
  --background examples/homer-discovery-workflow/background.fa \
  --motif-length 6 --mismatches 1 --motifs-to-find 1 \
  --strands both --alphabet strict-dna --duplicate-ids error \
  --overlap-policy error --statistic binomial --normalization none \
  --discovery-mode full-mask
```

Oligo-only masking with fixed final thresholds:

```sh
java -Xmx1g -jar "$JAR" homer-tools denovo \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_QUICK_FIXED \
  --targets examples/homer-discovery-workflow/targets.fa \
  --background examples/homer-discovery-workflow/background.fa \
  --motif-length 6 --mismatches 1 --motifs-to-find 1 \
  --strands both --alphabet strict-dna --duplicate-ids error \
  --overlap-policy error --statistic binomial --normalization none \
  --discovery-mode quick-mask-fixed
```

Oligo-only masking with final threshold optimization and hypergeometric scoring:

```sh
java -Xmx1g -jar "$JAR" homer-tools denovo \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_QUICK_OPTIMIZE \
  --targets examples/homer-discovery-workflow/targets.fa \
  --background examples/homer-discovery-workflow/background.fa \
  --motif-length 6 --mismatches 1 --motifs-to-find 1 \
  --strands both --alphabet strict-dna --duplicate-ids error \
  --overlap-policy error --statistic hypergeometric --normalization none \
  --discovery-mode quick-mask-optimize
```

Full masking with explicitly selected two-base oligo normalization:

```sh
java -Xmx1g -jar "$JAR" homer-tools denovo \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_NORM2 \
  --targets examples/homer-discovery-workflow/targets.fa \
  --background examples/homer-discovery-workflow/background.fa \
  --motif-length 6 --mismatches 1 --motifs-to-find 1 \
  --strands both --alphabet strict-dna --duplicate-ids error \
  --overlap-policy error --statistic binomial --normalization oligo2 \
  --discovery-mode full-mask
```

**Read each output's `WARNINGS.md` and Required action.** Missing, forbidden,
unsupported or changed dependencies skip3. Other failures exit2 and quarantine
provisional files under `partial/`; correct the cause and use new paths.
Original discovery counts/log P stay separate from printed-model rescans and
conservative presence envelopes. Uncertain compatible bounds do not reconstruct
internal precision. Positive1.000 seed sentinels are unscored, not log P values.
The emitted normalization file is an iteration snapshot, not final weights.

See [the workflow guide](../../docs/literature/omics-2023-2026/HOMER_DISCOVERY_WORKFLOWS.md)
for setup, parameters, recovery and scientific limits. Earlier dense find
capacity skips were incorrect and removed in the current candidate. Genomic
annotation, peak, coverage and RNA workflows remain unfinished. Cell Ranger
remains excluded.
