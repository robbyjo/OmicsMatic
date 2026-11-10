# GENESIS: retained missing interaction results

**FAIL2: no result admitted.** Two additional synthetic GENESIS 2.42.0 interaction controls returned original R missing values in required estimates, standard errors, statistics and P values. The native audit refused both, including through the actual root command. No missing value was replaced and no otherwise finite subset was admitted.

| Declared genotype coding | Affected original BIM rows | Complete missing output cells |
|---|---|---|
| Dominant | 110, 212, 258, 326, 359, 384 | 48 across six variants |
| Recessive | 33, 35, 41, 51, 89, 169, 185, 215, 243, 247, 267, 271, 293, 321, 322, 357, 367, 375, 409, 463, 489 | 168 across twenty-one variants |

Each affected row contains R `NA`, with `is.na=TRUE` and `is.nan=FALSE`, in all eight fields: `Est.G`, `Est.G:PRS`, `SE.G`, `SE.G:PRS`, `GxE.Stat`, `Joint.Stat`, `GxE.pval` and `Joint.pval`. Original input rows identify the variants; do not infer identities from a filtered output row number. The complete [finding receipt](omics-2023-2026/genesis-cross-option-findings.json) binds every missing cell, original source frames and actual refusal receipts.

The retained original `.testGenoSingleVarWaldGxE` source initializes missing results and leaves a variant missing when its Cholesky inversion fails. These checks do not independently refit the interaction estimator. The observations apply to the recorded synthetic inputs; they do not establish that dominant/recessive interaction analysis always fails.

**Required action:** keep these attempts unusable. Read `WARNINGS.md`, the original association RDS/schema/cell files in `unadmitted-worker`, the original BIM and declared model/interaction design. Investigate the unassessable variant/model combination and make any different input, coding, model or filtering decision explicitly in a NEW analysis. Do not replace missing estimates, relabel variants or silently drop failed rows to claim a completed result.

Ten other declared option combinations passed their native component contracts: dominant/recessive SPA and BinomiRare, requested CMP/BinomiRare with source GxE dispatch, Poisson SPA-to-Score fallback, Gaussian GxE with requested SPA, and two within-group two-stage association rescalings. Component collection admits no project result. Both failed interaction controls separately produced prominent actual root-command warnings and `result_admitted=false`.

Use [GENESIS_INTEGRATION.md](GENESIS_INTEGRATION.md) for complete examples, dependency skips, original CMP errors, out-of-range P-value refusals and effective-method warnings. These are repeated synthetic controls, not biological findings or historical-paper reconstructions.
