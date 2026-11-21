# GCTA: native components and explicit original-tool workflows

Build the current source with `gradlew.bat executableJar` (Windows) or
`./gradlew executableJar` on a host with a permitted JDK. Use the actual JAR
path reported by the build. The published 0.4.0 release predates this command.
The global entry point is now:

```powershell
$jar = 'C:/absolute/path/to/omicsmatic-0.4.0.jar'
java -jar $jar gcta-tools --help
java -jar $jar gcta-tools --request C:/absolute/path/to/request.json
java -jar $jar gcta-tools --verify C:/absolute/path/to/complete-bundle
```

The retained standalone entry point is `org.jlinalg.cli.GctaWorkflowCli` with
the same arguments. Select the engine, model and source edition explicitly;
the workflow never switches engines automatically.

| Request model | Engines and prepared inputs |
| --- | --- |
| `GRM` | Native or exact original GCTA; autosomal diploid PLINK hard calls |
| `SPARSE_GRM` | Native or exact original GCTA; prepared dense GRM and literal sample identities |
| `PCA` | Exact original GCTA; prepared GRM |
| `COJO_SELECT`, `COJO_JOINT`, `COJO_CONDITION` | Exact original GCTA; prepared genotypes and complete literal summary statistics, plus declared extract/conditioning inputs |
| `REML` | Exact original GCTA; one or multiple prepared GRMs, complete phenotype and optional explicit covariates |
| `LD_SCORE`, `LD_SCORE_REGION` | Exact original GCTA; prepared genotypes and explicit LD options |
| `FASTGWA_BINARY` | Exact original GCTA; complete explicitly coded 0/1 phenotype, sparse GRM and prepared genotypes; original null model retained |
| `MTCOJO` | Exact original GCTA; ordered traits, all 22 reference/weight LD shards, explicit GSMR or supplied causal effects and `effect_scale:"as_supplied"` |

Start with the [native GRM file example](../../examples/gcta-workflows/README.md).
The [mtCOJO examples](../../examples/mtcojo/README.md) provide synthetic inputs,
an original-runtime request builder and the separate native prepared-parameter
conditional API. That API does not estimate GSMR, LD scores or heritability.
The [closed request source](../../src/main/java/org/jlinalg/cli/GctaWorkflowContracts.java)
defines fields, defaults and limits; arbitrary upstream flags are not accepted.

RUN captures complete declared inputs and available original objects/logs,
including partial/null/failure exports. VERIFY is offline: native GRM is
recomputed, while original models receive deterministic intake, integrity and
declared export-invariant checks. It does not independently refit an original
model or authenticate coordinated replacements. REPLAY verifies the parent,
uses a new output outside it and preserves the parent. Crossing Windows/Linux
runtime flavors requires explicit `allow_runtime_flavor_change:true` and the
corresponding exact runtime; source calibration can differ by runtime flavor.

## Warnings and required actions

**Only exact GCTA 1.94.1 Windows/Linux runtimes are qualified.** Native requests
need no external GCTA installation. Original requests require the exact permitted
runtime named in the request; Windows also needs the matching adjacent
`libomp.dll`. Obtain the [official v1.94.1 release](https://github.com/jianyangqt/gcta/releases/tag/v1.94.1)
and read its GPLv3 `LICENSE`. No executable or runtime DLL is bundled here.

```text
Windows EXE SHA256 2529bccb2f685332f115726c3cb2bf8cab91f12ae3344a991e3a52d751bdb07e
Windows DLL SHA256 ee9ce7570217bbccf87d569eb06e71fe9ec246899d2b2fe4fd918215e29e1c79
Linux ELF SHA256 dbb2b5e5d51e278328ff297a403d3dd8ee5f5280f0b6d3d8db74b3e7fead7b49
```

**SKIP3: dependent analysis skipped.** Supply the named exact permitted runtime/
input or retain the skip if unavailable or installation is forbidden. Do not
substitute a nearby edition, beta, population or algorithm. Unimplemented work
remains unfinished rather than becoming a fabricated prerequisite skip.

**FAIL2: NO COMPLETE RESULT.** Read `WARNINGS.md`, `analysis-status.json`,
`manifest.json`, model objects and every original worker log. Preserve inputs,
null/partial/model exports and all diagnostics; fix the reported cause and retry
into a **new directory**. Exit zero from an original program alone does not
establish convergence or finite scientific results. A failed/skipped bundle
cannot pass VERIFY. Resource limits are logical/monitored bounds, not OS quotas.

- Preserve literal identifiers, alleles, ancestry, ploidy, coding, thresholds and
  source editions. No strand/ID repair, imputation or undeclared filtering occurs.
- Valid converged unconstrained REML variance/h2 can fall outside 0..1; do not
  clamp it. Original rounded-to-zero exports retain their precision warnings.
- Source COJO effective sample sizes are positive real values, not necessarily
  integer or bounded by the reference FAM sample count. Signed adjusted LD
  scores remain unchanged.
- GCTA1.94.1 mtCOJO retains prevalence metadata but performs **no liability
  conversion**. Its supplied-effect absolute-100 sentinel, integer-parsed LD-FDR,
  low-instrument NaN failure and strict gzip requirements are described in the
  mtCOJO example. Failed GSMR is never replaced with an invented estimator.
- Individual author studies with older/unstated editions, unrecovered preparation
  or missing settings remain unqualified. The source crosswalk preserves these
  limits, including wheat ploidy, reference-panel labels and ambiguous cutoffs.

See the [global qualification and source-role checkpoint](../literature/GCTA_GLOBAL_CHECKPOINT_2026-11-20.md).
GCTA and COJO are labels for the same software family, not two independent
packages. The [catalog admission scope](../literature/GCTA_CATALOG_INTEGRATION.md)
credits one GCTA family and a COJO alias with zero extra credit, with exact
source-role and transaction evidence. No complete paper or biological
reproduction is claimed.
