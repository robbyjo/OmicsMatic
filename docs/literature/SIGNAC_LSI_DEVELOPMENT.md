# Typed Signac LSI development

The standalone development command now takes sparse integer peak counts through
native Java TF–IDF normalization and feature selection, then runs an explicitly
selected upstream SVD method. Java independently checks its numerical outputs.
This is a usable **LSI operation**, not a complete Signac integration. The
catalog count remains **68 new integrations, 71 including existing adapters**;
Signac remains missing pending its other paper roles and complete workflow gates.

**Read these warnings before running:**

| Condition | Behavior and required action |
| --- | --- |
| **Unicode paths on legacy Windows launchers** | Direct Java/R argument conversion can change paths before application code receives them. Use the UTF-8 JSON request entry point below, put the request file itself in an ASCII-only path, and put exact Unicode data/output paths inside the JSON. The R child also receives its values through a UTF-8 file. Do not accept transliterated paths. |
| **SKIP3 — runtime unavailable** | Missing Rscript, either explicit library directory, a required package edition or a working DLL skips dependent LSI. Read `WARNINGS.md` and worker logs. Supply a permitted exact private runtime and rerun into a new directory. If installation is forbidden, leave the analysis skipped. |
| **Historical source profiles** | These execute an unmodified archived `RunSVD.default` function with current pinned dependencies. They are not historical whole-package installations, historical assay wrappers, or complete reproductions of the papers. Choose them explicitly; no edition is substituted silently. |
| **FAIL2 — invalid input, timeout or inconsistent output** | No successful result tables are admitted. Retain the attempt, read the named cause and required action, correct it and use a new output directory. Never relabel partial worker files as results. |
| **Development runtime scope** | Exact package versions, source bodies, worker resources and numerical outputs are checked. Complete native DLL/host dependency closure is not certified. Runtime fits were tested on Windows; Linux tests covered native methods and captured verification. |
| **Other Signac operations** | No fragment counting, nucleosome/TSS QC, gene activity, motifs/chromVAR, differential accessibility, peak–gene links, WNN, clustering, coverage plots, or automatic downstream component selection is performed. Continue those implementations separately. |

All attempts, explicit recovery instructions and successful evidence are in
[signac-lsi-development](signac-lsi-development/). The prior
[native normalization milestone](SIGNAC_NORMALIZATION_DEVELOPMENT.md) remains
unchanged.

## Run the synthetic example

The example has 60 features and 45 cells. Its deliberately unsorted Matrix Market
input includes duplicate coordinates and a stored zero. It contains no study or
patient data. Run from the repository root in PowerShell, using a **new** output
directory:

The verified output already exists in this workspace at
`build/signac-lsi-request-demo-NEW`; the supplied `VERIFY` request can inspect it.
Before fitting again, edit the `out` value in `request.json` to an unused directory.
The command deliberately refuses to overwrite the existing evidence.

```powershell
$signacLsiJar = 'build/literature-review/signac-runtime/lsi-request-build-3/omicsmatic-0.3.7-signac-lsi-request-development.jar'
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' -Xmx512m -cp $signacLsiJar org.jlinalg.cli.SignacLsiRequestCli --request examples/signac-lsi/request.json
```

The supplied UTF-8 JSON selects the synthetic files, private runtime,
`1.17.1-package-rspectra`, five components, seed4901 and the new output directory
`build/signac-lsi-request-demo-NEW`. Every relative path resolves against the
request file's directory. Edit the output value for each new attempt. For example:

```json
{
  "schema_version": 1,
  "mode": "FIT",
  "options": {
    "counts": "counts.mtx",
    "features": "features.tsv",
    "cells": "cells.tsv",
    "out": "../../build/signac-lsi-request-demo-NEW",
    "rscript": "C:/Program Files/R/R-4.6.1/bin/Rscript.exe",
    "r-library": "../../build/literature-review/signac-runtime/reference-setup-1/R-lib",
    "r-base-library": "../../build/omics-sctransform-r",
    "profile": "1.17.1-package-rspectra",
    "components": 5,
    "seed": 4901
  }
}
```

This uses the private reference installation prepared in this workspace. Required
`packageVersion` values are Signac 1.17.1, Matrix 1.7.5, SeuratObject 5.4.0,
RSpectra 0.16.2 (CRAN package spelling `0.16-2`) and irlba 2.4.1. Both supplied
library directories must exist; their contents are not installed or modified by
the analysis command. R runs with `--vanilla`, explicit library paths, disabled
profile/library overrides and single-thread settings. Ordinary host environment
variables are preserved because a nearly empty environment caused this Windows
R runtime to crash at shutdown. Environment variable values are not logged.

Where installation is permitted and the existing base library is available, the
reference setup helper can prepare a new private installation:

```powershell
python tools/literature/setup_signac_reference.py --out build/literature-review/signac-runtime/reference-setup-NEW --allow-install
```

Inspect that setup's result, package versions and warnings before using its
`R-lib` directory. The analysis command's exact-version checks remain authoritative.
If prerequisites cannot be installed or supplied, skip LSI. The setup helper is
a reference-runtime preparation aid, not complete scientific runtime admission.

The final candidate is 164,425,447 bytes with SHA-256
`e7ab3b904c2f95ec33c07da0c9172a64cec51380965e6ccbf703920a19b8835b`.
It adds 13 classes and 8 resources while preserving all 19,631 entries of the
validated normalization parent. The request entry point alone adds one class and
preserves all 19,651 entries of the validated LSI core candidate. It has standalone Java entry points; root
dispatch, the canonical executable, releases and the public website are unchanged.
To rebuild it in this development workspace:

```powershell
python tools/literature/build_signac_lsi_development.py --out build/literature-review/signac-runtime/lsi-build-NEW
python tools/literature/build_signac_lsi_request.py --out build/literature-review/signac-runtime/lsi-request-build-NEW
```

The core builder requires the retained validated normalization parent JAR and source pins.
The request builder deliberately targets the frozen `lsi-build-5` parent; its
validation checks require that parent in addition to any new core build. A fresh
checkout without those build prerequisites cannot reproduce this candidate using
that command alone. Do not confuse a source checkout or a privately built
candidate with a released package.

## Inputs and options

`--counts` accepts uncompressed Matrix Market `coordinate integer general`,
features by cells. `--features` and `--cells` each contain exactly one unique ID
per line, in matrix order. A three-column 10x feature table must first be converted
to an explicitly reviewed one-column ID file; no automatic column guessing occurs.
IDs are preserved, including tested spaces, underscores and Unicode. Matrix
coordinates may be unordered. Duplicate counts are summed exactly and reported;
explicit zero coordinates remain stored.

The intake rejects negative/fractional counts, malformed UTF-8, duplicate IDs,
dimension disagreements, extra/missing entries and feature/cell totals above
2^53−1. Current command bounds are 2 million features, 2 million cells, 10 million
input entries, 2 billion bytes per input and 50 million singular-triplet values.
These are admission bounds, not a claim that every accepted shape fits every
heap. Choose adequate memory; larger datasets need a separately admitted larger
workflow. No genome, species, peaks, count units or biological filters are inferred.

| Option | Meaning |
| --- | --- |
| `--profile 1.17.1-package-rspectra` | Full installed current Signac `RunSVD`, using `RSpectra::svds`; PCA is disabled. |
| `--profile 1.6.0-source-irlba`, `1.7.0-source-irlba`, `1.9.0-source-irlba`, `1.14.0-source-irlba` | Exact archived matrix function using `irlba::irlba` with pinned current dependencies. Historical package/assay behavior is not claimed. |
| `--tfidf-method 1` / `--tfidf-scale 10000` | Native source-compatible count normalization. Methods 1–4 are available; computed IDF uses total counts. The command does not accept precomputed IDF. |
| `--cutoff-percentile 5` | Default `q5` semantics: retain ECDF strictly greater than 0.05. |
| `--cutoff-count 5` | Alternative numeric cutoff: retain feature counts strictly greater than 5. These cutoff flags are mutually exclusive. |
| `--components 50` | Requested SVD rank. Upstream clamps it to cells minus one. Effective rank must remain below both selected matrix dimensions. No additional rank reduction occurs. |
| `--scale-embeddings true` | Center and sample-SD-scale raw left singular vectors across cells. LSI does not multiply them by singular values. |
| `--clip 1.2` | Optional symmetric clipping after scaling. With unscaled embeddings the source ignores clipping; a warning records this. |
| `--tolerance 0.00001` | Solver argument. The 1.6.0/1.7.0 archived functions do not forward it; their irlba default applies and is explicitly reported. |
| `--irlba-work N` | Historical profiles only. Default is three times effective rank, preserving the source's lazy default after cell-based clamping. |
| `--seed 1` / `--timeout 3600` | Explicit R RNG seed and owned-process timeout in seconds. |

For example, P051's documented numeric feature cutoff can be selected with
`--profile 1.14.0-source-irlba --cutoff-count 5`. This reproduces this matrix-method
component under the declared dependencies; it does not reproduce P051's full
multiome workflow or original R installation. P039/P099 explicitly report TF–IDF
and SVD too. The command emits all computed components; it does not silently omit
LSI dimension 1 or select downstream dimensions 2:30/2:50.

The solver change is documented in the official
[Signac changelog](https://stuartlab.org/signac/news/). That release also removed
`RunChromVAR`; a future motif adapter must handle the relevant paper editions
explicitly rather than assume the current package provides every historical method.

## Outputs and verification

Successful result tables are `result-embeddings.tsv`, `result-loadings.tsv` and
`result-singular-values.tsv`. The bundle also retains the original sparse inputs,
feature counts/ECDF/selection, normalized sparse exchange, raw singular vectors,
original R reduction object, effective options, package versions, session info,
stdout/stderr, warnings, manifests and checksums. Failures retain diagnostics and
partials; those are not admitted result tables. An existing destination is never
overwritten. `manifest.json` is authoritative for the typed verifier; the inherited
transaction helper also writes `manifest.yaml`.

Java verifies both sparse singular-triplet residuals, orthogonality, singular
value order, feature/cell identities, source-based scaling/clipping and the
feature-based reported SD denominator. Residuals are relative to the input
Frobenius norm, with limit `max(1e-7, 50 × effective tolerance)`; orthogonality error
must not exceed `1e-6`. Postprocessing comparison allows absolute/relative error
`3e-12 × max(1, |value|)` or 32 ULP. Source NaN from a constant scaled component
does not become an admitted finite result. These checks do not certify biological
accuracy or precision of every small singular component.

```powershell
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' -cp $signacLsiJar org.jlinalg.cli.SignacLsiToolsCli --verify build/signac-lsi-request-demo-NEW
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' -cp $signacLsiJar org.jlinalg.cli.SignacLsiRequestCli --request examples/signac-lsi/verify-request.json
```

The first form is the lower-level value-option entry point for an ASCII path;
replace its directory with your actual analysis path. The second verifies the
supplied example output through UTF-8 JSON. For a different analysis, edit the
`analysis` field in the `VERIFY` request; relative paths use that request's directory.

Captured verification needs neither R nor the original input paths. It works on
the tested Windows/Linux JVMs and on relocated copies. It checks the complete
artifact inventory, bundled worker/source resources, replayed native
normalization/selection, effective solver settings, diagnostics and result-table
consistency. SHA-256 establishes integrity, not a signature or independent proof
that an upstream process historically ran.

## Validation and retained limitations

The final request candidate passed 1,650 native/request tests on each of Windows Java 21,
Windows Java 25 and Linux Java 21, including all prior normalization controls.
Java 17 compilation and Javadoc with warnings as errors passed. Twenty-two
successful upstream fits on the LSI core candidate exercised all five profiles, scaled/unscaled/clipped
embeddings, all four normalization methods, rank clamping and both Windows JVMs.
Independent original-R pipelines matched normalization, selection and solver
results with sign alignment. Those 22 core fits were fresh executions. Every
core class/resource remains byte-identical inside the final request candidate;
the extra request class has its own tests and actual fits below.
An earlier validation generation explicitly reused and reverified unchanged fits
after correcting the independent collector's JSON-library load order; those older
fits were not relabeled as executions of the final candidate.

There were 69 captured-bundle verifications across the three JVM platforms, including
three checks of a relocated copy, and 27
rejections of deliberately modified scientific outputs after their checksums were
recomputed. Missing executable/library cases returned SKIP3 with required actions.
Actual live owned R processes were observed and stopped in interruption and
timeout tests, and no results were admitted. Three further fresh fits through the
request entry point checked the documented example, Unicode identities/paths and
a historical source profile on Windows Java25. Each was also verified on all
three JVM platforms.
The exact checked-in FIT and VERIFY JSON files were also executed successfully;
their result tables matched the independent reference.

The first development fit was rejected because the original R process crashed at
shutdown under an over-restricted environment. Both that attempt and the
environment-isolation probes are retained. An intermediate candidate passed ASCII
checks but was correctly rejected for escaping Unicode labels at export; the
final worker writes explicit UTF-8 bytes. Separate Java/R trailing-argument path
conversion failures led to UTF-8 request files at both process boundaries; those
failed attempts and argument-codepoint probes are retained. Worker source and
output paths are relative to its owned working directory to avoid additional
native-encoding conversions in source lookup and compressed R-object output.
The failed independent collector and
all successful original fits are retained too. Earlier historical assay-wrapper
skips and annotation-index warnings remain in the normalization evidence; this
LSI matrix route does not repair or bypass those assay/genome requirements.

The next work remains fragment/feature quantification and QC, gene activity,
motifs/chromVAR, peak–gene linking and differential accessibility, then complete
typed workflow/runtime gates and catalog promotion. The additional P134 Signac
use still needs source curation; P007 SignAC remains an institutional name.
No release, deployment, GitHub push, full Signac credit or completion of the
250-paper implementation goal is claimed.
