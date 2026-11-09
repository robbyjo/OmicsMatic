# Native MR-MEGA GWAS workflow

**A standalone native workflow is implemented and reference-tested. Registration
in the project's main command dispatcher and literature catalog is still pending.**
MR-MEGA therefore has no new catalog availability credit at this checkpoint.
The catalog remains at 91 new scoped integrations, 94 including existing tools,
and 250 missing tools. The full 250-paper implementation goal remains unfinished.

The independently authored Java implementation reads cohort GWAS files, aligns
exact/swapped alleles, infers ancestry axes or reads supplied axes, performs
MR-MEGA's residual-scaled weighted regression, and optionally applies input and
output genomic control (GC). Native calculations require Java and the project's
existing JDistlib dependency. **No original MR-MEGA, compiler, R, download or
external installation is needed for user analysis.** R and the original C++
program were used as separate validation references.

## Quick start

Create `mrmega-settings.json`. Every setting below is required; axis count and
trait type are explicit choices rather than inferred defaults:

```json
{
  "schema_version": 1,
  "trait": "quantitative",
  "axes": 3,
  "ancestry": "inferred",
  "no_alleles": false,
  "input_gc": false,
  "output_gc": false,
  "standard_names": true,
  "aliases": {},
  "filters": []
}
```

Create `cohorts.in` with one cohort path per line, in the intended cohort order.
Relative paths resolve against the process working directory. Whitespace in
cohort paths is unsupported by the source file-list format. All files must be
readable regular files; symlinks and special files are refused.

```text
D:/analysis/cohort1.tsv
D:/analysis/cohort2.tsv
D:/analysis/cohort3.tsv
D:/analysis/cohort4.tsv
D:/analysis/cohort5.tsv
D:/analysis/cohort6.tsv
```

Quantitative cohort files require these whitespace-delimited columns. Header
matching is case-insensitive; allele values are case-sensitive:

```text
MARKERNAME CHROMOSOME POSITION EA NEA EAF N BETA SE
rs10001 1 1000100 A G 0.35 1200 0.031 0.014
rs10002 1 2000100 C T 0.42 1200 -0.022 0.018
```

Use a NEW output directory whose parent already exists. From the repository
root, Windows PowerShell can run the thin development jar with the recorded
project runtime:

```powershell
java -cp "docs/literature/mrmega-workflow-development/mrmega-workflow-javac21.jar;build/cli/omicsmatic-0.3.7.jar" org.jlinalg.cli.MrMegaWorkflowCli --settings mrmega-settings.json --file-list cohorts.in --out D:/analysis/mrmega-run1
```

On Linux, replace the classpath semicolon with a colon and use Linux paths.
These jars target Java17; actual execution was tested on JVM21/JVM25. They are
development components that need the separately recorded project runtime, not
self-contained replacement distributions. The canonical executable is unchanged.

An inferred-ancestry analysis needs enough complete common-frequency variants
to resolve the requested positive axes. A variant needs at least `axes + 3`
participating cohorts to be fitted. Every cohort needs at least two source
chi-square rows because the original lambda median is undefined below two.
The small table above illustrates the format, not a sufficient ancestry panel.

## Other supported choices

For supplied axes, set `"ancestry": "precalculated"`. Each file-list line must
then contain its cohort path followed by exactly `axes` finite coordinates.
Blank lines are refused because they misalign the original physical-line PC
reader. Explicit intercept-only analysis uses `axes: 0` with precalculated mode.

For binary traits, set `"trait": "binary"` and provide `OR`, `OR_95L`, `OR_95U`
instead of `BETA`, `SE`. Conversion uses `log(OR)` and
`(log(OR) - log(OR_95L)) / 1.96`. The upper confidence-bound column is mandatory
and must be numeric and finite, but does not enter the original calculation.
No symmetric-interval or P-value-to-SE substitution is made.

Input/list filenames ending in `gz` are read as gzip, matching the source suffix
rule. UTF-8 decoding errors, corrupt gzip files and truncated rows fail clearly.
Custom headers use canonical column keys, for example
`"aliases": {"MARKERNAME": ["SNP"], "BETA": ["EFFECT"]}`. With
`standard_names: false`, aliases must define every required column. The last
matching header wins, with a warning if several matches exist.

Filters are explicit numeric conditions, for example:

```json
"filters": [{"column": "INFO", "operator": ">", "value": 0.7}]
```

Operators are `==`, `!=`, `>=`, `<=`, `>`, `<`. **A filter absent from a cohort's
header is not applied in that cohort**, matching the original. This produces a
warning; check the cohort headers if the restriction must apply everywhere.
Filtered rows are recorded before effect parsing, so filtered malformed effects
do not become artificial zero effects.

## Warnings, failures and required actions

**Always read `WARNINGS.md`, `run.json` and the row/variant dispositions.**
Exit code zero means the declared workflow completed with its recorded warnings.
Exit code two means no result table was admitted. Correct the named cause and
rerun to a NEW output directory. Existing outputs are preserved. Failed partials
stay in `partial/`; they are evidence, not successful analysis results. The
result table is published last, after receipts and supporting files are written.

Source-compatible behavior that can change interpretation is explicit:

- Exact/swapped alleles are accepted; swaps change frequency and beta direction.
  No strand complement, genome-build conversion or identifier repair is inferred.
  Literal `N` participates in the upstream initialization-sentinel behavior; use
  actual consistent alleles rather than treating `N` as a generic missing value.
- **Avoid `no_alleles: true` unless the original behavior is intended.** Source0.2
  retains the first/new-marker beta as supplied, then negates later occurrences
  because their empty allele label differs from stored `N`. This behavior is
  preserved with a prominent warning; supply allele columns to avoid it.
- Duplicates overwrite earlier cohort values and may contribute multiple lambda
  rows. Rejected existing-marker rows can still update their sample count before
  allele/frequency rejection. Every occurrence has a row disposition.
- Chromosome/position conflicts keep the first valid coordinate with warnings.
  Unknown/invalid source coordinate codes are not converted to a different build.
- Frequencies, beta and SE are stored as float32. GC-adjusted SE is again stored
  as float32, and the reciprocal is computed as float32 before squaring in double.
  Weighted EAF also retains the original float32 multiplication boundary.
- The source odd-count lambda median uses sorted index `floor(n/2) - 1`, rather
  than the conventional middle value. Input lambda divides by literal
  `0.4549364`; input GC applies only above one. Output GC applies even below one
  and changes only association chi-square/probability. Raw association, adjusted
  association and unchanged lnBF are kept separately.
- Inferred ancestry preserves the source selection rules described in
  [the ancestry component notes](MRMEGA_ANCESTRY_DEVELOPMENT.md), including its
  observed bin-300 alias behavior and zero-ordinal omission. Coordinate signs
  follow the native deterministic convention; comparison-only sign maps never
  overwrite either original or native results.

Malformed/nonfinite numeric tokens, negative N, nonpositive/underflowing SE,
beta's reserved `-666` sentinel, integer overflow, unresolved/tied ancestry
cutoffs and singular regression models are refused explicitly. Native code does
not reproduce unsafe `atof`/`atoi` coercion or undefined array/inversion results.
`SmallCohortCount` rows stay in the result table with `NA` tests. A numerical
failure retains diagnostics in `partial/` and prevents result admission.

**Native P fields are stable chi-square survival probabilities.** They are named
`stable_output_association_P`, `stable_ancestry_P`, `stable_residual_P`; they are
not original subtraction-approximation fields. Source zero-statistic undefined
boundaries remain `NaN`. Original output P fields remain separately archived.

## Outputs and validation

`results.tsv` keeps every admitted source-ordered marker, metadata, coefficients,
SEs, raw/output association, both heterogeneity tests, lnBF and status.
`cohorts.tsv`, `rows.tsv`, `prepared.tsv`, `used.tsv`, `coordinates.tsv`,
`covariance.tsv` and `fit-diagnostics.tsv` retain cohort lambdas, row decisions,
stored values, participating effects/weights, axes and complete fit diagnostics.
Inferred mode also writes ancestry selection/disposition ledgers. `run.json`
records input SHA-256 pins, settings choices through the saved `settings.json`,
counts and status. Input pins are checked after ingestion and again before
publishing. SHA-256 provides integrity checks, not authentication.

Twenty-two complete synthetic GWAS families ran through the unchanged original
MR-MEGA0.2 CLI. Two compiler builds on three JVMs passed 132 complete workflow
comparisons, including 4,134 full-precision fitted variants and exact stored
frequencies/effects/weights from original public parser/marker APIs. All 12,402
native probability fields matched a separately executed base-R reference.
Twenty-four real CLI refusal families plus an analytic odd-median control passed
on both builds and all three JVMs: 144 refusals and six analytic comparisons.

Frozen verification checks the retained source bindings, populations and numeric
relationships; it does not rerun Java/C++/R, independently refit current user data,
authenticate past execution or reproduce a whole paper. Original source, ZIP,
objects and binaries are not redistributed. If original-reference regeneration
needs a download/compiler/runtime unavailable or forbidden in the environment,
skip that dependent validation, retain its warning/failure receipt, and supply
the named prerequisite only on a permitted host. This does not add an external
dependency to native user analysis.
