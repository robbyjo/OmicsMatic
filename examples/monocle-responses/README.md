# Explicit native response preparation

This complete synthetic example uses 48 cells and four genes. Java prepares the
responses, appends a supplied covariate, and then runs the existing residual-Moran
test. Python 3.11+ only copies checked inputs and writes requests. R is not needed.
Use a source-built JAR containing `monocle-tools RESPONSES-1.0.0`; release assets
have their own versioned feature lists.

**Preparation is not a gene test or a paper reproduction.** Counts, size factors,
pre-filter detected-gene counts, numeric covariate coding and graph weights are
explicit inputs. This example estimates none of them. Its deterministic ring
graph has no biological interpretation. P021's author recipe, executed commit,
root, population and assay choices remain separate unresolved requirements.

From the repository root, using NEW directories:

```powershell
python -B examples/monocle-responses/prepare.py --out build/NEW-response-example
$jar = 'build/cli/omicsmatic-0.4.0.jar'
java -jar $jar monocle-tools RESPONSES-1.0.0 RUN --request build/NEW-response-example/preparation-request.json
java -jar $jar monocle-tools RESPONSES-1.0.0 VERIFY --capture build/NEW-response-example/preparation --out build/NEW-response-verification
java -jar $jar monocle-tools RESPONSES-1.0.0 REPLAY --capture build/NEW-response-example/preparation --out build/NEW-response-replay
java -jar $jar monocle-tools MORAN-LM --request build/NEW-response-example/moran-request.json
```

Build the normal JAR with `./gradlew.bat executableJar` if necessary; follow the
project's Java/build dependency instructions. Change `$jar` to the actual output
path for an isolated build. Keep the request pathname ASCII on Windows; Unicode
input paths can be declared inside UTF-8 JSON. Paths in RUN requests are relative
to the request file, while VERIFY/REPLAY command paths use the current directory.

Read `preparation/WARNINGS.md` and `preparation/analysis.json`. Require exit 0,
`COMPLETED.json` and `response_preparation_admitted=true`. The preparation reports
`scientific_test_admitted=false`. Only the separate `statistics/analysis.json`
with `result_admitted=true` admits the complete residual-Moran testing family.
`statistics/tests.json` contains original-comparable I, expectation, variance, z,
normal p/log-p and complete-family BH q values. Read numeric warnings if p rounds
to zero; retain log-p. `original-reference.json` contains the original synthetic
control, not output computed by the Java implementation.

**SKIP3:** an input/output requirement is unavailable or forbidden. Supply the
exact accessible path named in the error and retry NEW output. If installation
is prohibited, skip the dependent analysis and state what must be supplied.
**FAIL2:** preserve partial output and `FAILURE.txt`, correct the named cause and
retry NEW output. Never consume a directory without its valid completion/admission
record. Do not drop genes/cells, recode IDs, estimate missing factors or replace
nonfinite values to force success. VERIFY recalculates captured arithmetic;
REPLAY writes a fresh complete capture and preserves its parent. Neither reads
the original input paths after capture.

For real data, explicitly select `size-factor-log10-plus-0.1` or `as-is` in the
request. The first computes `log10(expression / supplied_size_factor + 0.1)` and
requires positive finite supplied factors and nonnegative finite expression.
`as-is` copies supplied finite prepared responses and refuses an ignored factor
table. A family label does not fit a quasi-Poisson GLM.

The expression TSV is literal UTF-8: `gene_id` followed by complete cell IDs, then
one gene per row. Quotes, spaces, Unicode and `NA` IDs are preserved literally.
All values must be finite decimals. Factor and metadata tables have headers
`cell_id<TAB>size_factor` and `cell_id<TAB>nFeature_RNA`, with the identical cell
axis and order. Detected-gene values must be exactly represented positive integer
counts up to 2^53; supply their pre-filter provenance. They are never recomputed
from selected genes. Their natural log is emitted in `log_n_genes.tsv`.

Optional `base_design.tsv` has `cell_id` and explicit numeric columns, including
any desired intercept/factor contrasts. With explicit design provenance and
detected-gene metadata, the command appends `log_n_genes` to `design.tsv`. It does
not infer coding, rank, root or missingness rules; the downstream test checks its
own complete design and graph. Capacity limits are 64 MiB/file, 128 MiB input,
256 MiB capture, 16 million matrix entries and 128 final design columns. Operation
limits cover complete preparation/design work and refuse partial populations;
they are not operating-system resource quotas.

**Downstream numerical refusal:** a response that lies in the supplied design
space has no resolved residual variation. MORAN-LM refuses its entire testing
family, even when original R returns finite inference from rounding residuals.
The retained `asis-plain` control demonstrates this difference; no gene is dropped
and no finite R result is silently promoted or replaced. Preparation can still
complete because it performs arithmetic without fitting that model.
