# Known association families and baseline unions

`fuma-tools --method known-associations` adjusts complete declared families,
constructs the selected baseline association union, and reviews every target.
This Java module accepts supplied association results. It preserves all tests and
evidence before applying the requested matching projection.

**REVIEW FAMILY COMPLETENESS AND SOURCE IDENTITIES.** Supply every intended
gene, trait and model test in its original correction family, including explicit
missing rows. Expected member counts are your assertions; matching those counts
does not independently certify biological completeness. Required action: verify
the model and resource releases, namespaces, original probability definition and
all family policies before interpreting any selected association.

**JAVA UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP THE ANALYSIS (verifier exit 3).**
Required action: provide Java 17+ on a permitted host, using `--java PATH` in the
public verifier if necessary. If installation is forbidden, keep the dependent
analysis skipped. This native module needs no R, MAGMA or upstream FUMA runtime
and never installs software. Skip unavailable upstream fitting analyses and
supply valid permitted results; missing associations are never invented.

**INPUT OR CAPACITY FAILURE (exit 2):** inspect the named cause in `WARNINGS.md`,
`analysis-status.json`, `session.json` and `partial/`; correct it and rerun to a NEW
directory. Partial tables are diagnostics. Exit 0 still requires review of all
warnings and unassessable rows.

## Run the example

Use the [self-contained example](../../../examples/fuma-known-association-workflow/README.md)
and [validation JSON](fuma-known-association-validation.json). The verified local
development JAR is
`build/literature-review/fuma-known-association-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`45f43df4caf5903bf37ae287c86663f333f08ed4926e636072872ae3a5fad12d`.
This is a local development artifact, with no release or deployment claim.

```powershell
python examples/fuma-known-association-workflow/prepare_inputs.py --out build/association-inputs-NEW
$jar = 'build/literature-review/fuma-known-association-candidate-1/jlinalg-0.3.7.jar'
java -jar $jar fuma-tools --known-options-file build/association-inputs-NEW/exact.json
```

The example includes five recipes, independent reference values, and a verifier
that checks all nine result tables and exact source snapshots on Windows/Linux.

## Declare the inputs

Use unquoted UTF-8 TSV tables with these required headers. Additional columns,
including citations and original numeric lexemes, remain byte-exact in snapshots.
Controls, malformed rows, duplicate headers, invalid UTF-8 and undeclared IDs fail.
Text identities are literal; gene versions and trait aliases are never normalized.

| File | Required columns |
| --- | --- |
| Sources | `source`, `role`, `model`, `resource`, `gene_namespace`, `trait_namespace`, `gene_annotation`, `genome_build` |
| Families | `family`, `source`, `encoding`, `adjustment`, `missing`, `reported_zero`, `duplicates`, `probability_definition` |
| Members | `family`, `trait`, `context`, `expected_rows` |
| Tests | `family`, `test_id`, `gene`, `trait`, `context`, `value` |

Each source declares `BASELINE` or `TARGET`, an identified model and resource,
and explicit annotation/build labels. All sources must share the declared gene
namespace; `gene-trait` matching also requires the declared trait namespace.
Identical namespace labels assert comparability; they do not perform harmonization.
Coordinate-free joins retain each source's build and annotation label separately.
Different labels trigger a review warning. Optional `--build` labels the analysis;
it does not convert coordinates or force source build labels to agree.

Each family belongs to one source and declares its policies explicitly:

| Policy | Values and behavior |
| --- | --- |
| `encoding` | `P_VALUE` accepts probabilities in [0,1]. `NATURAL_LOG` accepts natural logs at most zero and retains finite values below probability underflow. |
| `adjustment` | `BH`, `BY`, `HOLM`, `BONFERRONI`, or `SUPPLIED_ADJUSTED`. The last imports already adjusted values unchanged. |
| `missing` | `ERROR` refuses missing values. `EXCLUDE` retains missing rows but excludes them from the computed adjustment denominator. |
| `reported_zero` | `ERROR` refuses reported zero probabilities and negative-infinite logs. `ALLOW_REPORTED` retains them with precision warnings. |
| `duplicates` | `ERROR` refuses repeated gene/trait/context hypotheses within a family. `RETAIN` counts them separately, retains every contributor and records first-row provenance. |
| `probability_definition` | Describe the supplied test or original adjustment; the command performs no two-sided conversion or direction inference. |

Every family needs declared trait/context members with nonnegative integer
`expected_rows`, including missing tests. Explicit zero-row members are allowed.
Actual counts must match exactly. Every source needs a family, including sources
with no available tests. Test IDs must be unique within each family even with
`duplicates=RETAIN`.

Test values are decimal numbers, literal `NA`, or `-Inf` for an explicitly admitted
reported log zero. A nonzero decimal such as `1e-1000` that would become zero in
binary64 is refused; supply its finite natural log instead. Lost log precision is
never reconstructed. Original text remains in snapshots, while numeric result
columns use parsed binary64 values.

For imported adjusted values, `available_rows` reports available input rows and
`computed_adjustment_size=NA` leaves the original correction denominator unknown.
Required action: verify the source's adjustment method and original family;
importing q values does not certify how they were calculated.

## Select associations and review targets

```powershell
java -jar $jar fuma-tools --method known-associations --analysis-id my-review --gene-namespace my-genes --trait-namespace my-traits --sources sources.tsv --families families.tsv --members members.tsv --tests tests.tsv --cutoff 0.05 --matching gene-trait --absence require-baseline-tests --out build/association-review-NEW
```

All families are adjusted before selection, union or matching. Selection is
inclusive: probability families use `q <= cutoff`; natural-log families use
`ln(q) <= ln(cutoff)`. Finite logs remain available when the displayed probability
underflows to zero. Threshold-adjacent values trigger review; the command adds no
tolerance or rounding to the selection rule.

`--matching gene-trait` matches exact gene/trait pairs. `gene-across-traits` forms
gene unions across supplied traits for matching. It can retain links from different
traits and does not merge, split or otherwise change any correction family.
Contexts identify family members and source tests; they do not restrict the
declared gene/trait matching key.

`--absence require-baseline-tests` requires available matching tests in every
baseline source before absence can support novelty. `selected-reference` explicitly
permits keys absent from the selected baseline tables as relative absence. In both
modes, matching missing baseline tests or a baseline source with no available tests
make absence unassessable. A selected baseline match is sufficient evidence of a
known association even if another baseline is unavailable.

| Target status | Interpretation |
| --- | --- |
| `MISSING_TARGET_VALUE` | The target value is missing; no novelty comparison is assessed. |
| `TARGET_NOT_SELECTED` | The target does not meet its adjusted cutoff; no novelty comparison is assessed. |
| `KNOWN` | At least one selected baseline association matches the declared key. Every selected baseline link remains visible. |
| `NOVEL_RELATIVE_TO_BASELINES` | No selected baseline match exists and the explicit absence requirements are met. This is relative to the supplied sources and choices. |
| `UNASSESSABLE_BASELINE` | No selected match resolves the target and baseline evidence is incomplete. Required action: resolve missingness/eligibility or retain this state; never relabel it novel. |

`--known-options-file OPTIONS.json` accepts string values, resolves paths relative
to the carrier, and permits explicit direct-option overrides. An ASCII carrier
path supports Unicode Windows data paths. The typed workflow accepts independent
`known_associations` with the same string-valued options; paths are relative to the
workflow JSON. An independent completed stage can remain usable after another
stage fails or skips, but it does not change the parent failure/skip status.

## Inspect the evidence

| Output | Contents |
| --- | --- |
| `analysis-sources.tsv` | Every source identity, role, model, resource and namespace/build/annotation label. |
| `family-audit.tsv` | Policies and complete expected, actual, available, missing, zero, duplicate, selected and underflow counts. |
| `family-members.tsv` | Every declared trait/context member, including zero-row members, and its actual count. |
| `all-tests.tsv` | Every original test row, physical source line, parsed input, adjusted probability/log, selection and duplicate provenance. |
| `known-union.tsv` | Each selected baseline matching key and its contributing test count. |
| `known-contributors.tsv` | Every selected baseline test contributing to each union key. |
| `target-decisions.tsv` | Every target row and its status; missing/unselected targets retain `matched_known_tests=NA`. |
| `baseline-coverage.tsv` | Matching/available/missing/selected counts from each baseline source for every selected target. |
| `known-links.tsv` | Every selected target-to-baseline test link, using one-based input data rows. |

`resource-ledger.tsv` records source paths, snapshot paths, hashes and byte counts.
`source-inputs/` holds exact raw copies. Original files and snapshots are verified
before and after analysis. `session.json` records the declared choices, populations,
work counts and warnings. Evidence counts are not counts of independent biological
discoveries, and matching does not establish allele direction or replication.

Defaults limit each file to 268,435,456 bytes, combined input data rows to 1,000,000,
columns to 256, line bytes to 1,048,576 and input cells to 50,000,000. Exact snapshots
have a 1,073,741,824-byte budget and share the same-sized total scientific-output
budget with the tables. The nine result tables share 5,000,000 output rows; union
contributors, coverage and links share 2,000,000 evidence entries. Measured native
work is limited to 100,000,000 operations; `--timeout 300` is cooperative. These
admission limits are not OS memory quotas. Exceeding a limit fails the complete
analysis without silently truncating populations; diagnostics remain available.

## Paper context and validation

The reviewed [P019 Nature paper](https://www.nature.com/articles/s41586-026-10836-6)
uses a known baseline union from significant bulk TWAS and MAGMA associations and
distinguishes per-trait comparisons from unions across traits. The command exposes
correction families and matching projections independently; it does not infer an
exact historical novelty-FDR method from a citation. The
[supplement](https://media.springernature.com/original/springer-static/esm/art%3A10.1038%2Fs41586-026-10836-6/MediaObjects/41586_2026_10836_MOESM1_ESM.pdf)
and main methods use different correction descriptions for the later Fisher
comparison. Fisher enrichment is available through a [separate workflow](FUMA_FISHER_WORKFLOWS.md);
neither description is silently selected here. Source coordinate labels are retained without inferring
a conversion; this module joins gene IDs and never uses positions.

The raw-probability reference uses unmodified R 4.2.3 `stats::p.adjust`; the
[R documentation](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html)
describes its adjustment methods. Natural-log references use an independently
declared R extension that retains finite logs below probability underflow. This
does not claim that `p.adjust` itself accepts log probabilities, or that R 4.2.3
matches every historical package version used in the paper.

Validation includes 25 native and 26 CLI tests on Windows Java21/25 and Linux21,
27 novelty regression tests on those profiles, a 1,309-test/94-suite combined
gate, strict Java17 compilation and clean Javadoc. All 1,309 exact-JAR tests pass
on Windows21/25. The native references cover 50 families/785 rows and two
240,000-row BY families on three JVM profiles, totalling 1.44 million adjusted
rows. Thirty-six actual packaged main commands cover fifteen complete populations,
each with 150 families and 2,355 test rows; all nine tables and exact snapshots
match independent references. Fifteen public recipe runs and missing-Java/
altered-archive controls pass. See the validation JSON for exact evidence and
retained development failures. Broader FUMA remains missing, this extension adds
no new unique tool count, and Cell Ranger remains excluded.
