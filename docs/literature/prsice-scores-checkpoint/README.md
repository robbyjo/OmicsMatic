# Native PRSice scoring checkpoint

The Java hard-call scoring component is validated against 96 original PRSice
2.3.5 fixture executions: 48 combinations on each operating system and 1,152
native/original score comparisons. All 18 selected Java tests pass on Windows
and Linux. The comparison respects the original nine-significant-digit output;
internal full-double identity is not claimed. These are artificial data.

**No PRSice catalog admission or full execution adapter is claimed.** The
catalog remains at 160 scoped families and 1,765 reviewed uses. Five source roles
are frozen separately, with all 250 XMLs, 17 eligible prose anchors and seven
other structures checked. Patch editions and original paper analyses remain
unqualified. Current source is separate from existing release binaries.

Read the [Java API guide](../PRSICE_HARD_CALL_SCORES.md), especially its warning
about the original non-additive missing/centering convention. Always check the
result status. Nonfinite/unavailable results remain unadmitted and retain their
raw values. No filtering, harmonization, clumping, model fitting, threshold
search or model selection is supplied by this component.

From the repository root, check public source/evidence without computation:

```sh
python tools/literature/prsice_scores/verify.py
```

Recompute the packaged native fixture, using the exact qualified JAR and a
permitted JDK (Java 21 was tested; compiled code targets Java 17):

```sh
python tools/literature/prsice_scores/verify.py --jar EXACT_QUALIFIED_JAR --out NEW_OUTPUT
```

Supply `--java` and `--javac` when those commands are not on PATH. Add
`--upstream-home ORIGINAL_PRSICE_2_3_5_HOME` to execute all 48 original scoring
combinations on the current qualified Windows/Linux platform. The helper
generates its own artificial fixture and preserves all original outputs/logs.
It does not analyze user data. The allowed binary identities are in
`tools/literature/prsice_scores/source-profile.json`; binaries are private
dependencies, not bundled public artifacts.

An optional `--cache PRIMARY_XML_DIRECTORY --papers EXACT_PREDECESSOR_PAPERS`
rechecks all 250 primary sources. Source metadata must retain the frozen
pre-admission bytes. The predecessor commit is
`13d2c66da78ee2ad535d38863e991f67a7c39247`; PREDECESSOR-CONTEXT.json identifies
the exact 1,063-file physical context in which the unchanged old catalog reader
passed. No old verifier, expected source hash or producer hash was refreshed.

**SKIP3: dependent verification skipped.** Restore the exact named JAR, source
inputs or permitted JDK/runtime; download/install only where allowed. If the
environment prohibits installation/execution, retain the skip. Normal native
API use needs no PRSice runtime. Historical verification uses the retained
qualified producer; a new build needs a separate qualification.

**FAIL2: no verified result.** Preserve the attempt, raw/nonfinite values and
logs, resolve the named data/source/producer/output cause and retry NEW output.
Existing outputs are preserved. Do not change old expected hashes or infer
scientific choices. The upstream Windows/Eigen warning, BGEN limitations and
historical R-squared differences remain outside this component's qualification.

FILES.json binds public source and evidence. STATUS.json identifies the exact
source-built producer. Raw runtime bindings, machine/process/cleanup receipts
and original command logs remain private. Hashes establish integrity rather
than signatures or host attestation. No tag or release is created here.
