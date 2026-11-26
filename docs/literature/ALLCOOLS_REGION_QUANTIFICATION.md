# Native ALLCools region quantification

`allcools-region-quantify` calculates ALLCools 1.0.8 count, hypo-score and hyper-score outputs from complete **prepared raw region counts**. It runs entirely in Java. This extends the existing ALLCools family; it adds no family or paper-review credit. The original ALLC/Tabix/Zarr dataset adapter is still pending.

**Warnings and required actions:** uncovered regions receive hyper-score **1**. Count outputs saturate at **4,294,967,295**, which can change methylation ratios. Scores use the **uncapped** counts. Read `WARNINGS.md`, `OUTPUT-AUDIT.json` and `outputs/pools.tsv` before interpreting a completed run. A hyper-score of 1 at zero coverage supplies no methylation evidence.

Use the executable JAR built from this source. The canonical released binary has not been replaced by this source change. No external software, download, registration or licence acceptance is needed for native prepared-count quantification.

```text
java -jar omicsmatic-<version>.jar allcools-region-quantify --help
java -jar omicsmatic-<version>.jar allcools-region-quantify --example
java -jar omicsmatic-<version>.jar allcools-region-quantify --request request.json --out NEW_CAPTURE
java -jar omicsmatic-<version>.jar allcools-region-quantify --verify COMPLETE_CAPTURE
```

Save the `--example` JSON as `request.json`. Replace `input.counts` with the canonical absolute path to your count table, `preparation` with its actual provenance, and the three `dimensions` with the complete population sizes. Set both score cutoffs and the input/output byte and wall-time capacities explicitly. Use a new output directory for every attempt.

The UTF-8 input has five literal tab-separated fields, with this exact header:

```text
cell	mc_type	feature	mc	cov
cellA	CGN	region1	2	5
cellA	CGN	region2	6000000000	8000000000
cellA	CGN	region3	0	0
```

For this example, declare one cell, one methylation type and three features. A literal tab separates columns; quoting does not create escaped fields. Each count is a nonnegative int64 integer and `mc <= cov`. Complete axes must appear in cell, then methylation-type, then feature order. Every group must contain the same ordered unique features; every cell must contain the same ordered unique types. Type names are labels for already aggregated counts. This command does not execute an IUPAC query or construct regions. Counts from overlapping regions or patterns must retain their actual preparation semantics.

For each cell/type, the method computes `p = sum(raw_mc) / (sum(raw_cov) + 1e-6)`. A region's initial hypo-score is the strict binomial survival `P(X > raw_mc)`, except `raw_cov <= raw_mc` gives zero, including `0/0`. The survival is rounded to binary16. The hyper-score then computes `1 - survival` in binary16, before either threshold is applied. Each cutoff also follows NumPy 1.23.5 binary16 scalar promotion on `[0,1]`; a value equal to the rounded cutoff is retained. This order matters near cutoff and rounding boundaries.

Outputs retain raw and saturated counts, both scores, full pooled totals and saturation/zero-coverage counts. `outputs/posterior-counts.tsv` contains the saturated uint32 counts in the exact input format of [the native posterior command](ALLCOOLS_POSTERIOR.md). To use it, create a separate `allcools-posterior --example` request with that file as `input.counts`, matching complete dimensions and explicit posterior settings. Require a separate completed and verified posterior capture. Do not calculate scores from that saturated export.

An unsuccessful calculation exits **FAIL2** and retains its failure trace without a completed result. Fix the named invalid input, integer-sum overflow, dimensions, numeric, cancellation or capacity failure, then retry at new paths. A missing or forbidden input/producer exits **SKIP3** and skips the dependent calculation; restore the permitted prerequisite before retrying. Preserve original inputs, producing JARs and failed attempts. Saved verification replays every numeric output from captured input bytes, including the posterior count export, and needs the exact producing JAR. Original input paths may be absent on a different replay host.

The binomial implementation is JDistlib; the retained original-source comparison tests use unchanged ALLCools 1.0.8 with SciPy 1.10.1 and NumPy 1.23.5. Binary16 output agreement on the tested cases does not establish equivalence for every extreme or rounding boundary. Invalid native probabilities fail explicitly. Integer-sum overflow is refused rather than reproducing NumPy int64 wraparound. Complete region axes must fit in memory; the declared wall limit is cooperative. Hashes check integrity and do not establish biological validity.

The upstream dataset probe also retained three source failures: three-column BED automatic IDs fail with pandas 1.5.3; a one-sample CLI table fails after `squeeze`; score calculation with a custom observation dimension fails because the source selects `cell`. A deliberately selected Python Series interface supports one sample; it is not an automatic fallback for the CLI. Four-column BED and bin datasets passed all three original quantifiers in the retained probe. These are upstream behavior observations, not qualification of a public dataset adapter. The adapter, clustering, integration, BaseDS and DMR workflows remain pending.

Source: [ALLCools v1.0.8 dataset.py](https://github.com/lhqing/ALLCools/blob/v1.0.8/ALLCools/count_matrix/dataset.py), commit `2a77f30bf911726de10fe6cc4971331f023d9e68`. The original MIT notice is retained in the packaged methylation resources. Synthetic fixtures can be reproduced with `tools/literature/generate_allcools_region_reference.py --out NEW_DIRECTORY` in the separately permitted exact upstream environment. Validation details are recorded beside this guide in `allcools-region-validation/QUALIFICATION.json`.
