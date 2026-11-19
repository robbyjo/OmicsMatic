# Native Seurat preprocessing qualification

**Qualified matrix component; the full Seurat workflow and 250-paper goal remain
unfinished.** Use the [API guide](../../vignettes/seurat-native-preprocessing.md),
[synthetic example](../../../examples/seurat-preprocessing/README.md) and
[checkpoint](../SEURAT_PREPROCESSING_CHECKPOINT_2026-11-19.md).

The native API covers the pinned 5.6.0 LogNormalize, RC, both CLR margins and
unsplit/no-regression sparse ScaleData behavior. It preserves source arithmetic,
explicit zeros, exact IDs and full selected populations. It is not a substitute
for SCTransform, feature selection, regression, integration, clustering or an
author's historical runtime. No catalog family credit or release is claimed.

`VALIDATION.json` binds the exact new source, independent original references,
auditor, parent/candidate JARs and ten method classes.
`qualification-evidence.zip` contains **220 indexed payloads**, all read back:

- Six guarded current source files plus the matching auditor source.
- Thirteen reference datasets, 90 normalization and 208 scaling requests from
  Windows R 4.6.1 / Seurat 5.6.0 / Matrix 1.7.5. Three original shape errors are
  retained. All 295 returned views are compared, including diagnostic-only
  values: 15,081 primary dense fields and 920 pipeline fields.
- Thirteen actual packaged tests on each of Windows and Linux Java 21, with
  zero failures, errors or skips. Native results comprise 247 usable matrices
  and 48 explicit numerical refusals with retained diagnostic views.
- Four actual standalone example launches, two successful and two expected
  exit-2 demonstrations. Their output agrees after line-ending normalization;
  method bytecode matches the packaged candidate exactly.
- A genuine original-reference recapture with identical JSON and RDS bytes,
  and four SKIP3 controls. No reference process launches for forbidden
  execution, missing R, missing libraries or a missing required package.
- Preservation receipts for 24,841 private R-library files, 23,053 pre-existing
  JAR payloads, the nine unchanged canonical catalog/manifest/HTML files and
  the other 20 worktrees. Historical prototype captures and corrected fixture
  or recovery-heading failures remain explicitly labelled.

The public packet contains source, synthetic data/results, bytecode and selected
receipts. It does not contain the whole 194 MB private candidate JAR, its
parent, the private R-library manifest or installed runtimes. They remain at
the checkpoint's local paths. They are unnecessary for the fresh-clone native
example, which compiles directly from two source files.

## Audit saved evidence

```powershell
python -B tools/literature/seurat_preprocessing/verify.py --out build/seurat-evidence-audit-new
```

For the optional full physical JAR and R-library checks on this workstation:

```powershell
python -B tools/literature/seurat_preprocessing/verify.py --out build/seurat-physical-audit-new --parent-jar build/metal-1/adapter-evidence-4/readers/omicsmatic-0.4.0.jar --candidate-jar build/seurat-high-impact-1/build-1/jar/omicsmatic-0.4.0.jar --runtime-manifest build/seurat-high-impact-1/probe-2/R-LIBRARIES-BEFORE.json
```

Every output path must be new. The audit is read-only apart from its new
receipts. It checks source/integrity relationships, not producer authenticity,
an independent statistical refit, biological validity or whole-paper
reproduction. It does not run R or repeat Java tests. Original R controls were
Windows-only; Linux qualification concerns the native Java code and examples.

**SKIP3:** supply the named permitted audit prerequisite or leave it skipped.
**FAIL2:** no complete qualification; retain evidence, read `FAILURE.json`,
correct the cause and retry a new path. For native numerical refusals,
`matrix()` throws; diagnostic getters retain the source values. Do not repair
NaN/Inf, erase coordinates or silently remove populations to make results pass.

The [first audit attempt](../seurat-preprocessing-development/README.md) is
retained unchanged. It miscounted scalar JSON strings as several cells and
returned FAIL2. This version fixes the counter; native code, references and
method bytecode are unchanged. No old fingerprint was refreshed.
