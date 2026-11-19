# Native ABF workflow qualification

**Scoped file workflow qualified; cumulative COLOC catalog admission remains
open.** There is no new family credit, release or whole-paper/tool claim.
Read the [checkpoint](../COLOC_ABF_WORKFLOW_CHECKPOINT_2026-11-19.md) and
[workflow guide](../../vignettes/coloc-abf-workflow.md).

`QUALIFICATION.json` pins live production/test/example sources, the earlier
synthetic original-package fixture and exact evidence archive. The archive
contains source/class identities, actual JVM test reports and packaged process
logs, seven complete small synthetic bundles and selected failed-attempt logs.
All 23,129 unaffected parent JAR payloads, including the native ABF arithmetic
and old SuSiE methods, were preserved. Only the global dispatcher payload was
replaced, with eight workflow class payloads added.

Evidence covers 62 tests on each of Windows Java 21/25 and Linux Java 21,
packaged RUN/VERIFY/REPLAY on all three, Linux verification/replay of a Windows
bundle, four existing SuSiE/coloc tests, the earlier component packet readback,
and a Linux symlink-input refusal. Original R was not rerun in this workflow
campaign; all 30 frozen cases came from the earlier actual coloc 6.0.3 capture.

From the repository root, with Python 3.11+:

```powershell
python -B tools/literature/verify_coloc_abf_workflow.py --out build/coloc-workflow-readback-NEW --restore-bundles
```

The readback verifies bytes, complete archive population and saved test counts.
It does **not** execute Java/R or independently qualify a scientific analysis.
Optional restoration writes only exact synthetic bundles under the NEW output.
Use the tested/current matching producer JAR to run its scientific verifier:

```powershell
java -jar PATH_TO_MATCHING_JAR coloc-abf --verify build/coloc-workflow-readback-NEW/bundles/windows21-analysis
```

Missing/unreadable exact files give SKIP3; changed/inconsistent evidence gives
FAIL2. Restore original prerequisites, retain partials/errors and retry a new
output. Do not refresh saved hashes. Changed Java sources need a new explicit
qualification; old source/classes and frozen failures must remain preserved.
SHA-256 records integrity and is not a signature/authentication scheme.

Large JARs, full reference campaigns, paper XML and private R libraries remain
local. Transfer paths are in the checkpoint and prior handover. Full Gradle
build/all-project testing and real paper/cohort reproduction are not claimed;
the candidate was compiled with Java 21 `--release 17`, preserving every other
parent JAR payload, and tested through actual packaged execution.
