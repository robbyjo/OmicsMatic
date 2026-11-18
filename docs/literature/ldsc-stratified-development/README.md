# Native prepared stratified LDSC component

The Java API implements the selected CBIIT 3.0.2 partitioned Hsq path
(`old_weights=True`) and binary-overlapping-annotation summaries. Read the
[API guide](../../vignettes/stratified-ldsc.md) and
[prominent warnings/actions](WARNINGS.md) before analysis. This is a method
extension within the existing LDSC family, with no additional integration count.

- Final reference: **48 models and 22,596 checked fields** from unchanged
  original numerical modules, including their overlapping output method.
- Packaged verification: **nine tests on Windows and the same nine on Linux**;
  all 48 corresponding complete model payloads are byte-identical.
- Cases include 2..9 joint annotations, overlapping/disjoint resources,
  variable N, free/fixed intercepts, signed regressors/coefficients, negative
  and above-one heritability, initial weight clamping and missing baseline tests.
- Full and delete rank deficiency, complete-input validation, capacity refusal,
  undefined total proportions, source diagnostics and defensive copies are tested.
- Every prior JAR member is byte-identical to the qualified RG/Hsq JAR. New
  members are the stratified API and its result classes.

`VALIDATION.json` records source guards, artifact digest, tests and archive
read-back counts. `checks.zip` contains original reference commands/outputs,
source module snapshots/license, runtime manifest, both reference generations,
the first failed tests, corrected tests, packaged Windows/Linux outputs and
complete source snapshots. `ENTRY-MANIFEST.json` indexes every archive payload.
The local verifier checks source guards and reads every indexed payload:

```powershell
python -B tools/literature/ldsc_stratified/verify.py --out build/stratified-ldsc-recheck-1
```

The original private Python 3.10.11 / NumPy 1.23.3 / SciPy 1.9.2 / pandas 1.5.0
reference runtime was reused with every saved file checked before and after
execution. The original functions were unchanged; namespace loading bypassed
the unrelated web initializer. End-user analysis uses Java.

The first reference generation is development history, rather than additional
qualified models. The final 48-model set and first failed nine-test attempt are
kept separately, as explained in the warnings. No historical fingerprints are
refreshed. The current catalog remains 128 new scoped integrations with 260
missing families. No website deployment, release or remote push occurs here.
