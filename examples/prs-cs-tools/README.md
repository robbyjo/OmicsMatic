# PRS-CS synthetic command examples

**The validated development command passed218 focused tests,12 packaged
workflows and28 controls on Java21/25.** Use the candidate and SHA-256 in the
[workflow guide](../../docs/literature/omics-2023-2026/PRSCS_WORKFLOWS.md).
The older canonical executable lacks this command.

These examples contain synthetic data. Twenty variants enter the model from
25 target rows and 26 summary rows. The supplied two-chromosome LD matrices are
synthetic correlations. Sampling settings 40 iterations, 20 burn-in and thinning5
exercise the software; scientific work needs its own justified schedule and
assessment of chain stability and predictive performance.

Run from the repository root. Install an admitted private runtime where permitted,
set `JLINALG_PRSCS_HOME`, then supply a recipe and a new output directory:

```powershell
java -jar build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar prs-cs-tools `
  --options-file examples/prs-cs-tools/current-beta-se-auto-both.json `
  --out build/prscs-example-new-attempt
```

Read `WARNINGS.md`, `analysis-status.json`, `session.yaml` and the variant/input
ledgers before using any output. Missing, changed or forbidden dependencies skip
with exit3 and explain setup recovery. Input, process and numerical validation
failures return exit2. Analysis never installs software or downloads a panel.

| Recipe | Operation |
|---|---|
| `current-beta-se-auto-both.json` | Current source; auto shrinkage, standardized-genotype means and draws, psi, explicit per-allele mean-weight export. |
| `current-or-se-fixed-mean.json` | Current source; OR/SE(logOR), fixed phi, per-allele means and psi. |
| `current-beta-p-fixed-draws.json` | Current source; fixed phi, standardized retained draws, explicit original P floor, no psi. |
| `legacy110-or-p-auto-mean.json` | Explicit historical1.1.0 reproduction; auto shrinkage, per-allele means and psi. |
| `legacy100-beta-p-auto-mean.json` | Explicit historical1.0.0 reproduction; auto shrinkage, standardized means and explicit per-allele export. |
| `legacy100-or-p-fixed-mean.json` | Explicit historical1.0.0 reproduction; fixed phi and per-allele means. |

**Historical recipes explicitly opt into offline reproduction with Python3.10.11,
which is out of support.** Use a compatible separately pinned current-source
environment for new analyses. Preserve sealed environments and use a new home
when selecting additional profiles or repairing a changed installation.

The P-format fixtures intentionally include zero/extreme P values. Their recipes
explicitly permit the upstream1e-323 floor; raw input tokens remain retained.
BETA/SE or OR/SE with a compatible profile avoids reconstructing effect magnitude
from a floored P value. Effect frequencies in exports come from the supplied
synthetic LD panel and are not target-cohort estimates.

`provenance.json` records input and recipe hashes. The independently frozen
original results are in `src/test/resources/org/jlinalg/genetics/prscs-reference`.
