# Native Java column-wise quantile normalization

**AVAILABLE IN THE VALIDATED LOCAL EXECUTABLE.** Native qnorm passed the full
1,461-test gate with zero failures/errors and five optional skips. All 55
required suites executed without skips. The actual JAR passed 20 qnorm CLI
runs/five checks; all 23 prior executable verifier groups passed on the same JAR. The local artifact
is `build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`5680cbc4e457007f8903d1d4db3e74ecc890a150b350870f0b82d5650bdc0fec`.
This is an unreleased task artifact. See [native validation](qnorm-native-validation.json)
and [packaged checks](qnorm-native-packaged-validation.json).

**OUTPUT IS CONTINUOUS NORMALIZED DATA, NOT RAW COUNTS.** Review whether matching
column distributions is scientifically appropriate. Do not pass this output to
a model that requires unmodified integer counts. `WARNINGS.md`, the console and
the machine-readable analysis status retain this warning on successful runs.

Rows are shared features/observations and selected columns are samples. The
native `QuantileNormalization.normalizeColumns` API fits the mean of each rank
across columns or uses a supplied target. Each tie receives the average of its
occupied target ranks. Different tie patterns can therefore leave different
marginal distributions. Input arrays and result accessors are isolated copies.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar quantile-normalize --matrix methylation.tsv --id-column feature --columns sample1,sample2 --axis columns --input-kind continuous --out output/quantile-normalized
```

Use `raw-counts` only for exact nonnegative integer text in
`0..9007199254740991`. Larger integers and fractional text that would round to
an integer double fail explicitly. `estimated-counts` permits nonnegative
fractional values. `continuous` also permits negative finite values. Missing,
NaN and infinite values require an explicit upstream preprocessing decision.
Finite rank or tie sums that overflow fail with a rescaling action.

The output retains exact feature IDs, row order and the requested sample-column
order. The tabular `target.tsv` contains one sorted target per rank; those rows
are quantiles, not feature annotations. To apply an existing target:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar quantile-normalize --matrix held-out.tsv --id-column feature --columns heldout1,heldout2 --axis columns --input-kind continuous --target output/training/target.tsv --target-column target --out output/held-out-normalized
```

For held-out evaluation, fit the target using training columns and apply it to
the same feature universe in held-out samples. Equal row counts alone do not
establish matching feature universes. A supplied target is sorted by numeric
value; its row IDs are not matched to matrix features.

Set `--max-cells` and `--max-bytes` explicitly if the default resource limits are
inappropriate. Always use a new output directory. Source hashes are checked
before publication. Invalid inputs produce a failed bundle with recovery steps
and retained partials, without a successful `results.tsv`.

The validated mathematical scope is qnorm 0.8.0's **finite float64 ndarray
column normalization (`axis=1`, one CPU)**. The 500 frozen actual-original cases
cover 61,403 values, including ties and supplied targets. Java double arithmetic
does not promise bitwise NumPy/Numba equivalence. qnorm's float32/int32 casting,
ndarray `axis=0` transpose behavior, pandas, incremental and multiprocessing APIs
are outside this implementation. Cast original reference inputs to float64
when comparing this subset. Row normalization is explicitly rejected.

This is distinct from R's inverse-normal `qnorm` function. P061's transcript-body
methylation and boundary-probability normalization is catalogued separately from
its permutation tests, normal-tail approximation, FDR, Pearson tests and GO
background. Those downstream procedures are not supplied by quantile normalization.

The original MIT license and attribution are packaged in `META-INF`.
`tools/literature/generate_qnorm_references.py` invokes the actual original
function, and `tools/literature/verify_qnorm_native.py` checks the executable
without requiring Python or qnorm during the Java analyses.
