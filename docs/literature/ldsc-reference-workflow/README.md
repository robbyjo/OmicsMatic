# Native reference LD-score workflow qualification

This record covers the native captured reference-preparation command and its
LD-score SDK update, using selected unchanged CBIIT package 3.0.2 source at
`6c673952cee74bd5c57aef1555a03b1c015399a0`. The root CLI's literal banner is
3.0.1; both source identities are preserved. Read the
[beginner guide](../../vignettes/ldsc-reference-preparation.md),
[complete synthetic examples](../../../examples/ldsc-reference/README.md) and
[warnings with required actions](WARNINGS.md).

## Scope and evidence

- Twenty focused tests pass against the packaged JAR on Windows Java 21 and
  Linux Java 21: ten SDK and ten workflow tests. Sources compile for Java 17.
- Both API and file boundaries check all 37 original-core models and all
  75,226 reference fields. These are the same 37 models exercised at different
  boundaries/platforms, not additional independent model populations.
- Thirty-seven complete API payloads are byte-identical across platforms.
  Comparison against the earlier qualified API preserves every numerical,
  graph, audit and warning field; only the declared retained-byte estimate
  increases to cover count vectors and binary overlap storage.
- Only LD-score SDK class members change in the earlier qualified API JAR;
  new members belong to the SDK graph record and new command. All unrelated
  prior JAR members remain byte-identical.
- Fourteen successful actual command launches cover seven corresponding
  Windows/Linux cases, with 182 byte-identical bundle payloads. Seven additional
  main launches test four SKIP3 and three FAIL2 outcomes, including a deliberately
  incomplete backend. Four late-failure main controls reject changed original
  input, changed captured input, changed class-directory code and closed console.
- Three complete public examples run through the actual main entry point on
  both platforms: six additional successful launches. Total actual native
  analysis launches are 31: 20 successes, four SKIP3 and seven FAIL2 controls.
- Five unchanged upstream root-CLI runs exercise unpartitioned SNP windows,
  BP windows, binary annotations, continuous annotations and print membership.
  Their 154 total printed rows match the native output within the root CLI's
  three-decimal printing tolerance. Binary/unpartitioned counts match exactly;
  the observed continuous annotation-sum difference is `3.55e-15`, retained.
- Two unchanged upstream subset runs fail before estimation in the original
  `np.nonzero(pd.Series)` join. Their failures are retained and not admitted as
  results. Original-core fixtures separately establish native subset math and
  transport; full root-CLI subset parity is not claimed.

Initial development failures are retained: the first packaged test decoded
an exact JSON numeric token incorrectly in its test helper; the initial
root-CLI comparator required bitwise equality for differently accumulated
continuous sums. Neither was a change to the scientific oracle or source.
The two upstream subset failures remain genuine failures. No original
function was patched or monkeypatched to obtain success. The isolated
4,441-file original runtime and selected source guards were checked unchanged.

`VALIDATION.json` gives exact source, fixture, artifact and archive hashes.
`checks.zip` has a complete indexed inventory; every archived payload is read
back. Large private runtime binaries and the development JAR are not
redistributed in the bundle; their identities and execution records are saved.

## Relationship to earlier evidence

The [earlier API qualification](../ldsc-scores-development/README.md) and all its
fingerprints remain unchanged. Its exact-source verifier correctly rejects
the updated SDK source. Historical guarded SDK/test bytes are bound to actual
Git commit `a39ac83c4720be26cf94a06f0e069770af7d3ff9` and saved separately in
the new archive. The fresh numerical/package comparisons qualify the changed
source; they do not waive or refresh the earlier record.

The earlier stratified workflow and its source/evidence remain intact. This
component adds no extra LDSC family credit: the catalog remains 128 new scoped
integrations, and the overall 250-paper implementation/review goal is open.
No biological, whole-paper, whole-tool or historical-edition equivalence is
established. No website deployment or release is part of this qualification.

## Developer audit

From a Git source checkout with Python 3.9+ and Git available:

```powershell
python -B tools/literature/ldsc_reference/verify.py --out build/ldsc-reference-audit-NEW
```

Choose a new output directory. The audit checks current exact source bindings,
the complete archive and its internal inventory, historical source against
the actual earlier Git commit, and the preserved parent qualification archives.
It rechecks saved evidence; it does not rerun upstream LDSC or JUnit. Missing
required evidence yields SKIP3; changed/inconsistent evidence yields FAIL2,
with explicit required actions. Runtime analysis itself needs Java 17+ only.
