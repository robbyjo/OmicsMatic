# Original QuPath 0.5.1 script workflow

**Development workflow; no whole-tool, whole-paper or biological qualification.** This uses the complete unchanged original QuPath 0.5.1 release and embedded Java 21.0.2 through its original headless Groovy script engine. It preserves full source/runtime inventories, caller scripts, declared inputs, native outputs, original conditions and admission status. It does not replace the original segmentation/classification engine with a reduced Java method.

**Version and model warning:** P009 and P040 report 0.5.1. Other reviewed papers report 0.6.0, 0.4.1, 0.4.3, a literal 0.2.3-to-0.4.3 range, or no edition. The selected edition does not silently substitute for those reports. Source roles involving manual ROIs, unknown stain vectors/thresholds, custom StarDist models/classifiers, image registration or other tools remain separate. See [all 12 individually reviewed uses and 16 primary anchors](../../tools/literature/qupath051/paper-use-intake.json). Catalog admission is a separate step; this component by itself adds no badge.

## Install and build

Use native Python for the selected Windows/Linux x86_64 platform, and NEW paths:

```powershell
python tools/literature/qupath051/provider.py --allow-install --allow-download --out build/my-new-qupath051-provider
python tools/literature/qupath051/build.py --java-bin "C:/path/to/jdk-21/bin" --out build/my-new-qupath051-build
java -jar build/my-new-qupath051-build/qupath051-workflow-development.jar --help
```

On Linux, replace the compiler path with the actual permitted JDK 21/25 `bin` directory. The controller targets Java 17 bytecode, but qualified controller runtimes are Java 21/25; original QuPath runs on its separately bundled Java 21.0.2. The native binary, source archive and every installed regular file are pinned. Installation retains every bundled original license and all 1,097 source files. Windows has 536 original runtime files; Linux has 481.

For offline private setup, use `--source-cache path/to/exact-archives --allow-install` without `--allow-download`. The cache must contain the platform binary archive and commit-pinned source archive named in `source-profile.json`. The upstream binary release and source are not rebuilt or altered. The fetched release metadata has null asset digests; recorded SHA-256 values pin complete official downloads and do not authenticate the producer or prove binary/source reproducibility.

The complete original runtime/JRE and extracted native-library caches are not redistributed in the small project adapter. Supply them through permitted original installation. A source-only checkout or development JAR is not an offline original runtime.

## Run a synthetic example

```powershell
python tools/literature/qupath051/make_example.py --provider build/my-new-qupath051-provider --out build/my-new-qupath051-example
java -jar build/my-new-qupath051-build/qupath051-workflow-development.jar --request build/my-new-qupath051-example/request.json
```

These are explicit synthetic controls, not recovered author images, stain vectors, annotations or settings. The six jobs cover a full image, a blank image with genuinely zero detections, a cropped ROI, nucleus scoring, unsmoothed boundaries and binary intensity thresholding. Their direct original reference contains 72 detections. All four H-DAB intensity classes are exercised in the full-image control.

Read `analysis/analysis-status.json`, `analysis/WARNINGS.md`, `analysis/ORIGINAL_CONDITIONS.tsv` and the full original process logs. `analysis/results/complete-summary.json` reports all six jobs. Each job retains measurement TSV, complete object GeoJSON, a raw original QPDATA hierarchy and a separate QPDATA hierarchy with explicit deterministic synthetic fixture identifiers. Those fixture identifiers support comparison; they are not applied by the production adapter to user data.

Every original image, parameter, ROI and fixture UUID is explicit. Blank-image outputs remain present; no failed or empty case is dropped. Complete portable exports can be compared with independent direct original runs. Binary QPDATA files are retained intact; full opaque QPDATA byte parity is not claimed because they also encode execution-specific context. No raw object is rewritten to force a comparison to pass.

The image-control parameters use the original application's units. P040's printed area-unit wording remains explicit in the source review; the control does not silently repair that wording or establish the author's exact invocation.

## Use your own original Groovy script

The request schema is demonstrated by the generated `request.json`. Paths in JSON resolve against that JSON file's directory. Use absolute paths when storing a request elsewhere. Specify the exact provider and `version: "0.5.1"`; other versions are separate identities.

The exact trusted script is captured as `script.groovy` and executed by the original command. It receives the owned work directory in `args[0]`. Files explicitly mapped in `inputs` are copied byte for byte under that directory's `inputs/`; logical filenames are caller choices and identifiers within files remain literal. Write every result under `results/` and declare every expected relative result filename. Complete files, rather than sampled rows or output prefixes, are retained and checked.

Use an explicit string completion marker as the script's final return, matching `expected_return`, after the entire declared population has completed. Do not rely on an incidental numeric or boolean return from a scientific function. Check original plugin return values and propagate original failures. The supplied control script demonstrates these checks. `input_provenance` and `analysis_population` must describe the actual inputs and complete population; the controller cannot infer scientific validity from arbitrary caller code.

`required_classes` checks the selected distribution for needed original classes before analysis. Stock QuPath does not supply every third-party extension or author model. A missing declared class skips the dependent analysis. Do not add files to a pinned provider in place or imply a stock core supplies a custom StarDist classifier. Acquisition, stitching and upstream image preparation remain separately declared operations.

Both `execution_policy` and `trusted_code_policy` must explicitly allow execution. Groovy runs with the user's account permissions; the owned directory is not an OS security sandbox. The worker heap, wall timeout and workspace file/byte limits are explicit resource controls, not CPU affinity or total OS RSS limits. Increase documented capacities deliberately for larger complete inputs; exceeding a capacity admits no prefix.

Each run uses a fresh QuPath preference node and owned home/cache. Existing QuPath preferences are not selected. Windows Java Preferences may store that fresh node in the current-user registry. The original JavaCPP native loader creates relative and absolute aliases within its owned cache; those links are retained only when their normalized and real targets remain inside that cache. Inputs and result files must remain regular files without symbolic links. Links outside the cache boundary refuse admission.

## Warnings, errors and recovery

**SKIP3 skips dependent analysis.** Supply missing permitted archives, the complete native-platform provider, required original classes, runtime prerequisites and explicit execution policies, then retry a NEW output. Download and install only where permitted. If an extension/model cannot be supplied under the applicable environment/license, retain the warning and skip its dependent analysis. A successful setup `READY.json` is not an image-analysis result.

**FAIL2 admits no complete result.** Preserve all inputs, returned images/hierarchies/tables, errors, original process logs, conditions, indexes and status. Correct the named cause and retry a NEW path. Do not reuse an output directory, remove failed images, accept an output prefix or relabel a partial run complete.

Original QuPath can log command-line parse failures and return zero, and project batches can log an image exception and continue. This adapter requires successful exit, no selected-format original ERROR headers, exactly one complete-population return marker, every declared result and no extra result files. Original WARN headers are counted prominently, with their log locations in `ORIGINAL_CONDITIONS.tsv`; complete multiline diagnostics remain byte-preserved in `process.stdout` and `process.stderr`. Review every warning and justify or correct its source cause. Read the full logs even when a message does not use the selected structured log format.

`MANIFEST.json` covers the complete data, process and warnings population; it explicitly excludes itself and the final admission record. `analysis-status.json` is written after the data manifest. Hashes establish integrity, not authenticity, independent statistical refitting, biological correctness or complete paper reproduction.

The earlier failed fixture used optional Groovy JSON classes not included in this distribution; the corrected fixture uses QuPath's bundled Gson. Earlier path-handling and Linux cache-boundary attempts remain failures and are retained. Original scientific code and complete fixture inputs were not changed to conceal differences. The canonical executable and all unrelated unfinished work remain outside this development component.

Use the [official command-line reference](https://qupath.readthedocs.io/en/0.5/docs/advanced/command_line.html) for original script semantics. The full semantic review and 250-paper implementation objective remain active.

## Saved qualification

Windows controller Java 21/25 and Linux controller Java 21 each matched all 13 complete portable original export files across the same six jobs and 72 detections. Each run also retains all 12 raw/fixture-labelled QPDATA files. The Windows Java 21 suite passed 15 protocol controls; Linux passed 19, including both allowed cache-alias forms and rejected links outside the cache boundary. Six actual setup-policy/cache controls and five archive-boundary controls passed on each operating system. Three private original installations are recorded, including an actual public-installer network setup. The public example was executed successfully.

The records retain the failed optional-module fixture, incorrect initial test path, unsupported Windows launcher-name guard and two overly strict Linux cache attempts. Their complete scientific inputs, outputs, source snapshots and diagnostics remain available. Installed vendor binaries/JREs and extracted native dependency caches remain local dependencies; their profiles/metadata are retained, and they are not redistributed as scientific control data. No failed attempt is relabelled successful.

[Validation](qupath051-workflow-development/VALIDATION.json), [complete retained member inventory](qupath051-workflow-development/CONTROLS_LAYOUT.json), [evidence archive](qupath051-workflow-development/controls.zip) and [controller artifacts](qupath051-workflow-development/ARTIFACTS.json) describe this pre-admission development checkpoint.

```powershell
python tools/literature/qupath051/verify.py --out build/literature-review/monocle-runtime/my-new-qupath051-evidence-check
```

This verifies every retained member, current source/artifact pins, full portable export comparisons, preserved raw hierarchies, protocol/setup populations and all 16 reviewed primary anchors. It does not execute a new original analysis or independently validate biology. Source-only offline controller rebuilding and a new original refit require their separately documented prerequisites; the evidence archive is not a redistributed native runtime. The main catalog, canonical executable and unrelated unfinished files are unchanged by this component checkpoint.
