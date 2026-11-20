# Toolbox handover: native GSEA engine checkpoint

**Resume here. The 250-paper collection is complete; implementation and review
are unfinished. The new GSEA Java library is implemented and checked. Its
project file workflow, RUN/VERIFY/REPLAY, global command and catalog admission
remain open. Do not count this checkpoint as another admitted tool.**

The predecessor main is `7f4cc3185a642c61c7ab42b8b84a2630fac1fec7`.
This handover and the engine are committed together in the next main checkpoint;
use Git history for its exact commit rather than a self-referential SHA here.
The active development checkout is `D:/projects/JLinAlg/build/gs-3`, branch
`codex/gsea-engine-20261120`. All 31 worktrees are retained. Start new work from
the latest GitHub main in a new isolated checkout; preserve this checkpoint.

## Current counts and completed work

The frozen collection contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG, in the original 2023-10-23 through 2026-10-23 window. No extension was
needed. It contains 2,705 tool-use observations, with **1,520 reviewed and 1,185
pending**. There are **139 admitted scoped integrations: 25 native Java and 114
upstream**, or 142 including the three pre-existing integrations. The inventory
has 446 named/485 effective definitions, 259 missing and 36 method overlaps.
The canonical manifest has 823 entries. These counts are unchanged here.

The latest admitted family is edgeR at `0de612c6`. The preceding GSEA checkpoint
already qualified the native score walk and empirical-statistics arithmetic.
The new `BroadGseaEngine` adds prepared-data execution:

- Preranked, expression/phenotype permutation and expression/gene-set permutation
  analyses, including all supplied raw null scores and complete-family statistics.
- Five categorical ranking metrics: signal-to-noise, t-test, difference, ratio and
  log2 ratio; explicit median, biased variance, low-variance floor, absolute sort,
  ascending order and positive-class selection.
- Original no-balance, balance-class0, balance-class1 and equalize-and-balance
  randomization, exact seed handling, ordered class memberships and source set
  filtering/member order. Weighting includes classic, weighted, p2 and p1.5 within
  the strictly admitted finite branches.
- Explicit dense/null/work/permutation limits, defensive result arrays and
  progress callbacks. Structured `Failure` identifies a failed numerical phase,
  set and permutation where available. Input validation and some capacity failures
  may occur before that wrapper. The caller owns input retention and persistence.

This library uses native Java with no vendor process. It does not import vendor
file formats, transform expression, map identifiers, retrieve gene sets, collapse
probes or produce vendor marker/GUI reports. Its explicit source profiles are
4.3.2, 4.3.3 and 4.4.0; the two old source tags resolve to the same commit but were
built separately. A profile label is not a claim of byte identity with vendor
distributions. The original BSD notice remains in the packaged license resource.

## Validation retained with this checkpoint

The normal executable JAR builds successfully; both focused regression classes
pass (three tests total). Every one of the **23,490 predecessor JAR entries** is
unchanged; only engine classes are added. The native engine is then loaded from
that exact normal JAR, without an original GSEA runtime on its classpath, on
Windows Java 21, Windows Java 25 and Linux Java 21.

Each JVM passes:

- Nine original source compositions, three forms per profile and 10,000
  permutations per form: **468,270 exact field comparisons**, including every
  raw null ES, rank, hit/profile and family statistic.
- A separate 208-case option/boundary corpus per source profile: **423 admitted
  cases, 201 expected strict refusals and 200,469 exact field comparisons** in
  aggregate across the three profiles. This includes full-universe sets,
  float32 ties with different double metrics, hash collisions, low-variance
  floors, class-member ordering and source filter/randomizer boundaries.

Thus each host checks 668,739 fields plus 201 expected refusals. These synthetic
checks qualify the stated library scope, not whole-tool behavior, author data,
biological conclusions or the unfinished end-user workflow. The reference sweep
runs both stock and audited original scoring tables and compares exported
mathematical objects; the observer does not alter admitted source results.

Read the [saved qualification](gsea-engine-development/README.md) and
[reference/checker instructions](../../tools/literature/gsea_engine/README.md).
The source corpus, complete-null synthetic references, build/test logs, packaged
checks, source versions and local orchestration scripts are committed. The saved
reader checks integrity and counters, without rerunning scientific models:

```powershell
python -B tools/literature/verify_broad_gsea_engine_checkpoint.py
.\gradlew.bat :test --tests org.jlinalg.enrichment.BroadGseaEngineTest --tests org.jlinalg.enrichment.BroadGseaStatisticsTest
```

The earlier GSEA arithmetic reader, historical-overlay verifier and unchanged
edgeR catalog owner have also been read back for publication. The first invocation
of the old arithmetic reader omitted its mandatory `--out` argument and returned
a usage error; that attempt is retained, and the corrected invocation succeeds.
No scientific result or old source hash was changed to resolve it.

## Warnings and required actions

**GitHub does not contain ignored runtimes, the executable producer JAR, cached
full texts or complete private evidence. Before changing machines, separately
transfer the paths below, publication receipts and all scientific evidence with
their manifests. Do not treat this source push as their backup.**

**There is no ready GSEA project RUN/VERIFY/REPLAY command.** Users of the new
class must provide prepared literal identifiers, finite float32 data and explicit
parameters through the Java API. Do not advertise end-user file execution until
the next tasks pass. Library cell/work limits are not operating-system memory,
CPU, wall-time or output containment.

**Do not silently repair source boundaries.** Duplicate identifiers/members,
nonfinite rankings, invalid weight mass, undefined statistics, impossible
randomizer counts and declared-capacity failures produce no complete result.
The engine refuses original substitution branches rather than applying source
fallback constants. Retain the exact inputs, diagnostics and emitted partials.
The source's `min == max` setting disables both size filtering and namespace
projection; it does not mean select exactly that size. The source's
`equalize_and_balance` does not equalize class sizes. Preserve these literal
behaviors and explicit class order/orientation in any file interface.

For missing or prohibited exact software/data prerequisites, report **SKIP3**,
name the affected analysis and exact prerequisite, and explain how to provide it
or retain the skip. For invalid data/design/source/integrity, process or numerical
failure, report **FAIL2**, preserve partial evidence and require a corrected run
in new output. Do not install or substitute another edition to make a run pass.
Native GSEA execution itself needs no upstream runtime; original runtimes are
needed only to regenerate its original-source comparisons.

Cell Ranger remains excluded at the user's request. Earlier educational/research
FIMO and academic noncommercial GATK 3.8 permissions remain recorded; do not infer
broader redistribution rights. Preserve all historical/null/failed/superseded
evidence. Both literature HTML reports remain withheld from GitHub Pages.
No release, new site deployment or full-goal completion is claimed here.

## Next work, in priority order

1. **Finish the native GSEA file workflow around the existing engine.** Do not
   reimplement its arithmetic. Add a strict prepared-data recipe with literal
   IDs, float32 numbers parsed from their lexical tokens, ordered class members,
   explicit source edition/null model/ranking options/weight/seed and organism,
   identifier namespace, expression-unit, universe and gene-set provenance.
   Use decimal-string signed 64-bit seeds. `NextflowParameters` preserves numeric
   lexical tokens; inspect its API before reuse. A generic JSON `Double` round-trip
   can change float32 midpoint rounding. Standard GCT/CLS/GMT/RNK import remains
   a separate parser-semantics task; do not imply that prepared JSON reproduces
   vendor file ingestion. Only meandiv normalization is currently implemented.
2. **Make failure/output behavior concrete before registering a command.** Provide
   standalone RUN, VERIFY and REPLAY with immutable captured inputs/effective
   recipe, exact owned class provenance and complete output inventories. A child
   using the project's own Java/JAR can enforce explicit heap, wall and live
   output-file limits; remove external Java injection variables. Monitor live
   file sizes, not stale Windows directory snapshots. Kill only owned processes.
   Persist progress callbacks/null prefixes and structured conditions even on
   failure. Distinguish byte integrity from semantic correctness: VERIFY must
   recompute from captured inputs and compare exact result objects; REPLAY must
   use fresh output and preserve the parent. Refuse path/junction aliases,
   unknown extra files and incomplete families. Test actual packaged positive,
   numerical/refusal/capacity, tamper/reseal, timeout/output and cross-host cases.
   edgeR and SuSiE have qualified workflow patterns to reuse after inspection.
3. **Complete the Broad GSEA source-role crosswalk.** The all-250 XML lexical
   census, 19 existing uses and 31 extra candidates are intake evidence only.
   No GSEA review or family credit has been added. Separate Broad Desktop from
   clusterProfiler, fgsea, GSEApy, ssGSEA and resource mentions. Consult
   [candidate anchors and source notes](gsea-handover-20261120/SOURCE-NOTES.md).
   P077 names Broad 4.3.2; P220 names 4.3.3, rlog and 10,000 permutations.
   Unknown metrics/null types remain unknown. P096's `2023.1` is not an executable
   edition; audit its script/data and zero-adjusted-P boundary before admission.
4. **Qualify global registration, then guarded catalog admission.** Adding the
   dispatcher changes shared source identities. Preserve the exact historical
   owner and introduce an explicit verified transition; do not refresh old
   fingerprints. Build the normal JAR, run actual command examples and controls,
   then plan/inspect/apply/fresh-verify admission. Preserve all previous reviews
   and report any new observation/provider corrections explicitly. Catalog
   ownership currently resides in the unchanged edgeR admission checkpoint.
5. **Continue higher-impact missing scopes and the remaining 1,185 reviews.**
   After closing GSEA, reassess GCTA (17 uses), limma (16), PEER (15),
   TwoSampleMR (12), GCTA-COJO/QTLtools (nine each) against already implemented
   scopes, feasibility and scientific impact. Counts alone do not establish
   priority or unsupported scope. Prefer useful native Java methods where
   practical and tested exact-upstream adapters where appropriate. Downloads
   and private installs are authorized where permitted; retain prominent skips
   with recovery instructions when they are unavailable.

## Exact local resume and transfer paths

| Path | Purpose |
| --- | --- |
| `D:/projects/JLinAlg/build/gs-3` | This engine checkpoint worktree; keep it intact |
| `D:/projects/JLinAlg/build/gs-3/build/gsea-high-impact-3/native-gradle-1/project-build/cli/omicsmatic-0.4.0.jar` | Exact qualified normal producer |
| `D:/projects/JLinAlg/build/gs-3/build/gsea-high-impact-3` | Complete new reference sweeps, previous versions, native checks, build and packaged qualification |
| `D:/projects/JLinAlg/build/gsea-engine-start-20261120-1` | Worktree startup/snapshot receipts |
| `D:/projects/JLinAlg/build/git-publish/20261120-gsea-handover-63` | This publication's snapshots, protected-artifact manifests, readbacks and final push receipt |
| `D:/projects/JLinAlg/build/git-publish/20261120-gsea-native-62` | Predecessor publication/protection baseline |
| `D:/projects/JLinAlg/build/gs-2/build/gsea-high-impact-2` | Earlier original source builds, runtimes, 10,000-permutation compositions and primitive qualification |
| `D:/projects/JLinAlg/build/gs-1/build/gsea-high-impact-1` | Official-source intake, 4.4.0 runtime and all-250 source census |
| `D:/projects/JLinAlg/build/edg-3` | Immutable admitted edgeR owner; run its catalog verifier there |
| `D:/projects/JLinAlg/build/literature-review/xml` | Exact cached full-text inputs used by the census and catalog audit |

New private evidence folders include `original-engine-sweep-1` and `-2`,
`native-engine-initial-1`, `native-engine-sweep-1` and `-2`, `native-gradle-1`,
`packaged-engine-qualification-1` and `predecessor-native-readback-1`. Keep version
one evidence; version two strengthens the tie/collision/class-order controls.
The final packaged checks rerun both suites on the final unchanged engine bytes.

Publication protection inherits the predecessor SHA-256 manifests and checks
inherited size/mtime while pinning new artifacts. It is not a repeated full
rehash of all inherited files; the receipt states the exact scope. All 30 older
worktrees match their saved snapshots. Their committed HEADs are ancestors of
main, and unpublished old file variants are already retained in the verified
[historical overlay archive](handover-20261119/README.md). Do not apply those
overlays over current code or reset/clean the original worktrees. Keep the dirty
root `D:/projects/JLinAlg` on its historical branch.

The earlier [native arithmetic checkpoint](GSEA_NATIVE_CHECKPOINT_2026-11-20.md)
and [source intake handover](HANDOVER_2026-11-20_GSEA.md) contain detailed source
commits, runtimes and previous failures. Their engine-unimplemented statements
are superseded by this handover; their retained evidence remains valid history.
