# Native smartpca PCA workflows

Use this prepared-data workflow for human-autosomal EIGENSTRAT PCA, iterative
outlier removal, ordinary or least-squares projection, and perturbation
`shrinkmode`. It implements selected behavior from EIG **v7.2.1**, source commit
`4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1`, program identifier **16000**.
The package edition and program identifier are distinct. Computation uses native
Java FP64 CPU methods. No external EIGENSOFT installation is required.

**Build from current source.** Existing release assets may predate this entry
point. From the project root, build the complete JAR using the repository's
normal Java/Gradle instructions, then invoke:

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --help
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --request examples/smartpca-native/run.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --verify build/smartpca-native-example
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --request examples/smartpca-native/replay.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --verify build/smartpca-replay-example
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --request examples/smartpca-native/shrinkage.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --verify build/smartpca-shrinkage-example
```

The example inputs are synthetic and retain the exact inputs of one original-C
reference case. Every output directory must be NEW and have an existing physical
parent directory. Preserve a previous run; copy the request and change `out` to
run it again. Paths in JSON resolve relative to that JSON file. REPLAY first
verifies its parent, then recalculates into a disjoint new directory. VERIFY is
read-only and recomputes the complete declared scientific calculation, including
all coordinate chunks, marker tables, outlier passes and shrinkage diagnostics.
Keep the exact producer JAR recorded by each capture for future VERIFY/REPLAY.

## Required input and explicit choices

Provide three complete UTF-8 EIGENSTRAT text files:

- `.geno`: one marker per row and one `0`, `1`, `2` or `9` hard-call character per
  individual. `9` is missing. Row count and width must exactly match the metadata.
  The workflow does not coerce dosages, infer alleles, impute or merge files.
- `.ind`: exactly three whitespace-separated fields per noncomment row:
  unique literal individual ID, sex annotation and population. Literal IDs
  `NA`, `NaN` and `Inf` remain identifiers. `Ignore` is a source-reserved population
  whose separate source branch is not qualified by this file profile; it is
  refused with instructions rather than renamed or silently removed.
- `.snp`: four fields (unique marker ID, numeric chromosome, genetic position,
  physical position), or six fields with both allele annotations. Positions
  must be finite and nonnegative. Rows must already be ordered by numeric
  chromosome and physical position, consistently with `.geno`. No implicit
  sorting or `chr`-prefix conversion is performed. Only numeric chromosomes
  1 through 22 enter this declared human-autosomal PCA; excluded rows remain
  captured, are still validated, and appear with explicit status in the marker
  table. Allele annotations remain literal and are not harmonized.

IDs and population names are limited to 39 UTF-8 bytes and cannot contain
whitespace/control characters. Input order is preserved. Explicitly declare
`reference_populations` as an array of population names to fit a reference basis,
or `null` to fit all individuals. Every named group must exist; at least two
fitting individuals and more individuals than requested axes are needed.
This file profile requires at least two fitting populations for `shrinkmode`;
its single-population source branch remains unsupported. Do not invent labels to
bypass that restriction. Set `genome_build` to your literal declaration; the
default is `unspecified`, with no inferred build or coordinate conversion.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "engine": "native",
  "profile": "eig-7.2.1-smartpca-16000-native-pca-v1",
  "operation": "PCA",
  "out": "new-pca-output",
  "inputs": {
    "genotypes": "prepared.geno",
    "markers": "prepared.snp",
    "samples": "prepared.ind",
    "reference_populations": ["REF_A", "REF_B"],
    "genome_build": "GRCh38"
  },
  "options": {
    "numoutevec": 10,
    "lsqproject": true,
    "numoutlieriter": 0,
    "shrinkmode": false
  }
}
```

## Source options and limits

Omitted options become explicit source defaults in `canonical-request.json`.
Booleans in JSON are `true`/`false`; source parameter strings such as `YES` are
not accepted as implicit conversions. Unknown options are refused.

| Option | Default | Meaning |
| --- | --- | --- |
| `numoutevec` | 10 | Requested PCA axes; 1 through 1,000, subject to data rank. |
| `usenorm` | true | Source allele normalization. |
| `altnormstyle` | true | Selected source normalization convention; actual C expressions determine behavior. |
| `numoutlieriter` | 5 | Maximum removal passes; zero disables removal. |
| `numoutlierevec` | 10 | Axes examined for outliers, capped by remaining basis size as in source. |
| `outliersigmathresh` | 6 | Positive finite strict deviation cutoff. |
| `outliermode` | 0 | 0: all-sample mean/variance; 1: source leave-one-out rule; 2: disabled. |
| `lsqproject` | false | Least-squares rather than ordinary projection. |
| `shrinkmode` | false | Source perturbation shrinkage; separate from autoshrink/fastshrink. |
| `minallelecnt` | 1 | Source minimum minor-allele count filter. |
| `maxmissing` | 9,999,999 | Maximum missing fitting-basis calls per marker. |

`limits` accepts `maximum_input_bytes` (default 1 GiB per file),
`maximum_total_input_bytes` (2 GiB), `maximum_output_bytes` (512 MiB of numerical
outputs), `maximum_samples` (10,000), `maximum_markers` (1,000,000),
`maximum_matrix_cells` (32,000,000), `maximum_line_bytes` (1 MiB),
`maximum_estimated_work` (2,000,000,000), `maximum_retained_bytes` (1 GiB estimate),
and `timeout_seconds` (1,800). These are full-input and explicit work/storage
admission limits; exceeding them refuses the run instead of subsetting it.
Storage estimates are algorithm estimates, not an operating-system heap quota.
The deadline covers bounded input parsing and calculation in an owned worker;
input capture and final verification/sealing have their own file/byte bounds.
Cancellation interrupts only that worker and requires it to stop before any
admission. Increase a named limit only when the complete declared analysis fits
your available resources. Raw/partial diagnostics remain if a later step fails.

## Reading results

- `coordinates.tsv` contains every original individual, literal ID/population,
  numerical coordinates, source status and `coordinate_fields_admitted`.
  `coordinates/*.json` preserves the same full population in 500-row chunks.
- `eigenvalues.tsv` retains all raw eigenvalues and identifies requested axes.
  Tiny or negative unrequested values remain numerical diagnostics, unclamped.
- `outliers.tsv` records original individual order, iteration, zero-based axis
  and signed deviation. Eigenvector signs are arbitrary; original/native
  reference comparisons account for that sign indeterminacy.
- `markers.tsv` retains all original marker IDs/annotations and final inclusion
  status, including nonautosomal and permanently excluded rows.
- `shrinkage.tsv` retains every perturbation, delta, multiplier and original
  bad-delta branch. Shrinkage scales coordinates across all individuals,
  including projection targets; changing targets can change final scaling.
- `passes/*.json`, `input-summary.json`, `input-samples.tsv` and
  `native-result.json` retain typed diagnostics and input/basis counts.
- `source-request.json`, `canonical-request.json`, bindings, original source
  resources, producer, owned-worker receipt, `capture-files.json` and
  `COMPLETED.json` retain provenance and complete admission evidence. SHA-256
  checks integrity, not authenticity. Keep original inputs and exact producer.

Least-squares targets with insufficient observed markers and ordinary removed
outliers have conditional NaN coordinates and ineligible fields. The source can
return finite shrinkage coordinates for removed outliers; those rows retain a
separate status and remain ineligible. Rows with no observed used markers also
remain ineligible. Undefined singular/repeated-axis shrinkage is refused;
no ridge, pseudoinverse or previous individual's stale coefficient is substituted.

Nonfinite source outlier diagnostics prevent complete admission by default.
An explicit `policies: {"allow_unreportable_fields": true}` can retain that
source diagnostic state in a completed capture; it does not make any affected
coordinate fields eligible. Read every per-row flag before interpreting results.

## Warnings and required actions

**SKIP3 means the dependent analysis was skipped.** Supply the exact permitted
input, capture or complete producer named by the diagnostic. If installation is
prohibited or a prerequisite is unavailable, retain the skip. This Java profile
requires no external smartpca installation.

**FAIL2 means no complete result is admitted.** Preserve raw/original/partial/
failed/undefined/superseded evidence, fix the named input, numerical, work,
storage, deadline or integrity cause, and retry in a NEW directory. An existing
incomplete or altered capture is FAIL2. Require completion, passing VERIFY,
`WARNINGS.md`, `analysis-status.json` and applicable field flags before use.
Never silently repair IDs, replace NA/NaN/Inf, drop individuals, relabel groups,
clamp source values or substitute a different algorithm.

This scope does not implement genotype harmonization/merging, LD pruning, FST,
autoshrink, fastshrink, ancestry assignment, historical-edition equivalence or
whole-paper reproduction. The six catalog roles include different/unreported
editions (v6, 7.2.0, 7.2.1 and literal program 13050); selecting this source does
not reproduce all those environments. FST and guarded catalog admission remain
required in the broader [250-paper handover](HANDOVER_2026-11-23.md).
