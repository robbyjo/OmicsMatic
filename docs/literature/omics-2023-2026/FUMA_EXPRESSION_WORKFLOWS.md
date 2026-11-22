# Supplied expression and DEG resources for FUMA

The native Java command selects precomputed expression values and tests supplied
DEG memberships using the inspected GENE2FUNC method. **Broader FUMA remains
unfinished.** This command does not generate DEGs, transform expression, resolve
gene namespaces, cluster heatmaps or perform cell-type MAGMA. Current source is
pinned to [FUMA-webapp](https://github.com/vufuma/FUMA-webapp) commit
`bdfbf06b5dd24037ec3c7497d773fad3c2656150`; this does not establish equivalence
to a paper's historical FUMA version or tissue-resource release.

Start with [the runnable synthetic example](../../../examples/fuma-expression-workflow/README.md).
The scoped candidate is `build/literature-review/fuma-expression-candidate-1/jlinalg-0.3.7.jar`.
Validation status and its exact identity are recorded in
[fuma-expression-validation.json](fuma-expression-validation.json). No canonical
release, public deployment or full ROOT repository test claim follows from this
scoped development milestone.

## Inputs

All query/background IDs must already be resolved to the exact Ensembl resource
namespace. Version suffixes, case and multiplicity are retained. Supply each of:

| Option | Required content |
| --- | --- |
| `build`, `annotation-id`, `expression-id`, `deg-id` | Explicit supplied release identities; no inference or download |
| `symbols` | UTF-8 TSV with exact `ensg` and `symbol` header; first matching annotation symbol wins |
| `query`, `background` | Headerless, one exact resolved ID per line |
| `log2`, `norm` | Separate precomputed TSVs: `ensg` then at least two distinct labels; distinct gene rows and finite numeric values |
| `deg-sets` | Headerless name, declared count and colon-separated members; includes at least one exact `all` row |
| `duplicates` | `error` or explicit `original-count` |
| `outside-query` | `error` or explicit `source-size` |
| `out` | New writable directory |

Use `fuma-tools --method expression --help` for all options and capacities.
JSON options-file values are strings; paths resolve relative to that file.
This also supports Unicode paths through an ASCII command-line carrier path.
The typed parent workflow accepts an `expression` object with the same string
options, excluding `out` and recursive carriers. It supplies the parent build;
query/background remain explicit and independent of other stages.

## Source behavior and explicit differences

Expression opens only when more than one query entry occurs in the supplied log2
resource. Both matrices are then selected independently in their original row
order. Values and labels are copied without centering, normalization or a log
transform. Differences in ordered populations or labels are prominent warnings.
R drops dimensions for a one-row subset; Java rejects this case with a named
cause. For an empty normalized subset, original R omits the `ensg` header. Java
keeps the full typed header, writes zero rows and warns about this source difference.

The DEG gate requires more than one query entry in the user background. The test
then restricts that background to the union of exact `all` rows, recomputes each
set size from its retained memberships and preserves the full query size. It
uses an upper hypergeometric tail. Literal, unanchored `.up`, `.down` and `.twoside`
markers define three separate Bonferroni families. A row can enter multiple
families; duplicate names and empty rows remain in the correction multiplicity.
All result rows are retained without a significance filter.

Direct double P and adjusted P reproduce source arithmetic, including underflow
to zero. Separate native log values remain available. The original's invalid
draw/set populations can return NaN; Java rejects these populations rather than
publishing a probability. Deliberately retained multiplicity or query genes
outside the expressed background are diagnosed as incompatible with sampling
unique genes from the declared universe. They require explicit scientific review.

## Results, failures and required action

**Always inspect `WARNINGS.md` and `analysis-status.json`.** Exit 0 indicates a
completed workflow with review warnings. A source gate may still omit expression
or DEG output; missing output is not evidence of zero expression or no enrichment.
Exit 2 indicates failure. Earlier tables move into `partial/` and are diagnostic
only; correct the named resource, policy, capacity or output cause and rerun into
a new directory. Existing outputs are never overwritten.

`session.json` records release declarations, both gates, population diagnostics
and SHA-256 input identities. `DEG.txt` contains every source-order family row;
`deg-tests.tsv` adds original row/name, declared/recomputed counts, family size,
native logs and population flags. The expressed background and outside query are
separate ledgers. Each matrix retains its supplied labels and row order.

This native analysis requires Java only. The general project rule still applies
to upstream analyses: missing or forbidden installation must be prominently
reported as skipped, with the named cause and required user action. Cell Ranger
remains excluded at the user's request.

## Evidence scope

Private native tests cover source families, multiplicity, underflow, dimensions,
population capacities, gates, typed options, parent orchestration and retained
failures. Original-reference checks execute the unmodified pinned R DEG function
on 13 cases and full `gene2func.R` on eight supplied synthetic resource protocols.
Complete expression/DEG populations are compared on Java 21 and 25, with the one
empty-header difference explicitly recorded. Other full-script outputs such as
ID conversion and DrugBank annotations are outside this native command's scope.
The public verifier checks both runnable synthetic command paths and every
matrix value and DEG row. Synthetic arithmetic does not establish biological
calibration or reproduce the 14 papers' historical analyses.
