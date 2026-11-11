# MTAG: setup, analysis and required recovery

The scoped `mtag-tools` adapter runs unchanged **MTAG 1.0.8**, commit `9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc`, with explicit scientific choices and native Java admission checks. Use the matching development root supplement plus the pinned canonical dependency. The released 0.3.7 JAR by itself does not contain this command.

**SKIP3 means skip the dependent analysis.** Supply the exact permitted software or supported capability and retry into NEW paths. If installation is forbidden or cannot be completed, leave the analysis skipped. **FAIL2 means NO RESULT ADMITTED**, including partial original output. Preserve the complete attempt, resolve its named input, integrity, process, capacity or scientific cause, and retry into NEW paths.

**Before interpretation, require all four:** wrapper exit 0, `session.json` with `result_admitted=true`, `COMPLETED.json`, and successful `mtag-tools --verify`. Read `WARNINGS.md`, `scientific-audit.json`, every native ledger and the original logs. Original MTAG catches scientific exceptions and can return process exit 0 after failure; the adapter also checks the source completion/error log.

## Start with the matching command

Run actual analyses using **Linux Java**, including a permitted WSL environment on Windows. The following commands assume the project root is the current directory in Linux. Java 17+ is required; the retained controls use Java 21 and 25.

```bash
java -Xmx2g -cp docs/literature/mtag-workflow-development/mtag-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.JLinAlgCli mtag-tools --help
```

Keep both JARs on the classpath. The canonical dependency must have SHA-256 `545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`; the supplement hash is recorded in [workflow validation](mtag-workflow-development/VALIDATION.json). Do not overwrite the canonical JAR. Missing Java support classes, including bzip2 support, mean SKIP3 and require the complete matching classpath.

Saved native verification also works with Windows Java, without Python or fitting. In PowerShell, use a semicolon between JAR paths:

```powershell
$rootJar = 'D:\projects\JLinAlg\docs\literature\mtag-workflow-development\mtag-root-javac21.jar'
$canonicalJar = 'D:\projects\JLinAlg\build\cli\omicsmatic-0.3.7.jar'
& 'C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot\bin\java.exe' -Xmx2g -cp "$rootJar;$canonicalJar" org.jlinalg.cli.JLinAlgCli mtag-tools --verify D:\ABSOLUTE\PATH\COMPLETED-BUNDLE
```

Adjust the absolute paths to your workspace. For an archived example, `python tools/literature/verify_mtag_workflow_evidence.py --replay windows21 --out build/literature-review/mtag-runtime/NEW-saved-replay` extracts and verifies all 23 complete saved workflow bundles without running MTAG. `windows25` and `linux21` are also available. This verifies stated native contracts and integrity; it does not authenticate a producer or validate biological conclusions.

## Prepare the exact private runtime

Export the helper and its archive lock:

```bash
java -cp docs/literature/mtag-workflow-development/mtag-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.JLinAlgCli mtag-tools --export-runtime-helper build/NEW-mtag-helper
python3 build/NEW-mtag-helper/prepare_mtag_runtime.py \
  --out build/NEW-mtag-runtime \
  --environment-home /var/tmp/omicsmatic-NEW-mtag-environment \
  --allow-install --allow-download
```

Use Linux x86_64 Python 3.11+ for preparation. The original analysis environment is separately pinned to Python 2.7.15, NumPy 1.16.4, pandas 0.24.2, SciPy 1.2.1 and 33 exact package archives. The source lock includes all 1,096 original source files. The helper checks archive hashes before installation and performs no GWAS analysis.

Use a native Linux filesystem for the environment and package cache. The retained NTFS installation failed on package symlinks/case behavior. Use a persistent permitted location such as `/var/tmp`; `/tmp` may disappear after a WSL restart. Do not reuse, update or nest an existing environment/output directory.

Omit `--allow-download` to forbid downloads. For offline setup, supply `--archive-dir DIRECTORY` with `mtag-source.zip` and all exact package filenames in the exported lock, plus a permitted `--micromamba FILE` or its separately permitted pinned download. `--existing-environment EXACT_PRIVATE_DIRECTORY` captures an already installed exact environment without installing packages. Always use a NEW `--out`. Read the helper's `--help` and retain `setup.json`, `WARNINGS.md` and process logs. A setup receipt alone admits no analysis. Analysis never installs software. System loader/libraries are not independently pinned.

## Prepare inputs and declare the analysis

Use complete UTF-8, unquoted TSV files, optionally gzip or bzip2 compressed. The current profile accepts two through eight explicitly ordered traits, autosomal biallelic SNPs, shared declared column names and caller-supplied per-SNP Z and N. Preserve source identifiers and original input bytes. Allele harmonization follows the original same/swapped-pair rules; strand complements are not inferred.

INFO filtering currently requires the literal input column name `info`; the original MTAG program has no `--info_name` option. Other declared source column names must match your existing input headers exactly. Prepare any separately authorized header or sample-size preprocessing with its own provenance.

Copy [the complete request example](mtag-request-example.json) into your analysis directory and edit every path and scientific choice. It expects existing `SNP CHR BP A1 A2 Z N EAF P` columns. Paths are absolute Linux paths or relative to the request JSON. Each trait requires its own explicit filter values. Unknown/missing fields are rejected. Output, source runtime and input roots must be canonical and disjoint; existing output directories are preserved and rejected.

The example deliberately chooses a two-interval maxFDR grid. **The upstream default is ten.** It is an example, not a paper command or a recommendation to reduce a failed calculation. Choose the intended model, reference, covariances, filters, grid and capacities explicitly, including effective author settings for any paper reproduction.

Run the original analysis and then saved verification:

```bash
java -Xmx2g -cp docs/literature/mtag-workflow-development/mtag-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.JLinAlgCli mtag-tools --request /ABSOLUTE/PATH/request.json
java -Xmx2g -cp docs/literature/mtag-workflow-development/mtag-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.JLinAlgCli mtag-tools --verify /ABSOLUTE/PATH/NEW-mtag-output
```

The bundle retains inputs, original outputs, exact parsing captures, source/runtime identities, process commands/status/cleanup, native diagnostics, warnings and a complete file manifest. Every retained variant and trait-result row is checked; no automatic sampling, coordinate repair, covariance repair or smaller grid occurs. Saved verification is read-only and checks every diagnostic ledger row as well as association algebra and aggregate fields, without refitting.

## Source limitations that need attention

| Condition | Outcome and required action |
|---|---|
| Default `freq` header bypasses internal MAF munging | Native admission refuses retained SNPs that violate the declared strict cutoff. Inspect original column interpretation; select an existing author-specified frequency column explicitly. Preserve provenance for separately authorized preprocessing. No automatic rename/prefilter occurs. |
| `no_allele_flipping=true` | SKIP3: the original flag is declared but unused. The supported workflow retains actual source swaps, Z-sign changes and frequency changes in its ledger. |
| Non-null `n_max` | SKIP3: the original Python 2 string comparison produces a scalar boolean and fails indexing. No source patch or silent prefilter is substituted. |
| Custom maxFDR grid | SKIP3: an original bitwise-and/precedence error prevents this path. Select a different scientifically justified analysis explicitly, rather than automatically replacing the grid. |
| Union `meta_format` or NPY covariance file | SKIP3: not admitted in this profile. Supplied covariance files require the original complete `.txt` format. |
| Original beta/SE mode | Disabled upstream. Supply explicitly prepared Z and N with author units and provenance; the adapter does not enable or replace that mode. |
| Original P=0 | Retain the SciPy 1.2.1 numerical underflow. It is not a mathematically zero probability. The workflow follows the source Cephes tail; the earlier native MTAG estimator component has a different tail and is not silently changed. |
| Scientific error with source process exit 0 | FAIL2; no partial result. Inspect `original.stderr`, `original/mtag.log` or `unadmitted-original/mtag.log`, and `session.json`. |
| Resource/capacity limit | FAIL2; preserve the attempt. Supply sufficient permitted resources and NEW paths for the intended unchanged analysis, or make an explicitly justified different request. Logical bounds are not OS memory quotas; complete native log auditing currently requires source logs at most 16 MiB. |

The complete four-trait, ten-interval preflight contains **3,268,760 unpruned grid points**. The source then applies its own causal-probability/PSD pruning. A small capacity budget refuses the request before source execution. Do not silently reduce the grid to obtain a result. The original maxFDR uses a mean-N approximation; it is not a per-SNP FDR or a model-validity certificate.

**N definitions differ.** Trait output `N` is the original per-SNP input N. The original trait-level "GWAS equiv. (max) N" is in `mtag.log`. Case/control, imputation and MTAG-derived effective sample sizes are different definitions. Choose and label the intended downstream value. P115 explicitly uses MTAG-derived effective N in downstream LDSC; this does not authorize interchanging the definitions elsewhere.

## What the five papers used

| Paper | Reviewed MTAG role | Settings that remain explicit or unresolved |
|---|---|---|
| P077 | Four-way aortic-valve/aortic-stenosis analysis in reported 1.0.8 defaults, including maxFDR; enhanced statistics feed separate polygenic scoring. | Complete literal command, source commit and author inputs are unreported. REGENIE, METAL and PRScs remain separate steps. |
| P088 | Correlation screening followed by a three-trait DCM/Ecc/LVESVi run; selection uses positive `rg>0.7`. | The SE/MAF effective-N formula is a separate primary JATS equation, not contained in its adjacent paragraph. Preserve author units and source preparation. |
| P090 | Separate LDSC screening of ten MRI traits, then MTAG 1.0.8 with selected `|rg|>0.7` inputs. | The final selected file set and complete command are unstated; do not infer that all ten screening traits were combined. |
| P112 | EUR cannabis, alcohol and nicotine-dependence summary statistics after separate duplicate/MAF/strand filtering. | Version, complete command, duplicate-removal policy and per-SNP Ns are unspecified. Reported total cohort Ns do not supply per-SNP N. |
| P115 | Six input selections, training-fold-specific GWAS/MTAG, held-out PRSice evaluation and MTAG-derived effective N downstream in LDSC. | Version and complete settings are unstated; complete input sets require figures/supplements. The authors report input-sensitive findings and heritability inflation. |

See [primary role coverage](omics-2023-2026/mtag-source-role-coverage.json) and [the separately reviewed P088 equation](omics-2023-2026/mtag-P088-equation-source-review.json). The equation is `n_snp_eff = 1 / (2 * MAF * (1 - MAF) * SE^2)`; it does not provide a case/control or GWAS-equivalent-N substitution. A shared version number does not prove a shared historical commit.

## Validation and limits

The [original typed-workflow evidence](mtag-workflow-development/VALIDATION.json) includes 25 option cases: 20 admitted workflows, four expected scientific refusals and one expected skip, covering 172,625 trait-result rows. It includes 60 portable option replays, three admitted root workflows, one root source error, three root skips, nine root saved replays, 24 portable safety/tamper protocols and five setup refusals. Source-bound tails checked 386,088 finite comparisons and retained 35,853 original numerical zeros across three JVMs.

The trait-row metric counts per-target algebra checks. In the equal-heritability branch, the source writes one shared meta-analysis file and every target equation is checked against that file; those checks are not independent physical output rows or biological datasets.

The [additional dimension evidence](mtag-dimensions-development/VALIDATION.json) covers actual original four- through eight-trait workflows: the default four-trait ten-interval maxFDR grid, explicitly chosen two-interval grids for five through eight traits, 30,000 trait-result rows and 15 saved replays. These are prefixes of one declared synthetic population, not five independent biological datasets. Covariances in these additional width controls are explicitly supplied; the earlier controls separately exercise LDSC, GMM and numerical covariance paths.

Java audits finite covariance/conditional-effect algebra, exact parsed input operations, source-tail behavior, grid/range/log consistency and complete diagnostics. Estimator fitting, full source QC and the maxFDR formula remain upstream. No independent LDSC/maxFDR refit, biological accuracy, full-paper reconstruction or whole-package equivalence is claimed. The earlier [development guide](MTAG_WORKFLOW_DEVELOPMENT.md) preserves its historical pre-catalog checkpoint; its statement that live root/catalog promotion was pending describes that checkpoint. Cell Ranger remains excluded and the full 250-paper toolbox goal remains open.
