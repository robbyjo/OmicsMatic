# Native pySCENIC AUCell cell-activity component

`org.jlinalg.enrichment.PyScenicAucell` computes supplied-signature cell activity
in native Java. It implements the selected pySCENIC0.12.1/ctxcore0.2.0 function
profile, including NumPy1.23.5 seeded column permutation and pandas1.5.3 ranking
and output layout. **Full SCENIC coexpression/motif-pruning workflows, sealed
capture/recomputation/replay and family catalog admission remain pending.**
There is no new family or canonical paper-review credit in this component stage.

Use the current complete source build. Existing release assets can predate this
class. Java source targets17; validation covers Windows Java21/25 and Linux21.
Native analysis needs Java only. Original Python packages are needed to regenerate
original comparisons, not to call this native API.

The [copyable example](../../examples/pyscenic-aucell/README.md) compiles against
the project JAR. Its expression matrix and supplied signature are artificial:

```java
var options = new PyScenicAucell.Options(
    0.25, false, false, 17,
    PyScenicAucell.Layout.SERIAL_UNSTACK,
    PyScenicAucell.Limits.defaults());
var result = PyScenicAucell.scoreExpression(
    cellIds, geneIds, expression, suppliedSignatures, options);
// Inspect result.diagnostics() and result.eligible(cell, regulon).
```

For already prepared source ranks, use `prepared(cellIds, geneIds, ranks, limits)`
followed by `score`. Every prepared row must permute zero-based ranks0..N-1.
The native rank result exposes the shuffled gene order, full ranks and original
column permutation. Returned lists/maps and array snapshots are immutable.

## Exact source behavior

The sources are [pySCENIC0.12.1](https://github.com/aertslab/pySCENIC/tree/ce41b61b6570490949bd12b514e9f6de46d19c1f),
[ctxcore0.2.0](https://github.com/aertslab/ctxcore/tree/e68a0b168b9511efb3140c5cf1dd2b07bbd811cc),
and [NumPy1.23.5](https://github.com/numpy/numpy/tree/de82cd9468704a033702974010ee7e7efc85b393).
Their selected original GPL/BSD source bytes and notices are packaged under
`org/jlinalg/omics/pyscenic-ce41b61/`, with a complete selected-source manifest.
The component reuses the unchanged native `CisTargetRecovery.ctxcoreDirectAuc`.

- An explicit integer seed0..4,294,967,295 selects NumPy RandomState/MT19937
  permutation. One gene-column permutation is shared across cells. Within each
  cell, larger expression ranks first; ties use that shuffled order. Expression
  NaN ranks at the bottom; infinities and signed zero follow source ranking.
  Values are ranked, not imputed or relabeled.
- Gene matching uses exact literal identifiers. At least80percent of a signature's
  genes must be present, with at least one matching gene. Otherwise the source
  returns zero placeholders and a coverage warning. These zeros are retained
  with false eligibility, not interpreted as inactive regulons.
- The rounded source count is Python ties-to-even `round(fraction * geneCount)`.
  A covered signature requires this count to be positive and strictly below the
  genome size. The recovery cutoff is one less. The weighted-step numerator and
  denominator follow ctxcore's source convention; no attainable-signature
  renormalization or added pseudocount is applied.
- A rounded count of1 gives an empty recovery window and source zeros. The
  component marks these uninformative scores ineligible even without optional
  normalization. Invalid cutoff/weight validation is bypassed by the source's
  insufficient-coverage branch; diagnostics preserve that distinction.
- `noWeights=true` explicitly uses unit weights. Otherwise supplied weights are
  used in ranked-column order. Computed weighted profiles require finite weights
  and a finite positive source denominator. Negative finite weights and their
  unclamped values are retained as diagnostics with false inference eligibility.
- `normalize=true` divides each column by its maximum across cells. It preserves
  source NaN/Infinity on division by a zero maximum. False eligibility remains
  false. Without normalization, a legitimate finite zero with a nonempty window
  can be a computed numerical activity summary; it is not a calibrated hypothesis
  test or proof of biological inactivity.
- `SERIAL_UNSTACK` matches source `num_workers=1`, sorting both axes by Unicode
  scalar order. `MULTIPROCESS_INPUT_ORDER` matches successful multiprocessing
  output layout. Native arithmetic runs without Python subprocesses. The layout
  option does not reproduce original worker-crash bugs.

This is the function-level `create_rankings`/`aucell4r`/`aucell` profile. Do not
infer command-line `--weights` defaults from API `noWeights` defaults. Caller
options must be explicit when reproducing a source command. Author-reported or
unreported seeds/settings are not recovered by selecting a project example.

## Existing AUCell remains separate

| Behavior | Existing `SignatureScores` AUCell | New pySCENIC component |
| --- | --- | --- |
| Prepared ranks |1-based |0-based source permutation |
| Ties | Input feature order | Seeded NumPy column shuffle |
| Signature matching | Supplied feature indices | Literal genes and80percent coverage branch |
| Weights | Unweighted | Explicit weighted or unit-weight source profile |
| Area convention | Attainable-signature normalization | ctxcore weighted-step denominator |
| Optional normalization | Separate existing contract | Per-regulon maximum across supplied cells |

Neither existing methods nor their saved source hashes are changed. Neither
profile is advertised as a complete SCENIC implementation or an R/Python
interchangeability guarantee.

## Limits, warnings and required actions

The supported native input has nonempty, unique cell/gene/regulon labels and
nonempty numeric signatures. Duplicate identifiers are refused, not repaired.
Literal `NA`, `NaN` and `Inf` identifiers remain strings. Nonfinite expression
values participate in the declared ranking rule; nonfinite computed weights or
overflow produce no complete native result. Signature weights can remain ignored
or uninspected in the exact `noWeights`/insufficient-coverage source branches.

Default caps are32million matrix/output entries,2billion estimated work units
and1GiB estimated input/rank/result storage. Work includes combined ranking and
scoring for `scoreExpression`. Limits are configurable estimates, not an OS hard
RAM quota. Interruption, malformed input or exceeded caps throw before a result
is returned. Do not modify thresholds or drop rows merely to obtain a pass.

**FAIL2: no complete analysis result.** Preserve the original inputs and error,
correct the named rank, duplicate-ID, denominator, numerical, resource or cutoff
cause and rerun. Read all diagnostics and eligibility flags. Native zero-window,
low-coverage, negative-weight and nonfinite-normalization values remain explicit.
Changing settings must be a declared scientific choice. Never clamp, impute or
replace undefined output to force eligibility.

**SKIP3: required prerequisite unavailable.** Build/provide the complete current
JAR and suitable Java runtime. If permitted upstream comparison software cannot
be installed, skip that dependent original comparison and report the missing
version and remedy. Native scoring itself needs no Python installation. The
user's Cell Ranger exclusion still applies to separate workflows.

An original pySCENIC multiprocessing call can return parent zero values after a
child assertion failure. The reference retains that error and returned values;
the native component refuses the invalid request. A successful parent return is
not enough to admit an original workflow. Full adapters still need child/output
completion checks and independent scientific recomputation.

The first private install failed because current setuptools lacked the legacy
`pkg_resources` API. A separate compatible pinned environment succeeded without
patching upstream science or changing existing prefixes. Keep both attempts.
The first reference capture omitted caught-warning lists on exceptional returns;
the corrected capture retains them too. Earlier producer JARs and controls remain
historical; use the final qualified producer named by the saved checkpoint.

## Paper scope and next work

Five draft source roles retain P058's mouse/raw-count workflow, P070's reported
0.10.3, P100's0.12.1/mc9nr/dropout-masked workflow, P145's20,000-cell fitting
subset versus full activity matrix, and P225's orthogonal TF-overlap comparator.
Available0.12.1 does not establish0.10.3 equivalence or author environments.
SCENIC+ motif/resource-only uses remain distinct from RNA-only SCENIC inference.

The fresh all250-paper audit also found P153 SCENIC+ benchmarking missing from
the current inventory. Its positive comparator role is retained as an unadmitted
candidate; direct execution versus reused predictions and exact settings need
supplement/code review. It is not silently added or counted as a new RNA-only
SCENIC use. Canonical totals remain154 scoped families and1,697 reviewed/1,025
currently catalogued pending uses; missed-provider screening remains open.

Next finish original GRN/ctx and supplied resource/runtime contracts, full
RUN/VERIFY/REPLAY qualification, historical-version/source-role review and guarded
catalog admission, then continue high-impact gaps including preserved nf-core
and newest-iDEP development. The full250-paper goal remains unfinished.
