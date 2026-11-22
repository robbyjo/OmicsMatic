# Original PRS-CS workflows with native checks

**The development candidate passed 218 focused tests, 12 packaged workflows and 28
failure controls on Java 21/25.** Use
`build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`33b26e76b989ce3387d3965f4b625a6e4a02e3e2b55d8bccd90bb1e48981a764`.
The canonical a104 executable remains unchanged and lacks `prs-cs-tools`.
The [source review](../PRSCS_SOURCE_REVIEW.md) explains the 12 paper roles and
the separately checked native math, input, LD and posterior contracts.

**Always read `WARNINGS.md` and `analysis-status.json`.** Dependency absence,
changed software or forbidden installation skips the dependent analysis with
exit 3. Input, process or numerical-validation failures return exit 2. A completed
process is followed by native file checks and review warnings. Failed canonical
streams are retained under `partial/`.

## Private setup where permitted

Supply CPython 3.12 x64 on Windows. Setup installs a separate environment, original
source and exact dependency wheels privately. Installation/download flags are
independent; omit download permission when verified artifacts are already cached.

```powershell
python tools/literature/install_prscs.py `
  --profiles source-fbc9ec0 `
  --python C:/PATH/TO/CPYTHON312/python.exe `
  --home build/omics-prscs-current `
  --allow-install --allow-download
$env:JLINALG_PRSCS_HOME = (Resolve-Path build/omics-prscs-current).Path
```

If the operating environment forbids installation or lacks the required runtime,
preserve the skip report and run the dependent analysis on a permitted host after
following its recovery instructions. Analysis itself does not install or download
software or an LD panel. Changed/unmanaged homes are preserved. Use a new private
home to add profiles or recover a changed installation.

Historical 1.0.0/1.1.0 profiles require an explicit
`--legacy-use offline-reproduction` analysis choice and a separately selected
private setup. Their Python 3.10.11 runtime is out of support. The original code is
unchanged, with its compatible pinned NumPy/SciPy/h5py dependencies; newer SciPy
has removed aliases used by these historical sources. Known deprecations remain
in retained stderr and prominent warnings. Unknown or numerical warnings fail.

## Run the synthetic example

Run from the repository root with the validated development candidate:

```powershell
java -jar build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar prs-cs-tools `
  --options-file examples/prs-cs-tools/current-beta-se-auto-both.json `
  --out build/prscs-example-new-attempt
```

The [six recipes](../../../examples/prs-cs-tools/README.md) exercise all four
summary formats and the three explicit source profiles. All values are strings
in UTF-8 JSON; direct flags override recipe values. The fixture has synthetic
LD/SNP data and a deliberately short 40/20/5 schedule. It supplies no scientific
sampling or predictive-performance recommendation.

For study data, declare the target BIM, summary file, reference kind/chromosomes,
summary format, trait kind, uniform GWAS N and its interpretation, population/build
labels, a/b/phi, iterations/burn-in/thinning/seed and output units/mode. Choose
unmatched, palindromic, complement, position and extreme-P policies explicitly.
Binary effective N is supplied by the caller; no case/control conversion or
phenotype/liability-scale transformation is inferred.

The reference directory basename must contain the declared lowercase `1kg` or
`ukbb` label and contain the original `snpinfo_*_hm3` and chromosome HDF5 files.
Keep the documented original panel and its provenance. Population/build labels
are declarations; the adapter does not establish their biological compatibility
or perform liftover. Supplied tables are intersected by exact IDs and original
allele rules, with every requested target row retained in the disposition ledger.

## Review outputs and downstream scoring

| File | Meaning and required review |
|---|---|
| `WARNINGS.md`, `analysis-status.json` | Completion/skip/failure and concrete recovery actions. Read first. |
| `session.yaml`, `manifest.yaml` | Exact options, runtime/source identity, original commands, hashes and numerical-check counts. Original command paths describe the staging location at execution. |
| `variant-ledger.tsv`, three input ledgers | Every input disposition and requested-target inclusion/exclusion. Source row numbers remain available even when the final ledger follows reference order. |
| `native-marginals.tsv`, `audit/original-marginals.tsv` | Native standardized marginals compared with unmodified upstream parser results. |
| `ld-validation.tsv`, `audit/` | Raw HDF5/SNP-order checks and original matrix identities. Original sign multiplication and SVD adjustment remain upstream operations. |
| `posterior-means.tsv`, `posterior-draws.tsv` | Requested complete variant families, declared units and unchanged original numeric strings. Values have seven significant digits. |
| `shrinkage.tsv` | Requested posterior psi, with native checks in (0,1]. Historical 1.0.0 has no psi output. |
| `score-weights.tsv` | Optional explicit per-allele mean-weight export for `genotype-score`. Standardized means use the supplied reference-frequency conversion. |
| `upstream-mean/`, `upstream-draws/` | Unchanged original outputs and retained stdout/stderr. |

`--posterior both` uses two same-seed upstream runs and checks their mean/draw
rounding intervals and psi identity. Historical sources support means; current
source also supports retained draws. Original filenames round a/b/phi labels,
so use exact parameters in `session.yaml`. The original source restarts the
same supplied seed on each requested chromosome. Chain stability and uncertainty
assessment need their own scientifically justified analysis.

`--score-export per-allele` requires a mean run. The exported frequency is from
the supplied LD panel. Choose downstream missing-genotype policy explicitly;
the adapter does not infer target-cohort frequencies, select hyperparameters,
fit ancestry combination weights or calibrate absolute risk. Training-target
overlap, reference compatibility and independent predictive evaluation require
separate assessment.

## Resource limits and recovery

Input table rows and decompressed bytes, selected reference SNPs, full source LD
cells, blocks/block width, retained-storage allowance, posterior scalars, line
width and report bytes have explicit ceilings. Full requested LD matrices count
toward the conservative work allowance even for unused markers. Use reviewed
larger limits or explicit per-chromosome runs when appropriate. These guards do
not guarantee total process RAM, including native libraries and HDF5 string
allocations. Whole-runtime integrity checks can dominate small synthetic runs.

After a failure, inspect the first named error, its raw logs and the exact input
or policy involved. `<bundle>` in warnings refers to the published output
directory. Correct the named cause, preserve the failed attempt and rerun into a
new output directory. Do not reuse a quarantined canonical file as a validated
result. Resource-limit changes do not repair incompatible alleles, source order
or reference provenance.

## Reproduce packaged validation

After the development candidate is built, this verifier preserves every attempt
and compares actual packaged outputs with frozen independent upstream results:

```powershell
python tools/literature/verify_prscs_workflows.py `
  --jar build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar `
  --java C:/PATH/TO/JAVA21/bin/java.exe `
  --java C:/PATH/TO/JAVA25/bin/java.exe `
  --home PRIVATE_HOME_WITH_ALL_THREE_PROFILES `
  --out build/prscs-packaged-verification-new
```

Six successful workflows and 14 failure controls run per supplied JVM. The inputs
are synthetic and copied into the attempt directory. This gate checks packaged
execution, original bytes, canonical units/identities, score conversion and
actionable skips/errors. It does not provide clinical or convergence validation
or substitute for a full-project release gate.
