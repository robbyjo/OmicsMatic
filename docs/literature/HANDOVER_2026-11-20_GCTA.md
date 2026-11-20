# Toolbox handover: GCTA prototype and remaining work

**The 250-paper collection is complete. Implementation and individual source
review remain unfinished.** The admitted catalog remains at **140 scoped
integrations: 26 native Java subsets and 114 upstream adapters**, or 143 including
three pre-existing adapters. There are 2,714 observations: **1,548 reviewed and
1,166 pending**. GCTA/COJO receives **zero new credit** in this handover.

The scientific/catalog baseline is main
`bdf1c3e8067896131d31cbca3e7de9191aa93a0d` (GSEA admitted). This handover adds a
development Java GRM prototype, reproducible five-case checks and saved research
evidence. It does not change canonical metadata, existing scientific classes,
the global dispatcher, website sources or the 826-entry catalog manifest.

The cohort remains frozen at **23 October 2023–23 October 2026**, with 71 Nature,
104 Nature Genetics and 75 AJHG papers. No time extension was needed. Do not
silently slide the window during continuation.

## Read these warnings before continuing

- **GCTA integration is unfinished.** The prototype handles prepared autosomal
  diploid hard calls and source 1.94.1 GRM algorithms 0/1. There is no GCTA file
  workflow, global command, qualified adapter or catalog admission. A normal
  project producer JAR and meaningful edge/refusal JUnit corpus have not been
  built for this addition. Do not present it as complete GCTA, COJO or a paper
  reproduction.
- **FAIL2: NO COMPLETE RESULT — fastGWA.** Both Windows and Linux original
  binary-association attempts terminate with `can't read 2000 SNPs from the
  autosomes for tuning`. The fixture has only 768 variants. Preserve both
  failures and all null-fit diagnostics; generate a separate sufficiently large
  fixture and retry in a **new** output directory. The earlier null-fit
  convergence message does not make this a complete association result.
- **SKIP3 — exact prerequisite unavailable.** Recover the exact permitted
  runtime, input or JAR named by a dependent check; otherwise skip that analysis
  and state precisely what the user must supply. Never silently upgrade an
  edition or refresh an old producer hash. Installation/download is authorized
  where the environment permits. Cell Ranger remains excluded by the user.
- **FAIL2: NO COMPLETE RESULT — invalid input, source/integrity mismatch,
  numerical/convergence/process/capacity failure.** Retain original inputs,
  available model/null objects, conditions, partial files and diagnostics.
  Correct the stated cause and retry in new output. Do not repair IDs, alleles,
  populations, thresholds, nonfinite values, editions or algorithms silently.
- **Do not clamp valid unconstrained estimates.** The original synthetic REML
  run converges with V(G) = -0.229400 and h2 = -0.335983 on both hosts; source
  semantics and independent model checks remain to be qualified. P238
  explicitly permits estimates outside 0–1. A bound crossing alone is not a
  numerical failure. Pairwise-missing source GRMs can also be indefinite;
  changing them to positive semidefinite changes the method.
- **Transfer private artifacts separately before moving machines.** GitHub
  contains the selected source, handover and synthetic evidence, not ignored
  original runtimes, producer JARs, complete XML/PDF caches or the full private
  evidence store. Preserve all **23 current worktrees**, including unrelated
  dirty local revisions. Do not clean/reset worktrees or apply stale overlays.

## What has been saved and checked

The Java prototype is
[`GctaHardcallGrm.java`](../../src/main/java/org/jlinalg/genetics/GctaHardcallGrm.java).
It preserves source pair-specific called-marker counts, missing-call behavior,
128-marker blocks and algorithm-1 float rounding. Composite FID/IID identities
remain ordered and literal. The strict boundary refuses source zero fallback
for a sample pair with no jointly called retained markers. This narrower
behavior is explicit, not full source equivalence.

Five saved original-runtime cases cover complete/missing algorithm 0,
complete algorithm 1 and two marker subsets. **185,280 float32 fields match
exactly per JVM**, on Windows Java 21/25 and Linux Java 21. The final probe
recompiles with Windows JDK 21; the other two final executions use its exact
pinned class files. Linux `/usr/bin/javac` is absent, so Linux source compilation
is **SKIP3** until a permitted JDK is supplied. Linux runtime comparison still
passes using those pinned source-built classes. This does not establish the
unimplemented edge corpus, full workflow, normal JAR or complete tool.

Actual original GCTA 1.94.1 Windows/Linux exploration is retained:

- Nine core cases per host exit zero: five GRMs and four COJO selection,
  joint, conditional and coherent allele-reversal cases. GRM/N binary outputs
  match between hosts; COJO scientific text tokens match across line endings.
  COJO's estimated effective `n` can be fractional and exceed the input N:
  preserve it rather than coercing it to a bounded integer.
- Nine advanced attempts per host include PCA, positive/unconstrained/multi-GRM
  REML, three LD-score modes, sparse GRM and binary fastGWA. Eight exit zero;
  fastGWA fails as described above. Full scientific/semantic qualification of
  these advanced outputs remains open; exit zero is insufficient.
- An earlier fixture setup failed because SciPy was unavailable. Its 18
  downstream missing-input attempts remain retained under `original-core-1`.
  The corrected generator uses standard `math.erfc` for the stated normal
  Wald P-value definition. Corrected runners verify input hashes before launch.

See [source roles and edition constraints](gcta-development/SOURCE-NOTES.md),
[prototype profile](gcta-development/PROTOTYPE.json),
[synthetic archive inventory](gcta-development/SYNTHETIC-INVENTORY.json) and
[development evidence and commands](gcta-development/README.md).
The archive contains synthetic inputs, original outputs and failures, initial
native captures and the probe source. Original runtimes and primary paper
full text are deliberately separate. SHA-256 provides integrity, not a publisher
signature. Historical orchestration scripts are saved with their original
machine-specific paths; inspect them before reuse and select new output paths.

## Next work, in priority order

1. **Finish the GCTA source-bound native GRM component.** Add independent
   original controls for missing calls with algorithm 1; 127/128/129 marker
   boundaries; monomorphic/all-missing markers; rare/major allele coding;
   indefinite pairwise GRMs; and empty-pair refusal. Test duplicate/composite/
   Unicode identities, malformed hard calls, capacity before allocation,
   cancellation, defensive results and dimension failures. Use meaningful
   focused JUnit tests, then a normal producer build and payload comparison.
2. **Qualify original model behavior before wrapping it.** Check REML
   convergence, complete fixed effects/BLUP and variance results, multi-GRM
   identity/order, PCA eigenvectors/subspaces, LD-score window/adjustment units,
   COJO source filtering/effective sample size and the failed fastGWA retry.
   mtCOJO needs a separate contract for LD scores, conditioning inputs and
   binary prevalences. Existing generic conditional/GRM components have
   different semantics and cannot be relabeled GCTA.
3. **Implement explicit native/upstream file workflows.** Define typed modes,
   exact runtime profiles, bounded prepared inputs, prominent SKIP3/FAIL2
   warnings and complete retained diagnostic/model results. Add closed
   RUN/VERIFY/REPLAY and actual cross-host verification/replay, invalid-result
   controls and normal-JAR examples. Never substitute a model or runtime.
4. **Resolve reported editions and source ambiguities.** 1.92.4, 1.93.2 and
   1.93.0 have not been resolved to exact release assets/source profiles.
   Confirm P140's stated 1.91.7beta/binary-fastGWA combination. Keep P171 wheat
   genotype/ploidy preparation unresolved; do not assume human diploidy. P248's
   XML formula lacks a fraction operator; recover the publisher formula rather
   than repairing it from a nearby numeric cutoff. Preserve literal MAF/percent,
   flag-case, window and ancestry statements in the source-role table.
5. **Perform guarded GCTA/COJO catalog admission only after qualification.**
   The all-250 intake finds 22 candidates: 18 execution candidates and four
   non-execution contexts. P112's full-name mtCOJO role is missed by existing
   observations; P127's unreviewed COJO comparison is a proposed false-positive
   removal. Preserve exact-before metadata and all 1,548 prior reviews. Do not
   count 17 GCTA plus nine COJO observations as 26 papers or two independent
   software packages. Run plan/inspect/apply/fresh verify, source/compatibility
   and refusal controls, and report UI checks before changing totals.
6. **Continue the high-impact backlog and remaining individual reviews.**
   After GCTA/COJO, inspect current gaps for limma (16 observations), PEER (15)
   and TwoSampleMR (12), preserving exact source roles/editions. Higher-count
   Prism/FlowJo/FACSDiva mentions need a meaningful permitted scientific scope,
   not superficial GUI substitutes. The catalog still reports 259 missing
   definitions and 35 method-overlap definitions; these are not completed tools.

GSEA is already admitted. Do not repeat its implementation or apply older
admission-open instructions. Future changes to the global dispatcher or shared
catalog need exact new compatibility evidence or their qualified historical
owner checkout; do not refresh old owner hashes to make checks pass.

## Resume paths and machine transfer

Current development checkout:
`D:/projects/JLinAlg/build/gc-1`, branch
`codex/gcta-high-impact-20261120`.
Private evidence root:
`D:/projects/JLinAlg/build/gc-1/build/gcta-high-impact-1`.
Relevant children are `upstream-1.94.1-intake-1`, `source-intake-1`,
`original-fixtures-2`, `mixed-fixtures-1`, `original-core-1`, `original-core-2`,
`original-advanced-1`, `native-grm-initial-1` and `handover-final-*`.

The latest qualified GSEA catalog owner is
`D:/projects/JLinAlg/build/gs-6`. Its predecessor source and producer paths are
listed in the [GSEA checkpoint](GSEA_CATALOG_CHECKPOINT_2026-11-20.md).
The exact P077 supplemental PDF remains private at
`D:/projects/JLinAlg/build/gsea-author-followup-20261120-1/P077-EPMC-supplements/P077-supplement.pdf`.
The complete frozen XML cache is `D:/projects/JLinAlg/build/literature-review/xml`.
Keep these exact source identities and all historical/superseded/failed evidence.

Two historical full-marker checks remain **SKIP3** pending their original
164,898,617-byte JAR, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
A separate retired 193,411,520-byte producer, SHA-256
`9955f022aa4a418a7d16ba248d2c40e3bf9588f2a3942728b48262065bbc4cb7`,
also remains unlocated. Supply an exact permitted backup or keep its dependent
checks skipped. The prior reconciled cleanup receipts identify retired caches
and recovery archives; do not recreate old worktrees or delete retained inputs.

Before moving machines, transfer the complete private evidence/runtime stores
and preserved local worktree revisions with a checksummed inventory, verify
the destination, and then resume in a new output directory. GitHub alone does
not accomplish that transfer. No release has been requested. Both literature
HTML reports remain withheld from Pages; preserve that publication boundary.
