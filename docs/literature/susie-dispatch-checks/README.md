# SuSiE global command and replay-parent qualification

The current source-built normal JAR exposes `susie-tools`; the native `susie`
command remains a separate choice. See the [current command guide](../../vignettes/susie-tools.md)
and [current handover](../HANDOVER_2026-11-20_FINAL.md).

This checkpoint also fixes an observed workflow-control defect: REPLAY could
create output inside its original capture and invalidate that capture's
inventory. The retained reproduction uses a disposable copy; the original
scientific capture remained valid. The guard now refuses nested destinations
and existing-path aliases before creating output. Model-fitting code is unchanged.

Qualification uses the normal Gradle executable JAR, not an overlaid producer:

- 173 checks pass without skips on each of Windows Java 21, Windows Java 25
  and Linux Java 21, including original workflow, native fine-mapping,
  Scanpy/Enrichr regressions, saved original captures and refusal checks.
- Five actual root-route model comparisons against independent original R
  objects have maximum numeric absolute difference zero: three converged RUNs,
  one deliberately refused nonconvergence and one original-runtime REPLAY.
  Credible-set membership/order and iteration/convergence fields agree; call
  provenance alone is excluded. Actual R fitting ran on Windows, not Linux.
- Windows nested/junction and Linux symlink controls each return FAIL2 before
  output creation, with the parent still passing VERIFY.
- Exactly two of 23,460 decoded JAR payloads change: `JLinAlgCli.class` and
  `SusieWorkflowCli.class`. All other 23,458 payloads are identical. Raw test
  stdout retains a stale one-class caption; the saved byte inventory and both
  pairs of class files establish the two changes. The caption is not evidence
  of unchanged replay control.
- The first 99-test run retains seven optional-reference skips per host. The
  supplied-reference second run passes 99/99; final qualification passes
  173/173. Earlier attempts, diagnostics and original model objects are retained.
- The unchanged preceding catalog owner and portable reader pass in a separate,
  clean checkout at `05a3b132447c60b93fd0c51401c4e17a79405f30`. The preceding
  reader runs with Git absent from PATH. No historical source lookup is overlaid.

`QUALIFICATION.json` binds 135 current source files and all 875 payloads in
`evidence.zip`. `previous-workflow-control.java` preserves the exact preceding
control source. Old qualification fingerprints remain frozen; they do not
accept the changed control producer. The new control has this separate
qualification. Only the three additive dispatcher/help lines have a generic
declared source inverse.

From the repository root, with Python 3.11 or later:

```text
python -B tools/literature/verify_susie_dispatch.py --out build/susie-dispatch-saved-readback-NEW
python -B tools/literature/close_susie_dispatch.py --verify --out build/susie-command-catalog-readback-NEW
```

The first command reads saved evidence without R, Git or model refitting.
The second checks the applied command-discovery metadata and clean 820-entry
manifest; it requires Git and the retained catalog sources. Do not reapply
the completed transition. Counts remain 138 integrations, 1,503 reviewed
uses and 1,201 pending; no new family or review credit is claimed.

**SKIP3: restore the named exact permitted missing source/runtime/input or
keep the dependent analysis skipped. FAIL2: no complete result; preserve
inputs, partials and diagnostics, resolve the named cause and retry a NEW
output directory. Never refresh older hashes to accept changed evidence.**

These are bounded source/synthetic qualifications, not author-runtime,
historical-host, whole-package or whole-paper reproduction. Linux R scientific
deployment, exact susieR 0.11.43 and declared hosted/downstream roles remain
unqualified. SHA-256 provides integrity, not authentication or a signature.
The published 0.4.0 release predates this route; build current source.
