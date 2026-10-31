# Supplied MAGMA gene/set report example

This Java command adds gene symbols to supplied results and reproduces the
FUMA `magma_gene.R` gene-set report. **It does not fit MAGMA models.** Inputs
are synthetic; references are captured original R output and complete Holm
families. No upstream program, runtime or biological database is redistributed.

From the repository root, with Python 3 and Java 17 or newer:

```powershell
python examples/fuma-magma-report-workflow/prepare_inputs.py --out build/magma-report-demo
java -jar build/literature-review/fuma-magma-report-candidate-1/jlinalg-0.3.7.jar fuma-tools --magma-report-options-file build/magma-report-demo/minimal.json
java -jar build/literature-review/fuma-magma-report-candidate-1/jlinalg-0.3.7.jar fuma-tools --workflow-options-file build/magma-report-demo/parent.json
```

Each preparation/output directory must be new. The preparer verifies the archive
and every member SHA-256 before creating the directory. Paths inside its UTF-8
JSON recipes are relative to the carrier. Recipe values are strings.

| Recipe | Explicit behavior |
| --- | --- |
| `minimal.json` | Twelve sets; preferred GSA file, complete Holm family, ten displayed rows |
| `fallback.json` | The same result via `gsa=none` and a plain TSV fallback |
| `padded.json` | Three sets and seven source padding rows; explicit `source-rows` policy |
| `missing.json` | Eleven finite tests plus missing P; explicit missing-P and placeholder policies |
| `parent.json` | Independent native `magma_report` workflow stage |

Run all five recipes and compare every reference field:

```powershell
python tools/literature/verify_fuma_magma_report_workflow.py --jar build/literature-review/fuma-magma-report-candidate-1/jlinalg-0.3.7.jar --work build/magma-report-verification
```

Use `--java PATH_TO_JAVA` if needed. The verifier checks complete gene/report
tables and adjustment families, full-set input order, raw snapshots/hashes,
scientific byte counts and warnings. All five recipes passed on Windows Java
21/25 and Linux Java 21. The exact local development JAR hash is in
[the validation record](../../docs/literature/omics-2023-2026/fuma-magma-report-validation.json).
If that local candidate is absent, build the current executable JAR in the
validated checkout. A version label alone does not identify tested bytes.

**Interpretation requires action even after exit 0.** Read `WARNINGS.md`,
`session.json`, `analysis-status.json`, `full-tests.tsv`, `gene-joins.tsv` and
`display-rows.tsv`. `Pbon` is the source's label for **Holm**, not Bonferroni.
Missing P values are excluded from the source's default adjustment family.
Padded all-NA rows are report placeholders, not tested sets. The source report
does not establish a paper's BH-FDR or cross-trait correction choice.

**Unavailable or forbidden Java: verification skips dependent analysis, exit
3, with the cause and Required action.** Supply Java 17+ on a permitted host
and rerun to a new directory. These fixtures need no R or MAGMA installation.
If software needed to generate original model results cannot be installed or
used where permitted, skip that affected fitting analysis and supply valid
permitted results. Postprocessing never installs software or invents fits.

**Invalid inputs, capacities and runtime failures exit 2.** Inspect the named
cause and retained logs. Failed command tables/snapshots are quarantined under
`partial/` as diagnostics. Correct the cause and rerun to a new output; existing
outputs are preserved. The verifier retains `verification.json` and command
logs. Consult [the full reference](../../docs/literature/omics-2023-2026/FUMA_MAGMA_REPORT_WORKFLOWS.md)
before replacing synthetic data. Broader FUMA and the overall implementation
goal remain unfinished. Cell Ranger remains excluded.
