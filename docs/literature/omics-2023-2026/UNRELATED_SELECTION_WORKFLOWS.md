# Native phenotype-eligible unrelated-sample selection

`unrelated-select` is a native Java17+ component inspired by P194's custom post-GRM cohort selection. It requires no SAIGE installation. The validated development executable is `build/literature-review/saige-native-candidate-1/jlinalg-0.3.7.jar`, SHA256 `9786ba29bd4dc92c43174af90e1ca643e5479cd5c8b824144a10635874059adf`. The canonical executable and release are unchanged.

**Warnings require review before using a selected cohort.** Supply a complete source participant universe, phenotype/covariate eligibility and an additive-GRM pair graph complete above the declared generation cutoff. The command cannot recover omitted relationships or validate the scientific scale from the numbers alone. A graph generated at a higher threshold is rejected when selecting at a lower threshold. Do not declare KING kinship values to be additive-GRM values. Repeat complete-case eligibility and selection for each phenotype.

```powershell
java -jar build/literature-review/saige-native-candidate-1/jlinalg-0.3.7.jar unrelated-select --samples examples/unrelated-select/samples.tsv --pairs examples/unrelated-select/pairs.tsv --cutoff 0.125 --pair-generation-cutoff 0.05 --pair-coverage complete-above-cutoff --scale additive-grm --ties input-order --out build/unrelated-example
```

The synthetic example's cutoff is illustrative. P194 reports `2^(-3.5)` on its SAIGE GRM and does not specify tie resolution or whether degrees are updated dynamically. This command requires explicit ties and uses **initial degrees counted once after eligibility filtering**; identical paper-selected IDs are not claimed. It does not calculate a GRM, fit a mixed model or perform association tests. Read [the SAIGE source review](SAIGE_SOURCE_REVIEW.md) for the distinction.

## Input and algorithm

Samples have exact TSV columns `sample_id`, `eligible`, with one unique nonblank ID and explicit `true`/`false` per row. Include isolated and ineligible participants. Pair rows have exact TSV columns `sample_a`, `sample_b`, `relatedness`. All pair endpoints must exist in the source universe. Self-pairs, duplicate unordered pairs, unknown IDs and nonfinite numbers fail, including invalid rows involving ineligible participants. Optional below-threshold rows are allowed. Relatedness can be negative; only a strict `relatedness > cutoff` forms an edge.

The command filters eligibility before counting initial degrees, visits ascending degree, retains a participant not already excluded and excludes its neighbors. Equal-degree ties use explicit `input-order` or `lexical-id` (Java String/UTF16 lexicographic order). Every excluded eligible participant names a retained blocking neighbor. Source participant order, retained source order and retained visitation order are exported separately.

The result is an independent set and is **maximal in the supplied eligible graph**: an excluded eligible participant cannot be added without a supplied above-cutoff relationship. It need not have maximum cardinality. An independent exhaustive reference includes a graph where this greedy method retains four participants although five are possible.

Defaults bound participants to200,000, pair rows to2,000,000 and each source file to512MiB; explicit ceilings cannot exceed1,000,000 participants/10,000,000 pairs. These bounds do not guarantee available JVM memory. The native API is `org.jlinalg.genetics.RelatedSampleSelection.select(...)`; it uses a compact CSR graph and immutable full decisions.

## Outputs and recovery

`decisions.tsv` preserves every source participant. `retained-samples.tsv` preserves source order; `retained-visit-order.txt` records the greedy order. `results.json` records exact counts, choices and source/staged hashes. `session.json`, `analysis-status.json`, the transaction manifest and prominent `WARNINGS.md` retain status and recovery steps.

Exit0 completes with review warnings. An empty eligible cohort has an explicit warning and an empty retained table. Exit2 retains failed inputs/partials, removes canonical success results and names the correction. Existing outputs are preserved. **Required action:** inspect every eligibility and graph assertion plus the full decision table; correct the named cause and rerun to a new writable output directory. No dependency skip or installation is needed for this native command. For Unicode Windows Java21 data/output paths, use an ASCII-named UTF8 JSON string-options carrier (`--options-file`) or the [public runner](../../../examples/unrelated-select/README.md).

## Validation boundary

Thirteen new API/CLI tests pass on Java21 and25. They include501 independent Python full-decision references,250 additional random-graph invariant checks and an exhaustive maximal-versus-maximum counterexample. The isolated32-test/5-suite gate includes19 unchanged IGV regression tests, strict Java17 compilation, Javadoc and executable packaging. Actual packaged workflows separately verify all decisions, strict boundaries, tie choices, empty eligibility, invalid sparse coverage/scale/declarations/pairs, preserved existing outputs and the unchanged Unicode example. See `unrelated-selection-validation.json` for exact evidence.

This validates the bounded native method. The original SAIGE runtime has separate exploratory evidence; its installer, admitted runtime and typed project association/gene/LD workflows remain pending. No full-current-shared-checkout gate, original-paper reconstruction, commit or release is claimed.
