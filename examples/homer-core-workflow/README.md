# Original HOMER core examples

Use Linux Java (inside WSL on Windows), from the repository root. Set `JAR` to
the development executable identified in `homer-core-validation.json`,
`HOMER_HOME` to a private admitted home and `PROFILE` to its exact
`linux-x86_64-5.1`, `linux-x86_64-4.11` or `linux-x86_64-4.11.1` profile.
Choose new output paths in place of `OUT_CHECK`, `OUT_HYPERGEO`, `OUT_BINOMIAL`
and `OUT_FIND`. Read [the workflow guide](../../docs/literature/omics-2023-2026/HOMER_CORE_WORKFLOWS.md)
for installation, contracts, limits and remaining work.

Check the already installed profile:

```sh
java -Xmx1g -jar "$JAR" homer-tools check \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_CHECK
```

Test the supplied motifs against an explicit supplied background. These are
unweighted sequence-presence tests without background generation, sequence
normalization, threshold optimization or multiple-testing adjustment.

```sh
java -Xmx1g -jar "$JAR" homer-tools known \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_HYPERGEO \
  --targets examples/homer-core-workflow/targets.fa \
  --background examples/homer-core-workflow/background.fa \
  --motifs examples/homer-core-workflow/motifs.motif \
  --strands both --alphabet strict-dna --duplicate-ids error \
  --overlap-policy error --statistic hypergeometric --normalization none
```

```sh
java -Xmx1g -jar "$JAR" homer-tools known \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_BINOMIAL \
  --targets examples/homer-core-workflow/targets.fa \
  --background examples/homer-core-workflow/background.fa \
  --motifs examples/homer-core-workflow/motifs.motif \
  --strands both --alphabet strict-dna --duplicate-ids error \
  --overlap-policy error --statistic binomial --normalization none
```

Find every supplied fixed-threshold site with an explicit midpoint offset:

```sh
java -Xmx1g -jar "$JAR" homer-tools find \
  --home "$HOMER_HOME" --profile "$PROFILE" --out OUT_FIND \
  --targets examples/homer-core-workflow/targets.fa \
  --motifs examples/homer-core-workflow/motifs.motif \
  --strands both --offset midpoint --alphabet strict-dna --duplicate-ids error
```

**Read each output's `WARNINGS.md` and Required action.** Missing, forbidden,
unsupported or changed dependencies skip with exit 3. Other failures exit 2;
provisional files under `partial/` are not completed results. Preserve them,
correct the named cause and use new paths. Original coarse P strings remain
distinct from original log P and native audited log P. No q or E values are
produced. Review all identity/model/population/count ledgers before using results.

These examples do not implement motif discovery, genome/Perl annotation, peak,
coverage or RNA workflows. A motif score is not a binding probability. Native
`homer-scan` is separately available; it is not an implicit fallback. Cell Ranger
remains excluded.
