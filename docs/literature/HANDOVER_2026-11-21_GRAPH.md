# Toolbox handover: Monocle graph draft and remaining high-impact work

**The 250-paper collection is complete. The implementation and paper-use review
goal is unfinished.** The catalog remains **144 scoped families (26 native Java
+ 118 upstream), 1,630 reviewed uses and 1,092 pending uses**. Including three
existing families gives 147 integrations. There are 2,722 observations, 446 named
labels, 485 registry definitions, 259 missing labels and 29 method-overlap labels.
Aliases receive no extra family credit. The frozen collection window remains
2023-10-23 through 2026-10-23: 71 Nature, 104 Nature Genetics and 75 AJHG papers.
This handover changes no catalog counts and does not refresh that window.

**WARNING: THE NEW GRAPH ADAPTER IS A DEVELOPMENT DRAFT.** It has no completed
normal-JAR qualification, packaged-host validation, public example qualification,
failure/lifetime matrix or complete comparison with the independent reference
cases. Its source is saved outside the compiled tree. The last qualified
executable sources remain unchanged. Do not report this draft as an implemented
catalog family, a paper reproduction, a gene test or a biological trajectory.

## Qualified baseline and current development

The baseline is GitHub main `ff9fc9bb11315b2d32cb095e6103ebf1a12c9f59`.
The [preceding neighbor checkpoint](MONOCLE100_NEIGHBORS_CHECKPOINT_2026-11-21.md)
records 716 focused tests per normal build and Windows Java21/25 and Linux Java21
packaged JVM; 66 expected neighbor outcomes (50 graphs, 16 retained failures);
independent original comparisons of 66 nearest-neighbor returns, 25 listw objects,
eight errors and 66 RNG records; two public examples/replays; and five actual
no-result controls plus eight process-only controls. Those are recorded preceding
results. This handover does not rerun or extend that scientific qualification.

The new work concerns **Monocle3 package version 1.0.0**, original commit
`004c096f3a3bdaf1e6709a65fb36f40ba775f1ab`, not Monocle generation 1. It proposes
standalone RUN/VERIFY/REPLAY with explicit LEARN, ORDER or combined RUN operations.
Inputs must be complete, caller-prepared original-edition CDS objects with literal
UMAP coordinates, clusters and partitions. It does not preprocess raw counts,
compute an embedding or cluster cells. Roots, provenance, convergence policy and
learning controls must be explicit. The unchanged original R functions learn/order
the graph; existing native Java pseudotime independently checks the exported
ordering. No automatic engine or edition substitution is proposed.

Four new Java classes and an extension of the shared neighbor runtime passed
quick strict Java17 compilation (`--release 17 -Xlint:all -Werror`). The shared
runtime extension selects the graph profile and 18 packages; its previous default
neighbor package list/profile remain unchanged. This extension still requires
neighbor regression validation before integration. A normal packaged build has
not been made for the graph draft.

Development evidence under `build/mo-4/build/monocle100-graph-1` records:

- `probe-1`: unchanged original graph learning and explicit-root ordering on a
  complete synthetic 27-cell, three-gene CDS; five principal points and four edges.
- `worker-smoke-1` through `worker-smoke-6`: retained failures in the adapter's
  scientific-state serializer/invariant checker. Fixes use `methods::slot` for S4
  NULL sentinels, `unclass` before traversing version objects, and normalization of
  names on empty masked holders only. Original CDS contents are not repaired.
- `worker-smoke-7`: all three direct-worker operations pass, including complete
  scientific-state RDS round trips and preserved input invariants. This is an
  earlier worker snapshot, not final-code qualification.
- `pilot-learn-1` and `pilot-run-1`: quick-compiled Java development pilots exit0.
  RUN retains all five points and matches native pseudotime (483 operations).
- `reference-inputs-1`: six complete synthetic inputs, including circle geometry,
  alternating disconnected partitions, source-invalid partition labels, uniform
  coordinates and scaled coordinates. No paper data are used.
- `original-reference-1`: 21 direct unchanged-original reference cases completed
  with a terminal exit0 receipt. They define 16 expected successful adapter exits,
  three original errors and two explicit convergence-policy refusals. This is an
  oracle preparation result, **not a 21-case adapter pass**. The circle case did
  not satisfy the requested convergence policy; preserve that outcome.

**The last two source edits postdate the successful pilots:** the error handler
now records RNG state at the error, and the graph profile now selects 18 packages.
Do not reseal or refresh hashes in old captures to match these edits. Create new
final-source captures. No current final-source RUN/VERIFY/REPLAY has been qualified.

## Ordered next actions

1. Resume from the preserved graph draft and exact original reference results.
   Verify restoration hashes before copying to `src/main` in a NEW worktree based
   on current main. The original dirty `build/mo-4` owner remains intact and can
   also be used for continuation. Never reset or clean the older root checkout.
2. Run fresh final-source LEARN, ORDER and RUN plus R-free VERIFY and real REPLAY.
   Then execute the complete 21-case PLAN through the adapter. Compare every
   expected result, error and convergence refusal. Do not infer a successful
   adapter matrix from the direct-original oracle's exit0.
3. Implement an independent complete-object comparator for learned/output CDS,
   original ordering and RNG/error class/message/call. Compare all S4 slots and
   S3 attributes; ignore only igraph's private cache representation. Traverse S4
   through `slot` and S3 lists through `unclass`. Never source the production
   worker as the independent oracle. Policy refusals are not original exceptions.
4. Add meaningful Java contract/capture tests from fresh genuine captures:
   operation-specific required/forbidden fields, literal UMAT refusal, explicit
   roots including repetitions, complete axes, disconnected-cell Inf, unusual
   unused principal-edge metadata, convergence policy, caps, missing completion
   markers, changed source and resealed semantic contradictions. Preserve errors.
5. Build the normal executable using the repository's strict Java17 conventions.
   Run the existing 716 focused controls plus the new graph controls, and actual
   packaged tests on Windows21/25 and Linux21. Compare every preceding JAR member.
   Only the explicitly reviewed shared-runtime classes and new graph members may
   change. Re-run the neighbor examples, real replays and five actual refusals to
   cover the shared runner. Use the untouched actual `build/mo-3` owner for its
   frozen P94 saved reader; do not refresh old hashes for new files.
6. Qualify an actual public graph example with explicit prepared input and roots,
   LEARN/ORDER or combined RUN, three-host VERIFY and complete-object REPLAY.
   Check real timeout, cancellation, Java-parent death, capacity and console
   failure before result admission. The shared Python supervisor is unchanged;
   its old process-only evidence is retained, not new graph qualification.
7. If bridging a newly learned graph into neighbor weights/MORAN-LM, generate a
   fresh original-spdep reference. Old fixed-graph weight/inference expectations
   cannot establish the new graph's results. Never substitute stock Moran for
   P021's modified covariate test.
8. Refresh the all-250-paper/nine-Monocle-role and current catalog audits. Evaluate
   bounded Monocle admission against the actual editions and roles below. The
   nine source summaries are ALREADY in the reviewed ledger: do not add nine
   duplicate reviews. Correct stale scope notes through a guarded transition.
9. Continue the remaining 1,092 individual-use reviews and higher-impact gaps.
   Candidate counts from the retained priority audit: QTLtools(9), SKAT-O(8),
   ACAT(7), FUSION(7), MR-PRESSO(6), SKAT(6), fastQTL(5), GenomicSEM(5), PRSice(5).
   Inspect existing implementations and actual paper roles/editions first.
   Prefer native Java for straightforward methods and tested upstream adapters
   for substantial external software. Keep aliases at zero extra family credit.
10. Publish a narrowly scoped checkpoint only after those required checks pass.
    The overarching 250-paper implementation goal remains unfinished.

Important source behavior: original `learn_graph` and `order_cells` hardcode UMAP;
`rann.k=NULL` maps to 25 in the outer original function; `scale=TRUE` follows the
original coordinate behavior. Preserve literal partition labels and source
failures. Require explicit `require` or `retain-original` convergence policy;
never silently admit a one-iteration or nonzero-kmeans-ifault result. Keep original
nonfinite unused principal-edge attributes separate from the finite nonnegative
projected graph used for pseudotime. Repeated roots follow the original behavior.

## Monocle role boundaries to retain

- P021: version1.0.0, paper says **UMAT**. Never rename it to UMAP. The modified
  covariate Moran test and author executed commit/license remain unresolved;
  private source inspection does not authorize execution or redistribution.
- P024: py-monocle is a separate Python implementation; R/native controls do not
  qualify it. The 200 Braak-selected roots and exact roles matter. The existing
  research/nonprofit preference already answers the use-category question;
  inspect actual current source terms rather than asking that category again.
- P043: Cicero/platform context, mm10/500kb and source filtering details; do not
  turn this into a trajectory claim.
- P051: Monocle1.3.7/Batchelor1.20.0; existing ALIGN adapter covers part of scope.
- P085: Monocle1.3.1 gene GLM; original quasi-Poisson/t coefficients/grouped Holm
  differ from documentation BH wording. Pearson is separate. Old runtime-failure
  catalog wording may be stale; preserve provenance when correcting it.
- P099: Monocle2.26.0 differential testing remains partial; exact patch, formula
  and units are unresolved. Retained source-family failures are unadmitted.
- P117: Monocle1.3.3, graph controls130/140/150, branch15/20, no loops; root unknown.
- P134: protein activity, not raw RNA; exact patch unresolved.
- P233: condition-specific Seurat conversion; do not pool conditions silently.

Existing `monocle-tools` already includes ORDER/RUN1.4.27, MODELS1.3.1,
GRAPH1.3.3, ALIGN1.3.7, DIFFERENTIAL2.26.0 and MORAN-LM. Reuse established pieces.
The [catalog handover](HANDOVER_2026-11-21_CATALOG.md) has the wider source backlog.

## Resume paths and preserved material

Project root: `D:/projects/JLinAlg`. Original graph owner: `build/mo-4`, branch
`codex/monocle100-graph-20261121`, based on the baseline above. Handover publication
owner: `build/handover-95`, branch `codex/handover-20261121-graph`. There are now
**52 worktrees**, including all 51 original owners. The originals are preserved.
Publication receipts: `build/git-publish/20261121-handover-95`.

The [saved handover packet](handover-2026-11-21-graph/README.md) preserves every
Git-visible dirty variant and staged patch from those worktrees, with baseline
Git blobs or SHA256-named archive blobs. This saves competing variants without
promoting stale catalogs or unfinished scientific source into the main build.
The [draft directory](../../tools/literature/monocle100_graph/development/README.md)
contains byte-exact code and a restoration map. Intake/probe/reference runners
and their original commands/source snapshots are retained in the packet.

```powershell
python -B tools/literature/monocle100_graph/verify_handover.py --out build/NEW-graph-handover-check
```

Use Python3.11+ and Git. Add `--restore` to extract retained evidence and worktree
variants under NEW output, without applying patches or changing original owners.
In a Linux clone use native Git. Under WSL on this linked Windows checkout add
`--git '/mnt/c/Program Files/Git/cmd/git.exe'`. This reader checks saved bytes and
recorded scope; it runs no R/Java/model and does not prove scientific correctness.

Private exact original library:
`D:/projects/JLinAlg/build/mo-1/build/monocle100-1/install-1/R-lib`.
Readiness receipt: `build/mo-1/build/monocle100-1/install-1/RESULT.json`.
R4.3.3 base environment:
`build/literature-review/monocle-runtime/monocle133-reference-setup-1/.pixi/envs/default`.
Rscript is `bin/Rscript`, R home `lib/R`, native libraries `lib`, base R library
`lib/R/library`. Use `/mnt/d/...` paths in Linux. WSL distributions are
`JLinAlg-Omics-SAIGE-20261026` for original R controls and
`JLinAlg-Omics-PyMOL-20261026` for Linux Java/Python execution.
The selected dependency versions are in the saved graph PROFILE; they do not
establish a reconstructed paper-era environment. Windows R4.6.1 syntax parsing is
not selected-runtime scientific execution.

Last qualified normal producer:
`build/mo-3/build/monocle100-neighbors-qualification-1/normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar`.
Size197178192; SHA256
`e5955aefcf43282e8f491a80e60542e7fe4a29412e1a075574bffebef398f546`.
Keep this artifact and the previous original-source/runtime receipts intact.

## Warnings and required actions

**GITHUB IS NOT A COMPLETE MACHINE BACKUP. Before retiring this machine, transfer
ignored runtimes, JARs, full text, author source, ignored outputs and complete
private evidence separately, then verify SHA256 at the destination.** The packet's
`PRIVATE-TRANSFER.json` pins current omissions and important artifact paths. Follow
the linked earlier transfer records for the wider project. Preserve raw, null,
failed, invalidated and superseded evidence. Restore into NEW locations.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the exact named permitted runtime,
original-edition input or accessible executable path. Download/private installation
authorization persists where allowed by the operating environment. If installation
or execution is prohibited, retain the skip, explain the specific cause and what
the user must supply, and continue independent work. Analysis commands do not
install runtimes automatically.

**FAIL2: NO COMPLETE RESULT.** Preserve errors/logs/partial output. Correct the
named source, input, numeric, capacity or process issue and retry NEW output.
Require exit0, `COMPLETED.json` and explicit admitted analysis before accepting a
result. A graph or pseudotime is not a gene test or verified biological direction.
Do not weaken guards, recode identifiers, drop cells or replace editions to pass.

Research/educational/nonprofit use and the separate GATK3.8 academic noncommercial
terms were already accepted. **Cell Ranger is explicitly excluded.** Investigate
current iDEP instead of assuming current terms cover legacy iDEP.96. Private
author source, GPL-2-only SLAM intake and legacy startup configuration stay private.

Both unfinished literature reports remain off Pages. The inherited full-site
citation gate still has missing `broad-gsea-tools.md` citations; this handover
claims no full-site pass, release, GPU qualification or additional catalog credit.
Six descriptive inventory/registry differences remain recorded. The full goal is
unfinished; this commit is the requested handover and main-branch push.
