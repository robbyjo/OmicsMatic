# PLINK, PLINK 2 and KING: source roles and qualified scopes

The cumulative admission records **96 reviewed roles: 53 PLINK, 26 PLINK 2 and
17 KING**. The [individual 96-role crosswalk](plink-source-review/CROSSWALK.md)
and [primary bindings](plink-source-review/CENSUS.json) preserve how each tool
was used, the cited editions, cohort-specific settings, method hosts and
unresolved choices. The [machine-readable coverage](omics-2023-2026/plink-source-role-coverage.json)
is written only by successful admission. Seven non-execution contexts and
eight proper-name mentions are excluded from positive-use credit.

The scope below supports **three upstream adapter family credits**. Existing
native helpers are reused, without additional native-family credit. A family
credit means the listed bounded workflows are available; it does not mean all
author operations or historical editions are reproduced.

| Component overlap in the source census | Available scope and evidence |
|---|---|
| `plink-glm` | Original 1.9.0 linear/logistic and 2.0.0-b.1.2 linear/logistic/Firth/fallback on additive diploid human autosomal hard calls; original report/error fidelity and independent ordinary OLS, not an independent Firth estimator |
| `plink-qc` | Explicit original sample/variant missingness, MAF/MAC, exact/mid-P HWE with declared populations/founders, filter order and unreportability policies |
| `plink-pruning` | Original hard-call pruning with explicit window/step/threshold/reference population and PLINK 2 pair order; not an unspecified author's pruning recipe |
| `plink-pca` | Original exact/randomized genetic PCA with declared sample/frequency masks, missingness and seeds; no ancestry assignment inferred from PCs alone |
| `plink-relatedness` | Separately qualified original PLINK 1 GENOME and PLINK 2 KING tables/matrices/cutoff; native pair-count checks have declared limits, not independent full IBD inference or PPC distance-thinning refitting |
| `king232` | Published standalone Java adapter for exact original KING 2.3.2 kinship/degree/duplicate/unrelated and Linux IBD-coordinate transport; no independent segment placement or optimal sample-selection refit |
| `king-historical` | Separately qualified original 2.2.7 and two 2.0 profiles for kinship/duplicates; source-discrepant output is rejected |
| `native-king-counts` | Qualified PLINK 2 between-family integer counts/formula with finite/nonfinite dispositions; not standalone KING or SNPRelate host equivalence |
| `native-king-thresholds` | Exact decimal inclusive/strict threshold arithmetic; caller must choose the correct source coefficient/operator and selection policy |
| `native-score` | Existing supplied-weight diploid autosomal scoring, explicit alleles/frequencies/missingness and original-reference checks; no weight estimation or clinical prediction validation |
| `native-ld-clump`, `native-ld-statistics` | Existing native method overlap remains separate. This admission does not qualify every original clump option, signed LD-matrix export, haploid/nonhuman model or custom pleiotropy ranking |

Use the [component qualification and reproduction instructions](plink-component-qualification/README.md),
[relatedness guide](../vignettes/plink-relatedness-workflows.md),
[typed PLINK examples](../../examples/plink-tools/README.md),
[KING guide](KING_INTEGRATION.md) and
[historical KING guide](HISTORICAL_KING_INTEGRATION.md).
The exact tested development JAR and published standalone component JARs are
identified in their own immutable qualifications. This admission does not
assert that every current public release contains every listed workflow.

## Source distinctions that remain explicit

- Newly proved P088/PLINK QC, P130/PLINK 2 compound-release postprocessing,
  P212/KING 1.4 and P229/lowercase KING 1.4 add four observations. P114's PLINK
  1.07 CNV role is bound to exact paper-linked author code inspected without
  execution. That legacy operation is outside current SNP workflows.
- P084/P123 host the KING estimator in PLINK 2; P191 hosts it in SNPRelate.
  Five other KING roles leave the host unspecified. These distinctions do
  not establish standalone package execution. All source/paper execution and
  completeness flags remain false; reported source use is not a project run.
- P109 reports a PLINK 3.31 LD edition while PCA names 1.9. P235's PLINK
  attribution for Tajima D and several flag spellings, HWE inequalities,
  window/step units and cohort allocations remain unresolved. They are not
  corrected into plausible author commands.
- Source-specific dosage/X, nonhuman/SV, pseudohaploid, ROH, meta-analysis,
  legacy CNV, signed LD-matrix and other unqualified steps remain visible in
  each role's `remaining_roles`. An LD-clump correlation table is not a signed
  LD matrix. A native formula is not proof of another program host's defaults.

## Warnings and required actions

**SKIP3:** if an exact source, permitted original runtime or validation package
is unavailable/forbidden, skip the dependent analysis/audit. Restore the named
prerequisite, verify its exact bytes and retry NEW outputs, or leave the skip
prominent. KING 1.4 is not qualified here. Do not substitute a newer KING or
PLINK edition to claim a historical source workflow. Cell Ranger stays excluded.

**FAIL2:** admit no complete result for changed evidence, invalid scientific
inputs/models, original-process errors, nonfinite/unreportable outcomes or
capacity failures. Retain inputs, raw reports, partials and errors; correct the
specific cause and retry NEW paths. Do not repair identifiers, alleles,
inequalities, nonfinite statistics or algorithms to force success. The known
KING 2.0 original/native joint-call discrepancy remains a scientific rejection.

**Legacy verification warning:** four old PLINK verifier mains initially fail
against the current checkout's byte-different fixtures. The unchanged producers
passed using an explicit exact frozen fixture project; use the
[restorer instructions](plink-component-qualification/README.md). Initial
failures remain retained. No old verifier or expected hash was refreshed.
Original PLINK execution in that campaign was Windows-only; frozen evidence
readback and restoration were checked separately on Windows/Linux.

**Historical marker warning:** two earlier full marker-verifier mains remain
SKIP3 for the missing exact old JAR (164,898,617 bytes, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`).
Restore that file or retain the skip. Separate retained-source/archive and
current method-identity checks do not replace those full historical mains.

## Catalog transition and limits

Successful admission produces **250 papers, 2,700 observations, 1,413 reviewed
positive uses and 1,287 awaiting individual review**. The **134 new scoped
integrations comprise 24 native Java and 110 upstream**; including three
existing adapters gives 137. Missing families remain 259 and method-overlap
families become 41. All 1,339 earlier reviews, fixed paper identities and
unrelated observations are retained. The [admission driver](../../tools/literature/plink_catalog/ADMISSION.md)
supports plan/inspect/apply/verify and rollback/concurrent-edit preservation.

The complete source collection remains the frozen original 250-paper window.
The full implementation and paper-wide completeness goal remains unfinished.
Both literature HTML reports stay withheld from Pages. No release is created;
ordinary site changes on main may deploy the rest of the site while excluding
these two reports. Private full text, author archives, original runtimes and
large JARs need separate permitted transfer; GitHub is not a machine backup.
