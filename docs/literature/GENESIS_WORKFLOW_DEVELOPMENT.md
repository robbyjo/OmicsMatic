# GENESIS workflow development

**GENESIS has no toolbox catalog integration credit yet.** This development command is being tested separately from the released executable. The original package performs the statistical fits; Java prepares explicit inputs, supervises execution, checks the stated scientific contracts and verifies saved bundles. Neither a successful fit nor verification establishes biological validity or reproduces a paper.

**Before using any result:** require exit code **0**, `result_admitted=true` in `COMPLETED.json`, and a successful `--verify`. Read `WARNINGS.md`, `scientific-audit.json`, original warnings/messages and the variant disposition report for association runs. Partial original outputs from a failed attempt remain diagnostic evidence and admit no analysis result.

| Disposition | What happened | Required action |
|---|---|---|
| `SKIP3` / exit 3 | The exact permitted runtime is unavailable or cannot be launched | **Skip the dependent analysis.** Supply the named prerequisites where downloads and installation are allowed. If installation is unavailable or forbidden, leave the analysis skipped. Retry a new output directory. |
| `FAIL2` / exit 2 | Invalid inputs, exhausted capacity, upstream failure or failed scientific admission | **Use no results from the attempt.** Preserve its evidence, resolve the stated cause and retry new paths. Do not repair source values, change versions/settings, reshape malformed source output or substitute a different method to suppress the error. |
| Completed / exit 0 | Original execution and the stated Java contracts passed | Verify the saved bundle and assess its warnings and the effective model before downstream use. |

Build an isolated development JAR from a source checkout on this Windows development environment:

```powershell
python tools/literature/build_genesis_tools.py --compiler javac21 --out build/literature-review/genesis-runtime/my-adapter-build-1
java -jar build/literature-review/genesis-runtime/my-adapter-build-1/genesis-tools-development.jar --help
```

The build does not overwrite `build/cli/omicsmatic-0.3.7.jar`. The development command has no route in that released JAR. Java 17 bytecode can verify saved bundles on Windows and Linux; original analysis requires the exact Windows runtime below.

The profile requires Windows x86_64, R **4.6.1** with a 64-bit long-double significand, GENESIS **2.42.0**, GWASTools **1.58.0**, SNPRelate **1.46.0**, gdsfmt **1.48.2**, RhpcBLASctl **0.23-42** and the complete pinned dependency library. A profile records every file in the supplied R distribution and private library. Windows system DLLs outside those roots are not independently pinned. Preparation changes no shared R library.

Where downloads and installation are permitted, the source-checkout setup commands are in [the preceding development checkpoint](GENESIS_DEVELOPMENT.md). Export a runtime-profile helper and point it at an existing successful private setup:

```powershell
java -jar build/literature-review/genesis-runtime/my-adapter-build-1/genesis-tools-development.jar --export-runtime-helper build/literature-review/genesis-runtime/my-profile-helper-1
python build/literature-review/genesis-runtime/my-profile-helper-1/prepare_genesis_runtime.py --setup build/literature-review/genesis-runtime/my-runtime-1/COMPLETED.json --out build/literature-review/genesis-runtime/my-profile-1
```

The exported helper installs nothing and runs no user-data analysis. Setup/profile success alone admits no result. Analysis takes a strict typed request; arbitrary R expressions, formulas and free command flags are unsupported:

```powershell
java -jar build/literature-review/genesis-runtime/my-adapter-build-1/genesis-tools-development.jar --request path/to/explicit-request.json
java -jar build/literature-review/genesis-runtime/my-adapter-build-1/genesis-tools-development.jar --verify path/to/completed-bundle
```

Input tables lead with separate `FID` and `IID` columns. Their pair identifies a participant; repeated IIDs in different families and literal `NA`/`NaN` identifiers remain distinct. Numeric missing or infinite scientific inputs are refused. Matrices require every cell (`row`, `column`, `value_hex`) and a separate ordered FID/IID sample table. Covariance reordering requires explicit `covariance_alignment="phenotype-order"`; captured originals remain unchanged and the derived order and cells are saved. `require-order` refuses mismatches. Phenotype rows determine model order. Association currently requires the same complete participant set as the genotypes; it does not silently drop or impute phenotype rows.

Genotypes are complete variant-major BED plus six-field BIM/FAM sidecars. This profile accepts sorted numeric autosomes 1–22, distinct A/C/G/T alleles, PLINK sex 0/1/2 and at most 5,000 participants. Numeric row aliases in the derived GDS are mapped explicitly to original FID/IID pairs. Original missing calls stay missing; GENESIS may mean-impute derived association inputs according to its own method. The full native/source genotype comparison and association disposition report preserve this distinction. Capacity limits are logical estimates, not operating-system memory quotas or production performance guarantees.

PC-AiR retains the original ordered partition, eigenvector signs, projected relatives and uncomputed variance NaNs. Java checks the native matrix partition, unrelated-population QC and orthogonality; it does not independently refit PCA. The original one-component projected output can have missing dimensions and IDs: the adapter refuses it without reconstruction. Standard PCA through an explicit `SNPGDSFileClass` handle can fail on an upstream missing method; `gds.class` is a separately declared interface, not an automatic fallback.

PC-Relate consumes caller-supplied finite PCs and an explicit training set; it never recomputes ancestry PCs. Negative unconstrained IBD estimates remain unchanged. The covariance cutoff applies **after scaling**, identifies connected components and retains all edges within a component, including weaker edges. Java checks the resulting complete covariance cells; it does not independently refit kinship/IBD estimates.

Models retain the requested design, convergence status, original fit objects and boundary-variance auxiliary NAs. If GENESIS estimates every random-effect variance as zero and switches to GLM, `WARNINGS.md` prominently records `effective_mixed_model=false`. Do not report that result as a fitted positive-kinship-variance model. Fixed-effect statistics depend on the original effective model; association `Score.Stat` is the signed standardized score. Single-variant Score without interaction has the initial native admission contract; other requested upstream methods require further validation before admission.

Saved verification replays the stated transport, identity, partition, covariance and algebra contracts without R. It verifies the full saved file inventory and requires the matching worker source. It does not independently decode/refit every raw RDS field, recompute association P values, authenticate the producer or prove that inputs match an intended study. Preserve the matching JAR, complete bundle and raw original inputs.

The five reviewed GENESIS paper roles remain documented in [the preceding checkpoint](GENESIS_DEVELOPMENT.md). A subsequent [source review receipt](genesis-workflow-development/P128-supplement-source-review.json) resolves part of P128's previously unavailable supplement. [Supplementary Note 7, printed page 32](https://media.springernature.com/full/springer-static/esm/art%3A10.1038%2Fs41588-026-02722-8/MediaObjects/41588_2026_2722_MOESM1_ESM.pdf) explicitly reports GENESIS 2.16.1 `pcairPartition` and a kinship cutoff of **0.044**, after PLINK 1.9 pruning and KING 2.3.1 estimates. Subsequent PCA and adjusted-kinship function names remain unstated; PC-Relate is not inferred from this note. The frozen earlier census and its integration counts are unchanged. Current GENESIS 2.42.0 behavior does not reproduce the papers' reported 2.16.1 or 2.26.0 versions. Full branch validation, portable evidence, root-command integration and catalog admission remain separate work.
