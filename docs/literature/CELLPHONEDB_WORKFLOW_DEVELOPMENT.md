# CellPhoneDB original-workflow development checkpoint

The development JAR adds typed original CellPhoneDB5.0.1 simple, statistical and supplied-DEG execution, a separately permitted runtime installer, and independent native Java checks. The published250-paper catalog still reports84 new scoped integrations,87 including existing adapters and247 missing tools. CellPhoneDB remains uncredited there until its paper-role/registry/catalog integration and catalog checks are finished. Twelve paper-role drafts and five newly observed software definitions remain unapplied. The whole project goal is unfinished.

Use the artifact path and SHA256 recorded in [the validation receipt](cellphonedb-workflow-development/VALIDATION.json). This is an additive development artifact, not a release or replacement for the canonical executable. Every20934 parent JAR member remains byte-identical. The existing native [preparation](CELLPHONEDB_PREPARATION_DEVELOPMENT.md) and [prepared-statistics](CELLPHONEDB_STATISTICS_DEVELOPMENT.md) evidence stays frozen.

## Run the development adapter

Use Java17 or newer. Replace `DEVELOPMENT.jar` below with the actual development JAR. Original execution and installation require Linux x86_64, including WSL; Windows can export the kit and run the portable native APIs. The runtime filesystem must be case sensitive.

```sh
java -cp DEVELOPMENT.jar org.jlinalg.cli.CellPhoneDbSetupCli --export /absolute/new-kit
```

Read the exported `SETUP.md`. It supplies the exact micromamba binary version/size/hash and a copyable setup command. With a permitted exact installer:

```sh
python3 -I -B /absolute/new-kit/setup/prepare.py \
  --out /absolute/new-setup-receipt \
  --prefix /absolute/new-runtime \
  --micromamba /absolute/permitted/micromamba \
  --allow-install --allow-download
```

Installation and uncached downloading need their separate explicit flags. For offline installation, omit `--allow-download` and supply repeatable `--archive-cache /absolute/cache` directories with the exact locked archives. The fresh validation installed43Conda archives and49Python source archives into a new private prefix, checked52 distribution versions, original wheel members and imports, and verified unchanged complete runtime trees. It used the supplied exact micromamba2.10.0, not an unpinned installer. RUN never installs software or changes a shared environment.

Copy [the simple request example](cellphonedb-workflow-example.json) beside your input files. Replace the runtime receipt, expression, metadata and database paths, declare the actual database edition/provenance and scientific choices, and choose a NEW output path. The example thresholds are illustrative choices, not inferred parameters from any paper. Counts should be the original-method-compatible normalized expression you intend to analyze; this adapter does not normalize raw data. Metadata requires unchanged `cell` and `cell_type` fields. No curated database, synonyms, species conversion or biological dataset is bundled.

```sh
java -Xmx4g -cp DEVELOPMENT.jar org.jlinalg.cli.CellPhoneDbWorkflowCli \
  --request /absolute/request.json
```

Counts accept explicitly declared CSV, TSV or H5AD X, including validated dense/CSR float32/float64 controls. Metadata, microenvironments, supplied DEG marks and active-TF tables accept CSV/TSV. The database is a supplied data-only ZIP with the six required original tables and optional gene-synonym/TF tables. Arbitrary Python objects, pickle inputs and implicit suffix guessing are rejected. Duplicate gene rows and many-to-many gene relations are retained. Metadata coercion that changes a cell/label, including leading-zero labels parsed as integers, fails admission.

For statistical mode use `"method":"statistical"` and add the following explicit `statistical` object. Choose the values for your analysis;100permutations is a software-control illustration. The original serial method has a source progress-step failure at very low iteration counts; it is not repaired to admit results.

```json
{
  "iterations": 100,
  "debug_seed": 4711,
  "pvalue": 0.05,
  "subsampling": {
    "enabled": false,
    "log": false,
    "pcs": 2,
    "cells": null,
    "failure_policy": "fail"
  }
}
```

For DEG mode use `"method":"degs"` and supply `"degs":{"path":"degs.tsv","format":"tsv"}`. Do not add statistical options in simple/DEG mode. DEG fitting is separate: the adapter consumes supplied marks. Optional `microenvironments` and `active_tfs` have the same path/format shape; original simple mode has no active-TF input. `score_interactions` runs the original expression-specificity scoring code. All methods declare thresholds, identifier field, precision, separator, seed and threads explicitly.

## Warnings and required actions

**SKIP3 means dependent analysis is skipped.** An unavailable/forbidden installer, download or runtime, unsuitable filesystem/platform, missing required exact archives, or unsupported requested edition does not produce an analysis. Read the printed error, `WARNINGS.md` and setup/analysis status. Obtain permission or the exact missing dependency through an approved route, choose suitable NEW paths, and retry. If installation is not allowed, keep the analysis skipped. Reported5.1,5.0.0 and2.1.4 are never silently substituted with5.0.1.

**FAIL2 means no result from the entire requested batch is admitted.** Earlier completed cases remain provisional when a later source method, audit, runtime-integrity check, cancellation or capacity check fails. Preserve the entire directory and originals, resolve the named cause, and retry NEW paths. Only exit0 AND `analysis-status.json` `result_admitted=true` admit the whole batch. Read WARNINGS.md and every original stdout/stderr/error/process receipt even on success. Native input-disposition ledgers retain unknown genes, all source/DB relations, removed cells, zero-protein decisions and every changed float32 cast.

**Subsampling is not made deterministic by the supplied seeds.** Original full-method `Subsampler` initializes with `debug_seed=None` and reseeds NumPy from entropy. It also catches internal PCA/sketch errors and returns full counts. The default explicit policy `"fail"` rejects that fallback. `"retain-original-full-counts"` must be chosen explicitly to retain it, with a prominent warning that the result is not subsampled. Actual selected cells, source exceptions and RNG states remain saved. A separate legitimately seeded original subsampling-kernel control demonstrates observation parity; it does not alter the full method's entropy seed.

**Original semantics remain visible.** Fractions require strict `>threshold`; permutation tails require strict `>observed`, hits/iterations, no plus-one correction, and source observed-zero/gate rules. P cutoff0 is falsy in original significance masking. Simple exported means remain ungated. Complex deconvolution uses the original complex multidata index for its cluster values. Source errors for alias annotations, certain no-complex inputs and inconsistent protein IDs remain failures rather than repaired inputs. Original UserWarning suppression is unchanged. No input/output missing, NaN/Inf, class-sentinel or error state is silently replaced to force admission.

Debug pickle files remain opaque provenance bytes. The public worker and controls never deserialize them. Data-only JSON/base64 snapshots record actual return values with unchanged original function/module/code bindings; line/opcode tracing is disabled and no locals, RNG draws or namespace functions are replaced. Automatic approval review rejected the earlier proposal to load pickle objects because it could execute code; the implemented path uses these data-only snapshots.

## What was checked

Nineteen positive paired original workflows cover13 previous method/control families plus CSV, numeric canonical labels, dense H5AD, CSR H5AD, fractional float32 H5AD and a two-process statistical run. Seventeen deterministic serial cases match every returned family, original TSV byte and RNG state against the uninstrumented original calls. The full entropy-sampled case checks actual selected-cell membership/values without a false repeatability claim. The parallel case checks real-data results and independently derives P values from its actual packed null statistics without promising serial/parallel null-byte identity. A separately seeded original subsampling-kernel control matches complete selection, values and RNG state.

All19 source-output families pass independent native preparation, observed-kernel and complete TSV checks on actual Windows Java21, Windows Java25 and Linux Java21. Every raw mapped/filtered value, relation/composition identity, cluster mean/fraction, interaction mean, gate, supplied DEG flag and actual-null-derived P value is checked at the scoped stages. Every exported table cell is compared with its typed original return, including source dtypes and non-result class/empty states. No floating tolerance is widened to force these controls to pass.

Seven coherent corruption tests reject altered mapping, axes, means, actual null bits, missing output families, false empty CellSign states and a modified TSV with an updated checksum. Public command checks cover the complete19-case batch, missing runtime/edition SKIP3, a later-case source failure after an earlier success, runtime-profile corruption and changed leading-zero labels. Separate producer protocols cover six known source/input failures, atomic later failure, both subsampling-fallback policies, unsafe object input, wall-time and valid output limits. Export protection, Windows platform SKIP3, denied installation and unsuitable-filesystem SKIP3 use actual process exit codes.

The validation archive also retains failed attempts: safe-worker comparison1 exposed entropy reseeding; native-audit attempts1–4 exposed selected-metadata, empty-map, exact floating-index and object-column serialization issues in the new audit; protocol batch1 requested an invalid1byte cap; Windows protocol1 asserted the wrong license location; packaged control collection1 used the ordinary synthetic database instead of the required distinct-alias error database, so that two-case scientific batch correctly passed while the control collection failed. The corrected collection uses the original error fixture database without changing its inputs or software. These are failed historical controls, never promoted to passing evidence. Their inputs, errors and available source snapshots remain retained.

Scoring, CellSign, NumPy random draws and subsampling geometry execute original upstream code. They have original-method comparison controls; Java does not independently reimplement or validate every upstream algorithm. No database authenticity, whole-paper reproduction, biological communication, calibration, whole-goal or release claim is made. Runtime supervision bounds wall time, output/files and owned processes; it is not OS RAM/CPU isolation. Default input/matrix/operation capacities and Java heap are explicit; larger work must fit declared limits and available memory, and no truncated family is admitted.
