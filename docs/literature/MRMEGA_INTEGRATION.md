# MR-MEGA integration

**MR-MEGA is available through the project's `mrmega-tools` command.** This
checkpoint brings the catalog to 92 new scoped integrations: 18 native Java
methods and 74 upstream adapters, or 95 including three existing adapters.
There are 249 named tools still missing. Cell Ranger remains excluded by the
user. The full 250-paper implementation goal remains unfinished.

This native workflow supports quantitative/binary cohort files, exact/swapped
allele alignment, original float32 preparation, inferred or supplied ancestry,
MR-MEGA0.2 regression, covariance and heterogeneity statistics, lnBF, and
optional input/output genomic control. It uses explicitly named stable native
probabilities. No original MR-MEGA, compiler, R or software installation is
needed for native analysis.

The [workflow guide](MRMEGA_WORKFLOW_DEVELOPMENT.md) contains the full settings
JSON, required columns, cohort-list examples, output definitions, restrictions
and recovery instructions. Its opening statement that main-command/catalog
registration was pending describes the earlier frozen development checkpoint;
this integration completes that registration without changing its tested
scientific classes or historical evidence.

## Run through the project command

Prepare the settings JSON and cohort list using the workflow guide. From the
repository root, Windows PowerShell can run:

```powershell
java -cp "docs/literature/mrmega-catalog-development/mrmega-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar" org.jlinalg.cli.JLinAlgCli mrmega-tools --settings mrmega-settings.json --file-list cohorts.in --out D:/analysis/mrmega-run1
```

On Linux, use a colon in the classpath and Linux input/output paths. The public
root supplement includes all native MR-MEGA classes and the exact predecessor
supplement classes needed by the current main dispatcher. It needs the separately
recorded project runtime. Both compiler artifacts target Java17; actual execution
was tested on Windows JVM21/JVM25 and Linux JVM21. The canonical JAR is unchanged;
this is source integration and a tested development supplement, not a release.

`mrmega-tools --help` describes the entry point. The main help also lists it.
Use a NEW output directory with an existing parent. **Require exit zero,
`run.json` status `PASS_NATIVE_MRMEGA_WITH_WARNINGS`, and an admitted
`results.tsv`.** Read `WARNINGS.md`, the row/variant ledgers and source pins
before interpretation.

## Warnings and required actions

**FAIL2 admits no result table.** Failed files remain under `partial/`. Correct
the named input/settings/numeric/rank/capacity error and retry NEW paths.
Existing results stay intact. SmallCohortCount variants remain explicit `NA`
rows; do not reinterpret them as nonsignificant fits.

Source-compatible quirks have scientific consequences. The odd-count lambda
median uses `floor(n/2)-1`; duplicates overwrite and can contribute multiple
lambda rows. Float32 storage/reciprocal boundaries are retained. Missing filter
columns cause that cohort's filter to be skipped, with a warning. Binary SE
conversion uses the lower OR confidence bound, with the required upper bound
unused. Input GC applies only above one; output GC applies even below one and
changes association but leaves lnBF, coefficients and heterogeneity unchanged.

**Supply actual consistent allele columns.** With `no_alleles=true`, source0.2
keeps a new marker's beta then negates later occurrences. This behavior is
preserved and warned about; it is not silently changed into a different method.
No strand complement, build conversion, variant-ID repair or paper-setting
inference is performed. Literal `N`, observed ancestry-bin300 aliasing and
unresolved/tied ancestry cutoffs require the restrictions in the workflow guide.

Native stable survival probabilities are separately named. Original approximation
outputs and extreme-tail/zero-test discrepancies remain archived and unmodified.
No locus posterior/credible-set construction, CrossMap preprocessing, METAL or
PLINK meta-analysis, SuSiEx fine-mapping, or whole-paper reproduction is implied.

Original-reference regeneration requires its separate permitted Linux compiler
and R prerequisites. If unavailable or forbidden, skip that dependent reference
validation and follow the retained receipt's named recovery action. This adds
no upstream runtime dependency to native user analysis. Cell Ranger remains
excluded; no installation action is required for it.

## Paper uses and validation

Three primary-source reviews distinguish these roles:

- [P072](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786718/): MR-MEGA0.2 with
  three axes, variants present in at least six of seven cohorts, no input GC,
  and downstream use of lnBF for locus posterior/credible-set construction.
  The latter construction and stated exclusions remain separate from regression.
- [P105](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703676/): MR-MEGA0.2 with
  four MDS axes and a separate chi-square-based small-P recalculation. Exact
  cohort inputs, GC flags and recalculation code are unstated.
- [P162](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447099/): ancestry-related
  index-variant heterogeneity, with six significant findings. Edition, axes and
  complete tested population are unstated. The adjacent SuSiEx analysis is separate.

The corrected counts above replace two errors in unpublished draft reviews.
The original drafts stay frozen as history and were never applied to the catalog.
All 1,009 prior catalog reviews remain exact; three new reviews give 1,012 roles
and 355 supporting pointers across the unchanged 250-paper cohort.

The standalone workflow passed 132 complete original-file comparisons, including
4,134 full-precision fits, 12,402 independent R probability fields, 144 real CLI
refusals and six analytic controls. Main-command tests execute all 132 native
workflows again and require identical tables/settings/warnings and equivalent
run receipts. Another 66 dispatcher checks cover help/refusal behavior and eight
predecessor routes. All selected predecessor scientific classes and native
MR-MEGA classes remain byte-identical; only the main dispatch class changes.

Frozen verification checks source bindings, archived populations, numeric/output
relationships and catalog consistency. It does not execute scientific software,
refit current user data, authenticate past runs or certify biological accuracy.
