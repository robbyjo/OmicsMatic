# AllelicSeries 0.1.1.2 development workflow

**Warnings and required actions:** P204 literally reports version `1.1.2`; the selected unchanged CRAN archive declares `0.1.1.2`. Their exact build equivalence is **unproven**. Keep both strings. This is a development component undergoing qualification. It adds no main-catalog implementation credit automatically and does not establish paper reproduction.

The provider installs the complete original package and eight fixed original dependency sources in a NEW private library. It compares all 42 installed R function bodies/formals to their original source definitions and checks all 16 exported functions. It performs no paper analysis. No original source is patched. The native Java class separately implements the summary-statistic baseline and weighted-sum burden calculations, the checked ASBTSS wrapper, genomic control and positive-definiteness checks. Full original COAST and SKAT-O remain original R operations.

## Original package setup

Use Linux or permitted WSL, Python 3.11+, a complete R 4.5.3 prefix, and permitted C/C++/make tools. The selected reference base has the literal DESCRIPTION versions `glue=1.8.1`, `Rcpp=1.1.2` and `Matrix=1.7-6`. The complete locked base used by the existing FastMNN workflow is one tested source of that runtime; this provider does not alter that prefix or its package library. Missing or different prerequisites skip dependent analysis with **SKIP3**. Supply the documented permitted profile before retrying.

```bash
python3 tools/literature/allelicseries0112/provider.py \
  --r-prefix /path/to/permitted/R-4.5.3-prefix \
  --allow-download --allow-install \
  --out build/new-allelicseries-original
```

`--r-prefix` is the directory containing both `bin/Rscript` and `lib/R/library`. It is not the `lib/R` directory returned by `R.home()`. Use a NEW output path. To avoid downloading, replace `--allow-download` with `--source-cache /path/to/exact-archives`. The cache must contain `AllelicSeries_0.1.1.2.tar.gz` and every exact dependency filename listed in `source-profile.json`. The provider verifies each complete archive before installation. Its installation is private; it installs dependencies only into the new output's `library`.

`READY.json` means original namespace readiness, not a completed analysis. Compiling the unchanged C++ dependencies can take several minutes. Complete compiler warnings, failures and source archives are retained. A missing/forbidden compiler, archive, installation or runtime is **SKIP3**: skip the dependent analysis, correct the named prerequisite where permitted, and retry a NEW path. **FAIL2 admits no setup/result**; inspect `FAILURE.json`, `WARNINGS_FAILURE.md` and the named raw process logs. Preserve earlier attempts.

## Run original methods

The public runner accepts any of the 16 exact original function exports and a complete named R argument list saved as RDS. It does not change arguments, sort variants, align alleles or replace original defaults. For example, create a COASTSS request using the permitted Rscript, replacing the file paths below:

Supported exports: `ASBT`, `ASBTSS`, `ASKAT`, `ASKATSS`, `Aggregator`, `BaselineSS`, `COAST`, `COASTSS`, `CalcSumstats`, `Comparator`, `Counts`, `DGP`, `GenomicControl`, `OLS`, `SumCountSS` and `isPD`.

```r
ss <- read.delim("my-gene-sumstats.tsv")  # columns: anno, beta, se, maf
ld <- as.matrix(read.table("my-gene-ld.tsv", header = FALSE))
stopifnot(nrow(ld) == nrow(ss), ncol(ld) == nrow(ss))
# The caller must establish matching variant order and effect-allele orientation.
request <- list(anno = ss$anno, beta = ss$beta, se = ss$se,
                maf = ss$maf, ld = ld, weights = c(1, 2, 3))
saveRDS(request, "my-new-coastss-arguments.rds", version = 3)
```

The category weights and labels above are an explicit example, not recovered paper parameters. Consult the installed original help for the chosen method and declare the required inputs. `COAST` accepts individual-level data; `COASTSS` accepts summary statistics and does not contain every original individual-level component.

**Model scope:** the primary paper derives COAST-SS for quantitative phenotypes. It leaves calibration for binary/discrete traits as further work. An accepted API call does not establish that calibration.

```bash
python3 tools/literature/allelicseries0112/run.py \
  --provider build/new-allelicseries-original \
  --function COASTSS --arguments-rds my-new-coastss-arguments.rds \
  --seed 88201 --allow-analysis --out build/my-new-coastss-result
```

Use a NEW output directory for each call. An explicit seed makes stochastic calls reproducible in the same qualified runtime; omitting it preserves the original unseeded behavior. Every call retains the exact input RDS, full original result object, original warning/message/error conditions, RNG state, runtime, source identity, process logs and file manifest. COAST objects also export complete Betas, Counts and Pvals tables when present. Structure previews are abbreviated; `original-value.rds` and `complete-original-return.rds` preserve the full values.

RDS preserves complete R types, attributes, `NA`, `NaN` and infinite values. The current numeric TSV projection uses 17-digit text and distinct `NA`, `NaN`, `Inf` and `-Inf` spellings. Row labels occupy the first column. Use a proper quoted TSV reader for identifiers containing tabs/newlines/quotes; use RDS for complete types and attributes. The older checkpoint's TSV formatting remains retained as historical evidence.

Original errors or nonfinite returned values are retained with **FAIL2 and no analysis admission**, including an original COASTSS object whose SKAT component is missing. Read the original conditions and correct the actual cause before retrying. The adapter does not silently replace that component. Successful execution is not validation of input biology, study design, calibration or exact author-runtime reproduction.

Original warnings are printed prominently in the command output and at the start of `WARNINGS.md`. `COMPLETED.json` records their count and whether review is required. Review the original cause, then justify or correct the named input/model choice before using the result. Original errors are also printed with the failure marker and concrete condition text.

The original low-level `BaselineSS`/`SumCountSS` functions return scalar zero when no positive standard errors remain. The adapter retains that exact return and marks **FAIL2/no analysis admission** with `RETURN_ADMISSION_REASON.txt`. It also withholds an ambiguous low-level zero whose sentinel status cannot be established from typed arguments. A computed zero tail probability with explicit positive SEs remains a successful original return. The original method is always called once; admission checks do not replace values or reevaluate an argument expression.

## Batch many genes in one original R process

Use [`batch.py`](batch.py) with a named R list of complete argument lists, one per literal gene identifier. All 16 original exports are supported. The adapter verifies the provider before and after the complete batch and loads one original R namespace for all entries. [Copyable batch examples and result/recovery guidance](../../../docs/literature/ALLELICSERIES0112_BATCH.md) cover this interface.

Every request is processed in its original order, and every original return/error is retained in an ordinal directory. Identifiers are never used as file paths or repaired; the full ID stays in RDS and the quoted index. Missing/duplicate/empty identifiers, malformed argument lists and capacity overflow reject the full batch before any original function call. A failed/nonfinite/scalar-zero entry prevents complete-batch admission, while all completed entries remain visible. There is no automatic gene dropping or prefix admission. A seed is set once for the shared original RNG stream; stochastic results depend on input order.

## Native Java methods

`org.jlinalg.genetics.AllelicSeries0112` uses the project's exact JDistlib 0.10.2 dependency, including its Commons Math matrix classes. It compiles to Java 17. The independent reference comparisons cover the complete returned effects, standard errors and p-values for 539 original burden calls, including 12 original failures and four low-level scalar-zero returns. Those comparisons passed on Windows Java 21/25 and Linux Java 21. They do not cover every possible R argument/coercion, arbitrary matrix conditioning, every utility parameter or the original SKAT-O integration. Broader qualification and packaging remain open.

The native API uses typed integer annotations and rectangular Java arrays. Low-level `baseline` and `sumCount` expect one-based labels and explicit LD; `burden` implements the source wrapper's zero-label shift, missing-LD warning, single epsilon addition and genomic-control behavior. Missing categories retain the full declared baseline degrees of freedom. The original scalar-zero low-level edge and inconsistent mixed positive/nonpositive SE behavior are explicit. Native exceptions are not identical R condition objects. Arrays and warning lists returned by the API are immutable copies.

Do not interpret `sourceScalarZero=true` as a successful association result. The checked wrapper retains the original failure to extract effects/p-value from that scalar return.

```java
import org.jlinalg.genetics.AllelicSeries0112;

var result = AllelicSeries0112.burden(
    new int[] {1, 2, 3}, new double[] {.1, -.2, .3},
    new double[] {.2, .2, .2},
    new double[][] {{1, .1, 0}, {.1, 1, .1}, {0, .1, 1}},
    new double[] {1, 2, 3}, "sum", 1.0, 1.0);
System.out.println(result.pValue());
System.out.println(result.warnings());
```

This synthetic example explicitly selects the weighted-sum method, epsilon and genomic-control factor. It is not a native COASTSS omnibus call.

Initial native comparisons exposed literal annotation-matrix multiplication effects on `Inf`/`NaN` propagation. The baseline and weighted-sum operations have different multiplication orders. Their failed source snapshots and complete mismatch logs are retained; subsequent passing results do not relabel those failures.

The selected source, official version-lookup evidence, complete original objects and private runtime logs remain separate from the main catalog. The first development checkpoint describes its archived source snapshot; the batch/scalar-admission successor changes project wrappers, not the original package. The full 250-paper software audit and implementation goal remains open.

`paper-use-intake.json` individually binds all 26 named AllelicSeries/COAST-SS source anchors in P204. It distinguishes UKB full in-sample LD, borrowed UKB LD in meta-analysis/TOPMed, model omissions, preprocessing, experiments, interpretation and availability. These are component-development reviews; they do not change the main catalog's reviewed-use totals or establish whole-paper reproduction.
