# Reproduce prepared FastQTL validation

These are artificial matrices: 32 named samples, 16 variants, four phenotypes
and two numeric covariates. VCF/covariate columns are deliberately reordered;
DS fixtures include contradictory GT to exercise the source's DS precedence.
Missing genotype/phenotype values, ties, a no-cis phenotype and a filtered
monomorphic variant are included. `fixtures/FILES.json` records exact input
identities; BGZF and TBI files were made with the author's bundled Tabix source.
`controls/` contains deliberate constant/foreign-index inputs.

Use the **complete current source-built executable JAR** and a private runtime
staged by [the installer](../install_fastqtl_runtime.py). Linux x86_64 is required
for actual FastQTL execution. This is synthetic workflow validation, not a
paper reanalysis or evidence of biological calibration.

```sh
python3 tools/literature/fastqtl_workflow/validate.py \
  --jar "$PWD/build/cli/omicsmatic-0.4.0.jar" \
  --runtime-home "$PWD/build/fastqtl-runtime" \
  --out "$PWD/build/fastqtl-validation"
```

The harness checks eight nominal/fixed/adaptive cases against separate direct
calls to the same pinned distribution executable. It checks saved inspection,
two fresh upstream VERIFY/REPLAY computations, and fifteen actual refusal
controls, including nonfinite source output, a one-second owned-process timeout,
foreign indexes and forged scientific summaries with refreshed metadata hashes.
Deliberately altered control captures remain failed evidence. Primary completed
captures and fresh-refit parents are preserved. Read `RESULT.json` and every
failure/warning file. An assertion or nonzero process leaves validation failed.

For an independent nominal numerical comparison with base R:

```sh
Rscript tools/literature/fastqtl_workflow/reference.R \
  tools/literature/fastqtl_workflow/fixtures \
  build/fastqtl-validation build/fastqtl-R-reference
```

This checks 240 P/slope/SE values from GT and DS inputs against `lm`, and verifies
minor-allele summaries and coordinate identities. R uses double arithmetic;
FastQTL's float vectors and six-significant-digit printing motivate the declared
`1e-12 + 5e-5 * abs(R_reference)` tolerance. Direct-executable comparisons remain
byte-exact. Other editions and all scientific input distributions are outside
this finite qualification.

The Java Gradle tests additionally cover contracts, the paired-index cursor
regression, bounded text, loose-class refusal, and process ownership helpers.
Native Windows is tested for an explicit skipped upstream analysis, not an
upstream Windows fit. Staging success or `--help` output alone is not scientific
validation. SHA-256 records integrity, not execution authentication.
