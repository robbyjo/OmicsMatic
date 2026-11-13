# Original ImageJ 1.54t Java workflow

> **Development integration.** Use the separate executable built below. The canonical OmicsMatic executable has not been replaced. This is the complete original **ImageJ 1.54t core**, not a recovered Fiji distribution or an ImageJ2 edition. External Fiji plugins and paper-specific custom macros need separate providers and qualification. No new catalog badge or paper reproduction is claimed.
>
> **SKIP3 means the dependent analysis was skipped.** Provide a permitted full Java runtime with AWT and a working display, or the missing declared plugin/input, then retry in a new output directory. If installation or display use is forbidden, keep the skip and do not interpret it as an analysis result. **FAIL2 means no result is admitted:** retain the exact macro, inputs, outputs, `WARNINGS.md`, `analysis-status.json`, `worker-status.json` when present, and process logs. Fix the stated cause and retry in a new directory. Never overwrite failed evidence.

This integration executes the caller's exact ImageJ macro through the original Java interpreter and command registry. It also supplies the original public native Java APIs on the development JAR classpath. It is not a replacement implementation of a small selection of filters. Caller macros can use the original core's stack processing, projections, channel/composite displays, ROI functions, measurements and other commands. Availability does not imply that every command, image format or external plugin has been tested.

## Source and paper context

The recovered original source is [ImageJ commit 6797cee](https://github.com/imagej/ImageJ/commit/6797cee444df5e9b0f3d5872d6e87ae748ae9966), the 16 May 2026 version 1.54t release. `source-profile.json` pins all 520 original files and the complete original Git archive. The official [ImageJ introduction](https://imagej.net/ij/docs/intro.html) describes the original core as public domain; external plugins have their own licenses. The archive includes the original precompiled Mac adapter resources. Mac execution is not qualified here.

The original Ant build uses source/target 1.6, unsupported by the permitted modern compilers. The provider compiles all 353 original `ij/**/*.java` files unchanged with `--release 8 -g`; the project workflow and diagnostic shims target Java 17 and are tested with Java 21/25. This compiler-target difference is recorded, not concealed. Every original core JAR entry except its replaced executable manifest is copied unchanged into the development JAR and checked before analysis.

P040 ([primary article](https://doi.org/10.1038/s41586-026-10954-1), PMCID PMC13645638, Methods paragraph 31 in the frozen catalog) reports ImageJ 1.54t for composite-channel image processing, outlier removal, median/Gaussian filtering and display contrast; cytomapper is an alternative visualization route. The paper's original inputs, full custom workflow and all settings are not recovered by these controls. Parameters in examples are explicit illustrative choices. The 51-paper Fiji/ImageJ family also includes other reported core/distribution versions and separate plugins; this edition does not silently substitute for them. In particular, ImageJ's median-based Remove Outliers command is not the separate IMC preprocessing hot-pixel procedure.

## Build the complete original provider

From the repository root, with Python and a permitted full JDK:

```powershell
$javaBin = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin'
python tools/literature/imagej154t/provider.py `
  --java-bin $javaBin --allow-download --allow-build `
  --out build/imagej-original-provider
python tools/literature/imagej154t/build.py `
  --java-bin $javaBin --provider build/imagej-original-provider `
  --out build/imagej-workflow
& "$javaBin/java.exe" -jar build/imagej-workflow/imagej154t-workflow-development.jar --help
```

All output paths must be new. These commands make private files; they do not install into the system JDK or replace another ImageJ installation. Download is optional: supply `--source-root /path/to/complete/pristine/source` instead of `--allow-download`. The source directory must contain all 520 exact original files, without `.git`, compiled output or edits. The public evidence bundle also retains the complete original Git archive for offline restoration by `verify.py`.

Without `--allow-build`, or without a local source and download permission, the provider returns SKIP3. Missing compilers/sources/download access also skip dependent work. Source contradictions fail with FAIL2. The provider retains original compiler warnings and records terminal process exits. Source-build/download timeouts stop the owned process and retain diagnostics.

## Run a macro

Save this illustrative `job.ijm`. The selected Gaussian parameter is **sigma in pixels**; the adapter does not reinterpret parameters from prose or scale by calibration implicitly.

```javascript
setBatchMode(true);
root = getArgument();
open(root + "/inputs/channels.tif");
run("Remove Outliers...", "radius=1 threshold=50 which=Bright stack");
run("Median...", "radius=0.5 stack");
run("Gaussian Blur...", "sigma=0.5 stack");
saveAs("Tiff", root + "/results/filtered.tif");
run("Z Project...", "projection=[Max Intensity]");
saveAs("Tiff", root + "/results/maximum.tif");
return "complete";
```

Save `job.json` beside the macro. Relative paths resolve against the request file's directory. The declared output's parent directory must already exist.

```json
{
  "schema_version": 1,
  "version": "1.54t",
  "mode": "MACRO",
  "macro": "job.ijm",
  "inputs": {"channels.tif": "prepared-channels.tif"},
  "plugins_directory": null,
  "required_commands": ["Remove Outliers...", "Median...", "Gaussian Blur...", "Z Project..."],
  "expected_outputs": {
    "filtered.tif": {"kind": "TIFF", "minimum_bytes": 1},
    "maximum.tif": {"kind": "TIFF", "minimum_bytes": 1}
  },
  "input_provenance": "Replace with the actual preparation and intensity units; no preprocessing is inferred",
  "analysis_population": "Replace with the actual included images, channels, Z planes and time points",
  "execution_policy": "ALLOW",
  "display_policy": "ALLOW",
  "expected_return": "complete",
  "threads": 1,
  "timeout_seconds": 900,
  "heap_megabytes": 2048,
  "out": "new-imagej-result"
}
```

```powershell
& "$javaBin/java.exe" -jar build/imagej-workflow/imagej154t-workflow-development.jar --request job.json
```

Logical input/output names are portable relative paths, such as `channels.tif` or `tables/measurements.csv`. Their original source files are byte-captured under `inputs/`; `getArgument()` is the owned work directory, using forward slashes. The original macro bytes are unchanged. Declare every output under `results/`: missing files, extra files, incomplete files or a wrong completion return fail admission. `expected_return` is optional. Use `FILE` for CSV, ROI ZIPs and other byte-preserved outputs; use `TIFF` for complete original TIFF decoding, pixel hashing and image-state inspection. Other formats are not silently converted or treated as TIFF.

The original files are retained in full. Every TIFF plane's complete raw pixel array, dimensions, bit depth, slice labels, display ranges, LUTs, explicit calibration fields, serialized ROIs/overlays, image info and properties are recorded. Nonfinite floating-point pixels are retained through the original APIs and hashed from their decoded raw bits. This is not a claim to enumerate every private ImagePlus field or every TIFF tag. It does not infer biological validity, normalize intensities, repair identifiers, or correct original algorithm behavior.

## Display and plugin prerequisites

Original core command initialization constructs AWT menus even in batch mode. `-Djava.awt.headless=true` cannot run the complete command registry. On Linux use a **full**, AWT-capable JDK/JRE and a working permitted display, including an existing WSLg display or a separately permitted Xvfb installation. A headless-only Java installation can compile the workflow yet lack `libawt_xawt.so` at execution. That prerequisite failure returns SKIP3 before macro execution. The qualification used a private matching-version Ubuntu AWT supplement and the existing WSLg display, leaving the installed system JDK unchanged. No automatic system installation is attempted by the workflow.

For separately licensed plugins, set `plugins_directory` to a complete regular-file provider directory and supply a nonempty `plugins_provenance`. The entire tree is copied, hashed and checked before/after execution. Original ImageJ plugin discovery semantics apply; required menu commands are checked before the macro. A missing required command or missing provider skips dependent analysis. Providing a plugin directory does not qualify that plugin or recover a Fiji distribution. A plugin may require further native libraries or non-ImageJ programs; satisfy its original requirements explicitly. Core-overriding provider resources are rejected, preserving the selected original edition.

Batch mode is not a security sandbox. Macros and plugins can execute code with the user's OS permissions. Use trusted code. The wrapper initializes the original command menus without starting an ImageJ main window or a startup-macro runner. The caller's macro/plugin can still explicitly open windows, read/write outside the work directory or start other programs. Wall-time/worker-heap bounds and observed descendant cleanup are not OS CPU, RSS, filesystem or network containment guarantees.

## Output and admission

`analysis-status.json` is the terminal admission decision. Success additionally requires the original worker's terminal `worker-status.json`, an error-free original interpreter, no recorded caught command exceptions, the exact declared output population and complete matching file hashes. A process exit of zero alone is insufficient. Error diagnostics include original interpreter error state/text; a project subclass replaces its top-level modal error display with retained text after the original interpreter has set its error state. A stream observer recognizes caught original command exceptions by the actual `IJ.log`/`Executer.run` call site, not by matching arbitrary user log text. It leaves original processing code unchanged. It also records exceptions reported to ImageJ's public exception handler and uncaught worker-thread exceptions. It cannot establish success for arbitrary external code that silently fails or suppresses its own diagnostics.

Other retained files include `source-request.json`, `macro.ijm`, `inputs/`, `results/`, `runtime-provenance.json`, `original-command-registry.json`, `complete-results.json` on success, `process-live.json`, `process-exit.json`, `process.stdout` and `process.stderr`. Timeout/failure outputs are retained and unadmitted. Resolve causes using `WARNINGS.md` and the worker/process records; do not consume partial files as successful results.

Capacities are explicit: `maximum_input_bytes` defaults to 1 GiB; `maximum_output_bytes` defaults to 2 GiB and bounds the **whole owned workspace**, including captured inputs/plugins/logs as well as results; `maximum_decoded_pixels` defaults to 268435456 over declared TIFF outputs; `maximum_files` defaults to 20000 and includes directories. No image planes or files are silently truncated to fit. Worker heap is separately bounded by `heap_megabytes`; the original decoder can require more memory than the raw pixels alone.

## Native Java APIs and validation

Compile Java image-processing callers with `imagej154t-workflow-development.jar` on the classpath. Its original `ij.ImagePlus`, `ij.ImageStack`, `ij.plugin.filter.GaussianBlur`, `ij.plugin.filter.RankFilters`, `ij.plugin.ZProjector`, ROI and measurement APIs are present in full. For example, `new GaussianBlur().blurGaussian(image.getStack().getProcessor(slice), 0.5, 0.5, 0.002)` uses the original implementation; iterate over every intended slice explicitly and use the original `FileSaver.saveAsTiffStack` for stacks. Direct API callers retain ordinary original mutable-image/dialog semantics; the workflow's admission/capture rules apply to the workflow interface.

The retained qualification compares five complete original macros, independently run with the unmodified original interpreter, with the packaged interface on Windows Java 21, Windows Java 25 and Linux Java 21. All 14 output files per platform are byte-identical, including all TIFF bytes and complete independently decoded pixels/axes/calibration. Controls cover 8-bit stack outlier/median/Gaussian operations, maximum projection and ROI measurement; 16-bit C/Z/T composite stacks with calibration, ROIs and overlays; floating-point NaN/infinity/signed-zero cases; RGB stacks; and threshold/particle measurements. These are explicit synthetic workflows, not an exhaustive qualification of every ImageJ command.

Each platform also checks 21 refusals/error paths and one successful synthetic plugin-provider case. The controls retain caught original command exceptions, reject a plugin's zero exit without terminal status, enforce complete output populations, reject edition/core overrides, and verify timeout termination. Seven setup controls cover build/download policies, absent prerequisites, incomplete source and protected paths. Separate Linux evidence proves missing native AWT skips macro execution. Historical failed intake/runtime checks remain retained with their scope and recovery explanation.

See `imagej154t-workflow-development/VALIDATION.json`, `SHA256SUMS` and the complete `controls.zip` archive for exact populations and file identities. `tools/literature/imagej154t/verify.py` restores the fixed original source and can rebuild and rerun the actual complete controls offline when both build and analysis are explicitly allowed. Required Java/display prerequisites remain external.
