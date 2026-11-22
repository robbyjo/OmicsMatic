# Classic cell-type gene-property example

This example runs original MAGMA 1.10 fits and native Java FUMA cell-type
postprocessing on synthetic data: 201 tested genes, two datasets and three cell
properties per dataset. The sealed mean-model gene results, supplied `Average`
values and complete original R reference tables are included as numerical data.
The archive contains no upstream executable or source and no biological dataset.

Use Python 3 and Java 17 or newer. **Fitting currently requires Windows x86_64
and an admitted private original MAGMA 1.10 home.** Linux fitting is skipped with
exit 3. The portable Java interpretation API has separate Linux validation.
The current scoped development JAR is
`build/literature-review/fuma-celltype-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`992379fafaf8dc786073a8fd947e002e33b586162cacd1384f2718dee03b87ff`.
It is a local development artifact; no release or deployment is implied.

Run these commands from the repository root. Choose new preparation/output
directories for every attempt. Install only where the operating environment and
upstream terms permit it. The installer verifies and admits the private runtime;
the analysis command never installs software.

```powershell
python tools/literature/install_magma.py --allow-download --environment build/omics-magma
python examples/fuma-celltype-workflow/prepare_inputs.py --out build/fuma-celltype-example-NEW --home build/omics-magma
java -jar build/literature-review/fuma-celltype-candidate-1/jlinalg-0.3.7.jar fuma-tools --celltype-options-file build/fuma-celltype-example-NEW/cross.json
```

If `build/omics-magma` is already admitted, reuse it and omit installation. If
installation is unavailable or forbidden, **skip this fitting analysis**. Supply
a permitted admitted home on a supported Windows host before rerunning; do not
interpret a skip or partial output as an analysis result.

The preparer checks the archive and all eleven member SHA-256 values before
writing files. It creates four explicit recipes, each with its own new output:

| Recipe | Selected stages | Original MAGMA calls |
| --- | --- | --- |
| `marginal.json` | Marginal models only | 2 |
| `within.json` | Marginal plus within-dataset conditional models | 4 |
| `cross.json` | Marginal, within and cross-dataset models | 6 |
| `parent.json` | Same full analysis through typed parent `cell_type` | 6 |

Pass any of the first three to `--celltype-options-file`. Pass `parent.json` to
`--workflow-options-file`. All choices use explicit `BH`, `error` coverage and
`error` missingness policies; these are example choices, not inferred historical
paper defaults. The copied inputs and `source-provenance.json` describe the
captured original workflow and its transport limitations.

To execute and audit all four recipes against every original reference table
row and field, use a new work directory:

```powershell
python tools/literature/verify_fuma_celltype_workflow.py --jar build/literature-review/fuma-celltype-candidate-1/jlinalg-0.3.7.jar --home build/omics-magma --work build/fuma-celltype-verification-NEW
```

**Read `WARNINGS.md`, `analysis-status.json` and `session.json` in every output.**
Exit 0 means completed with review warnings; 3 means a dependency or supported
host is unavailable; 2 means failure. The verifier preserves these skip semantics
and writes `verification.json`. Failed or skipped child tables are quarantined
under `partial/`; they are diagnostic only. Correct the named cause and choose a
new output. Review raw calls, effective gene counts, coverage and join ledgers
before any scientific interpretation.

For real inputs, exact gene namespaces, genome build, resource releases and
precomputed `Average` are the investigator's responsibility. See the
[input and behavior guide](../../docs/literature/omics-2023-2026/FUMA_CELLTYPE_WORKFLOWS.md)
and [validation record](../../docs/literature/omics-2023-2026/fuma-celltype-validation.json).
Broader FUMA remains unfinished; Cell Ranger remains excluded.
