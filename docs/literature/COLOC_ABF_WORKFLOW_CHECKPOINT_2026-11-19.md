# COLOC ABF workflow checkpoint, 19 November 2026

**The native summary-statistics file workflow is implemented and scoped checks
passed. Cumulative COLOC catalog admission remains unfinished.**
This supersedes the CLI/workflow to-do in the
[earlier handover](HANDOVER_2026-11-19_COLOC_ABF.md). Preserve that handover,
its frozen API guide, source census and all evidence bytes.

Use [`coloc-abf --request`](../vignettes/coloc-abf-workflow.md) for RUN, VERIFY
and REPLAY. The [complete example](../../examples/coloc-abf-workflow/README.md)
includes two supplied synthetic TSVs and three request files. The existing
SuSiE `coloc` command retains its input and default-prior contract. The new
workflow needs Java and no R analysis runtime.

The workflow captures exact input files and source/effective requests, uses
explicit column/type/scale/weight/prior settings, saves regional and per-variant
output, displays warnings and retains failed/skipped attempts. Its verifier
checks complete inventories and rederives all native calculations; replay
verifies the parent and recomputes from captured data into new output. Finite
tiny probabilities cannot be replaced with zero. Numeric literal underflow,
invalid/nonfinite data, capacity and provenance corruption produce no complete
admitted result. SHA-256 integrity does not authenticate coordinated fabrication.

## Evidence

- **62 tests on each of three JVM profiles:** Windows Java 21/25 and Linux
  Java 21; 186 executions, zero skips/failures. They include all 30 original-
  package cases, eight expected scientific refusals, complete source-visible
  numerical output fields, Unicode/whitespace IDs, replay, schema and capacity
  controls, resealed scientific corruption and tiny-probability preservation.
- **Nine packaged commands:** RUN/VERIFY/REPLAY on each JVM profile.
- **Cross-host:** Linux verified and replayed a Windows bundle, preserving the
  complete Windows bundle byte-for-byte.
- **Existing behavior:** four SuSiE/coloc CLI regression tests passed. The earlier
  COLOC component packet readback passed unchanged. A real Linux symlink input
  was refused with FAIL2 and no complete result.
- **Build identity:** Java 21 `--release 17`; eight workflow payloads added and
  only `JLinAlgCli.class` replaced to add dispatch/help. All 23,129 other parent
  payloads, including the native ABF arithmetic and old SuSiE methods, are
  byte-identical. This is a qualified development candidate, not a release or
  an all-project Gradle/test-suite success claim.

The [compact packet/readback](coloc-abf-workflow-checks/README.md) contains exact
source/candidate pins, raw reports/logs, source/class snapshots, complete small
synthetic bundles and retained failure records. The original 6.0.3 reference
capture is reused; no fresh R execution or historical author-runtime claim is
made for this campaign. The 12,000/10,000-variant case is a synthetic regional
check, not a million-variant scale qualification.

Retained failed attempts: workflow build 1 had a method-name shadowing compile
error; workflow tests 1 had 26 output-schema comparison failures plus a test
edit that also changed headers. Output columns were completed and the test
transport corrected before new successful runs. Regression setup 1 omitted a
test helper and did not execute tests; setup 2 passed. Earlier failed attempts
and prototypes remain intact and unqualified, with no historical hash refresh.

## Resume next

1. Finish source-role/qualification coverage for existing SuSiE functionality
   and the [27 curated COLOC roles](coloc-abf-handover/source-roles.json).
   Preserve explicit ABF versus SuSiE versus unspecified methods, FUSION hosts,
   literal historical editions/priors, selection rules and supplementary gaps.
2. Implement/test cumulative COLOC catalog plan/inspect/apply/verify, exact
   predecessor guards, all 1,413 old reviews, method/edition coverage and
   compatibility deltas, metadata/report/manifest writing and fresh final
   source audit. Do not award whole-package or whole-paper completion.
3. Then continue higher-impact gaps and the wider pending individual reviews:
   Enrichr/Scanpy (22 papers), GSEA/SuSiE (19), GCTA (17), edgeR (16). Reuse
   existing methods; native Java where practical and tested upstream adapters
   as appropriate. The full 250-paper goal remains active and unfinished.

Canonical totals are unchanged: **250 papers, 2,700 observations, 134 new
scoped integrations (24 native +110 upstream), 1,413 reviewed uses and 1,287
pending**. There are 259 missing and 41 method-overlap definitions. COLOC has
zero new family credit until cumulative admission passes.

## Warnings and transfer

**SKIP3:** named missing/unreadable exact prerequisite; dependent analysis
skipped. Restore it or leave the analysis skipped. **FAIL2:** invalid scientific
input/nonfinite output, changed evidence, schema/capacity/heap/other failure;
no complete admitted result. Preserve raw inputs, partials and errors, correct
the cause and retry NEW output. Read `WARNINGS.md` and `analysis-status.json`;
never repair IDs, alleles, P values or NaN/Inf silently. Failures before a valid
fresh output exists are reported on standard error. Historical marker-verifier
SKIP3 gaps remain as documented in the Seurat checkpoint.

Active checkout is `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. Private evidence root is its
`build/coloc-high-impact-1/`: retain every earlier folder plus
`workflow-build-1..4`, `workflow-tests-1..3`, `workflow-qualification-1`,
`workflow-regression-1..2`, and the new public packet/readback attempts.
The final candidate is `workflow-build-4/omicsmatic-0.4.0.jar`:
194,520,647 bytes, SHA-256
`314e6760eb28953abecc45bdf6388f12cd580426f5e7315692b30f0809ff1609`.
Preserve all 21 worktrees, P36's 114 protected artifacts and 12 unchanged
canonical pins. Publication receipts continue in
`D:/projects/JLinAlg/build/git-publish/20261119-coloc-workflow-37`.
The earlier handover lists original XML, upstream source and both dependent
private R-library transfer paths. GitHub is not a complete machine backup.

Both literature reports remain withheld from Pages. No release is created.
The user-authorized download/install and research-use decisions remain in force;
Cell Ranger remains excluded. Missing/forbidden exact dependencies require
prominent skip/recovery reporting and independent work should continue.
