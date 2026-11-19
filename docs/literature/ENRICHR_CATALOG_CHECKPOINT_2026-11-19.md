# Enrichr cumulative catalog checkpoint, 19 November 2026

**The qualified current-service Enrichr adapter and 25-role source crosswalk
are admitted. The whole implementation and individual-review goal remains open.**

The catalog now has **136 new scoped integrations: 25 native Java and 111
upstream**, or 139 including three existing adapters. The fixed 250-paper
cohort has **2,703 observations, 1,462 reviewed uses and 1,241 pending**,
446 named families and 485 effective definitions. Missing definitions remain
259; method-overlap definitions decrease to 39. All 1,439 earlier reviews and
all paper identities are retained. The 2023-10-23 through 2026-10-23 cohort
window is unchanged; no extension was needed.

Read the [25-role crosswalk](ENRICHR_CATALOG_INTEGRATION.md),
[machine-readable coverage](omics-2023-2026/enrichr-source-role-coverage.json),
[admission proof](omics-2023-2026/enrichr-catalog-validation.json),
[normal-build qualification](enrichr-current-checks/README.md),
[qualified guide](../vignettes/enrichr-current-service.md) and
[runnable examples](../../examples/enrichr-workflow/README.md).

## What changed and what was checked

The new `enrichr-tools` command supplies RUN, offline VERIFY and offline
REPLAY for current human/mouse standard-background, explicit custom-background
and library-download profiles. Statistical computation is upstream. Java
preserves raw/partial captures, exact numeric tokens, literal input ledgers,
complete returned families and explicit warning/failure policies.

The normal strict Gradle JAR passed 42 API/workflow tests on each of Windows
Java 21/25 and Linux Java 21, live standard/custom/library commands, independent
nine-field comparisons of 600 result rows and cross-host verification/replay.
All six documented synthetic example commands passed. These checks add to,
and do not overwrite, the earlier composed-reference and failed-attempt evidence.

The source census checked all 250 XML identities and 44 named paragraphs.
Admission adds three source-supported observations (P048/P051/P134) and 23
reviews. P048/P051 are explicitly identified embedded figure-legend uses.
The P061/P163 review objects and use objects remain exact apart from separate
source-role qualification. Eighteen scope/preservation tests passed on each
of Windows and Linux. Actual missing-cache SKIP3, changed-source FAIL2 and
stale-plan FAIL2 controls passed before the reviewed plan was applied.

The unchanged original COLOC catalog owner runs in a separate exact clean
`29486e3f7bf83f534324f21c2d2bd917ad35b134` checkout, against its own source
and complete metadata. Its 809-entry predecessor manifest passed. No original
reader, scientific source lookup or saved hash is overlaid or refreshed.
The new reader separately checks the exact dispatcher addition, SOURCE-only
annotation inverses, 25 byte-identical original/annotated class pairs and all
retained ABF/API archive payloads. The new cumulative manifest has 812 entries.
Fresh post-admission verification repeated the original-owner, source,
qualification, metadata and review-inverse checks and passed with no manifest
discrepancies. Its exact receipt is `verify-1/COMPLETED.json`; the
[saved admission evidence](enrichr-catalog-checks/README.md) retains the checks
and controls.

## Warnings and required actions

**SKIP3: required exact permitted dependency/service unavailable. Skip its
dependent analysis/audit, restore the named item and retry NEW output. FAIL2:
invalid data/schema, changed evidence, stale plan, interruption or capacity.
Admit no complete result, preserve inputs/raw prefixes/partials/errors, correct
the cause and retry NEW output. Never silently alter IDs, P values, source
inequalities, library names or algorithms. Read `WARNINGS.md` and
`analysis-status.json`. Source P=0 and positive infinite score margins remain
explicit warnings, not finite substitutes.**

P055 is library-resource use followed by custom target filtering/intersections
and FGSEA; P121 includes separate Julia Fisher/odds-ratio/BH recalculation.
Neither is reproduced by current remote statistics. P182's ClusterProfiler
function attribution remains unresolved and receives no execution mapping.
Historical clients, library editions/bytes, exact author inputs/options,
complete author workflows and biological validity are not established. Keep
P039's literal inequality, P098's reported two-sided test and P161's singular
library spelling. Do not silently repair them or infer absent settings.

Stored query echoes do not establish the final scoring population. Full
custom normalization is unobserved and requires explicit acknowledgement;
pre/post library equality is observed stability, not an authenticated atomic
snapshot. The example's 16-gene background is artificial, not a recommended
biological universe. Keep numeric tokens as strings/BigDecimal and use a
quote-aware TSV reader.

Two historical full marker-verifier mains still require the exact original
`build/cli/omicsmatic-0.3.7.jar`, 164,898,617 bytes, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
They remain SKIP3 until restored; another release or changed hash is no substitute.
Earlier fixture/build/archival failures are retained. Cell Ranger remains
excluded. Permitted downloads/installations and accepted research-use terms
continue as documented in the previous handover.

The unchanged development-packet reader pins its old handover navigation and
therefore refuses later navigation updates. Use `verify_enrichr_current.py`
for the qualified current component and the guarded Enrichr catalog driver
for cumulative verification. The frozen older source/proof files remain
available; no old fingerprint is refreshed to make a check pass.

## Resume and transfer

Use the [guarded admission instructions](../../tools/literature/enrichr_catalog/ADMISSION.md)
with `--verify` and NEW output; **do not reapply the completed admission**.
Next prioritize the high-impact missing scopes: Scanpy (22 current catalog
papers), SuSiE (19), GSEA (19), GCTA (17), edgeR (16). Audit actual paper roles
and existing code before implementation. Continue the 1,241 individual reviews.
These frequencies are current catalog counts, not new full source censuses.

Active checkout: `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. All 21 previous worktrees remain intact;
the added clean historical COLOC checkout is a 22nd worktree and must also
be preserved. Do not reset/clean the original dirty project root or overwrite
historical overlays. The earlier [transfer baseline](HANDOVER_2026-11-19_FINAL.md)
and [Enrichr development handover](HANDOVER_2026-11-19_ENRICHR_WORKFLOW.md)
retain original paths and failure history.

**GitHub is not a complete machine backup.** Preserve/transfer permitted
private inputs, large JARs and runtimes separately, especially the entire
active-checkout `build/enrichr-high-impact-1/` (including
`workflow-normal-live-1/`, `workflow-examples-1/`, `current-packet-1/` and
the normal/composed candidate JARs) and `build/enrichr-catalog-admission-1/`
(original-owner checkout, plan/inspect/apply/verify, controls and raw logs).
Keep all earlier XML/PDF/author archives and evidence/runtime stores.

Publication and preservation receipts are under
`D:/projects/JLinAlg/build/git-publish/20261119-enrichr-catalog-43/`.
Both literature reports remain withheld from Pages. A push touching their
`site/` source triggers the existing deployment, which excludes both reports;
check its hosted result and live exclusions. No release is created. The
published 0.4.0 binary predates this command; use a current source build or
the exact qualified normal JAR pinned in the guide.
