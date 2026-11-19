# Scoped PLINK-relatedness qualification

Read the [checkpoint](../PLINK_RELATEDNESS_CHECKPOINT_2026-11-19.md) and
[workflow guide](../../vignettes/plink-relatedness-workflows.md).
This packet qualifies the stated original-program workflows and native checks;
**PLINK-family source review and cumulative catalog admission remain unfinished**.

`QUALIFICATION.json` binds source, tests, scripts, candidate/parent artifacts,
archive members and expected capture-verification outcomes. `PACKAGE.json`
pins every original parent payload and each of the 27 additions. `evidence.zip`
retains synthetic original captures, raw failures/corruptions, controls,
Windows/Linux test XML and owned-process records, inputs and development helpers.
No upstream executable/archive, large JAR or full paper text is redistributed.

To recheck with Python 3.11+ and Java 17+, from the project root:

```powershell
python tools/literature/verify_plink_relatedness_workflows.py `
  --project . `
  --jar build/plink-relatedness-development-1/build-6/omicsmatic-0.4.0.jar `
  --java 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' `
  --out build/plink-relatedness-proof-readback-NEW
```

This checks every JAR payload, extracts pinned evidence to fresh output,
invokes actual Java VERIFY for every declared capture, then checks all extracted
files remain unchanged. It executes no PLINK or installer. Run the same script
with local Linux paths and `/usr/bin/java` for portable Linux verification.
The resulting `COMPLETED.json` records actual outcomes. Original captures and
proofs remain unchanged. Unexpected refusal/acceptance is FAIL2, not success.

To rebuild from the exact retained RNA parent using the tested JDK 21 compiler:

```powershell
python tools/literature/build_plink_relatedness_workflows.py `
  --project . `
  --parent build/seurat-workflow-1/qualification-1/omicsmatic-0.4.0.jar `
  --javac 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' `
  --out build/plink-relatedness-payload-build-NEW
```

Compilation targets Java 17 with all warnings treated as errors. Every old
parent member and every new class/resource must match the qualification pins.
Different ZIP timestamps may change the whole JAR hash; the verifier admits
that case only after all member names/bytes match. A different compilation
needs a separate qualification; do not refresh pins to make it pass. Missing
exact parent/candidate returns SKIP3 with recovery instructions. Retain failed
attempts and use new output paths.

[READBACK.json](READBACK.json), [REBUILD.json](REBUILD.json) and
[EXAMPLE.json](EXAMPLE.json) retain actual checks of this public packet/build
helper and all four generated example RUN/VERIFY pairs. The prior
[development handover](../plink-relatedness-handover/README.md) remains frozen
against commit `3ba8369d`; its old source guard intentionally rejects changed
current source. Use that historical source snapshot when checking that draft.
