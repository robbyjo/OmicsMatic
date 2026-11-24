# Original IMPUTE2 workflows

**Development status: the v2.3.2 scoped adapter passes its [development checkpoint](IMPUTE2_CHECKPOINT_2026-11-23.md). It adds no admitted family or paper reviews.** Canonical paper-role admission remains pending, and the full 250-paper project remains incomplete. Read the scope and retained failures before using the adapter.

`impute-tools` runs an unmodified private IMPUTE2 v2.3.2 static Linux executable. Java validates and captures Oxford-format inputs, constructs explicit scientific commands, audits original outputs and supplies optional native probability-rounding bounds. The imputation and phasing estimator runs upstream. This profile does not substitute IMPUTE2 for papers using IMPUTE4 or IMPUTE5.

**Runtime, licence and privacy:** the original binary and raw vendor traces are excluded from GitHub. Review the actual Oxford Genetics Software Suite licence bundled in the [official v2.3.2 archive](https://mathgen.stats.ox.ac.uk/impute/impute_v2.3.2_x86_64_static.tgz) before accepting its terms. The selected private academic runtime uses the 2007 bundled terms; the current generic website licence is a different document. The project user's acceptance does not accept terms on behalf of other users or authorize commercial use. Preserve the original `LICENCE` beside the unmodified executable. Setup must be permitted in your operating environment; otherwise skip the dependent analysis.

Use Linux x86_64 Java 17 or newer, including Java inside WSL on Windows. `java -jar ... impute-tools --help` shows the request contract. The binary is not included or installed by an analysis. An unavailable, forbidden or wrong-version dependency produces **SKIP3**; fix the named dependency or retain the skipped analysis. Input, process, deadline and audit failures produce **FAIL2**; read `FAILURE.md`, preserve the raw/partial files and retry with a new output path. Both conditions are printed prominently to stderr. **A failed or skipped capture supplies no completed result.**

## First imputation

Save the following JSON with paths relative to its own directory, after reviewing the assembly, autosomal diploidy, participant column order, cohort/reference eligibility and strand file. The example does not certify these assumptions for your study. All paths must resolve to physical locations; the output must be new and disjoint from the runtime and retained evidence.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "profile": "impute2-2.3.2-static-autosomal-v1",
  "operation": "IMPUTE_GENOTYPES",
  "runtime_home": "/private/impute_v2.3.2_x86_64_static",
  "out": "imputation-run-1",
  "assembly": "GRCh37",
  "chromosome": "22",
  "license_accepted": true,
  "inputs": {
    "map": "inputs/chr22.map",
    "reference_haps": "inputs/reference.hap.gz",
    "reference_legend": "inputs/reference.legend.gz",
    "study_genotypes": "inputs/study.gen.gz",
    "strand": "inputs/study.strand"
  },
  "options": {
    "seed": 1729,
    "Ne": 20000,
    "interval_start": 20400000,
    "interval_end": 20500000,
    "parameter_policy": "original-v2.3.2-defaults",
    "sample_identity_policy": "unlabelled-ordered-columns",
    "input_reviewed": true,
    "genotype_audit": "summary",
    "typed_genotype_policy": "preserve-input"
  },
  "limits": {"timeout_seconds": 3600}
}
```

```sh
java -Xmx2g -jar /path/to/omicsmatic-0.4.0.jar impute-tools --request imputation.json
```

Always read `WARNINGS.md`, `input-audit.json`, `scientific-audit.json` and the original `worker/result_warnings` and `worker/result_summary`. Retain the exact producer JAR: saved captures intentionally require that complete JAR, including all its classes. A later JAR does not refresh or replace a historical producer identity.

## Operations and policies

| Operation | Exact input roles | Original operation |
| --- | --- | --- |
| `PREPHASE` | `map`, `study_genotypes` | `-prephase_g`; outputs study HAPS and relative phase confidence as well as the original GEN/INFO |
| `IMPUTE_GENOTYPES` | `map`, `reference_haps`, `reference_legend`, `study_genotypes`, `strand` | Imputation from unphased genotype probabilities |
| `IMPUTE_PREPHASED` | `map`, `reference_haps`, `reference_legend`, `study_haps`, `strand` | `-use_prephased_g -known_haps_g`; consumes actual phased haplotypes |
| `MERGE_REFERENCE` | `map`, `reference_haps`, `reference_legend`, `second_reference_haps`, `second_reference_legend` | `-merge_ref_panels_output_ref`; produces a merged HAPS/legend panel for a subsequent imputation |

All operations require the common options in the example. Only `IMPUTE_GENOTYPES` accepts `typed_genotype_policy`. Choose `preserve-input` for original default typed-call behavior, `reimpute-all` for `-pgs`, or `fill-missing-at-source-threshold` for `-pgs_miss` with the original calling threshold. The latter carries the upstream caution about downstream association testing. Missing typed calls remain missing under `preserve-input`; successful imputation does not imply they were filled. See the [official IMPUTE2 documentation](https://mathgen.stats.ox.ac.uk/impute/impute_v2.html).

The original MCMC/template/buffer defaults are retained; this initial profile accepts no arbitrary upstream arguments or estimator reductions. PREPHASE preserves the study's strand coding. Supply the explicit strand file once during the later imputation; do not apply an additional Java allele flip. Original strand processing and mismatches remain in the private raw diagnostics. Two-panel merging and the subsequent imputation are separate requests, preserving both original captures.

Input parsing streams plain or gzip files, detects compression from bytes and retains every supplied byte. GEN has five metadata fields and three probabilities per sample. Study HAPS has five metadata fields and two literal phased calls per sample; reference HAPS has one row per legend variant. IDs and duplicate positions with different alleles are preserved. This profile uses explicit **unlabelled ordered columns**: column ordinals are not invented participant IDs and do not establish independence. Map extrapolation remains an original-software behavior; Java does not invent coordinates.

## Probability and field interpretation

GEN probabilities describe zero, one and two copies of the file's allele1, which need not be genome ALT. All-zero GEN vectors are format-specific missing codes and never yield dosage zero. Input non-unit sums are reported and passed unchanged to the original program. Output probabilities are declared at the original three-decimal precision; Java never silently renormalizes them. INFO, per-sample QC, negative-one sentinels, `NaN`/`Inf` fields and original warnings stay in their raw files. A missing or undefined metric is not a zero, and IMPUTE INFO is not Minimac Rsq. No post-QC filtering is inferred.

**Probability bounds are conditional, and unit mass is not established by the printed tokens.** Original v2.3.2 example runs at 3, 6 and 9 decimal places falsified a nearest-only rounding assumption: at three places, values as high as 0.000999494 were printed as zero. The comparisons support censoring values below one output unit to zero, then nearest rounding of nonzero values. This is an empirical format control on the retained example and seed, not a formal proof for every input. Typed calls can deliberately carry subunit mass. A raw GEN vector's compatibility with the unit-mass model does not prove that model is appropriate for it. The first development qualification's nearest-only bounds are retained as superseded and are not admitted for use.

`genotype_audit: "summary"` streams variant-level counts and exceptional-genotype eligibility. `"rounding-bounds"` additionally writes `genotype-bounds.tsv`, with explicitly named **conditional unit-mass** allele1 dosage, posterior variance and maximum-probability bounds under this zero-censor contract. It intersects the declared component intervals with the unit-mass constraint. It does not reconstruct the original posterior, establish unit mass, restore a caller's missing/outlier probability or measure biological accuracy. Read `OUTCOME-WARNINGS.md`; use these mathematical bounds scientifically only after establishing their premises for your analysis. Missing, invalid or infeasible vectors receive an explicit status in `unreportable-genotypes.tsv` and no invented bounds. A bound being unavailable does not invalidate the preserved upstream operation or justify repairing its output.

This detailed mode costs more time and disk space per genotype; use summary mode for large runs and choose explicit resource limits. The native API `RoundedGenotypeProbabilities.bounds(p0, p1, p2, decimalPlaces)` uses ordinary nearest rounding by default. Its overload accepts the explicit `Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING` contract used by this adapter. Both APIs assume unit mass; neither infers the right contract from printed tokens.

Phase confidence refers to the current and previous heterozygote. Homozygote `*` fields and the first-heterozygote convention remain explicit; they do not establish independent phasing accuracy.

## Saved checks and fresh refits

```sh
java -jar /path/to/exact-producer.jar impute-tools --inspect imputation-run-1
```

`--inspect` checks the saved closure and exact producer identity. It runs no new imputation and does not authenticate execution against coordinated replacement of an entire capture.

```json
{
  "schema_version": 1,
  "mode": "VERIFY",
  "analysis": "imputation-run-1",
  "out": "imputation-verify-1"
}
```

Run this with `--request verify.json`. `REPLAY` uses the same fields. Both modes actually rerun the original estimator using the parent's captured inputs, create a new disjoint capture, compare every declared scientific file and preserve the parent. They do not depend on original external input files remaining at their old paths. An optional `runtime_home` can restore the same exact permitted binary/license at another physical location. Dynamic command paths and timing fields remain retained but are excluded from scientific byte comparisons. Refit agreement is computational reproducibility under this profile, not empirical accuracy, biological validation or an independent implementation of IMPUTE's estimator.

The current profile excludes chromosome X/PAR, other ploidies, IMPUTE4/5, genotype-format conversion, original unphased-reference workflows, sample exclusion and whole-paper admission. Unsupported analyses must be kept explicitly skipped or pending rather than silently routed to this profile.
