# Rsubread counting: warnings and required actions

**Require exit 0 and `COMPLETED.json` with `analysis_admitted=true` for the entire requested batch.** Individual `PROVISIONAL.json` files, pending receipts, upstream exit 0, count tables and complete RDS files alone do not admit a result. A later failure or cancellation admits none of the earlier cases. Read `WARNINGS.md`, `FINAL-ERROR.txt` when present, original `stdout.log`/`stderr.log`, and `original-error.txt` in the failed worker directory.

**SKIP3: dependent counting is skipped.** If private software export, R execution or installation is forbidden/unavailable, retain the skip. When permitted, export the exact original package into NEW private paths, provide the matching clean original R runtime, and bind NEW paths. The binder names missing, extra or changed files. Never refresh known hashes or automatically substitute an edition.

**FAIL2: no batch result is admitted.** Preserve all inputs, original objects, errors and provisional outputs. Correct the named input, source, runtime, capacity or filesystem cause and retry NEW output paths. Do not round counts, replace missing identifiers, relabel samples, silently change counting units, or truncate a result to make a check pass. An unsupported double-storage GeneID fails dependent interpretation while retaining the original R object.

This development workflow executes **original Rsubread bulk short-read featureCounts**. It supports the two explicitly selected Windows x86_64 runtime families below. Java 17+ can read and structurally verify frozen outputs without R, including on Linux. **Original counting and runtime binding on Linux return SKIP3; no Linux R analysis is claimed.** No compiler, installer, package manager or download runs during analysis. The canonical executable is unchanged; use the separate development JAR.

This is a scoped integration, not the complete Rsubread package or a reproduced paper. Alignment, single-cell/UMI processing, long-read counting, read-group expansion, per-read reports and arbitrary R code are not exposed. GeneID storage supports character, integer and logical values, including NA; generic value tables can retain doubles, but the complete count-list interpretation rejects double GeneIDs because R text coercion needs separate validation. DESeq2, peak construction, seqinr and other downstream analyses remain separate.

## Choose an original runtime

| Edition | Original package / R | Identity and restriction |
| --- | --- | --- |
| `2260` | Rsubread 2.26.0 / R 4.6.1 | Original official Windows package and known R/Matrix/runtime file populations. |
| `2232-R441` | Rsubread 2.23.2 / R 4.4.1 | Unmodified official source commit `666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6`, built with the recorded GCC 13 tool family. |

The second edition is one of **22 recovered upstream commits carrying the same 2.23.2 label**. Its compiler development warnings and the earlier failed R 4.6.1/GCC 14 build remain retained. The paper commit and binary are unidentified. Matching a version label does not recover its annotation, flags, source revision or dirty-runtime state. No paper whose edition is unreported is automatically assigned either profile.

Before any probe, binding requires the known complete file populations under the R home `bin`, `modules`, `etc`, `share`, `library` directories and the private R library. Extra packages that could shadow imports are rejected. It then checks known Rscript/loaded DLL identities and original helper bodies/formals, and rechecks the bound file population. This is recorded integrity and source/runtime continuity, **not complete host/OS authentication**. A different local build or modified R installation requires separate validation; changing the saved expected hashes is not a remedy.

## Export the already validated GPL package

Export requires explicit permission and NEW output paths:

```powershell
python tools/literature/export_rsubread_runtime_package.py --edition 2260 --out build/my-rsubread-2260 --allow-install
```

For the selected older edition, use `--edition 2232-R441` and another new directory. Without `--allow-install`, the command returns SKIP3 and exports no package files. It extracts only previously validated original files from the checked-in matrix evidence, checks every package file against the known profile, includes the complete corresponding original source and GPL license, and writes an expected R-home manifest. It does not execute R, rebuild the package or alter a shared library. Keep the source and notices when redistributing the package.

The separate R runtime is not bundled by this export. In this checkout, the validated R 4.6.1 home is `C:/Program Files/R/R-4.6.1`; the permitted private R 4.4.1 extraction is `build/literature-review/rsubread-runtime/r441-setup-1/R441/root`. Use an existing matching permitted installation/extraction. If it is unavailable and installation is forbidden, skip counting. [Earlier runtime provenance and setup](RSUBREAD_MATRIX_COMPONENT.md) records original intake, private extraction/build steps and retained failures.

## Bind the runtime

Save as `bind-rsubread.json`, adjusting paths to your actual NEW package export:

```json
{
  "schema_version": 1,
  "mode": "BIND",
  "edition": "2260",
  "out": "build/my-rsubread-binding",
  "rscript": "C:/Program Files/R/R-4.6.1/bin/Rscript.exe",
  "r_home": "C:/Program Files/R/R-4.6.1",
  "r_library": "build/my-rsubread-2260/R-lib",
  "allow_execution": true
}
```

```powershell
java -Xmx256m -cp docs/literature/rsubread-execution-development/rsubread-workflow-development.jar org.jlinalg.cli.RsubreadRuntimeCli bind-rsubread.json
```

Paths in requests are relative to the request file, not the shell's current directory. A successful binding records `runtime_bound=true` but performs no counting analysis. Its `COMPLETED.json` is the input to the next step. Existing output directories are preserved and rejected.

## Count declared libraries

Export the small synthetic demonstration data first, or replace the paths below with your real SAM/BAM and annotation files:

```powershell
python tools/literature/export_rsubread_demo.py --out build/my-rsubread-demo
```

This data export installs no software and performs no R analysis. The deliberately overlapping features and literal `NA` gene exercise source behavior; they are not biological study data.

Save as `count-rsubread.json`:

```json
{
  "schema_version": 1,
  "mode": "ANALYZE",
  "out": "build/my-counts",
  "binding": "build/my-rsubread-binding/COMPLETED.json",
  "cases": [
    {
      "id": "rna",
      "files": [
        {
          "path": "build/my-rsubread-demo/single.sam",
          "paired_end": false,
          "count_read_pairs": false,
          "strand": 0
        }
      ],
      "annotation": {
        "path": "build/my-rsubread-demo/genes.gtf.gz",
        "format": "GTF",
        "feature_types": ["exon"],
        "attribute": "gene_id",
        "extra_attributes": []
      },
      "genome": null,
      "options": {
        "useMetaFeatures": true,
        "countMultiMappingReads": false,
        "juncCounts": true,
        "checkFragLength": false,
        "nthreads": 1
      }
    }
  ]
}
```

```powershell
java -Xmx512m -jar docs/literature/rsubread-execution-development/rsubread-workflow-development.jar count-rsubread.json
```

Declare `paired_end`, `count_read_pairs` and `strand` for **every** ordered library. Strand values are 0 (unstranded), 1 (forward) and 2 (reverse). Mixed single-/paired-end files can share one counting call. Original Rsubread ignores `countReadPairs` for single-end libraries; the effective declaration records `effective_count_read_pairs=false` while retaining the requested value. The adapter does not choose units from the filename or alignment flags.

GTF and SAF are explicit formats. SAF uses `GeneID`, `Chr`, `Start`, `End`, `Strand` and one-based inclusive intervals; BED is not silently converted. An optional FASTA `genome` enables the corresponding original junction behavior; `null` remains explicit. `useMetaFeatures=false` retains feature-level duplicate gene rows. Extra GTF attributes remain literal fields.

**Fragment thresholds do not enable filtering.** `checkFragLength=false` leaves `minFragLength` and `maxFragLength` inactive. Original source requires `requireBothEndsMapped=true` when `checkFragLength=true`; the adapter rejects an inconsistent request rather than changing it. The P133-linked ATAC script supplies `maxFragLength=100` but leaves `checkFragLength` and `countReadPairs` unspecified. Its later sample renaming is a separate operation. [Source audit](rsubread-result-development/AUTHOR-CODE-AUDIT.json) retains immutable links and hashes; no unlicensed author script is executed or redistributed.

Every default is written to `effective-request.json`; it is a choice for a **new analysis**, not a recovered paper setting. Boolean options default to the original values: `useMetaFeatures`, `countMultiMappingReads`, `countChimericFragments`, `autosort` are true; `allowMultiOverlap`, `fraction`, `ignoreDup`, `juncCounts`, `isLongRead`, `requireBothEndsMapped`, `checkFragLength`, `primaryOnly`, `splitOnly`, `nonSplitOnly`, `largestOverlap` are false. Long-read true is rejected. Numeric defaults are `minOverlap=1`, `minMQS=0`, `minFragLength=50`, `maxFragLength=600`, `nthreads=1`, `fracOverlap=0`, `fracOverlapFeature=0`. Unknown arguments and contradictory split-only settings fail.

All source input bytes are captured and checked before original counting starts. Original files and captured copies are rechecked after counting. The request spelling, resolved original path, captured relative path, original counting argument and byte/hash identity are kept in `captured-inputs.json` and each `case-info.json`. Repeated identical input spellings retain the same captured file, without collapsing library observations. Duplicate basenames cause upstream to return full captured input paths as targets. Preserve those returned names and the explicit mapping; subsequent sample renaming needs its own mapping.

## Outputs, verification and cancellation

Each `cases/ID/worker` retains exact typed arguments, original complete `arguments.rds` and `complete-original-result.rds`, readable original objects/tables, literal value wires, session/DLL/helper-guard evidence and process logs. Native checks preserve all matrix values/storage/names, all data-frame scalar storage/NA/raw numeric bits, annotation intervals and chromosome/strand union lengths, 14 summary categories, target positions and the 14-metadata-column junction layout. Empty junction tables and additional annotation fields are retained.

These checks do not independently assign reads, discover junctions, validate junction biological annotations, reproduce original GTF parsing or prove a paper runtime. The full original RDS preserves attributes and encoding details that the scalar value bridge does not serialize. [Value API documentation](RSUBREAD_RESULT_VALUES.md) explains the native contracts and restrictions.

Save a verification request:

```json
{"schema_version":1,"mode":"VERIFY","analysis":"build/my-counts"}
```

```powershell
java -Xmx512m -jar docs/literature/rsubread-execution-development/rsubread-workflow-development.jar verify-rsubread.json
```

VERIFY checks the frozen complete file population, hashes, packaged sources, typed declarations, input/library mappings and full native result relationships. It needs neither R nor the original files outside the capture. It does **not** refit/recount, authenticate execution, or independently establish that self-consistent data were produced by the original software. Keep full provenance and the original controls.

To cancel an active batch, create `CANCEL` in its output root. Cancellation is checked during input hashing/capture, owned process monitoring, native checks and final admission. A JVM shutdown hook also requests cleanup. Only observed owned process identities are targeted; cleanup receipts report survivors. Pending/provisional outputs remain evidence. File-based cancellation applies until final admission; later changes invalidate frozen capture verification.

## Capacities

Optional `limits` fields set whole-batch captured input bytes (default 512,000,000), whole-batch output bytes including retained sources/metadata (1,000,000,000), per-original-worker wall-clock seconds (3,600), and per-table/matrix rows (1,000,000), columns (10,000), cells (25,000,000), per-text UTF-8 bytes (1 MiB), and total UTF-8 text (64 MiB). Summary tables themselves have 14 rows. Requests allow at most 256 cases, 1,000 ordered libraries per case and 64 original C threads.

Use adequate Java **and original R/native** memory and storage. Counts/annotation/native limits are checked after upstream has produced its objects, and several representations coexist. The process guard samples output sizes and owns the original worker lifetime; it is not a hard aggregate RSS bound, disk reservation or OS sandbox. Peak output between samples may exceed a threshold. R can legitimately delete its own temporary directories; monitoring retries a complete scan and records transient retry counts, while final retained file populations/hashes remain strict. Atomic final admission requires filesystem support for atomic file moves.

## Evidence and development scope

The final artifact has 50 fresh public complete count-list comparisons on Windows Java 21 and eight on Windows Java 25 against separately executed original references. They cover both source/runtime editions, mixed libraries, fractional/stranded/fragment settings, all original annotation/summary/junction/target values, empty junctions, typed identifiers, extra attributes, compressed GTF and BAM. Windows Java 25 and Linux Java 21 verify the frozen captures; actual Linux analysis/binding return SKIP3. Process tests exercise real R timeout, output limits, observed-live cancellation and interruption. Failure controls retain earlier original results without admitting them, and deeper verification rejects inconsistent annotation/member/population changes even after outer hash metadata is recomputed.

The evidence retains the first rejected batch, its original source/build snapshots and all earlier attempts. No failure is relabeled as a pass. The private package exporter is permission controlled; mutable packages and shadow imports are rejected before R starts. Original package source, runtime/compiler warnings and prior source/build failures remain in the earlier matrix evidence. The full 250-paper implementation goal remains unfinished; Cell Ranger remains excluded at the user's request. Catalog availability and primary-use review updates are separate validated steps.
