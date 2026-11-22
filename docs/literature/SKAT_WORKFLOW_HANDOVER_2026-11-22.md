# SKAT development handover and remaining work

## Current state

The last admitted catalog milestone is commit
`81269ccdbd3a74351e22f53c9783b1d5eda07e37`: native ACAT admission and 22 individual
ACAT/SKAT/SKAT-O role reviews. The catalog remains at **250 papers, 147 scoped
families (27 native + 120 upstream), 1,661 reviewed uses and 1,062 pending uses**.
There are 446 labels, 485 provider definitions and 2,723 observations; 258 missing
labels, 27 method overlaps and two aliases receive no extra family credit.
The frozen collection window is 2023-10-23 through 2026-10-23: 71 Nature,
104 Nature Genetics and 75 American Journal of Human Genetics papers.

This handover adds source and development evidence for original **SKAT 2.2.5**.
It adds no family credit and changes no production CLI route or catalog data.
**The standalone Java SKAT workflow is unfinished and unavailable to users.**

Authoritative development owner:
`D:/projects/JLinAlg/build/sk-1`, branch `codex/skat-workflows-20261122`.
Evidence root: `build/skat-workflows-1` under that owner. Publication receipts:
`D:/projects/JLinAlg/build/git-publish/20261122-handover-111`.
There are 67 retained worktrees. Preserve the dirty root, older drafts,
scientific evidence, runtime libraries and original JARs.

## What exists

* `SkatWorkflowContracts.java`: closed typed request normalization, explicit
  source-method/kernel/null-model choices, RNG seed and admission policies.
* `SkatWorkflowFiles.java`: literal streaming TSV intake, ordered sample/variant
  identities, explicit sets, weights, complete cases and declared capacities.
* `SkatWorkflowProcess.java`: owned root and start-identified observed-child
  cleanup, time and workspace monitoring. This is not OS job containment or a
  hard aggregate RAM quota. Process behavior still needs dedicated tests.
* `src/main/resources/org/jlinalg/omics/skat-workflow/skat_worker.R`: original
  SKAT worker prototype. No namespace replacement, tracing or rewritten model
  implementation. It guards 239 original function formals/bodies before and
  after execution, captures full original null/results and RNG RDS, and replays
  genotype diagnostics while restoring the original RNG state.
* `SOURCE.json` pins 302 GPL-compatible reference members from the exact official
  source, package metadata and 239 function snapshots. No upstream human data,
  example datasets, installed packages or runtime binaries are bundled.

The worker expects `source/`, `inputs/`, `worker/` and initially empty `results/`.
Run Rscript with `--vanilla` from `worker/`. Before any model fit it publishes a
nonce-bearing readiness marker, reports runtime files, then waits for matching
`runtime-ack.txt`. A future Java runtime must fingerprint before acknowledging
and compare afterward. The present Python prototype supplies that handshake.

Input TSVs use literal UTF-8, no BOM or automatic name repair: phenotype has
`sample_id` and one value column; genotypes have `sample_id` and variant columns;
optional covariates have `sample_id` and numeric columns; sets have exact
`set_id,variant_id` tab-separated headers; optional weights have
`variant_id,weight` for every genotype column in the same order. Preserve row,
column, group and membership order. Missing phenotype/covariates require explicit
complete-case policy. Source genotype `NA` and `9` rules remain distinct.

## Validation and retained failures

See [STATUS.json](skat-workflow-handover-20261122/STATUS.json) and the evidence
manifest for exact source hashes, commands, results and archive hashes.

* Official-source parsing matches 239 installed namespace functions exactly.
* Eight actual R worker cases cover continuous/binary outcomes, `davies` and
  `optimal.adj`, and adjustment FALSE/TRUE. Each case includes an ordinary set,
  one informative variant and an explicitly untested monomorphic set. The first
  set agrees with the preceding independent direct-original controls. This is
  **not** independent validation of every helper, method, mask or RNG chain.
* Current source passes a fresh eight-case rerun after the latest admission and
  logging changes. Three internal Java drafts pass `javac --release 17 -Xlint:all
  -Werror` against the previously qualified native ACAT JAR. This does not claim
  Java 17 runtime qualification or a functional SKAT Java CLI.
* The normal build and existing native ACAT regression result are recorded in
  STATUS.json. They provide compilation/regression evidence, not SKAT CLI tests.
* Prototype attempt 1 refused initialization because the harness incorrectly
  compared raw `RSpectra 0.16-2` with canonical `packageVersion` value `0.16.2`.
  It ran zero null models and zero association calls. The corrected guard checks
  raw DESCRIPTION and canonical versions separately; the failed capture stays.
* Attempt 3 completed seven cases before the Python harness hit a Windows
  PermissionError reading the last readiness marker. That R worker timed out
  waiting for acknowledgment, with zero null fits and association calls in that
  last case. The attempt is incomplete. A bounded retry of that metadata read
  produced a separate successful attempt 4. No scientific result was repaired.

Public archives contain only development source/logs and artificial fixtures or
results. Installed runtimes and the development JAR stay private. No release,
Pages publication, biological finding or complete-paper reproduction is claimed.

## Resume in this order

1. Read this handover, STATUS.json and the previous ACAT checkpoint. Confirm
   current GitHub main before creating a new `codex/` branch or owner. Keep all
   original worktrees unchanged. Run the portable handover verifier. Install or
   supply the exact permitted R profile in a private location; never overwrite
   old scientific runtime libraries.
2. Implement **SkatWorkflowRuntime.java**: exact source/worker/class hashes,
   declared and resolved executable/library paths, controlled R environment,
   nonce handshake, runtime/file fingerprints before ACK and after execution,
   and accurate SKIP3 versus FAIL2 outcomes. Pin the worker's current SHA from
   STATUS.json; do not reuse an older prototype's worker hash.
3. Implement **SkatWorkflowAudit.java**: verify intake, masks, source-field and
   exported p-value relationships, variant mappings, source MAC and whole-batch
   admission. Offline VERIFY is integrity/structural validation, not independent
   R refitting, RDS interpretation or proof of convergence. Do not imply more.
4. Implement **SkatWorkflowCli.java** and global dispatch only after it works:
   RUN with raw/effective request capture, prominent status/warnings and sealed
   outputs; VERIFY; REPLAY into NEW output with physical parent-alias protection
   and parent seals checked before/after. Keep all scientific choices explicit.
5. Qualify meaningful schema/intake, wrong/missing runtime SKIP3, changed-source
   FAIL2, real original continuous/binary fits, missingness, dosage, weights,
   nonlinear kernels, exact threshold boundaries, multiple sets/RNG chains,
   resampling, fallback/zero/empty policies, numerical failure, timeout and child
   cleanup, resealed contradictions, and real REPLAY. Use a separate unmodified
   upstream oracle; the worker is not its own independent reference. Build through
   normal Gradle and test the actual global packaged JAR. Retain every failure.
6. Recover **P246** supplementary method/rho arguments. Its main text names
   SKAT R 2.2.5, SKAT() for SKAT-O, age, sex and ten PCs but omits exact options.
   Do not fill those gaps from defaults. Re-audit provider-specific paper roles.
   Admit one standalone upstream SKAT family after qualification; SKAT-O within
   that provider is an operation/alias, not an automatic second package family.
7. Continue high-impact gaps and the 1,062 pending uses. Method-overlap counts
   include SKAT-O 9 papers, GenomicRanges 7, g:Profiler 7, FUSION 7, Squidpy 6,
   smartpca 6 and MR-PRESSO 6. Frequency is a screening signal, not execution
   evidence. Rank feasible scientific gaps before long proprietary GUI projects;
   many high-frequency labels such as GraphPad/FlowJo do not establish a portable
   computational method to reproduce. Prefer easy native Java methods and exact
   tested adapters for substantial upstream software. Do not substitute a generic
   method for REGENIE, SAIGE, REMETA, SEAK or COAST paper-specific models.

## Source behavior that must survive implementation

* Actual default `optimal.adj` rho grid has **seven** values:
  `0, .1^2, .2^2, .3^2, .5^2, .5, 1`; the manual lists an extra `.4^2` value.
  Preserve actual source and disclose the discrepancy. Source also retains
  caller rho vectors of length greater than one in cases the manual describes
  differently. Preserve requested versus actual method/rho resolution.
* Missing ratio is **strictly less than** `missing_cutoff`; MAF is **strictly
  less than** `max_maf`. Source filters MAF before imputation/flipping. For
  estimate_MAF 2, source subsets genotypes to included phenotypes while its
  missing-ratio denominator retains the original n. Do not silently fix this.
* Small binary adjusted nulls use 10,000 kurtosis resamples; preserve the seed,
  RNG kind and chain across sets. Dosage forces fixed imputation in source.
* One-variant and identical-column adaptive tests can substitute SKAT. Require
  explicit `allow_test_substitution`. Nonlinear kernels can omit small-sample
  adjustment; require explicit `allow_adjustment_omission`.
* `SingleSNP_INFO` reports source column sums, without an extra minor-allele
  conversion. A one-variant source result may lose names. Keep raw unnamed
  objects and separate declared-ID mappings. Sentinel diagnostic weights are
  identity markers only and must never become association weights.
* Source exposes some convergence/fallback flags, including resampling flags,
  but catches some integration errors and uses Liu internally without a complete
  returned indicator. `allow_reported_fallback` governs reported evidence only.
  Do not claim a complete convergence certificate. Preserve upstream hidden
  behavior honestly; do not patch the namespace to manufacture one.
* Numeric zero is retained raw. Default refusal can be relaxed only for a
  source-reported bound; export then stays NA rather than an exact zero.
  Zero-valued resampling p-values lack individual bounds and remain unadmitted.
* Monomorphic/empty sets may return source p=1. Mark these not tested and export
  NA, require explicit empty-set acceptance and at least one actually tested set.
  One failed/unadmitted set withholds the entire complete p-value export.

## Runtime and recovery requirements

The private tested Windows profile is R **4.6.1**, SKAT **2.2.5**, SPAtest **3.1.2**,
RSpectra **0.16-2**, Rcpp **1.1.2**, RcppEigen **0.3.4.0.2**, Matrix **1.7-5**, and
stats/methods/utils/tools **4.6.1**. Raw versions and canonical R versions differ
for hyphens. Preserve both. Several installed binaries were built under R 4.6.2
and load under 4.6.1; retain their Built metadata and warning. Do not describe
this as a same-build toolchain. The exact CRAN archive is SHA-256
`1441fa46b6a78a060007442fb8cb8c87753bdc2b1ea2dc24ff951ac3fef651f4`.
SKAT's upstream license is GPL >= 2; root GPL licensing and upstream notices apply.

Rscript was not on PATH in the two inspected non-Cell-Ranger WSL distributions.
This does not establish absence of a private Linux R. No Linux R fit is qualified.
Locate or install a permitted private Linux runtime before claiming such results.

**SKIP3 — dependent analysis did not run:** supply the exact permitted executable,
R packages, input/index or audit dependency named in the warning. Download/private
installation is authorized where permitted. If prohibited, retain the skip and
explain the affected analysis and exact user action. Cell Ranger stays excluded.

**FAIL2 — no complete result:** preserve raw inputs, original RDS, warnings,
conditions, null/failed/partial outputs and process records. Resolve the reported
input, numerical, capacity, process or integrity cause and retry NEW output.
Never repair NaN/Inf, identifiers, endpoints or scientific options silently; never
refresh old owner hashes to make changed code look qualified.

**Before retiring the workstation:** transfer the private items in
PRIVATE-TRANSFER.json and preceding linked inventories. A GitHub push does not
transfer ignored builds, installed runtimes, all primary XML/PDF caches, private
manuscripts or every historical failed attempt. Historical backup branches are
preservation snapshots, not approved mainline implementations.
