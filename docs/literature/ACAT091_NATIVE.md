# Native ACAT 0.91 matrix combination

`acat-tools` now exposes native Java matrix p-value combination with saved
RUN/VERIFY/REPLAY captures. The bounded reference is the checked
`ACAT(Pvals, weights, is.check=TRUE)` function in upstream ACAT 0.91, commit
`e737d7d188ba6731800c5e6971539ca6a3888405`. The selected original source,
DESCRIPTION, NAMESPACE and byte pins are packaged with the implementation.
The upstream files retain their GPLv3 terms. See the
[official source](https://github.com/yaowuliu/ACAT/tree/e737d7d188ba6731800c5e6971539ca6a3888405),
[working example](../../examples/acat091/README.md) and
[handover](HANDOVER_2026-11-22_ACAT_SKAT.md).

The new `org.jlinalg.settest.Acat091` API supplies `combine` for vectors and
`combineMatrix` for row-major matrices. Every column is a separate combination.
The command consumes finite numeric JSON values, a nonempty rectangular
`p_values` matrix, optional exactly matching `weights`, optional unique literal
`column_ids`, and explicit limits. Omit weights for the source's unweighted
behavior. There is no broadcasting, automatic parsing of strings, identifier
repair, missing-value omission or selection of scientifically appropriate
weights. The caller declares which p-values belong together.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "engine": "native",
  "profile": "0.91",
  "operation": "COMBINE",
  "out": "NEW-output",
  "p_values": [[0.01, 0.2], [0.1, 0.3]],
  "weights": [[1, 1], [1, 1]],
  "column_ids": ["declared-A", "declared-B"],
  "limits": {"maximum_cells": 1000000, "maximum_columns": 10000}
}
```

Run with `java -jar YOUR_CURRENT_JAR acat-tools --request REQUEST.json`.
VERIFY accepts `--verify CAPTURE`, or a request with `schema_version:1`,
`mode:"VERIFY"` and `analysis:"CAPTURE"`. REPLAY accepts those fields with
`mode:"REPLAY"` and `out:"NEW-output"`. Replay must resolve outside the parent
capture, including through directory aliases. The parent is verified before
and after execution. These local integrity checks do not authenticate a producer
and do not establish protection against hostile concurrent filesystem changes.

Default matrix limits are 1,000,000 cells and 10,000 columns; permitted maxima
are 10,000,000 and 25,000. Requests and individual capture members have a fixed
16 MiB capacity. Column IDs preserve literal text, must be unique, and allow
1–128 UTF-16 units without controls or unpaired surrogates. Resource limits can
refuse an otherwise shaped request. Keep any partial capture and reduce the
declared batch or provide appropriate heap capacity before retrying NEW output.

## Scientific scope and numerical admission

The implementation follows the source threshold `p < 1e-15`, its direct weight
normalization, endpoint checks and Cauchy calculation. It reproduces the selected
64-bit-significand R 4.6.1 column sum/mean behavior explicitly; deterministic
`StrictMath` supplies native transcendental functions. Tests compare complete
artificial matrices with actual unmodified upstream R function calls, including
errors, warnings and nonfinite returns. Positive probability comparisons use
strict tail tolerances rather than a permissive absolute-only threshold.

The existing stable `Acat`, `AcatTests`, ACAT-V/ACAT-O and `rare-meta` methods
remain unchanged. Their numerical or model behavior is not substituted for this
profile. The new command does not fit null models or genotypes, calculate
ACAT-V/ACAT-O, run SKAT/SKAT-O, correct FDR, or reconstruct a signed Z score.
The original ACAT 0.91 namespace exports ACAT, ACAT_V and NULL_Model; an exported
ACAT_O function is not present in that selected package.

**FAIL2: no complete result is admitted if any column is numerically unadmitted.**
Raw values and warnings remain in `native-result.json`; the whole batch withholds
`p-values.tsv`. In particular, a zero-weight input zero can yield source NaN;
overflowing sums of finite weights can produce a misleading source 0.5; and
reciprocal overflow for an ultra-small positive input can produce numeric zero.
Those returns are retained and refused, without rescaling, clipping, dropping
rows or silently falling back to another mathematical method. A literal input
zero retains the source warning and raw zero; an admitted numeric zero does not
establish infinitely precise biological significance. Source zero and one checks
include zero-weight rows. Both endpoints in one column are an error.

**Read `analysis-status.json` and `WARNINGS.md` before using an export.** Captures
include the exact request, reference source, native class hashes, all diagnostics,
completion/admission status and a full file inventory. VERIFY checks the inventory,
source/class identity, warnings, entire export and complete native recomputation.
A consistent failed capture can pass VERIFY while `result_admitted` stays false.
Interrupted or malformed partial captures need not have a complete verifiable
inventory. They remain evidence and must be retained.

**SKIP3: the dependent analysis did not run.** Supply the exact named permitted
Java/runtime or missing packaged source resource; download or privately install
when allowed. If installation is prohibited, retain the skip and identify the
affected analysis and exact prerequisite. **FAIL2** also covers invalid input,
process, capacity and integrity errors: preserve raw, null, failed and partial
evidence, resolve the named cause, and retry NEW output. No old hash refresh or
overwrite is an acceptable substitute for resolving changed evidence.

## Validation and catalog status

The normal build passes **500 focused tests**: 274 original ACAT matrix cases,
192 original R column sum/mean pairs, 19 CLI capture/admission/replay checks,
six native API checks and nine preceding ACAT tests. The same 500 pass against
the actual executable JAR on Windows Java 21, Windows Java 25 and Linux Java 21.
All 22,440 unchanged preceding JAR payloads match byte for byte; the one modified
preceding payload is the reviewed global dispatcher, with ten new payloads.
The final replay regression exercises an actual owned Windows junction or Linux
symbolic link and checks that the parent remains unchanged.

This is a qualified component checkpoint. It adds **zero catalog family credit**.
Canonical totals remain **146 scoped families, 1,639 reviewed uses and 1,083 pending**.
The 250-paper objective is unfinished. Source review/admission for ACAT and a
standalone SKAT execution adapter remain next steps. The public
[validation record](acat091-development/QUALIFICATION.json) binds the actual JAR,
fixtures and retained development failures. Full private evidence and local
runtimes must be transferred separately; the handover lists their exact paths.
