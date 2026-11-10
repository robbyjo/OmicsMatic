# SNPRelate workflows

The development adapter runs original **SNPRelate 1.46.0** for genotype PCA, KING-robust relatedness, LD pruning and GRM construction. Java checks complete input identities, hard calls, allele/missingness statistics and QC populations. It independently recomputes KING coefficients and checks PCA output dimensions and mathematical invariants. The upstream software performs the PCA, LD and GRM estimations.

**Read the disposition before using results.** Require exit `0`, `COMPLETED.json` with `result_admitted=true`, and a successful `--verify`. A missing permitted runtime returns **SKIP3** and skips dependent analysis. A source/input/capacity/scientific failure returns **FAIL2** and admits no result. Both give a prominent `WARNINGS.md` and recovery instructions. Empty QC populations are rejected before analysis. Never fill source NaNs, repair identifiers, change options or switch software versions to suppress an error.

This is a development integration. The released `omicsmatic-0.3.7.jar` remains unchanged. The root supplement uses that JAR as a dependency; the standalone SNPRelate JAR needs only Java17+ for its Java functionality. Original analyses additionally require the explicitly pinned private Windows R runtime. Saved verification works on Windows and Linux without R, downloads or installation.

## Quick start

Run commands from the repository root. Choose **new** output paths for every attempt; existing evidence is preserved.

```powershell
java -jar docs/literature/snprelate-tools-development/snprelate-javac21.jar --help
Expand-Archive examples/snprelate-workflows/inputs.zip build/literature-review/snprelate-runtime/demo-inputs-1
```

The example genotypes are project-owned synthetic controls, with explicit original ID maps. They are not participant data or biological findings.

An analysis needs an exact runtime profile. If one already exists, use it. Otherwise, where downloads and installation are permitted, prepare a **new private** library with the source-checkout helper:

```powershell
python tools/literature/setup_snprelate_reference.py --out build/literature-review/snprelate-runtime/my-runtime-1 --allow-download --allow-install
java -jar docs/literature/snprelate-tools-development/snprelate-javac21.jar --export-runtime-helper build/literature-review/snprelate-runtime/my-helper-1
python build/literature-review/snprelate-runtime/my-helper-1/prepare_snprelate_runtime.py --setup build/literature-review/snprelate-runtime/my-runtime-1/COMPLETED.json --out build/literature-review/snprelate-runtime/my-profile-1
```

The tested setup requires Windows x86_64 and the named R4.6.1 installation. It installs SNPRelate1.46.0, gdsfmt1.48.2 and RhpcBLASctl0.23-42 privately. The exported helper only checks and pins the supplied existing runtime; it installs nothing. Shared R libraries are not changed. **If the required R/package installation is unavailable or forbidden, keep the dependent analysis skipped.** Supply the exact permitted prerequisites, then retry in a new directory. There is no automatic switch to WSL, PLINK, another package version or native ordinary PCA.

Save this valid JSON as `snprelate-pca.request.json` in the repository root:

```json
{
  "schema_version": 1,
  "version": "1.46.0",
  "mode": "pca",
  "bfile": "build/literature-review/snprelate-runtime/demo-inputs-1/mixed/cohort",
  "runtime_profile": "build/literature-review/snprelate-runtime/my-profile-1/runtime-profile.json",
  "out": "build/literature-review/snprelate-runtime/my-pca-1",
  "layout": "snp-first",
  "autosomes_only": true,
  "remove_monomorphic": true,
  "maf": "NaN",
  "missing_rate": 0.01,
  "threads": 1,
  "options": {
    "algorithm": "exact",
    "eigen_count": 3,
    "bayesian": false,
    "eigen_method": "DSPEVX",
    "aux_dim": 6,
    "iterations": 3,
    "seed": 20261110,
    "need_genmat": true
  },
  "limits": {
    "input_bytes": 10000000,
    "output_bytes": 50000000,
    "output_files": 150,
    "samples": 100,
    "variants": 20000,
    "rows": 2000000,
    "pair_variant_operations": 1000000000,
    "working_bytes": 1000000000,
    "wall_seconds": 180
  }
}
```

```powershell
java -Xmx1g -jar docs/literature/snprelate-tools-development/snprelate-javac21.jar --request snprelate-pca.request.json
java -Xmx1g -jar docs/literature/snprelate-tools-development/snprelate-javac21.jar --verify build/literature-review/snprelate-runtime/my-pca-1
```

Relative paths resolve against the request file's directory. `bfile` names a complete `.bed/.bim/.fam` prefix. The example limits suit the small demonstration; real data require deliberate limits appropriate to the sample/variant population, storage and available memory. The input profile requires chromosome/position sorted, numeric chromosomes1..26 and distinct A/C/G/T biallelic diploid alleles. Unsupported inputs fail; they are not sorted, renamed, recoded or partially admitted. Sample order is the supplied FAM order, and duplicate FID/IID pairs fail. Repeated IIDs in different families and literal `NA`/`NaN` strings remain intact.

The root CLI route uses the supplement **first** on the classpath. In PowerShell:

```powershell
java -cp "docs/literature/snprelate-tools-development/snprelate-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar" org.jlinalg.cli.JLinAlgCli snprelate-tools --help
```

Use `:` between classpath entries on Linux. Root and standalone commands reach the same scientific implementation. This command does not imply the released JAR already contains the new route.

## Choose the requested workflow explicitly

All requests require the common fields shown above, one tested thread, physical `snp-first` or `sample-first` GDS layout, explicit QC and complete capacities. Change `mode`, its entire `options` object and the new output path together.

| Mode | Required `options` | Meaning |
|---|---|---|
| `king-robust` | `{"family_mode":"singletons"}` or `{"family_mode":"original-fid"}` | SNPRelate-hosted KING. Explicit common FIDs select the within-family formula; singleton mode uses the between-family formula. Placeholder FIDs are not inferred to represent known families. |
| `ld-pruning` | `{"method":"composite","start":"random.f500","seed":20261110,"window_bp":500000,"window_variants":null,"ld_threshold":0.2}` | Original pruning using absolute LD in the specified windows. Methods are `composite`, `r`, `dprime`, `corr`; starts are `random.f500`, `random`, `first`, `last`. This is not an r-squared threshold or PLINK clumping. |
| `grm` | `{"method":"GCTA"}` | Original `GCTA`, `Eigenstrat`, `EIGMIX`, `Weighted`, `Corr` or `IndivBeta`. The source reports `Weighted` as `EIGMIX`; both requested and reported values are retained. |
| `pca` | The complete PCA object above | Original exact or randomized PCA, with explicit normalization, components, solver, random seed and projection settings. |

For randomized PCA, use `algorithm:"randomized"`, a positive component count, `bayesian:false`, `need_genmat:false` and the explicit unused `DSPEVX` solver sentinel. This profile requires `eigen_count <= aux_dim <= samples` and `aux_dim*(iterations+1) <= QC variants`. Exact PCA requires `need_genmat:true` so saved verification can check its eigen equations. An exact `eigen_count:0` explicitly requests all sample components. Settings are forwarded to the source; no paper defaults are inferred.

The common `maf` and `missing_rate` accept JSON numbers or the explicit strings `NaN`, `Inf`, `-Inf`. Nonfinite numeric cutoffs disable those thresholds in this source version. With monomorphic removal false and both cutoffs nonfinite, original QC is bypassed, even for all-missing SNPs. Active QC rejects nonfinite MAF. Layout changes the source's floating-point missing-rate expression and can change a near-boundary selection. The adapter preserves this behavior and reports every input variant's disposition.

## Results, warnings and recovery

`inputs/` retains the complete original BED/BIM/FAM bytes and a declared dosage transport. Numeric GDS row keys are explicit transport identifiers; `original-sample-map.tsv` and `original-variant-map.tsv` retain original FID/IID and SNP IDs. This avoids silently accepting the original BED importer's conversion of a literal SNP ID `NA` to an empty ID. There is no failed-import fallback or repair of the original input.

`worker/` retains raw upstream results, complete genotype/statistics/population reports, RDS results, source warnings and process logs. `native-variant-qc.tsv` reports all input variants; `scientific-audit.json` records the independent checks and limitations. Failed execution outputs remain under `unadmitted-worker/`. Read the top-level `WARNINGS.md` first. Invalid requests whose output path cannot yet be bound report their error on stderr.

For PCA, the adapter preserves the original sample eigenvectors; it does not rescale them into another score convention or change signs. `DSPEVX` can return a full-length eigenvalue vector whose uncomputed tail is NaN. Those placeholders are retained and distinguished from requested nonfinite components. Randomized output can contain zero padding beyond its projected rank; those zeros do not establish an exact complete spectrum. Raw small negative eigenvalues are retained. Native checks of exact PCA use a fixed1e-8 orthogonality/relative eigen-equation bound and1e-12 variance/trace consistency bound; they do not perform an independent decomposition or modify source values.

Unassessable source off-diagonal values, or duplicate original variant IDs that make ID-based downstream extraction ambiguous, produce `downstream_eligible=false`. Preserve those results and skip the affected dependent analysis. Review the declared study population and informative genotype/QC requirements before a new request. Do not fill NaNs, merge identifiers, suppress failures or change thresholds merely to obtain output. Empty QC or selected SNP populations admit no analysis. Setup and analysis never overwrite earlier attempts.

The runtime profile pins the complete supplied R distribution and private library before and after execution. Windows system DLLs outside those roots are not independently pinned. Saved verification checks byte integrity, recorded commands/parameters and scientific contracts; it authenticates no producer and executes no original software. Capacity estimates and output/time monitoring are logical bounds, not hard OS memory/disk quotas. Production-cohort performance and biological validity are not certified.

## Evidence and paper scope

Validation includes240 initial LD/GRM/KING requests:186 original analyses and54 empty-QC refusals, with480 saved checks on Windows/Linux. The current four-workflow verifier replays all240 preceding bundles. Eighty additional PCA analyses compare every reported eigenvalue, variance proportion, loading, original sign, placeholder and returned genotype-matrix field with separately collected source results. Twenty request/input/skip JUnit methods run on Windows21/25 and Linux21, and40 deliberately resealed scientific/protocol mutations are rejected across two compiler builds and both hosts. Root routing, predecessor commands and permitted missing-runtime skips have separate records in the final evidence bundle.

The [corrected primary-role supplement](omics-2023-2026/snprelate-genesis-primary-roles-v2.json) rechecks all250 fixed papers using citation-normalized names while retaining raw primary paragraph hashes. It identifies SNPRelate in **P130, P170, P191 and P216**. The earlier three-paper list is retained with a prominent [correction](snprelate-native-development/CENSUS-CORRECTION.md); it missed P170 because a citation number directly followed the package name.

| Paper | Reviewed SNPRelate use | Scope that remains explicit |
|---|---|---|
| P130 | Named in a collective GRM/null-model package list | Reports1.30.1; no function is uniquely assigned to SNPRelate. This1.46.0 profile does not reproduce that historical edition or establish SNPRelate null-model fitting. |
| P170 | Genotype PCA in two perturbation cohorts after separate PLINK pruning | SNPRelate version, exact function, component count and solver are unreported. PLINK pruning, Picard liftover and prior genotype QC remain separate steps. |
| P191 | KING-robust pairwise relatedness for non-European GENESIS PC-AiR | This is the SNPRelate host. European PLINK2 PCA and SAIGE sparse GRMs are distinct. Versions, family and QC settings are not inferred. |
| P216 | Explicit `snpgdsLDpruning` before PLINK2 principal components | Version, LD measure, windows, seed and thresholds are unreported. The reference implementation must be chosen explicitly; PLINK2 PCA remains downstream. |

GENESIS is a separate package used in five papers. Its private runtime is prepared, but runtime installation alone implements none of its PC-AiR, PC-Relate or model-fitting workflows. This SNPRelate integration earns no GENESIS credit or claim of paper-result reproduction. The complete250-paper toolbox goal remains unfinished.

The original arithmetic/software is attributed to Xiuwen Zheng's GPL-3.0 SNPRelate project; see the [official package page](https://bioconductor.org/packages/release/bioc/html/SNPRelate.html). Original R programs, packages, author example data and article prose are not bundled in the development evidence. Project-owned synthetic inputs and upstream-generated results are included for offline replay.
