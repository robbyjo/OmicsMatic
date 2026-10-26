# Original genetic PCA with explicit sample roles

**Development command; released 0.3.7 does not contain this workflow.**
Use the candidate identified by the retained packaged validation report.
`plink-tools --task pca` runs the pinned original program on resolved biallelic
autosomal BED hard calls. Choose the algorithm, missing-data mode, marker panel,
component count and independent fitting/frequency samples explicitly.

For the separate bounded native Java transform and new-sample projection, see
[native genetic PCA](../genetic-pca/README.md). This original-program command
reports fitted coordinates and original weights; projection is a separate workflow.

## Setup and runnable recipes

The original runtime profile is Windows x86_64. Where installation and downloads
are permitted, create a private verified installation:

```powershell
python tools/literature/install_plink.py --home build/omics-plink-official --allow-install --allow-download
$pcaJar = 'build/literature-review/plink-pca-candidate-1/jlinalg-0.3.7.jar'
java -jar $pcaJar plink-tools --options-file examples/plink-tools/pca/plink2-pca-founders-mean.json
java -jar $pcaJar plink-tools --options-file examples/plink-tools/pca/founders-seed17.json
```

Analysis itself never installs software. Existing verified homes need neither
permission flag. Preserve changed/unmanaged installations and select a new path.
If installation is forbidden or unavailable, skip this analysis and follow the
dependency warning; do not interpret an absent result.

All ten recipes use synthetic data with 128 source people, 125 fitted people,
87 explicitly selected markers, and either 125 people or 123 recorded founders
in the frequency reference. They contain no patient data or biological ancestry
labels. The `founders` filename describes an explicitly prepared sample mask;
the command does not select founders automatically. The two selected nonfounders
remain fitted samples in those examples. Use a new `--out` directory for each run.

## Inputs and modes

The sample TSV must contain every exact FAM FID/IID pair once, with this header:

```text
fid	iid	fit	frequency_reference
```

The two role columns independently contain `yes` or `no`. A frequency-reference
person can be outside the fitted basis. Samples with neither role contribute to
neither analysis. FAM phenotypes are ignored; exact identifiers map to recorded
ASCII surrogates for the original process. `sample-reference.tsv` maps them back.
Founder/pedigree metadata alone do not certify unrelatedness.

`--variants` names a UTF-8 file with one exact unique original marker ID per line.
The command preserves BIM order and does not infer QC, LD pruning or a genome
assembly. Select a reviewed panel and basis beforehand. Admission checks the
whole BED-set structure and metadata; submitted markers must have resolved
distinct alleles and lie on human autosomes. The declared reference-call minimum
and `--missing-genotypes error|upstream` apply to the fitted/reference populations.
No invalid submitted marker is silently dropped.

| Engine | Algorithm | `--pca-missing` | Additional requirement |
|---|---|---|---|
| PLINK1 | exact | pairwise | No seed or mean-GRM tolerance |
| PLINK2 | exact | pairwise | No seed or mean-GRM tolerance |
| PLINK2 | exact | mean | Explicit relative eigen-residual tolerance |
| PLINK2 | approx | mean | Explicit positive integer seed and relative eigen-residual tolerance |

For example, mean modes require
`--maximum-relative-eigen-residual 0.01`. **The example's tolerance is a synthetic
test setting, not a prescribed threshold for a paper or real cohort.** The command
does not infer unstated paper parameters or retry with another seed/algorithm.
Randomized PCA always mean-imputes; this is an upstream contract documented in
the [official PCA guide](https://www.cog-genomics.org/plink/2.0/strat).

The original program estimates allele counts in a separate process using only the
declared frequency-reference mask. The wrapper verifies each allele/count against
those exact hard calls, then loads the count file for PCA. It retains the default
[GRM low-MAF guard](https://www.cog-genomics.org/plink/2.0/distance#make_rel).
It never adds `--bad-freqs`, `--grm-maf ... yes-really`, or an inferred filter to
make a run succeed. A synthetic reference with MAF 0.004 is rejected at the
loaded-frequency default guard of 0.005.

## Quality, resource limits and interpretation

All modes validate positive requested eigenvalues, exact fitted identities,
complete original weight families and sample-vector orthogonality within the
original six-significant-digit output precision. They preserve original signs
and decimal strings. A component-boundary gap that the original report does not
contain is not inferred.

Mean modes additionally stream `G*v` from the declared panel and allele
frequencies, without allocating an N-by-N relationship matrix. The diagnostic is
`norm(G*v-lambda*v)/(abs(lambda)*norm(v))`. Each component must meet the requested
maximum **as observed in the printed output**. Its decimal-uncertainty envelope
is reported separately and never widens that threshold. A Ritz residual measures
the eigen-equation error; it does not establish exact top-K subspace recovery or
biological interpretation. Inspect stability and study design separately.

Pairwise missingness can yield an indefinite GRM. That mode checks format,
identity and printed orthogonality; it does not use the incompatible streaming
mean-GRM residual or substitute SVD. Pairwise-fitted coordinates generally differ
from a linear mean-imputed projection of the same people.

Default limits admit up to 100,000 source samples, two million source variants,
500 million source genotype cells, 100 components, 512 MiB retained metadata,
and two billion validation products. Independent ceilings apply together;
their simultaneous maxima are not admissible. The mean-GRM pass admits
`2*M*N*K` work products and O(N*K) additional storage. Increase explicit reviewed
limits when appropriate, or reduce the panel/basis. Components can be raised
with `--maximum-components` up to 1,000. The primary randomized allocation
estimate follows the official guide; exact mode checks the primary N-by-N matrix
minimum. `--memory-mib` is upstream main workspace, **not a strict process RSS
limit**; solver, native-library and OS overhead need additional memory.

## Outputs, warnings and required actions

**Read `analysis-status.json`, `WARNINGS.md` and `session.yaml` before using results.**

| Exit | Meaning | Required action |
|---|---|---|
| 0 | Passed with review warnings | Review sample/panel choices, original logs, quality diagnostics and interpretation. |
| 2 | Input, upstream process, resource, report or accuracy failure | Correct the named cause and rerun to a new directory. Review rare variants through an explicit QC/panel decision. Do not silently loosen accuracy requirements. |
| 3 | Original dependency unavailable, changed, unsupported or forbidden | Where permitted, use the installer above and select the sealed home; otherwise skip the dependent analysis. |

`eigenvectors.tsv` contains only fitted original FID/IID pairs. `eigenvalues.tsv`
contains the requested positive reported eigenvalues. `variant-weights.tsv`
retains actual original allele rows, counted alleles and the declared allele-order
convention: PLINK1 A1/A2 or PLINK2 REF/ALT. **These coefficients are not native
`GeneticPrincipalComponents` projection coefficients.** Preserve engine, allele
columns, signs and scale together. No original-weight conversion is inferred.

`sample-reference.tsv`, `variant-reference.tsv`, and (for mean modes)
`eigen-quality.tsv` retain selection/count/quality diagnostics. `upstream/`
retains commands, raw reports and logs. Recipes, source/staged input hashes,
original executable identity, seed and resource choices are recorded. Failed
canonical outputs are quarantined under `partial/`; diagnostic and upstream
files remain available. Process success alone is insufficient for publication.

This is an original PCA workflow for a declared hard-call subset. It does not
reproduce every historical paper version, certify unrelatedness/ancestry,
automatically remove outliers, or implement every PLINK PCA/projection option.

## Validation evidence

The development candidate passed 165 focused tests in 18 required suites without
failures or skips, plus Java 17 strict compilation, packaging and configured
Javadoc generation. The packaged JAR passed all ten public recipes on Java 21/25,
two additional Unicode workflows and 24 controls. Independent known-call checks
covered 2,750 fitted rows, 1,914 frequency rows, 17,400 allele-weight fields,
110 eigenvalues and 13,750 coordinate fields. Accuracy rejection, upstream low-MAF
failure and unavailable-runtime skips retained prominent required-action messages.
These are synthetic workflow validations; no large production cohort was run.

The exact archive comparison retained 16,126 unchanged shared entries from the
previous native-PCA candidate. The public dispatcher changed and 17 PCA class
entries were added. The broader full configured test suite was not rerun on this
candidate; the retained earlier full gate belongs to its recorded prior candidate.
