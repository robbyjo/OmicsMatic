# Toolbox handover: GCTA file-workflow development checkpoint

**The full goal is unfinished and paused for handover. Resume only when instructed.**
Start with the next actions below. This snapshot
adds development RUN/VERIFY/REPLAY workflows to the normal product JAR. It does
not admit GCTA/COJO to the catalog, add a global `gcta-tools` command, qualify
mtCOJO, reproduce a whole paper, or create a release. The earlier
[native GRM checkpoint](GCTA_NATIVE_CHECKPOINT_2026-11-20.md) remains valid.

## Current position

| Item | Current recorded state |
|---|---|
| Paper cohort | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen search interval | 2023-10-23 through 2026-10-23; no extension used |
| Cataloged definitions | 446 named; 485 effective definitions |
| Tool-use observations | 2,714 total; 1,548 reviewed; **1,166 pending** |
| Admitted scoped integrations | **140: 26 native + 114 upstream**; 143 including three existing integrations |
| Still missing / method overlap | 259 / 35 definitions; neither category means implemented |
| GCTA / COJO credit added here | **Zero**; source-role and cumulative admission remain open |
| Preserved local worktrees | **25**; do not reset, clean, delete or reapply old overlays |

These counts describe the current canonical catalog, not 140 fully replicated
software packages. The paper interval is deliberately frozen to the original
task; it is not a rolling three-year window updated at this checkpoint.

## Saved development work and checks

The standalone entry point is `org.jlinalg.cli.GctaWorkflowCli`. Native Java
implements prepared hard-call GRM algorithms 0/1 and float-threshold sparse GRM
conversion. The explicit original-runtime engine covers these and PCA,
COJO selection/joint/conditional models, univariate single/multi-GRM REML,
ordinary/regional LD scores, and binary fastGWA. There are ten typed model
names, not ten newly admitted software families. Only exact GCTA 1.94.1 runtime
profiles are supported. There is no engine, edition or algorithm fallback.

The normal build and 16 focused tests pass. The normal JAR preserves all 23,530
prior payloads byte for byte and adds eleven workflow class files. The same 16
tests pass with that JAR first on the classpath on Windows Java 21, Windows
Java 25 and Linux Java 21. Windows Java 21 compiled the source; independent Linux
compilation is **SKIP3 because Linux javac is absent**, not a claimed pass.

On each of Windows Java 21 and Linux Java 21, 33 normal-JAR request cases give
28 complete successes, four deliberate FAIL2 refusals and one deliberate
edition SKIP3. All 28 successful captures per host pass separate-JVM VERIFY.
The failures reject unsupported 1/2 binary coding and three original GRM
zero/undefined-count cases. Actual cross-host checks and replay receipts, when
present, are listed in [the saved checkpoint profile](gcta-workflow-development/PROFILE.json).
Read its precise scope rather than treating these checks as complete model
qualification. The [evidence reader](../../tools/literature/gcta_workflows/verify_checkpoint.py)
checks saved bytes and receipts; it does not rerun a model.

Complete available original outputs are retained, including COJO LD and
conditioning exports; REML fixed effects, full logged variance-component
sampling covariance and all BLP columns (including the residual component);
and the binary fastGWA null-model binary, IDs and every decoded model-array
entry. Internal state that upstream never exports is not invented. Raw inputs,
null results, errors, partials and old failed attempts remain available.

## Warnings and exact recovery

- **SKIP3: dependent analysis skipped.** Supply the named exact permitted
  runtime/input and use a new output directory. If installation is unavailable
  or forbidden, retain the skip. Do not substitute a nearby release, beta or
  new JAR hash. Missing original runtime is not permission to use native Java.
- **FAIL2: NO COMPLETE RESULT.** Read `analysis-status.json`, `WARNINGS.md`,
  `manifest.json`, available `model-objects.json` and every worker stdout,
  stderr and log. Preserve all objects and partials. Correct the stated data,
  source, integrity, numeric, convergence, capacity or process cause and retry
  into a **new** output directory. Never present partial rows as a full result.
- **Unimplemented work stays unfinished.** mtCOJO, other editions, global
  discovery and catalog admission have not been completed. Their absence is
  not a successful analysis or a fabricated dependency skip.
- **Prepared-cohort restriction.** Current phenotype and covariate inputs must
  describe the same complete literal FID/IID cohort as their genotype/GRM
  inputs. No automatic missing-value filtering, imputation, allele or strand
  repair, ID rewriting, population change, or phenotype recoding occurs.
  fastGWA requires explicit `binary_coding: "0/1"`. Preserve other encodings;
  prepare a separately documented 0/1 input before requesting that profile.
- **Source numbers remain source numbers.** Valid converged unconstrained
  REML variance/h2 can be negative or exceed one. COJO effective n is a positive
  real and may exceed the input sample count. Adjusted LD scores can be negative.
  None is clamped or rounded into a different scientific quantity.
- **Printed precision matters.** Original REML variance/SE tables use six fixed
  decimals; small nonzero internal values can print as zero. Full scientific
  covariance and source h2 exports are preserved. Do not infer exact internal
  zeros or invent missing precision. The first independent audit tried expected
  Fisher covariance and disagreed; the source uses average-information REML.
  That failed interpretation is retained. A source-correct independent AI
  covariance follow-up is still required.
- **Runtime flavor matters.** The 512-sample / 4,096-marker binary fixture
  succeeds on both original runtimes with seed 2026, but its calibration differs:
  Windows c_inf 0.7571667038620485; Linux 0.7576320533521899. Loaded-model and
  direct association tokens match within each host. Cross-flavor replay
  requires explicit `allow_runtime_flavor_change: true` and the exact runtime;
  it does not promise identical numeric results. Do not replace SPA P values
  with their unadjusted counterparts.
- **Verification has a limit.** Original-model VERIFY checks complete capture
  inventory, deterministic intake and declared export invariants. It does not
  independently refit original models or authenticate coordinated replacement.
  Native GRM is recomputed. SHA-256 is integrity, not a signature.
- **Capacity is not an OS quota.** Request byte, metadata, count and logical
  Java-work limits are explicit. External output polling and timeouts can
  overshoot between checks; they are not operating-system memory/file quotas.
- **Private transfer is required before changing machines.** GitHub contains
  source and selected synthetic evidence, not the complete machine state.
  Transfer the paths below with a checksummed inventory, verify destination
  bytes, and preserve all historical owners and failed/superseded evidence.

## Next actions, in priority order

1. **Finish qualification of these prepared-data workflows.** Compare all
   positive exports with the corresponding untouched originals, including full
   COJO LD/conditioning tables, all REML BLP/component exports and both fastGWA
   null models. Complete the independent AI-covariance point check, retaining
   the earlier Fisher mismatch. Add meaningful malformed-model, nonconvergence,
   SPA, timeout/owned-process cleanup, output-cap, integrity, alias and replay
   inverse controls. Existing JUnit and handover checks are a bounded starting
   point, not proof that every such case has been qualified. Retain all failed
   attempts; use a fresh build and evidence directory after source changes.
2. **Qualify mtCOJO and paper-specific editions.** Build its separate LD-score,
   conditioning and prevalence contract and run an actual successful original
   analysis. Resolve 1.92.4, 1.93.2, 1.93.0, and P140's stated 1.91.7beta/binary
   fastGWA combination. Do not silently use 1.94.1. Resolve P171's wheat ploidy
   preparation and P248's publisher formula; preserve literal thresholds,
   units, allele, ancestry and flag-case statements. See the
   [22-candidate source census](gcta-development/SOURCE-NOTES.md).
3. **Register the global command and perform guarded catalog admission.**
   Preserve the exact existing dispatcher and historical GSEA owner. Establish
   new shared-source compatibility evidence rather than refreshing old hashes.
   Review the 18 execution candidates separately from four non-execution
   contexts. Add missed P112 only with its mtCOJO role and retain exact-before
   evidence for proposed P127 comparison-only removal. Do not count 17 GCTA
   plus nine COJO observations as 26 independent studies/packages. Preserve all
   1,548 earlier reviews; run plan/inspect/apply/fresh verify and required report
   checks before changing counts.
4. **Continue high-impact missing families and the 1,166 remaining reviews.**
   Recheck the current inventory, then prioritize limma (16 observations), PEER
   (15) and TwoSampleMR (12). Implement tractable methods in Java and qualify
   exact upstream adapters where appropriate. Prism/FlowJo/FACSDiva need a
   meaningful permitted scientific scope. Do not replace GUI names with
   superficial substitutes. Cell Ranger remains excluded by user instruction.
5. **Keep publication and preservation boundaries.** Both literature HTML
   reports remain withheld from Pages. Do not publish them incidentally. No
   release was requested. Merge current remote main without force if it has
   advanced, validate a selected snapshot, and preserve later concurrent edits.

## Exact resume and transfer paths

- Active checkout: `D:/projects/JLinAlg/build/gc-3`, branch
  `codex/gcta-file-workflows-20261120`; initial baseline
  `6f4e5591e997b16f4183333c5cc7d6ab66cec4d0`.
- Current private evidence: `D:/projects/JLinAlg/build/gc-3/build/gcta-workflows-1`.
  `normal-gradle-3` is the current source/normal-build owner;
  `normal-workflow-runs-1` holds both hosts' requests and complete/failed/skipped
  captures. Keep `normal-gradle-1` (genuine serial UID build failure), both
  earlier compile owners and `initial-workflow-runs-1` unchanged. Initial
  prototype captures belong to their saved compile-2 source, not the new JAR.
- Exact current producer:
  `normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar` under that evidence
  root; 195,985,654 bytes; SHA-256
  `37a064b42ea58d52e7446eb207346d2731edbc188f3b8df7008cfeba0a4024a8`.
- Qualified native GRM owner: `D:/projects/JLinAlg/build/gc-2` at baseline above;
  private evidence `build/gcta-native-1`. Do not alter its qualified API,
  fixture corpus, source hashes or cancellation-failure evidence.
- Original source/runtime/intake owner: `D:/projects/JLinAlg/build/gc-1` at
  `23f5b5008e4e9e716e69a5c2c260e7764293ce4b`; private
  `build/gcta-high-impact-1/upstream-1.94.1-intake-1`. Original GCTA source commit
  is `6da64e9d8be838d01c34efb974e69c72e861f4c2`. Keep `original-fixtures-2`,
  `mixed-fixtures-1`, both core attempts and `original-advanced-1`.
- Current GSEA catalog owner: `D:/projects/JLinAlg/build/gs-6`; its producer and
  historical dispatcher owners are linked in the
  [GSEA catalog checkpoint](GSEA_CATALOG_CHECKPOINT_2026-11-20.md).
- Preserve the frozen full-text cache `D:/projects/JLinAlg/build/literature-review/xml`,
  exact source supplements (including private P077), runtime installations,
  producer JARs, recovery archives and all 25 worktrees. Publication receipts
  are under `D:/projects/JLinAlg/build/git-publish`; the current publication
  follows the `20261120-gcta-native-68` checkpoint.

Two historical full-marker checks remain **SKIP3** until their exact
164,898,617-byte producer is restored, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
A separate unlocated retired 193,411,520-byte producer has SHA-256
`9955f022aa4a418a7d16ba248d2c40e3bf9588f2a3942728b48262065bbc4cb7`.
Supply an exact permitted backup or keep dependent checks skipped. Never
substitute a new producer or silently refresh the saved hash.

The [minimal native example](../../examples/gcta-workflows/README.md) provides
copyable commands. The [checkpoint README](gcta-workflow-development/README.md)
describes public synthetic evidence and private-transfer exclusions. Earlier
handovers remain history; use this task list for the current snapshot.
