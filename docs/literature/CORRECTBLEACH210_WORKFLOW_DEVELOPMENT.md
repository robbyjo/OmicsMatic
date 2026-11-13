# CorrectBleach 2.1.0 and native Java histogram correction

> **Development workflow:** build the separate executable below. The canonical OmicsMatic executable is unchanged. The workflow provides all three original CorrectBleach 2.1.0 correction methods and an explicitly selected native Java histogram engine. Catalog admission is pending; no new catalog badge or paper reproduction is claimed here.
>
> **SKIP3: the dependent analysis was skipped.** Supply the missing permitted Java runtime, exact provider, source or input, then retry with a new output directory. If downloading, building or execution is forbidden in your environment, retain the skip. A skipped analysis has no result.
>
> **FAIL2: no correction is admitted.** Read `WARNINGS.md`, `analysis-status.json`, `worker-status.json` when present, and process logs. Retain complete inputs, original outputs and any `unadmitted-original-image.tif`. Resolve the stated cause and retry in a new directory. Do not use a partial or failed correction as a successful result.
>
> **Histogram matching is inappropriate for quantitative intensity measurements.** The request must declare its actual purpose. Use it for an appropriate segmentation or visualization task; do not relabel an intensity analysis to bypass this restriction. This restriction follows the [upstream documentation](https://imagej.net/plugins/bleach-correction).

## Source and paper context

The original implementation is [CorrectBleach 2.1.0, commit f3b48fc](https://github.com/fiji/CorrectBleach/tree/f3b48fc23cfbb06c3437b24519f7149dfc0e12f6). The source profile pins all 19 original files, including eight Java files, and 13 dependency artifacts, source archives and POMs. Its declared dependency profile is ImageJ 1.53t, SciJava Common 2.89.0, Parsington 3.0.0 and EventBus 1.4, resolved through the retained SciJava parent POM 33.2.0. ImageJ 1.54t from the separate core workflow is not substituted.

The provider compiles all eight original Java files unchanged with `--release 8 -g`. Project controller, native kernel and observer classes target Java 17 and are exercised with Java 21 and 25. The published original plugin is independently obtained from SciJava Maven and the Fiji update site; both copies have SHA-256 `65b861ebae641b2aaa235e296f42afdb948d356bbddec92d2808f0c40da1174a`. Independent controls use that published JAR; the analysis provider uses the source-built original classes.

P026 ([primary article](https://doi.org/10.1038/s41586-024-08493-8), PMCID PMC11946911, Methods paragraph 51 in the frozen catalog) reports Fast4DReg XY drift correction followed by the Bleach Corrector plugin for SYTOX Orange time-lapse imaging. It does not report the bleach plugin's edition, correction method, baseline or fit settings. The selected 2.1.0 edition is explicit; it is not an inferred paper setting. Drift correction, MicrobeJ, ilastik masks, segmentation and plotting remain separate tools and analyses. The original software article is [Miura, 2020](https://doi.org/10.12688/f1000research.27171.1).

## Build a private provider and executable

From the repository root, with Python and a permitted JDK:

```powershell
$javaBin = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin'
python tools/literature/correctbleach210/provider.py `
  --java-bin $javaBin --allow-download --allow-build `
  --out build/correctbleach-provider
python tools/literature/correctbleach210/build.py `
  --java-bin $javaBin --provider build/correctbleach-provider `
  --out build/correctbleach-workflow
& "$javaBin/java.exe" -jar build/correctbleach-workflow/correctbleach210-workflow-development.jar --help
```

All output paths must be new. These commands download and compile into private project directories; they do not install into the system JDK, alter a Fiji installation or replace the canonical executable. Image analysis does not automatically download or install prerequisites.

For offline setup, replace `--allow-download` with both `--source-root /path/to/pristine-source` and `--dependency-root /path/to/complete-artifacts`. Every source file and all 13 artifacts must match `source-profile.json`. A directory with only the runtime JARs is not the complete provider input. Without permitted build/download prerequisites, setup returns SKIP3 and dependent analysis must be skipped. Source or edition contradictions return FAIL2. Original compiler output and terminal process receipts are retained.

The original APIs used here work with the tested headless Linux JDK; no display server or full Fiji distribution is required for this fixed workflow. This does not establish headless compatibility for arbitrary Fiji plugins.

## Run an explicit correction

Save this illustrative `correction.json` in a directory whose `prepared-sequence.tif` is the complete intended unsigned 8-bit or 16-bit stack. Relative paths resolve against the JSON file, including provider and output paths. The output parent directory must already exist. Replace the baseline and provenance with your actual experiment; `7` is a synthetic example, not a paper parameter.

```json
{
  "schema_version": 1,
  "version": "2.1.0",
  "imagej_version": "1.53t",
  "mode": "RUN",
  "image": "prepared-sequence.tif",
  "roi_mode": "IMAGE_ROI",
  "provider_root": "build/correctbleach-provider",
  "out": "new-correction-result",
  "method": "SIMPLE_RATIO",
  "engine": "ORIGINAL",
  "baseline": 7,
  "purpose": "INTENSITY_ANALYSIS",
  "input_units": "raw unsigned detector pixel intensities",
  "input_provenance": "Replace with actual acquisition, preparation and included channels/Z planes/time points",
  "axes_policy": "ORIGINAL_SOURCE",
  "execution_policy": "ALLOW",
  "timeout_seconds": 900,
  "heap_megabytes": 2048
}
```

```powershell
& "$javaBin/java.exe" -jar build/correctbleach-workflow/correctbleach210-workflow-development.jar `
  --request correction.json
```

The choices below require deliberate scientific selection; there is no automatic method selection or fallback.

| Method | Required settings and original behavior |
| --- | --- |
| `SIMPLE_RATIO` | `engine: ORIGINAL` and an explicit finite `baseline`. Original unsigned pixel arithmetic subtracts the baseline, including its clipping behavior, and does not add it back. The first frame or volume supplies the reference ROI mean. Nonfinite ratios are retained but unadmitted. |
| `EXPONENTIAL_FIT` | `engine: ORIGINAL`; omit `baseline`. Runs the original exponential with offset, `a*exp(-b*x)+c`, optimizer and initialization. Time coordinate `x` is the original zero-based frame index, not automatically calibrated seconds. |
| `HISTOGRAM_MATCHING` | Omit `baseline`; declare `purpose: SEGMENTATION` or `VISUALIZATION`. Select `engine: ORIGINAL` or explicitly `JAVA_NATIVE_HISTOGRAM210`. Every 256-bin or 65,536-bin lookup is complete. The histogram uses the whole frame or volume, regardless of the selected ROI. |

`roi_mode` must be `IMAGE_ROI`, `NONE`, or `ROI_FILE`. For `ROI_FILE`, also supply `roi_file`; omit that field for the other modes. The original ROI filename is preserved during capture because it can supply the identity of an unnamed ROI. Image ROI side effects, ROI placement on the duplicate, calibration, overlays and complete TIFF bytes are retained.

## Original axes and fit diagnostics

The original code treats an image as 3D time only when both Z and T exceed one. Otherwise it processes the complete linear plane sequence, which can interleave channels. In 3D time it requires `Z*T == stack size` and does not automatically split channels. A multichannel 3D time image can trigger the original warning and unchanged/partial return; the workflow retains that image and refuses admission. Inspect the actual C/Z/T axes and prepare channels explicitly before analysis. The required `ORIGINAL_SOURCE` policy acknowledges this behavior; it does not fix it.

For exponential correction, a project observer overrides only the two original public fitting entry points, calls `super` unchanged and retains the very `CurveFitter` returned to the original `core()`. Input duplication follows the original dispatcher. No second fit is performed. Complete TIFFs are compared independently with the published original dispatcher. Worker diagnostics retain all parameters, X/Y points, residuals, optimizer status, iterations, R-squared, goodness of fit and standard deviation, with numeric strings and raw double bits. Fitted intensities and correction factors are explicitly derived from the retained parameters using the original public equation. Failed optimizer status or nonfinite parameters/factors prevent admission after retaining original outputs.

Original nondecaying sequences raise an ImageJ error and can then throw a null-pointer exception; that failure is retained without a replacement method. A two-frame synthetic sequence nevertheless returns original optimizer `Success`, with original goodness of fit `0.0`; its behavior is preserved. Successful execution and finite parameters do not establish an identifiable or scientifically adequate model. Inspect the actual diagnostics and experimental design before interpreting corrected intensities. No extra R-squared threshold or biological validity is inferred.

## Native Java histogram API

The public `org.jlinalg.imaging.CorrectBleachHistogram210` class has no ImageJ dependency. Use `cdf(int[])`, `matchHistograms(int[], int[])`, or the overload with an explicit comparison budget. The methods preserve original signed 32-bit accumulation, overflow, CDF ties, zero-total NaN/infinity, differing lengths and original null/empty exceptions. The final reference CDF bin is not compared by the original reverse scan; this behavior is preserved. Finite sorted reference prefixes use binary search with the same complete mapping. Other prefixes use the literal reverse scan. Inputs are not modified, and budget exhaustion never returns a truncated mapping.

The full native image engine uses original ImageJ duplication, histogram collection, pixel lookup application and TIFF export around this kernel. Native maps are retained in `native-complete-lookups.bin`; writes are buffered without changing any bin or byte. Earlier Linux unbuffered-export timeouts are retained as failed, unadmitted evidence. A native lookup is not a guarantee that histogram matching is scientifically appropriate for an experiment.

## Results, limits and recovery

Only `analysis-status.json` with `result_admitted: true`, together with a complete successful worker status and matching complete TIFF fingerprint, admits a correction. Exit zero alone is insufficient. `corrected.tif` can exist after failed admission and must not be consumed as a successful result. Original errors are surfaced in the prominent warnings, with full logs retained.

The output captures original input TIFF/ROI bytes, all 19 original source files, provider readiness, all five runtime JARs and license notices. It also retains decoded input/output image state, complete raw pixel hashes, original fitter diagnostics, process logs and terminal exit receipts. Complete TIFFs preserve serialized bytes; the state JSON describes selected public fields and does not claim to enumerate all private ImageJ state or every TIFF tag.

Defaults are 1 GiB combined image/ROI inputs, 2 GiB for the whole workspace, 268,435,456 decoded pixels, 20,000 workspace entries and 500,000,000 native comparisons per lookup. The workspace bound includes captured inputs, original sources, runtimes and logs. Explicit fields `maximum_input_bytes`, `maximum_output_bytes`, `maximum_decoded_pixels`, `maximum_files` and `maximum_native_comparisons_per_map` change those limits. There is no silent truncation. Worker heap and wall time are bounded, and observed descendants are stopped on failure; these controls are not OS CPU/RSS/filesystem containment or producer authentication.

The qualification scripts compare all three original methods on complete synthetic unsigned 8/16-bit 2D/3D time stacks, including metadata and ROI behavior, and exercise original failures, native maps, protected paths and runtime policies. `test_native.py` compares all 1,048 original histogram cases: 212,763 complete lookup values and 426,993 raw CDF double values, including original exceptions. These controls cover explicit populations; they do not qualify every possible image, Fiji plugin, paper workflow or biological use.

Windows Java 21, Windows Java 25 and Linux Java 21 each passed 21 complete original image comparisons, six complete native image comparisons and 24 request/resource/failure controls. The twelve setup refusals and an actual fixed-source/dependency download and build also passed. With buffering, the Linux native 16-bit export completed in about 4.2 seconds at the same 240-second limit that the earlier unbuffered attempt exceeded; the complete original histogram reference packet remained byte-identical on all three runtimes.

Retained qualification and complete source/dependency artifacts are in `correctbleach210-workflow-development/VALIDATION.json`, `SHA256SUMS` and `controls.zip`. To verify and rerun the complete actual controls offline, with explicit permission:

```powershell
python tools/literature/correctbleach210/verify.py `
  --java-bin $javaBin --allow-build --allow-analysis `
  --out build/new-correctbleach-offline-replay
```

Omitting either permission flag verifies retained evidence and returns SKIP3 before the dependent build/analysis. The replay does not require network access or a system installation. Historical failed export and incorrect two-frame test-expectation attempts remain labeled and retained separately; their outputs are not silently promoted into successful qualification.
