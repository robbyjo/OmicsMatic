# Popcorn cross-population GWAS workflows

The source build adds `popcorn-tools`: original two-population LD covariance-score
generation from PLINK reference panels, fitting from those scores or existing
ten-column scores, and complete saved-input replay. Select genetic-effect or
genetic-impact correlation explicitly. Both stages use the declared mode.

**Warnings and required action:** missing or forbidden prerequisites return
**SKIP3**, skipping dependent analysis. Supply the named input or install the
permitted software in a separate private runtime, then create a **new** binding
and capture. Changed bytes, failed data/model checks, cancellation or exhausted
capacities return **FAIL2** and admit no complete result. Preserve every attempt;
inspect `FAILURE.json`, `WORKER-FAILURE.json`, all original logs and the named
input/output audits. Correct the cause and retry new paths. Commands install
nothing. A runtime binding alone admits no analysis.

Use Java 17 or newer. Upstream execution and replay require Linux x86_64 Java;
on Windows run Linux Java inside WSL. The public native numerical API also runs
on Windows. These commands are in the current source build; this change does
not publish a release binary.

```sh
java -jar omicsmatic-0.4.1.jar popcorn-tools --profiles
java -jar omicsmatic-0.4.1.jar popcorn-tools --example binding > binding.json
java -jar omicsmatic-0.4.1.jar popcorn-tools --example compute-fit > analysis.json
# Replace every illustrative path, provenance statement, setting and capacity.
java -jar omicsmatic-0.4.1.jar popcorn-tools --bind binding.json --out NEW_BINDING
java -jar omicsmatic-0.4.1.jar popcorn-tools --request analysis.json --out NEW_CAPTURE
java -jar omicsmatic-0.4.1.jar popcorn-tools --verify NEW_CAPTURE --scratch NEW_REPLAY
```

Examples for `compute` and `fit` are also available. A compute request supplies
two PLINK prefixes and no summary files; fit supplies one precomputed score
file and two summaries; compute-fit supplies both panels and both summaries.
No shell command, extra arbitrary upstream flags or implicit input search is
accepted. Inspect stderr warnings even when the exit code is zero. Require a
completed capture **and** a successful fresh replay using its exact retained
producing JAR and unchanged bound runtime.

## Original versions and runtime setup

| Profile | Original revision | Version interpretation |
| --- | --- | --- |
| `Popcorn-1.0-3c21c41f` | `3c21c41fd2cf0ca51305526dbbefb6b5995c0209` | Python3 port with CLI banner 1.0; not proof of an author's exact revision |
| `Popcorn-1.1-20a29e8c` | `20a29e8cb9c43fe88de8e903537eb874d52f38b3` | CLI banner 1.1; package VERSION still says 1.0.0 |

Sources: [official repository](https://github.com/brielin/Popcorn),
[1.0 source](https://github.com/brielin/Popcorn/tree/3c21c41fd2cf0ca51305526dbbefb6b5995c0209),
[1.1 source](https://github.com/brielin/Popcorn/tree/20a29e8cb9c43fe88de8e903537eb874d52f38b3).
The original CLI declares GNU GPL v3. Retain the original notices. The Java
regression source credits Brielin C Brown and declares GPL-3.0-or-later.
Upstream sources, installed runtimes and raw upstream traces are kept private
by this project's evidence workflow.

Prepare a separate Python **3.11** virtual environment. The profiled numerical
stack is NumPy 1.23.5, SciPy 1.10.1, pandas 2.1.4, statsmodels 0.14.1,
PySnpTools 0.5.13, bottleneck 1.3.7, bed-reader 1.0.6 and h5py 3.8.0. Install
their required dependencies too. PySnpTools 0.5.13 has no compatible binary
wheel in the inspected index: build its unmodified official source archive
using its build requirements when the environment permits. A binary-only pip
download fails; this is retained in the private setup evidence. Do not modify
scientific source to bypass incompatibilities. If installation is prohibited
or unavailable, skip the dependent analysis and resolve the named prerequisite.

The binding's `source` directory must be an exact **non-test source view**:
copy only the relative files listed in the selected packaged `source_files`
profile, preserving their bytes. It includes the original README, setup,
requirements and Python files; do not point it at a full checkout containing
extra Git metadata or upstream test data. The binder checks every source-file
hash, exact package versions, imported native libraries, declared runtime
roots, standard library and dynamic dependency closure. Use an explicit private
Python executable, complete runtime roots and any actual `library_path`.
Imports disable user site packages and bytecode writing. No scientific source
is patched. Keep all effective inventory roots **and symlink targets** for replay.

## Inputs, models and retained source behavior

Reference computation requires one chromosome, strictly increasing distinct
positions, unique SNP IDs, and shared SNPs in the same order and coordinates
in both panels. The original independently sorts panel indices and scans
windows without chromosome separation; the adapter refuses data that would
silently violate those assumptions. It does not reorder panels or repair
identifiers. BIM metadata must be tab-delimited, FAM must have six columns and
unique FID/IID pairs, and the SNP-major BED must have the complete expected
byte count. Actual ancestry, build, relatedness and reference preparation
remain the caller's responsibility. Multi-chromosome precomputed scores can
be fitted with their original total M and documented preparation.

The original two-population score format has **no header**, with these columns:

```text
chr  pos  id  a1  a2  af1  af2  score1  score2  scoreX
```

The delimiter is a tab. Precomputed numeric values cannot establish whether
scores represent effect or impact correlation: provide honest explicit mode
and source provenance. The original reads PLINK dosages with `count_A1=False`
(A2 counts), despite a different AF description in its README. Original
within-population normalization and PySnpTools cross-population Unit
normalization differ with missing genotypes; the workflow preserves both.

Summary statistics are original tab-delimited files. Original ID precedence
is `rsid`, then `SNP`, then chromosome/position construction; allele columns
are `a1/a2` or `A1/A2`; the frequency column actually read is lowercase `af`.
`N` is required. Effects use `beta` and `SE` first, then signed `Z`, then `OR`
and `p-value`. In the inspected original 1.0 parser, Z-only input can fail
because it requests absent beta/SE columns during missing-value filtering;
1.1 changes this behavior. Original coordinate-ID formatting and lowercase
allele paths also have source-specific limitations. Such failures remain
visible; the adapter does not silently repair data or substitute a parser.

Both scores and summaries use original strict MAF filtering and original
allele/frequency alignment. `M` is the **full score-file row count before
summary merging and fit filtering**, and stays fixed in deleted-block fits.
The options expose KBP/SNP windows, source read/store buffers, optional bias,
extract/range selection and explicit h2 weighting. Genetic-effect plus h2weight
is refused because upstream prioritizes one weight branch; choose the actual
intended model explicitly. Keep enough buffer for the selected window. A
source 1.0 buffer error is a failed attempt, never an admitted partial score.

Regression fits WLS with intercepts and the original statsmodels pseudoinverse
cutoff. Correlation is not constrained to [-1,1]; out-of-range values remain
unchanged with a warning. Original MLE is separately selectable. Original
1.1 rejects nonpositive full-fit heritability; original 1.0 can emit invalid
estimates. Nonpositive/nonfinite fitted heritabilities or requested undefined
uncertainty retain the raw output and fail complete scientific admission.
Explicitly disabling jackknife admits point estimates only, with the original
NaN SE/Z/P and a prominent warning.

The original adaptive block jackknife uses 10–200 blocks and pseudovalues
`nb * deleted - (nb - 1) * full`. NaNs are masked according to original rules;
no conventional-sign or bootstrap replacement occurs. The reported correlation
Z is **(1 − rho) / SE**, testing rho = 1, with original `1 - chi2.cdf` rounding.
Any separately derived rho = 0 test must be labelled separately. Never clip
estimates or change the null to match an expected result.

The workflow does not infer single-population fits, sample overlap, liability
conversion, hidden region-fit behavior, likelihood plots, author reference
files or exact paper recipes. An external region analysis needs its actual
selected score rows and M documented; a global fit is not a local estimator.

## Native Java method and evidence

`org.jlinalg.genetics.PopcornRegression.fit` implements the original complete
aligned-row WLS and block-jackknife numerical stage using a caller-owned
compute backend. It does not calculate reference scores or harmonize files.
The upstream workflow automatically checks this Java stage against every
aligned original default-regression result, including point/deleted/pseudo
estimates and standard errors. Near-zero errors make Z numerically unstable;
the documented Z/P comparison threshold is SE above 1e-10. Original outputs
are always retained. Native inputs and result arrays are checked/defensively
copied; cancellation is propagated. Capacities do not reserve JVM memory.

Every capture retains input hashes and bytes, preparation/provenance, original
source profile, runtime seal, process ownership/limits, original logs, full
scores, aligned rows, fitted results and jackknife populations where requested.
Fresh replay uses captured inputs, reruns the original source and compares all
canonical rows. Extra or changed saved files fail verification. Keep the exact
producing JAR, binding, inputs and evidence; never refresh historical seals.

The [source audit](literature/popcorn-source-audit/ROLES.json) distinguishes all
five paper roles and 25 primary-text contexts, including the newly recovered
uppercase P112 observation, P105 mode ambiguity and P199 null/resampling
differences. Supplementary-method completeness and whole-paper reproduction
remain open. Qualification and publication receipts are recorded separately
under `docs/literature/popcorn-validation/`; the overall 250-paper goal remains active.
