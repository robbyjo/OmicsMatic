# Covariate-aware residual Moran tests

`monocle-tools MORAN-LM` tests graph autocorrelation remaining after an
**unweighted linear model**. It reports residual Moran's I, its fitted-design
expectation and variance, a normal-reference z statistic, p/log-p values and
BH-adjusted values for the complete declared gene family.

The calculation runs in Java. R and Monocle are not needed. Build current
source; the released 0.4.0 JAR is unchanged.

```bash
./gradlew executableJar
python3 -B examples/residual-moran/prepare.py --out build/moran-example
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar monocle-tools MORAN-LM \
  --request build/moran-example/request.json
```

On Windows use `./gradlew.bat executableJar` and your permitted Python command.
The example directory and its `result` directory must both be new. The helper
copies byte-checked synthetic inputs and writes a relative-path request; it
downloads and installs nothing. Check the exit status, `result/COMPLETED.json`,
`analysis.json` with `result_admitted: true`, and `WARNINGS.md` before using
results. The example has 60 observations, six explicit design columns and
three genes. Its inputs and original-spdep expected values are recorded in
[PROVENANCE.json](../../examples/residual-moran/PROVENANCE.json).

**Windows request pathname:** some Java launchers use the active Windows code
page and can change a non-ASCII character before the application receives it.
Use an ASCII pathname for the request file. Data/output paths and identifiers
inside UTF-8 JSON/TSV may retain Unicode. For a Unicode work directory, the
helper can write a separate ASCII request:

```bash
python3 -B examples/residual-moran/prepare.py \
  --out "build/moran café β" --request build/moran-ascii-request.json
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar monocle-tools MORAN-LM \
  --request build/moran-ascii-request.json
```

The helper declares the exact Unicode data/output paths in that JSON; it does
not rename files or repair identifiers. Preserve an earlier skipped attempt.

## Prepare the scientific inputs explicitly

| File | Literal, unquoted UTF-8 TSV representation |
|---|---|
| `design.tsv` | `cell_id` followed by unique numeric design-column names; one complete row per observation. Include an intercept column explicitly if intended. |
| `weights.tsv` | Exact header `from_cell`, `to_cell`, `weight`, separated by tabs. Each directed non-self arc has a positive finite weight. Reciprocal arcs are distinct; duplicates and zero/negative weights are refused. |
| `expression.tsv` | `gene_id` followed by the complete design cell IDs in exactly the same order; one finite prepared response per cell, one row per gene. |

Labels remain literal, including whitespace, quotes, Unicode and the string
`NA`. The token `NA` in a numeric field is refused. No rows are removed, axes
reordered, duplicates summed, missing values imputed or factor contrasts
inferred. Optional UTF-8 BOMs are recorded; captured input bytes remain exact.
Result JSON preserves identifiers directly; `tests.tsv` quotes gene labels
using ordinary doubled-quote TSV escaping.

The example design contains an explicit intercept, treatment-coded BatchB,
BatchC and SexM columns, PMI and log_n_genes. This is one declared synthetic
model, not an automatic encoding choice for other datasets. Rank deficiency
rejects the complete model; columns are not discarded.

Responses must already be on the scale justified by the intended model.
`response_units: "prepared-linear-response"`, a provenance statement and a
complete-population declaration are required. There is no automatic count,
size-factor, log or residual transformation before fitting the specified
linear model. Weighted regression, offsets, GLMs and alternative residual
definitions require their own methods.

```json
{
  "schema_version": 1,
  "design": "design.tsv",
  "weights": "weights.tsv",
  "expression": "expression.tsv",
  "response_units": "prepared-linear-response",
  "response_provenance": "Explicitly prepared responses on the selected scientific scale",
  "population_declaration": "Complete prespecified observations and testing genes",
  "zero_policy": false,
  "alternative": "greater",
  "rank_tolerance": 1e-7,
  "maximum_operations": 500000000,
  "out": "result"
}
```

Paths resolve relative to the request file. Unknown fields and executable
formula strings are refused. `alternative` is explicitly `greater`, `less`
or `two.sided`. The reference is the normal approximation for regression
residual Moran inference, not a permutation test or biological certification.

## Graph weights, isolated observations and model correction

The method uses `U = (W + W') / 2`, matching `spdep::listw2U`. It does not
row-normalize weights or construct neighbors. Supply the intended already
prepared weights; a distance is not automatically interpreted as a weight.

With `zero_policy: false`, the reference sample size is the complete model
population, and a truly isolated vertex in the symmetrized graph is refused.
With `zero_policy: true`, the reference sample size follows the original
`spdep::lm.morantest` adjustment: the number of original directed rows with an
outgoing neighbor. **All model and response rows remain present**, including
isolates. An incoming-only vertex can therefore affect the effective-N choice
even though symmetrization gives it a neighbor. The result separately records
actual observations, outgoing isolates, effective observations, model rank,
model residual degrees of freedom and test-reference degrees of freedom.

The fitted-design correction applies to the reference expectation and
variance as well as the observed residual statistic. Running an unadjusted
Moran test on residuals does not supply the same inference.

Preparation uses a thin orthonormal design and sparse symmetric edges. It
does not allocate an observation-by-observation dense weight/projector matrix.
One prepared graph/design can be reused across genes. Internal design-column
normalization and positive response scaling improve projection arithmetic;
the input data remain unchanged, and each response's scale and scaled residual
norm are reported. Near-rank, zero-residual and unresolved-variance cases fail
explicitly rather than producing an arbitrary numeric result.

## Results, limits and recovery

Keep the entire prespecified gene family in the request. BH adjustment is
computed across all successfully tested genes in that complete family. Any
input or gene failure rejects the whole run, including earlier fitted genes.
No failed gene is relabeled as a valid null finding with p/q equal to one.

`tests.json` and `tests.tsv` contain all gene rows in input order.
`analysis.json` records the design, population, reference choice, capacities
and input BOM flags. `manifest.json` records captured-input/output identities.
`COMPLETED.json` is written after complete analysis, identity checks and
successful console recording. Partial tables alone are not completed results.

**Floating-point probability warning:** very small p/q values can become zero.
`NUMERIC-WARNING.txt` and analysis metadata flag that event. Preserve
`log_p_value`, z and model/graph metadata; numeric zero is not an exactly zero
mathematical probability. A `-Inf` log token explicitly denotes an
unrepresentable log tail. No probability floor is inserted. The independent
10,000-observation sparse control includes finite log tails near -5,000 while
ordinary p-values underflow.

The CLI refuses more than 64 MiB per input, 128 MiB of total inputs, one
million observations, 128 design columns, two million directed arcs, 100,000
genes or 16 million entries in either matrix. A conservative complete-work
bound is also enforced: default 500 million operations, configurable up to
two billion. These are explicit refusal bounds, not OS resource quotas. They
are not a claim that every full atlas matrix fits this initial CLI. Do not
remove observations or testing genes to force a run under a bound; use a
justified larger-capacity implementation while retaining the intended family.

**SKIP3 — analysis skipped:** supply permitted accessible input/output paths.
This native command needs no external analysis runtime or installation.
**FAIL2 — no complete result admitted:** preserve inputs and partial files,
read `FAILURE.txt`, `analysis.json` and `WARNINGS.md`, correct the named cause
and retry a new output directory. Existing outputs are preserved. If final
recording fails, computed metadata are retained as `unadmitted-analysis.json`
and the analysis flag is false.

## Java API

```java
import org.jlinalg.spatial.RegressionMoran;

var prepared = RegressionMoran.prepare(design, directedWeights, false);
var result = prepared.test(response, RegressionMoran.Alternative.GREATER);
System.out.println(result.moranI() + "\t" + result.logPValue());
```

`design` is a complete rectangular `double[][]`; `directedWeights` is a
`List<RegressionMoran.Arc>` with zero-based observation indices. Prepared
objects own their arrays and are reusable across responses and threads.
No testing-family adjustment is implicit in the single-response API; collect
the complete intended family before applying the project's BH routine.

## Independent validation and paper scope

The original reference runs `stats::lm` and `spdep::lm.morantest` in R 4.3.3
with spdep 1.4-1. The installed function's body and formals match the exact
official CRAN source. Frozen fixtures contain 108 gene tests across 36
graph/design/alternative cases, including directed and reciprocal weights,
disconnected partitions, incoming-only and truly isolated observations,
explicit factor designs, no intercept, changed units and sparse tail controls.
See the [fixture provenance](../../src/test/resources/org/jlinalg/spatial/regression-moran/PROVENANCE.json).

P021's current author code identifies a covariate-aware regression-residual
Moran primitive inside a modified Monocle graph test. This native method
provides that statistical operation on **prepared** inputs. It does not
calculate the paper's Monocle1.0.0 weights, recover historical commits, choose
roots/lineages, recreate UMAT or establish its exact assay/covariate encoding.
That author code is neither executed nor redistributed here. The
[source review](../literature/monocle-command-development/P021_METHOD_DISCOVERY.json)
retains those distinctions. The complete Monocle family and 250-paper goal
remain open; no new whole-tool credit is awarded for this component.

### Method-specific source notes

- Bivand and contributors, [spdep regression-residual Moran documentation](https://r-spatial.github.io/spdep/reference/lm.morantest.html), referencing Cliff and Ord (1981), p. 203.
- Original [spdep 1.4-1 CRAN source](https://cran.r-project.org/src/contrib/Archive/spdep/spdep_1.4-1.tar.gz), `R/lm.morantest.R`, GPL >= 2; the native project uses GPL-3.0-or-later.
- P021, [Lifespan single-cell transcriptomic atlas of the human prefrontal cortex](https://doi.org/10.1038/s41586-026-10271-7), with the separately pinned current-author-code method review above.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [P. A. P. Moran (1950) — Notes on continuous stochastic phenomena](../CITATIONS.md#moran-1950) — [PMID: 15420245](https://pubmed.ncbi.nlm.nih.gov/15420245/)
- [Roger Bivand and spdep contributors (2026) — Moran test for regression residuals](../CITATIONS.md#spdep-moran-residuals)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
