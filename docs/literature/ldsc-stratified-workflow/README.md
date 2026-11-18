# Native stratified LDSC workflow qualification

The [prepared-data command](../../vignettes/stratified-ldsc-workflow.md) adds exact
input capture, full model diagnostics and checked result admission around the
qualified native Java partitioned API. Read [WARNINGS.md](WARNINGS.md) for
SKIP3/FAIL2 actions, retained partials and scientific interpretation limits.

- **48 original-core models and 22,596 reference fields**, checked separately
  through both the API and the JSON/TSV file interface.
- **17 focused tests on Windows and the same 17 on Linux**, without failures
  or skips. The packaged tests compile against the actual new JAR.
- **33 CLI process launches:** 11 expected successes, six expected SKIP3 and
  16 expected FAIL2 controls, including the three complete public examples.
- **46 byte-identical output payloads** from four corresponding successful
  Windows/Linux models, including captured inputs, warnings and manifests.
- A deliberately incomplete runtime omits `jdistlib.T`; the process returns
  SKIP3 and retains its unadmitted component with a clear complete-JAR action.
- Invalid overlap, duplicate rows, malformed UTF-8, noninteger binary counts,
  incorrect annotation order/declarations and existing output are tested.
  Failed console recording is also checked by the focused tests.
- **Every earlier API JAR member is unchanged.** Only the new workflow classes
  are added. The numerical engine and original reference fixtures are unchanged.

The current [verifier](../../../tools/literature/ldsc_stratified/workflow_verify.py)
reads every indexed control payload, checks exact current source bindings and
verifies the prior API qualification against its actual Git snapshot:

```powershell
python -B tools/literature/ldsc_stratified/workflow_verify.py --out build/stratified-workflow-recheck-1
```

`VALIDATION.json` binds the artifact, source guards and `checks.zip` digest.
The archive contains source/test/example snapshots, the first failed build and
subsequent builds, packaged Windows/Linux tests and process outputs, current
catalog verification and original reference inputs/expected fields. Its
`ENTRY-MANIFEST.json` indexes every payload. The earlier
[API scientific evidence](../ldsc-stratified-development/README.md) retains its
original source, reference/runtime provenance and failed test history. This
workflow reuses that qualified reference; it does not rerun Python or claim
additional independent original models.

The older API verifier is specific to its source snapshot at
`e5dafcacaba4d7db6c36ecca52518474b8ca7df7`, including its then-current priority
document. The new workflow verifier is the current entry point. It preserves
historical fingerprints and checks current scientific sources without treating
documentation updates as a new numerical qualification.

This method extension leaves the current catalog at **128 new scoped
integrations**, 24 native Java and 104 upstream, with 260 missing families.
No second LDSC family credit, whole-tool parity or whole-paper reproduction is
claimed. No release, website deployment or remote push occurs in this checkpoint.
