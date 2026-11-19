# Original Seurat RNA workflow qualification

**Qualified scope: the current unsplit RNA workflow and surrounding native
audits, using the six tested profiles. The full paper/tool goal remains
unfinished and no new catalog family credit is claimed.**

Read the [user guide](../../vignettes/seurat-rna-workflow.md),
[synthetic example](../../../examples/seurat-rna-workflow/README.md) and
[checkpoint](../SEURAT_WORKFLOW_CHECKPOINT_2026-11-19.md).
`VALIDATION.json` pins current sources and every payload of
`qualification-evidence.zip`. `PACKAGE.json` identifies the exact qualified
development JAR; its 0.4.0 version string alone does not identify these bytes.

Evidence includes:

- Six original-RNA profile captures, plus an actual original-model replay;
  all have successful Java capture/native audits. The six primary profiles
  were verified with the packaged JAR on Windows and Linux Java 21.
- Six JUnit tests on each JVM, including independent set-intersection checks
  of native SNN across neighbor populations, ranks and inclusive thresholds,
  capacity/cancellation refusals and method-inapplicable request options.
- All 74 selected original Seurat R bodies/formals matched the pinned 5.6.0
  source before model calls. This is not whole-host/native-DLL authentication.
- Twenty-six controls: three genuine prerequisite skips, other input/model/
  source/time/output/capacity refusals, rejection of nine failed/skipped
  captures, raw and resealed corruption refusals, and a real successful replay.
  Timeout cleanup records show the owned handles were no longer alive.
- Independent standalone original-R calls on exactly captured inputs matched
  5,376 PCA/UMAP/t-SNE numeric fields within the declared `1e-10` relative-to-one
  tolerance (observed maximum below `5e-15`) and exact cluster cells/columns/
  labels. Original execution was on Windows; Linux qualification covers Java.
- All 23,063 prior JAR payloads were preserved. The two frozen R-library roots
  retained all 24,841 indexed files; the other 20 worktrees and 36 protected
  artifacts remained unchanged. No installation was needed or attempted.

**Retained t-SNE discrepancy:** the earlier independently generated regression
covariates differ from captured covariates by at most `2.22e-16`; their t-SNE
coordinates differ substantially despite nearly identical PCA and identical
clusters. `OLDER-FIXTURE-SENSITIVITY.json` retains that comparison. The
exact-input standalone reference subsequently agrees. A seed is not an input
equivalence guarantee; preserve exact inputs and do not suppress discrepancies.

The first worker attempt completed original models but failed in graph export
because a generic summary dispatch did not return the expected matrix summary.
Its full failed capture remains under `historical-export-failure/`. Direct
sparse-slot export corrected only the protocol. A first reference auditor
also confused the original empty row-name header with the explicit `cell`
header; its failure is retained separately. Neither attempt is admitted as a
successful workflow/reference audit.

The ZIP contains full synthetic captures and partials, source and added
bytecode/resource payloads, test receipts, exact-input original references,
corruption evidence and historical developer helpers. Those helpers retain
machine-specific paths and should be read before adapting. It excludes full
parent/candidate JARs, installed runtimes, private library manifest and article
text. Transfer local private stores separately for a machine handover.

From the repository root, Python 3.11+ can audit exact source/evidence bytes
and recorded outcomes into a **new** directory:

```powershell
python -B tools/literature/seurat_workflow/verify.py --out build/seurat-workflow-public-audit-new
```

To additionally compare all prior JAR payloads and run Java-only verification
of all seven saved complete captures on this workstation:

```powershell
python -B tools/literature/seurat_workflow/verify.py --out build/seurat-workflow-package-audit-new --parent-jar build/seurat-high-impact-1/build-1/jar/omicsmatic-0.4.0.jar --candidate-jar build/seurat-workflow-1/qualification-1/omicsmatic-0.4.0.jar --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe"
```

This does not rerun R, independently refit models, establish biological
validity, authenticate a producer or defeat coordinated fabrication.
**FAIL2 means no complete audit**: retain its `FAILURE.json`/logs, restore the
named exact source/evidence or supply the matching JAR, then use a new output
path. Workflow SKIP3/FAIL2 causes and required recovery are prominent in the
guide and captures. Positive leidenbase execution, all metric/objective
combinations, original Linux R and a full-project test suite are not claimed.
