# Native stable SuSiE colocalization

`ColocSusie.analyze` and the `coloc` command compare supplied credible-signal
log Bayes factors. They implement a **native stable statistical variant**.
Their H3 calculation and prior-weighted conditional SNP probabilities have
documented differences from the selected coloc 6.0.3 source. Literal full-
package or historical-paper parity is not claimed.

Use the [synthetic CLI/API example](../../examples/coloc-susie-native/README.md).
For ordinary beta/variance or P-value summary statistics, use
[`coloc-abf`](coloc-abf-workflow.md). It has its own single-causal-variant input
contract, priors, warnings and source-compatible calculation.

## Input and identity contract

CLI files are CSV/TSV with `effect_index`, `variant_id` and `log_bayes_factor`.
Every effect in a trait file must cover that file's same variant set. Effect
identifiers are retained as supplied nonnegative integers; Java constructors
default to zero-based effects, while R uses one-based indices. Importers must
record any explicit index conversion. Trait-1 variant order defines the shared
population, and trait 2 is the outer loop for pair ordering.

Variant IDs are exact, unique within an effect, nonblank valid Unicode.
The CLI preserves spacing and Unicode in summary, labeled LD and effect tables;
it does not trim IDs. ` a ` and `a` remain distinct. Null IDs and malformed
Unicode are refused. No allele/ID harmonization, locus selection or association
model fitting occurs during colocalization. For the preceding `susie` command,
labeled LD must refer to the exact summary IDs, including whitespace.

**R's literal `null` hypothesis column is not a variant.** It is rejected by
the native input contract. An explicit R import must select the intended
credible-effect rows, preserve originals and, if a null column exists, compute
`variant_log_BF - null_log_BF` per signal before removing the hypothesis column.
Record this conversion. The native API/CLI does not perform it silently.
Do not discard a nonzero null baseline without subtracting it.

Finite log BFs and negative infinity are supported if each signal has finite
support somewhere in its original population. The legacy CLI's Java numeric
spelling for negative infinity is `-Infinity`; it does not guess R tokens such
as `-Inf`. Positive infinity and NaN are refused.

The API also accepts credible rows from a JLinAlg `SusieResult`. Comparing
imported original-package BFs does not establish that the native SuSiE fit,
LD estimation, credible-set selection or convergence policy reproduces the
original upstream fit.

## Priors, overlap and source differences

Defaults remain p1=p2=1e-4, p12=5e-6, minimum overlap 0.5 and posterior trimming
enabled. This is distinct from the ABF workflow's p12=1e-5 default. Priors must
be in (0,1). A shared prior larger than either trait prior is now accepted,
matching the selected source's scalar-prior interface. Preserve author-defined
marginal/exclusive prior definitions; do not infer exact arguments from rounded
reported quantities.

Complete shared variant populations have overlap exactly one and are retained
at `--minimum-overlap 1`. Previously two floating-point summation paths could
produce slightly less than one and drop all pairs. Partial-population trimming
continues to use the declared posterior-overlap criterion.

| Behavior | Native stable method | Selected coloc 6.0.3 behavior observed |
| --- | --- | --- |
| H3 | Enumerates distinct configurations in log space | Log-difference can round positive H3 support to zero |
| Weighted regional H0..H4 | Positive finite weights, normalized in log space | Agrees in tested finite ordinary cases; extreme finite weights can yield nonfinite source arithmetic |
| Conditional SNP posterior with weights | Includes supplied prior weights | Source returns its unweighted conditional SNP column |
| Single shared variant | Retains a normalized hypothesis distribution | Audited `coloc.bf_bf` returned finite but nonnormalized hypotheses and `-` lead IDs |
| No shared H4 support | API retains a legacy zero-vector marker with an explicit undefined flag/warning; CLI refuses a complete result | Audited source returned nonfinite conditional fields |
| Reserved null column | Explicitly refused pending a documented import conversion | Source recognizes/scales a named null-hypothesis column |

Weights are currently a Java API option; CLI weight arguments are not provided.
Native weight normalization can remain finite where the source sum overflows.
This is an explicitly different numerical method, not a repair or successful
execution of a failed source result. Raw original failures and invalid source
outputs remain retained and unadmitted in the audit evidence.

## Display warnings and handle undefined results

Applications must display and retain `result.warnings()`. The CLI prints them
and saves them in its log, along with `method=native-stable-coloc-variant`.
Warnings identify stable-method scope, prior-weighted conditional semantics,
the single-variant discrepancy, absent pairs and undefined conditional values.

Before reporting a conditional SNP vector, check
`result.isSharedVariantPosteriorDefined(pairIndex)`. The legacy API retains a
zero vector when H4 has no shared support for compatibility; **that marker is
not a probability distribution**. The CLI now refuses this case with exit 2
before writing regional/variant result tables. Preserve the inputs/log and
inspect the shared BF support before retrying new output paths. A defined SNP
posterior is still conditional on H4; evaluate regional H4 support as well.

No retained signal pairs means no hypothesis posterior is reported. Do not
reinterpret an empty result as H0 or absence of colocalization. Inspect overlap,
credible-set selection and matching coverage; change scientific settings only
deliberately and retain the earlier attempt. The command can complete with
empty tables and a prominent no-pairs warning.

The legacy `susie`/`coloc` commands write result files/logs, not the full
RUN/VERIFY/REPLAY capture bundle supplied by `coloc-abf`. Use new outputs and
preserve originals. Invalid input/numerical failures return exit 2. Missing
original R prerequisites affect only an attempted upstream reference/replay;
skip that dependent work with the exact restoration action instead of claiming
it passed. Native supplied-BF analysis itself needs Java, not R.

## Evidence and remaining scope

The [source-audit checkpoint](../literature/COLOC_SUSIE_AUDIT_CHECKPOINT_2026-11-19.md)
covers 28 original-package profiles, including two actual original
`runsusie`/`coloc.susie` fit pairs. Seven installed function bodies and formals
matched pinned source commit `27776b1bb14e19d1542798ac7477fa378fc2fc0d`.
Twelve complete profiles match the native result within declared tolerance;
four weighted profiles match regional hypotheses while retaining the documented
conditional difference. Remaining profiles explicitly preserve method
differences, unsupported inputs or inadmissible source outputs.

Fifty source/boundary/regression tests passed on each of Windows Java 21/25 and
Linux Java 21. Packaged success and undefined-result refusal passed on all three.
The ABF source/capture packets and an old ABF bundle verified unchanged.
No fresh R execution occurred during the JVM campaign; it consumed the frozen
original capture. The reference cases are synthetic, not biological validation
or original-paper reproduction. Full source-mode null handling, per-SNP prior
vectors, historical editions, original multi-causal fit workflow parity and
cumulative catalog admission remain separate gaps.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Gao Wang, Abhishek Sarkar, Peter Carbonetto, and Matthew Stephens (2020) — A simple new approach to variable selection in regression, with application to genetic fine mapping](../CITATIONS.md#wang-susie-2020) — [PMID: 37220626](https://pubmed.ncbi.nlm.nih.gov/37220626/) · [PMCID: PMC10201948](https://pmc.ncbi.nlm.nih.gov/articles/PMC10201948/)
- [Chris Wallace (2021) — A more accurate method for colocalisation analysis allowing for multiple causal variants](../CITATIONS.md#wallace-coloc-2021) — [PMID: 34587156](https://pubmed.ncbi.nlm.nih.gov/34587156/) · [PMCID: PMC8504726](https://pmc.ncbi.nlm.nih.gov/articles/PMC8504726/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
