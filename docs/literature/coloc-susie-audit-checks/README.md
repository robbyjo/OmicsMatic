# Native SuSiE source-audit and fix evidence

**Scoped native stable method; 12 completely matching profiles out of 28 original
profiles. This is not 28 profiles of literal source parity.** The
[checkpoint](../COLOC_SUSIE_AUDIT_CHECKPOINT_2026-11-19.md) and
[guide](../../vignettes/coloc-susie-native.md) explain every coverage category,
warning, unsupported input and required recovery. Catalog admission is open.

`QUALIFICATION.json` pins production/test/example source bytes, the frozen
266-member original-package fixture and exact evidence archive. The archive
contains source/class snapshots, actual reports and packaged logs, source
capture/comparison provenance, current native captures and explicit ABF
compatibility evidence. The public fixture includes raw original failures and
the finite but nonnormalized source single-variant result; neither is repaired
or admitted as a valid original posterior. Earlier capture `FINITE` labels
describe finite fields only.

The original capture executed coloc 6.0.3 for 26 BF profiles and two actual
original fit pairs, checking seven selected installed function bodies/formals
against pinned source. Native qualification used 50 tests on each of Windows
Java 21/25 and Linux Java 21, packaged success/refusal controls, ABF predecessor
readbacks and an old ABF bundle's native scientific verification. Original R
was not rerun during the JVM qualification. No independent native SuSiE-fit,
whole-package, historical-edition, paper or biological parity is claimed.

From the repository root, with Python 3.11+:

```powershell
python -B tools/literature/verify_coloc_susie_audit.py --out build/coloc-susie-audit-readback-NEW
```

This checks complete recorded bytes/counts/classifications and does not execute
R or Java. Missing/unreadable exact source/evidence gives SKIP3. Changed or
inconsistent bytes give FAIL2. Restore the named original prerequisite, retain
errors and retry a new output; do not refresh historical hashes. A changed
implementation requires a new explicit qualification. SHA-256 is integrity,
not signature or authentication against coordinated fabrication.

To rerun the focused Java checks from source:

```powershell
.\gradlew.bat :test --tests org.jlinalg.cli.ColocSusieSourceAuditTest --tests org.jlinalg.coloc.ColocSusieTest --tests org.jlinalg.coloc.ColocAuditBoundaryTest --tests org.jlinalg.cli.FineMappingCliTest --no-daemon
```

On Linux/macOS use `./gradlew`. This is a rerun recipe; the recorded campaign
used compiled `--release 17` classes and the preserved candidate JAR. Full
Gradle/all-project testing and a release are not claimed. See the tested
[example](../../../examples/coloc-susie-native/README.md) for normal use.
Large runtimes, original full campaign directories and all earlier evidence
remain local and must be transferred separately, as listed in the checkpoint.
