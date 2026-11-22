# MAGMA supplied tissue models and hidden conditioning

The development `magma-tools` adapter now transports explicit `--condition-hide`
to original MAGMA 1.10 for gene-set/property models. A supplied `Average` variable
stays in each fitted model, with its coefficient omitted by upstream. Every
reported coefficient is retained and validated; unexpected hidden rows fail.
Ordinary `--condition` behavior is preserved, including its reported covariate
coefficients. Those rows are not additional independent tissue hypotheses.

**DEPENDENCY UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THE ANALYSIS (exit 3).**
Fitting currently requires Windows x86_64, Java 17+ and a privately admitted,
unmodified MAGMA 1.10 home. Required action: read the named cause in stderr and
`WARNINGS.md`; use the permitted private installer or provide that home on a
supported permitted host, then rerun to a NEW output directory. The current
adapter has no Linux fitting profile. Analysis never downloads or installs.
If installation is forbidden or unavailable, keep the dependent analysis skipped.

**FAILED INPUTS OR MODELS (exit 2) ARE NOT RESULTS.** Required action: inspect
`analysis-status.json`, native logs and `partial/`, correct the exact cause and
rerun to a NEW directory. Unknown/duplicate conditioning names, conflicting
ordinary/hidden roles, explicit tests of hidden variables and no remaining
targets are rejected. Genuine collinear models can produce upstream NA fits;
existing finite-output admission rejects these while retaining diagnostic raw
tables, rather than publishing invented successful fits.

Use the [runnable example](../../../examples/fuma-tissue-workflow/README.md) and
[validation record](fuma-tissue-validation.json). The scoped candidate is
`build/literature-review/fuma-tissue-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`b69bdbc6465b425b72055c1ece562e3554985b8e33a289269f02317a5fd5ce8a`.
This is a local development artifact, not a published release or deployment.

## Inputs and choices

Provide a hash-sealed `magma-tools` gene-analysis bundle from the intended
genome-wide analysis, an explicit matching build, and a whitespace-delimited
property matrix with a `GENE` header, unique gene identifiers, numeric tissue
columns and the named `Average` column. Declare the same gene namespace as the
gene bundle. No ID conversion, expression normalization, tissue aggregation,
Average calculation, orthologue conversion or resource download occurs here.
The supplied matrix is staged byte for byte in `input.properties.tsv`.

```powershell
java -jar $jar magma-tools --method properties --build GRCh38 --home build/omics-magma --gene-bundle my-gene-bundle --properties my-tissue-properties.tsv --property-direction positive --condition-hide Average --missing-values error --max-missing 0 --seed 42 --out build/my-tissue-fit-NEW
```

`$jar` must point to the validated current development JAR above. Refer to
[MAGMA workflows](MAGMA_WORKFLOWS.md) for gene analysis, coordinates, runtime
admission and upstream terms. The adapter does not silently fit only the genes
selected by another FUMA stage. Review the intended competitive background and
the actual `NGENES` from each fit.

Select missingness explicitly: `error` rejects missing properties, while `mean`
or `drop` transports the declared original MAGMA policy and `--max-missing` limit.
Inspect native logs and effective denominators. In the pinned synthetic NA
example, explicit mean gives 200 or 201 genes by target, and drop gives 198.
No denominator is silently repaired. Technical corrections and original model
restrictions remain upstream. Use `--analyse` and joint/ordinary conditioning
only with a justified explicit target/model population.

## Raw P and correction families

Direct `results.tsv` contains original raw P values. `--correct all|none`
controls technical covariates; it does not select Bonferroni, BH or another
multiple-testing correction. Declare the family across tissues, resources,
traits and analyses before interpreting significance.

The runnable example also reuses `fuma-tools --celltype-options-file` for a
supplied tissue-only marginal workflow, with `step2=false`, `step3=false` and
explicit `correction=BH`. Its output column name `Cell_type` holds tissue labels.
`step1.tsv` reports raw P, `P.adj.pds` across each supplied dataset and `P.adj`
across their pooled complete population. Synthetic dataset sizes are 54 and 30;
the pooled size is 84. All tests are retained before adjustment. Independent
R `stats::p.adjust(method="BH")` references validate both choices. The example
does not decide which family matches a particular paper's use of “FDR”.

## Source scope and evidence

The [official FUMA tutorial](https://fuma.ctglab.nl/tutorial) describes positive
tissue-property models conditioned on average expression. The pinned FUMA
driver at commit `bdfbf06b5dd24037ec3c7497d773fad3c2656150` uses the legacy
direction spelling. Fresh calls to unmodified MAGMA 1.10 verified equivalent
tested positive-direction spellings. Output-only `abbreviate=0` expands three
long source labels through `FULL_NAME`; numeric results are unchanged.

Validation includes 1,215 focused tests in 90 suites, strict Java 17 compilation,
clean Javadoc and all 1,215 exact-JAR tests on Windows Java 21 and 25. Thirteen
original protocols cover 54/30 populations, supplied label/row order, multiple
hidden controls, ordinary plus hidden controls, explicit targets, missingness,
no targets and collinear failure. Each Windows profile matches ten complete
original populations: 109 coefficient rows, 769 fields, 551 numeric fields, with
zero observed numeric difference. Two input failures and one collinear-output
restriction remain distinct. Public recipes additionally check the optional
84-test Java BH workflow against complete R references. Read the validation JSON
for actual public runs, skips and retained verifier failures.

Historical GTEx versions, expression preprocessing and exact resources must be
reconstructed separately for each reviewed paper. Synthetic labels and counts
do not certify a biological GTEx resource or complete published pipeline. This
adds a workflow to the already counted MAGMA integration; broader FUMA remains
missing in the tool inventory. Cell Ranger remains excluded.
