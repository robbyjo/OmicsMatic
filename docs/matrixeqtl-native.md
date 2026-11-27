# Native Matrix eQTL additive associations

`matrixeqtl-native` implements the additive `modelLINEAR` behavior of **MatrixEQTL
2.3** in FP64 Java. R is used only for independent development validation. The
native command requires no R installation. Build from current source; an older
released JAR does not contain this command.

**Warnings and required actions:** use a result only when `analysis-status.json`
has `result_admitted: true`. Read `WARNINGS.md`. **SKIP3** skips dependent analysis
when execution is forbidden or a declared prerequisite is unavailable. Supply
the named permitted prerequisite and retry a **new output directory**. **FAIL2**
admits no partial batch: fix the named input, integrity, numerical, capacity or
cancellation problem and retry a new directory. Preserve failed attempts.

```powershell
java -jar omicsmatic-0.4.1.jar matrixeqtl-native --request request.json
java -jar omicsmatic-0.4.1.jar matrixeqtl-native --verify capture --out replay
```

The executable is produced by Gradle's `executableJar` task. The full synthetic
example is [examples/matrixeqtl23](../examples/matrixeqtl23/request.json). Copy
that directory to a writable location; the output parent must exist. Relative
paths resolve against the request file's parent. All request keys are required;
optional file inputs use JSON `null`.

```json
{
  "schema_version": 1,
  "source_version": "2.3",
  "model": "modelLINEAR",
  "snps": "snps.tsv",
  "genes": "genes.tsv",
  "covariates": null,
  "snp_positions": "snp_positions.tsv",
  "gene_positions": "gene_positions.tsv",
  "error_covariance": null,
  "threshold": 0.00001,
  "cis_threshold": 0.001,
  "cis_distance": 1000000,
  "block_size": 256,
  "max_input_bytes": 100000000,
  "max_entries": 10000000,
  "max_pairs": 100000000,
  "max_retained": 100000,
  "max_seconds": 3600,
  "execution_policy": "ALLOW",
  "population": "Declare the exact analyzed sample/stratum population",
  "preparation": "Declare normalization, residualization, selection and covariate encoding",
  "allele_orientation": "Declare the counted effect allele of supplied dosages",
  "out": "capture"
}
```

Matrix TSVs have header `id` followed by unique sample IDs. Rows are uniquely
named SNPs, genes/CpGs or numeric covariates. Sample IDs and order must match
exactly. Encode categorical covariates explicitly without a redundant intercept;
the kernel adds one. Covariance TSV rows and columns must both name the same
ordered samples. It must be finite, exactly symmetric and positive definite.
Position TSVs have header `id chr start end` separated by tabs, in matrix row
order; SNP start/end must agree. Positive integer coordinates are required.
Chromosome strings are compared literally. Reversed gene endpoints are supported.

`threshold` controls all tests when `cis_threshold` is zero, otherwise trans
tests. Zero disables that output family. At least one threshold must be positive;
when both are positive, the trans threshold cannot exceed the cis threshold.
Cis membership includes both endpoints of the gene interval expanded by
`cis_distance`. FDR denominators count all tested pairs in each family, including
constant rows, rather than only retained hits. Minimum p-values cover the tested
population; a feature without eligible pairs has the original value one.

The source imputes `NA`/`NaN` cells to the row mean and all-missing rows to zero.
The report counts imputations and zero residual rows. Infinite input is refused.
Source zero-row handling, infinite t statistics and the minimum-normal positive
p-value floor remain explicit. `derived_SE` is an extension independently checked
against R regression; it is `NA` when the SNP or response is unidentifiable. The
standard MatrixEQTL result table does not itself emit this SE column.

Captured inputs, parameters, defining class hashes and all results are retained.
`--verify` checks the complete member population and recomputes all result fields
from captured inputs. These consistency checks do not authenticate a producer or
prevent someone from fabricating an entirely new bundle. Input files may be moved
after capture. Keep the producing JAR for later verification. Matrices remain
resident in bounded heap memory; temporary correlation products are tiled. This
profile is not a streaming five-million-variant deployment. Increase declared
capacities and heap only after checking the complete workload.

The [four primary-source roles](literature/matrixeqtl-source-audit/ROLES.json)
remain distinct:

| Paper | Matrix eQTL use | Separate preparation or downstream work |
| --- | --- | --- |
| P169, Nature Genetics, 2.3 | Colon cis expression QTLs | PEER/covariate preparation, locus/MAF filtering, cohort meta-analysis and SMR |
| P185, AJHG, 2.3 | Methylation QTLs within 500 kb with genotype/methylation PCs, sex and batch | PC estimation, factor encoding and genomic annotation |
| P213, AJHG, version unreported | Sex/cell-type stratum coefficients and SE | Chromosome testing families, lead selection, Z comparisons and consistency filtering; the published pi0 threshold is unresolved |
| P219, AJHG, 2.3 | Skin methylation cis/trans associations, per-feature minimum p-values | Twenty twin-pair/zygosity constrained genotype permutations and empirical permutation FDR |

**Package BH does not implement P219's permutation FDR.** The paper's strict
distance descriptions leave the exact 1 Mb boundary unspecified; this kernel
retains the package's inclusive boundary. Run each sex/cell/cohort or explicitly
prepared permutation as its own declared input population. Do not substitute an
interaction model for separate stratum fits. No paper cohort, author recipe,
permutation threshold, ANOVA, LINEAR_CROSS, direct-memory saver or graphical
histogram routine is qualified by this additive profile. P213's software version
remains unknown. Current CRAN versions are not substituted for the three explicit
2.3 uses. See the [upstream notice](../LICENSES/MatrixEQTL-NOTICE.md).
