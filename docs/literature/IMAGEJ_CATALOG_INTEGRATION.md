# Current ImageJ and Bleach Corrector catalog admission

**The catalog now admits two selected upstream workflows:** complete original ImageJ 1.54t core and original CorrectBleach 2.1.0 with ImageJ 1.53t. CorrectBleach's optional native Java histogram engine belongs to the same tool admission. It does not earn another family badge. Both workflows use separate development executables; the canonical executable is unchanged.

**Current checkpoint:** 250 papers, 430 named software families, 2,669 paper-tool observations and 1,130 individually reviewed uses. There are 105 new scoped integrations: 19 native Java subsets and 86 upstream workflows, or 108 including three existing adapters. Another 266 cataloged families remain missing. The full semantic review and implementation goal remain open. A broader plugin/software census is still under individual review; detector hits have not been silently counted as software use or implementation.

The frozen cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers from 2023-10-23 through 2026-10-23. No time extension was needed. The amendment reads all 250 exact primary XML sources, checks their hashes and DOIs, and verifies all 84 named paragraphs supporting the 51 ImageJ reviews. Bleach Corrector appears in one additional named paragraph in P026. Bibliographic labels are removed only for name matching; raw paragraph hashes and reported version strings are preserved.

## Use the workflows

- [ImageJ 1.54t build, macro/API use, display requirements and recovery](IMAGEJ154T_WORKFLOW_DEVELOPMENT.md).
- [CorrectBleach 2.1.0 build, explicit methods, native histogram API and recovery](CORRECTBLEACH210_WORKFLOW_DEVELOPMENT.md).

Those component guides and validation records are frozen qualification snapshots from before catalog admission. Their original statements about pending catalog credit remain retained as history. This document and the current inventory state the later catalog admission. They do not alter component source or qualification evidence.

**SKIP3 means dependent analysis was skipped.** Supply missing prerequisites only where downloading, building, installation and execution are permitted. Use the documented exact provider, runtime and new output paths. ImageJ's complete macro command registry needs full AWT and a working display; the selected CorrectBleach public APIs were tested with a headless Linux JDK. ImageJ2, Fiji plugins, Jython scripts and other providers have additional requirements. If a prerequisite cannot be installed, keep the skip and report the missing prerequisite and the dependent analysis that did not run.

**FAIL2 admits no result.** Retain complete inputs, original output images/models, warnings, terminal status and process logs. An output TIFF or process exit zero alone does not establish admission. Correct the named cause and retry in a new directory. Historical original failures, Linux export timeouts and incorrect test-expectation attempts remain retained with their meanings; they are not promoted into successful results.

**Do not use histogram matching for intensity quantification.** The request must declare its actual purpose and explicit correction method. Original simple-ratio baseline subtraction, exponential-with-offset initialization and fitting, unsigned pixel arithmetic, ROI effects and C/Z/T handling are preserved. Inspect actual fit diagnostics; original optimizer success on two points does not establish an adequate scientific model.

## What the source reviews establish

P040 reports ImageJ 1.54t for composite-channel processing, outlier removal, median or Gaussian filtering and display contrast. The complete original selected core and macro engine are available, with 14 complete output files compared independently per platform on Windows Java 21/25 and Linux Java 21. This does not recover original author inputs or custom settings. Median-based ImageJ Remove Outliers is distinct from an IMC preprocessing hot-pixel procedure.

The other ImageJ reviews distinguish image display, western/gel densitometry, projections, manually selected ROIs and counts, spatial-section registration, morphology, segmentation, colocalization and reused measurements. They preserve reported editions, including ambiguous literals such as `1.8.0`, `2.14.0/1.54 f` and `1.53t 24`. No Java-version reinterpretation or automatic edition substitution is made. Reused adipocyte measurements in P192 are not described as a new ImageJ execution. Unnamed macros, Jython scripts and manual annotation decisions remain unrecovered.

Named components such as Cell Counter, TrakEM2, Crosshair, BigWarp, Grid/Collection Stitching, MicrobeJ, Fast4DReg, StarDist, DiAna, JACoP and ICTN have distinct source roles. The core badge does not qualify them. The reported name `TrackEM` remains unresolved rather than silently normalized to TrakEM2. Acquisition software, registration, deconvolution and downstream statistical programs remain separate. The broader name audit is still open.

P026 reports Fast4DReg XY drift correction followed by Bleach Corrector on SYTOX Orange time-lapse data. It reports no bleach plugin version, correction method, baseline or fit settings. The selected original 2.1.0 workflow offers all three methods and the native histogram alternative explicitly. It does not perform registration or infer an unreported paper method. Its 21 original and six native complete-image comparisons, 24 request/refusal checks and 1,048 complete histogram cases passed on three runtimes; twelve setup refusals, actual fixed downloads/builds and Windows/Linux offline rebuilds also passed.

## Preservation and verification

Fifty unreviewed ImageJ extraction records are enriched with individual summaries and complete support anchors. Each current record retains its entire earlier extraction under `source_extraction_before_review`. Their exact earlier bytes also remain in the eight-file predecessor archive. The other 2,618 earlier observations, all 1,079 earlier reviews and the already reviewed P040 ImageJ use remain unchanged. Fifty ImageJ reviews and one Bleach Corrector review are appended; no duplicate P040 review is added.

All original scientific code, verifiers, component guides and controls remain live and unchanged. The predecessor admission is checked with exactly eight archived catalog metadata inputs. Scientific paths and additional metadata cannot be overlaid. Current component verification reads every retained complete evidence object and checks all published development JAR hashes. This catalog gate does not repeat original image analysis or install software.

```powershell
python tools/literature/imagej_catalog/verify.py `
  --rescan-sources `
  --out build/literature-review/monocle-runtime/new-imagej-catalog-check
python tools/literature/imagej_catalog/test_catalog.py
```

The cached primary XML files are required only for `--rescan-sources`; unavailable or forbidden source access skips that dependent rescan. The catalog, source census, role-coverage records and predecessor archive remain auditable without publishing research full texts. Historical generators must not overwrite this cumulative catalog. Cell Ranger remains excluded at the user's request.
