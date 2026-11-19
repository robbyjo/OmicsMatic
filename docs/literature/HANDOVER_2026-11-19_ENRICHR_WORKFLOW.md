# Toolbox handover: Enrichr workflow development checkpoint

**The 250-paper collection is complete; the wider implementation and individual
review goal is unfinished. Start here, then read the
[transfer baseline](HANDOVER_2026-11-19_FINAL.md) and
[Enrichr source/API checkpoint](ENRICHR_COMPONENT_CHECKPOINT_2026-11-19.md).**

Canonical totals remain **135 new scoped integrations (25 native Java + 110
upstream)**, or 138 including three existing adapters. There are 2,700
observations: **1,439 reviewed and 1,261 pending**, 446 named families, 485
effective definitions, 259 missing definitions and 40 method-overlap definitions.
The 250 frozen papers comprise 71 Nature, 104 Nature Genetics and 75 AJHG papers
in **2023-10-23 through 2026-10-23**. No timeframe extension was needed. Do not
shift that frozen cohort or award new Enrichr credit before guarded admission.

## Saved work

The new `enrichr-tools` file workflow implements RUN, offline VERIFY and offline
REPLAY for the current human/mouse Enrichr service. It supports server-default
and explicit custom backgrounds, exact library download, raw request/reply
retention, literal input ledgers, all returned terms/hits and exact decimal
tokens. It checks library bytes before/after requests, records server query
echo where available, reports semantic warnings and refuses incomplete results.
The [workflow guide](../vignettes/enrichr-workflow.md) describes its limits.

The composed reference candidate passed 26 workflow tests on Windows Java
21/25 and Linux Java 21, live standard/custom two-library and library-only
commands, independent comparisons of all nine result fields for 600 returned
rows, and cross-host offline verification/replay. Its live custom result retained
54 positive infinite scores with warnings. These are source-defined margins,
not silently capped values. Raw live captures and downloaded libraries remain
private local evidence, not part of the compact GitHub packet.

A normal full Gradle executable build then passed with strict lint unchanged.
The normal JAR passed **42 workflow/API tests on each of Windows Java 21/25 and
Linux Java 21 (126 executions, no failures/errors/skips)**. The Linux tests and
their synthetic cases were captured in one invocation. The normal JAR is a
distinct artifact: **194,636,306 bytes**, SHA-256
`605674339041983a30997bf421b39cacc989ea36762c7d54cc5523bc63dc3923`.
**A fresh live service run, cross-host live-bundle replay and copyable example
qualification against this normal Gradle JAR remain to be done.** Do not merge
the two artifact identities or describe this as completed catalog admission,
whole-package parity, paper reproduction, full-repository testing or a release.

The first normal Gradle attempt failed six strict serialization warnings.
Narrow inline SOURCE-retained warning annotations resolved them without
changing line numbers. A separate strict compilation proved all 25 affected
older class payloads byte-identical. The first archive attempt also failed
because earlier Linux temporary directories were unavailable on a later WSL
invocation; tests had passed. Both failed attempts remain saved. The later
Linux test/capture invocation passed and archived its synthetic cases.

The [portable handover packet](enrichr-workflow-checks/README.md) contains saved
test/compatibility evidence and exact source pins. Its reader performs saved
evidence readback; it does not contact Enrichr or rerun scientific analyses.

## Resume in this order

1. Read the guide and packet, then run its saved readback into a NEW directory.
   Preserve every earlier successful/failed attempt and all 21 worktrees.
2. Finish normal-Gradle-JAR live standard/custom/library-only qualification,
   independent exact field comparisons, Windows/Linux offline VERIFY/REPLAY,
   and small runnable documented examples. Reuse the existing reference
   qualifier, changing the artifact and its exact producer pins explicitly.
   Do not reuse or refresh old bundle producer fingerprints to hide differences.
3. Finish the 25-role Enrichr crosswalk. The saved census checked all 250 XML
   identities and identifies three missed observations (P048/P051/P134), 23
   execution roles, one library-resource role (P055) and one ambiguous host
   attribution (P182). Preserve the two existing P061/P163 reviews. The
   proposed 23 additional reviews and three observations are **not admitted**.
4. State each role's coverage precisely. P055 downloads libraries then uses
   separate FGSEA/intersections; P121 recalculates statistics in Julia. Keep
   historical editions, exact library spellings, P039's literal inequality,
   P098's reported two-sided test and P182's ambiguous attribution explicit.
   A current remote API workflow does not establish those historical methods.
5. Complete guarded cumulative Enrichr admission through the existing COLOC
   predecessor and 809-entry manifest. Preserve old source/evidence hashes,
   all old review objects and the frozen paper identities. Resolve the exact
   reviewed dispatcher/SOURCE-annotation transition explicitly; do not edit
   historical readers or scientific lookups to make a gate pass. Recompute
   counts/reports and qualification only when actual admission succeeds.
6. Continue high-impact gaps: Scanpy (22 catalog papers), SuSiE (19), GSEA
   (19), GCTA (17), edgeR (16). Audit primary source roles and existing code
   before selecting a bounded scope. These frequencies come from the current
   catalog, not a completed fresh source census. Native Java where practical;
   otherwise use tested upstream adapters. Continue the 1,261 individual
   reviews alongside implementation.
7. Validate and push each concrete checkpoint to GitHub main without force.
   Keep both literature reports excluded from Pages. This handover changes no
   website files and creates no release. The published 0.4.0 binary predates
   this command; use a source build or the explicitly identified local JAR.

## Warnings and required actions

**SKIP3: the exact permitted dependency or service is unavailable. Skip its
dependent analysis, retain the diagnostic and restore the named dependency or
service before retrying into NEW outputs. FAIL2: invalid data/schema, changed
evidence, capacity or interruption. No complete result is admitted. Preserve
inputs, raw/partial captures and errors, correct the cause and retry into NEW
outputs. Never alter IDs, alleles, inequalities, P values or NaN/Inf silently.**

Historical `verify_coloc_abf_workflow.py` and `verify_enrichr_component.py` now
correctly refuse their old exact source pins because the dispatcher and narrow
SOURCE-only annotations changed. Their source, saved fingerprints and proof
packets remain untouched. The new handover reader verifies the exact additive
dispatcher inverse, exact annotation inverses, every other old pin, and all
retained ABF/API archive payloads. The separate 25-class identity proof applies
to the annotation change. This is explicit compatibility evidence, not a claim
that the old readers now accept the current source or that the cumulative
catalog gate was rerun successfully. Use the new readback for this checkpoint;
finish the cumulative transition during admission. The older ABF scientific
bundle separately passed with the composed reference JAR; the normal Gradle
JAR may have different producer class fingerprints.

Two historical marker-verifier mains still require the exact original
`build/cli/omicsmatic-0.3.7.jar`: 164,898,617 bytes, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
They remain **SKIP3** until restored. A different release is not a substitute.

Enrichr uploads gene identifiers to the remote service only with explicit
`allow_upload: true`. Use appropriately permitted prepared identifiers. The
standard stored-query echo is not independent proof of the final scoring
population. Custom normalization is unobserved and requires explicit
acknowledgement. Library pre/post equality establishes observed stability,
not an authenticated atomic or historical snapshot. Numeric tokens must be
read as strings/BigDecimal: converting `1e-400` to a double loses information.

The user authorized permitted downloads/installations, research/nonprofit
FIMO and academic noncommercial GATK 3.8. **Cell Ranger stays excluded.**
Missing exact runtimes must cause a clear skip with recovery instructions.

## Machine transfer and exact resume paths

Active checkout: `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. Do not reset/clean the original dirty
`D:/projects/JLinAlg` checkout or any other worktree. Historical variants are
already retained in the [21-worktree overlay archive](handover-20261119/README.md).

**GitHub does not contain ignored scientific inputs, complete live library
captures, large JARs or installed/restricted runtimes. Transfer them separately
where permitted.** Preserve all paths in the prior handover, plus the entire
active-checkout `build/enrichr-high-impact-1/`, especially:

- `workflow-qualification-1/`: composed candidate live captures, exact
  comparisons, offline proofs and earlier three-JVM XML reports.
- `workflow-build-2/omicsmatic-0.4.0.jar`: composed reference artifact,
  SHA-256 `6d16d96dbc0710e8037e80ed12205a2b67d50d3df015ceb68abf7662af00cfb7`.
- `workflow-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`: normal Gradle
  artifact identified above; `workflow-gradle-validation-1/BUILD.json` pins
  its exact sources and isolated test-only classpath.
- `workflow-handover-validation-2/`: final 126-test result, XML, native Linux
  synthetic case archive and compatibility record. Earlier validation-1's
  failed archive attempt remains retained and is not the final receipt.
- `workflow-serial-source-before-1/`, `workflow-serial-identity-1/`,
  `workflow-dispatch-before-1/`, `workflow-closure-1/` and all failed builds.
- `source-census-3/`, `api-reference-4/`, `component-qualification-3/`,
  `component-closure-3/`, `packet-checks-1/`, `upstream-intake-1/` and
  `upstream-details-1/`. Do not redownload into these frozen paths.

Reusable private helpers are under `D:/projects/JLinAlg/build/`:
`qualify_enrichr_workflow_20261119.py`,
`gradle_enrichr_workflow_v2_20261119.py`,
`validate_enrichr_handover_20261119.py`,
`finalize_enrichr_handover_validation_20261119.py` and
`archive_enrichr_linux_cases_20261119.py`.
They use create-new output directories; **do not rerun unchanged helpers into
existing outputs**. Copy/adapt into new attempts. Capture Linux temporary
cases in the same WSL invocation; `/tmp` was unavailable on a later invocation.

Publication receipts are under
`D:/projects/JLinAlg/build/git-publish/20261119-enrichr-workflow-handover-42/`.
The preceding published API checkpoint is
`383c677d81924d09c2f2f8140dfc44eab99393d5`. The final `PUBLISHED.json` records
this handover's pushed commit, exact public-byte checks, preserved worktrees
and protected artifacts. No whole-goal completion or full-machine backup is
claimed.
