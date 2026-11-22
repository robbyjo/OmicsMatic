# Classic FUMA cell-type gene-property workflows

The typed command combines original MAGMA fits with native Java multiple-test
correction and conditional interpretation. It supports the classic supplied
cell-expression resource workflow in the pinned
[FUMA-webapp source](https://github.com/vufuma/FUMA-webapp/blob/bdfbf06b5dd24037ec3c7497d773fad3c2656150/scripts/magma_celltype/magma_celltype.R).
**Broader FUMA remains unfinished.** EWCE, CELLEX, CEPO, MAGMA.Celltyping and
gsMAP are separate methods. This implementation does not establish equivalence
to a paper's historical FUMA version, resource release or biological result.

Start with [the runnable synthetic example](../../../examples/fuma-celltype-workflow/README.md).
The exact local candidate and evidence counts are in
[fuma-celltype-validation.json](fuma-celltype-validation.json). This scoped
development milestone has no canonical release, push or deployment claim.

## Required inputs

```text
fuma-tools --method cell-type --celltype-options-file OPTIONS.json
```

All JSON option values are strings. Paths resolve relative to the carrier file.
The carrier path and output parent must be ASCII for original Windows fitting.
Unicode resource paths and the final output directory name can occur inside the
UTF-8 carrier. The output directory must be new.

| Option | Required content |
| --- | --- |
| `runtime-profile`, `home` | `windows-x86_64-static` and a private admitted original MAGMA 1.10 home |
| `gene-bundle` | Sealed `magma-tools` gene bundle: marker, tested results, original `genes.raw` and gene status |
| `datasets` | UTF-8 TSV with exact `dataset`, `resource_id`, `path` header |
| `build`, `expression-id` | Explicit assembly and supplied resource identity; build must match the gene bundle |
| `correction` | `bonferroni`, `BH`, `BY`, `holm`, `hochberg` or `hommel` |
| `step2`, `step3` | Explicit `true` or `false`; step 3 requires step 2 |
| `gene-coverage`, `missing-values` | Each explicitly `error` or `source-default` |
| `out` | New writable output directory in an ASCII parent |

Dataset property paths resolve relative to the dataset manifest. Each property
TSV has `GENE`, at least one cell column, and exact `Average`. Gene, dataset and
cell labels must be unique ASCII identifiers containing letters, digits, dot,
underscore or hyphen, at most 256 characters. Dataset names may not contain
`_fumaCelltype`. Property values are finite decimal/scientific numbers, or exact
`NA` under explicit `source-default`. No expression normalization, imputation,
orthologue conversion, identifier conversion or `Average` computation occurs.

The sealed gene bundle records the tested universe, model, ancestry and assembly.
Supply genome-wide gene analysis independently of locus filtering. Matching IDs
does not prove that resource releases or biological populations are compatible.
The `error` policies reject absent tested genes and any resource NA values;
`source-default` retains source missingness for original MAGMA handling and
requires review of effective `NGENES`, missing genes and join dispositions.

The typed parent workflow accepts a `cell_type` object with the same options,
excluding `out` and recursive carriers. It supplies the parent build. Provide an
external `gene-bundle`, or select `magma_gene` and let the parent connect its
sealed result. A connected generated bundle cannot be overridden. Dependency
skips propagate to dependent stages while independent branches retain their own
status and warnings.

## Fits and interpretation

Step 1 fits all supplied cell properties per dataset, conditioning on its supplied
`Average`, using the original source's `correct=all`, `direction=greater` model.
The complete marginal populations define both per-dataset and global correction
families. Global adjusted P strictly below 0.05 selects optional conditional tests.
All marginal rows are retained in dataset order, with stable P sorting within
each dataset. Correction uses direct double arithmetic like R, including P-value
underflow. Lost log probabilities are not invented.

Step 2 fits every pair of selected cells within each dataset and applies the
pinned source's branch order, PS thresholds, main-driver switching and joint,
partial, colinear and drop states. **A source-code inequality differs from the
tutorial's introductory prose:** the relevant main-driver branch uses partner
conditional P greater than or equal to 0.05. Java follows the pinned executable
R code. Zero and singleton selection gates have explicit typed summaries and
warnings; the original can omit its summary file. An all-missing original
collinearity pair is distinguished from an incomplete fit. Genuine original
model failure is a failure, not an invented NA fallback or a successful fit.

Step 3 uses retained within-dataset states and selected between-dataset pairs.
Its expression join preserves left resource order, uses exact gene matching and
drops missing right genes/right `Average`; left `Average` NA remains explicit.
Every join decision is recorded. Numeric matrices use fifteen significant digits
to follow the original R write stage. Original fits condition on both supplied
averages, with source `max-miss=0.1`. Original model order, conditional means,
PS and PS.avg are retained; undefined ratios remain explicit. Output-only
`--settings gene-info abbreviate=0` expands original reports and labels. Actual
comparisons confirm the same tested fit values.

The current source form defaults to Bonferroni and unchecked conditional steps.
A historical paper saying "default" does not establish which optional steps it
ran. The catalog keeps P072's direct classic FUMA role separate from P131's
gsMAP cell-type analysis and FUMA gene-priority support.

## Output, limits and recovery

**Always read `WARNINGS.md`, `analysis-status.json` and `session.json`.**
Completed output includes `step1.tsv` and requested/gated conditional tables,
`all-original-fits.tsv`, `resource-coverage.tsv`, `step3-joins.tsv`, `datasets.tsv`
and `gene-analysis-universe.tsv`. `prepared/` and `calls/` preserve exact model
inputs, original native outputs and logs. The session binds input SHA-256 values,
source commit, runtime identity, every original call, expected/effective fits and
gate choices. Execution uses a temporary staging prefix recorded separately from
the final publication prefix; logs retain the paths that actually executed.

Use `--help` for all capacity options. Defaults bound input bytes (256 MiB per
file), one million genes, 64 datasets, 10,000 cells, 20 million resource values,
100,000 requested models, 256 native calls, two million ledger rows and 100
million interpretation operations. Completed native outputs are checked between
calls against a 1 GiB default; each call has a 3,600-second timeout and the
workflow a 7,200-second deadline. Capacities do not reserve JVM heap. The command
rejects an over-limit complete population; it does not truncate models or rows.

| Exit | Meaning and required action |
| --- | --- |
| 0 | Completed with warnings. Review resources, all ledgers, raw native logs and scientific choices before interpretation. |
| 3 | **Analysis skipped:** runtime missing, changed, unsupported or unavailable. Supply the named admitted runtime on a permitted Windows x86_64 host. If installation is unavailable or forbidden, keep fitting skipped. |
| 2 | **Analysis failed:** invalid input, policy, scientific model, capacity or output. Inspect named cause and `partial/`, correct it and rerun to a new output. |

Failures and skips quarantine child tables under `partial/`; they are diagnostic
only and never admitted results. Existing outputs and failed attempts are
preserved. Collinear designs produce a prominent native cause and required
action. Original MAGMA can report an error despite exit 0; this is checked.

Install separately, only where permitted:

```powershell
python tools/literature/install_magma.py --allow-download --environment NEW_WINDOWS_HOME
```

Do not redistribute the private MAGMA program or modify its admission marker.
This command currently admits Windows fitting only. The pinned source Docker
Linux download endpoints timed out during intake; they do not establish an
available validated Linux fitting profile. Actual Linux Java analysis returns
skip 3 with zero fits and clear recovery. The independent Java interpretation
API is portable and was compared on Windows Java 21/25 and Linux Java 21.
Cell Ranger remains excluded at the user's request.

## Validation scope

The candidate passes strict Java 17 compilation, clean Javadoc and 1,103 focused
tests in 85 suites, including 51 cell-type tests. All 1,103 tests also execute
against the exact packaged JAR on Windows Java 21 and 25. Native interpretation
matches 44 source cases, including two rejected source errors, and 77 R
correction families. Full unmodified source comparisons include nine original
R workflows and 42 actual MAGMA calls: eight completed workflows and one
retained singular-model failure. Typed comparisons cover complete output tables,
three-dataset ordering, retained within models, missing genes/values, zero and
singleton gates, long labels and every tested cross-dataset input matrix value.

Separate controls verify complete-population budgets, retained failed partials,
Unicode inputs/final names and both parent connection modes. The public verifier
runs all four recipes on Windows Java 21/25 and compares every table row and
field with the included original R references. Missing Java, missing MAGMA and a
real Linux host all skip 3 with required action. These numerical and contract
checks do not reproduce historical human expression resources or certify the
whole FUMA platform. Broader annotation/discovery and other catalog tools remain
open work in the active implementation goal.
