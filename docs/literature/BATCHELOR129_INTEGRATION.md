# Current Batchelor and Rphenograph integration status

**Current checkpoint: 103 new scoped integrations: 19 native Java subsets and 84 tested upstream adapters; 106 including three existing adapters.** There are 407 named tools, 2,482 paper-tool observations and 1,055 reviewed uses across the fixed 250 papers. **245 tools remain missing.** Batchelor gains one scoped upstream admission; the newly recognized Rphenograph tool adds one missing entry. The full toolbox and paper-wide semantic audit remain active. Cell Ranger remains excluded at the user's request.

| Source role | Available selected workflow | Important boundary |
| --- | --- | --- |
| P040: method version 1.29 on transformed imaging-protein measurements | Original Batchelor 1.29.0 `fastMNN`, complete prepared single-matrix input, explicit batches/parameters and native Java checks | Selected source candidate; the exact paper patch, call, Ki-67 handling and settings are not recovered |
| P051: Batchelor 1.20.0 integration before Monocle3 1.3.7 trajectories | Original Monocle 1.3.7 `align_cds` calling Batchelor 1.20 `reducedMNN`, followed by explicit graph/root ordering | Explicit interpretation; the actual paper integration function and roots are unreported |
| P040: Rphenograph 0.1.1 with k = 40 after correction | Missing; source use and version discrepancy recorded | All four inspected official commits report 0.99.1; the reported 0.1.1 remains unrecovered |

Read [the FastMNN workflow guide](FASTMNN129_WORKFLOW_DEVELOPMENT.md) and [the separate Monocle 1.3.7/reducedMNN guide](MONOCLE137_WORKFLOW_DEVELOPMENT.md) to select the operation and runtime. Each standalone JAR bundles its own source profile; neither changes the released executable or root dispatcher. Do not mix their original R libraries: the tested FastMNN provider uses R4.5.3/Batchelor 1.29.0, while the selected Monocle provider uses R4.3.3/Batchelor 1.20. The FastMNN profile is a custom tested source build, distinct from Bioconductor's recommended R4.6 development environment.

**Rphenograph's k = 40 is a clustering setting, not a recovered FastMNN neighbor setting.** The source paper gives those operations separate roles. Correction does not implement graph clustering, marker-based lineage annotation, subclustering or visual label refinement. The latter steps include human decisions. Preserve the literal reported Rphenograph version 0.1.1; do not relabel the inspected 0.99.1 source as the exact paper version. The [earlier source intake](PHENOGRAPH_SOURCE_REVIEW.md) is retained as a historical checkpoint; this amendment adds its observation/review to the current catalog.

The two selected Batchelor operations cover the two named main-article roles found by the saved full 250-source Batchelor/function census. This grants one scoped upstream badge. It does not qualify every Batchelor function, every upstream input constructor, hidden settings, all supplementary/author-code uses, or either biological study. The FastMNN API covers prepared single-matrix input with explicit batch labels; other upstream constructors/backends remain outside this typed interface.

## Qualification and complete evidence

FastMNN qualification used 19 actual original controls: 17 successful cases and two preserved original unnamed-weight failures. For every success, complete cells/features, corrected coordinates, rotations and reconstructed values matched the independent representation-matched original control. All17 full SingleCellExperiment and metadata RDS files were byte-for-byte identical to independent originals. The exact canonical source comprises 90 raw Git blobs; readiness checks 105 installed named functions and 51 namespace versions. These identity checks do not mean every function was behaviorally qualified.

The controls include prepared signed protein and log-expression data, dense/sparse representations, IRLBA/exact PCA, KMKNN/exhaustive neighbors, null/false deferred modes, no-PCA/tree merges, automatic merges, selected features/correct-all, restrictions, all exposed weight representations and parallel workers. They are complete synthetic 72-feature/360-cell populations and explicitly declared subsets, not paper data or recovered paper settings.

Portable qualification passed 133 checks across Windows JDK21/25 and Linux JDK21, plus seven actual setup refusals. The [full FastMNN bundle](fastmnn129-workflow-development/README.md) preserves 6,949 complete logical files with content-addressed deduplication. Public restoration/rebuild/native replay passed 128 checks across the three platforms. Saved replay compares supplied data and bytes; it does not repeat original R fitting, reload RDS or authenticate a producer.

The separate selected reducedMNN workflow retains complete 64-gene/480-cell controls, 108 packaged comparisons and 240 explicitly retained infinite pseudotimes in the disconnected control. The earlier [Batchelor role review](BATCHELOR_INTEGRATION.md) and primary review notes are retained unchanged as historical evidence. The report displays current selected coverage above each historical implementation note.

## Warnings and required actions

**SKIP3: skip dependent original analysis if the exact runtime, download, installation or execution cannot be supplied where permitted.** Run the operation's setup helper only on an allowed host. Supply the returned exact Rscript/library/pristine-source paths. Analysis installs nothing. Windows users may use permitted WSL with Linux Java and Linux paths; saved-result audits also run directly on Windows Java.

**FAIL2: no result is admitted on source, input, numeric, timeout, capacity or process failures.** Keep complete inputs, WARNINGS.md, status, original logs and any unadmitted RDS. Correct the named cause and retry a NEW output directory. Never trim the population, rename identifiers or replace nonfinite values to obtain a passing result.

**Unnamed-weight failures are preserved.** In the original single-matrix path, unnamed numeric batch weights can produce NA centers. Both IRLBA and exact-SVD controls failed. Changing only the PCA backend did not fix them. An explicit `weights.batch_weights` dictionary keyed by every declared batch is a different request; the controller never supplies names automatically.

**FACTORS and ALL_VALUES make different claims.** FACTORS retains the full lazy original assay and checks every factor, while evaluating zero reconstructed entries. ALL_VALUES additionally streams and checks every reconstructed value. Provide resources for the entire declared population. A full saved-archive restore on a WSL-mounted Windows filesystem can take several minutes; the evidence retains actual elapsed times.

## Verify the catalog amendment

Run from the project root with Python3 and a NEW output:

```bash
python3 tools/literature/batchelor129_catalog/verify.py \
  --out build/literature-review/monocle-runtime/NEW-catalog-check

# Also read every complete exact cached primary source for both name censuses.
python3 tools/literature/batchelor129_catalog/verify.py \
  --rescan-sources \
  --out build/literature-review/monocle-runtime/NEW-full-source-check
```

This gate checks exact current metadata, preserves every earlier observation/review, follows the earlier admission through an explicit eight-path historical metadata view, and verifies current scientific source hashes and complete saved-model integrity. It cannot overlay scientific code or alter original controls. All2,481 previous observations and1,054 reviews remain unchanged; only one Rphenograph observation/review is appended. Earlier scientific gates, workflow evidence and original errors remain intact.
