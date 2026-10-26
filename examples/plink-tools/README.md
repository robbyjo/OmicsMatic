# Original PLINK additive association workflow

For the separate explicit autosomal genotype QC workflow, see [QC.md](QC.md).
For original unphased hard-call LD pruning, see [PRUNING.md](PRUNING.md).
For original exact/randomized genetic PCA, see [PCA.md](PCA.md).
For the native bounded genetic GRM/PCA/projection API, see [genetic PCA](../genetic-pca/README.md).

**Development feature; the released 0.3.7 JAR does not contain this command.**
Use the development candidate identified by the retained packaged verification
report. This example is synthetic: 128 samples and 96 variants, with repeated
IIDs in distinct families. It contains no patient data. Its arbitrary coordinates
do not establish a genome assembly.

`plink-tools --task glm` runs the pinned original PLINK program. Choose
`--engine plink1` or `plink2`; `linear` and ordinary `logistic` are available in
both profiles. `firth` and `firth-fallback` require `plink2`. Only additive,
diploid human autosomal hard-call BED/BIM/FAM inputs and resolved uppercase DNA
alleles of 1–1,000 bases are admitted. This command does not expose every PLINK
operation or reproduce every paper's version and analysis choices.

The current original runtime profile is Windows x86_64. Install into a private
directory only where installation and downloads are permitted:

```powershell
python tools/literature/install_plink.py --home build/omics-plink-official --allow-install --allow-download
```

`--allow-install` and `--allow-download` grant separate permissions. Existing
verified installations can be reused without either flag or a network request.
An existing changed or unmanaged home is preserved; select a new private path.
The installer records the original versions, archive and executable hashes, and
GPL license. Original executables are not included in the source repository.
Version checks alone do not validate scientific results.

After selecting the verified development JAR, run from the repository root:

```powershell
$plinkJar = 'build/literature-review/plink-ci-candidate-1/jlinalg-0.3.7.jar'
java -jar $plinkJar plink-tools --options-file examples/plink-tools/linear-options.json
java -jar $plinkJar plink-tools --options-file examples/plink-tools/logistic-options.json
java -jar $plinkJar plink-tools --options-file examples/plink-tools/confidence-options.json
```

Use a new `--out` directory outside the sealed runtime home for each attempt.
Overlapping paths are rejected before creating output or runtime directories.
Direct options override recipe
values. Recipes contain string-valued options and must be UTF-8 JSON; unknown,
duplicate or recursive options fail. For Windows paths containing Unicode, put
the paths in a UTF-8 recipe with an ASCII filename so that Java's native launcher
argument conversion cannot alter them. Paths resolve against the calling
directory. The recipe, effective options and input hashes are retained.

## Cohort and model choices

The cohort header is exactly `fid`, `iid`, `phenotype`, followed by the declared
covariates in order, separated by tabs. Include every FAM pair exactly once.
FID/IID pairs are matched as two fields, with no concatenation or IID-only join.
Other cohort values must be finite numeric values or the exact token `NA`.
`-9` is a numeric value here. The workflow selects an unused missing-value code
accepted by the original engine; it never silently treats a valid `-9` as missing.

Declare `--covariates AGE,SEX` or explicitly choose `none`. Categorical covariates
must be encoded by the analyst before this workflow. `SEX` in the example is an
explicit numeric covariate, independent of the recorded FAM sex. Autosomal sex
adjustment, population structure adjustment, relatedness adjustment, QC filtering,
imputation and multiplicity correction are not inferred.

Binary models require `--binary-coding 0/1` or `1/2`. The example uses 0/1; the
workflow explicitly maps this to the original 1=control, 2=case convention.
FAM phenotype values are ignored. Linear traits whose complete-case values
contain only 0/1/2 are rejected because the original loaders infer binary types
from those codes. Do not transform a phenotype merely to evade that check.

Choose `--missing error` to prohibit exclusions, or `exclude` to remove rows
missing phenotype or declared covariates. Review every exclusion in
`sample-ledger.tsv`. Original genotype missingness is then handled per variant,
without imputation; reportable rows must have the expected complete-case
nonmissing-genotype count. Resources have explicit sample, variant, cell,
metadata, line, BED, cohort and report bounds; review the named limit before
increasing it.

## Results, warnings and recovery

**Always read console warnings and `WARNINGS.md` before using results.**

| Exit | Meaning | Required action |
|---|---|---|
| 0 | Completed; scientific warnings may still be present | Review status flags, cohort exclusions, genome-build declaration and upstream warnings. |
| 2 | Input, model, original-process, scientific or output-publication failure | Read the named cause and retained logs; correct it and rerun to a new directory. Failed canonical streams are quarantined under `partial/`. |
| 3 | Original dependency unavailable, changed, unsupported or forbidden | Where permitted, run the installer above and supply the sealed home. Otherwise skip this analysis; do not interpret an absent result. |

`results.tsv` maps every tested variant to its original ID and retains the actual
effect and other alleles. PLINK2 may test BIM A2. Binary `beta` is the logarithm
of the **reported rounded OR**, rather than an unrounded regression coefficient.
The reader never reconstructs an absent standard error from rounded columns.
Original `ERRCODE` or missing estimates make canonical numeric fields blank,
even when PLINK prints finite provisional Firth estimates and exits successfully.
If every association is unreportable, the analysis fails and quarantines results.

A printed P=0 has unresolved P-value precision; it receives no invented floor.
A positive decimal P below Java's `double` range retains `negative_log10_p` while
`p` is blank. Use that log field for comparisons. Original textual values remain
in `upstream/`; ASCII surrogate IDs map through `variant-metadata.tsv` and
`sample-ledger.tsv`. Original parent/founder and sex fields are preserved in the
staged pedigree. Genetic distance is staged as zero because it does not enter
this GLM; the original BIM file is separately hashed.

`manifest.yaml` hashes the input and output files. `session.yaml` records the
original runtime identity, typed commands, child-process IDs, exit codes,
staged-input hashes, cohort counts and reportability counts. Inputs and staged
copies are rechecked for changes. These records establish the local invocation
and validation scope, not equivalence to an entire paper or all PLINK releases.

## Reported standard errors and confidence intervals

`--confidence-level LEVEL` accepts the original programs' range `0.01 <= LEVEL < 1`
and requests their reported standard errors and confidence limits. The confidence
example selects PLINK1 and 0.955. PLINK1 supplies SE when this reporting option is
selected; without it, absent SE remains blank with an actionable warning.
For both programs, `standard_error` is the coefficient SE: log-odds SE for binary
models. Binary `ci_lower`/`ci_upper` remain on the **odds-ratio scale**, while linear
limits remain on the beta scale. `ci_scale` names that distinction.

`confidence_level_requested` retains the invocation choice. PLINK1 truncates
percentage labels: a request of 0.955 still prints `L95/U95`. PLINK2 prints more
digits, but sufficiently high levels can display `L100/U100` even though the
requested level is below 1. The reader checks the selected engine's label format;
it cannot recover or prove an invocation level from a rounded label. Use retained
command provenance, including when importing an existing report through the
[Java API example](../plink-association-import/ConfidenceImportExample.java).

The original programs use a normal critical value for these approximate limits,
including linear models whose reported P-values use a Student t distribution.
The workflow imports the original bounds and never recalculates them from rounded
beta/SE columns. CI fields are blank with `NOT_REQUESTED` when no level is selected.
Upstream error or missing effects suppress the interval as `UNREPORTABLE_ESTIMATE`.
Unavailable, nonfinite, reversed or unresolved interval bounds become
`UNREPORTABLE_BOUNDS`, with a prominent warning and required recovery action;
core effect and SE reportability are assessed separately. Do not treat blank
interval bounds as zero or substitute a different level silently.

These conventions are documented by the original
[PLINK1 report format](https://www.cog-genomics.org/plink/1.9/formats#assoc_linear)
and [PLINK2 report format](https://www.cog-genomics.org/plink/2.0/formats#glm_linear).
The normal critical value and header formatting were also checked against the
original [PLINK1 GLM source](https://github.com/chrchang/plink-ng/blob/master/1.9/plink_glm.c)
and [PLINK2 linear GLM source](https://github.com/chrchang/plink-ng/blob/master/2.0/plink2_glm_linear.cc),
alongside actual pinned-program output and independent ordinary-regression checks.

The input fixture and its source hashes are described in `provenance.json`.
Original semantics: [PLINK1 association](https://www.cog-genomics.org/plink/1.9/assoc),
[PLINK2 association](https://www.cog-genomics.org/plink/2.0/assoc),
[PLINK2 input conventions](https://www.cog-genomics.org/plink/2.0/input).
