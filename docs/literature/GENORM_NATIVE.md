# Native Java geNorm calculations

**P040's input representation remains unresolved.** Its primary Methods report
ACTB and HPRT1 normalization of PLA2G2D and describe a geometric mean of Cq
values. This implementation requires quantities or log2 quantities explicitly.
It does not reinterpret that wording, infer amplification efficiencies, recover
the paper's software edition or reproduce its fold changes. See the retained
[paper review](P040_SOFTWARE_REVIEW.md) and
[primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645638/).

The new Java method implements the geNorm equations for reference-gene
stability, iterative removal of the largest M, pairwise variation between
successive normalization factors, and geometric-mean normalization in log2
space. The method originates in
[Vandesompele et al. (2002)](https://doi.org/10.1186/gb-2002-3-7-research0034).
It includes full diagnostics, target normalization and computational replay.
It uses Java only; running an analysis does not install or launch R.

## Run the supplied example

Build the development source using a supported JDK:

~~~powershell
.\gradlew.bat :executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.GeNormCli `
  --references examples/genorm-native/references.tsv `
  --targets examples/genorm-native/targets.tsv `
  --scale log2-quantity --reference-count 2 `
  --out build/NEW-genorm-example

java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.GeNormCli `
  --verify build/NEW-genorm-example
~~~

This is a standalone class entry point in the development executable JAR.
Use the JAR built from this source, not an older published v0.4.0 asset. The
main command dispatcher and published release assets have not been changed.
`--help` shows the argument syntax. Every analysis needs an explicit scale,
reference count and NEW output directory. Verification uses the captured inputs
and preserves the original bundle; its source files need not remain available.

The example is a small analytic construction, not biological data. Candidates B
and C share best rank one, candidate A is removed first, and V(2/3) is 0.5.
The log2 normalization factors are 0, 1.5 and 3. The normalized log2 target
values are 2, 2.5 and 3. No automatic choice based on 0.15 is made.

Inputs are unquoted UTF-8 TSV files, irrespective of filename extension. Their
first header is exactly `sample_id`, followed by unique, nonblank gene names.
Sample IDs are unique and nonblank. Spaces and Unicode are preserved; quotes,
embedded tabs/newlines and blank data rows are unsupported and rejected. A
target matrix must have exactly the same sample IDs in the same order as the
reference matrix. It uses the same explicitly selected scale. No sample or gene
is silently removed, renamed or reordered to repair an input.

`--scale quantity` means complete, finite, strictly positive relative quantities.
They are explicitly converted to log2 quantities. `--scale log2-quantity` means
complete finite log2 quantities, which may include zero and negative values.
Neither option means Cq/Ct, and `--scale cq` is refused. Positive numbers that
happen to be Cq measurements cannot be identified from their values alone:
the caller must supply the correct representation. No calibration sample,
amplification efficiency or Cq-to-quantity convention is inferred.

## Results and failure handling

* `ranking.tsv`: most to least stable genes, original column numbers and the
  explicit factor-selection indicator. The last two survivors both have rank 1.
* `stability.tsv` and `steps.tsv`: every M, mean M, removal and successive V.
  An exact largest-M tie removes the first remaining input column. Near ties can
  depend on floating-point rounding; inspect the complete trace. The final
  two-gene step has no next V and is labelled `NA_not_applicable`, not a measured
  zero. Starting with two references computes their M and factor only.
* `normalization.tsv`: one log2 factor per sample for the explicitly requested
  number of top-ranked references. All targets use this same selection.
* `normalized-targets-log2.tsv`, when requested: log2 target quantity minus the
  log2 factor. Values are not converted into control-relative fold changes.
* Captured inputs, complete command/runtime fields, `WARNINGS.md`, and
  `COMPLETED.properties`: raw-file hashes and seven implementation-class
  identities. Verification checks the full file inventory and recomputes every
  scientific output, so resealing an altered output hash alone cannot pass.

**FAIL2 means no accepted analysis.** Read the console, `WARNINGS.md` and
`FAILURE.txt` when present. Correct the named cause and rerun into a NEW
directory. Partial files remain provisional. A failure after marker creation
invalidates that marker. Existing output directories are never overwritten.
Do not infer completion from a TSV's presence; require successful verification.
If class identities differ, use the original pinned build or retain a separately
qualified new run. Integrity and replay do not establish authenticity.

Limits are 8 MiB per input, 2..100000 samples, 2..512 candidate references,
at most 10000 target columns, ten million entries per matrix,
samples times squared reference count at most 200 million, and 64 MiB per
bundle file. Non-finite arithmetic is an explicit error. These bounds are not
an operating-system memory quota. Missing values, nonpositive linear
quantities, imputation, efficiency estimation, NormFinder, commercial GUI
behavior and biological validation are outside this profile.

## Source comparison and reproducibility

The reference source is
[NormqPCR 1.58.0, Bioconductor 3.23](https://bioconductor.org/packages/3.23/bioc/html/NormqPCR.html),
upstream source commit `0cc4bdd`, LGPL-3. Its exact official source archive has
SHA-256 `f556aab9ae67d7d02deda192df6d1adf65520b0c9125797582a5b74839864a35`.
The project independently implements the published equations. It tests against
the original `stabMeasureM` and unchanged matrix definition of `selectHKs` with
`method="geNorm", minNrHKs=2, log=TRUE, na.rm=FALSE`. The matrix definition is
selected from the parsed original `setMethod` expression without changing its
body, creating fake S4 classes, or installing the full package. This establishes
a pure matrix-method comparison, not full-package or proprietary-runtime parity.

The 45 synthetic reference cases comprise 44 executions of the original
`selectHKs` matrix method and one two-reference `stabMeasureM` control.
`selectHKs` itself refuses two references; that control's tied ranking and factor
are derived from the equations, not labelled as a returned `selectHKs` result.
The fixtures retain complete rankings, mean M, V, every per-gene step and
normalization factors. The private original R return/function objects, original
sources, conditions, R session, derived two-gene summary and failed attempts
are retained separately. The public test ZIP contains synthetic numeric outputs
and provenance, not original package source or function objects.

The Java profile calculates factors as means of log2 quantities. NormqPCR's
separate `log=FALSE` path calculates products before roots and can overflow.
That literal branch was executed and retained separately; the large-quantity
control produces non-finite V there while the original log-space branch remains
finite. These profiles are not presented as bitwise interchangeable. Java's
finite-precision M/V values are compared with a stated numerical tolerance;
complete rankings and exact tie behavior are checked separately.

Recreate reference fixtures only at NEW paths, using the exact permitted source
archive and R. Original source downloads are not needed for ordinary Java runs:

~~~powershell
python -B tools/literature/genorm_native/prepare_reference.py `
  --archive PATH/NormqPCR_1.58.0.tar.gz `
  --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe" `
  --out build/NEW-genorm-reference `
  --fixture build/NEW-genorm-reference-fixtures.zip

.\gradlew.bat :test --tests org.jlinalg.expression.GeNormTest `
  --tests org.jlinalg.cli.GeNormCliTest
~~~

If the exact source or permitted R runtime is unavailable, skip reference
regeneration, retain the error and supply that prerequisite before retrying.
The checked-in synthetic fixtures keep the Java tests independent of R.

This addition does not change the frozen 250-paper catalog, award paper-specific
credit, resolve P040's Cq wording or qualify the current main source transition.
The [main source warning](MAIN_SYNC_20261115_STATUS.md) remains in force, and the
full toolbox implementation objective remains open.

The saved checkpoint passed 15 tests on each of Windows Java21 and Java25,
without failures, errors or skips. Both runs exercise all 45 original synthetic
matrix controls. The actual packaged example ran on Windows Java21/25 and
Linux Java21 with identical scientific TSVs and successful cross-platform
bundle replay. This does not claim all 45 reference controls ran on Linux.
The initial fixture-reader failure (CRLF scale-property handling) and the
subsequent successful checkpoints are retained; original fixtures were not
rewritten to resolve it.

Read back the saved [development evidence](genorm-native-development/README.md):

~~~powershell
python -B tools/literature/genorm_native/records.py
# Optional complete readback of the retained local original/runtime evidence:
python -B tools/literature/genorm_native/records.py `
  --private-base build/genorm-native
~~~

Readback hashes saved records and checks the complete own-source archive. It
does not execute another scientific fit or authenticate upstream binaries. If
current project sources differ, the record is explicitly historical; do not
refresh its hashes to make it appear current.
