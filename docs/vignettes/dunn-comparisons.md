# Native Dunn comparisons with explicit rank and correction families

`posthoc-tools dunn` compares independent groups using pooled midranks,
tie-corrected standard errors and two-sided normal-reference probabilities.
It supports all pairs, comparisons with one control and an explicitly listed
family. It runs in Java without R or Prism. Build current source; the released
0.4.0 artifact has not been replaced.

The [source review and validation](../literature/native-dunn-development/README.md)
identifies eight papers describing Dunn tests, with Bonferroni explicitly named
in P122 and Holm in P167. One additional name hit is an author, rather than a
test. Exact observational units, author inputs, comparison families, model
choices and historical defaults remain separate requirements. This component
does not complete Prism or reproduce a paper's experiments.

```sh
java -jar omicsmatic-0.4.0.jar posthoc-tools dunn --help
java -jar omicsmatic-0.4.0.jar posthoc-tools dunn --request request.json
```

Save this UTF-8 request next to the input. Paths resolve relative to the
request, and `out` must be new:

```json
{
  "schema_version": 1,
  "input": "groups.tsv",
  "out": "new-dunn-result",
  "design": "independent",
  "adjustment": "HOLM",
  "family": "all",
  "rank_population_declaration": "All independent biological-replicate observations in the intended rank population",
  "data_provenance": "State the measurement, units, observational unit and any earlier processing"
}
```

`groups.tsv` is literal, unquoted UTF-8 TSV with this exact header:

```text
group	value
Control	1
Control	2
Control	2
Treatment A	2
Treatment A	3
Treatment A	4
Treatment B	5
Treatment B	6
```

An optional UTF-8 BOM is recorded; captured bytes remain unchanged. Every
observation must be a finite decimal. Labels are literal and keep first
appearance order. Empty rows, missing values and malformed labels/observations
refuse the entire analysis. The command does not transform values or remove
observations. Output text fields use TSV quoting to preserve literal labels.

Set `family` explicitly:

| Family | Additional request fields | Meaning |
|---|---|---|
| `all` | None | Every unique pair of declared groups. |
| `control` | `"control": "Control"` | Every other group versus that exact control. Signed differences are treatment minus control. |
| `specified` | `"comparisons": "contrasts.tsv"` | Only the listed, unique two-sided hypotheses, in their declared order/direction. |

For `specified`, `contrasts.tsv` has exact header
`first_group<TAB>second_group`. Each following row names two existing groups.
The reported difference and z are **second group minus first group**.
Repeated hypotheses, including reversed duplicates, are errors. Fields for
other family modes must be absent.

**The rank population and correction family have different roles.** Every
input group contributes to pooled ranking and tie variance, including groups
absent from a selected contrast. Only the declared comparisons contribute to
multiplicity adjustment. Removing an input group can change the test itself;
removing a comparison changes its correction family. Neither change is
implicit. The complete Kruskal–Wallis diagnostic is reported without using its
significance to decide which comparisons to run.

Choose `adjustment` explicitly: `NONE`, `BONFERRONI`, `HOLM`, `SIDAK`,
`HOLM_SIDAK`, `BH` or `BY`. Bonferroni/Holm control familywise error under
arbitrary dependence. Šidák/Holm–Šidák and BH require their corresponding
dependence assumptions; BY addresses arbitrary dependence. A software mention
does not establish which method or family the authors used. An outer family
across outcomes, genotypes or separate experiments needs a separately justified
correction; it is not silently added here.

Outputs include exact source input/request captures, `groups.tsv` with sample
sizes and mean ranks, `comparisons.tsv` with mean-rank differences, standard
errors, z, raw/adjusted probabilities and their logs, and `analysis.json` with
rank variance, tie diagnostics, omnibus values and explicit settings.
`manifest.json` fingerprints every result/input/warning file, and
`COMPLETED.json` pins that manifest after successful recording. Preserve the
qualified executable and its independently recorded checksum as well.

**Require exit 0, `COMPLETED.json` and `analysis.json` with
`"result_admitted": true`.** Read `WARNINGS.md` after success. A partial table
or `unadmitted-analysis.json` is not an admitted family.

The normal approximation applies even to small groups; there is no exact test
or continuity correction. Dunn's pooled ranks differ from separately running
Mann–Whitney tests. This is not universally a test of group medians. Paired,
repeated-measures, nested, mixed and covariate-adjusted designs need their own
models. All observations tied means zero rank variance and a failed complete
family. Individual constant groups and singletons can be represented when the
full population has positive rank variance, with the approximation warning
retained.

At floating-point underflow, raw or adjusted probabilities may be numeric
zero while their log probabilities remain finite. `NUMERIC-WARNING.txt`
identifies this condition. Keep the log probabilities and z; zero does not
establish an exactly zero mathematical probability. Log-space correction and
the Šidák tail route preserve positive subnormal/underflowed tails. A literal
`-Inf` log records an unrepresentable log tail and is never substituted for a
missing observation.

Whole-input bounds are 64 MiB observations, 16 MiB selected contrasts, two
million observations and 256 groups. The API separately bounds the comparison
family at one million unique hypotheses. No prefix of rows, groups or pairs is
admitted. These are refusal bounds, not memory/time guarantees.

**SKIP3 — dependent analysis skipped:** a required input/access is unavailable
or a paired/repeated/nested/mixed design was explicitly requested. Supply a
permitted input or a separately qualified implementation of the required
model, then retry new output. **FAIL2 — no complete result admitted:** preserve
input/partial output and read `FAILURE.txt` and `WARNINGS.md`; resolve the named
format, all-tied variance, family, capacity or integrity issue and retry new
output. Do not remove rows/groups, repair labels or replace nonfinite values
to force success. Native use requires no installation. Optional R development
references require a permitted R/rlang environment; if unavailable, skip that
dependent reference generation and keep the warning.

The Java API is `DunnComparisons.allPairs`, `versusControl` or `compare`, with
explicit group arrays, zero-based contrast indices and `Adjustment`. Results
and returned lists are immutable; inputs are not modified. Ranking alone
handles extreme finite measurements without subtracting their original units.
Tie counts and doubled rank sums use exact integer arithmetic within the
declared capacities before conversion to FP64.

The method follows the pooled-rank, tie-corrected normal calculation documented
in [GraphPad's Dunn guide](https://www.graphpad.com/guides/prism/latest/statistics/stat_how_the_dunn_method_for_nonpar.htm).
Its Bonferroni adjustment matches that described method. Validation also uses
the unchanged original [CRAN dunn.test source](https://cran.r-project.org/package=dunn.test)
for raw statistics and retains its differing stepwise values/errors, while
standard Holm/BH/BY values follow
[R's p.adjust](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html).
These references do not establish licensed Prism runtime equivalence.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Alexis Dinno (2026) — dunn.test: multiple comparisons using rank sums](../CITATIONS.md#dunn-test-documentation)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
