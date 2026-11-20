# Native GSEA file workflow checkpoint and next work

**The standalone native GSEA RUN/VERIFY/REPLAY workflow is implemented and
qualified. Global command registration, the source-role crosswalk and guarded
catalog admission remain unfinished. No new GSEA family credit is added here.**

The predecessor is main `7b6ed9d96d9d52bc0b141811dc741bed874616ba`. This checkpoint
is developed in `D:/projects/JLinAlg/build/gs-4`, branch
`codex/gsea-files-20261120`; consult its Git history for the final commit. Preserve
this checkpoint and start subsequent work in an isolated checkout of latest main.

The canonical catalog is unchanged: **250 papers, 2,705 observations, 1,520
reviewed uses, 1,185 pending and 139 admitted scoped integrations (25 native Java
and 114 upstream)**, with 823 manifest entries. Including the three pre-existing
integrations gives 142. The original 2023-10-23 through 2026-10-23 collection
contains 71 Nature, 104 Nature Genetics and 75 AJHG papers; no extension was needed.
There are 446 named/485 effective definitions, 259 missing and 36 method overlaps.
The full implementation/review goal remains active and unfinished.

## What is now usable

Read the [beginner guide](../vignettes/gsea-native-workflow.md) and copy the
[synthetic examples](../../examples/native-gsea/README.md) to NEW working space.
The entry point is `org.jlinalg.cli.BroadGseaWorkflowCli` in the normal executable
JAR. It accepts `--request` with explicit RUN, VERIFY or REPLAY recipes. This is
currently a standalone command; do not claim discovery through the global menu.

It supports preranked, expression/gene-set and expression/phenotype permutation
analyses using the already qualified native engine. Prepared JSON retains literal
gene/set/sample identities, ordered class members and original numeric lexemes
for direct finite float32 parsing. All source edition, ranking, randomization,
weight, meandiv normalization, seed, universe/provenance and capacity choices are
explicit. Each analysis has its own complete supplied gene-set family.

The owned Java worker has explicit heap/wall/live-output/file limits and recorded
source-class/JAR identities. Raw inputs, rankings, dispositions, observed profiles,
all null ES values and normalized null vectors are retained. Failure callbacks and
prefixes survive ordinary errors; owned-process cleanup is recorded on limits.
The supervising JVM also needs sufficient launch memory; missing final completion
status after its abrupt termination means NO COMPLETE RESULT.

VERIFY structurally checks the complete saved capture, recomputes captured inputs
in NEW output and compares every scientific output byte. REPLAY preserves the
parent and records lineage. Both refuse aliases/junctions, extra/missing files or
directories, mismatched recipes, incompatible owned class payloads and incomplete
families. Cross-host verification does not require original external source paths.

The current scope excludes vendor file ingestion, expression transformation,
identifier mapping, probe/chip collapse, collection retrieval, continuous
phenotypes and vendor marker/GUI reports. It does not reproduce author data or
establish biological validity. Other source branches remain explicit gaps.

## Exact qualification

Final normal producer:

```text
build/gs-4/build/gsea-high-impact-4/native-gradle-3/project-build/cli/omicsmatic-0.4.0.jar
195901164 bytes
SHA-256 a47205131ac6f7b52271545f457a46aebd5bbc84b35eda305c6a563be512661e
```

All **23,508 predecessor JAR members** remain unchanged. Only workflow classes
are added. Eight focused JUnit tests pass in the normal build. Windows Java 21,
Windows Java 25 and Linux Java 21 each pass:

- Nine original-source compositions, three forms in each of 4.3.2/4.3.3/4.4.0,
  with 10,000 permutations per form: **468,270 exact fields**.
- **423 source-admitted option cases and 200,469 exact fields**, including raw
  double ranking metrics, float ties, constrained randomization, class order,
  source set filtering and full-universe boundaries.
- **34 expected refusals**, including missing/unsupported prerequisites, schema,
  data/design/numerical boundaries, partial batches, heap exhaustion, live output,
  timeout/owned cleanup with an unrelated sentinel preserved, tamper/reseal,
  producer changes and actual physical parent aliases.

Additional checks pass for Unicode/control identities, signed seed extremes,
float32 midpoint rounding, both cross-host VERIFY/REPLAY directions and a named
class-directory SKIP3. Parent bytes and modification times remain intact.

The [saved proof](gsea-workflow-development/README.md) includes exact binary objects
for all 432 positive original-counterpart cases. Its reader rechecks **668,739
original fields** from archive bytes without Java/model execution. Saved-reader
positive, exact-JAR and refusal controls also run on Windows/Linux Python; their
separate receipts are retained alongside the proof. This is a scoped synthetic
qualification, not whole-tool, paper or biological completion.

Earlier failed/superseded attempts remain preserved. WSL temporarily blocked the
original host during concurrent compaction; final Linux runs use the existing
`JLinAlg-Omics-PyMOL-20261026` Java 21 environment. A heap test first hit its input
cap and was corrected to exercise the intended heap boundary. Cross-host checks
caught two platform-dependent JSON-lines endings with identical numerical
binaries; the final producer writes canonical LF and passes cross-host checks.
No source fallback, tolerance relaxation or old fingerprint refresh admitted them.

## Warnings, preservation and transfer

**SKIP3:** supply the named exact permitted input/executable prerequisite, or keep
the dependent analysis skipped. **FAIL2: NO COMPLETE RESULT:** preserve inputs,
partials, null prefixes and diagnostics; correct the reported data/design,
integrity, numerical, capacity or process cause and retry NEW output. Do not
silently change identifiers, replace nonfinite values, change editions or
substitute algorithms. Native GSEA needs no external GSEA installation.

Keep the source's size-filter and randomizer semantics: equal min/max disables
filtering and namespace projection; `EQUALIZE_AND_BALANCE` does not equalize class
sizes. All analysis settings must remain analyst-declared where papers are silent.
Cell Ranger remains excluded; prior FIMO/GATK research-use decisions remain in the
earlier handover. Do not infer redistribution rights for ignored third-party files.

The concurrent completed cleanup is indexed at
`D:/projects/JLinAlg/build/cleanup-20261120.json`. It retired 12 historical
worktrees, preserved their branches/build evidence and saved six exact local-edit
recovery archives. All archived payloads were freshly checked against SHA-256,
lengths, ZIP CRC and retained Git blobs. **There are now 20 registered worktrees**;
the 19 earlier live worktrees remain exact. Do not recreate retired stale trees or
apply archived drafts over current code.

The publication audit accounts for all 401,823 predecessor artifact pins: 401,820
live artifacts retain their size/mtime and three are explicitly retired under the
completed rebuildable-cache cleanup. No unexplained missing/changed artifact is
admitted. Old SHA-256/stat manifests are retained; this is not a repeated full
rehash of every inherited artifact. New evidence is separately SHA-256 pinned.

**Legacy exact-producer warning:** the cleanup retired three old JAR paths. Four
exact retained copies cover two of those producer identities and are newly pinned.
For the remaining 193,411,520-byte producer, SHA-256
`9955f022aa4a418a7d16ba248d2c40e3bf9588f2a3942728b48262065bbc4cb7`, no exact regular
JAR copy was found in the workspace build search. Any check explicitly requiring
that historical producer must remain **SKIP3** until an exact permitted backup is
provided. Do not substitute a newer JAR or refresh its old hash. The current GSEA
producer and direct predecessor remain available. See
[the precise identities, retained copies and recovery action](gsea-workflow-development/PRESERVATION.json).

**GitHub does not back up ignored producer JARs, runtimes, full-text caches or
complete private evidence. Transfer those separately before changing machines.**
Keep `build/gs-4/build/gsea-high-impact-4`, the preceding `gs-1`/`gs-2`/`gs-3` GSEA
evidence, `build/literature-review/xml`, cleanup recovery records and
`build/git-publish/20261120-gsea-files-64`. The latter contains final publication,
current-worktree and artifact-transition receipts. Both literature reports remain
withheld from Pages. This creates no release and claims no new site deployment.

## Next work

1. Finish the GSEA source-role crosswalk using the existing 19 uses and 31 new
   lexical candidates. The [source notes](gsea-handover-20261120/SOURCE-NOTES.md)
   distinguish Broad from clusterProfiler/fgsea/GSEApy/ssGSEA/resource mentions.
   P077 names 4.3.2; P220 names 4.3.3, rlog and 10,000 permutations. Unknown metric,
   null model and resource choices stay unknown. P096's `2023.1` is not an
   executable edition; audit the linked script/data and zero-adjusted-P boundary.
2. Qualify global command registration and metadata with an explicit predecessor
   transition for shared source guards. Preserve the exact current owner at
   `D:/projects/JLinAlg/build/edg-3`; do not refresh old saved hashes. Run actual
   normal-JAR command examples and inverse controls before catalog changes.
3. Plan/inspect/apply/fresh-verify guarded admission, preserving all 1,520 prior
   reviews and explicitly reporting any observation/provider correction. The
   current edgeR owner freshly verifies 139 integrations and 823 manifest entries.
4. Continue higher-impact missing scopes and the remaining 1,185 reviews. Reassess
   GCTA (17 uses), limma (16), PEER (15), TwoSampleMR (12), GCTA-COJO/QTLtools (nine
   each) against existing scope, feasibility and scientific impact. Prefer native
   Java where practical and tested exact-upstream adapters where appropriate.
   Downloads/private installs remain authorized where permitted; otherwise retain
   prominent analysis skips with exact prerequisite and recovery instructions.

The [preceding engine handover](HANDOVER_2026-11-20_GSEA_ENGINE.md) gives original
source commits, runtime locations and source-fidelity details. Its file-workflow
unfinished and 31-worktree statements are superseded by this checkpoint; its
scientific evidence remains retained history.
