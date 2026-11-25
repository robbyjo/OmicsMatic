# Native spectral-library annotation preparation

The source-built `spectral-library-annotations` command expands explicit fragment annotations such as `b2^1` and `y12^3` into separate columns. It preserves all original fields and records. It does not identify or quantify spectra, run DIA-NN, or admit a raw-MS analysis.

This is useful when a library contains its fragment charge in an `Annotation` cell but a consuming program expects a separate `FragmentCharge` column. Do not replace missing charges with 1. Use this command only when the original annotation states the charge explicitly.

## Quick start

Build the current source with Java17 or newer:

```shell
./gradlew check executableJar
java -jar build/cli/omicsmatic-0.4.1.jar spectral-library-annotations --example
```

On Windows use `gradlew.bat`. Older release downloads do not acquire this command when the repository changes.

Save a request like this as `request.json`. Replace the path and provenance with your actual values. The input must be an absolute canonical path; use forward slashes in Windows JSON paths, for example `D:/data/library.tsv`.

```json
{
  "schema_version": 1,
  "engine": "native",
  "mode": "expand-explicit-fragment-annotations",
  "annotation_syntax": "by-ordinal-caret-charge-v1",
  "preparation": "Original supplier TSV; Annotation explicitly contains b/y fragment number and charge",
  "input": {
    "file": "/absolute/library.tsv",
    "annotation_column": "Annotation",
    "format": "literal-utf8-tsv"
  },
  "capacity": {
    "rows": 2000000,
    "input_bytes": 536870912,
    "output_bytes": 1073741824,
    "wall_millis": 3600000,
    "fragment_charge": 16
  },
  "process": null
}
```

Use a NEW output directory, then verify the complete capture with the same producing JAR:

```shell
java -jar build/cli/omicsmatic-0.4.1.jar spectral-library-annotations --request request.json --out prepared-new
java -jar build/cli/omicsmatic-0.4.1.jar spectral-library-annotations --verify prepared-new
```

Read `prepared-new/WARNINGS.md` and `OUTPUT-AUDIT.json`. The generated table is `prepared-new/outputs/library.tsv`. The exact original input is retained at `inputs/library.tsv`; `ROWS.jsonl` records every original data row's explicit annotation and parsed fields.

## What changes

The command appends four columns in this order:

| Column | Source |
| --- | --- |
| `FragmentCharge` | Positive integer after `^` |
| `FragmentType` | Literal `b` or `y` |
| `FragmentSeriesNumber` | Positive integer between the ion letter and `^` |
| `FragmentLossType` | `noloss`, as required by this explicitly loss-free grammar |

Every original cell remains a literal string in its original column and order. This includes negative retention-time scales, original intensity precision, identifiers and unused metadata. No mass matching, numeric conversion, reference annotation, intensity normalization or biological interpretation occurs. Original scientific values still need validation by the downstream analysis.

The format is uncompressed literal UTF-8 TSV. Quotes are literal characters; CSV quoting and compressed input are not inferred. Generated records use LF endings and omit an optional leading header BOM. The original bytes, including their original line endings, remain captured unchanged.

## Warnings and required actions

**FAIL2 means no complete preparation is admitted.** Preserve the attempt and read `FAILURE.json`. Correct the named problem and retry into a NEW directory. Unsupported ions, losses, missing charges, leading zeros, blank records, malformed UTF-8, inconsistent widths and capacity failures reject the complete preparation. No offending record is silently discarded. Existing annotation-output columns are refused rather than overwritten; supply an already annotated library directly to its appropriate downstream workflow.

**SKIP3 means a prerequisite is unavailable.** Supply the named complete permitted input or exact source-built producing JAR, or retain the skip. Class-directory execution cannot produce a qualified capture. This preparation command never downloads, installs or launches an upstream runtime. If downstream software, licences, references or installation permissions are unavailable or forbidden, skip the dependent analysis and state the missing requirement.

At most1020 original columns and1Mi characters per record are supported. Bounds cover all records and original bytes. `output_bytes` covers the generated table plus the row ledger; metadata has a separate16MiB reserve. Time/cancellation checks are cooperative. Provision JVM heap for bounded records and charge counters; bounds are not a guarantee of available memory.

**A complete preparation is not a completed DIA-NN analysis.** Preserve the chosen original library and all original-program warnings. A program's library import or exit0 with zero MS files does not establish raw-data processing. For example, text export can omit fragmentless precursors, so do not assume exported text and original binary libraries contain identical populations. Original fitting, FDR, MBR, protein inference, reference selection and paper-specific processing remain separate.

## Saved verification and scope

Validation covers34 focused tests on Windows and actual Linux (ten new preparation tests and24 unchanged iq/GEMMA regressions), three complete captures and21 capture/refusal calls per OS, and all three foreign-host capture replays. Nine generated artifacts match exactly across hosts. An independent full-file check compared983636 records and9836360 original cells. The prepared library imported successfully in original DIA-NN1.8,1.8.1 and2.0.2; the fragment-charge warnings disappeared. These were library-import controls with zero MS files, so they do not qualify a raw search. See [the scoped qualification](spectral-annotations-validation/QUALIFICATION.json) for exact counts and limits.

Verification requires the exact producing JAR, checks the entire file/directory namespace, and replays every source record and original cell. It compares the generated table and row ledger byte for byte. It works from captured bytes without accessing original-host input paths. Changed unused metadata, changed annotation fields and extra files/directories are refused. Hashes establish integrity, not authenticity or biological correctness.

This native preparation component adds no DIA-NN whole-tool or paper-reproduction claim and earns no additional catalog family credit. MaxQuant runtime analyses remain skipped at the user's request while MaxQuant stays pending; Cell Ranger remains excluded.
