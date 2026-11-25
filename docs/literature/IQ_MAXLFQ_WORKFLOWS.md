# Native iq MaxLFQ protein aggregation

The source-built `iq-tools` command implements the `iq` 2.3.0 `fast_MaxLFQ`
aggregation role in P168. It combines prepared ion/precursor measurements into
relative protein quantities using sample-pair median ratios and connected
components. It runs entirely in Java on Windows/Linux; R and DIA-NN are not
required for this aggregation command.

**Warnings and required action:** input quantities must already be normalized
**log2 intensities**. Record the actual filtering and normalization in the
request. This command does not infer FDR filters, convert raw intensities,
impute values, or implement the Proteograph/PepCal preparation. Read each result's
`WARNINGS.md`, `OUTPUT-AUDIT.json` and complete row/protein/sample ledgers.
Older release JARs do not contain this source-built addition. The native command
was checked against33 unmodified-original synthetic tables,24 focused Java
tests on each of Windows/Linux, and complete saved captures and foreign-host
recomputation. These controls do not reproduce the study.

Build with Java17 or later and create a request template:

```shell
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.1.jar iq-tools --profiles
java -jar build/cli/omicsmatic-0.4.1.jar iq-tools --example > iq-request.json
```

On Windows use `gradlew.bat` and write UTF-8 explicitly:

```powershell
java -jar build/cli/omicsmatic-0.4.1.jar iq-tools --example |
    Set-Content -LiteralPath iq-request.json -Encoding utf8
```

Edit the request's absolute input path, column mappings, actual preparation
description and capacities. A prepared tab-separated input might contain:

```text
protein	sample	ion	normalized_log2
ProteinA	Sample1	Peptide1_2+	20.0
ProteinA	Sample2	Peptide1_2+	21.0
ProteinA	Sample1	Peptide2_2+	22.0
ProteinA	Sample2	Peptide2_2+	23.0
```

Ion identity is supplied explicitly: combine the peptide/fragment/charge
attributes appropriate to your experiment before analysis. Labels are literal;
no delimiter-based identity construction, trimming or name repair is inferred.
Distinct role columns are required. Declare compression (`none` or `gzip`) and
separator (`tab`, `comma` or `whitespace`) explicitly. Tables are strict UTF-8,
with optional BOM and bounded quoted records. Blank physical records are skipped;
all original bytes and every parsed row, including unused columns, are retained.

Run with a new output directory and enough heap for the complete population:

```shell
java -Xmx8g -jar build/cli/omicsmatic-0.4.1.jar iq-tools --request /absolute/iq-request.json --out /absolute/NEW_iq_result
java -Xmx8g -jar build/cli/omicsmatic-0.4.1.jar iq-tools --verify /absolute/NEW_iq_result
```

The parent of the new output directory must already exist. On Windows substitute
canonical absolute Windows paths. Examples are templates, not recovered author
settings or guaranteed heap sizes. Rows, proteins, samples, ions, per-protein
matrix/pair populations, numeric memory estimates, input/decoded/output bytes,
numeric scans/work and cooperative time/iteration limits are explicit. An
inadequate limit fails without truncation. This in-memory command does not
implement iq's separate streaming formats.

`outputs/proteins.tsv` is **long format**, with one row for every protein/sample
pair, including missing quantities. Its fields are `protein`, `sample`,
`normalized_log2`, `component_zero_based` and `source_annotation`. Long format
avoids collisions between literal sample labels and metadata headers.
`PROTEINS.jsonl` also retains complete estimate/component arrays, ion identities,
observed counts, duplicate counts, pairwise-ratio counts and convergence evidence.
`ROWS.jsonl` preserves every parsed input row and its duplicate disposition;
`SAMPLES.jsonl` records the complete first-observed sample order.

Duplicate measurements for the same protein/ion/sample sum in **linear space**
before conversion back to log2, as iq2.3.0 does. Older iq1.9.12 code overwrites
duplicate cells; this command does not silently substitute that behavior.
Zero and negative log2 quantities are valid. Missing or infinite input quantities
are refused instead of silently removed. Duplicate-sum overflow/underflow fails.

Each eligible sample pair contributes one median log ratio, regardless of how
many ions it shares. Components are shifted to their observed-cell mean;
isolated samples use their ion median. A single-ion protein follows the source
shortcut. Unobserved protein/sample quantities remain `NA`. The source annotation
is empty for one quantified component, or contains one-based component labels
and `NA` for disconnected/missing samples. Raw zero-based component labels are
also retained; missing sample positions can leave gaps in these labels.

The native bit-graph conjugate-gradient solver follows the iq2.3.0 squared
relative threshold1e-20 and maximum10*(samples+1) iterations, subject to any
lower declared iteration bound. It additionally recomputes the final residual.
Breakdown or nonconvergence is a failure; no convenient best iterate is admitted.
`--verify` checks every file/directory and byte, requires the exact producing
JAR, and numerically recomputes all saved results from captured original inputs.
Original host input paths are not accessed during saved verification.

[P168](https://doi.org/10.1038/s41588-026-02697-6) reports `fast_MaxLFQ` from the
R `iq` package after external PepCal normalization. The iq edition and complete
aggregation inputs are unreported. This implementation is checked against an
explicitly selected current source behavior; it does not establish the author
version or reproduce the full study. DIA-NN search, variant-library construction,
PepCal extraction/smoothing, and limma plate correction remain separate.

Reference sources are the [official iq repository](https://github.com/tvpham/iq)
and [CRAN package](https://cran.r-project.org/package=iq). The original algorithm
is BSD-3-Clause; its attribution/notice is included in the source and executable.
The public Java API is `org.jlinalg.proteomics.MaxLfq`.

**SKIP3:** the named qualified executable prerequisite is unavailable; use the
complete source-built JAR or retain the skipped analysis. **FAIL2:** no complete
result for invalid/changed input, insufficient capacity, cancellation,
arithmetic, nonconvergence or finalization failure. Preserve the attempt,
correct its reported cause and use new paths. No full-package equivalence,
historical paper reproduction or whole-project completion is claimed.
