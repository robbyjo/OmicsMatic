# LDpred2 catalog integration

**Scope:** native Java LDpred2-inf and complete serial grid, plus original
bigsnpr 1.12.2/selected development 1.12.4 inf/grid/auto adapters. This is one
scoped native family credit, with original auto explicitly identified as an
upstream dependency. LDpred1 is unsupported. Build current source; the released
v0.4.0 artifacts do not contain this command.

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --request request.properties --out NEW-analysis
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --verify NEW-analysis --out NEW-replay
```

Read [current command and auto-scale scope](LDPRED2_AUTO_SCALE.md) and the
[prepared-input/runtime reference](LDPRED2_WORKFLOWS_V2.md). The frozen earlier
checkpoints retain their historical limitations and source identities.

The fixed cohort remains 250 papers, 446 families and 2,691 observations. Five
LDpred2 roles are reviewed: four reviews are added and one updated. The resulting
catalog has 1,203 reviewed uses, 1,488 unreviewed uses, 124 scoped integrations
(22 native and 102 upstream), and 263 missing families. Including three existing
adapters gives 127. Definitions remain 485. This is not whole-paper semantic
completeness or completion of the full toolbox objective.

| Paper | Source role | Important unresolved scope |
| --- | --- | --- |
| P074 | Aortic-stenosis grid PRSs; bigsnpr 1.12.2, population-matched 1000 Genomes LD, heritability multipliers 0.7/1/1.4, 17 causal fractions and sparse/dense models | Actual estimated heritability, author commands/build/seed and downstream MGBB tuning. A multiplier of 1.4 is not an inferred h2 value. |
| P109 | Grape-trait polygenic scores; reported 1.12.4, figure identifies auto, 70/20/10 split after variant clustering | Selected official package source is not established as the exact author commit. Clustering, split construction, author options and prediction fitting remain separate. |
| P125 | Auto with bigsnpr/custom scripts and author-provided European UKB LD for about 1.1 million HapMap3 SNPs | Version, custom controls and author binary. Synthetic pair LD does not reproduce the biological resource. |
| P128 | Multi-ancestry Alzheimer-disease PRS comparison and separate summation/APOE-region analyses | Version, LD controls and inf/grid/auto choice. Skip paper-specific execution until the choice is resolved. |
| P205 | LDpred2 adjusts real-GWAS effects in conventional PRS comparators; PRS-CS supplies simulated-GWAS comparators | Version, LD and method choice; downstream fine-tuning, score selection and combination fitting. |

The separately recorded [P074 disease-label correction](P074_PRS_DISEASE_ERRATUM.md)
preceded this admission. It corrected aortic stenosis in the existing PRS-CS
summary and the first unadmitted LDpred2 draft. This admission preserves that
correction and changes no other family or review.

**Finite is not converged.** Native inf/grid have 1.1 million variant software
controls. Original auto has complete-object and genuine-replay controls at the
same dimension for the selected 1.12.4 source, using three chains and all original
500 burn-in/200 retained iterations. Its finite causal-fraction estimates differ
substantially. No convergence test, chain selection, pooling, phenotype tuning,
calibration or biological validation is supplied. Inspect paths and establish a
justified stability/selection policy before interpreting or combining weights.
The other original edition retains its separate small controls.

**SKIP3:** missing or forbidden prerequisites skip dependent analysis. Supply
the named runtime/helper only where installation is allowed; otherwise leave
it skipped. Original execution requires Linux x86-64. Never substitute an
edition/method or unreported author setting to make a paper-specific run proceed.

**FAIL2:** retain all captured inputs, original objects, failed attempts and raw
consoles. Analysis/verification/recording failure admits no complete collection;
correct the named cause and retry new paths. Source nonfinite candidates have
no accepted effects. Never repair their values or average rejected chains.

The current source review hashes every existing tracked source file against the
8f45d582 checkpoint, with only the explicitly reviewed main dispatch/help change.
All new validation/source-review files are enumerated and hashed. Numerical and
worker class bytes match the preceding qualified build. The original whole-catalog
verifier runs unchanged in a clean 070ddea4 checkout, with exact clean 1a9ff82
and 357c9301 checkouts for its predecessor chain. Native records receive only
their exact historical eight metadata byte strings through the existing API;
no scientific source, verifier function or Git identity is overlaid. The
new consumer independently validates the actual current catalog transition.

```powershell
python -B tools/literature/ldpred2_catalog/catalog.py --verify --catalog-parent-checkout D:/trusted/clean-070ddea4 --previous-parent-checkout D:/trusted/clean-1a9ff82 --baseline-checkout D:/trusted/clean-357c9301 --rescan-sources --out build/NEW-catalog-check
```

This is saved scientific-record/source verification, not fresh scientific
execution. Old verifiers remain unchanged and may refuse later source/metadata;
use the new explicit consumer. Never refresh historical hashes to suppress a
refusal. The source literature pages remain excluded from website deployment.
