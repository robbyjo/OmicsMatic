# Native unrelated-sample selection

This synthetic example runs the Java `unrelated-select` command. It requires Java17+ and the development executable identified in [the workflow guide](../../docs/literature/omics-2023-2026/UNRELATED_SELECTION_WORKFLOWS.md). It has no external software dependency or installation step.

```powershell
python examples/unrelated-select/run-workflow.py --java 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' --jar build/literature-review/saige-native-candidate-1/jlinalg-0.3.7.jar --out build/unrelated-example
```

The example excludes `leaf3` as phenotype/covariate-ineligible **before** counting degrees. It retains the isolated participant, then `leaf2` and `leaf1`; `center` names `leaf2` as its retained blocking neighbor. `expected-decisions.tsv` checks every input-order decision. The input-order tie choice is explicit.

**Warnings require review.** For real data, supply the complete source participant universe and verify complete pair coverage above the declared generation cutoff, the additive-GRM relationship scale and all complete-case eligibility flags. Those properties cannot be inferred from omitted pairs. A graph generated at a higher cutoff cannot support selection at a lower cutoff. KING kinship values require a separately established conversion and are rejected by this CLI's scale declaration.

The output is maximal in the supplied eligible graph; it need not be the largest possible cohort. The custom paper method does not state its tie policy or whether degrees are updated dynamically. This implementation explicitly uses initial eligible degrees and does not claim identical paper-selected IDs. It does not generate a SAIGE GRM, fit a mixed model or run association tests. SAIGE integration remains pending.

Read `WARNINGS.md`, `decisions.tsv`, both retained-order files, `results.json` and `session.json`. Exit0 completes with review warnings. Exit2 retains failed inputs/partials and recovery instructions. Existing outputs are preserved; rerun corrected inputs to a new directory. The UTF8 JSON options carrier supports Unicode data/output paths on Windows Java21.
