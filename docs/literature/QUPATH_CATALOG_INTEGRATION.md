# QuPath catalog integration

**QuPath 0.5.1 is available through an explicit standalone Java controller and the complete original headless Groovy engine.** The controller requires a permitted private Windows/Linux x86_64 original provider and a trusted caller script. It adds one scoped upstream integration. It does not implement native Java segmentation, reproduce any paper or qualify every original function. The canonical executable and root dispatch remain unchanged.

The catalog contains 250 papers, 432 named families and 2,671 paper-family observations. There are now 1,143 individually reviewed uses and 1,528 awaiting individual review. Available new scoped integrations total 107: 20 native Java subsets and 87 upstream workflows; 110 including three existing adapters. There are 266 missing families. The full semantic review and implementation objective remain open.

## Start with a synthetic example

Use permitted native-platform Python, Java and NEW output paths. These commands download/install the original runtime and build the separate controller:

```powershell
python tools/literature/qupath051/provider.py --allow-install --allow-download --out build/my-new-qupath051-provider
python tools/literature/qupath051/build.py --java-bin "C:/path/to/jdk-21/bin" --out build/my-new-qupath051-build
python tools/literature/qupath051/make_example.py --provider build/my-new-qupath051-provider --out build/my-new-qupath051-example
java -jar build/my-new-qupath051-build/qupath051-workflow-development.jar --request build/my-new-qupath051-example/request.json
```

Replace the compiler path with a permitted JDK 21/25 `bin` directory. On Linux use native Linux Python and the Linux JDK path. The controller targets Java 17 bytecode and was qualified on Windows Java 21/25 and Linux Java 21. Original QuPath always uses its bundled Java 21.0.2. For an offline installation, supply the exact platform/source archives with `--source-cache path/to/exact-archives --allow-install`, omitting `--allow-download`. The adapter JAR and source checkout do not include the original native runtime/JRE.

The [complete workflow guide](QUPATH051_WORKFLOW_DEVELOPMENT.md) explains request paths, trusted-code and execution policies, required original classes, resource limits, completion markers and every declared output. Request paths resolve relative to their JSON file; use absolute paths when moving requests. Groovy runs with account permissions, so supply only a trusted script. Fresh provider installation records are not analysis results.

Read `analysis/analysis-status.json`, `analysis/WARNINGS.md`, `analysis/ORIGINAL_CONDITIONS.tsv`, and the complete `process.stdout`/`process.stderr`. Preserve every result, including blank-image outputs and original QPDATA hierarchies. Synthetic fixture identifiers are explicit control choices and are not production identifier normalization.

## Source roles and unresolved requirements

All 250 exact cached primary XMLs were searched and their DOI/hash identities checked. The 12 QuPath paper-family uses cover all 16 named paragraphs. The [individual readings](../../tools/literature/qupath-paper-use-reviews.json) retain complete paragraph indexes, hashes, sections, literal reported editions and role classification. Each enriched observation retains its exact previous extracted record. P040's already-reviewed observation and global review remain unchanged; its current selected-workflow scope is displayed separately.

| Paper | Reported edition | Source role and requirements |
| --- | --- | --- |
| [P009](https://doi.org/10.1038/s41586-024-08334-8) | 0.5.1 | DAPI nuclei/cell segmentation, 2-micrometer expansion, intensities and centroid proximity. Both analysis paragraphs state 0.5.1. Manual marker thresholds, channels, ROIs and section/mouse aggregation remain caller choices; H-DAB controls do not validate the fluorescence/proximity pipeline. |
| [P020](https://doi.org/10.1038/s41586-025-09869-0) | 0.6.0 | Foreground pixel classifier, manual stroma exclusion, optical-density cell detection and Ki-67 fractions. The reported edition and author classifiers/ROIs are separate from selected 0.5.1. |
| [P021](https://doi.org/10.1038/s41586-026-10271-7) | Unreported | Cell and RNAscope puncta detection with measurement exports. Paragraph 78 analyzes output tables downstream in R; it does not establish another QuPath execution. Fiji preparation and author puncta settings remain separate. |
| [P024](https://doi.org/10.1038/s41586-025-09573-z) | Unreported | Tissue ROIs, DAPI cell detection, subcellular RNA puncta and per-cell exports. Manual settings, Fiji stitching, custom Python classification, CLR and group tests are separate. |
| [P040](https://doi.org/10.1038/s41586-026-10954-1) | 0.5.1 | H-DAB positive-cell detection and cytoplasmic intensity classes. Numeric stain vectors and author ROIs remain unknown. Thresholds 0.08/0.16/0.24 remain literal. The paper prints an area-unit wording that is not silently repaired to application units. The synthetic controls are not the author invocation. |
| [P049](https://doi.org/10.1038/s41586-026-10981-y) | 0.4.3 | Multiplex RNA-FISH, DAPI cells/spots and single/composite classifiers. Reported edition, LIF series, RGC ROIs, thresholds and trained resources are separate. |
| [P056](https://doi.org/10.1038/s41586-025-09762-w) | Unreported | Generic IHC/RNAscope image quantification. The source does not identify a segmentation algorithm, thresholds or export structure; none is inferred. |
| [P058](https://doi.org/10.1038/s41586-023-06817-8) | 0.4.1 | Polygon ROIs, positive/composite classification, counts and nuclear fluorescence. Advice acknowledged in paragraph 73 is not another execution. Manual choices, coronal-series scaling and downstream ggpubr/Wilcoxon are separate. No second edition is inferred. |
| [P098](https://doi.org/10.1038/s41588-026-02745-1) | Unreported | Blinded tumor IHC quantification; the algorithm and thresholds remain unidentified. Blinding is not an algorithm setting. |
| [P158](https://doi.org/10.1038/s41588-025-02368-y) | Literal `v.0.2.3 to v.0.4.3` | Custom StarDist nuclei classifier, manual channel thresholds and exports. The range excludes selected 0.5.1; stock QuPath does not supply the custom extension/model. Downstream Python remains separate. |
| [P161](https://doi.org/10.1038/s41588-026-02665-0) | Unreported | H&E alignment and pathologist neighborhood annotation. Registration transforms and annotation decisions are unknown; GeoPandas, SCANPY, CellPhoneDB and CytoSignal are separate. |
| [P226](https://doi.org/10.1016/j.ajhg.2025.08.004) | Unreported | H2B-mRuby nuclei, 2-micrometer perinuclear expansion and morphology/fluorescence filtering. Thresholds are unknown; Nikon NIS Elements rolling-ball preprocessing is separate. |

The complete original script engine accepts caller code; that availability does not independently validate every caller algorithm. Scientific controls here cover six synthetic H-DAB jobs and 72 detections per controller, with all 13 portable exports compared to independent direct original runs. The full 12 raw/fixture QPDATA files per controller are preserved without an opaque byte-parity claim. Fluorescence/proximity, RNAscope puncta, registration, custom StarDist models and author pipelines remain outside that scientific control population. Other editions are not silently substituted.

## Warnings, errors and recovery

**SKIP3 skips dependent analysis.** If required downloads, installation, source, runtime, provider, extension/model or execution are absent or forbidden, preserve the warning and skip the affected analysis. Supply the named prerequisite only where permitted and retry NEW paths. Do not mutate a pinned stock provider to imply it contains a custom model. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Keep all inputs, full images/hierarchies/tables, conditions, status and raw logs; correct the named cause and retry a NEW output. Do not drop failed/empty images, admit an output prefix or call a zero-exit logged error successful. Original QuPath can return zero after logging an error. Admission requires exit zero, no selected-format ERROR headers, exactly one completion marker, every declared result and no extras. Review every original warning and the full logs, including messages outside the selected structured log format.

Hashes prove byte integrity, not producer authentication, independent statistical validity or biological correctness. Binary/source reproducible builds and GUI behavior are not qualified. Complete original binaries, JRE and extracted native caches remain private installed dependencies. Public evidence retains the complete source archive, scientific outputs, diagnostics and explicit cache/link metadata.

## Validate the catalog

```powershell
python tools/literature/qupath_catalog/verify.py --out build/literature-review/monocle-runtime/my-new-qupath-catalog-check --rescan-sources
python tools/literature/qupath_catalog/test_catalog.py
```

The current gate checks the exact eight-path predecessor archive, every current admission source/evidence pin, the complete unchanged predecessor gate, the unchanged original QuPath verifier, all 1,381 scientific evidence members and the four retained refusal cases. Optional rescanning checks all 250 exact primary XMLs and complete support identities. It runs no new original analysis or installation. Only the eight catalog metadata paths may be overlaid for historical validation; scientific sources remain live. The existing cumulative registry stays at 470 definitions with only QuPath coverage fields updated.

The original [component validation](qupath051-workflow-development/VALIDATION.json) and [qualified intake](../../tools/literature/qupath051/paper-use-intake.json) retain their pre-admission `new_tool_credit: 0` and unchanged-catalog statements. Those describe the earlier checkpoint. This later [catalog validation](omics-2023-2026/qupath-catalog-validation.json) records one admitted upstream workflow and eleven additional reviews. No scientific workflow or frozen verifier is changed to achieve catalog admission. Earlier failed attempts and limitations remain retained. New warnings appear prominently in both catalog pages, with previous checkpoints preserved as history.
