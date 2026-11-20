# Native Broad GSEA arithmetic: checkpoint and next work

**The full 250-paper implementation/review goal remains unfinished.** A native
Java arithmetic component is now qualified. The complete project GSEA file
workflow, RUN/VERIFY/REPLAY, global discovery, provider-aware source-role review
and cumulative admission remain open. This checkpoint supersedes the earlier
GSEA source-build/statistical-execution-open instructions where those refer to
upstream synthetic qualification. It does not supersede the pending project
workflow or paper-review tasks.

## What is ready

`org.jlinalg.enrichment.BroadGseaStatistics` implements original source-defined
score walks and supplied-null NES, nominal P, empirical FDR and FWER. It
preserves float/mixed accumulation, member order, strict/inclusive comparison
rules, sign tails and per-permutation-column FDR behavior. Four original
weighting modes are exposed, with explicit refusal of source fallback branches.
It does not generate permutations, rank expression data or map/collapse IDs.
See the [copyable Java API guide](../vignettes/gsea-native-statistics.md).

Original unchanged-source 4.3.2, 4.3.3 and 4.4.0 profiles each complete preranked,
expression/phenotype-null and expression/gene-set-null synthetic compositions
with 10,000 permutations. Full original float objects agree with 225 CLI
scalar exports at the source four-decimal precision. The native component
matches 356,286 float fields exactly per JVM on Windows Java 21/25 and Linux
Java 21, with 1,701 source-bound refusals per JVM. These include 2,415 admitted
walks and 444 supplied-null families across source profiles, not that many
independent papers or biological findings. Shared 4.3 source tags remain
explicitly identified. Native tests on Linux do not establish original Linux
statistical execution or author-host reproduction.

The normal source build and both focused JUnit tests pass. All 23,485 old JAR
payloads remain exact; four new native class payloads and the unchanged original
license resource are added. The normal producer is 195,816,030 bytes, SHA-256
`f701b3b92a67871119c1c9096eb0f7d74f23a3cc0dfad95823bd5f4eba5d13cd`.
The released 0.4.0 binary predates these APIs; use a current source build.

The [saved qualification](gsea-native-development/README.md) reuses a
3,870,731-byte manifested fixture with original sources, full vectors and
failure objects. Its standard-library reader passes on Windows/Linux with
the exact producer. Six refusal controls per host cover existing output,
different producer, missing archive, changed archive, missing source guard,
and a resealed failed native process. Original failed attempts are retained.

## Warnings and required actions

**FAIL2: NO COMPLETE ANALYSIS RESULT.** Keep raw inputs, original model/null
objects, conditions, diagnostics, partials and all attempts. Native arithmetic
refuses nonfinite/zero required denominators, source fallback weights, zero
observed ES without an original FDR result, bad membership/shapes and exceeded
explicit capacity. Resolve the named cause and use a NEW output. Do not repair
IDs, versions, thresholds, nonfinite values or algorithms silently, invent nulls,
add pseudocounts or replace empirical FDR with BH. An unused absent sign tail
remains distinct from a missing required tail.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Restore the named exact permitted
input/source/runtime/package/producer, or retain the skip if unavailable or
installation is forbidden. Downloads/private installation remain authorized
where permitted. The native methods need only Java; original permutation and
expression-ranking workflows still need their separately prepared runtime.
Cell Ranger remains excluded. Historical edgeR/marker prerequisites and
convergence refusals in the admitted edgeR checkpoint remain unchanged.

The first 50-gene phenotype request failed because it requested 100 markers.
The separate 300-gene/16-sample request retains 100 markers and completes all
three 10,000-permutation forms. Two early original-API probes failed before
statistics because application initialization was missing; the corrected
reference explicitly uses the normal original application handler. Two JDK 8
intakes failed size/hash checks and were never used. The unchanged old source
builds succeeded with cached Linux JDK 11/Gradle 4.10.2; their reflective-access
and deprecation warnings remain retained. No vendor source was patched.

**GitHub is not a complete machine backup.** Transfer ignored runtimes,
full source/XML/PDF caches, complete evidence/builds and publication manifests
separately if changing machines. Keep all 30 worktrees and all failed and
superseded attempts. Do not reset/clean them or apply historical overlays over
main. SHA-256 is integrity, not authentication. Both literature report pages
remain withheld from Pages; no release is requested.

## Current admitted catalog remains unchanged

| Item | Value |
| --- | --- |
| Frozen papers | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Original window | 2023-10-23 through 2026-10-23; no extension |
| Named families / effective definitions | 446 / 485 |
| Observations | 2,705 |
| Reviewed / pending | 1,520 / 1,185 |
| Scoped integrations | 139: 25 native +114 upstream; 142 including three existing adapters |
| Missing / method-overlap | 259 / 36 |
| Manifest entries | 823 |
| New GSEA family/review credit | Zero; 19 original observations remain unreviewed |

Native component qualification does not itself admit an umbrella software
family or complete a paper role. The exact admitted edgeR checkpoint remains
`0de612c6182b87d42a1d574af5f06a99630a0924`; the following handover is main
`7a50e4a18d27e0bccd82d70b9a5632062abb3fc3`.

## Continue in this order

1. Build the project GSEA file workflow using the three established original
   profiles and this native audit. Inspect the original Java ranking/permutation
   code and implement practical native branches, with tested upstream adapters
   where appropriate. Support both expression/phenotype
   and preranked inputs, plus the explicitly separate gene-set null. P220 uses
   GSEA 4.3.3 on DESeq2 rlog matrices and always 10,000 permutations; a preranked
   substitute does not cover that role. Require explicit class orientation,
   metric, null model, seed, size limits, gene-set collection/species and input
   units. Source defaults do not establish unspecified author settings.
2. Use strict prepared inputs and data-only requests. Capture raw bytes,
   original ranking/universe and member dispositions, full-precision nulls and
   original result/condition objects. Do not rely on four-decimal EDB vectors
   for bit-exact replay. Refuse unrequested mapping, remapping, duplicate
   selection and nonfinite substitution. Keep selected-universe source roles
   explicit; do not silently expand them. Isolate the original Java runtime
   and dependencies so the project JAR cannot shadow vendor classes. Add new
   worker/orchestration files; preserve the qualified reference probes.
3. Add owned process/output/time/memory controls, strict failed/skipped status,
   complete file inventories and semantic VERIFY/REPLAY. Use live EOF size
   monitoring rather than Windows directory-snapshot sizes. Reuse proven
   helpers where applicable, but do not edit frozen edgeR/other engines without
   a separate qualification. Test actual normal-JAR execution, numerical/no-result
   boundaries, missing/forbidden prerequisites, parent aliases and cross-host
   replay before registering a global command.
4. Finish semantic review of all 50 lexical candidates and remaining paper
   contexts before adding observations/reviews. See the
   [provisional source notes](gsea-handover-20261120/SOURCE-NOTES.md) and hashed
   candidate anchors. Many GSEA-labelled roles use clusterProfiler, fgsea,
   GSEApy, ssGSEA, ORA or MSigDB resources. P040's 4.3.3 is clusterProfiler;
   P096's literal 2023.1 is not an established executable edition. Audit its
   script 20 and supplements. P077 explicitly names GSEA 4.3.2; exact settings
   still need supplementary evidence. Do not invent function names/settings.
5. Then qualify global discovery, a per-paper scope crosswalk and guarded
   cumulative plan/inspect/apply/fresh verify through the exact retained edgeR
   owner. Do not patch old readers, refresh their hashes or run old owners on
   newer metadata as if it were their producing state. Keep the catalog counts
   unchanged until the new admission passes. Preserve unsupported paper modes,
   editions and unresolved source wording as explicit gaps.
6. Continue higher-impact remaining families and all other individual/paper-wide
   reviews. Frequent gaps include GCTA (17 observations), limma (16), PEER
   (15), TwoSampleMR (12), GCTA-COJO and QTLtools (9 each); counts overlap and
   are not a complete impact ranking. Reuse existing provider-specific/native
   methods and iDEP 2.4.4. The 259 missing definitions, other 1,185 pending uses,
   held-open monitor audit, practical native edgeR filtering/`sumTechReps`,
   exact historical inference gaps and full semantic completeness remain open.

## Exact resume paths

Active checkout: `D:/projects/JLinAlg/build/gs-2`, branch
`codex/gsea-workflows-20261120`, based on exact main handover `7a50e4a1`.
Keep `build/gs-1` at that handover and `build/edg-3` at the admitted edgeR
predecessor. Preserve the dirty root and all other historical worktrees.
Older draft variants remain in the published overlay archives.

From the active checkout, Python 3.11+ without `-O`, NEW outputs:

```text
python -B tools/literature/verify_broad_gsea_native.py --out build/gsea-native-proof-NEW
python -B tools/literature/verify_broad_gsea_native.py --out build/gsea-native-exact-jar-NEW --jar build/gsea-high-impact-2/native-gradle-1/project-build/cli/omicsmatic-0.4.0.jar
```

The default reader does not require Java, Git, GSEA or private runtimes. Optional
`--jar` requires the exact producer, not an interchangeable new build. It does
not rerun the mathematics or original permutations. Use the API guide or
qualified source-counterpart tests for computational checks. Do not reapply
stale catalog plans.

Private root: `D:/projects/JLinAlg/build/gs-2/build/gsea-high-impact-2/`:

| Directory | Retained purpose |
| --- | --- |
| `original-synthetic-4.4.0-1` | First small runs and genuine phenotype marker-cap failure |
| `original-workflow-forms-4.4.0-2` | Three successful 300-gene/16-sample 10,000-permutation forms |
| `original-api-4.4.0-1`, `-2`, `-3` | Two failed initialization attempts/source, then full-float API/CLI counterparts |
| `legacy-build-prereqs-1`, `legacy-build-jdk8-2` | Exact Gradle intake and two unused failed JDK 8 intakes |
| `original-build-4.3-source-1` | Successful unchanged old source builds, exact commands/logs and producers |
| `original-4.3-profiles-1/4.3.2` and `/4.3.3` | Private runtimes, original API/CLI forms and primitive exports |
| `original-primitives-1` | Original 4.4 full-precision walk/family cases and failures |
| `native-original-check-1`, `native-primitives-check-1` | Initial independent source comparisons |
| `native-gradle-1` | Normal source build, JUnit report, exact producer |
| `native-packaged-qualification-1` | Whole producer inventory, unchanged predecessor payloads and three JVM runs |
| `native-reference-packet-1`, `native-public-proof-1` | Public fixture/proof construction and saved readback |
| `native-reader-controls-1` | Both portable readers and six refusal controls each |

Original immutable source intakes and the 4.4 runtime remain under
`D:/projects/JLinAlg/build/gs-1/build/gsea-high-impact-1/`. The exact frozen
250-paper XML cache remains `D:/projects/JLinAlg/build/literature-review/xml`.
The preceding handover lists Java/runtime paths, original intake helpers and
the cached Linux JDK 11. Do not run those one-shot helpers over existing outputs.

The cumulative owner still runs unchanged in `D:/projects/JLinAlg/build/edg-3`,
using its own NEW build output. Publication receipts are under
`D:/projects/JLinAlg/build/git-publish/`; the previous one is
`20261120-gsea-handover-61/PUBLISHED.json` and this milestone uses
`20261120-gsea-native-62/`. Preserve inherited SHA evidence and its new stat
checks separately from new-artifact rehashes; do not claim a fresh full
inherited hash audit. Transfer the complete permitted private caches/evidence
and verify identities/prerequisites on the receiving machine.
