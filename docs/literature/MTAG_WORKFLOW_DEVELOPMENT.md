# MTAG workflow development: warnings and required actions

**This is an isolated development workflow. MTAG still has no catalog credit or root-command integration. The canonical/released 0.3.7 JAR is unchanged.** The separate [native component record](MTAG_DEVELOPMENT.md) remains a historical, strictly narrower milestone. Its estimator uses JDistlib tails; the workflow described here audits the original SciPy 1.2.1 tail, including its numerical zeros.

**SKIP3 means skip the dependent analysis.** Provide the exact permitted runtime or capability, then retry into new paths. If installation or downloads are forbidden, retain the skip and stop the dependent analysis. **FAIL2 means no result is admitted.** Preserve the entire attempt, including `unadmitted-original`, read the named error and original logs, correct its cause and retry into new paths. Never repair identifiers, nonfinite results, covariance matrices, P values or scientific settings to hide an error. A zero original Python process exit code alone does not prove success: MTAG catches scientific exceptions and can return zero. The wrapper also checks the source completion/error log.

For any result, require command exit 0, `session.json` with `result_admitted=true`, `COMPLETED.json` and a successful saved `--verify`. Read `WARNINGS.md`, `scientific-audit.json`, `native-audit/variant-dispositions.tsv`, `native-audit/input-parser-differences.tsv` and all original logs before interpretation. Hashes establish integrity, not producer authenticity.

## Exact scope and source behavior

The workflow supervises unchanged [MTAG 1.0.8 source at commit 9e17f3c](https://github.com/JonJala/mtag/tree/9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc). It requires Linux x86_64 Java and an admitted private Python 2.7.15, NumPy 1.16.4, pandas 0.24.2 and SciPy 1.2.1 environment. The lock fixes all 33 original package archives and all 1,096 source files. Windows users execute analysis with Linux Java through WSL. Saved native auditing also runs on Windows Java 21 and 25.

Declare ordered summary statistics for two through eight traits, shared explicit column names and autosomal biallelic SNPs. Intake accepts complete UTF-8 unquoted TSV, gzip TSV and bzip2 TSV; it preserves original bytes. The common-SNP workflow supports original LDSC or supplied residual covariance, GMM/numerical/supplied genetic covariance, perfect genetic correlation, the original equal-heritability meta-analysis branch and supported original maxFDR grid or spike/slab choices. Text covariance files must have the original `.txt` format. No analysis-time installation, command interpolation, automatic harmonization, covariance repair or fallback model occurs.

The source performs fitting, its full QC and maxFDR calculations. A separate preparation sidecar repeats the original parsing and QC, without fitting, to capture exact input values. Java checks all retained identities, coordinates, allele swaps, effective Z/N/frequency values, trait-result rows and effect/SE/Z algebra. It audits original maxFDR grid rows, finite ranges and logged maxima; it does not independently implement LDSC or the maxFDR formula. Exact rational PSD checks can refuse an unassessable covariance. The source's declared perfect-correlation constraint has a separate, explicit rounding-aware check.

Important source findings and recovery:

- **Default `freq` can bypass original MAF filtering.** Internal munging does not recognize that default header. Java still requires every retained SNP to satisfy its declared strict MAF cutoff; a violation is FAIL2, with no automatic prefilter or rename. Inspect the original column interpretation. Select an existing, author-specified frequency column explicitly, and retain provenance for any separately authorized preprocessing. Separate controls use an explicitly declared `frequency` header to exercise active original filtering. Do not treat the source's "0 SNPs removed" message as proof the declared frequency column was filtered.
- **`no_allele_flipping=true` is SKIP3.** The original parser declares the flag but the merge does not honor it. Supported common-SNP analysis retains actual source swaps and their Z-sign/frequency changes. Strand complements are not inferred.
- **Non-null `n_max` is SKIP3.** Its original Python 2 text comparison produces a scalar boolean and fails later indexing. The source remains unchanged.
- **Custom maxFDR grids are SKIP3.** The original float-array expression has a bitwise-and/precedence error. The first failed source attempt is retained. An alternative mode requires an explicit scientific choice; no grid is substituted automatically.
- **Union `meta_format` and NPY covariance intake are SKIP3.** They are outside this admitted profile. The original beta/SE mode is disabled upstream. Supply explicit, scientifically justified Z and N inputs; the adapter does not enable or replace that mode.
- **Original P=0 is retained.** The source SciPy tail underflows to zero at extreme Z, including Z=38; this does not mean the mathematical probability is zero. The workflow's Java tail follows the exact [SciPy 1.2.1 Cephes implementation](https://github.com/scipy/scipy/blob/v1.2.1/scipy/special/cephes/ndtr.c). The earlier native MTAG component uses a different tail and can return a positive subnormal at this boundary; it is not silently changed.
- **Sample-size definitions differ.** Trait output `N` remains the original per-SNP input N. The source's trait-level "GWAS equiv. (max) N" is in `mtag.log`. Choose and label any downstream N explicitly; do not interchange these with case/control or imputation effective sizes.
- **Capacity failures admit no partial result.** Every input/result row is checked; there is no automatic subsampling or smaller FDR grid. Explicit capacities are logical checks, not OS memory quotas. Complete source logs above 16 MiB exceed the current native log-audit capacity. System loader/libraries are not independently pinned.

## Prepare a private runtime

Use the current isolated development JAR with the exact canonical JAR on its classpath. Its manifest still targets a developer probe, so invoke `org.jlinalg.cli.MtagToolsCli` explicitly. The canonical dependency must match the SHA-256 recorded in the build receipt; it supplies required Java support classes, including bzip2.

For the current Windows workspace, export the helper with Java:

```powershell
$mtagJar = 'D:\projects\JLinAlg\docs\literature\mtag-workflow-development\mtag-workflow-javac21.jar'
$canonicalJar = 'D:\projects\JLinAlg\build\cli\omicsmatic-0.3.7.jar'
& 'C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot\bin\java.exe' -cp "$mtagJar;$canonicalJar" org.jlinalg.cli.MtagToolsCli --export-runtime-helper D:\projects\JLinAlg\build\mtag-user-helper-1
```

Adapt the absolute paths to your workspace. Then use Linux Python 3.11+ and NEW directories. This example explicitly permits installation and downloads:

```bash
python3 /mnt/d/projects/JLinAlg/build/mtag-user-helper-1/prepare_mtag_runtime.py \
  --out /mnt/d/projects/JLinAlg/build/mtag-user-runtime-1 \
  --environment-home /var/tmp/omicsmatic-mtag-user-environment-1 \
  --allow-install --allow-download
```

Keep the environment and package cache on the native Linux filesystem: the retained NTFS attempt failed on package symlinks/case behavior. Use `/var/tmp` or another permitted persistent private location; `/tmp` can disappear when WSL restarts. The helper checks all archive hashes before an offline explicit-package install. It performs no GWAS analysis. The exact bootstrap is [micromamba 2.10.0-0](https://github.com/mamba-org/micromamba-releases/releases/tag/2.10.0-0).

For offline setup, supply `--archive-dir DIRECTORY` containing `mtag-source.zip` and all 33 archive filenames listed in `mtag-workflow-lock.json`. Supply a permitted `--micromamba FILE`, or permit its pinned download. Omit `--allow-download` to forbid downloads. Alternatively, `--existing-environment EXACT_PRIVATE_DIRECTORY` captures an already installed exact environment without installing packages; it still requires the exact source archive/cache. Every helper invocation needs a NEW `--out`. Read the helper's own `--help` for the full argument contract. A setup receipt alone admits no analysis.

## Declare and run an analysis

Create a JSON request. All fields are required; paths are relative to that JSON or absolute Linux paths. This example expects existing columns `SNP CHR BP A1 A2 Z N EAF P` in each TSV. It deliberately selects a two-interval maxFDR grid; the upstream default is ten. Choose the model, grid and capacities for the intended analysis explicitly, including author settings for paper reproduction.

```json
{
  "schema_version": 1,
  "runtime_profile": "../mtag-user-runtime-1/runtime.json",
  "output_dir": "NEW-analysis-1",
  "traits": [
    {"name": "trait1", "input": "trait1.tsv"},
    {"name": "trait2", "input": "trait2.tsv"}
  ],
  "columns": {"snp":"SNP","chr":"CHR","bp":"BP","a1":"A1","a2":"A2","z":"Z","n":"N","freq":"EAF","p":"P","info":null},
  "filters": {"maf_min":[0.01,0.01],"n_min":[1,1],"n_max":null,"info_min":null,"homogeneous_kind":"none","homogeneous_values":null,"include":[],"exclude":[],"chromosomes":[],"include_ambiguous":false},
  "covariance": {"sigma":"ldsc","sigma_file":null,"omega":"gmm","omega_file":null,"constraint":"none","no_overlap":false,"tolerance":0.000001,"ld_reference":"bundled_1kg_eur","ld_reference_dir":null},
  "fdr": {"mode":"grid","intervals":2,"grid_file":null,"p_significant":0.00000005,"cores":1},
  "standardized_effects": false,
  "force_low_signal": false,
  "median_z_cutoff": 0.1,
  "limits": {"rows_per_trait":50000,"input_bytes":1000000000,"output_bytes":1000000000,"working_bytes":1000000000,"wall_seconds":1800,"maximum_files":1000,"maximum_fdr_grid_points":20000},
  "no_allele_flipping": false,
  "meta_format": false
}
```

For a ten-interval, three-trait maxFDR request, the unpruned preflight contains 19,448 grid points; a 10,000-point capacity refuses it before source execution. Raising a declared resource bound does not change the chosen grid. The source can subsequently prune scientifically invalid grid rows according to its original algorithm.

Run analysis and saved verification with Linux Java, using the complete classpath:

```bash
java -Xmx2g -cp /mnt/d/projects/JLinAlg/docs/literature/mtag-workflow-development/mtag-workflow-javac21.jar:/mnt/d/projects/JLinAlg/build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.MtagToolsCli --request /ABSOLUTE/PATH/request.json
java -Xmx2g -cp /mnt/d/projects/JLinAlg/docs/literature/mtag-workflow-development/mtag-workflow-javac21.jar:/mnt/d/projects/JLinAlg/build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.MtagToolsCli --verify /ABSOLUTE/PATH/NEW-analysis-1
```

Outputs and runtime roots must be canonical, disjoint paths. Existing output directories are preserved and rejected. The saved bundle includes complete original inputs and outputs, exact parsing captures, process commands/status/cleanup, runtime identity, native diagnostics, warnings and full file hashes. Saved verification is read-only and repeats native contracts without running Python or fitting again.

## Validation status

The first complete typed 20,000-variant, three-trait source run passed all 60,000 trait-result rows, native replay, and unchanged input/runtime checks. Saved replay passed on Windows Java 21/25 and Linux Java 21. A source-bound Cephes tail control checked 128,696 finite values per JVM across those three JVMs, including 11,951 original-zero outcomes per JVM, plus nonfinite refusals. A fresh exact 33-package private installation also passed.

The option matrix contains 25 cases: 20 admitted original workflows, four expected scientific refusals and one expected skip. The admitted option workflows cover 172,625 trait-result rows. All 20 bundles passed saved native replay through each of Windows Java 21/25 and Linux Java 21. Isolated root commands additionally ran three admitted original analyses, one expected source error and three expected skips, with nine saved replays and 12 help checks. The final native audit also verifies every diagnostic ledger row; all 24 portable saved/tamper protocols passed. Five setup refusal controls confirm forbidden downloads/installations do not create environments and existing outputs remain unchanged.

The Java 25 root preserves the predecessor JSON parser bytes. Its difference from the standalone parser is confined to line-number debug attributes; removing only those attributes produces identical class bytes. No predecessor scientific class is replaced. The live project root source is unchanged; the exact two-edit dispatch/help change is staged inside the isolated root artifacts. Invoke the staged route with the complete classpath, for example:

```bash
java -Xmx2g -cp /mnt/d/projects/JLinAlg/docs/literature/mtag-workflow-development/mtag-root-javac21.jar:/mnt/d/projects/JLinAlg/build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.JLinAlgCli mtag-tools --help
```

Read [the complete workflow evidence](mtag-workflow-development/VALIDATION.json). Verify its sources, archives and retained contracts with `python tools/literature/verify_mtag_workflow_evidence.py`. Add `--replay windows21`, `--replay windows25` or `--replay linux21`, plus a NEW `--out` below `build/literature-review/mtag-runtime`, to run all 23 saved workflow bundles without Python or model fits. Preserve its warnings and receipts; unavailable permitted Java prerequisites mean SKIP3.

All failed and superseded attempts remain retained. These controls reuse declared synthetic fixtures and their derivatives; they do not establish biological accuracy, reconstruct any of the five papers, or complete the 250-paper implementation goal. Live root dispatch, five reviewed paper-role applications, catalog promotion and current catalog/browser gates remain outstanding. The catalog still reports 97 new scoped integrations, 100 including existing adapters and 246 missing tools; MTAG is still in that missing count.
